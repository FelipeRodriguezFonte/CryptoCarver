package com.cryptocarver.ui;

import com.cryptocarver.crypto.*;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.GeneratedAsymmetricKeySummary;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

final class AsymmetricKeyGenerationCoordinator extends KeysCoordinatorSupport {
    record View(
            VBox rsaSummaryCard,
            Label rsaSummaryAlgoLabel,
            Label rsaSummaryFingerprintLabel,
            Label rsaSummaryPubLenLabel,
            Label rsaSummaryPrivLenLabel,
            Label rsaSummaryCreatedLabel,
            Label rsaSummarySavedStatusLabel,
            VBox ecdsaSummaryCard,
            Label ecdsaSummaryAlgoLabel,
            Label ecdsaSummaryFingerprintLabel,
            Label ecdsaSummaryPubLenLabel,
            Label ecdsaSummaryPrivLenLabel,
            Label ecdsaSummaryCreatedLabel,
            Label ecdsaSummarySavedStatusLabel,
            VBox dsaSummaryCard,
            Label dsaSummaryAlgoLabel,
            Label dsaSummaryFingerprintLabel,
            Label dsaSummaryPubLenLabel,
            Label dsaSummaryPrivLenLabel,
            Label dsaSummaryCreatedLabel,
            Label dsaSummarySavedStatusLabel,
            VBox eddsaSummaryCard,
            Label eddsaSummaryAlgoLabel,
            Label eddsaSummaryFingerprintLabel,
            Label eddsaSummaryPubLenLabel,
            Label eddsaSummaryPrivLenLabel,
            Label eddsaSummaryCreatedLabel,
            Label eddsaSummarySavedStatusLabel,
            ComboBox<Integer> rsaKeySizeCombo,
            TextArea rsaPublicKeyArea,
            TextArea rsaPrivateKeyArea,
            ComboBox<String> dsaKeySizeCombo,
            TextArea dsaPublicKeyArea,
            TextArea dsaPrivateKeyArea,
            ComboBox<String> ecdsaFpCurveCombo,
            TextArea ecdsaFpPublicKeyArea,
            TextArea ecdsaFpPrivateKeyArea,
            TextArea ed25519PublicKeyArea,
            TextArea ed25519PrivateKeyArea,
            Button rsaGenerateBtn,
            Button dsaGenerateBtn) { }
    private final Supplier<View> views;
    private final Consumer<StateUpdate> state;
    record StateUpdate(KeyPair keyPair, String algorithm, GeneratedAsymmetricKeySummary summary) { }

    AsymmetricKeyGenerationCoordinator(Supplier<View> views, Supplier<StatusReporter> reporter,
            BiConsumer<String, String> errors, Consumer<String> status, BiFunction<String, Object[], String> text,
            Consumer<StateUpdate> state) {
        super(reporter, errors, status, text);
        this.views = views;
        this.state = state;
    }
    private View view() { return views.get(); }

    void initializeRSA() {

        view().rsaKeySizeCombo().getItems().addAll(AsymmetricKeyOperations.RSA_KEY_SIZES);
        view().rsaKeySizeCombo().setValue(2048);
    }

    void initializeDSA() {

        view().dsaKeySizeCombo().getItems().addAll(AsymmetricKeyOperations.DSA_KEY_SIZES);
        view().dsaKeySizeCombo().setValue("2048/256");
    }

    void initializeECDSAFp() {

        view().ecdsaFpCurveCombo().getItems().addAll(AsymmetricKeyOperations.ECDSA_FP_NAMED_CURVES);
        view().ecdsaFpCurveCombo().setValue("secp256r1");
    }

    void initializeEd25519() {
    }

    public void handleGenerateRSA() {
        try {
            Integer keySize = view().rsaKeySizeCombo().getValue();
            if (keySize == null) {
                showError("Input Error", "Please select RSA key size");
                return;
            }

            updateStatus("Generating RSA-" + keySize + " key pair... This may take a moment.");

            Callable<KeyPair> task = () -> AsymmetricKeyOperations.generateRSAKeyPair(keySize);

            Consumer<KeyPair> onSuccess = keyPair -> {
                try {

                    GeneratedAsymmetricKeySummary summary = new GeneratedAsymmetricKeySummary(keyPair, "RSA", keySize + " bits");
                    state.accept(new StateUpdate(keyPair, "RSA", summary));
                    updateAsymmetricSummaryCard(view().rsaSummaryCard(), view().rsaSummaryAlgoLabel(), view().rsaSummaryFingerprintLabel(), view().rsaSummaryPubLenLabel(), view().rsaSummaryPrivLenLabel(), view().rsaSummaryCreatedLabel(), view().rsaSummarySavedStatusLabel(), summary);

                    var material = AsymmetricKeyGenerationLogic.material(keyPair, "RSA", keySize + " bits");
                    view().rsaPublicKeyArea().setText(material.publicReport());
                    view().rsaPrivateKeyArea().setText(material.privateReport());

                    updateStatus("RSA-" + keySize + " key pair generated successfully");

                    if (reporter() != null) {
                        try {
                            java.util.List<com.cryptocarver.model.OperationDetail> details = new java.util.ArrayList<>();
                            details.add(com.cryptocarver.model.OperationDetail.publicDetail("Key Size", keySize + " bits"));
                            details.add(com.cryptocarver.model.OperationDetail.publicDetail("Public Key", material.publicPem()));
                            details.add(com.cryptocarver.model.OperationDetail.secretDetail("Private Key", material.privatePem()));

                            reporter().publish(OperationResult.forOperation("Generate RSA Key")
                                    .output(material.publicPem()
                                            .getBytes(StandardCharsets.UTF_8))
                                    .enrichedOutput(AsymmetricKeyGenerationLogic.renderGeneratedKeyPair(
                                            material.publicPem(),
                                            material.privatePem()),
                                            com.cryptocarver.model.OperationDetail.Classification.SECRET)
                                    .details(details)
                                    .status("RSA-" + keySize + " key pair generated successfully")
                                    .build());
                        } catch (Exception e) {
                            System.err.println("Failed to add to history: " + e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    showError("RSA Generation Error", e.getMessage());
                }
            };

            Consumer<Throwable> onFailure = err -> {
                showError("RSA Generation Error", err != null ? err.getMessage() : "Unknown error during key generation");
            };

            Runnable onCancelled = () -> {
                updateStatus("RSA key generation cancelled.");
            };

            if (reporter() != null && reporter().getOperationExecutor() != null) {
                reporter().getOperationExecutor().execute("RSA-" + keySize + " Key Generation", view().rsaGenerateBtn(), task, onSuccess, onFailure, onCancelled);
            } else {
                KeyPair kp = task.call();
                onSuccess.accept(kp);
            }
        } catch (Exception e) {
            showError("RSA Generation Error", e.getMessage());
        }
    }

    public void handleGenerateDSA() {
        try {
            String keySize = view().dsaKeySizeCombo().getValue();
            if (keySize == null) {
                showError("Input Error", "Please select DSA key size");
                return;
            }

            updateStatus("Generating DSA-" + keySize + " key pair...");

            Callable<KeyPair> task = () -> AsymmetricKeyOperations.generateDSAKeyPair(keySize);

            Consumer<KeyPair> onSuccess = keyPair -> {
                try {

                    GeneratedAsymmetricKeySummary summary = new GeneratedAsymmetricKeySummary(keyPair, "DSA", keySize + " bits");
                    state.accept(new StateUpdate(keyPair, "DSA", summary));
                    updateAsymmetricSummaryCard(view().dsaSummaryCard(), view().dsaSummaryAlgoLabel(), view().dsaSummaryFingerprintLabel(), view().dsaSummaryPubLenLabel(), view().dsaSummaryPrivLenLabel(), view().dsaSummaryCreatedLabel(), view().dsaSummarySavedStatusLabel(), summary);

                    var material = AsymmetricKeyGenerationLogic.material(keyPair, "DSA", keySize + " bits");
                    view().dsaPublicKeyArea().setText(material.publicReport());
                    view().dsaPrivateKeyArea().setText(material.privateReport());

                    updateStatus("DSA-" + keySize + " key pair generated successfully");

                    if (reporter() != null) {
                        try {
                            java.util.List<com.cryptocarver.model.OperationDetail> details = new java.util.ArrayList<>();
                            details.add(com.cryptocarver.model.OperationDetail.publicDetail("Key Size", keySize));
                            details.add(com.cryptocarver.model.OperationDetail.publicDetail("Public Key", material.publicPem()));
                            details.add(com.cryptocarver.model.OperationDetail.secretDetail("Private Key", material.privatePem()));

                            reporter().publish(OperationResult.forOperation("Generate DSA Key")
                                    .output(material.publicPem()
                                            .getBytes(StandardCharsets.UTF_8))
                                    .enrichedOutput(AsymmetricKeyGenerationLogic.renderGeneratedKeyPair(
                                            material.publicPem(),
                                            material.privatePem()),
                                            com.cryptocarver.model.OperationDetail.Classification.SECRET)
                                    .details(details)
                                    .status("DSA-" + keySize + " key pair generated successfully")
                                    .build());
                        } catch (Exception e) {
                            System.err.println("Failed to add to history: " + e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    showError("DSA Generation Error", e.getMessage());
                }
            };

            Consumer<Throwable> onFailure = err -> {
                showError("DSA Generation Error", err != null ? err.getMessage() : "Unknown error during key generation");
            };

            Runnable onCancelled = () -> {
                updateStatus(com.cryptocarver.service.I18nService.getInstance().text("module.keys.generationCancelled"));
            };

            if (reporter() != null && reporter().getOperationExecutor() != null) {
                reporter().getOperationExecutor().execute("DSA-" + keySize + " Key Generation", view().dsaGenerateBtn(), task, onSuccess, onFailure, onCancelled);
            } else {
                KeyPair kp = task.call();
                onSuccess.accept(kp);
            }
        } catch (Exception e) {
            showError("DSA Generation Error", e.getMessage());
        }
    }

    public void handleGenerateECDSAFp() {
        try {
            String curve = view().ecdsaFpCurveCombo().getValue();
            if (curve == null) {
                showError("Input Error", "Please select a curve");
                return;
            }

            updateStatus("Generating ECDSA F(p) key pair on curve " + curve + "...");

            KeyPair keyPair = AsymmetricKeyOperations.generateECDSAFpKeyPair(curve);

            GeneratedAsymmetricKeySummary summary = new GeneratedAsymmetricKeySummary(keyPair, "ECDSA", curve);
            state.accept(new StateUpdate(keyPair, "ECDSA", summary));
            updateAsymmetricSummaryCard(view().ecdsaSummaryCard(), view().ecdsaSummaryAlgoLabel(), view().ecdsaSummaryFingerprintLabel(), view().ecdsaSummaryPubLenLabel(), view().ecdsaSummaryPrivLenLabel(), view().ecdsaSummaryCreatedLabel(), view().ecdsaSummarySavedStatusLabel(), summary);

            var material = AsymmetricKeyGenerationLogic.material(keyPair, "ECDSA", curve);
            view().ecdsaFpPublicKeyArea().setText(material.publicReport());
            view().ecdsaFpPrivateKeyArea().setText(material.privateReport());

            updateStatus("ECDSA F(p) key pair generated on curve " + curve);

            if (reporter() != null) {
                try {
                    java.util.List<com.cryptocarver.model.OperationDetail> details = new java.util.ArrayList<>();
                    details.add(com.cryptocarver.model.OperationDetail.publicDetail("Curve", curve));
                    details.add(com.cryptocarver.model.OperationDetail.publicDetail("Public Key", material.publicPem()));
                    details.add(com.cryptocarver.model.OperationDetail.secretDetail("Private Key", material.privatePem()));

                    reporter().publish(OperationResult.forOperation("Generate ECDSA Key")
                            .output(material.publicPem()
                                    .getBytes(StandardCharsets.UTF_8))
                            .enrichedOutput(AsymmetricKeyGenerationLogic.renderGeneratedKeyPair(
                                    material.publicPem(),
                                    material.privatePem()),
                                    com.cryptocarver.model.OperationDetail.Classification.SECRET)
                            .details(details)
                            .status("ECDSA F(p) key pair generated on curve " + curve)
                            .build());
                } catch (Exception e) {
                    System.err.println("Failed to add to history: " + e.getMessage());
                }
            } else {
                if (reporter() != null) {
                reporter().publish(com.cryptocarver.model.OperationResult.forOperation("Generate ECDSA F(p) - " + curve)
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", "N/A", com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", "Curve: " + curve, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null)
                    ))
                    .build());
            }
            }

        } catch (Exception e) {
            showError("Generation Error", "Error generating ECDSA F(p) key: " + e.getMessage());
        }
    }

    public void handleGenerateEd25519() {
        try {
            updateStatus("Generating Ed25519 key pair...");

            KeyPair keyPair = AsymmetricKeyOperations.generateEd25519KeyPair();

            GeneratedAsymmetricKeySummary summary = new GeneratedAsymmetricKeySummary(keyPair, "Ed25519", "Ed25519 (255-bit curve)");
            state.accept(new StateUpdate(keyPair, "Ed25519", summary));
            updateAsymmetricSummaryCard(view().eddsaSummaryCard(), view().eddsaSummaryAlgoLabel(), view().eddsaSummaryFingerprintLabel(), view().eddsaSummaryPubLenLabel(), view().eddsaSummaryPrivLenLabel(), view().eddsaSummaryCreatedLabel(), view().eddsaSummarySavedStatusLabel(), summary);

            var material = AsymmetricKeyGenerationLogic.material(keyPair, "Ed25519", "Ed25519 (255-bit curve)");
            view().ed25519PublicKeyArea().setText(material.publicReport());
            view().ed25519PrivateKeyArea().setText(material.privateReport());

            updateStatus("Ed25519 key pair generated successfully");

            if (reporter() != null) {
                try {
                    String publicPem = material.publicPem();
                    java.util.List<com.cryptocarver.model.OperationDetail> details = new java.util.ArrayList<>();
                    details.add(com.cryptocarver.model.OperationDetail.publicDetail("Algorithm", "Ed25519"));
                    details.add(com.cryptocarver.model.OperationDetail.publicDetail("Public Key", publicPem));
                    details.add(com.cryptocarver.model.OperationDetail.secretDetail("Private Key",
                            material.privatePem()));
                    reporter().publish(OperationResult.forOperation("Generate EdDSA Key")
                            .output(publicPem.getBytes(StandardCharsets.UTF_8))
                            .enrichedOutput(AsymmetricKeyGenerationLogic.renderGeneratedKeyPair(publicPem,
                                    material.privatePem()),
                                    com.cryptocarver.model.OperationDetail.Classification.SECRET)
                            .details(details)
                            .status("Ed25519 key pair generated successfully")
                            .build());
                } catch (Exception e) {
                    System.err.println("Failed to add to history: " + e.getMessage());
                }
            } else {
                if (reporter() != null) {
                reporter().publish(com.cryptocarver.model.OperationResult.forOperation("Generate Ed25519")
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", "N/A", com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", "Algorithm: Ed25519", com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null)
                    ))
                    .build());
            }
            }

        } catch (Exception e) {
            showError("Generation Error", "Error generating Ed25519 key: " + e.getMessage());
        }
    }

    public void handleGenerateEdDSA() {
        handleGenerateEd25519();
    }

    private void updateAsymmetricSummaryCard(VBox card, Label algoLbl, Label fpLbl, Label pubLenLbl, Label privLenLbl, Label createdLbl, Label savedLbl, GeneratedAsymmetricKeySummary summary) {
        if (card == null || summary == null) return;
        if (algoLbl != null) algoLbl.setText(summary.getAlgorithm() + " (" + summary.getCurveOrKeySize() + ")");
        if (fpLbl != null) fpLbl.setText(summary.getPublicFingerprintTruncated());
        if (pubLenLbl != null) pubLenLbl.setText(summary.getPublicKeyLength());
        if (privLenLbl != null) privLenLbl.setText(summary.getPrivateKeyLength());
        if (createdLbl != null) createdLbl.setText(summary.getCreatedAt());
        if (savedLbl != null) savedLbl.setText(summary.getSavedStatus() != null ? "✓ " + summary.getSavedStatus() : "");
        card.setVisible(true);
        card.setManaged(true);
    }
}
