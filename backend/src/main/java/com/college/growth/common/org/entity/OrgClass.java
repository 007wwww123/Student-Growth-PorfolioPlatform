package com.college.growth.common.org.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 班级（物理表 {@code org_class}）。
 *
 * <p>{@code basic.classId} 引用本表的班级编码，{@code basic.majorId} 由 {@code majorName} 推导，
 * {@code counselorUserId} 决定该班档案的审核权。</p>
 */
@Data
@TableName("org_class")
public class OrgClass {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 班级编码，全局唯一。 */
    private String classCode;

    /** 班级名称，如 软件2401。 */
    private String className;

    /** 所属学院 org_college.id（脚本 16 增列）。 */
    private Long collegeId;

    /** 所属专业 org_major.id（脚本 16 增列）。 */
    private Long majorId;

    /** 专业名称，basic.majorId 由此推导。 */
    private String majorName;

    /** 年级，四位年份。 */
    private Integer gradeYear;

    /** 现任辅导员，决定审核权。 */
    private Long counselorUserId;

    /** 1 在读 / 0 已毕业或撤销。 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
