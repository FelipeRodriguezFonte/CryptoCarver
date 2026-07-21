package com.cryptocarver.ui;

import com.cryptocarver.crypto.AsicOperations;
import com.cryptocarver.model.OperationResult;
import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ComboBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.List;

/** Explicit, bounded ASiC-S laboratory panel. */
public final class AsicController {
    private static final long MAX_BYTES = 64L * 1024L * 1024L;

    @FXML private TextField asicInputPathField;
    @FXML private TextField asicOutputPathField;
    @FXML private TextField asicPkcs12PathField;
    @FXML private PasswordField asicPasswordField;
    @FXML private RadioButton asicSourceLocalRadio;
    @FXML private RadioButton asicSourcePkcs11Radio;
    @FXML private javafx.scene.control.ToggleGroup asicSourceToggleGroup;
    @FXML private javafx.scene.layout.HBox asicLocalKeyBox;
    @FXML private javafx.scene.layout.HBox asicPkcs11Box;
    @FXML private ComboBox<String> asicPkcs11AliasCombo;
    @FXML private TextField asicEPayloadsField;
    @FXML private TextField asicTrustStorePathField;
    @FXML private PasswordField asicTrustStorePasswordField;
    @FXML private TextArea asicResultArea;

    private StatusReporter statusReporter;
    private List<File> asicEPayloads = List.of();

    public void setStatusReporter(StatusReporter statusReporter) {
        this.statusReporter = statusReporter;
    }

    @FXML
    private void initialize() {
        handleSourceChanged();
    }

    @FXML private void handleChooseInput() { chooseInput(asicInputPathField, "Select ASiC payload"); }
    @FXML private void handleChoosePkcs12() { chooseInput(asicPkcs12PathField, "Select PKCS#12 signing key"); }
    @FXML private void handleChooseTrustStore() { chooseInput(asicTrustStorePathField, "Select local truststore"); }

    @FXML
    private void handleChooseAsicEPayloads() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select ASiC-E payload files (one or more)");
        List<File> selected = chooser.showOpenMultipleDialog(owner());
        if (selected == null || selected.isEmpty()) return;
        asicEPayloads = List.copyOf(selected);
        if (asicEPayloadsField != null) asicEPayloadsField.setText(asicEPayloads.size() + " payload file(s) selected");
    }

    @FXML
    private void handleSourceChanged() {
        boolean tokenSource = asicSourcePkcs11Radio != null && asicSourcePkcs11Radio.isSelected();
        setVisibleManaged(asicLocalKeyBox, !tokenSource);
        setVisibleManaged(asicPkcs11Box, tokenSource);
    }

    @FXML
    private void handleLoadTokenKeys() {
        try {
            List<String> aliases = com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance()
                    .requireSession().listPrivateKeysWithCertificate();
            if (aliases.isEmpty()) throw new IllegalArgumentException("No private PKCS#11 keys with X.509 certificates are available");
            asicPkcs11AliasCombo.getItems().setAll(aliases);
            asicPkcs11AliasCombo.getSelectionModel().selectFirst();
        } catch (Exception error) {
            showError("ASiC-S PKCS#11", error.getMessage());
        }
    }

    @FXML
    private void handleChooseOutput() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save ASiC-S container (new file required)");
        chooser.setInitialFileName("signed-container.asics");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ASiC-S containers", "*.asics", "*.zip"));
        File file = chooser.showSaveDialog(owner());
        if (file != null) asicOutputPathField.setText(file.getAbsolutePath());
    }

    @FXML
    private void handleCreate() {
        char[] password = password();
        try {
            File input = requireFile(asicInputPathField, "ASiC payload");
            File output = requireNewFile(asicOutputPathField, "ASiC output");
            byte[] payload = readBounded(input);
            boolean tokenSource = asicSourcePkcs11Radio != null && asicSourcePkcs11Radio.isSelected();
            byte[] container;
            if (tokenSource) {
                container = com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().requireSession()
                        .createAsicS(selectedTokenAlias(), payload, input.getName());
            } else {
                File pkcs12 = requireFile(asicPkcs12PathField, "PKCS#12 signing key");
                container = AsicOperations.createAsicS(payload, input.getName(), pkcs12, password);
            }
            Files.write(output.toPath(), container, java.nio.file.StandardOpenOption.CREATE_NEW);
            AsicOperations.AsicInspection inspection = AsicOperations.inspectAndVerify(container);
            asicResultArea.setText("ASiC-S written to: " + output.getName() + "\nPayload: " + inspection.payloadName()
                    + "\nCAdES signature: " + (inspection.signatureValid() ? "VALID" : "INVALID")
                    + "\nProfile: " + inspection.cadesProfile()
                    + "\n\nStructural/CAdES integrity only; certificate trust and LTV are not evaluated.");
            publish("ASiC-S Create" + (tokenSource ? " (PKCS#11)" : ""), input, output, inspection);
        } catch (Exception error) {
            showError("ASiC-S creation", error.getMessage());
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    @FXML
    private void handleInspect() {
        char[] trustStorePassword = trustStorePassword();
        try {
            File input = requireFile(asicInputPathField, "ASiC container");
            byte[] container = readBounded(input);
            String mimeType = AsicOperations.detectDeclaredMimeType(container);
            KeyStore trustStore = loadOptionalTrustStore(trustStorePassword);
            if (AsicOperations.ASIC_E_MIME_TYPE.equals(mimeType)) {
                AsicOperations.AsicEInspection inspection = AsicOperations.inspectAndVerifyE(container,
                        trustStore);
                asicResultArea.setText("ASiC-E inspection\nPayloads: " + inspection.payloadCount()
                        + "\nEntries: " + inspection.entryCount()
                        + "\nManifest hashes: " + (inspection.manifestDigestsValid() ? "VALID" : "INVALID")
                        + "\nSignature reference: " + (inspection.signatureReferenceValid() ? "VALID" : "INVALID")
                        + "\nCAdES signature: " + (inspection.signatureValid() ? "VALID" : "INVALID")
                        + "\nTrust chain (local): " + inspection.trustState() + " — " + inspection.trustDetails()
                        + "\n\nRevocation and LTV are not evaluated by this local ASiC-E inspection.");
                if (statusReporter != null) statusReporter.publish(OperationResult.forOperation("ASiC-E Inspect")
                        .detail("Container type", "ASiC-E")
                        .detail("Payloads", String.valueOf(inspection.payloadCount()))
                        .detail("Entries", String.valueOf(inspection.entryCount()))
                        .detail("Manifest hashes", inspection.manifestDigestsValid() ? "VALID" : "INVALID")
                        .detail("Signature reference", inspection.signatureReferenceValid() ? "VALID" : "INVALID")
                        .detail("CAdES profile", inspection.cadesProfile())
                        .detail("CAdES signature", inspection.signatureValid() ? "VALID" : "INVALID")
                        .detail("Certificate binding", inspection.certificateBindingValid() ? "VALID" : "INVALID")
                        .detail("Trust chain", inspection.trustState().name())
                        .detail("Trust details", inspection.trustDetails())
                        .detail("Revocation / LTV", "NOT EVALUATED (offline inspection)")
                        .status("ASiC-E inspection completed").build());
            } else if (AsicOperations.MIME_TYPE.equals(mimeType)) {
                AsicOperations.AsicInspection inspection = AsicOperations.inspectAndVerify(container, trustStore);
                asicResultArea.setText("ASiC-S inspection\nPayload: " + inspection.payloadName()
                        + "\nEntries: " + inspection.entryCount()
                        + "\nMimetype: " + (inspection.mimeTypeValid() ? "VALID" : "INVALID")
                        + "\nCAdES profile: " + inspection.cadesProfile()
                        + "\nCAdES signature: " + (inspection.signatureValid() ? "VALID" : "INVALID")
                        + "\nCertificate binding: " + (inspection.certificateBindingValid() ? "VALID" : "INVALID")
                        + "\nTrust chain (local): " + inspection.trustState() + " — " + inspection.trustDetails()
                        + "\n\nRevocation and LTV are not evaluated by this local ASiC-S inspection.");
                if (statusReporter != null) statusReporter.publish(OperationResult.forOperation("ASiC-S Inspect")
                        .detail("Container type", "ASiC-S")
                        .detail("Payload", inspection.payloadName())
                        .detail("Entries", String.valueOf(inspection.entryCount()))
                        .detail("Mimetype", inspection.mimeTypeValid() ? "VALID" : "INVALID")
                        .detail("CAdES profile", inspection.cadesProfile())
                        .detail("CAdES signature", inspection.signatureValid() ? "VALID" : "INVALID")
                        .detail("Certificate binding", inspection.certificateBindingValid() ? "VALID" : "INVALID")
                        .detail("Trust chain", inspection.trustState().name())
                        .detail("Trust details", inspection.trustDetails())
                        .detail("Revocation / LTV", "NOT EVALUATED (offline inspection)")
                        .status("ASiC-S inspection completed").build());
            } else {
                throw new IllegalArgumentException("Unsupported ASiC mimetype: " + mimeType);
            }
        } catch (Exception error) {
            showError("ASiC-S inspection", error.getMessage());
        } finally {
            Arrays.fill(trustStorePassword, '\0');
        }
    }

    /** Creates the CAdES ASiC-E baseline with one manifest covering every selected payload. */
    @FXML
    private void handleCreateE() {
        char[] password = password();
        try {
            if (asicEPayloads.isEmpty()) throw new IllegalArgumentException("Select one or more ASiC-E payload files first");
            File output = requireNewFile(asicOutputPathField, "ASiC-E output");
            java.util.Map<String, byte[]> payloads = new java.util.LinkedHashMap<>();
            for (File payload : asicEPayloads) payloads.put(payload.getName(), readBounded(payload));
            boolean tokenSource = asicSourcePkcs11Radio != null && asicSourcePkcs11Radio.isSelected();
            byte[] container;
            if (tokenSource) {
                container = com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().requireSession()
                        .createAsicE(selectedTokenAlias(), payloads);
            } else {
                File pkcs12 = requireFile(asicPkcs12PathField, "PKCS#12 signing key");
                container = AsicOperations.createAsicE(payloads, pkcs12, password);
            }
            Files.write(output.toPath(), container, java.nio.file.StandardOpenOption.CREATE_NEW);
            AsicOperations.AsicEInspection inspection = AsicOperations.inspectAndVerifyE(container);
            asicResultArea.setText("ASiC-E written to: " + output.getName() + "\nPayloads: " + inspection.payloadCount()
                    + "\nManifest hashes: " + (inspection.manifestDigestsValid() ? "VALID" : "INVALID")
                    + "\nCAdES signature: " + (inspection.signatureValid() ? "VALID" : "INVALID")
                    + "\n\nExperimental CAdES baseline: certificate trust, revocation and LTV are not evaluated.");
            if (statusReporter != null) statusReporter.publish(OperationResult.forOperation("ASiC-E Create" + (tokenSource ? " (PKCS#11)" : ""))
                    .detail("Container type", "ASiC-E")
                    .detail("Payloads", String.valueOf(inspection.payloadCount()))
                    .detail("Container", output.getName())
                    .detail("Manifest hashes", inspection.manifestDigestsValid() ? "VALID" : "INVALID")
                    .detail("Signature reference", inspection.signatureReferenceValid() ? "VALID" : "INVALID")
                    .detail("CAdES profile", inspection.cadesProfile())
                    .detail("Certificate binding", inspection.certificateBindingValid() ? "VALID" : "INVALID")
                    .detail("Trust chain", "NOT EVALUATED (creation)")
                    .status("Experimental ASiC-E container created").build());
        } catch (Exception error) {
            showError("ASiC-E creation", error.getMessage());
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private void chooseInput(TextField field, String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        File file = chooser.showOpenDialog(owner());
        if (file != null) field.setText(file.getAbsolutePath());
    }

    private javafx.stage.Window owner() {
        return asicResultArea == null || asicResultArea.getScene() == null ? null : asicResultArea.getScene().getWindow();
    }

    private static byte[] readBounded(File file) throws Exception {
        if (Files.size(file.toPath()) > MAX_BYTES) throw new IllegalArgumentException("File exceeds the 64 MiB laboratory limit");
        return Files.readAllBytes(file.toPath());
    }

    private static File requireFile(TextField field, String label) {
        if (field == null || field.getText().isBlank()) throw new IllegalArgumentException(label + " is required");
        File file = new File(field.getText().trim());
        if (!file.isFile()) throw new IllegalArgumentException(label + " does not exist or is not a file");
        return file;
    }

    private static File requireNewFile(TextField field, String label) {
        if (field == null || field.getText().isBlank()) throw new IllegalArgumentException(label + " is required");
        File file = new File(field.getText().trim());
        if (Files.exists(file.toPath())) throw new IllegalArgumentException(label + " already exists; choose a new destination");
        return file;
    }

    private char[] password() {
        return asicPasswordField == null || asicPasswordField.getText() == null ? new char[0] : asicPasswordField.getText().toCharArray();
    }

    private char[] trustStorePassword() {
        return asicTrustStorePasswordField == null || asicTrustStorePasswordField.getText() == null
                ? new char[0] : asicTrustStorePasswordField.getText().toCharArray();
    }

    private KeyStore loadOptionalTrustStore(char[] password) throws Exception {
        if (asicTrustStorePathField == null || asicTrustStorePathField.getText().isBlank()) return null;
        File trustStore = requireFile(asicTrustStorePathField, "Truststore");
        String name = trustStore.getName().toLowerCase(java.util.Locale.ROOT);
        KeyStore keyStore = KeyStore.getInstance(name.endsWith(".p12") || name.endsWith(".pfx") ? "PKCS12" : "JKS");
        try (var input = Files.newInputStream(trustStore.toPath())) {
            keyStore.load(input, password);
        }
        return keyStore;
    }

    private String selectedTokenAlias() {
        if (asicPkcs11AliasCombo == null || asicPkcs11AliasCombo.getValue() == null
                || asicPkcs11AliasCombo.getValue().isBlank()) {
            throw new IllegalArgumentException("Load and select a PKCS#11 token signing key first");
        }
        return asicPkcs11AliasCombo.getValue();
    }

    private static void setVisibleManaged(javafx.scene.Node node, boolean value) {
        if (node == null) return;
        node.setVisible(value);
        node.setManaged(value);
    }

    private void publish(String operation, File input, File output, AsicOperations.AsicInspection inspection) {
        if (statusReporter != null) statusReporter.publish(OperationResult.forOperation(operation)
                .detail("Container type", "ASiC-S")
                .detail("Payload", input.getName()).detail("Container", output.getName())
                .detail("CAdES profile", inspection.cadesProfile())
                .detail("CAdES signature", inspection.signatureValid() ? "VALID" : "INVALID")
                .detail("Certificate binding", inspection.certificateBindingValid() ? "VALID" : "INVALID")
                .detail("Trust chain", "NOT EVALUATED (creation)")
                .status("ASiC-S container created").build());
    }

    private void showError(String title, String message) {
        if (statusReporter != null) statusReporter.showError(title, message);
    }
}
