package com.example.s1000dviewer.applicability;

import com.example.s1000dviewer.domain.ApplicabilityResult;
import org.springframework.stereotype.Component;

@Component
public class Phase3ApplicabilityRuleEngine implements ApplicabilityRuleEngine {

    @Override
    public ApplicabilityResult evaluateRules(String dmId, ApplicabilityContext context) {
        // TODO Phase 3: integrate full product model + BREX aligned rule evaluation.
        return ApplicabilityResult.UNKNOWN;
    }
}
