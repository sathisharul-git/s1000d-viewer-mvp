package com.s1000Dorg.viewer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "viewer.storage")
public class StorageProperties {

    private String csdbRoot = "./data/csdb";
    private String publishedRoot = "./data/published";
    private String cacheRoot = "./data/cache";
    private String auditRoot = "./data/audit";

    public String getCsdbRoot() {
        return csdbRoot;
    }

    public void setCsdbRoot(String csdbRoot) {
        this.csdbRoot = csdbRoot;
    }

    public String getPublishedRoot() {
        return publishedRoot;
    }

    public void setPublishedRoot(String publishedRoot) {
        this.publishedRoot = publishedRoot;
    }

    public String getCacheRoot() {
        return cacheRoot;
    }

    public void setCacheRoot(String cacheRoot) {
        this.cacheRoot = cacheRoot;
    }

    public String getAuditRoot() {
        return auditRoot;
    }

    public void setAuditRoot(String auditRoot) {
        this.auditRoot = auditRoot;
    }
}
