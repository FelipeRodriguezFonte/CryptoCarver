package com.cryptocarver.ui;

import com.cryptocarver.model.OperationDescriptor;
import com.cryptocarver.model.OperationRegistry;
import com.cryptocarver.service.I18nService;
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
    /** First navigable leaf of the current section, captured before Favorites/Recents are
     *  spliced in, so a bare section switch lands on that section's own content rather than
     *  an unrelated global favorite. Null for sections with nothing selectable (e.g. Search). */
    private TreeItem<OperationNode> firstPrimaryOperation;

    private Consumer<com.cryptocarver.model.HistoryCommand> onHistoryItemSelected;

    // Helper wrapper for TreeView
    private static class OperationNode {
        String label;
        OperationDescriptor descriptor;
        com.cryptocarver.model.HistoryCommand historyCommand;

        OperationNode(String label) { this.label = label; }
        OperationNode(OperationDescriptor desc) { this.descriptor = desc; this.label = desc.getTitle(); }
        OperationNode(com.cryptocarver.model.HistoryCommand cmd) {
            this.historyCommand = cmd;
            this.label = cmd.getOperation() + " (" + cmd.getTimestamp() + ")";
            this.descriptor = OperationRegistry.getInstance().resolveNavigation(cmd.getNavigationOperation()).orElse(null);
        }

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
        searchField.setPromptText(I18nService.getInstance().text("side.search"));
        searchField.setAccessibleText(I18nService.getInstance().text("side.search"));
        searchField.getStyleClass().add("search-field");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        searchField.textProperty().addListener((obs, old, newVal) -> filterTree(newVal));
        searchField.setOnAction(event -> selectFirstSearchResult());

        collapseButton = new Button("«");
        collapseButton.setTooltip(new Tooltip(I18nService.getInstance().text("side.collapse")));
        collapseButton.setAccessibleText(I18nService.getInstance().text("side.collapse"));
        collapseButton.setFocusTraversable(true);
        collapseButton.getStyleClass().add("button");
        collapseButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #7f8c8d; -fx-font-weight: bold;");
        collapseButton.setOnAction(e -> collapse());

        header.getChildren().addAll(searchIcon, searchField, collapseButton);

        // Navigation TreeView
        navigationTree = new TreeView<>();
        navigationTree.setShowRoot(false);
        navigationTree.setAccessibleText(I18nService.getInstance().text("side.navigation"));
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
                        Label textLabel = new Label(item.historyCommand != null ? item.label : item.descriptor.getTitle());
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
                                + "\n" + I18nService.getInstance().text("side.category", item.descriptor.getCategory());
                        if (!item.descriptor.getAliases().isEmpty()) {
                            tooltipText += "\n" + I18nService.getInstance().text("side.aliases", String.join(", ", item.descriptor.getAliases()));
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

                if (selected.historyCommand != null) {
                    if (onHistoryItemSelected != null) {
                        onHistoryItemSelected.accept(selected.historyCommand);
                    } else if (onItemSelected != null) {
                        onItemSelected.accept(selected.historyCommand.getOperation());
                    }
                    return;
                }

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

    private com.cryptocarver.model.HistoryManager historyManager;

    public void setHistoryManager(com.cryptocarver.model.HistoryManager historyManager) {
        this.historyManager = historyManager;
    }

    public void setOnHistoryItemSelected(Consumer<com.cryptocarver.model.HistoryCommand> handler) {
        this.onHistoryItemSelected = handler;
    }

    public void updateContent(NavigationRail.Section section) {
        if (section != NavigationRail.Section.SEARCH) {
            this.currentSection = section;
        }

        rootItem = new TreeItem<>(new OperationNode(localizedSection(section)));

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
            case COSE:
                buildCategoryTree("COSE");
                break;
            case PAYMENTS:
                buildCategoryTree("Payments");
                break;
            case ASN1:
                buildCategoryTree("ASN1");
                break;
            case HISTORY:
                buildHistoryTree();
                break;
            case SEARCH:
                rootItem.getChildren().add(new TreeItem<>(new OperationNode(I18nService.getInstance().text("side.quickSearch"))));
                break;
        }

        // Snapshot the first real leaf of this section's own tree before Favorites/Recents
        // (which are global, not section-scoped) get spliced in above/below it.
        firstPrimaryOperation = section == NavigationRail.Section.SEARCH ? null : findFirstSelectableLeaf(rootItem);

        if (section != NavigationRail.Section.SEARCH) {
            attachFavoritesIfAny();
            if (section != NavigationRail.Section.HISTORY) {
                attachRecentsIfAny();
            }
        }

        navigationTree.setRoot(rootItem);
        expandAll(rootItem);
    }

    /**
     * Selects the first navigable operation of the current section without rebuilding the
     * tree. Used when the user switches sections via the rail icon or the breadcrumb section
     * pill, so the content pane lands on a real screen that matches the tree instead of
     * leaving whatever module happened to be shown before the switch.
     */
    public void selectFirstOperation() {
        if (firstPrimaryOperation != null) {
            navigationTree.getSelectionModel().select(firstPrimaryOperation);
            navigationTree.scrollTo(navigationTree.getRow(firstPrimaryOperation));
        }
    }

    private TreeItem<OperationNode> findFirstSelectableLeaf(TreeItem<OperationNode> node) {
        if (node == null) return null;
        for (TreeItem<OperationNode> child : node.getChildren()) {
            OperationNode value = child.getValue();
            if (child.isLeaf()) {
                if (value == null) continue;
                if (value.historyCommand != null) return child;
                if (value.descriptor != null
                        && value.descriptor.getStatus() != OperationDescriptor.Status.PLANNED) {
                    return child;
                }
            } else {
                TreeItem<OperationNode> found = findFirstSelectableLeaf(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    public NavigationRail.Section getCurrentSection() {
        return currentSection != null ? currentSection : NavigationRail.Section.KEYS;
    }

    private void attachFavoritesIfAny() {
        List<String> favs = com.cryptocarver.model.AppSettings.getInstance().getFavorites();
        if (favs.isEmpty()) return;

        TreeItem<OperationNode> favsGroup = new TreeItem<>(new OperationNode(I18nService.getInstance().text("side.favorites")));
        for (String fav : favs) {
            OperationDescriptor desc = OperationRegistry.getInstance().resolveNavigation(fav).orElse(null);
            if (desc != null) {
                favsGroup.getChildren().add(new TreeItem<>(new OperationNode(desc)));
            } else {
                favsGroup.getChildren().add(new TreeItem<>(new OperationNode(fav)));
            }
        }
        rootItem.getChildren().add(0, favsGroup);
    }

    private void attachRecentsIfAny() {
        if (historyManager == null) return;
        List<com.cryptocarver.model.HistoryCommand> items = historyManager.getHistoryItems();
        if (items.isEmpty()) return;

        TreeItem<OperationNode> recentsGroup = new TreeItem<>(new OperationNode(I18nService.getInstance().text("side.recent")));
        for (com.cryptocarver.model.HistoryCommand item : items.stream().limit(8).toList()) {
            recentsGroup.getChildren().add(new TreeItem<>(new OperationNode(item)));
        }
        rootItem.getChildren().add(recentsGroup);
    }

    private void buildHistoryTree() {
        buildCategoryTree("History");
        if (historyManager == null || historyManager.getHistoryItems().isEmpty()) {
            rootItem.getChildren().add(new TreeItem<>(new OperationNode(I18nService.getInstance().text("side.emptyHistory"))));
        } else {
            attachRecentsIfAny();
        }
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
                    new OperationNode(I18nService.getInstance().text("side.searchResults", results.size())));

            for (OperationDescriptor res : results) {
                filteredRoot.getChildren().add(new TreeItem<>(new OperationNode(res)));
            }

            if (results.isEmpty()) {
                filteredRoot.getChildren().add(new TreeItem<>(new OperationNode(I18nService.getInstance().text("side.noOperations"))));
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

    private String localizedSection(NavigationRail.Section section) {
        String key = switch (section) {
            case POST_QUANTUM -> "postQuantum";
            case XML_SECURITY -> "xmlSecurity";
            default -> section.name().toLowerCase(java.util.Locale.ROOT);
        };
        return I18nService.getInstance().text("nav." + key);
    }

    /** Refreshes labels/tooltips while preserving the current section and search text. */
    public void refreshLocalizedText() {
        javafx.scene.Node focusOwner = getScene() == null ? null : getScene().getFocusOwner();
        searchField.setPromptText(I18nService.getInstance().text("side.search"));
        searchField.setAccessibleText(I18nService.getInstance().text("side.search"));
        collapseButton.setTooltip(new Tooltip(I18nService.getInstance().text("side.collapse")));
        collapseButton.setAccessibleText(I18nService.getInstance().text("side.collapse"));
        navigationTree.setAccessibleText(I18nService.getInstance().text("side.navigation"));
        String query = searchField.getText();
        if (query == null || query.isBlank()) updateContent(currentSection);
        else filterTree(query);
        if (focusOwner != null) {
            javafx.application.Platform.runLater(() -> {
                if (focusOwner.getScene() != null && focusOwner.isVisible()
                        && !focusOwner.isDisabled() && focusOwner.isFocusTraversable()) {
                    focusOwner.requestFocus();
                }
            });
        }
    }
}
