package com.college.growth.common.field.validation.validator;

import com.college.growth.common.field.FieldOption;
import com.college.growth.common.field.FieldValues;
import com.college.growth.common.field.entity.ArcFieldDef;
import com.college.growth.common.field.validation.FieldValidator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 枚举校验（ENUM）：值必须命中 {@code options_json}（BR-F04）。
 *
 * <p>选项为空视为配置错误，直接拒绝写入，避免「没有选项」变成「什么都能存」。</p>
 */
@Component
public class EnumValidator implements FieldValidator {

    @Override
    public String code() {
        return "ENUM";
    }

    @Override
    public Optional<String> validate(ArcFieldDef def, Object value) {
        List<FieldOption> options = FieldOption.parse(def.getOptionsJson());
        if (options.isEmpty()) {
            return Optional.of("该字段的选项未配置，请联系管理员");
        }
        String text = FieldValues.asText(value);
        if (text == null) {
            return Optional.of("该字段只接受单个选项值");
        }
        String candidate = text.trim();
        boolean hit = options.stream().anyMatch(option -> option.value().equals(candidate));
        if (!hit) {
            return Optional.of("取值不在可选范围内");
        }
        return Optional.empty();
    }
}
