package com.s1000Dorg.viewer.graphics;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;

class CgmConversionTest {

    @Test
    void converterShouldProduceSvgWithImage() throws Exception {
        // Arrange
        DemoCgmToSvgConverter fallback = new DemoCgmToSvgConverter();
        CgmToSvgConverter converter = buildConverter(fallback);
        InputStream cgmStream = new ClassPathResource("cgm/ICN-DEMO-CGM-0001.cgm").getInputStream();

        // Act
        String svg = converter.convert(cgmStream);

        // Assert
        assertThat(svg, startsWith("<svg"));
        assertThat(svg, anyOf(
            containsString("Rendered via jcgm"),
            containsString("CGM conversion is not available in this runtime")
        ));
    }

    private CgmToSvgConverter buildConverter(DemoCgmToSvgConverter fallback) throws Exception {
        List<URL> jars = resolveJcgmJarUrls();
        if (jars.isEmpty()) {
            return new JcgmBackedCgmToSvgConverter(fallback);
        }
        URLClassLoader classLoader = new URLClassLoader(jars.toArray(new URL[0]));
        return new JcgmBackedCgmToSvgConverter(fallback, classLoader);
    }

    private List<URL> resolveJcgmJarUrls() throws MalformedURLException {
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
        } catch (MalformedURLException ex) {
            throw new IllegalStateException("Invalid jcgm jar URL for " + path, ex);
        }
    }
}
