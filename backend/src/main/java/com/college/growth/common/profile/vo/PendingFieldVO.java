package com.college.growth.common.profile.vo;

import lombok.Data;

/**
 * 待确认字段处理结果。
 *
 * <p>确认后返回生效值；敏感字段返回脱敏值，需要明文仍走各板块的「查看完整」接口
 * （会写审计），不能借确认接口绕过。</p>
 */
@Data
public class PendingFieldVO {

    private String fieldCode;

    private String fieldName;

    /** 处理后的对外值（敏感字段为脱敏值）。 */
    private Object value;

    /** 处理后的确认状态：APPROVED / REJECTED。 */
    private String confirmStatus;
}
