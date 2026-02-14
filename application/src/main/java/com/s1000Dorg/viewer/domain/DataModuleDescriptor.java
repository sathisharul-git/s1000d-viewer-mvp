package com.s1000Dorg.viewer.domain;

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

