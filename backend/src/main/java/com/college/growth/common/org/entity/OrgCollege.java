package com.college.growth.common.org.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 学院（{@code org_college}，脚本 16）。 */
@Data
@TableName("org_college")
public class OrgCollege {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String collegeCode;

    private String collegeName;

    /** 1 启用 / 0 停用。 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
