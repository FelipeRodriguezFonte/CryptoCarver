package com.cryptocarver.ui;

import javafx.scene.control.TextArea;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.function.Supplier;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

final class CertificateChainCoordinator extends KeysCoordinatorSupport {
    record View(
            TextArea chainInputArea,
            TextArea chainCrlInputArea,
            TextArea chainResultArea) { }
    private static final Logger LOG = LoggerFactory.getLogger(CertificateChainCoordinator.class);
    private View controls;

    CertificateChainCoordinator(Supplier<StatusReporter> reporter, BiConsumer<String, String> errors,
            Consumer<String> status, BiFunction<String, Object[], String> text) {
        super(reporter, errors, status, text);
    }
    private View view() { return controls; }
    void initialize(View view) { controls = view; }

    public void handleValidateCertificateChain() {
        try {
            String chainStr = view().chainInputArea().getText().trim();

            if (chainStr.isEmpty()) {
                showError("Input Error", "Certificate Chain PEM is required");
                return;
            }

            updateStatus("Validating chain...");

            var validation = CertificateChainLogic.validate(chainStr,
                    view().chainCrlInputArea() == null ? null : view().chainCrlInputArea().getText());
            var result = validation.result();
            String outputText = validation.outputText();
            view().chainResultArea().setText(outputText);
            view().chainResultArea().setVisible(true);
            view().chainResultArea().setManaged(true);

            updateStatus("Chain validation complete: " + (result.isValid ? "Valid" : "Invalid"));
            if (reporter() != null) {
                reporter().publish(com.cryptocarver.model.OperationResult.forOperation("Validate Chain")
                    .enrichedOutput(outputText, com.cryptocarver.model.OperationDetail.Classification.PUBLIC)
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Chain Length", String.valueOf(validation.chainLength()), com.cryptocarver.model.OperationDetail.Classification.PUBLIC, false, null),
                        new com.cryptocarver.model.OperationDetail("Result", result.isValid ? "Valid" : "Invalid", com.cryptocarver.model.OperationDetail.Classification.PUBLIC, false, null)
                    ))
                    .status("Certificate chain validation completed")
                    .build());
            }

        } catch (KeysInputValidation e) {
            showError(e.title(), e.getMessage());
        } catch (Exception e) {
            showError("Validation Error", "Error validating chain: " + e.getMessage());
            LOG.warn("Key operation failed", e);
        }
    }
}
