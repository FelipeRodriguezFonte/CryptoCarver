package com.cryptocarver.ui;

import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in JavaFX contract for actionable PAN feedback and reset focus. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class Ux20RPaymentsLiveUITest {
    @BeforeAll
    static void startToolkit() {
        try {
            Platform.startup(() -> Platform.setImplicitExit(false));
        } catch (IllegalStateException ignored) {
            // JavaFX toolkit already started by another UI test.
        }
    }

    @Test
    void invalidPanIsActionableInBothLanguagesAndResetKeepsFocusAndData() throws Exception {
        runAndWait(() -> {
            I18nService service = I18nService.getInstance();
            service.setPreference(LanguagePreference.EN);
            Stage stage = new Stage();
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/payments.fxml"));
                Parent root = loader.load();
                root.setManaged(true);
                root.setVisible(true);
                Scene scene = new Scene(root);
                stage.setScene(scene);
                stage.show();
                root.applyCss();
                root.layout();

                PaymentsController controller = loader.getController();
                TextField cvkA = (TextField) root.lookup("#cvkAField");
                TextField cvkB = (TextField) root.lookup("#cvkBField");
                TextField pan = (TextField) root.lookup("#panFieldCvv");
                TextField expiry = (TextField) root.lookup("#expiryDateField");
                TextField serviceCode = (TextField) root.lookup("#serviceCodeField");
                ComboBox<?> cvvType = (ComboBox<?>) root.lookup("#cvvTypeCombo");
                TextArea result = (TextArea) root.lookup("#cvvResultArea");
                assertNotNull(cvkA);
                assertNotNull(cvkB);
                assertNotNull(pan);
                assertNotNull(expiry);
                assertNotNull(serviceCode);
                assertNotNull(cvvType);
                assertNotNull(result);

                cvkA.setText("0011223344556677");
                cvkB.setText("8899AABBCCDDEEFF");
                pan.setText("1234");
                expiry.setText("2512");
                serviceCode.setText("000");
                cvvType.getSelectionModel().selectFirst();
                controller.handleGenerateCvv();
                String english = result.getText();
                assertTrue(english.contains("PAN"));
                assertNotEquals(service.text("module.payments.error.required"), english);

                service.setPreference(LanguagePreference.ES);
                controller.handleGenerateCvv();
                String spanish = result.getText();
                assertTrue(spanish.contains("PAN"));
                assertNotEquals(english, spanish);

                pan.setText("1234567890123");
                pan.requestFocus();
                controller.handleReset();
                assertEquals("1234567890123", pan.getText(), "Reset Defaults must retain local data");
                assertSame(pan, scene.getFocusOwner(), "Reset Defaults must restore the focused control");

                controller.handleClear();
                assertEquals("", pan.getText(), "Clear must remove local input data");
            } catch (Exception error) {
                throw new AssertionError(error);
            } finally {
                stage.hide();
                service.setPreference(LanguagePreference.EN);
            }
        });
    }

    private static void runAndWait(Runnable action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable error) {
                failure[0] = error;
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(15, TimeUnit.SECONDS), "JavaFX test timed out");
        if (failure[0] != null) throw new AssertionError(failure[0]);
    }
}
