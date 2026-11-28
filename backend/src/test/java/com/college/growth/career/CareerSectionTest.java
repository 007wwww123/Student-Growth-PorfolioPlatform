package com.college.growth.career;

import com.college.growth.career.dto.CareerSectionSaveDTO;
import com.college.growth.career.util.CareerSectionUtils;
import com.college.growth.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 职业规划板块的入参兼容与板块级规则。
 *
 * <p>字段渲染、状态机属于公共能力（{@code SectionFormServiceTest} 已覆盖），
 * 这里只测本板块独有的部分：DTO 双写法、发展意向字典兜底、毕业学年判定。</p>
 */
class CareerSectionTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void acceptsEnvelopeAndFlatBodiesAndPreservesExplicitClear() throws Exception {
        for (String body : List.of(
                "{\"intention\":null}",
                "{\"fields\":{\"intention\":null}}",
                "{\"academicYear\":\"2025-2026-1\",\"fields\":{\"support\":null}}")) {
            CareerSectionSaveDTO dto = json.readValue(body, CareerSectionSaveDTO.class);
            assertEquals(1, dto.getFields().size());
            assertNull(dto.getFields().values().iterator().next());
        }
    }

    @Test
    void envelopeKeepsAcademicYearOutsideFields() throws Exception {
        CareerSectionSaveDTO dto = json.readValue(
                "{\"academicYear\":\"2025-2026-1\",\"fields\":{\"intention\":\"POSTGRAD\"}}",
                CareerSectionSaveDTO.class);
        assertEquals("2025-2026-1", dto.getAcademicYear());
        assertEquals(Set.of("intention"), dto.getFields().keySet());
    }

    @Test
    void flatBodyAlsoCollectsUnknownKeysAsFields() throws Exception {
        CareerSectionSaveDTO dto = json.readValue(
                "{\"intention\":\"EMPLOY\",\"industry\":\"工业软件\"}", CareerSectionSaveDTO.class);
        assertEquals(Set.of("intention", "industry"), dto.getFields().keySet());
        assertNull(dto.getAcademicYear());
    }

    @Test
    void intentionMustBeOneOfDictionaryCodes() {
        for (String code : List.of("EMPLOY", "POSTGRAD", "OVERSEAS", "STARTUP", "UNDECIDED")) {
            assertDoesNotThrow(() -> CareerSectionUtils.validateFields(Map.of("intention", code)));
        }
        // 大小写与首尾空格容忍：前端从下拉取值，历史脚本可能写成小写
        assertDoesNotThrow(() -> CareerSectionUtils.validateFields(Map.of("intention", " postgrad ")));

        var ex = assertThrows(BusinessException.class,
                () -> CareerSectionUtils.validateFields(Map.of("intention", "考研")));
        assertEquals("career.intention", ex.getDetails().get(0).getField());
    }

    @Test
    void blankIntentionIsLeftToRequiredCheck() {
        // 留空由提交时的必填校验报 EC-42202；这里不重复报错，避免一次提交出现两条结论
        assertDoesNotThrow(() -> CareerSectionUtils.validateFields(Map.of()));
        assertDoesNotThrow(() -> CareerSectionUtils.validateFields(Map.of("intention", " ")));
    }

    @Test
    void graduationYearMatchesFourthAcademicYear() {
        // 2022 级四年制：第 4 学年为 2025-2026（起始年 = 入学年 + 3）
        assertTrue(CareerSectionUtils.isGraduationYear(2022, "2025-2026-2"));
        assertTrue(CareerSectionUtils.isGraduationYear(2022, "2025-2026-1"));
        assertFalse(CareerSectionUtils.isGraduationYear(2023, "2025-2026-2"));
        assertFalse(CareerSectionUtils.isGraduationYear(2022, "2024-2025-2"));
        assertFalse(CareerSectionUtils.isGraduationYear(2022, null));
        assertFalse(CareerSectionUtils.isGraduationYear(2022, "2025"));
    }
}
