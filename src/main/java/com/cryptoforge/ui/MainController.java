package com.cryptoforge.ui;

import com.cryptoforge.util.DataConverter;
import javafx.fxml.FXML;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.geometry.Insets;

/**
 * Controlador principal de la interfaz gráfica
 */
public class MainController implements StatusReporter {

    private final PauseTransition statusResetTimer = new PauseTransition(Duration.seconds(3));

    @Override
    public void updateInspector(String operation, byte[] input, byte[] output, java.util.List<com.cryptoforge.model.OperationDetail> details) {
        // Implementación legada (simulada o redirigida si es necesario)
    }

    @Override
    public void addToHistory(String operation, java.util.List<com.cryptoforge.model.OperationDetail> details) {
        // Implementación legada
    }

    @FXML
    private BorderPane mainPane;

    @FXML
    private TabPane mainTabPane;

    @FXML
    private TextArea inputArea;

    @FXML
    private TextArea outputArea;

    @FXML
    private ComboBox<String> inputFormatCombo;

    @FXML
    private ComboBox<String> outputFormatCombo;

    @FXML
    private Label statusLabel;

    // Generic tab controller
    private GenericController genericController;

    // Cipher tab controller
    private CipherController cipherController;

    // Keys tab controller
    private KeysController keysController;

    // Payments tab controller
    private PaymentsController paymentsController;

    // EMV tab controller
    private EMVController emvController;

    // Authentication tab controller
    private SignatureController signatureController;
    private MACController authMacController;
    private ASN1Controller asn1Controller;
    private PinController pinController;

    // Keys tab FXML components
    @FXML
    private ComboBox<String> keyTypeCombo;
    @FXML
    private javafx.scene.control.CheckBox forceOddParityCheck;
    @FXML
    private TextArea generatedKeyField;
    @FXML
    private TextField keyInputField;
    @FXML
    private TextArea validationResultArea;
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

    // Key Derivation
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

    // Advanced Keys - RSA Generation
    @FXML
    private ComboBox<Integer> rsaKeySizeCombo;
    @FXML
    private TextArea rsaPublicKeyArea;
    @FXML
    private TextArea rsaPrivateKeyArea;

    // Advanced Keys - DSA Generation
    @FXML
    private ComboBox<String> dsaKeySizeCombo;
    @FXML
    private TextArea dsaPublicKeyArea;
    @FXML
    private TextArea dsaPrivateKeyArea;

    // Advanced Keys - ECDSA F(p)
    @FXML
    private ComboBox<String> ecdsaFpCurveCombo;
    @FXML
    private TextArea ecdsaFpPublicKeyArea;
    @FXML
    private TextArea ecdsaFpPrivateKeyArea;

    // Ed25519 key generation
    @FXML
    private TextArea ed25519PublicKeyArea;
    @FXML
    private TextArea ed25519PrivateKeyArea;

    // Certificate Generator
    @FXML
    private TextField certCNField;
    @FXML
    private TextField certOrgField;
    @FXML
    private TextField certOUField;
    @FXML
    private TextField certLocalityField;
    @FXML
    private TextField certStateField;
    @FXML
    private TextField certCountryField;
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

    // TR-31 Key Block
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
    private TextArea tr31ExportResultArea;
    @FXML
    private TextField tr31KbpkImportField;
    @FXML
    private TextArea tr31KeyBlockField;
    @FXML
    private TextField tr31KeyLengthField;
    @FXML
    private TextArea tr31ImportResultArea;

    // Generic - Modular Arithmetic
    @FXML
    private ComboBox<String> modOperationCombo;
    @FXML
    private TextField modOperandAField;
    @FXML
    private TextField modOperandBField;
    @FXML
    private TextField modModulusField;
    @FXML
    private TextArea modResultArea;

    // Generic - File Converter
    @FXML
    private TextField fileInputPathField;
    @FXML
    private TextField fileOutputPathField;
    @FXML
    private ComboBox<String> fileInputFormatCombo;
    @FXML
    private ComboBox<String> fileOutputFormatCombo;
    @FXML
    private ComboBox<String> fileEncodingCombo;
    @FXML
    private TextArea fileResultArea;

    // Generic - Padding Operations
    @FXML
    private ComboBox<String> paddingStandardCombo;
    @FXML
    private TextField paddingBlockSizeField;

    // Generic - Data Decimalization
    @FXML
    private TextField decimalizationTableField;
    @FXML
    private TextField decimalizationOffsetField;
    @FXML
    private ComboBox<String> decimalizationOutputCombo;

    // Payments tab controls
    @FXML
    private TextField pinField;
    @FXML
    private TextField panFieldEncode;
    @FXML
    private TextField pinBlockField;
    @FXML
    private TextField panFieldDecode;
    @FXML
    private ComboBox<String> pinBlockFormatCombo;
    @FXML
    private ComboBox<String> pinBlockFormatDecodeCombo;
    @FXML
    private TextArea pinBlockResultArea;
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
    private ComboBox<String> cvvTypeCombo;
    @FXML
    private TextArea cvvResultArea;
    @FXML
    private ComboBox<String> macAlgorithmCombo;
    @FXML
    private TextField macKeyField;
    @FXML
    private TextArea macDataField;
    @FXML
    private TextArea macResultArea;

    // EMV tab controls
    @FXML
    private TextField imkField;
    @FXML
    private TextField panFieldSession;
    @FXML
    private TextField panSeqFieldSession;
    @FXML
    private TextField atcField;
    @FXML
    private TextArea sessionKeyResultArea;
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
    private TextField skARPCField;
    @FXML
    private TextField arqcField;
    @FXML
    private TextField arcField;
    @FXML
    private TextField csuField;
    @FXML
    private ComboBox<String> arpcMethodCombo;
    @FXML
    private TextArea arpcResultArea;
    @FXML
    private TextField panTrack2Field;
    @FXML
    private TextField expiryTrack2Field;
    @FXML
    private TextField serviceCodeFieldTrack2; // Note: serviceCodeField already exists for CVV
    @FXML
    private TextField discretionaryDataField;
    @FXML
    private TextField track2InputField;
    @FXML
    private TextArea track2ResultArea;

    // History tab controls
    @FXML
    private ComboBox<String> historyCategoryFilter;
    @FXML
    private TextField historySearchField;
    @FXML
    private javafx.scene.control.TableView<com.cryptoforge.utils.OperationHistory.OperationEntry> historyTable;
    @FXML
    private javafx.scene.control.TableColumn<com.cryptoforge.utils.OperationHistory.OperationEntry, String> timestampColumn;
    @FXML
    private javafx.scene.control.TableColumn<com.cryptoforge.utils.OperationHistory.OperationEntry, String> categoryColumn;
    @FXML
    private javafx.scene.control.TableColumn<com.cryptoforge.utils.OperationHistory.OperationEntry, String> operationColumn;
    @FXML
    private javafx.scene.control.TableColumn<com.cryptoforge.utils.OperationHistory.OperationEntry, String> inputColumn;
    @FXML
    private javafx.scene.control.TableColumn<com.cryptoforge.utils.OperationHistory.OperationEntry, String> outputColumn;
    @FXML
    private Label historyCountLabel;

    @FXML
    private SplitPane genericCenterPane;

    @FXML
    private TabPane keysCenterPane;

    @FXML
    private TabPane paymentsCenterPane;

    @FXML
    private TabPane cmsCenterPane;

    @FXML
    private VBox historyCenterPane;

    @FXML
    private SplitPane asn1CenterPane;

    // CMS tab fields
    @FXML
    private TextArea generateDataArea;
    @FXML
    private TextArea generateCertArea;
    @FXML
    private TextArea generatePrivateKeyArea;
    @FXML
    private ComboBox<String> pkcs7TypeCombo;
    @FXML
    private TextArea associatedDataArea;
    @FXML
    private TextArea generateResultArea;
    @FXML
    private TextArea verifyPkcs7Area;
    @FXML
    private TextArea verifyCertArea;
    @FXML
    private TextArea verifyPrivateKeyArea;
    @FXML
    private TextArea verifyResultArea;

    // CMS tab controller
    private CMSController cmsController;

    @FXML
    public void initialize() {
        setupInputFormats();
        setupOutputFormats();
        setupStatusBar();

        // Fix MenuBar styling
        javafx.application.Platform.runLater(() -> {
            fixMenuBarStyling();
        });

        // Configurar placeholder text
        inputArea.setPromptText("Enter your input data here (Hex, Base64, or Text)...");
        outputArea.setPromptText("Output will appear here...");
        outputArea.setEditable(false);

        // Initialize Generic controller
        initializeGenericController();

        // Initialize Cipher controller
        initializeCipherController();

        // Initialize Keys controller
        initializeKeysController();

        // Initialize Payments controller
        initializePaymentsController();

        // Initialize CMS controller
        initializeCMSController();

        // Initialize EMV controller
        initializeEMVController();

        // Initialize Authentication controller
        initializeSignatureController();

        // Initialize PIN controller
        initializePinController();

        // Initialize History tab
        initializeHistoryTab();

        // Add listener to switch center panel based on selected tab
        mainTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null) {
                String tabName = newTab.getText();

                // Hide all panels first
                genericCenterPane.setVisible(false);
                genericCenterPane.setManaged(false);
                keysCenterPane.setVisible(false);
                keysCenterPane.setManaged(false);
                paymentsCenterPane.setVisible(false);
                paymentsCenterPane.setManaged(false);
                historyCenterPane.setVisible(false);
                historyCenterPane.setManaged(false);
                asn1CenterPane.setVisible(false);
                asn1CenterPane.setManaged(false);
                cmsCenterPane.setVisible(false);
                cmsCenterPane.setManaged(false);

                // Show appropriate panel
                if (tabName.equals("Keys")) {
                    keysCenterPane.setVisible(true);
                    keysCenterPane.setManaged(true);
                } else if (tabName.equals("Payments")) {
                    paymentsCenterPane.setVisible(true);
                    paymentsCenterPane.setManaged(true);
                } else if (tabName.equals("CMS")) {
                    cmsCenterPane.setVisible(true);
                    cmsCenterPane.setManaged(true);
                } else if (tabName.equals("History")) {
                    historyCenterPane.setVisible(true);
                    historyCenterPane.setManaged(true);
                    refreshHistoryTable(); // Refresh when opened
                } else if (tabName.equals("ASN.1")) {
                    asn1CenterPane.setVisible(true);
                    asn1CenterPane.setManaged(true);
                } else {
                    // Generic, Cipher, Authentication, EMV use generic center
                    genericCenterPane.setVisible(true);
                    genericCenterPane.setManaged(true);
                }
            }
        });
    }

    /**
     * Initialize Generic tab controller
     */
    private void initializeGenericController() {
        genericController = new GenericController(
                this,
                inputArea,
                outputArea,
                inputFormatCombo,
                outputFormatCombo);

        // Set ComboBox references
        if (hashAlgorithmCombo != null) {
            genericController.setHashAlgorithmCombo(hashAlgorithmCombo);
        }
        if (checkDigitAlgorithmCombo != null) {
            genericController.setCheckDigitAlgorithmCombo(checkDigitAlgorithmCombo);
        }
        if (randomBytesField != null && randomFormatCombo != null) {
            genericController.setRandomGeneratorFields(randomBytesField, randomFormatCombo);
        }

        // Initialize Modular Arithmetic
        if (modOperationCombo != null && modOperandAField != null &&
                modOperandBField != null && modModulusField != null && modResultArea != null) {
            genericController.initializeModularArithmetic(
                    modOperationCombo, modOperandAField, modOperandBField,
                    modModulusField, modResultArea);
        }

        // Initialize File Converter
        if (fileInputPathField != null && fileOutputPathField != null &&
                fileInputFormatCombo != null && fileOutputFormatCombo != null &&
                fileEncodingCombo != null && fileResultArea != null) {
            genericController.initializeFileConverter(
                    fileInputPathField, fileOutputPathField,
                    fileInputFormatCombo, fileOutputFormatCombo,
                    fileEncodingCombo, fileResultArea);
        }

        // Initialize Padding Operations
        if (paddingStandardCombo != null && paddingBlockSizeField != null) {
            paddingStandardCombo.getItems().addAll(
                    "PKCS#7",
                    "ISO/IEC 9797-1 Method 1",
                    "ISO/IEC 9797-1 Method 2",
                    "ANSI X9.23");
            paddingStandardCombo.setValue("PKCS#7");
        }

        // Initialize Data Decimalization
        if (decimalizationOutputCombo != null) {
            decimalizationOutputCombo.getItems().addAll(
                    "Numeric (0-9)",
                    "Hexadecimal (0-F)",
                    "Custom Table");
            decimalizationOutputCombo.setValue("Numeric (0-9)");
        }
    }

    /**
     * Initialize Cipher tab controller
     */
    private void initializeCipherController() {
        cipherController = new CipherController(
                this,
                inputArea,
                outputArea,
                inputFormatCombo,
                outputFormatCombo);

        // Set ComboBox and TextField references
        if (symmetricAlgorithmCombo != null) {
            cipherController.setSymmetricAlgorithmCombo(symmetricAlgorithmCombo);
        }
        if (cipherModeCombo != null) {
            cipherController.setCipherModeCombo(cipherModeCombo);
        }
        if (paddingCombo != null) {
            cipherController.setPaddingCombo(paddingCombo);
        }
        if (symmetricKeyField != null) {
            cipherController.setSymmetricKeyField(symmetricKeyField);
        }
        if (ivField != null) {
            cipherController.setIVField(ivField);
        }
        if (rsaPaddingCombo != null) {
            // RSA usa los mismos combos de formato del toolbar (consistencia)
            cipherController.setRSACombos(rsaPaddingCombo, inputFormatCombo, outputFormatCombo);
        }
    }

    /**
     * Initialize Keys tab controller
     */
    private void initializeKeysController() {
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

        // Initialize advanced key operations
        if (rsaKeySizeCombo != null && rsaPublicKeyArea != null && rsaPrivateKeyArea != null) {
            keysController.initializeRSA(rsaKeySizeCombo, rsaPublicKeyArea, rsaPrivateKeyArea);
        }

        if (dsaKeySizeCombo != null && dsaPublicKeyArea != null && dsaPrivateKeyArea != null) {
            keysController.initializeDSA(dsaKeySizeCombo, dsaPublicKeyArea, dsaPrivateKeyArea);
        }

        if (ecdsaFpCurveCombo != null && ecdsaFpPublicKeyArea != null && ecdsaFpPrivateKeyArea != null) {
            keysController.initializeECDSAFp(ecdsaFpCurveCombo, ecdsaFpPublicKeyArea, ecdsaFpPrivateKeyArea);
        }

        if (ed25519PublicKeyArea != null && ed25519PrivateKeyArea != null) {
            keysController.initializeEd25519(ed25519PublicKeyArea, ed25519PrivateKeyArea);
        }

        if (kdfAlgorithmCombo != null && kdfInputField != null) {
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
        }

        if (certCNField != null && certOrgField != null && certOUField != null &&
                certLocalityField != null && certStateField != null && certCountryField != null &&
                certEmailField != null && certValidityField != null && certKeyTypeCombo != null &&
                certSignAlgoCombo != null && certOutputArea != null) {
            keysController.initializeCertificateGen(
                    certCNField, certOrgField, certOUField, certLocalityField,
                    certStateField, certCountryField, certEmailField, certValidityField,
                    certKeyTypeCombo, certSignAlgoCombo, certOutputArea);
        }

        // Initialize TR-31
        if (tr31KbpkExportField != null && tr31KeyToWrapField != null && tr31UsageCombo != null &&
                tr31AlgorithmCombo != null && tr31ModeCombo != null && tr31ExportabilityCombo != null &&
                tr31ExportResultArea != null &&
                tr31KbpkImportField != null && tr31KeyBlockField != null &&
                tr31KeyLengthField != null && tr31ImportResultArea != null) {
            keysController.initializeTR31(
                    tr31KbpkExportField, tr31KeyToWrapField, tr31VersionCombo,
                    tr31UsageCombo, tr31AlgorithmCombo, tr31ModeCombo,
                    tr31ExportabilityCombo, null, tr31ExportResultArea,
                    tr31KbpkImportField, tr31KeyBlockField, tr31KeyLengthField,
                    tr31ImportResultArea);
        }
    }

    private void initializePaymentsController() {
        paymentsController = new PaymentsController();
        paymentsController.initialize(
                this,
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
                null, // atcField
                cvvTypeCombo,
                cvvResultArea,
                macAlgorithmCombo,
                macKeyField,
                macDataField,
                macResultArea,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, // New Offset Config Fields (Start, Length, Pad)
                null, null, null, null, null, // VISA PVV
                null, null, null, null, null); // Derive PIN
    }

    private void initializeCMSController() {
        cmsController = new CMSController();
        cmsController.initialize(
                this,
                generateDataArea,
                generateCertArea,
                generatePrivateKeyArea,
                pkcs7TypeCombo,
                associatedDataArea,
                generateResultArea,
                verifyPkcs7Area,
                verifyCertArea,
                verifyPrivateKeyArea,
                verifyResultArea);
    }

    private void initializeEMVController() {
        emvController = new EMVController();
        emvController.initialize(
                this,
                // Session Key fields
                imkField,
                panFieldSession,
                panSeqFieldSession,
                atcField,
                sessionKeyResultArea,
                // ARQC fields
                skARQCField,
                amountField,
                currencyField,
                countryField,
                atcARQCField,
                tvrField,
                txDateField,
                txTypeField,
                unField,
                arqcResultArea,
                null, null, null, null, // New args (Raw/AmtOth/ICC/Padding)
                // ARPC fields
                skARPCField,
                arqcField,
                arcField,
                csuField,
                null, // Prop Auth Data
                arpcMethodCombo,
                arpcResultArea,
                // Track 2 fields
                panTrack2Field,
                expiryTrack2Field,
                serviceCodeFieldTrack2,
                discretionaryDataField,
                track2InputField,
                track2ResultArea);
    }

    /**
     * Initialize Authentication (Signature) controller
     */
    private void initializeSignatureController() {
        signatureController = new SignatureController(this);

        if (signatureAlgorithmCombo != null) {
            signatureController.initialize(
                    signatureAlgorithmCombo,
                    inputFormatCombo, // Usa toolbar
                    outputFormatCombo, // Usa toolbar
                    inputArea,
                    outputArea,
                    signatureKeyStatusLabel,
                    signatureVerifyField // Campo para firma en verificación
            );
        }

        // Initialize Authentication MAC controller
        authMacController = new MACController(this);

        if (authMacAlgorithmCombo != null) {
            authMacController.initialize(
                    authMacAlgorithmCombo,
                    inputFormatCombo, // Usa toolbar
                    outputFormatCombo, // Usa toolbar
                    inputArea,
                    outputArea,
                    authMacKeyField,
                    authMacKeyInfoLabel,
                    authMacKeyK,
                    authMacKeyKPrime,
                    authMacTruncationCombo,
                    authMacVerifyField);
        }

        // Initialize ASN.1 Parser controller
        asn1Controller = new ASN1Controller(this);

        if (asn1InputFormatCombo != null) {
            asn1Controller.initialize(
                    asn1InputFormatCombo,
                    asn1TypeCombo,
                    asn1InputArea,
                    asn1TreeArea,
                    asn1DetailsArea,
                    asn1StatusLabel);
        }
    }

    /**
     * Initialize PIN controller
     */
    private void initializePinController() {
        pinController = new PinController();

        if (iso0PinField != null) {
            pinController.initialize(
                    this,
                    // ISO 0 Encode
                    iso0PinField,
                    iso0PanFieldEncode,
                    iso0PinBlockKeyField,
                    iso0ResultArea,
                    // ISO 0 Decode
                    iso0PinBlockFieldDecode,
                    iso0PanFieldDecode,
                    iso0PinBlockKeyFieldDecode,
                    iso0DecodeResultArea,
                    // ISO 2 Encode
                    iso2PinField,
                    iso2PinBlockKeyField,
                    iso2ResultArea,
                    // ISO 2 Decode
                    iso2PinBlockFieldDecode,
                    iso2PinBlockKeyFieldDecode,
                    iso2DecodeResultArea,
                    // ISO 3 Encode
                    iso3PinField,
                    iso3PanFieldEncode,
                    iso3PinBlockKeyField,
                    iso3ResultArea,
                    // ISO 3 Decode
                    iso3PinBlockFieldDecode,
                    iso3PanFieldDecode,
                    iso3PinBlockKeyFieldDecode,
                    iso3DecodeResultArea,
                    // ISO 4 Encode
                    iso4PinField,
                    iso4PanFieldEncode,
                    iso4PinBlockKeyField,
                    iso4ResultArea,
                    // ISO 4 Decode
                    iso4PinBlockFieldDecode,
                    iso4PanFieldDecode,
                    iso4PinBlockKeyFieldDecode,
                    iso4DecodeResultArea,
                    // IBM 3624 Generate PIN
                    ibm3624PvkField,
                    ibm3624ConvTableField,
                    ibm3624OffsetField,
                    ibm3624PanField,
                    ibm3624PanOffsetField,
                    ibm3624PanLengthField,
                    ibm3624PanPadField,
                    ibm3624ResultArea,
                    // IBM 3624 Generate Offset
                    ibm3624PvkFieldOffset,
                    ibm3624ConvTableFieldOffset,
                    ibm3624PinFieldOffset,
                    ibm3624PanFieldOffset,
                    ibm3624PanOffsetFieldOffset,
                    ibm3624PanLengthFieldOffset,
                    ibm3624PanPadFieldOffset,
                    ibm3624OffsetResultArea,
                    // VISA PVV
                    visaPvvPvkField,
                    visaPvvPvkiField,
                    visaPvvPinField,
                    visaPvvPanField,
                    visaPvvResultArea);
        }
    }

    private void initializeHistoryTab() {
        // Setup category filter
        historyCategoryFilter.getItems().addAll(
                "All", "Generic", "Cipher", "Keys", "PIN Block", "CVV", "MAC");
        historyCategoryFilter.getSelectionModel().selectFirst();

        // Setup table columns
        timestampColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(
                cellData.getValue().getTimestamp().format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));

        categoryColumn.setCellValueFactory(
                cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getCategory()));

        operationColumn.setCellValueFactory(
                cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getOperation()));

        inputColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(
                truncateForTable(cellData.getValue().getInput(), 50)));

        outputColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(
                truncateForTable(cellData.getValue().getOutput(), 40)));

        // Load initial data
        refreshHistoryTable();
    }

    private String truncateForTable(String str, int maxLength) {
        if (str == null)
            return "N/A";
        if (str.length() <= maxLength)
            return str;
        return str.substring(0, maxLength) + "...";
    }

    private void refreshHistoryTable() {
        java.util.List<com.cryptoforge.utils.OperationHistory.OperationEntry> entries = com.cryptoforge.utils.OperationHistory
                .getInstance().getHistory();

        historyTable.getItems().clear();
        historyTable.getItems().addAll(entries);
        historyCountLabel.setText("Total operations: " + entries.size());
    }

    private void setupInputFormats() {
        inputFormatCombo.getItems().addAll(
                "Hexadecimal",
                "Base64",
                "Text (UTF-8)",
                "Binary");
        inputFormatCombo.setValue("Hexadecimal");
    }

    private void setupOutputFormats() {
        outputFormatCombo.getItems().addAll(
                "Hexadecimal",
                "Base64",
                "Text (UTF-8)",
                "Binary",
                "C Array");
        outputFormatCombo.setValue("Hexadecimal");
    }

    private void setupStatusBar() {
        updateStatus("Ready");
    }

    @Override
    public void updateStatus(String message) {
        if (!javafx.application.Platform.isFxApplicationThread()) {
            javafx.application.Platform.runLater(() -> updateStatus(message));
            return;
        }
        if (statusLabel == null) return;
        statusLabel.setText(message);
        statusResetTimer.stop();
        statusResetTimer.setOnFinished(event -> statusLabel.setText("Ready"));
        statusResetTimer.playFromStart();
    }

    @Override
    public void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @Override
    public void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Get Generic controller for external access
     */
    public GenericController getGenericController() {
        return genericController;
    }

    @FXML
    private void handleClearInput() {
        inputArea.clear();
        updateStatus("Input cleared");
    }

    @FXML
    private void handleClearOutput() {
        outputArea.clear();
        updateStatus("Output cleared");
    }

    @FXML
    private void handleCopyOutput() {
        String output = outputArea.getText();
        if (output != null && !output.isEmpty()) {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(output);
            clipboard.setContent(content);
            updateStatus("Output copied to clipboard");
        }
    }

    // ===== GENERIC TAB HANDLERS =====

    @FXML
    private ComboBox<String> hashAlgorithmCombo;

    @FXML
    private ComboBox<String> checkDigitAlgorithmCombo;

    @FXML
    private TextField randomBytesField;

    @FXML
    private ComboBox<String> randomFormatCombo;

    // ===== CIPHER TAB FIELDS =====

    @FXML
    private ComboBox<String> symmetricAlgorithmCombo;

    @FXML
    private ComboBox<String> cipherModeCombo;

    @FXML
    private ComboBox<String> paddingCombo;

    @FXML
    private TextField symmetricKeyField;

    @FXML
    private TextField ivField;

    @FXML
    private ComboBox<String> rsaPaddingCombo;
    @FXML
    private javafx.scene.control.Label asymmetricKeyStatusLabel;

    // Authentication tab components
    @FXML
    private ComboBox<String> signatureAlgorithmCombo;
    @FXML
    private javafx.scene.control.Label signatureKeyStatusLabel;
    @FXML
    private TextField signatureVerifyField;

    // MAC components (Authentication tab)
    @FXML
    private ComboBox<String> authMacAlgorithmCombo;
    @FXML
    private TextField authMacKeyField;
    @FXML
    private javafx.scene.control.Label authMacKeyInfoLabel;
    @FXML
    private TextField authMacKeyK;
    @FXML
    private TextField authMacKeyKPrime;
    @FXML
    private ComboBox<String> authMacTruncationCombo;
    @FXML
    private TextField authMacVerifyField;

    // ASN.1 Parser components
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
    private javafx.scene.control.Label asn1StatusLabel;

    // PIN Operations components
    // ISO 0
    @FXML
    private TextField iso0PinField;
    @FXML
    private TextField iso0PanFieldEncode;
    @FXML
    private TextField iso0PinBlockKeyField;
    @FXML
    private TextArea iso0ResultArea;
    @FXML
    private TextField iso0PinBlockFieldDecode;
    @FXML
    private TextField iso0PanFieldDecode;
    @FXML
    private TextField iso0PinBlockKeyFieldDecode;
    @FXML
    private TextArea iso0DecodeResultArea;

    // ISO 2
    @FXML
    private TextField iso2PinField;
    @FXML
    private TextField iso2PinBlockKeyField;
    @FXML
    private TextArea iso2ResultArea;
    @FXML
    private TextField iso2PinBlockFieldDecode;
    @FXML
    private TextField iso2PinBlockKeyFieldDecode;
    @FXML
    private TextArea iso2DecodeResultArea;

    // ISO 3
    @FXML
    private TextField iso3PinField;
    @FXML
    private TextField iso3PanFieldEncode;
    @FXML
    private TextField iso3PinBlockKeyField;
    @FXML
    private TextArea iso3ResultArea;
    @FXML
    private TextField iso3PinBlockFieldDecode;
    @FXML
    private TextField iso3PanFieldDecode;
    @FXML
    private TextField iso3PinBlockKeyFieldDecode;
    @FXML
    private TextArea iso3DecodeResultArea;

    // ISO 4
    @FXML
    private TextField iso4PinField;
    @FXML
    private TextField iso4PanFieldEncode;
    @FXML
    private TextField iso4PinBlockKeyField;
    @FXML
    private TextArea iso4ResultArea;
    @FXML
    private TextField iso4PinBlockFieldDecode;
    @FXML
    private TextField iso4PanFieldDecode;
    @FXML
    private TextField iso4PinBlockKeyFieldDecode;
    @FXML
    private TextArea iso4DecodeResultArea;

    // IBM 3624
    @FXML
    private TextField ibm3624PvkField;
    @FXML
    private TextField ibm3624ConvTableField;
    @FXML
    private TextField ibm3624OffsetField;
    @FXML
    private TextField ibm3624PanField;
    @FXML
    private TextField ibm3624PanOffsetField;
    @FXML
    private TextField ibm3624PanLengthField;
    @FXML
    private TextField ibm3624PanPadField;
    @FXML
    private TextArea ibm3624ResultArea;

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

    // VISA PVV
    @FXML
    private TextField visaPvvPvkField;
    @FXML
    private TextField visaPvvPvkiField;
    @FXML
    private TextField visaPvvPinField;
    @FXML
    private TextField visaPvvPanField;
    @FXML
    private TextArea visaPvvResultArea;

    @FXML
    private void handleCalculateHash() {
        if (genericController != null) {
            genericController.handleCalculateHash();
        }
    }

    @FXML
    private void handleConvert() {
        if (genericController != null) {
            genericController.handleConvert();
        }
    }

    @FXML
    private void handleHexToBase64() {
        // Legacy - redirects to convert
        if (genericController != null) {
            genericController.handleConvert();
        }
    }

    @FXML
    private void handleHexToText() {
        // Legacy - redirects to convert
        if (genericController != null) {
            genericController.handleConvert();
        }
    }

    @FXML
    private void handleGenerateUUID() {
        if (genericController != null) {
            genericController.handleGenerateUUID();
        }
    }

    @FXML
    private void handleGenerateRandom() {
        if (genericController != null) {
            genericController.handleGenerateRandom();
        }
    }

    @FXML
    private void handleCalculateCheckDigit() {
        if (genericController != null) {
            genericController.handleCalculateCheckDigit();
        }
    }

    @FXML
    private void handleValidateCheckDigit() {
        if (genericController != null) {
            genericController.handleValidateCheckDigit();
        }
    }

    // ===== CIPHER TAB HANDLERS =====

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
            cipherController.handleAsymmetricEncrypt();
        }
    }

    @FXML
    private void handleAsymmetricDecrypt() {
        if (cipherController != null) {
            cipherController.handleAsymmetricDecrypt();
        }
    }

    @FXML
    private void handleLoadPublicKey() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Load Public Key (PEM)");
        fileChooser.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("PEM Files", "*.pem", "*.pub", "*.key"));
        java.io.File file = fileChooser.showOpenDialog(null);
        if (file != null && cipherController != null) {
            cipherController.handleLoadPublicKey(file.getAbsolutePath(), asymmetricKeyStatusLabel);
        }
    }

    @FXML
    private void handleLoadPrivateKey() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Load Private Key (PEM)");
        fileChooser.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("PEM Files", "*.pem", "*.key"));
        java.io.File file = fileChooser.showOpenDialog(null);
        if (file != null && cipherController != null) {
            cipherController.handleLoadPrivateKey(file.getAbsolutePath(), asymmetricKeyStatusLabel);
        }
    }

    // ===== AUTHENTICATION TAB HANDLERS =====

    @FXML
    private void handleSign() {
        if (signatureController != null) {
            signatureController.handleSign();
        }
    }

    @FXML
    private void handleVerify() {
        if (signatureController != null) {
            signatureController.handleVerify();
        }
    }

    @FXML
    private void handleLoadSignPrivateKey() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Load Private Key for Signing (PEM)");
        fileChooser.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("PEM Files", "*.pem", "*.key"));
        java.io.File file = fileChooser.showOpenDialog(null);
        if (file != null && signatureController != null) {
            signatureController.handleLoadPrivateKey(file.getAbsolutePath());
        }
    }

    @FXML
    private void handleLoadSignPublicKey() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Load Public Key for Verification (PEM)");
        fileChooser.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("PEM Files", "*.pem", "*.pub", "*.key"));
        java.io.File file = fileChooser.showOpenDialog(null);
        if (file != null && signatureController != null) {
            signatureController.handleLoadPublicKey(file.getAbsolutePath());
        }
    }

    // ===== MAC HANDLERS =====

    @FXML
    private void handleGenerateMAC() {
        if (authMacController != null) {
            authMacController.handleGenerateMAC();
        }
    }

    @FXML
    private void handleVerifyMAC() {
        if (authMacController != null) {
            authMacController.handleVerifyMAC();
        }
    }

    // ===== KEYS TAB HANDLERS =====

    @FXML
    private void handleGenerateKey() {
        if (keysController != null) {
            keysController.handleGenerateKey();
        }
    }

    @FXML
    private void handleValidateKey() {
        if (keysController != null) {
            keysController.handleValidateKey();
        }
    }

    @FXML
    private void handleSplitKey() {
        if (keysController != null) {
            keysController.handleSplitKey();
        }
    }

    @FXML
    private void handleCombineComponents() {
        if (keysController != null) {
            keysController.handleCombineComponents();
        }
    }

    @FXML
    private void handleDeriveKey() {
        if (keysController != null) {
            keysController.handleDeriveKey();
        }
    }

    // ==================== ADVANCED KEYS HANDLERS ====================

    @FXML
    private void handleGenerateRSA() {
        if (keysController != null) {
            keysController.handleGenerateRSA();
        }
    }

    @FXML
    private void handleGenerateDSA() {
        if (keysController != null) {
            keysController.handleGenerateDSA();
        }
    }

    @FXML
    private void handleGenerateECDSAFp() {
        if (keysController != null) {
            keysController.handleGenerateECDSAFp();
        }
    }

    @FXML
    private void handleGenerateEd25519() {
        if (keysController != null) {
            keysController.handleGenerateEd25519();
        }
    }

    @FXML
    private void handleGenerateCertificate() {
        if (keysController != null) {
            keysController.handleGenerateCertificate();
        }
    }

    // ==================== TR-31 HANDLERS ====================

    @FXML
    private void handleTR31Export() {
        if (keysController != null) {
            keysController.handleTR31Export();
        }
    }

    @FXML
    private void handleTR31Import() {
        if (keysController != null) {
            keysController.handleTR31Import();
        }
    }

    @FXML
    private void handleTR31ParseHeader() {
        if (keysController != null) {
            keysController.handleTR31ParseHeader();
        }
    }

    // ==================== GENERIC TAB ADVANCED HANDLERS ====================

    @FXML
    private void handleModularCalculate() {
        if (genericController != null) {
            genericController.handleModularCalculate();
        }
    }

    @FXML
    private void handleFileConvert() {
        if (genericController != null) {
            genericController.handleFileConvert();
        }
    }

    @FXML
    private void handleBrowseInputFile() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Select Input File");
        java.io.File file = fileChooser.showOpenDialog(null);
        if (file != null && fileInputPathField != null) {
            fileInputPathField.setText(file.getAbsolutePath());
        }
    }

    @FXML
    private void handleBrowseOutputFile() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Select Output File");
        java.io.File file = fileChooser.showSaveDialog(null);
        if (file != null && fileOutputPathField != null) {
            fileOutputPathField.setText(file.getAbsolutePath());
        }
    }

    // ==================== PAYMENTS TAB HANDLERS ====================

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
    private void handleGenerateMac() {
        if (paymentsController != null) {
            paymentsController.handleGenerateMac();
        }
    }

    @FXML
    private void handleVerifyMac() {
        if (paymentsController != null) {
            paymentsController.handleVerifyMac();
        }
    }

    // ==================== EMV TAB HANDLERS ====================

    @FXML
    private void handleDeriveSessionKey() {
        if (emvController != null) {
            emvController.handleDeriveSessionKey();
        }
    }

    @FXML
    private void handleGenerateARQC() {
        if (emvController != null) {
            emvController.handleGenerateARQC();
        }
    }

    @FXML
    private void handleVerifyARQC() {
        if (emvController != null) {
            emvController.handleVerifyARQC();
        }
    }

    @FXML
    private void handleGenerateARPC() {
        if (emvController != null) {
            emvController.handleGenerateARPC();
        }
    }

    @FXML
    private void handleEncodeTrack2() {
        if (emvController != null) {
            emvController.handleEncodeTrack2();
        }
    }

    @FXML
    private void handleDecodeTrack2() {
        if (emvController != null) {
            emvController.handleDecodeTrack2();
        }
    }

    // ==================== CMS/PKCS#7 HANDLERS ====================

    @FXML
    private void handleLoadCertGenerate() {
        if (cmsController != null) {
            cmsController.handleLoadCertGenerate();
        }
    }

    @FXML
    private void handleLoadCertVerify() {
        if (cmsController != null) {
            cmsController.handleLoadCertVerify();
        }
    }

    @FXML
    private void handleLoadKeyGenerate() {
        if (cmsController != null) {
            cmsController.handleLoadKeyGenerate();
        }
    }

    @FXML
    private void handleLoadKeyVerify() {
        if (cmsController != null) {
            cmsController.handleLoadKeyVerify();
        }
    }

    @FXML
    private void handleGeneratePKCS7() {
        if (cmsController != null) {
            cmsController.handleGeneratePKCS7();
        }
    }

    @FXML
    private void handleVerifyDecryptPKCS7() {
        if (cmsController != null) {
            cmsController.handleVerifyPKCS7();
        }
    }

    // ==================== HISTORY TAB HANDLERS ====================

    @FXML
    private void handleHistoryFilter() {
        String category = historyCategoryFilter.getSelectionModel().getSelectedItem();
        String searchText = historySearchField.getText();

        java.util.List<com.cryptoforge.utils.OperationHistory.OperationEntry> entries;

        if (searchText != null && !searchText.trim().isEmpty()) {
            // Search mode
            entries = com.cryptoforge.utils.OperationHistory.getInstance().searchHistory(searchText);

            // Filter by category if not "All"
            if (category != null && !category.equals("All")) {
                final String cat = category;
                entries = entries.stream()
                        .filter(e -> e.getCategory().equals(cat))
                        .toList();
            }
        } else if (category != null && !category.equals("All")) {
            // Category filter only
            entries = com.cryptoforge.utils.OperationHistory.getInstance().getHistory(category);
        } else {
            // Show all
            entries = com.cryptoforge.utils.OperationHistory.getInstance().getHistory();
        }

        historyTable.getItems().clear();
        historyTable.getItems().addAll(entries);
        historyCountLabel.setText("Showing: " + entries.size() + " operations");
    }

    @FXML
    private void handleHistoryRefresh() {
        historySearchField.clear();
        historyCategoryFilter.getSelectionModel().selectFirst();
        refreshHistoryTable();
        updateStatus("History refreshed");
    }

    @FXML
    private void handleExportTxt() {
        try {
            String content = com.cryptoforge.utils.OperationHistory.getInstance().exportToText();
            saveToFile(content, "history.txt", "Text Files", "*.txt");
            updateStatus("History exported to TXT");
        } catch (Exception e) {
            updateStatus("Error exporting to TXT: " + e.getMessage());
        }
    }

    @FXML
    private void handleExportCsv() {
        try {
            String content = com.cryptoforge.utils.OperationHistory.getInstance().exportToCSV();
            saveToFile(content, "history.csv", "CSV Files", "*.csv");
            updateStatus("History exported to CSV");
        } catch (Exception e) {
            updateStatus("Error exporting to CSV: " + e.getMessage());
        }
    }

    @FXML
    private void handleExportJson() {
        try {
            String content = com.cryptoforge.utils.OperationHistory.getInstance().exportToJSON();
            saveToFile(content, "history.json", "JSON Files", "*.json");
            updateStatus("History exported to JSON");
        } catch (Exception e) {
            updateStatus("Error exporting to JSON: " + e.getMessage());
        }
    }

    @FXML
    private void handleClearHistory() {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.CONFIRMATION);
        alert.setTitle("Clear History");
        alert.setHeaderText("Clear all operation history?");
        alert.setContentText("This action cannot be undone.");

        alert.showAndWait().ifPresent(response -> {
            if (response == javafx.scene.control.ButtonType.OK) {
                com.cryptoforge.utils.OperationHistory.getInstance().clearHistory();
                refreshHistoryTable();
                updateStatus("History cleared");
            }
        });
    }

    private void saveToFile(String content, String defaultFileName, String description, String extension) {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Save " + description);
        fileChooser.setInitialFileName(defaultFileName);
        fileChooser.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter(description, extension));

        java.io.File file = fileChooser.showSaveDialog(mainPane.getScene().getWindow());
        if (file != null) {
            try {
                java.nio.file.Files.writeString(file.toPath(), content);
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // ==================== VIEW MENU HANDLERS ====================

    private String currentFontSize = "13px";

    @FXML
    private void handleThemeLight() {
        mainPane.setStyle("");
        updateStatus("Theme: Light");
        javafx.application.Platform.runLater(() -> fixMenuBarStyling());
    }

    @FXML
    private void handleThemeDark() {
        mainPane.setStyle(
                "-fx-base: #2b2b2b; " +
                        "-fx-background: #1e1e1e; " +
                        "-fx-control-inner-background: #3c3c3c; " +
                        "-fx-text-fill: #e0e0e0;");
        updateStatus("Theme: Dark");
        javafx.application.Platform.runLater(() -> fixMenuBarStyling());
    }

    @FXML
    private void handleThemeHighContrast() {
        mainPane.setStyle(
                "-fx-base: #000000; " +
                        "-fx-background: #000000; " +
                        "-fx-control-inner-background: #000000; " +
                        "-fx-text-fill: #FFFFFF; " +
                        "-fx-font-weight: bold;");
        updateStatus("Theme: High Contrast");
        javafx.application.Platform.runLater(() -> fixMenuBarStyling());
    }

    @FXML
    private void handleThemeSolarized() {
        mainPane.setStyle(
                "-fx-base: #002b36; " +
                        "-fx-background: #002b36; " +
                        "-fx-control-inner-background: #073642; " +
                        "-fx-text-fill: #839496; " +
                        "-fx-accent: #268bd2;");
        updateStatus("Theme: Solarized Dark");
        javafx.application.Platform.runLater(() -> fixMenuBarStyling());
    }

    @FXML
    private void handleThemeDracula() {
        mainPane.setStyle(
                "-fx-base: #282a36; " +
                        "-fx-background: #282a36; " +
                        "-fx-control-inner-background: #44475a; " +
                        "-fx-text-fill: #f8f8f2; " +
                        "-fx-accent: #bd93f9;");
        updateStatus("Theme: Dracula");
        javafx.application.Platform.runLater(() -> fixMenuBarStyling());
    }

    @FXML
    private void handleFontSmall() {
        currentFontSize = "11px";
        applyFontSize();
        updateStatus("Font size: Small (11px)");
    }

    @FXML
    private void handleFontMedium() {
        currentFontSize = "13px";
        applyFontSize();
        updateStatus("Font size: Medium (13px)");
    }

    @FXML
    private void handleFontLarge() {
        currentFontSize = "15px";
        applyFontSize();
        updateStatus("Font size: Large (15px)");
    }

    @FXML
    private void handleFontExtraLarge() {
        currentFontSize = "18px";
        applyFontSize();
        updateStatus("Font size: Extra Large (18px)");
    }

    private void applyFontSize() {
        // Parse font size to double
        double size = Double.parseDouble(currentFontSize.replace("px", ""));

        // Create font
        javafx.scene.text.Font font = javafx.scene.text.Font.font("Courier New", size);

        // Apply to each TextArea
        applyFontToTextArea(inputArea, font);
        applyFontToTextArea(outputArea, font);
        applyFontToTextArea(validationResultArea, font);
        applyFontToTextArea(componentResultsArea, font);
        applyFontToTextArea(generatedKeyField, font);
        // keyInputField is now TextField, not TextArea
        applyFontToTextArea(keyToSplitField, font);

        // Log to console for debugging
        System.out.println("Font changed to: Courier New " + size + "px");

        // Fix MenuBar styling after font change
        javafx.application.Platform.runLater(() -> fixMenuBarStyling());
    }

    private void applyFontToTextArea(TextArea textArea, javafx.scene.text.Font font) {
        if (textArea != null) {
            textArea.setFont(font);
            System.out.println("Font applied to TextArea: " + font.getName() + " " + font.getSize());
        }
    }

    @FXML
    private void handleResetView() {
        handleThemeLight();
        currentFontSize = "13px";
        applyFontSize();
        updateStatus("View reset to defaults");
    }

    // ==================== END VIEW MENU HANDLERS ====================

    @FXML
    private void handleAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About CryptoCarver");
        alert.setHeaderText("CryptoCarver v1.0.0");
        alert.setContentText(
                "Advanced Cryptographic Tool\n\n" +
                        "A modern evolution of BP-Tools with enhanced features:\n" +
                        "• Multiple cipher algorithms (DES, 3DES, AES, RSA)\n" +
                        "• Advanced padding standards\n" +
                        "• KeyStore management\n" +
                        "• Payment cryptography (PIN blocks, CVV)\n" +
                        "• EMV support\n" +
                        "• Development tools\n\n" +
                        "Cross-platform support: Windows, macOS, Linux\n" +
                        "Built with Java 21 + JavaFX 21");
        alert.showAndWait();
    }

    @FXML
    private void handleExit() {
        System.exit(0);
    }

    /**
     * Fix MenuBar styling programmatically
     */
    private void fixMenuBarStyling() {
        try {
            // Fix MenuBar
            if (mainPane != null && mainPane.getTop() != null) {
                javafx.scene.Node topNode = mainPane.getTop();
                if (topNode instanceof javafx.scene.layout.VBox) {
                    javafx.scene.layout.VBox vbox = (javafx.scene.layout.VBox) topNode;
                    if (!vbox.getChildren().isEmpty()
                            && vbox.getChildren().get(0) instanceof javafx.scene.control.MenuBar) {
                        javafx.scene.control.MenuBar menuBar = (javafx.scene.control.MenuBar) vbox.getChildren().get(0);

                        // Apply style directly to MenuBar
                        menuBar.setStyle("-fx-background-color: #2c3e50;");

                        // Apply style to each Menu
                        for (javafx.scene.control.Menu menu : menuBar.getMenus()) {
                            menu.setStyle("-fx-text-fill: white;");
                        }
                    }
                }
            }

            // Fix ComboBoxes in sidebar (ensure they're visible)
            fixAllComboBoxes();

            // Fix TextAreas and TextFields
            fixAllTextFields();

        } catch (Exception e) {
            System.err.println("Error fixing MenuBar styling: " + e.getMessage());
        }
    }

    /**
     * Fix ALL ComboBox styling in the application
     */
    private void fixAllComboBoxes() {
        // Toolbar ComboBoxes
        fixComboBoxStyling(inputFormatCombo);
        fixComboBoxStyling(outputFormatCombo);

        // Generic tab
        fixComboBoxStyling(hashAlgorithmCombo);
        fixComboBoxStyling(checkDigitAlgorithmCombo);
        fixComboBoxStyling(randomFormatCombo);
        fixComboBoxStyling(modOperationCombo);
        fixComboBoxStyling(fileInputFormatCombo);
        fixComboBoxStyling(fileOutputFormatCombo);
        fixComboBoxStyling(fileEncodingCombo);

        // Cipher tab
        fixComboBoxStyling(symmetricAlgorithmCombo);
        fixComboBoxStyling(cipherModeCombo);
        fixComboBoxStyling(paddingCombo);
        fixComboBoxStyling(rsaPaddingCombo);

        // Authentication tab
        fixComboBoxStyling(signatureAlgorithmCombo);
        fixComboBoxStyling(authMacAlgorithmCombo);
        fixComboBoxStyling(authMacTruncationCombo);

        // Keys tab
        fixComboBoxStyling(keyTypeCombo);
        fixComboBoxStyling(numComponentsCombo);
        fixComboBoxStyling(rsaKeySizeCombo);
        fixComboBoxStyling(dsaKeySizeCombo);
        fixComboBoxStyling(ecdsaFpCurveCombo);
        fixComboBoxStyling(certKeyTypeCombo);
        fixComboBoxStyling(certSignAlgoCombo);

        // Payments tab
        fixComboBoxStyling(pinBlockFormatCombo);
        fixComboBoxStyling(pinBlockFormatDecodeCombo);
        fixComboBoxStyling(cvvTypeCombo);
        fixComboBoxStyling(macAlgorithmCombo);

        // EMV tab
        fixComboBoxStyling(arpcMethodCombo);

        // History tab
        fixComboBoxStyling(historyCategoryFilter);
    }

    /**
     * Fix ALL TextArea and TextField styling in the application
     */
    private void fixAllTextFields() {
        // Main input/output areas
        fixTextAreaStyling(inputArea);
        fixTextAreaStyling(outputArea);

        // Keys tab - Symmetric
        fixTextAreaStyling(generatedKeyField);
        // keyInputField is now TextField, not TextArea
        fixTextAreaStyling(keyToSplitField);
        fixTextAreaStyling(validationResultArea);
        fixTextAreaStyling(componentResultsArea);
        // component1-5Field are now TextFields, no need to fix styling

        // Keys tab - Advanced
        fixTextAreaStyling(rsaPublicKeyArea);
        fixTextAreaStyling(rsaPrivateKeyArea);
        fixTextAreaStyling(dsaPublicKeyArea);
        fixTextAreaStyling(dsaPrivateKeyArea);
        fixTextAreaStyling(ecdsaFpPublicKeyArea);
        fixTextAreaStyling(ecdsaFpPrivateKeyArea);
        fixTextAreaStyling(ed25519PublicKeyArea);
        fixTextAreaStyling(ed25519PrivateKeyArea);
        fixTextAreaStyling(certOutputArea);
        fixTextFieldStyling(certCNField);
        fixTextFieldStyling(certOrgField);
        fixTextFieldStyling(certOUField);
        fixTextFieldStyling(certLocalityField);
        fixTextFieldStyling(certStateField);
        fixTextFieldStyling(certCountryField);
        fixTextFieldStyling(certValidityField);

        // Generic tab - Advanced
        fixTextFieldStyling(modOperandAField);
        fixTextFieldStyling(modOperandBField);
        fixTextFieldStyling(modModulusField);
        fixTextAreaStyling(modResultArea);
        fixTextFieldStyling(fileInputPathField);
        fixTextFieldStyling(fileOutputPathField);
        fixTextAreaStyling(fileResultArea);

        // Cipher tab
        fixTextFieldStyling(symmetricKeyField);
        fixTextFieldStyling(ivField);

        // Payments tab
        fixTextFieldStyling(pinField);
        fixTextFieldStyling(panFieldEncode);
        fixTextFieldStyling(pinBlockField);
        fixTextFieldStyling(panFieldDecode);
        fixTextFieldStyling(cvkAField);
        fixTextFieldStyling(cvkBField);
        fixTextFieldStyling(panFieldCvv);
        fixTextFieldStyling(expiryDateField);
        fixTextFieldStyling(serviceCodeField);
        fixTextFieldStyling(macKeyField);
        fixTextAreaStyling(pinBlockResultArea);
        fixTextAreaStyling(cvvResultArea);
        fixTextAreaStyling(macDataField);
        fixTextAreaStyling(macResultArea);

        // EMV tab
        fixTextFieldStyling(imkField);
        fixTextFieldStyling(panFieldSession);
        fixTextFieldStyling(panSeqFieldSession);
        fixTextFieldStyling(atcField);
        fixTextAreaStyling(sessionKeyResultArea);
        fixTextFieldStyling(skARQCField);
        fixTextFieldStyling(amountField);
        fixTextFieldStyling(currencyField);
        fixTextFieldStyling(countryField);
        fixTextFieldStyling(atcARQCField);
        fixTextFieldStyling(tvrField);
        fixTextFieldStyling(txDateField);
        fixTextFieldStyling(txTypeField);
        fixTextFieldStyling(unField);
        fixTextAreaStyling(arqcResultArea);
        fixTextFieldStyling(skARPCField);
        fixTextFieldStyling(arqcField);
        fixTextFieldStyling(arcField);
        fixTextFieldStyling(csuField);
        fixTextAreaStyling(arpcResultArea);
        fixTextFieldStyling(panTrack2Field);
        fixTextFieldStyling(expiryTrack2Field);
        fixTextFieldStyling(serviceCodeFieldTrack2);
        fixTextFieldStyling(discretionaryDataField);
        fixTextFieldStyling(track2InputField);
        fixTextAreaStyling(track2ResultArea);

        // Generic tab
        fixTextFieldStyling(randomBytesField);

        // History tab
        fixTextFieldStyling(historySearchField);
    }

    /**
     * Fix TextArea styling to ensure visibility
     */
    private void fixTextAreaStyling(javafx.scene.control.TextArea textArea) {
        if (textArea != null) {
            textArea.setStyle(
                    "-fx-control-inner-background: white; " +
                            "-fx-text-fill: black; " +
                            "-fx-prompt-text-fill: #999999; " +
                            "-fx-highlight-fill: #3498db; " +
                            "-fx-highlight-text-fill: white;");
        }
    }

    /**
     * Fix TextField styling to ensure visibility
     */
    private void fixTextFieldStyling(javafx.scene.control.TextField textField) {
        if (textField != null) {
            textField.setStyle(
                    "-fx-control-inner-background: white; " +
                            "-fx-text-fill: black; " +
                            "-fx-prompt-text-fill: #999999; " +
                            "-fx-highlight-fill: #3498db; " +
                            "-fx-highlight-text-fill: white;");
        }
    }

    /**
     * Fix ComboBox styling to ensure visibility in all themes
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void fixComboBoxStyling(javafx.scene.control.ComboBox<?> combo) {
        if (combo != null) {
            // Force button cell to show text (using raw type to avoid generics issues)
            javafx.scene.control.ComboBox rawCombo = combo;
            rawCombo.setButtonCell(new javafx.scene.control.ListCell() {
                @Override
                protected void updateItem(Object item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(combo.getPromptText());
                        setStyle("-fx-text-fill: #999999;");
                    } else {
                        setText(item.toString());
                        setStyle("-fx-text-fill: black;");
                    }
                }
            });

            // Ensure text is visible regardless of theme
            combo.setStyle(
                    "-fx-background-color: white; " +
                            "-fx-text-fill: black; " +
                            "-fx-prompt-text-fill: #999999;");
        }
    }

    // ============ ASN.1 Parser Methods ============

    @FXML
    private void handleParseASN1() {
        if (asn1Controller != null) {
            asn1Controller.handleParseASN1();
        }
    }

    @FXML
    private void handleLoadASN1File() {
        if (asn1Controller != null) {
            asn1Controller.handleLoadFile();
        }
    }

    @FXML
    private void handleLoadASN1Example() {
        if (asn1Controller != null) {
            asn1Controller.handleLoadExample();
        }
    }

    @FXML
    private void handleExportASN1Tree() {
        if (asn1Controller != null) {
            asn1Controller.handleExportTree();
        }
    }

    @FXML
    private void handleClearASN1() {
        if (asn1Controller != null) {
            asn1Controller.handleClear();
        }
    }

    // ==================== PIN HANDLERS ====================

    @FXML
    private void handleEncodeIso0() {
        if (pinController != null) {
            pinController.handleEncodeIso0();
        }
    }

    @FXML
    private void handleDecodeIso0() {
        if (pinController != null) {
            pinController.handleDecodeIso0();
        }
    }

    @FXML
    private void handleEncodeIso2() {
        if (pinController != null) {
            pinController.handleEncodeIso2();
        }
    }

    @FXML
    private void handleDecodeIso2() {
        if (pinController != null) {
            pinController.handleDecodeIso2();
        }
    }

    @FXML
    private void handleEncodeIso3() {
        if (pinController != null) {
            pinController.handleEncodeIso3();
        }
    }

    @FXML
    private void handleDecodeIso3() {
        if (pinController != null) {
            pinController.handleDecodeIso3();
        }
    }

    @FXML
    private void handleEncodeIso4() {
        if (pinController != null) {
            pinController.handleEncodeIso4();
        }
    }

    @FXML
    private void handleDecodeIso4() {
        if (pinController != null) {
            pinController.handleDecodeIso4();
        }
    }

    @FXML
    private void handleGenerateIbm3624Pin() {
        if (pinController != null) {
            pinController.handleGenerateIbm3624Pin();
        }
    }

    @FXML
    private void handleGenerateIbm3624Offset() {
        if (pinController != null) {
            pinController.handleGenerateIbm3624Offset();
        }
    }

    @FXML
    private void handleGenerateVisaPvv() {
        if (pinController != null) {
            pinController.handleGenerateVisaPvv();
        }
    }

    // Padding Operations
    @FXML
    private void handleAddPadding() {
        try {
            String input = inputArea.getText();
            if (input.isEmpty()) {
                outputArea.setText("Error: Input required");
                return;
            }

            String standard = paddingStandardCombo.getValue();
            if (standard == null || standard.isEmpty()) {
                outputArea.setText("Error: Select padding standard");
                return;
            }

            int blockSize = Integer.parseInt(paddingBlockSizeField.getText());

            // Convert input based on input format
            byte[] data = convertInputToBytes(input);

            // Add padding based on standard
            byte[] padded = addPadding(data, standard, blockSize);

            // Output in selected format
            outputArea.setText(convertBytesToOutput(padded));
            updateStatus("Padding added successfully");

        } catch (Exception e) {
            outputArea.setText("Error: " + e.getMessage());
        }
    }

    @FXML
    private void handleRemovePadding() {
        try {
            String input = inputArea.getText();
            if (input.isEmpty()) {
                outputArea.setText("Error: Input required");
                return;
            }

            String standard = paddingStandardCombo.getValue();
            if (standard == null || standard.isEmpty()) {
                outputArea.setText("Error: Select padding standard");
                return;
            }

            // Convert input based on input format
            byte[] data = convertInputToBytes(input);

            // Remove padding based on standard
            byte[] unpadded = removePadding(data, standard);

            // Output in selected format
            outputArea.setText(convertBytesToOutput(unpadded));
            updateStatus("Padding removed successfully");

        } catch (Exception e) {
            outputArea.setText("Error: " + e.getMessage());
        }
    }

    @FXML
    private void handleDecimalize() {
        try {
            String input = inputArea.getText();
            if (input.isEmpty()) {
                outputArea.setText("Error: Input required");
                return;
            }

            String table = decimalizationTableField.getText();
            String offset = decimalizationOffsetField.getText();

            if (table.length() != 16) {
                outputArea.setText("Error: Decimalization table must be 16 characters");
                return;
            }

            if (offset.length() != 16) {
                outputArea.setText("Error: Offset must be 16 characters");
                return;
            }

            // Perform decimalization
            String result = decimalize(input, table, offset);
            outputArea.setText(result);
            updateStatus("Decimalization completed");

        } catch (Exception e) {
            outputArea.setText("Error: " + e.getMessage());
        }
    }

    // Helper methods for padding
    private byte[] convertInputToBytes(String input) throws Exception {
        // Use input format from combo to convert
        String format = inputFormatCombo.getValue();
        if (format == null) {
            format = "Hex";
        }

        switch (format) {
            case "Hex":
                return DataConverter.hexToBytes(input.replaceAll("\\s+", ""));
            case "Base64":
                return java.util.Base64.getDecoder().decode(input.replaceAll("\\s+", ""));
            case "Text (UTF-8)":
                return input.getBytes("UTF-8");
            default:
                return DataConverter.hexToBytes(input.replaceAll("\\s+", ""));
        }
    }

    private String convertBytesToOutput(byte[] data) {
        // Use output format from combo
        String format = outputFormatCombo.getValue();
        if (format == null) {
            format = "Hex";
        }

        switch (format) {
            case "Hex":
                return DataConverter.bytesToHex(data);
            case "Base64":
                return java.util.Base64.getEncoder().encodeToString(data);
            case "Text (UTF-8)":
                try {
                    return new String(data, "UTF-8");
                } catch (Exception e) {
                    return DataConverter.bytesToHex(data) + " (encoding error)";
                }
            default:
                return DataConverter.bytesToHex(data);
        }
    }

    private byte[] addPadding(byte[] data, String standard, int blockSize) throws Exception {
        int padLength = blockSize - (data.length % blockSize);
        if (padLength == blockSize && !standard.equals("ISO/IEC 9797-1 Method 2")) {
            padLength = 0; // No padding needed if already block-aligned (except for Method 2)
        }

        byte[] result = new byte[data.length + padLength];
        System.arraycopy(data, 0, result, 0, data.length);

        switch (standard) {
            case "PKCS#7":
                // Pad with byte value = number of padding bytes
                for (int i = 0; i < padLength; i++) {
                    result[data.length + i] = (byte) padLength;
                }
                break;
            case "ISO/IEC 9797-1 Method 1":
                // Pad with zeros
                break; // Already zeros from array initialization
            case "ISO/IEC 9797-1 Method 2":
                // Pad with 0x80 followed by zeros
                if (padLength > 0 || data.length % blockSize == 0) {
                    result[data.length] = (byte) 0x80;
                }
                break;
            case "ANSI X9.23":
                // Pad with zeros, last byte is padding length
                if (padLength > 0) {
                    result[result.length - 1] = (byte) padLength;
                }
                break;
            default:
                throw new Exception("Unknown padding standard: " + standard);
        }

        return result;
    }

    private byte[] removePadding(byte[] data, String standard) throws Exception {
        if (data.length == 0) {
            return data;
        }

        int padLength;
        switch (standard) {
            case "PKCS#7":
                padLength = data[data.length - 1] & 0xFF;
                // Validate padding
                if (padLength > data.length || padLength == 0) {
                    throw new Exception("Invalid PKCS#7 padding");
                }
                for (int i = 0; i < padLength; i++) {
                    if ((data[data.length - 1 - i] & 0xFF) != padLength) {
                        throw new Exception("Invalid PKCS#7 padding");
                    }
                }
                break;
            case "ISO/IEC 9797-1 Method 2":
                // Find 0x80 byte from the end
                padLength = 0;
                for (int i = data.length - 1; i >= 0; i--) {
                    if ((data[i] & 0xFF) == 0x80) {
                        padLength = data.length - i;
                        break;
                    } else if (data[i] != 0) {
                        throw new Exception("Invalid ISO/IEC 9797-1 Method 2 padding");
                    }
                }
                if (padLength == 0) {
                    throw new Exception("Invalid ISO/IEC 9797-1 Method 2 padding: 0x80 not found");
                }
                break;
            case "ANSI X9.23":
                padLength = data[data.length - 1] & 0xFF;
                if (padLength > data.length || padLength == 0) {
                    throw new Exception("Invalid ANSI X9.23 padding");
                }
                // Validate zeros
                for (int i = 1; i < padLength; i++) {
                    if (data[data.length - 1 - i] != 0) {
                        throw new Exception("Invalid ANSI X9.23 padding");
                    }
                }
                break;
            case "ISO/IEC 9797-1 Method 1":
                // Remove trailing zeros
                padLength = 0;
                for (int i = data.length - 1; i >= 0; i--) {
                    if (data[i] == 0) {
                        padLength++;
                    } else {
                        break;
                    }
                }
                break;
            default:
                throw new Exception("Unknown padding standard: " + standard);
        }

        byte[] result = new byte[data.length - padLength];
        System.arraycopy(data, 0, result, 0, result.length);
        return result;
    }

    private String decimalize(String input, String table, String offset) throws Exception {
        // Convert input to bytes
        byte[] inputBytes = DataConverter.hexToBytes(input.replaceAll("\\s+", ""));

        // Perform decimalization
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < inputBytes.length; i++) {
            int idx = (inputBytes[i] & 0x0F);
            int offsetIdx = Character.digit(offset.charAt(i % offset.length()), 16);
            int finalIdx = (idx + offsetIdx) % 16;
            result.append(table.charAt(finalIdx));
        }

        return result.toString();
    }
}
