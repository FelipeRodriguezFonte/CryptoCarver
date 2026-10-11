package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.model.help.HelpContext;
import com.cryptocarver.model.help.HelpContextState;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
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
class PinBlockContextualHelpUITest {
    @BeforeAll static void startFx() {
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException alreadyStarted) { Platform.setImplicitExit(false); }
    }

    @Test void pinHelpTracksFormatsAndActionsWithoutReadingOrChangingInputs() throws Exception {
        UiTestLifecycleExtension.onFx(() -> {
            AppSettings original = AppSettings.getInstance();
            LanguagePreference originalLanguage = I18nService.getInstance().getPreference();
            boolean originalMode = original.isContextualHelpEnabled();
            java.nio.file.Path isolated = null;
            try {
                isolated = java.nio.file.Files.createTempDirectory("cryptocarver-pin-help-test");
                AppSettings.setInstanceForTesting(new AppSettings(isolated.resolve("settings.json")));
                for (LanguagePreference language : new LanguagePreference[]{LanguagePreference.EN, LanguagePreference.ES}) {
                    I18nService.getInstance().setPreference(language);
                    for (double width : new double[]{1800, 900}) {
                        var loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
                        Parent root = loader.load(); ModernMainController shell = loader.getController();
                        Stage stage = new Stage(); stage.setScene(new Scene(root, width, 900)); stage.show(); root.applyCss(); root.layout();
                        shell.navigateTo("Clear PIN Blocks");
                        PaymentsController payments = field(shell, "paymentsController");
                        CheckMenuItem mode = field(shell, "contextualHelpModeMenuItem");
                        HelpContextState state = field(shell, "contextualHelpState");
                        mode.setSelected(false); shell.handleToggleContextualHelpMode();
                        TitledPane clearPane = field(payments, "clearPinBlockPane");
                        TitledPane encryptedPaneBeforeMode = field(payments, "encryptedPinBlockPane");
                        javafx.scene.control.Accordion accordion = field(payments, "paymentsAccordion");
                        HelpContext inactive = state.current();
                        accordion.setExpandedPane(encryptedPaneBeforeMode);
                        assertEquals(inactive, state.current(), "Mode-off accordion changes must not mutate help state");
                        assertNull(field(shell, "contextualHelpWindowCoordinator"));
                        mode.setSelected(true); shell.handleToggleContextualHelpMode();
                        accordion.setExpandedPane(clearPane);
                        assertEquals("encode", state.current().actionId(), "Active pane expansion tracks the C080 direction");
                        accordion.setExpandedPane(encryptedPaneBeforeMode);
                        assertEquals("encode", state.current().actionId(), "A different PIN panel must not replace the active route context");
                        accordion.setExpandedPane(clearPane);
                        for (String suffix : java.util.List.of("op_pay_clear_pin_encode_pin", "op_pay_clear_pin_encode_pan_encode",
                                "op_pay_clear_pin_select_format_format_encode", "op_pay_clear_pin_select_format_padding",
                                "op_pay_clear_pin_decode_clear_block", "op_pay_clear_pin_decode_pan_decode",
                                "op_pay_clear_pin_decode_format_decode", "op_pay_clear_pin_encode_action",
                                "op_pay_clear_pin_decode_action")) assertTrue(helpButton(payments, suffix).isVisible(), suffix);

                        TextField pin = field(payments, "pinField"), pan = field(payments, "panFieldEncode"), block = field(payments, "pinBlockField");
                        TextField panDecode = field(payments, "panFieldDecode");
                        ComboBox<String> encodeFormat = field(payments, "pinBlockFormatCombo"), decodeFormat = field(payments, "pinBlockFormatDecodeCombo");
                        ComboBox<String> padding = field(payments, "pinBlockPaddingCombo");
                        TextArea clearResult = field(payments, "pinBlockResultArea");
                        pin.setText("PIN_CONTEXT_SENTINEL"); pan.setText("PAN_CONTEXT_SENTINEL"); panDecode.setText("PAN_DECODE_SENTINEL");
                        block.setText("BLOCK_CONTEXT_SENTINEL"); clearResult.setText("RESULT_CONTEXT_SENTINEL");
                        encodeFormat.setValue("Format 0 (ISO-0)"); decodeFormat.setValue("Format 1 (ISO-1)");
                        padding.setValue(null);
                        assertEquals("format_0_iso_0", state.current().selections().get(HelpContext.Selection.CLEAR_PIN_ENCODE_FORMAT).code());
                        assertEquals("format_1_iso_1", state.current().selections().get(HelpContext.Selection.CLEAR_PIN_DECODE_FORMAT).code());
                        javafx.scene.Node pinHelp = helpButton(payments, "op_pay_clear_pin_encode_pin");
                        assertTrue(pinHelp.isVisible() && pinHelp.isManaged());
                        assertEquals(I18nService.getInstance().text("help.field.op_pay_clear_pin.pin.label"), pinHelp.getAccessibleText());
                        Map<String,String> clearSnapshot = snapshotControls(payments);
                        ContextualHelpTestSupport.shiftF1(pinHelp);
                        assertEquals("pin", state.current().fieldId());
                        assertFalse(state.current().toString().contains("SENTINEL"));
                        assertEquals(clearSnapshot, snapshotControls(payments));
                        assertContains(currentHelp(shell), I18nService.getInstance().text("help.pin.currentFormat"));
                        assertContains(currentHelp(shell), I18nService.getInstance().text("help.pin.format.format_0_iso_0.pan"));
                        assertFalse(helpText(shell).contains("help.pin.format."),
                                "Rendered help should contain translated text, not resource keys");
                        assertFalse(helpText(shell).contains("PIN_CONTEXT_SENTINEL"));
                        if (language == LanguagePreference.EN) {
                            I18nService.getInstance().setPreference(LanguagePreference.ES); shell.applyLocalization();
                            assertEquals(I18nService.getInstance().text("help.field.op_pay_clear_pin.pin.label"), pinHelp.getAccessibleText());
                            I18nService.getInstance().setPreference(LanguagePreference.EN); shell.applyLocalization();
                        }

                        // Every live format is allowlisted and a selector change updates context immediately.
                        for (String format : com.cryptocarver.crypto.PinBlockFormat.displayNames()) {
                            encodeFormat.setValue(format);
                            assertEquals(com.cryptocarver.model.help.OperationHelpRegistry.slug(format),
                                    state.current().selections().get(HelpContext.Selection.CLEAR_PIN_ENCODE_FORMAT).code());
                        }
                        encodeFormat.setValue("ECI-2 (no PAN binding)");
                        assertEquals("F", padding.getValue());
                        assertFalse(padding.isDisabled(), "C080 should enable padding when the selected format offers choices");
                        padding.setValue("A");
                        assertEquals("a", state.current().selections().get(HelpContext.Selection.CLEAR_PIN_PADDING).code());
                        assertTrue(helpText(shell).contains(I18nService.getInstance().text("help.pin.selectedPadding") + ": A"));
                        encodeFormat.setValue("Format 0 (ISO-0)");
                        assertTrue(padding.isDisabled(), "C080 should disable padding for formats without choices");
                        decodeFormat.setValue("Format 0 (ISO-0)");
                        assertEquals("format_0_iso_0", state.current().selections().get(HelpContext.Selection.CLEAR_PIN_DECODE_FORMAT).code());
                        javafx.scene.Node clearDecodeFormatHelp = helpButton(payments, "op_pay_clear_pin_decode_format_decode");
                        ContextualHelpTestSupport.shiftF1(clearDecodeFormatHelp);
                        assertEquals("op_pay_clear_pin", state.current().operationId());
                        assertEquals("decode", state.current().actionId());
                        assertEquals("format_decode", state.current().fieldId());
                        assertContains(currentHelp(shell), I18nService.getInstance().text("help.pin.clear.padding.decode"));
                        Button backToClearGeneral = currentHelp(shell).getChildren().stream()
                                .filter(Button.class::isInstance).map(Button.class::cast)
                                .filter(button -> button.getText().equals(I18nService.getInstance().text("help.backToOperation")))
                                .findFirst().orElseThrow();
                        backToClearGeneral.fire();
                        assertEquals("op_pay_clear_pin", state.current().operationId());
                        assertEquals("", state.current().actionId());
                        assertEquals("", state.current().fieldId());
                        shell.handleShowOperationHelp();
                        assertEquals("", state.current().actionId(), "General-operation help must preserve its own route context");
                        ContextualHelpTestSupport.shiftF1(clearDecodeFormatHelp);
                        ContextualHelpTestSupport.shiftF1(field(shell, "inputFormatCombo"));
                        assertEquals("aux_global_formats", state.current().operationId());
                        shell.navigateTo("Clear PIN Blocks");
                        shell.handleShowOperationHelp();
                        assertEquals("decode", state.current().actionId(), "A001 return must retain the C080 action");
                        assertEquals("format_decode", state.current().fieldId(), "A001 return must retain the C080 field");
                        shell.navigateTo("Symmetric Ciphers");
                        assertFalse(shell.checkPreflightReadiness("Symmetric Ciphers", false));
                        ((Button) field(shell, "errorBannerHelpBtn")).fire();
                        assertEquals("aux_preflight_error", state.current().operationId());
                        shell.navigateTo("Clear PIN Blocks");
                        shell.handleShowOperationHelp();
                        assertEquals("decode", state.current().actionId(), "A002 return must retain the C080 action");
                        assertEquals("format_decode", state.current().fieldId(), "A002 return must retain the C080 field");
                        ContextualHelpTestSupport.shiftF1(clearDecodeFormatHelp);
                        shell.navigateTo("Encrypted PIN Blocks");
                        assertEquals("op_pay_enc_pin", state.current().operationId(), "Navigation must not restore a C080 action in C081");
                        assertEquals("", state.current().actionId());
                        assertEquals("", state.current().fieldId());
                        TitledPane encryptedPane = encryptedPaneBeforeMode; accordion.setExpandedPane(encryptedPane);
                        for (String suffix : java.util.List.of("op_pay_enc_pin_select_format_format", "op_pay_enc_pin_protect_pin",
                                "op_pay_enc_pin_protect_pan_encode", "op_pay_enc_pin_protect_encrypt_key",
                                "op_pay_enc_pin_decode_encrypted_block", "op_pay_enc_pin_decode_pan_decode",
                                "op_pay_enc_pin_decode_decrypt_key", "op_pay_enc_pin_protect_action",
                                "op_pay_enc_pin_decode_action")) assertTrue(helpButton(payments, suffix).isVisible(), suffix);
                        TextField encPin = field(payments, "encPinField"), encPan = field(payments, "encPanFieldEncode");
                        TextField encKey = field(payments, "encPinBlockKeyField"), decKey = field(payments, "encPinBlockKeyFieldDecode");
                        TextField encBlock = field(payments, "encPinBlockFieldDecode");
                        ComboBox<String> encFormat = field(payments, "encPinBlockFormatCombo");
                        TextArea encResult = field(payments, "encResultArea");
                        encPin.setText("ENCRYPT_PIN_SENTINEL"); encPan.setText("ENCRYPT_PAN_SENTINEL");
                        encKey.setText("SECRET_ENCODE_KEY_SENTINEL"); decKey.setText("");
                        encBlock.setText("ENCRYPTED_BLOCK_SENTINEL"); encResult.setText("ENCRYPTED_RESULT_SENTINEL");
                        encFormat.setValue("Format 4 (ISO-4)");
                        assertEquals("true", state.current().selections().get(HelpContext.Selection.ENCRYPT_PIN_KEY_PRESENT).code());
                        assertEquals("false", state.current().selections().get(HelpContext.Selection.DECRYPT_PIN_KEY_PRESENT).code());
                        javafx.scene.Node keyHelp = helpButton(payments, "op_pay_enc_pin_protect_encrypt_key");
                        javafx.scene.Node protectFormatHelp = helpButton(payments, "op_pay_enc_pin_protect_action");
                        Map<String,String> encSnapshot = snapshotControls(payments);
                        assertTrue(keyHelp.isFocusTraversable(), "The PIN key field should be keyboard-focusable");
                        ContextualHelpTestSupport.shiftF1(keyHelp);
                        assertEquals("op_pay_enc_pin", state.current().operationId());
                        assertEquals("encrypt_key", state.current().fieldId());
                        assertFalse(state.current().toString().contains("SENTINEL"));
                        assertEquals(encSnapshot, snapshotControls(payments));
                        assertFalse(helpText(shell).contains("SECRET_ENCODE_KEY_SENTINEL"));
                        assertContains(currentHelp(shell), I18nService.getInstance().text("help.pin.encryption.aes"));
                        assertContains(currentHelp(shell), I18nService.getInstance().text("help.pin.iso4.resultFields"));
                        assertContains(currentHelp(shell), I18nService.getInstance().text("help.pin.encrypted.padding.notConfigurable"));
                        javafx.scene.Node decodeAction = helpButton(payments, "op_pay_enc_pin_decode_action");
                        ContextualHelpTestSupport.shiftF1(decodeAction);
                        assertEquals("decode", state.current().actionId());
                        assertEquals("format_4_iso_4", state.current().selections().get(HelpContext.Selection.ENCRYPTED_PIN_FORMAT).code());
                        assertContains(currentHelp(shell), I18nService.getInstance().text("help.pin.decodeKey.absent"));
                        assertContains(currentHelp(shell), I18nService.getInstance().text("help.pin.encrypted.padding.decode"));
                        assertFalse(helpText(shell).contains(I18nService.getInstance().text("help.pin.encrypted.padding.notConfigurable")));
                        encFormat.setValue("VISA-2");
                        assertEquals("visa_2", state.current().selections().get(HelpContext.Selection.ENCRYPTED_PIN_FORMAT).code());
                        ContextualHelpTestSupport.shiftF1(protectFormatHelp);
                        assertContains(currentHelp(shell), I18nService.getInstance().text("help.pin.encrypted.padding.default") + ": 5");
                        ContextualHelpTestSupport.shiftF1(decodeAction);
                        assertContains(currentHelp(shell), I18nService.getInstance().text("help.pin.encrypted.padding.decode"));
                        assertFalse(helpText(shell).contains(I18nService.getInstance().text("help.pin.format.visa_2.padding")));
                        encFormat.setValue("ECI-2 (no PAN binding)");
                        ContextualHelpTestSupport.shiftF1(protectFormatHelp);
                        assertContains(currentHelp(shell), I18nService.getInstance().text("help.pin.encrypted.padding.default") + ": F");
                        encFormat.setValue("ECI-3 (no PAN binding)");
                        ContextualHelpTestSupport.shiftF1(protectFormatHelp);
                        assertContains(currentHelp(shell), I18nService.getInstance().text("help.pin.encrypted.padding.default") + ": F");
                        encFormat.setValue("Docutel (Format 02)");
                        ContextualHelpTestSupport.shiftF1(protectFormatHelp);
                        assertContains(currentHelp(shell), I18nService.getInstance().text("help.pin.encrypted.padding.notConfigurable"));
                        // An unrelated C080 selector value must not change the C081 format/default.
                        padding.setValue("A");
                        assertEquals("docutel_format_02", state.current().selections().get(HelpContext.Selection.ENCRYPTED_PIN_FORMAT).code());
                        assertContains(currentHelp(shell), I18nService.getInstance().text("help.pin.currentFormat") + ": Docutel (Format 02)");
                        ContextualHelpTestSupport.shiftF1(keyHelp);
                        shell.navigateTo("Clear PIN Blocks");
                        assertEquals("op_pay_clear_pin", state.current().operationId());
                        assertEquals("decode", state.current().actionId(), "Returning to C080 must restore its own action");
                        assertEquals("format_decode", state.current().fieldId(), "Returning to C080 must restore its own field");
                        shell.navigateTo("Encrypted PIN Blocks");
                        assertEquals("op_pay_enc_pin", state.current().operationId());
                        assertEquals("protect", state.current().actionId(), "Returning to C081 must restore its own action");
                        assertEquals("encrypt_key", state.current().fieldId(), "Returning to C081 must restore its own field");
                        encKey.setText("");
                        javafx.scene.Node protectAction = helpButton(payments, "op_pay_enc_pin_protect_action");
                        ContextualHelpTestSupport.shiftF1(protectAction);
                        assertEquals("false", state.current().selections().get(HelpContext.Selection.ENCRYPT_PIN_KEY_PRESENT).code());
                        assertContains(currentHelp(shell), I18nService.getInstance().text("help.pin.key.absent"));
                        shell.navigateTo("Hashing");
                        HelpContext whileInactive = state.current();
                        ContextualHelpTestSupport.shiftF1(keyHelp);
                        assertEquals(whileInactive, state.current(), "PIN help controls must be inert outside the PIN routes");
                        shell.navigateTo("Encrypted PIN Blocks");
                        ContextualHelpTestSupport.shiftF1(keyHelp);
                        assertEquals("encrypt_key", state.current().fieldId());
                        if (width < 1100) {
                            ContextualHelpWindowCoordinator window = field(shell, "contextualHelpWindowCoordinator");
                            window.windowForTesting().close(); shell.handleShowOperationHelp();
                            assertTrue(window.isShowing());
                            assertEquals("op_pay_enc_pin", state.current().operationId());
                            assertEquals("protect", state.current().actionId());
                            assertEquals("encrypt_key", state.current().fieldId(), "Compact close/reopen must preserve the C081 field");
                        }
                        mode.setSelected(false); shell.handleToggleContextualHelpMode(); shell.shutdown(); stage.close(); stage.setScene(null);
                    }
                }
            } catch (Exception error) { throw new AssertionError(error); }
            finally {
                AppSettings.resetInstanceForTesting(); original.setContextualHelpEnabled(originalMode);
                I18nService.getInstance().setPreference(originalLanguage);
                if (isolated != null) try (var paths = java.nio.file.Files.walk(isolated)) {
                    paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> { try { java.nio.file.Files.deleteIfExists(path); } catch (java.io.IOException ignored) { } });
                } catch (java.io.IOException ignored) { }
            }
        });
    }

    private static javafx.scene.Node helpButton(PaymentsController controller, String suffix) {
        javafx.scene.Node accordion = field(controller, "paymentsAccordion");
        javafx.scene.Node control = ContextualHelpTestSupport.control(accordion.getScene().getRoot(), "contextHelp_" + suffix);
        if (control == null) throw new AssertionError("Missing help control " + suffix);
        return control;
    }
    private static VBox currentHelp(ModernMainController shell) {
        ContextualHelpWindowCoordinator window = field(shell, "contextualHelpWindowCoordinator");
        return window != null && window.isShowing() ? (VBox)((ScrollPane)window.windowForTesting().getScene().getRoot()).getContent()
                : field(shell, "contextualHelpContent");
    }
    private static String helpText(ModernMainController shell) {
        return currentHelp(shell).getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast)
                .map(Label::getText).collect(java.util.stream.Collectors.joining("\n"));
    }
    private static void assertContains(VBox help, String expected) {
        assertTrue(help.getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast)
                .anyMatch(label -> label.getText().contains(expected)), "Missing help text: " + expected);
    }
    private static Map<String,String> snapshotControls(Object controller) {
        Map<String,String> result = new java.util.TreeMap<>();
        for (Class<?> type=controller.getClass(); type!=null; type=type.getSuperclass()) for (Field field:type.getDeclaredFields()) try {
            field.setAccessible(true); Object value=field.get(controller);
            if (value instanceof javafx.scene.control.TextInputControl input) result.put(field.getName(), input.getText());
            else if (value instanceof ComboBox<?> combo) result.put(field.getName(), String.valueOf(combo.getValue()));
            else if (value instanceof javafx.scene.control.CheckBox check) result.put(field.getName(), Boolean.toString(check.isSelected()));
        } catch (IllegalAccessException error) { throw new AssertionError(error); }
        return result;
    }
    @SuppressWarnings("unchecked") private static <T>T field(Object object,String name) {
        try { for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass()) try {
            Field f=type.getDeclaredField(name); f.setAccessible(true); return (T)f.get(object);
        } catch(NoSuchFieldException ignored) { }
            throw new NoSuchFieldException(name);
        } catch(ReflectiveOperationException error) { throw new AssertionError(error); }
    }
}
