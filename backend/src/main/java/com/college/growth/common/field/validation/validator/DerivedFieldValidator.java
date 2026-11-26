package com.college.growth.common.field.validation.validator;

import com.college.growth.common.field.entity.ArcFieldDef;
import com.college.growth.common.field.validation.FieldValidator;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 系统推导字段（DERIVED）：如专业由班级推导，不接受任何角色独立提交。
 *
 * <p>如果这里放行，学生改一个 classId 就能顺带改专业，推导规则形同虚设。</p>
 */
@Component
public class DerivedFieldValidator implements FieldValidator {

    /**
     * 校验器标识。
     *
     * <p>除了拒绝写入之外，完成度统计、必填校验也要靠它识别「不落库的推导字段」，
     * 因此对外暴露常量，避免各处再抄一遍字符串。</p>
     */
    public static final String CODE = "DERIVED";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Optional<String> validate(ArcFieldDef def, Object value) {
        return Optional.of("该字段由系统推导，不能直接填写");
    }
}
