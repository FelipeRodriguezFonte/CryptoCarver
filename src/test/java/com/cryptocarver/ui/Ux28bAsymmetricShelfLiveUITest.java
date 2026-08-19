package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.ClipboardEntry;
import com.cryptocarver.model.ClipboardShelfManager;
import com.cryptocarver.model.GeneratedAsymmetricKeySummary;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real main-view-modern.fxml regression for UX-28B. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class Ux28bAsymmetricShelfLiveUITest {
    @TempDir
    static Path temporaryDirectory;

    private static boolean toolkitStarted;
    private Stage stage;
    private ModernMainController mainController;
    private KeysController keysController;
    private ClipboardShelfManager shelf;

    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                ready.countDown();
            });
        } catch (IllegalStateException alreadyStarted) {
            ready.countDown();
        }
        assertTrue(ready.await(15, TimeUnit.SECONDS), "JavaFX toolkit did not start");
        toolkitStarted = true;
    }

    @BeforeEach
    void setUp() throws Exception {
        AppSettings.setInstanceForTesting(new AppSettings(temporaryDirectory.resolve("settings.json")));
        AppSettings.getInstance().setSecretVisibilityProfile(SecretVisibilityProfile.FULL_LAB);
        I18nService.getInstance().setPreference(LanguagePreference.EN);
        shelf = ClipboardShelfManager.getInstance();
        shelf.clear();

        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                Parent root = loader.load();
                mainController = loader.getController();
                keysController = mainController.getKeysController();
                stage = new Stage();
                stage.setScene(new Scene(root, 1600, 1000));
                stage.show();
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        });
        assertNotNull(mainController);
        assertNotNull(keysController);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (stage != null) runAndWait(() -> stage.close());
        if (shelf != null) shelf.clear();
        AppSettings.resetInstanceForTesting();
    }

    @AfterAll
    static void keepToolkitAliveForOtherUITests() {
        if (toolkitStarted) Platform.setImplicitExit(false);
    }

    @Test
    void generateAddOpenShelfUsesCanonicalPemAndProtectsPrivateMaterial() throws Exception {
        Path shelfPath = readField(shelf, "shelfPath");

        runAndWait(() -> mainController.navigateToModule("RSA Key Generation"));
        Button rsaGenerate = readField(keysController, "rsaGenerateBtn");
        runAndWait(rsaGenerate::fire);
        awaitFx(() -> readFieldUnchecked(keysController, "currentRsaSummary") != null, 30_000);

        GeneratedAsymmetricKeySummary rsa = readField(keysController, "currentRsaSummary");
        TabPane rsaTabs = readField(keysController, "rsaKeyMaterialTabs");
        Button rsaPublicSend = readField(keysController, "rsaSendShelfBtn");
        Button rsaPrivateSend = readField(keysController, "rsaSendPrivateShelfBtn");
        Button globalShelf = readField(mainController, "toolbarShelfButton");
        MenuItem shelfShortcut = readField(mainController, "clipboardShelfMenuItem");
        TextField shelfSearch = readField(readField(mainController, "clipboardShelfController"), "searchField");

        runAndWait(() -> {
            rsaTabs.getSelectionModel().select(0);
            shelfSearch.setText("filter-does-not-match");
            globalShelf.fire();
        });
        assertEquals("Added RSA public key to Clipboard Shelf.", readStatus());

        ClipboardEntry rsaPublic = onlyEntryWithValue(rsa.getPublicKeyPem());
        assertEquals(ClipboardEntry.Format.PEM, rsaPublic.getFormat());
        assertEquals("PUBLIC", rsaPublic.getClassification().name());
        assertEquals("Key Generation", rsaPublic.getSourceOperation());
        assertEquals(rsa.getAlgorithm(), rsaPublic.getAlgorithm());
        assertEquals(rsa.getPublicKeyPem(), rsaPublic.getValue());
        assertFalse(rsaPublic.getValue().contains("=== RSA"));
        assertFalse(rsaPublic.getValue().contains("Modulus"));
        assertTrue(!rsaPublic.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(1)));

        assertEquals(javafx.scene.input.KeyCombination.valueOf("Shortcut+Shift+V"),
                shelfShortcut.getAccelerator());
        runAndWait(shelfShortcut::fire);
        TableView<ClipboardEntry> shelfTable = readField(readField(mainController, "clipboardShelfController"), "shelfTable");
        assertTrue(shelfTable.getItems().contains(rsaPublic), "New public PEM must be revealed despite the active filter");
        assertEquals(rsaPublic, shelfTable.getSelectionModel().getSelectedItem());

        runAndWait(() -> mainController.navigateToModule("RSA Key Generation"));
        runAndWait(() -> {
            rsaTabs.getSelectionModel().select(1);
            globalShelf.fire();
        });
        assertEquals("Added RSA private key to Clipboard Shelf (session only).", readStatus());
        ClipboardEntry rsaPrivate = onlySessionEntryWithValue(rsa.getPrivateKeyPem());
        assertEquals(ClipboardEntry.EntryKind.SESSION_ONLY_PRIVATE_KEY, rsaPrivate.getEntryKind());
        assertEquals(ClipboardEntry.Format.PEM, rsaPrivate.getFormat());
        assertEquals("Key Generation", rsaPrivate.getSourceOperation());
        assertEquals(rsa.getPrivateKeyPem(), rsaPrivate.getValue());
        String persistedAfterPublic = Files.readString(shelfPath);
        assertFalse(persistedAfterPublic.contains(rsa.getPrivateKeyPem()), "Private PEM must never be written to shelf.json");
        shelf.removeEntry(rsaPrivate.getId());
        assertEquals(0, countEntriesWithValue(rsa.getPrivateKeyPem()));

        runAndWait(() -> {
            AppSettings.getInstance().setSecretVisibilityProfile(SecretVisibilityProfile.MASKED);
            keysController.updateVisibilityControls();
            assertTrue(rsaPrivateSend.isDisable());
            globalShelf.fire();
        });
        assertEquals("Action blocked: private key material requires FULL_LAB.", readStatus());
        assertEquals(0, countEntriesWithValue(rsa.getPrivateKeyPem()));

        runAndWait(() -> {
            AppSettings.getInstance().setSecretVisibilityProfile(SecretVisibilityProfile.REDACTED);
            keysController.updateVisibilityControls();
            assertTrue(rsaPrivateSend.isDisable());
            globalShelf.fire();
        });
        assertEquals("Action blocked: private key material requires FULL_LAB.", readStatus());
        assertEquals(0, countEntriesWithValue(rsa.getPrivateKeyPem()));

        runAndWait(() -> {
            AppSettings.getInstance().setSecretVisibilityProfile(SecretVisibilityProfile.FULL_LAB);
            keysController.updateVisibilityControls();
            rsaTabs.getSelectionModel().select(0);
            rsaPublicSend.fire();
        });
        assertEquals("Added RSA public key to Clipboard Shelf.", readStatus());
        assertEquals(2, countEntriesWithValue(rsa.getPublicKeyPem()), "The explicit public route must remain available");

        runAndWait(() -> mainController.navigateToModule("ECDSA Key Generation"));
        Button ecdsaGenerate = findButton(readField(mainController, "keysContainer"), "Generate ECDSA Key Pair");
        assertNotNull(ecdsaGenerate);
        runAndWait(ecdsaGenerate::fire);
        GeneratedAsymmetricKeySummary ecdsa = readField(keysController, "currentEcdsaSummary");
        assertNotNull(ecdsa, "ECDSA regression must generate a summary");
        TabPane ecdsaTabs = readField(keysController, "ecdsaKeyMaterialTabs");
        runAndWait(() -> {
            ecdsaTabs.getSelectionModel().select(0);
            globalShelf.fire();
        });
        assertEquals("Added ECDSA public key to Clipboard Shelf.", readStatus());
        ClipboardEntry ecdsaPublic = onlyEntryWithValue(ecdsa.getPublicKeyPem());
        assertEquals(ClipboardEntry.Format.PEM, ecdsaPublic.getFormat());
        assertEquals("Key Generation", ecdsaPublic.getSourceOperation());
        assertEquals(ecdsa.getPublicKeyPem(), ecdsaPublic.getValue());
        assertFalse(ecdsaPublic.getValue().contains("=== ECDSA"));
    }

    private String readStatus() throws Exception {
        return runAndGet(() -> ((javafx.scene.control.Label) readFieldUnchecked(mainController, "statusLabel")).getText());
    }

    private ClipboardEntry onlyEntryWithValue(String value) {
        return shelf.getEntries().stream()
                .filter(entry -> value.equals(entry.getValue()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No Shelf entry for canonical PEM"));
    }

    private ClipboardEntry onlySessionEntryWithValue(String value) {
        return shelf.getEntries().stream()
                .filter(ClipboardEntry::isSessionOnlyPrivateKey)
                .filter(entry -> value.equals(entry.getValue()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No session-only private Shelf entry for canonical PEM"));
    }

    private long countEntriesWithValue(String value) {
        return shelf.getEntries().stream().filter(entry -> value.equals(entry.getValue())).count();
    }

    private static Button findButton(Node root, String text) {
        if (root instanceof Button button && text.equals(button.getText())) return button;
        if (root instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Button found = findButton(child, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void awaitFx(BooleanSupplier condition, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (runAndGet(condition::getAsBoolean)) return;
            Thread.sleep(50);
        }
        assertTrue(runAndGet(condition::getAsBoolean), "Timed out waiting for JavaFX condition");
    }

    private static void runAndWait(Runnable action) throws Exception {
        runAndGet(() -> {
            action.run();
            return true;
        });
    }

    private static <T> T runAndGet(java.util.function.Supplier<T> action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                value.set(action.get());
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(40, TimeUnit.SECONDS), "JavaFX action timed out");
        if (failure.get() != null) throw new AssertionError(failure.get());
        return value.get();
    }

    @SuppressWarnings("unchecked")
    private static <T> T readField(Object owner, String name) throws Exception {
        return (T) readFieldUnchecked(owner, name);
    }

    private static Object readFieldUnchecked(Object owner, String name) {
        Class<?> type = owner.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(owner);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException error) {
                throw new AssertionError(error);
            }
        }
        throw new AssertionError("Missing field " + name);
    }
}
