package com.college.growth.interest;

import com.college.growth.common.exception.BusinessException;
import com.college.growth.interest.dto.InterestSectionSaveDTO;
import com.college.growth.interest.util.InterestSectionUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InterestSectionTest {
    @Test
    void acceptsFlatAndWrappedUpdatesAndPreservesExplicitClear() throws Exception {
        var json = new ObjectMapper();
        for (String body : List.of("{\"ideal\":null}", "{\"fields\":{\"ideal\":null}}")) {
            var dto = json.readValue(body, InterestSectionSaveDTO.class);
            assertEquals(Set.of("ideal"), dto.getFields().keySet());
            assertNull(dto.getFields().get("ideal"));
        }
    }

    @Test
    void otherHobbyRequiresNonblankStoredDescription() {
        var hobbies = List.of("OTHER");
        assertDoesNotThrow(() -> InterestSectionUtils.validateFields(Map.of("hobbies", hobbies, "hobbyDesc", "观星")));
        var ex = assertThrows(BusinessException.class, () ->
                InterestSectionUtils.validateFields(Map.of("hobbies", hobbies, "hobbyDesc", "<b></b>")));
        assertEquals("interest.hobbyDesc", ex.getDetails().get(0).getField());
    }

    @Test
    void invalidArrayElementIsRejected() {
        var ex = assertThrows(BusinessException.class, () ->
                InterestSectionUtils.validateFields(Map.of("hobbies", List.of("运动", false))));
        assertEquals("interest.hobbies", ex.getDetails().get(0).getField());
    }
}
