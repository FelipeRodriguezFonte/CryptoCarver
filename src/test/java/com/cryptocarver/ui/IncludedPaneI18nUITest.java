package com.cryptocarver.ui;

import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Labeled;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TitledPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Panes included into another module must translate both their title and their contents.
 *
 * <p>{@code ModuleI18n} indexes from the node it is handed downwards, once, on the first
 * refresh. A {@code TitledPane}'s content is not among its children until the skin is
 * built, which has not happened at that point. So binding the pane alone translates only
 * the title, and binding the inner container alone translates everything except the
 * title — and the title is what the user reads while the pane is collapsed.</p>
 */
@Tag("ui")
class IncludedPaneI18nUITest {

    @BeforeAll
    static void startToolkit() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyRunning) {
            latch.countDown();
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS), "JavaFX toolkit failed to start");
    }

    private static void onFxThread(ThrowingRunnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(20, TimeUnit.SECONDS), "FX action timed out");
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    /** Every Labeled under {@code root}, including the root itself. */
    private static List<String> labelTexts(Node root) {
        List<String> texts = new ArrayList<>();
        collect(root, texts);
        return texts;
    }

    private static void collect(Node node, List<String> texts) {
        if (node == null) return;
        if (node instanceof Labeled labeled && labeled.getText() != null) {
            texts.add(labeled.getText());
        }
        if (node instanceof TitledPane pane) {
            collect(pane.getContent(), texts);
        }
        if (node instanceof TabPane tabPane) {
            // Tab content is not among the TabPane's children until its skin exists.
            for (Tab tab : tabPane.getTabs()) {
                if (tab.getText() != null) texts.add(tab.getText());
                collect(tab.getContent(), texts);
            }
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) collect(child, texts);
        }
    }

    private static void assertTranslates(String resource, String englishTitle,
                                         String spanishTitle, String spanishContentFragment)
            throws Exception {
        onFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(
                    IncludedPaneI18nUITest.class.getResource(resource));
            assertNotNull(loader.getLocation(), "missing resource " + resource);
            loader.load();
            TitledPane pane = loader.getRoot() instanceof TitledPane titled ? titled : null;
            assertNotNull(pane, resource + " is expected to have a TitledPane root");

            I18nService.getInstance().setPreference(LanguagePreference.EN);
            assertTrue(pane.getText().contains(englishTitle),
                    resource + " English title: " + pane.getText());

            I18nService.getInstance().setPreference(LanguagePreference.ES);
            assertTrue(pane.getText().contains(spanishTitle),
                    resource + " title does not follow the language: " + pane.getText());

            List<String> texts = labelTexts(pane);
            assertTrue(texts.stream().anyMatch(text -> text.contains(spanishContentFragment)),
                    resource + " content does not follow the language; labels were: " + texts);
        });
    }

    @Test
    void thePkcs11ProfilesPaneTranslatesItsTitleAndItsContents() throws Exception {
        assertTranslates("/fxml/pkcs11_profiles.fxml",
                "PKCS#11 Profiles", "Perfiles PKCS#11", "Nombre del perfil");
    }

    @Test
    void theCryptoEnvelopeInspectorTranslatesItsTitleAndItsContents() throws Exception {
        assertTranslates("/fxml/crypto_envelope_inspector.fxml",
                "Crypto Envelope Inspector", "Inspector de Crypto Envelope", "Cabecera");
    }

    @Test
    void theIcsfPanesTranslateBothHalves() throws Exception {
        assertTranslates("/fxml/icsf_token.fxml",
                "Key Token Analyzer", "Analizador de key tokens", "Key token (hexadecimal)");
        assertTranslates("/fxml/icsf_batch.fxml",
                "Batch Analysis", "Analisis en lote", "Filtro:");
    }
}
