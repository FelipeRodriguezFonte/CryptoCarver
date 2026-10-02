package com.cryptocarver.ui;

import com.cryptocarver.crypto.*;
import com.cryptocarver.crypto.hsm.KeyMaterial;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller for Keys tab - Enhanced with asymmetric cryptography
 *
 * @author Felipe
 */
public class KeysController {

    private final KeysWorkspaceState workspace = new KeysWorkspaceState();

    private PaymentKeyBlockCoordinator paymentKeyBlockCoordinator;

    private PaymentKeyBlockCoordinator paymentKeyBlockCoordinator() {
        if (paymentKeyBlockCoordinator == null) {
            paymentKeyBlockCoordinator = new PaymentKeyBlockCoordinator(
                    () -> new PaymentKeyBlockCoordinator.View(
                            thalesLmkField, thalesKeyTypeField, thalesSchemeCombo, thalesClearKeyField,
                            thalesCryptogramField, thalesCheckValueField, thalesComponentCheck,
                            thalesResultArea, keyBlockInputArea, keyBlockResultArea, keyBlockLmkField,
                            atallaTemplateCombo, atalla0Combo, atalla1Combo, atalla2Combo, atalla3Combo,
                            atalla4Combo, atalla5Combo, atalla6Combo, atalla7Combo, atallaHeaderField,
                            atallaMeaningArea, atallaMfkField, atallaKeyField, atallaBlockArea,
                            atallaResultArea),
                    () -> mainController);
        }
        return paymentKeyBlockCoordinator;
    }

    private KeyLabCoordinator keyLabCoordinator;

    private KeyLabCoordinator keyLabCoordinator() {
        if (keyLabCoordinator == null) {
            keyLabCoordinator = new KeyLabCoordinator(
                    () -> new KeyLabCoordinator.View(
                            keyLabPane, keyLabSearchField, keyLabStatusFilterCombo, keyLabTable,
                            keyLabNewNameField, keyLabNewAlgoCombo, keyLabNewSizeCombo, keyLabImportBytesField,
                            keyLabImportBtn, keyLabDetailIdField, keyLabDetailNameField, keyLabDetailAlgoLabel,
                            keyLabDetailBitsLabel, keyLabUsageEncryptCheck, keyLabUsageDecryptCheck,
                            keyLabUsageMacCheck, keyLabUsageWrapCheck, keyLabUsageUnwrapCheck,
                            keyLabDetailExportabilityLabel, keyLabDetailKcvLabel, keyLabDetailFingerprintLabel,
                            keyLabDetailOriginLabel, keyLabDetailCreatedLabel, keyLabDetailModifiedLabel,
                            keyLabDetailStatusLabel, keyLabDetailValueField, keyLabRevealBtn, keyLabArchiveBtn,
                            keyLabUseCipherBtn, keyLabUseMacBtn, summarySavedStatusLabel,
                            rsaSendPrivateShelfBtn, ecdsaSendPrivateShelfBtn, dsaSendPrivateShelfBtn,
                            eddsaSendPrivateShelfBtn),
                    () -> mainController, workspace, () -> hsmRefreshCallback.run(), this::selectedKcvLength);
        }
        return keyLabCoordinator;
    }

    private KeySummaryCoordinator keySummaryCoordinator;

    private KeySummaryCoordinator keySummaryCoordinator() {
        if (keySummaryCoordinator == null) {
            keySummaryCoordinator = new KeySummaryCoordinator(
                    () -> new KeySummaryCoordinator.View(
                            keyTypeCombo, saveGeneratedKeyButton, rsaKeySizeCombo, ecdsaCurveCombo,
                            dsaKeySizeCombo, ecdsaPublicKeyArea, ecdsaPrivateKeyArea, eddsaPublicKeyArea,
                            eddsaPrivateKeyArea, rsaKeyMaterialTabs, ecdsaKeyMaterialTabs, dsaKeyMaterialTabs,
                            eddsaKeyMaterialTabs, generatedKeyField, generatedKeySummaryCard, summaryAlgoLabel,
                            summaryLengthLabel, summaryKcvLabel, summaryFingerprintLabel, summaryParityLabel,
                            summaryOriginLabel, summarySavedStatusLabel, validationPane, useFourByteKcvCheck,
                            rsaSummaryCard, ecdsaSummaryCard, dsaSummaryCard, eddsaSummaryCard, keyInputField,
                            validationResultArea, componentResultsArea, component1Field, component2Field,
                            component3Field, rsaPublicKeyArea, rsaPrivateKeyArea, dsaPublicKeyArea,
                            dsaPrivateKeyArea, ecdsaFpPublicKeyArea, ecdsaFpPrivateKeyArea,
                            ed25519PublicKeyArea, ed25519PrivateKeyArea),
                    () -> mainController, workspace, this::handleValidateKey);
        }
        return keySummaryCoordinator;
    }

    private SymmetricKeyCoordinator symmetricKeyCoordinator;

    private SymmetricKeyCoordinator symmetricKeyCoordinator() {
        if (symmetricKeyCoordinator == null) {
            symmetricKeyCoordinator = new SymmetricKeyCoordinator(
                    () -> new SymmetricKeyCoordinator.View(
                            keyTypeCombo, forceOddParityCheck, generatedKeyField, saveGeneratedKeyButton,
                            keyInputField, validationResultArea, numComponentsCombo, keyToSplitField,
                            componentResultsArea, component1Field, component2Field, component3Field,
                            component4Field, component5Field),
                    () -> mainController, workspace, this::updateGeneratedKeySummaryCard, this::selectedKcvLength);
        }
        return symmetricKeyCoordinator;
    }

    private KeyStoreCoordinator keyStoreCoordinator;

    private KeyStoreCoordinator keyStoreCoordinator() {
        if (keyStoreCoordinator == null) {
            keyStoreCoordinator = new KeyStoreCoordinator(
                    () -> new KeyStoreCoordinator.View(
                            keysRoot, pkcs11ProfilesController, icsfTokenPaneController,
                            icsfBatchPaneController, icsfKeyWrapPaneController, keyMaterialInputArea,
                            keyMaterialReportArea, keyComparePublicArea, keyComparePrivateArea,
                            keyCompareResultArea, keyStoreTypeCombo, keyStorePasswordField,
                            keyStoreUnsafeExtractCheck, keyStorePathField, keyStoreReportArea,
                            keyStoreProfileCombo, keyStoreProfileNameField, pkcs11NameField,
                            pkcs11LibraryField, pkcs11SlotField, pkcs11PinField, pkcs11ProfileCombo,
                            pkcs11ReportArea, pkcs11SigningKeyCombo, pkcs11SignatureAlgorithmCombo,
                            pkcs11DataArea, pkcs11SignatureArea, pkcs11CertificateAliasCombo,
                            pkcs11CertificateArea, pkcs11JwtAlgorithmCombo, pkcs11JwtPayloadArea,
                            pkcs11JwtOutputArea, pkcs11CmsDataArea, pkcs11CmsDetachedCheck,
                            pkcs11CmsOutputArea, pkcs11WrappingKeyCombo, pkcs11WrapKeyCombo,
                            pkcs11WrapTransformationCombo, pkcs11WrapResultArea, pkcs11UnwrappingKeyCombo,
                            pkcs11UnwrapDataArea, pkcs11UnwrapTransformationCombo, pkcs11UnwrapAlgorithmField,
                            pkcs11UnwrapTypeCombo, pkcs11UnwrapResultArea),
                    () -> mainController, () -> hsmRefreshCallback.run());
        }
        return keyStoreCoordinator;
    }

    private CertificateCoordinator certificateCoordinator;

    private CertificateCoordinator certificateCoordinator() {
        if (certificateCoordinator == null) {
            certificateCoordinator = new CertificateCoordinator(
                    () -> new CertificateCoordinator.View(
                            certCNField, certOrgField, certOUField, certLocalityField, certStateField,
                            certCountryField, certEmailField, certValidityField, certKeyTypeCombo,
                            certSignAlgoCombo, certOutputArea, certSanDnsField, certSanIpField,
                            certRootCaCheck, certInputArea, certParseResultArea, certCompareLeftArea,
                            certCompareRightArea, certCompareResultArea, certIssueCsrArea, certIssueCaCertArea,
                            certIssueCaKeyArea, certIssueValidityField, certIssueSignatureField,
                            certIssueResultArea, certIssueProfileCombo, certIssuePathLengthField,
                            crlIssuerCertArea, crlIssuerKeyArea, crlExistingCrlArea, crlRevokeSerialField,
                            crlRevokeReasonCombo, crlResultArea, valCertInput, valIssuerInput, valResultArea),
                    () -> mainController, this::requirePkcs11SigningAlias);
        }
        return certificateCoordinator;
    }

    private Tr31Coordinator tr31Coordinator;

    private Tr31Coordinator tr31Coordinator() {
        if (tr31Coordinator == null) {
            tr31Coordinator = new Tr31Coordinator(
                    () -> new Tr31Coordinator.View(
                            tr31KbpkExportField, tr31KeyToWrapField, tr31UsageCombo, tr31AlgorithmCombo,
                            tr31ModeCombo, tr31VersionCombo, tr31ExportabilityCombo, tr31OptionalBlocksField,
                            tr31OptionalBlockCombo, tr31ExportResultArea, tr31KbpkImportField,
                            tr31KeyBlockField, tr31KeyLengthField, tr31ImportResultArea),
                    () -> mainController, this::updateStatus, this::t);
        }
        return tr31Coordinator;
    }

    private RsaKeyExchangeCoordinator rsaKexCoordinator;

    private RsaKeyExchangeCoordinator rsaKexCoordinator() {
        if (rsaKexCoordinator == null) {
            rsaKexCoordinator = new RsaKeyExchangeCoordinator(
                    () -> new RsaKeyExchangeCoordinator.View(
                            rsaKexRecipientPemArea, rsaKexKeyToWrapField, rsaKexExportProfileCombo,
                            rsaKexIncludeEnvelopeCheck, rsaKexEnvelopeFieldsBox, rsaKexKidField,
                            rsaKexKeyVersionField, rsaKexExportResultArea, rsaKexPrivateKeyArea,
                            rsaKexWrappedDataArea, rsaKexImportProfileCombo, rsaKexImportResultArea),
                    () -> mainController, this::updateStatus, this::t);
        }
        return rsaKexCoordinator;
    }

    private Tr34Coordinator tr34Coordinator;

    private Tr34Coordinator tr34Coordinator() {
        if (tr34Coordinator == null) {
            tr34Coordinator = new Tr34Coordinator(
                    () -> new Tr34Coordinator.View(
                            tr34SenderPrivateKeyArea, tr34SenderCertArea, tr34ReceiverCertArea,
                            tr34KeyToDistributeField, tr34KeyIdField, tr34BindingNonceField,
                            tr34IncludeEnvelopeCheck, tr34DistributeResultArea, tr34ReceiverPrivateKeyArea,
                            tr34ExpectedSenderCertArea, tr34DistributedDataArea, tr34ChallengeNonceField,
                            tr34ReceiveResultArea),
                    () -> mainController, this::updateStatus, this::t);
        }
        return tr34Coordinator;
    }

    private AsymmetricKeyGenerationCoordinator asymmetricKeyGenerationCoordinator;

    private AsymmetricKeyGenerationCoordinator asymmetricKeyGenerationCoordinator() {
        if (asymmetricKeyGenerationCoordinator == null) {
            asymmetricKeyGenerationCoordinator = new AsymmetricKeyGenerationCoordinator(
                    () -> new AsymmetricKeyGenerationCoordinator.View(
                            rsaSummaryCard, rsaSummaryAlgoLabel, rsaSummaryFingerprintLabel,
                            rsaSummaryPubLenLabel, rsaSummaryPrivLenLabel, rsaSummaryCreatedLabel,
                            rsaSummarySavedStatusLabel, ecdsaSummaryCard, ecdsaSummaryAlgoLabel,
                            ecdsaSummaryFingerprintLabel, ecdsaSummaryPubLenLabel, ecdsaSummaryPrivLenLabel,
                            ecdsaSummaryCreatedLabel, ecdsaSummarySavedStatusLabel, dsaSummaryCard,
                            dsaSummaryAlgoLabel, dsaSummaryFingerprintLabel, dsaSummaryPubLenLabel,
                            dsaSummaryPrivLenLabel, dsaSummaryCreatedLabel, dsaSummarySavedStatusLabel,
                            eddsaSummaryCard, eddsaSummaryAlgoLabel, eddsaSummaryFingerprintLabel,
                            eddsaSummaryPubLenLabel, eddsaSummaryPrivLenLabel, eddsaSummaryCreatedLabel,
                            eddsaSummarySavedStatusLabel, rsaKeySizeCombo, rsaPublicKeyArea, rsaPrivateKeyArea,
                            dsaKeySizeCombo, dsaPublicKeyArea, dsaPrivateKeyArea, ecdsaFpCurveCombo,
                            ecdsaFpPublicKeyArea, ecdsaFpPrivateKeyArea, ed25519PublicKeyArea,
                            ed25519PrivateKeyArea, rsaGenerateBtn, dsaGenerateBtn),
                    () -> mainController, this::showError, this::updateStatus, this::t, this::acceptAsymmetricGeneration);
        }
        return asymmetricKeyGenerationCoordinator;
    }

    private void acceptAsymmetricGeneration(AsymmetricKeyGenerationCoordinator.StateUpdate update) { keySummaryCoordinator().acceptAsymmetricGeneration(update); }

    private KdfKeyWrapCoordinator kdfKeyWrapCoordinator;

    private KdfKeyWrapCoordinator kdfKeyWrapCoordinator() {
        if (kdfKeyWrapCoordinator == null) {
            kdfKeyWrapCoordinator = new KdfKeyWrapCoordinator(
                    () -> new KdfKeyWrapCoordinator.View(
                            kdfAlgorithmCombo, kdfInputFormatCombo, kdfSaltFormatCombo, kdfInfoFormatCombo,
                            kdfInputField, kdfSaltField, kdfInfoField, kdfIterationsField,
                            kdfOutputLengthField, kdfResultArea, kdfInputHelpLabel, kdfValidationLabel,
                            kdfIterationsLabel, kdfSaltBox, kdfInfoBox, kdfInputBadgeLabel, kdfSaltBadgeLabel,
                            kdfInfoBadgeLabel, keyWrapModeCombo, keyWrapUnwrapCheck, keyWrapKekField,
                            keyWrapDataField, keyWrapResultArea),
                    () -> mainController, this::showError, this::updateStatus, this::t);
        }
        return kdfKeyWrapCoordinator;
    }

    private CmsCoordinator cmsCoordinator;

    private CmsCoordinator cmsCoordinator() {
        if (cmsCoordinator == null) {
            cmsCoordinator = new CmsCoordinator(
                    () -> mainController, this::showError, this::updateStatus, this::t);
        }
        return cmsCoordinator;
    }

    private CertificateChainCoordinator certificateChainCoordinator;

    private CertificateChainCoordinator certificateChainCoordinator() {
        if (certificateChainCoordinator == null) {
            certificateChainCoordinator = new CertificateChainCoordinator(
                    () -> mainController, this::showError, this::updateStatus, this::t);
        }
        return certificateChainCoordinator;
    }

    private String t(String key, Object... args) {
        return com.cryptocarver.service.I18nService.getInstance().text(key, args);
    }

    @FXML private VBox keysRoot;
    @FXML private TitledPane pkcs11Profiles;
    @FXML private Pkcs11ProfilesController pkcs11ProfilesController;
    @FXML private TitledPane icsfTokenPane;
    @FXML private IcsfTokenController icsfTokenPaneController;
    @FXML private TitledPane icsfBatchPane;
    @FXML private IcsfBatchController icsfBatchPaneController;
    @FXML private TitledPane icsfKeyWrapPane;
    @FXML private IcsfKeyWrapController icsfKeyWrapPaneController;
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

    @FXML private TitledPane keyLabPane;
    @FXML private TextField keyLabSearchField;
    @FXML private ComboBox<String> keyLabStatusFilterCombo;
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
    @FXML private CheckBox keyLabUsageEncryptCheck;
    @FXML private CheckBox keyLabUsageDecryptCheck;
    @FXML private CheckBox keyLabUsageMacCheck;
    @FXML private CheckBox keyLabUsageWrapCheck;
    @FXML private CheckBox keyLabUsageUnwrapCheck;
    @FXML private Label keyLabDetailExportabilityLabel;
    @FXML private Label keyLabDetailKcvLabel;
    @FXML private Label keyLabDetailFingerprintLabel;
    @FXML private Label keyLabDetailOriginLabel;
    @FXML private Label keyLabDetailCreatedLabel;
    @FXML private Label keyLabDetailModifiedLabel;
    @FXML private Label keyLabDetailStatusLabel;
    @FXML private TextField keyLabDetailValueField;
    @FXML private Button keyLabRevealBtn;
    @FXML private Button keyLabArchiveBtn;
    @FXML private Button keyLabUseCipherBtn;
    @FXML private Button keyLabUseMacBtn;

    private StatusReporter mainController;
    private Runnable hsmRefreshCallback = () -> { };

    @FXML private ComboBox<String> keyTypeCombo;
    @FXML private javafx.scene.control.CheckBox forceOddParityCheck;
    @FXML private TextArea generatedKeyField;
    @FXML private Button saveGeneratedKeyButton;

    @FXML private VBox generatedKeySummaryCard;
    @FXML private Label summaryAlgoLabel;
    @FXML private Label summaryLengthLabel;
    @FXML private Label summaryKcvLabel;
    @FXML private Label summaryFingerprintLabel;
    @FXML private Label summaryParityLabel;
    @FXML private Label summaryOriginLabel;
    @FXML private Label summarySavedStatusLabel;
    @FXML private TitledPane validationPane;
    @FXML private CheckBox useFourByteKcvCheck;
    @FXML private Button copyGeneratedKeyButton;
    @FXML private Button copyGeneratedKcvButton;
    @FXML private Button copyGeneratedSummaryButton;
    @FXML private Button saveGeneratedSummaryButton;
    @FXML private Button openValidationButton;

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

    @FXML private TextField keyInputField;
    @FXML private TextArea validationResultArea;

    @FXML private TextArea keyMaterialInputArea;
    @FXML private TextArea keyMaterialReportArea;
    @FXML private TextArea keyComparePublicArea;
    @FXML private TextArea keyComparePrivateArea;
    @FXML private TextArea keyCompareResultArea;
    @FXML private ComboBox<String> keyStoreTypeCombo;
    @FXML private PasswordField keyStorePasswordField;
    @FXML private CheckBox keyStoreUnsafeExtractCheck;
    @FXML private TextField keyStorePathField;
    @FXML private TextArea keyStoreReportArea;
    @FXML private ComboBox<String> keyStoreProfileCombo;
    @FXML private TextField keyStoreProfileNameField;
    @FXML private TextField pkcs11NameField;
    @FXML private TextField pkcs11LibraryField;
    @FXML private TextField pkcs11SlotField;
    @FXML private PasswordField pkcs11PinField;
    @FXML private ComboBox<String> pkcs11ProfileCombo;
    @FXML private TextArea pkcs11ReportArea;
    @FXML private ComboBox<String> pkcs11SigningKeyCombo;
    @FXML private ComboBox<String> pkcs11SignatureAlgorithmCombo;
    @FXML private TextArea pkcs11DataArea;
    @FXML private TextArea pkcs11SignatureArea;
    @FXML private ComboBox<String> pkcs11CertificateAliasCombo;
    @FXML private TextArea pkcs11CertificateArea;
    @FXML private ComboBox<String> pkcs11JwtAlgorithmCombo;
    @FXML private TextArea pkcs11JwtPayloadArea;
    @FXML private TextArea pkcs11JwtOutputArea;
    @FXML private TextArea pkcs11CmsDataArea;
    @FXML private CheckBox pkcs11CmsDetachedCheck;
    @FXML private TextArea pkcs11CmsOutputArea;
    @FXML private ComboBox<String> pkcs11WrappingKeyCombo;
    @FXML private ComboBox<String> pkcs11WrapKeyCombo;
    @FXML private ComboBox<String> pkcs11WrapTransformationCombo;
    @FXML private TextArea pkcs11WrapResultArea;
    @FXML private ComboBox<String> pkcs11UnwrappingKeyCombo;
    @FXML private TextArea pkcs11UnwrapDataArea;
    @FXML private ComboBox<String> pkcs11UnwrapTransformationCombo;
    @FXML private TextField pkcs11UnwrapAlgorithmField;
    @FXML private ComboBox<String> pkcs11UnwrapTypeCombo;
    @FXML private TextArea pkcs11UnwrapResultArea;

    @FXML private ComboBox<String> numComponentsCombo;
    @FXML private TextArea keyToSplitField;
    @FXML private TextArea componentResultsArea;
    @FXML private TextField component1Field;
    @FXML private TextField component2Field;
    @FXML private TextField component3Field;
    @FXML private TextField component4Field;
    @FXML private TextField component5Field;

    @FXML private ComboBox<String> kdfAlgorithmCombo;
    @FXML private ComboBox<String> kdfInputFormatCombo;
    @FXML private ComboBox<String> kdfSaltFormatCombo;
    @FXML private ComboBox<String> kdfInfoFormatCombo;
    @FXML private TextField kdfInputField;
    @FXML private TextField kdfSaltField;
    @FXML private TextField kdfInfoField;
    @FXML private TextField kdfIterationsField;
    @FXML private TextField kdfOutputLengthField;
    @FXML private TextArea kdfResultArea;
    @FXML private Label kdfInputHelpLabel;
    @FXML private Label kdfValidationLabel;
    @FXML private Label kdfIterationsLabel;
    @FXML private VBox kdfSaltBox;
    @FXML private VBox kdfInfoBox;
    @FXML private Label kdfInputBadgeLabel;
    @FXML private Label kdfSaltBadgeLabel;
    @FXML private Label kdfInfoBadgeLabel;

    @FXML private ComboBox<String> keyWrapModeCombo;
    @FXML private CheckBox keyWrapUnwrapCheck;
    @FXML private TextField keyWrapKekField;
    @FXML private TextField keyWrapDataField;
    @FXML private TextArea keyWrapResultArea;

    @FXML private ComboBox<Integer> rsaKeySizeCombo;
    @FXML private TextArea rsaPublicKeyArea;
    @FXML private TextArea rsaPrivateKeyArea;

    @FXML private ComboBox<String> dsaKeySizeCombo;
    @FXML private TextArea dsaPublicKeyArea;
    @FXML private TextArea dsaPrivateKeyArea;

    private ComboBox<String> ecdsaFpCurveCombo;
    private TextArea ecdsaFpPublicKeyArea;
    private TextArea ecdsaFpPrivateKeyArea;

    private TextArea ed25519PublicKeyArea;
    private TextArea ed25519PrivateKeyArea;

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

    private TextArea crlIssuerCertArea;
    private TextArea crlIssuerKeyArea;
    private TextArea crlExistingCrlArea;
    private TextField crlRevokeSerialField;
    private ComboBox<String> crlRevokeReasonCombo;
    private TextArea crlResultArea;

    private TextArea valCertInput;
    private TextArea valIssuerInput;
    private TextArea valResultArea;

    public KeyPair getLastGeneratedKeyPair() {
        return workspace.lastGeneratedKeyPair;
    }

    @FXML
    private void initialize() {
        moduleI18n = ModuleI18n.bind(keysRoot, ModuleTextCatalog.keys());
        initializeRsaKexControls();
        initializeThalesControls();
        initializeAtalla();
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
                tr31ImportResultArea, tr31OptionalBlockCombo);
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
        keySummaryCoordinator().initializeSummaryListeners();
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
        keyStoreCoordinator().init();
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

    public TitledPane expandSymmetricPane(String paneName) {
        if (paneName == null || paneName.isBlank()) return null;
        if (paneName.contains("PKCS#11 Profiles")) {
            return openIncludedPane(pkcs11Profiles);
        }
        // Both words, because "Batch" alone would also claim any later non-ICSF batch pane.
        if (paneName.contains("ICSF") && paneName.contains("Batch")) {
            return openIncludedPane(icsfBatchPane);
        }
        // Before the bare "ICSF" test below, which would otherwise claim this one too.
        if (paneName.contains("ICSF") && (paneName.contains("Export") || paneName.contains("Import"))) {
            return openIncludedPane(icsfKeyWrapPane);
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

    private List<TitledPane> includedSymmetricPanes() {
        List<TitledPane> panes = new ArrayList<>();
        if (pkcs11Profiles != null) panes.add(pkcs11Profiles);
        if (icsfTokenPane != null) panes.add(icsfTokenPane);
        if (icsfBatchPane != null) panes.add(icsfBatchPane);
        if (icsfKeyWrapPane != null) panes.add(icsfKeyWrapPane);
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

    public void fillTR31KeyBlockInput(String value) { tr31Coordinator().fillTR31KeyBlockInput(value); }

    @FXML
    public void handleTR31Clear() { tr31Coordinator().handleTR31Clear(); }

    @FXML
    public void handleTR31Reset() { tr31Coordinator().handleTR31Reset(); }

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

    @FXML private void handleChooseKeyStore() { keyStoreCoordinator().handleChooseKeyStore(); }
    @FXML private void handleSaveKeyStoreProfile() { keyStoreCoordinator().handleSaveKeyStoreProfile(); }
    @FXML private void handleChoosePkcs11Library() { keyStoreCoordinator().handleChoosePkcs11Library(); }
    @FXML private void handleConnectPkcs11() { keyStoreCoordinator().handleConnectPkcs11(); }
    @FXML private void handleDisconnectPkcs11() { keyStoreCoordinator().handleDisconnectPkcs11(); }
    @FXML private void handlePkcs11Sign() { keyStoreCoordinator().handlePkcs11Sign(); }
    @FXML private void handlePkcs11Verify() { keyStoreCoordinator().handlePkcs11Verify(); }
    @FXML private void handleShowPkcs11Certificate() { keyStoreCoordinator().handleShowPkcs11Certificate(); }
    @FXML private void handleGeneratePkcs11Jwt() { keyStoreCoordinator().handleGeneratePkcs11Jwt(); }
    @FXML private void handleGeneratePkcs11Cms() { keyStoreCoordinator().handleGeneratePkcs11Cms(); }
    @FXML private void handlePkcs11Wrap() { keyStoreCoordinator().handlePkcs11Wrap(); }
    @FXML private void handlePkcs11Unwrap() { keyStoreCoordinator().handlePkcs11Unwrap(); }
    @FXML private void handleLoadKeyStoreProfile() { keyStoreCoordinator().handleLoadKeyStoreProfile(); }
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

    private void updateStatus(String message) {
        if (mainController != null) mainController.updateStatus(message);
    }

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
        symmetricKeyCoordinator().initialize();
    }

    public void initializeKeyMaterialInspector(TextArea inputArea, TextArea reportArea) {
        this.keyMaterialInputArea = inputArea;
        this.keyMaterialReportArea = reportArea;
        keyStoreCoordinator().initializeKeyMaterialInspector();
    }

    public void initializeKeyPairComparator(TextArea publicArea, TextArea privateArea, TextArea resultArea) {
        this.keyComparePublicArea = publicArea;
        this.keyComparePrivateArea = privateArea;
        this.keyCompareResultArea = resultArea;
        keyStoreCoordinator().initializeKeyPairComparator();
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
        keyStoreCoordinator().initializeKeyStoreInspector();
    }

    public void initializePkcs11Inspector(TextField nameField, TextField libraryField, TextField slotField,
            PasswordField pinField, ComboBox<String> profileCombo, TextArea reportArea) {
        this.pkcs11NameField = nameField;
        this.pkcs11LibraryField = libraryField;
        this.pkcs11SlotField = slotField;
        this.pkcs11PinField = pinField;
        this.pkcs11ProfileCombo = profileCombo;
        this.pkcs11ReportArea = reportArea;
        keyStoreCoordinator().initializePkcs11Inspector();
    }

    public void initializePkcs11Signing(ComboBox<String> keyCombo, ComboBox<String> algorithmCombo,
            TextArea dataArea, TextArea signatureArea) {
        this.pkcs11SigningKeyCombo = keyCombo;
        this.pkcs11SignatureAlgorithmCombo = algorithmCombo;
        this.pkcs11DataArea = dataArea;
        this.pkcs11SignatureArea = signatureArea;
        keyStoreCoordinator().initializePkcs11Signing();
    }

    public void initializePkcs11Certificates(ComboBox<String> certificateAliasCombo, TextArea certificateArea) {
        this.pkcs11CertificateAliasCombo = certificateAliasCombo;
        this.pkcs11CertificateArea = certificateArea;
        keyStoreCoordinator().initializePkcs11Certificates();
    }

    public void initializePkcs11Jwt(ComboBox<String> algorithmCombo, TextArea payloadArea, TextArea outputArea) {
        this.pkcs11JwtAlgorithmCombo = algorithmCombo;
        this.pkcs11JwtPayloadArea = payloadArea;
        this.pkcs11JwtOutputArea = outputArea;
        keyStoreCoordinator().initializePkcs11Jwt();
    }

    public void initializePkcs11Cms(TextArea dataArea, CheckBox detachedCheck, TextArea outputArea) {
        this.pkcs11CmsDataArea = dataArea;
        this.pkcs11CmsDetachedCheck = detachedCheck;
        this.pkcs11CmsOutputArea = outputArea;
        keyStoreCoordinator().initializePkcs11Cms();
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
        keyStoreCoordinator().initializePkcs11Wrap();
    }

    public void connectPkcs11() { keyStoreCoordinator().connectPkcs11(); }

    public void disconnectPkcs11() { keyStoreCoordinator().disconnectPkcs11(); }

    public void choosePkcs11Library() { keyStoreCoordinator().choosePkcs11Library(); }

    public void handleSavePkcs11Profile() { keyStoreCoordinator().handleSavePkcs11Profile(); }

    public void handleDeletePkcs11Profile() { keyStoreCoordinator().handleDeletePkcs11Profile(); }

    public void refreshPkcs11SigningKeys() { keyStoreCoordinator().refreshPkcs11SigningKeys(); }

    public void refreshPkcs11CertificateAliases() { keyStoreCoordinator().refreshPkcs11CertificateAliases(); }

    public void refreshPkcs11WrapKeyAliases() { keyStoreCoordinator().refreshPkcs11WrapKeyAliases(); }

    public void wrapWithPkcs11() { keyStoreCoordinator().wrapWithPkcs11(); }

    public void unwrapWithPkcs11() { keyStoreCoordinator().unwrapWithPkcs11(); }

    public void showPkcs11CertificateChain() { keyStoreCoordinator().showPkcs11CertificateChain(); }

    public void handleUpdatePkcs11CertificateChain() { keyStoreCoordinator().handleUpdatePkcs11CertificateChain(); }

    public void generatePkcs11Jwt() { keyStoreCoordinator().generatePkcs11Jwt(); }

    public void generatePkcs11Cms() { keyStoreCoordinator().generatePkcs11Cms(); }

    public void signWithPkcs11() { keyStoreCoordinator().signWithPkcs11(); }

    public void verifyWithPkcs11() { keyStoreCoordinator().verifyWithPkcs11(); }

    private String requirePkcs11SigningAlias() { return keyStoreCoordinator().requirePkcs11SigningAlias(); }

    public void handleInspectKeyMaterial() { keyStoreCoordinator().handleInspectKeyMaterial(); }

    public void handleCompareKeyPair() { keyStoreCoordinator().handleCompareKeyPair(); }

    public void handleInspectKeyStore() { keyStoreCoordinator().handleInspectKeyStore(); }

    public void chooseKeyStore() { keyStoreCoordinator().chooseKeyStore(); }

    public void saveKeyStoreProfile() { keyStoreCoordinator().saveKeyStoreProfile(); }

    public void loadKeyStoreProfile() { keyStoreCoordinator().loadKeyStoreProfile(); }

    public void initializeRSA(ComboBox<Integer> keySizeCombo, TextArea publicArea, TextArea privateArea) {
        this.rsaKeySizeCombo = keySizeCombo;
        this.rsaPublicKeyArea = publicArea;
        this.rsaPrivateKeyArea = privateArea;
        asymmetricKeyGenerationCoordinator().initializeRSA();
    }

    public void initializeDSA(ComboBox<String> keySizeCombo, TextArea publicArea, TextArea privateArea) {
        this.dsaKeySizeCombo = keySizeCombo;
        this.dsaPublicKeyArea = publicArea;
        this.dsaPrivateKeyArea = privateArea;
        asymmetricKeyGenerationCoordinator().initializeDSA();
    }

    public void initializeECDSAFp(ComboBox<String> curveCombo, TextArea publicArea, TextArea privateArea) {
        this.ecdsaFpCurveCombo = curveCombo;
        this.ecdsaFpPublicKeyArea = publicArea;
        this.ecdsaFpPrivateKeyArea = privateArea;
        asymmetricKeyGenerationCoordinator().initializeECDSAFp();
    }

    public void initializeEd25519(TextArea publicArea, TextArea privateArea) {
        this.ed25519PublicKeyArea = publicArea;
        this.ed25519PrivateKeyArea = privateArea;
        asymmetricKeyGenerationCoordinator().initializeEd25519();
    }

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
        certificateCoordinator().initializeCertificateGen();
    }

    public void initializeCertificateGen(
            TextField cnField, TextField orgField, TextField ouField,
            TextField localityField, TextField stateField, TextField countryField,
            TextField emailField, TextField validityField, ComboBox<String> keyTypeCombo,
            ComboBox<String> signAlgoCombo, TextArea outputArea) {
        initializeCertificateGen(cnField, orgField, ouField, localityField, stateField, countryField, emailField,
                validityField, keyTypeCombo, signAlgoCombo, outputArea, null, null, null);
    }

    public void initializeCertificateParse(TextArea inputArea, TextArea resultArea) {
        this.certInputArea = inputArea;
        this.certParseResultArea = resultArea;
        certificateCoordinator().initializeCertificateParse();
    }

    public void initializeCertificateComparator(TextArea leftArea, TextArea rightArea, TextArea resultArea) {
        this.certCompareLeftArea = leftArea;
        this.certCompareRightArea = rightArea;
        this.certCompareResultArea = resultArea;
        certificateCoordinator().initializeCertificateComparator();
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
        certificateCoordinator().initializeCertificateIssuer();
    }

    public void initializeCrlManagement(TextArea issuerCertArea, TextArea issuerKeyArea, TextArea existingCrlArea,
            TextField serialField, ComboBox<String> reasonCombo, TextArea resultArea) {
        this.crlIssuerCertArea = issuerCertArea;
        this.crlIssuerKeyArea = issuerKeyArea;
        this.crlExistingCrlArea = existingCrlArea;
        this.crlRevokeSerialField = serialField;
        this.crlRevokeReasonCombo = reasonCombo;
        this.crlResultArea = resultArea;
        certificateCoordinator().initializeCrlManagement();
    }

    public void initializeCertificateChainValidation(TextArea chainArea, TextArea trustAnchorArea, TextArea resultArea) { certificateChainCoordinator().initialize(new CertificateChainCoordinator.View(chainArea, trustAnchorArea, resultArea)); }

    public void handleIssueCertificateFromCsr() { certificateCoordinator().handleIssueCertificateFromCsr(); }

    public void handleGenerateCrl() { certificateCoordinator().handleGenerateCrl(); }

    public void handleRevokeCrl() { certificateCoordinator().handleRevokeCrl(); }

    public void handleCompareCertificates() { certificateCoordinator().handleCompareCertificates(); }

    public void initializeValidateCertificate(TextArea valCertInput, TextArea valIssuerInput, TextArea valResultArea) {
        this.valCertInput = valCertInput;
        this.valIssuerInput = valIssuerInput;
        this.valResultArea = valResultArea;
        certificateCoordinator().initializeValidateCertificate();
    }

    public void initializeValidateChain() {
        // No components to initialize for now
    }

    public void handleGenerateKey() { symmetricKeyCoordinator().handleGenerateKey(); }

    @FXML
    public void handleSaveGeneratedKeyToLab() { keyLabCoordinator().handleSaveGeneratedKeyToLab(); }

    private void updateGeneratedKeySummaryCard(com.cryptocarver.model.GeneratedKeySummary summary) { keySummaryCoordinator().updateGeneratedKeySummaryCard(summary); }

    @FXML
    public void handleCopyGeneratedKey() { keySummaryCoordinator().handleCopyGeneratedKey(); }

    @FXML
    public void handleCopyGeneratedKcv() { keySummaryCoordinator().handleCopyGeneratedKcv(); }

    @FXML
    public void handleCopyGeneratedSummary() { keySummaryCoordinator().handleCopyGeneratedSummary(); }

    @FXML
    public void handleOpenValidationAndKcv() { keySummaryCoordinator().handleOpenValidationAndKcv(); }

    @FXML
    public void handleKcvLengthToggle() { keySummaryCoordinator().handleKcvLengthToggle(); }

    private int selectedKcvLength() { return keySummaryCoordinator().selectedKcvLength(); }

    public void handleValidateKey() { symmetricKeyCoordinator().handleValidateKey(); }

    public void handleSplitKey() { symmetricKeyCoordinator().handleSplitKey(); }

    public void handleCombineComponents() { symmetricKeyCoordinator().handleCombineComponents(); }

    @FXML private Button rsaGenerateBtn;
    @FXML private Button dsaGenerateBtn;

    public void handleGenerateRSA() { asymmetricKeyGenerationCoordinator().handleGenerateRSA(); }

    public void handleGenerateDSA() { asymmetricKeyGenerationCoordinator().handleGenerateDSA(); }

    public void handleGenerateECDSAFp() { asymmetricKeyGenerationCoordinator().handleGenerateECDSAFp(); }

    public void handleGenerateEd25519() { asymmetricKeyGenerationCoordinator().handleGenerateEd25519(); }

    public void handleGenerateEdDSA() { asymmetricKeyGenerationCoordinator().handleGenerateEdDSA(); }

    public void handleGenerateCertificate() { certificateCoordinator().handleGenerateCertificate(); }

    public void handleGenerateCSR() { certificateCoordinator().handleGenerateCSR(); }

    public void handleParseCertificate() { certificateCoordinator().handleParseCertificate(); }

    public void handleValidateCertificate() { certificateCoordinator().handleValidateCertificate(); }

    @FXML private TextField tr31KbpkExportField;
    @FXML private TextField tr31KeyToWrapField;
    @FXML private ComboBox<String> tr31UsageCombo;
    @FXML private ComboBox<String> tr31AlgorithmCombo;
    @FXML private ComboBox<String> tr31ModeCombo;
    @FXML private ComboBox<String> tr31VersionCombo;
    @FXML private ComboBox<String> tr31ExportabilityCombo;
    @FXML private TextField tr31OptionalBlocksField;
    @javafx.fxml.FXML private ComboBox<String> tr31OptionalBlockCombo;
    @FXML private TextArea tr31ExportResultArea;

    @FXML private TextField tr31KbpkImportField;
    @FXML private TextArea tr31KeyBlockField;
    @FXML private TextField tr31KeyLengthField;
    @FXML private TextArea tr31ImportResultArea;

    public void initializeTR31(TextField tr31KbpkExportField, TextField tr31KeyToWrapField,
            ComboBox<String> tr31VersionCombo, ComboBox<String> tr31UsageCombo,
            ComboBox<String> tr31AlgorithmCombo, ComboBox<String> tr31ModeCombo,
            ComboBox<String> tr31ExportabilityCombo,
            TextField tr31OptionalBlocksField,
            TextArea tr31ExportResultArea, TextField tr31KbpkImportField,
            TextArea tr31KeyBlockField, TextField tr31KeyLengthField,
            TextArea tr31ImportResultArea, ComboBox<String> optionalBlockCombo) {

        this.tr31KbpkExportField = tr31KbpkExportField;
        this.tr31KeyToWrapField = tr31KeyToWrapField;
        this.tr31VersionCombo = tr31VersionCombo;
        this.tr31UsageCombo = tr31UsageCombo;
        this.tr31AlgorithmCombo = tr31AlgorithmCombo;
        this.tr31ModeCombo = tr31ModeCombo;
        this.tr31ExportabilityCombo = tr31ExportabilityCombo;
        this.tr31OptionalBlocksField = tr31OptionalBlocksField;
        this.tr31OptionalBlockCombo = optionalBlockCombo;
        this.tr31ExportResultArea = tr31ExportResultArea;

        this.tr31KbpkImportField = tr31KbpkImportField;
        this.tr31KeyBlockField = tr31KeyBlockField;
        this.tr31KeyLengthField = tr31KeyLengthField;
        this.tr31ImportResultArea = tr31ImportResultArea;

        tr31Coordinator().initialize();
    }

    @FXML
    public void handleTR31Export() { tr31Coordinator().handleTR31Export(); }

    @FXML
    public void handleTR31Import() { tr31Coordinator().handleTR31Import(); }

    @FXML
    public void handleTR31ParseHeader() { tr31Coordinator().handleTR31ParseHeader(); }

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

    private void initializeRsaKexControls() { rsaKexCoordinator().initializeRsaKexControls(); }

    @FXML
    public void handleRsaKexEnvelopeToggle() { rsaKexCoordinator().handleRsaKexEnvelopeToggle(); }

    @FXML
    public void handleRsaKexExport() { rsaKexCoordinator().handleRsaKexExport(); }

    @FXML
    public void handleRsaKexImport() { rsaKexCoordinator().handleRsaKexImport(); }

    @FXML
    public void handleRsaKexClear() { rsaKexCoordinator().handleRsaKexClear(); }

    @FXML
    public void handleRsaKexReset() { rsaKexCoordinator().handleRsaKexReset(); }

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

    @FXML
    public void handleTr34Distribute() { tr34Coordinator().handleTr34Distribute(); }

    @FXML
    public void handleTr34Receive() { tr34Coordinator().handleTr34Receive(); }

    @FXML
    public void handleTr34GenerateChallenge() { tr34Coordinator().handleTr34GenerateChallenge(); }

    @FXML
    public void handleTr34Clear() { tr34Coordinator().handleTr34Clear(); }

    @FXML
    public void handleTr34Reset() { tr34Coordinator().handleTr34Reset(); }

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
        kdfKeyWrapCoordinator().initializeKDF();
    }

    @FXML
    public void handleGenerateKdfSalt() { kdfKeyWrapCoordinator().handleGenerateKdfSalt(); }

    public void initializeKeyWrap(ComboBox<String> modeCombo, CheckBox unwrapCheck, TextField kekField,
            TextField dataField, TextArea resultArea) {
        this.keyWrapModeCombo = modeCombo;
        this.keyWrapUnwrapCheck = unwrapCheck;
        this.keyWrapKekField = kekField;
        this.keyWrapDataField = dataField;
        this.keyWrapResultArea = resultArea;
        kdfKeyWrapCoordinator().initializeKeyWrap();
    }

    public void handleKeyWrap() { kdfKeyWrapCoordinator().handleKeyWrap(); }

    public void handleDeriveKey() { kdfKeyWrapCoordinator().handleDeriveKey(); }

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
            CheckBox onlineRevocationCheck) { cmsCoordinator().initialize(new CmsCoordinator.View(inputArea, outputArea, detachedCheck, cadesBesCheck, cadesTCheck, cadesTsaUrlField, cadesTsaBox, signCertArea, signKeyArea, encryptCertArea, decryptKeyArea, signSourcePkcs11Radio, signLocalGrid, signPkcs11Box, signKeyAliasCombo, verifyDataArea, encryptSourcePkcs11Radio, encryptLocalGrid, encryptPkcs11Box, encryptKeyAliasCombo, signButton, onlineRevocationCheck)); }

    public void handleCadesTimestampOptionChanged() { cmsCoordinator().handleCadesTimestampOptionChanged(); }

    public void handleCMSourceChanged() { cmsCoordinator().handleCMSourceChanged(); }

    public void handleLoadCMSKeys() { cmsCoordinator().handleLoadCMSKeys(); }

    public void handleCMSEncryptSourceChanged() { cmsCoordinator().handleCMSEncryptSourceChanged(); }

    public void handleLoadCMSEncryptKeys() { cmsCoordinator().handleLoadCMSEncryptKeys(); }

    public void handleCMSSign() { cmsCoordinator().handleCMSSign(); }

    public void handleCMSVerify() { cmsCoordinator().handleCMSVerify(); }

    public void handleUpgradeCadesLt() { cmsCoordinator().handleUpgradeCadesLt(); }

    public void handleCMSEncrypt() { cmsCoordinator().handleCMSEncrypt(); }

    public void handleCMSDecrypt() { cmsCoordinator().handleCMSDecrypt(); }

    public void initializeCertificateChain(TextArea inputArea, TextArea crlArea, TextArea resultArea) { certificateChainCoordinator().initialize(new CertificateChainCoordinator.View(inputArea, crlArea, resultArea)); }

    public void handleValidateCertificateChain() { certificateChainCoordinator().handleValidateCertificateChain(); }

    public void handleClear() { keySummaryCoordinator().handleClear(); }

    public void handleClearAsymmetric() { keySummaryCoordinator().handleClearAsymmetric(); }

    public void handleGlobalSymmetricShelfAction() { keySummaryCoordinator().handleGlobalSymmetricShelfAction(); }

    public void handleGlobalAsymmetricShelfAction(String operation) { keySummaryCoordinator().handleGlobalAsymmetricShelfAction(operation); }

    @FXML public void handleCopyRsaPublicKey() { keySummaryCoordinator().handleCopyRsaPublicKey(); }
    @FXML public void handleCopyRsaPrivateKey() { keySummaryCoordinator().handleCopyRsaPrivateKey(); }
    @FXML public void handleCopyRsaSummary() { keySummaryCoordinator().handleCopyRsaSummary(); }
    @FXML public void handleExportRsaPublicPem() { keySummaryCoordinator().handleExportRsaPublicPem(); }
    @FXML public void handleExportRsaPrivatePem() { keySummaryCoordinator().handleExportRsaPrivatePem(); }
    @FXML public void handleSendRsaPublicToShelf() { keySummaryCoordinator().handleSendRsaPublicToShelf(); }
    @FXML public void handleSendRsaPrivateToShelf() { keySummaryCoordinator().handleSendRsaPrivateToShelf(); }
    @FXML public void handleUseRsaInCipher() { keySummaryCoordinator().handleUseRsaInCipher(); }
    @FXML public void handleUseRsaInSignatures() { keySummaryCoordinator().handleUseRsaInSignatures(); }
    @FXML public void handleUseRsaInCertificates() { keySummaryCoordinator().handleUseRsaInCertificates(); }
    @FXML public void handleClearRsa() { keySummaryCoordinator().handleClearRsa(); }

    @FXML public void handleCopyEcdsaPublicKey() { keySummaryCoordinator().handleCopyEcdsaPublicKey(); }
    @FXML public void handleCopyEcdsaPrivateKey() { keySummaryCoordinator().handleCopyEcdsaPrivateKey(); }
    @FXML public void handleCopyEcdsaSummary() { keySummaryCoordinator().handleCopyEcdsaSummary(); }
    @FXML public void handleExportEcdsaPublicPem() { keySummaryCoordinator().handleExportEcdsaPublicPem(); }
    @FXML public void handleExportEcdsaPrivatePem() { keySummaryCoordinator().handleExportEcdsaPrivatePem(); }
    @FXML public void handleSendEcdsaPublicToShelf() { keySummaryCoordinator().handleSendEcdsaPublicToShelf(); }
    @FXML public void handleSendEcdsaPrivateToShelf() { keySummaryCoordinator().handleSendEcdsaPrivateToShelf(); }
    @FXML public void handleUseEcdsaInSignatures() { keySummaryCoordinator().handleUseEcdsaInSignatures(); }
    @FXML public void handleUseEcdsaInCertificates() { keySummaryCoordinator().handleUseEcdsaInCertificates(); }
    @FXML public void handleClearEcdsa() { keySummaryCoordinator().handleClearEcdsa(); }

    @FXML public void handleCopyDsaPublicKey() { keySummaryCoordinator().handleCopyDsaPublicKey(); }
    @FXML public void handleCopyDsaPrivateKey() { keySummaryCoordinator().handleCopyDsaPrivateKey(); }
    @FXML public void handleCopyDsaSummary() { keySummaryCoordinator().handleCopyDsaSummary(); }
    @FXML public void handleExportDsaPublicPem() { keySummaryCoordinator().handleExportDsaPublicPem(); }
    @FXML public void handleExportDsaPrivatePem() { keySummaryCoordinator().handleExportDsaPrivatePem(); }
    @FXML public void handleSendDsaPublicToShelf() { keySummaryCoordinator().handleSendDsaPublicToShelf(); }
    @FXML public void handleSendDsaPrivateToShelf() { keySummaryCoordinator().handleSendDsaPrivateToShelf(); }
    @FXML public void handleUseDsaInSignatures() { keySummaryCoordinator().handleUseDsaInSignatures(); }
    @FXML public void handleUseDsaInCertificates() { keySummaryCoordinator().handleUseDsaInCertificates(); }
    @FXML public void handleClearDsa() { keySummaryCoordinator().handleClearDsa(); }

    @FXML public void handleCopyEddsaPublicKey() { keySummaryCoordinator().handleCopyEddsaPublicKey(); }
    @FXML public void handleCopyEddsaPrivateKey() { keySummaryCoordinator().handleCopyEddsaPrivateKey(); }
    @FXML public void handleCopyEddsaSummary() { keySummaryCoordinator().handleCopyEddsaSummary(); }
    @FXML public void handleExportEddsaPublicPem() { keySummaryCoordinator().handleExportEddsaPublicPem(); }
    @FXML public void handleExportEddsaPrivatePem() { keySummaryCoordinator().handleExportEddsaPrivatePem(); }
    @FXML public void handleSendEddsaPublicToShelf() { keySummaryCoordinator().handleSendEddsaPublicToShelf(); }
    @FXML public void handleSendEddsaPrivateToShelf() { keySummaryCoordinator().handleSendEddsaPrivateToShelf(); }
    @FXML public void handleUseEddsaInSignatures() { keySummaryCoordinator().handleUseEddsaInSignatures(); }
    @FXML public void handleUseEddsaInCertificates() { keySummaryCoordinator().handleUseEddsaInCertificates(); }
    @FXML public void handleClearEd25519() { keySummaryCoordinator().handleClearEd25519(); }

    public String getOutputText() { return keySummaryCoordinator().getOutputText(); }

    public void loadProfile(com.cryptocarver.model.payments.PaymentProfile p) { tr31Coordinator().loadProfile(p); }

    private void initializeKeyLab() { keyLabCoordinator().initializeKeyLab(); }

    public void updateVisibilityControls() { keyLabCoordinator().updateVisibilityControls(); }

    public void refreshKeyLabTable() { keyLabCoordinator().refreshKeyLabTable(); }

    @FXML
    public void handleUseKeyLabInCipher() { keyLabCoordinator().handleUseKeyLabInCipher(); }

    @FXML
    public void handleUseKeyLabInMac() { keyLabCoordinator().handleUseKeyLabInMac(); }

    @FXML private void handleKeyLabGenerate() { keyLabCoordinator().handleKeyLabGenerate(); }

    @FXML private void handleKeyLabImport() { keyLabCoordinator().handleKeyLabImport(); }

    @FXML private void handleKeyLabReveal() { keyLabCoordinator().handleKeyLabReveal(); }

    @FXML private void handleKeyLabCopyId() { keyLabCoordinator().handleKeyLabCopyId(); }

    @FXML private void handleKeyLabSaveMetadata() { keyLabCoordinator().handleKeyLabSaveMetadata(); }

    @FXML private void handleKeyLabArchive() { keyLabCoordinator().handleKeyLabArchive(); }

    @FXML private void handleKeyLabDelete() { keyLabCoordinator().handleKeyLabDelete(); }

    @FXML private void handleImportKeyLabMetadata() { keyLabCoordinator().handleImportKeyLabMetadata(); }

    @FXML private void handleExportKeyLabMetadata() { keyLabCoordinator().handleExportKeyLabMetadata(); }

    public void selectKeyInKeyLab(String keyId) { keyLabCoordinator().selectKeyInKeyLab(keyId); }

    private void setupHexValidation(TextField field) { symmetricKeyCoordinator().setupHexValidation(field); }

    private void setupHexValidation(TextArea field) { symmetricKeyCoordinator().setupHexValidation(field); }

    @FXML private TextField thalesLmkField;
    @FXML private TextField thalesKeyTypeField;
    @FXML private ComboBox<String> thalesSchemeCombo;
    @FXML private TextField thalesClearKeyField;
    @FXML private TextField thalesCryptogramField;
    @FXML private TextField thalesCheckValueField;
    @FXML private CheckBox thalesComponentCheck;
    @FXML private TextArea thalesResultArea;

    @FXML
    public void handleThalesEncrypt() { paymentKeyBlockCoordinator().handleThalesEncrypt(); }

    @FXML
    public void handleThalesDecrypt() { paymentKeyBlockCoordinator().handleThalesDecrypt(); }

    @FXML
    public void handleThalesDescribe() { paymentKeyBlockCoordinator().handleThalesDescribe(); }

    @FXML
    public void handleThalesLookup() { paymentKeyBlockCoordinator().handleThalesLookup(); }

    @FXML
    public void handleThalesLoadExample() { paymentKeyBlockCoordinator().handleThalesLoadExample(); }

    private void initializeThalesControls() { paymentKeyBlockCoordinator().initializeThalesControls(); }

    @FXML private TextArea keyBlockInputArea;
    @FXML private TextArea keyBlockResultArea;
    @FXML private TextField keyBlockLmkField;

    @FXML
    public void handleKeyBlockInspect() { paymentKeyBlockCoordinator().handleKeyBlockInspect(); }

    @FXML
    public void handleKeyBlockUnwrap() { paymentKeyBlockCoordinator().handleKeyBlockUnwrap(); }

    @FXML
    public void handleKeyBlockExample() { paymentKeyBlockCoordinator().handleKeyBlockExample(); }

    @FXML private ComboBox<AtallaAkbHeader.Template> atallaTemplateCombo;
    @FXML private ComboBox<AtallaAkbHeader.Option> atalla0Combo;
    @FXML private ComboBox<AtallaAkbHeader.Option> atalla1Combo;
    @FXML private ComboBox<AtallaAkbHeader.Option> atalla2Combo;
    @FXML private ComboBox<AtallaAkbHeader.Option> atalla3Combo;
    @FXML private ComboBox<AtallaAkbHeader.Option> atalla4Combo;
    @FXML private ComboBox<AtallaAkbHeader.Option> atalla5Combo;
    @FXML private ComboBox<AtallaAkbHeader.Option> atalla6Combo;
    @FXML private ComboBox<AtallaAkbHeader.Option> atalla7Combo;
    @FXML private TextField atallaHeaderField;
    @FXML private TextArea atallaMeaningArea;
    @FXML private TextField atallaMfkField;
    @FXML private TextField atallaKeyField;
    @FXML private TextArea atallaBlockArea;
    @FXML private TextArea atallaResultArea;

    private void initializeAtalla() { paymentKeyBlockCoordinator().initializeAtalla(); }

    @FXML
    public void handleAtallaGenerate() { paymentKeyBlockCoordinator().handleAtallaGenerate(); }

    @FXML
    public void handleAtallaUnwrap() { paymentKeyBlockCoordinator().handleAtallaUnwrap(); }

    @FXML
    public void handleAtallaExample() { paymentKeyBlockCoordinator().handleAtallaExample(); }

}
