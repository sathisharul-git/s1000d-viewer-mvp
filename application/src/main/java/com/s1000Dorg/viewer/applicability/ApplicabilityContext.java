package com.s1000Dorg.viewer.applicability;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;

public record ApplicabilityContext(String aircraft, String engine, String variant, Map<String, String> productAttributes) {

    public ApplicabilityContext {
        Map<String, String> normalized = new LinkedHashMap<>();
        if (productAttributes != null) {
            for (Map.Entry<String, String> entry : productAttributes.entrySet()) {
                String key = normalize(entry.getKey());
                String value = normalize(entry.getValue());
                if (key == null || value == null) {
                    continue;
                }
                normalized.put(key.toLowerCase(), value);
            }
        }
        if (normalize(aircraft) != null) {
            normalized.putIfAbsent("aircraft", normalize(aircraft));
        }
        if (normalize(engine) != null) {
            normalized.putIfAbsent("engine", normalize(engine));
        }
        if (normalize(variant) != null) {
            normalized.putIfAbsent("variant", normalize(variant));
        }
        productAttributes = Collections.unmodifiableMap(normalized);
    }

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
        if (key == null || key.isBlank()) {
            return null;
        }
        String normalizedKey = key.trim().toLowerCase();
        return productAttributes.get(normalizedKey);
    }

    public Map<String, String> allProductAttributes() {
        return productAttributes;
    }
}

