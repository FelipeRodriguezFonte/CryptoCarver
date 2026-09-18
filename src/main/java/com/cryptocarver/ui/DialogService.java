package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.ThemePreference;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Single entry point for modal dialogs used by the modern UI.
 *
 * <p>The service deliberately keeps the dialog construction in one place so
 * future controller migrations do not accidentally reintroduce un-themed,
 * untranslated alerts. File chooser directories are remembered per logical
 * file type for the lifetime of the application.</p>
 */
public final class DialogService {
    private static final String BASE_STYLESHEET = "/css/styles.css";
    private static final String LIGHT_STYLESHEET = "/css/theme-light.css";
    private static final String DARK_STYLESHEET = "/css/theme-dark.css";

    private final I18nService i18n;
    private final Map<String, File> lastDirectories = new LinkedHashMap<>();

    public DialogService() {
        this(I18nService.getInstance());
    }

    /** Injectable constructor for UI tests and controller migration. */
    public DialogService(I18nService i18n) {
        this.i18n = Objects.requireNonNull(i18n, "i18n");
    }

    /** Shows a destructive confirmation with a concrete verb and Cancel focused first. */
    public boolean confirmDestructive(String title, String consequence, String confirmLabel) {
        return confirmDestructive(null, title, consequence, confirmLabel);
    }

    /** Shows a destructive confirmation owned by the supplied window. */
    public boolean confirmDestructive(Window owner, String title, String consequence, String confirmLabel) {
        String safeConfirm = nonBlank(confirmLabel, i18n.text("dialog.confirm"));
        ButtonType confirm = new ButtonType(safeConfirm, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(i18n.text("dialog.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, nonBlank(consequence, ""), cancel, confirm);
        configure(alert, owner, title, null);
        focusButtonWhenShown(alert, cancel);
        return alert.showAndWait().filter(confirm::equals).isPresent();
    }

    public void info(String title, String detail) {
        info(null, title, detail);
    }

    public void info(Window owner, String title, String detail) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, nonBlank(detail, ""), ButtonType.OK);
        configure(alert, owner, title, null);
        alert.showAndWait();
    }

    public void warning(Window owner, String title, String detail) {
        Alert alert = new Alert(Alert.AlertType.WARNING, nonBlank(detail, ""), ButtonType.OK);
        configure(alert, owner, title, null);
        alert.showAndWait();
    }

    public void error(Window owner, String title, String detail) {
        Alert alert = new Alert(Alert.AlertType.ERROR, nonBlank(detail, ""), ButtonType.OK);
        configure(alert, owner, title, null);
        alert.showAndWait();
    }

    public void warning(String title, String detail) { warning(null, title, detail); }

    public void error(String title, String detail) { error(null, title, detail); }

    /**
     * Opens an input chooser and remembers its last directory under {@code type}.
     * A null return means that the user cancelled the chooser.
     */
    public File pickFile(String type, String title, FileChooser.ExtensionFilter... filters) {
        return pickFile(null, type, title, filters);
    }

    public File pickFile(Window owner, String type, String title, FileChooser.ExtensionFilter... filters) {
        FileChooser chooser = createFileChooser(type, title, filters);
        File selected = chooser.showOpenDialog(owner);
        remember(nonBlank(type, "default"), selected);
        return selected;
    }

    /** Factory kept package-private so chooser setup can be tested without opening native UI. */
    FileChooser createFileChooser(String type, String title, FileChooser.ExtensionFilter... filters) {
        String key = nonBlank(type, "default");
        FileChooser chooser = new FileChooser();
        chooser.setTitle(nonBlank(title, "CryptoCarver"));
        File previous = lastDirectories.get(key);
        if (previous != null && previous.isDirectory()) {
            chooser.setInitialDirectory(previous);
        }
        if (filters != null) {
            Arrays.stream(filters).filter(Objects::nonNull).forEach(filter -> chooser.getExtensionFilters().add(filter));
        }
        return chooser;
    }

    private void remember(String key, File selected) {
        if (selected != null) {
            File parent = selected.isDirectory() ? selected : selected.getParentFile();
            if (parent != null && parent.isDirectory()) {
                lastDirectories.put(key, parent);
            }
        }
    }

    private void configure(Alert alert, Window owner, String title, String header) {
        if (owner != null) alert.initOwner(owner);
        alert.setTitle(nonBlank(title, i18n.text("dialog.confirm")));
        alert.setHeaderText(header);
        alert.getDialogPane().getStyleClass().add("cc-dialog-pane");
        addThemeStylesheets(alert.getDialogPane());
    }

    private void addThemeStylesheets(javafx.scene.control.DialogPane pane) {
        URL base = DialogService.class.getResource(BASE_STYLESHEET);
        URL theme = DialogService.class.getResource(themeStylesheet());
        if (base != null) pane.getStylesheets().add(base.toExternalForm());
        if (theme != null) pane.getStylesheets().add(theme.toExternalForm());
    }

    private String themeStylesheet() {
        ThemePreference preference = AppSettings.getInstance().getThemePreference();
        return SystemAppearance.resolve(preference) == ThemePreference.DARK
                ? DARK_STYLESHEET : LIGHT_STYLESHEET;
    }

    private static void focusButtonWhenShown(Alert alert, ButtonType buttonType) {
        alert.setOnShown(event -> {
            Node button = alert.getDialogPane().lookupButton(buttonType);
            if (button != null) {
                if (Platform.isFxApplicationThread()) button.requestFocus();
                else Platform.runLater(button::requestFocus);
            }
        });
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

}
