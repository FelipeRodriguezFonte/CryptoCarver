package com.cryptocarver.ui;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Static/headless regression checks for the UX-20 reset and catalog contract. */
class Ux20HeadlessTest {
    private static final Path ROOT = Path.of("src/main");

    @Test
    void specializedFxmlExposesDistinctClearAndResetDefaultsActions() throws Exception {
        for (String resource : List.of("xml_security.fxml", "wss_security.fxml", "jose.fxml",
                "cms_inspector.fxml", "pades.fxml", "asic.fxml", "payments.fxml", "emv.fxml")) {
            String fxml = Files.readString(ROOT.resolve("resources/fxml").resolve(resource), StandardCharsets.UTF_8);
            assertTrue(fxml.contains("text=\"Clear\""), resource);
            assertTrue(fxml.contains("text=\"Reset Defaults\""), resource);
        }
        String process = Files.readString(ROOT.resolve("resources/fxml/process_designer.fxml"), StandardCharsets.UTF_8);
        assertTrue(process.contains("onAction=\"#handleClearCanvas\""));
        assertTrue(process.contains("onAction=\"#handleResetDefaults\""));
    }

    @Test
    void catalogContainsEveryNewResetLabelAndBundlesContainBothLanguages() throws Exception {
        assertEquals("module.common.clear", ModuleTextCatalog.payments().get("Clear"));
        assertEquals("module.common.resetDefaults", ModuleTextCatalog.payments().get("Reset Defaults"));
        assertEquals("module.common.clear", ModuleTextCatalog.cmsInspector().get("Clear"));
        String en = Files.readString(ROOT.resolve("resources/i18n/messages.properties"), StandardCharsets.UTF_8);
        String es = Files.readString(ROOT.resolve("resources/i18n/messages_es.properties"), StandardCharsets.UTF_8);
        for (String key : List.of("module.common.clear", "module.common.clearStatus",
                "module.payments.clearStatus", "module.emv.clearStatus", "module.xml.clearStatus",
                "module.wss.clearStatus", "module.process.cancelledOutput")) {
            assertTrue(en.lines().anyMatch(line -> line.startsWith(key + "=")), "EN " + key);
            assertTrue(es.lines().anyMatch(line -> line.startsWith(key + "=")), "ES " + key);
        }
    }
}
