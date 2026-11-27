package com.college.growth.archive.vo;

import java.util.List;
import lombok.Data;

/**
 * 批量归档结果（S-1-11 / EX-12）。
 *
 * <p>{@code rejected = true} 表示整批未执行——只要名单里有一个人板块没全部通过，
 * 全批都不动（EX-12），此时 {@code students} 里是完整的未完成清单。</p>
 *
 * <p>为什么用 200 + {@code rejected} 而不是 409：拒绝的原因是「一份清单」，
 * 塞进错误响应只能压成字符串或丢掉结构；前端要逐人展示缺哪几块，
 * 因此把结果作为业务数据返回，错误码只用于真正的非法请求与越权。</p>
 */
@Data
public class ArchiveResultVO {

    private String academicYear;

    /** 本次提交的学生数。 */
    private int requestedStudents;

    /** 实际转为已归档的学生数。 */
    private int archivedStudents;

    /** 实际转为已归档的板块实例数。 */
    private int archivedProfiles;

    /** 本来就已归档、本次跳过的学生数。 */
    private int skippedStudents;

    /** 是否因存在未完成板块而整批拒绝（此时 archivedStudents = 0）。 */
    private boolean rejected;

    /**
     * 逐学生结果。
     *
     * <p>拒绝时只放未完成清单（{@code status = INCOMPLETE}），成功时放本次归档与已跳过的学生；
     * 两种场景都不重复罗列「本来就可归档」的人，前端按 {@code rejected} 决定文案。</p>
     */
    private List<ArchiveStudentResultVO> students;
}
