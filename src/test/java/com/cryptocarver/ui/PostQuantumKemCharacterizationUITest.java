package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.ClipboardEntry;
import com.cryptocarver.model.ClipboardShelfManager;
import com.cryptocarver.model.HistoryManager;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.model.OperationDetail;
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
import javafx.scene.control.ProgressIndicator;
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

/** Characterizes the PQC KEM cycle, benchmark controls, and shared-secret surfaces. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class PostQuantumKemCharacterizationUITest {
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
            AppSettings.getInstance().setSecretVisibilityProfile(previousSettings.getSecretVisibilityProfile());
            if (previousTestMode == null) System.clearProperty("test.mode");
            else System.setProperty("test.mode", previousTestMode);
            I18nService.getInstance().setPreference(previousLanguage);
            previousSettings.setLastRoute(previousRoute);
            AppSettings.setInstanceForTesting(previousSettings);
            I18nService.getInstance().refreshFromSettings();
            shell = null; controller = null; root = null; stage = null;
        });
        if (isolatedHistory != null) isolatedHistory.clearHistory();
        if (shelf != null) {
            shelf.clear();
            for (int index = previousShelf.size() - 1; index >= 0; index--) shelf.addEntry(previousShelf.get(index));
        }
    }

    @Test
    void encapsulateDecapsulateAndBenchmarkHaveStableTranscript() throws Exception {
        List<String> transcript = new ArrayList<>();
        List<String> violations = new ArrayList<>();
        List<String> sharedSecrets = new ArrayList<>();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        try (PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
            System.setOut(out); System.setErr(err);
            onFx(() -> {
                try {
                    var loader = UiTestFxml.loader("/fxml/main-view-modern.fxml");
                    root = loader.load(); shell = loader.getController(); stage = new Stage();
                    stage.setScene(new Scene(root, 1400, 900));
                    isolatedHistory = new HistoryManager(tempDir.resolve("history.json"));
                    set(shell, "historyManager", isolatedHistory);
                    shell.navigateToModule("Post-Quantum Cryptography"); root.applyCss(); root.layout();
                    controller = get(shell, "postQuantumContainerController"); assertNotNull(controller);
                    controller.initModule(new DirectShellReporter(shell));

                    ComboBox<String> keyAlgorithm = (ComboBox<String>) root.lookup("#pqcAlgorithmCombo");
                    ComboBox<String> kemAlgorithm = (ComboBox<String>) root.lookup("#pqcKemAlgoCombo");
                    ComboBox<String> benchmarkAlgorithm = (ComboBox<String>) root.lookup("#pqcBenchmarkAlgoCombo");
                    TextArea ciphertextArea = (TextArea) root.lookup("#pqcKemCiphertextArea");
                    TextField bobSecret = (TextField) root.lookup("#pqcKemSharedSecretField");
                    TextField aliceSecret = (TextField) root.lookup("#pqcAliceSecretField");
                    TextArea privateArea = (TextArea) root.lookup("#pqcPrivateKeyArea");
                    TextArea benchmarkArea = (TextArea) root.lookup("#pqcBenchmarkArea");
                    ProgressIndicator progress = (ProgressIndicator) root.lookup("#pqcBenchmarkProgress");
                    assertNotNull(keyAlgorithm); assertNotNull(kemAlgorithm); assertNotNull(benchmarkAlgorithm);
                    assertNotNull(ciphertextArea); assertNotNull(bobSecret); assertNotNull(aliceSecret);
                    assertNotNull(privateArea); assertNotNull(benchmarkArea); assertNotNull(progress);
                    keyAlgorithm.setValue("ML-KEM-512"); kemAlgorithm.setValue("ML-KEM-512");
                    controller.handleGeneratePQCKeyPair();
                    KeyPair pair = new KeyPair(controller.getCurrentPublicKey(), controller.getCurrentPrivateKey());
                    String privatePem = pem("PRIVATE KEY", pair.getPrivate().getEncoded());
                    transcript.add("generated algorithm=ML-KEM-512 keypair=present");

                    for (SecretVisibilityProfile profile : SecretVisibilityProfile.values()) {
                        AppSettings.getInstance().setSecretVisibilityProfile(profile);
                        shell.getHistoryManager().clearHistory(); shelf.clear();
                        I18nService.getInstance().setPreference(LanguagePreference.EN);
                        controller.handlePQCEncapsulate();
                        OperationResult encapsulated = lastResult(shell);
                        assertEquals("ML-KEM Encapsulate", encapsulated.getOperation());
                        String ciphertext = ciphertextArea.getText().trim();
                        String bobHex = bobSecret.getText();
                        assertFalse(ciphertext.isBlank()); assertFalse(bobHex.isBlank());
                        sharedSecrets.add(bobHex);
                        inspectSecretSurfaces(profile, bobHex, privatePem, "encapsulation", transcript, violations);
                        transcript.add(profile + " encapsulation=ciphertext-ready secret-local=present classification="
                                + ResultPresentationPolicy.classifyPublishedResult(encapsulated));

                        ciphertextArea.setText(ciphertext);
                        controller.handlePQCDecapsulate();
                        OperationResult decapsulated = lastResult(shell);
                        assertEquals("ML-KEM Decapsulate", decapsulated.getOperation());
                        assertEquals(com.cryptocarver.model.OperationDetail.Classification.SECRET,
                                decapsulated.getOutputClassification());
                        assertEquals(bobHex, aliceSecret.getText());
                        assertTrue(MessageDigest.isEqual(hex(bobHex), hex(aliceSecret.getText())));
                        sharedSecrets.add(aliceSecret.getText());
                        inspectSecretSurfaces(profile, bobHex, privatePem, "decapsulation", transcript, violations);
                        transcript.add(profile + " decapsulation=matches secret-local=present classification="
                                + ResultPresentationPolicy.classifyPublishedResult(decapsulated));
                    }

                    kemAlgorithm.setValue("ML-KEM-768");
                    for (LanguagePreference language : List.of(LanguagePreference.EN, LanguagePreference.ES)) {
                        I18nService.getInstance().setPreference(language);
                        controller.handlePQCEncapsulate();
                        String error = errorText(root);
                        boolean readable = readableKEMMismatch(language, error);
                        transcript.add(language + " incompatible-KEM-algorithm=" + (readable ? "readable" : "unlocalized")
                                + " message=" + concise(error));
                        if (!readable) violations.add(language + " incompatible KEM algorithm feedback is not localized/readable");
                    }

                    kemAlgorithm.setValue("ML-KEM-512");
                    ciphertextArea.setText("GG");
                    for (LanguagePreference language : List.of(LanguagePreference.EN, LanguagePreference.ES)) {
                        I18nService.getInstance().setPreference(language);
                        controller.handlePQCDecapsulate();
                        String error = errorText(root);
                        boolean readable = readableMalformedCiphertext(language, error);
                        transcript.add(language + " malformed-ciphertext=" + (readable ? "readable" : "unlocalized"));
                        if (!readable) violations.add(language + " malformed ciphertext feedback is blank/unreadable");
                    }

                    benchmarkAlgorithm.setValue("ML-KEM-512");
                    controller.handlePQCBenchmark();
                    String benchmark = benchmarkArea.getText();
                    boolean benchmarkCompleted = benchmark != null
                            && benchmark.contains("=== PQC Benchmark: ML-KEM-512 ===")
                            && benchmark.contains("Iterations: 1000")
                            && benchmark.contains("Encapsulation:") && benchmark.contains("Decapsulation:")
                            && !progress.isVisible();
                    transcript.add("benchmark algorithm=ML-KEM-512 iterations=1000 result="
                            + (benchmarkCompleted ? "complete" : "blank-or-incomplete") + " timings=ignored");
                    if (!benchmarkCompleted) violations.add("benchmark produced a blank or incomplete result");

                    String logs = stdout.toString(StandardCharsets.UTF_8) + stderr.toString(StandardCharsets.UTF_8);
                    boolean secretLogged = sharedSecrets.stream().anyMatch(secret -> contains(secret, logs));
                    boolean keyLogged = logs.contains(privatePem);
                    transcript.add("telemetry/logs=" + (secretLogged || keyLogged ? "leaked" : "no private-key or shared-secret bytes"));
                    if (secretLogged || keyLogged) violations.add("private key or shared-secret bytes reached stdout/stderr");

                    Files.createDirectories(Path.of("target"));
                    String joined = String.join("\n", transcript) + "\n";
                    Files.writeString(Path.of("target/pqc-kem-characterization.txt"), joined, StandardCharsets.UTF_8);
                    String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(joined.getBytes(StandardCharsets.UTF_8)));
                    assertEquals(2, violations.size(), String.join("\n", violations) + "\n" + joined);
                    assertEquals("cbc6ecb8aa880d65b899199f0b853489812ddbcf3b37606bdf3c5ff8c6686368",
                            digest, joined);
                } catch (Throwable error) { throw new AssertionError(error); }
            });
        } finally {
            System.setOut(originalOut); System.setErr(originalErr);
        }
    }

    private void inspectSecretSurfaces(SecretVisibilityProfile profile, String secretHex, String privatePem,
                                       String operation, List<String> transcript, List<String> violations) throws Exception {
        if (profile == SecretVisibilityProfile.FULL_LAB) {
            transcript.add(profile + " " + operation + " result=visible-by-policy");
            closeExpandedViewer(shell); shelf.clear(); return;
        }
        String viewer = shell.resolveCurrentOutputText();
        String inspector = nodeText(root.lookup("#inspectorPanel"));
        String history = new com.google.gson.Gson().toJson(shell.getHistoryManager().getHistoryItems());
        String status = ((Labeled) root.lookup("#statusLabel")).getText();
        shelf.clear(); shell.handleAddCurrentOutputToShelf();
        String shelfText = shelf.getEntries().stream().map(ClipboardEntry::getValue).reduce("", (a, b) -> a + "\n" + b);
        shell.handleOpenExpandedResultViewer();
        String expanded = expandedContent(shell);
        boolean[] leaked = {contains(secretHex, viewer), contains(secretHex, inspector), contains(secretHex, history),
                contains(secretHex, shelfText), contains(secretHex, status), contains(secretHex, expanded)};
        boolean keyLeak = viewer.contains(privatePem) || inspector.contains(privatePem) || history.contains(privatePem)
                || shelfText.contains(privatePem) || status.contains(privatePem) || expanded.contains(privatePem);
        if (java.util.Arrays.stream(toInt(leaked)).anyMatch(value -> value != 0) || keyLeak) {
            violations.add(profile + " " + operation + " shared-secret/private-key exposure: viewer=" + leaked[0]
                    + " inspector=" + leaked[1] + " history=" + leaked[2] + " shelf=" + leaked[3]
                    + " status=" + leaked[4] + " expanded=" + leaked[5] + " private-key=" + keyLeak);
        }
        transcript.add(profile + " " + operation + " viewer=" + safe(leaked[0]) + " inspector=" + safe(leaked[1])
                + " history=" + safe(leaked[2]) + " shelf=" + safe(leaked[3]) + " status=" + safe(leaked[4])
                + " expanded=" + safe(leaked[5]));
        closeExpandedViewer(shell); shelf.clear();
    }

    private static int[] toInt(boolean[] values) {
        int[] result = new int[values.length]; for (int index = 0; index < values.length; index++) result[index] = values[index] ? 1 : 0;
        return result;
    }
    private static boolean readableKEMMismatch(LanguagePreference language, String error) {
        String lower = error == null ? "" : error.toLowerCase(java.util.Locale.ROOT);
        int separator = lower.indexOf('|');
        String message = separator >= 0 ? lower.substring(separator + 1) : lower;
        return !message.isBlank() && (language == LanguagePreference.EN
                ? message.contains("generate a key pair")
                : message.contains("genera un par") || message.contains("generar un par")
                        || message.contains("algoritmo seleccionado"));
    }
    private static boolean readableMalformedCiphertext(LanguagePreference language, String error) {
        String lower = error == null ? "" : error.toLowerCase(java.util.Locale.ROOT);
        return !lower.isBlank() && !lower.contains("exception") && (language == LanguagePreference.EN
                ? lower.contains("hexadecimal") || lower.contains("hex")
                : lower.contains("hexadecimal") || lower.contains("hex"));
    }
    private static String safe(boolean value) { return value ? "leaked" : "safe"; }
    private static String concise(String text) { return text == null ? "" : text.replace('\n', ' ').replaceAll("\\s+", " ").trim(); }
    private static boolean contains(String needle, String haystack) {
        return haystack.toLowerCase(java.util.Locale.ROOT).contains(needle.toLowerCase(java.util.Locale.ROOT));
    }
    private static byte[] hex(String value) { return HexFormat.of().parseHex(value); }
    private static OperationResult lastResult(ModernMainController shell) throws Exception { return get(shell, "lastPublishedResultSnapshot"); }
    private static String errorText(Parent root) {
        Node title = root.lookup("#errorBannerTitle"); Node remedy = root.lookup("#errorBannerRemedy");
        return (title instanceof Labeled labeled ? labeled.getText() : "") + " | "
                + (remedy instanceof Labeled labeled ? labeled.getText() : "");
    }
    private static String expandedContent(ModernMainController shell) throws Exception {
        Object viewer = get(shell, "expandedTextViewer"); Object area = get(viewer, "contentArea");
        return area instanceof TextArea textArea ? textArea.getText() : "";
    }
    private static void closeExpandedViewer(ModernMainController shell) throws Exception {
        Object viewer = get(shell, "expandedTextViewer"); Method hide = viewer.getClass().getDeclaredMethod("hide");
        hide.setAccessible(true); hide.invoke(viewer);
    }
    private static String nodeText(Node node) {
        if (node == null) return "";
        String own = node instanceof Labeled labeled ? labeled.getText()
                : node instanceof javafx.scene.control.TextInputControl text ? text.getText() : "";
        if (node instanceof Parent parent) for (Node child : parent.getChildrenUnmodifiable()) own += "\n" + nodeText(child);
        return own;
    }
    private static String pem(String type, byte[] bytes) {
        return "-----BEGIN " + type + "-----\n" + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(bytes) + "\n-----END " + type + "-----";
    }
    @SuppressWarnings("unchecked") private static <T> T get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return (T) field.get(target);
    }
    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static void onFx(Runnable action) throws Exception {
        if (Platform.isFxApplicationThread()) { action.run(); return; }
        CountDownLatch done = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
        Platform.runLater(() -> { try { action.run(); } catch (Throwable error) { failure.set(error); } finally { done.countDown(); } });
        assertTrue(done.await(120, TimeUnit.SECONDS), "JavaFX action timed out");
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private static final class DirectShellReporter implements StatusReporter {
        private final ModernMainController shell;
        private DirectShellReporter(ModernMainController shell) { this.shell = shell; }
        @Override public void updateStatus(String message) { shell.updateStatus(message); }
        @Override public void updateInspector(String operation, byte[] input, byte[] output, List<OperationDetail> details) {
            shell.updateInspector(operation, input, output, details);
        }
        @Override public void showError(String title, String message) { shell.showError(title, message); }
        @Override public void showInfo(String title, String message) { shell.showInfo(title, message); }
        @Override public void addToHistory(String operation, List<OperationDetail> details) { shell.addToHistory(operation, details); }
        @Override public OperationExecutor getOperationExecutor() { return null; }
        @Override public void publish(OperationResult result) { shell.publish(result); }
    }
}
