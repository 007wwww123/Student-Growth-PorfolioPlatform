package com.college.growth.archive.vo;

import java.util.List;
import lombok.Data;

/**
 * 归档候选人：一名学生某学年的六板块完成情况。
 *
 * <p>口径：{@code archivable = 六板块齐全且每块都处于 APPROVED 或 LOCKED}。
 * 已通过和已归档都算「完成」——否则归档过一次的学生会永远显示为「不可归档」。
 * 未建档的学生也会出现在清单里（{@code sectionCount = 0}），
 * 让辅导员看到「谁根本没开始」，而不是从列表里凭空消失。</p>
 */
@Data
public class ArchiveCandidateVO {

    private Long studentId;

    private String studentNo;

    private String studentName;

    private String classCode;

    private String className;

    /** 已存在的板块实例数，0–6。 */
    private int sectionCount;

    /** 已通过（APPROVED）的板块数。 */
    private int approvedCount;

    /** 已归档（LOCKED）的板块数。 */
    private int lockedCount;

    /** 尚未完成（未建档或未通过）的板块，为空表示六块都已完成。 */
    private List<ArchiveSectionStatusVO> incompleteSections;

    /** 是否可归档：六板块齐全且全部完成。 */
    private boolean archivable;

    /** 是否已归档：六板块均为 LOCKED。 */
    private boolean archived;
}
