package com.college.growth.permission.service;

import com.college.growth.permission.dto.PermissionSaveDTO;
import com.college.growth.permission.vo.PermissionMatrixVO;
import com.college.growth.permission.vo.PermissionSaveResultVO;

/**
 * 字段编辑权限配置（管理员模式）。
 *
 * <p>权限只落在 {@code arc_field_def.editable_roles}：这是六个板块表单渲染、
 * 保存校验共同读取的唯一来源，因此保存后立即生效，不需要刷新缓存或重启服务。</p>
 */
public interface FieldPermissionService {

    /** 读取六大板块的字段权限矩阵。 */
    PermissionMatrixVO matrix();

    /**
     * 保存某板块的字段权限。
     *
     * @param sectionCode 板块编码，大小写均可
     * @param dto         本板块整批字段权限
     * @return 实际变更数与保存后的快照
     */
    PermissionSaveResultVO save(String sectionCode, PermissionSaveDTO dto);

    /**
     * 恢复某板块的默认权限（初始化时由字段来源推导的口径）。
     *
     * @param sectionCode 板块编码，大小写均可
     */
    PermissionSaveResultVO reset(String sectionCode);
}
