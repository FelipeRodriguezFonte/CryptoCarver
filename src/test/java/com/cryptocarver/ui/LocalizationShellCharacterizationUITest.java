package com.cryptocarver.ui;

import com.cryptocarver.model.*;
import com.cryptocarver.service.I18nService;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class LocalizationShellCharacterizationUITest {
    @TempDir Path directory;
    private AppSettings previousSettings;
    private LanguagePreference previousLanguage;
    private List<ClipboardEntry> previousShelf;
    private ModernMainController shell;
    private Parent root;
    private Stage stage;
    private Path settingsFile;

    @BeforeEach void load() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); }
        catch (IllegalStateException started) { ready.countDown(); }
        assertTrue(ready.await(15, TimeUnit.SECONDS));
        previousSettings = AppSettings.getInstance();
        previousLanguage = I18nService.getInstance().getPreference();
        previousShelf = List.copyOf(ClipboardShelfManager.getInstance().getEntries());
        settingsFile = directory.resolve("settings.json");
        fx(() -> {
            Platform.setImplicitExit(false);
            AppSettings.setInstanceForTesting(new AppSettings(settingsFile));
            AppSettings.getInstance().setLastRoute("Hashing");
            I18nService.getInstance().setPreference(LanguagePreference.EN);
            var loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
            root = loader.load(); shell = loader.getController();
            stage = new Stage(); stage.setScene(new Scene(root, 1400, 900)); stage.show();
        });
    }

    @AfterEach void restore() throws Exception {
        fx(() -> {
            if (shell != null) {
                // Existing shutdown does not detach this listener; prevent test shells surviving teardown.
                I18nService.getInstance().removeLocaleChangeListener(field(shell, "i18nListener"));
                shell.shutdown();
            }
            if (stage != null) { stage.close(); stage.setScene(null); }
            AppSettings.setInstanceForTesting(previousSettings);
            I18nService.getInstance().setPreference(previousLanguage);
            // This fixture performs no Shelf mutations: its complete contents must survive unchanged.
            assertEquals(previousShelf, ClipboardShelfManager.getInstance().getEntries());
        });
    }

    @Test void languageFontsAndPersistenceTranscript() throws Exception {
        List<String> rows = new ArrayList<>();
        for (LanguagePreference language : List.of(LanguagePreference.EN, LanguagePreference.ES, LanguagePreference.EN)) {
            fx(() -> {
                ((RadioMenuItem) field(shell, language == LanguagePreference.EN ? "languageEnMenuItem" : "languageEsMenuItem")).fire();
                assertEquals(language, AppSettings.getInstance().getLanguagePreference());
                assertEquals(language, new AppSettings(settingsFile).getLanguagePreference());
                rows.add("language=" + language + "; persisted=" + new AppSettings(settingsFile).getLanguagePreference());
                for (String name : List.of("fileMenu", "editMenu", "viewMenu", "securityMenu", "toolsMenu", "helpMenu",
                        "languageMenu", "appearanceMenu", "languageSystemMenuItem", "languageEsMenuItem", "languageEnMenuItem",
                        "toolbarSearchButton", "toolbarSaveSessionButton", "toolbarClearButton", "toolbarExpandButton",
                        "toolbarShelfButton", "toolbarCopyButton", "inspectorTitleLabel", "inspectorHistoryTitle",
                        "inspectorInputBytesTitle", "commandTitleLabel", "errorBannerTitle", "resultLastLabel")) {
                    Object control = field(shell, name);
                    String text = control == null ? "<absent>" : control instanceof MenuItem item ? item.getText() : ((Labeled) control).getText();
                    // Platform shortcut glyphs vary; pin the semantic text and token rather than the OS glyph.
                    if (name.equals("toolbarSearchButton")) text = text.replaceAll("\\([^)]*\\)$", "(SHORTCUT)");
                    rows.add(name + "=" + text);
                }
                SidePanel side = field(shell, "sidePanel");
                rows.add("sideSearch=" + ((TextField) field(side, "searchField")).getPromptText());
                rows.add("commandPrompt=" + ((TextField) field(shell, "commandSearchField")).getPromptText());
                rows.add("statusLanguage=" + ((Label) field(shell, "statusLanguageLabel")).getText());
                rows.add("resultCopyAccessible=" + ((Button) field(shell, "resultCopyButton")).getAccessibleText());
            });
        }
        fx(() -> {
            TextArea area = (TextArea) root.lookup("#hashInputArea");
            assertNotNull(area);
            rows.add("font.initial=" + area.getStyle());
            shell.handleIncreaseFontSize(); rows.add("font.increased=" + area.getStyle());
            assertTrue(area.getStyle().contains("16px"));
            I18nService.getInstance().setPreference(LanguagePreference.ES);
            assertTrue(area.getStyle().contains("16px"));
            shell.handleDecreaseFontSize(); rows.add("font.decreased=" + area.getStyle());
            for (int i = 0; i < 20; i++) shell.handleDecreaseFontSize();
            rows.add("font.minimum=" + area.getStyle()); assertTrue(area.getStyle().contains("8px"));
            for (int i = 0; i < 20; i++) shell.handleIncreaseFontSize();
            rows.add("font.maximum=" + area.getStyle()); assertTrue(area.getStyle().contains("24px"));
            assertEquals(LanguagePreference.ES, new AppSettings(settingsFile).getLanguagePreference());
            rows.add("persisted.afterFonts=" + new AppSettings(settingsFile).getLanguagePreference());
        });
        String transcript = String.join("\n", rows) + "\n";
        Files.writeString(Path.of("target/localization-shell-transcript.txt"), transcript);
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(transcript.getBytes(StandardCharsets.UTF_8)));
        assertEquals("a57ad3eaf8e958a7768a7ecee4bbd22f16b43fee7d4e0985a1b5895df82fdbfb", digest, transcript);
    }

    @Test void fontTraversalPreservesContainerOrderAndNullHandling() throws Exception {
        fx(() -> {
            TextArea area = new TextArea(); TextField field = new TextField(); Label untouched = new Label("label");
            untouched.setStyle("original");
            var box = new javafx.scene.layout.VBox(area, field, untouched);
            var accordion = new Accordion(new TitledPane("title", new ScrollPane(box)));
            var split = new SplitPane(accordion);
            var method = ModernMainController.class.getDeclaredMethod("updateNodeFonts", Node.class);
            method.setAccessible(true); method.invoke(shell, split); method.invoke(shell, new Object[]{null});
            assertEquals("-fx-font-family: 'Monospaced'; -fx-font-size: 14px;", area.getStyle());
            assertEquals(area.getStyle(), field.getStyle()); assertEquals("original", untouched.getStyle());
        });
    }

    @Test void localizationReadsReplacedLiveStateAtPaintTime() throws Exception {
        fx(() -> {
            var coordinator = new ShellLocalizationCoordinator(I18nService.getInstance());
            var view = new ShellLocalizationCoordinator.View(new javafx.scene.layout.VBox());
            RadioMenuItem system = new RadioMenuItem(), light = new RadioMenuItem(), dark = new RadioMenuItem();
            view.themeItems(system, light, dark, null);
            AtomicReference<Label> status = new AtomicReference<>(new Label("Ready"));
            AtomicReference<String> operation = new AtomicReference<>("old operation");
            List<String> calls = new ArrayList<>();
            AtomicReference<StatusReporter> reporter = new AtomicReference<>(new StatusReporter() {
                public void updateStatus(String message) { calls.add("status=" + message); }
                public void updateInspector(String name, byte[] input, byte[] output, List<OperationDetail> details) {}
                public void showError(String title, String message) {}
            });
            AtomicReference<StatusBarPresenter> presenter = new AtomicReference<>();
            AtomicReference<InlineErrorPresenter> errors = new AtomicReference<>();
            var live = new ShellLocalizationCoordinator.LiveState(reporter::get, () -> null, () -> null,
                    operation::get, status::get, presenter::get, errors::get, () -> root,
                    () -> operation.set("current operation"),
                    name -> calls.add("breadcrumbs=" + name), name -> calls.add("favorite=" + name));
            AppSettings.getInstance().setThemePreference(ThemePreference.DARK);
            coordinator.applyLocalization(view, live);
            assertTrue(dark.isSelected());
            assertEquals(List.of("breadcrumbs=current operation", "favorite=current operation", "status=Ready"), calls);
            calls.clear();
            // Replace values after constructing both View and LiveState; no stale snapshots may survive.
            status.set(new Label("operation completed"));
            Label context = new Label();
            presenter.set(new StatusBarPresenter(null, null, context, I18nService.getInstance()));
            AppSettings.getInstance().setThemePreference(ThemePreference.LIGHT);
            I18nService.getInstance().setPreference(LanguagePreference.ES);
            Label errorTitle = new Label();
            errors.set(new InlineErrorPresenter(null, errorTitle, null, null, null, null));
            errors.get().showError(new UserFacingError("Validation Error", "fixture", "retry", "fixtureField"), root);
            coordinator.applyLocalization(view, live);
            assertTrue(light.isSelected()); assertFalse(dark.isSelected());
            assertEquals(List.of("breadcrumbs=current operation", "favorite=current operation"), calls);
            assertEquals("Idioma: Español", context.getText());
            assertEquals("Error de validación", errors.get().getCurrentError().title());
            status.set(new Label("Listo")); calls.clear();
            coordinator.applyLocalization(view, live);
            assertEquals("status=Listo", calls.get(2));
        });
    }

    @SuppressWarnings("unchecked") private static <T> T field(Object object, String name) throws Exception {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return (T) field.get(object);
    }
    @FunctionalInterface private interface FxAction { void run() throws Exception; }
    private static void fx(FxAction action) throws Exception {
        CountDownLatch done = new CountDownLatch(1); AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> { try { action.run(); } catch (Throwable failure) { error.set(failure); } finally { done.countDown(); } });
        assertTrue(done.await(30, TimeUnit.SECONDS)); if (error.get() != null) throw new AssertionError(error.get());
    }
}
