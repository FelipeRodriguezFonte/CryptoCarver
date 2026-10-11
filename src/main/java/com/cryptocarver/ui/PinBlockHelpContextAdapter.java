package com.cryptocarver.ui;

import com.cryptocarver.crypto.PinBlockFormat;
import com.cryptocarver.model.help.HelpContext;
import com.cryptocarver.model.help.HelpContextSource;
import com.cryptocarver.model.help.OperationHelpRegistry;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextInputControl;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Publishes only allowlisted PIN format/padding codes and key-presence flags. */
final class PinBlockHelpContextAdapter implements AutoCloseable {
    private record Selector(HelpContext.Selection id, ComboBox<String> control, List<String> allowed,
            ChangeListener<String> listener) { }
    private record Presence(HelpContext.Selection id, TextInputControl control, ChangeListener<String> listener) { }

    private final HelpContextSource source;
    private final List<Selector> selectors = new ArrayList<>();
    private final List<Presence> presences = new ArrayList<>();
    private boolean active;
    private boolean closed;
    private String activeOperation = "";

    PinBlockHelpContextAdapter(HelpContextSource source, ComboBox<String> encodeFormat,
            ComboBox<String> decodeFormat, ComboBox<String> padding, ComboBox<String> encryptedFormat,
            TextInputControl encryptKey, TextInputControl decryptKey) {
        this.source = Objects.requireNonNull(source);
        bind(HelpContext.Selection.CLEAR_PIN_ENCODE_FORMAT, encodeFormat, PinBlockFormat.displayNames());
        bind(HelpContext.Selection.CLEAR_PIN_DECODE_FORMAT, decodeFormat, PinBlockFormat.displayNames());
        bind(HelpContext.Selection.CLEAR_PIN_PADDING, padding, com.cryptocarver.crypto.PinBlockPadding.OPTIONS);
        bind(HelpContext.Selection.ENCRYPTED_PIN_FORMAT, encryptedFormat, PinBlockFormat.displayNames());
        bindPresence(HelpContext.Selection.ENCRYPT_PIN_KEY_PRESENT, encryptKey);
        bindPresence(HelpContext.Selection.DECRYPT_PIN_KEY_PRESENT, decryptKey);
    }

    private void bind(HelpContext.Selection id, ComboBox<String> control, List<String> codes) {
        if (control == null) return;
        List<String> allowed = List.copyOf(codes);
        ChangeListener<String> listener = (obs, oldValue, value) -> publish(id, value, allowed);
        selectors.add(new Selector(id, control, allowed, listener));
    }

    private void bindPresence(HelpContext.Selection id, TextInputControl control) {
        if (control == null) return;
        ChangeListener<String> listener = (obs, oldValue, value) -> {
            if (relevant(id)) source.setSelection(id, HelpContext.PublicSelection.flag(value != null && !value.isBlank()));
        };
        presences.add(new Presence(id, control, listener));
    }

    void activate(String operationId) {
        if (closed) return;
        activeOperation = operationId == null ? "" : operationId;
        if (!active) {
            active = true;
            selectors.forEach(item -> item.control().valueProperty().addListener(item.listener()));
            presences.forEach(item -> item.control().textProperty().addListener(item.listener()));
        }
        publishCurrentSelections();
    }

    private boolean relevant(HelpContext.Selection id) {
        return switch (activeOperation) {
            case "op_pay_clear_pin" -> id == HelpContext.Selection.CLEAR_PIN_ENCODE_FORMAT
                    || id == HelpContext.Selection.CLEAR_PIN_DECODE_FORMAT || id == HelpContext.Selection.CLEAR_PIN_PADDING;
            case "op_pay_enc_pin" -> id == HelpContext.Selection.ENCRYPTED_PIN_FORMAT
                    || id == HelpContext.Selection.ENCRYPT_PIN_KEY_PRESENT || id == HelpContext.Selection.DECRYPT_PIN_KEY_PRESENT;
            default -> false;
        };
    }

    void deactivate() {
        if (!active) return;
        selectors.forEach(item -> item.control().valueProperty().removeListener(item.listener()));
        presences.forEach(item -> item.control().textProperty().removeListener(item.listener()));
        active = false;
        activeOperation = "";
    }

    void publishCurrentSelections() {
        if (!active || closed) return;
        selectors.stream().filter(item -> relevant(item.id()))
                .forEach(item -> publish(item.id(), item.control().getValue(), item.allowed()));
        presences.stream().filter(item -> relevant(item.id()))
                .forEach(item -> item.listener().changed(null, null, item.control().getText()));
    }

    private void publish(HelpContext.Selection id, String value, List<String> allowed) {
        if (!relevant(id)) return;
        String canonical = allowed.stream().filter(code -> code.equals(value)).findFirst()
                .map(OperationHelpRegistry::slug).orElse(null);
        source.setSelection(id, canonical == null ? null : HelpContext.PublicSelection.of(canonical));
    }

    boolean isActive() { return active; }
    @Override public void close() { deactivate(); selectors.clear(); presences.clear(); closed = true; }
}
