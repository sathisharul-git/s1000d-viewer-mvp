package com.s1000Dorg.viewer.applicability;

import com.s1000Dorg.viewer.domain.ApplicabilityResult;

public record DmApplicabilityEvaluation(
    ApplicabilityResult status,
    String displayText,
    String reason,
    String source
) {
    public static DmApplicabilityEvaluation unknown(String reason) {
        String normalizedReason = reason == null || reason.isBlank() ? "No applicability metadata found" : reason;
        return new DmApplicabilityEvaluation(ApplicabilityResult.UNKNOWN, "", normalizedReason, "none");
    }
}

