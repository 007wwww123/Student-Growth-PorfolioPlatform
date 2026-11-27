package com.college.growth.enroll.controller;

import com.college.growth.common.result.Result;
import com.college.growth.enroll.common.EnrollConstants;
import com.college.growth.enroll.dto.BatchCreateDTO;
import com.college.growth.enroll.service.ProfileEnrollService;
import com.college.growth.enroll.vo.BatchCreateResultVO;
import com.college.growth.enroll.vo.ClassEnrollVO;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 批量建档接口（S-1-01）。
 *
 * <p>两个接口一个是「能不能建」（班级与进度），一个是「开始建」；
 * 学生清单由服务端按主数据取，前端不传学生 ID，避免越权建档。</p>
 */
@RestController
@RequestMapping(EnrollConstants.API_BASE_PATH)
public class ProfileEnrollController {

    private final ProfileEnrollService profileEnrollService;

    public ProfileEnrollController(ProfileEnrollService profileEnrollService) {
        this.profileEnrollService = profileEnrollService;
    }

    /** 本班建档进度：班级、在读人数、已建档人数。 */
    @GetMapping("/classes")
    public Result<List<ClassEnrollVO>> classes(@RequestParam(required = false) String academicYear) {
        return Result.success(profileEnrollService.listClasses(academicYear));
    }

    /** 批量生成六板块空档案（DRAFT）：已存在的板块跳过，不重复创建（EX-01）。 */
    @PostMapping("/batch-create")
    public Result<BatchCreateResultVO> batchCreate(@Valid @RequestBody BatchCreateDTO dto) {
        return Result.success(profileEnrollService.batchCreate(dto));
    }
}
