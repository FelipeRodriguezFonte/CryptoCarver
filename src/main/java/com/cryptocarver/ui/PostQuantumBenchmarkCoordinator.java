package com.cryptocarver.ui;

import com.cryptocarver.crypto.pqc.PQCBenchmark;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;

import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Coordinates PQC benchmark task execution and its view lifecycle. */
final class PostQuantumBenchmarkCoordinator {
    record View(ComboBox<String> algorithm,
                Button button,
                ProgressIndicator progress,
                TextArea result) { }

    private final View view;
    private final Supplier<StatusReporter> reporter;

    PostQuantumBenchmarkCoordinator(View view, Supplier<StatusReporter> reporter) {
        this.view = view;
        this.reporter = reporter;
    }

    private StatusReporter reporter() { return reporter.get(); }

    private String t(String key, Object... args) {
        return com.cryptocarver.service.I18nService.getInstance().text(key, args);
    }

    void handlePQCBenchmark() {
        StatusReporter statusReporter = reporter();
        String algo = view.algorithm().getValue();
        if (algo == null) {
            if (statusReporter != null) statusReporter.showError("Benchmark Error", "Select an algorithm to benchmark");
            return;
        }

        if (view.progress() != null) view.progress().setVisible(true);
        if (view.result() != null) view.result().setText(t("module.pqc.benchmarking", algo));

        Callable<String> task = () -> {
            PQCBenchmark bench = new PQCBenchmark(algo, 1000);
            bench.run();
            return bench.getPartialResult();
        };

        Consumer<String> onSuccess = resultText -> {
            if (view.result() != null) view.result().setText(resultText);
            if (view.progress() != null) view.progress().setVisible(false);
        };

        Consumer<Throwable> onFailure = err -> {
            if (view.result() != null) view.result().setText(t("module.pqc.benchmarkFailed", err != null ? err.getMessage() : t("error.unknown")));
            if (view.progress() != null) view.progress().setVisible(false);
            if (statusReporter != null) statusReporter.showError("Benchmark Error", err != null ? err.getMessage() : "Unknown error");
        };

        Runnable onCancelled = () -> {
            if (view.result() != null) view.result().setText(t("module.pqc.benchmarkCancelled"));
            if (view.progress() != null) view.progress().setVisible(false);
        };

        if (statusReporter != null && statusReporter.getOperationExecutor() != null) {
            statusReporter.getOperationExecutor().execute("PQC Benchmark (" + algo + ")", view.button(), task, onSuccess, onFailure, onCancelled);
        } else {
            try {
                String res = task.call();
                onSuccess.accept(res);
            } catch (Exception e) {
                onFailure.accept(e);
            }
        }
    }
}
