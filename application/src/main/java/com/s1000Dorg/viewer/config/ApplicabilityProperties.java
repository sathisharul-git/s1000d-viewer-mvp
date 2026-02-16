package com.s1000Dorg.viewer.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "viewer.applicability")
public class ApplicabilityProperties {

    private UnknownPolicy unknownPolicy = UnknownPolicy.INCLUDE;
    private List<String> allowedDimensions = List.of("aircraft", "engine", "variant");
    private final Toggle fragmentEvaluation = new Toggle();

    public UnknownPolicy getUnknownPolicy() {
        return unknownPolicy;
    }

    public void setUnknownPolicy(UnknownPolicy unknownPolicy) {
        this.unknownPolicy = unknownPolicy;
    }

    public List<String> getAllowedDimensions() {
        return allowedDimensions;
    }

    public void setAllowedDimensions(List<String> allowedDimensions) {
        this.allowedDimensions = allowedDimensions;
    }

    public Toggle getFragmentEvaluation() {
        return fragmentEvaluation;
    }

    public enum UnknownPolicy {
        INCLUDE,
        EXCLUDE
    }

    public static class Toggle {

        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
