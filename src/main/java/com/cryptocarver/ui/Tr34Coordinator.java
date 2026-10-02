package com.cryptocarver.ui;

import com.cryptocarver.crypto.TR34Operations;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.util.DataConverter;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Owns tr34 controls and presentation; dependencies are resolved at use time. */
final class Tr34Coordinator {
    private static final Logger LOG = LoggerFactory.getLogger(Tr34Coordinator.class);
    record View(
            TextArea tr34SenderPrivateKeyArea,
            TextArea tr34SenderCertArea,
            TextArea tr34ReceiverCertArea,
            TextField tr34KeyToDistributeField,
            TextField tr34KeyIdField,
            TextField tr34BindingNonceField,
            CheckBox tr34IncludeEnvelopeCheck,
            TextArea tr34DistributeResultArea,
            TextArea tr34ReceiverPrivateKeyArea,
            TextArea tr34ExpectedSenderCertArea,
            TextArea tr34DistributedDataArea,
            TextField tr34ChallengeNonceField,
            TextArea tr34ReceiveResultArea) { }
    private final Supplier<View> views;
    private final Supplier<StatusReporter> reporter;
    private final Consumer<String> status;
    private final BiFunction<String, Object[], String> text;

    Tr34Coordinator(Supplier<View> views, Supplier<StatusReporter> reporter,
            Consumer<String> status, BiFunction<String, Object[], String> text) {
        this.views = views;
        this.reporter = reporter;
        this.status = status;
        this.text = text;
    }

    private String t(String key, Object... args) { return text.apply(key, args); }
    private void updateStatus(String message) { status.accept(message); }

    public void handleTr34Distribute() {
        View view = views.get();
        try {
            var outcome = Tr34Logic.distribute(view.tr34SenderPrivateKeyArea().getText(), view.tr34SenderCertArea().getText(), view.tr34ReceiverCertArea().getText(), view.tr34KeyToDistributeField().getText(), view.tr34KeyIdField() == null ? "" : view.tr34KeyIdField().getText(), view.tr34BindingNonceField() == null ? "" : view.tr34BindingNonceField().getText(), view.tr34IncludeEnvelopeCheck() != null && view.tr34IncludeEnvelopeCheck().isSelected());
            var keyToDistribute = outcome.keyToDistribute();
            var keyHex = outcome.keyHex();
            var keyId = outcome.keyId();
            var bindingNonceHex = outcome.bindingNonceHex();
            var twoPass = outcome.twoPass();
            var outputText = outcome.outputText();
            String report = outcome.report();

            view.tr34DistributeResultArea().setText(report);
            updateStatus(t("module.keys.tr34.status.distributed"));

            if (reporter.get() != null) {
                List<com.cryptocarver.model.OperationDetail> details = new ArrayList<>();
                if (!keyId.isEmpty()) details.add(com.cryptocarver.model.OperationDetail.publicDetail("Key ID", keyId));
                details.add(com.cryptocarver.model.OperationDetail.publicDetail("Profile", twoPass ? "Two-pass" : "One-pass"));
                if (twoPass) details.add(com.cryptocarver.model.OperationDetail.publicDetail("Binding Nonce", bindingNonceHex.toUpperCase()));
                details.add(com.cryptocarver.model.OperationDetail.secretDetail("Key to Distribute", keyHex));
                details.add(com.cryptocarver.model.OperationDetail.publicDetail("Output", outputText));
                reporter.get().publish(OperationResult.forOperation("TR-34 Key Distribution")
                        .input(keyToDistribute)
                        .output(outputText.getBytes(StandardCharsets.UTF_8))
                        .details(details)
                        .status("TR-34 key distributed successfully")
                        .build());
            }
        } catch (KeyDistributionValidation validation) {
            showTr34Validation(t(validation.messageKey()), validation.fieldKey(), view.tr34DistributeResultArea()::setText);
        } catch (Exception e) {
            showTr34Validation(t("module.keys.tr34.operation", e.getMessage()), "tr34KeyToDistributeField", view.tr34DistributeResultArea()::setText);
            updateStatus(t("module.keys.tr34.status.distributeFailed"));
            logTr34Failure("distribute", e);
        }
    }

    public void handleTr34Receive() {
        View view = views.get();
        try {
            var outcome = Tr34Logic.receive(view.tr34ReceiverPrivateKeyArea().getText(), view.tr34ExpectedSenderCertArea().getText(), view.tr34DistributedDataArea().getText(), view.tr34ChallengeNonceField() == null ? "" : view.tr34ChallengeNonceField().getText());
            var distributed = outcome.distributed();
            var received = outcome.received();
            var recoveredHex = outcome.recoveredHex();
            var twoPass = outcome.twoPass();
            String report = outcome.report();

            view.tr34ReceiveResultArea().setText(report);
            boolean trustworthy = received.isSignatureVerified() && (!twoPass || received.isNonceVerified());
            if (!received.isSignatureVerified()) {
                updateStatus(t("module.keys.tr34.status.receivedUnverified"));
            } else if (twoPass && !received.isNonceVerified()) {
                updateStatus(t("module.keys.tr34.status.receivedNonceMismatch"));
            } else {
                updateStatus(t("module.keys.tr34.status.received"));
            }

            if (reporter.get() != null) {
                List<com.cryptocarver.model.OperationDetail> details = new ArrayList<>();
                details.add(com.cryptocarver.model.OperationDetail.publicDetail("Signature Verified", String.valueOf(received.isSignatureVerified())));
                if (twoPass) details.add(com.cryptocarver.model.OperationDetail.publicDetail("Nonce Verified", String.valueOf(received.isNonceVerified())));
                details.add(com.cryptocarver.model.OperationDetail.secretDetail("Recovered Key (hex)", recoveredHex));
                reporter.get().publish(OperationResult.forOperation("TR-34 Key Reception")
                        .input(distributed)
                        .output(received.getKey(), com.cryptocarver.model.OperationDetail.Classification.SECRET)
                        .details(details)
                        .status(trustworthy ? "TR-34 key received and verified" : "TR-34 key received but NOT verified")
                        .build());
            }
        } catch (KeyDistributionValidation validation) {
            showTr34Validation(t(validation.messageKey()), validation.fieldKey(), view.tr34ReceiveResultArea()::setText);
        } catch (Exception e) {
            showTr34Validation(t("module.keys.tr34.operation", e.getMessage()), "tr34DistributedDataArea", view.tr34ReceiveResultArea()::setText);
            updateStatus(t("module.keys.tr34.status.receiveFailed"));
            logTr34Failure("receive", e);
        }
    }

    public void handleTr34GenerateChallenge() {
        View view = views.get();
        if (view.tr34ChallengeNonceField() == null) return;
        byte[] nonce = TR34Operations.generateChallengeNonce();
        view.tr34ChallengeNonceField().setText(DataConverter.bytesToHex(nonce).toUpperCase());
        updateStatus(t("module.keys.tr34.status.challengeGenerated"));
    }

    public void handleTr34Clear() {
        clearTr34Fields();
        if (reporter.get() != null) reporter.get().updateStatus(t("module.keys.tr34.clearStatus"));
    }

    public void handleTr34Reset() {
        View view = views.get();
        clearTr34Fields();
        if (view.tr34IncludeEnvelopeCheck() != null) view.tr34IncludeEnvelopeCheck().setSelected(false);
        if (reporter.get() != null) reporter.get().updateStatus(t("module.keys.tr34.resetStatus"));
    }

    private void clearTr34Fields() {
        View view = views.get();
        if (view.tr34SenderPrivateKeyArea() != null) view.tr34SenderPrivateKeyArea().clear();
        if (view.tr34SenderCertArea() != null) view.tr34SenderCertArea().clear();
        if (view.tr34ReceiverCertArea() != null) view.tr34ReceiverCertArea().clear();
        if (view.tr34KeyToDistributeField() != null) view.tr34KeyToDistributeField().clear();
        if (view.tr34KeyIdField() != null) view.tr34KeyIdField().clear();
        if (view.tr34BindingNonceField() != null) view.tr34BindingNonceField().clear();
        if (view.tr34DistributeResultArea() != null) view.tr34DistributeResultArea().clear();
        if (view.tr34ReceiverPrivateKeyArea() != null) view.tr34ReceiverPrivateKeyArea().clear();
        if (view.tr34ExpectedSenderCertArea() != null) view.tr34ExpectedSenderCertArea().clear();
        if (view.tr34DistributedDataArea() != null) view.tr34DistributedDataArea().clear();
        if (view.tr34ChallengeNonceField() != null) view.tr34ChallengeNonceField().clear();
        if (view.tr34ReceiveResultArea() != null) view.tr34ReceiveResultArea().clear();
    }

    private void showTr34Validation(String message, String fieldKey, Consumer<String> feedbackTarget) {
        String safeMessage = InlineErrorPresenter.redactSecrets(message);
        UserFacingError error = new UserFacingError(t("module.keys.tr34.errorTitle"), safeMessage, safeMessage, fieldKey);
        if (reporter.get() != null) {
            reporter.get().showError(error);
        } else if (feedbackTarget != null) {
            feedbackTarget.accept(safeMessage);
        }
    }

    private void logTr34Failure(String operation, Exception error) {
        LOG.error("TR-34 {} failed: {}", operation, InlineErrorPresenter.redactSecrets(error.toString()), error);
    }
}
