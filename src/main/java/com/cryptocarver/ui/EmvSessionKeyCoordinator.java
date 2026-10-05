package com.cryptocarver.ui;

import com.cryptocarver.crypto.EMVOperations;
import com.cryptocarver.crypto.EmvSecureMessaging;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.payments.PaymentProfile;
import com.cryptocarver.service.I18nService;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** Coordinates EMV ICC/session-key derivation and the secure-messaging session-key action. */
final class EmvSessionKeyCoordinator {

    record View(Supplier<TextField> imkField,
            Supplier<TextField> panFieldSession,
            Supplier<TextField> panSeqFieldSession,
            Supplier<ComboBox<String>> iccMethodCombo,
            Supplier<TextField> atcField,
            Supplier<TextArea> sessionKeyResultArea,
            Supplier<ComboBox<String>> smSchemeCombo,
            Supplier<TextField> smMkSmiField,
            Supplier<TextField> smMkSmcField,
            Supplier<TextField> smPanSeqField,
            Supplier<TextField> smUdkSmiField,
            Supplier<TextField> smUdkSmcField,
            Supplier<TextField> smAcField,
            Supplier<TextField> smCommandNumberField,
            Supplier<TextField> smAtcField,
            Supplier<TextField> smSkMacField,
            Supplier<TextField> smSkEncField,
            Supplier<TextArea> smResultArea) {
    }

    private final View view;
    private final Supplier<StatusReporter> reporter;

    EmvSessionKeyCoordinator(View view, Supplier<StatusReporter> reporter) {
        this.view = view;
        this.reporter = reporter;
    }

    void handleDeriveSessionKey() {
        try {
            String imk = view.imkField().get().getText().trim().replaceAll("\\s+", "");
            String pan = view.panFieldSession().get().getText().trim().replaceAll("\\s+", "");
            String panSeq = view.panSeqFieldSession().get().getText().trim();
            String atc = view.atcField().get().getText().trim().replaceAll("\\s+", "");

            if (imk.isEmpty() || pan.isEmpty()) {
                view.sessionKeyResultArea().get().setText(text("module.emv.feedback.sessionRequired"));
                return;
            }

            if (panSeq.isEmpty()) {
                panSeq = "00";
            }

            StringBuilder result = new StringBuilder();
            result.append("EMV SESSION KEY DERIVATION\n");
            result.append("═══════════════════════════\n\n");

            result.append("Step 1: Derive ICC Master Key (UDK)\n");
            result.append("───────────────────────────────────\n");
            EMVOperations.IccMasterKeyDerivation derivation = EMVOperations.deriveICCMasterKey(
                    imk, pan, panSeq, selectedIccMethod());
            String iccMK = derivation.key();
            result.append("IMK: ").append(imk).append("\n");
            result.append("PAN: ").append(pan).append("\n");
            result.append("PAN Sequence: ").append(panSeq).append("\n");
            result.append(text("module.emv.iccResult.method")).append(' ').append(derivation.method()).append("\n");
            result.append(text("module.emv.iccResult.input")).append(' ').append(derivation.input()).append("\n");
            if (derivation.method() == EMVOperations.IccMasterKeyMethod.B) {
                result.append(text("module.emv.iccResult.sha1")).append(' ').append(derivation.sha1()).append("\n");
                result.append(text("module.emv.iccResult.decimalized")).append(' ').append(derivation.decimalizedDigits()).append("\n");
                result.append(text("module.emv.iccResult.y")).append(' ').append(derivation.y()).append("\n");
            }
            result.append("➜ ICC Master Key: ").append(iccMK).append("\n\n");

            if (!atc.isEmpty()) {
                result.append("Step 2: Derive Session Key\n");
                result.append("───────────────────────────\n");
                String sessionKey = EMVOperations.deriveSessionKey(iccMK, atc, "");
                result.append("ICC Master Key: ").append(iccMK).append("\n");
                result.append("ATC: ").append(atc).append(" (").append(EMVOperations.formatATC(atc)).append(")\n");
                result.append("➜ Session Key: ").append(sessionKey).append("\n\n");
            }

            result.append("✅ Session key derivation complete\n");
            view.sessionKeyResultArea().get().setText(result.toString());
            view.sessionKeyResultArea().get().setVisible(true);
            view.sessionKeyResultArea().get().setManaged(true);

            LinkedHashMap<String, String> details = new LinkedHashMap<>();
            details.put("PAN", maskPan(pan));
            details.put("PAN Sequence", panSeq);
            details.put("ATC", atc);
            details.put("IMK", "[not persisted]");
            reporter.get().publish(OperationResult.forOperation("Session Key Derivation")
                    .output(result.toString().getBytes(StandardCharsets.UTF_8),
                            OperationDetail.Classification.SECRET).details(details)
                    .status(text("module.emv.status.session")).build());

        } catch (Exception e) {
            TextArea result = view.sessionKeyResultArea().get();
            result.setText(sessionKeyErrorMessage(e));
            result.setVisible(true);
            result.setManaged(true);
        }
    }

    void handleSmDeriveSessionKeys() {
        try {
            StringBuilder report = new StringBuilder();
            String skMac;
            String skEnc;
            if (smVisa()) {
                String atc = smText(view.smAtcField().get());
                skMac = EmvSecureMessaging.visaSessionKey(smText(view.smUdkSmiField().get()), atc);
                skEnc = EmvSecureMessaging.visaSessionKey(smText(view.smUdkSmcField().get()), atc);
                report.append("Visa: session key = UDK with ATC ").append(atc)
                        .append(" XORed into the left half and its complement into the right\n");
            } else {
                if (!smText(view.smMkSmiField().get()).isEmpty() || !smText(view.smMkSmcField().get()).isEmpty()) {
                    String panSeq = smText(view.smPanSeqField().get());
                    view.smUdkSmiField().get().setText(EmvSecureMessaging.mastercardUdk(smText(view.smMkSmiField().get()), panSeq));
                    view.smUdkSmcField().get().setText(EmvSecureMessaging.mastercardUdk(smText(view.smMkSmcField().get()), panSeq));
                    report.append("UDK-SMI: ").append(view.smUdkSmiField().get().getText()).append('\n')
                            .append("UDK-SMC: ").append(view.smUdkSmcField().get().getText()).append("   (EMV option A, odd parity)\n");
                }
                String commandText = smText(view.smCommandNumberField().get());
                int command;
                try {
                    command = commandText.isEmpty() ? 0 : Integer.parseInt(commandText);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(text("module.emv.sm.commandInvalid"));
                }
                if (command < 0) throw new IllegalArgumentException(text("module.emv.sm.commandInvalid"));
                String ac = smText(view.smAcField().get());
                skMac = EmvSecureMessaging.mastercardSessionKey(smText(view.smUdkSmiField().get()), ac, command);
                skEnc = EmvSecureMessaging.mastercardSessionKey(smText(view.smUdkSmcField().get()), ac, command);
                report.append("Mastercard SKD: R = AC + ").append(command).append('\n');
            }
            view.smSkMacField().get().setText(skMac);
            view.smSkEncField().get().setText(skEnc);
            report.append("SK MAC: ").append(skMac).append('\n').append("SK ENC: ").append(skEnc).append('\n');
            showSm(report.toString());
            StatusReporter statusReporter = reporter.get();
            if (statusReporter != null) {
                statusReporter.publish(OperationResult.forOperation("Secure Messaging Session Keys")
                        .output(report.toString().getBytes(StandardCharsets.UTF_8)).details(List.of(
                                OperationDetail.publicDetail("Scheme", view.smSchemeCombo().get().getValue()),
                                OperationDetail.secretDetail("SK MAC", skMac),
                                OperationDetail.secretDetail("SK ENC", skEnc)))
                        .status(text("module.emv.sm.status")).build());
            }
        } catch (Exception e) {
            showSm(text("module.emv.sm.error", e.getMessage()));
        }
    }

    String deriveLaboratorySessionKey(PaymentProfile profile) {
        if (profile.getInputs().containsKey("sessionKey")) {
            return profile.getInputs().get("sessionKey");
        }
        try {
            String imk = profile.getInput("imk");
            String pan = profile.getInput("pan");
            String panSeq = profile.getInput("panSeq");
            String atc = profile.getInput("atc");
            if (imk == null || pan == null || panSeq == null || atc == null) return "";
            String iccMasterKey = EMVOperations.deriveICCMasterKey(imk, pan, panSeq,
                    EMVOperations.IccMasterKeyMethod.AUTO).key();
            return EMVOperations.deriveSessionKey(iccMasterKey, atc, "");
        } catch (Exception ignored) {
            return "";
        }
    }

    private EMVOperations.IccMasterKeyMethod selectedIccMethod() {
        ComboBox<String> combo = view.iccMethodCombo().get();
        if (combo == null) return EMVOperations.IccMasterKeyMethod.AUTO;
        return switch (combo.getSelectionModel().getSelectedIndex()) {
            case 1 -> EMVOperations.IccMasterKeyMethod.A;
            case 2 -> EMVOperations.IccMasterKeyMethod.B;
            default -> EMVOperations.IccMasterKeyMethod.AUTO;
        };
    }

    private String sessionKeyErrorMessage(Exception error) {
        TextField panField = view.panFieldSession().get();
        String pan = panField == null || panField.getText() == null ? ""
                : panField.getText().trim().replaceAll("\\s+", "");
        if (!pan.matches("[0-9]+")) return text("module.emv.error.panDigits");

        TextField imkField = view.imkField().get();
        String imk = imkField == null || imkField.getText() == null ? ""
                : imkField.getText().trim().replaceAll("\\s+", "");
        String compactImk = imk.replace(":", "").replace("-", "");
        if (!compactImk.matches("(?i)[0-9a-f]+")
                || (compactImk.length() != 32 && compactImk.length() != 48)) {
            return text("module.emv.error.imkLength");
        }

        TextField atcField = view.atcField().get();
        String atc = atcField == null || atcField.getText() == null ? ""
                : atcField.getText().trim().replaceAll("\\s+", "");
        if (!atc.isEmpty() && !atc.matches("(?i)[0-9a-f]{4}")) return text("module.emv.error.atcFormat");
        return text("module.emv.error.generate", error.getMessage());
    }

    private boolean smVisa() {
        ComboBox<String> combo = view.smSchemeCombo().get();
        return combo != null && "Visa".equals(combo.getValue());
    }

    private static String smText(TextInputControl field) {
        return field == null || field.getText() == null
                ? "" : field.getText().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    private void showSm(String report) {
        TextArea area = view.smResultArea().get();
        area.setText(report);
        area.setVisible(true);
        area.setManaged(true);
    }

    private static String maskPan(String pan) {
        if (pan == null || pan.length() < 5) return "[redacted]";
        return "*".repeat(Math.max(0, pan.length() - 4)) + pan.substring(pan.length() - 4);
    }

    private static String text(String key, Object... args) {
        return I18nService.getInstance().text(key, args);
    }
}
