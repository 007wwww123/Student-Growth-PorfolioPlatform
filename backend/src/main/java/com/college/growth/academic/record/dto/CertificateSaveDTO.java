package com.college.growth.academic.record.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Data;

/** 证书新增/修改入参。 */
@Data
public class CertificateSaveDTO {

    @NotBlank(message = "证书类型不能为空")
    @Size(max = 32)
    private String certType;

    @NotBlank(message = "证书名称不能为空")
    @Size(min = 2, max = 128, message = "证书名称 2-128 字")
    private String certName;

    @Size(max = 32, message = "成绩/等级不超过 32 字")
    private String score;

    @NotNull(message = "取得日期不能为空")
    private LocalDate obtainDate;

    private Long evidenceFileId;
}
