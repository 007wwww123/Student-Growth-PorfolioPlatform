package com.college.growth.identity;

import static org.junit.jupiter.api.Assertions.*;
import com.college.growth.common.exception.BusinessException;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ArchiveIdentityTest {
    private ArchiveIdentity identity(ArchiveIdentity.Role role) {
        return new ArchiveIdentity(7, role, Set.of(7L), Set.of("2025-2026-1"));
    }

    @Test void studentCanOnlyEditOwnStudentSections() {
        var student = identity(ArchiveIdentity.Role.STUDENT);
        assertTrue(student.canWrite(7, "BASIC"));
        assertFalse(student.canWrite(8, "basic"));
        assertFalse(student.canWrite(7, "MENTOR"));
        assertThrows(BusinessException.class, () -> student.requireYear("2024-2025-1"));
        assertThrows(BusinessException.class, () -> student.requireReview(7));
        assertThrows(IllegalArgumentException.class, () -> new ArchiveIdentity(7,
                ArchiveIdentity.Role.STUDENT, Set.of(8L), Set.of("2025-2026-1")));
    }

    @Test void staffStayWithinAssignedRoleAndScope() {
        var mentor = identity(ArchiveIdentity.Role.MENTOR);
        assertTrue(mentor.canWrite(7, "mentor"));
        assertFalse(mentor.canWrite(7, "life"));
        var reviewer = identity(ArchiveIdentity.Role.COUNSELOR);
        assertTrue(reviewer.canWrite(7, "academic"));
        assertFalse(reviewer.canWrite(7, "basic"));
        reviewer.requireReview(7);
        assertThrows(BusinessException.class, () -> reviewer.requireReview(8));
        assertThrows(BusinessException.class, () -> reviewer.requireArchive(7));
        identity(ArchiveIdentity.Role.ADMIN).requireArchive(7);
    }

    @Test void bearerMustBePresentAndWellFormed() {
        assertThrows(BusinessException.class, () -> SessionAccess.tokenHash(null));
        assertThrows(BusinessException.class, () -> SessionAccess.tokenHash("Bearer short"));
        assertEquals(64, SessionAccess.tokenHash("Bearer " + "A".repeat(43)).length());
    }
}
