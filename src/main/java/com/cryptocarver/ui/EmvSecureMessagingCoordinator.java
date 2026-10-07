package com.cryptocarver.ui;

import com.cryptocarver.crypto.EmvSecureMessaging;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import javafx.scene.control.*;
import java.util.function.Supplier;

/** Coordinates the extracted EMV actions using the controller's existing controls. */
final class EmvSecureMessagingCoordinator {
    record View(Supplier<TextInputControl> smMkSmiField,
            Supplier<TextInputControl> smMkSmcField,
            Supplier<TextInputControl> smPanSeqField,
            Supplier<TextInputControl> smUdkSmiField,
            Supplier<TextInputControl> smUdkSmcField,
            Supplier<TextInputControl> smAcField,
            Supplier<TextInputControl> smCommandNumberField,
            Supplier<TextInputControl> smAtcField,
            Supplier<TextInputControl> smUdkAField,
            Supplier<TextInputControl> smHeaderField,
            Supplier<TextInputControl> smPinField,
            Supplier<TextInputControl> smSkMacField,
            Supplier<TextInputControl> smSkEncField,
            Supplier<TextInputControl> smDataField,
            Supplier<ComboBox<String>> smSchemeCombo,
            Supplier<TextArea> smResultArea) { }
    private final View view;
    private final Supplier<StatusReporter> reporter;
    EmvSecureMessagingCoordinator(View view, Supplier<StatusReporter> reporter) {
        this.view = view;
        this.reporter = reporter;
    }
    private String t(String key, Object... args) {
        return com.cryptocarver.service.I18nService.getInstance().text(key, args);
    }

    void handleSmLoadExample() {
        if (smVisa()) {
            view.smMkSmiField().get().clear(); view.smMkSmcField().get().clear(); view.smPanSeqField().get().clear();
            view.smUdkSmiField().get().setText("94E3194C02105E3B153438D562D5A49D");
            view.smUdkSmcField().get().setText("94E3194C02105E3B153438D562D5A49D");
            view.smAcField().get().setText("EFB5340A1BF07421");
            view.smCommandNumberField().get().clear();
            view.smAtcField().get().setText("0003");
            view.smUdkAField().get().setText("64C8621A76A2EA9EF23D5749FE1A64F1");
            view.smHeaderField().get().setText("8424000218");
        } else {
            view.smMkSmiField().get().setText("862F13DF807A13B9D9AEAEC885FE7CA4");
            view.smMkSmcField().get().setText("BF89B32308CDADDC04B952C7DF0715E0");
            view.smPanSeqField().get().setText("7430100000157500");
            view.smUdkSmiField().get().clear(); view.smUdkSmcField().get().clear();
            view.smAcField().get().setText("51DB71A5DCC47F8A");
            view.smCommandNumberField().get().setText("1");
            view.smAtcField().get().setText("0010");
            view.smUdkAField().get().clear();
            view.smHeaderField().get().setText("8424000210");
        }
        view.smPinField().get().setText("4222");
        view.smSkMacField().get().clear(); view.smSkEncField().get().clear(); view.smDataField().get().clear();
        smShow(t("module.emv.sm.exampleLoaded", view.smSchemeCombo().get().getValue()));
    }

    void handleSmEncipherPin() {
        try {
            String pin = view.smPinField().get().getText() == null ? "" : view.smPinField().get().getText().trim();
            if (!pin.matches("\\d{4,12}")) throw new IllegalArgumentException(t("module.emv.sm.pinInvalid"));
            String encrypted = smVisa()
                    ? EmvSecureMessaging.visaEncryptedPin(smText(view.smSkEncField().get()), smText(view.smUdkAField().get()), pin)
                    : EmvSecureMessaging.mastercardEncryptedPin(smText(view.smSkEncField().get()), pin);
            view.smDataField().get().setText(encrypted);
            String report = (smVisa() ? "Visa PIN data (08 || PIN block XOR UDK A || 80..), TDES ECB\n"
                    : "Mastercard ISO format 2 PIN block, TDES ECB\n") + "Enciphered PIN: " + encrypted + '\n';
            smShow(report);
            // The PIN itself is never published.
            smPublish("Secure Messaging PIN", report, java.util.List.of(
                    com.cryptocarver.model.OperationDetail.publicDetail("Scheme", view.smSchemeCombo().get().getValue()),
                    com.cryptocarver.model.OperationDetail.publicDetail("Enciphered PIN", encrypted)));
        } catch (Exception e) {
            smShow(t("module.emv.sm.error", e.getMessage()));
        }
    }

    void handleSmGenerateMac() {
        try {
            String mac = EmvSecureMessaging.commandMac(smText(view.smSkMacField().get()), smText(view.smHeaderField().get()), smText(view.smAtcField().get()),
                    smText(view.smAcField().get()), smText(view.smDataField().get()));
            String command = smText(view.smHeaderField().get()) + smText(view.smDataField().get()) + mac.substring(0, 8);
            String report = "MAC (ISO 9797-1 alg. 3): " + mac + '\n' + "Command with 4-byte MAC: " + command + '\n';
            smShow(report);
            smPublish("Secure Messaging MAC", report, java.util.List.of(
                    com.cryptocarver.model.OperationDetail.publicDetail("Scheme", view.smSchemeCombo().get().getValue()),
                    com.cryptocarver.model.OperationDetail.publicDetail("MAC", mac)));
        } catch (Exception e) {
            smShow(t("module.emv.sm.error", e.getMessage()));
        }
    }

    private boolean smVisa() {
        return view.smSchemeCombo().get() != null && "Visa".equals(view.smSchemeCombo().get().getValue());
    }

    private void smShow(String text) {
        view.smResultArea().get().setText(text);
        view.smResultArea().get().setVisible(true);
        view.smResultArea().get().setManaged(true);
    }

    private void smPublish(String operation, String report, java.util.List<com.cryptocarver.model.OperationDetail> details) {
        if (reporter.get() == null) return;
        reporter.get().publish(OperationResult.forOperation(operation)
                .output(report.getBytes(java.nio.charset.StandardCharsets.UTF_8)).details(details)
                .status(t("module.emv.sm.status")).build());
    }

    private static String smText(TextInputControl field) {
        return field == null || field.getText() == null ? "" : field.getText().replaceAll("\\s+", "").toUpperCase(java.util.Locale.ROOT);
    }
}
