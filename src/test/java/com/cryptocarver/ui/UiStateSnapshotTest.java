package com.cryptocarver.ui;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class UiStateSnapshotTest {

    @BeforeAll
    static void initJfx() {
        try {
            javafx.application.Platform.startup(() -> {});
        } catch (IllegalStateException e) {
            // Toolkit already initialized
        }
    }

    public static class DummyController {
        @FXML public TextField keyField;
        @FXML public TextField passwordInput;
        @FXML public TextField ivField;
        @FXML public TextField dataField;
        @FXML public ComboBox<String> algoCombo;
        @FXML public CheckBox myCheck;

        public DummyController() {
            keyField = new TextField("my-super-secret-key");
            passwordInput = new TextField("hunter2");
            ivField = new TextField("1234567890123456");
            dataField = new TextField("hello world");
            algoCombo = new ComboBox<>();
            algoCombo.getItems().add("AES");
            algoCombo.setValue("AES");
            myCheck = new CheckBox();
            myCheck.setSelected(true);
        }
    }

    @Test
    void captureHistoryRecipeRedactsSecrets() {
        DummyController controller = new DummyController();

        Map<String, Object> state = UiStateSnapshot.captureHistoryRecipe(controller);

        assertEquals("[REDACTED_SECRET]", state.get("DummyController.keyField"));
        assertEquals("[REDACTED_SECRET]", state.get("DummyController.passwordInput"));
        assertEquals("[REDACTED_SECRET]", state.get("DummyController.ivField"));
        assertEquals("hello world", state.get("DummyController.dataField"));
        assertEquals("AES", state.get("DummyController.algoCombo"));
        assertEquals(true, state.get("DummyController.myCheck"));
    }

    @Test
    void capturePortableConfigurationPreservesSecrets() {
        DummyController controller = new DummyController();

        Map<String, Object> state = UiStateSnapshot.capturePortableConfiguration(controller);

        assertEquals("my-super-secret-key", state.get("DummyController.keyField"));
    }
}
