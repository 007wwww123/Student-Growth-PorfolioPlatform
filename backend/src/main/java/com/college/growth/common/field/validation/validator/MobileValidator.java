package com.college.growth.common.field.validation.validator;

import com.college.growth.common.field.FieldValues;
import com.college.growth.common.field.entity.ArcFieldDef;
import com.college.growth.common.field.validation.FieldValidator;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 手机号校验（MOBILE）：11 位，1 开头，第二位 3–9。
 */
@Component
public class MobileValidator implements FieldValidator {

    private static final Pattern MOBILE = Pattern.compile("^1[3-9]\\d{9}$");

    @Override
    public String code() {
        return "MOBILE";
    }

    @Override
    public Optional<String> validate(ArcFieldDef def, Object value) {
        String text = FieldValues.asText(value);
        if (text == null || !MOBILE.matcher(text.trim()).matches()) {
            return Optional.of("手机号应为 11 位数字，以 1 开头");
        }
        return Optional.empty();
    }
}
