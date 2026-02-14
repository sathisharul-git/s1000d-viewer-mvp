package com.s1000Dorg.viewer.applicability;

import com.s1000Dorg.viewer.adapters.fs.FsDataRepository;
import com.s1000Dorg.viewer.adapters.fs.PublishedManifestEntry;
import com.s1000Dorg.viewer.domain.Applicability;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class MetadataApplicabilityProvider implements ApplicabilityProvider {

    private final FsDataRepository repository;

    public MetadataApplicabilityProvider(FsDataRepository repository) {
        this.repository = repository;
    }

    @Override
    public ApplicabilityInfo resolve(String dmId) {
        Map<String, PublishedManifestEntry> manifest = repository.readPublishedManifest();
        PublishedManifestEntry entry = manifest.get(dmId.toUpperCase(Locale.ROOT));
        if (entry != null && entry.applicability() != null && !entry.applicability().isUnknown()) {
            return new ApplicabilityInfo(entry.applicability(), ApplicabilitySource.PUBLISHED);
        }

        Optional<JsonNode> sidecarNode = repository.readMetaNode(dmId);
        if (sidecarNode.isPresent()) {
            Applicability applicability = parseSidecarApplicability(sidecarNode.get());
            if (!applicability.isUnknown()) {
                return new ApplicabilityInfo(applicability, ApplicabilitySource.META);
            }
        }

        return new ApplicabilityInfo(Applicability.unknown(), ApplicabilitySource.NONE);
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


