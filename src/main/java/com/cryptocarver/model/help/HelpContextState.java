package com.cryptocarver.model.help;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Single source of truth for current help navigation and public selector state. */
public final class HelpContextState implements HelpContextSource {
    private final CopyOnWriteArrayList<Consumer<HelpContext>> listeners = new CopyOnWriteArrayList<>();
    private volatile HelpContext context = new HelpContext("unknown", "", "", Locale.ENGLISH, "", java.util.Map.of());

    @Override public HelpContext current() { return context; }
    @Override public void addListener(Consumer<HelpContext> listener) { if (listener != null) listeners.addIfAbsent(listener); }
    @Override public void removeListener(Consumer<HelpContext> listener) { if (listener != null) listeners.remove(listener); }

    @Override public void setOperation(String operationId) { update(context.withOperation(operationId)); }
    @Override public void setAction(String actionId) { update(context.withAction(actionId)); }
    @Override public void setLocale(Locale locale) { update(context.withLocale(locale)); }
    @Override public void setField(String fieldId) { update(context.withField(fieldId)); }
    @Override public void setSelection(HelpContext.Selection selection, HelpContext.PublicSelection value) {
        update(context.withSelection(selection, value));
    }
    @Override public void replace(HelpContext value) { update(Objects.requireNonNull(value)); }

    private void update(HelpContext next) {
        if (context.equals(next)) return;
        context = next;
        for (Consumer<HelpContext> listener : listeners) listener.accept(next);
    }
}
