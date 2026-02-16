package com.s1000Dorg.viewer.graphics;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class SampleDataCgmConversionTest {

    private static final Path SVG_OUTPUT_DIR = Path.of("build", "test-output", "svg").toAbsolutePath().normalize();

    @ParameterizedTest(name = "Converts sample CGM: {0}")
    @MethodSource("sampleCgmFiles")
    void convertsSampleDataCgmIntoSvg(Path cgmPath) throws Exception {
        DemoCgmToSvgConverter fallback = new DemoCgmToSvgConverter();
        CgmToSvgConverter converter = buildConverter(fallback);

        String svg;
        try (InputStream input = Files.newInputStream(cgmPath)) {
            svg = converter.convert(input);
        }

        assertThat(svg, startsWith("<svg"));
        assertThat(svg, containsString("viewBox"));
        assertThat(svg, anyOf(
            containsString("Rendered via jcgm"),
            containsString("CGM conversion is not available in this runtime")
        ));

        Path savedSvg = saveSvg(cgmPath, "converted", svg);
        assertTrue(Files.isRegularFile(savedSvg));
    }

    @Test
    void demoFallbackIncludesDiagnosticsForSampleCgm() throws Exception {
        Path sample = sampleCgmFiles().findFirst()
            .orElseThrow(() -> new IllegalStateException("No sample CGM file found."));

        DemoCgmToSvgConverter converter = new DemoCgmToSvgConverter();
        String detail = "sample-data conversion diagnostics";
        long size = Files.size(sample);

        String svg;
        try (InputStream input = Files.newInputStream(sample)) {
            svg = converter.convert(input, detail);
        }

        assertThat(svg, startsWith("<svg"));
        assertThat(svg, containsString("Diagnostics: " + detail));
        assertThat(svg, containsString("Source size: " + size + " bytes"));

        Path savedSvg = saveSvg(sample, "fallback", svg);
        assertTrue(Files.isRegularFile(savedSvg));
    }

    static Stream<Path> sampleCgmFiles() {
        List<Path> files = new ArrayList<>();
        for (Path root : sampleRoots()) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (var stream = Files.list(root)) {
                stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cgm"))
                    .sorted()
                    .limit(3)
                    .forEach(files::add);
            } catch (Exception ignored) {
                // Try next candidate root.
            }
            if (!files.isEmpty()) {
                break;
            }
        }

        assertFalse(files.isEmpty(), "No sample .CGM files found under sample-data/csdb/S1000D_4-1_Bike_Samples.");
        return files.stream();
    }

    private static List<Path> sampleRoots() {
        return List.of(
            Path.of("..", "sample-data", "csdb", "S1000D_4-1_Bike_Samples").toAbsolutePath().normalize(),
            Path.of("sample-data", "csdb", "S1000D_4-1_Bike_Samples").toAbsolutePath().normalize()
        );
    }

    private CgmToSvgConverter buildConverter(DemoCgmToSvgConverter fallback) throws Exception {
        List<URL> jars = resolveJcgmJarUrls();
        if (jars.isEmpty()) {
            return new JcgmBackedCgmToSvgConverter(fallback);
        }
        URLClassLoader classLoader = new URLClassLoader(jars.toArray(new URL[0]));
        return new JcgmBackedCgmToSvgConverter(fallback, classLoader);
    }

    private List<URL> resolveJcgmJarUrls() {
        List<Path> candidates = List.of(
            Path.of("application", "libs", "jcgm"),
            Path.of("libs", "jcgm"),
            Path.of("..", "application", "libs", "jcgm")
        );
        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (!Files.isDirectory(normalized)) {
                continue;
            }

            List<URL> urls = new ArrayList<>();
            try (var stream = Files.list(normalized)) {
                stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .sorted()
                    .forEach(path -> urls.add(toUrl(path)));
            } catch (Exception ignored) {
                // Try the next candidate directory.
            }
            if (!urls.isEmpty()) {
                return urls;
            }
        }
        return List.of();
    }

    private URL toUrl(Path path) {
        try {
            return path.toUri().toURL();
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid jcgm jar URL for " + path, ex);
        }
    }

    private Path saveSvg(Path cgmPath, String mode, String svg) throws Exception {
        Files.createDirectories(SVG_OUTPUT_DIR);
        String baseName = cgmPath.getFileName().toString().replaceAll("(?i)\\.cgm$", "");
        String safeBase = baseName.replaceAll("[^A-Za-z0-9._-]", "_");
        Path outputPath = SVG_OUTPUT_DIR.resolve(safeBase + "-" + mode + ".svg");
        Files.writeString(outputPath, svg, StandardCharsets.UTF_8);
        return outputPath;
    }
}
