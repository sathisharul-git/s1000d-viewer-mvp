package com.example.s1000dviewer.applicability;

import com.example.s1000dviewer.domain.ApplicabilityResult;

public record ApplicabilityDecision(ApplicabilityResult result, String reason) {

    public static ApplicabilityDecision of(ApplicabilityResult result, String reason) {
        String normalizedReason = reason == null || reason.isBlank() ? "no constraints requested" : reason;
        return new ApplicabilityDecision(result, normalizedReason);
    }
}

