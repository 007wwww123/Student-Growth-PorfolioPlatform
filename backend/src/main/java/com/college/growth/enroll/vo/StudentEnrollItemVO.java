package com.college.growth.enroll.vo;

import java.util.List;
import lombok.Data;

/**
 * 单个学生的建档结果。
 *
 * <p>把「新建了哪几个板块」显式带出，是为了让部分建档（例如上学期只建了三块）
 * 能被辅导员看到，而不是只看到一个「成功」的总数。</p>
 */
@Data
public class StudentEnrollItemVO {

    private Long studentId;

    private String studentNo;

    private String studentName;

    private String classCode;

    private String className;

    /** 本次新建的板块编码，大写，如 BASIC。 */
    private List<String> createdSections;

    /** 本次跳过（已存在）的板块数量。 */
    private int skippedSectionCount;

    /** CREATED（新建了至少一个板块）/ SKIPPED（六板块均已存在）。 */
    private String status;
}
