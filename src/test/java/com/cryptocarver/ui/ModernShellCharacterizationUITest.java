package com.cryptocarver.ui;

import com.cryptocarver.model.AppDiagnostics;
import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.KeyboardShortcutEntry;
import com.cryptocarver.model.KeyboardShortcutRegistry;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.model.PlatformShortcuts;
import com.cryptocarver.service.I18nService;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ModernShellCharacterizationUITest {
    private static final Path TEST_HOME = Path.of("target", "test-home").toAbsolutePath();
    private String previousHome;
    private String previousTestMode;
    private LanguagePreference previousLanguage;

    @BeforeAll
    static void startJavaFx() throws Exception {
        Files.createDirectories(TEST_HOME);
        System.setProperty("user.home", TEST_HOME.toString());
        System.setProperty("javafx.cachedir", TEST_HOME.resolve("javafx-cache").toString());
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                latch.countDown();
            });
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        assertTrue(latch.await(15, TimeUnit.SECONDS));
    }

    @BeforeEach
    void configureIsolatedTestSettings() throws Exception {
        previousHome = System.getProperty("user.home");
        previousTestMode = System.getProperty("test.mode");
        previousLanguage = I18nService.getInstance().getPreference();
        System.setProperty("user.home", TEST_HOME.toString());
        System.setProperty("test.mode", "true");
        AppSettings.setInstanceForTesting(new AppSettings(TEST_HOME.resolve("settings.json")));
        AppSettings.getInstance().setLanguagePreference(previousLanguage);
        I18nService.getInstance().refreshFromSettings();
    }

    @AfterEach
    void restoreSystemAndSettings() {
        AppSettings.resetInstanceForTesting();
        I18nService.getInstance().setPreference(previousLanguage);
        restoreProperty("test.mode", previousTestMode);
        restoreProperty("user.home", previousHome);
    }

    @AfterAll
    static void leaveJavaFxRunningForOtherUiTests() {
        Platform.setImplicitExit(false);
    }

    @Test
    void diagnosticsLoadedWithProductionFxmlContainsOnlyExpectedSafeFields() throws Exception {
        loadProductionController();
        String report = AppDiagnostics.report("synthetic-display-summary");
        assertTrue(report.startsWith("CryptoCarver diagnostics\n"));
        assertTrue(report.contains("Application version:"));
        assertTrue(report.contains("Java:"));
        assertTrue(report.contains("Display: synthetic-display-summary"));
        assertTrue(report.contains("No key material, input data, credentials or file paths"));
        assertFalse(report.contains(TEST_HOME.toString()));
        assertFalse(report.contains(System.getProperty("user.name", "unknown")));
        Path output = TEST_HOME.resolve("diagnostics-report.txt");
        ModernMainController.writeDiagnosticsReport(output, report);
        assertEquals(report, Files.readString(output));
    }

    @Test
    void everyShortcutCatalogEntryMatchesItsProductionMenuAcceleratorInEnglishAndSpanish() throws Exception {
        setLanguage(LanguagePreference.EN);
        AtomicReference<ModernMainController> englishController = new AtomicReference<>();
        runOnFxThread(() -> englishController.set(loadProductionController()));
        List<String> english = verifyShortcutEntries(englishController.get(), Locale.ENGLISH);
        setLanguage(LanguagePreference.ES);
        AtomicReference<ModernMainController> spanishController = new AtomicReference<>();
        runOnFxThread(() -> spanishController.set(loadProductionController()));
        List<String> spanish = verifyShortcutEntries(spanishController.get(), Locale.forLanguageTag("es"));
        List<String> expectedEnglishCatalog = expectedShortcutCatalog();
        assertEquals(expectedEnglishCatalog, english);
        assertEquals(expectedEnglishCatalog, spanish);
    }

    @Test
    void showInfoAndShowErrorReportExpectedNonModalNoticesInTestMode() throws Exception {
        AtomicReference<ModernMainController> controller = new AtomicReference<>();
        runOnFxThread(() -> controller.set(loadProductionController()));
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        try (PrintStream capturedOut = new PrintStream(stdout, true, StandardCharsets.UTF_8);
             PrintStream capturedErr = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
            System.setOut(capturedOut);
            System.setErr(capturedErr);
            runOnFxThread(() -> {
                controller.get().showInfo("Synthetic info title", "Synthetic info message");
                controller.get().showError("Synthetic error title", "Synthetic error message");
            });
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
        assertTrue(stdout.toString(StandardCharsets.UTF_8).contains("SHOW_INFO: Synthetic info title - Synthetic info message"));
        assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("SHOW_ERROR: Synthetic error title - Synthetic error message"));
    }

    @Test
    void informationalAlertOkButtonFitsItsLocalizedLabel() throws Exception {
        AtomicReference<Alert> alertReference = new AtomicReference<>();
        runOnFxThread(() -> {
            ButtonType accept = new ButtonType("Aceptar", ButtonBar.ButtonData.OK_DONE);
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Synthetic notice", accept);
            alert.getDialogPane().getStyleClass().add("cc-dialog-pane");
            alert.getDialogPane().getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
            alert.getDialogPane().getStylesheets().add(getClass().getResource("/css/theme-light.css").toExternalForm());
            DialogService.prepareInformationalDialog(alert);
            alert.show();
            alertReference.set(alert);
        });
        AtomicReference<Boolean> buttonFits = new AtomicReference<>(false);
        AtomicReference<String> buttonGeometry = new AtomicReference<>("");
        runOnFxThread(() -> {
            Alert alert = alertReference.get();
            alert.getDialogPane().applyCss();
            alert.getDialogPane().layout();
            Button button = (Button) alert.getDialogPane().lookupButton(alert.getDialogPane().getButtonTypes().get(0));
            ButtonBar buttonBar = (ButtonBar) alert.getDialogPane().lookup(".button-bar");
            // GTK can allocate exactly prefWidth; measure the skin's displayed text,
            // rather than demand arbitrary extra pixels beyond the preferred size.
            javafx.scene.text.Text rendered = (javafx.scene.text.Text) button.lookup(".text");
            buttonFits.set(rendered != null && button.getText().equals(rendered.getText()) && button.getWidth() >= 80);
            buttonGeometry.set("actual=" + button.getWidth() + ", preferred=" + button.prefWidth(-1) + ", minimum=" + button.getMinWidth()
                    + ", barMinimum=" + buttonBar.getButtonMinWidth() + ", paneClasses=" + alert.getDialogPane().getStyleClass());
            alert.close();
        });
        assertTrue(buttonFits.get(), "Informational Alert clips the localized OK label: " + buttonGeometry.get());
    }

    private static List<String> verifyShortcutEntries(ModernMainController controller, Locale locale) throws Exception {
        List<String> checked = new ArrayList<>();
        MenuBar menuBar = readField(controller, "mainMenuBar");
        List<MenuItem> menuItems = new ArrayList<>();
        for (Menu menu : menuBar.getMenus()) {
            collectMenuItems(menu, menuItems);
        }
        for (KeyboardShortcutEntry entry : KeyboardShortcutRegistry.getShortcuts()) {
            javafx.scene.input.KeyCombination expected = javafx.scene.input.KeyCombination.valueOf(entry.getKeyCombination());
            boolean registered = menuItems.stream().anyMatch(item -> expected.equals(item.getAccelerator())
                    && (locale.getLanguage().equals("es")
                    || KeyboardShortcutRegistry.findShortcutByAction(item.getText()).filter(found -> found == entry).isPresent()));
            if (entry.getActionName().equals("Toggle Favorite")) {
                Button favorite = (Button) readField(controller, "favoriteToggleBtn");
                registered = favorite.getTooltip().getText().contains(entry.getDisplayCombination());
            }
            assertTrue(registered, "Unregistered shortcut: " + entry.getActionName() + "; menu entries=" + menuItems.stream()
                    .map(item -> item.getText() + "=" + item.getAccelerator()).toList());
            checked.add(entry.getDisplayCombination() + " | " + entry.getActionName() + " | " + entry.getDescription());
        }
        return checked;
    }

    private static List<String> expectedShortcutCatalog() {
        return List.of(
                expectedShortcut("Shortcut+K", "Command Palette", "Open operation search and quick palette"),
                expectedShortcut("F1", "Keyboard Shortcuts", "Show application keyboard shortcuts reference"),
                expectedShortcut("Shortcut+S", "Save Session", "Save current workspace state to session file"),
                expectedShortcut("Shortcut+O", "Import Key", "Import cryptographic key or certificate file"),
                expectedShortcut("Shortcut+Q", "Exit Application", "Close CryptoCarver workbench"),
                expectedShortcut("Shortcut+Shift+O", "Clear Output", "Clear output results in active view"),
                expectedShortcut("Shortcut+Shift+C", "Copy Output", "Copy operation result text to system clipboard"),
                expectedShortcut("Shortcut+B", "Toggle Side Panel", "Show/hide navigation sidebar"),
                expectedShortcut("Shortcut+I", "Toggle Inspector", "Show/hide result inspector panel"),
                expectedShortcut("Shortcut+Shift+E", "Expand Result", "Open expanded result text viewer"),
                expectedShortcut("Shortcut+Shift+T", "Expand Table", "Open expanded table data viewer"),
                expectedShortcut("Shortcut+PLUS", "Zoom In (Font)", "Increase application interface font size"),
                expectedShortcut("Shortcut+MINUS", "Zoom Out (Font)", "Decrease application interface font size"),
                expectedShortcut("Shortcut+T", "Epoch Converter", "Open Unix timestamp epoch conversion tool"),
                expectedShortcut("Shortcut+J", "JSON Formatter", "Open JSON formatter and validator tool"),
                expectedShortcut("Shortcut+Shift+H", "Quick Start", "Navigate to Laboratory Quick Start dashboard"),
                expectedShortcut("Shortcut+Shift+V", "Clipboard Shelf", "Open and refresh the integrated Clipboard Shelf"),
                expectedShortcut("Shortcut+Shift+F", "Toggle Favorite", "Toggle favorite star for current operation"));
    }

    private static String expectedShortcut(String combination, String action, String description) {
        return PlatformShortcuts.display(combination) + " | " + action + " | " + description;
    }

    private static ModernMainController loadProductionController() {
        FXMLLoader loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
        try {
            loader.load();
            return loader.getController();
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private static void setLanguage(LanguagePreference preference) {
        AppSettings.getInstance().setLanguagePreference(preference);
        I18nService.getInstance().refreshFromSettings();
    }

    private static void collectMenuItems(Menu menu, List<MenuItem> items) {
        items.addAll(menu.getItems());
        for (MenuItem item : menu.getItems()) {
            if (item instanceof Menu childMenu) {
                collectMenuItems(childMenu, items);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T readField(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(target);
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private static void runOnFxThread(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(60, TimeUnit.SECONDS), "FX thread timed out");
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }
}
