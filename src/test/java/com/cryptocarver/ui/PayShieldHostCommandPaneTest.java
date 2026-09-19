package com.cryptocarver.ui;

import javafx.application.Platform;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The payShield host-frame pane, driven through its controller methods. */
class PayShieldHostCommandPaneTest {

    @BeforeAll
    static void startToolkit() {
        try {
            Platform.startup(() -> { });
        } catch (Exception alreadyRunning) {
            // JavaFX is process-wide.
        }
    }

    @Test
    void suppliedNcExampleIsDecoded() throws Exception {
        PaymentsController controller = wire();

        controller.handleHsmHostLoadExample();
        assertEquals("0000ND007B44AC1DDEE2A94B0007-E000",
                area(controller, "hsmHostCapturedFrameArea").getText());

        controller.handleHsmHostAnalyzeResponse();

        String report = area(controller, "hsmHostResultArea").getText();
        assertTrue(report.contains("ND"), report);
        assertTrue(report.contains("00"), report);
        assertTrue(report.contains("7B44AC1DDEE2A94B0007-E000"), report);
        assertTrue(report.contains("LMK check value: 7B44AC1DDEE2A94B"), report);
        assertTrue(report.contains("Firmware version: 0007-E000"), report);
    }

    @Test
    void ncCommandCanBeComposedAndParsed() throws Exception {
        PaymentsController controller = wire();

        controller.handleHsmHostCompose();
        assertEquals("0000NC", area(controller, "hsmHostCapturedFrameArea").getText());

        controller.handleHsmHostAnalyzeCommand();
        String report = area(controller, "hsmHostResultArea").getText();
        assertTrue(report.contains("NC"), report);
        assertTrue(report.toLowerCase().contains("diagnostic"), report);
    }

    @Test
    void malformedFrameIsReportedInThePane() throws Exception {
        PaymentsController controller = wire();
        area(controller, "hsmHostCapturedFrameArea").setText("0000N");

        controller.handleHsmHostAnalyzeResponse();

        assertTrue(area(controller, "hsmHostResultArea").getText().toLowerCase().contains("error"),
                area(controller, "hsmHostResultArea").getText());
    }

    @Test
    void everySentenceThePaneShowsIsTranslatable() throws Exception {
        String fxml = Files.readString(
                Path.of("src/main/resources/fxml/payments.fxml"), StandardCharsets.UTF_8);
        String spanish = Files.readString(Path.of("src/main/resources/i18n/messages_es.properties"),
                StandardCharsets.UTF_8);
        Map<String, String> catalog = ModuleTextCatalog.payments();

        int from = fxml.indexOf("🖥 payShield Host Command Bank");
        assertTrue(from > 0, "The payShield pane is missing from payments.fxml");
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

    private static PaymentsController wire() throws Exception {
        PaymentsController controller = new PaymentsController();
        set(controller, "hsmHostHeaderField", new TextField("0000"));
        set(controller, "hsmHostCommandCodeField", new TextField("NC"));
        set(controller, "hsmHostBodyField", new TextField());
        set(controller, "hsmHostTrailerField", new TextField());
        set(controller, "hsmHostHeaderLengthField", new TextField("4"));
        set(controller, "hsmHostTcpPrefixCheck", new CheckBox());
        set(controller, "hsmHostCapturedFrameArea", new TextArea());
        set(controller, "hsmHostResultArea", new TextArea());
        return controller;
    }

    private static TextInputControl field(PaymentsController controller, String name) throws Exception {
        Field declared = PaymentsController.class.getDeclaredField(name);
        declared.setAccessible(true);
        return (TextInputControl) declared.get(controller);
    }

    private static TextArea area(PaymentsController controller, String name) throws Exception {
        return (TextArea) field(controller, name);
    }

    private static void set(PaymentsController controller, String name, Object value) throws Exception {
        Field declared = PaymentsController.class.getDeclaredField(name);
        declared.setAccessible(true);
        declared.set(controller, value);
    }
}
