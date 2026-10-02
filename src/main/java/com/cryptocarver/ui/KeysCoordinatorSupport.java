package com.cryptocarver.ui;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Shared shell callbacks, resolved without storing a module controller. */
abstract class KeysCoordinatorSupport {
    private final Supplier<StatusReporter> reporter;
    private final BiConsumer<String, String> errors;
    private final Consumer<String> status;
    private final BiFunction<String, Object[], String> text;

    KeysCoordinatorSupport(Supplier<StatusReporter> reporter, BiConsumer<String, String> errors,
            Consumer<String> status, BiFunction<String, Object[], String> text) {
        this.reporter = reporter;
        this.errors = errors;
        this.status = status;
        this.text = text;
    }

    protected final DialogService dialogService = new DialogService();

    KeysCoordinatorSupport(Supplier<StatusReporter> reporter) {
        this(reporter,
                (title, message) -> {
                    StatusReporter current = reporter.get();
                    if (current != null) current.showError(title, message);
                },
                message -> {
                    StatusReporter current = reporter.get();
                    if (current != null) current.updateStatus(message);
                },
                (key, args) -> com.cryptocarver.service.I18nService.getInstance().text(key, args));
    }

    final void showError(UserFacingError error) {
        StatusReporter current = reporter();
        if (current != null) current.showError(error);
    }

    final void showError(Throwable cause, String title, String fieldKey) {
        StatusReporter current = reporter();
        if (current != null) current.showError(cause, title, fieldKey);
    }

    final void showInfo(String title, String message) {
        StatusReporter current = reporter();
        if (current != null) current.showInfo(title, message);
    }

    final StatusReporter reporter() { return reporter.get(); }
    final void showError(String title, String message) { errors.accept(title, message); }
    final void updateStatus(String message) { status.accept(message); }
    final String t(String key, Object... args) { return text.apply(key, args); }
}
