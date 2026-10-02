package com.cryptocarver.ui;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;


/**
 * Controller for Payments tab
 */
public class PaymentsController {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentsController.class);

    private StatusReporter mainController;
    @FXML private VBox paymentsContainer;
    private ModuleI18n.Binding moduleI18n;

    private String t(String key, Object... args) {
        return com.cryptocarver.service.I18nService.getInstance().text(key, args);
    }

    // Route module status and errors through the active shell reporter.
    private void updateStatus(String message) {
        if (mainController != null) mainController.updateStatus(message);
    }

    private void showError(String title, String message) {
        showError(title, message, null);
    }

    private void showError(String title, String message, String fieldKey) {
        if (mainController != null) {
            String safeMessage = InlineErrorPresenter.redactSecrets(message);
            mainController.showError(new UserFacingError(title, safeMessage, safeMessage, fieldKey));
        }
    }

    // PIN Block controls
    @FXML private TextField pinField;
    @FXML private TextField panFieldEncode;
    @FXML private TextField pinBlockField;
    @FXML private TextField panFieldDecode;
    @FXML private ComboBox<String> pinBlockFormatCombo;
    @FXML private ComboBox<String> pinBlockFormatDecodeCombo;
    @FXML private ComboBox<String> pinBlockPaddingCombo;
    @FXML private javafx.scene.control.Label pinBlockPaddingLabel;
    @FXML private TextArea pinBlockResultArea;
    @FXML private ResultPanel paymentsResultPanel;

    // payShield host command bank: local framing and capture analysis only.
    @FXML private TextField hsmHostHeaderField;
    @FXML private TextField hsmHostCommandCodeField;
    @FXML private TextField hsmHostBodyField;
    @FXML private TextField hsmHostTrailerField;
    @FXML private TextField hsmHostHeaderLengthField;
    @FXML private CheckBox hsmHostTcpPrefixCheck;
    @FXML private TextArea hsmHostCapturedFrameArea;
    @FXML private TextArea hsmHostResultArea;

    // ISO 8583 message workbench. The UI delegates parsing, field definitions,
    // bitmap handling and enrichment to the typed core codec.
    @FXML private ComboBox<String> iso8583ProfileCombo;
    @FXML private ComboBox<String> iso8583BitmapEncodingCombo;
    @FXML private ComboBox<String> iso8583LengthEncodingCombo;
    @FXML private TextArea iso8583MessageArea;
    @FXML private TextArea iso8583ReportArea;

    // CVV controls
    @FXML private TextField cvkAField;
    @FXML private TextField cvkBField;
    @FXML private TextField panFieldCvv;
    @FXML private TextField expiryDateField;
    @FXML private TextField serviceCodeField;
    @FXML private TextField atcField;
    @FXML private ComboBox<String> cvvTypeCombo;
    @FXML private TextArea cvvResultArea;

    // Additional PIN fields for Encrypted PIN Blocks (Generic)
    @FXML private ComboBox<String> encPinBlockFormatCombo;
    @FXML private TextField encPinField;
    @FXML private TextField encPanFieldEncode;
    @FXML private TextField encPinBlockKeyField;
    @FXML private TextField encPinBlockFieldDecode;
    @FXML private TextField encPanFieldDecode;
    @FXML private TextField encPinBlockKeyFieldDecode;
    @FXML private TextArea encResultArea;

    @FXML private TextField ibm3624PvkField;
    @FXML private TextField ibm3624ConvTableField;
    @FXML private TextField ibm3624OffsetField;
    @FXML private TextField ibm3624PanField;
    @FXML private TextField ibm3624PinVerifyField;
    @FXML private TextArea ibm3624ResultArea;
    @FXML private TextField ibm3624StartField;
    @FXML private TextField ibm3624LengthField;
    @FXML private TextField ibm3624PadField;

    // PIN Generators (Offset & PVV)
    @FXML private TextField genOffsetPvkField;
    @FXML private TextField genOffsetDecTableField;
    @FXML private TextField genOffsetPanField;
    @FXML private TextField genOffsetPinField;
    @FXML private TextArea genOffsetResultArea;
    @FXML private TextField genOffsetStartField;
    @FXML private TextField genOffsetLengthField;
    @FXML private TextField genOffsetPadField;

    @FXML private TextField genPvvPvkField;
    @FXML private TextField genPvvPanField;
    @FXML private TextField genPvvPinField;
    @FXML private TextField genPvvKeyIndexField;
    @FXML private TextArea genPvvResultArea;

    // Derive PIN from PVV (VISA)
    @FXML private TextField derivePvvPvkField;
    @FXML private TextField derivePvvPanField;
    @FXML private TextField derivePvvTargetPvvField;
    @FXML private TextField derivePvvKeyIndexField;
    @FXML private TextArea derivePvvResultArea;
    @FXML private TextField dukptBdkField, dukptKsnField, dukptAesPinBlockField;
    @FXML private TextArea dukptResultArea;
    @FXML private ComboBox<String> dukptSchemeCombo, dukptTdesUsageCombo, dukptAesUsageCombo, dukptAesKeyTypeCombo, dukptAesPinOperationCombo;
    @FXML private HBox dukptTdesOptionsBox, dukptAesOptionsBox;
    @FXML private VBox dukptAesPinBox;

    @FXML
    public void handleInspectDukpt() { dukpt().handleInspectDukpt(); }

    @FXML
    public void handleAesDukptPinBlock() { dukpt().handleAesDukptPinBlock(); }

    @FXML
    public void handleHsmHostCompose() { hsmHost().handleHsmHostCompose(); }

    @FXML
    public void handleHsmHostAnalyzeCommand() { hsmHost().handleHsmHostAnalyzeCommand(); }

    @FXML
    public void handleHsmHostAnalyzeResponse() { hsmHost().handleHsmHostAnalyzeResponse(); }

    @FXML
    public void handleHsmHostLoadExample() { hsmHost().handleHsmHostLoadExample(); }

    @FXML
    public void initialize() {
        moduleI18n = ModuleI18n.bind(paymentsContainer, ModuleTextCatalog.payments());
        iso8583().configure();
        if (pinBlockFormatCombo != null && pinBlockFormatDecodeCombo != null) {
            pinBlocks().setupPinBlockFormats();
        }
        if (encPinBlockFormatCombo != null) {
            encPinBlockFormatCombo.getItems().addAll(com.cryptocarver.crypto.PinBlockFormat.displayNames());
            encPinBlockFormatCombo.getSelectionModel().selectFirst();
        }
        if (cvvTypeCombo != null) {
            cvv().configure();
        }
        pinGeneration().configure();
        dukpt().configure();
        if (paymentsResultPanel != null) {
            paymentsResultPanel.connectTo(() -> mainController);
            // An encoded PIN block is the input to decoding it, so those two chain. A CVV and a
            // generated IBM 3624 PIN are not the input to anything here, so they pass no target
            // and the action is hidden while their result is on screen.
            // The result areas hold a whole report; chaining passes on just the block it produced.
            bindResult(paymentsResultPanel, pinBlockResultArea, "PIN block",
                    pinBlockField == null ? null : report -> chain(pinBlocks().encodedBlock(), pinBlockField));
            bindResult(paymentsResultPanel, cvvResultArea, "CVV", null);
            bindResult(paymentsResultPanel, encResultArea, "Encrypted PIN block",
                    encPinBlockFieldDecode == null ? null
                            : report -> chain(pinBlocks().encryptedBlock(), encPinBlockFieldDecode));
            bindResult(paymentsResultPanel, ibm3624ResultArea, "IBM 3624 PIN", null);
        }
    }

    @FXML
    public void handleParseIso8583() { iso8583().handleParseIso8583(); }

    @FXML
    public void handleBuildIso8583() { iso8583().handleBuildIso8583(); }

    /** Puts the block an encoding produced into the field that decodes it, when there is one. */
    private static void chain(String block, TextField target) {
        if (block != null) target.setText(block);
    }

    /**
     * Routes one operation's result area into the shared result surface.
     *
     * <p>{@code chainTarget} is where "use as input" should put the value while this operation's
     * result is the one shown; a null one hides the action rather than leaving it inert.
     */
    private static void bindResult(ResultPanel panel, TextArea area, String operation,
                                   java.util.function.Consumer<String> chainTarget) {
        if (panel == null || area == null) return;
        area.textProperty().addListener((obs, oldValue, value) -> {
            panel.showText(operation, value);
            panel.setChainHandler(chainTarget);
        });
    }

    private PinBlockCoordinator pinBlocks;

    private PinBlockCoordinator pinBlocks() {
        if (pinBlocks == null) {
            pinBlocks = new PinBlockCoordinator(new PinBlockCoordinator.View(pinField, panFieldEncode, pinBlockField,
                    panFieldDecode, pinBlockFormatCombo, pinBlockFormatDecodeCombo, pinBlockPaddingCombo,
                    pinBlockPaddingLabel, pinBlockResultArea, encPinBlockFormatCombo, encPinField, encPanFieldEncode,
                    encPinBlockKeyField, encPinBlockFieldDecode, encPanFieldDecode, encPinBlockKeyFieldDecode,
                    encResultArea), () -> mainController);
        }
        return pinBlocks;
    }

    private PinGenerationCoordinator pinGeneration;

    private PinGenerationCoordinator pinGeneration() {
        if (pinGeneration == null) {
            pinGeneration = new PinGenerationCoordinator(new PinGenerationCoordinator.View(ibm3624PvkField,
                    ibm3624ConvTableField, ibm3624OffsetField, ibm3624PanField, ibm3624PinVerifyField,
                    ibm3624ResultArea, ibm3624StartField, ibm3624LengthField, ibm3624PadField, genOffsetPvkField,
                    genOffsetDecTableField, genOffsetPanField, genOffsetPinField, genOffsetResultArea,
                    genOffsetStartField, genOffsetLengthField, genOffsetPadField, genPvvPvkField, genPvvPanField,
                    genPvvPinField, genPvvKeyIndexField, genPvvResultArea, derivePvvPvkField, derivePvvPanField,
                    derivePvvTargetPvvField, derivePvvKeyIndexField, derivePvvResultArea), () -> mainController);
        }
        return pinGeneration;
    }

    private CvvCoordinator cvv;

    private CvvCoordinator cvv() {
        if (cvv == null) {
            cvv = new CvvCoordinator(new CvvCoordinator.View(cvkAField, cvkBField, panFieldCvv, expiryDateField, serviceCodeField, atcField, cvvTypeCombo, cvvResultArea), () -> mainController, this::askCvvToVerify);
        }
        return cvv;
    }

    private DukptCoordinator dukpt;

    private DukptCoordinator dukpt() {
        if (dukpt == null) {
            dukpt = new DukptCoordinator(new DukptCoordinator.View(dukptBdkField, dukptKsnField, dukptResultArea, dukptSchemeCombo, dukptTdesUsageCombo, dukptAesUsageCombo, dukptAesKeyTypeCombo, dukptAesPinBlockField, dukptAesPinOperationCombo, dukptTdesOptionsBox, dukptAesOptionsBox, dukptAesPinBox), () -> mainController);
        }
        return dukpt;
    }

    private HsmHostCommandCoordinator hsmHost;

    private HsmHostCommandCoordinator hsmHost() {
        if (hsmHost == null) {
            hsmHost = new HsmHostCommandCoordinator(new HsmHostCommandCoordinator.View(hsmHostHeaderField, hsmHostCommandCodeField, hsmHostBodyField, hsmHostTrailerField, hsmHostHeaderLengthField, hsmHostTcpPrefixCheck, hsmHostCapturedFrameArea, hsmHostResultArea), () -> mainController);
        }
        return hsmHost;
    }

    private Iso8583Coordinator iso8583;

    private Iso8583Coordinator iso8583() {
        if (iso8583 == null) {
            iso8583 = new Iso8583Coordinator(new Iso8583Coordinator.View(iso8583ProfileCombo, iso8583BitmapEncodingCombo, iso8583LengthEncodingCombo, iso8583MessageArea, iso8583ReportArea), () -> mainController);
        }
        return iso8583;
    }

    /** Package-private so tests can answer the CVV question without a modal dialog. */
    java.util.function.Supplier<java.util.Optional<String>> cvvPrompt = () -> {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(t("module.payments.dialog.verifyCvvTitle"));
        dialog.setHeaderText(t("module.payments.dialog.verifyCvvHeader"));
        dialog.setContentText(t("module.payments.dialog.cvv"));
        return dialog.showAndWait();
    };

    private java.util.Optional<String> askCvvToVerify() {
        return cvvPrompt.get();
    }

    public void init(StatusReporter reporter) {
        this.mainController = reporter;
    }

    /** Restores safe module defaults while retaining local data, profiles and history. */
    public void resetModule() {
        ModuleResetPolicy.apply(paymentsContainer, ModuleResetPolicy.Action.RESET_DEFAULTS,
                this::clearModuleData, this::restoreSafeDefaults);
        updateStatus(t("module.payments.resetStatus"));
    }

    public void fillClipboardInput(String value) {
        if (pinBlockField != null) pinBlockField.setText(value);
    }

    @FXML
    public void handleClear() {
        ModuleResetPolicy.apply(paymentsContainer, ModuleResetPolicy.Action.CLEAR,
                this::clearModuleData, null);
        updateStatus(t("module.payments.clearStatus"));
    }

    private void clearModuleData() {
        ModuleResetPolicy.clearTextInputs(paymentsContainer);
    }

    private void restoreSafeDefaults() {
        if (pinBlockFormatCombo != null) pinBlockFormatCombo.getSelectionModel().selectFirst();
        if (pinBlockFormatDecodeCombo != null) pinBlockFormatDecodeCombo.getSelectionModel().selectFirst();
        if (encPinBlockFormatCombo != null) encPinBlockFormatCombo.getSelectionModel().selectFirst();
        if (cvvTypeCombo != null) cvvTypeCombo.getSelectionModel().selectFirst();
        if (dukptSchemeCombo != null) dukptSchemeCombo.getSelectionModel().selectFirst();
    }

    @FXML
    public void handleReset() {
        resetModule();
    }

    @FXML
    public void handleEncodePinBlock() { pinBlocks().handleEncodePinBlock(); }

    @FXML
    public void handleDecodePinBlock() { pinBlocks().handleDecodePinBlock(); }

    @FXML
    public void handleGenerateCvv() { cvv().handleGenerateCvv(); }

    @FXML
    public void handleVerifyCvv() { cvv().handleVerifyCvv(); }

    @FXML
    public void handleEncodeEncryptedPinBlock() { pinBlocks().handleEncodeEncryptedPinBlock(); }

    @FXML
    public void handleDecodeEncryptedPinBlock() { pinBlocks().handleDecodeEncryptedPinBlock(); }

    @FXML
    public void handleGenerateIbm3624Pin() { pinGeneration().handleGenerateIbm3624Pin(); }

    @FXML
    public void handleVerifyIbm3624Pin() { pinGeneration().handleVerifyIbm3624Pin(); }

    @FXML
    public void handleGenerateOffsetUtility() { pinGeneration().handleGenerateOffsetUtility(); }

    @FXML
    public void handleGeneratePVVUtility() { pinGeneration().handleGeneratePVVUtility(); }

    @FXML
    public void handleDerivePinFromPvvUtility() { pinGeneration().handleDerivePinFromPvvUtility(); }
    public void loadProfile(com.cryptocarver.model.payments.PaymentProfile p) {
        if (p.getType() == com.cryptocarver.model.payments.PaymentProfile.ProfileType.DUKPT_TDES) {
            dukpt().loadTdesProfile(p);
        } else if (p.getType() == com.cryptocarver.model.payments.PaymentProfile.ProfileType.DUKPT_AES) {
            dukpt().loadAesProfile(p);
        } else if (p.getType() == com.cryptocarver.model.payments.PaymentProfile.ProfileType.PIN) {
            if (p.getParameters().containsKey("format")) {
                String formatStr = p.getParameters().get("format");
                boolean encodePinBlock = p.getInputs().containsKey("pin") && !p.getInputs().containsKey("pinBlock");
                boolean encryptedProfile = p.getInputs().containsKey("key");
                if (encryptedProfile) {
                    selectPinFormat(encPinBlockFormatCombo, formatStr);
                    if (encodePinBlock) {
                        if (encPinField != null) encPinField.setText(p.getInputs().get("pin"));
                        if (encPanFieldEncode != null) encPanFieldEncode.setText(p.getInputs().getOrDefault("pan", ""));
                        if (encPinBlockKeyField != null) encPinBlockKeyField.setText(p.getInputs().get("key"));
                    } else {
                        if (encPinBlockFieldDecode != null) encPinBlockFieldDecode.setText(p.getInputs().get("pinBlock"));
                        if (encPanFieldDecode != null) encPanFieldDecode.setText(p.getInputs().getOrDefault("pan", ""));
                        if (encPinBlockKeyFieldDecode != null) encPinBlockKeyFieldDecode.setText(p.getInputs().get("key"));
                    }
                } else if (encodePinBlock) {
                    if (pinBlockFormatCombo != null) {
                        selectPinFormat(pinBlockFormatCombo, formatStr);
                    }
                    if (pinField != null && p.getInputs().containsKey("pin")) pinField.setText(p.getInputs().get("pin"));
                    if (panFieldEncode != null && p.getInputs().containsKey("pan")) panFieldEncode.setText(p.getInputs().get("pan"));
                } else {
                    if (pinBlockFormatDecodeCombo != null) {
                        selectPinFormat(pinBlockFormatDecodeCombo, formatStr);
                    }
                    if (pinBlockField != null && p.getInputs().containsKey("pinBlock")) pinBlockField.setText(p.getInputs().get("pinBlock"));
                    if (panFieldDecode != null && p.getInputs().containsKey("pan")) panFieldDecode.setText(p.getInputs().get("pan"));
                }
            }
            updateStatus(t("module.payments.status.profileLoaded", "PIN - " + p.getName()));
        } else if (p.getType() == com.cryptocarver.model.payments.PaymentProfile.ProfileType.SECURE_MESSAGING) {
            // The Payments screen has no secure-messaging form to fill; say so instead of "loaded".
            updateStatus(t("module.payments.status.profileNoForm", p.getName()));
        }
    }

    private void selectPinFormat(ComboBox<String> comboBox, String profileFormat) {
        if (comboBox == null || profileFormat == null) return;
        String formatNumber = profileFormat.replaceAll("\\D", "");
        for (String item : comboBox.getItems()) {
            if (item.contains(profileFormat)
                    || (!formatNumber.isEmpty() && item.contains("Format " + formatNumber))) {
                comboBox.setValue(item);
                return;
            }
        }
    }
}
