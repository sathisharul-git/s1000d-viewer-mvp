package com.s1000Dorg.viewer.graphics;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class CgmHotspotOverlayTest {

    private static final Pattern HOTSPOT_ID_PATTERN = Pattern.compile("data-hotspot-id=\"([^\"]+)\"");

    @Test
    void hotspotSampleProducesOverlayLayerWithStableIdentifiers() throws Exception {
        Path sampleCgm = resolveHotspotSample();
        DemoCgmToSvgConverter fallback = new DemoCgmToSvgConverter();
        CgmToSvgConverter converter = buildConverter(fallback);

        String svgFirst = convert(converter, sampleCgm);
        String svgSecond = convert(converter, sampleCgm);

        assertThat(svgFirst, containsString("id=\"hotspot-overlay-layer\""));
        assertThat(svgFirst, containsString("class=\"s1000d-hotspot\""));
        assertThat(svgFirst, containsString("data-hotspot-id=\""));
        assertThat(svgFirst, anyOf(
            containsString("<polygon class=\"s1000d-hotspot-shape\""),
            containsString("<rect class=\"s1000d-hotspot-shape\""),
            containsString("<path class=\"s1000d-hotspot-shape\"")
        ));

        List<String> firstIds = extractHotspotIds(svgFirst);
        List<String> secondIds = extractHotspotIds(svgSecond);
        assertFalse(firstIds.isEmpty(), "Expected at least one hotspot id in overlay output.");
        assertEquals(firstIds, secondIds, "Hotspot IDs must remain stable across repeated conversions.");
    }

    @Test
    void cgmWithoutHotspotsKeepsSvgOutputWithoutOverlayLayer() throws Exception {
        DemoCgmToSvgConverter fallback = new DemoCgmToSvgConverter();
        CgmToSvgConverter converter = buildConverter(fallback);

        try (InputStream input = new ClassPathResource("cgm/ICN-DEMO-CGM-0001.cgm").getInputStream()) {
            String svg = converter.convert(input);
            assertThat(svg, not(containsString("id=\"hotspot-overlay-layer\"")));
        }
    }

    private String convert(CgmToSvgConverter converter, Path cgmPath) throws Exception {
        try (InputStream input = Files.newInputStream(cgmPath)) {
            return converter.convert(input);
        }
    }

    private List<String> extractHotspotIds(String svg) {
        List<String> ids = new ArrayList<>();
        Matcher matcher = HOTSPOT_ID_PATTERN.matcher(svg);
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        return ids;
    }

    private Path resolveHotspotSample() {
        List<Path> candidates = List.of(
            Path.of("..", "sample-data", "csdb", "S1000D_4-1_Bike_Samples", "ICN-S1000DBIKE-AAA-D000000-0-U8025-00536-A-04-1.CGM"),
            Path.of("sample-data", "csdb", "S1000D_4-1_Bike_Samples", "ICN-S1000DBIKE-AAA-D000000-0-U8025-00536-A-04-1.CGM")
        );
        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (Files.isRegularFile(normalized)) {
                return normalized;
            }
        }
        throw new IllegalStateException("Hotspot sample CGM not found: ICN-S1000DBIKE-AAA-D000000-0-U8025-00536-A-04-1.CGM");
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
                // Try next path.
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
}
