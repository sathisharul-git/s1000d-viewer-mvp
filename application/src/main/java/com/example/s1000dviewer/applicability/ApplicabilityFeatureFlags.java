package com.example.s1000dviewer.applicability;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "applicability")
public class ApplicabilityFeatureFlags {

    private final FeatureToggle fragmentEvaluation = new FeatureToggle();
    private final FeatureToggle policyEnforcement = new FeatureToggle();

    public FeatureToggle getFragmentEvaluation() {
        return fragmentEvaluation;
    }

    public FeatureToggle getPolicyEnforcement() {
        return policyEnforcement;
    }

    public static class FeatureToggle {

        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}

