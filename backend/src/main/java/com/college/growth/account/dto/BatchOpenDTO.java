package com.college.growth.account.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

/** 批量开通账号入参：按班级为已有学生主数据但没有账号的学生建号。 */
@Data
public class BatchOpenDTO {

    @NotEmpty(message = "请至少选择一个班级")
    private List<String> classCodes;
}
