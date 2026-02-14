package com.example.s1000dviewer.applicability;

import com.example.s1000dviewer.domain.ApplicabilityResult;
import org.springframework.stereotype.Component;

@Component
public class NoOpPolicyDecisionPoint implements PolicyDecisionPoint {

    @Override
    public ApplicabilityResult decide(String resourceId, ApplicabilityContext context) {
        // TODO Phase 3: call an external ABAC PDP (for example OPA) for DM and fragment decisions.
        return ApplicabilityResult.UNKNOWN;
    }
}
