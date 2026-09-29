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
import java.util.Optional; // For Dialogs
import java.io.IOException;
import java.util.Base64;
import java.nio.file.Files;
import javafx.scene.text.TextFlow;
import com.cryptocarver.model.AppDiagnostics;
import com.cryptocarver.model.AppSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// Attempt to use DataConverter if available, otherwise will rely on local helpers or standard libs
import com.cryptocarver.util.DataConverter;
import com.cryptocarver.model.LanguagePreference;
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
        java.nio.file.Files.writeString(report, content);
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
    private final ExpandedTableViewer expandedTableViewer = new ExpandedTableViewer();
    private OperationInspectorPresenter inspectorPresenter;
    private final ResultAreaTracker resultAreaTracker = new ResultAreaTracker();
    private ResultViewerCoordinator resultViewerCoordinator;
    private ResultPublicationCoordinator resultPublicationCoordinator;
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
    @FXML private Button errorBannerCloseBtn;
    private InlineErrorPresenter inlineErrorPresenter;
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

    @FXML
    private Label contractOperationLabel;

    @FXML
    private HBox formatFlowBar;


    // Managers
    private com.cryptocarver.model.HistoryManager historyManager;
    private HistoryCoordinator historyCoordinator;
    private SavedSessionsCoordinator savedSessionsCoordinator;
    private String currentActiveOperation = "Dashboard"; // Defaul
    private boolean processDesignerWorkspace;
    private boolean sidePanelVisibleBeforeProcessDesigner;
    private boolean inspectorVisibleBeforeProcessDesigner;
    @FXML
    private ComboBox<String> outputFormatCombo;

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
    private java.util.function.Consumer<java.util.Locale> i18nListener;

    // Async Progress UI
    @FXML private HBox asyncProgressBox;
    @FXML private ProgressIndicator asyncProgressIndicator;
    @FXML private ProgressBar asyncProgressBar;
    @FXML private Label asyncProgressLabel;
    @FXML private Button asyncCancelBtn;
    @FXML private Label inspectorTitleLabel;
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

    private NavigationRouter createNavigationRouter() {
        java.util.EnumMap<UiNavigationRegistry.Module, java.util.function.BiConsumer<UiNavigationRegistry.Route,Object>> callbacks = new java.util.EnumMap<>(UiNavigationRegistry.Module.class);
        callbacks.put(UiNavigationRegistry.Module.JOSE, (r,c) -> { if(c instanceof JOSEController x) x.showSection(currentActiveOperation); });
        callbacks.put(UiNavigationRegistry.Module.COSE, (r,c) -> { if(c instanceof COSEController x) x.showSection(currentActiveOperation); });
        callbacks.put(UiNavigationRegistry.Module.WALLET, (r,c) -> { if(c instanceof WalletController x) x.showSection(currentActiveOperation); });
        callbacks.put(UiNavigationRegistry.Module.EPOCH_CONVERTER, (r,c) -> handleEpochConverter());
        callbacks.put(UiNavigationRegistry.Module.JSON_FORMATTER, (r,c) -> handleJsonFormatter());
        callbacks.put(UiNavigationRegistry.Module.KEYS_SYMMETRIC, (r,c) -> { loadSymmetricKeysContent(); if(keysController!=null) keysController.showSymmetricSection(); expandAccordionPane(r.section()); });
        callbacks.put(UiNavigationRegistry.Module.KEYS_ASYMMETRIC, (r,c) -> { loadSymmetricKeysContent(); if(keysController!=null) keysController.showAsymmetricSection(); expandAsymmetricAccordionPane(r.section()); });
        callbacks.put(UiNavigationRegistry.Module.CERTIFICATES, (r,c) -> {
            expandCertificatesAccordionPane(r.section());
            if(c instanceof CertificatesController x) {
                if(r.variant()==UiNavigationRegistry.Variant.ASN1_DECODE) x.selectAsn1DecodeTab();
                else if(r.variant()==UiNavigationRegistry.Variant.ASN1_ENCODE) x.selectAsn1EncodeTab();
            }
        });
        callbacks.put(UiNavigationRegistry.Module.GENERIC, (r,c) -> NavigationRouter.expandByTitle(genericContainer, r.section(), ModuleTextCatalog.generic(), this::revealExpandedPane));
        callbacks.put(UiNavigationRegistry.Module.POST_QUANTUM, (r,c) -> { loadPostQuantumContent(); expandPQCAccordionPane(r.section()); });
        callbacks.put(UiNavigationRegistry.Module.XML_SECURITY, (r,c) -> { loadXMLSecurityContent(); expandXMLAccordionPane(r.section()); });
        callbacks.put(UiNavigationRegistry.Module.WSS_SECURITY, (r,c) -> { loadWssSecurityContent(); expandWssAccordionPane(r.section()); });
        callbacks.put(UiNavigationRegistry.Module.EMV, (r,c) -> { loadEMVContent(); expandEMVAccordionPane(r.section()); updateContentHeader("EMV Operations"); updateContentSubtitle("Session keys, ARQC/ARPC, and Track 2 data"); });
        callbacks.put(UiNavigationRegistry.Module.CLIPBOARD_SHELF, (r,c) -> { if(c instanceof ClipboardShelfController x) x.refresh(); });
        callbacks.put(UiNavigationRegistry.Module.HISTORY, (r,c) -> {
            if(r.variant()==UiNavigationRegistry.Variant.HISTORY_EXPORT) {
                if(c instanceof HistoryController x) x.focusExportActions();
                updateStatus("Choose Export Visible JSON or Export JSON Record in Recent Operations.");
            }
        });
        callbacks.put(UiNavigationRegistry.Module.CIPHER, (r,c) -> { loadCipherContent(); expandCipherAccordionPane(r.section()); });
        callbacks.put(UiNavigationRegistry.Module.AUTHENTICATION, (r,c) -> { loadAuthenticationContent(); expandAuthenticationAccordionPane(r.section()); });
        callbacks.put(UiNavigationRegistry.Module.PAYMENTS, (r,c) -> { loadPaymentsContent(); expandPaymentsAccordionPane(r.section()); });
        callbacks.put(UiNavigationRegistry.Module.SAVED_SESSIONS, (r,c) -> { savedSessionsCoordinator().show(); updateContentHeader("Saved Sessions"); updateContentSubtitle("Load or manage your saved workspaces"); });
        callbacks.put(UiNavigationRegistry.Module.PROCESS_DESIGNER, (r,c) -> {
            enterProcessDesignerWorkspace();
            if(processDesignerContainer != null && processDesignerContainer.root() instanceof TitledPane pane) pane.setExpanded(true);
            updateContentHeader("Process Designer");
            updateContentSubtitle("Visual workflow builder and execution engine");
        });
        return new NavigationRouter(
                () -> java.util.Arrays.stream(moduleHosts()).filter(java.util.Objects::nonNull).map(n -> (javafx.scene.Node)n).toList(),
                () -> java.util.stream.Stream.concat(contentContainer.getChildren().stream().filter(n -> n instanceof Label), java.util.stream.Stream.of(quickStartContainer, savedSessionsContainer)).filter(java.util.Objects::nonNull).toList(),
                this::navigationHost,
                this::resolveNavigationController,
                callbacks,
                this::exitProcessDesignerWorkspace,
                this::handleItemSelectedImpl);
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
            case JOSE -> joseController = ensureModule(jose, JOSEController.class);
            case COSE -> coseController = ensureModule(cose, COSEController.class);
            case WALLET -> walletController = ensureModule(wallet, WalletController.class);
            case EPOCH_CONVERTER, JSON_FORMATTER, SAVED_SESSIONS -> null;
            case KEYS_SYMMETRIC, KEYS_ASYMMETRIC -> keysController = keysContainerController = ensureModule(keysContainer, KeysController.class);
            case CERTIFICATES -> certificatesContainerController = ensureModule(certificatesContainer, CertificatesController.class);
            case GENERIC -> genericContainerController = ensureModule(genericContainer, GenericController.class);
            case POST_QUANTUM -> postQuantumContainerController = ensureModule(postQuantumContainer, PostQuantumController.class);
            case XML_SECURITY -> xmlSecurityContainerController = ensureModule(xmlSecurityContainer, XMLSignatureController.class);
            case WSS_SECURITY -> wssSecurityContainerController = ensureModule(wssSecurityContainer, WssSecurityController.class);
            case EMV -> emvController = emvContainerController = ensureModule(emvContainer, EMVController.class);
            case HISTORY -> historyViewController = ensureModule(historyView, HistoryController.class);
            case CLIPBOARD_SHELF -> clipboardShelfController = ensureModule(clipboardShelf, ClipboardShelfController.class);
            case CIPHER -> cipherController = cipherContainerController = ensureModule(cipherContainer, CipherController.class);
            case AUTHENTICATION -> authenticationContainerController = ensureModule(authenticationContainer, AuthenticationController.class);
            case PAYMENTS -> paymentsController = paymentsContainerController = ensureModule(paymentsContainer, PaymentsController.class);
            case PROCESS_DESIGNER -> processDesignerContainerController = ensureModule(processDesignerContainer, ProcessDesignerController.class);
        };
    }

    private void configureDeferredModules() {
        java.util.concurrent.Executor direct = Runnable::run;
        for (ModuleHost host : moduleHosts()) {
            if (host != null) host.configure(moduleLoader, direct);
        }
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
    private void connectShellServices(Object controller) {
        if (controller instanceof JOSEController jose) {
            jose.setReporter(this);
        } else if (controller instanceof COSEController cose) {
            cose.setReporter(this);
        } else if (controller instanceof WalletController wallet) {
            wallet.setReporter(this);
        } else if (controller instanceof HistoryController history) {
            history.setHistoryManager(historyManager());
            history.setOperationNavigator(this);
        } else if (controller instanceof ClipboardShelfController shelf) {
            shelf.setNavigator(this, this);
        } else if (controller instanceof GenericController generic) {
            generic.setStatusReporter(this);
            generic.setFormatControls(inputFormatCombo, outputFormatCombo);
            if (generic.getKeyCertificateWorkbenchController() != null) {
                generic.getKeyCertificateWorkbenchController().setStatusReporter(this);
            }
            if (generic.getCryptoEnvelopeInspectorController() != null) {
                generic.getCryptoEnvelopeInspectorController().setStatusReporter(this);
            }
        }
    }

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

    public void showAsyncProgress(String operationName) {
        if (asyncProgressBox != null) {
            if (asyncProgressLabel != null) {
                String title = (operationName != null && !operationName.isBlank()) ? operationName : i18n.text("progress.operation");
                asyncProgressLabel.setText(title + "…");
                asyncProgressLabel.setAccessibleText(title);
            }
            if (asyncCancelBtn != null) {
                asyncCancelBtn.setDisable(false);
            }
            asyncProgressBox.setManaged(true);
            asyncProgressBox.setVisible(true);
        }
    }

    public void updateAsyncProgressDetails(OperationExecutor.ProgressDetails details) {
        if (asyncProgressBox == null || details == null) return;
        if (!asyncProgressBox.isVisible()) {
            asyncProgressBox.setManaged(true);
            asyncProgressBox.setVisible(true);
        }
        if (asyncProgressLabel != null) {
            asyncProgressLabel.setText(details.getFormattedText());
            asyncProgressLabel.setAccessibleText(details.getFormattedText());
        }

        if (details.getTotalBytes() > 0) {
            double ratio = Math.min(1.0, (double) details.getBytesProcessed() / details.getTotalBytes());
            if (asyncProgressBar != null) {
                asyncProgressBar.setProgress(ratio);
                asyncProgressBar.setAccessibleText(String.format(java.util.Locale.US, "Progress: %d%%", Math.round(ratio * 100)));
                asyncProgressBar.setVisible(true);
                asyncProgressBar.setManaged(true);
            }
            if (asyncProgressIndicator != null) {
                asyncProgressIndicator.setVisible(false);
                asyncProgressIndicator.setManaged(false);
            }
        } else {
            if (asyncProgressIndicator != null) {
                asyncProgressIndicator.setProgress(-1);
                asyncProgressIndicator.setAccessibleText("Working: " + details.getOperationName());
                asyncProgressIndicator.setVisible(true);
                asyncProgressIndicator.setManaged(true);
            }
            if (asyncProgressBar != null) {
                asyncProgressBar.setVisible(false);
                asyncProgressBar.setManaged(false);
            }
        }
    }

    public void hideAsyncProgress() {
        if (asyncProgressBox != null) {
            asyncProgressBox.setVisible(false);
            asyncProgressBox.setManaged(false);
        }
    }

    @FXML
    private final java.util.concurrent.atomic.AtomicBoolean isShutdown = new java.util.concurrent.atomic.AtomicBoolean(false);

    public void handleCancelAsyncOperation() {
        boolean cancelled = operationExecutor.cancelCurrentOperation();
        if (cancelled) {
            if (asyncProgressLabel != null) {
                asyncProgressLabel.setText(i18n.text("progress.cancelling"));
            }
        } else if (operationExecutor.isInCommitPhase()) {
            if (asyncProgressLabel != null) {
                asyncProgressLabel.setText(i18n.text("progress.finishing"));
            }
            if (asyncCancelBtn != null) {
                asyncCancelBtn.setDisable(true);
            }
        } else {
            hideAsyncProgress();
        }
    }

    public void shutdown() {
        if (isShutdown.compareAndSet(false, true)) {
            if (clipboardShelfController != null) {
                clipboardShelfController.dispose();
            }
            if (operationExecutor != null) {
                operationExecutor.shutdown();
            }
        }
    }

    private void setupWindowLifecycleListeners() {
        javafx.scene.Node node = rootStackPane != null ? rootStackPane : asyncProgressBox;
        if (node == null) return;

        javafx.beans.value.ChangeListener<javafx.stage.Window> windowListener = (obsWindow, oldWindow, newWindow) -> {
            if (newWindow != null) {
                newWindow.addEventHandler(javafx.stage.WindowEvent.WINDOW_HIDING, e -> shutdown());
            }
        };

        javafx.beans.value.ChangeListener<javafx.scene.Scene> sceneListener = (obsScene, oldScene, newScene) -> {
            if (newScene != null) {
                if (newScene.getWindow() != null) {
                    newScene.getWindow().addEventHandler(javafx.stage.WindowEvent.WINDOW_HIDING, e -> shutdown());
                }
                newScene.windowProperty().addListener(windowListener);
            }
        };

        if (node.getScene() != null) {
            sceneListener.changed(null, null, node.getScene());
        }
        node.sceneProperty().addListener(sceneListener);
    }

    @FXML
    public void initialize() {
        configureDeferredModules();
        navigationRouter = createNavigationRouter();
        navigationChrome = new NavigationChromeCoordinator(inputFormatCombo, outputFormatCombo, inputFormatLabel,
                contractOperationLabel, contentTitleLabel, contentSubtitleLabel, breadcrumbContainer,
                breadcrumbSectionBtn, breadcrumbSep1, breadcrumbModuleBtn, breadcrumbSep2,
                breadcrumbOperationLabel, favoriteToggleBtn, FAVORITE_SHORTCUT, key -> {
                    if (genericContainerController != null) genericContainerController.setActiveFormatContractOperation(key);
                }, this::selectBreadcrumbSection, this::navigateToModule);
        // Module reporters are wired in connectShellServices as each module materializes.
        System.out.println("ModernMainController initializing...");
        com.cryptocarver.model.ClipboardShelfManager.getInstance().setReporter(this);

        operationExecutor.setProgressHandlers(
                this::showAsyncProgress,
                this::updateAsyncProgressDetails,
                this::hideAsyncProgress
        );

        setupWindowLifecycleListeners();

        setupLaboratoryMenu();
        initializeCommandPalette();
        syncMenuBarAccelerators();

        inlineErrorPresenter = new InlineErrorPresenter(
                errorBanner, errorBannerTitle, errorBannerRemedy,
                errorBannerGoToFieldBtn, errorBannerCopyDetailsBtn, errorBannerCloseBtn
        );

        if (securityTipLabel != null && securityTipBox != null) {
            securityTipLabel.textProperty().addListener((obs, oldVal, newVal) -> {
                boolean hasTip = newVal != null && !newVal.trim().isEmpty();
                securityTipBox.setVisible(hasTip);
                securityTipBox.setManaged(hasTip);
            });
        }

        statusBarPresenter = new StatusBarPresenter(statusLabel, statusVisibilityButton, statusLanguageLabel, i18n);

        if (visibilityProfileGroup != null) {
            com.cryptocarver.model.SecretVisibilityProfile profile = com.cryptocarver.model.AppSettings.getInstance().getSecretVisibilityProfile();
            for (javafx.scene.control.Toggle toggle : visibilityProfileGroup.getToggles()) {
                if (toggle instanceof javafx.scene.control.RadioMenuItem item && item.getText().contains(profile.name())) {
                    item.setSelected(true);
                    break;
                }
            }
        }

        installResponsiveLayoutSupport();

        // Connect Rail to SidePanel
        navigationRail.setSidePanel(sidePanel);

        // Handle item selection from SidePanel
        sidePanel.setOnItemSelected(this::handleItemSelected);
        navigationController = new NavigationController(navigationRail, sidePanel, this::handleItemSelected);
        navigationController.install();

        i18n.refreshFromSettings();
        i18nListener = locale -> {
            Runnable refresh = this::applyLocalization;
            if (Platform.isFxApplicationThread()) refresh.run();
            else Platform.runLater(refresh);
        };
        i18n.addLocaleChangeListener(i18nListener);
        applyLocalization();

        // Initialize History
        initializeHistory();
        refreshSessionTrailUI();

        // Load symmetric keys content (default)
        loadSymmetricKeysContent();

        // Show the symmetric keys by default
        showSymmetricKeys();
        restoreStartupLastRoute();

        // Apply default font size
        applyFontSize();
        // All static FXML content is available at this point. Install now so a
        // result written immediately after loading cannot miss the listener.
        // The method is idempotent for any later/dynamic invocation.
        installResultViewerSupport();
        Platform.runLater(this::installTableViewerSupport);

        System.out.println("ModernMainController initialized successfully!");
    }

    /** Applies shell strings without changing operation names, routes or technical values. */
    public void applyLocalization() {
        shellLocalizationCoordinator.applyLocalization(localizationView());
    }

    private ShellLocalizationCoordinator.View localizationView() {
        ShellLocalizationCoordinator.View view = new ShellLocalizationCoordinator.View(mainPane);
        view.text(fileMenu, "menu.file");
        view.text(editMenu, "menu.edit");
        view.text(viewMenu, "menu.view");
        view.text(securityMenu, "menu.security");
        view.text(toolsMenu, "menu.tools");
        view.text(helpMenu, "menu.help");
        view.text(laboratoryMenu, "menu.laboratory");
        view.text(languageMenu, "menu.language");
        view.text(appearanceMenu, "menu.appearance");
        view.text(importKeyMenuItem, "menu.importKey");
        view.text(exportScreenMenuItem, "menu.exportScreen");
        view.text(importScreenMenuItem, "menu.importScreen");
        view.text(saveSessionMenuItem, "menu.saveSession");
        view.text(exportSessionTrailMenuItem, "menu.exportSessionTrail");
        view.text(exportHistoryMenuItem, "menu.exportHistory");
        view.text(exitMenuItem, "menu.exit");
        view.text(clearInputMenuItem, "menu.clearInput");
        view.text(clearOutputMenuItem, "menu.clearOutput");
        view.text(copyOutputMenuItem, "menu.copyOutput");
        view.text(addToShelfMenuItem, "menu.addToShelf");
        view.text(quickStartMenuItem, "menu.quickStart");
        view.text(clipboardShelfMenuItem, "menu.clipboardShelf");
        view.text(commandPaletteMenuItem, "menu.commandPalette");
        view.text(toggleSidePanelMenuItem, "menu.toggleSidePanel");
        view.text(toggleInspectorMenuItem, "menu.toggleInspector");
        view.text(expandResultMenuItem, "menu.expandResult");
        view.text(expandTableMenuItem, "menu.expandTable");
        view.text(zoomInMenuItem, "menu.zoomIn");
        view.text(zoomOutMenuItem, "menu.zoomOut");
        view.text(resetViewMenuItem, "menu.resetView");
        view.text(visibilityFullLabMenuItem, "menu.visibility.full");
        view.text(visibilityMaskedMenuItem, "menu.visibility.masked");
        view.text(visibilityRedactedMenuItem, "menu.visibility.redacted");
        view.text(epochMenuItem, "menu.epoch");
        view.text(jsonMenuItem, "menu.json");
        view.text(byteInspectorMenuItem, "menu.byteInspector");
        view.text(clearKeyCacheMenuItem, "menu.clearKeyCache");
        view.text(shortcutsMenuItem, "menu.shortcuts");
        view.text(diagnosticsMenuItem, "menu.diagnostics");
        view.text(aboutMenuItem, "menu.about");
        view.text(laboratoryQuickStartMenuItem, "menu.quickStart");
        view.text(languageSystemMenuItem, "app.language.system");
        view.text(languageEsMenuItem, "app.language.es");
        view.text(languageEnMenuItem, "app.language.en");
        view.text(themeSystemMenuItem, "app.theme.system");
        view.text(themeLightMenuItem, "app.theme.light");
        view.text(themeDarkMenuItem, "app.theme.dark");
        view.text(toolbarSaveSessionButton, "menu.saveSession");
        view.text(toolbarClearButton, "toolbar.clear");
        view.text(toolbarExpandButton, "toolbar.expand");
        view.text(toolbarShelfButton, "toolbar.addShelf");
        view.text(toolbarCopyButton, "toolbar.copy");
        view.text(inputFormatLabel, "toolbar.payloadFormat");
        view.text(outputFormatLabel, "toolbar.output");
        view.text(resultSaveStepButton, "sessionTrail.saveStep");
        view.text(inspectorSessionTrailTitle, "sessionTrail.title");
        view.text(inspectorAddSessionStepButton, "sessionTrail.addCurrent");
        view.text(inspectorExportSessionTrailButton, "sessionTrail.exportAll");
        view.text(inspectorClearSessionTrailButton, "sessionTrail.clearShort");
        view.text(inspectorOpenSessionStepButton, "sessionTrail.viewData");
        view.text(resultLastLabel, "result.last");
        view.text(resultAlgorithmStaticLabel, "result.algorithm");
        view.text(errorBannerTitle, "error.failed");
        view.text(errorBannerRemedy, "error.remedy");
        view.text(errorBannerGoToFieldBtn, "error.goToField");
        view.text(errorBannerCopyDetailsBtn, "error.copyDetails");
        view.text(guideBackBtn, "guide.back");
        view.text(guideNextBtn, "guide.next");
        view.text(guideSkipBtn, "guide.skip");
        view.text(guideExitBtn, "guide.exit");
        view.text(asyncProgressLabel, "progress.working");
        view.text(asyncCancelBtn, "progress.cancel");
        view.text(inspectorTitleLabel, "inspector.title");
        view.text(inspectorInputBytesTitle, "inspector.inputBytes");
        view.text(inspectorOutputBytesTitle, "inspector.outputBytes");
        view.text(inspectorAlgorithmTitle, "inspector.algorithm");
        view.text(inspectorSecurityTipsTitle, "inspector.securityTips");
        view.text(inspectorWarningTitle, "inspector.warning");
        view.text(inspectorHistoryTitle, "inspector.history");
        view.text(inspectorExportJsonButton, "inspector.exportJson");
        view.text(inspectorClearHistoryButton, "toolbar.clear");
        view.text(commandEscapeLabel, "command.escape");
        view.text(commandEmptyLabel, "command.empty");
        view.text(commandNavigateLabel, "command.navigate");
        view.text(commandSelectLabel, "command.select");
        view.text(commandCancelLabel, "command.cancel");
        view.text(commandTitleLabel, "command.title");
        view.laboratoryMenu(laboratoryMenu);
        view.languageItems(languageSystemMenuItem, languageEsMenuItem, languageEnMenuItem);
        view.themeItems(themeSystemMenuItem, themeLightMenuItem, themeDarkMenuItem,
                AppSettings.getInstance().getThemePreference());
        view.toolbarSearch(toolbarSearchButton, COMMAND_PALETTE_SHORTCUT);
        view.accessible(toolbarSearchButton);
        view.accessible(toolbarSaveSessionButton);
        view.accessible(toolbarClearButton);
        view.accessible(toolbarExpandButton);
        view.accessible(toolbarShelfButton);
        view.accessible(toolbarCopyButton);
        view.accessible(asyncCancelBtn);
        view.accessible(resultExpandButton, "a11y.resultExpand");
        view.accessible(resultShelfButton, "a11y.resultShelf");
        view.accessible(resultCopyButton, "a11y.resultCopy");
        view.accessible(resultSaveStepButton, "a11y.sessionTrailSaveStep");
        view.accessible(inspectorAddSessionStepButton, "a11y.sessionTrailSaveStep");
        view.accessible(inspectorPreviousSessionStepButton, "sessionTrail.previousStep");
        view.accessible(inspectorNextSessionStepButton, "sessionTrail.nextStep");
        view.accessible(inspectorOpenSessionStepButton, "sessionTrail.viewData");
        view.accessible(inspectorToggleButton, "a11y.inspectorToggle");
        view.accessible(errorBannerCloseBtn, "a11y.errorClose");
        view.accessible(inspectorExportSessionTrailButton, "sessionTrail.exportTitle");
        view.accessible(inspectorClearSessionTrailButton, "sessionTrail.clear");
        view.accessible(inputFormatCombo, "a11y.payloadFormat");
        view.accessible(outputFormatCombo, "a11y.outputFormat");
        view.accessible(commandSearchField, "a11y.commandSearch");
        view.accessible(favoriteToggleBtn, "a11y.favorite");
        view.accessible(resultStatusBadge, "result.status");
        view.accessible(errorBannerGoToFieldBtn, "a11y.errorGoToField");
        view.accessible(errorBannerCopyDetailsBtn, "a11y.errorCopyDetails");
        view.accessibleHelp(inputFormatCombo, "toolbar.payloadTooltip");
        view.accessibleHelp(outputFormatCombo, "a11y.outputFormat");
        view.accessibleHelp(commandSearchField, "command.prompt");
        view.accessibleHelp(errorBannerTitle, "a11y.errorTitle");
        view.accessibleHelp(errorBannerRemedy, "a11y.errorRemedy");
        view.accessibleHelp(errorBannerGoToFieldBtn, "a11y.errorGoToFieldHelp");
        view.accessibleHelp(errorBannerCopyDetailsBtn, "a11y.errorCopyDetailsHelp");
        view.accessibleHelp(errorBannerCloseBtn, "a11y.errorCloseHelp");
        view.accessibleHelp(favoriteToggleBtn, "favorite.tooltip", FAVORITE_SHORTCUT);
        view.tooltip(inputFormatLabel, "toolbar.payloadTooltip");
        view.tooltip(inputFormatCombo, "toolbar.payloadTooltip");
        view.tooltip(inspectorExportJsonButton, "inspector.exportJsonTooltip");
        view.tooltip(inspectorClearSessionTrailButton, "sessionTrail.clear");
        view.prompt(commandSearchField, "command.prompt");
        view.finishPainting(() -> {
            refreshSessionTrailNavigation();
            if (navigationRail != null) {
                navigationRail.refreshLocalizedText();
            }
            if (sidePanel != null) {
                sidePanel.refreshLocalizedText();
            }
            updateBreadcrumbs(currentActiveOperation);
            updateFavoriteToggleState(currentActiveOperation);
            if (statusLabel != null && (statusLabel.getText() == null || statusLabel.getText().isBlank()
                    || statusLabel.getText().equals("Ready") || statusLabel.getText().equals("Listo"))) {
                statusBarPresenter.showStatus(i18n.text("status.ready"));
            }
            if (statusBarPresenter != null) {
                statusBarPresenter.refreshContext(AppSettings.getInstance().getSecretVisibilityProfile());
            }
            if (inlineErrorPresenter != null && inlineErrorPresenter.getCurrentError() != null) {
                inlineErrorPresenter.showError(localizedError(inlineErrorPresenter.getCurrentError()),
                        rootStackPane != null ? rootStackPane : mainPane);
            }
        });
        return view;
    }

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
        mainPane.widthProperty().addListener((observable, previousWidth, newWidth) ->
                updateResponsiveLayout(newWidth.doubleValue()));
        Platform.runLater(() -> updateResponsiveLayout(mainPane.getWidth()));
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

        // Update header
        updateContentHeader(itemName);

        // Update inspector
        updateInspector(itemName);

        if (!activateNavigationRoute(itemName)) {
            showPlaceholderContent(itemName);
        }

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

    public void reopenRecentHistoryCommand(com.cryptocarver.model.HistoryCommand item) { historyCoordinator().reopenHistoryOperation(item); }

    /**
     * Opens a recorded execution for inspection. Selecting an entry under
     * "Recent Executions" is a view action; restoring its recipe remains the
     * explicit job of the Reopen button in the History view.
     */
    public void showRecentHistoryCommand(com.cryptocarver.model.HistoryCommand item) {
        historyCoordinator().showRecentHistoryCommand(item);
    }

    @Override
    public void reopenHistoryOperation(com.cryptocarver.model.HistoryCommand item) {
        historyCoordinator().reopenHistoryOperation(item);
    }

    private java.util.List<com.cryptocarver.model.OperationDetail> visibleHistoryDetails(
            com.cryptocarver.model.HistoryCommand item) {
        return historyCoordinator().visibleHistoryDetails(item);
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
        inspectorPresenter().present(operation, input, output, details);

        if (details != null && inlineErrorPresenter != null) {
            boolean isInvalidResult = details.stream().anyMatch(d ->
                    "Result".equalsIgnoreCase(d.name()) && d.value() != null && d.value().toUpperCase().contains("INVALID"));
            if (!isInvalidResult) {
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
                module -> switch (module) {
                    case JOSE -> new ScreenConfigurationCoordinator.ConfigurationTarget(joseController, jose);
                    case COSE -> new ScreenConfigurationCoordinator.ConfigurationTarget(coseController, cose);
                    case WALLET -> new ScreenConfigurationCoordinator.ConfigurationTarget(walletController, wallet);
                    case KEYS_SYMMETRIC, KEYS_ASYMMETRIC -> new ScreenConfigurationCoordinator.ConfigurationTarget(keysContainerController, keysContainer);
                    case CERTIFICATES -> new ScreenConfigurationCoordinator.ConfigurationTarget(certificatesContainerController, certificatesContainer);
                    case GENERIC -> new ScreenConfigurationCoordinator.ConfigurationTarget(genericContainerController, genericContainer);
                    case POST_QUANTUM -> new ScreenConfigurationCoordinator.ConfigurationTarget(postQuantumContainerController, postQuantumContainer);
                    case XML_SECURITY -> new ScreenConfigurationCoordinator.ConfigurationTarget(xmlSecurityContainerController, xmlSecurityContainer);
                    case WSS_SECURITY -> new ScreenConfigurationCoordinator.ConfigurationTarget(wssSecurityContainerController, wssSecurityContainer);
                    case EMV -> new ScreenConfigurationCoordinator.ConfigurationTarget(emvContainerController, emvContainer);
                    case CIPHER -> new ScreenConfigurationCoordinator.ConfigurationTarget(cipherContainerController, cipherContainer);
                    case AUTHENTICATION -> new ScreenConfigurationCoordinator.ConfigurationTarget(authenticationContainerController, authenticationContainer);
                    case PAYMENTS -> new ScreenConfigurationCoordinator.ConfigurationTarget(paymentsContainerController, paymentsContainer);
                    case PROCESS_DESIGNER -> new ScreenConfigurationCoordinator.ConfigurationTarget(processDesignerContainerController, processDesignerContainer);
                    default -> null;
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

    private HistoryCoordinator historyCoordinator() {
        if (historyCoordinator == null) {
            historyCoordinator = new HistoryCoordinator(
                    this::historyManager, () -> sidePanel, () -> historyViewController,
                    this::captureHistoryState, () -> currentActiveOperation,
                    () -> inputFormatCombo == null ? null : inputFormatCombo.getValue(),
                    () -> outputFormatCombo == null ? null : outputFormatCombo.getValue(),
                    this::navigateToModule, this::restoreHistoryRecipe,
                    (operation, details) -> updateInspector(operation, null, null, details),
                    this::visibleOperationDetails, this::updateStatus, () -> windowOf(mainPane),
                    dialogService, i18n);
        }
        return historyCoordinator;
    }

    private void initializeHistory() {
        historyCoordinator().initialize();
    }

    /**
     * Refreshes every surface that shows history.
     *
     * <p>The Inspector's own history card list was removed in favour of a single History view,
     * so what is left is the side-panel's recent-operations group and, once the user has opened
     * it, the History module's table.
     */
    private void refreshHistoryUI() {
        historyCoordinator().refresh();
    }

    @Override
    public void refreshHistoryNavigation() {
        historyCoordinator().refreshNavigation();
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
        historyCoordinator().addToHistory(operation, details, currentActiveOperation);
    }

    private void addToHistory(String operation, java.util.List<com.cryptocarver.model.OperationDetail> details,
                              String navigationOperation) {
        historyCoordinator().addToHistory(operation, details, navigationOperation);
    }

    /** Exposes the shared history store to the FXML history module. */
    public com.cryptocarver.model.HistoryManager getHistoryManager() { return historyCoordinator().historyManager(); }

    /** Restores an operation selected from the modular history view. */
    public void restoreOperationState(java.util.Map<String, Object> state, String operation) {
        historyCoordinator().restoreOperationState(state, operation);
    }

    private void restoreHistoryRecipe(java.util.Map<String, Object> state, String operation) {
        handleItemSelected(operation);
        java.util.List<javafx.scene.Node> redacted = UiStateSnapshot.restoreHistoryRecipe(this, state);
        if (redacted != null && !redacted.isEmpty()) {
            updateStatus("Restored configuration for: " + operation + ". Re-enter redacted sensitive values.");
            // Recipes span every module; focus the first redacted field on the reopened screen.
            javafx.application.Platform.runLater(() -> redacted.stream().filter(ModernMainController::isShowing)
                    .findFirst().ifPresent(javafx.scene.Node::requestFocus));
        } else {
            updateStatus("Restored state for: " + operation);
        }
    }

    private static boolean isShowing(javafx.scene.Node node) {
        if (node.getScene() == null) return false;
        for (javafx.scene.Node current = node; current != null; current = current.getParent()) {
            if (!current.isVisible()) return false;
        }
        return true;
    }

    @FXML
    private void handleExportHistory() {
        historyCoordinator().chooseAndExport();
    }

    void exportHistoryTo(java.nio.file.Path target,
                         com.cryptocarver.model.SecretVisibilityProfile visibility) throws IOException {
        historyCoordinator().exportTo(target, visibility);
    }

    /** Materializes and presents the modular Recent Operations view. */
    /**
     * The accordion of a materialized module, wherever it sits under its host.
     *
     * <p>A module used to be inlined into its container, which put its accordion one level
     * down. Deferred modules are loaded into a {@link ModuleHost}, so the host's child is the
     * module's FXML root and the accordion is deeper still. Looking only at direct children
     * finds nothing, and since every caller here treats "no accordion" as "nothing to expand",
     * that failure is silent: navigating to an operation opens its module but leaves the
     * matching pane closed.
     */
    private static Accordion moduleAccordion(ModuleHost host) {
        return host == null ? null : findAccordion(host);
    }

    private static Accordion findAccordion(Node node) {
        if (node instanceof Accordion accordion) return accordion;
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Accordion found = findAccordion(child);
                if (found != null) return found;
            }
        }
        return null;
    }

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

    private void expandCipherAccordionPane(String itemName) {
        boolean symmetric = itemName.contains("Symmetric") || itemName.contains("AES")
                || itemName.contains("DES") || itemName.contains("Padding");
        if (cipherController != null) cipherController.showSymmetricWorkspace(symmetric);
        if (symmetric) {
            mainScrollPane.setVvalue(0);
            return;
        }
        Accordion accordion = moduleAccordion(cipherContainer);

        if (accordion != null) {
            String targetPane = "";
            if (itemName.contains("Format-Preserving")) {
                targetPane = "Format-Preserving";
            } else if (itemName.contains("File Cipher")) {
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
                if (!targetPane.isEmpty()
                        && ModulePaneMatcher.matches(pane, targetPane, ModuleTextCatalog.cipher())) {
                    accordion.setExpandedPane(pane);
                    revealExpandedPane(pane);
                    break;
                }
            }
        }
    }

    private void expandAuthenticationAccordionPane(String itemName) {
        Accordion accordion = moduleAccordion(authenticationContainer);

        if (accordion != null) {
            String targetPane = "";
            if (itemName.contains("Signature") || itemName.contains("Sign")) {
                targetPane = "Signatures";
            } else if (itemName.contains("MAC")) {
                targetPane = "MAC";
            }

            for (TitledPane pane : accordion.getPanes()) {
                if (!targetPane.isEmpty()
                        && ModulePaneMatcher.matches(pane, targetPane, ModuleTextCatalog.authentication())) {
                    accordion.setExpandedPane(pane);
                    revealExpandedPane(pane);
                    break;
                }
            }
        }
    }

    private void expandPaymentsAccordionPane(String itemName) {
        Accordion accordion = moduleAccordion(paymentsContainer);

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
                if (!targetPane.isEmpty()
                        && ModulePaneMatcher.matches(pane, targetPane, ModuleTextCatalog.payments())) {
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
        contentPlaceholderLabel.setText(requestedOperation
                + "\n\nNo view is registered for this legacy operation. Select a tool from the side panel.");
        contentPlaceholderLabel.setGraphic(IconRegistry.icon("clipboard"));
        contentPlaceholderLabel.setManaged(true);
        contentPlaceholderLabel.setVisible(true);
        updateContentHeader(requestedOperation);
        updateContentSubtitle("No module available");
    }

    private void expandAccordionPane(String paneName) {
        if (keysController == null) return;
        // Keys was the one module that expanded a pane without scrolling to it. That went
        // unnoticed while every destination lived in the accordion, which lifts the pane it
        // opens near the top by collapsing the previous one; the included panes below it have
        // no such effect and stayed off-screen.
        revealExpandedPane(keysController.expandSymmetricPane(paneName));
    }

    private void expandAsymmetricAccordionPane(String paneName) {
        if (keysController == null) return;
        revealExpandedPane(keysController.expandAsymmetricPane(paneName));
    }

    private void expandCertificatesAccordionPane(String paneName) {
        if (certificatesContainerController != null) certificatesContainerController.expandPane(paneName);
    }

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
        if (currentActiveOperation == null) {
            updateStatus(i18n.text("status.noActiveOperation"));
            return;
        }

        if (isContainerVisible(emvContainer) && emvController != null) {
            emvController.handleClear();
        } else if (isContainerVisible(cipherContainer) && cipherController != null) {
            cipherController.handleClear();
        } else if (isContainerVisible(authenticationContainer) && authenticationContainerController != null) {
            authenticationContainerController.handleClear();
        } else if (isContainerVisible(keysContainer) && keysController != null) {
            if (keysController.isSymmetricSectionVisible()) keysController.handleClear();
            else keysController.handleClearAsymmetric();
        } else if (isContainerVisible(genericContainer) && genericContainerController != null) {
            genericContainerController.handleClear();
        } else if (isContainerVisible(processDesignerContainer) && processDesignerContainerController != null) {
            processDesignerContainerController.handleClearCanvas();
        } else if (isContainerVisible(certificatesContainer)) {
            // Certificate clearing not fully implemented via global toolbar ye
        }

        clearPublishedResultSnapshot();
        updateStatus(i18n.text("status.inputCleared"));
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
    public void handleAddCurrentOutputToShelf() {
        if (keysController != null && isActiveAsymmetricKeyGeneration()) {
            keysController.handleGlobalAsymmetricShelfAction(currentActiveOperation);
            return;
        }
        // The generated symmetric key lives in a TextField the result tracker does not capture.
        if (keysController != null && "Key Generation".equals(currentActiveOperation)) {
            keysController.handleGlobalSymmetricShelfAction();
            return;
        }
        // An explicitly focused/updated rendered result wins over a sibling
        // Workbench that happens to remain visible in the generic accordion.
        TextArea area = resultAreaTracker.shelfCaptureArea(null);
        if (area == null) {
            KeyCertificateWorkbenchController workbench = activeWorkbenchForShelf();
            if (workbench != null) {
                workbench.sendCurrentMaterialToShelf();
                return;
            }
            area = resultAreaTracker.shelfCaptureArea(mainPane);
        }
        String content = resolveShelfCaptureText(area);
        if (content == null || content.isBlank()) {
            updateStatus(isShelfCaptureBlockedByVisibility(area)
                    ? "Action blocked: output hidden by visibility policy."
                    : "No current output available.");
            showInfo("No result available", "Run an operation with output before adding it to Clipboard Shelf.");
            return;
        }
        handleAddToClipboardShelfSecure(area, null);
    }

    private boolean isActiveAsymmetricKeyGeneration() {
        return switch (currentActiveOperation) {
            case "RSA Key Generation", "ECDSA Key Generation", "DSA Key Generation", "EdDSA Key Generation" -> true;
            default -> false;
        };
    }

    private KeyCertificateWorkbenchController activeWorkbenchForShelf() {
        if (!isContainerVisible(genericContainer) || genericContainerController == null) return null;
        KeyCertificateWorkbenchController workbench =
                genericContainerController.getKeyCertificateWorkbenchController();
        return workbench != null && workbench.isShelfMaterialViewVisible() ? workbench : null;
    }

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

    String resolveCurrentOutputText() {
        return resolveResultText(preferredResultArea());
    }

    private TextArea preferredResultArea() {
        return resultAreaTracker.preferred(mainPane, hasPublishedPayload());
    }

    private boolean hasPublishedPayload() {
        if (lastPublishedResultSnapshot == null) return false;
        if (lastPublishedResultSnapshot.getEnrichedOutput() != null
                && !lastPublishedResultSnapshot.getEnrichedOutput().isBlank()) return true;
        byte[] output = lastPublishedResultSnapshot.getOutput();
        return output != null && output.length > 0;
    }

    private String resolveResultText(TextArea requestedArea) {
        if (ResultAreaTracker.isKeyPairResultArea(requestedArea) && resultAreaTracker.isRegistered(requestedArea)) {
            return renderResultArea(requestedArea);
        }
        if (lastPublishedResultSnapshot != null) {
            return renderPublishedResult(lastPublishedResultSnapshot,
                    com.cryptocarver.model.AppSettings.getInstance().getSecretVisibilityProfile());
        }
        if (resultAreaTracker.isRegistered(requestedArea) && !requestedArea.isEditable()) {
            String rendered = renderResultArea(requestedArea);
            if (rendered != null && !rendered.isBlank()) return rendered;
        }
        TextArea fallback = resultAreaTracker.findVisible(mainPane);
        if (fallback != null && resultAreaTracker.isRegistered(fallback) && !fallback.isEditable()) {
            String rendered = renderResultArea(fallback);
            if (rendered != null && !rendered.isBlank()) return rendered;
        }
        return "";
    }

    /**
     * Resolves only a real output for Shelf capture. A summary assembled from
     * public details is useful to the viewer but is not a captured artifact.
     */
    private String resolveShelfCaptureText(TextArea requestedArea) {
        TextArea area = requestedArea;
        if (area == null) {
            area = resultAreaTracker.shelfCaptureArea(mainPane);
        }
        if (resultAreaTracker.isValidShelfCaptureArea(area)) {
            String visible = renderResultArea(area);
            if (visible == null || visible.isBlank()
                    || "***MASKED***".equals(visible)
                    || isPrivateMaterialPlaceholder(visible)) {
                return "";
            }
            return visible;
        }
        com.cryptocarver.model.OperationResult snapshot = shelfSnapshot();
        if (snapshot == null) return "";
        boolean hasArtifact = (snapshot.getEnrichedOutput() != null
                && !snapshot.getEnrichedOutput().isBlank())
                || (snapshot.getOutput() != null
                && snapshot.getOutput().length > 0);
        return hasArtifact ? renderPublishedResult(snapshot,
                com.cryptocarver.model.AppSettings.getInstance().getSecretVisibilityProfile()) : "";
    }

    /**
     * The published result, but only while its screen is still the active one:
     * after navigating elsewhere it must not be added to the Shelf under the
     * new screen's name.
     */
    private com.cryptocarver.model.OperationResult shelfSnapshot() {
        return java.util.Objects.equals(lastPublishedScreen, currentActiveOperation) ? lastPublishedResultSnapshot : null;
    }

    private boolean isShelfCaptureBlockedByVisibility(TextArea area) {
        return com.cryptocarver.model.ResultPresentationPolicy.isShelfCaptureBlockedByVisibility(
                classificationForResultArea(area), com.cryptocarver.model.AppSettings.getInstance().getSecretVisibilityProfile());
    }

    private boolean isPrivateMaterialPlaceholder(String text) {
        return com.cryptocarver.model.ResultPresentationPolicy.isPrivateMaterialPlaceholder(text);
    }

    private String renderResultArea(TextArea area) {
        if (area == null || area.getText() == null || area.getText().isBlank()) {
            return "";
        }
        com.cryptocarver.model.OperationDetail.Classification classification = classificationForResultArea(area);
        com.cryptocarver.model.SecretVisibilityProfile visibility =
                com.cryptocarver.model.AppSettings.getInstance().getSecretVisibilityProfile();
        if (classification == com.cryptocarver.model.OperationDetail.Classification.SECRET) {
            if (visibility == com.cryptocarver.model.SecretVisibilityProfile.REDACTED) return "";
            if (visibility == com.cryptocarver.model.SecretVisibilityProfile.MASKED) return "***MASKED***";
        } else if (classification == com.cryptocarver.model.OperationDetail.Classification.SENSITIVE
                && !AppSettings.isFullLab()) {
            return "***MASKED***";
        }
        return area.getText();
    }

    private com.cryptocarver.model.OperationDetail.Classification classificationForResultArea(TextArea area) {
        if (ResultAreaTracker.isPrivateKeyResultArea(area)) {
            return com.cryptocarver.model.OperationDetail.Classification.SECRET;
        }
        if (ResultAreaTracker.isKeyPairResultArea(area)
                && area.getId().toLowerCase(java.util.Locale.ROOT).contains("publickeyarea")) {
            return com.cryptocarver.model.OperationDetail.Classification.PUBLIC;
        }
        if (lastPublishedResultSnapshot != null
                && resultAreaTracker.isCurrentResultArea(area, true)) {
            return classifyPublishedResult(lastPublishedResultSnapshot);
        }
        if (area != null) {
            String id = area.getId() == null ? "" : area.getId().toLowerCase(java.util.Locale.ROOT);
            if (id.contains("privatekey") || id.contains("secret") || id.contains("kdf") || id.contains("pin")
                    || id.contains("pass") || id.contains("pwd") || id.contains("cvv") || id.contains("dukpt")
                    || id.contains("keywrap")) {
                return com.cryptocarver.model.OperationDetail.Classification.SECRET;
            }
            if (id.contains("key") || id.contains("mac") || id.contains("iv") || id.contains("cipher")) {
                return com.cryptocarver.model.OperationDetail.Classification.SENSITIVE;
            }
        }
        if (lastPublishedResultSnapshot != null) {
            return classifyPublishedResult(lastPublishedResultSnapshot);
        }
        return com.cryptocarver.model.OperationDetail.Classification.PUBLIC;
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
    public void handleShowKeyboardShortcuts() {
        VBox contentBox = new VBox(10);
        contentBox.setPrefWidth(540);
        contentBox.setStyle("-fx-padding: 10;");

        Label intro = new Label("System Keyboard Shortcuts:");
        intro.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        contentBox.getChildren().add(intro);

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(8);
        grid.getStyleClass().add("quick-start-card");

        int row = 0;
        for (com.cryptocarver.model.KeyboardShortcutEntry shortcut : com.cryptocarver.model.KeyboardShortcutRegistry.getShortcuts()) {
            Label comboLabel = new Label(shortcut.getDisplayCombination());
            comboLabel.getStyleClass().add("quick-start-title");

            Label actionLabel = new Label(shortcut.getActionName());
            actionLabel.getStyleClass().add("heading-text");
            actionLabel.setStyle("-fx-font-size: 12px;");

            Label descLabel = new Label(shortcut.getDescription());
            descLabel.getStyleClass().add("quick-start-description");

            grid.add(comboLabel, 0, row);
            grid.add(actionLabel, 1, row);
            grid.add(descLabel, 2, row);
            row++;
        }

        ScrollPane scrollPane = new ScrollPane(grid);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(340);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        contentBox.getChildren().add(scrollPane);
        dialogService.show(Alert.AlertType.INFORMATION, windowOf(mainPane),
                "Keyboard Shortcuts", "CryptoCarver Keyboard Shortcuts", contentBox, ButtonType.OK);
    }

    @FXML
    private void handleAbout() {
        dialogService.show(Alert.AlertType.INFORMATION, windowOf(mainPane), "About CryptoCarver", "CryptoCarver",
                new Label("A comprehensive tool for cryptographic operations.\n\n" +
                "Version: 1.0.0\n" +
                "Author: Felipe Rodríguez Fonte\n" +
                "Contact: felipe.rodriguez.fonte@gmail.com\n\n" +
                "Features:\n" +
                "- Symmetric & Asymmetric Encryption\n" +
                "- Digital Signatures & Certificates\n" +
                "- Payments (EMV, PIN, CVV)\n" +
                "- JOSE (JWT, JWE, JWK)\n" +
                "- ASN.1 Analysis"), ButtonType.OK);
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
        String diagnosticText = AppDiagnostics.report(describePrimaryDisplay());
        TextArea report = new TextArea(diagnosticText);
        report.setEditable(false);
        report.setWrapText(false);
        report.setPrefColumnCount(68);
        report.setPrefRowCount(15);
        report.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");

        ButtonType copyButton = new ButtonType("Copy report", ButtonBar.ButtonData.LEFT);
        java.util.Optional<ButtonType> selected = dialogService.show(Alert.AlertType.INFORMATION, windowOf(mainPane),
                "CryptoCarver diagnostics", "Runtime information (safe to copy)", report, copyButton, ButtonType.OK);
        if (selected.isPresent() && selected.get() == copyButton) {
            javafx.scene.input.ClipboardContent clipboard = new javafx.scene.input.ClipboardContent();
            clipboard.putString(diagnosticText);
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(clipboard);
            updateStatus("Diagnostics copied to clipboard");
        }
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
        if (inlineErrorPresenter != null) {
            inlineErrorPresenter.hideBanner();
        }
    }

    public void showWarning(String title, String message) {
        if ("true".equals(System.getProperty("test.mode"))) {
            System.out.println("SHOW_WARNING: " + title + " - " + message);
            return;
        }
        dialogService.warning(windowOf(mainPane), title, message);
    }

    @Override
    public void showInfo(String title, String message) {
        if ("true".equals(System.getProperty("test.mode"))) {
            System.out.println("SHOW_INFO: " + title + " - " + message);
            return;
        }
        dialogService.info(windowOf(mainPane), title, message);
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

    private void expandGenericAccordionPane(String paneName) { NavigationRouter.expandByTitle(genericContainer, paneName, ModuleTextCatalog.generic(), this::revealExpandedPane); }

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

    private void expandEMVAccordionPane(String title) {
        Accordion acc = moduleAccordion(emvContainer);
        if (title != null && !title.isBlank() && acc != null) {
            for (TitledPane pane : acc.getPanes()) {
                if (ModulePaneMatcher.matches(pane, title, ModuleTextCatalog.emv())) {
                    acc.setExpandedPane(pane);
                    revealExpandedPane(pane);
                    break;
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
    public void handleSaveSession() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(i18n.text("dialog.saveSession.title"));
        dialog.setHeaderText(i18n.text("dialog.saveSession.header"));
        TextField nameField = new TextField("My Session");
        nameField.setPromptText(i18n.text("dialog.saveSession.prompt"));
        CheckBox includeSecrets = new CheckBox(i18n.text("savedSessions.includeSecrets"));
        includeSecrets.setSelected(false);
        includeSecrets.setDisable(AppSettings.getInstance().getSecretVisibilityProfile()
                == com.cryptocarver.model.SecretVisibilityProfile.REDACTED);
        long sensitiveCount = captureUIState().entrySet().stream()
                .filter(entry -> UiStateSnapshot.holdsSecretValue(entry.getKey(), entry.getValue())).count();
        if (!sessionTrailState.log().isEmpty()) sensitiveCount++;
        final long secretsCount = sensitiveCount;
        Label secretNotice = new Label(i18n.text("savedSessions.redactedCount", secretsCount));
        secretNotice.setWrapText(true);
        secretNotice.setVisible(secretsCount > 0);
        secretNotice.setManaged(secretsCount > 0);
        includeSecrets.selectedProperty().addListener((obs, wasSelected, selected) -> {
            secretNotice.setText(selected ? i18n.text("savedSessions.secretsEncrypted")
                    : i18n.text("savedSessions.redactedCount", secretsCount));
            secretNotice.setVisible(selected || secretsCount > 0);
            secretNotice.setManaged(selected || secretsCount > 0);
        });
        VBox content = new VBox(10, new Label(i18n.text("dialog.saveSession.prompt")), nameField,
                includeSecrets, secretNotice);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        java.util.Optional<ButtonType> result = dialog.showAndWait();
        if (result.orElse(ButtonType.CANCEL) != ButtonType.OK || nameField.getText().trim().isEmpty()) return;
        char[] password = null;
        if (includeSecrets.isSelected()) {
            PasswordField field = new PasswordField();
            field.setPromptText(i18n.text("savedSessions.passwordPrompt"));
            PasswordField confirmation = new PasswordField();
            confirmation.setPromptText(i18n.text("savedSessions.passwordConfirmPrompt"));
            Dialog<ButtonType> passwordDialog = new Dialog<>();
            passwordDialog.setTitle(i18n.text("savedSessions.passwordTitle"));
            passwordDialog.setHeaderText(i18n.text("savedSessions.passwordRequired"));
            passwordDialog.getDialogPane().setContent(new VBox(8, field, confirmation));
            passwordDialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            boolean accepted = passwordDialog.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
            password = field.getText().toCharArray();
            char[] repeated = confirmation.getText().toCharArray();
            field.clear();
            confirmation.clear();
            boolean matches = java.util.Arrays.equals(password, repeated);
            java.util.Arrays.fill(repeated, '\0');
            if (!accepted || !matches || password.length < 8) {
                java.util.Arrays.fill(password, '\0');
                if (accepted) showWarning(i18n.text("savedSessions.passwordTitle"),
                        i18n.text(matches ? "savedSessions.passwordTooShort" : "savedSessions.passwordMismatch"));
                return;
            }
        }
        savedSessionsCoordinator().save(nameField.getText(), password);
    }

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
        if (postQuantumContainerController == null) postQuantumContainerController = ensureModule(postQuantumContainer, PostQuantumController.class);
        if (postQuantumContainerController != null) {
            postQuantumContainerController.initModule(this);
        }
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
        if (xmlSecurityContainerController == null) xmlSecurityContainerController = ensureModule(xmlSecurityContainer, XMLSignatureController.class);
        if (xmlSecurityContainerController != null) {
            xmlSecurityContainerController.initModule(this);
        }
    }

    private void expandXMLAccordionPane(String itemName) {
        if (xmlSecurityContainerController != null) {
            xmlSecurityContainerController.expandAccordionPane(itemName);
        }
    }

    private void loadWssSecurityContent() {
        if (wssSecurityContainerController == null) wssSecurityContainerController = ensureModule(wssSecurityContainer, WssSecurityController.class);
        if (wssSecurityContainerController != null) {
            wssSecurityContainerController.initModule(this);
        }
    }

    private void expandWssAccordionPane(String itemName) {
        if (wssSecurityContainerController != null) {
            wssSecurityContainerController.expandAccordionPane(itemName);
        }
    }

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
        if (mainMenuBar == null) return;
        boolean hasLabMenu = mainMenuBar.getMenus().stream().anyMatch(m -> "laboratory".equals(m.getUserData()));
        if (!hasLabMenu) {
            javafx.scene.control.Menu labMenu = new javafx.scene.control.Menu("Laboratory");
            labMenu.setUserData("laboratory");
            labMenu.setStyle("-fx-text-fill: white;");

            javafx.scene.control.MenuItem quickStartItem = new javafx.scene.control.MenuItem("Quick Start");
            quickStartItem.setOnAction(e -> showQuickStart());
            labMenu.getItems().add(quickStartItem);
            labMenu.getItems().add(new javafx.scene.control.SeparatorMenuItem());
            for (com.cryptocarver.model.payments.PaymentProfile p : com.cryptocarver.model.payments.PaymentProfileManager.getAllProfiles()) {
                // Si el perfil no tiene aún pantalla funcional, no incluirlo en Laboratory hasta que la tenga.
                // Currently only TR31, EMV, DUKPT_TDES, DUKPT_AES, PIN and SECURE_MESSAGING have UI or are going to have UI via EMV/Payments/Keys controllers.
                // We will add all but let's make sure loadProfile handles them.

                javafx.scene.control.Menu profileMenu = new javafx.scene.control.Menu(p.getType().name() + " - " + p.getName());

                javafx.scene.control.MenuItem loadItem = new javafx.scene.control.MenuItem("Load Data");
                loadItem.setOnAction(e -> {
                    // Modern UI navigation to the relevant section
                    if (p.getType() == com.cryptocarver.model.payments.PaymentProfile.ProfileType.TR31) {
                        handleItemSelected("Symmetric Keys");
                        if (keysController != null) keysController.loadProfile(p);
                    } else if (p.getType() == com.cryptocarver.model.payments.PaymentProfile.ProfileType.EMV) {
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

                    com.cryptocarver.crypto.VerificationResult result = com.cryptocarver.crypto.PaymentProfileVerifier.verify(p);

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

    @FXML private HBox readinessPanel;
    @FXML private Label readinessStatusBadge;
    @FXML private Label readinessSummaryLabel;
    @FXML private FlowPane readinessChecksContainer;
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
                    node.requestFocus();
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

    private java.util.List<com.cryptocarver.model.CommandItem> allPaletteCommands = new java.util.ArrayList<>();
    private final javafx.collections.ObservableList<com.cryptocarver.model.CommandItem> filteredPaletteCommands = javafx.collections.FXCollections.observableArrayList();

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
        if (rootStackPane != null) {
            rootStackPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    newScene.getAccelerators().put(
                            new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.K, javafx.scene.input.KeyCombination.SHORTCUT_DOWN),
                            this::handleOpenCommandPalette
                    );
                }
            });
        }

        if (commandResultsListView == null || commandSearchField == null) return;

        allPaletteCommands = com.cryptocarver.model.CommandRegistry.buildCommands(this);
        commandResultsListView.setItems(filteredPaletteCommands);

        commandResultsListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(com.cryptocarver.model.CommandItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    HBox row = new HBox(10);
                    row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    row.getStyleClass().add("command-palette-item");

                    Label categoryBadge = new Label(item.getCategory());
                    categoryBadge.getStyleClass().add("command-palette-category");

                    VBox textContainer = new VBox(2);
                    Label titleLabel = new Label(item.getTitle());
                    titleLabel.getStyleClass().add("command-palette-item-title");

                    Label descLabel = new Label(item.getDescription());
                    descLabel.getStyleClass().add("command-palette-item-desc");

                    textContainer.getChildren().addAll(titleLabel, descLabel);
                    HBox.setHgrow(textContainer, Priority.ALWAYS);

                    row.getChildren().addAll(categoryBadge, textContainer);

                    if (item.getShortcut() != null && !item.getShortcut().isEmpty()) {
                        Label shortcutLabel = new Label(item.getShortcut());
                        shortcutLabel.getStyleClass().add("command-palette-shortcut");
                        row.getChildren().add(shortcutLabel);
                    }

                    if (!item.isEnabled()) {
                        row.setOpacity(0.45);
                    } else {
                        row.setOpacity(1.0);
                    }

                    setGraphic(row);
                }
            }
        });

        commandSearchField.textProperty().addListener((obs, oldVal, newVal) -> filterCommandPalette(newVal));

        commandSearchField.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.DOWN) {
                if (!filteredPaletteCommands.isEmpty()) {
                    commandResultsListView.getSelectionModel().select(0);
                    commandResultsListView.requestFocus();
                }
                e.consume();
            } else if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                handleCloseCommandPalette();
                e.consume();
            } else if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                handleExecuteSelectedCommand();
                e.consume();
            }
        });

        commandResultsListView.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                handleCloseCommandPalette();
                e.consume();
            } else if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                handleExecuteSelectedCommand();
                e.consume();
            }
        });

        commandResultsListView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                handleExecuteSelectedCommand();
            }
        });
    }

    @FXML
    public void handleOpenCommandPalette() {
        if (commandPaletteOverlay == null) return;

        allPaletteCommands = com.cryptocarver.model.CommandRegistry.buildCommands(this);
        commandPaletteOverlay.setManaged(true);
        commandPaletteOverlay.setVisible(true);

        if (commandSearchField != null) {
            commandSearchField.setText("");
            filterCommandPalette("");
            commandSearchField.requestFocus();
        }
    }

    @FXML
    public void handleCloseCommandPalette() {
        if (commandPaletteOverlay == null) return;
        commandPaletteOverlay.setManaged(false);
        commandPaletteOverlay.setVisible(false);
        if (commandSearchField != null) {
            commandSearchField.setText("");
        }
    }

    @FXML
    public void handleExecuteSelectedCommand() {
        if (commandResultsListView == null) return;
        com.cryptocarver.model.CommandItem selected = commandResultsListView.getSelectionModel().getSelectedItem();
        if (selected != null && selected.isEnabled()) {
            handleCloseCommandPalette();
            selected.execute();
        }
    }

    private void filterCommandPalette(String query) {
        java.util.List<com.cryptocarver.model.CommandItem> matched = com.cryptocarver.model.CommandSearchEngine.search(allPaletteCommands, query);
        filteredPaletteCommands.setAll(matched);

        if (commandEmptyLabel != null) {
            boolean empty = matched.isEmpty();
            commandEmptyLabel.setManaged(empty);
            commandEmptyLabel.setVisible(empty);
        }

        if (commandResultsListView != null && !matched.isEmpty()) {
            commandResultsListView.getSelectionModel().select(0);
        }
    }
}
