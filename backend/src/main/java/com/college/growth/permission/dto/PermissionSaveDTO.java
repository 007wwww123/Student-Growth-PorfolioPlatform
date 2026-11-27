package com.college.growth.permission.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

/**
 * 保存某板块的字段权限。
 *
 * <p>按板块整批提交而不是逐字段提交：一次保存要么全部生效，要么全部不生效，
 * 避免管理员看到「一半新权限、一半旧权限」的中间态。</p>
 */
@Data
public class PermissionSaveDTO {

    /** 本板块需要保存的字段权限项。 */
    @NotEmpty(message = "至少需要提交一个字段")
    @Valid
    private List<FieldPermissionDTO> fields;
}
