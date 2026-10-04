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
import javafx.scene.control.TextField;
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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Pins the PQC key lifecycle, import validation, and private-key result surfaces. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class PostQuantumKeyCharacterizationUITest {
    private static final String MESSAGE = "invented PQC key specimen";
    private static final String MALFORMED_PEM =
            "-----BEGIN PUBLIC KEY-----\n%%%\n-----END PUBLIC KEY-----";

    @TempDir Path tempDir;
    private AppSettings previousSettings;
    private LanguagePreference previousLanguage;
    private String previousRoute;
    private String previousTestMode;
    private List<ClipboardEntry> previousShelf;
    private ClipboardShelfManager shelf;
    private ModernMainController shell;
    private PostQuantumController controller;
    private Parent root;
    private Stage stage;
    private HistoryManager isolatedHistory;

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
        System.setProperty("test.mode", "true");
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
            controller = null;
            root = null;
            stage = null;
        });
        if (isolatedHistory != null) isolatedHistory.clearHistory();
        if (shelf != null) {
            shelf.clear();
            for (int index = previousShelf.size() - 1; index >= 0; index--) {
                shelf.addEntry(previousShelf.get(index));
            }
        }
    }

    @Test
    void generationExportImportAndInvalidInputsHaveStableTranscript() throws Exception {
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
                try {
                    var loader = UiTestFxml.loader("/fxml/main-view-modern.fxml");
                    root = loader.load();
                    shell = loader.getController();
                    stage = new Stage();
                    stage.setScene(new Scene(root, 1400, 900));
                    isolatedHistory = new HistoryManager(tempDir.resolve("history.json"));
                    set(shell, "historyManager", isolatedHistory);
                    shell.navigateToModule("Post-Quantum Cryptography");
                    root.applyCss(); root.layout();
                    controller = get(shell, "postQuantumContainerController");
                    assertNotNull(controller);
                    controller.initModule(new DirectShellReporter(shell));

                    var algorithm = (javafx.scene.control.ComboBox<String>) root.lookup("#pqcAlgorithmCombo");
                    var privateArea = (TextArea) root.lookup("#pqcPrivateKeyArea");
                    assertNotNull(algorithm); assertNotNull(privateArea);
                    algorithm.setValue("ML-DSA-44");
                    AppSettings.getInstance().setSecretVisibilityProfile(SecretVisibilityProfile.FULL_LAB);
                    shell.getHistoryManager().clearHistory();
                    controller.handleGeneratePQCKeyPair();
                    KeyPair generated = new KeyPair(controller.getCurrentPublicKey(), controller.getCurrentPrivateKey());
                    assertNotNull(generated.getPublic()); assertNotNull(generated.getPrivate());

                    String publicPem = exportedPem("PUBLIC KEY", generated.getPublic().getEncoded());
                    String privatePem = exportedPem("PRIVATE KEY", generated.getPrivate().getEncoded());
                    Path publicFile = tempDir.resolve("invented-public.pem");
                    Path privateFile = tempDir.resolve("invented-private.pem");
                    Files.writeString(publicFile, publicPem, StandardCharsets.US_ASCII);
                    Files.writeString(privateFile, privatePem, StandardCharsets.US_ASCII);
                    assertEquals(publicPem, Files.readString(publicFile, StandardCharsets.US_ASCII));
                    assertEquals(privatePem, Files.readString(privateFile, StandardCharsets.US_ASCII));
                    transcript.add("generated algorithm=ML-DSA-44 public=present private=held-in-memory");
                    transcript.add("export public/private PEM=valid file-roundtrip=valid");

                    for (SecretVisibilityProfile profile : SecretVisibilityProfile.values()) {
                        AppSettings.getInstance().setSecretVisibilityProfile(profile);
                        shelf.clear();
                        shell.handleAddCurrentOutputToShelf();
                        shell.handleOpenExpandedResultViewer();
                        String viewer = shell.resolveCurrentOutputText();
                        String inspector = nodeText(root.lookup("#inspectorPanel"));
                        String history = new com.google.gson.Gson().toJson(shell.getHistoryManager().getHistoryItems());
                        String shelfText = shelf.getEntries().stream().map(ClipboardEntry::getValue)
                                .reduce("", (left, right) -> left + "\n" + right);
                        String status = ((Labeled) root.lookup("#statusLabel")).getText();
                        String expanded = expandedContent(shell);
                        String resultSnapshot = resultSnapshotText(shell);
                        assertAbsent(privatePem, profile, "expanded viewer", expanded, violations);
                        assertAbsent(privatePem, profile, "result viewer", viewer, violations);
                        assertAbsent(privatePem, profile, "inspector", inspector, violations);
                        assertAbsent(privatePem, profile, "history", history, violations);
                        assertAbsent(privatePem, profile, "Shelf", shelfText, violations);
                        assertAbsent(privatePem, profile, "status bar", status, violations);
                        assertAbsent(privatePem, profile, "published result snapshot", resultSnapshot, violations);
                        transcript.add(profile + " private-key=absent history=checked shelf=checked status=checked expanded=checked");
                        closeExpandedViewer(shell);
                    }

                    // Exercise the same serialization used by the export handler, then import both
                    // generated files through the public file-import API without opening a chooser.
                    controller.importKeysFromFiles(List.of(publicFile.toFile(), privateFile.toFile()));
                    assertArrayEquals(generated.getPublic().getEncoded(), controller.getCurrentPublicKey().getEncoded());
                    assertArrayEquals(generated.getPrivate().getEncoded(), controller.getCurrentPrivateKey().getEncoded());
                    assertEquals("ML-DSA-44", algorithm.getValue());
                    assertFalse(privateArea.getText().contains("[DER"));
                    transcript.add("import public/private=ML-DSA-44 state=updated atomically");

                    KeyPair unrelated = com.cryptocarver.crypto.PostQuantumOperations.generateKeyPair("ML-DSA-44");
                    KeyPair otherAlgorithm = com.cryptocarver.crypto.PostQuantumOperations.generateKeyPair("ML-DSA-65");
                    for (LanguagePreference language : List.of(LanguagePreference.EN, LanguagePreference.ES)) {
                        I18nService.getInstance().setPreference(language);
                        List<String> errorKinds = new ArrayList<>();
                        errorKinds.add(localizedError("malformed PEM", () ->
                                controller.importKeysFromContents(List.of(MALFORMED_PEM)), language));
                        errorKinds.add(localizedError("different algorithm", () ->
                                controller.importKeysFromContents(List.of(
                                        pem("PUBLIC KEY", generated.getPublic().getEncoded()),
                                        pem("PRIVATE KEY", otherAlgorithm.getPrivate().getEncoded()))), language));
                        errorKinds.add(localizedError("nonmatching pair", () ->
                                controller.importKeysFromContents(List.of(
                                        pem("PUBLIC KEY", generated.getPublic().getEncoded()),
                                        pem("PRIVATE KEY", unrelated.getPrivate().getEncoded()))), language));
                        long readableCount = errorKinds.stream().filter("readable"::equals).count();
                        transcript.add(language + " invalid inputs=3 readable messages=" + readableCount);
                        if (readableCount != 3) {
                            violations.add(language + " import errors were not all localized/readable ("
                                    + readableCount + "/3)");
                        }
                    }

                    String logs = stdout.toString(StandardCharsets.UTF_8) + stderr.toString(StandardCharsets.UTF_8);
                    assertAbsent(privatePem, SecretVisibilityProfile.MASKED, "telemetry/logs", logs, violations);
                    assertAbsent(privatePem, SecretVisibilityProfile.REDACTED, "telemetry/logs", logs, violations);
                    transcript.add("telemetry/logs=private-key-absent");
                    Files.createDirectories(Path.of("target"));
                    String joined = String.join("\n", transcript) + "\n";
                    Files.writeString(Path.of("target/pqc-key-characterization.txt"), joined, StandardCharsets.UTF_8);
                    String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(joined.getBytes(StandardCharsets.UTF_8)));
                    assertEquals("7831be3ff9b4b83497f849c73ffe77c918abc8ba0e610e785c1835d7bc9a1a40",
                            digest, joined);
                    assertTrue(violations.isEmpty(), String.join("\n", violations)
                            + "\nTranscript:\n" + joined);
                } catch (Throwable error) {
                    throw new AssertionError(error);
                }
            });
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    private String localizedError(String label, ThrowingAction action, LanguagePreference language) throws Exception {
        String message;
        try {
            action.run();
            fail("Expected " + label + " to be rejected");
            return "unreachable";
        } catch (IllegalArgumentException expected) {
            message = expected.getMessage();
        }
        boolean readable = message != null && !message.isBlank() && !message.contains("-----BEGIN")
                && (language == LanguagePreference.EN ? englishFragment(label, message)
                : spanishFragment(label, message));
        return readable ? "readable" : "unlocalized";
    }

    private static boolean englishFragment(String label, String message) {
        return switch (label) {
            case "malformed PEM" -> message.contains("PEM key") && message.contains("invalid");
            case "different algorithm" -> message.contains("Mismatched keys");
            case "nonmatching pair" -> message.contains("do not form a matching");
            default -> false;
        };
    }

    private static boolean spanishFragment(String label, String message) {
        String lower = message.toLowerCase(java.util.Locale.ROOT);
        return switch (label) {
            case "malformed PEM" -> lower.contains("clave pem")
                    && (lower.contains("no válidos") || lower.contains("inválidos"));
            case "different algorithm" -> lower.contains("claves incompatibles");
            case "nonmatching pair" -> lower.contains("no forman un par");
            default -> false;
        };
    }

    private String exportedPem(String type, byte[] encoded) throws Exception {
        Method method = PostQuantumController.class.getDeclaredMethod("toPem", String.class, byte[].class);
        method.setAccessible(true);
        return (String) method.invoke(controller, type, encoded);
    }

    private static String pem(String type, byte[] encoded) {
        return "-----BEGIN " + type + "-----\n" + Base64.getEncoder().encodeToString(encoded)
                + "\n-----END " + type + "-----";
    }

    private static void assertAbsent(String keyPem, SecretVisibilityProfile profile, String surface,
                                     String content, List<String> violations) {
        String body = keyPem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
        String hex = HexFormat.of().withUpperCase().formatHex(Base64.getMimeDecoder().decode(body));
        if (content.contains(keyPem) || content.contains(body) || content.contains(hex)) {
            violations.add(profile + " private key leaked in " + surface);
        }
    }

    private static OperationResult lastResult(ModernMainController shell) throws Exception {
        return get(shell, "lastPublishedResultSnapshot");
    }

    private static String resultSnapshotText(ModernMainController shell) throws Exception {
        OperationResult result = lastResult(shell);
        if (result == null || result.getOutput() == null) return "";
        return HexFormat.of().withUpperCase().formatHex(result.getOutput());
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
        StringBuilder result = new StringBuilder();
        if (node instanceof Labeled labeled) result.append(labeled.getText()).append('\n');
        if (node instanceof TextArea area) result.append(area.getText()).append('\n');
        if (node instanceof TextField field) result.append(field.getText()).append('\n');
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) result.append(nodeText(child));
        }
        return result.toString();
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @SuppressWarnings("unchecked")
    private static <T> T get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(target);
    }

    private static void onFx(Runnable action) throws Exception {
        if (Platform.isFxApplicationThread()) { action.run(); return; }
        CountDownLatch done = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Throwable> error = new java.util.concurrent.atomic.AtomicReference<>();
        Platform.runLater(() -> {
            try { action.run(); }
            catch (Throwable t) { error.set(t); }
            finally { done.countDown(); }
        });
        assertTrue(done.await(60, TimeUnit.SECONDS), "JavaFX action timed out");
        if (error.get() != null) throw new AssertionError(error.get());
    }

    @FunctionalInterface
    private interface ThrowingAction { void run() throws Exception; }

    private static final class DirectShellReporter implements StatusReporter {
        private final ModernMainController shell;
        private DirectShellReporter(ModernMainController shell) { this.shell = shell; }
        @Override public void updateStatus(String message) { shell.updateStatus(message); }
        @Override public void updateInspector(String operation, byte[] input, byte[] output,
                                              List<OperationDetail> details) {
            shell.updateInspector(operation, input, output, details);
        }
        @Override public void showError(String title, String message) { shell.showError(title, message); }
        @Override public void showInfo(String title, String message) { shell.showInfo(title, message); }
        @Override public void addToHistory(String operation, List<OperationDetail> details) {
            shell.addToHistory(operation, details);
        }
        @Override public OperationExecutor getOperationExecutor() { return null; }
        @Override public void publish(OperationResult result) { shell.publish(result); }
    }

    private static final class MethodAccess {
        private static Object invoke(Object target, String name) throws Exception {
            Method method = target.getClass().getDeclaredMethod(name);
            method.setAccessible(true);
            return method.invoke(target);
        }
    }
}
