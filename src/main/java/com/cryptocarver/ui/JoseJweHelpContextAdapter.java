package com.cryptocarver.ui;

import com.cryptocarver.crypto.JoseKeyMaterial;
import com.cryptocarver.crypto.JweComposer;
import com.cryptocarver.model.help.HelpContext;
import com.cryptocarver.model.help.HelpContextSource;
import com.cryptocarver.model.help.OperationHelpRegistry;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.ComboBox;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextInputControl;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Publishes only allowlisted JWE selectors and derived yes/no material presence. */
final class JoseJweHelpContextAdapter implements AutoCloseable {
    private record Selector(HelpContext.Selection selection, ComboBox<String> control,
            List<String> allowed, ChangeListener<String> listener) { }
    private record Presence(HelpContext.Selection selection, TextInputControl control,
            ChangeListener<String> listener) { }
    private record Toggle(HelpContext.Selection selection, CheckBox control,
            ChangeListener<Boolean> listener) { }

    private final HelpContextSource source;
    private final List<Selector> selectors = new ArrayList<>();
    private final List<Presence> presences = new ArrayList<>();
    private final List<Toggle> toggles = new ArrayList<>();
    private boolean active;
    private boolean closed;

    JoseJweHelpContextAdapter(HelpContextSource source,
            ComboBox<String> keyAlgorithm, ComboBox<String> contentEncryption,
            ComboBox<String> serialization, ComboBox<String> keyFormat,
            ComboBox<String> decryptKeyFormat, CheckBox compression, TextInputControl aad,
            TextInputControl publicMaterial, TextInputControl privateMaterial, TextInputControl payload) {
        this.source = Objects.requireNonNull(source);
        bind(HelpContext.Selection.JWE_KEY_ALGORITHM, keyAlgorithm, JweComposer.KEY_ALGORITHMS);
        bind(HelpContext.Selection.JWE_CONTENT_ENCRYPTION, contentEncryption, JweComposer.CONTENT_ALGORITHMS);
        bind(HelpContext.Selection.JWE_SERIALIZATION,
                serialization, java.util.Arrays.stream(JweComposer.Serialization.values())
                        .map(JweComposer.Serialization::label).toList());
        List<String> secretFormats = java.util.Arrays.stream(JoseKeyMaterial.SecretEncoding.values())
                .map(JoseKeyMaterial.SecretEncoding::label).toList();
        bind(HelpContext.Selection.JWE_KEY_FORMAT, keyFormat, secretFormats);
        bind(HelpContext.Selection.JWE_DECRYPT_KEY_FORMAT, decryptKeyFormat, secretFormats);
        bindToggle(HelpContext.Selection.JWE_COMPRESSION, compression);
        bindPresence(HelpContext.Selection.JWE_AAD_PRESENT, aad);
        bindPresence(HelpContext.Selection.JWE_PUBLIC_MATERIAL_PRESENT, publicMaterial);
        bindPresence(HelpContext.Selection.JWE_PRIVATE_MATERIAL_PRESENT, privateMaterial);
        bindPresence(HelpContext.Selection.JWE_PAYLOAD_PRESENT, payload);
    }

    private void bind(HelpContext.Selection selection, ComboBox<String> control, List<String> allowed) {
        if (control == null) return;
        List<String> codes = List.copyOf(allowed);
        ChangeListener<String> listener = (observable, oldValue, newValue) -> publish(selection, newValue, codes);
        selectors.add(new Selector(selection, control, codes, listener));
    }

    private void bindPresence(HelpContext.Selection selection, TextInputControl control) {
        if (control == null) return;
        ChangeListener<String> listener = (observable, oldValue, newValue) -> publishPresence(selection, newValue);
        presences.add(new Presence(selection, control, listener));
    }

    private void bindToggle(HelpContext.Selection selection, CheckBox control) {
        if (control == null) return;
        ChangeListener<Boolean> listener = (observable, oldValue, newValue) -> publishToggle(selection, newValue);
        toggles.add(new Toggle(selection, control, listener));
    }

    void activate() {
        if (closed || active) return;
        active = true;
        for (Selector selector : selectors) selector.control().valueProperty().addListener(selector.listener());
        for (Presence presence : presences) presence.control().textProperty().addListener(presence.listener());
        for (Toggle toggle : toggles) toggle.control().selectedProperty().addListener(toggle.listener());
        publishCurrentSelections();
    }

    void deactivate() {
        if (!active) return;
        for (Selector selector : selectors) selector.control().valueProperty().removeListener(selector.listener());
        for (Presence presence : presences) presence.control().textProperty().removeListener(presence.listener());
        for (Toggle toggle : toggles) toggle.control().selectedProperty().removeListener(toggle.listener());
        active = false;
    }

    void publishCurrentSelections() {
        if (!active || closed) return;
        for (Selector selector : selectors) publish(selector.selection(), selector.control().getValue(), selector.allowed());
        for (Presence presence : presences) publishPresence(presence.selection(), presence.control().getText());
        for (Toggle toggle : toggles) publishToggle(toggle.selection(), toggle.control().isSelected());
    }

    int selectorBindingCount() { return selectors.size(); }
    int presenceBindingCount() { return presences.size(); }
    boolean isActive() { return active; }

    @Override public void close() {
        if (closed) return;
        deactivate();
        selectors.clear();
        presences.clear();
        toggles.clear();
        closed = true;
    }

    private void publish(HelpContext.Selection selection, String value, List<String> allowed) {
        String canonical = allowed.stream().filter(code -> code.equals(value)).findFirst()
                .map(OperationHelpRegistry::slug).orElse(null);
        source.setSelection(selection, canonical == null ? null : new HelpContext.PublicSelection(canonical));
    }

    private void publishPresence(HelpContext.Selection selection, String value) {
        source.setSelection(selection, HelpContext.PublicSelection.flag(value != null && !value.isBlank()));
    }

    private void publishToggle(HelpContext.Selection selection, boolean value) {
        source.setSelection(selection, HelpContext.PublicSelection.of(value ? "enabled" : "disabled"));
    }
}
