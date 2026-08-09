package com.cryptocarver.ui;

import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in JavaFX accessibility and keyboard checks using the real modern FXML. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class Ux23AccessibilityLiveUITest {
    private static Stage stage;

    @BeforeAll
    static void startFx() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                ready.countDown();
            });
        } catch (IllegalStateException alreadyStarted) {
            ready.countDown();
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS), "JavaFX toolkit did not start");
    }

    @AfterAll
    static void stopStage() throws Exception {
        fx(() -> {
            if (stage != null) stage.hide();
            I18nService.getInstance().setPreference(LanguagePreference.EN);
        });
    }

    @Test
    void realBannerSupportsFocusOrderKeyboardActivationCloseAndLiveLocale() throws Exception {
        final ModernMainController[] controller = new ModernMainController[1];
        final Parent[] root = new Parent[1];
        final Scene[] scene = new Scene[1];
        final Button[] go = new Button[1];
        final Button[] copy = new Button[1];
        final Button[] close = new Button[1];
        final Label[] title = new Label[1];
        final Label[] remedy = new Label[1];
        final Node[] target = new Node[1];

        fx(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
            try {
                root[0] = loader.load();
            } catch (Exception e) {
                throw new AssertionError("Could not load main-view-modern.fxml", e);
            }
            controller[0] = loader.getController();
            scene[0] = new Scene(root[0], 1200, 800);
            stage = new Stage();
            stage.setScene(scene[0]);
            stage.show();

            go[0] = getField(controller[0], "errorBannerGoToFieldBtn", Button.class);
            copy[0] = getField(controller[0], "errorBannerCopyDetailsBtn", Button.class);
            close[0] = getField(controller[0], "errorBannerCloseBtn", Button.class);
            title[0] = getField(controller[0], "errorBannerTitle", Label.class);
            remedy[0] = getField(controller[0], "errorBannerRemedy", Label.class);
            target[0] = InlineErrorPresenter.findNodeByFieldKey(root[0], "inputFormatCombo");
            assertNotNull(target[0], "Real FXML must expose inputFormatCombo");
        });

        I18nService i18n = I18nService.getInstance();
        fx(() -> {
            i18n.setPreference(LanguagePreference.EN);
            controller[0].applyLocalization();
            String secret = "00112233445566778899AABBCCDDEEFF";
            controller[0].showError(new UserFacingError(
                    "Invalid key=" + secret,
                    "Use secret=" + secret,
                    "Correct the highlighted field.",
                    "inputFormatCombo"));

            Button actionGo = go[0];
            Button actionCopy = copy[0];
            Button actionClose = close[0];
            assertTrue(actionGo.isVisible() && actionGo.isManaged());
            assertTrue(actionGo.isFocusTraversable());
            assertTrue(actionCopy.isFocusTraversable());
            assertTrue(actionClose.isFocusTraversable());
            assertEquals("Go to field", actionGo.getAccessibleText());
            assertEquals("Copy technical details", actionCopy.getAccessibleText());
            assertEquals("Dismiss the validation message", actionClose.getAccessibleText());
            assertFalse(title[0].getAccessibleText().contains(secret));
            assertFalse(title[0].getText().contains(secret));
            assertFalse(remedy[0].getAccessibleText().contains(secret));
            assertFalse(remedy[0].getText().contains(secret));

            HBox actions = (HBox) actionGo.getParent();
            assertEquals(actionGo, actions.getChildren().get(0));
            assertEquals(actionCopy, actions.getChildren().get(1));
            assertEquals(actionClose, actions.getChildren().get(2));
            assertSame(target[0], scene[0].getFocusOwner(), "A valid target must keep focus ahead of the banner");

            actionGo.requestFocus();
            fireKey(actionGo, KeyCode.TAB, false, true);
            assertSame(actionCopy, scene[0].getFocusOwner(), "Tab must move to copy details");
            fireKey(actionCopy, KeyCode.TAB, true, true);
            assertSame(actionGo, scene[0].getFocusOwner(), "Shift+Tab must return to go-to-field");

            actionGo.requestFocus();
            fireKey(actionGo, KeyCode.ENTER, false, true);
            fireKey(actionGo, KeyCode.ENTER, false, false);
            assertSame(target[0], scene[0].getFocusOwner(), "Enter must activate go-to-field");

            actionClose.requestFocus();
            fireKey(actionClose, KeyCode.SPACE, false, true);
            fireKey(actionClose, KeyCode.SPACE, false, false);
            assertFalse(actionClose.getParent().getParent().isVisible(), "Space must close the banner");
            assertSame(target[0], scene[0].getFocusOwner(), "Closing must restore the invalid field focus");
        });

        fx(() -> {
            i18n.setPreference(LanguagePreference.ES);
            controller[0].applyLocalization();
            controller[0].showError(new UserFacingError(
                    "Error de entrada", "Completa el campo.", "Corrige el valor.", "inputFormatCombo"));
            assertEquals("Ir al campo", go[0].getAccessibleText());
            assertEquals("Copiar detalles técnicos", copy[0].getAccessibleText());
            assertEquals("Descartar el mensaje de validación", close[0].getAccessibleText());
            assertEquals("Mensaje de error", title[0].getAccessibleHelp());
            assertEquals("Acción recomendada",
                    getField(controller[0], "errorBannerRemedy", Label.class).getAccessibleHelp());
        });
    }

    private static void fireKey(Node node, KeyCode code, boolean shift, boolean pressed) {
        KeyEvent event = new KeyEvent(pressed ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED,
                "", "", code, shift, false, false, false);
        javafx.event.Event.fireEvent(node, event);
    }

    private static <T> T getField(Object target, String name, Class<T> type) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(target));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Missing field " + name, e);
        }
    }

    private static void fx(Runnable action) throws Exception {
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
        assertTrue(done.await(30, TimeUnit.SECONDS), "JavaFX action timed out");
        if (failure[0] != null) throw new AssertionError("JavaFX accessibility check failed", failure[0]);
    }
}
