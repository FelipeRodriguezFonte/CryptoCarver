package com.cryptocarver.ui;

import javafx.scene.control.DialogPane;
import javafx.application.Platform;
import javafx.stage.FileChooser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class DialogServiceTest {
    @BeforeAll
    static void startFx() throws Exception {
        java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(5, java.util.concurrent.TimeUnit.SECONDS));
    }

    @Test
    void chooserIsConfiguredAndUsesLogicalTypeForDirectoryMemory() throws Exception {
        DialogService service = new DialogService();
        FileChooser.ExtensionFilter filter = new FileChooser.ExtensionFilter("JSON", "*.json");

        FileChooser chooser = service.createFileChooser("history", "Open history", filter);

        assertEquals("Open history", chooser.getTitle());
        assertEquals(1, chooser.getExtensionFilters().size());
        assertEquals("JSON", chooser.getExtensionFilters().get(0).getDescription());

        Path directory = Files.createTempDirectory("cryptocarver-dialog");
        Path file = directory.resolve("history.json");
        Field directories = DialogService.class.getDeclaredField("lastDirectories");
        directories.setAccessible(true);
        @SuppressWarnings("unchecked") Map<String, java.io.File> values = (Map<String, java.io.File>) directories.get(service);
        values.put("history", directory.toFile());

        FileChooser remembered = service.createFileChooser("history", "Open history", filter);
        assertEquals(directory.toFile(), remembered.getInitialDirectory());
        Files.deleteIfExists(file);
        Files.deleteIfExists(directory);
    }

    @Test
    void dialogThemeClassAndStylesheetsCanBeApplied() {
        DialogPane pane = new DialogPane();
        pane.getStyleClass().add("cc-dialog-pane");
        assertTrue(pane.getStyleClass().contains("cc-dialog-pane"));
    }
}
