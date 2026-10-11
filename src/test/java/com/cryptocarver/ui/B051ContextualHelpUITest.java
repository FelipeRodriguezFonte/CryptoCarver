package com.cryptocarver.ui;

import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.model.help.HelpContext;
import com.cryptocarver.model.help.HelpContextState;
import com.cryptocarver.service.I18nService;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class B051ContextualHelpUITest {
    @BeforeAll static void startFx() {
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException alreadyStarted) { Platform.setImplicitExit(false); }
    }

    @Test void b051ConsumersAndHelpWorkInSpanishAndEnglishAtWideAndCompactWidths() throws Exception {
        for (LanguagePreference language : List.of(LanguagePreference.ES, LanguagePreference.EN)) {
            for (double width : List.of(1800.0, 900.0)) {
                Path dir = Files.createTempDirectory("cryptocarver-b051-");
                Path source = dir.resolve("source.txt"), comparison = dir.resolve("same.txt"), destination = dir.resolve("out.hex");
                Files.writeString(source, "abc"); Files.copy(source, comparison);
                final Object[] refs = new Object[3];
                try {
                    UiTestLifecycleExtension.onFx(() -> {
                        try {
                            I18nService.getInstance().setPreference(language);
                            var loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
                            Parent root = loader.load();
                            ModernMainController shell = loader.getController();
                            Stage owner = new Stage(); owner.setScene(new Scene(root, width, 900)); owner.show();
                            root.applyCss(); root.layout();
                            CheckMenuItem mode = field(shell, "contextualHelpModeMenuItem");
                            mode.setSelected(true); shell.handleToggleContextualHelpMode();
                            shell.navigateTo("Hashing");
                            root.applyCss(); root.layout();
                            GenericController generic = field(shell, "genericContainerController");
                            shell.setInputFormat("Text (UTF-8)"); shell.setOutputFormat("Hexadecimal");
                            TextArea hashInput = field(generic, "hashInputArea");
                            TextArea hashOutput = field(generic, "hashOutputArea");
                            hashInput.setText("abc"); hashOutput.setText("KEEP_HASH_RESULT");
                            generic.handleCalculateHash();
                            assertEquals("BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD", hashOutput.getText());
                            hashInput.setText("GG"); shell.setInputFormat("Hexadecimal"); generic.handleCalculateHash();
                            assertEquals("", hashOutput.getText(), "invalid Hex clears the old result");
                            hashInput.setText("B051_PRIVATE_SENTINEL"); hashOutput.setText("B051_RESULT_SENTINEL");
                            javafx.scene.Node hashInputHelp = ContextualHelpTestSupport.control(root, "contextHelp_op_gen_hash_calculate_input");
                            assertNotNull(hashInputHelp); assertTrue(hashInputHelp.isVisible()); ContextualHelpTestSupport.shiftF1(hashInputHelp);
                            HelpContextState state = field(shell, "contextualHelpState");
                            assertEquals("op_gen_hash", state.current().operationId());
                            assertEquals("calculate", state.current().actionId()); assertEquals("input", state.current().fieldId());
                            assertEquals("B051_PRIVATE_SENTINEL", hashInput.getText());
                            assertEquals("B051_RESULT_SENTINEL", hashOutput.getText());
                            assertFalse(state.current().toString().contains("B051_PRIVATE_SENTINEL"));
                            assertFalse(state.current().toString().contains("B051_RESULT_SENTINEL"));

                            shell.navigateTo("Manual Conversion");
                            TextArea manualInput = field(generic, "manualInputArea"), manualOutput = field(generic, "manualOutputArea");
                            ComboBox<String> manualInFormat = field(generic, "manualInputFormatCombo"), manualOutFormat = field(generic, "manualOutputFormatCombo");
                            manualInFormat.setValue("Hexadecimal"); manualOutFormat.setValue("Base64"); manualInput.setText("4142");
                            generic.handleManualConvert(); assertEquals("QUI=", manualOutput.getText());
                            manualInFormat.setValue("Text (UTF-8)"); manualOutFormat.setValue("Hexadecimal");
                            ((javafx.scene.control.CheckBox) field(generic, "ebcdicConversionCheck")).setSelected(true);
                            ((ComboBox<String>) field(generic, "ebcdicDirectionCombo")).setValue("Encode UTF-8 → EBCDIC");
                            ((ComboBox<String>) field(generic, "ebcdicCodePageCombo")).setValue("IBM037 — US/Canada");
                            manualInput.setText("AB"); generic.handleManualConvert(); assertEquals("C1C2", manualOutput.getText());
                            ((javafx.scene.control.CheckBox) field(generic, "ebcdicConversionCheck")).setSelected(false);
                            ((ComboBox<String>) field(generic, "endianWordSizeCombo")).setValue("32 bits (4 bytes)");
                            manualInFormat.setValue("Hexadecimal"); manualOutFormat.setValue("Hexadecimal"); manualInput.setText("01020304");
                            generic.handleConvertEndian(); assertEquals("04030201", manualOutput.getText());
                            manualInput.setText("010203"); generic.handleConvertEndian(); assertEquals("", manualOutput.getText());
                            javafx.scene.Node sharedManualInputHelp = ContextualHelpTestSupport.control(root, "contextHelp_op_gen_manual_convert_input");
                            javafx.scene.Node sharedManualOutputHelp = ContextualHelpTestSupport.control(root, "contextHelp_op_gen_manual_convert_output");
                            assertNotNull(sharedManualInputHelp); assertNotNull(sharedManualOutputHelp);
                            manualInput.setText("B051_MANUAL_INPUT"); manualOutput.setText("B051_MANUAL_OUTPUT");
                            ContextualHelpTestSupport.shiftF1(sharedManualInputHelp);
                            assertEquals("op_gen_manual", state.current().operationId());
                            assertEquals("endian", state.current().actionId()); assertEquals("input", state.current().fieldId());
                            ContextualHelpTestSupport.shiftF1(sharedManualOutputHelp);
                            assertEquals("endian", state.current().actionId()); assertEquals("output", state.current().fieldId());
                            assertEquals("B051_MANUAL_INPUT", manualInput.getText()); assertEquals("B051_MANUAL_OUTPUT", manualOutput.getText());
                            javafx.scene.Node decompressActionHelp = ContextualHelpTestSupport.control(root, "contextHelp_op_gen_manual_decompress_input");
                            assertNotNull(decompressActionHelp); ContextualHelpTestSupport.shiftF1(decompressActionHelp);
                            ((ComboBox<String>) field(generic, "compressionFormatCombo")).setValue("gzip");
                            ContextualHelpTestSupport.shiftF1(sharedManualInputHelp);
                            assertEquals("decompress", state.current().actionId(), "an explicitly selected help action survives selector changes");
                            manualInput.setText("AB"); generic.handleEncodeBase64Url(); assertEquals("QUI", manualOutput.getText());
                            ContextualHelpTestSupport.shiftF1(sharedManualInputHelp);
                            assertEquals("base64url_encode", state.current().actionId(), "real action replaces prior contextual selection");
                            shell.navigateTo("Hashing"); shell.navigateTo("Manual Conversion");
                            assertEquals("base64url_encode", state.current().actionId(), "return restores last real action");
                            manualInput.setText("AB"); generic.handleEncodeBase64Url(); assertEquals("QUI", manualOutput.getText());
                            manualInput.setText("QUI"); generic.handleDecodeBase64Url(); assertEquals("AB", manualOutput.getText());
                            manualInput.setText("+"); generic.handleDecodeBase64Url(); assertEquals("", manualOutput.getText());
                            manualInput.setText("A"); generic.handleEncodeBase32(); assertEquals("IE======", manualOutput.getText());
                            manualInput.setText("IE======"); generic.handleDecodeBase32(); assertEquals("A", manualOutput.getText());
                            manualInput.setText("a b"); generic.handleEncodeUrl(); assertEquals("a+b", manualOutput.getText());
                            manualInput.setText("a+b"); generic.handleDecodeUrl(); assertEquals("a b", manualOutput.getText());
                            manualInput.setText("%2"); generic.handleDecodeUrl(); assertEquals("", manualOutput.getText());
                            ((ComboBox<String>) field(generic, "compressionFormatCombo")).setValue("gzip");
                            manualInFormat.setValue("Text (UTF-8)"); manualOutFormat.setValue("Hexadecimal"); manualInput.setText("abc");
                            generic.handleCompressData(); String gzipHex = manualOutput.getText(); assertFalse(gzipHex.isBlank());
                            manualInFormat.setValue("Hexadecimal"); manualOutFormat.setValue("Text (UTF-8)"); manualInput.setText(gzipHex);
                            generic.handleDecompressData(); assertEquals("abc", manualOutput.getText());
                            manualInput.setText("12345"); generic.handleEncodeBcd(); assertEquals("012345", manualOutput.getText());
                            manualInput.setText("012345"); generic.handleDecodeBcd(); assertEquals("012345", manualOutput.getText());
                            manualInput.setText("-12345"); generic.handleEncodeComp3(); assertEquals("12345D", manualOutput.getText());
                            manualInput.setText("12345D"); generic.handleDecodeComp3(); assertEquals("-12345", manualOutput.getText());
                            ((TextField) field(generic, "bitShiftBitsField")).setText("1"); manualInput.setText("12");
                            generic.handleShiftLeft(); assertEquals("24", manualOutput.getText());
                            generic.handleShiftRight(); assertEquals("09", manualOutput.getText());
                            manualInput.setText("00000100: DE AD BE EF"); generic.handleExtractTraceHex(); assertEquals("DEADBEEF", manualOutput.getText());

                            shell.navigateTo("Compressed Hex (2-row)");
                            CompressedHexController compressed = field(generic, "compressedHexPaneController");
                            TextArea compressedInput = field(compressed, "compressedHexInputArea"), compressedOutput = field(compressed, "compressedHexOutputArea");
                            compressedInput.setText("1234\nABCD"); ((Button) root.lookup("#expandButton")).fire();
                            assertEquals("1A2B3C4D", compressedOutput.getText());
                            compressedInput.setText("1A2B3C4D"); ((Button) root.lookup("#compressButton")).fire();
                            assertEquals("1234\nABCD", compressedOutput.getText());
                            compressedInput.setText("1234\nABC"); ((Button) root.lookup("#expandButton")).fire();
                            assertEquals("", compressedOutput.getText(), "unequal rows clear stale output");
                            compressedInput.setText("123"); ((Button) root.lookup("#compressButton")).fire();
                            assertEquals("", compressedOutput.getText(), "odd conventional Hex clears stale output");
                            javafx.scene.Node compressActionHelp = ContextualHelpTestSupport.control(root, "contextHelp_op_gen_compressed_hex_compress_action");
                            assertNotNull(compressActionHelp);
                            // The direction question on the shared input follows the action selected in help.
                            ContextualHelpTestSupport.shiftF1(compressActionHelp);
                            javafx.scene.Node compressedInputHelp = ContextualHelpTestSupport.control(root, "contextHelp_op_gen_compressed_hex_expand_input");
                            assertNotNull(compressedInputHelp);
                            assertEquals(I18nService.getInstance().text("help.field.op_gen_compressed_hex.input.label"), compressedInputHelp.getAccessibleText());
                            compressedInput.setText("B051_COMPRESSED_INPUT"); compressedOutput.setText("B051_COMPRESSED_OUTPUT");
                            ContextualHelpTestSupport.shiftF1(compressedInputHelp);
                            assertEquals("compress", state.current().actionId()); assertEquals("input", state.current().fieldId());
                            assertEquals("B051_COMPRESSED_INPUT", compressedInput.getText()); assertEquals("B051_COMPRESSED_OUTPUT", compressedOutput.getText());

                            shell.navigateTo("File Conversion");
                            TextField fileInput = field(generic, "fileInputPathField"), fileOutput = field(generic, "fileOutputPathField"), fileCompare = field(generic, "fileComparePathField");
                            ComboBox<String> fileIn = field(generic, "fileInputFormatCombo"), fileOut = field(generic, "fileOutputFormatCombo");
                            fileInput.setText(source.toString()); fileOutput.setText(destination.toString()); fileCompare.setText(comparison.toString());
                            fileIn.setValue("Text"); fileOut.setValue("Hex"); generic.handleConvertFile();
                            assertEquals("616263", Files.readString(destination));
                            generic.handleCompareFiles(); assertTrue(((TextArea) field(generic, "fileResultArea")).getText().toLowerCase().contains("identical"));
                            Files.writeString(comparison, "abd"); generic.handleCompareFiles();
                            assertTrue(((TextArea) field(generic, "fileResultArea")).getText().contains("offset 2"));
                            Files.copy(source, comparison, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            generic.handleHashFileStreaming(); assertTrue(((TextArea) field(generic, "fileResultArea")).getText().contains("BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD"));
                            generic.handlePreviewFileStreaming(); assertTrue(((TextArea) field(generic, "fileResultArea")).getText().contains("61 62 63"));
                            javafx.scene.Node sharedFileResultHelp = ContextualHelpTestSupport.control(root, "contextHelp_op_gen_file_convert_result");
                            assertNotNull(sharedFileResultHelp);
                            generic.handleHashFileStreaming(); ContextualHelpTestSupport.shiftF1(sharedFileResultHelp);
                            assertEquals("hash_file", state.current().actionId()); assertEquals("result", state.current().fieldId());
                            generic.handleConvertFile(); ContextualHelpTestSupport.shiftF1(sharedFileResultHelp);
                            assertEquals("convert", state.current().actionId()); assertEquals("result", state.current().fieldId());
                            javafx.scene.Node filePathHelp = ContextualHelpTestSupport.control(root, "contextHelp_op_gen_file_choose_source_source_path");
                            assertNotNull(filePathHelp); ContextualHelpTestSupport.shiftF1(filePathHelp);
                            assertEquals("convert", state.current().actionId(), "source path help follows the file action");
                            assertEquals("source_path", state.current().fieldId());
                            fileInput.setText("B051_PATH_SENTINEL");
                            ContextualHelpTestSupport.shiftF1(filePathHelp);
                            assertFalse(state.current().toString().contains("B051_PATH_SENTINEL"));
                            assertEquals("B051_PATH_SENTINEL", fileInput.getText());

                            shell.navigateTo("Random Number Generator");
                            TextField bytes = field(generic, "randomBytesField"); ComboBox<String> randomFormat = field(generic, "randomFormatCombo"); TextArea randomOutput = field(generic, "randomOutputArea");
                            bytes.setText("16"); randomFormat.setValue("Hexadecimal"); generic.handleGenerateRandom();
                            assertEquals(32, randomOutput.getText().length()); assertTrue(randomOutput.getText().matches("[0-9A-F]+"));
                            String previousRandomHex = randomOutput.getText();
                            randomFormat.setValue("Base64");
                            assertEquals(previousRandomHex, randomOutput.getText(), "changing format leaves the existing result untouched");
                            assertTrue(I18nService.getInstance().text("help.action.op_gen_random.select_format.description")
                                    .toLowerCase(java.util.Locale.ROOT).contains(language == LanguagePreference.ES ? "próxima generación" : "next generation"));
                            generic.handleGenerateRandom();
                            assertEquals(16, java.util.Base64.getDecoder().decode(randomOutput.getText()).length);
                            randomFormat.setValue("Decimal"); generic.handleGenerateRandom();
                            assertEquals(16, randomOutput.getText().trim().split("\\s+").length);
                            randomFormat.setValue("Binary"); generic.handleGenerateRandom();
                            assertEquals(16, randomOutput.getText().trim().split("\\s+").length);
                            assertTrue(randomOutput.getText().replace(" ", "").matches("[01]{128}"));
                            bytes.setText("1025"); randomOutput.setText("old"); generic.handleGenerateRandom(); assertEquals("old", randomOutput.getText());

                            shell.navigateTo("Check Digits");
                            TextField checkInput = field(generic, "checkDigitInput"); TextField checkOutput = field(generic, "checkDigitOutput");
                            checkInput.setText("7992739871"); generic.handleCalculateCheckDigit();
                            assertTrue(checkOutput.getText().contains("Check Digit: 3"));
                            assertTrue(checkOutput.getText().contains("79927398713"));
                            javafx.scene.Node sharedCheckInputHelp = ContextualHelpTestSupport.control(root, "contextHelp_op_gen_check_digits_calculate_input");
                            javafx.scene.Node sharedCheckResultHelp = ContextualHelpTestSupport.control(root, "contextHelp_op_gen_check_digits_calculate_result");
                            assertNotNull(sharedCheckInputHelp); assertNotNull(sharedCheckResultHelp);
                            ContextualHelpTestSupport.shiftF1(sharedCheckInputHelp);
                            assertEquals("calculate", state.current().actionId(), "field help follows the action just executed");
                            assertEquals("7992739871", checkInput.getText());
                            ContextualHelpTestSupport.shiftF1(sharedCheckResultHelp);
                            assertEquals("calculate", state.current().actionId()); assertEquals("result", state.current().fieldId());
                            checkInput.setText("79927398713"); generic.handleValidateCheckDigit(); assertTrue(checkOutput.getText().contains("VALID"));
                            ContextualHelpTestSupport.shiftF1(sharedCheckInputHelp);
                            assertEquals("validate", state.current().actionId(), "later action replaces the earlier context");
                            shell.navigateTo("Hashing"); shell.navigateTo("Check Digits");
                            assertEquals("validate", state.current().actionId(), "return restores the last check action");
                            checkInput.setText("79927398714"); generic.handleValidateCheckDigit(); assertTrue(checkOutput.getText().contains("INVALID"));
                            ContextualHelpTestSupport.shiftF1(sharedCheckInputHelp);
                            assertEquals("validate", state.current().actionId()); assertEquals("input", state.current().fieldId());
                            ComboBox<String> checkAlgorithm = field(generic, "checkDigitAlgorithmCombo");
                            checkAlgorithm.setValue("Damm"); checkInput.setText("572"); generic.handleCalculateCheckDigit();
                            assertTrue(checkOutput.getText().contains("5724"));
                            checkInput.setText("5724"); generic.handleValidateCheckDigit(); assertTrue(checkOutput.getText().contains("VALID"));
                            checkAlgorithm.setValue("Verhoeff"); checkInput.setText("572"); generic.handleCalculateCheckDigit();
                            assertTrue(checkOutput.getText().contains("5727"));
                            checkInput.setText("5727"); generic.handleValidateCheckDigit(); assertTrue(checkOutput.getText().contains("VALID"));
                            checkAlgorithm.setValue("AMEX SE (Luhn/Mod 10)"); checkInput.setText("572"); generic.handleCalculateCheckDigit();
                            assertTrue(checkOutput.getText().contains("5728"));
                            checkAlgorithm.setValue("Luhn (Mod 10)"); checkInput.setText("00123"); generic.handleCalculateCheckDigit();
                            assertTrue(checkOutput.getText().contains("001230"), "leading zeros remain part of the base");

                            shell.navigateTo("Modular Arithmetic");
                            ComboBox<String> modOperation = field(generic, "modOperationCombo");
                            ((TextField) field(generic, "modOperandAField")).setText("03");
                            ((TextField) field(generic, "modOperandBField")).setText("04");
                            ((TextField) field(generic, "modModulusField")).setText("0B");
                            modOperation.setValue("Addition (a + b) mod m"); generic.handleModularCalculate();
                            assertTrue(((TextArea) field(generic, "modResultArea")).getText().contains("\n7\n"));
                            modOperation.setValue("Reciprocal (1/a) mod m");
                            ((TextField) field(generic, "modOperandAField")).setText("6"); ((TextField) field(generic, "modModulusField")).setText("9");
                            generic.handleModularCalculate(); assertTrue(((TextArea) field(generic, "modResultArea")).getText().contains("gcd(a, m) = 1"));

                            TitledPane uuidPane = field(generic, "uuidPane"); uuidPane.setExpanded(true);
                            root.applyCss(); root.layout();
                            javafx.scene.Node uuidGenerateHelp = ContextualHelpTestSupport.control(root, "contextHelp_aux_uuid_generate_action");
                            javafx.scene.Node uuidCopyHelp = ContextualHelpTestSupport.control(root, "contextHelp_aux_uuid_copy_action");
                            assertNotNull(uuidGenerateHelp); assertNotNull(uuidCopyHelp);
                            Button uuidGenerate = (Button) root.lookup("#help_handleGenerateUUID"); assertNotNull(uuidGenerate); uuidGenerate.fire();
                            String uuidText = ((TextField) field(generic, "uuidOutputField")).getText();
                            UUID uuid = UUID.fromString(uuidText);
                            assertEquals(4, uuid.version()); assertEquals(2, uuid.variant()); assertEquals(36, uuidText.length());
                            Button uuidCopy = (Button) root.lookup("#help_handleCopyUUID"); assertNotNull(uuidCopy); uuidCopy.fire();
                            assertEquals(uuidText, javafx.scene.input.Clipboard.getSystemClipboard().getString());
                            ContextualHelpTestSupport.shiftF1(uuidGenerateHelp); assertEquals("aux_uuid", state.current().operationId()); assertEquals("generate", state.current().actionId());
                            VBox help = field(shell, "contextualHelpContent");
                            assertTrue(help.getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast)
                                    .anyMatch(label -> label.getText().equals(I18nService.getInstance().text("help.action.aux_uuid.generate.description"))),
                                    help.getChildren().stream().map(node -> node instanceof Label label ? label.getText() : node.getClass().getSimpleName()).toList().toString());
                            shell.navigateTo("Hashing");
                            assertEquals("op_gen_hash", state.current().operationId());
                            assertEquals("calculate", state.current().actionId()); assertEquals("input", state.current().fieldId());
                            assertEquals("B051_PRIVATE_SENTINEL", hashInput.getText());
                            assertEquals("B051_RESULT_SENTINEL", hashOutput.getText());
                            assertTrue(hashInputHelp.isFocusTraversable());
                            mode.setSelected(false); shell.handleToggleContextualHelpMode();
                            HelpContext beforeModeOffFocus = state.current();
                            hashInputHelp.requestFocus();
                            assertEquals(beforeModeOffFocus, state.current(), "Focus must not publish help once the mode is off");
                            shell.shutdown(); owner.close(); owner.setScene(null);
                            refs[0] = Boolean.TRUE;
                        } catch (Exception error) { throw new AssertionError(error); }
                    });
                } finally {
                    Files.deleteIfExists(destination); Files.deleteIfExists(source); Files.deleteIfExists(comparison); Files.deleteIfExists(dir);
                }
            }
        }
    }

    @SuppressWarnings("unchecked") private static <T> T field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return (T) field.get(target);
    }
}
