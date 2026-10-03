package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextInputControl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
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
 * Hashing and Manual Conversion in generic.fxml (formats, byte tools, EBCDIC, templates and
 * the shared toolbar contract), pinned before they move out of GenericController.
 */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class GenericConversionCharacterizationUITest {
    private static final List<String> TOOLBAR_FORMATS = List.of("Text (UTF-8)", "Hexadecimal", "Base64", "Base64URL",
            "Binary", "Decimal");
    private String previousCodePage;
    private String previousDirection;

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

    @BeforeEach
    void rememberEbcdicSettings() {
        previousCodePage = AppSettings.getInstance().getEBCDICCodePage();
        previousDirection = AppSettings.getInstance().getEBCDICDirection();
    }

    @AfterEach
    void restoreEbcdicSettings() {
        AppSettings.getInstance().setEBCDICCodePage(previousCodePage);
        AppSettings.getInstance().setEBCDICDirection(previousDirection);
    }

    @Test
    void hashingInEveryAlgorithmAndFormat() throws Exception {
        List<String> transcript = new ArrayList<>();
        withPanel(panel -> {
            transcript.add("algorithms " + panel.combo("hashAlgorithmCombo").getItems());
            for (String algorithm : panel.combo("hashAlgorithmCombo").getItems()) {
                panel.combo("hashAlgorithmCombo").setValue(algorithm);
                panel.toolbar("Text (UTF-8)", "Hexadecimal");
                panel.set("hashInputArea", "abc");
                panel.controller().handleCalculateHash();
                transcript.add(panel.step(algorithm, "hashOutputArea"));
            }
            panel.combo("hashAlgorithmCombo").setValue("SHA-256");
            String[][] formats = {{"Hexadecimal", "Base64", "616263"}, {"Base64", "Base64URL", "YWJj"},
                    {"Binary", "Decimal", "01100001 01100010 01100011"}, {"Hexadecimal", "Hexadecimal", "XYZ"},
                    {"Text (UTF-8)", "Hexadecimal", ""}};
            for (String[] each : formats) {
                panel.toolbar(each[0], each[1]);
                panel.set("hashInputArea", each[2]);
                panel.controller().handleCalculateHash();
                transcript.add(panel.step(each[0] + "->" + each[1] + " '" + each[2] + "'", "hashOutputArea"));
            }
            for (String template : List.of("SHA-256 — Text UTF-8 → Hex", "SHA-512 — Text UTF-8 → Base64")) {
                panel.combo("hashTemplateCombo").setValue(template);
                panel.invoke("handleApplyHashTemplate");
                transcript.add(panel.step("template " + template + " algorithm " + panel.combo("hashAlgorithmCombo").getValue(), null));
            }
            panel.invoke("handleResetHashDefaults");
            transcript.add(panel.step("reset algorithm " + panel.combo("hashAlgorithmCombo").getValue(), null));
        });
        assertEquals("8dc1bbc417bb37d19aba7ffedf9f011ab73e0df3a245e2fa60edb5c352922ad6", digest(transcript), String.join("\n", transcript));
    }

    @Test
    void manualConversionMatrixAndByteTools() throws Exception {
        List<String> transcript = new ArrayList<>();
        withPanel(panel -> {
            transcript.add("formats " + panel.combo("manualInputFormatCombo").getItems());
            String[][] inputs = {{"Text (UTF-8)", "Hi é"}, {"Hexadecimal", "48 69"}, {"Base64", "SGk="},
                    {"Base64URL", "SGk"}, {"Binary", "0100100001101001"}, {"Decimal", "72 105"}};
            for (String[] input : inputs) {
                for (String output : panel.combo("manualOutputFormatCombo").getItems()) {
                    panel.combo("manualInputFormatCombo").setValue(input[0]);
                    panel.combo("manualOutputFormatCombo").setValue(output);
                    panel.set("manualInputArea", input[1]);
                    panel.controller().handleManualConvert();
                    transcript.add(panel.step(input[0] + "->" + output, "manualOutputArea"));
                }
            }
            panel.combo("manualInputFormatCombo").setValue("Hexadecimal");
            panel.set("manualInputArea", "ZZ");
            panel.controller().handleManualConvert();
            transcript.add(panel.step("bad hex", "manualOutputArea"));
            panel.set("manualInputArea", "");
            panel.controller().handleManualConvert();
            transcript.add(panel.step("empty", "manualOutputArea"));

            panel.combo("manualInputFormatCombo").setValue("Text (UTF-8)");
            panel.combo("manualOutputFormatCombo").setValue("Hexadecimal");
            String[][] tools = {{"handleEncodeBase64Url", "hello?>"}, {"handleDecodeBase64Url", "aGVsbG8_Pg"},
                    {"handleEncodeBase32", "hello"}, {"handleDecodeBase32", "NBSWY3DP"},
                    {"handleEncodeUrl", "a b&c=d/é"}, {"handleDecodeUrl", "a+b%26c%3Dd%2F%C3%A9"},
                    {"handleEncodeBcd", "12345"}, {"handleDecodeBcd", "012345"}, {"handleEncodeComp3", "-123"},
                    {"handleDecodeComp3", "123D"}, {"handleExtractTraceHex", "0000: 48 65 6C 6C 6F  Hello"},
                    {"handleDecodeBase32", "!!"}, {"handleDecodeBcd", "1A"}};
            for (String[] tool : tools) {
                panel.set("manualInputArea", tool[1]);
                panel.invoke(tool[0]);
                transcript.add(panel.step(tool[0] + " '" + tool[1] + "'", "manualOutputArea"));
            }
            panel.combo("manualInputFormatCombo").setValue("Hexadecimal");
            for (String size : panel.combo("endianWordSizeCombo").getItems()) {
                panel.combo("endianWordSizeCombo").setValue(size);
                panel.set("manualInputArea", "00112233445566778899AABBCCDDEEFF");
                panel.controller().handleConvertEndian();
                transcript.add(panel.step("endian " + size, "manualOutputArea"));
            }
            panel.set("bitShiftBitsField", "4");
            panel.set("manualInputArea", "0F F0");
            panel.controller().handleShiftLeft();
            transcript.add(panel.step("shift left", "manualOutputArea"));
            panel.controller().handleShiftRight();
            transcript.add(panel.step("shift right", "manualOutputArea"));
            panel.combo("manualInputFormatCombo").setValue("Text (UTF-8)");
            for (String format : panel.combo("compressionFormatCombo").getItems()) {
                panel.combo("compressionFormatCombo").setValue(format);
                panel.combo("manualOutputFormatCombo").setValue("Hexadecimal");
                panel.set("manualInputArea", "compress me compress me compress me");
                panel.controller().handleCompressData();
                String compressed = panel.text("manualOutputArea");
                transcript.add(panel.step("compress " + format, "manualOutputArea"));
                panel.combo("manualInputFormatCombo").setValue("Hexadecimal");
                panel.combo("manualOutputFormatCombo").setValue("Text (UTF-8)");
                panel.set("manualInputArea", compressed);
                panel.controller().handleDecompressData();
                transcript.add(panel.step("decompress " + format, "manualOutputArea"));
                panel.combo("manualInputFormatCombo").setValue("Text (UTF-8)");
            }
        });
        assertEquals("6871901310b20d63bf6f8829dbdde4088f650dda9818140f700ca963be408844", digest(transcript), String.join("\n", transcript));
    }

    @Test
    void ebcdicTemplatesAndToolbarContract() throws Exception {
        List<String> transcript = new ArrayList<>();
        withPanel(panel -> {
            CheckBox ebcdic = (CheckBox) panel.node("ebcdicConversionCheck");
            transcript.add("ebcdic disabled " + panel.combo("ebcdicCodePageCombo").isDisable() + " pages "
                    + panel.combo("ebcdicCodePageCombo").getItems().size());
            ebcdic.setSelected(true);
            panel.combo("ebcdicCodePageCombo").setValue("IBM037 — US/Canada");
            panel.combo("ebcdicDirectionCombo").setValue("Encode UTF-8 → EBCDIC");
            panel.combo("manualInputFormatCombo").setValue("Text (UTF-8)");
            panel.combo("manualOutputFormatCombo").setValue("Hexadecimal");
            panel.set("manualInputArea", "HELLO 123");
            panel.controller().handleManualConvert();
            String encoded = panel.text("manualOutputArea");
            transcript.add(panel.step("ebcdic encode", "manualOutputArea"));
            panel.combo("manualOutputFormatCombo").setValue("Text (UTF-8)");
            panel.controller().handleManualConvert();
            transcript.add(panel.step("ebcdic encode to text", "manualOutputArea"));
            panel.combo("ebcdicDirectionCombo").setValue("Decode EBCDIC → UTF-8");
            panel.combo("manualInputFormatCombo").setValue("Hexadecimal");
            panel.set("manualInputArea", encoded);
            panel.controller().handleManualConvert();
            transcript.add(panel.step("ebcdic decode", "manualOutputArea"));
            ebcdic.setSelected(false);

            for (String template : List.of("Convert Text UTF-8 → Base64", "Convert Hex → Text UTF-8")) {
                panel.combo("manualTemplateCombo").setValue(template);
                panel.invoke("handleApplyManualTemplate");
                transcript.add(panel.step("template " + template + " " + panel.combo("manualInputFormatCombo").getValue()
                        + "->" + panel.combo("manualOutputFormatCombo").getValue(), null));
            }
            panel.set("manualInputArea", "x");
            panel.invoke("handleResetManualDefaults");
            transcript.add(panel.step("reset " + panel.combo("manualInputFormatCombo").getValue() + "->"
                    + panel.combo("manualOutputFormatCombo").getValue() + " input '" + panel.text("manualInputArea") + "'", null));

            panel.controller().setActiveFormatContractOperation("Manual Conversion");
            panel.toolbar("Base64", "Binary");
            transcript.add("toolbar->manual " + panel.combo("manualInputFormatCombo").getValue() + "->"
                    + panel.combo("manualOutputFormatCombo").getValue());
            panel.combo("manualInputFormatCombo").setValue("Decimal");
            transcript.add("manual->toolbar " + panel.input.getValue() + "->" + panel.output.getValue());
            panel.toolbar("Hexadecimal", "Hexadecimal");
            panel.controller().setActiveFormatContractOperation("Hashing");
            panel.toolbar("Base64", "Decimal");
            transcript.add("inactive contract keeps manual " + panel.combo("manualInputFormatCombo").getValue());

            panel.controller().fillManualConversionInput("AQID", com.cryptocarver.model.ClipboardEntry.Format.BASE64);
            transcript.add("fill " + panel.text("manualInputArea") + " " + panel.combo("manualInputFormatCombo").getValue());
            panel.controller().fillHashInput("616263", com.cryptocarver.model.ClipboardEntry.Format.HEX);
            transcript.add("fill hash " + panel.text("hashInputArea") + "\n" + panel.reporter().drain());
        });
        assertEquals("ed3b2b11d2c5b8db178282571413e09d2af0495ed783d4cf5e8359791bfbeb1a", digest(transcript), String.join("\n", transcript));
    }

    private static String digest(List<String> transcript) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(String.join("\n", transcript).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    private void withPanel(Consumer<Panel> body) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                FXMLLoader loader = UiTestFxml.loader("/fxml/generic.fxml");
                loader.load();
                GenericController controller = loader.getController();
                ComboBox<String> input = new ComboBox<>();
                input.getItems().setAll(TOOLBAR_FORMATS);
                ComboBox<String> output = new ComboBox<>();
                output.getItems().setAll(TOOLBAR_FORMATS);
                Recorder reporter = new Recorder(input, output);
                controller.setStatusReporter(reporter);
                controller.setFormatControls(input, output);
                body.accept(new Panel(loader, controller, reporter, input, output));
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(60, TimeUnit.SECONDS), "FX thread did not complete");
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private record Panel(FXMLLoader loader, GenericController controller, Recorder reporter,
                         ComboBox<String> input, ComboBox<String> output) {
        Object node(String id) { return loader.getNamespace().get(id); }
        @SuppressWarnings("unchecked")
        ComboBox<String> combo(String id) { return (ComboBox<String>) node(id); }
        void set(String id, String value) { ((TextInputControl) node(id)).setText(value); }
        String text(String id) { return ((TextInputControl) node(id)).getText(); }

        void toolbar(String in, String out) {
            input.setValue(in);
            output.setValue(out);
        }

        void invoke(String handler) {
            try {
                Method method = GenericController.class.getDeclaredMethod(handler);
                method.setAccessible(true);
                method.invoke(controller);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException(error);
            }
        }

        String step(String name, String resultField) {
            return "## " + name + (resultField == null ? "" : "\n" + text(resultField)) + "\n" + reporter.drain();
        }
    }

    private static final class Recorder implements StatusReporter {
        private final ComboBox<String> input;
        private final ComboBox<String> output;
        private final List<String> lines = new ArrayList<>();

        Recorder(ComboBox<String> input, ComboBox<String> output) {
            this.input = input;
            this.output = output;
        }

        String drain() {
            String text = String.join("\n", lines);
            lines.clear();
            return text;
        }

        @Override
        public void updateStatus(String message) {
            lines.add("status " + message);
        }

        @Override
        public void updateInspector(String operation, byte[] input, byte[] output, List<OperationDetail> details) {
        }

        @Override
        public void showError(String title, String message) {
            lines.add("error " + title + ": " + message);
        }

        @Override
        public void setInputFormat(String format) {
            lines.add("input format " + format);
            input.setValue(format);
        }

        @Override
        public void setOutputFormat(String format) {
            lines.add("output format " + format);
            output.setValue(format);
        }

        @Override
        public void publish(OperationResult result) {
            List<String> details = result.getDetails().stream()
                    .map(detail -> detail.name() + "=" + detail.value())
                    .toList();
            lines.add("publish " + result.getOperation() + "|" + result.getStatusMessage() + "|"
                    + String.join(",", details) + "|in=" + (result.getInput() == null ? 0 : result.getInput().length)
                    + "|out=" + (result.getOutput() == null ? 0 : result.getOutput().length));
        }
    }
}
