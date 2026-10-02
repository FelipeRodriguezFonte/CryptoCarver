package com.cryptocarver.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.service.I18nService;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.event.Event;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CommandPaletteCharacterizationTest {
    private ModernMainController controller;
    private Stage stage;
    private LanguagePreference previousLanguage;
    private SecretVisibilityProfile previousVisibility;
    private List<String> previousFavorites;
    private String previousRoute;

    @BeforeAll
    static void initializeJavaFxOnce() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                latch.countDown();
            });
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        if (!latch.await(15, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX startup timed out");
        }
    }

    @BeforeEach
    void loadProductionShell() throws Exception {
        previousLanguage = AppSettings.getInstance().getLanguagePreference();
        previousVisibility = AppSettings.getInstance().getSecretVisibilityProfile();
        previousFavorites = AppSettings.getInstance().getFavorites();
        previousRoute = AppSettings.getInstance().getLastRoute();
        runFx(() -> {
            System.setProperty("test.mode", "true");
            System.setProperty("user.home", "target/test-home");
            I18nService.getInstance().setPreference(LanguagePreference.EN);
            AppSettings.getInstance().setLastRoute("Hashing");
            try {
                FXMLLoader loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
                Parent root = loader.load();
                controller = loader.getController();
                stage = new Stage();
                Scene scene = new Scene(root, 1400, 900);
                scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
                stage.setScene(scene);
                stage.show();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });
    }

    @AfterEach
    void restoreSettingsAndCloseShell() throws Exception {
        runFx(() -> {
            I18nService.getInstance().setPreference(previousLanguage);
            AppSettings.getInstance().setLanguagePreference(previousLanguage);
            AppSettings.getInstance().setSecretVisibilityProfile(previousVisibility);
            AppSettings.getInstance().setLastRoute(previousRoute);
            for (String favorite : AppSettings.getInstance().getFavorites()) {
                AppSettings.getInstance().toggleFavorite(favorite);
            }
            for (String favorite : previousFavorites) {
                if (!AppSettings.getInstance().isFavorite(favorite)) {
                    AppSettings.getInstance().toggleFavorite(favorite);
                }
            }
            if (stage != null) {
                stage.close();
            }
        });
    }

    @Test
    void openingAndClosingPaletteControlsVisibilityAndFocus() throws Exception {
        AtomicReference<Node> previousFocus = new AtomicReference<>();
        AtomicReference<VBox> overlay = new AtomicReference<>();
        AtomicReference<TextField> search = new AtomicReference<>();
        runFx(() -> {
            previousFocus.set(stage.getScene().getFocusOwner());
            if (previousFocus.get() == null) {
                ((Parent) stage.getScene().getRoot()).requestFocus();
                previousFocus.set(stage.getScene().getFocusOwner());
            }
            overlay.set(field(controller, "commandPaletteOverlay"));
            search.set(field(controller, "commandSearchField"));
            controller.handleOpenCommandPalette();
        });
        assertTrue(overlay.get().isVisible());
        assertNotNull(search.get());
        runFx(() -> controller.handleCloseCommandPalette());
        assertFalse(overlay.get().isVisible());
        runFx(() -> assertSame(previousFocus.get(), stage.getScene().getFocusOwner()));
    }

    @Test
    void hashSearchRanksHashingFirstAndNoMatchClearsResults() throws Exception {
        AtomicReference<ListView<?>> results = new AtomicReference<>();
        runFx(() -> {
            controller.handleOpenCommandPalette();
            TextField search = field(controller, "commandSearchField");
            results.set(field(controller, "commandResultsListView"));
            search.setText("hash");
        });
        assertEquals("Hashing", ((com.cryptocarver.model.CommandItem) results.get().getItems().get(0)).getTitle());
        runFx(() -> ((TextField) field(controller, "commandSearchField")).setText("nothing-matches-xyz"));
        assertTrue(results.get().getItems().isEmpty());
    }

    @Test
    void enterExecutesSelectedRouteAndEscapeClosesOnly() throws Exception {
        AtomicReference<VBox> overlay = new AtomicReference<>();
        runFx(() -> {
            overlay.set(field(controller, "commandPaletteOverlay"));
            controller.handleOpenCommandPalette();
            TextField search = field(controller, "commandSearchField");
            search.setText("hash");
            search.fireEvent(key(KeyCode.ENTER));
        });
        assertFalse(overlay.get().isVisible());
        assertEquals("Hashing", AppSettings.getInstance().getLastRoute());
        runFx(() -> {
            controller.handleOpenCommandPalette();
            ((TextField) field(controller, "commandSearchField")).fireEvent(key(KeyCode.ESCAPE));
        });
        assertFalse(overlay.get().isVisible());
        assertEquals("Hashing", AppSettings.getInstance().getLastRoute());
    }

    @Test
    void escapeFromFocusedSearchFieldAfterRealAcceleratorClosesPaletteAndRestoresFocus() throws Exception {
        AtomicReference<Node> previousFocus = new AtomicReference<>();
        AtomicReference<VBox> overlay = new AtomicReference<>();
        AtomicReference<TextField> search = new AtomicReference<>();
        runFx(() -> {
            previousFocus.set(stage.getScene().getFocusOwner());
            overlay.set(field(controller, "commandPaletteOverlay"));
            search.set(field(controller, "commandSearchField"));
            Runnable accelerator = stage.getScene().getAccelerators().get(
                    new KeyCodeCombination(KeyCode.K, KeyCombination.SHORTCUT_DOWN));
            assertNotNull(accelerator);
            accelerator.run();
            assertSame(search.get(), stage.getScene().getFocusOwner());
            Event.fireEvent(stage.getScene().getFocusOwner(), key(KeyCode.ESCAPE));
        });
        assertFalse(overlay.get().isVisible());
        runFx(() -> assertSame(previousFocus.get(), stage.getScene().getFocusOwner()));
    }

    @Test
    void escapeFromSearchFieldAfterToolbarButtonOpensPalette() throws Exception {
        AtomicReference<VBox> overlay = new AtomicReference<>();
        AtomicReference<TextField> search = new AtomicReference<>();
        runFx(() -> {
            overlay.set(field(controller, "commandPaletteOverlay"));
            search.set(field(controller, "commandSearchField"));
            ((javafx.scene.control.Button) field(controller, "toolbarSearchButton")).fire();
            assertSame(search.get(), stage.getScene().getFocusOwner());
            Event.fireEvent(stage.getScene().getFocusOwner(), key(KeyCode.ESCAPE));
        });
        assertFalse(overlay.get().isVisible());
    }

    @Test
    void clickOnTheBackdropClosesButClickOnTheCardDoesNot() throws Exception {
        AtomicReference<VBox> overlay = new AtomicReference<>();
        runFx(() -> {
            overlay.set(field(controller, "commandPaletteOverlay"));
            controller.handleOpenCommandPalette();
            Node card = overlay.get().getChildren().get(0);
            Event.fireEvent(card, click(card));
            assertTrue(overlay.get().isVisible());
            Event.fireEvent(overlay.get(), click(overlay.get()));
        });
        assertFalse(overlay.get().isVisible());
    }

    private static javafx.scene.input.MouseEvent click(Node target) {
        return new javafx.scene.input.MouseEvent(target, target, javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                1, 1, 1, 1, javafx.scene.input.MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, true, false, false, null);
    }

    @Test
    void escapeFromFocusedResultsListClosesPalette() throws Exception {
        AtomicReference<VBox> overlay = new AtomicReference<>();
        AtomicReference<ListView<?>> results = new AtomicReference<>();
        runFx(() -> {
            overlay.set(field(controller, "commandPaletteOverlay"));
            results.set(field(controller, "commandResultsListView"));
            controller.handleOpenCommandPalette();
            results.get().requestFocus();
            assertSame(results.get(), stage.getScene().getFocusOwner());
            Event.fireEvent(stage.getScene().getFocusOwner(), key(KeyCode.ESCAPE));
        });
        assertFalse(overlay.get().isVisible());
    }

    @Test
    void arrowKeysMoveTheSelectedCommand() throws Exception {
        AtomicReference<ListView<?>> results = new AtomicReference<>();
        AtomicReference<TextField> search = new AtomicReference<>();
        runFx(() -> {
            controller.handleOpenCommandPalette();
            results.set(field(controller, "commandResultsListView"));
            search.set(field(controller, "commandSearchField"));
            search.get().fireEvent(key(KeyCode.DOWN));
        });
        assertEquals(0, results.get().getSelectionModel().getSelectedIndex());
        runFx(() -> results.get().fireEvent(key(KeyCode.DOWN)));
        assertEquals(1, results.get().getSelectionModel().getSelectedIndex());
        runFx(() -> results.get().fireEvent(key(KeyCode.UP)));
        assertEquals(0, results.get().getSelectionModel().getSelectedIndex());
    }

    @Test
    void laboratoryQuickStartItemNavigatesToItsScreen() throws Exception {
        AtomicReference<Menu> lab = new AtomicReference<>();
        AtomicReference<VBox> quickStart = new AtomicReference<>();
        runFx(() -> {
            MenuBar menuBar = field(controller, "mainMenuBar");
            lab.set(menuBar.getMenus().stream().filter(menu -> "laboratory".equals(menu.getUserData())).findFirst().orElseThrow());
            quickStart.set(field(controller, "quickStartContainer"));
            lab.get().getItems().get(0).fire();
        });
        assertTrue(quickStart.get().isVisible());
    }

    @Test
    void laboratoryFallbackMenuLocalizesAndRoutesEveryProfile() throws Exception {
        AtomicReference<Menu> lab = new AtomicReference<>();
        List<String> routes = new java.util.ArrayList<>();
        runFx(() -> {
            I18nService.getInstance().setPreference(LanguagePreference.ES);
            MenuBar menuBar = new MenuBar();
            LaboratoryMenuCoordinator coordinator = new LaboratoryMenuCoordinator(menuBar, () -> { }, routes::add,
                    () -> null, () -> null, () -> null);
            coordinator.setup();
            lab.set(menuBar.getMenus().get(0));
            assertEquals("Laboratorio", lab.get().getText());
            assertEquals("Inicio rápido", lab.get().getItems().get(0).getText());
            for (javafx.scene.control.MenuItem item : lab.get().getItems()) {
                if (item instanceof Menu profileMenu) {
                    assertEquals("Cargar datos", profileMenu.getItems().get(0).getText());
                    assertEquals("Ejecutar y verificar", profileMenu.getItems().get(1).getText());
                    profileMenu.getItems().get(0).fire();
                }
            }
        });
        assertTrue(routes.contains("Symmetric Keys"));
        assertTrue(routes.contains("EMV Tool"));
        assertTrue(routes.contains("Payments"));
    }

    @Test
    void paletteRefreshesLocalizedCommandTitlesAndLaboratoryMenu() throws Exception {
        AtomicReference<ListView<?>> results = new AtomicReference<>();
        AtomicReference<Menu> lab = new AtomicReference<>();
        runFx(() -> {
            controller.handleOpenCommandPalette();
            results.set(field(controller, "commandResultsListView"));
            MenuBar menuBar = field(controller, "mainMenuBar");
            lab.set(menuBar.getMenus().stream().filter(menu -> "laboratory".equals(menu.getUserData())).findFirst().orElseThrow());
            I18nService.getInstance().setPreference(LanguagePreference.ES);
        });
        assertEquals("Laboratorio", lab.get().getText());
        assertEquals("Inicio rápido", lab.get().getItems().get(0).getText());
        runFx(() -> controller.handleOpenCommandPalette());
        assertTrue(results.get().getItems().stream().map(item -> ((com.cryptocarver.model.CommandItem) item).getTitle()).anyMatch(title -> title.contains("Hash")));
    }

    @Test
    void restrictedVisibilityDoesNotIntroduceSecretsIntoCatalogAndFavoritesRestore() throws Exception {
        SecretVisibilityProfile previous = AppSettings.getInstance().getSecretVisibilityProfile();
        try {
            runFx(() -> {
                AppSettings.getInstance().setSecretVisibilityProfile(SecretVisibilityProfile.REDACTED);
                controller.handleOpenCommandPalette();
                ListView<?> results = field(controller, "commandResultsListView");
                assertTrue(results.getItems().stream().map(Object::toString).noneMatch(item -> item.contains("password-value")));
            });
        } finally {
            runFx(() -> AppSettings.getInstance().setSecretVisibilityProfile(previous));
        }
    }

    private static KeyEvent key(KeyCode code) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false);
    }

    private static void runFx(Runnable action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
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
        if (!latch.await(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX action timed out");
        }
        if (failure.get() != null) {
            throw new RuntimeException(failure.get());
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object instance, String name) {
        try {
            java.lang.reflect.Field field = instance.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(instance);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }
}
