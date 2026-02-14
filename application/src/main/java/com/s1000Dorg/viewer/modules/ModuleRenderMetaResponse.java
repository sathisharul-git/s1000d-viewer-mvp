package com.s1000Dorg.viewer.modules;

import com.s1000Dorg.viewer.domain.ApplicabilityResult;

public record ModuleRenderMetaResponse(
    String title,
    ApplicabilityResponse applicability,
    ApplicabilityResult applicabilityResult,
    String applicabilityReason,
    String applicabilitySource
) {
}

