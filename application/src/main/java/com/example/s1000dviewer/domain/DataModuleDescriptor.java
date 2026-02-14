package com.example.s1000dviewer.domain;

public record DataModuleDescriptor(
    String dmId,
    String title,
    Applicability applicability,
    String applicabilitySource,
    String source,
    boolean hasPublishedPreview,
    String primaryIcnId
) {
}
