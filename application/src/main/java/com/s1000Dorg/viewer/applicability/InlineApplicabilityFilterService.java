package com.s1000Dorg.viewer.applicability;

import com.s1000Dorg.viewer.domain.ApplicabilityResult;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
public class InlineApplicabilityFilterService {

    private final DmApplicabilityEvaluator dmApplicabilityEvaluator;

    public InlineApplicabilityFilterService(DmApplicabilityEvaluator dmApplicabilityEvaluator) {
        this.dmApplicabilityEvaluator = dmApplicabilityEvaluator;
    }

    public InlineApplicabilityFilterResult apply(String xml, ApplicabilityContext context) {
        if (xml == null || xml.isBlank()) {
            return InlineApplicabilityFilterResult.none(xml == null ? "" : xml);
        }
        String normalizedXml = sanitizeXml(xml);

        Document document = parseXml(normalizedXml);
        Element root = document.getDocumentElement();
        if (root == null) {
            return InlineApplicabilityFilterResult.none(normalizedXml);
        }

        Map<String, String> definitions = dmApplicabilityEvaluator.extractInlineApplicabilityDefinitions(normalizedXml);
        if (definitions.isEmpty()) {
            return InlineApplicabilityFilterResult.none(normalizedXml);
        }

        Map<String, String> aliases = extractApplicRefAliases(document);
        List<Element> targets = collectInlineTargets(document);
        if (targets.isEmpty()) {
            return InlineApplicabilityFilterResult.none(normalizedXml);
        }

        int removed = 0;
        int kept = 0;
        boolean evaluatedAny = false;

        targets.sort(Comparator.comparingInt(this::depth).reversed());
        for (Element element : targets) {
            if (!isConnectedToDocument(root, element)) {
                continue;
            }
            String refId = extractApplicRefId(element);
            if (refId.isBlank()) {
                continue;
            }

            String lookup = refId;
            if (!definitions.containsKey(lookup) && aliases.containsKey(lookup)) {
                lookup = aliases.get(lookup);
            }
            String applicXml = definitions.get(lookup);
            if (applicXml == null || applicXml.isBlank()) {
                kept++;
                continue;
            }

            evaluatedAny = true;
            ApplicabilityResult result = dmApplicabilityEvaluator.evaluateInlineApplicability(applicXml, context);
            if (result == ApplicabilityResult.NOT_APPLICABLE) {
                Node parent = element.getParentNode();
                if (parent != null) {
                    parent.removeChild(element);
                    removed++;
                }
            } else {
                kept++;
            }
        }

        if (!evaluatedAny) {
            return InlineApplicabilityFilterResult.none(normalizedXml);
        }
        return new InlineApplicabilityFilterResult(
            toXml(document),
            "FILTERED",
            removed,
            kept
        );
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
            throw new IllegalStateException("Failed to parse XML for inline applicability filtering", ex);
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

    private String toXml(Document document) {
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            java.io.StringWriter writer = new java.io.StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return writer.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize filtered XML", ex);
        }
    }

    private List<Element> collectInlineTargets(Document document) {
        NodeList nodes = document.getElementsByTagName("*");
        List<Element> targets = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (!(nodes.item(i) instanceof Element element)) {
                continue;
            }
            if (extractApplicRefId(element).isBlank()) {
                continue;
            }
            String tag = element.getTagName();
            if ("applic".equalsIgnoreCase(tag) || "applicRef".equalsIgnoreCase(tag)) {
                continue;
            }
            targets.add(element);
        }
        return targets;
    }

    private String extractApplicRefId(Element element) {
        String[] names = {"applicRefId", "applicrefid", "applicRef", "applicref"};
        for (String name : names) {
            String value = safeTrim(element.getAttribute(name));
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private Map<String, String> extractApplicRefAliases(Document document) {
        Map<String, String> aliases = new LinkedHashMap<>();
        NodeList refs = document.getElementsByTagName("applicRef");
        for (int i = 0; i < refs.getLength(); i++) {
            if (!(refs.item(i) instanceof Element ref)) {
                continue;
            }
            String id = safeTrim(ref.getAttribute("id"));
            String ident = safeTrim(ref.getAttribute("applicIdentValue"));
            if (!id.isBlank() && !ident.isBlank()) {
                aliases.put(id, ident);
            }
        }
        return aliases;
    }

    private int depth(Node node) {
        int depth = 0;
        Node cursor = node;
        while (cursor != null) {
            depth++;
            cursor = cursor.getParentNode();
        }
        return depth;
    }

    private boolean isConnectedToDocument(Element root, Node node) {
        Node cursor = node;
        while (cursor != null) {
            if (cursor == root) {
                return true;
            }
            cursor = cursor.getParentNode();
        }
        return false;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
