package com.cryptocarver.ui;

import javafx.application.Platform;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Thales pane, driven through the controller.
 *
 * <p>Every field is wired by name from the FXML, so a name that does not match
 * leaves a silent {@code null} and a button that does nothing. Here the pane's
 * own buttons load the manual's worked example and then reproduce it.</p>
 */
class ThalesLmkPaneTest {

    private static final String MANUAL_CRYPTOGRAM = "5178C9D3D1052B15BF6AEC458B4A4564";
    private static final String MANUAL_CHECK_VALUE = "8357D9";

    @BeforeAll
    static void startToolkit() {
        try {
            Platform.startup(() -> { });
        } catch (Exception alreadyRunning) {
            // The toolkit is process-wide; another test class may have started it.
        }
    }

    @Test
    void theManualExampleLoadsAndThenReproducesItself() throws Exception {
        KeysController controller = wire();

        controller.handleThalesLoadExample();
        assertEquals("209", field(controller, "thalesKeyTypeField").getText());
        assertEquals("U", combo(controller).getValue());

        // Clear the answer first, so reproducing it means something.
        field(controller, "thalesCryptogramField").setText("");
        controller.handleThalesEncrypt();

        assertEquals(MANUAL_CRYPTOGRAM, field(controller, "thalesCryptogramField").getText());
        assertEquals(MANUAL_CHECK_VALUE, field(controller, "thalesCheckValueField").getText());
        assertTrue(result(controller).contains("MK-SMI"), result(controller));
    }

    @Test
    void decryptPutsTheKeyBack() throws Exception {
        KeysController controller = wire();
        controller.handleThalesLoadExample();
        field(controller, "thalesClearKeyField").setText("");

        controller.handleThalesDecrypt();

        assertEquals("F1F1F1F1F1F1F1F1C1C1C1C1C1C1C1C1", field(controller, "thalesClearKeyField").getText());
        assertEquals(MANUAL_CHECK_VALUE, field(controller, "thalesCheckValueField").getText());
    }

    @Test
    void theKeyTypeSearchFindsTheTypeTheExampleUsed() throws Exception {
        KeysController controller = wire();
        controller.handleThalesLoadExample();

        controller.handleThalesLookup();

        assertTrue(result(controller).contains("209"), result(controller));
    }

    @Test
    void aBadInputIsReportedInThePaneRatherThanThrown() throws Exception {
        KeysController controller = wire();
        controller.handleThalesLoadExample();
        field(controller, "thalesLmkField").setText("not hex");

        controller.handleThalesEncrypt();

        assertTrue(result(controller).toLowerCase().contains("hexadecimal"), result(controller));
    }

    /** The pane must not show English in a Spanish window, as three panes have. */
    @Test
    void everySentenceThePaneShowsIsTranslatable() throws Exception {
        String fxml = Files.readString(Path.of("src/main/resources/fxml/keys.fxml"), StandardCharsets.UTF_8);
        String spanish = Files.readString(Path.of("src/main/resources/i18n/messages_es.properties"),
                StandardCharsets.UTF_8);
        Map<String, String> catalog = ModuleTextCatalog.keys();

        // Only this pane's own strings; the rest of the module is not this change's business.
        int from = fxml.indexOf("🏛 Thales Variant LMK");
        assertTrue(from > 0, "The Thales pane is missing from keys.fxml");
        String pane = fxml.substring(from, fxml.indexOf("</TitledPane>", from));

        Matcher matcher = Pattern
                .compile("<(?:Label|TitledPane|Button|CheckBox)[^>]*?text=\"([^\"]+)\"", Pattern.DOTALL)
                .matcher(pane);
        List<String> untranslated = new java.util.ArrayList<>();
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
        for (String name : List.of("thalesLmkField", "thalesKeyTypeField", "thalesClearKeyField",
                "thalesCryptogramField", "thalesCheckValueField")) {
            set(controller, name, new TextField());
        }
        set(controller, "thalesResultArea", new TextArea());
        set(controller, "thalesComponentCheck", new CheckBox());
        ComboBox<String> scheme = new ComboBox<>();
        scheme.getItems().setAll("U", "T", "Z");
        scheme.getSelectionModel().selectFirst();
        set(controller, "thalesSchemeCombo", scheme);
        return controller;
    }

    private static String result(KeysController controller) throws Exception {
        return field(controller, "thalesResultArea").getText();
    }

    private static TextInputControl field(KeysController controller, String name) throws Exception {
        Field declared = KeysController.class.getDeclaredField(name);
        declared.setAccessible(true);
        return (TextInputControl) declared.get(controller);
    }

    @SuppressWarnings("unchecked")
    private static ComboBox<String> combo(KeysController controller) throws Exception {
        Field declared = KeysController.class.getDeclaredField("thalesSchemeCombo");
        declared.setAccessible(true);
        return (ComboBox<String>) declared.get(controller);
    }

    private static void set(KeysController controller, String name, Object value) throws Exception {
        Field declared = KeysController.class.getDeclaredField(name);
        declared.setAccessible(true);
        declared.set(controller, value);
    }
}
