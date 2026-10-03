package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.ClipboardShelfManager;
import com.cryptocarver.model.HistoryManager;
import com.cryptocarver.service.I18nService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.*;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;

/** Owns ordered shell startup, fixed router construction and startup listener registrations. */
final class ShellStartupCoordinator {
    private final I18nService i18n;
    private final List<Runnable> detachments = new ArrayList<>();

    ShellStartupCoordinator(I18nService i18n) { this.i18n = i18n; }

    record ChromeView(ComboBox<String> input, ComboBox<String> output, Label inputLabel,
            Label contract, Label title, Label subtitle, HBox breadcrumbs, Button section,
            Label separator1, Button module, Label separator2, Label operation, Button favorite,
            String favoriteShortcut, Consumer<String> formatOperation, Consumer<Object> breadcrumbSection,
            Consumer<String> navigate) {
        NavigationChromeCoordinator create() {
            return new NavigationChromeCoordinator(input, output, inputLabel, contract, title, subtitle,
                    breadcrumbs, section, separator1, module, separator2, operation, favorite,
                    favoriteShortcut, formatOperation, breadcrumbSection, navigate);
        }
    }

    record PresenterView(HBox errorBanner, Label errorTitle, Label errorRemedy, Button goToField,
            Button copyDetails, Button closeError, Label securityTip, VBox securityBox,
            Label status, Button visibility, Label language, ToggleGroup visibilityGroup) {}

    record Services(Consumer<ShellDialogCoordinator> dialogs, Consumer<NavigationRouter> router,
            Consumer<NavigationChromeCoordinator> chrome, Consumer<InlineErrorPresenter> errors,
            Consumer<StatusBarPresenter> status, Consumer<NavigationController> navigation,
            Consumer<Consumer<Locale>> localeListener) {}

    record Actions(Runnable lifecycle, Runnable laboratory, Runnable palette, Runnable accelerators,
            Runnable responsive, Runnable localize, Runnable history, Runnable trail, Runnable loadKeys,
            Runnable showKeys, Runnable restoreRoute, Runnable fonts, Runnable results, Runnable tables,
            Consumer<String> itemSelected, Consumer<String> phaseCompleted, BooleanSupplier closed) {}

    record View(DialogService dialogService, Supplier<Window> window, Supplier<ModuleHost[]> hosts,
            ModuleLoader loader, Supplier<NavigationRouter> routerFactory, ChromeView chrome,
            PresenterView presenters, Supplier<NavigationRail> rail, Supplier<SidePanel> side,
            Supplier<StatusReporter> reporter, OperationExecutor executor, Consumer<String> showProgress,
            Consumer<OperationExecutor.ProgressDetails> updateProgress, Runnable hideProgress,
            Services services, Actions actions) {}

    void initialize(View view) {
        Services services = view.services(); Actions actions = view.actions(); PresenterView controls = view.presenters();
        step(view, "dialogs", () -> services.dialogs().accept(new ShellDialogCoordinator(view.dialogService(), view.window())));
        step(view, "deferred", () -> {
            for (ModuleHost host : view.hosts().get()) if (host != null) host.configure(view.loader(), Runnable::run);
        });
        step(view, "router", () -> services.router().accept(view.routerFactory().get()));
        step(view, "chrome", () -> services.chrome().accept(view.chrome().create()));
        System.out.println("ModernMainController initializing...");
        step(view, "shelf", () -> ClipboardShelfManager.getInstance().setReporter(view.reporter().get()));
        step(view, "progress", () -> view.executor().setProgressHandlers(view.showProgress(), view.updateProgress(), view.hideProgress()));
        step(view, "lifecycle", actions.lifecycle());
        step(view, "laboratory", actions.laboratory());
        step(view, "palette", actions.palette());
        step(view, "accelerators", actions.accelerators());
        step(view, "errors", () -> services.errors().accept(new InlineErrorPresenter(controls.errorBanner(),
                controls.errorTitle(), controls.errorRemedy(), controls.goToField(), controls.copyDetails(), controls.closeError())));
        step(view, "security-tip", () -> {
            if (controls.securityTip() != null && controls.securityBox() != null) {
                javafx.beans.value.ChangeListener<String> listener = (obs, oldValue, newValue) -> {
                    boolean hasTip = newValue != null && !newValue.trim().isEmpty();
                    controls.securityBox().setVisible(hasTip); controls.securityBox().setManaged(hasTip);
                };
                controls.securityTip().textProperty().addListener(listener);
                detachments.add(() -> controls.securityTip().textProperty().removeListener(listener));
            }
        });
        step(view, "status", () -> services.status().accept(new StatusBarPresenter(controls.status(), controls.visibility(), controls.language(), i18n)));
        step(view, "visibility", () -> {
            if (controls.visibilityGroup() != null) {
                var profile = AppSettings.getInstance().getSecretVisibilityProfile();
                for (Toggle toggle : controls.visibilityGroup().getToggles()) {
                    if (toggle instanceof RadioMenuItem item && item.getText().contains(profile.name())) {
                        item.setSelected(true); break;
                    }
                }
            }
        });
        step(view, "responsive", actions.responsive());
        step(view, "navigation", () -> {
            NavigationRail rail = view.rail().get(); SidePanel side = view.side().get();
            rail.setSidePanel(side); side.setOnItemSelected(actions.itemSelected());
            var navigation = new NavigationController(rail, side, actions.itemSelected());
            services.navigation().accept(navigation); navigation.install();
            detachments.add(() -> { rail.setOnSectionSelected(null); side.setOnItemSelected(null); });
        });
        step(view, "localization", () -> {
            i18n.refreshFromSettings();
            Consumer<Locale> listener = locale -> {
                if (Platform.isFxApplicationThread()) actions.localize().run();
                else Platform.runLater(actions.localize());
            };
            services.localeListener().accept(listener);
            i18n.addLocaleChangeListener(listener);
            detachments.add(() -> i18n.removeLocaleChangeListener(listener));
            actions.localize().run();
        });
        step(view, "history", actions.history());
        step(view, "trail", actions.trail());
        step(view, "keys-load", actions.loadKeys());
        step(view, "keys-show", actions.showKeys());
        step(view, "route", actions.restoreRoute());
        step(view, "fonts", actions.fonts());
        step(view, "results", actions.results());
        step(view, "tables-scheduled", () -> Platform.runLater(() -> {
            if (!actions.closed().getAsBoolean()) actions.tables().run();
        }));
        System.out.println("ModernMainController initialized successfully!");
    }

    private static void step(View view, String phase, Runnable action) {
        action.run(); view.actions().phaseCompleted().accept(phase);
    }

    void shutdown() {
        detachments.forEach(Runnable::run); detachments.clear();
    }

    record ShellServices(Supplier<StatusReporter> reporter, Supplier<OperationNavigator> navigator,
            Supplier<HistoryManager> history, Supplier<ComboBox<String>> input, Supplier<ComboBox<String>> output,
            Consumer<ClipboardShelfController> shelfNavigation) {}

    void connectShellServices(Object controller, ShellServices services) {
        if (controller instanceof JOSEController jose) {
            jose.setReporter(services.reporter().get());
        } else if (controller instanceof COSEController cose) {
            cose.setReporter(services.reporter().get());
        } else if (controller instanceof WalletController wallet) {
            wallet.setReporter(services.reporter().get());
        } else if (controller instanceof HistoryController history) {
            history.setHistoryManager(services.history().get()); history.setOperationNavigator(services.navigator().get());
        } else if (controller instanceof ClipboardShelfController shelf) {
            services.shelfNavigation().accept(shelf);
        } else if (controller instanceof GenericController generic) {
            generic.setStatusReporter(services.reporter().get()); generic.setFormatControls(services.input().get(), services.output().get());
            if (generic.getKeyCertificateWorkbenchController() != null) {
                generic.getKeyCertificateWorkbenchController().setStatusReporter(services.reporter().get());
            }
            if (generic.getCryptoEnvelopeInspectorController() != null) {
                generic.getCryptoEnvelopeInspectorController().setStatusReporter(services.reporter().get());
            }
        }
    }

    record RouterView(Supplier<ModuleHost[]> hosts, Supplier<Pane> content, Supplier<Node> quickStart,
            Supplier<Node> savedContainer, Function<UiNavigationRegistry.Module, Node> host,
            Function<UiNavigationRegistry.Module, Object> controller, Supplier<String> activeOperation,
            Supplier<KeysController> keys, Supplier<ModuleHost> processDesigner,
            Runnable loadKeys, Consumer<String> symmetric, Consumer<String> asymmetric, Consumer<String> certificates,
            Consumer<String> generic, Runnable loadPqc, Consumer<String> pqc, Runnable loadXml, Consumer<String> xml,
            Runnable loadWss, Consumer<String> wss, Runnable loadEmv, Consumer<String> emv,
            Runnable loadCipher, Consumer<String> cipher, Runnable loadAuthentication, Consumer<String> authentication,
            Runnable loadPayments, Consumer<String> payments, Runnable epoch, Runnable json, Runnable savedSessions,
            Runnable enterProcessDesigner, Runnable exitProcessDesigner, Consumer<String> header,
            Consumer<String> subtitle, Consumer<String> status, Consumer<String> itemSelected) {}

    static NavigationRouter createNavigationRouter(RouterView view) {
        java.util.EnumMap<UiNavigationRegistry.Module, java.util.function.BiConsumer<UiNavigationRegistry.Route,Object>> callbacks = new java.util.EnumMap<>(UiNavigationRegistry.Module.class);
        callbacks.put(UiNavigationRegistry.Module.JOSE, (r,c) -> { if(c instanceof JOSEController x) x.showSection(view.activeOperation().get()); });
        callbacks.put(UiNavigationRegistry.Module.COSE, (r,c) -> { if(c instanceof COSEController x) x.showSection(view.activeOperation().get()); });
        callbacks.put(UiNavigationRegistry.Module.WALLET, (r,c) -> { if(c instanceof WalletController x) x.showSection(view.activeOperation().get()); });
        callbacks.put(UiNavigationRegistry.Module.EPOCH_CONVERTER, (r,c) -> view.epoch().run());
        callbacks.put(UiNavigationRegistry.Module.JSON_FORMATTER, (r,c) -> view.json().run());
        callbacks.put(UiNavigationRegistry.Module.KEYS_SYMMETRIC, (r,c) -> { view.loadKeys().run(); if(view.keys().get()!=null) view.keys().get().showSymmetricSection(); view.symmetric().accept(r.section()); });
        callbacks.put(UiNavigationRegistry.Module.KEYS_ASYMMETRIC, (r,c) -> { view.loadKeys().run(); if(view.keys().get()!=null) view.keys().get().showAsymmetricSection(); view.asymmetric().accept(r.section()); });
        callbacks.put(UiNavigationRegistry.Module.CERTIFICATES, (r,c) -> {
            view.certificates().accept(r.section());
            if(c instanceof CertificatesController x) {
                if(r.variant()==UiNavigationRegistry.Variant.ASN1_DECODE) x.selectAsn1DecodeTab();
                else if(r.variant()==UiNavigationRegistry.Variant.ASN1_ENCODE) x.selectAsn1EncodeTab();
            }
        });
        callbacks.put(UiNavigationRegistry.Module.GENERIC, (r,c) -> view.generic().accept(r.section()));
        callbacks.put(UiNavigationRegistry.Module.POST_QUANTUM, (r,c) -> { view.loadPqc().run(); view.pqc().accept(r.section()); });
        callbacks.put(UiNavigationRegistry.Module.XML_SECURITY, (r,c) -> { view.loadXml().run(); view.xml().accept(r.section()); });
        callbacks.put(UiNavigationRegistry.Module.WSS_SECURITY, (r,c) -> { view.loadWss().run(); view.wss().accept(r.section()); });
        callbacks.put(UiNavigationRegistry.Module.EMV, (r,c) -> { view.loadEmv().run(); view.emv().accept(r.section()); view.header().accept("EMV Operations"); view.subtitle().accept("Session keys, ARQC/ARPC, and Track 2 data"); });
        callbacks.put(UiNavigationRegistry.Module.CLIPBOARD_SHELF, (r,c) -> { if(c instanceof ClipboardShelfController x) x.refresh(); });
        callbacks.put(UiNavigationRegistry.Module.HISTORY, (r,c) -> {
            if(r.variant()==UiNavigationRegistry.Variant.HISTORY_EXPORT) {
                if(c instanceof HistoryController x) x.focusExportActions();
                view.status().accept("Choose Export Visible JSON or Export JSON Record in Recent Operations.");
            }
        });
        callbacks.put(UiNavigationRegistry.Module.CIPHER, (r,c) -> { view.loadCipher().run(); view.cipher().accept(r.section()); });
        callbacks.put(UiNavigationRegistry.Module.AUTHENTICATION, (r,c) -> { view.loadAuthentication().run(); view.authentication().accept(r.section()); });
        callbacks.put(UiNavigationRegistry.Module.PAYMENTS, (r,c) -> { view.loadPayments().run(); view.payments().accept(r.section()); });
        callbacks.put(UiNavigationRegistry.Module.SAVED_SESSIONS, (r,c) -> { view.savedSessions().run(); view.header().accept("Saved Sessions"); view.subtitle().accept("Load or manage your saved workspaces"); });
        callbacks.put(UiNavigationRegistry.Module.PROCESS_DESIGNER, (r,c) -> {
            view.enterProcessDesigner().run();
            if(view.processDesigner().get() != null && view.processDesigner().get().root() instanceof TitledPane pane) pane.setExpanded(true);
            view.header().accept("Process Designer");
            view.subtitle().accept("Visual workflow builder and execution engine");
        });
        return new NavigationRouter(
                () -> java.util.Arrays.stream(view.hosts().get()).filter(java.util.Objects::nonNull).map(n -> (javafx.scene.Node)n).toList(),
                () -> java.util.stream.Stream.concat(view.content().get().getChildren().stream().filter(n -> n instanceof Label), java.util.stream.Stream.of(view.quickStart().get(), view.savedContainer().get())).filter(java.util.Objects::nonNull).toList(),
                view.host(),
                view.controller(),
                callbacks,
                view.exitProcessDesigner(),
                view.itemSelected());
    }

}
