package com.cryptocarver.ui;

import com.cryptocarver.crypto.EMVOperations;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.service.I18nService;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.function.Supplier;

/** Coordinates ARQC generation and verification UI behavior. */
final class EmvArqcCoordinator {

    record View(Supplier<TextField> sessionKey,
            Supplier<TextField> amount,
            Supplier<TextField> amountOther,
            Supplier<TextField> currency,
            Supplier<TextField> country,
            Supplier<TextField> atc,
            Supplier<TextField> tvr,
            Supplier<TextField> transactionDate,
            Supplier<TextField> transactionType,
            Supplier<TextField> unpredictableNumber,
            Supplier<TextArea> rawTransactionData,
            Supplier<TextField> iccData,
            Supplier<ComboBox<String>> paddingMethod,
            Supplier<TextArea> resultArea,
            State state) {
    }

    /** The last generated ARQC's exact input and padding, reused only for its matching value. */
    static final class State {
        private String lastTransactionData;
        private int lastPaddingMethod = 1;
        private String lastArqc;

        void record(String transactionData, int paddingMethod, String arqc) {
            lastTransactionData = transactionData;
            lastPaddingMethod = paddingMethod;
            lastArqc = arqc;
        }

        void clear() {
            lastTransactionData = null;
            lastPaddingMethod = 1;
            lastArqc = null;
        }
    }

    private final View view;
    private final Supplier<StatusReporter> reporter;

    EmvArqcCoordinator(View view, Supplier<StatusReporter> reporter) {
        this.view = view;
        this.reporter = reporter;
    }

    void handleGenerateARQC() {
        try {
            TextField skARQCField = view.sessionKey().get();
            TextArea arqcTerminalDataField = view.rawTransactionData().get();
            TextField atcARQCField = view.atc().get();
            TextField amountField = view.amount().get();
            TextField amountOtherField = view.amountOther().get();
            TextField currencyField = view.currency().get();
            TextField countryField = view.country().get();
            TextField tvrField = view.tvr().get();
            TextField txDateField = view.transactionDate().get();
            TextField txTypeField = view.transactionType().get();
            TextField unField = view.unpredictableNumber().get();
            TextArea arqcResultArea = view.resultArea().get();
            TextField iccDataField = view.iccData().get();
            ComboBox<String> arqcPaddingMethodCombo = view.paddingMethod().get();

            String sk = skARQCField.getText().trim().replaceAll("\\s+", "");

            String rawData = arqcTerminalDataField != null
                    ? arqcTerminalDataField.getText().trim().replaceAll("\\s+", "")
                    : "";

            String txData;
            String amount = "";
            String amountOther = "";
            String currency = "";
            String country = "";
            String atc = "";
            String tvr = "";
            String txDate = "";
            String txType = "";
            String un = "";

            if (!rawData.isEmpty()) {
                txData = rawData;
                atc = atcARQCField.getText().trim();
            } else {
                amount = amountField.getText().trim().replaceAll("\\s+", "");
                amountOther = amountOtherField != null ? amountOtherField.getText().trim().replaceAll("\\s+", "")
                        : "";
                currency = currencyField.getText().trim().replaceAll("\\s+", "");
                country = countryField.getText().trim().replaceAll("\\s+", "");
                atc = atcARQCField.getText().trim().replaceAll("\\s+", "");
                tvr = tvrField.getText().trim().replaceAll("\\s+", "");
                txDate = txDateField.getText().trim().replaceAll("\\s+", "");
                txType = txTypeField.getText().trim().replaceAll("\\s+", "");
                un = unField.getText().trim().replaceAll("\\s+", "");

                if (sk.isEmpty() || amount.isEmpty()) {
                    arqcResultArea.setText(text("module.emv.feedback.arqcRequired"));
                    return;
                }

                if (amountOther.isEmpty()) amountOther = "000000000000";
                if (currency.isEmpty()) currency = "0978";
                if (country.isEmpty()) country = "0724";
                if (tvr.isEmpty()) tvr = "0000000000";
                if (txDate.isEmpty()) txDate = "251207";
                if (txType.isEmpty()) txType = "00";
                if (un.isEmpty()) un = "12345678";

                txData = EMVOperations.buildARQCData(
                        amount, amountOther, country, tvr, currency, txDate, txType, un);
            }

            String iccData = iccDataField != null ? iccDataField.getText().trim().replaceAll("\\s+", "") : "";
            if (!iccData.isEmpty()) {
                txData += iccData;
            }

            StringBuilder result = new StringBuilder();
            result.append("ARQC GENERATION (Authorization Request Cryptogram)\n");
            result.append("═══════════════════════════════════════════════════\n\n");

            if (!rawData.isEmpty()) {
                result.append("Using Raw Terminal Data:\n").append(rawData).append("\n");
                if (!iccData.isEmpty()) {
                    result.append("Appended ICC Data:\n").append(iccData).append("\n");
                }
                result.append("Total Input for MAC:\n").append(txData).append("\n\n");
            } else {
                result.append("Transaction Data (external tool structure):\n");
                result.append("─────────────────\n");
                result.append("Amount: ").append(amount).append("\n");
                result.append("Amount Other: ").append(amountOther).append("\n");
                result.append("Country: ").append(country).append(" (Spain/ES)\n");
                result.append("TVR: ").append(tvr).append("\n");
                result.append("Concatenated Data: ").append(txData).append("\n\n");
            }

            result.append("ARQC Calculation:\n");
            result.append("─────────────────\n");
            result.append("Session Key: ").append(sk).append("\n");

            int paddingMethod = 1;
            if (arqcPaddingMethodCombo != null && arqcPaddingMethodCombo.getValue() != null) {
                if (arqcPaddingMethodCombo.getValue().contains("Method 2")) {
                    paddingMethod = 2;
                }
            }
            result.append("Padding Method: ").append(paddingMethod == 2 ? "Method 2" : "Method 1").append("\n");

            String arqc = EMVOperations.generateARQC(sk, txData, paddingMethod);
            view.state().record(txData, paddingMethod, arqc);
            result.append("➜ ARQC: ").append(arqc).append("\n\n");
            result.append("✅ ARQC generated successfully\n");

            arqcResultArea.setText(result.toString());
            arqcResultArea.setVisible(true);
            arqcResultArea.setManaged(true);

            LinkedHashMap<String, String> details = new LinkedHashMap<>();
            details.put("Padding", "Method " + paddingMethod);
            details.put("Transaction Data", txData.substring(0, Math.min(20, txData.length())) + "...");
            details.put("Session Key", "[not persisted]");
            reporter.get().publish(OperationResult.forOperation("ARQC Generation")
                    .output(com.cryptocarver.util.DataConverter.hexToBytes(arqc), OperationDetail.Classification.SECRET)
                    .details(details)
                    .status(text("module.emv.status.arqc")).build());

        } catch (Exception e) {
            TextArea resultArea = view.resultArea().get();
            resultArea.setText(arqcGenerationErrorMessage(e));
            resultArea.setVisible(true);
            resultArea.setManaged(true);
        }
    }

    void handleVerifyARQC() {
        try {
            TextField skARQCField = view.sessionKey().get();
            TextField amountField = view.amount().get();
            TextArea arqcResultArea = view.resultArea().get();
            TextField amountOtherField = view.amountOther().get();
            TextField currencyField = view.currency().get();
            TextField countryField = view.country().get();
            TextField tvrField = view.tvr().get();
            TextField txDateField = view.transactionDate().get();
            TextField txTypeField = view.transactionType().get();
            TextField unField = view.unpredictableNumber().get();
            TextField iccDataField = view.iccData().get();
            ComboBox<String> arqcPaddingMethodCombo = view.paddingMethod().get();

            String sk = skARQCField.getText().trim().replaceAll("\\s+", "");
            String amount = amountField.getText().trim().replaceAll("\\s+", "");
            String arqcToVerify = arqcResultArea.getText();

            if (arqcToVerify.contains("ARQC: ")) {
                int start = arqcToVerify.indexOf("ARQC: ") + 6;
                int end = arqcToVerify.indexOf("\n", start);
                if (end == -1) end = arqcToVerify.length();
                arqcToVerify = arqcToVerify.substring(start, end).trim();
            }

            if (sk.isEmpty() || arqcToVerify.isEmpty() || arqcToVerify.length() != 16) {
                arqcResultArea.setText(text("module.emv.error.generateFirst"));
                return;
            }

            String txData;
            int paddingMethod;
            State state = view.state();
            if (state.lastTransactionData != null && arqcToVerify.equalsIgnoreCase(state.lastArqc)) {
                txData = state.lastTransactionData;
                paddingMethod = state.lastPaddingMethod;
            } else {
                if (amount.isEmpty()) throw new IllegalArgumentException(text("module.emv.feedback.arqcAmountRequired"));
                String amountOther = amountOtherField == null ? "" : amountOtherField.getText().trim().replaceAll("\\s+", "");
                String currency = currencyField.getText().trim().replaceAll("\\s+", "");
                String country = countryField.getText().trim().replaceAll("\\s+", "");
                String tvr = tvrField.getText().trim().replaceAll("\\s+", "");
                String txDate = txDateField.getText().trim().replaceAll("\\s+", "");
                String txType = txTypeField.getText().trim().replaceAll("\\s+", "");
                String un = unField.getText().trim().replaceAll("\\s+", "");
                if (amountOther.isEmpty()) amountOther = "000000000000";
                if (currency.isEmpty()) currency = "0978";
                if (country.isEmpty()) country = "0724";
                if (tvr.isEmpty()) tvr = "0000000000";
                if (txDate.isEmpty()) txDate = "251207";
                if (txType.isEmpty()) txType = "00";
                if (un.isEmpty()) un = "12345678";
                txData = EMVOperations.buildARQCData(amount, amountOther, country, tvr, currency, txDate, txType, un);
                String iccData = iccDataField == null ? "" : iccDataField.getText().trim().replaceAll("\\s+", "");
                if (!iccData.isEmpty()) txData += iccData;
                paddingMethod = arqcPaddingMethodCombo != null && arqcPaddingMethodCombo.getValue() != null
                        && arqcPaddingMethodCombo.getValue().contains("Method 2") ? 2 : 1;
            }

            boolean valid = EMVOperations.verifyARQC(sk, arqcToVerify, txData, paddingMethod);

            StringBuilder result = new StringBuilder();
            result.append("ARQC VERIFICATION\n");
            result.append("═════════════════\n\n");
            result.append("ARQC to Verify: ").append(arqcToVerify).append("\n");
            result.append("Padding Method: ").append(paddingMethod).append("\n");
            result.append("MAC input bytes: ").append(txData.length() / 2).append("\n");
            result.append("Session Key: ").append(sk).append("\n\n");

            if (valid) {
                result.append("✅ ARQC IS VALID\n");
                result.append("\nThe cryptogram is authentic and the transaction data has not been tampered with.\n");
            } else {
                result.append("❌ ARQC IS INVALID\n");
                result.append("\nThe cryptogram does not match. Possible reasons:\n");
                result.append("- Wrong session key\n");
                result.append("- Transaction data has been modified\n");
                result.append("- ARQC was generated with different parameters\n");
            }

            arqcResultArea.setText(result.toString());
            arqcResultArea.setVisible(true);
            arqcResultArea.setManaged(true);

            StatusReporter statusReporter = reporter.get();
            if (statusReporter != null) {
                statusReporter.publish(OperationResult.forOperation("ARQC Verification")
                        .output(result.toString().getBytes(StandardCharsets.UTF_8), OperationDetail.Classification.SECRET)
                        .detail("Valid", String.valueOf(valid))
                        .detail("Padding", "Method " + paddingMethod)
                        .status(text(valid ? "module.emv.feedback.arqcValid" : "module.emv.feedback.arqcInvalid")).build());
            }

        } catch (Exception e) {
            view.resultArea().get().setText(text("module.emv.error.verification", e.getMessage()));
        }
    }

    private String arqcGenerationErrorMessage(Exception error) {
        TextField sessionKeyField = view.sessionKey().get();
        String sessionKey = sessionKeyField == null || sessionKeyField.getText() == null ? ""
                : sessionKeyField.getText().trim().replaceAll("\\s+", "");
        if (!sessionKey.isEmpty() && !sessionKey.matches("(?i)[0-9a-f]{32}")) {
            return text("module.emv.error.arqcSessionKeyLength");
        }

        TextArea rawDataArea = view.rawTransactionData().get();
        String rawData = rawDataArea == null || rawDataArea.getText() == null ? ""
                : rawDataArea.getText().trim().replaceAll("\\s+", "");
        TextField unField = view.unpredictableNumber().get();
        String un = unField == null || unField.getText() == null ? ""
                : unField.getText().trim().replaceAll("\\s+", "");
        if (rawData.isEmpty() && !un.isEmpty() && !un.matches("(?i)[0-9a-f]{8}")) {
            return text("module.emv.error.arqcUnFormat");
        }
        return text("module.emv.error.generate", error.getMessage());
    }

    private static String text(String key, Object... args) {
        return I18nService.getInstance().text(key, args);
    }
}
