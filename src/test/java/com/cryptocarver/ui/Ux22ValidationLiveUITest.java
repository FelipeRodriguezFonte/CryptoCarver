package com.cryptocarver.ui;

import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in JavaFX checks for shared validation focus and live locale changes. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class Ux22ValidationLiveUITest {
    private static Stage stage;

    @BeforeAll
    static void startFx() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); }
        catch (IllegalStateException alreadyStarted) { ready.countDown(); }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
    }

    @AfterAll
    static void stopStage() throws Exception {
        fx(() -> { if (stage != null) stage.hide(); });
    }

    private static void fx(Runnable action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> { try { action.run(); } finally { done.countDown(); } });
        assertTrue(done.await(15, TimeUnit.SECONDS));
    }

    @Test
    void cipherXmlWssPaymentsAndTr31ValidationFocusesTheAssociatedField() throws Exception {
        final InlineErrorPresenter[] presenter = new InlineErrorPresenter[1];
        final VBox[] root = new VBox[1];
        final TextField[] fields = new TextField[5];
        fx(() -> {
            HBox banner = new HBox();
            Label title = new Label();
            Label remedy = new Label();
            Button go = new Button();
            Button copy = new Button();
            Button close = new Button();
            banner.getChildren().addAll(title, remedy, go, copy, close);
            root[0] = new VBox(banner);
            String[] ids = {"cipherInputArea", "xmlVerifyInputArea", "wssSignInputArea", "pinField", "tr31KeyBlockField"};
            for (int i = 0; i < ids.length; i++) {
                fields[i] = new TextField();
                fields[i].setId(ids[i]);
                root[0].getChildren().add(fields[i]);
            }
            presenter[0] = new InlineErrorPresenter(banner, title, remedy, go, copy, close);
            stage = new Stage();
            stage.setScene(new Scene(root[0], 700, 300));
            stage.show();
        });

        String[] keys = {"cipherInputArea", "xmlVerifyInputArea", "wssSignInputArea", "pinField", "tr31KeyBlockField"};
        for (int i = 0; i < keys.length; i++) {
            int index = i;
            fx(() -> {
                presenter[0].showError(new UserFacingError("Validation", "Field is required.",
                        "Provide a value before execution.", keys[index]), root[0]);
                presenter[0].goToField(root[0]);
                assertSame(fields[index], root[0].getScene().getFocusOwner(), keys[index]);
                assertTrue(fields[index].getStyleClass().contains("field-error"));
            });
        }
    }

    @Test
    void validationBannerChangesLanguageWithoutChangingFieldRouting() throws Exception {
        final Label[] title = new Label[1];
        final VBox[] root = new VBox[1];
        fx(() -> {
            HBox banner = new HBox();
            title[0] = new Label();
            Label remedy = new Label();
            Button go = new Button();
            Button copy = new Button();
            Button close = new Button();
            banner.getChildren().addAll(title[0], remedy, go, copy, close);
            root[0] = new VBox(banner, new TextField());
            Ux22ValidationLiveUITestHolder.presenter = new InlineErrorPresenter(banner, title[0], remedy, go, copy, close);
        });
        I18nService service = I18nService.getInstance();
        service.setPreference(LanguagePreference.EN);
        fx(() -> Ux22ValidationLiveUITestHolder.presenter.showError(new UserFacingError(
                service.text("preflight.title"), service.text("preflight.input.required"),
                service.text("preflight.remedy.input"), "cipherInputArea"), root[0]));
        assertEquals("Validation required before execution", title[0].getText());
        service.setPreference(LanguagePreference.ES);
        fx(() -> Ux22ValidationLiveUITestHolder.presenter.showError(new UserFacingError(
                service.text("preflight.title"), service.text("preflight.input.required"),
                service.text("preflight.remedy.input"), "cipherInputArea"), root[0]));
        assertEquals("Validación necesaria antes de ejecutar", title[0].getText());
    }

    private static final class Ux22ValidationLiveUITestHolder {
        private static InlineErrorPresenter presenter;
    }
}
