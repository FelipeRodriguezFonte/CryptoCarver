package com.cryptocarver.ui;

import javafx.application.Platform;
import javafx.scene.control.TextArea;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Key Block pane, driven through the controller. */
class ThalesKeyBlockPaneTest {

    @BeforeAll
    static void startToolkit() {
        try {
            Platform.startup(() -> { });
        } catch (Exception alreadyRunning) {
            // The toolkit is process-wide; another test class may have started it.
        }
    }

    @Test
    void theManualExampleLoadsAndInspectsCleanly() throws Exception {
        KeysController controller = wire();

        controller.handleKeyBlockExample();
        assertTrue(area(controller, "keyBlockInputArea").getText().startsWith("S00072V2TG22N0033"));

        controller.handleKeyBlockInspect();

        String report = area(controller, "keyBlockResultArea").getText();
        assertTrue(report.contains("PIN Verification Key (Visa PVV)"), report);
        assertTrue(report.contains("WELL FORMED"), report);
        // The example's own LMK ID is outside the range its page defines.
        assertTrue(report.contains("outside the range"), report);
    }

    @Test
    void theReportSaysWhyTheKeyDataIsNotDecrypted() throws Exception {
        KeysController controller = wire();
        controller.handleKeyBlockExample();

        controller.handleKeyBlockInspect();

        String report = area(controller, "keyBlockResultArea").getText();
        assertTrue(report.contains("not decrypted here"), report);
        assertTrue(report.contains("a variant of the LMK"), report);
    }

    @Test
    void aBadBlockIsReportedInThePaneRatherThanThrown() throws Exception {
        KeysController controller = wire();
        area(controller, "keyBlockInputArea").setText("S0007");

        controller.handleKeyBlockInspect();

        assertTrue(area(controller, "keyBlockResultArea").getText().contains("16 characters"),
                area(controller, "keyBlockResultArea").getText());
    }

    @Test
    void everySentenceThePaneShowsIsTranslatable() throws Exception {
        String fxml = Files.readString(Path.of("src/main/resources/fxml/keys.fxml"), StandardCharsets.UTF_8);
        String spanish = Files.readString(Path.of("src/main/resources/i18n/messages_es.properties"),
                StandardCharsets.UTF_8);
        Map<String, String> catalog = ModuleTextCatalog.keys();

        int from = fxml.indexOf("🧱 Thales Key Block");
        assertTrue(from > 0, "The Key Block pane is missing from keys.fxml");
        String pane = fxml.substring(from, fxml.indexOf("</TitledPane>", from));

        List<String> untranslated = new ArrayList<>();
        Matcher matcher = Pattern
                .compile("<(?:Label|TitledPane|Button|CheckBox)[^>]*?text=\"([^\"]+)\"", Pattern.DOTALL)
                .matcher(pane);
        while (matcher.find()) {
            String text = matcher.group(1)
                    .replace("&quot;", "\"").replace("&apos;", "'")
                    .replace("&lt;", "<").replace("&gt;", ">")
                    .replace("&amp;", "&");
            String key = catalog.get(text);
            if (key == null || spanish.lines().noneMatch(line -> line.startsWith(key + "="))) {
                untranslated.add(text.length() > 50 ? text.substring(0, 50) + "..." : text);
            }
        }
        assertTrue(untranslated.isEmpty(),
                "These would show in English in a Spanish window: " + untranslated);
    }

    private static KeysController wire() throws Exception {
        KeysController controller = new KeysController();
        set(controller, "keyBlockInputArea", new TextArea());
        set(controller, "keyBlockResultArea", new TextArea());
        return controller;
    }

    private static TextArea area(KeysController controller, String name) throws Exception {
        Field declared = KeysController.class.getDeclaredField(name);
        declared.setAccessible(true);
        return (TextArea) declared.get(controller);
    }

    private static void set(KeysController controller, String name, Object value) throws Exception {
        Field declared = KeysController.class.getDeclaredField(name);
        declared.setAccessible(true);
        declared.set(controller, value);
    }
}
