package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.ClipboardShelfManager;
import com.cryptocarver.model.ClipboardEntry;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Pins what the production shell exposes to Copy/Expand and Clipboard Shelf. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ResultCaptureCharacterizationUITest {
    private ModernMainController shell;
    private Parent root;
    private Stage stage;
    private SecretVisibilityProfile oldVisibility;
    private LanguagePreference oldLanguage;
    private String oldRoute;
    private final List<String> syntheticSecrets = new ArrayList<>();

    @BeforeAll static void startFx() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); } catch (IllegalStateException started) { ready.countDown(); }
        assertTrue(ready.await(15, TimeUnit.SECONDS));
        fx(() -> Platform.setImplicitExit(false));
    }

    @BeforeEach void loadShell() throws Exception {
        AppSettings settings = AppSettings.getInstance();
        oldVisibility = settings.getSecretVisibilityProfile();
        oldLanguage = settings.getLanguagePreference();
        oldRoute = settings.getLastRoute();
        syntheticSecrets.clear();
        I18nService.getInstance().setPreference(LanguagePreference.EN);
        settings.setLastRoute("");
        ClipboardShelfManager.getInstance().clear();
        fx(() -> {
            var loader = Fxml.loader("/fxml/main-view-modern.fxml");
            root = loader.load(); shell = loader.getController();
            stage = new Stage(); stage.setScene(new Scene(root, 1400, 900));
        });
    }

    @AfterEach void closeShell() throws Exception {
        fx(() -> {
            if (shell != null) shell.shutdown();
            if (stage != null) { stage.close(); stage.setScene(null); }
            AppSettings.getInstance().setSecretVisibilityProfile(oldVisibility);
            I18nService.getInstance().setPreference(oldLanguage);
            AppSettings.getInstance().setLastRoute(oldRoute);
        });
        ClipboardShelfManager.getInstance().clear();
    }

    @Test void productionShellCaptureTranscript() throws Exception {
        List<String> transcript = new ArrayList<>();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream(), stderr = new ByteArrayOutputStream();
        PrintStream originalOut = System.out, originalErr = System.err;
        try (PrintStream capturedOut = new PrintStream(stdout, true, StandardCharsets.UTF_8);
             PrintStream capturedErr = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
            System.setOut(capturedOut); System.setErr(capturedErr);
            for (SecretVisibilityProfile profile : SecretVisibilityProfile.values()) {
                fx(() -> AppSettings.getInstance().setSecretVisibilityProfile(profile));
                runProductionOperations(profile, transcript);
                recordPrivateKeyCapture(profile, transcript);
                recordLimits(profile, transcript);
            }
        } finally {
            System.setOut(originalOut); System.setErr(originalErr);
        }
        String joined = String.join("\n", transcript);
        String logs = stdout.toString(StandardCharsets.UTF_8) + stderr.toString(StandardCharsets.UTF_8);
        for (String secret : syntheticSecrets) {
            assertFalse(secret.isBlank());
            assertFalse(logs.contains(secret), "Synthetic secret reached application logs");
            assertFalse(joined.contains(secret), "Synthetic secret reached the review transcript");
        }
        assertFalse(joined.contains("4000001234567899"));
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/result-capture-transcript.txt"), joined + "\n");
        assertEquals("15f9b8043aca2c9bb321205b121e7bb6272b162ce49f2115fd9092216e54237b", sha(joined));
    }

    /** Runs the actual production module handlers inside the loaded modern shell. */
    private void runProductionOperations(SecretVisibilityProfile profile, List<String> transcript) throws Exception {
        fx(() -> {
            shell.navigateToModule("Hashing");
            root.applyCss(); root.layout();
            ((javafx.scene.control.ComboBox<String>) root.lookup("#hashAlgorithmCombo")).setValue("SHA-256");
            ((TextArea) root.lookup("#hashInputArea")).setText("capture specimen");
            ((GenericController) read(shell, "genericContainerController")).handleCalculateHash();
        });
        recordActual(profile, "Hash", "Text (UTF-8) → Hexadecimal", transcript);

        fx(() -> {
            shell.navigateToModule("Manual Conversion");
            root.applyCss(); root.layout();
            ((javafx.scene.control.ComboBox<String>) root.lookup("#manualInputFormatCombo")).setValue("Hexadecimal");
            ((javafx.scene.control.ComboBox<String>) root.lookup("#manualOutputFormatCombo")).setValue("Text (UTF-8)");
            ((TextArea) root.lookup("#manualInputArea")).setText("4142");
            ((GenericController) read(shell, "genericContainerController")).handleManualConvert();
        });
        recordActual(profile, "Manual Conversion", "Hexadecimal → Text (UTF-8)", transcript);

        fx(() -> {
            shell.navigateToModule("Symmetric Ciphers");
            root.applyCss(); root.layout();
            ((javafx.scene.control.ComboBox<String>) root.lookup("#symmetricAlgorithmCombo")).setValue("AES-128");
            ((javafx.scene.control.ComboBox<String>) root.lookup("#cipherModeCombo")).setValue("CBC");
            ((javafx.scene.control.ComboBox<String>) root.lookup("#paddingCombo")).setValue("PKCS5Padding");
            ((javafx.scene.control.TextField) root.lookup("#symmetricKeyField")).setText("000102030405060708090A0B0C0D0E0F");
            ((javafx.scene.control.TextField) root.lookup("#ivField")).setText("0F0E0D0C0B0A09080706050403020100");
            ((TextArea) root.lookup("#cipherInputArea")).setText("capture specimen");
            ((CipherController) read(shell, "cipherController")).handleSymmetricEncrypt();
        });
        syntheticSecrets.add("000102030405060708090A0B0C0D0E0F");
        syntheticSecrets.add("0F0E0D0C0B0A09080706050403020100");
        recordActual(profile, "Symmetric Cipher", "Text (UTF-8) → Hexadecimal", transcript);

        fx(() -> {
            shell.navigateToModule("Key Generation");
            root.applyCss(); root.layout();
            ((javafx.scene.control.ComboBox<String>) root.lookup("#keyTypeCombo")).setValue("AES-128");
            shell.getKeysController().handleGenerateKey();
        });
        recordActual(profile, "Symmetric Key Generation", "Hexadecimal", transcript);

        fx(() -> {
            shell.navigateToModule("RSA Key Generation");
            root.applyCss(); root.layout();
            shell.getKeysController().handleGenerateRSA();
            javafx.scene.control.TabPane tabs = (javafx.scene.control.TabPane) root.lookup("#rsaKeyMaterialTabs");
            tabs.getSelectionModel().select(1);
            TextArea privateArea = (TextArea) root.lookup("#rsaPrivateKeyArea");
            Object tracker = read(shell, "resultAreaTracker");
            invoke(tracker, "focus", new Class<?>[]{TextArea.class}, new Object[]{privateArea});
            invoke(tracker, "markUpdated", new Class<?>[]{TextArea.class}, new Object[]{privateArea});
        });
        recordActual(profile, "Asymmetric Key Generation", "PEM", transcript);

        fx(() -> {
            shell.navigateToModule("Clear PIN Blocks");
            root.applyCss(); root.layout();
            ((javafx.scene.control.TextField) root.lookup("#pinField")).setText("1234");
            ((javafx.scene.control.TextField) root.lookup("#panFieldEncode")).setText("4000001234567899");
            ((PaymentsController) read(shell, "paymentsController")).handleEncodePinBlock();
        });
        recordActual(profile, "PIN Block", "ISO-0 Hexadecimal", transcript);
    }

    private void recordActual(SecretVisibilityProfile profile, String operation, String format, List<String> transcript) throws Exception {
        AtomicReference<String> current = new AtomicReference<>(), shelf = new AtomicReference<>(), status = new AtomicReference<>();
        AtomicReference<String> classification = new AtomicReference<>("<none>");
        AtomicReference<OperationResult> resultRef = new AtomicReference<>();
        fx(() -> {
            current.set((String) invoke(shell, "resolveCurrentOutputText"));
            shelf.set((String) invoke(shell, "resolveShelfCaptureText", new Class<?>[]{TextArea.class}, new Object[]{null}));
            Object result = read(shell, "lastPublishedResultSnapshot");
            if (result instanceof OperationResult published) {
                resultRef.set(published);
                classification.set(com.cryptocarver.model.ResultPresentationPolicy.classifyPublishedResult(published).name());
            }
            shell.handleAddCurrentOutputToShelf(); status.set(status());
        });
        ClipboardEntry entry = ClipboardShelfManager.getInstance().getEntries().isEmpty() ? null : ClipboardShelfManager.getInstance().getEntries().get(0);
        String generatedSymmetric = "";
        String generatedPrivate = "";
        String generatedPublic = "";
        if (operation.equals("Symmetric Key Generation")) generatedSymmetric = ((TextArea) root.lookup("#generatedKeyField")).getText();
        if (operation.equals("Asymmetric Key Generation")) {
            generatedPrivate = ((TextArea) root.lookup("#rsaPrivateKeyArea")).getText();
            generatedPublic = ((TextArea) root.lookup("#rsaPublicKeyArea")).getText();
        }
        if (operation.equals("Symmetric Key Generation") && !generatedSymmetric.isBlank()) syntheticSecrets.add(generatedSymmetric);
        if (operation.equals("Asymmetric Key Generation") && !generatedPrivate.isBlank()) syntheticSecrets.add(generatedPrivate);
        String copy = normalizeGenerated(operation, current.get(), generatedSymmetric, generatedPrivate, generatedPublic);
        String shelfCapture = normalizeGenerated(operation, shelf.get(), generatedSymmetric, generatedPrivate, generatedPublic);
        String entryContent = entry == null ? "<none>" : normalizeGenerated(operation, entry.getValue(), generatedSymmetric, generatedPrivate, generatedPublic);
        if (resultRef.get() != null && operation.equals("Symmetric Key Generation")
                && profile == SecretVisibilityProfile.FULL_LAB) assertEquals(generatedSymmetric, current.get());
        if (resultRef.get() != null && operation.equals("Asymmetric Key Generation")
                && profile == SecretVisibilityProfile.FULL_LAB)
            transcript.add("FULL_LAB | RSA Copy/Expand includes generated private area=" + current.get().contains(generatedPrivate));
        if (entry != null && profile != SecretVisibilityProfile.FULL_LAB)
            assertNotEquals(OperationDetail.Classification.SECRET, entry.getClassification());
        if (operation.equals("PIN Block")) {
            assertEquals("041234FEDCBA9876", current.get());
            assertEquals(current.get(), shelf.get());
            copy = "<clear-PIN-block-captured>";
            shelfCapture = "<clear-PIN-block-captured>";
            entryContent = "<clear-PIN-block-captured>";
            if (profile != SecretVisibilityProfile.FULL_LAB)
                transcript.add(profile + " | BUG: restricted profile Copy/Expand and Shelf still capture clear PIN block (result/Shelf classification PUBLIC)");
        }
        transcript.add(profile + " | " + operation + " | input=" + operationInput(operation)
                + " | format=" + format + " | result-classification=" + classification.get()
                + " | copy/expand=" + safe(copy) + " | shelf-resolver=" + safe(shelfCapture)
                + " | shelf-entry=" + (entry == null ? "<none>" : "content=" + safe(entryContent) + ",format=" + entry.getFormat() + ",classification=" + entry.getClassification())
                + " | status=" + normalizedStatus(status.get()));
        ClipboardShelfManager.getInstance().clear();
    }

    private void recordPrivateKeyCapture(SecretVisibilityProfile profile, List<String> transcript) throws Exception {
        AtomicReference<Boolean> blocked = new AtomicReference<>(false);
        AtomicReference<String> privateKey = new AtomicReference<>("");
        fx(() -> {
            TextArea area = (TextArea) root.lookup("#rsaPrivateKeyArea");
            privateKey.set(area.getText());
            String visible = (String) invoke(shell, "renderResultArea", new Class<?>[]{TextArea.class}, new Object[]{area});
            blocked.set(visible.isEmpty() || "***MASKED***".equals(visible));
        });
        String privatePem = privateKey.get();
        transcript.add(profile + " | asymmetric key generation | Keys private-area="
                + normalizeGenerated("Asymmetric Key Generation", privatePem, "", privatePem, "") + " | blocked=" + blocked.get());
        OperationResult placeholder = OperationResult.forOperation("Asymmetric Key Generation")
                .detail(OperationDetail.secretDetail("privateKey", "PRIVATE KEY MATERIAL NOT RECORDED"))
                .build();
        transcript.add(profile + " | private-material placeholder | rendered=" + safe(shell.renderPublishedResult(placeholder, profile)));
        AtomicReference<String> genericCapture = new AtomicReference<>("");
        fx(() -> {
            invoke(shell, "clearPublishedResultSnapshot");
            TextArea generic = new TextArea(privatePem); generic.setId("fixtureResultArea"); generic.setEditable(false);
            generic.getStyleClass().add("result-area");
            ((javafx.scene.layout.VBox) root.lookup("#contentContainer")).getChildren().add(generic);
            Object tracker = read(shell, "resultAreaTracker");
            invoke(tracker, "register", new Class<?>[]{TextArea.class}, new Object[]{generic});
            invoke(tracker, "focus", new Class<?>[]{TextArea.class}, new Object[]{generic});
            invoke(tracker, "markUpdated", new Class<?>[]{TextArea.class}, new Object[]{generic});
            genericCapture.set((String) invoke(shell, "resolveCurrentOutputText"));
            ((javafx.scene.layout.VBox) root.lookup("#contentContainer")).getChildren().remove(generic);
        });
        transcript.add(profile + " | generic private PEM candidate | raw-private-material-exposed=" + privatePem.equals(genericCapture.get()));
    }

    private void recordLimits(SecretVisibilityProfile profile, List<String> transcript) throws Exception {
        AtomicReference<String> current = new AtomicReference<>(), shelf = new AtomicReference<>(), status = new AtomicReference<>();
        fx(() -> {
            set(shell, "currentActiveOperation", "Manual Conversion");
            invoke(shell, "handleClearOutput");
            current.set((String) invoke(shell, "resolveCurrentOutputText"));
            shelf.set((String) invoke(shell, "resolveShelfCaptureText", new Class<?>[]{TextArea.class}, new Object[]{null}));
            shell.handleAddCurrentOutputToShelf(); status.set(status());
        });
        transcript.add(profile + " | no-result | copy/expand=" + safe(current.get()) + " | shelf-resolver=" + safe(shelf.get()) + " | status=" + normalizedStatus(status.get()));
        ClipboardShelfManager.getInstance().clear();
        AtomicReference<String> publishedCopy = new AtomicReference<>(), shelfArea = new AtomicReference<>();
        fx(() -> {
            set(shell, "currentActiveOperation", "Fixture Module");
            shell.publish(OperationResult.forOperation("Fixture Result").enrichedOutput("PUBLISHED-OUTPUT").build());
            TextArea visible = new TextArea("VISIBLE-SCREEN-OUTPUT"); visible.setId("fixtureResultArea"); visible.setEditable(false);
            visible.getStyleClass().add("result-area");
            ((javafx.scene.layout.VBox) root.lookup("#contentContainer")).getChildren().add(visible);
            Object tracker = read(shell, "resultAreaTracker");
            invoke(tracker, "register", new Class<?>[]{TextArea.class}, new Object[]{visible});
            invoke(tracker, "focus", new Class<?>[]{TextArea.class}, new Object[]{visible});
            invoke(tracker, "markUpdated", new Class<?>[]{TextArea.class}, new Object[]{visible});
            publishedCopy.set((String) invoke(shell, "resolveCurrentOutputText"));
            shelfArea.set((String) invoke(shell, "resolveShelfCaptureText", new Class<?>[]{TextArea.class}, new Object[]{null}));
            ((javafx.scene.layout.VBox) root.lookup("#contentContainer")).getChildren().remove(visible);
        });
        transcript.add(profile + " | visible-area-vs-publication | copy/expand=" + safe(publishedCopy.get()) + " | shelf-resolver=" + safe(shelfArea.get()));
        AtomicReference<String> staleShelf = new AtomicReference<>(), noVisibleCopy = new AtomicReference<>(), noVisibleShelf = new AtomicReference<>();
        fx(() -> {
            invoke(read(shell, "resultAreaTracker"), "clearSelection");
            set(shell, "currentActiveOperation", "Other Module");
            staleShelf.set((String) invoke(shell, "resolveShelfCaptureText", new Class<?>[]{TextArea.class}, new Object[]{null}));
            invoke(shell, "handleClearOutput");
            TextArea hidden = new TextArea("SYNTHETIC-HIDDEN-OUTPUT"); hidden.setId("fixtureResultArea"); hidden.setEditable(false);
            hidden.getStyleClass().add("result-area"); hidden.setVisible(false); hidden.setManaged(false);
            Object tracker = read(shell, "resultAreaTracker");
            invoke(tracker, "register", new Class<?>[]{TextArea.class}, new Object[]{hidden});
            invoke(tracker, "focus", new Class<?>[]{TextArea.class}, new Object[]{hidden});
            noVisibleCopy.set((String) invoke(shell, "resolveCurrentOutputText"));
            noVisibleShelf.set((String) invoke(shell, "resolveShelfCaptureText", new Class<?>[]{TextArea.class}, new Object[]{hidden}));
            ((javafx.scene.layout.VBox) root.lookup("#contentContainer")).getChildren().removeIf(node -> node.getId() != null && node.getId().equals("fixtureResultArea"));
        });
        transcript.add(profile + " | published-on-other-screen | shelf-resolver=" + safe(staleShelf.get()));
        transcript.add(profile + " | non-visible-output | copy/expand=" + safe(noVisibleCopy.get()) + " | shelf-resolver=" + safe(noVisibleShelf.get()));
    }

    private String status() throws Exception {
        Object label = read(shell, "statusLabel");
        return label instanceof javafx.scene.control.Label l ? l.getText() : String.valueOf(label);
    }
    private static String normalizeGenerated(String operation, String value, String symmetric, String privatePem, String publicPem) {
        if (value == null || value.isBlank() || value.equals("***MASKED***")) return value;
        if (operation.equals("Symmetric Key Generation") && value.equals(symmetric)) return "<generated-symmetric-key>";
        if (operation.equals("Asymmetric Key Generation")) {
            if (value.equals(privatePem)) return "<generated-private-key>";
            if (value.equals(publicPem)) return "<generated-public-key>";
            if (!privatePem.isEmpty()) value = value.replace(privatePem, "<generated-private-key>");
            if (!publicPem.isEmpty()) value = value.replace(publicPem, "<generated-public-key>");
            value = value.replaceAll("(?s)-----BEGIN PUBLIC KEY-----.*?-----END PUBLIC KEY-----", "<generated-public-key>");
            if (value.contains("BEGIN PRIVATE KEY")) return "<generated-asymmetric-result>";
        }
        return value;
    }
    private static String operationInput(String operation) {
        return switch (operation) {
            case "Hash" -> "capture specimen | UTF-8 | PUBLIC";
            case "Manual Conversion" -> "4142 | Hexadecimal | PUBLIC";
            case "Symmetric Cipher" -> "capture specimen | UTF-8 | PUBLIC; synthetic AES key+IV | Hexadecimal | SECRET";
            case "Symmetric Key Generation" -> "AES-128 | algorithm parameter | PUBLIC";
            case "Asymmetric Key Generation" -> "RSA defaults | parameters | PUBLIC";
            case "PIN Block" -> "synthetic PIN+PAN fields | SECRET; ISO-0 | format";
            default -> "<none>";
        };
    }
    private static String safe(String value) { return value == null || value.isBlank() ? "<empty>" : value.replace("\n", "\\n"); }
    private static String normalizedStatus(String value) { if (value == null) return "<none>"; int separator = value.indexOf(" · "); return separator < 0 ? value : value.substring(0, separator); }
    private static byte[] bytes(String text) { return text.getBytes(StandardCharsets.UTF_8); }
    private static String sha(String text) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes(text))); }
    private static Object invoke(Object target, String name) throws Exception { Method m = target.getClass().getDeclaredMethod(name); m.setAccessible(true); return m.invoke(target); }
    private static Object invoke(Object target, String name, Class<?>[] types, Object[] args) throws Exception { Method m = target.getClass().getDeclaredMethod(name, types); m.setAccessible(true); return m.invoke(target, args); }
    private static void set(Object target, String name, Object value) throws Exception { Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); f.set(target, value); }
    private static Object read(Object target, String name) throws Exception { Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target); }
    @FunctionalInterface private interface FxAction { void run() throws Exception; }
    private static void fx(FxAction action) throws Exception {
        CountDownLatch done = new CountDownLatch(1); AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> { try { action.run(); } catch (Throwable error) { failure.set(error); } finally { done.countDown(); } });
        assertTrue(done.await(45, TimeUnit.SECONDS)); if (failure.get() != null) throw new AssertionError(failure.get());
    }
}
