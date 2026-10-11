package com.cryptocarver.ui;

import com.cryptocarver.crypto.SymmetricCipher;
import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.help.SymmetricCapabilityPolicy;
import com.cryptocarver.model.help.HelpContext;
import com.cryptocarver.model.help.HelpContextState;
import com.cryptocarver.util.DataConverter;
import com.cryptocarver.utils.OperationHistory;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Button;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

/**
 * Controller for Cipher operations
 */
public class CipherController implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(CipherController.class);
    private final DialogService dialogService = new DialogService();

    @FXML private VBox cipherRoot;
    private ModuleI18n.Binding moduleI18n;
    @FXML private javafx.scene.layout.GridPane cipherWorkbench;
    @FXML private VBox symmetricConfig;
    @FXML private VBox cipherIoCard;
    @FXML private javafx.scene.layout.GridPane cipherIoGrid;
    @FXML private javafx.scene.control.Accordion cipherAccordion;
    @FXML private javafx.scene.layout.FlowPane symmetricActions;
    private final javafx.beans.property.BooleanProperty symmetricWorkspace =
            new javafx.beans.property.SimpleBooleanProperty(true);

    public javafx.beans.property.ReadOnlyBooleanProperty symmetricWorkspaceProperty() {
        return symmetricWorkspace;
    }

    public javafx.scene.layout.FlowPane detachSymmetricActions() {
        cipherRoot.getChildren().remove(symmetricActions);
        return symmetricActions;
    }

    public void showSymmetricWorkspace(boolean active) {
        symmetricWorkspace.set(active);
        symmetricConfig.setVisible(active);
        symmetricConfig.setManaged(active);
        cipherAccordion.setVisible(!active);
        cipherAccordion.setManaged(!active);
        javafx.scene.layout.ColumnConstraints inputColumn = new javafx.scene.layout.ColumnConstraints();
        inputColumn.setPercentWidth(active ? 100 : 50);
        cipherIoGrid.getColumnConstraints().setAll(inputColumn);
        if (!active) {
            javafx.scene.layout.ColumnConstraints outputColumn = new javafx.scene.layout.ColumnConstraints();
            outputColumn.setPercentWidth(50);
            cipherIoGrid.getColumnConstraints().add(outputColumn);
        }
        javafx.scene.layout.GridPane.setConstraints(cipherIoGrid.getChildren().get(1), active ? 0 : 1, active ? 1 : 0);
        layoutCipherWorkbench();
    }

    private void layoutCipherWorkbench() {
        boolean wide = symmetricWorkspace.get() && cipherWorkbench.getWidth() >= 740;
        javafx.scene.layout.ColumnConstraints first = new javafx.scene.layout.ColumnConstraints();
        first.setPercentWidth(wide ? 48 : 100);
        first.setMinWidth(0);
        cipherWorkbench.getColumnConstraints().setAll(first);
        if (wide) {
            javafx.scene.layout.ColumnConstraints second = new javafx.scene.layout.ColumnConstraints();
            second.setPercentWidth(52);
            second.setMinWidth(0);
            cipherWorkbench.getColumnConstraints().add(second);
        }
        javafx.scene.layout.GridPane.setConstraints(symmetricConfig, 0, 0);
        javafx.scene.layout.GridPane.setConstraints(cipherIoCard, wide ? 1 : 0,
                wide || !symmetricWorkspace.get() ? 0 : 1);
        javafx.scene.layout.GridPane.setValignment(cipherIoCard, javafx.geometry.VPos.TOP);
    }


    @FXML private TextArea cipherInputArea;
    @FXML private TextArea cipherOutputArea;
    @FXML private Button cipherCopyResultButton;
    @FXML private Button cipherShelfResultButton;
    @FXML private Button cipherExpandResultButton;
    @FXML private Button cipherSaveResultButton;
    @FXML private Button cipherUseResultButton;
    private ComboBox<String> cipherInputFormatCombo;
    private ComboBox<String> outputFormatCombo;
    private StatusReporter statusReporter;
    private java.util.function.Supplier<java.security.KeyPair> sharedKeyPairSupplier;
    private boolean preflightListenersInstalled;

    // Symmetric cipher UI components
    @FXML private ComboBox<String> cipherTemplateCombo;
    @FXML private MenuButton cipherTemplateMenu;
    @FXML private MenuItem saveCipherTemplateItem;
    @FXML private MenuItem exportCipherTemplateItem;
    @FXML private MenuItem importCipherTemplateItem;
    @FXML private MenuItem deleteCipherTemplateItem;
    @FXML private ComboBox<String> symmetricAlgorithmCombo;
    @FXML private ComboBox<String> cipherModeCombo;
    @FXML private ComboBox<String> paddingCombo;
    @FXML private ComboBox<String> symKeySourceCombo;
    @FXML private ComboBox<String> symHsmKeyCombo;
    @FXML private TextField symmetricKeyField;
    @FXML private TextField ivField;
    @FXML private TextField gcmTagField;
    @FXML private TextField aadField;
    @FXML private Button inspectKeyBtn;
    @FXML private Button saveToLabBtn;
    @FXML private Button pasteSymKeyBtn;
    @FXML private Button loadSymKeyBtn;
    @FXML private Button generateIvBtn;
    @FXML private Button applyCipherTemplateBtn;
    @FXML private Button resetCipherDefaultsBtn;
    @FXML private MenuButton symKeyShelfMenu;
    @FXML private MenuButton cipherPubKeyShelfMenu;
    @FXML private MenuButton cipherPrivKeyShelfMenu;
    @FXML private Label ivLabel;
    @FXML private HBox ivContainer;
    @FXML private Label gcmTagLabel;
    @FXML private Label aadLabel;
    @FXML private VBox ecbWarningBox;
    @FXML private Label aeadNoteLabel;
    @FXML private Label rsaPaddingWarningLabel;
    @FXML private Label rsaPaddingHelpLabel;
    @FXML private Label symKeyBadgeLabel;
    @FXML private Label ivBadgeLabel;
    @FXML private Label gcmTagBadgeLabel;
    @FXML private Label aadBadgeLabel;
    private HelpContextState helpContextState;
    private java.util.function.Consumer<HelpContext> helpContextOpener = ignored -> { };
    private java.util.function.Consumer<HelpContext> helpContextUpdater = ignored -> { };
    private boolean contextualHelpEnabled;
    private boolean contextualHelpRouteActive;
    private String activeHelpOperationId = "";
    private ContextualHelpButtonBinder moduleHelpButtons;
    private javafx.beans.value.ChangeListener<String> fpeHelpDirectionListener;
    private javafx.beans.value.ChangeListener<String> helpKeySourceListener;
    private javafx.beans.value.ChangeListener<String> helpHsmKeyListener;
    private javafx.beans.value.ChangeListener<String> helpManualKeyListener;
    private javafx.beans.value.ChangeListener<Boolean> fileCipherHelpLinesListener;
    private javafx.beans.value.ChangeListener<String> rsaHelpRefreshListener;

    // Format-preserving encryption workbench
    @FXML private ComboBox<String> fpeOperationCombo;
    @FXML private ComboBox<String> fpeAlgorithmCombo;
    @FXML private ComboBox<String> fpeAlphabetPresetCombo;
    @FXML private TextField fpeKeyField;
    @FXML private TextField fpeTweakField;
    @FXML private TextField fpeAlphabetField;
    @FXML private TextArea fpeInputArea;
    @FXML private TextArea fpeOutputArea;
    @FXML private Button fpeRunButton;

    // Streaming file cipher UI components
    @FXML private ComboBox<String> fileCipherAlgorithmCombo;
    @FXML private TextField fileCipherSourceField;
    @FXML private TextField fileCipherDestinationField;
    @FXML private TextField fileCipherTagField;
    @FXML private TextField fileCipherKeyField;
    @FXML private TextField fileCipherNonceField;
    @FXML private TextField fileCipherAadField;
    @FXML private TextArea fileCipherResultArea;
    @FXML private CheckBox fileCipherLinesCheck;
    @FXML private ComboBox<String> fileCipherLineEncodingCombo;
    @FXML private ComboBox<String> fileCipherLineCharsetCombo;
    @FXML private CheckBox fileCipherCompactCbcCheck;
    @FXML private Button fileCipherEncryptBtn;
    @FXML private Button fileCipherDecryptBtn;
    @FXML private Button fileCipherAnalyzeBtn;
    @FXML private Button fileCipherChooseSourceBtn;
    @FXML private Button fileCipherChooseDestinationBtn;
    @FXML private Button fileCipherChooseTagBtn;
    @FXML private Button fileCipherGenerateNonceBtn;
    @FXML private Button fileCipherExportRecipeBtn;
    @FXML private Button fileCipherImportRecipeBtn;
    private FileCipherCoordinator fileCipher;
    private AsymmetricCipherCoordinator rsa;
    private SymmetricCipherCoordinator symmetric;
    private CipherTemplateCoordinator templates;
    private SymmetricFieldsPresenter fields;
    private LabKeySelector labKeys;
    private FpeCoordinator fpe;

    // Asymmetric cipher UI components
    @FXML private ComboBox<String> rsaPaddingCombo;
    @FXML private ComboBox<String> asymmetricInputFormatCombo;
    @FXML private ComboBox<String> asymmetricOutputFormatCombo;
    @FXML private Button asymPastePublicKeyBtn;
    @FXML private Button asymLoadPublicKeyBtn;
    @FXML private Button asymPastePrivateKeyBtn;
    @FXML private Button asymLoadPrivateKeyBtn;
    @FXML private Button asymEncryptBtn;
    @FXML private Button asymDecryptBtn;

    // Key Input Areas (Manual Loading)
    @FXML private TextArea publicKeyArea;
    @FXML private TextArea privateKeyArea;
    @FXML private javafx.scene.layout.VBox openPgpContainer;
    @FXML private OpenPgpController openPgpContainerController;

    public CipherController() {
    }

    @FXML
    public void initialize() {
        moduleI18n = ModuleI18n.bind(cipherRoot, ModuleTextCatalog.cipher());
        setSymmetricAlgorithmCombo(symmetricAlgorithmCombo);
        setCipherModeCombo(cipherModeCombo);
        setPaddingCombo(paddingCombo);
        setSymmetricKeyField(symmetricKeyField);
        setSymKeySourceCombo(symKeySourceCombo);
        setSymHsmKeyCombo(symHsmKeyCombo);
        setIVField(ivField);
        setGcmTagField(gcmTagField);
        setAADField(aadField);
        fileCipher().configure();
        rsa().configure();

        fpe().configure();
        setupHexValidation(fpeKeyField);
        setupHexValidation(fpeTweakField);

        setupHexValidation(symmetricKeyField);
        setupHexValidation(ivField);
        setupHexValidation(gcmTagField);

        IngestionUIHelper.bindField(symmetricKeyField, null, com.cryptocarver.model.MaterialDetectionResult.MaterialType.HEX, com.cryptocarver.model.MaterialDetectionResult.MaterialType.BASE64, com.cryptocarver.model.MaterialDetectionResult.MaterialType.TEXT_UNKNOWN);
        IngestionUIHelper.bindField(ivField, null, com.cryptocarver.model.MaterialDetectionResult.MaterialType.HEX);
        IngestionUIHelper.bindField(publicKeyArea, null, com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_PUBLIC_KEY, com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_CERTIFICATE, com.cryptocarver.model.MaterialDetectionResult.MaterialType.HEX);
        IngestionUIHelper.bindField(privateKeyArea, null, com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_PRIVATE_KEY, com.cryptocarver.model.MaterialDetectionResult.MaterialType.HEX);
        IngestionUIHelper.bindField(cipherInputArea, null, com.cryptocarver.model.MaterialDetectionResult.MaterialType.TEXT_UNKNOWN, com.cryptocarver.model.MaterialDetectionResult.MaterialType.HEX, com.cryptocarver.model.MaterialDetectionResult.MaterialType.BASE64);

        symmetricActions.visibleProperty().bind(symmetricWorkspace);
        symmetricActions.managedProperty().bind(symmetricActions.visibleProperty());
        cipherWorkbench.widthProperty().addListener((obs, before, after) -> layoutCipherWorkbench());
        // Hide the whole row, including badges and spacing, when a mode does not use it.
        for (javafx.scene.Node field : java.util.List.of(ivContainer, gcmTagField, aadField)) {
            field.getParent().visibleProperty().bind(field.visibleProperty());
            field.getParent().managedProperty().bind(field.visibleProperty());
        }
        showSymmetricWorkspace(true);
        templates().refreshCipherTemplateCombo();
        fields().updateModeAndAlgorithmVisibility();
    }

    public void configureContextualHelp(HelpContextState state, java.util.function.Consumer<HelpContext> opener,
            java.util.function.Consumer<HelpContext> updater) {
        helpContextState = state;
        helpContextOpener = opener == null ? ignored -> { } : opener;
        helpContextUpdater = updater == null ? ignored -> { } : updater;
        if (moduleHelpButtons == null && state != null) installCipherModuleHelpButtons();
        if (openPgpContainerController != null) openPgpContainerController.configureContextualHelp(state, helpContextOpener,
                this::enrichPublishedHelpContext);
    }

    public void setContextualHelpEnabled(boolean enabled) {
        contextualHelpEnabled = enabled;
        if (moduleHelpButtons != null) moduleHelpButtons.setEnabled(enabled);
        if (openPgpContainerController != null) openPgpContainerController.setContextualHelpEnabled(
                enabled && "op_openpgp".equals(activeHelpOperationId));
    }

    public void setContextualHelpOperationActive(String operationId) {
        activeHelpOperationId = operationId == null ? "" : operationId;
        if (moduleHelpButtons != null) moduleHelpButtons.setActiveRoute(activeHelpOperationId);
        if (openPgpContainerController != null) openPgpContainerController.setContextualHelpRouteActive(
                "op_openpgp".equals(activeHelpOperationId));
        if (openPgpContainerController != null) openPgpContainerController.setContextualHelpEnabled(
                contextualHelpEnabled && "op_openpgp".equals(activeHelpOperationId));
    }

    private void installCipherModuleHelpButtons() {
        moduleHelpButtons = new ContextualHelpButtonBinder(helpContextState, helpContextOpener, this::enrichPublishedHelpContext);
        installSymmetricHelpBindings();
        String fpe = "op_fpe", file = "op_sym_file", rsaOp = "op_asym_ciphers", analysis = "aux_encrypted_file_analysis";
        moduleHelpButtons.bind(fpe, fpe, "select_algorithm", "operation", fpeOperationCombo);
        moduleHelpButtons.bind(fpe, fpe, "select_algorithm", "algorithm", fpeAlgorithmCombo);
        moduleHelpButtons.bind(fpe, fpe, "select_alphabet", "alphabet_preset", fpeAlphabetPresetCombo);
        moduleHelpButtons.bind(fpe, fpe, "select_alphabet", "alphabet", fpeAlphabetField);
        moduleHelpButtons.bind(fpe, fpe, "encrypt", "key", fpeKeyField);
        moduleHelpButtons.bind(fpe, fpe, "select_algorithm", "tweak", fpeTweakField);
        moduleHelpButtons.bind(fpe, fpe, "encrypt", "input", fpeInputArea);
        moduleHelpButtons.bind(fpe, fpe, "encrypt", "output", fpeOutputArea);
        moduleHelpButtons.bind(fpe, fpe, () -> "DECRYPT".equals(fpeOperationCombo.getValue()) ? "decrypt" : "encrypt", "", fpeRunButton);

        moduleHelpButtons.bind(file, file, "select_profile", "profile", fileCipherAlgorithmCombo);
        moduleHelpButtons.bind(file, file, "choose_source", "source", fileCipherSourceField);
        moduleHelpButtons.bind(file, file, "choose_source", "", fileCipherChooseSourceBtn);
        moduleHelpButtons.bind(file, file, "choose_destination", "destination", fileCipherDestinationField);
        moduleHelpButtons.bind(file, file, "choose_destination", "", fileCipherChooseDestinationBtn);
        moduleHelpButtons.bind(file, file, "choose_tag", "tag_file", fileCipherTagField);
        moduleHelpButtons.bind(file, file, "choose_tag", "", fileCipherChooseTagBtn);
        moduleHelpButtons.bind(file, file, this::fileFieldHelpActionId, "key", fileCipherKeyField);
        moduleHelpButtons.bind(file, file, this::fileFieldHelpActionId, "iv_nonce", fileCipherNonceField);
        moduleHelpButtons.bind(file, file, "generate_nonce", "", fileCipherGenerateNonceBtn);
        moduleHelpButtons.bind(file, file, this::fileFieldHelpActionId, "aad", fileCipherAadField);
        moduleHelpButtons.bind(file, file, "select_line_options", "lines_mode", fileCipherLinesCheck);
        moduleHelpButtons.bind(file, file, "select_line_options", "line_encoding", fileCipherLineEncodingCombo);
        moduleHelpButtons.bind(file, file, "select_line_options", "charset", fileCipherLineCharsetCombo);
        moduleHelpButtons.bind(file, file, "select_line_options", "compact_cbc", fileCipherCompactCbcCheck);
        moduleHelpButtons.bind(file, file, () -> fileCipherLinesCheck.isSelected() ? "encrypt_lines" : "encrypt_file", "", fileCipherEncryptBtn);
        moduleHelpButtons.bind(file, file, () -> fileCipherLinesCheck.isSelected() ? "decrypt_lines" : "decrypt_file", "", fileCipherDecryptBtn);
        moduleHelpButtons.bind(file, analysis, "analyze", "file_representation", fileCipherAnalyzeBtn);
        moduleHelpButtons.bind(file, file, "export_recipe", "", fileCipherExportRecipeBtn);
        moduleHelpButtons.bind(file, file, "import_recipe", "", fileCipherImportRecipeBtn);
        moduleHelpButtons.bind(file, file, this::fileFieldHelpActionId, "result", fileCipherResultArea);

        moduleHelpButtons.bind(rsaOp, rsaOp, "select_padding", "padding", rsaPaddingCombo);
        moduleHelpButtons.bind(rsaOp, rsaOp, "encrypt", "input_format", asymmetricInputFormatCombo);
        moduleHelpButtons.bind(rsaOp, rsaOp, "encrypt", "output_format", asymmetricOutputFormatCombo);
        moduleHelpButtons.bind(rsaOp, rsaOp, "load_public_key", "public_key", publicKeyArea);
        moduleHelpButtons.bind(rsaOp, rsaOp, "load_public_key", "", asymPastePublicKeyBtn);
        moduleHelpButtons.bind(rsaOp, rsaOp, "load_public_key", "", asymLoadPublicKeyBtn);
        moduleHelpButtons.bind(rsaOp, rsaOp, "use_public_key_shelf", "", cipherPubKeyShelfMenu);
        moduleHelpButtons.bind(rsaOp, rsaOp, "load_private_key", "private_key", privateKeyArea);
        moduleHelpButtons.bind(rsaOp, rsaOp, "load_private_key", "", asymPastePrivateKeyBtn);
        moduleHelpButtons.bind(rsaOp, rsaOp, "load_private_key", "", asymLoadPrivateKeyBtn);
        moduleHelpButtons.bind(rsaOp, rsaOp, "use_private_key_shelf", "", cipherPrivKeyShelfMenu);
        moduleHelpButtons.bind(rsaOp, rsaOp, "encrypt", "input", cipherInputArea);
        moduleHelpButtons.bind(rsaOp, rsaOp, "encrypt", "output", cipherOutputArea);
        moduleHelpButtons.bind(rsaOp, rsaOp, "encrypt", "", asymEncryptBtn);
        moduleHelpButtons.bind(rsaOp, rsaOp, "decrypt", "", asymDecryptBtn);
        fpeHelpDirectionListener = (observable, before, after) -> moduleHelpButtons.refreshLocalization();
        if (fpeOperationCombo != null) fpeOperationCombo.valueProperty().addListener(fpeHelpDirectionListener);
        fileCipherHelpLinesListener = (observable, before, after) -> {
            moduleHelpButtons.refreshLocalization();
            refreshFileCipherHelpContext(Boolean.TRUE.equals(after));
        };
        if (fileCipherLinesCheck != null) fileCipherLinesCheck.selectedProperty().addListener(fileCipherHelpLinesListener);
        rsaHelpRefreshListener = (observable, before, after) -> refreshRsaHelpContext();
        if (rsaPaddingCombo != null) rsaPaddingCombo.valueProperty().addListener(rsaHelpRefreshListener);
        if (publicKeyArea != null) publicKeyArea.textProperty().addListener(rsaHelpRefreshListener);
    }

    private void installSymmetricHelpBindings() {
        String sym = "op_sym_ciphers";
        // Selector codes and the Key Lab/manual key field depend on live state, so they are resolved at publish time.
        java.util.function.UnaryOperator<HelpContext> live = next -> {
            boolean hsm = symKeySourceCombo != null && "Simulated HSM".equals(symKeySourceCombo.getValue());
            String field = "manual_key".equals(next.fieldId()) && hsm ? "key_lab_key" : next.fieldId();
            return new HelpContext(next.operationId(), next.actionId(), "", next.locale(), field, currentPublicHelpSelections());
        };
        moduleHelpButtons.bind(sym, sym, "encrypt", "algorithm", symmetricAlgorithmCombo, live);
        moduleHelpButtons.bind(sym, sym, "encrypt", "mode", cipherModeCombo, live);
        moduleHelpButtons.bind(sym, sym, "encrypt", "padding", paddingCombo, live);
        moduleHelpButtons.bind(sym, sym, "select_key_source", "key_source", symKeySourceCombo, live);
        moduleHelpButtons.bind(sym, sym, "encrypt", "manual_key", symmetricKeyField, live);
        moduleHelpButtons.bind(sym, sym, "encrypt", "key_lab_key", symHsmKeyCombo, live);
        moduleHelpButtons.bind(sym, sym, "inspect_key", "manual_key", inspectKeyBtn, live);
        moduleHelpButtons.bind(sym, sym, "save_key_lab", "manual_key", saveToLabBtn, live);
        moduleHelpButtons.bind(sym, sym, "paste_key", "manual_key", pasteSymKeyBtn, live);
        moduleHelpButtons.bind(sym, sym, "load_key", "manual_key", loadSymKeyBtn, live);
        moduleHelpButtons.bind(sym, sym, "use_key_shelf", "manual_key", symKeyShelfMenu, live);
        moduleHelpButtons.bind(sym, sym, "encrypt", "iv_nonce", ivField, live);
        moduleHelpButtons.bind(sym, sym, "generate_iv", "iv_nonce", generateIvBtn, live);
        moduleHelpButtons.bind(sym, sym, "decrypt", "tag", gcmTagField, live);
        moduleHelpButtons.bind(sym, sym, "encrypt", "aad", aadField, live);
        moduleHelpButtons.bind(sym, sym, "encrypt", "payload", cipherInputArea, live);
        moduleHelpButtons.bind(sym, sym, "encrypt", "output", cipherOutputArea, live);
        moduleHelpButtons.bind(sym, sym, "copy_result", "output", cipherCopyResultButton, live);
        moduleHelpButtons.bind(sym, sym, "shelf_result", "output", cipherShelfResultButton, live);
        moduleHelpButtons.bind(sym, sym, "expand_result", "output", cipherExpandResultButton, live);
        moduleHelpButtons.bind(sym, sym, "save_result", "output", cipherSaveResultButton, live);
        moduleHelpButtons.bind(sym, sym, "reuse_result", "output", cipherUseResultButton, live);
        if (symmetricActions != null) {
            moduleHelpButtons.bind(sym, sym, "encrypt", "", symmetricActions.lookup("#symmetricEncryptButton"), live);
            moduleHelpButtons.bind(sym, sym, "decrypt", "", symmetricActions.lookup("#symmetricDecryptButton"), live);
        }
        moduleHelpButtons.bind(sym, sym, "manage_templates", "template_selection", cipherTemplateCombo, live);
        moduleHelpButtons.bind(sym, sym, "apply_template", "template_selection", applyCipherTemplateBtn, live);
        moduleHelpButtons.bind(sym, sym, "manage_templates", "template_selection", cipherTemplateMenu, live);
        moduleHelpButtons.bind(sym, sym, "reset_defaults", "template_selection", resetCipherDefaultsBtn, live);
    }

    /** Completes a context published by focus or Shift+F1 with module-derived selections and remembers it. */
    private void enrichPublishedHelpContext(HelpContext published) {
        HelpContext enriched = published;
        if ("op_sym_file".equals(published.operationId()) && fileCipherLinesCheck != null) {
            enriched = published.withSelection(HelpContext.Selection.FILE_LINES,
                    HelpContext.PublicSelection.flag(fileCipherLinesCheck.isSelected()));
        } else if ("op_asym_ciphers".equals(published.operationId())) {
            int bits = rsa().publicModulusBitsForHelp();
            enriched = published.withSelection(HelpContext.Selection.RSA_MODULUS_BITS,
                    bits > 0 ? HelpContext.PublicSelection.byteSize(bits) : null);
        }
        helpContextUpdater.accept(enriched);
    }

    public ComboBox<String> getFpeOperationCombo() { return fpeOperationCombo; }
    public ComboBox<String> getFpeAlgorithmCombo() { return fpeAlgorithmCombo; }
    public ComboBox<String> getFpeAlphabetPresetCombo() { return fpeAlphabetPresetCombo; }
    public TextField getFpeKeyField() { return fpeKeyField; }
    public TextField getFpeTweakField() { return fpeTweakField; }
    public TextField getFpeAlphabetField() { return fpeAlphabetField; }
    public ComboBox<String> getFileCipherAlgorithmCombo() { return fileCipherAlgorithmCombo; }
    public CheckBox getFileCipherLinesCheck() { return fileCipherLinesCheck; }
    public ComboBox<String> getFileCipherLineEncodingCombo() { return fileCipherLineEncodingCombo; }
    public ComboBox<String> getFileCipherLineCharsetCombo() { return fileCipherLineCharsetCombo; }
    public CheckBox getFileCipherCompactCbcCheck() { return fileCipherCompactCbcCheck; }
    public ComboBox<String> getRsaPaddingCombo() { return rsaPaddingCombo; }
    public ComboBox<String> getAsymmetricInputFormatCombo() { return asymmetricInputFormatCombo; }
    public ComboBox<String> getAsymmetricOutputFormatCombo() { return asymmetricOutputFormatCombo; }
    public TextArea getPublicKeyArea() { return publicKeyArea; }
    public TextArea getPrivateKeyArea() { return privateKeyArea; }
    public TextField getSymmetricKeyField() { return symmetricKeyField; }
    public TextField getFileCipherKeyField() { return fileCipherKeyField; }
    public TextField getFileCipherNonceField() { return fileCipherNonceField; }
    public TextField getFileCipherTagField() { return fileCipherTagField; }
    public TextField getFileCipherAadField() { return fileCipherAadField; }
    public TextField getIvField() { return ivField; }
    public TextField getGcmTagField() { return gcmTagField; }
    public TextField getAadField() { return aadField; }
    public int getRsaPublicModulusBitsForHelp() { return rsa().publicModulusBitsForHelp(); }

    private String fileFieldHelpActionId() {
        boolean decrypt = helpContextState != null
                && "op_sym_file".equals(helpContextState.current().operationId())
                && helpContextState.current().actionId().startsWith("decrypt_");
        return (decrypt ? "decrypt" : "encrypt") + (fileCipherLinesCheck.isSelected() ? "_lines" : "_file");
    }

    private void refreshFileCipherHelpContext(boolean linesMode) {
        if (!contextualHelpEnabled || !"op_sym_file".equals(activeHelpOperationId) || helpContextState == null) return;
        HelpContext current = helpContextState.current();
        if (!"op_sym_file".equals(current.operationId())) return;
        String action = current.actionId();
        boolean encryptAction = action.startsWith("encrypt_");
        boolean decryptAction = action.startsWith("decrypt_");
        if (!encryptAction && !decryptAction) return;
        String direction = decryptAction ? "decrypt" : "encrypt";
        String variant = linesMode ? "_lines" : "_file";
        HelpContext updated = current.withAction(direction + variant)
                .withSelection(HelpContext.Selection.FILE_LINES, HelpContext.PublicSelection.flag(linesMode));
        helpContextUpdater.accept(updated);
    }

    @Override public void close() {
        closeContextualHelpBindings();
        if (moduleHelpButtons != null) moduleHelpButtons.close();
        if (fileCipherHelpLinesListener != null && fileCipherLinesCheck != null)
            fileCipherLinesCheck.selectedProperty().removeListener(fileCipherHelpLinesListener);
        if (fpeHelpDirectionListener != null && fpeOperationCombo != null)
            fpeOperationCombo.valueProperty().removeListener(fpeHelpDirectionListener);
        if (rsaHelpRefreshListener != null) {
            if (rsaPaddingCombo != null) rsaPaddingCombo.valueProperty().removeListener(rsaHelpRefreshListener);
            if (publicKeyArea != null) publicKeyArea.textProperty().removeListener(rsaHelpRefreshListener);
        }
        if (openPgpContainerController != null) openPgpContainerController.closeContextualHelpBindings();
    }

    public void setContextualHelpRouteActive(boolean active) {
        contextualHelpRouteActive = active;
        if (active) installHelpMaterialListeners();
        else removeHelpMaterialListeners();
    }

    public void refreshHelpButtonLocalization() {
        if (moduleHelpButtons != null) moduleHelpButtons.refreshLocalization();
        if (openPgpContainerController != null) openPgpContainerController.refreshContextualHelpLocalization();
        if (cipherTemplateMenu != null) {
            var i18n = com.cryptocarver.service.I18nService.getInstance();
            cipherTemplateMenu.setAccessibleText(i18n.text("help.action.op_sym_ciphers.manage_templates.title"));
            cipherTemplateMenu.setAccessibleHelp(i18n.text("help.action.op_sym_ciphers.manage_templates.description"));
        }
        String keyField = symKeySourceCombo != null && "Simulated HSM".equals(symKeySourceCombo.getValue())
                ? "key_lab_key" : "manual_key";
    }

    private void installHelpMaterialListeners() {
        if (helpKeySourceListener != null) return;
        helpKeySourceListener = (observable, before, after) -> {
            labKeys().updateKeySourceVisibility();
            updatePublicKeySource();
            updatePublicMaterialPresence();
            refreshHelpButtonLocalization();
        };
        helpHsmKeyListener = (observable, before, after) -> updatePublicMaterialPresence();
        helpManualKeyListener = (observable, before, after) -> updatePublicMaterialPresence();
        symKeySourceCombo.valueProperty().addListener(helpKeySourceListener);
        symHsmKeyCombo.valueProperty().addListener(helpHsmKeyListener);
        symmetricKeyField.textProperty().addListener(helpManualKeyListener);
    }

    private void updatePublicKeySource() {
        if (helpContextState == null || !"op_sym_ciphers".equals(helpContextState.current().operationId())) return;
        String source = symKeySourceCombo == null ? null : symKeySourceCombo.getValue();
        if (source != null) helpContextState.setSelection(HelpContext.Selection.KEY_SOURCE,
                HelpContext.PublicSelection.of(com.cryptocarver.model.help.OperationHelpRegistry.slug(source)));
        HelpContext current = helpContextState.current();
        if ("encrypt".equals(current.actionId()) || "decrypt".equals(current.actionId())) {
            if ("manual_key".equals(current.fieldId()) && "Simulated HSM".equals(source)) helpContextState.setField("key_lab_key");
            else if ("key_lab_key".equals(current.fieldId()) && !"Simulated HSM".equals(source)) helpContextState.setField("manual_key");
        }
    }

    private void updatePublicMaterialPresence() {
        if (helpContextState == null || !"op_sym_ciphers".equals(helpContextState.current().operationId())) return;
        boolean hsm = symKeySourceCombo != null && "Simulated HSM".equals(symKeySourceCombo.getValue());
        boolean present;
        if (hsm) {
            String keyId = symHsmKeyCombo == null ? null : symHsmKeyCombo.getValue();
            var metadata = keyId == null ? null : com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().getKeyMetadata(keyId);
            present = metadata != null && metadata.hasKeyMaterial();
        } else present = symmetricKeyField != null && !symmetricKeyField.getText().isBlank();
        helpContextState.setSelection(HelpContext.Selection.MATERIAL_PRESENT, HelpContext.PublicSelection.flag(present));
    }

    public void closeContextualHelpBindings() {
        removeHelpMaterialListeners();
        if (fpeHelpDirectionListener != null && fpeOperationCombo != null)
            fpeOperationCombo.valueProperty().removeListener(fpeHelpDirectionListener);
        fpeHelpDirectionListener = null;
    }

    private void removeHelpMaterialListeners() {
        if (helpKeySourceListener != null && symKeySourceCombo != null) symKeySourceCombo.valueProperty().removeListener(helpKeySourceListener);
        if (helpHsmKeyListener != null && symHsmKeyCombo != null) symHsmKeyCombo.valueProperty().removeListener(helpHsmKeyListener);
        if (helpManualKeyListener != null && symmetricKeyField != null) symmetricKeyField.textProperty().removeListener(helpManualKeyListener);
        helpKeySourceListener = null; helpHsmKeyListener = null; helpManualKeyListener = null;
    }

    /** Re-seeds only public selector codes and derived material availability after returning from an auxiliary help view. */
    public void publishCurrentHelpSelections() {
        if (!contextualHelpRouteActive || helpContextState == null
                || !"op_sym_ciphers".equals(helpContextState.current().operationId())) return;
        HelpContext current = helpContextState.current();
        HelpContext restored = new HelpContext(current.operationId(), current.actionId(), current.variantId(),
                current.locale(), current.fieldId(), currentPublicHelpSelections());
        helpContextState.replace(restored);
    }

    private java.util.EnumMap<HelpContext.Selection, HelpContext.PublicSelection> currentPublicHelpSelections() {
        java.util.EnumMap<HelpContext.Selection, HelpContext.PublicSelection> selections =
                new java.util.EnumMap<>(HelpContext.Selection.class);
        putSelection(selections, HelpContext.Selection.ALGORITHM, symmetricAlgorithmCombo == null ? null : symmetricAlgorithmCombo.getValue());
        putSelection(selections, HelpContext.Selection.MODE, cipherModeCombo == null ? null : cipherModeCombo.getValue());
        putSelection(selections, HelpContext.Selection.PADDING, paddingCombo == null ? null : paddingCombo.getValue());
        putSelection(selections, HelpContext.Selection.KEY_SOURCE, symKeySourceCombo == null ? null : symKeySourceCombo.getValue());
        boolean hsm = symKeySourceCombo != null && "Simulated HSM".equals(symKeySourceCombo.getValue());
        boolean materialPresent;
        if (hsm) {
            String keyId = symHsmKeyCombo == null ? null : symHsmKeyCombo.getValue();
            var metadata = keyId == null ? null : com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().getKeyMetadata(keyId);
            materialPresent = metadata != null && metadata.hasKeyMaterial();
        } else {
            materialPresent = symmetricKeyField != null && !symmetricKeyField.getText().isBlank();
        }
        selections.put(HelpContext.Selection.MATERIAL_PRESENT, HelpContext.PublicSelection.flag(materialPresent));
        return selections;
    }

    private static void putSelection(java.util.Map<HelpContext.Selection, HelpContext.PublicSelection> values,
                                     HelpContext.Selection key, String code) {
        if (code != null && !code.isBlank()) {
            values.put(key, HelpContext.PublicSelection.of(com.cryptocarver.model.help.OperationHelpRegistry.slug(code)));
        }
    }

    @FXML
    private void handleCopyCipherResult() {
        if (statusReporter != null) {
            statusReporter.copyCurrentResult();
        } else {
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(cipherOutputArea.getText());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
        }
    }

    @FXML
    private void handleShelfCipherResult() {
        if (statusReporter != null) statusReporter.addCurrentResultToShelf();
    }

    @FXML
    private void handleExpandCipherResult() {
        if (statusReporter != null) statusReporter.expandCurrentResult();
    }

    @FXML
    private void handleSaveCipherResult() {
        if (statusReporter != null) statusReporter.saveCurrentResultAsSessionStep();
    }

    @FXML
    private void handleUseCipherResultAsInput() {
        cipherInputArea.setText(cipherOutputArea.getText());
        if (statusReporter instanceof ModernMainController modern) modern.revealCipherEditor(cipherInputArea);
    }

    @FXML
    public void handlePasteSymKey() {
        IngestionUIHelper.pasteFromClipboard(symmetricKeyField, null, null,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.HEX,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.BASE64,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.TEXT_UNKNOWN);
    }

    @FXML
    public void handleLoadSymKey() {
        IngestionUIHelper.loadFile(symmetricKeyField != null && symmetricKeyField.getScene() != null ? symmetricKeyField.getScene().getWindow() : null,
                symmetricKeyField, null, null,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.HEX,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.BASE64,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.TEXT_UNKNOWN);
    }

    @FXML
    public void handlePopulateSymKeyShelf() {
        IngestionUIHelper.populateShelfMenu(symKeyShelfMenu, symmetricKeyField, null, null,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.HEX,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.BASE64,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.TEXT_UNKNOWN);
    }

    @FXML
    public void handlePastePublicKey() {
        IngestionUIHelper.pasteFromClipboard(publicKeyArea, null, () -> handleLoadPublicKey(),
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_PUBLIC_KEY,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_CERTIFICATE,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.HEX);
    }

    @FXML
    public void handlePopulateCipherPubKeyShelf() {
        IngestionUIHelper.populateShelfMenu(cipherPubKeyShelfMenu, publicKeyArea, null, () -> handleLoadPublicKey(),
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_PUBLIC_KEY,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_CERTIFICATE,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.HEX);
    }

    @FXML
    public void handlePastePrivateKey() {
        IngestionUIHelper.pasteFromClipboard(privateKeyArea, null, () -> handleLoadPrivateKey(),
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_PRIVATE_KEY,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.HEX);
    }

    @FXML
    public void handlePopulateCipherPrivKeyShelf() {
        IngestionUIHelper.populateShelfMenu(cipherPrivKeyShelfMenu, privateKeyArea, null, () -> handleLoadPrivateKey(),
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_PRIVATE_KEY,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.HEX);
    }

    @FXML
    private void handleApplyCipherTemplate() {
        templates().handleApplyCipherTemplate();
    }

    @FXML
    private void handleSaveCipherTemplate() {
        templates().handleSaveCipherTemplate();
    }

    @FXML
    private void handleExportCipherTemplate() {
        templates().handleExportCipherTemplate();
    }

    @FXML
    private void handleImportCipherTemplate() {
        templates().handleImportCipherTemplate();
    }

    @FXML
    private void handleDeleteCipherTemplate() {
        templates().handleDeleteCipherTemplate();
    }

    @FXML
    private void handleResetCipherDefaults() {
        templates().handleResetCipherDefaults();
    }

    public void initModern(StatusReporter reporter,
            ComboBox<String> globalInputFormatCombo,
            ComboBox<String> globalOutputFormatCombo,
            java.util.function.Supplier<java.security.KeyPair> keyPairSupplier) {
        this.statusReporter = reporter;
        this.cipherInputFormatCombo = globalInputFormatCombo;
        this.outputFormatCombo = globalOutputFormatCombo;
        this.sharedKeyPairSupplier = keyPairSupplier;
        asymmetricInputFormatCombo.valueProperty().bindBidirectional(globalInputFormatCombo.valueProperty());
        asymmetricOutputFormatCombo.valueProperty().bindBidirectional(globalOutputFormatCombo.valueProperty());
        // Same choices as the toolbar the panel's format selectors are bound to.
        asymmetricInputFormatCombo.setItems(globalInputFormatCombo.getItems());
        asymmetricOutputFormatCombo.setItems(globalOutputFormatCombo.getItems());
        if (openPgpContainerController != null) {
            openPgpContainerController.setStatusReporter(reporter);
        }
        installPreflightListeners();
    }

    private void installPreflightListeners() {
        if (preflightListenersInstalled) return;
        preflightListenersInstalled = true;
        observePreflight(cipherInputArea);
        observePreflight(symmetricKeyField);
        observePreflight(ivField);
        observePreflight(gcmTagField);
        observePreflight(aadField);
        observePreflight(publicKeyArea);
        observePreflight(privateKeyArea);
        observePreflight(symmetricAlgorithmCombo);
        observePreflight(cipherModeCombo);
        observePreflight(paddingCombo);
        observePreflight(symKeySourceCombo);
        observePreflight(symHsmKeyCombo);
        observePreflight(rsaPaddingCombo);
    }

    private void observePreflight(javafx.scene.control.TextInputControl control) {
        if (control != null) control.textProperty().addListener((obs, previous, value) -> refreshPreflight());
    }

    private void observePreflight(ComboBox<?> control) {
        if (control != null) control.valueProperty().addListener((obs, previous, value) -> refreshPreflight());
    }

    private void refreshPreflight() {
        if (statusReporter instanceof ModernMainController modern) modern.updateReadinessPanel();
    }

    public CipherController(StatusReporter statusReporter,
            TextArea inputArea,
            TextArea outputArea,
            ComboBox<String> inputFormatCombo,
            ComboBox<String> outputFormatCombo) {
        this(statusReporter, inputArea, outputArea, inputFormatCombo, outputFormatCombo, null, null);
    }

    public CipherController(StatusReporter statusReporter,
            TextArea inputArea,
            TextArea outputArea,
            ComboBox<String> inputFormatCombo,
            ComboBox<String> outputFormatCombo,
            TextArea publicKeyArea,
            TextArea privateKeyArea) {
        this.statusReporter = statusReporter;
        this.cipherInputArea = inputArea;
        this.cipherOutputArea = outputArea;
        this.cipherInputFormatCombo = inputFormatCombo;
        this.outputFormatCombo = outputFormatCombo;
        this.publicKeyArea = publicKeyArea;
        this.privateKeyArea = privateKeyArea;
    }

    @FXML
    private void handleReadPublicKeyFile() {
        rsa().readPublicKeyFile();
    }

    @FXML
    private void handleReadPrivateKeyFile() {
        rsa().readPrivateKeyFile();
    }

    /**
     * Set symmetric algorithm ComboBox
     */
    public void setSymmetricAlgorithmCombo(ComboBox<String> combo) {
        this.symmetricAlgorithmCombo = combo;
        symmetricAlgorithmCombo.getItems().addAll(SymmetricCipher.SUPPORTED_ALGORITHMS);
        symmetricAlgorithmCombo.setValue("AES-256");

        // Add listener to update algorithm and mode UI visibility
        symmetricAlgorithmCombo.valueProperty().addListener((obs, oldVal, newVal) -> fields().updateModeAndAlgorithmVisibility());
        symmetricAlgorithmCombo.setOnAction(e -> fields().updateModeAndAlgorithmVisibility());
    }

    private FileCipherCoordinator fileCipher() {
        if (fileCipher == null) {
            fileCipher = new FileCipherCoordinator(new FileCipherCoordinator.View(fileCipherAlgorithmCombo,
                    fileCipherSourceField, fileCipherDestinationField, fileCipherTagField, fileCipherKeyField,
                    fileCipherNonceField, fileCipherAadField, fileCipherResultArea, fileCipherLinesCheck,
                    fileCipherLineEncodingCombo, fileCipherLineCharsetCombo, fileCipherCompactCbcCheck,
                    fileCipherEncryptBtn, fileCipherDecryptBtn), () -> statusReporter, dialogService);
        }
        return fileCipher;
    }

    public void handleFileCipherEncrypt() { fileCipher().handleFileCipherEncrypt(); }

    public void handleFileCipherDecrypt() { fileCipher().handleFileCipherDecrypt(); }

    public void handleExportFileCipherRecipe() { fileCipher().handleExportFileCipherRecipe(); }

    public void handleImportFileCipherRecipe() { fileCipher().handleImportFileCipherRecipe(); }

    public void chooseFileCipherSource() { fileCipher().chooseFileCipherSource(); }

    public void chooseFileCipherDestination() { fileCipher().chooseFileCipherDestination(); }

    public void chooseFileCipherTag() { fileCipher().chooseFileCipherTag(); }

    public void generateFileCipherNonce() { fileCipher().generateFileCipherNonce(); }

    private CipherTemplateCoordinator templates() {
        if (templates == null) {
            templates = new CipherTemplateCoordinator(new CipherTemplateCoordinator.View(cipherTemplateCombo,
                    symmetricAlgorithmCombo, cipherModeCombo, paddingCombo, symKeySourceCombo, symmetricKeyField,
                    ivField, gcmTagField, aadField, rsaPaddingCombo, asymmetricInputFormatCombo,
                    asymmetricOutputFormatCombo),
                    () -> statusReporter, () -> cipherInputFormatCombo, () -> outputFormatCombo);
        }
        return templates;
    }

    private SymmetricFieldsPresenter fields() {
        if (fields == null) {
            fields = new SymmetricFieldsPresenter(new SymmetricFieldsPresenter.View(symmetricAlgorithmCombo,
                    cipherModeCombo, paddingCombo, symKeySourceCombo, symHsmKeyCombo, symmetricKeyField, ivField,
                    gcmTagField, aadField, ivLabel, ivContainer, gcmTagLabel, aadLabel, ecbWarningBox, aeadNoteLabel,
                    symKeyBadgeLabel, ivBadgeLabel, gcmTagBadgeLabel, aadBadgeLabel));
        }
        return fields;
    }

    @FXML
    public void generateIV() { fields().generateIV(); }

    private LabKeySelector labKeys() {
        if (labKeys == null) {
            labKeys = new LabKeySelector(new LabKeySelector.View(symKeySourceCombo, symHsmKeyCombo, symmetricKeyField,
                    symmetricAlgorithmCombo, inspectKeyBtn, saveToLabBtn),
                    () -> statusReporter, () -> fields().updateModeAndAlgorithmVisibility());
        }
        return labKeys;
    }

    @FXML
    private void handleInspectKey() { labKeys().handleInspectKey(); }

    public void refreshHsmKeys() { labKeys().refreshHsmKeys(); }

    /** Selects a usable Key Lab key for symmetric operations without revealing its bytes. */
    public void selectLabKey(String keyId) { labKeys().selectLabKey(keyId); }

    @FXML
    public void saveCurrentKeyToHsm() { labKeys().saveCurrentKeyToHsm(); }

    private FpeCoordinator fpe() {
        if (fpe == null) {
            fpe = new FpeCoordinator(new FpeCoordinator.View(fpeOperationCombo, fpeAlgorithmCombo, fpeAlphabetPresetCombo,
                    fpeKeyField, fpeTweakField, fpeAlphabetField, fpeInputArea, fpeOutputArea), () -> statusReporter);
        }
        return fpe;
    }

    @FXML
    public void handleFpe() { fpe().handleFpe(); }

    private SymmetricCipherCoordinator symmetric() {
        if (symmetric == null) {
            symmetric = new SymmetricCipherCoordinator(new SymmetricCipherCoordinator.View(
                    symmetricAlgorithmCombo, cipherModeCombo, paddingCombo, symKeySourceCombo, symHsmKeyCombo,
                    symmetricKeyField, ivField, gcmTagField, aadField, cipherInputArea, cipherOutputArea),
                    () -> statusReporter, () -> cipherInputFormatCombo, () -> outputFormatCombo);
        }
        return symmetric;
    }

    public void handleSymmetricEncrypt() { symmetric().handleSymmetricEncrypt(); }

    public void handleSymmetricDecrypt() { symmetric().handleSymmetricDecrypt(); }

    public com.cryptocarver.model.ShelfPackage createAuthenticatedCipherShelfPackage() {
        return symmetric().createAuthenticatedCipherShelfPackage();
    }

    private AsymmetricCipherCoordinator rsa() {
        if (rsa == null) {
            rsa = new AsymmetricCipherCoordinator(new AsymmetricCipherCoordinator.View(rsaPaddingCombo,
                    asymmetricInputFormatCombo, asymmetricOutputFormatCombo, rsaPaddingWarningLabel,
                    rsaPaddingHelpLabel, publicKeyArea, privateKeyArea, cipherInputArea, cipherOutputArea),
                    () -> statusReporter, () -> sharedKeyPairSupplier);
            rsa.setPublicKeyMetadataChanged(this::refreshRsaHelpContext);
        }
        return rsa;
    }

    private void refreshRsaHelpContext() {
        if (!contextualHelpEnabled || !"op_asym_ciphers".equals(activeHelpOperationId)
                || helpContextState == null || !"op_asym_ciphers".equals(helpContextState.current().operationId())) return;
        HelpContext current = helpContextState.current();
        int bits = rsa().publicModulusBitsForHelp();
        HelpContext updated = current.withSelection(HelpContext.Selection.RSA_MODULUS_BITS,
                bits > 0 ? HelpContext.PublicSelection.byteSize(bits) : null);
        helpContextUpdater.accept(updated);
    }

    public void refreshRsaHelpContextForCurrentRoute() { refreshRsaHelpContext(); }

    /** Returns whether the selected asymmetric operation has key material available without mutating state. */
    public boolean hasAsymmetricKeyAvailable(boolean forEncryption) { return rsa().hasAsymmetricKeyAvailable(forEncryption); }

    public void handleLoadPublicKey() { rsa().handleLoadPublicKey(); }

    public void handleLoadPrivateKey() { rsa().handleLoadPrivateKey(); }

    public void handleAsymmetricEncrypt() { rsa().handleAsymmetricEncrypt(); }

    public void handleAsymmetricDecrypt() { rsa().handleAsymmetricDecrypt(); }

    /**
     * Runs the encrypted-file analyser using the material shown in the File Cipher
     * panel.  The analyser itself is shared with the expert Cipher workflow, so
     * its key/IV/AAD/tag controls are kept in sync immediately before execution.
     * This lets an operator investigate the file they have just encrypted or
     * received without manually duplicating the same material in another panel.
     */
    @FXML
    public void handleAnalyzeFileCipher() {
        try {
            Path source = FileCipherInputs.requiredPath(fileCipherSourceField == null ? null : fileCipherSourceField.getText(),
                    "Source file path");
            if (!Files.isRegularFile(source)) {
                throw new IllegalArgumentException("Source file does not exist or is not a regular file");
            }

            byte[] key = FileCipherInputs.requiredHex(fileCipherKeyField == null ? null : fileCipherKeyField.getText(), "Key");
            if (symmetricKeyField != null) {
                symmetricKeyField.setText(DataConverter.bytesToHex(key));
            }
            if (ivField != null && fileCipherNonceField != null) {
                ivField.setText(fileCipherNonceField.getText().trim());
            }
            if (aadField != null && fileCipherAadField != null) {
                aadField.setText(fileCipherAadField.getText().trim());
            }

            // Streaming AEAD stores the authentication tag separately.  The
            // analysis engine expects it in the common GCM/tag field.
            if (gcmTagField != null && fileCipherTagField != null && !fileCipherTagField.getText().isBlank()) {
                Path tagPath = Path.of(fileCipherTagField.getText().trim());
                if (!Files.isRegularFile(tagPath)) {
                    // Fail loudly instead of silently keeping whatever tag was left over
                    // from a previous, unrelated run: a stale tag makes every AEAD
                    // combination fail authentication with no indication why.
                    throw new IllegalArgumentException("Tag file does not exist or is not a regular file: " + tagPath);
                }
                gcmTagField.setText(DataConverter.bytesToHex(Files.readAllBytes(tagPath)));
            }

            handleAnalyzeEncryptedFile(source);
            if (cipherOutputArea != null && fileCipherResultArea != null) {
                fileCipherResultArea.setText(cipherOutputArea.getText());
            }
        } catch (IllegalArgumentException e) {
            if (statusReporter != null) statusReporter.showError("Encrypted File Analysis", e.getMessage());
        } catch (Exception e) {
            if (statusReporter != null) {
                statusReporter.showError("Encrypted File Analysis", "Cannot analyse file: " + e.getMessage());
            }
        }
    }

    /**
     * Set cipher mode ComboBox
     */
    public void setCipherModeCombo(ComboBox<String> combo) {
        this.cipherModeCombo = combo;
        cipherModeCombo.getItems().addAll(SymmetricCipher.SUPPORTED_MODES);
        cipherModeCombo.setValue("CBC");

        // Add listener to update IV field and GCM Tag field requiremen
        // Use valueProperty listener to catch programmatic changes (e.g. Restore UI)
        cipherModeCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            fields().updateModeAndAlgorithmVisibility();
            fields().updatePaddingFieldState();
        });

        // Also keep action handler just in case
        cipherModeCombo.setOnAction(e -> {
            fields().updateModeAndAlgorithmVisibility();
            fields().updatePaddingFieldState();
        });
    }

    /**
     * Set padding ComboBox
     */
    public void setPaddingCombo(ComboBox<String> combo) {
        this.paddingCombo = combo;
        paddingCombo.getItems().addAll(SymmetricCipher.SUPPORTED_PADDINGS);
        paddingCombo.setValue("PKCS7Padding");

        // Add listener to disable padding for modes that don't support i
        cipherModeCombo.setOnAction(e -> fields().updatePaddingFieldState());
    }

    /**
     * Set symmetric key TextField
     */
    public void setSymmetricKeyField(TextField field) {
        this.symmetricKeyField = field;
        symmetricKeyField.setPromptText("Key (Hex) - e.g., for AES-256: 64 hex characters");
    }

    public void setSymKeySourceCombo(ComboBox<String> combo) {
        this.symKeySourceCombo = combo;
        combo.getItems().addAll(SymmetricCapabilityPolicy.KEY_SOURCES);
        combo.setValue("Manual Input");
        combo.setOnAction(e -> labKeys().updateKeySourceVisibility());
    }

    public void setSymHsmKeyCombo(ComboBox<String> combo) {
        this.symHsmKeyCombo = combo;
        labKeys().installHsmKeyCombo();
    }

    /**
     * Set IV TextField
     */
    public void setIVField(TextField field) {
        this.ivField = field;
        ivField.setPromptText("IV (Hex) - required for CBC, CTR, GCM, etc.");
    }

    public void setGcmTagField(TextField field) {
        this.gcmTagField = field;
        fields().updateModeAndAlgorithmVisibility();
    }

    public void setAADField(TextField field) {
        this.aadField = field;
        aadField.setPromptText("AAD (Hex or ASCII) - for GCM/Poly1305");
        fields().updateModeAndAlgorithmVisibility();
    }

    /**
     * Analyze encrypted file with default options
     */
    public void handleAnalyzeEncryptedFile(Path inputFile) {
        handleAnalyzeEncryptedFile(inputFile, EncryptedFileAnalyzer.FileAnalysisOptions.defaults());
    }

    /**
     * Analyze encrypted file using brute-force strategy against candidate combinations.
     */
    public void handleAnalyzeEncryptedFile(Path inputFile, EncryptedFileAnalyzer.FileAnalysisOptions options) {
        try {
            if (inputFile == null) {
                statusReporter.showError("File Error", "Please select an encrypted file");
                return;
            }

            byte[] key = getSymmetricKeyBytes("analysis");
            EncryptedFileAnalyzer.Outcome outcome = new EncryptedFileAnalyzer(analysisInputs())
                    .analyze(inputFile, key, options);
            if (outcome == null) {
                statusReporter.showError("Input Error", "Input file is empty");
                return;
            }

            cipherOutputArea.setText(outcome.reportText());
            if (outcome.hasCandidate()) {
                statusReporter.updateInspector(
                        "Encrypted File Analysis",
                        outcome.analysedBytes(),
                        outcome.inspectorOutput(),
                        outcome.inspectorDetails());
                OperationHistory.getInstance().addOperation(
                        "Cipher",
                        "Analyze Encrypted File",
                        outcome.historyInput(),
                        outcome.historyResult());
            }
            statusReporter.updateStatus(outcome.status());

        } catch (IllegalArgumentException e) {
            statusReporter.showError("Validation Error", e.getMessage());
        } catch (Exception e) {
            statusReporter.showError("Analysis Error", "Error analyzing encrypted file: " + e.getMessage());
        }
    }

    // --- Helper Methods for Global Toolbar ---

    public void handleClear() {
        if (cipherInputArea != null)
            cipherInputArea.clear();
        if (cipherOutputArea != null)
            cipherOutputArea.clear();
        if (symmetricKeyField != null)
            symmetricKeyField.clear();
        if (ivField != null)
            ivField.clear();
        if (aadField != null)
            aadField.clear();
        if (gcmTagField != null)
            gcmTagField.clear();
    }

    public String getOutputText() {
        return cipherOutputArea != null ? cipherOutputArea.getText() : "";
    }

    public TextArea getOutputArea() {
        return cipherOutputArea;
    }

    public TextArea getFileResultArea() {
        return fileCipherResultArea;
    }

    public boolean isPrimaryOutput(TextArea area) {
        return area != null && area == cipherOutputArea;
    }

    public String getAuthenticationTagText() {
        return gcmTagField == null ? "" : gcmTagField.getText();
    }

    private EncryptedFileAnalyzer.CipherInputs analysisInputs() {
        return new EncryptedFileAnalyzer.CipherInputs(
                ivField != null ? ivField.getText() : "",
                aadField != null ? aadField.getText() : "",
                aadField != null && !aadField.isDisabled(),
                gcmTagField != null ? gcmTagField.getText() : "",
                AppSettings.isFullLab());
    }

    private byte[] getSymmetricKeyBytes(String operation) {
        String keyHex = symmetricKeyField != null ? symmetricKeyField.getText().trim() : "";
        if (keyHex.isEmpty()) {
            throw new IllegalArgumentException("Please enter " + operation + " key in hexadecimal");
        }
        return DataConverter.hexToBytes(keyHex);
    }

    public void fillSymmetricCipherInput(String value, com.cryptocarver.model.ClipboardEntry.Format format) {
        String targetFormat = "Text (UTF-8)";
        switch (format) {
            case HEX: targetFormat = "Hexadecimal"; break;
            case BASE64: targetFormat = "Base64"; break;
            case BASE64URL: targetFormat = "Base64URL"; break;
            default: break;
        }

        if (cipherInputFormatCombo != null && !cipherInputFormatCombo.getItems().contains(targetFormat)) {
            dialogService.warning("Format Not Supported", "Incompatible Format\n\nThe format " + format
                    + " is not supported by Symmetric Cipher Input.");
            return;
        }

        if (cipherInputArea != null) {
            cipherInputArea.setText(value);
        }
        if (cipherInputFormatCombo != null) {
            cipherInputFormatCombo.setValue(targetFormat);
        }
    }

    /**
     * Rehydrates a structured Shelf package without touching the user's key.
     * The package is intentionally limited to public operation artefacts.
     */
    public void fillSymmetricCipherPackage(com.cryptocarver.model.ShelfPackage packageData) {
        if (packageData == null || !com.cryptocarver.model.ShelfPackage.AUTHENTICATED_CIPHER
                .equals(packageData.getType())) {
            throw new IllegalArgumentException("Unsupported Symmetric Cipher shelf package");
        }
        String format = packageData.artifact("format");
        com.cryptocarver.model.ClipboardEntry.Format entryFormat = switch (format) {
            case "Hexadecimal" -> com.cryptocarver.model.ClipboardEntry.Format.HEX;
            case "Base64" -> com.cryptocarver.model.ClipboardEntry.Format.BASE64;
            case "Base64URL" -> com.cryptocarver.model.ClipboardEntry.Format.BASE64URL;
            default -> com.cryptocarver.model.ClipboardEntry.Format.TEXT;
        };
        fillSymmetricCipherInput(packageData.artifact("ciphertext"), entryFormat);
        if (symmetricAlgorithmCombo != null) symmetricAlgorithmCombo.setValue(packageData.artifact("algorithm"));
        if (cipherModeCombo != null) cipherModeCombo.setValue(packageData.artifact("mode"));
        if (paddingCombo != null) paddingCombo.setValue(packageData.artifact("padding"));
        if (ivField != null) ivField.setText(packageData.artifact("nonce"));
        if (gcmTagField != null) gcmTagField.setText(packageData.artifact("authTag"));
        if (aadField != null) aadField.setText(packageData.artifact("aad") == null ? "" : packageData.artifact("aad"));
    }

    private void setupHexValidation(TextField field) {
        if (field == null) return;
        field.textProperty().addListener((obs, old, val) -> {
            if (val != null && !val.trim().isEmpty() && !isValidHex(val.trim())) {
                if (!field.getStyleClass().contains("field-error")) {
                    field.getStyleClass().add("field-error");
                }
            } else {
                field.getStyleClass().remove("field-error");
            }
        });
    }

    private boolean isValidHex(String value) {
        if (value == null) return false;
        return value.matches("^[0-9a-fA-F]*$");
    }

    public ComboBox<String> getSymmetricAlgorithmCombo() { return symmetricAlgorithmCombo; }
    public ComboBox<String> getSymKeySourceCombo() { return symKeySourceCombo; }
    public ComboBox<String> getCipherModeCombo() { return cipherModeCombo; }
    public ComboBox<String> getPaddingCombo() { return paddingCombo; }
    public ComboBox<String> getCipherTemplateCombo() { return cipherTemplateCombo; }
}
