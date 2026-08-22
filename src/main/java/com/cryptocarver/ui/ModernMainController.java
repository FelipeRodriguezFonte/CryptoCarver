package com.cryptocarver.ui;

import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import javafx.fxml.FXML;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.io.File;
import java.util.Optional; // For Dialogs
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javafx.scene.text.TextFlow;
import com.cryptocarver.model.AppDiagnostics;
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
    @FXML private javafx.scene.layout.VBox jose;
    @FXML private javafx.scene.layout.VBox cose;
    @FXML private GenericController genericContainerController;

    private static final Logger LOG = LoggerFactory.getLogger(ModernMainController.class);
    private final PauseTransition statusResetTimer = new PauseTransition(Duration.seconds(3));
    private final ExpandedTextViewer expandedTextViewer = new ExpandedTextViewer();
    private final ExpandedTableViewer expandedTableViewer = new ExpandedTableViewer();
    private OperationInspectorPresenter inspectorPresenter;
    private final ResultAreaTracker resultAreaTracker = new ResultAreaTracker();
    private TableView<?> lastFocusedTable;
    /**
     * Snapshot published by the latest completed operation.  It is deliberately
     * separate from the last focused text area: focus is a navigation concern,
     * while publishing is the authoritative completion event.  Without this a
     * still-visible result area from a previous accordion pane could be shown
     * by Expand Result after a different operation completed.
     */
    private String lastPublishedOperation = "";
    private com.cryptocarver.model.OperationResult lastPublishedResultSnapshot;

    @FXML
    private BorderPane mainPane;
    @FXML
    private ToggleGroup visibilityProfileGroup;

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
    @FXML private VBox keysContainer;
    @FXML private KeysController keysContainerController;
    @FXML private VBox certificatesContainer;
    @FXML private CertificatesController certificatesContainerController;
    @FXML
    private VBox inspectorPanel;
    @FXML
    private VBox inspectorDetailsContainer;
    private boolean inspectorHiddenForCompactLayout;

    // CIPHER UI
    @FXML private VBox cipherContainer;
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
    @FXML
    private Label runtimeInfoLabel;
    @FXML private Label statusLabel;
    @FXML private HBox errorBanner;
    @FXML private Label errorBannerTitle;
    @FXML private Label errorBannerRemedy;
    @FXML private Button errorBannerGoToFieldBtn;
    @FXML private Button errorBannerCopyDetailsBtn;
    @FXML private Button errorBannerCloseBtn;
    private InlineErrorPresenter inlineErrorPresenter;
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

    private GuidedOperation currentGuidedOp;
    private int currentGuidedStep = 1;




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

    private final java.util.Map<String, String> rememberedInputFormats = new java.util.HashMap<>();
    private final java.util.Map<String, String> rememberedOutputFormats = new java.util.HashMap<>();
    private String currentFormatProfileOperation = "Dashboard";

    // Managers
    private com.cryptocarver.model.HistoryManager historyManager;
    private com.cryptocarver.model.SavedSessionsManager savedSessionsManager;
    private String currentActiveOperation = "Dashboard"; // Defaul
    @FXML
    private ComboBox<String> outputFormatCombo;

    // Symmetric and asymmetric key controls are owned by keysContainerController.
    // Certificate, CRL and CMS controls are owned by certificatesContainerController.
    // Generic Tab FXML Fields
    @FXML
    private Accordion genericContainer;

    // Post-Quantum UI
    @FXML private VBox postQuantumContainer;
    @FXML private PostQuantumController postQuantumContainerController;

    // XML Security UI
    @FXML private VBox xmlSecurityContainer;
    @FXML private VBox wssSecurityContainer;
    @FXML private WssSecurityController wssSecurityContainerController;
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
    @FXML private VBox authenticationContainer;
    @FXML private AuthenticationController authenticationContainerController;

    // Payments module
    @FXML private VBox paymentsContainer;
    @FXML private PaymentsController paymentsContainerController;

    // EMV module
    @FXML private VBox emvContainer;
    @FXML private EMVController emvContainerController;

    // Controllers
    private KeysController keysController;
    private PaymentsController paymentsController;
    private EMVController emvController;
    private CipherController cipherController;
    @FXML private JOSEController joseController;
    @FXML private COSEController coseController;

    @FXML
    private MenuBar mainMenuBar;
    @FXML private Menu fileMenu;
    @FXML private Menu editMenu;
    @FXML private Menu viewMenu;
    @FXML private Menu securityMenu;
    @FXML private Menu toolsMenu;
    @FXML private Menu helpMenu;
    @FXML private Menu languageMenu;
    @FXML private RadioMenuItem languageSystemMenuItem;
    @FXML private RadioMenuItem languageEsMenuItem;
    @FXML private RadioMenuItem languageEnMenuItem;
    @FXML private ToggleGroup languagePreferenceGroup;
    @FXML private MenuItem importKeyMenuItem;
    @FXML private MenuItem exportScreenMenuItem;
    @FXML private MenuItem importScreenMenuItem;
    @FXML private MenuItem saveSessionMenuItem;
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
    @FXML private Button toolbarSearchButton;
    @FXML private Button toolbarSaveSessionButton;
    @FXML private Button toolbarClearButton;
    @FXML private Button toolbarExpandButton;
    @FXML private Button toolbarShelfButton;
    @FXML private Button toolbarCopyButton;
    @FXML private Label outputFormatLabel;
    private final I18nService i18n = I18nService.getInstance();
    private java.util.function.Consumer<java.util.Locale> i18nListener;

    // Async Progress UI
    @FXML private HBox asyncProgressBox;
    @FXML private ProgressIndicator asyncProgressSpinner;
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
            if (asyncProgressSpinner != null) {
                asyncProgressSpinner.setVisible(false);
                asyncProgressSpinner.setManaged(false);
            }
        } else {
            if (asyncProgressSpinner != null) {
                asyncProgressSpinner.setProgress(-1);
                asyncProgressSpinner.setAccessibleText("Working: " + details.getOperationName());
                asyncProgressSpinner.setVisible(true);
                asyncProgressSpinner.setManaged(true);
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
        if (joseController != null) joseController.setReporter(this);
        if (coseController != null) coseController.setReporter(this);
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

        if (runtimeInfoLabel != null) {
            String javaVer = System.getProperty("java.version");
            String javafxVer = System.getProperty("javafx.version");
            String javaText = (javaVer != null && !javaVer.isBlank()) ? "Java " + javaVer : "Java";
            String javafxText = (javafxVer != null && !javafxVer.isBlank()) ? "JavaFX " + javafxVer : "JavaFX";
            runtimeInfoLabel.setText(javaText + " | " + javafxText + " | BouncyCastle");
        }

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

        // Load symmetric keys content (default)
        loadSymmetricKeysContent();
        loadCipherContent();
        loadAuthenticationContent();
        loadPaymentsContent();
        loadEMVContent();
        if (genericContainerController != null) {
            genericContainerController.setStatusReporter(this);
            genericContainerController.setFormatControls(inputFormatCombo, outputFormatCombo);
        }
        if (genericContainerController != null && genericContainerController.getKeyCertificateWorkbenchController() != null) {
            genericContainerController.getKeyCertificateWorkbenchController().setStatusReporter(this);
        }
        if (genericContainerController != null && genericContainerController.getCryptoEnvelopeInspectorController() != null) {
            genericContainerController.getCryptoEnvelopeInspectorController().setStatusReporter(this);
        }
        loadPostQuantumContent();
        loadXMLSecurityContent();
        loadWssSecurityContent();

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
        Node focusOwner = mainPane != null && mainPane.getScene() != null
                ? mainPane.getScene().getFocusOwner() : null;
        setText(fileMenu, "menu.file");
        setText(editMenu, "menu.edit");
        setText(viewMenu, "menu.view");
        setText(securityMenu, "menu.security");
        setText(toolsMenu, "menu.tools");
        setText(helpMenu, "menu.help");
        setText(languageMenu, "menu.language");

        setText(importKeyMenuItem, "menu.importKey");
        setText(exportScreenMenuItem, "menu.exportScreen");
        setText(importScreenMenuItem, "menu.importScreen");
        setText(saveSessionMenuItem, "menu.saveSession");
        setText(exportHistoryMenuItem, "menu.exportHistory");
        setText(exitMenuItem, "menu.exit");
        setText(clearInputMenuItem, "menu.clearInput");
        setText(clearOutputMenuItem, "menu.clearOutput");
        setText(copyOutputMenuItem, "menu.copyOutput");
        setText(addToShelfMenuItem, "menu.addToShelf");
        setText(quickStartMenuItem, "menu.quickStart");
        setText(clipboardShelfMenuItem, "menu.clipboardShelf");
        setText(commandPaletteMenuItem, "menu.commandPalette");
        setText(toggleSidePanelMenuItem, "menu.toggleSidePanel");
        setText(toggleInspectorMenuItem, "menu.toggleInspector");
        setText(expandResultMenuItem, "menu.expandResult");
        setText(expandTableMenuItem, "menu.expandTable");
        setText(zoomInMenuItem, "menu.zoomIn");
        setText(zoomOutMenuItem, "menu.zoomOut");
        setText(resetViewMenuItem, "menu.resetView");
        setText(visibilityFullLabMenuItem, "menu.visibility.full");
        setText(visibilityMaskedMenuItem, "menu.visibility.masked");
        setText(visibilityRedactedMenuItem, "menu.visibility.redacted");
        setText(epochMenuItem, "menu.epoch");
        setText(jsonMenuItem, "menu.json");
        setText(byteInspectorMenuItem, "menu.byteInspector");
        setText(clearKeyCacheMenuItem, "menu.clearKeyCache");
        setText(shortcutsMenuItem, "menu.shortcuts");
        setText(diagnosticsMenuItem, "menu.diagnostics");
        setText(aboutMenuItem, "menu.about");
        if (mainMenuBar != null) {
            mainMenuBar.getMenus().stream()
                    .filter(menu -> "laboratory".equals(menu.getUserData()))
                    .findFirst()
                    .ifPresent(this::localizeLaboratoryMenu);
        }

        setText(languageSystemMenuItem, "app.language.system");
        setText(languageEsMenuItem, "app.language.es");
        setText(languageEnMenuItem, "app.language.en");
        LanguagePreference selected = i18n.getPreference();
        if (languageSystemMenuItem != null) languageSystemMenuItem.setSelected(selected == LanguagePreference.SYSTEM);
        if (languageEsMenuItem != null) languageEsMenuItem.setSelected(selected == LanguagePreference.ES);
        if (languageEnMenuItem != null) languageEnMenuItem.setSelected(selected == LanguagePreference.EN);

        if (toolbarSearchButton != null) {
            toolbarSearchButton.setText(i18n.text("toolbar.search", COMMAND_PALETTE_SHORTCUT));
        }
        setText(toolbarSaveSessionButton, "menu.saveSession");
        setText(toolbarClearButton, "toolbar.clear");
        setText(toolbarExpandButton, "toolbar.expand");
        setText(toolbarShelfButton, "toolbar.addShelf");
        setText(toolbarCopyButton, "toolbar.copy");
        setAccessibleText(toolbarSearchButton);
        setAccessibleText(toolbarSaveSessionButton);
        setAccessibleText(toolbarClearButton);
        setAccessibleText(toolbarExpandButton);
        setAccessibleText(toolbarShelfButton);
        setAccessibleText(toolbarCopyButton);
        setText(inputFormatLabel, "toolbar.payloadFormat");
        setText(outputFormatLabel, "toolbar.output");
        setAccessibleText(resultExpandButton, "a11y.resultExpand");
        setAccessibleText(resultShelfButton, "a11y.resultShelf");
        setAccessibleText(resultCopyButton, "a11y.resultCopy");
        setAccessibleText(inspectorToggleButton, "a11y.inspectorToggle");
        setAccessibleText(errorBannerCloseBtn, "a11y.errorClose");
        if (inputFormatCombo != null) {
            inputFormatCombo.setAccessibleText(i18n.text("a11y.payloadFormat"));
            inputFormatCombo.setAccessibleHelp(i18n.text("toolbar.payloadTooltip"));
        }
        if (outputFormatCombo != null) {
            outputFormatCombo.setAccessibleText(i18n.text("a11y.outputFormat"));
            outputFormatCombo.setAccessibleHelp(i18n.text("a11y.outputFormat"));
        }
        if (commandSearchField != null) {
            commandSearchField.setAccessibleText(i18n.text("a11y.commandSearch"));
            commandSearchField.setAccessibleHelp(i18n.text("command.prompt"));
        }
        if (favoriteToggleBtn != null) {
            favoriteToggleBtn.setAccessibleText(i18n.text("a11y.favorite"));
            favoriteToggleBtn.setAccessibleHelp(i18n.text("favorite.tooltip", FAVORITE_SHORTCUT));
        }
        if (inputFormatLabel != null) inputFormatLabel.setTooltip(new Tooltip(i18n.text("toolbar.payloadTooltip")));
        if (inputFormatCombo != null) inputFormatCombo.setTooltip(new Tooltip(i18n.text("toolbar.payloadTooltip")));

        setText(resultLastLabel, "result.last");
        setText(resultAlgorithmStaticLabel, "result.algorithm");
        if (resultStatusBadge != null) resultStatusBadge.setAccessibleText(i18n.text("result.status"));
        setText(errorBannerTitle, "error.failed");
        setText(errorBannerRemedy, "error.remedy");
        setText(errorBannerGoToFieldBtn, "error.goToField");
        setText(errorBannerCopyDetailsBtn, "error.copyDetails");
        if (errorBannerTitle != null) {
            errorBannerTitle.setAccessibleHelp(i18n.text("a11y.errorTitle"));
        }
        if (errorBannerRemedy != null) {
            errorBannerRemedy.setAccessibleHelp(i18n.text("a11y.errorRemedy"));
        }
        if (errorBannerGoToFieldBtn != null) {
            errorBannerGoToFieldBtn.setAccessibleText(i18n.text("a11y.errorGoToField"));
            errorBannerGoToFieldBtn.setAccessibleHelp(i18n.text("a11y.errorGoToFieldHelp"));
        }
        if (errorBannerCopyDetailsBtn != null) {
            errorBannerCopyDetailsBtn.setAccessibleText(i18n.text("a11y.errorCopyDetails"));
            errorBannerCopyDetailsBtn.setAccessibleHelp(i18n.text("a11y.errorCopyDetailsHelp"));
        }
        if (errorBannerCloseBtn != null) {
            errorBannerCloseBtn.setAccessibleText(i18n.text("a11y.errorClose"));
            errorBannerCloseBtn.setAccessibleHelp(i18n.text("a11y.errorCloseHelp"));
        }
        setText(guideBackBtn, "guide.back");
        setText(guideNextBtn, "guide.next");
        setText(guideSkipBtn, "guide.skip");
        setText(guideExitBtn, "guide.exit");
        setText(asyncProgressLabel, "progress.working");
        setText(asyncCancelBtn, "progress.cancel");
        setAccessibleText(asyncCancelBtn);
        setText(inspectorTitleLabel, "inspector.title");
        setText(inspectorInputBytesTitle, "inspector.inputBytes");
        setText(inspectorOutputBytesTitle, "inspector.outputBytes");
        setText(inspectorAlgorithmTitle, "inspector.algorithm");
        setText(inspectorSecurityTipsTitle, "inspector.securityTips");
        setText(inspectorWarningTitle, "inspector.warning");
        setText(inspectorHistoryTitle, "inspector.history");
        setText(inspectorExportJsonButton, "inspector.exportJson");
        setText(inspectorClearHistoryButton, "toolbar.clear");
        if (inspectorExportJsonButton != null) {
            inspectorExportJsonButton.setTooltip(new Tooltip(i18n.text("inspector.exportJsonTooltip")));
        }
        if (commandSearchField != null) commandSearchField.setPromptText(i18n.text("command.prompt"));
        setText(commandEscapeLabel, "command.escape");
        setText(commandEmptyLabel, "command.empty");
        setText(commandNavigateLabel, "command.navigate");
        setText(commandSelectLabel, "command.select");
        setText(commandCancelLabel, "command.cancel");
        setText(commandTitleLabel, "command.title");

        if (navigationRail != null) navigationRail.refreshLocalizedText();
        if (sidePanel != null) sidePanel.refreshLocalizedText();
        updateBreadcrumbs(currentActiveOperation);
        updateFavoriteToggleState(currentActiveOperation);
        if (statusLabel != null && (statusLabel.getText() == null || statusLabel.getText().isBlank()
                || statusLabel.getText().equals("Ready") || statusLabel.getText().equals("Listo"))) {
            statusLabel.setText(i18n.text("status.ready"));
        }
        if (inlineErrorPresenter != null && inlineErrorPresenter.getCurrentError() != null) {
            inlineErrorPresenter.showError(localizedError(inlineErrorPresenter.getCurrentError()),
                    rootStackPane != null ? rootStackPane : mainPane);
        }
        restoreFocusAfterLocalization(focusOwner);
    }

    private void setText(javafx.scene.control.MenuItem item, String key) {
        if (item != null) item.setText(i18n.text(key));
    }

    private void setText(javafx.scene.control.Menu menu, String key) {
        if (menu != null) menu.setText(i18n.text(key));
    }

    private void setText(javafx.scene.control.Button button, String key) {
        if (button != null) button.setText(i18n.text(key));
    }

    private void setText(javafx.scene.control.Label label, String key) {
        if (label != null) label.setText(i18n.text(key));
    }

    private void localizeLaboratoryMenu(javafx.scene.control.Menu menu) {
        menu.setText(i18n.text("menu.laboratory"));
        if (!menu.getItems().isEmpty()) setText(menu.getItems().get(0), "menu.quickStart");
        for (javafx.scene.control.MenuItem item : menu.getItems()) {
            if (item instanceof javafx.scene.control.Menu profile) {
                for (javafx.scene.control.MenuItem profileItem : profile.getItems()) {
                    if ("Load Data".equals(profileItem.getText()) || "Cargar datos".equals(profileItem.getText())) {
                        setText(profileItem, "menu.loadData");
                    } else if ("Run and Verify".equals(profileItem.getText()) || "Ejecutar y verificar".equals(profileItem.getText())) {
                        setText(profileItem, "menu.runVerify");
                    }
                }
            }
        }
    }

    private void setAccessibleText(javafx.scene.control.ButtonBase control) {
        if (control != null && control.getText() != null) {
            control.setAccessibleText(control.getText());
        }
    }

    private void setAccessibleText(javafx.scene.control.ButtonBase control, String key) {
        if (control != null && key != null) {
            control.setAccessibleText(i18n.text(key));
        }
    }

    private void restoreFocusAfterLocalization(Node focusOwner) {
        if (focusOwner == null) return;
        Platform.runLater(() -> {
            if (focusOwner.getScene() != null && focusOwner.isVisible()
                    && !focusOwner.isDisabled() && focusOwner.isFocusTraversable()) {
                focusOwner.requestFocus();
            }
        });
    }

    private String localizedSectionText(String value) {
        if (value == null) return "";
        return switch (value) {
            case "Cryptographic Operations" -> i18n.text("bread.cryptoOperations");
            case "Symmetric Keys" -> i18n.text("bread.symmetricKeys");
            case "Asymmetric Keys" -> i18n.text("bread.asymmetricKeys");
            case "Ciphers" -> i18n.text("bread.ciphers");
            case "Signatures & MAC" -> i18n.text("bread.signaturesMac");
            case "Certificates", "Certificates & CMS" -> i18n.text("bread.certificatesCms");
            case "JOSE / JWT" -> i18n.text("bread.joseJwt");
            case "Post-Quantum", "Post-Quantum PQC" -> i18n.text("bread.postQuantumPqc");
            case "XML Security" -> i18n.text("bread.xmlSecurity");
            case "WSS Security" -> i18n.text("bread.wssSecurity");
            case "EMV & Smartcards" -> i18n.text("bread.emvSmartcards");
            case "Payment Cryptography" -> i18n.text("bread.paymentCryptography");
            case "Utilities" -> i18n.text("bread.utilities");
            case "History" -> i18n.text("bread.history");
            case "Clipboard Shelf" -> i18n.text("bread.clipboardShelf");
            case "Saved Sessions" -> i18n.text("bread.savedSessions");
            default -> value;
        };
    }

    private String localizedModuleText(String value) {
        if (value == null) return "";
        return switch (value) {
            case "Symmetric" -> i18n.text("bread.symmetric");
            case "Asymmetric" -> i18n.text("bread.asymmetric");
            case "Tools" -> i18n.text("bread.tools");
            case "General" -> i18n.text("bread.module");
            default -> value;
        };
    }

    @FXML private void handleLanguageSystem() { i18n.setPreference(LanguagePreference.SYSTEM); }
    @FXML private void handleLanguageEs() { i18n.setPreference(LanguagePreference.ES); }
    @FXML private void handleLanguageEn() { i18n.setPreference(LanguagePreference.EN); }

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
        if (cipherContainerController != null) {
            cipherController = cipherContainerController;
            cipherController.initModern(this, inputFormatCombo, outputFormatCombo,
                    () -> keysController == null ? null : keysController.getLastGeneratedKeyPair());
        }
    }

    private void loadAuthenticationContent() {
        if (authenticationContainerController != null) {
            authenticationContainerController.init(this, inputFormatCombo, outputFormatCombo);
        }
    }

    private void loadEMVContent() {
        if (emvContainerController != null) {
            emvController = emvContainerController;
            emvController.init(this);
        }
    }

    private void loadPaymentsContent() {
        if (paymentsContainerController != null) {
            paymentsController = paymentsContainerController;
            paymentsController.init(this);
        }
    }

    private void loadSymmetricKeysContent() {
        try {
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
    public void navigateTo(String operation) {
        handleItemSelected(operation);
    }

    @Override
    public void setInputFormat(String format) {
        if (inputFormatCombo != null) {
            setToolbarFormat(inputFormatCombo, format);
        }
    }

    @Override
    public void setOutputFormat(String format) {
        if (outputFormatCombo != null) {
            setToolbarFormat(outputFormatCombo, format);
        }
    }

    private static void setToolbarFormat(ComboBox<String> combo, String format) {
        String canonical = normalizeToolbarFormat(format);
        if (canonical == null) {
            combo.setValue(null);
        } else if (combo.getItems().contains(canonical)) {
            combo.setValue(canonical);
        } else if (!combo.isDisabled()) {
            // Do not leave a previous format selected when a template value is
            // not representable by the active operation contract.
            combo.setValue(null);
        }
    }

    /**
     * Keeps legacy module labels compatible with the canonical values exposed by
     * the shared format toolbar. A ComboBox silently retains its previous value
     * when assigned an item that it does not contain, which previously made a
     * text template run with a stale hexadecimal input format.
     */
    static String normalizeToolbarFormat(String format) {
        if ("Plain Text".equalsIgnoreCase(format) || "Text".equalsIgnoreCase(format)) {
            return "Text (UTF-8)";
        }
        return format;
    }

    public void navigateToModule(String moduleName) {
        handleItemSelected(moduleName);
    }

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
        if (com.cryptocarver.model.AppSettings.getInstance().getSecretVisibilityProfile()
                != com.cryptocarver.model.SecretVisibilityProfile.FULL_LAB) {
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

    public void handleItemSelected(String itemName) {
        String requestedItem = itemName;
        itemName = com.cryptocarver.model.OperationRegistry.getInstance()
                .resolveNavigation(itemName)
                .map(com.cryptocarver.model.OperationDescriptor::getNavigationPath)
                .orElse(itemName);

        // Save current formats before switching
        if (this.currentFormatProfileOperation != null) {
            rememberedInputFormats.put(this.currentFormatProfileOperation, inputFormatCombo.getValue());
            rememberedOutputFormats.put(this.currentFormatProfileOperation, outputFormatCombo.getValue());
        }

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

        if (!activateNavigationRoute(itemName)) {
            showPlaceholderContent(itemName);
        }

        updateStatus("Loaded: " + itemName);
        // An untouched form is naturally incomplete. Do not make that the
        // first thing users see; reveal the checklist after an edit, a real
        // warning, or an attempted execution.
        readinessPanelActivated = false;
        readinessShowDetails = false;
        refreshReadinessPanelForOperation(currentActiveOperation, currentPreflightEncrypt);
    }

    private boolean activateNavigationRoute(String operation) {
        java.util.Optional<UiNavigationRegistry.Route> resolved = UiNavigationRegistry.resolve(operation);
        if (resolved.isEmpty()) return false;

        UiNavigationRegistry.Route route = resolved.get();
        switch (route.module()) {
            case JOSE -> showJOSE();
            case COSE -> showCOSE();
            case EPOCH_CONVERTER -> handleEpochConverter();
            case JSON_FORMATTER -> handleJsonFormatter();
            case KEYS_SYMMETRIC -> {
                showSymmetricKeys();
                expandAccordionPane(route.section());
            }
            case KEYS_ASYMMETRIC -> {
                showAsymmetricKeys();
                expandAsymmetricAccordionPane(route.section());
            }
            case CERTIFICATES -> {
                showCertificates();
                expandCertificatesAccordionPane(route.section());
                if (certificatesContainerController != null) {
                    if (route.variant() == UiNavigationRegistry.Variant.ASN1_DECODE) {
                        certificatesContainerController.selectAsn1DecodeTab();
                    } else if (route.variant() == UiNavigationRegistry.Variant.ASN1_ENCODE) {
                        certificatesContainerController.selectAsn1EncodeTab();
                    }
                }
            }
            case GENERIC -> {
                showGeneric();
                expandGenericAccordionPane(route.section());
            }
            case POST_QUANTUM -> {
                showPostQuantum();
                expandPQCAccordionPane(route.section());
            }
            case XML_SECURITY -> {
                showXMLSecurity();
                expandXMLAccordionPane(route.section());
            }
            case WSS_SECURITY -> {
                showWssSecurity();
                expandWssAccordionPane(route.section());
            }
            case EMV -> {
                showEMV();
                expandEMVAccordionPane(route.section());
            }
            case HISTORY -> {
                showHistoryView();
                if (route.variant() == UiNavigationRegistry.Variant.HISTORY_EXPORT) {
                    if (historyViewController != null) historyViewController.focusExportActions();
                    updateStatus("Choose Export Visible JSON or Export JSON Record in Recent Operations.");
                }
            }
            case CLIPBOARD_SHELF -> showClipboardShelf();
            case SAVED_SESSIONS -> showSavedSessions();
            case CIPHER -> {
                showCipher();
                expandCipherAccordionPane(route.section());
            }
            case AUTHENTICATION -> {
                showAuthentication();
                expandAuthenticationAccordionPane(route.section());
            }
            case PAYMENTS -> {
                showPayments();
                expandPaymentsAccordionPane(route.section());
            }
        }
        return true;
    }

    private void updateContentHeader(String itemName) {
        // Determine section and subsection
        String section = "Cryptographic Operations";
        String subsection = itemName;
        java.util.Optional<com.cryptocarver.model.OperationDescriptor> descriptor =
                com.cryptocarver.model.OperationRegistry.getInstance().resolveNavigation(itemName);
        if (descriptor.isPresent()) {
            com.cryptocarver.model.OperationDescriptor operation = descriptor.get();
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

        if (contentTitleLabel != null) contentTitleLabel.setText(localizedSectionText(section));
        if (contentSubtitleLabel != null) contentSubtitleLabel.setText(subsection);

        // UX-07: Update Breadcrumbs, Favorites & Last Route
        updateBreadcrumbs(itemName);
        updateFavoriteToggleState(itemName);
        com.cryptocarver.model.AppSettings.getInstance().setLastRoute(itemName);

        // Update format profile in the toolbar
        if (contractOperationLabel != null) {
            contractOperationLabel.setText(subsection);
        }
        applyOperationFormatProfile(itemName);
    }

    private void restoreStartupLastRoute() {
        try {
            String lastRoute = com.cryptocarver.model.AppSettings.getInstance().getLastRoute();
            if (lastRoute != null && !lastRoute.isBlank()) {
                if (UiNavigationRegistry.resolve(lastRoute).isPresent()) {
                    navigateToModule(lastRoute);
                }
            }
        } catch (Exception ignored) {
            // Preferences must never fail application startup
        }
    }

    private void updateBreadcrumbs(String operationName) {
        if (breadcrumbContainer == null || operationName == null) return;

        String sectionLabel = i18n.text("bread.section");
        String moduleLabel = i18n.text("bread.module");
        String operationLabel = operationName;
        String canonicalModulePath = operationName;

        java.util.Optional<UiNavigationRegistry.Route> resolved = UiNavigationRegistry.resolve(operationName);
        if (resolved.isPresent()) {
            UiNavigationRegistry.Route route = resolved.get();
            sectionLabel = switch (route.module()) {
                case KEYS_SYMMETRIC -> i18n.text("bread.symmetricKeys");
                case KEYS_ASYMMETRIC -> i18n.text("bread.asymmetricKeys");
                case CIPHER -> i18n.text("bread.ciphers");
                case AUTHENTICATION -> i18n.text("bread.signaturesMac");
                case CERTIFICATES -> i18n.text("bread.certificatesCms");
                case JOSE -> i18n.text("bread.joseJwt");
                case COSE -> i18n.text("bread.coseSign1");
                case POST_QUANTUM -> i18n.text("bread.postQuantumPqc");
                case XML_SECURITY -> i18n.text("bread.xmlSecurity");
                case WSS_SECURITY -> i18n.text("bread.wssSecurity");
                case EMV -> i18n.text("bread.emvSmartcards");
                case PAYMENTS -> i18n.text("bread.paymentCryptography");
                case GENERIC -> i18n.text("bread.utilities");
                case HISTORY -> i18n.text("bread.history");
                case CLIPBOARD_SHELF -> i18n.text("bread.clipboardShelf");
                case SAVED_SESSIONS -> i18n.text("bread.savedSessions");
                default -> i18n.text("bread.section");
            };

            if (route.section() != null && !route.section().isBlank()) {
                moduleLabel = route.section();
                canonicalModulePath = route.section();
            } else {
                moduleLabel = route.module().name();
                canonicalModulePath = operationName;
            }
        } else {
            java.util.Optional<com.cryptocarver.model.OperationDescriptor> descriptor =
                    com.cryptocarver.model.OperationRegistry.getInstance().resolveNavigation(operationName);
            if (descriptor.isPresent()) {
                com.cryptocarver.model.OperationDescriptor op = descriptor.get();
                sectionLabel = op.getCategory() != null ? localizedSectionText(op.getCategory()) : i18n.text("bread.section");
                moduleLabel = sectionLabel;
                operationLabel = op.getTitle();
                canonicalModulePath = op.getNavigationPath() != null ? op.getNavigationPath() : op.getTitle();
            }
        }

        if (breadcrumbSectionBtn != null) {
            breadcrumbSectionBtn.setText(sectionLabel);
            breadcrumbSectionBtn.setUserData(resolved.isPresent() ? resolved.get().module() : sectionLabel);
            breadcrumbSectionBtn.setAccessibleText(i18n.text("bread.navigateSection", sectionLabel));
            breadcrumbSectionBtn.setTooltip(new Tooltip(i18n.text("bread.navigateSection", sectionLabel)));
        }

        if (breadcrumbModuleBtn != null) {
            breadcrumbModuleBtn.setText(localizedModuleText(moduleLabel));
            breadcrumbModuleBtn.setUserData(canonicalModulePath);
            breadcrumbModuleBtn.setAccessibleText(i18n.text("bread.navigateModule", localizedModuleText(moduleLabel)));
            breadcrumbModuleBtn.setTooltip(new Tooltip(i18n.text("bread.navigateModule", localizedModuleText(moduleLabel))));
        }

        if (breadcrumbOperationLabel != null) {
            breadcrumbOperationLabel.setText(operationLabel);
        }
    }

    @FXML
    public void handleBreadcrumbSectionClick() {
        if (breadcrumbSectionBtn == null) return;
        if (breadcrumbSectionBtn.getUserData() instanceof UiNavigationRegistry.Module module) {
            switch (module) {
                case KEYS_SYMMETRIC, KEYS_ASYMMETRIC -> navigationRail.selectSection(NavigationRail.Section.KEYS);
                case CIPHER -> navigationRail.selectSection(NavigationRail.Section.CIPHER);
                case AUTHENTICATION -> navigationRail.selectSection(NavigationRail.Section.AUTHENTICATION);
                case CERTIFICATES -> navigationRail.selectSection(NavigationRail.Section.CERTIFICATES);
                case JOSE -> navigationRail.selectSection(NavigationRail.Section.JOSE);
                case COSE -> navigationRail.selectSection(NavigationRail.Section.COSE);
                case POST_QUANTUM -> navigationRail.selectSection(NavigationRail.Section.POST_QUANTUM);
                case XML_SECURITY, WSS_SECURITY -> navigationRail.selectSection(NavigationRail.Section.XML_SECURITY);
                case EMV, PAYMENTS -> navigationRail.selectSection(NavigationRail.Section.PAYMENTS);
                case HISTORY -> navigationRail.selectSection(NavigationRail.Section.HISTORY);
                default -> showQuickStart();
            }
            return;
        }
        String sectionText = breadcrumbSectionBtn.getText();
        if (navigationRail == null) {
            showQuickStart();
            return;
        }
        switch (sectionText) {
            case "Symmetric Keys", "Asymmetric Keys" -> navigationRail.selectSection(NavigationRail.Section.KEYS);
            case "Ciphers" -> navigationRail.selectSection(NavigationRail.Section.CIPHER);
            case "Signatures & MAC" -> navigationRail.selectSection(NavigationRail.Section.AUTHENTICATION);
            case "Certificates & CMS" -> navigationRail.selectSection(NavigationRail.Section.CERTIFICATES);
            case "JOSE / JWT" -> navigationRail.selectSection(NavigationRail.Section.JOSE);
            case "Post-Quantum PQC" -> navigationRail.selectSection(NavigationRail.Section.POST_QUANTUM);
            case "XML Security", "WSS Security" -> navigationRail.selectSection(NavigationRail.Section.XML_SECURITY);
            case "EMV & Smartcards", "Payment Cryptography" -> navigationRail.selectSection(NavigationRail.Section.PAYMENTS);
            case "History" -> navigationRail.selectSection(NavigationRail.Section.HISTORY);
            default -> showQuickStart();
        }
    }

    @FXML
    public void handleBreadcrumbModuleClick() {
        if (breadcrumbModuleBtn == null) return;
        Object data = breadcrumbModuleBtn.getUserData();
        String targetRoute = data instanceof String s && !s.isBlank() ? s : breadcrumbModuleBtn.getText();
        navigateToModule(targetRoute);
    }

    public void reopenRecentHistoryCommand(com.cryptocarver.model.HistoryCommand item) {
        reopenHistoryOperation(item);
    }

    /**
     * Opens a recorded execution for inspection. Selecting an entry under
     * "Recent Executions" is a view action; restoring its recipe remains the
     * explicit job of the Reopen button in the History view.
     */
    public void showRecentHistoryCommand(com.cryptocarver.model.HistoryCommand item) {
        if (item == null) return;
        navigateToModule("Recent Operations");
        if (historyViewController != null) {
            historyViewController.selectHistoryCommand(item);
        }
        updateInspector(item.getOperation(), null, null, visibleHistoryDetails(item));
        updateStatus("Viewing historical execution: " + item.getOperation());
    }

    @Override
    public void reopenHistoryOperation(com.cryptocarver.model.HistoryCommand item) {
        if (item == null || item.getOperation() == null) return;
        String navigationOperation = item.getNavigationOperation();
        navigateToModule(navigationOperation);
        restoreOperationState(item.getParameters(), navigationOperation);
        updateInspector(item.getOperation(), null, null, visibleHistoryDetails(item));
        updateStatus("Reopened historical execution: " + item.getOperation());
    }

    private java.util.List<com.cryptocarver.model.OperationDetail> visibleHistoryDetails(
            com.cryptocarver.model.HistoryCommand item) {
        java.util.List<com.cryptocarver.model.OperationDetail> details = item.getStructuredDetails();
        if (details == null || details.isEmpty()) {
            if (item.getDetails() == null || item.getDetails().isBlank()) return java.util.List.of();
            details = java.util.List.of(com.cryptocarver.model.OperationDetail.sensitiveDetail(
                    "Legacy details", item.getDetails()));
        }
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

    @FXML
    public void handleToggleFavorite() {
        if (currentActiveOperation == null || currentActiveOperation.isBlank()) return;
        com.cryptocarver.model.AppSettings.getInstance().toggleFavorite(currentActiveOperation);
        updateFavoriteToggleState(currentActiveOperation);
        if (sidePanel != null && sidePanel.isVisible()) {
            sidePanel.updateContent(sidePanel.getCurrentSection());
        }
    }

    private void updateFavoriteToggleState(String operationName) {
        if (favoriteToggleBtn == null || operationName == null) return;
        boolean isFav = com.cryptocarver.model.AppSettings.getInstance().isFavorite(operationName);
        if (isFav) {
            favoriteToggleBtn.setText("★");
            if (!favoriteToggleBtn.getStyleClass().contains("active")) {
                favoriteToggleBtn.getStyleClass().add("active");
            }
            favoriteToggleBtn.setAccessibleText(i18n.text("favorite.remove", operationName));
            favoriteToggleBtn.setTooltip(new Tooltip(i18n.text("favorite.active", FAVORITE_SHORTCUT)));
        } else {
            favoriteToggleBtn.setText("☆");
            favoriteToggleBtn.getStyleClass().remove("active");
            favoriteToggleBtn.setAccessibleText(i18n.text("favorite.add", operationName));
            favoriteToggleBtn.setTooltip(new Tooltip(i18n.text("favorite.tooltip", FAVORITE_SHORTCUT)));
        }
    }

    @FXML
    public void handleQuickStart() {
        showQuickStart();
    }

    private void applyOperationFormatProfile(String itemName) {
        String profileOperation = formatProfileOperation(itemName);
        com.cryptocarver.model.OperationFormatProfile profile = com.cryptocarver.model.OperationFormatRegistry.getInstance().getProfile(profileOperation);

        String rememberedInput = rememberedInputFormats.get(profileOperation);
        applyFormatToCombo(inputFormatCombo, profile.allowedInputFormats(), profile.defaultInputFormat(), rememberedInput);

        String rememberedOutput = rememberedOutputFormats.get(profileOperation);
        applyFormatToCombo(outputFormatCombo, profile.allowedOutputFormats(), profile.defaultOutputFormat(), rememberedOutput);
        currentFormatProfileOperation = profileOperation;

        if (genericContainerController != null) {
            genericContainerController.setActiveFormatContractOperation(profileOperation);
        }

        if (contractOperationLabel != null) {
            String opText = "Operation";
            java.util.Optional<com.cryptocarver.model.OperationDescriptor> op = com.cryptocarver.model.OperationRegistry.getInstance().resolveNavigation(itemName);
            if (op.isPresent()) {
                opText = op.get().getTitle();
            } else {
                opText = itemName;
            }
            contractOperationLabel.setText(opText);

            // Add a tooltip for the contract description
            String defaultPayloadTooltip = i18n.text("toolbar.payloadTooltip");
            if (profile.contractDescription() != null && !profile.contractDescription().isEmpty()) {
                Tooltip tooltipObj = new Tooltip(profile.contractDescription());
                contractOperationLabel.setTooltip(tooltipObj);
                if (inputFormatCombo != null) {
                    inputFormatCombo.setTooltip(new Tooltip(defaultPayloadTooltip + "\n" + profile.contractDescription()));
                }
                if (outputFormatCombo != null) {
                    outputFormatCombo.setTooltip(tooltipObj);
                }
            } else {
                contractOperationLabel.setTooltip(null);
                if (inputFormatCombo != null) {
                    inputFormatCombo.setTooltip(new Tooltip(defaultPayloadTooltip));
                }
                if (outputFormatCombo != null) {
                    outputFormatCombo.setTooltip(null);
                }
            }
        }
    }

    private String formatProfileOperation(String operation) {
        return operation != null && operation.startsWith("Hashing:") ? "Hashing" : operation;
    }

    private void applyFormatToCombo(ComboBox<String> combo, java.util.List<String> allowedFormats, String defaultFormat, String remembered) {
        if (combo == null) return;

        if (allowedFormats == null || allowedFormats.isEmpty()) {
            // Operations without a shared byte-format contract must not mutate
            // the last meaningful toolbar selection while navigating.
            combo.setDisable(true);
            return;
        }

        combo.setDisable(false);
        combo.getItems().setAll(allowedFormats);

        if (remembered != null && allowedFormats.contains(remembered)) {
            combo.setValue(remembered);
        } else if (defaultFormat != null && allowedFormats.contains(defaultFormat)) {
            combo.setValue(defaultFormat);
        } else if (!allowedFormats.isEmpty()) {
            combo.setValue(allowedFormats.get(0));
        }
    }

    private String operationStatusSummary(com.cryptocarver.model.OperationDescriptor operation) {
        String status = operation.getStatus() == com.cryptocarver.model.OperationDescriptor.Status.EXPERIMENTAL
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

        // Ensure history container is visible (it might be hidden by Saved Sessions
        // view)
        if (historyContainer != null && !historyContainer.isVisible()) {
            historyContainer.setManaged(true);
            historyContainer.setVisible(true);
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
        UiNavigationRegistry.Route route = activeConfigurationRoute();
        ConfigurationTarget target = configurationTarget(route);
        if (route == null || target == null) {
            throw new IllegalStateException("The current screen does not expose a portable configuration");
        }
        java.util.Map<String, Object> state = new java.util.LinkedHashMap<>(
                UiStateSnapshot.capturePortableConfiguration(target.controller(), target.root(), route.section()));
        if (inputFormatCombo != null && inputFormatCombo.getValue() != null) {
            state.put("ModernMainController.inputFormatCombo", inputFormatCombo.getValue());
        }
        if (outputFormatCombo != null && outputFormatCombo.getValue() != null) {
            state.put("ModernMainController.outputFormatCombo", outputFormatCombo.getValue());
        }
        return new com.cryptocarver.model.ScreenConfiguration(
                currentActiveOperation, route.module().name(), state,
                com.cryptocarver.model.AppSettings.getInstance().getSecretVisibilityProfile());
    }

    void applyScreenConfiguration(com.cryptocarver.model.ScreenConfiguration configuration) {
        if (configuration == null) throw new IllegalArgumentException("Configuration is required");
        String operation = com.cryptocarver.model.OperationRegistry.getInstance()
                .resolveNavigation(configuration.operation())
                .map(com.cryptocarver.model.OperationDescriptor::getNavigationPath)
                .orElse(configuration.operation());
        UiNavigationRegistry.Route route = UiNavigationRegistry.resolve(operation)
                .orElseThrow(() -> new IllegalArgumentException("Unsupported configuration operation: " + operation));
        if (!route.module().name().equals(configuration.module())) {
            throw new IllegalArgumentException("Configuration module does not match its operation");
        }

        handleItemSelected(operation);
        ConfigurationTarget target = configurationTarget(route);
        if (target == null) throw new IllegalArgumentException("Configuration target is not available");

        java.util.Set<String> allowed = new java.util.LinkedHashSet<>(
                UiStateSnapshot.capturePortableConfiguration(
                        target.controller(), target.root(), route.section()).keySet());
        // Version 1 exported every control owned by a module, including hidden
        // accordion panes. Accept those documents for backwards compatibility.
        if (configuration.version() == 1) {
            allowed.addAll(UiStateSnapshot.capturePortableConfiguration(target.controller()).keySet());
        }
        allowed.add("ModernMainController.inputFormatCombo");
        allowed.add("ModernMainController.outputFormatCombo");
        java.util.Map<String, Object> state = configuration.toState();
        java.util.Set<String> unknown = new java.util.LinkedHashSet<>(state.keySet());
        unknown.removeAll(allowed);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Configuration contains fields outside the target screen: "
                    + String.join(", ", unknown.stream().limit(5).toList()));
        }
        UiStateSnapshot.restore(this, state);
        updateStatus("Screen configuration loaded: " + operation);
    }

    private UiNavigationRegistry.Route activeConfigurationRoute() {
        String operation = currentActiveOperation;
        if (operation != null && operation.startsWith("Hashing: ")) operation = "Hashing";
        return UiNavigationRegistry.resolve(operation).orElse(null);
    }

    private ConfigurationTarget configurationTarget(UiNavigationRegistry.Route route) {
        if (route == null) return null;
        return switch (route.module()) {
            case JOSE -> new ConfigurationTarget(joseController, jose);
            case COSE -> new ConfigurationTarget(coseController, cose);
            case KEYS_SYMMETRIC, KEYS_ASYMMETRIC -> new ConfigurationTarget(keysContainerController, keysContainer);
            case CERTIFICATES -> new ConfigurationTarget(certificatesContainerController, certificatesContainer);
            case GENERIC -> new ConfigurationTarget(genericContainerController, genericContainer);
            case POST_QUANTUM -> new ConfigurationTarget(postQuantumContainerController, postQuantumContainer);
            case XML_SECURITY -> new ConfigurationTarget(xmlSecurityContainerController, xmlSecurityContainer);
            case WSS_SECURITY -> new ConfigurationTarget(wssSecurityContainerController, wssSecurityContainer);
            case EMV -> new ConfigurationTarget(emvContainerController, emvContainer);
            case CIPHER -> new ConfigurationTarget(cipherContainerController, cipherContainer);
            case AUTHENTICATION -> new ConfigurationTarget(authenticationContainerController, authenticationContainer);
            case PAYMENTS -> new ConfigurationTarget(paymentsContainerController, paymentsContainer);
            default -> null;
        };
    }

    private record ConfigurationTarget(Object controller, javafx.scene.Parent root) { }

    private java.util.List<javafx.scene.Node> restoreUIState(java.util.Map<String, Object> state) {
        return UiStateSnapshot.restore(this, state);
    }

    // History Managemen

    private void initializeHistory() {
        if (historyManager == null) {
            historyManager = new com.cryptocarver.model.HistoryManager();
        }
        if (sidePanel != null) {
            sidePanel.setHistoryManager(historyManager);
            sidePanel.setOnHistoryItemSelected(this::showRecentHistoryCommand);
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
        if (historyContainer == null || historyManager == null) return;
        historyContainer.getChildren().clear();

        java.util.List<com.cryptocarver.model.HistoryCommand> items = historyManager.getHistoryItems();

        if (items.isEmpty()) {
            Label placeholder = new Label("No recent operations");
            placeholder.getStyleClass().add("muted-text");
            placeholder.setStyle("-fx-font-size: 11px; -fx-padding: 10;");
            historyContainer.getChildren().add(placeholder);
        } else {
            for (com.cryptocarver.model.HistoryCommand item : items) {
                HBox historyCommand = new HBox(8);
                historyCommand.getStyleClass().add("history-card");
                historyCommand.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                VBox infoBox = new VBox(2);
                Label opLabel = new Label(item.getOperation());
                opLabel.getStyleClass().add("history-card-title");

                String relTime = formatRelativeTime(item.getTimestamp());
                Label timeLabel = new Label(relTime);
                timeLabel.getStyleClass().add("history-card-time");
                Tooltip.install(timeLabel, new Tooltip("Executed on: " + item.getTimestamp()));

                infoBox.getChildren().addAll(opLabel, timeLabel);
                HBox.setHgrow(infoBox, javafx.scene.layout.Priority.ALWAYS);

                Button reopenButton = new Button("Reopen");
                reopenButton.getStyleClass().add("history-card-action");
                reopenButton.setAccessibleText("Reopen operation " + item.getOperation());

                reopenButton.setOnAction(e -> {
                    reopenHistoryOperation(item);
                });

                historyCommand.getChildren().addAll(infoBox, reopenButton);
                historyContainer.getChildren().add(historyCommand);
            }
        }

        refreshHistoryNavigation();
    }

    @Override
    public void refreshHistoryNavigation() {
        if (sidePanel != null) {
            sidePanel.updateContent(sidePanel.getCurrentSection());
        }
    }

    private String formatRelativeTime(String timestampStr) {
        if (timestampStr == null) return "";
        try {
            java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(
                timestampStr,
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            );
            java.time.ZonedDateTime zdt = ldt.atZone(java.time.ZoneId.systemDefault());
            long timeMillis = zdt.toInstant().toEpochMilli();
            long delta = System.currentTimeMillis() - timeMillis;
            if (delta < 0) return "just now";
            if (delta < 60000) {
                return (delta / 1000) + "s ago";
            } else if (delta < 3600000) {
                return (delta / 60000) + "m ago";
            } else if (delta < 86400000) {
                return (delta / 3600000) + "h ago";
            } else {
                return (delta / 86400000) + "d ago";
            }
        } catch (Exception e) {
            return timestampStr; // Fallback
        }
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
        addToHistory(operation, details, effectiveNavigationTarget(operation, currentActiveOperation));
    }

    /**
     * Picks the value used to reopen a history entry. The currently active screen is
     * preferred (it disambiguates sub-operations that share a display name), but only
     * when it actually resolves to a catalogued operation. Otherwise — e.g. history is
     * recorded before any navigation happened, or from a stale/default screen name —
     * fall back to the operation label itself so the entry stays reopenable and its
     * module filter/category stays correct instead of collapsing to "Other".
     */
    private String effectiveNavigationTarget(String operation, String candidateNavigationOperation) {
        if (candidateNavigationOperation != null && !candidateNavigationOperation.isBlank()
                && com.cryptocarver.model.OperationRegistry.getInstance()
                        .resolveNavigation(candidateNavigationOperation).isPresent()) {
            return candidateNavigationOperation;
        }
        return operation;
    }

    private void addToHistory(String operation, java.util.List<com.cryptocarver.model.OperationDetail> details,
                              String navigationOperation) {
        java.util.Map<String, Object> state = captureHistoryState();

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

        com.cryptocarver.model.HistoryCommand.Reproducibility rep = com.cryptocarver.model.HistoryCommand.Reproducibility.REPRODUCIBLE_WITHOUT_SECRETS;
        String reason = "All parameters are available.";
        if (state.values().contains("[REDACTED_SECRET]")) {
            rep = com.cryptocarver.model.HistoryCommand.Reproducibility.REPRODUCIBLE_WITH_SECRETS;
            reason = "Sensitive secrets were redacted from the history recipe.";
        }

        String inFmt = inputFormatCombo != null ? inputFormatCombo.getValue() : null;
        String outFmt = outputFormatCombo != null ? outputFormatCombo.getValue() : null;

        com.cryptocarver.model.HistoryCommand item = new com.cryptocarver.model.HistoryCommand(
                operation, detailsJson, state, rep, reason, inFmt, outFmt, navigationOperation);
        item.setStructuredDetails(details);

        if (historyManager == null) {
            initializeHistory();
        }

        historyManager.addHistoryItem(item);

        refreshHistoryUI();
    }

    public void addToHistoryManual(String operation, String detailsString) {
        java.util.Map<String, Object> state = captureHistoryState();
        com.cryptocarver.model.HistoryCommand.Reproducibility rep = com.cryptocarver.model.HistoryCommand.Reproducibility.REPRODUCIBLE_WITHOUT_SECRETS;
        String reason = "All parameters are available.";
        if (state.values().contains("[REDACTED_SECRET]")) {
            rep = com.cryptocarver.model.HistoryCommand.Reproducibility.REPRODUCIBLE_WITH_SECRETS;
            reason = "Sensitive secrets were redacted from the history recipe.";
        }

        String inFmt = inputFormatCombo != null ? inputFormatCombo.getValue() : null;
        String outFmt = outputFormatCombo != null ? outputFormatCombo.getValue() : null;

        com.cryptocarver.model.HistoryCommand item = new com.cryptocarver.model.HistoryCommand(
                operation, detailsString, state, rep, reason, inFmt, outFmt,
                effectiveNavigationTarget(operation, currentActiveOperation));

        if (historyManager == null) {
            initializeHistory();
        }

        historyManager.addHistoryItem(item);
        refreshHistoryUI();
    }

    /** Exposes the shared history store to the FXML history module. */
    public com.cryptocarver.model.HistoryManager getHistoryManager() {
        if (historyManager == null) initializeHistory();
        return historyManager;
    }

    /** Restores an operation selected from the modular history view. */
    public void restoreOperationState(java.util.Map<String, Object> state, String operation) {
        handleItemSelected(operation);
        java.util.List<javafx.scene.Node> redacted = UiStateSnapshot.restoreHistoryRecipe(this, state);
        if (redacted != null && !redacted.isEmpty()) {
            updateStatus("Restored configuration for: " + operation + ". Re-enter redacted sensitive values.");
            javafx.application.Platform.runLater(() -> redacted.get(0).requestFocus());
        } else {
            updateStatus("Restored state for: " + operation);
        }
    }

    @FXML
    private void handleExportHistory() {
        if (historyManager == null || historyManager.getHistoryItems().isEmpty()) {
            Alert alert = LocalizedDialogSupport.alert(Alert.AlertType.INFORMATION,
                    "dialog.exportHistory.title", "dialog.exportHistory.emptyHeader",
                    i18n.text("dialog.exportHistory.emptyContent"));
            alert.showAndWait();
            return;
        }

        FileChooser fileChooser = LocalizedDialogSupport.fileChooser(
                "dialog.exportHistory.title", "dialog.exportHistory.filter", "JSON Files", "*.json");
        fileChooser.setInitialFileName("cryptocarver-history-export.json");

        File file = fileChooser.showSaveDialog(mainPane.getScene().getWindow());
        if (file != null) {
            try (PrintWriter writer = new PrintWriter(file, StandardCharsets.UTF_8)) {
                com.cryptocarver.model.SecretVisibilityProfile visibility = com.cryptocarver.model.AppSettings.getInstance()
                        .getSecretVisibilityProfile();
                String json = com.cryptocarver.utils.HistoryRecordExporter.toJson(
                        historyManager.getHistoryItems(), visibility);
                writer.write(json);

                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle(i18n.text("dialog.exportHistory.success"));
                alert.setHeaderText(null);
                alert.setContentText("History successfully exported using " + visibility + " policy to:\n"
                        + file.getAbsolutePath());
                alert.showAndWait();
            } catch (IOException e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle(i18n.text("dialog.exportHistory.failure"));
                alert.setHeaderText(i18n.text("dialog.exportHistory.saveFailure"));
                alert.setContentText(e.getMessage());
                alert.showAndWait();
            }
        }
    }

    @FXML
    private void handleClearHistory() {
        if (historyManager != null && (historyManager.getHistoryItems().isEmpty() || Boolean.getBoolean("test.mode")
                || confirmClearHistory())) {
            historyManager.clearHistory();
            refreshHistoryUI();
            updateStatus("History cleared");
        }
    }

    private boolean confirmClearHistory() {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                i18n.text("module.history.clearConfirm"), ButtonType.CANCEL, ButtonType.OK);
        confirmation.setTitle(i18n.text("module.history.clearTitle"));
        confirmation.setHeaderText(i18n.text("module.history.clearHeader"));
        return confirmation.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

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

    private void showSymmetricKeys() {
        hideAllContainers();
        if (keysContainer != null) {
            keysContainer.setManaged(true);
            keysContainer.setVisible(true);
        }
        if (keysController != null) keysController.showSymmetricSection();
    }

    private void showAsymmetricKeys() {
        hideAllContainers();

        if (contentTitleLabel != null) {
            contentTitleLabel.setText("Asymmetric Keys");
        }

        if (keysContainer != null) {
            keysContainer.setManaged(true);
            keysContainer.setVisible(true);
        }
        if (keysController != null) keysController.showAsymmetricSection();
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
                if (!targetPane.isEmpty()
                        && ModulePaneMatcher.matches(pane, targetPane, ModuleTextCatalog.cipher())) {
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
                if (!targetPane.isEmpty()
                        && ModulePaneMatcher.matches(pane, targetPane, ModuleTextCatalog.authentication())) {
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
        contentPlaceholderLabel.setText("📋 " + requestedOperation
                + "\n\nNo view is registered for this legacy operation. Select a tool from the side panel.");
        contentPlaceholderLabel.setManaged(true);
        contentPlaceholderLabel.setVisible(true);
        updateContentHeader(requestedOperation);
        updateContentSubtitle("No module available");
    }

    private void expandAccordionPane(String paneName) {
        if (keysController != null) keysController.expandSymmetricPane(paneName);
    }

    private void expandAsymmetricAccordionPane(String paneName) {
        if (keysController != null) keysController.expandAsymmetricPane(paneName);
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
        if (statusLabel == null) return;
        statusLabel.setText(message);
        statusResetTimer.stop();
        statusResetTimer.setOnFinished(event -> statusLabel.setText(i18n.text("status.ready")));
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
        } else if (isContainerVisible(certificatesContainer)) {
            // Certificate clearing not fully implemented via global toolbar ye
        }

        clearPublishedResultSnapshot();
        updateStatus(i18n.text("status.inputCleared"));
    }

    @FXML
    private void handleClearOutput() {
        // Reuse clear input logic for now (Clear All)
        handleClearInput();
        updateStatus(i18n.text("status.outputCleared"));
    }

    public boolean hasCurrentResult() {
        return lastPublishedResultSnapshot != null || !resolveCurrentOutputText().isBlank();
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
        if (lastPublishedResultSnapshot == null) return "";
        boolean hasArtifact = (lastPublishedResultSnapshot.getEnrichedOutput() != null
                && !lastPublishedResultSnapshot.getEnrichedOutput().isBlank())
                || (lastPublishedResultSnapshot.getOutput() != null
                && lastPublishedResultSnapshot.getOutput().length > 0);
        return hasArtifact ? renderPublishedResult(lastPublishedResultSnapshot,
                com.cryptocarver.model.AppSettings.getInstance().getSecretVisibilityProfile()) : "";
    }

    private boolean isShelfCaptureBlockedByVisibility(TextArea area) {
        com.cryptocarver.model.OperationDetail.Classification classification = classificationForResultArea(area);
        com.cryptocarver.model.SecretVisibilityProfile visibility =
                com.cryptocarver.model.AppSettings.getInstance().getSecretVisibilityProfile();
        return visibility != com.cryptocarver.model.SecretVisibilityProfile.FULL_LAB
                && (classification == com.cryptocarver.model.OperationDetail.Classification.SECRET
                || classification == com.cryptocarver.model.OperationDetail.Classification.SENSITIVE);
    }

    private boolean isPrivateMaterialPlaceholder(String text) {
        if (text == null) return false;
        String normalized = text.toUpperCase(java.util.Locale.ROOT);
        return normalized.contains("PRIVATE KEY MATERIAL") && normalized.contains("NOT RECORDED");
    }

    private boolean isCompletePrivateKeyMaterial(String text) {
        if (text == null || isPrivateMaterialPlaceholder(text)) return false;
        String normalized = text.toUpperCase(java.util.Locale.ROOT);
        return normalized.contains("-----BEGIN ")
                && normalized.contains("PRIVATE KEY-----")
                && normalized.contains("-----END ")
                && normalized.contains("PRIVATE KEY-----");
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
                && visibility != com.cryptocarver.model.SecretVisibilityProfile.FULL_LAB) {
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
        if (result == null) {
            return;
        }
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> publish(result));
            return;
        }
        lastPublishedOperation = result.getOperation();
        lastPublishedResultSnapshot = result;
        updateInspector(result.getOperation(), result.getInput(), result.getOutput(), result.getDetails());
        addToHistory(result.getOperation(), detailsForHistory(result),
                effectiveNavigationTarget(result.getOperation(), currentActiveOperation));
        if (result.getStatusMessage() != null && !result.getStatusMessage().isBlank()) {
            updateStatus(result.getStatusMessage());
        }

        boolean isFailed = result.getStatusMessage() != null && result.getStatusMessage().toLowerCase(java.util.Locale.ROOT).contains("failed");
        boolean hasPayload = (result.getOutput() != null && result.getOutput().length > 0)
                || (result.getEnrichedOutput() != null && !result.getEnrichedOutput().isBlank());
        boolean hasInspectableResult = hasPayload || !result.getDetails().isEmpty();

        if (resultSummaryBar != null) {
            if (isFailed || !hasInspectableResult) {
                resultSummaryBar.setManaged(false);
                resultSummaryBar.setVisible(false);
            } else {
                resultSummaryBar.setManaged(true);
                resultSummaryBar.setVisible(true);
                if (resultOpLabel != null) resultOpLabel.setText(result.getOperation());

                // Resolve algorithm
                String algo = "N/A";
                if (result.getDetails() != null) {
                    for (com.cryptocarver.model.OperationDetail d : result.getDetails()) {
                        if ("Algorithm".equalsIgnoreCase(d.name()) || "Type".equalsIgnoreCase(d.name())) {
                            algo = d.value();
                            break;
                        }
                    }
                }
                if (resultAlgoLabel != null) resultAlgoLabel.setText(algo);

                // Resolve sizes
                int inLen = result.getInput() != null ? result.getInput().length : 0;
                int outLen = result.getOutput() != null ? result.getOutput().length : 0;
                if (resultSizeLabel != null) resultSizeLabel.setText(i18n.text("result.size", inLen, outLen));

                // Resolve output format
                String outFormat = outputFormatCombo != null ? outputFormatCombo.getValue() : "HEX";
                if (resultFormatLabel != null) resultFormatLabel.setText(outFormat);

                // Success / Error status
                if (resultStatusBadge != null) {
                    resultStatusBadge.setText(i18n.text("result.success"));
                    resultStatusBadge.getStyleClass().setAll("result-status-success");
                    resultStatusBadge.setStyle("");
                    resultStatusBadge.setAccessibleText(i18n.text("a11y.resultStatus", resultStatusBadge.getText()));
                }
            }
        }
    }

    /** Clears the cached result whenever it no longer represents the visible UI state. */
    private void clearPublishedResultSnapshot() {
        lastPublishedOperation = "";
        lastPublishedResultSnapshot = null;
        resultAreaTracker.clearSelection();
        if (resultSummaryBar != null) {
            resultSummaryBar.setManaged(false);
            resultSummaryBar.setVisible(false);
        }
    }


    /** Renders bytes as strict UTF-8 where possible, otherwise as hexadecimal. */
    private String renderBytesForDisplay(byte[] bytes) {
        return OperationResultRenderer.renderBytes(bytes);
    }

    private boolean isPrintableUtf8(byte[] bytes) {
        return OperationResultRenderer.isPrintableUtf8(bytes);
    }

    /**
     * Keeps an operation's actual byte input/output alongside its declared
     * details. They are marked SENSITIVE: visible in Unsafe lab, masked in
     * normal exports and never treated as ordinary public metadata.
     */
    private java.util.List<com.cryptocarver.model.OperationDetail> detailsForHistory(
            com.cryptocarver.model.OperationResult result) {
        java.util.List<com.cryptocarver.model.OperationDetail> details = new java.util.ArrayList<>(result.getDetails());
        addPayloadDetail(details, "Input", result.getInput());
        addPayloadDetail(details, "Output", result.getOutput());
        return details;
    }

    private void addPayloadDetail(java.util.List<com.cryptocarver.model.OperationDetail> details, String name, byte[] bytes) {
        if (bytes == null) {
            return;
        }
        String rendered = renderBytesForDisplay(bytes);
        String format = isPrintableUtf8(bytes) ? "UTF-8" : "Hex";
        details.add(new com.cryptocarver.model.OperationDetail(
                name + " (" + bytes.length + " bytes)", rendered,
                com.cryptocarver.model.OperationDetail.Classification.SENSITIVE,
                rendered.indexOf('\n') >= 0 || rendered.length() > 120, format));
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
        resultAreaTracker.install(mainPane, additionalAreas,
                area -> area.setContextMenu(createResultContextMenu(area)));
    }

    private ContextMenu createResultContextMenu(TextArea area) {
        MenuItem expand = new MenuItem("Open in Expanded Viewer");
        expand.setOnAction(event -> {
            resultAreaTracker.focus(area);
            handleOpenExpandedResultViewer();
        });

        MenuItem addToShelf = new MenuItem("Add to Clipboard Shelf");
        addToShelf.setOnAction(event -> {
            resultAreaTracker.focus(area);
            String selected = area.getSelectedText();
            handleAddToClipboardShelfSecure(area, selected != null && !selected.isEmpty() ? selected : null);
        });

        MenuItem copyToSys = new MenuItem("Copy to System Clipboard");
        copyToSys.setOnAction(event -> {
            resultAreaTracker.focus(area);
            handleCopySecure(area, null, false);
        });

        MenuItem copy = new MenuItem("Copy");
        copy.setOnAction(event -> {
            resultAreaTracker.focus(area);
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
            String content = resolveResultText(area);
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

        if (!resultAreaTracker.isCurrentSelection(area, lastPublishedResultSnapshot != null)) {
            updateStatus("Action blocked: Cannot securely copy selection from old or unknown result.");
            return;
        }
        com.cryptocarver.model.OperationDetail.Classification cls = classificationForResultArea(area);
        boolean requiresFullLab = cls == com.cryptocarver.model.OperationDetail.Classification.SECRET
                || cls == com.cryptocarver.model.OperationDetail.Classification.SENSITIVE;
        if (requiresFullLab
                && com.cryptocarver.model.AppSettings.getInstance().getSecretVisibilityProfile() != com.cryptocarver.model.SecretVisibilityProfile.FULL_LAB) {
            updateStatus("Action blocked: Cannot copy partial selection of protected text in current visibility mode.");
            return;
        }

        copyToClipboard(textToCopy);
    }

    private void handleAddToClipboardShelfSecure(javafx.scene.control.TextArea area, String selectedText) {
        String text;
        if (selectedText != null) {
            if (!resultAreaTracker.isCurrentSelection(area, lastPublishedResultSnapshot != null)) {
                updateStatus("Action blocked: Cannot securely add selection from old or unknown result.");
                return;
            }
            com.cryptocarver.model.OperationDetail.Classification cls = classificationForResultArea(area);
            boolean requiresFullLab = cls == com.cryptocarver.model.OperationDetail.Classification.SECRET
                    || cls == com.cryptocarver.model.OperationDetail.Classification.SENSITIVE;
            if (requiresFullLab
                    && com.cryptocarver.model.AppSettings.getInstance().getSecretVisibilityProfile() != com.cryptocarver.model.SecretVisibilityProfile.FULL_LAB) {
                updateStatus("Action blocked: Cannot add partial selection of protected text in current visibility mode.");
                return;
            }
            text = selectedText;
        } else {
            text = resolveShelfCaptureText(area);
        }

        if (text == null || text.isEmpty()) {
             updateStatus(isShelfCaptureBlockedByVisibility(area)
                     ? "Action blocked: output hidden by visibility policy."
                     : "No current output available.");
             return;
        }

        if (text.equals("***MASKED***") || isPrivateMaterialPlaceholder(text)) {
             updateStatus(text.equals("***MASKED***")
                     ? "Action blocked: output hidden by visibility policy."
                     : "Action blocked: private output is not an explicit complete private-key area.");
            return;
        }

        com.cryptocarver.model.ClipboardEntry.Format format = com.cryptocarver.model.ClipboardEntry.Format.inferFormat(text);
        com.cryptocarver.model.OperationDetail.Classification cls = classificationForResultArea(area);
        // Raw asymmetric private-key material (a complete PEM block) gets a dedicated,
        // deliberately non-persistent path below: it is never written to the Shelf's
        // JSON file on disk, only held in memory for this session. The check is on the
        // *content shape* (isCompletePrivateKeyMaterial), not just the source area's id
        // — a private key pasted or rendered somewhere other than a designated
        // "…PrivateKeyArea" still gets the same protection. Every other SECRET-classified
        // result (derived/symmetric keys from KDF, wrapped keys, PIN blocks, CVVs, DUKPT
        // outputs, etc.) falls through to the normal Shelf entry path further down —
        // classification plus the visibility profile (FULL_LAB/MASKED/REDACTED, already
        // applied above in resolveShelfCaptureText/renderResultArea) are the security
        // levels this lab tool uses to gate secret exposure; an additional hard block on
        // top of those was redundant and silently dropped legitimate lab results (e.g.
        // Add to Shelf after a KDF derivation) with no path to fix it.
        boolean privateKeyMaterial = selectedText == null
                && (ResultAreaTracker.isPrivateKeyResultArea(area) || isCompletePrivateKeyMaterial(text));
        if (cls == com.cryptocarver.model.OperationDetail.Classification.SECRET && privateKeyMaterial) {
            if (isCompletePrivateKeyMaterial(text)
                    && com.cryptocarver.model.AppSettings.getInstance().getSecretVisibilityProfile()
                        == com.cryptocarver.model.SecretVisibilityProfile.FULL_LAB) {
                String sourceOp = lastPublishedResultSnapshot != null ? lastPublishedResultSnapshot.getOperation()
                        : (currentActiveOperation != null ? currentActiveOperation : "Unknown");
                String algorithm = null;
                if (lastPublishedResultSnapshot != null && lastPublishedResultSnapshot.getDetails() != null) {
                    for (com.cryptocarver.model.OperationDetail detail : lastPublishedResultSnapshot.getDetails()) {
                        if (detail != null && ("Algorithm".equalsIgnoreCase(detail.name())
                                || "Type".equalsIgnoreCase(detail.name()))) {
                            algorithm = detail.value();
                            break;
                        }
                    }
                }
                com.cryptocarver.model.ClipboardEntry sessionEntry =
                        com.cryptocarver.model.ClipboardShelfManager.getInstance()
                                .addSessionOnlyPrivateKey(text, sourceOp, algorithm);
                updateStatus(sessionEntry != null
                        ? "Added private key to Clipboard Shelf (session only)."
                        : "Action blocked: session-only private keys require FULL_LAB.");
                revealShelfEntry(sessionEntry);
                return;
            }
            updateStatus(isCompletePrivateKeyMaterial(text)
                    ? "Action blocked: private keys can only be added to the Shelf (session only) under FULL_LAB visibility."
                    : "Action blocked: private-key area does not contain complete, exportable key material.");
            return;
        }
        String sourceOp = lastPublishedResultSnapshot != null ? lastPublishedResultSnapshot.getOperation() : (currentActiveOperation != null ? currentActiveOperation : "Unknown");
        String algorithm = null;
        if (lastPublishedResultSnapshot != null && lastPublishedResultSnapshot.getDetails() != null) {
            for (com.cryptocarver.model.OperationDetail detail : lastPublishedResultSnapshot.getDetails()) {
                if (detail != null && ("Algorithm".equalsIgnoreCase(detail.name())
                        || "Type".equalsIgnoreCase(detail.name()))) {
                    algorithm = detail.value();
                    break;
                }
            }
        }

        java.util.Optional<com.cryptocarver.model.ClipboardEntry> duplicate = com.cryptocarver.model.ClipboardShelfManager.getInstance().findDuplicate(text, sourceOp);
        if (duplicate.isPresent()) {
            updateStatus("Item already in Clipboard Shelf: " + duplicate.get().getLabel());
            return;
        }

        com.cryptocarver.model.ClipboardEntry entry = new com.cryptocarver.model.ClipboardEntry(
                "Copied from " + sourceOp,
                text,
                format,
                cls,
                sourceOp,
                algorithm
        );
        // Full authenticated-cipher results are stored as a typed package.
        // A partial selection deliberately remains the historical simple value.
        if (selectedText == null && cipherController != null && cipherController.isPrimaryOutput(area)) {
            com.cryptocarver.model.ShelfPackage packageData = cipherController.createAuthenticatedCipherShelfPackage();
            if (packageData != null) {
                text = packageData.artifact("ciphertext");
                format = com.cryptocarver.model.ClipboardEntry.Format.inferFormat(text);
                entry = new com.cryptocarver.model.ClipboardEntry(
                        "Authenticated ciphertext from " + sourceOp, text, format, cls, sourceOp, algorithm)
                        .withShelfPackage(packageData);
            }
        }
        com.cryptocarver.model.ClipboardShelfManager.getInstance().addEntry(entry);
        revealShelfEntry(entry);
        updateStatus("Added public output to Clipboard Shelf.");
    }

    void revealShelfEntry(com.cryptocarver.model.ClipboardEntry entry) {
        if (entry != null && clipboardShelfController != null) {
            clipboardShelfController.refreshAndReveal(entry.getId());
        }
    }

    public com.cryptocarver.model.OperationDetail.Classification classifyPublishedResult(com.cryptocarver.model.OperationResult result) {
        return OperationResultRenderer.classification(result);
    }

    public void fillClipboardTarget(String targetType, String value, com.cryptocarver.model.ClipboardEntry.Format format) {
        fillClipboardTarget(targetType, value, format, null);
    }

    public void fillClipboardTarget(String targetType, String value, com.cryptocarver.model.ClipboardEntry.Format format,
                                    com.cryptocarver.model.ShelfPackage packageData) {
        if (value == null) return;
        switch (targetType) {
            case "MANUAL_CONVERSION":
                if (genericContainerController != null) {
                    navigateToModule("Manual Conversion");
                    genericContainerController.fillManualConversionInput(value, format);
                    expandGenericAccordionPane("Manual Conversion");
                }
                break;
            case "SYMMETRIC_CIPHER":
                if (cipherController != null) {
                    navigateToModule("Symmetric Ciphers");
                    if (packageData != null) cipherController.fillSymmetricCipherPackage(packageData);
                    else cipherController.fillSymmetricCipherInput(value, format);
                    expandCipherAccordionPane("Symmetric Ciphers");
                }
                break;
            case "HASHING":
                if (genericContainerController != null) {
                    navigateToModule("Hashing");
                    genericContainerController.fillHashInput(value, format);
                    expandGenericAccordionPane("Hashing");
                }
                break;
            case "XML_SECURITY":
                if (xmlSecurityContainerController != null) {
                    navigateToModule("XML Security");
                    xmlSecurityContainerController.fillClipboardInput(value);
                    expandXMLAccordionPane("Inspect Signed XML");
                }
                break;
            case "WSS_SECURITY":
                if (wssSecurityContainerController != null) {
                    navigateToModule("WSS Security");
                    wssSecurityContainerController.fillClipboardInput(value);
                    expandWssAccordionPane("Sign SOAP");
                }
                break;
            case "PAYMENTS":
                if (paymentsContainerController != null) {
                    navigateToModule("Payments");
                    paymentsContainerController.fillClipboardInput(value);
                    expandPaymentsAccordionPane("Clear PIN Blocks");
                }
                break;
            case "TR31":
                if (keysController != null) {
                    navigateToModule("TR-31 Key Blocks");
                    keysController.fillTR31KeyBlockInput(value);
                    expandAccordionPane("TR-31 Key Blocks");
                }
                break;
            case "JOSE_JWT":
                if (joseController != null) {
                    joseController.fillJwtPayload(value);
                    showJOSE();
                }
                break;
            default:
                updateStatus("Unsupported target: " + targetType);
        }
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
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Keyboard Shortcuts");
        alert.setHeaderText("CryptoCarver Keyboard Shortcuts");

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
        alert.getDialogPane().setContent(contentBox);
        alert.getDialogPane().setPrefWidth(580);
        alert.showAndWait();
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
        if (error == null) return null;
        String title = error.title() == null ? "" : error.title().toLowerCase(java.util.Locale.ROOT);
        String keyPrefix = null;
        if (title.contains("authentication tag") || title.contains("tag verification") || title.contains("autenticación")) keyPrefix = "error.wrap.tag";
        else if (title.contains("padding")) keyPrefix = "error.wrap.padding";
        else if (title.contains("key parameter") || title.contains("parámetro de clave")) keyPrefix = "error.wrap.key";
        else if (title.contains("hexadecimal")) keyPrefix = "error.wrap.hex";
        else if (title.contains("base64")) keyPrefix = "error.wrap.base64";
        else if ((title.contains("certificate") || title.contains("certificado") || title.contains("key format"))
                && !title.startsWith("missing ") && !title.startsWith("falta ")) keyPrefix = "error.wrap.cert";
        else if (title.contains("timestamp authority") || title.contains("sellado de tiempo")) keyPrefix = "error.wrap.tsa";
        if (keyPrefix == null) return error;
        return new UserFacingError(
                i18n.text(keyPrefix + ".title"),
                i18n.text(keyPrefix + ".detail"),
                i18n.text(keyPrefix + ".remedy"),
                error.fieldKey(), error.cause());
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
    // EVENT HANDLERS - Payments Operations
    // ============================================================

    private void hideAllContainers() {
        contentContainer.getChildren().stream()
                .filter(node -> node instanceof Label)
                .forEach(node -> {
                    node.setManaged(false);
                    node.setVisible(false);
                });

        if (keysContainer != null) {
            keysContainer.setVisible(false);
            keysContainer.setManaged(false);
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
        if (cose != null) {
            cose.setVisible(false);
            cose.setManaged(false);
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
        if (wssSecurityContainer != null) {
            wssSecurityContainer.setVisible(false);
            wssSecurityContainer.setManaged(false);
        }
        if (quickStartContainer != null) {
            quickStartContainer.setVisible(false);
            quickStartContainer.setManaged(false);
        }
        if (savedSessionsContainer != null) {
            savedSessionsContainer.setVisible(false);
            savedSessionsContainer.setManaged(false);
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
        if (jose != null) {
            jose.setManaged(true);
            jose.setVisible(true);
        }
        if (joseController != null) {
            joseController.showSection(currentActiveOperation);
        }
    }

    private void showCOSE() {
        hideAllContainers();
        if (cose != null) {
            cose.setManaged(true);
            cose.setVisible(true);
        }
        if (coseController != null) {
            coseController.showSection(currentActiveOperation);
        }
    }

    private void expandGenericAccordionPane(String paneName) {
        if (paneName == null || paneName.isBlank() || genericContainer == null || genericContainer.getPanes().isEmpty())
            return;

        for (TitledPane pane : genericContainer.getPanes()) {
            if (ModulePaneMatcher.matches(pane, paneName, ModuleTextCatalog.generic())) {
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
                    if (ModulePaneMatcher.matches(pane, title, ModuleTextCatalog.emv())) {
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

    // ============================================================
    // SAVED SESSIONS LOGIC
    // ============================================================

    private void initializeSavedSessions() {
        if (savedSessionsManager == null) {
            savedSessionsManager = com.cryptocarver.model.SavedSessionsManager.getInstance();
        }
        refreshSavedSessionsUI();
    }

    private void refreshSavedSessionsUI() {
        if (savedSessionsList == null)
            return;
        savedSessionsList.getChildren().clear();

        if (savedSessionsManager == null)
            return;

        java.util.List<com.cryptocarver.model.SavedSession> sessions = savedSessionsManager.getSessions();

        if (sessions.isEmpty()) {
            Label placeholder = new Label("No saved sessions");
            placeholder.setStyle("-fx-text-fill: #718096; -fx-font-size: 11px; -fx-padding: 10;");
            savedSessionsList.getChildren().add(placeholder);
            return;
        }

        for (com.cryptocarver.model.SavedSession session : sessions) {
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
    private void handleVisibilityFullLab() {
        com.cryptocarver.model.AppSettings.getInstance().setSecretVisibilityProfile(com.cryptocarver.model.SecretVisibilityProfile.FULL_LAB);
        updateStatus("Visibility set to FULL_LAB (Debug/Learning)");
        if (keysController != null) {
            keysController.updateVisibilityControls();
            keysController.refreshKeyLabTable();
        }
    }

    @FXML
    private void handleVisibilityMasked() {
        com.cryptocarver.model.AppSettings.getInstance().setSecretVisibilityProfile(com.cryptocarver.model.SecretVisibilityProfile.MASKED);
        updateStatus("Visibility set to MASKED (Classroom/Demo)");
        if (keysController != null) {
            keysController.updateVisibilityControls();
            keysController.refreshKeyLabTable();
        }
    }

    @FXML
    private void handleVisibilityRedacted() {
        com.cryptocarver.model.AppSettings.getInstance().setSecretVisibilityProfile(com.cryptocarver.model.SecretVisibilityProfile.REDACTED);
        updateStatus("Visibility set to REDACTED (Strict/Production)");
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

    @FXML
    public void handleSaveSession() {
        // Init manager
        if (savedSessionsManager == null) {
            savedSessionsManager = com.cryptocarver.model.SavedSessionsManager.getInstance();
        }

        // Ask for name
        TextInputDialog dialog = LocalizedDialogSupport.textInput(
                "dialog.saveSession.title", "dialog.saveSession.header", "dialog.saveSession.prompt", "My Session");

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

            com.cryptocarver.model.SavedSession session = new com.cryptocarver.model.SavedSession(name, currentOperation,
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
    private void handleExportScreenConfiguration() {
        final com.cryptocarver.model.ScreenConfiguration configuration;
        try {
            configuration = captureActiveScreenConfiguration();
        } catch (Exception e) {
            showWarning("Screen Configuration", e.getMessage());
            return;
        }

        ChoiceDialog<String> modeDialog = new ChoiceDialog<>("Encrypted (.ccconfig)",
                "Encrypted (.ccconfig)", "Plain JSON — unsafe");
        modeDialog.setTitle(i18n.text("dialog.configuration.exportTitle"));
        modeDialog.setHeaderText("This configuration may contain keys, passwords, PINs or payloads.");
        modeDialog.setContentText("Protection:");
        java.util.Optional<String> mode = modeDialog.showAndWait();
        if (mode.isEmpty()) return;

        boolean encrypted = mode.get().startsWith("Encrypted");
        char[] password = null;
        if (encrypted) {
            java.util.Optional<char[]> selected = promptConfigurationPassword(true);
            if (selected.isEmpty()) return;
            password = selected.get();
        } else {
            Alert warning = new Alert(Alert.AlertType.CONFIRMATION,
                    "The exported JSON can contain raw cryptographic keys and sensitive input. Continue?",
                    ButtonType.CANCEL, ButtonType.OK);
            warning.setTitle("Unsafe Plain Configuration");
            warning.setHeaderText("Secrets will not be encrypted");
            if (warning.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n.text("dialog.configuration.exportTitle"));
        chooser.setInitialFileName("cryptocarver-" + safeFileName(configuration.operation())
                + (encrypted ? ".ccconfig" : ".json"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                encrypted ? "Encrypted CryptoCarver Configuration" : "CryptoCarver Configuration JSON",
                encrypted ? "*.ccconfig" : "*.json"));
        File file = chooser.showSaveDialog(mainPane == null || mainPane.getScene() == null
                ? null : mainPane.getScene().getWindow());
        if (file == null) {
            if (password != null) java.util.Arrays.fill(password, '\0');
            return;
        }
        try {
            String document = encrypted
                    ? com.cryptocarver.model.ScreenConfigurationCodec.encodeEncrypted(configuration, password)
                    : com.cryptocarver.model.ScreenConfigurationCodec.encodePlain(configuration);
            com.cryptocarver.model.ScreenConfigurationFiles.writeAtomic(file.toPath(), document);
            updateStatus("Screen configuration exported: " + file.getName());
            showInfo("Configuration Exported", encrypted
                    ? "Encrypted configuration saved. Share its password separately."
                    : "Plain configuration saved. Treat the file as sensitive material.");
        } catch (Exception e) {
            showError("Configuration Export", e.getMessage());
        } finally {
            if (password != null) java.util.Arrays.fill(password, '\0');
        }
    }

    @FXML
    private void handleImportScreenConfiguration() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n.text("dialog.configuration.importTitle"));
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(i18n.text("dialog.configuration.filter"), "*.ccconfig", "*.json"),
                new FileChooser.ExtensionFilter(i18n.text("dialog.allFiles"), "*.*"));
        File file = chooser.showOpenDialog(mainPane == null || mainPane.getScene() == null
                ? null : mainPane.getScene().getWindow());
        if (file == null) return;
        try {
            String document = com.cryptocarver.model.ScreenConfigurationFiles.read(file.toPath());
            char[] password = null;
            if (com.cryptocarver.model.ScreenConfigurationCodec.isEncrypted(document)) {
                java.util.Optional<char[]> selected = promptConfigurationPassword(false);
                if (selected.isEmpty()) return;
                password = selected.get();
            }
            com.cryptocarver.model.ScreenConfiguration configuration;
            try {
                configuration = com.cryptocarver.model.ScreenConfigurationCodec.decode(document, password);
            } finally {
                if (password != null) java.util.Arrays.fill(password, '\0');
            }
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                    "Operation: " + configuration.operation()
                            + "\nModule: " + configuration.module()
                            + "\nFields: " + configuration.values().size()
                            + "\nCreated: " + configuration.createdAt()
                            + "\n\nImporting may place raw keys or passwords in the laboratory UI.",
                    ButtonType.CANCEL, ButtonType.OK);
            confirmation.setTitle("Import Screen Configuration");
            confirmation.setHeaderText("Review portable configuration");
            if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            applyScreenConfiguration(configuration);
            if (isLegacyKeyGenerationConfiguration(configuration)) {
                showWarning("Generated Key Not Present",
                        "The configuration settings were restored, but this file does not contain the generated key. "
                                + "It was exported by a version that excluded read-only generated material, so the "
                                + "original key cannot be reconstructed. Generate a new key and export the screen again.");
            }
        } catch (Exception e) {
            showError("Configuration Import", e.getMessage());
        }
    }

    static boolean isLegacyKeyGenerationConfiguration(
            com.cryptocarver.model.ScreenConfiguration configuration) {
        if (configuration == null || !"Key Generation".equals(configuration.operation())) return false;
        com.cryptocarver.model.ScreenConfiguration.Value generated =
                configuration.values().get("KeysController.generatedKeyField");
        return generated == null || generated.value().isBlank();
    }

    private java.util.Optional<char[]> promptConfigurationPassword(boolean confirmationRequired) {
        Dialog<char[]> dialog = new Dialog<>();
        dialog.setTitle(confirmationRequired ? "Protect Configuration" : "Unlock Configuration");
        dialog.setHeaderText(confirmationRequired
                ? "Use at least 8 characters and share the password separately."
                : "Enter the password used to protect this configuration.");
        ButtonType accept = new ButtonType(confirmationRequired ? "Encrypt" : "Unlock", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, accept);
        PasswordField password = new PasswordField();
        password.setPromptText("Password");
        VBox fields = new VBox(8, new Label("Password:"), password);
        PasswordField confirmation = null;
        if (confirmationRequired) {
            confirmation = new PasswordField();
            confirmation.setPromptText("Repeat password");
            fields.getChildren().addAll(new Label("Repeat password:"), confirmation);
        }
        dialog.getDialogPane().setContent(fields);
        PasswordField confirmationField = confirmation;
        javafx.scene.Node acceptButton = dialog.getDialogPane().lookupButton(accept);
        acceptButton.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                () -> password.getText().length() < 8 || (confirmationField != null
                        && !password.getText().equals(confirmationField.getText())),
                confirmationField == null
                        ? new javafx.beans.Observable[]{password.textProperty()}
                        : new javafx.beans.Observable[]{password.textProperty(), confirmationField.textProperty()}));
        dialog.setResultConverter(button -> button == accept ? password.getText().toCharArray() : null);
        Platform.runLater(password::requestFocus);
        return dialog.showAndWait();
    }

    private String safeFileName(String value) {
        String safe = value == null ? "screen" : value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return safe.isBlank() ? "screen" : safe;
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

    private void loadWssSecurityContent() {
        if (wssSecurityContainerController != null) {
            wssSecurityContainerController.initModule(this);
        }
    }

    private void showWssSecurity() {
        hideAllContainers();
        if (wssSecurityContainer != null) {
            wssSecurityContainer.setManaged(true);
            wssSecurityContainer.setVisible(true);
        }
        if (mainScrollPane != null) mainScrollPane.setVvalue(0);
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
        this.currentGuidedOp = op;
        this.currentGuidedStep = 1;
        switch (op) {
            case ENCRYPT -> handleItemSelected("Symmetric Encryption");
            case HASH -> handleItemSelected("Hashing");
            case SIGN -> handleItemSelected("Digital Signatures");
            case CERT -> handleItemSelected("Parse Certificate");
            case CONVERT -> handleItemSelected("Manual Conversion");
        }
        if (guidedFlowPanel != null) {
            guidedFlowPanel.setVisible(true);
            guidedFlowPanel.setManaged(true);
            setupGuidedFlowKeyboardAndTooltips();
        }
        updateGuidedStepUI();
    }

    @FXML
    private void handleGuideNext() {
        if (currentGuidedStep < 5) {
            currentGuidedStep++;
            updateGuidedStepUI();
        }
    }

    @FXML
    private void handleGuideBack() {
        if (currentGuidedStep > 1) {
            currentGuidedStep--;
            updateGuidedStepUI();
        }
    }

    @FXML
    private void handleGuideSkip() {
        currentGuidedStep = 4;
        updateGuidedStepUI();
    }

    @FXML
    private void handleGuideExit() {
        if (guidedFlowPanel != null) {
            guidedFlowPanel.setVisible(false);
            guidedFlowPanel.setManaged(false);
        }
    }

    private void updateGuidedStepUI() {
        if (guideStepTitleLabel == null || guideStepDescLabel == null || currentGuidedOp == null) return;

        if (guideBackBtn != null) guideBackBtn.setDisable(currentGuidedStep <= 1);
        if (guideNextBtn != null) guideNextBtn.setDisable(currentGuidedStep >= 5);

        switch (currentGuidedStep) {
            case 1 -> {
                guideStepTitleLabel.setText("Step 1 of 5: Choose data/input format");
                guideStepDescLabel.setText("Configure the input encoding on the format flow bar (UTF-8, Hex, Base64).");
                if (inputFormatCombo != null) inputFormatCombo.requestFocus();
            }
            case 2 -> {
                guideStepTitleLabel.setText("Step 2 of 5: Choose algorithm & settings (or Start from a Template)");
                switch (currentGuidedOp) {
                    case ENCRYPT -> guideStepDescLabel.setText("Select cipher algorithm (e.g. AES-256), mode (GCM/CBC), or Apply a safe template.");
                    case HASH -> guideStepDescLabel.setText("Select digest algorithm (e.g. SHA-256, SHA-512) or Apply a safe template.");
                    case SIGN -> guideStepDescLabel.setText("Select signature scheme (e.g. RSA-SHA256, ECDSA) or Apply a safe template.");
                    case CERT -> guideStepDescLabel.setText("Configure certificate format options or Apply a safe template.");
                    case CONVERT -> guideStepDescLabel.setText("Select target output encoding (Base64, Hex, EBCDIC) or Apply a template.");
                }
            }
            case 3 -> {
                guideStepTitleLabel.setText("Step 3 of 5: Provide key / material");
                switch (currentGuidedOp) {
                    case ENCRYPT -> guideStepDescLabel.setText("Select key source (Manual, Key Lab, HSM). For GCM/CBC, click Generate for a fresh IV/nonce. (Applying a template does not auto-advance or supply keys).");
                    case HASH -> guideStepDescLabel.setText("Enter or paste the input payload to hash.");
                    case SIGN -> guideStepDescLabel.setText("Select Private key (to sign) or Public key/cert (to verify).");
                    case CERT -> guideStepDescLabel.setText("Paste PEM certificate text into input area.");
                    case CONVERT -> guideStepDescLabel.setText("Enter input data to convert.");
                }
            }
            case 4 -> {
                guideStepTitleLabel.setText("Step 4 of 5: Review & execute");
                guideStepDescLabel.setText("Review your configuration and click the Execute/Run button to process data safely.");
            }
            case 5 -> {
                guideStepTitleLabel.setText("Step 5 of 5: Inspect, copy & save");
                guideStepDescLabel.setText("Inspect output bytes in summary bar or inspector. Copy or send to Clipboard Shelf.");
            }
        }
    }

    private void setupGuidedFlowKeyboardAndTooltips() {
        if (guidedFlowPanel == null) return;

        if (guideBackBtn != null) guideBackBtn.setTooltip(new Tooltip(i18n.text("guide.backTooltip")));
        if (guideNextBtn != null) guideNextBtn.setTooltip(new Tooltip(i18n.text("guide.nextTooltip")));

        guidedFlowPanel.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                handleGuideExit();
                event.consume();
            } else if (event.getCode() == javafx.scene.input.KeyCode.ENTER) {
                if (currentGuidedStep < 4) {
                    handleGuideNext();
                    event.consume();
                }
            }
        });
    }




    private void setupLaboratoryMenu() {
        if (mainMenuBar == null) return;
        boolean hasLabMenu = mainMenuBar.getMenus().stream().anyMatch(m -> "Laboratory".equals(m.getText()));
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
    private boolean readinessShowDetails = false;
    private boolean readinessPanelActivated = false;
    private com.cryptocarver.model.PreflightReport currentPreflightReport;
    private boolean currentPreflightEncrypt = true;

    @FXML
    private void handleToggleReadinessDetails() {
        readinessShowDetails = !readinessShowDetails;
        if (readinessToggleDetailsBtn != null) {
            readinessToggleDetailsBtn.setText(readinessShowDetails ? "Hide Details" : "Show Details");
        }
        updateReadinessPanelUI();
    }

    @Override
    public boolean checkPreflightReadiness(String operation, boolean isEncrypt) {
        updateReadinessPanelForOperation(operation, isEncrypt);
        if (currentPreflightReport != null && !currentPreflightReport.isExecutable()) {
            com.cryptocarver.model.PreflightCheck firstIssue = currentPreflightReport.getFirstNonReadyCheck();
            if (firstIssue != null && firstIssue.getTargetControlKey() != null) {
                focusControl(firstIssue.getTargetControlKey());
            }
            if (readinessPanel != null) {
                readinessPanel.setManaged(true);
                readinessPanel.setVisible(true);
            }
            String msg = firstIssue != null ? localizedPreflightMessage(firstIssue) : i18n.text("preflight.remedy.generic");
            String fieldKey = firstIssue != null ? firstIssue.getTargetControlKey() : null;
            showError(new UserFacingError(i18n.text("preflight.title"), msg,
                    localizedPreflightRemedy(firstIssue), fieldKey));
            return false;
        }
        return true;
    }

    public void updateReadinessPanel() {
        refreshReadinessPanelForOperation(currentActiveOperation, currentPreflightEncrypt);
    }

    public void updateReadinessPanelForOperation(String operation, boolean isEncrypt) {
        readinessPanelActivated = true;
        refreshReadinessPanelForOperation(operation, isEncrypt);
    }

    private void refreshReadinessPanelForOperation(String operation, boolean isEncrypt) {
        if (readinessPanel == null) return;
        currentPreflightEncrypt = isEncrypt;

        com.cryptocarver.model.PreflightReport report = evaluatePreflightForOperation(operation, isEncrypt);
        currentPreflightReport = report;
        if (report == null) {
            readinessPanel.setManaged(false);
            readinessPanel.setVisible(false);
            return;
        }

        if (report.isExecutable()) {
            readinessPanelActivated = false;
        }

        // Keep validation out of the user's way while they type. The panel is
        // a recovery aid after an attempted execution was blocked, not a
        // permanent header banner for every partially completed form.
        boolean showPanel = readinessPanelActivated && !report.isExecutable();
        readinessPanel.setManaged(showPanel);
        readinessPanel.setVisible(showPanel);
        if (!showPanel) return;
        updateReadinessPanelUI();
    }

    private void updateReadinessPanelUI() {
        if (currentPreflightReport == null || readinessStatusBadge == null || readinessSummaryLabel == null || readinessChecksContainer == null) return;

        com.cryptocarver.model.PreflightStatus status = currentPreflightReport.getOverallStatus();
        switch (status) {
            case READY -> {
                readinessStatusBadge.setText("✔ READY");
                readinessStatusBadge.getStyleClass().setAll("readiness-status-ready");
                readinessStatusBadge.setStyle("");
            }
            case WARNING -> {
                readinessStatusBadge.setText("⚠️ WARNING");
                readinessStatusBadge.getStyleClass().setAll("readiness-status-warning");
                readinessStatusBadge.setStyle("");
            }
            case INCOMPLETE -> {
                readinessStatusBadge.setText("❓ INCOMPLETE");
                readinessStatusBadge.getStyleClass().setAll("readiness-status-incomplete");
                readinessStatusBadge.setStyle("");
            }
            case BLOCKED -> {
                readinessStatusBadge.setText("⛔ BLOCKED");
                readinessStatusBadge.getStyleClass().setAll("readiness-status-blocked");
                readinessStatusBadge.setStyle("");
            }
        }

        readinessSummaryLabel.setText(localizedPreflightSummary(currentPreflightReport));

        readinessChecksContainer.getChildren().clear();
        java.util.List<com.cryptocarver.model.PreflightCheck> checks = currentPreflightReport.getChecks();
        int maxVisible = readinessShowDetails ? checks.size() : Math.min(3, checks.size());

        for (int i = 0; i < maxVisible; i++) {
            com.cryptocarver.model.PreflightCheck check = checks.get(i);
            Button checkBtn = new Button();
            String icon = switch (check.getStatus()) {
                case READY -> "✔ ";
                case WARNING -> "⚠️ ";
                case INCOMPLETE -> "❓ ";
                case BLOCKED -> "⛔ ";
            };
            checkBtn.setText(icon + check.getName() + ": " + localizedPreflightMessage(check));
            checkBtn.getStyleClass().add("readiness-check-button");
            checkBtn.setOnAction(e -> {
                if (check.getTargetControlKey() != null) {
                    focusControl(check.getTargetControlKey());
                }
            });
            readinessChecksContainer.getChildren().add(checkBtn);
        }
    }

    private String localizedPreflightSummary(com.cryptocarver.model.PreflightReport report) {
        long issues = report.getChecks().stream()
                .filter(check -> check.getStatus() != com.cryptocarver.model.PreflightStatus.READY)
                .count();
        return switch (report.getOverallStatus()) {
            case READY -> i18n.text("preflight.summary.ready");
            case BLOCKED -> i18n.text("preflight.summary.blocked", issues);
            case INCOMPLETE -> i18n.text("preflight.summary.incomplete", issues);
            case WARNING -> i18n.text("preflight.summary.warning", issues);
        };
    }

    private String localizedPreflightMessage(com.cryptocarver.model.PreflightCheck check) {
        if (check == null) return i18n.text("preflight.remedy.generic");
        String message = check.getMessage() == null ? "" : check.getMessage();
        String lower = message.toLowerCase(java.util.Locale.ROOT);
        String target = check.getTargetControlKey() == null ? "" : check.getTargetControlKey().toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("empty") || lower.contains("required") || lower.contains("missing")) {
            if (target.contains("tag")) return i18n.text("preflight.tag.required");
            if (target.contains("signature")) return i18n.text("preflight.signature.required");
            if (target.contains("algorithm")) return i18n.text("preflight.algorithm.required");
            if (target.contains("mode")) return i18n.text("preflight.mode.required");
            if (target.contains("key")) return i18n.text("preflight.key.required");
            if (target.contains("iv") || target.contains("nonce")) return i18n.text("preflight.iv.required");
            return i18n.text("preflight.input.required");
        }
        if (lower.contains("non-hexadecimal") || lower.contains("invalid characters")) {
            if (target.contains("key")) return i18n.text("preflight.key.invalid");
            if (target.contains("iv") || target.contains("nonce")) return i18n.text("preflight.iv.invalid");
            if (target.contains("tag")) return i18n.text("preflight.tag.invalid");
            return i18n.text("preflight.input.hex.invalid");
        }
        if (lower.contains("odd number")) return i18n.text("preflight.input.hex.odd");
        if (lower.contains("base64")) return i18n.text("preflight.input.base64.invalid");
        return message;
    }

    private String localizedPreflightRemedy(com.cryptocarver.model.PreflightCheck check) {
        if (check == null || check.getTargetControlKey() == null) return i18n.text("preflight.remedy.generic");
        String target = check.getTargetControlKey().toLowerCase(java.util.Locale.ROOT);
        if (target.contains("algorithm")) return i18n.text("preflight.remedy.algorithm");
        if (target.contains("mode")) return i18n.text("preflight.remedy.mode");
        if (target.contains("key")) return i18n.text("preflight.remedy.key");
        if (target.contains("iv") || target.contains("nonce")) return i18n.text("preflight.remedy.iv");
        if (target.contains("tag")) return i18n.text("preflight.remedy.tag");
        if (target.contains("input") || target.contains("data")) return i18n.text("preflight.remedy.input");
        return i18n.text("preflight.remedy.generic");
    }

    private com.cryptocarver.model.PreflightReport evaluatePreflightForOperation(String operation, boolean isEncrypt) {
        if (operation == null) return null;
        String opName = formatProfileOperation(operation);

        if ("Symmetric Ciphers".equals(opName)) {
            if (cipherContainerController == null) return null;
            return com.cryptocarver.model.OperationPreflightEngine.checkSymmetricCipher(
                    getFieldText(cipherContainerController, "cipherInputArea"),
                    inputFormatCombo != null ? inputFormatCombo.getValue() : "Text (UTF-8)",
                    getComboValue(cipherContainerController, "symmetricAlgorithmCombo"),
                    getComboValue(cipherContainerController, "cipherModeCombo"),
                    getComboValue(cipherContainerController, "paddingCombo"),
                    getComboValue(cipherContainerController, "symKeySourceCombo"),
                    getFieldText(cipherContainerController, "symmetricKeyField"),
                    getComboValue(cipherContainerController, "symHsmKeyCombo"),
                    isHsmKeyMetadataOnly(getComboValue(cipherContainerController, "symHsmKeyCombo")),
                    getFieldText(cipherContainerController, "ivField"),
                    getFieldText(cipherContainerController, "gcmTagField"),
                    getFieldText(cipherContainerController, "aadField"),
                    isEncrypt
            );
        } else if ("Hashing".equals(opName)) {
            return com.cryptocarver.model.OperationPreflightEngine.checkHashing(
                    getFieldText(genericContainerController, "hashInputArea"),
                    inputFormatCombo != null ? inputFormatCombo.getValue() : "Text (UTF-8)",
                    getComboValue(genericContainerController, "hashAlgorithmCombo")
            );
        } else if ("Digital Signatures".equals(opName)) {
            String keyText = isEncrypt ? getFieldText(authenticationContainerController, "signaturePrivateKeyArea") : getFieldText(authenticationContainerController, "signaturePublicKeyArea");
            String verifyText = getFieldText(authenticationContainerController, "signatureVerifyField");
            return com.cryptocarver.model.OperationPreflightEngine.checkDigitalSignature(
                    getFieldText(authenticationContainerController, "authInputArea"),
                    getComboValue(authenticationContainerController, "signatureAlgorithmCombo"),
                    keyText,
                    verifyText,
                    false,
                    isEncrypt
            );
        } else if ("Message Authentication Codes".equals(opName)) {
            String keySource = getComboValue(authenticationContainerController, "macKeySourceCombo");
            String keyReference = getComboValue(authenticationContainerController, "macHsmKeyCombo");
            String macVerifyText = getFieldText(authenticationContainerController, "authMacVerifyField");
            return com.cryptocarver.model.OperationPreflightEngine.checkMac(
                    getFieldText(authenticationContainerController, "authInputArea"),
                    getComboValue(authenticationContainerController, "authMacAlgorithmCombo"),
                    keySource,
                    getFieldText(authenticationContainerController, "authMacKeyField"),
                    keyReference,
                    macVerifyText,
                    isEncrypt,
                    "Simulated HSM".equalsIgnoreCase(keySource) && isHsmKeyMetadataOnly(keyReference)
            );
        } else if ("Asymmetric Ciphers".equals(opName)) {
            String keyText = isEncrypt ? getFieldText(cipherContainerController, "publicKeyArea") : getFieldText(cipherContainerController, "privateKeyArea");
            if ((keyText == null || keyText.isBlank())
                    && cipherContainerController != null
                    && cipherContainerController.hasAsymmetricKeyAvailable(isEncrypt)) {
                keyText = "[loaded key pair]";
            }
            return com.cryptocarver.model.OperationPreflightEngine.checkAsymmetricCipher(
                    getFieldText(cipherContainerController, "cipherInputArea"),
                    keyText,
                    false,
                    getComboValue(cipherContainerController, "rsaPaddingCombo"),
                    isEncrypt
            );
        }

        return null;
    }

    private boolean isHsmKeyMetadataOnly(String keyId) {
        if (keyId == null || keyId.isEmpty()) return false;
        try {
            var km = com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().getKeyMetadata(keyId);
            return km != null && !km.hasKeyMaterial();
        } catch (Exception e) {
            return false;
        }
    }

    private String getFieldText(Object controllerObj, String fieldName) {
        if (controllerObj == null || fieldName == null) return "";
        try {
            java.lang.reflect.Field field = controllerObj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object val = field.get(controllerObj);
            if (val instanceof TextInputControl tic) {
                return tic.getText();
            }
        } catch (Exception ignored) {}
        return "";
    }

    private String getComboValue(Object controllerObj, String fieldName) {
        if (controllerObj == null || fieldName == null) return null;
        try {
            java.lang.reflect.Field field = controllerObj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object val = field.get(controllerObj);
            if (val instanceof ComboBox<?> cb) {
                Object selected = cb.getValue();
                return selected != null ? selected.toString() : null;
            }
        } catch (Exception ignored) {}
        return null;
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
                    row.setStyle("-fx-padding: 6 10;");

                    Label categoryBadge = new Label("[" + item.getCategory() + "]");
                    categoryBadge.getStyleClass().add("command-palette-category");

                    VBox textContainer = new VBox(2);
                    Label titleLabel = new Label(item.getTitle());
                    titleLabel.getStyleClass().add("history-card-title");

                    Label descLabel = new Label(item.getDescription());
                    descLabel.getStyleClass().add("subtle-text");
                    descLabel.setStyle("-fx-font-size: 11px;");

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
