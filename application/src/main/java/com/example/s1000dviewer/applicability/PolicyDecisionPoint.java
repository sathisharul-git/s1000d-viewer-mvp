package com.example.s1000dviewer.applicability;

import com.example.s1000dviewer.domain.ApplicabilityResult;

public interface PolicyDecisionPoint {
    ApplicabilityResult decide(String resourceId, ApplicabilityContext context);
}
