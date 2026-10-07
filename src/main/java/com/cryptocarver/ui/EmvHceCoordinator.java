package com.cryptocarver.ui;

import com.cryptocarver.crypto.VisaHceOperations;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import javafx.scene.control.*;
import java.util.function.Supplier;

/** Coordinates the extracted EMV actions using the controller's existing controls. */
final class EmvHceCoordinator {
    record View(Supplier<TextField> hceUdkField,
            Supplier<TextField> hceYearField,
            Supplier<TextField> hceHoursField,
            Supplier<TextField> hceCounterField,
            Supplier<TextField> hceMsdLukField,
            Supplier<TextField> hceQvsdcLukField,
            Supplier<TextField> hceMsdAtcField,
            Supplier<TextField> hceDeviceTypeField,
            Supplier<TextField> hceAmountField,
            Supplier<TextField> hceOtherAmountField,
            Supplier<TextField> hceCountryField,
            Supplier<TextField> hceTvrField,
            Supplier<TextField> hceCurrencyField,
            Supplier<TextField> hceDateField,
            Supplier<TextField> hceTypeField,
            Supplier<TextField> hceUnField,
            Supplier<TextField> hceAipField,
            Supplier<TextField> hceQvsdcAtcField,
            Supplier<TextField> hceCvrField,
            Supplier<TextArea> hceResultArea) { }
    private final View view;
    private final Supplier<StatusReporter> reporter;
    EmvHceCoordinator(View view, Supplier<StatusReporter> reporter) {
        this.view = view;
        this.reporter = reporter;
    }
    private String t(String key, Object... args) {
        return com.cryptocarver.service.I18nService.getInstance().text(key, args);
    }

    void handleHceLoadExample() {
        view.hceUdkField().get().setText("94E3194C02105E3B153438D562D5A49D");
        view.hceYearField().get().setText("26"); view.hceHoursField().get().setText("6431"); view.hceCounterField().get().setText("01");
        view.hceMsdLukField().get().clear(); view.hceQvsdcLukField().get().clear();
        view.hceMsdAtcField().get().setText("0001"); view.hceDeviceTypeField().get().setText("AAAA000000000001");
        view.hceAmountField().get().setText("000000001000"); view.hceOtherAmountField().get().setText("000000000000");
        view.hceCountryField().get().setText("0710"); view.hceTvrField().get().setText("0000000000");
        view.hceCurrencyField().get().setText("0710"); view.hceDateField().get().setText("130205");
        view.hceTypeField().get().setText("00"); view.hceUnField().get().setText("30901B6A");
        view.hceAipField().get().setText("3C00"); view.hceQvsdcAtcField().get().setText("0055");
        view.hceCvrField().get().setText("03A4A082");
        emvShow(view.hceResultArea().get(), t("module.emv.hce.exampleLoaded"));
    }

    void handleHceLuk() {
        try {
            String udk = emvHex(view.hceUdkField().get(), "module.emv.hce.udk", 16);
            String year = smText(view.hceYearField().get()), hours = smText(view.hceHoursField().get()), counter = smText(view.hceCounterField().get());
            if (!year.matches("\\d{1,2}")) throw new IllegalArgumentException(t("module.emv.hce.yearInvalid"));
            if (!hours.matches("\\d{4}")) throw new IllegalArgumentException(t("module.emv.hce.hoursInvalid"));
            if (!counter.matches("\\d{2}")) throw new IllegalArgumentException(t("module.emv.hce.counterInvalid"));
            String luk = VisaHceOperations.limitedUseKey(udk, year, hours, counter);
            view.hceMsdLukField().get().setText(luk); view.hceQvsdcLukField().get().setText(luk);
            emvShow(view.hceResultArea().get(), t("module.emv.hce.lukResult", luk));
            emvPublish("module.emv.hce.lukAction", "module.emv.hce.status", luk, true,
                    java.util.List.of(OperationDetail.secretDetail("UDK", udk), OperationDetail.secretDetail("LUK", luk)));
        } catch (Exception e) { emvShow(view.hceResultArea().get(), t("module.emv.hce.error", e.getMessage())); }
    }

    void handleHceMsd() {
        try {
            String luk = emvHex(view.hceMsdLukField().get(), "module.emv.hce.luk", 16);
            String atc = emvHex(view.hceMsdAtcField().get(), "module.emv.hce.atc", 2);
            String device = emvHex(view.hceDeviceTypeField().get(), "module.emv.hce.deviceType", 8);
            String value = VisaHceOperations.msdVerificationValue(luk, atc, device);
            emvShow(view.hceResultArea().get(), t("module.emv.hce.msdResult", value));
            emvPublish("module.emv.hce.msdAction", "module.emv.hce.status", value, false,
                    java.util.List.of(OperationDetail.secretDetail("LUK", luk), OperationDetail.publicDetail("MSD", value)));
        } catch (Exception e) { emvShow(view.hceResultArea().get(), t("module.emv.hce.error", e.getMessage())); }
    }

    void handleHceQvsdc() {
        try {
            String luk = emvHex(view.hceQvsdcLukField().get(), "module.emv.hce.luk", 16);
            String terminal = emvHex(view.hceAmountField().get(), "module.emv.hce.amount", 6)
                    + emvHex(view.hceOtherAmountField().get(), "module.emv.hce.otherAmount", 6)
                    + emvHex(view.hceCountryField().get(), "module.emv.hce.country", 2)
                    + emvHex(view.hceTvrField().get(), "module.emv.hce.tvr", 5)
                    + emvHex(view.hceCurrencyField().get(), "module.emv.hce.currency", 2)
                    + emvHex(view.hceDateField().get(), "module.emv.hce.date", 3)
                    + emvHex(view.hceTypeField().get(), "module.emv.hce.type", 1)
                    + emvHex(view.hceUnField().get(), "module.emv.hce.un", 4);
            String chip = emvHex(view.hceAipField().get(), "module.emv.hce.aip", 2)
                    + emvHex(view.hceQvsdcAtcField().get(), "module.emv.hce.atc", 2)
                    + emvHex(view.hceCvrField().get(), "module.emv.hce.cvr", 4);
            String value = VisaHceOperations.qvsdcCryptogram(luk, terminal, chip);
            emvShow(view.hceResultArea().get(), t("module.emv.hce.qvsdcResult", value));
            emvPublish("module.emv.hce.qvsdcAction", "module.emv.hce.status", value, false,
                    java.util.List.of(OperationDetail.secretDetail("LUK", luk), OperationDetail.publicDetail("qVSDC", value)));
        } catch (Exception e) { emvShow(view.hceResultArea().get(), t("module.emv.hce.error", e.getMessage())); }
    }

    private static String smText(TextInputControl field) {
        return field == null || field.getText() == null ? "" : field.getText().replaceAll("\\s+", "").toUpperCase(java.util.Locale.ROOT);
    }

    private String emvHex(TextField field, String labelKey, int bytes) {
        String value = smText(field);
        if (!value.matches("[0-9A-F]{" + (bytes * 2) + "}"))
            throw new IllegalArgumentException(t("module.emv.hce.hexLength", t(labelKey), bytes));
        return value;
    }

    private void emvShow(TextArea area, String text) {
        area.setText(text);
        area.setVisible(true);
        area.setManaged(true);
    }

    private void emvPublish(String operationKey, String statusKey, String value,
                            boolean secret, java.util.List<OperationDetail> details) {
        if (reporter.get() == null) return;
        reporter.get().publish(OperationResult.forOperation(t(operationKey))
                .output(value.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        secret ? OperationDetail.Classification.SECRET : OperationDetail.Classification.PUBLIC)
                .details(details).status(t(statusKey)).build());
    }
}
