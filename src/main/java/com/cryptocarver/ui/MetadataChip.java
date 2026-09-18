package com.cryptocarver.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

/** Compact, textual metadata badge that never relies on colour alone. */
public final class MetadataChip extends HBox {
    public enum Variant { NEUTRAL, INFO, WARNING, DANGER }

    public MetadataChip(String text, Variant variant) {
        Label label = new Label(text == null ? "" : text);
        label.setAccessibleText(label.getText());
        getChildren().add(label);
        setAlignment(Pos.CENTER);
        getStyleClass().add("metadata-chip");
        getStyleClass().add("metadata-chip-" + (variant == null ? Variant.NEUTRAL : variant).name().toLowerCase());
        setAccessibleText(label.getText());
    }
}
