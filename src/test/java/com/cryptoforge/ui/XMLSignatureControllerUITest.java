package com.cryptoforge.ui;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.net.URL;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
public class XMLSignatureControllerUITest {

    private static Parent root;
    private static XMLSignatureController controller;

    @BeforeAll
    public static void setUp() throws Exception {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException e) {
            // Platform already started
        }

        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                URL location = XMLSignatureControllerUITest.class.getResource("/fxml/xml_security.fxml");
                FXMLLoader loader = new FXMLLoader(location);
                root = loader.load();
                controller = loader.getController();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        });
        latch.await();
        assertNotNull(root, "FXML must be loaded");
        assertNotNull(controller, "Controller must be loaded");
    }

    @Test
    public void testToggleGroupBranching() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                // lookup() needs a Scene for CSS application
                javafx.scene.Scene scene = new javafx.scene.Scene(root);
                root.applyCss();
                root.layout();

                RadioButton localRadio = (RadioButton) root.lookup("#xmlSignSourceLocalRadio");
                RadioButton pkcs11Radio = (RadioButton) root.lookup("#xmlSignSourcePkcs11Radio");
                ToggleGroup toggleGroup = localRadio.getToggleGroup();

                assertNotNull(localRadio);
                assertNotNull(pkcs11Radio);
                assertNotNull(toggleGroup);

                assertTrue(localRadio.isSelected());

                toggleGroup.selectToggle(pkcs11Radio);
                assertTrue(pkcs11Radio.isSelected());

                toggleGroup.selectToggle(localRadio);
                assertTrue(localRadio.isSelected());

            } finally {
                latch.countDown();
            }
        });
        latch.await();
    }

    @Test
    public void testPkcs11SourceHidesTheEntireLocalKeystoreForm() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                RadioButton localRadio;
                RadioButton pkcs11Radio;
                VBox localKeyBox;
                try {
                    localRadio = injected("xmlSignSourceLocalRadio", RadioButton.class);
                    pkcs11Radio = injected("xmlSignSourcePkcs11Radio", RadioButton.class);
                    localKeyBox = injected("xmlSignLocalKeyBox", VBox.class);
                } catch (ReflectiveOperationException error) {
                    throw new AssertionError("The XAdES signing source controls must be injected from FXML", error);
                }

                assertNotNull(localKeyBox);
                assertTrue(localKeyBox.isVisible());
                assertTrue(localKeyBox.isManaged());

                pkcs11Radio.fire();
                assertTrue(pkcs11Radio.isSelected());
                assertFalse(localKeyBox.isVisible());
                assertFalse(localKeyBox.isManaged());

                localRadio.fire();
                assertTrue(localKeyBox.isVisible());
                assertTrue(localKeyBox.isManaged());
            } finally {
                latch.countDown();
            }
        });
        latch.await();
    }

    private static <T> T injected(String fieldName, Class<T> type) throws ReflectiveOperationException {
        Field field = XMLSignatureController.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return type.cast(field.get(controller));
    }
}
