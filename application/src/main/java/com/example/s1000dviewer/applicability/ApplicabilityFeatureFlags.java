package com.example.s1000dviewer.applicability;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "applicability")
public class ApplicabilityFeatureFlags {

    private final PhaseFlag phase2 = new PhaseFlag();
    private final PhaseFlag phase3 = new PhaseFlag();

    public PhaseFlag getPhase2() {
        return phase2;
    }

    public PhaseFlag getPhase3() {
        return phase3;
    }

    public static class PhaseFlag {

        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
