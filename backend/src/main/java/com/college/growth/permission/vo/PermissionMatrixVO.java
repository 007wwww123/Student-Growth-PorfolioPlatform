package com.college.growth.permission.vo;

import java.util.List;
import lombok.Data;

/**
 * 六大板块的权限矩阵，一次返回，供管理页做板块切换。
 *
 * <p>矩阵只读字段定义，不读取任何学生档案内容——管理员配置权限的过程
 * 不应接触业务数据。</p>
 */
@Data
public class PermissionMatrixVO {

    /** 板块顺序与需求文档 4.2–4.7 一致。 */
    private List<SectionPermissionVO> sections;
}
