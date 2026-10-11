package com.cryptocarver.ui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.nio.file.Files;
import javafx.scene.text.TextFlow;
import com.cryptocarver.model.DiagnosticsReportBuilder;
import com.cryptocarver.model.AppSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// Attempt to use DataConverter if available, otherwise will rely on local helpers or standard libs
import com.cryptocarver.util.DataConverter;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.model.help.HelpContext;
import com.cryptocarver.model.help.HelpContextState;
import com.cryptocarver.service.I18nService;

/**
 * Modern Main Controller for Rail + SidePanel navigation
 */
public class ModernMainController implements StatusReporter, OperationNavigator {

    private static javafx.stage.Window windowOf(Node node) {
        return node == null || node.getScene() == null ? null : node.getScene().getWindow();
    }

    private static final double COMPACT_LAYOUT_WIDTH = 1_100;
    /** Rendered per platform so Windows and Linux do not show macOS glyphs as empty boxes. */
    private static final String COMMAND_PALETTE_SHORTCUT =
            com.cryptocarver.model.PlatformShortcuts.display("Shortcut+K");
    private static final String FAVORITE_SHORTCUT =
            com.cryptocarver.model.PlatformShortcuts.display("Shortcut+Shift+F");

    static void writeDiagnosticsReport(java.nio.file.Path report, String content) throws Exception {
        DiagnosticsReportBuilder.write(report, content);
    }

    @FXML private javafx.scene.control.Label contentPlaceholderLabel;
    @FXML private ModuleHost jose;
    @FXML private ModuleHost cose;
    @FXML private ModuleHost wallet;
    @FXML private GenericController genericContainerController;

    private static final Logger LOG = LoggerFactory.getLogger(ModernMainController.class);
    private final ExpandedTextViewer expandedTextViewer = new ExpandedTextViewer();
    private final ExpandedTextViewer sessionStepViewer = new ExpandedTextViewer();
    private final DialogService dialogService = new DialogService();
    private ShellDialogCoordinator shellDialogCoordinator;
    private final ExpandedTableViewer expandedTableViewer = new ExpandedTableViewer();
    private OperationInspectorPresenter inspectorPresenter;
    private ContextualHelpCoordinator contextualHelpCoordinator;
    private ContextualHelpWindowCoordinator contextualHelpWindowCoordinator;
    private final HelpContextState contextualHelpState = new HelpContextState();
    private HelpContextAdapter cipherHelpContextAdapter;
    private JoseJweHelpContextAdapter jweHelpContextAdapter;
    private HelpContext savedJweHelpContext;
    private final Map<String, HelpContext> savedCipherHelpContexts = new HashMap<>();
    private PinBlockHelpContextAdapter pinBlockHelpContextAdapter;
    private final Map<String, HelpContext> savedPinBlockHelpContexts = new HashMap<>();
    private final ResultAreaTracker resultAreaTracker = new ResultAreaTracker();
    private ResultViewerCoordinator resultViewerCoordinator;
    private ResultPublicationCoordinator resultPublicationCoordinator;
    private ResultCaptureCoordinator resultCaptureCoordinator;
    private CommandPaletteCoordinator commandPaletteCoordinator;
    private LaboratoryMenuCoordinator laboratoryMenuCoordinator;
    private final com.cryptocarver.model.SessionTrailState sessionTrailState = new com.cryptocarver.model.SessionTrailState();
    private SessionTrailCoordinator sessionTrailCoordinator;
    /**
     * Snapshot published by the latest completed operation.  It is deliberately
     * separate from the last focused text area: focus is a navigation concern,
     * while publishing is the authoritative completion event.  Without this a
     * still-visible result area from a previous accordion pane could be shown
     * by Expand Result after a different operation completed.
     */
    private String lastPublishedOperation = "";
    private com.cryptocarver.model.OperationResult lastPublishedResultSnapshot;
    /** Screen that was active when {@link #lastPublishedResultSnapshot} was published. */
    private String lastPublishedScreen;

    @FXML
    private BorderPane mainPane;
    /** Workspace split pane owns divider persistence and responsive collapsing. */
    @FXML
    private ResponsiveWorkspaceSplitPane workspaceSplitPane;
    @FXML
    private ToggleGroup visibilityProfileGroup;

    @FXML
    private NavigationRail navigationRail;
    @FXML
    private SidePanel sidePanel;
    private NavigationController navigationController;
    private NavigationRouter navigationRouter;
    private NavigationChromeCoordinator navigationChrome;
    @FXML
    private VBox mainContentArea;
    @FXML
    private ScrollPane mainScrollPane;
    @FXML private VBox cipherActionDock;
    @FXML
    private VBox contentContainer;
    @FXML private ModuleHost keysContainer;
    @FXML private KeysController keysContainerController;
    @FXML private ModuleHost certificatesContainer;
    @FXML private CertificatesController certificatesContainerController;
    @FXML
    private VBox inspectorPanel;
    @FXML
    private VBox inspectorDetailsContainer;
    private boolean inspectorHiddenForCompactLayout;

    // CIPHER UI
    @FXML private ModuleHost cipherContainer;
    @FXML private CipherController cipherContainerController;

    // Header labels
    @FXML
    private Label contentTitleLabel;
    @FXML
    private Label contentSubtitleLabel;

    // Breadcrumbs & Favorites UI (UX-07)
    @FXML private HBox breadcrumbContainer;
    @FXML private Button breadcrumbSectionBtn;
    @FXML private Label breadcrumbSep1;
    @FXML private Button breadcrumbModuleBtn;
    @FXML private Label breadcrumbSep2;
    @FXML private Label breadcrumbOperationLabel;
    @FXML private Button favoriteToggleBtn;

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
    private VBox securityTipBox;
    @FXML private Label statusLabel;
    @FXML private Button statusVisibilityButton;
    @FXML private Label statusLanguageLabel;
    @FXML private HBox errorBanner;
    @FXML private Label errorBannerTitle;
    @FXML private Label errorBannerRemedy;
    @FXML private Button errorBannerGoToFieldBtn;
    @FXML private Button errorBannerCopyDetailsBtn;
    @FXML private Button errorBannerHelpBtn;
    @FXML private Button errorBannerCloseBtn;
    @FXML private Button toolbarReadinessHelpButton;
    private InlineErrorPresenter inlineErrorPresenter;
    private HelpContext bannerHelpContext;
    private StatusBarPresenter statusBarPresenter;
    @FXML
    private Label sessionTrailCountLabel;
    @FXML private Label inspectorSessionTrailTitle;
    @FXML private Button inspectorAddSessionStepButton;
    @FXML private Button inspectorExportSessionTrailButton;
    @FXML private Button inspectorClearSessionTrailButton;
    @FXML private HBox sessionTrailNavigation;
    @FXML private Button inspectorPreviousSessionStepButton;
    @FXML private Button inspectorNextSessionStepButton;
    @FXML private Button inspectorOpenSessionStepButton;
    @FXML private Label sessionTrailPositionLabel;
    @FXML
    private ModuleHost historyView;
    @FXML
    private HistoryController historyViewController;

    @FXML
    private ModuleHost clipboardShelf;
    @FXML
    private ClipboardShelfController clipboardShelfController;

    // Compact Result Summary Bar
    @FXML private HBox resultSummaryBar;
    @FXML private Label resultOpLabel;
    @FXML private Label resultAlgoLabel;
    @FXML private Label resultSizeLabel;
    @FXML private Label resultFormatLabel;
    @FXML private Label resultStatusBadge;
    @FXML private Label resultLastLabel;
    @FXML private Label resultAlgorithmStaticLabel;
    @FXML private Button resultExpandButton;
    @FXML private Button resultShelfButton;
    @FXML private Button resultCopyButton;
    private ContextualHelpButtonBinder resultActionHelp;
    private ModuleHelpInstaller moduleHelp;
    @FXML private Button resultSaveStepButton;
    @FXML private Button inspectorToggleButton;

    // Quick Start & Guided Workflows
    @FXML private VBox quickStartContainer;
    @FXML private HBox guidedFlowPanel;
    @FXML private Label guideStepTitleLabel;
    @FXML private Label guideStepDescLabel;
    @FXML private Button guideBackBtn;
    @FXML private Button guideNextBtn;
    @FXML private Button guideSkipBtn;
    @FXML private Button guideExitBtn;

    public enum GuidedOperation {
        ENCRYPT, HASH, SIGN, CERT, CONVERT
    }

    private ReadinessPanelCoordinator readinessPanelCoordinator;




    // Saved Sessions
    @FXML
    private VBox savedSessionsContainer;

    @FXML
    private VBox savedSessionsList;

    @FXML
    private Label inputFormatLabel;

    @FXML
    private ComboBox<String> inputFormatCombo;
    private boolean formatHelpShortcutsInstalled;
    @FXML private Button formatContractHelpButton;

    @FXML
    private Label contractOperationLabel;

    @FXML
    private HBox formatFlowBar;


    // Managers
    private com.cryptocarver.model.HistoryManager historyManager;
    private HistoryCoordinator historyCoordinator;
    private ShellHistoryCoordinator shellHistoryCoordinator;
    private SavedSessionsCoordinator savedSessionsCoordinator;
    private SaveSessionCoordinator saveSessionCoordinator;
    private UtilityToolsCoordinator utilityToolsCoordinator;
    private String currentActiveOperation = "Dashboard"; // Defaul
    private boolean processDesignerWorkspace;
    private boolean sidePanelVisibleBeforeProcessDesigner;
    private boolean inspectorVisibleBeforeProcessDesigner;
    @FXML
    private ComboBox<String> outputFormatCombo;
    private javafx.beans.value.ChangeListener<String> inputFormatHelpListener;
    private javafx.beans.value.ChangeListener<String> outputFormatHelpListener;
    private boolean formatHelpListenersInstalled;

    // Symmetric and asymmetric key controls are owned by keysContainerController.
    // Certificate, CRL and CMS controls are owned by certificatesContainerController.
    // Generic Tab FXML Fields
    @FXML private ModuleHost genericContainer;

    // Post-Quantum UI
    @FXML private ModuleHost postQuantumContainer;
    @FXML private PostQuantumController postQuantumContainerController;

    // XML Security UI
    @FXML private ModuleHost xmlSecurityContainer;
    @FXML private ModuleHost wssSecurityContainer;
    @FXML private WssSecurityController wssSecurityContainerController;
    @FXML private TextField xmlSignInputPathField;
    @FXML private TextField xmlSignKeyPathField;
    @FXML private PasswordField xmlSignKeyPasswordField;
    @FXML private ComboBox<String> xmlSignKeyAliasCombo;
    @FXML private ComboBox<String> xmlSignLevelCombo;
    @FXML private ComboBox<String> xmlSignPackagingCombo;
    // New Controllers
    @FXML private XMLSignatureController xmlSecurityContainerController;
    @FXML private ModuleHost processDesignerContainer;
    @FXML private ProcessDesignerController processDesignerContainerController;

    // Generic Utilities
    // Hashing
    @FXML
    private ComboBox<String> hashAlgorithmCombo;
    // Legacy generic fields moved to GenericController.
    // The FXML fx:id bindings are now handled by genericContainerController.    @FXML
    private TextField uuidOutputField;

    // Authentication Tab FXML Fields
    @FXML private ModuleHost authenticationContainer;
    @FXML private AuthenticationController authenticationContainerController;

    // Payments module
    @FXML private ModuleHost paymentsContainer;
    @FXML private PaymentsController paymentsContainerController;

    // EMV module
    @FXML private ModuleHost emvContainer;
    @FXML private EMVController emvContainerController;

    // Controllers
    private KeysController keysController;
    private PaymentsController paymentsController;
    private EMVController emvController;
    private CipherController cipherController;
    @FXML private JOSEController joseController;
    @FXML private COSEController coseController;
    @FXML private WalletController walletController;

    @FXML
    private MenuBar mainMenuBar;
    @FXML private Menu fileMenu;
    @FXML private Menu editMenu;
    @FXML private Menu viewMenu;
    @FXML private Menu securityMenu;
    @FXML private Menu toolsMenu;
    @FXML private Menu helpMenu;
    @FXML private Menu laboratoryMenu;
    @FXML private Menu languageMenu;
    @FXML private Menu appearanceMenu;
    @FXML private RadioMenuItem languageSystemMenuItem;
    @FXML private RadioMenuItem languageEsMenuItem;
    @FXML private RadioMenuItem languageEnMenuItem;
    @FXML private ToggleGroup languagePreferenceGroup;
    @FXML private ToggleGroup themePreferenceGroup;
    @FXML private RadioMenuItem themeSystemMenuItem;
    @FXML private RadioMenuItem themeLightMenuItem;
    @FXML private RadioMenuItem themeDarkMenuItem;
    @FXML private MenuItem importKeyMenuItem;
    @FXML private MenuItem exportScreenMenuItem;
    @FXML private MenuItem importScreenMenuItem;
    @FXML private MenuItem saveSessionMenuItem;
    @FXML private MenuItem exportSessionTrailMenuItem;
    @FXML private MenuItem exportHistoryMenuItem;
    @FXML private MenuItem exitMenuItem;
    @FXML private MenuItem clearInputMenuItem;
    @FXML private MenuItem clearOutputMenuItem;
    @FXML private MenuItem copyOutputMenuItem;
    @FXML private MenuItem addToShelfMenuItem;
    @FXML private MenuItem quickStartMenuItem;
    @FXML private MenuItem clipboardShelfMenuItem;
    @FXML private MenuItem commandPaletteMenuItem;
    @FXML private MenuItem toggleSidePanelMenuItem;
    @FXML private MenuItem toggleInspectorMenuItem;
    @FXML private MenuItem expandResultMenuItem;
    @FXML private MenuItem expandTableMenuItem;
    @FXML private MenuItem zoomInMenuItem;
    @FXML private MenuItem zoomOutMenuItem;
    @FXML private MenuItem resetViewMenuItem;
    @FXML private RadioMenuItem visibilityFullLabMenuItem;
    @FXML private RadioMenuItem visibilityMaskedMenuItem;
    @FXML private RadioMenuItem visibilityRedactedMenuItem;
    @FXML private MenuItem epochMenuItem;
    @FXML private MenuItem jsonMenuItem;
    @FXML private MenuItem byteInspectorMenuItem;
    @FXML private MenuItem clearKeyCacheMenuItem;
    @FXML private MenuItem shortcutsMenuItem;
    @FXML private MenuItem diagnosticsMenuItem;
    @FXML private MenuItem aboutMenuItem;
    @FXML private MenuItem laboratoryQuickStartMenuItem;
    @FXML private Button toolbarSearchButton;
    @FXML private Button toolbarSaveSessionButton;
    @FXML private Button toolbarClearButton;
    @FXML private Button toolbarExpandButton;
    @FXML private Button toolbarShelfButton;
    @FXML private Button toolbarCopyButton;
    @FXML private Label outputFormatLabel;
    private final I18nService i18n = I18nService.getInstance();
    private final ShellTextResolver shellTextResolver = new ShellTextResolver(i18n::text);
    private final ShellLocalizationCoordinator shellLocalizationCoordinator =
            new ShellLocalizationCoordinator(i18n);
    private final ShellStartupCoordinator startupCoordinator = new ShellStartupCoordinator(i18n);
    private java.util.function.Consumer<java.util.Locale> i18nListener;

    // Async Progress UI
    @FXML private HBox asyncProgressBox;
    @FXML private ProgressIndicator asyncProgressIndicator;
    @FXML private ProgressBar asyncProgressBar;
    @FXML private Label asyncProgressLabel;
    @FXML private Button asyncCancelBtn;
    @FXML private Label inspectorTitleLabel;
    @FXML private TabPane inspectorTabs;
    @FXML private Tab inspectorResultTab;
    @FXML private Tab inspectorHelpTab;
    @FXML private VBox contextualHelpContent;
    @FXML private MenuItem operationHelpMenuItem;
    @FXML private CheckMenuItem contextualHelpModeMenuItem;
    @FXML private Button toolbarOperationHelpButton;
    @FXML private Label inspectorInputBytesTitle;
    @FXML private Label inspectorOutputBytesTitle;
    @FXML private Label inspectorAlgorithmTitle;
    @FXML private Label inspectorSecurityTipsTitle;
    @FXML private Label inspectorWarningTitle;
    @FXML private Label inspectorHistoryTitle;
    @FXML private Button inspectorExportJsonButton;
    @FXML private Button inspectorClearHistoryButton;
    @FXML private Label commandEscapeLabel;
    @FXML private Label commandNavigateLabel;
    @FXML private Label commandSelectLabel;
    @FXML private Label commandCancelLabel;
    @FXML private Label commandTitleLabel;

    private final OperationExecutor operationExecutor = new OperationExecutor();
    private final ModuleLoader moduleLoader = new ModuleLoader(ModernMainController.class);

    private ModuleHost[] moduleHosts() {
        return new ModuleHost[]{jose, cose, wallet, keysContainer, certificatesContainer,
                cipherContainer, authenticationContainer, paymentsContainer, emvContainer,
                genericContainer, historyView, clipboardShelf, postQuantumContainer,
                xmlSecurityContainer, wssSecurityContainer, processDesignerContainer};
    }

    private NavigationRouter createNavigationRouter() { return ShellStartupCoordinator.createNavigationRouter(routerView()); }

    private ShellStartupCoordinator.RouterView routerView() {
        return new ShellStartupCoordinator.RouterView(this::moduleHosts, () -> contentContainer,
                () -> quickStartContainer, () -> savedSessionsContainer, this::navigationHost, this::resolveNavigationController,
                () -> currentActiveOperation, () -> keysController, () -> processDesignerContainer,
                this::loadSymmetricKeysContent, this::expandAccordionPane, this::expandAsymmetricAccordionPane,
                this::expandCertificatesAccordionPane, this::expandGenericAccordionPane,
                this::loadPostQuantumContent, this::expandPQCAccordionPane, this::loadXMLSecurityContent, this::expandXMLAccordionPane,
                this::loadWssSecurityContent, this::expandWssAccordionPane, this::loadEMVContent, this::expandEMVAccordionPane,
                this::loadCipherContent, this::expandCipherAccordionPane, this::loadAuthenticationContent, this::expandAuthenticationAccordionPane,
                this::loadPaymentsContent, this::expandPaymentsAccordionPane, this::handleEpochConverter, this::handleJsonFormatter,
                () -> savedSessionsCoordinator().show(), this::enterProcessDesignerWorkspace, this::exitProcessDesignerWorkspace,
                this::updateContentHeader, this::updateContentSubtitle, this::updateStatus, this::handleItemSelectedImpl);
    }

    private javafx.scene.Node navigationHost(UiNavigationRegistry.Module module) {
        return switch(module) {
            case JOSE -> jose; case COSE -> cose; case WALLET -> wallet;
            case EPOCH_CONVERTER, JSON_FORMATTER -> null;
            case KEYS_SYMMETRIC, KEYS_ASYMMETRIC -> keysContainer;
            case CERTIFICATES -> certificatesContainer; case GENERIC -> genericContainer;
            case POST_QUANTUM -> postQuantumContainer; case XML_SECURITY -> xmlSecurityContainer;
            case WSS_SECURITY -> wssSecurityContainer; case EMV -> emvContainer;
            case HISTORY -> historyView; case CLIPBOARD_SHELF -> clipboardShelf;
            case SAVED_SESSIONS -> savedSessionsContainer; case CIPHER -> cipherContainer;
            case AUTHENTICATION -> authenticationContainer; case PAYMENTS -> paymentsContainer;
            case PROCESS_DESIGNER -> processDesignerContainer;
        };
    }

    private Object resolveNavigationController(UiNavigationRegistry.Module module) {
        return switch(module) {
            case JOSE -> {
                joseController = ensureModule(jose, JOSEController.class);
                if (joseController != null && jweHelpContextAdapter == null) {
                    jweHelpContextAdapter = joseController.createJweHelpContextAdapter(contextualHelpState);
                    joseController.configureJweHelp(contextualHelpState, this::showJweHelpContext, this::rememberJweHelpContext);
                    installResultActionHelp();
                    joseController.setJweHelpActionTracker(this::trackJweHelpAction);
                }
                yield joseController;
            }
            case COSE -> coseController = ensureModule(cose, COSEController.class);
            case WALLET -> walletController = ensureModule(wallet, WalletController.class);
            case EPOCH_CONVERTER, JSON_FORMATTER, SAVED_SESSIONS -> null;
            case KEYS_SYMMETRIC, KEYS_ASYMMETRIC -> keysController = keysContainerController = ensureModule(keysContainer, KeysController.class);
            case CERTIFICATES -> certificatesContainerController = ensureModule(certificatesContainer, CertificatesController.class);
            case GENERIC -> {
                genericContainerController = ensureModule(genericContainer, GenericController.class);
                if (genericContainerController != null) {
                    genericContainerController.setActiveFormatContractOperation(
                            com.cryptocarver.model.FormatProfilePolicy.operation(currentActiveOperation));
                    genericContainerController.configureContextualHelp(contextualHelpState, this::showGenericHelpContext);
                    genericContainerController.setActiveContextualHelpOperation(genericHelpOperationId(currentActiveOperation));
                }
                yield genericContainerController;
            }
            case POST_QUANTUM -> postQuantumContainerController = ensureModule(postQuantumContainer, PostQuantumController.class);
            case XML_SECURITY -> xmlSecurityContainerController = ensureModule(xmlSecurityContainer, XMLSignatureController.class);
            case WSS_SECURITY -> wssSecurityContainerController = ensureModule(wssSecurityContainer, WssSecurityController.class);
            case EMV -> emvController = emvContainerController = ensureModule(emvContainer, EMVController.class);
            case HISTORY -> historyViewController = ensureModule(historyView, HistoryController.class);
            case CLIPBOARD_SHELF -> {
                clipboardShelfController = ensureModule(clipboardShelf, ClipboardShelfController.class);
                if (clipboardShelfController != null) {
                    clipboardShelfController.configureContextualHelp(contextualHelpState, this::showGenericHelpContext);
                    clipboardShelfController.setActiveContextualHelpOperation("op_gen_clipboard",
                            AppSettings.getInstance().isContextualHelpEnabled());
                }
                yield clipboardShelfController;
            }
            case CIPHER -> {
                cipherController = cipherContainerController = ensureModule(cipherContainer, CipherController.class);
                if (cipherContainerController != null && cipherHelpContextAdapter == null) {
                    cipherHelpContextAdapter = new HelpContextAdapter(contextualHelpState);
                    cipherHelpContextAdapter.bindSelection(HelpContext.Selection.ALGORITHM,
                            cipherContainerController.getSymmetricAlgorithmCombo());
                    cipherHelpContextAdapter.bindSelection(HelpContext.Selection.MODE,
                            cipherContainerController.getCipherModeCombo());
                    cipherHelpContextAdapter.bindSelection(HelpContext.Selection.PADDING,
                            cipherContainerController.getPaddingCombo());
                    cipherHelpContextAdapter.bindSelection(HelpContext.Selection.KEY_SOURCE,
                            cipherContainerController.getSymKeySourceCombo());
                    cipherHelpContextAdapter.bindSelection(HelpContext.Selection.FPE_OPERATION, cipherContainerController.getFpeOperationCombo());
                    cipherHelpContextAdapter.bindSelection(HelpContext.Selection.FPE_ALGORITHM, cipherContainerController.getFpeAlgorithmCombo());
                    cipherHelpContextAdapter.bindSelection(HelpContext.Selection.FPE_PRESET,
                            cipherContainerController.getFpeAlphabetPresetCombo(), value ->
                                    "Custom".equals(value) ? "custom" : com.cryptocarver.model.help.OperationHelpRegistry.slug(value));
                    cipherHelpContextAdapter.bindDerivedSize(HelpContext.Selection.FPE_RADIX,
                            cipherContainerController.getFpeAlphabetField(), raw -> {
                                String preset = cipherContainerController.getFpeAlphabetPresetCombo().getValue();
                                if ("Decimal (0-9)".equals(preset)) return 10;
                                if ("Alphanumeric".equals(preset)) return 62;
                                if ("ASCII printable".equals(preset)) return 95;
                                if (!"Custom".equals(preset)) return -1;
                                int[] symbols = raw.codePoints().toArray();
                                if (symbols.length > 256 || java.util.Arrays.stream(symbols).anyMatch(c -> c > 0xff)
                                        || java.util.Arrays.stream(symbols).distinct().count() != symbols.length) return -1;
                                return symbols.length;
                            });
                    cipherHelpContextAdapter.bindDerivedSize(HelpContext.Selection.FPE_KEY_BYTES,
                            cipherContainerController.getFpeKeyField(), ModernMainController::hexByteLength);
                    cipherHelpContextAdapter.bindDerivedSize(HelpContext.Selection.FPE_TWEAK_BYTES,
                            cipherContainerController.getFpeTweakField(), ModernMainController::hexByteLength);
                    cipherHelpContextAdapter.bindSelection(HelpContext.Selection.FILE_PROFILE, cipherContainerController.getFileCipherAlgorithmCombo());
                    cipherHelpContextAdapter.bindFlag(HelpContext.Selection.FILE_LINES, cipherContainerController.getFileCipherLinesCheck());
                    cipherHelpContextAdapter.bindSelection(HelpContext.Selection.FILE_LINE_ENCODING, cipherContainerController.getFileCipherLineEncodingCombo());
                    cipherHelpContextAdapter.bindSelection(HelpContext.Selection.FILE_CHARSET, cipherContainerController.getFileCipherLineCharsetCombo());
                    cipherHelpContextAdapter.bindFlag(HelpContext.Selection.FILE_COMPACT_CBC, cipherContainerController.getFileCipherCompactCbcCheck());
                    cipherHelpContextAdapter.bindSelection(HelpContext.Selection.RSA_PADDING, cipherContainerController.getRsaPaddingCombo());
                    cipherHelpContextAdapter.bindSelection(HelpContext.Selection.RSA_INPUT_FORMAT, cipherContainerController.getAsymmetricInputFormatCombo());
                    cipherHelpContextAdapter.bindSelection(HelpContext.Selection.RSA_OUTPUT_FORMAT, cipherContainerController.getAsymmetricOutputFormatCombo());
                    cipherHelpContextAdapter.bindPresence(HelpContext.Selection.RSA_PUBLIC_KEY_PRESENT, cipherContainerController.getPublicKeyArea());
                    cipherHelpContextAdapter.bindPresence(HelpContext.Selection.RSA_PRIVATE_KEY_PRESENT, cipherContainerController.getPrivateKeyArea());
                    cipherHelpContextAdapter.bindPresence(HelpContext.Selection.ANALYSIS_HAS_KEY, cipherContainerController.getFileCipherKeyField());
                    cipherHelpContextAdapter.bindPresence(HelpContext.Selection.ANALYSIS_HAS_IV, cipherContainerController.getFileCipherNonceField());
                    cipherHelpContextAdapter.bindPresence(HelpContext.Selection.ANALYSIS_HAS_TAG, cipherContainerController.getFileCipherTagField());
                    cipherHelpContextAdapter.bindPresence(HelpContext.Selection.ANALYSIS_HAS_AAD, cipherContainerController.getFileCipherAadField());
                }
                if (cipherContainerController != null) {
                    cipherContainerController.configureContextualHelp(contextualHelpState,
                            this::showCipherModuleHelpContext, this::updateCipherModuleHelpContext);
                    cipherContainerController.setContextualHelpRouteActive(
                            isSymmetricCipherRouteActive());
                    cipherContainerController.setContextualHelpOperationActive(cipherOperationId(currentActiveOperation));
                    cipherContainerController.setContextualHelpEnabled(AppSettings.getInstance().isContextualHelpEnabled()
                            && !cipherOperationId(currentActiveOperation).isBlank());
                }
                yield cipherContainerController;
            }
            case AUTHENTICATION -> authenticationContainerController = ensureModule(authenticationContainer, AuthenticationController.class);
            case PAYMENTS -> paymentsController = paymentsContainerController = ensureModule(paymentsContainer, PaymentsController.class);
            case PROCESS_DESIGNER -> {
                processDesignerContainerController = ensureModule(processDesignerContainer, ProcessDesignerController.class);
                if (processDesignerContainerController != null) {
                    processDesignerContainerController.configureContextualHelp(contextualHelpState, this::showGenericHelpContext);
                    processDesignerContainerController.setActiveContextualHelpOperation("op_gen_process_designer",
                            AppSettings.getInstance().isContextualHelpEnabled());
                }
                yield processDesignerContainerController;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private <T> T ensureModule(ModuleHost host, Class<T> controllerType) {
        if (host == null) return null;
        host.showConfigured();
        Object controller = host.controller();
        connectShellServices(controller);
        return controllerType.isInstance(controller) ? controllerType.cast(controller) : null;
    }

    /**
     * Connects a module to the shell's result publishing and shared format controls.
     *
     * <p>This has to happen as the module materializes, not in {@link #initialize()}: modules
     * load on first use, so at initialization time every module controller is still null and a
     * null-guarded wiring pass there silently connects nothing. A module left unconnected keeps
     * its reporter null, and since each module guards on that, its results and errors are
     * dropped instead of reaching the status bar.
     */
    private void connectShellServices(Object controller) { startupCoordinator.connectShellServices(controller, shellServices); }

    private final ShellStartupCoordinator.ShellServices shellServices = new ShellStartupCoordinator.ShellServices(
            () -> this, () -> this, this::historyManager, () -> this.inputFormatCombo, () -> this.outputFormatCombo,
            shelf -> shelf.setNavigator(this, this));

    /**
     * Materializes every deferred module and runs the same initialization the shell performs
     * when the user first navigates to it.
     *
     * <p>Modules load on first use, so the {@code *Controller} fields stay null until the user
     * reaches the section that owns them, and their {@code init} wiring — status reporter,
     * shared format combos, cross-module callbacks — runs at that moment. UI tests assert on
     * those controllers directly and have no user to navigate for them, so they call this once
     * after loading the shell. It delegates to the real loaders rather than to
     * {@link #ensureModule} so a test sees a module wired the way the app wires it. Must run on
     * the FX thread.
     */
    void materializeModulesForTesting() {
        // Loading a module also shows it, so which module is on screen is remembered and put
        // back afterwards: a shell with every module visible at once is a state the app never
        // reaches, and code that asks what is currently visible would answer from it.
        ModuleHost[] hosts = moduleHosts();
        boolean[] wasVisible = new boolean[hosts.length];
        boolean[] wasManaged = new boolean[hosts.length];
        for (int i = 0; i < hosts.length; i++) {
            if (hosts[i] == null) continue;
            wasVisible[i] = hosts[i].isVisible();
            wasManaged[i] = hosts[i].isManaged();
        }

        loadSymmetricKeysContent();
        loadCipherContent();
        loadAuthenticationContent();
        loadEMVContent();
        loadPaymentsContent();
        loadPostQuantumContent();
        loadXMLSecurityContent();
        loadWssSecurityContent();
        if (genericContainerController == null) genericContainerController = ensureModule(genericContainer, GenericController.class);
        if (joseController == null) joseController = ensureModule(jose, JOSEController.class);
        if (coseController == null) coseController = ensureModule(cose, COSEController.class);
        if (historyViewController == null) historyViewController = ensureModule(historyView, HistoryController.class);
        if (clipboardShelfController == null) clipboardShelfController = ensureModule(clipboardShelf, ClipboardShelfController.class);
        if (processDesignerContainerController == null) processDesignerContainerController = ensureModule(processDesignerContainer, ProcessDesignerController.class);

        for (int i = 0; i < hosts.length; i++) {
            if (hosts[i] == null) continue;
            hosts[i].setVisible(wasVisible[i]);
            hosts[i].setManaged(wasManaged[i]);
        }
    }

    public OperationExecutor getOperationExecutor() {
        return operationExecutor;
    }

    private ShellAsyncProgressCoordinator asyncProgressCoordinator;

    private ShellAsyncProgressCoordinator asyncProgressCoordinator() {
        if (asyncProgressCoordinator == null) asyncProgressCoordinator = new ShellAsyncProgressCoordinator(i18n, () -> this);
        return asyncProgressCoordinator;
    }

    private ShellAsyncProgressCoordinator.View asyncProgressView() {
        return new ShellAsyncProgressCoordinator.View(asyncProgressBox, asyncProgressIndicator, asyncProgressBar, asyncProgressLabel, asyncCancelBtn);
    }

    public void showAsyncProgress(String operationName) { asyncProgressCoordinator().show(asyncProgressView(), operationName); }
    public void updateAsyncProgressDetails(OperationExecutor.ProgressDetails details) { asyncProgressCoordinator().update(asyncProgressView(), details); }
    public void hideAsyncProgress() { asyncProgressCoordinator().hide(asyncProgressView()); }
    public void handleCancelAsyncOperation() { asyncProgressCoordinator().cancel(asyncProgressView()); }

    @FXML
    private final java.util.concurrent.atomic.AtomicBoolean isShutdown = new java.util.concurrent.atomic.AtomicBoolean(false);

    public void shutdown() {
        if (isShutdown.compareAndSet(false, true)) {
            bannerHelpContext = null;
            removeGlobalFormatHelpListeners();
            detachStartupListeners();
            detachWindowLifecycleListeners();
            if (contextualHelpWindowCoordinator != null) contextualHelpWindowCoordinator.close();
            if (contextualHelpCoordinator != null) contextualHelpCoordinator.close();
            if (cipherHelpContextAdapter != null) cipherHelpContextAdapter.close();
            if (jweHelpContextAdapter != null) jweHelpContextAdapter.close();
            if (pinBlockHelpContextAdapter != null) pinBlockHelpContextAdapter.close();
            if (paymentsController != null) paymentsController.closePinBlockHelpBindings();
            if (joseController != null) joseController.closeJweHelpBindings();
            if (resultActionHelp != null) { resultActionHelp.close(); resultActionHelp = null; }
            if (moduleHelp != null) { moduleHelp.close(); moduleHelp = null; }
            if (cipherContainerController != null) cipherContainerController.closeContextualHelpBindings();
            expandedTextViewer.dispose();
            sessionStepViewer.dispose();
            expandedTableViewer.dispose();
            if (clipboardShelfController != null) {
                clipboardShelfController.dispose();
            }
            if (operationExecutor != null) {
                operationExecutor.shutdown();
            }
        }
    }

    private javafx.beans.value.ChangeListener<Number> responsiveWidthListener;

    private void detachStartupListeners() {
        startupCoordinator.shutdown();
        if (mainPane != null && responsiveWidthListener != null) mainPane.widthProperty().removeListener(responsiveWidthListener);
    }

    private javafx.scene.Node lifecycleNode;
    private javafx.scene.Scene lifecycleScene;
    private javafx.stage.Window lifecycleWindow;
    private final javafx.event.EventHandler<javafx.stage.WindowEvent> lifecycleHiddenHandler = event -> shutdown();
    private final javafx.beans.value.ChangeListener<javafx.stage.Window> lifecycleWindowListener = (obs, oldWindow, newWindow) -> {
        observeLifecycleWindow(newWindow);
        if (oldWindow != null && newWindow == null) shutdown();
    };
    private final javafx.beans.value.ChangeListener<javafx.scene.Scene> lifecycleSceneListener = (obs, oldScene, newScene) -> {
        if (oldScene != null && newScene == null) shutdown();
        else observeLifecycleScene(newScene);
    };

    private void setupWindowLifecycleListeners() {
        lifecycleNode = rootStackPane != null ? rootStackPane : asyncProgressBox;
        if (lifecycleNode == null) return;
        lifecycleNode.sceneProperty().addListener(lifecycleSceneListener);
        observeLifecycleScene(lifecycleNode.getScene());
    }

    private void observeLifecycleScene(javafx.scene.Scene scene) {
        if (lifecycleScene == scene) return;
        if (lifecycleScene != null) lifecycleScene.windowProperty().removeListener(lifecycleWindowListener);
        observeLifecycleWindow(null);
        lifecycleScene = scene;
        if (scene != null) {
            scene.windowProperty().addListener(lifecycleWindowListener);
            observeLifecycleWindow(scene.getWindow());
        }
    }

    private void observeLifecycleWindow(javafx.stage.Window window) {
        if (lifecycleWindow == window) return;
        if (lifecycleWindow != null) lifecycleWindow.removeEventHandler(
                javafx.stage.WindowEvent.WINDOW_HIDING, lifecycleHiddenHandler);
        lifecycleWindow = window;
        if (window != null) window.addEventHandler(javafx.stage.WindowEvent.WINDOW_HIDING, lifecycleHiddenHandler);
    }

    private void detachWindowLifecycleListeners() {
        if (lifecycleNode != null) lifecycleNode.sceneProperty().removeListener(lifecycleSceneListener);
        observeLifecycleScene(null);
        lifecycleNode = null;
    }

    /** Observation boundary for startup diagnostics; production has no observer. */
    void startupPhaseCompleted(String phase) {}

    @FXML
    public void initialize() {
        startupCoordinator.initialize(startupView());
        installGlobalFormatHelpListeners();
    }

    private void installGlobalFormatHelpListeners() {
        if (formatHelpListenersInstalled) return;
        formatHelpListenersInstalled = true;
        inputFormatHelpListener = (observable, before, after) -> refreshOpenFormatHelp("input");
        outputFormatHelpListener = (observable, before, after) -> refreshOpenFormatHelp("output");
        if (inputFormatCombo != null) inputFormatCombo.valueProperty().addListener(inputFormatHelpListener);
        if (outputFormatCombo != null) outputFormatCombo.valueProperty().addListener(outputFormatHelpListener);
    }

    private void removeGlobalFormatHelpListeners() {
        if (!formatHelpListenersInstalled) return;
        if (inputFormatCombo != null) inputFormatCombo.valueProperty().removeListener(inputFormatHelpListener);
        if (outputFormatCombo != null) outputFormatCombo.valueProperty().removeListener(outputFormatHelpListener);
        formatHelpListenersInstalled = false;
    }

    private ShellStartupCoordinator.View startupView() {
        var chrome = new ShellStartupCoordinator.ChromeView(inputFormatCombo, outputFormatCombo, inputFormatLabel,
                contractOperationLabel, contentTitleLabel, contentSubtitleLabel, breadcrumbContainer,
                breadcrumbSectionBtn, breadcrumbSep1, breadcrumbModuleBtn, breadcrumbSep2, breadcrumbOperationLabel,
                favoriteToggleBtn, FAVORITE_SHORTCUT, key -> {
                    if (genericContainerController != null) genericContainerController.setActiveFormatContractOperation(key);
                }, this::selectBreadcrumbSection, this::navigateToModule);
        var presenters = new ShellStartupCoordinator.PresenterView(errorBanner, errorBannerTitle, errorBannerRemedy,
                errorBannerGoToFieldBtn, errorBannerCopyDetailsBtn, errorBannerCloseBtn, securityTipLabel, securityTipBox,
                statusLabel, statusVisibilityButton, statusLanguageLabel, visibilityProfileGroup);
        var services = new ShellStartupCoordinator.Services(value -> shellDialogCoordinator = value,
                value -> navigationRouter = value, value -> navigationChrome = value, value -> inlineErrorPresenter = value,
                value -> statusBarPresenter = value, value -> navigationController = value, value -> i18nListener = value);
        var actions = new ShellStartupCoordinator.Actions(this::setupWindowLifecycleListeners, this::setupLaboratoryMenu,
                this::initializeCommandPalette, this::syncMenuBarAccelerators, this::installResponsiveLayoutSupport,
                this::applyLocalization, this::initializeHistory, this::refreshSessionTrailUI, this::loadSymmetricKeysContent,
                this::showSymmetricKeys, this::restoreStartupLastRoute, this::applyFontSize, this::installResultViewerSupport,
                this::installTableViewerSupport, this::handleItemSelected, this::startupPhaseCompleted, isShutdown::get);
        return new ShellStartupCoordinator.View(dialogService, () -> windowOf(mainPane), this::moduleHosts, moduleLoader,
                this::createNavigationRouter, chrome, presenters, () -> navigationRail, () -> sidePanel, () -> this,
                operationExecutor, this::showAsyncProgress, this::updateAsyncProgressDetails, this::hideAsyncProgress, services, actions);
    }

    /** Applies shell strings without changing operation names, routes or technical values. */
    public void applyLocalization() {
        shellLocalizationCoordinator.applyLocalization(localizationView(), localizationState);
        if (operationHelpMenuItem != null) operationHelpMenuItem.setText(i18n.text("help.operation.open"));
        if (contextualHelpModeMenuItem != null) {
            contextualHelpModeMenuItem.setText(i18n.text("help.mode"));
            contextualHelpModeMenuItem.setSelected(AppSettings.getInstance().isContextualHelpEnabled());
        }
        if (toolbarOperationHelpButton != null) {
            // Compact glyph: the caption lives in the tooltip so the Spanish toolbar does not clip.
            toolbarOperationHelpButton.setAccessibleText(i18n.text("help.operation.open"));
            toolbarOperationHelpButton.setAccessibleHelp(i18n.text("help.control.shortcut"));
            toolbarOperationHelpButton.setTooltip(new Tooltip(i18n.text("help.operation.open") + "\n" + i18n.text("help.control.shortcut")));
        }
        if (inspectorResultTab != null) inspectorResultTab.setText(i18n.text("help.tab.result"));
        if (inspectorHelpTab != null) inspectorHelpTab.setText(i18n.text("help.tab.help"));
        if (errorBannerHelpBtn != null) {
            errorBannerHelpBtn.setText(i18n.text("help.aux.preflight.open"));
            errorBannerHelpBtn.setAccessibleText(i18n.text("help.aux.preflight.open"));
            errorBannerHelpBtn.setAccessibleHelp(i18n.text("help.aux.preflight.open.help"));
        }
        if (toolbarReadinessHelpButton != null) {
            toolbarReadinessHelpButton.setTooltip(new Tooltip(i18n.text("help.readiness.open")));
            toolbarReadinessHelpButton.setAccessibleText(i18n.text("help.readiness.open"));
            toolbarReadinessHelpButton.setAccessibleHelp(i18n.text("help.readiness.open.help"));
        }
        if (joseController != null) joseController.refreshJweContextHelpLocalization();
        if (paymentsController != null) paymentsController.refreshPinBlockContextHelpLocalization();
        if (resultActionHelp != null) resultActionHelp.refreshLocalization();
        if (moduleHelp != null) moduleHelp.refreshLocalization();
        localizeFormatHelpButtons();
        if (cipherContainerController != null) cipherContainerController.refreshHelpButtonLocalization();
        if (readinessHelpBtn != null) {
            readinessHelpBtn.setText(i18n.text("help.readiness.checks"));
            readinessHelpBtn.setAccessibleText(i18n.text("help.readiness.checks"));
            readinessHelpBtn.setAccessibleHelp(i18n.text("help.readiness.checks.help"));
        }
        if (bannerHelpContext != null) bannerHelpContext = bannerHelpContext.withLocale(i18n.getLocale());
        contextualHelpState.setLocale(i18n.getLocale());
        if (contextualHelpCoordinator != null) contextualHelpCoordinator.refresh();
        if (contextualHelpWindowCoordinator != null) contextualHelpWindowCoordinator.refreshLocalization();
    }

    private void localizeFormatHelpButtons() {
        localizeFormatHelpButton(formatContractHelpButton, "contract");
        installFormatHelpShortcuts();
    }

    /** The format selectors answer Shift+F1 themselves; only the contract keeps a visible help button. */
    private void installFormatHelpShortcuts() {
        if (formatHelpShortcutsInstalled || inputFormatCombo == null || outputFormatCombo == null) return;
        formatHelpShortcutsInstalled = true;
        onShiftF1(inputFormatCombo, this::handleInputFormatHelp);
        onShiftF1(outputFormatCombo, this::handleOutputFormatHelp);
    }

    private static void onShiftF1(javafx.scene.Node control, Runnable help) {
        control.addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() != javafx.scene.input.KeyCode.F1 || !event.isShiftDown() || event.isControlDown()
                    || event.isAltDown() || event.isMetaDown()) return;
            event.consume();
            help.run();
        });
    }

    private void localizeFormatHelpButton(Button button, String action) {
        if (button == null) return;
        button.setAccessibleText(i18n.text("help.aux.global_formats.action." + action + ".title"));
        button.setAccessibleHelp(i18n.text("help.aux.global_formats.action." + action + ".description"));
    }

    private ShellLocalizationCoordinator.View localizationView() { return ShellLocalizationCoordinator.createView(localizationControls(), COMMAND_PALETTE_SHORTCUT, FAVORITE_SHORTCUT); }

    private ShellLocalizationCoordinator.Controls localizationControls() {
        return new ShellLocalizationCoordinator.Controls(
                fileMenu, editMenu, viewMenu, securityMenu,
                toolsMenu, helpMenu, laboratoryMenu, languageMenu,
                appearanceMenu, importKeyMenuItem, exportScreenMenuItem, importScreenMenuItem,
                saveSessionMenuItem, exportSessionTrailMenuItem, exportHistoryMenuItem, exitMenuItem,
                clearInputMenuItem, clearOutputMenuItem, copyOutputMenuItem, addToShelfMenuItem,
                quickStartMenuItem, clipboardShelfMenuItem, commandPaletteMenuItem, toggleSidePanelMenuItem,
                toggleInspectorMenuItem, expandResultMenuItem, expandTableMenuItem, zoomInMenuItem,
                zoomOutMenuItem, resetViewMenuItem, visibilityFullLabMenuItem, visibilityMaskedMenuItem,
                visibilityRedactedMenuItem, epochMenuItem, jsonMenuItem, byteInspectorMenuItem,
                clearKeyCacheMenuItem, shortcutsMenuItem, diagnosticsMenuItem, aboutMenuItem,
                laboratoryQuickStartMenuItem, languageSystemMenuItem, languageEsMenuItem, languageEnMenuItem,
                themeSystemMenuItem, themeLightMenuItem, themeDarkMenuItem, toolbarSaveSessionButton,
                toolbarClearButton, toolbarExpandButton, toolbarShelfButton, toolbarCopyButton,
                inputFormatLabel, outputFormatLabel, resultSaveStepButton, inspectorSessionTrailTitle,
                inspectorAddSessionStepButton, inspectorExportSessionTrailButton, inspectorClearSessionTrailButton, inspectorOpenSessionStepButton,
                resultLastLabel, resultAlgorithmStaticLabel, errorBannerTitle, errorBannerRemedy,
                errorBannerGoToFieldBtn, errorBannerCopyDetailsBtn, guideBackBtn, guideNextBtn,
                guideSkipBtn, guideExitBtn, asyncProgressLabel, asyncCancelBtn,
                inspectorTitleLabel, inspectorInputBytesTitle, inspectorOutputBytesTitle, inspectorAlgorithmTitle,
                inspectorSecurityTipsTitle, inspectorWarningTitle, inspectorHistoryTitle, inspectorExportJsonButton,
                inspectorClearHistoryButton, commandEscapeLabel, commandEmptyLabel, commandNavigateLabel,
                commandSelectLabel, commandCancelLabel, commandTitleLabel, toolbarSearchButton,
                resultExpandButton, resultShelfButton, resultCopyButton, inspectorPreviousSessionStepButton,
                inspectorNextSessionStepButton, inspectorToggleButton, errorBannerCloseBtn, inputFormatCombo,
                outputFormatCombo, commandSearchField, favoriteToggleBtn, resultStatusBadge,
                mainPane);
    }

    private final ShellLocalizationCoordinator.LiveState localizationState = new ShellLocalizationCoordinator.LiveState(
            () -> this, () -> navigationRail, () -> sidePanel, () -> currentActiveOperation,
            () -> statusLabel, () -> statusBarPresenter, () -> inlineErrorPresenter,
            () -> this.rootStackPane != null ? this.rootStackPane : mainPane,
            this::refreshSessionTrailNavigation, this::updateBreadcrumbs, this::updateFavoriteToggleState);

    private String localizedSectionText(String value) {
        return shellTextResolver.localizedSectionText(value);
    }

    private String localizedModuleText(String value) {
        return shellTextResolver.localizedModuleText(value);
    }

    @FXML private void handleLanguageSystem() { shellLocalizationCoordinator.setLanguage(LanguagePreference.SYSTEM); }
    @FXML private void handleLanguageEs() { shellLocalizationCoordinator.setLanguage(LanguagePreference.ES); }
    @FXML private void handleLanguageEn() { shellLocalizationCoordinator.setLanguage(LanguagePreference.EN); }
    @FXML private void handleThemeSystem() { setTheme(com.cryptocarver.model.ThemePreference.SYSTEM); }
    @FXML private void handleThemeLight() { setTheme(com.cryptocarver.model.ThemePreference.LIGHT); }
    @FXML private void handleThemeDark() { setTheme(com.cryptocarver.model.ThemePreference.DARK); }

    private void setTheme(com.cryptocarver.model.ThemePreference theme) {
        com.cryptocarver.model.AppSettings.getInstance().setThemePreference(theme);
        if (mainPane == null || mainPane.getScene() == null) return;
        String light = getClass().getResource("/css/theme-light.css").toExternalForm();
        String dark = getClass().getResource("/css/theme-dark.css").toExternalForm();
        mainPane.getScene().getStylesheets().removeAll(light, dark);
        mainPane.getScene().getStylesheets().add(SystemAppearance.resolve(theme)
                == com.cryptocarver.model.ThemePreference.DARK ? dark : light);
    }

    @FXML
    private void handleOpenSecurityMenu() {
        if (securityMenu != null) securityMenu.show();
    }

    private void refreshStatusBarContext() {
        if (statusBarPresenter != null) {
            statusBarPresenter.refreshContext(com.cryptocarver.model.AppSettings.getInstance().getSecretVisibilityProfile());
        }
    }

    /**
     * Keeps the working canvas usable on laptop-sized windows. The inspector
     * remains available through View > Toggle Inspector, but it should not
     * consume almost half of the workspace once the application gets narrow.
     */
    private void installResponsiveLayoutSupport() {
        if (mainPane == null) {
            return;
        }
        responsiveWidthListener = (observable, previousWidth, newWidth) -> updateResponsiveLayout(newWidth.doubleValue());
        mainPane.widthProperty().addListener(responsiveWidthListener);
        Platform.runLater(() -> { if (!isShutdown.get()) updateResponsiveLayout(mainPane.getWidth()); });
    }

    private void updateResponsiveLayout(double width) {
        if (inspectorPanel == null || width <= 0) {
            return;
        }
        if (width < COMPACT_LAYOUT_WIDTH && inspectorPanel.isVisible()) {
            inspectorPanel.setVisible(false);
            inspectorPanel.setManaged(false);
            inspectorHiddenForCompactLayout = true;
        } else if (width >= COMPACT_LAYOUT_WIDTH && inspectorHiddenForCompactLayout) {
            inspectorPanel.setVisible(true);
            inspectorPanel.setManaged(true);
            inspectorHiddenForCompactLayout = false;
        }
    }

    private void loadCipherContent() {
        if (cipherContainerController == null) cipherContainerController = ensureModule(cipherContainer, CipherController.class);
        if (cipherContainerController != null) {
            cipherController = cipherContainerController;
            if (cipherActionDock != null && cipherActionDock.getChildren().isEmpty()) {
                cipherActionDock.getChildren().setAll(cipherController.detachSymmetricActions());
                cipherController.getOutputArea().textProperty().addListener((obs, oldText, newText) -> {
                    if (newText != null && !newText.isBlank()) revealCipherEditor(cipherController.getOutputArea());
                });
                cipherActionDock.visibleProperty().bind(cipherContainer.visibleProperty()
                        .and(cipherController.symmetricWorkspaceProperty()));
                cipherActionDock.managedProperty().bind(cipherActionDock.visibleProperty());
            }
            cipherController.initModern(this, inputFormatCombo, outputFormatCombo,
                    () -> keysController == null ? null : keysController.getLastGeneratedKeyPair());
        }
    }

    private void loadAuthenticationContent() {
        if (authenticationContainerController == null) authenticationContainerController = ensureModule(authenticationContainer, AuthenticationController.class);
        if (authenticationContainerController != null) {
            authenticationContainerController.init(this, inputFormatCombo, outputFormatCombo);
        }
    }

    private void loadEMVContent() {
        if (emvContainerController == null) emvContainerController = ensureModule(emvContainer, EMVController.class);
        if (emvContainerController != null) {
            emvController = emvContainerController;
            emvController.init(this);
        }
    }

    private void loadPaymentsContent() {
        if (paymentsContainerController == null) paymentsContainerController = ensureModule(paymentsContainer, PaymentsController.class);
        if (paymentsContainerController != null) {
            paymentsController = paymentsContainerController;
            paymentsController.init(this);
            if (pinBlockHelpContextAdapter == null) {
                pinBlockHelpContextAdapter = new PinBlockHelpContextAdapter(contextualHelpState,
                        paymentsController.getPinBlockFormatCombo(), paymentsController.getPinBlockFormatDecodeCombo(),
                        paymentsController.getPinBlockPaddingCombo(), paymentsController.getEncryptedPinBlockFormatCombo(),
                        paymentsController.getEncryptPinKeyField(), paymentsController.getDecryptPinKeyField());
                paymentsController.configurePinBlockHelp(contextualHelpState, this::showPinBlockHelpContext,
                        this::savePinBlockHelpContext);
                paymentsController.setPinBlockHelpActionTracker(this::trackPinBlockHelpAction);
            }
        }
    }

    private void loadSymmetricKeysContent() {
        try {
            if (keysContainerController == null) keysContainerController = ensureModule(keysContainer, KeysController.class);
            if (certificatesContainerController == null) certificatesContainerController = ensureModule(certificatesContainer, CertificatesController.class);
            keysController = keysContainerController;
            if (keysController == null) return;
            keysController.init(this, () -> {
                if (cipherController != null) cipherController.refreshHsmKeys();
                if (authenticationContainerController != null) authenticationContainerController.refreshHsmKeys();
            });

            if (certificatesContainerController != null) {
                certificatesContainerController.init(this, keysController);
            }

            System.out
                    .println("KeysController (with TR-31 + Asymmetric + Certificates + CMS) initialized successfully!");
        } catch (Exception e) {
            System.err.println("Error initializing KeysController: " + e.getMessage());
            LOG.error("Modern UI operation failed", e);
        }
    }

    public KeysController getKeysController() {
        return keysController;
    }

    /** Opens Symmetric Cipher with the selected Key Lab entry bound by reference. */
    public void useLabKeyInSymmetricCipher(String keyId) {
        if (cipherController == null) {
            throw new IllegalStateException("Symmetric Cipher workspace is not available");
        }
        cipherController.selectLabKey(keyId);
        navigateTo("Symmetric Ciphers");
    }

    /** Opens MAC with the selected Key Lab entry bound by reference. */
    public void useLabKeyInMac(String keyId) {
        if (authenticationContainerController == null) {
            throw new IllegalStateException("MAC workspace is not available");
        }
        authenticationContainerController.selectLabKey(keyId);
        navigateTo("Message Authentication Codes");
    }

    // ============================================================
    // EVENT HANDLERS - Symmetric Keys Operations
    // ============================================================




    // ============================================================================
    // KEY HANDLERS (Delegates)
    // ============================================================================

    // ============================================================================
    // ASN.1 DECODER
    // ============================================================================

    // ============================================================
    // NAVIGATION HANDLERS
    // ============================================================

    @Override
    public void navigateTo(String operation) { navigationRouter.handleItemSelected(operation); }

    @Override public void setInputFormat(String format) { if (navigationChrome != null) navigationChrome.setInputFormat(format); }
    @Override public void setOutputFormat(String format) { if (navigationChrome != null) navigationChrome.setOutputFormat(format); }
    static String normalizeToolbarFormat(String format) { return com.cryptocarver.model.FormatProfilePolicy.normalize(format); }

    public void navigateToModule(String moduleName) { navigationRouter.handleItemSelected(moduleName); }

    private void openBatchRecordOperation(String operation) {
        navigateToModule("Batch Runner");
        if (genericContainerController != null) genericContainerController.selectBatchRecordOperation(operation);
    }

    public void openBatchEncryptRecord() { openBatchRecordOperation("Encrypt Record"); }
    public void openBatchDecryptRecord() { openBatchRecordOperation("Decrypt Record"); }

    /** Opens the integrated Shelf view and refreshes its in-session contents. */
    @FXML
    public void handleOpenClipboardShelf() {
        navigateToModule("Clipboard Shelf");
    }

    /** Uses a session-only private-key entry without exposing it to other targets. */
    public void loadSessionOnlyPrivateKey(com.cryptocarver.model.ClipboardEntry entry) {
        if (entry == null || !entry.isSessionOnlyPrivateKey()) {
            updateStatus("Action blocked: only session-only private-key entries can be reused here.");
            return;
        }
        if (!AppSettings.isFullLab()) {
            updateStatus("Action blocked: session-only private keys require FULL_LAB.");
            return;
        }
        navigateToModule("Key & Certificate Format Workbench");
        if (genericContainerController != null && genericContainerController.getKeyCertificateWorkbenchController() != null) {
            genericContainerController.getKeyCertificateWorkbenchController().loadSessionOnlyPrivateKey(entry.getValue());
        }
    }

    /** Opens the signatures workspace with a generated laboratory key pair prepared, without executing it. */
    public void useGeneratedKeyPairInSignatures(java.security.KeyPair keyPair, String publicPem, String privatePem) {
        handleItemSelected("Digital Signatures");
        if (authenticationContainerController != null) {
            authenticationContainerController.loadGeneratedKeyPair(keyPair, publicPem, privatePem);
        }
    }

    public void handleItemSelected(String itemName) { navigationRouter.handleItemSelected(itemName); }

    private void handleItemSelectedImpl(String itemName) {
        String requestedItem = itemName;
        itemName = com.cryptocarver.model.OperationRegistry.getInstance()
                .resolveNavigation(itemName)
                .map(com.cryptocarver.model.OperationDescriptor::getNavigationPath)
                .orElse(itemName);

        this.currentActiveOperation = itemName;
        if (!"Process Designer".equals(itemName)) {
            exitProcessDesignerWorkspace();
        }
        if (navigationController != null) navigationController.navigate(itemName);

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
        HelpContext currentHelp = contextualHelpState.current();
        boolean keepFormatHelp = "aux_global_formats".equals(currentHelp.operationId())
                && java.util.Set.of("input", "output", "contract").contains(currentHelp.actionId());
        if (keepFormatHelp) {
            showGlobalFormatHelp(currentHelp.actionId(), currentHelp.fieldId());
        } else {
            contextualHelpCoordinator().showOperation(itemName);
            if (contextualHelpWindowCoordinator != null) contextualHelpWindowCoordinator.updateOperation(itemName);
            if (isJweRoute(itemName) && savedJweHelpContext != null) {
                contextualHelpState.setAction(savedJweHelpContext.actionId());
                contextualHelpState.setField(savedJweHelpContext.fieldId());
            }
            String cipherId = cipherOperationId(itemName);
            HelpContext savedCipher = savedCipherHelpContexts.get(cipherId);
            if (!cipherId.isBlank() && savedCipher != null) {
                if ("op_sym_file".equals(cipherId)) {
                    savedCipher = reconcileFileCipherHelpContext(savedCipher, false);
                    savedCipherHelpContexts.put(cipherId, savedCipher);
                    contextualHelpState.setSelection(HelpContext.Selection.FILE_LINES,
                            HelpContext.PublicSelection.flag(cipherContainerController != null
                                    && cipherContainerController.getFileCipherLinesCheck().isSelected()));
                }
                contextualHelpState.setAction(savedCipher.actionId());
                contextualHelpState.setField(savedCipher.fieldId());
            }
            String pinOperationId = pinHelpOperationId(itemName);
            if (!pinOperationId.isBlank()) restorePinBlockHelpContext(pinOperationId, currentHelp);
        }
        if (AppSettings.getInstance().isContextualHelpEnabled() && inspectorTabs != null) {
            inspectorTabs.getSelectionModel().select(inspectorHelpTab);
            if (mainPane != null && mainPane.getWidth() >= COMPACT_LAYOUT_WIDTH && inspectorPanel != null) {
                inspectorPanel.setVisible(true);
                inspectorPanel.setManaged(true);
                inspectorHiddenForCompactLayout = false;
            }
        }

        // Update header
        updateContentHeader(itemName);

        // Update inspector
        updateInspector(itemName);

        if (!activateNavigationRoute(itemName)) {
            showPlaceholderContent(itemName);
        }
        updateHelpContextAdapters(itemName);
        installModuleHelp(itemName);

        updateStatus("Loaded: " + itemName);
        // An untouched form is naturally incomplete. Do not make that the
        // first thing users see; reveal the checklist after an edit, a real
        // warning, or an attempted execution.
        readinessPanelCoordinator().onOperationSelected();
    }

    private boolean activateNavigationRoute(String operation) { return navigationRouter.activate(operation); }

    private void updateContentHeader(String itemName) {
        if (navigationChrome != null) navigationChrome.updateHeader(itemName);
    }

    private void restoreStartupLastRoute() { navigationRouter.restoreStartupLastRoute(() -> com.cryptocarver.model.AppSettings.getInstance().getLastRoute(), this::navigateToModule); }

    private void updateBreadcrumbs(String operationName) {
        if (navigationChrome != null) navigationChrome.updateBreadcrumbOnly(operationName);
    }

    @FXML
    public void handleBreadcrumbSectionClick() {
        if (navigationChrome != null) navigationChrome.handleBreadcrumbSectionClick();
        else if (breadcrumbSectionBtn != null) selectBreadcrumbSection(breadcrumbSectionBtn.getUserData());
    }

    private void selectBreadcrumbSection(Object sectionTarget) {
        if (sectionTarget instanceof UiNavigationRegistry.Module module) {
            switch (module) {
                case KEYS_SYMMETRIC, KEYS_ASYMMETRIC -> navigationRail.selectSection(NavigationRail.Section.KEYS);
                case CIPHER -> navigationRail.selectSection(NavigationRail.Section.CIPHER);
                case AUTHENTICATION -> navigationRail.selectSection(NavigationRail.Section.AUTHENTICATION);
                case CERTIFICATES -> navigationRail.selectSection(NavigationRail.Section.CERTIFICATES);
                case JOSE -> navigationRail.selectSection(NavigationRail.Section.JOSE);
                case COSE -> navigationRail.selectSection(NavigationRail.Section.COSE);
                case WALLET -> navigationRail.selectSection(NavigationRail.Section.WALLET);
                case POST_QUANTUM -> navigationRail.selectSection(NavigationRail.Section.POST_QUANTUM);
                case XML_SECURITY, WSS_SECURITY -> navigationRail.selectSection(NavigationRail.Section.XML_SECURITY);
                case EMV, PAYMENTS -> navigationRail.selectSection(NavigationRail.Section.PAYMENTS);
                case HISTORY -> navigationRail.selectSection(NavigationRail.Section.HISTORY);
                case PROCESS_DESIGNER -> navigationRail.selectSection(NavigationRail.Section.PROCESS_DESIGNER);
                case GENERIC, EPOCH_CONVERTER, JSON_FORMATTER, CLIPBOARD_SHELF ->
                        navigationRail.selectSection(NavigationRail.Section.GENERIC);
                case SAVED_SESSIONS -> navigationRail.selectSection(NavigationRail.Section.HISTORY);
            }
            return;
        }
        if (navigationController != null) navigationController.navigate(currentActiveOperation);
    }

    @FXML
    public void handleBreadcrumbModuleClick() {
        if (navigationChrome != null) navigationChrome.handleBreadcrumbModuleClick();
        else if (breadcrumbModuleBtn != null) navigateToModule(breadcrumbModuleBtn.getText());
    }

    public void reopenRecentHistoryCommand(com.cryptocarver.model.HistoryCommand item) { shellHistoryCoordinator().reopenHistoryOperation(item); }

    /**
     * Opens a recorded execution for inspection. Selecting an entry under
     * "Recent Executions" is a view action; restoring its recipe remains the
     * explicit job of the Reopen button in the History view.
     */
    public void showRecentHistoryCommand(com.cryptocarver.model.HistoryCommand item) {
        shellHistoryCoordinator().showRecentHistoryCommand(item);
    }

    @Override
    public void reopenHistoryOperation(com.cryptocarver.model.HistoryCommand item) {
        shellHistoryCoordinator().reopenHistoryOperation(item);
    }

    private java.util.List<com.cryptocarver.model.OperationDetail> visibleHistoryDetails(
            com.cryptocarver.model.HistoryCommand item) {
        return shellHistoryCoordinator().visibleHistoryDetails(item);
    }

    private java.util.List<com.cryptocarver.model.OperationDetail> visibleOperationDetails(
            java.util.List<com.cryptocarver.model.OperationDetail> details) {
        com.cryptocarver.model.SecretVisibilityProfile visibility =
                com.cryptocarver.model.AppSettings.getInstance().getSecretVisibilityProfile();
        return details.stream().filter(java.util.Objects::nonNull).map(detail -> {
            String value = detail.value();
            if (visibility == com.cryptocarver.model.SecretVisibilityProfile.REDACTED
                    && detail.classification() == com.cryptocarver.model.OperationDetail.Classification.SECRET) {
                value = "***REDACTED***";
            } else if (visibility == com.cryptocarver.model.SecretVisibilityProfile.MASKED
                    && detail.classification() != com.cryptocarver.model.OperationDetail.Classification.PUBLIC) {
                value = "***MASKED***";
            } else if (visibility == com.cryptocarver.model.SecretVisibilityProfile.REDACTED
                    && detail.classification() == com.cryptocarver.model.OperationDetail.Classification.SENSITIVE) {
                value = "***MASKED***";
            }
            return new com.cryptocarver.model.OperationDetail(detail.name(), value,
                    detail.classification(), detail.multiline(), detail.format());
        }).toList();
    }

    @FXML public void handleToggleFavorite() {
        if (currentActiveOperation == null || currentActiveOperation.isBlank()) return;
        if (navigationChrome != null) navigationChrome.toggleFavorite(currentActiveOperation);
        if (sidePanel != null && sidePanel.isVisible()) sidePanel.updateContent(sidePanel.getCurrentSection());
    }
    private void updateFavoriteToggleState(String operationName) {
        if (navigationChrome != null) navigationChrome.updateFavoriteOnly(operationName);
    }

    @FXML
    public void handleQuickStart() {
        showQuickStart();
    }

    private void updateContentSubtitle(String subtitle) { if (navigationChrome != null) navigationChrome.updateSubtitle(subtitle); }

    // deleted duplicate cmsKeyArea and syntax error

    private void updateInspector(String operation) {
        updateInspector(operation, null, null, (java.util.List<com.cryptocarver.model.OperationDetail>) null);
    }

    public void updateInspector(String operation, byte[] input, byte[] output, java.util.Map<String, String> details) {
        java.util.List<com.cryptocarver.model.OperationDetail> list = new java.util.ArrayList<>();
        if (details != null) {
            details.forEach((k, v) -> list.add(com.cryptocarver.model.OperationDetail.publicDetail(k, v)));
        }
        updateInspector(operation, input, output, list);
    }

    @Override
    public void updateInspector(String operation, byte[] input, byte[] output, java.util.List<com.cryptocarver.model.OperationDetail> details) {
        java.util.List<com.cryptocarver.model.OperationDetail> visibleDetails =
                details == null ? null : visibleOperationDetails(details);
        inspectorPresenter().present(operation, input, output, visibleDetails);

        if (visibleDetails != null && inlineErrorPresenter != null) {
            boolean isInvalidResult = visibleDetails.stream().anyMatch(d ->
                    "Result".equalsIgnoreCase(d.name()) && d.value() != null && d.value().toUpperCase().contains("INVALID"));
            if (!isInvalidResult) {
                bannerHelpContext = null;
                inlineErrorPresenter.hideBanner();
            }
        }
    }

    private OperationInspectorPresenter inspectorPresenter() {
        if (inspectorPresenter == null) {
            inspectorPresenter = new OperationInspectorPresenter(operationLabel, inputBytesLabel, outputBytesLabel,
                    securityTipLabel, inspectorDetailsContainer);
        }
        return inspectorPresenter;
    }

    private ContextualHelpCoordinator contextualHelpCoordinator() {
        if (contextualHelpCoordinator == null) {
            contextualHelpCoordinator = new ContextualHelpCoordinator(contextualHelpContent, i18n, contextualHelpState);
            contextualHelpCoordinator.setActionSelectionListener(this::handleContextualHelpAction);
        }
        return contextualHelpCoordinator;
    }

    private void updateHelpContextAdapters(String route) {
        if (genericContainerController != null) {
            genericContainerController.setActiveContextualHelpOperation(genericHelpOperationId(route));
        }
        if (clipboardShelfController != null) clipboardShelfController.setActiveContextualHelpOperation(
                "Clipboard Shelf".equals(route) ? "op_gen_clipboard" : "",
                "Clipboard Shelf".equals(route) && AppSettings.getInstance().isContextualHelpEnabled());
        if (processDesignerContainerController != null) processDesignerContainerController.setActiveContextualHelpOperation(
                "Process Designer".equals(route) ? "op_gen_process_designer" : "",
                "Process Designer".equals(route) && AppSettings.getInstance().isContextualHelpEnabled());
        String cipherOperation = cipherOperationId(route);
        boolean cipherRoute = !cipherOperation.isBlank();
        boolean symmetricCipher = "op_sym_ciphers".equals(cipherOperation);
        if (cipherHelpContextAdapter != null) {
            if (cipherRoute) {
                cipherHelpContextAdapter.activate();
                cipherHelpContextAdapter.refreshCurrentSelections();
            }
            else cipherHelpContextAdapter.deactivate();
        }
        if (cipherContainerController != null) {
            cipherContainerController.setContextualHelpOperationActive(cipherOperation);
            cipherContainerController.setContextualHelpEnabled(cipherRoute && AppSettings.getInstance().isContextualHelpEnabled());
            if ("op_asym_ciphers".equals(cipherOperation))
                cipherContainerController.refreshRsaHelpContextForCurrentRoute();
        }
        if (cipherContainerController != null) cipherContainerController.setContextualHelpRouteActive(symmetricCipher);
        boolean jwe = isJweRoute(route);
        if (jweHelpContextAdapter != null) {
            if (jwe) jweHelpContextAdapter.activate();
            else jweHelpContextAdapter.deactivate();
        }
        boolean jweHelpEnabled = jwe && AppSettings.getInstance().isContextualHelpEnabled();
        if (joseController != null) joseController.setJweContextHelp(jwe, jweHelpEnabled);
        if (resultActionHelp != null) {
            resultActionHelp.setActiveRoute(jwe ? "op_jose_jwe" : "");
            resultActionHelp.setEnabled(jweHelpEnabled);
        }
        boolean pinRoute = isPinBlockRoute(route);
        if (pinBlockHelpContextAdapter != null) {
            if (pinRoute) pinBlockHelpContextAdapter.activate(isClearPinRoute(route) ? "op_pay_clear_pin" : "op_pay_enc_pin");
            else pinBlockHelpContextAdapter.deactivate();
        }
        if (paymentsController != null) paymentsController.setPinBlockContextHelpActive(
                pinRoute ? (isClearPinRoute(route) ? "op_pay_clear_pin" : "op_pay_enc_pin") : "",
                pinRoute && AppSettings.getInstance().isContextualHelpEnabled());
    }

    private String genericHelpOperationId(String route) {
        if ("UUID Generator".equals(route)) return "aux_uuid";
        return com.cryptocarver.model.OperationRegistry.getInstance().getByNavigationPath(route)
                .map(operation -> com.cryptocarver.model.help.B05HelpCatalog.supports(operation.getId())
                        ? operation.getId() : "")
                .orElse("");
    }

    private void showGenericHelpContext(HelpContext context) {
        String activeGenericRoute = genericContainerController == null
                ? genericHelpOperationId(currentActiveOperation)
                : genericContainerController.activeContextualHelpRoute();
        if (context == null || !context.operationId().equals(activeGenericRoute)) return;
        showCurrentHelpContext();
    }

    private String cipherOperationId(String route) {
        return com.cryptocarver.model.OperationRegistry.getInstance().getByNavigationPath(route)
                .map(operation -> switch (operation.getId()) {
                    case "op_sym_ciphers", "op_fpe", "op_sym_file", "op_openpgp", "op_asym_ciphers" -> operation.getId();
                    default -> "";
                }).orElse("");
    }

    private static int hexByteLength(String raw) {
        String value = raw == null ? "" : raw.replaceAll("\\s+", "");
        return value.isEmpty() || (value.length() & 1) != 0 || !value.matches("[0-9A-Fa-f]+")
                ? -1 : value.length() / 2;
    }

    private boolean isJweRoute(String route) {
        return com.cryptocarver.model.OperationRegistry.getInstance().getByNavigationPath(route)
                .map(operation -> "op_jose_jwe".equals(operation.getId())).orElse(false);
    }

    private boolean isPinBlockRoute(String route) {
        return com.cryptocarver.model.OperationRegistry.getInstance().getByNavigationPath(route)
                .map(operation -> "op_pay_clear_pin".equals(operation.getId()) || "op_pay_enc_pin".equals(operation.getId()))
                .orElse(false);
    }

    private void showPinBlockHelpContext(HelpContext context) {
        if (context == null || !isPinBlockRoute(currentActiveOperation)
                || !pinHelpOperationId(currentActiveOperation).equals(context.operationId())) return;
        savePinBlockHelpContext(contextualHelpState.current());
        showCurrentHelpContext();
    }

    private void trackPinBlockHelpAction(String actionId) {
        if (!AppSettings.getInstance().isContextualHelpEnabled() || !isPinBlockRoute(currentActiveOperation)) return;
        String operation = isClearPinRoute(currentActiveOperation) ? "op_pay_clear_pin" : "op_pay_enc_pin";
        contextualHelpState.setOperation(operation);
        contextualHelpState.setAction(actionId == null ? "" : actionId);
        contextualHelpState.setField("");
        savePinBlockHelpContext(contextualHelpState.current());
    }

    private boolean isClearPinRoute(String route) {
        return com.cryptocarver.model.OperationRegistry.getInstance().getByNavigationPath(route)
                .map(operation -> "op_pay_clear_pin".equals(operation.getId())).orElse(false);
    }

    private String pinHelpOperationId(String route) {
        if (isClearPinRoute(route)) return "op_pay_clear_pin";
        if (com.cryptocarver.model.OperationRegistry.getInstance().getByNavigationPath(route)
                .map(operation -> "op_pay_enc_pin".equals(operation.getId())).orElse(false)) return "op_pay_enc_pin";
        return "";
    }

    private boolean isValidPinBlockHelpContext(HelpContext candidate, String operationId) {
        if (candidate == null || !operationId.equals(candidate.operationId())) return false;
        if (candidate.actionId().isBlank()) return candidate.fieldId().isBlank();
        var action = com.cryptocarver.model.help.OperationHelpRegistry.getInstance().find(operationId)
                .flatMap(definition -> definition.actions().stream()
                        .filter(item -> item.id().equals(candidate.actionId())).findFirst()).orElse(null);
        if (action == null) return false;
        if (candidate.fieldId().isBlank()) return true;
        return action.fields().stream().anyMatch(field -> field.id().equals(candidate.fieldId()))
                || action.options().stream().anyMatch(option -> option.fieldId().equals(candidate.fieldId()));
    }

    private void savePinBlockHelpContext(HelpContext candidate) {
        if (candidate == null || !isPinBlockRoute(currentActiveOperation)) return;
        String operationId = pinHelpOperationId(currentActiveOperation);
        if (isValidPinBlockHelpContext(candidate, operationId)) savedPinBlockHelpContexts.put(operationId, candidate);
    }

    /** Restores only action/field memory for this route; live selector state comes from its active adapter. */
    private void restorePinBlockHelpContext(String operationId, HelpContext preferred) {
        HelpContext selected = isValidPinBlockHelpContext(preferred, operationId) ? preferred
                : savedPinBlockHelpContexts.get(operationId);
        if (!isValidPinBlockHelpContext(selected, operationId)) selected = null;
        contextualHelpState.setOperation(operationId);
        contextualHelpState.setAction(selected == null ? "" : selected.actionId());
        contextualHelpState.setField(selected == null ? "" : selected.fieldId());
        if (selected != null) savedPinBlockHelpContexts.put(operationId, selected);
    }

    private void showJweHelpContext(HelpContext context) {
        if (context == null || !isJweRoute(currentActiveOperation)) return;
        savedJweHelpContext = contextualHelpState.current();
        showCurrentHelpContext();
    }

    private void rememberJweHelpContext(HelpContext context) {
        if (context != null && isJweRoute(currentActiveOperation)) savedJweHelpContext = context;
    }

    private void trackJweHelpAction(String actionId) {
        if (!AppSettings.getInstance().isContextualHelpEnabled() || !isJweRoute(currentActiveOperation)) return;
        contextualHelpState.setOperation("op_jose_jwe");
        contextualHelpState.setAction(actionId == null ? "" : actionId);
        contextualHelpState.setField("");
        savedJweHelpContext = contextualHelpState.current();
    }

    private void showCurrentHelpContext() {
        if (mainPane != null && mainPane.getWidth() > 0 && mainPane.getWidth() < COMPACT_LAYOUT_WIDTH) {
            if (contextualHelpWindowCoordinator == null) {
                contextualHelpWindowCoordinator = new ContextualHelpWindowCoordinator(i18n, contextualHelpState);
                contextualHelpWindowCoordinator.setActionSelectionListener(this::handleContextualHelpAction);
            }
            contextualHelpWindowCoordinator.showCurrent(mainPane.getScene() == null ? null : mainPane.getScene().getWindow());
        } else if (inspectorTabs != null) {
            inspectorPanel.setVisible(true); inspectorPanel.setManaged(true); inspectorHiddenForCompactLayout = false;
            inspectorTabs.getSelectionModel().select(inspectorHelpTab);
            contextualHelpCoordinator().refresh();
        }
    }

    private boolean isSymmetricCipherRouteActive() {
        return com.cryptocarver.model.OperationRegistry.getInstance()
                .getByNavigationPath(currentActiveOperation)
                .map(operation -> "op_sym_ciphers".equals(operation.getId())).orElse(false);
    }

    private void showSymmetricHelpContext(HelpContext help) {
        if (mainPane != null && mainPane.getWidth() > 0 && mainPane.getWidth() < COMPACT_LAYOUT_WIDTH) {
            if (contextualHelpWindowCoordinator == null) {
                contextualHelpWindowCoordinator = new ContextualHelpWindowCoordinator(i18n, contextualHelpState);
                contextualHelpWindowCoordinator.setActionSelectionListener(this::handleContextualHelpAction);
            }
            contextualHelpWindowCoordinator.showCurrent(mainPane.getScene() == null ? null : mainPane.getScene().getWindow());
        } else if (inspectorTabs != null) {
            inspectorPanel.setVisible(true); inspectorPanel.setManaged(true); inspectorHiddenForCompactLayout = false;
            inspectorTabs.getSelectionModel().select(inspectorHelpTab);
            contextualHelpCoordinator().refresh();
        }
    }

    private void showCipherModuleHelpContext(HelpContext help) {
        if (help == null) return;
        String routeOperation = cipherOperationId(currentActiveOperation);
        boolean direct = help.operationId().equals(routeOperation);
        boolean analyzer = "aux_encrypted_file_analysis".equals(help.operationId())
                && "op_sym_file".equals(routeOperation);
        if (!direct && !analyzer) return;
        if ("op_sym_file".equals(help.operationId()))
            help = reconcileFileCipherHelpContext(help, true);
        if ("op_asym_ciphers".equals(help.operationId()) && cipherContainerController != null) {
            int bits = cipherContainerController.getRsaPublicModulusBitsForHelp();
            help = help.withSelection(HelpContext.Selection.RSA_MODULUS_BITS,
                    bits > 0 ? HelpContext.PublicSelection.byteSize(bits) : null);
        }
        contextualHelpState.replace(help);
        if (!analyzer) savedCipherHelpContexts.put(help.operationId(), help);
        showCurrentHelpContext();
    }

    private HelpContext reconcileFileCipherHelpContext(HelpContext help, boolean preserveSavedDirectionFromAuxiliary) {
        if (help == null || !"op_sym_file".equals(help.operationId()) || cipherContainerController == null) return help;
        String action = help.actionId();
        boolean encrypt = action.startsWith("encrypt_");
        boolean decrypt = action.startsWith("decrypt_");
        if (!encrypt && !decrypt) return help;
        String direction = decrypt ? "decrypt" : "encrypt";
        boolean fieldQuery = java.util.Set.of("key", "iv_nonce", "aad", "result").contains(help.fieldId());
        if (fieldQuery && preserveSavedDirectionFromAuxiliary && encrypt) {
            HelpContext saved = savedCipherHelpContexts.get("op_sym_file");
            if (saved != null && saved.actionId().startsWith("decrypt_")) direction = "decrypt";
            else if (saved != null && saved.actionId().startsWith("encrypt_")) direction = "encrypt";
        }
        boolean lines = cipherContainerController.getFileCipherLinesCheck().isSelected();
        return help.withAction(direction + (lines ? "_lines" : "_file"))
                .withSelection(HelpContext.Selection.FILE_LINES, HelpContext.PublicSelection.flag(lines));
    }

    /** Updates an already-selected module context without opening a hidden help surface. */
    private void updateCipherModuleHelpContext(HelpContext help) {
        if (help == null || !AppSettings.getInstance().isContextualHelpEnabled()) return;
        if ("op_sym_file".equals(help.operationId())) help = reconcileFileCipherHelpContext(help, true);
        contextualHelpState.replace(help);
        if (help.operationId().startsWith("op_")) savedCipherHelpContexts.put(help.operationId(), help);
    }

    @FXML private void handleInputFormatHelp() { showGlobalFormatHelp("input", "inputFormatCombo"); }
    @FXML private void handleOutputFormatHelp() { showGlobalFormatHelp("output", "outputFormatCombo"); }
    @FXML private void handleFormatContractHelp() { showGlobalFormatHelp("contract", "contractOperationLabel"); }

    private void showGlobalFormatHelp(String action, String fieldId) {
        String profileOperation = com.cryptocarver.model.FormatProfilePolicy.operation(currentActiveOperation);
        String operationId = com.cryptocarver.model.OperationRegistry.getInstance()
                .getByNavigationPath(profileOperation)
                .map(com.cryptocarver.model.OperationDescriptor::getId).orElse("format_fallback");
        java.util.EnumMap<HelpContext.Selection, HelpContext.PublicSelection> selections =
                new java.util.EnumMap<>(HelpContext.Selection.class);
        selections.put(HelpContext.Selection.PROFILE, HelpContext.PublicSelection.of(operationId));
        String selected = "input".equals(action)
                ? inputFormatCombo == null ? null : inputFormatCombo.getValue()
                : "output".equals(action) ? outputFormatCombo == null ? null : outputFormatCombo.getValue() : null;
        if (isAllowedFormat(operationId, action, selected)) {
            selections.put(HelpContext.Selection.FORMAT,
                    HelpContext.PublicSelection.of(com.cryptocarver.model.help.OperationHelpRegistry.slug(selected)));
        }
        HelpContext help = new HelpContext("aux_global_formats", action, "", i18n.getLocale(), fieldId, selections);
        showGlobalFormatHelpContext(help);
    }

    private boolean isAllowedFormat(String operationId, String action, String format) {
        if (format == null) return false;
        var descriptor = com.cryptocarver.model.OperationRegistry.getInstance().getById(operationId).orElse(null);
        String route = descriptor == null ? "Fallback" : descriptor.getNavigationPath();
        var profile = com.cryptocarver.model.OperationFormatRegistry.getInstance().getProfile(route);
        return "input".equals(action) ? profile.allowedInputFormats().contains(format)
                : profile.allowedOutputFormats().contains(format);
    }

    private void refreshOpenFormatHelp(String direction) {
        HelpContext current = contextualHelpState.current();
        if (!"aux_global_formats".equals(current.operationId()) || !direction.equals(current.actionId())) return;
        showGlobalFormatHelp(direction, "input".equals(direction) ? "inputFormatCombo" : "outputFormatCombo");
    }

    private void showGlobalFormatHelpContext(HelpContext help) {
        if (mainPane != null && mainPane.getWidth() > 0 && mainPane.getWidth() < COMPACT_LAYOUT_WIDTH) {
            contextualHelpState.replace(help);
            if (contextualHelpWindowCoordinator == null) {
                contextualHelpWindowCoordinator = new ContextualHelpWindowCoordinator(i18n, contextualHelpState);
                contextualHelpWindowCoordinator.setActionSelectionListener(this::handleContextualHelpAction);
            }
            contextualHelpWindowCoordinator.showCurrent(mainPane.getScene() == null ? null : mainPane.getScene().getWindow());
        } else if (inspectorTabs != null) {
            inspectorPanel.setVisible(true); inspectorPanel.setManaged(true); inspectorHiddenForCompactLayout = false;
            inspectorTabs.getSelectionModel().select(inspectorHelpTab);
            contextualHelpState.replace(help);
            contextualHelpCoordinator().refresh();
        }
    }

    @FXML
    public void handleShowOperationHelp() {
        if (mainPane == null || inspectorTabs == null) return;
        HelpContext requestedHelpContext = contextualHelpState.current();
        if (mainPane.getWidth() > 0 && mainPane.getWidth() < COMPACT_LAYOUT_WIDTH) {
            if (contextualHelpWindowCoordinator == null) {
                contextualHelpWindowCoordinator = new ContextualHelpWindowCoordinator(i18n, contextualHelpState);
                contextualHelpWindowCoordinator.setActionSelectionListener(this::handleContextualHelpAction);
            }
            contextualHelpWindowCoordinator.show(mainPane.getScene() == null ? null : mainPane.getScene().getWindow(), currentActiveOperation);
        } else {
            inspectorPanel.setVisible(true);
            inspectorPanel.setManaged(true);
            inspectorHiddenForCompactLayout = false;
            inspectorTabs.getSelectionModel().select(inspectorHelpTab);
            contextualHelpCoordinator().showOperation(currentActiveOperation);
        }
        if (isSymmetricCipherRouteActive() && cipherContainerController != null) {
            cipherContainerController.publishCurrentHelpSelections();
        }
        if (isJweRoute(currentActiveOperation) && jweHelpContextAdapter != null) {
            jweHelpContextAdapter.publishCurrentSelections();
            if (savedJweHelpContext != null) {
                contextualHelpState.setAction(savedJweHelpContext.actionId());
                contextualHelpState.setField(savedJweHelpContext.fieldId());
            }
        }
        if (isPinBlockRoute(currentActiveOperation) && pinBlockHelpContextAdapter != null) {
            restorePinBlockHelpContext(pinHelpOperationId(currentActiveOperation), requestedHelpContext);
            pinBlockHelpContextAdapter.publishCurrentSelections();
        }
    }

    @FXML
    public void handleToggleContextualHelpMode() {
        boolean enabled = contextualHelpModeMenuItem != null && contextualHelpModeMenuItem.isSelected();
        AppSettings.getInstance().setContextualHelpEnabled(enabled);
        if (contextualHelpModeMenuItem != null) contextualHelpModeMenuItem.setSelected(enabled);
        if (cipherContainerController != null) cipherContainerController.setContextualHelpEnabled(
                enabled && !cipherOperationId(currentActiveOperation).isBlank());
        if (cipherContainerController != null) cipherContainerController.setContextualHelpOperationActive(
                cipherOperationId(currentActiveOperation));
        if (joseController != null) joseController.setJweContextHelp(isJweRoute(currentActiveOperation),
                enabled && isJweRoute(currentActiveOperation));
        if (resultActionHelp != null) resultActionHelp.setEnabled(enabled && isJweRoute(currentActiveOperation));
        if (paymentsController != null) paymentsController.setPinBlockContextHelpActive(
                isPinBlockRoute(currentActiveOperation)
                        ? (isClearPinRoute(currentActiveOperation) ? "op_pay_clear_pin" : "op_pay_enc_pin") : "",
                enabled && isPinBlockRoute(currentActiveOperation));
        if (genericContainerController != null) genericContainerController.setActiveContextualHelpOperation(genericHelpOperationId(currentActiveOperation));
        if (clipboardShelfController != null) clipboardShelfController.setActiveContextualHelpOperation(
                "Clipboard Shelf".equals(currentActiveOperation) ? "op_gen_clipboard" : "",
                enabled && "Clipboard Shelf".equals(currentActiveOperation));
        if (processDesignerContainerController != null) processDesignerContainerController.setActiveContextualHelpOperation(
                "Process Designer".equals(currentActiveOperation) ? "op_gen_process_designer" : "",
                enabled && "Process Designer".equals(currentActiveOperation));
        if (utilityToolsCoordinator != null) utilityToolsCoordinator.setContextualHelpEnabled(enabled);
        if (moduleHelp != null) moduleHelp.setEnabled(enabled);
        updateStatus(i18n.text(enabled ? "help.mode.enabled" : "help.mode.disabled"));
        if (enabled) handleShowOperationHelp();
    }

    private void handleContextualHelpAction(String actionId) {
        HelpContext activeHelp = contextualHelpState.current();
        if (com.cryptocarver.model.help.OperationHelpRegistry.getInstance().definitions().stream()
                .anyMatch(definition -> definition.operationId().equals(activeHelp.operationId()))) {
            String operationId = activeHelp.operationId();
            if (operationId.startsWith("op_") && !operationId.equals("op_sym_ciphers"))
                savedCipherHelpContexts.put(operationId, activeHelp);
        }
        if (isPinBlockRoute(currentActiveOperation)) savePinBlockHelpContext(contextualHelpState.current());
        if (isJweRoute(currentActiveOperation) && "op_jose_jwe".equals(contextualHelpState.current().operationId())) {
            savedJweHelpContext = contextualHelpState.current();
        }
        if (!"aux_preflight_error".equals(contextualHelpState.current().operationId())) return;
        switch (actionId) {
            case "go_to_field" -> focusControl(contextualHelpState.current().fieldId());
            case "copy_diagnostic" -> {
                if (inlineErrorPresenter != null && inlineErrorPresenter.isVisible()
                        && inlineErrorPresenter.getCurrentError() != null) {
                    inlineErrorPresenter.copyTechnicalDetails(this);
                }
            }
            default -> { }
        }
    }

    /** Shell copy/expand result actions share the JWE help route: help follows their focus too. */
    private void installResultActionHelp() {
        if (resultActionHelp != null) return;
        resultActionHelp = new ContextualHelpButtonBinder(contextualHelpState, this::showJweHelpContext,
                this::rememberJweHelpContext);
        if (resultCopyButton != null) resultActionHelp.bind("op_jose_jwe", "op_jose_jwe", "copy_result", "output", resultCopyButton);
        if (resultExpandButton != null) resultActionHelp.bind("op_jose_jwe", "op_jose_jwe", "expand_result", "output", resultExpandButton);
        boolean jwe = isJweRoute(currentActiveOperation);
        resultActionHelp.setActiveRoute(jwe ? "op_jose_jwe" : "");
        resultActionHelp.setEnabled(jwe && AppSettings.getInstance().isContextualHelpEnabled());
    }

    /** Modules declared in modules.spec get focus-following help from the shell, without controller wiring. */
    private void installModuleHelp(String route) {
        String operationId = com.cryptocarver.model.OperationRegistry.getInstance().getByNavigationPath(route)
                .map(com.cryptocarver.model.OperationDescriptor::getId).orElse("");
        if (moduleHelp == null) moduleHelp = new ModuleHelpInstaller(contextualHelpState, ignored -> showCurrentHelpContext());
        moduleHelp.activate(operationId, rootStackPane != null ? rootStackPane : mainPane,
                AppSettings.getInstance().isContextualHelpEnabled());
    }

    private void showPreflightHelpContext(HelpContext context) {
        if (mainPane != null && mainPane.getWidth() > 0 && mainPane.getWidth() < COMPACT_LAYOUT_WIDTH) {
            contextualHelpState.replace(context);
            if (contextualHelpWindowCoordinator == null) {
                contextualHelpWindowCoordinator = new ContextualHelpWindowCoordinator(i18n, contextualHelpState);
                contextualHelpWindowCoordinator.setActionSelectionListener(this::handleContextualHelpAction);
            }
            contextualHelpWindowCoordinator.showCurrent(mainPane.getScene() == null ? null : mainPane.getScene().getWindow());
        } else if (inspectorTabs != null) {
            inspectorPanel.setVisible(true); inspectorPanel.setManaged(true); inspectorHiddenForCompactLayout = false;
            inspectorTabs.getSelectionModel().select(inspectorHelpTab);
            // Selecting the Help tab can invoke the ordinary route-help listener; restore
            // the auxiliary preflight context after that navigation callback runs.
            contextualHelpState.replace(context);
            contextualHelpCoordinator().refresh();
        }
        if (!context.fieldId().isBlank()) focusControl(context.fieldId());
    }

    private void showGenericErrorHelp() {
        HelpContext context = new HelpContext("aux_preflight_error", "details", "", i18n.getLocale(), "", java.util.Map.of());
        if (inlineErrorPresenter != null && inlineErrorPresenter.getCurrentError() != null) {
            String field = inlineErrorPresenter.getCurrentError().fieldKey();
            if (field != null && field.matches("[A-Za-z][A-Za-z0-9_.-]{0,127}")) context = context.withField(field);
        }
        showPreflightHelpContext(context);
    }

    @FXML
    private void handleErrorBannerHelp() {
        if (inlineErrorPresenter != null && inlineErrorPresenter.isVisible()
                && inlineErrorPresenter.getCurrentError() != null && bannerHelpContext != null) {
            showPreflightHelpContext(bannerHelpContext);
            return;
        }
        showGenericErrorHelp();
    }

    @FXML
    private void handleShowReadinessHelp() { readinessPanelCoordinator().showReadinessHelp(); }

    // State management for history Rerun
    private java.util.Map<String, Object> captureUIState() {
        return UiStateSnapshot.capture(this);
    }

    private java.util.Map<String, Object> captureHistoryState() {
        return UiStateSnapshot.captureHistoryRecipe(this);
    }

    com.cryptocarver.model.ScreenConfiguration captureActiveScreenConfiguration() {
        return screenConfigurationCoordinator().captureActiveScreenConfiguration();
    }

    void applyScreenConfiguration(com.cryptocarver.model.ScreenConfiguration configuration) {
        screenConfigurationCoordinator().applyScreenConfiguration(configuration);
    }

    ScreenConfigurationCoordinator screenConfigurationCoordinator() {
        return new ScreenConfigurationCoordinator(() -> currentActiveOperation,
                () -> inputFormatCombo, () -> outputFormatCombo,
                () -> com.cryptocarver.model.AppSettings.getInstance().getSecretVisibilityProfile(),
                module -> {
                    Object controller = resolveNavigationController(module);
                    javafx.scene.Node host = navigationHost(module);
                    return controller != null && host instanceof Parent root
                            ? new ScreenConfigurationCoordinator.ConfigurationTarget(controller, root) : null;
                }, this::handleItemSelected, state -> UiStateSnapshot.restore(this, state),
                () -> windowOf(mainPane), dialogService, i18n, this::updateStatus);
    }

    private java.util.List<javafx.scene.Node> restoreUIState(java.util.Map<String, Object> state) {
        return UiStateSnapshot.restore(this, state);
    }

    // History Managemen

    /** The shared history store, created on first use. */
    private com.cryptocarver.model.HistoryManager historyManager() {
        if (historyManager == null) {
            historyManager = new com.cryptocarver.model.HistoryManager();
        }
        return historyManager;
    }

    private ShellHistoryCoordinator shellHistoryCoordinator() {
        if (shellHistoryCoordinator == null) {
            shellHistoryCoordinator = new ShellHistoryCoordinator(new ShellHistoryCoordinator.View(
                    this::historyManager, () -> sidePanel, () -> historyViewController,
                    this::captureHistoryState, () -> currentActiveOperation,
                    () -> inputFormatCombo == null ? null : inputFormatCombo.getValue(),
                    () -> outputFormatCombo == null ? null : outputFormatCombo.getValue(),
                    this::navigateToModule, this::handleItemSelected, () -> this,
                    (operation, details) -> updateInspector(operation, null, null, details),
                    this::visibleOperationDetails, () -> this, () -> windowOf(mainPane),
                    dialogService, i18n, () -> i18n.text("status.noActiveOperation"),
                    () -> i18n.text("status.inputCleared"),
                    () -> isContainerVisible(emvContainer) && emvController != null,
                    () -> () -> emvController.handleClear(),
                    () -> isContainerVisible(cipherContainer) && cipherController != null,
                    () -> () -> cipherController.handleClear(),
                    () -> isContainerVisible(authenticationContainer) && authenticationContainerController != null,
                    () -> () -> authenticationContainerController.handleClear(),
                    () -> isContainerVisible(keysContainer) && keysController != null,
                    () -> () -> { if (keysController.isSymmetricSectionVisible()) keysController.handleClear();
                        else keysController.handleClearAsymmetric(); },
                    () -> isContainerVisible(genericContainer) && genericContainerController != null,
                    () -> () -> genericContainerController.handleClear(),
                    () -> isContainerVisible(processDesignerContainer) && processDesignerContainerController != null,
                    () -> () -> processDesignerContainerController.handleClearCanvas(),
                    () -> isContainerVisible(certificatesContainer), this::clearPublishedResultSnapshot));
        }
        return shellHistoryCoordinator;
    }

    private void initializeHistory() {
        historyCoordinator().initialize();
    }

    private HistoryCoordinator historyCoordinator() {
        if (historyCoordinator == null) historyCoordinator = shellHistoryCoordinator().historyCoordinator();
        return historyCoordinator;
    }

    /**
     * Refreshes every surface that shows history.
     *
     * <p>The Inspector's own history card list was removed in favour of a single History view,
     * so what is left is the side-panel's recent-operations group and, once the user has opened
     * it, the History module's table.
     */
    private void refreshHistoryUI() {
        shellHistoryCoordinator().refresh();
    }

    @Override
    public void refreshHistoryNavigation() {
        shellHistoryCoordinator().refreshNavigation();
    }

    public void addToHistory(String operation, java.util.Map<String, String> details) {
        java.util.List<com.cryptocarver.model.OperationDetail> list = new java.util.ArrayList<>();
        if (details != null) {
            details.forEach((k, v) -> list.add(com.cryptocarver.model.OperationDetail.publicDetail(k, v)));
        }
        addToHistory(operation, list);
    }

    @Override
    public void addToHistory(String operation, java.util.List<com.cryptocarver.model.OperationDetail> details) {
        shellHistoryCoordinator().addToHistory(operation, details, currentActiveOperation);
    }

    private void addToHistory(String operation, java.util.List<com.cryptocarver.model.OperationDetail> details,
                              String navigationOperation) {
        shellHistoryCoordinator().addToHistory(operation, details, navigationOperation);
    }

    /** Exposes the shared history store to the FXML history module. */
    public com.cryptocarver.model.HistoryManager getHistoryManager() { return shellHistoryCoordinator().historyManager(); }

    /** Restores an operation selected from the modular history view. */
    public void restoreOperationState(java.util.Map<String, Object> state, String operation) {
        shellHistoryCoordinator().restoreOperationState(state, operation);
    }

    @FXML
    private void handleExportHistory() {
        shellHistoryCoordinator().chooseAndExport();
    }

    void exportHistoryTo(java.nio.file.Path target,
                         com.cryptocarver.model.SecretVisibilityProfile visibility) throws IOException {
        shellHistoryCoordinator().exportTo(target, visibility);
    }

    /** Materializes and presents the modular Recent Operations view. */
    /** Bring a newly produced result (or reused input) into view on compact layouts. */
    void revealCipherEditor(Node editor) {
        Platform.runLater(() -> {
            if (!cipherContainer.isVisible() || !cipherController.symmetricWorkspaceProperty().get()
                    || editor.getScene() == null) return;
            contentContainer.applyCss();
            contentContainer.layout();
            javafx.geometry.Bounds bounds = editor.localToScene(editor.getBoundsInLocal());
            javafx.geometry.Bounds viewport = mainScrollPane.localToScene(mainScrollPane.getBoundsInLocal());
            if (bounds.getMinY() >= viewport.getMinY() && bounds.getMaxY() <= viewport.getMaxY()) return;
            double height = contentContainer.getBoundsInLocal().getHeight() - mainScrollPane.getViewportBounds().getHeight();
            if (height > 0) {
                double top = contentContainer.localToScene(contentContainer.getBoundsInLocal()).getMinY();
                mainScrollPane.setVvalue(Math.max(0, Math.min(1, (bounds.getMinY() - top - 12) / height)));
            }
        });
    }

    private ModulePaneNavigator modulePaneNavigator;

    private ModulePaneNavigator modulePaneNavigator() {
        if (modulePaneNavigator == null) {
            var hosts = new java.util.EnumMap<UiNavigationRegistry.Module, java.util.function.Supplier<ModuleHost>>(UiNavigationRegistry.Module.class);
            hosts.put(UiNavigationRegistry.Module.KEYS_SYMMETRIC, () -> keysContainer);
            hosts.put(UiNavigationRegistry.Module.KEYS_ASYMMETRIC, () -> keysContainer);
            hosts.put(UiNavigationRegistry.Module.CERTIFICATES, () -> certificatesContainer);
            hosts.put(UiNavigationRegistry.Module.GENERIC, () -> genericContainer);
            hosts.put(UiNavigationRegistry.Module.POST_QUANTUM, () -> postQuantumContainer);
            hosts.put(UiNavigationRegistry.Module.XML_SECURITY, () -> xmlSecurityContainer);
            hosts.put(UiNavigationRegistry.Module.WSS_SECURITY, () -> wssSecurityContainer);
            hosts.put(UiNavigationRegistry.Module.EMV, () -> emvContainer);
            hosts.put(UiNavigationRegistry.Module.CIPHER, () -> cipherContainer);
            hosts.put(UiNavigationRegistry.Module.AUTHENTICATION, () -> authenticationContainer);
            hosts.put(UiNavigationRegistry.Module.PAYMENTS, () -> paymentsContainer);
            modulePaneNavigator = new ModulePaneNavigator(hosts, () -> keysController, () -> cipherController,
                    () -> certificatesContainerController, () -> postQuantumContainerController,
                    () -> xmlSecurityContainerController, () -> wssSecurityContainerController,
                    () -> mainScrollPane, () -> contentContainer);
        }
        return modulePaneNavigator;
    }

    private void expandCipherAccordionPane(String itemName) { modulePaneNavigator().expandCipherAccordionPane(itemName); }

    private void expandAuthenticationAccordionPane(String itemName) { modulePaneNavigator().expandAuthenticationAccordionPane(itemName); }

    private void expandPaymentsAccordionPane(String itemName) { modulePaneNavigator().expandPaymentsAccordionPane(itemName); }

    private void showPlaceholderContent(String title) {
        hideAllContainers();
        if (contentPlaceholderLabel == null) {
            return;
        }

        String requestedOperation = title == null || title.isBlank() ? "Unknown operation" : title;
        contentPlaceholderLabel.setText(requestedOperation
                + "\n\nNo view is registered for this legacy operation. Select a tool from the side panel.");
        contentPlaceholderLabel.setGraphic(IconRegistry.icon("clipboard"));
        contentPlaceholderLabel.setManaged(true);
        contentPlaceholderLabel.setVisible(true);
        updateContentHeader(requestedOperation);
        updateContentSubtitle("No module available");
    }

    private void expandAccordionPane(String paneName) { modulePaneNavigator().expandAccordionPane(paneName); }

    private void expandAsymmetricAccordionPane(String paneName) { modulePaneNavigator().expandAsymmetricAccordionPane(paneName); }

    private void expandCertificatesAccordionPane(String paneName) { modulePaneNavigator().expandCertificatesAccordionPane(paneName); }

    @Override
    public void updateStatus(String message) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> updateStatus(message));
            return;
        }
        if (statusBarPresenter != null) statusBarPresenter.showStatus(message);
    }

    // Menu handlers
    @FXML
    private void handleExit() {
        Platform.exit();
    }

    @FXML
    private void handleClearInput() {
        shellHistoryCoordinator().clearInput();
    }

    @FXML
    private void handleClearOutput() {
        ResultAreaTracker.clearVisibleOutputs(contentContainer);
        clearPublishedResultSnapshot();
        updateInspector(currentActiveOperation);
        updateStatus(i18n.text("status.outputCleared"));
    }

    public boolean hasCurrentResult() {
        return lastPublishedResultSnapshot != null || !resolveCurrentOutputText().isBlank();
    }

    // A module's result surface offers the same actions as the shell's result bar, on the same
    // result: the shell is what knows which result is current and what the visibility policy
    // allows, so these delegate instead of resolving it a second time.

    @Override
    public void copyCurrentResult() {
        handleCopyOutput();
    }

    @Override
    public void addCurrentResultToShelf() {
        handleAddCurrentOutputToShelf();
    }

    @Override
    public void expandCurrentResult() {
        handleOpenExpandedResultViewer();
    }

    @Override
    public void saveCurrentResultAsSessionStep() {
        handleSaveCurrentResultAsSessionStep();
    }

    @FXML
    public void handleCopyOutput() {
        String content = resolveCurrentOutputText();
        if (content == null || content.isBlank()) {
            updateStatus(i18n.text("status.noOutput"));
            return;
        }
        if (content.equals("***MASKED***")) {
            updateStatus(i18n.text("status.secretBlocked"));
            return;
        }
        copyToClipboard(content);
        updateStatus(i18n.text("status.outputCopied"));
    }

    /** Adds the active rendered result to the in-session Clipboard Shelf. */
    @FXML
    public void handleAddCurrentOutputToShelf() { resultCaptureCoordinator().handleAddCurrentOutputToShelf(); }

    private boolean isActiveAsymmetricKeyGeneration() { return resultCaptureCoordinator().isActiveAsymmetricKeyGeneration(); }

    private KeyCertificateWorkbenchController activeWorkbenchForShelf() { return resultCaptureCoordinator().activeWorkbenchForShelf(); }

    /** Opens the active operation result in a large, independent viewer. */
    @FXML
    public void handleOpenExpandedResultViewer() {
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

    String resolveCurrentOutputText() { return resultCaptureCoordinator().resolveCurrentOutputText(); }

    private TextArea preferredResultArea() { return resultCaptureCoordinator().preferredResultArea(); }

    private boolean hasPublishedPayload() { return resultCaptureCoordinator().hasPublishedPayload(); }

    private String resolveResultText(TextArea requestedArea) { return resultCaptureCoordinator().resolveResultText(requestedArea); }

    /**
     * Resolves only a real output for Shelf capture. A summary assembled from
     * public details is useful to the viewer but is not a captured artifact.
     */
    private String resolveShelfCaptureText(TextArea requestedArea) { return resultCaptureCoordinator().resolveShelfCaptureText(requestedArea); }

    /**
     * The published result, but only while its screen is still the active one:
     * after navigating elsewhere it must not be added to the Shelf under the
     * new screen's name.
     */
    private com.cryptocarver.model.OperationResult shelfSnapshot() { return resultCaptureCoordinator().shelfSnapshot(); }

    private boolean isShelfCaptureBlockedByVisibility(TextArea area) { return resultCaptureCoordinator().isShelfCaptureBlockedByVisibility(area); }

    private boolean isPrivateMaterialPlaceholder(String text) { return resultCaptureCoordinator().isPrivateMaterialPlaceholder(text); }

    private String renderResultArea(TextArea area) { return resultCaptureCoordinator().renderResultArea(area); }

    private com.cryptocarver.model.OperationDetail.Classification classificationForResultArea(TextArea area) { return resultCaptureCoordinator().classificationForResultArea(area); }

    private ResultCaptureCoordinator resultCaptureCoordinator() {
        if (resultCaptureCoordinator == null) resultCaptureCoordinator = new ResultCaptureCoordinator(
                () -> keysController, () -> genericContainerController, () -> genericContainer,
                () -> mainPane, () -> resultAreaTracker, () -> lastPublishedResultSnapshot,
                () -> lastPublishedScreen, () -> currentActiveOperation,
                () -> AppSettings.getInstance().getSecretVisibilityProfile(),
                this::updateStatus, this::showInfo, this::handleAddToClipboardShelfSecure);
        return resultCaptureCoordinator;
    }

    String renderPublishedResult(com.cryptocarver.model.OperationResult result, com.cryptocarver.model.SecretVisibilityProfile visibility) {
        return OperationResultRenderer.render(result, visibility);
    }



    /**
     * Publishes the UI update and refreshes the expanded-view snapshot as one
     * event. This makes the expanded viewer independent from focus order and
     * from the visibility of sibling accordion panes.
     */
    @Override
    public void publish(com.cryptocarver.model.OperationResult result) {
        resultPublicationCoordinator().publish(result);
    }

    private ResultPublicationCoordinator resultPublicationCoordinator() {
        if (resultPublicationCoordinator == null) resultPublicationCoordinator = new ResultPublicationCoordinator(
                result -> { lastPublishedOperation = result.getOperation(); lastPublishedResultSnapshot = result; lastPublishedScreen = currentActiveOperation; },
                result -> updateInspector(result.getOperation(), result.getInput(), result.getOutput(), result.getDetails()),
                this::refreshSessionTrailNavigation,
                (result, details) -> addToHistory(result.getOperation(), details, currentActiveOperation),
                this::updateStatus, inspectorAddSessionStepButton, resultSummaryBar, resultOpLabel, resultAlgoLabel,
                resultSizeLabel, resultFormatLabel, resultStatusBadge, outputFormatCombo, i18n, sessionTrailState);
        return resultPublicationCoordinator;
    }

    /** Clears the cached result whenever it no longer represents the visible UI state. */
    private void clearPublishedResultSnapshot() {
        lastPublishedOperation = "";
        lastPublishedResultSnapshot = null;
        sessionTrailState.clearPublishedResult();
        refreshSessionTrailNavigation();
        resultAreaTracker.clearSelection();
        if (resultSummaryBar != null) {
            resultSummaryBar.setManaged(false);
            resultSummaryBar.setVisible(false);
        }
        if (inspectorAddSessionStepButton != null) inspectorAddSessionStepButton.setDisable(true);
    }


    /**
     * Result areas can be opened directly even when their feature does not use the
     * shared global output panel (for example XAdES, PQC and inspectors).
     */
    private void installResultViewerSupport() {
        java.util.List<TextArea> additionalAreas = new java.util.ArrayList<>();
        if (cipherController != null && cipherController.getOutputArea() != null) {
            additionalAreas.add(cipherController.getOutputArea());
        }
        if (cipherController != null && cipherController.getFileResultArea() != null) {
            additionalAreas.add(cipherController.getFileResultArea());
        }
        resultViewerCoordinator();
        resultAreaTracker.install(mainPane, additionalAreas,
                area -> area.setContextMenu(resultViewerCoordinator.createContextMenu(area)));
    }

    private ResultViewerCoordinator resultViewerCoordinator() {
        if (resultViewerCoordinator == null) {
            resultViewerCoordinator = new ResultViewerCoordinator(resultAreaTracker, expandedTableViewer,
                    area -> handleOpenExpandedResultViewer(),
                    area -> { String selected = area.getSelectedText(); handleAddToClipboardShelfSecure(area, selected != null && !selected.isEmpty() ? selected : null); },
                    area -> handleCopySecure(area, null, false),
                    area -> { String selected = area.getSelectedText(); boolean hasSelection = selected != null && !selected.isEmpty(); handleCopySecure(area, hasSelection ? selected : null, hasSelection); },
                    () -> currentActiveOperation, () -> lastPublishedResultSnapshot != null,
                    this::resolveResultText, this::classificationForResultArea, AppSettings::isFullLab,
                    this::updateStatus, this::copyToClipboard,
                    entry -> { if (entry != null && clipboardShelfController != null) clipboardShelfController.refreshAndReveal(entry.getId()); },
                    new ResultViewerCoordinator.ShelfServices() {
                        public String capture(TextArea area) { return resolveShelfCaptureText(area); }
                        public boolean blockedByVisibility(TextArea area) { return isShelfCaptureBlockedByVisibility(area); }
                        public boolean isCurrentSelection(TextArea area) { return resultAreaTracker.isCurrentSelection(area, lastPublishedResultSnapshot != null); }
                        public com.cryptocarver.model.OperationResult snapshot() { return shelfSnapshot(); }
                        public String activeOperation() { return currentActiveOperation; }
                        public com.cryptocarver.model.ClipboardShelfManager manager() { return com.cryptocarver.model.ClipboardShelfManager.getInstance(); }
                        public boolean isPrimaryCipherOutput(TextArea area) { return cipherController != null && cipherController.isPrimaryOutput(area); }
                        public com.cryptocarver.model.ShelfPackage createCipherPackage() { return cipherController == null ? null : cipherController.createAuthenticatedCipherShelfPackage(); }
                    });
        }
        return resultViewerCoordinator;
    }

    private ContextMenu createResultContextMenu(TextArea area) { return resultViewerCoordinator.createContextMenu(area); }

    private void handleCopySecure(TextArea area, String textToCopy, boolean isSelection) {
        resultViewerCoordinator.copySecure(area, textToCopy, isSelection);
    }

    private void handleAddToClipboardShelfSecure(javafx.scene.control.TextArea area, String selectedText) {
        resultViewerCoordinator.addToClipboardShelfSecure(area, selectedText);
    }

    public void revealShelfEntry(com.cryptocarver.model.ClipboardEntry entry) { resultViewerCoordinator().revealShelfEntry(entry); }

    public com.cryptocarver.model.OperationDetail.Classification classifyPublishedResult(com.cryptocarver.model.OperationResult result) {
        return com.cryptocarver.model.ResultPresentationPolicy.classifyPublishedResult(result);
    }

    public void fillClipboardTarget(String targetType, String value, com.cryptocarver.model.ClipboardEntry.Format format) {
        fillClipboardTarget(targetType, value, format, null);
    }

    public void fillClipboardTarget(String targetType, String value, com.cryptocarver.model.ClipboardEntry.Format format,
                                    com.cryptocarver.model.ShelfPackage packageData) {
        clipboardTargetNavigator().fill(targetType, value, format, packageData);
    }

    /**
     * Built per call: module controllers load on demand, so a cached navigator
     * would keep the null reference of a module that was not loaded yet.
     */
    private ClipboardTargetNavigator clipboardTargetNavigator() {
        return new ClipboardTargetNavigator(
                genericContainerController, cipherController, xmlSecurityContainerController, wssSecurityContainerController,
                paymentsContainerController, keysController, joseController, this::navigateToModule,
                this::expandGenericAccordionPane, this::expandCipherAccordionPane, this::expandXMLAccordionPane,
                this::expandWssAccordionPane, this::expandPaymentsAccordionPane, this::expandAccordionPane,
                this::showJOSE, this::updateStatus);
    }

    @FXML
    private void handleOpenExpandedTableViewer() {
        if (resultViewerCoordinator == null || !resultViewerCoordinator.hasFocusedTable()) {
            showInfo("No table selected", "Right-click a table or select a cell before opening its expanded view.");
            return;
        }
        resultViewerCoordinator.openFocusedTable();
    }

    private void installTableViewerSupport() {
        if (resultViewerCoordinator == null) installResultViewerSupport();
        resultViewerCoordinator.installTables(mainPane);
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
    public void handleToggleSidePanel() {
        boolean visible = sidePanel.isVisible();
        sidePanel.setVisible(!visible);
        sidePanel.setManaged(!visible);
        updateStatus(visible ? "Side panel hidden" : "Side panel shown");
    }

    @FXML
    public void handleToggleInspector() {
        boolean visible = inspectorPanel.isVisible();
        inspectorPanel.setVisible(!visible);
        inspectorPanel.setManaged(!visible);
        inspectorHiddenForCompactLayout = false;
        updateStatus(visible ? "Inspector hidden" : "Inspector shown");
    }

    @FXML
    private void handleResetView() {
        sidePanel.setVisible(true);
        sidePanel.setManaged(true);
        inspectorPanel.setVisible(false);
        inspectorPanel.setManaged(false);
        inspectorHiddenForCompactLayout = false;
        updateStatus("View reset to defaults");
    }

    private int currentFontSize = 14;

    @FXML
    public void handleIncreaseFontSize() {
        if (currentFontSize < 24) {
            currentFontSize += 2;
            applyFontSize();
            updateStatus("Font size increased to " + currentFontSize + "px");
        }
    }

    @FXML
    public void handleDecreaseFontSize() {
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

    private void updateNodeFonts(javafx.scene.Node node) { shellLocalizationCoordinator.updateNodeFonts(node, currentFontSize); }

    @FXML
    public void handleShowKeyboardShortcuts() {
        shellDialogCoordinator.showKeyboardShortcuts();
    }

    @FXML
    private void handleAbout() {
        shellDialogCoordinator.showAbout();
    }

    /**
     * JavaFX reports the work area in logical units, so a 1920x1080 panel at 150% scaling
     * shows up as 1280x720 here. That is the number the window sizing has to respect, and
     * the one that explains a clipped layout on a machine the maintainer cannot see.
     */
    private static String describePrimaryDisplay() {
        javafx.stage.Screen primary = javafx.stage.Screen.getPrimary();
        javafx.geometry.Rectangle2D work = primary.getVisualBounds();
        javafx.geometry.Rectangle2D full = primary.getBounds();
        return String.format(java.util.Locale.ROOT,
                "%.0fx%.0f logical (work area %.0fx%.0f) at %.2fx output scale",
                full.getWidth(), full.getHeight(), work.getWidth(), work.getHeight(),
                primary.getOutputScaleX());
    }

    @FXML
    private void handleDiagnostics() {
        shellDialogCoordinator.showDiagnostics(ModernMainController::describePrimaryDisplay, this::updateStatus);
    }

    // ============================================================
    // HELPER METHODS FOR KeysController
    // ============================================================

    @Override
    public void showError(String title, String message) {
        if ("true".equals(System.getProperty("test.mode"))) {
            System.err.println("SHOW_ERROR: " + title + " - " + message);
        }
        UserFacingError error = UserFacingErrorMapper.map(title, message, null);
        showError(error);
    }

    @Override
    public void showError(UserFacingError error) {
        if (error == null) return;
        bannerHelpContext = error.helpContext();
        if ("true".equals(System.getProperty("test.mode"))) {
            System.err.println("SHOW_ERROR: " + error.title() + " - " + error.remedy());
        }
        if (inlineErrorPresenter != null) {
            inlineErrorPresenter.showError(localizedError(error), rootStackPane != null ? rootStackPane : mainPane);
            inlineErrorPresenter.goToField(rootStackPane != null ? rootStackPane : mainPane);
        }
    }

    private UserFacingError localizedError(UserFacingError error) {
        return shellTextResolver.localizedError(error);
    }

    @Override
    public void showError(Throwable cause, String contextTitle, String fieldKey) {
        UserFacingError error = UserFacingErrorMapper.map(cause, contextTitle, fieldKey);
        showError(error);
    }

    @FXML
    private void handleErrorBannerGoToField() {
        if (inlineErrorPresenter != null) {
            inlineErrorPresenter.goToField(rootStackPane != null ? rootStackPane : mainPane);
        }
    }

    @FXML
    private void handleErrorBannerCopyDetails() {
        if (inlineErrorPresenter != null) {
            inlineErrorPresenter.copyTechnicalDetails(this);
        }
    }

    @FXML
    private void handleErrorBannerClose() {
        bannerHelpContext = null;
        if (inlineErrorPresenter != null) {
            inlineErrorPresenter.hideBanner();
        }
    }

    public void showWarning(String title, String message) {
        shellDialogCoordinator.warning(title, message);
    }

    @Override
    public void showInfo(String title, String message) {
        shellDialogCoordinator.info(title, message);
    }

    // Generic module initialized by FXML include

















    // File conversion handlers moved to GenericController







    // ============================================================
    // EVENT HANDLERS - Payments Operations
    // ============================================================

    private void hideAllContainers() { navigationRouter.hideAllContainers(); }

    private void showSymmetricKeys() { navigationRouter.showContainer(UiNavigationRegistry.Module.KEYS_SYMMETRIC); if(keysController!=null) keysController.showSymmetricSection(); }
    private void showJOSE() { navigationRouter.activate("JWT (Signed)"); }

    public void showProcessDesigner() { navigationRouter.activate("Process Designer"); }

    /** Gives the designer its own canvas without permanently changing shell panels. */
    private void enterProcessDesignerWorkspace() {
        if (processDesignerWorkspace) return;
        processDesignerWorkspace = true;
        sidePanelVisibleBeforeProcessDesigner = sidePanel != null && sidePanel.isVisible();
        inspectorVisibleBeforeProcessDesigner = inspectorPanel != null && inspectorPanel.isVisible();
        if (sidePanel != null) {
            sidePanel.setVisible(false);
            sidePanel.setManaged(false);
        }
        if (inspectorPanel != null) {
            inspectorPanel.setVisible(false);
            inspectorPanel.setManaged(false);
        }
    }

    private void exitProcessDesignerWorkspace() {
        if (!processDesignerWorkspace) return;
        processDesignerWorkspace = false;
        if (sidePanel != null) {
            sidePanel.setVisible(sidePanelVisibleBeforeProcessDesigner);
            sidePanel.setManaged(sidePanelVisibleBeforeProcessDesigner);
        }
        if (inspectorPanel != null) {
            inspectorPanel.setVisible(inspectorVisibleBeforeProcessDesigner);
            inspectorPanel.setManaged(inspectorVisibleBeforeProcessDesigner);
        }
    }

    public TitledPane getProcessDesignerContainer() {
        return processDesignerContainer != null && processDesignerContainer.root() instanceof TitledPane pane ? pane : null;
    }

    public ProcessDesignerController getProcessDesignerContainerController() {
        return processDesignerContainerController;
    }

    private void expandGenericAccordionPane(String paneName) { modulePaneNavigator().expandGenericAccordionPane(paneName); }

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

    private void expandEMVAccordionPane(String title) { modulePaneNavigator().expandEMVAccordionPane(title); }

    // ============================================================
    // SAVED SESSIONS LOGIC
    // ============================================================

    private SavedSessionsCoordinator savedSessionsCoordinator() {
        if (savedSessionsCoordinator == null) {
            com.cryptocarver.model.SavedSessionsManager manager =
                    com.cryptocarver.model.SavedSessionsManager.getInstance();
            savedSessionsCoordinator = new SavedSessionsCoordinator(
                    savedSessionsContainer, savedSessionsList, this, manager, i18n, dialogService,
                    this::captureUIState, this::restoreUIState, this::handleItemSelected,
                    this::refreshSessionTrailUI, this::showSessionStep, sessionTrailState,
                    () -> currentActiveOperation, () -> contentSubtitleLabel == null ? null : contentSubtitleLabel.getText(),
                    () -> mainPane);
        }
        return savedSessionsCoordinator;
    }

    private SaveSessionCoordinator saveSessionCoordinator() {
        if (saveSessionCoordinator == null) {
            saveSessionCoordinator = new SaveSessionCoordinator(i18n, this::captureUIState,
                    () -> sessionTrailState, this::savedSessionsCoordinator, this::showWarning);
        }
        return saveSessionCoordinator;
    }

    private UtilityToolsCoordinator utilityToolsCoordinator() {
        if (utilityToolsCoordinator == null) {
            utilityToolsCoordinator = new UtilityToolsCoordinator(() -> mainPane == null ? null : mainPane.getScene(),
                    this::addToHistory, this::showError, contextualHelpState, this::showStandaloneAuxiliaryHelpContext);
        }
        return utilityToolsCoordinator;
    }

    @FXML
    private void handleVisibilityFullLab() {
        com.cryptocarver.model.AppSettings.getInstance().setSecretVisibilityProfile(com.cryptocarver.model.SecretVisibilityProfile.FULL_LAB);
        sessionStepViewer.hide();
        if (sessionTrailState.selectedIndex() >= 0) showSessionStep(sessionTrailState.selectedIndex());
        updateStatus("Visibility set to FULL_LAB (Debug/Learning)");
        refreshStatusBarContext();
        if (keysController != null) {
            keysController.updateVisibilityControls();
            keysController.refreshKeyLabTable();
        }
    }

    @FXML
    private void handleVisibilityMasked() {
        com.cryptocarver.model.AppSettings.getInstance().setSecretVisibilityProfile(com.cryptocarver.model.SecretVisibilityProfile.MASKED);
        sessionStepViewer.hide();
        if (sessionTrailState.selectedIndex() >= 0) showSessionStep(sessionTrailState.selectedIndex());
        updateStatus("Visibility set to MASKED (Classroom/Demo)");
        refreshStatusBarContext();
        if (keysController != null) {
            keysController.updateVisibilityControls();
            keysController.refreshKeyLabTable();
        }
    }

    @FXML
    private void handleVisibilityRedacted() {
        com.cryptocarver.model.AppSettings.getInstance().setSecretVisibilityProfile(com.cryptocarver.model.SecretVisibilityProfile.REDACTED);
        sessionStepViewer.hide();
        if (sessionTrailState.selectedIndex() >= 0) showSessionStep(sessionTrailState.selectedIndex());
        updateStatus("Visibility set to REDACTED (Strict/Production)");
        refreshStatusBarContext();
        if (keysController != null) {
            keysController.updateVisibilityControls();
            keysController.refreshKeyLabTable();
        }
    }

    public void refreshHsmKeyCombos() {
        if (cipherController != null) cipherController.refreshHsmKeys();
        if (cipherContainerController != null) cipherContainerController.refreshHsmKeys();
        if (authenticationContainerController != null) authenticationContainerController.refreshHsmKeys();
    }

    @FXML
    private void handleClearLabKeyCache() {
        com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().clear();
        showInfo("Success", "Lab Key Cache cleared");
        refreshHsmKeyCombos();
    }

    /** Adds the latest completed result to the current session's ordered trail. */
    @FXML
    public void handleSaveCurrentResultAsSessionStep() { sessionTrailCoordinator().saveCurrentResultAsSessionStep(); }

    com.cryptocarver.model.SessionOperationStep saveCurrentResultAsSessionStep(String title, String commaSeparatedTags) {
        return sessionTrailCoordinator().saveCurrentResultAsSessionStep(title, commaSeparatedTags);
    }

    private void refreshSessionTrailUI() { sessionTrailCoordinator().refresh(); }
    private void refreshSessionTrailNavigation() { sessionTrailCoordinator().refreshNavigation(); }
    private void showSessionStep(int index) { sessionTrailCoordinator().showSessionStep(index); }

    @FXML
    private void handlePreviousSessionStep() { sessionTrailCoordinator().previous(); }

    @FXML
    private void handleNextSessionStep() { sessionTrailCoordinator().next(); }

    @FXML
    private void handleOpenSelectedSessionStep() { sessionTrailCoordinator().openSelected(); }

    @FXML
    public void handleExportSessionTrail() { sessionTrailCoordinator().handleExport(); }

    void exportSessionTrail(java.nio.file.Path target) throws IOException { sessionTrailCoordinator().export(target); }

    @FXML
    public void handleClearSessionTrail() { sessionTrailCoordinator().clear(); }

    private SessionTrailCoordinator sessionTrailCoordinator() {
        if (sessionTrailCoordinator == null) sessionTrailCoordinator = new SessionTrailCoordinator(
                sessionTrailState, sessionTrailCountLabel, sessionTrailPositionLabel, inspectorExportSessionTrailButton, inspectorClearSessionTrailButton,
                inspectorPreviousSessionStepButton, inspectorNextSessionStepButton, inspectorOpenSessionStepButton,
                sessionTrailNavigation, dialogService, sessionStepViewer, () -> mainPane,
                () -> lastPublishedResultSnapshot, this::captureActiveScreenConfiguration, this::captureUIState,
                step -> inspectorPresenter().presentSavedStep(step, visibleOperationDetails(step.getDetails())),
                result -> inspectorPresenter().present(result.getOperation(), result.getInput(), result.getOutput(), result.getDetails()),
                () -> updateInspector(currentActiveOperation),
                () -> { if (inspectorPanel != null && !inspectorPanel.isVisible()) { inspectorPanel.setVisible(true); inspectorPanel.setManaged(true); inspectorHiddenForCompactLayout = false; } },
                this::updateStatus, this::showWarning, i18n);
        return sessionTrailCoordinator;
    }

    @FXML
    public void handleSaveSession() { saveSessionCoordinator().handleSaveSession(); }

    @FXML
    private void handleExportScreenConfiguration() { screenConfigurationCoordinator().exportScreenConfiguration(); }

    static boolean isEncryptedConfigurationOption(String selectedOption, String encryptedOption) {
        return ScreenConfigurationCoordinator.isEncryptedConfigurationOption(selectedOption, encryptedOption);
    }

    @FXML
    private void handleImportScreenConfiguration() { screenConfigurationCoordinator().importScreenConfiguration(); }

    static boolean isLegacyKeyGenerationConfiguration(
            com.cryptocarver.model.ScreenConfiguration configuration) {
        return ScreenConfigurationCoordinator.isLegacyKeyGenerationConfiguration(configuration);
    }

    @FXML
    private void handleImportKey() {
        if (joseController == null) return;
        navigateTo("JWK (Keys)");
        joseController.importKeyFromFile();
    }

    @FXML
    private void handleEpochConverter() { utilityToolsCoordinator().handleEpochConverter(); }

    @FXML
    private void handleJsonFormatter() { utilityToolsCoordinator().handleJsonFormatter(); }

    @FXML
    private void handleByteInspector() {
        ByteInspectorWindow.show((Stage) mainPane.getScene().getWindow(), contextualHelpState,
                this::showStandaloneAuxiliaryHelpContext);
    }

    private void showStandaloneAuxiliaryHelpContext(HelpContext context) {
        if (context == null || !com.cryptocarver.model.help.B05HelpCatalog.supports(context.operationId())) return;
        contextualHelpState.replace(context);
        showCurrentHelpContext();
    }

    // ============================================================
    // POST-QUANTUM HANDLERS
    // ============================================================

    private void loadPostQuantumContent() {
        if (postQuantumContainerController == null) postQuantumContainerController = ensureModule(postQuantumContainer, PostQuantumController.class);
        if (postQuantumContainerController != null) {
            postQuantumContainerController.initModule(this);
        }
    }

    private void expandPQCAccordionPane(String itemName) { modulePaneNavigator().expandPQCAccordionPane(itemName); }

    // ============================================================
    // XML SECURITY HANDLERS
    // ============================================================

    private void loadXMLSecurityContent() {
        if (xmlSecurityContainerController == null) xmlSecurityContainerController = ensureModule(xmlSecurityContainer, XMLSignatureController.class);
        if (xmlSecurityContainerController != null) {
            xmlSecurityContainerController.initModule(this);
        }
    }

    private void expandXMLAccordionPane(String itemName) { modulePaneNavigator().expandXMLAccordionPane(itemName); }

    private void loadWssSecurityContent() {
        if (wssSecurityContainerController == null) wssSecurityContainerController = ensureModule(wssSecurityContainer, WssSecurityController.class);
        if (wssSecurityContainerController != null) {
            wssSecurityContainerController.initModule(this);
        }
    }

    private void expandWssAccordionPane(String itemName) { modulePaneNavigator().expandWssAccordionPane(itemName); }

    public void showQuickStart() {
        hideAllContainers();
        if (quickStartContainer != null) {
            quickStartContainer.setVisible(true);
            quickStartContainer.setManaged(true);
        }
        updateContentHeader("Quick Start");
        updateStatus("Quick Start dashboard active.");
    }

    @FXML
    private void handleStartGuidedEncrypt() {
        startGuidedWorkflow(GuidedOperation.ENCRYPT);
    }

    @FXML
    private void handleStartGuidedHash() {
        startGuidedWorkflow(GuidedOperation.HASH);
    }

    @FXML
    private void handleStartGuidedSign() {
        startGuidedWorkflow(GuidedOperation.SIGN);
    }

    @FXML
    private void handleStartGuidedCert() {
        startGuidedWorkflow(GuidedOperation.CERT);
    }

    @FXML
    private void handleStartGuidedConvert() {
        startGuidedWorkflow(GuidedOperation.CONVERT);
    }

    public void startGuidedWorkflow(GuidedOperation op) {
        readinessPanelCoordinator().startGuidedWorkflow(op);
    }

    @FXML
    private void handleGuideNext() {
        readinessPanelCoordinator().handleGuideNext();
    }

    @FXML
    private void handleGuideBack() {
        readinessPanelCoordinator().handleGuideBack();
    }

    @FXML
    private void handleGuideSkip() {
        readinessPanelCoordinator().handleGuideSkip();
    }

    @FXML
    private void handleGuideExit() {
        readinessPanelCoordinator().handleGuideExit();
    }

    private void updateGuidedStepUI() {
        readinessPanelCoordinator().updateGuidedStepUI();
    }

    private void setupGuidedFlowKeyboardAndTooltips() {
        readinessPanelCoordinator().setupGuidedFlowKeyboardAndTooltips();
    }




    private void setupLaboratoryMenu() {
        laboratoryMenuCoordinator().setup();
    }

    private LaboratoryMenuCoordinator laboratoryMenuCoordinator() {
        if (laboratoryMenuCoordinator == null) {
            laboratoryMenuCoordinator = new LaboratoryMenuCoordinator(mainMenuBar, this::showQuickStart,
                    this::handleItemSelected, () -> keysController, () -> emvController, () -> paymentsController);
        }
        return laboratoryMenuCoordinator;
    }

    @FXML private HBox readinessPanel;
    @FXML private Label readinessStatusBadge;
    @FXML private Label readinessSummaryLabel;
    @FXML private FlowPane readinessChecksContainer;
    @FXML private Button readinessHelpBtn;
    @FXML private Button readinessToggleDetailsBtn;
    private com.cryptocarver.model.PreflightReport currentPreflightReport;
    private boolean currentPreflightEncrypt = true;

    @FXML
    private void handleToggleReadinessDetails() {
        readinessPanelCoordinator().toggleReadinessDetails();
    }

    @Override
    public boolean checkPreflightReadiness(String operation, boolean isEncrypt) {
        return readinessPanelCoordinator().checkPreflightReadiness(operation, isEncrypt);
    }

    public void updateReadinessPanel() {
        readinessPanelCoordinator().updateReadinessPanel();
    }

    public void updateReadinessPanelForOperation(String operation, boolean isEncrypt) {
        readinessPanelCoordinator().updateReadinessPanelForOperation(operation, isEncrypt);
    }

    private void updateReadinessPanelUI() {
        readinessPanelCoordinator().updateReadinessPanelUI();
    }

    private ReadinessPanelCoordinator readinessPanelCoordinator() {
        if (readinessPanelCoordinator == null) {
            readinessPanelCoordinator = new ReadinessPanelCoordinator(
                    () -> currentActiveOperation,
                    () -> cipherContainerController,
                    () -> genericContainerController,
                    () -> authenticationContainerController,
                    () -> inputFormatCombo,
                    () -> readinessPanel,
                    () -> readinessStatusBadge,
                    () -> readinessSummaryLabel,
                    () -> readinessChecksContainer,
                    () -> readinessToggleDetailsBtn,
                    () -> guidedFlowPanel,
                    () -> guideStepTitleLabel,
                    () -> guideStepDescLabel,
                    () -> guideBackBtn,
                    () -> guideNextBtn,
                    () -> currentPreflightReport,
                    report -> currentPreflightReport = report,
                    encrypt -> currentPreflightEncrypt = encrypt,
                    this::focusControl,
                    this::showError,
                    this::showPreflightHelpContext,
                    this::handleItemSelected);
        }
        return readinessPanelCoordinator;
    }

    public void focusControl(String controlKey) {
        if (controlKey == null || controlKey.isEmpty()) return;
        Object[] controllers = new Object[]{ this, cipherContainerController, genericContainerController,
                authenticationContainerController, certificatesContainerController, keysContainerController,
                xmlSecurityContainerController, wssSecurityContainerController, paymentsContainerController,
                emvContainerController, joseController, coseController };
        for (Object ctrl : controllers) {
            if (ctrl == null) continue;
            try {
                java.lang.reflect.Field field = ctrl.getClass().getDeclaredField(controlKey);
                field.setAccessible(true);
                Object val = field.get(ctrl);
                if (val instanceof javafx.scene.Node node) {
                    ContextualHelpButtonBinder.withoutFollowing(node::requestFocus);
                    return;
                }
            } catch (Exception ignored) {}
        }
    }

    @FXML private StackPane rootStackPane;
    @FXML private VBox commandPaletteOverlay;
    @FXML private TextField commandSearchField;
    @FXML private ListView<com.cryptocarver.model.CommandItem> commandResultsListView;
    @FXML private Label commandEmptyLabel;

    private void syncMenuBarAccelerators() {
        if (mainMenuBar == null) return;
        for (javafx.scene.control.Menu menu : mainMenuBar.getMenus()) {
            for (javafx.scene.control.MenuItem item : menu.getItems()) {
                if (item == null || item.getText() == null) continue;
                com.cryptocarver.model.KeyboardShortcutRegistry.findShortcutByAction(item.getText()).ifPresent(s -> {
                    try {
                        item.setAccelerator(javafx.scene.input.KeyCombination.valueOf(s.getKeyCombination()));
                    } catch (Exception ignored) {}
                });
            }
        }
    }

    private void initializeCommandPalette() {
        commandPaletteCoordinator().initialize(rootStackPane);
    }

    @FXML
    public void handleOpenCommandPalette() {
        commandPaletteCoordinator().open();
    }

    @FXML
    public void handleCloseCommandPalette() {
        commandPaletteCoordinator().close();
    }

    @FXML
    public void handleExecuteSelectedCommand() {
        commandPaletteCoordinator().executeSelected();
    }

    private CommandPaletteCoordinator commandPaletteCoordinator() {
        if (commandPaletteCoordinator == null) {
            commandPaletteCoordinator = new CommandPaletteCoordinator(commandPaletteOverlay, commandSearchField,
                    commandResultsListView, commandEmptyLabel, this::paletteCommandActions);
        }
        return commandPaletteCoordinator;
    }

    private com.cryptocarver.model.PaletteCommandCatalog.Actions paletteCommandActions() {
        return new com.cryptocarver.model.PaletteCommandCatalog.Actions() {
            @Override
            public void showQuickStart() { ModernMainController.this.showQuickStart(); }

            @Override
            public void navigateToModule(String moduleName) { ModernMainController.this.navigateToModule(moduleName); }

            @Override
            public void openBatchEncryptRecord() { ModernMainController.this.openBatchEncryptRecord(); }

            @Override
            public void openBatchDecryptRecord() { ModernMainController.this.openBatchDecryptRecord(); }

            @Override
            public void toggleInspector() { ModernMainController.this.handleToggleInspector(); }

            @Override
            public void toggleSidePanel() { ModernMainController.this.handleToggleSidePanel(); }

            @Override
            public boolean hasCurrentResult() { return ModernMainController.this.hasCurrentResult(); }

            @Override
            public void openExpandedResultViewer() { ModernMainController.this.handleOpenExpandedResultViewer(); }

            @Override
            public void increaseFontSize() { ModernMainController.this.handleIncreaseFontSize(); }

            @Override
            public void decreaseFontSize() { ModernMainController.this.handleDecreaseFontSize(); }

            @Override
            public void copyOutput() { ModernMainController.this.handleCopyOutput(); }

            @Override
            public void addCurrentOutputToShelf() { ModernMainController.this.handleAddCurrentOutputToShelf(); }

            @Override
            public void toggleFavorite() { ModernMainController.this.handleToggleFavorite(); }
        };
    }
}
