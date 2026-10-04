package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.ClipboardEntry;
import com.cryptocarver.model.ClipboardShelfManager;
import com.cryptocarver.model.HistoryManager;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextInputControl;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Pins Authentication's production-shell signature flows and restricted result capture. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class AuthenticationSignatureCharacterizationUITest {
    private static final String MESSAGE = "invented signature specimen 64";
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
    void signatureFlowsAndVisibilityHaveStableTranscript() throws Exception {
        List<String> transcript = new ArrayList<>();
        List<String> violations = new ArrayList<>();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        AtomicReference<KeyPair> keyPair = new AtomicReference<>();
        AtomicReference<String> privatePem = new AtomicReference<>();
        AtomicReference<ModernMainController> shellRef = new AtomicReference<>();
        AtomicReference<AuthenticationController> controllerRef = new AtomicReference<>();
        AtomicReference<Parent> rootRef = new AtomicReference<>();
        AtomicReference<Stage> stageRef = new AtomicReference<>();
        List<String> signatures = new ArrayList<>();
        try (PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
            System.setOut(out);
            System.setErr(err);
            System.setProperty("test.mode", "true");
            onFx(() -> {
                try {
                    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                    generator.initialize(1024);
                    keyPair.set(generator.generateKeyPair());
                    privatePem.set(pem("PRIVATE KEY", keyPair.get().getPrivate().getEncoded()));

                    var loader = UiTestFxml.loader("/fxml/main-view-modern.fxml");
                    Parent root = loader.load();
                    ModernMainController shell = loader.getController();
                    Stage stage = new Stage();
                    stage.setScene(new Scene(root, 1400, 900));
                    // Replace any host history resolved during FXML setup with this test's file.
                    set(shell, "historyManager", new HistoryManager(tempDir.resolve("history.json")));
                    shell.navigateToModule("Digital Signatures");
                    root.applyCss(); root.layout();
                    AuthenticationController controller = (AuthenticationController) get(shell, "authenticationContainerController");
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
                var privateArea = (TextArea) root.lookup("#signaturePrivateKeyArea");
                var publicArea = (TextArea) root.lookup("#signaturePublicKeyArea");
                var input = (TextArea) root.lookup("#authInputArea");
                var verifyField = (javafx.scene.control.TextField) root.lookup("#signatureVerifyField");
                var output = (TextArea) root.lookup("#authOutputArea");
                privateArea.clear(); publicArea.clear(); input.setText(MESSAGE);

                for (LanguagePreference language : List.of(LanguagePreference.EN, LanguagePreference.ES)) {
                    I18nService.getInstance().setPreference(language);
                    controller.handleSign();
                    String error = errorText(root);
                    assertFalse(error.isBlank(), "Missing signing-key validation must remain readable in " + language);
                    assertFalse(error.contains(MESSAGE));
                    transcript.add(language + " missing-signing-key=" + concise(error));
                    controller.handleVerify();
                    error = errorText(root);
                    assertFalse(error.isBlank(), "Missing public-key validation must remain readable in " + language);
                    transcript.add(language + " missing-public-key=" + concise(error));
                }

                I18nService.getInstance().setPreference(LanguagePreference.EN);
                privateArea.setText(privatePem.get());
                publicArea.setText(pem("PUBLIC KEY", keyPair.get().getPublic().getEncoded()));
                for (SecretVisibilityProfile profile : SecretVisibilityProfile.values()) {
                    AppSettings.getInstance().setSecretVisibilityProfile(profile);
                    shell.getHistoryManager().clearHistory();
                    input.setText(MESSAGE);
                    controller.handleSign();
                    OperationResult signed = lastResult(shell);
                    assertNotNull(signed, "Signing publishes one result");
                    String signatureHex = HexFormat.of().withUpperCase().formatHex(signed.getOutput());
                    assertFalse(signatureHex.isBlank());
                    signatures.add(signatureHex);

                    verifyField.setText(signatureHex);
                    controller.handleVerify();
                    OperationResult verified = lastResult(shell);
                    assertEquals("Signature Verified", verified.getOperation());
                    assertTrue(verified.getDetails().stream().anyMatch(detail ->
                            "Result".equals(detail.name()) && "VALID".equals(detail.value())));
                    inspectRestrictedSurfaces(profile, signatureHex, privatePem.get(), shell, root,
                            transcript, violations);

                    String altered = alterFirstByte(signatureHex);
                    signatures.add(altered);
                    verifyField.setText(altered);
                    I18nService.getInstance().setPreference(LanguagePreference.EN);
                    controller.handleVerify();
                    String englishError = errorText(root);
                    assertFalse(englishError.isBlank(), "Altered signature must show a readable EN message");
                    assertTrue(englishError.toLowerCase(java.util.Locale.ROOT).contains("invalid")
                                    || englishError.toLowerCase(java.util.Locale.ROOT).contains("failed"), englishError);
                    transcript.add(profile + " altered-signature EN=" + concise(englishError));
                    inspectRestrictedSurfaces(profile, altered, privatePem.get(), shell, root,
                            transcript, violations);

                    I18nService.getInstance().setPreference(LanguagePreference.ES);
                    verifyField.setText(altered);
                    controller.handleVerify();
                    String spanishError = errorText(root);
                    assertFalse(spanishError.isBlank(), "Altered signature must show a readable ES message");
                    assertTrue(spanishError.toLowerCase(java.util.Locale.ROOT).contains("invalid")
                                    || spanishError.toLowerCase(java.util.Locale.ROOT).contains("failed")
                                    || spanishError.toLowerCase(java.util.Locale.ROOT).contains("inválid"), spanishError);
                    transcript.add(profile + " altered-signature ES=" + concise(spanishError));
                    inspectRestrictedSurfaces(profile, altered, privatePem.get(), shell, root,
                            transcript, violations);
                    I18nService.getInstance().setPreference(LanguagePreference.EN);
                }
            });
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }

        String logs = stdout.toString(StandardCharsets.UTF_8) + stderr.toString(StandardCharsets.UTF_8);
        assertFalse(logs.contains(privatePem.get()), "Private test key reached application logs");
        for (String signature : signatures) {
            assertFalse(logs.contains(signature), "Signature bytes reached application logs");
        }
        for (String row : transcript) assertFalse(row.contains(privatePem.get()), row);
        transcript.add("telemetry/logs=no private key or signature bytes");
        Files.createDirectories(Path.of("target"));
        String joined = String.join("\n", transcript);
        Files.writeString(Path.of("target/authentication-signature-transcript.txt"), joined + "\n");
        assertTrue(violations.isEmpty(), String.join("\n", violations) + "\nTranscript:\n" + joined);
        assertEquals("3cc974344c23208d6ed8ac63a3d0f5576f4d6f571e951a6d55a1dd6157291cc4", digest(joined), joined);
        onFx(() -> {
            ModernMainController shell = shellRef.get();
            if (shell != null) shell.shutdown();
            if (stageRef.get() != null) { stageRef.get().close(); stageRef.get().setScene(null); }
        });
    }

    private void inspectRestrictedSurfaces(SecretVisibilityProfile profile, String signature,
                                           String privateKey, ModernMainController shell, Parent root,
                                           List<String> transcript, List<String> violations) throws Exception {
        if (profile == SecretVisibilityProfile.FULL_LAB) {
            assertTrue(shell.resolveCurrentOutputText().contains(signature),
                    "FULL_LAB keeps the signature available in the result viewer");
            transcript.add(profile + " signature capture=visible by policy");
            closeExpandedViewer(shell);
            shelf.clear();
            return;
        }
        String viewer = shell.resolveCurrentOutputText();
        boolean resultLeaked = viewer.contains(signature);
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
        boolean signatureLeaked = combined.contains(signature);
        boolean privateKeyLeaked = combined.contains(privateKey);
        if (signatureLeaked || privateKeyLeaked) violations.add(profile + " signature/private-key exposure: result=" + resultLeaked
                + " inspector-signature=" + inspector.contains(signature) + " status-signature=" + status.contains(signature)
                + " history-signature=" + history.contains(signature) + " shelf-signature=" + shelfContent.contains(signature)
                + " expanded-signature=" + expanded.contains(signature) + " private-key=" + privateKeyLeaked);
        transcript.add(profile + " result=" + (resultLeaked ? "visible" : "protected")
                + " inspector=" + (inspector.contains(signature) ? "leaked" : "safe")
                + " history=" + (history.contains(signature) ? "leaked" : "safe")
                + " shelf=" + (shelfContent.contains(signature) ? "leaked" : "blocked")
                + " status=" + (status.contains(signature) ? "leaked" : "safe")
                + " expanded=" + (expanded.contains(signature) ? "leaked" : "protected"));
        closeExpandedViewer(shell);
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

    private static boolean containsAny(String content, String... needles) {
        for (String needle : needles) if (needle != null && !needle.isBlank() && content.contains(needle)) return true;
        return false;
    }

    private static String concise(String text) { return text.replace('\n', ' ').replaceAll("\\s+", " ").trim(); }

    private static String alterFirstByte(String hex) {
        int first = Integer.parseInt(hex.substring(0, 2), 16) ^ 1;
        return String.format(java.util.Locale.ROOT, "%02X%s", first, hex.substring(2));
    }

    private static String pem(String type, byte[] der) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
    }

    private static String digest(String transcript) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(transcript.getBytes(StandardCharsets.UTF_8)));
    }

    private static Object get(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
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
