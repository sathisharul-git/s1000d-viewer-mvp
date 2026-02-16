package com.s1000Dorg.viewer.applicability;

import java.util.Collections;
import java.util.Map;

public record ApplicabilityContext(String aircraft, String engine, String variant, Map<String, String> productAttributes) {

    public ApplicabilityContext(String aircraft, String engine, String variant) {
        this(aircraft, engine, variant, Collections.emptyMap());
    }

    public static ApplicabilityContext of(String aircraft, String engine, String variant) {
        return new ApplicabilityContext(normalize(aircraft), normalize(engine), normalize(variant), Collections.emptyMap());
    }

    public static ApplicabilityContext of(String aircraft, String engine, String variant, Map<String, String> productAttributes) {
        return new ApplicabilityContext(normalize(aircraft), normalize(engine), normalize(variant), productAttributes == null ? Collections.emptyMap() : productAttributes);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    public String getProductAttribute(String key) {
        return productAttributes.get(key);
    }
}

