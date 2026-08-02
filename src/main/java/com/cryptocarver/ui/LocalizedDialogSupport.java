package com.cryptocarver.ui;

import com.cryptocarver.service.I18nService;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;
import javafx.stage.FileChooser;

/** Shared localized wrapper for legacy JavaFX dialogs. Runtime details are caller-owned and unchanged. */
public final class LocalizedDialogSupport {
    private LocalizedDialogSupport() { }

    public static Alert alert(Alert.AlertType type, String titleKey, String headerKey,
                              String technicalDetail, ButtonType... buttons) {
        I18nService i18n = I18nService.getInstance();
        Alert alert = new Alert(type, technicalDetail, buttons == null || buttons.length == 0
                ? new ButtonType[] {ButtonType.OK} : buttons);
        alert.setTitle(i18n.text(titleKey));
        alert.setHeaderText(headerKey == null || headerKey.isBlank() ? null : i18n.text(headerKey));
        return alert;
    }

    public static TextInputDialog textInput(String titleKey, String headerKey, String promptKey,
                                             String defaultValue) {
        I18nService i18n = I18nService.getInstance();
        TextInputDialog dialog = new TextInputDialog(defaultValue);
        dialog.setTitle(i18n.text(titleKey));
        dialog.setHeaderText(i18n.text(headerKey));
        dialog.setContentText(i18n.text(promptKey));
        return dialog;
    }

    public static FileChooser fileChooser(String titleKey, String filterKey,
                                           String descriptionFallback, String... extensions) {
        I18nService i18n = I18nService.getInstance();
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n.text(titleKey));
        String description = filterKey == null || filterKey.isBlank()
                ? descriptionFallback : i18n.text(filterKey);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(description, extensions));
        return chooser;
    }
}
