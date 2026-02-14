package com.example.s1000dviewer.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "s1000d")
public class AppProperties {

    private String dataRoot = "../data";
    private String jwtSecret = "change-me-in-real-env-change-me-in-real-env-change-me";
    private long jwtExpirationSeconds = 28800;
    private boolean phase2SectionApplicabilityEnabled = false;
    private boolean phase3RuleEngineEnabled = false;

    public String getDataRoot() {
        return dataRoot;
    }

    public void setDataRoot(String dataRoot) {
        this.dataRoot = dataRoot;
    }

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public long getJwtExpirationSeconds() {
        return jwtExpirationSeconds;
    }

    public void setJwtExpirationSeconds(long jwtExpirationSeconds) {
        this.jwtExpirationSeconds = jwtExpirationSeconds;
    }

    public boolean isPhase2SectionApplicabilityEnabled() {
        return phase2SectionApplicabilityEnabled;
    }

    public void setPhase2SectionApplicabilityEnabled(boolean phase2SectionApplicabilityEnabled) {
        this.phase2SectionApplicabilityEnabled = phase2SectionApplicabilityEnabled;
    }

    public boolean isPhase3RuleEngineEnabled() {
        return phase3RuleEngineEnabled;
    }

    public void setPhase3RuleEngineEnabled(boolean phase3RuleEngineEnabled) {
        this.phase3RuleEngineEnabled = phase3RuleEngineEnabled;
    }
}
