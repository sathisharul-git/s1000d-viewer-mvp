package com.example.s1000dviewer.applicability;

public record ApplicabilityContext(String aircraft, String engine) {

    public static ApplicabilityContext of(String aircraft, String engine) {
        return new ApplicabilityContext(normalize(aircraft), normalize(engine));
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
