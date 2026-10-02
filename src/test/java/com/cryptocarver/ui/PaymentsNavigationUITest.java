package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Accordion;
import javafx.scene.control.TitledPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every Payments route opens the pane it names, in English and Spanish. */
class PaymentsNavigationUITest {
    private ModernMainController shell;
    private Parent root;
    private Stage stage;
    private SecretVisibilityProfile previousVisibility;
    private LanguagePreference previousLanguage;
    private String previousRoute;

    @BeforeAll
    static void startFx() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try {
            Platform.startup(ready::countDown);
        } catch (IllegalStateException started) {
            ready.countDown();
        }
        assertTrue(ready.await(15, TimeUnit.SECONDS));
        fx(() -> Platform.setImplicitExit(false));
    }

    @BeforeEach
    void loadProductionShell() throws Exception {
        AppSettings settings = AppSettings.getInstance();
        previousVisibility = settings.getSecretVisibilityProfile();
        previousLanguage = settings.getLanguagePreference();
        previousRoute = settings.getLastRoute();
        settings.setLastRoute("");
        fx(() -> {
            var loader = Fxml.loader("/fxml/main-view-modern.fxml");
            root = loader.load();
            shell = loader.getController();
            stage = new Stage();
            stage.setScene(new Scene(root, 1400, 900));
        });
    }

    @AfterEach
    void releaseShellAndRestoreSettings() throws Exception {
        fx(() -> {
            if (shell != null) shell.shutdown();
            if (stage != null) {
                stage.close();
                stage.setScene(null);
            }
            AppSettings.getInstance().setSecretVisibilityProfile(previousVisibility);
            I18nService.getInstance().setPreference(previousLanguage);
            AppSettings.getInstance().setLastRoute(previousRoute);
        });
    }

    @ParameterizedTest
    @CsvSource({
            "Clear PIN Blocks, Clear PIN Blocks, EN",
            "Encrypted PIN Blocks, Encrypted PIN Blocks, EN",
            "PIN Generation, PIN Generation, EN",
            "CVV Operations, CVV Operations, EN",
            "DUKPT TDES / AES, DUKPT KSN, EN",
            "ISO 8583 Message Inspector, ISO 8583 Message Inspector, EN",
            "Host Command Bank, Host Command Bank, EN",
            "Clear PIN Blocks, Bloques PIN en claro, ES",
            "ISO 8583 Message Inspector, Inspector de mensajes ISO 8583, ES",
            "Host Command Bank, Banco de comandos host, ES"})
    void routeOpensItsPane(String route, String expectedTitle, String language) throws Exception {
        AtomicReference<String> expanded = new AtomicReference<>();
        fx(() -> {
            I18nService.getInstance().setPreference(LanguagePreference.valueOf(language));
            shell.navigateToModule(route);
            root.applyCss();
            root.layout();
            Accordion accordion = findAccordion(root.lookup("#paymentsContainer"));
            assertNotNull(accordion);
            TitledPane pane = accordion.getExpandedPane();
            expanded.set(pane == null ? "<none>" : pane.getText());
        });
        assertTrue(expanded.get().contains(expectedTitle), route + " expanded " + expanded.get());
    }

    private static Accordion findAccordion(Node node) {
        if (node instanceof Accordion accordion) return accordion;
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Accordion found = findAccordion(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    @FunctionalInterface
    private interface FxAction { void run() throws Exception; }

    private static void fx(FxAction action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(45, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
}
