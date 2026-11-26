package com.college.growth.common.field.validation.validator;

import com.college.growth.common.field.FieldRule;
import com.college.growth.common.field.FieldValues;
import com.college.growth.common.field.entity.ArcFieldDef;
import com.college.growth.common.field.validation.FieldValidator;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 文本长度校验（TEXT_LEN）：按 rule_json 的 minLen / maxLen 判断，按去除首尾空格后的字符数计。
 */
@Component
public class TextLengthValidator implements FieldValidator {

    @Override
    public String code() {
        return "TEXT_LEN";
    }

    @Override
    public Optional<String> validate(ArcFieldDef def, Object value) {
        String text = FieldValues.asText(value);
        if (text == null) {
            return Optional.of("该字段只接受文本");
        }
        FieldRule rule = FieldRule.parse(def.getRuleJson());
        int length = text.trim().length();
        if (rule.getMinLen() != null && length < rule.getMinLen()) {
            return Optional.of("长度不能少于 " + rule.getMinLen() + " 个字符");
        }
        if (rule.getMaxLen() != null && length > rule.getMaxLen()) {
            return Optional.of("长度不能超过 " + rule.getMaxLen() + " 个字符");
        }
        return Optional.empty();
    }
}
