package com.cryptocarver.ui;

import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/** Presents an early process-validation failure without changing execution state. */
final class ProcessPreflightPresenter {
    record View(Supplier<TableView<ProcessExecutionRow>> executionStatusTable,
                Supplier<TextArea> executionOutputArea,
                Function<String, String> failedMessage) {
        View {
            Objects.requireNonNull(executionStatusTable);
            Objects.requireNonNull(executionOutputArea);
            Objects.requireNonNull(failedMessage);
        }
    }

    void show(View view, String message) {
        TableView<ProcessExecutionRow> executionStatusTable = view.executionStatusTable().get();
        if (executionStatusTable != null) {
            executionStatusTable.getItems().setAll(new ProcessExecutionRow("validation", "-", "Validation",
                    "PRE-FLIGHT", "-", "-", "ERROR", "0 ms"));
        }
        view.executionOutputArea().get().setText(view.failedMessage().apply(message));
    }
}
