package com.s1000Dorg.viewer.modules;

import com.s1000Dorg.viewer.adapters.fs.FsDataRepository;
import com.s1000Dorg.viewer.applicability.ApplicabilityContext;
import com.s1000Dorg.viewer.applicability.ApplicabilityDecision;
import com.s1000Dorg.viewer.applicability.ApplicabilityInfo;
import com.s1000Dorg.viewer.applicability.ApplicabilityRuleEngine;
import com.s1000Dorg.viewer.applicability.ApplicabilityService;
import com.s1000Dorg.viewer.applicability.DmApplicabilityEvaluation;
import com.s1000Dorg.viewer.applicability.DmApplicabilityEvaluator;
import com.s1000Dorg.viewer.applicability.eval.ApplicabilityEvaluator;
import com.s1000Dorg.viewer.config.ApplicabilityProperties;
import com.s1000Dorg.viewer.config.PolicyProperties;
import com.s1000Dorg.viewer.csdb.index.CsdbIndexer;
import com.s1000Dorg.viewer.csdb.persistence.entity.DmEntity;
import com.s1000Dorg.viewer.csdb.persistence.repository.DmIcnRepository;
import com.s1000Dorg.viewer.csdb.persistence.repository.DmRepository;
import com.s1000Dorg.viewer.csdb.persistence.repository.PmcDmRepository;
import com.s1000Dorg.viewer.domain.Applicability;
import com.s1000Dorg.viewer.domain.ApplicabilityResult;
import com.s1000Dorg.viewer.domain.DataModuleDescriptor;
import com.s1000Dorg.viewer.render.InlineApplicabilitySummary;
import com.s1000Dorg.viewer.render.RenderFacade;
import com.s1000Dorg.viewer.render.RenderedDm;
import com.s1000Dorg.viewer.storage.VaultService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ModuleService {

    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9._-]+$");
    private static final List<String> SUPPORTED_GRAPHIC_EXTENSIONS = List.of(".CGM", ".SVG", ".PNG", ".JPG", ".JPEG", ".GIF");

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
    private final PmcDmRepository pmcDmRepository;
    private final VaultService vaultService;
    private final CsdbIndexer csdbIndexer;
    private final DmApplicabilityEvaluator dmApplicabilityEvaluator;

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
        PmcDmRepository pmcDmRepository,
        VaultService vaultService,
        CsdbIndexer csdbIndexer,
        DmApplicabilityEvaluator dmApplicabilityEvaluator
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
        this.pmcDmRepository = pmcDmRepository;
        this.vaultService = vaultService;
        this.csdbIndexer = csdbIndexer;
        this.dmApplicabilityEvaluator = dmApplicabilityEvaluator;
    }

    public ModuleListResponse listModules(String aircraft, String engine, String variant) {
        return listModules(ApplicabilityContext.of(aircraft, engine, variant));
    }

    public ModuleListResponse listModules(ApplicabilityContext context) {
        List<ModuleListItemResponse> modules = new ArrayList<>();
        for (DataModuleDescriptor descriptor : resolveDescriptors()) {
            ModuleEvaluationRow row = toEvaluationRow(descriptor, context);
            ApplicabilityDecision decision = ApplicabilityDecision.of(row.dmApplicabilityStatus(), row.dmApplicabilityReason());
            if (!applicabilityService.includeInModuleList(decision)) {
                continue;
            }
            modules.add(new ModuleListItemResponse(
                row.dmId(),
                row.displayName(),
                toApplicabilityResponse(row.applicability()),
                row.source(),
                row.hasPublishedPreview(),
                row.dmApplicabilityStatus(),
                row.dmApplicabilityReason(),
                row.dmApplicabilitySource()
            ));
        }

        modules.sort(Comparator.comparing(ModuleListItemResponse::dmId));
        return new ModuleListResponse(new ModuleFiltersResponse(context.aircraft(), context.engine(), context.variant()), modules);
    }

    public ModuleRenderResponse renderModule(String dmId, String aircraft, String engine, String variant) {
        return renderModule(dmId, null, ApplicabilityContext.of(aircraft, engine, variant));
    }

    public ModuleRenderResponse renderModule(String dmId, String pmcId, ApplicabilityContext context) {
        validateDmId(dmId);
        validatePmcScopeIfPresent(dmId, pmcId);

        DataModuleDescriptor descriptor = resolveDescriptor(dmId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Module not found"));
        if (!descriptor.hasPublishedPreview()) {
            try {
                vaultService.resolveDmFile(dmId);
            } catch (ResponseStatusException notFound) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Module content not found");
            }
        }

        DmApplicabilityEvaluation dmApplicability = evaluateDmApplicability(descriptor, context);
        ApplicabilityResult applicabilityResult = dmApplicability.status();
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
        RenderedDm rendered = renderFacade.render(descriptor, applicabilityResult, context);
        InlineApplicabilitySummary inlineSummary = rendered.inlineApplicability() == null
            ? InlineApplicabilitySummary.none()
            : rendered.inlineApplicability();

        return new ModuleRenderResponse(
            rendered.dmId(),
            rendered.source(),
            rendered.html(),
            new ModuleApplicabilitySummaryResponse(
                dmApplicability.status(),
                dmApplicability.displayText(),
                dmApplicability.reason(),
                dmApplicability.source()
            ),
            new InlineApplicabilityResponse(
                inlineSummary.mode(),
                inlineSummary.removedCount(),
                inlineSummary.keptCount()
            ),
            new ModuleRenderMetaResponse(
                rendered.title(),
                toApplicabilityResponse(rendered.applicability()),
                applicabilityResult,
                dmApplicability.reason(),
                dmApplicability.source()
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

    public ModuleZipImportResponse importZip(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ZIP file is required");
        }
        String original = Optional.ofNullable(file.getOriginalFilename()).orElse("dataset.zip");
        if (!original.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only .zip archives are supported");
        }

        try {
            repository.ensureWritableDataDirs();
            Files.createDirectories(repository.csdbIcnDir());
            Files.createDirectories(repository.csdbRoot());

            int importedDmCount = 0;
            int importedPmcCount = 0;
            int importedIcnCount = 0;
            int skippedCount = 0;

            try (InputStream input = file.getInputStream(); ZipInputStream zip = new ZipInputStream(input)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        zip.closeEntry();
                        continue;
                    }

                    String fileName = entryFileName(entry);
                    if (fileName.isBlank()) {
                        skippedCount++;
                        zip.closeEntry();
                        continue;
                    }

                    String upper = fileName.toUpperCase(Locale.ROOT);
                    Path destination;
                    if (upper.startsWith("DMC-") && upper.endsWith(".XML")) {
                        destination = repository.csdbDmDir().resolve(fileName);
                        Files.copy(zip, destination, StandardCopyOption.REPLACE_EXISTING);
                        importedDmCount++;
                    } else if (upper.startsWith("PMC-") && upper.endsWith(".XML")) {
                        destination = repository.csdbRoot().resolve(fileName);
                        Files.copy(zip, destination, StandardCopyOption.REPLACE_EXISTING);
                        importedPmcCount++;
                    } else if (upper.startsWith("ICN-") && hasSupportedGraphicExtension(upper)) {
                        destination = repository.csdbIcnDir().resolve(fileName);
                        Files.copy(zip, destination, StandardCopyOption.REPLACE_EXISTING);
                        importedIcnCount++;
                    } else {
                        skippedCount++;
                    }
                    zip.closeEntry();
                }
            }

            csdbIndexer.indexAll();
            String message = "ZIP import complete: DMs=%d, PMCs=%d, ICNs=%d, skipped=%d"
                .formatted(importedDmCount, importedPmcCount, importedIcnCount, skippedCount);
            return new ModuleZipImportResponse(importedDmCount, importedPmcCount, importedIcnCount, skippedCount, message);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to import ZIP dataset", ex);
        }
    }

    private List<DataModuleDescriptor> resolveDescriptors() {
        return dmRepository.findAll().stream()
            .map(this::buildDescriptor)
            .sorted(Comparator.comparing(DataModuleDescriptor::dmId))
            .toList();
    }

    private Optional<DataModuleDescriptor> resolveDescriptor(String dmId) {
        Optional<DmEntity> exact = dmRepository.findByDmIdIgnoreCase(dmId);
        if (exact.isPresent()) {
            return exact.map(this::buildDescriptor);
        }

        String canonical = canonicalDmId(dmId);
        return dmRepository.findAll().stream()
            .filter(entity -> canonicalDmId(entity.getDmId()).equalsIgnoreCase(canonical))
            .findFirst()
            .map(this::buildDescriptor);
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
            primaryIcn,
            nullToEmpty(dmEntity.getSystemCode()),
            nullToEmpty(dmEntity.getInfoCode())
        );
    }

    public Optional<ModuleEvaluationRow> evaluateModuleRow(String dmId, ApplicabilityContext context) {
        return resolveDescriptor(dmId).map(descriptor -> toEvaluationRow(descriptor, context));
    }

    public ModuleEvaluationRow toEvaluationRow(DataModuleDescriptor descriptor, ApplicabilityContext context) {
        DmApplicabilityEvaluation dmApplicability = evaluateDmApplicability(descriptor, context);
        return new ModuleEvaluationRow(
            descriptor.dmId(),
            descriptor.title(),
            descriptor.systemCode(),
            descriptor.infoCode(),
            descriptor.applicability(),
            descriptor.source(),
            descriptor.hasPublishedPreview(),
            dmApplicability.status(),
            dmApplicability.displayText(),
            dmApplicability.reason(),
            dmApplicability.source()
        );
    }

    private DmApplicabilityEvaluation evaluateDmApplicability(DataModuleDescriptor descriptor, ApplicabilityContext context) {
        return dmApplicabilityEvaluator.evaluate(
            descriptor.dmId(),
            descriptor.applicability(),
            descriptor.applicabilitySource(),
            context
        );
    }

    private void validatePmcScopeIfPresent(String dmId, String pmcId) {
        if (pmcId == null || pmcId.isBlank()) {
            return;
        }
        String requestedCanonical = canonicalDmId(dmId);
        boolean present = pmcDmRepository.findByPmcIdIgnoreCaseOrderBySortOrder(pmcId).stream()
            .map(rel -> rel.getId().getDmId())
            .anyMatch(ref -> ref != null && (
                ref.equalsIgnoreCase(dmId)
                || canonicalDmId(ref).equalsIgnoreCase(requestedCanonical)
            ));
        if (!present) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Module not found in PMC scope");
        }
    }

    private String canonicalDmId(String dmId) {
        if (dmId == null) {
            return "";
        }
        String normalized = dmId.trim();
        int issueIdx = normalized.indexOf('_');
        return (issueIdx > 0 ? normalized.substring(0, issueIdx) : normalized).toUpperCase(Locale.ROOT);
    }

    public record ModuleEvaluationRow(
        String dmId,
        String displayName,
        String systemCode,
        String infoCode,
        Applicability applicability,
        String source,
        boolean hasPublishedPreview,
        ApplicabilityResult dmApplicabilityStatus,
        String dmApplicabilityDisplayText,
        String dmApplicabilityReason,
        String dmApplicabilitySource
    ) {
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

    private String entryFileName(ZipEntry entry) {
        String raw = Optional.ofNullable(entry.getName()).orElse("");
        if (raw.isBlank()) {
            return "";
        }
        String normalized = raw.replace('\\', '/');
        String fileName = Path.of(normalized).getFileName().toString();
        if (fileName.contains("..")) {
            return "";
        }
        return fileName;
    }

    private boolean hasSupportedGraphicExtension(String fileNameUpper) {
        for (String extension : SUPPORTED_GRAPHIC_EXTENSIONS) {
            if (fileNameUpper.endsWith(extension)) {
                return true;
            }
        }
        return false;
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


