package com.college.growth.common.profile.service;

import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.SectionCodeEnum;
import com.college.growth.common.enums.UserRoleEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.review.service.ProfileWorkflowService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "GROWTH_MYSQL_TEST", matches = "true")
class SectionFormIntegrationTest {
    @Autowired SectionFormService forms;
    @Autowired ProfileLocator locator;
    @Autowired ProfileWorkflowService workflow;

    @AfterEach
    void clearSession() {
        UserContext.clear();
    }

    @ParameterizedTest
    @EnumSource(value = SectionCodeEnum.class, names = {"ACADEMIC", "INTEREST"})
    void studentDraftCanBeReadSubmittedWithdrawnAndReviewed(SectionCodeEnum section) {
        long student = 1001L;
        String year = "2098-2099-1";
        UserContext.set(new CurrentUser(student, UserRoleEnum.STU, false));
        Map<String, Object> fields = section == SectionCodeEnum.ACADEMIC
                ? Map.of("goalShort", "每周整理课程笔记并记录薄弱知识点，定期复习巩固学习成果", "strongSubjects", List.of("数学"))
                : Map.of("hobbies", List.of("其他"), "hobbyDesc", "学习棋艺");
        forms.save(student, year, section, fields);
        var draft = forms.detail(student, year, section);
        fields.forEach((key, value) -> assertEquals(value, draft.fields().get(key)));
        forms.submit(student, year, section);
        assertThrows(BusinessException.class, () -> forms.save(student, year, section, fields));
        forms.withdraw(student, year, section);
        forms.submit(student, year, section);
        UserContext.set(new CurrentUser(9001L, UserRoleEnum.COU, false));
        workflow.approve(locator.require(student, section.name(), year, section.getLabel()), "通过");
        assertEquals("APPROVED", forms.detail(student, year, section).status());
    }
}
