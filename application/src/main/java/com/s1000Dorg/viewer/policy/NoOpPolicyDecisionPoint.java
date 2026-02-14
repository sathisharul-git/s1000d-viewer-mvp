package com.s1000Dorg.viewer.policy;

import com.s1000Dorg.viewer.applicability.ApplicabilityContext;
import org.springframework.stereotype.Component;

@Component
public class NoOpPolicyDecisionPoint implements PolicyDecisionPoint {

    @Override
    public PolicyDecision decide(String resourceId, ApplicabilityContext context) {
        // TODO: call an external ABAC PDP (for example OPA) for DM and fragment decisions.
        return PolicyDecision.allow("policy enforcement disabled");
    }
}

