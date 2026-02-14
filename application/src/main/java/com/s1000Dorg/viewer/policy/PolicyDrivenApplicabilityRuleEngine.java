package com.s1000Dorg.viewer.policy;

import com.s1000Dorg.viewer.applicability.ApplicabilityContext;
import com.s1000Dorg.viewer.applicability.ApplicabilityRuleEngine;
import com.s1000Dorg.viewer.domain.ApplicabilityResult;
import org.springframework.stereotype.Component;

@Component
public class PolicyDrivenApplicabilityRuleEngine implements ApplicabilityRuleEngine {

    private final PolicyDecisionPoint policyDecisionPoint;
    private final BrexValidator brexValidator;

    public PolicyDrivenApplicabilityRuleEngine(PolicyDecisionPoint policyDecisionPoint, BrexValidator brexValidator) {
        this.policyDecisionPoint = policyDecisionPoint;
        this.brexValidator = brexValidator;
    }

    @Override
    public ApplicabilityResult evaluateRules(String dmId, ApplicabilityContext context) {
        // TODO: replace placeholder payload with real DM source and BREX profile.
        brexValidator.validate(dmId, "<dm/>");
        PolicyDecision decision = policyDecisionPoint.decide(dmId, context);
        if (decision.outcome() == PolicyDecision.Outcome.DENY) {
            return ApplicabilityResult.NOT_APPLICABLE;
        }
        return ApplicabilityResult.UNKNOWN;
    }
}


