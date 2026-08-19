package com.cryptocarver.ui;

import java.io.InputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contract-level coverage for the FXML shell migration; live JavaFX coverage is opt-in in the UI suite. */
class ModernMainControllerI18nFxmlTest {
    @Test
    void migratedModernShellFxmlParsesAndContainsLanguageControls() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/fxml/main-view-modern.fxml")) {
            assertNotNull(input);
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input);
            String xml = new String(getClass().getResourceAsStream("/fxml/main-view-modern.fxml").readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            assertNotNull(document);
            assertTrue(xml.contains("fx:id=\"languageMenu\""));
            assertTrue(xml.contains("fx:id=\"languageSystemMenuItem\""));
            assertTrue(xml.contains("fx:id=\"toolbarSearchButton\""));
            assertTrue(xml.contains("fx:id=\"commandSearchField\""));
        }
    }
}
