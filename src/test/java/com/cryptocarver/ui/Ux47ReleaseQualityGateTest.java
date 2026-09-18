package com.cryptocarver.ui;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static release gates for the cross-cutting UX work in UXP-47. */
@Tag("fxml-smoke")
class Ux47ReleaseQualityGateTest {

    private static final List<String> FXML_FILES = List.of(
            "asic.fxml", "asn1.fxml", "authentication.fxml", "certificates.fxml",
            "cipher.fxml", "clipboard_shelf.fxml", "cms_inspector.fxml", "compare_results.fxml",
            "compressed_hex.fxml", "cose.fxml", "emv.fxml", "generic.fxml", "history.fxml",
            "icsf_batch.fxml", "icsf_token.fxml", "jose.fxml", "key_certificate_workbench.fxml",
            "keys.fxml", "main-view-modern.fxml", "main-view.fxml", "openpgp.fxml", "pades.fxml",
            "payments.fxml", "pqc.fxml", "process_designer.fxml", "wss_security.fxml",
            "xml_security.fxml");
    private static final Pattern PLACEHOLDER = Pattern.compile("%(?:\\d+\\$)?[a-zA-Z]|\\{\\d+}");

    @Test
    void productionFxmlHasNoInlineStyles() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (String file : FXML_FILES) {
            Document document = parse(file);
            for (Element element : elements(document.getDocumentElement())) {
                if (element.hasAttribute("style")) {
                    offenders.add(file + " <" + element.getTagName() + ">");
                }
            }
        }
        assertTrue(offenders.isEmpty(), "Inline FXML styles are forbidden: " + offenders);
    }

    @Test
    void iconOnlyButtonsHaveAnAccessibleName() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (String file : FXML_FILES) {
            for (Element element : elements(parse(file).getDocumentElement())) {
                if (!"Button".equals(element.getTagName()) || !isIconOnly(element)) continue;
                String accessibleText = element.getAttribute("accessibleText").trim();
                if (accessibleText.isEmpty()) {
                    offenders.add(file + " Button#" + element.getAttribute("fx:id")
                            + " text=" + element.getAttribute("text"));
                }
            }
        }
        assertTrue(offenders.isEmpty(), "Icon-only buttons need accessibleText: " + offenders);
    }

    @Test
    void acceleratorsAreUniqueWithinEachFxmlDocument() throws Exception {
        List<String> duplicates = new ArrayList<>();
        for (String file : FXML_FILES) {
            Map<String, String> seen = new HashMap<>();
            for (Element element : elements(parse(file).getDocumentElement())) {
                if (!element.hasAttribute("accelerator")) continue;
                String accelerator = element.getAttribute("accelerator").trim();
                if (accelerator.isEmpty()) continue;
                String previous = seen.putIfAbsent(accelerator, element.getAttribute("fx:id"));
                if (previous != null) {
                    duplicates.add(file + " " + accelerator + " (" + previous + ", "
                            + element.getAttribute("fx:id") + ")");
                }
            }
        }
        assertTrue(duplicates.isEmpty(), "Duplicate menu accelerators: " + duplicates);
    }

    @Test
    void spanishBundleMatchesBaseAndPreservesPlaceholders() throws Exception {
        Properties base = load("/i18n/messages.properties");
        Properties spanish = load("/i18n/messages_es.properties");
        Set<String> missing = new HashSet<>();
        Set<String> extra = new HashSet<>();
        for (String key : base.stringPropertyNames()) {
            if (!spanish.containsKey(key)) missing.add(key);
            else {
                assertFalse(spanish.getProperty(key).isBlank(), "Blank Spanish translation: " + key);
                assertEquals(placeholders(base.getProperty(key)), placeholders(spanish.getProperty(key)),
                        "Placeholder mismatch for " + key);
            }
        }
        for (String key : spanish.stringPropertyNames()) {
            if (!base.containsKey(key)) extra.add(key);
        }
        assertTrue(missing.isEmpty(), "Spanish bundle keys missing from base: " + missing);
        assertTrue(extra.isEmpty(), "Spanish bundle has unknown keys: " + extra);
    }

    private static boolean isIconOnly(Element button) {
        String text = button.getAttribute("text").trim();
        if (text.isEmpty() || button.hasAttribute("graphic")) return true;
        return text.codePoints().allMatch(codePoint -> !Character.isLetterOrDigit(codePoint));
    }

    private static Document parse(String file) throws Exception {
        try (InputStream input = Ux47ReleaseQualityGateTest.class
                .getResourceAsStream("/fxml/" + file)) {
            assertTrue(input != null, "Missing production FXML: " + file);
            return secureFactory().newDocumentBuilder().parse(input);
        }
    }

    private static Properties load(String resource) throws Exception {
        Properties properties = new Properties();
        try (InputStream input = Ux47ReleaseQualityGateTest.class.getResourceAsStream(resource)) {
            assertTrue(input != null, "Missing i18n bundle: " + resource);
            properties.load(input);
        }
        return properties;
    }

    private static Set<String> placeholders(String text) {
        Set<String> result = new HashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(text == null ? "" : text);
        while (matcher.find()) result.add(matcher.group());
        return result;
    }

    private static DocumentBuilderFactory secureFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory;
    }

    private static List<Element> elements(Element root) {
        List<Element> result = new ArrayList<>();
        collect(root, result);
        return result;
    }

    private static void collect(Element element, List<Element> result) {
        result.add(element);
        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element childElement) collect(childElement, result);
        }
    }
}
