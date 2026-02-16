package com.s1000Dorg.viewer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "viewer.render")
public class RenderProperties {

    private boolean publishedPreferred = true;
    private boolean quickPreviewEnabled = true;
    private long cacheTtlSeconds = 3600;

    public boolean isPublishedPreferred() {
        return publishedPreferred;
    }

    public void setPublishedPreferred(boolean publishedPreferred) {
        this.publishedPreferred = publishedPreferred;
    }

    public boolean isQuickPreviewEnabled() {
        return quickPreviewEnabled;
    }

    public void setQuickPreviewEnabled(boolean quickPreviewEnabled) {
        this.quickPreviewEnabled = quickPreviewEnabled;
    }

    public long getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }

    public void setCacheTtlSeconds(long cacheTtlSeconds) {
        this.cacheTtlSeconds = cacheTtlSeconds;
    }
}
