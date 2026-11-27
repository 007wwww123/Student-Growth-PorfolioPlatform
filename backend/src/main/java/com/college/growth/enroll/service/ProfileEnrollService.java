package com.college.growth.enroll.service;

import com.college.growth.enroll.dto.BatchCreateDTO;
import com.college.growth.enroll.vo.BatchCreateResultVO;
import com.college.growth.enroll.vo.ClassEnrollVO;
import java.util.List;

/**
 * 批量建档（S-1-01）。
 *
 * <p>职责边界：只负责「按学生主数据生成六板块空实例」，不写任何字段值，
 * 也不发起审核。字段值仍由各板块的保存接口写入，学生主数据字段由
 * {@code StudentSyncService} 在保存/提交时物化。</p>
 *
 * <p>数据范围：仅辅导员可执行，且只能处理本人带班班级；管理员没有业务写权限
 * （BR-S03），学生更不涉及——越权一律 {@code EC-40302}。</p>
 */
public interface ProfileEnrollService {

    /**
     * 查询当前辅导员可建档的班级及其建档进度。
     *
     * @param academicYear 学年，空取当前学年
     * @return 按班级编码升序
     */
    List<ClassEnrollVO> listClasses(String academicYear);

    /**
     * 为所选班级的在读学生批量生成六板块空实例，已存在的板块跳过（EX-01）。
     *
     * @param dto 学年与班级编码
     * @return 本次新建 / 跳过的汇总
     */
    BatchCreateResultVO batchCreate(BatchCreateDTO dto);
}
