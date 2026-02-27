package com.s1000Dorg.viewer.render;

public record InlineApplicabilitySummary(
    String mode,
    int removedCount,
    int keptCount
) {
    public static InlineApplicabilitySummary none() {
        return new InlineApplicabilitySummary("NONE", 0, 0);
    }
}

