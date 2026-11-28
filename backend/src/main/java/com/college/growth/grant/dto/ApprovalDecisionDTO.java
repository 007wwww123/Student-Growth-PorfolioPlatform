package com.college.growth.grant.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 审批意见（通过可为空，拒绝必填）。 */
@Data
public class ApprovalDecisionDTO {

    @Size(max = 500, message = "审批意见最多 500 字")
    private String remark;
}
