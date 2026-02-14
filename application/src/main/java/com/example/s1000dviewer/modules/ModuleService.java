package com.example.s1000dviewer.modules;

import com.example.s1000dviewer.adapters.fs.FsDataRepository;
import com.example.s1000dviewer.adapters.fs.PublishedManifestEntry;
import com.example.s1000dviewer.applicability.ApplicabilityContext;
import com.example.s1000dviewer.applicability.ApplicabilityMatchDecision;
import com.example.s1000dviewer.applicability.ApplicabilityMatcher;
import com.example.s1000dviewer.applicability.ApplicabilityFeatureFlags;
import com.example.s1000dviewer.applicability.ApplicabilityRuleEngine;
import com.example.s1000dviewer.applicability.ApplicabilityEvaluator;
import com.example.s1000dviewer.applicability.ApplicabilityProvider;
import com.example.s1000dviewer.applicability.ApplicabilityResolution;
import com.example.s1000dviewer.common.AppProperties;
import com.example.s1000dviewer.domain.Applicability;
import com.example.s1000dviewer.domain.ApplicabilityResult;
import com.example.s1000dviewer.domain.DataModuleDescriptor;
import com.example.s1000dviewer.render.RenderFacade;
import com.example.s1000dviewer.render.RenderedDm;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
import org.xml.sax.InputSource;

@Service
public class ModuleService {

    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9._-]+$");

    private final FsDataRepository repository;
    private final ApplicabilityProvider applicabilityProvider;
    private final ApplicabilityMatcher applicabilityMatcher;
    private final ApplicabilityFeatureFlags applicabilityFeatureFlags;
    private final ApplicabilityEvaluator applicabilityEvaluator;
    private final ApplicabilityRuleEngine applicabilityRuleEngine;
    private final RenderFacade renderFacade;
    private final XmlValidationService xmlValidationService;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    public ModuleService(
        FsDataRepository repository,
        ApplicabilityProvider applicabilityProvider,
        ApplicabilityMatcher applicabilityMatcher,
        ApplicabilityFeatureFlags applicabilityFeatureFlags,
        ApplicabilityEvaluator applicabilityEvaluator,
        ApplicabilityRuleEngine applicabilityRuleEngine,
        RenderFacade renderFacade,
        XmlValidationService xmlValidationService,
        ObjectMapper objectMapper,
        AppProperties appProperties
    ) {
        this.repository = repository;
        this.applicabilityProvider = applicabilityProvider;
        this.applicabilityMatcher = applicabilityMatcher;
        this.applicabilityFeatureFlags = applicabilityFeatureFlags;
        this.applicabilityEvaluator = applicabilityEvaluator;
        this.applicabilityRuleEngine = applicabilityRuleEngine;
        this.renderFacade = renderFacade;
        this.xmlValidationService = xmlValidationService;
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
    }

    public ModuleListResponse listModules(String aircraft, String engine, String variant) {
        ApplicabilityContext context = ApplicabilityContext.of(aircraft, engine, variant);

        List<ModuleListItemResponse> modules = new ArrayList<>();
        for (DataModuleDescriptor descriptor : resolveDescriptors()) {
            ApplicabilityMatchDecision decision = applicabilityMatcher.evaluate(descriptor.applicability(), context);
            if (!applicabilityMatcher.includeInModuleList(decision)) {
                continue;
            }
            modules.add(new ModuleListItemResponse(
                descriptor.dmId(),
                descriptor.title(),
                toApplicabilityResponse(descriptor.applicability()),
                descriptor.source(),
                descriptor.hasPublishedPreview(),
                decision.result(),
                decision.reason(),
                descriptor.applicabilitySource()
            ));
        }

        modules.sort(Comparator.comparing(ModuleListItemResponse::dmId));
        return new ModuleListResponse(new ModuleFiltersResponse(context.aircraft(), context.engine(), context.variant()), modules);
    }

    public ModuleRenderResponse renderModule(String dmId, String aircraft, String engine, String variant) {
        validateDmId(dmId);
        ApplicabilityContext context = ApplicabilityContext.of(aircraft, engine, variant);

        DataModuleDescriptor descriptor = resolveDescriptor(dmId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Module not found"));
        if (!descriptor.hasPublishedPreview() && repository.findDmXml(dmId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Module content not found");
        }

        ApplicabilityMatchDecision decision = applicabilityMatcher.evaluate(descriptor.applicability(), context);
        ApplicabilityResult applicabilityResult = decision.result();
        if (appProperties.isPhase3RuleEngineEnabled()) {
            ApplicabilityResult phase3 = applicabilityRuleEngine.evaluateRules(dmId, context);
            if (phase3 == ApplicabilityResult.NOT_APPLICABLE) {
                applicabilityResult = ApplicabilityResult.NOT_APPLICABLE;
            }
        }
        if (applicabilityFeatureFlags.getPhase2().isEnabled()) {
            // TODO Phase 2: section-level applicability expression comes from parsed DM nodes.
            applicabilityEvaluator.isApplicable("phase2-placeholder", context);
        }
        RenderedDm rendered = renderFacade.render(descriptor, applicabilityResult);

        return new ModuleRenderResponse(
            rendered.dmId(),
            rendered.source(),
            rendered.html(),
            new ModuleRenderMetaResponse(
                rendered.title(),
                toApplicabilityResponse(rendered.applicability()),
                rendered.applicabilityResult(),
                decision.reason(),
                descriptor.applicabilitySource()
            ),
            new ModuleAssetsResponse(rendered.icns()),
            new ModuleLinksResponse(rendered.dmRefs())
        );
    }

    public ModuleUploadResponse uploadModule(
        MultipartFile file,
        String aircraft,
        String engine,
        String variant,
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
            repository.ensureWritableDataDirs();
            byte[] xmlBytes = file.getBytes();
            xmlValidationService.validateWellFormed(new ByteArrayInputStream(xmlBytes));
            xmlValidationService.validateAgainstXsdHook(new ByteArrayInputStream(xmlBytes));

            Path xmlPath = repository.csdbDmDir().resolve(dmId + ".xml").normalize();
            Files.write(xmlPath, xmlBytes);

            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("dmId", dmId);
            meta.put("title", blankToNull(title));
            meta.put("icnId", blankToNull(icnId));

            Map<String, Object> applicability = new LinkedHashMap<>();
            applicability.put("aircraft", toSingleList(aircraft));
            applicability.put("engine", toSingleList(engine));
            applicability.put("variant", toSingleList(variant));
            meta.put("applicability", applicability);

            // Keep legacy fields for compatibility.
            meta.put("aircraft", blankToNull(aircraft));
            meta.put("engine", blankToNull(engine));
            meta.put("variant", blankToNull(variant));

            Path metaPath = repository.csdbMetaDir().resolve(dmId + ".json").normalize();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(metaPath.toFile(), meta);

            return new ModuleUploadResponse(dmId, "Module uploaded successfully");
        } catch (IllegalArgumentException badXml) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, badXml.getMessage(), badXml);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to upload module", ex);
        }
    }

    private List<DataModuleDescriptor> resolveDescriptors() {
        Map<String, PublishedManifestEntry> manifest = repository.readPublishedManifest();

        Map<String, DataModuleDescriptor> descriptors = new LinkedHashMap<>();
        for (Path path : repository.listDmXmlFiles()) {
            String dmId = dmIdFromPath(path);
            descriptors.put(dmId.toUpperCase(Locale.ROOT), buildDescriptor(dmId, manifest));
        }

        for (PublishedManifestEntry entry : manifest.values()) {
            String dmId = entry.dmId();
            if (dmId == null || dmId.isBlank()) {
                continue;
            }
            descriptors.putIfAbsent(dmId.toUpperCase(Locale.ROOT), buildDescriptor(dmId, manifest));
        }

        return descriptors.values().stream().toList();
    }

    private Optional<DataModuleDescriptor> resolveDescriptor(String dmId) {
        return resolveDescriptors().stream().filter(item -> item.dmId().equalsIgnoreCase(dmId)).findFirst();
    }

    private DataModuleDescriptor buildDescriptor(String dmId, Map<String, PublishedManifestEntry> manifest) {
        PublishedManifestEntry entry = manifest.get(dmId.toUpperCase(Locale.ROOT));
        String manifestTitle = entry == null ? "" : nullToEmpty(entry.title());

        String title = firstNonBlank(
            manifestTitle,
            readMetaText(dmId, "title"),
            extractTitleFromXml(dmId),
            dmId
        );

        String primaryIcn = firstNonBlank(
            readMetaText(dmId, "icnId"),
            entry != null && entry.icns() != null && !entry.icns().isEmpty() ? entry.icns().get(0) : "",
            extractFirstIcnFromXml(dmId)
        );

        ApplicabilityResolution applicabilityResolution = applicabilityProvider.resolve(dmId);
        Applicability applicability = applicabilityResolution.applicability();
        boolean hasPublished = repository.hasPublishedHtml(dmId);
        String source = hasPublished ? "published" : "csdb";

        return new DataModuleDescriptor(
            dmId,
            title,
            applicability,
            applicabilityResolution.source().toApiValue(),
            source,
            hasPublished,
            primaryIcn
        );
    }

    private ApplicabilityResponse toApplicabilityResponse(Applicability applicability) {
        return new ApplicabilityResponse(applicability.aircraft(), applicability.engine(), applicability.variant());
    }

    private String readMetaText(String dmId, String field) {
        return repository.readMetaNode(dmId)
            .map(node -> node.path(field))
            .filter(com.fasterxml.jackson.databind.JsonNode::isTextual)
            .map(node -> node.asText("").trim())
            .orElse("");
    }

    private String extractTitleFromXml(String dmId) {
        return repository.readDmXml(dmId)
            .map(xml -> {
                try {
                    var factory = DocumentBuilderFactory.newInstance();
                    factory.setNamespaceAware(false);
                    factory.setExpandEntityReferences(false);
                    var builder = factory.newDocumentBuilder();
                    var doc = builder.parse(new InputSource(new StringReader(xml)));
                    String techName = firstTag(doc, "techName");
                    String infoName = firstTag(doc, "infoName");
                    if (!techName.isBlank() && !infoName.isBlank()) {
                        return techName + " - " + infoName;
                    }
                    return firstNonBlank(techName, infoName);
                } catch (Exception ignored) {
                    return "";
                }
            })
            .orElse("");
    }

    private String extractFirstIcnFromXml(String dmId) {
        return repository.readDmXml(dmId)
            .map(xml -> {
                String upper = xml.toUpperCase(Locale.ROOT);
                int idx = upper.indexOf("ICN-");
                if (idx < 0) {
                    return "";
                }
                int end = idx;
                while (end < upper.length()) {
                    char ch = upper.charAt(end);
                    if (Character.isLetterOrDigit(ch) || ch == '-' || ch == '_') {
                        end++;
                        continue;
                    }
                    break;
                }
                return upper.substring(idx, end);
            })
            .orElse("");
    }

    private String firstTag(org.w3c.dom.Document doc, String tagName) {
        var nodes = doc.getElementsByTagName(tagName);
        if (nodes.getLength() == 0 || nodes.item(0) == null) {
            return "";
        }
        String text = nodes.item(0).getTextContent();
        return text == null ? "" : text.trim();
    }

    private void validateDmId(String dmId) {
        if (dmId == null || dmId.isBlank() || !SAFE_ID.matcher(dmId).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid dmId");
        }
    }

    private String dmIdFromPath(Path path) {
        return stripExtension(path.getFileName().toString());
    }

    private String stripExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        return idx > 0 ? fileName.substring(0, idx) : fileName;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private List<String> toSingleList(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return List.of();
        }
        return List.of(normalized);
    }
}
