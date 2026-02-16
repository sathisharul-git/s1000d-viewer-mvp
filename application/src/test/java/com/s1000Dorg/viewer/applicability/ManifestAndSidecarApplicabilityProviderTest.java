package com.s1000Dorg.viewer.applicability;

import com.s1000Dorg.viewer.adapters.fs.FsDataRepository;
import com.s1000Dorg.viewer.adapters.fs.PublishedManifestLoader;
import com.s1000Dorg.viewer.config.StorageProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataApplicabilityProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void prefersPublishedApplicabilityOverSidecar() throws IOException {
        String dmId = "DMC-SAMPLE-TEST-0001";
        writeManifest("""
            {
              "modules": [
                {
                  "dmId": "DMC-SAMPLE-TEST-0001",
                  "applicability": {
                    "aircraft": ["A320"],
                    "engine": ["CFM56"],
                    "variant": ["MOD-12"]
                  }
                }
              ]
            }
            """);
        writeSidecar(dmId, """
            {
              "dmId": "DMC-SAMPLE-TEST-0001",
              "applicability": {
                "aircraft": ["A350"],
                "engine": ["XWB"],
                "variant": ["MOD-22"]
              }
            }
            """);

        MetadataApplicabilityProvider provider = new MetadataApplicabilityProvider(repository());
        ApplicabilityInfo result = provider.resolve(dmId);

        assertThat(result.source()).isEqualTo(ApplicabilitySource.PUBLISHED);
        assertThat(result.applicability().aircraft()).containsExactly("A320");
        assertThat(result.applicability().engine()).containsExactly("CFM56");
        assertThat(result.applicability().variant()).containsExactly("MOD-12");
    }

    @Test
    void fallsBackToSidecarWhenPublishedEntryMissing() throws IOException {
        String dmId = "DMC-SAMPLE-TEST-0002";
        writeManifest("{\"modules\":[]}");
        writeSidecar(dmId, """
            {
              "dmId": "DMC-SAMPLE-TEST-0002",
              "applicability": {
                "aircraft": ["A350"],
                "engine": ["XWB"],
                "variant": ["MOD-22"]
              }
            }
            """);

        MetadataApplicabilityProvider provider = new MetadataApplicabilityProvider(repository());
        ApplicabilityInfo result = provider.resolve(dmId);

        assertThat(result.source()).isEqualTo(ApplicabilitySource.META);
        assertThat(result.applicability().aircraft()).containsExactly("A350");
        assertThat(result.applicability().engine()).containsExactly("XWB");
        assertThat(result.applicability().variant()).containsExactly("MOD-22");
    }

    @Test
    void returnsNoneWhenNoApplicabilityMetadataExists() throws IOException {
        writeManifest("{\"modules\":[]}");
        MetadataApplicabilityProvider provider = new MetadataApplicabilityProvider(repository());

        ApplicabilityInfo result = provider.resolve("DMC-SAMPLE-UNKNOWN");

        assertThat(result.source()).isEqualTo(ApplicabilitySource.NONE);
        assertThat(result.applicability().isUnknown()).isTrue();
    }

    private FsDataRepository repository() throws IOException {
        Files.createDirectories(tempDir.resolve("csdb").resolve("dm"));
        Files.createDirectories(tempDir.resolve("csdb").resolve("meta"));
        Files.createDirectories(tempDir.resolve("csdb").resolve("icn"));
        Files.createDirectories(tempDir.resolve("csdb").resolve("hotspots"));
        Files.createDirectories(tempDir.resolve("published").resolve("dm"));
        Files.createDirectories(tempDir.resolve("published").resolve("icn"));
        Files.createDirectories(tempDir.resolve("published").resolve("hotspots"));
        Files.createDirectories(tempDir.resolve("cache"));

        StorageProperties storageProperties = new StorageProperties();
        storageProperties.setCsdbRoot(tempDir.resolve("csdb").toString());
        storageProperties.setPublishedRoot(tempDir.resolve("published").toString());
        storageProperties.setCacheRoot(tempDir.resolve("cache").toString());
        storageProperties.setAuditRoot(tempDir.resolve("audit").toString());
        return new FsDataRepository(storageProperties, objectMapper, new PublishedManifestLoader(objectMapper));
    }

    private void writeManifest(String json) throws IOException {
        Path manifestPath = tempDir.resolve("published").resolve("manifest.json");
        Files.createDirectories(manifestPath.getParent());
        Files.writeString(manifestPath, json);
    }

    private void writeSidecar(String dmId, String json) throws IOException {
        Path sidecarPath = tempDir.resolve("csdb").resolve("meta").resolve(dmId + ".json");
        Files.createDirectories(sidecarPath.getParent());
        Files.writeString(sidecarPath, json);
    }
}


