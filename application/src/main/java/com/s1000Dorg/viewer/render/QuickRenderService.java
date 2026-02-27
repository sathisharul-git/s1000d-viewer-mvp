package com.s1000Dorg.viewer.render;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import com.s1000Dorg.viewer.domain.ApplicabilityResult;
import com.s1000Dorg.viewer.domain.DataModuleDescriptor;
import com.s1000Dorg.viewer.applicability.ApplicabilityContext;
import com.s1000Dorg.viewer.applicability.InlineApplicabilityFilterResult;
import com.s1000Dorg.viewer.applicability.InlineApplicabilityFilterService;
import com.s1000Dorg.viewer.modules.XmlValidationService;
import com.s1000Dorg.viewer.storage.VaultService;

@Service
public class QuickRenderService {

    private static final Pattern DM_REF_PATTERN = Pattern.compile("DMC-[A-Z0-9_-]+");
    private static final Pattern ICN_PATTERN = Pattern.compile("ICN-[A-Z0-9_-]+");

    private final VaultService vaultService;
    private final RenderCache renderCache;
    private final XmlValidationService xmlValidationService;
    private final InlineApplicabilityFilterService inlineApplicabilityFilterService;

    public QuickRenderService(
        VaultService vaultService,
        RenderCache renderCache,
        XmlValidationService xmlValidationService,
        InlineApplicabilityFilterService inlineApplicabilityFilterService
    ) {
        this.vaultService = vaultService;
        this.renderCache = renderCache;
        this.xmlValidationService = xmlValidationService;
        this.inlineApplicabilityFilterService = inlineApplicabilityFilterService;
    }

    public RenderedDm render(
        String dmId,
        DataModuleDescriptor descriptor,
        ApplicabilityResult applicabilityResult,
        ApplicabilityContext context
    ) {
        String xml;
        try {
            Path xmlPath = vaultService.resolveDmFile(dmId);
            xml = Files.readString(xmlPath, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("DM XML not found for quick rendering", ex);
        }

        xmlValidationService.validateWellFormed(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        InlineApplicabilityFilterResult inlineFilterResult = inlineApplicabilityFilterService.apply(xml, context);
        String filteredXml = inlineFilterResult.xml();

        String cacheKey = cacheKey(dmId, filteredXml, context);
        var cached = renderCache.get(cacheKey);
        if (cached.isPresent()) {
            return withApplicability(cached.get(), descriptor, applicabilityResult, inlineFilterResult);
        }

        String html = transform(filteredXml);
        List<String> icns = extract(ICN_PATTERN, filteredXml.toUpperCase());
        List<String> dmRefs = extract(DM_REF_PATTERN, filteredXml.toUpperCase());

        String title = extractTitle(filteredXml, descriptor.title());
        RenderedDm rendered = new RenderedDm(
            dmId,
            "quick",
            html,
            title,
            descriptor.applicability(),
            applicabilityResult,
            icns,
            dmRefs,
            new InlineApplicabilitySummary(
                inlineFilterResult.mode(),
                inlineFilterResult.removedCount(),
                inlineFilterResult.keptCount()
            )
        );
        renderCache.put(cacheKey, rendered);
        return rendered;
    }

    /**
     * @param cached
     * @param descriptor
     * @param result
     * @return
     */
    private RenderedDm withApplicability(
        RenderedDm cached,
        DataModuleDescriptor descriptor,
        ApplicabilityResult result,
        InlineApplicabilityFilterResult inlineFilterResult
    ) {
        return new RenderedDm(
            cached.dmId(),
            cached.source(),
            cached.html(),
            cached.title(),
            descriptor.applicability(),
            result,
            cached.icns(),
            cached.dmRefs(),
            new InlineApplicabilitySummary(
                inlineFilterResult.mode(),
                inlineFilterResult.removedCount(),
                inlineFilterResult.keptCount()
            )
        );
    }
    private String transform(String xml) {
        try {
            TransformerFactory factory = transformerFactory();
            Source xslt = new StreamSource(new ClassPathResource("xslt/s1000d-dm-to-html.xsl").getInputStream());
            Transformer transformer = factory.newTransformer(xslt);
            Source xmlSource = new StreamSource(new StringReader(sanitizeXml(xml)));
            java.io.StringWriter out = new java.io.StringWriter();
            transformer.transform(xmlSource, new StreamResult(out));
            return out.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Quick render failed", ex);
        }
    }

    private TransformerFactory transformerFactory() {
        try {
            return TransformerFactory.newInstance("net.sf.saxon.TransformerFactoryImpl", getClass().getClassLoader());
        } catch (Throwable ignored) {
            return TransformerFactory.newInstance();
        }
    }
// Extracts a title from the DM XML using techName and infoName, with a fallback to the provided title if extraction fails.
    private String extractTitle(String xml, String fallbackTitle) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler() {
                @Override
                public void warning(SAXParseException e) throws SAXParseException {
                    throw e;
                }

                @Override
                public void error(SAXParseException e) throws SAXParseException {
                    throw e;
                }

                @Override
                public void fatalError(SAXParseException e) throws SAXParseException {
                    throw e;
                }
            });
            Document document = builder.parse(new InputSource(new StringReader(sanitizeXml(xml))));
            String techName = firstText(document, "techName");
            String infoName = firstText(document, "infoName");
            if (!techName.isBlank() && !infoName.isBlank()) {
                return techName + " - " + infoName;
            }
            if (!techName.isBlank()) {
                return techName;
            }
            if (!infoName.isBlank()) {
                return infoName;
            }
            return fallbackTitle;
        } catch (Exception ex) {
            return fallbackTitle;
        }
    }

    private String firstText(Document document, String tagName) {
        var nodes = document.getElementsByTagName(tagName);
        if (nodes.getLength() == 0 || nodes.item(0) == null) {
            return "";
        }
        String text = nodes.item(0).getTextContent();
        return text == null ? "" : text.trim();
    }

    private List<String> extract(Pattern pattern, String input) {
        List<String> values = new ArrayList<>();
        Matcher matcher = pattern.matcher(input);
        while (matcher.find()) {
            String value = matcher.group();
            if (!values.contains(value)) {
                values.add(value);
            }
        }
        return values;
    }

    private String cacheKey(String dmId, String xml, ApplicabilityContext context) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder signature = new StringBuilder(xml).append('|');
            if (context != null) {
                signature.append(context.aircraft()).append('|')
                    .append(context.engine()).append('|')
                    .append(context.variant()).append('|');
                for (Map.Entry<String, String> entry : context.allProductAttributes().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .toList()) {
                    signature.append(entry.getKey()).append('=').append(entry.getValue()).append(';');
                }
            }
            byte[] hash = digest.digest(signature.toString().getBytes(StandardCharsets.UTF_8));
            return dmId + "_" + HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            return dmId + "_" + Integer.toHexString((xml + "_" + context).hashCode());
        }
    }

    private String sanitizeXml(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        if (raw.charAt(0) == '\uFEFF') {
            return raw.substring(1);
        }
        return raw;
    }
}

