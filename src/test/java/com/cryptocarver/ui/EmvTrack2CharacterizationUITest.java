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

/** Pins Track 2 UI behavior and restricted-profile visibility of account data. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class EmvTrack2CharacterizationUITest {
    private static final String PAN = "4761739001010119";
    private static final String EXPIRY = "2912";
    private static final String SERVICE_CODE = "201";
    private static final String DISCRETIONARY = "123456";

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
    void completeCycleAndInvalidTrack2HaveStableVisibilityTranscript() throws Exception {
        List<String> transcript = new ArrayList<>();
        List<String> violations = new ArrayList<>();
        List<String> localizationFailures = new ArrayList<>();
        List<String> observedSecrets = new ArrayList<>();
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
                TextArea resultArea = (TextArea) get(controller, "track2ResultArea");
                TextField pan = (TextField) get(controller, "panTrack2Field");
                TextField expiry = (TextField) get(controller, "expiryTrack2Field");
                TextField service = (TextField) get(controller, "serviceCodeFieldTrack2");
                TextField discretionary = (TextField) get(controller, "discretionaryDataField");
                TextField trackInput = (TextField) get(controller, "track2InputField");

                for (LanguagePreference language : List.of(LanguagePreference.EN, LanguagePreference.ES)) {
                    I18nService.getInstance().setPreference(language);
                    pan.clear(); expiry.clear(); service.clear(); discretionary.clear();
                    resultArea.clear();
                    controller.handleEncodeTrack2();
                    String required = concise(resultArea.getText());
                    String expectedRequired = I18nService.getInstance().text("module.emv.feedback.trackRequired");
                    boolean requiredLocalized = required.equals(expectedRequired);
                    if (!requiredLocalized) localizationFailures.add(language + " encode-required is not localized");
                    transcript.add(language + " encode-required localized=" + requiredLocalized);

                    trackInput.setText("NOT A TRACK 2 VALUE");
                    resultArea.clear();
                    controller.handleDecodeTrack2();
                    String invalid = concise(resultArea.getText());
                    boolean readable = !invalid.isBlank() && (invalid.startsWith("Error:") || invalid.startsWith("Error de"));
                    String expectedInvalid = I18nService.getInstance().text("module.emv.track2.invalid");
                    boolean invalidLocalized = invalid.equals(expectedInvalid);
                    if (!readable) violations.add(language + " malformed track data has no readable message");
                    if (!invalidLocalized) localizationFailures.add(language + " decode-invalid is not localized");
                    transcript.add(language + " decode-invalid readable=" + readable
                            + " localized=" + invalidLocalized + " diagnostic=<validation-message>");
                }

                for (SecretVisibilityProfile profile : List.of(SecretVisibilityProfile.FULL_LAB,
                        SecretVisibilityProfile.MASKED, SecretVisibilityProfile.REDACTED)) {
                    AppSettings.getInstance().setSecretVisibilityProfile(profile);
                    I18nService.getInstance().setPreference(LanguagePreference.EN);
                    pan.setText(PAN); expiry.setText(EXPIRY); service.setText(SERVICE_CODE);
                    discretionary.setText(DISCRETIONARY);
                    controller.handleEncodeTrack2();
                    String encoded = new String(lastResult(shell).getOutput(), StandardCharsets.US_ASCII);
                    assertFalse(encoded.isBlank(), "Track 2 encoder must publish encoded data");
                    assertTrue(resultArea.getText().contains(encoded), "The local encoder report must contain Track 2 data");
                    observedSecrets.addAll(List.of(PAN, encoded, DISCRETIONARY));
                    transcript.add(profile + " encode classification="
                            + ResultPresentationPolicy.classifyPublishedResult(lastResult(shell)));
                    inspectSurfaces(profile, "encode", List.of(PAN, encoded, DISCRETIONARY), shell, root,
                            transcript, violations);
                    shelf.clear(); shell.getHistoryManager().clearHistory();

                    trackInput.setText(encoded);
                    controller.handleDecodeTrack2();
                    OperationResult decodedResult = lastResult(shell);
                    String decoded = new String(decodedResult.getOutput(), StandardCharsets.UTF_8);
                    assertTrue(decoded.contains(PAN), "Track 2 decoder must return the invented PAN");
                    assertTrue(resultArea.getText().contains(PAN), "The local decoder report must contain the invented PAN");
                    transcript.add(profile + " decode classification="
                            + ResultPresentationPolicy.classifyPublishedResult(decodedResult));
                    inspectSurfaces(profile, "decode", List.of(PAN, encoded, DISCRETIONARY), shell, root,
                            transcript, violations);
                    shelf.clear(); shell.getHistoryManager().clearHistory();
                }
                I18nService.getInstance().setPreference(LanguagePreference.EN);
            });
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }

        String logs = stdout.toString(StandardCharsets.UTF_8) + stderr.toString(StandardCharsets.UTF_8);
        if (observedSecrets.stream().anyMatch(logs::contains)) violations.add("telemetry/logs leaked invented Track 2 data");
        transcript.add("telemetry/logs=" + (violations.stream().anyMatch(v -> v.startsWith("telemetry/")) ? "leaked" : "safe"));
        violations.addAll(localizationFailures);
        String joined = String.join("\n", transcript);
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/emv-track2-transcript.txt"), joined);
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(joined.getBytes(StandardCharsets.UTF_8)));
        assertEquals("6f876c3e2473de4d727e719ab04d4f41aa92bc493e4b79759b187d517e7cf043", digest, "Transcript:\n" + joined);
        assertTrue(violations.isEmpty(), String.join("\n", violations) + "\nTranscript:\n" + joined);
    }

    private void inspectSurfaces(SecretVisibilityProfile profile, String operation, List<String> secrets,
                                 ModernMainController shell, Parent root, List<String> transcript,
                                 List<String> violations) throws Exception {
        String viewer = shell.resolveCurrentOutputText();
        String inspector = nodeText(root.lookup("#inspectorPanel"));
        String history = new com.google.gson.Gson().toJson(shell.getHistoryManager().getHistoryItems());
        Labeled statusLabel = (Labeled) root.lookup("#statusLabel");
        assertNotNull(statusLabel, "The real status surface must be present");
        shell.handleAddCurrentOutputToShelf();
        String shelfContent = shelf.getEntries().stream().map(ClipboardEntry::getValue)
                .reduce("", (left, right) -> left + "\n" + right);
        closeExpandedViewer(shell);
        if (viewer != null && !viewer.isBlank()) shell.handleOpenExpandedResultViewer();
        String expanded = expandedContent(shell);
        boolean fullLab = profile == SecretVisibilityProfile.FULL_LAB;
        String[] surfaces = {viewer, inspector, history, shelfContent, statusLabel.getText(), expanded};
        String[] names = {"result", "inspector", "history", "shelf", "status", "expanded"};
        List<String> states = new ArrayList<>();
        for (int i = 0; i < surfaces.length; i++) {
            String content = surfaces[i];
            boolean containsSecret = secrets.stream().anyMatch(secret -> content != null && content.contains(secret));
            if (fullLab && i == 0 && !containsSecret) {
                violations.add("FULL_LAB " + operation + " should show invented Track 2 data in result viewer");
            } else if (fullLab && i == 3 && !shelf.getEntries().isEmpty() && !containsSecret) {
                violations.add("FULL_LAB " + operation + " should retain the captured result on Shelf");
            } else if (!fullLab && containsSecret) {
                violations.add(profile + " " + operation + " leaked invented Track 2 data in " + names[i]);
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
