package com.example.s1000dviewer.adapters.fs;

import com.example.s1000dviewer.domain.Applicability;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PublishedManifestLoader {

    private final ObjectMapper objectMapper;

    public PublishedManifestLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, PublishedManifestEntry> load(Path manifestPath) {
        if (manifestPath == null || !Files.isRegularFile(manifestPath)) {
            return Map.of();
        }

        try {
            JsonNode root = objectMapper.readTree(manifestPath.toFile());
            JsonNode modules = root.path("modules");
            if (!modules.isArray()) {
                return Map.of();
            }

            Map<String, PublishedManifestEntry> entries = new LinkedHashMap<>();
            for (JsonNode module : modules) {
                String dmId = text(module, "dmId");
                if (dmId.isBlank()) {
                    continue;
                }

                String title = text(module, "title");
                Applicability applicability = toApplicability(module.path("applicability"));
                List<String> icns = readStringList(module.path("icns"));
                List<String> dmRefs = readStringList(module.path("dmRefs"));

                PublishedManifestEntry entry = new PublishedManifestEntry(dmId, title, applicability, icns, dmRefs);
                entries.put(dmId.toUpperCase(Locale.ROOT), entry);
            }
            return entries;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read published manifest", ex);
        }
    }

    private Applicability toApplicability(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Applicability.unknown();
        }
        return new Applicability(
            readStringList(node.path("aircraft")),
            readStringList(node.path("engine")),
            readStringList(node.path("variant"))
        );
    }

    private List<String> readStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item != null && item.isTextual()) {
                String value = item.asText("").trim();
                if (!value.isBlank()) {
                    values.add(value);
                }
            }
        }
        return values;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText("").trim() : "";
    }
}
