package com.example.s1000dviewer.modules;

import com.example.s1000dviewer.domain.ApplicabilityResult;

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
