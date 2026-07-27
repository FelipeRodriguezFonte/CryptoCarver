package com.cryptocarver.ui;

import javafx.scene.control.ListCell;

public class ProcessDesignerAlgorithmListCell extends ListCell<String> {
    @Override
    protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText(null);
            setDisable(false);
            setStyle("");
        } else if (item.startsWith("---")) {
            setText(item);
            setDisable(true);
            setStyle("-fx-font-weight: bold; -fx-text-fill: #999999;");
        } else {
            setText(item);
            setDisable(false);
            setStyle("-fx-padding: 0 0 0 10;");
        }
    }
}
