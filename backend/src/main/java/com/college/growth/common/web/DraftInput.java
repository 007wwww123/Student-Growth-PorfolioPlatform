package com.college.growth.common.web;

import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.result.ResultCode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Preserve omitted fields separately from explicit nulls in a partial draft update. */
public class DraftInput {
    private Map<String, Object> submittedFields = Map.of();

    public static String bind(DraftInput dto, Map<String, Object> body) {
        Map<String, Object> fields = new LinkedHashMap<>(body);
        Object year = fields.remove("academicYear");
        if (!(year instanceof String text) || text.isBlank() || text.length() > 16) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "学年格式错误");
        }
        dto.submittedFields = Collections.unmodifiableMap(fields);
        return text;
    }

    public Map<String, Object> submittedFields() {
        return submittedFields;
    }
}
