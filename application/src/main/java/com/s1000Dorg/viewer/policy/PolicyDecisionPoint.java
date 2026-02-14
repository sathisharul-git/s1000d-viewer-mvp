package com.s1000Dorg.viewer.policy;

import com.s1000Dorg.viewer.applicability.ApplicabilityContext;

public interface PolicyDecisionPoint {
    PolicyDecision decide(String resourceId, ApplicabilityContext context);
}

