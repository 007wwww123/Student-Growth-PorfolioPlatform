package com.college.growth.life.service.impl;

import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.ProfileStatusEnum;
import com.college.growth.common.log.service.ChangeLogService;
import com.college.growth.basic.vo.FieldMetaVO;
import com.college.growth.common.profile.entity.ArcProfile;
import com.college.growth.common.profile.entity.ArcProfileItem;
import com.college.growth.common.profile.service.ProfileCompletenessService;
import com.college.growth.common.profile.service.ProfileItemService;
import com.college.growth.common.profile.service.ProfileLocator;
import com.college.growth.common.profile.service.SectionFormService;
import com.college.growth.common.util.AcademicYearUtils;
import com.college.growth.life.dto.LifeSectionSaveDTO;
import com.college.growth.life.service.LifeSectionService;
import com.college.growth.life.util.LifeSectionUtils;
import com.college.growth.life.vo.LifeSectionVO;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import static com.college.growth.common.enums.SectionCodeEnum.LIFE;

/**
 * 板块四 生活心理（LIFE）业务实现。
 *
 * <p>查询 / 保存 / 提交 / 撤回全部委托 {@link SectionFormService}：
 * 字段渲染、可见与可编辑判定、逐字段校验、状态机与双日志都在那里，
 * 本板块只负责「板块级」的差异——关注标记的派生与板块自有规则。</p>
 *
 * <p>为什么不再各板块写一份：字段定义是唯一来源（{@code arc_field_def}），
 * 若每个板块自己拼字段、自己改状态，六份实现迟早出现「同一个状态在不同板块能改」这类偏差。</p>
 */
@Service
@RequiredArgsConstructor
public class LifeSectionServiceImpl implements LifeSectionService {

    /** 关注标记字段短名：由压力自评与情绪派生，固定不可编辑（需求 4.5）。 */
    private static final String CARE_FLAG_KEY = "careFlag";

    /** 派生关注标记要读的两个字段编码。 */
    private static final String STRESS_SCORE_CODE = "life.stressScore";

    private static final String MOOD_CODE = "life.mood";

    private final SectionFormService forms;

    private final ProfileLocator locator;

    private final ProfileItemService itemService;

    private final ProfileCompletenessService completeness;

    private final ChangeLogService history;

    @Override
    public LifeSectionVO detail(Long studentId, String academicYear) {
        String year = AcademicYearUtils.resolve(academicYear);
        CurrentUser user = UserContext.get();
        // 未持临时授权的管理员没有业务数据权限：返回空视图，与板块一口径一致（需求 2.1）。
        if (!user.canReadBusinessData()) {
            return emptyView(studentId, year);
        }
        var form = forms.detail(studentId, year, LIFE);
        ArcProfile profile = locator.find(studentId, LIFE.name(), year);

        LifeSectionVO vo = new LifeSectionVO();
        vo.setStudentId(studentId);
        vo.setAcademicYear(year);
        vo.setSectionCode(LIFE.name());
        vo.setSectionName(LIFE.getLabel());
        vo.setStatus(form.status());
        vo.setFields(withCareFlag(form.fields(), form.fieldMeta(), profile));
        vo.setFieldMeta(form.fieldMeta());
        vo.setEditable(form.editable());
        // 完成度按当前库里的值现算：学工导入与辅导员代填都不经过保存接口，
        // 只读 arc_profile.completeness 会拿到过期百分比。
        vo.setCompleteness(profile == null ? BigDecimal.ZERO : completeness.compute(profile));
        vo.setSubmitCount(countOf(profile, true));
        vo.setWithdrawCount(countOf(profile, false));
        vo.setSubmittedAt(profile == null ? null : profile.getSubmittedAt());
        attachReviewTrace(vo, profile);
        return vo;
    }

    @Override
    public void save(Long studentId, LifeSectionSaveDTO dto) {
        forms.save(studentId, dto.getAcademicYear(), LIFE, dto.getFields());
    }

    @Override
    public void submit(Long studentId, String academicYear) {
        forms.submit(studentId, academicYear, LIFE);
    }

    @Override
    public void withdraw(Long studentId, String academicYear) {
        forms.withdraw(studentId, academicYear, LIFE);
    }

    /**
     * 回填关注标记。
     *
     * <p>careFlag 是派生字段（source=SYS、editable_roles 为空），不落 {@code arc_profile_item}：
     * 一旦落库就会有人去「手工修正」，把派生值变成第二份事实。这里在读取时按
     * 压力自评与情绪现算，与 {@link LifeSectionUtils#deriveCareFlag} 同一口径。</p>
     *
     * <p>只在字段定义下发了该字段时才回填：当前角色看不到该字段（如被配置为对老师不可见）时，
     * 不应凭空多出一个没有 fieldMeta 的值。</p>
     *
     * <p>取值来源有一个坑：压力自评对辅导员不可见（visible_roles=STU），所以辅导员的
     * {@code fields} 里没有 stressScore，不能从「当前视图」推导——否则辅导员看到的关注标记
     * 永远是 0，恰好把唯一能看的结论弄错。视图里缺值时回落到库内原值。</p>
     */
    private Map<String, Object> withCareFlag(Map<String, Object> values,
                                             List<FieldMetaVO> metas,
                                             ArcProfile profile) {
        Map<String, Object> merged = new LinkedHashMap<>(values);
        boolean exposed = metas.stream().anyMatch(meta -> CARE_FLAG_KEY.equals(meta.getKey()));
        if (!exposed) {
            return merged;
        }
        Integer score = LifeSectionUtils.parseStressScore(merged.get("stressScore"));
        Object mood = merged.get("mood");
        if ((score == null || mood == null) && profile != null) {
            Map<String, ArcProfileItem> items = itemService.loadByProfile(profile.getId());
            if (score == null) {
                score = LifeSectionUtils.parseStressScore(storedText(items, STRESS_SCORE_CODE));
            }
            if (mood == null) {
                mood = storedText(items, MOOD_CODE);
            }
        }
        merged.put(CARE_FLAG_KEY, LifeSectionUtils.deriveCareFlag(
                score, mood == null ? null : String.valueOf(mood)));
        return merged;
    }

    /** 读库内文本值；字段使用 JSON 列存储时返回 null（本板块这两项都是文本列）。 */
    private String storedText(Map<String, ArcProfileItem> items, String fieldCode) {
        ArcProfileItem item = items.get(fieldCode);
        return item == null ? null : item.getValueText();
    }

    /** 无业务数据权限时的空视图：只有信封，没有字段与权限位。 */
    private LifeSectionVO emptyView(Long studentId, String year) {
        LifeSectionVO vo = new LifeSectionVO();
        vo.setStudentId(studentId);
        vo.setAcademicYear(year);
        vo.setSectionCode(LIFE.name());
        vo.setSectionName(LIFE.getLabel());
        vo.setStatus(ProfileStatusEnum.DRAFT.name());
        vo.setFields(Map.of());
        vo.setFieldMeta(List.of());
        vo.setEditable(false);
        vo.setCompleteness(BigDecimal.ZERO);
        vo.setSubmitCount(0);
        vo.setWithdrawCount(0);
        return vo;
    }

    /**
     * 回填最近一次退回理由（BR-S04）。
     *
     * <p>只在「已退回」状态返回：退回后学生保存草稿不改状态，理由照常可见；
     * 重新提交后不再返回，避免上一次的审核结论被当成当前结论。</p>
     */
    private void attachReviewTrace(LifeSectionVO vo, ArcProfile profile) {
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
