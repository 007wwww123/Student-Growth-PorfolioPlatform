package com.college.growth.common.student.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.college.growth.common.student.entity.StuStudent;
import com.college.growth.common.student.mapper.StuStudentMapper;
import com.college.growth.common.util.TextUtils;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 学生主数据读取入口。
 *
 * <p>全系统取学生基本信息（姓名、学号、班级等）都必须走本类，不要各自拼 SQL：
 * 学籍口径一旦调整（例如接入统一身份、增加学籍状态过滤），只改这里。</p>
 */
@Service
public class StudentMasterService {

    private final StuStudentMapper studentMapper;

    public StudentMasterService(StuStudentMapper studentMapper) {
        this.studentMapper = studentMapper;
    }

    /** 按学生 ID 查主数据；不存在返回空。 */
    public Optional<StuStudent> find(Long studentId) {
        if (studentId == null || studentId <= 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(studentMapper.selectById(studentId));
    }

    /** 按学号查主数据；不存在返回空。 */
    public Optional<StuStudent> findByStudentNo(String studentNo) {
        if (TextUtils.isBlank(studentNo)) {
            return Optional.empty();
        }
        return Optional.ofNullable(studentMapper.selectOne(Wrappers.<StuStudent>lambdaQuery()
                .eq(StuStudent::getStudentNo, studentNo.trim())
                .last("limit 1")));
    }

    /**
     * 取学生所属班级编码。
     *
     * @return 主数据缺失或未分配班级时返回 null
     */
    public String findClassCode(Long studentId) {
        return find(studentId).map(StuStudent::getClassCode)
                .filter(code -> !TextUtils.isBlank(code))
                .orElse(null);
    }

    /** 学生是否在读（1 在读）。主数据缺失时按在读处理，避免误拦历史档案。 */
    public boolean isActive(Long studentId) {
        return find(studentId).map(student -> student.getStatus() == null || student.getStatus() == 1)
                .orElse(true);
    }
}
