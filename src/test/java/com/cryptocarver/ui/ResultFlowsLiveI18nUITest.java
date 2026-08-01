package com.cryptocarver.ui;

import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ResultFlowsLiveI18nUITest {
    @BeforeAll
    static void startJavaFx() throws Exception {
        try {
            Platform.startup(() -> Platform.setImplicitExit(false));
        } catch (IllegalStateException ignored) {
            // JavaFX was already started by another UI test.
        }
    }

    @Test
    void processDesignerChangesLanguageWithoutReloading() throws Exception {
        runAndWait(() -> {
            I18nService service = I18nService.getInstance();
            service.setPreference(LanguagePreference.EN);
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/process_designer.fxml"));
                TitledPane root = loader.load();
                assertEquals("🧩 Process Designer (MVP)", root.getText());
                service.setPreference(LanguagePreference.ES);
                assertEquals("🧩 Diseñador de procesos (MVP)", root.getText());
            } catch (Exception e) {
                throw new AssertionError(e);
            } finally {
                service.setPreference(LanguagePreference.EN);
            }
        });
    }

    @Test
    void clipboardShelfChangesLanguageWithoutChangingStoredValue() throws Exception {
        runAndWait(() -> {
            I18nService service = I18nService.getInstance();
            service.setPreference(LanguagePreference.EN);
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/clipboard_shelf.fxml"));
                Parent root = loader.load();
                Label header = (Label) root.lookupAll(".header-label").stream().findFirst().orElse(null);
                assertNotNull(header);
                assertEquals("Clipboard Shelf / Laboratory Notebook", header.getText());
                service.setPreference(LanguagePreference.ES);
                assertEquals("Clipboard Shelf / Cuaderno de laboratorio", header.getText());
            } catch (Exception e) {
                throw new AssertionError(e);
            } finally {
                service.setPreference(LanguagePreference.EN);
            }
        });
    }

    private void runAndWait(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure[0] = throwable;
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("JavaFX test timed out");
        if (failure[0] != null) throw new AssertionError(failure[0]);
    }
}
