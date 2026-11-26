package com.college.growth.basic.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 敏感字段明文视图。
 *
 * <p>仅辅导员（或持临时授权的管理员）调用「查看完整」时返回，且该动作写审计日志（BR-D05）。
 * 明文不进列表接口、不进导出、不进日志。</p>
 */
@Data
@AllArgsConstructor
public class SensitiveFieldVO {

    /** 字段编码，如 basic.idCard。 */
    private String fieldCode;

    /** 字段显示名。 */
    private String fieldName;

    /** 完整明文值。 */
    private String value;
}
