package com.cryptocarver.ui;

import com.cryptocarver.service.I18nService;
import com.cryptocarver.model.help.HelpContextSource;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import java.util.function.Consumer;

/** Owns the one compact contextual-help window for a shell and its lifecycle. */
public final class ContextualHelpWindowCoordinator {
    private final I18nService i18n;
    private final HelpContextSource contextSource;
    private Stage window;
    private ContextualHelpCoordinator help;
    private String navigationPath = "";
    private Consumer<String> actionSelectionListener = ignored -> { };

    public ContextualHelpWindowCoordinator(I18nService i18n, HelpContextSource contextSource) {
        this.i18n = i18n;
        this.contextSource = contextSource;
    }

    public void show(Window owner, String route) {
        navigationPath = route == null ? "" : route;
        helpContextOperation(route);
        showCurrent(owner);
    }

    public void showCurrent(Window owner) {
        if (window != null && window.isShowing()) {
            updateTitle();
            window.toFront();
            window.requestFocus();
            return;
        }
        VBox content = new VBox(10);
        help = new ContextualHelpCoordinator(content, i18n, contextSource);
        help.setActionSelectionListener(actionSelectionListener);
        help.refresh();
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        Stage created = new Stage();
        if (owner != null) created.initOwner(owner);
        created.setScene(new Scene(scroll, 420, 620));
        created.setOnHidden(event -> {
            if (window == created) {
                window = null;
                ContextualHelpCoordinator hiddenHelp = help;
                help = null;
                if (hiddenHelp != null) hiddenHelp.close();
            }
        });
        window = created;
        updateTitle();
        created.show();
        created.requestFocus();
    }

    public void updateOperation(String route) {
        navigationPath = route == null ? "" : route;
        helpContextOperation(navigationPath);
        if (window == null || !window.isShowing() || help == null) return;
        updateTitle();
    }

    public void showAction(String actionId) {
        contextSource.setAction(actionId);
    }

    public void setActionSelectionListener(Consumer<String> listener) {
        actionSelectionListener = listener == null ? ignored -> { } : listener;
        if (help != null) help.setActionSelectionListener(actionSelectionListener);
    }

    /** Called by the shell's existing localization refresh; no extra listener is retained. */
    public void refreshLocalization() {
        if (window == null || !window.isShowing() || help == null) return;
        help.refresh();
        updateTitle();
    }

    public void close() {
        Stage closing = window;
        window = null;
        ContextualHelpCoordinator closingHelp = help;
        help = null;
        if (closingHelp != null) closingHelp.close();
        if (closing != null) {
            closing.setOnHidden(null);
            closing.close();
            closing.setScene(null);
        }
    }

    public boolean isShowing() { return window != null && window.isShowing(); }
    public Stage windowForTesting() { return window; }

    private void updateTitle() {
        if (window == null || help == null) return;
        window.setTitle(i18n.text("help.tab.help") + " — " + help.operationTitle());
    }

    private void helpContextOperation(String route) {
        if (route == null || route.isBlank()) return;
        helpContextSourceOperation(route);
    }

    private void helpContextSourceOperation(String route) {
        if (com.cryptocarver.model.OperationRegistry.getInstance().getByNavigationPath(route).isPresent()) {
            contextSource.setOperation(com.cryptocarver.model.OperationRegistry.getInstance().getByNavigationPath(route).orElseThrow().getId());
        } else if (com.cryptocarver.model.OperationRegistry.getInstance().getById(route).isPresent()) {
            contextSource.setOperation(route);
        }
    }
}
