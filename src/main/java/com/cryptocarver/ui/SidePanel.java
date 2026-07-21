package com.cryptocarver.ui;

import com.cryptocarver.model.OperationDescriptor;
import com.cryptocarver.model.OperationRegistry;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Side Panel - Hierarchical navigation with search
 * Matches "Module Explorer" visual target
 */
public class SidePanel extends VBox {

    private final TextField searchField;
    private final TreeView<OperationNode> navigationTree;
    private final Button collapseButton;
    private Consumer<String> onItemSelected;
    private TreeItem<OperationNode> rootItem;
    private NavigationRail.Section currentSection = NavigationRail.Section.KEYS;

    // Helper wrapper for TreeView
    private static class OperationNode {
        String label;
        OperationDescriptor descriptor;

        OperationNode(String label) { this.label = label; }
        OperationNode(OperationDescriptor desc) { this.descriptor = desc; this.label = desc.getTitle(); }

        @Override
        public String toString() { return label; }
    }

    public SidePanel() {
        // Panel styling via CSS
        setMinWidth(280);
        setMaxWidth(280);
        setPrefWidth(280);
        getStyleClass().add("side-panel");

        // Header with search and collapse button
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(8));
        header.getStyleClass().add("side-panel-header");

        Label searchIcon = new Label("🔍");
        searchIcon.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 14px;");

        searchField = new TextField();
        searchField.setPromptText("Search");
        searchField.getStyleClass().add("search-field");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        searchField.textProperty().addListener((obs, old, newVal) -> filterTree(newVal));
        searchField.setOnAction(event -> selectFirstSearchResult());

        collapseButton = new Button("«");
        collapseButton.setTooltip(new Tooltip("Collapse panel"));
        collapseButton.getStyleClass().add("button");
        collapseButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #7f8c8d; -fx-font-weight: bold;");
        collapseButton.setOnAction(e -> collapse());

        header.getChildren().addAll(searchIcon, searchField, collapseButton);

        // Navigation TreeView
        navigationTree = new TreeView<>();
        navigationTree.setShowRoot(false);
        navigationTree.getStyleClass().add("navigation-tree");
        searchField.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE && !searchField.getText().isBlank()) {
                searchField.clear();
                navigationTree.requestFocus();
                event.consume();
            }
        });

        // Custom Cell Factory for visual states
        navigationTree.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(OperationNode item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTooltip(null);
                } else {
                    if (item.descriptor != null) {
                        HBox content = new HBox(5);
                        content.setAlignment(Pos.CENTER_LEFT);
                        Label iconLabel = new Label(item.descriptor.getIcon());
                        Label textLabel = new Label(item.descriptor.getTitle());
                        content.getChildren().addAll(iconLabel, textLabel);

                        if (item.descriptor.getStatus() == OperationDescriptor.Status.EXPERIMENTAL) {
                            Label expBadge = new Label("EXP");
                            expBadge.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white; -fx-font-size: 9px; -fx-padding: 1 3; -fx-background-radius: 3;");
                            content.getChildren().add(expBadge);
                        } else if (item.descriptor.getStatus() == OperationDescriptor.Status.PLANNED) {
                            Label planBadge = new Label("PLANNED");
                            planBadge.setStyle("-fx-background-color: #7f8c8d; -fx-text-fill: white; -fx-font-size: 9px; -fx-padding: 1 3; -fx-background-radius: 3;");
                            content.getChildren().add(planBadge);
                            textLabel.setStyle("-fx-text-fill: #7f8c8d;");
                        }

                        setGraphic(content);
                        setText(null);

                        // Keep category and aliases discoverable without making the narrow navigation tree wider.
                        String tooltipText = item.descriptor.getSubtitle()
                                + "\nCategory: " + item.descriptor.getCategory();
                        if (!item.descriptor.getAliases().isEmpty()) {
                            tooltipText += "\nAliases: " + String.join(", ", item.descriptor.getAliases());
                        }
                        if (item.descriptor.getStatus() != OperationDescriptor.Status.STABLE) {
                            tooltipText += " (" + item.descriptor.getStatus() + ")";
                        }
                        setTooltip(new Tooltip(tooltipText));

                    } else {
                        setText(item.label);
                        setGraphic(null);
                        setTooltip(null);
                    }
                }
            }
        });

        VBox.setVgrow(navigationTree, Priority.ALWAYS);

        // Item selection handler
        navigationTree.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            if (newVal != null && newVal.isLeaf()) {
                OperationNode selected = newVal.getValue();

                // Do not navigate to PLANNED operations
                if (selected.descriptor != null && selected.descriptor.getStatus() == OperationDescriptor.Status.PLANNED) {
                    return;
                }

                String navigationPath = selected.descriptor != null ? selected.descriptor.getNavigationPath() : selected.label;
                System.out.println("TreeView item selected: " + navigationPath);
                if (onItemSelected != null) {
                    onItemSelected.accept(navigationPath);
                }
            }
        });

        getChildren().addAll(header, navigationTree);

        // Initialize with default content (Keys)
        updateContent(NavigationRail.Section.KEYS);
    }

    public void updateContent(NavigationRail.Section section) {
        if (section != NavigationRail.Section.SEARCH) {
            this.currentSection = section;
        }

        rootItem = new TreeItem<>(new OperationNode(section.getLabel()));

        switch (section) {
            case CIPHER:
                buildCategoryTree("Cipher");
                break;
            case GENERIC:
                buildCategoryTree("Generic");
                break;
            case AUTHENTICATION:
                buildCategoryTree("Authentication");
                break;
            case KEYS:
                buildKeysTree(); // Custom grouping for keys
                break;
            case POST_QUANTUM:
                buildCategoryTree("Post-Quantum");
                break;
            case XML_SECURITY:
                buildCategoryTree("XML Security");
                break;
            case CERTIFICATES:
                buildCategoryTree("Certificates");
                break;
            case JOSE:
                buildCategoryTree("JOSE");
                break;
            case PAYMENTS:
                buildCategoryTree("Payments");
                break;
            case ASN1:
                buildCategoryTree("ASN1");
                break;
            case HISTORY:
                buildCategoryTree("History");
                break;
            case SEARCH:
                rootItem.getChildren().add(new TreeItem<>(new OperationNode("Quick search across all operations")));
                break;
        }

        navigationTree.setRoot(rootItem);
        expandAll(rootItem);
    }

    private void buildCategoryTree(String category) {
        List<OperationDescriptor> ops = OperationRegistry.getInstance().getAll().stream()
                .filter(o -> category.equals(o.getCategory()))
                .collect(Collectors.toList());
        for (OperationDescriptor op : ops) {
            rootItem.getChildren().add(new TreeItem<>(new OperationNode(op)));
        }
    }

    private void buildKeysTree() {
        TreeItem<OperationNode> symmetric = new TreeItem<>(new OperationNode("Symmetric"));
        TreeItem<OperationNode> asymmetric = new TreeItem<>(new OperationNode("Asymmetric"));
        TreeItem<OperationNode> tools = new TreeItem<>(new OperationNode("Tools"));

        List<OperationDescriptor> keysOps = OperationRegistry.getInstance().getAll().stream()
                .filter(o -> "Keys".equals(o.getCategory()))
                .collect(Collectors.toList());

        for (OperationDescriptor op : keysOps) {
            if (op.getId().startsWith("op_keys_rsa") || op.getId().startsWith("op_keys_ecdsa") ||
                op.getId().startsWith("op_keys_dsa") || op.getId().startsWith("op_keys_eddsa") ||
                op.getId().startsWith("op_keys_compare")) {
                asymmetric.getChildren().add(new TreeItem<>(new OperationNode(op)));
            } else if (op.getId().startsWith("op_keys_material") || op.getId().startsWith("op_keys_store")
                    || op.getId().startsWith("op_keys_pkcs11")) {
                tools.getChildren().add(new TreeItem<>(new OperationNode(op)));
            } else {
                symmetric.getChildren().add(new TreeItem<>(new OperationNode(op)));
            }
        }

        rootItem.getChildren().addAll(symmetric, asymmetric, tools);
    }

    private void expandAll(TreeItem<?> item) {
        if (item != null && !item.isLeaf()) {
            item.setExpanded(true);
            for (TreeItem<?> child : item.getChildren()) {
                expandAll(child);
            }
        }
    }

    private void filterTree(String filter) {
        if (filter == null || filter.trim().isEmpty()) {
            updateContent(this.currentSection);
        } else {
            List<OperationDescriptor> results = OperationRegistry.getInstance().search(filter);
            TreeItem<OperationNode> filteredRoot = new TreeItem<>(
                    new OperationNode("Search Results (" + results.size() + ")"));

            for (OperationDescriptor res : results) {
                filteredRoot.getChildren().add(new TreeItem<>(new OperationNode(res)));
            }

            if (results.isEmpty()) {
                filteredRoot.getChildren().add(new TreeItem<>(new OperationNode("No operations found")));
            }

            navigationTree.setRoot(filteredRoot);
            expandAll(filteredRoot);
        }
    }

    private void selectFirstSearchResult() {
        TreeItem<OperationNode> searchRoot = navigationTree.getRoot();
        if (searchRoot == null || searchField.getText().isBlank()) {
            return;
        }
        for (TreeItem<OperationNode> item : searchRoot.getChildren()) {
            if (item.isLeaf() && item.getValue() != null && item.getValue().descriptor != null) {
                navigationTree.getSelectionModel().select(item);
                navigationTree.scrollTo(navigationTree.getRow(item));
                return;
            }
        }
    }

    private void collapse() {
        setVisible(false);
        setManaged(false);
    }

    public void setOnItemSelected(Consumer<String> handler) {
        this.onItemSelected = handler;
    }
}
