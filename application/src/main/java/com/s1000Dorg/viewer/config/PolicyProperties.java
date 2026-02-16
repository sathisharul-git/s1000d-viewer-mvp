package com.s1000Dorg.viewer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "viewer.policy")
public class PolicyProperties {

    private final Toggle enforcement = new Toggle();

    public Toggle getEnforcement() {
        return enforcement;
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
