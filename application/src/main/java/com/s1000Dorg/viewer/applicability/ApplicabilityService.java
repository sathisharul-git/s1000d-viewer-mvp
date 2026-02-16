package com.s1000Dorg.viewer.applicability;

import com.s1000Dorg.viewer.config.ApplicabilityProperties;
import com.s1000Dorg.viewer.domain.Applicability;
import com.s1000Dorg.viewer.domain.ApplicabilityResult;
import org.springframework.stereotype.Service;

@Service
public class ApplicabilityService {

    private final ApplicabilityProvider applicabilityProvider;
    private final ApplicabilityMatcher applicabilityMatcher;
    private final ApplicabilityProperties applicabilityProperties;

    public ApplicabilityService(
        ApplicabilityProvider applicabilityProvider,
        ApplicabilityMatcher applicabilityMatcher,
        ApplicabilityProperties applicabilityProperties
    ) {
        this.applicabilityProvider = applicabilityProvider;
        this.applicabilityMatcher = applicabilityMatcher;
        this.applicabilityProperties = applicabilityProperties;
    }

    public ApplicabilityInfo resolve(String dmId) {
        return applicabilityProvider.resolve(dmId);
    }

    public ApplicabilityDecision evaluate(Applicability applicability, ApplicabilityContext context) {
        return applicabilityMatcher.evaluate(applicability, context);
    }

    public boolean includeInModuleList(ApplicabilityDecision decision) {
        if (decision.result() == ApplicabilityResult.NOT_APPLICABLE) {
            return false;
        }
        if (decision.result() == ApplicabilityResult.UNKNOWN) {
            return applicabilityProperties.getUnknownPolicy() == ApplicabilityProperties.UnknownPolicy.INCLUDE;
        }
        return true;
    }
}

