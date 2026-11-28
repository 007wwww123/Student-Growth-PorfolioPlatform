package com.college.growth.academic.record.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Data;

/** 竞赛科研新增/修改入参。 */
@Data
public class EventSaveDTO {

    @NotBlank(message = "类型不能为空")
    private String eventType;

    @NotBlank(message = "名称不能为空")
    @Size(min = 4, max = 255, message = "名称 4-255 字")
    private String title;

    @Size(max = 64, message = "角色不超过 64 字")
    private String roleName;

    @NotBlank(message = "级别不能为空")
    private String level;

    @NotNull(message = "开始日期不能为空")
    private LocalDate startDate;

    private LocalDate endDate;

    @Size(max = 500, message = "描述不超过 500 字")
    private String description;

    private Long evidenceFileId;
}
