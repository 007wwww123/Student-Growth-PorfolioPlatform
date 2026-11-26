package com.college.growth.common.field.validation;

import com.college.growth.common.field.entity.ArcFieldDef;
import java.util.Optional;

/**
 * 字段校验器（BR-F02）。
 *
 * <p>实现类以 {@link #code()} 注册到 {@link FieldValidatorRegistry}，字段定义里的
 * {@code validator} 指向该标识。校验器只处理非空值，必填判断由业务层统一负责，
 * 避免「必填」与「格式」两件事耦合。</p>
 */
public interface FieldValidator {

    /** 校验器标识，与 {@code arc_field_def.validator} 一致。 */
    String code();

    /**
     * 校验字段值。
     *
     * @param def   字段定义，提供 rule_json 与 options_json
     * @param value 非空字段值
     * @return 错误提示；通过时返回 {@link Optional#empty()}
     */
    Optional<String> validate(ArcFieldDef def, Object value);
}
