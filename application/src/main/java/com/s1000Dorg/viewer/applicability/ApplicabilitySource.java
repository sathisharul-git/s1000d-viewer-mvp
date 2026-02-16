package com.s1000Dorg.viewer.applicability;

public enum ApplicabilitySource {
    PUBLISHED,
    META,
    NONE;
    public String toApiValue() {
        return name().toLowerCase();
    }
}

