package com.s1000Dorg.viewer.modules;

public record InlineApplicabilityResponse(
    String mode,
    int removedCount,
    int keptCount
) {
}

