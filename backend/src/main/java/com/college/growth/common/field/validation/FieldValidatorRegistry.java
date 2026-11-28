package com.college.growth.common.field.validation;

import com.college.growth.common.field.FieldValues;
import com.college.growth.common.field.entity.ArcFieldDef;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 字段校验器注册表。
 *
 * <p>启动时把全部 {@link FieldValidator} 按标识建索引，重复标识直接失败，避免配置指向不确定的实现。
 * 运行期遇到未注册标识时抛 {@link IllegalStateException}：这是配置错误，不能静默放行，
 * 否则字段会「看起来能存、实际没校验」。</p>
 */
@Component
public class FieldValidatorRegistry {

    private final Map<String, FieldValidator> validators;

    public FieldValidatorRegistry(List<FieldValidator> beans) {
        Map<String, FieldValidator> index = new HashMap<>();
        for (FieldValidator validator : beans) {
            FieldValidator previous = index.put(validator.code(), validator);
            if (previous != null) {
                throw new IllegalStateException("字段校验器标识重复: " + validator.code());
            }
        }
        this.validators = Map.copyOf(index);
    }

    /**
     * 已注册的校验器标识集合，供字段定义配置页展示与校验。
     */
    public Set<String> codes() {
        return validators.keySet();
    }

    /**
     * 校验非空字段值。
     *
     * @return 错误提示；为空值或校验通过时返回 empty
     */
    public Optional<String> validate(ArcFieldDef def, Object value) {
        if (FieldValues.isEmpty(value)) {
            return Optional.empty();
        }
        FieldValidator validator = validators.get(def.getValidator());
        if (validator == null) {
            throw new IllegalStateException("字段 " + def.getFieldCode() + " 引用了未注册的校验器: " + def.getValidator());
        }
        return validator.validate(def, value);
    }
}
