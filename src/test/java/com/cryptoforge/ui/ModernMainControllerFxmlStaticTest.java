package com.cryptoforge.ui;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModernMainControllerFxmlStaticTest {

    @Test
    void testMainViewModernFxml() throws Exception {
        verifyFxmlAgainstController("/fxml/main-view-modern.fxml", ModernMainController.class);
    }

    @Test
    void testPqcFxml() throws Exception {
        verifyFxmlAgainstController("/fxml/pqc.fxml", PostQuantumController.class);
    }

    @Test
    void testXmlSecurityFxml() throws Exception {
        verifyFxmlAgainstController("/fxml/xml_security.fxml", XMLSignatureController.class);
    }

    @Test
    void testAsn1Fxml() throws Exception {
        verifyFxmlAgainstController("/fxml/asn1.fxml", ASN1Controller.class);
    }

    @Test
    void testJoseFxml() throws Exception {
        verifyFxmlAgainstController("/fxml/jose.fxml", JOSEController.class);
    }

    @Test
    void testCompressedHexFxml() throws Exception {
        verifyFxmlAgainstController("/fxml/compressed_hex.fxml", CompressedHexController.class);
    }

    @Test
    void testGenericFxml() throws Exception {
        verifyFxmlAgainstController("/fxml/generic.fxml", GenericController.class);
    }

    @Test
    void testHistoryFxml() throws Exception {
        verifyFxmlAgainstController("/fxml/history.fxml", HistoryController.class);
    }

    @Test
    void testOpenPgpFxml() throws Exception {
        verifyFxmlAgainstController("/fxml/openpgp.fxml", OpenPgpController.class);
    }

    @Test
    void testPadesFxml() throws Exception {
        verifyFxmlAgainstController("/fxml/pades.fxml", PadesController.class);
    }

    @Test
    void testAsicFxml() throws Exception {
        verifyFxmlAgainstController("/fxml/asic.fxml", AsicController.class);
    }

    @Test
    void testCmsInspectorFxml() throws Exception {
        verifyFxmlAgainstController("/fxml/cms_inspector.fxml", CmsInspectorController.class);
    }

    @Test
    void testClipboardShelfFxml() throws Exception {
        verifyFxmlAgainstController("/fxml/clipboard_shelf.fxml", ClipboardShelfController.class);
    }

    @Test
    void testKeyCertificateWorkbenchFxml() throws Exception {
        verifyFxmlAgainstController("/fxml/key_certificate_workbench.fxml", KeyCertificateWorkbenchController.class);
    }

    private void verifyFxmlAgainstController(String fxmlPath, Class<?> controllerClass) throws Exception {
        InputStream is = getClass().getResourceAsStream(fxmlPath);
        assertNotNull(is, "FXML file must exist: " + fxmlPath);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(is);
        assertNotNull(doc, "Document should be parseable XML");

        Element root = doc.getDocumentElement();
        String expectedControllerName = root.getAttribute("fx:controller");
        assertFalse(expectedControllerName == null || expectedControllerName.isBlank(),
                "FXML module must declare its controller: " + fxmlPath);
        assertEquals(controllerClass.getName(), expectedControllerName, "Controller must match");

        String rootFxId = root.getAttribute("fx:id");
        List<String> fxIds = extractAttributes(root, "fx:id");
        for (String fxId : fxIds) {
            if (fxId.equals(rootFxId)) continue; // The root fx:id is for the parent inclusion, not the controller itself.
            try {
                Field f = controllerClass.getDeclaredField(fxId);
                assertNotNull(f);
            } catch (NoSuchFieldException e) {
                // If it's fx:id="postQuantumContainer" inside main-view-modern, ModernMainController has it.
                // Wait! main-view-modern uses fx:include. fx:include also has an fx:id.
                // It injects a VBox or parent AND the controller, e.g. postQuantumContainer and postQuantumContainerController.
                // So it should be present.
                fail("fx:id '" + fxId + "' is defined in FXML but missing in controller " + controllerClass.getSimpleName());
            }
        }

        List<String> handlers = extractAttributes(root, "onAction");
        for (String handler : handlers) {
            if (handler.startsWith("#")) {
                String methodName = handler.substring(1);
                boolean methodFound = false;
                for (Method m : controllerClass.getDeclaredMethods()) {
                    if (m.getName().equals(methodName)) {
                        methodFound = true;
                        break;
                    }
                }
                assertTrue(methodFound, "Handler method '" + methodName + "' missing in " + controllerClass.getSimpleName());
            }
        }
    }

    private List<String> extractAttributes(Element element, String attributeName) {
        List<String> values = new ArrayList<>();
        if (element.hasAttribute(attributeName)) {
            values.add(element.getAttribute(attributeName));
        }
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element) {
                values.addAll(extractAttributes((Element) children.item(i), attributeName));
            }
        }
        return values;
    }
}
