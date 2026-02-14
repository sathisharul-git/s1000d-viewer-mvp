package com.example.s1000dviewer.applicability;

import com.example.s1000dviewer.domain.ApplicabilityResult;

public record ApplicabilityMatchDecision(ApplicabilityResult result, String reason) {

    public static ApplicabilityMatchDecision of(ApplicabilityResult result, String reason) {
        String normalizedReason = reason == null || reason.isBlank() ? "no constraints requested" : reason;
        return new ApplicabilityMatchDecision(result, normalizedReason);
    }
}
