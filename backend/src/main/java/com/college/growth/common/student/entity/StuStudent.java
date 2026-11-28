package com.college.growth.common.student.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 学生主数据（物理表 {@code stu_student}）。
 *
 * <p>SIS（学工/教务）在本系统的落地副本，权威源在外部系统。板块一的 7 个 {@code source=SIS}
 * 字段不允许任何角色直接填写，只能由 {@code StudentSyncService} 从本表物化到
 * {@code arc_profile_item}；档案侧读的仍是 {@code arc_profile_item}，
 * 因此渲染、校验、变更日志、待确认区全链路不受影响。</p>
 *
 * <p>{@code id} 与 {@code arc_profile.student_id} 同口径（统一认证后的用户 ID），
 * 不是自增学籍号；学号在 {@code studentNo} 上单独建唯一键。</p>
 */
@Data
@TableName("stu_student")
public class StuStudent {

    @TableId(type = IdType.INPUT)
    private Long id;

    /** 学号，学籍唯一标识，与 basic.studentNo 对齐。 */
    private String studentNo;

    /** 姓名。 */
    private String name;

    /** 性别 M/F/U，与 basic.gender 的选项值一致。 */
    private String gender;

    /** 民族，存中文名，与 basic.nation 的选项值一致。 */
    private String nation;

    /** 身份证号，18 位；接入加密后改密文列。 */
    private String idCard;

    /** 身份证密文（AES-256-GCM，enc:v1: 前缀），迁移后明文列为空。 */
    private String idCardCipher;

    /** 身份证 SHA-256 十六进制哈希，用于唯一性比对。 */
    private String idCardHash;

    /** 籍贯。 */
    private String nativePlace;

    /** 所属班级，引用 org_class.class_code，是审核权的判定依据。 */
    private String classCode;

    /** 入学年份。 */
    private Integer enrollmentYear;

    /** 1 在读 / 2 休学 / 3 退学 / 4 毕业。 */
    private Integer status;

    /** 数据来源：SIS / MANUAL。 */
    private String syncSource;

    /** 最近一次从来源系统同步的时间。 */
    private LocalDateTime syncedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
