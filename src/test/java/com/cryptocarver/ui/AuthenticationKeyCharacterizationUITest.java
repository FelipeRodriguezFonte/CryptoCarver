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
import javafx.scene.control.ComboBox;
import javafx.scene.control.Labeled;
import javafx.scene.control.MenuButton;
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

/** Pins Authentication's key ingestion, cache transitions, and restricted result surfaces. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class AuthenticationKeyCharacterizationUITest {
    private static final String MESSAGE = "invented Authentication key specimen 65";
    private static final String INVALID_PRIVATE_PEM =
            "-----BEGIN PRIVATE KEY-----\nAQID\n-----END PRIVATE KEY-----\n";
    private static final String INVALID_PUBLIC_PEM =
            "-----BEGIN PUBLIC KEY-----\nAQID\n-----END PUBLIC KEY-----\n";
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
    void keySelectionCacheAndVisibilityHaveStableTranscript() throws Exception {
        List<String> transcript = new ArrayList<>();
        List<String> violations = new ArrayList<>();
        List<String> signatures = new ArrayList<>();
        AtomicReference<String> privatePem = new AtomicReference<>();
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
                ComboBox<String> algorithm = (ComboBox<String>) root.lookup("#signatureAlgorithmCombo");
                TextArea privateArea = (TextArea) root.lookup("#signaturePrivateKeyArea");
                TextArea publicArea = (TextArea) root.lookup("#signaturePublicKeyArea");
                TextArea input = (TextArea) root.lookup("#authInputArea");
                TextField verifyField = (TextField) root.lookup("#signatureVerifyField");
                Labeled keyStatus = (Labeled) root.lookup("#signatureKeyStatusLabel");
                MenuButton privateShelf = (MenuButton) root.lookup("#sigPrivKeyShelfMenu");
                MenuButton publicShelf = (MenuButton) root.lookup("#sigPubKeyShelfMenu");
                assertNotNull(algorithm); assertNotNull(privateArea); assertNotNull(publicArea);
                assertNotNull(input); assertNotNull(verifyField); assertNotNull(keyStatus);
                assertNotNull(privateShelf); assertNotNull(publicShelf);
                algorithm.setValue("RSA-SHA256-PKCS1");
                input.setText(MESSAGE);
                AuthenticationKeyState keys = (AuthenticationKeyState) get(controller, "authenticationKeyState");

                for (LanguagePreference language : List.of(LanguagePreference.EN, LanguagePreference.ES)) {
                    I18nService.getInstance().setPreference(language);
                    keys.setPrivateKey(null);
                    privateArea.clear();
                    shelf.clear();
                    shelf.addEntry(new ClipboardEntry("invented malformed private key", INVALID_PRIVATE_PEM,
                            ClipboardEntry.Format.PEM, OperationDetail.Classification.SECRET, "Test fixture"));
                    controller.handlePopulateSigPrivKeyShelf();
                    assertEquals(1, privateShelf.getItems().size());
                    privateShelf.getItems().get(0).fire();
                    assertNull(keys.privateKey(), "A malformed private PEM must clear only its private-key cache");
                    assertEquals("Error loading private key", keyStatus.getText());
                    String privateError = errorText(root);
                    assertFalse(privateError.isBlank(), "Malformed private key must show a readable error in " + language);
                    transcript.add(language + " malformed-private=" + concise(privateError));

                    keys.setPublicKey(null);
                    publicArea.clear();
                    shelf.clear();
                    shelf.addEntry(new ClipboardEntry("invented malformed public key", INVALID_PUBLIC_PEM,
                            ClipboardEntry.Format.PEM, OperationDetail.Classification.PUBLIC, "Test fixture"));
                    controller.handlePopulateSigPubKeyShelf();
                    assertEquals(1, publicShelf.getItems().size());
                    publicShelf.getItems().get(0).fire();
                    assertNull(keys.publicKey(), "A malformed public PEM must clear only its public-key cache");
                    assertEquals("Error loading public key", keyStatus.getText());
                    String publicError = errorText(root);
                    assertFalse(publicError.isBlank(), "Malformed public key must show a readable error in " + language);
                    transcript.add(language + " malformed-public=" + concise(publicError));
                }

                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(1024);
                KeyPair pair = generator.generateKeyPair();
                String publicKeyPem = pem("PUBLIC KEY", pair.getPublic().getEncoded());
                privatePem.set(pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
                AppSettings.getInstance().setSecretVisibilityProfile(SecretVisibilityProfile.FULL_LAB);
                shell.getHistoryManager().clearHistory();
                shelf.clear();

                shelf.addEntry(ClipboardEntry.sessionOnlyPrivateKey(privatePem.get(), "Test fixture", "RSA"));
                shelf.addEntry(new ClipboardEntry("invented public key", publicKeyPem,
                        ClipboardEntry.Format.PEM, OperationDetail.Classification.PUBLIC, "Test fixture"));
                controller.handlePopulateSigPrivKeyShelf();
                assertEquals(1, privateShelf.getItems().size());
                privateShelf.getItems().get(0).fire();
                controller.handlePopulateSigPubKeyShelf();
                assertEquals(1, publicShelf.getItems().size());
                publicShelf.getItems().get(0).fire();
                assertNotNull(keys.privateKey()); assertNotNull(keys.publicKey());
                assertArrayEquals(pair.getPrivate().getEncoded(), keys.privateKey().getEncoded());
                assertArrayEquals(pair.getPublic().getEncoded(), keys.publicKey().getEncoded());
                assertEquals(privatePem.get(), privateArea.getText());
                assertEquals(publicKeyPem, publicArea.getText());
                assertEquals("Public: RSA 1024 bits", keyStatus.getText());
                transcript.add("FULL_LAB shelf-selection=private/public cache=private/public status=" + keyStatus.getText());

                keys.setPrivateKey(null); keys.setPublicKey(null);
                privateArea.clear(); publicArea.clear(); keyStatus.setText("");
                controller.handlePopulateSigPubKeyShelf();
                assertEquals(1, publicShelf.getItems().size());
                publicShelf.getItems().get(0).fire();
                controller.handlePopulateSigPrivKeyShelf();
                assertEquals(1, privateShelf.getItems().size());
                privateShelf.getItems().get(0).fire();
                assertNotNull(keys.privateKey()); assertNotNull(keys.publicKey());
                assertEquals("Private: RSA 1024 bits", keyStatus.getText());
                transcript.add("FULL_LAB shelf-selection=public/private cache=private/public status=" + keyStatus.getText());
                shelf.clear();
                runSignatureAndVerify(controller, shell, input, verifyField, signatures);
                inspectSurfaces(SecretVisibilityProfile.FULL_LAB, privatePem.get(), signatures,
                        shell, root, transcript, violations);

                for (SecretVisibilityProfile profile : List.of(
                        SecretVisibilityProfile.MASKED, SecretVisibilityProfile.REDACTED)) {
                    AppSettings.getInstance().setSecretVisibilityProfile(profile);
                    shell.getHistoryManager().clearHistory();
                    shelf.clear();
                    keys.setPrivateKey(null); keys.setPublicKey(null);
                    privateArea.clear(); publicArea.clear();
                    keyStatus.setText("");
                    controller.loadGeneratedKeyPair(pair, publicKeyPem, privatePem.get());
                    assertNotNull(keys.privateKey());
                    assertNotNull(keys.publicKey());
                    assertEquals(privatePem.get(), privateArea.getText());
                    assertEquals(publicKeyPem, publicArea.getText());
                    transcript.add(profile + " generated-pair=private/public cached status=" + keyStatus.getText());

                    // No test-only key item is left on the Shelf during restricted-surface checks.
                    shelf.clear();
                    input.setText(MESSAGE);
                    runSignatureAndVerify(controller, shell, input, verifyField, signatures);
                    inspectSurfaces(profile, privatePem.get(), signatures, shell, root, transcript, violations);
                }
                I18nService.getInstance().setPreference(LanguagePreference.EN);
            });
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }

        String logs = stdout.toString(StandardCharsets.UTF_8) + stderr.toString(StandardCharsets.UTF_8);
        assertFalse(logs.contains(privatePem.get()), "Invented private PEM reached application logs");
        String privateDerBase64 = Base64.getEncoder().encodeToString(
                java.util.Base64.getMimeDecoder().decode(privatePem.get().replace(
                        "-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "")));
        assertFalse(logs.contains(privateDerBase64), "Invented private-key encoding reached application logs");
        for (String signature : signatures) assertFalse(logs.contains(signature), "Signature bytes reached application logs");
        transcript.add("telemetry/logs=no invented private key or signature bytes");
        Files.createDirectories(Path.of("target"));
        String joined = String.join("\n", transcript);
        Files.writeString(Path.of("target/authentication-key-transcript.txt"), joined + "\n");
        assertTrue(violations.isEmpty(), String.join("\n", violations) + "\nTranscript:\n" + joined);
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(joined.getBytes(StandardCharsets.UTF_8)));
        assertEquals("cb01b7abb468fe19fabe21f76351bfc80a20c0706809ce5f6a06c275c090c805", digest, joined);
        onFx(() -> {
            ModernMainController shell = shellRef.get();
            if (shell != null) shell.shutdown();
            if (stageRef.get() != null) { stageRef.get().close(); stageRef.get().setScene(null); }
        });
    }

    private void runSignatureAndVerify(AuthenticationController controller, ModernMainController shell,
                                       TextArea input, TextField verifyField, List<String> signatures) throws Exception {
        input.setText(MESSAGE);
        controller.handleSign();
        OperationResult signed = lastResult(shell);
        assertEquals("Data Signed", signed.getOperation());
        assertEquals(OperationDetail.Classification.SECRET, signed.getOutputClassification());
        String signatureHex = HexFormat.of().withUpperCase().formatHex(signed.getOutput());
        signatures.add(signatureHex);
        verifyField.setText(signatureHex);
        controller.handleVerify();
        OperationResult verified = lastResult(shell);
        assertEquals("Signature Verified", verified.getOperation());
        assertTrue(verified.getDetails().stream().anyMatch(detail ->
                "Result".equals(detail.name()) && "VALID".equals(detail.value())));
    }

    private void inspectSurfaces(SecretVisibilityProfile profile, String privateKey, List<String> signatures,
                                 ModernMainController shell, Parent root, List<String> transcript,
                                 List<String> violations) throws Exception {
        String viewer = shell.resolveCurrentOutputText();
        String inspector = nodeText(root.lookup("#inspectorPanel"));
        String status = ((Labeled) root.lookup("#statusLabel")).getText();
        String history = new com.google.gson.Gson().toJson(shell.getHistoryManager().getHistoryItems());
        if (profile == SecretVisibilityProfile.FULL_LAB) {
            assertTrue(signatures.stream().anyMatch(viewer::contains),
                    "FULL_LAB keeps the signature available in the result viewer");
            transcript.add(profile + " result=visible private-key=absent");
            closeExpandedViewer(shell);
            return;
        }
        shelf.clear();
        shell.handleAddCurrentOutputToShelf();
        String shelfContent = shelf.getEntries().stream().map(ClipboardEntry::getValue)
                .reduce("", (left, right) -> left + "\n" + right);
        shell.handleOpenExpandedResultViewer();
        String expanded = expandedContent(shell);
        List<String> keyNeedles = List.of(privateKey,
                Base64.getEncoder().encodeToString(java.util.Base64.getMimeDecoder().decode(
                        privateKey.replace("-----BEGIN PRIVATE KEY-----", "")
                                .replace("-----END PRIVATE KEY-----", ""))));
        String[] surfaces = {viewer, inspector, history, shelfContent, status, expanded};
        String[] surfaceNames = {"result", "inspector", "history", "shelf", "status", "expanded"};
        for (int index = 0; index < surfaces.length; index++) {
            String surface = surfaces[index];
            for (String keyNeedle : keyNeedles) {
                if (surface.contains(keyNeedle)) violations.add(profile + " private key leaked in " + surfaceNames[index]);
            }
            for (String signature : signatures) {
                if (surface.contains(signature)) violations.add(profile + " signature leaked in " + surfaceNames[index]);
            }
        }
        transcript.add(profile + " result=safe inspector=safe history=safe shelf=safe status=safe expanded=safe");
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

    private static String concise(String text) { return text.replace('\n', ' ').replaceAll("\\s+", " ").trim(); }

    private static String pem(String type, byte[] der) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
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
