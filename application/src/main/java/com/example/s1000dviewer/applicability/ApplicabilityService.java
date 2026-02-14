package com.example.s1000dviewer.applicability;

import com.example.s1000dviewer.domain.Applicability;
import org.springframework.stereotype.Service;

@Service
public class ApplicabilityService {

    private final ApplicabilityProvider applicabilityProvider;
    private final ApplicabilityMatcher applicabilityMatcher;

    public ApplicabilityService(ApplicabilityProvider applicabilityProvider, ApplicabilityMatcher applicabilityMatcher) {
        this.applicabilityProvider = applicabilityProvider;
        this.applicabilityMatcher = applicabilityMatcher;
    }

    public ApplicabilityInfo resolve(String dmId) {
        return applicabilityProvider.resolve(dmId);
    }

    public ApplicabilityDecision evaluate(Applicability applicability, ApplicabilityContext context) {
        return applicabilityMatcher.evaluate(applicability, context);
    }

    public boolean includeInModuleList(ApplicabilityDecision decision) {
        return applicabilityMatcher.includeInModuleList(decision);
    }
}
