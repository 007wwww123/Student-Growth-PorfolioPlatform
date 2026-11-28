package com.college.growth.common.profile;

import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.field.FieldTypeEnum;
import com.college.growth.common.field.FieldValues;
import com.college.growth.common.field.entity.ArcFieldDef;
import com.college.growth.common.profile.entity.ArcProfileItem;
import com.college.growth.common.result.ResultCode;
import com.college.growth.common.security.IdCardCrypto;
import com.college.growth.common.util.TextUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 字段值编解码：在「字段定义的存储形态」与「接口上的 JSON 值」之间转换。
 *
 * <p>六个板块与待确认区、审核页读取值都必须走同一套转换，避免出现
 * 「某个板块按字符串存、另一个按数组存」的历史包袱（需求文档 4.1.1：
 * FILE 一律存 file_id 数组，MULTI_SELECT 一律存数组）。</p>
 */
public final class FieldValueCodec {

    private static final Logger log = LoggerFactory.getLogger(FieldValueCodec.class);

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String ID_CARD_FIELD = "basic.idCard";

    private FieldValueCodec() {
    }

    /** 读取正式值；无记录返回 null。 */
    public static Object read(ArcFieldDef def, ArcProfileItem item) {
        if (item == null) {
            return null;
        }
        if (FieldTypeEnum.fromCode(def.getFieldType()).isJsonStored()) {
            return parseJson(item.getValueJson());
        }
        return plainText(def, item.getValueText());
    }

    /** 读取待确认值；无记录返回 null。 */
    public static Object readPending(ArcFieldDef def, ArcProfileItem item) {
        if (item == null) {
            return null;
        }
        if (FieldTypeEnum.fromCode(def.getFieldType()).isJsonStored()) {
            return parseJson(item.getPendingJson());
        }
        return plainText(def, item.getPendingText());
    }

    /** 写入正式值，并清掉另一种存储列，避免两列同时有值。 */
    public static void write(ArcProfileItem item, ArcFieldDef def, Object value) {
        if (FieldTypeEnum.fromCode(def.getFieldType()).isJsonStored()) {
            item.setValueJson(toJson(value));
            item.setValueText(null);
        } else {
            item.setValueText(storedText(def, toText(value)));
            item.setValueJson(null);
        }
    }

    /** 写入待确认值。 */
    public static void writePending(ArcProfileItem item, ArcFieldDef def, Object value) {
        // JSON null identifies a deliberate clear; legacy first fills left both candidate columns SQL NULL.
        if (value == null) {
            item.setPendingText(null);
            item.setPendingJson("null");
            return;
        }
        if (FieldTypeEnum.fromCode(def.getFieldType()).isJsonStored()) {
            item.setPendingJson(toJson(value));
            item.setPendingText(null);
        } else {
            item.setPendingText(storedText(def, toText(value)));
            item.setPendingJson(null);
        }
    }

    private static String storedText(ArcFieldDef def, String value) {
        return ID_CARD_FIELD.equals(def.getFieldCode()) ? IdCardCrypto.encrypt(value) : value;
    }

    private static String plainText(ArcFieldDef def, String value) {
        return ID_CARD_FIELD.equals(def.getFieldCode()) ? IdCardCrypto.decrypt(value) : value;
    }

    /** 清空待确认区：确认或驳回后都必须调用，否则下次修改会被误判为「已有待确认值」。 */
    public static void clearPending(ArcProfileItem item) {
        item.setPendingText(null);
        item.setPendingJson(null);
    }

    /** 两个值按存储形态比较，用于判断「学生提交的是否与原值相同」。 */
    public static boolean same(ArcFieldDef def, Object left, Object right) {
        if (FieldTypeEnum.fromCode(def.getFieldType()).isJsonStored()) {
            return Objects.equals(toJson(left), toJson(right));
        }
        return Objects.equals(toText(left), toText(right));
    }

    /** 转存 JSON：字符串按单元素数组存，保证 FILE 字段「传文件 ID」与「传数组」落到库里形态一致。 */
    public static String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof String text) {
                return JSON.writeValueAsString(List.of(text.trim()));
            }
            return JSON.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.FIELD_VALIDATION_FAILED, "字段值格式不正确");
        }
    }

    /** 转文本：统一清洗不可见字符与首尾空白。 */
    public static String toText(Object value) {
        String text = FieldValues.asText(value);
        if (text == null) {
            return null;
        }
        return TextUtils.normalize(TextUtils.sanitize(text));
    }

    private static Object parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(json, Object.class);
        } catch (Exception ex) {
            log.warn("字段 JSON 值解析失败，按原文返回: {}", json);
            return json;
        }
    }
}
