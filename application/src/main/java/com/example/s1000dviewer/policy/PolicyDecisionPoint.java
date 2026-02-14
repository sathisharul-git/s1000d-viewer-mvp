package com.example.s1000dviewer.policy;

import com.example.s1000dviewer.applicability.ApplicabilityContext;

public interface PolicyDecisionPoint {
    PolicyDecision decide(String resourceId, ApplicabilityContext context);
}
