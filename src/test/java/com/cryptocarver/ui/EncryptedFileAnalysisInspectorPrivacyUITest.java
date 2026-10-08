package com.cryptocarver.ui;

import com.cryptocarver.crypto.SymmetricCipher;
import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.util.DataConverter;
import com.cryptocarver.utils.OperationHistory;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Real shell and presenter: the inspector renders byte counts, not decrypted bytes. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class EncryptedFileAnalysisInspectorPrivacyUITest extends EmvExtractionCharacterizationSupport {
    @ParameterizedTest
    @EnumSource(value = SecretVisibilityProfile.class, names = {"MASKED", "REDACTED"})
    void inspectorControlsShowCountsAndDetailsWithoutSecrets(SecretVisibilityProfile profile) throws Exception {
        onFx(() -> {
            OperationHistory legacy = OperationHistory.getInstance();
            List<OperationHistory.OperationEntry> previous = legacy.getHistory();
            Path previousPath = (Path) get(legacy, "historyFilePath");
            @SuppressWarnings("unchecked")
            List<OperationHistory.OperationEntry> entries = (List<OperationHistory.OperationEntry>) get(legacy, "history");
            try {
                set(legacy, "historyFilePath", tempDir.resolve("legacy-history.json"));
                entries.clear();
                AppSettings.getInstance().setSecretVisibilityProfile(profile);
                Random random = new Random(76L);
                byte[] key = new byte[16];
                byte[] iv = new byte[16];
                random.nextBytes(key);
                random.nextBytes(iv);
                String marker = "Invented confidential recovered text for assignment 76.";
                byte[] plaintext = (marker + "\n" + marker + "\n").getBytes(StandardCharsets.US_ASCII);
                byte[] encrypted = SymmetricCipher.encrypt(plaintext, key, "AES-128", "CBC", "PKCS5Padding", iv, null);
                Path source = tempDir.resolve("privacy.bin");
                Files.write(source, encrypted);
                shell.navigateTo("Symmetric Ciphers");
                CipherController cipher = (CipherController) get(shell, "cipherContainerController");
                ((TextField) get(cipher, "symmetricKeyField")).setText(DataConverter.bytesToHex(key));
                ((TextField) get(cipher, "ivField")).setText(DataConverter.bytesToHex(iv));
                var options = new EncryptedFileAnalyzer.FileAnalysisOptions(new int[] {64}, true, false, 8,
                        EncryptedFileAnalyzer.FileDataEncoding.RAW, 262144);
                cipher.handleAnalyzeEncryptedFile(source, options);
                assertInstanceOf(OperationInspectorPresenter.class, get(shell, "inspectorPresenter"));
                assertEquals("Encrypted File Analysis", ((Label) get(shell, "operationLabel")).getText());
                assertEquals(String.valueOf(encrypted.length), ((Label) get(shell, "inputBytesLabel")).getText());
                assertEquals(String.valueOf(plaintext.length), ((Label) get(shell, "outputBytesLabel")).getText());
                Parent inspector = (Parent) get(shell, "inspectorPanel");
                Parent details = (Parent) get(shell, "inspectorDetailsContainer");
                String detailText = nodeText(details);
                assertTrue(detailText.contains("Best Algorithm:"), detailText);
                assertTrue(detailText.contains("AES-128"), detailText);
                assertTrue(detailText.contains("Best Score:"), detailText);
                assertTrue(detailText.contains("Best Confidence:"), detailText);
                assertControlsSafe(inspector, marker, DataConverter.bytesToHex(key));
                List<String> violations = new ArrayList<>();
                inspectSurfaces(profile, "Encrypted File Analysis", List.of(marker, DataConverter.bytesToHex(key)),
                        shell, root, new ArrayList<>(), violations);
                assertTrue(violations.isEmpty(), String.join("\n", violations));
                assertEquals(1, legacy.getHistory().size());
                for (var entry : legacy.getHistory()) {
                    check("legacy history input", entry.getInput(), marker, DataConverter.bytesToHex(key));
                    check("legacy history output", entry.getOutput(), marker, DataConverter.bytesToHex(key));
                }
            } finally {
                entries.clear();
                entries.addAll(previous);
                set(legacy, "historyFilePath", previousPath);
            }
        });
    }

    private static void assertControlsSafe(Node node, String marker, String key) {
        String location = location(node);
        String text = node instanceof Labeled labeled ? labeled.getText()
                : node instanceof TextInputControl input ? input.getText() : "";
        check(location, text, marker, key);
        check(location + " accessibleText", node.getAccessibleText(), marker, key);
        check(location + " accessibleHelp", node.getAccessibleHelp(), marker, key);
        if (node instanceof javafx.scene.control.Control control && control.getTooltip() != null) {
            check(location + " tooltip", control.getTooltip().getText(), marker, key);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) assertControlsSafe(child, marker, key);
        }
    }

    private static String location(Node node) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (current.getId() != null && !current.getId().isBlank()) {
                return "fx:id=" + current.getId() + " control=" + node.getClass().getSimpleName();
            }
        }
        return "control=" + node.getClass().getSimpleName();
    }

    private static void check(String location, String text, String marker, String key) {
        if (text == null) return;
        assertFalse(text.contains(marker), location + " exposes recovered text");
        assertFalse(text.toUpperCase(Locale.ROOT).contains(key.toUpperCase(Locale.ROOT)), location + " exposes invented key");
    }
}
