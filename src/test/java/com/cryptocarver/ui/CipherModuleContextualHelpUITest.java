package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.help.HelpContext;
import com.cryptocarver.model.help.HelpContextState;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.model.help.OperationHelpRegistry;
import com.cryptocarver.model.help.OptionHelp;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named="runUiTests", matches="true")
class CipherModuleContextualHelpUITest {
    @BeforeAll static void startFx() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); } catch (IllegalStateException alreadyStarted) { ready.countDown(); }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
    }

    @Test void fpeFileAndRsaHelpFollowTheirRouteAndKeepFreeTextOutOfContext() throws Exception {
        UiTestLifecycleExtension.onFx(() -> {
            AppSettings original = AppSettings.getInstance();
            java.nio.file.Path isolated = null;
            Stage stage = null;
            try {
                isolated = Files.createTempDirectory("cryptocarver-b04-help");
                AppSettings.setInstanceForTesting(new AppSettings(isolated.resolve("settings.json")));
                com.cryptocarver.service.I18nService.getInstance().setPreference(LanguagePreference.EN);
                var loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
                Parent root = loader.load(); stage = new Stage(); stage.setScene(new Scene(root, 1500, 900)); stage.show(); root.applyCss(); root.layout();
                ModernMainController shell = loader.getController();
                shell.navigateTo("Format-Preserving Encryption");
                CipherController cipher = field(shell, "cipherContainerController");
                CheckMenuItem mode = field(shell, "contextualHelpModeMenuItem");
                mode.setSelected(true); shell.handleToggleContextualHelpMode(); root.applyCss(); root.layout();
                HelpContextState state = field(shell, "contextualHelpState");
                cipher.getFpeAlphabetPresetCombo().setValue("Custom");
                cipher.getFpeAlphabetField().setText("0123456789");
                assertTrue(((HelpContextAdapter) field(shell, "cipherHelpContextAdapter")).isActive());
                assertEquals("10", state.current().selections().get(HelpContext.Selection.FPE_RADIX).code());
                TextArea fpeInput = field(cipher, "fpeInputArea"); fpeInput.setText("FPE_INPUT_SENTINEL");
                javafx.scene.Node fpeHelp = ContextualHelpTestSupport.control(root, "contextHelp_op_fpe_select_alphabet_alphabet_preset");
                assertNotNull(fpeHelp); assertTrue(fpeHelp.isVisible());
                ContextualHelpTestSupport.shiftF1(fpeHelp);
                assertEquals("op_fpe", state.current().operationId());
                assertEquals("10", state.current().selections().get(HelpContext.Selection.FPE_RADIX).code());
                assertFalse(state.current().toString().contains("FPE_INPUT_SENTINEL"));
                assertTrue(shownHelpText(shell).contains(Integer.toString(
                        com.cryptocarver.crypto.FormatPreservingEncryption.maximumTweakLength())),
                        "FF1 maximum tweak length is rendered from the algorithm limit");
                assertEquals("FPE_INPUT_SENTINEL", fpeInput.getText());
                cipher.getFpeOperationCombo().setValue("DECRYPT");
                assertEquals("DECRYPT", state.current().selections().get(HelpContext.Selection.FPE_OPERATION).code());
                javafx.scene.Node runHelp = ContextualHelpTestSupport.control(root, "contextHelp_op_fpe_encrypt_action");
                assertNotNull(runHelp);
                assertEquals(com.cryptocarver.service.I18nService.getInstance().text("help.action.op_fpe.decrypt.title"),
                        runHelp.getAccessibleText());
                ContextualHelpTestSupport.shiftF1(runHelp); assertEquals("decrypt", state.current().actionId());

                shell.navigateTo("File Cipher (Streaming)"); root.applyCss(); root.layout();
                javafx.scene.Node analyzerHelp = ContextualHelpTestSupport.control(root, "contextHelp_aux_encrypted_file_analysis_analyze_file_representation");
                assertNotNull(analyzerHelp); assertTrue(analyzerHelp.isVisible());
                ContextualHelpTestSupport.shiftF1(analyzerHelp);
                assertEquals("aux_encrypted_file_analysis", state.current().operationId());
                assertFalse(state.current().actionId().isBlank());

                shell.navigateTo("Asymmetric Ciphers"); root.applyCss(); root.layout();
                var rsaDefinition = com.cryptocarver.model.help.OperationHelpRegistry.getInstance().resolve("op_asym_ciphers");
                var formats = rsaDefinition.actions().get(0).options().stream()
                        .filter(option -> option.fieldId().equals("input_format") || option.fieldId().equals("output_format"))
                        .map(OptionHelp::code).distinct().toList();
                assertEquals(cipher.getAsymmetricInputFormatCombo().getItems(), formats);
                assertEquals(cipher.getAsymmetricOutputFormatCombo().getItems(), formats);
                assertFalse(formats.contains("Base64URL"));
                javafx.scene.Node paddingHelp = ContextualHelpTestSupport.control(root, "contextHelp_op_asym_ciphers_select_padding_padding");
                assertNotNull(paddingHelp); assertTrue(paddingHelp.isVisible());
                ContextualHelpTestSupport.shiftF1(paddingHelp);
                assertEquals("op_asym_ciphers", state.current().operationId());
                assertEquals("RSA_ECB_PKCS1PADDING",
                        state.current().selections().get(HelpContext.Selection.RSA_PADDING).code());
                var firstPair = java.security.KeyPairGenerator.getInstance("RSA"); firstPair.initialize(1024);
                String firstPem = publicPem(firstPair.generateKeyPair().getPublic());
                cipher.getPublicKeyArea().setText(firstPem); cipher.handleLoadPublicKey();
                assertEquals("1024", state.current().selections().get(HelpContext.Selection.RSA_MODULUS_BITS).code());
                cipher.getRsaPaddingCombo().setValue("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
                assertTrue(shownHelpText(shell).contains("62 bytes"), "maximum follows the live padding and loaded public modulus");
                var secondPair = java.security.KeyPairGenerator.getInstance("RSA"); secondPair.initialize(2048);
                String secondPem = publicPem(secondPair.generateKeyPair().getPublic());
                cipher.getPublicKeyArea().setText(secondPem);
                assertFalse(state.current().selections().containsKey(HelpContext.Selection.RSA_MODULUS_BITS),
                        "replaced but not yet loaded public text must clear the old maximum");
                cipher.handleLoadPublicKey();
                assertEquals("2048", state.current().selections().get(HelpContext.Selection.RSA_MODULUS_BITS).code());
                assertTrue(shownHelpText(shell).contains("190 bytes"));
                cipher.getPublicKeyArea().clear();
                assertFalse(state.current().selections().containsKey(HelpContext.Selection.RSA_MODULUS_BITS),
                        "clearing the panel key with no shared fallback must remove stale modulus metadata");

                shell.navigateTo("Key Generation"); root.applyCss(); root.layout();
                Object keysController = field(shell, "keysController");
                Object workspace = field(keysController, "workspace");
                var sharedGenerator = java.security.KeyPairGenerator.getInstance("RSA"); sharedGenerator.initialize(2048);
                java.lang.reflect.Field generatedPair = workspace.getClass().getDeclaredField("lastGeneratedKeyPair");
                generatedPair.setAccessible(true);
                generatedPair.set(workspace, sharedGenerator.generateKeyPair());
                shell.navigateTo("Asymmetric Ciphers"); root.applyCss(); root.layout();
                assertEquals("select_padding", state.current().actionId(), "the RSA help selection should return with its route; " + state.current() + " focus=" + root.getScene().getFocusOwner());
                assertEquals("2048", state.current().selections().get(HelpContext.Selection.RSA_MODULUS_BITS).code(),
                        "with an empty area, help uses only the shared public RSA metadata the handler would select");
                cipher.getRsaPaddingCombo().setValue("RSA/ECB/PKCS1Padding");
                assertEquals("2048", state.current().selections().get(HelpContext.Selection.RSA_MODULUS_BITS).code());
                assertTrue(shownHelpText(shell).contains("245 bytes"), "padding changes refresh the open RSA help card");
                shell.shutdown(); stage.close();
            } catch (Throwable failure) { throw new RuntimeException(failure); }
            finally { AppSettings.setInstanceForTesting(original); }
        });
    }

    @Test void fileLineActionsAndAnalyzerContextFollowEffectiveFormWithoutRunningIt() throws Exception {
        UiTestLifecycleExtension.onFx(() -> {
            AppSettings original = AppSettings.getInstance();
            Stage stage = null;
            try {
                AppSettings.setInstanceForTesting(new AppSettings(Files.createTempDirectory("b04-r3-r4").resolve("settings.json")));
                com.cryptocarver.service.I18nService.getInstance().setPreference(LanguagePreference.EN);
                var loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
                Parent root = loader.load(); stage = new Stage(); stage.setScene(new Scene(root, 900, 900)); stage.show();
                ModernMainController shell = loader.getController();
                shell.navigateTo("File Cipher (Streaming)");
                CheckMenuItem mode = field(shell, "contextualHelpModeMenuItem");
                mode.setSelected(true); shell.handleToggleContextualHelpMode(); root.applyCss(); root.layout();
                CipherController cipher = field(shell, "cipherContainerController");
                HelpContextState state = field(shell, "contextualHelpState");
                TextArea result = field(cipher, "fileCipherResultArea");
                TextArea output = field(cipher, "cipherOutputArea");
                result.setText("FILE_RESULT_SENTINEL"); output.setText("ANALYSIS_RESULT_SENTINEL");
                cipher.getFileCipherKeyField().setText("AA".repeat(32));
                cipher.getFileCipherNonceField().setText("11".repeat(12));
                cipher.getFileCipherTagField().setText("/missing/never-read.tag");
                cipher.getFileCipherAadField().setText("CC");
                cipher.getSymmetricKeyField().clear(); cipher.getIvField().clear(); cipher.getGcmTagField().clear(); cipher.getAadField().clear();
                javafx.scene.Node analyzeHelp = ContextualHelpTestSupport.control(root, "contextHelp_aux_encrypted_file_analysis_analyze_file_representation");
                assertNotNull(analyzeHelp); ContextualHelpTestSupport.shiftF1(analyzeHelp);
                assertEquals("aux_encrypted_file_analysis", state.current().operationId());
                assertEquals("true", state.current().selections().get(HelpContext.Selection.ANALYSIS_HAS_KEY).code());
                assertEquals("true", state.current().selections().get(HelpContext.Selection.ANALYSIS_HAS_IV).code());
                assertEquals("true", state.current().selections().get(HelpContext.Selection.ANALYSIS_HAS_TAG).code(), "tag path is only a reference; help must not open it");
                assertEquals("true", state.current().selections().get(HelpContext.Selection.ANALYSIS_HAS_AAD).code());
                assertEquals("FILE_RESULT_SENTINEL", result.getText());
                assertEquals("ANALYSIS_RESULT_SENTINEL", output.getText());
                assertFalse(state.current().toString().contains("/missing/never-read.tag"));

                cipher.getFileCipherKeyField().clear(); cipher.getFileCipherNonceField().clear();
                cipher.getFileCipherTagField().clear(); cipher.getFileCipherAadField().clear();
                cipher.getSymmetricKeyField().setText("DD".repeat(32)); cipher.getIvField().setText("EE".repeat(16));
                cipher.getGcmTagField().setText("FF".repeat(16)); cipher.getAadField().setText("11");
                ContextualHelpTestSupport.shiftF1(analyzeHelp);
                assertEquals("false", state.current().selections().get(HelpContext.Selection.ANALYSIS_HAS_KEY).code());
                assertEquals("false", state.current().selections().get(HelpContext.Selection.ANALYSIS_HAS_IV).code());
                assertEquals("false", state.current().selections().get(HelpContext.Selection.ANALYSIS_HAS_TAG).code());
                assertEquals("false", state.current().selections().get(HelpContext.Selection.ANALYSIS_HAS_AAD).code());

                javafx.scene.Node encryptHelp = ContextualHelpTestSupport.control(root, "contextHelp_op_sym_file_encrypt_file_action");
                javafx.scene.Node decryptHelp = ContextualHelpTestSupport.control(root, "contextHelp_op_sym_file_decrypt_file_action");
                assertNotNull(encryptHelp); assertNotNull(decryptHelp);
                cipher.getFileCipherAlgorithmCombo().setValue("AES-256-GCM");
                cipher.getFileCipherLinesCheck().setSelected(true);
                assertEquals(com.cryptocarver.service.I18nService.getInstance().text("help.action.op_sym_file.encrypt_lines.title"), encryptHelp.getAccessibleText());
                assertEquals(com.cryptocarver.service.I18nService.getInstance().text("help.action.op_sym_file.decrypt_lines.title"), decryptHelp.getAccessibleText());
                ContextualHelpTestSupport.shiftF1(encryptHelp); assertEquals("encrypt_lines", state.current().actionId());
                assertEquals("true", state.current().selections().get(HelpContext.Selection.FILE_LINES).code());
                assertTrue(shownHelpText(shell).toLowerCase(java.util.Locale.ROOT).contains("separate text record"));
                cipher.getFileCipherAlgorithmCombo().setValue("AES-256-CBC");
                cipher.getFileCipherCompactCbcCheck().setSelected(true);
                ContextualHelpTestSupport.shiftF1(decryptHelp); assertEquals("decrypt_lines", state.current().actionId());
                assertEquals("true", state.current().selections().get(HelpContext.Selection.FILE_COMPACT_CBC).code());
                assertTrue(shownHelpText(shell).toLowerCase(java.util.Locale.ROOT).contains("compact"));
                assertEquals("FILE_RESULT_SENTINEL", result.getText());
                assertEquals("ANALYSIS_RESULT_SENTINEL", output.getText());
                shell.shutdown(); stage.close();
            } catch (Throwable failure) { throw new RuntimeException(failure); }
            finally { AppSettings.setInstanceForTesting(original); }
        });
    }

    @Test void b04CipherQueriesRenderInSpanishAndEnglishInInspectorAndCompactViews() throws Exception {
        UiTestLifecycleExtension.onFx(() -> {
            AppSettings original = AppSettings.getInstance();
            Stage stage = null;
            try {
                for (LanguagePreference locale : java.util.List.of(LanguagePreference.ES, LanguagePreference.EN)) {
                    for (int width : java.util.List.of(1800, 900)) {
                        AppSettings.setInstanceForTesting(new AppSettings(Files.createTempDirectory("b04-localized-route").resolve("settings.json")));
                        com.cryptocarver.service.I18nService.getInstance().setPreference(locale);
                        var loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
                        Parent root = loader.load(); stage = new Stage(); stage.setScene(new Scene(root, width, 900)); stage.show();
                        ModernMainController shell = loader.getController();
                        shell.navigateTo("Format-Preserving Encryption"); root.applyCss(); root.layout();
                        CheckMenuItem mode = field(shell, "contextualHelpModeMenuItem");
                        mode.setSelected(true); shell.handleToggleContextualHelpMode(); root.applyCss(); root.layout();
                        HelpContextState state = field(shell, "contextualHelpState");
                        javafx.scene.Node fpe = ContextualHelpTestSupport.control(root, "contextHelp_op_fpe_select_algorithm_algorithm");
                        assertNotNull(fpe); ContextualHelpTestSupport.shiftF1(fpe);
                        assertEquals("select_algorithm", state.current().actionId());
                        assertTrue(fpe.getAccessibleText().equals(com.cryptocarver.service.I18nService.getInstance()
                                .text("help.field.op_fpe.algorithm.label")));
                        assertTrue(shownHelpText(shell).contains(com.cryptocarver.service.I18nService.getInstance()
                                .text("help.action.op_fpe.select_algorithm.description")));

                        CipherController cipher = field(shell, "cipherContainerController");
                        shell.navigateTo("File Cipher (Streaming)"); root.applyCss(); root.layout();
                        cipher.getFileCipherLinesCheck().setSelected(true);
                        javafx.scene.Node encrypt = ContextualHelpTestSupport.control(root, "contextHelp_op_sym_file_encrypt_file_action");
                        assertNotNull(encrypt); ContextualHelpTestSupport.shiftF1(encrypt);
                        assertEquals("encrypt_lines", state.current().actionId());
                        assertTrue(encrypt.getAccessibleText().equals(com.cryptocarver.service.I18nService.getInstance()
                                .text("help.action.op_sym_file.encrypt_lines.title")));
                        assertTrue(shownHelpText(shell).contains(com.cryptocarver.service.I18nService.getInstance()
                                .text("help.action.op_sym_file.encrypt_lines.description")));
                        javafx.scene.Node analyze = ContextualHelpTestSupport.control(root, "contextHelp_aux_encrypted_file_analysis_analyze_file_representation");
                        assertNotNull(analyze); ContextualHelpTestSupport.shiftF1(analyze);
                        assertEquals("aux_encrypted_file_analysis", state.current().operationId());
                        assertTrue(shownHelpText(shell).contains(com.cryptocarver.service.I18nService.getInstance()
                                .text("help.analysis.route")));

                        cipher.getFileCipherKeyField().setText("A1".repeat(32));
                        cipher.getFileCipherNonceField().setText("B2".repeat(12));
                        cipher.getFileCipherAadField().setText("C3D4");
                        TextArea fileResult = field(cipher, "fileCipherResultArea");
                        fileResult.setText("FILE_RESULT_PRESERVED");
                        javafx.scene.Node[] directionButtons = { encrypt,
                                ContextualHelpTestSupport.control(root, "contextHelp_op_sym_file_decrypt_file_action") };
                        String[] directions = { "encrypt", "decrypt" };
                        String[] fieldIds = { "key", "iv_nonce", "aad", "result" };
                        String[] bindingIds = { "key", "iv_nonce", "aad", "result" };
                        for (int directionIndex = 0; directionIndex < directions.length; directionIndex++) {
                            String direction = directions[directionIndex];
                            javafx.scene.Node directionButton = directionButtons[directionIndex];
                            assertNotNull(directionButton);
                            ContextualHelpTestSupport.shiftF1(directionButton);
                            for (int fieldIndex = 0; fieldIndex < fieldIds.length; fieldIndex++) {
                                javafx.scene.Node fieldHelp = ContextualHelpTestSupport.control(root, "contextHelp_op_sym_file_encrypt_file_"
                                        + bindingIds[fieldIndex]);
                                assertNotNull(fieldHelp, "field binding exists for " + fieldIds[fieldIndex]);
                                ContextualHelpTestSupport.shiftF1(fieldHelp);
                                String action = direction + "_lines";
                                assertEquals(action, state.current().actionId());
                                assertEquals(fieldIds[fieldIndex], state.current().fieldId());
                                assertTrue(shownHelpText(shell).contains(com.cryptocarver.service.I18nService.getInstance()
                                        .text("help.fileCipher.linesEnabled")));

                                // The already-open card follows a programmatic toggle without another ? click.
                                cipher.getFileCipherLinesCheck().setSelected(false);
                                assertEquals(direction + "_file", state.current().actionId());
                                assertEquals(fieldIds[fieldIndex], state.current().fieldId());
                                assertEquals("false", state.current().selections().get(HelpContext.Selection.FILE_LINES).code());
                                assertTrue(shownHelpText(shell).contains(com.cryptocarver.service.I18nService.getInstance()
                                        .text("help.fileCipher.linesDisabled")));
                                cipher.getFileCipherLinesCheck().setSelected(true);
                                assertEquals(action, state.current().actionId());
                                assertEquals(fieldIds[fieldIndex], state.current().fieldId());
                            }
                        }
                        assertEquals("A1".repeat(32), cipher.getFileCipherKeyField().getText());
                        assertEquals("B2".repeat(12), cipher.getFileCipherNonceField().getText());
                        assertEquals("C3D4", cipher.getFileCipherAadField().getText());
                        assertEquals("FILE_RESULT_PRESERVED", fileResult.getText());
                        assertFalse(shownHelpText(shell).contains("A1".repeat(32)));
                        assertFalse(shownHelpText(shell).contains("FILE_RESULT_PRESERVED"));

                        ContextualHelpTestSupport.shiftF1(analyze);
                        assertEquals("aux_encrypted_file_analysis", state.current().operationId());
                        cipher.getFileCipherLinesCheck().setSelected(false);
                        assertEquals("aux_encrypted_file_analysis", state.current().operationId(),
                                "changing the file mode while the analyzer card is active keeps that auxiliary card selected");
                        javafx.scene.Node resultHelp = ContextualHelpTestSupport.control(root, "contextHelp_op_sym_file_encrypt_file_result");
                        assertNotNull(resultHelp);
                        ContextualHelpTestSupport.shiftF1(resultHelp);
                        assertEquals("op_sym_file", state.current().operationId());
                        assertEquals("decrypt_file", state.current().actionId(),
                                "a field query from A008 preserves the saved decrypt direction and uses the live whole-file mode");
                        assertEquals("result", state.current().fieldId());
                        shell.navigateTo("Format-Preserving Encryption");
                        shell.navigateTo("File Cipher (Streaming)"); root.applyCss(); root.layout();
                        assertEquals("op_sym_file", state.current().operationId());
                        assertEquals("decrypt_file", state.current().actionId());
                        assertEquals("result", state.current().fieldId());
                        assertEquals("false", state.current().selections().get(HelpContext.Selection.FILE_LINES).code());

                        ContextualHelpTestSupport.shiftF1(analyze);
                        assertEquals("aux_encrypted_file_analysis", state.current().operationId());
                        cipher.getFileCipherLinesCheck().setSelected(true);
                        assertEquals("aux_encrypted_file_analysis", state.current().operationId(),
                                "the second selector transition also leaves A008 active");
                        shell.navigateTo("Format-Preserving Encryption");
                        shell.navigateTo("File Cipher (Streaming)"); root.applyCss(); root.layout();
                        assertEquals("op_sym_file", state.current().operationId());
                        assertEquals("decrypt_lines", state.current().actionId());
                        assertEquals("result", state.current().fieldId());
                        assertEquals("true", state.current().selections().get(HelpContext.Selection.FILE_LINES).code());
                        assertEquals("FILE_RESULT_PRESERVED", fileResult.getText());

                        ContextualHelpTestSupport.shiftF1(encrypt);
                        javafx.scene.Node keyHelp = ContextualHelpTestSupport.control(root, "contextHelp_op_sym_file_encrypt_file_key");
                        assertNotNull(keyHelp); ContextualHelpTestSupport.shiftF1(keyHelp);
                        assertEquals("encrypt_lines", state.current().actionId());
                        assertEquals("key", state.current().fieldId());
                        ContextualHelpTestSupport.shiftF1(analyze);
                        cipher.getFileCipherLinesCheck().setSelected(false);
                        assertEquals("aux_encrypted_file_analysis", state.current().operationId());
                        shell.navigateTo("Format-Preserving Encryption");
                        shell.navigateTo("File Cipher (Streaming)"); root.applyCss(); root.layout();
                        assertEquals("encrypt_file", state.current().actionId());
                        assertEquals("key", state.current().fieldId());
                        assertEquals("false", state.current().selections().get(HelpContext.Selection.FILE_LINES).code());

                        shell.navigateTo("Asymmetric Ciphers"); root.applyCss(); root.layout();
                        javafx.scene.Node padding = ContextualHelpTestSupport.control(root, "contextHelp_op_asym_ciphers_select_padding_padding");
                        assertNotNull(padding); ContextualHelpTestSupport.shiftF1(padding);
                        assertEquals("op_asym_ciphers", state.current().operationId());
                        assertTrue(shownHelpText(shell).contains(com.cryptocarver.service.I18nService.getInstance()
                                .text("help.field.op_asym_ciphers.padding.description")));
                        Object compact = field(shell, "contextualHelpWindowCoordinator");
                        assertEquals(width < 1100, compact instanceof ContextualHelpWindowCoordinator window && window.isShowing());
                        shell.shutdown(); stage.close(); stage = null;
                    }
                }
            } catch (Throwable failure) { throw new RuntimeException(failure); }
            finally { if (stage != null) stage.close(); AppSettings.setInstanceForTesting(original); }
        });
    }

    @Test void changingFileLineModeWithHelpDisabledDoesNotOpenHelp() throws Exception {
        UiTestLifecycleExtension.onFx(() -> {
            AppSettings original = AppSettings.getInstance();
            Stage stage = null;
            try {
                AppSettings.setInstanceForTesting(new AppSettings(Files.createTempDirectory("b04-disabled-help").resolve("settings.json")));
                com.cryptocarver.service.I18nService.getInstance().setPreference(LanguagePreference.EN);
                var loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
                Parent root = loader.load(); stage = new Stage(); stage.setScene(new Scene(root, 900, 900)); stage.show();
                ModernMainController shell = loader.getController();
                shell.navigateTo("File Cipher (Streaming)"); root.applyCss(); root.layout();
                CheckMenuItem mode = field(shell, "contextualHelpModeMenuItem");
                mode.setSelected(false); shell.handleToggleContextualHelpMode();
                CipherController cipher = field(shell, "cipherContainerController");
                HelpContextState state = field(shell, "contextualHelpState");
                assertNull(field(shell, "contextualHelpWindowCoordinator"));
                cipher.getFileCipherLinesCheck().setSelected(true);
                assertNull(field(shell, "contextualHelpWindowCoordinator"), "selector changes with help disabled must not create a window");
                assertTrue(state.current().actionId().isBlank());
                assertEquals("true", state.current().selections().get(HelpContext.Selection.FILE_LINES).code());
                shell.shutdown(); stage.close();
            } catch (Throwable failure) { throw new RuntimeException(failure); }
            finally { AppSettings.setInstanceForTesting(original); }
        });
    }

    @SuppressWarnings("unchecked") private static <T> T field(Object owner, String name) throws Exception {
        Field f=owner.getClass().getDeclaredField(name); f.setAccessible(true); return (T)f.get(owner);
    }

    private static String publicPem(java.security.PublicKey key) {
        return "-----BEGIN PUBLIC KEY-----\n" + java.util.Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(key.getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }

    private static String shownHelpText(ModernMainController shell) throws Exception {
        Object compact = field(shell, "contextualHelpWindowCoordinator");
        javafx.scene.layout.VBox content = compact instanceof ContextualHelpWindowCoordinator window && window.isShowing()
                ? (javafx.scene.layout.VBox) ((javafx.scene.control.ScrollPane) window.windowForTesting().getScene().getRoot()).getContent()
                : field(shell, "contextualHelpContent");
        return content.getChildren().stream().filter(javafx.scene.control.Label.class::isInstance)
                .map(javafx.scene.control.Label.class::cast).map(javafx.scene.control.Label::getText)
                .collect(java.util.stream.Collectors.joining("\n"));
    }
}
