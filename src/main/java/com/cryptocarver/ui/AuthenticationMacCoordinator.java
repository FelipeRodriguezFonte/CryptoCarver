package com.cryptocarver.ui;

import com.cryptocarver.crypto.MACOperations;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.util.DataConverter;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Generates and verifies Authentication MACs against the live MAC form. */
final class AuthenticationMacCoordinator {
    record View(Supplier<ComboBox<String>> algorithm,
                Supplier<TextField> key,
                Supplier<ComboBox<String>> truncation,
                Supplier<TextField> verifyValue,
                Supplier<TextField> nonce,
                Supplier<ComboBox<String>> keySource,
                Supplier<ComboBox<String>> hsmKey,
                Supplier<TextArea> inputArea,
                Supplier<ComboBox<String>> inputFormat,
                Supplier<TextArea> outputArea,
                Supplier<ComboBox<String>> outputFormat) { }

    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationController.class);
    private final View view;
    private final Supplier<StatusReporter> reporter;

    AuthenticationMacCoordinator(View view, Supplier<StatusReporter> reporter) {
        this.view = view;
        this.reporter = reporter;
    }

    private StatusReporter reporter() { return reporter.get(); }

    void handleGenerateMAC() {
        StatusReporter mainController = reporter();
        if (mainController != null && !mainController.checkPreflightReadiness("Message Authentication Codes", true)) {
            return;
        }
        try {
            String algorithm = view.algorithm().get().getValue();
            if (algorithm == null) {
                mainController.showError(new UserFacingError("Algorithm Error", "Please select a MAC algorithm.", "Select an algorithm from the dropdown list.", "authMacAlgorithmCombo"));
                return;
            }

            String hsmKeyId = getHsmMacKeyId();
            String pkcs11KeyAlias = getPkcs11MacKeyAlias();
            byte[] manualKey = hsmKeyId == null && pkcs11KeyAlias == null ? getManualMacKey() : null;

            byte[] data = AuthenticationDataFormatter.read(view.inputArea().get(), view.inputFormat().get(), mainController);
            if (data == null || data.length == 0) {
                mainController.showError(new UserFacingError("Missing Input Data", "Please enter data to MAC.", "Provide text or binary data in the message field.", "authInputArea"));
                return;
            }

            int truncation = getTruncationBytes();
            byte[] mac = generateMac(data, hsmKeyId, pkcs11KeyAlias, manualKey, algorithm);

            if (truncation > 0 && truncation < mac.length) {
                byte[] truncatedMac = new byte[truncation];
                System.arraycopy(mac, 0, truncatedMac, 0, truncation);
                mac = truncatedMac;
            }

            AuthenticationDataFormatter.write(view.outputArea().get(), view.outputFormat().get(), mac, mainController);

            mainController.showInfo("Success",
                    String.format("MAC generated successfully!\nAlgorithm: %s\nMAC size: %d bytes",
                            algorithm, mac.length));

            Map<String, String> details = new HashMap<>();
            details.put("Algorithm", algorithm);
            details.put("Data Size", data.length + " bytes");
            details.put("MAC Size", mac.length + " bytes");
            details.put("Truncation", truncation > 0 ? truncation + " bytes" : "None");
            details.put("Key Source", pkcs11KeyAlias != null ? "PKCS#11 Token" : hsmKeyId != null ? "Simulated HSM" : "Manual Input");

            mainController.publish(OperationResult.forOperation("MAC Generated")
                    .input(data).output(mac, OperationDetail.Classification.SECRET).details(details)
                    .status("MAC generated with " + algorithm).build());

        } catch (Exception e) {
            mainController.showError(e, "MAC Error", "authMacKeyField");
            LOG.error("MAC generation failed", e);
        }
    }

    void handleVerifyMAC() {
        StatusReporter mainController = reporter();
        if (mainController != null && !mainController.checkPreflightReadiness("Message Authentication Codes", false)) {
            return;
        }
        try {
            String algorithm = view.algorithm().get().getValue();
            if (algorithm == null) {
                mainController.showError(new UserFacingError("Algorithm Error", "Please select a MAC algorithm.", "Select an algorithm from the dropdown list.", "authMacAlgorithmCombo"));
                return;
            }

            String hsmKeyId = getHsmMacKeyId();
            String pkcs11KeyAlias = getPkcs11MacKeyAlias();
            byte[] manualKey = hsmKeyId == null && pkcs11KeyAlias == null ? getManualMacKey() : null;

            String macText = view.verifyValue().get().getText().trim();
            if (macText.isEmpty()) {
                mainController.showError(new UserFacingError("Missing MAC Verification Value", "Please paste the MAC in the verification field.", "Enter MAC value to verify.", "authMacVerifyField"));
                return;
            }

            byte[] providedMac;
            try {
                providedMac = com.cryptocarver.crypto.SharedMaterialParser.parseBytesByFormat(macText, "Hex / Base64");
            } catch (Exception e) {
                mainController.showError(new UserFacingError("MAC Error", "Invalid MAC format: " + e.getMessage(), "Check MAC format (Hex or Base64).", "authMacVerifyField"));
                return;
            }

            byte[] data = AuthenticationDataFormatter.read(view.inputArea().get(), view.inputFormat().get(), mainController);
            if (data == null || data.length == 0) {
                mainController.showError(new UserFacingError("Data Error", "Please enter the original data that was MACed.", "Provide original message text in the input area.", "authInputArea"));
                return;
            }

            byte[] calculatedMac = generateMac(data, hsmKeyId, pkcs11KeyAlias, manualKey, algorithm);
            if (providedMac.length < calculatedMac.length) {
                byte[] truncatedMac = new byte[providedMac.length];
                System.arraycopy(calculatedMac, 0, truncatedMac, 0, providedMac.length);
                calculatedMac = truncatedMac;
            }

            boolean valid = MACOperations.constantTimeEquals(calculatedMac, providedMac);
            if (valid) {
                mainController.showInfo("Verification Success",
                        "✅ MAC is VALID!\n\nThe data has not been tampered with.");
            } else {
                mainController.showError(new UserFacingError("Verification Failed",
                        "MAC is INVALID! The data may have been tampered with or the wrong key was used.",
                        "Check MAC key and message content.",
                        "authMacVerifyField"));
            }

            Map<String, String> details = new HashMap<>();
            details.put("Algorithm", algorithm);
            details.put("Result", valid ? "VALID" : "INVALID");
            details.put("Data Size", data.length + " bytes");
            details.put("Truncation", providedMac.length + " bytes (provided)");
            details.put("Key Source", pkcs11KeyAlias != null ? "PKCS#11 Token" : hsmKeyId != null ? "Simulated HSM" : "Manual Input");
            mainController.publish(OperationResult.forOperation("MAC Verified")
                    .input(data).output(providedMac, OperationDetail.Classification.SECRET).details(details)
                    .status("MAC verification: " + (valid ? "VALID" : "INVALID")).build());

        } catch (Exception e) {
            mainController.showError(e, "Verification Error", "authMacVerifyField");
            LOG.error("MAC verification failed", e);
        }
    }

    private String getHsmMacKeyId() {
        if (view.keySource().get() != null && "Simulated HSM".equals(view.keySource().get().getValue())) {
            String keyId = view.hsmKey().get().getValue();
            if (keyId == null || keyId.isEmpty()) {
                throw new IllegalArgumentException("Please select a key from the Lab Cache");
            }
            var km = com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().getKeyMetadata(keyId);
            if (km != null && !km.hasKeyMaterial()) {
                throw new IllegalStateException("Selected Key Lab entry contains metadata only. Re-import or regenerate the key bytes.");
            }
            return keyId;
        }
        return null;
    }

    private boolean isPkcs11MacSource() {
        return view.keySource().get() != null && "PKCS#11 Token".equals(view.keySource().get().getValue());
    }

    private String getPkcs11MacKeyAlias() {
        if (!isPkcs11MacSource()) return null;
        ComboBox<String> hsmKey = view.hsmKey().get();
        String alias = hsmKey == null ? null : hsmKey.getValue();
        if (alias == null || alias.isBlank()) {
            throw new IllegalArgumentException("Connect a PKCS#11 token and select one of its secret-key objects");
        }
        return alias;
    }

    private byte[] getManualMacKey() {
        String keyHex = view.key().get().getText().trim();
        if (keyHex.isEmpty()) {
            throw new IllegalArgumentException("Please enter MAC key in hexadecimal");
        }
        return DataConverter.hexToBytes(keyHex);
    }

    private int getTruncationBytes() {
        String value = view.truncation().get().getValue();
        if (value == null || value.startsWith("0")) {
            return 0;
        }
        try {
            if (value.contains(" ")) {
                return Integer.parseInt(value.split(" ")[0]);
            }
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private byte[] generateMac(byte[] data, String hsmKeyId, String pkcs11KeyAlias,
                               byte[] manualKey, String algorithm) throws Exception {
        if (pkcs11KeyAlias != null) {
            if ("GMAC-AES".equals(algorithm) || "Poly1305".equals(algorithm)) {
                throw new IllegalArgumentException(algorithm + " is not available through the generic PKCS#11 MAC path");
            }
            return com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().requireSession()
                    .mac(pkcs11KeyAlias, data, algorithm);
        }
        if ("GMAC-AES".equals(algorithm)) {
            String nonceHex = view.nonce().get().getText().trim();
            if (nonceHex.isEmpty()) {
                throw new IllegalArgumentException("GMAC requires a unique nonce/IV in hexadecimal");
            }
            byte[] nonce = DataConverter.hexToBytes(nonceHex.replaceAll("\\s+", ""));
            if (hsmKeyId != null) {
                return com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().generateGmac(hsmKeyId, data, nonce);
            }
            return MACOperations.generateGmac(data, manualKey, nonce);
        }
        if ("Poly1305".equals(algorithm)) {
            if (hsmKeyId != null) {
                return com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().generatePoly1305(hsmKeyId, data);
            }
            return MACOperations.generatePoly1305(data, manualKey);
        }
        if (hsmKeyId != null) {
            return com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().generateMac(hsmKeyId, data, algorithm);
        }
        return MACOperations.generate(data, manualKey, algorithm);
    }
}
