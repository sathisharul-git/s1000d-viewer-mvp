package com.s1000Dorg.viewer.applicability;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ApplicabilityContextFactory {

    public ApplicabilityContext fromRequest(
        String aircraft,
        String engine,
        String variant,
        Map<String, String> requestParams
    ) {
        Map<String, String> attributes = new LinkedHashMap<>();
        if (requestParams != null) {
            for (Map.Entry<String, String> entry : requestParams.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                String key = entry.getKey().trim();
                String value = entry.getValue().trim();
                if (key.isBlank() || value.isBlank()) {
                    continue;
                }
                if (key.regionMatches(true, 0, "pa.", 0, 3)) {
                    attributes.put(key.substring(3), value);
                }
            }
        }
        return ApplicabilityContext.of(aircraft, engine, variant, attributes);
    }
}

