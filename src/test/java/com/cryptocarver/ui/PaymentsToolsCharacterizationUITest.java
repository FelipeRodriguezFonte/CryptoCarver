package com.cryptocarver.ui;

import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.payments.PaymentProfile;
import com.cryptocarver.model.payments.PaymentProfileManager;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
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
 * CVV, DUKPT, the host command panel and the ISO 8583 workbench of payments.fxml, pinned before
 * they move out of PaymentsController. Each transcript records result areas, field states,
 * errors and published results and is fixed with SHA-256.
 */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class PaymentsToolsCharacterizationUITest {
    private static final String CVK_A = "0123456789ABCDEF";
    private static final String CVK_B = "FEDCBA9876543210";
    private static final String PAN = "4000001234567899";
    private static final String TDES_BDK = "0123456789ABCDEFFEDCBA9876543210";
    private static final String TDES_KSN = "FFFF9876543210E00001";
    private static final String AES_BDK = "FEDCBA9876543210F1F1F1F1F1F1F1F1";
    private static final String AES_KSN = "123456789012345600000001";

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
    void cvvGenerationForEveryTypeAndItsValidation() throws Exception {
        List<String> transcript = new ArrayList<>();
        withPanel(panel -> {
            transcript.add("types " + panel.combo("cvvTypeCombo").getItems());
            for (String type : panel.combo("cvvTypeCombo").getItems()) {
                panel.combo("cvvTypeCombo").setValue(type);
                panel.set("cvkAField", CVK_A);
                panel.set("cvkBField", CVK_B);
                panel.set("panFieldCvv", PAN);
                panel.set("expiryDateField", "2512");
                panel.set("serviceCodeField", "");
                panel.set("atcField", type.contains("dCVV") ? "0001" : "");
                panel.controller().handleGenerateCvv();
                transcript.add(panel.step(type + " service code " + panel.text("serviceCodeField"), "cvvResultArea"));
            }
            panel.combo("cvvTypeCombo").setValue("CVV (Magnetic Stripe)");
            String[][] bad = {{"cvkAField", "0123"}, {"cvkBField", "XYZ"}, {"panFieldCvv", "123"},
                    {"expiryDateField", "25-12"}, {"serviceCodeField", "1"}, {"serviceCodeField", ""}};
            for (String[] each : bad) {
                panel.set("cvkAField", CVK_A);
                panel.set("cvkBField", CVK_B);
                panel.set("panFieldCvv", PAN);
                panel.set("expiryDateField", "2512");
                panel.set("serviceCodeField", "101");
                panel.set(each[0], each[1]);
                panel.controller().handleGenerateCvv();
                panel.controller().handleVerifyCvv();
                transcript.add(panel.step("bad " + each[0] + "=" + each[1], "cvvResultArea"));
            }
            panel.combo("cvvTypeCombo").setValue("dCVV (Dynamic)");
            panel.set("serviceCodeField", "101");
            panel.set("atcField", "");
            panel.controller().handleGenerateCvv();
            transcript.add(panel.step("dcvv without atc", "cvvResultArea"));
            panel.set("atcField", "GG");
            panel.controller().handleGenerateCvv();
            transcript.add(panel.step("dcvv bad atc", "cvvResultArea"));
        });
        assertEquals("b14f3d7d7bfb1d003f3a33f2f40fd4e6541832499826d62af884bbee26f7a8a1", digest(transcript), String.join("\n", transcript));
    }

    @Test
    void cvvVerificationComparesTheAnsweredValue() throws Exception {
        withPanel(panel -> {
            panel.combo("cvvTypeCombo").setValue("CVV (Magnetic Stripe)");
            panel.set("cvkAField", CVK_A);
            panel.set("cvkBField", CVK_B);
            panel.set("panFieldCvv", PAN);
            panel.set("expiryDateField", "2512");
            panel.set("serviceCodeField", "101");
            panel.controller().handleGenerateCvv();
            String generated = panel.text("cvvResultArea").lines().filter(line -> line.startsWith("CVV:"))
                    .map(line -> line.substring(line.lastIndexOf(' ') + 1)).findFirst().orElseThrow();
            panel.reporter().drain();

            panel.controller().cvvPrompt = () -> java.util.Optional.of(generated);
            panel.controller().handleVerifyCvv();
            assertTrue(panel.reporter().drain().contains("Result=VALID"));
            panel.controller().cvvPrompt = () -> java.util.Optional.of("000");
            panel.controller().handleVerifyCvv();
            assertTrue(panel.reporter().drain().contains("Result=INVALID"));
            panel.controller().cvvPrompt = java.util.Optional::empty;
            panel.controller().handleVerifyCvv();
            assertEquals("", panel.reporter().drain());
        });
    }

    @Test
    void aesProfilesAreCheckedAgainstTheirExpectedWorkingKey() throws Exception {
        withPanel(panel -> {
            PaymentProfile profile = PaymentProfileManager.getProfilesByType(PaymentProfile.ProfileType.DUKPT_AES).get(0);
            panel.controller().loadProfile(profile);
            panel.controller().handleInspectDukpt();
            String report = panel.text("dukptResultArea");
            assertTrue(report.contains("[Laboratory Profile]") && report.contains("[Laboratory Expected Key]"), report);
        });
    }

    @Test
    void dukptInspectionDerivationAndProfiles() throws Exception {
        List<String> transcript = new ArrayList<>();
        withPanel(panel -> {
            transcript.add(panel.dukptState("initial"));
            panel.set("dukptKsnField", TDES_KSN);
            panel.set("dukptBdkField", "");
            panel.controller().handleInspectDukpt();
            transcript.add(panel.step("tdes ksn only", "dukptResultArea"));
            panel.set("dukptBdkField", TDES_BDK);
            for (String usage : panel.combo("dukptTdesUsageCombo").getItems()) {
                panel.combo("dukptTdesUsageCombo").setValue(usage);
                panel.controller().handleInspectDukpt();
                transcript.add(panel.step("tdes " + usage, "dukptResultArea"));
            }
            panel.set("dukptKsnField", "FFFF");
            panel.controller().handleInspectDukpt();
            transcript.add(panel.step("tdes bad ksn", null));

            panel.combo("dukptSchemeCombo").setValue("AES (X9.24-3, 12-byte KSN)");
            transcript.add(panel.dukptState("aes selected"));
            panel.set("dukptKsnField", AES_KSN);
            panel.set("dukptBdkField", AES_BDK);
            for (String usage : List.of("Data encryption (encrypt)", "PIN encryption", "MAC generation")) {
                panel.combo("dukptAesUsageCombo").setValue(usage);
                panel.controller().handleInspectDukpt();
                transcript.add(panel.step("aes " + usage, "dukptResultArea"));
            }
            panel.set("dukptAesPinBlockField", "441234AAAAAAAAAA0123456789ABCDEF");
            panel.controller().handleAesDukptPinBlock();
            String encrypted = panel.text("dukptResultArea");
            transcript.add(panel.step("aes pin encrypt", "dukptResultArea"));
            String block = encrypted.lines().filter(line -> line.toLowerCase().contains("output"))
                    .map(line -> line.substring(line.lastIndexOf(' ') + 1)).findFirst().orElse("");
            panel.combo("dukptAesPinOperationCombo").setValue("Decrypt encrypted PIN block");
            panel.set("dukptAesPinBlockField", block);
            panel.controller().handleAesDukptPinBlock();
            transcript.add(panel.step("aes pin decrypt", "dukptResultArea"));
            panel.set("dukptBdkField", "");
            panel.controller().handleAesDukptPinBlock();
            transcript.add(panel.step("aes pin without bdk", null));
            panel.combo("dukptSchemeCombo").setValue("TDES (legacy, 10-byte KSN)");
            panel.controller().handleAesDukptPinBlock();
            transcript.add(panel.step("aes pin with tdes scheme", null));

            for (PaymentProfile.ProfileType type : List.of(PaymentProfile.ProfileType.DUKPT_TDES,
                    PaymentProfile.ProfileType.DUKPT_AES)) {
                PaymentProfile profile = PaymentProfileManager.getProfilesByType(type).get(0);
                panel.controller().loadProfile(profile);
                panel.controller().handleInspectDukpt();
                transcript.add(panel.dukptState("profile " + profile.getName()));
                transcript.add(panel.step("profile inspect", "dukptResultArea"));
            }
        });
        assertEquals("0d9fe20724fde8e1f985dc39944835326f8f925c6e23df06ff5b4068159521a7", digest(transcript), String.join("\n", transcript));
    }

    @Test
    void hostCommandPanelComposesAndAnalyses() throws Exception {
        List<String> transcript = new ArrayList<>();
        withPanel(panel -> {
            panel.controller().handleHsmHostLoadExample();
            transcript.add(panel.step("example", "hsmHostResultArea") + "\nframe " + panel.text("hsmHostCapturedFrameArea"));
            panel.controller().handleHsmHostAnalyzeResponse();
            transcript.add(panel.step("analyse example response", "hsmHostResultArea"));
            panel.set("hsmHostHeaderField", "0000");
            panel.set("hsmHostCommandCodeField", "nc");
            panel.set("hsmHostBodyField", "");
            panel.set("hsmHostTrailerField", "");
            panel.controller().handleHsmHostCompose();
            transcript.add(panel.step("compose", "hsmHostResultArea") + "\nframe " + panel.text("hsmHostCapturedFrameArea"));
            panel.controller().handleHsmHostAnalyzeCommand();
            transcript.add(panel.step("analyse composed", "hsmHostResultArea"));
            ((CheckBox) panel.node("hsmHostTcpPrefixCheck")).setSelected(true);
            panel.controller().handleHsmHostCompose();
            transcript.add(panel.step("compose with tcp prefix", "hsmHostResultArea") + "\nframe "
                    + panel.text("hsmHostCapturedFrameArea"));
            panel.set("hsmHostCapturedFrameArea", "ZZ");
            panel.controller().handleHsmHostAnalyzeCommand();
            transcript.add(panel.step("bad hex frame", "hsmHostResultArea"));
            panel.set("hsmHostHeaderLengthField", "four");
            panel.controller().handleHsmHostAnalyzeCommand();
            transcript.add(panel.step("bad header length", "hsmHostResultArea"));
        });
        assertEquals("c543a88e7cafcdf9a5fea18327c2882d52f678bcbd9bbc0dd21bc0675c3b8250", digest(transcript), String.join("\n", transcript));
    }

    @Test
    void iso8583BuildsAndParsesInEveryEncoding() throws Exception {
        List<String> transcript = new ArrayList<>();
        withPanel(panel -> {
            transcript.add("profiles " + panel.combo("iso8583ProfileCombo").getItems() + " "
                    + panel.combo("iso8583BitmapEncodingCombo").getItems() + " "
                    + panel.combo("iso8583LengthEncodingCombo").getItems());
            panel.set("iso8583MessageArea", "");
            panel.controller().handleParseIso8583();
            transcript.add(panel.step("empty", "iso8583ReportArea"));
            for (String bitmap : panel.combo("iso8583BitmapEncodingCombo").getItems()) {
                for (String length : panel.combo("iso8583LengthEncodingCombo").getItems()) {
                    panel.combo("iso8583BitmapEncodingCombo").setValue(bitmap);
                    panel.combo("iso8583LengthEncodingCombo").setValue(length);
                    panel.set("iso8583MessageArea", "0200\n2=" + PAN + "\n3=000000\n4=000000001000\n11=123456");
                    panel.controller().handleBuildIso8583();
                    String built = panel.text("iso8583ReportArea");
                    transcript.add(panel.step("build " + bitmap + "/" + length, "iso8583ReportArea"));
                    String message = built.lines().reduce((first, second) -> second).orElse("");
                    panel.set("iso8583MessageArea", message);
                    panel.controller().handleParseIso8583();
                    transcript.add(sortedFieldLines(panel.step("parse " + bitmap + "/" + length, "iso8583ReportArea")));
                }
            }
            panel.combo("iso8583BitmapEncodingCombo").setValue("Binary");
            panel.set("iso8583MessageArea", "XYZ");
            panel.controller().handleParseIso8583();
            transcript.add(panel.step("bad binary", "iso8583ReportArea"));
            panel.set("iso8583MessageArea", "0200\n3000000");
            panel.controller().handleBuildIso8583();
            transcript.add(panel.step("bad build line", "iso8583ReportArea"));
        });
        assertEquals("e7f1b3dd9afc09110576f148630686e498dee22debce816b862e29f30716ad37", digest(transcript), String.join("\n", transcript));
    }

    /** The parse report lists fields in an unstable order (it comes from crypto/); pin them sorted. */
    private static String sortedFieldLines(String report) {
        List<String> fields = report.lines().filter(line -> line.matches("F\\d{3} .*")).sorted().toList();
        List<String> others = report.lines().filter(line -> !line.matches("F\\d{3} .*")).toList();
        return String.join("\n", others) + "\n" + String.join("\n", fields);
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
        Object node(String id) { return loader.getNamespace().get(id); }
        @SuppressWarnings("unchecked")
        ComboBox<String> combo(String id) { return (ComboBox<String>) node(id); }
        void set(String id, String value) { ((TextInputControl) node(id)).setText(value); }
        String text(String id) { return ((TextInputControl) node(id)).getText(); }

        String step(String name, String resultArea) {
            return "## " + name + (resultArea == null ? "" : "\n" + text(resultArea)) + "\n" + reporter.drain();
        }

        String dukptState(String name) {
            StringBuilder out = new StringBuilder("## state " + name);
            for (String id : List.of("dukptTdesOptionsBox", "dukptAesOptionsBox", "dukptAesPinBox")) {
                Node box = (Node) node(id);
                out.append(' ').append(id).append('=').append(box.isVisible()).append('/').append(box.isManaged());
            }
            for (String id : List.of("dukptSchemeCombo", "dukptTdesUsageCombo", "dukptAesUsageCombo",
                    "dukptAesKeyTypeCombo", "dukptAesPinOperationCombo")) {
                out.append(' ').append(id).append('=').append(combo(id).getValue());
            }
            out.append(" bdk=").append(text("dukptBdkField")).append(" ksn=").append(text("dukptKsnField"));
            return out.toString();
        }
    }

    private static final class Recorder implements StatusReporter {
        private final List<String> lines = new ArrayList<>();

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
            String output = result.getOutput() == null ? "" : new String(result.getOutput(), StandardCharsets.UTF_8);
            List<String> details = result.getDetails().stream()
                    .map(detail -> detail.name() + "=" + detail.value())
                    .toList();
            lines.add("publish " + result.getOperation() + "|" + result.getStatusMessage() + "|"
                    + String.join(",", details) + "|out=" + output);
        }
    }
}
