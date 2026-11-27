package com.college.growth.permission.vo;

import lombok.Data;

/**
 * 权限保存结果。
 *
 * <p>返回保存后的板块快照，前端直接替换本地状态即可，不需要再发一次 GET；
 * {@code changed} 表示真正发生变化的字段数，未变化的字段不计入审计。</p>
 */
@Data
public class PermissionSaveResultVO {

    /** 实际更新的字段数。 */
    private Integer changed;

    /** 保存后的板块权限快照。 */
    private SectionPermissionVO section;
}
