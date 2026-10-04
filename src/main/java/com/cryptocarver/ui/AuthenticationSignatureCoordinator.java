package com.cryptocarver.ui;

import com.cryptocarver.crypto.SignatureOperations;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Signs and verifies Authentication payloads against the live signature form. */
final class AuthenticationSignatureCoordinator {
    record View(Supplier<ComboBox<String>> algorithm,
                Supplier<TextArea> privateKeyArea,
                Supplier<TextArea> publicKeyArea,
                Supplier<TextField> signatureValue,
                Supplier<TextArea> inputArea,
                Supplier<ComboBox<String>> inputFormat,
                Supplier<TextArea> outputArea,
                Supplier<ComboBox<String>> outputFormat,
                Supplier<AuthenticationKeyState> keys) { }

    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationController.class);
    private final View view;
    private final Supplier<StatusReporter> reporter;

    AuthenticationSignatureCoordinator(View view, Supplier<StatusReporter> reporter) {
        this.view = view;
        this.reporter = reporter;
    }

    private StatusReporter reporter() { return reporter.get(); }

    void handleSign() {
        StatusReporter mainController = reporter();
        if (mainController != null && !mainController.checkPreflightReadiness("Digital Signatures", true)) {
            return;
        }
        AuthenticationKeyState keys = view.keys().get();
        PrivateKey currentPrivateKey = keys.privateKey();
        try {
            String algorithm = view.algorithm().get().getValue();
            if (algorithm == null) {
                mainController.showError("Algorithm Error", "Please select a signature algorithm");
                return;
            }

            TextArea privateKeyArea = view.privateKeyArea().get();
            if (privateKeyArea != null && !privateKeyArea.getText().trim().isEmpty()) {
                try {
                    String pem = privateKeyArea.getText().trim();
                    currentPrivateKey = com.cryptocarver.crypto.SharedMaterialParser.parsePrivateKeyPem(pem);
                    keys.setPrivateKey(currentPrivateKey);
                } catch (Exception e) {
                    mainController.showError("Key Parse Error",
                            "Could not parse private key from text area: " + e.getMessage());
                    return;
                }
            }

            if (currentPrivateKey == null) {
                mainController.showError(new UserFacingError("Missing Signing Key", "Paste or load a private key first.", "Provide a private key PEM in the key area.", "signaturePrivateKeyArea"));
                return;
            }

            byte[] data = AuthenticationDataFormatter.read(view.inputArea().get(), view.inputFormat().get(), mainController);
            if (data == null || data.length == 0) {
                mainController.showError(new UserFacingError("Missing Input Data", "Please enter data to sign.", "Provide text or binary input in the message field.", "authInputArea"));
                return;
            }

            String expectedKeyType = SignatureOperations.getExpectedKeyType(algorithm);
            String actualKeyType = currentPrivateKey.getAlgorithm();
            if (!actualKeyType.equals(expectedKeyType)) {
                mainController.showError(new UserFacingError("Key Mismatch",
                        String.format("Algorithm %s requires %s key, but pasted/loaded key is %s",
                                algorithm, expectedKeyType, actualKeyType),
                        "Provide a matching private key for the selected algorithm.",
                        "signaturePrivateKeyArea"));
                return;
            }

            byte[] signature = SignatureOperations.sign(data, currentPrivateKey, algorithm);
            AuthenticationDataFormatter.write(view.outputArea().get(), view.outputFormat().get(), signature, mainController);

            mainController.showInfo("Success",
                    String.format("Signature created successfully!\nAlgorithm: %s\nSignature size: %d bytes",
                            algorithm, signature.length));

            Map<String, String> details = new HashMap<>();
            details.put("Algorithm", algorithm);
            details.put("Data Size", data.length + " bytes");
            details.put("Signature Size", signature.length + " bytes");
            details.put("Key Type", currentPrivateKey != null ? currentPrivateKey.getAlgorithm() : "Unknown");
            mainController.publish(OperationResult.forOperation("Data Signed")
                    .input(data).output(signature, OperationDetail.Classification.SECRET).details(details)
                    .status("Signature created with " + algorithm).build());

        } catch (Exception e) {
            mainController.showError(e, "Signature Error", "signaturePrivateKeyArea");
            LOG.error("Digital signature creation failed", e);
        }
    }

    void handleVerify() {
        StatusReporter mainController = reporter();
        if (mainController != null && !mainController.checkPreflightReadiness("Digital Signatures", false)) {
            return;
        }
        AuthenticationKeyState keys = view.keys().get();
        PublicKey currentPublicKey = keys.publicKey();
        try {
            String algorithm = view.algorithm().get().getValue();
            if (algorithm == null) {
                mainController.showError("Algorithm Error", "Please select a signature algorithm");
                return;
            }

            TextArea publicKeyArea = view.publicKeyArea().get();
            if (publicKeyArea != null && !publicKeyArea.getText().trim().isEmpty()) {
                try {
                    String pem = publicKeyArea.getText().trim();
                    currentPublicKey = com.cryptocarver.crypto.SharedMaterialParser.parsePublicKeyPem(pem);
                    keys.setPublicKey(currentPublicKey);
                } catch (Exception e) {
                    mainController.showError("Key Parse Error",
                            "Could not parse public key from text area: " + e.getMessage());
                    return;
                }
            }

            if (currentPublicKey == null) {
                mainController.showError(new UserFacingError("Missing Public Key", "Paste or load a public key first.", "Provide a public key PEM in the key area.", "signaturePublicKeyArea"));
                return;
            }

            String signatureText = view.signatureValue().get().getText().trim();
            if (signatureText.isEmpty()) {
                mainController.showError(new UserFacingError("Missing Signature", "Please paste the signature in the verification field.", "Enter signature bytes/text to verify.", "signatureVerifyField"));
                return;
            }

            byte[] signature;
            try {
                signature = com.cryptocarver.crypto.SharedMaterialParser.parseBytesByFormat(signatureText, "Hex / Base64");
            } catch (Exception e) {
                mainController.showError(new UserFacingError("Signature Error", "Invalid signature format: " + e.getMessage(), "Check that the signature is valid Hex or Base64.", "signatureVerifyField"));
                return;
            }

            byte[] data = AuthenticationDataFormatter.read(view.inputArea().get(), view.inputFormat().get(), mainController);
            if (data == null || data.length == 0) {
                mainController.showError(new UserFacingError("Missing Data to Verify", "Please enter the original data that was signed.", "Provide original message text in the input area.", "authInputArea"));
                return;
            }

            boolean valid = SignatureOperations.verify(data, signature, currentPublicKey, algorithm);

            if (valid) {
                mainController.showInfo("Verification Success",
                        "✅ Signature is VALID!\n\nThe data has not been tampered with.");
            } else {
                mainController.showError(new UserFacingError("Verification Failed",
                        "Signature is INVALID! The data may have been tampered with or the wrong key was used.",
                        "Check that the public key matches the private key used for signing, and verify the message text.",
                        "signatureVerifyField"));
            }

            Map<String, String> details = new HashMap<>();
            details.put("Algorithm", algorithm);
            details.put("Result", valid ? "VALID" : "INVALID");
            details.put("Data Size", data.length + " bytes");
            details.put("Key Type", currentPublicKey != null ? currentPublicKey.getAlgorithm() : "Unknown");
            mainController.publish(OperationResult.forOperation("Signature Verified")
                    .input(data).output(signature, OperationDetail.Classification.SECRET).details(details)
                    .status("Signature verification: " + (valid ? "VALID" : "INVALID")).build());

        } catch (Exception e) {
            mainController.showError(e, "Verification Error", "signatureVerifyField");
            LOG.error("Digital signature verification failed", e);
        }
    }
}
