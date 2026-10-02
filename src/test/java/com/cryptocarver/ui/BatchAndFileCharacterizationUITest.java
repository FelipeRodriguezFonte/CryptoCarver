package com.cryptocarver.ui;

import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Batch Runner and File Conversion panes of generic.fxml, pinned before they move out of
 * GenericController. Paths are normalised; each transcript is fixed with SHA-256.
 */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class BatchAndFileCharacterizationUITest {
    private static final String KEY_256 = "000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F";

    @TempDir Path dir;

    @BeforeAll
    static void startFx() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try {
            Platform.startup(ready::countDown);
        } catch (IllegalStateException alreadyStarted) {
            ready.countDown();
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
    }

    @Test
    void fileConversionsAndStreamingTools() throws Exception {
        Path binary = dir.resolve("data.bin");
        Files.write(binary, "Hello, file conversion!\nSecond line é".getBytes(StandardCharsets.UTF_8));
        Path hex = dir.resolve("data.hex");
        Files.writeString(hex, "48 65 6C 6C 6F");
        Path badHex = dir.resolve("bad.hex");
        Files.writeString(badHex, "ZZ");
        Path b64 = dir.resolve("data.b64");
        Files.writeString(b64, "SGVsbG8gd29ybGQ=");
        Path copy = dir.resolve("copy.bin");
        Files.copy(binary, copy);
        Path other = dir.resolve("other.bin");
        Files.write(other, "Hello, FILE".getBytes(StandardCharsets.UTF_8));
        List<String> transcript = new ArrayList<>();
        withPanel(panel -> {
            transcript.add("formats " + panel.combo("fileInputFormatCombo").getItems() + " "
                    + panel.combo("fileOutputFormatCombo").getItems() + " " + panel.combo("fileEncodingCombo").getItems()
                    + " encodingDisabled=" + panel.combo("fileEncodingCombo").isDisable());
            String[][] cases = {
                    {binary.toString(), "", "Binary", "Hex"},
                    {binary.toString(), dir.resolve("out.hex").toString(), "Binary", "Hex"},
                    {hex.toString(), dir.resolve("out.bin").toString(), "Hex", "Binary"},
                    {b64.toString(), "", "Base64", "Text"},
                    {binary.toString(), "", "Text", "Base64"},
                    {binary.toString(), "", "Binary", "Binary"},
                    {badHex.toString(), "", "Hex", "Text"},
                    {dir.resolve("missing.bin").toString(), "", "Binary", "Hex"},
                    {"", "", "Binary", "Hex"}};
            for (String[] each : cases) {
                panel.set("fileInputPathField", each[0]);
                panel.set("fileOutputPathField", each[1]);
                panel.combo("fileInputFormatCombo").setValue(each[2]);
                panel.combo("fileOutputFormatCombo").setValue(each[3]);
                if ("Text".equals(each[2]) || "Text".equals(each[3])) panel.combo("fileEncodingCombo").setValue("ISO-8859-1");
                panel.controller().handleConvertFile();
                transcript.add(panel.step("convert " + each[2] + "->" + each[3] + (each[1].isEmpty() ? "" : " to file"),
                        "fileResultArea") + " encodingDisabled=" + panel.combo("fileEncodingCombo").isDisable());
            }
            transcript.add("written hex " + read(dir.resolve("out.hex")) + " bin " + read(dir.resolve("out.bin")));

            panel.set("fileInputPathField", binary.toString());
            panel.set("fileComparePathField", copy.toString());
            panel.controller().handleCompareFiles();
            transcript.add(panel.step("compare identical", "fileResultArea"));
            panel.set("fileComparePathField", other.toString());
            panel.controller().handleCompareFiles();
            transcript.add(panel.step("compare different", "fileResultArea"));
            panel.set("fileComparePathField", "");
            panel.controller().handleCompareFiles();
            transcript.add(panel.step("compare missing", null));
            panel.controller().handleHashFileStreaming();
            transcript.add(panel.step("hash", "fileResultArea"));
            panel.controller().handlePreviewFileStreaming();
            transcript.add(panel.step("preview", "fileResultArea"));
            panel.set("fileInputPathField", "");
            panel.controller().handleHashFileStreaming();
            panel.controller().handlePreviewFileStreaming();
            transcript.add(panel.step("hash and preview without file", null));
        });
        assertEquals("ff370697db063e5eaa4bde2e552ff50c637ceffa50235c3dbddc29e4a99fd0a1", digest(transcript), String.join("\n", transcript));
    }

    @Test
    void conversionWithoutOutputPathPreviewsAndFailuresClearTheResult() throws Exception {
        Path binary = dir.resolve("small.bin");
        Files.write(binary, new byte[] {1, 2, 3});
        withPanel(panel -> {
            panel.set("fileInputPathField", binary.toString());
            panel.set("fileOutputPathField", "");
            panel.combo("fileInputFormatCombo").setValue("Binary");
            panel.combo("fileOutputFormatCombo").setValue("Hex");
            panel.controller().handleConvertFile();
            assertTrue(panel.text("fileResultArea").contains("010203"), panel.text("fileResultArea"));
            assertEquals(1, panel.reporter().drain().lines().filter(line -> line.startsWith("publish")).count());

            panel.set("fileInputPathField", dir.resolve("absent.bin").toString());
            panel.controller().handleConvertFile();
            assertEquals("", panel.text("fileResultArea"));
            assertTrue(panel.reporter().drain().contains("File not found"));
        });
    }

    @Test
    void batchDryRunsRunsAndErrors() throws Exception {
        List<String> transcript = new ArrayList<>();
        withPanel(panel -> {
            transcript.add("operations " + panel.combo("batchOperationCombo").getItems());
            transcript.add("crypto box " + ((VBox) panel.node("batchCryptoConfigBox")).isVisible());
            panel.combo("batchInputFormatCombo").setValue("CSV");
            panel.set("batchInputArea", "input,id\nhello,1\nworld,2\n");
            panel.set("batchColumnField", "input");
            panel.set("batchOutputColumnField", "result");
            panel.combo("batchOperationCombo").setValue(panel.combo("batchOperationCombo").getItems().get(0));
            panel.controller().handleDryRunBatch();
            transcript.add(panel.batchStep("dry run first operation"));
            panel.set("batchInputArea", "input\n\"unterminated");
            panel.controller().handleDryRunBatch();
            transcript.add(panel.batchStep("dry run invalid csv"));
        });
        for (String operation : List.of("SHA-256 (UTF-8 → Hex)", "Encrypt Record")) {
            withPanel(panel -> {
                panel.combo("batchInputFormatCombo").setValue("JSON Lines (.jsonl)");
                panel.set("batchInputArea", "{\"input\":\"hello\"}\n{\"input\":\"world\"}\n");
                panel.set("batchColumnField", "input");
                panel.set("batchOutputColumnField", "result");
                panel.combo("batchOperationCombo").setValue(operation);
                transcript.add("crypto box " + ((VBox) panel.node("batchCryptoConfigBox")).isVisible());
                panel.set("batchKeyField", KEY_256);
                panel.set("batchIvNonceField", "");
                panel.controller().handleDryRunBatch();
                transcript.add(panel.batchStep("dry run " + operation));
                panel.controller().handleRunBatch();
            });
            awaitBatch();
            withLastPanel(panel -> {
                String result = panel.text("batchResultArea");
                transcript.add("## run " + operation + "\n" + (operation.startsWith("Encrypt")
                        ? result.replaceAll("(#\\d+ OK  )\\S+", "$1<random>") : result)
                        + "\nstatus label " + panel.label("batchStatusLabel") + "\nkey after run '" + panel.text("batchKeyField") + "'"
                        + "\n" + panel.reporter().drain());
            });
        }
        withPanel(panel -> {
            panel.set("batchColumnField", "");
            panel.controller().handleRunBatch();
            transcript.add(panel.batchStep("no columns"));
            panel.set("batchColumnField", "missing");
            panel.set("batchOutputColumnField", "result");
            panel.set("batchInputArea", "input\nhello\n");
            panel.controller().handleRunBatch();
            transcript.add(panel.batchStep("column absent"));
            panel.set("batchInputArea", "");
            panel.controller().handleRunBatch();
            transcript.add(panel.batchStep("no rows"));
            panel.set("batchColumnField", "input");
            panel.set("batchInputArea", "input\nhello\n");
            panel.combo("batchOperationCombo").setValue("Encrypt Record");
            panel.set("batchKeyField", "00");
            panel.controller().handleRunBatch();
            transcript.add(panel.batchStep("bad key"));
            panel.controller().handleExportBatchResults();
            transcript.add(panel.batchStep("export without results"));
            panel.controller().handleCancelBatch();
            transcript.add(panel.batchStep("cancel when idle"));
            panel.set("batchKeyField", KEY_256);
            panel.controller().handleResetBatch();
            transcript.add(panel.batchStep("reset") + " input='" + panel.text("batchInputArea") + "' key='"
                    + panel.text("batchKeyField") + "'");
        });
        assertEquals("3a3cabfd064274cb44860c48e67ba320a4439755be12f5aa03a65175d29e5e2f", digest(transcript), String.join("\n", transcript));
    }

    private Panel lastPanel;

    private void awaitBatch() throws Exception {
        for (int i = 0; i < 200; i++) {
            AtomicReference<String> label = new AtomicReference<>();
            onFx(() -> label.set(lastPanel.label("batchStatusLabel")));
            if (!label.get().toLowerCase().contains("process")) return;
            Thread.sleep(50);
        }
        throw new AssertionError("batch did not finish");
    }

    private void withLastPanel(Consumer<Panel> body) throws Exception {
        onFx(() -> body.accept(lastPanel));
    }

    private static String read(Path path) {
        try {
            return HexFormat.of().formatHex(Files.readAllBytes(path));
        } catch (Exception e) {
            return "<" + e.getClass().getSimpleName() + ">";
        }
    }

    private static String digest(List<String> transcript) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(String.join("\n", transcript).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    private void withPanel(Consumer<Panel> body) throws Exception {
        onFx(() -> {
            try {
                FXMLLoader loader = UiTestFxml.loader("/fxml/generic.fxml");
                loader.load();
                GenericController controller = loader.getController();
                Recorder reporter = new Recorder(dir);
                controller.setStatusReporter(reporter);
                lastPanel = new Panel(loader, controller, reporter);
                body.accept(lastPanel);
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        });
    }

    private static void onFx(Runnable action) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(60, TimeUnit.SECONDS), "FX thread did not complete");
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private record Panel(FXMLLoader loader, GenericController controller, Recorder reporter) {
        Object node(String id) { return loader.getNamespace().get(id); }
        @SuppressWarnings("unchecked")
        ComboBox<String> combo(String id) { return (ComboBox<String>) node(id); }
        void set(String id, String value) { ((TextInputControl) node(id)).setText(value); }
        String text(String id) { return ((TextInputControl) node(id)).getText(); }
        String label(String id) { return ((Label) node(id)).getText(); }

        String step(String name, String resultArea) {
            return "## " + name + (resultArea == null ? "" : "\n" + reporter.normalise(text(resultArea)))
                    + "\n" + reporter.drain();
        }

        String batchStep(String name) {
            return "## " + name + "\n" + text("batchResultArea") + "\nstatus label " + label("batchStatusLabel")
                    + "\n" + reporter.drain();
        }
    }

    private static final class Recorder implements StatusReporter {
        private final Path dir;
        private final List<String> lines = new ArrayList<>();

        Recorder(Path dir) {
            this.dir = dir;
        }

        String normalise(String value) {
            return value == null ? "" : value.replace(dir.toString(), "<DIR>");
        }

        synchronized String drain() {
            String text = String.join("\n", lines);
            lines.clear();
            return text;
        }

        @Override
        public synchronized void updateStatus(String message) {
            lines.add("status " + normalise(message));
        }

        @Override
        public void updateInspector(String operation, byte[] input, byte[] output, List<OperationDetail> details) {
        }

        @Override
        public synchronized void showError(String title, String message) {
            lines.add("error " + title + ": " + normalise(message));
        }

        @Override
        public synchronized void publish(OperationResult result) {
            List<String> details = result.getDetails().stream()
                    .map(detail -> detail.name() + "=" + normalise(detail.value()))
                    .toList();
            lines.add("publish " + result.getOperation() + "|" + normalise(result.getStatusMessage()) + "|"
                    + String.join(",", details) + "|out=" + (result.getOutput() == null ? 0 : result.getOutput().length));
        }
    }
}
