package com.cryptocarver.ui;

import com.cryptocarver.model.help.HelpContext;
import com.cryptocarver.model.help.HelpContextSource;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.ComboBox;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Explicit lifecycle adapter for public selector controls in a deferred module.
 * Callers bind only enumerated ComboBox selections; text fields are not accepted.
 */
public final class HelpContextAdapter implements AutoCloseable {
    private record Binding(HelpContext.Selection selection, ComboBox<String> control,
                           Function<String, String> codeMapper,
                           ChangeListener<String> listener) { }
    private record FlagBinding(HelpContext.Selection selection, CheckBox control,
                               ChangeListener<Boolean> listener) { }
    private record SizeBinding(HelpContext.Selection selection, TextField control,
                               ToIntFunction<String> sizeMapper, ChangeListener<String> listener) { }
    private record PresenceBinding(HelpContext.Selection selection, TextInputControl control,
                                   ChangeListener<String> listener) { }

    private final HelpContextSource source;
    private final List<Binding> bindings = new ArrayList<>();
    private final List<FlagBinding> flagBindings = new ArrayList<>();
    private final List<SizeBinding> sizeBindings = new ArrayList<>();
    private final List<PresenceBinding> presenceBindings = new ArrayList<>();
    private boolean active;
    private boolean closed;

    public HelpContextAdapter(HelpContextSource source) {
        this.source = Objects.requireNonNull(source);
    }

    public void bindSelection(HelpContext.Selection selection, ComboBox<String> selector) {
        bindSelection(selection, selector, HelpContextAdapter::canonicalCode);
    }

    public void bindSelection(HelpContext.Selection selection, ComboBox<String> selector,
                              Function<String, String> codeMapper) {
        if (closed) throw new IllegalStateException("Adapter is closed");
        Objects.requireNonNull(selection);
        Objects.requireNonNull(selector);
        Objects.requireNonNull(codeMapper);
        ChangeListener<String> listener = (observable, oldValue, newValue) -> publish(selection, newValue, codeMapper);
        bindings.add(new Binding(selection, selector, codeMapper, listener));
        if (active) {
            selector.valueProperty().addListener(listener);
            publish(selection, selector.getValue(), codeMapper);
        }
    }

    /** Binds only a boolean state; no free-form control text is published. */
    public void bindFlag(HelpContext.Selection selection, CheckBox control) {
        if (closed) throw new IllegalStateException("Adapter is closed");
        Objects.requireNonNull(selection); Objects.requireNonNull(control);
        ChangeListener<Boolean> listener = (observable, before, value) -> source.setSelection(selection, HelpContext.PublicSelection.flag(Boolean.TRUE.equals(value)));
        flagBindings.add(new FlagBinding(selection, control, listener));
        if (active) { control.selectedProperty().addListener(listener); publishFlag(selection, control); }
    }

    /** Publishes a bounded derived size only; the underlying free-form text is never retained. */
    public void bindDerivedSize(HelpContext.Selection selection, TextField control, ToIntFunction<String> sizeMapper) {
        if (closed) throw new IllegalStateException("Adapter is closed");
        Objects.requireNonNull(selection); Objects.requireNonNull(control); Objects.requireNonNull(sizeMapper);
        ChangeListener<String> listener = (observable, before, value) -> publishSize(selection, value, sizeMapper);
        sizeBindings.add(new SizeBinding(selection, control, sizeMapper, listener));
        if (active) { control.textProperty().addListener(listener); publishSize(selection, control.getText(), sizeMapper); }
    }

    /** Publishes only whether a control contains text, never its contents. */
    public void bindPresence(HelpContext.Selection selection, TextInputControl control) {
        if (closed) throw new IllegalStateException("Adapter is closed");
        Objects.requireNonNull(selection); Objects.requireNonNull(control);
        ChangeListener<String> listener = (observable, before, value) -> publishPresence(selection, value);
        presenceBindings.add(new PresenceBinding(selection, control, listener));
        if (active) { control.textProperty().addListener(listener); publishPresence(selection, control.getText()); }
    }

    /** Registers listeners once and seeds current public choices. */
    public void activate() {
        if (closed || active) return;
        active = true;
        for (Binding binding : bindings) {
            binding.control().valueProperty().addListener(binding.listener());
            publish(binding.selection(), binding.control().getValue(), binding.codeMapper());
        }
        for (FlagBinding binding : flagBindings) {
            binding.control().selectedProperty().addListener(binding.listener());
            publishFlag(binding.selection(), binding.control());
        }
        for (SizeBinding binding : sizeBindings) {
            binding.control().textProperty().addListener(binding.listener());
            publishSize(binding.selection(), binding.control().getText(), binding.sizeMapper());
        }
        for (PresenceBinding binding : presenceBindings) {
            binding.control().textProperty().addListener(binding.listener());
            publishPresence(binding.selection(), binding.control().getText());
        }
    }

    /** Releases every selector subscription when the owning module is hidden. */
    public void deactivate() {
        if (!active) return;
        for (Binding binding : bindings) binding.control().valueProperty().removeListener(binding.listener());
        for (FlagBinding binding : flagBindings) binding.control().selectedProperty().removeListener(binding.listener());
        for (SizeBinding binding : sizeBindings) binding.control().textProperty().removeListener(binding.listener());
        for (PresenceBinding binding : presenceBindings) binding.control().textProperty().removeListener(binding.listener());
        active = false;
    }

    public boolean isActive() { return active; }
    public int bindingCount() { return bindings.size(); }

    /** Re-publishes safe selector and derived values after the shell changes operation context. */
    public void refreshCurrentSelections() {
        if (closed || !active) return;
        for (Binding binding : bindings) publish(binding.selection(), binding.control().getValue(), binding.codeMapper());
        for (FlagBinding binding : flagBindings) publishFlag(binding.selection(), binding.control());
        for (SizeBinding binding : sizeBindings) publishSize(binding.selection(), binding.control().getText(), binding.sizeMapper());
        for (PresenceBinding binding : presenceBindings) publishPresence(binding.selection(), binding.control().getText());
    }

    @Override public void close() {
        if (closed) return;
        deactivate();
        bindings.clear(); flagBindings.clear(); sizeBindings.clear(); presenceBindings.clear();
        closed = true;
    }

    private void publish(HelpContext.Selection selection, String value, Function<String, String> mapper) {
        if (value == null || value.isBlank()) {
            source.setSelection(selection, null);
            return;
        }
        String code = mapper.apply(value);
        source.setSelection(selection, new HelpContext.PublicSelection(code));
    }

    private void publishFlag(HelpContext.Selection selection, CheckBox control) {
        source.setSelection(selection, HelpContext.PublicSelection.flag(control.isSelected()));
    }

    private void publishSize(HelpContext.Selection selection, String raw, ToIntFunction<String> mapper) {
        int size = mapper.applyAsInt(raw == null ? "" : raw);
        if (size < 0) source.setSelection(selection, null);
        else source.setSelection(selection, HelpContext.PublicSelection.byteSize(size));
    }

    private void publishPresence(HelpContext.Selection selection, String raw) {
        source.setSelection(selection, HelpContext.PublicSelection.flag(raw != null && !raw.isBlank()));
    }

    private static String canonicalCode(String value) {
        return value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_.:-]+", "_");
    }
}
