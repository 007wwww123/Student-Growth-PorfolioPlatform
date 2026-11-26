package com.college.growth.common.field.validation.validator;

import com.college.growth.common.field.FieldRule;
import com.college.growth.common.field.FieldValues;
import com.college.growth.common.field.entity.ArcFieldDef;
import com.college.growth.common.field.validation.FieldValidator;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 姓名校验（PERSON_NAME）：汉字、字母、间隔号、空格，不得含数字。
 *
 * <p>少数民族姓名的间隔号（·）与空格是合法字符，不能简单用「纯汉字」判断。</p>
 */
@Component
public class PersonNameValidator implements FieldValidator {

    private static final Pattern ALLOWED = Pattern.compile("^[\\p{IsHan}A-Za-z·\\u00B7 ]+$");

    private static final Pattern DIGIT = Pattern.compile("\\d");

    @Override
    public String code() {
        return "PERSON_NAME";
    }

    @Override
    public Optional<String> validate(ArcFieldDef def, Object value) {
        String text = FieldValues.asText(value);
        if (text == null) {
            return Optional.of("姓名只接受文本");
        }
        String name = text.trim();
        FieldRule rule = FieldRule.parse(def.getRuleJson());
        int min = rule.getMinLen() == null ? 2 : rule.getMinLen();
        int max = rule.getMaxLen() == null ? 32 : rule.getMaxLen();
        if (name.length() < min || name.length() > max) {
            return Optional.of("姓名长度应为 " + min + "–" + max + " 个字符");
        }
        if (DIGIT.matcher(name).find()) {
            return Optional.of("姓名不能包含数字");
        }
        if (!ALLOWED.matcher(name).matches()) {
            return Optional.of("姓名只能包含汉字、字母、间隔号与空格");
        }
        return Optional.empty();
    }
}
