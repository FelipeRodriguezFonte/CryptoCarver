package com.cryptocarver.ui;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextFlow;
import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;


import com.cryptocarver.util.DataConverter;
import com.cryptocarver.crypto.JOSEService;
import com.cryptocarver.crypto.JoseNoneJws;
import com.cryptocarver.crypto.JWEManualCekRecovery;
import com.cryptocarver.crypto.JoseKeyMaterial;
import com.cryptocarver.crypto.JoseJwkPolicy;
import com.cryptocarver.crypto.JweComposer;
import com.cryptocarver.crypto.JwtClaimsBuilder;
import com.cryptocarver.crypto.JwtValidator;
import com.cryptocarver.crypto.SignerConfig;
import com.cryptocarver.model.OperationResult;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.*;
import com.nimbusds.jose.jca.JCAContext;
import com.nimbusds.jwt.*;
import com.nimbusds.jose.util.Base64URL;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.text.TextFlow;
import javafx.scene.text.Text;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.UUID;
import java.util.Set;
import java.util.Collections;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;

import javafx.fxml.Initializable;
import java.net.URL;
import java.util.ResourceBundle;

import java.util.function.Supplier;

final class JoseJweCoordinator extends JoseCoordinatorSupport {
    record View(TextField jweAadField,
            TextField jweApuField,
            TextField jweApvField,
            TextArea jweAuthTagArea,
            TextArea jweCiphertextArea,
            CheckBox jweCompressCheck,
            ComboBox<String> jweContentAlgoCombo,
            TextField jweCtyField,
            TextArea jweCustomHeaderArea,
            TextArea jweDecodedHeaderArea,
            TextArea jweDecodedPayloadArea,
            ComboBox<String> jweDecryptKeyFormatCombo,
            TextArea jweDecryptedKeyArea,
            TextArea jweEncryptedKeyArea,
            TextArea jweHeaderArea,
            TextArea jweIVArea,
            TextArea jweInputArea,
            ComboBox<String> jweKeyAlgoCombo,
            ComboBox<String> jweKeyFormatCombo,
            TextField jweKidField,
            TextArea jweOutputArea,
            TextArea jwePayloadArea,
            TextField jwePbes2IterField,
            TextArea jwePrivateKeyArea,
            TextArea jwePublicKeyArea,
            ComboBox<String> jweSerializationCombo,
            Label jweStatusLabel,
            TextField jweTypField) { }
    private final Supplier<View> controls;
    private static final Logger LOG = LoggerFactory.getLogger(JoseJweCoordinator.class);
    JoseJweCoordinator(Supplier<View> controls, Supplier<StatusReporter> reporter) { super(reporter); this.controls = controls; }
    private View view() { return controls.get(); }
    void handleGenerateJWE() {

            if (isBlank(view().jwePayloadArea())) { showValidation(t("module.jose.feedback.inputRequired"), "jwePayloadArea"); return; }
            if (isBlank(view().jwePublicKeyArea())) { showValidation(t("module.jose.feedback.keyRequired"), "jwePublicKeyArea"); return; }
            int iterations = JweComposer.DEFAULT_PBES2_ITERATIONS;
            if (view().jwePbes2IterField() != null && !view().jwePbes2IterField().getText().isBlank()) {
                try {
                    iterations = Integer.parseInt(view().jwePbes2IterField().getText().trim());
                } catch (NumberFormatException e) {
                    iterations = -1;
                }
                if (iterations < 1000) {
                    showValidation(t("module.jose.feedback.pbes2Iterations"), "jwePbes2IterField");
                    return;
                }
            }

            this.generateJWE(
                    view().jwePayloadArea().getText(),
                    view().jweKeyAlgoCombo().getValue(),
                    view().jweContentAlgoCombo().getValue(),
                    view().jwePublicKeyArea().getText(),
                    view().jweCompressCheck().isSelected(),
                    new JweComposer.HeaderOptions(textOf(view().jweKidField()), textOf(view().jweTypField()), textOf(view().jweCtyField()),
                            textOf(view().jweApuField()), textOf(view().jweApvField()), textOf(view().jweCustomHeaderArea())),
                    secretEncoding(view().jweKeyFormatCombo()),
                    iterations,
                    JweComposer.Serialization.fromLabel(view().jweSerializationCombo() == null ? null : view().jweSerializationCombo().getValue()),
                    textOf(view().jweAadField()),
                    view().jweOutputArea());

        }

    void handleDecryptJWE() {

            if (isBlank(view().jweInputArea())) { showValidation(t("module.jose.feedback.inputRequired"), "jweInputArea"); return; }
            if (isBlank(view().jwePrivateKeyArea())) { showValidation(t("module.jose.feedback.keyRequired"), "jwePrivateKeyArea"); return; }

            this.decryptJWE(
                    view().jweInputArea().getText(),
                    view().jwePrivateKeyArea().getText(),
                    secretEncoding(view().jweDecryptKeyFormatCombo()),
                    view().jweDecodedHeaderArea(),
                    view().jweDecodedPayloadArea(),
                    view().jweHeaderArea(),
                    view().jweEncryptedKeyArea(),
                    view().jweDecryptedKeyArea(),
                    view().jweIVArea(),
                    view().jweCiphertextArea(),
                    view().jweAuthTagArea(),
                    view().jweStatusLabel());

        }

    public void generateJWE(String payload, String keyAlgo, String contentAlgo, String keyMaterial, boolean compress,
            JweComposer.HeaderOptions headerOptions, JoseKeyMaterial.SecretEncoding secretEncoding,
            int pbes2Iterations, JweComposer.Serialization serialization, String aad, TextArea outputArea) {
        try {
            String serialized = JweComposer.encrypt(payload, keyAlgo, contentAlgo, compress, headerOptions,
                    keyMaterial, secretEncoding, pbes2Iterations, serialization, aad);
            outputArea.setText(serialized);
            String status = "JWE Encrypted (" + keyAlgo + " / " + contentAlgo + ")";
            if (compress)
                status += " [Compressed]";
            OperationResult.Builder result = OperationResult.forOperation("JWE Encryption")
                    .input(payload.getBytes(StandardCharsets.UTF_8))
                    .output(serialized.getBytes(StandardCharsets.US_ASCII))
                    .detail("Key Algorithm", keyAlgo).detail("Content Algorithm", contentAlgo)
                    .detail("Compression", String.valueOf(compress))
                    .detail("Serialization", serialization == null ? "Compact" : serialization.label())
                    .detail(com.cryptocarver.model.OperationDetail.secretDetail("Key Material", keyMaterial));
            if (aad != null && !aad.isBlank()) result.detail("AAD", aad);
            if (headerOptions != null && headerOptions.kid() != null && !headerOptions.kid().isBlank()) {
                result.detail("kid", headerOptions.kid().trim());
            }
            if (JWEAlgorithm.Family.PBES2.contains(JWEAlgorithm.parse(keyAlgo))) {
                result.detail("PBES2 Iterations", String.valueOf(pbes2Iterations));
            }
            addSecurityWarning(result, jweSecurityWarning(keyAlgo));
            String metadataWarning = metadataWarning(keyMaterial, JoseJwkPolicy.Operation.ENCRYPT);
            if (metadataWarning != null) result.detail("Security warning", metadataWarning);
            reporter().publish(result.status(status).build());

        } catch (Exception e) {
            reporter().showError("JWE Encryption Error", e.getMessage());
            // No exception attached: provider messages may echo key material.
            LOG.error("JWE encryption failed for key-management algorithm {}", keyAlgo);
        }
    }

    public void decryptJWE(String jweString, String privateKeyPEM, JoseKeyMaterial.SecretEncoding secretEncoding,
            TextArea headerOut, TextArea payloadOut,
            TextArea jweHeaderArea, TextArea jweEncryptedKeyArea, TextArea jweDecryptedKeyArea,
            TextArea jweIVArea, TextArea jweCiphertextArea, TextArea jweAuthTagArea,
            Label statusLabel) {
        String algorithmName = "(unknown)";
        try {
            if (jweString == null || jweString.isBlank()) {
                throw new IllegalArgumentException("A JWE compact serialization is required.");
            }
            if (privateKeyPEM == null || privateKeyPEM.isBlank()) {
                throw new IllegalArgumentException("Key material is required to decrypt the JWE.");
            }
            if (jweString.trim().startsWith("{")) {
                algorithmName = "(JSON serialization)";
                JweComposer.JsonDecryption result = JweComposer.decryptJson(jweString.trim(), privateKeyPEM.trim(),
                        secretEncoding);
                String header = com.nimbusds.jose.util.JSONObjectUtils.toJSONString(result.effectiveHeader());
                headerOut.setText(header);
                payloadOut.setText(result.payload());
                jweHeaderArea.setText(header);
                jweEncryptedKeyArea.setText(result.encryptedKey() == null ? "" : result.encryptedKey().toString());
                jweDecryptedKeyArea.setText("Manual CEK preview is only available for compact serialization.");
                jweIVArea.setText(result.iv() == null ? "" : result.iv() + " \n[Hex: "
                        + DataConverter.bytesToHex(result.iv().decode()) + "]");
                jweCiphertextArea.setText(result.cipherText().toString());
                jweAuthTagArea.setText(result.authTag() == null ? "" : result.authTag() + " \n[Hex: "
                        + DataConverter.bytesToHex(result.authTag().decode()) + "]");
                statusLabel.setText(t("module.jose.decryptionSuccessful") + " (recipient "
                        + (result.recipientIndex() + 1) + "/" + result.recipientCount() + ")");
                statusLabel.setStyle("-fx-text-fill: green;");
                OperationResult.Builder published = OperationResult.forOperation("JWE Decryption")
                        .input(jweString.getBytes(StandardCharsets.UTF_8))
                        .output(result.payload().getBytes(StandardCharsets.UTF_8), com.cryptocarver.model.OperationDetail.Classification.SECRET)
                        .detail("Key Algorithm", String.valueOf(result.effectiveHeader().get("alg")))
                        .detail("Content Algorithm", String.valueOf(result.effectiveHeader().get("enc")))
                        .detail("Serialization", "JSON")
                        .detail("Recipient", (result.recipientIndex() + 1) + " of " + result.recipientCount());
                if (result.aad() != null) published.detail("AAD", result.aad());
                addSecurityWarning(published, jweSecurityWarning(String.valueOf(result.effectiveHeader().get("alg"))));
                reporter().publish(published.status(t("module.jose.feedback.statusJweDecrypted")).build());
                return;
            }

            final JWEObject jweObject;
            try {
                jweObject = JWEObject.parse(jweString);
            } catch (java.text.ParseException e) {
                throw new IllegalArgumentException("The JWE is corrupt or has an invalid compact serialization.", e);
            }

            JWEAlgorithm alg = jweObject.getHeader().getAlgorithm();
            algorithmName = alg == null ? "(missing)" : alg.getName();
            if (alg == null) {
                throw new IllegalArgumentException("The JWE header has no 'alg' parameter.");
            }

            JWEDecrypter decrypter;
            JweComposer.LoadedKey loaded = new JweComposer.LoadedKey();
            try {
                decrypter = JweComposer.decrypter(alg, privateKeyPEM.trim(), secretEncoding, loaded);
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalArgumentException("Key material is missing or incompatible with JWE algorithm " + algorithmName + ".", e);
            }
            PrivateKey privateKey = loaded.privateKey;
            byte[] secret = loaded.secret;

            try {
                jweObject.decrypt(decrypter);
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "JWE authentication failed for " + algorithmName
                                + ": the supplied key may be incorrect/incompatible or the JWE header/ciphertext may be corrupt.", e);
            }

            // 4. Display Parts
            headerOut.setText(jweObject.getHeader().toString());
            payloadOut.setText(jweObject.getPayload().toString());

            // 5. Visual Breakdown
            jweHeaderArea.setText(jweObject.getHeader().toString());

            Base64URL encryptedKey = jweObject.getEncryptedKey();
            jweEncryptedKeyArea.setText(encryptedKey != null ? encryptedKey.toString() : "");

            if (JWEAlgorithm.DIR.equals(alg)) {
                // The direct key is the CEK. Keep it out of automatic preview,
                // OperationResult, history, reports, clipboard and logs.
                jweDecryptedKeyArea.setText(directCekPreviewMessage());
            } else if (!JWEManualCekRecovery.isSupported(alg)) {
                jweDecryptedKeyArea.setText("Manual CEK preview is not available for " + algorithmName + ".");
            } else {
                try {
                    byte[] cek = JWEManualCekRecovery.recover(jweObject, privateKey, secret);
                    jweDecryptedKeyArea.setText(DataConverter.bytesToHex(cek));
                    java.util.Arrays.fill(cek, (byte) 0);
                } catch (JWEManualCekRecovery.ManualCekRecoveryException ex) {
                    jweDecryptedKeyArea.setText("Manual CEK preview error: " + ex.getMessage());
                }
            }

            jweIVArea.setText(jweObject.getIV() != null
                    ? jweObject.getIV().toString() + " \n[Hex: "
                            + com.cryptocarver.util.DataConverter.bytesToHex(jweObject.getIV().decode()) + "]"
                    : "");
            jweCiphertextArea.setText(jweObject.getCipherText() != null ? jweObject.getCipherText().toString() : "");
            jweAuthTagArea
                    .setText(
                            jweObject.getAuthTag() != null
                                    ? jweObject.getAuthTag().toString() + " \n[Hex: "
                                            + com.cryptocarver.util.DataConverter
                                                    .bytesToHex(jweObject.getAuthTag().decode())
                                            + "]"
                                    : "");

            statusLabel.setText(t("module.jose.decryptionSuccessful"));
            statusLabel.setStyle("-fx-text-fill: green;");

            String payload = jweObject.getPayload().toString();
            reporter().publish(buildJweDecryptionResult(jweString, payload, jweObject, privateKeyPEM));

        } catch (Exception e) {
            statusLabel.setText(t("module.jose.decryptionFailed"));
            statusLabel.setStyle("-fx-text-fill: red;");
            String message = e.getMessage();
            if (message == null || message.isBlank()) {
                message = "JWE decryption failed for " + algorithmName + ".";
            }
            reporter().showError("JWE Decryption Error", message);
            // Do not attach the exception: CEKs and other secret material must
            // never reach application logs through a provider exception.
            LOG.error("JWE decryption failed for key-management algorithm {}", algorithmName);
        }
    }

    OperationResult buildJweDecryptionResult(String jweString, String payload, JWEObject jweObject) {
        return buildJweDecryptionResult(jweString, payload, jweObject, null);
    }

    OperationResult buildJweDecryptionResult(String jweString, String payload, JWEObject jweObject, String keyMaterial) {
        OperationResult.Builder result = OperationResult.forOperation("JWE Decryption")
                .input(jweString.getBytes(StandardCharsets.US_ASCII))
                .output(payload.getBytes(StandardCharsets.UTF_8), com.cryptocarver.model.OperationDetail.Classification.SECRET)
                .detail("Key Algorithm", jweObject.getHeader().getAlgorithm().getName())
                .detail("Content Algorithm", jweObject.getHeader().getEncryptionMethod().getName())
                ;
        addSecurityWarning(result, jweSecurityWarning(jweObject.getHeader().getAlgorithm().getName()));
        String metadataWarning = metadataWarning(keyMaterial, JoseJwkPolicy.Operation.DECRYPT);
        if (metadataWarning != null) result.detail("Security warning", metadataWarning);
        return result.status(t("module.jose.feedback.statusJweDecrypted")).build();
    }

    static String directCekPreviewMessage() {
        return "Direct encryption: the CEK is the supplied direct key and is not displayed automatically.";
    }

}
