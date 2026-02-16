package com.s1000Dorg.viewer.modules;

import java.io.InputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

@Service
public class XmlValidationService {

    public void validateWellFormed(InputStream xmlStream) {
        try {
            DocumentBuilderFactory factory = secureFactory();
            factory.newDocumentBuilder().parse(xmlStream);
        } catch (SAXException parseException) {
            throw new IllegalArgumentException("Uploaded XML is not well-formed", parseException);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to validate XML", ex);
        }
    }

    public void validateAgainstXsdHook(InputStream xmlStream) {
        // Extension point for future S1000D XSD validation.
        // Hook intentionally left as a stub for demo usage.
    }

    private DocumentBuilderFactory secureFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        setFeatureIfSupported(factory, "http://apache.org/xml/features/disallow-doctype-decl", false);
        setFeatureIfSupported(factory, "http://xml.org/sax/features/external-general-entities", false);
        setFeatureIfSupported(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        setFeatureIfSupported(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setExpandEntityReferences(false);
        factory.setNamespaceAware(false);
        return factory;
    }

    private void setFeatureIfSupported(DocumentBuilderFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (Exception ignored) {
            // Parser-specific optional feature.
        }
    }
}

