package com.cryptocarver.ui;

import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
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
 * IBM 3624 PIN generation and verification, the offset and PVV generators and PIN derivation
 * from a PVV in payments.fxml, pinned before they move out of PaymentsController. Each transcript
 * records the result area, errors and published results and is fixed with SHA-256.
 */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class PinGenerationCharacterizationUITest {
    private static final String PVK = "0123456789ABCDEFFEDCBA9876543210";
    private static final String TABLE = "0123456789012345";
    private static final String PAN = "4000001234567899";
    private static final String PIN = "1234";

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
    void ibm3624GenerationAndVerification() throws Exception {
        List<String> transcript = new ArrayList<>();
        withPanel(panel -> {
            transcript.add("defaults table=" + panel.text("ibm3624ConvTableField"));
            panel.set("ibm3624PvkField", PVK);
            panel.set("ibm3624ConvTableField", TABLE);
            panel.set("ibm3624OffsetField", "0000");
            panel.set("ibm3624PanField", PAN);
            panel.controller().handleGenerateIbm3624Pin();
            transcript.add(panel.step("generate default config", "ibm3624ResultArea"));
            String naturalPin = panel.reporter().lastOutput();

            panel.set("ibm3624PinVerifyField", naturalPin);
            panel.controller().handleVerifyIbm3624Pin();
            transcript.add(panel.step("verify right pin", "ibm3624ResultArea"));
            panel.set("ibm3624PinVerifyField", "9999");
            panel.controller().handleVerifyIbm3624Pin();
            transcript.add(panel.step("verify wrong pin", "ibm3624ResultArea"));

            panel.set("ibm3624StartField", "4");
            panel.set("ibm3624LengthField", "12");
            panel.set("ibm3624PadField", "F");
            panel.set("ibm3624OffsetField", "4321");
            panel.controller().handleGenerateIbm3624Pin();
            transcript.add(panel.step("generate custom config", "ibm3624ResultArea"));

            panel.set("ibm3624StartField", "x");
            panel.controller().handleGenerateIbm3624Pin();
            panel.controller().handleVerifyIbm3624Pin();
            transcript.add(panel.step("bad start", null));
            panel.set("ibm3624StartField", "");
            panel.set("ibm3624LengthField", "y");
            panel.controller().handleGenerateIbm3624Pin();
            panel.controller().handleVerifyIbm3624Pin();
            transcript.add(panel.step("bad length", null));
            panel.set("ibm3624LengthField", "16");
            panel.controller().handleGenerateIbm3624Pin();
            transcript.add(panel.step("length beyond pan", "ibm3624ResultArea"));
            panel.set("ibm3624LengthField", "");
            panel.set("ibm3624PvkField", "ZZ");
            panel.controller().handleGenerateIbm3624Pin();
            transcript.add(panel.step("bad pvk", null));
            panel.set("ibm3624PvkField", "");
            panel.controller().handleGenerateIbm3624Pin();
            panel.controller().handleVerifyIbm3624Pin();
            transcript.add(panel.step("missing pvk", null));
        });
        assertEquals("e59f30b2dbc36c23b0e67dd9fff72e2ca3506dee800ef67446d5bb98902b6827", digest(transcript), String.join("\n", transcript));
    }

    @Test
    void offsetGeneratorMatchesIbm3624Generation() throws Exception {
        List<String> transcript = new ArrayList<>();
        withPanel(panel -> {
            panel.set("genOffsetPvkField", PVK);
            panel.set("genOffsetDecTableField", TABLE);
            panel.set("genOffsetPanField", PAN);
            panel.set("genOffsetPinField", PIN);
            panel.controller().handleGenerateOffsetUtility();
            transcript.add(panel.step("offset default config", "genOffsetResultArea"));
            String offset = panel.reporter().lastOutput();

            panel.set("ibm3624PvkField", PVK);
            panel.set("ibm3624ConvTableField", TABLE);
            panel.set("ibm3624OffsetField", offset);
            panel.set("ibm3624PanField", PAN);
            panel.controller().handleGenerateIbm3624Pin();
            assertEquals(PIN, panel.reporter().lastOutput());
            panel.reporter().drain();

            panel.set("genOffsetStartField", "2");
            panel.set("genOffsetLengthField", "10");
            panel.set("genOffsetPadField", "A");
            panel.controller().handleGenerateOffsetUtility();
            transcript.add(panel.step("offset custom config", "genOffsetResultArea"));

            panel.set("genOffsetPanField", "40000012345");
            panel.set("genOffsetStartField", "1");
            panel.set("genOffsetLengthField", "11");
            panel.controller().handleGenerateOffsetUtility();
            transcript.add(panel.step("short pan custom config", "genOffsetResultArea"));

            panel.set("genOffsetDecTableField", "0123");
            panel.controller().handleGenerateOffsetUtility();
            transcript.add(panel.step("short table", null));
            panel.set("genOffsetPinField", "");
            panel.controller().handleGenerateOffsetUtility();
            transcript.add(panel.step("missing pin", null));
        });
        assertEquals("ae79220c8611e4fc419f93948431c6cad65e4435c76991e0477c427f6d420862", digest(transcript), String.join("\n", transcript));
    }

    @Test
    void pvvGenerationAndDerivation() throws Exception {
        List<String> transcript = new ArrayList<>();
        withPanel(panel -> {
            panel.set("genPvvPvkField", PVK);
            panel.set("genPvvPanField", PAN);
            panel.set("genPvvPinField", PIN);
            panel.set("genPvvKeyIndexField", "1");
            panel.controller().handleGeneratePVVUtility();
            transcript.add(panel.step("pvv", "genPvvResultArea"));
            String pvv = panel.reporter().lastOutput();

            panel.set("genPvvKeyIndexField", "");
            panel.controller().handleGeneratePVVUtility();
            transcript.add(panel.step("pvv default index", "genPvvResultArea"));
            panel.set("genPvvPinField", "");
            panel.controller().handleGeneratePVVUtility();
            transcript.add(panel.step("pvv missing pin", null));

            panel.set("derivePvvPvkField", PVK);
            panel.set("derivePvvPanField", PAN);
            panel.set("derivePvvTargetPvvField", pvv);
            panel.set("derivePvvKeyIndexField", "1");
            panel.controller().handleDerivePinFromPvvUtility();
            String derived = panel.text("derivePvvResultArea");
            assertTrue(derived.contains(PIN), derived);
            transcript.add(panel.step("derive", "derivePvvResultArea"));
            panel.set("derivePvvTargetPvvField", "");
            panel.controller().handleDerivePinFromPvvUtility();
            transcript.add(panel.step("derive missing pvv", null));
        });
        assertEquals("49cbb5da8b56f495b0aaf3b8bcd3e08bbd97cdf9fb098b4cb1dd516d58622ecb", digest(transcript), String.join("\n", transcript));
    }

    @Test
    void secureMessagingProfilesSayTheyCannotBeLoadedHere() throws Exception {
        withPanel(panel -> {
            var profile = com.cryptocarver.model.payments.PaymentProfileManager.getProfilesByType(
                    com.cryptocarver.model.payments.PaymentProfile.ProfileType.SECURE_MESSAGING).get(0);
            panel.controller().loadProfile(profile);
            String reported = panel.reporter().drain();
            assertTrue(reported.contains("was not loaded"), reported);
        });
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
        assertTrue(done.await(120, TimeUnit.SECONDS), "FX thread did not complete");
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private record Panel(FXMLLoader loader, PaymentsController controller, Recorder reporter) {
        void set(String id, String value) { ((TextField) loader.getNamespace().get(id)).setText(value); }

        String text(String id) {
            Object node = loader.getNamespace().get(id);
            return node instanceof TextArea area ? area.getText() : ((TextField) node).getText();
        }

        String step(String name, String resultArea) {
            return "## " + name + (resultArea == null ? "" : "\n" + text(resultArea)) + "\n" + reporter.drain();
        }
    }

    private static final class Recorder implements StatusReporter {
        private final List<String> lines = new ArrayList<>();
        private String lastOutput = "";

        String lastOutput() {
            return lastOutput;
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
        public void showError(UserFacingError error) {
            lines.add("error " + error.title() + ": " + error.detail() + " @" + error.fieldKey());
        }

        @Override
        public void publish(OperationResult result) {
            lastOutput = result.getOutput() == null ? "" : new String(result.getOutput(), StandardCharsets.UTF_8);
            List<String> details = result.getDetails().stream()
                    .map(detail -> detail.name() + "=" + detail.value())
                    .toList();
            lines.add("publish " + result.getOperation() + "|" + result.getStatusMessage() + "|"
                    + String.join(",", details) + "|out=" + lastOutput);
        }
    }
}
