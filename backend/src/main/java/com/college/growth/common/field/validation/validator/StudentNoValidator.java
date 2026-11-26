package com.college.growth.common.field.validation.validator;

import com.college.growth.common.field.FieldRule;
import com.college.growth.common.field.FieldValues;
import com.college.growth.common.field.entity.ArcFieldDef;
import com.college.growth.common.field.validation.FieldValidator;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 学号校验（STUDENT_NO）：纯数字，长度取 rule_json，默认 8–12 位。
 */
@Component
public class StudentNoValidator implements FieldValidator {

    private static final Pattern DIGITS = Pattern.compile("^\\d+$");

    @Override
    public String code() {
        return "STUDENT_NO";
    }

    @Override
    public Optional<String> validate(ArcFieldDef def, Object value) {
        String text = FieldValues.asText(value);
        if (text == null || !DIGITS.matcher(text.trim()).matches()) {
            return Optional.of("学号必须为纯数字");
        }
        FieldRule rule = FieldRule.parse(def.getRuleJson());
        int min = rule.getMinLen() == null ? 8 : rule.getMinLen();
        int max = rule.getMaxLen() == null ? 12 : rule.getMaxLen();
        int length = text.trim().length();
        if (length < min || length > max) {
            return Optional.of("学号应为 " + min + "–" + max + " 位数字");
        }
        return Optional.empty();
    }
}
