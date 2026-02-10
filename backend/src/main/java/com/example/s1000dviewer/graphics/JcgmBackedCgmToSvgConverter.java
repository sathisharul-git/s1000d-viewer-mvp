package com.example.s1000dviewer.graphics;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class JcgmBackedCgmToSvgConverter implements CgmToSvgConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(JcgmBackedCgmToSvgConverter.class);
    private static final String CGM_READER_SPI_CLASS = "net.sf.jcgm.imageio.plugins.cgm.CGMImageReaderSpi";

    private final DemoCgmToSvgConverter fallbackConverter;
    private final Object probeLock = new Object();

    private volatile boolean probeComplete = false;
    private volatile boolean jcgmAvailable = false;
    private volatile ClassLoader jcgmClassLoader;

    public JcgmBackedCgmToSvgConverter(DemoCgmToSvgConverter fallbackConverter) {
        this.fallbackConverter = fallbackConverter;
    }

    @Override
    public String convert(InputStream cgmStream) throws IOException {
        byte[] payload = cgmStream.readAllBytes();
        if (payload.length == 0) {
            return fallbackConverter.convert(new ByteArrayInputStream(payload));
        }

        Optional<BufferedImage> decoded = tryDecodeWithJcgm(payload);
        if (decoded.isPresent()) {
            return wrapImageAsSvg(decoded.get(), payload);
        }

        return fallbackConverter.convert(new ByteArrayInputStream(payload));
    }

    private Optional<BufferedImage> tryDecodeWithJcgm(byte[] payload) {
        if (!ensureJcgmAvailable()) {
            return Optional.empty();
        }

        try {
            Class<?> spiClass = Class.forName(CGM_READER_SPI_CLASS, true, jcgmClassLoader);
            Object spi = spiClass.getDeclaredConstructor().newInstance();
            if (!(spi instanceof javax.imageio.spi.ImageReaderSpi imageReaderSpi)) {
                LOGGER.warn("CGM reader SPI does not implement ImageReaderSpi: {}", spiClass.getName());
                return Optional.empty();
            }

            ImageReader reader = imageReaderSpi.createReaderInstance();
            try (ImageInputStream imageInput = ImageIO.createImageInputStream(new ByteArrayInputStream(payload))) {
                reader.setInput(imageInput, true, true);
                BufferedImage image = reader.read(0);
                return Optional.ofNullable(image);
            } finally {
                reader.dispose();
            }
        } catch (Exception ex) {
            LOGGER.warn("jcgm conversion failed, using fallback converter: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private boolean ensureJcgmAvailable() {
        if (probeComplete) {
            return jcgmAvailable;
        }

        synchronized (probeLock) {
            if (probeComplete) {
                return jcgmAvailable;
            }

            ClassLoader resolved = resolveJcgmClassLoader();
            if (resolved != null) {
                jcgmClassLoader = resolved;
                jcgmAvailable = true;
                LOGGER.info("Using jcgm CGM reader: {}", classLoaderDescription(resolved));
            } else {
                jcgmAvailable = false;
                LOGGER.info("jcgm jars not found. Using fallback CGM converter.");
            }

            probeComplete = true;
            return jcgmAvailable;
        }
    }

    private ClassLoader resolveJcgmClassLoader() {
        ClassLoader appLoader = Thread.currentThread().getContextClassLoader();
        if (classExists(CGM_READER_SPI_CLASS, appLoader)) {
            return appLoader;
        }

        for (Path jarDir : candidateJarDirs()) {
            Optional<ClassLoader> loader = tryBuildJarClassLoader(jarDir);
            if (loader.isPresent() && classExists(CGM_READER_SPI_CLASS, loader.get())) {
                return loader.get();
            }
        }

        return null;
    }

    private List<Path> candidateJarDirs() {
        List<Path> candidates = new ArrayList<>();
        candidates.add(Path.of("backend", "libs", "jcgm"));
        candidates.add(Path.of("libs", "jcgm"));
        candidates.add(Path.of("..", "backend", "libs", "jcgm"));
        return candidates;
    }

    private Optional<ClassLoader> tryBuildJarClassLoader(Path jarDir) {
        Path normalized = jarDir.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) {
            return Optional.empty();
        }

        try {
            List<URL> urls;
            try (var stream = Files.list(normalized)) {
                urls = stream
                    .filter(path -> Files.isRegularFile(path))
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .map(path -> {
                        try {
                            return path.toUri().toURL();
                        } catch (Exception ex) {
                            throw new IllegalStateException("Invalid jar URL for " + path, ex);
                        }
                    })
                    .toList();
            }

            if (urls.isEmpty()) {
                return Optional.empty();
            }

            ClassLoader parent = Thread.currentThread().getContextClassLoader();
            return Optional.of(new URLClassLoader(urls.toArray(URL[]::new), parent));
        } catch (IOException ex) {
            LOGGER.warn("Unable to scan jcgm jar directory {}: {}", normalized, ex.getMessage());
            return Optional.empty();
        }
    }

    private boolean classExists(String fqcn, ClassLoader classLoader) {
        if (classLoader == null) {
            return false;
        }
        try {
            Class.forName(fqcn, true, classLoader);
            return true;
        } catch (Throwable ex) {
            return false;
        }
    }

    private String wrapImageAsSvg(BufferedImage image, byte[] cgmPayload) throws IOException {
        ByteArrayOutputStream pngBytes = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", pngBytes)) {
            return fallbackConverter.convert(new ByteArrayInputStream(cgmPayload));
        }
        String base64 = Base64.getEncoder().encodeToString(pngBytes.toByteArray());
        int width = Math.max(1, image.getWidth());
        int height = Math.max(1, image.getHeight());
        int sourceBytes = cgmPayload.length;

        return """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %d %d" role="img" aria-label="CGM rendered image">
              <rect width="%d" height="%d" fill="#f8fafc" />
              <image href="data:image/png;base64,%s" x="0" y="0" width="%d" height="%d" preserveAspectRatio="xMidYMid meet" />
              <rect x="8" y="8" width="%d" height="28" fill="#0f172a" fill-opacity="0.82" rx="6" />
              <text x="16" y="27" fill="#e2e8f0" font-size="14" font-family="Segoe UI, sans-serif">Rendered via jcgm (CGM source bytes: %d)</text>
            </svg>
            """.formatted(width, height, width, height, base64, width, height, Math.max(160, width - 16), sourceBytes);
    }

    private String classLoaderDescription(ClassLoader classLoader) {
        if (classLoader instanceof URLClassLoader urlClassLoader) {
            URL[] urls = urlClassLoader.getURLs();
            return "URLClassLoader[" + urls.length + " jars]";
        }
        return classLoader.getClass().getName();
    }
}
