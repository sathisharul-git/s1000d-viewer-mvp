package com.s1000Dorg.viewer.render;

import com.s1000Dorg.viewer.domain.Applicability;
import com.s1000Dorg.viewer.domain.ApplicabilityResult;
import java.util.List;

public record RenderedDm(
    String dmId,
    String source,
    String html,
    String title,
    Applicability applicability,
    ApplicabilityResult applicabilityResult,
    List<String> icns,
    List<String> dmRefs,
    InlineApplicabilitySummary inlineApplicability
) {
}

