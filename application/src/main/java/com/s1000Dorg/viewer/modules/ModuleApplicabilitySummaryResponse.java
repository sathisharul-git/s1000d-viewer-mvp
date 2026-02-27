package com.s1000Dorg.viewer.modules;

import com.s1000Dorg.viewer.domain.ApplicabilityResult;

public record ModuleApplicabilitySummaryResponse(
    ApplicabilityResult dmStatus,
    String displayText,
    String reason,
    String source
) {
}

