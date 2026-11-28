package com.college.growth.account.vo;

import java.util.List;
import lombok.Data;

/** 批量开通结果。 */
@Data
public class BatchOpenResultVO {

    private int totalStudents;

    private int created;

    private int skipped;

    private List<BatchOpenItemVO> accounts;
}
