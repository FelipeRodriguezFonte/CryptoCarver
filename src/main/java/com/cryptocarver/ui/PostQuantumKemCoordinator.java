package com.cryptocarver.ui;

import com.cryptocarver.crypto.PostQuantumOperations;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.util.DataConverter;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.security.MessageDigest;
import java.util.List;
import java.util.function.Supplier;

/** Coordinates the PQC encapsulation and decapsulation flows against shared key state. */
final class PostQuantumKemCoordinator {
    record View(ComboBox<String> algorithm,
                TextArea ciphertext,
                TextField bobSecret,
                TextField aliceSecret,
                Label status,
                PostQuantumKeyState keys) { }

    private final View view;
    private final Supplier<StatusReporter> reporter;

    PostQuantumKemCoordinator(View view, Supplier<StatusReporter> reporter) {
        this.view = view;
        this.reporter = reporter;
    }

    private StatusReporter reporter() { return reporter.get(); }

    private String t(String key, Object... args) {
        return com.cryptocarver.service.I18nService.getInstance().text(key, args);
    }

    void handlePQCEncapsulate() {
        StatusReporter statusReporter = reporter();
        PostQuantumKeyState keys = view.keys();
        try {
            requireKemKeyPair(keys);
            String selectedAlgorithm = view.algorithm().getValue();
            if (selectedAlgorithm == null || !PostQuantumOperations.areAlgorithmsCompatible(selectedAlgorithm, keys.publicKey().getAlgorithm())) {
                if (statusReporter != null) statusReporter.showError(
                        t("module.pqc.error.kemAlgorithmMismatchTitle"),
                        t("module.pqc.error.kemAlgorithmMismatch", selectedAlgorithm, keys.publicKey().getAlgorithm()));
                return;
            }
            PostQuantumOperations.KEMResult result = PostQuantumOperations.encapsulate(keys.publicKey(), selectedAlgorithm);
            view.ciphertext().setText(DataConverter.bytesToHex(result.encapsulation()));
            view.bobSecret().setText(DataConverter.bytesToHex(result.sharedSecret()));
            keys.setBobSecret(result.sharedSecret());
            if (view.aliceSecret() != null) view.aliceSecret().clear();
            view.status().setText(t("module.pqc.encapsulated"));
            view.status().setStyle("");
            List<OperationDetail> details = List.of(
                    OperationDetail.publicDetail("Algorithm", selectedAlgorithm),
                    OperationDetail.publicDetail("Ciphertext Size", result.encapsulation().length + " bytes"),
                    OperationDetail.secretDetail("Secret Size", result.sharedSecret().length + " bytes")
            );
            if (statusReporter != null) {
                statusReporter.publish(OperationResult.forOperation("ML-KEM Encapsulate")
                        .output(result.encapsulation()).details(details)
                        .status("ML-KEM encapsulation completed")
                        .build());
            }
        } catch (Exception e) {
            if (statusReporter != null) statusReporter.showError("KEM Error", "Unable to encapsulate: " + e.getMessage());
        }
    }

    void handlePQCDecapsulate() {
        StatusReporter statusReporter = reporter();
        PostQuantumKeyState keys = view.keys();
        try {
            requireKemKeyPair(keys);
            String ciphertextHex = view.ciphertext().getText().trim();
            if (ciphertextHex.isEmpty()) {
                if (statusReporter != null) statusReporter.showError("KEM Input Error", "Encapsulate first or paste an encapsulation in hexadecimal.");
                return;
            }
            String selectedAlgorithm = view.algorithm().getValue();
            byte[] secret = PostQuantumOperations.decapsulate(keys.privateKey(), DataConverter.hexToBytes(ciphertextHex), selectedAlgorithm);
            if (view.aliceSecret() != null) view.aliceSecret().setText(DataConverter.bytesToHex(secret));
            if (keys.bobSecret() != null) {
                boolean match = MessageDigest.isEqual(keys.bobSecret(), secret);
                if (match) {
                    view.status().setText(t("module.pqc.match"));
                    view.status().setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                } else {
                    view.status().setText(t("module.pqc.mismatch"));
                    view.status().setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                }
            } else {
                view.status().setText(t("module.pqc.bobSecretUnknown"));
            }
            List<OperationDetail> details = List.of(
                    OperationDetail.publicDetail("Algorithm", view.algorithm().getValue()),
                    OperationDetail.publicDetail("Encapsulation Length", ciphertextHex.length() / 2 + " bytes"),
                    OperationDetail.secretDetail("Shared Secret", "Recovered (not displayed in history)")
            );
            if (statusReporter != null) {
                statusReporter.publish(OperationResult.forOperation("ML-KEM Decapsulate")
                        .input(DataConverter.hexToBytes(ciphertextHex))
                        .output(secret, OperationDetail.Classification.SECRET).details(details)
                        .status("ML-KEM decapsulation completed")
                        .build());
            }
        } catch (Exception e) {
            if (statusReporter != null) statusReporter.showError("KEM Error", "Unable to decapsulate: " + e.getMessage());
        }
    }

    private static void requireKemKeyPair(PostQuantumKeyState keys) {
        if (keys.publicKey() == null || keys.privateKey() == null || !PostQuantumKeyCoordinator.isKemAlgorithm(keys.publicKey().getAlgorithm())) {
            throw new IllegalStateException("Generate an ML-KEM/Kyber key pair first.");
        }
    }
}
