package com.s1000Dorg.viewer.applicability;

public record ApplicabilityContext(String aircraft, String engine, String variant) {

    public static ApplicabilityContext of(String aircraft, String engine, String variant) {
        return new ApplicabilityContext(normalize(aircraft), normalize(engine), normalize(variant));
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}

