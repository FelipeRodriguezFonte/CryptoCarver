package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.ClipboardEntry;
import com.cryptocarver.model.ClipboardShelfManager;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.SecretVisibilityProfile;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class Ux28AddToShelfUITest {
    private static final String SYNTHETIC_PKCS8 =
            "-----BEGIN PRIVATE KEY-----\nSYNTHETIC_PKCS8_FIXTURE_ONLY\n-----END PRIVATE KEY-----";
    private static final String PRIVATE_PLACEHOLDER = "*** PRIVATE KEY MATERIAL — NOT RECORDED ***";

    private final ClipboardShelfManager shelf = ClipboardShelfManager.getInstance();

    @BeforeAll
    static void initJavaFx() throws Exception {
        try {
            Platform.startup(() -> Platform.setImplicitExit(false));
        } catch (IllegalStateException alreadyStarted) {
            Platform.setImplicitExit(false);
        }
    }

    @BeforeEach
    void reset() {
        shelf.clear();
        AppSettings.getInstance().setSecretVisibilityProfile(SecretVisibilityProfile.FULL_LAB);
    }

    @Test
    void publicVisibleOutputIsCapturedAndRevealedDespiteShelfFilters() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        AtomicReference<ClipboardShelfController> shelfControllerRef = new AtomicReference<>();

        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                Parent root = loader.load();
                ModernMainController controller = loader.getController();
                controllerRef.set(controller);
                shelfControllerRef.set(field(controller, "clipboardShelfController"));

                TextArea activePublishedArea = new TextArea("PUBLIC_VISIBLE_OUTPUT");
                activePublishedArea.setId("activePublishedOutputArea");
                activePublishedArea.setEditable(false);
                activePublishedArea.getStyleClass().add("text-area");
                ((BorderPane) field(controller, "mainPane")).getChildren().add(activePublishedArea);

                ResultAreaTracker tracker = field(controller, "resultAreaTracker");
                tracker.register(activePublishedArea);
                tracker.focus(activePublishedArea);
                tracker.markUpdated(activePublishedArea);

                // The snapshot has a different real payload. The visible
                // output area is the artifact that Add to Shelf must capture.
                controller.publish(OperationResult.forOperation("Synthetic public output")
                        .output("STALE_GLOBAL_OUTPUT".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                                OperationDetail.Classification.PUBLIC)
                        .detail(new OperationDetail("Algorithm", "SHA-256", OperationDetail.Classification.PUBLIC, false, null))
                        .build());

                OperationResult snapshot = field(controller, "lastPublishedResultSnapshot");
                assertArrayEquals("STALE_GLOBAL_OUTPUT".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        snapshot.getOutput(), "The regression fixture must contain a non-empty global snapshot payload");

                TextField search = field(shelfControllerRef.get(), "searchField");
                search.setText("this-filter-does-not-match");
                controller.handleAddCurrentOutputToShelf();
            } catch (Exception failure) {
                throw new AssertionError(failure);
            }
        });

        ClipboardEntry added = shelf.getEntries().stream()
                .filter(entry -> "PUBLIC_VISIBLE_OUTPUT".equals(entry.getValue()))
                .findFirst().orElse(null);
        assertNotNull(added, "Add to Shelf must capture the active published output, not the summary snapshot");
        assertTrue(shelf.getEntries().stream().noneMatch(entry -> "STALE_GLOBAL_OUTPUT".equals(entry.getValue())),
                "The global snapshot payload must not win over the visible active area");
        assertEquals(OperationDetail.Classification.PUBLIC, added.getClassification());
        assertTrue(shelfControllerRef.get().shelfTable.getSelectionModel().getSelectedItems().contains(added),
                "The integrated Shelf must reveal and select a newly-created entry even with an active filter");
        assertTrue(status(controllerRef.get()).contains("Added public output"));
    }

    @Test
    void publishedSnapshotIsUsedOnlyWhenNoCurrentShelfAreaIsValid() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        AtomicReference<ClipboardShelfController> shelfControllerRef = new AtomicReference<>();

        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                Parent root = loader.load();
                ModernMainController controller = loader.getController();
                controllerRef.set(controller);
                shelfControllerRef.set(field(controller, "clipboardShelfController"));

                ResultAreaTracker tracker = field(controller, "resultAreaTracker");
                tracker.clearSelection();

                controller.publish(OperationResult.forOperation("Synthetic snapshot fallback")
                        .output("SNAPSHOT_FALLBACK_OUTPUT".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                                OperationDetail.Classification.PUBLIC)
                        .build());

                TextField search = field(shelfControllerRef.get(), "searchField");
                search.setText("this-filter-does-not-match");
                controller.handleAddCurrentOutputToShelf();
            } catch (Exception failure) {
                throw new AssertionError(failure);
            }
        });

        ClipboardEntry added = shelf.getEntries().stream()
                .filter(entry -> "SNAPSHOT_FALLBACK_OUTPUT".equals(entry.getValue()))
                .findFirst().orElse(null);
        assertNotNull(added, "A real snapshot payload must be the Shelf fallback when no area is current");
        assertTrue(shelfControllerRef.get().shelfTable.getSelectionModel().getSelectedItems().contains(added),
                "The fallback entry must also be revealed and selected while filters remain active");
        assertTrue(status(controllerRef.get()).contains("Added public output"));
    }

    @Test
    void workbenchSpecificAndGlobalShelfActionsUseDetectedInputAndRevealWithShortcut() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        AtomicReference<ClipboardShelfController> shelfControllerRef = new AtomicReference<>();

        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                ModernMainController controller = loader.getController();
                controllerRef.set(controller);
                shelfControllerRef.set(field(controller, "clipboardShelfController"));

                controller.navigateToModule("Key & Certificate Format Workbench");
                GenericController generic = field(controller, "genericContainerController");
                KeyCertificateWorkbenchController workbench = generic.getKeyCertificateWorkbenchController();

                java.security.KeyPairGenerator generator = java.security.KeyPairGenerator.getInstance("RSA");
                generator.initialize(1024);
                java.security.KeyPair pair = generator.generateKeyPair();
                String publicPem = "-----BEGIN PUBLIC KEY-----\n"
                        + java.util.Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(pair.getPublic().getEncoded())
                        + "\n-----END PUBLIC KEY-----";
                workbench.workbenchInputArea.setText(publicPem);
                invokeWorkbench(workbench, "handleParse");

                shelf.clear();
                controller.handleAddCurrentOutputToShelf();
                ClipboardEntry globalEntry = shelf.getEntries().stream()
                        .filter(entry -> publicPem.equals(entry.getValue()))
                        .findFirst().orElse(null);
                assertNotNull(globalEntry, "Global Add to Shelf must delegate to the visible Workbench input");
                assertEquals(OperationDetail.Classification.PUBLIC, globalEntry.getClassification());

                shelf.clear();
                TextField search = field(shelfControllerRef.get(), "searchField");
                search.setText("filter-does-not-match");
                invokeWorkbench(workbench, "handleSendToShelf");
                ClipboardEntry specificEntry = shelf.getEntries().stream()
                        .filter(entry -> publicPem.equals(entry.getValue()))
                        .findFirst().orElse(null);
                assertNotNull(specificEntry, "The Workbench button must use the same detected input resolution");
                assertEquals(globalEntry.getValue(), specificEntry.getValue());
                assertTrue(status(controller).contains("Added public key material"));

                controller.handleOpenClipboardShelf();
                assertTrue(shelfControllerRef.get().shelfTable.getSelectionModel()
                                .getSelectedItems().contains(specificEntry),
                        "Shortcut+Shift+V route must reveal and select the new entry despite the active filter");
            } catch (Exception failure) {
                throw new AssertionError(failure);
            }
        });

    }

    @Test
    void explicitPrivateAreaCreatesSessionOnlyKeyAndRestrictedProfilesBlockIt() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();

        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                ModernMainController controller = loader.getController();
                controllerRef.set(controller);

                TextArea privateArea = new TextArea(SYNTHETIC_PKCS8);
                privateArea.setId("syntheticPrivateKeyArea");
                privateArea.setEditable(false);
                privateArea.getStyleClass().add("text-area");
                ((BorderPane) field(controller, "mainPane")).getChildren().add(privateArea);
                ResultAreaTracker tracker = field(controller, "resultAreaTracker");
                tracker.register(privateArea);
                tracker.focus(privateArea);
                tracker.markUpdated(privateArea);

                controller.publish(OperationResult.forOperation("Synthetic private export")
                        .output(PRIVATE_PLACEHOLDER.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                                OperationDetail.Classification.SECRET)
                        .build());

                AppSettings.getInstance().setSecretVisibilityProfile(SecretVisibilityProfile.FULL_LAB);
                controller.handleAddCurrentOutputToShelf();
                ClipboardEntry session = shelf.getEntries().stream()
                        .filter(ClipboardEntry::isSessionOnlyPrivateKey)
                        .findFirst().orElse(null);
                assertNotNull(session);
                assertEquals(SYNTHETIC_PKCS8, session.getValue());
                assertEquals(ClipboardEntry.EntryKind.SESSION_ONLY_PRIVATE_KEY, session.getEntryKind());

                for (SecretVisibilityProfile restricted : new SecretVisibilityProfile[] {
                        SecretVisibilityProfile.MASKED, SecretVisibilityProfile.REDACTED}) {
                    shelf.clear();
                    AppSettings.getInstance().setSecretVisibilityProfile(restricted);
                    controller.handleAddCurrentOutputToShelf();
                    assertTrue(shelf.getEntries().stream().noneMatch(ClipboardEntry::isSessionOnlyPrivateKey),
                            restricted + " must block creation of a temporary private-key entry");
                    assertTrue(status(controller).contains("output hidden by visibility policy"));
                }

                // A complete private-key PEM shown in a generic (non-"…PrivateKeyArea")
                // result area still gets the session-only protection: the check is on
                // content shape, not just the source area's id, so a private key cannot
                // dodge the never-persisted rule by appearing somewhere else.
                shelf.clear();
                AppSettings.getInstance().setSecretVisibilityProfile(SecretVisibilityProfile.FULL_LAB);
                TextArea genericArea = new TextArea(SYNTHETIC_PKCS8);
                genericArea.setId("genericOutputArea");
                genericArea.setEditable(false);
                genericArea.getStyleClass().add("text-area");
                ((BorderPane) field(controller, "mainPane")).getChildren().add(genericArea);
                tracker.register(genericArea);
                tracker.focus(genericArea);
                tracker.markUpdated(genericArea);
                controller.handleAddCurrentOutputToShelf();
                ClipboardEntry genericSession = shelf.getEntries().stream()
                        .filter(ClipboardEntry::isSessionOnlyPrivateKey)
                        .findFirst().orElse(null);
                assertNotNull(genericSession, "Complete private-key PEM must be protected regardless of area id");
                assertEquals(SYNTHETIC_PKCS8, genericSession.getValue());
            } catch (Exception failure) {
                throw new AssertionError(failure);
            }
        });
    }

    /**
     * Regression for a reported bug: a KDF result (classified SECRET purely because
     * its result area id contains "kdf", not because it is a private key) was silently
     * dropped by Add to Shelf with no way to recover it — the old code hard-blocked
     * every SECRET-classified area that wasn't an explicit complete private-key area.
     * Non-private SECRET material must now reach the Shelf like everything else,
     * persisted and gated only by classification + the visibility profile.
     */
    @Test
    void secretNonPrivateKeyOutputLikeKdfIsCapturedAsAPersistentEntry() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();

        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                ModernMainController controller = loader.getController();
                controllerRef.set(controller);

                TextArea kdfLikeArea = new TextArea("A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4");
                kdfLikeArea.setId("syntheticKdfResultArea");
                kdfLikeArea.setEditable(false);
                kdfLikeArea.getStyleClass().add("text-area");
                ((BorderPane) field(controller, "mainPane")).getChildren().add(kdfLikeArea);
                ResultAreaTracker tracker = field(controller, "resultAreaTracker");
                tracker.register(kdfLikeArea);
                tracker.focus(kdfLikeArea);
                tracker.markUpdated(kdfLikeArea);

                controller.handleAddCurrentOutputToShelf();
            } catch (Exception failure) {
                throw new AssertionError(failure);
            }
        });

        ClipboardEntry added = shelf.getEntries().stream()
                .filter(entry -> "A1B2C3D4E5F6A1B2C3D4E5F6A1B2C3D4".equals(entry.getValue()))
                .findFirst().orElse(null);
        assertNotNull(added, "A KDF-derived (SECRET, non-private-key) result must reach the Clipboard Shelf");
        assertEquals(OperationDetail.Classification.SECRET, added.getClassification());
        assertFalse(added.isSessionOnlyPrivateKey(), "Non-private-key SECRET results use the normal persistent entry path");
        assertTrue(status(controllerRef.get()).contains("Added public output"));
    }

    private static String status(ModernMainController controller) throws Exception {
        javafx.scene.control.Label status = field(controller, "statusLabel");
        return status.getText();
    }

    private static void invokeWorkbench(KeyCertificateWorkbenchController controller, String methodName)
            throws Exception {
        java.lang.reflect.Method method = KeyCertificateWorkbenchController.class
                .getDeclaredMethod(methodName, javafx.event.ActionEvent.class);
        method.setAccessible(true);
        method.invoke(controller, (javafx.event.ActionEvent) null);
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object target, String name) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return (T) field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void runAndWait(Runnable action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(15, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
}
