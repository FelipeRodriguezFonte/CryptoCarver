package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.ClipboardEntry;
import com.cryptocarver.model.ClipboardShelfManager;
import com.cryptocarver.model.HistoryManager;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextArea;
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

/** Pins Authentication's production-shell MAC flows and secret-capture behavior. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class AuthenticationMacCharacterizationUITest {
    private static final String MESSAGE = "invented MAC specimen 65";
    private static final String TEST_KEY = "00112233445566778899AABBCCDDEEFF";
    private static final String SHORT_AES_KEY = "0011223344556677";
    @TempDir Path tempDir;
    private AppSettings previousSettings;
    private LanguagePreference previousLanguage;
    private String previousRoute;
    private String previousTestMode;
    private List<ClipboardEntry> previousShelf;
    private ClipboardShelfManager shelf;
    private ModernMainController shell;
    private Stage stage;

    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); }
        catch (IllegalStateException alreadyStarted) { ready.countDown(); }
        assertTrue(ready.await(15, TimeUnit.SECONDS), "JavaFX toolkit must start");
        Platform.setImplicitExit(false);
    }

    @BeforeEach
    void isolateApplicationState() {
        previousSettings = AppSettings.getInstance();
        previousLanguage = I18nService.getInstance().getPreference();
        previousRoute = previousSettings.getLastRoute();
        previousTestMode = System.getProperty("test.mode");
        AppSettings.setInstanceForTesting(new AppSettings(tempDir.resolve("settings.json")));
        AppSettings.getInstance().setLanguagePreference(LanguagePreference.EN);
        AppSettings.getInstance().setLastRoute("");
        I18nService.getInstance().setPreference(LanguagePreference.EN);
        shelf = ClipboardShelfManager.getInstance();
        previousShelf = List.copyOf(shelf.getEntries());
        shelf.clear();
    }

    @AfterEach
    void restoreApplicationState() throws Exception {
        onFx(() -> {
            if (shell != null) shell.shutdown();
            if (stage != null) { stage.close(); stage.setScene(null); }
            AppSettings.getInstance().setSecretVisibilityProfile(
                    previousSettings.getSecretVisibilityProfile());
            if (previousTestMode == null) System.clearProperty("test.mode");
            else System.setProperty("test.mode", previousTestMode);
            I18nService.getInstance().setPreference(previousLanguage);
            previousSettings.setLastRoute(previousRoute);
            AppSettings.setInstanceForTesting(previousSettings);
            I18nService.getInstance().refreshFromSettings();
            shell = null;
            stage = null;
        });
        if (shelf != null) {
            shelf.clear();
            for (int index = previousShelf.size() - 1; index >= 0; index--) {
                shelf.addEntry(previousShelf.get(index));
            }
        }
    }

    @Test
    void macFlowsAndVisibilityHaveStableTranscript() throws Exception {
        List<String> transcript = new ArrayList<>();
        List<String> violations = new ArrayList<>();
        List<String> macValues = new ArrayList<>();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        AtomicReference<ModernMainController> shellRef = new AtomicReference<>();
        AtomicReference<AuthenticationController> controllerRef = new AtomicReference<>();
        AtomicReference<Parent> rootRef = new AtomicReference<>();
        AtomicReference<Stage> stageRef = new AtomicReference<>();

        try (PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
            System.setOut(out);
            System.setErr(err);
            System.setProperty("test.mode", "true");
            onFx(() -> {
                try {
                    var loader = UiTestFxml.loader("/fxml/main-view-modern.fxml");
                    Parent root = loader.load();
                    ModernMainController shell = loader.getController();
                    Stage stage = new Stage();
                    stage.setScene(new Scene(root, 1400, 900));
                    set(shell, "historyManager", new HistoryManager(tempDir.resolve("history.json")));
                    shell.navigateToModule("Digital Signatures");
                    root.applyCss(); root.layout();
                    AuthenticationController controller =
                            (AuthenticationController) get(shell, "authenticationContainerController");
                    assertNotNull(controller);
                    shell.setInputFormat("Text (UTF-8)");
                    shell.setOutputFormat("Hexadecimal");
                    ((TextArea) root.lookup("#authInputArea")).setText(MESSAGE);
                    shellRef.set(shell); controllerRef.set(controller); rootRef.set(root); stageRef.set(stage);
                    this.shell = shell;
                    this.stage = stage;
                } catch (Throwable failure) {
                    throw new AssertionError(failure);
                }
            });

            onFx(() -> {
                ModernMainController shell = shellRef.get();
                AuthenticationController controller = controllerRef.get();
                Parent root = rootRef.get();
                var keyField = (javafx.scene.control.TextField) root.lookup("#authMacKeyField");
                var algorithm = (javafx.scene.control.ComboBox<String>) root.lookup("#authMacAlgorithmCombo");
                var input = (TextArea) root.lookup("#authInputArea");
                var verifyField = (javafx.scene.control.TextField) root.lookup("#authMacVerifyField");
                assertNotNull(keyField); assertNotNull(algorithm); assertNotNull(input); assertNotNull(verifyField);
                input.setText(MESSAGE);
                algorithm.setValue("HMAC-SHA256");

                keyField.clear();
                for (LanguagePreference language : List.of(LanguagePreference.EN, LanguagePreference.ES)) {
                    I18nService.getInstance().setPreference(language);
                    controller.handleGenerateMAC();
                    String error = errorText(root);
                    assertFalse(error.isBlank(), "Missing-key validation must remain readable in " + language);
                    assertTrue(error.toLowerCase(java.util.Locale.ROOT).contains("mac")
                                    || error.toLowerCase(java.util.Locale.ROOT).contains("key")
                                    || error.toLowerCase(java.util.Locale.ROOT).contains("clave"), error);
                    transcript.add(language + " missing-key=" + concise(error));
                }

                algorithm.setValue("CMAC-AES");
                keyField.setText(SHORT_AES_KEY);
                for (LanguagePreference language : List.of(LanguagePreference.EN, LanguagePreference.ES)) {
                    I18nService.getInstance().setPreference(language);
                    controller.handleGenerateMAC();
                    String error = errorText(root);
                    assertFalse(error.isBlank(), "Wrong-length-key validation must remain readable in " + language);
                    transcript.add(language + " wrong-length-key=" + concise(error));
                }

                algorithm.setValue("HMAC-SHA256");
                keyField.setText(TEST_KEY);

                for (SecretVisibilityProfile profile : SecretVisibilityProfile.values()) {
                    AppSettings.getInstance().setSecretVisibilityProfile(profile);
                    shell.getHistoryManager().clearHistory();
                    I18nService.getInstance().setPreference(LanguagePreference.EN);
                    controller.handleGenerateMAC();
                    OperationResult generated = lastResult(shell);
                    assertNotNull(generated, "Generation publishes one result");
                    assertEquals("MAC Generated", generated.getOperation());
                    String macHex = HexFormat.of().withUpperCase().formatHex(generated.getOutput());
                    assertFalse(macHex.isBlank());
                    macValues.add(macHex);
                    if (generated.getOutputClassification() != OperationDetail.Classification.SECRET) {
                        violations.add(profile + " generated MAC output is classified "
                                + generated.getOutputClassification());
                    }
                    if (generated.getDetails().stream().anyMatch(detail ->
                            "Output".equals(detail.name()) && detail.value().contains(macHex))) {
                        violations.add(profile + " generated MAC is copied into a public Output detail");
                    }
                    inspectRestrictedSurfaces(profile, List.of(macHex), shell, root, transcript, violations);

                    verifyField.setText(macHex);
                    controller.handleVerifyMAC();
                    OperationResult verified = lastResult(shell);
                    assertEquals("MAC Verified", verified.getOperation());
                    assertTrue(verified.getDetails().stream().anyMatch(detail ->
                            "Result".equals(detail.name()) && "VALID".equals(detail.value())));
                    if (verified.getOutputClassification() != OperationDetail.Classification.SECRET) {
                        violations.add(profile + " verified MAC output is classified "
                                + verified.getOutputClassification());
                    }
                    inspectRestrictedSurfaces(profile, List.of(macHex), shell, root, transcript, violations);

                    String altered = alterFirstByte(macHex);
                    macValues.add(altered);
                    for (LanguagePreference language : List.of(LanguagePreference.EN, LanguagePreference.ES)) {
                        I18nService.getInstance().setPreference(language);
                        verifyField.setText(altered);
                        controller.handleVerifyMAC();
                        String error = errorText(root);
                        assertFalse(error.isBlank(), "Altered MAC must show a readable message in " + language);
                        assertTrue(error.toLowerCase(java.util.Locale.ROOT).contains("invalid")
                                        || error.toLowerCase(java.util.Locale.ROOT).contains("failed")
                                        || error.toLowerCase(java.util.Locale.ROOT).contains("inválid")
                                        || error.toLowerCase(java.util.Locale.ROOT).contains("incorrect"),
                                error);
                        transcript.add(profile + " altered-MAC " + language + "=" + concise(error));
                        OperationResult invalid = lastResult(shell);
                        assertTrue(invalid.getDetails().stream().anyMatch(detail ->
                                "Result".equals(detail.name()) && "INVALID".equals(detail.value())));
                        if (invalid.getOutputClassification() != OperationDetail.Classification.SECRET) {
                            violations.add(profile + " invalid verification output is classified "
                                    + invalid.getOutputClassification());
                        }
                        inspectRestrictedSurfaces(profile, List.of(macHex, altered), shell, root,
                                transcript, violations);
                    }
                    I18nService.getInstance().setPreference(LanguagePreference.EN);
                }
            });
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }

        String logs = stdout.toString(StandardCharsets.UTF_8) + stderr.toString(StandardCharsets.UTF_8);
        assertFalse(logs.contains(TEST_KEY), "Invented MAC key reached application logs");
        for (String mac : macValues) assertFalse(logs.contains(mac), "MAC bytes reached application logs");
        transcript.add("telemetry/logs=no invented MAC key or MAC bytes");
        Files.createDirectories(Path.of("target"));
        String joined = String.join("\n", transcript);
        Files.writeString(Path.of("target/authentication-mac-transcript.txt"), joined + "\n");
        assertTrue(violations.isEmpty(), String.join("\n", violations) + "\nTranscript:\n" + joined);
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(joined.getBytes(StandardCharsets.UTF_8)));
        assertEquals("95e2692fb07d42fca1f02df27fda47240d1cdb0120120ed8e153031880dad598", digest, joined);
        onFx(() -> {
            ModernMainController shell = shellRef.get();
            if (shell != null) shell.shutdown();
            if (stageRef.get() != null) { stageRef.get().close(); stageRef.get().setScene(null); }
        });
    }

    private void inspectRestrictedSurfaces(SecretVisibilityProfile profile, List<String> macs,
                                           ModernMainController shell, Parent root,
                                           List<String> transcript, List<String> violations) throws Exception {
        if (profile == SecretVisibilityProfile.FULL_LAB) {
            String viewer = shell.resolveCurrentOutputText();
            assertTrue(macs.stream().anyMatch(viewer::contains),
                    "FULL_LAB keeps the MAC available in the result viewer");
            transcript.add(profile + " MAC capture=visible by policy");
            closeExpandedViewer(shell);
            shelf.clear();
            return;
        }
        String viewer = shell.resolveCurrentOutputText();
        String inspector = nodeText(root.lookup("#inspectorPanel"));
        String status = ((Labeled) root.lookup("#statusLabel")).getText();
        String history = new com.google.gson.Gson().toJson(shell.getHistoryManager().getHistoryItems());
        shelf.clear();
        shell.handleAddCurrentOutputToShelf();
        String shelfContent = shelf.getEntries().stream().map(ClipboardEntry::getValue)
                .reduce("", (left, right) -> left + "\n" + right);
        shell.handleOpenExpandedResultViewer();
        String expanded = expandedContent(shell);
        String combined = viewer + inspector + status + history + shelfContent + expanded;
        for (String mac : macs) {
            if (combined.contains(mac)) {
                violations.add(profile + " MAC exposure: result=" + viewer.contains(mac)
                        + " inspector=" + inspector.contains(mac) + " status=" + status.contains(mac)
                        + " history=" + history.contains(mac) + " shelf=" + shelfContent.contains(mac)
                        + " expanded=" + expanded.contains(mac));
            }
        }
        if (combined.contains(TEST_KEY)) violations.add(profile + " MAC key exposed in a restricted result surface");
        transcript.add(profile + " result=" + captureState(viewer, macs)
                + " inspector=" + captureState(inspector, macs)
                + " history=" + captureState(history, macs)
                + " shelf=" + captureState(shelfContent, macs)
                + " status=" + captureState(status, macs)
                + " expanded=" + captureState(expanded, macs));
        closeExpandedViewer(shell);
    }

    private static String captureState(String surface, List<String> secrets) {
        return secrets.stream().anyMatch(surface::contains) ? "leaked" : "safe";
    }

    private static OperationResult lastResult(ModernMainController shell) throws Exception {
        return (OperationResult) get(shell, "lastPublishedResultSnapshot");
    }

    private static String errorText(Parent root) {
        Node title = root.lookup("#errorBannerTitle");
        Node remedy = root.lookup("#errorBannerRemedy");
        return (title instanceof Labeled labeled ? labeled.getText() : "") + " | "
                + (remedy instanceof Labeled labeled ? labeled.getText() : "");
    }

    private static String expandedContent(ModernMainController shell) throws Exception {
        Object viewer = get(shell, "expandedTextViewer");
        Object area = get(viewer, "contentArea");
        return area instanceof TextArea textArea ? textArea.getText() : "";
    }

    private static void closeExpandedViewer(ModernMainController shell) throws Exception {
        Object viewer = get(shell, "expandedTextViewer");
        MethodAccess.invoke(viewer, "hide");
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

    private static String concise(String text) { return text.replace('\n', ' ').replaceAll("\\s+", " ").trim(); }

    private static String alterFirstByte(String hex) {
        int first = Integer.parseInt(hex.substring(0, 2), 16) ^ 1;
        return String.format(java.util.Locale.ROOT, "%02X%s", first, hex.substring(2));
    }

    private static Object get(Object target, String fieldName) throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void onFx(FxAction action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try { action.run(); } catch (Throwable error) { failure.set(error); }
            finally { done.countDown(); }
        });
        assertTrue(done.await(60, TimeUnit.SECONDS), "Timed out waiting for JavaFX thread");
        if (failure.get() instanceof Exception exception) throw exception;
        if (failure.get() instanceof Error error) throw error;
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @FunctionalInterface
    private interface FxAction { void run() throws Exception; }

    private static final class MethodAccess {
        static void invoke(Object target, String name) throws Exception {
            var method = target.getClass().getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(target);
        }
    }
}
