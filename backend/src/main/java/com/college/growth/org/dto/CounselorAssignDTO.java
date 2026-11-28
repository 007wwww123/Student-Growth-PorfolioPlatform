package com.college.growth.org.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 班级绑定辅导员入参。 */
@Data
public class CounselorAssignDTO {

    @NotNull(message = "请选择辅导员")
    private Long counselorUserId;
}
