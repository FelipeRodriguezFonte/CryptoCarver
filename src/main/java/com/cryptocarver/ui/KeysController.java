package com.cryptocarver.ui;

import com.cryptocarver.crypto.*;
import com.cryptocarver.crypto.hsm.KeyMaterial;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.GeneratedKeySummary;
import com.cryptocarver.model.GeneratedAsymmetricKeySummary;
import com.cryptocarver.model.CryptoEnvelope;
import com.cryptocarver.model.CryptoEnvelopeCodec;
import com.cryptocarver.util.DataConverter;
import com.cryptocarver.utils.OperationHistory;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Key;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Controller for Keys tab - Enhanced with asymmetric cryptography
 *
 * @author Felipe
 */
public class KeysController {

    private String t(String key, Object... args) {
        return com.cryptocarver.service.I18nService.getInstance().text(key, args);
    }

    @FXML private VBox keysRoot;
    @FXML private TitledPane pkcs11Profiles;
    @FXML private Pkcs11ProfilesController pkcs11ProfilesController;
    // ICSF / CCA native key tokens: an included, self-contained pane in the manner of
    // the PKCS#11 one. No ICSF logic lives in this controller.
    @FXML private TitledPane icsfTokenPane;
    @FXML private IcsfTokenController icsfTokenPaneController;
    @FXML private TitledPane icsfBatchPane;
    @FXML private IcsfBatchController icsfBatchPaneController;
    private ModuleI18n.Binding moduleI18n;

    @FXML private VBox symmetricKeysContainer;
    @FXML private VBox asymmetricKeysContainer;
    @FXML private ComboBox<String> ecdsaCurveCombo;
    @FXML private TextArea ecdsaPublicKeyArea;
    @FXML private TextArea ecdsaPrivateKeyArea;
    @FXML private TextArea eddsaPublicKeyArea;
    @FXML private TextArea eddsaPrivateKeyArea;
    @FXML private TabPane rsaKeyMaterialTabs;
    @FXML private TabPane ecdsaKeyMaterialTabs;
    @FXML private TabPane dsaKeyMaterialTabs;
    @FXML private TabPane eddsaKeyMaterialTabs;

    // Key Lab FXML fields
    @FXML private TitledPane keyLabPane;
    @FXML private TextField keyLabSearchField;
    @FXML private ComboBox<String> keyLabStatusFilter;
    @FXML private TableView<KeyMaterial> keyLabTable;
    @FXML private TextField keyLabNewNameField;
    @FXML private ComboBox<String> keyLabNewAlgoCombo;
    @FXML private ComboBox<String> keyLabNewSizeCombo;
    @FXML private PasswordField keyLabImportBytesField;
    @FXML private Button keyLabImportBtn;
    @FXML private TextField keyLabDetailIdField;
    @FXML private TextField keyLabDetailNameField;
    @FXML private Label keyLabDetailAlgoLabel;
    @FXML private Label keyLabDetailBitsLabel;
    @FXML private Label keyLabDetailKcvLabel;
    @FXML private Label keyLabDetailFingerprintLabel;
    @FXML private Label keyLabDetailOriginLabel;
    @FXML private Label keyLabDetailCreatedLabel;
    @FXML private Label keyLabDetailModifiedLabel;
    @FXML private Label keyLabDetailStatusLabel;
    @FXML private TextField keyLabDetailValueField;
    @FXML private Button keyLabRevealBtn;
    @FXML private Button keyLabArchiveBtn;

    private StatusReporter mainController;
    private Runnable hsmRefreshCallback = () -> { };

    // Symmetric Key Generation components
    @FXML
    private ComboBox<String> keyTypeCombo;
    @FXML
    private javafx.scene.control.CheckBox forceOddParityCheck;
    @FXML
    private TextArea generatedKeyField;
    @FXML
    private Button saveGeneratedKeyButton;
    private byte[] lastGeneratedSymmetricKeyBytes;
    private String lastGeneratedSymmetricKeyType;

    // Generated Key Summary components
    @FXML private VBox generatedKeySummaryCard;
    @FXML private Label summaryAlgoLabel;
    @FXML private Label summaryLengthLabel;
    @FXML private Label summaryKcvLabel;
    @FXML private Label summaryFingerprintLabel;
    @FXML private Label summaryParityLabel;
    @FXML private Label summaryOriginLabel;
    @FXML private Label summarySavedStatusLabel;
    @FXML private TitledPane validationPane;
    @FXML private Button copyGeneratedKeyButton;
    @FXML private Button copyGeneratedKcvButton;
    @FXML private Button copyGeneratedSummaryButton;
    @FXML private Button saveGeneratedSummaryButton;
    @FXML private Button openValidationButton;

    private GeneratedKeySummary currentGeneratedKeySummary;

    // Asymmetric Key Generation summary components
    @FXML private VBox rsaSummaryCard;
    @FXML private Label rsaSummaryAlgoLabel;
    @FXML private Label rsaSummaryFingerprintLabel;
    @FXML private Label rsaSummaryPubLenLabel;
    @FXML private Label rsaSummaryPrivLenLabel;
    @FXML private Label rsaSummaryCreatedLabel;
    @FXML private Label rsaSummarySavedStatusLabel;
    @FXML private Button rsaCopyPublicBtn;
    @FXML private Button rsaCopyPrivateBtn;
    @FXML private Button rsaCopySummaryBtn;
    @FXML private Button rsaExportPublicBtn;
    @FXML private Button rsaExportPrivateBtn;
    @FXML private Button rsaSendShelfBtn;
    @FXML private Button rsaSendPrivateShelfBtn;
    @FXML private Button rsaUseCipherBtn;
    @FXML private Button rsaUseSignaturesBtn;
    @FXML private Button rsaUseCertificatesBtn;
    @FXML private Button rsaClearBtn;

    @FXML private VBox ecdsaSummaryCard;
    @FXML private Label ecdsaSummaryAlgoLabel;
    @FXML private Label ecdsaSummaryFingerprintLabel;
    @FXML private Label ecdsaSummaryPubLenLabel;
    @FXML private Label ecdsaSummaryPrivLenLabel;
    @FXML private Label ecdsaSummaryCreatedLabel;
    @FXML private Label ecdsaSummarySavedStatusLabel;
    @FXML private Button ecdsaCopyPublicBtn;
    @FXML private Button ecdsaCopyPrivateBtn;
    @FXML private Button ecdsaCopySummaryBtn;
    @FXML private Button ecdsaExportPublicBtn;
    @FXML private Button ecdsaExportPrivateBtn;
    @FXML private Button ecdsaSendShelfBtn;
    @FXML private Button ecdsaSendPrivateShelfBtn;
    @FXML private Button ecdsaUseCipherBtn;
    @FXML private Button ecdsaUseSignaturesBtn;
    @FXML private Button ecdsaUseCertificatesBtn;
    @FXML private Button ecdsaClearBtn;

    @FXML private VBox dsaSummaryCard;
    @FXML private Label dsaSummaryAlgoLabel;
    @FXML private Label dsaSummaryFingerprintLabel;
    @FXML private Label dsaSummaryPubLenLabel;
    @FXML private Label dsaSummaryPrivLenLabel;
    @FXML private Label dsaSummaryCreatedLabel;
    @FXML private Label dsaSummarySavedStatusLabel;
    @FXML private Button dsaCopyPublicBtn;
    @FXML private Button dsaCopyPrivateBtn;
    @FXML private Button dsaCopySummaryBtn;
    @FXML private Button dsaExportPublicBtn;
    @FXML private Button dsaExportPrivateBtn;
    @FXML private Button dsaSendShelfBtn;
    @FXML private Button dsaSendPrivateShelfBtn;
    @FXML private Button dsaUseCipherBtn;
    @FXML private Button dsaUseSignaturesBtn;
    @FXML private Button dsaUseCertificatesBtn;
    @FXML private Button dsaClearBtn;

    @FXML private VBox eddsaSummaryCard;
    @FXML private Label eddsaSummaryAlgoLabel;
    @FXML private Label eddsaSummaryFingerprintLabel;
    @FXML private Label eddsaSummaryPubLenLabel;
    @FXML private Label eddsaSummaryPrivLenLabel;
    @FXML private Label eddsaSummaryCreatedLabel;
    @FXML private Label eddsaSummarySavedStatusLabel;
    @FXML private Button eddsaCopyPublicBtn;
    @FXML private Button eddsaCopyPrivateBtn;
    @FXML private Button eddsaCopySummaryBtn;
    @FXML private Button eddsaExportPublicBtn;
    @FXML private Button eddsaExportPrivateBtn;
    @FXML private Button eddsaSendShelfBtn;
    @FXML private Button eddsaSendPrivateShelfBtn;
    @FXML private Button eddsaUseCipherBtn;
    @FXML private Button eddsaUseSignaturesBtn;
    @FXML private Button eddsaUseCertificatesBtn;
    @FXML private Button eddsaClearBtn;

    private GeneratedAsymmetricKeySummary currentRsaSummary;
    private GeneratedAsymmetricKeySummary currentEcdsaSummary;
    private GeneratedAsymmetricKeySummary currentDsaSummary;
    private GeneratedAsymmetricKeySummary currentEddsaSummary;

    // Key Validation components
    @FXML
    private TextField keyInputField;
    @FXML
    private TextArea validationResultArea;

    // Key material inspection
    @FXML
    private TextArea keyMaterialInputArea;
    @FXML
    private TextArea keyMaterialReportArea;
    @FXML
    private TextArea keyComparePublicArea;
    @FXML
    private TextArea keyComparePrivateArea;
    @FXML
    private TextArea keyCompareResultArea;
    @FXML
    private ComboBox<String> keyStoreTypeCombo;
    @FXML
    private PasswordField keyStorePasswordField;
    @FXML
    private CheckBox keyStoreUnsafeExtractCheck;
    @FXML
    private TextField keyStorePathField;
    @FXML
    private TextArea keyStoreReportArea;
    @FXML
    private ComboBox<String> keyStoreProfileCombo;
    @FXML
    private TextField keyStoreProfileNameField;
    @FXML
    private TextField pkcs11NameField;
    @FXML
    private TextField pkcs11LibraryField;
    @FXML
    private TextField pkcs11SlotField;
    @FXML
    private PasswordField pkcs11PinField;
    @FXML
    private ComboBox<String> pkcs11ProfileCombo;
    @FXML
    private TextArea pkcs11ReportArea;
    @FXML
    private ComboBox<String> pkcs11SigningKeyCombo;
    @FXML
    private ComboBox<String> pkcs11SignatureAlgorithmCombo;
    @FXML
    private TextArea pkcs11DataArea;
    @FXML
    private TextArea pkcs11SignatureArea;
    @FXML
    private ComboBox<String> pkcs11CertificateAliasCombo;
    @FXML
    private TextArea pkcs11CertificateArea;
    @FXML
    private ComboBox<String> pkcs11JwtAlgorithmCombo;
    @FXML
    private TextArea pkcs11JwtPayloadArea;
    @FXML
    private TextArea pkcs11JwtOutputArea;
    @FXML
    private TextArea pkcs11CmsDataArea;
    @FXML
    private CheckBox pkcs11CmsDetachedCheck;
    @FXML
    private TextArea pkcs11CmsOutputArea;
    @FXML
    private ComboBox<String> pkcs11WrappingKeyCombo;
    @FXML
    private ComboBox<String> pkcs11WrapKeyCombo;
    @FXML
    private ComboBox<String> pkcs11WrapTransformationCombo;
    @FXML
    private TextArea pkcs11WrapResultArea;
    @FXML
    private ComboBox<String> pkcs11UnwrappingKeyCombo;
    @FXML
    private TextArea pkcs11UnwrapDataArea;
    @FXML
    private ComboBox<String> pkcs11UnwrapTransformationCombo;
    @FXML
    private TextField pkcs11UnwrapAlgorithmField;
    @FXML
    private ComboBox<String> pkcs11UnwrapTypeCombo;
    @FXML
    private TextArea pkcs11UnwrapResultArea;

    // Key Sharing components
    @FXML
    private ComboBox<String> numComponentsCombo;
    @FXML
    private TextArea keyToSplitField;
    @FXML
    private TextArea componentResultsArea;
    @FXML
    private TextField component1Field;
    @FXML
    private TextField component2Field;
    @FXML
    private TextField component3Field;
    @FXML
    private TextField component4Field;
    @FXML
    private TextField component5Field;

    // Key Derivation components
    @FXML
    private ComboBox<String> kdfAlgorithmCombo;
    @FXML
    private ComboBox<String> kdfInputFormatCombo;
    @FXML
    private ComboBox<String> kdfSaltFormatCombo;
    @FXML
    private ComboBox<String> kdfInfoFormatCombo;
    @FXML
    private TextField kdfInputField;
    @FXML
    private TextField kdfSaltField;
    @FXML
    private TextField kdfInfoField;
    @FXML
    private TextField kdfIterationsField;
    @FXML
    private TextField kdfOutputLengthField;
    @FXML
    private TextArea kdfResultArea;
    @FXML
    private Label kdfInputHelpLabel;
    @FXML
    private Label kdfValidationLabel;
    @FXML
    private Label kdfIterationsLabel;
    @FXML
    private VBox kdfSaltBox;
    @FXML
    private VBox kdfInfoBox;
    @FXML
    private Label kdfInputBadgeLabel;
    @FXML
    private Label kdfSaltBadgeLabel;
    @FXML
    private Label kdfInfoBadgeLabel;

    private com.cryptocarver.ui.component.MaterialFieldBadge kdfInputBadge;
    private com.cryptocarver.ui.component.MaterialFieldBadge kdfSaltBadge;
    private com.cryptocarver.ui.component.MaterialFieldBadge kdfInfoBadge;

    // AES Key Wrap components
    @FXML
    private ComboBox<String> keyWrapModeCombo;
    @FXML
    private CheckBox keyWrapUnwrapCheck;
    @FXML
    private TextField keyWrapKekField;
    @FXML
    private TextField keyWrapDataField;
    @FXML
    private TextArea keyWrapResultArea;

    // RSA Generation components
    @FXML
    private ComboBox<Integer> rsaKeySizeCombo;
    @FXML
    private TextArea rsaPublicKeyArea;
    @FXML
    private TextArea rsaPrivateKeyArea;

    // DSA Generation components
    @FXML
    private ComboBox<String> dsaKeySizeCombo;
    @FXML
    private TextArea dsaPublicKeyArea;
    @FXML
    private TextArea dsaPrivateKeyArea;

    // ECDSA F(p) components
    private ComboBox<String> ecdsaFpCurveCombo;
    private TextArea ecdsaFpPublicKeyArea;
    private TextArea ecdsaFpPrivateKeyArea;

    // Ed25519 components
    private TextArea ed25519PublicKeyArea;
    private TextArea ed25519PrivateKeyArea;

    // Certificate Generation components
    private TextField certCNField;
    private TextField certOrgField;
    private TextField certOUField;
    private TextField certLocalityField;
    private TextField certStateField;
    private TextField certCountryField;
    private TextField certEmailField;
    private TextField certValidityField;
    private ComboBox<String> certKeyTypeCombo;
    private ComboBox<String> certSignAlgoCombo;
    private TextArea certOutputArea;
    private TextField certSanDnsField;
    private TextField certSanIpField;
    private CheckBox certRootCaCheck;

    // Certificate Parsing components
    private TextArea certInputArea;
    private TextArea certParseResultArea;
    private TextArea certCompareLeftArea;
    private TextArea certCompareRightArea;
    private TextArea certCompareResultArea;
    private TextArea certIssueCsrArea;
    private TextArea certIssueCaCertArea;
    private TextArea certIssueCaKeyArea;
    private TextField certIssueValidityField;
    private TextField certIssueSignatureField;
    private TextArea certIssueResultArea;
    private ComboBox<String> certIssueProfileCombo;
    private TextField certIssuePathLengthField;

    // CRL Management components
    private TextArea crlIssuerCertArea;
    private TextArea crlIssuerKeyArea;
    private TextArea crlExistingCrlArea;
    private TextField crlRevokeSerialField;
    private ComboBox<String> crlRevokeReasonCombo;
    private TextArea crlResultArea;

    // Validate Certificate components
    private TextArea valCertInput;
    private TextArea valIssuerInput;
    private TextArea valResultArea;
    private TextArea chainInputArea;
    private TextArea chainCrlInputArea;
    private TextArea chainResultArea;

    // Store last generated key pair for certificate generation
    private KeyPair lastGeneratedKeyPair;
    private String lastKeyType;

    public KeyPair getLastGeneratedKeyPair() {
        return lastGeneratedKeyPair;
    }

    @FXML
    private void initialize() {
        moduleI18n = ModuleI18n.bind(keysRoot, ModuleTextCatalog.keys());
        initializeRsaKexControls();
        initialize(null, keyTypeCombo, forceOddParityCheck, generatedKeyField, keyInputField, validationResultArea,
                numComponentsCombo, keyToSplitField, componentResultsArea,
                component1Field, component2Field, component3Field, component4Field, component5Field);
        initializeKeyMaterialInspector(keyMaterialInputArea, keyMaterialReportArea);
        initializeKeyPairComparator(keyComparePublicArea, keyComparePrivateArea, keyCompareResultArea);
        initializeKeyStoreInspector(keyStoreTypeCombo, keyStorePasswordField, keyStoreUnsafeExtractCheck,
                keyStorePathField, keyStoreReportArea, keyStoreProfileCombo, keyStoreProfileNameField);
        initializePkcs11Inspector(pkcs11NameField, pkcs11LibraryField, pkcs11SlotField,
                pkcs11PinField, pkcs11ProfileCombo, pkcs11ReportArea);
        initializePkcs11Signing(pkcs11SigningKeyCombo, pkcs11SignatureAlgorithmCombo,
                pkcs11DataArea, pkcs11SignatureArea);
        initializePkcs11Certificates(pkcs11CertificateAliasCombo, pkcs11CertificateArea);
        initializePkcs11Jwt(pkcs11JwtAlgorithmCombo, pkcs11JwtPayloadArea, pkcs11JwtOutputArea);
        initializePkcs11Cms(pkcs11CmsDataArea, pkcs11CmsDetachedCheck, pkcs11CmsOutputArea);
        initializePkcs11Wrap(pkcs11WrappingKeyCombo, pkcs11WrapKeyCombo, pkcs11WrapTransformationCombo,
                pkcs11WrapResultArea, pkcs11UnwrappingKeyCombo, pkcs11UnwrapDataArea,
                pkcs11UnwrapTransformationCombo, pkcs11UnwrapAlgorithmField, pkcs11UnwrapTypeCombo,
                pkcs11UnwrapResultArea);
        initializeKDF(kdfAlgorithmCombo, kdfInputFormatCombo, kdfSaltFormatCombo, kdfInfoFormatCombo,
                kdfInputField, kdfSaltField, kdfInfoField, kdfIterationsField, kdfOutputLengthField, kdfResultArea);
        initializeKeyWrap(keyWrapModeCombo, keyWrapUnwrapCheck, keyWrapKekField, keyWrapDataField, keyWrapResultArea);
        initializeTR31(tr31KbpkExportField, tr31KeyToWrapField, tr31VersionCombo, tr31UsageCombo,
                tr31AlgorithmCombo, tr31ModeCombo, tr31ExportabilityCombo, tr31OptionalBlocksField,
                tr31ExportResultArea, tr31KbpkImportField, tr31KeyBlockField, tr31KeyLengthField,
                tr31ImportResultArea);
        initializeRSA(rsaKeySizeCombo, rsaPublicKeyArea, rsaPrivateKeyArea);
        initializeDSA(dsaKeySizeCombo, dsaPublicKeyArea, dsaPrivateKeyArea);
        initializeECDSAFp(ecdsaCurveCombo, ecdsaPublicKeyArea, ecdsaPrivateKeyArea);
        initializeEd25519(eddsaPublicKeyArea, eddsaPrivateKeyArea);
        initializeKeyLab();

        setupHexValidation(keyInputField);
        setupHexValidation(keyToSplitField);
        setupHexValidation(component1Field);
        setupHexValidation(component2Field);
        setupHexValidation(component3Field);
        setupHexValidation(component4Field);
        setupHexValidation(component5Field);
        if (keyTypeCombo != null) {
            keyTypeCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (currentGeneratedKeySummary != null && (newVal == null || !newVal.equalsIgnoreCase(currentGeneratedKeySummary.getAlgorithm()))) {
                    hideGeneratedKeySummary();
                    if (lastGeneratedSymmetricKeyBytes != null) {
                        Arrays.fill(lastGeneratedSymmetricKeyBytes, (byte) 0);
                    }
                    lastGeneratedSymmetricKeyBytes = null;
                    lastGeneratedSymmetricKeyType = null;
                    if (generatedKeyField != null) generatedKeyField.clear();
                    if (saveGeneratedKeyButton != null) saveGeneratedKeyButton.setDisable(true);
                }
            });
        }

        if (rsaKeySizeCombo != null) {
            rsaKeySizeCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
                currentRsaSummary = null;
                if (rsaSummaryCard != null) { rsaSummaryCard.setVisible(false); rsaSummaryCard.setManaged(false); }
            });
        }
        if (ecdsaCurveCombo != null) {
            ecdsaCurveCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
                currentEcdsaSummary = null;
                if (ecdsaSummaryCard != null) { ecdsaSummaryCard.setVisible(false); ecdsaSummaryCard.setManaged(false); }
            });
        }
        if (dsaKeySizeCombo != null) {
            dsaKeySizeCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
                currentDsaSummary = null;
                if (dsaSummaryCard != null) { dsaSummaryCard.setVisible(false); dsaSummaryCard.setManaged(false); }
            });
        }
        setupHexValidation(keyWrapKekField);
        setupHexValidation(keyWrapDataField);
        setupHexValidation(tr31KbpkExportField);
        setupHexValidation(tr31KeyToWrapField);
        setupHexValidation(tr31KbpkImportField);
        setupHexValidation(keyLabImportBytesField);

        showSymmetricSection();
    }

    public void init(StatusReporter reporter, Runnable hsmRefreshCallback) {
        this.mainController = reporter;
        this.hsmRefreshCallback = hsmRefreshCallback == null ? () -> { } : hsmRefreshCallback;
        if (pkcs11ProfilesController != null && reporter != null) {
            pkcs11ProfilesController.setStatusReporter(reporter);
            pkcs11ProfilesController.setOperationExecutor(reporter.getOperationExecutor());
        }
        if (icsfTokenPaneController != null && reporter != null) {
            icsfTokenPaneController.setStatusReporter(reporter);
        }
        if (icsfBatchPaneController != null && reporter != null) {
            icsfBatchPaneController.setStatusReporter(reporter);
        }
    }

    public void showSymmetricSection() {
        setSectionVisible(symmetricKeysContainer, true);
        setSectionVisible(asymmetricKeysContainer, false);
    }

    public void showAsymmetricSection() {
        setSectionVisible(symmetricKeysContainer, false);
        setSectionVisible(asymmetricKeysContainer, true);
    }

    public boolean isSymmetricSectionVisible() {
        return symmetricKeysContainer != null && symmetricKeysContainer.isVisible();
    }

    /**
     * Opens the named symmetric pane and returns it, so the caller can scroll it into view.
     *
     * <p>PKCS#11 Profiles and the two ICSF / CCA panes are included siblings that follow the
     * accordion rather than members of it, so nothing collapsed the accordion when one of them
     * opened. With a pane as tall as Key Generation expanded above them they landed below the
     * fold and navigating there changed nothing the user could see. Opening one now closes the
     * accordion and the other includes, which is the exclusivity the accordion panes already
     * had among themselves.</p>
     */
    public TitledPane expandSymmetricPane(String paneName) {
        if (paneName == null || paneName.isBlank()) return null;
        if (paneName.contains("PKCS#11 Profiles")) {
            return openIncludedPane(pkcs11Profiles);
        }
        // Both words, because "Batch" alone would also claim any later non-ICSF batch pane.
        if (paneName.contains("ICSF") && paneName.contains("Batch")) {
            return openIncludedPane(icsfBatchPane);
        }
        if (paneName.contains("ICSF")) {
            return openIncludedPane(icsfTokenPane);
        }
        TitledPane expanded = expandPane(symmetricKeysContainer, paneName);
        if (expanded != null) collapseIncludedPanes(null);
        return expanded;
    }

    public TitledPane expandAsymmetricPane(String paneName) {
        return expandPane(asymmetricKeysContainer, paneName);
    }

    /** The symmetric panes keys.fxml includes rather than owns, in layout order. */
    private List<TitledPane> includedSymmetricPanes() {
        List<TitledPane> panes = new ArrayList<>();
        if (pkcs11Profiles != null) panes.add(pkcs11Profiles);
        if (icsfTokenPane != null) panes.add(icsfTokenPane);
        if (icsfBatchPane != null) panes.add(icsfBatchPane);
        return panes;
    }

    private void collapseIncludedPanes(TitledPane except) {
        for (TitledPane pane : includedSymmetricPanes()) {
            pane.setExpanded(pane == except);
        }
    }

    private TitledPane openIncludedPane(TitledPane pane) {
        if (pane == null) return null;
        Accordion accordion = accordionOf(symmetricKeysContainer);
        if (accordion != null) accordion.setExpandedPane(null);
        collapseIncludedPanes(pane);
        return pane;
    }

    private Accordion accordionOf(VBox section) {
        if (section == null) return null;
        for (javafx.scene.Node child : section.getChildren()) {
            if (child instanceof Accordion accordion) return accordion;
        }
        return null;
    }

    public void fillTR31KeyBlockInput(String value) {
        if (tr31KeyBlockField != null) tr31KeyBlockField.setText(value);
    }

    @FXML
    public void handleTR31Clear() {
        clearTR31Fields();
        if (mainController != null) mainController.updateStatus(t("module.keys.tr31ClearStatus"));
    }

    @FXML
    public void handleTR31Reset() {
        clearTR31Fields();
        if (tr31VersionCombo != null) tr31VersionCombo.setValue("B - TDES Key Derivation Binding");
        if (tr31UsageCombo != null) tr31UsageCombo.getSelectionModel().selectFirst();
        if (tr31AlgorithmCombo != null) tr31AlgorithmCombo.getSelectionModel().selectFirst();
        if (tr31ModeCombo != null) tr31ModeCombo.getSelectionModel().selectFirst();
        if (tr31ExportabilityCombo != null) tr31ExportabilityCombo.getSelectionModel().selectFirst();
        if (mainController != null) mainController.updateStatus(t("module.keys.tr31ResetStatus"));
    }

    private void clearTR31Fields() {
        if (tr31KbpkExportField != null) tr31KbpkExportField.clear();
        if (tr31KeyToWrapField != null) tr31KeyToWrapField.clear();
        if (tr31OptionalBlocksField != null) tr31OptionalBlocksField.clear();
        if (tr31KbpkImportField != null) tr31KbpkImportField.clear();
        if (tr31KeyBlockField != null) tr31KeyBlockField.clear();
        if (tr31KeyLengthField != null) tr31KeyLengthField.clear();
        if (tr31ExportResultArea != null) tr31ExportResultArea.clear();
        if (tr31ImportResultArea != null) {
            tr31ImportResultArea.clear();
            tr31ImportResultArea.setManaged(false);
            tr31ImportResultArea.setVisible(false);
        }
    }

    private void showTR31Validation(String message, String fieldKey, TextArea feedbackArea) {
        showTR31Validation(message, fieldKey, feedbackArea == null ? null : safeMessage -> {
            feedbackArea.setText(safeMessage);
            feedbackArea.setVisible(true);
            feedbackArea.setManaged(true);
        });
    }

    private void showTR31Validation(String message, String fieldKey, TR31FeedbackTarget feedbackTarget) {
        String safeMessage = InlineErrorPresenter.redactSecrets(message);
        UserFacingError error = new UserFacingError(t("module.keys.tr31.errorTitle"), safeMessage, safeMessage, fieldKey);
        if (mainController != null) {
            mainController.showError(error);
        } else if (feedbackTarget != null) {
            feedbackTarget.present(safeMessage);
        }
    }

    @FunctionalInterface
    interface TR31FeedbackTarget {
        void present(String safeMessage);
    }

    private void logTR31Failure(String operation, Exception error) {
        StringWriter trace = new StringWriter();
        error.printStackTrace(new PrintWriter(trace));
        System.err.print(InlineErrorPresenter.redactSecrets("TR-31 " + operation + " failed:\n" + trace));
    }

    private void setSectionVisible(VBox section, boolean visible) {
        if (section != null) {
            section.setManaged(visible);
            section.setVisible(visible);
        }
    }

    private TitledPane expandPane(VBox section, String paneName) {
        if (section == null || paneName == null || paneName.isBlank()) return null;
        // Comparing the canonical name against the pane's visible text only works while the two
        // are the same string, which stops being true the moment the title is translated:
        // "Key Generation" never matches "Generación de claves", so nothing expanded and the
        // navigation silently did nothing. ModulePaneMatcher knows the translations.
        Accordion accordion = accordionOf(section);
        if (accordion == null) return null;
        for (TitledPane pane : accordion.getPanes()) {
            if (ModulePaneMatcher.matches(pane, paneName, ModuleTextCatalog.keys())) {
                accordion.setExpandedPane(pane);
                return pane;
            }
        }
        return null;
    }

    @FXML private void handleChooseKeyStore() { chooseKeyStore(); }
    @FXML private void handleSaveKeyStoreProfile() { saveKeyStoreProfile(); }
    @FXML private void handleChoosePkcs11Library() { choosePkcs11Library(); }
    @FXML private void handleConnectPkcs11() { connectPkcs11(); hsmRefreshCallback.run(); }
    @FXML private void handleDisconnectPkcs11() { disconnectPkcs11(); hsmRefreshCallback.run(); }
    @FXML private void handlePkcs11Sign() { signWithPkcs11(); }
    @FXML private void handlePkcs11Verify() { verifyWithPkcs11(); }
    @FXML private void handleShowPkcs11Certificate() { showPkcs11CertificateChain(); }
    @FXML private void handleGeneratePkcs11Jwt() { generatePkcs11Jwt(); }
    @FXML private void handleGeneratePkcs11Cms() { generatePkcs11Cms(); }
    @FXML private void handlePkcs11Wrap() { wrapWithPkcs11(); }
    @FXML private void handlePkcs11Unwrap() { unwrapWithPkcs11(); }
    @FXML private void handleLoadKeyStoreProfile() { loadKeyStoreProfile(); }
    @FXML private void handleAesKeyWrap() { handleKeyWrap(); }
    @FXML public void handleGenerateECDSA() { handleGenerateECDSAFp(); }

    private void showError(String title, String message) {
        if (mainController != null) mainController.showError(title, message);
    }

    private void showError(UserFacingError error) {
        if (mainController != null) mainController.showError(error);
    }

    private void showError(Throwable cause, String contextTitle, String fieldKey) {
        if (mainController != null) mainController.showError(cause, contextTitle, fieldKey);
    }

    private void showInfo(String title, String message) {
        if (mainController != null) mainController.showInfo(title, message);
    }

    private void updateStatus(String message) {
        if (mainController != null) mainController.updateStatus(message);
    }

    /**
     * Initialize the controller - Symmetric keys
     */
    public void initialize(StatusReporter mainController,
            ComboBox<String> keyTypeCombo,
            javafx.scene.control.CheckBox forceOddParityCheck,
            TextArea generatedKeyField,
            TextField keyInputField,
            TextArea validationResultArea,
            ComboBox<String> numComponentsCombo,
            TextArea keyToSplitField,
            TextArea componentResultsArea,
            TextField component1Field,
            TextField component2Field,
            TextField component3Field,
            TextField component4Field,
            TextField component5Field) {

        this.mainController = mainController;
        this.keyTypeCombo = keyTypeCombo;
        this.forceOddParityCheck = forceOddParityCheck;
        this.generatedKeyField = generatedKeyField;
        this.keyInputField = keyInputField;
        this.validationResultArea = validationResultArea;
        this.numComponentsCombo = numComponentsCombo;
        this.keyToSplitField = keyToSplitField;
        this.componentResultsArea = componentResultsArea;
        this.component1Field = component1Field;
        this.component2Field = component2Field;
        this.component3Field = component3Field;
        this.component4Field = component4Field;
        this.component5Field = component5Field;

        // Populate combo boxes
        keyTypeCombo.getItems().addAll("DES", "3DES-2KEY", "3DES-3KEY", "AES-128", "AES-192", "AES-256");
        keyTypeCombo.setValue("3DES-2KEY");

        numComponentsCombo.getItems().addAll("2", "3", "4", "5");
        numComponentsCombo.setValue("2");
    }

    public void initializeKeyMaterialInspector(TextArea inputArea, TextArea reportArea) {
        this.keyMaterialInputArea = inputArea;
        this.keyMaterialReportArea = reportArea;
    }

    public void initializeKeyPairComparator(TextArea publicArea, TextArea privateArea, TextArea resultArea) {
        this.keyComparePublicArea = publicArea;
        this.keyComparePrivateArea = privateArea;
        this.keyCompareResultArea = resultArea;
    }

    public void initializeKeyStoreInspector(ComboBox<String> typeCombo, PasswordField passwordField, CheckBox unsafeExtractCheck,
            TextField pathField, TextArea reportArea, ComboBox<String> profileCombo, TextField profileNameField) {
        this.keyStoreTypeCombo = typeCombo;
        this.keyStorePasswordField = passwordField;
        this.keyStoreUnsafeExtractCheck = unsafeExtractCheck;
        this.keyStorePathField = pathField;
        this.keyStoreReportArea = reportArea;
        this.keyStoreProfileCombo = profileCombo;
        this.keyStoreProfileNameField = profileNameField;
        typeCombo.getItems().setAll("Auto", "PKCS12", "JKS", "JCEKS");
        typeCombo.setValue("Auto");
        refreshKeyStoreProfiles();
    }

    public void initializePkcs11Inspector(TextField nameField, TextField libraryField, TextField slotField,
            PasswordField pinField, ComboBox<String> profileCombo, TextArea reportArea) {
        this.pkcs11NameField = nameField;
        this.pkcs11LibraryField = libraryField;
        this.pkcs11SlotField = slotField;
        this.pkcs11PinField = pinField;
        this.pkcs11ProfileCombo = profileCombo;
        this.pkcs11ReportArea = reportArea;
        if (pkcs11NameField != null && pkcs11NameField.getText().isBlank()) pkcs11NameField.setText("CryptoCarverToken");
        if (pkcs11SlotField != null && pkcs11SlotField.getText().isBlank()) pkcs11SlotField.setText("0");
        refreshPkcs11Profiles();
        if (pkcs11ProfileCombo != null) {
            pkcs11ProfileCombo.setOnAction(e -> handlePkcs11ProfileSelection());
        }
    }

    /** Initializes direct token signing controls. Data and signatures are hexadecimal. */
    public void initializePkcs11Signing(ComboBox<String> keyCombo, ComboBox<String> algorithmCombo,
            TextArea dataArea, TextArea signatureArea) {
        this.pkcs11SigningKeyCombo = keyCombo;
        this.pkcs11SignatureAlgorithmCombo = algorithmCombo;
        this.pkcs11DataArea = dataArea;
        this.pkcs11SignatureArea = signatureArea;
        if (pkcs11SignatureAlgorithmCombo != null) {
            pkcs11SignatureAlgorithmCombo.getItems().setAll(
                    "SHA256withRSA", "SHA384withRSA", "SHA512withRSA",
                    "SHA256withECDSA", "SHA384withECDSA", "Ed25519");
            pkcs11SignatureAlgorithmCombo.setValue("SHA256withRSA");
        }
        refreshPkcs11SigningKeys();
    }

    public void initializePkcs11Certificates(ComboBox<String> certificateAliasCombo, TextArea certificateArea) {
        this.pkcs11CertificateAliasCombo = certificateAliasCombo;
        this.pkcs11CertificateArea = certificateArea;
        refreshPkcs11CertificateAliases();
    }

    public void initializePkcs11Jwt(ComboBox<String> algorithmCombo, TextArea payloadArea, TextArea outputArea) {
        this.pkcs11JwtAlgorithmCombo = algorithmCombo;
        this.pkcs11JwtPayloadArea = payloadArea;
        this.pkcs11JwtOutputArea = outputArea;
        if (pkcs11JwtAlgorithmCombo != null) {
            pkcs11JwtAlgorithmCombo.getItems().setAll("RS256", "RS384", "RS512", "ES256", "ES384", "ES512", "EdDSA");
            pkcs11JwtAlgorithmCombo.setValue("RS256");
        }
    }

    public void initializePkcs11Cms(TextArea dataArea, CheckBox detachedCheck, TextArea outputArea) {
        this.pkcs11CmsDataArea = dataArea;
        this.pkcs11CmsDetachedCheck = detachedCheck;
        this.pkcs11CmsOutputArea = outputArea;
    }

    public void initializePkcs11Wrap(ComboBox<String> wrappingKeyCombo, ComboBox<String> keyToWrapCombo,
            ComboBox<String> wrapTransformationCombo, TextArea wrapResultArea,
            ComboBox<String> unwrappingKeyCombo, TextArea unwrapDataArea, ComboBox<String> unwrapTransformationCombo,
            TextField unwrapAlgorithmField, ComboBox<String> unwrapTypeCombo, TextArea unwrapResultArea) {
        this.pkcs11WrappingKeyCombo = wrappingKeyCombo;
        this.pkcs11WrapKeyCombo = keyToWrapCombo;
        this.pkcs11WrapTransformationCombo = wrapTransformationCombo;
        this.pkcs11WrapResultArea = wrapResultArea;
        this.pkcs11UnwrappingKeyCombo = unwrappingKeyCombo;
        this.pkcs11UnwrapDataArea = unwrapDataArea;
        this.pkcs11UnwrapTransformationCombo = unwrapTransformationCombo;
        this.pkcs11UnwrapAlgorithmField = unwrapAlgorithmField;
        this.pkcs11UnwrapTypeCombo = unwrapTypeCombo;
        this.pkcs11UnwrapResultArea = unwrapResultArea;
        // RSA/ECB/PKCS1Padding is what real tokens actually advertise in practice (confirmed
        // empirically against SoftHSM — see Pkcs11Session#wrapKey); OAEP is offered too in case a
        // specific token/HSM does expose it, but is not the safe default here.
        java.util.List<String> transformations = java.util.List.of("RSA/ECB/PKCS1Padding", "RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        if (pkcs11WrapTransformationCombo != null) {
            pkcs11WrapTransformationCombo.getItems().setAll(transformations);
            pkcs11WrapTransformationCombo.setValue(transformations.get(0));
        }
        if (pkcs11UnwrapTransformationCombo != null) {
            pkcs11UnwrapTransformationCombo.getItems().setAll(transformations);
            pkcs11UnwrapTransformationCombo.setValue(transformations.get(0));
        }
        if (pkcs11UnwrapTypeCombo != null) {
            pkcs11UnwrapTypeCombo.getItems().setAll("Secret Key", "Private Key", "Public Key");
            pkcs11UnwrapTypeCombo.setValue("Secret Key");
        }
        if (pkcs11UnwrapAlgorithmField != null && pkcs11UnwrapAlgorithmField.getText().isBlank()) {
            pkcs11UnwrapAlgorithmField.setText("AES");
        }
        refreshPkcs11WrapKeyAliases();
    }

    /** Opens a real JDK SunPKCS11 session. The PIN is used once and never persisted. */
    public void connectPkcs11() {
        char[] pin = pkcs11PinField == null ? new char[0] : pkcs11PinField.getText().toCharArray();
        try {
            int slot = Integer.parseInt(pkcs11SlotField.getText().trim());
            var configuration = new com.cryptocarver.crypto.hsm.Pkcs11Configuration(
                    pkcs11NameField.getText(), java.nio.file.Path.of(pkcs11LibraryField.getText().trim()), slot);
            disconnectPkcs11Internal();
            com.cryptocarver.crypto.hsm.Pkcs11Session pkcs11Session =
                    com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().connect(configuration, pin);
            var objects = pkcs11Session.listObjects();
            StringBuilder report = new StringBuilder("========================================\nPKCS#11 TOKEN SESSION\n========================================\n\n")
                    .append("Provider: ").append(pkcs11Session.providerName()).append("\n")
                    .append("Library: ").append(configuration.library()).append("\n")
                    .append("Slot list index: ").append(configuration.slotListIndex()).append("\n")
                    .append("Objects: ").append(objects.size()).append("\n\n");
            for (var object : objects) {
                report.append("Alias: ").append(object.alias())
                        .append("\nType: ").append(object.objectType())
                        .append("\nAlgorithm: ").append(object.algorithm())
                        .append("\nFormat: ").append(object.format())
                        .append("\nFingerprint: ").append(object.fingerprint())
                        .append("\n----------------------------------------\n");
            }

            report.append("\n========================================\nJCA PROVIDER SERVICES (COMPATIBILITY)\n========================================\n")
                    .append("Advertised services are not a direct PKCS#11 mechanism list; a selected key may still reject an operation.\n\n");
            var sigs = pkcs11Session.getSupportedMechanisms("Signature");
            report.append("Signatures (").append(sigs.size()).append("): ").append(String.join(", ", sigs)).append("\n\n");
            var ciphers = pkcs11Session.getSupportedMechanisms("Cipher");
            report.append("Ciphers (").append(ciphers.size()).append("): ").append(String.join(", ", ciphers)).append("\n\n");
            var macs = pkcs11Session.getSupportedMechanisms("Mac");
            report.append("MACs (").append(macs.size()).append("): ").append(String.join(", ", macs)).append("\n\n");

            report.append("UI Compatible Signatures:\n");
            if (pkcs11SignatureAlgorithmCombo != null) {
                for (String algo : pkcs11SignatureAlgorithmCombo.getItems()) {
                    if (sigs.contains(algo)) {
                        report.append(" [YES] ").append(algo).append("\n");
                    } else {
                        report.append(" [NO]  ").append(algo).append("\n");
                    }
                }
            }

            pkcs11ReportArea.setText(report.toString());
            refreshPkcs11SigningKeys();
            refreshPkcs11CertificateAliases();
            refreshPkcs11WrapKeyAliases();
            if (mainController != null) {
                mainController.publish(OperationResult.forOperation("PKCS#11 Token Connect")
                        .output(report.toString().getBytes(StandardCharsets.UTF_8))
                        .detail("Provider", pkcs11Session.providerName())
                        .detail("Slot list index", String.valueOf(slot))
                        .detail("Objects", String.valueOf(objects.size()))
                        .status("PKCS#11 token connected; " + objects.size() + " object(s) discovered")
                        .build());
            }
        } catch (Exception error) {
            showError("PKCS#11 connection", "Unable to open token: " + safePkcs11Message(error));
        } finally {
            java.util.Arrays.fill(pin, '\0');
            if (pkcs11PinField != null) pkcs11PinField.clear();
        }
    }

    public void disconnectPkcs11() {
        boolean wasConnected = com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().isConnected();
        disconnectPkcs11Internal();
        if (pkcs11ReportArea != null) {
            pkcs11ReportArea.setText(wasConnected ? "PKCS#11 session closed. Token keys remain on the token." : "No PKCS#11 session is open.");
        }
        updateStatus(com.cryptocarver.service.I18nService.getInstance().text(
                wasConnected ? "module.keys.pkcs11Closed" : "module.keys.pkcs11NotOpen"));
        refreshPkcs11SigningKeys();
        refreshPkcs11CertificateAliases();
        refreshPkcs11WrapKeyAliases();
    }

    public void choosePkcs11Library() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select PKCS#11 native library");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("PKCS#11 libraries", "*.dylib", "*.so", "*.dll"),
                new FileChooser.ExtensionFilter("All files", "*"));
        java.io.File selected = chooser.showOpenDialog(null);
        if (selected != null && pkcs11LibraryField != null) pkcs11LibraryField.setText(selected.getAbsolutePath());
    }

    public void handleSavePkcs11Profile() {
        if (pkcs11NameField == null || pkcs11LibraryField == null || pkcs11SlotField == null) return;
        String name = pkcs11NameField.getText().trim();
        String library = pkcs11LibraryField.getText().trim();
        String slotStr = pkcs11SlotField.getText().trim();
        if (name.isEmpty() || library.isEmpty()) {
            showError("Save Profile", "Profile name and library path are required.");
            return;
        }
        int slot = 0;
        try {
            slot = Integer.parseInt(slotStr);
        } catch (NumberFormatException e) {
            showError("Save Profile", "Slot must be a valid integer.");
            return;
        }
        if (slot < 0) {
            showError("Save Profile", "Slot must be zero or greater.");
            return;
        }
        com.cryptocarver.model.AppSettings.getInstance().savePkcs11Profile(name, library, slot);
        refreshPkcs11Profiles();
        if (pkcs11ProfileCombo != null) pkcs11ProfileCombo.setValue(name);
        updateStatus("PKCS#11 profile '" + name + "' saved");
    }

    public void handleDeletePkcs11Profile() {
        if (pkcs11ProfileCombo == null || pkcs11ProfileCombo.getValue() == null) return;
        String name = pkcs11ProfileCombo.getValue();
        com.cryptocarver.model.AppSettings.getInstance().removePkcs11Profile(name);
        refreshPkcs11Profiles();
        updateStatus("PKCS#11 profile '" + name + "' deleted");
    }

    private void handlePkcs11ProfileSelection() {
        if (pkcs11ProfileCombo == null || pkcs11ProfileCombo.getValue() == null) return;
        String name = pkcs11ProfileCombo.getValue();
        for (var profile : com.cryptocarver.model.AppSettings.getInstance().getPkcs11Profiles()) {
            if (profile.name().equalsIgnoreCase(name)) {
                pkcs11NameField.setText(profile.name());
                pkcs11LibraryField.setText(profile.library());
                pkcs11SlotField.setText(String.valueOf(profile.slot()));
                if (pkcs11PinField != null) pkcs11PinField.clear(); // Ensure PIN is blank
                break;
            }
        }
    }

    private void refreshPkcs11Profiles() {
        if (pkcs11ProfileCombo == null) return;
        String current = pkcs11ProfileCombo.getValue();
        pkcs11ProfileCombo.getItems().clear();
        for (var profile : com.cryptocarver.model.AppSettings.getInstance().getPkcs11Profiles()) {
            pkcs11ProfileCombo.getItems().add(profile.name());
        }
        if (current != null && pkcs11ProfileCombo.getItems().contains(current)) {
            pkcs11ProfileCombo.setValue(current);
        }
    }

    private void disconnectPkcs11Internal() {
        com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().disconnect();
    }

    private String safePkcs11Message(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    public void refreshPkcs11SigningKeys() {
        if (pkcs11SigningKeyCombo == null) return;
        String selected = pkcs11SigningKeyCombo.getValue();
        pkcs11SigningKeyCombo.getItems().clear();
        try {
            pkcs11SigningKeyCombo.getItems().addAll(
                    com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().listPrivateKeyAliases());
            if (selected != null && pkcs11SigningKeyCombo.getItems().contains(selected)) {
                pkcs11SigningKeyCombo.setValue(selected);
            } else if (!pkcs11SigningKeyCombo.getItems().isEmpty()) {
                pkcs11SigningKeyCombo.setValue(pkcs11SigningKeyCombo.getItems().get(0));
            }
        } catch (Exception ignored) {
            // No token session is expected before the user connects one.
        }
    }

    public void refreshPkcs11CertificateAliases() {
        if (pkcs11CertificateAliasCombo == null) return;
        String selected = pkcs11CertificateAliasCombo.getValue();
        pkcs11CertificateAliasCombo.getItems().clear();
        try {
            pkcs11CertificateAliasCombo.getItems().addAll(
                    com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().listCertificateAliases());
            if (selected != null && pkcs11CertificateAliasCombo.getItems().contains(selected)) {
                pkcs11CertificateAliasCombo.setValue(selected);
            } else if (!pkcs11CertificateAliasCombo.getItems().isEmpty()) {
                pkcs11CertificateAliasCombo.setValue(pkcs11CertificateAliasCombo.getItems().get(0));
            }
        } catch (Exception ignored) {
            // No token session is expected before the user connects one.
        }
    }

    /** Wrapping/unwrapping key aliases can be any object on the token (private, public or
     *  secret), unlike the signing combo which only lists private keys with a certificate. */
    public void refreshPkcs11WrapKeyAliases() {
        if (pkcs11WrappingKeyCombo == null && pkcs11WrapKeyCombo == null && pkcs11UnwrappingKeyCombo == null) return;
        String selectedWrapping = pkcs11WrappingKeyCombo == null ? null : pkcs11WrappingKeyCombo.getValue();
        String selectedTarget = pkcs11WrapKeyCombo == null ? null : pkcs11WrapKeyCombo.getValue();
        String selectedUnwrapping = pkcs11UnwrappingKeyCombo == null ? null : pkcs11UnwrappingKeyCombo.getValue();
        if (pkcs11WrappingKeyCombo != null) pkcs11WrappingKeyCombo.getItems().clear();
        if (pkcs11WrapKeyCombo != null) pkcs11WrapKeyCombo.getItems().clear();
        if (pkcs11UnwrappingKeyCombo != null) pkcs11UnwrappingKeyCombo.getItems().clear();
        try {
            var session = com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().requireSession();
            java.util.List<String> allAliases = session.listObjects().stream()
                    .map(com.cryptocarver.crypto.hsm.Pkcs11ObjectInfo::alias)
                    .distinct().toList();
            java.util.List<String> privateAliases = session.listPrivateKeysWithCertificate();
            if (pkcs11WrappingKeyCombo != null) {
                pkcs11WrappingKeyCombo.getItems().addAll(privateAliases);
                selectComboValue(pkcs11WrappingKeyCombo, selectedWrapping);
            }
            if (pkcs11UnwrappingKeyCombo != null) {
                pkcs11UnwrappingKeyCombo.getItems().addAll(privateAliases);
                selectComboValue(pkcs11UnwrappingKeyCombo, selectedUnwrapping);
            }
            if (pkcs11WrapKeyCombo != null) {
                pkcs11WrapKeyCombo.getItems().addAll(allAliases);
                selectComboValue(pkcs11WrapKeyCombo, selectedTarget);
            }
        } catch (Exception ignored) {
            // No token session is expected before the user connects one.
        }
    }

    private static void selectComboValue(ComboBox<String> combo, String previous) {
        if (previous != null && combo.getItems().contains(previous)) {
            combo.setValue(previous);
        } else if (!combo.getItems().isEmpty()) {
            combo.setValue(combo.getItems().get(0));
        }
    }

    public void wrapWithPkcs11() {
        try {
            String wrappingAlias = requireComboValue(pkcs11WrappingKeyCombo,
                    "Connect a token, then select a wrapping key alias");
            String targetAlias = requireComboValue(pkcs11WrapKeyCombo,
                    "Select the alias of the key to wrap");
            String transformation = pkcs11WrapTransformationCombo == null || pkcs11WrapTransformationCombo.getValue() == null
                    ? "RSA/ECB/PKCS1Padding" : pkcs11WrapTransformationCombo.getValue();
            byte[] wrapped = com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().requireSession()
                    .wrapKey(wrappingAlias, targetAlias, transformation);
            String hex = DataConverter.bytesToHex(wrapped);
            pkcs11WrapResultArea.setText(hex);
            if (mainController != null) {
                mainController.publish(OperationResult.forOperation("PKCS#11 Wrap Key")
                        .output(wrapped)
                        .detail("Wrapping key alias", wrappingAlias)
                        .detail("Wrapped key alias", targetAlias)
                        .detail("Transformation", transformation)
                        .status("Wrapped '" + targetAlias + "' under '" + wrappingAlias + "'").build());
            }
        } catch (Exception error) {
            showError("PKCS#11 Wrap", "Unable to wrap key: " + safePkcs11Message(error));
        }
    }

    public void unwrapWithPkcs11() {
        try {
            String unwrappingAlias = requireComboValue(pkcs11UnwrappingKeyCombo,
                    "Connect a token, then select an unwrapping key alias");
            byte[] wrapped = DataConverter.hexToBytes(requirePkcs11Text(pkcs11UnwrapDataArea, "Wrapped key"));
            String transformation = pkcs11UnwrapTransformationCombo == null || pkcs11UnwrapTransformationCombo.getValue() == null
                    ? "RSA/ECB/PKCS1Padding" : pkcs11UnwrapTransformationCombo.getValue();
            String algorithm = pkcs11UnwrapAlgorithmField == null || pkcs11UnwrapAlgorithmField.getText().isBlank()
                    ? "AES" : pkcs11UnwrapAlgorithmField.getText().trim();
            int keyType = switch (pkcs11UnwrapTypeCombo == null || pkcs11UnwrapTypeCombo.getValue() == null
                    ? "Secret Key" : pkcs11UnwrapTypeCombo.getValue()) {
                case "Private Key" -> javax.crypto.Cipher.PRIVATE_KEY;
                case "Public Key" -> javax.crypto.Cipher.PUBLIC_KEY;
                default -> javax.crypto.Cipher.SECRET_KEY;
            };
            java.security.Key unwrapped = com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().requireSession()
                    .unwrapKey(unwrappingAlias, wrapped, transformation, algorithm, keyType);
            // The recovered key material is never displayed or logged — only a description of the
            // handle, matching how every other PKCS#11 operation in this class treats key material.
            String summary = "Unwrapped " + unwrapped.getClass().getSimpleName()
                    + " (algorithm=" + unwrapped.getAlgorithm() + ", format=" + unwrapped.getFormat() + ")";
            pkcs11UnwrapResultArea.setText(summary);
            if (mainController != null) {
                mainController.publish(OperationResult.forOperation("PKCS#11 Unwrap Key")
                        .input(wrapped)
                        .detail("Unwrapping key alias", unwrappingAlias)
                        .detail("Transformation", transformation)
                        .detail("Recovered algorithm", unwrapped.getAlgorithm())
                        .status(summary).build());
            }
        } catch (Exception error) {
            showError("PKCS#11 Unwrap", "Unable to unwrap key: " + safePkcs11Message(error));
        }
    }

    private static String requireComboValue(ComboBox<String> combo, String message) {
        String value = combo == null ? null : combo.getValue();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    public void showPkcs11CertificateChain() {
        try {
            String alias = pkcs11CertificateAliasCombo == null ? null : pkcs11CertificateAliasCombo.getValue();
            if (alias == null || alias.isBlank()) {
                throw new IllegalArgumentException("Connect a token and select an alias with a certificate");
            }
            String pem = com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().requireSession()
                    .certificateChainPem(alias);
            pkcs11CertificateArea.setText(pem);
            mainController.publish(OperationResult.forOperation("PKCS#11 Certificate Export")
                    .output(pem.getBytes(StandardCharsets.US_ASCII))
                    .detail("Key alias", alias).detail("Content", "Public X.509 certificate chain")
                    .status("Exported public certificate chain from PKCS#11 token").build());
        } catch (Exception error) {
            showError("PKCS#11 certificate", "Unable to load certificate chain: " + safePkcs11Message(error));
        }
    }

    public void handleUpdatePkcs11CertificateChain() {
        try {
            String alias = pkcs11CertificateAliasCombo == null ? null : pkcs11CertificateAliasCombo.getValue();
            if (alias == null || alias.isBlank()) {
                throw new IllegalArgumentException("Connect a token and select an alias to update");
            }

            String pem = pkcs11CertificateArea.getText().trim();
            if (pem.isEmpty()) {
                throw new IllegalArgumentException("Paste the PEM certificate chain in the text area");
            }

            List<X509Certificate> chain = new ArrayList<>();
            String[] parts = pem.split("-----BEGIN CERTIFICATE-----");
            for (String part : parts) {
                if (part.trim().isEmpty()) continue;
                String certPem = "-----BEGIN CERTIFICATE-----" + part;
                int endIndex = certPem.indexOf("-----END CERTIFICATE-----");
                if (endIndex != -1) {
                    certPem = certPem.substring(0, endIndex + 25);
                    chain.add(CertificateGenerator.parseCertificate(certPem));
                }
            }

            if (chain.isEmpty()) {
                throw new IllegalArgumentException("No valid PEM certificates found");
            }

            // Determine the leaf from verified issuer relationships so the
            // confirmation describes the certificate that will be installed.
            java.security.cert.X509Certificate leaf = null;
            for (java.security.cert.X509Certificate cert : chain) {
                boolean isIssuer = false;
                for (java.security.cert.X509Certificate other : chain) {
                    if (cert != other && isVerifiedIssuer(cert, other)) {
                        isIssuer = true;
                        break;
                    }
                }
                if (!isIssuer) {
                    if (leaf != null) throw new IllegalArgumentException("Chain contains multiple leaves");
                    leaf = cert;
                }
            }
            if (leaf == null) {
                throw new IllegalArgumentException("Could not determine a unique leaf in the chain");
            }

            String subject = leaf.getSubjectX500Principal().getName();
            String issuer = leaf.getIssuerX500Principal().getName();

            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
            alert.setTitle("Confirm Token Update");
            alert.setHeaderText("Updating certificate chain for alias: " + alias);
            alert.setContentText("Leaf Subject: " + subject + "\nLeaf Issuer: " + issuer + "\nChain length: " + chain.size() + "\n\nProceed with token modification?");

            java.util.Optional<javafx.scene.control.ButtonType> result = alert.showAndWait();
            if (result.isEmpty() || result.get() != javafx.scene.control.ButtonType.OK) {
                updateStatus("Update cancelled by user");
                return;
            }

            com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().requireSession()
                    .updateCertificateChain(alias, chain.toArray(new java.security.cert.Certificate[0]));

            updateStatus("Successfully updated certificate chain for token alias: " + alias);

            if (mainController != null) {
                mainController.publish(OperationResult.forOperation("Update PKCS#11 Certificate Chain")
                        .detail("Alias", alias)
                        .detail("Subject", subject)
                        .detail("Issuer", issuer)
                        .detail("Chain Length", String.valueOf(chain.size()))
                        .status("Success").build());
            }
        } catch (Exception error) {
            showError("Update PKCS#11 certificate chain", "Failed to update chain: " + safePkcs11Message(error));
        }
    }

    private static boolean isVerifiedIssuer(X509Certificate issuer, X509Certificate certificate) {
        if (!issuer.getSubjectX500Principal().equals(certificate.getIssuerX500Principal())) {
            return false;
        }
        try {
            certificate.verify(issuer.getPublicKey());
            return true;
        } catch (java.security.GeneralSecurityException e) {
            return false;
        }
    }

    public void generatePkcs11Jwt() {
        try {
            String alias = requirePkcs11SigningAlias();
            String payload = requirePkcs11TextPayload(pkcs11JwtPayloadArea, "JWT claims JSON");
            String algorithm = pkcs11JwtAlgorithmCombo == null ? null : pkcs11JwtAlgorithmCombo.getValue();
            String compactJws = com.cryptocarver.crypto.JOSEService.generateSignedJwtWithPkcs11(payload, algorithm,
                    com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().requireSession(), alias);
            pkcs11JwtOutputArea.setText(compactJws);
            mainController.publish(OperationResult.forOperation("PKCS#11 Signed JWT")
                    .input(payload.getBytes(StandardCharsets.UTF_8)).output(compactJws.getBytes(StandardCharsets.US_ASCII))
                    .detail("Key alias", alias).detail("Algorithm", algorithm).detail("Serialization", "Compact JWS")
                    .status("JWT signed by PKCS#11 token object " + alias).build());
        } catch (Exception error) {
            showError("PKCS#11 JWT", "Unable to create signed JWT: " + safePkcs11Message(error));
        }
    }

    public void generatePkcs11Cms() {
        try {
            String alias = requirePkcs11SigningAlias();
            byte[] data = DataConverter.hexToBytes(requirePkcs11Text(pkcs11CmsDataArea, "CMS data"));
            boolean detached = pkcs11CmsDetachedCheck != null && pkcs11CmsDetachedCheck.isSelected();
            byte[] cms = com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().requireSession()
                    .signCms(alias, data, detached);
            String base64 = java.util.Base64.getEncoder().encodeToString(cms);
            pkcs11CmsOutputArea.setText(base64);
            mainController.publish(OperationResult.forOperation("PKCS#11 CMS SignedData")
                    .input(data).output(cms)
                    .detail("Key alias", alias).detail("Detached", String.valueOf(detached))
                    .detail("Encoding", "Base64 CMS/PKCS#7")
                    .status("CMS SignedData created by PKCS#11 token object " + alias).build());
        } catch (Exception error) {
            showError("PKCS#11 CMS", "Unable to create CMS SignedData: " + safePkcs11Message(error));
        }
    }

    public void signWithPkcs11() {
        try {
            String alias = requirePkcs11SigningAlias();
            byte[] data = DataConverter.hexToBytes(requirePkcs11Text(pkcs11DataArea, "Data"));
            String algorithm = pkcs11SignatureAlgorithmCombo.getValue();
            byte[] signature = com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().requireSession()
                    .sign(alias, data, algorithm);
            pkcs11SignatureArea.setText(DataConverter.bytesToHex(signature));
            mainController.publish(OperationResult.forOperation("PKCS#11 Sign")
                    .input(data).output(signature)
                    .detail("Key alias", alias).detail("Algorithm", algorithm)
                    .status("Signature created by PKCS#11 token object " + alias).build());
        } catch (Exception error) {
            showError("PKCS#11 signing", "Unable to sign: " + safePkcs11Message(error));
        }
    }

    public void verifyWithPkcs11() {
        try {
            String alias = requirePkcs11SigningAlias();
            byte[] data = DataConverter.hexToBytes(requirePkcs11Text(pkcs11DataArea, "Data"));
            byte[] signature = DataConverter.hexToBytes(requirePkcs11Text(pkcs11SignatureArea, "Signature"));
            String algorithm = pkcs11SignatureAlgorithmCombo.getValue();
            boolean valid = com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().requireSession()
                    .verify(alias, data, signature, algorithm);
            mainController.publish(OperationResult.forOperation("PKCS#11 Signature Verify")
                    .input(data).output(signature)
                    .detail("Key alias", alias).detail("Algorithm", algorithm).detail("Valid", String.valueOf(valid))
                    .status("PKCS#11 signature verification: " + (valid ? "VALID" : "INVALID")).build());
            if (valid) updateStatus("PKCS#11 signature is valid");
            else showError("PKCS#11 verification", "Signature is not valid for the selected token key");
        } catch (Exception error) {
            showError("PKCS#11 verification", "Unable to verify: " + safePkcs11Message(error));
        }
    }

    private String requirePkcs11SigningAlias() {
        String alias = pkcs11SigningKeyCombo == null ? null : pkcs11SigningKeyCombo.getValue();
        if (alias == null || alias.isBlank()) {
            throw new IllegalArgumentException("Connect a token that exposes a private-key object and select its alias");
        }
        return alias;
    }

    private static String requirePkcs11Text(TextArea area, String name) {
        String value = area == null ? null : area.getText().replaceAll("\\s+", "");
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " hex is required");
        return value;
    }

    private static String requirePkcs11TextPayload(TextArea area, String name) {
        String value = area == null ? null : area.getText().trim();
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }

    /** Inspects PEM keys and certificates without modifying them. */
    public void handleInspectKeyMaterial() {
        try {
            String pem = keyMaterialInputArea.getText().trim();
            if (pem.isEmpty()) throw new IllegalArgumentException("Paste PEM key or certificate material first");
            String report;
            if (pem.contains("BEGIN CERTIFICATE")) {
                var factory = java.security.cert.CertificateFactory.getInstance("X.509");
                var certificate = (java.security.cert.X509Certificate) factory.generateCertificate(
                        new java.io.ByteArrayInputStream(pem.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
                report = KeyMaterialInspector.describeCertificate(certificate);
            } else if (pem.contains("PRIVATE KEY")) {
                java.security.PrivateKey key = AsymmetricKeyOperations.importPrivateKeyPEMAuto(pem);
                report = KeyMaterialInspector.describeKey(key);
            } else if (pem.contains("BEGIN PUBLIC KEY")) {
                java.security.PublicKey key = AsymmetricKeyOperations.importPublicKeyPEMAuto(pem);
                report = KeyMaterialInspector.describeKey(key);
            } else {
                throw new IllegalArgumentException("Recognized PEM headers are PUBLIC KEY, EC/PRIVATE KEY and CERTIFICATE");
            }
            keyMaterialReportArea.setText(report);
            updateStatus("Key material inspected successfully");
            if (mainController != null) {
                mainController.publish(com.cryptocarver.model.OperationResult.forOperation("Key Material Inspection")
                        .enrichedOutput(report, com.cryptocarver.model.OperationDetail.Classification.PUBLIC)
                        .status("Key material inspected successfully")
                        .build());
            }
        } catch (Exception e) {
            showError("Key Material Inspector", "Cannot inspect material: " + e.getMessage());
        }
    }

    public void handleCompareKeyPair() {
        try {
            java.security.PublicKey publicKey = parsePublicMaterial(keyComparePublicArea.getText().trim());
            java.security.PrivateKey privateKey = parsePrivateMaterial(keyComparePrivateArea.getText().trim());
            boolean matches = KeyMaterialInspector.matches(publicKey, privateKey);
            String reportText = "========================================\nKEY PAIR COMPARISON\n========================================\n\n"
                    + "Public algorithm: " + publicKey.getAlgorithm() + "\nPrivate algorithm: " + privateKey.getAlgorithm() + "\n"
                    + "Public SHA-256: " + KeyMaterialInspector.fingerprint(publicKey.getEncoded()) + "\n\n"
                    + (matches ? "✓ MATCH: the private key successfully signed a challenge verified by the public key."
                            : "✗ NO MATCH: signature verification failed or the algorithms are incompatible.");
            keyCompareResultArea.setText(reportText);
            updateStatus(matches ? "Key pair comparison: match" : "Key pair comparison: no match");
            if (mainController != null) {
                mainController.publish(com.cryptocarver.model.OperationResult.forOperation("Key Pair Comparison")
                        .enrichedOutput(reportText, com.cryptocarver.model.OperationDetail.Classification.PUBLIC)
                        .status(matches ? "Key pair comparison: match" : "Key pair comparison: no match")
                        .build());
            }
        } catch (Exception e) {
            showError("Compare Key Pair", "Cannot compare material: " + e.getMessage());
        }
    }

    public void handleInspectKeyStore() {
        char[] password = keyStorePasswordField.getText().toCharArray();
        try {
            boolean unsafe = keyStoreUnsafeExtractCheck.isSelected();
            var report = KeyStoreInspector.inspect(java.nio.file.Path.of(keyStorePathField.getText().trim()), password,
                    keyStoreTypeCombo.getValue(), unsafe);
            StringBuilder text = new StringBuilder("========================================\nKEYSTORE REPORT\n========================================\n\n")
                    .append("Type: ").append(report.type()).append("\nEntries: ").append(report.entries().size()).append("\n")
                    .append(unsafe ? "⚠️ UNSAFE EXTRACTION ENABLED — do not use this mode in production.\n\n" : "\n");
            for (var entry : report.entries()) {
                text.append("Alias: ").append(entry.alias()).append("\nType: ").append(entry.kind())
                        .append("\nAlgorithm: ").append(entry.algorithm());
                if (!entry.subject().isEmpty()) text.append("\nSubject: ").append(entry.subject());
                if (!entry.fingerprint().equals("Not exposed")) text.append("\nSHA-256: ").append(entry.fingerprint());
                if (unsafe && !entry.keyMaterial().equals("Not requested")) text.append("\nEXPORTED KEY (HEX): ").append(entry.keyMaterial());
                text.append("\n----------------------------------------\n");
            }
            keyStoreReportArea.setText(text.toString());
            updateStatus("KeyStore inspected: " + report.entries().size() + " entries");
            if (mainController != null) {
                mainController.publish(com.cryptocarver.model.OperationResult.forOperation("KeyStore Inspection")
                        .enrichedOutput(text.toString(), unsafe ? com.cryptocarver.model.OperationDetail.Classification.SECRET : com.cryptocarver.model.OperationDetail.Classification.PUBLIC)
                        .status("KeyStore inspected: " + report.entries().size() + " entries")
                        .build());
            }
        } catch (Exception e) {
            showError("KeyStore Inspector", "Cannot inspect keystore: " + e.getMessage());
        } finally {
            java.util.Arrays.fill(password, '\0');
            keyStorePasswordField.clear();
        }
    }

    public void chooseKeyStore() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select PKCS#12, JKS or JCEKS KeyStore");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("KeyStores", "*.p12", "*.pfx", "*.jks", "*.jceks"),
                new FileChooser.ExtensionFilter("All files", "*"));
        java.io.File selected = chooser.showOpenDialog(null);
        if (selected != null) keyStorePathField.setText(selected.getAbsolutePath());
    }

    public void saveKeyStoreProfile() {
        try {
            AppSettings.getInstance().saveTrustStoreProfile(keyStoreProfileNameField.getText(), keyStorePathField.getText(), keyStoreTypeCombo.getValue());
            refreshKeyStoreProfiles();
            keyStoreProfileCombo.setValue(keyStoreProfileNameField.getText().trim());
            updateStatus("KeyStore profile saved (password not stored)");
        } catch (Exception e) {
            showError("KeyStore Profile", e.getMessage());
        }
    }

    public void loadKeyStoreProfile() {
        String name = keyStoreProfileCombo.getValue();
        if (name == null || name.isBlank()) return;
        AppSettings.getInstance().getTrustStoreProfiles().stream().filter(profile -> name.equals(profile.name())).findFirst().ifPresent(profile -> {
            keyStorePathField.setText(profile.path());
            keyStoreTypeCombo.setValue(profile.type());
            keyStorePasswordField.clear();
            updateStatus("KeyStore profile loaded; enter password to inspect");
        });
    }

    private void refreshKeyStoreProfiles() {
        if (keyStoreProfileCombo == null) return;
        keyStoreProfileCombo.getItems().setAll(AppSettings.getInstance().getTrustStoreProfiles().stream()
                .map(AppSettings.TrustStoreProfile::name).sorted(String.CASE_INSENSITIVE_ORDER).toList());
    }

    private java.security.PublicKey parsePublicMaterial(String pem) throws Exception {
        if (pem.isBlank()) throw new IllegalArgumentException("Public key or certificate is required");
        if (pem.contains("BEGIN CERTIFICATE")) {
            var factory = java.security.cert.CertificateFactory.getInstance("X.509");
            return ((java.security.cert.X509Certificate) factory.generateCertificate(
                    new java.io.ByteArrayInputStream(pem.getBytes(java.nio.charset.StandardCharsets.US_ASCII)))).getPublicKey();
        }
        return AsymmetricKeyOperations.importPublicKeyPEMAuto(pem);
    }

    private java.security.PrivateKey parsePrivateMaterial(String pem) throws Exception {
        if (pem.isBlank()) throw new IllegalArgumentException("Private key is required");
        if (pem.contains("ED25519")) return AsymmetricKeyOperations.importEd25519PrivateKeyPEM(pem);
        if (pem.contains("EC PRIVATE")) return AsymmetricKeyOperations.importECPrivateKeyPEM(pem);
        return AsymmetricKeyOperations.importPrivateKeyPEMAuto(pem);
    }

    /**
     * Initialize RSA components
     */
    public void initializeRSA(ComboBox<Integer> keySizeCombo, TextArea publicArea, TextArea privateArea) {
        this.rsaKeySizeCombo = keySizeCombo;
        this.rsaPublicKeyArea = publicArea;
        this.rsaPrivateKeyArea = privateArea;

        rsaKeySizeCombo.getItems().addAll(AsymmetricKeyOperations.RSA_KEY_SIZES);
        rsaKeySizeCombo.setValue(2048);
    }

    /**
     * Initialize DSA components
     */
    public void initializeDSA(ComboBox<String> keySizeCombo, TextArea publicArea, TextArea privateArea) {
        this.dsaKeySizeCombo = keySizeCombo;
        this.dsaPublicKeyArea = publicArea;
        this.dsaPrivateKeyArea = privateArea;

        dsaKeySizeCombo.getItems().addAll(AsymmetricKeyOperations.DSA_KEY_SIZES);
        dsaKeySizeCombo.setValue("2048/256");
    }

    /**
     * Initialize ECDSA F(p) components
     */
    public void initializeECDSAFp(ComboBox<String> curveCombo, TextArea publicArea, TextArea privateArea) {
        this.ecdsaFpCurveCombo = curveCombo;
        this.ecdsaFpPublicKeyArea = publicArea;
        this.ecdsaFpPrivateKeyArea = privateArea;

        ecdsaFpCurveCombo.getItems().addAll(AsymmetricKeyOperations.ECDSA_FP_NAMED_CURVES);
        ecdsaFpCurveCombo.setValue("secp256r1");
    }

    /**
     * Initialize Ed25519 components
     */
    public void initializeEd25519(TextArea publicArea, TextArea privateArea) {
        this.ed25519PublicKeyArea = publicArea;
        this.ed25519PrivateKeyArea = privateArea;
    }

    /**
     * Initialize ECDSA F(2^m) components
     */

    /**
     * Initialize Certificate Generator components
     */
    public void initializeCertificateGen(
            TextField cnField, TextField orgField, TextField ouField,
            TextField localityField, TextField stateField, TextField countryField,
            TextField emailField, TextField validityField, ComboBox<String> keyTypeCombo,
            ComboBox<String> signAlgoCombo, TextArea outputArea, TextField sanDnsField, TextField sanIpField, CheckBox rootCaCheck) {

        this.certCNField = cnField;
        this.certOrgField = orgField;
        this.certOUField = ouField;
        this.certLocalityField = localityField;
        this.certStateField = stateField;
        this.certCountryField = countryField;
        this.certEmailField = emailField;
        this.certValidityField = validityField;
        this.certKeyTypeCombo = keyTypeCombo;
        this.certSignAlgoCombo = signAlgoCombo;
        this.certOutputArea = outputArea;
        this.certSanDnsField = sanDnsField;
        this.certSanIpField = sanIpField;
        this.certRootCaCheck = rootCaCheck;

        certKeyTypeCombo.getItems().addAll("RSA-2048", "RSA-4096", "ECDSA-P256", "ECDSA-P384", "Local PEM (Parse Area)", "PKCS#11 Active Alias");
        certKeyTypeCombo.setValue("RSA-2048");

        certSignAlgoCombo.getItems().addAll("SHA256withRSA", "SHA384withRSA", "SHA512withRSA");
        certSignAlgoCombo.setValue("SHA256withRSA");

        certValidityField.setText("365");
    }

    /** Compatibility entry point for the classic UI, which has no SAN controls. */
    public void initializeCertificateGen(
            TextField cnField, TextField orgField, TextField ouField,
            TextField localityField, TextField stateField, TextField countryField,
            TextField emailField, TextField validityField, ComboBox<String> keyTypeCombo,
            ComboBox<String> signAlgoCombo, TextArea outputArea) {
        initializeCertificateGen(cnField, orgField, ouField, localityField, stateField, countryField, emailField,
                validityField, keyTypeCombo, signAlgoCombo, outputArea, null, null, null);
    }

    /**
     * Initialize Certificate Parsing components
     */
    public void initializeCertificateParse(TextArea inputArea, TextArea resultArea) {
        this.certInputArea = inputArea;
        this.certParseResultArea = resultArea;
    }

    public void initializeCertificateComparator(TextArea leftArea, TextArea rightArea, TextArea resultArea) {
        this.certCompareLeftArea = leftArea;
        this.certCompareRightArea = rightArea;
        this.certCompareResultArea = resultArea;
    }

    public void initializeCertificateIssuer(TextArea csrArea, TextArea caCertArea, TextArea caKeyArea,
            TextField validityField, TextField signatureField, TextArea resultArea, ComboBox<String> profileCombo,
            TextField pathLengthField) {
        this.certIssueCsrArea = csrArea;
        this.certIssueCaCertArea = caCertArea;
        this.certIssueCaKeyArea = caKeyArea;
        this.certIssueValidityField = validityField;
        this.certIssueSignatureField = signatureField;
        this.certIssueResultArea = resultArea;
        this.certIssueProfileCombo = profileCombo;
        this.certIssuePathLengthField = pathLengthField;

        if (certIssueProfileCombo != null) {
            certIssueProfileCombo.getItems().setAll(Arrays.stream(CertificateAuthorityOperations.IssuanceProfile.values())
                .map(Enum::name).toList());
            certIssueProfileCombo.setValue(CertificateAuthorityOperations.IssuanceProfile.TLS_SERVER.name());
        }
    }

    public void initializeCrlManagement(TextArea issuerCertArea, TextArea issuerKeyArea, TextArea existingCrlArea,
            TextField serialField, ComboBox<String> reasonCombo, TextArea resultArea) {
        this.crlIssuerCertArea = issuerCertArea;
        this.crlIssuerKeyArea = issuerKeyArea;
        this.crlExistingCrlArea = existingCrlArea;
        this.crlRevokeSerialField = serialField;
        this.crlRevokeReasonCombo = reasonCombo;
        this.crlResultArea = resultArea;

        if (crlRevokeReasonCombo != null) {
            crlRevokeReasonCombo.getItems().setAll(
                "UNSPECIFIED", "KEY_COMPROMISE", "CA_COMPROMISE", "AFFILIATION_CHANGED",
                "SUPERSEDED", "CESSATION_OF_OPERATION", "CERTIFICATE_HOLD", "PRIVILEGE_WITHDRAWN"
            );
            crlRevokeReasonCombo.setValue("UNSPECIFIED");
        }
    }

    public void initializeCertificateChainValidation(TextArea chainArea, TextArea trustAnchorArea, TextArea resultArea) {
        this.chainInputArea = chainArea;
        this.chainCrlInputArea = trustAnchorArea;
        this.chainResultArea = resultArea;
    }

    public void handleIssueCertificateFromCsr() {
        try {
            String csrPem = certIssueCsrArea.getText().trim();
            String compact = csrPem.replaceAll("-----[^-]+-----|\\s", "");
            var csr = new org.bouncycastle.pkcs.PKCS10CertificationRequest(java.util.Base64.getDecoder().decode(compact));
            var factory = java.security.cert.CertificateFactory.getInstance("X.509");
            var issuerCert = (X509Certificate) factory.generateCertificate(new java.io.ByteArrayInputStream(
                    certIssueCaCertArea.getText().trim().getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
            var issuerKey = AsymmetricKeyOperations.importPrivateKeyPEMAuto(certIssueCaKeyArea.getText().trim());
            int validityDays = Integer.parseInt(certIssueValidityField.getText().trim());
            String overrideAlgorithm = certIssueSignatureField.getText().trim();
            if ("Automatic".equalsIgnoreCase(overrideAlgorithm)) overrideAlgorithm = null;
            if (overrideAlgorithm == null || overrideAlgorithm.isBlank()) {
                overrideAlgorithm = CertificateAuthorityOperations.suggestSignatureAlgorithm(issuerKey);
            }

            CertificateAuthorityOperations.IssuanceProfile profile = CertificateAuthorityOperations.IssuanceProfile.TLS_SERVER;
            if (certIssueProfileCombo != null && certIssueProfileCombo.getValue() != null) {
                profile = CertificateAuthorityOperations.IssuanceProfile.valueOf(certIssueProfileCombo.getValue());
            }

            int pathLength = -1;
            if (profile == CertificateAuthorityOperations.IssuanceProfile.INTERMEDIATE_CA) {
                try {
                    pathLength = Integer.parseInt(certIssuePathLengthField.getText().trim());
                } catch (NumberFormatException ignored) {}
            }

            var issued = CertificateAuthorityOperations.issueFromCsr(csr, issuerCert, issuerKey, validityDays, overrideAlgorithm, profile, pathLength);
            String outputText = "=== ISSUED CERTIFICATE ===\n\n"
                    + CertificateGenerator.getCertificateInfo(issued)
                    + "\n\n" + CertificateGenerator.exportCertificatePEM(issued);
            certIssueResultArea.setText(outputText);
            updateStatus("Certificate issued from validated CSR");
            if (mainController != null) {
                mainController.publish(com.cryptocarver.model.OperationResult.forOperation("Issue CA Certificate")
                    .enrichedOutput(outputText, com.cryptocarver.model.OperationDetail.Classification.PUBLIC)
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Target", issued.getSubjectX500Principal().getName(), com.cryptocarver.model.OperationDetail.Classification.PUBLIC, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", outputText, com.cryptocarver.model.OperationDetail.Classification.PUBLIC, false, null)
                    ))
                    .build());
            }
        } catch (Exception e) {
            showError("Issue Certificate", "Cannot issue certificate: " + e.getMessage());
        }
    }

    public void handleGenerateCrl() {
        try {
            var factory = java.security.cert.CertificateFactory.getInstance("X.509");
            var issuerCert = (X509Certificate) factory.generateCertificate(new java.io.ByteArrayInputStream(
                    crlIssuerCertArea.getText().trim().getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
            var issuerKey = AsymmetricKeyOperations.importPrivateKeyPEMAuto(crlIssuerKeyArea.getText().trim());

            var crl = RevocationOperations.generateEmptyCrl(issuerCert, issuerKey);
            String outputText = RevocationOperations.exportCrlToPem(crl);
            crlResultArea.setText(outputText);
            updateStatus("Empty CRL generated successfully");
            if (mainController != null) {
                mainController.publish(OperationResult.forOperation("Generate CRL")
                        .enrichedOutput(outputText, com.cryptocarver.model.OperationDetail.Classification.PUBLIC)
                        .detail(com.cryptocarver.model.OperationDetail.publicDetail(
                                "Issuer", issuerCert.getSubjectX500Principal().getName()))
                        .status("Empty CRL generated successfully")
                        .build());
            }
        } catch (Exception e) {
            showError("Generate CRL", "Failed to generate CRL: " + e.getMessage());
        }
    }

    public void handleRevokeCrl() {
        try {
            var factory = java.security.cert.CertificateFactory.getInstance("X.509");
            var issuerCert = (X509Certificate) factory.generateCertificate(new java.io.ByteArrayInputStream(
                    crlIssuerCertArea.getText().trim().getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
            var issuerKey = AsymmetricKeyOperations.importPrivateKeyPEMAuto(crlIssuerKeyArea.getText().trim());
            String existingCrlStr = crlExistingCrlArea.getText().trim();
            java.security.cert.X509CRL existingCrl = null;
            if (!existingCrlStr.isEmpty()) {
                existingCrl = RevocationOperations.parseCrlPem(existingCrlStr);
            }

            String serialStr = crlRevokeSerialField.getText().trim();
            if (serialStr.isEmpty()) throw new IllegalArgumentException("Serial number required for revocation");
            java.math.BigInteger serial = new java.math.BigInteger(serialStr, 16);

            String reasonStr = crlRevokeReasonCombo.getValue();
            int reason = org.bouncycastle.asn1.x509.CRLReason.unspecified;
            if (reasonStr != null) {
                switch (reasonStr) {
                    case "KEY_COMPROMISE": reason = org.bouncycastle.asn1.x509.CRLReason.keyCompromise; break;
                    case "CA_COMPROMISE": reason = org.bouncycastle.asn1.x509.CRLReason.cACompromise; break;
                    case "AFFILIATION_CHANGED": reason = org.bouncycastle.asn1.x509.CRLReason.affiliationChanged; break;
                    case "SUPERSEDED": reason = org.bouncycastle.asn1.x509.CRLReason.superseded; break;
                    case "CESSATION_OF_OPERATION": reason = org.bouncycastle.asn1.x509.CRLReason.cessationOfOperation; break;
                    case "CERTIFICATE_HOLD": reason = org.bouncycastle.asn1.x509.CRLReason.certificateHold; break;
                    case "PRIVILEGE_WITHDRAWN": reason = org.bouncycastle.asn1.x509.CRLReason.privilegeWithdrawn; break;
                }
            }

            var crl = RevocationOperations.appendRevocation(existingCrl, issuerCert, issuerKey, serial, reason, new java.util.Date());
            String outputText = RevocationOperations.exportCrlToPem(crl);
            crlResultArea.setText(outputText);
            updateStatus("CRL updated successfully");
            if (mainController != null) {
                mainController.publish(OperationResult.forOperation("Update CRL")
                        .enrichedOutput(outputText, com.cryptocarver.model.OperationDetail.Classification.PUBLIC)
                        .detail(com.cryptocarver.model.OperationDetail.publicDetail("Revoked Serial", serial.toString(16)))
                        .status("CRL updated successfully")
                        .build());
            }
        } catch (Exception e) {
            showError("Update CRL", "Failed to update CRL: " + e.getMessage());
        }
    }

    public void handleCompareCertificates() {
        try {
            var factory = java.security.cert.CertificateFactory.getInstance("X.509");
            var left = (X509Certificate) factory.generateCertificate(new java.io.ByteArrayInputStream(
                    certCompareLeftArea.getText().trim().getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
            var right = (X509Certificate) factory.generateCertificate(new java.io.ByteArrayInputStream(
                    certCompareRightArea.getText().trim().getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
            String outputText = CertificateComparator.compare(left, right);
            certCompareResultArea.setText(outputText);
            updateStatus("Certificates compared");
            if (mainController != null) {
                mainController.publish(OperationResult.forOperation("Compare Certificates")
                        .enrichedOutput(outputText, com.cryptocarver.model.OperationDetail.Classification.PUBLIC)
                        .status("Certificates compared")
                        .build());
            }
        } catch (Exception e) {
            showError("Compare Certificates", "Cannot compare certificates: " + e.getMessage());
        }
    }

    /**
     * Initialize Validate Certificate components
     */
    public void initializeValidateCertificate(TextArea valCertInput, TextArea valIssuerInput, TextArea valResultArea) {
        this.valCertInput = valCertInput;
        this.valIssuerInput = valIssuerInput;
        this.valResultArea = valResultArea;
    }

    /**
     * Initialize Validate Chain components
     */
    public void initializeValidateChain() {
        // No components to initialize for now
    }

    /**
     * Generate a random key
     */
    public void handleGenerateKey() {
        try {
            String keyType = keyTypeCombo.getValue();
            if (keyType == null) {
                showError("Input Error", "Please select a key type");
                return;
            }

            boolean forceParity = forceOddParityCheck.isSelected();
            byte[] key = KeyOperations.generateKey(keyType, forceParity);
            String keyHex = DataConverter.bytesToHex(key);
            generatedKeyField.setText(keyHex);

            this.lastGeneratedSymmetricKeyBytes = key;
            this.lastGeneratedSymmetricKeyType = keyType;
            if (saveGeneratedKeyButton != null) {
                saveGeneratedKeyButton.setDisable(false);
            }

            // Create and update GeneratedKeySummary card
            GeneratedKeySummary summary = new GeneratedKeySummary(key, keyType, forceParity);
            this.currentGeneratedKeySummary = summary;
            updateGeneratedKeySummaryCard(summary);

            String parityStatus = forceParity ? " with odd parity" : " without parity adjustment";
            updateStatus("Generated " + keyType + " key" + parityStatus);

            // Delegate to ModernMainController history if available
            if (mainController != null) {
                try {
                    java.util.List<com.cryptocarver.model.OperationDetail> details = new java.util.ArrayList<>();
                    details.add(com.cryptocarver.model.OperationDetail.publicDetail("Key Type", keyType));
                    details.add(com.cryptocarver.model.OperationDetail.secretDetail("Generated Key", keyHex));
                    try {
                        if (keyType.contains("DES") || keyType.contains("3DES")) {
                            byte[] kcv = KeyOperations.calculateKCV_VISA(key);
                            details.add(com.cryptocarver.model.OperationDetail.publicDetail("KCV (VISA)", DataConverter.bytesToHex(kcv)));
                        } else {
                            byte[] kcv = KeyOperations.calculateKCV_AES(key);
                            details.add(com.cryptocarver.model.OperationDetail.publicDetail("KCV (AES)", DataConverter.bytesToHex(kcv)));
                        }
                    } catch (Exception e) {
                        details.add(com.cryptocarver.model.OperationDetail.publicDetail("KCV", "Error calculating"));
                    }

                    mainController.publish(OperationResult.forOperation("Generate Symmetric Key")
                            .output(key)
                            .details(details)
                            .status("Generated " + keyType + " key" + parityStatus)
                            .build());
                } catch (Exception e) {
                    System.err.println("Failed to add to history: " + e.getMessage());
                }
            } else {
                if (mainController != null) {
                mainController.publish(com.cryptocarver.model.OperationResult.forOperation("Generate Symmetric Key")
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", keyType, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", "Key: " + keyHex, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null)
                    ))
                    .build());
            }
            }

        } catch (Exception e) {
            showError("Generation Error", "Error generating key: " + e.getMessage());
        }
    }

    @FXML
    public void handleSaveGeneratedKeyToLab() {
        byte[] keyBytes = this.lastGeneratedSymmetricKeyBytes;
        String algoName = this.lastGeneratedSymmetricKeyType;

        if (keyBytes == null || keyBytes.length == 0) {
            showError("No Key Available", "Generate a key first before saving to Key Lab.");
            return;
        }

        if (algoName == null || algoName.isEmpty()) {
            algoName = "AES-256";
        }

        String fingerprint = com.cryptocarver.crypto.hsm.KeyMaterialFactory.generateFingerprint(keyBytes);
        String kcvHex = "N/A";
        try {
            byte[] kcvBytes = (algoName.contains("DES") || algoName.contains("3DES"))
                    ? KeyOperations.calculateKCV_VISA(keyBytes)
                    : KeyOperations.calculateKCV_AES(keyBytes);
            kcvHex = DataConverter.bytesToHex(kcvBytes);
        } catch (Exception ignored) {}

        // Fingerprint Duplicate Check
        com.cryptocarver.crypto.hsm.KeyMaterial existing = com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().findKeyByFingerprint(fingerprint);
        if (existing != null) {
            showInfo("Duplicate Key Detected",
                    "A key with an identical fingerprint already exists in Key Lab:\n\n"
                    + "Name: " + existing.getName() + "\n"
                    + "ID: " + existing.getId() + "\n"
                    + "Algorithm: " + existing.getAlgorithm() + "\n"
                    + "KCV: " + existing.getKcv() + "\n\n"
                    + "No duplicate entry was created.");
            refreshKeyLabTable();
            if (keyLabTable != null) {
                keyLabTable.getItems().stream()
                        .filter(km -> km.getId().equals(existing.getId()))
                        .findFirst()
                        .ifPresent(km -> {
                            keyLabTable.getSelectionModel().select(km);
                            showKeyLabDetails(km);
                        });
            }
            if (mainController != null) {
                mainController.refreshHsmKeyCombos();
            }
            updateStatus("Key already exists in Key Lab: " + existing.getName() + " (ID: " + existing.getId() + ")");
            return;
        }

        // Show metadata dialog
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Save Generated Key to Key Lab");
        dialog.setHeaderText("Specify key metadata for Simulated HSM / Key Lab");

        javafx.scene.control.ButtonType saveButtonType = new javafx.scene.control.ButtonType("Save to Key Lab", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, javafx.scene.control.ButtonType.CANCEL);

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(15));

        // Metadata summary
        javafx.scene.control.Label summaryLabel = new javafx.scene.control.Label(
                "Algorithm: " + algoName + "  |  Length: " + (keyBytes.length * 8) + " bits  |  KCV: " + kcvHex + "  |  Origin: Generated"
        );
        summaryLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #3b82f6;");
        grid.add(summaryLabel, 0, 0, 2, 1);

        javafx.scene.control.Label nameLabel = new javafx.scene.control.Label("Name (1-100 chars):");
        javafx.scene.control.TextField nameField = new javafx.scene.control.TextField("Generated " + algoName + " Key");
        nameField.setPromptText("Enter key name...");
        grid.add(nameLabel, 0, 1);
        grid.add(nameField, 1, 1);

        javafx.scene.control.Label usageLabel = new javafx.scene.control.Label("Key Usages:");
        javafx.scene.layout.VBox usageBox = new javafx.scene.layout.VBox(5);
        javafx.scene.control.CheckBox chkEncrypt = new javafx.scene.control.CheckBox("ENCRYPT"); chkEncrypt.setSelected(true);
        javafx.scene.control.CheckBox chkDecrypt = new CheckBox("DECRYPT"); chkDecrypt.setSelected(true);
        javafx.scene.control.CheckBox chkMac = new javafx.scene.control.CheckBox("MAC"); chkMac.setSelected(algoName.contains("HMAC") || algoName.contains("GMAC"));
        javafx.scene.control.CheckBox chkWrap = new javafx.scene.control.CheckBox("WRAP / UNWRAP (KEY_WRAP)"); chkWrap.setSelected(true);
        usageBox.getChildren().addAll(chkEncrypt, chkDecrypt, chkMac, chkWrap);
        grid.add(usageLabel, 0, 2);
        grid.add(usageBox, 1, 2);

        javafx.scene.control.Label exportLabel = new javafx.scene.control.Label("Exportability:");
        javafx.scene.control.ComboBox<com.cryptocarver.crypto.hsm.KeyExportability> exportCombo = new javafx.scene.control.ComboBox<>();
        exportCombo.getItems().addAll(com.cryptocarver.crypto.hsm.KeyExportability.NON_EXPORTABLE, com.cryptocarver.crypto.hsm.KeyExportability.EXPORTABLE);
        exportCombo.setValue(com.cryptocarver.crypto.hsm.KeyExportability.NON_EXPORTABLE);
        grid.add(exportLabel, 0, 3);
        grid.add(exportCombo, 1, 3);

        dialog.getDialogPane().setContent(grid);

        // Validation
        javafx.scene.Node saveButton = dialog.getDialogPane().lookupButton(saveButtonType);
        nameField.textProperty().addListener((obs, oldVal, newVal) -> {
            boolean valid = newVal != null && !newVal.trim().isEmpty() && newVal.trim().length() <= 100;
            if (saveButton != null) {
                saveButton.setDisable(!valid);
            }
        });

        if (!Boolean.getBoolean("test.mode") && !Boolean.getBoolean("runUiTests") && !System.getProperty("java.awt.headless", "false").equals("true")) {
            java.util.Optional<javafx.scene.control.ButtonType> result = dialog.showAndWait();
            if (result.isEmpty() || result.get() != saveButtonType) {
                return;
            }
        }

        String keyName = nameField.getText().trim();
        if (keyName.isEmpty() || keyName.length() > 100) {
            keyName = "Generated " + algoName + " Key";
        }

        java.util.Set<com.cryptocarver.crypto.hsm.KeyUsage> usages = new java.util.HashSet<>();
        if (chkEncrypt.isSelected()) usages.add(com.cryptocarver.crypto.hsm.KeyUsage.ENCRYPT);
        if (chkDecrypt.isSelected()) usages.add(com.cryptocarver.crypto.hsm.KeyUsage.DECRYPT);
        if (chkMac.isSelected()) usages.add(com.cryptocarver.crypto.hsm.KeyUsage.MAC);
        if (chkWrap.isSelected()) {
            usages.add(com.cryptocarver.crypto.hsm.KeyUsage.WRAP);
            usages.add(com.cryptocarver.crypto.hsm.KeyUsage.UNWRAP);
        }
        if (usages.isEmpty()) {
            usages.add(com.cryptocarver.crypto.hsm.KeyUsage.ENCRYPT);
            usages.add(com.cryptocarver.crypto.hsm.KeyUsage.DECRYPT);
        }

        com.cryptocarver.crypto.hsm.KeyExportability exportability = exportCombo.getValue();
        if (exportability == null) exportability = com.cryptocarver.crypto.hsm.KeyExportability.NON_EXPORTABLE;

        javax.crypto.SecretKey secretKey = new javax.crypto.spec.SecretKeySpec(keyBytes, algoName);
        String keyId = java.util.UUID.randomUUID().toString();

        com.cryptocarver.crypto.hsm.KeyMaterial km = new com.cryptocarver.crypto.hsm.KeyMaterial(
                keyId,
                fingerprint,
                com.cryptocarver.crypto.hsm.KeyType.SYMMETRIC,
                algoName,
                keyBytes.length * 8,
                com.cryptocarver.crypto.hsm.KeyFormat.RAW,
                usages,
                exportability,
                secretKey,
                null,
                keyName,
                "Generated",
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                kcvHex,
                "ACTIVE",
                true
        );

        com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().importKey(km);

        refreshKeyLabTable();
        if (keyLabTable != null) {
            keyLabTable.getItems().stream()
                    .filter(item -> item.getId().equals(keyId))
                    .findFirst()
                    .ifPresent(item -> {
                        keyLabTable.getSelectionModel().select(item);
                        showKeyLabDetails(item);
                    });
        }

        if (mainController != null) {
            mainController.refreshHsmKeyCombos();
        }

        if (currentGeneratedKeySummary != null) {
            String status = "Saved to Key Lab (" + keyName + ")";
            currentGeneratedKeySummary.setSavedStatus(status);
            if (summarySavedStatusLabel != null) {
                summarySavedStatusLabel.setText("✓ " + status);
            }
        }

        updateStatus("Saved generated key to Key Lab: " + keyName + " — " + algoName + " — KCV " + kcvHex);
    }

    private void hideGeneratedKeySummary() {
        this.currentGeneratedKeySummary = null;
        if (generatedKeySummaryCard != null) {
            generatedKeySummaryCard.setVisible(false);
            generatedKeySummaryCard.setManaged(false);
        }
    }

    private void updateGeneratedKeySummaryCard(com.cryptocarver.model.GeneratedKeySummary summary) {
        if (generatedKeySummaryCard == null || summary == null) return;
        if (summaryAlgoLabel != null) summaryAlgoLabel.setText(summary.getAlgorithm());
        if (summaryLengthLabel != null) summaryLengthLabel.setText(summary.getFormattedLength());
        if (summaryKcvLabel != null) summaryKcvLabel.setText(summary.getFormattedKcv());
        if (summaryFingerprintLabel != null) summaryFingerprintLabel.setText(summary.getFingerprintTruncated());
        if (summaryParityLabel != null) summaryParityLabel.setText(summary.getParityStatus());
        if (summaryOriginLabel != null) summaryOriginLabel.setText(summary.getOrigin());
        if (summarySavedStatusLabel != null) {
            summarySavedStatusLabel.setText(summary.getSavedStatus() != null ? "✓ " + summary.getSavedStatus() : "");
        }
        generatedKeySummaryCard.setVisible(true);
        generatedKeySummaryCard.setManaged(true);
    }

    @FXML
    public void handleCopyGeneratedKey() {
        if (currentGeneratedKeySummary == null || currentGeneratedKeySummary.getRawKeyBytes().length == 0) {
            updateStatus("No generated key summary available to copy.");
            return;
        }
        com.cryptocarver.model.SecretVisibilityProfile profile = com.cryptocarver.model.AppSettings.getInstance().getSecretVisibilityProfile();
        if (profile != com.cryptocarver.model.SecretVisibilityProfile.FULL_LAB) {
            updateStatus("Action blocked: Secret key cannot be copied in current visibility mode.");
            showInfo("Security Policy", "Copying key material is blocked under " + profile + " mode. Switch to FULL_LAB to copy secret keys.");
            return;
        }
        copyToClipboard(currentGeneratedKeySummary.getRawKeyHex());
        updateStatus("Copied generated key to clipboard");
    }

    @FXML
    public void handleCopyGeneratedKcv() {
        if (currentGeneratedKeySummary == null) {
            updateStatus("No generated key summary available to copy.");
            return;
        }
        String kcv = currentGeneratedKeySummary.getFormattedKcv();
        copyToClipboard(kcv);
        updateStatus("Copied KCV to clipboard: " + kcv);
    }

    @FXML
    public void handleCopyGeneratedSummary() {
        if (currentGeneratedKeySummary == null) {
            updateStatus("No generated key summary available to copy.");
            return;
        }
        com.cryptocarver.model.SecretVisibilityProfile profile = com.cryptocarver.model.AppSettings.getInstance().getSecretVisibilityProfile();
        String keyDisplay = (profile == com.cryptocarver.model.SecretVisibilityProfile.FULL_LAB)
                ? currentGeneratedKeySummary.getRawKeyHex()
                : "***MASKED***";

        StringBuilder sb = new StringBuilder();
        sb.append("--- Generated Key Summary ---\n");
        sb.append("Algorithm: ").append(currentGeneratedKeySummary.getAlgorithm()).append("\n");
        sb.append("Length: ").append(currentGeneratedKeySummary.getFormattedLength()).append("\n");
        sb.append("KCV: ").append(currentGeneratedKeySummary.getFormattedKcv()).append("\n");
        sb.append("Fingerprint: ").append(currentGeneratedKeySummary.getFingerprintTruncated()).append("\n");
        sb.append("Odd Parity: ").append(currentGeneratedKeySummary.getParityStatus()).append("\n");
        sb.append("Origin: ").append(currentGeneratedKeySummary.getOrigin()).append("\n");
        sb.append("Key: ").append(keyDisplay);
        if (currentGeneratedKeySummary.getSavedStatus() != null) {
            sb.append("\nStatus: ").append(currentGeneratedKeySummary.getSavedStatus());
        }

        copyToClipboard(sb.toString());
        updateStatus("Copied Key Summary to clipboard");
    }

    @FXML
    public void handleOpenValidationAndKcv() {
        if (currentGeneratedKeySummary == null) {
            updateStatus("No generated key available for validation.");
            return;
        }
        com.cryptocarver.model.SecretVisibilityProfile profile = com.cryptocarver.model.AppSettings.getInstance().getSecretVisibilityProfile();
        if (profile == com.cryptocarver.model.SecretVisibilityProfile.FULL_LAB && keyInputField != null) {
            keyInputField.setText(currentGeneratedKeySummary.getRawKeyHex());
        }
        if (validationPane != null) {
            validationPane.setExpanded(true);
        }
        handleValidateKey();
    }

    private void copyToClipboard(String text) {
        if (text == null) return;
        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);
    }

    /**
     * Validate a key and calculate all KCVs
     */
    public void handleValidateKey() {
        try {
            String keyHex = keyInputField.getText().trim();
            if (keyHex.isEmpty()) {
                showError("Input Error", "Please enter a key in hexadecimal");
                return;
            }

            byte[] key = DataConverter.hexToBytes(keyHex);

            if (!KeyOperations.isValidKeyLength(key)) {
                showError("Validation Error",
                        "Invalid key length. Key must be 8, 16, 24, or 32 bytes (16, 32, 48, or 64 hex characters)");
                return;
            }

            StringBuilder result = new StringBuilder();
            result.append("========================================\n");
            result.append("KEY VALIDATION RESULTS\n");
            result.append("========================================\n\n");

            result.append("Key: ").append(keyHex).append("\n");
            result.append("Key Length: ").append(key.length).append(" bytes (")
                    .append(key.length * 8).append(" bits)\n");
            result.append("Key Type: ").append(KeyOperations.getKeyType(key)).append("\n\n");

            // Detect parity
            KeyOperations.ParityType parity = KeyOperations.detectParity(key);
            result.append("Parity Detected: ").append(parity).append("\n\n");

            // Calculate all KCVs
            result.append("----------------------------------------\n");
            result.append("KEY CHECK VALUES (KCV)\n");
            result.append("----------------------------------------\n\n");

            try {
                byte[] kcvVisa = KeyOperations.calculateKCV_VISA(key);
                result.append("KCV (VISA):     ").append(DataConverter.bytesToHex(kcvVisa)).append("\n");
            } catch (Exception e) {
                result.append("KCV (VISA):     Error - ").append(e.getMessage()).append("\n");
            }

            try {
                byte[] kcvAtalla = KeyOperations.calculateKCV_ATALLA(key);
                result.append("KCV (ATALLA):   ").append(DataConverter.bytesToHex(kcvAtalla)).append("\n\n");
            } catch (Exception e) {
                result.append("KCV (ATALLA):   Error - ").append(e.getMessage()).append("\n\n");
            }

            result.append("--- Modern Methods ---\n\n");

            try {
                byte[] kcvSha256 = KeyOperations.calculateKCV_SHA256(key);
                result.append("KCV (SHA256):   ").append(DataConverter.bytesToHex(kcvSha256)).append("\n");
            } catch (Exception e) {
                result.append("KCV (SHA256):   Error - ").append(e.getMessage()).append("\n");
            }

            try {
                byte[] kcvCMAC = KeyOperations.calculateKCV_CMAC(key);
                result.append("KCV (CMAC):     ").append(DataConverter.bytesToHex(kcvCMAC)).append("\n");
            } catch (Exception e) {
                result.append("KCV (CMAC):     Error - ").append(e.getMessage()).append("\n");
            }

            // Only calculate AES KCV for AES keys
            if (key.length == 16 || key.length == 24 || key.length == 32) {
                try {
                    byte[] kcvAES = KeyOperations.calculateKCV_AES(key);
                    result.append("KCV (AES):      ").append(DataConverter.bytesToHex(kcvAES)).append("\n");
                } catch (Exception e) {
                    result.append("KCV (AES):      Error - ").append(e.getMessage()).append("\n");
                }
            }

            result.append("\n========================================\n");

            validationResultArea.setText(result.toString());
            validationResultArea.setVisible(true);
            validationResultArea.setManaged(true);
            updateStatus(com.cryptocarver.service.I18nService.getInstance().text("module.keys.validated"));

            // Publish a coherent result so the inspector, history and expanded
            // viewer contain the actual validation report and the input key is
            // consistently classified as sensitive.
            if (mainController != null) {
                try {
                    java.util.List<com.cryptocarver.model.OperationDetail> details = new java.util.ArrayList<>();
                    details.add(com.cryptocarver.model.OperationDetail.secretDetail("Key", keyHex));
                    details.add(com.cryptocarver.model.OperationDetail.publicDetail("Validation Report", result.toString()));
                    mainController.publish(OperationResult.forOperation("Validate Symmetric Key")
                            .input(key)
                            .output(result.toString().getBytes(StandardCharsets.UTF_8))
                            .details(details)
                            .status("Key validated successfully")
                            .build());
                } catch (Exception e) {
                    System.err.println("Failed to add to history: " + e.getMessage());
                }
            } else {
                // Add to history (Legacy)
                String keyType = KeyOperations.getKeyType(key);
                if (mainController != null) {
                mainController.publish(com.cryptocarver.model.OperationResult.forOperation("Validate - " + keyType)
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", "Key: " + keyHex, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", result.toString(), com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null)
                    ))
                    .build());
            }
            }

        } catch (IllegalArgumentException e) {
            showError("Input Error", e.getMessage());
        } catch (Exception e) {
            showError("Validation Error", "Error validating key: " + e.getMessage());
        }
    }

    /**
     * Split a key into components
     */
    public void handleSplitKey() {
        try {
            String keyHex = keyToSplitField.getText().trim();
            if (keyHex.isEmpty()) {
                showError("Input Error", "Please enter a key to split");
                return;
            }

            byte[] key = DataConverter.hexToBytes(keyHex);

            if (!KeyOperations.isValidKeyLength(key)) {
                showError("Validation Error",
                        "Invalid key length. Key must be 8, 16, 24, or 32 bytes");
                return;
            }

            int numComponents = Integer.parseInt(numComponentsCombo.getValue());

            byte[][] components = KeyOperations.splitKey(key, numComponents);

            StringBuilder result = new StringBuilder();
            result.append("========================================\n");
            result.append("KEY SPLITTING RESULTS\n");
            result.append("========================================\n\n");

            result.append("Original Key: ").append(keyHex).append("\n");
            result.append("Number of Components: ").append(numComponents).append("\n\n");

            result.append("Components (XOR these to get original key):\n\n");
            for (int i = 0; i < numComponents; i++) {
                String componentHex = DataConverter.bytesToHex(components[i]);
                result.append("Component ").append(i + 1).append(": ").append(componentHex).append("\n");

                // Also set in individual text fields for easy copying
                switch (i) {
                    case 0:
                        component1Field.setText(componentHex);
                        break;
                    case 1:
                        component2Field.setText(componentHex);
                        break;
                    case 2:
                        component3Field.setText(componentHex);
                        break;
                    case 3:
                        component4Field.setText(componentHex);
                        break;
                    case 4:
                        component5Field.setText(componentHex);
                        break;
                }
            }

            // Clear unused component fields
            if (numComponents < 3)
                component3Field.setText("");
            if (numComponents < 4)
                component4Field.setText("");
            if (numComponents < 5)
                component5Field.setText("");

            result.append("\n");

            // Calculate KCV of original key
            try {
                byte[] kcv = KeyOperations.calculateKCV_VISA(key);
                result.append("Original Key KCV (VISA): ").append(DataConverter.bytesToHex(kcv)).append("\n");
            } catch (Exception e) {
                // Ignore
            }

            result.append("\n========================================\n");
            result.append("ℹ️  XOR all components together to reconstruct the original key\n");
            result.append("ℹ️  Each component should be stored securely in separate locations\n");

            componentResultsArea.setText(result.toString());
            componentResultsArea.setVisible(true);
            componentResultsArea.setManaged(true);
            updateStatus("Key split into " + numComponents + " components");

            // Add to history
            if (mainController != null) {
                mainController.publish(com.cryptocarver.model.OperationResult.forOperation("Split - " + numComponents + " components")
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", "Input Key: " + keyHex, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", result.toString(), com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null)
                    ))
                    .build());
            }

        } catch (NumberFormatException e) {
            showError("Input Error", "Invalid number of components");
        } catch (Exception e) {
            showError("Splitting Error", "Error splitting key: " + e.getMessage());
        }
    }

    /**
     * Combine key components back into original key
     */
    public void handleCombineComponents() {
        try {
            String comp1 = component1Field.getText().trim();
            String comp2 = component2Field.getText().trim();

            if (comp1.isEmpty() || comp2.isEmpty()) {
                showError("Input Error", "Please enter at least 2 components");
                return;
            }

            // Collect all non-empty components
            java.util.List<byte[]> componentList = new java.util.ArrayList<>();
            componentList.add(DataConverter.hexToBytes(comp1));
            componentList.add(DataConverter.hexToBytes(comp2));

            if (!component3Field.getText().trim().isEmpty()) {
                componentList.add(DataConverter.hexToBytes(component3Field.getText().trim()));
            }
            if (!component4Field.getText().trim().isEmpty()) {
                componentList.add(DataConverter.hexToBytes(component4Field.getText().trim()));
            }
            if (!component5Field.getText().trim().isEmpty()) {
                componentList.add(DataConverter.hexToBytes(component5Field.getText().trim()));
            }

            byte[][] components = componentList.toArray(new byte[0][]);

            // Verify all components have the same length
            int length = components[0].length;
            for (byte[] comp : components) {
                if (comp.length != length) {
                    showError("Validation Error",
                            "All components must have the same length");
                    return;
                }
            }

            byte[] combinedKey = KeyOperations.combineKeyComponents(components);
            String combinedKeyHex = DataConverter.bytesToHex(combinedKey);

            // Display combined key in results area
            StringBuilder result = new StringBuilder();
            result.append("========================================\n");
            result.append("COMBINED KEY\n");
            result.append("========================================\n\n");
            result.append("Combined Key: ").append(combinedKeyHex).append("\n");
            result.append("Key Length:   ").append(combinedKey.length).append(" bytes (");
            result.append(combinedKey.length * 8).append(" bits)\n\n");

            // Calculate KCV
            try {
                byte[] kcv = KeyOperations.calculateKCV_VISA(combinedKey);
                result.append("KCV (VISA):   ").append(DataConverter.bytesToHex(kcv)).append("\n");
                result.append("\n========================================\n");
                updateStatus("Components combined. KCV: " + DataConverter.bytesToHex(kcv));
            } catch (Exception e) {
                result.append("\n========================================\n");
                updateStatus("Components combined successfully");
            }

            componentResultsArea.setText(result.toString());
            componentResultsArea.setVisible(true);
            componentResultsArea.setManaged(true);

            // Add to history
            if (mainController != null) {
                mainController.publish(com.cryptocarver.model.OperationResult.forOperation("Combine - " + components.length + " components")
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", "Components: " + components.length, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", combinedKeyHex.substring(0, Math.min(32, combinedKeyHex.length())), com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null)
                    ))
                    .build());
            }

        } catch (IllegalArgumentException e) {
            showError("Input Error", e.getMessage());
        } catch (Exception e) {
            showError("Combining Error", "Error combining components: " + e.getMessage());
        }
    }

    // ============================================================================
    // ADVANCED ASYMMETRIC KEY GENERATION
    // ============================================================================
    @FXML private Button rsaGenerateBtn;
    @FXML private Button dsaGenerateBtn;

    /**
     * Generate RSA key pair.
     * Note: JCA KeyPairGenerator executes internal prime-finding loops that do not check Thread.interrupted().
     * Cancellation here is UI/Interface Best-Effort cancellation: the UI thread detaches instantly, hides progress,
     * re-enables controls, and discards all output/history, while the JCA background task completes off the UI thread.
     */
    public void handleGenerateRSA() {
        try {
            Integer keySize = rsaKeySizeCombo.getValue();
            if (keySize == null) {
                showError("Input Error", "Please select RSA key size");
                return;
            }

            updateStatus("Generating RSA-" + keySize + " key pair... This may take a moment.");

            Callable<KeyPair> task = () -> AsymmetricKeyOperations.generateRSAKeyPair(keySize);

            Consumer<KeyPair> onSuccess = keyPair -> {
                try {
                    lastGeneratedKeyPair = keyPair;
                    lastKeyType = "RSA";

                    GeneratedAsymmetricKeySummary summary = new GeneratedAsymmetricKeySummary(keyPair, "RSA", keySize + " bits");
                    this.currentRsaSummary = summary;
                    updateAsymmetricSummaryCard(rsaSummaryCard, rsaSummaryAlgoLabel, rsaSummaryFingerprintLabel, rsaSummaryPubLenLabel, rsaSummaryPrivLenLabel, rsaSummaryCreatedLabel, rsaSummarySavedStatusLabel, summary);

                    String publicKeyInfo = AsymmetricKeyOperations.getRSAPublicKeyInfo(keyPair.getPublic());
                    String privateKeyInfo = AsymmetricKeyOperations.getRSAPrivateKeyInfo(keyPair.getPrivate());

                    rsaPublicKeyArea.setText("=== RSA PUBLIC KEY ===\n\n" + publicKeyInfo +
                            "\n\n=== PEM FORMAT ===\n" + AsymmetricKeyOperations.exportPublicKeyPEM(keyPair.getPublic()));

                    rsaPrivateKeyArea.setText("=== RSA PRIVATE KEY ===\n\n" + privateKeyInfo +
                            "\n\n=== PEM FORMAT ===\n" + AsymmetricKeyOperations.exportPrivateKeyPEM(keyPair.getPrivate()));

                    updateStatus("RSA-" + keySize + " key pair generated successfully");

                    if (mainController != null) {
                        try {
                            java.util.List<com.cryptocarver.model.OperationDetail> details = new java.util.ArrayList<>();
                            details.add(com.cryptocarver.model.OperationDetail.publicDetail("Key Size", keySize + " bits"));
                            details.add(com.cryptocarver.model.OperationDetail.publicDetail("Public Key", AsymmetricKeyOperations.exportPublicKeyPEM(keyPair.getPublic())));
                            details.add(com.cryptocarver.model.OperationDetail.secretDetail("Private Key", AsymmetricKeyOperations.exportPrivateKeyPEM(keyPair.getPrivate())));

                            mainController.publish(OperationResult.forOperation("Generate RSA Key")
                                    .output(AsymmetricKeyOperations.exportPublicKeyPEM(keyPair.getPublic())
                                            .getBytes(StandardCharsets.UTF_8))
                                    .enrichedOutput(renderGeneratedKeyPair(
                                            AsymmetricKeyOperations.exportPublicKeyPEM(keyPair.getPublic()),
                                            AsymmetricKeyOperations.exportPrivateKeyPEM(keyPair.getPrivate())),
                                            com.cryptocarver.model.OperationDetail.Classification.SECRET)
                                    .details(details)
                                    .status("RSA-" + keySize + " key pair generated successfully")
                                    .build());
                        } catch (Exception e) {
                            System.err.println("Failed to add to history: " + e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    showError("RSA Generation Error", e.getMessage());
                }
            };

            Consumer<Throwable> onFailure = err -> {
                showError("RSA Generation Error", err != null ? err.getMessage() : "Unknown error during key generation");
            };

            Runnable onCancelled = () -> {
                updateStatus("RSA key generation cancelled.");
            };

            if (mainController != null && mainController.getOperationExecutor() != null) {
                mainController.getOperationExecutor().execute("RSA-" + keySize + " Key Generation", rsaGenerateBtn, task, onSuccess, onFailure, onCancelled);
            } else {
                KeyPair kp = task.call();
                onSuccess.accept(kp);
            }
        } catch (Exception e) {
            showError("RSA Generation Error", e.getMessage());
        }
    }

    /**
     * Generate DSA key pair
     */
    public void handleGenerateDSA() {
        try {
            String keySize = dsaKeySizeCombo.getValue();
            if (keySize == null) {
                showError("Input Error", "Please select DSA key size");
                return;
            }

            updateStatus("Generating DSA-" + keySize + " key pair...");

            Callable<KeyPair> task = () -> AsymmetricKeyOperations.generateDSAKeyPair(keySize);

            Consumer<KeyPair> onSuccess = keyPair -> {
                try {
                    lastGeneratedKeyPair = keyPair;
                    lastKeyType = "DSA";

                    GeneratedAsymmetricKeySummary summary = new GeneratedAsymmetricKeySummary(keyPair, "DSA", keySize + " bits");
                    this.currentDsaSummary = summary;
                    updateAsymmetricSummaryCard(dsaSummaryCard, dsaSummaryAlgoLabel, dsaSummaryFingerprintLabel, dsaSummaryPubLenLabel, dsaSummaryPrivLenLabel, dsaSummaryCreatedLabel, dsaSummarySavedStatusLabel, summary);

                    String publicKeyInfo = AsymmetricKeyOperations.getDSAKeyInfo(keyPair.getPublic());
                    String privateKeyInfo = AsymmetricKeyOperations.getDSAKeyInfo(keyPair.getPrivate());

                    dsaPublicKeyArea.setText("=== DSA PUBLIC KEY ===\n\n" + publicKeyInfo +
                            "\n\n=== PEM FORMAT ===\n" + AsymmetricKeyOperations.exportPublicKeyPEM(keyPair.getPublic()));

                    dsaPrivateKeyArea.setText("=== DSA PRIVATE KEY ===\n\n" + privateKeyInfo +
                            "\n\n=== PEM FORMAT ===\n" + AsymmetricKeyOperations.exportPrivateKeyPEM(keyPair.getPrivate()));

                    updateStatus("DSA-" + keySize + " key pair generated successfully");

                    if (mainController != null) {
                        try {
                            java.util.List<com.cryptocarver.model.OperationDetail> details = new java.util.ArrayList<>();
                            details.add(com.cryptocarver.model.OperationDetail.publicDetail("Key Size", keySize));
                            details.add(com.cryptocarver.model.OperationDetail.publicDetail("Public Key", AsymmetricKeyOperations.exportPublicKeyPEM(keyPair.getPublic())));
                            details.add(com.cryptocarver.model.OperationDetail.secretDetail("Private Key", AsymmetricKeyOperations.exportPrivateKeyPEM(keyPair.getPrivate())));

                            mainController.publish(OperationResult.forOperation("Generate DSA Key")
                                    .output(AsymmetricKeyOperations.exportPublicKeyPEM(keyPair.getPublic())
                                            .getBytes(StandardCharsets.UTF_8))
                                    .enrichedOutput(renderGeneratedKeyPair(
                                            AsymmetricKeyOperations.exportPublicKeyPEM(keyPair.getPublic()),
                                            AsymmetricKeyOperations.exportPrivateKeyPEM(keyPair.getPrivate())),
                                            com.cryptocarver.model.OperationDetail.Classification.SECRET)
                                    .details(details)
                                    .status("DSA-" + keySize + " key pair generated successfully")
                                    .build());
                        } catch (Exception e) {
                            System.err.println("Failed to add to history: " + e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    showError("DSA Generation Error", e.getMessage());
                }
            };

            Consumer<Throwable> onFailure = err -> {
                showError("DSA Generation Error", err != null ? err.getMessage() : "Unknown error during key generation");
            };

            Runnable onCancelled = () -> {
                updateStatus(com.cryptocarver.service.I18nService.getInstance().text("module.keys.generationCancelled"));
            };

            if (mainController != null && mainController.getOperationExecutor() != null) {
                mainController.getOperationExecutor().execute("DSA-" + keySize + " Key Generation", dsaGenerateBtn, task, onSuccess, onFailure, onCancelled);
            } else {
                KeyPair kp = task.call();
                onSuccess.accept(kp);
            }
        } catch (Exception e) {
            showError("DSA Generation Error", e.getMessage());
        }
    }

    /**
     * Generate ECDSA F(p) key pair
     */
    public void handleGenerateECDSAFp() {
        try {
            String curve = ecdsaFpCurveCombo.getValue();
            if (curve == null) {
                showError("Input Error", "Please select a curve");
                return;
            }

            updateStatus("Generating ECDSA F(p) key pair on curve " + curve + "...");

            KeyPair keyPair = AsymmetricKeyOperations.generateECDSAFpKeyPair(curve);

            lastGeneratedKeyPair = keyPair;
            lastKeyType = "ECDSA";

            GeneratedAsymmetricKeySummary summary = new GeneratedAsymmetricKeySummary(keyPair, "ECDSA", curve);
            this.currentEcdsaSummary = summary;
            updateAsymmetricSummaryCard(ecdsaSummaryCard, ecdsaSummaryAlgoLabel, ecdsaSummaryFingerprintLabel, ecdsaSummaryPubLenLabel, ecdsaSummaryPrivLenLabel, ecdsaSummaryCreatedLabel, ecdsaSummarySavedStatusLabel, summary);

            String publicKeyInfo = AsymmetricKeyOperations.getECKeyInfo(keyPair.getPublic());
            String privateKeyInfo = AsymmetricKeyOperations.getECKeyInfo(keyPair.getPrivate());

            ecdsaFpPublicKeyArea.setText("=== ECDSA F(p) PUBLIC KEY ===\n" +
                    "Curve: " + curve + "\n\n" + publicKeyInfo +
                    "\n\n=== PEM FORMAT ===\n" + AsymmetricKeyOperations.exportPublicKeyPEM(keyPair.getPublic()));

            ecdsaFpPrivateKeyArea.setText("=== ECDSA F(p) PRIVATE KEY ===\n" +
                    "Curve: " + curve + "\n\n" + privateKeyInfo +
                    "\n\n=== PEM FORMAT ===\n" + AsymmetricKeyOperations.exportPrivateKeyPEM(keyPair.getPrivate()));

            updateStatus("ECDSA F(p) key pair generated on curve " + curve);

            if (mainController != null) {
                try {
                    java.util.List<com.cryptocarver.model.OperationDetail> details = new java.util.ArrayList<>();
                    details.add(com.cryptocarver.model.OperationDetail.publicDetail("Curve", curve));
                    details.add(com.cryptocarver.model.OperationDetail.publicDetail("Public Key", AsymmetricKeyOperations.exportPublicKeyPEM(keyPair.getPublic())));
                    details.add(com.cryptocarver.model.OperationDetail.secretDetail("Private Key", AsymmetricKeyOperations.exportPrivateKeyPEM(keyPair.getPrivate())));

                    mainController.publish(OperationResult.forOperation("Generate ECDSA Key")
                            .output(AsymmetricKeyOperations.exportPublicKeyPEM(keyPair.getPublic())
                                    .getBytes(StandardCharsets.UTF_8))
                            .enrichedOutput(renderGeneratedKeyPair(
                                    AsymmetricKeyOperations.exportPublicKeyPEM(keyPair.getPublic()),
                                    AsymmetricKeyOperations.exportPrivateKeyPEM(keyPair.getPrivate())),
                                    com.cryptocarver.model.OperationDetail.Classification.SECRET)
                            .details(details)
                            .status("ECDSA F(p) key pair generated on curve " + curve)
                            .build());
                } catch (Exception e) {
                    System.err.println("Failed to add to history: " + e.getMessage());
                }
            } else {
                if (mainController != null) {
                mainController.publish(com.cryptocarver.model.OperationResult.forOperation("Generate ECDSA F(p) - " + curve)
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", "N/A", com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", "Curve: " + curve, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null)
                    ))
                    .build());
            }
            }

        } catch (Exception e) {
            showError("Generation Error", "Error generating ECDSA F(p) key: " + e.getMessage());
        }
    }

    /**
     * Generate Ed25519 key pair
     */
    public void handleGenerateEd25519() {
        try {
            updateStatus("Generating Ed25519 key pair...");

            KeyPair keyPair = AsymmetricKeyOperations.generateEd25519KeyPair();

            lastGeneratedKeyPair = keyPair;
            lastKeyType = "Ed25519";

            GeneratedAsymmetricKeySummary summary = new GeneratedAsymmetricKeySummary(keyPair, "Ed25519", "Ed25519 (255-bit curve)");
            this.currentEddsaSummary = summary;
            updateAsymmetricSummaryCard(eddsaSummaryCard, eddsaSummaryAlgoLabel, eddsaSummaryFingerprintLabel, eddsaSummaryPubLenLabel, eddsaSummaryPrivLenLabel, eddsaSummaryCreatedLabel, eddsaSummarySavedStatusLabel, summary);

            ed25519PublicKeyArea.setText("=== Ed25519 PUBLIC KEY ===\n" +
                    "Algorithm: Ed25519 (255-bit curve)\n" +
                    "Use: Digital signatures (fast, secure)\n\n" +
                    "=== PEM FORMAT ===\n" + AsymmetricKeyOperations.exportPublicKeyPEM(keyPair.getPublic()));

            ed25519PrivateKeyArea.setText("=== Ed25519 PRIVATE KEY ===\n" +
                    "Algorithm: Ed25519 (255-bit curve)\n" +
                    "Use: Digital signatures (fast, secure)\n\n" +
                    "=== PEM FORMAT ===\n" + AsymmetricKeyOperations.exportPrivateKeyPEM(keyPair.getPrivate()));

            updateStatus("Ed25519 key pair generated successfully");

            if (mainController != null) {
                try {
                    String publicPem = AsymmetricKeyOperations.exportPublicKeyPEM(keyPair.getPublic());
                    java.util.List<com.cryptocarver.model.OperationDetail> details = new java.util.ArrayList<>();
                    details.add(com.cryptocarver.model.OperationDetail.publicDetail("Algorithm", "Ed25519"));
                    details.add(com.cryptocarver.model.OperationDetail.publicDetail("Public Key", publicPem));
                    details.add(com.cryptocarver.model.OperationDetail.secretDetail("Private Key",
                            AsymmetricKeyOperations.exportPrivateKeyPEM(keyPair.getPrivate())));
                    mainController.publish(OperationResult.forOperation("Generate EdDSA Key")
                            .output(publicPem.getBytes(StandardCharsets.UTF_8))
                            .enrichedOutput(renderGeneratedKeyPair(publicPem,
                                    AsymmetricKeyOperations.exportPrivateKeyPEM(keyPair.getPrivate())),
                                    com.cryptocarver.model.OperationDetail.Classification.SECRET)
                            .details(details)
                            .status("Ed25519 key pair generated successfully")
                            .build());
                } catch (Exception e) {
                    System.err.println("Failed to add to history: " + e.getMessage());
                }
            } else {
                if (mainController != null) {
                mainController.publish(com.cryptocarver.model.OperationResult.forOperation("Generate Ed25519")
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", "N/A", com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", "Algorithm: Ed25519", com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null)
                    ))
                    .build());
            }
            }

        } catch (Exception e) {
            showError("Generation Error", "Error generating Ed25519 key: " + e.getMessage());
        }
    }

    private String renderGeneratedKeyPair(String publicKeyPem, String privateKeyPem) {
        return "=== PUBLIC KEY ===\n\n" + publicKeyPem
                + "\n\n=== PRIVATE KEY ===\n\n" + privateKeyPem;
    }

    /**
     * Alias for handleGenerateEd25519 for Modern UI
     */
    public void handleGenerateEdDSA() {
        handleGenerateEd25519();
    }

    /**
     * Generate ECDSA F(2^m) key pair
     */

    /**
     * Generate self-signed X.509 certificate
     */
    public void handleGenerateCertificate() {
        try {
            // Validate inputs
            String cn = certCNField.getText().trim();
            if (cn.isEmpty()) {
                showError("Input Error", "Common Name (CN) is required");
                return;
            }

            int validity;
            try {
                validity = Integer.parseInt(certValidityField.getText().trim());
                if (validity <= 0)
                    throw new NumberFormatException();
            } catch (NumberFormatException e) {
                showError("Input Error", "Validity must be a positive number of days");
                return;
            }

            updateStatus("Generating certificate and key pair...");

            // Generate or use existing key pair
            KeyPair keyPair;
            String keyTypeDesc;

            String certKeyType = certKeyTypeCombo.getValue();
            if (certKeyType.startsWith("RSA")) {
                int keySize = Integer.parseInt(certKeyType.substring(4));
                keyPair = AsymmetricKeyOperations.generateRSAKeyPair(keySize);
                keyTypeDesc = "RSA-" + keySize;
            } else if (certKeyType.startsWith("ECDSA")) {
                String curve = certKeyType.equals("ECDSA-P256") ? "secp256r1" : "secp384r1";
                keyPair = AsymmetricKeyOperations.generateECDSAFpKeyPair(curve);
                keyTypeDesc = "ECDSA-" + curve;
            } else {
                showError("Input Error", "Invalid key type selected");
                return;
            }

            // Build certificate configuration
            CertificateGenerator.CertificateConfig config = new CertificateGenerator.CertificateConfig();
            config.commonName = cn;
            config.organization = certOrgField != null ? certOrgField.getText().trim() : "Crypto Org";
            config.organizationalUnit = certOUField != null ? certOUField.getText().trim() : "IT Security";
            config.locality = certLocalityField != null ? certLocalityField.getText().trim() : "Madrid";
            config.state = certStateField != null ? certStateField.getText().trim() : "Madrid";
            config.country = certCountryField != null ? certCountryField.getText().trim() : "ES";
            config.validityDays = validity;
            config.signatureAlgorithm = certSignAlgoCombo.getValue();
            applySanConfiguration(config);

            // Email is optional - only add if provided
            String email = certEmailField != null ? certEmailField.getText().trim() : "";
            config.email = email.isEmpty() ? null : email;

            // Generate certificate
            boolean rootCa = certRootCaCheck != null && certRootCaCheck.isSelected();
            X509Certificate certificate = rootCa
                    ? CertificateGenerator.generateRootCA(keyPair, config, 1)
                    : CertificateGenerator.generateSelfSignedCertificate(keyPair, config);

            // Build output
            StringBuilder output = new StringBuilder();
            output.append(rootCa ? "=== SELF-SIGNED ROOT CA (LABORATORY) ===\n\n" : "=== SELF-SIGNED X.509 CERTIFICATE ===\n\n");
            output.append(CertificateGenerator.getCertificateInfo(certificate));
            output.append("\n\n=== CERTIFICATE (PEM) ===\n");
            output.append(CertificateGenerator.exportCertificatePEM(certificate));
            output.append("\n=== PRIVATE KEY (PEM) ===\n");
            output.append(AsymmetricKeyOperations.exportPrivateKeyPEM(keyPair.getPrivate()));
            output.append("\n=== PUBLIC KEY (PEM) ===\n");
            output.append(AsymmetricKeyOperations.exportPublicKeyPEM(keyPair.getPublic()));

            certOutputArea.setText(output.toString());
            certOutputArea.setVisible(true);
            certOutputArea.setManaged(true);

            updateStatus("Certificate generated successfully with " + keyTypeDesc);

            if (mainController != null) {
                mainController.publish(com.cryptocarver.model.OperationResult.forOperation("Generate Certificate - " + keyTypeDesc)
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", "CN=" + cn + ", Validity=" + validity + " days", com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", output.toString(), com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null)
                    ))
                    .build());
            }

        } catch (Exception e) {
            showError("Generation Error", "Error generating certificate: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Generates a PKCS#10 request and a fresh laboratory key pair using the certificate form parameters. */
    public void handleGenerateCSR() {
        try {
            String cn = certCNField.getText().trim();
            if (cn.isEmpty()) throw new IllegalArgumentException("Common Name (CN) is required");
            String selected = certKeyTypeCombo.getValue();
            CertificateGenerator.CertificateConfig config = new CertificateGenerator.CertificateConfig();
            config.commonName = cn;
            config.organization = certOrgField.getText().trim();
            config.organizationalUnit = certOUField.getText().trim();
            config.locality = certLocalityField.getText().trim();
            config.state = certStateField.getText().trim();
            config.country = certCountryField.getText().trim();
            config.email = certEmailField.getText().trim().isEmpty() ? null : certEmailField.getText().trim();
            config.signatureAlgorithm = certSignAlgoCombo.getValue();
            applySanConfiguration(config);

            String csrPem;
            String keyDesc = "";

            if ("Local PEM (Parse Area)".equals(selected)) {
                String pem = certInputArea != null ? certInputArea.getText().trim() : "";
                if (pem.isEmpty()) throw new IllegalArgumentException("Please paste a private key in the 'Parse Certificate / Key' area");
                PrivateKey privateKey = parsePrivateMaterial(pem);
                PublicKey publicKey = AsymmetricKeyOperations.derivePublicKey(privateKey);
                KeyPair pair = new KeyPair(publicKey, privateKey);
                csrPem = CertificateGenerator.generateCSR(pair, config);
                keyDesc = "Local PEM Key";
            } else if ("PKCS#11 Active Alias".equals(selected)) {
                String alias = requirePkcs11SigningAlias();
                csrPem = com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().requireSession().generateCsr(alias, config);
                keyDesc = "PKCS#11 Token (Alias: " + alias + ")";
            } else {
                KeyPair pair;
                if (selected.startsWith("RSA")) pair = AsymmetricKeyOperations.generateRSAKeyPair(Integer.parseInt(selected.substring(4)));
                else if (selected.startsWith("ECDSA")) pair = AsymmetricKeyOperations.generateECDSAFpKeyPair(selected.equals("ECDSA-P256") ? "secp256r1" : "secp384r1");
                else throw new IllegalArgumentException("Unsupported CSR key type");
                csrPem = CertificateGenerator.generateCSR(pair, config);
                keyDesc = "Generated " + selected + "\n\n=== PRIVATE KEY (LABORATORY ONLY) ===\n" + AsymmetricKeyOperations.exportPrivateKeyPEM(pair.getPrivate());
            }

            String outputText = "=== PKCS#10 CERTIFICATE SIGNING REQUEST ===\n\n" + csrPem
                    + "\n" + (keyDesc.startsWith("Generated") ? keyDesc : "Source: " + keyDesc);
            certOutputArea.setText(outputText);
            certOutputArea.setManaged(true);
            certOutputArea.setVisible(true);
            updateStatus("CSR generated with requested SANs");

            if (mainController != null) {
                com.cryptocarver.model.OperationDetail.Classification cls = keyDesc.startsWith("Generated") ? com.cryptocarver.model.OperationDetail.Classification.SECRET : com.cryptocarver.model.OperationDetail.Classification.PUBLIC;
                mainController.publish(com.cryptocarver.model.OperationResult.forOperation("Generate CSR")
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Common Name", cn, com.cryptocarver.model.OperationDetail.Classification.PUBLIC, false, null),
                        new com.cryptocarver.model.OperationDetail("Source", keyDesc.startsWith("Generated") ? "Generated new pair" : keyDesc, cls, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", outputText, cls, false, null)
                    ))
                    .build());
            }
        } catch (Exception e) {
            showError("CSR Generation", "Cannot generate CSR: " + e.getMessage());
        }
    }

    private void applySanConfiguration(CertificateGenerator.CertificateConfig config) {
        config.sanDnsNames = commaSeparatedValues(certSanDnsField == null ? null : certSanDnsField.getText());
        config.sanIpAddresses = commaSeparatedValues(certSanIpField == null ? null : certSanIpField.getText());
        config.addSubjectAlternativeNames = !config.sanDnsNames.isEmpty() || !config.sanIpAddresses.isEmpty();
    }

    private List<String> commaSeparatedValues(String value) {
        if (value == null || value.isBlank()) return new ArrayList<>();
        return Arrays.stream(value.split(",")).map(String::trim).filter(part -> !part.isEmpty()).toList();
    }

    /**
     * Parse and display certificate information
     */
    public void handleParseCertificate() {
        try {
            if (certInputArea == null || certParseResultArea == null) {
                updateStatus("Certificate parsing not initialized");
                return;
            }

            String pemCert = certInputArea.getText().trim();
            if (pemCert.isEmpty()) {
                showError(new UserFacingError("Missing Certificate Input", "Please paste a certificate in PEM format.", "Provide X.509 PEM certificate data in the input area.", "certInputArea"));
                return;
            }

            updateStatus("Parsing certificate...");

            // Parse certificate using CertificateGenerator
            X509Certificate cert = CertificateGenerator.parseCertificate(pemCert);

            // Get certificate info
            String certInfo = CertificateGenerator.getCertificateInfo(cert);

            StringBuilder output = new StringBuilder();
            output.append("=== CERTIFICATE INFORMATION ===\n\n");
            output.append(certInfo);

            certParseResultArea.setText(output.toString());
            certParseResultArea.setVisible(true);
            certParseResultArea.setManaged(true);

            updateStatus("Certificate parsed successfully");

            if (mainController != null) {
                mainController.publish(com.cryptocarver.model.OperationResult.forOperation("Parse Certificate")
                    .enrichedOutput(output.toString(), com.cryptocarver.model.OperationDetail.Classification.PUBLIC)
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Subject", cert.getSubjectX500Principal().getName(), com.cryptocarver.model.OperationDetail.Classification.PUBLIC, false, null),
                        new com.cryptocarver.model.OperationDetail("Result", "Parsed successfully", com.cryptocarver.model.OperationDetail.Classification.PUBLIC, false, null)
                    ))
                    .status("Certificate parsed successfully")
                    .build());
            }

        } catch (Exception e) {
            certParseResultArea.setText("Error parsing certificate: " + e.getMessage());
            certParseResultArea.setVisible(true);
            certParseResultArea.setManaged(true);
            updateStatus("Certificate parse failed");
            showError(e, "Certificate Parse Error", "certInputArea");
        }
    }

    /**
     * Handle Validate Certificate button click
     */
    public void handleValidateCertificate() {
        try {
            if (valCertInput == null || valResultArea == null) {
                // Not initialized
                return;
            }

            String certPem = valCertInput.getText().trim();
            if (certPem.isEmpty()) {
                showError(new UserFacingError("Missing Validation Certificate", "Please paste a certificate to validate.", "Provide X.509 PEM certificate data in the validation input field.", "valCertInput"));
                return;
            }

            String issuerPem = valIssuerInput.getText().trim();

            updateStatus("Validating certificate...");

            // Parse certificates
            List<X509Certificate> chain = null;
            try {
                chain = CertificateGenerator.parseCertificateChain(certPem);
            } catch (Exception e) {
                valResultArea.setText("Error parsing certificate chain: " + e.getMessage());
                updateStatus("Validation failed: Parse error");
                showError(e, "Certificate Chain Parse Error", "valCertInput");
                return;
            }

            if (chain == null || chain.isEmpty()) {
                valResultArea.setText("No certificates found in input.");
                return;
            }

            StringBuilder sb = new StringBuilder();
            boolean isValid = false;
            String statusReason = "";

            if (issuerPem.isEmpty()) {
                // Chain validation or single self-signed
                CertificateGenerator.ChainValidationResult result = CertificateGenerator.validateCertificateChain(chain);
                isValid = result.isValid;
                statusReason = result.message;

                sb.append("=== CHAIN VALIDATION RESULT ===\n");
                sb.append("Status: ").append(result.isValid ? "VALID ✅" : "INVALID ❌").append("\n");
                sb.append("Message: ").append(result.message).append("\n\n");
                sb.append("=== DETAILS ===\n");
                for (String detail : result.details) {
                    sb.append("• ").append(detail).append("\n");
                }
            } else {
                // Legacy validation against explicit issuer
                X509Certificate issuer;
                try {
                    issuer = CertificateGenerator.parseCertificate(issuerPem);
                } catch (Exception e) {
                    valResultArea.setText("Error parsing issuer certificate: " + e.getMessage());
                    updateStatus("Validation failed: Issuer parse error");
                    return;
                }

                CertificateGenerator.CertificateValidationResult result = CertificateGenerator.validateCertificate(chain.get(0), issuer);
                isValid = result.isValid;
                statusReason = result.status;

                sb.append("=== SINGLE CERTIFICATE VALIDATION RESULT ===\n");
                sb.append("Status: ").append(result.isValid ? "VALID ✅" : "INVALID ❌").append("\n");
                sb.append("Reason: ").append(result.status).append("\n");
                sb.append("Message: ").append(result.message).append("\n\n");
                sb.append("=== DETAILS ===\n");
                for (String detail : result.details) {
                    sb.append("• ").append(detail).append("\n");
                }
            }

            String outputText = sb.toString();
            valResultArea.setText(outputText);
            updateStatus(isValid ? "Certificate is valid" : "Certificate is invalid");

            if (mainController != null) {
                mainController.publish(com.cryptocarver.model.OperationResult.forOperation("Validate Certificate")
                    .enrichedOutput(outputText, com.cryptocarver.model.OperationDetail.Classification.PUBLIC)
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", "Status: " + statusReason, com.cryptocarver.model.OperationDetail.Classification.PUBLIC, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", outputText, com.cryptocarver.model.OperationDetail.Classification.PUBLIC, false, null)
                    ))
                    .build());
            }

        } catch (Exception e) {
            valResultArea.setText("Error during validation: " + e.getMessage());
            updateStatus("Validation error");
            e.printStackTrace();
        }
    }

    // ============================================================================
    // TR-31 KEY BLOCK OPERATIONS
    // ============================================================================

    // TR-31 UI Components (to be added to FXML)
    @FXML
    private TextField tr31KbpkExportField;
    @FXML
    private TextField tr31KeyToWrapField;
    @FXML
    private ComboBox<String> tr31UsageCombo;
    @FXML
    private ComboBox<String> tr31AlgorithmCombo;
    @FXML
    private ComboBox<String> tr31ModeCombo;
    @FXML
    private ComboBox<String> tr31VersionCombo;
    @FXML
    private ComboBox<String> tr31ExportabilityCombo;
    @FXML
    private TextField tr31OptionalBlocksField;
    @FXML
    private TextArea tr31ExportResultArea;

    @FXML
    private TextField tr31KbpkImportField;
    @FXML
    private TextArea tr31KeyBlockField;
    @FXML
    private TextField tr31KeyLengthField;
    @FXML
    private TextArea tr31ImportResultArea;

    /**
     * Initialize TR-31 UI components
     */
    public void initializeTR31(TextField tr31KbpkExportField, TextField tr31KeyToWrapField,
            ComboBox<String> tr31VersionCombo, ComboBox<String> tr31UsageCombo,
            ComboBox<String> tr31AlgorithmCombo, ComboBox<String> tr31ModeCombo,
            ComboBox<String> tr31ExportabilityCombo,
            TextField tr31OptionalBlocksField,
            TextArea tr31ExportResultArea, TextField tr31KbpkImportField,
            TextArea tr31KeyBlockField, TextField tr31KeyLengthField,
            TextArea tr31ImportResultArea) {

        this.tr31KbpkExportField = tr31KbpkExportField;
        this.tr31KeyToWrapField = tr31KeyToWrapField;
        this.tr31VersionCombo = tr31VersionCombo;
        this.tr31UsageCombo = tr31UsageCombo;
        this.tr31AlgorithmCombo = tr31AlgorithmCombo;
        this.tr31ModeCombo = tr31ModeCombo;
        this.tr31ExportabilityCombo = tr31ExportabilityCombo;
        this.tr31OptionalBlocksField = tr31OptionalBlocksField;
        this.tr31ExportResultArea = tr31ExportResultArea;

        this.tr31KbpkImportField = tr31KbpkImportField;
        this.tr31KeyBlockField = tr31KeyBlockField;
        this.tr31KeyLengthField = tr31KeyLengthField;
        this.tr31ImportResultArea = tr31ImportResultArea;

        setupTR31Combos();
    }

    /**
     * Setup TR-31 ComboBoxes
     */
    private void setupTR31Combos() {
        if (tr31VersionCombo != null) {
            tr31VersionCombo.getItems().addAll(
                    "A - DES Key Variant Binding (deprecated)",
                    "B - TDES Key Derivation Binding",
                    "C - TDES Key Variant Binding (deprecated)",
                    "D - AES Key Derivation Binding");
            tr31VersionCombo.getSelectionModel().select(1); // Default to B
        }

        if (tr31UsageCombo != null) {
            tr31UsageCombo.getItems().addAll(
                    "B0 - BDK (Base Derivation Key)",
                    "B1 - Initial DUKPT Key",
                    "C0 - CVK (Card Verification Key)",
                    "D0 - Data Encryption (symmetric)",
                    "D1 - Data Encryption (asymmetric)",
                    "E0 - EMV/Chip Card Keys",
                    "I0 - Initialization Vector",
                    "K0 - Key Encryption / Wrapping",
                    "K1 - TR-31 KBPK",
                    "M0 - ISO 16609 MAC (algorithm 1)",
                    "M1 - ISO 9797-1 MAC (algorithm 1)",
                    "M3 - ISO 9797-1 MAC (algorithm 3 - Retail)",
                    "M6 - ISO 9797-1 CMAC (algorithm 5)",
                    "M7 - HMAC",
                    "P0 - PIN Encryption",
                    "S0 - Asymmetric Digital Signature",
                    "V0 - PIN Verification (other)",
                    "V1 - PIN Verification (IBM 3624)",
                    "V2 - PIN Verification (VISA PVV)");
            tr31UsageCombo.getSelectionModel().selectFirst();
        }

        if (tr31AlgorithmCombo != null) {
            tr31AlgorithmCombo.getItems().addAll(
                    "T - Triple DES",
                    "A - AES",
                    "D - DES (single)",
                    "H - HMAC",
                    "R - RSA",
                    "S - DSA",
                    "E - Elliptic Curve");
            tr31AlgorithmCombo.getSelectionModel().selectFirst();
        }

        if (tr31ModeCombo != null) {
            tr31ModeCombo.getItems().addAll(
                    "B - Both encrypt & decrypt",
                    "C - Both generate & verify",
                    "D - Decrypt only",
                    "E - Encrypt only",
                    "G - Generate only",
                    "N - No special restrictions",
                    "S - Signature only",
                    "T - Both sign & key transport",
                    "V - Verify only",
                    "X - Key derivation",
                    "Y - Create cryptographic checksum");
            tr31ModeCombo.getSelectionModel().selectFirst(); // "B - Both"
        }

        if (tr31ExportabilityCombo != null) {
            tr31ExportabilityCombo.getItems().addAll(
                    "E - Exportable",
                    "N - Non-exportable",
                    "S - Sensitive");
            tr31ExportabilityCombo.getSelectionModel().selectFirst(); // "E - Exportable"
        }
    }

    /**
     * Handle TR-31 Export (Wrap Key)
     */
    public void handleTR31Export() {
        try {
            updateStatus(t("module.keys.tr31.status.starting"));
            String kbpk = tr31KbpkExportField.getText().trim().replaceAll("\\s+", "");
            String key = tr31KeyToWrapField.getText().trim().replaceAll("\\s+", "");

            // Validate inputs
            if (kbpk.isEmpty() || key.isEmpty()) {
                showTR31Validation(t("module.keys.tr31.required"), kbpk.isEmpty() ? "tr31KbpkExportField" : "tr31KeyToWrapField", tr31ExportResultArea);
                return;
            }

            if (!kbpk.matches("[0-9A-Fa-f]+")) {
                showTR31Validation(t("module.keys.tr31.kbpkInvalid"), "tr31KbpkExportField", tr31ExportResultArea);
                return;
            }

            if (!key.matches("[0-9A-Fa-f]+")) {
                showTR31Validation(t("module.keys.tr31.keyInvalid"), "tr31KeyToWrapField", tr31ExportResultArea);
                return;
            }

            // Extract parameters
            String versionStr = tr31VersionCombo.getValue();
            char version = versionStr.charAt(0); // 'B' or 'D'

            String usageStr = tr31UsageCombo.getValue();
            String usage = usageStr.substring(0, 2); // Extract "P0", "D0", etc.

            String algoStr = tr31AlgorithmCombo.getValue();
            char algorithm = algoStr.charAt(0); // 'T' or 'A'

            String modeStr = tr31ModeCombo.getValue();
            char mode = modeStr.charAt(0); // 'E', 'D', 'B', etc.

            String exportStr = tr31ExportabilityCombo.getValue();
            char exportability = exportStr.charAt(0); // 'E', 'N', or 'S'

            // Wrap key
            String optionalBlocks = tr31OptionalBlocksField == null ? "" : tr31OptionalBlocksField.getText();
            String keyBlock = TR31Operations.wrapKey(kbpk, key, usage, version, algorithm, mode, exportability, optionalBlocks);

            // Parse header for display
            TR31Operations.TR31Header header = TR31Operations.TR31Header.parse(keyBlock);

            // Build result
            StringBuilder result = new StringBuilder();
            result.append("========================================\n");
            result.append("TR-31 KEY BLOCK EXPORT\n");
            result.append("========================================\n\n");

            result.append("HEADER INFORMATION:\n");
            result.append("------------------\n");
            result.append("Version ID:        ").append(header.versionId).append("\n");
            result.append("Key Block Length:  ").append(header.keyBlockLength).append(" characters\n");
            result.append("Key Usage:         ").append(header.keyUsage);
            result.append(" (").append(TR31Operations.getKeyUsageDescription(header.keyUsage)).append(")\n");
            result.append("Algorithm:         ").append(header.algorithm);
            result.append(" (").append(TR31Operations.getAlgorithmDescription(header.algorithm.charAt(0)))
                    .append(")\n");
            result.append("Mode of Use:       ").append(header.modeOfUse);
            result.append(" (").append(TR31Operations.getModeOfUseDescription(header.modeOfUse.charAt(0)))
                    .append(")\n");
            result.append("Key Version:       ").append(header.keyVersionNumber).append("\n");
            result.append("Exportability:     ").append(header.exportability);
            result.append(" (").append(TR31Operations.getExportabilityDescription(header.exportability.charAt(0)))
                    .append(")\n");
            result.append("Optional Blocks:   ").append(header.numOptionalBlocks).append("\n\n");
            if (!header.optionalBlockDetails.isEmpty()) {
                result.append("OPTIONAL BLOCKS:\n");
                for (TR31Operations.OptionalBlock block : header.optionalBlockDetails) {
                    result.append("  ").append(block.id()).append(" (" ).append(block.dataLength()).append(" bytes): ").append(block.data()).append("\n");
                }
                result.append("\n");
            }

            result.append("KEY BLOCK:\n");
            result.append("------------------\n");
            result.append(keyBlock).append("\n\n");

            result.append("KEY BLOCK (Formatted):\n");
            result.append("------------------\n");
            result.append("Header:       ")
                    .append(keyBlock.substring(0, Math.min(header.build().length(), keyBlock.length()))).append("\n");
            int headerLen = header.build().length();
            int macLen = (header.versionId.equals("A") || header.versionId.equals("C")) ? 8 : 16;
            if (keyBlock.length() > headerLen + macLen) {
                result.append("Encrypted Key: ").append(keyBlock.substring(headerLen, keyBlock.length() - macLen))
                        .append("\n");
                result.append("MAC:          ").append(keyBlock.substring(keyBlock.length() - macLen)).append("\n");
            }

            result.append("\n========================================\n");

            javafx.application.Platform.runLater(() -> {
                tr31ExportResultArea.setVisible(true);
                tr31ExportResultArea.setManaged(true);
                tr31ExportResultArea.setText(result.toString());

                // Force layout update specifically for VBox parent
                if (tr31ExportResultArea.getParent() != null) {
                    tr31ExportResultArea.getParent().requestLayout();
                    // If parent is VBox/HBox/Grid, this helps trigger resize
                    tr31ExportResultArea.getParent().layout();
                }
            });

            updateStatus(t("module.keys.tr31.status.wrapped"));

            // Delegate to ModernMainController history if available
            if (mainController != null) {
                try {
                    java.util.List<com.cryptocarver.model.OperationDetail> details = new java.util.ArrayList<>();
                    details.add(com.cryptocarver.model.OperationDetail.publicDetail("Version", header.versionId));
                    details.add(com.cryptocarver.model.OperationDetail.publicDetail("Usage", usage));
                    details.add(com.cryptocarver.model.OperationDetail.secretDetail("KBPK", kbpk));
                    details.add(com.cryptocarver.model.OperationDetail.secretDetail("Key to Wrap", key));
                    details.add(com.cryptocarver.model.OperationDetail.publicDetail("Key Block", keyBlock));

                    mainController.publish(OperationResult.forOperation("TR-31 Export")
                            .input(DataConverter.hexToBytes(key))
                            .output(keyBlock.getBytes(StandardCharsets.UTF_8))
                            .details(details)
                            .status("TR-31 key wrapped successfully")
                            .build());
                } catch (Exception e) {
                    System.err.println("Failed to add to history: " + e.getMessage());
                }
            } else {
                // Fallback to old system
                if (mainController != null) {
                mainController.publish(com.cryptocarver.model.OperationResult.forOperation("Wrap Key - " + TR31Operations.getKeyUsageDescription(usage))
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", "Version: " + header.versionId + " | Usage: " + usage, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", "KBPK: " + kbpk + "\nKey to Wrap: " + key + "\nKey Block: " + keyBlock, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null)
                    ))
                    .build());
            }
            }

        } catch (Exception e) {
            showTR31Validation(t("module.keys.tr31.operation", e.getMessage()), "tr31KeyToWrapField", tr31ExportResultArea);
            updateStatus(t("module.keys.tr31.status.wrapFailed"));
            logTR31Failure("wrap", e);
        }
    }

    /**
     * Handle TR-31 Import (Unwrap Key)
     */
    public void handleTR31Import() {
        try {
            String kbpk = tr31KbpkImportField.getText().trim().replaceAll("\\s+", "");
            String keyBlock = tr31KeyBlockField.getText().trim().replaceAll("\\s+", "");

            // Validate inputs
            if (kbpk.isEmpty() || keyBlock.isEmpty()) {
                showTR31Validation(t("module.keys.tr31.keyBlockRequired"), kbpk.isEmpty() ? "tr31KbpkImportField" : "tr31KeyBlockField", tr31ImportResultArea);
                return;
            }

            // Parse header
            TR31Operations.TR31Header header = TR31Operations.TR31Header.parse(keyBlock);

            // Unwrap key
            String unwrappedKey = TR31Operations.unwrapKey(kbpk, keyBlock);

            // Build result
            StringBuilder result = new StringBuilder();
            result.append("========================================\n");
            result.append("TR-31 KEY BLOCK IMPORT\n");
            result.append("========================================\n\n");

            result.append("HEADER INFORMATION:\n");
            result.append("------------------\n");
            result.append("Version ID:        ").append(header.versionId).append("\n");
            result.append("Key Block Length:  ").append(header.keyBlockLength).append(" characters\n");
            result.append("Key Usage:         ").append(header.keyUsage);
            result.append(" (").append(TR31Operations.getKeyUsageDescription(header.keyUsage)).append(")\n");
            result.append("Algorithm:         ").append(header.algorithm);
            result.append(" (").append(TR31Operations.getAlgorithmDescription(header.algorithm.charAt(0)))
                    .append(")\n");
            result.append("Mode of Use:       ").append(header.modeOfUse);
            result.append(" (").append(TR31Operations.getModeOfUseDescription(header.modeOfUse.charAt(0)))
                    .append(")\n");
            result.append("Key Version:       ").append(header.keyVersionNumber).append("\n");
            result.append("Exportability:     ").append(header.exportability).append("\n");
            result.append("Optional Blocks:   ").append(header.numOptionalBlocks).append("\n");
            result.append("\n");

            result.append("UNWRAPPED KEY:\n");
            result.append("------------------\n");
            result.append(unwrappedKey.toUpperCase()).append("\n");
            result.append("\nKey Length: ").append(unwrappedKey.length() / 2).append(" bytes (");
            result.append(unwrappedKey.length()).append(" hex characters)\n");

            result.append("\n========================================\n");

            tr31ImportResultArea.setText(result.toString());
            tr31ImportResultArea.setVisible(true);
            tr31ImportResultArea.setManaged(true);
            updateStatus(t("module.keys.tr31.status.unwrapped"));

            if (mainController != null) {
                mainController.publish(com.cryptocarver.model.OperationResult.forOperation("Unwrap Key - " + TR31Operations.getKeyUsageDescription(header.keyUsage))
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", "Version " + header.versionId, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", "Key Length: " + (unwrappedKey.length() / 2) + " bytes", com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null)
                    ))
                    .build());
            }

        } catch (Exception e) {
            showTR31Validation(t("module.keys.tr31.operation", e.getMessage()), "tr31KeyBlockField", tr31ImportResultArea);
            updateStatus(t("module.keys.tr31.status.unwrapFailed"));
            logTR31Failure("unwrap", e);
        }
    }

    /**
     * Handle Parse TR-31 Header (without unwrapping)
     */
    public void handleTR31ParseHeader() {
        try {
            String keyBlock = tr31KeyBlockField.getText().trim().replaceAll("\\s+", "");

            if (keyBlock.isEmpty()) {
                showTR31Validation(t("module.keys.tr31.keyBlockRequired"), "tr31KeyBlockField", tr31ImportResultArea);
                return;
            }

            // Parse header
            TR31Operations.TR31Header header = TR31Operations.TR31Header.parse(keyBlock);

            // Build result
            StringBuilder result = new StringBuilder();
            result.append("========================================\n");
            result.append("TR-31 HEADER PARSE\n");
            result.append("========================================\n\n");

            result.append("HEADER FIELDS:\n");
            result.append("------------------\n");
            result.append("Version ID:        ").append(header.versionId).append("\n");
            result.append("Key Block Length:  ").append(header.keyBlockLength).append(" characters\n");
            result.append("Key Usage:         ").append(header.keyUsage);
            result.append(" (").append(TR31Operations.getKeyUsageDescription(header.keyUsage)).append(")\n");
            result.append("Algorithm:         ").append(header.algorithm);
            result.append(" (").append(TR31Operations.getAlgorithmDescription(header.algorithm.charAt(0)))
                    .append(")\n");
            result.append("Mode of Use:       ").append(header.modeOfUse);
            result.append(" (").append(TR31Operations.getModeOfUseDescription(header.modeOfUse.charAt(0)))
                    .append(")\n");
            result.append("Key Version:       ").append(header.keyVersionNumber).append("\n");
            result.append("Exportability:     ").append(header.exportability).append(" (")
                    .append(TR31Operations.getExportabilityDescription(header.exportability.charAt(0))).append(")\n");
            result.append("Optional Blocks:   ").append(header.numOptionalBlocks).append("\n");
            result.append("Reserved:          ").append(header.reserved).append("\n\n");

            result.append("INPUT LENGTH:       ").append(keyBlock.length()).append(" characters\n");

            if (!header.optionalBlockDetails.isEmpty()) {
                result.append("OPTIONAL BLOCKS:\n");
                result.append("------------------\n");
                for (TR31Operations.OptionalBlock block : header.optionalBlockDetails) {
                    result.append(block.id()).append(": ").append(block.dataLength()).append(" bytes\n");
                    result.append("  Data: ").append(block.data()).append("\n");
                }
                result.append("\n");
            }

            result.append("DIAGNOSTICS:\n");
            result.append("------------------\n");
            if (header.getDiagnostics().isEmpty()) result.append("No structural warnings detected.\n\n");
            else {
                for (String diagnostic : header.getDiagnostics()) result.append(diagnostic).append("\n");
                result.append("\n");
            }

            result.append("RAW HEADER:\n");
            result.append("------------------\n");
            result.append(header.build()).append("\n");

            result.append("\n========================================\n");

            tr31ImportResultArea.setText(result.toString());
            tr31ImportResultArea.setVisible(true);
            tr31ImportResultArea.setManaged(true);
            updateStatus(t("module.keys.tr31.status.headerParsed"));

            if (mainController != null) {
                mainController.publish(com.cryptocarver.model.OperationResult.forOperation("TR-31 Header Parse")
                        .enrichedOutput(result.toString(), com.cryptocarver.model.OperationDetail.Classification.PUBLIC)
                        .status("TR-31 header parsed successfully")
                        .build());
            }

        } catch (Exception e) {
            showTR31Validation(t("module.keys.tr31.operation", e.getMessage()), "tr31KeyBlockField", tr31ImportResultArea);
            updateStatus(t("module.keys.tr31.status.parseFailed"));
            logTR31Failure("header parse", e);
        }
    }

    // ============================================================================
    // RSA KEY EXCHANGE — export/import of a symmetric key under RSA (Raw OAEP,
    // JWE Compact or CMS EnvelopedData), the RSA sibling of TR-31 above. See
    // RsaKeyWrapOperations for the underlying wrap/unwrap primitives and
    // CryptoEnvelope/CryptoEnvelopeCodec for the optional crypto-agility header.
    // ============================================================================

    @FXML private TextArea rsaKexRecipientPemArea;
    @FXML private TextField rsaKexKeyToWrapField;
    @FXML private ComboBox<String> rsaKexExportProfileCombo;
    @FXML private CheckBox rsaKexIncludeEnvelopeCheck;
    @FXML private javafx.scene.layout.HBox rsaKexEnvelopeFieldsBox;
    @FXML private TextField rsaKexKidField;
    @FXML private TextField rsaKexKeyVersionField;
    @FXML private TextArea rsaKexExportResultArea;

    @FXML private TextArea rsaKexPrivateKeyArea;
    @FXML private TextArea rsaKexWrappedDataArea;
    @FXML private ComboBox<String> rsaKexImportProfileCombo;
    @FXML private TextArea rsaKexImportResultArea;

    private void initializeRsaKexControls() {
        if (rsaKexExportProfileCombo != null) {
            rsaKexExportProfileCombo.getItems().setAll("Raw OAEP", "JWE Compact", "CMS EnvelopedData");
            rsaKexExportProfileCombo.setValue("Raw OAEP");
        }
        if (rsaKexImportProfileCombo != null) {
            rsaKexImportProfileCombo.getItems().setAll("Raw OAEP", "JWE Compact", "CMS EnvelopedData");
            rsaKexImportProfileCombo.setValue("Raw OAEP");
        }
    }

    @FXML
    public void handleRsaKexEnvelopeToggle() {
        boolean selected = rsaKexIncludeEnvelopeCheck != null && rsaKexIncludeEnvelopeCheck.isSelected();
        if (rsaKexEnvelopeFieldsBox != null) {
            rsaKexEnvelopeFieldsBox.setVisible(selected);
            rsaKexEnvelopeFieldsBox.setManaged(selected);
        }
    }

    private static RsaKeyWrapOperations.WrapProfile rsaKexProfileFromCombo(String value) {
        if (value == null) return RsaKeyWrapOperations.WrapProfile.RAW_OAEP;
        return switch (value) {
            case "JWE Compact" -> RsaKeyWrapOperations.WrapProfile.JWE_COMPACT;
            case "CMS EnvelopedData" -> RsaKeyWrapOperations.WrapProfile.CMS_ENVELOPED;
            default -> RsaKeyWrapOperations.WrapProfile.RAW_OAEP;
        };
    }

    /**
     * Handle RSA Key Exchange Export (Wrap Key)
     */
    @FXML
    public void handleRsaKexExport() {
        try {
            String pem = rsaKexRecipientPemArea.getText().trim();
            String keyHex = rsaKexKeyToWrapField.getText().trim().replaceAll("\\s+", "");

            if (pem.isEmpty() || keyHex.isEmpty()) {
                showRsaKexValidation(t("module.keys.rsaKex.required"),
                        pem.isEmpty() ? "rsaKexRecipientPemArea" : "rsaKexKeyToWrapField", rsaKexExportResultArea::setText);
                return;
            }
            if (!keyHex.matches("[0-9A-Fa-f]+")) {
                showRsaKexValidation(t("module.keys.rsaKex.keyInvalid"), "rsaKexKeyToWrapField", rsaKexExportResultArea::setText);
                return;
            }

            byte[] keyToWrap = DataConverter.hexToBytes(keyHex);

            PublicKey publicKey;
            X509Certificate certificate = null;
            if (pem.contains("BEGIN CERTIFICATE")) {
                java.security.cert.CertificateFactory factory = java.security.cert.CertificateFactory.getInstance("X.509");
                certificate = (X509Certificate) factory.generateCertificate(
                        new java.io.ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
                publicKey = certificate.getPublicKey();
            } else if (pem.contains("BEGIN PUBLIC KEY")) {
                publicKey = AsymmetricKeyOperations.importPublicKeyPEMAuto(pem);
            } else {
                showRsaKexValidation(t("module.keys.rsaKex.pemUnrecognized"), "rsaKexRecipientPemArea", rsaKexExportResultArea::setText);
                return;
            }

            RsaKeyWrapOperations.WrapProfile profile = rsaKexProfileFromCombo(rsaKexExportProfileCombo.getValue());
            RsaKeyWrapOperations.WrapResult wrapResult = RsaKeyWrapOperations.wrap(keyToWrap, publicKey, certificate, profile);

            boolean asEnvelope = rsaKexIncludeEnvelopeCheck != null && rsaKexIncludeEnvelopeCheck.isSelected();
            String outputText;
            if (asEnvelope) {
                CryptoEnvelope.Builder builder = CryptoEnvelope.forAlgorithm(wrapResult.getAlgorithm())
                        .ciphertext(wrapResult.getWrapped())
                        .kcv(wrapResult.getKcvHex())
                        .extension("profile", profile.name());
                String kid = rsaKexKidField == null ? "" : rsaKexKidField.getText().trim();
                if (!kid.isEmpty()) builder.kid(kid);
                String keyVersionText = rsaKexKeyVersionField == null ? "" : rsaKexKeyVersionField.getText().trim();
                if (!keyVersionText.isEmpty()) {
                    try {
                        builder.keyVersion(Integer.parseInt(keyVersionText));
                    } catch (NumberFormatException e) {
                        showRsaKexValidation(t("module.keys.rsaKex.keyVersionInvalid"), "rsaKexKeyVersionField", rsaKexExportResultArea::setText);
                        return;
                    }
                }
                outputText = CryptoEnvelopeCodec.serializeCompact(builder.build());
            } else if (profile == RsaKeyWrapOperations.WrapProfile.JWE_COMPACT) {
                outputText = new String(wrapResult.getWrapped(), StandardCharsets.US_ASCII);
            } else {
                outputText = java.util.Base64.getEncoder().encodeToString(wrapResult.getWrapped());
            }

            StringBuilder result = new StringBuilder();
            result.append("========================================\n");
            result.append("RSA KEY EXCHANGE — EXPORT\n");
            result.append("========================================\n\n");
            result.append("Profile:        ").append(profile).append("\n");
            result.append("Algorithm:      ").append(wrapResult.getAlgorithm()).append("\n");
            if (wrapResult.getKcvHex() != null) {
                result.append("Key KCV:        ").append(wrapResult.getKcvHex()).append("\n");
            }
            result.append("Envelope:       ").append(asEnvelope ? "yes (compact)" : "no").append("\n\n");
            result.append("OUTPUT:\n");
            result.append("------------------\n");
            result.append(outputText).append("\n");
            result.append("\n========================================\n");

            rsaKexExportResultArea.setText(result.toString());
            updateStatus(t("module.keys.rsaKex.status.wrapped"));

            if (mainController != null) {
                List<com.cryptocarver.model.OperationDetail> details = new ArrayList<>();
                details.add(com.cryptocarver.model.OperationDetail.publicDetail("Profile", profile.name()));
                details.add(com.cryptocarver.model.OperationDetail.publicDetail("Algorithm", wrapResult.getAlgorithm()));
                details.add(com.cryptocarver.model.OperationDetail.secretDetail("Key to Wrap", keyHex));
                details.add(com.cryptocarver.model.OperationDetail.publicDetail("Output", outputText));
                mainController.publish(OperationResult.forOperation("RSA Key Exchange Export")
                        .input(keyToWrap)
                        .output(outputText.getBytes(StandardCharsets.UTF_8))
                        .details(details)
                        .status("RSA key wrapped successfully (" + profile + ")")
                        .build());
            }
        } catch (Exception e) {
            showRsaKexValidation(t("module.keys.rsaKex.operation", e.getMessage()), "rsaKexKeyToWrapField", rsaKexExportResultArea::setText);
            updateStatus(t("module.keys.rsaKex.status.wrapFailed"));
            logRsaKexFailure("wrap", e);
        }
    }

    /**
     * Handle RSA Key Exchange Import (Unwrap Key)
     */
    @FXML
    public void handleRsaKexImport() {
        try {
            String privatePem = rsaKexPrivateKeyArea.getText().trim();
            String wrappedText = rsaKexWrappedDataArea.getText().trim();

            if (privatePem.isEmpty() || wrappedText.isEmpty()) {
                showRsaKexValidation(t("module.keys.rsaKex.importRequired"),
                        privatePem.isEmpty() ? "rsaKexPrivateKeyArea" : "rsaKexWrappedDataArea", rsaKexImportResultArea::setText);
                return;
            }

            PrivateKey privateKey = AsymmetricKeyOperations.importPrivateKeyPEMAuto(privatePem);

            byte[] wrapped;
            RsaKeyWrapOperations.WrapProfile profile;
            CryptoEnvelope envelope = null;
            if (CryptoEnvelopeCodec.looksLikeEnvelope(wrappedText)) {
                envelope = CryptoEnvelopeCodec.deserializeAuto(wrappedText);
                wrapped = java.util.Base64.getDecoder().decode(envelope.getCiphertextB64());
                String profileExt = envelope.getExtensions().get("profile");
                profile = profileExt != null
                        ? RsaKeyWrapOperations.WrapProfile.valueOf(profileExt)
                        : rsaKexProfileFromCombo(rsaKexImportProfileCombo.getValue());
            } else {
                profile = rsaKexProfileFromCombo(rsaKexImportProfileCombo.getValue());
                wrapped = profile == RsaKeyWrapOperations.WrapProfile.JWE_COMPACT
                        ? wrappedText.getBytes(StandardCharsets.US_ASCII)
                        : java.util.Base64.getDecoder().decode(wrappedText);
            }

            byte[] recovered = RsaKeyWrapOperations.unwrap(wrapped, privateKey, profile);
            String recoveredHex = DataConverter.bytesToHex(recovered);
            String recoveredKcv = null;
            if (recovered.length == 16 || recovered.length == 24 || recovered.length == 32) {
                try {
                    recoveredKcv = DataConverter.bytesToHex(KeyOperations.calculateKCV_AES(recovered));
                } catch (Exception ignored) {
                    // Best-effort only — KCV is a convenience cross-check, not required.
                }
            }

            StringBuilder result = new StringBuilder();
            result.append("========================================\n");
            result.append("RSA KEY EXCHANGE — IMPORT\n");
            result.append("========================================\n\n");
            result.append("Profile:        ").append(profile).append("\n");
            if (envelope != null) {
                result.append("Envelope:       yes\n");
                result.append("Algorithm:      ").append(envelope.getAlg()).append("\n");
                if (envelope.getKid() != null) result.append("Key ID:         ").append(envelope.getKid()).append("\n");
                if (envelope.getKeyVersion() != null) result.append("Key Version:    ").append(envelope.getKeyVersion()).append("\n");
                if (envelope.getKcv() != null) {
                    boolean matches = recoveredKcv != null && recoveredKcv.equalsIgnoreCase(envelope.getKcv());
                    result.append("Envelope KCV:   ").append(envelope.getKcv())
                            .append(matches ? "  (matches recovered key)" : "  (!) does not match the recovered key's KCV")
                            .append("\n");
                }
            } else {
                result.append("Envelope:       no\n");
            }
            result.append("\nUNWRAPPED KEY:\n");
            result.append("------------------\n");
            result.append(recoveredHex.toUpperCase()).append("\n");
            if (recoveredKcv != null) result.append("Recovered KCV:  ").append(recoveredKcv).append("\n");
            result.append("Key Length:     ").append(recovered.length).append(" bytes\n");
            result.append("\n========================================\n");

            rsaKexImportResultArea.setText(result.toString());
            updateStatus(t("module.keys.rsaKex.status.unwrapped"));

            if (mainController != null) {
                List<com.cryptocarver.model.OperationDetail> details = new ArrayList<>();
                details.add(com.cryptocarver.model.OperationDetail.publicDetail("Profile", profile.name()));
                details.add(com.cryptocarver.model.OperationDetail.secretDetail("Recovered Key (hex)", recoveredHex));
                mainController.publish(OperationResult.forOperation("RSA Key Exchange Import")
                        .input(wrapped)
                        .output(recovered, com.cryptocarver.model.OperationDetail.Classification.SECRET)
                        .details(details)
                        .status("RSA key unwrapped successfully (" + profile + ")")
                        .build());
            }
        } catch (Exception e) {
            showRsaKexValidation(t("module.keys.rsaKex.operation", e.getMessage()), "rsaKexWrappedDataArea", rsaKexImportResultArea::setText);
            updateStatus(t("module.keys.rsaKex.status.unwrapFailed"));
            logRsaKexFailure("unwrap", e);
        }
    }

    @FXML
    public void handleRsaKexClear() {
        clearRsaKexFields();
        if (mainController != null) mainController.updateStatus(t("module.keys.rsaKex.clearStatus"));
    }

    @FXML
    public void handleRsaKexReset() {
        clearRsaKexFields();
        if (rsaKexExportProfileCombo != null) rsaKexExportProfileCombo.setValue("Raw OAEP");
        if (rsaKexImportProfileCombo != null) rsaKexImportProfileCombo.setValue("Raw OAEP");
        if (rsaKexIncludeEnvelopeCheck != null) rsaKexIncludeEnvelopeCheck.setSelected(false);
        if (rsaKexEnvelopeFieldsBox != null) {
            rsaKexEnvelopeFieldsBox.setVisible(false);
            rsaKexEnvelopeFieldsBox.setManaged(false);
        }
        if (mainController != null) mainController.updateStatus(t("module.keys.rsaKex.resetStatus"));
    }

    private void clearRsaKexFields() {
        if (rsaKexRecipientPemArea != null) rsaKexRecipientPemArea.clear();
        if (rsaKexKeyToWrapField != null) rsaKexKeyToWrapField.clear();
        if (rsaKexKidField != null) rsaKexKidField.clear();
        if (rsaKexKeyVersionField != null) rsaKexKeyVersionField.clear();
        if (rsaKexExportResultArea != null) rsaKexExportResultArea.clear();
        if (rsaKexPrivateKeyArea != null) rsaKexPrivateKeyArea.clear();
        if (rsaKexWrappedDataArea != null) rsaKexWrappedDataArea.clear();
        if (rsaKexImportResultArea != null) rsaKexImportResultArea.clear();
    }

    private void showRsaKexValidation(String message, String fieldKey, Consumer<String> feedbackTarget) {
        String safeMessage = InlineErrorPresenter.redactSecrets(message);
        UserFacingError error = new UserFacingError(t("module.keys.rsaKex.errorTitle"), safeMessage, safeMessage, fieldKey);
        if (mainController != null) {
            mainController.showError(error);
        } else if (feedbackTarget != null) {
            feedbackTarget.accept(safeMessage);
        }
    }

    private void logRsaKexFailure(String operation, Exception error) {
        StringWriter trace = new StringWriter();
        error.printStackTrace(new PrintWriter(trace));
        System.err.print(InlineErrorPresenter.redactSecrets("RSA Key Exchange " + operation + " failed:\n" + trace));
    }

    // ============================================================================
    // TR-34 KEY DISTRIBUTION — laboratory RSA remote key distribution, inspired by
    // ANSI X9 TR-34 (sign then envelope with CMS). Supports both the one-pass
    // profile and an optional two-pass, binding-nonce profile for replay
    // protection (fill the Binding Nonce / Challenge Nonce fields to engage it;
    // leave them blank for plain one-pass, unchanged from before). See
    // TR34Operations for the "this is not a byte-for-byte TR-34 implementation"
    // disclosure; the same caveat is shown to the user directly in the pane
    // (keys.fxml).
    // ============================================================================

    @FXML private TextArea tr34SenderPrivateKeyArea;
    @FXML private TextArea tr34SenderCertArea;
    @FXML private TextArea tr34ReceiverCertArea;
    @FXML private TextField tr34KeyToDistributeField;
    @FXML private TextField tr34KeyIdField;
    @FXML private TextField tr34BindingNonceField;
    @FXML private CheckBox tr34IncludeEnvelopeCheck;
    @FXML private TextArea tr34DistributeResultArea;

    @FXML private TextArea tr34ReceiverPrivateKeyArea;
    @FXML private TextArea tr34ExpectedSenderCertArea;
    @FXML private TextArea tr34DistributedDataArea;
    @FXML private TextField tr34ChallengeNonceField;
    @FXML private TextArea tr34ReceiveResultArea;

    private static X509Certificate parseCertificatePem(String pem) throws Exception {
        java.security.cert.CertificateFactory factory = java.security.cert.CertificateFactory.getInstance("X.509");
        return (X509Certificate) factory.generateCertificate(
                new java.io.ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
    }

    /**
     * Handle TR-34 Distribute (sender side: sign then envelope the key)
     */
    @FXML
    public void handleTr34Distribute() {
        try {
            String privatePem = tr34SenderPrivateKeyArea.getText().trim();
            String senderCertPem = tr34SenderCertArea.getText().trim();
            String receiverCertPem = tr34ReceiverCertArea.getText().trim();
            String keyHex = tr34KeyToDistributeField.getText().trim().replaceAll("\\s+", "");

            if (privatePem.isEmpty() || senderCertPem.isEmpty() || receiverCertPem.isEmpty() || keyHex.isEmpty()) {
                showTr34Validation(t("module.keys.tr34.required"),
                        privatePem.isEmpty() ? "tr34SenderPrivateKeyArea"
                                : senderCertPem.isEmpty() ? "tr34SenderCertArea"
                                : receiverCertPem.isEmpty() ? "tr34ReceiverCertArea" : "tr34KeyToDistributeField",
                        tr34DistributeResultArea::setText);
                return;
            }
            if (!keyHex.matches("[0-9A-Fa-f]+")) {
                showTr34Validation(t("module.keys.tr34.keyInvalid"), "tr34KeyToDistributeField", tr34DistributeResultArea::setText);
                return;
            }

            byte[] keyToDistribute = DataConverter.hexToBytes(keyHex);
            PrivateKey senderPrivateKey = AsymmetricKeyOperations.importPrivateKeyPEMAuto(privatePem);
            X509Certificate senderCert = parseCertificatePem(senderCertPem);
            X509Certificate receiverCert = parseCertificatePem(receiverCertPem);
            String keyId = tr34KeyIdField == null ? "" : tr34KeyIdField.getText().trim();
            String bindingNonceHex = tr34BindingNonceField == null ? ""
                    : tr34BindingNonceField.getText().trim().replaceAll("\\s+", "");
            if (!bindingNonceHex.isEmpty() && !bindingNonceHex.matches("[0-9A-Fa-f]+")) {
                showTr34Validation(t("module.keys.tr34.keyInvalid"), "tr34BindingNonceField", tr34DistributeResultArea::setText);
                return;
            }
            boolean twoPass = !bindingNonceHex.isEmpty();

            byte[] distributed = twoPass
                    ? TR34Operations.distributeKeyTwoPass(keyToDistribute, senderCert, senderPrivateKey,
                            receiverCert, DataConverter.hexToBytes(bindingNonceHex), keyId.isEmpty() ? null : keyId)
                    : TR34Operations.distributeKey(keyToDistribute, senderCert, senderPrivateKey,
                            receiverCert, keyId.isEmpty() ? null : keyId);

            boolean asEnvelope = tr34IncludeEnvelopeCheck != null && tr34IncludeEnvelopeCheck.isSelected();
            String outputText;
            if (asEnvelope) {
                CryptoEnvelope.Builder builder = CryptoEnvelope.forAlgorithm("TR34-CMS")
                        .ciphertext(distributed)
                        .kcv(tr34KcvIfEligible(keyToDistribute));
                if (!keyId.isEmpty()) builder.kid(keyId);
                outputText = CryptoEnvelopeCodec.serializeCompact(builder.build());
            } else {
                outputText = java.util.Base64.getEncoder().encodeToString(distributed);
            }

            StringBuilder result = new StringBuilder();
            result.append("========================================\n");
            result.append("TR-34 KEY DISTRIBUTION — DISTRIBUTE\n");
            result.append("========================================\n\n");
            if (!keyId.isEmpty()) result.append("Key ID:         ").append(keyId).append("\n");
            result.append("Profile:        ").append(twoPass ? "two-pass (bound to nonce " + bindingNonceHex.toUpperCase() + ")" : "one-pass").append("\n");
            result.append("Envelope:       ").append(asEnvelope ? "yes (compact)" : "no").append("\n\n");
            result.append("OUTPUT:\n");
            result.append("------------------\n");
            result.append(outputText).append("\n");
            result.append("\n========================================\n");

            tr34DistributeResultArea.setText(result.toString());
            updateStatus(t("module.keys.tr34.status.distributed"));

            if (mainController != null) {
                List<com.cryptocarver.model.OperationDetail> details = new ArrayList<>();
                if (!keyId.isEmpty()) details.add(com.cryptocarver.model.OperationDetail.publicDetail("Key ID", keyId));
                details.add(com.cryptocarver.model.OperationDetail.publicDetail("Profile", twoPass ? "Two-pass" : "One-pass"));
                if (twoPass) details.add(com.cryptocarver.model.OperationDetail.publicDetail("Binding Nonce", bindingNonceHex.toUpperCase()));
                details.add(com.cryptocarver.model.OperationDetail.secretDetail("Key to Distribute", keyHex));
                details.add(com.cryptocarver.model.OperationDetail.publicDetail("Output", outputText));
                mainController.publish(OperationResult.forOperation("TR-34 Key Distribution")
                        .input(keyToDistribute)
                        .output(outputText.getBytes(StandardCharsets.UTF_8))
                        .details(details)
                        .status("TR-34 key distributed successfully")
                        .build());
            }
        } catch (Exception e) {
            showTr34Validation(t("module.keys.tr34.operation", e.getMessage()), "tr34KeyToDistributeField", tr34DistributeResultArea::setText);
            updateStatus(t("module.keys.tr34.status.distributeFailed"));
            logTr34Failure("distribute", e);
        }
    }

    /**
     * Handle TR-34 Receive (receiver side: decrypt then verify)
     */
    @FXML
    public void handleTr34Receive() {
        try {
            String privatePem = tr34ReceiverPrivateKeyArea.getText().trim();
            String expectedSenderCertPem = tr34ExpectedSenderCertArea.getText().trim();
            String distributedText = tr34DistributedDataArea.getText().trim();

            if (privatePem.isEmpty() || expectedSenderCertPem.isEmpty() || distributedText.isEmpty()) {
                showTr34Validation(t("module.keys.tr34.receiveRequired"),
                        privatePem.isEmpty() ? "tr34ReceiverPrivateKeyArea"
                                : expectedSenderCertPem.isEmpty() ? "tr34ExpectedSenderCertArea" : "tr34DistributedDataArea",
                        tr34ReceiveResultArea::setText);
                return;
            }

            PrivateKey receiverPrivateKey = AsymmetricKeyOperations.importPrivateKeyPEMAuto(privatePem);
            X509Certificate expectedSenderCert = parseCertificatePem(expectedSenderCertPem);

            byte[] distributed;
            CryptoEnvelope envelope = null;
            if (CryptoEnvelopeCodec.looksLikeEnvelope(distributedText)) {
                envelope = CryptoEnvelopeCodec.deserializeAuto(distributedText);
                distributed = java.util.Base64.getDecoder().decode(envelope.getCiphertextB64());
            } else {
                distributed = java.util.Base64.getDecoder().decode(distributedText);
            }

            String challengeNonceHex = tr34ChallengeNonceField == null ? ""
                    : tr34ChallengeNonceField.getText().trim().replaceAll("\\s+", "");
            if (!challengeNonceHex.isEmpty() && !challengeNonceHex.matches("[0-9A-Fa-f]+")) {
                showTr34Validation(t("module.keys.tr34.keyInvalid"), "tr34ChallengeNonceField", tr34ReceiveResultArea::setText);
                return;
            }
            boolean twoPass = !challengeNonceHex.isEmpty();

            TR34Operations.ReceivedKey received = twoPass
                    ? TR34Operations.receiveKeyTwoPass(distributed, receiverPrivateKey, expectedSenderCert,
                            DataConverter.hexToBytes(challengeNonceHex))
                    : TR34Operations.receiveKey(distributed, receiverPrivateKey, expectedSenderCert);
            String recoveredHex = DataConverter.bytesToHex(received.getKey());

            StringBuilder result = new StringBuilder();
            result.append("========================================\n");
            result.append("TR-34 KEY DISTRIBUTION — RECEIVE\n");
            result.append("========================================\n\n");
            result.append("Signature Verified: ").append(received.isSignatureVerified() ? "YES" : "NO — do not trust this key").append("\n");
            if (twoPass) {
                result.append("Nonce Verified:      ").append(received.isNonceVerified()
                        ? "YES" : "NO — possible replay of an old distribution, or wrong challenge").append("\n");
            }
            String keyId = received.getKeyId();
            if (keyId != null) result.append("Key ID (authenticated): ").append(keyId).append("\n");
            if (envelope != null) {
                result.append("Envelope KCV:        ").append(envelope.getKcv() == null ? "-" : envelope.getKcv()).append("\n");
            }
            result.append("\nRECOVERED KEY:\n");
            result.append("------------------\n");
            result.append(recoveredHex.toUpperCase()).append("\n");
            result.append("Key Length:     ").append(received.getKey().length).append(" bytes\n");
            result.append("\n========================================\n");

            tr34ReceiveResultArea.setText(result.toString());
            boolean trustworthy = received.isSignatureVerified() && (!twoPass || received.isNonceVerified());
            if (!received.isSignatureVerified()) {
                updateStatus(t("module.keys.tr34.status.receivedUnverified"));
            } else if (twoPass && !received.isNonceVerified()) {
                updateStatus(t("module.keys.tr34.status.receivedNonceMismatch"));
            } else {
                updateStatus(t("module.keys.tr34.status.received"));
            }

            if (mainController != null) {
                List<com.cryptocarver.model.OperationDetail> details = new ArrayList<>();
                details.add(com.cryptocarver.model.OperationDetail.publicDetail("Signature Verified", String.valueOf(received.isSignatureVerified())));
                if (twoPass) details.add(com.cryptocarver.model.OperationDetail.publicDetail("Nonce Verified", String.valueOf(received.isNonceVerified())));
                details.add(com.cryptocarver.model.OperationDetail.secretDetail("Recovered Key (hex)", recoveredHex));
                mainController.publish(OperationResult.forOperation("TR-34 Key Reception")
                        .input(distributed)
                        .output(received.getKey(), com.cryptocarver.model.OperationDetail.Classification.SECRET)
                        .details(details)
                        .status(trustworthy ? "TR-34 key received and verified" : "TR-34 key received but NOT verified")
                        .build());
            }
        } catch (Exception e) {
            showTr34Validation(t("module.keys.tr34.operation", e.getMessage()), "tr34DistributedDataArea", tr34ReceiveResultArea::setText);
            updateStatus(t("module.keys.tr34.status.receiveFailed"));
            logTr34Failure("receive", e);
        }
    }

    /** Fills the Receive tab's challenge nonce field with a fresh random value (two-pass, step 1). */
    @FXML
    public void handleTr34GenerateChallenge() {
        if (tr34ChallengeNonceField == null) return;
        byte[] nonce = TR34Operations.generateChallengeNonce();
        tr34ChallengeNonceField.setText(DataConverter.bytesToHex(nonce).toUpperCase());
        updateStatus(t("module.keys.tr34.status.challengeGenerated"));
    }

    @FXML
    public void handleTr34Clear() {
        clearTr34Fields();
        if (mainController != null) mainController.updateStatus(t("module.keys.tr34.clearStatus"));
    }

    @FXML
    public void handleTr34Reset() {
        clearTr34Fields();
        if (tr34IncludeEnvelopeCheck != null) tr34IncludeEnvelopeCheck.setSelected(false);
        if (mainController != null) mainController.updateStatus(t("module.keys.tr34.resetStatus"));
    }

    private void clearTr34Fields() {
        if (tr34SenderPrivateKeyArea != null) tr34SenderPrivateKeyArea.clear();
        if (tr34SenderCertArea != null) tr34SenderCertArea.clear();
        if (tr34ReceiverCertArea != null) tr34ReceiverCertArea.clear();
        if (tr34KeyToDistributeField != null) tr34KeyToDistributeField.clear();
        if (tr34KeyIdField != null) tr34KeyIdField.clear();
        if (tr34BindingNonceField != null) tr34BindingNonceField.clear();
        if (tr34DistributeResultArea != null) tr34DistributeResultArea.clear();
        if (tr34ReceiverPrivateKeyArea != null) tr34ReceiverPrivateKeyArea.clear();
        if (tr34ExpectedSenderCertArea != null) tr34ExpectedSenderCertArea.clear();
        if (tr34DistributedDataArea != null) tr34DistributedDataArea.clear();
        if (tr34ChallengeNonceField != null) tr34ChallengeNonceField.clear();
        if (tr34ReceiveResultArea != null) tr34ReceiveResultArea.clear();
    }

    /** KCV is only defined here for AES-length key material (16/24/32 bytes); anything else is best-effort skipped. */
    private static String tr34KcvIfEligible(byte[] keyMaterial) {
        if (keyMaterial.length != 16 && keyMaterial.length != 24 && keyMaterial.length != 32) {
            return null;
        }
        try {
            return DataConverter.bytesToHex(KeyOperations.calculateKCV_AES(keyMaterial));
        } catch (Exception e) {
            return null;
        }
    }

    private void showTr34Validation(String message, String fieldKey, Consumer<String> feedbackTarget) {
        String safeMessage = InlineErrorPresenter.redactSecrets(message);
        UserFacingError error = new UserFacingError(t("module.keys.tr34.errorTitle"), safeMessage, safeMessage, fieldKey);
        if (mainController != null) {
            mainController.showError(error);
        } else if (feedbackTarget != null) {
            feedbackTarget.accept(safeMessage);
        }
    }

    private void logTr34Failure(String operation, Exception error) {
        StringWriter trace = new StringWriter();
        error.printStackTrace(new PrintWriter(trace));
        System.err.print(InlineErrorPresenter.redactSecrets("TR-34 " + operation + " failed:\n" + trace));
    }

    /**
     * Initialize Key Derivation Functions
     */
    public void initializeKDF(ComboBox<String> algorithmCombo,
            ComboBox<String> inputFormatCombo,
            ComboBox<String> saltFormatCombo,
            ComboBox<String> infoFormatCombo,
            TextField inputField,
            TextField saltField,
            TextField infoField,
            TextField iterationsField,
            TextField outputLengthField,
            TextArea resultArea) {
        this.kdfAlgorithmCombo = algorithmCombo;
        this.kdfInputFormatCombo = inputFormatCombo;
        this.kdfSaltFormatCombo = saltFormatCombo;
        this.kdfInfoFormatCombo = infoFormatCombo;
        this.kdfInputField = inputField;
        this.kdfSaltField = saltField;
        this.kdfInfoField = infoField;
        this.kdfIterationsField = iterationsField;
        this.kdfOutputLengthField = outputLengthField;
        this.kdfResultArea = resultArea;

        // Populate algorithms (with SHA variants)
        kdfAlgorithmCombo.getItems().addAll(
                "HKDF-SHA1",
                "HKDF-SHA256",
                "HKDF-SHA512",
                "NIST-800-108-SHA256",
                "X9.63-SHA256",
                "PBKDF2-SHA1",
                "PBKDF2-SHA256",
                "PBKDF2-SHA512",
                "SCrypt",
                "Argon2id");
        kdfAlgorithmCombo.setValue("HKDF-SHA256");

        // Populate format combos
        String[] formats = { "UTF-8", "Hex", "Base64" };
        kdfInputFormatCombo.getItems().addAll(formats);
        kdfSaltFormatCombo.getItems().addAll(formats);
        kdfInfoFormatCombo.getItems().addAll(formats);

        kdfInputFormatCombo.setValue("UTF-8");
        kdfSaltFormatCombo.setValue("Hex");
        kdfInfoFormatCombo.setValue("UTF-8");

        if (kdfInputBadgeLabel != null && kdfInputBadge == null) {
            kdfInputBadge = new com.cryptocarver.ui.component.MaterialFieldBadge("Input Key Material");
            kdfInputBadge.attach(kdfInputField, kdfInputFormatCombo);
            kdfInputBadge.textProperty().addListener((obs, oldVal, newVal) -> kdfInputBadgeLabel.setText(newVal));
            kdfInputBadge.getStyleClass().addListener((javafx.collections.ListChangeListener<String>) c -> {
                kdfInputBadgeLabel.getStyleClass().setAll(kdfInputBadge.getStyleClass());
            });
        }
        if (kdfSaltBadgeLabel != null && kdfSaltBadge == null) {
            kdfSaltBadge = new com.cryptocarver.ui.component.MaterialFieldBadge("Salt");
            kdfSaltBadge.attach(kdfSaltField, kdfSaltFormatCombo);
            kdfSaltBadge.textProperty().addListener((obs, oldVal, newVal) -> kdfSaltBadgeLabel.setText(newVal));
            kdfSaltBadge.getStyleClass().addListener((javafx.collections.ListChangeListener<String>) c -> {
                kdfSaltBadgeLabel.getStyleClass().setAll(kdfSaltBadge.getStyleClass());
            });
        }
        if (kdfInfoBadgeLabel != null && kdfInfoBadge == null) {
            kdfInfoBadge = new com.cryptocarver.ui.component.MaterialFieldBadge("Info");
            kdfInfoBadge.attach(kdfInfoField, kdfInfoFormatCombo);
            kdfInfoBadge.textProperty().addListener((obs, oldVal, newVal) -> kdfInfoBadgeLabel.setText(newVal));
            kdfInfoBadge.getStyleClass().addListener((javafx.collections.ListChangeListener<String>) c -> {
                kdfInfoBadgeLabel.getStyleClass().setAll(kdfInfoBadge.getStyleClass());
            });
        }

        // Add listener to update parameters based on algorithm
        kdfAlgorithmCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateKDFParameters(newVal);
        });
        kdfInputFormatCombo.valueProperty().addListener((obs, oldVal, newVal) -> updateKdfFormatHints());
        kdfSaltFormatCombo.valueProperty().addListener((obs, oldVal, newVal) -> updateKdfFormatHints());
        kdfInfoFormatCombo.valueProperty().addListener((obs, oldVal, newVal) -> updateKdfFormatHints());
        kdfInputField.textProperty().addListener((obs, oldVal, newVal) -> validateKdfEncodedField(kdfInputField, kdfInputFormatCombo));
        kdfSaltField.textProperty().addListener((obs, oldVal, newVal) -> validateKdfEncodedField(kdfSaltField, kdfSaltFormatCombo));
        kdfInfoField.textProperty().addListener((obs, oldVal, newVal) -> validateKdfEncodedField(kdfInfoField, kdfInfoFormatCombo));

        updateKDFParameters("HKDF-SHA256");
        updateKdfFormatHints();
    }

    private void updateKdfFormatHints() {
        updateKdfEncodedFieldHint(kdfInputField, kdfInputFormatCombo, "Input key material");
        updateKdfEncodedFieldHint(kdfSaltField, kdfSaltFormatCombo, "Salt");
        updateKdfEncodedFieldHint(kdfInfoField, kdfInfoFormatCombo, "Info / application context");
        if (kdfInputHelpLabel != null) {
            String format = kdfInputFormatCombo == null || kdfInputFormatCombo.getValue() == null
                    ? "UTF-8" : kdfInputFormatCombo.getValue();
            kdfInputHelpLabel.setText("Input key material — interpreted as " + format + " using Input key material format above.");
        }
    }

    private void updateKdfEncodedFieldHint(TextField field, ComboBox<String> formatCombo, String label) {
        if (field == null) return;
        String format = formatCombo == null || formatCombo.getValue() == null ? "UTF-8" : formatCombo.getValue();
        field.setPromptText(label + " (" + format + ")...");
        field.setAccessibleText(label + "; encoding: " + format);
        validateKdfEncodedField(field, formatCombo);
    }

    private void validateKdfEncodedField(TextField field, ComboBox<String> formatCombo) {
        if (field == null) return;
        String value = field.getText() == null ? "" : field.getText().trim();
        String format = formatCombo == null ? null : formatCombo.getValue();
        boolean invalid = !value.isEmpty() && (format == null || parseData(value, format) == null);
        if (invalid) {
            if (!field.getStyleClass().contains("field-error")) field.getStyleClass().add("field-error");
        } else {
            field.getStyleClass().remove("field-error");
        }
        if (kdfInputBadge != null) kdfInputBadge.updateState();
        if (kdfSaltBadge != null) kdfSaltBadge.updateState();
        if (kdfInfoBadge != null) kdfInfoBadge.updateState();
    }

    @FXML
    public void handleGenerateKdfSalt() {
        if (kdfSaltField == null || kdfSaltFormatCombo == null) return;
        byte[] salt = new byte[16];
        new java.security.SecureRandom().nextBytes(salt);
        kdfSaltFormatCombo.setValue("Hex");
        kdfSaltField.setText(DataConverter.bytesToHex(salt));
        clearKdfValidation();
        if (kdfSaltBadge != null) kdfSaltBadge.updateState();
        updateStatus("Generated a fresh 16-byte salt for key derivation");
    }

    /**
     * Update KDF parameters based on selected algorithm
     */
    private void updateKDFParameters(String algorithm) {
        if (algorithm == null) return;

        boolean requiresSalt = algorithm.startsWith("PBKDF2") || algorithm.equals("SCrypt") || algorithm.equals("Argon2id");

        if (algorithm.startsWith("HKDF")) {
            if (kdfIterationsLabel != null) { kdfIterationsLabel.setVisible(false); kdfIterationsLabel.setManaged(false); }
            if (kdfIterationsField != null) { kdfIterationsField.setText("1"); kdfIterationsField.setVisible(false); kdfIterationsField.setManaged(false); }
            if (kdfSaltBox != null) { kdfSaltBox.setVisible(true); kdfSaltBox.setManaged(true); }
            if (kdfSaltField != null) { kdfSaltField.setDisable(false); kdfSaltField.setPromptText("Optional salt (zeros if omitted)"); }
            if (kdfInfoBox != null) { kdfInfoBox.setVisible(true); kdfInfoBox.setManaged(true); }
            if (kdfInfoField != null) { kdfInfoField.setDisable(false); kdfInfoField.setPromptText("Optional application context"); }
        } else if (algorithm.startsWith("NIST-800-108")) {
            if (kdfIterationsLabel != null) { kdfIterationsLabel.setVisible(false); kdfIterationsLabel.setManaged(false); }
            if (kdfIterationsField != null) { kdfIterationsField.setText("1"); kdfIterationsField.setVisible(false); kdfIterationsField.setManaged(false); }
            if (kdfSaltBox != null) { kdfSaltBox.setVisible(true); kdfSaltBox.setManaged(true); }
            if (kdfSaltField != null) { kdfSaltField.setDisable(false); kdfSaltField.setPromptText("Label (optional)"); }
            if (kdfInfoBox != null) { kdfInfoBox.setVisible(true); kdfInfoBox.setManaged(true); }
            if (kdfInfoField != null) { kdfInfoField.setDisable(false); kdfInfoField.setPromptText("Context (optional)"); }
        } else if (algorithm.startsWith("X9.63")) {
            if (kdfIterationsLabel != null) { kdfIterationsLabel.setVisible(false); kdfIterationsLabel.setManaged(false); }
            if (kdfIterationsField != null) { kdfIterationsField.setText("1"); kdfIterationsField.setVisible(false); kdfIterationsField.setManaged(false); }
            if (kdfSaltBox != null) { kdfSaltBox.setVisible(false); kdfSaltBox.setManaged(false); }
            if (kdfInfoBox != null) { kdfInfoBox.setVisible(true); kdfInfoBox.setManaged(true); }
            if (kdfInfoField != null) { kdfInfoField.setDisable(false); kdfInfoField.setPromptText("Shared info (optional)"); }
        } else if (algorithm.startsWith("PBKDF2")) {
            if (kdfIterationsLabel != null) { kdfIterationsLabel.setVisible(true); kdfIterationsLabel.setManaged(true); }
            if (kdfIterationsField != null) { kdfIterationsField.setVisible(true); kdfIterationsField.setManaged(true); kdfIterationsField.setDisable(false); if (kdfIterationsField.getText().equals("1")) kdfIterationsField.setText("600000"); }
            if (kdfInfoBox != null) { kdfInfoBox.setVisible(false); kdfInfoBox.setManaged(false); }
            if (kdfSaltBox != null) { kdfSaltBox.setVisible(true); kdfSaltBox.setManaged(true); }
            if (kdfSaltField != null) { kdfSaltField.setDisable(false); kdfSaltField.setPromptText("Required salt"); }
        } else if (algorithm.equals("SCrypt")) {
            if (kdfIterationsLabel != null) { kdfIterationsLabel.setVisible(true); kdfIterationsLabel.setManaged(true); }
            if (kdfIterationsField != null) { kdfIterationsField.setVisible(true); kdfIterationsField.setManaged(true); kdfIterationsField.setDisable(false); if (kdfIterationsField.getText().equals("1")) kdfIterationsField.setText("32768"); }
            if (kdfInfoBox != null) { kdfInfoBox.setVisible(false); kdfInfoBox.setManaged(false); }
            if (kdfSaltBox != null) { kdfSaltBox.setVisible(true); kdfSaltBox.setManaged(true); }
            if (kdfSaltField != null) { kdfSaltField.setDisable(false); kdfSaltField.setPromptText("Required salt"); }
        } else if (algorithm.equals("Argon2id")) {
            if (kdfIterationsLabel != null) { kdfIterationsLabel.setVisible(true); kdfIterationsLabel.setManaged(true); }
            if (kdfIterationsField != null) { kdfIterationsField.setVisible(true); kdfIterationsField.setManaged(true); kdfIterationsField.setDisable(false); if (kdfIterationsField.getText().equals("1")) kdfIterationsField.setText("3"); }
            if (kdfInfoBox != null) { kdfInfoBox.setVisible(false); kdfInfoBox.setManaged(false); }
            if (kdfSaltBox != null) { kdfSaltBox.setVisible(true); kdfSaltBox.setManaged(true); }
            if (kdfSaltField != null) { kdfSaltField.setDisable(false); kdfSaltField.setPromptText("Required salt"); }
        }

        if (kdfSaltBadge != null) {
            if (requiresSalt && (kdfSaltField == null || kdfSaltField.getText().trim().isEmpty())) {
                kdfSaltBadge.updateStateIncomplete("Salt required");
            } else {
                kdfSaltBadge.updateState();
            }
        }
    }

    /** Initializes the standalone AES Key Wrap laboratory panel. */
    public void initializeKeyWrap(ComboBox<String> modeCombo, CheckBox unwrapCheck, TextField kekField,
            TextField dataField, TextArea resultArea) {
        this.keyWrapModeCombo = modeCombo;
        this.keyWrapUnwrapCheck = unwrapCheck;
        this.keyWrapKekField = kekField;
        this.keyWrapDataField = dataField;
        this.keyWrapResultArea = resultArea;
        modeCombo.getItems().setAll("RFC 3394 - AES Key Wrap", "RFC 5649 - AES Key Wrap with Padding");
        modeCombo.setValue("RFC 3394 - AES Key Wrap");
    }

    /** Executes wrapping or authenticated unwrapping of hexadecimal key material. */
    public void handleKeyWrap() {
        try {
            byte[] kek = DataConverter.hexToBytes(keyWrapKekField.getText().replaceAll("\\s+", ""));
            byte[] data = DataConverter.hexToBytes(keyWrapDataField.getText().replaceAll("\\s+", ""));
            boolean unwrap = keyWrapUnwrapCheck.isSelected();
            boolean padded = keyWrapModeCombo.getValue().startsWith("RFC 5649");
            byte[] result;
            if (unwrap) {
                result = padded ? KeyWrapOperations.unwrapRfc5649(kek, data) : KeyWrapOperations.unwrapRfc3394(kek, data);
            } else {
                result = padded ? KeyWrapOperations.wrapRfc5649(kek, data) : KeyWrapOperations.wrapRfc3394(kek, data);
            }
            String operation = unwrap ? "UNWRAP" : "WRAP";
            StringBuilder text = new StringBuilder("========================================\nAES KEY ")
                    .append(operation).append("\n========================================\n\n")
                    .append("Mode: ").append(keyWrapModeCombo.getValue()).append("\n")
                    .append("KEK: ").append(kek.length * 8).append(" bits\n")
                    .append("Input: ").append(data.length).append(" bytes\n")
                    .append("Output: ").append(result.length).append(" bytes\n\n")
                    .append(unwrap ? "UNWRAPPED:" : "WRAPPED:").append("\n")
                    .append(DataConverter.bytesToHex(result)).append("\n\n")
                    .append("✓ Integrity is verified during unwrapping.");
            keyWrapResultArea.setText(text.toString());
            keyWrapResultArea.setManaged(true);
            keyWrapResultArea.setVisible(true);
            updateStatus("AES Key Wrap " + operation.toLowerCase() + " completed");
            if (mainController != null) {
                mainController.publish(com.cryptocarver.model.OperationResult.forOperation("AES Key " + operation)
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", "Mode: " + (padded ? "RFC 5649" : "RFC 3394") + ", KEK: " + kek.length * 8 + " bits", com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", "Input: " + data.length + " bytes, output: " + result.length + " bytes", com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null)
                    ))
                    .build());
            }
        } catch (Exception e) {
            showError("AES Key Wrap", "Cannot execute operation: " + e.getMessage());
        }
    }

    /**
     * Handle key derivation
     */
    public void handleDeriveKey() {
        try {
            clearKdfValidation();
            String algorithm = kdfAlgorithmCombo.getValue();
            String inputFormat = kdfInputFormatCombo.getValue();
            String saltFormat = kdfSaltFormatCombo.getValue();
            String infoFormat = kdfInfoFormatCombo.getValue();

            String inputText = kdfInputField.getText().trim();
            String saltText = kdfSaltField.getText().trim();
            String infoText = kdfInfoField.getText().trim();
            String iterationsText = kdfIterationsField.getText().trim();
            String outputLengthText = kdfOutputLengthField.getText().trim();

            if (inputText.isEmpty()) {
                showKdfValidation("Enter input key material in the selected " + inputFormat + " format.", kdfInputField);
                return;
            }

            // Parse input according to format
            byte[] input = parseData(inputText, inputFormat);
            if (input == null) {
                showKdfValidation("Input key material is not valid " + inputFormat + ".", kdfInputField);
                return;
            }

            // Parse salt according to format (NULL if empty - no forced generation!)
            byte[] salt = null;
            if (!saltText.isEmpty()) {
                salt = parseData(saltText, saltFormat);
                if (salt == null) {
                    showKdfValidation("Salt is not valid " + saltFormat + ". For Hex, use pairs of digits 0-9 and A-F.", kdfSaltField);
                    return;
                }
            }

            // Parse info according to format
            byte[] info = null;
            if (!infoText.isEmpty()) {
                info = parseData(infoText, infoFormat);
                if (info == null) {
                    showKdfValidation("Info / application context is not valid " + infoFormat + ".", kdfInfoField);
                    return;
                }
            }

            // Parse iterations
            int iterations;
            try {
                iterations = Integer.parseInt(iterationsText);
            } catch (Exception e) {
                showKdfValidation("Iterations must be a positive whole number.", kdfIterationsField);
                return;
            }

            // Parse output length
            int outputLength;
            try {
                outputLength = Integer.parseInt(outputLengthText);
                if (outputLength < 1 || outputLength > 256) {
                    showKdfValidation("Output length must be between 1 and 256 bytes.", kdfOutputLengthField);
                    return;
                }
            } catch (Exception e) {
                showKdfValidation("Output length must be a whole number of bytes.", kdfOutputLengthField);
                return;
            }

            // Extract hash algorithm from name (e.g., "HKDF-SHA256" -> "SHA256")
            String hashAlgo = "SHA256"; // default
            if (algorithm.contains("SHA1")) {
                hashAlgo = "SHA1";
            } else if (algorithm.contains("SHA256")) {
                hashAlgo = "SHA256";
            } else if (algorithm.contains("SHA512")) {
                hashAlgo = "SHA512";
            }

            // Derive key based on algorithm
            byte[] derivedKey;
            String resultInfo;

            if (algorithm.startsWith("HKDF")) {
                // HKDF requires digest
                org.bouncycastle.crypto.Digest digest = com.cryptocarver.crypto.KeyDerivation.getDigest(hashAlgo);
                derivedKey = com.cryptocarver.crypto.KeyDerivation.hkdf(input, salt, info, outputLength, digest);
                resultInfo = buildHKDFResult(input, salt, info, outputLength, derivedKey, hashAlgo);
            } else if (algorithm.startsWith("NIST-800-108")) {
                org.bouncycastle.crypto.Digest digest = com.cryptocarver.crypto.KeyDerivation.getDigest(hashAlgo);
                derivedKey = com.cryptocarver.crypto.KeyDerivation.sp800108Counter(input, salt, info, outputLength, digest);
                resultInfo = buildContextKdfResult("NIST SP 800-108 Counter KDF", "Key", input,
                        "Label", salt, "Context", info, outputLength, derivedKey, hashAlgo);
            } else if (algorithm.startsWith("X9.63")) {
                org.bouncycastle.crypto.Digest digest = com.cryptocarver.crypto.KeyDerivation.getDigest(hashAlgo);
                derivedKey = com.cryptocarver.crypto.KeyDerivation.x963(input, info, outputLength, digest);
                resultInfo = buildContextKdfResult("ANSI X9.63 / Concatenation KDF", "Shared secret", input,
                        null, null, "Shared info", info, outputLength, derivedKey, hashAlgo);
            } else if (algorithm.startsWith("PBKDF2")) {
                // PBKDF2 requires salt
                if (salt == null || salt.length == 0) {
                    showKdfValidation("PBKDF2 requires a non-empty salt. Use Generate for a fresh 16-byte salt.", kdfSaltField);
                    return;
                }
                derivedKey = com.cryptocarver.crypto.KeyDerivation.pbkdf2(input, salt, iterations, outputLength,
                        hashAlgo);
                resultInfo = buildPBKDF2Result(input, salt, iterations, outputLength, derivedKey, hashAlgo);
            } else if (algorithm.equals("SCrypt")) {
                // SCrypt requires salt
                if (salt == null || salt.length == 0) {
                    showKdfValidation("SCrypt requires a non-empty salt. Use Generate for a fresh 16-byte salt.", kdfSaltField);
                    return;
                }
                // N=iterations, r=8, p=1
                derivedKey = com.cryptocarver.crypto.KeyDerivation.scrypt(input, salt, iterations, 8, 1, outputLength);
                resultInfo = buildSCryptResult(input, salt, iterations, 8, 1, outputLength, derivedKey);
            } else if (algorithm.equals("Argon2id")) {
                // Argon2 requires salt
                if (salt == null || salt.length < 8) {
                    showKdfValidation("Argon2id requires a salt of at least 8 bytes. Use Generate for a fresh 16-byte salt.", kdfSaltField);
                    return;
                }
                // iterations=time, memory=64MB, parallelism=4
                derivedKey = com.cryptocarver.crypto.KeyDerivation.argon2(input, salt, iterations, 65536, 4,
                        outputLength);
                resultInfo = buildArgon2Result(input, salt, iterations, 65536, 4, outputLength, derivedKey);
            } else {
                showKdfValidation("Choose a supported KDF algorithm.", kdfAlgorithmCombo);
                return;
            }

            // Display result
            kdfResultArea.setText(resultInfo);
            kdfResultArea.setVisible(true);
            kdfResultArea.setManaged(true);
            updateStatus("Key derived successfully using " + algorithm);

            // Add to history
            if (mainController != null) {
                mainController.publish(com.cryptocarver.model.OperationResult.forOperation("Derive - " + algorithm)
                    .input(input)
                    .output(derivedKey, com.cryptocarver.model.OperationDetail.Classification.SECRET)
                    .enrichedOutput(resultInfo, com.cryptocarver.model.OperationDetail.Classification.SECRET)
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", "Input: " + inputText.substring(0, Math.min(30, inputText.length())), com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", "Derived: " + DataConverter.bytesToHex(derivedKey).substring(0,
                            Math.min(50, DataConverter.bytesToHex(derivedKey).length())), com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null)
                    ))
                    .status("Key derived successfully using " + algorithm)
                    .build());
            }

        } catch (Exception e) {
            showKdfValidation("Cannot derive the key: " + e.getMessage(), null);
        }
    }

    private void clearKdfValidation() {
        if (kdfValidationLabel != null) {
            kdfValidationLabel.setText("");
            kdfValidationLabel.setVisible(false);
            kdfValidationLabel.setManaged(false);
        }
    }

    private void showKdfValidation(String message, javafx.scene.Node field) {
        if (kdfValidationLabel != null) {
            kdfValidationLabel.setText("⚠ " + message);
            kdfValidationLabel.setVisible(true);
            kdfValidationLabel.setManaged(true);
        }
        if (field != null) {
            if (!field.getStyleClass().contains("field-error")) field.getStyleClass().add("field-error");
            field.requestFocus();
        }
        updateStatus(message);
    }

    /**
     * Parse data according to format
     */
    private byte[] parseData(String text, String format) {
        try {
            switch (format) {
                case "UTF-8":
                    return text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                case "Hex":
                    return DataConverter.hexToBytes(text.replaceAll("\\s+", ""));
                case "Base64":
                    return java.util.Base64.getDecoder().decode(text.replaceAll("\\s+", ""));
                default:
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private String buildHKDFResult(byte[] input, byte[] salt, byte[] info, int outputLength, byte[] derivedKey,
            String hashAlgo) {
        StringBuilder result = new StringBuilder();
        result.append("========================================\n");
        result.append("HKDF-").append(hashAlgo).append(" KEY DERIVATION\n");
        result.append("========================================\n\n");
        result.append("Algorithm: HKDF (RFC 5869) with ").append(hashAlgo).append("\n\n");
        result.append("Input Key Material (").append(input.length).append(" bytes):\n");
        result.append(DataConverter.bytesToHex(input)).append("\n\n");
        if (salt != null && salt.length > 0) {
            result.append("Salt (").append(salt.length).append(" bytes):\n");
            result.append(DataConverter.bytesToHex(salt)).append("\n\n");
        } else {
            result.append("Salt: (none provided - HKDF will use zeros)\n\n");
        }
        if (info != null && info.length > 0) {
            result.append("Info (").append(info.length).append(" bytes):\n");
            result.append(new String(info, java.nio.charset.StandardCharsets.UTF_8)).append("\n");
            result.append("(hex: ").append(DataConverter.bytesToHex(info)).append(")\n\n");
        }
        result.append("Output Length: ").append(outputLength).append(" bytes\n\n");
        result.append("DERIVED KEY:\n");
        result.append(DataConverter.bytesToHex(derivedKey)).append("\n\n");
        result.append("✓ HKDF is deterministic: same inputs always produce same output\n");
        result.append("✓ Used in: TLS 1.3, Signal Protocol, WireGuard\n");
        return result.toString();
    }

    private String buildContextKdfResult(String name, String inputLabel, byte[] input, String firstLabel,
            byte[] firstValue, String secondLabel, byte[] secondValue, int outputLength, byte[] derivedKey,
            String hashAlgorithm) {
        StringBuilder result = new StringBuilder();
        result.append("========================================\n");
        result.append(name.toUpperCase()).append("\n");
        result.append("========================================\n\n");
        result.append("Hash/PRF: HMAC-").append(hashAlgorithm).append("\n");
        result.append(inputLabel).append(" (").append(input.length).append(" bytes):\n")
                .append(DataConverter.bytesToHex(input)).append("\n\n");
        appendKdfField(result, firstLabel, firstValue);
        appendKdfField(result, secondLabel, secondValue);
        result.append("Output Length: ").append(outputLength).append(" bytes\n\nDERIVED KEY:\n")
                .append(DataConverter.bytesToHex(derivedKey)).append("\n\n")
                .append("✓ Deterministic: preserve every input to reproduce this result\n");
        return result.toString();
    }

    private void appendKdfField(StringBuilder result, String label, byte[] value) {
        if (label == null) return;
        result.append(label).append(": ");
        if (value == null || value.length == 0) {
            result.append("(empty)\n\n");
        } else {
            result.append(value.length).append(" bytes\n").append(DataConverter.bytesToHex(value)).append("\n\n");
        }
    }

    private String buildPBKDF2Result(byte[] password, byte[] salt, int iterations, int outputLength, byte[] derivedKey,
            String hashAlgo) {
        StringBuilder result = new StringBuilder();
        result.append("========================================\n");
        result.append("PBKDF2-").append(hashAlgo).append(" KEY DERIVATION\n");
        result.append("========================================\n\n");
        result.append("Algorithm: PBKDF2 (PKCS #5) with HMAC-").append(hashAlgo).append("\n\n");
        result.append("Password/Input (").append(password.length).append(" bytes):\n");
        result.append(DataConverter.bytesToHex(password)).append("\n\n");
        result.append("Salt (").append(salt.length).append(" bytes):\n");
        result.append(DataConverter.bytesToHex(salt)).append("\n\n");
        result.append("Iterations: ").append(String.format("%,d", iterations));
        if (iterations < 100000) {
            result.append(" ⚠️ LOW - Recommend 600,000+ (OWASP 2023)");
        } else if (iterations < 600000) {
            result.append(" ⚠️ MEDIUM - Recommend 600,000+ (OWASP 2023)");
        } else {
            result.append(" ✓ GOOD (OWASP 2023 compliant)");
        }
        result.append("\n");
        result.append("Output Length: ").append(outputLength).append(" bytes\n\n");
        result.append("DERIVED KEY:\n");
        result.append(DataConverter.bytesToHex(derivedKey)).append("\n\n");
        result.append("✓ Standard password-based key derivation\n");
        result.append("✓ Widely supported and battle-tested\n");
        return result.toString();
    }

    private String buildSCryptResult(byte[] password, byte[] salt, int N, int r, int p, int outputLength,
            byte[] derivedKey) {
        StringBuilder result = new StringBuilder();
        result.append("========================================\n");
        result.append("SCRYPT KEY DERIVATION\n");
        result.append("========================================\n\n");
        result.append("Algorithm: SCrypt (memory-hard KDF)\n\n");
        result.append("Password/Input (").append(password.length).append(" bytes):\n");
        result.append(DataConverter.bytesToHex(password)).append("\n\n");
        result.append("Salt (").append(salt.length).append(" bytes):\n");
        result.append(DataConverter.bytesToHex(salt)).append("\n\n");
        result.append("Parameters:\n");
        result.append("  N (CPU/Memory cost): ").append(String.format("%,d", N));
        if (N < 16384) {
            result.append(" ⚠️ LOW");
        } else {
            result.append(" ✓ GOOD");
        }
        result.append("\n");
        result.append("  r (Block size): ").append(r).append("\n");
        result.append("  p (Parallelism): ").append(p).append("\n");
        result.append("  Memory required: ~").append((128 * N * r / 1024)).append(" KB\n\n");
        result.append("Output Length: ").append(outputLength).append(" bytes\n\n");
        result.append("DERIVED KEY:\n");
        result.append(DataConverter.bytesToHex(derivedKey)).append("\n\n");
        result.append("✓ Memory-hard: resistant to hardware attacks\n");
        result.append("✓ Used in: Litecoin, many password managers\n");
        return result.toString();
    }

    private String buildArgon2Result(byte[] password, byte[] salt, int iterations, int memory, int parallelism,
            int outputLength, byte[] derivedKey) {
        StringBuilder result = new StringBuilder();
        result.append("========================================\n");
        result.append("ARGON2ID KEY DERIVATION\n");
        result.append("========================================\n\n");
        result.append("Algorithm: Argon2id (Password Hashing Competition winner 2015)\n\n");
        result.append("Password/Input (").append(password.length).append(" bytes):\n");
        result.append(DataConverter.bytesToHex(password)).append("\n\n");
        result.append("Salt (").append(salt.length).append(" bytes):\n");
        result.append(DataConverter.bytesToHex(salt)).append("\n\n");
        result.append("Parameters:\n");
        result.append("  Time cost (iterations): ").append(iterations);
        if (iterations < 3) {
            result.append(" ⚠️ LOW");
        } else {
            result.append(" ✓ GOOD");
        }
        result.append("\n");
        result.append("  Memory cost: ").append(memory).append(" KB (").append(memory / 1024).append(" MB)\n");
        result.append("  Parallelism: ").append(parallelism).append(" threads\n\n");
        result.append("Output Length: ").append(outputLength).append(" bytes\n\n");
        result.append("DERIVED KEY:\n");
        result.append(DataConverter.bytesToHex(derivedKey)).append("\n\n");
        result.append("✓ Most modern and secure password hashing algorithm\n");
        result.append("✓ Combines data-dependent (Argon2i) and data-independent (Argon2d) approaches\n");
        result.append("✓ Recommended for new applications\n");
        return result.toString();
    }
    // ============================================================================
    // CMS / PKCS#7 OPERATIONS
    // ============================================================================

    // CMS UI // CMS
    private TextArea cmsInputArea;
    private TextArea cmsOutputArea;
    private CheckBox cmsDetachedCheck;
    private CheckBox cmsCadesBesCheck;
    private CheckBox cmsCadesTCheck;
    private TextField cmsCadesTsaUrlField;
    private javafx.scene.layout.HBox cmsCadesTsaBox;
    // Split fields
    private TextArea cmsSignCertArea;
    private TextArea cmsSignKeyArea;
    private TextArea cmsEncryptCertArea;
    private TextArea cmsDecryptKeyArea;
    private javafx.scene.control.RadioButton cmsSignSourcePkcs11Radio;
    private javafx.scene.layout.GridPane cmsSignLocalGrid;
    private javafx.scene.layout.HBox cmsSignPkcs11Box;
    private javafx.scene.control.ComboBox<String> cmsSignKeyAliasCombo;
    private javafx.scene.control.TextArea cmsVerifyDataArea;

    private javafx.scene.control.RadioButton cmsEncryptSourcePkcs11Radio;
    private javafx.scene.layout.GridPane cmsEncryptLocalGrid;
    private javafx.scene.layout.HBox cmsEncryptPkcs11Box;
    private javafx.scene.control.ComboBox<String> cmsEncryptKeyAliasCombo;
    private javafx.scene.control.Button cmsSignButton;
    private CheckBox cmsOnlineRevocationCheck;

    /**
     * Initialize CMS components
     */
    public void initializeCMS(TextArea inputArea, TextArea outputArea, CheckBox detachedCheck, CheckBox cadesBesCheck,
            CheckBox cadesTCheck, TextField cadesTsaUrlField, javafx.scene.layout.HBox cadesTsaBox,
            TextArea signCertArea, TextArea signKeyArea,
            TextArea encryptCertArea, TextArea decryptKeyArea,
            javafx.scene.control.RadioButton signSourcePkcs11Radio,
            javafx.scene.layout.GridPane signLocalGrid,
            javafx.scene.layout.HBox signPkcs11Box,
            javafx.scene.control.ComboBox<String> signKeyAliasCombo,
            TextArea verifyDataArea,
            javafx.scene.control.RadioButton encryptSourcePkcs11Radio,
            javafx.scene.layout.GridPane encryptLocalGrid,
            javafx.scene.layout.HBox encryptPkcs11Box,
            javafx.scene.control.ComboBox<String> encryptKeyAliasCombo, javafx.scene.control.Button signButton,
            CheckBox onlineRevocationCheck) {
        this.cmsInputArea = inputArea;
        this.cmsOutputArea = outputArea;
        this.cmsDetachedCheck = detachedCheck;
        this.cmsCadesBesCheck = cadesBesCheck;
        this.cmsCadesTCheck = cadesTCheck;
        this.cmsCadesTsaUrlField = cadesTsaUrlField;
        this.cmsCadesTsaBox = cadesTsaBox;
        this.cmsSignCertArea = signCertArea;
        this.cmsSignKeyArea = signKeyArea;
        this.cmsEncryptCertArea = encryptCertArea;
        this.cmsDecryptKeyArea = decryptKeyArea;
        this.cmsSignSourcePkcs11Radio = signSourcePkcs11Radio;
        this.cmsSignLocalGrid = signLocalGrid;
        this.cmsSignPkcs11Box = signPkcs11Box;
        this.cmsSignKeyAliasCombo = signKeyAliasCombo;
        this.cmsVerifyDataArea = verifyDataArea;

        this.cmsEncryptSourcePkcs11Radio = encryptSourcePkcs11Radio;
        this.cmsEncryptLocalGrid = encryptLocalGrid;
        this.cmsEncryptPkcs11Box = encryptPkcs11Box;
        this.cmsEncryptKeyAliasCombo = encryptKeyAliasCombo;
        this.cmsSignButton = signButton;
        this.cmsOnlineRevocationCheck = onlineRevocationCheck;
        handleCadesTimestampOptionChanged();
    }

    /** Shows the timestamp inputs and keeps CAdES-T dependent on CAdES-BES. */
    public void handleCadesTimestampOptionChanged() {
        boolean cadesT = cmsCadesTCheck != null && cmsCadesTCheck.isSelected();
        if (cadesT && cmsCadesBesCheck != null) {
            cmsCadesBesCheck.setSelected(true);
        }
        if (cmsCadesTsaBox != null) {
            cmsCadesTsaBox.setVisible(cadesT);
            cmsCadesTsaBox.setManaged(cadesT);
        }
        if (cadesT && cmsCadesTsaUrlField != null && cmsCadesTsaUrlField.getText().isBlank()) {
            cmsCadesTsaUrlField.setText(AppSettings.getInstance().getCustomTsaUrl());
        }
    }

    public void handleCMSourceChanged() {
        boolean usePkcs11 = cmsSignSourcePkcs11Radio != null && cmsSignSourcePkcs11Radio.isSelected();
        if (cmsSignLocalGrid != null) cmsSignLocalGrid.setVisible(!usePkcs11);
        if (cmsSignLocalGrid != null) cmsSignLocalGrid.setManaged(!usePkcs11);
        if (cmsSignPkcs11Box != null) cmsSignPkcs11Box.setVisible(usePkcs11);
        if (cmsSignPkcs11Box != null) cmsSignPkcs11Box.setManaged(usePkcs11);
    }

    public void handleLoadCMSKeys() {
        if (!com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().isConnected()) {
            showError("PKCS#11 Error", "No token is connected. Please connect from the left panel first.");
            return;
        }
        try {
            java.util.List<String> aliases = com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().requireSession().listPrivateKeysWithCertificate();
            cmsSignKeyAliasCombo.getItems().setAll(aliases);
            if (!aliases.isEmpty()) {
                cmsSignKeyAliasCombo.getSelectionModel().selectFirst();
            }
        } catch (Exception error) {
            showError("PKCS#11 Error", "Unable to list valid signing aliases: " + error.getMessage());
        }
    }

    public void handleCMSEncryptSourceChanged() {
        boolean usePkcs11 = cmsEncryptSourcePkcs11Radio != null && cmsEncryptSourcePkcs11Radio.isSelected();
        if (cmsEncryptLocalGrid != null) cmsEncryptLocalGrid.setVisible(!usePkcs11);
        if (cmsEncryptLocalGrid != null) cmsEncryptLocalGrid.setManaged(!usePkcs11);
        if (cmsEncryptPkcs11Box != null) cmsEncryptPkcs11Box.setVisible(usePkcs11);
        if (cmsEncryptPkcs11Box != null) cmsEncryptPkcs11Box.setManaged(usePkcs11);
    }

    public void handleLoadCMSEncryptKeys() {
        if (!com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().isConnected()) {
            showError("PKCS#11 Error", "No token is connected. Please connect from the left panel first.");
            return;
        }
        try {
            java.util.List<String> aliases = com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().requireSession().listPrivateKeysWithCertificate();
            cmsEncryptKeyAliasCombo.getItems().setAll(aliases);
            if (!aliases.isEmpty()) {
                cmsEncryptKeyAliasCombo.getSelectionModel().selectFirst();
            }
        } catch (Exception error) {
            showError("PKCS#11 Error", "Unable to list valid encrypt/decrypt aliases: " + error.getMessage());
        }
    }

    /**
     * Handle CMS Sign
     */
    public void handleCMSSign() {
        try {
            String dataStr = cmsInputArea.getText();
            boolean detached = cmsDetachedCheck.isSelected();
            boolean cadesBes = cmsCadesBesCheck != null && cmsCadesBesCheck.isSelected();
            boolean cadesT = cmsCadesTCheck != null && cmsCadesTCheck.isSelected();
            if (cadesT) cadesBes = true;
            boolean usePkcs11 = cmsSignSourcePkcs11Radio != null && cmsSignSourcePkcs11Radio.isSelected();

            if (dataStr.isEmpty()) {
                showError("Input Error", "Data to sign is required");
                return;
            }

            byte[] data = dataStr.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String alias = usePkcs11 ? cmsSignKeyAliasCombo.getSelectionModel().getSelectedItem() : null;
            String certStr = usePkcs11 ? null : cmsSignCertArea.getText().trim();
            String keyStr = usePkcs11 ? null : cmsSignKeyArea.getText().trim();
            String tsaUrl = cadesT ? cmsCadesTsaUrlField == null ? "" : cmsCadesTsaUrlField.getText().trim() : null;
            if (usePkcs11 && (alias == null || alias.isEmpty())) {
                showError("Input Error", "Please select a token alias with a valid certificate.");
                return;
            }
            if (!usePkcs11 && (certStr.isEmpty() || keyStr.isEmpty())) {
                showError("Input Error", "Signer Certificate and Private Key are required for local signing");
                return;
            }
            if (cadesT && !tsaUrl.startsWith("http://") && !tsaUrl.startsWith("https://")) {
                showError("CAdES-T TSA", "Enter a valid http:// or https:// TSA URL for CAdES-T.");
                return;
            }
            final boolean cadesBesOption = cadesBes;
            final boolean cadesTOption = cadesT;
            OperationExecutor executor = mainController == null ? null : mainController.getOperationExecutor();
            if (executor == null) {
                showError("Signing Error", "CMS operation executor is not available");
                return;
            }
            updateStatus("Signing data...");
            executor.execute("CMS/CAdES signing", cmsSignButton, () -> {
                byte[] signature;
                java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
                if (usePkcs11) {
                    signature = cadesBesOption
                            ? com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().requireSession()
                                    .signCadesBes(alias, data, detached)
                            : com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().requireSession()
                                    .signCms(alias, data, detached);
                    details.put("Source", "PKCS#11 Token");
                    details.put("Alias", alias);
                } else {
                    X509Certificate cert = CertificateGenerator.parseCertificate(certStr);
                    PrivateKey privateKey = parsePrivateKeyFromPEM(keyStr);
                    signature = cadesBesOption
                            ? CMSOperations.generateCadesBes(data, cert, privateKey, null, detached)
                            : CMSOperations.generateSignedData(data, cert, privateKey, null, detached);
                    details.put("Source", "Local PEM");
                    details.put("Certificate", "Present");
                    details.put("Private Key", "[not persisted]");
                }
                if (cadesTOption) {
                    AppSettings.getInstance().setCustomTsaUrl(tsaUrl);
                    byte[] signatureValue = CMSOperations.cadesSignatureValue(signature);
                    TsaDiagnostics.TokenResult timestamp = TsaDiagnostics.timestamp(tsaUrl, signatureValue, "SHA-256");
                    signature = CMSOperations.addCadesTSignatureTimestamp(signature, timestamp.token());
                    details.put("TSA", tsaUrl);
                    details.put("Timestamp", timestamp.report().generationTime());
                }
                return new CadesSignResult(signature, details);
            }, result -> {
                String output = "-----BEGIN PKCS7-----\n" + java.util.Base64.getEncoder().encodeToString(result.signature())
                        + "\n-----END PKCS7-----";
                cmsOutputArea.setText(output);
                result.details().put("Type", detached ? "Detached SignedData" : "Encapsulated SignedData");
                result.details().put("Profile", cadesTOption ? "CAdES-T" : (cadesBesOption ? "CAdES-BES" : "CMS / PKCS#7"));
                mainController.publish(OperationResult.forOperation(cadesTOption ? "CAdES-T Sign" : (cadesBesOption ? "CAdES-BES Sign" : "CMS Sign"))
                        .input(data).output(result.signature()).details(result.details())
                        .status((cadesTOption ? "CAdES-T" : (cadesBesOption ? "CAdES-BES" : "CMS")) + " signature generated successfully").build());
            }, error -> {
                showError("Signing Error", "Error signing data: " + error.getMessage());
                error.printStackTrace();
            }, () -> updateStatus("Signing cancelled"));
        } catch (Exception e) {
            showError("Signing Error", "Error signing data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private record CadesSignResult(byte[] signature, java.util.Map<String, String> details) { }

    /**
     * Handle CMS Verify
     */
    public void handleCMSVerify() {
        try {
            if (cmsOnlineRevocationCheck != null && cmsOnlineRevocationCheck.isSelected()) {
                handleCMSVerifyOnline();
                return;
            }
            String pkcs7Str = cmsInputArea.getText().trim();

            if (pkcs7Str.isEmpty()) {
                showError("Input Error", "PKCS#7 Signature is required in Input");
                return;
            }

            updateStatus("Verifying signature...");

            // Clean PEM
            String base64 = pkcs7Str.replace("-----BEGIN PKCS7-----", "")
                    .replace("-----END PKCS7-----", "")
                    .replaceAll("\\s+", "");
            byte[] pkcs7Bytes = java.util.Base64.getDecoder().decode(base64);

            byte[] detachedData = null;
            if (cmsVerifyDataArea != null && !cmsVerifyDataArea.getText().trim().isEmpty()) {
                detachedData = cmsVerifyDataArea.getText().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            }

            // Verify
            CMSOperations.VerificationResult result = CMSOperations.verifySignedData(pkcs7Bytes, null, detachedData);
            CMSOperations.CadesProfile cadesProfile = CMSOperations.inspectCadesProfile(pkcs7Bytes);
            CMSOperations.CadesTimestampStatus timestampStatus = CMSOperations.inspectCadesTimestamp(pkcs7Bytes);
            CMSOperations.CadesLongTermStatus longTerm = CMSOperations.inspectCadesLongTermEvidence(pkcs7Bytes);
            CMSOperations.CadesLongTermValidation longTermValidation =
                    CMSOperations.validateCadesLongTermEvidence(pkcs7Bytes, new java.util.Date());

            StringBuilder output = new StringBuilder();
            output.append("VERIFICATION RESULT: ").append(result.verified ? "✅ VALID" : "❌ INVALID").append("\n\n");
            output.append("SIGNATURE PROFILE: ").append(cadesProfile.profile()).append("\n");
            if (cadesProfile.certificateBindingPresent()) {
                output.append("CAdES certificate binding: ")
                        .append(cadesProfile.certificateBindingValid() ? "✅ VALID" : "❌ INVALID")
                        .append("\n");
            }
            output.append(cadesProfile.message()).append("\n\n");
            if (timestampStatus.present()) {
                output.append("CAdES signature timestamp: ")
                        .append(timestampStatus.imprintValid() ? "✅ VALID" : "❌ INVALID").append("\n")
                        .append(timestampStatus.message()).append("\n\n");
            }
            if (cadesProfile.profile().startsWith("CAdES")) {
                output.append("LONG-TERM EVIDENCE: ").append(longTerm.level()).append("\n")
                        .append("CRL evidence: ").append(longTermValidation.crlCount())
                        .append("; signature-valid: ").append(longTermValidation.signatureValidCrlCount())
                        .append("; within declared validity: ").append(longTermValidation.currentCrlCount()).append("\n")
                        .append(longTermValidation.message()).append("\n\n");
            }

            if (result.content != null) {
                output.append("SIGNED CONTENT:\n");
                output.append(new String(result.content, java.nio.charset.StandardCharsets.UTF_8)).append("\n\n");
            } else {
                output.append("Content is detached (not present in signature).\n\n");
            }

            if (!result.associatedData.isEmpty()) {
                output.append("SIGNED ATTRIBUTES:\n");
                for (java.util.Map.Entry<String, String> entry : result.associatedData.entrySet()) {
                    output.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                }
            }

            cmsOutputArea.setText(output.toString());
            mainController.publish(OperationResult.forOperation(
                            cadesProfile.profile().startsWith("CAdES") ? cadesProfile.profile() + " Verify" : "CMS Verify")
                    .input(pkcs7Bytes).output(result.content)
                    .detail("Result", result.verified ? "VALID" : "INVALID")
                    .detail("Profile", cadesProfile.profile())
                    .detail("Certificate binding", cadesProfile.certificateBindingPresent()
                            ? (cadesProfile.certificateBindingValid() ? "VALID" : "INVALID") : "NOT PRESENT")
                    .detail("Signature timestamp", timestampStatus.present()
                            ? (timestampStatus.imprintValid() ? "VALID" : "INVALID") : "NOT PRESENT")
                    .detail("Long-term evidence", longTerm.level())
                    .detail("CRLs embedded", String.valueOf(longTermValidation.crlCount()))
                    .detail("CRLs signature-valid", String.valueOf(longTermValidation.signatureValidCrlCount()))
                    .detail("CRLs currently valid", String.valueOf(longTermValidation.currentCrlCount()))
                    .status("CMS verification: " + (result.verified ? "valid" : "invalid")).build());

        } catch (Exception e) {
            cmsOutputArea.setText("Verification Failed: " + e.getMessage());
            updateStatus("Verification failed");
            e.printStackTrace();
        }
    }

    private void handleCMSVerifyOnline() {
        String input = cmsInputArea == null ? "" : cmsInputArea.getText().trim();
        if (input.isEmpty()) {
            showError("Input Error", "PKCS#7 Signature is required in Input");
            return;
        }
        final byte[] cmsBytes;
        try {
            cmsBytes = decodeCmsArmored(input);
        } catch (Exception error) {
            showError("Verification Error", "Invalid CMS encoding");
            return;
        }
        final byte[] detached = cmsVerifyDataArea != null && !cmsVerifyDataArea.getText().trim().isEmpty()
                ? cmsVerifyDataArea.getText().getBytes(java.nio.charset.StandardCharsets.UTF_8) : null;
        OperationExecutor executor = mainController == null ? null : mainController.getOperationExecutor();
        if (executor == null) {
            showError("Verification Error", "CMS operation executor is not available");
            return;
        }
        executor.execute("CMS/CAdES online revocation validation", null,
                () -> new com.cryptocarver.crypto.CmsInspector().inspect(cmsBytes, detached, null, true, java.util.List.of()),
                report -> {
                    String integrity = report.getValidationSteps().stream()
                            .filter(step -> "Signature/Integrity".equals(step.getStepName()))
                            .map(step -> step.getState().name()).findFirst().orElse("NOT_EVALUATED");
                    String text = "CMS/CAdES verification report\n"
                            + "Integrity/signature: " + integrity + "\n"
                            + "Trust chain: not evaluated (no truststore provided)\n"
                            + "Revocation: " + report.getRevocation().status() + "\n"
                            + "Evidence: " + report.getRevocation().evidence() + "\n"
                            + (report.getRevocation().errors().isEmpty() ? "" : "Reason: " + String.join("; ", report.getRevocation().errors()) + "\n");
                    cmsOutputArea.setText(text);
                    updateStatus("CMS revocation validation completed: " + report.getRevocation().status());
                },
                error -> {
                    cmsOutputArea.setText("Verification Failed: " + (error.getMessage() == null ? "CMS validation failed" : error.getMessage()));
                    updateStatus("Verification failed");
                },
                () -> updateStatus("CMS validation cancelled"));
    }

    /**
     * Upgrades the CAdES-T currently shown in the CMS output area by embedding
     * user-selected CRL and optional certificate-chain evidence. It is
     * deliberately offline: CryptoCarver never discovers or downloads
     * revocation URLs on the user's behalf.
     */
    public void handleUpgradeCadesLt() {
        try {
            String current = cmsOutputArea == null ? "" : cmsOutputArea.getText().trim();
            if (current.isEmpty()) {
                showError("CAdES-LT", "Generate or paste a CAdES-T signature into the Output area first.");
                return;
            }
            byte[] cadesT = decodeCmsArmored(current);
            CMSOperations.CadesLongTermStatus status = CMSOperations.inspectCadesLongTermEvidence(cadesT);
            if (!"CAdES-T".equals(status.level())) {
                showError("CAdES-LT", "The selected CMS must be a valid CAdES-T signature without LT evidence.");
                return;
            }

            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select CAdES-LT Evidence (CRL required; certificates optional)");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("CRL or certificate evidence", "*.crl", "*.cer", "*.crt", "*.der", "*.pem"),
                    new FileChooser.ExtensionFilter("All files", "*.*"));
            java.util.List<java.io.File> files = chooser.showOpenMultipleDialog(cmsOutputArea.getScene().getWindow());
            if (files == null || files.isEmpty()) return;

            java.util.List<java.security.cert.X509CRL> crls = new java.util.ArrayList<>();
            java.util.List<java.security.cert.X509Certificate> certificates = new java.util.ArrayList<>();
            for (java.io.File file : files) {
                byte[] evidence = java.nio.file.Files.readAllBytes(file.toPath());
                try {
                    crls.add(CMSOperations.parseX509Crl(evidence));
                } catch (Exception notACrl) {
                    try {
                        certificates.addAll(CMSOperations.parseX509Certificates(evidence));
                    } catch (Exception notACertificate) {
                        throw new IllegalArgumentException(file.getName()
                                + " is neither a valid X.509 CRL nor X.509 certificate evidence", notACertificate);
                    }
                }
            }
            if (crls.isEmpty()) {
                showError("CAdES-LT", "Select at least one CRL. Certificate files alone are not revocation evidence.");
                return;
            }
            byte[] upgraded = CMSOperations.addCadesLtEvidence(cadesT, certificates, crls);
            String armored = "-----BEGIN PKCS7-----\n" + java.util.Base64.getEncoder().encodeToString(upgraded)
                    + "\n-----END PKCS7-----";
            cmsOutputArea.setText(armored);
            mainController.publish(OperationResult.forOperation("CAdES-LT Evidence")
                    .input(cadesT).output(upgraded)
                    .detail("CRL evidence", String.valueOf(crls.size()))
                    .detail("Certificate evidence", String.valueOf(certificates.size()))
                    .detail("Network", "Not used; evidence selected locally")
                    .status("CAdES-LT evidence embedded; validate freshness and trust separately").build());
            updateStatus("CAdES-LT evidence embedded from " + crls.size() + " CRL(s) and "
                    + certificates.size() + " certificate(s).");
        } catch (Exception error) {
            showError("CAdES-LT", "Unable to embed LT evidence: " + error.getMessage());
        }
    }

    private static byte[] decodeCmsArmored(String input) {
        String base64 = input.replace("-----BEGIN PKCS7-----", "")
                .replace("-----END PKCS7-----", "")
                .replaceAll("\\s+", "");
        return java.util.Base64.getDecoder().decode(base64);
    }

    public void handleCMSEncrypt() {
        try {
            String dataStr = cmsInputArea.getText();
            boolean usePkcs11 = cmsEncryptSourcePkcs11Radio != null && cmsEncryptSourcePkcs11Radio.isSelected();

            if (dataStr.isEmpty()) {
                showError("Input Error", "Data to encrypt is required");
                return;
            }

            updateStatus("Encrypting data...");
            byte[] data = dataStr.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            X509Certificate cert;
            String alias = null;

            if (usePkcs11) {
                if (!com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().isConnected()) {
                    showError("PKCS#11 Error", "No token connected.");
                    return;
                }
                alias = cmsEncryptKeyAliasCombo.getValue();
                if (alias == null || alias.isEmpty()) {
                    showError("PKCS#11 Error", "Select an alias from the token.");
                    return;
                }
                cert = (X509Certificate) com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().requireSession().getCertificateChain(alias)[0];
                if (cert == null) {
                    showError("PKCS#11 Error", "No certificate found for the selected alias.");
                    return;
                }
            } else {
                String certStr = cmsEncryptCertArea.getText().trim();
                if (certStr.isEmpty()) {
                    showError("Input Error", "Recipient Certificate is required in Local mode");
                    return;
                }
                cert = CertificateGenerator.parseCertificate(certStr);
            }

            byte[] encrypted = CMSOperations.generateEnvelopedData(data, cert);

            String output = "-----BEGIN PKCS7-----\n" +
                    java.util.Base64.getEncoder().encodeToString(encrypted) +
                    "\n-----END PKCS7-----";

            cmsOutputArea.setText(output);
            updateStatus("CMS Encrypted (EnvelopedData) successfully");
            String sourceStr = usePkcs11 ? ("PKCS#11 Token (alias: " + alias + ")") : "Local PEM";
            mainController.publish(com.cryptocarver.model.OperationResult.forOperation("CMS Encrypt (EnvelopedData)")
                    .input(data).output(encrypted)
                    .detail("Source", sourceStr)
                    .status("CMS data encrypted successfully").build());
        } catch (Exception e) {
            showError("Encryption Error", "Error encrypting data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void handleCMSDecrypt() {
        try {
            String pkcs7Str = cmsInputArea.getText().trim();
            boolean usePkcs11 = cmsEncryptSourcePkcs11Radio != null && cmsEncryptSourcePkcs11Radio.isSelected();

            if (pkcs7Str.isEmpty()) {
                showError("Input Error", "PKCS#7 Enveloped Data is required");
                return;
            }

            updateStatus("Decrypting data...");

            // Clean PEM
            String base64 = pkcs7Str.replace("-----BEGIN PKCS7-----", "")
                    .replace("-----END PKCS7-----", "")
                    .replaceAll("\\s+", "");
            byte[] pkcs7Bytes = java.util.Base64.getDecoder().decode(base64);

            byte[] decrypted;
            String alias = null;

            if (usePkcs11) {
                if (!com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().isConnected()) {
                    showError("PKCS#11 Error", "No token connected.");
                    return;
                }
                alias = cmsEncryptKeyAliasCombo.getValue();
                if (alias == null || alias.isEmpty()) {
                    showError("PKCS#11 Error", "Select an alias from the token.");
                    return;
                }
                decrypted = com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().requireSession().decryptCms(alias, pkcs7Bytes);
            } else {
                String keyStr = cmsDecryptKeyArea.getText().trim();
                if (keyStr.isEmpty()) {
                    showError("Input Error", "Private Key is required in Local mode");
                    return;
                }
                PrivateKey privateKey = parsePrivateKeyFromPEM(keyStr);
                decrypted = CMSOperations.decryptEnvelopedData(pkcs7Bytes, privateKey);
            }

            cmsOutputArea.setText(new String(decrypted, java.nio.charset.StandardCharsets.UTF_8));
            updateStatus("CMS Decrypted successfully");
            String sourceStr = usePkcs11 ? ("PKCS#11 Token (alias: " + alias + ")") : "Local PEM";
            mainController.publish(com.cryptocarver.model.OperationResult.forOperation("CMS Decrypt (EnvelopedData)")
                    .input(pkcs7Bytes).output(decrypted)
                    .detail("Source", sourceStr)
                    .detail("Private Key", "[not persisted]")
                    .status("CMS data decrypted successfully").build());
        } catch (Exception e) {
            cmsOutputArea.setText("Decryption Failed: " + e.getMessage());
            updateStatus("Decryption failed");
            e.printStackTrace();
        }
    }

    // Helper to parse Private Key from PEM (simplistic version for now)
    private PrivateKey parsePrivateKeyFromPEM(String pemKey) throws Exception {
        String base64 = pemKey.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");

        byte[] encoded = java.util.Base64.getDecoder().decode(base64);
        java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA"); // Defaulting to RSA for now

        try {
            return keyFactory.generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(encoded));
        } catch (Exception e) {
            // Try as standard RSA private key (PKCS#1) if needed, but Java mostly supports
            // PKCS#8
            // If BouncyCastle is registered, we can try to use it more robustly
            throw new Exception("Could not parse Private Key. Ensure it is PKCS#8 format (or standard PEM). sent: "
                    + e.getMessage());
        }
    }

    // ============================================================================
    // CERTIFICATE CHAIN VALIDATION
    // ============================================================================

    public void initializeCertificateChain(TextArea inputArea, TextArea crlArea, TextArea resultArea) {
        this.chainInputArea = inputArea;
        this.chainCrlInputArea = crlArea;
        this.chainResultArea = resultArea;
    }

    public void handleValidateCertificateChain() {
        try {
            String chainStr = chainInputArea.getText().trim();

            if (chainStr.isEmpty()) {
                showError("Input Error", "Certificate Chain PEM is required");
                return;
            }

            updateStatus("Validating chain...");

            // Extract multiple certificates from PEM sequence
            List<String> pemCerts = new ArrayList<>();
            String[] parts = chainStr.split("-----BEGIN CERTIFICATE-----");

            for (String part : parts) {
                if (part.trim().isEmpty())
                    continue;
                String pem = "-----BEGIN CERTIFICATE-----" + part;
                int endIndex = pem.indexOf("-----END CERTIFICATE-----");
                if (endIndex != -1) {
                    pem = pem.substring(0, endIndex + 25);
                    pemCerts.add(pem);
                }
            }

            if (pemCerts.isEmpty()) {
                showError("Input Error", "No valid PEM certificates found");
                return;
            }

            List<X509Certificate> chain = new ArrayList<>();
            for (String pem : pemCerts) {
                chain.add(CertificateGenerator.parseCertificate(pem));
            }

            List<java.security.cert.X509CRL> crls = null;
            if (chainCrlInputArea != null && !chainCrlInputArea.getText().trim().isEmpty()) {
                crls = new ArrayList<>();
                String crlsStr = chainCrlInputArea.getText().trim();
                String[] crlParts = crlsStr.split("-----BEGIN X509 CRL-----");
                for (String part : crlParts) {
                    if (part.trim().isEmpty()) continue;
                    String pem = "-----BEGIN X509 CRL-----" + part;
                    int endIndex = pem.indexOf("-----END X509 CRL-----");
                    if (endIndex != -1) {
                        pem = pem.substring(0, endIndex + 22);
                        crls.add(RevocationOperations.parseCrlPem(pem));
                    }
                }
            }

            // Validate
            CertificateGenerator.ChainValidationResult result = CertificateGenerator.validateCertificateChain(chain, crls);

            StringBuilder sb = new StringBuilder();
            sb.append("CHAIN VALIDATION: ").append(result.isValid ? "✅ VALID" : "❌ INVALID").append("\n\n");

            if (result.message != null) {
                sb.append("Message: ").append(result.message).append("\n\n");
            }

            sb.append("DETAILS:\n");
            for (String detail : result.details) {
                sb.append("- ").append(detail).append("\n");
            }

            String outputText = sb.toString();
            chainResultArea.setText(outputText);
            chainResultArea.setVisible(true);
            chainResultArea.setManaged(true);

            updateStatus("Chain validation complete: " + (result.isValid ? "Valid" : "Invalid"));
            if (mainController != null) {
                mainController.publish(com.cryptocarver.model.OperationResult.forOperation("Validate Chain")
                    .enrichedOutput(outputText, com.cryptocarver.model.OperationDetail.Classification.PUBLIC)
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Chain Length", String.valueOf(chain.size()), com.cryptocarver.model.OperationDetail.Classification.PUBLIC, false, null),
                        new com.cryptocarver.model.OperationDetail("Result", result.isValid ? "Valid" : "Invalid", com.cryptocarver.model.OperationDetail.Classification.PUBLIC, false, null)
                    ))
                    .status("Certificate chain validation completed")
                    .build());
            }

        } catch (Exception e) {
            showError("Validation Error", "Error validating chain: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // --- Global Helper Methods ---

    public void handleClear() {
        // Symmetric
        if (generatedKeyField != null)
            generatedKeyField.clear();
        if (keyInputField != null)
            keyInputField.clear();
        if (validationResultArea != null)
            validationResultArea.clear();
        if (component1Field != null)
            component1Field.clear();
        if (component2Field != null)
            component2Field.clear();
        if (component3Field != null)
            component3Field.clear();
        if (componentResultsArea != null)
            componentResultsArea.clear();
    }

    public void handleClearAsymmetric() {
        // Asymmetric
        currentRsaSummary = null;
        currentEcdsaSummary = null;
        currentDsaSummary = null;
        currentEddsaSummary = null;

        if (rsaSummaryCard != null) { rsaSummaryCard.setVisible(false); rsaSummaryCard.setManaged(false); }
        if (ecdsaSummaryCard != null) { ecdsaSummaryCard.setVisible(false); ecdsaSummaryCard.setManaged(false); }
        if (dsaSummaryCard != null) { dsaSummaryCard.setVisible(false); dsaSummaryCard.setManaged(false); }
        if (eddsaSummaryCard != null) { eddsaSummaryCard.setVisible(false); eddsaSummaryCard.setManaged(false); }

        if (rsaPublicKeyArea != null) rsaPublicKeyArea.clear();
        if (rsaPrivateKeyArea != null) rsaPrivateKeyArea.clear();
        if (dsaPublicKeyArea != null) dsaPublicKeyArea.clear();
        if (dsaPrivateKeyArea != null) dsaPrivateKeyArea.clear();
        if (ecdsaPublicKeyArea != null) ecdsaPublicKeyArea.clear();
        if (ecdsaPrivateKeyArea != null) ecdsaPrivateKeyArea.clear();
        if (ecdsaFpPublicKeyArea != null) ecdsaFpPublicKeyArea.clear();
        if (ecdsaFpPrivateKeyArea != null) ecdsaFpPrivateKeyArea.clear();
        if (eddsaPublicKeyArea != null) eddsaPublicKeyArea.clear();
        if (eddsaPrivateKeyArea != null) eddsaPrivateKeyArea.clear();
        if (ed25519PublicKeyArea != null) ed25519PublicKeyArea.clear();
        if (ed25519PrivateKeyArea != null) ed25519PrivateKeyArea.clear();
    }

    private void updateAsymmetricSummaryCard(VBox card, Label algoLbl, Label fpLbl, Label pubLenLbl, Label privLenLbl, Label createdLbl, Label savedLbl, GeneratedAsymmetricKeySummary summary) {
        if (card == null || summary == null) return;
        if (algoLbl != null) algoLbl.setText(summary.getAlgorithm() + " (" + summary.getCurveOrKeySize() + ")");
        if (fpLbl != null) fpLbl.setText(summary.getPublicFingerprintTruncated());
        if (pubLenLbl != null) pubLenLbl.setText(summary.getPublicKeyLength());
        if (privLenLbl != null) privLenLbl.setText(summary.getPrivateKeyLength());
        if (createdLbl != null) createdLbl.setText(summary.getCreatedAt());
        if (savedLbl != null) savedLbl.setText(summary.getSavedStatus() != null ? "✓ " + summary.getSavedStatus() : "");
        card.setVisible(true);
        card.setManaged(true);
    }

    private void copyPublicKey(GeneratedAsymmetricKeySummary summary) {
        if (summary == null || summary.getPublicKeyPem() == null) {
            updateStatus("No public key available to copy.");
            return;
        }
        copyToClipboard(summary.getPublicKeyPem());
        updateStatus("Copied " + summary.getAlgorithm() + " public key to clipboard");
    }

    private void copyPrivateKey(GeneratedAsymmetricKeySummary summary) {
        if (summary == null || summary.getPrivateKeyPem() == null) {
            updateStatus("No private key available to copy.");
            return;
        }
        com.cryptocarver.model.SecretVisibilityProfile profile = com.cryptocarver.model.AppSettings.getInstance().getSecretVisibilityProfile();
        if (profile != com.cryptocarver.model.SecretVisibilityProfile.FULL_LAB) {
            updateStatus("Action blocked: Private key cannot be copied under " + profile + " profile.");
            showInfo("Security Policy", "Copying private key material is blocked under " + profile + " profile. Switch to FULL_LAB to copy private keys.");
            return;
        }
        copyToClipboard(summary.getPrivateKeyPem());
        updateStatus("Copied " + summary.getAlgorithm() + " private key to clipboard");
    }

    private void copyAsymmetricSummary(GeneratedAsymmetricKeySummary summary) {
        if (summary == null) {
            updateStatus("No asymmetric summary available to copy.");
            return;
        }
        com.cryptocarver.model.SecretVisibilityProfile profile = com.cryptocarver.model.AppSettings.getInstance().getSecretVisibilityProfile();
        String privDisplay = (profile == com.cryptocarver.model.SecretVisibilityProfile.FULL_LAB)
                ? summary.getPrivateKeyPem()
                : "***MASKED***";

        StringBuilder sb = new StringBuilder();
        sb.append("--- ").append(summary.getAlgorithm()).append(" Key Pair Summary ---\n");
        sb.append("Algorithm/Size: ").append(summary.getAlgorithm()).append(" (").append(summary.getCurveOrKeySize()).append(")\n");
        sb.append("Public Fingerprint (SHA-256): ").append(summary.getPublicFingerprintTruncated()).append("\n");
        sb.append("Public Key Length: ").append(summary.getPublicKeyLength()).append("\n");
        sb.append("Private Key Length: ").append(summary.getPrivateKeyLength()).append("\n");
        sb.append("Creation Time: ").append(summary.getCreatedAt()).append("\n");
        sb.append("Compatible Uses: ").append(summary.getCompatibleUses()).append("\n");
        sb.append("Origin: ").append(summary.getOrigin()).append("\n\n");
        sb.append("=== PUBLIC KEY (PEM) ===\n").append(summary.getPublicKeyPem()).append("\n\n");
        sb.append("=== PRIVATE KEY (PEM) ===\n").append(privDisplay);

        copyToClipboard(sb.toString());
        updateStatus("Copied " + summary.getAlgorithm() + " PEM Pair Summary to clipboard");
    }

    private void exportPublicPem(GeneratedAsymmetricKeySummary summary, String defaultFilename) {
        if (summary == null || summary.getPublicKeyPem() == null) {
            updateStatus("No public key available to export.");
            return;
        }
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Public Key PEM");
        fc.setInitialFileName(defaultFilename);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PEM Files (*.pem, *.pub)", "*.pem", "*.pub"));
        java.io.File file = fc.showSaveDialog(null);
        if (file != null) {
            try {
                java.nio.file.Files.writeString(file.toPath(), summary.getPublicKeyPem(), StandardCharsets.UTF_8);
                updateStatus("Exported public key to " + file.getName());
                summary.setSavedStatus("Exported to " + file.getName());
            } catch (Exception e) {
                showError("Export Error", "Error exporting public key: " + e.getMessage());
            }
        }
    }

    private void exportPrivatePem(GeneratedAsymmetricKeySummary summary, String defaultFilename) {
        if (summary == null || summary.getPrivateKeyPem() == null) {
            updateStatus("No private key available to export.");
            return;
        }
        com.cryptocarver.model.SecretVisibilityProfile profile = com.cryptocarver.model.AppSettings.getInstance().getSecretVisibilityProfile();
        if (profile != com.cryptocarver.model.SecretVisibilityProfile.FULL_LAB) {
            updateStatus("Action blocked: Exporting private key is blocked under " + profile + " profile.");
            showInfo("Security Policy", "Exporting private key files is blocked under " + profile + " profile. Switch to FULL_LAB to export private keys.");
            return;
        }
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Private Key PEM");
        fc.setInitialFileName(defaultFilename);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PEM Files (*.pem, *.key)", "*.pem", "*.key"));
        java.io.File file = fc.showSaveDialog(null);
        if (file != null) {
            try {
                java.nio.file.Files.writeString(file.toPath(), summary.getPrivateKeyPem(), StandardCharsets.UTF_8);
                updateStatus("Exported private key to " + file.getName());
                summary.setSavedStatus("Exported to " + file.getName());
            } catch (Exception e) {
                showError("Export Error", "Error exporting private key: " + e.getMessage());
            }
        }
    }

    private void sendPublicKeyToShelf(GeneratedAsymmetricKeySummary summary) {
        sendAsymmetricKeyToShelf(summary, AsymmetricShelfMaterial.PUBLIC);
    }

    private enum AsymmetricShelfMaterial {
        PUBLIC,
        PRIVATE
    }

    /**
     * Stores only canonical PEM material from the generated summary. The
     * rendered diagnostic TextAreas are deliberately not consulted here.
     */
    private void sendAsymmetricKeyToShelf(GeneratedAsymmetricKeySummary summary,
                                          AsymmetricShelfMaterial material) {
        if (summary == null) {
            updateStatus("No generated " + (material == AsymmetricShelfMaterial.PRIVATE ? "private" : "public")
                    + " key pair available for Clipboard Shelf.");
            return;
        }

        if (material == AsymmetricShelfMaterial.PRIVATE) {
            if (com.cryptocarver.model.AppSettings.getInstance().getSecretVisibilityProfile()
                    != com.cryptocarver.model.SecretVisibilityProfile.FULL_LAB) {
                updateStatus("Action blocked: private key material requires FULL_LAB.");
                return;
            }
            String privatePem = summary.getPrivateKeyPem();
            if (privatePem == null || privatePem.isBlank()) {
                updateStatus("No generated private key available for Clipboard Shelf.");
                return;
            }
            com.cryptocarver.model.ClipboardEntry entry =
                    com.cryptocarver.model.ClipboardShelfManager.getInstance()
                            .addSessionOnlyPrivateKey(privatePem, "Key Generation", summary.getAlgorithm());
            if (entry == null) {
                updateStatus("Action blocked: private key material requires FULL_LAB.");
                return;
            }
            revealShelfEntry(entry);
            updateStatus("Added " + summary.getAlgorithm() + " private key to Clipboard Shelf (session only).");
            return;
        }

        String publicPem = summary.getPublicKeyPem();
        if (publicPem == null || publicPem.isBlank()) {
            updateStatus("No generated public key available for Clipboard Shelf.");
            return;
        }
        com.cryptocarver.model.ClipboardEntry entry = new com.cryptocarver.model.ClipboardEntry(
                summary.getAlgorithm() + " Public Key",
                publicPem,
                com.cryptocarver.model.ClipboardEntry.Format.PEM,
                com.cryptocarver.model.OperationDetail.Classification.PUBLIC,
                "Key Generation",
                summary.getAlgorithm()
        );
        com.cryptocarver.model.ClipboardShelfManager.getInstance().addEntry(entry);
        revealShelfEntry(entry);
        updateStatus("Added " + summary.getAlgorithm() + " public key to Clipboard Shelf.");
    }

    private void revealShelfEntry(com.cryptocarver.model.ClipboardEntry entry) {
        if (entry != null && mainController instanceof ModernMainController modern) {
            modern.revealShelfEntry(entry);
        }
    }

    private GeneratedAsymmetricKeySummary summaryForGeneration(String operation) {
        if (operation == null) return null;
        return switch (operation) {
            case "RSA Key Generation" -> currentRsaSummary;
            case "ECDSA Key Generation" -> currentEcdsaSummary;
            case "DSA Key Generation" -> currentDsaSummary;
            case "EdDSA Key Generation" -> currentEddsaSummary;
            default -> null;
        };
    }

    private TabPane tabsForGeneration(String operation) {
        if (operation == null) return null;
        return switch (operation) {
            case "RSA Key Generation" -> rsaKeyMaterialTabs;
            case "ECDSA Key Generation" -> ecdsaKeyMaterialTabs;
            case "DSA Key Generation" -> dsaKeyMaterialTabs;
            case "EdDSA Key Generation" -> eddsaKeyMaterialTabs;
            default -> null;
        };
    }

    /** Entry point used only by ModernMainController's global Add to Shelf. */
    public void handleGlobalAsymmetricShelfAction(String operation) {
        GeneratedAsymmetricKeySummary summary = summaryForGeneration(operation);
        TabPane tabs = tabsForGeneration(operation);
        if (summary == null || tabs == null) {
            String algorithm = operation == null ? "asymmetric" : operation.replace(" Key Generation", "");
            updateStatus("No generated " + algorithm + " key pair available for Clipboard Shelf.");
            return;
        }
        Tab selectedTab = tabs.getSelectionModel().getSelectedItem();
        Object selectedMaterial = selectedTab == null ? null : selectedTab.getUserData();
        if ("PRIVATE".equals(selectedMaterial)) {
            sendAsymmetricKeyToShelf(summary, AsymmetricShelfMaterial.PRIVATE);
        } else if ("PUBLIC".equals(selectedMaterial)) {
            sendAsymmetricKeyToShelf(summary, AsymmetricShelfMaterial.PUBLIC);
        } else {
            updateStatus("Action blocked: select Public Key (PEM) or Private Key (PEM) before adding to Shelf.");
        }
    }

    private void useInSignatures(GeneratedAsymmetricKeySummary summary) {
        if (summary == null || summary.getKeyPair() == null) {
            updateStatus("No key pair available for signatures.");
            return;
        }
        updateStatus("Selected " + summary.getAlgorithm() + " key pair for Digital Signatures");
        if (mainController instanceof ModernMainController modern) {
            modern.useGeneratedKeyPairInSignatures(
                    summary.getKeyPair(), summary.getPublicKeyPem(), summary.getPrivateKeyPem());
        } else if (mainController != null) {
            mainController.navigateTo("Digital Signatures");
        }
    }

    private void useInCertificates(GeneratedAsymmetricKeySummary summary) {
        if (summary == null) {
            updateStatus("No key pair available for certificates.");
            return;
        }
        updateStatus("Selected " + summary.getAlgorithm() + " key pair for Certificates");
        if (mainController != null) {
            mainController.navigateTo("Generate Certificate");
        }
    }

    // RSA Action Handlers
    @FXML public void handleCopyRsaPublicKey() { copyPublicKey(currentRsaSummary); }
    @FXML public void handleCopyRsaPrivateKey() { copyPrivateKey(currentRsaSummary); }
    @FXML public void handleCopyRsaSummary() { copyAsymmetricSummary(currentRsaSummary); }
    @FXML public void handleExportRsaPublicPem() { exportPublicPem(currentRsaSummary, "rsa_public.pem"); }
    @FXML public void handleExportRsaPrivatePem() { exportPrivatePem(currentRsaSummary, "rsa_private.pem"); }
    @FXML public void handleSendRsaPublicToShelf() { sendPublicKeyToShelf(currentRsaSummary); }
    @FXML public void handleSendRsaPrivateToShelf() { sendAsymmetricKeyToShelf(currentRsaSummary, AsymmetricShelfMaterial.PRIVATE); }
    @FXML public void handleUseRsaInCipher() {
        if (currentRsaSummary == null) {
            updateStatus("No RSA key pair available for encryption.");
            return;
        }
        updateStatus("Selected RSA key pair for RSA Cipher");
        if (mainController != null) {
            mainController.navigateTo("Asymmetric Ciphers");
        }
    }
    @FXML public void handleUseRsaInSignatures() { useInSignatures(currentRsaSummary); }
    @FXML public void handleUseRsaInCertificates() { useInCertificates(currentRsaSummary); }
    @FXML public void handleClearRsa() {
        currentRsaSummary = null;
        if (rsaSummaryCard != null) { rsaSummaryCard.setVisible(false); rsaSummaryCard.setManaged(false); }
        if (rsaPublicKeyArea != null) rsaPublicKeyArea.clear();
        if (rsaPrivateKeyArea != null) rsaPrivateKeyArea.clear();
        updateStatus("Cleared RSA key pair");
    }

    // ECDSA Action Handlers
    @FXML public void handleCopyEcdsaPublicKey() { copyPublicKey(currentEcdsaSummary); }
    @FXML public void handleCopyEcdsaPrivateKey() { copyPrivateKey(currentEcdsaSummary); }
    @FXML public void handleCopyEcdsaSummary() { copyAsymmetricSummary(currentEcdsaSummary); }
    @FXML public void handleExportEcdsaPublicPem() { exportPublicPem(currentEcdsaSummary, "ecdsa_public.pem"); }
    @FXML public void handleExportEcdsaPrivatePem() { exportPrivatePem(currentEcdsaSummary, "ecdsa_private.pem"); }
    @FXML public void handleSendEcdsaPublicToShelf() { sendPublicKeyToShelf(currentEcdsaSummary); }
    @FXML public void handleSendEcdsaPrivateToShelf() { sendAsymmetricKeyToShelf(currentEcdsaSummary, AsymmetricShelfMaterial.PRIVATE); }
    @FXML public void handleUseEcdsaInSignatures() { useInSignatures(currentEcdsaSummary); }
    @FXML public void handleUseEcdsaInCertificates() { useInCertificates(currentEcdsaSummary); }
    @FXML public void handleClearEcdsa() {
        currentEcdsaSummary = null;
        if (ecdsaSummaryCard != null) { ecdsaSummaryCard.setVisible(false); ecdsaSummaryCard.setManaged(false); }
        if (ecdsaPublicKeyArea != null) ecdsaPublicKeyArea.clear();
        if (ecdsaPrivateKeyArea != null) ecdsaPrivateKeyArea.clear();
        if (ecdsaFpPublicKeyArea != null) ecdsaFpPublicKeyArea.clear();
        if (ecdsaFpPrivateKeyArea != null) ecdsaFpPrivateKeyArea.clear();
        updateStatus("Cleared ECDSA key pair");
    }

    // DSA Action Handlers
    @FXML public void handleCopyDsaPublicKey() { copyPublicKey(currentDsaSummary); }
    @FXML public void handleCopyDsaPrivateKey() { copyPrivateKey(currentDsaSummary); }
    @FXML public void handleCopyDsaSummary() { copyAsymmetricSummary(currentDsaSummary); }
    @FXML public void handleExportDsaPublicPem() { exportPublicPem(currentDsaSummary, "dsa_public.pem"); }
    @FXML public void handleExportDsaPrivatePem() { exportPrivatePem(currentDsaSummary, "dsa_private.pem"); }
    @FXML public void handleSendDsaPublicToShelf() { sendPublicKeyToShelf(currentDsaSummary); }
    @FXML public void handleSendDsaPrivateToShelf() { sendAsymmetricKeyToShelf(currentDsaSummary, AsymmetricShelfMaterial.PRIVATE); }
    @FXML public void handleUseDsaInSignatures() { useInSignatures(currentDsaSummary); }
    @FXML public void handleUseDsaInCertificates() { useInCertificates(currentDsaSummary); }
    @FXML public void handleClearDsa() {
        currentDsaSummary = null;
        if (dsaSummaryCard != null) { dsaSummaryCard.setVisible(false); dsaSummaryCard.setManaged(false); }
        if (dsaPublicKeyArea != null) dsaPublicKeyArea.clear();
        if (dsaPrivateKeyArea != null) dsaPrivateKeyArea.clear();
        updateStatus("Cleared DSA key pair");
    }

    // Ed25519 Action Handlers
    @FXML public void handleCopyEddsaPublicKey() { copyPublicKey(currentEddsaSummary); }
    @FXML public void handleCopyEddsaPrivateKey() { copyPrivateKey(currentEddsaSummary); }
    @FXML public void handleCopyEddsaSummary() { copyAsymmetricSummary(currentEddsaSummary); }
    @FXML public void handleExportEddsaPublicPem() { exportPublicPem(currentEddsaSummary, "ed25519_public.pem"); }
    @FXML public void handleExportEddsaPrivatePem() { exportPrivatePem(currentEddsaSummary, "ed25519_private.pem"); }
    @FXML public void handleSendEddsaPublicToShelf() { sendPublicKeyToShelf(currentEddsaSummary); }
    @FXML public void handleSendEddsaPrivateToShelf() { sendAsymmetricKeyToShelf(currentEddsaSummary, AsymmetricShelfMaterial.PRIVATE); }
    @FXML public void handleUseEddsaInSignatures() { useInSignatures(currentEddsaSummary); }
    @FXML public void handleUseEddsaInCertificates() { useInCertificates(currentEddsaSummary); }
    @FXML public void handleClearEd25519() {
        currentEddsaSummary = null;
        if (eddsaSummaryCard != null) { eddsaSummaryCard.setVisible(false); eddsaSummaryCard.setManaged(false); }
        if (eddsaPublicKeyArea != null) eddsaPublicKeyArea.clear();
        if (eddsaPrivateKeyArea != null) eddsaPrivateKeyArea.clear();
        if (ed25519PublicKeyArea != null) ed25519PublicKeyArea.clear();
        if (ed25519PrivateKeyArea != null) ed25519PrivateKeyArea.clear();
        updateStatus("Cleared Ed25519 key pair");
    }

    public String getOutputText() {
        // Check Symmetric Results
        if (componentResultsArea != null && !componentResultsArea.getText().isEmpty()) {
            return componentResultsArea.getText();
        }
        if (validationResultArea != null && !validationResultArea.getText().isEmpty()) {
            return validationResultArea.getText();
        }
        if (generatedKeyField != null && !generatedKeyField.getText().isEmpty()) {
            return generatedKeyField.getText();
        }

        // Check Asymmetric (Public/Private)
        StringBuilder sb = new StringBuilder();
        // RSA
        if (rsaPublicKeyArea != null && !rsaPublicKeyArea.getText().isEmpty()) {
            sb.append("RSA Public Key:\n").append(rsaPublicKeyArea.getText()).append("\n\n");
        }
        if (rsaPrivateKeyArea != null && !rsaPrivateKeyArea.getText().isEmpty()) {
            sb.append("RSA Private Key:\n").append(rsaPrivateKeyArea.getText()).append("\n\n");
        }
        // DSA
        if (dsaPublicKeyArea != null && !dsaPublicKeyArea.getText().isEmpty()) {
            sb.append("DSA Public Key:\n").append(dsaPublicKeyArea.getText()).append("\n\n");
        }

        return sb.toString();
    }

    public void loadProfile(com.cryptocarver.model.payments.PaymentProfile p) {
        if (p.getType() == com.cryptocarver.model.payments.PaymentProfile.ProfileType.TR31) {
            java.util.Map<String, String> params = p.getParameters();
            if (tr31VersionCombo != null && params.containsKey("version")) {
                for (String item : tr31VersionCombo.getItems()) {
                    if (item.startsWith(params.get("version").substring(0, 1))) { tr31VersionCombo.setValue(item); break; }
                }
            }
            if (tr31AlgorithmCombo != null && params.containsKey("algorithm")) {
                for (String item : tr31AlgorithmCombo.getItems()) {
                    if (item.startsWith(params.get("algorithm").substring(0, 1))) { tr31AlgorithmCombo.setValue(item); break; }
                }
            }
            if (tr31UsageCombo != null && params.containsKey("usage")) {
                for (String item : tr31UsageCombo.getItems()) {
                    if (item.startsWith(params.get("usage").substring(0, 2))) { tr31UsageCombo.setValue(item); break; }
                }
            }
            if (tr31ModeCombo != null && params.containsKey("mode")) {
                for (String item : tr31ModeCombo.getItems()) {
                    if (item.startsWith(params.get("mode").substring(0, 1))) { tr31ModeCombo.setValue(item); break; }
                }
            }
            if (tr31ExportabilityCombo != null && params.containsKey("exportability")) {
                for (String item : tr31ExportabilityCombo.getItems()) {
                    if (item.startsWith(params.get("exportability").substring(0, 1))) { tr31ExportabilityCombo.setValue(item); break; }
                }
            }
            if (tr31KbpkExportField != null && p.getInputs().containsKey("kbpk")) {
                tr31KbpkExportField.setText(p.getInputs().get("kbpk"));
            }
            if (tr31KeyToWrapField != null && p.getInputs().containsKey("keyToWrap")) {
                tr31KeyToWrapField.setText(p.getInputs().get("keyToWrap"));
            }
            if (tr31OptionalBlocksField != null && p.getInputs().containsKey("optionalBlocks")) {
                tr31OptionalBlocksField.setText(p.getInputs().get("optionalBlocks"));
            }
            updateStatus("Loaded TR-31 profile: " + p.getName());
        }
    }

    private void initializeKeyLab() {
        if (keyLabStatusFilter != null) {
            keyLabStatusFilter.getItems().setAll("Active Only", "Archived Only", "All Keys");
            keyLabStatusFilter.setValue("Active Only");
            keyLabStatusFilter.setOnAction(e -> refreshKeyLabTable());
        }

        if (keyLabNewAlgoCombo != null) {
            keyLabNewAlgoCombo.getItems().setAll("AES", "3DES", "DES", "ChaCha20");
            keyLabNewAlgoCombo.setValue("AES");
            keyLabNewAlgoCombo.setOnAction(e -> updateNewKeySizes());
        }

        if (keyLabNewSizeCombo != null) {
            updateNewKeySizes();
        }

        if (keyLabSearchField != null) {
            keyLabSearchField.textProperty().addListener((obs, old, val) -> refreshKeyLabTable());
        }

        if (keyLabTable != null) {
            TableColumn<KeyMaterial, String> nameCol = new TableColumn<>("Name");
            nameCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getName()));
            nameCol.setPrefWidth(120);

            TableColumn<KeyMaterial, String> algoCol = new TableColumn<>("Algorithm");
            algoCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getAlgorithm()));
            algoCol.setPrefWidth(80);

            TableColumn<KeyMaterial, String> bitsCol = new TableColumn<>("Bits");
            bitsCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(String.valueOf(d.getValue().getSize())));
            bitsCol.setPrefWidth(50);

            TableColumn<KeyMaterial, String> kcvCol = new TableColumn<>("KCV");
            kcvCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getKcv()));
            kcvCol.setPrefWidth(60);

            TableColumn<KeyMaterial, String> originCol = new TableColumn<>("Origin");
            originCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getOrigin()));
            originCol.setPrefWidth(80);

            TableColumn<KeyMaterial, String> statusCol = new TableColumn<>("Status");
            statusCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getStatus()));
            statusCol.setPrefWidth(70);

            keyLabTable.getColumns().setAll(nameCol, algoCol, bitsCol, kcvCol, originCol, statusCol);

            keyLabTable.setRowFactory(tv -> new TableRow<KeyMaterial>() {
                @Override
                protected void updateItem(KeyMaterial item, boolean empty) {
                    super.updateItem(item, empty);
                    if (item == null || empty) {
                        setStyle("");
                        getStyleClass().removeAll("key-row-archived", "key-row-metadata-only", "key-row-non-exportable");
                    } else {
                        getStyleClass().removeAll("key-row-archived", "key-row-metadata-only", "key-row-non-exportable");
                        if ("ARCHIVED".equalsIgnoreCase(item.getStatus())) {
                            getStyleClass().add("key-row-archived");
                        } else if (!item.hasKeyMaterial()) {
                            getStyleClass().add("key-row-metadata-only");
                        }
                        if (item.getExportability() == com.cryptocarver.crypto.hsm.KeyExportability.NON_EXPORTABLE) {
                            getStyleClass().add("key-row-non-exportable");
                        }
                    }
                }
            });

            keyLabTable.getSelectionModel().selectedItemProperty().addListener((obs, old, val) -> showKeyLabDetails(val));
        }

        updateVisibilityControls();
        refreshKeyLabTable();
    }

    public void updateVisibilityControls() {
        boolean isFullLab = com.cryptocarver.model.AppSettings.getInstance().getSecretVisibilityProfile() == com.cryptocarver.model.SecretVisibilityProfile.FULL_LAB;
        if (rsaSendPrivateShelfBtn != null) rsaSendPrivateShelfBtn.setDisable(!isFullLab);
        if (ecdsaSendPrivateShelfBtn != null) ecdsaSendPrivateShelfBtn.setDisable(!isFullLab);
        if (dsaSendPrivateShelfBtn != null) dsaSendPrivateShelfBtn.setDisable(!isFullLab);
        if (eddsaSendPrivateShelfBtn != null) eddsaSendPrivateShelfBtn.setDisable(!isFullLab);
        if (keyLabImportBytesField != null) {
            keyLabImportBytesField.setDisable(!isFullLab);
            if (!isFullLab) {
                keyLabImportBytesField.setText("");
                keyLabImportBytesField.setPromptText("Importing secret key material requires FULL_LAB");
            } else {
                keyLabImportBytesField.setPromptText("Or paste raw key bytes in hex...");
            }
        }
        if (keyLabImportBtn != null) {
            keyLabImportBtn.setDisable(!isFullLab);
        }
    }

    private void updateNewKeySizes() {
        if (keyLabNewSizeCombo == null || keyLabNewAlgoCombo == null) return;
        String algo = keyLabNewAlgoCombo.getValue();
        if ("AES".equals(algo)) {
            keyLabNewSizeCombo.getItems().setAll("128", "192", "256");
            keyLabNewSizeCombo.setValue("256");
        } else if ("3DES".equals(algo)) {
            keyLabNewSizeCombo.getItems().setAll("128 (2key)", "192 (3key)");
            keyLabNewSizeCombo.setValue("192 (3key)");
        } else if ("DES".equals(algo)) {
            keyLabNewSizeCombo.getItems().setAll("64");
            keyLabNewSizeCombo.setValue("64");
        } else if ("ChaCha20".equals(algo)) {
            keyLabNewSizeCombo.getItems().setAll("256");
            keyLabNewSizeCombo.setValue("256");
        }
    }

    public void refreshKeyLabTable() {
        if (keyLabTable == null) return;

        boolean includeArchived = !"Active Only".equals(keyLabStatusFilter.getValue());
        boolean onlyArchived = "Archived Only".equals(keyLabStatusFilter.getValue());
        String query = keyLabSearchField != null ? keyLabSearchField.getText().toLowerCase(java.util.Locale.ROOT) : "";

        java.util.List<KeyMaterial> filtered = new java.util.ArrayList<>();
        for (String id : com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().listKeyIds(true)) {
            KeyMaterial km = com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().getKeyMetadata(id);
            if (km == null) continue;

            if (onlyArchived && !"ARCHIVED".equalsIgnoreCase(km.getStatus())) continue;
            if (!includeArchived && "ARCHIVED".equalsIgnoreCase(km.getStatus())) continue;

            if (!query.isEmpty()) {
                boolean matches = km.getName().toLowerCase().contains(query) ||
                                  km.getAlgorithm().toLowerCase().contains(query) ||
                                  km.getId().toLowerCase().contains(query);
                if (!matches) continue;
            }

            filtered.add(km);
        }

        keyLabTable.getItems().setAll(filtered);
    }

    private void showKeyLabDetails(KeyMaterial km) {
        if (km == null) {
            clearKeyLabDetails();
            return;
        }

        keyLabDetailIdField.setText(km.getId());
        keyLabDetailNameField.setText(km.getName());
        keyLabDetailAlgoLabel.setText(km.getAlgorithm());
        keyLabDetailBitsLabel.setText(km.getSize() + " bits");
        keyLabDetailKcvLabel.setText(km.getKcv());
        keyLabDetailFingerprintLabel.setText(km.getFingerprint());
        keyLabDetailOriginLabel.setText(km.getOrigin());

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        keyLabDetailCreatedLabel.setText(sdf.format(new java.util.Date(km.getCreated())));
        keyLabDetailModifiedLabel.setText(sdf.format(new java.util.Date(km.getModified())));
        keyLabDetailStatusLabel.setText(km.getStatus() + (km.hasKeyMaterial() ? "" : " (Metadata-only)"));

        boolean isFullLab = com.cryptocarver.model.AppSettings.getInstance().getSecretVisibilityProfile() == com.cryptocarver.model.SecretVisibilityProfile.FULL_LAB;
        boolean isExportable = km.getExportability() == com.cryptocarver.crypto.hsm.KeyExportability.EXPORTABLE;
        keyLabRevealBtn.setDisable(!isFullLab || !isExportable);

        keyLabDetailValueField.setText("************************");

        if ("ARCHIVED".equalsIgnoreCase(km.getStatus())) {
            keyLabArchiveBtn.setText("Restore");
        } else {
            keyLabArchiveBtn.setText("Archive");
        }
    }

    private void clearKeyLabDetails() {
        keyLabDetailIdField.clear();
        keyLabDetailNameField.clear();
        keyLabDetailAlgoLabel.setText("N/A");
        keyLabDetailBitsLabel.setText("N/A");
        keyLabDetailKcvLabel.setText("N/A");
        keyLabDetailFingerprintLabel.setText("N/A");
        keyLabDetailOriginLabel.setText("N/A");
        keyLabDetailCreatedLabel.setText("N/A");
        keyLabDetailModifiedLabel.setText("N/A");
        keyLabDetailStatusLabel.setText("N/A");
        keyLabDetailValueField.clear();
        keyLabRevealBtn.setDisable(true);
        keyLabArchiveBtn.setText("Archive");
    }

    @FXML
    private void handleKeyLabGenerate() {
        try {
            String name = keyLabNewNameField.getText().trim();
            if (name.isEmpty()) {
                showError("Validation Error", "Please specify a name for the new key");
                return;
            }
            String algo = keyLabNewAlgoCombo.getValue();
            String sizeStr = keyLabNewSizeCombo.getValue();
            int size = 256;
            if (sizeStr != null) {
                if (sizeStr.contains("128")) size = 128;
                else if (sizeStr.contains("192")) size = 192;
                else if (sizeStr.contains("64")) size = 64;
            }

            String keyTypeMap = "AES";
            if ("3DES".equals(algo)) keyTypeMap = "3DES";
            else if ("DES".equals(algo)) keyTypeMap = "DES";
            else if ("ChaCha20".equals(algo)) keyTypeMap = "AES-256";

            byte[] keyBytes = com.cryptocarver.crypto.KeyOperations.generateKey(keyTypeMap, true);
            if ("AES".equals(algo) && keyBytes.length != (size / 8)) {
                byte[] temp = new byte[size / 8];
                System.arraycopy(keyBytes, 0, temp, 0, temp.length);
                keyBytes = temp;
            }

            String realAlgo = algo;
            if ("ChaCha20".equals(algo)) realAlgo = "ChaCha20";

            javax.crypto.SecretKey spec = new javax.crypto.spec.SecretKeySpec(keyBytes, realAlgo);
            String id = UUID.randomUUID().toString();
            KeyMaterial km = com.cryptocarver.crypto.hsm.KeyMaterialFactory.fromSecretKey(
                    id, spec, com.cryptocarver.crypto.hsm.KeyExportability.EXPORTABLE,
                    java.util.Set.of(com.cryptocarver.crypto.hsm.KeyUsage.ENCRYPT, com.cryptocarver.crypto.hsm.KeyUsage.DECRYPT, com.cryptocarver.crypto.hsm.KeyUsage.MAC)
            );
            km.setName(name);
            km.setModified(System.currentTimeMillis());

            var existing = com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().findKeyByFingerprint(km.getFingerprint());
            if (existing != null) {
                showError("Duplicate Key", "A key with this fingerprint already exists in the Lab: " + existing.getName() + " (" + existing.getId() + ")");
                return;
            }

            com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().importKey(km);
            refreshKeyLabTable();
            keyLabTable.getSelectionModel().select(km);
            keyLabTable.scrollTo(km);
            keyLabTable.requestFocus();
            keyLabNewNameField.clear();
            hsmRefreshCallback.run();
            if (mainController != null) {
                mainController.updateStatus("Generated and registered key: " + name);
            }
        } catch (Exception e) {
            showError("Generation Error", "Failed to generate key: " + e.getMessage());
        }
    }

    @FXML
    private void handleKeyLabImport() {
        if (com.cryptocarver.model.AppSettings.getInstance().getSecretVisibilityProfile() != com.cryptocarver.model.SecretVisibilityProfile.FULL_LAB) {
            showError("Security Error", "Importing secret key material requires FULL_LAB visibility profile.");
            return;
        }
        try {
            String name = keyLabNewNameField.getText().trim();
            if (name.isEmpty()) {
                showError("Validation Error", "Please specify a name for the imported key");
                return;
            }
            String hex = keyLabImportBytesField.getText().trim();
            if (hex.isEmpty()) {
                showError("Validation Error", "Please enter key bytes in hexadecimal format");
                return;
            }
            byte[] bytes = com.cryptocarver.util.DataConverter.hexToBytes(hex);
            String algo = keyLabNewAlgoCombo.getValue();

            javax.crypto.SecretKey spec = new javax.crypto.spec.SecretKeySpec(bytes, algo);
            String id = UUID.randomUUID().toString();
            KeyMaterial km = com.cryptocarver.crypto.hsm.KeyMaterialFactory.fromSecretKey(
                    id, spec, com.cryptocarver.crypto.hsm.KeyExportability.EXPORTABLE,
                    java.util.Set.of(com.cryptocarver.crypto.hsm.KeyUsage.ENCRYPT, com.cryptocarver.crypto.hsm.KeyUsage.DECRYPT, com.cryptocarver.crypto.hsm.KeyUsage.MAC)
            );
            km.setName(name);
            km.setModified(System.currentTimeMillis());

            var existing = com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().findKeyByFingerprint(km.getFingerprint());
            if (existing != null) {
                showError("Duplicate Key", "A key with this fingerprint already exists in the Lab: " + existing.getName() + " (" + existing.getId() + ")");
                return;
            }

            com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().importKey(km);
            refreshKeyLabTable();
            keyLabTable.getSelectionModel().select(km);
            keyLabTable.scrollTo(km);
            keyLabTable.requestFocus();
            keyLabNewNameField.clear();
            keyLabImportBytesField.clear();
            hsmRefreshCallback.run();
            if (mainController != null) {
                mainController.updateStatus("Imported key: " + name);
            }
        } catch (Exception e) {
            showError("Import Error", "Failed to import key: " + e.getMessage());
        }
    }

    @FXML
    private void handleKeyLabReveal() {
        KeyMaterial km = keyLabTable.getSelectionModel().getSelectedItem();
        if (km == null) return;

        if (com.cryptocarver.model.AppSettings.getInstance().getSecretVisibilityProfile() != com.cryptocarver.model.SecretVisibilityProfile.FULL_LAB) {
            showError("Security Restriction", "Revealing key material is only allowed in FULL_LAB security profile.");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Warning: Reveal Secret Key");
        alert.setHeaderText("Are you sure you want to reveal raw secret key bytes?");
        alert.setContentText("Warning: Exporting or displaying cleartext key material violates production security standards. Only proceed in isolated lab environments.");

        java.util.Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                byte[] keyBytes = com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().revealExportableKeyForFullLab(km.getId());
                if (keyBytes != null) {
                    keyLabDetailValueField.setText(com.cryptocarver.util.DataConverter.bytesToHex(keyBytes).toUpperCase());
                } else {
                    keyLabDetailValueField.setText("[No raw key material available / Opaque key]");
                }
            } catch (Exception e) {
                showError("Security Restriction", e.getMessage());
            }
        }
    }

    @FXML
    private void handleKeyLabCopyId() {
        KeyMaterial km = keyLabTable.getSelectionModel().getSelectedItem();
        if (km == null) return;
        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(km.getId());
        clipboard.setContent(content);
        if (mainController != null) {
            mainController.updateStatus("Copied key ID to clipboard: " + km.getId());
        }
    }

    @FXML
    private void handleKeyLabSaveMetadata() {
        KeyMaterial km = keyLabTable.getSelectionModel().getSelectedItem();
        if (km == null) return;

        String newName = keyLabDetailNameField.getText().trim();
        if (newName.isEmpty()) {
            showError("Validation Error", "Name cannot be empty");
            return;
        }

        com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().updateKeyMetadata(km.getId(), newName, km.getStatus());
        refreshKeyLabTable();
        for (KeyMaterial item : keyLabTable.getItems()) {
            if (item.getId().equals(km.getId())) {
                keyLabTable.getSelectionModel().select(item);
                break;
            }
        }
        hsmRefreshCallback.run();
        if (mainController != null) {
            mainController.showInfo("Success", "Key metadata updated");
        }
    }

    @FXML
    private void handleKeyLabArchive() {
        KeyMaterial km = keyLabTable.getSelectionModel().getSelectedItem();
        if (km == null) return;

        boolean willArchive = !"ARCHIVED".equalsIgnoreCase(km.getStatus());
        String actionText = willArchive ? "archive" : "restore";
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm " + (willArchive ? "Archive" : "Restore"));
        alert.setHeaderText((willArchive ? "Archive" : "Restore") + " Key: " + km.getName());
        alert.setContentText("Are you sure you want to " + actionText + " this key? "
            + (willArchive ? "Archived keys are hidden from standard operations but kept in history." : "This key will be active again."));

        java.util.Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().archiveKey(km.getId());
            refreshKeyLabTable();
            var reloaded = com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().getKeyMetadata(km.getId());
            if (reloaded != null) {
                keyLabTable.getSelectionModel().select(reloaded);
            }
            hsmRefreshCallback.run();
        }
    }

    @FXML
    private void handleKeyLabDelete() {
        KeyMaterial km = keyLabTable.getSelectionModel().getSelectedItem();
        if (km == null) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Deletion");
        alert.setHeaderText("Delete Key: " + km.getName());
        alert.setContentText("Are you sure you want to permanently delete this key from the Lab? This action cannot be undone.");

        java.util.Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().deleteKey(km.getId());
            refreshKeyLabTable();
            keyLabTable.getSelectionModel().clearSelection();
            hsmRefreshCallback.run();
            if (mainController != null) {
                mainController.updateStatus("Deleted key: " + km.getName());
            }
        }
    }

    @FXML
    private void handleImportKeyLabMetadata() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Key Lab Metadata Manifest");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
        java.io.File file = chooser.showOpenDialog(keyLabTable.getScene().getWindow());
        if (file != null) {
            try {
                com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().importMetadata(file);
                refreshKeyLabTable();
                hsmRefreshCallback.run();
                if (mainController != null) {
                    mainController.showInfo("Success", "Imported key metadata manifest successfully.");
                }
            } catch (Exception e) {
                showError("Import Error", "Failed to import metadata: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleExportKeyLabMetadata() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Key Lab Metadata Manifest (No Secrets)");
        chooser.setInitialFileName("key-lab-metadata.json");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
        java.io.File file = chooser.showSaveDialog(keyLabTable.getScene().getWindow());
        if (file != null) {
            try {
                com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().exportMetadata(file);
                if (mainController != null) {
                    mainController.showInfo("Success", "Exported metadata successfully to " + file.getName());
                }
            } catch (Exception e) {
                showError("Export Error", "Failed to export metadata: " + e.getMessage());
            }
        }
    }

    public void selectKeyInKeyLab(String keyId) {
        if (keyLabPane != null) {
            keyLabPane.setExpanded(true);
        }
        if (keyLabStatusFilter != null) {
            keyLabStatusFilter.setValue("All Keys");
        }
        refreshKeyLabTable();
        if (keyLabTable != null) {
            for (KeyMaterial km : keyLabTable.getItems()) {
                if (km.getId().equals(keyId)) {
                    keyLabTable.getSelectionModel().select(km);
                    keyLabTable.scrollTo(km);
                    keyLabTable.requestFocus();
                    break;
                }
            }
        }
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

    private void setupHexValidation(TextArea field) {
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
}
