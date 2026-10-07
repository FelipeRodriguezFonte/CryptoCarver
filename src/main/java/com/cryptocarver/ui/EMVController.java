package com.cryptocarver.ui;

import com.cryptocarver.crypto.EmvTlv;

import com.cryptocarver.crypto.EMVOperations;
import com.cryptocarver.crypto.EmvOdaOperations;
import com.cryptocarver.crypto.VisaHceOperations;
import com.cryptocarver.crypto.MastercardDataStorage;
import com.cryptocarver.crypto.MastercardIccDynamicNumber;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;

import javafx.fxml.FXML;
import javafx.scene.control.*;

/**
 * Controller for EMV tab operations
 * Handles ARQC/ARPC, session key derivation, and EMV cryptography
 *
 * @author Felipe
 */
public class EMVController {
    @FXML private javafx.scene.layout.VBox emvContainer;
    private ModuleI18n.Binding moduleI18n;

    private String t(String key, Object... args) {
        return com.cryptocarver.service.I18nService.getInstance().text(key, args);
    }
    @FXML private TextArea emvTlvInputArea, emvTlvResultArea;
    @FXML private TextField emvDolTemplateField;
    @FXML private TextArea emvDolValuesArea, emvDolResultArea;

    public void initializeTlvInspector(TextArea input, TextArea output) { this.emvTlvInputArea = input; this.emvTlvResultArea = output; }
    public void initializeDolBuilder(TextField template, TextArea values, TextArea output) { this.emvDolTemplateField = template; this.emvDolValuesArea = values; this.emvDolResultArea = output; }

    public void handleBuildDol() {
        try {
            java.util.Map<String, String> values = new java.util.LinkedHashMap<>();
            for (String line : emvDolValuesArea.getText().split("\\R")) {
                if (line.isBlank()) continue;
                String[] pair = line.split("=", 2);
                if (pair.length != 2) throw new IllegalArgumentException(t("module.emv.feedback.dolFormat"));
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
            if (mainController != null) {
                mainController.publish(OperationResult.forOperation("EMV DOL Build")
                        .input(emvDolTemplateField.getText().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        .output(result.data().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        .detail("Fields", String.valueOf(result.fields().size()))
                        .detail("Warnings", String.valueOf(result.warnings().size()))
                        .status(t("module.emv.status.dol")).build());
            }
        } catch (Exception e) { emvDolResultArea.setText(t("module.emv.error.dol", e.getMessage())); }
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
            if (mainController != null) {
                mainController.publish(OperationResult.forOperation("EMV TLV Inspection")
                        .input(emvTlvInputArea.getText().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        .output(report.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        .detail("Top-level objects", String.valueOf(analysis.topLevelItems()))
                        .detail("Total objects", String.valueOf(analysis.totalItems()))
                        .status(t("module.emv.status.tlv")).build());
            }
        } catch (Exception e) { emvTlvResultArea.setText(t("module.emv.error.tlv", e.getMessage())); }
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

    private EmvSecureMessagingCoordinator emvSecureMessagingCoordinator;
    private EmvSecureMessagingCoordinator emvSecureMessagingCoordinator() {
        if (emvSecureMessagingCoordinator == null) {
            emvSecureMessagingCoordinator = new EmvSecureMessagingCoordinator(new EmvSecureMessagingCoordinator.View(
                    () -> smMkSmiField,
                    () -> smMkSmcField,
                    () -> smPanSeqField,
                    () -> smUdkSmiField,
                    () -> smUdkSmcField,
                    () -> smAcField,
                    () -> smCommandNumberField,
                    () -> smAtcField,
                    () -> smUdkAField,
                    () -> smHeaderField,
                    () -> smPinField,
                    () -> smSkMacField,
                    () -> smSkEncField,
                    () -> smDataField,
                    () -> smSchemeCombo,
                    () -> smResultArea), () -> mainController);
        }
        return emvSecureMessagingCoordinator;
    }

    private StatusReporter mainController;
    private EmvSessionKeyCoordinator emvSessionKeyCoordinator;

    // Session Key Derivation controls
    @FXML private TextField imkField;
    @FXML private TextField panFieldSession;
    @FXML private TextField panSeqFieldSession;
    @FXML private ComboBox<String> iccMethodCombo;
    @FXML private TextField emvAtcField;
    private TextField atcField;
    @FXML private TextArea sessionKeyResultArea;

    private EmvSessionKeyCoordinator emvSessionKeyCoordinator() {
        if (emvSessionKeyCoordinator == null) {
            emvSessionKeyCoordinator = new EmvSessionKeyCoordinator(new EmvSessionKeyCoordinator.View(
                    () -> imkField,
                    () -> panFieldSession,
                    () -> panSeqFieldSession,
                    () -> iccMethodCombo,
                    () -> atcField,
                    () -> sessionKeyResultArea,
                    () -> smSchemeCombo,
                    () -> smMkSmiField,
                    () -> smMkSmcField,
                    () -> smPanSeqField,
                    () -> smUdkSmiField,
                    () -> smUdkSmcField,
                    () -> smAcField,
                    () -> smCommandNumberField,
                    () -> smAtcField,
                    () -> smSkMacField,
                    () -> smSkEncField,
                    () -> smResultArea), () -> mainController);
        }
        return emvSessionKeyCoordinator;
    }

    // ARQC Generation controls
    @FXML private TextField skARQCField;
    @FXML private TextField amountField;
    @FXML private TextField currencyField;
    @FXML private TextField countryField;
    @FXML private TextField atcARQCField;
    @FXML private TextField tvrField;
    @FXML private TextField txDateField;
    @FXML private TextField txTypeField;
    @FXML private TextField unField;
    @FXML private TextArea arqcResultArea;
    private final EmvArqcCoordinator.State emvArqcState = new EmvArqcCoordinator.State();
    private EmvArqcCoordinator emvArqcCoordinator;

    private EmvArqcCoordinator emvArqcCoordinator() {
        if (emvArqcCoordinator == null) {
            emvArqcCoordinator = new EmvArqcCoordinator(new EmvArqcCoordinator.View(
                    () -> skARQCField,
                    () -> amountField,
                    () -> amountOtherField,
                    () -> currencyField,
                    () -> countryField,
                    () -> atcARQCField,
                    () -> tvrField,
                    () -> txDateField,
                    () -> txTypeField,
                    () -> unField,
                    () -> arqcTerminalDataField,
                    () -> iccDataField,
                    () -> arqcPaddingMethodCombo,
                    () -> arqcResultArea,
                    emvArqcState), () -> mainController);
        }
        return emvArqcCoordinator;
    }

    // ARPC Generation controls
    @FXML private TextField skARPCField;
    @FXML private TextField arqcField;
    @FXML private TextField arcField;
    @FXML private TextField csuField;
    @FXML private ComboBox<String> arpcMethodCombo;
    @FXML private TextArea arpcResultArea;

    private EmvArpcCoordinator emvArpcCoordinator;

    private EmvArpcCoordinator emvArpcCoordinator() {
        if (emvArpcCoordinator == null) {
            emvArpcCoordinator = new EmvArpcCoordinator(new EmvArpcCoordinator.View(
                    () -> skARPCField,
                    () -> arqcField,
                    () -> arcField,
                    () -> csuField,
                    () -> arpcMethodCombo,
                    () -> arpcResultArea), () -> mainController);
        }
        return emvArpcCoordinator;
    }

    private EmvModuleStateCoordinator emvModuleStateCoordinator;

    private EmvModuleStateCoordinator emvModuleStateCoordinator() {
        if (emvModuleStateCoordinator == null) {
            emvModuleStateCoordinator = new EmvModuleStateCoordinator(new EmvModuleStateCoordinator.View(
                    () -> emvContainer,
                    () -> sessionKeyResultArea,
                    () -> arqcResultArea,
                    () -> arpcResultArea,
                    () -> track2ResultArea,
                    () -> imkField,
                    () -> panFieldSession,
                    () -> panSeqFieldSession,
                    () -> atcField,
                    () -> skARQCField,
                    () -> amountField,
                    () -> amountOtherField,
                    () -> atcARQCField,
                    () -> unField,
                    () -> arqcTerminalDataField,
                    () -> iccDataField,
                    () -> skARPCField,
                    () -> arqcField,
                    () -> arcField,
                    () -> csuField,
                    () -> propAuthDataField,
                    () -> panTrack2Field,
                    () -> expiryTrack2Field,
                    () -> serviceCodeFieldTrack2,
                    () -> track2DiscretionaryInputField,
                    () -> track2InputField,
                    () -> emvTlvInputArea,
                    () -> emvTlvResultArea,
                    () -> emvDolTemplateField,
                    () -> emvDolValuesArea,
                    () -> emvDolResultArea,
                    () -> arqcPaddingMethodCombo,
                    () -> arpcMethodCombo,
                    () -> emvArqcState), () -> mainController);
        }
        return emvModuleStateCoordinator;
    }

    private EmvTrack2Coordinator emvTrack2Coordinator;

    private EmvTrack2Coordinator emvTrack2Coordinator() {
        if (emvTrack2Coordinator == null) {
            emvTrack2Coordinator = new EmvTrack2Coordinator(new EmvTrack2Coordinator.View(
                    () -> panTrack2Field,
                    () -> expiryTrack2Field,
                    () -> serviceCodeFieldTrack2,
                    () -> track2DiscretionaryInputField,
                    () -> track2InputField,
                    () -> track2ResultArea), () -> mainController);
        }
        return emvTrack2Coordinator;
    }

    // Track 2 controls
    @FXML private TextField panTrack2Field;
    @FXML private TextField expiryTrack2Field;
    @FXML private TextField serviceCodeFieldTrack2;
    @FXML private TextField track2DiscretionaryInputField;
    @FXML private TextField track2InputField;
    @FXML private TextArea track2ResultArea;

    // New fields
    @FXML private TextArea arqcTerminalDataField;
    @FXML private TextField amountOtherField;
    @FXML private TextField iccDataField;
    @FXML private ComboBox<String> arqcPaddingMethodCombo;
    @FXML private TextField propAuthDataField;

    // Issuer-script secure messaging
    @FXML private ComboBox<String> smSchemeCombo;
    @FXML private TextField smMkSmiField, smMkSmcField, smPanSeqField, smUdkSmiField, smUdkSmcField;
    @FXML private TextField smAcField, smCommandNumberField, smAtcField, smSkMacField, smSkEncField;
    @FXML private PasswordField smPinField;
    @FXML private TextField smUdkAField, smHeaderField, smDataField;
    @FXML private TextArea smResultArea;

    // Visa cloud payments and Mastercard Integrated Data Storage
    @FXML private TextField hceUdkField, hceYearField, hceHoursField, hceCounterField;
    @FXML private TextField hceMsdLukField, hceMsdAtcField, hceDeviceTypeField;
    @FXML private TextField hceQvsdcLukField, hceAmountField, hceOtherAmountField, hceCountryField;
    @FXML private TextField hceTvrField, hceCurrencyField, hceDateField, hceTypeField, hceUnField;
    @FXML private TextField hceAipField, hceQvsdcAtcField, hceCvrField;
    @FXML private TextArea hceResultArea;
    @FXML private TextField dsIdField, dsOperatorIdField, dsInputField;
    @FXML private TextField dsSummary1Field, dsAmountField, dsCurrencyField, dsRcpField, dsGacField, dsDsUnField, dsUnField;
    @FXML private TextArea dsResultArea;
    @FXML private TextField dnMkField, dnPanField, dnAtcField, dnUnField;
    @FXML private TextArea dnResultArea;
    @FXML private TextField capIpbField, capIafField, capPanSnField, capCidField, capAtcField, capAcField, capIadField;
    @FXML private TextArea capResultArea;

    static final String SM_MASTERCARD = "Mastercard";
    static final String SM_VISA = "Visa";

    @FXML
    private void initialize() {
        moduleI18n = ModuleI18n.bind(emvContainer, ModuleTextCatalog.emv());
        atcField = emvAtcField;
        setupARQCPaddingMethods();
        setupARPCMethods();
        setupIccMethods();
        if (smSchemeCombo != null) {
            smSchemeCombo.getItems().setAll(SM_MASTERCARD, SM_VISA);
            smSchemeCombo.getSelectionModel().selectFirst();
        }
    }

    public void init(StatusReporter reporter) {
        this.mainController = reporter;
    }

    private void setupIccMethods() {
        if (iccMethodCombo == null) return;
        iccMethodCombo.getItems().setAll(t("module.emv.iccMethod.auto"),
                t("module.emv.iccMethod.a"), t("module.emv.iccMethod.b"));
        iccMethodCombo.getSelectionModel().selectFirst();
    }

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
        this.track2DiscretionaryInputField = discretionaryDataField;
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

    public void handleDeriveSessionKey() { emvSessionKeyCoordinator().handleDeriveSessionKey(); }

    // ============================================================================
    // ISSUER-SCRIPT SECURE MESSAGING (all cryptography in EmvSecureMessaging)
    // ============================================================================



    private static String smText(TextInputControl field) {
        return field == null || field.getText() == null ? "" : field.getText().replaceAll("\\s+", "").toUpperCase(java.util.Locale.ROOT);
    }





    /** Fills every field with the worked example the external tool ships for the selected scheme. */
    public void handleSmLoadExample() { emvSecureMessagingCoordinator().handleSmLoadExample(); }

    public void handleSmDeriveSessionKeys() { emvSessionKeyCoordinator().handleSmDeriveSessionKeys(); }

    public void handleSmEncipherPin() { emvSecureMessagingCoordinator().handleSmEncipherPin(); }

    public void handleSmGenerateMac() { emvSecureMessagingCoordinator().handleSmGenerateMac(); }

    // The controller validates field shape and delegates all EMV calculations.
    private String emvHex(TextField field, String labelKey, int bytes) {
        String value = smText(field);
        if (!value.matches("[0-9A-F]{" + (bytes * 2) + "}"))
            throw new IllegalArgumentException(t("module.emv.hce.hexLength", t(labelKey), bytes));
        return value;
    }

    private String emvDsId() {
        String value = smText(dsIdField);
        if (!value.matches("[0-9A-F]{12,}") || value.length() % 2 != 0)
            throw new IllegalArgumentException(t("module.emv.ds.idInvalid"));
        return value;
    }

    private void emvShow(TextArea area, String text) {
        area.setText(text);
        area.setVisible(true);
        area.setManaged(true);
    }

    private void emvPublish(String operationKey, String statusKey, String value,
                            boolean secret, java.util.List<OperationDetail> details) {
        if (mainController == null) return;
        mainController.publish(OperationResult.forOperation(t(operationKey))
                .output(value.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        secret ? OperationDetail.Classification.SECRET : OperationDetail.Classification.PUBLIC)
                .details(details).status(t(statusKey)).build());
    }

    public void handleHceLoadExample() {
        hceUdkField.setText("94E3194C02105E3B153438D562D5A49D");
        hceYearField.setText("26"); hceHoursField.setText("6431"); hceCounterField.setText("01");
        hceMsdLukField.clear(); hceQvsdcLukField.clear();
        hceMsdAtcField.setText("0001"); hceDeviceTypeField.setText("AAAA000000000001");
        hceAmountField.setText("000000001000"); hceOtherAmountField.setText("000000000000");
        hceCountryField.setText("0710"); hceTvrField.setText("0000000000");
        hceCurrencyField.setText("0710"); hceDateField.setText("130205");
        hceTypeField.setText("00"); hceUnField.setText("30901B6A");
        hceAipField.setText("3C00"); hceQvsdcAtcField.setText("0055");
        hceCvrField.setText("03A4A082");
        emvShow(hceResultArea, t("module.emv.hce.exampleLoaded"));
    }

    public void handleHceLuk() {
        try {
            String udk = emvHex(hceUdkField, "module.emv.hce.udk", 16);
            String year = smText(hceYearField), hours = smText(hceHoursField), counter = smText(hceCounterField);
            if (!year.matches("\\d{1,2}")) throw new IllegalArgumentException(t("module.emv.hce.yearInvalid"));
            if (!hours.matches("\\d{4}")) throw new IllegalArgumentException(t("module.emv.hce.hoursInvalid"));
            if (!counter.matches("\\d{2}")) throw new IllegalArgumentException(t("module.emv.hce.counterInvalid"));
            String luk = VisaHceOperations.limitedUseKey(udk, year, hours, counter);
            hceMsdLukField.setText(luk); hceQvsdcLukField.setText(luk);
            emvShow(hceResultArea, t("module.emv.hce.lukResult", luk));
            emvPublish("module.emv.hce.lukAction", "module.emv.hce.status", luk, true,
                    java.util.List.of(OperationDetail.secretDetail("UDK", udk), OperationDetail.secretDetail("LUK", luk)));
        } catch (Exception e) { emvShow(hceResultArea, t("module.emv.hce.error", e.getMessage())); }
    }

    public void handleHceMsd() {
        try {
            String luk = emvHex(hceMsdLukField, "module.emv.hce.luk", 16);
            String atc = emvHex(hceMsdAtcField, "module.emv.hce.atc", 2);
            String device = emvHex(hceDeviceTypeField, "module.emv.hce.deviceType", 8);
            String value = VisaHceOperations.msdVerificationValue(luk, atc, device);
            emvShow(hceResultArea, t("module.emv.hce.msdResult", value));
            emvPublish("module.emv.hce.msdAction", "module.emv.hce.status", value, false,
                    java.util.List.of(OperationDetail.secretDetail("LUK", luk), OperationDetail.publicDetail("MSD", value)));
        } catch (Exception e) { emvShow(hceResultArea, t("module.emv.hce.error", e.getMessage())); }
    }

    public void handleHceQvsdc() {
        try {
            String luk = emvHex(hceQvsdcLukField, "module.emv.hce.luk", 16);
            String terminal = emvHex(hceAmountField, "module.emv.hce.amount", 6)
                    + emvHex(hceOtherAmountField, "module.emv.hce.otherAmount", 6)
                    + emvHex(hceCountryField, "module.emv.hce.country", 2)
                    + emvHex(hceTvrField, "module.emv.hce.tvr", 5)
                    + emvHex(hceCurrencyField, "module.emv.hce.currency", 2)
                    + emvHex(hceDateField, "module.emv.hce.date", 3)
                    + emvHex(hceTypeField, "module.emv.hce.type", 1)
                    + emvHex(hceUnField, "module.emv.hce.un", 4);
            String chip = emvHex(hceAipField, "module.emv.hce.aip", 2)
                    + emvHex(hceQvsdcAtcField, "module.emv.hce.atc", 2)
                    + emvHex(hceCvrField, "module.emv.hce.cvr", 4);
            String value = VisaHceOperations.qvsdcCryptogram(luk, terminal, chip);
            emvShow(hceResultArea, t("module.emv.hce.qvsdcResult", value));
            emvPublish("module.emv.hce.qvsdcAction", "module.emv.hce.status", value, false,
                    java.util.List.of(OperationDetail.secretDetail("LUK", luk), OperationDetail.publicDetail("qVSDC", value)));
        } catch (Exception e) { emvShow(hceResultArea, t("module.emv.hce.error", e.getMessage())); }
    }

    public void handleDsLoadExample() {
        dsIdField.setText("5168624300900697"); dsOperatorIdField.setText("8199829983998499");
        dsInputField.setText("1223344556677889");
        if (dsSummary1Field != null) {
            dsSummary1Field.setText("1223344556677889"); dsAmountField.setText("000000001234");
            dsCurrencyField.setText("840"); dsRcpField.setText("80"); dsGacField.setText("01");
            dsDsUnField.setText("11223344"); dsUnField.setText("11223344");
        }
        emvShow(dsResultArea, t("module.emv.ds.exampleLoaded"));
    }

    public void handleDsDspk() {
        try {
            String value = MastercardDataStorage.partialKey(emvDsId());
            emvShow(dsResultArea, t("module.emv.ds.dspkResult", value));
            emvPublish("module.emv.ds.dspkAction", "module.emv.ds.status", value, true,
                    java.util.List.of(OperationDetail.secretDetail("DSPK", value)));
        } catch (Exception e) { emvShow(dsResultArea, t("module.emv.ds.error", e.getMessage())); }
    }

    public void handleDsDigest() {
        try {
            String id = emvDsId();
            String oid = emvHex(dsOperatorIdField, "module.emv.ds.operatorId", 8);
            String input = emvHex(dsInputField, "module.emv.ds.input", 8);
            String value = MastercardDataStorage.owhf2(id, oid, input);
            emvShow(dsResultArea, t("module.emv.ds.digestResult", value));
            emvPublish("module.emv.ds.digestAction", "module.emv.ds.status", value, false,
                    java.util.List.of(OperationDetail.publicDetail("Digest", value)));
        } catch (Exception e) { emvShow(dsResultArea, t("module.emv.ds.error", e.getMessage())); }
    }

    public void handleDsSummary() {
        try {
            String value = MastercardDataStorage.summary(emvDsId(),
                    emvHex(dsSummary1Field, "module.emv.ds.summary1", 8),
                    smText(dsAmountField), smText(dsCurrencyField),
                    emvHex(dsRcpField, "module.emv.ds.rcp", 1), emvHex(dsGacField, "module.emv.ds.gac", 1),
                    emvHex(dsDsUnField, "module.emv.ds.dsUn", 4), emvHex(dsUnField, "module.emv.ds.un", 4));
            emvShow(dsResultArea, t("module.emv.ds.summaryResult", value));
            emvPublish("module.emv.ds.summaryAction", "module.emv.ds.status", value, false,
                    java.util.List.of(OperationDetail.publicDetail("DS Summary", value)));
        } catch (Exception e) { emvShow(dsResultArea, t("module.emv.ds.error", e.getMessage())); }
    }

    public void handleIccDnLoadExample() {
        dnMkField.setText("0123456789ABCDEFFEDCBA9876543210"); dnPanField.setText("4111111111111111");
        dnAtcField.setText("0001"); dnUnField.setText("00000000");
        emvShow(dnResultArea, t("module.emv.iccDn.exampleLoaded"));
    }

    public void handleIccDnCalculate() {
        try {
            String sk = MastercardIccDynamicNumber.sessionKey(dnMkField.getText(), dnPanField.getText());
            String dn = MastercardIccDynamicNumber.dynamicNumber(sk, dnAtcField.getText(), dnUnField.getText());
            emvShow(dnResultArea, t("module.emv.iccDn.result", dn));
            emvPublish("module.emv.iccDn.action", "module.emv.iccDn.status", dn, false,
                    java.util.List.of(OperationDetail.publicDetail("Dynamic number", dn)));
        } catch (Exception e) { emvShow(dnResultArea, t("module.emv.iccDn.error", e.getMessage())); }
    }

    public void handleCapLoadExample() {
        capIpbField.setText("00007FFFFF00000000000000000000208000"); capIafField.setText("40");
        capPanSnField.setText("00"); capCidField.setText("80"); capAtcField.setText("0001");
        capAcField.setText("5AC19AC9FE1360F3"); capIadField.setText("06010A03A41000");
        emvShow(capResultArea, t("module.emv.cap.exampleLoaded"));
    }

    public void handleCapCalculate() {
        try {
            MastercardIccDynamicNumber.CapResult cap = MastercardIccDynamicNumber.capToken(capIpbField.getText(),
                    capIafField.getText(), capPanSnField.getText(), capCidField.getText(), capAtcField.getText(),
                    capAcField.getText(), capIadField.getText());
            String report = t("module.emv.cap.result", cap.tokenData(), cap.paddedIpb(), cap.compressedBits(), cap.token());
            emvShow(capResultArea, report);
            emvPublish("module.emv.cap.action", "module.emv.cap.status", cap.token(), false,
                    java.util.List.of(OperationDetail.publicDetail("Token data", cap.tokenData()),
                            OperationDetail.publicDetail("IPB data", cap.paddedIpb()),
                            OperationDetail.publicDetail("Compressed data", cap.compressedBits()),
                            OperationDetail.publicDetail("Token", cap.token())));
        } catch (Exception e) { emvShow(capResultArea, t("module.emv.cap.error", e.getMessage())); }
    }

    // ============================================================================
    // ARQC GENERATION
    // ============================================================================

    // ============================================================================
    // ARQC GENERATION
    // ============================================================================

    public void handleGenerateARQC() { emvArqcCoordinator().handleGenerateARQC(); }

    public void handleVerifyARQC() { emvArqcCoordinator().handleVerifyARQC(); }

    // ============================================================================
    // ARPC GENERATION
    // ============================================================================

    public void handleGenerateARPC() { emvArpcCoordinator().handleGenerateARPC(); }

    // ============================================================================
    // TRACK 2 OPERATIONS
    // ============================================================================

    public void handleEncodeTrack2() { emvTrack2Coordinator().handleEncodeTrack2(); }

    public void handleDecodeTrack2() { emvTrack2Coordinator().handleDecodeTrack2(); }

    // --- Helper Methods for Global Toolbar ---

    @FXML
    public void handleClear() {
        if (emvContainer != null) {
            ModuleResetPolicy.apply(emvContainer, ModuleResetPolicy.Action.CLEAR,
                    this::clearModuleData, null);
        } else {
            clearModuleData();
        }
        if (mainController != null) mainController.updateStatus(t("module.emv.clearStatus"));
    }

    private void clearModuleData() { emvModuleStateCoordinator().clearModuleData(); }

    @FXML
    public void handleReset() {
        if (emvContainer != null) {
            ModuleResetPolicy.apply(emvContainer, ModuleResetPolicy.Action.RESET_DEFAULTS,
                    null, this::restoreSafeDefaults);
        } else {
            restoreSafeDefaults();
        }
        if (mainController != null) mainController.updateStatus(t("module.emv.resetStatus"));
    }

    /** Public reset entry point used by the module toolbar and headless UI tests. */
    public void resetModule() {
        handleReset();
    }

    private void restoreSafeDefaults() {
        if (arqcPaddingMethodCombo != null) arqcPaddingMethodCombo.getSelectionModel().selectFirst();
        if (arpcMethodCombo != null) arpcMethodCombo.getSelectionModel().selectFirst();
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

    public void loadProfile(com.cryptocarver.model.payments.PaymentProfile p) { emvModuleStateCoordinator().loadProfile(p); }

    // =====================================================================
    // Offline Data Authentication — EMV Book 2 clauses 5 and 6
    // =====================================================================

    @FXML private TextArea odaCaModulusArea;
    @FXML private TextField odaCaExponentField;
    @FXML private TextArea odaIssuerCertificateArea;
    @FXML private TextField odaIssuerRemainderField;
    @FXML private TextField odaIssuerExponentField;
    @FXML private TextArea odaIccCertificateArea;
    @FXML private TextField odaIccRemainderField;
    @FXML private TextField odaIccExponentField;
    @FXML private TextArea odaStaticDataArea;
    @FXML private TextField odaPanField;
    @FXML private TextArea odaSsadArea;
    @FXML private TextArea odaSdadArea;
    @FXML private TextField odaTerminalDataField;
    @FXML private TextField odaCidField;
    @FXML private TextArea odaTransactionDataArea;
    @FXML private TextArea odaResultArea;

    /** The test card's Unpredictable Number, so DDA and CDA stay reproducible. */
    private static final String TEST_UNPREDICTABLE_NUMBER = "01020304";
    private static final String TEST_PAN = "4761739001010119";

    @FXML
    public void handleOdaRecoverKeys() {
        runOda(() -> {
            StringBuilder report = new StringBuilder();
            EmvOdaOperations.IssuerCertificate issuer = recoverIssuer();
            report.append(EmvOdaOperations.describe(issuer));
            if (!text(odaIccCertificateArea).isEmpty()) {
                report.append('\n').append(EmvOdaOperations.describe(recoverIcc(issuer)));
            }
            return report.toString();
        }, "EMV ODA Key Recovery");
    }

    @FXML
    public void handleOdaVerifySda() {
        runOda(() -> {
            EmvOdaOperations.IssuerCertificate issuer = recoverIssuer();
            if (!issuer.passed()) {
                return EmvOdaOperations.describe(issuer)
                        + "\nThe issuer key did not come back clean, so the SSAD below was not checked.\n";
            }
            return EmvOdaOperations.describe(EmvOdaOperations.verifyStaticApplicationData(
                    text(odaSsadArea), issuer.issuerPublicKey(), text(odaStaticDataArea)));
        }, "EMV SDA");
    }

    @FXML
    public void handleOdaVerifyDda() {
        runOda(() -> {
            EmvOdaOperations.IccCertificate icc = recoverIcc(recoverIssuer());
            if (!icc.passed()) {
                return EmvOdaOperations.describe(icc)
                        + "\nThe ICC key did not come back clean, so the dynamic signature was not checked.\n";
            }
            return EmvOdaOperations.describe(EmvOdaOperations.verifyDynamicApplicationData(
                    text(odaSdadArea), icc.iccPublicKey(), text(odaTerminalDataField)));
        }, "EMV DDA");
    }

    @FXML
    public void handleOdaVerifyCda() {
        runOda(() -> {
            EmvOdaOperations.IccCertificate icc = recoverIcc(recoverIssuer());
            if (!icc.passed()) {
                return EmvOdaOperations.describe(icc)
                        + "\nThe ICC key did not come back clean, so the combined signature was not checked.\n";
            }
            return EmvOdaOperations.describe(EmvOdaOperations.verifyCombinedApplicationData(
                    text(odaSdadArea), icc.iccPublicKey(), text(odaTerminalDataField),
                    text(odaCidField), text(odaTransactionDataArea)));
        }, "EMV CDA");
    }

    /**
     * Personalises a throwaway card and fills every field with it, so the pane
     * is usable without a reader. The keys exist for the length of this click.
     */
    @FXML
    public void handleOdaIssueTestCard() {
        runOda(() -> {
            java.security.KeyPair ca = odaKeyPair(1024);
            java.security.KeyPair issuer = odaKeyPair(768);
            java.security.KeyPair icc = odaKeyPair(512);

            String staticData = "70115A0844AAAAAAAAAAAAAA5F3401009F0702FF00";
            String expiry = java.time.YearMonth.now().plusYears(3).format(
                    java.time.format.DateTimeFormatter.ofPattern("MMyy"));

            EmvOdaOperations.IssuedCertificate issuerCertificate = EmvOdaOperations.signIssuerCertificate(
                    EmvOdaOperations.RsaPrivateKey.of((java.security.interfaces.RSAPrivateKey) ca.getPrivate()),
                    EmvOdaOperations.RsaPublicKey.of((java.security.interfaces.RSAPublicKey) issuer.getPublic()),
                    TEST_PAN.substring(0, 8), expiry, "000001");
            EmvOdaOperations.IssuedCertificate iccCertificate = EmvOdaOperations.signIccCertificate(
                    EmvOdaOperations.RsaPrivateKey.of((java.security.interfaces.RSAPrivateKey) issuer.getPrivate()),
                    EmvOdaOperations.RsaPublicKey.of((java.security.interfaces.RSAPublicKey) icc.getPublic()),
                    TEST_PAN, expiry, "000002", staticData);

            String ssad = EmvOdaOperations.signStaticApplicationData(
                    EmvOdaOperations.RsaPrivateKey.of((java.security.interfaces.RSAPrivateKey) issuer.getPrivate()),
                    "1234", staticData);

            String transactionData = "000000010000000000000000097801020304";
            String dynamicData = EmvOdaOperations.combinedDynamicData("1122334455667788", "80",
                    "A1B2C3D4E5F60718", EmvOdaOperations.transactionDataHashCode(transactionData));
            String sdad = EmvOdaOperations.signDynamicApplicationData(
                    EmvOdaOperations.RsaPrivateKey.of((java.security.interfaces.RSAPrivateKey) icc.getPrivate()),
                    dynamicData, TEST_UNPREDICTABLE_NUMBER);

            set(odaCaModulusArea, EmvOdaOperations.RsaPublicKey.of(
                    (java.security.interfaces.RSAPublicKey) ca.getPublic()).modulusHex());
            set(odaCaExponentField, EmvOdaOperations.RsaPublicKey.of(
                    (java.security.interfaces.RSAPublicKey) ca.getPublic()).exponentHex());
            set(odaIssuerCertificateArea, issuerCertificate.certificate());
            set(odaIssuerRemainderField, issuerCertificate.remainder());
            set(odaIssuerExponentField, issuerCertificate.exponent());
            set(odaIccCertificateArea, iccCertificate.certificate());
            set(odaIccRemainderField, iccCertificate.remainder());
            set(odaIccExponentField, iccCertificate.exponent());
            set(odaStaticDataArea, staticData);
            set(odaPanField, TEST_PAN);
            set(odaSsadArea, ssad);
            set(odaSdadArea, sdad);
            set(odaTerminalDataField, TEST_UNPREDICTABLE_NUMBER);
            set(odaCidField, "80");
            set(odaTransactionDataArea, transactionData);

            return t("module.emv.oda.testCardIssued");
        }, "EMV ODA Test Card");
    }

    @FXML
    public void handleOdaClear() {
        for (TextInputControl field : new TextInputControl[] {
                odaCaModulusArea, odaCaExponentField, odaIssuerCertificateArea, odaIssuerRemainderField,
                odaIssuerExponentField, odaIccCertificateArea, odaIccRemainderField, odaIccExponentField,
                odaStaticDataArea, odaPanField, odaSsadArea, odaSdadArea, odaTerminalDataField,
                odaCidField, odaTransactionDataArea, odaResultArea}) {
            if (field != null) field.clear();
        }
    }

    private EmvOdaOperations.IssuerCertificate recoverIssuer() {
        return EmvOdaOperations.recoverIssuerPublicKey(
                text(odaIssuerCertificateArea), text(odaIssuerRemainderField), text(odaIssuerExponentField),
                EmvOdaOperations.RsaPublicKey.of(text(odaCaModulusArea), text(odaCaExponentField)),
                text(odaPanField));
    }

    private EmvOdaOperations.IccCertificate recoverIcc(EmvOdaOperations.IssuerCertificate issuer) {
        if (issuer.issuerPublicKey() == null) {
            throw new IllegalArgumentException(t("module.emv.oda.noIssuerKey"));
        }
        return EmvOdaOperations.recoverIccPublicKey(
                text(odaIccCertificateArea), text(odaIccRemainderField), text(odaIccExponentField),
                issuer.issuerPublicKey(), text(odaStaticDataArea), text(odaPanField));
    }

    /** EMV allows exponent 3 and 65537 only; a bench card uses 3, as most do. */
    private static java.security.KeyPair odaKeyPair(int bits) throws Exception {
        java.security.KeyPairGenerator generator = java.security.KeyPairGenerator.getInstance("RSA");
        generator.initialize(new java.security.spec.RSAKeyGenParameterSpec(bits, java.math.BigInteger.valueOf(3)));
        return generator.generateKeyPair();
    }

    private interface OdaStep {
        String run() throws Exception;
    }

    private void runOda(OdaStep step, String operation) {
        try {
            String report = step.run();
            if (odaResultArea != null) odaResultArea.setText(report);
            if (mainController != null) {
                mainController.publish(OperationResult.forOperation(operation)
                        .output(report.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        .status(t("module.emv.oda.status"))
                        .build());
            }
        } catch (Exception e) {
            if (odaResultArea != null) {
                odaResultArea.setText(t("module.emv.oda.error", String.valueOf(e.getMessage())));
            }
        }
    }

    private static String text(TextInputControl field) {
        return field == null || field.getText() == null ? "" : field.getText().trim();
    }

    private static void set(TextInputControl field, String value) {
        if (field != null) field.setText(value);
    }
}
