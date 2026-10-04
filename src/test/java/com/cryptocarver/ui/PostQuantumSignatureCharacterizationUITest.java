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

/** Characterizes PQC signature workflows and secret visibility through the real shell. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class PostQuantumSignatureCharacterizationUITest {
    private static final String MESSAGE = "invented PQC signature specimen";

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
    void signingVerificationAndRestrictedSurfacesHaveStableTranscript() throws Exception {
        List<String> transcript = new ArrayList<>();
        List<String> violations = new ArrayList<>();
        List<String> signatures = new ArrayList<>();
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

                    var keyAlgorithm = (javafx.scene.control.ComboBox<String>) root.lookup("#pqcAlgorithmCombo");
                    var signAlgorithm = (javafx.scene.control.ComboBox<String>) root.lookup("#pqcSignAlgoCombo");
                    var input = (TextArea) root.lookup("#pqcSignInputArea");
                    var output = (TextArea) root.lookup("#pqcSignOutputArea");
                    var verifyField = (TextField) root.lookup("#pqcVerifySignatureField");
                    var privateArea = (TextArea) root.lookup("#pqcPrivateKeyArea");
                    assertNotNull(keyAlgorithm); assertNotNull(signAlgorithm); assertNotNull(input);
                    assertNotNull(output); assertNotNull(verifyField); assertNotNull(privateArea);
                    keyAlgorithm.setValue("ML-DSA-44");
                    signAlgorithm.setValue("ML-DSA-44");
                    input.setText(MESSAGE);
                    controller.handleGeneratePQCKeyPair();
                    KeyPair pair = new KeyPair(controller.getCurrentPublicKey(), controller.getCurrentPrivateKey());
                    assertNotNull(pair.getPrivate()); assertNotNull(pair.getPublic());
                    String privatePem = pem("PRIVATE KEY", pair.getPrivate().getEncoded());
                    transcript.add("generated algorithm=ML-DSA-44 message=synthetic");

                    for (SecretVisibilityProfile profile : SecretVisibilityProfile.values()) {
                        AppSettings.getInstance().setSecretVisibilityProfile(profile);
                        shell.getHistoryManager().clearHistory();
                        shelf.clear();
                        I18nService.getInstance().setPreference(LanguagePreference.EN);
                        controller.handlePQCSign();
                        OperationResult signed = lastResult(shell);
                        assertNotNull(signed);
                        String signature = HexFormat.of().withUpperCase().formatHex(signed.getOutput());
                        signatures.add(signature);
                        assertEquals(signature, output.getText());
                        inspectSurfaces(profile, signature, privatePem, "sign result", transcript, violations);
                        transcript.add(profile + " sign classification="
                                + ResultPresentationPolicy.classifyPublishedResult(signed));
                        verifyField.setText(signature);
                        controller.handlePQCVerify();
                        OperationResult verified = lastResult(shell);
                        assertEquals("PQC Verify", verified.getOperation());
                        signatures.add(HexFormat.of().withUpperCase().formatHex(verified.getOutput()));
                        inspectSurfaces(profile, signature, privatePem, "valid verification", transcript, violations);
                        transcript.add(profile + " sign+verify=valid local-output=available classification="
                                + ResultPresentationPolicy.classifyPublishedResult(verified));
                    }

                    AppSettings.getInstance().setSecretVisibilityProfile(SecretVisibilityProfile.MASKED);
                    verifyField.setText(alterFirstByte(signatures.get(0)));
                    for (LanguagePreference language : List.of(LanguagePreference.EN, LanguagePreference.ES)) {
                        I18nService.getInstance().setPreference(language);
                        controller.handlePQCVerify();
                        String error = errorText(root);
                        boolean readable = localized(language, "altered signature", error);
                        transcript.add(language + " altered-signature=" + (readable ? "readable" : "unlocalized")
                                + " message=" + concise(error));
                        if (!readable) violations.add(language + " altered signature feedback is not localized/readable: " + concise(error));
                    }

                    signAlgorithm.setValue("ML-DSA-65");
                    for (LanguagePreference language : List.of(LanguagePreference.EN, LanguagePreference.ES)) {
                        I18nService.getInstance().setPreference(language);
                        controller.handlePQCSign();
                        String error = errorText(root);
                        boolean readable = localized(language, "algorithm mismatch", error);
                        transcript.add(language + " incompatible-algorithm=" + (readable ? "readable" : "unlocalized")
                                + " message=" + concise(error));
                        if (!readable) violations.add(language + " incompatible algorithm feedback is not localized/readable: " + concise(error));
                    }

                    for (SecretVisibilityProfile profile : List.of(SecretVisibilityProfile.MASKED, SecretVisibilityProfile.REDACTED)) {
                        AppSettings.getInstance().setSecretVisibilityProfile(profile);
                        String altered = alterFirstByte(signatures.get(0));
                        verifyField.setText(altered);
                        I18nService.getInstance().setPreference(LanguagePreference.EN);
                        controller.handlePQCVerify();
                        inspectSurfaces(profile, altered, privatePem, "altered verification", transcript, violations);
                    }

                    transcript.add("telemetry/logs=" + (containsAny(
                            stdout.toString(StandardCharsets.UTF_8) + stderr.toString(StandardCharsets.UTF_8),
                            privatePem, signatures.toArray(String[]::new)) ? "leaked" : "no key or signature bytes"));
                    String logs = stdout.toString(StandardCharsets.UTF_8) + stderr.toString(StandardCharsets.UTF_8);
                    if (containsAny(logs, privatePem, signatures.toArray(String[]::new))) {
                        violations.add("private key or signature bytes reached stdout/stderr");
                    }
                    Files.createDirectories(Path.of("target"));
                    String joined = String.join("\n", transcript) + "\n";
                    Files.writeString(Path.of("target/pqc-signature-characterization.txt"), joined, StandardCharsets.UTF_8);
                    String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(joined.getBytes(StandardCharsets.UTF_8)));
                    assertEquals(9, violations.size(), String.join("\n", violations) + "\n" + joined);
                    assertEquals("8d714330589f20b66158732e13ce884051777331101d9fdeb055dff892ca0cfe",
                            digest, joined);
                } catch (Throwable error) {
                    throw new AssertionError(error);
                }
            });
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    private void inspectSurfaces(SecretVisibilityProfile profile, String signature, String privatePem,
                                 String operation, List<String> transcript, List<String> violations) throws Exception {
        if (profile == SecretVisibilityProfile.FULL_LAB) {
            String viewer = shell.resolveCurrentOutputText();
            transcript.add(profile + " " + operation + " result=" + (viewer.contains(signature) ? "visible" : "protected"));
            closeExpandedViewer(shell);
            shelf.clear();
            return;
        }
        String viewer = shell.resolveCurrentOutputText();
        String inspector = nodeText(root.lookup("#inspectorPanel"));
        String history = new com.google.gson.Gson().toJson(shell.getHistoryManager().getHistoryItems());
        String status = ((Labeled) root.lookup("#statusLabel")).getText();
        shelf.clear();
        shell.handleAddCurrentOutputToShelf();
        String shelfText = shelf.getEntries().stream().map(ClipboardEntry::getValue)
                .reduce("", (left, right) -> left + "\n" + right);
        shell.handleOpenExpandedResultViewer();
        String expanded = expandedContent(shell);
        boolean sigViewer = contains(signature, viewer);
        boolean sigInspector = contains(signature, inspector);
        boolean sigHistory = contains(signature, history);
        boolean sigShelf = contains(signature, shelfText);
        boolean sigStatus = contains(signature, status);
        boolean sigExpanded = contains(signature, expanded);
        boolean keyLeaked = viewer.contains(privatePem) || inspector.contains(privatePem) || history.contains(privatePem)
                || shelfText.contains(privatePem) || status.contains(privatePem) || expanded.contains(privatePem);
        if (sigViewer || sigInspector || sigHistory || sigShelf || sigStatus || sigExpanded || keyLeaked) {
            violations.add(profile + " " + operation + " exposure: viewer=" + sigViewer + " inspector=" + sigInspector
                    + " history=" + sigHistory + " shelf=" + sigShelf + " status=" + sigStatus
                    + " expanded=" + sigExpanded + " private-key=" + keyLeaked);
        }
        transcript.add(profile + " " + operation + " viewer=" + safe(sigViewer) + " inspector=" + safe(sigInspector)
                + " history=" + safe(sigHistory) + " shelf=" + safe(sigShelf) + " status=" + safe(sigStatus)
                + " expanded=" + safe(sigExpanded));
        closeExpandedViewer(shell);
        shelf.clear();
    }

    private static boolean localized(LanguagePreference language, String kind, String text) {
        String lower = text == null ? "" : text.toLowerCase(java.util.Locale.ROOT);
        if (lower.isBlank()) return false;
        if (kind.equals("altered signature")) {
            return language == LanguagePreference.EN
                    ? lower.contains("invalid") || lower.contains("failed")
                    : (lower.contains("inválid") || lower.contains("fall") || lower.contains("no válida"))
                            && !lower.contains("signature is invalid");
        }
        return (language == LanguagePreference.EN
                ? lower.contains("does not match")
                : lower.contains("no coincide") || lower.contains("no coinciden"))
                && lower.contains("ml-dsa-65") && lower.contains("ml-dsa-44");
    }

    private static boolean contains(String needle, String haystack) {
        return haystack.contains(needle) || haystack.contains(needle.toLowerCase(java.util.Locale.ROOT));
    }
    private static String safe(boolean value) { return value ? "leaked" : "safe"; }
    private static String concise(String text) { return text == null ? "" : text.replace('\n', ' ').replaceAll("\\s+", " ").trim(); }
    private static String alterFirstByte(String hex) {
        int first = Integer.parseInt(hex.substring(0, 2), 16) ^ 1;
        return String.format(java.util.Locale.ROOT, "%02X%s", first, hex.substring(2));
    }
    private static boolean containsAny(String text, String key, String... signatures) {
        if (text.contains(key)) return true;
        for (String signature : signatures) if (signature != null && !signature.isBlank() && contains(signature, text)) return true;
        return false;
    }
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
        Method method = get(shell, "expandedTextViewer").getClass().getDeclaredMethod("hide");
        method.setAccessible(true); method.invoke(get(shell, "expandedTextViewer"));
    }
    private static String nodeText(Node node) {
        if (node == null) return "";
        String own = node instanceof Labeled labeled ? labeled.getText()
                : node instanceof javafx.scene.control.TextInputControl text ? text.getText() : "";
        if (node instanceof Parent parent) for (Node child : parent.getChildrenUnmodifiable()) own += "\n" + nodeText(child);
        return own;
    }
    private static String pem(String type, byte[] encoded) {
        return "-----BEGIN " + type + "-----\n" + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(encoded) + "\n-----END " + type + "-----";
    }
    @SuppressWarnings("unchecked")
    private static <T> T get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return (T) field.get(target);
    }
    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static void onFx(Runnable action) throws Exception {
        if (Platform.isFxApplicationThread()) { action.run(); return; }
        CountDownLatch done = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Throwable> error = new java.util.concurrent.atomic.AtomicReference<>();
        Platform.runLater(() -> { try { action.run(); } catch (Throwable t) { error.set(t); } finally { done.countDown(); } });
        assertTrue(done.await(60, TimeUnit.SECONDS), "JavaFX action timed out");
        if (error.get() != null) throw new AssertionError(error.get());
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
