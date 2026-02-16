package com.s1000Dorg.viewer.adapters.fs;

import com.s1000Dorg.viewer.config.StorageProperties;
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

    private final Path csdbRoot;
    private final Path csdbDmDir;
    private final Path csdbMetaDir;
    private final Path csdbIcnDir;
    private final Path csdbHotspotsDir;
    private final Path publishedDmDir;
    private final Path publishedIcnDir;
    private final Path publishedHotspotsDir;
    private final Path publishedManifestPath;
    private final Path cacheDir;
    private final Path auditDir;

    private final ObjectMapper objectMapper;
    private final PublishedManifestLoader publishedManifestLoader;

    public FsDataRepository(StorageProperties storageProperties, ObjectMapper objectMapper, PublishedManifestLoader publishedManifestLoader) {
        this.csdbRoot = Path.of(storageProperties.getCsdbRoot()).toAbsolutePath().normalize();
        Path publishedRoot = Path.of(storageProperties.getPublishedRoot()).toAbsolutePath().normalize();
        this.cacheDir = Path.of(storageProperties.getCacheRoot()).toAbsolutePath().normalize();
        this.auditDir = Path.of(storageProperties.getAuditRoot()).toAbsolutePath().normalize();

        this.csdbDmDir = resolveSubDirOrRoot(this.csdbRoot, "dm");
        this.csdbMetaDir = resolveSubDirOrRoot(this.csdbRoot, "meta");
        this.csdbIcnDir = resolveSubDirOrRoot(this.csdbRoot, "icn");
        this.csdbHotspotsDir = resolveSubDirOrRoot(this.csdbRoot, "hotspots");
        this.publishedDmDir = publishedRoot.resolve("dm").normalize();
        this.publishedIcnDir = publishedRoot.resolve("icn").normalize();
        this.publishedHotspotsDir = publishedRoot.resolve("hotspots").normalize();
        this.publishedManifestPath = publishedRoot.resolve("manifest.json").normalize();

        this.objectMapper = objectMapper;
        this.publishedManifestLoader = publishedManifestLoader;
    }

    public List<Path> listDmXmlFiles() {
        if (!Files.isDirectory(csdbDmDir)) {
            return List.of();
        }

        try (var stream = Files.walk(csdbDmDir)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(this::isDmFile)
                .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to list data modules", ex);
        }
    }

    public List<Path> listPmcXmlFiles() {
        if (!Files.isDirectory(csdbRoot)) {
            return List.of();
        }

        try (var stream = Files.walk(csdbRoot)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(this::isPmcFile)
                .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to list PMCs", ex);
        }
    }

    public List<Path> listIcnFiles() {
        if (!Files.isDirectory(csdbIcnDir)) {
            return List.of();
        }
        try (var stream = Files.walk(csdbIcnDir)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(this::isGraphicFile)
                .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to list graphics", ex);
        }
    }

    public Optional<Path> findDmXml(String dmId) {
        validateSafeId(dmId, "Invalid dmId");
        Path candidate = csdbDmDir.resolve(dmId + ".xml").normalize();
        if (Files.isRegularFile(candidate)) {
            return Optional.of(candidate);
        }
        Path upperCandidate = csdbDmDir.resolve((dmId + ".xml").toUpperCase(Locale.ROOT)).normalize();
        if (Files.isRegularFile(upperCandidate)) {
            return Optional.of(upperCandidate);
        }
        if (Files.isDirectory(csdbDmDir)) {
            try (var stream = Files.walk(csdbDmDir)) {
                Optional<Path> match = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase(dmId + ".xml"))
                    .findFirst();
                if (match.isPresent()) {
                    return match;
                }
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to resolve DM XML", ex);
            }
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
            Path legacyMetaPath = csdbMetaDir.resolve(dmId + ".meta.json").normalize();
            if (Files.isRegularFile(legacyMetaPath)) {
                metaPath = legacyMetaPath;
            } else if (Files.isDirectory(csdbMetaDir)) {
                try (var stream = Files.walk(csdbMetaDir)) {
                    Optional<Path> found = stream
                        .filter(Files::isRegularFile)
                        .filter(path -> {
                            String name = path.getFileName().toString();
                            return name.equalsIgnoreCase(dmId + ".json") || name.equalsIgnoreCase(dmId + ".meta.json");
                        })
                        .findFirst();
                    if (found.isPresent()) {
                        metaPath = found.get();
                    } else {
                        return Optional.empty();
                    }
                } catch (IOException ex) {
                    throw new IllegalStateException("Failed to resolve module metadata", ex);
                }
            } else {
                return Optional.empty();
            }
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

    public Path csdbRoot() {
        return csdbRoot;
    }

    public void ensureWritableDataDirs() {
        createDirectories(csdbDmDir);
        createDirectories(csdbMetaDir);
        createDirectories(cacheDir);
        createDirectories(auditDir);
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

    private boolean isPmcFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.startsWith("pmc-") && name.endsWith(".xml");
    }

    private boolean isGraphicFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String extension : GRAPHIC_EXTENSIONS) {
            if (name.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private void createDirectories(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create data directories", ex);
        }
    }

    private Path resolveSubDirOrRoot(Path root, String child) {
        Path candidate = root.resolve(child).normalize();
        if (Files.isDirectory(candidate)) {
            return candidate;
        }
        return root;
    }
}

