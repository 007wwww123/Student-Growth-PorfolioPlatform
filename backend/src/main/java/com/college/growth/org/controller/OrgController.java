package com.college.growth.org.controller;

import com.college.growth.common.dto.StatusUpdateDTO;
import com.college.growth.common.result.Result;
import com.college.growth.org.dto.ClassSaveDTO;
import com.college.growth.org.dto.CollegeSaveDTO;
import com.college.growth.org.dto.CounselorAssignDTO;
import com.college.growth.org.dto.MajorSaveDTO;
import com.college.growth.org.service.OrgService;
import com.college.growth.org.vo.ClassVO;
import com.college.growth.org.vo.CollegeVO;
import com.college.growth.org.vo.MajorVO;
import com.college.growth.org.vo.OrgOptionsVO;
import com.college.growth.permission.support.RequirePermission;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 组织架构维护（F-02 / AC-005）。
 *
 * <p>写接口全部要求管理员权限点；读取接口按 {@code org:class:read} 放开给辅导员，
 * 供班级台账与建档页使用。</p>
 */
@RestController
@RequestMapping("/org")
public class OrgController {

    private final OrgService orgService;

    public OrgController(OrgService orgService) {
        this.orgService = orgService;
    }

    /** 下拉数据：学院、专业、班级、辅导员。 */
    @GetMapping("/options")
    public Result<OrgOptionsVO> options() {
        return Result.success(orgService.options());
    }

    @GetMapping("/colleges")
    @RequirePermission("org:college:manage")
    public Result<List<CollegeVO>> colleges() {
        return Result.success(orgService.colleges());
    }

    @PostMapping("/colleges")
    @RequirePermission("org:college:manage")
    public Result<CollegeVO> createCollege(@Valid @RequestBody CollegeSaveDTO dto) {
        return Result.success(orgService.createCollege(dto));
    }

    @PutMapping("/colleges/{id}")
    @RequirePermission("org:college:manage")
    public Result<CollegeVO> updateCollege(@PathVariable Long id,
                                           @Valid @RequestBody CollegeSaveDTO dto) {
        return Result.success(orgService.updateCollege(id, dto));
    }

    @PutMapping("/colleges/{id}/status")
    @RequirePermission("org:college:manage")
    public Result<Void> updateCollegeStatus(@PathVariable Long id,
                                            @Valid @RequestBody StatusUpdateDTO dto) {
        orgService.updateCollegeStatus(id, dto.getStatus());
        return Result.success();
    }

    @GetMapping("/majors")
    @RequirePermission("org:major:manage")
    public Result<List<MajorVO>> majors(@RequestParam(required = false) Long collegeId) {
        return Result.success(orgService.majors(collegeId));
    }

    @PostMapping("/majors")
    @RequirePermission("org:major:manage")
    public Result<MajorVO> createMajor(@Valid @RequestBody MajorSaveDTO dto) {
        return Result.success(orgService.createMajor(dto));
    }

    @PutMapping("/majors/{id}")
    @RequirePermission("org:major:manage")
    public Result<MajorVO> updateMajor(@PathVariable Long id,
                                       @Valid @RequestBody MajorSaveDTO dto) {
        return Result.success(orgService.updateMajor(id, dto));
    }

    @PutMapping("/majors/{id}/status")
    @RequirePermission("org:major:manage")
    public Result<Void> updateMajorStatus(@PathVariable Long id,
                                          @Valid @RequestBody StatusUpdateDTO dto) {
        orgService.updateMajorStatus(id, dto.getStatus());
        return Result.success();
    }

    @GetMapping("/classes")
    @RequirePermission("org:class:read")
    public Result<List<ClassVO>> classes(@RequestParam(required = false) Long collegeId,
                                        @RequestParam(required = false) Long majorId) {
        return Result.success(orgService.classes(collegeId, majorId));
    }

    @PostMapping("/classes")
    @RequirePermission("org:class:manage")
    public Result<ClassVO> createClass(@Valid @RequestBody ClassSaveDTO dto) {
        return Result.success(orgService.createClass(dto));
    }

    @PutMapping("/classes/{id}")
    @RequirePermission("org:class:manage")
    public Result<ClassVO> updateClass(@PathVariable Long id,
                                       @Valid @RequestBody ClassSaveDTO dto) {
        return Result.success(orgService.updateClass(id, dto));
    }

    @PutMapping("/classes/{id}/counselor")
    @RequirePermission("org:class:manage")
    public Result<ClassVO> assignCounselor(@PathVariable Long id,
                                           @Valid @RequestBody CounselorAssignDTO dto) {
        return Result.success(orgService.assignCounselor(id, dto.getCounselorUserId()));
    }

    @PutMapping("/classes/{id}/status")
    @RequirePermission("org:class:manage")
    public Result<Void> updateClassStatus(@PathVariable Long id,
                                          @Valid @RequestBody StatusUpdateDTO dto) {
        orgService.updateClassStatus(id, dto.getStatus());
        return Result.success();
    }
}
