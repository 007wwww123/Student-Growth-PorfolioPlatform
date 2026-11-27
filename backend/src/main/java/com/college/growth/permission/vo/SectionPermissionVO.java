package com.college.growth.permission.vo;

import java.util.List;
import lombok.Data;

/**
 * 一个板块的字段权限清单。
 *
 * <p>统计位（学生可编辑数 / 老师可编辑数 / 只读数）由服务端算好，
 * 前端不再自行遍历，避免两端对「只读」的口径不一致。</p>
 */
@Data
public class SectionPermissionVO {

    /** 板块编码，大写，如 BASIC。 */
    private String sectionCode;

    /** 板块中文名。 */
    private String sectionName;

    /** 字段权限清单，按表单顺序。 */
    private List<FieldPermissionVO> fields;

    /** 当前配置下学生可编辑的字段数。 */
    private Integer studentEditable;

    /** 当前配置下老师可编辑的字段数。 */
    private Integer counselorEditable;

    /** 当前配置下任何人都不可编辑的字段数。 */
    private Integer readonly;

    /** 已偏离默认配置的字段数。 */
    private Integer customized;
}
