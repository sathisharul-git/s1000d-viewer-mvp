package com.s1000Dorg.viewer.graphics;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class JcgmBackedCgmToSvgConverter implements CgmToSvgConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(JcgmBackedCgmToSvgConverter.class);
    private static final String CGM_READER_SPI_CLASS = "net.sf.jcgm.imageio.plugins.cgm.CGMImageReaderSpi";
    private static final List<String> DEFAULT_LIB_CANDIDATES = List.of(
        "application/libs/jcgm",
        "libs/jcgm",
        "../application/libs/jcgm"
    );

    private final DemoCgmToSvgConverter fallbackConverter;
    private final Object probeLock = new Object();
    private final List<Path> additionalJarDirs;

    private volatile boolean probeComplete = false;
    private volatile boolean jcgmAvailable = false;
    private volatile ClassLoader jcgmClassLoader;
    private volatile String probeMessage = "Probe not executed";

    private record DecodeResult(Optional<BufferedImage> image, String reason) {
    }

    @Autowired
    public JcgmBackedCgmToSvgConverter(DemoCgmToSvgConverter fallbackConverter) {
        this(fallbackConverter, null, List.of());
    }

    JcgmBackedCgmToSvgConverter(DemoCgmToSvgConverter fallbackConverter, ClassLoader jcgmClassLoader) {
        this(fallbackConverter, jcgmClassLoader, List.of());
    }

    JcgmBackedCgmToSvgConverter(
        DemoCgmToSvgConverter fallbackConverter,
        ClassLoader jcgmClassLoader,
        List<Path> additionalJarDirs
    ) {
        this.fallbackConverter = fallbackConverter;
        this.jcgmClassLoader = jcgmClassLoader;
        this.additionalJarDirs = additionalJarDirs == null ? List.of() : List.copyOf(additionalJarDirs);
    }

    @Override
    public String convert(InputStream cgmStream) throws IOException {
        byte[] payload = cgmStream.readAllBytes();
        if (payload.length == 0) {
            return fallbackConverter.convert(new ByteArrayInputStream(payload), "Empty CGM payload.");
        }

        DecodeResult decoded = tryDecodeWithJcgm(payload);
        if (decoded.image().isPresent()) {
            return wrapImageAsSvg(decoded.image().get(), payload);
        }

        return fallbackConverter.convert(new ByteArrayInputStream(payload), decoded.reason());
    }

    DecodeResult tryDecodeWithJcgm(byte[] payload) {
        if (!ensureJcgmAvailable()) {
            return new DecodeResult(Optional.empty(), probeMessage);
        }

        try {
            Class<?> spiClass = Class.forName(CGM_READER_SPI_CLASS, true, jcgmClassLoader);
            Object spi = spiClass.getDeclaredConstructor().newInstance();
            if (!(spi instanceof javax.imageio.spi.ImageReaderSpi imageReaderSpi)) {
                LOGGER.warn("CGM reader SPI does not implement ImageReaderSpi: {}", spiClass.getName());
                return new DecodeResult(Optional.empty(), "CGM reader SPI is not compatible with ImageReaderSpi.");
            }

            ImageReader reader = imageReaderSpi.createReaderInstance();
            try (ImageInputStream imageInput = ImageIO.createImageInputStream(new ByteArrayInputStream(payload))) {
                reader.setInput(imageInput, true, true);
                ImageReadParam readParam = reader.getDefaultReadParam();
                BufferedImage image = reader.read(0, readParam);
                if (image == null) {
                    return new DecodeResult(Optional.empty(), "CGM decoded to an empty image.");
                }
                return new DecodeResult(Optional.of(image), "CGM decoded with jcgm.");
            } finally {
                reader.dispose();
            }
        } catch (Throwable ex) {
            LOGGER.warn("jcgm conversion failed, using fallback converter: {}", ex.toString());
            return new DecodeResult(Optional.empty(), "jcgm conversion failed: " + ex.getClass().getSimpleName());
        }
    }

    boolean ensureJcgmAvailable() {
        if (probeComplete) {
            return jcgmAvailable;
        }

        synchronized (probeLock) {
            if (probeComplete) {
                return jcgmAvailable;
            }

            if (jcgmClassLoader == null) {
                jcgmClassLoader = resolveJcgmClassLoader();
            }

            if (jcgmClassLoader != null) {
                jcgmAvailable = canInstantiateReaderSpi(jcgmClassLoader);
                probeMessage = jcgmAvailable
                    ? "jcgm probe succeeded"
                    : "jcgm probe failed: reader SPI class present but instantiation failed";
            } else {
                jcgmAvailable = false;
                probeMessage = "jcgm probe failed: no loader could resolve jcgm jars";
            }

            if (jcgmAvailable) {
                LOGGER.info("Using jcgm CGM reader: {} ({})", classLoaderDescription(jcgmClassLoader), probeMessage);
            } else {
                LOGGER.info("Using fallback CGM converter. {}", probeMessage);
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
            if (loader.isPresent()) {
                return loader.get();
            }
        }

        return null;
    }

    private List<Path> candidateJarDirs() {
        LinkedHashSet<Path> candidates = new LinkedHashSet<>();
        for (String candidate : DEFAULT_LIB_CANDIDATES) {
            candidates.add(Path.of(candidate).toAbsolutePath().normalize());
        }
        for (Path additionalJarDir : additionalJarDirs) {
            candidates.add(additionalJarDir.toAbsolutePath().normalize());
        }
        return new ArrayList<>(candidates);
    }

    private Optional<ClassLoader> tryBuildJarClassLoader(Path jarDir) {
        Path normalized = jarDir.toAbsolutePath().normalize();
        LOGGER.info("Checking for jcgm JARs in: {}", normalized);
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
            LOGGER.info("Found {} jcgm candidate jar(s) in {}", urls.size(), normalized);

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
            Class.forName(fqcn, false, classLoader);
            return true;
        } catch (Throwable ex) {
            LOGGER.info("Class {} unavailable in {}: {}", fqcn, classLoaderDescription(classLoader), ex.toString());
            return false;
        }
    }

    private boolean canInstantiateReaderSpi(ClassLoader classLoader) {
        try {
            Class<?> spiClass = Class.forName(CGM_READER_SPI_CLASS, false, classLoader);
            Object spi = spiClass.getDeclaredConstructor().newInstance();
            return spi instanceof javax.imageio.spi.ImageReaderSpi;
        } catch (Throwable ex) {
            LOGGER.warn("jcgm SPI probe failed in {}: {}", classLoaderDescription(classLoader), ex.toString());
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
              <metadata>Rendered via jcgm; source-bytes=%d</metadata>
            </svg>
            """.formatted(width, height, width, height, base64, width, height, sourceBytes);
    }

    private String classLoaderDescription(ClassLoader classLoader) {
        if (classLoader instanceof URLClassLoader urlClassLoader) {
            URL[] urls = urlClassLoader.getURLs();
            return "URLClassLoader[" + urls.length + " jars]";
        }
        return classLoader.getClass().getName();
    }
}
