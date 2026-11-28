package com.college.growth.review.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.ProfileStatusEnum;
import com.college.growth.common.enums.SectionCodeEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.org.OrgConstants;
import com.college.growth.common.org.entity.OrgClass;
import com.college.growth.common.org.mapper.OrgClassMapper;
import com.college.growth.common.security.IdCardCrypto;
import com.college.growth.common.profile.entity.ArcProfile;
import com.college.growth.common.result.PageResult;
import com.college.growth.common.result.ResultCode;
import com.college.growth.common.util.AcademicYearUtils;
import com.college.growth.review.common.ReviewConstants;
import com.college.growth.review.mapper.ReviewTaskMapper;
import com.college.growth.review.mapper.StudentFieldRow;
import com.college.growth.review.service.ReviewTaskService;
import com.college.growth.review.vo.ReviewTaskVO;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 待审列表实现。
 *
 * <p>列表只查一次主表，再用两次批量查询补姓名与班级名，避免 N+1；
 * 姓名与班级来自基本信息板块，字段编码集中在 {@link OrgConstants}，不散落在 SQL 之外。</p>
 */
@Service
public class ReviewTaskServiceImpl implements ReviewTaskService {

    /** 列表补全需要的字段：姓名与班级。 */
    private static final List<String> LIST_FIELDS = List.of("basic.name", OrgConstants.CLASS_FIELD_CODE);

    private final ReviewTaskMapper reviewTaskMapper;

    private final OrgClassMapper orgClassMapper;

    public ReviewTaskServiceImpl(ReviewTaskMapper reviewTaskMapper, OrgClassMapper orgClassMapper) {
        this.reviewTaskMapper = reviewTaskMapper;
        this.orgClassMapper = orgClassMapper;
    }

    @Override
    public PageResult<ReviewTaskVO> listTasks(String sectionCode, String status, String classId,
                                              String academicYear, Long pageNum, Long pageSize) {
        CurrentUser user = UserContext.get();
        Long counselorId = resolveCounselorScope(user);
        String year = AcademicYearUtils.resolve(academicYear);
        String normalizedSection = normalizeSection(sectionCode);
        String normalizedStatus = normalizeStatus(status);
        long num = pageNum == null ? ReviewConstants.DEFAULT_PAGE_NUM : pageNum;
        long size = pageSize == null ? ReviewConstants.DEFAULT_PAGE_SIZE : pageSize;
        if (num < 1) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "页码从 1 开始");
        }
        if (size < 1 || size > ReviewConstants.MAX_PAGE_SIZE) {
            throw new BusinessException(ResultCode.PARAM_INVALID,
                    "每页条数应在 1–" + ReviewConstants.MAX_PAGE_SIZE + " 之间");
        }

        IPage<ArcProfile> page = reviewTaskMapper.selectTaskPage(new Page<>(num, size),
                normalizedSection, normalizedStatus, year, classId, counselorId);

        List<ReviewTaskVO> records = toVOs(page.getRecords(), year);
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 解析数据范围。
     *
     * @return 辅导员 ID（只查本班）；null 表示不限（仅持临时授权的管理员）
     */
    private Long resolveCounselorScope(CurrentUser user) {
        if (user.isCounselor()) {
            if (user.userId() == null || user.userId() <= 0) {
                throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "缺少辅导员身份，无法查看待审列表");
            }
            return user.userId();
        }
        if (user.isAdminWithGrant()) {
            return null;
        }
        throw new BusinessException(ResultCode.DATA_SCOPE_DENIED, "无权查看待审列表");
    }

    private String normalizeSection(String sectionCode) {
        if (sectionCode == null || sectionCode.isBlank()) {
            return null;
        }
        SectionCodeEnum section = SectionCodeEnum.tryFromCode(sectionCode);
        if (section == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "未知板块编码：" + sectionCode);
        }
        return section.name();
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return ReviewConstants.DEFAULT_STATUS;
        }
        try {
            return ProfileStatusEnum.valueOf(status.trim().toUpperCase()).name();
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "未知板块状态：" + status);
        }
    }

    private List<ReviewTaskVO> toVOs(List<ArcProfile> profiles, String year) {
        if (profiles == null || profiles.isEmpty()) {
            return List.of();
        }
        List<Long> studentIds = profiles.stream()
                .map(ArcProfile::getStudentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Map<String, String>> studentFields = loadStudentFields(studentIds, year);
        Map<String, String> classNames = loadClassNames(studentFields);

        List<ReviewTaskVO> result = new ArrayList<>(profiles.size());
        for (ArcProfile profile : profiles) {
            ReviewTaskVO vo = new ReviewTaskVO();
            vo.setProfileId(profile.getId());
            vo.setStudentId(profile.getStudentId());
            vo.setSectionCode(profile.getSectionCode());
            vo.setAcademicYear(profile.getAcademicYear());
            vo.setStatus(profile.getStatus());
            vo.setStatusLabel(statusLabel(profile.getStatus()));
            vo.setCompleteness(profile.getCompleteness());
            vo.setSubmitCount(profile.getSubmitCount());
            vo.setSubmittedAt(profile.getSubmittedAt());
            vo.setReviewerId(profile.getReviewerId());
            vo.setReviewedAt(profile.getReviewedAt());

            SectionCodeEnum section = SectionCodeEnum.tryFromCode(profile.getSectionCode());
            vo.setSectionName(section == null ? profile.getSectionCode() : section.getLabel());

            Map<String, String> fields = studentFields.getOrDefault(profile.getStudentId(), Map.of());
            vo.setStudentName(fields.get("basic.name"));
            String classId = fields.get(OrgConstants.CLASS_FIELD_CODE);
            vo.setClassId(classId);
            vo.setClassName(classId == null ? null : classNames.get(classId));
            result.add(vo);
        }
        return result;
    }

    private Map<Long, Map<String, String>> loadStudentFields(List<Long> studentIds, String year) {
        if (studentIds.isEmpty()) {
            return Map.of();
        }
        List<StudentFieldRow> rows = reviewTaskMapper.selectStudentFields(studentIds, LIST_FIELDS, year);
        Map<Long, Map<String, String>> result = new HashMap<>();
        for (StudentFieldRow row : rows) {
            String value = "basic.idCard".equals(row.getFieldCode())
                    ? IdCardCrypto.decrypt(row.getValueText()) : row.getValueText();
            result.computeIfAbsent(row.getStudentId(), key -> new HashMap<>())
                    .put(row.getFieldCode(), value);
        }
        return result;
    }

    private Map<String, String> loadClassNames(Map<Long, Map<String, String>> studentFields) {
        List<String> classCodes = studentFields.values().stream()
                .map(fields -> fields.get(OrgConstants.CLASS_FIELD_CODE))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (classCodes.isEmpty()) {
            return Map.of();
        }
        List<OrgClass> classes = orgClassMapper.selectList(Wrappers.<OrgClass>lambdaQuery()
                .in(OrgClass::getClassCode, classCodes));
        return classes.stream()
                .collect(Collectors.toMap(OrgClass::getClassCode, OrgClass::getClassName, (left, right) -> left));
    }

    private String statusLabel(String status) {
        try {
            return ProfileStatusEnum.valueOf(status).getLabel();
        } catch (IllegalArgumentException | NullPointerException ex) {
            return status;
        }
    }
}
