package com.example.s1000dviewer.applicability;

import com.example.s1000dviewer.domain.ApplicabilityResult;
import org.springframework.stereotype.Component;

@Component
public class Phase3ApplicabilityRuleEngine implements ApplicabilityRuleEngine {

    private final PolicyDecisionPoint policyDecisionPoint;
    private final BrexValidator brexValidator;

    public Phase3ApplicabilityRuleEngine(PolicyDecisionPoint policyDecisionPoint, BrexValidator brexValidator) {
        this.policyDecisionPoint = policyDecisionPoint;
        this.brexValidator = brexValidator;
    }

    @Override
    public ApplicabilityResult evaluateRules(String dmId, ApplicabilityContext context) {
        // TODO Phase 3: replace placeholder payload with real DM source and BREX profile.
        brexValidator.validate(dmId, "<dm/>");
        return policyDecisionPoint.decide(dmId, context);
    }
}
