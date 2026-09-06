package com.cryptocarver.ui;

import com.cryptocarver.model.process.NodeCatalog;
import com.cryptocarver.model.process.ProcessDefinition;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
public class ProcessDesignerPaletteUITest {

    private static boolean jfxIsSetup;

    @BeforeAll
    static void initJFX() {
        if (!jfxIsSetup) {
            try {
                CountDownLatch latch = new CountDownLatch(1);
                Platform.startup(() -> {
                    Platform.setImplicitExit(false);
                    latch.countDown();
                });
                try {
                    latch.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {}
            } catch (Throwable ignored) {
                try {
                    Platform.setImplicitExit(false);
                } catch (Throwable ignored2) {}
            }
            jfxIsSetup = true;
        }
    }

    private void runAndWait(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] error = new Throwable[1];
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                error[0] = t;
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            fail("UI test timed out waiting for FX thread");
        }
        if (error[0] != null) {
            if (error[0] instanceof Exception e) throw e;
            throw new RuntimeException(error[0]);
        }
    }

    @Test
    void testPaletteSearchAndDoubleClickAdd() throws Exception {
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/process_designer.fxml"));
                TitledPane root = loader.load();
                ProcessDesignerController controller = loader.getController();

                Scene scene = new Scene(root, 1000, 700);
                Stage stage = new Stage();
                stage.setScene(scene);
                stage.show();
                root.applyCss();
                root.layout();

                VBox paletteContainer = (VBox) scene.lookup("#paletteItemsContainer");
                assertNotNull(paletteContainer, "paletteItemsContainer must be present in FXML");

                // Count items (excluding category headers)
                List<Node> itemNodes = paletteContainer.getChildren().stream()
                        .filter(n -> n instanceof javafx.scene.layout.HBox)
                        .toList();

                assertEquals(NodeCatalog.descriptors().size(), itemNodes.size(),
                        "Total palette item cards must equal NodeCatalog descriptor count");

                // Test palette filtering
                TextField searchField = (TextField) scene.lookup("#paletteSearchField");
                assertNotNull(searchField, "paletteSearchField must be present");

                searchField.setText("Hash");
                List<Node> filtered = paletteContainer.getChildren().stream()
                        .filter(n -> n instanceof javafx.scene.layout.HBox)
                        .toList();
                assertTrue(filtered.size() >= 1, "Searching 'Hash' must return at least 1 result");
                assertTrue(filtered.size() < itemNodes.size(), "Filtering must reduce item count");

                // Clear filter and test double-click addition
                searchField.setText("");
                controller.handleClearCanvas();
                assertEquals(0, controller.toDefinition().nodes.size());

                Node firstItem = paletteContainer.getChildren().stream()
                        .filter(n -> n instanceof javafx.scene.layout.HBox)
                        .findFirst()
                        .orElse(null);
                assertNotNull(firstItem);

                MouseEvent dblClick = new MouseEvent(
                        MouseEvent.MOUSE_CLICKED,
                        0, 0, 10, 10,
                        MouseButton.PRIMARY, 2,
                        false, false, false, false,
                        true, false, false, false, false, false, null
                );
                firstItem.fireEvent(dblClick);

                ProcessDefinition def = controller.toDefinition();
                assertEquals(1, def.nodes.size(), "Double click on palette item must add node to canvas");

                stage.close();
            } catch (Exception e) {
                fail("Palette UI test failed: " + e.getMessage());
            }
        });
    }
}
