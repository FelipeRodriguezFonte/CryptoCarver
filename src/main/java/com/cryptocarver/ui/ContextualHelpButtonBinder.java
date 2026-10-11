package com.cryptocarver.ui;

import com.cryptocarver.model.help.HelpContext;
import com.cryptocarver.model.help.HelpContextSource;
import com.cryptocarver.service.I18nService;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Makes contextual help follow the keyboard focus of the controls in a lazily loaded module.
 * With help mode on, focusing a bound control (or any descendant) only publishes its action and
 * field to the shared help state; {@code Shift+F1} on a bound control publishes it and opens the help.
 */
final class ContextualHelpButtonBinder implements AutoCloseable {
    /** Node property holding the stable ids ({@code contextHelp_<op>_<action>_<field|action>}) bound to a control. */
    static final String BINDING_IDS_KEY = "cryptocarver.contextualHelp.bindingIds";
    private static final String BINDINGS_KEY = "cryptocarver.contextualHelp.bindings";

    private static final class Binding {
        final ContextualHelpButtonBinder owner;
        final String routeId, contextId, fieldId;
        final Supplier<String> actionId;
        final UnaryOperator<HelpContext> refine;
        final String stableId;
        final Node target;
        final ChangeListener<Boolean> focusListener;
        final EventHandler<KeyEvent> keyHandler;
        String appliedText, appliedHelp, localizedFor;

        Binding(ContextualHelpButtonBinder owner, String routeId, String contextId, Supplier<String> actionId, String fieldId, Node target,
                UnaryOperator<HelpContext> refine, ChangeListener<Boolean> focusListener, EventHandler<KeyEvent> keyHandler) {
            this.owner = owner; this.routeId = routeId; this.contextId = contextId; this.actionId = actionId; this.fieldId = fieldId; this.refine = refine;
            this.stableId = "contextHelp_" + contextId + "_" + actionId.get() + "_"
                    + (fieldId.isBlank() ? "action" : fieldId).replaceAll("[^A-Za-z0-9_]+", "_");
            this.target = target; this.focusListener = focusListener; this.keyHandler = keyHandler;
        }
    }

    private static int followSuspended;

    private final List<Binding> bindings = new ArrayList<>();
    private final HelpContextSource state;
    private final Consumer<HelpContext> opener;
    private final Consumer<HelpContext> onPublished;
    private final Consumer<HelpContext> contextListener = ignored -> refreshLocalization();
    private boolean enabled;
    private String activeRoute = "";
    private boolean closed;

    ContextualHelpButtonBinder(HelpContextSource state, Consumer<HelpContext> opener) {
        this(state, opener, null);
    }

    /** {@code onPublished} runs after a bound control publishes its context (focus or Shift+F1), before the opener. */
    ContextualHelpButtonBinder(HelpContextSource state, Consumer<HelpContext> opener, Consumer<HelpContext> onPublished) {
        this.state = state;
        this.opener = opener == null ? ignored -> { } : opener;
        this.onPublished = onPublished == null ? ignored -> { } : onPublished;
        if (state != null) state.addListener(contextListener);
    }

    /** Runs a programmatic focus change (e.g. "go to field" from a diagnostic) without letting help follow it. */
    static void withoutFollowing(Runnable action) {
        followSuspended++;
        try { action.run(); } finally { followSuspended--; }
    }

    boolean bind(String routeId, String contextId, String actionId, String fieldId, Node target) {
        return bind(routeId, contextId, () -> actionId, fieldId, target);
    }

    boolean bind(String routeId, String contextId, Supplier<String> actionId, String fieldId, Node target) {
        return bind(routeId, contextId, actionId, fieldId, target, null);
    }

    /** {@code refine} may add per-control state (e.g. a selection) to the context before it is published. */
    boolean bind(String routeId, String contextId, String actionId, String fieldId, Node target,
                 UnaryOperator<HelpContext> refine) {
        return bind(routeId, contextId, () -> actionId, fieldId, target, refine);
    }

    boolean bind(String routeId, String contextId, Supplier<String> actionId, String fieldId, Node target,
                 UnaryOperator<HelpContext> refine) {
        if (closed || target == null) return false;
        String field = fieldId == null ? "" : fieldId;
        Binding[] holder = new Binding[1];
        ChangeListener<Boolean> focusListener = (obs, oldValue, focused) -> {
            if (Boolean.TRUE.equals(focused) && followSuspended == 0 && hasFocusOwnerWithin(target) && isWinner(holder[0], true)) publish(holder[0]);
        };
        EventHandler<KeyEvent> keyHandler = event -> {
            if (event.getCode() != KeyCode.F1 || !event.isShiftDown() || event.isControlDown()
                    || event.isAltDown() || event.isMetaDown()) return;
            if (closed || !holder[0].routeId.equals(activeRoute)) return;
            event.consume();
            if (!isWinner(holder[0], false)) return;
            HelpContext next = publish(holder[0]);
            if (next != null) opener.accept(next);
        };
        Binding binding = new Binding(this, routeId, contextId, actionId, field, target, refine, focusListener, keyHandler);
        holder[0] = binding;
        bindings.add(binding);
        target.focusWithinProperty().addListener(focusListener);
        target.addEventHandler(KeyEvent.KEY_PRESSED, keyHandler);
        @SuppressWarnings("unchecked")
        java.util.Set<String> ids = (java.util.Set<String>) target.getProperties()
                .computeIfAbsent(BINDING_IDS_KEY, key -> new java.util.LinkedHashSet<String>());
        ids.add(binding.stableId);
        @SuppressWarnings("unchecked")
        List<Binding> onTarget = (List<Binding>) target.getProperties()
                .computeIfAbsent(BINDINGS_KEY, key -> new ArrayList<Binding>());
        onTarget.add(binding);
        localize(binding);
        return true;
    }

    void setEnabled(boolean value) { enabled = value; }
    void setActiveRoute(String routeId) { activeRoute = routeId == null ? "" : routeId; }
    void refreshLocalization() { for (Binding binding : bindings) localize(binding); }

    /**
     * A control shared by several routes or questions answers with one context: the first live
     * binding in bind order, so duplicated focus listeners and key handlers stay idempotent.
     */
    private static boolean isWinner(Binding binding, boolean requireEnabled) {
        if (!(binding.target.getProperties().get(BINDINGS_KEY) instanceof List<?> all)) return false;
        for (Object candidate : all) {
            Binding other = (Binding) candidate;
            ContextualHelpButtonBinder owner = other.owner;
            if (!owner.closed && other.routeId.equals(owner.activeRoute) && (!requireEnabled || owner.enabled))
                return other == binding;
        }
        return false;
    }

    /**
     * Cells of combo boxes, lists and tables report a focused state on their own, which turns on
     * {@code focusWithin} of the whole control during layout; only a real scene focus owner counts.
     */
    private static boolean hasFocusOwnerWithin(Node target) {
        javafx.scene.Scene scene = target.getScene();
        if (scene == null) return false;
        for (Node node = scene.getFocusOwner(); node != null; node = node.getParent()) if (node == target) return true;
        return false;
    }

    private HelpContext publish(Binding binding) {
        if (state == null) return null;
        HelpContext current = state.current();
        HelpContext next = new HelpContext(binding.contextId, binding.actionId.get(), "", current.locale(),
                binding.fieldId, current.selections());
        if (binding.refine != null) next = binding.refine.apply(next);
        state.replace(next);
        onPublished.accept(next);
        return next;
    }

    /** Fills accessible text/help only where the control has none of its own (or still carries ours). */
    private void localize(Binding binding) {
        I18nService i18n = I18nService.getInstance();
        String operationId = binding.contextId, actionId = binding.actionId.get(), fieldId = binding.fieldId;
        // Every context change reaches every binding; only a new locale or dynamic action changes the texts.
        String localizedFor = i18n.getLocale() + "|" + actionId;
        if (localizedFor.equals(binding.localizedFor)) return;
        binding.localizedFor = localizedFor;
        String text, help;
        var action = com.cryptocarver.model.help.OperationHelpRegistry.getInstance().find(operationId)
                .flatMap(definition -> definition.actions().stream().filter(item -> item.id().equals(actionId)).findFirst())
                .orElse(null);
        if (action != null) {
            var field = fieldId.isBlank() ? null : action.fields().stream()
                    .filter(candidate -> candidate.id().equals(fieldId)).findFirst().orElse(null);
            text = i18n.text(field == null ? action.titleKey() : field.labelKey());
            help = i18n.text(field == null ? action.descriptionKey() : field.descriptionKey());
        } else {
            String actionPrefix = "help.action." + operationId + "." + actionId;
            text = i18n.text(actionPrefix + ".title");
            help = i18n.text(actionPrefix + ".description");
        }
        Node target = binding.target;
        if (isBlank(target.getAccessibleText()) || target.getAccessibleText().equals(binding.appliedText)) {
            target.setAccessibleText(text);
            binding.appliedText = text;
        }
        if (isBlank(target.getAccessibleHelp()) || target.getAccessibleHelp().equals(binding.appliedHelp)) {
            target.setAccessibleHelp(help);
            binding.appliedHelp = help;
        }
    }

    private static boolean isBlank(String value) { return value == null || value.isBlank(); }

    @Override public void close() {
        if (closed) return;
        if (state != null) state.removeListener(contextListener);
        for (Binding binding : bindings) {
            binding.target.focusWithinProperty().removeListener(binding.focusListener);
            binding.target.removeEventHandler(KeyEvent.KEY_PRESSED, binding.keyHandler);
            if (binding.target.getProperties().get(BINDINGS_KEY) instanceof List<?> onTarget) {
                onTarget.remove(binding);
                if (onTarget.isEmpty()) binding.target.getProperties().remove(BINDINGS_KEY);
            }
            if (binding.target.getProperties().get(BINDING_IDS_KEY) instanceof java.util.Set<?> ids) {
                ids.remove(binding.stableId);
                if (ids.isEmpty()) binding.target.getProperties().remove(BINDING_IDS_KEY);
            }
            if (binding.appliedText != null && binding.appliedText.equals(binding.target.getAccessibleText()))
                binding.target.setAccessibleText(null);
            if (binding.appliedHelp != null && binding.appliedHelp.equals(binding.target.getAccessibleHelp()))
                binding.target.setAccessibleHelp(null);
        }
        bindings.clear();
        closed = true;
    }
}
