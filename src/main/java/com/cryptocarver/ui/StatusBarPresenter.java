package com.cryptocarver.ui;

import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.service.I18nService;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import javafx.css.PseudoClass;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

/**
 * Owns the small, persistent status summary shown at the bottom of the shell.
 * Keeping the formatting here prevents operation controllers from having to know
 * about timestamps, status severity or the active privacy profile.
 */
final class StatusBarPresenter {
    private static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");
    private final Label messageLabel;
    private final Button visibilityButton;
    private final Label languageLabel;
    private final I18nService i18n;

    StatusBarPresenter(Label messageLabel, Button visibilityButton, Label languageLabel, I18nService i18n) {
        this.messageLabel = messageLabel;
        this.visibilityButton = visibilityButton;
        this.languageLabel = languageLabel;
        this.i18n = i18n;
    }

    void showStatus(String message) {
        if (messageLabel == null) return;
        boolean error = isError(message);
        String icon = error ? "×" : "✓";
        String safeMessage = message == null || message.isBlank() ? i18n.text("status.ready") : message;
        String time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                .withLocale(i18n.getLocale()).format(LocalTime.now());
        messageLabel.setText(i18n.text("status.message", icon, safeMessage, time));
        messageLabel.pseudoClassStateChanged(ERROR, error);
        messageLabel.setAccessibleText(safeMessage + ", " + time);
    }

    void refreshContext(SecretVisibilityProfile profile) {
        if (visibilityButton != null) {
            SecretVisibilityProfile safeProfile = profile == null ? SecretVisibilityProfile.FULL_LAB : profile;
            visibilityButton.setText(i18n.text("status.visibility." + safeProfile.name().toLowerCase(java.util.Locale.ROOT)));
            visibilityButton.setTooltip(new javafx.scene.control.Tooltip(i18n.text("status.visibility.tooltip")));
            visibilityButton.setAccessibleText(i18n.text("status.visibility.accessible", visibilityButton.getText()));
        }
        if (languageLabel != null) {
            String language = i18n.text("app.language." + i18n.getPreference().name().toLowerCase(java.util.Locale.ROOT));
            languageLabel.setText(i18n.text("status.language", language));
        }
    }

    private static boolean isError(String message) {
        if (message == null) return false;
        String normalized = message.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("error") || normalized.contains("failed") || normalized.contains("fall")
                || normalized.contains("blocked") || normalized.contains("invalid") || normalized.contains("no ");
    }
}
