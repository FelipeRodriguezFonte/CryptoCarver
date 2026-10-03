package com.cryptocarver.ui;

import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ComboBox;
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
 * Check digits, random bytes, UUIDs and modular arithmetic in generic.fxml, plus the module's
 * clear and output collection, pinned before they move out of GenericController.
 */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class GenericUtilitiesCharacterizationUITest {

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
    void checkDigitsForEveryAlgorithm() throws Exception {
        List<String> transcript = new ArrayList<>();
        withPanel(panel -> {
            transcript.add("algorithms " + panel.combo("checkDigitAlgorithmCombo").getItems());
            for (String algorithm : panel.combo("checkDigitAlgorithmCombo").getItems()) {
                panel.combo("checkDigitAlgorithmCombo").setValue(algorithm);
                panel.set("checkDigitInput", "7992739871");
                panel.controller().handleCalculateCheckDigit();
                transcript.add(panel.step(algorithm + " calculate", "checkDigitOutput"));
                panel.set("checkDigitInput", "79927398713");
                panel.controller().handleValidateCheckDigit();
                transcript.add(panel.step(algorithm + " validate", "checkDigitOutput"));
            }
            panel.set("checkDigitInput", "");
            panel.controller().handleCalculateCheckDigit();
            panel.controller().handleValidateCheckDigit();
            transcript.add(panel.step("empty", null));
            panel.set("checkDigitInput", "12a4");
            panel.combo("checkDigitAlgorithmCombo").setValue("Luhn (Mod 10)");
            panel.controller().handleCalculateCheckDigit();
            transcript.add(panel.step("non numeric", null));
        });
        assertEquals("e15ca5c71c2989e1d6ca54c932d5ec5ad694f217725ba05f63ab3b1fb88af0c8", digest(transcript), String.join("\n", transcript));
    }

    @Test
    void randomBytesAndUuids() throws Exception {
        List<String> transcript = new ArrayList<>();
        withPanel(panel -> {
            transcript.add("formats " + panel.combo("randomFormatCombo").getItems());
            for (String format : panel.combo("randomFormatCombo").getItems()) {
                panel.combo("randomFormatCombo").setValue(format);
                panel.set("randomBytesField", "16");
                panel.controller().handleGenerateRandom();
                String output = panel.text("randomOutputArea");
                transcript.add("## " + format + " shape " + output.replaceAll("[0-9A-Za-z+/]", "x").replaceAll("x+", "x") + " length " + output.length()
                        + "\n" + panel.reporter().drain().replaceAll("out=\\d+", "out=n"));
            }
            for (String bad : List.of("", "0", "1025", "abc")) {
                panel.set("randomBytesField", bad);
                panel.controller().handleGenerateRandom();
                transcript.add(panel.step("random '" + bad + "'", null));
            }
            panel.controller().handleGenerateUUID();
            String uuid = panel.text("uuidOutputField");
            assertTrue(uuid.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"), uuid);
            transcript.add("## uuid\n" + panel.reporter().drain().replaceAll("out=\\d+", "out=n"));
        });
        assertEquals("59f5f073a3b2f0b63b721e99bf638c557201a1ebd42ebdfdf032579febaa310e", digest(transcript), String.join("\n", transcript));
    }

    @Test
    void modularArithmeticForEveryOperation() throws Exception {
        List<String> transcript = new ArrayList<>();
        withPanel(panel -> {
            transcript.add("operations " + panel.combo("modOperationCombo").getItems());
            String[][] inputs = {{"0F", "0A", "11"}, {"03", "07", "0B"}, {"1234", "00FF", ""}, {"12", "", "35"}};
            for (String operation : panel.combo("modOperationCombo").getItems()) {
                for (String[] each : inputs) {
                    panel.combo("modOperationCombo").setValue(operation);
                    panel.set("modOperandAField", operation.contains("Decimal") ? each[0].replaceAll("[A-F]", "9") : each[0]);
                    panel.set("modOperandBField", operation.contains("Decimal") ? each[1].replaceAll("[A-F]", "9") : each[1]);
                    panel.set("modModulusField", each[2]);
                    panel.controller().handleModularCalculate();
                    transcript.add(panel.step(operation + " " + String.join("/", each), "modResultArea"));
                }
            }
        });
        assertEquals("8bc92e5a1bb463ba5570562ee519854ba87f2bf7a25170a747c48a5fbe858412", digest(transcript), String.join("\n", transcript));
    }

    @Test
    void clearAndOutputCollection() throws Exception {
        List<String> transcript = new ArrayList<>();
        withPanel(panel -> {
            panel.set("checkDigitInput", "7992739871");
            panel.controller().handleCalculateCheckDigit();
            transcript.add("output after check digit '" + panel.controller().getOutputText() + "'");
            panel.set("modOperandAField", "0F");
            panel.set("modOperandBField", "0A");
            panel.set("modModulusField", "11");
            panel.controller().handleModularCalculate();
            transcript.add("output after modular '" + panel.controller().getOutputText().lines().findFirst().orElse("") + "'");
            panel.controller().handleClear();
            transcript.add("after clear check='" + panel.text("checkDigitOutput") + "' mod='" + panel.text("modResultArea")
                    + "' output='" + panel.controller().getOutputText() + "'");
        });
        assertEquals("2de624309870b06db242f2fa06d3601cf06d46dd963c868600bb329e895be80d", digest(transcript), String.join("\n", transcript));
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
                Recorder reporter = new Recorder();
                controller.setStatusReporter(reporter);
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

    private record Panel(FXMLLoader loader, GenericController controller, Recorder reporter) {
        @SuppressWarnings("unchecked")
        ComboBox<String> combo(String id) { return (ComboBox<String>) loader.getNamespace().get(id); }
        void set(String id, String value) { ((TextInputControl) loader.getNamespace().get(id)).setText(value); }
        String text(String id) { return ((TextInputControl) loader.getNamespace().get(id)).getText(); }

        String step(String name, String resultField) {
            return "## " + name + (resultField == null ? "" : "\n" + text(resultField)) + "\n" + reporter.drain();
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
        public void publish(OperationResult result) {
            List<String> details = result.getDetails().stream()
                    .map(detail -> detail.name() + "=" + detail.value())
                    .toList();
            lines.add("publish " + result.getOperation() + "|" + result.getStatusMessage() + "|"
                    + String.join(",", details) + "|out=" + (result.getOutput() == null ? 0 : result.getOutput().length));
        }
    }
}
