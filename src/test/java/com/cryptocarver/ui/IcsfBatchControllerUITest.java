package com.cryptocarver.ui;

import com.cryptocarver.crypto.icsf.FindingCode;
import com.cryptocarver.crypto.icsf.IcsfHex;
import com.cryptocarver.crypto.icsf.IcsfTvv;
import com.cryptocarver.crypto.icsf.InventoryColumn;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Loads the ICSF batch pane in a real JavaFX runtime and drives its four views. */
@Tag("ui")
class IcsfBatchControllerUITest {

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

    private static FXMLLoader load() throws Exception {
        FXMLLoader loader = new FXMLLoader(
                IcsfBatchControllerUITest.class.getResource("/fxml/icsf_batch.fxml"));
        assertNotNull(loader.getLocation(), "icsf_batch.fxml must be on the classpath");
        loader.load();
        return loader;
    }

    @Test
    void thePaneLoadsWithItsFourViews() throws Exception {
        onFxThread(() -> {
            FXMLLoader loader = load();
            TitledPane pane = loader.getRoot();

            assertNotNull(loader.getController());
            assertTrue(pane.getText().contains("ICSF"));
            assertEquals(4, field(loader, "icsfBatchTabs", TabPane.class).getTabs().size());
            assertNotNull(field(loader, "icsfBatchStatisticsTable", TableView.class));
            assertNotNull(field(loader, "icsfBatchFindingsTable", TableView.class));
            assertNotNull(field(loader, "icsfBatchInventoryTable", TableView.class));
            assertNotNull(field(loader, "icsfBatchReportArea", TextArea.class));
        });
    }

    @Test
    void theInventoryTableIsBuiltFromTheModelSoItCannotDrift() throws Exception {
        onFxThread(() -> {
            FXMLLoader loader = load();
            TableView<?> inventory = field(loader, "icsfBatchInventoryTable", TableView.class);

            assertEquals(InventoryColumn.values().length, inventory.getColumns().size());
            assertEquals(18, inventory.getColumns().size());
            for (int index = 0; index < InventoryColumn.values().length; index++) {
                assertEquals(InventoryColumn.values()[index], inventory.getColumns().get(index).getUserData());
            }
        });
    }

    @Test
    void analysingABatchFillsEveryView() throws Exception {
        onFxThread(() -> {
            FXMLLoader loader = load();
            IcsfBatchController controller = loader.getController();

            field(loader, "icsfBatchInputArea", TextArea.class).setText(sampleBatch());
            invoke(controller, "handleAnalyze");

            assertFalse(field(loader, "icsfBatchStatisticsTable", TableView.class).getItems().isEmpty());
            assertFalse(field(loader, "icsfBatchFindingsTable", TableView.class).getItems().isEmpty());
            assertEquals(4, field(loader, "icsfBatchInventoryTable", TableView.class).getItems().size());

            String report = field(loader, "icsfBatchReportArea", TextArea.class).getText();
            assertTrue(report.contains("STATISTICS"));
            assertTrue(report.contains("AUDIT FINDINGS"));
            assertTrue(report.contains("INVENTORY"));
            // The report shown on screen is the one that gets saved, notice and all.
            assertTrue(report.contains("decrypts nothing"));
        });
    }

    @Test
    void theFilterNarrowsTheInventory() throws Exception {
        onFxThread(() -> {
            FXMLLoader loader = load();
            IcsfBatchController controller = loader.getController();
            field(loader, "icsfBatchInputArea", TextArea.class).setText(sampleBatch());
            invoke(controller, "handleAnalyze");

            TableView<?> inventory = field(loader, "icsfBatchInventoryTable", TableView.class);
            assertEquals(4, inventory.getItems().size());

            field(loader, "icsfBatchFilterField", TextField.class).setText("SINGLE");
            assertTrue(inventory.getItems().size() < 4, "the filter must narrow the inventory");
            assertFalse(inventory.getItems().isEmpty());

            invoke(controller, "handleClearFilter");
            assertEquals(4, inventory.getItems().size());
        });
    }

    @Test
    void filteringByAFindingSwitchesToTheInventoryAndNarrowsIt() throws Exception {
        onFxThread(() -> {
            FXMLLoader loader = load();
            IcsfBatchController controller = loader.getController();
            field(loader, "icsfBatchInputArea", TextArea.class).setText(sampleBatch());
            invoke(controller, "handleAnalyze");

            controller.filterByFinding(FindingCode.DES_56_BITS);

            TabPane tabs = field(loader, "icsfBatchTabs", TabPane.class);
            assertEquals("icsfBatchInventoryTab", tabs.getSelectionModel().getSelectedItem().getId());
            assertEquals("DES-56-BITS", field(loader, "icsfBatchFilterField", TextField.class).getText());
            assertEquals(1, controller.visibleInventory().size(),
                    "only the single-length DES key raises DES-56-BITS in this batch");
        });
    }

    @Test
    void selectingAFindingExplainsWhatItIsAndWhatToDo() throws Exception {
        onFxThread(() -> {
            FXMLLoader loader = load();
            IcsfBatchController controller = loader.getController();
            field(loader, "icsfBatchInputArea", TextArea.class).setText(sampleBatch());
            invoke(controller, "handleAnalyze");

            @SuppressWarnings("unchecked")
            TableView<IcsfBatchController.FindingRow> findings =
                    field(loader, "icsfBatchFindingsTable", TableView.class);
            int row = -1;
            for (int index = 0; index < findings.getItems().size(); index++) {
                if (findings.getItems().get(index).code() == FindingCode.BYTE59_FUERA_DE_TABLA) row = index;
            }
            assertTrue(row >= 0, "the batch must raise BYTE59-FUERA-DE-TABLA");
            findings.getSelectionModel().select(row);

            String detail = field(loader, "icsfBatchFindingDetailArea", TextArea.class).getText();
            assertTrue(detail.contains("BYTE59-FUERA-DE-TABLA"));
            assertTrue(detail.contains("SUBDIVID"),
                    "the explanation must keep the point that older levels subdivided that byte");
            assertTrue(detail.contains("Tokens:"));
        });
    }

    @Test
    void doubleClickingAFindingRowFiltersTheInventory() throws Exception {
        onFxThread(() -> {
            FXMLLoader loader = load();
            IcsfBatchController controller = loader.getController();
            field(loader, "icsfBatchInputArea", TextArea.class).setText(sampleBatch());
            invoke(controller, "handleAnalyze");

            @SuppressWarnings("unchecked")
            TableView<IcsfBatchController.FindingRow> findings =
                    field(loader, "icsfBatchFindingsTable", TableView.class);
            int row = -1;
            for (int index = 0; index < findings.getItems().size(); index++) {
                if (findings.getItems().get(index).code() == FindingCode.DES_56_BITS) row = index;
            }
            assertTrue(row >= 0);
            findings.getSelectionModel().select(row);

            // Fire the real event rather than calling the method behind it, so the
            // handler wiring itself is covered and not just the code it delegates to.
            findings.fireEvent(new javafx.scene.input.MouseEvent(
                    javafx.scene.input.MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
                    javafx.scene.input.MouseButton.PRIMARY, 2,
                    false, false, false, false, true, false, false, true, false, true, null));

            assertEquals("DES-56-BITS",
                    field(loader, "icsfBatchFilterField", TextField.class).getText());
            assertEquals("icsfBatchInventoryTab",
                    field(loader, "icsfBatchTabs", TabPane.class)
                            .getSelectionModel().getSelectedItem().getId());
        });
    }

    @Test
    void theReportTabSaysWhatItIsShowing() throws Exception {
        onFxThread(() -> {
            FXMLLoader loader = load();
            IcsfBatchController controller = loader.getController();
            field(loader, "icsfBatchInputArea", TextArea.class).setText(sampleBatch());
            invoke(controller, "handleAnalyze");

            String report = field(loader, "icsfBatchReportArea", TextArea.class).getText();
            // The screen shows the summary; the saved file may carry the detail as well,
            // and the pane states that rather than leaving it to be inferred.
            assertFalse(report.contains("FULL PER-TOKEN DETAIL"));
            assertTrue(report.contains("INVENTORY"));
        });
    }

    @Test
    void theSecurityNoticeIsPermanentAndTranslated() throws Exception {
        onFxThread(() -> {
            FXMLLoader loader = load();
            Label security = field(loader, "icsfBatchSecurityLabel", Label.class);

            I18nService.getInstance().setPreference(LanguagePreference.EN);
            assertTrue(security.getText().contains("decrypts nothing"), security.getText());
            assertTrue(security.isVisible(), "the notice must not be dismissible");

            I18nService.getInstance().setPreference(LanguagePreference.ES);
            assertTrue(security.getText().contains("no descifra nada"), security.getText());
            assertTrue(security.getText().contains("master key"));
        });
    }

    @Test
    void theInventoryHeadersFollowTheSelectedLanguage() throws Exception {
        onFxThread(() -> {
            FXMLLoader loader = load();
            TableView<?> inventory = field(loader, "icsfBatchInventoryTable", TableView.class);

            I18nService.getInstance().setPreference(LanguagePreference.ES);
            assertTrue(inventory.getColumns().stream()
                            .anyMatch(column -> "Hallazgos".equals(column.getText())),
                    "column headers must be rebuilt on a language change");

            I18nService.getInstance().setPreference(LanguagePreference.EN);
            assertTrue(inventory.getColumns().stream()
                    .anyMatch(column -> "Findings".equals(column.getText())));
        });
    }

    @Test
    void anEmptyBatchIsRefusedInlineWithoutThrowing() throws Exception {
        onFxThread(() -> {
            FXMLLoader loader = load();
            IcsfBatchController controller = loader.getController();

            invoke(controller, "handleAnalyze");

            assertFalse(field(loader, "icsfBatchFeedbackLabel", Label.class).getText().isBlank());
            assertTrue(controller.items().isEmpty());
        });
    }

    @Test
    void resetEmptiesEveryView() throws Exception {
        onFxThread(() -> {
            FXMLLoader loader = load();
            IcsfBatchController controller = loader.getController();
            field(loader, "icsfBatchInputArea", TextArea.class).setText(sampleBatch());
            invoke(controller, "handleAnalyze");
            invoke(controller, "handleReset");

            assertTrue(field(loader, "icsfBatchInputArea", TextArea.class).getText().isEmpty());
            assertTrue(field(loader, "icsfBatchStatisticsTable", TableView.class).getItems().isEmpty());
            assertTrue(field(loader, "icsfBatchFindingsTable", TableView.class).getItems().isEmpty());
            assertTrue(field(loader, "icsfBatchInventoryTable", TableView.class).getItems().isEmpty());
            assertTrue(field(loader, "icsfBatchReportArea", TextArea.class).getText().isEmpty());
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

    /** Four tokens covering a clean key, an odd byte 59, a 56-bit DES key and a bad line. */
    private static String sampleBatch() {
        return String.join("\n",
                "# a sample batch",
                "KEK.IMPORTER.01|" + IcsfHex.hex(des("00427D0003410000", "00427D0003210000", 16, null)),
                "BYTE59.ODD|" + IcsfHex.hex(des("00427D0003410000", "00427D0003210000", 16, 0x40)),
                "DATA.LEGACY|" + IcsfHex.hex(des(null, null, 8, null)),
                "NOT-HEX-AT-ALL");
    }

    private static byte[] des(String cvLeft, String cvRight, int keyLength, Integer byte59) {
        byte[] token = new byte[64];
        token[0] = 0x02;
        token[4] = (byte) (keyLength == 8 ? 0x00 : 0x01);
        token[6] = (byte) (cvLeft == null ? 0x80 : 0xC0);
        for (int index = 0; index < keyLength; index++) {
            int target = index < 8 ? 16 + index : 24 + (index - 8);
            token[target] = (byte) ((0x11 * (index + 1)) & 0xFF);
        }
        if (cvLeft != null) {
            System.arraycopy(IcsfHex.clean(cvLeft), 0, token, 32, 8);
            if (keyLength > 8) System.arraycopy(IcsfHex.clean(cvRight), 0, token, 40, 8);
        } else {
            token[59] = (byte) (keyLength == 8 ? 0x00 : 0x10);
        }
        if (byte59 != null) token[59] = byte59.byteValue();
        long tvv = IcsfTvv.compute(token);
        token[60] = (byte) ((tvv >>> 24) & 0xFF);
        token[61] = (byte) ((tvv >>> 16) & 0xFF);
        token[62] = (byte) ((tvv >>> 8) & 0xFF);
        token[63] = (byte) (tvv & 0xFF);
        return token;
    }
}
