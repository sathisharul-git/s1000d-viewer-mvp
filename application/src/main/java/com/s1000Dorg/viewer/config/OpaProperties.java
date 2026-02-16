package com.s1000Dorg.viewer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "s1000d.opa")
public class OpaProperties {

    private boolean enabled = true;
    private String url = "http://localhost:8181";
    private String policyPath = "/v1/data/s1000d/authz/allow";
    private boolean allowReadOnError = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getPolicyPath() {
        return policyPath;
    }

    public void setPolicyPath(String policyPath) {
        this.policyPath = policyPath;
    }

    public boolean isAllowReadOnError() {
        return allowReadOnError;
    }

    public void setAllowReadOnError(boolean allowReadOnError) {
        this.allowReadOnError = allowReadOnError;
    }
}

