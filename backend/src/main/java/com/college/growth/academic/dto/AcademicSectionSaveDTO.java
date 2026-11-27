package com.college.growth.academic.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/** 接受平铺字段和通用表单 fields，保留显式清空的字段。 */
@Getter
public class AcademicSectionSaveDTO {
    @Setter
    @Size(max = 16, message = "学年长度不能超过 16")
    private String academicYear;

    @NotEmpty(message = "没有需要保存的字段")
    private final Map<String, Object> fields = new LinkedHashMap<>();

    public void setFields(Map<String, Object> values) {
        fields.putAll(values);
    }

    @JsonAnySetter
    public void putField(String key, Object value) {
        fields.put(key, value);
    }
}
