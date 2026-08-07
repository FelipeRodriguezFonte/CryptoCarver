package com.cryptocarver.ui;

import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Accordion;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class SpecializedLiveI18nUITest {
    @BeforeAll
    static void startJavaFx() {
        try {
            Platform.startup(() -> Platform.setImplicitExit(false));
        } catch (IllegalStateException ignored) {
            // Another JavaFX test already started the toolkit.
        }
    }

    @Test
    void pqcChangesLanguageWithoutReloading() throws Exception {
        runAndWait(() -> {
            I18nService service = I18nService.getInstance();
            service.setPreference(LanguagePreference.EN);
            try {
                Parent root = new FXMLLoader(getClass().getResource("/fxml/pqc.fxml")).load();
                Accordion accordion = (Accordion) root.lookupAll(".module-accordion").stream().findFirst().orElse(null);
                assertNotNull(accordion);
                assertEquals("🔑 PQC Key Generation", accordion.getPanes().get(0).getText());
                service.setPreference(LanguagePreference.ES);
                assertEquals("🔑 Generación de claves PQC", accordion.getPanes().get(0).getText());
            } catch (Exception e) {
                throw new AssertionError(e);
            } finally {
                service.setPreference(LanguagePreference.EN);
            }
        });
    }

    @Test
    void joseChangesLanguageWithoutReloading() throws Exception {
        runAndWait(() -> {
            I18nService service = I18nService.getInstance();
            service.setPreference(LanguagePreference.EN);
            try {
                Parent root = new FXMLLoader(getClass().getResource("/fxml/jose.fxml")).load();
                Label header = (Label) root.lookupAll(".section-header").stream().findFirst().orElse(null);
                assertNotNull(header);
                assertEquals("JSON Web Token (JWT)", header.getText());
                service.setPreference(LanguagePreference.ES);
                assertEquals("JSON Web Token (JWT)", header.getText());
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
            try { action.run(); }
            catch (Throwable throwable) { failure[0] = throwable; }
            finally { latch.countDown(); }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("JavaFX test timed out");
        if (failure[0] != null) throw new AssertionError(failure[0]);
    }
}
