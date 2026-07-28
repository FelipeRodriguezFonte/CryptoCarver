package com.cryptocarver.ui;

import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationDescriptor;
import com.cryptocarver.model.OperationRegistry;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

/** Renders operation metadata without coupling feature controllers to the application shell. */
final class OperationInspectorPresenter {

    private final Label operationLabel;
    private final Label inputBytesLabel;
    private final Label outputBytesLabel;
    private final Label securityTipLabel;
    private final VBox detailsContainer;

    OperationInspectorPresenter(Label operationLabel, Label inputBytesLabel, Label outputBytesLabel,
                                Label securityTipLabel, VBox detailsContainer) {
        this.operationLabel = operationLabel;
        this.inputBytesLabel = inputBytesLabel;
        this.outputBytesLabel = outputBytesLabel;
        this.securityTipLabel = securityTipLabel;
        this.detailsContainer = detailsContainer;
    }

    void present(String operation, byte[] input, byte[] output, List<OperationDetail> details) {
        String operationName = operation == null ? "" : operation;
        if (operationLabel != null) operationLabel.setText(operationName);
        if (inputBytesLabel != null) inputBytesLabel.setText(byteCount(input));
        if (outputBytesLabel != null) outputBytesLabel.setText(byteCount(output));

        if (operationName.contains("Key Generation") || operationName.contains("Key Sharing")) {
            if (inputBytesLabel != null) inputBytesLabel.setText("-");
            if (outputBytesLabel != null) outputBytesLabel.setText("-");
        }

        renderDetails(details == null ? contextDetails(operationName) : details);
        if (securityTipLabel != null) securityTipLabel.setText(securityTip(operationName));
    }

    /** Metadata-first help shown before an operation has produced a result. */
    static List<OperationDetail> contextDetails(String operation) {
        return OperationRegistry.getInstance().resolveNavigation(operation)
                .map(OperationInspectorPresenter::contextDetails)
                .orElseGet(List::of);
    }

    private static List<OperationDetail> contextDetails(OperationDescriptor descriptor) {
        List<OperationDetail> details = new java.util.ArrayList<>();
        details.add(OperationDetail.publicDetail("Purpose", descriptor.getSubtitle()));
        details.add(OperationDetail.publicDetail("Category", descriptor.getCategory()));
        details.add(OperationDetail.publicDetail("Maturity", descriptor.getStatus().name()));
        details.add(OperationDetail.publicDetail("Sensitivity", descriptor.getSecretRisk().name()));
        Guidance guidance = guidanceFor(descriptor.getCategory());
        details.add(OperationDetail.publicDetail("Expected input", guidance.input()));
        details.add(OperationDetail.publicDetail("Produces", guidance.output()));
        details.add(OperationDetail.publicDetail("Key parameters", guidance.parameters()));
        if (!descriptor.getAliases().isEmpty()) {
            details.add(OperationDetail.publicDetail("Also found as", String.join(", ", descriptor.getAliases())));
        }
        return List.copyOf(details);
    }

    private static Guidance guidanceFor(String category) {
        return switch (category == null ? "" : category) {
            case "Cipher" -> new Guidance(
                    "Data plus the appropriate key and IV/nonce.",
                    "Ciphertext or recovered plaintext; AEAD may also produce an authentication tag.",
                    "Algorithm, mode, padding, IV/nonce and AAD where applicable.");
            case "Authentication" -> new Guidance(
                    "Data plus signing/MAC key, signature or MAC when verifying.",
                    "Signature/MAC or a verification result.",
                    "Algorithm, key source, hash and encoding.");
            case "Keys" -> new Guidance(
                    "Key material or generation parameters.",
                    "Generated, validated, derived or wrapped key material.",
                    "Key type, size, usage, format and export policy.");
            case "Post-Quantum" -> new Guidance(
                    "PQC key material, message or encapsulation parameters.",
                    "PQC keys, signatures, shared secrets or verification result.",
                    "Algorithm family, parameter set and key encoding.");
            case "XML Security" -> new Guidance(
                    "XML/SOAP content plus certificates or keys when required.",
                    "Signed, encrypted, timestamped or validated XML/SOAP.",
                    "Signature/encryption profile, key source, trust and timestamp settings.");
            case "Certificates" -> new Guidance(
                    "Certificate, CSR, CMS/PDF/ASiC data and trust material as needed.",
                    "Generated artifact, parsed report or validation result.",
                    "Profile, issuer/key source, truststore and revocation settings.");
            case "JOSE" -> new Guidance(
                    "JWT/JWE/JWK data and the selected key material.",
                    "Token, key representation or validation report.",
                    "JOSE algorithm, claims, content encryption and key format.");
            case "Payments" -> new Guidance(
                    "Payment data, PIN/PAN/KSN and laboratory keys as required.",
                    "PIN block, CVV/PVV, EMV/DUKPT value or validation result.",
                    "Scheme, format, transaction data and key usage.");
            case "ASN1" -> new Guidance(
                    "ASN.1 bytes or structured values in the selected representation.",
                    "Decoded tree/report or DER-encoded value.",
                    "Type, input representation and strict DER validation.");
            case "History" -> new Guidance(
                    "Recorded laboratory operations and filters.",
                    "Filtered history, exported record or restored screen state.",
                    "Visibility policy, filters and export format.");
            default -> new Guidance(
                    "Data in the selected input representation.",
                    "A transformed value, report or generated laboratory artifact.",
                    "Operation-specific algorithm and input/output format.");
        };
    }

    private record Guidance(String input, String output, String parameters) { }

    private void renderDetails(List<OperationDetail> details) {
        if (detailsContainer == null) return;
        detailsContainer.getChildren().clear();
        if (details == null) return;

        for (OperationDetail detail : details) {
            if (detail == null) continue;
            HBox row = new HBox(10);
            Label key = new Label(detail.name() + ":");
            key.getStyleClass().add("inspector-detail-key");
            Label value = new Label(detail.value() == null ? "" : detail.value());
            value.getStyleClass().add("inspector-detail-value");
            value.setWrapText(true);
            if (!value.getText().isBlank()) value.setTooltip(new Tooltip(value.getText()));
            row.getChildren().addAll(key, value);
            detailsContainer.getChildren().add(row);
        }
    }

    static String securityTip(String operation) {
        String name = operation == null ? "" : operation;
        if (name.contains("ASN.1")) {
            return "🔍 ASN.1 represents cryptographic data; BER and DER are encoding rules.";
        }
        if (name.contains("KDF") || name.contains("Derivation")) {
            return "⚠️ Use the required salt or context and verify every KDF parameter before production use.";
        }
        if (name.contains("Key Generation")) {
            return "🔐 Generated keys use SecureRandom. Do not reuse keys across unrelated systems.";
        }
        if (name.contains("AES")) {
            return "✅ Prefer authenticated encryption such as AES-GCM and never reuse a nonce with the same key.";
        }
        if (name.contains("RSA")) {
            return "📏 Use RSA keys of at least 2048 bits and OAEP/PSS for new designs.";
        }
        return "💡 Validate inputs, parameters and key sizes before using a result outside the laboratory.";
    }

    private static String byteCount(byte[] value) {
        return value == null ? "-" : String.valueOf(value.length);
    }
}
