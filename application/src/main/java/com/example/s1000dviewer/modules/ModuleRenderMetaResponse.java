package com.example.s1000dviewer.modules;

import com.example.s1000dviewer.domain.ApplicabilityResult;

public record ModuleRenderMetaResponse(String title, ApplicabilityResponse applicability, ApplicabilityResult applicabilityResult) {
}
