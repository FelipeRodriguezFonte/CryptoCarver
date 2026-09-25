package com.cryptocarver.ui;

import com.cryptocarver.crypto.PaymentOperations;
import com.cryptocarver.crypto.DukptKsn;
import com.cryptocarver.crypto.AesDukpt;
import com.cryptocarver.crypto.hsm.PayShieldBodyDecomposer;
import com.cryptocarver.crypto.hsm.PayShieldCommand;
import com.cryptocarver.crypto.hsm.PayShieldErrorCatalog;
import com.cryptocarver.crypto.hsm.PayShieldMessage;
import com.cryptocarver.crypto.hsm.PayShieldMessageCodec;
import com.cryptocarver.crypto.hsm.PayShieldResponse;
import com.cryptocarver.crypto.iso8583.Iso8583Operations;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.util.DataConverter;
import com.cryptocarver.utils.OperationHistory;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;

import java.util.LinkedHashMap;
import java.util.Map;

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

    // Helper methods to call methods on MainController or ModernMainController
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

    private static final String SUPPLIED_NC_RESPONSE = "0000ND007B44AC1DDEE2A94B0007-E000";

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

    // MAC controls
    private ComboBox<String> macAlgorithmCombo;
    private TextField macKeyField;
    private TextArea macDataField;
    private TextArea macResultArea;

    // PIN Controller for advanced operations
    private PinController pinController;

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
    private DukptKsn.TdesKeyUsage selectedTdesUsage = DukptKsn.TdesKeyUsage.PIN_ENCRYPTION;
    private String loadedDukptProfileName;
    private String loadedDukptExpectedWorkingKey;

    public void initializeDukptControls(TextField bdkField, TextField ksnField, TextArea resultArea,
            ComboBox<String> schemeCombo, ComboBox<String> tdesUsageCombo, ComboBox<String> aesUsageCombo,
            ComboBox<String> aesKeyTypeCombo, TextField aesPinBlockField, ComboBox<String> aesPinOperationCombo,
            HBox tdesOptionsBox, HBox aesOptionsBox, VBox aesPinBox) {
        this.dukptBdkField = bdkField; this.dukptKsnField = ksnField; this.dukptResultArea = resultArea;
        this.dukptSchemeCombo = schemeCombo; this.dukptTdesUsageCombo = tdesUsageCombo;
        this.dukptAesUsageCombo = aesUsageCombo; this.dukptAesKeyTypeCombo = aesKeyTypeCombo;
        this.dukptAesPinBlockField = aesPinBlockField; this.dukptAesPinOperationCombo = aesPinOperationCombo;
        this.dukptTdesOptionsBox = tdesOptionsBox; this.dukptAesOptionsBox = aesOptionsBox; this.dukptAesPinBox = aesPinBox;
        if (schemeCombo != null) {
            schemeCombo.getItems().setAll("TDES (legacy, 10-byte KSN)", "AES (X9.24-3, 12-byte KSN)");
            schemeCombo.setValue("TDES (legacy, 10-byte KSN)");
            schemeCombo.valueProperty().addListener((ignored, oldValue, newValue) -> updateDukptOptionsVisibility());
        }
        if (tdesUsageCombo != null) {
            tdesUsageCombo.getItems().setAll("PIN Encryption", "MAC Request", "MAC Response", "Data Encryption");
            tdesUsageCombo.setValue(selectedTdesUsage.label());
            tdesUsageCombo.valueProperty().addListener((ignored, oldValue, newValue) -> selectedTdesUsage = selectedTdesUsage());
        }
        if (aesUsageCombo != null) { aesUsageCombo.getItems().setAll("Data encryption (encrypt)", "Data encryption (decrypt)", "PIN encryption", "MAC generation", "MAC verification", "MAC both ways", "Key encryption", "Key derivation"); aesUsageCombo.setValue("Data encryption (encrypt)"); }
        if (aesKeyTypeCombo != null) { aesKeyTypeCombo.getItems().setAll("AES-128", "AES-192", "AES-256"); aesKeyTypeCombo.setValue("AES-128"); }
        if (aesPinOperationCombo != null) { aesPinOperationCombo.getItems().setAll("Encrypt formatted PIN block", "Decrypt encrypted PIN block"); aesPinOperationCombo.setValue("Encrypt formatted PIN block"); }
        updateDukptOptionsVisibility();
    }

    private void updateDukptOptionsVisibility() {
        boolean aes = dukptSchemeCombo != null && dukptSchemeCombo.getValue() != null && dukptSchemeCombo.getValue().startsWith("AES");
        setDukptSectionVisible(dukptTdesOptionsBox, !aes);
        setDukptSectionVisible(dukptAesOptionsBox, aes);
        setDukptSectionVisible(dukptAesPinBox, aes);
    }

    private static void setDukptSectionVisible(javafx.scene.Node node, boolean visible) {
        if (node != null) {
            node.setVisible(visible);
            node.setManaged(visible);
        }
    }

    private DukptKsn.TdesKeyUsage selectedTdesUsage() {
        String selection = dukptTdesUsageCombo == null ? null : dukptTdesUsageCombo.getValue();
        return switch (selection == null ? "" : selection) {
            case "MAC Request" -> DukptKsn.TdesKeyUsage.MAC_REQUEST;
            case "MAC Response" -> DukptKsn.TdesKeyUsage.MAC_RESPONSE;
            case "Data Encryption" -> DukptKsn.TdesKeyUsage.DATA_ENCRYPTION;
            default -> DukptKsn.TdesKeyUsage.PIN_ENCRYPTION;
        };
    }

    public void handleInspectDukpt() {
        try {
            if (dukptSchemeCombo != null && dukptSchemeCombo.getValue().startsWith("AES")) { inspectAesDukpt(); return; }
            DukptKsn.Parsed ksn = DukptKsn.parseTdes(dukptKsnField.getText());
            String result = "--- DUKPT TDES KSN ---\nKSN: " + ksn.ksnHex() + "\nBase KSN: " + ksn.baseKsnHex()
                    + "\nDevice ID: " + ksn.deviceIdentifierHex() + "\nTransaction counter: " + ksn.transactionCounter()
                    + "\nCounter exhausted: " + DukptKsn.isTdesCounterExhausted(ksn.ksnHex())
                    + "\nNext KSN: " + (DukptKsn.isTdesCounterExhausted(ksn.ksnHex()) ? t("module.payments.status.notAvailable") : DukptKsn.nextTdesKsn(ksn.ksnHex()));
            if (!dukptBdkField.getText().isBlank()) {
                String ipek = DukptKsn.deriveIpek(dukptBdkField.getText(), ksn.ksnHex());
                DukptKsn.TdesDerivedKey derived = DukptKsn.deriveWorkingKey(ipek, ksn.ksnHex(), selectedTdesUsage);

                result += "\n\n=== Derivation Tree ===";
                result += "\n[BDK]\n  └─ " + dukptBdkField.getText().replaceAll("\\s+", "").toUpperCase();
                result += "\n\n[IPEK (Initial PIN Encryption Key)]\n  └─ " + ipek.toUpperCase();

                result += "\n\n[Counter Steps (Intermediate)]";
                if (derived.derivationSteps().isEmpty()) {
                    result += "\n  └─ (None)";
                } else {
                    for (String step : derived.derivationSteps()) {
                        result += "\n  └─ " + step.toUpperCase();
                    }
                }

                if (loadedDukptProfileName != null) {
                    result += "\n\n[Laboratory Profile]\n  └─ " + loadedDukptProfileName;
                }
                result += "\n\n[Selected Working Key (" + selectedTdesUsage.label() + ")]\n  └─ "
                        + derived.workingKeyHex().toUpperCase();
                if (loadedDukptExpectedWorkingKey != null) {
                    boolean matches = loadedDukptExpectedWorkingKey.equalsIgnoreCase(derived.workingKeyHex());
                    result += "\n\n[Laboratory Expected Key]\n  └─ " + loadedDukptExpectedWorkingKey.toUpperCase();
                    result += "\n[" + t("module.payments.result.vectorCheck") + "]\n  └─ " + t(matches ? "module.payments.status.match" : "module.payments.status.mismatch");
                }

                DukptKsn.TdesDerivedKey macDerived = DukptKsn.deriveWorkingKey(ipek, ksn.ksnHex(), DukptKsn.TdesKeyUsage.MAC_REQUEST);
                result += "\n\n[Working Key (MAC Variant)]\n  └─ " + macDerived.workingKeyHex().toUpperCase();

                DukptKsn.TdesDerivedKey dataDerived = DukptKsn.deriveWorkingKey(ipek, ksn.ksnHex(), DukptKsn.TdesKeyUsage.DATA_ENCRYPTION);
                result += "\n\n[Working Key (Data Variant)]\n  └─ " + dataDerived.workingKeyHex().toUpperCase();
            }
            dukptResultArea.setText(result); dukptResultArea.setManaged(true); dukptResultArea.setVisible(true);
            updateStatus(t("module.payments.status.dukptInspected"));
        } catch (Exception e) { showError(t("module.payments.error.dukptTitle"), t("module.payments.error.operation", t("module.payments.error.dukptTitle"), e.getMessage())); }
    }

    private void inspectAesDukpt() throws Exception {
        AesDukpt.ParsedKsn ksn = AesDukpt.parseKsn(dukptKsnField.getText());
        String result = "--- AES DUKPT (ANSI X9.24-3) ---\nKSN: " + ksn.ksnHex() + "\nInitial Key ID: " + ksn.initialKeyIdHex()
                + "\nBase KSN: " + ksn.baseKsnHex() + "\nTransaction counter: " + String.format("%08X", ksn.transactionCounter())
                + "\nCounter exhausted: " + AesDukpt.isCounterExhausted(ksn.ksnHex())
                + "\nNext KSN: " + (AesDukpt.isCounterExhausted(ksn.ksnHex()) ? t("module.payments.status.notAvailable") : AesDukpt.nextKsn(ksn.ksnHex()));
        if (!dukptBdkField.getText().isBlank()) {
            AesDukpt.KeyUsage usage = selectedAesUsage();
            AesDukpt.KeyType type = selectedAesKeyType();
            AesDukpt.DerivedKey derived = AesDukpt.deriveWorkingKey(dukptBdkField.getText(), ksn.ksnHex(), usage, type);

            result += "\n\n=== Derivation Tree ===";
            result += "\n[BDK]\n  └─ " + dukptBdkField.getText().replaceAll("\\s+", "").toUpperCase() + " (" + AesDukpt.KeyType.fromBytes(dukptBdkField.getText().replaceAll("\\s+", "").length() / 2) + ")";
            result += "\n\n[Initial Key / IPEK]\n  └─ " + derived.initialKeyHex().toUpperCase();

            if (!derived.initialKeyHex().equalsIgnoreCase(derived.intermediateKeyHex())) {
                result += "\n\n[Counter Steps (Intermediate)]\n  └─ " + derived.intermediateKeyHex().toUpperCase();
            }

            result += "\n\n[Final Derivation Data]\n  └─ " + derived.derivationDataHex().toUpperCase();
            result += "\n\n[Working Key]\n  └─ " + derived.workingKeyHex().toUpperCase();
        }

        dukptResultArea.setText(result); dukptResultArea.setManaged(true); dukptResultArea.setVisible(true); updateStatus(t("module.payments.status.aesDukptDerived"));
    }
    private AesDukpt.KeyUsage selectedAesUsage() {
        String selection = dukptAesUsageCombo == null ? "Data encryption (encrypt)" : dukptAesUsageCombo.getValue();
        return switch (selection) {
            case "Data encryption (decrypt)" -> AesDukpt.KeyUsage.DATA_ENCRYPTION_DECRYPT;
            case "PIN encryption" -> AesDukpt.KeyUsage.PIN_ENCRYPTION;
            case "MAC generation" -> AesDukpt.KeyUsage.MAC_GENERATION;
            case "MAC verification" -> AesDukpt.KeyUsage.MAC_VERIFICATION;
            case "MAC both ways" -> AesDukpt.KeyUsage.MAC_BOTH_WAYS;
            case "Key encryption" -> AesDukpt.KeyUsage.KEY_ENCRYPTION;
            case "Key derivation" -> AesDukpt.KeyUsage.KEY_DERIVATION;
            default -> AesDukpt.KeyUsage.DATA_ENCRYPTION_ENCRYPT;
        };
    }
    private AesDukpt.KeyType selectedAesKeyType() {
        String selection = dukptAesKeyTypeCombo == null ? "AES-128" : dukptAesKeyTypeCombo.getValue();
        return "AES-192".equals(selection) ? AesDukpt.KeyType.AES192 : "AES-256".equals(selection) ? AesDukpt.KeyType.AES256 : AesDukpt.KeyType.AES128;
    }

    public void handleAesDukptPinBlock() {
        try {
            if (dukptSchemeCombo == null || !dukptSchemeCombo.getValue().startsWith("AES")) {
                throw new IllegalArgumentException(t("module.payments.error.aesDukptSelection"));
            }
            if (dukptBdkField == null || dukptBdkField.getText().isBlank()) throw new IllegalArgumentException(t("module.payments.error.aesDukptBdkRequired"));
            boolean decrypt = dukptAesPinOperationCombo != null && dukptAesPinOperationCombo.getValue().startsWith("Decrypt");
            AesDukpt.KeyType type = selectedAesKeyType();
            AesDukpt.DerivedKey derived = AesDukpt.deriveWorkingKey(dukptBdkField.getText(), dukptKsnField.getText(), AesDukpt.KeyUsage.PIN_ENCRYPTION, type);
            String output = AesDukpt.cryptPinBlock(dukptBdkField.getText(), dukptKsnField.getText(), type, dukptAesPinBlockField.getText(), decrypt);
            dukptResultArea.setText("--- AES DUKPT PIN block (lab operation) ---\nKSN: " + AesDukpt.parseKsn(dukptKsnField.getText()).ksnHex()
                    + "\nPIN key type: " + type + "\nDerived PIN key: " + derived.workingKeyHex()
                    + "\n" + t("module.payments.result.inputBlock", decrypt ? "encrypted" : "formatted") + " " + dukptAesPinBlockField.getText().replaceAll("\\s+", "").toUpperCase()
                    + "\n" + t("module.payments.result.outputBlock", decrypt ? "formatted" : "encrypted") + " " + output
                    + "\n\n" + t("module.payments.result.aesDukptNote"));
            updateStatus(t("module.payments.status.aesPinBlockProcessed"));
        } catch (Exception e) { showError(t("module.payments.operation.aesPinBlock"), t("module.payments.error.operation", t("module.payments.operation.aesPinBlock"), e.getMessage())); }
    }

    @FXML
    public void handleHsmHostCompose() {
        runHsmHost(() -> {
            PayShieldMessageCodec codec = hsmHostCodec();
            byte[] frame = codec.composeCommand(
                    controlText(hsmHostHeaderField),
                    controlText(hsmHostCommandCodeField).toUpperCase(java.util.Locale.ROOT),
                    controlText(hsmHostBodyField).getBytes(StandardCharsets.US_ASCII),
                    controlText(hsmHostTrailerField).getBytes(StandardCharsets.US_ASCII));
            String rendered = renderHsmFrame(frame, codec.tcpLengthPrefix());
            if (hsmHostCapturedFrameArea != null) {
                hsmHostCapturedFrameArea.setText(rendered);
            }
            return describeHsmCommand(codec.parseCommand(frame));
        });
    }

    @FXML
    public void handleHsmHostAnalyzeCommand() {
        runHsmHost(() -> {
            PayShieldMessageCodec codec = hsmHostCodec();
            return describeHsmCommand(codec.parseCommand(readHsmFrame(codec.tcpLengthPrefix())));
        });
    }

    @FXML
    public void handleHsmHostAnalyzeResponse() {
        runHsmHost(() -> {
            PayShieldMessageCodec codec = hsmHostCodec();
            return describeHsmResponse(codec.parseResponse(readHsmFrame(codec.tcpLengthPrefix())));
        });
    }

    /**
     * Origin not recorded: this value was already in the tree when its
     * provenance was questioned, and no independent capture supports it.
     * Capture NC-00 will replace it.
     */
    @FXML
    public void handleHsmHostLoadExample() {
        if (hsmHostHeaderLengthField != null) {
            hsmHostHeaderLengthField.setText("4");
        }
        if (hsmHostTcpPrefixCheck != null) {
            hsmHostTcpPrefixCheck.setSelected(false);
        }
        if (hsmHostCapturedFrameArea != null) {
            hsmHostCapturedFrameArea.setText(SUPPLIED_NC_RESPONSE);
        }
        if (hsmHostResultArea != null) {
            hsmHostResultArea.setText(t("module.payments.hsmHost.exampleLoaded"));
        }
    }

    private PayShieldMessageCodec hsmHostCodec() {
        String length = controlText(hsmHostHeaderLengthField);
        int headerLength;
        try {
            headerLength = Integer.parseInt(length);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("header length must be a decimal integer");
        }
        return new PayShieldMessageCodec(headerLength,
                hsmHostTcpPrefixCheck != null && hsmHostTcpPrefixCheck.isSelected());
    }

    private byte[] readHsmFrame(boolean tcpPrefix) {
        String value = controlText(hsmHostCapturedFrameArea);
        if (!tcpPrefix) {
            return value.getBytes(StandardCharsets.US_ASCII);
        }
        try {
            return HexFormat.of().parseHex(value.replaceAll("\\s+", ""));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("a TCP-prefixed frame must be entered as hexadecimal");
        }
    }

    private static String renderHsmFrame(byte[] frame, boolean tcpPrefix) {
        return tcpPrefix
                ? HexFormat.of().withUpperCase().formatHex(frame)
                : new String(frame, StandardCharsets.US_ASCII);
    }

    private static String describeHsmCommand(PayShieldMessage message) {
        PayShieldCommand command = findHsmCommand(message.code());
        String body = new String(message.body(), StandardCharsets.US_ASCII);
        String trailer = new String(message.trailer(), StandardCharsets.US_ASCII);
        StringBuilder report = new StringBuilder()
                .append("Type: command\n")
                .append("Header: ").append(message.header()).append('\n')
                .append("Command code: ").append(message.code()).append('\n')
                .append("Command: ")
                .append(command == null ? "Unknown; body left opaque" : command.displayName())
                .append('\n')
                .append("Expected response: ")
                .append(command == null ? "Unknown" : command.expectedResponseCode()).append('\n')
                .append("Body length: ").append(message.body().length).append('\n')
                .append("Body (opaque): ").append(body.isEmpty() ? "(empty)" : body).append('\n')
                .append("Trailer: ").append(trailer.isEmpty() ? "(none)" : trailer);
        appendHsmDecomposition(report, PayShieldBodyDecomposer.decompose(message));
        return report.toString();
    }

    private static String describeHsmResponse(PayShieldResponse response) {
        PayShieldCommand command = findHsmCommandByResponse(response.responseCode());
        String data = new String(response.data(), StandardCharsets.US_ASCII);
        String trailer = new String(response.trailer(), StandardCharsets.US_ASCII);
        StringBuilder report = new StringBuilder()
                .append("Type: response\n")
                .append("Header: ").append(response.header()).append('\n')
                .append("Response code: ").append(response.responseCode()).append('\n')
                .append("Command: ")
                .append(command == null ? "Unknown" : command.name() + " — " + command.displayName())
                .append('\n')
                .append("Error code: ").append(response.errorCode()).append(" — ")
                .append(PayShieldErrorCatalog.translate(response.errorCode())).append('\n')
                .append("Data length: ").append(response.data().length).append('\n')
                .append("Data (opaque): ").append(data.isEmpty() ? "(empty)" : data).append('\n')
                .append("Trailer: ").append(trailer.isEmpty() ? "(none)" : trailer);
        appendHsmDecomposition(report, PayShieldBodyDecomposer.decompose(response));
        return report.toString();
    }

    private static void appendHsmDecomposition(
            StringBuilder report,
            Optional<PayShieldBodyDecomposer.Decomposition> optionalDecomposition) {
        optionalDecomposition.ifPresent(decomposition -> {
            report.append("\nBody schema: ")
                    .append(decomposition.schema().evidenceStatus())
                    .append(" (").append(decomposition.schema().evidenceId()).append(')');
            for (PayShieldBodyDecomposer.DecodedField field : decomposition.fields()) {
                report.append('\n')
                        .append(field.definition().displayName())
                        .append(": ")
                        .append(field.value());
            }
        });
    }

    private static PayShieldCommand findHsmCommand(String code) {
        try {
            return PayShieldCommand.valueOf(code);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static PayShieldCommand findHsmCommandByResponse(String responseCode) {
        for (PayShieldCommand command : PayShieldCommand.values()) {
            if (command.expectedResponseCode().equals(responseCode)) {
                return command;
            }
        }
        return null;
    }

    private interface HsmHostStep {
        String run();
    }

    private void runHsmHost(HsmHostStep step) {
        try {
            String report = step.run();
            if (hsmHostResultArea != null) {
                hsmHostResultArea.setText(report);
            }
            updateStatus(t("module.payments.hsmHost.status"));
        } catch (Exception e) {
            if (hsmHostResultArea != null) {
                hsmHostResultArea.setText(
                        t("module.payments.hsmHost.error", String.valueOf(e.getMessage())));
            }
        }
    }

    private static String controlText(TextInputControl control) {
        return control == null || control.getText() == null ? "" : control.getText().trim();
    }

    @FXML
    public void initialize() {
        moduleI18n = ModuleI18n.bind(paymentsContainer, ModuleTextCatalog.payments());
        initializeIso8583Controls();
        initialize(null,
                pinField, panFieldEncode, pinBlockField, panFieldDecode,
                pinBlockFormatCombo, pinBlockFormatDecodeCombo, pinBlockResultArea,
                cvkAField, cvkBField, panFieldCvv, expiryDateField, serviceCodeField,
                atcField, cvvTypeCombo, cvvResultArea,
                null, null, null, null,
                encPinBlockFormatCombo, encPinField, encPanFieldEncode, encPinBlockKeyField,
                encPinBlockFieldDecode, encPanFieldDecode, encPinBlockKeyFieldDecode, encResultArea,
                genOffsetPvkField, genOffsetDecTableField, genOffsetPanField, genOffsetPinField,
                genOffsetResultArea, genOffsetStartField, genOffsetLengthField, genOffsetPadField,
                genPvvPvkField, genPvvPanField, genPvvPinField, genPvvKeyIndexField, genPvvResultArea,
                derivePvvPvkField, derivePvvPanField, derivePvvTargetPvvField,
                derivePvvKeyIndexField, derivePvvResultArea);
        initializeIbm3624Controls(ibm3624PvkField, ibm3624ConvTableField, ibm3624OffsetField,
                ibm3624PanField, ibm3624PinVerifyField, ibm3624ResultArea,
                ibm3624StartField, ibm3624LengthField, ibm3624PadField);
        initializeDukptControls(dukptBdkField, dukptKsnField, dukptResultArea,
                dukptSchemeCombo, dukptTdesUsageCombo, dukptAesUsageCombo, dukptAesKeyTypeCombo,
                dukptAesPinBlockField, dukptAesPinOperationCombo,
                dukptTdesOptionsBox, dukptAesOptionsBox, dukptAesPinBox);
        if (ibm3624ConvTableField != null && ibm3624ConvTableField.getText().isBlank()) {
            ibm3624ConvTableField.setText("0123456789012345");
        }
        if (paymentsResultPanel != null) {
            paymentsResultPanel.connectTo(() -> mainController);
            // An encoded PIN block is the input to decoding it, so those two chain. A CVV and a
            // generated IBM 3624 PIN are not the input to anything here, so they pass no target
            // and the action is hidden while their result is on screen.
            bindResult(paymentsResultPanel, pinBlockResultArea, "PIN block",
                    pinBlockField == null ? null : pinBlockField::setText);
            bindResult(paymentsResultPanel, cvvResultArea, "CVV", null);
            bindResult(paymentsResultPanel, encResultArea, "Encrypted PIN block",
                    encPinBlockFieldDecode == null ? null : encPinBlockFieldDecode::setText);
            bindResult(paymentsResultPanel, ibm3624ResultArea, "IBM 3624 PIN", null);
        }
    }

    private void initializeIso8583Controls() {
        if (iso8583ProfileCombo != null) {
            iso8583ProfileCombo.getItems().setAll("ISO 8583:1987", "ISO 8583:1993");
            iso8583ProfileCombo.getSelectionModel().selectFirst();
        }
        if (iso8583BitmapEncodingCombo != null) {
            iso8583BitmapEncodingCombo.getItems().setAll("Binary", "Hexadecimal ASCII");
            iso8583BitmapEncodingCombo.getSelectionModel().selectFirst();
        }
        if (iso8583LengthEncodingCombo != null) {
            iso8583LengthEncodingCombo.getItems().setAll("ASCII", "BCD");
            iso8583LengthEncodingCombo.getSelectionModel().selectFirst();
        }
    }

    @FXML
    public void handleParseIso8583() {
        runIso8583Operation("parse");
    }

    @FXML
    public void handleBuildIso8583() {
        runIso8583Operation("build");
    }

    private void runIso8583Operation(String operation) {
        if (iso8583MessageArea == null || iso8583ReportArea == null) return;
        String input = iso8583MessageArea.getText() == null ? "" : iso8583MessageArea.getText().trim();
        if (input.isEmpty()) {
            iso8583ReportArea.setText(t("module.payments.iso8583.emptyMessage"));
            return;
        }
        try {
            Iso8583Operations.Profile profile = iso8583Profile();
            String report;
            if ("parse".equals(operation)) {
                Iso8583Operations.Message parsed = parseIso8583(input, profile);
                report = parsed.report();
            } else {
                BuiltIso8583 built = buildIso8583(input, profile);
                byte[] rebuilt = Iso8583Operations.build(built.mti(), built.values(), profile);
                report = "Built message (" + profile.bitmapEncoding() + ", " + profile.lengthEncoding() + "):\n"
                        + formatIsoOutput(rebuilt, profile);
            }
            iso8583ReportArea.setText(report);
            updateStatus(t("module.payments.iso8583.completed", operation));
        } catch (Exception e) {
            LOG.debug("ISO 8583 {} failed", operation, e);
            iso8583ReportArea.setText(t("module.payments.iso8583.error", e.getMessage()));
        }
    }

    private Iso8583Operations.Profile iso8583Profile() {
        Iso8583Operations.Version version = "ISO 8583:1993".equals(iso8583ProfileCombo.getValue())
                ? Iso8583Operations.Version.ISO_1993 : Iso8583Operations.Version.ISO_1987;
        Iso8583Operations.BitmapEncoding bitmap = "Hexadecimal ASCII".equals(iso8583BitmapEncodingCombo.getValue())
                ? Iso8583Operations.BitmapEncoding.ASCII_HEX : Iso8583Operations.BitmapEncoding.BINARY;
        Iso8583Operations.LengthEncoding length = "BCD".equals(iso8583LengthEncodingCombo.getValue())
                ? Iso8583Operations.LengthEncoding.BCD : Iso8583Operations.LengthEncoding.ASCII;
        return new Iso8583Operations.Profile(version, bitmap, length, false);
    }

    private static Iso8583Operations.Message parseIso8583(String input, Iso8583Operations.Profile profile) {
        if (profile.bitmapEncoding() == Iso8583Operations.BitmapEncoding.BINARY) {
            return Iso8583Operations.parseBinary(hexToBytes(input), profile);
        }
        return Iso8583Operations.parseAsciiHex(input.replaceAll("\\s+", ""), profile);
    }

    private record BuiltIso8583(String mti, Map<Integer, String> values) { }

    /** Build input is MTI on the first line followed by one decimal field per line, e.g. 3=000000. */
    private static BuiltIso8583 buildIso8583(String input, Iso8583Operations.Profile profile) {
        String[] lines = input.lines().map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
        if (lines.length > 1 && lines[0].matches("\\d{4}")) {
            Map<Integer, String> fields = new LinkedHashMap<>();
            for (int i = 1; i < lines.length; i++) {
                int equals = lines[i].indexOf('=');
                if (equals < 1) throw new IllegalArgumentException("build lines must use field=value");
                int number = Integer.parseInt(lines[i].substring(0, equals).trim());
                fields.put(number, lines[i].substring(equals + 1).trim());
            }
            return new BuiltIso8583(lines[0], fields);
        }
        Iso8583Operations.Message parsed = parseIso8583(input, profile);
        Map<Integer, String> values = new LinkedHashMap<>();
        parsed.fields().forEach((n, field) -> values.put(n, field.value()));
        return new BuiltIso8583(parsed.mti().value(), values);
    }

    private static String formatIsoOutput(byte[] bytes, Iso8583Operations.Profile profile) {
        if (profile.bitmapEncoding() == Iso8583Operations.BitmapEncoding.ASCII_HEX) {
            return new String(bytes, java.nio.charset.StandardCharsets.US_ASCII);
        }
        StringBuilder out = new StringBuilder();
        for (byte b : bytes) out.append(String.format(java.util.Locale.ROOT, "%02X", b & 0xff));
        return out.toString();
    }

    private static byte[] hexToBytes(String value) {
        String hex = value.replaceAll("\\s+", "");
        if (!hex.matches("(?i)[0-9a-f]+") || (hex.length() & 1) != 0) {
            throw new IllegalArgumentException("binary input must be an even-length hexadecimal message");
        }
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < result.length; i++) result[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        return result;
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

    public void initialize(StatusReporter mainController,
            TextField pinField,
            TextField panFieldEncode,
            TextField pinBlockField,
            TextField panFieldDecode,
            ComboBox<String> pinBlockFormatCombo,
            ComboBox<String> pinBlockFormatDecodeCombo,
            TextArea pinBlockResultArea,
            TextField cvkAField,
            TextField cvkBField,
            TextField panFieldCvv,
            TextField expiryDateField,
            TextField serviceCodeField,
            TextField atcField,
            ComboBox<String> cvvTypeCombo,
            TextArea cvvResultArea,
            ComboBox<String> macAlgorithmCombo,
            TextField macKeyField,
            TextArea macDataField,
            TextArea macResultArea,
            // New Encrypted PIN Fields (Generic)
            ComboBox<String> encPinBlockFormatCombo,
            TextField encPinField,
            TextField encPanFieldEncode,
            TextField encPinBlockKeyField,
            TextField encPinBlockFieldDecode,
            TextField encPanFieldDecode,
            TextField encPinBlockKeyFieldDecode,
            TextArea encResultArea,
            // New PIN Generator Fields
            TextField genOffsetPvkField,
            TextField genOffsetDecTableField,
            TextField genOffsetPanField,
            TextField genOffsetPinField,
            TextArea genOffsetResultArea,
            // Offset Config
            TextField genOffsetStartField,
            TextField genOffsetLengthField,
            TextField genOffsetPadField,
            TextField genPvvPvkField,
            TextField genPvvPanField,
            TextField genPvvPinField,
            TextField genPvvKeyIndexField,
            TextArea genPvvResultArea,
            // Derive PIN from PVV Fields
            TextField derivePvvPvkField,
            TextField derivePvvPanField,
            TextField derivePvvTargetPvvField,
            TextField derivePvvKeyIndexField,
            TextArea derivePvvResultArea) {

        this.mainController = mainController;
        this.pinField = pinField;
        this.panFieldEncode = panFieldEncode;
        this.pinBlockField = pinBlockField;
        this.panFieldDecode = panFieldDecode;
        this.pinBlockFormatCombo = pinBlockFormatCombo;
        this.pinBlockFormatDecodeCombo = pinBlockFormatDecodeCombo;
        this.pinBlockResultArea = pinBlockResultArea;
        this.cvkAField = cvkAField;
        this.cvkBField = cvkBField;
        this.panFieldCvv = panFieldCvv;
        this.expiryDateField = expiryDateField;
        this.serviceCodeField = serviceCodeField;
        this.atcField = atcField;
        this.cvvTypeCombo = cvvTypeCombo;
        this.cvvResultArea = cvvResultArea;
        this.macAlgorithmCombo = macAlgorithmCombo;
        this.macKeyField = macKeyField;
        this.macDataField = macDataField;
        this.macResultArea = macResultArea;

        // Assign generic Encrypted PIN fields
        this.encPinBlockFormatCombo = encPinBlockFormatCombo;
        this.encPinField = encPinField;
        this.encPanFieldEncode = encPanFieldEncode;
        this.encPinBlockKeyField = encPinBlockKeyField;
        this.encPinBlockFieldDecode = encPinBlockFieldDecode;
        this.encPanFieldDecode = encPanFieldDecode;
        this.encPinBlockKeyFieldDecode = encPinBlockKeyFieldDecode;
        this.encResultArea = encResultArea;

        // Assign PIN Generator fields
        this.genOffsetPvkField = genOffsetPvkField;
        this.genOffsetDecTableField = genOffsetDecTableField;
        this.genOffsetPanField = genOffsetPanField;
        this.genOffsetPinField = genOffsetPinField;
        this.genOffsetResultArea = genOffsetResultArea;
        this.genOffsetStartField = genOffsetStartField;
        this.genOffsetLengthField = genOffsetLengthField;
        this.genOffsetPadField = genOffsetPadField;
        this.genPvvPvkField = genPvvPvkField;
        this.genPvvPanField = genPvvPanField;
        this.genPvvPinField = genPvvPinField;
        this.genPvvKeyIndexField = genPvvKeyIndexField;
        this.genPvvResultArea = genPvvResultArea;

        // Derive PIN Fields
        this.derivePvvPvkField = derivePvvPvkField;
        this.derivePvvPanField = derivePvvPanField;
        this.derivePvvTargetPvvField = derivePvvTargetPvvField;
        this.derivePvvKeyIndexField = derivePvvKeyIndexField;
        this.derivePvvResultArea = derivePvvResultArea;

        // Only setup controls that are available (not null)
        if (pinBlockFormatCombo != null && pinBlockFormatDecodeCombo != null) {
            setupPinBlockFormats();
        }
        if (cvvTypeCombo != null) {
            setupCvvTypes();
        }
        if (macAlgorithmCombo != null) {
            setupMacAlgorithms();
        }

        // Initialize Encrypted PIN Block Format Combo if available
        if (encPinBlockFormatCombo != null) {
            encPinBlockFormatCombo.getItems().addAll(
                    "Format 0 (ISO-0)",
                    "Format 1 (ISO-1)",
                    "Format 2 (ISO-2)",
                    "Format 3 (ISO-3)");
            encPinBlockFormatCombo.getSelectionModel().selectFirst();
        }
    }

    private void setupPinBlockFormats() {
        if (pinBlockFormatCombo == null || pinBlockFormatDecodeCombo == null) {
            return; // Safety check
        }
        pinBlockFormatCombo.getItems().addAll(
                "Format 0 (ISO-0)",
                "Format 1 (ISO-1)",
                "Format 2 (ISO-2)",
                "Format 3 (ISO-3)",
                "Format 4 (ISO-4)",
                "ANSI X9.8",
                "IBM 3624",
                "VISA-1",
                "VISA-2",
                "VISA-3",
                "VISA-4",
                "ECI-1",
                "ECI-2 (no PAN binding)",
                "ECI-3 (no PAN binding)",
                "ECI-4");
        pinBlockFormatCombo.getSelectionModel().selectFirst();

        pinBlockFormatDecodeCombo.getItems().addAll(pinBlockFormatCombo.getItems());
        pinBlockFormatDecodeCombo.getSelectionModel().selectFirst();
    }

    private void setupCvvTypes() {
        if (cvvTypeCombo == null) {
            return; // Safety check
        }
        cvvTypeCombo.getItems().addAll(
                "CVV (Magnetic Stripe)",
                "CVV2 (Card Printed)",
                "iCVV (Chip)",
                "dCVV (Dynamic)");
        cvvTypeCombo.getSelectionModel().selectFirst();
    }

    private void setupMacAlgorithms() {
        if (macAlgorithmCombo == null) {
            return; // Safety check
        }
        macAlgorithmCombo.getItems().addAll(
                "Retail MAC (ISO 9797-1 Alg 3)",
                "CBC-MAC (ISO 9797-1 Alg 1)",
                "ISO-9797-1-ALG2",
                "ISO-9797-1-ALG4",
                "ISO-9797-1-ALG6",
                "CMAC-TDES (ISO 9797-1 Alg 5)",
                "CMAC-AES (ISO 9797-1 Alg 5)",
                "HMAC-SHA256",
                "AS2805.4 (1985)");
        macAlgorithmCombo.getSelectionModel().selectFirst();
    }

    // ==================== PIN BLOCK HANDLERS ====================

    public void handleEncodePinBlock() {
        try {
            String pin = pinField.getText().trim();
            String pan = panFieldEncode.getText().trim().replaceAll("\\s+", "");
            String format = pinBlockFormatCombo.getSelectionModel().getSelectedItem();

            // Validate inputs
            if (pin.isEmpty() || pan.isEmpty()) {
                pinBlockResultArea.setText(t("module.payments.error.pinPanRequired"));
                pinBlockResultArea.setManaged(true);
                pinBlockResultArea.setVisible(true);
                return;
            }

            if (!pin.matches("\\d{4,12}")) {
                pinBlockResultArea.setText(t("module.payments.error.pinLength"));
                pinBlockResultArea.setManaged(true);
                pinBlockResultArea.setVisible(true);
                return;
            }

            if (!pan.matches("\\d{13,19}")) {
                pinBlockResultArea.setText(t("module.payments.error.panInvalid"));
                pinBlockResultArea.setManaged(true);
                pinBlockResultArea.setVisible(true);
                return;
            }

            // For ISO-4, use special method that returns both clear field and PIN block
            String clearPinField = null;
            String clearPanBlock = null;
            String pinBlock;

            boolean isISO4 = format.contains("ISO 4") || format.contains("ISO-4");

            if (isISO4) {
                // Format 4 has no PIN block in clear: without the AES key the two
                // fields are the result, and the PIN field is what gets enciphered.
                String[] iso4Result = PaymentOperations.encodePinBlockISO4WithClear(pin, pan);
                clearPinField = iso4Result[0];
                clearPanBlock = iso4Result[1];
                pinBlock = clearPinField;
            } else {
                pinBlock = PaymentOperations.encodePinBlock(pin, pan, format);
            }

            // Display result
            StringBuilder result = new StringBuilder();
            result.append("========================================\n");
            result.append(t("module.payments.result.pinBlockEncodingTitle")).append("\n");
            result.append("========================================\n\n");
            result.append(t("module.payments.result.format")).append("    ").append(format).append("\n");
            result.append(t("module.payments.result.pin")).append("       ").append(pin).append(" (").append(t("module.payments.result.pinLength", pin.length())).append(")\n");
            result.append(t("module.payments.result.pan")).append("       ").append(pan).append("\n\n");

            // For ISO-4, show both clear blocks
            if (isISO4) {
                result.append(t("module.payments.result.pinBlockClear")).append(" ").append(clearPinField).append("\n");
                result.append(t("module.payments.result.panBlockClear")).append(" ").append(clearPanBlock).append("\n");
            } else {
                result.append(t("module.payments.result.pinBlock")).append(" ").append(pinBlock).append("\n");
            }
            result.append("========================================\n");

            pinBlockResultArea.setText(result.toString());
            pinBlockResultArea.setManaged(true);
            pinBlockResultArea.setVisible(true);
            java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
            details.put("Format", format);
            details.put("PAN", maskPan(pan));
            details.put("PIN Length", pin.length() + " digits");
            mainController.publish(OperationResult.forOperation("Encode PIN Block")
                    .output(DataConverter.hexToBytes(pinBlock)).details(details)
                    .status(t("module.payments.status.success")).build());

        } catch (Exception e) {
            pinBlockResultArea.setText(t("module.payments.error.operation", t("module.payments.result.pinBlockEncodingTitle"), e.getMessage()));
            pinBlockResultArea.setManaged(true);
            pinBlockResultArea.setVisible(true);
            updateStatus(t("module.payments.error.operation", t("module.payments.result.pinBlockEncodingTitle"), e.getMessage()));
        }
    }

    public void handleDecodePinBlock() {
        try {
            String pinBlock = pinBlockField.getText().trim().replaceAll("\\s+", "");
            String pan = panFieldDecode.getText().trim().replaceAll("\\s+", "");
            String format = pinBlockFormatDecodeCombo.getSelectionModel().getSelectedItem();

            // Validate inputs
            if (pinBlock.isEmpty() || pan.isEmpty()) {
                pinBlockResultArea.setText(t("module.payments.error.pinPanRequired"));
                pinBlockResultArea.setManaged(true);
                pinBlockResultArea.setVisible(true);
                return;
            }

            // Validate PIN block length based on format
            boolean isISO4 = format != null && (format.contains("ISO-4") || format.contains("ISO 4"));
            int expectedLength = isISO4 ? 32 : 16;

            if (!pinBlock.matches("[0-9A-Fa-f]{" + expectedLength + "}")) {
                pinBlockResultArea.setText(t("module.payments.error.pinBlockInvalid",
                        expectedLength, format, pinBlock.length()));
                pinBlockResultArea.setManaged(true);
                pinBlockResultArea.setVisible(true);
                return;
            }

            // Validate PAN format (13-19 digits OR 32 hex chars for ISO-4 block)
            boolean isValidPan = pan.matches("\\d{13,19}");
            boolean isValidIso4PanBlock = isISO4 && pan.matches("[0-9A-Fa-f]{32}");

            if (!isValidPan && !isValidIso4PanBlock) {
                pinBlockResultArea.setText(t("module.payments.error.panInvalid"));
                pinBlockResultArea.setManaged(true);
                pinBlockResultArea.setVisible(true);
                return;
            }

            // Decode PIN block
            String pin = PaymentOperations.decodePinBlock(pinBlock, pan, format);

            // Display result
            StringBuilder result = new StringBuilder();
            result.append("========================================\n");
            result.append(t("module.payments.result.pinBlockDecodingTitle")).append("\n");
            result.append("========================================\n\n");
            result.append(t("module.payments.result.format")).append("     ").append(format).append("\n");
            result.append(t("module.payments.result.pinBlock")).append("  ").append(pinBlock.toUpperCase()).append("\n");
            result.append(t("module.payments.result.pan")).append("        ").append(pan).append("\n\n");
            result.append(t("module.payments.result.decodedPin")).append(" ").append(pin).append(" (").append(t("module.payments.result.pinLength", pin.length())).append(")\n");
            result.append("========================================\n");

            pinBlockResultArea.setText(result.toString());
            pinBlockResultArea.setManaged(true);
            pinBlockResultArea.setVisible(true);
            java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
            details.put("Format", format);
            details.put("PAN", maskPan(pan));
            details.put("PIN Length", pin.length() + " digits");
            mainController.publish(OperationResult.forOperation("Decode PIN Block")
                    .input(DataConverter.hexToBytes(pinBlock))
                    .output(pin.getBytes(java.nio.charset.StandardCharsets.UTF_8)).details(details)
                    .status(t("module.payments.status.success")).build());

        } catch (Exception e) {
            pinBlockResultArea.setText(t("module.payments.error.operation", t("module.payments.result.pinBlockDecodingTitle"), e.getMessage()));
            pinBlockResultArea.setManaged(true);
            pinBlockResultArea.setVisible(true);
            updateStatus(t("module.payments.error.operation", t("module.payments.result.pinBlockDecodingTitle"), e.getMessage()));
        }
    }

    // ==================== CVV HANDLERS ====================

    public void handleGenerateCvv() {
        try {
            String cvkA = cvkAField.getText().trim().replaceAll("\\s+", "");
            String cvkB = cvkBField.getText().trim().replaceAll("\\s+", "");
            String pan = panFieldCvv.getText().trim().replaceAll("\\s+", "");
            String expiry = expiryDateField.getText().trim();
            String serviceCode = serviceCodeField.getText().trim();
            String atc = atcField.getText().trim();
            String cvvType = cvvTypeCombo.getSelectionModel().getSelectedItem();

            // Auto-populate Service Code if empty based on type
            if (serviceCode.isEmpty()) {
                if (cvvType != null) {
                    if (cvvType.contains("CVV2")) {
                        serviceCode = "000";
                        serviceCodeField.setText("000");
                    } else if (cvvType.contains("iCVV")) {
                        serviceCode = "000"; // Display 000 as per user preference/expert tool
                        serviceCodeField.setText("000");
                    }
                }
            }

            // Validate inputs
            if (cvkA.isEmpty() || cvkB.isEmpty() || pan.isEmpty() || expiry.isEmpty() || serviceCode.isEmpty()) {
                cvvResultArea.setText(t("module.payments.error.cvvRequired"));
                return;
            }

            if (!cvkA.matches("[0-9A-Fa-f]{16}")) {
                cvvResultArea.setText(t("module.payments.error.cvkAInvalid"));
                return;
            }

            if (!cvkB.matches("[0-9A-Fa-f]{16}")) {
                cvvResultArea.setText(t("module.payments.error.cvkBInvalid"));
                return;
            }

            if (!pan.matches("\\d{13,19}")) {
                cvvResultArea.setText(t("module.payments.error.panInvalid"));
                return;
            }

            if (!expiry.matches("\\d{4}")) {
                cvvResultArea.setText(t("module.payments.error.expiryInvalid"));
                return;
            }

            if (!serviceCode.matches("\\d{3}")) {
                cvvResultArea.setText(t("module.payments.error.serviceCodeInvalid"));
                return;
            }

            // Generate CVV
            String cvv;
            String serviceCodeForCalc = serviceCode;
            if (cvvType != null && cvvType.contains("dCVV")) {
                if (atc.isEmpty()) {
                    cvvResultArea.setText(t("module.payments.error.atcRequired"));
                    return;
                }
                if (!atc.matches("[0-9A-Fa-f]{1,4}")) {
                    cvvResultArea.setText(t("module.payments.error.atcInvalid"));
                    return;
                }
                // CVK A || CVK B is the issuer MDK; the card key is derived with PSN 00.
                cvv = PaymentOperations.generateDCVV(cvkA, cvkB, pan, "00", expiry, serviceCode, atc);
            } else { // Standard CVV, CVV2, iCVV
                if (cvvType != null && cvvType.contains("iCVV")) {
                    // iCVV always uses 999 for calculation, regardless of magnetic stripe service
                    // code
                    serviceCodeForCalc = "999";
                } else if (cvvType != null && cvvType.contains("CVV2")) {
                    // CVV2 always uses 000 for calculation
                    serviceCodeForCalc = "000";
                }
                cvv = PaymentOperations.generateCVV(cvkA, cvkB, pan, expiry, serviceCodeForCalc);
            }

            // Display result
            StringBuilder result = new StringBuilder();
            result.append("═══ ").append(t("module.payments.result.cvvGenerationTitle")).append(" ═══\n\n");
            result.append(t("module.payments.result.type")).append("         ").append(cvvType);
            if (cvvType != null && cvvType.contains("dCVV")) {
                result.append(" (Visa CVN 10)");
            }
            result.append("\n");

            result.append(t("module.payments.result.cvkA")).append("        ").append(cvkA.toUpperCase()).append("\n");
            result.append(t("module.payments.result.cvkB")).append("        ").append(cvkB.toUpperCase()).append("\n");
            result.append(t("module.payments.result.pan")).append("          ").append(pan).append("\n");
            result.append(t("module.payments.result.expiry")).append("       ").append(expiry).append("\n");

            // Always show Service Code, but note usage
            result.append(t("module.payments.result.serviceCode")).append(" ").append(serviceCode);
            if (cvvType != null) {
                if (cvvType.contains("CVV2") || cvvType.contains("iCVV")) {
                    result.append(" ").append(t("module.payments.result.forcedCalculation", serviceCodeForCalc));
                } else if (cvvType.contains("dCVV")) {
                    result.append(" ").append(t("module.payments.result.notUsedDcvv"));
                }
            }
            result.append("\n");

            if (!atc.isEmpty() || (cvvType != null && cvvType.contains("dCVV"))) {
                result.append(t("module.payments.result.atc")).append("          ").append(atc)
                        .append(cvvType.contains("dCVV") ? " " + t("module.payments.result.usedDcvv") : " " + t("module.payments.result.notUsedStatic") + "\n");
            }
            result.append("\n");
            result.append(t("module.payments.result.cvv")).append("          ").append(cvv).append("\n");

            cvvResultArea.setText(result.toString());
            java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
            details.put("Type", cvvType);
            details.put("PAN", maskPan(pan));
            details.put("Expiry", expiry);
            details.put("Service Code", serviceCode);
            mainController.publish(OperationResult.forOperation("Generate CVV")
                    .output(cvv.getBytes(java.nio.charset.StandardCharsets.UTF_8)).details(details)
                    .status(t("module.payments.status.success")).build());

        } catch (Exception e) {
            cvvResultArea.setText(t("module.payments.error.operation", t("module.payments.result.cvvGenerationTitle"), e.getMessage()));
            updateStatus(t("module.payments.error.operation", t("module.payments.result.cvvGenerationTitle"), e.getMessage()));
        }
    }

    public void handleVerifyCvv() {
        try {
            String cvkA = cvkAField.getText().trim().replaceAll("\\s+", "");
            String cvkB = cvkBField.getText().trim().replaceAll("\\s+", "");
            String pan = panFieldCvv.getText().trim().replaceAll("\\s+", "");
            String expiry = expiryDateField.getText().trim();
            String serviceCode = serviceCodeField.getText().trim();

            // Use the result area text as "input" CVV if it looks like a CVV,
            // otherwise prompt or expect user to put it somewhere?
            // For now, let's assume verification matches the Generated one re-calculated.
            // Better: Add a dialog or assume the user compares it visually?
            // "Verify" usually implies taking an input CVV and checking it.
            // But we don't have a specific "Input CVV to Verify" field.
            // We can add a TextInputDialog.

            if (cvkA.isEmpty() || cvkB.isEmpty() || pan.isEmpty() || expiry.isEmpty() || serviceCode.isEmpty()) {
                cvvResultArea.setText(t("module.payments.error.cvvRequired"));
                return;
            }
            if (!cvkA.matches("[0-9A-Fa-f]{16}")) {
                cvvResultArea.setText(t("module.payments.error.cvkAInvalid"));
                return;
            }
            if (!cvkB.matches("[0-9A-Fa-f]{16}")) {
                cvvResultArea.setText(t("module.payments.error.cvkBInvalid"));
                return;
            }
            if (!pan.matches("\\d{13,19}")) {
                cvvResultArea.setText(t("module.payments.error.panInvalid"));
                return;
            }
            if (!expiry.matches("\\d{4}")) {
                cvvResultArea.setText(t("module.payments.error.expiryInvalid"));
                return;
            }
            if (!serviceCode.matches("\\d{3}")) {
                cvvResultArea.setText(t("module.payments.error.serviceCodeInvalid"));
                return;
            }

            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle(t("module.payments.dialog.verifyCvvTitle"));
            dialog.setHeaderText(t("module.payments.dialog.verifyCvvHeader"));
            dialog.setContentText(t("module.payments.dialog.cvv"));

            java.util.Optional<String> outcome = dialog.showAndWait();
            if (outcome.isPresent()) {
                String inputCvv = outcome.get().trim();
                String atc = atcField.getText().trim();

                boolean isValid;
                String calculated;

                if (cvvTypeCombo.getSelectionModel().getSelectedItem() != null &&
                        cvvTypeCombo.getSelectionModel().getSelectedItem().contains("dCVV")) {

                    if (atc.isEmpty()) {
                        cvvResultArea.setText(t("module.payments.error.atcRequired"));
                        return;
                    }
                    if (!atc.matches("[0-9A-Fa-f]{1,4}")) {
                        cvvResultArea.setText(t("module.payments.error.atcInvalid"));
                        return;
                    }
                    isValid = PaymentOperations.verifyDCVV(cvkA, cvkB, pan, "00", expiry, serviceCode, atc, inputCvv);
                    calculated = PaymentOperations.generateDCVV(cvkA, cvkB, pan, "00", expiry, serviceCode, atc);

                } else {
                    String serviceCodeForCalc = serviceCode;
                    if (cvvTypeCombo.getSelectionModel().getSelectedItem() != null &&
                            cvvTypeCombo.getSelectionModel().getSelectedItem().contains("iCVV")) {
                        serviceCodeForCalc = "999";
                    } else if (cvvTypeCombo.getSelectionModel().getSelectedItem() != null &&
                            cvvTypeCombo.getSelectionModel().getSelectedItem().contains("CVV2")) {
                        serviceCodeForCalc = "000";
                    }

                    isValid = PaymentOperations.verifyCVV(cvkA, cvkB, pan, expiry, serviceCodeForCalc, inputCvv);
                    calculated = PaymentOperations.generateCVV(cvkA, cvkB, pan, expiry, serviceCodeForCalc);
                }

                StringBuilder result = new StringBuilder();
                result.append("═══ ").append(t("module.payments.result.cvvVerificationTitle")).append(" ═══\n\n");
                result.append(t("module.payments.result.inputCvv")).append("    ").append(inputCvv).append("\n");
                result.append(t("module.payments.result.calculated")).append("   ").append(calculated).append("\n\n");
                result.append(t("module.payments.result.result")).append("       ").append(t(isValid ? "module.payments.result.matchSymbol" : "module.payments.result.mismatchSymbol")).append("\n");

                cvvResultArea.setText(result.toString());
                java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
                details.put("Type", cvvTypeCombo.getSelectionModel().getSelectedItem());
                details.put("PAN", maskPan(pan));
                details.put("Result", isValid ? "VALID" : "INVALID");
                mainController.publish(OperationResult.forOperation("Verify CVV")
                        .output(calculated.getBytes(java.nio.charset.StandardCharsets.UTF_8)).details(details)
                        .status(t(isValid ? "module.payments.status.cvvValid" : "module.payments.status.cvvInvalid")).build());
            }

        } catch (Exception e) {
            cvvResultArea.setText(t("module.payments.error.operation", t("module.payments.result.cvvVerificationTitle"), e.getMessage()));
            updateStatus(t("module.payments.error.operation", t("module.payments.result.cvvVerificationTitle"), e.getMessage()));
        }
    }

    private String maskPan(String pan) {
        if (pan == null || pan.length() < 5) return "[redacted]";
        return "*".repeat(Math.max(0, pan.length() - 4)) + pan.substring(pan.length() - 4);
    }

    // ==================== MAC HANDLERS ====================

    public void handleGenerateMac() {
        try {
            String algorithm = macAlgorithmCombo.getSelectionModel().getSelectedItem();
            String macKey = macKeyField.getText().trim().replaceAll("\\s+", "");
            String data = macDataField.getText().trim().replaceAll("\\s+", "");

            // Validate inputs
            if (macKey.isEmpty() || data.isEmpty()) {
                macResultArea.setText(t("module.payments.error.macRequired"));
                return;
            }

            if (!macKey.matches("[0-9A-Fa-f]{32}")) {
                macResultArea.setText(t("module.payments.error.macKeyInvalid"));
                return;
            }

            if (!data.matches("[0-9A-Fa-f]+")) {
                macResultArea.setText(t("module.payments.error.macDataHex"));
                return;
            }

            // Generate MAC
            String mac = PaymentOperations.generateMAC(macKey, data, algorithm);

            // Display result
            StringBuilder result = new StringBuilder();
            result.append("========================================\n");
            result.append(t("module.payments.result.macGenerationTitle")).append("\n");
            result.append("========================================\n\n");
            result.append(t("module.common.algorithm")).append(" ").append(algorithm).append("\n");
            result.append("Key:       ").append(macKey.toUpperCase()).append("\n");
            result.append("Data:      ").append(data.toUpperCase()).append("\n");
            result.append("           (").append(data.length() / 2).append(" bytes)\n\n");
            result.append("MAC:       ").append(mac).append("\n");
            result.append("========================================\n");

            macResultArea.setText(result.toString());
            updateStatus(t("module.payments.status.success"));

        } catch (Exception e) {
            macResultArea.setText(t("module.payments.error.operation", t("module.payments.result.macGenerationTitle"), e.getMessage()));
            updateStatus(t("module.payments.error.operation", t("module.payments.result.macGenerationTitle"), e.getMessage()));
        }
    }

    public void handleVerifyMac() {
        macResultArea.setText(t("module.payments.status.macVerificationComingSoon"));
        updateStatus(t("module.payments.status.comingSoon"));
    }

    // ==================== NEW ADVANCED FEATURES ====================

    // PIN Translation fields (will be added to FXML)
    private TextField pinTransSourceBlockField;
    private TextField pinTransPanField;
    private ComboBox<String> pinTransSourceFormatCombo;
    private ComboBox<String> pinTransTargetFormatCombo;
    private TextArea pinTransResultArea;

    // PVV fields
    private TextField pvvPinField;
    private TextField pvvPanField;
    private TextField pvvKeyField;
    private TextField pvvLengthField;
    private TextField pvvValueField; // For verification
    private TextArea pvvResultArea;

    // Track Data fields
    private TextField trackPanField;
    private TextField trackNameField;
    private TextField trackExpiryField;
    private TextField trackServiceCodeField;
    private TextField trackDiscretionaryField;
    private TextArea trackDataField; // For parsing
    private TextArea trackResultArea;

    /**
     * Initialize advanced Payments features
     */
    public void initializeAdvancedFeatures(
            // PIN Translation
            TextField pinTransSourceBlockField,
            TextField pinTransPanField,
            ComboBox<String> pinTransSourceFormatCombo,
            ComboBox<String> pinTransTargetFormatCombo,
            TextArea pinTransResultArea,
            // PVV
            TextField pvvPinField,
            TextField pvvPanField,
            TextField pvvKeyField,
            TextField pvvLengthField,
            TextField pvvValueField,
            TextArea pvvResultArea,
            // Track Data
            TextField trackPanField,
            TextField trackNameField,
            TextField trackExpiryField,
            TextField trackServiceCodeField,
            TextField trackDiscretionaryField,
            TextArea trackDataField,
            TextArea trackResultArea) {

        // PIN Translation
        this.pinTransSourceBlockField = pinTransSourceBlockField;
        this.pinTransPanField = pinTransPanField;
        this.pinTransSourceFormatCombo = pinTransSourceFormatCombo;
        this.pinTransTargetFormatCombo = pinTransTargetFormatCombo;
        this.pinTransResultArea = pinTransResultArea;

        // PVV
        this.pvvPinField = pvvPinField;
        this.pvvPanField = pvvPanField;
        this.pvvKeyField = pvvKeyField;
        this.pvvLengthField = pvvLengthField;
        this.pvvValueField = pvvValueField;
        this.pvvResultArea = pvvResultArea;

        // Track Data
        this.trackPanField = trackPanField;
        this.trackNameField = trackNameField;
        this.trackExpiryField = trackExpiryField;
        this.trackServiceCodeField = trackServiceCodeField;
        this.trackDiscretionaryField = trackDiscretionaryField;
        this.trackDataField = trackDataField;
        this.trackResultArea = trackResultArea;

        // Setup combo boxes
        if (pinTransSourceFormatCombo != null) {
            pinTransSourceFormatCombo.getItems().addAll(
                    "Format 0 (ISO-0)", "Format 1 (ISO-1)", "Format 2 (ISO-2)",
                    "Format 3 (ISO-3)", "Format 4 (ISO-4)", "ANSI X9.8",
                    "IBM 3624", "VISA-1");
            pinTransSourceFormatCombo.getSelectionModel().selectFirst();
        }

        if (pinTransTargetFormatCombo != null) {
            pinTransTargetFormatCombo.getItems().addAll(
                    "Format 0 (ISO-0)", "Format 1 (ISO-1)", "Format 2 (ISO-2)",
                    "Format 3 (ISO-3)", "Format 4 (ISO-4)", "ANSI X9.8",
                    "IBM 3624", "VISA-1");
            pinTransTargetFormatCombo.getSelectionModel().select(1); // Default to Format 1
        }

        if (pvvLengthField != null) {
            pvvLengthField.setText("4");
        }
    }

    /**
     * Initialize IBM 3624 PIN Generation controls
     */
    public void initializeIbm3624Controls(
            TextField ibm3624PvkField,
            TextField ibm3624ConvTableField,
            TextField ibm3624OffsetField,
            TextField ibm3624PanField,
            TextField ibm3624PinVerifyField,
            TextArea ibm3624ResultArea,
            TextField ibm3624StartField,
            TextField ibm3624LengthField,
            TextField ibm3624PadField) {

        this.ibm3624PvkField = ibm3624PvkField;
        this.ibm3624ConvTableField = ibm3624ConvTableField;
        this.ibm3624OffsetField = ibm3624OffsetField;
        this.ibm3624PanField = ibm3624PanField;
        this.ibm3624PinVerifyField = ibm3624PinVerifyField;
        this.ibm3624ResultArea = ibm3624ResultArea;
        this.ibm3624StartField = ibm3624StartField;
        this.ibm3624LengthField = ibm3624LengthField;
        this.ibm3624PadField = ibm3624PadField;

        // Set default values if fields are loaded
        if (ibm3624ConvTableField != null) {
            ibm3624ConvTableField.setText("0123456789012345");
        }
    }

    // ==================== PIN TRANSLATION HANDLERS ====================

    public void handleTranslatePinBlock() {
        try {
            String sourceBlock = pinTransSourceBlockField.getText().trim().replaceAll("\\s", "");
            String pan = pinTransPanField.getText().trim().replaceAll("\\s", "");
            String sourceFormat = pinTransSourceFormatCombo.getValue();
            String targetFormat = pinTransTargetFormatCombo.getValue();

            if (sourceBlock.isEmpty() || pan.isEmpty()) {
                pinTransResultArea.setText(t("module.payments.error.pinBlockPanRequired"));
                return;
            }

            if (sourceFormat.equals(targetFormat)) {
                pinTransResultArea.setText(t("module.payments.error.samePinFormats"));
                return;
            }

            // Perform translation with details
            String details = PaymentOperations.getTranslationDetails(sourceBlock, pan, sourceFormat, targetFormat);
            pinTransResultArea.setText(details);

            // Add to history
            if (mainController != null) {
                mainController.publish(com.cryptocarver.model.OperationResult.forOperation("Translate " + sourceFormat + " → " + targetFormat)
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", "Block: " + sourceBlock + ", PAN: " + pan, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", PaymentOperations.translatePinBlock(sourceBlock, pan, sourceFormat, targetFormat), com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null)
                    ))
                    .build());
            }

            updateStatus(t("module.payments.status.success"));

        } catch (Exception e) {
            pinTransResultArea.setText(t("module.payments.error.operation", t("module.payments.operation.pinTranslation"), e.getMessage()));
            updateStatus(t("module.payments.error.operation", t("module.payments.operation.pinTranslation"), e.getMessage()));
        }
    }

    // ==================== PVV HANDLERS ====================

    public void handleGeneratePVV() {
        try {
            String pin = pvvPinField.getText().trim();
            String pan = pvvPanField.getText().trim().replaceAll("\\s", "");
            String pvk = pvvKeyField.getText().trim().replaceAll("\\s", "");
            String lengthStr = pvvLengthField.getText().trim();

            if (pin.isEmpty() || pan.isEmpty() || pvk.isEmpty()) {
                pvvResultArea.setText(t("module.payments.error.pvvRequired"));
                return;
            }

            int pvvLength = 4; // Default
            if (!lengthStr.isEmpty() && !lengthStr.matches("\\d+")) {
                pvvResultArea.setText(t("module.payments.error.pvvFormatInvalid"));
                return;
            }
            if (!lengthStr.isEmpty()) {
                pvvLength = Integer.parseInt(lengthStr);
            }

            // Generate PVV with details
            String details = PaymentOperations.getPVVDetails(pin, pan, pvk, "0", pvvLength);
            pvvResultArea.setText(details);

            // Also set the PVV value for verification
            String pvv = PaymentOperations.generatePVV(pin, pan, pvk, "0", pvvLength);
            pvvValueField.setText(pvv);

            // Add to history
            if (mainController != null) {
                mainController.publish(com.cryptocarver.model.OperationResult.forOperation("Generate PVV")
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", "PIN: [HIDDEN], PAN: " + pan, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", "PVV: " + pvv, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null)
                    ))
                    .build());
            }

            updateStatus(t("module.payments.status.success"));

        } catch (Exception e) {
            pvvResultArea.setText(t("module.payments.error.operation", t("module.payments.operation.pvvGeneration"), e.getMessage()));
            updateStatus(t("module.payments.error.operation", t("module.payments.operation.pvvGeneration"), e.getMessage()));
        }
    }

    public void handleVerifyPVV() {
        try {
            String pin = pvvPinField.getText().trim();
            String pan = pvvPanField.getText().trim().replaceAll("\\s", "");
            String pvk = pvvKeyField.getText().trim().replaceAll("\\s", "");
            String pvvToVerify = pvvValueField.getText().trim();
            String lengthStr = pvvLengthField.getText().trim();

            if (pin.isEmpty() || pan.isEmpty() || pvk.isEmpty() || pvvToVerify.isEmpty()) {
                pvvResultArea.setText(t("module.payments.error.pvvRequired"));
                return;
            }

            int pvvLength = pvvToVerify.length();
            if (!pvvToVerify.matches("\\d+")) {
                pvvResultArea.setText(t("module.payments.error.pvvFormatInvalid"));
                return;
            }
            if (!lengthStr.isEmpty() && !lengthStr.matches("\\d+")) {
                pvvResultArea.setText(t("module.payments.error.pvvFormatInvalid"));
                return;
            }
            if (!lengthStr.isEmpty()) {
                pvvLength = Integer.parseInt(lengthStr);
            }

            // Generate and verify
            String generatedPVV = PaymentOperations.generatePVV(pin, pan, pvk, "0", pvvLength);
            boolean isValid = generatedPVV.equals(pvvToVerify);

            StringBuilder result = new StringBuilder();
            result.append("═══ ").append(t("module.payments.result.pvvVerificationTitle")).append(" ═══\n\n");
            result.append(t("module.payments.result.generatedPvv")).append(" ").append(generatedPVV).append("\n");
            result.append(t("module.payments.result.providedPvv")).append("  ").append(pvvToVerify).append("\n\n");
            result.append(t("module.payments.result.result")).append(" ").append(t(isValid ? "module.payments.result.validSymbol" : "module.payments.result.invalidSymbol")).append("\n");

            pvvResultArea.setText(result.toString());

            // Add to history
            if (mainController != null) {
                mainController.publish(com.cryptocarver.model.OperationResult.forOperation("Verify PVV")
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", "PAN: " + pan + ", PVV: " + pvvToVerify, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", isValid ? "VALID" : "INVALID", com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null)
                    ))
                    .build());
            }

            updateStatus(t(isValid ? "module.payments.status.valid" : "module.payments.status.invalid"));

        } catch (Exception e) {
            pvvResultArea.setText(t("module.payments.error.operation", t("module.payments.operation.pvvVerification"), e.getMessage()));
            updateStatus(t("module.payments.error.operation", t("module.payments.operation.pvvVerification"), e.getMessage()));
        }
    }

    // ==================== TRACK DATA HANDLERS ====================

    public void handleEncodeTrack1() {
        try {
            String pan = trackPanField.getText().trim().replaceAll("\\s", "");
            String name = trackNameField.getText().trim();
            String expiry = trackExpiryField.getText().trim();
            String serviceCode = trackServiceCodeField.getText().trim();
            String discretionary = trackDiscretionaryField.getText().trim();

            if (pan.isEmpty() || name.isEmpty() || expiry.isEmpty() || serviceCode.isEmpty()) {
                trackResultArea.setText(t("module.payments.error.trackRequired"));
                return;
            }
            if (!pan.matches("\\d{13,19}")) {
                trackResultArea.setText(t("module.payments.error.panInvalid"));
                return;
            }
            if (!expiry.matches("\\d{4}")) {
                trackResultArea.setText(t("module.payments.error.expiryInvalid"));
                return;
            }
            if (!serviceCode.matches("\\d{3}")) {
                trackResultArea.setText(t("module.payments.error.serviceCodeInvalid"));
                return;
            }

            String track1 = PaymentOperations.encodeTrack1(pan, name, expiry, serviceCode, discretionary);

            StringBuilder result = new StringBuilder();
            result.append("═══ ").append(t("module.payments.result.track1EncodedTitle")).append(" ═══\n\n");
            result.append(t("module.payments.result.track1")).append(" ").append(track1).append("\n\n");
            result.append(t("module.payments.result.length", track1.length())).append("\n");
            result.append(t("module.payments.result.isoTrackFormat", "1")).append("\n");

            trackResultArea.setText(result.toString());

            // Set to track data field for parsing
            trackDataField.setText(track1);

            // Add to history
            if (mainController != null) {
                mainController.publish(com.cryptocarver.model.OperationResult.forOperation("Encode Track 1")
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", "PAN: " + pan + ", Name: " + name, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", track1, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null)
                    ))
                    .build());
            }

            updateStatus(t("module.payments.status.success"));

        } catch (Exception e) {
            trackResultArea.setText(t("module.payments.error.operation", t("module.payments.result.track1EncodedTitle"), e.getMessage()));
            updateStatus(t("module.payments.error.operation", t("module.payments.result.track1EncodedTitle"), e.getMessage()));
        }
    }

    public void handleEncodeTrack2() {
        try {
            String pan = trackPanField.getText().trim().replaceAll("\\s", "");
            String expiry = trackExpiryField.getText().trim();
            String serviceCode = trackServiceCodeField.getText().trim();
            String discretionary = trackDiscretionaryField.getText().trim();

            if (pan.isEmpty() || expiry.isEmpty() || serviceCode.isEmpty()) {
                trackResultArea.setText(t("module.payments.error.trackRequired"));
                return;
            }
            if (!pan.matches("\\d{13,19}")) {
                trackResultArea.setText(t("module.payments.error.panInvalid"));
                return;
            }
            if (!expiry.matches("\\d{4}")) {
                trackResultArea.setText(t("module.payments.error.expiryInvalid"));
                return;
            }
            if (!serviceCode.matches("\\d{3}")) {
                trackResultArea.setText(t("module.payments.error.serviceCodeInvalid"));
                return;
            }

            String track2 = PaymentOperations.encodeTrack2(pan, expiry, serviceCode, discretionary);

            StringBuilder result = new StringBuilder();
            result.append("═══ ").append(t("module.payments.result.track2EncodedTitle")).append(" ═══\n\n");
            result.append(t("module.payments.result.track2")).append(" ").append(track2).append("\n");
            result.append(t("module.payments.result.track2Hex")).append(" ").append(PaymentOperations.track2ToHex(track2)).append("\n\n");
            result.append(t("module.payments.result.length", track2.length())).append("\n");
            result.append(t("module.payments.result.isoTrackFormat", "2")).append("\n");

            trackResultArea.setText(result.toString());

            // Set to track data field for parsing
            trackDataField.setText(track2);

            // Add to history
            if (mainController != null) {
                mainController.publish(com.cryptocarver.model.OperationResult.forOperation("Encode Track 2")
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", "PAN: " + pan, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", track2, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null)
                    ))
                    .build());
            }

            updateStatus(t("module.payments.status.success"));

        } catch (Exception e) {
            trackResultArea.setText(t("module.payments.error.operation", t("module.payments.result.track2EncodedTitle"), e.getMessage()));
            updateStatus(t("module.payments.error.operation", t("module.payments.result.track2EncodedTitle"), e.getMessage()));
        }
    }

    public void handleParseTrackData() {
        try {
            String trackData = trackDataField.getText().trim();

            if (trackData.isEmpty()) {
                trackResultArea.setText(t("module.payments.error.trackRequired"));
                return;
            }

            String result;
            if (trackData.startsWith("%B")) {
                result = PaymentOperations.parseTrack1(trackData);
            } else if (trackData.startsWith(";")) {
                result = PaymentOperations.parseTrack2(trackData);
            } else {
                result = t("module.payments.error.trackFormatInvalid");
            }

            trackResultArea.setText(result);

            // Add to history
            if (mainController != null) {
                mainController.publish(com.cryptocarver.model.OperationResult.forOperation("Parse Track Data")
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", trackData.substring(0, Math.min(50, trackData.length())) + "...", com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", t("module.payments.status.parsedSuccessfully"), com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null)
                    ))
                    .build());
            }

            updateStatus(t("module.payments.status.success"));

        } catch (Exception e) {
            trackResultArea.setText(t("module.payments.error.operation", "Track parsing", e.getMessage()));
            updateStatus(t("module.payments.error.operation", "Track parsing", e.getMessage()));
        }
    }

    // ============================================================
    // ENCRYPTED PIN BLOCK OPERATIONS (Generic)
    // ============================================================

    private static boolean isIso4(String format) {
        return format != null && (format.contains("ISO-4") || format.contains("ISO 4"));
    }

    public void handleEncodeEncryptedPinBlock() {
        try {
            if (encPinField == null || encPanFieldEncode == null || encResultArea == null) {
                showError(t("module.payments.error.configurationTitle"), t("module.payments.error.controlsNotInitialized", "Encrypted PIN"));
                return;
            }

            String pin = encPinField.getText().trim();
            String pan = encPanFieldEncode.getText().trim().replaceAll("\\s+", "");
            String keyHex = encPinBlockKeyField != null ? encPinBlockKeyField.getText().trim().replaceAll("\\s+", "")
                    : "";
            String format = encPinBlockFormatCombo.getSelectionModel().getSelectedItem();

            if (pin.isEmpty()) {
                showError(t("module.payments.error.inputTitle"), t("module.payments.error.enterValue", "PIN"), "encPinField");
                return;
            }
            // Some formats might not need PAN, but mostly they do for XOR or binding
            if (pan.isEmpty() && (format.contains("ISO-0") || format.contains("ISO-3"))) {
                showError(t("module.payments.error.inputTitle"), t("module.payments.error.enterPanForFormat", format), "encPanFieldEncode");
                return;
            }

            // 1. Create clear PIN block using PaymentOperations (which supports all
            // formats)
            String clearPinBlock = PaymentOperations.encodePinBlock(pin, pan, format);
            String publishedBlock = clearPinBlock;

            String result = t("module.payments.result.format") + " " + format + "\n";
            result += t("module.payments.result.clearPinBlock") + "\n" + clearPinBlock;

            // 2. Encrypt if key provided
            if (!keyHex.isEmpty()) {
                try {
                    byte[] key = DataConverter.hexToBytes(keyHex);
                    if (isIso4(format)) {
                        // Format 4 is AES and binds the PAN between two encryptions.
                        publishedBlock = PaymentOperations.encipherPinBlockISO4(key, pin, pan);
                    } else {
                        byte[] clearBytes = DataConverter.hexToBytes(clearPinBlock);
                        byte[] encrypted = PaymentOperations.encryptDesEcb(clearBytes, key);
                        publishedBlock = DataConverter.bytesToHex(encrypted).toUpperCase();
                    }
                    result += "\n\n" + t("module.payments.result.encryptedPinBlock") + "\n" + publishedBlock;
                } catch (Exception e) {
                    result += "\n\nEncryption Error: " + e.getMessage();
                }
            }

            encResultArea.setText(result);
            encResultArea.setManaged(true);
            encResultArea.setVisible(true);

            java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
            details.put("Format", format);
            details.put("PAN", maskPan(pan));
            details.put("PIN", "[not persisted]");
            details.put("Protected", keyHex.isEmpty() ? "No key supplied" : isIso4(format) ? "AES (ISO 9564-1 format 4)" : "TDES ECB");
            mainController.publish(OperationResult.forOperation("Encode Encrypted PIN Block")
                    .output(DataConverter.hexToBytes(publishedBlock)).details(details)
                    .status(t("module.payments.status.success")).build());

        } catch (Exception e) {
            showError(t("module.payments.error.encodingTitle"), t("module.payments.error.operation", t("module.payments.result.encryptedPinBlock"), e.getMessage()));
            LOG.error("Encrypted PIN block encoding failed", e);
        }
    }

    public void handleDecodeEncryptedPinBlock() {
        try {
            if (encPinBlockFieldDecode == null || encPanFieldDecode == null || encResultArea == null) {
                showError(t("module.payments.error.configurationTitle"), t("module.payments.error.controlsNotInitialized", "Encrypted PIN decode"));
                return;
            }

            String pinBlockHex = encPinBlockFieldDecode.getText().trim().replaceAll("\\s+", "");
            String pan = encPanFieldDecode.getText().trim().replaceAll("\\s+", "");
            String keyHex = encPinBlockKeyFieldDecode != null
                    ? encPinBlockKeyFieldDecode.getText().trim().replaceAll("\\s+", "")
                    : "";
            String format = encPinBlockFormatCombo.getSelectionModel().getSelectedItem();

            if (pinBlockHex.isEmpty()) {
                showError(t("module.payments.error.inputTitle"), t("module.payments.error.enterValue", "PIN Block"), "encPinBlockFieldDecode");
                return;
            }

            String clearPinBlockHex = pinBlockHex;

            if (isIso4(format) && !keyHex.isEmpty()) {
                String pin;
                try {
                    pin = PaymentOperations.decipherPinBlockISO4(DataConverter.hexToBytes(keyHex), pinBlockHex, pan);
                } catch (Exception e) {
                    showError(t("module.payments.error.decryptionTitle"), t("module.payments.error.operation", t("module.payments.operation.encryptedPinBlock"), e.getMessage()));
                    return;
                }
                encResultArea.setText(t("module.payments.result.format") + " " + format + "\n\n"
                        + t("module.payments.result.decodedPinLine") + " " + pin);
                encResultArea.setManaged(true);
                encResultArea.setVisible(true);
                java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
                details.put("Format", format);
                details.put("PAN", maskPan(pan));
                details.put("PIN", "[not persisted]");
                details.put("Protected", "AES (ISO 9564-1 format 4)");
                mainController.publish(OperationResult.forOperation("Decode Encrypted PIN Block")
                        .input(DataConverter.hexToBytes(pinBlockHex))
                        .output(pin.getBytes(java.nio.charset.StandardCharsets.UTF_8)).details(details)
                        .status(t("module.payments.status.success")).build());
                return;
            }

            // 1. Decrypt if key provided
            if (!keyHex.isEmpty()) {
                try {
                    byte[] key = DataConverter.hexToBytes(keyHex);
                    byte[] encrypted = DataConverter.hexToBytes(pinBlockHex);

                    // Decrypt with TDES
                    byte[] decrypted = PaymentOperations.decryptDesEcb(encrypted, key);
                    clearPinBlockHex = DataConverter.bytesToHex(decrypted).toUpperCase();
                } catch (Exception e) {
                    showError(t("module.payments.error.decryptionTitle"), t("module.payments.error.operation", t("module.payments.operation.encryptedPinBlock"), e.getMessage()));
                    return;
                }
            }

            // 2. Decode PIN block using PaymentOperations
            String pin = PaymentOperations.decodePinBlock(clearPinBlockHex, pan, format);

            String result = t("module.payments.result.format") + " " + format + "\n";
            result += t("module.payments.result.clearPinBlock") + " " + clearPinBlockHex + "\n\n" + t("module.payments.result.decodedPinLine") + " " + pin;

            encResultArea.setText(result);
            encResultArea.setManaged(true);
            encResultArea.setVisible(true);

            java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
            details.put("Format", format);
            details.put("PAN", maskPan(pan));
            details.put("PIN", "[not persisted]");
            details.put("Protected", keyHex.isEmpty() ? "No key supplied" : "TDES ECB");
            mainController.publish(OperationResult.forOperation("Decode Encrypted PIN Block")
                    .input(DataConverter.hexToBytes(pinBlockHex))
                    .output(pin.getBytes(java.nio.charset.StandardCharsets.UTF_8)).details(details)
                    .status(t("module.payments.status.success")).build());

        } catch (Exception e) {
            showError(t("module.payments.error.decodingTitle"), t("module.payments.error.operation", t("module.payments.operation.encryptedPinBlock"), e.getMessage()));
            LOG.error("Encrypted PIN block decoding failed", e);
        }
    }

    // ============================================================
    // IBM 3624 PIN OPERATIONS
    // ============================================================

    public void handleGenerateIbm3624Pin() {
        try {
            if (ibm3624PvkField == null || ibm3624OffsetField == null || ibm3624PanField == null
                    || ibm3624ResultArea == null) {
                showError(t("module.payments.error.configurationTitle"), t("module.payments.error.controlsNotInitialized", "IBM 3624"));
                return;
            }

            String pvkHex = ibm3624PvkField.getText().trim();
            String convTable = ibm3624ConvTableField != null ? ibm3624ConvTableField.getText().trim()
                    : "0123456789012345";
            String offset = ibm3624OffsetField.getText().trim();
            String pan = ibm3624PanField.getText().trim();

            if (pvkHex.isEmpty() || offset.isEmpty() || pan.isEmpty()) {
                showError(t("module.payments.error.inputTitle"), t("module.payments.error.pvkOffsetPanRequired"), "ibm3624PvkField");
                return;
            }

            // Convert PVK to bytes
            byte[] pvk = DataConverter.hexToBytes(pvkHex);

            // Parse configuration
            int startPos = 0;
            int length = 12; // Default for simpler IBM 3624
            String padChar = "0";

            if (ibm3624StartField != null && !ibm3624StartField.getText().trim().isEmpty()) {
                try {
                    startPos = Integer.parseInt(ibm3624StartField.getText().trim());
                    // Convert 1-based start position to 0-based index
                    if (startPos > 0)
                        startPos--;
                } catch (NumberFormatException e) {
                    showError(t("module.payments.error.inputTitle"), t("module.payments.error.invalidStartPosition"));
                    return;
                }
            }

            if (ibm3624LengthField != null && !ibm3624LengthField.getText().trim().isEmpty()) {
                try {
                    length = Integer.parseInt(ibm3624LengthField.getText().trim());
                } catch (NumberFormatException e) {
                    showError(t("module.payments.error.inputTitle"), t("module.payments.error.invalidLength"));
                    return;
                }
            }

            if (ibm3624PadField != null && !ibm3624PadField.getText().trim().isEmpty()) {
                padChar = ibm3624PadField.getText().trim().substring(0, 1);
            }

            // Generate PIN using IBM 3624 method
            String pin = com.cryptocarver.pin.Pin.generateIbm3624Pin(
                    pvk,
                    convTable,
                    offset,
                    pan,
                    startPos,
                    length,
                    padChar);

            // Reconstruct Validation Data Block for display (Debugging feedback)
            String rawVd = "";
            try {
                if (pan.length() >= startPos + length) {
                    rawVd = pan.substring(startPos, startPos + length);
                } else {
                    rawVd = "Error: bounds";
                }
            } catch (Exception e) {
                rawVd = "Error";
            }

            // Pad if necessary (Display logic only, Pin.java handles actual logic)
            String displayVd = rawVd;
            if (!rawVd.startsWith("Error")) {
                if (displayVd.length() > 16)
                    displayVd = displayVd.substring(0, 16);
                while (displayVd.length() < 16)
                    displayVd += padChar;
            }

            // Show User's Start Input (startPos + 1) for clarity
            int displayStart = startPos + 1;

            String result = t("module.payments.result.pin") + " " + pin + "\n\n" +
                    t("module.payments.result.method") + " IBM 3624\n" +
                    t("module.payments.result.pan") + " " + pan + "\n" +
                    "Offset: " + offset + "\n" +
                    "Conversion Table: " + convTable + "\n" +
                    t("module.payments.result.validationConfig", displayStart, length, padChar) + "\n" +
                    t("module.payments.result.validationDataBlock", " (Computed)") + " " + displayVd.toUpperCase();

            ibm3624ResultArea.setText(result);
            ibm3624ResultArea.setManaged(true);
            ibm3624ResultArea.setVisible(true);

            java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
            details.put("Method", "IBM 3624");
            details.put("PAN", maskPan(pan));
            details.put("Offset", offset);
            details.put("PIN", "[not persisted]");
            mainController.publish(OperationResult.forOperation("Generate PIN (IBM 3624)")
                    .output(pin.getBytes(java.nio.charset.StandardCharsets.UTF_8)).details(details)
                    .status(t("module.payments.status.success")).build());

        } catch (Exception e) {
            showError(t("module.payments.error.generationTitle"), t("module.payments.error.operation", "PIN", e.getMessage()));
            LOG.error("IBM 3624 PIN generation failed", e);
        }
    }

    public void handleVerifyIbm3624Pin() {
        try {
            if (ibm3624PvkField == null || ibm3624PinVerifyField == null || ibm3624PanField == null
                    || ibm3624ResultArea == null) {
                showError(t("module.payments.error.configurationTitle"), t("module.payments.error.controlsNotInitialized", "IBM 3624 verify"));
                return;
            }

            String pvkHex = ibm3624PvkField.getText().trim();
            String convTable = ibm3624ConvTableField != null ? ibm3624ConvTableField.getText().trim()
                    : "0123456789012345";
            String offset = ibm3624OffsetField != null ? ibm3624OffsetField.getText().trim() : "";
            String pan = ibm3624PanField.getText().trim();
            String pinToVerify = ibm3624PinVerifyField.getText().trim();

            if (pvkHex.isEmpty() || pan.isEmpty() || pinToVerify.isEmpty()) {
                showError(t("module.payments.error.inputTitle"), t("module.payments.error.pvkPanPinRequired"), "ibm3624PvkField");
                return;
            }

            // Convert PVK to bytes
            byte[] pvk = DataConverter.hexToBytes(pvkHex);

            // Parse configuration (Same as Generation)
            int startPos = 0;
            int length = 12; // Default
            String padChar = "0";

            if (ibm3624StartField != null && !ibm3624StartField.getText().trim().isEmpty()) {
                try {
                    startPos = Integer.parseInt(ibm3624StartField.getText().trim());
                    if (startPos > 0)
                        startPos--; // 1-based to 0-based
                } catch (NumberFormatException e) {
                    showError(t("module.payments.error.inputTitle"), t("module.payments.error.invalidStartPosition"));
                    return;
                }
            }

            if (ibm3624LengthField != null && !ibm3624LengthField.getText().trim().isEmpty()) {
                try {
                    length = Integer.parseInt(ibm3624LengthField.getText().trim());
                } catch (NumberFormatException e) {
                    showError(t("module.payments.error.inputTitle"), t("module.payments.error.invalidLength"));
                    return;
                }
            }

            if (ibm3624PadField != null && !ibm3624PadField.getText().trim().isEmpty()) {
                padChar = ibm3624PadField.getText().trim().substring(0, 1);
            }

            // Generate expected PIN - use static method with all parameters
            String expectedPin = com.cryptocarver.pin.Pin.generateIbm3624Pin(
                    pvk,
                    convTable,
                    offset,
                    pan,
                    startPos,
                    length,
                    padChar);

            // Reconstruct Validation Data Block for display (Debugging feedback)
            String rawVd = "";
            try {
                if (pan.length() >= startPos + length) {
                    rawVd = pan.substring(startPos, startPos + length);
                } else {
                    rawVd = "Error: bounds";
                }
            } catch (Exception e) {
                rawVd = "Error";
            }

            String displayVd = rawVd;
            if (!rawVd.startsWith("Error")) {
                if (displayVd.length() > 16)
                    displayVd = displayVd.substring(0, 16);
                while (displayVd.length() < 16)
                    displayVd += padChar;
            }
            int displayStart = startPos + 1;

            boolean isValid = expectedPin.equals(pinToVerify);

            String result = t("module.payments.result.pinVerification") + " " + t(isValid ? "module.payments.result.validSymbol" : "module.payments.result.invalidSymbol") + "\n\n" +
                    t("module.payments.result.enteredPin") + " " + pinToVerify + "\n" +
                    t("module.payments.result.expectedPin") + " " + expectedPin + "\n" +
                    t("module.payments.result.method") + " IBM 3624\n" +
                    t("module.payments.result.pan") + " " + pan + "\n" +
                    "Offset: " + offset + "\n" +
                    t("module.payments.result.validationConfig", displayStart, length, padChar) + "\n" +
                    t("module.payments.result.validationDataBlock", "") + " " + displayVd.toUpperCase();

            ibm3624ResultArea.setText(result);
            ibm3624ResultArea.setManaged(true);
            ibm3624ResultArea.setVisible(true);

            java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
            details.put("Method", "IBM 3624");
            details.put("PAN", maskPan(pan));
            details.put("Result", isValid ? "VALID" : "INVALID");
            details.put("PIN", "[not persisted]");
            mainController.publish(OperationResult.forOperation("Verify PIN (IBM 3624)")
                    .output(expectedPin.getBytes(java.nio.charset.StandardCharsets.UTF_8)).details(details)
                    .status(t(isValid ? "module.payments.status.valid" : "module.payments.status.invalid")).build());

        } catch (Exception e) {
            showError(t("module.payments.error.verificationTitle"), t("module.payments.error.operation", "PIN", e.getMessage()));
            LOG.error("IBM 3624 PIN verification failed", e);
        }
    }

    // PIN GENERATORS (OFFSET & PVV)
    // ============================================================

    public void handleGenerateOffsetUtility() {
        try {
            if (genOffsetPvkField == null || genOffsetResultArea == null) {
                showError(t("module.payments.error.configurationTitle"), t("module.payments.error.controlsNotInitialized", "PIN generator"));
                return;
            }

            String pvk = genOffsetPvkField.getText().trim();
            String decTable = genOffsetDecTableField.getText().trim();
            String pan = genOffsetPanField.getText().trim();
            String pin = genOffsetPinField.getText().trim();

            if (pvk.isEmpty() || decTable.isEmpty() || pan.isEmpty() || pin.isEmpty()) {
                showError(t("module.payments.error.inputTitle"), t("module.payments.error.pvkPanPin"), "genOffsetPvkField");
                return;
            }

            if (decTable.length() != 16) {
                showError(t("module.payments.error.inputTitle"), t("module.payments.error.decimalizationTable"));
                return;
            }

            String offset = PaymentOperations.generateIBM3624Offset(pin, pan, pvk, decTable);

            // Reconstruct Validation Data Block for display (This helper uses defaults, so
            // "offset" might be wrong if defaults mismatch)
            // WE MUST RE-CALCULATE using Pin.java directly to support custom config

            // Convert PVK to bytes
            byte[] pvkBytes = DataConverter.hexToBytes(pvk);

            // Parse configuration
            int startPos = 0;
            int length = 12; // Default
            String padChar = "0";

            if (genOffsetStartField != null && !genOffsetStartField.getText().trim().isEmpty()) {
                try {
                    startPos = Integer.parseInt(genOffsetStartField.getText().trim());
                    if (startPos > 0)
                        startPos--; // 1-based to 0-based
                } catch (NumberFormatException e) {
                    showError(t("module.payments.error.inputTitle"), t("module.payments.error.invalidStartPosition"));
                    return;
                }
            }

            if (genOffsetLengthField != null && !genOffsetLengthField.getText().trim().isEmpty()) {
                try {
                    length = Integer.parseInt(genOffsetLengthField.getText().trim());
                } catch (NumberFormatException e) {
                    showError(t("module.payments.error.inputTitle"), t("module.payments.error.invalidLength"));
                    return;
                }
            }

            if (genOffsetPadField != null && !genOffsetPadField.getText().trim().isEmpty()) {
                padChar = genOffsetPadField.getText().trim().substring(0, 1);
            }

            // Generate Offset directly
            offset = com.cryptocarver.pin.Pin.generateIbm3624Offset(
                    pvkBytes,
                    decTable,
                    pin,
                    pan,
                    startPos,
                    length,
                    padChar);

            // Reconstruct Validation Data Block for display
            String rawVd = "";
            try {
                if (pan.length() >= startPos + length) {
                    rawVd = pan.substring(startPos, startPos + length);
                } else {
                    rawVd = "Error: bounds";
                }
            } catch (Exception e) {
                rawVd = "Error";
            }

            String displayVd = rawVd;
            if (!rawVd.startsWith("Error")) {
                if (displayVd.length() > 16)
                    displayVd = displayVd.substring(0, 16);
                while (displayVd.length() < 16)
                    displayVd += padChar;
            }
            int displayStart = startPos + 1;

            StringBuilder res = new StringBuilder();
            res.append(t("module.payments.result.generatedOffset")).append("\n").append(offset).append("\n\n");
            res.append(t("module.payments.result.forPin")).append(" ").append(pin).append("\n");
            res.append(t("module.payments.result.validationConfig", displayStart, length, padChar)).append("\n");
            res.append(t("module.payments.result.validationDataBlock", "")).append(" ").append(displayVd.toUpperCase());

            genOffsetResultArea.setText(res.toString());
            genOffsetResultArea.setManaged(true);
            genOffsetResultArea.setVisible(true);

            java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
            details.put("Method", "IBM 3624 offset");
            details.put("PAN", maskPan(pan));
            details.put("PIN", "[not persisted]");
            mainController.publish(OperationResult.forOperation("Generate Offset")
                    .output(offset.getBytes(java.nio.charset.StandardCharsets.UTF_8)).details(details)
                    .status(t("module.payments.status.success")).build());

        } catch (Exception e) {
            showError(t("module.payments.error.generationTitle"), t("module.payments.error.operation", "Offset", e.getMessage()));
        }
    }

    public void handleGeneratePVVUtility() {
        try {
            if (genPvvPvkField == null || genPvvResultArea == null) {
                showError(t("module.payments.error.configurationTitle"), t("module.payments.error.controlsNotInitialized", "PVV generator"));
                return;
            }

            String pvk = genPvvPvkField.getText().trim();
            String pan = genPvvPanField.getText().trim();
            String pin = genPvvPinField.getText().trim();
            String keyIndex = genPvvKeyIndexField != null ? genPvvKeyIndexField.getText().trim() : "0";
            if (keyIndex.isEmpty())
                keyIndex = "0";

            if (pvk.isEmpty() || pan.isEmpty() || pin.isEmpty()) {
                showError(t("module.payments.error.inputTitle"), t("module.payments.error.pvkPanPin"), "genPvvPvkField");
                return;
            }

            String pvv = PaymentOperations.generatePVV(pin, pan, pvk, keyIndex, 4);

            StringBuilder res = new StringBuilder();
            res.append(t("module.payments.result.generatedPvv")).append(" ").append(pvv).append("\n\n");
            res.append(t("module.payments.result.keyIndex")).append(" ").append(keyIndex).append("\n");

            genPvvResultArea.setText(res.toString());
            genPvvResultArea.setManaged(true);
            genPvvResultArea.setVisible(true);

            java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
            details.put("Method", "VISA PVV");
            details.put("PAN", maskPan(pan));
            details.put("Key Index", keyIndex);
            details.put("PIN", "[not persisted]");
            mainController.publish(OperationResult.forOperation("Generate PVV")
                    .output(pvv.getBytes(java.nio.charset.StandardCharsets.UTF_8)).details(details)
                    .status(t("module.payments.status.success")).build());

        } catch (Exception e) {
            showError(t("module.payments.error.generationTitle"), t("module.payments.error.operation", "PVV", e.getMessage()));
        }
    }

    public void handleDerivePinFromPvvUtility() {
        try {
            if (derivePvvPvkField == null || derivePvvResultArea == null) {
                showError(t("module.payments.error.configurationTitle"), t("module.payments.error.controlsNotInitialized", "PVV derivation"));
                return;
            }

            String pvk = derivePvvPvkField.getText().trim();
            String pan = derivePvvPanField.getText().trim();
            String targetPvv = derivePvvTargetPvvField.getText().trim();
            String keyIndex = derivePvvKeyIndexField != null ? derivePvvKeyIndexField.getText().trim() : "0";
            if (keyIndex.isEmpty())
                keyIndex = "0";

            if (pvk.isEmpty() || pan.isEmpty() || targetPvv.isEmpty()) {
                showError(t("module.payments.error.inputTitle"), t("module.payments.error.pvvTargetRequired"), "derivePvvTargetPvvField");
                return;
            }

            java.util.List<String> matches = PaymentOperations.derivePinFromPvv(pan, pvk, keyIndex, targetPvv, 4);

            StringBuilder res = new StringBuilder();
            res.append("Derive PIN Results:\n");
            res.append("-------------------\n");
            res.append("PVK: ").append(pvk).append("\n");
            res.append(t("module.payments.result.pan")).append(" ").append(pan).append("\n");
            res.append(t("module.payments.result.targetPvv")).append(" ").append(targetPvv).append("\n");
            res.append(t("module.payments.result.pvki")).append(" ").append(keyIndex).append("\n\n");

            if (matches.isEmpty()) {
                res.append(t("module.payments.result.noPinsFound"));
            } else {
                res.append(t("module.payments.result.foundMatches", matches.size())).append("\n\n");
                for (String pin : matches) {
                    res.append("  • ").append(t("module.payments.result.pin")).append(" ").append(pin).append("\n");
                }
            }

            derivePvvResultArea.setText(res.toString());
            derivePvvResultArea.setManaged(true);
            derivePvvResultArea.setVisible(true);

            java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
            details.put("Method", "Derive PIN from PVV");
            details.put("PAN", maskPan(pan));
            details.put("PVV", targetPvv);
            details.put("Matches", String.valueOf(matches.size()));
            details.put("PINs", "[not persisted]");
            mainController.publish(OperationResult.forOperation("Derive PIN from PVV")
                    .output(res.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).details(details)
                    .status(t("module.payments.status.success")).build());

        } catch (Exception e) {
            showError(t("module.payments.error.derivationTitle"), t("module.payments.error.operation", "PIN", e.getMessage()));
        }
    }
    public void loadProfile(com.cryptocarver.model.payments.PaymentProfile p) {
        if (p.getType() == com.cryptocarver.model.payments.PaymentProfile.ProfileType.DUKPT_TDES) {
            if (dukptSchemeCombo != null) dukptSchemeCombo.setValue("TDES (legacy, 10-byte KSN)");
            selectedTdesUsage = p.getParameters().getOrDefault("usage", "").toLowerCase().contains("mac")
                    ? DukptKsn.TdesKeyUsage.MAC_REQUEST : DukptKsn.TdesKeyUsage.PIN_ENCRYPTION;
            if (dukptTdesUsageCombo != null) dukptTdesUsageCombo.setValue(selectedTdesUsage.label());
            loadedDukptProfileName = p.getName();
            loadedDukptExpectedWorkingKey = p.getOutputs().get("workingKey");
            if (dukptBdkField != null && p.getInputs().containsKey("bdk")) dukptBdkField.setText(p.getInputs().get("bdk"));
            if (dukptKsnField != null && p.getInputs().containsKey("ksn")) dukptKsnField.setText(p.getInputs().get("ksn"));
            updateStatus(t("module.payments.status.profileLoaded", "DUKPT TDES - " + p.getName()));
        } else if (p.getType() == com.cryptocarver.model.payments.PaymentProfile.ProfileType.DUKPT_AES) {
            if (dukptSchemeCombo != null) dukptSchemeCombo.setValue("AES (X9.24-3, 12-byte KSN)");
            loadedDukptProfileName = p.getName();
            loadedDukptExpectedWorkingKey = p.getOutputs().get("workingKey");
            if (dukptAesUsageCombo != null && p.getParameters().containsKey("usage")) {
                String usageStr = p.getParameters().get("usage");
                for (String item : dukptAesUsageCombo.getItems()) {
                    if (item.toLowerCase().contains(usageStr.toLowerCase())) { dukptAesUsageCombo.setValue(item); break; }
                }
            }
            if (dukptBdkField != null && p.getInputs().containsKey("bdk")) dukptBdkField.setText(p.getInputs().get("bdk"));
            if (dukptKsnField != null && p.getInputs().containsKey("ksn")) dukptKsnField.setText(p.getInputs().get("ksn"));
            updateStatus(t("module.payments.status.profileLoaded", "DUKPT AES - " + p.getName()));
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
            if (macKeyField != null && p.getInputs().containsKey("sessionKey")) macKeyField.setText(p.getInputs().get("sessionKey"));
            if (macDataField != null && p.getInputs().containsKey("apdu")) macDataField.setText(p.getInputs().get("apdu"));
            if (macAlgorithmCombo != null && p.getParameters().containsKey("algorithm")) {
                String algoStr = p.getParameters().get("algorithm");
                if (algoStr.contains("Algorithm 3")) {
                    macAlgorithmCombo.setValue("Retail MAC (ISO 9797-1 Alg 3)");
                } else {
                    for (String item : macAlgorithmCombo.getItems()) {
                        if (item.contains(algoStr)) { macAlgorithmCombo.setValue(item); break; }
                    }
                }
            }
            updateStatus(t("module.payments.status.profileLoaded", "Secure Messaging - " + p.getName()));
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
