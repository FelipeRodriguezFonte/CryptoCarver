package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.utils.OperationHistory;
import javafx.stage.FileChooser;
import javafx.scene.control.*;
import javafx.fxml.FXML;

import java.io.File;
import java.nio.file.Files;

/**
 * Controller for XML Security (XAdES) operations
 */
public class XMLSignatureController {

    private StatusReporter statusReporter;

    // Sign UI

    @FXML private javafx.scene.control.RadioButton xmlSignSourceLocalRadio;
    @FXML private javafx.scene.control.RadioButton xmlSignSourcePkcs11Radio;
    @FXML private javafx.scene.control.ToggleGroup xmlSignSourceToggleGroup;
    @FXML private javafx.scene.layout.VBox xmlSignLocalKeyBox;
    @FXML private javafx.scene.control.Label xmlSignKeyPathLabel;
    @FXML private javafx.scene.control.Label xmlSignKeyPasswordLabel;

    @FXML private TextField xmlSignInputPathField;
    @FXML private TextField xmlSignKeyPathField;
    @FXML private PasswordField xmlSignKeyPasswordField;
    @FXML private ComboBox<String> xmlSignKeyAliasCombo;
    @FXML private TextArea xmlSignOutputArea;
    @FXML private ComboBox<String> xmlSignLevelCombo;
    @FXML private ComboBox<String> xmlSignPackagingCombo;
    @FXML private ComboBox<String> xmlSignTsaUrlText;
    @FXML private ComboBox<String> xmlSignTsaProfileCombo;
    @FXML private TextField xmlSignTsaProfileNameField;
    @FXML private ComboBox<String> xmlSignTsaAuthTypeCombo;
    @FXML private TextField xmlSignTsaUserField;
    @FXML private PasswordField xmlSignTsaPasswordField;

    private static final String NO_TSA = XmlSignatureTimestampCoordinator.NO_TSA;
    private static final String DIGICERT_TSA = XmlSignatureTimestampCoordinator.DIGICERT_TSA;
    private static final String FREETSA_TSA = XmlSignatureTimestampCoordinator.FREETSA_TSA;

    // Verify UI
    @FXML private TextArea xmlVerifyInputArea; // Or file path
    @FXML private TextArea xmlVerifyReportArea;
    @FXML private TextField xmlVerifyTrustStorePathField;
    @FXML private PasswordField xmlVerifyTrustStorePasswordField;
    @FXML private ComboBox<String> xmlVerifyTrustStoreProfileCombo;

    // Inspector UI (local structural analysis; no validation is performed)
    @FXML private TextArea xmlInspectInputArea;
    @FXML private TextArea xmlInspectReportArea;

    @FXML private TextField xmlTimestampFileField;
    @FXML private TextField xmlTimestampUrlField;
    @FXML private ComboBox<String> xmlTimestampHashCombo;
    @FXML private TextField xmlTimestampTokenField;
    @FXML private TextArea xmlTimestampReportArea;
    @FXML private TextField xmlTimestampTrustStoreField;
    @FXML private PasswordField xmlTimestampTrustStorePasswordField;

    @FXML private Accordion xmlAccordion;
    @FXML private javafx.scene.layout.VBox xmlSecurityContainer;
    private ModuleI18n.Binding moduleI18n;

    private String t(String key, Object... args) {
        return com.cryptocarver.service.I18nService.getInstance().text(key, args);
    }

    private void showValidationError(String title, String message, String fieldKey) {
        if (statusReporter != null) {
            String safeMessage = InlineErrorPresenter.redactSecrets(message);
            statusReporter.showError(new UserFacingError(title, safeMessage, safeMessage, fieldKey));
        }
    }

    private XmlSignatureSigningCoordinator signingCoordinator;
    private XmlSignatureKeyMaterialCoordinator keyMaterialCoordinator;
    private XmlSignatureTimestampCoordinator timestampCoordinator;

    private XmlSignatureTimestampCoordinator timestampCoordinator() {
        if (timestampCoordinator == null) {
            timestampCoordinator = new XmlSignatureTimestampCoordinator(new XmlSignatureTimestampCoordinator.View(
                    () -> xmlSignTsaUrlText, () -> xmlSignTsaProfileCombo, () -> xmlSignTsaProfileNameField,
                    () -> xmlSignTsaAuthTypeCombo, () -> xmlSignTsaUserField, () -> xmlSignTsaPasswordField,
                    () -> xmlTimestampFileField, () -> xmlTimestampUrlField, () -> xmlTimestampHashCombo,
                    () -> xmlTimestampTokenField, () -> xmlTimestampReportArea, () -> xmlTimestampTrustStoreField,
                    () -> xmlTimestampTrustStorePasswordField, this::chooseFile),
                    () -> statusReporter);
        }
        return timestampCoordinator;
    }

    private XmlSignatureKeyMaterialCoordinator keyMaterialCoordinator() {
        if (keyMaterialCoordinator == null) {
            keyMaterialCoordinator = new XmlSignatureKeyMaterialCoordinator(new XmlSignatureKeyMaterialCoordinator.View(
                    () -> xmlSignSourcePkcs11Radio, () -> xmlSignKeyPathField, () -> xmlSignKeyPasswordField,
                    () -> xmlSignKeyAliasCombo, () -> xmlVerifyTrustStorePathField,
                    () -> xmlVerifyTrustStorePasswordField, () -> xmlVerifyTrustStoreProfileCombo,
                    () -> xmlTimestampTrustStoreField, this::chooseFile),
                    () -> statusReporter);
        }
        return keyMaterialCoordinator;
    }

    private XmlSignatureSigningCoordinator signingCoordinator() {
        if (signingCoordinator == null) {
            signingCoordinator = new XmlSignatureSigningCoordinator(new XmlSignatureSigningCoordinator.View(
                    () -> xmlSignInputPathField, () -> xmlSignLevelCombo, () -> xmlSignPackagingCombo,
                    () -> xmlSignSourcePkcs11Radio, () -> xmlSignKeyAliasCombo, () -> xmlSignKeyPathField,
                    () -> xmlSignKeyPasswordField, () -> xmlSignOutputArea, () -> xmlVerifyInputArea,
                    () -> xmlVerifyReportArea, () -> xmlVerifyTrustStorePathField,
                    () -> xmlVerifyTrustStorePasswordField, () -> xmlInspectInputArea, () -> xmlInspectReportArea,
                    () -> timestampCoordinator().getTsaUrl(), url -> timestampCoordinator().isHttpUrl(url),
                    url -> timestampCoordinator().saveCustomTsa(url), url -> timestampCoordinator().publishedTsaUrl(url),
                    () -> timestampCoordinator().getTsaCredentials(), this::handleLoadXMLKeys, this::showValidationError),
                    () -> statusReporter);
        }
        return signingCoordinator;
    }

    public XMLSignatureController() {
    }

    public void initModule(StatusReporter reporter) {
        this.statusReporter = reporter;
    }

    @FXML
    private void handleXMLSignSourceChanged() {
        boolean isLocal = xmlSignSourceLocalRadio.isSelected();
        xmlSignLocalKeyBox.setVisible(isLocal);
        xmlSignLocalKeyBox.setManaged(isLocal);
        xmlSignKeyPathLabel.setVisible(isLocal);
        xmlSignKeyPathLabel.setManaged(isLocal);
        xmlSignKeyPathField.setVisible(isLocal);
        xmlSignKeyPathField.setManaged(isLocal);

        xmlSignKeyPasswordLabel.setVisible(isLocal);
        xmlSignKeyPasswordLabel.setManaged(isLocal);
        xmlSignKeyPasswordField.setVisible(isLocal);
        xmlSignKeyPasswordField.setManaged(isLocal);

        // Aliases belong to the selected source; never reuse a local entry for a token.
        xmlSignKeyAliasCombo.getItems().clear();
        xmlSignKeyAliasCombo.getSelectionModel().clearSelection();
    }

    public void initialize() {
        moduleI18n = ModuleI18n.bind(xmlSecurityContainer, ModuleTextCatalog.xmlSecurity());
        xmlSignLevelCombo.getItems().addAll("XAdES-BASELINE-B", "XAdES-BASELINE-T", "XAdES-BASELINE-LT", "XAdES-BASELINE-LTA");
        xmlSignLevelCombo.setValue("XAdES-BASELINE-B");
        xmlSignPackagingCombo.getItems().setAll("ENVELOPED", "ENVELOPING", "DETACHED");
        xmlSignPackagingCombo.setValue("ENVELOPED");
        xmlSignTsaUrlText.getItems().setAll(NO_TSA, DIGICERT_TSA, FREETSA_TSA);
        String customTsa = AppSettings.getInstance().getCustomTsaUrl();
        xmlSignTsaUrlText.setValue(customTsa.isBlank() ? NO_TSA : customTsa);
        timestampCoordinator().reloadTsaProfiles();

        if (xmlSignTsaAuthTypeCombo != null) {
            xmlSignTsaAuthTypeCombo.getItems().addAll("NONE", "BASIC", "BEARER");
            xmlSignTsaAuthTypeCombo.setValue("NONE");
        }

        xmlVerifyTrustStoreProfileCombo.getItems().setAll(AppSettings.getInstance().getTrustStoreProfiles().stream()
                .map(AppSettings.TrustStoreProfile::name).sorted(String.CASE_INSENSITIVE_ORDER).toList());

        xmlTimestampHashCombo.getItems().setAll("SHA-256", "SHA-384", "SHA-512");
        xmlTimestampHashCombo.setValue("SHA-256");
        String saved = AppSettings.getInstance().getCustomTsaUrl();
        if (!saved.isBlank()) xmlTimestampUrlField.setText(saved);
        handleXMLSignSourceChanged();
    }

    public void expandAccordionPane(String itemName) {
        if (xmlAccordion == null) return;
        for (TitledPane pane : xmlAccordion.getPanes()) {
            if (ModulePaneMatcher.matches(pane, itemName, ModuleTextCatalog.xmlSecurity())) {
                xmlAccordion.setExpandedPane(pane);
                break;
            }
        }
    }

    public void fillClipboardInput(String value) {
        if (xmlInspectInputArea != null) xmlInspectInputArea.setText(value);
    }

    @FXML
    public void handleReset() {
        ModuleResetPolicy.apply(xmlSecurityContainer, ModuleResetPolicy.Action.RESET_DEFAULTS,
                this::clearModuleData, this::restoreSafeDefaults);
        if (statusReporter != null) statusReporter.updateStatus(t("module.xml.resetStatus"));
    }

    @FXML
    public void handleClear() {
        ModuleResetPolicy.apply(xmlSecurityContainer, ModuleResetPolicy.Action.CLEAR,
                this::clearModuleData, null);
        if (statusReporter != null) statusReporter.updateStatus(t("module.xml.clearStatus"));
    }

    private void clearModuleData() {
        ModuleResetPolicy.clearTextInputs(xmlSecurityContainer);
        timestampCoordinator().clearLastTimestampToken();
    }

    private void restoreSafeDefaults() {
        if (xmlSignLevelCombo != null) xmlSignLevelCombo.setValue("XAdES-BASELINE-B");
        if (xmlSignPackagingCombo != null) xmlSignPackagingCombo.setValue("ENVELOPED");
        if (xmlSignTsaAuthTypeCombo != null) xmlSignTsaAuthTypeCombo.setValue("NONE");
        if (xmlTimestampHashCombo != null) xmlTimestampHashCombo.setValue("SHA-256");
    }

    @FXML
    public void handleBrowseXMLSignInput() {
        File file = chooseFile("Select XML to Sign");
        if (file != null) {
            xmlSignInputPathField.setText(file.getAbsolutePath());
        }
    }

    @FXML
    public void handleBrowseXMLKey() { keyMaterialCoordinator().handleBrowseXMLKey(); }

    @FXML
    public void handleLoadXMLKeys() { keyMaterialCoordinator().handleLoadXMLKeys(); }

    @FXML
    public void handleTestTSA() { timestampCoordinator().handleTestTSA(); }

    @FXML
    public void handleSaveTSA() { timestampCoordinator().handleSaveTSA(); }

    @FXML
    public void handleLoadTSASavedProfile() { timestampCoordinator().handleLoadTSASavedProfile(); }

    @FXML
    public void handleSaveTSASavedProfile() { timestampCoordinator().handleSaveTSASavedProfile(); }

    @FXML
    public void handleDeleteTSASavedProfile() { timestampCoordinator().handleDeleteTSASavedProfile(); }

    @FXML
    public void handleSignXML() { signingCoordinator().handleSignXML(); }

    @FXML
    public void handleVerifyXML() { signingCoordinator().handleVerifyXML(); }

    @FXML
    public void handleBrowseXMLInspectorInput() {
        File file = chooseFile("Select Signed XML to Inspect");
        if (file == null) return;
        try {
            xmlInspectInputArea.setText(Files.readString(file.toPath()));
            statusReporter.updateStatus(t("module.xml.feedback.statusLoaded", file.getName()));
        } catch (Exception e) {
            statusReporter.showError("XML Inspector", t("module.xml.operationFailed", "XML read", e.getMessage()));
        }
    }

    @FXML
    public void handleInspectSignedXML() { signingCoordinator().handleInspectSignedXML(); }

    @FXML
    public void handleBrowseTimestampFile() { timestampCoordinator().handleBrowseTimestampFile(); }

    @FXML
    public void handleRequestTimestamp() { timestampCoordinator().handleRequestTimestamp(); }

    @FXML
    public void handleSaveTimestampToken() { timestampCoordinator().handleSaveTimestampToken(); }

    @FXML
    public void handleBrowseTimestampToken() { timestampCoordinator().handleBrowseTimestampToken(); }

    @FXML
    public void handleInspectTimestampToken() { timestampCoordinator().handleInspectTimestampToken(); }

    @FXML
    public void handleValidateTimestampToken() { timestampCoordinator().handleValidateTimestampToken(); }

    @FXML
    public void handleBrowseTimestampTrustStore() { keyMaterialCoordinator().handleBrowseTimestampTrustStore(); }

    private File chooseFile(String title) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        return fileChooser.showOpenDialog(null); // Should use stage if possible
    }

    @FXML
    public void handleBrowseXMLTrustStore() { keyMaterialCoordinator().handleBrowseXMLTrustStore(); }

    @FXML
    public void handleLoadXMLTrustStoreProfile() { keyMaterialCoordinator().handleLoadXMLTrustStoreProfile(); }

    @FXML
    public void handleSaveSignedXML() { signingCoordinator().handleSaveSignedXML(); }
}
