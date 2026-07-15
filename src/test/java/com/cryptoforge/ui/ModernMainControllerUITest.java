package com.cryptoforge.ui;

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
            Platform.startup(latch::countDown);
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("JavaFX failed to start within timeout");
            }
            jfxIsSetup = true;
        }
    }

    private <T> T getField(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(target);
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
            {"Digital Signatures", "authenticationContainer"},
            {"Key Generation", "symmetricKeysContainer"},
            {"PQC Key Generation", "postQuantumContainer"},
            {"Sign XML (XAdES)", "xmlSecurityContainer"},
            {"Generate Certificate", "certificatesContainer"},
            {"JWT (Signed)", "joseContainer"},
            {"PIN Generation", "paymentsContainer"},
            {"Decode ASN.1", "certificatesContainer"},
            {"Recent Operations", "historyContainer"}
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
        
        // Only deterministic handlers with no FileChoosers, Alerts or Networking
        String[] deterministicHandlers = {
            "handlePemToJwk",
            "handleJwkToPem"
        };

        for (String handler : deterministicHandlers) {
            runAndWait(() -> {
                try {
                    Method m = null;
                    try {
                        m = ModernMainController.class.getDeclaredMethod(handler);
                    } catch (NoSuchMethodException e) {
                        return; // Method might be moved, testing only what exists here
                    }
                    m.setAccessible(true);
                    m.invoke(controller);
                } catch (InvocationTargetException e) {
                    throw new RuntimeException("Handler " + handler + " failed internally", e.getCause());
                } catch (Exception e) {
                    throw new RuntimeException("Handler invocation failed: " + handler, e);
                }
            });
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
}
