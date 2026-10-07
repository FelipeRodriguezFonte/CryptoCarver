package com.cryptocarver.ui;

import com.cryptocarver.model.process.NodeCatalog;
import com.cryptocarver.model.process.NodeDescriptor;
import com.cryptocarver.model.process.ProcessDefinition;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Builds and filters the searchable process node palette. */
final class ProcessPaletteCoordinator {
    record View(Supplier<VBox> paletteItemsContainer,
                Function<String, String> text,
                Supplier<Integer> nodeCount,
                NodeAdder addNode,
                Consumer<ProcessDefinition.Node> selectNode) {
        View {
            Objects.requireNonNull(paletteItemsContainer);
            Objects.requireNonNull(text);
            Objects.requireNonNull(nodeCount);
            Objects.requireNonNull(addNode);
            Objects.requireNonNull(selectNode);
        }
    }

    @FunctionalInterface
    interface NodeAdder {
        ProcessDefinition.Node add(String type, String label, double x, double y);
    }

    void buildPalette(View view, String filter) {
        filterPalette(view, filter);
    }

    void filterPalette(View view, String filter) {
        VBox paletteItemsContainer = view.paletteItemsContainer().get();
        if (paletteItemsContainer == null) return;
        paletteItemsContainer.getChildren().clear();

        Function<String, String> text = view.text();
        Supplier<Integer> nodeCount = view.nodeCount();
        NodeAdder addNode = view.addNode();
        Consumer<ProcessDefinition.Node> selectNode = view.selectNode();
        String query = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        for (String category : NodeCatalog.categories()) {
            List<NodeDescriptor> matching = NodeCatalog.descriptorsByCategory(category).stream()
                    .filter(descriptor -> matchesSearch(view, descriptor, query))
                    .toList();
            if (matching.isEmpty()) continue;

            String categoryKey = "module.process.category." + switch (category) {
                case "Inputs" -> "inputs";
                case "Conversions" -> "conversions";
                case "Crypto" -> "crypto";
                case "Generators" -> "generators";
                case "Key Material" -> "keyMaterial";
                case "WS-Security" -> "wsSecurity";
                case "Outputs" -> "outputs";
                default -> category.toLowerCase(Locale.ROOT);
            };
            Label categoryHeader = new Label(text.apply(categoryKey).toUpperCase(Locale.ROOT));
            categoryHeader.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: #8899aa; -fx-padding: 4 0 2 0;");
            paletteItemsContainer.getChildren().add(categoryHeader);

            for (NodeDescriptor descriptor : matching) {
                HBox item = new HBox(6);
                item.setPadding(new Insets(4, 6, 4, 6));
                item.setStyle("-fx-background-color: #242d38; -fx-background-radius: 4; -fx-cursor: hand;");

                Label iconLabel = new Label(descriptor.icon());
                iconLabel.setStyle("-fx-font-size: 13px;");
                // Bind the preferred-size sentinel so the legacy 18 px CSS minimum
                // cannot override it after applyCss on platforms with wider glyphs.
                iconLabel.minWidthProperty().bind(new javafx.beans.property.SimpleDoubleProperty(
                        javafx.scene.layout.Region.USE_PREF_SIZE));

                VBox textBox = new VBox(1);
                Label titleLabel = new Label(text.apply(descriptor.labelKey()));
                titleLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #ffffff; -fx-font-weight: bold;");
                Label descLabel = new Label(text.apply(descriptor.descriptionKey()));
                descLabel.setStyle("-fx-font-size: 9px; -fx-text-fill: #8899aa;");
                descLabel.setWrapText(true);
                textBox.getChildren().addAll(titleLabel, descLabel);
                item.getChildren().addAll(iconLabel, textBox);

                item.setOnMouseEntered(event -> item.setStyle("-fx-background-color: #334455; -fx-background-radius: 4; -fx-cursor: hand;"));
                item.setOnMouseExited(event -> item.setStyle("-fx-background-color: #242d38; -fx-background-radius: 4; -fx-cursor: hand;"));
                item.setOnMouseClicked(event -> addOnDoubleClick(
                        nodeCount, addNode, selectNode, text, descriptor, event));
                paletteItemsContainer.getChildren().add(item);
            }
        }
    }

    private void addOnDoubleClick(Supplier<Integer> nodeCount, NodeAdder addNode,
                                  Consumer<ProcessDefinition.Node> selectNode, Function<String, String> text,
                                  NodeDescriptor descriptor, MouseEvent event) {
        if (event.getClickCount() != 2) return;
        int count = nodeCount.get();
        double placeX = 60 + (count % 5) * 40;
        double placeY = 80 + (count % 6) * 35;
        ProcessDefinition.Node added = addNode.add(
                descriptor.type(), text.apply(descriptor.labelKey()), placeX, placeY);
        selectNode.accept(added);
    }

    private boolean matchesSearch(View view, NodeDescriptor descriptor, String query) {
        if (query.isEmpty()) return true;
        return descriptor.type().toLowerCase(Locale.ROOT).contains(query)
                || descriptor.category().toLowerCase(Locale.ROOT).contains(query)
                || view.text().apply(descriptor.labelKey()).toLowerCase(Locale.ROOT).contains(query)
                || view.text().apply(descriptor.descriptionKey()).toLowerCase(Locale.ROOT).contains(query);
    }
}
