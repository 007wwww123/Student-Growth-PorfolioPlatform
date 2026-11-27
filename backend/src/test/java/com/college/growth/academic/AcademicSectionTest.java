package com.college.growth.academic;

import com.college.growth.academic.dto.AcademicSectionSaveDTO;
import com.college.growth.academic.util.AcademicSectionUtils;
import com.college.growth.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AcademicSectionTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void acceptsBothSaveShapesAndKeepsExplicitClears() throws Exception {
        var flat = json.readValue("{\"goalLong\":null}", AcademicSectionSaveDTO.class);
        var nested = json.readValue("{\"fields\":{\"goalLong\":null}}", AcademicSectionSaveDTO.class);
        assertEquals(flat.getFields(), nested.getFields());
        assertEquals(1, flat.getFields().size());
        assertTrue(flat.getFields().containsKey("goalLong"));
        assertNull(flat.getFields().get("goalLong"));
    }

    @Test
    void checksForOverlappingSubjectsAfterWhitespaceNormalization() {
        var error = assertThrows(BusinessException.class, () -> AcademicSectionUtils.validateFields(
                Map.of("strongSubjects", List.of(" 数学"), "weakSubjects", List.of("数学"))));
        assertEquals("academic.weakSubjects", error.getDetails().get(0).getField());
        assertDoesNotThrow(() -> AcademicSectionUtils.validateFields(
                Map.of("strongSubjects", List.of("数学"), "weakSubjects", List.of("英语"))));
    }
}
