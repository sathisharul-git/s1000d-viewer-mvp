package com.s1000Dorg.viewer.render;

import com.s1000Dorg.viewer.adapters.fs.FsDataRepository;
import com.s1000Dorg.viewer.domain.ApplicabilityResult;
import com.s1000Dorg.viewer.domain.DataModuleDescriptor;
import com.s1000Dorg.viewer.modules.XmlValidationService;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
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

@Service
public class QuickRenderService {

    private static final Pattern DM_REF_PATTERN = Pattern.compile("DMC-[A-Z0-9_-]+");
    private static final Pattern ICN_PATTERN = Pattern.compile("ICN-[A-Z0-9_-]+");

    private final FsDataRepository repository;
    private final RenderCache renderCache;
    private final XmlValidationService xmlValidationService;

    public QuickRenderService(FsDataRepository repository, RenderCache renderCache, XmlValidationService xmlValidationService) {
        this.repository = repository;
        this.renderCache = renderCache;
        this.xmlValidationService = xmlValidationService;
    }

    public RenderedDm render(String dmId, DataModuleDescriptor descriptor, ApplicabilityResult applicabilityResult) {
        String xml = repository.readDmXml(dmId)
            .orElseThrow(() -> new IllegalStateException("DM XML not found for quick rendering"));

        xmlValidationService.validateWellFormed(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        String cacheKey = cacheKey(dmId, xml);
        var cached = renderCache.get(cacheKey);
        if (cached.isPresent()) {
            return withApplicability(cached.get(), descriptor, applicabilityResult);
        }

        String html = transform(xml);
        List<String> icns = extract(ICN_PATTERN, xml.toUpperCase());
        List<String> dmRefs = extract(DM_REF_PATTERN, xml.toUpperCase());

        String title = extractTitle(xml, descriptor.title());
        RenderedDm rendered = new RenderedDm(
            dmId,
            "quick",
            html,
            title,
            descriptor.applicability(),
            applicabilityResult,
            icns,
            dmRefs
        );
        renderCache.put(cacheKey, rendered);
        return rendered;
    }

    private RenderedDm withApplicability(RenderedDm cached, DataModuleDescriptor descriptor, ApplicabilityResult result) {
        return new RenderedDm(
            cached.dmId(),
            cached.source(),
            cached.html(),
            cached.title(),
            descriptor.applicability(),
            result,
            cached.icns(),
            cached.dmRefs()
        );
    }

    private String transform(String xml) {
        try {
            TransformerFactory factory = transformerFactory();
            Source xslt = new StreamSource(new ClassPathResource("xslt/s1000d-dm-to-html.xsl").getInputStream());
            Transformer transformer = factory.newTransformer(xslt);
            Source xmlSource = new StreamSource(new StringReader(xml));
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

    private String extractTitle(String xml, String fallbackTitle) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
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

    private String cacheKey(String dmId, String xml) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(xml.getBytes(StandardCharsets.UTF_8));
            return dmId + "_" + HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            return dmId + "_" + Integer.toHexString(xml.hashCode());
        }
    }
}

