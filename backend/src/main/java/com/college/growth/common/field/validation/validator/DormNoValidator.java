package com.college.growth.common.field.validation.validator;

import com.college.growth.common.field.FieldRule;
import com.college.growth.common.field.FieldValues;
import com.college.growth.common.field.entity.ArcFieldDef;
import com.college.growth.common.field.validation.FieldValidator;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 宿舍号校验（DORM_NO）：格式由 rule_json 的正则给出，默认 {@code 3-412} 这种「楼栋-房间」写法。
 */
@Component
public class DormNoValidator implements FieldValidator {

    @Override
    public String code() {
        return "DORM_NO";
    }

    @Override
    public Optional<String> validate(ArcFieldDef def, Object value) {
        String text = FieldValues.asText(value);
        if (text == null) {
            return Optional.of("宿舍号只接受文本");
        }
        FieldRule rule = FieldRule.parse(def.getRuleJson());
        if (rule.getPattern() != null && !rule.getPattern().matcher(text.trim()).matches()) {
            return Optional.of("宿舍号格式应为「楼栋-房间」，如 3-412");
        }
        return Optional.empty();
    }
}
