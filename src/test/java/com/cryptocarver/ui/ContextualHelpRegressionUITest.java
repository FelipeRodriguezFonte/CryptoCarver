package com.cryptocarver.ui;

import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.model.help.HelpContext;
import com.cryptocarver.model.help.HelpContextState;
import com.cryptocarver.service.I18nService;
import com.cryptocarver.service.LocalHelpService;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ContextualHelpRegressionUITest {
    @BeforeAll static void startFx() {
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException alreadyStarted) { Platform.setImplicitExit(false); }
    }

    @Test void dockedActionsAreSelectableByMouseAndKeyboardWithoutChangingResultOrInputs() throws Exception {
        AtomicReference<ModernMainController> shellRef = new AtomicReference<>();
        AtomicReference<Stage> ownerRef = new AtomicReference<>();
        UiTestLifecycleExtension.onFx(() -> {
            try {
                I18nService.getInstance().setPreference(LanguagePreference.ES);
                var loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
                Parent root = loader.load();
                ModernMainController shell = loader.getController();
                Stage owner = new Stage();
                owner.setScene(new Scene(root, 1400, 900));
                owner.show(); root.applyCss(); root.layout();
                shell.navigateTo("Symmetric Ciphers");
                Object cipher = field(shell, "cipherController");
                TextField key = field(cipher, "symmetricKeyField");
                TextArea input = field(cipher, "cipherInputArea");
                TextArea output = field(cipher, "cipherOutputArea");
                key.setText("000102030405060708090A0B0C0D0E0F");
                input.setText("B00_INPUT_SENTINEL");
                output.setText("B00_RESULT_SENTINEL");
                shell.updateInspector("B00 historical result", new byte[]{1}, new byte[]{2}, java.util.Map.of());
                String result = ((Label) field(shell, "operationLabel")).getText();
                shell.handleShowOperationHelp();
                VBox content = field(shell, "contextualHelpContent");
                Button encrypt = action(content, "Cifrar");
                encrypt.fire();
                assertContainsLabel(content, I18nService.getInstance().text("help.action.op_sym_ciphers.encrypt.description"));
                assertContainsLabel(content, I18nService.getInstance().text("help.action.op_sym_ciphers.encrypt.example"));

                Button decrypt = action(content, "Descifrar");
                decrypt.requestFocus();
                fireKey(decrypt, KeyCode.SPACE, true);
                fireKey(decrypt, KeyCode.SPACE, false);
                assertContainsLabel(content, I18nService.getInstance().text("help.action.op_sym_ciphers.decrypt.description"));
                assertContainsLabel(content, I18nService.getInstance().text("help.action.op_sym_ciphers.decrypt.example"));
                assertEquals("000102030405060708090A0B0C0D0E0F", key.getText());
                assertEquals("B00_INPUT_SENTINEL", input.getText());
                assertEquals("B00_RESULT_SENTINEL", output.getText());
                assertEquals(result, ((Label) field(shell, "operationLabel")).getText());
                shellRef.set(shell); ownerRef.set(owner);
            } catch (Exception error) { throw new AssertionError(error); }
        });
        UiTestLifecycleExtension.onFx(() -> {
            shellRef.get().shutdown();
            ownerRef.get().close(); ownerRef.get().setScene(null);
        });
    }

    @Test void b01FieldHelpRendersLocalizedActionOnceAndBackKeepsLiveSelections() throws Exception {
        AtomicReference<ModernMainController> shellRef = new AtomicReference<>();
        AtomicReference<Stage> ownerRef = new AtomicReference<>();
        UiTestLifecycleExtension.onFx(() -> {
            try {
                I18nService.getInstance().setPreference(LanguagePreference.ES);
                var loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
                Parent root = loader.load(); ModernMainController shell = loader.getController();
                Stage owner = new Stage(); owner.setScene(new Scene(root, 1200, 850)); owner.show(); root.applyCss(); root.layout();
                shell.navigateTo("Symmetric Ciphers"); Object cipher = field(shell, "cipherController");
                @SuppressWarnings("unchecked") javafx.scene.control.ComboBox<String> algorithm = field(cipher, "symmetricAlgorithmCombo");
                @SuppressWarnings("unchecked") javafx.scene.control.ComboBox<String> mode = field(cipher, "cipherModeCombo");
                algorithm.setValue("ChaCha20-Poly1305"); mode.setValue("CBC");
                ((CheckMenuItem) field(shell, "contextualHelpModeMenuItem")).setSelected(true); shell.handleToggleContextualHelpMode();
                ContextualHelpTestSupport.shiftF1(symHelp(cipher, "symDecryptHelpBtn")); VBox help = currentHelp(shell);
                String desc = I18nService.getInstance().text("help.action.op_sym_ciphers.decrypt.description");
                String example = I18nService.getInstance().text("help.action.op_sym_ciphers.decrypt.example");
                assertEquals(1, exactLabelCount(help, desc)); assertEquals(1, exactLabelCount(help, example));
                assertFalse(help.getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast)
                        .anyMatch(label -> label.getText().startsWith("help.")));
                ContextualHelpTestSupport.shiftF1(symHelp(cipher, "symIvHelpBtn")); HelpContextState state = field(shell, "contextualHelpState");
                assertEquals("chacha20_poly1305", state.current().selections().get(HelpContext.Selection.ALGORITHM).code());
                assertEquals("cbc", state.current().selections().get(HelpContext.Selection.MODE).code());
                Button back = help.getChildren().stream().filter(Button.class::isInstance).map(Button.class::cast)
                        .filter(button -> I18nService.getInstance().text("help.backToOperation").equals(button.getText()))
                        .findFirst().orElseThrow();
                back.fire();
                assertEquals("chacha20_poly1305", state.current().selections().get(HelpContext.Selection.ALGORITHM).code());
                assertEquals("cbc", state.current().selections().get(HelpContext.Selection.MODE).code());
                ContextualHelpTestSupport.shiftF1(symHelp(cipher, "symDecryptHelpBtn"));
                assertEquals("chacha20_poly1305", state.current().selections().get(HelpContext.Selection.ALGORITHM).code());
                assertEquals("cbc", state.current().selections().get(HelpContext.Selection.MODE).code());
                for (var actionHelp : com.cryptocarver.model.help.OperationHelpRegistry.getInstance()
                        .resolve("op_sym_ciphers").actions()) {
                    Button actionButton = help.getChildren().stream().filter(Button.class::isInstance).map(Button.class::cast)
                            .filter(button -> actionHelp.id().equals(button.getUserData())).findFirst().orElseThrow();
                    actionButton.fire();
                    String titleKey = actionHelp.titleKey();
                    String descriptionKey = actionHelp.descriptionKey();
                    String exampleKey = actionHelp.exampleKey();
                    assertFalse(I18nService.getInstance().text(titleKey).equals(titleKey), titleKey);
                    String localizedDescription = I18nService.getInstance().text(descriptionKey);
                    String localizedExample = I18nService.getInstance().text(exampleKey);
                    assertFalse(localizedDescription.equals(descriptionKey), descriptionKey);
                    assertFalse(localizedExample.equals(exampleKey), exampleKey);
                    assertEquals(1, exactLabelCount(help, localizedDescription), actionHelp.id());
                    assertEquals(1, exactLabelCount(help, localizedExample), actionHelp.id());
                    assertFalse(help.getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast)
                            .anyMatch(label -> label.getText().startsWith("help.")), actionHelp.id());
                }
                CheckMenuItem modePreference = field(shell, "contextualHelpModeMenuItem");
                modePreference.setSelected(false); shell.handleToggleContextualHelpMode();
                shellRef.set(shell); ownerRef.set(owner);
            } catch (Exception error) { throw new AssertionError(error); }
        });
        UiTestLifecycleExtension.onFx(() -> { shellRef.get().shutdown(); ownerRef.get().close(); ownerRef.get().setScene(null); });
    }

    @Test void b01SymmetricHelpButtonsFollowRouteAndRealKeySourceControlsAcrossWidths() throws Exception {
        AtomicReference<ModernMainController> shellRef = new AtomicReference<>();
        AtomicReference<Stage> ownerRef = new AtomicReference<>();
        var simulatedHsm = com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance();
        String fixtureKeyId = "b01-help-material-fixture";
        var fixtureKey = com.cryptocarver.crypto.hsm.KeyMaterialFactory.fromSecretKey(fixtureKeyId,
                new javax.crypto.spec.SecretKeySpec(new byte[32], "AES"),
                com.cryptocarver.crypto.hsm.KeyExportability.NON_EXPORTABLE,
                java.util.Set.of(com.cryptocarver.crypto.hsm.KeyUsage.ENCRYPT, com.cryptocarver.crypto.hsm.KeyUsage.DECRYPT));
        simulatedHsm.importKey(fixtureKey, true);
        UiTestLifecycleExtension.onFx(() -> {
            try {
                I18nService.getInstance().setPreference(LanguagePreference.ES);
                var loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
                Parent root = loader.load(); ModernMainController shell = loader.getController();
                Stage owner = new Stage(); owner.setScene(new Scene(root, 1800, 900)); owner.show(); root.applyCss(); root.layout();
                shell.navigateTo("Symmetric Ciphers"); Object cipher = field(shell, "cipherController");
                CheckMenuItem mode = field(shell, "contextualHelpModeMenuItem"); mode.setSelected(true); shell.handleToggleContextualHelpMode();
                javafx.scene.Node algorithmHelp = symHelp(cipher, "symAlgorithmHelpBtn"); assertTrue(algorithmHelp.isVisible() && algorithmHelp.isManaged());
                ContextualHelpTestSupport.shiftF1(field(shell, "inputFormatCombo"));
                assertEquals("aux_global_formats", ((HelpContextState) field(shell, "contextualHelpState")).current().operationId());
                mode.setSelected(false); shell.handleToggleContextualHelpMode(); assertFocusDoesNotPublish(shell, algorithmHelp);
                mode.setSelected(true); shell.handleToggleContextualHelpMode(); assertTrue(algorithmHelp.isVisible() && algorithmHelp.isManaged());
                assertFalse(shell.checkPreflightReadiness("Symmetric Ciphers", false));
                ((Button) field(shell, "errorBannerHelpBtn")).fire();
                assertEquals("aux_preflight_error", ((HelpContextState) field(shell, "contextualHelpState")).current().operationId());
                mode.setSelected(false); shell.handleToggleContextualHelpMode();
                assertFocusDoesNotPublish(shell, algorithmHelp);
                mode.setSelected(true); shell.handleToggleContextualHelpMode();
                assertTrue(algorithmHelp.isVisible() && algorithmHelp.isManaged());
                ContextualHelpTestSupport.shiftF1(algorithmHelp);
                assertEquals("op_sym_ciphers", ((HelpContextState) field(shell, "contextualHelpState")).current().operationId());
                owner.setWidth(900); root.applyCss(); root.layout(); assertTrue(algorithmHelp.isVisible() && algorithmHelp.isManaged());
                @SuppressWarnings("unchecked") javafx.scene.control.ComboBox<String> source = field(cipher, "symKeySourceCombo");
                javafx.scene.Node inspect = symHelp(cipher, "symInspectKeyHelpBtn"); Button inspectAction = field(cipher, "inspectKeyBtn");
                source.setValue("Manual Input"); source.fireEvent(new javafx.event.ActionEvent());
                assertFalse(inspectAction.isVisible() || inspect.isVisible() || inspect.isManaged());
                source.setValue("Simulated HSM"); source.fireEvent(new javafx.event.ActionEvent());
                assertTrue(inspectAction.isVisible() && inspectAction.isManaged()); assertTrue(inspect.isVisible() && inspect.isManaged());
                assertEquals(I18nService.getInstance().text("help.field.op_sym_ciphers.key_lab_key.label"),
                        symHelp(cipher, "symKeyLabKeyHelp").getAccessibleText());
                @SuppressWarnings("unchecked") javafx.scene.control.ComboBox<String> hsmKey = field(cipher, "symHsmKeyCombo");
                hsmKey.setValue(null); ContextualHelpTestSupport.shiftF1(symHelp(cipher, "symIvHelpBtn"));
                assertEquals("false", ((HelpContextState) field(shell, "contextualHelpState")).current()
                        .selections().get(HelpContext.Selection.MATERIAL_PRESENT).code());
                hsmKey.setValue(fixtureKeyId); ContextualHelpTestSupport.shiftF1(symHelp(cipher, "symIvHelpBtn"));
                assertEquals("true", ((HelpContextState) field(shell, "contextualHelpState")).current()
                        .selections().get(HelpContext.Selection.MATERIAL_PRESENT).code());
                source.setValue("Manual Input"); source.fireEvent(new javafx.event.ActionEvent());
                assertFalse(inspect.isVisible() || inspect.isManaged());
                assertEquals(I18nService.getInstance().text("help.field.op_sym_ciphers.manual_key.label"),
                        symHelp(cipher, "symKeyMaterialHelpBtn").getAccessibleText());
                shell.navigateTo("Hashing"); assertShiftF1Inert(shell, algorithmHelp);
                simulatedHsm.deleteKey(fixtureKeyId);
                mode.setSelected(false); shell.handleToggleContextualHelpMode();
                shellRef.set(shell); ownerRef.set(owner);
            } catch (Exception error) { throw new AssertionError(error); }
        });
        UiTestLifecycleExtension.onFx(() -> { shellRef.get().shutdown(); ownerRef.get().close(); ownerRef.get().setScene(null); });
    }

    @Test void b01ReturningFromA001AndA002ReseedsCurrentSymmetricSelectors() throws Exception {
        UiTestLifecycleExtension.onFx(() -> {
            try {
                for (LanguagePreference language : List.of(LanguagePreference.ES, LanguagePreference.EN)) {
                    I18nService.getInstance().setPreference(language);
                    for (double width : List.of(1800.0, 900.0)) {
                        var loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
                        Parent root = loader.load(); ModernMainController shell = loader.getController();
                        Stage owner = new Stage(); owner.setScene(new Scene(root, width, 850)); owner.show(); root.applyCss(); root.layout();
                        shell.navigateTo("Symmetric Ciphers"); Object cipher = field(shell, "cipherController");
                        @SuppressWarnings("unchecked") javafx.scene.control.ComboBox<String> algorithm = field(cipher, "symmetricAlgorithmCombo");
                        @SuppressWarnings("unchecked") javafx.scene.control.ComboBox<String> mode = field(cipher, "cipherModeCombo");
                        TextField key = field(cipher, "symmetricKeyField"); TextArea input = field(cipher, "cipherInputArea");
                        TextArea output = field(cipher, "cipherOutputArea");
                        key.setText("000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F");
                        input.setText("R2_AUX_INPUT_SENTINEL"); output.setText("R2_AUX_OUTPUT_SENTINEL");
                        mode.setValue("CBC"); algorithm.setValue("ChaCha20-Poly1305");
                        CheckMenuItem helpMode = field(shell, "contextualHelpModeMenuItem");
                        helpMode.setSelected(true); shell.handleToggleContextualHelpMode();
                        ContextualHelpTestSupport.shiftF1(symHelp(cipher, "symModeHelpBtn"));
                        ContextualHelpTestSupport.shiftF1(field(shell, "inputFormatCombo"));
                        assertEquals("aux_global_formats", ((HelpContextState) field(shell, "contextualHelpState")).current().operationId());
                        shell.handleShowOperationHelp();
                        HelpContextState state = field(shell, "contextualHelpState");
                        assertHelpSelection(state, "chacha20_poly1305", "cbc", "manual_input", true);
                        algorithm.setValue("AES-192");
                        ContextualHelpTestSupport.shiftF1(field(shell, "inputFormatCombo"));
                        shell.handleShowOperationHelp();
                        assertHelpSelection(state, "aes_192", "cbc", "manual_input", true);
                        ContextualHelpTestSupport.shiftF1(symHelp(cipher, "symDecryptHelpBtn"));
                        assertHelpSelection(state, "aes_192", "cbc", "manual_input", true);

                        algorithm.setValue("ChaCha20-Poly1305");
                        assertFalse(shell.checkPreflightReadiness("Symmetric Ciphers", false));
                        ((Button) field(shell, "errorBannerHelpBtn")).fire();
                        assertEquals("aux_preflight_error", state.current().operationId());
                        algorithm.setValue("AES-192");
                        shell.handleShowOperationHelp();
                        assertHelpSelection(state, "aes_192", "cbc", "manual_input", true);
                        ContextualHelpTestSupport.shiftF1(symHelp(cipher, "symDecryptHelpBtn"));
                        assertHelpSelection(state, "aes_192", "cbc", "manual_input", true);
                        assertEquals("R2_AUX_INPUT_SENTINEL", input.getText());
                        assertEquals("R2_AUX_OUTPUT_SENTINEL", output.getText());
                        assertEquals("000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F", key.getText());
                        assertEquals(13, ((HelpContextAdapter) field(shell, "cipherHelpContextAdapter")).bindingCount());
                        helpMode.setSelected(false); shell.handleToggleContextualHelpMode();
                        shell.shutdown(); owner.close(); owner.setScene(null);
                    }
                }
            } catch (Exception error) { throw new AssertionError(error); }
        });
    }

    @Test void b01FormatDescriptionsMatchSymmetricAndCodecConsumerPaths() throws Exception {
        byte[] bytes = new byte[]{0x41, 0x42};
        assertArrayEquals(bytes, SymmetricCipherCoordinator.parseInputData("41 42", "Hexadecimal"));
        assertThrows(IllegalArgumentException.class,
                () -> SymmetricCipherCoordinator.parseInputData("41:42", "Hexadecimal"));
        assertArrayEquals(bytes, SymmetricCipherCoordinator.parseInputData("01000001 01000010", "Binary"));
        assertThrows(IllegalArgumentException.class,
                () -> SymmetricCipherCoordinator.parseInputData("01000001:01000010", "Binary"));
        assertArrayEquals(new byte[]{0x41}, SymmetricCipherCoordinator.parseInputData("QQ==", "Base64"));
        assertThrows(IllegalArgumentException.class, () -> SymmetricCipherCoordinator.parseInputData(
                "-----BEGIN DATA-----\nQQ==\n-----END DATA-----", "Base64"));
        assertEquals("\uFFFD", SymmetricCipherCoordinator.formatOutputData(new byte[]{(byte) 0xff}, "Text (UTF-8)"));

        assertArrayEquals(bytes, GenericController.parseInput("41:42", "Hexadecimal"));
        assertArrayEquals(bytes, GenericController.parseInput("01000001:01000010", "Binary"));
        assertArrayEquals(new byte[]{0x41}, GenericController.parseInput(
                "-----BEGIN DATA-----\nQQ==\n-----END DATA-----", "Base64"));
        assertThrows(RuntimeException.class, () -> GenericController.formatBytes(new byte[]{(byte) 0xff}, "Text (UTF-8)"));

        UiTestLifecycleExtension.onFx(() -> {
            try {
                javafx.scene.control.TextArea authInput = new javafx.scene.control.TextArea("QQ==");
                javafx.scene.control.ComboBox<String> authInputFormat = new javafx.scene.control.ComboBox<>();
                authInputFormat.setValue("Base64");
                assertArrayEquals(new byte[]{0x41}, AuthenticationDataFormatter.read(authInput, authInputFormat, null));
                authInput.setText("-----BEGIN DATA-----\nQQ==\n-----END DATA-----");
                assertThrows(IllegalArgumentException.class,
                        () -> AuthenticationDataFormatter.read(authInput, authInputFormat, null));
                javafx.scene.control.TextArea authOutput = new javafx.scene.control.TextArea();
                javafx.scene.control.ComboBox<String> authOutputFormat = new javafx.scene.control.ComboBox<>();
                authOutputFormat.setValue("Text (UTF-8)");
                AuthenticationDataFormatter.write(authOutput, authOutputFormat, new byte[]{(byte) 0xff}, null);
                assertEquals("\uFFFD", authOutput.getText());
                for (LanguagePreference language : List.of(LanguagePreference.ES, LanguagePreference.EN)) {
                    I18nService.getInstance().setPreference(language);
                    for (double width : List.of(1800.0, 900.0)) {
                        var loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
                        Parent root = loader.load(); ModernMainController shell = loader.getController();
                        Stage owner = new Stage(); owner.setScene(new Scene(root, width, 850)); owner.show(); root.applyCss(); root.layout();
                        shell.navigateTo("Symmetric Ciphers");
                        @SuppressWarnings("unchecked") javafx.scene.control.ComboBox<String> inputFormat = field(shell, "inputFormatCombo");
                        @SuppressWarnings("unchecked") javafx.scene.control.ComboBox<String> outputFormat = field(shell, "outputFormatCombo");
                        inputFormat.setValue("Hexadecimal"); ContextualHelpTestSupport.shiftF1(field(shell, "inputFormatCombo"));
                        VBox help = currentHelp(shell);
                        String symHex = I18nService.getInstance().text("help.format.profile.symmetric_ciphers.option.hexadecimal.input");
                        assertContainsText(help, symHex);
                        assertFalse(help.getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast)
                                .anyMatch(label -> label.getText().contains(I18nService.getInstance().text("help.format.option.hexadecimal.input"))));
                        inputFormat.setValue("Base64"); ContextualHelpTestSupport.shiftF1(field(shell, "inputFormatCombo"));
                        assertContainsText(currentHelp(shell), I18nService.getInstance().text(
                                "help.format.profile.symmetric_ciphers.option.base64.input"));
                        outputFormat.setValue("Text (UTF-8)"); ContextualHelpTestSupport.shiftF1(field(shell, "outputFormatCombo"));
                        assertContainsText(currentHelp(shell), I18nService.getInstance().text(
                                "help.format.profile.symmetric_ciphers.option.text_utf_8.output"));

                        shell.navigateTo("Hashing"); inputFormat.setValue("Hexadecimal");
                        ContextualHelpTestSupport.shiftF1(field(shell, "inputFormatCombo"));
                        assertContainsText(currentHelp(shell), I18nService.getInstance().text("help.format.option.hexadecimal.input"));
                        inputFormat.setValue("Base64"); ContextualHelpTestSupport.shiftF1(field(shell, "inputFormatCombo"));
                        assertContainsText(currentHelp(shell), I18nService.getInstance().text("help.format.option.base64.input"));
                        shell.shutdown(); owner.close(); owner.setScene(null);
                    }
                }
            } catch (Exception error) { throw new AssertionError(error); }
        });
    }

    @Test void b01ManualBase64UrlHelpMatchesStandardConvertAcceptanceInBothLocales() throws Exception {
        UiTestLifecycleExtension.onFx(() -> {
            try {
                for (LanguagePreference language : List.of(LanguagePreference.ES, LanguagePreference.EN)) {
                    I18nService.getInstance().setPreference(language);
                    var loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
                    Parent root = loader.load();
                    ModernMainController shell = loader.getController();
                    Stage owner = new Stage();
                    owner.setScene(new Scene(root, 1400, 850)); owner.show(); root.applyCss(); root.layout();
                    shell.navigateTo("Manual Conversion");

                    @SuppressWarnings("unchecked") javafx.scene.control.ComboBox<String> inputFormat = field(shell, "inputFormatCombo");
                    @SuppressWarnings("unchecked") javafx.scene.control.ComboBox<String> outputFormat = field(shell, "outputFormatCombo");
                    GenericController generic = field(shell, "genericContainerController");
                    TextArea input = field(generic, "manualInputArea");
                    TextArea output = field(generic, "manualOutputArea");
                    CheckBox ebcdic = field(generic, "ebcdicConversionCheck");
                    inputFormat.setValue("Base64URL");
                    outputFormat.setValue("Hexadecimal");
                    assertEquals("Base64URL", ((javafx.scene.control.ComboBox<?>) field(generic, "manualInputFormatCombo")).getValue());
                    assertEquals("Hexadecimal", ((javafx.scene.control.ComboBox<?>) field(generic, "manualOutputFormatCombo")).getValue());
                    assertFalse(ebcdic.isSelected());

                    ContextualHelpTestSupport.shiftF1(field(shell, "inputFormatCombo"));
                    VBox help = currentHelp(shell);
                    String specific = I18nService.getInstance().text(
                            "help.format.profile.manual_conversion.option.base64url.input");
                    assertContainsText(help, specific);
                    String genericDescription = I18nService.getInstance().text("help.format.option.base64url.input");
                    assertFalse(help.getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast)
                            .anyMatch(label -> label.getText().contains(genericDescription)));

                    for (String accepted : List.of("QQ==", "Q Q==", "QQ", "Q Q")) {
                        input.setText(accepted);
                        generic.handleManualConvert();
                        assertEquals("41", output.getText(), language + " / " + accepted);
                    }
                    input.setText("?Q");
                    generic.handleManualConvert();
                    assertEquals("", output.getText(), language + " / invalid Base64URL must not produce output");

                    // The dedicated Base64URL decode action goes through the strict shared codec.
                    input.setText("Q Q==");
                    generic.handleDecodeBase64Url();
                    assertEquals("", output.getText(), language + " / strict codec action rejects whitespace");
                    shell.shutdown(); owner.close(); owner.setScene(null);
                }
            } catch (Exception error) { throw new AssertionError(error); }
        });
    }

    @Test void b01FieldHelpTracksPublicCipherSelectionsAndGlobalFormatProfilesWithoutReadingFields() throws Exception {
        AtomicReference<ModernMainController> shellRef = new AtomicReference<>();
        AtomicReference<Stage> ownerRef = new AtomicReference<>();
        UiTestLifecycleExtension.onFx(() -> {
            try {
                I18nService.getInstance().setPreference(LanguagePreference.ES);
                var loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
                Parent root = loader.load();
                ModernMainController shell = loader.getController();
                Stage owner = new Stage(); owner.setScene(new Scene(root, 1500, 900)); owner.show(); root.applyCss(); root.layout();
                shell.navigateTo("Symmetric Ciphers");
                Object cipher = field(shell, "cipherController");
                TextField key = field(cipher, "symmetricKeyField");
                TextArea input = field(cipher, "cipherInputArea");
                TextArea output = field(cipher, "cipherOutputArea");
                key.setText("B01_SECRET_SENTINEL"); input.setText("B01_PAYLOAD_SENTINEL"); output.setText("B01_RESULT_SENTINEL");
                ((CheckMenuItem) field(shell, "contextualHelpModeMenuItem")).setSelected(true);
                shell.handleToggleContextualHelpMode();
                javafx.scene.Node algorithmHelp = symHelp(cipher, "symAlgorithmHelpBtn");
                assertTrue(algorithmHelp.isVisible());
                ContextualHelpTestSupport.shiftF1(algorithmHelp);
                VBox help = currentHelp(shell);
                assertContainsText(help, I18nService.getInstance().text("help.field.op_sym_ciphers.algorithm.label"));
                assertContainsText(help, I18nService.getInstance().text("help.option.op_sym_ciphers.algorithm.aes_256"));
                HelpContextState state = field(shell, "contextualHelpState");
                assertEquals("op_sym_ciphers", state.current().operationId());
                String publicContext = state.current().toString();
                assertFalse(publicContext.contains(key.getText()));
                assertFalse(publicContext.contains(input.getText()));
                assertFalse(publicContext.contains(output.getText()));

                @SuppressWarnings("unchecked") javafx.scene.control.ComboBox<String> algorithm = field(cipher, "symmetricAlgorithmCombo");
                algorithm.setValue("Salsa20");
                assertContainsText(currentHelp(shell), I18nService.getInstance().text("help.rule.symmetric.mode.stream_disabled"));
                assertTrue(((javafx.scene.control.ComboBox<?>) field(cipher, "cipherModeCombo")).isDisabled());
                ContextualHelpTestSupport.shiftF1(symHelp(cipher, "symDecryptHelpBtn"));
                assertEquals("decrypt", state.current().actionId());
                assertContainsText(currentHelp(shell), I18nService.getInstance().text("help.action.op_sym_ciphers.decrypt.description"));
                assertEquals("B01_SECRET_SENTINEL", key.getText());
                assertEquals("B01_PAYLOAD_SENTINEL", input.getText());
                assertEquals("B01_RESULT_SENTINEL", output.getText());

                shell.navigateTo("Hashing");
                @SuppressWarnings("unchecked") javafx.scene.control.ComboBox<String> inputFormat = field(shell, "inputFormatCombo");
                inputFormat.setValue("Hexadecimal");
                ContextualHelpTestSupport.shiftF1(field(shell, "inputFormatCombo"));
                assertEquals("aux_global_formats", state.current().operationId());
                assertContainsText(currentHelp(shell), "Hexadecimal");
                assertContainsText(currentHelp(shell), I18nService.getInstance().text("help.format.option.hexadecimal.input"));

                var jwe = com.cryptocarver.model.OperationRegistry.getInstance().getById("op_jose_jwe").orElseThrow();
                shell.navigateTo(jwe.getNavigationPath());
                ContextualHelpTestSupport.shiftF1(field(shell, "inputFormatCombo"));
                assertEquals("op_jose_jwe", state.current().selections().get(HelpContext.Selection.PROFILE).code());
                assertContainsText(currentHelp(shell), I18nService.getInstance().text("help.format.disabled.jwe_encrypted"));
                shellRef.set(shell); ownerRef.set(owner);
            } catch (Exception error) { throw new AssertionError(error); }
        });
        UiTestLifecycleExtension.onFx(() -> { shellRef.get().shutdown(); ownerRef.get().close(); ownerRef.get().setScene(null); });
    }

    @Test void compactWindowTracksRouteActionLanguageAndShellLifecycle() throws Exception {
        AtomicReference<ModernMainController> shellRef = new AtomicReference<>();
        AtomicReference<Stage> ownerRef = new AtomicReference<>();
        UiTestLifecycleExtension.onFx(() -> {
            try {
                I18nService.getInstance().setPreference(LanguagePreference.ES);
                var loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
                Parent root = loader.load();
                ModernMainController shell = loader.getController();
                Stage owner = new Stage();
                owner.setScene(new Scene(root, 900, 700));
                owner.show(); root.applyCss(); root.layout();
                shell.navigateTo("Symmetric Ciphers");
                CheckMenuItem mode = field(shell, "contextualHelpModeMenuItem");
                mode.setSelected(true);
                shell.handleToggleContextualHelpMode();
                ContextualHelpWindowCoordinator windows = field(shell, "contextualHelpWindowCoordinator");
                assertTrue(windows.isShowing());
                Stage help = windows.windowForTesting();
                assertEquals(1, ownedHelpWindows(owner));
                VBox contents = helpContent(help);

                action(contents, "Cifrar").fire();
                assertContainsLabel(contents, I18nService.getInstance().text("help.action.op_sym_ciphers.encrypt.description"));
                assertContainsLabel(contents, I18nService.getInstance().text("help.action.op_sym_ciphers.encrypt.example"));
                assertContainsReference(contents, Locale.forLanguageTag("es"));
                VBox docked = field(shell, "contextualHelpContent");
                assertContainsLabel(docked, I18nService.getInstance().text("help.action.op_sym_ciphers.encrypt.description"));
                action(docked, "Descifrar").fire();
                assertContainsLabel(contents, I18nService.getInstance().text("help.action.op_sym_ciphers.decrypt.description"));
                action(contents, "Cifrar").fire();
                I18nService.getInstance().setPreference(LanguagePreference.EN);
                assertEquals("Help — Symmetric Ciphers", help.getTitle());
                assertContainsLabel(contents, I18nService.getInstance().text("help.action.op_sym_ciphers.encrypt.description"));
                assertContainsLabel(contents, I18nService.getInstance().text("help.action.op_sym_ciphers.encrypt.example"));
                assertContainsReference(contents, Locale.ENGLISH);
                assertContainsLabel(docked, I18nService.getInstance().text("help.operation.op_sym_ciphers.purpose"));
                I18nService.getInstance().setPreference(LanguagePreference.ES);
                assertEquals("Ayuda — Cifrados simétricos", help.getTitle());
                assertContainsLabel(contents, I18nService.getInstance().text("help.action.op_sym_ciphers.encrypt.description"));
                assertContainsLabel(contents, I18nService.getInstance().text("help.action.op_sym_ciphers.encrypt.example"));
                assertContainsReference(contents, Locale.forLanguageTag("es"));
                assertContainsLabel(docked, I18nService.getInstance().text("help.operation.op_sym_ciphers.purpose"));
                Button windowDecrypt = action(contents, "Descifrar");
                windowDecrypt.requestFocus();
                fireKey(windowDecrypt, KeyCode.ENTER, true);
                fireKey(windowDecrypt, KeyCode.ENTER, false);
                assertContainsLabel(contents, I18nService.getInstance().text("help.action.op_sym_ciphers.decrypt.description"));
                action(contents, "Cifrar").fire();

                shell.navigateTo("Hashing");
                String hashTitle = I18nService.getInstance().text("help.operation.op_gen_hash.title");
                assertEquals(hashTitle, ((Label) contents.getChildren().get(0)).getText());
                assertEquals("Ayuda — " + hashTitle, help.getTitle());
                shell.navigateTo("Symmetric Ciphers");
                assertEquals("Ayuda — Cifrados simétricos", help.getTitle());

                shell.handleShowOperationHelp();
                assertEquals(1, ownedHelpWindows(owner), "Repeated open should focus/reuse the same window");
                assertSame(help, windows.windowForTesting());

                // User close clears ownership; the next request makes one fresh window.
                help.close();
                assertFalse(windows.isShowing());
                shell.handleShowOperationHelp();
                assertTrue(windows.isShowing());
                assertEquals(1, ownedHelpWindows(owner));

                Stage reopened = windows.windowForTesting();
                owner.setScene(new Scene(new StackPane(), 900, 700));
                assertFalse(reopened.isShowing(), "Replacing the shell scene must close its help window");
                assertFalse(windows.isShowing());
                shellRef.set(shell); ownerRef.set(owner);
            } catch (Exception error) { throw new AssertionError(error); }
        });
        UiTestLifecycleExtension.onFx(() -> {
            shellRef.get().shutdown();
            ownerRef.get().close(); ownerRef.get().setScene(null);
        });
    }

    @Test void dockedSelectedActionSurvivesFirstCompactOpenAndReopenInBothLocales() throws Exception {
        AtomicReference<ModernMainController> shellRef = new AtomicReference<>();
        AtomicReference<Stage> ownerRef = new AtomicReference<>();
        UiTestLifecycleExtension.onFx(() -> {
            try {
                I18nService.getInstance().setPreference(LanguagePreference.ES);
                var loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
                Parent root = loader.load();
                ModernMainController shell = loader.getController();
                Stage owner = new Stage();
                owner.setScene(new Scene(root, 1400, 900));
                owner.show(); root.applyCss(); root.layout();
                shell.navigateTo("Symmetric Ciphers");
                shell.handleShowOperationHelp();
                VBox docked = field(shell, "contextualHelpContent");
                action(docked, "Descifrar").fire();
                assertContainsLabel(docked, I18nService.getInstance().text("help.action.op_sym_ciphers.decrypt.description"));

                root.applyCss(); root.layout();
                ((javafx.scene.layout.Region) field(shell, "mainPane")).resize(900, 700);
                shell.handleShowOperationHelp(); // First compact window creation.
                ContextualHelpWindowCoordinator windows = field(shell, "contextualHelpWindowCoordinator");
                Stage help = windows.windowForTesting();
                VBox compact = helpContent(help);
                assertContainsLabel(compact, I18nService.getInstance().text("help.action.op_sym_ciphers.decrypt.description"));
                assertContainsLabel(compact, I18nService.getInstance().text("help.action.op_sym_ciphers.decrypt.example"));
                help.close();
                shell.handleShowOperationHelp();
                compact = helpContent(windows.windowForTesting());
                assertContainsLabel(compact, I18nService.getInstance().text("help.action.op_sym_ciphers.decrypt.description"));

                I18nService.getInstance().setPreference(LanguagePreference.EN);
                assertContainsLabel(compact, I18nService.getInstance().text("help.action.op_sym_ciphers.decrypt.description"));
                assertContainsLabel(compact, I18nService.getInstance().text("help.action.op_sym_ciphers.decrypt.example"));
                help = windows.windowForTesting();
                help.close();
                shell.handleShowOperationHelp();
                compact = helpContent(windows.windowForTesting());
                assertContainsLabel(compact, I18nService.getInstance().text("help.action.op_sym_ciphers.decrypt.description"));
                assertContainsLabel(compact, I18nService.getInstance().text("help.action.op_sym_ciphers.decrypt.example"));
                shellRef.set(shell); ownerRef.set(owner);
            } catch (Exception error) { throw new AssertionError(error); }
        });
        UiTestLifecycleExtension.onFx(() -> {
            shellRef.get().shutdown(); ownerRef.get().close(); ownerRef.get().setScene(null);
        });
    }

    @Test void cipherHelpAdapterRegistersOnlyWhileItsDeferredRouteIsActive() throws Exception {
        AtomicReference<ModernMainController> shellRef = new AtomicReference<>();
        AtomicReference<Stage> ownerRef = new AtomicReference<>();
        UiTestLifecycleExtension.onFx(() -> {
            try {
                var loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
                Parent root = loader.load();
                ModernMainController shell = loader.getController();
                Stage owner = new Stage(); owner.setScene(new Scene(root, 1400, 900)); owner.show();
                shell.navigateTo("Symmetric Ciphers");
                HelpContextState state = field(shell, "contextualHelpState");
                HelpContextAdapter adapter = field(shell, "cipherHelpContextAdapter");
                var cipher = field(shell, "cipherController");
                var algorithm = cipher.getClass().getMethod("getSymmetricAlgorithmCombo").invoke(cipher);
                @SuppressWarnings("unchecked") javafx.scene.control.ComboBox<String> combo = (javafx.scene.control.ComboBox<String>) algorithm;
                assertTrue(adapter.isActive());
                assertEquals(13, adapter.bindingCount());
                String alternate = combo.getItems().stream().filter(value -> !value.equals(combo.getValue())).findFirst().orElseThrow();
                combo.setValue(alternate);
                assertEquals(alternate.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_.:-]+", "_"),
                        state.current().selections().get(HelpContext.Selection.ALGORITHM).code());

                shell.navigateTo("Hashing");
                assertFalse(adapter.isActive());
                combo.setValue("AES");
                assertNull(state.current().selections().get(HelpContext.Selection.ALGORITHM));
                shell.navigateTo("Symmetric Ciphers");
                assertTrue(adapter.isActive());
                assertEquals("AES", state.current().selections().get(HelpContext.Selection.ALGORITHM).code());
                shellRef.set(shell); ownerRef.set(owner);
            } catch (Exception error) { throw new AssertionError(error); }
        });
        UiTestLifecycleExtension.onFx(() -> {
            shellRef.get().shutdown(); ownerRef.get().close(); ownerRef.get().setScene(null);
        });
    }

    @Test void preflightHelpLinksExistingRuleAndGenericErrorsStayRedacted() throws Exception {
        AtomicReference<ModernMainController> shellRef = new AtomicReference<>();
        AtomicReference<Stage> ownerRef = new AtomicReference<>();
        UiTestLifecycleExtension.onFx(() -> {
            try {
                I18nService.getInstance().setPreference(LanguagePreference.ES);
                var loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
                Parent root = loader.load();
                ModernMainController shell = loader.getController();
                Stage owner = new Stage(); owner.setScene(new Scene(root, 1800, 900)); owner.show(); root.applyCss(); root.layout();
                shell.navigateTo("Symmetric Ciphers");
                @SuppressWarnings("unchecked") javafx.scene.control.ComboBox<String> format = field(shell, "inputFormatCombo");
                format.setValue("Hexadecimal");
                Object cipher = field(shell, "cipherController");
                ((TextArea) field(cipher, "cipherInputArea")).setText("NOT_HEX_SENTINEL");
                ((TextField) field(cipher, "symmetricKeyField")).setText("000102030405060708090A0B0C0D0E0F");

                assertFalse(shell.checkPreflightReadiness("Symmetric Ciphers", true));
                HelpContextState state = field(shell, "contextualHelpState");
                assertEquals("aux_preflight_error", state.current().operationId());
                assertEquals("preflight.input_format.blocked", state.current().selections().get(HelpContext.Selection.RULE_ID).code());
                assertEquals("cipherInputArea", state.current().fieldId());
                InlineErrorPresenter errors = field(shell, "inlineErrorPresenter");
                assertEquals("cipherInputArea", errors.getCurrentError().fieldKey());
                assertSame(field(cipher, "cipherInputArea"), field(errors, "currentErrorTarget"));
                VBox help = field(shell, "contextualHelpContent");
                ContextualHelpWindowCoordinator helpWindows = field(shell, "contextualHelpWindowCoordinator");
                if (helpWindows != null && helpWindows.isShowing()) help = helpContent(helpWindows.windowForTesting());
                assertContainsText(help, I18nService.getInstance().text("help.preflight.rule.preflight.input_format.blocked"));
                assertContainsText(help, "BLOCKED");
                assertContainsText(help, I18nService.getInstance().text("preflight.remedy.input"));
                assertFalse(help.getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast)
                        .anyMatch(label -> label.getText().contains("NOT_HEX_SENTINEL")));

                shell.showError(new UserFacingError("Synthetic", "RAW_DIAGNOSTIC_SENTINEL", "Generic remedy"));
                Button errorHelp = field(shell, "errorBannerHelpBtn"); errorHelp.fire();
                assertTrue(state.current().selections().get(HelpContext.Selection.RULE_ID) == null);
                assertContainsLabel(help, I18nService.getInstance().text("help.preflight.noRule"));
                assertFalse(help.getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast)
                        .anyMatch(label -> label.getText().contains("RAW_DIAGNOSTIC_SENTINEL")));
                I18nService.getInstance().setPreference(LanguagePreference.EN);
                assertContainsLabel(help, I18nService.getInstance().text("help.preflight.noRule"));
                shellRef.set(shell); ownerRef.set(owner);
            } catch (Exception error) { throw new AssertionError(error); }
        });
        UiTestLifecycleExtension.onFx(() -> {
            shellRef.get().shutdown(); ownerRef.get().close(); ownerRef.get().setScene(null);
        });
    }

    @Test void errorBannerHelpRetainsItsOwnRuleAcrossLocalesLayoutsAndGeneralHelp() throws Exception {
        UiTestLifecycleExtension.onFx(() -> {
            try {
              for (LanguagePreference language : List.of(LanguagePreference.ES, LanguagePreference.EN)) {
                for (boolean compact : List.of(false, true)) {
                    I18nService.getInstance().setPreference(language);
                    var loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
                    Parent root = loader.load();
                    ModernMainController shell = loader.getController();
                    Stage owner = new Stage();
                    owner.setScene(new Scene(root, compact ? 900 : 1800, 900));
                    owner.show(); root.applyCss(); root.layout();
                    shell.navigateTo("Symmetric Ciphers");
                    @SuppressWarnings("unchecked") javafx.scene.control.ComboBox<String> format = field(shell, "inputFormatCombo");
                    format.setValue("Hexadecimal");
                    Object cipher = field(shell, "cipherController");
                    TextArea input = field(cipher, "cipherInputArea");
                    TextField key = field(cipher, "symmetricKeyField");
                    TextArea output = field(cipher, "cipherOutputArea");
                    input.setText("NOT_HEX_SENTINEL");
                    key.setText("000102030405060708090A0B0C0D0E0F");
                    output.setText("PRESERVED_OUTPUT_SENTINEL");
                    assertFalse(shell.checkPreflightReadiness("Symmetric Ciphers", true));

                    HelpContextState state = field(shell, "contextualHelpState");
                    Button bannerHelp = field(shell, "errorBannerHelpBtn");
                    bannerHelp.fire();
                    assertEquals("preflight.input_format.blocked",
                            state.current().selections().get(HelpContext.Selection.RULE_ID).code());
                    assertEquals("BLOCKED", state.current().selections().get(HelpContext.Selection.PREFLIGHT_STATUS).code());
                    VBox help = currentHelp(shell);
                    assertEquals(I18nService.getInstance().text("help.operation.aux_preflight_error.title"),
                            ((Label) help.getChildren().get(0)).getText());
                    assertContainsText(help, I18nService.getInstance().text("help.preflight.rule.preflight.input_format.blocked"));
                    assertContainsText(help, I18nService.getInstance().text("help.preflight.field.payload"));
                    assertFalse(help.getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast)
                            .anyMatch(label -> label.getText().equals("cipherInputArea")));

                    shell.navigateTo("Hashing");
                    shell.handleShowOperationHelp();
                    assertEquals("op_gen_hash", state.current().operationId());
                    ContextualHelpCoordinator activeHelp = field(shell, "contextualHelpCoordinator");
                    assertEquals(activeHelp.operationTitle(),
                            ((Label) currentHelp(shell).getChildren().get(0)).getText());
                    if (compact) {
                        ContextualHelpWindowCoordinator windows = field(shell, "contextualHelpWindowCoordinator");
                        ContextualHelpCoordinator dockedHelp = field(shell, "contextualHelpCoordinator");
                        assertEquals(I18nService.getInstance().text("help.tab.help") + " — "
                                + dockedHelp.operationTitle(),
                                windows.windowForTesting().getTitle());
                    }
                    bannerHelp.fire();
                    assertEquals("preflight.input_format.blocked",
                            state.current().selections().get(HelpContext.Selection.RULE_ID).code());
                    assertEquals(I18nService.getInstance().text("help.operation.aux_preflight_error.title"),
                            ((Label) currentHelp(shell).getChildren().get(0)).getText());
                    assertContainsText(currentHelp(shell), I18nService.getInstance().text("help.preflight.rule.preflight.input_format.blocked"));
                    if (compact) {
                        ContextualHelpWindowCoordinator windows = field(shell, "contextualHelpWindowCoordinator");
                        assertEquals(I18nService.getInstance().text("help.tab.help") + " — "
                                + I18nService.getInstance().text("help.operation.aux_preflight_error.title"),
                                windows.windowForTesting().getTitle());
                    }

                    shell.showError(new UserFacingError("Synthetic", "RAW_NEW_ERROR_SENTINEL", "Try again"));
                    bannerHelp.fire();
                    assertNull(state.current().selections().get(HelpContext.Selection.RULE_ID));
                    assertNull(state.current().selections().get(HelpContext.Selection.PREFLIGHT_STATUS));
                    assertContainsLabel(currentHelp(shell), I18nService.getInstance().text("help.preflight.noRule"));
                    assertEquals(I18nService.getInstance().text("help.operation.aux_preflight_error.title"),
                            ((Label) currentHelp(shell).getChildren().get(0)).getText());
                    assertFalse(currentHelp(shell).getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast)
                            .anyMatch(label -> label.getText().contains("RAW_NEW_ERROR_SENTINEL")));
                    ((Button) field(shell, "errorBannerCloseBtn")).fire();
                    assertNull(field(shell, "bannerHelpContext"), "Closing the banner must release its associated help context");
                    assertEquals("NOT_HEX_SENTINEL", input.getText());
                    assertEquals("000102030405060708090A0B0C0D0E0F", key.getText());
                    assertEquals("PRESERVED_OUTPUT_SENTINEL", output.getText());
                    shell.shutdown(); owner.close(); owner.setScene(null);
                }
              }
            } catch (Exception error) { throw new AssertionError(error); }
        });
    }

    @Test void readinessHelpOpensRealReadyAndWarningChecksWithoutExecuting() throws Exception {
        UiTestLifecycleExtension.onFx(() -> {
            try {
              for (String mode : List.of("GCM", "CBC")) {
                I18nService.getInstance().setPreference(LanguagePreference.ES);
                var loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
                Parent root = loader.load();
                ModernMainController shell = loader.getController();
                Stage owner = new Stage(); owner.setScene(new Scene(root, 1800, 900)); owner.show(); root.applyCss(); root.layout();
                shell.navigateTo("Symmetric Ciphers");
                Object cipher = field(shell, "cipherController");
                @SuppressWarnings("unchecked") javafx.scene.control.ComboBox<String> algorithm = field(cipher, "symmetricAlgorithmCombo");
                @SuppressWarnings("unchecked") javafx.scene.control.ComboBox<String> cipherMode = field(cipher, "cipherModeCombo");
                @SuppressWarnings("unchecked") javafx.scene.control.ComboBox<String> format = field(shell, "inputFormatCombo");
                algorithm.setValue("AES-256");
                cipherMode.setValue(mode);
                format.setValue("Text (UTF-8)");
                ((TextArea) field(cipher, "cipherInputArea")).setText("READINESS_HELP_FIXTURE");
                ((TextField) field(cipher, "symmetricKeyField")).setText("000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F");
                ((TextField) field(cipher, "ivField")).setText("000102030405060708090A0B");
                ((TextArea) field(cipher, "cipherOutputArea")).setText("OUTPUT_MUST_NOT_CHANGE");

                javafx.scene.control.MenuItem toolbarHelp = field(shell, "readinessHelpMenuItem");
                toolbarHelp.fire();
                var report = (com.cryptocarver.model.PreflightReport) field(shell, "currentPreflightReport");
                assertNotNull(report);
                boolean warning = "CBC".equals(mode);
                assertEquals(warning ? com.cryptocarver.model.PreflightStatus.WARNING : com.cryptocarver.model.PreflightStatus.READY,
                        report.getOverallStatus());
                assertTrue(report.isExecutable());
                HelpContextState state = field(shell, "contextualHelpState");
                String expectedRule = warning ? "preflight.security_policy.warning" : "preflight.input_data.ready";
                assertEquals(expectedRule, state.current().selections().get(HelpContext.Selection.RULE_ID).code());
                assertEquals(warning ? "WARNING" : "READY",
                        state.current().selections().get(HelpContext.Selection.PREFLIGHT_STATUS).code());
                assertContainsText(currentHelp(shell), I18nService.getInstance().text("help.preflight.rule." + expectedRule));
                assertEquals("OUTPUT_MUST_NOT_CHANGE", ((TextArea) field(cipher, "cipherOutputArea")).getText());

                javafx.scene.layout.FlowPane checkRows = field(shell, "readinessChecksContainer");
                javafx.scene.layout.HBox selectedRow = checkRows.getChildren().stream()
                        .filter(javafx.scene.layout.HBox.class::isInstance)
                        .map(javafx.scene.layout.HBox.class::cast)
                        .filter(row -> !warning || ((Button) row.getChildren().get(0)).getText().startsWith("⚠️"))
                        .findFirst().orElseThrow();
                ((Button) selectedRow.getChildren().get(1)).fire();
                assertEquals(expectedRule, state.current().selections().get(HelpContext.Selection.RULE_ID).code());
                assertContainsText(currentHelp(shell), I18nService.getInstance().text("help.preflight.rule." + expectedRule));
                ((Button) selectedRow.getChildren().get(0)).fire();
                assertEquals(I18nService.getInstance().text("readiness.check.focus.help"),
                        ((Button) selectedRow.getChildren().get(0)).getAccessibleHelp());

                Button panelHelp = field(shell, "readinessHelpBtn");
                panelHelp.fire();
                assertEquals(expectedRule, state.current().selections().get(HelpContext.Selection.RULE_ID).code());
                assertTrue(report.isExecutable(), "Help access must not change readiness/executability");
                I18nService.getInstance().setPreference(LanguagePreference.EN);
                assertContainsText(currentHelp(shell), I18nService.getInstance().text("help.preflight.rule." + expectedRule));
                assertEquals("READINESS_HELP_FIXTURE", ((TextArea) field(cipher, "cipherInputArea")).getText());
                assertEquals("OUTPUT_MUST_NOT_CHANGE", ((TextArea) field(cipher, "cipherOutputArea")).getText());
                shell.shutdown(); owner.close(); owner.setScene(null);
              }
            } catch (Exception error) { throw new AssertionError(error); }
        });
    }

    @Test void missingAeadTagAndWeakDigestHaveSpecificLocalizedRuleHelp() throws Exception {
        UiTestLifecycleExtension.onFx(() -> {
            try {
              for (LanguagePreference language : List.of(LanguagePreference.ES, LanguagePreference.EN)) {
                I18nService.getInstance().setPreference(language);
                var loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
                Parent root = loader.load();
                ModernMainController shell = loader.getController();
                Stage owner = new Stage(); owner.setScene(new Scene(root, 1800, 900)); owner.show(); root.applyCss(); root.layout();
                shell.navigateTo("Symmetric Ciphers");
                Object cipher = field(shell, "cipherController");
                @SuppressWarnings("unchecked") javafx.scene.control.ComboBox<String> algorithm = field(cipher, "symmetricAlgorithmCombo");
                @SuppressWarnings("unchecked") javafx.scene.control.ComboBox<String> mode = field(cipher, "cipherModeCombo");
                @SuppressWarnings("unchecked") javafx.scene.control.ComboBox<String> format = field(shell, "inputFormatCombo");
                TextArea cipherInput = field(cipher, "cipherInputArea");
                TextField key = field(cipher, "symmetricKeyField");
                TextField nonce = field(cipher, "ivField");
                TextArea output = field(cipher, "cipherOutputArea");
                algorithm.setValue("AES-256"); mode.setValue("GCM"); format.setValue("Text (UTF-8)");
                cipherInput.setText("TAG_HELP_FIXTURE");
                key.setText("000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F");
                nonce.setText("000102030405060708090A0B");
                output.setText("TAG_HELP_OUTPUT_SENTINEL");
                assertFalse(shell.checkPreflightReadiness("Symmetric Ciphers", false));
                Button bannerHelp = field(shell, "errorBannerHelpBtn"); bannerHelp.fire();
                HelpContextState state = field(shell, "contextualHelpState");
                assertEquals("preflight.aead_auth_tag.incomplete",
                        state.current().selections().get(HelpContext.Selection.RULE_ID).code());
                assertEquals("INCOMPLETE", state.current().selections().get(HelpContext.Selection.PREFLIGHT_STATUS).code());
                assertEquals("gcmTagField", state.current().fieldId());
                assertContainsText(currentHelp(shell), I18nService.getInstance().text("help.preflight.rule.preflight.aead_auth_tag.incomplete"));
                assertContainsText(currentHelp(shell), I18nService.getInstance().text("help.preflight.field.tag"));
                assertFalse(currentHelp(shell).getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast)
                        .anyMatch(label -> label.getText().contains("TAG_HELP_FIXTURE") || label.getText().contains(key.getText()) || label.getText().contains(nonce.getText())));
                assertEquals("TAG_HELP_OUTPUT_SENTINEL", output.getText());

                algorithm.setValue("ChaCha20");
                nonce.clear();
                assertFalse(shell.checkPreflightReadiness("Symmetric Ciphers", true));
                bannerHelp.fire();
                assertEquals("preflight.nonce.incomplete",
                        state.current().selections().get(HelpContext.Selection.RULE_ID).code());
                assertContainsText(currentHelp(shell), I18nService.getInstance().text("help.preflight.field.nonce"));

                shell.navigateTo("Hashing");
                Object generic = field(shell, "genericContainerController");
                TextArea hashInput = field(generic, "hashInputArea");
                @SuppressWarnings("unchecked") javafx.scene.control.ComboBox<String> digest = field(generic, "hashAlgorithmCombo");
                hashInput.setText("DIGEST_HELP_FIXTURE"); digest.setValue("MD5");
                javafx.scene.control.MenuItem readinessHelp = field(shell, "readinessHelpMenuItem"); readinessHelp.fire();
                var report = (com.cryptocarver.model.PreflightReport) field(shell, "currentPreflightReport");
                assertEquals(com.cryptocarver.model.PreflightStatus.WARNING, report.getOverallStatus());
                assertTrue(report.isExecutable());
                assertEquals("preflight.digest_security.warning",
                        state.current().selections().get(HelpContext.Selection.RULE_ID).code());
                assertEquals("WARNING", state.current().selections().get(HelpContext.Selection.PREFLIGHT_STATUS).code());
                assertEquals("hashAlgorithmCombo", state.current().fieldId());
                assertContainsText(currentHelp(shell), I18nService.getInstance().text("help.preflight.rule.preflight.digest_security.warning"));
                assertContainsText(currentHelp(shell), I18nService.getInstance().text("help.preflight.field.digestAlgorithm"));
                assertEquals("DIGEST_HELP_FIXTURE", hashInput.getText());
                assertTrue(report.isExecutable(), "Opening help must preserve executability");
                shell.shutdown(); owner.close(); owner.setScene(null);
              }
            } catch (Exception error) { throw new AssertionError(error); }
        });
    }

    /** The control that used to carry a symmetric "?" button, resolved by its help binding. */
    private static javafx.scene.Node symHelp(Object cipher, String legacyButton) throws Exception {
        String binding = switch (legacyButton) {
            case "symAlgorithmHelpBtn" -> "encrypt_algorithm";
            case "symModeHelpBtn" -> "encrypt_mode";
            case "symIvHelpBtn" -> "encrypt_iv_nonce";
            case "symDecryptHelpBtn" -> "decrypt_action";
            case "symInspectKeyHelpBtn" -> "inspect_key_manual_key";
            case "symKeyMaterialHelpBtn" -> "encrypt_manual_key";
            case "symKeyLabKeyHelp" -> "encrypt_key_lab_key";
            default -> throw new IllegalArgumentException(legacyButton);
        };
        javafx.scene.Node anchor = field(cipher, "cipherRoot");
        javafx.scene.Node control = ContextualHelpTestSupport.control(anchor.getScene().getRoot(), "contextHelp_op_sym_ciphers_" + binding);
        assertNotNull(control, "Missing help control " + binding);
        return control;
    }
    private static void assertFocusDoesNotPublish(ModernMainController shell, javafx.scene.Node control) throws Exception {
        HelpContextState state = field(shell, "contextualHelpState");
        HelpContext before = state.current();
        control.requestFocus();
        assertEquals(before, state.current(), "Focus must not publish help while the mode is off");
    }
    private static void assertShiftF1Inert(ModernMainController shell, javafx.scene.Node control) throws Exception {
        HelpContextState state = field(shell, "contextualHelpState");
        HelpContext before = state.current();
        ContextualHelpTestSupport.shiftF1(control);
        assertEquals(before, state.current(), "Help controls are inert outside their route");
    }
    private static Button action(VBox content, String text) {
        return content.getChildren().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> text.equals(button.getText())).findFirst().orElseThrow();
    }

    private static VBox helpContent(Stage stage) {
        return (VBox) ((ScrollPane) stage.getScene().getRoot()).getContent();
    }

    private static VBox currentHelp(ModernMainController shell) {
        ContextualHelpWindowCoordinator windows = field(shell, "contextualHelpWindowCoordinator");
        return windows != null && windows.isShowing()
                ? helpContent(windows.windowForTesting()) : field(shell, "contextualHelpContent");
    }

    private static long ownedHelpWindows(Stage owner) {
        return Window.getWindows().stream().filter(Stage.class::isInstance).map(Stage.class::cast)
                .filter(stage -> stage != owner && stage.isShowing()).count();
    }

    private static void assertContainsLabel(VBox content, String text) {
        assertTrue(content.getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast)
                .anyMatch(label -> text.equals(label.getText())), "Missing help text: " + text);
    }

    private static long exactLabelCount(VBox content, String expected) {
        return content.getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast)
                .filter(label -> expected.equals(label.getText())).count();
    }

    private static void assertHelpSelection(HelpContextState state, String algorithm, String mode,
                                            String keySource, boolean materialPresent) {
        assertEquals(algorithm, state.current().selections().get(HelpContext.Selection.ALGORITHM).code());
        assertEquals(mode, state.current().selections().get(HelpContext.Selection.MODE).code());
        assertEquals(keySource, state.current().selections().get(HelpContext.Selection.KEY_SOURCE).code());
        assertEquals(Boolean.toString(materialPresent),
                state.current().selections().get(HelpContext.Selection.MATERIAL_PRESENT).code());
    }

    private static void assertContainsText(VBox content, String text) {
        assertTrue(content.getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast)
                .anyMatch(label -> label.getText().contains(text)), "Missing help text: " + text);
    }

    private static void assertContainsReference(VBox content, Locale locale) {
        String reference = new LocalHelpService().readReference("cipher.symmetric", locale).orElseThrow().strip();
        assertContainsLabel(content, reference);
    }

    private static void fireKey(Button button, KeyCode code, boolean pressed) {
        KeyEvent event = new KeyEvent(pressed ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED,
                "", "", code, false, false, false, false);
        Event.fireEvent(button, event);
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object object, String name) {
        try {
            Class<?> type = object.getClass();
            while (type != null) {
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    return (T) field.get(object);
                } catch (NoSuchFieldException ignored) { type = type.getSuperclass(); }
            }
            throw new NoSuchFieldException(name);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
}
