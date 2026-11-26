package com.college.growth.profile;

import com.college.growth.common.enums.SectionCodeEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.result.ResultCode;
import com.college.growth.identity.ArchiveIdentity;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "GROWTH_MYSQL_TEST", matches = "true")
class ProfileStoreIntegrationTest {
    @Autowired ProfileStore store;
    @Autowired JdbcTemplate db;
    private final long student = UUID.randomUUID().getLeastSignificantBits() & Long.MAX_VALUE;
    private static final String YEAR = "2025-2026-1";

    private void session(long version) {
        var request = new MockHttpServletRequest();
        var identity = new ArchiveIdentity(student,ArchiveIdentity.Role.STUDENT,Set.of(student),Set.of(YEAR));
        request.setAttribute(ArchiveIdentity.class.getName(),identity);
        request.addHeader("If-Match",Long.toString(version));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void cleanup() {
        RequestContextHolder.resetRequestAttributes();
        db.update("DELETE l FROM arc_profile_log l JOIN arc_profile p ON p.id=l.profile_id WHERE p.student_id=?",student);
        db.update("DELETE i FROM arc_profile_item i JOIN arc_profile p ON p.id=i.profile_id WHERE p.student_id=?",student);
        db.update("DELETE FROM arc_profile WHERE student_id=?",student);
    }

    @Test
    void invalidSecondFieldRollsBackFirstFieldAndAudit() {
        session(0);
        var patch = new java.util.LinkedHashMap<String,Object>();
        patch.put("intention","EMPLOY");
        patch.put("unknown","should fail");
        assertThrows(BusinessException.class,() -> store.save(SectionCodeEnum.CAREER,student,YEAR,patch));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM arc_profile WHERE student_id=?",Integer.class,student));
    }

    @Test
    void configuredFieldRoundTripsWithoutDtoChange() {
        String key = "test"+Long.toUnsignedString(student);
        db.update("INSERT INTO arc_field_def(section_code,field_key,label,field_type,sort_order,validator) VALUES('CAREER',?,'测试','text',100,'TEXT_64')",key);
        try {
            session(0);
            store.save(SectionCodeEnum.CAREER,student,YEAR,Map.of(key,"动态字段"));
            var detail = store.detail(SectionCodeEnum.CAREER,student,YEAR,Map.class);
            assertEquals(Map.of(key,"动态字段"),detail.get("fields"));
            session(1);
            assertThrows(BusinessException.class,() -> store.save(SectionCodeEnum.CAREER,student,YEAR,Map.of(key,"长".repeat(65))));
        } finally {
            db.update("DELETE FROM arc_field_def WHERE section_code='CAREER' AND field_key=?",key);
        }
    }

    @Test
    void submittedRevisionIsDurableAndCannotBeOverwritten() {
        var code = SectionCodeEnum.CAREER;
        session(0);
        var missing = assertThrows(BusinessException.class,() -> store.submit(code,student,YEAR));
        assertEquals(ResultCode.REQUIRED_FIELD_MISSING,missing.getResultCode());
        store.save(code,student,YEAR,Map.of("intention","EMPLOY"));
        var stale = assertThrows(BusinessException.class,() -> store.save(code,student,YEAR,Map.of("intention","STARTUP")));
        assertEquals(ResultCode.STATE_CONFLICT,stale.getResultCode());
        session(1);
        store.submit(code,student,YEAR);
        var detail = store.detail(code,student,YEAR,Map.class);
        assertEquals("SUBMITTED",detail.get("status"));
        assertEquals(false,detail.get("editable"));
        assertEquals(Map.of("intention","EMPLOY"),detail.get("fields"));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM arc_profile_log l JOIN arc_profile p ON p.id=l.profile_id WHERE p.student_id=?",Integer.class,student));
    }
}
