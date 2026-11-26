package com.college.growth.common.field.validation.validator;

import com.college.growth.common.field.FieldValues;
import com.college.growth.common.field.entity.ArcFieldDef;
import com.college.growth.common.field.validation.FieldValidator;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 紧急联系电话校验（EMERGENCY_TEL）：手机或固话，二者命中其一即可。
 */
@Component
public class EmergencyTelValidator implements FieldValidator {

    private static final Pattern MOBILE = Pattern.compile("^1[3-9]\\d{9}$");

    private static final Pattern LANDLINE = Pattern.compile("^0\\d{2,3}-?\\d{7,8}$");

    @Override
    public String code() {
        return "EMERGENCY_TEL";
    }

    @Override
    public Optional<String> validate(ArcFieldDef def, Object value) {
        String text = FieldValues.asText(value);
        if (text == null) {
            return Optional.of("联系电话只接受文本");
        }
        String phone = text.trim();
        if (MOBILE.matcher(phone).matches() || LANDLINE.matcher(phone).matches()) {
            return Optional.empty();
        }
        return Optional.of("请填写手机号或带区号的固定电话，如 0571-88881234");
    }
}
