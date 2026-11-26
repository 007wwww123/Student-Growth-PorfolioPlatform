package com.college.growth.common.field.validation.validator;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.college.growth.common.field.FieldValues;
import com.college.growth.common.field.entity.ArcFieldDef;
import com.college.growth.common.field.validation.FieldValidator;
import com.college.growth.common.org.entity.OrgClass;
import com.college.growth.common.org.mapper.OrgClassMapper;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 班级引用校验（CLASS_REF）：值必须是 {@code org_class} 中在读的班级。
 *
 * <p>同时接受班级编码（RJ2401）与班级名称（软件2401），但落库统一存编码，
 * 避免班级改名后历史值对不上。</p>
 */
@Component
public class ClassRefValidator implements FieldValidator {

    private final OrgClassMapper orgClassMapper;

    public ClassRefValidator(OrgClassMapper orgClassMapper) {
        this.orgClassMapper = orgClassMapper;
    }

    @Override
    public String code() {
        return "CLASS_REF";
    }

    @Override
    public Optional<String> validate(ArcFieldDef def, Object value) {
        String text = FieldValues.asText(value);
        if (text == null) {
            return Optional.of("班级只接受文本");
        }
        String candidate = text.trim();
        Long count = orgClassMapper.selectCount(Wrappers.<OrgClass>lambdaQuery()
                .eq(OrgClass::getStatus, 1)
                .and(wrapper -> wrapper.eq(OrgClass::getClassCode, candidate)
                        .or()
                        .eq(OrgClass::getClassName, candidate)));
        if (count == null || count == 0) {
            return Optional.of("班级不存在或已撤销");
        }
        return Optional.empty();
    }
}
