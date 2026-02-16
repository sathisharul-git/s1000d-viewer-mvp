package com.s1000Dorg.viewer.applicability;

import com.s1000Dorg.viewer.domain.ApplicabilityResult;

public interface ApplicabilityRuleEngine {
    ApplicabilityResult evaluateRules(String dmId, ApplicabilityContext context);
}

