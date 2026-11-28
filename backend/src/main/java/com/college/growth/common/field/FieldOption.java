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
 * <p>支持两种写法：{@code ["A","B"]} 与 {@code [{"value":"A","label":"甲"}]}；
 * 字典停用项写成 {@code {"value":"摄影","label":"摄影","disabled":true}}，
 * 新填报校验拒绝、历史值仍按 label 展示。</p>
 */
public record FieldOption(String value, String label, boolean disabled) {

    private static final Logger log = LoggerFactory.getLogger(FieldOption.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public FieldOption(String value, String label) {
        this(value, label, false);
    }

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
                    boolean disabled = item.path("disabled").asBoolean(false)
                            || (item.has("status") && item.get("status").asInt(1) == 0);
                    options.add(new FieldOption(item.get("value").asText(), label, disabled));
                }
            }
        } catch (Exception ex) {
            log.warn("枚举选项解析失败: {}", optionsJson, ex);
        }
        return options;
    }

    /** 可取值的 value 列表；停用项不参与新值校验。 */
    public static List<String> valuesOf(List<FieldOption> options) {
        return options.stream().filter(option -> !option.disabled()).map(FieldOption::value).toList();
    }
}
