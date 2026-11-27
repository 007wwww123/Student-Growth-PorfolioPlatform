package com.college.growth.completion.task;

import com.college.growth.common.util.AcademicYearUtils;
import com.college.growth.completion.common.CompletionConstants;
import com.college.growth.completion.service.CompletionService;
import com.college.growth.completion.vo.CompletionRefreshVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 完成标记定时补偿任务（BR-S10）。
 *
 * <p>正常路径上，每个状态流转节点都会重算标记，这里只处理「状态没走状态机」的旁路数据：
 * 学工系统直接改库、批量修数脚本、迁移导入等。它们不会触发业务代码，标记会滞后。</p>
 *
 * <p>每天凌晨跑一次，只重算当前学年；关闭方式为 {@code growth.completion.refresh-enabled=false}，
 * 定时表达式可通过 {@code growth.completion.refresh-cron} 覆盖。</p>
 */
@Component
@ConditionalOnProperty(name = "growth.completion.refresh-enabled", havingValue = "true",
        matchIfMissing = true)
public class CompletionRefreshTask {

    private static final Logger log = LoggerFactory.getLogger(CompletionRefreshTask.class);

    private final CompletionService completionService;

    public CompletionRefreshTask(CompletionService completionService) {
        this.completionService = completionService;
    }

    /** 重算当前学年：单次失败不能让后续天次的补偿全部停掉，因此这里吞掉异常并告警。 */
    @Scheduled(cron = "${growth.completion.refresh-cron:0 30 3 * * ?}")
    public void refreshCurrentAcademicYear() {
        String year = AcademicYearUtils.current();
        try {
            CompletionRefreshVO vo = completionService.refreshAcademicYear(year,
                    CompletionConstants.TRIGGER_SCHEDULED);
            log.info("定时重算完成标记结束 year={} 判定={} 完成={} 变化={}",
                    year, vo.getCheckedStudents(), vo.getCompletedStudents(), vo.getChangedStudents());
        } catch (RuntimeException ex) {
            log.warn("定时重算完成标记失败 year={}，等待下次补偿", year, ex);
        }
    }
}
