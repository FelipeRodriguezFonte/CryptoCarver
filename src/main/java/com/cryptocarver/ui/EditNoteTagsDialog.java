package com.cryptocarver.ui;

import com.cryptocarver.model.ClipboardEntry;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class EditNoteTagsDialog {

    public static class Result {
        public final List<String> tags;
        public final String note;

        public Result(List<String> tags, String note) {
            this.tags = tags;
            this.note = note;
        }
    }

    public static Optional<Result> show(Window owner, ClipboardEntry entry) {
        Dialog<Result> dialog = new Dialog<>();
        dialog.setTitle("Edit Tags & Note");
        dialog.setHeaderText("Annotate context for: " + entry.getLabel());
        if (owner != null) dialog.initOwner(owner);

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        TextField tagsField = new TextField();
        tagsField.setPromptText("e.g. key, prod, v1 (comma separated, max 12)");
        tagsField.setText(String.join(", ", entry.getTags()));

        Label tagsHintLabel = new Label("Max 12 tags. Separate tags with commas.");
        tagsHintLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #7f8c8d;");

        TextArea noteArea = new TextArea();
        noteArea.setPromptText("Optional notes or laboratory context (max 1000 characters)...");
        noteArea.setWrapText(true);
        noteArea.setPrefRowCount(5);
        noteArea.setText(entry.getNote());

        Label charCountLabel = new Label();
        charCountLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #7f8c8d;");

        Runnable updateCharCount = () -> {
            int len = noteArea.getText() == null ? 0 : noteArea.getText().length();
            charCountLabel.setText(len + " / 1000 chars");
            if (len > 1000) {
                charCountLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #e74c3c;");
            } else {
                charCountLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #7f8c8d;");
            }
        };
        updateCharCount.run();

        noteArea.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.length() > 1000) {
                noteArea.setText(newVal.substring(0, 1000));
            }
            updateCharCount.run();
        });

        VBox content = new VBox(8,
                new Label("Tags:"),
                tagsField,
                tagsHintLabel,
                new Label("Note:"),
                noteArea,
                charCountLabel
        );
        content.setPadding(new Insets(10));
        content.setPrefWidth(450);

        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                String rawTagsStr = tagsField.getText();
                List<String> rawTagsList = rawTagsStr == null || rawTagsStr.isBlank()
                        ? java.util.Collections.emptyList()
                        : Arrays.asList(rawTagsStr.split("[,;]+"));
                List<String> cleanTags = ClipboardEntry.sanitizeTags(rawTagsList);
                String cleanNote = ClipboardEntry.sanitizeNote(noteArea.getText());
                return new Result(cleanTags, cleanNote);
            }
            return null;
        });

        return dialog.showAndWait();
    }
}
