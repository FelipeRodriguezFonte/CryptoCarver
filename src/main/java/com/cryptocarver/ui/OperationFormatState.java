package com.cryptocarver.ui;

import javafx.beans.value.ChangeListener;
import javafx.scene.control.ComboBox;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps the shared format selectors compatible with legacy controllers while
 * giving every navigable operation its own remembered input/output pair.
 */
final class OperationFormatState {

    static final String DEFAULT_FORMAT = "Hexadecimal";
    private static final List<String> FORMATS = List.of(
            "Text (UTF-8)", "Hexadecimal", "Base64", "Binary", "Decimal");

    record Selection(String input, String output) { }

    private final Map<String, Selection> selections = new LinkedHashMap<>();
    private ComboBox<String> input;
    private ComboBox<String> output;
    private String activeOperation;
    private boolean restoring;

    void attach(ComboBox<String> input, ComboBox<String> output) {
        this.input = input;
        this.output = output;
        configure(input);
        configure(output);
        input.setValue(DEFAULT_FORMAT);
        output.setValue(DEFAULT_FORMAT);
        ChangeListener<String> listener = (ignored, oldValue, newValue) -> rememberActive();
        input.valueProperty().addListener(listener);
        output.valueProperty().addListener(listener);
    }

    void activate(String operation) {
        rememberActive();
        activeOperation = normalized(operation);
        Selection selection = selections.get(activeOperation);
        restore(selection == null ? new Selection(DEFAULT_FORMAT, DEFAULT_FORMAT) : selection);
    }

    Selection activeSelection() {
        return new Selection(valueOrDefault(input), valueOrDefault(output));
    }

    Selection selectionFor(String operation) {
        return selections.getOrDefault(normalized(operation), new Selection(DEFAULT_FORMAT, DEFAULT_FORMAT));
    }

    private void rememberActive() {
        if (restoring || activeOperation == null || activeOperation.isBlank()) return;
        selections.put(activeOperation, activeSelection());
    }

    private void restore(Selection selection) {
        if (input == null || output == null) return;
        restoring = true;
        try {
            input.setValue(supported(selection.input()) ? selection.input() : DEFAULT_FORMAT);
            output.setValue(supported(selection.output()) ? selection.output() : DEFAULT_FORMAT);
        } finally {
            restoring = false;
        }
    }

    private static void configure(ComboBox<String> combo) {
        if (combo != null) combo.getItems().setAll(FORMATS);
    }

    private static String valueOrDefault(ComboBox<String> combo) {
        return combo == null || !supported(combo.getValue()) ? DEFAULT_FORMAT : combo.getValue();
    }

    private static boolean supported(String value) {
        return value != null && FORMATS.contains(value);
    }

    private static String normalized(String operation) {
        if (operation == null || operation.isBlank()) return "Dashboard";
        return operation.startsWith("Hashing: ") ? "Hashing" : operation.trim();
    }
}
