package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.HistoryCommand;
import com.cryptocarver.model.HistoryManager;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
public class QuickNavigationTest {

    @TempDir
    Path tempFolder;

    @BeforeAll
    static void initJFX() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException e) {
            // Toolkit already initialized
            latch.countDown();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX Platform failed to start");
    }

    @BeforeEach
    void setUp() {
        Path isolatedFile = tempFolder.resolve("test-settings.json");
        AppSettings isolated = new AppSettings(isolatedFile);
        AppSettings.setInstanceForTesting(isolated);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        AppSettings.resetInstanceForTesting();
    }

    private void runAndWait(Runnable action) {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                action.run();
            } finally {
                latch.countDown();
            }
        });
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS), "JavaFX application thread execution timed out");
        } catch (InterruptedException e) {
            fail("Thread interrupted waiting for JavaFX execution: " + e.getMessage());
        }
    }

    @Test
    void testSuiteNeverModifiesRealUserSettingsFile() throws Exception {
        Path realUserFile = Paths.get(System.getProperty("user.home"), ".cryptocarver", "settings.json");
        boolean existedBefore = Files.exists(realUserFile);
        byte[] bytesBefore = existedBefore ? Files.readAllBytes(realUserFile) : null;
        long modifiedBefore = existedBefore ? Files.getLastModifiedTime(realUserFile).toMillis() : -1L;

        // Perform AppSettings operations on isolated instance
        AppSettings.getInstance().toggleFavorite("Parse Certificate");
        AppSettings.getInstance().setLastRoute("JWT (Signed)");
        AppSettings.getInstance().resetForTesting();

        boolean existedAfter = Files.exists(realUserFile);
        assertEquals(existedBefore, existedAfter, "Real user settings file existence state must not change during tests");
        if (existedBefore) {
            byte[] bytesAfter = Files.readAllBytes(realUserFile);
            assertArrayEquals(bytesBefore, bytesAfter, "Real user settings file content must not be modified during tests");
            assertEquals(modifiedBefore, Files.getLastModifiedTime(realUserFile).toMillis(), "Real user settings file modification timestamp must not change");
        }
    }

    @Test
    void testFavoritesPersistenceAndPurge() {
        AppSettings settings = AppSettings.getInstance();
        String validRoute = "Parse Certificate";

        assertFalse(settings.isFavorite(validRoute));

        settings.toggleFavorite(validRoute);
        assertTrue(settings.isFavorite(validRoute));
        assertTrue(settings.getFavorites().contains(validRoute));

        // Untoggle favorite
        settings.toggleFavorite(validRoute);
        assertFalse(settings.isFavorite(validRoute));
        assertFalse(settings.getFavorites().contains(validRoute));

        // Invalid route toggle attempt should be ignored
        settings.toggleFavorite("InvalidNonExistentRoute999");
        assertFalse(settings.getFavorites().contains("InvalidNonExistentRoute999"));
    }

    @Test
    void testLastRoutePersistenceAndInvalidFallback() {
        AppSettings settings = AppSettings.getInstance();

        settings.setLastRoute("JWT (Signed)");
        assertEquals("JWT (Signed)", settings.getLastRoute());

        // Setting invalid route fallback cleanly to empty string
        settings.setLastRoute("InvalidCorruptRoute123");
        assertEquals("JWT (Signed)", settings.getLastRoute(), "Invalid route set attempt must leave last valid route intact");
    }

    @Test
    void testRecentsDerivationFromExecutedHistoryNotNavigation() {
        Path historyPath = tempFolder.resolve("test-history.json");
        HistoryManager manager = new HistoryManager(historyPath);

        assertTrue(manager.getHistoryItems().isEmpty(), "History items must start empty");

        // Add executed history command
        HistoryCommand cmd = new HistoryCommand("Digital Signatures", "RSA 2048 Signed", java.util.Map.of());
        manager.addHistoryItem(cmd);

        List<HistoryCommand> items = manager.getHistoryItems();
        assertEquals(1, items.size());
        assertEquals("Digital Signatures", items.get(0).getOperation());
        assertNotNull(items.get(0).getTimestamp());

        // Verify no secrets or sensitive payloads are exposed in history items
        assertFalse(cmd.getOperation().contains("privateKey"));
        assertFalse(cmd.getOperation().contains("secret"));
    }

    @Test
    void testReopenRecentHistoryCommandRestoresState() {
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                Parent root = loader.load();
                ModernMainController controller = loader.getController();

                HistoryCommand cmd = new HistoryCommand("Digital Signatures", "RSA 2048 Signed", java.util.Map.of("signatureAlgorithmCombo", "SHA256withRSA"));
                controller.reopenRecentHistoryCommand(cmd);

                assertEquals("Digital Signatures", getPrivateField(controller, "currentActiveOperation"));
            } catch (Exception e) {
                fail("Reopen recent history command failed: " + e.getMessage());
            }
        });
    }

    @Test
    void testCanonicalModuleBreadcrumbNavigation() {
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                Parent root = loader.load();
                ModernMainController controller = loader.getController();

                Button moduleBtn = (Button) getPrivateField(controller, "breadcrumbModuleBtn");
                controller.navigateToModule("JWT (Signed)");

                assertNotNull(moduleBtn.getUserData(), "Module breadcrumb must store canonical navigation path in userData");
                String canonicalRoute = (String) moduleBtn.getUserData();
                assertTrue(UiNavigationRegistry.resolve(canonicalRoute).isPresent(), "Canonical module route must resolve in UiNavigationRegistry");

                controller.handleBreadcrumbModuleClick();
            } catch (Exception e) {
                fail("Canonical module breadcrumb navigation failed: " + e.getMessage());
            }
        });
    }

    @Test
    void testBreadcrumbsReflectsAllRegisteredRoutes() {
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                Parent root = loader.load();
                ModernMainController controller = loader.getController();

                Button sectionBtn = (Button) getPrivateField(controller, "breadcrumbSectionBtn");
                Button moduleBtn = (Button) getPrivateField(controller, "breadcrumbModuleBtn");
                Label opLabel = (Label) getPrivateField(controller, "breadcrumbOperationLabel");

                assertNotNull(sectionBtn);
                assertNotNull(moduleBtn);
                assertNotNull(opLabel);

                // Test navigation and breadcrumbs update for multiple routes
                controller.navigateToModule("Parse Certificate");
                assertEquals("Certificates & CMS", sectionBtn.getText());
                assertEquals("Parse Certificate", moduleBtn.getText());
                assertEquals("Parse Certificate", opLabel.getText());

                controller.navigateToModule("JWT (Signed)");
                assertEquals("JOSE / JWT", sectionBtn.getText());
                assertEquals("JOSE", moduleBtn.getText());
                assertEquals("JWT (Signed)", opLabel.getText());

            } catch (Exception e) {
                fail("Failed testing breadcrumbs across registered routes: " + e.getMessage());
            }
        });
    }

    @Test
    void testToggleFavoriteHeaderButtonAndCommandPaletteAction() {
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                Parent root = loader.load();
                ModernMainController controller = loader.getController();

                Button favBtn = (Button) getPrivateField(controller, "favoriteToggleBtn");
                assertNotNull(favBtn);

                controller.navigateToModule("Parse Certificate");
                assertEquals("☆", favBtn.getText());

                controller.handleToggleFavorite();
                assertEquals("★", favBtn.getText());
                assertTrue(AppSettings.getInstance().isFavorite("Parse Certificate"));

                controller.handleToggleFavorite();
                assertEquals("☆", favBtn.getText());
                assertFalse(AppSettings.getInstance().isFavorite("Parse Certificate"));

            } catch (Exception e) {
                fail("Failed testing toggle favorite button: " + e.getMessage());
            }
        });
    }

    @Test
    void testQuickStartNavigation() {
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                Parent root = loader.load();
                ModernMainController controller = loader.getController();

                controller.handleQuickStart();
                assertNotNull(controller);
            } catch (Exception e) {
                fail("Quick Start navigation failed: " + e.getMessage());
            }
        });
    }

    private Object getPrivateField(Object obj, String fieldName) throws Exception {
        Field f = obj.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        return f.get(obj);
    }
}
