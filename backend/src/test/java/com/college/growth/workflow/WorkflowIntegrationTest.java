package com.college.growth.workflow;

import static org.junit.jupiter.api.Assertions.*;

import com.college.growth.common.enums.SectionCodeEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.identity.ArchiveIdentity;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "GROWTH_MYSQL_TEST", matches = "true")
class WorkflowIntegrationTest {
    @Autowired WorkflowController workflow;
    @Autowired JdbcTemplate db;
    final long student = UUID.randomUUID().getLeastSignificantBits() & Long.MAX_VALUE;
    final String year = "2025-2026-1";

    void session(long actor, ArchiveIdentity.Role role, long version) {
        var request = new MockHttpServletRequest();
        request.setAttribute(ArchiveIdentity.class.getName(), new ArchiveIdentity(actor, role, Set.of(student), Set.of(year)));
        request.addHeader("If-Match", Long.toString(version));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    void insert(String code, String status) {
        db.update("INSERT INTO arc_profile(student_id,academic_year,section_code,status,version) VALUES(?,?,?,?,1)",
                student, year, code, status);
    }

    @Test
    void concurrentReviewAcceptsOneDecision() throws Exception {
        insert("BASIC", "SUBMITTED");
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        Callable<Boolean> review = () -> {
            session(Thread.currentThread().getId(), ArchiveIdentity.Role.COUNSELOR, 1);
            try {
                start.await();
                workflow.transition(student, "basic", "approve", year, null);
                return true;
            } catch (BusinessException conflict) {
                assertEquals("EC-40902", conflict.getResultCode().getCode());
                return false;
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        };
        try {
            var one = pool.submit(review);
            var two = pool.submit(review);
            start.countDown();
            assertNotEquals(one.get(), two.get());
            assertEquals(1, logs());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void withdrawalCanOnlyBeUsedOnceAndDoesNotConsumeAReviewReturn() {
        insert("CAREER", "SUBMITTED");
        session(student, ArchiveIdentity.Role.STUDENT, 1);
        workflow.transition(student, "career", "withdraw", year, null);
        assertEquals("DRAFT", workflow.list(student, year).getData().get(0).status());
        db.update("UPDATE arc_profile SET status='SUBMITTED',version=3 WHERE student_id=?", student);
        session(student, ArchiveIdentity.Role.STUDENT, 3);
        var exceeded = assertThrows(BusinessException.class,
                () -> workflow.transition(student, "career", "withdraw", year, null));
        assertEquals("EC-40903", exceeded.getResultCode().getCode());
        session(12L, ArchiveIdentity.Role.COUNSELOR, 3);
        var shortReason = assertThrows(BusinessException.class, () -> workflow.transition(student,
                "career", "return", year, new WorkflowService.Decision("补充")));
        assertEquals("EC-42203", shortReason.getResultCode().getCode());
        workflow.transition(student, "career", "return", year, new WorkflowService.Decision("请补充本学期的职业发展计划"));
        assertEquals("RETURNED", workflow.list(student, year).getData().get(0).status());
        assertEquals(2, logs());
    }

    @Test
    void archiveRequiresAllSixReviewedVersions() {
        session(12L, ArchiveIdentity.Role.ADMIN, 1);
        Map<String, Long> versions = new HashMap<>();
        for (SectionCodeEnum code : SectionCodeEnum.values()) {
            versions.put(code.getCode(), 1L);
            if (code != SectionCodeEnum.MENTOR) insert(code.name(), "APPROVED");
        }
        assertThrows(BusinessException.class, () -> workflow.archive(student, year, versions));
        assertEquals(0, logs());
        insert("MENTOR", "APPROVED");
        versions.put("basic", 0L);
        assertThrows(BusinessException.class, () -> workflow.archive(student, year, versions));
        versions.put("basic", 1L);
        workflow.archive(student, year, versions);
        assertEquals(6, logs());
        assertTrue(workflow.list(student, year).getData().stream().allMatch(e -> e.status().equals("LOCKED") && e.version()==2));
        assertThrows(BusinessException.class, () -> workflow.archive(student, year, versions));
        assertEquals(6, logs());
    }

    @Test
    void auditFailureRollsBackStatusAndRevision() {
        insert("BASIC", "SUBMITTED");
        db.update("""
                INSERT INTO arc_profile_log(profile_id,actor_id,from_status,to_status,version)
                SELECT id,1,'DRAFT','SUBMITTED',2 FROM arc_profile WHERE student_id=?
                """, student);
        session(12L, ArchiveIdentity.Role.COUNSELOR, 1);
        assertThrows(org.springframework.dao.DuplicateKeyException.class,
                () -> workflow.transition(student, "basic", "approve", year, null));
        var entry = workflow.list(student, year).getData().get(0);
        assertEquals("SUBMITTED", entry.status());
        assertEquals(1, entry.version());
    }

    int logs() {
        return db.queryForObject("SELECT COUNT(*) FROM arc_profile_log l JOIN arc_profile p ON p.id=l.profile_id WHERE p.student_id=?", Integer.class, student);
    }

    @AfterEach
    void cleanup() {
        RequestContextHolder.resetRequestAttributes();
        db.update("DELETE l FROM arc_profile_log l JOIN arc_profile p ON p.id=l.profile_id WHERE p.student_id=?",student);
        db.update("DELETE FROM arc_profile WHERE student_id=?",student);
    }
}
