package com.cryptocarver.ui;

import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in JavaFX smoke paths for the specialized UX-19 modules. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class Ux19SpecializedLiveUITest {
    @BeforeAll
    static void startToolkit() {
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException ignored) { }
    }

    @Test
    void specializedRoutesLoadRealContentAndResetButtons() throws Exception {
        runAndWait(() -> {
            for (String resource : List.of("asn1.fxml", "xml_security.fxml", "wss_security.fxml",
                    "jose.fxml", "payments.fxml", "process_designer.fxml")) {
                try {
                    Parent root = new FXMLLoader(getClass().getResource("/fxml/" + resource)).load();
                    assertFalse(root.getChildrenUnmodifiable().isEmpty(), resource + " must not be an empty panel");
                    assertTrue(findButtons(root).stream().anyMatch(button -> "Reset".equals(button.getText())
                            || "Clear".equals(button.getText())
                            || "Clear canvas".equals(button.getText())), resource + " has no reset/clear action");
                } catch (Exception error) {
                    throw new AssertionError(resource, error);
                }
            }
        });
    }

    @Test
    void spanishEnglishRefreshKeepsTechnicalValuesAndFocusTarget() throws Exception {
        runAndWait(() -> {
            try {
                I18nService service = I18nService.getInstance();
                service.setPreference(LanguagePreference.EN);
                Parent asn1 = new FXMLLoader(getClass().getResource("/fxml/asn1.fxml")).load();
                Parent payments = new FXMLLoader(getClass().getResource("/fxml/payments.fxml")).load();
                Scene scene = new Scene(asn1);
                TextArea input = findTextAreas(asn1).stream().findFirst().orElseThrow();
                input.setText("3080A1B2");
                input.requestFocus();
                service.setPreference(LanguagePreference.ES);
                assertEquals("3080A1B2", input.getText());
                service.setPreference(LanguagePreference.EN);
                assertEquals("3080A1B2", input.getText());
                assertNotNull(payments);
                assertTrue(scene.getRoot().isFocusTraversable() || input.isFocusTraversable());
            } catch (Exception error) {
                throw new AssertionError(error);
            }
        });
    }

    private static List<Button> findButtons(Node node) {
        java.util.ArrayList<Button> result = new java.util.ArrayList<>();
        if (node instanceof Button button) result.add(button);
        if (node instanceof javafx.scene.control.Accordion accordion) {
            accordion.getPanes().forEach(pane -> result.addAll(findButtons(pane)));
        } else if (node instanceof javafx.scene.control.TitledPane pane && pane.getContent() != null) {
            result.addAll(findButtons(pane.getContent()));
        } else if (node instanceof javafx.scene.Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> result.addAll(findButtons(child)));
        }
        return result;
    }

    private static List<TextArea> findTextAreas(Node node) {
        java.util.ArrayList<TextArea> result = new java.util.ArrayList<>();
        if (node instanceof TextArea area) result.add(area);
        if (node instanceof javafx.scene.control.Accordion accordion) {
            accordion.getPanes().forEach(pane -> result.addAll(findTextAreas(pane)));
        } else if (node instanceof javafx.scene.control.TitledPane pane && pane.getContent() != null) {
            result.addAll(findTextAreas(pane.getContent()));
        } else if (node instanceof javafx.scene.Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> result.addAll(findTextAreas(child)));
        }
        return result;
    }

    private static void runAndWait(Runnable action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];
        Platform.runLater(() -> {
            try { action.run(); } catch (Throwable error) { failure[0] = error; } finally { done.countDown(); }
        });
        assertTrue(done.await(15, TimeUnit.SECONDS), "JavaFX test timed out");
        if (failure[0] != null) throw new AssertionError(failure[0]);
    }
}
