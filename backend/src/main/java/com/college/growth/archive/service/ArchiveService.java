package com.college.growth.archive.service;

import com.college.growth.archive.dto.ArchiveBatchDTO;
import com.college.growth.archive.vo.ArchiveCandidateVO;
import com.college.growth.archive.vo.ArchiveResultVO;
import java.util.List;

/**
 * 批量归档（S-1-11）。
 *
 * <p>归档是终态：六板块全部通过后整体转 {@code LOCKED}，此后任何角色都不能再写内容，
 * 只有管理员可以解冻（BR-S09）。因此本服务只做「判断 + 一次性流转」，不提供单板块入口。</p>
 *
 * <p>数据范围：辅导员只能归档本班学生，管理员持临时授权只读（不能归档），学生不涉及。</p>
 */
public interface ArchiveService {

    /**
     * 查询归档候选人及其六板块完成情况。
     *
     * @param academicYear 学年，空取当前学年
     * @param classCode    班级编码，空表示本人全部带班班级
     * @return 按学号升序
     */
    List<ArchiveCandidateVO> listCandidates(String academicYear, String classCode);

    /**
     * 批量归档：名单内全部完成才执行，任一未完成则整批拒绝（EX-12）。
     *
     * @param dto 学年与学生清单
     * @return 归档结果；{@code rejected=true} 时 {@code students} 为未完成清单
     */
    ArchiveResultVO archive(ArchiveBatchDTO dto);
}
