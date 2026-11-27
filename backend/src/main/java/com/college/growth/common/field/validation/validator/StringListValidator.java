package com.college.growth.common.field.validation.validator;

import com.college.growth.common.field.FieldRule;
import com.college.growth.common.field.entity.ArcFieldDef;
import com.college.growth.common.field.validation.FieldValidator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class StringListValidator implements FieldValidator {
    @Override
    public String code() {
        return "STRING_LIST";
    }

    @Override
    public Optional<String> validate(ArcFieldDef def, Object value) {
        if (!(value instanceof List<?> entries)) {
            return Optional.of("请提供文本列表");
        }
        Integer limit = FieldRule.parse(def.getRuleJson()).getMaxCount();
        if (limit != null && entries.size() > limit) {
            return Optional.of("最多选择 " + limit + " 项");
        }
        var seen = new HashSet<String>();
        for (Object entry : entries) {
            if (!(entry instanceof String text) || text.isBlank() || !seen.add(text.strip())) {
                return Optional.of("列表项须为非空且不重复的文本");
            }
        }
        return Optional.empty();
    }
}
