package com.s1000Dorg.viewer.applicability;

import com.fasterxml.jackson.databind.JsonNode;
import com.s1000Dorg.viewer.adapters.fs.FsDataRepository;
import com.s1000Dorg.viewer.domain.Applicability;
import com.s1000Dorg.viewer.domain.ApplicabilityResult;
import com.s1000Dorg.viewer.storage.VaultService;
import com.s1000Dorg.viewer.applicability.xml.ApplicXmlExpression;
import com.s1000Dorg.viewer.applicability.xml.ApplicabilityXmlEvaluator;
import com.s1000Dorg.viewer.applicability.xml.ApplicabilityXmlParseException;
import com.s1000Dorg.viewer.applicability.xml.ApplicabilityXmlParser;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

@Service
public class DmApplicabilityEvaluator {

    private final FsDataRepository repository;
    private final VaultService vaultService;
    private final ApplicabilityService applicabilityService;
    private final ApplicabilityXmlParser xmlParser = new ApplicabilityXmlParser();
    private final ApplicabilityXmlEvaluator xmlEvaluator = new ApplicabilityXmlEvaluator();

    public DmApplicabilityEvaluator(
        FsDataRepository repository,
        VaultService vaultService,
        ApplicabilityService applicabilityService
    ) {
        this.repository = repository;
        this.vaultService = vaultService;
        this.applicabilityService = applicabilityService;
    }

    public DmApplicabilityEvaluation evaluate(
        String dmId,
        Applicability fallbackApplicability,
        String fallbackSource,
        ApplicabilityContext context
    ) {
        Optional<String> metadataApplicXml = findApplicabilityXmlInSidecar(dmId);
        if (metadataApplicXml.isPresent()) {
            return evaluateApplicabilityXml(metadataApplicXml.get(), context, "metadata");
        }

        if (fallbackApplicability != null && !fallbackApplicability.isUnknown()) {
            ApplicabilityDecision decision = applicabilityService.evaluate(fallbackApplicability, context);
            String displayText = "aircraft=%s | engine=%s | variant=%s".formatted(
                String.join(",", fallbackApplicability.aircraft()),
                String.join(",", fallbackApplicability.engine()),
                String.join(",", fallbackApplicability.variant())
            );
            return new DmApplicabilityEvaluation(
                decision.result(),
                displayText,
                decision.reason(),
                normalizeSource(fallbackSource)
            );
        }

        Optional<String> headerApplicXml = findDmHeaderApplicabilityXml(dmId);
        if (headerApplicXml.isPresent()) {
            return evaluateApplicabilityXml(headerApplicXml.get(), context, "dmHeader");
        }

        return DmApplicabilityEvaluation.unknown("No metadata or DM header applicability found");
    }

    private String normalizeSource(String fallbackSource) {
        if (fallbackSource == null || fallbackSource.isBlank()) {
            return "metadata";
        }
        if ("published".equalsIgnoreCase(fallbackSource) || "meta".equalsIgnoreCase(fallbackSource)) {
            return "metadata";
        }
        return fallbackSource.trim();
    }

    public Map<String, String> extractInlineApplicabilityDefinitions(String xml) {
        Map<String, String> definitions = new LinkedHashMap<>();
        Document document = parseXml(xml);
        NodeList nodes = document.getElementsByTagName("applic");
        for (int i = 0; i < nodes.getLength(); i++) {
            if (!(nodes.item(i) instanceof Element element)) {
                continue;
            }
            String id = safeTrim(element.getAttribute("id"));
            if (id.isBlank()) {
                continue;
            }
            definitions.put(id, toXml(element));
        }
        return definitions;
    }

    public ApplicabilityResult evaluateInlineApplicability(String applicXml, ApplicabilityContext context) {
        try {
            ApplicXmlExpression expression = xmlParser.parse(applicXml);
            return xmlEvaluator.evaluate(expression, context);
        } catch (ApplicabilityXmlParseException ex) {
            return ApplicabilityResult.UNKNOWN;
        }
    }

    private DmApplicabilityEvaluation evaluateApplicabilityXml(String xml, ApplicabilityContext context, String source) {
        try {
            ApplicXmlExpression expression = xmlParser.parse(xml);
            ApplicabilityResult result = xmlEvaluator.evaluate(expression, context);
            String displayText = extractDisplayText(xml).orElse("");
            String reason = switch (result) {
                case APPLICABLE -> "Applicability conditions matched";
                case NOT_APPLICABLE -> "Applicability conditions did not match";
                case UNKNOWN -> "Applicability could not be fully evaluated";
            };
            return new DmApplicabilityEvaluation(result, displayText, reason, source);
        } catch (ApplicabilityXmlParseException ex) {
            return new DmApplicabilityEvaluation(
                ApplicabilityResult.UNKNOWN,
                "",
                "Invalid applicability expression: " + ex.getMessage(),
                source
            );
        }
    }

    private Optional<String> findApplicabilityXmlInSidecar(String dmId) {
        Optional<JsonNode> sidecarNode = repository.readMetaNode(dmId);
        if (sidecarNode.isEmpty()) {
            return Optional.empty();
        }

        Deque<JsonNode> stack = new ArrayDeque<>();
        stack.push(sidecarNode.get());
        while (!stack.isEmpty()) {
            JsonNode current = stack.pop();
            if (current == null) {
                continue;
            }
            if (current.isTextual()) {
                String text = current.asText("");
                String lower = text.toLowerCase();
                if (lower.contains("<applic") || lower.contains("&lt;applic")) {
                    return Optional.of(text);
                }
            } else if (current.isObject()) {
                current.fields().forEachRemaining(entry -> stack.push(entry.getValue()));
            } else if (current.isArray()) {
                current.forEach(stack::push);
            }
        }
        return Optional.empty();
    }

    private Optional<String> findDmHeaderApplicabilityXml(String dmId) {
        try {
            String xml = Files.readString(vaultService.resolveDmFile(dmId), StandardCharsets.UTF_8);
            Document document = parseXml(xml);
            NodeList statusNodes = document.getElementsByTagName("dmStatus");
            if (statusNodes.getLength() == 0 || !(statusNodes.item(0) instanceof Element dmStatus)) {
                return Optional.empty();
            }
            Element applic = firstChildElementByTag(dmStatus, "applic");
            if (applic == null) {
                return Optional.empty();
            }
            return Optional.of(toXml(applic));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private Optional<String> extractDisplayText(String applicXml) {
        try {
            Document document = parseXml(applicXml);
            NodeList nodes = document.getElementsByTagName("displayText");
            if (nodes.getLength() == 0 || !(nodes.item(0) instanceof Element displayText)) {
                return Optional.empty();
            }
            NodeList simpleParas = displayText.getElementsByTagName("simplePara");
            if (simpleParas.getLength() == 0) {
                return Optional.of(safeTrim(displayText.getTextContent()));
            }
            return Optional.of(safeTrim(simpleParas.item(0).getTextContent()));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private Document parseXml(String xml) {
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
            return builder.parse(new InputSource(new StringReader(sanitizeXml(xml))));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse applicability XML", ex);
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

    private String toXml(Node node) {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            java.io.StringWriter writer = new java.io.StringWriter();
            transformer.transform(new DOMSource(node), new StreamResult(writer));
            return writer.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize applicability node", ex);
        }
    }

    private Element firstChildElementByTag(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element child = (Element) children.item(i);
            if (tagName.equalsIgnoreCase(child.getTagName())) {
                return child;
            }
        }
        return null;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
