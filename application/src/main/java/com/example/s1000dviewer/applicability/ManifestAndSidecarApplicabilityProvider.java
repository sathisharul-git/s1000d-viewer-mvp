package com.example.s1000dviewer.applicability;

import com.example.s1000dviewer.adapters.fs.FsDataRepository;
import com.example.s1000dviewer.adapters.fs.PublishedManifestEntry;
import com.example.s1000dviewer.domain.Applicability;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ManifestAndSidecarApplicabilityProvider implements ApplicabilityProvider {

    private final FsDataRepository repository;

    public ManifestAndSidecarApplicabilityProvider(FsDataRepository repository) {
        this.repository = repository;
    }

    @Override
    public ApplicabilityResolution resolve(String dmId) {
        Map<String, PublishedManifestEntry> manifest = repository.readPublishedManifest();
        PublishedManifestEntry entry = manifest.get(dmId.toUpperCase(Locale.ROOT));
        if (entry != null && entry.applicability() != null && !entry.applicability().isUnknown()) {
            return new ApplicabilityResolution(entry.applicability(), ApplicabilitySource.PUBLISHED);
        }

        Optional<JsonNode> sidecarNode = repository.readMetaNode(dmId);
        if (sidecarNode.isPresent()) {
            Applicability applicability = parseSidecarApplicability(sidecarNode.get());
            if (!applicability.isUnknown()) {
                return new ApplicabilityResolution(applicability, ApplicabilitySource.META);
            }
        }

        return new ApplicabilityResolution(Applicability.unknown(), ApplicabilitySource.NONE);
    }

    private Applicability parseSidecarApplicability(JsonNode node) {
        JsonNode applicabilityNode = node.path("applicability");
        if (applicabilityNode.isObject()) {
            return new Applicability(
                readStringList(applicabilityNode.path("aircraft")),
                readStringList(applicabilityNode.path("engine")),
                readStringList(applicabilityNode.path("variant"))
            );
        }

        String aircraft = readLegacyValue(node.path("aircraft"));
        String engine = readLegacyValue(node.path("engine"));
        String variant = readLegacyValue(node.path("variant"));
        return new Applicability(singletonOrEmpty(aircraft), singletonOrEmpty(engine), singletonOrEmpty(variant));
    }

    private List<String> readStringList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(node.spliterator(), false)
            .filter(JsonNode::isTextual)
            .map(n -> n.asText("").trim())
            .filter(value -> !value.isBlank())
            .toList();
    }

    private String readLegacyValue(JsonNode node) {
        if (!node.isTextual()) {
            return "";
        }
        String value = node.asText("").trim();
        if (value.equalsIgnoreCase("ALL")) {
            return "";
        }
        return value;
    }

    private List<String> singletonOrEmpty(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value);
    }
}
