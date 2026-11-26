package com.college.growth.common.field;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 枚举选项（对应 {@code arc_field_def.options_json}）。
 *
 * <p>支持两种写法：{@code ["A","B"]} 与 {@code [{"value":"A","label":"甲"}]}。
 * 落库存 {@code value}，界面展示 {@code label}；历史值停用后仍按原 label 展示（BR-F04）。</p>
 *
 * @param value 存库值
 * @param label 展示文案
 */
public record FieldOption(String value, String label) {

    private static final Logger log = LoggerFactory.getLogger(FieldOption.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 解析选项配置；为空或格式错误返回空列表（ENUM 校验器会据此判定为配置缺失）。 */
    public static List<FieldOption> parse(String optionsJson) {
        List<FieldOption> options = new ArrayList<>();
        if (optionsJson == null || optionsJson.isBlank()) {
            return options;
        }
        try {
            JsonNode node = MAPPER.readTree(optionsJson);
            if (!node.isArray()) {
                return options;
            }
            for (JsonNode item : node) {
                if (item.isTextual()) {
                    options.add(new FieldOption(item.asText(), item.asText()));
                } else if (item.hasNonNull("value")) {
                    String label = item.hasNonNull("label") ? item.get("label").asText() : item.get("value").asText();
                    options.add(new FieldOption(item.get("value").asText(), label));
                }
            }
        } catch (Exception ex) {
            log.warn("枚举选项解析失败: {}", optionsJson, ex);
        }
        return options;
    }

    /** 取全部存库值，供校验器做命中判断。 */
    public static List<String> valuesOf(List<FieldOption> options) {
        return options.stream().map(FieldOption::value).toList();
    }
}
