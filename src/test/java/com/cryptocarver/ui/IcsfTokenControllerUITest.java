package com.cryptocarver.ui;

import com.cryptocarver.crypto.icsf.IcsfHex;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loads the ICSF pane in a real JavaFX runtime and drives it.
 *
 * <p>Opt-in, like the rest of the UI suite. The static FXML gate checks that the
 * controller and its handlers resolve; only actually loading the file catches a
 * bad generic binding on a typed ComboBox or a missing import.</p>
 */
@Tag("ui")
class IcsfTokenControllerUITest {

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

    /** Runs {@code action} on the FX thread and rethrows whatever it threw. */
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

    private static FXMLLoader load() throws Exception {
        FXMLLoader loader = new FXMLLoader(
                IcsfTokenControllerUITest.class.getResource("/fxml/icsf_token.fxml"));
        assertNotNull(loader.getLocation(), "icsf_token.fxml must be on the classpath");
        loader.load();
        return loader;
    }

    @Test
    void thePaneLoadsAndWiresEveryControl() throws Exception {
        onFxThread(() -> {
            FXMLLoader loader = load();
            TitledPane pane = loader.getRoot();
            IcsfTokenController controller = loader.getController();

            assertNotNull(pane);
            assertNotNull(controller);
            assertTrue(pane.getText().contains("ICSF"));

            assertNotNull(field(loader, "icsfTokenInputArea", TextArea.class));
            assertNotNull(field(loader, "icsfTokenDetailArea", TextArea.class));
            assertNotNull(field(loader, "icsfTokenOriginCombo", ComboBox.class));
            assertNotNull(field(loader, "icsfTokenFormatCombo", ComboBox.class));
            assertNotNull(field(loader, "icsfTokenSummaryTable", TableView.class));
            assertNotNull(field(loader, "icsfTokenFeedbackLabel", Label.class));
        });
    }

    @Test
    void analysingATokenFillsTheSummaryCardAndTheDetail() throws Exception {
        onFxThread(() -> {
            FXMLLoader loader = load();
            IcsfTokenController controller = loader.getController();

            // A double-length IMPORTER with Table 676's default Control Vector.
            byte[] token = importerToken();
            field(loader, "icsfTokenInputArea", TextArea.class).setText(IcsfHex.hex(token));
            invoke(controller, "handleAnalyze");

            TableView<?> summary = field(loader, "icsfTokenSummaryTable", TableView.class);
            String detail = field(loader, "icsfTokenDetailArea", TextArea.class).getText();

            assertFalse(summary.getItems().isEmpty(), "the summary card must be populated");
            assertTrue(detail.contains("IMPORTER"), detail.substring(0, Math.min(400, detail.length())));
            assertTrue(detail.contains("FIELD-BY-FIELD DETAIL"));
            assertTrue(detail.contains("SECURITY:"), "the saved report must carry the security notice");
        });
    }

    @Test
    void aTokenThatIsNotHexIsReportedInlineWithoutThrowing() throws Exception {
        onFxThread(() -> {
            FXMLLoader loader = load();
            IcsfTokenController controller = loader.getController();

            field(loader, "icsfTokenInputArea", TextArea.class).setText("ZZZZ");
            invoke(controller, "handleAnalyze");

            Label feedback = field(loader, "icsfTokenFeedbackLabel", Label.class);
            assertTrue(feedback.getText().toLowerCase().contains("hexadecimal"), feedback.getText());
            assertTrue(field(loader, "icsfTokenSummaryTable", TableView.class).getItems().isEmpty());
        });
    }

    @Test
    void theTwoRowShapeIsAcceptedFromTheUi() throws Exception {
        onFxThread(() -> {
            FXMLLoader loader = load();
            IcsfTokenController controller = loader.getController();
            byte[] token = importerToken();

            @SuppressWarnings("unchecked")
            ComboBox<IcsfTokenController.InputShape> shape =
                    field(loader, "icsfTokenFormatCombo", ComboBox.class);
            shape.setValue(IcsfTokenController.InputShape.TWO_ROW);
            field(loader, "icsfTokenInputArea", TextArea.class).setText(IcsfHex.toTwoRows(token));
            invoke(controller, "handleAnalyze");

            assertTrue(field(loader, "icsfTokenDetailArea", TextArea.class)
                    .getText().contains("IMPORTER"));
        });
    }

    @Test
    void clearAndResetEmptyTheView() throws Exception {
        onFxThread(() -> {
            FXMLLoader loader = load();
            IcsfTokenController controller = loader.getController();

            field(loader, "icsfTokenInputArea", TextArea.class).setText(IcsfHex.hex(importerToken()));
            invoke(controller, "handleAnalyze");
            invoke(controller, "handleReset");

            assertTrue(field(loader, "icsfTokenInputArea", TextArea.class).getText().isEmpty());
            assertTrue(field(loader, "icsfTokenDetailArea", TextArea.class).getText().isEmpty());
            assertTrue(field(loader, "icsfTokenSummaryTable", TableView.class).getItems().isEmpty());
        });
    }

    @Test
    void thePaneTitleFollowsTheSelectedLanguage() throws Exception {
        onFxThread(() -> {
            FXMLLoader loader = load();
            TitledPane pane = loader.getRoot();

            I18nService.getInstance().setPreference(LanguagePreference.ES);
            assertTrue(pane.getText().contains("Analizador"), pane.getText());

            I18nService.getInstance().setPreference(LanguagePreference.EN);
            assertTrue(pane.getText().contains("Analyzer"), pane.getText());
        });
    }

    @Test
    void theSummaryCardIsTranslatedButTheKeyTypeStaysTechnical() throws Exception {
        onFxThread(() -> {
            FXMLLoader loader = load();
            IcsfTokenController controller = loader.getController();

            I18nService.getInstance().setPreference(LanguagePreference.ES);
            field(loader, "icsfTokenInputArea", TextArea.class).setText(IcsfHex.hex(importerToken()));
            invoke(controller, "handleAnalyze");

            @SuppressWarnings("unchecked")
            TableView<IcsfTokenController.SummaryRow> summary =
                    field(loader, "icsfTokenSummaryTable", TableView.class);

            boolean translatedDimension = summary.getItems().stream()
                    .anyMatch(row -> "Ambito".equals(row.field()));
            boolean technicalKeyType = summary.getItems().stream()
                    .anyMatch(row -> "IMPORTER".equals(row.value()));

            assertTrue(translatedDimension, "dimension labels must follow the language");
            assertTrue(technicalKeyType, "a Table 676 key type is a technical identifier, not a word");
        });
    }

    // --- helpers ---------------------------------------------------------
    private static <T> T field(FXMLLoader loader, String id, Class<T> type) {
        Object node = loader.getNamespace().get(id);
        assertNotNull(node, "missing fx:id " + id);
        return type.cast(node);
    }

    private static void invoke(Object target, String method) throws Exception {
        var handle = target.getClass().getDeclaredMethod(method);
        handle.setAccessible(true);
        handle.invoke(target);
    }

    /** A double-length IMPORTER built the way the core's own fixtures build one. */
    private static byte[] importerToken() {
        byte[] token = new byte[64];
        token[0] = 0x02;
        token[4] = 0x01;
        token[6] = (byte) 0xC0;
        for (int index = 0; index < 16; index++) {
            token[16 + index] = (byte) ((0x11 * (index + 1)) & 0xFF);
        }
        System.arraycopy(IcsfHex.clean("00427D0003410000"), 0, token, 32, 8);
        System.arraycopy(IcsfHex.clean("00427D0003210000"), 0, token, 40, 8);
        long tvv = com.cryptocarver.crypto.icsf.IcsfTvv.compute(token);
        token[60] = (byte) ((tvv >>> 24) & 0xFF);
        token[61] = (byte) ((tvv >>> 16) & 0xFF);
        token[62] = (byte) ((tvv >>> 8) & 0xFF);
        token[63] = (byte) (tvv & 0xFF);
        return token;
    }
}
