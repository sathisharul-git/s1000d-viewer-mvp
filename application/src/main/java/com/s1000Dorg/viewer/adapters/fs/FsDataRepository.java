package com.s1000Dorg.viewer.adapters.fs;

import com.s1000Dorg.viewer.common.AppProperties;
import com.s1000Dorg.viewer.graphics.Hotspot;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class FsDataRepository {

    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9._-]+$");
    private static final List<String> GRAPHIC_EXTENSIONS = List.of(".svg", ".png", ".jpg", ".jpeg", ".gif", ".cgm");

    private final Path dataRoot;
    private final Path csdbDmDir;
    private final Path csdbMetaDir;
    private final Path csdbIcnDir;
    private final Path csdbHotspotsDir;
    private final Path publishedDmDir;
    private final Path publishedIcnDir;
    private final Path publishedHotspotsDir;
    private final Path publishedManifestPath;
    private final Path cacheDir;

    private final ObjectMapper objectMapper;
    private final PublishedManifestLoader publishedManifestLoader;

    public FsDataRepository(AppProperties appProperties, ObjectMapper objectMapper, PublishedManifestLoader publishedManifestLoader) {
        this.dataRoot = Path.of(appProperties.getDataRoot()).toAbsolutePath().normalize();
        this.csdbDmDir = dataRoot.resolve("csdb").resolve("dm").normalize();
        this.csdbMetaDir = dataRoot.resolve("csdb").resolve("meta").normalize();
        this.csdbIcnDir = dataRoot.resolve("csdb").resolve("icn").normalize();
        this.csdbHotspotsDir = dataRoot.resolve("csdb").resolve("hotspots").normalize();
        this.publishedDmDir = dataRoot.resolve("published").resolve("dm").normalize();
        this.publishedIcnDir = dataRoot.resolve("published").resolve("icn").normalize();
        this.publishedHotspotsDir = dataRoot.resolve("published").resolve("hotspots").normalize();
        this.publishedManifestPath = dataRoot.resolve("published").resolve("manifest.json").normalize();
        this.cacheDir = dataRoot.resolve("cache").normalize();

        this.objectMapper = objectMapper;
        this.publishedManifestLoader = publishedManifestLoader;
    }

    public List<Path> listDmXmlFiles() {
        if (!Files.isDirectory(csdbDmDir)) {
            return List.of();
        }

        try (var stream = Files.list(csdbDmDir)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(this::isDmFile)
                .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to list data modules", ex);
        }
    }

    public Optional<Path> findDmXml(String dmId) {
        validateSafeId(dmId, "Invalid dmId");
        Path candidate = csdbDmDir.resolve(dmId + ".xml").normalize();
        if (Files.isRegularFile(candidate)) {
            return Optional.of(candidate);
        }
        return Optional.empty();
    }

    public Optional<String> readDmXml(String dmId) {
        return findDmXml(dmId).map(path -> {
            try {
                return Files.readString(path, StandardCharsets.UTF_8);
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to read DM XML", ex);
            }
        });
    }

    public Optional<String> readPublishedHtml(String dmId) {
        validateSafeId(dmId, "Invalid dmId");
        Path htmlPath = publishedDmDir.resolve(dmId).resolve("index.html").normalize();
        if (!Files.isRegularFile(htmlPath)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(htmlPath, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read published preview", ex);
        }
    }

    public boolean hasPublishedHtml(String dmId) {
        validateSafeId(dmId, "Invalid dmId");
        return Files.isRegularFile(publishedDmDir.resolve(dmId).resolve("index.html").normalize());
    }

    public Map<String, PublishedManifestEntry> readPublishedManifest() {
        return publishedManifestLoader.load(publishedManifestPath);
    }

    public Optional<JsonNode> readMetaNode(String dmId) {
        validateSafeId(dmId, "Invalid dmId");
        Path metaPath = csdbMetaDir.resolve(dmId + ".json").normalize();
        if (!Files.isRegularFile(metaPath)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readTree(metaPath.toFile()));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read module metadata", ex);
        }
    }

    public Optional<Path> findGraphicPath(String icnId) {
        validateSafeId(icnId, "Invalid icnId");

        Path publishedCandidate = publishedIcnDir.resolve(icnId + ".svg").normalize();
        if (Files.isRegularFile(publishedCandidate)) {
            return Optional.of(publishedCandidate);
        }

        if (!Files.isDirectory(csdbIcnDir)) {
            return Optional.empty();
        }

        for (String ext : GRAPHIC_EXTENSIONS) {
            Path candidate = csdbIcnDir.resolve(icnId + ext).normalize();
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }

            Path upperCandidate = csdbIcnDir.resolve((icnId + ext).toUpperCase(Locale.ROOT)).normalize();
            if (Files.isRegularFile(upperCandidate)) {
                return Optional.of(upperCandidate);
            }
        }

        return Optional.empty();
    }

    public Optional<List<Hotspot>> readHotspots(String icnId) {
        validateSafeId(icnId, "Invalid icnId");
        Path publishedPath = publishedHotspotsDir.resolve(icnId + ".json").normalize();
        if (Files.isRegularFile(publishedPath)) {
            return Optional.of(readHotspotFile(publishedPath));
        }

        Path csdbPath = csdbHotspotsDir.resolve(icnId + ".json").normalize();
        if (Files.isRegularFile(csdbPath)) {
            return Optional.of(readHotspotFile(csdbPath));
        }

        return Optional.empty();
    }

    public Path cacheDir() {
        return cacheDir;
    }

    public Path csdbDmDir() {
        return csdbDmDir;
    }

    public Path csdbMetaDir() {
        return csdbMetaDir;
    }

    public void ensureWritableDataDirs() {
        createDirectories(csdbDmDir);
        createDirectories(csdbMetaDir);
        createDirectories(cacheDir);
    }

    private List<Hotspot> readHotspotFile(Path path) {
        try {
            return objectMapper.readValue(path.toFile(), new TypeReference<>() {
            });
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read hotspots", ex);
        }
    }

    private void validateSafeId(String value, String message) {
        if (value == null || value.isBlank() || !SAFE_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(message);
        }
    }

    private boolean isDmFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.startsWith("dmc-") && name.endsWith(".xml");
    }

    private void createDirectories(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create data directories", ex);
        }
    }
}

