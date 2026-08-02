package com.cryptocarver.ui;

import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Accordion;
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
class PaymentsFormatsLiveI18nUITest {
    @BeforeAll
    static void startJavaFx() {
        try {
            Platform.startup(() -> Platform.setImplicitExit(false));
        } catch (IllegalStateException ignored) {
            // JavaFX toolkit already started by another UI test.
        }
    }

    @Test
    void paymentsChangesLanguageWithoutReloading() throws Exception {
        assertModuleChangesLanguage("/fxml/payments.fxml", "💳 CVV Operations (CVV/CVV2/iCVV)",
                "💳 Operaciones CVV (CVV/CVV2/iCVV)");
    }

    @Test
    void openPgpChangesLanguageWithoutReloading() throws Exception {
        assertModuleChangesLanguage("/fxml/openpgp.fxml", "OpenPGP / GPG Laboratory", "Laboratorio OpenPGP / GPG");
    }

    private void assertModuleChangesLanguage(String resource, String english, String spanish) throws Exception {
        runAndWait(() -> {
            I18nService service = I18nService.getInstance();
            service.setPreference(LanguagePreference.EN);
            try {
                Parent root = new FXMLLoader(getClass().getResource(resource)).load();
                Accordion accordion = (Accordion) root.lookupAll(".module-accordion").stream().findFirst().orElse(null);
                assertNotNull(accordion);
                assertEquals(english, accordion.getPanes().get(0).getText());
                service.setPreference(LanguagePreference.ES);
                assertEquals(spanish, accordion.getPanes().get(0).getText());
            } catch (Exception error) {
                throw new AssertionError(error);
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
            catch (Throwable error) { failure[0] = error; }
            finally { latch.countDown(); }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("JavaFX test timed out");
        if (failure[0] != null) throw new AssertionError(failure[0]);
    }
}
