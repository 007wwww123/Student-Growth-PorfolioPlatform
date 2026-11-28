package com.college.growth.career.service.impl;

import com.college.growth.basic.vo.FieldMetaVO;
import com.college.growth.career.dto.CareerSectionSaveDTO;
import com.college.growth.career.service.CareerSectionService;
import com.college.growth.career.vo.CareerSectionVO;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.ProfileStatusEnum;
import com.college.growth.common.log.service.ChangeLogService;
import com.college.growth.common.profile.entity.ArcProfile;
import com.college.growth.common.profile.service.ProfileCompletenessService;
import com.college.growth.common.profile.service.ProfileLocator;
import com.college.growth.common.profile.service.SectionFormService;
import com.college.growth.common.util.AcademicYearUtils;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import static com.college.growth.common.enums.SectionCodeEnum.CAREER;

/**
 * 板块五 职业规划（CAREER）业务实现。
 *
 * <p>查询 / 保存 / 提交 / 撤回全部委托 {@link SectionFormService}：字段渲染、可见与可编辑判定、
 * 逐字段校验、状态机与双日志都在那里。本板块的 5 个字段全部由学生填写（无派生值、无辅导员专属字段），
 * 因此除信封组装外没有额外逻辑——这正是把公共能力收在一处的收益。</p>
 */
@Service
@RequiredArgsConstructor
public class CareerSectionServiceImpl implements CareerSectionService {

    private final SectionFormService forms;

    private final ProfileLocator locator;

    private final ProfileCompletenessService completeness;

    private final ChangeLogService history;

    @Override
    public CareerSectionVO detail(Long studentId, String academicYear) {
        String year = AcademicYearUtils.resolve(academicYear);
        CurrentUser user = UserContext.get();
        // 未持临时授权的管理员没有业务数据权限：返回空视图，与板块一口径一致（需求 2.1）。
        if (!user.canReadBusinessData()) {
            return emptyView(studentId, year);
        }
        var form = forms.detail(studentId, year, CAREER);
        ArcProfile profile = locator.find(studentId, CAREER.name(), year);

        CareerSectionVO vo = new CareerSectionVO();
        vo.setStudentId(studentId);
        vo.setAcademicYear(year);
        vo.setSectionCode(CAREER.name());
        vo.setSectionName(CAREER.getLabel());
        vo.setStatus(form.status());
        vo.setFields(form.fields());
        vo.setFieldMeta(form.fieldMeta());
        vo.setEditable(form.editable());
        vo.setCompleteness(profile == null ? BigDecimal.ZERO : completeness.compute(profile));
        vo.setSubmitCount(countOf(profile, true));
        vo.setWithdrawCount(countOf(profile, false));
        vo.setSubmittedAt(profile == null ? null : profile.getSubmittedAt());
        attachReviewTrace(vo, profile);
        return vo;
    }

    @Override
    public void save(Long studentId, CareerSectionSaveDTO dto) {
        forms.save(studentId, dto.getAcademicYear(), CAREER, dto.getFields());
    }

    @Override
    public void submit(Long studentId, String academicYear) {
        forms.submit(studentId, academicYear, CAREER);
    }

    @Override
    public void withdraw(Long studentId, String academicYear) {
        forms.withdraw(studentId, academicYear, CAREER);
    }

    /** 无业务数据权限时的空视图：只有信封，没有字段与权限位。 */
    private CareerSectionVO emptyView(Long studentId, String year) {
        CareerSectionVO vo = new CareerSectionVO();
        vo.setStudentId(studentId);
        vo.setAcademicYear(year);
        vo.setSectionCode(CAREER.name());
        vo.setSectionName(CAREER.getLabel());
        vo.setStatus(ProfileStatusEnum.DRAFT.name());
        vo.setFields(Map.of());
        vo.setFieldMeta(List.<FieldMetaVO>of());
        vo.setEditable(false);
        vo.setCompleteness(BigDecimal.ZERO);
        vo.setSubmitCount(0);
        vo.setWithdrawCount(0);
        return vo;
    }

    /** 回填最近一次退回理由（BR-S04）：只在「已退回」状态返回，重提后自然消失。 */
    private void attachReviewTrace(CareerSectionVO vo, ArcProfile profile) {
        if (profile == null || !ProfileStatusEnum.RETURNED.name().equals(vo.getStatus())) {
            return;
        }
        history.findLatestReturnNote(profile.getId()).ifPresent(note -> {
            vo.setReviewReason(note.getReason());
            vo.setReviewedAt(note.getCreatedAt());
        });
    }

    private int countOf(ArcProfile profile, boolean submit) {
        if (profile == null) {
            return 0;
        }
        Integer value = submit ? profile.getSubmitCount() : profile.getWithdrawCount();
        return value == null ? 0 : value;
    }
}
