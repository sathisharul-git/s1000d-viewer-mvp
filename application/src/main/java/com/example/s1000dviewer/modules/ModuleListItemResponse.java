package com.example.s1000dviewer.modules;

public record ModuleListItemResponse(
    String dmId,
    String title,
    ApplicabilityResponse applicability,
    String source,
    boolean hasPublishedPreview
) {
}
