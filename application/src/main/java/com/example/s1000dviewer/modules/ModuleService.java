package com.example.s1000dviewer.modules;

import com.example.s1000dviewer.common.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

@Service
public class ModuleService {

    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9._-]+$");

    private final Path dataRoot;
    private final Path uploadDmDir;
    private final ObjectMapper objectMapper;
    private final DmHtmlRenderer dmHtmlRenderer;
    private final XmlValidationService xmlValidationService;

    public ModuleService(
        AppProperties appProperties,
        ObjectMapper objectMapper,
        DmHtmlRenderer dmHtmlRenderer,
        XmlValidationService xmlValidationService
    ) {
        this.dataRoot = Path.of(appProperties.getDataRoot()).toAbsolutePath().normalize();
        this.uploadDmDir = this.dataRoot.resolve("dm").normalize();
        this.objectMapper = objectMapper;
        this.dmHtmlRenderer = dmHtmlRenderer;
        this.xmlValidationService = xmlValidationService;
    }

    public List<ModuleSummaryResponse> listModules(String aircraft, String engine) {
        String aircraftFilter = normalizeFilter(aircraft);
        String engineFilter = normalizeFilter(engine);

        return discoverDmFiles().stream()
            .sorted(Comparator.comparing(path -> path.getFileName().toString()))
            .map(this::toSummary)
            .filter(summary -> matchesApplicability(summary, aircraftFilter, engineFilter))
            .toList();
    }

    public ModuleContentResponse getModuleContent(String dmId, String aircraft, String engine) {
        validateDmId(dmId);
        Path xmlPath = findDmPath(dmId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Module not found"));

        ModuleSummaryResponse metadata = toSummary(xmlPath);
        if (!matchesApplicability(metadata, normalizeFilter(aircraft), normalizeFilter(engine))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Module not applicable for selected filters");
        }

        try {
            String xml = Files.readString(xmlPath, StandardCharsets.UTF_8);
            String html = dmHtmlRenderer.render(dmId, xml);
            return new ModuleContentResponse(metadata, html);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read module", ex);
        }
    }

    public ModuleUploadResponse uploadModule(
        MultipartFile file,
        String aircraft,
        String engine,
        String title,
        String icnId
    ) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload file is required");
        }

        String original = Optional.ofNullable(file.getOriginalFilename()).orElse("uploaded.xml");
        if (!original.toLowerCase(Locale.ROOT).endsWith(".xml")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only .xml data modules are supported");
        }

        String dmId = stripExtension(Path.of(original).getFileName().toString());
        validateDmId(dmId);

        try {
            ensureDir(uploadDmDir);
            byte[] xmlBytes = file.getBytes();
            xmlValidationService.validateWellFormed(new ByteArrayInputStream(xmlBytes));
            xmlValidationService.validateAgainstXsdHook(new ByteArrayInputStream(xmlBytes));

            Path targetXml = uploadDmDir.resolve(dmId + ".xml").normalize();
            if (!targetXml.startsWith(uploadDmDir)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid dmId");
            }
            Files.write(targetXml, xmlBytes);

            ModuleMetadataSidecar sidecar = new ModuleMetadataSidecar();
            sidecar.setAircraft(blankToNull(aircraft));
            sidecar.setEngine(blankToNull(engine));
            sidecar.setTitle(blankToNull(title));
            sidecar.setIcnId(blankToNull(icnId));
            writeSidecar(targetXml, sidecar);

            return new ModuleUploadResponse(dmId, "Module uploaded successfully");
        } catch (IllegalArgumentException badXml) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, badXml.getMessage(), badXml);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to upload module", ex);
        }
    }

    private ModuleSummaryResponse toSummary(Path xmlPath) {
        String fileName = xmlPath.getFileName().toString();
        String dmId = dmIdFromPath(xmlPath);
        ModuleMetadataSidecar sidecar = readSidecar(xmlPath);
        ExtractedMetadata extracted = readMetadataFromXml(xmlPath);

        String title = firstNonBlank(sidecar.getTitle(), extracted.title(), dmId);
        String aircraft = firstNonBlank(sidecar.getAircraft(), extracted.aircraft(), "ALL");
        String engine = firstNonBlank(sidecar.getEngine(), extracted.engine(), "ALL");
        String icnId = firstNonBlank(sidecar.getIcnId(), extracted.icnId(), "");

        return new ModuleSummaryResponse(dmId, title, aircraft, engine, icnId, fileName);
    }

    private boolean matchesApplicability(ModuleSummaryResponse summary, String aircraft, String engine) {
        boolean aircraftOk = aircraft == null || "ALL".equalsIgnoreCase(summary.aircraft()) || summary.aircraft().equalsIgnoreCase(aircraft);
        boolean engineOk = engine == null || "ALL".equalsIgnoreCase(summary.engine()) || summary.engine().equalsIgnoreCase(engine);
        return aircraftOk && engineOk;
    }

    private ModuleMetadataSidecar readSidecar(Path xmlPath) {
        String dmId = dmIdFromPath(xmlPath);
        Path sidecarPath = xmlPath.resolveSibling(dmId + ".meta.json").normalize();
        if (!Files.exists(sidecarPath) || !Files.isRegularFile(sidecarPath)) {
            return new ModuleMetadataSidecar();
        }
        try {
            return objectMapper.readValue(sidecarPath.toFile(), ModuleMetadataSidecar.class);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read metadata for " + dmId, ex);
        }
    }

    private void writeSidecar(Path xmlPath, ModuleMetadataSidecar sidecar) {
        String dmId = dmIdFromPath(xmlPath);
        Path sidecarPath = xmlPath.resolveSibling(dmId + ".meta.json").normalize();
        if (!sidecarPath.startsWith(uploadDmDir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid dmId");
        }
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(sidecarPath.toFile(), sidecar);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write metadata", ex);
        }
    }

    private ExtractedMetadata readMetadataFromXml(Path xmlPath) {
        try {
            String xml = Files.readString(xmlPath, StandardCharsets.UTF_8);
            DocumentBuilderFactory factory = xmlFactory();
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(silentErrorHandler());
            Document doc = builder.parse(new InputSource(new StringReader(xml)));
            doc.getDocumentElement().normalize();

            String techName = firstTag(doc, "techName");
            String infoName = firstTag(doc, "infoName");
            String title = "";
            if (!techName.isBlank() && !infoName.isBlank()) {
                title = techName + " - " + infoName;
            } else if (!techName.isBlank()) {
                title = techName;
            } else if (!infoName.isBlank()) {
                title = infoName;
            }

            String aircraft = "";
            var dmCodeNodes = doc.getElementsByTagName("dmCode");
            if (dmCodeNodes.getLength() > 0) {
                var dmCode = (org.w3c.dom.Element) dmCodeNodes.item(0);
                aircraft = safeTrim(dmCode.getAttribute("modelIdentCode"));
            }

            String engine = "";
            var asserts = doc.getElementsByTagName("assert");
            for (int i = 0; i < asserts.getLength(); i++) {
                var assertNode = (org.w3c.dom.Element) asserts.item(i);
                String ident = safeTrim(assertNode.getAttribute("applicPropertyIdent")).toLowerCase(Locale.ROOT);
                String value = safeTrim(assertNode.getAttribute("applicPropertyValues"));
                if (value.isBlank()) {
                    continue;
                }
                if (engine.isBlank() && ("model".equals(ident) || "engine".equals(ident))) {
                    engine = value;
                }
                if (aircraft.isBlank() && ("type".equals(ident) || "aircraft".equals(ident))) {
                    aircraft = value;
                }
            }

            String icnId = "";
            var graphics = doc.getElementsByTagName("graphic");
            for (int i = 0; i < graphics.getLength(); i++) {
                var graphic = (org.w3c.dom.Element) graphics.item(i);
                icnId = safeTrim(graphic.getAttribute("infoEntityIdent"));
                if (!icnId.isBlank()) {
                    break;
                }
                String href = safeTrim(firstNonBlank(graphic.getAttribute("xlink:href"), graphic.getAttribute("href")));
                icnId = extractIcnIdFromHref(href);
                if (!icnId.isBlank()) {
                    break;
                }
            }

            return new ExtractedMetadata(title, aircraft, engine, icnId);
        } catch (Exception ex) {
            return new ExtractedMetadata("", "", "", "");
        }
    }

    private String extractIcnIdFromHref(String href) {
        if (href == null || href.isBlank()) {
            return "";
        }
        String raw = href.trim();
        String upper = raw.toUpperCase(Locale.ROOT);
        int idx = upper.indexOf("ICN-");
        if (idx >= 0) {
            return raw.substring(idx);
        }
        return "";
    }

    private String firstTag(Document doc, String name) {
        var list = doc.getElementsByTagName(name);
        if (list.getLength() == 0 || list.item(0) == null) {
            return "";
        }
        String text = list.item(0).getTextContent();
        return text == null ? "" : text.trim();
    }

    private void validateDmId(String dmId) {
        if (dmId == null || dmId.isBlank() || !SAFE_ID.matcher(dmId).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid dmId");
        }
    }

    private String dmIdFromPath(Path xmlPath) {
        return stripExtension(xmlPath.getFileName().toString());
    }

    private String stripExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        if (idx <= 0) {
            return fileName;
        }
        return fileName.substring(0, idx);
    }

    private String normalizeFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private List<Path> discoverDmFiles() {
        try {
            Map<String, Path> byDmId = new LinkedHashMap<>();

            if (Files.isDirectory(uploadDmDir)) {
                try (var uploadStream = Files.list(uploadDmDir)) {
                    uploadStream
                        .filter(Files::isRegularFile)
                        .filter(this::isDmFile)
                        .forEach(path -> byDmId.putIfAbsent(dmIdFromPath(path).toUpperCase(Locale.ROOT), path));
                }
            }

            if (Files.isDirectory(dataRoot)) {
                try (var rootStream = Files.walk(dataRoot, 2)) {
                    rootStream
                        .filter(Files::isRegularFile)
                        .filter(this::isDmFile)
                        .forEach(path -> byDmId.putIfAbsent(dmIdFromPath(path).toUpperCase(Locale.ROOT), path));
                }
            }

            return byDmId.values().stream().toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to list data modules", ex);
        }
    }

    private Optional<Path> findDmPath(String dmId) {
        return discoverDmFiles().stream()
            .filter(path -> dmIdFromPath(path).equalsIgnoreCase(dmId))
            .findFirst();
    }

    private boolean isDmFile(Path path) {
        String fileName = path.getFileName().toString().toUpperCase(Locale.ROOT);
        return fileName.endsWith(".XML") && fileName.startsWith("DMC-");
    }

    private DocumentBuilderFactory xmlFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        setFeatureIfSupported(factory, "http://apache.org/xml/features/disallow-doctype-decl", false);
        setFeatureIfSupported(factory, "http://xml.org/sax/features/external-general-entities", false);
        setFeatureIfSupported(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        setFeatureIfSupported(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setExpandEntityReferences(false);
        factory.setNamespaceAware(false);
        return factory;
    }

    private void setFeatureIfSupported(DocumentBuilderFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (Exception ignored) {
            // Parser-specific optional feature.
        }
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private ErrorHandler silentErrorHandler() {
        return new ErrorHandler() {
            @Override
            public void warning(org.xml.sax.SAXParseException exception) {
            }

            @Override
            public void error(org.xml.sax.SAXParseException exception) throws SAXException {
                throw exception;
            }

            @Override
            public void fatalError(org.xml.sax.SAXParseException exception) throws SAXException {
                throw exception;
            }
        };
    }

    private void ensureDir(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to initialize data directory", ex);
        }
    }

    private record ExtractedMetadata(String title, String aircraft, String engine, String icnId) {
    }
}
