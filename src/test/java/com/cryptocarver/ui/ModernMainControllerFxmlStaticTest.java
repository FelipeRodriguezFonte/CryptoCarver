package com.cryptocarver.ui;

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
    void testAuthenticationFxml() throws Exception {
        verifyFxmlAgainstController("/fxml/authentication.fxml", AuthenticationController.class);
    }

    @Test
    void testCipherFxml() throws Exception {
        verifyFxmlAgainstController("/fxml/cipher.fxml", CipherController.class);
    }

    @Test
    void testPaymentsFxml() throws Exception {
        verifyFxmlAgainstController("/fxml/payments.fxml", PaymentsController.class);
    }

    @Test
    void testEmvFxml() throws Exception {
        verifyFxmlAgainstController("/fxml/emv.fxml", EMVController.class);
    }

    @Test
    void testKeysFxml() throws Exception {
        verifyFxmlAgainstController("/fxml/keys.fxml", KeysController.class);
    }

    @Test
    void testCertificatesFxml() throws Exception {
        verifyFxmlAgainstController("/fxml/certificates.fxml", CertificatesController.class);
    }

    @Test
    void testKeyCertificateWorkbenchFxml() throws Exception {
        verifyFxmlAgainstController("/fxml/key_certificate_workbench.fxml", KeyCertificateWorkbenchController.class);
    }

    @Test
    void testResponsiveActionBarsInFxml() throws Exception {
        String[] fxmlFiles = {
            "/fxml/keys.fxml",
            "/fxml/certificates.fxml",
            "/fxml/cipher.fxml",
            "/fxml/openpgp.fxml",
            "/fxml/pqc.fxml",
            "/fxml/pades.fxml",
            "/fxml/clipboard_shelf.fxml",
            "/fxml/cms_inspector.fxml",
            "/fxml/process_designer.fxml",
            "/fxml/compressed_hex.fxml",
            "/fxml/history.fxml"
        };

        for (String fxmlPath : fxmlFiles) {
            InputStream is = getClass().getResourceAsStream(fxmlPath);
            assertNotNull(is, "FXML file must exist: " + fxmlPath);

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(is);
            Element root = doc.getDocumentElement();

            List<Element> flowPanes = findElementsByTagName(root, "FlowPane");
            assertFalse(flowPanes.isEmpty(), fxmlPath + " should contain at least one FlowPane for responsive action bars");

            boolean foundResponsiveActionBar = false;
            for (Element fp : flowPanes) {
                String styleClass = fp.getAttribute("styleClass");
                if (styleClass != null && styleClass.contains("responsive-action-bar")) {
                    foundResponsiveActionBar = true;
                    break;
                }
            }
            assertTrue(foundResponsiveActionBar, fxmlPath + " should use responsive-action-bar styleClass on FlowPane");
        }
    }

    private List<Element> findElementsByTagName(Element element, String tagName) {
        List<Element> elements = new ArrayList<>();
        if (element.getTagName().equals(tagName)) {
            elements.add(element);
        }
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element) {
                elements.addAll(findElementsByTagName((Element) children.item(i), tagName));
            }
        }
        return elements;
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

    @Test
    void testActionBarsWithMoreThanThreeButtonsMustWrap() throws Exception {
        String[] fxmlFiles = {
            "/fxml/keys.fxml",
            "/fxml/cipher.fxml",
            "/fxml/authentication.fxml",
            "/fxml/certificates.fxml",
            "/fxml/generic.fxml",
            "/fxml/payments.fxml",
            "/fxml/emv.fxml",
            "/fxml/jose.fxml",
            "/fxml/pqc.fxml"
        };

        for (String fxmlPath : fxmlFiles) {
            InputStream is = getClass().getResourceAsStream(fxmlPath);
            assertNotNull(is, "FXML file must exist: " + fxmlPath);

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(is);
            Element root = doc.getDocumentElement();

            List<Element> hBoxes = findElementsByTagName(root, "HBox");
            for (Element hbox : hBoxes) {
                List<Element> buttons = findElementsByTagName(hbox, "Button");
                List<Element> menuButtons = findElementsByTagName(hbox, "MenuButton");
                int actionCount = buttons.size() + menuButtons.size();
                if (actionCount >= 4) {
                    fail(fxmlPath + " contains an HBox with " + actionCount + " action buttons. Action bars with 4+ buttons MUST use FlowPane with responsive-action-bar styleClass.");
                }
            }
        }
    }

    @Test
    void testResultSummaryBarStructureAndHandlers() throws Exception {
        InputStream is = getClass().getResourceAsStream("/fxml/main-view-modern.fxml");
        assertNotNull(is);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(is);
        Element root = doc.getDocumentElement();

        List<Element> hBoxes = findElementsByTagName(root, "HBox");
        Element resultSummaryBar = null;
        for (Element hbox : hBoxes) {
            if ("resultSummaryBar".equals(hbox.getAttribute("fx:id"))) {
                resultSummaryBar = hbox;
                break;
            }
        }
        assertNotNull(resultSummaryBar, "resultSummaryBar element missing in main-view-modern.fxml");

        List<Element> buttons = findElementsByTagName(resultSummaryBar, "Button");
        assertEquals(3, buttons.size(), "resultSummaryBar must contain 3 action buttons");

        List<String> actionHandlers = extractAttributes(resultSummaryBar, "onAction");
        assertTrue(actionHandlers.contains("#handleOpenExpandedResultViewer"), "Missing #handleOpenExpandedResultViewer");
        assertTrue(actionHandlers.contains("#handleAddCurrentOutputToShelf"), "Missing #handleAddCurrentOutputToShelf");
        assertTrue(actionHandlers.contains("#handleCopyOutput"), "Missing #handleCopyOutput");
    }

    @Test
    void testCriticalButtonsHaveFullText() throws Exception {
        String[] criticalTexts = {
            "Expand Result", "Copy Output", "Add to Shelf", "Generate Key"
        };

        for (String critText : criticalTexts) {
            boolean textFound = false;
            String[] targetFxmls = {"/fxml/main-view-modern.fxml", "/fxml/keys.fxml"};
            for (String fxmlPath : targetFxmls) {
                InputStream is = getClass().getResourceAsStream(fxmlPath);
                assertNotNull(is);
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder();
                Document doc = db.parse(is);
                Element root = doc.getDocumentElement();

                List<Element> buttons = findElementsByTagName(root, "Button");
                for (Element b : buttons) {
                    String text = b.getAttribute("text");
                    if (text != null && text.contains(critText)) {
                        textFound = true;
                        break;
                    }
                }
                if (textFound) break;
            }
            assertTrue(textFound, "Critical button text containing '" + critText + "' missing in FXMLs");
        }
    }

    @Test
    void testNoInvalidInlineColorTokensInFxmlAndJava() throws Exception {
        java.io.File fxmlDir = new java.io.File("src/main/resources/fxml");
        if (fxmlDir.exists() && fxmlDir.isDirectory()) {
            java.io.File[] files = fxmlDir.listFiles((dir, name) -> name.endsWith(".fxml"));
            if (files != null) {
                for (java.io.File f : files) {
                    String content = java.nio.file.Files.readString(f.toPath());
                    java.util.regex.Pattern p = java.util.regex.Pattern.compile("style=\\\"[^\\\"]*-color-[^\\\"]*\\\"");
                    java.util.regex.Matcher m = p.matcher(content);
                    assertFalse(m.find(), "FXML " + f.getName() + " must not contain inline style attributes with -color- tokens");
                }
            }
        }

        java.io.File uiDir = new java.io.File("src/main/java/com/cryptocarver/ui");
        if (uiDir.exists() && uiDir.isDirectory()) {
            java.io.File[] files = uiDir.listFiles((dir, name) -> name.endsWith(".java"));
            if (files != null) {
                for (java.io.File f : files) {
                    String content = java.nio.file.Files.readString(f.toPath());
                    java.util.regex.Pattern p = java.util.regex.Pattern.compile("setStyle\\s*\\([^)]*-color-[^)]*\\)");
                    java.util.regex.Matcher m = p.matcher(content);
                    assertFalse(m.find(), f.getName() + " must not use setStyle with unresolved -color- tokens");
                }
            }
        }
    }
}
