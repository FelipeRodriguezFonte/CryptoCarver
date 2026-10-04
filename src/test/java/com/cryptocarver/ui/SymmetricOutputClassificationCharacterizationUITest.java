package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.ClipboardEntry;
import com.cryptocarver.model.ClipboardShelfManager;
import com.cryptocarver.model.HistoryCommand;
import com.cryptocarver.model.HistoryManager;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.ResultPresentationPolicy;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.service.I18nService;
import com.cryptocarver.util.DataConverter;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Approved classification policy and user-visible behavior for symmetric results. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class SymmetricOutputClassificationCharacterizationUITest {
    private static final String KEY_DES = "0001020304050607";
    private static final String KEY_128 = "000102030405060708090A0B0C0D0E0F";
    private static final String KEY_192 = "000102030405060708090A0B0C0D0E0F1011121314151617";
    private static final String KEY_256 = KEY_192 + "18191A1B1C1D1E1F";
    private static final String IV_8 = "0F0E0D0C0B0A0908";
    private static final String IV_16 = "0F0E0D0C0B0A09080706050403020100";
    private static final String NONCE_8 = "0001020304050607";
    private static final String NONCE_12 = "CAFEBABEFACEDBADDECAF888";
    private static final String NONCE_24 = "000102030405060708090A0B0C0D0E0F1011121314151617";
    private static final String AAD = "FEEDFACEDEADBEEF";
    private static final String MESSAGE = "SYNTHETIC-CLASSIFICATION-PLAINTEXT";

    @TempDir Path tempDir;
    private ModernMainController shell;
    private Parent root;
    private Stage stage;
    private HistoryManager priorShellHistory;
    private List<ClipboardEntry> previousShelf;
    private SecretVisibilityProfile previousVisibility;
    private LanguagePreference previousLanguage;
    private String previousRoute;
    private String previousClipboard;

    @BeforeAll
    static void startFx() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                ready.countDown();
            });
        } catch (IllegalStateException started) {
            Platform.setImplicitExit(false);
            ready.countDown();
        }
        assertTrue(ready.await(15, TimeUnit.SECONDS));
    }

    @TestFactory
    Stream<DynamicTest> everyAlgorithmOperationPublishesItsApprovedClassification() {
        return cipherCases().stream().flatMap(cipherCase -> Stream.of(true, false).map(encrypt ->
                DynamicTest.dynamicTest(cipherCase.label() + (encrypt ? " encrypt" : " decrypt"),
                        () -> characterizeClassification(cipherCase, encrypt))));
    }

    @Test
    void chaCha20AndSalsa20HaveIdenticalOutputVisibilityForEncryptAndDecrypt() throws Exception {
        withPanel(panel -> {
            OperationResult[] chacha = roundTrip(panel, cipherCases().stream()
                    .filter(each -> each.algorithm().equals("ChaCha20")).findFirst().orElseThrow());
            OperationResult[] salsa = roundTrip(panel, cipherCases().stream()
                    .filter(each -> each.algorithm().equals("Salsa20")).findFirst().orElseThrow());

            assertEquals(chacha[0].getOutputClassification(), salsa[0].getOutputClassification(), "encrypt classification");
            assertEquals(chacha[1].getOutputClassification(), salsa[1].getOutputClassification(), "decrypt classification");
            assertEquals(OperationDetail.Classification.PUBLIC, chacha[0].getOutputClassification());
            assertEquals(OperationDetail.Classification.SENSITIVE, chacha[1].getOutputClassification());
            for (SecretVisibilityProfile profile : SecretVisibilityProfile.values()) {
                String chachaPlaintext = OperationResultRenderer.render(chacha[1], profile);
                String salsaPlaintext = OperationResultRenderer.render(salsa[1], profile);
                assertEquals(chachaPlaintext, salsaPlaintext, "decryption under " + profile);
                assertEquals(
                        ResultPresentationPolicy.isShelfCaptureBlockedByVisibility(
                                ResultPresentationPolicy.classifyPublishedResult(chacha[1]), profile),
                        ResultPresentationPolicy.isShelfCaptureBlockedByVisibility(
                                ResultPresentationPolicy.classifyPublishedResult(salsa[1]), profile),
                        "Shelf decision under " + profile);
                String chachaCiphertext = OperationResultRenderer.render(chacha[0], profile);
                String salsaCiphertext = OperationResultRenderer.render(salsa[0], profile);
                assertFalse(chachaCiphertext.equals("***MASKED***"), "ChaCha20 ciphertext under " + profile);
                assertFalse(salsaCiphertext.equals("***MASKED***"), "Salsa20 ciphertext under " + profile);
                assertEquals(chachaCiphertext, OperationResultRenderer.render(chacha[0], SecretVisibilityProfile.FULL_LAB));
                assertEquals(salsaCiphertext, OperationResultRenderer.render(salsa[0], SecretVisibilityProfile.FULL_LAB));
            }
        });
    }

    @Test
    void fullLabRetainsCopyExpandAndShelfForCiphertextAndPlaintextWhileRestrictedProfilesHidePlaintext() throws Exception {
        AppSettings settings = AppSettings.getInstance();
        previousVisibility = settings.getSecretVisibilityProfile();
        previousLanguage = settings.getLanguagePreference();
        previousRoute = settings.getLastRoute();
        previousShelf = List.copyOf(ClipboardShelfManager.getInstance().getEntries());
        previousClipboard = fxValue(() -> Clipboard.getSystemClipboard().getString());

        try {
            fx(() -> {
                settings.setSecretVisibilityProfile(SecretVisibilityProfile.FULL_LAB);
                settings.setLanguagePreference(LanguagePreference.EN);
                I18nService.getInstance().setPreference(LanguagePreference.EN);
                FXMLLoader loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
                root = loader.load();
                shell = loader.getController();
                priorShellHistory = read(shell, "historyManager");
                set(shell, "historyManager", new HistoryManager(tempDir.resolve("isolated-history.json")));
                stage = new Stage();
                stage.setScene(new Scene(root, 1400, 900));
                stage.show();
                shell.navigateToModule("Symmetric Ciphers");
                root.applyCss();
                root.layout();
                ClipboardShelfManager.getInstance().clear();
                configureCipher("AES-128", "CBC", "PKCS5Padding", KEY_128, IV_16, "", "");
                setFormats("Text (UTF-8)", "Hexadecimal");
                textArea("cipherInputArea").setText(MESSAGE);
                cipherController().handleSymmetricEncrypt();
            });

            OperationResult encrypted = fxValue(() -> read(shell, "lastPublishedResultSnapshot"));
            assertNotNull(encrypted);
            assertEquals(OperationDetail.Classification.PUBLIC, encrypted.getOutputClassification());
            String ciphertext = fxValue(() -> textArea("cipherOutputArea").getText());
            assertFullLabCaptureWorks(ciphertext, encrypted);
            assertEquals(1, ClipboardShelfManager.getInstance().getEntries().size());
            assertEquals(ciphertext, ClipboardShelfManager.getInstance().getEntries().get(0).getValue());

            String ciphertextHex = DataConverter.bytesToHex(encrypted.getOutput());
            fx(() -> {
                ClipboardShelfManager.getInstance().clear();
                configureCipher("AES-128", "CBC", "PKCS5Padding", KEY_128, IV_16, "", "");
                setFormats("Hexadecimal", "Text (UTF-8)");
                textArea("cipherInputArea").setText(ciphertextHex);
                cipherController().handleSymmetricDecrypt();
            });

            OperationResult decrypted = fxValue(() -> read(shell, "lastPublishedResultSnapshot"));
            assertNotNull(decrypted);
            assertEquals(OperationDetail.Classification.SENSITIVE, decrypted.getOutputClassification());
            assertFullLabCaptureWorks(MESSAGE, decrypted);
            assertEquals(1, ClipboardShelfManager.getInstance().getEntries().size());
            assertEquals(MESSAGE, ClipboardShelfManager.getInstance().getEntries().get(0).getValue());
            assertEquals(OperationDetail.Classification.SENSITIVE,
                    ClipboardShelfManager.getInstance().getEntries().get(0).getClassification());
            assertEquals(2, shell.getHistoryManager().getHistoryItems().size());

            for (SecretVisibilityProfile profile : List.of(SecretVisibilityProfile.MASKED, SecretVisibilityProfile.REDACTED)) {
                ClipboardShelfManager.getInstance().clear();
                fx(() -> {
                    settings.setSecretVisibilityProfile(profile);
                    shell.getHistoryManager().clearHistory();
                    configureCipher("AES-128", "CBC", "PKCS5Padding", KEY_128, IV_16, "", "");
                    setFormats("Hexadecimal", "Text (UTF-8)");
                    textArea("cipherInputArea").setText(ciphertextHex);
                    cipherController().handleSymmetricDecrypt();
                });

                OperationResult restricted = fxValue(() -> read(shell, "lastPublishedResultSnapshot"));
                assertEquals(OperationDetail.Classification.SENSITIVE, restricted.getOutputClassification());
                assertRestrictedPlaintextCaptureIsBlocked(profile);
                HistoryCommand history = shell.getHistoryManager().getHistoryItems().get(0);
                String historyText = history.getParameters() + " " + history.getStructuredDetails();
                assertFalse(historyText.contains(MESSAGE), "history plaintext under " + profile);
                assertFalse(historyText.contains(KEY_128), "history key under " + profile);
                assertFalse(historyText.contains(IV_16), "history nonce/IV under " + profile);
            }
        } finally {
            try {
                fx(() -> {
                    if (shell != null) {
                        set(shell, "historyManager", priorShellHistory);
                        shell.shutdown();
                    }
                    if (stage != null) {
                        stage.close();
                        stage.setScene(null);
                    }
                    settings.setSecretVisibilityProfile(previousVisibility);
                    settings.setLanguagePreference(previousLanguage);
                    settings.setLastRoute(previousRoute);
                    I18nService.getInstance().setPreference(previousLanguage);
                    restoreClipboard(previousClipboard);
                });
            } finally {
                restoreShelf(previousShelf);
            }
        }
    }

    private void characterizeClassification(CipherCase cipherCase, boolean encrypt) throws Exception {
        withPanel(panel -> {
            OperationResult result;
            if (encrypt) {
                panel.select(cipherCase.algorithm(), cipherCase.mode(), cipherCase.padding());
                panel.material(cipherCase.key(), cipherCase.nonce(), "", cipherCase.aad());
                panel.inputs("Text (UTF-8)", MESSAGE, "Hexadecimal");
                panel.encrypt();
                result = panel.reporter().published.get(panel.reporter().published.size() - 1);
            } else {
                result = decryptThroughUi(panel, cipherCase);
            }

            OperationDetail.Classification expected = encrypt
                    ? OperationDetail.Classification.PUBLIC : OperationDetail.Classification.SENSITIVE;
            assertEquals(expected, result.getOutputClassification(), cipherCase.label());
            assertEquals(expected, ResultPresentationPolicy.classifyPublishedResult(result), cipherCase.label());
            if (result.getEnrichedOutput() != null && !result.getEnrichedOutput().isBlank()) {
                assertEquals(expected, result.getEnrichedOutputClassification(), cipherCase.label() + " enriched output");
            }
            if (encrypt && cipherCase.authenticated() && result.getEnrichedOutput() != null) {
                assertTrue(result.getEnrichedOutput().contains("CIPHERTEXT"), cipherCase.label());
                assertTrue(result.getEnrichedOutput().contains("AUTHENTICATION TAG"), cipherCase.label());
            }

            String full = OperationResultRenderer.render(result, SecretVisibilityProfile.FULL_LAB);
            assertFalse(full.contains(cipherCase.key()), cipherCase.label() + " must not expose its key");
            assertFalse(full.contains(cipherCase.nonce()), cipherCase.label() + " must not expose its nonce");
            if (encrypt) assertFalse(full.contains(MESSAGE), cipherCase.label() + " ciphertext output");
            else assertTrue(full.contains(MESSAGE), cipherCase.label() + " plaintext output");

            for (SecretVisibilityProfile profile : SecretVisibilityProfile.values()) {
                String rendered = OperationResultRenderer.render(result, profile);
                if (expected == OperationDetail.Classification.PUBLIC) {
                    assertEquals(full, rendered, cipherCase.label() + " must remain visible under " + profile);
                    assertFalse(ResultPresentationPolicy.isShelfCaptureBlockedByVisibility(expected, profile));
                } else if (profile == SecretVisibilityProfile.FULL_LAB) {
                    assertEquals(full, rendered, cipherCase.label() + " FULL_LAB");
                    assertFalse(ResultPresentationPolicy.isShelfCaptureBlockedByVisibility(expected, profile));
                } else {
                    assertEquals("***MASKED***", rendered, cipherCase.label() + " under " + profile);
                    assertTrue(ResultPresentationPolicy.isShelfCaptureBlockedByVisibility(expected, profile));
                    assertFalse(rendered.contains(MESSAGE));
                }
            }
            String statuses = panel.reporter().statusText();
            assertFalse(statuses.contains(MESSAGE));
            assertFalse(statuses.contains(cipherCase.key()));
            assertFalse(statuses.contains(cipherCase.nonce()));
        });
    }

    private OperationResult[] roundTrip(Panel panel, CipherCase cipherCase) {
        panel.select(cipherCase.algorithm(), cipherCase.mode(), cipherCase.padding());
        panel.material(cipherCase.key(), cipherCase.nonce(), "", cipherCase.aad());
        panel.inputs("Text (UTF-8)", MESSAGE, "Hexadecimal");
        panel.encrypt();
        OperationResult encrypted = panel.reporter().published.get(panel.reporter().published.size() - 1);
        OperationResult decrypted = decryptThroughUi(panel, cipherCase);
        return new OperationResult[]{encrypted, decrypted};
    }

    private OperationResult decryptThroughUi(Panel panel, CipherCase cipherCase) {
        panel.select(cipherCase.algorithm(), cipherCase.mode(), cipherCase.padding());
        panel.material(cipherCase.key(), cipherCase.nonce(), "", cipherCase.aad());
        panel.inputs("Text (UTF-8)", MESSAGE, "Hexadecimal");
        panel.encrypt();
        OperationResult encrypted = panel.reporter().published.get(panel.reporter().published.size() - 1);
        panel.reporter().drain();

        byte[] combined = encrypted.getOutput();
        int tagLength = cipherCase.authenticated() ? 16 : 0;
        byte[] ciphertext = Arrays.copyOf(combined, combined.length - tagLength);
        String tag = tagLength == 0 ? "" : DataConverter.bytesToHex(
                Arrays.copyOfRange(combined, combined.length - tagLength, combined.length));
        panel.material(cipherCase.key(), cipherCase.nonce(), tag, cipherCase.aad());
        panel.inputs("Hexadecimal", DataConverter.bytesToHex(ciphertext), "Text (UTF-8)");
        panel.decrypt();
        return panel.reporter().published.get(panel.reporter().published.size() - 1);
    }

    private void assertFullLabCaptureWorks(String expected, OperationResult result) throws Exception {
        fx(() -> {
            assertEquals(expected, shell.resolveCurrentOutputText());
            TextArea output = textArea("cipherOutputArea");
            invoke(shell, "handleCopySecure", new Class<?>[]{TextArea.class, String.class, boolean.class},
                    new Object[]{output, null, false});
            assertEquals(expected, Clipboard.getSystemClipboard().getString(), "Copy in FULL_LAB");
            shell.handleOpenExpandedResultViewer();
            Object viewer = read(shell, "expandedTextViewer");
            assertEquals(expected, ((TextArea) read(viewer, "contentArea")).getText(), "Expand in FULL_LAB");
            shell.handleAddCurrentOutputToShelf();
        });
    }

    private void assertRestrictedPlaintextCaptureIsBlocked(SecretVisibilityProfile profile) throws Exception {
        fx(() -> {
            String displayed = shell.resolveCurrentOutputText();
            assertEquals("***MASKED***", displayed, "Copy/Expand source under " + profile);
            putClipboard("PRESERVE-CLIPBOARD");
            TextArea output = textArea("cipherOutputArea");
            invoke(shell, "handleCopySecure", new Class<?>[]{TextArea.class, String.class, boolean.class},
                    new Object[]{output, null, false});
            assertEquals("PRESERVE-CLIPBOARD", Clipboard.getSystemClipboard().getString(), "Copy blocked under " + profile);
            shell.handleOpenExpandedResultViewer();
            Object viewer = read(shell, "expandedTextViewer");
            assertEquals("***MASKED***", ((TextArea) read(viewer, "contentArea")).getText(), "Expand masked under " + profile);
            shell.handleAddCurrentOutputToShelf();
            assertTrue(ClipboardShelfManager.getInstance().getEntries().isEmpty(), "Shelf blocked under " + profile);
            Label status = (Label) root.lookup("#statusLabel");
            String statusText = status == null ? "" : status.getText();
            assertFalse(statusText.contains(MESSAGE));
            assertFalse(statusText.contains(KEY_128));
            assertFalse(statusText.contains(IV_16));
        });
    }

    private static List<CipherCase> cipherCases() {
        return List.of(
                new CipherCase("DES", "CBC", "PKCS5Padding", KEY_DES, IV_8, "", false),
                new CipherCase("3DES (Triple DES)", "CBC", "PKCS5Padding", KEY_192, IV_8, "", false),
                new CipherCase("AES-128", "CBC", "PKCS5Padding", KEY_128, IV_16, "", false),
                new CipherCase("AES-192", "CBC", "PKCS5Padding", KEY_192, IV_16, "", false),
                new CipherCase("AES-256", "CBC", "PKCS5Padding", KEY_256, IV_16, "", false),
                new CipherCase("AES-256", "GCM", "NoPadding", KEY_256, NONCE_12, AAD, true),
                new CipherCase("ChaCha20", null, null, KEY_256, NONCE_12, "", false),
                new CipherCase("Salsa20", null, null, KEY_256, NONCE_8, "", false),
                new CipherCase("ChaCha20-Poly1305", null, null, KEY_256, NONCE_12, "", true),
                new CipherCase("XChaCha20-Poly1305", null, null, KEY_256, NONCE_24, "", true));
    }

    private void withPanel(java.util.function.Consumer<Panel> body) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                FXMLLoader loader = UiTestFxml.loader("/fxml/cipher.fxml");
                loader.load();
                CipherController controller = loader.getController();
                ComboBox<String> inputFormat = new ComboBox<>();
                inputFormat.getItems().setAll("Text (UTF-8)", "Hexadecimal", "Base64", "Binary");
                ComboBox<String> outputFormat = new ComboBox<>();
                outputFormat.getItems().setAll("Text (UTF-8)", "Hexadecimal", "Base64", "Binary");
                Recorder reporter = new Recorder(inputFormat, outputFormat);
                controller.initModern(reporter, inputFormat, outputFormat, null);
                body.accept(new Panel(loader, controller, reporter, inputFormat, outputFormat));
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(60, TimeUnit.SECONDS), "FX thread did not complete");
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private void configureCipher(String algorithm, String mode, String padding, String key, String iv, String tag, String aad) {
        ((ComboBox<String>) root.lookup("#symmetricAlgorithmCombo")).setValue(algorithm);
        if (mode != null) ((ComboBox<String>) root.lookup("#cipherModeCombo")).setValue(mode);
        if (padding != null) ((ComboBox<String>) root.lookup("#paddingCombo")).setValue(padding);
        ((TextField) root.lookup("#symmetricKeyField")).setText(key);
        ((TextField) root.lookup("#ivField")).setText(iv);
        ((TextField) root.lookup("#gcmTagField")).setText(tag);
        ((TextField) root.lookup("#aadField")).setText(aad);
    }

    private void setFormats(String input, String output) {
        ((ComboBox<String>) root.lookup("#inputFormatCombo")).setValue(input);
        ((ComboBox<String>) root.lookup("#outputFormatCombo")).setValue(output);
    }

    private TextArea textArea(String id) { return (TextArea) root.lookup("#" + id); }

    private CipherController cipherController() throws Exception { return read(shell, "cipherController"); }

    private void restoreShelf(List<ClipboardEntry> entries) {
        ClipboardShelfManager shelf = ClipboardShelfManager.getInstance();
        shelf.clear();
        if (entries != null) {
            for (int index = entries.size() - 1; index >= 0; index--) shelf.addEntry(entries.get(index));
        }
    }

    private static void putClipboard(String value) {
        ClipboardContent content = new ClipboardContent();
        content.putString(value);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private static void restoreClipboard(String value) {
        if (value == null) Clipboard.getSystemClipboard().clear();
        else putClipboard(value);
    }

    private static <T> T fxValue(FxSupplier<T> action) throws Exception {
        AtomicReference<T> value = new AtomicReference<>();
        fx(() -> value.set(action.get()));
        return value.get();
    }

    private static void fx(FxAction action) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try { action.run(); } catch (Throwable error) { failure.set(error); }
            finally { done.countDown(); }
        });
        assertTrue(done.await(60, TimeUnit.SECONDS), "FX thread did not complete");
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private static void invoke(Object target, String name, Class<?>[] types, Object[] args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        method.invoke(target, args);
    }

    private static <T> T read(Object target, String name) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        return (T) field.get(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try { return current.getDeclaredField(name); }
            catch (NoSuchFieldException ignored) { current = current.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }

    private record CipherCase(String algorithm, String mode, String padding, String key,
                              String nonce, String aad, boolean authenticated) {
        String label() { return algorithm + (mode == null ? "" : "/" + mode); }
    }

    private record Panel(FXMLLoader loader, CipherController controller, Recorder reporter,
                         ComboBox<String> inputFormat, ComboBox<String> outputFormat) {
        @SuppressWarnings("unchecked")
        ComboBox<String> combo(String id) { return (ComboBox<String>) loader.getNamespace().get(id); }
        TextField field(String id) { return (TextField) loader.getNamespace().get(id); }
        TextArea input() { return (TextArea) loader.getNamespace().get("cipherInputArea"); }
        void select(String algorithm, String mode, String padding) {
            combo("symmetricAlgorithmCombo").setValue(algorithm);
            if (mode != null) combo("cipherModeCombo").setValue(mode);
            if (padding != null) combo("paddingCombo").setValue(padding);
        }
        void material(String key, String iv, String tag, String aad) {
            field("symmetricKeyField").setText(key); field("ivField").setText(iv);
            field("gcmTagField").setText(tag); field("aadField").setText(aad);
        }
        void inputs(String inputValue, String text, String outputValue) {
            inputFormat.setValue(inputValue); outputFormat.setValue(outputValue); input().setText(text);
        }
        void encrypt() { controller.handleSymmetricEncrypt(); }
        void decrypt() { controller.handleSymmetricDecrypt(); }
    }

    private static final class Recorder implements StatusReporter {
        private final ComboBox<String> inputFormat;
        private final ComboBox<String> outputFormat;
        private final List<String> lines = new ArrayList<>();
        private final List<OperationResult> published = new ArrayList<>();
        Recorder(ComboBox<String> inputFormat, ComboBox<String> outputFormat) {
            this.inputFormat = inputFormat; this.outputFormat = outputFormat;
        }
        String drain() { String text = String.join("\n", lines); lines.clear(); return text; }
        @Override public void updateStatus(String message) { lines.add("status " + message); }
        @Override public void updateInspector(String operation, byte[] input, byte[] output, List<OperationDetail> details) { }
        @Override public void showError(String title, String message) { lines.add("error " + title + ": " + message); }
        @Override public void showInfo(String title, String message) { lines.add("info " + title + ": " + message); }
        @Override public void setInputFormat(String format) { inputFormat.setValue(format); }
        @Override public void setOutputFormat(String format) { outputFormat.setValue(format); }
        @Override public void publish(OperationResult result) { published.add(result); }
        String statusText() {
            return lines.stream().filter(line -> line.startsWith("status ")).reduce((a, b) -> a + "\n" + b).orElse("");
        }
    }

    @FunctionalInterface private interface FxAction { void run() throws Exception; }
    @FunctionalInterface private interface FxSupplier<T> { T get() throws Exception; }
}
