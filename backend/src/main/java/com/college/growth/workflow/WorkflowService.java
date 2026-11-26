package com.college.growth.workflow;

import com.college.growth.common.enums.SectionCodeEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.result.Result;
import com.college.growth.common.result.ResultCode;
import com.college.growth.common.web.RequestVersion;
import com.college.growth.identity.ArchiveIdentity;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

@Service
public class WorkflowService {
    private final JdbcTemplate db;

    public WorkflowService(JdbcTemplate db) {
        this.db = db;
    }

    public record Entry(String code, String status, long version) {}
    public record Decision(String reason) {}
    private record Profile(long id, String code, String status, long version, int withdrawals) {}

    public Result<List<Entry>> list(long studentId, String academicYear) {
        var actor = ArchiveIdentity.current();
        actor.requireRead(studentId);
        actor.requireYear(academicYear);
        return Result.success(db.query("""
                SELECT section_code,status,version FROM arc_profile
                WHERE student_id=? AND academic_year=? AND (? = FALSE OR section_code='MENTOR') ORDER BY section_code
                """, (r,i) -> new Entry(r.getString(1).toLowerCase(java.util.Locale.ROOT),
                        r.getString(2), r.getLong(3)), studentId, academicYear, actor.role() == ArchiveIdentity.Role.MENTOR));
    }

    @Transactional
    public Result<Void> transition(long studentId, String code,
            String action, String academicYear,
            Decision decision) {
        var actor = ArchiveIdentity.current();
        actor.requireYear(academicYear);
        SectionCodeEnum section;
        try {
            section = SectionCodeEnum.fromCode(code);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.PARAM_INVALID);
        }
        boolean withdrawal = action.equals("withdraw");
        if (withdrawal) {
            actor.requireWrite(studentId, code);
            if (actor.role() != ArchiveIdentity.Role.STUDENT && actor.role() != ArchiveIdentity.Role.MENTOR) throw new BusinessException(ResultCode.DATA_SCOPE_DENIED);
        } else {
            actor.requireReview(studentId);
        }
        long version = RequestVersion.required();
        String reason = decision == null || decision.reason() == null ? "" : decision.reason().strip();
        if (action.equals("return") && reason.codePointCount(0, reason.length()) < 10)
            throw new BusinessException(ResultCode.RETURN_REASON_TOO_SHORT);
        if (reason.length() > 500) throw new BusinessException(ResultCode.PARAM_INVALID);
        var matches = db.query("""
                SELECT id, section_code,status,version,withdraw_count FROM arc_profile
                WHERE student_id=? AND academic_year=? AND section_code=? FOR UPDATE
                """, WorkflowService::profile, studentId, academicYear, section.name());
        if (matches.isEmpty()) throw new BusinessException(ResultCode.STATE_CONFLICT);
        Profile row = matches.get(0);
        if (!row.status.equals("SUBMITTED") || row.version != version)
            throw new BusinessException(ResultCode.STATE_CONFLICT);
        if (withdrawal && row.withdrawals >= 1) throw new BusinessException(ResultCode.WITHDRAW_EXCEEDED);
        String target = switch (action) {
            case "approve" -> "APPROVED";
            case "return" -> "RETURNED";
            default -> "DRAFT";
        };
        db.update("""
                UPDATE arc_profile SET status=?,version=version+1,return_reason=?,
                withdraw_count=withdraw_count+?,completed=?,reviewer_id=?,reviewed_at=?
                WHERE id=?
                """,target,action.equals("return") ? reason : null,withdrawal ? 1 : 0,
                target.equals("APPROVED"),withdrawal ? null : actor.actorId(),
                withdrawal ? null : java.sql.Timestamp.from(java.time.Instant.now()),row.id);
        log(row, target, actor.actorId(), reason);
        return Result.success();
    }

    @Transactional
    public Result<Void> archive(long studentId, String academicYear,
            Map<String, Long> versions) {
        var actor = ArchiveIdentity.current();
        actor.requireArchive(studentId);
        actor.requireYear(academicYear);
        if (versions.size() != SectionCodeEnum.values().length) throw new BusinessException(ResultCode.STATE_CONFLICT);
        List<Profile> rows = db.query("""
                SELECT id,section_code,status,version,withdraw_count FROM arc_profile
                WHERE student_id=? AND academic_year=? ORDER BY section_code FOR UPDATE
                """, WorkflowService::profile,studentId,academicYear);
        for (var section : SectionCodeEnum.values()) {
            Long expected = versions.get(section.getCode());
            if (rows.stream().noneMatch(p -> p.code.equals(section.name()) && p.status.equals("APPROVED")
                    && Long.valueOf(p.version).equals(expected))) throw new BusinessException(ResultCode.STATE_CONFLICT);
        }
        if (rows.size() != SectionCodeEnum.values().length) throw new BusinessException(ResultCode.STATE_CONFLICT);
        for (Profile row : rows) {
            db.update("UPDATE arc_profile SET status='LOCKED',version=version+1 WHERE id=?",row.id);
            log(row,"LOCKED",actor.actorId(),null);
        }
        return Result.success();
    }

    private static Profile profile(java.sql.ResultSet r,int index) throws java.sql.SQLException {
        return new Profile(r.getLong(1),r.getString(2),r.getString(3),r.getLong(4),r.getInt(5));
    }

    private void log(Profile row, String target, long actor, String reason) {
        db.update("""
                INSERT INTO arc_profile_log(profile_id,actor_id,from_status,to_status,version,reason)
                VALUES (?,?,?,?,?,?)
                """, row.id,actor,row.status,target,row.version+1,reason);
    }
}
