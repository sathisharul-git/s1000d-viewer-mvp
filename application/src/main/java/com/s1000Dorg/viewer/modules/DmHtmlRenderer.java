package com.s1000Dorg.viewer.modules;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

@Component
public class DmHtmlRenderer {

    private static final Pattern DM_REF_PATTERN = Pattern.compile("DMC-[A-Z0-9_-]+");

    public String render(String dmId, String xml) {
        try {
            DocumentBuilderFactory factory = secureFactory();
            Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            doc.getDocumentElement().normalize();

            ReferenceContext references = buildReferenceContext(doc);
            String title = extractTitle(doc);
            Element content = firstElementByTag(doc.getDocumentElement(), "content");

            StringBuilder html = new StringBuilder();
            html.append("<article class=\"dm-content\" data-dm-id=\"").append(escape(dmId)).append("\">");
            html.append("<h2>").append(escape(title)).append("</h2>");

            if (content == null) {
                renderFallbackParagraphs(doc, html);
            } else {
                renderChildBlocks(content, html, references, 3);
            }

            html.append("</article>");
            return html.toString();
        } catch (Exception ex) {
            return "<article class=\"dm-content\"><h2>" + escape(dmId)
                + "</h2><p>Unable to render XML to HTML.</p></article>";
        }
    }

    private void renderChildBlocks(Node parent, StringBuilder html, ReferenceContext refs, int headingLevel) {
        List<Element> children = childElements(parent);
        for (int i = 0; i < children.size(); i++) {
            Element child = children.get(i);
            String tag = localName(child);

            if ("proceduralStep".equals(tag)) {
                List<Element> stepGroup = new ArrayList<>();
                int cursor = i;
                while (cursor < children.size() && "proceduralStep".equals(localName(children.get(cursor)))) {
                    stepGroup.add(children.get(cursor));
                    cursor++;
                }
                renderProceduralSteps(stepGroup, html, refs, headingLevel);
                i = cursor - 1;
                continue;
            }

            switch (tag) {
                case "title" -> {
                    // Titles are handled by the parent container.
                }
                case "description", "applicCrossRefTable", "commonInfo" -> renderTitledSection(
                    sectionTitle(tag),
                    child,
                    html,
                    refs,
                    headingLevel
                );
                case "levelledPara" -> renderLevelledPara(child, html, refs, headingLevel);
                case "para", "simplePara" -> renderParagraphLike(child, html, refs, null);
                case "randomList", "sequentialList" -> renderList(child, html, refs);
                case "figure" -> renderFigure(child, html, refs);
                case "table" -> renderTable(child, html, refs);
                case "mainProcedure", "preliminaryRqmts", "closeRqmts" -> renderProcedureSection(
                    sectionTitle(tag),
                    child,
                    html,
                    refs,
                    headingLevel
                );
                case "proceduralStepAlts" -> renderStepAlternatives(child, html, refs, headingLevel);
                case "warning", "caution" -> renderAdmonition(child, html, refs, tag);
                case "warningAndCautionPara" -> renderWarningParagraph(child, html, refs, "warning");
                default -> {
                    if (!childElements(child).isEmpty()) {
                        renderTitledSection(sectionTitle(tag), child, html, refs, headingLevel);
                    } else {
                        String text = normalizeText(child.getTextContent());
                        if (!text.isBlank()) {
                            html.append("<p>").append(escape(text)).append("</p>");
                        }
                    }
                }
            }
        }
    }

    private void renderTitledSection(
        String title,
        Element container,
        StringBuilder html,
        ReferenceContext refs,
        int headingLevel
    ) {
        html.append("<section class=\"dm-section\">");
        if (title != null && !title.isBlank()) {
            html.append("<").append(headingTag(headingLevel)).append(">")
                .append(escape(title))
                .append("</").append(headingTag(headingLevel)).append(">");
        }
        renderChildBlocks(container, html, refs, Math.min(headingLevel + 1, 6));
        html.append("</section>");
    }

    private void renderLevelledPara(Element levelledPara, StringBuilder html, ReferenceContext refs, int headingLevel) {
        html.append("<section class=\"dm-section dm-levelled\">");
        String title = firstDirectChildText(levelledPara, "title");
        if (!title.isBlank()) {
            html.append("<").append(headingTag(headingLevel)).append(">")
                .append(escape(title))
                .append("</").append(headingTag(headingLevel)).append(">");
        }
        renderChildBlocks(levelledPara, html, refs, Math.min(headingLevel + 1, 6));
        html.append("</section>");
    }

    private void renderParagraphLike(Node paragraphNode, StringBuilder html, ReferenceContext refs, String cssClass) {
        StringBuilder inline = new StringBuilder();
        NodeList children = paragraphNode.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childEl = (Element) child;
                String tag = localName(childEl);
                if ("randomList".equals(tag) || "sequentialList".equals(tag)) {
                    flushParagraphBuffer(inline, html, cssClass);
                    renderList(childEl, html, refs);
                    continue;
                }
            }
            inline.append(renderInlineNode(child, refs));
        }

        flushParagraphBuffer(inline, html, cssClass);
    }

    private void flushParagraphBuffer(StringBuilder inline, StringBuilder html, String cssClass) {
        String text = compactInline(inline.toString());
        inline.setLength(0);
        if (text.isBlank()) {
            return;
        }
        if (cssClass == null || cssClass.isBlank()) {
            html.append("<p>").append(text).append("</p>");
        } else {
            html.append("<p class=\"").append(escape(cssClass)).append("\">").append(text).append("</p>");
        }
    }

    private void renderList(Element list, StringBuilder html, ReferenceContext refs) {
        String tag = "sequentialList".equals(localName(list)) ? "ol" : "ul";
        html.append("<").append(tag).append(" class=\"dm-inline-list\">");
        for (Element item : directChildrenByTag(list, "listItem")) {
            html.append("<li>");
            boolean hadParagraph = false;
            for (Element itemChild : childElements(item)) {
                String childTag = localName(itemChild);
                if ("para".equals(childTag) || "simplePara".equals(childTag)) {
                    String text = compactInline(renderInlineChildren(itemChild, refs));
                    if (!text.isBlank()) {
                        html.append(text);
                        hadParagraph = true;
                    }
                } else if ("randomList".equals(childTag) || "sequentialList".equals(childTag)) {
                    renderList(itemChild, html, refs);
                    hadParagraph = true;
                }
            }
            if (!hadParagraph) {
                String text = normalizeText(item.getTextContent());
                if (!text.isBlank()) {
                    html.append(escape(text));
                }
            }
            html.append("</li>");
        }
        html.append("</").append(tag).append(">");
    }

    private void renderProcedureSection(
        String title,
        Element section,
        StringBuilder html,
        ReferenceContext refs,
        int headingLevel
    ) {
        html.append("<section class=\"dm-section dm-procedure\">");
        html.append("<").append(headingTag(headingLevel)).append(">")
            .append(escape(title))
            .append("</").append(headingTag(headingLevel)).append(">");
        renderChildBlocks(section, html, refs, Math.min(headingLevel + 1, 6));
        html.append("</section>");
    }

    private void renderProceduralSteps(List<Element> steps, StringBuilder html, ReferenceContext refs, int headingLevel) {
        html.append("<ol class=\"dm-steps\">");
        for (Element step : steps) {
            renderProceduralStep(step, html, refs, headingLevel);
        }
        html.append("</ol>");
    }

    private void renderProceduralStep(Element step, StringBuilder html, ReferenceContext refs, int headingLevel) {
        html.append("<li class=\"dm-step\">");
        List<Element> children = childElements(step);

        for (Element child : children) {
            String tag = localName(child);
            switch (tag) {
                case "para", "simplePara" -> renderParagraphLike(child, html, refs, null);
                case "warning", "caution" -> renderAdmonition(child, html, refs, tag);
                case "randomList", "sequentialList" -> renderList(child, html, refs);
                case "proceduralStepAlts" -> renderStepAlternatives(child, html, refs, headingLevel + 1);
                case "proceduralStep" -> renderProceduralSteps(List.of(child), html, refs, headingLevel + 1);
                case "figure" -> renderFigure(child, html, refs);
                case "table" -> renderTable(child, html, refs);
                default -> {
                    if (!childElements(child).isEmpty()) {
                        renderChildBlocks(child, html, refs, Math.min(headingLevel + 1, 6));
                    } else {
                        String text = normalizeText(child.getTextContent());
                        if (!text.isBlank()) {
                            html.append("<p>").append(escape(text)).append("</p>");
                        }
                    }
                }
            }
        }
        html.append("</li>");
    }

    private void renderStepAlternatives(Element alts, StringBuilder html, ReferenceContext refs, int headingLevel) {
        html.append("<section class=\"dm-section dm-procedure-alts\">");
        html.append("<").append(headingTag(Math.min(headingLevel, 6))).append(">Alternative steps</")
            .append(headingTag(Math.min(headingLevel, 6))).append(">");

        List<Element> alternatives = directChildrenByTag(alts, "proceduralStep");
        if (alternatives.isEmpty()) {
            renderChildBlocks(alts, html, refs, Math.min(headingLevel + 1, 6));
        } else {
            html.append("<ol class=\"dm-steps\">");
            for (Element alternative : alternatives) {
                renderProceduralStep(alternative, html, refs, headingLevel + 1);
            }
            html.append("</ol>");
        }
        html.append("</section>");
    }

    private void renderAdmonition(Element admonition, StringBuilder html, ReferenceContext refs, String type) {
        html.append("<div class=\"dm-admonition ").append(escape(type)).append("\">");
        for (Element child : childElements(admonition)) {
            String tag = localName(child);
            if ("warningAndCautionPara".equals(tag) || "para".equals(tag) || "simplePara".equals(tag)) {
                renderParagraphLike(child, html, refs, null);
            }
        }
        String text = normalizeText(admonition.getTextContent());
        if (childElements(admonition).isEmpty() && !text.isBlank()) {
            html.append("<p>").append(escape(text)).append("</p>");
        }
        html.append("</div>");
    }

    private void renderWarningParagraph(Element warningPara, StringBuilder html, ReferenceContext refs, String type) {
        html.append("<div class=\"dm-admonition ").append(escape(type)).append("\">");
        renderParagraphLike(warningPara, html, refs, null);
        html.append("</div>");
    }

    private void renderFigure(Element figure, StringBuilder html, ReferenceContext refs) {
        String figureId = safeTrim(figure.getAttribute("id"));
        String title = firstDirectChildText(figure, "title");
        Element graphic = firstElementByTag(figure, "graphic");
        String icnId = extractIcnId(graphic);
        int hotspotCount = graphic == null ? 0 : graphic.getElementsByTagName("hotspot").getLength();

        html.append("<figure class=\"dm-figure\"");
        if (!figureId.isBlank()) {
            html.append(" id=\"").append(escape(figureId)).append("\"");
        }
        html.append(">");

        if (!title.isBlank()) {
            html.append("<figcaption>").append(escape(title)).append("</figcaption>");
        }

        if (!icnId.isBlank()) {
            html.append("<button type=\"button\" class=\"dm-link-btn dm-graphic-link dm-graphic-trigger\" data-icn-id=\"")
                .append(escape(icnId)).append("\">Open image");
            if (!title.isBlank()) {
                html.append(": ").append(escape(title));
            }
            html.append("</button>");
            html.append("<p><code>").append(escape(icnId)).append("</code></p>");
        } else {
            html.append("<p>Graphic reference found but no ICN ID was extracted.</p>");
        }

        if (hotspotCount > 0) {
            html.append("<p>").append(hotspotCount).append(" hotspot(s) available for this image.</p>");
        }

        for (Element child : childElements(figure)) {
            String tag = localName(child);
            if ("title".equals(tag) || "graphic".equals(tag)) {
                continue;
            }
            if ("para".equals(tag) || "simplePara".equals(tag)) {
                renderParagraphLike(child, html, refs, null);
            }
        }

        html.append("</figure>");
    }

    private void renderTable(Element table, StringBuilder html, ReferenceContext refs) {
        String title = firstDirectChildText(table, "title");
        html.append("<section class=\"dm-section dm-table-wrap\">");
        if (!title.isBlank()) {
            html.append("<h4>").append(escape(title)).append("</h4>");
        }
        html.append("<table class=\"dm-table\">");

        Element tgroup = firstElementByTag(table, "tgroup");
        Element thead = tgroup == null ? null : firstElementByTag(tgroup, "thead");
        Element tbody = tgroup == null ? null : firstElementByTag(tgroup, "tbody");

        if (thead != null) {
            html.append("<thead>");
            renderTableRows(thead, html, refs, true);
            html.append("</thead>");
        }
        if (tbody != null) {
            html.append("<tbody>");
            renderTableRows(tbody, html, refs, false);
            html.append("</tbody>");
        } else {
            html.append("<tbody>");
            renderTableRows(table, html, refs, false);
            html.append("</tbody>");
        }

        html.append("</table>");
        html.append("</section>");
    }

    private void renderTableRows(Element container, StringBuilder html, ReferenceContext refs, boolean header) {
        for (Element row : directChildrenByTag(container, "row")) {
            html.append("<tr>");
            for (Element entry : directChildrenByTag(row, "entry")) {
                String cellTag = header ? "th" : "td";
                html.append("<").append(cellTag).append(">");
                List<Element> blocks = childElements(entry);
                if (blocks.isEmpty()) {
                    String text = compactInline(renderInlineChildren(entry, refs));
                    if (!text.isBlank()) {
                        html.append(text);
                    }
                } else {
                    for (Element block : blocks) {
                        String tag = localName(block);
                        if ("para".equals(tag) || "simplePara".equals(tag)) {
                            renderParagraphLike(block, html, refs, null);
                        } else if ("randomList".equals(tag) || "sequentialList".equals(tag)) {
                            renderList(block, html, refs);
                        } else {
                            String text = normalizeText(block.getTextContent());
                            if (!text.isBlank()) {
                                html.append("<p>").append(escape(text)).append("</p>");
                            }
                        }
                    }
                }
                html.append("</").append(cellTag).append(">");
            }
            html.append("</tr>");
        }
    }

    private String renderInlineChildren(Element element, ReferenceContext refs) {
        StringBuilder inline = new StringBuilder();
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            inline.append(renderInlineNode(children.item(i), refs));
        }
        return inline.toString();
    }

    private String renderInlineNode(Node node, ReferenceContext refs) {
        if (node == null) {
            return "";
        }
        if (node.getNodeType() == Node.TEXT_NODE) {
            return escape(normalizeText(node.getTextContent()));
        }
        if (node.getNodeType() != Node.ELEMENT_NODE) {
            return "";
        }

        Element element = (Element) node;
        String tag = localName(element);
        return switch (tag) {
            case "emphasis" -> "<em>" + compactInline(renderInlineChildren(element, refs)) + "</em>";
            case "subScript", "subscript" -> "<sub>" + compactInline(renderInlineChildren(element, refs)) + "</sub>";
            case "superScript", "superscript" -> "<sup>" + compactInline(renderInlineChildren(element, refs)) + "</sup>";
            case "internalRef" -> renderInternalRef(element, refs);
            case "dmRef" -> renderDmRef(element);
            case "graphic" -> renderInlineGraphicRef(element);
            default -> compactInline(renderInlineChildren(element, refs));
        };
    }

    private String renderInternalRef(Element internalRef, ReferenceContext refs) {
        String href = firstNonBlank(
            safeTrim(internalRef.getAttribute("xlink:href")),
            safeTrim(internalRef.getAttribute("href")),
            safeTrim(internalRef.getAttribute("internalRefId"))
        );
        String targetId = stripHash(href);
        GraphicTarget target = refs.byTargetId().get(targetId);

        String icnId = target != null ? target.icnId() : extractIcnFromHref(href);
        String label = target != null ? target.label() : firstNonBlank(targetId, "View reference");

        if (!icnId.isBlank()) {
            return "<button type=\"button\" class=\"dm-inline-link dm-graphic-link\" data-icn-id=\""
                + escape(icnId) + "\">" + escape(label) + "</button>";
        }

        return "<span class=\"dm-inline-link\">" + escape(label) + "</span>";
    }

    private String renderDmRef(Element dmRef) {
        String href = firstNonBlank(safeTrim(dmRef.getAttribute("xlink:href")), safeTrim(dmRef.getAttribute("href")));
        String dmId = extractDmId(href);
        if (dmId.isBlank()) {
            return "<span class=\"dm-inline-link\">Data module reference</span>";
        }
        return "<button type=\"button\" class=\"dm-inline-link dm-dm-link\" data-dm-id=\""
            + escape(dmId) + "\">" + escape(dmId) + "</button>";
    }

    private String renderInlineGraphicRef(Element graphic) {
        String icnId = extractIcnId(graphic);
        if (icnId.isBlank()) {
            return "<span class=\"dm-inline-link\">Graphic</span>";
        }
        return "<button type=\"button\" class=\"dm-inline-link dm-graphic-link\" data-icn-id=\""
            + escape(icnId) + "\">Open image</button>";
    }

    private String extractTitle(Document doc) {
        String techName = firstTagText(doc, "techName");
        String infoName = firstTagText(doc, "infoName");
        if (!techName.isBlank() && !infoName.isBlank()) {
            return techName + " - " + infoName;
        }
        if (!techName.isBlank()) {
            return techName;
        }
        if (!infoName.isBlank()) {
            return infoName;
        }
        return "Untitled Data Module";
    }

    private void renderFallbackParagraphs(Document doc, StringBuilder html) {
        NodeList paras = doc.getElementsByTagName("para");
        if (paras.getLength() == 0) {
            html.append("<p>No content section found in this data module.</p>");
            return;
        }
        for (int i = 0; i < paras.getLength(); i++) {
            Element para = (Element) paras.item(i);
            String text = normalizeText(para.getTextContent());
            if (!text.isBlank()) {
                html.append("<p>").append(escape(text)).append("</p>");
            }
        }
    }

    private ReferenceContext buildReferenceContext(Document doc) {
        Map<String, GraphicTarget> byTargetId = new LinkedHashMap<>();
        NodeList figures = doc.getElementsByTagName("figure");
        for (int i = 0; i < figures.getLength(); i++) {
            Element figure = (Element) figures.item(i);
            String figureId = safeTrim(figure.getAttribute("id"));
            String figureTitle = firstDirectChildText(figure, "title");
            Element graphic = firstElementByTag(figure, "graphic");
            String icnId = extractIcnId(graphic);
            if (icnId.isBlank()) {
                continue;
            }

            String label = firstNonBlank(figureTitle, figureId, "View figure");
            if (!figureId.isBlank()) {
                byTargetId.putIfAbsent(figureId, new GraphicTarget(icnId, label));
            }

            if (graphic != null) {
                String graphicId = safeTrim(graphic.getAttribute("id"));
                if (!graphicId.isBlank()) {
                    byTargetId.putIfAbsent(graphicId, new GraphicTarget(icnId, label));
                }
                NodeList hotspots = graphic.getElementsByTagName("hotspot");
                for (int hs = 0; hs < hotspots.getLength(); hs++) {
                    Element hotspot = (Element) hotspots.item(hs);
                    String hotspotId = safeTrim(hotspot.getAttribute("id"));
                    if (hotspotId.isBlank()) {
                        continue;
                    }
                    String hotspotLabel = firstNonBlank(
                        safeTrim(hotspot.getAttribute("hotspotTitle")),
                        safeTrim(hotspot.getAttribute("objectDescr")),
                        label
                    );
                    byTargetId.putIfAbsent(hotspotId, new GraphicTarget(icnId, hotspotLabel));
                }
            }
        }
        return new ReferenceContext(byTargetId);
    }

    private String extractIcnId(Element graphic) {
        if (graphic == null) {
            return "";
        }
        String infoEntityIdent = safeTrim(graphic.getAttribute("infoEntityIdent"));
        if (!infoEntityIdent.isBlank()) {
            return infoEntityIdent;
        }
        String href = firstNonBlank(safeTrim(graphic.getAttribute("xlink:href")), safeTrim(graphic.getAttribute("href")));
        return extractIcnFromHref(href);
    }

    private String extractIcnFromHref(String href) {
        if (href == null || href.isBlank()) {
            return "";
        }
        String raw = href.trim();
        String upper = raw.toUpperCase(Locale.ROOT);
        int idx = upper.indexOf("ICN-");
        if (idx < 0) {
            return "";
        }
        return raw.substring(idx).replace("&", "").replace(";", "");
    }

    private String extractDmId(String href) {
        if (href == null || href.isBlank()) {
            return "";
        }
        Matcher matcher = DM_REF_PATTERN.matcher(href.toUpperCase(Locale.ROOT));
        if (matcher.find()) {
            return matcher.group();
        }
        return "";
    }

    private String sectionTitle(String tag) {
        return switch (tag) {
            case "description" -> "Description";
            case "mainProcedure" -> "Main procedure";
            case "preliminaryRqmts" -> "Preliminary requirements";
            case "closeRqmts" -> "Close requirements";
            case "applicCrossRefTable" -> "Applicability cross-reference table";
            default -> titleCase(tag);
        };
    }

    private String headingTag(int level) {
        int normalized = Math.max(2, Math.min(level, 6));
        return "h" + normalized;
    }

    private String titleCase(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String[] chunks = value.replace('_', ' ').split("(?=[A-Z])|\\s+");
        StringBuilder out = new StringBuilder();
        for (String chunk : chunks) {
            if (chunk == null || chunk.isBlank()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(chunk.charAt(0)));
            if (chunk.length() > 1) {
                out.append(chunk.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return out.toString();
    }

    private List<Element> childElements(Node parent) {
        List<Element> elements = new ArrayList<>();
        if (parent == null) {
            return elements;
        }
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                elements.add((Element) node);
            }
        }
        return elements;
    }

    private List<Element> directChildrenByTag(Element parent, String tag) {
        List<Element> matches = new ArrayList<>();
        for (Element child : childElements(parent)) {
            if (tag.equals(localName(child))) {
                matches.add(child);
            }
        }
        return matches;
    }

    private Element firstElementByTag(Element parent, String tag) {
        if (parent == null) {
            return null;
        }
        NodeList list = parent.getElementsByTagName(tag);
        if (list.getLength() == 0) {
            return null;
        }
        return (Element) list.item(0);
    }

    private String firstDirectChildText(Element parent, String tag) {
        for (Element child : childElements(parent)) {
            if (tag.equals(localName(child))) {
                return normalizeText(child.getTextContent());
            }
        }
        return "";
    }

    private String firstTagText(Document doc, String tagName) {
        NodeList list = doc.getElementsByTagName(tagName);
        if (list.getLength() == 0) {
            return "";
        }
        return normalizeText(list.item(0).getTextContent());
    }

    private String compactInline(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return html
            .replaceAll("\\s{2,}", " ")
            .replaceAll("\\s+</", "</")
            .trim();
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace('\t', ' ')
            .replaceAll("\\s{2,}", " ")
            .trim();
    }

    private String localName(Element element) {
        return element.getTagName();
    }

    private String stripHash(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("#")) {
            return trimmed.substring(1);
        }
        return trimmed;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private DocumentBuilderFactory secureFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        setFeatureIfSupported(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        setFeatureIfSupported(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        setFeatureIfSupported(factory, "http://xml.org/sax/features/external-general-entities", false);
        setFeatureIfSupported(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        setFeatureIfSupported(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setNamespaceAware(false);
        return factory;
    }

    private void setFeatureIfSupported(DocumentBuilderFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (Exception ignored) {
            // Optional parser hardening feature.
        }
    }

    private String escape(String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    private record GraphicTarget(String icnId, String label) {
    }

    private record ReferenceContext(Map<String, GraphicTarget> byTargetId) {
    }
}

