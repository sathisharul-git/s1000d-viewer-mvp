package com.example.s1000dviewer.applicability;

import com.example.s1000dviewer.domain.ApplicabilityResult;

public interface ApplicabilityRuleEngine {
    ApplicabilityResult evaluateRules(String dmId, ApplicabilityContext context);
}
