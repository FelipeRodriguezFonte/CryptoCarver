package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.ClipboardEntry;
import com.cryptocarver.model.ClipboardShelfManager;
import com.cryptocarver.model.HistoryManager;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.ResultPresentationPolicy;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.model.payments.PaymentProfile;
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

/** Pins ARPC visibility, profile loading, and module-local clear behavior. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class EmvArpcCharacterizationUITest {
    private static final String ICC_MASTER_KEY = "0123456789ABCDEFFEDCBA9876543210";
    private static final String PAN = "4111111111111111";
    private static final String SESSION_KEY = "38F14068B3EA57C194F8E3A20D51E3E6";
    private static final String ARQC = "A8DB2B65F9C821F1";
    private static final String METHOD_1_ARPC = "ADCB085B842E0A9D";
    private static final String CSU = "00820000";
    private static final String METHOD_2_ARPC = "54DB2625";

    @TempDir Path tempDir;
    private AppSettings previousSettings;
    private LanguagePreference previousLanguage;
    private String previousTestMode;
    private List<ClipboardEntry> previousShelf;
    private ClipboardShelfManager shelf;
    private ModernMainController shell;
    private Stage stage;
    private Parent root;
    private final List<String> localizationFailures = new ArrayList<>();

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
    void arpcProfilesClearAndVisibilityHaveStableTranscript() throws Exception {
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
                TextArea result = (TextArea) get(controller, "arpcResultArea");
                PaymentProfile profile = inventedArpcProfile();

                for (LanguagePreference language : List.of(LanguagePreference.EN, LanguagePreference.ES)) {
                    I18nService.getInstance().setPreference(language);
                    controller.loadProfile(profile);
                    String expected = I18nService.getInstance().text(
                            "module.payments.status.profileLoaded", profile.getName());
                    Labeled status = (Labeled) root.lookup("#statusLabel");
                    boolean loadedStatus = status != null && status.getText().contains(expected)
                            && status.getAccessibleText().startsWith(expected + ", ");
                    transcript.add("profile " + language + " status=" + (loadedStatus ? "localized" : "missing"));
                    if (!loadedStatus) localizationFailures.add(language + " profile-loaded status is not localized");
                    assertEquals(SESSION_KEY, ((TextField) get(controller, "skARPCField")).getText());
                    assertEquals(ARQC, ((TextField) get(controller, "arqcField")).getText());
                }
                I18nService.getInstance().setPreference(LanguagePreference.EN);

                for (SecretVisibilityProfile visibility : List.of(SecretVisibilityProfile.FULL_LAB,
                        SecretVisibilityProfile.MASKED, SecretVisibilityProfile.REDACTED)) {
                    AppSettings.getInstance().setSecretVisibilityProfile(visibility);
                    for (int method = 1; method <= 2; method++) {
                        fillArpc(controller, method);
                        result.clear();
                        clearPublishedState();
                        controller.handleGenerateARPC();
                        String expectedArpc = method == 1 ? METHOD_1_ARPC : METHOD_2_ARPC;
                        assertTrue(result.getText().contains(expectedArpc), "Invented-key ARPC vector changed");
                        OperationResult generated = lastResult(shell);
                        assertEquals("ARPC Generation", generated.getOperation());
                        transcript.add(visibility + " method-" + method + " classification="
                                + ResultPresentationPolicy.classifyPublishedResult(generated));
                        inspectSurfaces(visibility, "method-" + method,
                                List.of(SESSION_KEY, ARQC, expectedArpc), shell, root, transcript, violations);
                    }
                    clearPublishedState();
                }

                for (LanguagePreference language : List.of(LanguagePreference.EN, LanguagePreference.ES)) {
                    I18nService.getInstance().setPreference(language);
                    fillArpc(controller, 1);
                    ((TextField) get(controller, "skARPCField")).setText("1234");
                    result.clear();
                    controller.handleGenerateARPC();
                    String actual = concise(result.getText());
                    String expected = I18nService.getInstance().text("module.emv.error.arpcSessionKeyLength");
                    String diagnostic = stableDiagnostic(actual, expected);
                    transcript.add(language + " invalid-session-key-length=" + diagnostic);
                    if (!expected.equals(actual)) localizationFailures.add(language + " ARPC key-length error is not localized");
                }
                I18nService.getInstance().setPreference(LanguagePreference.EN);
                clearPublishedState();

                // Populate a reproducible ARQC cache and secret input fields, then characterize module-local clear.
                setTransactionForClear(controller);
                controller.handleGenerateARQC();
                fillArpc(controller, 1);
                controller.handleGenerateARPC();
                shell.handleAddCurrentOutputToShelf();
                int historyBefore = shell.getHistoryManager().getHistoryItems().size();
                List<ClipboardEntry> shelfBefore = List.copyOf(shelf.getEntries());
                controller.handleClear();
                boolean inputsEmpty = controllerTextInputsEmpty(controller);
                boolean outputEmpty = controller.getOutputText().isBlank();
                boolean sharedHistoryPreserved = historyBefore == shell.getHistoryManager().getHistoryItems().size();
                boolean sharedShelfPreserved = shelfBefore.equals(shelf.getEntries());
                transcript.add("clear local-inputs=" + (inputsEmpty ? "empty" : "residual")
                        + " local-output=" + (outputEmpty ? "empty" : "residual")
                        + " history-preserved=" + sharedHistoryPreserved
                        + " shelf-preserved=" + sharedShelfPreserved);
                if (!inputsEmpty || !outputEmpty) violations.add("module clear left local EMV material");
                if (!sharedHistoryPreserved || !sharedShelfPreserved) {
                    violations.add("module clear changed shared history or Shelf");
                }

                controller.handleVerifyARQC();
                String verifyAfterClear = concise(((TextArea) get(controller, "arqcResultArea")).getText());
                String expectedVerify = I18nService.getInstance().text("module.emv.error.generateFirst");
                boolean cacheCleared = verifyAfterClear.equals(expectedVerify);
                transcript.add("clear arqc-cache=" + (cacheCleared ? "cleared" : "residual"));
                if (!cacheCleared) violations.add("module clear retained generated ARQC verification cache");
                controller.handleClear();
                if (!controllerTextInputsEmpty(controller)) violations.add("second module clear left local EMV material");
            });
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }

        String logs = stdout.toString(StandardCharsets.UTF_8) + stderr.toString(StandardCharsets.UTF_8);
        if (List.of(ICC_MASTER_KEY, SESSION_KEY, ARQC, METHOD_1_ARPC, METHOD_2_ARPC)
                .stream().anyMatch(logs::contains)) {
            violations.add("telemetry/logs leaked an invented key or cryptogram");
        }
        transcript.add("telemetry/logs=" + (violations.stream().anyMatch(v -> v.startsWith("telemetry/"))
                ? "leaked" : "safe"));
        violations.addAll(localizationFailures);
        String joined = String.join("\n", transcript);
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/emv-arpc-transcript.txt"), joined + "\n");
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(joined.getBytes(StandardCharsets.UTF_8)));
        assertEquals("4803cbad2a058bc4ccba31d6133090d89aa4c61cf10ef186bb6c47b2d290a89b", digest,
                String.join("\n", violations) + "\nTranscript:\n" + joined);
        assertTrue(violations.isEmpty(), String.join("\n", violations) + "\nTranscript:\n" + joined);
    }

    private PaymentProfile inventedArpcProfile() {
        return new PaymentProfile.Builder("invented-emv-arpc-method-1", "EMV ARPC (Method 1)", "1.0",
                PaymentProfile.ProfileType.EMV)
                .addParameter("method", "Method 1")
                .addInput("imk", ICC_MASTER_KEY)
                .addInput("pan", PAN)
                .addInput("panSeq", "00")
                .addInput("atc", "0001")
                .addInput("arqc", ARQC)
                .addInput("arc", "00")
                .build();
    }

    private void fillArpc(EMVController controller, int method) throws Exception {
        ((TextField) get(controller, "skARPCField")).setText(SESSION_KEY);
        ((TextField) get(controller, "arqcField")).setText(ARQC);
        ((TextField) get(controller, "arcField")).setText("00");
        ((TextField) get(controller, "csuField")).setText(CSU);
        ComboBox<String> selection = (ComboBox<String>) get(controller, "arpcMethodCombo");
        selection.setValue(selection.getItems().stream().filter(item -> item.contains("Method " + method))
                .findFirst().orElseThrow());
    }

    private void setTransactionForClear(EMVController controller) throws Exception {
        ((TextField) get(controller, "skARQCField")).setText(SESSION_KEY);
        ((TextField) get(controller, "amountField")).setText("000000001000");
        ((TextField) get(controller, "amountOtherField")).setText("000000000000");
        ((TextField) get(controller, "currencyField")).setText("0978");
        ((TextField) get(controller, "countryField")).setText("0724");
        ((TextField) get(controller, "atcARQCField")).setText("0001");
        ((TextField) get(controller, "tvrField")).setText("0000000000");
        ((TextField) get(controller, "txDateField")).setText("250925");
        ((TextField) get(controller, "txTypeField")).setText("00");
        ((TextField) get(controller, "unField")).setText("12345678");
        ((TextField) get(controller, "iccDataField")).setText("1800000103A4A000");
        ((TextArea) get(controller, "arqcTerminalDataField")).clear();
    }

    private boolean controllerTextInputsEmpty(EMVController controller) throws Exception {
        for (Class<?> type = controller.getClass(); type != null; type = type.getSuperclass()) {
            for (var field : type.getDeclaredFields()) {
                if (!TextInputControl.class.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                Object value = field.get(controller);
                if (value instanceof TextInputControl input && !input.getText().isBlank()) return false;
            }
        }
        return true;
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
                violations.add("FULL_LAB " + operation + " should show an invented key/cryptogram in result viewer");
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

    private static String stableDiagnostic(String actual, String expected) {
        if (expected.equals(actual)) return expected;
        String prefix = actual.startsWith("Error de") ? "Error de" : actual.startsWith("Error:") ? "Error:" : "Error:";
        return prefix + " <jdk-exception>";
    }

    private static String concise(String text) {
        return text == null ? "" : text.replace('\n', ' ').replaceAll("\\s+", " ").trim();
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
        assertTrue(done.await(60, TimeUnit.SECONDS), "Timed out waiting for JavaFX action");
        if (error.get() != null) throw new AssertionError(error.get());
    }
}
