package com.s1000Dorg.viewer.graphics;

import com.s1000Dorg.viewer.adapters.fs.FsDataRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

@Service
public class GraphicsService {

    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9._-]+$");

    private final FsDataRepository repository;
    private final CgmToSvgConverter converter;

    public GraphicsService(FsDataRepository repository, CgmToSvgConverter converter) {
        this.repository = repository;
        this.converter = converter;
    }

    public String getSvg(String icnId) {
        validateIcnId(icnId);

        Path graphicPath = repository.findGraphicPath(icnId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Graphic not found"));

        String extension = extensionOf(graphicPath).toLowerCase(Locale.ROOT);
        try {
            return switch (extension) {
                case ".svg" -> Files.readString(graphicPath, StandardCharsets.UTF_8);
                case ".cgm" -> {
                    try (InputStream inputStream = Files.newInputStream(graphicPath)) {
                        yield converter.convert(inputStream);
                    }
                }
                case ".png" -> wrapRasterAsSvg(graphicPath, "image/png", icnId);
                case ".jpg", ".jpeg" -> wrapRasterAsSvg(graphicPath, "image/jpeg", icnId);
                case ".gif" -> wrapRasterAsSvg(graphicPath, "image/gif", icnId);
                default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Graphic format not supported");
            };
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load graphic", ex);
        }
    }

    public List<Hotspot> getHotspots(String icnId) {
        validateIcnId(icnId);
        return repository.readHotspots(icnId).orElseGet(() -> deriveHotspotsFromDm(icnId));
    }

    private List<Hotspot> deriveHotspotsFromDm(String icnId) {
        List<Path> dmFiles = repository.listDmXmlFiles();
        List<String> dmIds = dmFiles.stream().map(this::dmIdFromPath).toList();
        List<Hotspot> hotspots = new ArrayList<>();

        for (Path dmPath : dmFiles) {
            try {
                String xml = Files.readString(dmPath, StandardCharsets.UTF_8);
                var factory = xmlFactory();
                var builder = factory.newDocumentBuilder();
                var doc = builder.parse(new InputSource(new java.io.StringReader(xml)));
                doc.getDocumentElement().normalize();

                var graphics = doc.getElementsByTagName("graphic");
                for (int i = 0; i < graphics.getLength(); i++) {
                    Element graphic = (Element) graphics.item(i);
                    if (!graphicMatchesIcn(graphic, icnId)) {
                        continue;
                    }

                    var hotspotNodes = graphic.getElementsByTagName("hotspot");
                    for (int idx = 0; idx < hotspotNodes.getLength(); idx++) {
                        Element hotspotEl = (Element) hotspotNodes.item(idx);
                        String id = safeTrim(hotspotEl.getAttribute("id"));
                        String label = firstNonBlank(
                            safeTrim(hotspotEl.getAttribute("hotspotTitle")),
                            safeTrim(hotspotEl.getAttribute("objectDescr")),
                            id,
                            "Hotspot"
                        );

                        int position = hotspots.size();
                        int col = position % 4;
                        int row = position / 4;

                        String targetDmId = inferTargetDmId(label, dmIds, dmIdFromPath(dmPath));
                        hotspots.add(new Hotspot(
                            firstNonBlank(id, "hotspot-" + position),
                            5 + (col * 23),
                            8 + (row * 18),
                            20,
                            14,
                            label,
                            targetDmId
                        ));
                    }
                }
            } catch (Exception ignored) {
                // Ignore malformed DM while deriving fallback hotspots.
            }
        }

        return hotspots;
    }

    private String inferTargetDmId(String label, List<String> dmIds, String sourceDmId) {
        String upper = label.toUpperCase(Locale.ROOT);
        if (upper.contains("BRAKE")) {
            return dmIds.stream().filter(id -> id.contains("BRAKE") && !id.equalsIgnoreCase(sourceDmId)).findFirst().orElse(null);
        }
        if (upper.contains("LIGHT")) {
            return dmIds.stream().filter(id -> id.contains("LIGHT") && !id.equalsIgnoreCase(sourceDmId)).findFirst().orElse(null);
        }
        return null;
    }

    private boolean graphicMatchesIcn(Element graphic, String icnId) {
        String infoEntityIdent = safeTrim(graphic.getAttribute("infoEntityIdent"));
        if (infoEntityIdent.equalsIgnoreCase(icnId)) {
            return true;
        }

        String href = safeTrim(firstNonBlank(graphic.getAttribute("xlink:href"), graphic.getAttribute("href")));
        String extracted = extractIcnFromHref(href);
        return extracted.equalsIgnoreCase(icnId);
    }

    private String extractIcnFromHref(String href) {
        if (href == null || href.isBlank()) {
            return "";
        }
        String raw = href.trim();
        String upper = raw.toUpperCase(Locale.ROOT);
        int idx = upper.indexOf("ICN-");
        if (idx >= 0) {
            return raw.substring(idx).replace("&", "").replace(";", "");
        }
        return "";
    }

    private String wrapRasterAsSvg(Path imagePath, String mimeType, String icnId) throws IOException {
        byte[] bytes = Files.readAllBytes(imagePath);
        String base64 = Base64.getEncoder().encodeToString(bytes);
        String escapedId = escape(icnId);

        return """
            <svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 1200 800\" role=\"img\" aria-label=\"%s\">
              <rect width=\"1200\" height=\"800\" fill=\"#f8fafc\" />
              <image href=\"data:%s;base64,%s\" x=\"0\" y=\"0\" width=\"1200\" height=\"800\" preserveAspectRatio=\"xMidYMid meet\" />
            </svg>
            """.formatted(escapedId, mimeType, base64);
    }

    private DocumentBuilderFactory xmlFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        } catch (Exception ignored) {
            // Optional parser features.
        }
        factory.setNamespaceAware(false);
        factory.setExpandEntityReferences(false);
        return factory;
    }

    private String dmIdFromPath(Path path) {
        String fileName = path.getFileName().toString();
        int idx = fileName.lastIndexOf('.');
        return idx > 0 ? fileName.substring(0, idx) : fileName;
    }

    private String extensionOf(Path path) {
        String fileName = path.getFileName().toString();
        int idx = fileName.lastIndexOf('.');
        if (idx < 0) {
            return "";
        }
        return fileName.substring(idx);
    }

    private void validateIcnId(String icnId) {
        if (icnId == null || icnId.isBlank() || !SAFE_ID.matcher(icnId).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid icnId");
        }
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String escape(String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }
}

