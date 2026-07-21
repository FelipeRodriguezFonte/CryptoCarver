package com.cryptoforge.ui;

import com.cryptoforge.crypto.EmvTlv;

import com.cryptoforge.crypto.EMVOperations;
import com.cryptoforge.model.OperationResult;

import javafx.fxml.FXML;
import javafx.scene.control.*;

/**
 * Controller for EMV tab operations
 * Handles ARQC/ARPC, session key derivation, and EMV cryptography
 *
 * @author Felipe
 */
public class EMVController {
    private TextArea emvTlvInputArea, emvTlvResultArea;
    private TextField emvDolTemplateField;
    private TextArea emvDolValuesArea, emvDolResultArea;

    public void initializeTlvInspector(TextArea input, TextArea output) { this.emvTlvInputArea = input; this.emvTlvResultArea = output; }
    public void initializeDolBuilder(TextField template, TextArea values, TextArea output) { this.emvDolTemplateField = template; this.emvDolValuesArea = values; this.emvDolResultArea = output; }

    public void handleBuildDol() {
        try {
            java.util.Map<String, String> values = new java.util.LinkedHashMap<>();
            for (String line : emvDolValuesArea.getText().split("\\R")) {
                if (line.isBlank()) continue;
                String[] pair = line.split("=", 2);
                if (pair.length != 2) throw new IllegalArgumentException("Use one TAG=HEX value per line");
                values.put(pair[0].trim().toUpperCase(java.util.Locale.ROOT), pair[1].trim());
            }
            EmvTlv.DolBuildResult result = EmvTlv.buildDolDetailed(emvDolTemplateField.getText(), values);
            StringBuilder report = new StringBuilder("CDOL/DDOL data (").append(result.data().length() / 2).append(" bytes):\n")
                    .append(result.data()).append("\n\nFIELDS:\n");
            for (EmvTlv.DolField field : result.fields()) {
                report.append(field.supplied() ? "[provided] " : "[zero-fill] ").append(field.tag()).append(" — ")
                        .append(field.name()).append(" (" ).append(field.length()).append(" bytes): ").append(field.value()).append("\n");
            }
            if (!result.warnings().isEmpty()) {
                report.append("WARNINGS:\n");
                for (String warning : result.warnings()) report.append("- ").append(warning).append("\n");
            }
            emvDolResultArea.setText(report.toString());
        } catch (Exception e) { emvDolResultArea.setText("DOL error: " + e.getMessage()); }
    }

    public void handleInspectTlv() {
        try {
            java.util.List<EmvTlv.Item> items = EmvTlv.parse(emvTlvInputArea.getText());
            EmvTlv.Analysis analysis = EmvTlv.analyze(emvTlvInputArea.getText());
            StringBuilder report = new StringBuilder("--- EMV BER-TLV Inspector ---\n")
                    .append("Top-level objects: ").append(analysis.topLevelItems()).append(" | Total objects: ").append(analysis.totalItems())
                    .append(" | Value bytes: ").append(analysis.totalValueBytes()).append("\n\n")
                    .append("TRANSACTION SUMMARY (informative; not cryptogram validation)\n")
                    .append(EmvTlv.transactionSummary(analysis)).append("\n");
            if (!analysis.warnings().isEmpty()) {
                report.append("STRUCTURAL WARNINGS\n");
                for (String warning : analysis.warnings()) report.append("- ").append(warning).append("\n");
                report.append("\n");
            }
            report.append("TLV TREE\n");
            appendTlv(report, items, "");
            emvTlvResultArea.setText(report.toString());
        } catch (Exception e) { emvTlvResultArea.setText("TLV error: " + e.getMessage()); }
    }

    private void appendTlv(StringBuilder out, java.util.List<EmvTlv.Item> items, String indent) {
        for (EmvTlv.Item item : items) {
            out.append(indent).append(item.tag()).append(" — ").append(item.name()).append(" (" ).append(item.length()).append(" bytes)\n");
            if (item.constructed()) appendTlv(out, item.children(), indent + "  ");
            else {
                out.append(indent).append("  Value: ").append(item.value()).append("\n");
                String interpretation = EmvTlv.interpretation(item);
                if (!interpretation.isBlank()) out.append(indent).append("  Interpreted: ").append(interpretation).append("\n");
                if ("8C".equals(item.tag()) || "8D".equals(item.tag())) {
                    out.append(indent).append("  DOL fields:\n");
                    int total = 0;
                    for (EmvTlv.Item field : EmvTlv.parseDol(item.value())) {
                        total += field.length();
                        out.append(indent).append("    ").append(field.tag()).append(" — ").append(field.name())
                                .append(" (request ").append(field.length()).append(" bytes)\n");
                    }
                    out.append(indent).append("  Required concatenated data: ").append(total).append(" bytes\n");
                }
            }
        }
    }

    private StatusReporter mainController;

    // Session Key Derivation controls
    private TextField imkField;
    private TextField panFieldSession;
    private TextField panSeqFieldSession;
    private TextField atcField;
    private TextArea sessionKeyResultArea;

    // ARQC Generation controls
    private TextField skARQCField;
    private TextField amountField;
    private TextField currencyField;
    private TextField countryField;
    private TextField atcARQCField;
    private TextField tvrField;
    private TextField txDateField;
    private TextField txTypeField;
    private TextField unField;
    private TextArea arqcResultArea;
    /** Captures the exact bytes and padding used by the last local ARQC calculation. */
    private String lastArqcTransactionData;
    private int lastArqcPaddingMethod = 1;
    private String lastArqcValue;

    // ARPC Generation controls
    private TextField skARPCField;
    private TextField arqcField;
    private TextField arcField;
    private TextField csuField;
    private ComboBox<String> arpcMethodCombo;
    private TextArea arpcResultArea;

    // Track 2 controls
    private TextField panTrack2Field;
    private TextField expiryTrack2Field;
    private TextField serviceCodeFieldTrack2;
    private TextField discretionaryDataField;
    private TextField track2InputField;
    private TextArea track2ResultArea;

    // New fields
    private TextArea arqcTerminalDataField;
    private TextField amountOtherField;
    private TextField iccDataField;
    private ComboBox<String> arqcPaddingMethodCombo;
    private TextField propAuthDataField; // New

    public void initialize(StatusReporter mainController,
            // Session Key fields
            TextField imkField,
            TextField panFieldSession,
            TextField panSeqFieldSession,
            TextField atcField,
            TextArea sessionKeyResultArea,
            // ARQC fields
            TextField skARQCField,
            TextField amountField,
            TextField currencyField,
            TextField countryField,
            TextField atcARQCField,
            TextField tvrField,
            TextField txDateField,
            TextField txTypeField,
            TextField unField,
            TextArea arqcResultArea,
            TextArea arqcTerminalDataField, // New
            TextField amountOtherField, // New
            TextField iccDataField, // New
            ComboBox<String> arqcPaddingMethodCombo, // New
            // ARQC fields (end)
            // ARPC fields
            TextField skARPCField,
            TextField arqcField,
            TextField arcField,
            TextField csuField,
            TextField propAuthDataField, // New
            ComboBox<String> arpcMethodCombo,
            TextArea arpcResultArea,
            // Track 2 fields
            TextField panTrack2Field,
            TextField expiryTrack2Field,
            TextField serviceCodeFieldTrack2,
            TextField discretionaryDataField,
            TextField track2InputField,
            TextArea track2ResultArea) {

        this.mainController = mainController;
        this.imkField = imkField;
        this.panFieldSession = panFieldSession;
        this.panSeqFieldSession = panSeqFieldSession;
        this.atcField = atcField;
        this.sessionKeyResultArea = sessionKeyResultArea;
        this.arqcPaddingMethodCombo = arqcPaddingMethodCombo;

        setupARQCPaddingMethods();

        this.skARQCField = skARQCField;
        this.amountField = amountField;
        this.currencyField = currencyField;
        this.countryField = countryField;
        this.atcARQCField = atcARQCField;
        this.tvrField = tvrField;
        this.txDateField = txDateField;
        this.txTypeField = txTypeField;
        this.unField = unField;
        this.arqcResultArea = arqcResultArea;
        this.arqcTerminalDataField = arqcTerminalDataField; // New
        this.amountOtherField = amountOtherField; // New
        this.iccDataField = iccDataField; // New

        this.skARPCField = skARPCField;
        this.arqcField = arqcField;
        this.arcField = arcField;
        this.csuField = csuField;
        this.propAuthDataField = propAuthDataField; // New
        this.arpcMethodCombo = arpcMethodCombo;
        this.arpcResultArea = arpcResultArea;

        this.panTrack2Field = panTrack2Field;
        this.expiryTrack2Field = expiryTrack2Field;
        this.serviceCodeFieldTrack2 = serviceCodeFieldTrack2;
        this.discretionaryDataField = discretionaryDataField;
        this.track2InputField = track2InputField;
        this.track2ResultArea = track2ResultArea;

        setupARPCMethods();
    }

    private void setupARPCMethods() {
        arpcMethodCombo.getItems().addAll(
                "Method 1 (XOR with ARQC)",
                "Method 2 (CSU Method)");
        arpcMethodCombo.getSelectionModel().selectFirst();
    }

    // Helper to setup padding methods
    private void setupARQCPaddingMethods() {
        if (arqcPaddingMethodCombo != null) {
            arqcPaddingMethodCombo.getItems().clear();
            arqcPaddingMethodCombo.getItems().addAll(
                    "Method 1 (ISO 9797-1)",
                    "Method 2 (ISO 9797-1 / EMV)");
            arqcPaddingMethodCombo.getSelectionModel().selectFirst(); // Defaults to Method 1
        }
    }

    // ============================================================================
    // SESSION KEY DERIVATION
    // ============================================================================

    public void handleDeriveSessionKey() {
        try {
            String imk = imkField.getText().trim().replaceAll("\\s+", "");
            String pan = panFieldSession.getText().trim().replaceAll("\\s+", "");
            String panSeq = panSeqFieldSession.getText().trim();
            String atc = atcField.getText().trim().replaceAll("\\s+", "");

            if (imk.isEmpty() || pan.isEmpty()) {
                sessionKeyResultArea.setText("Error: IMK and PAN are required");
                return;
            }

            if (panSeq.isEmpty()) {
                panSeq = "00";
            }

            StringBuilder result = new StringBuilder();
            result.append("EMV SESSION KEY DERIVATION\n");
            result.append("═══════════════════════════\n\n");

            // Step 1: Derive ICC Master Key
            result.append("Step 1: Derive ICC Master Key (UDK)\n");
            result.append("───────────────────────────────────\n");
            String iccMK = EMVOperations.deriveICCMasterKey(imk, pan, panSeq);
            result.append("IMK: ").append(imk).append("\n");
            result.append("PAN: ").append(pan).append("\n");
            result.append("PAN Sequence: ").append(panSeq).append("\n");
            result.append("➜ ICC Master Key: ").append(iccMK).append("\n\n");

            // Step 2: Derive Session Key (if ATC provided)
            if (!atc.isEmpty()) {
                result.append("Step 2: Derive Session Key\n");
                result.append("───────────────────────────\n");
                String sessionKey = EMVOperations.deriveSessionKey(iccMK, atc, "");
                result.append("ICC Master Key: ").append(iccMK).append("\n");
                result.append("ATC: ").append(atc).append(" (").append(EMVOperations.formatATC(atc)).append(")\n");
                result.append("➜ Session Key: ").append(sessionKey).append("\n\n");
            }

            result.append("✅ Session key derivation complete\n");
            sessionKeyResultArea.setText(result.toString());
            sessionKeyResultArea.setVisible(true);
            sessionKeyResultArea.setManaged(true);

            java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
            details.put("PAN", maskPan(pan));
            details.put("PAN Sequence", panSeq);
            details.put("ATC", atc);
            details.put("IMK", "[not persisted]");
            mainController.publish(OperationResult.forOperation("Session Key Derivation")
                    .output(result.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).details(details)
                    .status("EMV session key derivation completed").build());

        } catch (Exception e) {
            sessionKeyResultArea.setText("Error: " + e.getMessage());
            sessionKeyResultArea.setVisible(true);
            sessionKeyResultArea.setManaged(true);
        }
    }

    // ============================================================================
    // ARQC GENERATION
    // ============================================================================

    // ============================================================================
    // ARQC GENERATION
    // ============================================================================

    public void handleGenerateARQC() {
        try {
            String sk = skARQCField.getText().trim().replaceAll("\\s+", "");

            // Check for Raw Data override
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
                // Use Raw Data directly
                txData = rawData;
                atc = atcARQCField.getText().trim(); // Still read for history/info
            } else {
                // Construct from Individual Fields (BP Tools Structure)
                amount = amountField.getText().trim().replaceAll("\\s+", "");
                amountOther = amountOtherField != null ? amountOtherField.getText().trim().replaceAll("\\s+", "")
                        : "";
                currency = currencyField.getText().trim().replaceAll("\\s+", "");
                country = countryField.getText().trim().replaceAll("\\s+", "");
                atc = atcARQCField.getText().trim().replaceAll("\\s+", ""); // Info/Key derivation context
                tvr = tvrField.getText().trim().replaceAll("\\s+", "");
                txDate = txDateField.getText().trim().replaceAll("\\s+", "");
                txType = txTypeField.getText().trim().replaceAll("\\s+", "");
                un = unField.getText().trim().replaceAll("\\s+", "");

                if (sk.isEmpty() || amount.isEmpty()) {
                    arqcResultArea.setText("Error: Session Key and Amount are required");
                    return;
                }

                // Set defaults if not provided
                if (amountOther.isEmpty())
                    amountOther = "000000000000";
                if (currency.isEmpty())
                    currency = "0978"; // EUR
                if (country.isEmpty())
                    country = "0724"; // Spain
                if (tvr.isEmpty())
                    tvr = "0000000000"; // All zeros
                if (txDate.isEmpty())
                    txDate = "251207"; // YYMMDD
                if (txType.isEmpty())
                    txType = "00"; // Purchase
                if (un.isEmpty())
                    un = "12345678"; // Random UN

                // Build transaction data (New Structure: Amt, AmtOther, Ctry, TVR, Cur, Date,
                // Type, UN)
                txData = EMVOperations.buildARQCData(
                        amount, amountOther, country, tvr, currency, txDate, txType, un);
            }

            // Append ICC Data if present (Applicable to BOTH Raw and Constructed modes)
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
                result.append("Transaction Data (BP-Tools Structure):\n");
                result.append("─────────────────\n");
                result.append("Amount: ").append(amount).append("\n");
                result.append("Amount Other: ")
                        .append(amountOther).append("\n");
                result.append("Country: ").append(country).append(" (Spain/ES)\n");
                result.append("TVR: ").append(tvr).append("\n");
                // ... (simplified logs)
                result.append("Concatenated Data: ").append(txData).append("\n\n");
            }

            // Generate ARQC
            result.append("ARQC Calculation:\n");
            result.append("─────────────────\n");
            result.append("Session Key: ").append(sk).append("\n");

            // Determine Padding Method
            int paddingMethod = 1; // Default
            if (arqcPaddingMethodCombo != null && arqcPaddingMethodCombo.getValue() != null) {
                if (arqcPaddingMethodCombo.getValue().contains("Method 2")) {
                    paddingMethod = 2;
                }
            }
            result.append("Padding Method: ").append(paddingMethod == 2 ? "Method 2" : "Method 1").append("\n");

            String arqc = EMVOperations.generateARQC(sk, txData, paddingMethod);
            lastArqcTransactionData = txData;
            lastArqcPaddingMethod = paddingMethod;
            lastArqcValue = arqc;
            result.append("➜ ARQC: ").append(arqc).append("\n\n");

            result.append("✅ ARQC generated successfully\n");

            arqcResultArea.setText(result.toString());
            arqcResultArea.setVisible(true);
            arqcResultArea.setManaged(true);

            java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
            details.put("Padding", "Method " + paddingMethod);
            details.put("Transaction Data", txData.substring(0, Math.min(20, txData.length())) + "...");
            details.put("Session Key", "[not persisted]");
            mainController.publish(OperationResult.forOperation("ARQC Generation")
                    .output(com.cryptoforge.util.DataConverter.hexToBytes(arqc)).details(details)
                    .status("ARQC generated successfully").build());

        } catch (Exception e) {
            arqcResultArea.setText("Error: " + e.getMessage());
            arqcResultArea.setVisible(true);
            arqcResultArea.setManaged(true);
        }
    }

    public void handleVerifyARQC() {
        try {
            String sk = skARQCField.getText().trim().replaceAll("\\s+", "");
            String amount = amountField.getText().trim().replaceAll("\\s+", "");
            String arqcToVerify = arqcResultArea.getText();

            // Extract ARQC from result area if it contains the full output
            if (arqcToVerify.contains("ARQC: ")) {
                int start = arqcToVerify.indexOf("ARQC: ") + 6;
                int end = arqcToVerify.indexOf("\n", start);
                if (end == -1)
                    end = arqcToVerify.length();
                arqcToVerify = arqcToVerify.substring(start, end).trim();
            }

            if (sk.isEmpty() || arqcToVerify.isEmpty() || arqcToVerify.length() != 16) {
                arqcResultArea.setText("Error: Please generate an ARQC first to verify");
                return;
            }

            String txData;
            int paddingMethod;
            if (lastArqcTransactionData != null && arqcToVerify.equalsIgnoreCase(lastArqcValue)) {
                txData = lastArqcTransactionData;
                paddingMethod = lastArqcPaddingMethod;
            } else {
                if (amount.isEmpty()) throw new IllegalArgumentException("Amount is required to reconstruct ARQC data");
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

        } catch (Exception e) {
            arqcResultArea.setText("Error during verification: " + e.getMessage());
        }
    }

    // ============================================================================
    // ARPC GENERATION
    // ============================================================================

    public void handleGenerateARPC() {
        try {
            String sk = skARPCField.getText().trim().replaceAll("\\s+", "");
            String arqc = arqcField.getText().trim().replaceAll("\\s+", "");
            String arc = arcField.getText().trim().replaceAll("\\s+", "");
            String csu = csuField.getText().trim().replaceAll("\\s+", "");

            if (sk.isEmpty() || arqc.isEmpty() || arc.isEmpty()) {
                arpcResultArea.setText("Error: Session Key, ARQC, and ARC are required");
                return;
            }

            StringBuilder result = new StringBuilder();
            result.append("ARPC GENERATION (Authorization Response Cryptogram)\n");
            result.append("═══════════════════════════════════════════════════\n\n");

            String selectedMethod = arpcMethodCombo.getSelectionModel().getSelectedItem();
            String arpc;

            if (selectedMethod.contains("Method 1")) {
                result.append("Method: Method 1 (ARPC = Encrypt(ARQC ⊕ ARC))\n");
                result.append("──────────────────────────────────────────────\n");
                result.append("Session Key: ").append(sk).append("\n");
                result.append("ARQC: ").append(arqc).append("\n");
                result.append("ARC: ").append(arc).append("\n\n");

                arpc = EMVOperations.generateARPC_Method1(sk, arqc, arc);

            } else {
                result.append("Method: Method 2 (CSU Method)\n");
                result.append("─────────────────────────────\n");
                result.append("Session Key: ").append(sk).append("\n");
                result.append("ARC: ").append(arc).append("\n");
                result.append("CSU: ").append(csu.isEmpty() ? "00000000" : csu).append("\n\n");

                arpc = EMVOperations.generateARPC_Method2(sk, arc, csu.isEmpty() ? "00000000" : csu);
            }

            result.append("➜ ARPC: ").append(arpc).append("\n\n");
            result.append("✅ ARPC generated successfully\n");
            result.append("\nℹ️  Send this ARPC to the card in the authorization response (Tag 91)\n");

            arpcResultArea.setText(result.toString());
            arpcResultArea.setVisible(true);
            arpcResultArea.setManaged(true);

            java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
            details.put("Method", arpcMethodCombo == null ? "Default" : arpcMethodCombo.getValue());
            details.put("ARC", arc);
            details.put("Session Key", "[not persisted]");
            mainController.publish(OperationResult.forOperation("ARPC Generation")
                    .output(com.cryptoforge.util.DataConverter.hexToBytes(arpc)).details(details)
                    .status("ARPC generated successfully").build());

        } catch (Exception e) {
            arpcResultArea.setText("Error: " + e.getMessage());
            arpcResultArea.setVisible(true);
            arpcResultArea.setManaged(true);
        }
    }

    private String maskPan(String pan) {
        if (pan == null || pan.length() < 5) return "[redacted]";
        return "*".repeat(Math.max(0, pan.length() - 4)) + pan.substring(pan.length() - 4);
    }

    // ============================================================================
    // TRACK 2 OPERATIONS
    // ============================================================================

    public void handleEncodeTrack2() {
        try {
            String pan = panTrack2Field.getText().trim().replaceAll("\\s+", "");
            String expiry = expiryTrack2Field.getText().trim();
            String serviceCode = serviceCodeFieldTrack2.getText().trim();
            String discretionaryData = discretionaryDataField.getText().trim();

            if (pan.isEmpty() || expiry.isEmpty() || serviceCode.isEmpty()) {
                track2ResultArea.setText("Error: PAN, Expiry Date, and Service Code are required");
                return;
            }

            StringBuilder result = new StringBuilder();
            result.append("TRACK 2 ENCODING\n");
            result.append("════════════════\n\n");

            String track2 = EMVOperations.encodeTrack2(pan, expiry, serviceCode, discretionaryData);

            result.append("Input Data:\n");
            result.append("───────────\n");
            result.append("PAN: ").append(pan).append("\n");
            result.append("Expiry: ").append(expiry).append(" (YYMM)\n");
            result.append("Service Code: ").append(serviceCode).append("\n");
            if (!discretionaryData.isEmpty()) {
                result.append("Discretionary Data: ").append(discretionaryData).append("\n");
            }
            result.append("\n");

            result.append("Track 2 Equivalent Data:\n");
            result.append("────────────────────────\n");
            result.append(track2).append("\n\n");

            result.append("Format: PAN + 'D' + Expiry + Service Code + Discretionary Data\n");
            result.append("✅ Track 2 encoded successfully\n");

            track2ResultArea.setText(result.toString());
            track2ResultArea.setVisible(true);
            track2ResultArea.setManaged(true);

            java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
            details.put("PAN", maskPan(pan));
            details.put("Expiry", expiry);
            details.put("Service Code", serviceCode);
            mainController.publish(OperationResult.forOperation("Track 2 Encoding")
                    .output(track2.getBytes(java.nio.charset.StandardCharsets.US_ASCII)).details(details)
                    .status("Track 2 encoded successfully").build());

        } catch (Exception e) {
            track2ResultArea.setText("Error: " + e.getMessage());
        }
    }

    public void handleDecodeTrack2() {
        try {
            String track2Input = track2InputField.getText().trim().replaceAll("\\s+", "");

            if (track2Input.isEmpty()) {
                track2ResultArea.setText("Error: Track 2 data is required");
                return;
            }

            String result = EMVOperations.decodeTrack2(track2Input);
            track2ResultArea.setText(result);
            track2ResultArea.setVisible(true);
            track2ResultArea.setManaged(true);

            mainController.publish(OperationResult.forOperation("Track 2 Decoding")
                    .input(track2Input.getBytes(java.nio.charset.StandardCharsets.US_ASCII))
                    .output(result.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .detail("PAN", "[contained in Track 2; not persisted]")
                    .status("Track 2 decoded successfully").build());

        } catch (Exception e) {
            track2ResultArea.setText("Error: " + e.getMessage());
        }
    }

    // --- Helper Methods for Global Toolbar ---

    public void handleClear() {
        // Clear Result Areas
        if (sessionKeyResultArea != null)
            sessionKeyResultArea.clear();
        if (arqcResultArea != null)
            arqcResultArea.clear();
        if (arpcResultArea != null)
            arpcResultArea.clear();
        if (track2ResultArea != null)
            track2ResultArea.clear();

        // Clear Inputs (Session Key)
        if (imkField != null)
            imkField.clear();
        if (panFieldSession != null)
            panFieldSession.clear();
        if (panSeqFieldSession != null)
            panSeqFieldSession.clear();
        if (atcField != null)
            atcField.clear();

        // Clear Inputs (ARQC)
        if (skARQCField != null)
            skARQCField.clear();
        if (amountField != null)
            amountField.clear();
        if (amountOtherField != null)
            amountOtherField.clear();
        if (atcARQCField != null)
            atcARQCField.clear();
        if (unField != null)
            unField.clear();
        if (arqcTerminalDataField != null)
            arqcTerminalDataField.clear();
        if (iccDataField != null)
            iccDataField.clear();

        // Clear Inputs (ARPC)
        if (skARPCField != null)
            skARPCField.clear();
        if (arqcField != null)
            arqcField.clear();
        if (arcField != null)
            arcField.clear();
        if (csuField != null)
            csuField.clear();
        if (propAuthDataField != null)
            propAuthDataField.clear();

        // Clear Inputs (Track2)
        if (panTrack2Field != null)
            panTrack2Field.clear();
        if (expiryTrack2Field != null)
            expiryTrack2Field.clear();
        if (serviceCodeFieldTrack2 != null)
            serviceCodeFieldTrack2.clear();
        if (discretionaryDataField != null)
            discretionaryDataField.clear();
        if (track2InputField != null)
            track2InputField.clear();
    }

    public String getOutputText() {
        if (arpcResultArea != null && !arpcResultArea.getText().isEmpty()) {
            return arpcResultArea.getText();
        }
        if (arqcResultArea != null && !arqcResultArea.getText().isEmpty()) {
            return arqcResultArea.getText();
        }
        if (track2ResultArea != null && !track2ResultArea.getText().isEmpty()) {
            return track2ResultArea.getText();
        }
        if (sessionKeyResultArea != null && !sessionKeyResultArea.getText().isEmpty()) {
            return sessionKeyResultArea.getText();
        }
        return "";
    }

    public String getArpcResultText() {
        return arpcResultArea != null ? arpcResultArea.getText() : "";
    }

    public void loadProfile(com.cryptoforge.model.payments.PaymentProfile p) {
        if (p.getType() == com.cryptoforge.model.payments.PaymentProfile.ProfileType.EMV) {
            String derivedSessionKey = deriveLaboratorySessionKey(p);
            if (p.getName().contains("ARQC")) {
                if (skARQCField != null) skARQCField.setText(derivedSessionKey);
                if (imkField != null && p.getInputs().containsKey("imk")) imkField.setText(p.getInputs().get("imk"));
                if (panFieldSession != null && p.getInputs().containsKey("pan")) panFieldSession.setText(p.getInputs().get("pan"));
                if (panSeqFieldSession != null && p.getInputs().containsKey("panSeq")) panSeqFieldSession.setText(p.getInputs().get("panSeq"));
                if (atcARQCField != null && p.getInputs().containsKey("atc")) atcARQCField.setText(p.getInputs().get("atc"));
                if (unField != null && p.getInputs().containsKey("unpredictableNumber")) unField.setText(p.getInputs().get("unpredictableNumber"));
                if (arqcTerminalDataField != null && p.getInputs().containsKey("transactionData")) arqcTerminalDataField.setText(p.getInputs().get("transactionData"));
                if (arqcPaddingMethodCombo != null && p.getParameters().containsKey("padding")) {
                    String padding = p.getParameters().get("padding");
                    for (String item : arqcPaddingMethodCombo.getItems()) {
                        if (item.contains(padding)) { arqcPaddingMethodCombo.setValue(item); break; }
                    }
                }
            } else if (p.getName().contains("ARPC")) {
                if (skARPCField != null) skARPCField.setText(derivedSessionKey);
                if (arqcField != null && p.getInputs().containsKey("arqc")) arqcField.setText(p.getInputs().get("arqc"));
                if (arcField != null && p.getInputs().containsKey("arc")) arcField.setText(p.getInputs().get("arc"));
                if (csuField != null && p.getInputs().containsKey("csu")) csuField.setText(p.getInputs().get("csu"));
                if (arpcMethodCombo != null) {
                    String method = p.getName().contains("Method 1") ? "Method 1"
                            : p.getParameters().getOrDefault("method", "");
                    for (String item : arpcMethodCombo.getItems()) {
                        if (item.contains(method)) { arpcMethodCombo.setValue(item); break; }
                    }
                }
            }
            System.out.println("Loaded EMV profile: " + p.getName());
        } else if (p.getType() == com.cryptoforge.model.payments.PaymentProfile.ProfileType.SECURE_MESSAGING) {
            // Secure Messaging uses MAC controls or specific SM UI if added.
            // Currently EMVController does not have Secure Messaging UI mapped, it relies on MAC in PaymentsController or a future SM tab.
            System.out.println("Loaded Secure Messaging profile: " + p.getName());
        }
    }

    /**
     * Laboratory EMV profiles carry the ICC master-key inputs, not a copied
     * session key.  Derive it before filling ARQC/ARPC so the loaded screen is
     * immediately executable.  Invalid laboratory vectors deliberately keep an
     * empty field and show their expected validation error when executed.
     */
    private String deriveLaboratorySessionKey(com.cryptoforge.model.payments.PaymentProfile profile) {
        if (profile.getInputs().containsKey("sessionKey")) {
            return profile.getInputs().get("sessionKey");
        }
        try {
            String imk = profile.getInput("imk");
            String pan = profile.getInput("pan");
            String panSeq = profile.getInput("panSeq");
            String atc = profile.getInput("atc");
            if (imk == null || pan == null || panSeq == null || atc == null) return "";
            String iccMasterKey = EMVOperations.deriveICCMasterKey(imk, pan, panSeq);
            return EMVOperations.deriveSessionKey(iccMasterKey, atc, "");
        } catch (Exception ignored) {
            return "";
        }
    }
}
