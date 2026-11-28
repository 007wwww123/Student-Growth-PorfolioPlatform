package com.college.growth.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 字典项（{@code sys_dict_item}，脚本 26）。 */
@Data
@TableName("sys_dict_item")
public class SysDictItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long dictId;

    private String itemValue;

    private String itemLabel;

    private Integer sortNo;

    /** 1 启用 / 0 停用（历史保留）。 */
    private Integer status;

    private Integer builtin;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
