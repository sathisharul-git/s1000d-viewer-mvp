package com.s1000Dorg.viewer.modules;

import com.s1000Dorg.viewer.domain.ApplicabilityResult;

public record ModuleListItemResponse(
    String dmId,
    String title,
    ApplicabilityResponse applicability,
    String source,
    boolean hasPublishedPreview,
    ApplicabilityResult applicabilityResult,
    String applicabilityReason,
    String applicabilitySource
) {
}

