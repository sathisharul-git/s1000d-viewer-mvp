package com.s1000Dorg.viewer.applicability.xml;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.web.util.HtmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

public class ApplicabilityXmlParser {

    public ApplicXmlExpression parse(String escapedXml) throws ApplicabilityXmlParseException {
        if (escapedXml == null || escapedXml.isBlank()) {
            return null;
        }
        String xml = HtmlUtils.htmlUnescape(escapedXml);
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
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
            Document doc = builder.parse(new InputSource(new StringReader(sanitizeXml(xml))));
            Element root = doc.getDocumentElement();
            if (!"applic".equalsIgnoreCase(root.getTagName())) {
                throw new ApplicabilityXmlParseException("Root element must be <applic>");
            }
            Element expressionRoot = findExpressionRoot(root);
            if (expressionRoot == null) {
                return null;
            }
            if ("evaluate".equalsIgnoreCase(expressionRoot.getTagName())) {
                return parseEvaluate(expressionRoot);
            }
            return parseAssert(expressionRoot);

        } catch (Exception e) {
            throw new ApplicabilityXmlParseException("Failed to parse applicability XML", e);
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

    private ApplicXmlExpression.Evaluate parseEvaluate(Element evaluateElement) throws ApplicabilityXmlParseException {
        String andOr = evaluateElement.getAttribute("andOr");
        if (!"and".equalsIgnoreCase(andOr) && !"or".equalsIgnoreCase(andOr)) {
            throw new ApplicabilityXmlParseException("<evaluate> andOr attribute must be 'and' or 'or'");
        }

        List<ApplicXmlExpression> expressions = new ArrayList<>();
        NodeList children = evaluateElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) children.item(i);
                if ("assert".equalsIgnoreCase(childElement.getTagName())) {
                    expressions.add(parseAssert(childElement));
                } else if ("evaluate".equalsIgnoreCase(childElement.getTagName())) {
                    expressions.add(parseEvaluate(childElement));
                } else {
                    throw new ApplicabilityXmlParseException("Unsupported element inside <evaluate>: " + childElement.getTagName());
                }
            }
        }
        return new ApplicXmlExpression.Evaluate(andOr.toLowerCase(), expressions);
    }

    private ApplicXmlExpression.Assert parseAssert(Element assertElement) {
        String ident = assertElement.getAttribute("applicPropertyIdent");
        String values = assertElement.getAttribute("applicPropertyValues");
        return new ApplicXmlExpression.Assert(ident, values);
    }

    private Element findExpressionRoot(Element applicRoot) throws ApplicabilityXmlParseException {
        NodeList childNodes = applicRoot.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            if (childNodes.item(i).getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element candidate = (Element) childNodes.item(i);
            if ("displayText".equalsIgnoreCase(candidate.getTagName())) {
                continue;
            }
            if ("evaluate".equalsIgnoreCase(candidate.getTagName()) || "assert".equalsIgnoreCase(candidate.getTagName())) {
                return candidate;
            }
            throw new ApplicabilityXmlParseException("Unsupported root expression element: " + candidate.getTagName());
        }
        return null;
    }
}
