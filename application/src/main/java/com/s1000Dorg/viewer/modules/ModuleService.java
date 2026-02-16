package com.s1000Dorg.viewer.modules;

import com.s1000Dorg.viewer.adapters.fs.FsDataRepository;
import com.s1000Dorg.viewer.applicability.ApplicabilityContext;
import com.s1000Dorg.viewer.applicability.ApplicabilityDecision;
import com.s1000Dorg.viewer.applicability.ApplicabilityInfo;
import com.s1000Dorg.viewer.applicability.ApplicabilityRuleEngine;
import com.s1000Dorg.viewer.applicability.ApplicabilityService;
import com.s1000Dorg.viewer.applicability.eval.ApplicabilityEvaluator;
import com.s1000Dorg.viewer.config.ApplicabilityProperties;
import com.s1000Dorg.viewer.config.PolicyProperties;
import com.s1000Dorg.viewer.csdb.index.CsdbIndexer;
import com.s1000Dorg.viewer.csdb.persistence.entity.DmEntity;
import com.s1000Dorg.viewer.csdb.persistence.repository.DmIcnRepository;
import com.s1000Dorg.viewer.csdb.persistence.repository.DmRepository;
import com.s1000Dorg.viewer.domain.Applicability;
import com.s1000Dorg.viewer.domain.ApplicabilityResult;
import com.s1000Dorg.viewer.domain.DataModuleDescriptor;
import com.s1000Dorg.viewer.render.RenderFacade;
import com.s1000Dorg.viewer.render.RenderedDm;
import com.s1000Dorg.viewer.storage.VaultService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ModuleService {

    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9._-]+$");

    private final FsDataRepository repository;
    private final ApplicabilityService applicabilityService;
    private final ApplicabilityProperties applicabilityProperties;
    private final PolicyProperties policyProperties;
    private final ApplicabilityEvaluator applicabilityEvaluator;
    private final ApplicabilityRuleEngine applicabilityRuleEngine;
    private final RenderFacade renderFacade;
    private final XmlValidationService xmlValidationService;
    private final ObjectMapper objectMapper;
    private final DmRepository dmRepository;
    private final DmIcnRepository dmIcnRepository;
    private final VaultService vaultService;
    private final CsdbIndexer csdbIndexer;

    public ModuleService(
        FsDataRepository repository,
        ApplicabilityService applicabilityService,
        ApplicabilityProperties applicabilityProperties,
        PolicyProperties policyProperties,
        ApplicabilityEvaluator applicabilityEvaluator,
        ApplicabilityRuleEngine applicabilityRuleEngine,
        RenderFacade renderFacade,
        XmlValidationService xmlValidationService,
        ObjectMapper objectMapper,
        DmRepository dmRepository,
        DmIcnRepository dmIcnRepository,
        VaultService vaultService,
        CsdbIndexer csdbIndexer
    ) {
        this.repository = repository;
        this.applicabilityService = applicabilityService;
        this.applicabilityProperties = applicabilityProperties;
        this.policyProperties = policyProperties;
        this.applicabilityEvaluator = applicabilityEvaluator;
        this.applicabilityRuleEngine = applicabilityRuleEngine;
        this.renderFacade = renderFacade;
        this.xmlValidationService = xmlValidationService;
        this.objectMapper = objectMapper;
        this.dmRepository = dmRepository;
        this.dmIcnRepository = dmIcnRepository;
        this.vaultService = vaultService;
        this.csdbIndexer = csdbIndexer;
    }

    public ModuleListResponse listModules(String aircraft, String engine, String variant) {
        ApplicabilityContext context = ApplicabilityContext.of(aircraft, engine, variant);

        List<ModuleListItemResponse> modules = new ArrayList<>();
        for (DataModuleDescriptor descriptor : resolveDescriptors()) {
            ApplicabilityDecision decision = applicabilityService.evaluate(descriptor.applicability(), context);
            if (!applicabilityService.includeInModuleList(decision)) {
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
        if (!descriptor.hasPublishedPreview()) {
            try {
                vaultService.resolveDmFile(dmId);
            } catch (ResponseStatusException notFound) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Module content not found");
            }
        }

        ApplicabilityDecision decision = applicabilityService.evaluate(descriptor.applicability(), context);
        ApplicabilityResult applicabilityResult = decision.result();
        if (policyProperties.getEnforcement().isEnabled()) {
            ApplicabilityResult policyResult = applicabilityRuleEngine.evaluateRules(dmId, context);
            if (policyResult == ApplicabilityResult.NOT_APPLICABLE) {
                applicabilityResult = ApplicabilityResult.NOT_APPLICABLE;
            }
        }
        if (applicabilityProperties.getFragmentEvaluation().isEnabled()) {
            // TODO: section-level applicability expression comes from parsed DM nodes.
            applicabilityEvaluator.isApplicable("fragment-evaluation-placeholder", context);
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
            csdbIndexer.reindexDm(dmId);

            return new ModuleUploadResponse(dmId, "Module uploaded successfully");
        } catch (IllegalArgumentException badXml) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, badXml.getMessage(), badXml);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to upload module", ex);
        }
    }

    private List<DataModuleDescriptor> resolveDescriptors() {
        return dmRepository.findAll().stream()
            .map(this::buildDescriptor)
            .sorted(Comparator.comparing(DataModuleDescriptor::dmId))
            .toList();
    }

    private Optional<DataModuleDescriptor> resolveDescriptor(String dmId) {
        return dmRepository.findByDmIdIgnoreCase(dmId).map(this::buildDescriptor);
    }

    private DataModuleDescriptor buildDescriptor(DmEntity dmEntity) {
        String dmId = dmEntity.getDmId();
        String title = firstNonBlank(dmEntity.getDisplayName(), readMetaText(dmId, "title"), dmId);
        String primaryIcn = dmIcnRepository.findByDmIdIgnoreCase(dmId).stream()
            .map(rel -> rel.getId().getIcnId())
            .findFirst()
            .orElse("");
        Applicability dbApplicability = new Applicability(
            splitCsv(dmEntity.getAircraftTags()),
            splitCsv(dmEntity.getEngineTags()),
            splitCsv(dmEntity.getVariantTags())
        );
        ApplicabilityInfo applicabilityInfo = applicabilityService.resolve(dmId);
        Applicability applicability = applicabilityInfo.applicability();
        String applicabilitySource = applicabilityInfo.source().toApiValue();
        if (applicability.isUnknown() && !dbApplicability.isUnknown()) {
            applicability = dbApplicability;
            applicabilitySource = "meta";
        }
        boolean hasPublished = vaultService.resolvePublishedHtml(dmId).isPresent();
        String source = hasPublished ? "published" : "csdb";

        return new DataModuleDescriptor(
            dmId,
            title,
            applicability,
            applicabilitySource,
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

    private List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(csv.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .toList();
    }
}


