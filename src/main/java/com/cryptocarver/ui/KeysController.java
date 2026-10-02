package com.cryptocarver.ui;

import com.cryptocarver.crypto.*;
import com.cryptocarver.crypto.hsm.KeyMaterial;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.GeneratedKeySummary;
import com.cryptocarver.model.GeneratedAsymmetricKeySummary;
import com.cryptocarver.util.DataConverter;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for Keys tab - Enhanced with asymmetric cryptography
 *
 * @author Felipe
 */
public class KeysController {

    private static final Logger LOG = LoggerFactory.getLogger(KeysController.class);
    private final KeysWorkspaceState workspace = new KeysWorkspaceState();
    private final DialogService dialogService = new DialogService();

    private PaymentKeyBlockCoordinator paymentKeyBlockCoordinator;

    private PaymentKeyBlockCoordinator paymentKeyBlockCoordinator() {
        if (paymentKeyBlockCoordinator == null) {
            paymentKeyBlockCoordinator = new PaymentKeyBlockCoordinator(
                    () -> new PaymentKeyBlockCoordinator.View(
                            thalesLmkField,
                            thalesKeyTypeField,
                            thalesSchemeCombo,
                            thalesClearKeyField,
                            thalesCryptogramField,
                            thalesCheckValueField,
                            thalesComponentCheck,
                            thalesResultArea,
                            keyBlockInputArea,
                            keyBlockResultArea,
                            keyBlockLmkField,
                            atallaTemplateCombo,
                            atalla0Combo,
                            atalla1Combo,
                            atalla2Combo,
                            atalla3Combo,
                            atalla4Combo,
                            atalla5Combo,
                            atalla6Combo,
                            atalla7Combo,
                            atallaHeaderField,
                            atallaMeaningArea,
                            atallaMfkField,
                            atallaKeyField,
                            atallaBlockArea,
                            atallaResultArea),
                    () -> mainController, workspace);
        }
        return paymentKeyBlockCoordinator;
    }

    private KeyLabCoordinator keyLabCoordinator;

    private KeyLabCoordinator keyLabCoordinator() {
        if (keyLabCoordinator == null) {
            keyLabCoordinator = new KeyLabCoordinator(
                    () -> new KeyLabCoordinator.View(
                            keyLabPane,
                            keyLabSearchField,
                            keyLabStatusFilterCombo,
                            keyLabTable,
                            keyLabNewNameField,
                            keyLabNewAlgoCombo,
                            keyLabNewSizeCombo,
                            keyLabImportBytesField,
                            keyLabImportBtn,
                            keyLabDetailIdField,
                            keyLabDetailNameField,
                            keyLabDetailAlgoLabel,
                            keyLabDetailBitsLabel,
                            keyLabUsageEncryptCheck,
                            keyLabUsageDecryptCheck,
                            keyLabUsageMacCheck,
                            keyLabUsageWrapCheck,
                            keyLabUsageUnwrapCheck,
                            keyLabDetailExportabilityLabel,
                            keyLabDetailKcvLabel,
                            keyLabDetailFingerprintLabel,
                            keyLabDetailOriginLabel,
                            keyLabDetailCreatedLabel,
                            keyLabDetailModifiedLabel,
                            keyLabDetailStatusLabel,
                            keyLabDetailValueField,
                            keyLabRevealBtn,
                            keyLabArchiveBtn,
                            keyLabUseCipherBtn,
                            keyLabUseMacBtn,
                            summarySavedStatusLabel,
                            rsaSendPrivateShelfBtn,
                            ecdsaSendPrivateShelfBtn,
                            dsaSendPrivateShelfBtn,
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
                            keyTypeCombo,
                            saveGeneratedKeyButton,
                            rsaKeySizeCombo,
                            ecdsaCurveCombo,
                            dsaKeySizeCombo,
                            ecdsaPublicKeyArea,
                            ecdsaPrivateKeyArea,
                            eddsaPublicKeyArea,
                            eddsaPrivateKeyArea,
                            rsaKeyMaterialTabs,
                            ecdsaKeyMaterialTabs,
                            dsaKeyMaterialTabs,
                            eddsaKeyMaterialTabs,
                            generatedKeyField,
                            generatedKeySummaryCard,
                            summaryAlgoLabel,
                            summaryLengthLabel,
                            summaryKcvLabel,
                            summaryFingerprintLabel,
                            summaryParityLabel,
                            summaryOriginLabel,
                            summarySavedStatusLabel,
                            validationPane,
                            useFourByteKcvCheck,
                            rsaSummaryCard,
                            ecdsaSummaryCard,
                            dsaSummaryCard,
                            eddsaSummaryCard,
                            keyInputField,
                            validationResultArea,
                            componentResultsArea,
                            component1Field,
                            component2Field,
                            component3Field,
                            rsaPublicKeyArea,
                            rsaPrivateKeyArea,
                            dsaPublicKeyArea,
                            dsaPrivateKeyArea,
                            ecdsaFpPublicKeyArea,
                            ecdsaFpPrivateKeyArea,
                            ed25519PublicKeyArea,
                            ed25519PrivateKeyArea),
                    () -> mainController, workspace, this::handleValidateKey);
        }
        return keySummaryCoordinator;
    }

    private SymmetricKeyCoordinator symmetricKeyCoordinator;

    private SymmetricKeyCoordinator symmetricKeyCoordinator() {
        if (symmetricKeyCoordinator == null) {
            symmetricKeyCoordinator = new SymmetricKeyCoordinator(
                    () -> new SymmetricKeyCoordinator.View(
                            keyTypeCombo,
                            forceOddParityCheck,
                            generatedKeyField,
                            saveGeneratedKeyButton,
                            keyInputField,
                            validationResultArea,
                            numComponentsCombo,
                            keyToSplitField,
                            componentResultsArea,
                            component1Field,
                            component2Field,
                            component3Field,
                            component4Field,
                            component5Field),
                    () -> mainController, workspace, this::updateGeneratedKeySummaryCard, this::selectedKcvLength);
        }
        return symmetricKeyCoordinator;
    }

    private Tr31Coordinator tr31Coordinator;

    private Tr31Coordinator tr31Coordinator() {
        if (tr31Coordinator == null) {
            tr31Coordinator = new Tr31Coordinator(
                    () -> new Tr31Coordinator.View(
                            tr31KbpkExportField,
                            tr31KeyToWrapField,
                            tr31UsageCombo,
                            tr31AlgorithmCombo,
                            tr31ModeCombo,
                            tr31VersionCombo,
                            tr31ExportabilityCombo,
                            tr31OptionalBlocksField,
                            tr31OptionalBlockCombo,
                            tr31ExportResultArea,
                            tr31KbpkImportField,
                            tr31KeyBlockField,
                            tr31KeyLengthField,
                            tr31ImportResultArea),
                    () -> mainController, this::updateStatus, this::t);
        }
        return tr31Coordinator;
    }

    private RsaKeyExchangeCoordinator rsaKexCoordinator;

    private RsaKeyExchangeCoordinator rsaKexCoordinator() {
        if (rsaKexCoordinator == null) {
            rsaKexCoordinator = new RsaKeyExchangeCoordinator(
                    () -> new RsaKeyExchangeCoordinator.View(
                            rsaKexRecipientPemArea,
                            rsaKexKeyToWrapField,
                            rsaKexExportProfileCombo,
                            rsaKexIncludeEnvelopeCheck,
                            rsaKexEnvelopeFieldsBox,
                            rsaKexKidField,
                            rsaKexKeyVersionField,
                            rsaKexExportResultArea,
                            rsaKexPrivateKeyArea,
                            rsaKexWrappedDataArea,
                            rsaKexImportProfileCombo,
                            rsaKexImportResultArea),
                    () -> mainController, this::updateStatus, this::t);
        }
        return rsaKexCoordinator;
    }

    private Tr34Coordinator tr34Coordinator;

    private Tr34Coordinator tr34Coordinator() {
        if (tr34Coordinator == null) {
            tr34Coordinator = new Tr34Coordinator(
                    () -> new Tr34Coordinator.View(
                            tr34SenderPrivateKeyArea,
                            tr34SenderCertArea,
                            tr34ReceiverCertArea,
                            tr34KeyToDistributeField,
                            tr34KeyIdField,
                            tr34BindingNonceField,
                            tr34IncludeEnvelopeCheck,
                            tr34DistributeResultArea,
                            tr34ReceiverPrivateKeyArea,
                            tr34ExpectedSenderCertArea,
                            tr34DistributedDataArea,
                            tr34ChallengeNonceField,
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
                            rsaSummaryCard,
                            rsaSummaryAlgoLabel,
                            rsaSummaryFingerprintLabel,
                            rsaSummaryPubLenLabel,
                            rsaSummaryPrivLenLabel,
                            rsaSummaryCreatedLabel,
                            rsaSummarySavedStatusLabel,
                            ecdsaSummaryCard,
                            ecdsaSummaryAlgoLabel,
                            ecdsaSummaryFingerprintLabel,
                            ecdsaSummaryPubLenLabel,
                            ecdsaSummaryPrivLenLabel,
                            ecdsaSummaryCreatedLabel,
                            ecdsaSummarySavedStatusLabel,
                            dsaSummaryCard,
                            dsaSummaryAlgoLabel,
                            dsaSummaryFingerprintLabel,
                            dsaSummaryPubLenLabel,
                            dsaSummaryPrivLenLabel,
                            dsaSummaryCreatedLabel,
                            dsaSummarySavedStatusLabel,
                            eddsaSummaryCard,
                            eddsaSummaryAlgoLabel,
                            eddsaSummaryFingerprintLabel,
                            eddsaSummaryPubLenLabel,
                            eddsaSummaryPrivLenLabel,
                            eddsaSummaryCreatedLabel,
                            eddsaSummarySavedStatusLabel,
                            rsaKeySizeCombo,
                            rsaPublicKeyArea,
                            rsaPrivateKeyArea,
                            dsaKeySizeCombo,
                            dsaPublicKeyArea,
                            dsaPrivateKeyArea,
                            ecdsaFpCurveCombo,
                            ecdsaFpPublicKeyArea,
                            ecdsaFpPrivateKeyArea,
                            ed25519PublicKeyArea,
                            ed25519PrivateKeyArea,
                            rsaGenerateBtn,
                            dsaGenerateBtn),
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
                            kdfAlgorithmCombo,
                            kdfInputFormatCombo,
                            kdfSaltFormatCombo,
                            kdfInfoFormatCombo,
                            kdfInputField,
                            kdfSaltField,
                            kdfInfoField,
                            kdfIterationsField,
                            kdfOutputLengthField,
                            kdfResultArea,
                            kdfInputHelpLabel,
                            kdfValidationLabel,
                            kdfIterationsLabel,
                            kdfSaltBox,
                            kdfInfoBox,
                            kdfInputBadgeLabel,
                            kdfSaltBadgeLabel,
                            kdfInfoBadgeLabel,
                            keyWrapModeCombo,
                            keyWrapUnwrapCheck,
                            keyWrapKekField,
                            keyWrapDataField,
                            keyWrapResultArea),
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
    // ICSF / CCA native key tokens: an included, self-contained pane in the manner of
    // the PKCS#11 one. No ICSF logic lives in this controller.
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

    // Key Lab FXML fields
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

    // Symmetric Key Generation components
    @FXML
    private ComboBox<String> keyTypeCombo;
    @FXML
    private javafx.scene.control.CheckBox forceOddParityCheck;
    @FXML
    private TextArea generatedKeyField;
    @FXML
    private Button saveGeneratedKeyButton;



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
    @FXML private CheckBox useFourByteKcvCheck;
    @FXML private Button copyGeneratedKeyButton;
    @FXML private Button copyGeneratedKcvButton;
    @FXML private Button copyGeneratedSummaryButton;
    @FXML private Button saveGeneratedSummaryButton;
    @FXML private Button openValidationButton;



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

    // Store last generated key pair for certificate generation



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
        if (pkcs11ProfilesController != null && reporter != null) {
            pkcs11ProfilesController.setStatusReporter(reporter);
            pkcs11ProfilesController.setOperationExecutor(reporter.getOperationExecutor());
        }
        if (icsfTokenPaneController != null && reporter != null) {
            icsfTokenPaneController.setStatusReporter(reporter);
        }
        if (icsfKeyWrapPaneController != null && reporter != null) {
            icsfKeyWrapPaneController.setStatusReporter(reporter);
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

    /** The symmetric panes keys.fxml includes rather than owns, in layout order. */
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
        symmetricKeyCoordinator().initialize();
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

            javafx.stage.Window owner = keysRoot == null || keysRoot.getScene() == null
                    ? null : keysRoot.getScene().getWindow();
            boolean confirmed = dialogService.confirmDestructive(owner, "Confirm Token Update",
                    "Updating certificate chain for alias: " + alias + "\n\nLeaf Subject: " + subject
                            + "\nLeaf Issuer: " + issuer + "\nChain length: " + chain.size()
                            + "\n\nProceed with token modification?", "Update");
            if (!confirmed) {
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
        asymmetricKeyGenerationCoordinator().initializeRSA();
    }

    /**
     * Initialize DSA components
     */
    public void initializeDSA(ComboBox<String> keySizeCombo, TextArea publicArea, TextArea privateArea) {
        this.dsaKeySizeCombo = keySizeCombo;
        this.dsaPublicKeyArea = publicArea;
        this.dsaPrivateKeyArea = privateArea;
        asymmetricKeyGenerationCoordinator().initializeDSA();
    }

    /**
     * Initialize ECDSA F(p) components
     */
    public void initializeECDSAFp(ComboBox<String> curveCombo, TextArea publicArea, TextArea privateArea) {
        this.ecdsaFpCurveCombo = curveCombo;
        this.ecdsaFpPublicKeyArea = publicArea;
        this.ecdsaFpPrivateKeyArea = privateArea;
        asymmetricKeyGenerationCoordinator().initializeECDSAFp();
    }

    /**
     * Initialize Ed25519 components
     */
    public void initializeEd25519(TextArea publicArea, TextArea privateArea) {
        this.ed25519PublicKeyArea = publicArea;
        this.ed25519PrivateKeyArea = privateArea;
        asymmetricKeyGenerationCoordinator().initializeEd25519();
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

    public void initializeCertificateChainValidation(TextArea chainArea, TextArea trustAnchorArea, TextArea resultArea) { certificateChainCoordinator().initialize(new CertificateChainCoordinator.View(chainArea, trustAnchorArea, resultArea)); }

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
    public void handleGenerateKey() { symmetricKeyCoordinator().handleGenerateKey(); }

    @FXML
    public void handleSaveGeneratedKeyToLab() { keyLabCoordinator().handleSaveGeneratedKeyToLab(); }

    private void hideGeneratedKeySummary() { keySummaryCoordinator().hideGeneratedKeySummary(); }

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



    /**
     * Validate a key and calculate all KCVs
     */
    public void handleValidateKey() { symmetricKeyCoordinator().handleValidateKey(); }

    /**
     * Split a key into components
     */
    public void handleSplitKey() { symmetricKeyCoordinator().handleSplitKey(); }

    /**
     * Combine key components back into original key
     */
    public void handleCombineComponents() { symmetricKeyCoordinator().handleCombineComponents(); }

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
    public void handleGenerateRSA() { asymmetricKeyGenerationCoordinator().handleGenerateRSA(); }

    /**
     * Generate DSA key pair
     */
    public void handleGenerateDSA() { asymmetricKeyGenerationCoordinator().handleGenerateDSA(); }

    /**
     * Generate ECDSA F(p) key pair
     */
    public void handleGenerateECDSAFp() { asymmetricKeyGenerationCoordinator().handleGenerateECDSAFp(); }

    /**
     * Generate Ed25519 key pair
     */
    public void handleGenerateEd25519() { asymmetricKeyGenerationCoordinator().handleGenerateEd25519(); }

    /**
     * Alias for handleGenerateEd25519 for Modern UI
     */
    public void handleGenerateEdDSA() { asymmetricKeyGenerationCoordinator().handleGenerateEdDSA(); }

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
            LOG.warn("Certificate generation failed", e);
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
            LOG.warn("Certificate validation failed", e);
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
    @javafx.fxml.FXML private ComboBox<String> tr31OptionalBlockCombo;
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

    /**
     * Handle TR-31 Export (Wrap Key)
     */
    @FXML
    public void handleTR31Export() { tr31Coordinator().handleTR31Export(); }

    /**
     * Handle TR-31 Import (Unwrap Key)
     */
    @FXML
    public void handleTR31Import() { tr31Coordinator().handleTR31Import(); }

    /**
     * Handle Parse TR-31 Header (without unwrapping)
     */
    @FXML
    public void handleTR31ParseHeader() { tr31Coordinator().handleTR31ParseHeader(); }

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

    private void initializeRsaKexControls() { rsaKexCoordinator().initializeRsaKexControls(); }

    @FXML
    public void handleRsaKexEnvelopeToggle() { rsaKexCoordinator().handleRsaKexEnvelopeToggle(); }

    /**
     * Handle RSA Key Exchange Export (Wrap Key)
     */
    @FXML
    public void handleRsaKexExport() { rsaKexCoordinator().handleRsaKexExport(); }

    /**
     * Handle RSA Key Exchange Import (Unwrap Key)
     */
    @FXML
    public void handleRsaKexImport() { rsaKexCoordinator().handleRsaKexImport(); }

    @FXML
    public void handleRsaKexClear() { rsaKexCoordinator().handleRsaKexClear(); }

    @FXML
    public void handleRsaKexReset() { rsaKexCoordinator().handleRsaKexReset(); }

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

    /**
     * Handle TR-34 Distribute (sender side: sign then envelope the key)
     */
    @FXML
    public void handleTr34Distribute() { tr34Coordinator().handleTr34Distribute(); }

    /**
     * Handle TR-34 Receive (receiver side: decrypt then verify)
     */
    @FXML
    public void handleTr34Receive() { tr34Coordinator().handleTr34Receive(); }

    /** Fills the Receive tab's challenge nonce field with a fresh random value (two-pass, step 1). */
    @FXML
    public void handleTr34GenerateChallenge() { tr34Coordinator().handleTr34GenerateChallenge(); }

    @FXML
    public void handleTr34Clear() { tr34Coordinator().handleTr34Clear(); }

    @FXML
    public void handleTr34Reset() { tr34Coordinator().handleTr34Reset(); }

    /** KCV is only defined here for AES-length key material (16/24/32 bytes); anything else is best-effort skipped. */

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
        kdfKeyWrapCoordinator().initializeKDF();
    }

    @FXML
    public void handleGenerateKdfSalt() { kdfKeyWrapCoordinator().handleGenerateKdfSalt(); }

    /**
     * Update KDF parameters based on selected algorithm
     */

    /** Initializes the standalone AES Key Wrap laboratory panel. */
    public void initializeKeyWrap(ComboBox<String> modeCombo, CheckBox unwrapCheck, TextField kekField,
            TextField dataField, TextArea resultArea) {
        this.keyWrapModeCombo = modeCombo;
        this.keyWrapUnwrapCheck = unwrapCheck;
        this.keyWrapKekField = kekField;
        this.keyWrapDataField = dataField;
        this.keyWrapResultArea = resultArea;
        kdfKeyWrapCoordinator().initializeKeyWrap();
    }

    /** Executes wrapping or authenticated unwrapping of hexadecimal key material. */
    public void handleKeyWrap() { kdfKeyWrapCoordinator().handleKeyWrap(); }

    /**
     * Handle key derivation
     */
    public void handleDeriveKey() { kdfKeyWrapCoordinator().handleDeriveKey(); }

    /**
     * Parse data according to format
     */

    // ============================================================================
    // CMS / PKCS#7 OPERATIONS
    // ============================================================================

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
            CheckBox onlineRevocationCheck) { cmsCoordinator().initialize(new CmsCoordinator.View(inputArea, outputArea, detachedCheck, cadesBesCheck, cadesTCheck, cadesTsaUrlField, cadesTsaBox, signCertArea, signKeyArea, encryptCertArea, decryptKeyArea, signSourcePkcs11Radio, signLocalGrid, signPkcs11Box, signKeyAliasCombo, verifyDataArea, encryptSourcePkcs11Radio, encryptLocalGrid, encryptPkcs11Box, encryptKeyAliasCombo, signButton, onlineRevocationCheck)); }

    /** Shows the timestamp inputs and keeps CAdES-T dependent on CAdES-BES. */
    public void handleCadesTimestampOptionChanged() { cmsCoordinator().handleCadesTimestampOptionChanged(); }

    public void handleCMSourceChanged() { cmsCoordinator().handleCMSourceChanged(); }

    public void handleLoadCMSKeys() { cmsCoordinator().handleLoadCMSKeys(); }

    public void handleCMSEncryptSourceChanged() { cmsCoordinator().handleCMSEncryptSourceChanged(); }

    public void handleLoadCMSEncryptKeys() { cmsCoordinator().handleLoadCMSEncryptKeys(); }

    /**
     * Handle CMS Sign
     */
    public void handleCMSSign() { cmsCoordinator().handleCMSSign(); }

    /**
     * Handle CMS Verify
     */
    public void handleCMSVerify() { cmsCoordinator().handleCMSVerify(); }

    /**
     * Upgrades the CAdES-T currently shown in the CMS output area by embedding
     * user-selected CRL and optional certificate-chain evidence. It is
     * deliberately offline: CryptoCarver never discovers or downloads
     * revocation URLs on the user's behalf.
     */
    public void handleUpgradeCadesLt() { cmsCoordinator().handleUpgradeCadesLt(); }

    public void handleCMSEncrypt() { cmsCoordinator().handleCMSEncrypt(); }

    public void handleCMSDecrypt() { cmsCoordinator().handleCMSDecrypt(); }

    // Helper to parse Private Key from PEM (simplistic version for now)

    // ============================================================================
    // CERTIFICATE CHAIN VALIDATION
    // ============================================================================

    public void initializeCertificateChain(TextArea inputArea, TextArea crlArea, TextArea resultArea) { certificateChainCoordinator().initialize(new CertificateChainCoordinator.View(inputArea, crlArea, resultArea)); }

    public void handleValidateCertificateChain() { certificateChainCoordinator().handleValidateCertificateChain(); }

    // --- Global Helper Methods ---

    public void handleClear() { keySummaryCoordinator().handleClear(); }

    public void handleClearAsymmetric() { keySummaryCoordinator().handleClearAsymmetric(); }















    /**
     * Stores only canonical PEM material from the generated summary. The
     * rendered diagnostic TextAreas are deliberately not consulted here.
     */








    /** Entry point used only by ModernMainController's global Add to Shelf. */
    /** Adds the symmetric key shown in Key Generation to the Clipboard Shelf. */
    public void handleGlobalSymmetricShelfAction() { keySummaryCoordinator().handleGlobalSymmetricShelfAction(); }

    public void handleGlobalAsymmetricShelfAction(String operation) { keySummaryCoordinator().handleGlobalAsymmetricShelfAction(operation); }





    // RSA Action Handlers
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

    // ECDSA Action Handlers
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

    // DSA Action Handlers
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

    // Ed25519 Action Handlers
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



    @FXML
    private void handleKeyLabGenerate() { keyLabCoordinator().handleKeyLabGenerate(); }

    @FXML
    private void handleKeyLabImport() { keyLabCoordinator().handleKeyLabImport(); }

    @FXML
    private void handleKeyLabReveal() { keyLabCoordinator().handleKeyLabReveal(); }

    @FXML
    private void handleKeyLabCopyId() { keyLabCoordinator().handleKeyLabCopyId(); }

    @FXML
    private void handleKeyLabSaveMetadata() { keyLabCoordinator().handleKeyLabSaveMetadata(); }

    @FXML
    private void handleKeyLabArchive() { keyLabCoordinator().handleKeyLabArchive(); }

    @FXML
    private void handleKeyLabDelete() { keyLabCoordinator().handleKeyLabDelete(); }

    @FXML
    private void handleImportKeyLabMetadata() { keyLabCoordinator().handleImportKeyLabMetadata(); }

    @FXML
    private void handleExportKeyLabMetadata() { keyLabCoordinator().handleExportKeyLabMetadata(); }

    public void selectKeyInKeyLab(String keyId) { keyLabCoordinator().selectKeyInKeyLab(keyId); }

    private void setupHexValidation(TextField field) { symmetricKeyCoordinator().setupHexValidation(field); }

    private void setupHexValidation(TextArea field) { symmetricKeyCoordinator().setupHexValidation(field); }



    // =====================================================================
    // Thales Variant LMK — payShield 10K Host Programmer's Manual, chapter 7
    // =====================================================================

    @FXML private TextField thalesLmkField;
    @FXML private TextField thalesKeyTypeField;
    @FXML private ComboBox<String> thalesSchemeCombo;
    @FXML private TextField thalesClearKeyField;
    @FXML private TextField thalesCryptogramField;
    @FXML private TextField thalesCheckValueField;
    @FXML private CheckBox thalesComponentCheck;
    @FXML private TextArea thalesResultArea;

    /** Clause 7.2.3 — the worked example, every value taken from the manual. */





    @FXML
    public void handleThalesEncrypt() { paymentKeyBlockCoordinator().handleThalesEncrypt(); }

    @FXML
    public void handleThalesDecrypt() { paymentKeyBlockCoordinator().handleThalesDecrypt(); }

    @FXML
    public void handleThalesDescribe() { paymentKeyBlockCoordinator().handleThalesDescribe(); }

    @FXML
    public void handleThalesLookup() { paymentKeyBlockCoordinator().handleThalesLookup(); }

    /**
     * Fills the pane with the manual's worked example.
     *
     * <p>Useful on its own, and useful as a check: run the same FK console
     * session on a real payShield and the printed cryptogram should be the one
     * this pane produces. If they differ, this bench is wrong, not the HSM.</p>
     */
    @FXML
    public void handleThalesLoadExample() { paymentKeyBlockCoordinator().handleThalesLoadExample(); }

    /**
     * The scheme list is the variant schemes only. X and Y are the ANSI X9.17
     * schemes and S is a Key Block: different formats, so offering them here
     * would be offering to produce something this code does not produce.
     */
    private void initializeThalesControls() { paymentKeyBlockCoordinator().initializeThalesControls(); }













    // =====================================================================
    // Thales Key Block — payShield 10K Host Programmer's Manual, chapter 8
    // =====================================================================

    @FXML private TextArea keyBlockInputArea;
    @FXML private TextArea keyBlockResultArea;
    @FXML private TextField keyBlockLmkField;

    /** Clause 8.5.1.8, the header the manual works through, padded out to the
     *  72 characters it declares. */
    /** payShield manual clause 8.8.1: the published 3DES Key Block test LMK. */

    /** A real block under that LMK, generated with an external tool and pinned in the tests. */


    @FXML
    public void handleKeyBlockInspect() { paymentKeyBlockCoordinator().handleKeyBlockInspect(); }

    /**
     * Unwraps with the Key Block LMK, which the manual never explains how to
     * turn into the encryption and MAC keys. The derivation here was taken from
     * the external tool and is pinned by a test; see ThalesKeyBlockOperations.
     */
    @FXML
    public void handleKeyBlockUnwrap() { paymentKeyBlockCoordinator().handleKeyBlockUnwrap(); }

    @FXML
    public void handleKeyBlockExample() { paymentKeyBlockCoordinator().handleKeyBlockExample(); }

    // =====================================================================
    // Atalla Key Block — Utimaco AJ560-9004A
    // =====================================================================

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

    /** The external tool vector for a 24-byte key, pinned in AtallaAkbOperationsTest. */
    @FXML
    public void handleAtallaExample() { paymentKeyBlockCoordinator().handleAtallaExample(); }


}
