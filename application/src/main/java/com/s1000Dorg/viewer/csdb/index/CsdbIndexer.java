package com.s1000Dorg.viewer.csdb.index;

import com.fasterxml.jackson.databind.JsonNode;
import com.s1000Dorg.viewer.adapters.fs.FsDataRepository;
import com.s1000Dorg.viewer.csdb.persistence.entity.DmEntity;
import com.s1000Dorg.viewer.csdb.persistence.entity.DmIcnEntity;
import com.s1000Dorg.viewer.csdb.persistence.entity.DmIcnId;
import com.s1000Dorg.viewer.csdb.persistence.entity.IcnEntity;
import com.s1000Dorg.viewer.csdb.persistence.entity.PmcDmEntity;
import com.s1000Dorg.viewer.csdb.persistence.entity.PmcDmId;
import com.s1000Dorg.viewer.csdb.persistence.entity.PmcEntity;
import com.s1000Dorg.viewer.csdb.persistence.repository.DmIcnRepository;
import com.s1000Dorg.viewer.csdb.persistence.repository.DmRepository;
import com.s1000Dorg.viewer.csdb.persistence.repository.IcnRepository;
import com.s1000Dorg.viewer.csdb.persistence.repository.PmcDmRepository;
import com.s1000Dorg.viewer.csdb.persistence.repository.PmcRepository;
import com.s1000Dorg.viewer.storage.VaultService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

@Service
public class CsdbIndexer {

    private static final Logger log = LoggerFactory.getLogger(CsdbIndexer.class);
    private static final Pattern DM_REF_PATTERN = Pattern.compile("DMC-[A-Z0-9_-]+");
    private static final Pattern ICN_PATTERN = Pattern.compile("ICN-[A-Z0-9_.-]+");

    private final FsDataRepository repository;
    private final VaultService vaultService;
    private final DmRepository dmRepository;
    private final PmcRepository pmcRepository;
    private final PmcDmRepository pmcDmRepository;
    private final IcnRepository icnRepository;
    private final DmIcnRepository dmIcnRepository;

    public CsdbIndexer(
        FsDataRepository repository,
        VaultService vaultService,
        DmRepository dmRepository,
        PmcRepository pmcRepository,
        PmcDmRepository pmcDmRepository,
        IcnRepository icnRepository,
        DmIcnRepository dmIcnRepository
    ) {
        this.repository = repository;
        this.vaultService = vaultService;
        this.dmRepository = dmRepository;
        this.pmcRepository = pmcRepository;
        this.pmcDmRepository = pmcDmRepository;
        this.icnRepository = icnRepository;
        this.dmIcnRepository = dmIcnRepository;
    }

    @Transactional
    public void indexAll() {
        indexIcnFiles();
        indexDmFiles();
        indexPmcFiles();
        log.info(
            "CSDB index complete: dm={}, pmc={}, icn={}",
            dmRepository.count(),
            pmcRepository.count(),
            icnRepository.count()
        );
    }

    @Transactional
    public void reindexDm(String dmId) {
        repository.findDmXml(dmId).ifPresent(this::indexSingleDm);
    }

    private void indexIcnFiles() {
        for (Path path : repository.listIcnFiles()) {
            upsertIcn(path);
        }
    }

    private void indexDmFiles() {
        for (Path path : repository.listDmXmlFiles()) {
            try {
                indexSingleDm(path);
            } catch (RuntimeException ex) {
                log.warn("Skipping DM file {} during indexing: {}", path.getFileName(), ex.getMessage());
            }
        }
    }

    private void indexSingleDm(Path path) {
        String dmId = stripExtension(path.getFileName().toString());
        String relativePath = toVaultRelativePath(path);
        String fileHash = vaultService.computeFileHash(path);
        DmEntity dm = dmRepository.findByDmIdIgnoreCase(dmId).orElseGet(DmEntity::new);
        if (isUnchanged(dm.getFileHash(), fileHash) && relativePath.equals(dm.getVaultPath())) {
            return;
        }

        String xml = readFile(path);
        Document document;
        try {
            document = parseXml(xml);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Failed to parse DM XML " + path.getFileName(), ex);
        }

        dm.setDmId(dmId);
        dm.setVaultPath(relativePath);
        dm.setFileHash(fileHash);
        dm.setLastIndexed(LocalDateTime.now());
        dm.setDisplayName(extractDisplayName(document, dmId));
        dm.setModelIdent(firstAttr(document, "dmCode", "modelIdentCode"));
        dm.setSystemCode(firstAttr(document, "dmCode", "systemCode"));
        dm.setInfoCode(firstAttr(document, "dmCode", "infoCode"));
        dm.setLanguageCode(firstNonBlank(
            firstAttr(document, "language", "languageIsoCode"),
            firstAttr(document, "language", "languageCode")
        ));
        dm.setIssueNumber(firstAttr(document, "issueInfo", "issueNumber"));
        dm.setInWork(firstAttr(document, "issueInfo", "inWork"));

        Optional<JsonNode> sidecar = repository.readMetaNode(dmId);
        dm.setAircraftTags(String.join(",", readApplicability(sidecar, "aircraft")));
        dm.setEngineTags(String.join(",", readApplicability(sidecar, "engine")));
        dm.setVariantTags(String.join(",", readApplicability(sidecar, "variant")));
        dmRepository.save(dm);

        Set<String> icnIds = extractMatches(ICN_PATTERN, xml.toUpperCase(Locale.ROOT));
        dmIcnRepository.deleteByDmIdIgnoreCase(dmId);
        for (String icnId : icnIds) {
            if (icnRepository.findByIcnIdIgnoreCase(icnId).isPresent()) {
                dmIcnRepository.save(new DmIcnEntity(new DmIcnId(dmId, icnId)));
            }
        }
    }

    private void indexPmcFiles() {
        Map<String, String> dmByCanonical = dmRepository.findAll().stream()
            .collect(java.util.stream.Collectors.toMap(
                dm -> canonicalDmId(dm.getDmId()),
                DmEntity::getDmId,
                (first, second) -> first,
                java.util.LinkedHashMap::new
            ));

        for (Path path : repository.listPmcXmlFiles()) {
            String pmcId = stripExtension(path.getFileName().toString());
            String relativePath = toVaultRelativePath(path);
            String fileHash = vaultService.computeFileHash(path);
            String xml = readFile(path);
            Set<String> dmRefs = extractMatches(DM_REF_PATTERN, xml.toUpperCase(Locale.ROOT));

            PmcEntity pmc = pmcRepository.findByPmcIdIgnoreCase(pmcId).orElseGet(PmcEntity::new);
            if (!isUnchanged(pmc.getFileHash(), fileHash) || !relativePath.equals(pmc.getVaultPath())) {
                pmc.setPmcId(pmcId);
                pmc.setVaultPath(relativePath);
                pmc.setFileHash(fileHash);
                pmc.setLastIndexed(LocalDateTime.now());
                pmc.setTitle(firstNonBlank(extractFirstTag(xml, "pmTitle"), pmcId));
                pmcRepository.save(pmc);
            }

            pmcDmRepository.deleteByPmcIdIgnoreCase(pmcId);
            int sortOrder = 1;
            for (String dmRef : dmRefs) {
                String resolvedDmId = resolveDmReference(dmRef, dmByCanonical);
                if (resolvedDmId == null || resolvedDmId.isBlank()) {
                    continue;
                }
                pmcDmRepository.save(new PmcDmEntity(new PmcDmId(pmcId, resolvedDmId), sortOrder++));
            }
        }
    }

    private String resolveDmReference(String dmRef, Map<String, String> dmByCanonical) {
        Optional<DmEntity> exact = dmRepository.findByDmIdIgnoreCase(dmRef);
        if (exact.isPresent()) {
            return exact.get().getDmId();
        }
        return dmByCanonical.get(canonicalDmId(dmRef));
    }

    private String canonicalDmId(String dmId) {
        if (dmId == null || dmId.isBlank()) {
            return "";
        }
        String normalized = dmId.trim().toUpperCase(Locale.ROOT);
        int issueIdx = normalized.indexOf('_');
        return issueIdx > 0 ? normalized.substring(0, issueIdx) : normalized;
    }

    private void upsertIcn(Path path) {
        String icnId = stripExtension(path.getFileName().toString());
        String relativePath = toVaultRelativePath(path);
        String fileHash = vaultService.computeFileHash(path);

        IcnEntity icn = icnRepository.findByIcnIdIgnoreCase(icnId).orElseGet(IcnEntity::new);
        if (isUnchanged(icn.getFileHash(), fileHash) && relativePath.equals(icn.getVaultPath())) {
            return;
        }

        icn.setIcnId(icnId);
        icn.setVaultPath(relativePath);
        icn.setType(extensionOf(path));
        icn.setFileHash(fileHash);
        icn.setLastIndexed(LocalDateTime.now());
        icnRepository.save(icn);
    }

    private String toVaultRelativePath(Path absolutePath) {
        Path normalized = absolutePath.toAbsolutePath().normalize();
        vaultService.validatePathSecurity(repository.csdbRoot(), normalized);
        return repository.csdbRoot().toAbsolutePath().normalize().relativize(normalized).toString().replace('\\', '/');
    }

    private List<String> readApplicability(Optional<JsonNode> sidecar, String key) {
        if (sidecar.isEmpty()) {
            return List.of();
        }
        JsonNode root = sidecar.get();
        JsonNode applicability = root.path("applicability").path(key);
        if (applicability.isArray()) {
            List<String> values = new ArrayList<>();
            applicability.forEach(node -> {
                if (node.isTextual() && !node.asText().isBlank()) {
                    values.add(node.asText().trim());
                }
            });
            return values;
        }
        JsonNode legacy = root.path(key);
        if (legacy.isTextual() && !legacy.asText().isBlank()) {
            return List.of(legacy.asText().trim());
        }
        return List.of();
    }

    private Document parseXml(String xml) {
        try {
            String sanitized = sanitizeXml(xml);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            try {
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            } catch (Exception ignored) {
                // Parser feature support varies by runtime.
            }
            factory.setNamespaceAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(new InputSource(new java.io.StringReader(sanitized)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse XML during CSDB indexing", ex);
        }
    }

    private String readFile(Path path) {
        try {
            return sanitizeXml(Files.readString(path, StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read file " + path, ex);
        }
    }

    private String sanitizeXml(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String text = raw;
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        int firstSignificant = 0;
        while (firstSignificant < text.length()) {
            char c = text.charAt(firstSignificant);
            if (Character.isWhitespace(c)) {
                firstSignificant++;
                continue;
            }
            if (c == '<') {
                break;
            }
            firstSignificant++;
        }
        return firstSignificant > 0 ? text.substring(firstSignificant) : text;
    }

    private String extractDisplayName(Document document, String fallback) {
        String techName = firstTagText(document, "techName");
        String infoName = firstTagText(document, "infoName");
        if (!techName.isBlank() && !infoName.isBlank()) {
            return techName + " - " + infoName;
        }
        return firstNonBlank(techName, infoName, fallback);
    }

    private String firstAttr(Document document, String tagName, String attrName) {
        var nodes = document.getElementsByTagName(tagName);
        if (nodes.getLength() == 0 || !(nodes.item(0) instanceof Element element)) {
            return "";
        }
        String value = element.getAttribute(attrName);
        return value == null ? "" : value.trim();
    }

    private String firstTagText(Document document, String tagName) {
        var nodes = document.getElementsByTagName(tagName);
        if (nodes.getLength() == 0 || nodes.item(0) == null) {
            return "";
        }
        String text = nodes.item(0).getTextContent();
        return text == null ? "" : text.trim();
    }

    private String extractFirstTag(String xml, String tagName) {
        try {
            Document document = parseXml(xml);
            return firstTagText(document, tagName);
        } catch (Exception ex) {
            return "";
        }
    }

    private Set<String> extractMatches(Pattern pattern, String source) {
        Set<String> values = new LinkedHashSet<>();
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            values.add(matcher.group());
        }
        return values;
    }

    private boolean isUnchanged(String existingHash, String newHash) {
        return existingHash != null && existingHash.equalsIgnoreCase(newHash);
    }

    private String extensionOf(Path path) {
        String name = path.getFileName().toString();
        int idx = name.lastIndexOf('.');
        if (idx < 0) {
            return "";
        }
        return name.substring(idx + 1).toUpperCase(Locale.ROOT);
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
}
