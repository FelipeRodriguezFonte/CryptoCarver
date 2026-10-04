package com.cryptocarver.ui;

import com.cryptocarver.crypto.PostQuantumOperations;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.util.DataConverter;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;

/** Coordinates the PQC sign and verify flows against the live form and shared key state. */
final class PostQuantumSignatureCoordinator {
    record View(Supplier<ComboBox<String>> algorithm,
                Supplier<TextArea> inputArea,
                Supplier<TextArea> outputArea,
                Supplier<TextField> verifySignature,
                Supplier<PostQuantumKeyState> keys) { }

    private final View view;
    private final Supplier<StatusReporter> reporter;

    PostQuantumSignatureCoordinator(View view, Supplier<StatusReporter> reporter) {
        this.view = view;
        this.reporter = reporter;
    }

    private StatusReporter reporter() { return reporter.get(); }

    private String t(String key, Object... args) {
        return com.cryptocarver.service.I18nService.getInstance().text(key, args);
    }

    void handlePQCSign() {
        StatusReporter statusReporter = reporter();
        PostQuantumKeyState keys = view.keys().get();
        try {
            String algo = view.algorithm().get().getValue();
            String inputData = view.inputArea().get().getText();

            if (keys.privateKey() == null) {
                if (statusReporter != null) statusReporter.showError("Key Error", "Please generate or import a compatible PQC signature private key first.");
                return;
            }
            if (!PostQuantumOperations.areAlgorithmsCompatible(algo, keys.privateKey().getAlgorithm())) {
                if (statusReporter != null) statusReporter.showError(
                        t("module.pqc.error.signatureAlgorithmMismatchTitle"),
                        t("module.pqc.error.signatureAlgorithmMismatch", algo, keys.privateKey().getAlgorithm()));
                return;
            }

            if (inputData.isEmpty()) {
                if (statusReporter != null) statusReporter.showError("Input Error", "Please enter data to sign");
                return;
            }

            byte[] data = inputData.getBytes(StandardCharsets.UTF_8);
            byte[] signature = PostQuantumOperations.sign(keys.privateKey(), data, algo);
            view.outputArea().get().setText(DataConverter.bytesToHex(signature));

            String kpDescription = keys.privateKey().getAlgorithm();
            List<OperationDetail> details = List.of(
                    OperationDetail.publicDetail("Algorithm", algo),
                    OperationDetail.publicDetail("Key Pair", kpDescription),
                    OperationDetail.publicDetail("Data Size", data.length + " bytes"),
                    OperationDetail.publicDetail("Signature Size", signature.length + " bytes")
            );
            if (statusReporter != null) {
                statusReporter.publish(OperationResult.forOperation("PQC Sign")
                        .input(data).output(signature, OperationDetail.Classification.SECRET).details(details)
                        .status("PQC signature generated")
                        .build());
            }
        } catch (Exception e) {
            if (statusReporter != null) statusReporter.showError("Signing Error", "Error signing data: " + e.getMessage());
        }
    }

    void handlePQCVerify() {
        StatusReporter statusReporter = reporter();
        PostQuantumKeyState keys = view.keys().get();
        try {
            String algo = view.algorithm().get().getValue();
            String inputData = view.inputArea().get().getText();
            String signatureHex = view.verifySignature().get().getText();

            if (keys.publicKey() == null) {
                if (statusReporter != null) statusReporter.showError("Key Error", "Please generate a key pair first");
                return;
            }
            if (!PostQuantumOperations.areAlgorithmsCompatible(algo, keys.publicKey().getAlgorithm())) {
                if (statusReporter != null) statusReporter.showError(
                        t("module.pqc.error.signatureAlgorithmMismatchTitle"),
                        t("module.pqc.error.signatureAlgorithmMismatch", algo, keys.publicKey().getAlgorithm()));
                return;
            }

            if (inputData.isEmpty() || signatureHex.isEmpty()) {
                if (statusReporter != null) statusReporter.showError("Input Error", "Please enter data and signature");
                return;
            }

            byte[] data = inputData.getBytes(StandardCharsets.UTF_8);
            byte[] signature = DataConverter.hexToBytes(signatureHex);
            boolean verified = PostQuantumOperations.verify(keys.publicKey(), data, signature, algo);

            if (verified) {
                if (statusReporter != null) statusReporter.showInfo("Verification Result", "✓ Signature is VALID");
            } else if (statusReporter != null) {
                statusReporter.showError(t("module.pqc.error.invalidSignatureTitle"),
                        t("module.pqc.error.invalidSignature"));
            }

            String kpDescription = keys.publicKey().getAlgorithm();
            List<OperationDetail> details = List.of(
                    OperationDetail.publicDetail("Algorithm", algo),
                    OperationDetail.publicDetail("Key Pair", kpDescription),
                    OperationDetail.publicDetail("Result", verified ? "VALID" : "INVALID"),
                    OperationDetail.publicDetail("Data Size", data.length + " bytes")
            );
            if (statusReporter != null) {
                statusReporter.publish(OperationResult.forOperation("PQC Verify")
                        .input(data).output(signature, OperationDetail.Classification.SECRET).details(details)
                        .status(verified ? "PQC signature is valid" : "PQC signature is invalid")
                        .build());
            }
        } catch (Exception e) {
            if (statusReporter != null) statusReporter.showError("Verification Error", "Error verifying: " + e.getMessage());
        }
    }
}
