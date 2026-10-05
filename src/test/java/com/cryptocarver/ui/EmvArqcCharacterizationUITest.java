package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.ClipboardEntry;
import com.cryptocarver.model.ClipboardShelfManager;
import com.cryptocarver.model.HistoryManager;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.ResultPresentationPolicy;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

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

/** Pins ARQC UI behavior, validation feedback, and secret visibility across result surfaces. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class EmvArqcCharacterizationUITest {
    private static final String SESSION_KEY = "38F14068B3EA57C194F8E3A20D51E3E6";
    private static final String ARQC = "A8DB2B65F9C821F1";
    private static final String ALTERED_ARQC = "A8DB2B65F9C821F0";

    @TempDir Path tempDir;
    private AppSettings previousSettings;
    private LanguagePreference previousLanguage;
    private String previousTestMode;
    private List<ClipboardEntry> previousShelf;
    private ClipboardShelfManager shelf;
    private ModernMainController shell;
    private Stage stage;
    private Parent root;

    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); }
        catch (IllegalStateException alreadyStarted) { ready.countDown(); }
        assertTrue(ready.await(15, TimeUnit.SECONDS), "JavaFX toolkit must start");
        Platform.setImplicitExit(false);
    }

    @BeforeEach
    void isolateApplicationState() throws Exception {
        previousSettings = AppSettings.getInstance();
        previousLanguage = I18nService.getInstance().getPreference();
        previousTestMode = System.getProperty("test.mode");
        AppSettings isolated = new AppSettings(tempDir.resolve("settings.json"));
        isolated.setLanguagePreference(LanguagePreference.EN);
        isolated.setLastRoute("");
        isolated.setSecretVisibilityProfile(SecretVisibilityProfile.FULL_LAB);
        AppSettings.setInstanceForTesting(isolated);
        I18nService.getInstance().setPreference(LanguagePreference.EN);
        shelf = ClipboardShelfManager.getInstance();
        previousShelf = List.copyOf(shelf.getEntries());
        shelf.clear();
        System.setProperty("test.mode", "true");

        onFx(() -> {
            var loader = UiTestFxml.loader("/fxml/main-view-modern.fxml");
            root = loader.load();
            shell = loader.getController();
            set(shell, "historyManager", new HistoryManager(tempDir.resolve("history.json")));
            stage = new Stage();
            stage.setScene(new Scene(root, 1400, 900));
            stage.show();
            shell.navigateToModule("EMV Tool");
            root.applyCss();
            root.layout();
        });
    }

    @AfterEach
    void restoreApplicationState() throws Exception {
        onFx(() -> {
            try {
                if (shell != null) shell.shutdown();
                if (stage != null) { stage.close(); stage.setScene(null); }
            } finally {
                AppSettings.setInstanceForTesting(previousSettings);
                I18nService.getInstance().setPreference(previousLanguage);
                I18nService.getInstance().refreshFromSettings();
                if (previousTestMode == null) System.clearProperty("test.mode");
                else System.setProperty("test.mode", previousTestMode);
                shell = null;
                stage = null;
                root = null;
            }
        });
        if (shelf != null) {
            shelf.clear();
            for (int i = previousShelf.size() - 1; i >= 0; i--) shelf.addEntry(previousShelf.get(i));
            assertEquals(previousShelf, shelf.getEntries());
        }
    }

    @Test
    void arqcGenerationVerificationAndVisibilityHaveStableTranscript() throws Exception {
        List<String> transcript = new ArrayList<>();
        List<String> violations = new ArrayList<>();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        try (PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
            System.setOut(out);
            System.setErr(err);
            onFx(() -> {
                EMVController controller = (EMVController) get(shell, "emvContainerController");
                assertNotNull(controller, "The real EMV screen must be loaded");
                TextArea result = (TextArea) get(controller, "arqcResultArea");

                for (LanguagePreference language : List.of(LanguagePreference.EN, LanguagePreference.ES)) {
                    I18nService.getInstance().setPreference(language);
                    assertReadableError(controller, result, language, "invalid-session-key-length", "1234", "12345678");
                    assertReadableError(controller, result, language, "invalid-un", SESSION_KEY, "ZZZZZZZZ");
                }
                transcript.addAll(invalidTranscript);
                I18nService.getInstance().setPreference(LanguagePreference.EN);

                for (SecretVisibilityProfile profile : List.of(SecretVisibilityProfile.FULL_LAB,
                        SecretVisibilityProfile.MASKED, SecretVisibilityProfile.REDACTED)) {
                    AppSettings.getInstance().setSecretVisibilityProfile(profile);
                    setTransaction(controller, SESSION_KEY, "12345678");
                    result.clear();
                    clearPublishedState();
                    controller.handleGenerateARQC();
                    assertTrue(result.getText().contains(ARQC), "Cross-checked invented-key ARQC changed");
                    OperationResult generated = lastResult(shell);
                    assertEquals("ARQC Generation", generated.getOperation());
                    transcript.add(profile + " generate classification="
                            + ResultPresentationPolicy.classifyPublishedResult(generated));
                    inspectSurfaces(profile, "generate", List.of(SESSION_KEY, ARQC), shell, root,
                            transcript, violations);

                    clearPublishedState();
                    controller.handleVerifyARQC();
                    assertTrue(result.getText().contains("ARQC IS VALID"), "Generated ARQC must verify");
                    OperationResult verified = lastResult(shell);
                    assertEquals("ARQC Verification", verified.getOperation());
                    transcript.add(profile + " verify-valid classification="
                            + ResultPresentationPolicy.classifyPublishedResult(verified));
                    inspectSurfaces(profile, "verify-valid", List.of(SESSION_KEY, ARQC), shell, root,
                            transcript, violations);

                    for (LanguagePreference language : List.of(LanguagePreference.EN, LanguagePreference.ES)) {
                        I18nService.getInstance().setPreference(language);
                        result.setText(ALTERED_ARQC);
                        clearPublishedState();
                        controller.handleVerifyARQC();
                        Labeled status = (Labeled) root.lookup("#statusLabel");
                        String expectedStatus = I18nService.getInstance().text("module.emv.feedback.arqcInvalid");
                        if (status == null || !status.getText().contains(expectedStatus)) {
                            localizationFailures.add(language + " altered-ARQC status is not localized");
                        }
                        transcript.add(profile + " altered-ARQC " + language + " status="
                                + (status != null && status.getText().contains(expectedStatus) ? expectedStatus : "missing"));
                        OperationResult altered = lastResult(shell);
                        transcript.add(profile + " verify-altered classification="
                                + ResultPresentationPolicy.classifyPublishedResult(altered));
                        inspectSurfaces(profile, "verify-altered", List.of(SESSION_KEY, ALTERED_ARQC), shell, root,
                                transcript, violations);
                    }
                    I18nService.getInstance().setPreference(LanguagePreference.EN);
                    clearPublishedState();
                }
            });
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }

        String logs = stdout.toString(StandardCharsets.UTF_8) + stderr.toString(StandardCharsets.UTF_8);
        if (logs.contains(SESSION_KEY) || logs.contains(ARQC) || logs.contains(ALTERED_ARQC)) {
            violations.add("telemetry/logs leaked an invented session key or ARQC");
        }
        transcript.add("telemetry/logs=" + (violations.stream().anyMatch(v -> v.startsWith("telemetry/")) ? "leaked" : "safe"));
        violations.addAll(localizationFailures);
        String joined = String.join("\n", transcript);
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/emv-arqc-transcript.txt"), joined + "\n");
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(joined.getBytes(StandardCharsets.UTF_8)));
        assertEquals("83596873ab42981366e1570edeac8c53a8a0b0f301cc496faa90aaeec49c851d", digest,
                String.join("\n", violations) + "\nTranscript:\n" + joined);
        assertTrue(violations.isEmpty(), String.join("\n", violations) + "\nTranscript:\n" + joined);
    }

    private final List<String> invalidTranscript = new ArrayList<>();
    private final List<String> localizationFailures = new ArrayList<>();

    private void assertReadableError(EMVController controller, TextArea result, LanguagePreference language,
                                    String name, String sessionKey, String un) throws Exception {
        setTransaction(controller, sessionKey, un);
        result.clear();
        controller.handleGenerateARQC();
        String message = concise(result.getText());
        assertFalse(message.isBlank(), name + " must show readable feedback in " + language);
        assertTrue(message.startsWith("Error:") || message.startsWith("Error de"), message);
        String messageKey = switch (name) {
            case "invalid-session-key-length" -> "module.emv.error.arqcSessionKeyLength";
            case "invalid-un" -> "module.emv.error.arqcUnFormat";
            default -> throw new IllegalArgumentException("Unexpected ARQC validation case: " + name);
        };
        String localized = I18nService.getInstance().text(messageKey);
        if (!localized.equals(message)) localizationFailures.add(language + " " + name + " is not localized");
        invalidTranscript.add(language + " " + name + "=" + stableDiagnostic(message, localized));
    }

    private static String stableDiagnostic(String actual, String expected) {
        if (expected.equals(actual)) return expected;
        String prefix = actual.startsWith("Error de") ? "Error de" : "Error:";
        return prefix + " <jdk-exception>";
    }

    private void setTransaction(EMVController controller, String sessionKey, String un) throws Exception {
        ((TextField) get(controller, "skARQCField")).setText(sessionKey);
        ((TextField) get(controller, "amountField")).setText("000000001000");
        ((TextField) get(controller, "amountOtherField")).setText("000000000000");
        ((TextField) get(controller, "currencyField")).setText("0978");
        ((TextField) get(controller, "countryField")).setText("0724");
        ((TextField) get(controller, "atcARQCField")).setText("0001");
        ((TextField) get(controller, "tvrField")).setText("0000000000");
        ((TextField) get(controller, "txDateField")).setText("250925");
        ((TextField) get(controller, "txTypeField")).setText("00");
        ((TextField) get(controller, "unField")).setText(un);
        ((TextField) get(controller, "iccDataField")).setText("1800000103A4A000");
        ((TextArea) get(controller, "arqcTerminalDataField")).clear();
        ComboBox<String> padding = (ComboBox<String>) get(controller, "arqcPaddingMethodCombo");
        padding.setValue(padding.getItems().stream().filter(item -> item.contains("Method 2")).findFirst().orElseThrow());
    }

    private void clearPublishedState() {
        shelf.clear();
        shell.getHistoryManager().clearHistory();
    }

    private void inspectSurfaces(SecretVisibilityProfile profile, String operation, List<String> secrets,
                                 ModernMainController shell, Parent root, List<String> transcript,
                                 List<String> violations) throws Exception {
        String viewer = shell.resolveCurrentOutputText();
        String inspector = nodeText(root.lookup("#inspectorPanel"));
        String history = new com.google.gson.Gson().toJson(shell.getHistoryManager().getHistoryItems());
        Labeled statusLabel = (Labeled) root.lookup("#statusLabel");
        shell.handleAddCurrentOutputToShelf();
        String shelfContent = shelf.getEntries().stream().map(ClipboardEntry::getValue)
                .reduce("", (left, right) -> left + "\n" + right);
        closeExpandedViewer(shell);
        if (viewer != null && !viewer.isBlank()) shell.handleOpenExpandedResultViewer();
        String expanded = expandedContent(shell);
        boolean fullLab = profile == SecretVisibilityProfile.FULL_LAB;
        String[] surfaces = {viewer, inspector, history, shelfContent,
                statusLabel == null ? "" : statusLabel.getText(), expanded};
        String[] names = {"result", "inspector", "history", "shelf", "status", "expanded"};
        List<String> states = new ArrayList<>();
        for (int i = 0; i < surfaces.length; i++) {
            String content = surfaces[i];
            boolean containsSecret = secrets.stream().anyMatch(secret -> content != null && content.contains(secret));
            if (fullLab && i == 0 && !containsSecret) {
                violations.add("FULL_LAB " + operation + " should show invented key/cryptogram in result viewer");
            } else if (fullLab && i == 3 && !shelf.getEntries().isEmpty() && !containsSecret) {
                violations.add("FULL_LAB " + operation + " should retain an explicitly captured result on Shelf");
            } else if (!fullLab && containsSecret) {
                violations.add(profile + " " + operation + " leaked an invented key/cryptogram in " + names[i]);
            }
            states.add(names[i] + "=" + (containsSecret ? "visible" : "safe"));
        }
        transcript.add(profile + " " + operation + " " + String.join(" ", states));
        closeExpandedViewer(shell);
    }

    private static OperationResult lastResult(ModernMainController shell) throws Exception {
        return (OperationResult) get(shell, "lastPublishedResultSnapshot");
    }

    private static String expandedContent(ModernMainController shell) throws Exception {
        Object viewer = get(shell, "expandedTextViewer");
        Object area = get(viewer, "contentArea");
        return area instanceof TextArea text ? text.getText() : "";
    }

    private static void closeExpandedViewer(ModernMainController shell) throws Exception {
        Object viewer = get(shell, "expandedTextViewer");
        var hide = viewer.getClass().getDeclaredMethod("hide");
        hide.setAccessible(true);
        hide.invoke(viewer);
    }

    private static String nodeText(Node node) {
        if (node == null) return "";
        String own = node instanceof Labeled labeled ? labeled.getText()
                : node instanceof TextInputControl text ? text.getText() : "";
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) own += "\n" + nodeText(child);
        }
        return own;
    }

    private static String concise(String text) {
        return text == null ? "" : text.replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }

    private static Object get(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException absent) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException absent) { }
        }
        throw new NoSuchFieldException(name);
    }

    @FunctionalInterface private interface FxAction { void run() throws Exception; }
    private static void onFx(FxAction action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try { action.run(); }
            catch (Throwable failure) { error.set(failure); }
            finally { done.countDown(); }
        });
        assertTrue(done.await(45, TimeUnit.SECONDS), "Timed out waiting for JavaFX");
        if (error.get() != null) throw new AssertionError(error.get());
    }
}
