package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.service.I18nService;
import java.util.function.Supplier;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

/** Paints async progress from current controls, snapshot details and the live host. */
final class ShellAsyncProgressCoordinator {
    record View(HBox box, ProgressIndicator spinner, ProgressBar bar, Label label, Button cancelButton) {}

    private final I18nService i18n;
    private final Supplier<StatusReporter> reporter;

    ShellAsyncProgressCoordinator(I18nService i18n, Supplier<StatusReporter> reporter) {
        this.i18n = i18n;
        this.reporter = reporter;
    }

    private String progressTitle(String operationName) {
        return AppSettings.getInstance().getSecretVisibilityProfile() == com.cryptocarver.model.SecretVisibilityProfile.FULL_LAB
                && operationName != null && !operationName.isBlank() ? operationName : i18n.text("progress.operation");
    }

    void show(View view, String operationName) {
        if (view.box() != null) {
            if (view.label() != null) {
                String title = progressTitle(operationName);
                view.label().setText(title + "…");
                view.label().setAccessibleText(title);
            }
            if (view.spinner() != null) {
                view.spinner().setProgress(-1);
                view.spinner().setAccessibleText("Working: " + progressTitle(operationName));
                view.spinner().setVisible(true);
                view.spinner().setManaged(true);
            }
            if (view.bar() != null) {
                view.bar().setProgress(-1);
                view.bar().setAccessibleText(null);
                view.bar().setVisible(false);
                view.bar().setManaged(false);
            }
            if (view.cancelButton() != null) {
                view.cancelButton().setDisable(false);
            }
            view.box().setManaged(true);
            view.box().setVisible(true);
        }
    }

    void update(View view, OperationExecutor.ProgressDetails details) {
        if (view.box() == null || details == null) return;
        if (!view.box().isVisible()) {
            view.box().setManaged(true);
            view.box().setVisible(true);
        }
        if (view.label() != null) {
            String text = AppSettings.getInstance().getSecretVisibilityProfile() == com.cryptocarver.model.SecretVisibilityProfile.FULL_LAB
                    ? details.getFormattedText() : OperationExecutor.formatProgressText(progressTitle(null),
                            details.getBytesProcessed(), details.getTotalBytes(), details.getElapsedTimeMs());
            view.label().setText(text);
            view.label().setAccessibleText(text);
        }

        if (view.spinner() != null) view.spinner().setAccessibleText("Working: " + progressTitle(details.getOperationName()));
        if (details.getTotalBytes() > 0) {
            double ratio = Math.min(1.0, (double) details.getBytesProcessed() / details.getTotalBytes());
            if (view.bar() != null) {
                view.bar().setProgress(ratio);
                view.bar().setAccessibleText(String.format(java.util.Locale.US, "Progress: %d%%", Math.round(ratio * 100)));
                view.bar().setVisible(true);
                view.bar().setManaged(true);
            }
            if (view.spinner() != null) {
                view.spinner().setVisible(false);
                view.spinner().setManaged(false);
            }
        } else {
            if (view.spinner() != null) {
                view.spinner().setProgress(-1);
                view.spinner().setAccessibleText("Working: " + progressTitle(details.getOperationName()));
                view.spinner().setVisible(true);
                view.spinner().setManaged(true);
            }
            if (view.bar() != null) {
                view.bar().setVisible(false);
                view.bar().setManaged(false);
            }
        }
    }

    void hide(View view) {
        if (view.box() != null) {
            view.box().setVisible(false);
            view.box().setManaged(false);
        }
    }

    void cancel(View view) {
        OperationExecutor operationExecutor = reporter.get().getOperationExecutor();
        boolean cancelled = operationExecutor.cancelCurrentOperation();
        if (cancelled) {
            if (view.label() != null) {
                view.label().setText(i18n.text("progress.cancelling"));
            }
        } else if (operationExecutor.isInCommitPhase()) {
            if (view.label() != null) {
                view.label().setText(i18n.text("progress.finishing"));
            }
            if (view.cancelButton() != null) {
                view.cancelButton().setDisable(true);
            }
        } else {
            hide(view);
        }
    }

}
