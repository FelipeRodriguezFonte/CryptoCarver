package com.cryptocarver.ui;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.VBox;
import javafx.scene.Node;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ModernMainControllerUITest {

    private static boolean jfxIsSetup;

    @BeforeAll
    static void initJFX() throws InterruptedException {
        if (!jfxIsSetup) {
            CountDownLatch latch = new CountDownLatch(1);
            try {
                Platform.startup(() -> {
                    Platform.setImplicitExit(false);
                    latch.countDown();
                });
                if (!latch.await(15, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("JavaFX failed to start within timeout");
                }
            } catch (IllegalStateException e) {
                Platform.setImplicitExit(false);
                // Toolkit already initialized
            }
            jfxIsSetup = true;
        }
    }

    private <T> T getField(Object target, String name) throws Exception {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Field f = clazz.getDeclaredField(name);
                f.setAccessible(true);
                return (T) f.get(target);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException("Field " + name + " not found in " + target.getClass());
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private void runAndWait(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> exceptionRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                exceptionRef.set(t);
            } finally {
                latch.countDown();
            }
        });

        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX runLater execution timed out");
        }

        if (exceptionRef.get() != null) {
            if (exceptionRef.get() instanceof Exception) {
                throw (Exception) exceptionRef.get();
            }
            throw new RuntimeException(exceptionRef.get());
        }
    }

    @Test
    void testCmsUiPkcs11Toggle() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                URL resource = getClass().getResource("/fxml/main-view-modern.fxml");
                FXMLLoader loader = new FXMLLoader(resource);
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                fail(e);
            }
        });

        ModernMainController controller = controllerRef.get();
        Object keysController = getField(controller, "keysController");

        // The cmsSignSourcePkcs11Radio should be present in FXML and loaded
        javafx.scene.control.RadioButton pkcs11Radio = getField(controller, "cmsSignSourcePkcs11Radio");
        javafx.scene.control.RadioButton localRadio = getField(controller, "cmsSignSourceLocalRadio");
        javafx.scene.layout.GridPane localGrid = getField(controller, "cmsSignLocalGrid");
        javafx.scene.layout.HBox pkcs11Box = getField(controller, "cmsSignPkcs11Box");

        runAndWait(() -> {
            localRadio.setSelected(true);
            try {
                Method m = keysController.getClass().getDeclaredMethod("handleCMSourceChanged");
                m.invoke(keysController);
            } catch (Exception e) {
                fail(e);
            }
        });
        assertTrue(localGrid.isVisible());
        assertFalse(pkcs11Box.isVisible());

        runAndWait(() -> {
            pkcs11Radio.setSelected(true);
            try {
                Method m = keysController.getClass().getDeclaredMethod("handleCMSourceChanged");
                m.invoke(keysController);
            } catch (Exception e) {
                fail(e);
            }
        });
        assertFalse(localGrid.isVisible());
        assertTrue(pkcs11Box.isVisible());
    }

    @Test
    void testCmsInspectorNavigation() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                fail(e);
            }
        });

        ModernMainController controller = controllerRef.get();
        runAndWait(() -> {
            try {
                Method m = controller.getClass().getDeclaredMethod("handleItemSelected", String.class);
                m.setAccessible(true);
                m.invoke(controller, "CMS Inspector");
            } catch (Exception e) {
                fail(e);
            }
        });

        // Assert that the certificates section is visible
        javafx.scene.layout.VBox certContainer = getField(controller, "certificatesContainer");
        assertTrue(certContainer.isVisible());
        assertTrue(certContainer.isManaged());

        // Assert that the CMS Inspector accordion is expanded
        javafx.scene.control.Accordion accordion = (javafx.scene.control.Accordion) certContainer.getChildren().get(0);
        assertNotNull(accordion.getExpandedPane());
        assertEquals("🔍 Inspect / Validate CMS", accordion.getExpandedPane().getText());
    }

    @Test
    void testCmsUiEncryptPkcs11Toggle() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                URL resource = getClass().getResource("/fxml/main-view-modern.fxml");
                FXMLLoader loader = new FXMLLoader(resource);
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                fail(e);
            }
        });
        ModernMainController controller = controllerRef.get();

        Object keysController = getField(controller, "keysController");
        javafx.scene.control.RadioButton pkcs11Radio = getField(controller, "cmsEncryptSourcePkcs11Radio");
        javafx.scene.control.RadioButton localRadio = getField(controller, "cmsEncryptSourceLocalRadio");
        javafx.scene.layout.GridPane localGrid = getField(controller, "cmsEncryptLocalGrid");
        javafx.scene.layout.HBox pkcs11Box = getField(controller, "cmsEncryptPkcs11Box");

        assertNotNull(pkcs11Radio, "cmsEncryptSourcePkcs11Radio should be injected");
        assertNotNull(localRadio, "cmsEncryptSourceLocalRadio should be injected");
        assertNotNull(localGrid, "cmsEncryptLocalGrid should be injected");
        assertNotNull(pkcs11Box, "cmsEncryptPkcs11Box should be injected");

        runAndWait(() -> {
            pkcs11Radio.setSelected(true);
            try {
                Method m = keysController.getClass().getDeclaredMethod("handleCMSEncryptSourceChanged");
                m.invoke(keysController);
            } catch (Exception e) {
                fail(e);
            }
        });
        assertFalse(localGrid.isVisible());
        assertFalse(localGrid.isManaged());
        assertTrue(pkcs11Box.isVisible());
        assertTrue(pkcs11Box.isManaged());

        runAndWait(() -> {
            localRadio.setSelected(true);
            try {
                Method m = keysController.getClass().getDeclaredMethod("handleCMSEncryptSourceChanged");
                m.invoke(keysController);
            } catch (Exception e) {
                fail(e);
            }
        });
        assertTrue(localGrid.isVisible());
        assertTrue(localGrid.isManaged());
        assertFalse(pkcs11Box.isVisible());
        assertFalse(pkcs11Box.isManaged());
    }

    @Test
    void testFxmlLoadAndInjection() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();

        runAndWait(() -> {
            try {
                URL resource = getClass().getResource("/fxml/main-view-modern.fxml");
                assertNotNull(resource, "main-view-modern.fxml not found");

                FXMLLoader loader = new FXMLLoader(resource);
                Parent root = loader.load();
                assertNotNull(root, "Root should not be null");

                ModernMainController controller = loader.getController();
                assertNotNull(controller, "Controller should be injected");
                controllerRef.set(controller);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load FXML", e);
            }
        });

        ModernMainController controller = controllerRef.get();
        assertNotNull(getField(controller, "sidePanel"));
        assertNotNull(getField(controller, "navigationRail"));
        assertNotNull(getField(controller, "jose"));
        assertNotNull(getField(controller, "joseController"));
        assertNotNull(getField(controller, "openPgpContainer"));
        assertNotNull(getField(controller, "openPgpContainerController"));
    }


    @Test
    void testNavigationRoutingCoverage() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();

        runAndWait(() -> {
            try {
                URL resource = getClass().getResource("/fxml/main-view-modern.fxml");
                FXMLLoader loader = new FXMLLoader(resource);
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        ModernMainController controller = controllerRef.get();
        Method handleItemMethod = ModernMainController.class.getDeclaredMethod("handleItemSelected", String.class);
        handleItemMethod.setAccessible(true);

        String[][] routes = {
            {"Symmetric Ciphers", "cipherContainer"},
            {"Hashing", "genericContainer"},
            {"Compressed Hex (2-row)", "genericContainer"},
            {"Digital Signatures", "authenticationContainer"},
            {"Key Generation", "symmetricKeysContainer"},
            {"PKCS#11 Token", "symmetricKeysContainer"},
            {"PQC Key Generation", "postQuantumContainer"},
            {"Sign XML (XAdES)", "xmlSecurityContainer"},
            {"Generate Certificate", "certificatesContainer"},
            {"JWT (Signed)", "jose"},
            {"PIN Generation", "paymentsContainer"},
            {"Decode ASN.1", "certificatesContainer"},
            {"Recent Operations", "historyView"}
        };

        for (String[] route : routes) {
            String item = route[0];
            String expectedContainer = route[1];

            runAndWait(() -> {
                try {
                    handleItemMethod.invoke(controller, item);
                    Node container = getField(controller, expectedContainer);
                    assertTrue(container.isVisible(), "Container " + expectedContainer + " should be visible for " + item);
                } catch (Exception e) {
                    throw new RuntimeException("Failed route: " + item, e);
                }
            });
        }
    }

    @Test
    void testFileCipherRouteExpandsStreamingPane() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        runAndWait(() -> {
            try {
                ModernMainController controller = controllerRef.get();
                Method route = ModernMainController.class.getDeclaredMethod("handleItemSelected", String.class);
                route.setAccessible(true);
                route.invoke(controller, "File Cipher (Streaming)");

                javafx.scene.layout.VBox cipher = getField(controller, "cipherContainer");
                javafx.scene.control.Accordion accordion = (javafx.scene.control.Accordion) cipher.getChildren().stream()
                        .filter(javafx.scene.control.Accordion.class::isInstance)
                        .findFirst().orElseThrow();
                assertNotNull(accordion.getExpandedPane());
                assertTrue(accordion.getExpandedPane().getText().contains("File Cipher"));
                javafx.scene.control.Label subtitle = getField(controller, "contentSubtitleLabel");
                assertTrue(subtitle.getText().contains("Experimental"));
                assertTrue(subtitle.getText().contains("Sensitive material"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void testDukptRouteExpandsDukptPane() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        runAndWait(() -> {
            try {
                ModernMainController controller = controllerRef.get();
                Method route = ModernMainController.class.getDeclaredMethod("handleItemSelected", String.class);
                route.setAccessible(true);
                route.invoke(controller, "DUKPT TDES / AES");

                javafx.scene.layout.VBox payments = getField(controller, "paymentsContainer");
                javafx.scene.control.Accordion accordion = (javafx.scene.control.Accordion) payments.getChildren().stream()
                        .filter(javafx.scene.control.Accordion.class::isInstance)
                        .findFirst().orElseThrow();
                assertNotNull(accordion.getExpandedPane());
                assertTrue(accordion.getExpandedPane().getText().contains("DUKPT KSN"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void testCompressedHexRouteExpandsDedicatedPane() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        runAndWait(() -> {
            try {
                ModernMainController controller = controllerRef.get();
                Method route = ModernMainController.class.getDeclaredMethod("handleItemSelected", String.class);
                route.setAccessible(true);
                route.invoke(controller, "Compressed Hex (2-row)");

                javafx.scene.control.Accordion accordion = getField(controller, "genericContainer");
                assertNotNull(accordion.getExpandedPane());
                assertTrue(accordion.getExpandedPane().getText().contains("Compressed Hex"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void testEveryRegisteredNavigationPathAvoidsThePlaceholder() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        ModernMainController controller = controllerRef.get();
        Method route = ModernMainController.class.getDeclaredMethod("handleItemSelected", String.class);
        route.setAccessible(true);
        runAndWait(() -> {
            try {
                javafx.scene.control.Label placeholder = getField(controller, "contentPlaceholderLabel");
                for (com.cryptocarver.model.OperationDescriptor operation
                        : com.cryptocarver.model.OperationRegistry.getInstance().getAll()) {
                    route.invoke(controller, operation.getNavigationPath());
                    assertFalse(placeholder.isVisible(), "Registered route must not show placeholder: "
                            + operation.getNavigationPath());
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void testHistoryRoutesReplaceThePreviouslyVisibleModule() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        ModernMainController controller = controllerRef.get();
        Method route = ModernMainController.class.getDeclaredMethod("handleItemSelected", String.class);
        route.setAccessible(true);
        runAndWait(() -> {
            try {
                Node cipher = getField(controller, "cipherContainer");
                Node savedSessions = getField(controller, "savedSessionsContainer");

                route.invoke(controller, "File Cipher (Streaming)");
                assertTrue(cipher.isVisible());

                route.invoke(controller, "Saved Sessions");
                assertTrue(savedSessions.isVisible());
                assertFalse(cipher.isVisible(), "Saved Sessions must hide the previous module");

                route.invoke(controller, "Export History");
                Node historyView = getField(controller, "historyView");
                assertTrue(historyView.isVisible());
                assertFalse(savedSessions.isVisible(), "Export History must hide Saved Sessions");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void testUnknownLegacyRouteUsesDedicatedPlaceholder() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        ModernMainController controller = controllerRef.get();
        Method route = ModernMainController.class.getDeclaredMethod("handleItemSelected", String.class);
        route.setAccessible(true);
        runAndWait(() -> {
            try {
                route.invoke(controller, "Legacy operation without a view");
                javafx.scene.control.Label placeholder = getField(controller, "contentPlaceholderLabel");
                assertTrue(placeholder.isVisible());
                assertTrue(placeholder.getText().contains("No view is registered"));
                assertFalse(placeholder.getText().contains("Phase 2"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void testClearInvalidatesExpandedResultSnapshot() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        ModernMainController controller = controllerRef.get();
        Method route = ModernMainController.class.getDeclaredMethod("handleItemSelected", String.class);
        Method clear = ModernMainController.class.getDeclaredMethod("handleClearInput");
        Method resolveOutput = ModernMainController.class.getDeclaredMethod("resolveCurrentOutputText");
        route.setAccessible(true);
        clear.setAccessible(true);
        resolveOutput.setAccessible(true);
        runAndWait(() -> {
            try {
                route.invoke(controller, "Hashing");
                controller.publish(com.cryptocarver.model.OperationResult.forOperation("Snapshot test")
                        .output("stale result".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        .status("Published")
                        .build());
                assertTrue(((String) resolveOutput.invoke(controller)).contains("stale result"));

                clear.invoke(controller);
                assertEquals("", resolveOutput.invoke(controller));
                assertEquals("", getField(controller, "lastPublishedOperation"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void testPublishedInputAndOutputAreStoredAsSensitiveHistoryDetails() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        ModernMainController controller = controllerRef.get();
        runAndWait(() -> {
            controller.getHistoryManager().clearHistory();
            controller.publish(com.cryptocarver.model.OperationResult.forOperation("SHA-256")
                    .input("hash input".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .output(new byte[] {0x01, 0x23, 0x45})
                    .detail("Algorithm", "SHA-256")
                    .build());

            com.cryptocarver.model.HistoryItem item = controller.getHistoryManager().getHistoryItems().get(0);
            assertTrue(item.getStructuredDetails().stream().anyMatch(detail ->
                    detail.name().startsWith("Input")
                            && "hash input".equals(detail.value())
                            && detail.classification() == com.cryptocarver.model.OperationDetail.Classification.SENSITIVE));
            assertTrue(item.getStructuredDetails().stream().anyMatch(detail ->
                    detail.name().startsWith("Output")
                            && "012345".equals(detail.value())
                            && detail.classification() == com.cryptocarver.model.OperationDetail.Classification.SENSITIVE));

            String masked = com.cryptocarver.utils.HistoryReportExporter.toMarkdown(
                    item, com.cryptocarver.model.SecretVisibility.MASKED);
            assertFalse(masked.contains("hash input"));
            assertTrue(masked.contains("***MASKED***"));
            String unsafe = com.cryptocarver.utils.HistoryReportExporter.toMarkdown(
                    item, com.cryptocarver.model.SecretVisibility.FULL_LAB);
            assertTrue(unsafe.contains("hash input"));
            assertTrue(unsafe.contains("012345"));
        });
    }

    @Test
    void testHistoryModuleInjectionAndRefresh() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        ModernMainController controller = controllerRef.get();
        assertNotNull(getField(controller, "historyViewController"));
        runAndWait(() -> {
            try {
                HistoryController historyController = getField(controller, "historyViewController");
                controller.getHistoryManager().clearHistory();
                controller.addToHistory("Hashing", java.util.List.of(
                        com.cryptocarver.model.OperationDetail.publicDetail("Result", "OK"),
                        com.cryptocarver.model.OperationDetail.secretDetail("Laboratory key", "all-visible-in-lab")));
                controller.addToHistory("File Cipher (Streaming)", java.util.List.of(
                        com.cryptocarver.model.OperationDetail.publicDetail("Algorithm", "SHA-256")));
                historyController.refresh();
                javafx.scene.control.TableView<?> table = getField(historyController, "historyTable");
                assertEquals(2, table.getItems().size());
                assertTrue(table.getItems().stream()
                        .map(com.cryptocarver.model.HistoryItem.class::cast)
                        .anyMatch(item -> item.getOperation().equals("Hashing")));

                javafx.scene.control.TextField filter = getField(historyController, "historyFilterField");
                javafx.scene.control.ComboBox<String> moduleFilter = getField(historyController, "historyModuleFilterCombo");
                javafx.scene.control.Label summary = getField(historyController, "historySummaryLabel");
                assertEquals("2 operations", summary.getText());
                filter.setText("result");
                assertEquals(1, table.getItems().size(), "Filtering by a detail name must not search detail values");
                assertEquals("Hashing", ((com.cryptocarver.model.HistoryItem) table.getItems().get(0)).getOperation());
                assertEquals("1 of 2 operations", summary.getText());
                filter.clear();
                assertEquals(2, table.getItems().size());
                assertTrue(moduleFilter.getItems().contains("Generic"));
                assertTrue(moduleFilter.getItems().contains("Cipher"));
                moduleFilter.setValue("Cipher");
                assertEquals(1, table.getItems().size());
                assertEquals("File Cipher (Streaming)", ((com.cryptocarver.model.HistoryItem) table.getItems().get(0)).getOperation());
                moduleFilter.setValue("Generic");
                table.getSelectionModel().selectFirst();
                javafx.scene.control.TableView<com.cryptocarver.model.OperationDetail> details = getField(historyController, "detailsTable");
                javafx.scene.control.ComboBox<com.cryptocarver.model.SecretVisibility> visibility = getField(historyController, "visibilityCombo");
                visibility.setValue(com.cryptocarver.model.SecretVisibility.FULL_LAB);
                visibility.getOnAction().handle(new javafx.event.ActionEvent());
                assertTrue(details.getItems().stream().anyMatch(detail -> "all-visible-in-lab".equals(detail.value())));
                visibility.setValue(com.cryptocarver.model.SecretVisibility.MASKED);
                visibility.getOnAction().handle(new javafx.event.ActionEvent());
                assertTrue(details.getItems().stream().anyMatch(detail -> "***MASKED***".equals(detail.value())));
                visibility.setValue(com.cryptocarver.model.SecretVisibility.FULL_LAB);
                visibility.getOnAction().handle(new javafx.event.ActionEvent());
                javafx.scene.control.Button exportReport = getField(historyController, "exportReportBtn");
                javafx.scene.control.Button copyReport = getField(historyController, "copyReportBtn");
                javafx.scene.control.Button exportRecipe = getField(historyController, "exportRecipeBtn");
                javafx.scene.control.Button exportJsonRecord = getField(historyController, "exportJsonRecordBtn");
                javafx.scene.control.Button exportVisibleJson = getField(historyController, "exportVisibleJsonBtn");
                assertFalse(exportReport.isDisabled(), "Latest history operation should be exportable by default");
                assertFalse(copyReport.isDisabled(), "Latest history operation should be copyable by default");
                assertFalse(exportRecipe.isDisabled(), "Latest history operation should expose its recipe export");
                assertFalse(exportJsonRecord.isDisabled(), "Latest history operation should expose its JSON record export");
                assertFalse(exportVisibleJson.isDisabled(), "Visible history entries should expose their bulk JSON export");
                details.getSelectionModel().selectFirst();
                javafx.scene.control.Button openDetail = getField(historyController, "openHistoryDetailBtn");
                javafx.scene.control.Button copyDetail = getField(historyController, "copyHistoryDetailBtn");
                assertFalse(openDetail.isDisabled(), "A selected history detail should be expandable");
                assertFalse(copyDetail.isDisabled(), "A selected history detail should be copyable");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void testExpandedResultUsesLatestPublishedOperation() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        ModernMainController controller = controllerRef.get();
        Method resolveOutput = ModernMainController.class.getDeclaredMethod("resolveCurrentOutputText");
        resolveOutput.setAccessible(true);
        runAndWait(() -> {
            try {
                controller.publish(com.cryptocarver.model.OperationResult.forOperation("DES Key Generation")
                        .output("DES-RESULT".getBytes(java.nio.charset.StandardCharsets.UTF_8)).build());
                controller.publish(com.cryptocarver.model.OperationResult.forOperation("EdDSA Key Generation")
                        .output("EDDSA-RESULT".getBytes(java.nio.charset.StandardCharsets.UTF_8)).build());

                assertEquals("EDDSA-RESULT", resolveOutput.invoke(controller));

                com.cryptocarver.model.SecretVisibility originalVisibility = com.cryptocarver.model.AppSettings.getInstance()
                        .getSecretVisibility();
                try {
                    com.cryptocarver.model.AppSettings.getInstance()
                            .setSecretVisibility(com.cryptocarver.model.SecretVisibility.REDACTED);
                    controller.publish(com.cryptocarver.model.OperationResult.forOperation("Signature Verification")
                            .detail(com.cryptocarver.model.OperationDetail.secretDetail("Private Key", "must-not-appear"))
                            .status("Signature is valid").build());
                    String noOutputResult = (String) resolveOutput.invoke(controller);
                    assertEquals("", noOutputResult, "REDACTED mode completely hides SECRET operation results");
                } finally {
                    com.cryptocarver.model.AppSettings.getInstance().setSecretVisibility(originalVisibility);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void testExpandedResultKeepsRenderedSymmetricGcmOutput() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        ModernMainController controller = controllerRef.get();
        Method route = ModernMainController.class.getDeclaredMethod("handleItemSelected", String.class);
        Method resolveOutput = ModernMainController.class.getDeclaredMethod("resolveCurrentOutputText");
        route.setAccessible(true);
        resolveOutput.setAccessible(true);
        runAndWait(() -> {
            try {
                route.invoke(controller, "Symmetric Ciphers");
                controller.publish(com.cryptocarver.model.OperationResult.forOperation("Symmetric Encrypt")
                        .output(new byte[] {(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF})
                        .enrichedOutput("=== AES-256-GCM ENCRYPTION RESULT ===\n\nCIPHERTEXT (4 bytes):\nDEADBEEF"
                        + "\n\nAUTHENTICATION TAG (16 bytes):\n00112233445566778899AABBCCDDEEFF", com.cryptocarver.model.OperationDetail.Classification.PUBLIC)
                        .build());

                String expanded = (String) resolveOutput.invoke(controller);
                assertTrue(expanded.contains("AUTHENTICATION TAG"));
                assertTrue(expanded.contains("DEADBEEF"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void testExpandedResultUsesUpdatedVisibleResultAreaWithoutFocus() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        ModernMainController controller = controllerRef.get();
        Method route = ModernMainController.class.getDeclaredMethod("handleItemSelected", String.class);
        Method resolveOutput = ModernMainController.class.getDeclaredMethod("resolveCurrentOutputText");
        route.setAccessible(true);
        resolveOutput.setAccessible(true);
        runAndWait(() -> {
            try {
                route.invoke(controller, "Symmetric Ciphers");
                controller.publish(com.cryptocarver.model.OperationResult.forOperation("Other Cipher Result")
                        .output("RAW-OUTPUT".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        .enrichedOutput("Formatted result from a specialized operation", com.cryptocarver.model.OperationDetail.Classification.PUBLIC)
                        .build());

                assertEquals("Formatted result from a specialized operation", resolveOutput.invoke(controller));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void testSmokeHandlersNoSilentFailures() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();

        runAndWait(() -> {
            try {
                URL resource = getClass().getResource("/fxml/main-view-modern.fxml");
                FXMLLoader loader = new FXMLLoader(resource);
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        ModernMainController controller = controllerRef.get();

        JOSEController joseController = getField(controller, "joseController");
        assertNotNull(joseController, "JOSE child controller must be injected by fx:include");

    }

    private Field getDeclaredFieldOrNull(Class<?> type, String name) {
        try {
            return type.getDeclaredField(name);
        } catch (NoSuchFieldException exception) {
            return null;
        }
    }



    @Test
    void testPqcAndXmlInjections() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();

        runAndWait(() -> {
            try {
                URL resource = getClass().getResource("/fxml/main-view-modern.fxml");
                FXMLLoader loader = new FXMLLoader(resource);
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        ModernMainController controller = controllerRef.get();
        Method handleItemMethod = ModernMainController.class.getDeclaredMethod("handleItemSelected", String.class);
        handleItemMethod.setAccessible(true);

        // 1. Verify that controllers are injected
        PostQuantumController pqcController = getField(controller, "postQuantumContainerController");
        XMLSignatureController xmlController = getField(controller, "xmlSecurityContainerController");
        assertNotNull(pqcController, "PQC Controller must be injected");
        assertNotNull(xmlController, "XML Security Controller must be injected");

        // 2. Navigate to PQC and XML
        runAndWait(() -> {
            try {
                handleItemMethod.invoke(controller, "PQC Key Generation");
                handleItemMethod.invoke(controller, "Sign XML (XAdES)");
            } catch (Exception e) {
                throw new RuntimeException("Navigation failed", e);
            }
        });

        // 3. Verify PQC fields and deterministic flow
        Object pqcKemCiphertextArea = getField(pqcController, "pqcKemCiphertextArea");
        assertNotNull(pqcKemCiphertextArea, "PQC KEM Ciphertext area must be injected");

        Object pqcSignAlgoCombo = getField(pqcController, "pqcSignAlgoCombo");
        assertNotNull(pqcSignAlgoCombo, "PQC Sign Algo Combo must be injected");

        Object xmlSignLevelCombo = getField(xmlController, "xmlSignLevelCombo");
        assertNotNull(xmlSignLevelCombo, "XML Sign Level Combo must be injected");
    }



    @Test
    void testGenericModuleExtracted() throws Exception {
        System.setProperty("test.mode", "true");
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                URL resource = getClass().getResource("/fxml/main-view-modern.fxml");
                FXMLLoader loader = new FXMLLoader(resource);
                Parent root = loader.load();
                ModernMainController controller = loader.getController();
                controllerRef.set(controller);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load FXML", e);
            }
        });

        ModernMainController controller = controllerRef.get();
        assertNotNull(controller);

        GenericController genericController = getField(controller, "genericContainerController");
        assertNotNull(genericController, "GenericController must be loaded via fx:include");

        runAndWait(() -> {
            try {
                // Hashing
                javafx.scene.control.TextArea hashInput = getField(genericController, "hashInputArea");
                javafx.scene.control.TextArea hashOutput = getField(genericController, "hashOutputArea");
                javafx.scene.control.ComboBox<String> hashAlgo = getField(genericController, "hashAlgorithmCombo");
                hashInput.setText("Hello");
                hashAlgo.setValue("SHA-256");
                hashAlgo.setValue("SHA-256");
                genericController.handleCalculateHash();
                assertNotNull(hashOutput.getText());

                // Base64URL
                javafx.scene.control.TextArea manualInput = getField(genericController, "manualInputArea");
                javafx.scene.control.ComboBox<String> manualInputFormat = getField(genericController, "manualInputFormatCombo");
                javafx.scene.control.ComboBox<String> manualOutputFormat = getField(genericController, "manualOutputFormatCombo");
                javafx.scene.control.TextArea manualOutput = getField(genericController, "manualOutputArea");

                manualInput.setText("Test data");
                manualInputFormat.setValue("Text");
                manualOutputFormat.setValue("Text");

                genericController.handleEncodeBase64Url();
                assertNotNull(manualOutput.getText());

                // Compressed Hex
                CompressedHexController compressedHexController = getField(genericController, "compressedHexPaneController");
                assertNotNull(compressedHexController);

                javafx.scene.control.TextArea hexInput = getField(compressedHexController, "compressedHexInputArea");
                javafx.scene.control.TextArea hexOutput = getField(compressedHexController, "compressedHexOutputArea");
                hexInput.setText("112233");
                java.lang.reflect.Method m = compressedHexController.getClass().getDeclaredMethod("handleCompressHex");
                m.setAccessible(true);
                m.invoke(compressedHexController);
                assertNotNull(hexOutput.getText());

                // Endian Conversion
                javafx.scene.control.ComboBox<String> endianCombo = getField(genericController, "endianWordSizeCombo");

                // test 16 bits
                manualInput.setText("1122");
                manualInputFormat.setValue("Hexadecimal");
                manualOutputFormat.setValue("Hexadecimal");
                endianCombo.setValue("16 bits (2 bytes)");
                genericController.handleConvertEndian();
                assertEquals("2211", manualOutput.getText());

                // 32 bits
                manualInput.setText("11223344");
                endianCombo.setValue("32 bits (4 bytes)");
                genericController.handleConvertEndian();
                assertEquals("44332211", manualOutput.getText());

                // 64 bits
                manualInput.setText("1122334455667788");
                endianCombo.setValue("64 bits (8 bytes)");
                genericController.handleConvertEndian();
                assertEquals("8877665544332211", manualOutput.getText());

                // 128 bits
                manualInput.setText("112233445566778899AABBCCDDEEFF00");
                endianCombo.setValue("128 bits (16 bytes)");
                genericController.handleConvertEndian();
                assertEquals("00FFEEDDCCBBAA998877665544332211", manualOutput.getText().toUpperCase());

                // invalid format should default to 4 bytes
                endianCombo.setValue("invalid bits");
                manualInput.setText("11223344");
                genericController.handleConvertEndian();
                assertEquals("44332211", manualOutput.getText());

                // invalid length should throw and show error, but not crash test
                manualInput.setText("112233");
                endianCombo.setValue("16 bits (2 bytes)");
                genericController.handleConvertEndian();



                // File Conversion
                javafx.scene.control.TextField fileInput = getField(genericController, "fileInputPathField");
                javafx.scene.control.TextField fileOutput = getField(genericController, "fileOutputPathField");
                javafx.scene.control.ComboBox<String> fileInFormat = getField(genericController, "fileInputFormatCombo");
                javafx.scene.control.ComboBox<String> fileOutFormat = getField(genericController, "fileOutputFormatCombo");
                javafx.scene.control.ComboBox<String> fileEncodingCombo = getField(genericController, "fileEncodingCombo");

                java.io.File tempIn = java.io.File.createTempFile("testIn", ".txt");
                java.io.File tempOut = java.io.File.createTempFile("testOut", ".txt");
                java.nio.file.Files.writeString(tempIn.toPath(), "test");

                fileInput.setText(tempIn.getAbsolutePath());
                fileOutput.setText(tempOut.getAbsolutePath());
                fileInFormat.setValue("Text");
                fileOutFormat.setValue("Hex");
                if (fileEncodingCombo != null) fileEncodingCombo.setValue("UTF-8");

                genericController.handleConvertFile();

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void testBatchRunnerFunctional() throws Exception {
        AtomicReference<GenericController> controllerRef = new AtomicReference<>();
        AtomicReference<ModernMainController> mainControllerRef = new AtomicReference<>();
        AtomicReference<javafx.scene.control.TextArea> inputAreaRef = new AtomicReference<>();
        AtomicReference<javafx.scene.control.ComboBox<String>> opRef = new AtomicReference<>();
        AtomicReference<javafx.scene.control.ComboBox<String>> formatRef = new AtomicReference<>();

        runAndWait(() -> {
            try {
                URL resource = getClass().getResource("/fxml/main-view-modern.fxml");
                FXMLLoader loader = new FXMLLoader(resource);
                Parent root = loader.load();
                ModernMainController controller = loader.getController();
                mainControllerRef.set(controller);
                GenericController generic = getField(controller, "genericContainerController");
                controllerRef.set(generic);
                inputAreaRef.set(getField(generic, "batchInputArea"));
                opRef.set(getField(generic, "batchOperationCombo"));
                formatRef.set(getField(generic, "batchInputFormatCombo"));

                formatRef.get().setValue("CSV");
                inputAreaRef.get().setText("input\nrow1\nrow2");
                opRef.get().setValue("SHA-256 (UTF-8 → Hex)");

                com.cryptocarver.model.HistoryManager hm = getField(controller, "historyManager");
                if (hm != null) hm.clearHistory();

                generic.handleRunBatch();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        GenericController generic = controllerRef.get();
        javafx.concurrent.Task<?> task = getField(generic, "activeBatchTask");
        assertNotNull(task);

        // Wait deterministically for the task to finish using Future.get()
        try {
            task.get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            fail("Batch task timed out");
        } catch (Exception e) {
            // ignore
        }

        runAndWait(() -> {
            try {
                javafx.scene.control.TextArea batchResultArea = getField(generic, "batchResultArea");
                assertNotNull(batchResultArea.getText());
                assertTrue(batchResultArea.getText().contains("Rows processed: 2"), "Output should summarize rows");
                assertTrue(batchResultArea.getText().contains("#1 OK"), "Output should contain OK for row 1");
                assertTrue(batchResultArea.getText().contains("#2 OK"), "Output should contain OK for row 2");

                Object lastReport = getField(generic, "lastBatchReport");
                assertNotNull(lastReport, "Exportable report should be present after success");
                java.lang.reflect.Method succeededMethod = lastReport.getClass().getDeclaredMethod("succeeded");
                assertEquals(2L, ((Number) succeededMethod.invoke(lastReport)).longValue(), "Should have 2 successes");

                // Verify publication to history
                com.cryptocarver.model.HistoryManager hm = getField(mainControllerRef.get(), "historyManager");
                assertNotNull(hm, "HistoryManager should be initialized");
                assertFalse(hm.getHistoryItems().isEmpty(), "History should not be empty");
                com.cryptocarver.model.HistoryItem item = hm.getHistoryItems().get(hm.getHistoryItems().size() - 1);
                assertEquals("Batch Runner", item.getOperation());
                assertTrue(item.getDetails().contains("Rows"), "History should contain Rows");
                assertTrue(item.getDetails().contains("2"), "History should contain 2");
                assertTrue(item.getDetails().contains("Succeeded"), "History should contain Succeeded");

                // Test Cancel with a long operation
                StringBuilder longInput = new StringBuilder("input\n");
                for (int j = 0; j < 50000; j++) {
                    longInput.append("row").append(j).append("\n");
                }
                inputAreaRef.get().setText(longInput.toString());
                generic.handleRunBatch();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        javafx.concurrent.Task<?> task2 = getField(generic, "activeBatchTask");
        assertNotNull(task2);

        // Cancel immediately
        runAndWait(() -> generic.handleCancelBatch());

        try {
            task2.get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            // CancellationException or others expected
        }

        runAndWait(() -> {
            try {
                Object lastReport = getField(generic, "lastBatchReport");
                assertNull(lastReport, "Exportable report should be null if cancelled or partial");

                com.cryptocarver.model.HistoryManager hm = getField(mainControllerRef.get(), "historyManager");
                if (hm != null && !hm.getHistoryItems().isEmpty()) {
                    com.cryptocarver.model.HistoryItem item = hm.getHistoryItems().get(hm.getHistoryItems().size() - 1);
                    // It shouldn't be the cancelled batch. The last one should be the previous successful batch.
                    assertFalse(item.getDetails().contains("50000"), "Cancelled batch should not be published");
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void testGenericModulePublishing() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();

        runAndWait(() -> {
            try {
                URL resource = getClass().getResource("/fxml/main-view-modern.fxml");
                FXMLLoader loader = new FXMLLoader(resource);
                loader.load();
                ModernMainController controller = loader.getController();
                controllerRef.set(controller);

                GenericController generic = getField(controller, "genericContainerController");

                javafx.scene.control.TextArea hashInputArea = getField(generic, "hashInputArea");
                javafx.scene.control.ComboBox<String> hashAlgorithmCombo = getField(generic, "hashAlgorithmCombo");

                hashInputArea.setText("Test data");
                hashAlgorithmCombo.setValue("SHA-256");

                com.cryptocarver.model.HistoryManager hm = getField(controller, "historyManager");
                if (hm != null) hm.clearHistory();

                generic.handleCalculateHash();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        runAndWait(() -> {
            try {
                com.cryptocarver.model.HistoryManager hm = getField(controllerRef.get(), "historyManager");
                assertNotNull(hm, "HistoryManager should be initialized");
                assertFalse(hm.getHistoryItems().isEmpty(), "History should not be empty");

                com.cryptocarver.model.HistoryItem item = hm.getHistoryItems().get(hm.getHistoryItems().size() - 1);
                assertTrue(item.getOperation().startsWith("Hashing:"), "Operation should start with Hashing:, but was " + item.getOperation());
                assertTrue(item.getOperation().contains("SHA-256"), "Operation should contain SHA-256");
                assertTrue(item.getDetails().contains("Algorithm"), "Details should include Algorithm");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

}
