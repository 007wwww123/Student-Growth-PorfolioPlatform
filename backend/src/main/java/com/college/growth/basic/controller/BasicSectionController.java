package com.college.growth.basic.controller;

import com.college.growth.basic.common.BasicConstants;
import com.college.growth.basic.dto.BasicSectionSaveDTO;
import com.college.growth.basic.service.BasicSectionService;
import com.college.growth.basic.vo.BasicSectionVO;
import com.college.growth.basic.vo.SensitiveFieldVO;
import com.college.growth.common.profile.vo.PendingFieldVO;
import com.college.growth.common.result.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 板块一 基本信息（BASIC）接口入口。
 *
 * <p>对应需求文档 4.2 字段清单与主流程 S-1-03 / S-1-04 / S-1-05。
 * 表单渲染、字段权限、校验、状态流转都在 Service 层完成，Controller 只做协议转换。</p>
 *
 * <p>骨架期的当前用户由请求头注入（见 {@code UserContextFilter}）：
 * {@code X-User-Id}、{@code X-User-Role}（STU/COU/ADMIN）、{@code X-Temp-Grant}。
 * 这不是鉴权手段，接入统一认证后必须移除。</p>
 */
@RestController
@RequestMapping(BasicConstants.API_BASE_PATH)
public class BasicSectionController {

    private final BasicSectionService basicSectionService;

    public BasicSectionController(BasicSectionService basicSectionService) {
        this.basicSectionService = basicSectionService;
    }

    /** 查询板块详情（S-1-03）：按 arc_field_def 渲染表单，逐字段计算可见与可编辑。 */
    @GetMapping("/{studentId}")
    public Result<BasicSectionVO> detail(@PathVariable Long studentId,
                                         @RequestParam(required = false) String academicYear) {
        return Result.success(basicSectionService.detail(studentId, academicYear));
    }

    /** 保存草稿（S-1-04）：逐字段校验后写入，DRAFT / RETURNED 之外的状态拒绝写入。 */
    @PutMapping("/{studentId}")
    public Result<Void> save(@PathVariable Long studentId,
                             @Valid @RequestBody BasicSectionSaveDTO dto) {
        basicSectionService.save(studentId, dto);
        return Result.success();
    }

    /** 提交板块（S-1-05）：校验必填齐全后由 DRAFT / RETURNED 转为 SUBMITTED。 */
    @PostMapping("/{studentId}/submit")
    public Result<Void> submit(@PathVariable Long studentId,
                               @RequestParam(required = false) String academicYear) {
        basicSectionService.submit(studentId, academicYear);
        return Result.success();
    }

    /** 撤回提交（S-1-09）：仅学生本人，SUBMITTED 转 DRAFT，全生命周期一次。 */
    @PostMapping("/{studentId}/withdraw")
    public Result<Void> withdraw(@PathVariable Long studentId,
                                 @RequestParam(required = false) String academicYear) {
        basicSectionService.withdraw(studentId, academicYear);
        return Result.success();
    }

    /** 确认待确认值（BR-F06）：仅该班现任辅导员，新值自此生效。 */
    @PostMapping("/{studentId}/fields/{fieldCode}/confirm")
    public Result<PendingFieldVO> confirm(@PathVariable Long studentId,
                                          @PathVariable String fieldCode,
                                          @RequestParam(required = false) String academicYear) {
        return Result.success(basicSectionService.confirmField(studentId, fieldCode, academicYear));
    }

    /** 驳回待确认值（BR-F06）：丢弃新值，对外仍返回旧值。 */
    @PostMapping("/{studentId}/fields/{fieldCode}/reject")
    public Result<Void> rejectPending(@PathVariable Long studentId,
                                      @PathVariable String fieldCode,
                                      @RequestParam(required = false) String academicYear) {
        basicSectionService.rejectField(studentId, fieldCode, academicYear);
        return Result.success();
    }

    /** 查看敏感字段明文（BR-D05）：仅辅导员或持临时授权的管理员，调用即写审计。 */
    @GetMapping("/{studentId}/fields/{fieldCode}/reveal")
    public Result<SensitiveFieldVO> reveal(@PathVariable Long studentId,
                                           @PathVariable String fieldCode,
                                           @RequestParam(required = false) String academicYear) {
        return Result.success(basicSectionService.reveal(studentId, fieldCode, academicYear));
    }
}
