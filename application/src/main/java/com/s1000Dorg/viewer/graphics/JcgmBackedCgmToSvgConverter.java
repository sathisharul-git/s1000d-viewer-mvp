package com.s1000Dorg.viewer.graphics;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
    private static final String CGM_CORE_CLASS = "net.sf.jcgm.core.CGM";
    private static final Pattern VIEWBOX_PATTERN = Pattern.compile("viewBox\\s*=\\s*\"\\s*[-+]?\\d+(?:\\.\\d+)?\\s+[-+]?\\d+(?:\\.\\d+)?\\s+([-+]?\\d+(?:\\.\\d+)?)\\s+([-+]?\\d+(?:\\.\\d+)?)\\s*\"");
    private static final int FALLBACK_WIDTH = 1100;
    private static final int FALLBACK_HEIGHT = 460;
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

    private record OverlayExtraction(List<HotspotOverlay> hotspots, Bounds bounds) {
        static OverlayExtraction empty() {
            return new OverlayExtraction(List.of(), Bounds.empty());
        }
    }

    private record HotspotOverlay(
        String hotspotId,
        String apsName,
        String linkUri,
        String screenTip,
        List<Point> points,
        int regionType,
        int apsIndex
    ) {
    }

    private record Point(double x, double y) {
    }

    private record Bounds(double minX, double minY, double maxX, double maxY) {
        static Bounds empty() {
            return new Bounds(0.0, 0.0, 0.0, 0.0);
        }

        boolean valid() {
            return maxX > minX && maxY > minY;
        }
    }

    private static class HotspotBuilder {
        private final int apsIndex;
        private String apsIdentifier;
        private String explicitId;
        private String linkUri;
        private String screenTip;
        private int regionType = -1;
        private List<Point> regionPoints = List.of();

        private HotspotBuilder(int apsIndex) {
            this.apsIndex = apsIndex;
        }
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

        OverlayExtraction overlays = extractHotspotOverlays(payload);
        DecodeResult decoded = tryDecodeWithJcgm(payload);
        if (decoded.image().isPresent()) {
            return wrapImageAsSvg(decoded.image().get(), payload, overlays);
        }

        String fallbackSvg = fallbackConverter.convert(new ByteArrayInputStream(payload), decoded.reason());
        return attachOverlayToFallbackSvg(fallbackSvg, overlays);
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

    private String wrapImageAsSvg(BufferedImage image, byte[] cgmPayload, OverlayExtraction overlays) throws IOException {
        ByteArrayOutputStream pngBytes = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", pngBytes)) {
            return fallbackConverter.convert(new ByteArrayInputStream(cgmPayload));
        }
        String base64 = Base64.getEncoder().encodeToString(pngBytes.toByteArray());
        int width = Math.max(1, image.getWidth());
        int height = Math.max(1, image.getHeight());
        int sourceBytes = cgmPayload.length;
        String overlayLayer = buildOverlayLayer(overlays, width, height);

        if (overlayLayer.isEmpty()) {
            return """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %d %d" role="img" aria-label="CGM rendered image">
                  <rect width="%d" height="%d" fill="#f8fafc" />
                  <image href="data:image/png;base64,%s" x="0" y="0" width="%d" height="%d" preserveAspectRatio="xMidYMid meet" />
                  <metadata>Rendered via jcgm; source-bytes=%d</metadata>
                </svg>
                """.formatted(width, height, width, height, base64, width, height, sourceBytes);
        }

        return """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %d %d" role="img" aria-label="CGM rendered image">
              <rect width="%d" height="%d" fill="#f8fafc" />
              <image href="data:image/png;base64,%s" x="0" y="0" width="%d" height="%d" preserveAspectRatio="xMidYMid meet" />
              %s
              <metadata>Rendered via jcgm; source-bytes=%d</metadata>
            </svg>
            """.formatted(width, height, width, height, base64, width, height, overlayLayer, sourceBytes);
    }

    private String buildOverlayLayer(OverlayExtraction overlays, int width, int height) {
        if (overlays.hotspots().isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("<g id=\"hotspot-overlay-layer\" pointer-events=\"all\">");
        for (HotspotOverlay hotspot : overlays.hotspots()) {
            String shape = renderOverlayShape(hotspot, overlays.bounds(), width, height);
            if (shape.isBlank()) {
                continue;
            }
            builder.append("<g class=\"s1000d-hotspot\" data-hotspot-id=\"")
                .append(escapeXml(hotspot.hotspotId()))
                .append("\"");
            if (!hotspot.apsName().isBlank()) {
                builder.append(" data-aps-name=\"").append(escapeXml(hotspot.apsName())).append("\"");
            }
            if (!hotspot.linkUri().isBlank()) {
                builder.append(" data-linkuri=\"").append(escapeXml(hotspot.linkUri())).append("\"");
            }
            if (!hotspot.screenTip().isBlank()) {
                builder.append(" data-screentip=\"").append(escapeXml(hotspot.screenTip())).append("\"");
            }
            builder.append(">");
            builder.append(shape);
            builder.append("</g>");
        }
        builder.append("</g>");
        return builder.toString();
    }

    private String attachOverlayToFallbackSvg(String fallbackSvg, OverlayExtraction overlays) {
        if (fallbackSvg == null || fallbackSvg.isBlank() || overlays.hotspots().isEmpty()) {
            return fallbackSvg;
        }
        int[] size = resolveSvgViewport(fallbackSvg);
        String overlayLayer = buildOverlayLayer(overlays, size[0], size[1]);
        if (overlayLayer.isBlank()) {
            return fallbackSvg;
        }

        int insertAt = fallbackSvg.lastIndexOf("</svg>");
        if (insertAt < 0) {
            return fallbackSvg;
        }
        return fallbackSvg.substring(0, insertAt) + overlayLayer + fallbackSvg.substring(insertAt);
    }

    private int[] resolveSvgViewport(String svg) {
        Matcher matcher = VIEWBOX_PATTERN.matcher(svg);
        if (!matcher.find()) {
            return new int[]{FALLBACK_WIDTH, FALLBACK_HEIGHT};
        }
        int width = parsePositiveDimension(matcher.group(1), FALLBACK_WIDTH);
        int height = parsePositiveDimension(matcher.group(2), FALLBACK_HEIGHT);
        return new int[]{width, height};
    }

    private int parsePositiveDimension(String raw, int fallback) {
        try {
            double value = Double.parseDouble(raw);
            if (value > 0.0) {
                return Math.max(1, (int) Math.round(value));
            }
        } catch (NumberFormatException ignored) {
            // Use fallback.
        }
        return fallback;
    }

    private String renderOverlayShape(HotspotOverlay hotspot, Bounds bounds, int width, int height) {
        if (hotspot.points().size() < 2) {
            return "";
        }

        List<Point> mapped = hotspot.points().stream()
            .map(point -> mapToImage(point, bounds, width, height))
            .toList();

        if (isAxisAlignedRectangle(mapped)) {
            double minX = mapped.stream().mapToDouble(Point::x).min().orElse(0.0);
            double minY = mapped.stream().mapToDouble(Point::y).min().orElse(0.0);
            double maxX = mapped.stream().mapToDouble(Point::x).max().orElse(0.0);
            double maxY = mapped.stream().mapToDouble(Point::y).max().orElse(0.0);
            return "<rect class=\"s1000d-hotspot-shape\" x=\"%s\" y=\"%s\" width=\"%s\" height=\"%s\" fill-opacity=\"0\" stroke-opacity=\"0\" pointer-events=\"all\"/>"
                .formatted(formatDouble(minX), formatDouble(minY), formatDouble(maxX - minX), formatDouble(maxY - minY));
        }

        String points = mapped.stream()
            .map(point -> formatDouble(point.x()) + "," + formatDouble(point.y()))
            .collect(Collectors.joining(" "));

        return "<polygon class=\"s1000d-hotspot-shape\" points=\"%s\" fill-opacity=\"0\" stroke-opacity=\"0\" pointer-events=\"all\"/>"
            .formatted(points);
    }

    private Point mapToImage(Point point, Bounds bounds, int width, int height) {
        if (!bounds.valid()) {
            return point;
        }
        double mappedX = ((point.x() - bounds.minX()) / (bounds.maxX() - bounds.minX())) * width;
        double mappedY = ((point.y() - bounds.minY()) / (bounds.maxY() - bounds.minY())) * height;
        return new Point(mappedX, mappedY);
    }

    private boolean isAxisAlignedRectangle(List<Point> points) {
        List<Point> normalized = stripClosingPoint(points);
        if (normalized.size() != 4) {
            return false;
        }
        Set<String> xs = normalized.stream().map(point -> formatDouble(point.x())).collect(Collectors.toSet());
        Set<String> ys = normalized.stream().map(point -> formatDouble(point.y())).collect(Collectors.toSet());
        return xs.size() == 2 && ys.size() == 2;
    }

    private List<Point> stripClosingPoint(List<Point> points) {
        if (points.size() < 2) {
            return points;
        }
        Point first = points.get(0);
        Point last = points.get(points.size() - 1);
        if (Math.abs(first.x() - last.x()) < 0.000001 && Math.abs(first.y() - last.y()) < 0.000001) {
            return points.subList(0, points.size() - 1);
        }
        return points;
    }

    private OverlayExtraction extractHotspotOverlays(byte[] payload) {
        ClassLoader loader = resolveCoreClassLoader();
        if (loader == null) {
            return OverlayExtraction.empty();
        }

        try {
            Class<?> cgmClass = Class.forName(CGM_CORE_CLASS, true, loader);
            Object cgm = cgmClass.getDeclaredConstructor().newInstance();
            Method readMethod = cgmClass.getMethod("read", java.io.DataInput.class);
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
                readMethod.invoke(cgm, input);
            }

            Bounds bounds = readBounds(cgmClass, cgm);
            Method commandsMethod = cgmClass.getMethod("getCommands");
            List<?> commands = (List<?>) commandsMethod.invoke(cgm);
            List<HotspotOverlay> hotspots = collectHotspots(commands);
            return new OverlayExtraction(hotspots, bounds);
        } catch (Throwable ex) {
            LOGGER.debug("Unable to extract hotspot overlays from CGM: {}", ex.toString());
            return OverlayExtraction.empty();
        }
    }

    private ClassLoader resolveCoreClassLoader() {
        if (jcgmClassLoader != null) {
            return jcgmClassLoader;
        }
        return resolveJcgmClassLoader();
    }

    private Bounds readBounds(Class<?> cgmClass, Object cgm) {
        try {
            Method extentMethod = cgmClass.getMethod("extent");
            Object extentValue = extentMethod.invoke(cgm);
            if (extentValue instanceof Object[] points && points.length >= 2) {
                double[] first = extractPoint(points[0]);
                double[] second = extractPoint(points[1]);
                if (first != null && second != null) {
                    double minX = Math.min(first[0], second[0]);
                    double minY = Math.min(first[1], second[1]);
                    double maxX = Math.max(first[0], second[0]);
                    double maxY = Math.max(first[1], second[1]);
                    return new Bounds(minX, minY, maxX, maxY);
                }
            }
        } catch (Exception ex) {
            LOGGER.debug("Unable to read CGM extent for hotspot mapping: {}", ex.toString());
        }
        return Bounds.empty();
    }

    private double[] extractPoint(Object pointObject) {
        if (pointObject == null) {
            return null;
        }
        try {
            Field xField = pointObject.getClass().getField("x");
            Field yField = pointObject.getClass().getField("y");
            xField.setAccessible(true);
            yField.setAccessible(true);
            Object xValue = xField.get(pointObject);
            Object yValue = yField.get(pointObject);
            if (xValue instanceof Number xNumber && yValue instanceof Number yNumber) {
                return new double[]{xNumber.doubleValue(), yNumber.doubleValue()};
            }
        } catch (Exception ex) {
            return null;
        }
        return null;
    }

    private List<HotspotOverlay> collectHotspots(List<?> commands) {
        List<HotspotOverlay> hotspots = new ArrayList<>();
        Deque<HotspotBuilder> stack = new java.util.ArrayDeque<>();
        Set<String> usedIds = new HashSet<>();
        int apsCounter = 0;

        for (Object command : commands) {
            if (command == null) {
                continue;
            }
            String simpleName = command.getClass().getSimpleName();
            if ("BeginApplicationStructure".equals(simpleName)) {
                HotspotBuilder builder = new HotspotBuilder(apsCounter++);
                builder.apsIdentifier = invokeString(command, "getIdentifier");
                stack.push(builder);
                continue;
            }
            if ("ApplicationStructureAttribute".equals(simpleName) && !stack.isEmpty()) {
                applyAttribute(stack.peek(), command);
                continue;
            }
            if ("EndApplicationStructure".equals(simpleName) && !stack.isEmpty()) {
                HotspotBuilder builder = stack.pop();
                if (!builder.regionPoints.isEmpty()) {
                    String stableId = resolveStableId(builder, usedIds);
                    usedIds.add(stableId);
                    hotspots.add(new HotspotOverlay(
                        stableId,
                        firstNonBlank(builder.apsIdentifier, builder.explicitId),
                        nullSafe(builder.linkUri),
                        nullSafe(builder.screenTip),
                        builder.regionPoints,
                        builder.regionType,
                        builder.apsIndex
                    ));
                }
            }
        }
        return hotspots;
    }

    private void applyAttribute(HotspotBuilder builder, Object attributeCommand) {
        String attributeType = invokeString(attributeCommand, "getApplicationStructureAttributeType").toLowerCase(Locale.ROOT);
        Object sdr = invokeObject(attributeCommand, "getSdr");
        if (sdr == null) {
            return;
        }

        if ("region".equals(attributeType)) {
            parseRegionAttribute(builder, sdr);
            return;
        }

        List<String> values = extractSdrValues(sdr).stream()
            .map(value -> String.valueOf(value).trim())
            .filter(value -> !value.isBlank())
            .toList();
        if (values.isEmpty()) {
            return;
        }
        String joined = String.join(" ", values);

        if ("name".equals(attributeType) || "id".equals(attributeType) || "identifier".equals(attributeType)) {
            builder.explicitId = joined;
        } else if (attributeType.contains("link")) {
            builder.linkUri = joined;
        } else if (attributeType.contains("tip") || attributeType.contains("screentip")) {
            builder.screenTip = joined;
        }
    }

    private void parseRegionAttribute(HotspotBuilder builder, Object sdr) {
        List<?> members = invokeMembers(sdr);
        List<Double> vdcValues = new ArrayList<>();
        Integer regionType = null;

        for (Object member : members) {
            String memberType = getMemberType(member);
            List<Object> data = getMemberData(member);
            if (memberType.contains("IX") && !data.isEmpty() && data.get(0) instanceof Number number) {
                regionType = number.intValue();
            } else if (memberType.contains("VDC")) {
                for (Object value : data) {
                    if (value instanceof Number number) {
                        vdcValues.add(number.doubleValue());
                    } else {
                        try {
                            vdcValues.add(Double.parseDouble(String.valueOf(value)));
                        } catch (NumberFormatException ignored) {
                            // Ignore non-numeric values.
                        }
                    }
                }
            }
        }

        if (vdcValues.size() < 4) {
            return;
        }

        List<Point> points = new ArrayList<>();
        for (int i = 0; i + 1 < vdcValues.size(); i += 2) {
            points.add(new Point(vdcValues.get(i), vdcValues.get(i + 1)));
        }
        builder.regionPoints = points;
        if (regionType != null) {
            builder.regionType = regionType;
        }
    }

    private List<?> invokeMembers(Object sdr) {
        try {
            Method membersMethod = sdr.getClass().getMethod("getMembers");
            Object value = membersMethod.invoke(sdr);
            if (value instanceof List<?> list) {
                return list;
            }
        } catch (Exception ignored) {
            // fall through
        }
        return List.of();
    }

    private List<Object> extractSdrValues(Object sdr) {
        List<Object> values = new ArrayList<>();
        for (Object member : invokeMembers(sdr)) {
            values.addAll(getMemberData(member));
        }
        return values;
    }

    private String getMemberType(Object member) {
        try {
            Field typeField = member.getClass().getDeclaredField("type");
            typeField.setAccessible(true);
            Object value = typeField.get(member);
            return value == null ? "" : value.toString().toUpperCase(Locale.ROOT);
        } catch (Exception ex) {
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object> getMemberData(Object member) {
        try {
            Field dataField = member.getClass().getDeclaredField("data");
            dataField.setAccessible(true);
            Object value = dataField.get(member);
            if (value instanceof List<?> list) {
                return (List<Object>) list;
            }
        } catch (Exception ignored) {
            // fall through
        }
        return List.of();
    }

    private String resolveStableId(HotspotBuilder builder, Set<String> usedIds) {
        String preferred = sanitizeId(firstNonBlank(builder.explicitId, builder.apsIdentifier));
        if (preferred.isBlank()) {
            preferred = "hs-" + stableHash(builder.apsIndex + "|" + geometrySignature(builder.regionPoints));
        }
        if (!usedIds.contains(preferred)) {
            return preferred;
        }
        return preferred + "-" + stableHash(builder.apsIndex + "|" + geometrySignature(builder.regionPoints)).substring(0, 8);
    }

    private String geometrySignature(List<Point> points) {
        return points.stream()
            .map(point -> formatDouble(point.x()) + ":" + formatDouble(point.y()))
            .collect(Collectors.joining("|"));
    }

    private String stableHash(String seed) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(seed.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) {
                builder.append(String.format(Locale.ROOT, "%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            return Integer.toHexString(seed.hashCode());
        }
    }

    private String sanitizeId(String value) {
        String normalized = nullSafe(value).replaceAll("[^A-Za-z0-9._-]+", "-");
        normalized = normalized.replaceAll("^-+", "").replaceAll("-+$", "");
        return normalized;
    }

    private String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private String firstNonBlank(String... values) {
        return Arrays.stream(values)
            .filter(value -> value != null && !value.isBlank())
            .findFirst()
            .orElse("");
    }

    private String nullSafe(String value) {
        return value == null ? "" : value.trim();
    }

    private String invokeString(Object target, String methodName) {
        Object value = invokeObject(target, methodName);
        return value == null ? "" : String.valueOf(value);
    }

    private Object invokeObject(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (Exception ex) {
            return null;
        }
    }

    private String escapeXml(String value) {
        return nullSafe(value)
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    private String classLoaderDescription(ClassLoader classLoader) {
        if (classLoader instanceof URLClassLoader urlClassLoader) {
            URL[] urls = urlClassLoader.getURLs();
            return "URLClassLoader[" + urls.length + " jars]";
        }
        return classLoader.getClass().getName();
    }
}
