package com.cryptocarver.ui;

import com.cryptocarver.crypto.DukptKsn;
import com.cryptocarver.crypto.AesDukpt;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.function.Supplier;

/**
 * The DUKPT panel of the payments screen: TDES (10-byte KSN) and AES (X9.24-3, 12-byte KSN)
 * inspection with the derivation tree, working keys per usage, AES DUKPT PIN block
 * encryption and laboratory profiles whose expected working key is checked on inspection.
 */
final class DukptCoordinator {

    /** The panel's controls, injected into PaymentsController from payments.fxml. */
    record View(TextField bdk,
            TextField ksn,
            TextArea result,
            ComboBox<String> scheme,
            ComboBox<String> tdesUsage,
            ComboBox<String> aesUsage,
            ComboBox<String> aesKeyType,
            TextField aesPinBlock,
            ComboBox<String> aesPinOperation,
            HBox tdesOptions,
            HBox aesOptions,
            VBox aesPinBox) {
    }

    private final TextField dukptBdkField;
    private final TextField dukptKsnField;
    private final TextArea dukptResultArea;
    private final ComboBox<String> dukptSchemeCombo;
    private final ComboBox<String> dukptTdesUsageCombo;
    private final ComboBox<String> dukptAesUsageCombo;
    private final ComboBox<String> dukptAesKeyTypeCombo;
    private final TextField dukptAesPinBlockField;
    private final ComboBox<String> dukptAesPinOperationCombo;
    private final HBox dukptTdesOptionsBox;
    private final HBox dukptAesOptionsBox;
    private final VBox dukptAesPinBox;
    private final Supplier<StatusReporter> reporter;

    DukptCoordinator(View view, Supplier<StatusReporter> reporter) {
        this.dukptBdkField = view.bdk();
        this.dukptKsnField = view.ksn();
        this.dukptResultArea = view.result();
        this.dukptSchemeCombo = view.scheme();
        this.dukptTdesUsageCombo = view.tdesUsage();
        this.dukptAesUsageCombo = view.aesUsage();
        this.dukptAesKeyTypeCombo = view.aesKeyType();
        this.dukptAesPinBlockField = view.aesPinBlock();
        this.dukptAesPinOperationCombo = view.aesPinOperation();
        this.dukptTdesOptionsBox = view.tdesOptions();
        this.dukptAesOptionsBox = view.aesOptions();
        this.dukptAesPinBox = view.aesPinBox();
        this.reporter = reporter;
    }

    private StatusReporter reporter() {
        return reporter.get();
    }

    private static String t(String key, Object... args) {
        return com.cryptocarver.service.I18nService.getInstance().text(key, args);
    }

    private void updateStatus(String message) {
        if (reporter() != null) reporter().updateStatus(message);
    }

    private void showError(String title, String message) {
        if (reporter() != null) {
            String safeMessage = InlineErrorPresenter.redactSecrets(message);
            reporter().showError(new UserFacingError(title, safeMessage, safeMessage, (String) null));
        }
    }

    private DukptKsn.TdesKeyUsage selectedTdesUsage = DukptKsn.TdesKeyUsage.PIN_ENCRYPTION;
    private String loadedDukptProfileName;
    private String loadedDukptExpectedWorkingKey;

    /** Fills the scheme, usage and key-type choices and shows the options of the selected scheme. */
    void configure() {
        if (dukptSchemeCombo != null) {
            dukptSchemeCombo.getItems().setAll("TDES (legacy, 10-byte KSN)", "AES (X9.24-3, 12-byte KSN)");
            dukptSchemeCombo.setValue("TDES (legacy, 10-byte KSN)");
            dukptSchemeCombo.valueProperty().addListener((ignored, oldValue, newValue) -> updateDukptOptionsVisibility());
        }
        if (dukptTdesUsageCombo != null) {
            dukptTdesUsageCombo.getItems().setAll("PIN Encryption", "MAC Request", "MAC Response", "Data Encryption");
            dukptTdesUsageCombo.setValue(selectedTdesUsage.label());
            dukptTdesUsageCombo.valueProperty().addListener((ignored, oldValue, newValue) -> selectedTdesUsage = selectedTdesUsage());
        }
        if (dukptAesUsageCombo != null) { dukptAesUsageCombo.getItems().setAll("Data encryption (encrypt)", "Data encryption (decrypt)", "PIN encryption", "MAC generation", "MAC verification", "MAC both ways", "Key encryption", "Key derivation"); dukptAesUsageCombo.setValue("Data encryption (encrypt)"); }
        if (dukptAesKeyTypeCombo != null) { dukptAesKeyTypeCombo.getItems().setAll("AES-128", "AES-192", "AES-256"); dukptAesKeyTypeCombo.setValue("AES-128"); }
        if (dukptAesPinOperationCombo != null) { dukptAesPinOperationCombo.getItems().setAll("Encrypt formatted PIN block", "Decrypt encrypted PIN block"); dukptAesPinOperationCombo.setValue("Encrypt formatted PIN block"); }
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

    void handleInspectDukpt() {
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

    void handleAesDukptPinBlock() {
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

    /** Fills the DUKPT panel from a TDES laboratory profile and remembers its expected working key. */
    void loadTdesProfile(com.cryptocarver.model.payments.PaymentProfile p) {
        if (dukptSchemeCombo != null) dukptSchemeCombo.setValue("TDES (legacy, 10-byte KSN)");
        selectedTdesUsage = p.getParameters().getOrDefault("usage", "").toLowerCase().contains("mac")
                ? DukptKsn.TdesKeyUsage.MAC_REQUEST : DukptKsn.TdesKeyUsage.PIN_ENCRYPTION;
        if (dukptTdesUsageCombo != null) dukptTdesUsageCombo.setValue(selectedTdesUsage.label());
        loadedDukptProfileName = p.getName();
        loadedDukptExpectedWorkingKey = p.getOutputs().get("workingKey");
        if (dukptBdkField != null && p.getInputs().containsKey("bdk")) dukptBdkField.setText(p.getInputs().get("bdk"));
        if (dukptKsnField != null && p.getInputs().containsKey("ksn")) dukptKsnField.setText(p.getInputs().get("ksn"));
        updateStatus(t("module.payments.status.profileLoaded", "DUKPT TDES - " + p.getName()));
    }

    /** Fills the DUKPT panel from an AES laboratory profile and remembers its expected working key. */
    void loadAesProfile(com.cryptocarver.model.payments.PaymentProfile p) {
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
    }
}
