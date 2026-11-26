package com.college.growth.identity;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "GROWTH_MYSQL_TEST", matches = "true")
class SessionAccessIntegrationTest {
    private static final String YEAR = "2025-2026-1";
    private final long student = UUID.randomUUID().getLeastSignificantBits() & Long.MAX_VALUE;
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;

    private String session(String role, int hours) {
        String header = "Bearer " + UUID.randomUUID() + UUID.randomUUID();
        long actor = role.equals("STUDENT") ? student : student / 2;
        db.update("""
                INSERT INTO arc_session(token_hash,actor_id,role_code,student_ids,academic_years,expires_at)
                VALUES(?,?,?,JSON_ARRAY(?),JSON_ARRAY(?),CURRENT_TIMESTAMP + INTERVAL ? HOUR)
                """, SessionAccess.tokenHash(header), actor, role, student, YEAR, hours);
        return header;
    }

    @Test void verifiesStudentAndYearFromBothQueryAndDraftBody() throws Exception {
        String auth = session("STUDENT", 1);
        String path = "/sections/basic/{id}";
        mvc.perform(get(path, student).header("Authorization", auth).param("academicYear", YEAR))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.academicYear").value(YEAR));
        mvc.perform(get(path, student + 1).header("Authorization", auth).param("academicYear", YEAR))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("EC-40302"));
        mvc.perform(get(path, student).header("Authorization", auth).param("academicYear", "2024-2025-1"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("EC-40302"));
        mvc.perform(put(path, student).header("Authorization", auth).header("If-Match", "0")
                .contentType("application/json").content("{\"academicYear\":\"2024-2025-1\",\"email\":\"a@example.org\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("EC-40302"));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM arc_profile WHERE student_id=?", Integer.class, student));
    }

    @Test void checksExpiryAndRevokesImmediatelyOnLogout() throws Exception {
        mvc.perform(get("/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("EC-40101"));
        mvc.perform(get("/auth/me").header("Authorization", session("STUDENT", -1)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("EC-40102"));
        String auth = session("STUDENT", 1);
        mvc.perform(get("/auth/me").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("STUDENT"));
        mvc.perform(post("/auth/logout").header("Authorization", auth)).andExpect(status().isOk());
        mvc.perform(get("/auth/me").header("Authorization", auth))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("EC-40101"));
    }

    @Test void mentorCanAccessAssignedMentorSectionOnlyIncludingWorkflowList() throws Exception {
        String auth = session("MENTOR", 1);
        db.update("""
                INSERT INTO arc_profile(student_id,academic_year,section_code)
                VALUES (?,?,'MENTOR'),(?,?,'BASIC')
                """, student, YEAR, student, YEAR);
        mvc.perform(get("/sections/mentor/{id}", student).header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.editable").value(true));
        mvc.perform(get("/sections/life/{id}", student).header("Authorization", auth))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("EC-40302"));
        mvc.perform(get("/sections/mentor/{id}", student + 1).header("Authorization", auth))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("EC-40302"));
        mvc.perform(get("/workflow/{id}", student).header("Authorization", auth).param("academicYear", YEAR))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].code").value("mentor"));
    }
}
