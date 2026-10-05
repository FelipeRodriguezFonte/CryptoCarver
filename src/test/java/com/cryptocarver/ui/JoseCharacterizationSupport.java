package com.cryptocarver.ui;

import com.cryptocarver.model.*;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import org.junit.jupiter.api.Assertions;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Real JOSE controls and shell rendering; fixtures never write the user's history or Shelf. */
final class JoseCharacterizationSupport implements AutoCloseable {
    static final String KEY = "0123456789ABCDEF0123456789ABCDEF";
    static final String PAYLOAD = "{\"sub\":\"invented-jose-71\"}";
    static final com.cryptocarver.crypto.JoseKeyMaterial.SecretEncoding UTF8 =
            com.cryptocarver.crypto.JoseKeyMaterial.SecretEncoding.UTF8;
    final FXMLLoader loader;
    final JOSEController controller;
    final Recorder reporter = new Recorder();
    final ModernMainController shell = new ModernMainController();
    final List<String> lines = new ArrayList<>();
    final LanguagePreference language = AppSettings.getInstance().getLanguagePreference();
    final SecretVisibilityProfile profile = AppSettings.getInstance().getSecretVisibilityProfile();
    final List<ClipboardEntry> shelf = ClipboardShelfManager.getInstance().getEntries();

    JoseCharacterizationSupport() throws Exception {
        loader = UiTestFxml.loader("/fxml/jose.fxml");
        loader.load();
        controller = loader.getController();
        controller.setReporter(reporter);
    }
    static void startFx() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.countDown(); }); }
        catch (IllegalStateException alreadyStarted) { ready.countDown(); }
        Assertions.assertTrue(ready.await(15, TimeUnit.SECONDS));
    }
    @SuppressWarnings("unchecked") <T> T control(String name) { return (T) loader.getNamespace().get(name); }
    TextArea area(String name) { return control(name); }
    ComboBox<String> combo(String name) { return control(name); }
    CheckBox check(String name) { return control(name); }
    Label label(String name) { return control(name); }
    void invoke(String name) throws Exception {
        var method = JOSEController.class.getDeclaredMethod(name); method.setAccessible(true); method.invoke(controller);
    }
    void language(LanguagePreference value) { I18nService.getInstance().setPreference(value); }
    void line(String name, Object value) { lines.add(name + "=" + value); }
    void published(String name) {
        Assertions.assertNotNull(reporter.result, reporter.error);
        line(name, reporter.result.getOperation());
        line(name + "_details", reporter.result.getDetails().stream()
                .filter(d -> d.classification() == OperationDetail.Classification.PUBLIC).toList());
    }
    void privacy(OperationResult result, String... secrets) throws Exception {
        for (SecretVisibilityProfile visibility : SecretVisibilityProfile.values()) {
            AppSettings.getInstance().setSecretVisibilityProfile(visibility);
            String expanded = shell.renderPublishedResult(result, visibility);
            String status = Objects.toString(result.getStatusMessage(), "");
            // The same visibility function used by the shell's history view.
            var method = ModernMainController.class.getDeclaredMethod("visibleOperationDetails", List.class);
            method.setAccessible(true);
            String history = method.invoke(shell, ResultPresentationPolicy.detailsForHistory(result)).toString();
            boolean blocked = ResultPresentationPolicy.isShelfCaptureBlockedByVisibility(
                    ResultPresentationPolicy.classifyPublishedResult(result), visibility);
            if (visibility != SecretVisibilityProfile.FULL_LAB) {
                for (String secret : secrets) {
                    Assertions.assertFalse(expanded.contains(secret), visibility + " expanded output leaked secret");
                    Assertions.assertFalse(history.contains(secret), visibility + " history leaked secret");
                    Assertions.assertFalse(status.contains(secret), visibility + " status leaked secret");
                }
                if (result.getOutput() != null && secrets.length > 0
                        && new String(result.getOutput(), StandardCharsets.UTF_8).contains(secrets[secrets.length-1]))
                    Assertions.assertTrue(blocked, visibility + " Shelf accepts decrypted plaintext");
            }
            line(result.getOperation()+"_"+visibility, "history/expanded/status checked;ShelfBlocked="+blocked);
        }
    }
    void digest(String expected) throws Exception {
        String text = String.join("\n", lines);
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        Assertions.assertEquals(expected, actual, text);
    }
    @Override public void close() {
        Assertions.assertEquals(shelf, ClipboardShelfManager.getInstance().getEntries());
        // Recorder history is isolated in memory; no user HistoryManager is ever changed.
        reporter.history.clear();
        AppSettings.getInstance().setSecretVisibilityProfile(profile);
        I18nService.getInstance().setPreference(language);
        shell.shutdown();
    }
    static final class Recorder implements StatusReporter {
        OperationResult result;
        String error;
        final List<OperationResult> history = new ArrayList<>();
        @Override public void updateStatus(String message) { }
        @Override public void updateInspector(String operation, byte[] input, byte[] output, List<OperationDetail> details) { }
        @Override public void showError(String title, String message) { error = message; }
        @Override public void publish(OperationResult result) { this.result = result; history.add(result); }
    }
}
