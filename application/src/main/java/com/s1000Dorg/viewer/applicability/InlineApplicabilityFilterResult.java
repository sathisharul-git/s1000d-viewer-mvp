package com.s1000Dorg.viewer.applicability;

public record InlineApplicabilityFilterResult(
    String xml,
    String mode,
    int removedCount,
    int keptCount
) {
    public static InlineApplicabilityFilterResult none(String xml) {
        return new InlineApplicabilityFilterResult(xml, "NONE", 0, 0);
    }
}

