package com.cryptocarver.ui;

import com.cryptocarver.crypto.AsymmetricKeyOperations;
import com.cryptocarver.crypto.COSEOperations;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.util.DataConverter;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.ResourceBundle;
import java.util.function.Consumer;

/**
 * Controller for the COSE (RFC 9052/9053) pane — {@code COSE_Sign1}/{@code COSE_Mac0}/
 * {@code COSE_Encrypt0}, mirroring {@link JOSEController}'s section-switching and
 * validate-then-delegate-then-publish pattern exactly. See {@link COSEOperations} for the
 * crypto layer and its documented scope/algorithm limitations.
 */
public class COSEController implements Initializable {

    private static final Logger LOG = LoggerFactory.getLogger(COSEController.class);

    @FXML private VBox coseContainer;
    @FXML private VBox sign1Section;
    @FXML private VBox mac0Section;
    @FXML private VBox encrypt0Section;

    // Sign1
    @FXML private ComboBox<String> sign1AlgoCombo;
    @FXML private TextArea sign1PrivateKeyArea;
    @FXML private TextArea sign1PublicKeyArea;
    @FXML private TextArea sign1PayloadArea;
    @FXML private TextArea sign1OutputArea;
    @FXML private TextArea verify1PublicKeyArea;
    @FXML private TextArea verify1MessageArea;
    @FXML private TextArea verify1ResultArea;

    // Mac0
    @FXML private ComboBox<String> mac0AlgoCombo;
    @FXML private TextField mac0KeyField;
    @FXML private TextArea mac0PayloadArea;
    @FXML private TextArea mac0OutputArea;
    @FXML private TextField verifyMac0KeyField;
    @FXML private TextArea verifyMac0MessageArea;
    @FXML private TextArea verifyMac0ResultArea;

    // Encrypt0
    @FXML private ComboBox<String> encrypt0AlgoCombo;
    @FXML private TextField encrypt0KeyField;
    @FXML private TextArea encrypt0PayloadArea;
    @FXML private TextArea encrypt0OutputArea;
    @FXML private TextField decrypt0KeyField;
    @FXML private TextArea decrypt0MessageArea;
    @FXML private TextArea decrypt0ResultArea;

    private StatusReporter statusReporter;
    private ModuleI18n.Binding moduleI18n;

    /** Required by FXMLLoader when this controller is used from an fx:include. */
    public COSEController() {
    }

    public COSEController(StatusReporter statusReporter) {
        this.statusReporter = statusReporter;
    }

    /** Connects the child module to the application-wide result publisher. */
    public void setReporter(StatusReporter statusReporter) {
        this.statusReporter = statusReporter;
    }

    private String t(String key, Object... args) {
        return com.cryptocarver.service.I18nService.getInstance().text(key, args);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        moduleI18n = ModuleI18n.bind(coseContainer, ModuleTextCatalog.cose());

        if (sign1AlgoCombo != null && sign1AlgoCombo.getItems().isEmpty()) {
            sign1AlgoCombo.getItems().addAll("ES256", "ES384", "ES512", "PS256", "PS384", "PS512", "EDDSA");
            sign1AlgoCombo.getSelectionModel().selectFirst();
        }
        if (mac0AlgoCombo != null && mac0AlgoCombo.getItems().isEmpty()) {
            mac0AlgoCombo.getItems().addAll("HS256", "HS384", "HS512");
            mac0AlgoCombo.getSelectionModel().selectFirst();
        }
        if (encrypt0AlgoCombo != null && encrypt0AlgoCombo.getItems().isEmpty()) {
            encrypt0AlgoCombo.getItems().addAll("A128GCM", "A192GCM", "A256GCM");
            encrypt0AlgoCombo.getSelectionModel().selectFirst();
        }
    }

    /** Shows the section matching {@code sectionName}'s prefix, hiding the others — same pattern as {@link JOSEController#showSection}. */
    public void showSection(String sectionName) {
        if (coseContainer != null) {
            coseContainer.setManaged(true);
            coseContainer.setVisible(true);
        }
        if (sign1Section != null) {
            sign1Section.setManaged(false);
            sign1Section.setVisible(false);
        }
        if (mac0Section != null) {
            mac0Section.setManaged(false);
            mac0Section.setVisible(false);
        }
        if (encrypt0Section != null) {
            encrypt0Section.setManaged(false);
            encrypt0Section.setVisible(false);
        }

        if (sectionName == null) return;

        if (sectionName.startsWith("COSE Sign1") || sectionName.startsWith("COSE Verify1")) {
            if (sign1Section != null) {
                sign1Section.setManaged(true);
                sign1Section.setVisible(true);
            }
        } else if (sectionName.startsWith("COSE MAC0")) {
            if (mac0Section != null) {
                mac0Section.setManaged(true);
                mac0Section.setVisible(true);
            }
        } else if (sectionName.startsWith("COSE Encrypt0") || sectionName.startsWith("COSE Decrypt0")) {
            if (encrypt0Section != null) {
                encrypt0Section.setManaged(true);
                encrypt0Section.setVisible(true);
            }
        }
    }

    // ---------------------------------------------------------------- Sign1

    @FXML
    private void handleSign1() {
        try {
            String payloadText = textOf(sign1PayloadArea);
            String privatePem = textOf(sign1PrivateKeyArea);
            String publicPem = textOf(sign1PublicKeyArea);
            String algoName = sign1AlgoCombo == null ? null : sign1AlgoCombo.getValue();

            if (isBlank(payloadText)) { showValidation(t("module.cose.payloadRequired"), "sign1PayloadArea"); return; }
            if (isBlank(privatePem)) { showValidation(t("module.cose.keyRequired"), "sign1PrivateKeyArea"); return; }

            byte[] payload = payloadText.getBytes(StandardCharsets.UTF_8);
            PrivateKey privateKey = AsymmetricKeyOperations.importPrivateKeyPEMAuto(privatePem);
            PublicKey publicKey;
            if (!isBlank(publicPem)) {
                publicKey = AsymmetricKeyOperations.importPublicKeyPEMAuto(publicPem);
            } else {
                // derivePublicKey only supports RSA/EC (not Ed25519) — that's fine here, the
                // public key is optional for signing; just proceed without it instead of
                // failing the whole operation over a convenience that doesn't apply.
                PublicKey derived = null;
                try {
                    derived = AsymmetricKeyOperations.derivePublicKey(privateKey);
                } catch (Exception notDerivable) {
                    // expected for Ed25519 — publicKey stays null
                }
                publicKey = derived;
            }
            COSEOperations.SignAlgorithm algorithm = COSEOperations.SignAlgorithm.valueOf(algoName);

            byte[] message = COSEOperations.sign1(payload, privateKey, publicKey, algorithm);
            String hex = DataConverter.bytesToHex(message).toUpperCase();
            sign1OutputArea.setText(hex);
            updateStatus(t("module.cose.status.signed"));

            if (statusReporter != null) {
                statusReporter.publish(OperationResult.forOperation("COSE Sign1")
                        .input(payload).output(message)
                        .detail("Algorithm", algoName)
                        .status(t("module.cose.status.signed")).build());
            }
        } catch (Exception e) {
            showValidation(t("module.cose.operation", e.getMessage()), "sign1PrivateKeyArea");
            updateStatus(t("module.cose.status.signFailed"));
            logFailure("sign1", e);
        }
    }

    @FXML
    private void handleVerify1() {
        try {
            String publicPem = textOf(verify1PublicKeyArea);
            String messageHex = textOf(verify1MessageArea).replaceAll("\\s+", "");

            if (isBlank(publicPem)) { showValidation(t("module.cose.keyRequired"), "verify1PublicKeyArea"); return; }
            if (isBlank(messageHex)) { showValidation(t("module.cose.messageRequired"), "verify1MessageArea"); return; }
            if (!messageHex.matches("[0-9A-Fa-f]+")) { showValidation(t("module.cose.messageInvalid"), "verify1MessageArea"); return; }

            PublicKey publicKey = AsymmetricKeyOperations.importPublicKeyPEMAuto(publicPem);
            byte[] message = DataConverter.hexToBytes(messageHex);

            COSEOperations.Sign1Result result = COSEOperations.verify1(message, publicKey);
            String payloadText = new String(result.getPayload(), StandardCharsets.UTF_8);
            String resultText = "Signature Verified: " + (result.isVerified() ? "YES" : "NO — do not trust this payload")
                    + "\n\nPayload:\n" + payloadText;
            verify1ResultArea.setText(resultText);
            updateStatus(result.isVerified() ? t("module.cose.status.verified") : t("module.cose.status.notVerified"));

            if (statusReporter != null) {
                statusReporter.publish(OperationResult.forOperation("COSE Verify1")
                        .input(message).output(result.getPayload())
                        .detail("Signature Verified", String.valueOf(result.isVerified()))
                        .status(result.isVerified() ? t("module.cose.status.verified") : t("module.cose.status.notVerified")).build());
            }
        } catch (Exception e) {
            showValidation(t("module.cose.operation", e.getMessage()), "verify1MessageArea");
            updateStatus(t("module.cose.status.verifyFailed"));
            logFailure("verify1", e);
        }
    }

    // ---------------------------------------------------------------- Mac0

    @FXML
    private void handleGenerateMac0Key() {
        if (mac0KeyField == null || mac0AlgoCombo == null) return;
        String algoName = mac0AlgoCombo.getValue();
        int bytes = algoName == null ? 32 : COSEOperations.MacAlgorithm.valueOf(algoName).requiredKeyBytes();
        mac0KeyField.setText(DataConverter.bytesToHex(randomBytes(bytes)).toUpperCase());
    }

    @FXML
    private void handleMac0() {
        try {
            String payloadText = textOf(mac0PayloadArea);
            String keyHex = textOf(mac0KeyField).replaceAll("\\s+", "");
            String algoName = mac0AlgoCombo == null ? null : mac0AlgoCombo.getValue();

            if (isBlank(payloadText)) { showValidation(t("module.cose.payloadRequired"), "mac0PayloadArea"); return; }
            if (isBlank(keyHex)) { showValidation(t("module.cose.keyRequired"), "mac0KeyField"); return; }
            if (!keyHex.matches("[0-9A-Fa-f]+")) { showValidation(t("module.cose.keyInvalid"), "mac0KeyField"); return; }

            byte[] payload = payloadText.getBytes(StandardCharsets.UTF_8);
            byte[] key = DataConverter.hexToBytes(keyHex);
            COSEOperations.MacAlgorithm algorithm = COSEOperations.MacAlgorithm.valueOf(algoName);

            byte[] message = COSEOperations.mac0(payload, key, algorithm);
            mac0OutputArea.setText(DataConverter.bytesToHex(message).toUpperCase());
            updateStatus(t("module.cose.status.maced"));

            if (statusReporter != null) {
                statusReporter.publish(OperationResult.forOperation("COSE MAC0")
                        .input(payload).output(message)
                        .detail("Algorithm", algoName)
                        .status(t("module.cose.status.maced")).build());
            }
        } catch (Exception e) {
            showValidation(t("module.cose.operation", e.getMessage()), "mac0KeyField");
            updateStatus(t("module.cose.status.macFailed"));
            logFailure("mac0", e);
        }
    }

    @FXML
    private void handleVerifyMac0() {
        try {
            String keyHex = textOf(verifyMac0KeyField).replaceAll("\\s+", "");
            String messageHex = textOf(verifyMac0MessageArea).replaceAll("\\s+", "");

            if (isBlank(keyHex)) { showValidation(t("module.cose.keyRequired"), "verifyMac0KeyField"); return; }
            if (!keyHex.matches("[0-9A-Fa-f]+")) { showValidation(t("module.cose.keyInvalid"), "verifyMac0KeyField"); return; }
            if (isBlank(messageHex)) { showValidation(t("module.cose.messageRequired"), "verifyMac0MessageArea"); return; }
            if (!messageHex.matches("[0-9A-Fa-f]+")) { showValidation(t("module.cose.messageInvalid"), "verifyMac0MessageArea"); return; }

            byte[] key = DataConverter.hexToBytes(keyHex);
            byte[] message = DataConverter.hexToBytes(messageHex);

            COSEOperations.Mac0Result result = COSEOperations.verifyMac0(message, key);
            String payloadText = new String(result.getPayload(), StandardCharsets.UTF_8);
            String resultText = "MAC Verified: " + (result.isVerified() ? "YES" : "NO — do not trust this payload")
                    + "\n\nPayload:\n" + payloadText;
            verifyMac0ResultArea.setText(resultText);
            updateStatus(result.isVerified() ? t("module.cose.status.macVerified") : t("module.cose.status.macNotVerified"));

            if (statusReporter != null) {
                statusReporter.publish(OperationResult.forOperation("COSE Verify MAC0")
                        .input(message).output(result.getPayload())
                        .detail("MAC Verified", String.valueOf(result.isVerified()))
                        .status(result.isVerified() ? t("module.cose.status.macVerified") : t("module.cose.status.macNotVerified")).build());
            }
        } catch (Exception e) {
            showValidation(t("module.cose.operation", e.getMessage()), "verifyMac0MessageArea");
            updateStatus(t("module.cose.status.macVerifyFailed"));
            logFailure("verifyMac0", e);
        }
    }

    // ---------------------------------------------------------------- Encrypt0

    @FXML
    private void handleGenerateEncrypt0Key() {
        if (encrypt0KeyField == null || encrypt0AlgoCombo == null) return;
        String algoName = encrypt0AlgoCombo.getValue();
        int bytes = algoName == null ? 32 : COSEOperations.EncryptAlgorithm.valueOf(algoName).requiredKeyBytes();
        encrypt0KeyField.setText(DataConverter.bytesToHex(randomBytes(bytes)).toUpperCase());
    }

    @FXML
    private void handleEncrypt0() {
        try {
            String payloadText = textOf(encrypt0PayloadArea);
            String keyHex = textOf(encrypt0KeyField).replaceAll("\\s+", "");
            String algoName = encrypt0AlgoCombo == null ? null : encrypt0AlgoCombo.getValue();

            if (isBlank(payloadText)) { showValidation(t("module.cose.payloadRequired"), "encrypt0PayloadArea"); return; }
            if (isBlank(keyHex)) { showValidation(t("module.cose.keyRequired"), "encrypt0KeyField"); return; }
            if (!keyHex.matches("[0-9A-Fa-f]+")) { showValidation(t("module.cose.keyInvalid"), "encrypt0KeyField"); return; }

            byte[] payload = payloadText.getBytes(StandardCharsets.UTF_8);
            byte[] key = DataConverter.hexToBytes(keyHex);
            COSEOperations.EncryptAlgorithm algorithm = COSEOperations.EncryptAlgorithm.valueOf(algoName);

            byte[] message = COSEOperations.encrypt0(payload, key, algorithm);
            encrypt0OutputArea.setText(DataConverter.bytesToHex(message).toUpperCase());
            updateStatus(t("module.cose.status.encrypted"));

            if (statusReporter != null) {
                statusReporter.publish(OperationResult.forOperation("COSE Encrypt0")
                        .input(payload).output(message, com.cryptocarver.model.OperationDetail.Classification.SECRET)
                        .detail("Algorithm", algoName)
                        .status(t("module.cose.status.encrypted")).build());
            }
        } catch (Exception e) {
            showValidation(t("module.cose.operation", e.getMessage()), "encrypt0KeyField");
            updateStatus(t("module.cose.status.encryptFailed"));
            logFailure("encrypt0", e);
        }
    }

    @FXML
    private void handleDecrypt0() {
        try {
            String keyHex = textOf(decrypt0KeyField).replaceAll("\\s+", "");
            String messageHex = textOf(decrypt0MessageArea).replaceAll("\\s+", "");

            if (isBlank(keyHex)) { showValidation(t("module.cose.keyRequired"), "decrypt0KeyField"); return; }
            if (!keyHex.matches("[0-9A-Fa-f]+")) { showValidation(t("module.cose.keyInvalid"), "decrypt0KeyField"); return; }
            if (isBlank(messageHex)) { showValidation(t("module.cose.messageRequired"), "decrypt0MessageArea"); return; }
            if (!messageHex.matches("[0-9A-Fa-f]+")) { showValidation(t("module.cose.messageInvalid"), "decrypt0MessageArea"); return; }

            byte[] key = DataConverter.hexToBytes(keyHex);
            byte[] message = DataConverter.hexToBytes(messageHex);

            byte[] payload = COSEOperations.decrypt0(message, key);
            String payloadText = new String(payload, StandardCharsets.UTF_8);
            decrypt0ResultArea.setText(payloadText);
            updateStatus(t("module.cose.status.decrypted"));

            if (statusReporter != null) {
                statusReporter.publish(OperationResult.forOperation("COSE Decrypt0")
                        .input(message).output(payload, com.cryptocarver.model.OperationDetail.Classification.SECRET)
                        .status(t("module.cose.status.decrypted")).build());
            }
        } catch (Exception e) {
            // A wrong key or tampered ciphertext surfaces here as an AEAD tag failure —
            // deliberately vague to the user (do not distinguish "wrong key" from "tampered"),
            // same principle as every other authenticated-decryption failure in this app.
            showValidation(t("module.cose.status.decryptFailed"), "decrypt0MessageArea");
            updateStatus(t("module.cose.status.decryptFailed"));
            logFailure("decrypt0", e);
        }
    }

    // ---------------------------------------------------------------- Clear / Reset

    @FXML
    private void handleClear() {
        clearFields();
        updateStatus(t("module.cose.clearStatus"));
    }

    @FXML
    private void handleReset() {
        clearFields();
        if (sign1AlgoCombo != null) sign1AlgoCombo.getSelectionModel().selectFirst();
        if (mac0AlgoCombo != null) mac0AlgoCombo.getSelectionModel().selectFirst();
        if (encrypt0AlgoCombo != null) encrypt0AlgoCombo.getSelectionModel().selectFirst();
        updateStatus(t("module.cose.resetStatus"));
    }

    private void clearFields() {
        clear(sign1PrivateKeyArea, sign1PublicKeyArea, sign1PayloadArea, sign1OutputArea,
                verify1PublicKeyArea, verify1MessageArea, verify1ResultArea,
                mac0PayloadArea, mac0OutputArea, verifyMac0MessageArea, verifyMac0ResultArea,
                encrypt0PayloadArea, encrypt0OutputArea, decrypt0MessageArea, decrypt0ResultArea);
        clear(mac0KeyField, verifyMac0KeyField, encrypt0KeyField, decrypt0KeyField);
    }

    private static void clear(TextArea... areas) {
        for (TextArea area : areas) {
            if (area != null) area.clear();
        }
    }

    private static void clear(TextField... fields) {
        for (TextField field : fields) {
            if (field != null) field.clear();
        }
    }

    // ---------------------------------------------------------------- Helpers

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    private static String textOf(TextArea area) {
        return area == null || area.getText() == null ? "" : area.getText().trim();
    }

    private static String textOf(TextField field) {
        return field == null || field.getText() == null ? "" : field.getText().trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void showValidation(String message, String fieldKey) {
        String safeMessage = InlineErrorPresenter.redactSecrets(message);
        UserFacingError error = new UserFacingError(t("module.cose.errorTitle"), safeMessage, safeMessage, fieldKey);
        if (statusReporter != null) {
            statusReporter.showError(error);
        }
    }

    private void updateStatus(String message) {
        if (statusReporter != null) statusReporter.updateStatus(message);
    }

    private void logFailure(String operation, Exception error) {
        StringWriter trace = new StringWriter();
        error.printStackTrace(new PrintWriter(trace));
        LOG.error("COSE {} failed:\n{}", operation, InlineErrorPresenter.redactSecrets(trace.toString()));
    }
}
