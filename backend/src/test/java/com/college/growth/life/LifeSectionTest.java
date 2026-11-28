package com.college.growth.life;

import com.college.growth.common.exception.BusinessException;
import com.college.growth.life.dto.LifeSectionSaveDTO;
import com.college.growth.life.util.LifeSectionUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 生活心理板块的入参兼容与板块级规则。
 *
 * <p>只测本板块独有的判断：DTO 的信封 / 平铺双写法、压力自评区间、压力来源条数、
 * 关注标记派生。字段渲染与状态机属公共能力，已在 {@code SectionFormServiceTest} 覆盖。</p>
 */
class LifeSectionTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void acceptsEnvelopeAndFlatBodiesAndPreservesExplicitClear() throws Exception {
        for (String body : List.of(
                "{\"orgRole\":null}",
                "{\"fields\":{\"orgRole\":null}}",
                "{\"academicYear\":\"2025-2026-1\",\"fields\":{\"orgRole\":null}}")) {
            LifeSectionSaveDTO dto = json.readValue(body, LifeSectionSaveDTO.class);
            assertEquals(Set.of("orgRole"), dto.getFields().keySet());
            assertNull(dto.getFields().get("orgRole"));
        }
    }

    @Test
    void nullFieldsBodyLeavesNoFields() throws Exception {
        LifeSectionSaveDTO dto = json.readValue("{\"fields\":null}", LifeSectionSaveDTO.class);
        assertEquals(0, dto.getFields().size());
    }

    @Test
    void stressScoreMustBeIntegerBetweenOneAndFive() {
        assertDoesNotThrow(() -> LifeSectionUtils.validateFields(Map.of("stressScore", "1")));
        assertDoesNotThrow(() -> LifeSectionUtils.validateFields(Map.of("stressScore", 5)));

        for (Object invalid : List.of("0", "6", "3.5", "high", -1)) {
            var ex = assertThrows(BusinessException.class,
                    () -> LifeSectionUtils.validateFields(Map.of("stressScore", invalid)),
                    "应拒绝压力自评：" + invalid);
            assertEquals("life.stressScore", ex.getDetails().get(0).getField());
        }
    }

    @Test
    void stressSourceAcceptsAtMostThreeNonBlankItems() {
        assertDoesNotThrow(() -> LifeSectionUtils.validateFields(
                Map.of("stressSource", List.of("学业", "就业", "人际"))));

        var tooMany = assertThrows(BusinessException.class, () -> LifeSectionUtils.validateFields(
                Map.of("stressSource", List.of("学业", "就业", "人际", "家庭"))));
        assertEquals("life.stressSource", tooMany.getDetails().get(0).getField());

        var blankItem = assertThrows(BusinessException.class, () -> LifeSectionUtils.validateFields(
                Map.of("stressSource", List.of("学业", " "))));
        assertEquals("life.stressSource", blankItem.getDetails().get(0).getField());
    }

    @Test
    void multiSelectFieldsRejectScalarValues() {
        // 多选字段存数组；前端把 "学业,就业" 整串提交时必须报错，而不是静默存成数组里的一个元素
        var scalar = assertThrows(BusinessException.class, () -> LifeSectionUtils.validateFields(
                Map.of("aidSource", "国家助学金")));
        assertEquals("life.aidSource", scalar.getDetails().get(0).getField());
    }

    @Test
    void aidSourceHasNoCountLimitButRequiresText() {
        assertDoesNotThrow(() -> LifeSectionUtils.validateFields(Map.of(
                "aidSource", List.of("国家助学金", "勤工助学", "助学贷款", "临时困难补助"))));
        assertThrows(BusinessException.class, () -> LifeSectionUtils.validateFields(
                Map.of("aidSource", List.of("国家助学金", 3))));
    }

    @Test
    void careFlagIsDerivedFromMoodAndStress() {
        assertEquals(1, LifeSectionUtils.deriveCareFlag(4, "LOW"));
        assertEquals(1, LifeSectionUtils.deriveCareFlag(5, "LOW"));
        assertEquals(0, LifeSectionUtils.deriveCareFlag(3, "LOW"));
        assertEquals(0, LifeSectionUtils.deriveCareFlag(5, "FAIR"));
        assertEquals(0, LifeSectionUtils.deriveCareFlag(null, "LOW"));
    }

    @Test
    void parseStressScoreToleratesStoredTextAndIgnoresNonNumeric() {
        assertEquals(3, LifeSectionUtils.parseStressScore("3"));
        assertEquals(3, LifeSectionUtils.parseStressScore(3));
        assertNull(LifeSectionUtils.parseStressScore("3.5"));
        assertNull(LifeSectionUtils.parseStressScore(""));
        assertNull(LifeSectionUtils.parseStressScore(null));
    }

    @Test
    void fieldsSnapshotWithoutRulesPassesValidation() {
        // 只填必填项、没有多选与压力来源时不能误报
        Map<String, Object> filled = new LinkedHashMap<>();
        filled.put("stressScore", "2");
        filled.put("mood", "GOOD");
        assertDoesNotThrow(() -> LifeSectionUtils.validateFields(filled));
    }
}
