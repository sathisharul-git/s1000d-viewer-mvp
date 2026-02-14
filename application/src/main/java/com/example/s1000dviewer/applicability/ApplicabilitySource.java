package com.example.s1000dviewer.applicability;

public enum ApplicabilitySource {
    PUBLISHED,
    META,
    NONE;

    public String toApiValue() {
        return name().toLowerCase();
    }
}
