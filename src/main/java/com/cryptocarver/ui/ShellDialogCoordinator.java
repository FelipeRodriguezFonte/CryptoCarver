package com.cryptocarver.ui;

import com.cryptocarver.model.DiagnosticsReportBuilder;
import com.cryptocarver.model.KeyboardShortcutEntry;
import com.cryptocarver.model.KeyboardShortcutRegistry;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/** Coordinates shell notices and the custom informational dialogs. */
final class ShellDialogCoordinator {
    private final DialogService dialogs;
    private final Supplier<Window> ownerProvider;

    ShellDialogCoordinator(DialogService dialogs, Supplier<Window> ownerProvider) {
        this.dialogs = dialogs;
        this.ownerProvider = ownerProvider;
    }

    void info(String title, String message) {
        if (Boolean.getBoolean("test.mode")) {
            System.out.println("SHOW_INFO: " + title + " - " + message);
            return;
        }
        dialogs.info(ownerProvider.get(), title, message);
    }

    void warning(String title, String message) {
        if (Boolean.getBoolean("test.mode")) {
            System.out.println("SHOW_WARNING: " + title + " - " + message);
            return;
        }
        dialogs.warning(ownerProvider.get(), title, message);
    }

    void showKeyboardShortcuts() {
        VBox contentBox = new VBox(10);
        contentBox.setPrefWidth(540);
        contentBox.setStyle("-fx-padding: 10;");
        Label intro = new Label("System Keyboard Shortcuts:");
        intro.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        contentBox.getChildren().add(intro);
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(8);
        grid.getStyleClass().add("quick-start-card");
        int row = 0;
        for (KeyboardShortcutEntry shortcut : KeyboardShortcutRegistry.getShortcuts()) {
            Label comboLabel = new Label(shortcut.getDisplayCombination());
            comboLabel.getStyleClass().add("quick-start-title");
            Label actionLabel = new Label(shortcut.getActionName());
            actionLabel.getStyleClass().add("heading-text");
            actionLabel.setStyle("-fx-font-size: 12px;");
            Label descLabel = new Label(shortcut.getDescription());
            descLabel.getStyleClass().add("quick-start-description");
            grid.add(comboLabel, 0, row);
            grid.add(actionLabel, 1, row);
            grid.add(descLabel, 2, row);
            row++;
        }
        ScrollPane scrollPane = new ScrollPane(grid);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(340);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        contentBox.getChildren().add(scrollPane);
        dialogs.show(Alert.AlertType.INFORMATION, ownerProvider.get(), "Keyboard Shortcuts",
                "CryptoCarver Keyboard Shortcuts", contentBox, ButtonType.OK);
    }

    void showAbout() {
        Node content = new Label("A comprehensive tool for cryptographic operations.\n\n" +
                "Version: 1.0.0\n" +
                "Author: Felipe Rodríguez Fonte\n" +
                "Contact: felipe.rodriguez.fonte@gmail.com\n\n" +
                "Features:\n" +
                "- Symmetric & Asymmetric Encryption\n" +
                "- Digital Signatures & Certificates\n" +
                "- Payments (EMV, PIN, CVV)\n" +
                "- JOSE (JWT, JWE, JWK)\n" +
                "- ASN.1 Analysis");
        dialogs.show(Alert.AlertType.INFORMATION, ownerProvider.get(), "About CryptoCarver",
                "CryptoCarver", content, ButtonType.OK);
    }

    void showDiagnostics(Supplier<String> displaySummaryProvider, Consumer<String> statusUpdater) {
        String diagnosticText = DiagnosticsReportBuilder.build(displaySummaryProvider.get());
        TextArea report = new TextArea(diagnosticText);
        report.setEditable(false);
        report.setWrapText(false);
        report.setPrefColumnCount(68);
        report.setPrefRowCount(15);
        report.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");
        ButtonType copyButton = new ButtonType("Copy report", ButtonBar.ButtonData.LEFT);
        Optional<ButtonType> selected = dialogs.show(Alert.AlertType.INFORMATION, ownerProvider.get(),
                "CryptoCarver diagnostics", "Runtime information (safe to copy)", report, copyButton, ButtonType.OK);
        if (selected.filter(copyButton::equals).isPresent()) {
            ClipboardContent clipboardContent = new ClipboardContent();
            clipboardContent.putString(diagnosticText);
            Clipboard.getSystemClipboard().setContent(clipboardContent);
            statusUpdater.accept("Diagnostics copied to clipboard");
        }
    }
}
