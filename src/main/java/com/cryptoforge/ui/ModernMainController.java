package com.cryptoforge.ui;

import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import javafx.fxml.FXML;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.io.File;
import java.util.Optional; // For Dialogs
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import javafx.scene.text.TextFlow;
import com.cryptoforge.asn1.ASN1Parser;
import com.cryptoforge.asn1.ASN1TreeNode;
import com.cryptoforge.model.AppDiagnostics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// Attempt to use DataConverter if available, otherwise will rely on local helpers or standard libs
import com.cryptoforge.util.DataConverter;

/**
 * Modern Main Controller for Rail + SidePanel navigation
 */
public class ModernMainController implements StatusReporter, OperationNavigator {

    static void writeDiagnosticsReport(java.nio.file.Path report, String content) throws Exception {
        java.nio.file.Files.writeString(report, content);
    }

    @FXML private javafx.scene.control.Label contentPlaceholderLabel;
    @FXML private javafx.scene.layout.VBox jose;
    @FXML private javafx.scene.control.TitledPane asn1;
    @FXML private javafx.scene.Node dukptTdesOptionsBox;
    @FXML private javafx.scene.Node dukptTdesUsageCombo;
    @FXML private javafx.scene.Node dukptAesOptionsBox;
    @FXML private javafx.scene.Node dukptAesPinBox;


    @FXML private GenericController genericContainerController;

    private static final Logger LOG = LoggerFactory.getLogger(ModernMainController.class);
    private final PauseTransition statusResetTimer = new PauseTransition(Duration.seconds(3));
    private final ExpandedTextViewer expandedTextViewer = new ExpandedTextViewer();
    private final ExpandedTableViewer expandedTableViewer = new ExpandedTableViewer();
    private TextArea lastFocusedResultArea;
    /** Most recently changed output/report area, tracked independently from keyboard focus. */
    private TextArea lastUpdatedResultArea;
    private final java.util.Set<TextArea> resultViewerAreas = java.util.Collections.newSetFromMap(
            new java.util.IdentityHashMap<>());
    private TableView<?> lastFocusedTable;
    /**
     * Snapshot published by the latest completed operation.  It is deliberately
     * separate from the last focused text area: focus is a navigation concern,
     * while publishing is the authoritative completion event.  Without this a
     * still-visible result area from a previous accordion pane could be shown
     * by Expand Result after a different operation completed.
     */
    private String lastPublishedResultText = "";
    private String lastPublishedOperation = "";
    private com.cryptoforge.model.OperationResult lastPublishedResultSnapshot;

    @FXML
    private BorderPane mainPane;
    @FXML
    private NavigationRail navigationRail;
    @FXML
    private SidePanel sidePanel;
    @FXML
    private VBox mainContentArea;
    @FXML
    private ScrollPane mainScrollPane;
    @FXML
    private VBox contentContainer;
    @FXML
    private VBox symmetricKeysContainer;
    @FXML
    private VBox asymmetricKeysContainer;
    @FXML
    private VBox certificatesContainer;
    @FXML
    private VBox inspectorPanel;
    @FXML
    private VBox inspectorDetailsContainer;

    // CIPHER UI
    @FXML
    private VBox cipherContainer;
    @FXML
    private ComboBox<String> symmetricAlgorithmCombo;
    @FXML
    private ComboBox<String> cipherModeCombo;
    @FXML
    private ComboBox<String> paddingCombo;
    @FXML
    private ComboBox<String> symKeySourceCombo;
    @FXML
    private ComboBox<String> symHsmKeyCombo;
    @FXML
    private TextField symmetricKeyField;
    @FXML
    private TextField ivField;
    @FXML
    private TextField gcmTagField;
    @FXML
    private TextField aadField;

    // Cipher - Streaming files
    @FXML private ComboBox<String> fileCipherAlgorithmCombo;
    @FXML private TextField fileCipherSourceField;
    @FXML private TextField fileCipherDestinationField;
    @FXML private TextField fileCipherTagField;
    @FXML private TextField fileCipherKeyField;
    @FXML private TextField fileCipherNonceField;
    @FXML private TextField fileCipherAadField;
    @FXML private TextArea fileCipherResultArea;
    @FXML
    private ComboBox<String> rsaPaddingCombo;
    @FXML
    private TextArea privateKeyArea; // New manual key load area
    @FXML
    private TextArea publicKeyArea; // New manual key load area
    @FXML
    private TextArea signaturePrivateKeyArea; // New Signatures
    @FXML
    private TextArea signaturePublicKeyArea; // New Signatures
    @FXML
    private TextArea cipherInputArea;
    @FXML
    private TextArea cipherOutputArea;

    // JWE UI
    @FXML
    private ComboBox<String> jweKeyAlgoCombo;
    @FXML
    private ComboBox<String> jweContentAlgoCombo;
    @FXML
    private TextArea jwePublicKeyArea;
    @FXML
    private TextArea jwePayloadArea;
    @FXML
    private TextArea jweOutputArea;
    @FXML
    private TextArea jweInputArea;
    @FXML
    private TextArea jwePrivateKeyArea;
    @FXML
    private TextArea jweDecodedHeaderArea;
    @FXML
    private TextArea jweDecodedPayloadArea;
    // Visual Breakdown
    @FXML
    private TextArea jweHeaderArea;
    @FXML
    private TextArea jweEncryptedKeyArea;
    @FXML
    private TextArea jweDecryptedKeyArea;
    @FXML
    private TextArea jweIVArea;
    @FXML
    private TextArea jweCiphertextArea;
    @FXML
    private TextArea jweAuthTagArea;

    @FXML
    private Label jweStatusLabel;
    @FXML
    private CheckBox jweCompressCheck;

    // Claims Builder
    @FXML
    private TextField jwtIssField;
    @FXML
    private TextField jwtSubField;
    @FXML
    private TextField jwtAudField;
    @FXML
    private TextField jwtExpField;

    // Nested JWT
    @FXML
    private ComboBox<String> nestedSignAlgoCombo;
    @FXML
    private TextArea nestedSigningKeyArea;
    @FXML
    private TextArea nestedPayloadArea;
    @FXML
    private ComboBox<String> nestedKeyAlgoCombo;
    @FXML
    private ComboBox<String> nestedContentAlgoCombo;
    @FXML
    private CheckBox nestedCompressCheck;
    @FXML
    private TextArea nestedEncryptionKeyArea;
    @FXML
    private TextArea nestedOutputArea;

    // Header labels
    @FXML
    private Label contentTitleLabel;
    @FXML
    private Label contentSubtitleLabel;

    // Inspector labels
    @FXML
    private Label inputBytesLabel;
    @FXML
    private Label outputBytesLabel;
    @FXML
    private Label operationLabel;
    @FXML
    private Label securityTipLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private VBox historyContainer;
    @FXML
    private VBox historyView;
    @FXML
    private HistoryController historyViewController;

    @FXML
    private VBox clipboardShelf;
    @FXML
    private ClipboardShelfController clipboardShelfController;




    // Saved Sessions
    @FXML
    private VBox savedSessionsContainer;

    // JOSE
    @FXML
    private VBox jwtSection;
    @FXML
    private VBox jweSection;
    @FXML
    private VBox jwkSection; // Restored
    @FXML
    private ComboBox<String> jwkKeyTypeCombo;
    @FXML
    private TextField jwkKeyIdField;
    @FXML
    private TextArea jwkInputArea;
    @FXML
    private Label jwkInputLabel;
    @FXML
    private Button pemToJwkBtn;
    @FXML
    private Button jwkToPemBtn;
    @FXML
    private TextArea jwkOutputArea;
    // JWKS
    @FXML
    private TextArea jwksArea;
    @FXML
    private ComboBox<String> jwksRotateAlgoCombo;

    @FXML
    private TableView<SimpleAlgo> jwaTable;
    @FXML
    private TableColumn<SimpleAlgo, String> jwaNameCol;
    @FXML
    private TableColumn<SimpleAlgo, String> jwaTypeCol;
    @FXML
    private TableColumn<SimpleAlgo, String> jwaDescCol;
    @FXML
    private VBox jwaSection;
    @FXML
    private VBox inspectorSection;
    @FXML
    private TextArea inspectorInputArea;
    @FXML
    private TextFlow inspectorOutputFlow;
    @FXML
    private TextArea jwtPayloadArea;
    @FXML
    private TextArea jwtOutputArea;
    // New Fields
    @FXML
    private ComboBox<String> jwtAlgoCombo;
    @FXML
    private TextArea jwtKeyArea;
    @FXML
    private TextArea jwtValidateTokenArea;
    @FXML
    private TextArea jwtValidateKeyArea;
    @FXML
    private TextArea jwtDecodedHeaderArea;
    @FXML
    private TextArea jwtDecodedPayloadArea;
    @FXML
    private Label jwtStatusLabel;
    @FXML private TextArea detachedPayloadArea, detachedTokenArea, detachedSigningKeyArea, detachedVerificationKeyArea;
    @FXML private ComboBox<String> detachedAlgoCombo;
    @FXML private Label detachedStatusLabel;

    @FXML
    private void handleGenerateDetachedJWS() { if (joseController != null) joseController.generateDetachedJWS(detachedPayloadArea.getText(), detachedAlgoCombo.getValue(), detachedSigningKeyArea.getText(), "Compact", false, detachedTokenArea); }
    @FXML
    private void handleVerifyDetachedJWS() { if (joseController != null) joseController.verifyDetachedJWS(detachedTokenArea.getText(), detachedPayloadArea.getText(), detachedAlgoCombo.getValue(), detachedVerificationKeyArea.getText(), detachedStatusLabel); }

    // Enterprise Features
    @FXML
    private ComboBox<String> jwtTemplateCombo;
    @FXML
    private TextField jwtExpectedIssField;
    @FXML
    private TextField jwtExpectedAudField;
    @FXML
    private TextField jwtClockSkewField;
    @FXML
    private CheckBox jwtCheckExpiryCheck;

    // Removed duplicate jwkOutputArea
    @FXML
    private VBox savedSessionsList;

    // Toolbar
    @FXML
    private ComboBox<String> inputFormatCombo;

    // Managers
    private com.cryptoforge.model.HistoryManager historyManager;
    private com.cryptoforge.model.SavedSessionsManager savedSessionsManager;
    private String currentActiveOperation = "Dashboard"; // Defaul
    @FXML
    private ComboBox<String> outputFormatCombo;
    @FXML
    private ComboBox<String> asymmetricInputFormatCombo; // Added for CipherController
    @FXML
    private ComboBox<String> asymmetricOutputFormatCombo; // Added for CipherController

    // Symmetric Keys - Key Generation
    @FXML
    private ComboBox<String> keyTypeCombo;
    @FXML
    private CheckBox forceOddParityCheck;
    @FXML
    private TextArea generatedKeyField;

    // Symmetric Keys - Validation
    @FXML
    private TextField keyInputField;
    @FXML
    private TextArea validationResultArea;
    @FXML
    private TextArea keyMaterialInputArea;
    @FXML
    private TextArea keyMaterialReportArea;
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
    @FXML private ComboBox<String> pkcs11ProfileCombo;
    @FXML private TextField pkcs11LibraryField;
    @FXML private TextField pkcs11SlotField;
    @FXML private PasswordField pkcs11PinField;
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

    // Symmetric Keys - Key Sharing
    @FXML
    private TextArea keyToSplitField;
    @FXML
    private ComboBox<String> numComponentsCombo;
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
    @FXML
    private TextArea componentResultsArea;

    // Symmetric Keys - KDF
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

    // Symmetric Keys - AES Key Wrap
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

    // TR-31 Key Blocks
    @FXML
    private TextField tr31KbpkExportField;
    @FXML
    private TextField tr31KeyToWrapField;
    @FXML
    private ComboBox<String> tr31VersionCombo;
    @FXML
    private ComboBox<String> tr31UsageCombo;
    @FXML
    private ComboBox<String> tr31AlgorithmCombo;
    @FXML
    private ComboBox<String> tr31ModeCombo;
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

    // Asymmetric Keys - RSA
    @FXML
    private ComboBox<Integer> rsaKeySizeCombo;
    @FXML
    private TextArea rsaPublicKeyArea;
    @FXML
    private TextArea rsaPrivateKeyArea;

    // Asymmetric Keys - ECDSA
    @FXML
    private ComboBox<String> ecdsaCurveCombo;
    @FXML
    private TextArea ecdsaPublicKeyArea;
    @FXML
    private TextArea ecdsaPrivateKeyArea;

    // Asymmetric Keys - DSA
    @FXML
    private ComboBox<String> dsaKeySizeCombo;
    @FXML
    private TextArea dsaPublicKeyArea;
    @FXML
    private TextArea dsaPrivateKeyArea;

    // Asymmetric Keys - EdDSA
    @FXML
    private TextArea eddsaPublicKeyArea;
    @FXML
    private TextArea eddsaPrivateKeyArea;

    // Certificates
    @FXML
    private TextField certCNField;
    @FXML
    private TextField certOrgField;
    @FXML
    private TextField certOUField;
    @FXML
    private TextField certCountryField;
    @FXML
    private TextField certStateField;
    @FXML
    private TextField certLocalityField;
    @FXML
    private TextField certEmailField;
    @FXML
    private TextField certValidityField;
    @FXML
    private ComboBox<String> certKeyTypeCombo;
    @FXML
    private ComboBox<String> certSignAlgoCombo;
    @FXML
    private TextArea certOutputArea;
    @FXML private TextField certSanDnsField;
    @FXML private TextField certSanIpField;
    @FXML private CheckBox certRootCaCheck;
    @FXML
    private TextArea certInputArea;
    @FXML
    private TextArea certParseResultArea;
    @FXML private TextArea certCompareLeftArea;
    @FXML private TextArea certCompareRightArea;
    @FXML private TextArea certCompareResultArea;
    @FXML private TextArea certIssueCsrArea;
    @FXML private TextArea certIssueCaCertArea;
    @FXML private TextArea certIssueCaKeyArea;
    @FXML private TextField certIssueValidityField;
    @FXML private TextField certIssueSignatureField;
    @FXML private TextArea certIssueResultArea;
    @FXML private CheckBox certIssueIntermediateCaCheck;
    @FXML private TextField certIssuePathLengthField;

    // Certificate Chain
    @FXML
    private TextArea chainInputArea;
    @FXML
    private TextArea chainResultArea;

    // Validate Certificate
    @FXML
    private TextArea valCertInput;
    @FXML
    private TextArea valIssuerInput;
    @FXML
    private TextArea valResultArea;

    // CMS Operations
    @FXML
    private TextArea cmsInputArea;
    @FXML
    private TextArea cmsOutputArea;
    @FXML
    private CheckBox cmsDetachedCheck;
    @FXML
    private CheckBox cmsCadesBesCheck;
    @FXML
    private CheckBox cmsCadesTCheck;
    @FXML
    private TextField cmsCadesTsaUrlField;
    @FXML
    private javafx.scene.layout.HBox cmsCadesTsaBox;
    @FXML
    private TextArea cmsSignCertArea;
    @FXML
    private TextArea cmsSignKeyArea;
    @FXML
    private javafx.scene.control.ToggleGroup cmsSignSourceToggleGroup;
    @FXML
    private RadioButton cmsSignSourceLocalRadio;
    @FXML
    private RadioButton cmsSignSourcePkcs11Radio;
    @FXML
    private TextArea cmsVerifyDataArea;
    @FXML
    private javafx.scene.layout.GridPane cmsSignLocalGrid;
    @FXML
    private javafx.scene.layout.HBox cmsSignPkcs11Box;
    @FXML
    private javafx.scene.control.ComboBox<String> cmsSignKeyAliasCombo;

    @FXML
    private javafx.scene.control.ToggleGroup cmsEncryptSourceToggleGroup;
    @FXML
    private RadioButton cmsEncryptSourceLocalRadio;
    @FXML
    private RadioButton cmsEncryptSourcePkcs11Radio;
    @FXML
    private javafx.scene.layout.GridPane cmsEncryptLocalGrid;
    @FXML
    private javafx.scene.layout.HBox cmsEncryptPkcs11Box;
    @FXML
    private javafx.scene.control.ComboBox<String> cmsEncryptKeyAliasCombo;

    // Output views
    @FXML
    private TextArea cmsEncryptCertArea;
    @FXML
    private TextArea cmsDecryptKeyArea;

    // ASN.1 Decoder
    @FXML
    private TitledPane asn1Pane;
    @FXML
    private TabPane asn1TabPane;
    @FXML
    private ComboBox<String> asn1InputFormatCombo;
    @FXML
    private ComboBox<String> asn1TypeCombo;
    @FXML
    private TextArea asn1InputArea;
    @FXML
    private TextArea asn1TreeArea;
    @FXML
    private TextArea asn1DetailsArea;
    @FXML
    private Label asn1StatusLabel;

    // ASN.1 Encoder
    @FXML
    private ComboBox<String> asn1EncodeTypeCombo;
    @FXML
    private ComboBox<String> asn1EncodeInputFormatCombo;
    @FXML
    private TextArea asn1EncodeInputArea;
    @FXML
    private TextArea asn1EncodeOutputArea;

    // Generic Tab FXML Fields
    @FXML
    private Accordion genericContainer;

    // Post-Quantum UI
    @FXML private VBox postQuantumContainer;
    @FXML private PostQuantumController postQuantumContainerController;

    // XML Security UI
    @FXML private VBox xmlSecurityContainer;
    @FXML private TextField xmlSignInputPathField;
    @FXML private TextField xmlSignKeyPathField;
    @FXML private PasswordField xmlSignKeyPasswordField;
    @FXML private ComboBox<String> xmlSignKeyAliasCombo;
    @FXML private ComboBox<String> xmlSignLevelCombo;
    @FXML private ComboBox<String> xmlSignPackagingCombo;
    // New Controllers
    @FXML private XMLSignatureController xmlSecurityContainerController;

    // Generic Utilities
    // Hashing
    @FXML
    private ComboBox<String> hashAlgorithmCombo;
    // Legacy generic fields moved to GenericController.
    // The FXML fx:id bindings are now handled by genericContainerController.    @FXML
    private TextField uuidOutputField;

    // Authentication Tab FXML Fields
    @FXML
    private VBox authenticationContainer;

    // Authentication - Digital Signatures
    @FXML
    private ComboBox<String> signatureAlgorithmCombo;
    @FXML
    private Label signatureKeyStatusLabel;
    @FXML
    private TextField signatureVerifyField;

    // Authentication - MAC
    @FXML
    private ComboBox<String> authMacAlgorithmCombo;
    @FXML
    private ComboBox<String> macKeySourceCombo;
    @FXML
    private ComboBox<String> macHsmKeyCombo;
    @FXML
    private TextField authMacKeyField;
    @FXML
    private Label authMacKeyInfoLabel;
    @FXML
    private TextField authMacNonceField;
    @FXML
    private ComboBox<String> authMacTruncationCombo;
    @FXML
    private TextField authMacVerifyField;
    @FXML
    private TextArea authInputArea;
    @FXML
    private TextArea authOutputArea;

    // Payments Tab FXML Fields
    @FXML
    private VBox paymentsContainer;

    // Payments - Clear PIN Blocks
    @FXML
    private TextField pinField;
    @FXML
    private TextField panFieldEncode;
    @FXML
    private ComboBox<String> pinBlockFormatCombo;
    @FXML
    private TextField pinBlockField;
    @FXML
    private TextField panFieldDecode;
    @FXML
    private ComboBox<String> pinBlockFormatDecodeCombo;
    @FXML
    private TextArea pinBlockResultArea;

    // Payments - ISO 0
    @FXML
    private ComboBox<String> encPinBlockFormatCombo; // New Format Combo
    @FXML
    private TextField encPinField;
    @FXML
    private TextField encPanFieldEncode;
    @FXML
    private TextField encPinBlockKeyField;
    @FXML
    private TextField encPinBlockFieldDecode;
    @FXML
    private TextField encPanFieldDecode;
    @FXML
    private TextField encPinBlockKeyFieldDecode;
    @FXML
    private TextArea encResultArea;

    // PIN Generators (Offset & PVV)
    @FXML
    private TextField genOffsetPvkField;
    @FXML
    private TextField genOffsetDecTableField;
    @FXML
    private TextField genOffsetPanField;
    @FXML
    private TextField genOffsetPinField;
    @FXML
    private TextArea genOffsetResultArea;

    @FXML
    private TextField genPvvPvkField;
    @FXML
    private TextField genPvvPanField;
    @FXML
    private TextField genPvvPinField;
    @FXML
    private TextField genPvvKeyIndexField;
    @FXML
    private TextArea genPvvResultArea;

    // Derive PIN from PVV Fields
    @FXML
    private TextField derivePvvPvkField;
    @FXML private TextField dukptBdkField, dukptKsnField, dukptAesPinBlockField;
    @FXML private ComboBox<String> dukptSchemeCombo, dukptAesUsageCombo, dukptAesKeyTypeCombo, dukptAesPinOperationCombo;
    @FXML private TextArea dukptResultArea;
    @FXML
    private TextField derivePvvPanField;
    @FXML
    private TextField derivePvvTargetPvvField;
    @FXML
    private TextField derivePvvKeyIndexField;
    @FXML
    private TextArea derivePvvResultArea;

    // Payments - CVV
    @FXML
    private ComboBox<String> cvvTypeCombo;
    @FXML
    private TextField cvkAField;
    @FXML
    private TextField cvkBField;
    @FXML
    private TextField panFieldCvv;
    @FXML
    private TextField expiryDateField;
    @FXML
    private TextField serviceCodeField;
    @FXML
    private TextField atcField; // Added for dCVV
    @FXML
    private TextArea cvvResultArea;

    // Payments - PIN Generation (IBM 3624)
    @FXML
    private TextField ibm3624PvkField;
    @FXML
    private TextField ibm3624ConvTableField;
    @FXML
    private TextField ibm3624OffsetField;
    @FXML
    private TextField ibm3624PanField;
    @FXML
    private TextField ibm3624PinVerifyField;
    @FXML
    private TextArea ibm3624ResultArea;
    @FXML
    private TextField ibm3624StartField;
    @FXML
    private TextField ibm3624LengthField;
    @FXML
    private TextField ibm3624PadField;

    // Explicit Validation Config for Offset (New)
    @FXML
    private TextField genOffsetStartField;
    @FXML
    private TextField genOffsetLengthField;
    @FXML
    private TextField genOffsetPadField;

    // IBM 3624 Generate Offset Fields (Restored)
    @FXML
    private TextField ibm3624PvkFieldOffset;
    @FXML
    private TextField ibm3624ConvTableFieldOffset;
    @FXML
    private TextField ibm3624PinFieldOffset;
    @FXML
    private TextField ibm3624PanFieldOffset;
    @FXML
    private TextField ibm3624PanOffsetFieldOffset;
    @FXML
    private TextField ibm3624PanLengthFieldOffset;
    @FXML
    private TextField ibm3624PanPadFieldOffset;
    @FXML
    private TextArea ibm3624OffsetResultArea;

    // EMV Operations - Session Key
    @FXML
    private TextField imkField;
    @FXML
    private TextField panFieldSession;
    @FXML
    private TextField panSeqFieldSession;
    @FXML
    private TextField emvAtcField; // Renamed to avoid collision with CVV atcField
    @FXML
    private TextArea sessionKeyResultArea;

    // EMV Operations - ARQC
    @FXML
    private TextField skARQCField;
    @FXML
    private TextField amountField;
    @FXML
    private TextField currencyField;
    @FXML
    private TextField countryField;
    @FXML
    private TextField atcARQCField;
    @FXML
    private TextField tvrField;
    @FXML
    private TextField txDateField;
    @FXML
    private TextField txTypeField;
    @FXML
    private TextField unField;
    @FXML
    private TextArea arqcResultArea;
    @FXML
    private TextArea arqcTerminalDataField; // New
    @FXML
    private TextField amountOtherField; // New
    @FXML
    private TextField iccDataField; // New
    @FXML
    private ComboBox<String> arqcPaddingMethodCombo; // New

    // EMV Operations - ARPC
    @FXML
    private TextField skARPCField;
    @FXML
    private TextField arqcField;
    @FXML
    private TextField arcField;
    @FXML
    private TextField csuField;
    @FXML
    private TextField propAuthDataField; // New
    @FXML
    private ComboBox<String> arpcMethodCombo;
    @FXML
    private TextArea arpcResultArea;

    // EMV Operations - Track 2
    @FXML
    private TextField panTrack2Field;
    @FXML
    private TextField expiryTrack2Field;
    @FXML
    private TextField serviceCodeFieldTrack2;
    @FXML
    private TextField discretionaryDataField;
    @FXML
    private TextField track2InputField;
    @FXML
    private TextArea track2ResultArea;

    @FXML
    private VBox emvContainer;
    @FXML private TextArea emvTlvInputArea, emvTlvResultArea;
    @FXML private TextField emvDolTemplateField;
    @FXML private TextArea emvDolValuesArea, emvDolResultArea;

    // Controllers
    private KeysController keysController;
    private PaymentsController paymentsController;
    private EMVController emvController;
    private CipherController cipherController;
    private AuthenticationController authenticationController;
    @FXML private JOSEController joseController;
    @FXML private ASN1Controller asn1Controller;
    @FXML private VBox openPgpContainer;
    @FXML private OpenPgpController openPgpContainerController;
    @FXML private VBox padesContainer;
    @FXML private PadesController padesContainerController;
    @FXML private VBox asicContainer;
    @FXML private AsicController asicContainerController;

    // Store parsed data for export potential
    private byte[] asn1LastParsedData;

    @FXML private CmsInspectorController cmsInspectorController;
    @FXML private javafx.scene.control.TitledPane cmsInspector;

    @FXML
    private MenuBar mainMenuBar;

    @FXML
    public void initialize() {
        if (cmsInspectorController != null) {
            cmsInspectorController.init(this);
        }
        if (asn1Controller != null) {
            asn1Controller.init(this);
        }
        if (detachedAlgoCombo != null) { detachedAlgoCombo.getItems().setAll("HS256", "RS256", "ES256", "ES384", "ES512"); detachedAlgoCombo.setValue("ES256"); }
        System.out.println("ModernMainController initializing...");
        com.cryptoforge.model.ClipboardShelfManager.getInstance().setReporter(this);

        setupLaboratoryMenu();

        // Populate ComboBox items
        inputFormatCombo.getItems().setAll("Text (UTF-8)", "Hexadecimal", "Base64", "Binary", "Decimal");
        outputFormatCombo.getItems().setAll("Text (UTF-8)", "Hexadecimal", "Base64", "Binary", "Decimal");
        asymmetricInputFormatCombo.getItems().setAll("Text (UTF-8)", "Hexadecimal", "Base64", "Binary", "Decimal");
        asymmetricOutputFormatCombo.getItems().setAll("Text (UTF-8)", "Hexadecimal", "Base64", "Binary", "Decimal");

        // Connect Rail to SidePanel
        navigationRail.setSidePanel(sidePanel);

        // Handle item selection from SidePanel
        sidePanel.setOnItemSelected(this::handleItemSelected);

        // Set default selections
        inputFormatCombo.setValue("Hexadecimal");
        outputFormatCombo.setValue("Hexadecimal");
        asymmetricInputFormatCombo.setValue("Hexadecimal");
        asymmetricOutputFormatCombo.setValue("Hexadecimal");

        // Synchronize Global and Asymmetric Input/Output Formats
        asymmetricInputFormatCombo.valueProperty().bindBidirectional(inputFormatCombo.valueProperty());
        asymmetricOutputFormatCombo.valueProperty().bindBidirectional(outputFormatCombo.valueProperty());

        // Initialize ASN.1 Encoder
        if (asn1EncodeTypeCombo != null) {
            asn1EncodeTypeCombo.getItems().addAll(
                    "INTEGER",
                    "OCTET STRING",
                    "BIT STRING",
                    "OBJECT IDENTIFIER (OID)",
                    "UTF8String",
                    "PrintableString",
                    "IA5String",
                    "BOOLEAN",
                    "NULL",
                    "SEQUENCE (from Hex content)",
                    "SET (from Hex content)");
            asn1EncodeTypeCombo.setValue("UTF8String");

            asn1EncodeInputFormatCombo.getItems().addAll("Text", "Hex", "Base64");
            asn1EncodeInputFormatCombo.setValue("Text");

            // Disable input area for NULL
            asn1EncodeTypeCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    if (newVal.equals("NULL")) {
                        asn1EncodeInputArea.setDisable(true);
                        asn1EncodeInputArea.setText("");
                        asn1EncodeInputFormatCombo.setDisable(true);
                    } else if (newVal.contains("SEQUENCE") || newVal.contains("SET")) {
                        asn1EncodeInputArea.setDisable(false);
                        asn1EncodeInputFormatCombo.setValue("Hex"); // Wrappers usually wrap encoded hex
                        asn1EncodeInputFormatCombo.setDisable(true); // Enforce Hex for wrapper convenience
                    } else {
                        asn1EncodeInputArea.setDisable(false);
                        asn1EncodeInputFormatCombo.setDisable(false);
                    }
                }
            });
        }

        // Initialize History
        initializeHistory();

        // Load symmetric keys content (default)
        loadSymmetricKeysContent();
        loadCipherContent();
        loadAuthenticationContent();
        loadPaymentsContent();
        loadEMVContent();
        if (genericContainerController != null) genericContainerController.setStatusReporter(this);
        if (openPgpContainerController != null) openPgpContainerController.setStatusReporter(this);
        if (padesContainerController != null) padesContainerController.setStatusReporter(this);
        if (asicContainerController != null) asicContainerController.setStatusReporter(this);
        if (genericContainerController != null && genericContainerController.getKeyCertificateWorkbenchController() != null) {
            genericContainerController.getKeyCertificateWorkbenchController().setStatusReporter(this);
        }
        loadPostQuantumContent();
        loadXMLSecurityContent();

        // Show the symmetric keys by defaul
        showSymmetricKeys();

        // Apply default font size
        applyFontSize();
        // All static FXML content is available at this point. Install now so a
        // result written immediately after loading cannot miss the listener.
        // The method is idempotent for any later/dynamic invocation.
        installResultViewerSupport();
        Platform.runLater(this::installTableViewerSupport);

        System.out.println("ModernMainController initialized successfully!");
    }

    private void loadCipherContent() {
        if (cipherController == null) {
            cipherController = new CipherController(this,
                    cipherInputArea, cipherOutputArea,
                    inputFormatCombo, outputFormatCombo,
                    publicKeyArea, privateKeyArea);

            // Set symmetric UI components
            cipherController.setSymmetricAlgorithmCombo(symmetricAlgorithmCombo);
            cipherController.setCipherModeCombo(cipherModeCombo);
            cipherController.setPaddingCombo(paddingCombo);
            cipherController.setSymmetricKeyField(symmetricKeyField);
            cipherController.setSymKeySourceCombo(symKeySourceCombo);
            cipherController.setSymHsmKeyCombo(symHsmKeyCombo);
            cipherController.setIVField(ivField);
            cipherController.setGcmTagField(gcmTagField);
            cipherController.setAADField(aadField);
            cipherController.setFileCipherFields(fileCipherAlgorithmCombo, fileCipherSourceField, fileCipherDestinationField,
                    fileCipherTagField, fileCipherKeyField, fileCipherNonceField, fileCipherAadField, fileCipherResultArea);

            // Set asymmetric components
            cipherController.setRSACombos(rsaPaddingCombo, asymmetricInputFormatCombo, asymmetricOutputFormatCombo);
        }
    }

    private void loadAuthenticationContent() {
        try {
            authenticationController = new AuthenticationController(
                    this,
                    authInputArea,
                    authOutputArea,
                    inputFormatCombo,
                    outputFormatCombo);

            // Initialize Digital Signatures
            authenticationController.initializeSignatures(
                    signatureAlgorithmCombo,
                    signatureKeyStatusLabel,
                    signatureVerifyField,
                    signaturePrivateKeyArea,
                    signaturePublicKeyArea);

            // Initialize MAC
            authenticationController.initializeMAC(
                    authMacAlgorithmCombo,
                    authMacKeyField,
                    authMacKeyInfoLabel,
                    authMacTruncationCombo,
                    authMacVerifyField,
                    authMacNonceField,
                    macKeySourceCombo,
                    macHsmKeyCombo);
        } catch (Exception e) {
            System.err.println("Error initializing AuthenticationController: " + e.getMessage());
            LOG.error("Modern UI operation failed", e);
        }
    }

    private void loadEMVContent() {
        if (emvController == null) {
            emvController = new EMVController();
            emvController.initialize(
                    this,
                    imkField, panFieldSession, panSeqFieldSession, emvAtcField, sessionKeyResultArea,
                    skARQCField, amountField, currencyField, countryField, atcARQCField, tvrField, txDateField,
                    txTypeField, unField, arqcResultArea,
                    arqcTerminalDataField, amountOtherField, iccDataField, arqcPaddingMethodCombo, // New args
                    skARPCField, arqcField, arcField, csuField, propAuthDataField, arpcMethodCombo, arpcResultArea,
                    panTrack2Field, expiryTrack2Field, serviceCodeFieldTrack2, discretionaryDataField, track2InputField,
                    track2ResultArea);
            emvController.initializeTlvInspector(emvTlvInputArea, emvTlvResultArea);
            emvController.initializeDolBuilder(emvDolTemplateField, emvDolValuesArea, emvDolResultArea);
        }
    }

    private void loadPaymentsContent() {
        try {
            paymentsController = new PaymentsController();

            // Initialize with ModernMainController (PaymentsController supports both
            // MainController and ModernMainController)
            paymentsController.initialize(
                    this, // mainController
                    pinField,
                    panFieldEncode,
                    pinBlockField,
                    panFieldDecode,
                    pinBlockFormatCombo,
                    pinBlockFormatDecodeCombo,
                    pinBlockResultArea,
                    cvkAField,
                    cvkBField,
                    panFieldCvv,
                    expiryDateField,
                    serviceCodeField,
                    atcField,
                    cvvTypeCombo,
                    cvvResultArea,
                    null, // macAlgorithmCombo (not used in Modern UI for Payments)
                    null, // macKeyField
                    null, // macDataField
                    null, // macResultArea
                    // New Encrypted PIN Fields
                    encPinBlockFormatCombo,
                    encPinField,
                    encPanFieldEncode,
                    encPinBlockKeyField,
                    encPinBlockFieldDecode,
                    encPanFieldDecode,
                    encPinBlockKeyFieldDecode,
                    encResultArea,
                    // New PIN Generator Fields
                    genOffsetPvkField,
                    genOffsetDecTableField,
                    genOffsetPanField,
                    genOffsetPinField,
                    genOffsetResultArea,
                    genOffsetStartField,
                    genOffsetLengthField,
                    genOffsetPadField,

                    genPvvPvkField,
                    genPvvPanField,
                    genPvvPinField,
                    genPvvKeyIndexField,
                    genPvvResultArea,
                    // Derive PIN from PVV Controls
                    derivePvvPvkField,
                    derivePvvPanField,
                    derivePvvTargetPvvField,
                    derivePvvKeyIndexField,
                    derivePvvResultArea);

            // Initialize IBM 3624 controls separately
            paymentsController.initializeIbm3624Controls(
                    ibm3624PvkField,
                    ibm3624ConvTableField,
                    ibm3624OffsetField,
                    ibm3624PanField,
                    ibm3624PinVerifyField,
                    ibm3624ResultArea,
                    ibm3624StartField,
                    ibm3624LengthField,
                    ibm3624PadField);
            paymentsController.initializeDukptControls(dukptBdkField, dukptKsnField, dukptResultArea, dukptSchemeCombo, dukptAesUsageCombo, dukptAesKeyTypeCombo, null, dukptAesPinBlockField, dukptAesPinOperationCombo, null, null, null);

            // Populate PIN Block format combos
            if (pinBlockFormatCombo != null) {
                pinBlockFormatCombo.getItems().setAll(
                        "ISO 0 (ANSI X9.8)",
                        "ISO 1 (ANSI X9.8)",
                        "ISO 2 (No PAN)",
                        "ISO 3 (EMV)",
                        "ISO 4 (EMV 2000)");
                pinBlockFormatCombo.setValue("ISO 0 (ANSI X9.8)");
            }

            if (pinBlockFormatDecodeCombo != null) {
                pinBlockFormatDecodeCombo.getItems().setAll(
                        "ISO 0 (ANSI X9.8)",
                        "ISO 1 (ANSI X9.8)",
                        "ISO 2 (No PAN)",
                        "ISO 3 (EMV)",
                        "ISO 4 (EMV 2000)");
                pinBlockFormatDecodeCombo.setValue("ISO 0 (ANSI X9.8)");
            }

            // Set default conversion table
            if (ibm3624ConvTableField != null) {
                ibm3624ConvTableField.setText("0123456789012345");
            }

            System.out.println("PaymentsController initialized successfully!");
        } catch (Exception e) {
            System.err.println("Error initializing PaymentsController: " + e.getMessage());
            LOG.error("Modern UI operation failed", e);
        }
    }

    private void loadSymmetricKeysContent() {
        try {
            // Initialize KeysController with all Symmetric Keys components
            keysController = new KeysController();
            keysController.initialize(
                    this,
                    keyTypeCombo,
                    forceOddParityCheck,
                    generatedKeyField,
                    keyInputField,
                    validationResultArea,
                    numComponentsCombo,
                    keyToSplitField,
                    componentResultsArea,
                    component1Field,
                    component2Field,
                    component3Field,
                    component4Field,
                    component5Field);
            keysController.initializeKeyMaterialInspector(keyMaterialInputArea, keyMaterialReportArea);
            keysController.initializeKeyPairComparator(keyComparePublicArea, keyComparePrivateArea, keyCompareResultArea);
            keysController.initializeKeyStoreInspector(keyStoreTypeCombo, keyStorePasswordField, keyStoreUnsafeExtractCheck,
                    keyStorePathField, keyStoreReportArea, keyStoreProfileCombo, keyStoreProfileNameField);
            keysController.initializePkcs11Inspector(pkcs11NameField, pkcs11LibraryField, pkcs11SlotField,
                    pkcs11PinField, pkcs11ProfileCombo, pkcs11ReportArea);
            keysController.initializePkcs11Signing(pkcs11SigningKeyCombo, pkcs11SignatureAlgorithmCombo,
                    pkcs11DataArea, pkcs11SignatureArea);
            keysController.initializePkcs11Certificates(pkcs11CertificateAliasCombo, pkcs11CertificateArea);
            keysController.initializePkcs11Jwt(pkcs11JwtAlgorithmCombo, pkcs11JwtPayloadArea, pkcs11JwtOutputArea);
            keysController.initializePkcs11Cms(pkcs11CmsDataArea, pkcs11CmsDetachedCheck, pkcs11CmsOutputArea);

            // Initialize KDF
            keysController.initializeKDF(
                    kdfAlgorithmCombo,
                    kdfInputFormatCombo,
                    kdfSaltFormatCombo,
                    kdfInfoFormatCombo,
                    kdfInputField,
                    kdfSaltField,
                    kdfInfoField,
                    kdfIterationsField,
                    kdfOutputLengthField,
                    kdfResultArea);

            keysController.initializeKeyWrap(keyWrapModeCombo, keyWrapUnwrapCheck, keyWrapKekField,
                    keyWrapDataField, keyWrapResultArea);

            // Initialize TR-31
            keysController.initializeTR31(
                    tr31KbpkExportField,
                    tr31KeyToWrapField,
                    tr31VersionCombo,
                    tr31UsageCombo,
                    tr31AlgorithmCombo,
                    tr31ModeCombo,
                    tr31ExportabilityCombo,
                    tr31OptionalBlocksField,
                    tr31ExportResultArea,
                    tr31KbpkImportField,
                    tr31KeyBlockField,
                    tr31KeyLengthField,
                    tr31ImportResultArea);

            // Initialize Asymmetric Keys
            keysController.initializeRSA(rsaKeySizeCombo, rsaPublicKeyArea, rsaPrivateKeyArea);
            keysController.initializeDSA(dsaKeySizeCombo, dsaPublicKeyArea, dsaPrivateKeyArea);
            keysController.initializeECDSAFp(ecdsaCurveCombo, ecdsaPublicKeyArea, ecdsaPrivateKeyArea);
            keysController.initializeEd25519(eddsaPublicKeyArea, eddsaPrivateKeyArea);

            // Initialize Certificates (simplified version)
            keysController.initializeCertificateGen(
                    certCNField, certOrgField, certOUField,
                    certLocalityField, certStateField, certCountryField,
                    certEmailField, certValidityField,
                    certKeyTypeCombo, certSignAlgoCombo, certOutputArea, certSanDnsField, certSanIpField, certRootCaCheck);
            keysController.initializeCertificateChain(chainInputArea, chainResultArea);
            keysController.initializeCertificateParse(certInputArea, certParseResultArea);
            keysController.initializeCertificateComparator(certCompareLeftArea, certCompareRightArea, certCompareResultArea);
            keysController.initializeCertificateIssuer(certIssueCsrArea, certIssueCaCertArea, certIssueCaKeyArea,
                    certIssueValidityField, certIssueSignatureField, certIssueResultArea, certIssueIntermediateCaCheck,
                    certIssuePathLengthField);
            keysController.initializeValidateCertificate(valCertInput, valIssuerInput, valResultArea);

            initializeASN1();

            // Navigation listener CMS
            keysController.initializeCMS(
                    cmsInputArea,
                    cmsOutputArea,
                    cmsDetachedCheck,
                    cmsCadesBesCheck,
                    cmsCadesTCheck,
                    cmsCadesTsaUrlField,
                    cmsCadesTsaBox,
                    cmsSignCertArea,
                    cmsSignKeyArea,
                    cmsEncryptCertArea,
                    cmsDecryptKeyArea,
                    cmsSignSourcePkcs11Radio,
                    cmsSignLocalGrid,
                    cmsSignPkcs11Box,
                    cmsSignKeyAliasCombo,
                    cmsVerifyDataArea,
                    cmsEncryptSourcePkcs11Radio,
                    cmsEncryptLocalGrid,
                    cmsEncryptPkcs11Box,
                    cmsEncryptKeyAliasCombo);

            System.out
                    .println("KeysController (with TR-31 + Asymmetric + Certificates + CMS) initialized successfully!");
        } catch (Exception e) {
            System.err.println("Error initializing KeysController: " + e.getMessage());
            LOG.error("Modern UI operation failed", e);
        }
    }

    // ============================================================
    // EVENT HANDLERS - Symmetric Keys Operations
    // ============================================================

    @FXML
    private void handleGenerateKey() {
        if (isRestoring)
            return;
        keysController.handleGenerateKey();

        java.util.Map<String, String> details = new java.util.HashMap<>();
        if (keyTypeCombo != null) {
            String kType = keyTypeCombo.getValue();
            details.put("Key Type", kType);

            if (forceOddParityCheck != null) {
                details.put("Force Parity", forceOddParityCheck.isSelected() ? "Yes" : "No");
            }
            if (kType.equals("DES"))
                details.put("Size", "64 bits (56 effective)");
            else if (kType.equals("3DES (2 key)"))
                details.put("Size", "128 bits (112 effective)");
            else if (kType.equals("3DES (3 key)"))
                details.put("Size", "192 bits (168 effective)");
            else if (kType.equals("AES-128"))
                details.put("Size", "128 bits");
            else if (kType.equals("AES-192"))
                details.put("Size", "192 bits");
            else if (kType.equals("AES-256"))
                details.put("Size", "256 bits");
        }

        if (generatedKeyField != null && !generatedKeyField.getText().isEmpty()) {
            // Removed "Output" detail to avoid redundancy
        }

        byte[] output = new byte[0];
        if (generatedKeyField != null && !generatedKeyField.getText().isEmpty()) {
            String txt = generatedKeyField.getText().trim();
            // If it contains KCV info like "KEY... \n KCV: ...", split i
            if (txt.contains("\n")) {
                txt = txt.split("\n")[0].trim();
            }
            try {
                output = DataConverter.hexToBytes(txt);
            } catch (Exception e) {
                // Ignore parsing error for inspector update
            }
        }

        updateInspector("Key Generation", new byte[0], output, details);
        // addToHistory("Generate Symmetric Key", details); // Delegated to
        // KeysController

    }

    @FXML
    private void handleValidateKey() {
        if (isRestoring)
            return;
        keysController.handleValidateKey();

        java.util.Map<String, String> details = new java.util.HashMap<>();
        byte[] input = new byte[0];
        if (keyInputField != null) {
            String inputHex = keyInputField.getText().trim();
            details.put("Input Length", String.valueOf(inputHex.length()) + " hex chars");
            // Removed "Input Key" and "Validation Result" to reduce clutter as per user
            // reques

            try {
                input = DataConverter.hexToBytes(keyInputField.getText().trim());
            } catch (Exception e) {
                /* ignore */ }
        }

        updateInspector("Validation & KCV", input, null, details);
        // addToHistory("Validate Symmetric Key", details); // Delegated to
        // KeysController

    }

    @FXML
    private void handleSplitKey() {
        keysController.handleSplitKey();
        updateInspector("Key Sharing - Split");
        java.util.Map<String, String> details = new java.util.HashMap<>();
        details.put("Action", "Split Key");
        if (numComponentsCombo != null) {
            details.put("Components", numComponentsCombo.getValue());
        }
        if (keyToSplitField != null && !keyToSplitField.getText().isEmpty()) {
            details.put("Key Input",
                    keyToSplitField.getText().substring(0, Math.min(keyToSplitField.getText().length(), 16)) + "...");
        }
        addToHistory("Split Key", details);
    }

    @FXML
    private void handleCombineComponents() {
        keysController.handleCombineComponents();
        updateInspector("Key Sharing - Combine");
        java.util.Map<String, String> details = new java.util.HashMap<>();
        details.put("Action", "Combine Components");
        addToHistory("Combine Components", details);
    }

    @FXML
    private void handleDeriveKey() {
        if (isRestoring)
            return;
        keysController.handleDeriveKey();

        byte[] input = new byte[0];
        byte[] output = new byte[0];
        java.util.Map<String, String> details = new java.util.HashMap<>();

        if (kdfAlgorithmCombo != null) {
            details.put("Algorithm", kdfAlgorithmCombo.getValue());
        }
        if (kdfIterationsField != null) {
            details.put("Iterations", kdfIterationsField.getText());
        }
        if (kdfSaltField != null && !kdfSaltField.getText().isEmpty()) {
            details.put("Salt", "Present (" + kdfSaltField.getText().length() + " chars)");
        }

        // Input Bytes
        if (kdfInputField != null && !kdfInputField.getText().isEmpty()) {
            try {
                // Simplified: just assuming text/hex based on content or standard UTF-8 for
                // length approx
                String inText = kdfInputField.getText();
                String format = kdfInputFormatCombo.getValue();
                if ("Hex".equals(format) || "Hexadecimal".equals(format)) {
                    input = DataConverter.hexToBytes(inText.replaceAll("\\s+", ""));
                } else if ("Base64".equals(format)) {
                    input = java.util.Base64.getDecoder().decode(inText.replaceAll("\\s+", ""));
                } else {
                    input = inText.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                }
            } catch (Exception e) {
                // ignore
            }
        }

        // Output Bytes
        // Output Bytes
        if (kdfResultArea != null && !kdfResultArea.getText().isEmpty()) {
            String res = kdfResultArea.getText();
            String cleanOutput = res;
            if (res.contains("DERIVED KEY:")) {
                try {
                    String[] parts = res.split("DERIVED KEY:");
                    if (parts.length > 1) {
                        cleanOutput = parts[1].trim().split("\n")[0].trim();
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
            // Removed redundant "Output" detail
            // details.put("Output", cleanOutput);

            // For inspector bytes
            if (res.contains("DERIVED KEY:")) {
                try {
                    String[] parts = res.split("DERIVED KEY:");
                    if (parts.length > 1) {
                        String hexKey = parts[1].trim().split("\n")[0].trim();
                        output = DataConverter.hexToBytes(hexKey);
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
        }

        updateInspector("Key Derivation (KDF)", input, output, details);
        addToHistory("Derive Key", details);

    }

    @FXML
    private void handleInspectKeyMaterial() {
        if (keysController == null) return;

        // The legacy inspector renders into its own TextArea.  Clear tha
        // area first so a failed parse cannot leave a stale report that is
        // subsequently offered by the global Expand Result action.
        if (keyMaterialReportArea != null) {
            keyMaterialReportArea.clear();
        }
        keysController.handleInspectKeyMaterial();

        String report = keyMaterialReportArea == null ? "" : keyMaterialReportArea.getText();
        if (report == null || report.isBlank()) {
            return;
        }

        String suppliedMaterial = keyMaterialInputArea == null ? "" : keyMaterialInputArea.getText();
        boolean privateMaterial = suppliedMaterial != null && suppliedMaterial.contains("PRIVATE KEY");
        java.util.List<com.cryptoforge.model.OperationDetail> details = new java.util.ArrayList<>();
        details.add(privateMaterial
                ? com.cryptoforge.model.OperationDetail.secretDetail("Input material", "Private key metadata inspected")
                : com.cryptoforge.model.OperationDetail.publicDetail("Input material", "Public key or certificate metadata inspected"));
        details.add(com.cryptoforge.model.OperationDetail.publicDetail("Report", "Key material inspection completed", true, "text/plain"));

        // Do not publish the supplied PEM.  The report itself contains only
        // diagnostics/fingerprints, and publishing it makes the global
        // result viewer show the same text as the local inspector.
        publish(com.cryptoforge.model.OperationResult.forOperation("Key Material Inspector")
                .output(report.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .details(details)
                .status("Key material inspected successfully")
                .build());
    }

    @FXML
    private void handleCompareKeyPair() {
        if (keysController == null) return;
        keysController.handleCompareKeyPair();
        java.util.Map<String, String> details = new java.util.HashMap<>();
        details.put("Action", "Cryptographic public/private comparison");
        addToHistory("Compare Key Pair", details);
        updateInspector("Compare Key Pair");
    }

    @FXML
    private void handleInspectKeyStore() {
        if (keysController == null) return;
        keysController.handleInspectKeyStore();
        java.util.Map<String, String> details = new java.util.HashMap<>();
        details.put("Type", keyStoreTypeCombo == null ? "" : keyStoreTypeCombo.getValue());
        addToHistory("Inspect KeyStore", details);
        updateInspector("KeyStore Inspector");
    }

    @FXML
    private void handleSavePkcs11Profile() {
        if (keysController != null) {
            keysController.handleSavePkcs11Profile();
        }
    }

    @FXML
    private void handleDeletePkcs11Profile() {
        if (keysController != null) {
            keysController.handleDeletePkcs11Profile();
        }
    }

    @FXML
    private void handleChooseKeyStore() {
        if (keysController != null) keysController.chooseKeyStore();
    }

    @FXML
    private void handleSaveKeyStoreProfile() {
        if (keysController != null) keysController.saveKeyStoreProfile();
    }

    @FXML
    private void handleChoosePkcs11Library() {
        if (keysController != null) keysController.choosePkcs11Library();
    }

    @FXML
    private void handleConnectPkcs11() {
        if (keysController != null) keysController.connectPkcs11();
        if (cipherController != null) cipherController.refreshHsmKeys();
        if (authenticationController != null) authenticationController.refreshHsmKeys();
    }

    @FXML
    private void handleDisconnectPkcs11() {
        if (keysController != null) keysController.disconnectPkcs11();
        if (cipherController != null) cipherController.refreshHsmKeys();
        if (authenticationController != null) authenticationController.refreshHsmKeys();
    }

    @FXML
    private void handlePkcs11Sign() {
        if (keysController != null) keysController.signWithPkcs11();
    }

    @FXML
    private void handlePkcs11Verify() {
        if (keysController != null) keysController.verifyWithPkcs11();
    }

    @FXML
    private void handleShowPkcs11Certificate() {
        if (keysController != null) keysController.showPkcs11CertificateChain();
    }

    @FXML
    private void handleGeneratePkcs11Jwt() {
        if (keysController != null) keysController.generatePkcs11Jwt();
    }

    @FXML
    private void handleGeneratePkcs11Cms() {
        if (keysController != null) keysController.generatePkcs11Cms();
    }

    @FXML
    private void handleLoadKeyStoreProfile() {
        if (keysController != null) keysController.loadKeyStoreProfile();
    }

    @FXML
    private void handleAesKeyWrap() {
        if (isRestoring) return;
        keysController.handleKeyWrap();
        java.util.Map<String, String> details = new java.util.HashMap<>();
        details.put("Mode", keyWrapModeCombo == null ? "" : keyWrapModeCombo.getValue());
        details.put("Action", keyWrapUnwrapCheck != null && keyWrapUnwrapCheck.isSelected() ? "Unwrap" : "Wrap");
        if (keyWrapResultArea != null && !keyWrapResultArea.getText().isBlank()) {
            details.put("Result", "Available in result panel");
        }
        updateInspector("AES Key Wrap", new byte[0], new byte[0], details);
        addToHistory("AES Key Wrap", details);
    }

    @FXML
    private void handleTR31Export() {
        System.out.println("DEBUG: Executing TR-31 Export Handler in ModernMainController");
        keysController.handleTR31Export();
        updateInspector("TR-31 Export (Wrap)");
        java.util.Map<String, String> details = new java.util.HashMap<>();
        details.put("Action", "Export/Wrap");
        if (tr31VersionCombo != null)
            details.put("Version", tr31VersionCombo.getValue());
        if (tr31UsageCombo != null)
            details.put("Usage", tr31UsageCombo.getValue());
        if (tr31AlgorithmCombo != null)
            details.put("Algorithm", tr31AlgorithmCombo.getValue());
        if (tr31ModeCombo != null)
            details.put("Mode", tr31ModeCombo.getValue());
        // addToHistory("TR-31 Export", details); // Delegated to KeysController for
        // full details
    }

    @FXML
    private void handleTR31Import() {
        keysController.handleTR31Import();
        updateInspector("TR-31 Import (Unwrap)");
        java.util.Map<String, String> details = new java.util.HashMap<>();
        details.put("Action", "Import/Unwrap");
        addToHistory("TR-31 Import", details);
    }

    @FXML
    private void handleTR31ParseHeader() {
        keysController.handleTR31ParseHeader();
        updateInspector("TR-31 Parse Header");
        java.util.Map<String, String> details = new java.util.HashMap<>();
        details.put("Action", "Parse Header");
        addToHistory("TR-31 Parse", details);
    }

    @FXML
    private void handleGenerateRSA() {
        if (isRestoring)
            return;
        keysController.handleGenerateRSA();

        java.util.Map<String, String> details = new java.util.HashMap<>();
        if (rsaKeySizeCombo != null) {
            details.put("Key Size", String.valueOf(rsaKeySizeCombo.getValue()));
        }
        details.put("Start Date", "Today");
        // Removed validity as per user request (it's policy dependent)

        // Result is in public/private areas
        // Result is in public/private areas
        // Removed verbose "Public Key Output" as per user request to avoid redundancy
        // details.put("Public Key Output", rsaPublicKeyArea.getText());
        // Removed verbose key details from Inspector as per user reques

        // Calculate approximate key size in bytes for the Inspector
        int keySize = rsaKeySizeCombo.getValue();
        byte[] dummyOutput = new byte[keySize / 8];

        updateInspector("RSA Key Generation", null, dummyOutput, details);
        // addToHistory("Generate RSA Key", details); // Delegated to KeysController

    }

    @FXML
    private void handleGenerateECDSA() {
        keysController.handleGenerateECDSAFp();

        java.util.Map<String, String> details = new java.util.HashMap<>();
        if (ecdsaCurveCombo != null) {
            details.put("Curve", ecdsaCurveCombo.getValue());
        }

        // Removed verbose key details from Inspector

        // Approximate size for Inspector
        int sizeBytes = 32; // Default 256 bits
        if (ecdsaCurveCombo.getValue().contains("384"))
            sizeBytes = 48;
        else if (ecdsaCurveCombo.getValue().contains("521"))
            sizeBytes = 66;

        updateInspector("ECDSA Key Generation", null, new byte[sizeBytes], details);
        // addToHistory("Generate ECDSA Key", details); // Delegated to KeysController

    }

    @FXML
    private void handleGenerateDSA() {
        keysController.handleGenerateDSA();

        java.util.Map<String, String> details = new java.util.HashMap<>();
        if (dsaKeySizeCombo != null) {
            details.put("Key Size", dsaKeySizeCombo.getValue());
        }

        // Removed verbose key details from Inspector

        int sizeBytes = 256; // Default 2048 bits
        String sizeStr = dsaKeySizeCombo.getValue();
        if (sizeStr != null && sizeStr.contains("/")) {
            try {
                int bits = Integer.parseInt(sizeStr.split("/")[0]);
                sizeBytes = bits / 8;
            } catch (Exception e) {
            }
        }

        updateInspector("DSA Key Generation", null, new byte[sizeBytes], details);
        // addToHistory("Generate DSA Key", details); // Delegated to KeysController

    }

    @FXML
    private void handleGenerateEdDSA() {
        keysController.handleGenerateEdDSA();

        java.util.Map<String, String> details = new java.util.HashMap<>();
        details.put("Curve", "Ed25519");

        // Removed verbose key details from Inspector

        updateInspector("EdDSA Key Generation", null, new byte[32], details);
        // addToHistory("Generate EdDSA Key", details); // Delegated to KeysController

    }

    @FXML
    private void handleLoadJWTKey() {
        File file = chooseFile("Load Signing Key");
        if (file != null) {
            try {
                String content = Files.readString(file.toPath());
                jwtKeyArea.setText(content);
            } catch (Exception e) {
                showError("Load Error", "Could not read key file: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleLoadJWTValidateKey() {
        File file = chooseFile("Load Verification Key");
        if (file != null) {
            try {
                String content = Files.readString(file.toPath());
                jwtValidateKeyArea.setText(content);
            } catch (Exception e) {
                showError("Load Error", "Could not read key file: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleLoadJWEPublicKey() {
        File file = chooseFile("Load Public Key");
        if (file != null) {
            try {
                jwePublicKeyArea.setText(Files.readString(file.toPath()));
            } catch (Exception e) {
                showError("Error", "Could not load file: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleLoadJWEPrivateKey() {
        File file = chooseFile("Load Private Key");
        if (file != null) {
            try {
                jwePrivateKeyArea.setText(Files.readString(file.toPath()));
            } catch (Exception e) {
                showError("Error", "Could not load file: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleGenerateJWE() {
        if (joseController != null) {
            joseController.generateJWE(
                    jwePayloadArea.getText(),
                    jweKeyAlgoCombo.getValue(),
                    jweContentAlgoCombo.getValue(),
                    jwePublicKeyArea.getText(),
                    jweCompressCheck.isSelected(),
                    jweOutputArea);
            Map<String, String> details = new HashMap<>(
                    Map.of("alg", jweKeyAlgoCombo.getValue(), "enc", jweContentAlgoCombo.getValue()));
            details.put("Compression", jweCompressCheck.isSelected() ? "Yes" : "No");
            addToHistory("Generate JWE", details);
        }
    }

    @FXML
    private void handleDecryptJWE() {
        if (joseController != null) {
            joseController.decryptJWE(
                    jweInputArea.getText(),
                    jwePrivateKeyArea.getText(),
                    jweDecodedHeaderArea,
                    jweDecodedPayloadArea,
                    jweHeaderArea,
                    jweEncryptedKeyArea,
                    jweDecryptedKeyArea,
                    jweIVArea,
                    jweCiphertextArea,
                    jweAuthTagArea,
                    jweStatusLabel);
            addToHistory("Decrypt JWE", new HashMap<>());
        }
    }

    // Advanced JOSE Handlers
    @FXML
    private void handleApplyJWTClaims() {
        if (jwtPayloadArea != null) {
            try {
                long now = System.currentTimeMillis() / 1000L;
                long expHours = 1;
                try {
                    expHours = Long.parseLong(jwtExpField.getText());
                } catch (NumberFormatException ignored) {
                }

                String json = String.format(
                        "{\n  \"iss\": \"%s\",\n  \"sub\": \"%s\",\n  \"aud\": \"%s\",\n  \"iat\": %d,\n  \"exp\": %d\n}",
                        jwtIssField.getText(),
                        jwtSubField.getText(),
                        jwtAudField.getText(),
                        now,
                        now + (expHours * 3600));
                jwtPayloadArea.setText(json);
            } catch (Exception e) {
                showError("Claims Error", e.getMessage());
            }
        }
    }

    @FXML
    private void handleLoadNestedSigningKey() {
        File file = chooseFile("Load Signing Key");
        if (file != null) {
            try {
                nestedSigningKeyArea.setText(Files.readString(file.toPath()));
            } catch (Exception e) {
                showError("Error", e.getMessage());
            }
        }
    }

    @FXML
    private void handleLoadNestedEncryptionKey() {
        File file = chooseFile("Load Encryption Key");
        if (file != null) {
            try {
                nestedEncryptionKeyArea.setText(Files.readString(file.toPath()));
            } catch (Exception e) {
                showError("Error", e.getMessage());
            }
        }
    }

    @FXML
    private void handleGenerateNestedJWT() {
        if (joseController != null) {
            joseController.generateNestedJWT(
                    nestedPayloadArea.getText(),
                    nestedSignAlgoCombo.getValue(),
                    nestedSigningKeyArea.getText(),
                    nestedKeyAlgoCombo.getValue(),
                    nestedContentAlgoCombo.getValue(),
                    nestedEncryptionKeyArea.getText(),
                    nestedCompressCheck.isSelected(),
                    nestedOutputArea);
            Map<String, String> details = new HashMap<>();
            details.put("Sign Algo", nestedSignAlgoCombo.getValue());
            details.put("Key Algo", nestedKeyAlgoCombo.getValue());
            details.put("Content Algo", nestedContentAlgoCombo.getValue());
            details.put("Compression", nestedCompressCheck.isSelected() ? "Yes" : "No");
            addToHistory("Generate Nested JWT", details);
        }
    }

    private File chooseFile(String title) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        return fileChooser.showOpenDialog(mainPane.getScene().getWindow());
    }

    @FXML
    private void handleGenerateSignedJWT() {
        if (joseController != null) {
            String algo = jwtAlgoCombo.getSelectionModel().getSelectedItem();
            String key = jwtKeyArea.getText();

            java.util.List<com.cryptoforge.crypto.SignerConfig> signers = new java.util.ArrayList<>();
            signers.add(new com.cryptoforge.crypto.SignerConfig(algo, key));
            joseController.generateSignedJWT(
                    jwtPayloadArea.getText(),
                    signers,
                    "JWT",
                    false,
                    jwtOutputArea);

            // Add to History
            Map<String, String> details = new HashMap<>();
            details.put("Algorithm", algo);
            details.put("Key/Secret", key.length() > 50 ? "Provided (Length: " + key.length() + ")" : key);
            details.put("Payload", jwtPayloadArea.getText());
            details.put("Output JWT", jwtOutputArea.getText());
            addToHistory("Generate JWT", details);
        }
    }

    @FXML
    private void handleValidateJWT() {
        if (joseController != null) {
            String iss = jwtExpectedIssField.getText();
            String aud = jwtExpectedAudField.getText();
            String skewStr = jwtClockSkewField.getText();
            long skew = 0;
            try {
                skew = Long.parseLong(skewStr);
            } catch (Exception e) {
            }
            boolean checkExp = jwtCheckExpiryCheck.isSelected();

            joseController.validateJWTAdvanced(
                    jwtValidateTokenArea.getText(), jwtValidateKeyArea.getText(), iss, aud, skew, checkExp, false, jwtDecodedHeaderArea, jwtDecodedPayloadArea, jwtStatusLabel);

            // Add to History
            Map<String, String> details = new HashMap<>();
            details.put("Token", jwtValidateTokenArea.getText());
            details.put("Verification Key", jwtValidateKeyArea.getText().length() > 50 ? "Provided (PEM/Secret)"
                    : jwtValidateKeyArea.getText());
            details.put("Status", jwtStatusLabel.getText());
            addToHistory("Validate JWT", details);
        }
    }

    // --- Enterprise JWK Handlers ---

    @FXML
    private void handleInspectToken() {
        if (joseController != null && inspectorInputArea != null && inspectorOutputFlow != null) {
            joseController.inspectToken(inspectorInputArea.getText(), inspectorOutputFlow);
            // History
            java.util.Map<String, String> details = new java.util.HashMap<>();
            if (inspectorInputArea.getText().length() > 50) {
                details.put("Token Preview", inspectorInputArea.getText().substring(0, 20) + "...");
            }
            addToHistory("Token Inspector", details);
        }
    }

    @FXML
    public void handlePemToJwk() {
        if (joseController != null) {
            joseController.convertPemToJwk(jwkInputArea.getText(), jwkKeyTypeCombo.getValue(), jwkKeyIdField.getText(),
                    jwkOutputArea);
            // History
            java.util.Map<String, String> details = new java.util.HashMap<>();
            details.put("Key Type", jwkKeyTypeCombo.getValue());
            if (jwkKeyIdField.getText() != null && !jwkKeyIdField.getText().isEmpty()) {
                details.put("Key ID", jwkKeyIdField.getText());
            }
            addToHistory("PEM to JWK", details);
        }
    }

    @FXML
    public void handleJwkToPem() {
        if (joseController != null) {
            joseController.convertJwkToPem(jwkInputArea.getText(), jwkOutputArea);
            // History
            addToHistory("JWK to PEM", new java.util.HashMap<>());
        }
    }

    @FXML
    public void handleCalculateThumbprint() {
        showJOSE();
        if (joseController != null) {
            joseController.calculateThumbprint(jwkInputArea.getText(), jwkOutputArea);
            // History
            addToHistory("JWK Thumbprint", new java.util.HashMap<>());
        }
    }

    @FXML
    private void handleCopyInspectorOutput() {
        String report = getInspectorReportText();
        if (!report.isBlank()) {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
            cc.putString(report);
            clipboard.setContent(cc);
            updateStatus("Copied Inspector report to clipboard!");
        } else {
            showError("Copy Error", "Nothing to copy.");
        }
    }

    @FXML
    private void handleOpenExpandedInspectorReport() {
        String report = getInspectorReportText();
        if (report.isBlank()) {
            showInfo("No report available", "Analyze a token before opening the expanded viewer.");
            return;
        }
        javafx.stage.Window owner = mainPane == null || mainPane.getScene() == null
                ? null : mainPane.getScene().getWindow();
        expandedTextViewer.show(owner, "Expanded Result — Token Inspector", report);
    }

    private String getInspectorReportText() {
        if (inspectorOutputFlow == null) {
            return "";
        }
        StringBuilder report = new StringBuilder();
        for (javafx.scene.Node node : inspectorOutputFlow.getChildren()) {
            if (node instanceof javafx.scene.text.Text text) {
                report.append(text.getText());
            }
        }
        return report.toString();
    }

    @FXML
    private void handleClearInspector() {
        if (inspectorInputArea != null)
            inspectorInputArea.clear();
        if (inspectorOutputFlow != null)
            inspectorOutputFlow.getChildren().clear();
    }

    // --- Template Handler ---
    @FXML
    private void handleLoadTemplate() {
        // Triggered by ComboBox onAction? Or separate logic.
        // I didn't add onAction to FXML for generic template combo, I need to add
        // listener in init.
    }

    @FXML
    private void handleGenerateRSAJWK() {
        if (joseController != null) {
            joseController.generateRSAJWK(jwkOutputArea);
        }
    }

    @FXML
    private void handleGenerateCertificate() {
        keysController.handleGenerateCertificate();
        updateInspector("Generate Certificate");
        java.util.Map<String, String> details = new java.util.HashMap<>();
        details.put("Action", "Generate X.509 Certificate");
        if (certCNField != null)
            details.put("CN", certCNField.getText());
        if (certOrgField != null)
            details.put("Organization", certOrgField.getText());
        if (certSignAlgoCombo != null)
            details.put("Algorithm", certSignAlgoCombo.getValue());
        if (certValidityField != null)
            details.put("Validity", certValidityField.getText() + " days");

        if (certOutputArea != null && !certOutputArea.getText().isEmpty()) {
            details.put("Output", certOutputArea.getText());
        }

        addToHistory("Generate Certificate", details);
    }

    @FXML
    private void handleGenerateCSR() {
        if (keysController == null) return;
        keysController.handleGenerateCSR();
        updateInspector("Generate CSR");
        java.util.Map<String, String> details = new java.util.HashMap<>();
        details.put("Action", "PKCS#10 CSR with SAN request");
        addToHistory("Generate CSR", details);
    }

    @FXML
    private void handleCompareCertificates() {
        if (keysController == null) return;
        keysController.handleCompareCertificates();
        updateInspector("Compare Certificates");
        java.util.Map<String, String> details = new java.util.HashMap<>();
        details.put("Action", "X.509 field comparison");
        addToHistory("Compare Certificates", details);
    }

    @FXML
    private void handleIssueCertificateFromCsr() {
        if (keysController == null) return;
        keysController.handleIssueCertificateFromCsr();
        updateInspector("Issue Certificate from CSR");
        java.util.Map<String, String> details = new java.util.HashMap<>();
        details.put("Action", "Laboratory CA issuance from validated CSR");
        addToHistory("Issue Certificate from CSR", details);
    }

    @FXML
    private void handleValidateCertificateChain() {
        if (keysController != null) {
            keysController.handleValidateCertificateChain();
        }
        updateInspector("Validate Chain");
        java.util.Map<String, String> details = new java.util.HashMap<>();
        details.put("Action", "Validate Chain");
        if (chainResultArea != null && !chainResultArea.getText().isEmpty()) {
            details.put("Result", chainResultArea.getText());
        }
        addToHistory("Validate Cert Chain", details);
    }

    @FXML
    private void handleValidateCertificate() {
        keysController.handleValidateCertificate();
        java.util.Map<String, String> details = new java.util.HashMap<>();
        details.put("Action", "Validate Certificate");
        if (valResultArea != null && !valResultArea.getText().isEmpty()) {
            details.put("Validation Result", valResultArea.getText());
        }
        addToHistory("Validate Certificate", details);
    }

    @FXML
    private void handleParseCertificate() {
        keysController.handleParseCertificate();
        updateInspector("Parse Certificate");
        java.util.Map<String, String> details = new java.util.HashMap<>();
        details.put("Action", "Parse X.509 Certificate");
        if (certParseResultArea != null && !certParseResultArea.getText().isEmpty()) {
            details.put("Output", certParseResultArea.getText());
        }
        addToHistory("Parse Certificate", details);
    }

    @FXML
    private void handleCMSSign() {
        keysController.handleCMSSign();
    }

    @FXML
    private void handleUpgradeCadesLt() {
        keysController.handleUpgradeCadesLt();
    }

    @FXML
    private void handleCMSourceChanged() {
        keysController.handleCMSourceChanged();
    }

    @FXML
    private void handleCadesTimestampOptionChanged() {
        keysController.handleCadesTimestampOptionChanged();
    }

    @FXML
    private void handleCMSEncryptSourceChanged() {
        keysController.handleCMSEncryptSourceChanged();
    }

    @FXML
    private void handleLoadCMSKeys() {
        keysController.handleLoadCMSKeys();
    }

    @FXML
    private void handleLoadCMSEncryptKeys() {
        keysController.handleLoadCMSEncryptKeys();
    }

    @FXML
    private void handleCMSVerify() {
        keysController.handleCMSVerify();
    }

    @FXML
    private void handleCMSEncrypt() {
        keysController.handleCMSEncrypt();
    }

    @FXML
    private void handleCMSDecrypt() {
        keysController.handleCMSDecrypt();
    }

    // ============================================================================
    // KEY HANDLERS (Delegates)
    // ============================================================================

    // ============================================================================
    // ASN.1 DECODER
    // ============================================================================

    private void initializeASN1() {
        if (asn1InputFormatCombo != null) {
            asn1InputFormatCombo.getItems().clear();
            asn1InputFormatCombo.getItems().addAll(
                    "Hexadecimal",
                    "Base64",
                    "Base64 (PEM)");
            asn1InputFormatCombo.setValue("Hexadecimal");
        }

        if (asn1TypeCombo != null) {
            asn1TypeCombo.getItems().clear();
            asn1TypeCombo.getItems().addAll(
                    "Auto-detect",
                    "X.509 Certificate",
                    "RSA Private Key",
                    "RSA Public Key",
                    "EC Private Key",
                    "Certificate Signing Request",
                    "Simple SEQUENCE");
            asn1TypeCombo.setValue("Auto-detect");
        }
    }

    @FXML
    private void handleParseASN1() {
        try {
            // Get input data
            String inputText = asn1InputArea.getText().trim();
            if (inputText.isEmpty()) {
                showError("Input Error", "Please enter ASN.1 data in the input area");
                return;
            }

            // Parse based on forma
            byte[] data = parseASN1InputData(inputText);

            if (data == null || data.length == 0) {
                showError("Parse Error", "Invalid input format");
                return;
            }

            // Store data
            asn1LastParsedData = data;

            // Parse ASN.1 (with truncation for display)
            ASN1TreeNode tree = ASN1Parser.parse(data);
            com.cryptoforge.asn1.DerValidator.Report derReport = com.cryptoforge.asn1.DerValidator.validate(data);

            // Display tree
            String treeString = tree.toIndentedString();
            asn1TreeArea.setText(treeString);

            // Detect type
            String detectedType = ASN1Parser.detectType(data);

            // Display details
            StringBuilder details = new StringBuilder();
            details.append("Detected Type: ").append(detectedType).append("\n");
            details.append("Total Size: ").append(data.length).append(" bytes\n");
            details.append("Root Element: ").append(tree.getLabel()).append("\n");
            details.append("DER Validation: ").append(derReport.validDer() ? "PASS" : "NOT CANONICAL / INVALID")
                    .append(" — ").append(derReport.message()).append("\n");
            details.append("Structure parsed successfully.");

            asn1DetailsArea.setText(details.toString());

            // Update status
            asn1StatusLabel.setText("✓ Parsed successfully - " + detectedType);
            asn1StatusLabel.setStyle("-fx-text-fill: #27ae60;");

            // Inspector update
            updateInspector("ASN.1 Parse", data, null);

            // History
            java.util.Map<String, String> histDetails = new java.util.HashMap<>();
            histDetails.put("Detected Type", detectedType);
            histDetails.put("Size", data.length + " bytes");
            histDetails.put("Root", tree.getLabel());
            addToHistory("ASN.1 Parse", histDetails);

        } catch (Exception e) {
            showError("Parse Error", "Failed to parse ASN.1 data: " + e.getMessage());
            asn1StatusLabel.setText("✗ Parse failed: " + e.getMessage());
            asn1StatusLabel.setStyle("-fx-text-fill: #e74c3c;");
            LOG.error("Modern UI operation failed", e);
        }
    }

    @FXML
    private void handleLoadASN1Example() {
        String type = asn1TypeCombo.getValue();
        if (type == null || type.equals("Auto-detect"))
            return;

        String example = "";

        switch (type) {
            case "X.509 Certificate":
                // simple self-signed
                example = "308201A830820111A00302010202090085B0BCA76BC08DA5300D06092A864886F70D01010B05003011310F300D060355040313064D7943657274301E170D32343031303130303030305A170D32353031303130303030305A3011310F300D060355040313064D7943657274305C300D06092A864886F70D0101010500034B003048024100C55E4A13D0F4A0B0C0D0E0F00112233445566778899AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899AABBCCDDEEFF0203010001A3533051301D0603551D0E0416041412345678901234567890123456789012301F0603551D230418301680141234567890123456789012345678901230300F0603551D130101FF040530030101FF300D06092A864886F70D01010B0500034100";
                break;
            case "RSA Public Key":
                example = "30819F300D06092A864886F70D010101050003818D0030818902818100C55E4A13D0F4A0B0C0D0E0F00112233445566778899AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899AABBCCDDEEFF0203010001";
                break;
            case "Simple SEQUENCE":
                example = "300C02012A0C0548656C6C6F";
                break;
            default:
                // Try to find reasonable defaul
                example = "02012A";
                break;
        }

        asn1InputFormatCombo.setValue("Hexadecimal");
        asn1InputArea.setText(example);
        updateStatus("Loaded example: " + type);
        // Auto-parse
        handleParseASN1();
    }

    @FXML
    private void handleEncodeASN1() {
        try {
            String type = asn1EncodeTypeCombo.getValue();
            String inputFormat = asn1EncodeInputFormatCombo.getValue();
            String inputText = asn1EncodeInputArea.getText();

            if (type == null)
                return;

            byte[] encoded = null;

            // For NULL, input is ignored
            if (type.equals("NULL")) {
                encoded = com.cryptoforge.asn1.ASN1Encoder.encodeNull();
            } else {
                if (inputText == null || inputText.isEmpty()) {
                    showError("Error", "Input cannot be empty for " + type);
                    return;
                }

                if (type.equals("INTEGER")) {
                    int radix = 10;
                    if (inputFormat.equals("Hex"))
                        radix = 16;
                    String numberStr = inputText.trim();
                    encoded = com.cryptoforge.asn1.ASN1Encoder.encodeInteger(numberStr, radix);

                } else if (type.equals("UTF8String")) {
                    String text = inputText;
                    if (inputFormat.equals("Hex"))
                        text = new String(hexToBytes(inputText), java.nio.charset.StandardCharsets.UTF_8);
                    else if (inputFormat.equals("Base64"))
                        text = new String(java.util.Base64.getDecoder().decode(inputText),
                                java.nio.charset.StandardCharsets.UTF_8);
                    encoded = com.cryptoforge.asn1.ASN1Encoder.encodeUTF8String(text);

                } else if (type.equals("PrintableString")) {
                    String text = inputText;
                    if (inputFormat.equals("Hex"))
                        text = new String(hexToBytes(inputText), java.nio.charset.StandardCharsets.US_ASCII);
                    encoded = com.cryptoforge.asn1.ASN1Encoder.encodePrintableString(text);

                } else if (type.equals("IA5String")) {
                    String text = inputText;
                    if (inputFormat.equals("Hex"))
                        text = new String(hexToBytes(inputText), java.nio.charset.StandardCharsets.US_ASCII);
                    encoded = com.cryptoforge.asn1.ASN1Encoder.encodeIA5String(text);

                } else if (type.equals("OBJECT IDENTIFIER (OID)")) {
                    encoded = com.cryptoforge.asn1.ASN1Encoder.encodeOID(inputText.trim());

                } else if (type.equals("BOOLEAN")) {
                    boolean val = Boolean.parseBoolean(inputText) || "1".equals(inputText)
                            || "yes".equalsIgnoreCase(inputText) || "true".equalsIgnoreCase(inputText);
                    encoded = com.cryptoforge.asn1.ASN1Encoder.encodeBoolean(val);

                } else if (type.equals("OCTET STRING") || type.equals("BIT STRING") || type.contains("SEQUENCE")
                        || type.contains("SET")) {
                    byte[] data = null;
                    if (inputFormat.equals("Hex"))
                        data = hexToBytes(inputText);
                    else if (inputFormat.equals("Base64"))
                        data = java.util.Base64.getDecoder().decode(inputText);
                    else
                        data = inputText.getBytes(java.nio.charset.StandardCharsets.UTF_8);

                    if (type.equals("OCTET STRING")) {
                        encoded = com.cryptoforge.asn1.ASN1Encoder.encodeOctetString(data);
                    } else if (type.equals("BIT STRING")) {
                        encoded = com.cryptoforge.asn1.ASN1Encoder.encodeBitString(data);
                    } else if (type.contains("SEQUENCE")) {
                        encoded = com.cryptoforge.asn1.ASN1Encoder.encodeSequence(data);
                    } else if (type.contains("SET")) {
                        encoded = com.cryptoforge.asn1.ASN1Encoder.encodeSet(data);
                    }
                }
            }

            if (encoded != null) {
                asn1EncodeOutputArea.setText(bytesToHex(encoded));

                publish(com.cryptoforge.model.OperationResult.forOperation("ASN.1 Encode")
                    .input(inputText.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .output(encoded)
                    .detail("Type", type)
                    .detail("Input Format", inputFormat)
                    .status("ASN.1 encoded successfully")
                    .build());
            }

        } catch (Exception e) {
            showError("ASN.1 Encode Error", e.getMessage());
            LOG.error("Modern UI operation failed", e);
        }
    }

    @FXML
    private void handleCopyASN1Output() {
        String content = asn1EncodeOutputArea.getText();
        if (content != null && !content.isEmpty()) {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
            cc.putString(content);
            clipboard.setContent(cc);
            updateStatus("Copied ASN.1 output to clipboard");
        }
    }

    @FXML
    private void handleClearASN1() {
        asn1InputArea.clear();
        asn1TreeArea.clear();
        asn1DetailsArea.clear();
        asn1StatusLabel.setText("Ready");
        asn1StatusLabel.setStyle("");
        asn1LastParsedData = null;
    }

    @FXML
    private void handleExportASN1Tree() {
        if (asn1LastParsedData == null || asn1LastParsedData.length == 0) {
            showError("Export Error", "No parsed ASN.1 data available to export.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save ASN.1 Tree");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        fileChooser.setInitialFileName("asn1_tree.txt");

        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try (PrintWriter writer = new PrintWriter(file)) {
                // Re-parse with no truncation limit (-1)
                ASN1TreeNode fullTree = ASN1Parser.parse(asn1LastParsedData, -1);
                String fullTreeString = fullTree.toIndentedString(true);

                writer.println("ASN.1 Structure Tree Export");
                writer.println("===========================");
                writer.println("Date: " + java.time.LocalDateTime.now());
                writer.println("\nInput Size: " + asn1LastParsedData.length + " bytes");
                writer.println("Details:\n" + asn1DetailsArea.getText());
                writer.println("\nStructure (Full View):");
                writer.println("---------------------------");
                writer.println(fullTreeString);

                updateStatus("Full tree exported to " + file.getName());
            } catch (Exception e) {
                showError("Export Failed", "Could not save file: " + e.getMessage());
                LOG.error("Modern UI operation failed", e);
            }
        }
    }

    @FXML
    private void handleExportASN1Json() { exportASN1Structured("ASN.1 JSON", "asn1_tree.json", "JSON Files", "*.json", true); }

    @FXML
    private void handleExportASN1Markdown() { exportASN1Structured("ASN.1 Markdown", "asn1_tree.md", "Markdown Files", "*.md", false); }

    @FXML
    private void handleLoadASN1OidRegistry() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Load ASN.1 OID Registry");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
        File file = chooser.showOpenDialog(null);
        if (file == null) return;
        try {
            int loaded = ASN1Parser.loadCustomOidNames(file.toPath());
            updateStatus("Loaded " + loaded + " custom ASN.1 OID name(s)");
            if (asn1DetailsArea != null) asn1DetailsArea.appendText("\nCustom OID registry loaded: " + loaded + " definitions. Re-parse to apply names.\n");
        } catch (Exception e) { showError("OID Registry", "Could not load OID registry: " + e.getMessage()); }
    }

    @FXML
    private void handleCanonicalizeASN1Der() {
        try {
            byte[] input = parseASN1InputData(asn1InputArea.getText().trim());
            byte[] der = com.cryptoforge.asn1.DerValidator.canonicalize(input);
            asn1DetailsArea.appendText("\nCanonical DER: " + der.length + " bytes (input: " + input.length + " bytes)\nDER hex: "
                    + DataConverter.bytesToHex(der) + "\n");
            updateStatus("ASN.1 canonical DER generated (input unchanged)");
        } catch (Exception e) { showError("Canonical DER", "Unable to canonicalize ASN.1: " + e.getMessage()); }
    }

    private void exportASN1Structured(String title, String name, String filterName, String extension, boolean json) {
        if (asn1LastParsedData == null || asn1LastParsedData.length == 0) { showError("Export Error", "Parse ASN.1 data before exporting."); return; }
        FileChooser chooser = new FileChooser(); chooser.setTitle(title); chooser.setInitialFileName(name);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(filterName, extension));
        File file = chooser.showSaveDialog(null); if (file == null) return;
        try {
            ASN1TreeNode tree = ASN1Parser.parse(asn1LastParsedData, -1);
            String content = json ? com.cryptoforge.asn1.ASN1TreeExporter.toJson(tree) : com.cryptoforge.asn1.ASN1TreeExporter.toMarkdown(tree);
            java.nio.file.Files.writeString(file.toPath(), content, java.nio.charset.StandardCharsets.UTF_8);
            updateStatus("ASN.1 export saved: " + file.getName());
        } catch (Exception e) { showError("Export Failed", "Could not export ASN.1 tree: " + e.getMessage()); }
    }

    private byte[] parseASN1InputData(String input) throws Exception {
        String format = asn1InputFormatCombo.getValue();

        if (format.equals("Hexadecimal")) {
            // Remove spaces, newlines, etc.
            String hex = input.replaceAll("\\s+", "");
            try {
                return DataConverter.hexToBytes(hex);
            } catch (Exception e) {
                // Fallback implemented manually if DataConverter fails
                int len = hex.length();
                byte[] data = new byte[len / 2];
                for (int i = 0; i < len; i += 2) {
                    data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                            + Character.digit(hex.charAt(i + 1), 16));
                }
                return data;
            }

        } else if (format.equals("Base64")) {
            // Remove whitespace
            String base64 = input.replaceAll("\\s+", "");
            return Base64.getDecoder().decode(base64);

        } else if (format.equals("Base64 (PEM)")) {
            // Extract base64 from PEM forma
            String[] lines = input.split("\n");
            StringBuilder base64 = new StringBuilder();
            boolean inData = false;
            boolean foundHeader = false;

            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("-----BEGIN")) {
                    inData = true;
                    foundHeader = true;
                    continue;
                }
                if (trimmed.startsWith("-----END")) {
                    break;
                }
                if (inData) {
                    base64.append(trimmed);
                }
            }

            if (!foundHeader) {
                // Try parsing as raw base64 if no header found, or error?
                // Let's assume user pasted body only
                return Base64.getDecoder().decode(input.replaceAll("\\s+", ""));
            }

            return Base64.getDecoder().decode(base64.toString());
        }

        return null;
    }
    // ============================================================
    // NAVIGATION HANDLERS
    // ============================================================

    @Override
    public void navigateTo(String operation) {
        handleItemSelected(operation);
    }

    private void handleItemSelected(String itemName) {
        String requestedItem = itemName;
        itemName = com.cryptoforge.model.OperationRegistry.getInstance()
                .resolveNavigation(itemName)
                .map(com.cryptoforge.model.OperationDescriptor::getNavigationPath)
                .orElse(itemName);
        this.currentActiveOperation = itemName;
        // Navigation alone is not a result. Clear the previous published
        // snapshot so Expand Result cannot accidentally expose data from the
        // route that the user has just left.
        clearPublishedResultSnapshot();
        System.out.println("Item selected: " + itemName
                + (java.util.Objects.equals(requestedItem, itemName) ? "" : " (resolved from: " + requestedItem + ")"));

        // Handle dynamic names (e.g. Hashing: SHA-256)
        if (itemName.startsWith("Hashing: ")) {
            itemName = "Hashing";
        }

        // Update header
        updateContentHeader(itemName);

        // Update inspector
        updateInspector(itemName);

        // Show appropriate conten
        switch (itemName) {
            case "JWT (Signed)":
            case "JWE (Encrypted)":
            case "JWK (Keys)":
            case "JWA (Algorithms)":
            case "Generate JWE":
            case "Decrypt JWE":
            case "Generate JWT":
            case "Validate JWT":
            case "Generate Nested JWT":
            case "Token Inspector":
            case "PEM to JWK":
            case "JWK to PEM":
            case "JWK Thumbprint":
            case "JWKS Rotate Key":
            case "Load JWKS File":
            case "Import Key (PEM)":
            case "Import Key (JSON)":
                showJOSE();
                updateContentHeader(itemName);
                break;
            case "Epoch Converter":
                handleEpochConverter();
                break;
            case "JSON Formatter":
                handleJsonFormatter();
                break;
            // Symmetric Key Operations
            case "Key Generation":
            case "Generate Symmetric Key":
                showSymmetricKeys();
                expandAccordionPane("Key Generation");
                break;
            case "Validation & KCV":
            case "Validate Symmetric Key":
                showSymmetricKeys();
                expandAccordionPane("Validation & KCV");
                break;
            case "Key Material Inspector":
                showSymmetricKeys();
                expandAccordionPane("Key Material Inspector");
                break;
            case "Key & Certificate Format Workbench":
                showSymmetricKeys();
                expandAccordionPane("Key & Certificate Format Workbench");
                break;
            case "KeyStore Inspector":
                showSymmetricKeys();
                expandAccordionPane("KeyStore Inspector");
                break;
            case "PKCS#11 Token":
                showSymmetricKeys();
                expandAccordionPane("PKCS#11 Token");
                break;
            case "Compare Public / Private Key":
                showSymmetricKeys();
                expandAccordionPane("Compare Public / Private Key");
                break;
            case "Key Sharing (XOR Split/Combine)":
            case "Split Key":
            case "Combine Components":
                showSymmetricKeys();
                expandAccordionPane("Key Sharing");
                break;
            case "Key Derivation (KDF)":
            case "Derive Key":
                showSymmetricKeys();
                expandAccordionPane("Key Derivation");
                break;
            case "AES Key Wrap":
                showSymmetricKeys();
                expandAccordionPane("AES Key Wrap");
                break;
            case "TR-31 Key Blocks":
            case "TR-31 Export":
            case "TR-31 Import":
            case "TR-31 Parse":
                showSymmetricKeys();
                expandAccordionPane("TR-31 Key Blocks");
                break;

            // Asymmetric Key Operations
            case "RSA Key Generation":
            case "Generate RSA Key":
                showAsymmetricKeys();
                expandAsymmetricAccordionPane("RSA Key Generation");
                break;
            case "ECDSA Key Generation":
            case "Generate ECDSA Key":
                showAsymmetricKeys();
                expandAsymmetricAccordionPane("ECDSA Key Generation");
                break;
            case "DSA Key Generation":
            case "Generate DSA Key":
                showAsymmetricKeys();
                expandAsymmetricAccordionPane("DSA Key Generation");
                break;
            case "EdDSA Key Generation":
            case "Generate EdDSA Key":
                showAsymmetricKeys();
                expandAsymmetricAccordionPane("EdDSA Key Generation");
                break;

            // Certificate Operations
            case "Generate Certificate":
                showCertificates();
                expandCertificatesAccordionPane("Generate Certificate");
                break;
            case "Issue Certificate from CSR":
                showCertificates();
                expandCertificatesAccordionPane("Issue Certificate from CSR");
                break;
            case "Compare Certificates":
                showCertificates();
                expandCertificatesAccordionPane("Compare Certificates");
                break;
            case "Parse Certificate":
                showCertificates();
                expandCertificatesAccordionPane("Parse Certificate");
                break;
            case "Validate Certificate":
                showCertificates();
                expandCertificatesAccordionPane("Validate Certificate");
                break;
            case "Validate Chain":
            case "Validate Cert Chain":
            case "Certificate Chain":
                showCertificates();
                expandCertificatesAccordionPane("Validate Chain");
                break;
            case "PAdES PDF Signatures":
            case "PAdES":
            case "PDF Signatures":
                showCertificates();
                expandCertificatesAccordionPane("PAdES PDF Signatures");
                break;
            case "ASiC-S Containers":
            case "ASiC-S":
            case "ASiC":
                showCertificates();
                expandCertificatesAccordionPane("ASiC-S Containers");
                break;

            // CMS Operations
            case "CMS Sign/Verify":
            case "CMS Encrypt/Decrypt":
            case "CMS/PKCS#7 Operations":
            case "CMS Sign":
            case "CMS Verify":
            case "CMS Encrypt":
            case "CMS Decrypt":
                showCertificates();
                expandCertificatesAccordionPane("CMS Operations");
                break;
            case "CMS Inspector":
                showCertificates();
                expandCertificatesAccordionPane("CMS Inspector");
                break;

            // ASN.1 Operations
            case "ASN.1 Decoder":
            case "Decode ASN.1":
            case "ASN.1 Parse":
                showCertificates();
                expandCertificatesAccordionPane("ASN.1 Decoder");
                if (asn1TabPane != null)
                    asn1TabPane.getSelectionModel().select(0); // Select Decode tab
                break;
            case "Encode ASN.1":
            case "ASN.1 Encode":
                showCertificates();
                expandCertificatesAccordionPane("ASN.1 Decoder");
                if (asn1TabPane != null)
                    asn1TabPane.getSelectionModel().select(1); // Select Encode tab
                break;

            // Generic Operations
            case "Hashing":
                showGeneric();
                expandGenericAccordionPane("Hashing");
                break;
            case "Encoding/Conversion":
                showGeneric();
                expandGenericAccordionPane("File Conversion");
                break;
            case "File Conversion":
                showGeneric();
                expandGenericAccordionPane("File Conversion");
                break;
            case "Manual Conversion":
            case "Decode EBCDIC":
            case "Encode EBCDIC":
                showGeneric();
                expandGenericAccordionPane("Manual Conversion");
                break;
            case "Compressed Hex (2-row)":
                showGeneric();
                expandGenericAccordionPane("Compressed Hex");
                break;
            case "Batch Runner":
                showGeneric();
                expandGenericAccordionPane("Batch Runner");
                break;
            case "Random Number Generator":
            case "Random Generation":
                showGeneric();
                expandGenericAccordionPane("Random Generator");
                break;
            case "Generate UUID":
                showGeneric();
                expandGenericAccordionPane("UUID Generator");
                break;
            case "Check Digits":
                showGeneric();
                expandGenericAccordionPane("Check Digits");
                break;
            case "Check Digits: Validate":
                showGeneric();
                expandGenericAccordionPane("Check Digits");
                break;
            case "Modular Arithmetic":
                showGeneric();
                expandGenericAccordionPane("Modular Arithmetic");
                break;

            // Post-Quantum
            case "Post-Quantum Cryptography":
            case "PQC Key Generation":
            case "PQC Key Import":
            case "PQC Key Export":
            case "Kyber (ML-KEM)":
            case "Dilithium (ML-DSA)":
                showPostQuantum();
                expandPQCAccordionPane("Key Generation");
                break;
            case "PQC Sign/Verify":
            case "PQC Sign":
            case "PQC Verify":
                showPostQuantum();
                expandPQCAccordionPane("Sign");
                break;
            case "ML-KEM Encapsulate":
            case "ML-KEM Decapsulate":
                showPostQuantum();
                expandPQCAccordionPane("ML-KEM");
                break;

            // XML Security
            case "XML Security":
            case "Sign XML (XAdES)":
            case "XAdES Sign":
            case "Save XAdES XML":
                showXMLSecurity();
                expandXMLAccordionPane("Sign XML");
                break;
            case "Verify XML (XAdES)":
            case "XAdES Verify":
                showXMLSecurity();
                expandXMLAccordionPane("Verify XML");
                break;
            case "Inspect Signed XML":
                showXMLSecurity();
                expandXMLAccordionPane("Inspect Signed XML");
                break;

            case "RFC 3161 Timestamp":
                showXMLSecurity();
                expandXMLAccordionPane("RFC 3161 Timestamp");
                break;

            case "EMV Operations":
            case "Session Key Derivation":
                showEMV();
                expandEMVAccordionPane("Session Key");
                break;
            case "ARQC Generation":
                showEMV();
                expandEMVAccordionPane("ARQC");
                break;
            case "ARPC Generation":
                showEMV();
                expandEMVAccordionPane("ARPC");
                break;
            case "Track 2 Encoding":
            case "Track 2 Decoding":
            case "Track 2 Operations":
                showEMV();
                expandEMVAccordionPane("Track 2");
                break;

            case "Recent Operations":
                showHistoryView();
                break;
            case "Clipboard Shelf":
                showClipboardShelf();
                break;
            case "Saved Sessions":
                showSavedSessions();
                break;

            // Cipher Operations
            case "Symmetric Ciphers":
            case "AES Encryption": // Keep legacy for safety/search
            case "DES/3DES Encryption": // Keep legacy
            case "Symmetric Encryption":
            case "Modes & Padding":
            case "Symmetric Encrypt":
            case "Symmetric Decrypt":
                showCipher();
                expandCipherAccordionPane("Symmetric Cipher");
                break;
            case "File Cipher (Streaming)":
                showCipher();
                expandCipherAccordionPane("File Cipher");
                break;
            case "OpenPGP (GPG Compatible)":
            case "OpenPGP":
            case "GPG":
                showCipher();
                expandCipherAccordionPane("OpenPGP");
                break;
            case "Asymmetric Ciphers":
            case "RSA Encryption": // Keep legacy
            case "ECC Encryption": // Keep legacy
            case "Asymmetric Encrypt":
            case "Asymmetric Decrypt":
                showCipher();
                expandCipherAccordionPane("Asymmetric Cipher");
                break;

            case "Export History":
                // Export is an action of the modern history module. Keeping a
                // second dynamic export screen made navigation diverge from
                // Recent Operations and duplicated visibility policy handling.
                showHistoryView();
                if (historyViewController != null) {
                    historyViewController.focusExportActions();
                }
                updateStatus("Choose Export Visible JSON or Export JSON Record in Recent Operations.");
                break;

            // Authentication Operations
            case "Digital Signatures":
            case "Sign":
            case "Verify Signature":
                showAuthentication();
                expandAuthenticationAccordionPane("Digital Signatures");
                break;
            case "Message Authentication Codes":
            case "MAC":
            case "Generate MAC":
            case "Verify MAC":
            case "MAC Generated":
            case "MAC Verified":
                showAuthentication();
                expandAuthenticationAccordionPane("MAC");
                break;

            // Payments Operations
            case "Clear PIN Blocks":
            case "Encode PIN Block":
            case "Decode PIN Block":
                showPayments();
                expandPaymentsAccordionPane("Clear PIN Blocks");
                break;
            case "Encrypted PIN Blocks":
            case "ISO PIN Blocks":
            case "ISO 0":
            case "ISO 2":
                showPayments();
                expandPaymentsAccordionPane("Encrypted PIN Blocks");
                break;
            case "PIN Generation":
            case "PIN Verification":
            case "IBM 3624":
            case "Generate PIN":
            case "Verify PIN":
                showPayments();
                expandPaymentsAccordionPane("PIN Generation");
                break;
            case "DUKPT TDES":
            case "DUKPT TDES / AES":
                showPayments();
                expandPaymentsAccordionPane("DUKPT KSN");
                break;
            case "EMV TLV Inspector":
                showEMV();
                expandEMVAccordionPane("EMV TLV Inspector");
                break;
            case "CVV Operations":
            case "CVV Generation":
            case "PIN Block Operations":
            case "PIN Block Encoding":
            case "PIN Block Decoding":
            case "PIN Block Encoded":
            case "PIN Block Decoded":
                showPayments();
                expandPaymentsAccordionPane("PIN Block Operations");
                break;
            case "Encrypted PIN Block Operations":
            case "Encrypted PIN Block Encoding":
            case "Encrypted PIN Block Decoding":
            case "Encrypted PIN Block Encoded":
            case "Encrypted PIN Block Decoded":
                showPayments();
                expandPaymentsAccordionPane("Encrypted PIN Block");
                break;

            default:
                showPlaceholderContent(itemName);
                break;
        }

        updateStatus("Loaded: " + itemName);
    }

    private void updateContentHeader(String itemName) {
        // Determine section and subsection
        String section = "Cryptographic Operations";
        String subsection = itemName;
        java.util.Optional<com.cryptoforge.model.OperationDescriptor> descriptor =
                com.cryptoforge.model.OperationRegistry.getInstance().resolveNavigation(itemName);
        if (descriptor.isPresent()) {
            com.cryptoforge.model.OperationDescriptor operation = descriptor.get();
            subsection = operation.getTitle() + " · " + operationStatusSummary(operation);
        }

        if (itemName.contains("Post-Quantum") || itemName.contains("PQC")
                || itemName.contains("ML-KEM") || itemName.contains("ML-DSA")
                || itemName.contains("SLH-DSA") || itemName.contains("Kyber")
                || itemName.contains("Dilithium") || itemName.contains("SPHINCS")) {
            section = "Post-Quantum";
        } else if (itemName.contains("XML") || itemName.contains("XAdES")) {
            section = "XML Security";
        } else if (itemName.contains("RSA") || itemName.contains("ECDSA") || itemName.contains("DSA")) {
            section = "Asymmetric Keys";
        } else if (itemName.contains("Key")) {
            section = "Symmetric Keys";
        } else if (itemName.contains("Certificate") || itemName.contains("CMS")) {
            section = "Certificates";
        } else if (itemName.contains("EMV") || itemName.contains("TR-31")) {
            section = "Payments";
        }

        contentTitleLabel.setText(section);
        contentSubtitleLabel.setText(subsection);
    }

    private String operationStatusSummary(com.cryptoforge.model.OperationDescriptor operation) {
        String status = operation.getStatus() == com.cryptoforge.model.OperationDescriptor.Status.EXPERIMENTAL
                ? "Experimental" : "Stable";
        return switch (operation.getSecretRisk()) {
            case NONE -> status;
            case LOW -> status + " · Low sensitivity";
            case HIGH -> status + " · Sensitive material";
            case EXTREME -> status + " · Highly sensitive material";
        };
    }

    private void updateContentSubtitle(String subtitle) {
        if (contentSubtitleLabel != null) {
            contentSubtitleLabel.setText(subtitle);
            boolean hasText = subtitle != null && !subtitle.isEmpty();
            contentSubtitleLabel.setVisible(hasText);
            contentSubtitleLabel.setManaged(hasText);
        }
    }

    // deleted duplicate cmsKeyArea and syntax error

    private void updateInspector(String operation) {
        updateInspector(operation, null, null, (java.util.List<com.cryptoforge.model.OperationDetail>) null);
    }

    private void updateInspector(String operation, byte[] input, byte[] output) {
        updateInspector(operation, input, output, (java.util.List<com.cryptoforge.model.OperationDetail>) null);
    }

    public void updateInspector(String operation, byte[] input, byte[] output, java.util.Map<String, String> details) {
        java.util.List<com.cryptoforge.model.OperationDetail> list = new java.util.ArrayList<>();
        if (details != null) {
            details.forEach((k, v) -> list.add(com.cryptoforge.model.OperationDetail.publicDetail(k, v)));
        }
        updateInspector(operation, input, output, list);
    }

    @Override
    public void updateInspector(String operation, byte[] input, byte[] output, java.util.List<com.cryptoforge.model.OperationDetail> details) {
        operationLabel.setText(operation);

        // Update byte counts if provided
        // Update byte counts if provided, otherwise rese
        if (input != null) {
            inputBytesLabel.setText(String.valueOf(input.length));
        } else {
            inputBytesLabel.setText("-");
        }

        if (output != null) {
            outputBytesLabel.setText(String.valueOf(output.length));
        } else if (input != null && !operation.contains("Key Generated")) {
            // Fallback: For simple transforms, output size roughly equals inpu
            // But valid only if not generating keys (where input is null or irrelevan
            // params)
            outputBytesLabel.setText(String.valueOf(input.length));
        } else {
            outputBytesLabel.setText("-");
        }

        // Special case: Hide byte counts for purely informational ops or if requested
        // Special case: Hide byte counts for purely informational ops or if requested
        if (operation.contains("Key Generation") || operation.contains("Key Sharing")) {
            // For KeyGen, showing "32" or "256" bytes output is technically correct bu
            // user finds
            // it confusing ("256" vs "2048" bits).
            // Better to hide byte count and rely on the explicit "Key Size" detail.
            inputBytesLabel.setText("-");
            outputBytesLabel.setText("-");
        }

        // Update Dynamic Details
        if (inspectorDetailsContainer != null) {
            inspectorDetailsContainer.getChildren().clear();
            if (details != null && !details.isEmpty()) {
                for (com.cryptoforge.model.OperationDetail detail : details) {
                    HBox row = new HBox(10);
                    Label key = new Label(detail.name() + ":");
                    key.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 10px;");
                    Label value = new Label(detail.value());
                    value.setStyle("-fx-text-fill: #2c3e50; -fx-font-size: 10px;");
                    row.getChildren().addAll(key, value);
                    inspectorDetailsContainer.getChildren().add(row);
                }
            }
        }

        // Update security tip based on operation
        String tip = getSecurityTip(operation);
        securityTipLabel.setText(tip);

        // Ensure history container is visible (it might be hidden by Saved Sessions
        // view)
        if (historyContainer != null && !historyContainer.isVisible()) {
            historyContainer.setManaged(true);
            historyContainer.setVisible(true);
        }
    }

    private String getSecurityTip(String operation) {
        if (operation.contains("ASN.1")) {
            return "🔍 ASN.1 is the standard for representing data in cryptography. BER/DER are its encoding rules.";
        } else if (operation.contains("KDF") || operation.contains("Derivation")) {
            return "⚠️ Always use a salt with KDF operations for better security. HKDF provides better resistance against rainbow table attacks.";
        } else if (operation.contains("Key Generation")) {
            return "🔐 Generated keys are cryptographically secure using Java's SecureRandom. Never reuse keys across different systems.";
        } else if (operation.contains("AES")) {
            return "✅ AES-256 with GCM mode is recommended for maximum security. Avoid ECB mode in production.";
        } else if (operation.contains("RSA")) {
            return "📏 Use RSA keys of at least 2048 bits. 4096 bits recommended for long-term security.";
        } else {
            return "💡 Always validate inputs and use appropriate key sizes for your security requirements.";
        }
    }

    // State management for history Rerun
    private java.util.Map<String, Object> captureUIState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        try {
            // Capture all @FXML fields that are UI controls
            for (java.lang.reflect.Field field : this.getClass().getDeclaredFields()) {
                if (field.isAnnotationPresent(FXML.class)) {
                    field.setAccessible(true);
                    Object value = field.get(this);
                    if (value == null)
                        continue;

                    if (value instanceof TextInputControl) {
                        state.put(field.getName(), ((TextInputControl) value).getText());
                    } else if (value instanceof ComboBox) {
                        state.put(field.getName(), ((ComboBox<?>) value).getValue());
                    } else if (value instanceof CheckBox) {
                        state.put(field.getName(), ((CheckBox) value).isSelected());
                    } else if (value instanceof RadioButton) {
                        state.put(field.getName(), ((RadioButton) value).isSelected());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error capturing UI state: " + e.getMessage());
        }
        return state;
    }

    private boolean isRestoring = false;

    private void restoreUIState(java.util.Map<String, Object> state) {
        if (state == null || state.isEmpty())
            return;

        isRestoring = true;
        try {
            for (java.util.Map.Entry<String, Object> entry : state.entrySet()) {
                try {
                    java.lang.reflect.Field field = this.getClass().getDeclaredField(entry.getKey());
                    field.setAccessible(true);
                    Object control = field.get(this);
                    Object validValue = entry.getValue();

                    if (control instanceof TextInputControl) {
                        ((TextInputControl) control).setText(validValue == null ? "" : String.valueOf(validValue));
                    } else if (control instanceof ComboBox) {
                        // Safe cast since we stored it as generic objec
                        ((ComboBox<Object>) control).setValue(validValue);
                    } else if (control instanceof CheckBox) {
                        ((CheckBox) control).setSelected(validValue instanceof Boolean
                                ? (Boolean) validValue : Boolean.parseBoolean(String.valueOf(validValue)));
                    } else if (control instanceof RadioButton) {
                        ((RadioButton) control).setSelected(validValue instanceof Boolean
                                ? (Boolean) validValue : Boolean.parseBoolean(String.valueOf(validValue)));
                    }
                } catch (NoSuchFieldException e) {
                    // Field might have been renamed or removed, ignore
                }
            }
        } catch (Exception e) {
            System.err.println("Error restoring UI state: " + e.getMessage());
        } finally {
            isRestoring = false;
        }
    }

    // History Managemen

    private void initializeHistory() {
        if (historyManager == null) {
            historyManager = new com.cryptoforge.model.HistoryManager();
        }
        if (historyViewController != null) {
            historyViewController.setHistoryManager(historyManager);
            historyViewController.setOperationNavigator(this);
        }
        if (clipboardShelfController != null) {
            clipboardShelfController.setNavigator(this, this);
        }
        refreshHistoryUI();
    }

    private void refreshHistoryUI() {
        if (historyViewController != null) {
            historyViewController.refresh();
        }
        if (historyContainer == null) return;

        historyContainer.getChildren().clear();

        java.util.List<com.cryptoforge.model.HistoryItem> items = historyManager.getHistoryItems();

        if (items.isEmpty()) {
            Label placeholder = new Label("No recent operations");
            placeholder.setStyle("-fx-text-fill: #718096; -fx-font-size: 11px; -fx-padding: 10;");
            historyContainer.getChildren().add(placeholder);
            return;
        }

        for (com.cryptoforge.model.HistoryItem item : items) {
            HBox historyItem = new HBox(8);
            historyItem.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            historyItem.setStyle(
                    "-fx-padding: 6; -fx-background-color: #37474f; -fx-background-radius: 4; -fx-border-color: #455a64; -fx-border-radius: 4;");

            VBox infoBox = new VBox(2);
            Label opLabel = new Label(item.getOperation());
            opLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 11px; -fx-font-weight: bold;");

            Label timeLabel = new Label(item.getTimestamp());
            timeLabel.setStyle("-fx-text-fill: #b0bec5; -fx-font-size: 9px;");

            infoBox.getChildren().addAll(opLabel, timeLabel);
            HBox.setHgrow(infoBox, javafx.scene.layout.Priority.ALWAYS);

            Button rerunButton = new Button("Rerun");
            rerunButton.setStyle(
                    "-fx-background-color: #0288d1; " +
                            "-fx-text-fill: #ffffff; " +
                            "-fx-font-size: 10px; " +
                            "-fx-padding: 3 8; " +
                            "-fx-background-radius: 3; " +
                            "-fx-cursor: hand;");

            rerunButton.setOnAction(e -> {
                restoreUIState(item.getUiState());
                updateStatus("Restored state for: " + item.getOperation());
                handleItemSelected(item.getOperation());
            });

            historyItem.getChildren().addAll(infoBox, rerunButton);
            historyContainer.getChildren().add(historyItem);
        }
    }

    @FXML
    private void handleReadPublicKeyFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Read Public Key File");
        fileChooser.getExtensionFilters()
                .add(new FileChooser.ExtensionFilter("PEM Files", "*.pem", "*.key", "*.pub", "*.txt"));
        File file = fileChooser.showOpenDialog(mainPane.getScene().getWindow());
        if (file != null) {
            try {
                String content = new String(java.nio.file.Files.readAllBytes(file.toPath()));
                if (publicKeyArea != null) {
                    publicKeyArea.setText(content);
                    if (cipherController != null) {
                        cipherController.handleLoadPublicKey();
                    }
                }
            } catch (Exception e) {
                showError("Read Error", "Failed to read file: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleReadPrivateKeyFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Read Private Key File");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PEM Files", "*.pem", "*.key", "*.txt"));
        File file = fileChooser.showOpenDialog(mainPane.getScene().getWindow());
        if (file != null) {
            try {
                String content = new String(java.nio.file.Files.readAllBytes(file.toPath()));
                if (privateKeyArea != null) {
                    privateKeyArea.setText(content);
                    if (cipherController != null) {
                        cipherController.handleLoadPrivateKey();
                    }
                }
            } catch (Exception e) {
                showError("Read Error", "Failed to read file: " + e.getMessage());
            }
        }
    }

    public void addToHistory(String operation, java.util.Map<String, String> details) {
        java.util.List<com.cryptoforge.model.OperationDetail> list = new java.util.ArrayList<>();
        if (details != null) {
            details.forEach((k, v) -> list.add(com.cryptoforge.model.OperationDetail.publicDetail(k, v)));
        }
        addToHistory(operation, list);
    }

    @Override
    public void addToHistory(String operation, java.util.List<com.cryptoforge.model.OperationDetail> details) {
        // Capture current state when adding to history
        java.util.Map<String, Object> state = captureUIState();

        String detailsJson = "";
        if (details != null && !details.isEmpty()) {
            try {
                com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping()
                        .create();
                detailsJson = gson.toJson(details);
            } catch (Exception e) {
                detailsJson = details.toString();
            }
        }

        com.cryptoforge.model.HistoryItem item = new com.cryptoforge.model.HistoryItem(operation, detailsJson, state);
        item.setStructuredDetails(details);

        if (historyManager == null) {
            initializeHistory();
        }

        historyManager.addHistoryItem(item);

        refreshHistoryUI();
    }

    public void addToHistoryManual(String operation, String detailsString) {
        java.util.Map<String, Object> state = captureUIState();
        com.cryptoforge.model.HistoryItem item = new com.cryptoforge.model.HistoryItem(operation, detailsString, state);

        if (historyManager == null) {
            initializeHistory();
        }

        historyManager.addHistoryItem(item);
        refreshHistoryUI();
    }

    /** Exposes the shared history store to the FXML history module. */
    public com.cryptoforge.model.HistoryManager getHistoryManager() {
        if (historyManager == null) initializeHistory();
        return historyManager;
    }

    /** Restores an operation selected from the modular history view. */
    public void restoreOperationState(java.util.Map<String, Object> state, String operation) {
        restoreUIState(state);
        updateStatus("Restored state for: " + operation);
        handleItemSelected(operation);
    }

    @FXML
    private void handleExportHistory() {
        if (historyManager == null || historyManager.getHistoryItems().isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Export History");
            alert.setHeaderText("No History to Export");
            alert.setContentText("The history is currently empty.");
            alert.showAndWait();
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export History");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
        fileChooser.setInitialFileName("cryptocarver-history-export.json");

        File file = fileChooser.showSaveDialog(mainPane.getScene().getWindow());
        if (file != null) {
            try (PrintWriter writer = new PrintWriter(file, StandardCharsets.UTF_8)) {
                com.cryptoforge.model.SecretVisibility visibility = com.cryptoforge.model.AppSettings.getInstance()
                        .getSecretVisibility();
                String json = com.cryptoforge.utils.HistoryRecordExporter.toJson(
                        historyManager.getHistoryItems(), visibility);
                writer.write(json);

                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Export Successful");
                alert.setHeaderText(null);
                alert.setContentText("History successfully exported using " + visibility + " policy to:\n"
                        + file.getAbsolutePath());
                alert.showAndWait();
            } catch (IOException e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Export Failed");
                alert.setHeaderText("Error Saving File");
                alert.setContentText(e.getMessage());
                alert.showAndWait();
            }
        }
    }

    @FXML
    private void handleClearHistory() {
        if (historyManager != null) {
            historyManager.clearHistory();
            refreshHistoryUI();
            if (historyTable != null) {
                historyTable.getItems().clear();
            }
            updateStatus("History cleared");
        }
    }

    // Main History View
    private VBox mainHistoryContainer;
    private TableView<com.cryptoforge.model.HistoryItem> historyTable;

    /**
     * Shows the modular Recent Operations view.  The old dynamic builder is
     * retained below temporarily for binary/source compatibility while all
     * navigation uses the FXML-backed controller.
     */
    private void showHistoryView() {
        hideAllContainers();
        initializeHistory();
        if (historyView != null) {
            historyView.setManaged(true);
            historyView.setVisible(true);
        }
        if (historyViewController != null) {
            historyViewController.refresh();
        }
        if (contentTitleLabel != null) {
            contentTitleLabel.setText("Cryptographic Operations");
        }
    }

    private void showClipboardShelf() {
        hideAllContainers();
        if (clipboardShelf != null) {
            clipboardShelf.setManaged(true);
            clipboardShelf.setVisible(true);
        }
        if (clipboardShelfController != null) {
            clipboardShelfController.refresh();
        }
    }

    @SuppressWarnings("unused")
    private void showLegacyHistoryView() {
        hideAllContainers();

        // Create main history container if needed
        // Check if existing container has outdated structure (missing SplitPane)
        if (mainHistoryContainer != null &&
                (mainHistoryContainer.getChildren().size() < 2
                        || !(mainHistoryContainer.getChildren().get(1) instanceof SplitPane))) {
            contentContainer.getChildren().remove(mainHistoryContainer);
            mainHistoryContainer = null;
        }

        if (mainHistoryContainer == null) {
            mainHistoryContainer = new VBox(10);
            mainHistoryContainer.setPadding(new javafx.geometry.Insets(20));
            VBox.setVgrow(mainHistoryContainer, javafx.scene.layout.Priority.ALWAYS);

            Label title = new Label("Recent Operations");
            title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #37474f;");

            Button clearBtn = new Button("Clear All");
            clearBtn.setStyle(
                    "-fx-background-color: #e53935; -fx-text-fill: white; -fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 4; -fx-padding: 4 10;");
            clearBtn.setOnAction(e -> handleClearHistory());

            Button exportRecipeBtn = new Button("Export Recipe");
            exportRecipeBtn.setStyle("-fx-background-color: #0288d1; -fx-text-fill: white; -fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 4; -fx-padding: 4 10;");
            exportRecipeBtn.setTooltip(new Tooltip("Select an operation first. Exports its non-secret settings as a reusable JSON recipe."));
            exportRecipeBtn.setOnAction(e -> exportSelectedHistoryRecipe());

            Button importRecipeBtn = new Button("Import Recipe");
            importRecipeBtn.setStyle("-fx-background-color: #546e7a; -fx-text-fill: white; -fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 4; -fx-padding: 4 10;");
            importRecipeBtn.setOnAction(e -> importOperationRecipe());

            Button compareBtn = new Button("Compare");
            compareBtn.setStyle("-fx-background-color: #8e44ad; -fx-text-fill: white; -fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 4; -fx-padding: 4 10;");
            compareBtn.setOnAction(e -> handleCompareHistory());

            ComboBox<com.cryptoforge.model.SecretVisibility> visibilityCombo = new ComboBox<>();
            visibilityCombo.getItems().setAll(com.cryptoforge.model.SecretVisibility.values());
            visibilityCombo.setValue(com.cryptoforge.model.AppSettings.getInstance().getSecretVisibility());
            visibilityCombo.setOnAction(e -> {
                com.cryptoforge.model.AppSettings.getInstance().setSecretVisibility(visibilityCombo.getValue());
            });
            visibilityCombo.setTooltip(new Tooltip("Global secret visibility (affects recipe export)"));

            HBox header = new HBox(10, title);
            javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
            HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
            header.getChildren().addAll(spacer, new Label("Secrets:"), visibilityCombo, compareBtn, importRecipeBtn, exportRecipeBtn, clearBtn);
            header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            // SplitPane for Table and Details
            SplitPane splitPane = new SplitPane();
            splitPane.setOrientation(javafx.geometry.Orientation.VERTICAL);
            VBox.setVgrow(splitPane, javafx.scene.layout.Priority.ALWAYS);

            // TABLE
            historyTable = new TableView<>();
            historyTable.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);
            historyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
            attachTableViewerSupport(historyTable);

            TableColumn<com.cryptoforge.model.HistoryItem, String> timeCol = new TableColumn<>("Time");
            timeCol.setCellValueFactory(
                    data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getTimestamp()));

            TableColumn<com.cryptoforge.model.HistoryItem, String> opCol = new TableColumn<>("Operation");
            opCol.setCellValueFactory(
                    data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getOperation()));

            TableColumn<com.cryptoforge.model.HistoryItem, String> actionCol = new TableColumn<>("Action");
            actionCol.setCellFactory(col -> new TableCell<>() {
                private final Button btn = new Button("Rerun");
                {
                    btn.setStyle(
                            "-fx-background-color: #0288d1; -fx-text-fill: white; -fx-font-size: 10px; -fx-padding: 3 8; -fx-cursor: hand;");
                    btn.setOnAction(event -> {
                        com.cryptoforge.model.HistoryItem item = getTableView().getItems().get(getIndex());
                        restoreUIState(item.getUiState());
                        updateStatus("Restored state for: " + item.getOperation());
                        handleItemSelected(item.getOperation());
                    });
                }

                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setGraphic(empty ? null : btn);
                }
            });

            historyTable.getColumns().add(timeCol);
            historyTable.getColumns().add(opCol);
            historyTable.getColumns().add(actionCol);

            // DETAILS TABLE
            VBox detailsBox = new VBox(5);
            Label detailsLabel = new Label("Operation Details");
            detailsLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #b0bec5;");

            TableView<com.cryptoforge.model.OperationDetail> detailsTable = new TableView<>();
            detailsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
            attachTableViewerSupport(detailsTable);
            VBox.setVgrow(detailsTable, javafx.scene.layout.Priority.ALWAYS);

            TableColumn<com.cryptoforge.model.OperationDetail, String> nameCol = new TableColumn<>("Property");
            nameCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().name()));

            TableColumn<com.cryptoforge.model.OperationDetail, String> valCol = new TableColumn<>("Value");
            valCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().value()));

            TableColumn<com.cryptoforge.model.OperationDetail, String> classCol = new TableColumn<>("Class");
            classCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().classification().name()));
            classCol.setMaxWidth(80);

            detailsTable.getColumns().addAll(nameCol, valCol, classCol);

            detailsBox.getChildren().addAll(detailsLabel, detailsTable);

            // Selection Listener
            historyTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && newVal.getStructuredDetails() != null) {
                    detailsTable.getItems().setAll(newVal.getStructuredDetails());
                } else if (newVal != null && newVal.getDetails() != null && !newVal.getDetails().isEmpty()) {
                    // Fallback for old history string json
                    detailsTable.getItems().clear();
                    detailsTable.getItems().add(com.cryptoforge.model.OperationDetail.publicDetail("Raw JSON", newVal.getDetails()));
                } else {
                    detailsTable.getItems().clear();
                }
            });

            exportRecipeBtn.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                () -> historyTable.getSelectionModel().getSelectedItems().size() != 1,
                historyTable.getSelectionModel().getSelectedItems()
            ));

            compareBtn.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                () -> historyTable.getSelectionModel().getSelectedItems().size() != 2,
                historyTable.getSelectionModel().getSelectedItems()
            ));

            splitPane.getItems().addAll(historyTable, detailsBox);
            splitPane.setDividerPositions(0.6); // Table takes 60%

            mainHistoryContainer.getChildren().addAll(header, splitPane);
            contentContainer.getChildren().add(mainHistoryContainer);

            // Populate
            if (historyManager == null)
                initializeHistory();
            historyTable.getItems().setAll(historyManager.getHistoryItems());
        } else {
            // refresh
            mainHistoryContainer.setManaged(true);
            mainHistoryContainer.setVisible(true);
            if (historyManager == null)
                initializeHistory();

            // Refresh if table exists
            if (historyTable != null) {
                historyTable.getItems().setAll(historyManager.getHistoryItems());
            }
        }
    }

    private void exportSelectedHistoryRecipe() {
        if (historyTable == null || historyTable.getSelectionModel().getSelectedItem() == null) {
            showError("Recipe Export", "Select a history entry first."); return;
        }
        com.cryptoforge.model.HistoryItem item = historyTable.getSelectionModel().getSelectedItem();
        com.cryptoforge.model.OperationRecipe recipe;
        if (item.getStructuredDetails() != null && !item.getStructuredDetails().isEmpty()) {
            recipe = new com.cryptoforge.model.OperationRecipe(
                item.getOperation(),
                item.getStructuredDetails(),
                com.cryptoforge.model.AppSettings.getInstance().getSecretVisibility()
            );
        } else {
            showError("Recipe Export", "Legacy operations without structured details cannot be exported securely.");
            return;
        }
        FileChooser chooser = new FileChooser(); chooser.setTitle("Export Operation Recipe"); chooser.setInitialFileName("cryptocarver-recipe.json");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Recipe JSON", "*.json"));
        File file = chooser.showSaveDialog(mainPane == null || mainPane.getScene() == null ? null : mainPane.getScene().getWindow()); if (file == null) return;
        try {
            java.nio.file.Files.writeString(file.toPath(), recipe.toJson(), java.nio.charset.StandardCharsets.UTF_8);
            updateStatus("Recipe exported: " + file.getName());
            Alert confirmation = new Alert(Alert.AlertType.INFORMATION, "Recipe saved to:\n" + file.getAbsolutePath(), ButtonType.OK);
            confirmation.setTitle("Recipe exported"); confirmation.setHeaderText("Reusable non-secret configuration saved"); confirmation.showAndWait();
        }
        catch (Exception e) { showError("Recipe Export", "Unable to write recipe: " + e.getMessage()); }
    }

    private void handleCompareHistory() {
        java.util.List<com.cryptoforge.model.HistoryItem> selected = historyTable.getSelectionModel().getSelectedItems();
        if (selected == null || selected.size() != 2) return;

        java.util.List<com.cryptoforge.utils.HistoryComparator.DiffEntry> diffs = com.cryptoforge.utils.HistoryComparator.compare(selected.get(0), selected.get(1));

        TableView<com.cryptoforge.utils.HistoryComparator.DiffEntry> diffTable = new TableView<>();
        diffTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        attachTableViewerSupport(diffTable);

        TableColumn<com.cryptoforge.utils.HistoryComparator.DiffEntry, String> keyCol = new TableColumn<>("Property");
        keyCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().key));

        TableColumn<com.cryptoforge.utils.HistoryComparator.DiffEntry, String> val1Col = new TableColumn<>("Item 1\n" + selected.get(0).getTimestamp());
        val1Col.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().value1));

        TableColumn<com.cryptoforge.utils.HistoryComparator.DiffEntry, String> val2Col = new TableColumn<>("Item 2\n" + selected.get(1).getTimestamp());
        val2Col.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().value2));

        diffTable.getColumns().addAll(keyCol, val1Col, val2Col);
        diffTable.getItems().setAll(diffs);

        // Highlight differences
        diffTable.setRowFactory(tv -> new javafx.scene.control.TableRow<com.cryptoforge.utils.HistoryComparator.DiffEntry>() {
            @Override
            protected void updateItem(com.cryptoforge.utils.HistoryComparator.DiffEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                } else if (item.isDifferent) {
                    setStyle("-fx-background-color: #ffcdd2;");
                } else {
                    setStyle("");
                }
            }
        });

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("History Comparison");
        alert.setHeaderText("Comparing:\n1) " + selected.get(0).getOperation() + "\n2) " + selected.get(1).getOperation());

        VBox content = new VBox(diffTable);
        content.setPrefSize(600, 400);
        alert.getDialogPane().setContent(content);
        alert.showAndWait();
    }

    private void importOperationRecipe() {
        FileChooser chooser = new FileChooser(); chooser.setTitle("Import Operation Recipe");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Recipe JSON", "*.json"));
        File file = chooser.showOpenDialog(null); if (file == null) return;
        try {
            com.cryptoforge.model.OperationRecipe recipe = com.cryptoforge.model.OperationRecipe.fromJson(
                    java.nio.file.Files.readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8));
            java.util.Set<String> variableNames = new java.util.TreeSet<>();
            recipe.parameters().values().forEach(value -> variableNames.addAll(
                    com.cryptoforge.model.RecipeVariables.referencedVariables(value)));
            java.util.Map<String, String> variables = new java.util.LinkedHashMap<>();
            for (String name : variableNames) {
                javafx.scene.control.TextInputDialog variableDialog = new javafx.scene.control.TextInputDialog();
                variableDialog.setTitle("Recipe variable");
                variableDialog.setHeaderText("Value required for ${" + name + "}");
                variableDialog.setContentText(name + ":");
                java.util.Optional<String> value = variableDialog.showAndWait();
                if (value.isEmpty()) return;
                variables.put(name, value.get());
            }
            java.util.Map<String, String> resolvedParameters = new java.util.LinkedHashMap<>();
            recipe.parameters().forEach((key, value) -> resolvedParameters.put(key,
                    com.cryptoforge.model.RecipeVariables.resolve(value, variables)));
            StringBuilder preview = new StringBuilder("Operation: ").append(recipe.operation())
                    .append("\nVersion: ").append(recipe.version()).append("\nCreated: ").append(recipe.createdAt()).append("\n\nParameters:\n");
            resolvedParameters.forEach((key, value) -> preview.append(key).append(" = ").append(value).append('\n'));
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION, preview.toString(), ButtonType.CANCEL, ButtonType.OK);
            confirmation.setTitle("Load Operation Recipe");
            confirmation.setHeaderText("Review recipe before restoring its state");
            if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            java.util.Map<String, Object> state = new java.util.LinkedHashMap<>();
            resolvedParameters.forEach((key, value) -> { if (!"historyTimestamp".equals(key)) state.put(key, value); });
            restoreUIState(state);
            handleItemSelected(recipe.operation());
            updateStatus("Recipe loaded: " + recipe.operation() + " (review inputs before running)");
        } catch (Exception e) { showError("Recipe Import", "Invalid or unsupported recipe: " + e.getMessage()); }
    }

    private void showSymmetricKeys() {
        hideAllContainers();

        // Show symmetric keys accordion
        symmetricKeysContainer.setManaged(true);
        symmetricKeysContainer.setVisible(true);
    }

    private void showAsymmetricKeys() {
        hideAllContainers();

        if (contentTitleLabel != null) {
            contentTitleLabel.setText("Asymmetric Keys");
        }

        // Show asymmetric keys accordion
        asymmetricKeysContainer.setManaged(true);
        asymmetricKeysContainer.setVisible(true);
    }

    private void showCertificates() {
        hideAllContainers();

        // Show certificates accordion
        if (certificatesContainer != null) {
            certificatesContainer.setManaged(true);
            certificatesContainer.setVisible(true);
        }
    }

    private void showCipher() {
        hideAllContainers();

        if (cipherContainer != null) {
            cipherContainer.setManaged(true);
            cipherContainer.setVisible(true);
        }
    }

    private void expandCipherAccordionPane(String itemName) {
        if (cipherContainer == null)
            return;

        Accordion accordion = (Accordion) cipherContainer.getChildren().stream()
                .filter(node -> node instanceof Accordion)
                .findFirst()
                .orElse(null);

        if (accordion != null) {
            String targetPane = "";
            if (itemName.contains("File Cipher")) {
                targetPane = "File Cipher";
            } else if (itemName.contains("OpenPGP") || itemName.contains("GPG")) {
                targetPane = "OpenPGP";
            } else if (itemName.contains("Symmetric") || itemName.contains("AES") || itemName.contains("DES")
                    || itemName.contains("Padding")) {
                targetPane = "Symmetric";
            } else if (itemName.contains("Asymmetric") || itemName.contains("RSA") || itemName.contains("ECC")) {
                targetPane = "Asymmetric";
            }

            for (TitledPane pane : accordion.getPanes()) {
                if (!targetPane.isEmpty() && pane.getText().contains(targetPane)) {
                    accordion.setExpandedPane(pane);
                    revealExpandedPane(pane);
                    break;
                }
            }
        }
    }

    private void showAuthentication() {
        hideAllContainers();

        if (authenticationContainer != null) {
            authenticationContainer.setManaged(true);
            authenticationContainer.setVisible(true);
        }
    }

    private void expandAuthenticationAccordionPane(String itemName) {
        if (authenticationContainer == null)
            return;

        Accordion accordion = (Accordion) authenticationContainer.getChildren().stream()
                .filter(node -> node instanceof Accordion)
                .findFirst()
                .orElse(null);

        if (accordion != null) {
            String targetPane = "";
            if (itemName.contains("Signature") || itemName.contains("Sign")) {
                targetPane = "Signatures";
            } else if (itemName.contains("MAC")) {
                targetPane = "MAC";
            }

            for (TitledPane pane : accordion.getPanes()) {
                if (!targetPane.isEmpty() && pane.getText().contains(targetPane)) {
                    accordion.setExpandedPane(pane);
                    revealExpandedPane(pane);
                    break;
                }
            }
        }
    }

    private void showPayments() {
        hideAllContainers();

        if (paymentsContainer != null) {
            paymentsContainer.setManaged(true);
            paymentsContainer.setVisible(true);
        }
    }

    private void expandPaymentsAccordionPane(String itemName) {
        if (paymentsContainer == null)
            return;

        Accordion accordion = (Accordion) paymentsContainer.getChildren().stream()
                .filter(node -> node instanceof Accordion)
                .findFirst()
                .orElse(null);

        if (accordion != null) {
            String targetPane = "";
            if (itemName.contains("DUKPT")) {
                targetPane = "DUKPT KSN";
            } else if (itemName.contains("CVV")) {
                targetPane = "CVV";
            } else if (itemName.contains("PIN Block Operations")) {
                targetPane = "Clear PIN Blocks";
            } else if (itemName.contains("Clear") || itemName.contains("Encode") || itemName.contains("Decode")) {
                targetPane = "Clear PIN";
            } else if (itemName.contains("Encrypted") || itemName.contains("ISO")) {
                targetPane = "Encrypted PIN";
            } else if (itemName.contains("Generation") || itemName.contains("IBM") || itemName.contains("Generate")
                    || itemName.contains("Verify")) {
                targetPane = "PIN Generation";
            }

            for (TitledPane pane : accordion.getPanes()) {
                if (!targetPane.isEmpty() && pane.getText().contains(targetPane)) {
                    accordion.setExpandedPane(pane);
                    revealExpandedPane(pane);
                    break;
                }
            }
        }
    }

    private void showPlaceholderContent(String title) {
        hideAllContainers();
        if (contentPlaceholderLabel == null) {
            return;
        }

        String requestedOperation = title == null || title.isBlank() ? "Unknown operation" : title;
        contentPlaceholderLabel.setText("📋 " + requestedOperation
                + "\n\nNo view is registered for this legacy operation. Select a tool from the side panel.");
        contentPlaceholderLabel.setManaged(true);
        contentPlaceholderLabel.setVisible(true);
        updateContentHeader(requestedOperation);
        updateContentSubtitle("No module available");
    }

    private void expandAccordionPane(String paneName) {
        // Find and expand the matching accordion pane
        if (paneName == null || paneName.isBlank() || symmetricKeysContainer.getChildren().isEmpty())
            return;

        if (symmetricKeysContainer.getChildren().get(0) instanceof Accordion) {
            Accordion accordion = (Accordion) symmetricKeysContainer.getChildren().get(0);

            for (TitledPane pane : accordion.getPanes()) {
                if (pane.getText().contains(paneName) || paneName.contains(stripEmoji(pane.getText()))) {
                    accordion.setExpandedPane(pane);
                    revealExpandedPane(pane);
                    break;
                }
            }
        }
    }

    private void expandAsymmetricAccordionPane(String paneName) {
        // Find and expand the matching accordion pane
        if (paneName == null || paneName.isBlank() || asymmetricKeysContainer == null || asymmetricKeysContainer.getChildren().isEmpty())
            return;

        if (asymmetricKeysContainer.getChildren().get(0) instanceof Accordion) {
            Accordion accordion = (Accordion) asymmetricKeysContainer.getChildren().get(0);

            for (TitledPane pane : accordion.getPanes()) {
                if (pane.getText().contains(paneName) || paneName.contains(stripEmoji(pane.getText()))) {
                    accordion.setExpandedPane(pane);
                    revealExpandedPane(pane);
                    break;
                }
            }
        }
    }

    private void expandCertificatesAccordionPane(String paneName) {
        // Find and expand the matching accordion pane
        if (paneName == null || paneName.isBlank() || certificatesContainer == null || certificatesContainer.getChildren().isEmpty())
            return;

        for (javafx.scene.Node child : certificatesContainer.getChildren()) {
            if (child instanceof Accordion) {
                Accordion accordion = (Accordion) child;
                if ("CMS Inspector".equals(paneName) && cmsInspector != null) {
                    accordion.setExpandedPane(cmsInspector);
                    revealExpandedPane(cmsInspector);
                    return;
                }
                for (TitledPane pane : accordion.getPanes()) {
                    if (pane.getText().contains(paneName) || paneName.contains(stripEmoji(pane.getText()))) {
                        accordion.setExpandedPane(pane);
                        revealExpandedPane(pane);
                        return;
                    }
                }
            }
        }
    }

    // Helper to strip emojis
    private String stripEmoji(String text) {
        // Remove emoji characters
        return text.replaceAll("[^\\p{L}\\p{N}\\p{P}\\p{Z}]", "").trim();

    }

    @Override
    public void updateStatus(String message) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> updateStatus(message));
            return;
        }
        if (statusLabel == null) return;
        statusLabel.setText(message);
        statusResetTimer.stop();
        statusResetTimer.setOnFinished(event -> statusLabel.setText("Ready"));
        statusResetTimer.playFromStart();
    }

    // Menu handlers
    @FXML
    private void handleExit() {
        Platform.exit();
    }

    @FXML
    private void handleClearInput() {
        if (currentActiveOperation == null) {
            updateStatus("No active operation to clear");
            return;
        }

        if (isContainerVisible(emvContainer) && emvController != null) {
            emvController.handleClear();
        } else if (isContainerVisible(cipherContainer) && cipherController != null) {
            cipherController.handleClear();
        } else if (isContainerVisible(symmetricKeysContainer) && keysController != null) {
            keysController.handleClear();
        } else if (isContainerVisible(asymmetricKeysContainer) && keysController != null) {
            keysController.handleClearAsymmetric();
        } else if (isContainerVisible(genericContainer) && genericContainerController != null) {
            genericContainerController.handleClear();
        } else if (isContainerVisible(certificatesContainer)) {
            // Certificate clearing not fully implemented via global toolbar ye
        }

        clearPublishedResultSnapshot();
        updateStatus("Input cleared");
    }

    @FXML
    private void handleClearOutput() {
        // Reuse clear input logic for now (Clear All)
        handleClearInput();
        updateStatus("Output cleared");
    }

    @FXML
    private void handleCopyOutput() {
        String content = resolveCurrentOutputText();

        if (content != null && !content.isEmpty()) {
            copyToClipboard(content);
            updateStatus("Output copied to clipboard");
        } else {
            updateStatus("No output available to copy");
        }
    }

    /** Adds the active rendered result to the in-session Clipboard Shelf. */
    @FXML
    private void handleAddCurrentOutputToShelf() {
        String content = resolveCurrentOutputText();
        if (content == null || content.isBlank()) {
            showInfo("No result available", "Run an operation with output before adding it to Clipboard Shelf.");
            return;
        }
        handleAddToClipboardShelfSecure(null, content);
    }

    /** Opens the active operation result in a large, independent viewer. */
    @FXML
    private void handleOpenExpandedResultViewer() {
        String content = resolveCurrentOutputText();
        if (content == null || content.isBlank()) {
            showInfo("No result available", "Run an operation with output before opening the expanded viewer.");
            return;
        }
        javafx.stage.Window owner = mainPane == null || mainPane.getScene() == null
                ? null : mainPane.getScene().getWindow();
        String operation = lastPublishedOperation == null || lastPublishedOperation.isBlank()
                ? currentActiveOperation : lastPublishedOperation;
        expandedTextViewer.show(owner, "Expanded Result — " + operation, content);
    }

    String resolveCurrentOutputText() {
        if (lastPublishedResultSnapshot == null) {
            return "";
        }
        return renderPublishedResult(lastPublishedResultSnapshot,
                com.cryptoforge.model.AppSettings.getInstance().getSecretVisibility());
    }

    String renderPublishedResult(com.cryptoforge.model.OperationResult result, com.cryptoforge.model.SecretVisibility visibility) {
        if (result == null) return "";

        com.cryptoforge.model.OperationDetail.Classification overall = com.cryptoforge.model.OperationDetail.Classification.PUBLIC;
        for (com.cryptoforge.model.OperationDetail detail : result.getDetails()) {
            if (detail == null) continue;
            if (detail.classification() == com.cryptoforge.model.OperationDetail.Classification.SECRET) {
                overall = com.cryptoforge.model.OperationDetail.Classification.SECRET;
                break;
            }
            if (detail.classification() == com.cryptoforge.model.OperationDetail.Classification.SENSITIVE) {
                overall = com.cryptoforge.model.OperationDetail.Classification.SENSITIVE;
            }
        }

        if (overall == com.cryptoforge.model.OperationDetail.Classification.SECRET) {
            if (visibility == com.cryptoforge.model.SecretVisibility.REDACTED) return "";
            if (visibility == com.cryptoforge.model.SecretVisibility.MASKED) return "***MASKED***";
        } else if (overall == com.cryptoforge.model.OperationDetail.Classification.SENSITIVE) {
            if (visibility != com.cryptoforge.model.SecretVisibility.FULL_LAB) return "***MASKED***";
        }

        if (result.getEnrichedOutput() != null && !result.getEnrichedOutput().isBlank()) {
            com.cryptoforge.model.OperationDetail.Classification cls = result.getEnrichedOutputClassification();
            if (cls == com.cryptoforge.model.OperationDetail.Classification.SECRET) {
                if (visibility == com.cryptoforge.model.SecretVisibility.REDACTED) return "";
                if (visibility == com.cryptoforge.model.SecretVisibility.MASKED) return "***MASKED***";
            } else if (cls == com.cryptoforge.model.OperationDetail.Classification.SENSITIVE) {
                if (visibility != com.cryptoforge.model.SecretVisibility.FULL_LAB) return "***MASKED***";
            }
            return result.getEnrichedOutput();
        }

        byte[] output = result.getOutput();
        if (output == null || output.length == 0) {
            return formatPublishedSummary(result);
        }

        com.cryptoforge.model.OperationDetail.Classification outCls = result.getOutputClassification();
        if (outCls == com.cryptoforge.model.OperationDetail.Classification.SECRET) {
            if (visibility == com.cryptoforge.model.SecretVisibility.REDACTED) return "";
            if (visibility == com.cryptoforge.model.SecretVisibility.MASKED) return "***MASKED***";
        } else if (outCls == com.cryptoforge.model.OperationDetail.Classification.SENSITIVE) {
            if (visibility != com.cryptoforge.model.SecretVisibility.FULL_LAB) return "***MASKED***";
        }

        return renderBytesForDisplay(output);
    }



    private String visibleText(TextArea area) {
        return area != null && isEffectivelyVisible(area) && !area.getText().isBlank() ? area.getText() : "";
    }

    /**
     * Publishes the UI update and refreshes the expanded-view snapshot as one
     * event. This makes the expanded viewer independent from focus order and
     * from the visibility of sibling accordion panes.
     */
    @Override
    public void publish(com.cryptoforge.model.OperationResult result) {
        if (result == null) {
            return;
        }
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> publish(result));
            return;
        }
        lastPublishedOperation = result.getOperation();
        lastPublishedResultSnapshot = result;
        // Text is now resolved on-demand using resolveCurrentOutputText() to dynamically react to SecretVisibility changes.
        lastPublishedResultText = "";
        updateInspector(result.getOperation(), result.getInput(), result.getOutput(), result.getDetails());
        addToHistory(result.getOperation(), detailsForHistory(result));
        if (result.getStatusMessage() != null && !result.getStatusMessage().isBlank()) {
            updateStatus(result.getStatusMessage());
        }
    }

    /** Clears the cached result whenever it no longer represents the visible UI state. */
    private void clearPublishedResultSnapshot() {
        lastPublishedResultText = "";
        lastPublishedOperation = "";
        lastPublishedResultSnapshot = null;
        lastFocusedResultArea = null;
        lastUpdatedResultArea = null;
    }


    /** Renders bytes as strict UTF-8 where possible, otherwise as hexadecimal. */
    private String renderBytesForDisplay(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        if (isPrintableUtf8(bytes)) {
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }
        return bytesToHex(bytes);
    }

    private boolean isPrintableUtf8(byte[] bytes) {
        java.nio.charset.CharsetDecoder decoder = java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
        try {
            String text = decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            return text.codePoints().allMatch(codePoint ->
                    !Character.isISOControl(codePoint) || codePoint == '\n' || codePoint == '\r' || codePoint == '\t');
        } catch (java.nio.charset.CharacterCodingException ignored) {
            // Binary data is intentionally rendered as hexadecimal by the caller.
        }
        return false;
    }

    /**
     * Keeps an operation's actual byte input/output alongside its declared
     * details. They are marked SENSITIVE: visible in Unsafe lab, masked in
     * normal exports and never treated as ordinary public metadata.
     */
    private java.util.List<com.cryptoforge.model.OperationDetail> detailsForHistory(
            com.cryptoforge.model.OperationResult result) {
        java.util.List<com.cryptoforge.model.OperationDetail> details = new java.util.ArrayList<>(result.getDetails());
        addPayloadDetail(details, "Input", result.getInput());
        addPayloadDetail(details, "Output", result.getOutput());
        return details;
    }

    private void addPayloadDetail(java.util.List<com.cryptoforge.model.OperationDetail> details, String name, byte[] bytes) {
        if (bytes == null) {
            return;
        }
        String rendered = renderBytesForDisplay(bytes);
        String format = isPrintableUtf8(bytes) ? "UTF-8" : "Hex";
        details.add(new com.cryptoforge.model.OperationDetail(
                name + " (" + bytes.length + " bytes)", rendered,
                com.cryptoforge.model.OperationDetail.Classification.SENSITIVE,
                rendered.indexOf('\n') >= 0 || rendered.length() > 120, format));
    }

    /** Builds a safe textual result when an operation has no byte payload. */
    private String formatPublishedSummary(com.cryptoforge.model.OperationResult result) {
        StringBuilder summary = new StringBuilder("Operation: ").append(result.getOperation());
        if (result.getStatusMessage() != null && !result.getStatusMessage().isBlank()) {
            summary.append("\nStatus: ").append(result.getStatusMessage());
        }
        com.cryptoforge.model.SecretVisibility visibility = com.cryptoforge.model.AppSettings.getInstance()
                .getSecretVisibility();
        for (com.cryptoforge.model.OperationDetail detail : result.getDetails()) {
            if (detail == null || (detail.classification() == com.cryptoforge.model.OperationDetail.Classification.SECRET
                    && visibility == com.cryptoforge.model.SecretVisibility.REDACTED)) {
                continue;
            }
            String value = detail.value();
            if (detail.classification() == com.cryptoforge.model.OperationDetail.Classification.SECRET
                    && visibility == com.cryptoforge.model.SecretVisibility.MASKED) {
                value = "***MASKED***";
            } else if (detail.classification() == com.cryptoforge.model.OperationDetail.Classification.SENSITIVE
                    && visibility != com.cryptoforge.model.SecretVisibility.FULL_LAB) {
                value = "***MASKED***";
            }
            summary.append("\n").append(detail.name()).append(": ").append(value == null ? "" : value);
        }
        return summary.toString();
    }

    /** Finds the result currently shown in the active pane, even if it has not received focus. */
    private TextArea findVisibleResultArea() {
        if (mainPane == null) {
            return null;
        }
        return mainPane.lookupAll(".text-area").stream()
                .filter(TextArea.class::isInstance)
                .map(TextArea.class::cast)
                .filter(this::isLikelyResultArea)
                .filter(this::isEffectivelyVisible)
                .filter(area -> !area.getText().isBlank())
                .max(java.util.Comparator.comparingInt(area -> area.getText().length()))
                .orElse(null);
    }

    /**
     * Result areas can be opened directly even when their feature does not use the
     * shared global output panel (for example XAdES, PQC and inspectors).
     */
    private void installResultViewerSupport() {
        if (mainPane == null) {
            return;
        }
        java.util.Set<javafx.scene.Node> candidateAreas = new java.util.LinkedHashSet<>(mainPane.lookupAll(".text-area"));
        // A few FXML layouts are assembled inside nested panes. Register the
        // shared cipher outputs explicitly as well, so their programmatic
        // updates are tracked even before the pane is attached to a Scene.
        if (cipherOutputArea != null) {
            candidateAreas.add(cipherOutputArea);
        }
        if (fileCipherResultArea != null) {
            candidateAreas.add(fileCipherResultArea);
        }
        for (javafx.scene.Node node : candidateAreas) {
            if (!(node instanceof TextArea area) || !isLikelyResultArea(area)) {
                continue;
            }
            if (!resultViewerAreas.add(area)) {
                continue;
            }
            area.focusedProperty().addListener((observable, wasFocused, isFocused) -> {
                if (isFocused) {
                    lastFocusedResultArea = area;
                }
            });
            area.textProperty().addListener((observable, previous, current) -> {
                if (current != null && !current.isBlank()) {
                    lastUpdatedResultArea = area;
                }
            });
            area.setContextMenu(createResultContextMenu(area));
        }
    }

    private boolean isLikelyResultArea(TextArea area) {
        String id = area.getId();
        if (id == null) {
            return false;
        }
        String normalized = id.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("output") || normalized.contains("result")
                || normalized.contains("report") || normalized.contains("details")
                || normalized.contains("ciphertext");
    }

    private ContextMenu createResultContextMenu(TextArea area) {
        MenuItem expand = new MenuItem("Open in Expanded Viewer");
        expand.setOnAction(event -> {
            lastFocusedResultArea = area;
            handleOpenExpandedResultViewer();
        });

        MenuItem addToShelf = new MenuItem("Add to Clipboard Shelf");
        addToShelf.setOnAction(event -> {
            lastFocusedResultArea = area;
            String selected = area.getSelectedText();
            handleAddToClipboardShelfSecure(area, selected != null && !selected.isEmpty() ? selected : null);
        });

        MenuItem copyToSys = new MenuItem("Copy to System Clipboard");
        copyToSys.setOnAction(event -> {
            lastFocusedResultArea = area;
            handleCopySecure(area, null, false);
        });

        MenuItem copy = new MenuItem("Copy");
        copy.setOnAction(event -> {
            lastFocusedResultArea = area;
            String selected = area.getSelectedText();
            boolean hasSelection = selected != null && !selected.isEmpty();
            handleCopySecure(area, hasSelection ? selected : null, hasSelection);
        });

        MenuItem selectAll = new MenuItem("Select All");
        selectAll.setOnAction(event -> area.selectAll());
        return new ContextMenu(expand, addToShelf, copyToSys, new SeparatorMenuItem(), copy, selectAll);
    }

    private void handleCopySecure(TextArea area, String textToCopy, boolean isSelection) {
        if (!isSelection) {
            String content = resolveCurrentOutputText();
            if (content == null || content.isEmpty()) {
                updateStatus("Action blocked: No current output available to copy.");
                return;
            }
            if (content.equals("***MASKED***")) {
                updateStatus("Action blocked: Secret cannot be copied in current visibility mode.");
                return;
            }
            copyToClipboard(content);
            return;
        }

        if (textToCopy == null || textToCopy.isEmpty()) return;

        // If it's a selection, ensure it comes from the current snapshot. We do not allow old selections to be treated as PUBLIC by default.
        if (lastPublishedResultSnapshot == null || area != lastUpdatedResultArea) {
            updateStatus("Action blocked: Cannot securely copy selection from old or unknown result.");
            return;
        }

        com.cryptoforge.model.OperationDetail.Classification cls = classifyPublishedResult(lastPublishedResultSnapshot);
        boolean requiresFullLab = cls == com.cryptoforge.model.OperationDetail.Classification.SECRET
                || cls == com.cryptoforge.model.OperationDetail.Classification.SENSITIVE;
        if (requiresFullLab && com.cryptoforge.model.AppSettings.getInstance().getSecretVisibility() != com.cryptoforge.model.SecretVisibility.FULL_LAB) {
            updateStatus("Action blocked: Cannot copy partial selection of protected text in current visibility mode.");
            return;
        }

        copyToClipboard(textToCopy);
    }

    private void handleAddToClipboardShelfSecure(javafx.scene.control.TextArea area, String selectedText) {
        String text;
        if (selectedText != null) {
            // Handle partial selection securely
            if (lastPublishedResultSnapshot == null || area != lastUpdatedResultArea) {
                updateStatus("Action blocked: Cannot securely add selection from old or unknown result.");
                return;
            }
            com.cryptoforge.model.OperationDetail.Classification cls = classifyPublishedResult(lastPublishedResultSnapshot);
            boolean requiresFullLab = cls == com.cryptoforge.model.OperationDetail.Classification.SECRET
                    || cls == com.cryptoforge.model.OperationDetail.Classification.SENSITIVE;
            if (requiresFullLab && com.cryptoforge.model.AppSettings.getInstance().getSecretVisibility() != com.cryptoforge.model.SecretVisibility.FULL_LAB) {
                updateStatus("Action blocked: Cannot add partial selection of protected text in current visibility mode.");
                return;
            }
            text = selectedText;
        } else {
            text = resolveCurrentOutputText();
        }

        if (text == null || text.isEmpty()) {
             updateStatus("Action blocked: Content hidden by security policy.");
             return;
        }

        if (text.equals("***MASKED***")) {
             updateStatus("Action blocked: Cannot add masked content to shelf.");
             return;
        }

        if (area == cipherOutputArea && gcmTagField != null && !gcmTagField.getText().isEmpty() && selectedText == null) {
             text = text + gcmTagField.getText();
        }

        com.cryptoforge.model.ClipboardEntry.Format format = com.cryptoforge.model.ClipboardEntry.Format.inferFormat(text);
        com.cryptoforge.model.OperationDetail.Classification cls = classifyPublishedResult(lastPublishedResultSnapshot);
        String sourceOp = lastPublishedResultSnapshot != null ? lastPublishedResultSnapshot.getOperation() : (currentActiveOperation != null ? currentActiveOperation : "Unknown");

        // Classification already determined by classifyPublishedResult

        com.cryptoforge.model.ClipboardEntry entry = new com.cryptoforge.model.ClipboardEntry(
                "Copied from " + sourceOp,
                text,
                format,
                cls,
                sourceOp
        );
        com.cryptoforge.model.ClipboardShelfManager.getInstance().addEntry(entry);
        updateStatus("Added to Clipboard Shelf");
    }

    public com.cryptoforge.model.OperationDetail.Classification classifyPublishedResult(com.cryptoforge.model.OperationResult result) {
        if (result == null) return com.cryptoforge.model.OperationDetail.Classification.PUBLIC;

        com.cryptoforge.model.OperationDetail.Classification maxCls = com.cryptoforge.model.OperationDetail.Classification.PUBLIC;

        if (result.getOutputClassification() == com.cryptoforge.model.OperationDetail.Classification.SECRET ||
            result.getEnrichedOutputClassification() == com.cryptoforge.model.OperationDetail.Classification.SECRET) {
            return com.cryptoforge.model.OperationDetail.Classification.SECRET;
        }

        if (result.getOutputClassification() == com.cryptoforge.model.OperationDetail.Classification.SENSITIVE ||
            result.getEnrichedOutputClassification() == com.cryptoforge.model.OperationDetail.Classification.SENSITIVE) {
            maxCls = com.cryptoforge.model.OperationDetail.Classification.SENSITIVE;
        }

        for (com.cryptoforge.model.OperationDetail detail : result.getDetails()) {
            if (detail == null) continue;
            if (detail.classification() == com.cryptoforge.model.OperationDetail.Classification.SECRET) {
                return com.cryptoforge.model.OperationDetail.Classification.SECRET;
            }
            if (detail.classification() == com.cryptoforge.model.OperationDetail.Classification.SENSITIVE) {
                maxCls = com.cryptoforge.model.OperationDetail.Classification.SENSITIVE;
            }
        }

        return maxCls;
    }

    public void fillClipboardTarget(String targetType, String value, com.cryptoforge.model.ClipboardEntry.Format format) {
        if (value == null) return;
        switch (targetType) {
            case "MANUAL_CONVERSION":
                if (genericContainerController != null) {
                    genericContainerController.fillManualConversionInput(value, format);
                    expandGenericAccordionPane("Manual Conversion");
                }
                break;
            case "SYMMETRIC_CIPHER":
                if (cipherController != null) {
                    cipherController.fillSymmetricCipherInput(value, format);
                    expandCipherAccordionPane("Symmetric Ciphers");
                }
                break;
            case "HASHING":
                if (genericContainerController != null) {
                    genericContainerController.fillHashInput(value);
                    expandGenericAccordionPane("Hashing");
                }
                break;
            case "JOSE_JWT":
                if (jwtPayloadArea != null) {
                    jwtPayloadArea.setText(value);
                    showJOSE();
                }
                break;
            default:
                updateStatus("Unsupported target: " + targetType);
        }
    }

    private boolean isEffectivelyVisible(javafx.scene.Node node) {
        for (javafx.scene.Node current = node; current != null; current = current.getParent()) {
            if (!current.isVisible()) {
                return false;
            }
            if (current instanceof TitledPane pane && !pane.isExpanded()) {
                return false;
            }
        }
        return true;
    }

    @FXML
    private void handleOpenExpandedTableViewer() {
        if (lastFocusedTable == null) {
            showInfo("No table selected", "Right-click a table or select a cell before opening its expanded view.");
            return;
        }
        openExpandedTable(lastFocusedTable);
    }

    private void installTableViewerSupport() {
        if (mainPane == null) {
            return;
        }
        for (javafx.scene.Node node : mainPane.lookupAll(".table-view")) {
            if (node instanceof TableView<?> table) {
                attachTableViewerSupport(table);
            }
        }
    }

    private void attachTableViewerSupport(TableView<?> table) {
        table.focusedProperty().addListener((observable, wasFocused, isFocused) -> {
            if (isFocused) {
                lastFocusedTable = table;
            }
        });
        MenuItem expand = new MenuItem("Open table in expanded viewer");
        expand.setOnAction(event -> openExpandedTable(table));
        table.setContextMenu(new ContextMenu(expand));
    }

    private void openExpandedTable(TableView<?> table) {
        lastFocusedTable = table;
        javafx.stage.Window owner = mainPane == null || mainPane.getScene() == null
                ? null : mainPane.getScene().getWindow();
        expandedTableViewer.show(owner, "Expanded Table — " + currentActiveOperation, table);
    }

    private boolean isContainerVisible(javafx.scene.Node container) {
        return container != null && container.isVisible();
    }

    // Helper to check accordion expansion
    private boolean isAccordionExpanded(Accordion accordion, String paneTitle) {
        if (accordion.getExpandedPane() != null) {
            return accordion.getExpandedPane().getText().equals(paneTitle);
        }
        return false;
    }

    private void copyToClipboard(String text) {
        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);
    }

    @FXML
    private void handleToggleSidePanel() {
        boolean visible = sidePanel.isVisible();
        sidePanel.setVisible(!visible);
        sidePanel.setManaged(!visible);
        updateStatus(visible ? "Side panel hidden" : "Side panel shown");
    }

    @FXML
    private void handleToggleInspector() {
        boolean visible = inspectorPanel.isVisible();
        inspectorPanel.setVisible(!visible);
        inspectorPanel.setManaged(!visible);
        updateStatus(visible ? "Inspector hidden" : "Inspector shown");
    }

    @FXML
    private void handleResetView() {
        sidePanel.setVisible(true);
        sidePanel.setManaged(true);
        inspectorPanel.setVisible(false);
        inspectorPanel.setManaged(false);
        updateStatus("View reset to defaults");
    }

    private int currentFontSize = 14;

    @FXML
    private void handleIncreaseFontSize() {
        if (currentFontSize < 24) {
            currentFontSize += 2;
            applyFontSize();
            updateStatus("Font size increased to " + currentFontSize + "px");
        }
    }

    @FXML
    private void handleDecreaseFontSize() {
        if (currentFontSize > 8) {
            currentFontSize -= 2;
            applyFontSize();
            updateStatus("Font size decreased to " + currentFontSize + "px");
        }
    }

    private void applyFontSize() {
        // Recursively find all TextAreas and TextFields in the mainContentArea and
        // inspectorPanel
        updateNodeFonts(mainContentArea);
        updateNodeFonts(inspectorPanel);
    }

    private byte[] getBytesFromPEM(String pem) {
        if (pem == null || pem.isEmpty())
            return new byte[0];
        try {
            String base64 = pem.replaceAll("-----BEGIN [A-Z ]+-----\n?", "")
                    .replaceAll("-----END [A-Z ]+-----\n?", "")
                    .replaceAll("\\s+", "");
            return java.util.Base64.getDecoder().decode(base64);
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private void updateNodeFonts(javafx.scene.Node node) {
        if (node == null)
            return;

        if (node instanceof TextArea) {
            ((TextArea) node).setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: " + currentFontSize + "px;");
        } else if (node instanceof TextField) {
            ((TextField) node).setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: " + currentFontSize + "px;");
        }

        // Recursive traversal
        if (node instanceof ScrollPane) {
            updateNodeFonts(((ScrollPane) node).getContent());
        } else if (node instanceof TitledPane) {
            updateNodeFonts(((TitledPane) node).getContent());
        } else if (node instanceof Accordion) {
            for (TitledPane pane : ((Accordion) node).getPanes()) {
                updateNodeFonts(pane);
            }
        } else if (node instanceof SplitPane) {
            for (javafx.scene.Node child : ((SplitPane) node).getItems()) {
                updateNodeFonts(child);
            }
        } else if (node instanceof javafx.scene.Parent) {
            for (javafx.scene.Node child : ((javafx.scene.Parent) node).getChildrenUnmodifiable()) {
                updateNodeFonts(child);
            }
        }
    }

    @FXML
    private void handleAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About CryptoCarver");
        alert.setHeaderText("CryptoCarver");
        alert.setContentText("A comprehensive tool for cryptographic operations.\n\n" +
                "Version: 1.0.0\n" +
                "Author: Felipe Rodríguez Fonte\n" +
                "Contact: felipe.rodriguez.fonte@gmail.com\n\n" +
                "Features:\n" +
                "- Symmetric & Asymmetric Encryption\n" +
                "- Digital Signatures & Certificates\n" +
                "- Payments (EMV, PIN, CVV)\n" +
                "- JOSE (JWT, JWE, JWK)\n" +
                "- ASN.1 Analysis");
        alert.showAndWait();
    }

    @FXML
    private void handleDiagnostics() {
        String diagnosticText = AppDiagnostics.report();
        TextArea report = new TextArea(diagnosticText);
        report.setEditable(false);
        report.setWrapText(false);
        report.setPrefColumnCount(68);
        report.setPrefRowCount(15);
        report.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("CryptoCarver diagnostics");
        alert.setHeaderText("Runtime information (safe to copy)");
        alert.getDialogPane().setContent(report);
        alert.getDialogPane().setPrefWidth(680);
        ButtonType copyButton = new ButtonType("Copy report", ButtonBar.ButtonData.LEFT);
        alert.getDialogPane().getButtonTypes().add(copyButton);
        java.util.Optional<ButtonType> selected = alert.showAndWait();
        if (selected.isPresent() && selected.get() == copyButton) {
            javafx.scene.input.ClipboardContent clipboard = new javafx.scene.input.ClipboardContent();
            clipboard.putString(diagnosticText);
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(clipboard);
            updateStatus("Diagnostics copied to clipboard");
        }
    }

    // ============================================================================
    // CIPHER HANDLERS
    // ============================================================================

    public void handleLoadPublicKey() {
        if (cipherController != null)
            cipherController.handleLoadPublicKey();
    }

    @FXML
    public void handleLoadPrivateKey() {
        if (cipherController != null)
            cipherController.handleLoadPrivateKey();
    }

    @FXML
    private void handleGenerateIV() {
        if (cipherController != null) {
            cipherController.generateIV();
        }
    }

    @FXML
    private void handleSymmetricEncrypt() {
        if (cipherController != null) {
            cipherController.handleSymmetricEncrypt();
        }
    }

    @FXML
    private void handleSymmetricDecrypt() {
        if (cipherController != null) {
            cipherController.handleSymmetricDecrypt();
        }
    }

    @FXML
    private void handleAsymmetricEncrypt() {
        if (cipherController != null) {
            // Try to share key from KeysController
            if (keysController != null) {
                java.security.KeyPair kp = keysController.getLastGeneratedKeyPair();
                if (kp != null) {
                    cipherController.setPublicKey(kp.getPublic());
                    cipherController.setPrivateKey(kp.getPrivate());
                }
            }

            cipherController.handleAsymmetricEncrypt();
        }
    }

    @FXML
    private void handleAsymmetricDecrypt() {
        if (cipherController != null) {
            // Try to share key from KeysController
            if (keysController != null) {
                java.security.KeyPair kp = keysController.getLastGeneratedKeyPair();
                if (kp != null) {
                    cipherController.setPublicKey(kp.getPublic());
                    cipherController.setPrivateKey(kp.getPrivate());
                }
            }

            cipherController.handleAsymmetricDecrypt();
        }
    }

    // ============================================================
    // HELPER METHODS FOR KeysController
    // ============================================================

    @Override
    public void showError(String title, String message) {
        if ("true".equals(System.getProperty("test.mode"))) {
            System.err.println("SHOW_ERROR: " + title + " - " + message);
            return;
        }
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void showWarning(String title, String message) {
        if ("true".equals(System.getProperty("test.mode"))) {
            System.out.println("SHOW_WARNING: " + title + " - " + message);
            return;
        }
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @Override
    public void showInfo(String title, String message) {
        if ("true".equals(System.getProperty("test.mode"))) {
            System.out.println("SHOW_INFO: " + title + " - " + message);
            return;
        }
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Generic module initialized by FXML include

















    @FXML private void handleVisualizeBytes() {

    }














    // File conversion handlers moved to GenericController







    private void enableFileDrop(TextField field) {
        if (field == null) return;
        field.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) event.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
            event.consume();
        });
        field.setOnDragDropped(event -> {
            boolean accepted = event.getDragboard().hasFiles() && !event.getDragboard().getFiles().isEmpty();
            if (accepted) field.setText(event.getDragboard().getFiles().get(0).getAbsolutePath());
            event.setDropCompleted(accepted);
            event.consume();
        });
    }















    // ============================================================
    // EVENT HANDLERS - Authentication Operations
    // ============================================================

    @FXML
    private void handleLoadSignPrivateKey() {
        if (authenticationController != null) {
            authenticationController.handleLoadSignPrivateKey();
            updateInspector("Load Private Key");
            addToHistory("Private Key Loaded", new java.util.HashMap<>());
        }
    }

    @FXML
    private void handleLoadSignPublicKey() {
        if (authenticationController != null) {
            authenticationController.handleLoadSignPublicKey();
            updateInspector("Load Public Key");
            addToHistory("Public Key Loaded", new java.util.HashMap<>());
        }
    }

    @FXML
    private void handleSign() {
        if (authenticationController != null) {
            authenticationController.handleSign();
            updateInspector("Digital Signature");
        }
    }

    @FXML
    private void handleVerify() {
        if (authenticationController != null) {
            authenticationController.handleVerify();
            updateInspector("Signature Verification");
        }
    }

    @FXML
    private void handleGenerateMAC() {
        if (authenticationController != null) {
            authenticationController.handleGenerateMAC();
            updateInspector("Generate MAC");
        }
    }

    @FXML
    private void handleVerifyMAC() {
        if (authenticationController != null) {
            authenticationController.handleVerifyMAC();
            updateInspector("Verify MAC");
        }
    }

    @FXML
    private void handleFileCipherEncrypt() {
        if (cipherController != null) cipherController.handleFileCipherEncrypt();
    }

    @FXML
    private void handleFileCipherDecrypt() {
        if (cipherController != null) cipherController.handleFileCipherDecrypt();
    }

    @FXML
    private void handleChooseFileCipherSource() {
        if (cipherController != null) cipherController.chooseFileCipherSource();
    }

    @FXML
    private void handleChooseFileCipherDestination() {
        if (cipherController != null) cipherController.chooseFileCipherDestination();
    }

    @FXML
    private void handleChooseFileCipherTag() {
        if (cipherController != null) cipherController.chooseFileCipherTag();
    }

    @FXML
    private void handleGenerateFileCipherNonce() {
        if (cipherController != null) cipherController.generateFileCipherNonce();
    }

    // ============================================================
    // EVENT HANDLERS - Payments Operations
    // ============================================================

    @FXML
    private void handleInspectEMVTLV() { if (emvController != null) emvController.handleInspectTlv(); }

    @FXML
    private void handleBuildEMVDOL() { if (emvController != null) emvController.handleBuildDol(); }

    @FXML
    private void handleEncodePinBlock() {
        if (paymentsController != null) {
            paymentsController.handleEncodePinBlock();
        }
    }

    @FXML
    private void handleDecodePinBlock() {
        if (paymentsController != null) {
            paymentsController.handleDecodePinBlock();
        }
    }

    @FXML
    private void handleGenerateCvv() {
        if (paymentsController != null) {
            paymentsController.handleGenerateCvv();
        }
    }

    @FXML
    private void handleVerifyCvv() {
        if (paymentsController != null) {
            paymentsController.handleVerifyCvv();
        }
    }

    @FXML
    private void handleEncodeEncryptedPinBlock() {
        if (paymentsController != null) {
            paymentsController.handleEncodeEncryptedPinBlock();
        }
    }

    @FXML
    private void handleDecodeEncryptedPinBlock() {
        if (paymentsController != null) {
            paymentsController.handleDecodeEncryptedPinBlock();
        }
    }

    @FXML
    private void handleGenerateIbm3624Pin() {
        if (paymentsController != null) {
            paymentsController.handleGenerateIbm3624Pin();
        }
    }

    @FXML
    private void handleVerifyIbm3624Pin() {
        if (paymentsController != null) {
            paymentsController.handleVerifyIbm3624Pin();
        }
    }

    private void hideAllContainers() {
        contentContainer.getChildren().stream()
                .filter(node -> node instanceof Label)
                .forEach(node -> {
                    node.setManaged(false);
                    node.setVisible(false);
                });

        if (symmetricKeysContainer != null) {
            symmetricKeysContainer.setVisible(false);
            symmetricKeysContainer.setManaged(false);
        }
        if (asymmetricKeysContainer != null) {
            asymmetricKeysContainer.setVisible(false);
            asymmetricKeysContainer.setManaged(false);
        }
        if (certificatesContainer != null) {
            certificatesContainer.setVisible(false);
            certificatesContainer.setManaged(false);
        }
        if (cipherContainer != null) {
            cipherContainer.setVisible(false);
            cipherContainer.setManaged(false);
        }
        if (authenticationContainer != null) {
            authenticationContainer.setVisible(false);
            authenticationContainer.setManaged(false);
        }
        if (paymentsContainer != null) {
            paymentsContainer.setVisible(false);
            paymentsContainer.setManaged(false);
        }
        if (emvContainer != null) {
            emvContainer.setVisible(false);
            emvContainer.setManaged(false);
        }
        if (jose != null) {
            jose.setVisible(false);
            jose.setManaged(false);
        }
        if (genericContainer != null) {
            genericContainer.setVisible(false);
            genericContainer.setManaged(false);
        }
        if (historyView != null) {
            historyView.setVisible(false);
            historyView.setManaged(false);
        }
        if (clipboardShelf != null) {
            clipboardShelf.setVisible(false);
            clipboardShelf.setManaged(false);
        }
        if (postQuantumContainer != null) {
            postQuantumContainer.setVisible(false);
            postQuantumContainer.setManaged(false);
        }
        if (xmlSecurityContainer != null) {
            xmlSecurityContainer.setVisible(false);
            xmlSecurityContainer.setManaged(false);
        }
        if (savedSessionsContainer != null) {
            savedSessionsContainer.setVisible(false);
            savedSessionsContainer.setManaged(false);
        }
        if (mainHistoryContainer != null) {
            mainHistoryContainer.setVisible(false);
            mainHistoryContainer.setManaged(false);
        }
    }

    private void showGeneric() {
        hideAllContainers();

        if (genericContainer != null) {
            genericContainer.setManaged(true);
            genericContainer.setVisible(true);
        }

    }

    private void showJOSE() {
        hideAllContainers();

        // Show JOSE container
        if (jose != null) {
            jose.setManaged(true);
            jose.setVisible(true);
        }

        // Initialize controller if needed
        if (joseController != null) {
            // Initialize Combo
            if (jwtAlgoCombo != null && jwtAlgoCombo.getItems().isEmpty()) {
                jwtAlgoCombo.getItems().addAll(
                        "HS256", "HS384", "HS512",
                        "RS256", "RS384", "RS512",
                        "ES256", "ES384", "ES512",
                        "PS256", "PS384", "PS512");
                jwtAlgoCombo.getSelectionModel().selectFirst();
            }

            // Init JWE Combos
            if (jweKeyAlgoCombo != null && jweKeyAlgoCombo.getItems().isEmpty()) {
                jweKeyAlgoCombo.getItems().setAll(
                        "RSA-OAEP-256", "RSA-OAEP-512",
                        "ECDH-ES", "ECDH-ES+A128KW", "ECDH-ES+A256KW");
                jweKeyAlgoCombo.getSelectionModel().selectFirst();
            }
            if (jweContentAlgoCombo != null && jweContentAlgoCombo.getItems().isEmpty()) {
                jweContentAlgoCombo.getItems().setAll("A128GCM", "A256GCM", "A128CBC-HS256", "A256CBC-HS512");
                jweContentAlgoCombo.getSelectionModel().select("A256GCM");
            }

            // Init Nested Combos
            if (nestedSignAlgoCombo != null && nestedSignAlgoCombo.getItems().isEmpty()) {
                nestedSignAlgoCombo.getItems().setAll("HS256", "HS384", "HS512", "RS256", "RS384", "RS512");
                nestedSignAlgoCombo.getSelectionModel().select("HS256");
            }
            if (nestedKeyAlgoCombo != null && nestedKeyAlgoCombo.getItems().isEmpty()) {
                nestedKeyAlgoCombo.getItems().setAll("RSA-OAEP-256", "RSA-OAEP-512");
                nestedKeyAlgoCombo.getSelectionModel().selectFirst();
            }
            if (nestedContentAlgoCombo != null && nestedContentAlgoCombo.getItems().isEmpty()) {
                nestedContentAlgoCombo.getItems().setAll("A128GCM", "A256GCM");
                nestedContentAlgoCombo.getSelectionModel().select("A256GCM");
            }

            // Init JWK Combo
            if (jwkKeyTypeCombo != null && jwkKeyTypeCombo.getItems().isEmpty()) {
                jwkKeyTypeCombo.getItems().setAll("RSA", "EC", "OCT");
                jwkKeyTypeCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
                    if (newV == null)
                        return;
                    if (newV.equals("OCT")) {
                        if (jwkInputLabel != null)
                            jwkInputLabel.setText("Input (Hex / Base64 Secret):");
                        if (jwkInputArea != null)
                            jwkInputArea.setPromptText("Paste Hex or Base64 Secret (e.g. 313233... or MTIz...)");
                        if (pemToJwkBtn != null)
                            pemToJwkBtn.setText("Secret -> JWK");
                        if (jwkToPemBtn != null)
                            jwkToPemBtn.setText("JWK -> Secret");
                    } else {
                        if (jwkInputLabel != null)
                            jwkInputLabel.setText("Input (PEM):");
                        if (jwkInputArea != null)
                            jwkInputArea.setPromptText("Paste PEM Key (e.g. -----BEGIN...)");
                        if (pemToJwkBtn != null)
                            pemToJwkBtn.setText("PEM -> JWK");
                        if (jwkToPemBtn != null)
                            jwkToPemBtn.setText("JWK -> PEM");
                    }
                });
                jwkKeyTypeCombo.getSelectionModel().selectFirst();
            }
            if (jwksRotateAlgoCombo != null && jwksRotateAlgoCombo.getItems().isEmpty()) {
                jwksRotateAlgoCombo.getItems().setAll(
                        "RS256", "RS384", "RS512", "ES256", "ES384", "ES512",
                        "HS256", "HS384", "HS512", "A128KW", "A256KW", "A128GCM", "A256GCM", "dir");
                jwksRotateAlgoCombo.getSelectionModel().selectFirst();
            }

            // Init JWA Table
            if (jwaTable != null && jwaTable.getItems().isEmpty()) {
                initJWATable();
            }

            // Init Template Combo
            if (jwtTemplateCombo != null && jwtTemplateCombo.getItems().isEmpty()) {
                jwtTemplateCombo.getItems().addAll(
                        "OAuth2 Access Token (JWT)",
                        "OIDC ID Token",
                        "DPoP Proof",
                        "Custom (Empty)");
                jwtTemplateCombo.setOnAction(e -> {
                    String sel = jwtTemplateCombo.getValue();
                    if (sel == null)
                        return;
                    String tmpl = "{}";
                    long now = System.currentTimeMillis() / 1000;
                    if (sel.contains("Access Token")) {
                        tmpl = "{\n  \"iss\": \"https://auth.server.com\",\n  \"sub\": \"user_123\",\n  \"aud\": \"https://api.server.com\",\n  \"iat\": "
                                + now + ",\n  \"exp\": " + (now + 3600) + ",\n  \"scope\": \"read write\"\n}";
                    } else if (sel.contains("ID Token")) {
                        tmpl = "{\n  \"iss\": \"https://auth.server.com\",\n  \"sub\": \"user_123\",\n  \"aud\": \"client_id_456\",\n  \"iat\": "
                                + now + ",\n  \"exp\": " + (now + 3600) + ",\n  \"nonce\": \"n-0S6_WzA2Mj\"\n}";
                    } else if (sel.contains("DPoP")) {
                        tmpl = "{\n  \"jti\": \"" + java.util.UUID.randomUUID().toString()
                                + "\",\n  \"htm\": \"POST\",\n  \"htu\": \"https://resource.server.org/protected\",\n  \"iat\": "
                                + now + "\n}";
                    }
                    if (jwtPayloadArea != null) {
                        jwtPayloadArea.setText(tmpl);
                    }
                });
            }
        }

        // Default show JWT if just opened or if switching from another rail with
        // incompatible operation
        boolean isJOSEOp = currentActiveOperation != null && (currentActiveOperation.startsWith("JWT") ||
                currentActiveOperation.startsWith("JWE") ||
                currentActiveOperation.startsWith("JWK") ||
                currentActiveOperation.startsWith("JWA") ||
                currentActiveOperation.startsWith("Token Inspector"));

        if (!isJOSEOp) {
            this.currentActiveOperation = "JWT (Signed)";
            // Update SidePanel selection if possible? (Hard to do reverse)
            // But at least show the conten
        }

        showJOSESubSection(this.currentActiveOperation);
    }

    private void showJOSESubSection(String sectionName) {
        if (jwtSection != null) {
            jwtSection.setManaged(false);
            jwtSection.setVisible(false);
        }
        if (jweSection != null) {
            jweSection.setManaged(false);
            jweSection.setVisible(false);
        }
        if (jwkSection != null) {
            jwkSection.setManaged(false);
            jwkSection.setVisible(false);
        }

        if (jwaSection != null) {
            jwaSection.setManaged(false);
            jwaSection.setVisible(false);
        }
        if (inspectorSection != null) {
            inspectorSection.setManaged(false);
            inspectorSection.setVisible(false);
        }

        if (sectionName == null)
            return;

        if (sectionName.startsWith("JWT")) {
            if (jwtSection != null) {
                jwtSection.setManaged(true);
                jwtSection.setVisible(true);
            }
        } else if (sectionName.startsWith("JWE")) {
            if (jweSection != null) {
                jweSection.setManaged(true);
                jweSection.setVisible(true);
            }
        } else if (sectionName.startsWith("JWK")) {
            if (jwkSection != null) {
                jwkSection.setManaged(true);
                jwkSection.setVisible(true);
            }
        } else if (sectionName.startsWith("JWA")) {
            if (jwaSection != null) {
                jwaSection.setManaged(true);
                jwaSection.setVisible(true);
            }
        } else if (sectionName.startsWith("Token Inspector")) {
            if (inspectorSection != null) {
                inspectorSection.setManaged(true);
                inspectorSection.setVisible(true);
            }
        }
    }

    private void expandGenericAccordionPane(String paneName) {
        if (paneName == null || paneName.isBlank() || genericContainer == null || genericContainer.getPanes().isEmpty())
            return;

        for (TitledPane pane : genericContainer.getPanes()) {
            if (pane.getText().contains(paneName) || paneName.contains(stripEmoji(pane.getText()))) {
                genericContainer.setExpandedPane(pane);
                revealExpandedPane(pane);
                break;
            }
        }
    }

    // Helper methods
    private byte[] hexToBytes(String hex) {
        if (hex == null || hex.isEmpty()) {
            return new byte[0];
        }
        hex = hex.replaceAll("\\s+", "");
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Invalid hex string (odd length)");
        }
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private String bytesToHex(byte[] bytes) {
        if (bytes == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    private void showEMV() {
        hideAllContainers();

        if (emvContainer != null) {
            emvContainer.setManaged(true);
            emvContainer.setVisible(true);
            updateContentHeader("EMV Operations");
            updateContentSubtitle("Session keys, ARQC/ARPC, and Track 2 data");
        }

        // Initialize if not already done
        if (emvController == null) {
            loadEMVContent();
        }
    }

    private void expandEMVAccordionPane(String title) {
        if (title != null && !title.isBlank() && emvContainer != null && !emvContainer.getChildren().isEmpty()) {
            if (emvContainer.getChildren().get(0) instanceof Accordion) {
                Accordion acc = (Accordion) emvContainer.getChildren().get(0);
                for (TitledPane pane : acc.getPanes()) {
                    if (pane.getText().contains(title)) {
                        acc.setExpandedPane(pane);
                        revealExpandedPane(pane);
                        break;
                    }
                }
            }
        }
    }

    /**
     * Makes a pane selected from the navigation tree immediately discoverable,
     * even when it sits far down a long accordion. Layout must complete first,
     * hence the deferred calculation.
     */
    private void revealExpandedPane(TitledPane pane) {
        if (pane == null) {
            return;
        }
        Platform.runLater(() -> {
            pane.requestFocus();
            if (mainScrollPane == null || contentContainer == null || pane.getScene() == null) {
                return;
            }
            javafx.geometry.Bounds contentBounds = contentContainer.localToScene(contentContainer.getBoundsInLocal());
            javafx.geometry.Bounds paneBounds = pane.localToScene(pane.getBoundsInLocal());
            if (contentBounds == null || paneBounds == null) {
                return;
            }
            double scrollableHeight = contentContainer.getBoundsInLocal().getHeight()
                    - mainScrollPane.getViewportBounds().getHeight();
            if (scrollableHeight <= 0) {
                return;
            }
            double target = (paneBounds.getMinY() - contentBounds.getMinY()) / scrollableHeight;
            mainScrollPane.setVvalue(Math.max(0, Math.min(1, target)));
        });
    }

    @FXML
    public void handleGenerateOffsetUtility() {
        if (paymentsController != null) {
            paymentsController.handleGenerateOffsetUtility();
        }
    }

    @FXML
    public void handleGeneratePVVUtility() {
        if (paymentsController != null) {
            paymentsController.handleGeneratePVVUtility();
        }
    }

    @FXML
    public void handleDerivePinFromPvvUtility() {
        if (paymentsController != null) {
            paymentsController.handleDerivePinFromPvvUtility();
        }
    }

    @FXML
    public void handleInspectDUKPT() {
        if (paymentsController != null) paymentsController.handleInspectDukpt();
    }

    @FXML
    public void handleAesDukptPinBlock() {
        if (paymentsController != null) paymentsController.handleAesDukptPinBlock();
    }

    // ============================================================
    // EMV HANDLERS
    // ============================================================

    @FXML
    private void handleDeriveSessionKey() {
        if (emvController != null)
            emvController.handleDeriveSessionKey();
    }

    @FXML
    private void handleGenerateARQC() {
        if (emvController != null)
            emvController.handleGenerateARQC();
    }

    @FXML
    private void handleVerifyARQC() {
        if (emvController != null)
            emvController.handleVerifyARQC();
    }

    @FXML
    private void handleGenerateARPC() {
        if (emvController != null)
            emvController.handleGenerateARPC();
    }

    @FXML
    private void handleEncodeTrack2() {
        if (emvController != null)
            emvController.handleEncodeTrack2();
    }

    @FXML
    private void handleDecodeTrack2() {
        if (emvController != null)
            emvController.handleDecodeTrack2();
    }

    // ============================================================
    // SAVED SESSIONS LOGIC
    // ============================================================

    private void initializeSavedSessions() {
        if (savedSessionsManager == null) {
            savedSessionsManager = com.cryptoforge.model.SavedSessionsManager.getInstance();
        }
        refreshSavedSessionsUI();
    }

    private void refreshSavedSessionsUI() {
        if (savedSessionsList == null)
            return;
        savedSessionsList.getChildren().clear();

        if (savedSessionsManager == null)
            return;

        java.util.List<com.cryptoforge.model.SavedSession> sessions = savedSessionsManager.getSessions();

        if (sessions.isEmpty()) {
            Label placeholder = new Label("No saved sessions");
            placeholder.setStyle("-fx-text-fill: #718096; -fx-font-size: 11px; -fx-padding: 10;");
            savedSessionsList.getChildren().add(placeholder);
            return;
        }

        for (com.cryptoforge.model.SavedSession session : sessions) {
            HBox sessionItem = new HBox(10);
            sessionItem.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            sessionItem.setStyle(
                    "-fx-padding: 10; -fx-background-color: #2d3748; -fx-background-radius: 5; -fx-border-color: #4a5568; -fx-border-radius: 5;");

            VBox infoBox = new VBox(2);
            Label nameLabel = new Label(session.getName());
            nameLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px;");

            Label detailsLabel = new Label(session.getTimestamp() + " • " + session.getOperation());
            detailsLabel.setStyle("-fx-text-fill: #a0aec0; -fx-font-size: 11px;");

            infoBox.getChildren().addAll(nameLabel, detailsLabel);
            HBox.setHgrow(infoBox, javafx.scene.layout.Priority.ALWAYS);

            Button loadButton = new Button("Load");
            loadButton.getStyleClass().add("action-button");
            loadButton.setStyle("-fx-font-size: 11px; -fx-padding: 5 10;");
            loadButton.setOnAction(e -> {
                restoreUIState(session.getUiState());
                // Switch to the relevant view contex
                handleItemSelected(session.getOperation());
                updateStatus("Loaded session: " + session.getName());
            });

            Button deleteButton = new Button("Delete");
            deleteButton.getStyleClass().add("secondary-button");
            deleteButton.setStyle("-fx-font-size: 11px; -fx-padding: 5 10; -fx-text-fill: #fc8181;");
            deleteButton.setOnAction(e -> {
                savedSessionsManager.removeSession(session);
                refreshSavedSessionsUI();
                updateStatus("Deleted session");
            });

            sessionItem.getChildren().addAll(infoBox, loadButton, deleteButton);
            savedSessionsList.getChildren().add(sessionItem);
        }
    }

    private void showSavedSessions() {
        hideAllContainers();
        if (savedSessionsContainer != null) {
            savedSessionsContainer.setVisible(true);
            savedSessionsContainer.setManaged(true);
            initializeSavedSessions();
        }
        updateContentHeader("Saved Sessions");
        updateContentSubtitle("Load or manage your saved workspaces");
    }

    @FXML
    private void handleClearLabKeyCache() {
        com.cryptoforge.crypto.hsm.SimulatedHsmProvider.getInstance().clear();
        showInfo("Success", "Lab Key Cache cleared");
        // refresh combos
        if (cipherController != null) cipherController.refreshHsmKeys();
        if (authenticationController != null) authenticationController.refreshHsmKeys();
    }

    @FXML
    private void handleSaveSymKeyToHsm() {
        if (cipherController != null) {
            cipherController.saveCurrentKeyToHsm();
            authenticationController.refreshHsmKeys();
        }
    }

    @FXML
    private void handleSaveMacKeyToHsm() {
        if (authenticationController != null) {
            authenticationController.saveCurrentKeyToHsm();
            if (cipherController != null) cipherController.refreshHsmKeys();
        }
    }

    @FXML
    public void handleSaveSession() {
        // Init manager
        if (savedSessionsManager == null) {
            savedSessionsManager = com.cryptoforge.model.SavedSessionsManager.getInstance();
        }

        // Ask for name
        TextInputDialog dialog = new TextInputDialog("My Session");
        dialog.setTitle("Save Session");
        dialog.setHeaderText("Save current workspace state");
        dialog.setContentText("Session Name:");

        // Style the dialog roughly to match dark theme (optional/basic)
        dialog.getDialogPane().setStyle("-fx-background-color: #2d3748;");
        dialog.getDialogPane().lookup(".content.label").setStyle("-fx-text-fill: white;");

        java.util.Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            if (name.trim().isEmpty())
                return;

            // Capture State
            java.util.Map<String, Object> state = captureUIState();

            String currentOperation = this.currentActiveOperation;

            // Fallback: If "Dashboard" (default), try to read from UI label
            if ("Dashboard".equals(currentOperation) || currentOperation == null) {
                if (contentSubtitleLabel != null && contentSubtitleLabel.getText() != null) {
                    currentOperation = contentSubtitleLabel.getText();
                }
            }

            // Final fallback
            if (currentOperation == null || currentOperation.isEmpty()) {
                currentOperation = "Generic";
            }

            com.cryptoforge.model.SavedSession session = new com.cryptoforge.model.SavedSession(name, currentOperation,
                    state);
            savedSessionsManager.addSession(session);

            updateStatus("Session saved: " + name);

            // If we are currently viewing Saved Sessions, refresh i
            if (savedSessionsContainer != null && savedSessionsContainer.isVisible()) {
                refreshSavedSessionsUI();
            }
        });
    }

    @FXML
    private void handleNewJWKS() {
        if (jwksArea != null) {
            jwksArea.setText("{\n  \"keys\": []\n}");
        }
    }

    @FXML
    private void handleLoadJWKS() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Load JWKS File");
        fileChooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("JSON Files", "*.json"));
        java.io.File file = fileChooser.showOpenDialog(mainPane.getScene().getWindow());
        if (file != null) {
            try {
                String content = java.nio.file.Files.readString(file.toPath());
                jwksArea.setText(content);
                updateStatus("JWKS loaded.");
                // History
                addToHistory("Load JWKS File", new java.util.HashMap<>());
            } catch (Exception e) {
                showError("Load Error", e.getMessage());
            }
        }
    }

    @FXML
    private void handleRotateKey() {
        if (joseController == null)
            return;
        try {
            String alg = jwksRotateAlgoCombo.getValue();
            if (alg == null) {
                showError("Rotate Error", "Select an algorithm first.");
                return;
            }

            // Security Warning for Symmetric Keys in JWKS
            if (alg.startsWith("HS") || alg.startsWith("A") || alg.equals("dir")) {
                Alert warning = new Alert(Alert.AlertType.WARNING);
                warning.setTitle("Security Warning");
                warning.setHeaderText("Symmetric Key in Public JWKS");
                warning.setContentText("You are adding a SYMMETRIC key (Secret) to this JWK Set.\n\n" +
                        "If you publish this JWKS file publicly (e.g. at .well-known/jwks.json), ANYONE will be able to read your secret key and forge tokens.\n\n"
                        +
                        "Are you sure you want to proceed?");
                warning.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
                java.util.Optional<ButtonType> result = warning.showAndWait();
                if (result.isEmpty() || result.get() != ButtonType.YES) {
                    return;
                }
            }

            com.nimbusds.jose.jwk.JWK newKey = joseController.generateNewJWK(alg, "sig");
            String currentJson = jwksArea.getText();
            if (currentJson == null || currentJson.isBlank())
                currentJson = "{\"keys\":[]}";

            String newJson = joseController.addToJWKSet(currentJson, newKey);
            jwksArea.setText(newJson);
            updateStatus("Added new " + alg + " key to JWKS.");

            // History
            java.util.Map<String, String> details = new java.util.HashMap<>();
            details.put("Algorithm", alg);
            addToHistory("JWKS Rotate Key", details);
        } catch (Exception e) {
            showError("Rotate Key Error", e.getMessage());
        }
    }

    @FXML
    private void handleExportPublicJWKS() {
        if (joseController == null)
            return;
        try {
            String json = jwksArea.getText();
            String publicJson = joseController.exportPublicJWKS(json);

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Public JWKS");
            alert.setHeaderText("Public Keys Only");
            TextArea area = new TextArea(publicJson);
            area.setEditable(false);
            area.setWrapText(true);
            area.setPrefSize(500, 300);
            alert.getDialogPane().setContent(area);
            alert.setResizable(true);
            alert.showAndWait();
        } catch (Exception e) {
            showError("Export Error", e.getMessage());
        }
    }

    @FXML
    private void handleImportKey() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Import Key File");
        java.io.File file = fileChooser.showOpenDialog(mainPane.getScene().getWindow());
        if (file != null) {
            try {
                String content = java.nio.file.Files.readString(file.toPath());
                // Simple heuristic detection
                if (content.contains("{") && content.contains("kty")) {
                    currentActiveOperation = "JWK (Keys)";
                    showJOSE();
                    // Force select JWK Tools tab (index 0)
                    if (jwkSection != null && jwkSection.getChildren().size() > 1) {
                        javafx.scene.Node node = jwkSection.getChildren().get(1);
                        if (node instanceof javafx.scene.control.TabPane) {
                            ((javafx.scene.control.TabPane) node).getSelectionModel().select(0);
                        }
                    }
                    jwkInputArea.setText(content);
                    updateStatus("Imported JSON Key.");
                    // History
                    addToHistory("Import Key (JSON)", new java.util.HashMap<>());
                } else if (content.contains("BEGIN PRIVATE KEY") || content.contains("BEGIN PUBLIC KEY")) {
                    // Decide where to put it. For now, let's put it in the JWK PEM inpu
                    currentActiveOperation = "JWK (Keys)";
                    showJOSE();
                    // Force select JWK Tools tab (index 0)
                    if (jwkSection != null && jwkSection.getChildren().size() > 1) {
                        javafx.scene.Node node = jwkSection.getChildren().get(1);
                        if (node instanceof javafx.scene.control.TabPane) {
                            ((javafx.scene.control.TabPane) node).getSelectionModel().select(0);
                        }
                    }
                    // Assumes RSA/EC by default if we are in JWK tab
                    jwkInputArea.setText(content);
                    updateStatus("Imported PEM Key.");
                    // History
                    addToHistory("Import Key (PEM)", new java.util.HashMap<>());
                } else {
                    showError("Import Error", "Unknown key format.");
                }
            } catch (Exception e) {
                showError("Import Error", e.getMessage());
            }
        }
    }

    @FXML
    private void handleEpochConverter() {
        try {
            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("Epoch Converter");
            javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(10);
            root.setPadding(new javafx.geometry.Insets(20));

            Label l1 = new Label("Unix Timestamp (seconds):");
            TextField tf = new TextField(String.valueOf(java.time.Instant.now().getEpochSecond()));
            Label l2 = new Label("Human Date (UTC):");
            TextField tfDate = new TextField();
            tfDate.setEditable(false);
            Button btn = new Button("Convert");

            btn.setOnAction(e -> {
                try {
                    long ts = Long.parseLong(tf.getText().trim());
                    String res = java.time.Instant.ofEpochSecond(ts).toString();
                    tfDate.setText(res);
                    // History (Manual log since popup)
                    java.util.Map<String, String> details = new java.util.HashMap<>();
                    details.put("Timestamp", tf.getText());
                    details.put("Result", res);
                    addToHistory("Epoch Converter", details);
                } catch (Exception ex) {
                    tfDate.setText("Invalid input");
                }
            });
            btn.fire(); // ini

            root.getChildren().addAll(l1, tf, btn, l2, tfDate);
            javafx.scene.Scene scene = new javafx.scene.Scene(root, 300, 250);
            // Apply current CSS if possible
            if (mainPane.getScene() != null) {
                scene.getStylesheets().addAll(mainPane.getScene().getStylesheets());
            }
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            showError("Tool Error", e.getMessage());
        }
    }

    @FXML
    private void handleJsonFormatter() {
        try {
            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("JSON Formatter");
            javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(10);
            root.setPadding(new javafx.geometry.Insets(10));
            javafx.scene.layout.VBox.setVgrow(root, javafx.scene.layout.Priority.ALWAYS);

            TextArea input = new TextArea();
            input.setPromptText("Paste JSON here...");
            TextArea output = new TextArea();
            output.setEditable(false);

            Button btn = new Button("Format");
            btn.setOnAction(e -> {
                try {
                    com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
                    Object json = gson.fromJson(input.getText(), Object.class);
                    output.setText(gson.toJson(json));
                    // History
                    addToHistory("JSON Formatter", new java.util.HashMap<>());
                } catch (Exception ex) {
                    output.setText("Invalid JSON: " + ex.getMessage());
                }
            });

            root.getChildren().addAll(new Label("Input:"), input, btn, new Label("Output:"), output);
            javafx.scene.Scene scene = new javafx.scene.Scene(root, 600, 400);
            if (mainPane.getScene() != null) {
                scene.getStylesheets().addAll(mainPane.getScene().getStylesheets());
            }
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            showError("Tool Error", e.getMessage());
        }
    }

    @FXML
    private void handleByteInspector() {
        ByteInspectorWindow.show((Stage) mainPane.getScene().getWindow());
    }

    // ============================================================
    // POST-QUANTUM HANDLERS
    // ============================================================

    private void loadPostQuantumContent() {
        if (postQuantumContainerController != null) {
            postQuantumContainerController.initModule(this);
        }
    }

    private void showPostQuantum() {
        hideAllContainers();
        if (postQuantumContainer != null) {
            postQuantumContainer.setManaged(true);
            postQuantumContainer.setVisible(true);
        }
        if (mainScrollPane != null) mainScrollPane.setVvalue(0);
    }

    private void expandPQCAccordionPane(String itemName) {
        if (postQuantumContainerController != null) {
            postQuantumContainerController.expandAccordionPane(itemName);
        }
    }

    // ============================================================
    // XML SECURITY HANDLERS
    // ============================================================

    private void loadXMLSecurityContent() {
        if (xmlSecurityContainerController != null) {
            xmlSecurityContainerController.initModule(this);
        }
    }

    private void showXMLSecurity() {
        hideAllContainers();
        if (xmlSecurityContainer != null) {
            xmlSecurityContainer.setManaged(true);
            xmlSecurityContainer.setVisible(true);
        }
        if (mainScrollPane != null) mainScrollPane.setVvalue(0);
    }

    private void expandXMLAccordionPane(String itemName) {
        if (xmlSecurityContainerController != null) {
            xmlSecurityContainerController.expandAccordionPane(itemName);
        }
    }



    private void initJWATable() {
        if (jwaTable == null)
            return;

        jwaNameCol.setCellValueFactory(
                cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getName()));
        jwaTypeCol.setCellValueFactory(
                cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getType()));
        jwaDescCol.setCellValueFactory(
                cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getDesc()));

        javafx.collections.ObservableList<SimpleAlgo> data = javafx.collections.FXCollections.observableArrayList(
                new SimpleAlgo("HS256", "Signature", "HMAC using SHA-256"),
                new SimpleAlgo("HS384", "Signature", "HMAC using SHA-384"),
                new SimpleAlgo("HS512", "Signature", "HMAC using SHA-512"),
                new SimpleAlgo("RS256", "Signature", "RSASSA-PKCS1-v1_5 using SHA-256"),
                new SimpleAlgo("RS384", "Signature", "RSASSA-PKCS1-v1_5 using SHA-384"),
                new SimpleAlgo("RS512", "Signature", "RSASSA-PKCS1-v1_5 using SHA-512"),
                new SimpleAlgo("ES256", "Signature", "ECDSA using P-256 and SHA-256"),
                new SimpleAlgo("ES384", "Signature", "ECDSA using P-384 and SHA-384"),
                new SimpleAlgo("ES512", "Signature", "ECDSA using P-521 and SHA-512"),
                new SimpleAlgo("PS256", "Signature", "RSASSA-PSS using SHA-256 and MGF1"),
                new SimpleAlgo("EdDSA", "Signature", "EdDSA using Ed25519 or Ed448"),
                new SimpleAlgo("RSA-OAEP-256", "Encryption", "RSAES OAEP using SHA-256 and MGF1"),
                new SimpleAlgo("A128KW", "Encryption", "AES Key Wrap (128-bit)"),
                new SimpleAlgo("A256KW", "Encryption", "AES Key Wrap (256-bit)"),
                new SimpleAlgo("dir", "Encryption", "Direct use of shared symmetric key"),
                new SimpleAlgo("ECDH-ES", "Encryption", "Elliptic Curve Diffie-Hellman Ephemeral Static"),
                new SimpleAlgo("A128GCM", "Encryption", "AES GCM (128-bit) content encryption"),
                new SimpleAlgo("A256GCM", "Encryption", "AES GCM (256-bit) content encryption"),
                new SimpleAlgo("A128CBC-HS256", "Encryption", "AES CBC (128-bit) + HMAC SHA-256"),
                new SimpleAlgo("A256CBC-HS512", "Encryption", "AES CBC (256-bit) + HMAC SHA-512"));
        jwaTable.setItems(data);
    }

    public static class SimpleAlgo {
        private final String name;
        private final String type;
        private final String desc;

        public SimpleAlgo(String n, String t, String d) {
            this.name = n;
            this.type = t;
            this.desc = d;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public String getDesc() {
            return desc;
        }
    }

    private void setupLaboratoryMenu() {
        if (mainMenuBar == null) return;
        boolean hasLabMenu = mainMenuBar.getMenus().stream().anyMatch(m -> "Laboratory".equals(m.getText()));
        if (!hasLabMenu) {
            javafx.scene.control.Menu labMenu = new javafx.scene.control.Menu("Laboratory");
            labMenu.setStyle("-fx-text-fill: white;");
            for (com.cryptoforge.model.payments.PaymentProfile p : com.cryptoforge.model.payments.PaymentProfileManager.getAllProfiles()) {
                // Si el perfil no tiene aún pantalla funcional, no incluirlo en Laboratory hasta que la tenga.
                // Currently only TR31, EMV, DUKPT_TDES, DUKPT_AES, PIN and SECURE_MESSAGING have UI or are going to have UI via EMV/Payments/Keys controllers.
                // We will add all but let's make sure loadProfile handles them.

                javafx.scene.control.Menu profileMenu = new javafx.scene.control.Menu(p.getType().name() + " - " + p.getName());

                javafx.scene.control.MenuItem loadItem = new javafx.scene.control.MenuItem("Load Data");
                loadItem.setOnAction(e -> {
                    // Modern UI navigation to the relevant section
                    if (p.getType() == com.cryptoforge.model.payments.PaymentProfile.ProfileType.TR31) {
                        handleItemSelected("Symmetric Keys");
                        if (keysController != null) keysController.loadProfile(p);
                    } else if (p.getType() == com.cryptoforge.model.payments.PaymentProfile.ProfileType.EMV) {
                        handleItemSelected("EMV Tool");
                        if (emvController != null) emvController.loadProfile(p);
                    } else {
                        handleItemSelected("Payments");
                        if (paymentsController != null) paymentsController.loadProfile(p);
                    }
                    System.out.println("Loaded profile: " + p.getName());
                });

                javafx.scene.control.MenuItem verifyItem = new javafx.scene.control.MenuItem("Run and Verify");
                verifyItem.setOnAction(e -> {
                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
                    alert.setTitle("Laboratory Verification");
                    alert.setHeaderText(p.getName());

                    com.cryptoforge.crypto.VerificationResult result = com.cryptoforge.crypto.PaymentProfileVerifier.verify(p);

                    StringBuilder content = new StringBuilder();
                    content.append(result.getMessage()).append("\n\n");
                    content.append("--- Profile Details ---\n");
                    content.append("Parameters: ").append(p.getParameters()).append("\n");
                    content.append("Inputs: ").append(p.getInputs()).append("\n");
                    content.append("Expected Outputs: ").append(p.getOutputs()).append("\n");

                    if (result.isSuccess()) {
                        alert.setAlertType(javafx.scene.control.Alert.AlertType.INFORMATION);
                    } else {
                        alert.setAlertType(javafx.scene.control.Alert.AlertType.ERROR);
                    }
                    alert.setContentText(content.toString());

                    if (!System.getProperty("java.awt.headless", "false").equals("true") && !Boolean.getBoolean("test.mode")) {
                        alert.showAndWait();
                    } else {
                        // In test mode or headless mode, print to console to avoid blocking UI tests
                        System.out.println("TEST MODE: Alert suppressed. Result: " + result.isSuccess() + ", Message: " + result.getMessage());
                    }
                });

                profileMenu.getItems().addAll(loadItem, verifyItem);
                labMenu.getItems().add(profileMenu);
            }
            mainMenuBar.getMenus().add(labMenu);
        }
    }
}
