package com.college.growth.review.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.college.growth.common.profile.entity.ArcProfile;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "GROWTH_MYSQL_TEST", matches = "true")
class ReviewTaskScopeIntegrationTest {
    @Autowired ReviewTaskMapper mapper;
    @Autowired JdbcTemplate db;
    private static final String YEAR = "scope-test";

    @Test
    void masterClassWithoutBasicAndLegacyClassShareTheSameScope() {
        db.update("INSERT INTO org_class(id,class_code,class_name,major_name,grade_year,counselor_user_id) VALUES(-91,'SCOPE1','测试一班','测试',2024,9001),(-92,'SCOPE2','测试二班','测试',2024,9002)");
        db.update("INSERT INTO stu_student(id,student_no,name,class_code) VALUES(-81,'scope81','学籍姓名','SCOPE1'),(-82,'scope82','其他班','SCOPE2')");
        db.update("""
                INSERT INTO arc_profile(id,student_id,academic_year,section_code,status) VALUES
                (-71,-81,?,'ACADEMIC','SUBMITTED'),(-72,-82,?,'ACADEMIC','SUBMITTED'),
                (-73,-83,?,'ACADEMIC','SUBMITTED'),(-74,-83,?,'BASIC','DRAFT'),(-75,-82,?,'BASIC','DRAFT'),
                (-76,-84,?,'ACADEMIC','SUBMITTED'),(-77,-84,'prior-scope','BASIC','DRAFT')
                """, YEAR, YEAR, YEAR, YEAR, YEAR, YEAR);
        db.update("""
                INSERT INTO arc_profile_item(id,profile_id,field_code,value_text) VALUES
                (-61,-74,'basic.classId','SCOPE1'),(-62,-75,'basic.classId','SCOPE1'),(-63,-77,'basic.classId','SCOPE1')
                """);
        var page = mapper.selectTaskPage(new Page<>(1, 10), "ACADEMIC", "SUBMITTED", YEAR, "SCOPE1", 9001L);
        assertEquals(2, page.getTotal());
        assertEquals(List.of(-81L, -83L), page.getRecords().stream().map(ArcProfile::getStudentId).toList());
        var fields = mapper.selectStudentFields(List.of(-81L), List.of("basic.name", "basic.classId"), YEAR);
        assertEquals(List.of("SCOPE1", "学籍姓名"), fields.stream().map(StudentFieldRow::getValueText).sorted().toList());
    }
}
