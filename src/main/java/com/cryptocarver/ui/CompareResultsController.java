package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.ClipboardEntry;
import com.cryptocarver.model.ResultComparator;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.utils.ResultComparisonReportExporter;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class CompareResultsController {

    @FXML private Label statusBadgeLabel;
    @FXML private Label summaryLabel;

    @FXML private Label item1HeaderLabel;
    @FXML private Label item2HeaderLabel;

    @FXML private Label item1Label;
    @FXML private Label item2Label;

    @FXML private Label item1Source;
    @FXML private Label item2Source;

    @FXML private Label item1Algorithm;
    @FXML private Label item2Algorithm;

    @FXML private Label item1Time;
    @FXML private Label item2Time;

    @FXML private Label item1FormatSize;
    @FXML private Label item2FormatSize;

    @FXML private Label item1Class;
    @FXML private Label item2Class;

    @FXML private Label item1Hash;
    @FXML private Label item2Hash;

    @FXML private TextArea diffDetailsArea;

    private ClipboardEntry entry1;
    private ClipboardEntry entry2;
    private SecretVisibilityProfile profile;

    public void setEntries(ClipboardEntry entry1, ClipboardEntry entry2) {
        this.entry1 = entry1;
        this.entry2 = entry2;
        this.profile = AppSettings.getInstance().getSecretVisibilityProfile();
        refresh();
    }

    private void refresh() {
        if (entry1 == null || entry2 == null) return;

        ResultComparator.ComparisonDetails details = ResultComparator.compare(entry1, entry2, profile);

        statusBadgeLabel.setText(details.status().getLabel());
        if (details.status() == ResultComparator.Status.EQUAL) {
            statusBadgeLabel.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 4 10; -fx-background-radius: 4;");
        } else if (details.status() == ResultComparator.Status.DIFFERENT) {
            statusBadgeLabel.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 4 10; -fx-background-radius: 4;");
        } else {
            statusBadgeLabel.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 4 10; -fx-background-radius: 4;");
        }

        summaryLabel.setText(details.summary());

        item1HeaderLabel.setText("Item 1 (" + entry1.getLabel() + ")");
        item2HeaderLabel.setText("Item 2 (" + entry2.getLabel() + ")");

        item1Label.setText(entry1.getLabel());
        item2Label.setText(entry2.getLabel());

        item1Source.setText(entry1.getSourceOperation() != null ? entry1.getSourceOperation() : "—");
        item2Source.setText(entry2.getSourceOperation() != null ? entry2.getSourceOperation() : "—");

        item1Algorithm.setText(entry1.getAlgorithm() != null ? entry1.getAlgorithm() : "—");
        item2Algorithm.setText(entry2.getAlgorithm() != null ? entry2.getAlgorithm() : "—");

        item1Time.setText(entry1.getCreatedAt().toString());
        item2Time.setText(entry2.getCreatedAt().toString());

        item1FormatSize.setText(entry1.getFormat() + " · " + (entry1.getByteLength() != null ? entry1.getByteLength() + " bytes" : "—"));
        item2FormatSize.setText(entry2.getFormat() + " · " + (entry2.getByteLength() != null ? entry2.getByteLength() + " bytes" : "—"));

        item1Class.setText(entry1.getClassification().name());
        item2Class.setText(entry2.getClassification().name());

        item1Hash.setText(details.fingerprint1() != null ? details.fingerprint1() : "—");
        item2Hash.setText(details.fingerprint2() != null ? details.fingerprint2() : "—");

        diffDetailsArea.setText(details.textualDiff() != null ? details.textualDiff() : "No diff details");
    }

    @FXML
    private void handleExportReport() {
        if (entry1 == null || entry2 == null) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Comparison Report");
        chooser.setInitialFileName("comparison-report.md");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Markdown (*.md)", "*.md"),
                new FileChooser.ExtensionFilter("JSON (*.json)", "*.json")
        );

        Stage stage = (Stage) statusBadgeLabel.getScene().getWindow();
        File file = chooser.showSaveDialog(stage);
        if (file == null) return;

        try {
            String content;
            if (file.getName().endsWith(".json")) {
                content = ResultComparisonReportExporter.toJson(entry1, entry2, profile);
            } else {
                content = ResultComparisonReportExporter.toMarkdown(entry1, entry2, profile);
            }
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);

            Alert confirmation = new Alert(Alert.AlertType.INFORMATION, "Report saved to:\n" + file.getAbsolutePath(), ButtonType.OK);
            confirmation.setTitle("Comparison Report Exported");
            confirmation.setHeaderText("Report Exported Successfully");
            confirmation.showAndWait();
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Unable to write report: " + e.getMessage(), ButtonType.OK);
            alert.showAndWait();
        }
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) statusBadgeLabel.getScene().getWindow();
        stage.close();
    }
}
