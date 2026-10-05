package com.cryptocarver.ui;

import com.cryptocarver.crypto.EMVOperations;
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
import java.lang.reflect.Method;
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

/** Pins session-key UI behavior and visibility of its result surfaces. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class EmvSessionKeyCharacterizationUITest {
    private static final String IMK = "0123456789ABCDEFFEDCBA9876543210";
    private static final String PAN = "4111111111111111";
    private static final String PAN_SEQUENCE = "00";
    private static final String ATC = "0001";
    private static final String SM_MK_SMI = "112233445566778899AABBCCDDEEFF00";
    private static final String SM_MK_SMC = "FFEEDDCCBBAA99887766554433221100";
    private static final String SM_PAN_SEQUENCE = "1234567890123456";
    private static final String SM_AC = "1020304050607080";

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
    void sessionKeysAndSecureMessagingKeysHaveStableVisibilityTranscript() throws Exception {
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
                TextArea sessionResult = (TextArea) get(controller, "sessionKeyResultArea");
                for (LanguagePreference language : List.of(LanguagePreference.EN, LanguagePreference.ES)) {
                    I18nService.getInstance().setPreference(language);
                    assertReadableError(controller, sessionResult, language, "invalid-pan", "BAD-PAN", IMK, ATC);
                    assertReadableError(controller, sessionResult, language, "invalid-atc", PAN, IMK, "ZZZZ");
                    assertReadableError(controller, sessionResult, language, "invalid-key-length", PAN, "1234", ATC);
                }
                transcript.addAll(invalidTranscript);

                String iccMasterKey = EMVOperations.deriveICCMasterKey(IMK, PAN, PAN_SEQUENCE);
                String sessionKey = EMVOperations.deriveSessionKey(iccMasterKey, ATC, "");
                transcript.add("vector=repo EMV Book 2 A1.4.1/A1.3 cross-checked house key ICC="
                        + iccMasterKey + " session=" + sessionKey);
                for (SecretVisibilityProfile profile : List.of(SecretVisibilityProfile.FULL_LAB,
                        SecretVisibilityProfile.MASKED, SecretVisibilityProfile.REDACTED)) {
                    AppSettings.getInstance().setSecretVisibilityProfile(profile);
                    setSessionInputs(controller, IMK, PAN, PAN_SEQUENCE, ATC);
                    controller.handleDeriveSessionKey();
                    assertTrue(sessionResult.getText().contains(iccMasterKey), "ICC key calculation changed");
                    assertTrue(sessionResult.getText().contains(sessionKey), "Session key calculation changed");
                    OperationResult published = lastResult(shell);
                    assertEquals("Session Key Derivation", published.getOperation());
                    transcript.add(profile + " session classification=" + ResultPresentationPolicy.classifyPublishedResult(published));
                    inspectSurfaces(profile, "session", List.of(IMK, iccMasterKey, sessionKey),
                            shell, root, transcript, violations);

                    shelf.clear();
                    shell.getHistoryManager().clearHistory();
                    configureInventedSecureMessagingKeys(controller);
                    controller.handleSmDeriveSessionKeys();
                    String smUdkSmi = ((TextField) get(controller, "smUdkSmiField")).getText();
                    String smUdkSmc = ((TextField) get(controller, "smUdkSmcField")).getText();
                    String smMac = ((TextField) get(controller, "smSkMacField")).getText();
                    String smEnc = ((TextField) get(controller, "smSkEncField")).getText();
                    assertTrue(smUdkSmi.matches("[0-9A-F]{32}"));
                    assertTrue(smUdkSmc.matches("[0-9A-F]{32}"));
                    assertTrue(smMac.matches("[0-9A-F]{32}"));
                    assertTrue(smEnc.matches("[0-9A-F]{32}"));
                    OperationResult smPublished = lastResult(shell);
                    assertEquals("Secure Messaging Session Keys", smPublished.getOperation());
                    assertEquals(OperationDetail.Classification.SECRET,
                            ResultPresentationPolicy.classifyPublishedResult(smPublished));
                    transcript.add(profile + " secure-messaging classification="
                            + ResultPresentationPolicy.classifyPublishedResult(smPublished));
                    inspectSurfaces(profile, "secure-messaging", List.of(SM_MK_SMI, SM_MK_SMC,
                            smUdkSmi, smUdkSmc, smMac, smEnc), shell, root, transcript, violations);
                    shelf.clear();
                    shell.getHistoryManager().clearHistory();
                }
                I18nService.getInstance().setPreference(LanguagePreference.EN);
            });
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }

        String logs = stdout.toString(StandardCharsets.UTF_8) + stderr.toString(StandardCharsets.UTF_8);
        for (String secret : List.of(IMK, "30089565674D73ED841A6F0637029C18", "38F14068B3EA57C194F8E3A20D51E3E6",
                SM_MK_SMI, SM_MK_SMC)) {
            if (logs.contains(secret)) violations.add("telemetry/logs leaked an invented key");
        }
        transcript.add("telemetry/logs=" + (violations.stream().anyMatch(v -> v.startsWith("telemetry/")) ? "leaked" : "safe"));
        violations.addAll(localizationFailures);
        String joined = String.join("\n", transcript);
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/emv-session-transcript.txt"), joined + "\n");
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(joined.getBytes(StandardCharsets.UTF_8)));
        assertEquals("429715cbed6ef4bd832d024d11a92698aa86cd84013f4a1fac66218bfb8935cc", digest, joined);
        assertTrue(violations.isEmpty(), String.join("\n", violations) + "\nTranscript:\n" + joined);
    }

    private void assertReadableError(EMVController controller, TextArea result, LanguagePreference language,
                                     String name, String pan, String imk, String atc) throws Exception {
        setSessionInputs(controller, imk, pan, PAN_SEQUENCE, atc);
        result.clear();
        controller.handleDeriveSessionKey();
        String message = concise(result.getText());
        assertFalse(message.isBlank(), name + " must show readable feedback in " + language);
        assertTrue(message.startsWith("Error:") || message.startsWith("Error de"), message);
        String messageKey = switch (name) {
            case "invalid-pan" -> "module.emv.error.panDigits";
            case "invalid-atc" -> "module.emv.error.atcFormat";
            case "invalid-key-length" -> "module.emv.error.imkLength";
            default -> throw new IllegalArgumentException("Unexpected EMV validation case: " + name);
        };
        String localized = I18nService.getInstance().text(messageKey);
        if (!localized.equals(message)) localizationFailures.add(language + " " + name + " is not localized");
        invalidTranscript.add(language + " " + name + "=" + stableDiagnostic(name, message, localized));
    }

    private final List<String> invalidTranscript = new ArrayList<>();
    private final List<String> localizationFailures = new ArrayList<>();

    private static String stableDiagnostic(String name, String actual, String expected) {
        if (expected.equals(actual)) return expected;
        // Normalize the JDK/provider key-length span before including this diagnostic in the digest.
        return "invalid-key-length".equals(name) ? "Error: <jdk-exception>" : actual;
    }

    private void setSessionInputs(EMVController controller, String imk, String pan, String panSequence, String atc)
            throws Exception {
        ((TextField) get(controller, "imkField")).setText(imk);
        ((TextField) get(controller, "panFieldSession")).setText(pan);
        ((TextField) get(controller, "panSeqFieldSession")).setText(panSequence);
        ((TextField) get(controller, "atcField")).setText(atc);
    }

    private static void configureInventedSecureMessagingKeys(EMVController controller) throws Exception {
        ((javafx.scene.control.ComboBox<String>) get(controller, "smSchemeCombo")).setValue("Mastercard");
        ((TextField) get(controller, "smMkSmiField")).setText(SM_MK_SMI);
        ((TextField) get(controller, "smMkSmcField")).setText(SM_MK_SMC);
        ((TextField) get(controller, "smPanSeqField")).setText(SM_PAN_SEQUENCE);
        ((TextField) get(controller, "smAcField")).setText(SM_AC);
        ((TextField) get(controller, "smCommandNumberField")).setText("3");
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
        String[] surfaces = {viewer, inspector, history, shelfContent, statusLabel.getText(), expanded};
        String[] names = {"result", "inspector", "history", "shelf", "status", "expanded"};
        List<String> states = new ArrayList<>();
        for (int i = 0; i < surfaces.length; i++) {
            String content = surfaces[i];
            boolean containsSecret = secrets.stream().anyMatch(secret -> content != null && content.contains(secret));
            if (fullLab && i == 0 && !containsSecret) {
                violations.add("FULL_LAB " + operation + " should show invented key material in the result viewer");
            } else if (fullLab && i == 3 && !shelf.getEntries().isEmpty() && !containsSecret) {
                violations.add("FULL_LAB " + operation + " should retain an explicitly captured result on Shelf");
            } else if (!fullLab && containsSecret) {
                violations.add(profile + " " + operation + " leaked an invented key in " + names[i]);
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
        Method hide = viewer.getClass().getDeclaredMethod("hide");
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
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
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
