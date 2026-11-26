package com.college.growth.profile;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProfileValidatorsTest {
    @Test
    void configuredValidatorsRejectInvalidAndUnknownRules() {
        assertTrue(ProfileValidators.accepts("STUDENT_NO","20250001"));
        assertFalse(ProfileValidators.accepts("STUDENT_NO","ABC"));
        assertFalse(ProfileValidators.accepts("STRESS",6));
        assertFalse(ProfileValidators.accepts("UNRECOGNIZED","text"));
    }
}
