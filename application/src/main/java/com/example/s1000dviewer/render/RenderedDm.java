package com.example.s1000dviewer.render;

import com.example.s1000dviewer.domain.Applicability;
import com.example.s1000dviewer.domain.ApplicabilityResult;
import java.util.List;

public record RenderedDm(
    String dmId,
    String source,
    String html,
    String title,
    Applicability applicability,
    ApplicabilityResult applicabilityResult,
    List<String> icns,
    List<String> dmRefs
) {
}
