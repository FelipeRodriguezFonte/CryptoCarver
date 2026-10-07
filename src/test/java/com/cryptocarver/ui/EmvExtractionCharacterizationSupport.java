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

/** Shared real-shell fixture for EMV extraction characterizations; isolated state only. */
abstract class EmvExtractionCharacterizationSupport {
    @TempDir Path tempDir;
    protected AppSettings previousSettings;
    protected LanguagePreference previousLanguage;
    protected String previousTestMode;
    protected List<ClipboardEntry> previousShelf;
    protected ClipboardShelfManager shelf;
    protected ModernMainController shell;
    protected Stage stage;
    protected Parent root;

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
                if (shell != null) { shell.getHistoryManager().clearHistory(); shell.shutdown(); }
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

    protected void inspectSurfaces(SecretVisibilityProfile profile, String operation, List<String> secrets,
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
            if (fullLab && operation.endsWith(" keys") && i == 0 && !containsSecret) {
                violations.add("FULL_LAB " + operation + " should show an invented key/cryptogram in result viewer");
            } else if (fullLab && operation.endsWith(" keys") && i == 3 && !shelf.getEntries().isEmpty() && !containsSecret) {
                violations.add("FULL_LAB " + operation + " should retain an explicitly captured result on Shelf");
            } else if (!fullLab && containsSecret) {
                violations.add(profile + " " + operation + " leaked an invented key/cryptogram in " + names[i]);
            }
            states.add(names[i] + "=" + (containsSecret ? "visible" : "safe"));
        }
        transcript.add(profile + " " + operation + " " + String.join(" ", states));
        closeExpandedViewer(shell);
    }

    protected static String expandedContent(ModernMainController shell) throws Exception {
        Object viewer = get(shell, "expandedTextViewer");
        Object area = get(viewer, "contentArea");
        return area instanceof TextArea text ? text.getText() : "";
    }

    protected static void closeExpandedViewer(ModernMainController shell) throws Exception {
        Object viewer = get(shell, "expandedTextViewer");
        var hide = viewer.getClass().getDeclaredMethod("hide");
        hide.setAccessible(true);
        hide.invoke(viewer);
    }

    protected static String nodeText(Node node) {
        if (node == null) return "";
        String own = node instanceof Labeled labeled ? labeled.getText()
                : node instanceof TextInputControl text ? text.getText() : "";
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) own += "\n" + nodeText(child);
        }
        return own;
    }

    protected static Object get(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException absent) { }
        }
        throw new NoSuchFieldException(name);
    }

    protected static void set(Object target, String name, Object value) throws Exception {
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

    @FunctionalInterface protected interface FxAction { void run() throws Exception; }
    protected static void onFx(FxAction action) throws Exception {
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

    protected EMVController controller() throws Exception {
        return (EMVController) get(shell, "emvController");
    }
    protected void resetShared() {
        shelf.clear(); shell.getHistoryManager().clearHistory();
    }
    protected static void pinTranscript(String name, String expected, List<String> lines) throws Exception {
        String joined = String.join("\n", lines);
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/" + name + "-transcript.txt"), joined + "\n");
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(joined.getBytes(StandardCharsets.UTF_8)));
        Files.writeString(Path.of("target/" + name + "-digest.txt"), digest);
        if (!expected.isEmpty()) assertEquals(expected, digest, "Transcript:\n" + joined);
    }
}
