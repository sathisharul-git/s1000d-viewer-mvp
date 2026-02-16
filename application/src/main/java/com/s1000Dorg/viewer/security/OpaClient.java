package com.s1000Dorg.viewer.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.s1000Dorg.viewer.config.OpaProperties;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class OpaClient {

    private final OpaProperties opaProperties;
    private final RestTemplate restTemplate;

    public OpaClient(OpaProperties opaProperties) {
        this.opaProperties = opaProperties;
        this.restTemplate = new RestTemplate();
    }

    public Optional<Boolean> evaluate(Map<String, Object> input) {
        if (!opaProperties.isEnabled()) {
            return Optional.empty();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(Map.of("input", input), headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                endpointUrl(),
                entity,
                JsonNode.class
            );
            JsonNode body = response.getBody();
            if (body == null) {
                return Optional.empty();
            }

            JsonNode result = body.path("result");
            if (result.isBoolean()) {
                return Optional.of(result.asBoolean());
            }
            if (result.has("allow") && result.get("allow").isBoolean()) {
                return Optional.of(result.get("allow").asBoolean());
            }
            return Optional.empty();
        } catch (RestClientException ex) {
            return Optional.empty();
        }
    }

    private String endpointUrl() {
        String base = trimTrailingSlash(opaProperties.getUrl());
        String path = opaProperties.getPolicyPath();
        if (path == null || path.isBlank()) {
            return base + "/v1/data/s1000d/authz/allow";
        }
        return path.startsWith("/") ? base + path : base + "/" + path;
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8181";
        }
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}

