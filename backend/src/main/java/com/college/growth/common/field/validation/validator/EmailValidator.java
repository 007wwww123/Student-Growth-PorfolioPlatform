package com.college.growth.common.field.validation.validator;

import com.college.growth.common.field.FieldRule;
import com.college.growth.common.field.FieldValues;
import com.college.growth.common.field.entity.ArcFieldDef;
import com.college.growth.common.field.validation.FieldValidator;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 邮箱校验（EMAIL）：标准邮箱格式，长度上限取 rule_json，默认 128。
 */
@Component
public class EmailValidator implements FieldValidator {

    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    @Override
    public String code() {
        return "EMAIL";
    }

    @Override
    public Optional<String> validate(ArcFieldDef def, Object value) {
        String text = FieldValues.asText(value);
        if (text == null || !EMAIL.matcher(text.trim()).matches()) {
            return Optional.of("邮箱格式不正确");
        }
        FieldRule rule = FieldRule.parse(def.getRuleJson());
        int max = rule.getMaxLen() == null ? 128 : rule.getMaxLen();
        if (text.trim().length() > max) {
            return Optional.of("邮箱长度不能超过 " + max + " 个字符");
        }
        return Optional.empty();
    }
}
