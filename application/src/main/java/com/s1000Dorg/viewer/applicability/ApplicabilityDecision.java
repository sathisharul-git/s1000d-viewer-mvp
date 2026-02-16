package com.s1000Dorg.viewer.applicability;

import com.s1000Dorg.viewer.domain.ApplicabilityResult;

public record ApplicabilityDecision(ApplicabilityResult result, String reason) {

    public static ApplicabilityDecision of(ApplicabilityResult result, String reason) {
        String normalizedReason = reason == null || reason.isBlank() ? "no constraints requested" : reason;
        return new ApplicabilityDecision(result, normalizedReason);
    }
}


