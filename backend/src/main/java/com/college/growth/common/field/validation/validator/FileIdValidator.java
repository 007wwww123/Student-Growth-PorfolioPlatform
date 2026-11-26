package com.college.growth.common.field.validation.validator;

import com.college.growth.common.field.FieldRule;
import com.college.growth.common.field.FieldValues;
import com.college.growth.common.field.entity.ArcFieldDef;
import com.college.growth.common.field.validation.FieldValidator;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 附件校验（FILE_ID）：值为文件 ID（单个或数组），数量上限取 rule_json。
 *
 * <p>文件大小、类型、去 EXIF 由文件服务（S1）负责，本校验只保证「引用的是文件 ID」，
 * 不在这里读文件流，避免一次保存触发多次对象存储调用。</p>
 */
@Component
public class FileIdValidator implements FieldValidator {

    private static final Pattern FILE_ID = Pattern.compile("^\\d{1,19}$");

    @Override
    public String code() {
        return "FILE_ID";
    }

    @Override
    public Optional<String> validate(ArcFieldDef def, Object value) {
        List<String> ids = toIds(value);
        if (ids.isEmpty()) {
            return Optional.of("附件值应为文件 ID");
        }
        boolean allValid = ids.stream().allMatch(id -> FILE_ID.matcher(id).matches());
        if (!allValid) {
            return Optional.of("附件值应为文件 ID");
        }
        FieldRule rule = FieldRule.parse(def.getRuleJson());
        int max = rule.getMaxCount() == null ? 5 : rule.getMaxCount();
        if (ids.size() > max) {
            return Optional.of("附件数量不能超过 " + max + " 个");
        }
        return Optional.empty();
    }

    private List<String> toIds(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .map(FieldValues::asText)
                    .filter(item -> item != null && !item.isBlank())
                    .toList();
        }
        String text = FieldValues.asText(value);
        return text == null ? List.of() : List.of(text.trim());
    }
}
