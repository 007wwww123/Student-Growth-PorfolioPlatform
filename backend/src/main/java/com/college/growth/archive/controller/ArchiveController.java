package com.college.growth.archive.controller;

import com.college.growth.archive.common.ArchiveConstants;
import com.college.growth.archive.dto.ArchiveBatchDTO;
import com.college.growth.archive.service.ArchiveService;
import com.college.growth.archive.vo.ArchiveCandidateVO;
import com.college.growth.archive.vo.ArchiveResultVO;
import com.college.growth.common.result.Result;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 毕业归档接口（S-1-11）。
 *
 * <p>两个接口：一个看「谁能归档」（六板块完成情况），一个提交名单执行归档。
 * 名单由辅导员勾选后提交，整批要么全归档、要么整体拒绝并返回未完成清单（EX-12）。</p>
 */
@RestController
@RequestMapping(ArchiveConstants.API_BASE_PATH)
public class ArchiveController {

    private final ArchiveService archiveService;

    public ArchiveController(ArchiveService archiveService) {
        this.archiveService = archiveService;
    }

    /** 归档候选人：本班学生 + 六板块完成情况；辅导员只看本班，管理员需临时授权。 */
    @GetMapping("/candidates")
    public Result<List<ArchiveCandidateVO>> candidates(
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) String classCode) {
        return Result.success(archiveService.listCandidates(academicYear, classCode));
    }

    /** 批量归档：六板块全部通过才执行，任一未完成整批拒绝（EX-12）。 */
    @PostMapping("/batch")
    public Result<ArchiveResultVO> batch(@Valid @RequestBody ArchiveBatchDTO dto) {
        return Result.success(archiveService.archive(dto));
    }
}
