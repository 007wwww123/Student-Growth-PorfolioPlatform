package com.college.growth.config.field.vo;

import java.util.List;
import lombok.Data;

/** 字段定义视图（管理员配置页）。 */
@Data
public class FieldDefVO {

    private Long id;

    private String sectionCode;

    private String sectionName;

    private String fieldCode;

    private String fieldName;

    private String fieldType;

    private String fieldTypeLabel;

    private Integer required;

    private String source;

    private String sourceLabel;

    private String sourceField;

    private List<String> visibleRoles;

    private List<String> editableRoles;

    private List<String> defaultEditableRoles;

    private String validator;

    private String ruleJson;

    private String optionsJson;

    /** 解析后的选项，值为空数组表示非枚举字段。 */
    private List<Option> options;

    private Integer sensitiveLevel;

    private Integer sortNo;

    private Integer status;

    private String remark;

    /** 是否已有字段值（决定类型能否修改）。 */
    private boolean hasValues;

    /** 是否系统维护字段（来源 SIS/SYS 或校验器 DERIVED）。 */
    private boolean systemOwned;

    @Data
    public static class Option {

        private String value;

        private String label;

        public Option(String value, String label) {
            this.value = value;
            this.label = label;
        }
    }
}
