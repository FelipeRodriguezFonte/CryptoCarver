package com.cryptocarver.ui;

import com.cryptocarver.service.I18nService;
import javafx.geometry.Pos;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;
import java.util.function.Consumer;

/**
 * Navigation Rail - compact icon-and-label navigation.
 * Search intentionally lives in the side panel/command palette, not in this
 * primary section rail.  The SEARCH enum value remains for route compatibility.
 */
public class NavigationRail extends VBox {

    private final ToggleGroup toggleGroup;
    private SidePanel sidePanel;
    private Consumer<Section> onSectionSelected;
    private boolean syncingSelection;

    // Navigation sections
    public enum Section {
        SEARCH("search", "Search"),
        PROCESS_DESIGNER("processDesigner", "Process Designer"),
        GENERIC("generic", "Generic"),
        CIPHER("cipher", "Cipher"),
        AUTHENTICATION("authentication", "Authentication"),
        KEYS("keys", "Keys"),
        POST_QUANTUM("postQuantum", "Post-Quantum"),
        XML_SECURITY("xmlSecurity", "XML Security"),
        CERTIFICATES("certificates", "Certificates"),
        JOSE("jose", "JOSE"),
        COSE("cose", "COSE"),
        PAYMENTS("payments", "Payments"),
        ASN1("asn1", "ASN.1"),
        HISTORY("history", "History");

        private final String icon;
        private final String label;

        Section(String icon, String label) {
            this.icon = icon;
            this.label = label;
        }

        public String getIcon() {
            return icon;
        }

        public String getLabel() {
            return label;
        }
    }

    public NavigationRail() {
        toggleGroup = new ToggleGroup();

        // Rail styling via CSS
        getStyleClass().add("navigation-rail");
        setAlignment(Pos.TOP_CENTER);
        setSpacing(4);
        setMinWidth(64);
        setMaxWidth(64);
        setPrefWidth(64);

        // Create buttons for main sections
        for (Section section : Section.values()) {
            if (section != Section.SEARCH) {
                addButton(section);
            }
        }

        // Select Keys by default
        selectSection(Section.KEYS);
    }

    private void addButton(Section section) {
        ToggleButton button = new ToggleButton(compactLabel(section));
        var icon = IconRegistry.icon(section.getIcon());
        icon.setIconSize(18);
        button.setGraphic(icon);
        button.setToggleGroup(toggleGroup);
        button.getStyleClass().add("rail-button");
        button.setMinWidth(60);
        button.setMaxWidth(60);
        button.setMinHeight(46);
        button.setPrefHeight(46);
        button.setMaxHeight(46);
        button.setContentDisplay(javafx.scene.control.ContentDisplay.TOP);
        button.setGraphicTextGap(3);
        button.setTooltip(new Tooltip(localizedLabel(section)));
        button.setAccessibleText(localizedLabel(section));
        button.setAccessibleHelp(localizedLabel(section));
        button.setFocusTraversable(true);
        if (isGroupStart(section)) {
            button.getStyleClass().add("rail-group-start");
        }

        // Selection handler
        button.selectedProperty().addListener((obs, wasSelected, isNowSelected) -> {
            if (isNowSelected) {
                handleSectionSelected(section);
            }
        });

        button.setUserData(section);
        getChildren().add(button);
    }

    private String compactLabel(Section section) {
        String label = localizedLabel(section).trim();
        if (label.length() <= 10) return label;
        int separator = label.indexOf(' ');
        if (separator > 0 && separator <= 9) return label.substring(0, separator);
        return label.substring(0, 9) + "…";
    }

    private boolean isGroupStart(Section section) {
        return switch (section) {
            case PROCESS_DESIGNER, CIPHER, POST_QUANTUM, PAYMENTS, HISTORY -> true;
            default -> false;
        };
    }

    private String localizedLabel(Section section) {
        return I18nService.getInstance().text("nav." + switch (section) {
            case POST_QUANTUM -> "postQuantum";
            case XML_SECURITY -> "xmlSecurity";
            case PROCESS_DESIGNER -> "processDesigner";
            default -> section.name().toLowerCase(java.util.Locale.ROOT);
        });
    }

    /** Reapplies only user-visible rail labels; section identity and routes remain unchanged. */
    public void refreshLocalizedText() {
        for (var node : getChildren()) {
            if (node instanceof ToggleButton button && button.getUserData() instanceof Section section) {
                String label = localizedLabel(section);
                button.setText(compactLabel(section));
                button.setTooltip(new Tooltip(label));
                button.setAccessibleText(label);
                button.setAccessibleHelp(label);
            }
        }
    }

    private void handleSectionSelected(Section section) {
        System.out.println("Rail section selected: " + section.getLabel());

        if (syncingSelection) return;
        if (onSectionSelected != null) {
            onSectionSelected.accept(section);
            return;
        }
        // Fallback for isolated uses of the control.
        if (sidePanel != null) {
            sidePanel.setVisible(true);
            sidePanel.setManaged(true);
            sidePanel.updateContent(section);
            // Without this, switching sections only refreshed the tree: the content pane,
            // breadcrumb and toolbar kept showing whatever operation was active before the
            // click, out of sync with the newly selected section.
            sidePanel.selectFirstOperation();
        }
    }

    public void setSidePanel(SidePanel panel) {
        this.sidePanel = panel;
    }

    public void setOnSectionSelected(Consumer<Section> handler) {
        this.onSectionSelected = handler;
    }

    /** Updates active affordance without treating a programmatic route sync as a rail click. */
    public void selectSectionSilently(Section section) {
        syncingSelection = true;
        try {
            for (var node : getChildren()) {
                if (node instanceof ToggleButton button && button.getUserData() == section) {
                    button.setSelected(true);
                    return;
                }
            }
        } finally {
            syncingSelection = false;
        }
    }

    public void selectSection(Section section) {
        for (var node : getChildren()) {
            if (node instanceof ToggleButton) {
                ToggleButton btn = (ToggleButton) node;
                if (btn.getUserData() == section) {
                    btn.setSelected(true);
                    btn.fire(); // Trigger action
                    break;
                }
            }
        }
    }
}
