package com.cryptoforge.ui;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

class FxmlContractTest {

    @Test
    void modernViewIsWellFormedAndAllHandlersExist() throws Exception {
        String resource = "/fxml/main-view-modern.fxml";
        try (InputStream stream = getClass().getResourceAsStream(resource)) {
            assertNotNull(stream, "Missing production FXML: " + resource);

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document document = assertDoesNotThrow(() -> factory.newDocumentBuilder().parse(stream));
            Element root = document.getDocumentElement();
            String controllerName = root.getAttributeNS("http://javafx.com/fxml/1", "controller");
            if (controllerName.isBlank()) {
                controllerName = root.getAttribute("fx:controller");
            }
            assertTrue(!controllerName.isBlank(), "FXML must declare fx:controller");

            String resolvedControllerName = controllerName;
            Class<?> controller = Class.forName(resolvedControllerName);
            Set<String> methods = new HashSet<>();
            for (Class<?> type = controller; type != null; type = type.getSuperclass()) {
                for (Method method : type.getDeclaredMethods()) {
                    methods.add(method.getName());
                }
            }

            NodeList nodes = document.getElementsByTagName("*");
            for (int i = 0; i < nodes.getLength(); i++) {
                NamedNodeMap attributes = nodes.item(i).getAttributes();
                for (int j = 0; j < attributes.getLength(); j++) {
                    Node attribute = attributes.item(j);
                    String value = attribute.getNodeValue();
                    if (value != null && value.startsWith("#")) {
                        String handler = value.substring(1);
                        assertTrue(methods.contains(handler),
                                () -> "FXML handler not found in " + resolvedControllerName + ": " + handler);
                    }
                }
            }
        }
    }
}
