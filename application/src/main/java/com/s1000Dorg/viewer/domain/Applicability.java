package com.s1000Dorg.viewer.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record Applicability(List<String> aircraft, List<String> engine, List<String> variant) {

    public Applicability {
        aircraft = normalizeList(aircraft);
        engine = normalizeList(engine);
        variant = normalizeList(variant);
    }

    public static Applicability unknown() {
        return new Applicability(List.of(), List.of(), List.of());
    }

    public boolean isUnknown() {
        return aircraft.isEmpty() && engine.isEmpty() && variant.isEmpty();
    }

    private static List<String> normalizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (!trimmed.isBlank()) {
                normalized.add(trimmed);
            }
        }
        if (normalized.isEmpty()) {
            return List.of();
        }
        return Collections.unmodifiableList(normalized);
    }
}

