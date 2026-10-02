package com.cryptocarver.ui;

import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.util.DataConverter;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

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
 * Clear and encrypted PIN block panels of payments.fxml, pinned before they move out of
 * PaymentsController. Formats with random padding are pinned by structure and round trip,
 * the rest byte for byte; each transcript is fixed with SHA-256.
 */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class PinBlockCharacterizationUITest {
    private static final String PIN = "1234";
    private static final String PAN = "4000001234567899";
    private static final String TDES_KEY = "0123456789ABCDEFFEDCBA9876543210";
    private static final String AES_KEY = "00112233445566778899AABBCCDDEEFF";

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
    void clearBlocksRoundTripInEveryFormat() throws Exception {
        List<String> transcript = new ArrayList<>();
        withPanel(panel -> {
            transcript.add("formats " + panel.combo("pinBlockFormatCombo").getItems());
            transcript.add("decode formats " + panel.combo("pinBlockFormatDecodeCombo").getItems());
            for (String format : panel.combo("pinBlockFormatCombo").getItems()) {
                panel.combo("pinBlockFormatCombo").setValue(format);
                transcript.add("## " + format + " padding " + panel.combo("pinBlockPaddingCombo").getItems().size()
                        + " default " + panel.combo("pinBlockPaddingCombo").getValue()
                        + " disabled " + panel.combo("pinBlockPaddingCombo").isDisable());
                panel.field("pinField").setText(PIN);
                panel.field("panFieldEncode").setText(PAN);
                panel.controller().handleEncodePinBlock();
                String first = panel.area("pinBlockResultArea").getText();
                String published = panel.reporter().lastOutput();
                panel.controller().handleEncodePinBlock();
                boolean random = !first.equals(panel.area("pinBlockResultArea").getText());
                transcript.add(random ? "random padding, length " + published.length() : first);
                transcript.add(panel.reporter().drain(random));
                if (published.isEmpty()) continue;

                panel.combo("pinBlockFormatDecodeCombo").setValue(format);
                panel.field("pinBlockField").setText(published);
                panel.field("panFieldDecode").setText(PAN);
                panel.controller().handleDecodePinBlock();
                String decoded = panel.area("pinBlockResultArea").getText();
                transcript.add(random ? "decoded pin line " + decodedPinLine(decoded) : decoded);
                transcript.add(panel.reporter().drain(random));
            }
        });
        assertEquals("f615cc012bcaff9851314aa00fefffc578b12affeadc81a79b5d70fc4195ecca", digest(transcript), String.join("\n", transcript));
    }

    @Test
    void clearBlockInputErrors() throws Exception {
        List<String> transcript = new ArrayList<>();
        withPanel(panel -> {
            panel.combo("pinBlockFormatCombo").setValue("Format 0 (ISO-0)");
            String[][] encodes = {{"", PAN}, {"12", PAN}, {"1234567890123", PAN}, {"12a4", PAN}, {PIN, "123"},
                    {PIN, ""}};
            for (String[] each : encodes) {
                panel.field("pinField").setText(each[0]);
                panel.field("panFieldEncode").setText(each[1]);
                panel.controller().handleEncodePinBlock();
                transcript.add("encode [" + each[0] + "|" + each[1] + "] " + panel.area("pinBlockResultArea").getText()
                        + " " + panel.reporter().drain(false));
            }
            panel.combo("pinBlockFormatDecodeCombo").setValue("Format 0 (ISO-0)");
            String[][] decodes = {{"", PAN}, {"0412AC", PAN}, {"ZZ12ACFFFFFFFFFF", PAN}, {"041234FFFFFFFFFF", "12"},
                    {"041234FFFFFFFFFF", PAN}};
            for (String[] each : decodes) {
                panel.field("pinBlockField").setText(each[0]);
                panel.field("panFieldDecode").setText(each[1]);
                panel.controller().handleDecodePinBlock();
                transcript.add("decode [" + each[0] + "|" + each[1] + "] " + panel.area("pinBlockResultArea").getText()
                        + " " + panel.reporter().drain(false));
            }
        });
        assertEquals("98da76e4da0299c3d09a24d04631011d64ee9dd3a48f8b2408ef0a0792b5ee83", digest(transcript), String.join("\n", transcript));
    }

    @Test
    void encryptedBlocksRoundTripWithTdesAndAes() throws Exception {
        List<String> transcript = new ArrayList<>();
        withPanel(panel -> {
            transcript.add("formats " + panel.combo("encPinBlockFormatCombo").getItems());
            for (String format : List.of("Format 0 (ISO-0)", "Format 1 (ISO-1)", "Format 3 (ISO-3)",
                    "Format 4 (ISO-4)", "VISA-1")) {
                boolean aes = format.contains("ISO-4");
                panel.combo("encPinBlockFormatCombo").setValue(format);
                panel.field("encPinField").setText(PIN);
                panel.field("encPanFieldEncode").setText(PAN);
                panel.field("encPinBlockKeyField").setText(aes ? AES_KEY : TDES_KEY);
                panel.controller().handleEncodeEncryptedPinBlock();
                String first = panel.area("encResultArea").getText();
                String published = panel.reporter().lastOutput();
                panel.controller().handleEncodeEncryptedPinBlock();
                boolean random = !first.equals(panel.area("encResultArea").getText());
                transcript.add("## " + format + (random ? " random, length " + published.length() : "\n" + first));
                transcript.add(panel.reporter().drain(random));

                panel.field("encPinBlockFieldDecode").setText(published);
                panel.field("encPanFieldDecode").setText(PAN);
                panel.field("encPinBlockKeyFieldDecode").setText(aes ? AES_KEY : TDES_KEY);
                panel.controller().handleDecodeEncryptedPinBlock();
                String decoded = panel.area("encResultArea").getText();
                transcript.add(random ? "decoded pin line " + decodedPinLine(decoded) : decoded);
                transcript.add(panel.reporter().drain(random));
            }
        });
        assertEquals("46790cbe363ef50bae569a95410aef2ade36dacaf1f543201d3d8ec1f5b60224", digest(transcript), String.join("\n", transcript));
    }

    @Test
    void encryptedBlockWithoutKeyAndInputErrors() throws Exception {
        List<String> transcript = new ArrayList<>();
        withPanel(panel -> {
            panel.combo("encPinBlockFormatCombo").setValue("Format 0 (ISO-0)");
            panel.field("encPinField").setText(PIN);
            panel.field("encPanFieldEncode").setText(PAN);
            panel.field("encPinBlockKeyField").setText("");
            panel.controller().handleEncodeEncryptedPinBlock();
            transcript.add("no key " + panel.area("encResultArea").getText() + " " + panel.reporter().drain(false));

            panel.field("encPinBlockKeyField").setText("0123456789ABCDEF0123");
            panel.controller().handleEncodeEncryptedPinBlock();
            transcript.add("bad key " + panel.area("encResultArea").getText() + " " + panel.reporter().drain(false));

            panel.field("encPinField").setText("");
            panel.controller().handleEncodeEncryptedPinBlock();
            transcript.add("no pin " + panel.reporter().drain(false));

            panel.field("encPinField").setText(PIN);
            panel.field("encPanFieldEncode").setText("");
            panel.controller().handleEncodeEncryptedPinBlock();
            transcript.add("no pan " + panel.reporter().drain(false));

            panel.field("encPinBlockFieldDecode").setText("");
            panel.controller().handleDecodeEncryptedPinBlock();
            transcript.add("decode empty " + panel.reporter().drain(false));

            panel.field("encPinBlockFieldDecode").setText("XYZ");
            panel.field("encPanFieldDecode").setText(PAN);
            panel.field("encPinBlockKeyFieldDecode").setText(TDES_KEY);
            panel.controller().handleDecodeEncryptedPinBlock();
            transcript.add("decode bad hex " + panel.reporter().drain(false));

            panel.combo("encPinBlockFormatCombo").setValue("Format 4 (ISO-4)");
            panel.field("encPinBlockFieldDecode").setText("00".repeat(16));
            panel.field("encPinBlockKeyFieldDecode").setText(AES_KEY);
            panel.controller().handleDecodeEncryptedPinBlock();
            transcript.add("iso4 wrong block " + panel.reporter().drain(false));
        });
        assertEquals("16c564678409e7ff90d04beb96a03de1917a1d423b6afb452e46ec949c6a40a5", digest(transcript), String.join("\n", transcript));
    }

    private static String decodedPinLine(String text) {
        return text.lines().filter(line -> line.contains("Decoded PIN")).map(String::strip).findFirst().orElse("<no pin>");
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
                FXMLLoader loader = UiTestFxml.loader("/fxml/payments.fxml");
                loader.load();
                PaymentsController controller = loader.getController();
                Recorder reporter = new Recorder();
                controller.init(reporter);
                body.accept(new Panel(loader, controller, reporter));
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(60, TimeUnit.SECONDS), "FX thread did not complete");
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private record Panel(FXMLLoader loader, PaymentsController controller, Recorder reporter) {
        @SuppressWarnings("unchecked")
        ComboBox<String> combo(String id) { return (ComboBox<String>) loader.getNamespace().get(id); }
        TextField field(String id) { return (TextField) loader.getNamespace().get(id); }
        TextArea area(String id) { return (TextArea) loader.getNamespace().get(id); }
    }

    private static final class Recorder implements StatusReporter {
        private final List<String> lines = new ArrayList<>();
        private String lastOutput = "";

        String lastOutput() {
            return lastOutput;
        }

        /** Returns and clears what was reported; for random blocks the published bytes are left out. */
        String drain(boolean random) {
            String text = String.join("\n", lines);
            lines.clear();
            lastOutput = "";
            return random ? text.replaceAll("\\|(in|out)=[0-9A-F]*", "|$1=<random>") : text;
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
        public void showError(UserFacingError error) {
            lines.add("error " + error.title() + ": " + error.detail() + " @" + error.fieldKey());
        }

        @Override
        public void publish(OperationResult result) {
            lastOutput = result.getOutput() == null ? "" : DataConverter.bytesToHex(result.getOutput());
            List<String> details = result.getDetails().stream()
                    .map(detail -> detail.name() + "=" + detail.value())
                    .toList();
            lines.add("publish " + result.getOperation() + "|" + result.getStatusMessage() + "|"
                    + String.join(",", details) + "|in=" + DataConverter.bytesToHex(
                    result.getInput() == null ? new byte[0] : result.getInput()) + "|out=" + lastOutput);
        }
    }
}
