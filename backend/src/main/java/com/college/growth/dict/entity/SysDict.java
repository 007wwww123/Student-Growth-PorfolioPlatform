package com.college.growth.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 字典类型（{@code sys_dict}，脚本 26）。 */
@Data
@TableName("sys_dict")
public class SysDict {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String dictCode;

    private String dictName;

    /** 绑定的字段编码，可空。 */
    private String fieldCode;

    private String remark;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
