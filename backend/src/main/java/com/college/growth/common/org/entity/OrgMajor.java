package com.college.growth.common.org.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 专业（{@code org_major}，脚本 16）。 */
@Data
@TableName("org_major")
public class OrgMajor {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long collegeId;

    private String majorCode;

    private String majorName;

    /** 1 启用 / 0 停用。 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
