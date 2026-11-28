package com.college.growth.mentor;

import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.field.entity.ArcFieldDef;
import com.college.growth.common.profile.entity.ArcProfile;
import com.college.growth.common.result.ResultCode;
import com.college.growth.mentor.common.MentorConstants;
import com.college.growth.mentor.dto.MentorSectionSaveDTO;
import com.college.growth.mentor.entity.ArcMentorNote;
import com.college.growth.mentor.mapper.ArcMentorNoteMapper;
import com.college.growth.mentor.service.MentorCompletenessProvider;
import com.college.growth.mentor.util.MentorSectionUtils;
import com.college.growth.mentor.vo.MentorNoteVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;

class MentorSectionTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void saveDtoAcceptsWrappedAndFlatBodies() throws Exception {
        MentorSectionSaveDTO wrapped = json.readValue(
                "{\"academicYear\":\"2025-2026-1\",\"fields\":{\"topic\":\"ACADEMIC\"}}",
                MentorSectionSaveDTO.class);
        assertEquals("2025-2026-1", wrapped.getAcademicYear());
        assertEquals("ACADEMIC", wrapped.getFields().get("topic"));

        MentorSectionSaveDTO flat = json.readValue("{\"topic\":\"ACADEMIC\"}", MentorSectionSaveDTO.class);
        assertEquals("ACADEMIC", flat.getFields().get("topic"));
    }

    @Test
    void saveDtoAllowsEmptyFieldsBecauseMentorHasNoMainTableValues() throws Exception {
        MentorSectionSaveDTO dto = json.readValue("{\"academicYear\":\"2025-2026-1\",\"fields\":{}}",
                MentorSectionSaveDTO.class);
        assertTrue(dto.getFields().isEmpty(), "MENTOR 的 PUT 只接受空 fields，不能带 @NotEmpty");
    }

    @Test
    void talkDateMustBeWithinEnrollmentAndNotInFuture() {
        assertTrue(MentorSectionUtils.isValidTalkDate(LocalDate.now(), 2024));
        assertFalse(MentorSectionUtils.isValidTalkDate(LocalDate.now().plusDays(1), 2024));
        assertFalse(MentorSectionUtils.isValidTalkDate(LocalDate.of(2023, 12, 31), 2024));
        assertTrue(MentorSectionUtils.isValidTalkDate(LocalDate.of(2024, 1, 1), 2024));
        // 主数据缺失（null）时只校验上界，历史档案仍可追加
        assertTrue(MentorSectionUtils.isValidTalkDate(LocalDate.of(2020, 5, 1), null));
        assertFalse(MentorSectionUtils.isValidTalkDate(null, 2024));
    }

    @Test
    void requiredCompleteNeedsDateTopicAndAdvice() {
        ArcMentorNote note = new ArcMentorNote();
        assertFalse(MentorSectionUtils.isRequiredComplete(note));
        note.setTalkDate(LocalDate.of(2026, 4, 18));
        note.setTopic("ACADEMIC");
        assertFalse(MentorSectionUtils.isRequiredComplete(note), "建议与寄语是必填");
        note.setAdvice("本学期重点补齐算法基础，建议参加校赛练手");
        assertTrue(MentorSectionUtils.isRequiredComplete(note));
        note.setAdvice("   ");
        assertFalse(MentorSectionUtils.isRequiredComplete(note), "空白串不算有效值");
    }

    @Test
    void studentViewNeverCarriesInternalRemarkOrCounselorIdentity() {
        ArcMentorNote note = sampleNote();
        MentorNoteVO student = MentorSectionUtils.toView(note, false, true, "学业规划");
        assertNull(student.getInternalRemark());
        assertNull(student.getCounselorId());
        assertEquals("辅导员", student.getAuthorLabel());
        assertEquals("学业规划", student.getTopicLabel());
        assertTrue(student.getCanFeedback());

        MentorNoteVO counselor = MentorSectionUtils.toView(note, true, false, "学业规划");
        assertEquals("家庭经济困难，需留意勤工助学名额", counselor.getInternalRemark());
        assertEquals(9001L, counselor.getCounselorId());
        assertFalse(counselor.getCanFeedback());
    }

    @Test
    void feedbackLockedOnceFeedbackAtIsSet() {
        ArcMentorNote note = sampleNote();
        note.setFeedbackAt(java.time.LocalDateTime.now());
        assertFalse(MentorSectionUtils.toView(note, false, true, "学业规划").getCanFeedback());
    }

    @Test
    void topicLabelFallsBackToRawCodeWhenDictionaryIsEmpty() {
        ArcFieldDef def = new ArcFieldDef();
        def.setFieldCode("mentor.topic");
        def.setOptionsJson("[{\"value\":\"ACADEMIC\",\"label\":\"学业规划\"}]");
        assertEquals("学业规划", MentorSectionUtils.topicLabelOf(def, "ACADEMIC"));
        assertEquals("UNKNOWN", MentorSectionUtils.topicLabelOf(def, "UNKNOWN"));
        assertEquals("DAILY", MentorSectionUtils.topicLabelOf(new ArcFieldDef(), "DAILY"));
    }

    @Test
    void appendSummaryExcludesInternalRemark() {
        ArcMentorNote note = sampleNote();
        String summary = MentorSectionUtils.noteSummary(note);
        assertFalse(summary.contains("家庭经济困难"), "变更日志对所有有查看权的人可见，不能写内部备注");
        assertTrue(summary.contains("ACADEMIC"));
    }

    @Test
    void completenessIsFullWhenAnyCompleteNoteExists() {
        ArcMentorNoteMapper mapper = Mockito.mock(ArcMentorNoteMapper.class);
        Mockito.when(mapper.selectList(Mockito.any())).thenReturn(List.of(sampleNote()));
        MentorCompletenessProvider provider = new MentorCompletenessProvider(mapper);
        ArcProfile profile = profile();

        assertEquals(new BigDecimal("100.00"), provider.compute(profile));
        assertDoesNotThrow(() -> provider.requireReviewReady(profile));
        assertEquals("MENTOR", provider.sectionCode());
    }

    @Test
    void completenessIsZeroAndReviewBlockedWithoutCompleteNote() {
        ArcMentorNoteMapper mapper = Mockito.mock(ArcMentorNoteMapper.class);
        Mockito.when(mapper.selectList(Mockito.any())).thenReturn(List.of());
        MentorCompletenessProvider provider = new MentorCompletenessProvider(mapper);
        ArcProfile profile = profile();

        assertEquals(BigDecimal.ZERO, provider.compute(profile));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> provider.requireReviewReady(profile));
        assertSame(ResultCode.REQUIRED_FIELD_MISSING, ex.getResultCode());
    }

    @Test
    void completenessOfUncreatedProfileIsZeroWithoutQuery() {
        ArcMentorNoteMapper mapper = Mockito.mock(ArcMentorNoteMapper.class);
        MentorCompletenessProvider provider = new MentorCompletenessProvider(mapper);
        assertEquals(BigDecimal.ZERO, provider.compute(null));
        assertEquals(BigDecimal.ZERO, provider.compute(new ArcProfile()));
        Mockito.verifyNoInteractions(mapper);
    }

    @Test
    void noteMapKeysStayStableForValidation() {
        assertEquals("talkDate", MentorSectionUtils.shortName(fieldDef("mentor.talkDate")));
        assertEquals("internalRemark", MentorSectionUtils.shortName(fieldDef("mentor.internalRemark")));
        assertEquals(MentorConstants.SECTION_CODE, "MENTOR");
    }

    private static ArcFieldDef fieldDef(String code) {
        ArcFieldDef def = new ArcFieldDef();
        def.setFieldCode(code);
        return def;
    }

    private static ArcProfile profile() {
        ArcProfile profile = new ArcProfile();
        profile.setId(3001L);
        profile.setStudentId(1001L);
        profile.setSectionCode("MENTOR");
        return profile;
    }

    private static ArcMentorNote sampleNote() {
        ArcMentorNote note = new ArcMentorNote();
        note.setId(5001L);
        note.setProfileId(3001L);
        note.setStudentId(1001L);
        note.setAcademicYear("2025-2026-1");
        note.setTalkDate(LocalDate.of(2026, 4, 18));
        note.setTopic("ACADEMIC");
        note.setAdvice("本学期重点补齐算法基础，建议参加校赛练手");
        note.setStarLevel(1);
        note.setInternalRemark("家庭经济困难，需留意勤工助学名额");
        note.setCounselorId(9001L);
        return note;
    }
}
