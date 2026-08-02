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
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless release smoke test for every production FXML resource.
 *
 * <p>This deliberately validates the XML, controller class and every
 * {@code onAction} target without starting JavaFX. Real FXMLLoader/stage
 * navigation remains in the opt-in UI gate.</p>
 */
@Tag("fxml-smoke")
class FxmlQualityGateTest {

    private static final List<String> FXML_FILES = List.of(
            "asic.fxml", "asn1.fxml", "authentication.fxml", "certificates.fxml",
            "cipher.fxml", "clipboard_shelf.fxml", "cms_inspector.fxml", "compare_results.fxml",
            "compressed_hex.fxml", "emv.fxml", "generic.fxml", "history.fxml", "jose.fxml",
            "key_certificate_workbench.fxml", "keys.fxml", "main-view-modern.fxml", "main-view.fxml",
            "openpgp.fxml", "pades.fxml", "payments.fxml", "pqc.fxml", "process_designer.fxml",
            "wss_security.fxml", "xml_security.fxml");

    @Test
    void everyProductionFxmlHasAResolvableControllerAndHandlers() throws Exception {
        assertFalse(FXML_FILES.isEmpty());
        List<String> missingHandlers = new ArrayList<>();
        for (String file : FXML_FILES) {
            String resource = "/fxml/" + file;
            try (InputStream input = getClass().getResourceAsStream(resource)) {
                assertNotNull(input, "Missing production FXML resource: " + resource);
                Document document = secureFactory().newDocumentBuilder().parse(input);
                Element root = document.getDocumentElement();
                String controllerName = root.getAttribute("fx:controller");
                assertFalse(controllerName == null || controllerName.isBlank(),
                        "Missing fx:controller in " + resource);

                Class<?> controller = Class.forName(controllerName);
                for (Element element : elements(root)) {
                    String handler = element.getAttribute("onAction");
                    if (handler == null || handler.isBlank() || !handler.startsWith("#")) continue;
                    String methodName = handler.substring(1);
                    if (!hasMethod(controller, methodName)) {
                        missingHandlers.add(resource + " -> #" + methodName + " on " + controllerName);
                    }
                }
            }
        }
        assertTrue(missingHandlers.isEmpty(), "FXML handlers missing: " + missingHandlers);
    }

    @Test
    void smokeListCoversAllProductionFxmlFiles() throws Exception {
        for (String file : FXML_FILES) {
            assertNotNull(getClass().getResourceAsStream("/fxml/" + file),
                    "Smoke list entry must remain a production resource: " + file);
        }
        assertTrue(FXML_FILES.size() >= 24, "Add new production FXML to this release gate");
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

    private static boolean hasMethod(Class<?> controller, String name) {
        for (Class<?> type = controller; type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.getName().equals(name)) return true;
            }
        }
        return false;
    }
}
