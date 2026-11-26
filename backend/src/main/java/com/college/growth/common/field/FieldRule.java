package com.college.growth.common.field;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 字段校验参数（对应 {@code arc_field_def.rule_json}）。
 *
 * <p>校验规则必须由配置驱动（BR-F02）：同一个校验器可以服务多个字段，
 * 差异只体现在这里，不在代码里写 {@code if ("basic.name".equals(code))}。</p>
 */
public final class FieldRule {

    private static final Logger log = LoggerFactory.getLogger(FieldRule.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final FieldRule EMPTY = new FieldRule(null, null, null, null, null);

    private final Integer minLen;

    private final Integer maxLen;

    private final Pattern pattern;

    private final Integer maxCount;

    private final Integer maxBytes;

    private FieldRule(Integer minLen, Integer maxLen, Pattern pattern, Integer maxCount, Integer maxBytes) {
        this.minLen = minLen;
        this.maxLen = maxLen;
        this.pattern = pattern;
        this.maxCount = maxCount;
        this.maxBytes = maxBytes;
    }

    /** 解析规则 JSON；为空或解析失败时返回空规则，由调用方按「仅基础校验」处理。 */
    public static FieldRule parse(String ruleJson) {
        if (ruleJson == null || ruleJson.isBlank()) {
            return EMPTY;
        }
        try {
            JsonNode node = MAPPER.readTree(ruleJson);
            Pattern pattern = node.hasNonNull("regex") ? Pattern.compile(node.get("regex").asText()) : null;
            return new FieldRule(
                    node.hasNonNull("minLen") ? node.get("minLen").asInt() : null,
                    node.hasNonNull("maxLen") ? node.get("maxLen").asInt() : null,
                    pattern,
                    node.hasNonNull("maxCount") ? node.get("maxCount").asInt() : null,
                    node.hasNonNull("maxBytes") ? node.get("maxBytes").asInt() : null);
        } catch (Exception ex) {
            log.warn("字段校验规则解析失败，按空规则处理: {}", ruleJson, ex);
            return EMPTY;
        }
    }

    public Integer getMinLen() {
        return minLen;
    }

    public Integer getMaxLen() {
        return maxLen;
    }

    public Pattern getPattern() {
        return pattern;
    }

    public Integer getMaxCount() {
        return maxCount;
    }

    public Integer getMaxBytes() {
        return maxBytes;
    }
}
