package com.cryptocarver.ui;

import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.help.HelpContext;
import com.cryptocarver.model.help.HelpContextState;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class JoseJweContextualHelpUITest {
    @BeforeAll static void startFx() {
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException alreadyStarted) { Platform.setImplicitExit(false); }
    }

    @Test void jweHelpTracksAllowlistedSelectorsPresenceAndAuxiliaryReturnAcrossPresentations() throws Exception {
        UiTestLifecycleExtension.onFx(() -> {
            AppSettings originalSettings = AppSettings.getInstance();
            LanguagePreference originalLanguage = I18nService.getInstance().getPreference();
            boolean originalHelpMode = originalSettings.isContextualHelpEnabled();
            java.nio.file.Path isolatedDirectory = null;
            try {
                isolatedDirectory = java.nio.file.Files.createTempDirectory("cryptocarver-jwe-help-test");
                AppSettings.setInstanceForTesting(new AppSettings(isolatedDirectory.resolve("settings.json")));
                for (LanguagePreference language : new LanguagePreference[]{LanguagePreference.EN, LanguagePreference.ES}) {
                    I18nService.getInstance().setPreference(language);
                    for (double width : new double[]{1500, 900}) {
                        var loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
                        Parent root = loader.load(); ModernMainController shell = loader.getController();
                        Stage owner = new Stage(); owner.setScene(new Scene(root, width, 850)); owner.show(); root.applyCss(); root.layout();
                        shell.navigateTo("JWE (Encrypted)");
                        Object jose = field(shell, "joseController");
                        CheckMenuItem helpMode = field(shell, "contextualHelpModeMenuItem");
                        helpMode.setSelected(false); shell.handleToggleContextualHelpMode();
                        javafx.scene.control.TitledPane decryptPane = field(jose, "jweDecryptPane");
                        javafx.scene.control.Accordion accordion = field(jose, "jweAccordion");
                        accordion.setExpandedPane(decryptPane);
                        HelpContextState beforeInactiveClick = field(shell, "contextualHelpState");
                        HelpContext previousContext = beforeInactiveClick.current();
                        clickPane(decryptPane);
                        assertEquals(previousContext, beforeInactiveClick.current(), "An inactive pane click must not mutate help context");
                        assertNull(field(shell, "contextualHelpWindowCoordinator"), "Inactive accordion expansion must not create compact help");
                        assertNotSame(field(shell, "inspectorHelpTab"), ((javafx.scene.control.TabPane) field(shell, "inspectorTabs")).getSelectionModel().getSelectedItem(),
                                "Inactive accordion expansion must not select the help tab");
                        accordion.setExpandedPane(field(jose, "jweEncryptPane"));
                        helpMode.setSelected(true); shell.handleToggleContextualHelpMode();

                        @SuppressWarnings("unchecked") ComboBox<String> alg = field(jose, "jweKeyAlgoCombo");
                        @SuppressWarnings("unchecked") ComboBox<String> enc = field(jose, "jweContentAlgoCombo");
                        @SuppressWarnings("unchecked") ComboBox<String> serialization = field(jose, "jweSerializationCombo");
                        javafx.scene.control.CheckBox compression = field(jose, "jweCompressCheck");
                        TextArea key = field(jose, "jwePublicKeyArea"), payload = field(jose, "jwePayloadArea");
                        TextField aad = field(jose, "jweAadField");
                        key.setText("JWE_PRIVATE_KEY_SENTINEL"); payload.setText("JWE_PAYLOAD_SENTINEL");
                        aad.setText("JWE_AAD_SENTINEL"); compression.setSelected(true);
                        alg.setValue("dir"); enc.setValue("A256GCM"); serialization.setValue("General JSON");

                        javafx.scene.Node algHelp = button(jose, "encrypt:key_algorithm");
                        assertTrue(algHelp.isVisible() && algHelp.isManaged());
                        assertEquals(I18nService.getInstance().text("help.field.op_jose_jwe.key_algorithm.label"),
                                algHelp.getAccessibleText());
                        if (language == LanguagePreference.EN) {
                            I18nService.getInstance().setPreference(LanguagePreference.ES);
                            shell.applyLocalization();
                            assertEquals(I18nService.getInstance().text("help.field.op_jose_jwe.key_algorithm.label"),
                                    algHelp.getAccessibleText());
                            I18nService.getInstance().setPreference(LanguagePreference.EN);
                            shell.applyLocalization();
                        }
                        ContextualHelpTestSupport.shiftF1(algHelp);
                        HelpContextState state = field(shell, "contextualHelpState");
                        assertEquals("op_jose_jwe", state.current().operationId());
                        assertEquals("key_algorithm", state.current().fieldId());
                        assertEquals("dir", state.current().selections().get(HelpContext.Selection.JWE_KEY_ALGORITHM).code());
                        assertEquals("a256gcm", state.current().selections().get(HelpContext.Selection.JWE_CONTENT_ENCRYPTION).code());
                        assertEquals("general_json", state.current().selections().get(HelpContext.Selection.JWE_SERIALIZATION).code());
                        assertEquals("true", state.current().selections().get(HelpContext.Selection.JWE_PUBLIC_MATERIAL_PRESENT).code());
                        assertEquals("true", state.current().selections().get(HelpContext.Selection.JWE_PAYLOAD_PRESENT).code());
                        assertEquals("enabled", state.current().selections().get(HelpContext.Selection.JWE_COMPRESSION).code());
                        assertEquals("true", state.current().selections().get(HelpContext.Selection.JWE_AAD_PRESENT).code());
                        assertEquals(10, state.current().selections().size());
                        assertFalse(state.current().toString().contains("SENTINEL"));
                        assertContains(currentHelp(shell), I18nService.getInstance().text("help.jwe.rule.direct.bytes", 32, 256));
                        assertContains(currentHelp(shell), I18nService.getInstance().text("help.jwe.rule.serialization.general"));
                        assertContains(currentHelp(shell), I18nService.getInstance().text("help.jwe.compression.enabled"));

                        ContextualHelpTestSupport.shiftF1(button(jose, "encrypt:public_key"));
                        assertContains(currentHelp(shell), I18nService.getInstance().text("help.jwe.material.public.present"));
                        ContextualHelpTestSupport.shiftF1(field(shell, "inputFormatCombo"));
                        assertEquals("aux_global_formats", state.current().operationId());
                        ContextualHelpTestSupport.shiftF1(field(shell, "outputFormatCombo"));
                        assertEquals("aux_global_formats", state.current().operationId());
                        shell.handleShowOperationHelp();
                        assertEquals("op_jose_jwe", state.current().operationId());
                        assertEquals("public_key", state.current().fieldId());
                        assertEquals("dir", state.current().selections().get(HelpContext.Selection.JWE_KEY_ALGORITHM).code());
                        assertEquals("general_json", state.current().selections().get(HelpContext.Selection.JWE_SERIALIZATION).code());

                        alg.setValue("RSA-OAEP-256"); enc.setValue("A128CBC-HS256"); serialization.setValue("Flattened JSON");
                        assertEquals("rsa_oaep_256", state.current().selections().get(HelpContext.Selection.JWE_KEY_ALGORITHM).code());
                        assertEquals("a128cbc_hs256", state.current().selections().get(HelpContext.Selection.JWE_CONTENT_ENCRYPTION).code());
                        assertEquals("flattened_json", state.current().selections().get(HelpContext.Selection.JWE_SERIALIZATION).code());
                        serialization.setValue("Compact");
                        assertContains(currentHelp(shell), I18nService.getInstance().text("help.jwe.rule.serialization.compact.aad"));

                        TextArea input = field(jose, "jweInputArea"), privateKey = field(jose, "jwePrivateKeyArea");
                        TextArea output = field(jose, "jweOutputArea"), decodedPayload = field(jose, "jweDecodedPayloadArea");
                        String receivedToken = "{\"protected\":\"fixture\",\"header\":{\"alg\":\"PBES2-HS512+A256KW\",\"enc\":\"A128CBC-HS256\"}}";
                        input.setText(receivedToken); privateKey.setText("JWE_DECRYPT_KEY_SENTINEL");
                        output.setText("JWE_RESULT_SENTINEL"); decodedPayload.setText("JWE_DECODED_SENTINEL");
                        accordion.setExpandedPane(decryptPane);
                        clickPane(decryptPane);
                        assertEquals("decrypt", state.current().actionId());
                        assertEquals("rsa_oaep_256", state.current().selections().get(HelpContext.Selection.JWE_KEY_ALGORITHM).code());
                        assertFalse(state.current().toString().contains("SENTINEL"));
                        assertFalse(currentHelp(shell).getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast)
                                .map(Label::getText).anyMatch(text -> text.contains(I18nService.getInstance().text("help.jwe.current.algorithm"))
                                        || text.contains(I18nService.getInstance().text("help.jwe.current.contentEncryption"))
                                        || text.contains(I18nService.getInstance().text("help.jwe.current.serialization"))
                                        || text.contains(I18nService.getInstance().text("help.jwe.rule.pbes2"))
                                        || text.contains(I18nService.getInstance().text("help.jwe.rule.serialization.compact.aad"))));
                        String[] beforeHelp = {input.getText(), privateKey.getText(), output.getText(), decodedPayload.getText(), key.getText(), payload.getText(), aad.getText()};
                        Map<String, String> allJweFormValues = snapshotControls(jose);
                        ContextualHelpTestSupport.shiftF1(button(jose, "decrypt:private_key"));
                        assertFalse(currentHelp(shell).getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast)
                                .map(Label::getText).anyMatch(text -> text.contains("fixture") || text.contains("JWE_")));
                        ContextualHelpTestSupport.shiftF1(button(jose, "decrypt:decrypt_key_format"));
                        assertContains(currentHelp(shell), I18nService.getInstance().text("help.field.op_jose_jwe.decrypt_key_format.description"));
                        assertContains(currentHelp(shell), I18nService.getInstance().text("help.option.op_jose_jwe.decrypt_key_format.hex"));
                        assertEquals(allJweFormValues, snapshotControls(jose), "Help queries must preserve every JWE form control and result");
                        assertArrayEquals(beforeHelp, new String[]{input.getText(), privateKey.getText(), output.getText(), decodedPayload.getText(), key.getText(), payload.getText(), aad.getText()});
                        assertFalse(state.current().toString().contains("fixture"));

                        Button encryptAction = currentHelp(shell).getChildren().stream().filter(Button.class::isInstance)
                                .map(Button.class::cast).filter(candidate -> "encrypt".equals(candidate.getUserData()))
                                .findFirst().orElseThrow();
                        encryptAction.requestFocus(); fireKey(encryptAction, KeyCode.ENTER, true); fireKey(encryptAction, KeyCode.ENTER, false);
                        assertEquals("encrypt", state.current().actionId(), "Keyboard activation should select a JWE help action");
                        if (width < 1100) {
                            ContextualHelpWindowCoordinator windows = field(shell, "contextualHelpWindowCoordinator");
                            var compactWindow = windows.windowForTesting();
                            compactWindow.close(); shell.handleShowOperationHelp();
                            assertTrue(windows.isShowing(), "Explicit help should create the compact window again after close");
                            assertEquals("encrypt", state.current().actionId());
                        }

                        shell.navigateTo("Hashing");
                        HelpContext whileInactive = state.current();
                        ContextualHelpTestSupport.shiftF1(algHelp);
                        assertEquals(whileInactive, state.current(), "Help controls must be inert outside the JWE route");
                        shell.navigateTo("JWE (Encrypted)");
                        ContextualHelpTestSupport.shiftF1(button(jose, "decrypt:private_key"));
                        assertEquals("private_key", state.current().fieldId(), "Context controls should reactivate on returning to JWE");
                        helpMode.setSelected(false); shell.handleToggleContextualHelpMode();
                        shell.shutdown(); owner.close(); owner.setScene(null);
                    }
                }
            } catch (Exception error) { throw new AssertionError(error); }
            finally {
                AppSettings.resetInstanceForTesting();
                originalSettings.setContextualHelpEnabled(originalHelpMode);
                I18nService.getInstance().setPreference(originalLanguage);
                if (isolatedDirectory != null) {
                    try (var paths = java.nio.file.Files.walk(isolatedDirectory)) {
                        paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                            try { java.nio.file.Files.deleteIfExists(path); } catch (java.io.IOException ignored) { }
                        });
                    } catch (java.io.IOException ignored) { }
                }
            }
        });
    }

    private static javafx.scene.Node button(Object controller, String binding) {
        String suffix = binding.endsWith(":") ? binding.substring(0, binding.length() - 1) + "_action"
                : binding.replace(':', '_');
        javafx.scene.Node section = field(controller, "jweSection");
        javafx.scene.Node control = ContextualHelpTestSupport.control(section, "contextHelp_op_jose_jwe_" + suffix);
        if (control == null) throw new AssertionError("Missing " + binding);
        return control;
    }

    private static java.util.List<javafx.scene.Node> descendants(javafx.scene.Parent root) {
        java.util.List<javafx.scene.Node> nodes = new java.util.ArrayList<>();
        for (javafx.scene.Node child : root.getChildrenUnmodifiable()) {
            nodes.add(child);
            if (child instanceof javafx.scene.Parent nested) nodes.addAll(descendants(nested));
        }
        return nodes;
    }

    private static VBox currentHelp(ModernMainController shell) {
        ContextualHelpWindowCoordinator windows = field(shell, "contextualHelpWindowCoordinator");
        return windows != null && windows.isShowing()
                ? (VBox) ((ScrollPane) windows.windowForTesting().getScene().getRoot()).getContent()
                : field(shell, "contextualHelpContent");
    }

    private static void assertContains(VBox content, String expected) {
        assertTrue(content.getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast)
                .anyMatch(label -> label.getText().contains(expected)), "Missing help text: " + expected
                + " ; actual=" + content.getChildren().stream().filter(Label.class::isInstance)
                        .map(Label.class::cast).map(Label::getText).toList());
    }

    private static void fireKey(Button button, KeyCode code, boolean pressed) {
        button.fireEvent(new KeyEvent(pressed ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED,
                "", "", code, false, false, false, false));
    }

    private static void clickPane(javafx.scene.control.TitledPane pane) {
        javafx.event.Event.fireEvent(pane, new MouseEvent(MouseEvent.MOUSE_CLICKED,
                1, 1, 1, 1, MouseButton.PRIMARY, 1, false, false, false, false,
                false, false, false, true, false, true,
                new javafx.scene.input.PickResult(pane, 1, 1)));
    }

    private static Map<String, String> snapshotControls(Object controller) {
        Map<String, String> values = new java.util.TreeMap<>();
        for (Class<?> type = controller.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(controller);
                    if (value instanceof javafx.scene.control.TextInputControl input) values.put(field.getName(), input.getText());
                    else if (value instanceof ComboBox<?> combo) values.put(field.getName(), String.valueOf(combo.getValue()));
                    else if (value instanceof javafx.scene.control.CheckBox check) values.put(field.getName(), Boolean.toString(check.isSelected()));
                } catch (IllegalAccessException error) { throw new AssertionError(error); }
            }
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object object, String name) {
        try {
            Class<?> type = object.getClass();
            while (type != null) {
                try { Field field = type.getDeclaredField(name); field.setAccessible(true); return (T) field.get(object); }
                catch (NoSuchFieldException ignored) { type = type.getSuperclass(); }
            }
            throw new NoSuchFieldException(name);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
}
