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

public class ApplicabilityXmlParser {

    public ApplicXmlExpression parse(String escapedXml) throws ApplicabilityXmlParseException {
        if (escapedXml == null || escapedXml.isBlank()) {
            return null;
        }
        String xml = HtmlUtils.htmlUnescape(escapedXml);
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));
            Element root = doc.getDocumentElement();
            if (!"applic".equalsIgnoreCase(root.getTagName())) {
                throw new ApplicabilityXmlParseException("Root element must be <applic>");
            }
            // The <applic> element should contain a single <evaluate> or <assert> element.
            // But the user's example shows it containing <evaluate>. I'll assume it contains one child that is the start of the expression.
            NodeList childNodes = root.getChildNodes();
            Element expressionRoot = null;
            for (int i = 0; i < childNodes.getLength(); i++) {
                if (childNodes.item(i).getNodeType() == Node.ELEMENT_NODE) {
                    expressionRoot = (Element) childNodes.item(i);
                    break;
                }
            }

            if (expressionRoot == null) {
                // This could be a valid case for an empty applicability, treat as no expression
                return null;
            }

            if ("evaluate".equalsIgnoreCase(expressionRoot.getTagName())) {
                return parseEvaluate(expressionRoot);
            } else if ("assert".equalsIgnoreCase(expressionRoot.getTagName())) {
                return parseAssert(expressionRoot);
            } else {
                throw new ApplicabilityXmlParseException("Unsupported root expression element: " + expressionRoot.getTagName());
            }

        } catch (Exception e) {
            throw new ApplicabilityXmlParseException("Failed to parse applicability XML", e);
        }
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
}
