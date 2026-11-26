package com.college.growth.identity;

import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.result.ResultCode;
import java.util.Set;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public record ArchiveIdentity(long actorId, Role role, Set<Long> studentIds, Set<String> academicYears) {
    public enum Role { STUDENT, MENTOR, COUNSELOR, ADMIN }

    public ArchiveIdentity {
        studentIds = Set.copyOf(studentIds);
        academicYears = Set.copyOf(academicYears);
        if (role == Role.STUDENT && !studentIds.equals(Set.of(actorId))) {
            throw new IllegalArgumentException("学生会话须绑定本人的档案");
        }
    }

    public static ArchiveIdentity current() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
                && attrs.getRequest().getAttribute(ArchiveIdentity.class.getName()) instanceof ArchiveIdentity identity) {
            return identity;
        }
        throw new BusinessException(ResultCode.UNAUTHORIZED);
    }

    public void requireRead(long studentId) { check(studentIds.contains(studentId)); }
    public void requireYear(String year) { check(year != null && academicYears.contains(year)); }

    public boolean canWrite(long studentId, String section) {
        if (!studentIds.contains(studentId)) return false;
        return switch (role) {
            case MENTOR -> "mentor".equalsIgnoreCase(section);
            case STUDENT -> !"mentor".equalsIgnoreCase(section);
            case ADMIN -> "basic".equalsIgnoreCase(section);
            case COUNSELOR -> "academic".equalsIgnoreCase(section);
        };
    }

    public void requireWrite(long studentId, String section) { check(canWrite(studentId, section)); }
    public void requireReview(long studentId) { requireRead(studentId); check(role == Role.COUNSELOR); }
    public void requireArchive(long studentId) { requireRead(studentId); check(role == Role.ADMIN); }

    private static void check(boolean allowed) {
        if (!allowed) throw new BusinessException(ResultCode.DATA_SCOPE_DENIED);
    }
}
