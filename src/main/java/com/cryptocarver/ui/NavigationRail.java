package com.cryptocarver.ui;

import com.cryptocarver.service.I18nService;
import javafx.geometry.Pos;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;

/**
 * Navigation Rail - Left sidebar with icon-only navigation
 * Modern IDE-style navigation with CSS styling
 */
public class NavigationRail extends VBox {

    private final ToggleGroup toggleGroup;
    private SidePanel sidePanel;

    // Navigation sections
    public enum Section {
        SEARCH("🔍", "Search"),
        GENERIC("◈", "Generic"),
        CIPHER("🔒", "Cipher"),
        AUTHENTICATION("🛡", "Authentication"),
        KEYS("🔑", "Keys"),
        POST_QUANTUM("⚛", "Post-Quantum"),
        XML_SECURITY("📝", "XML Security"),
        CERTIFICATES("📜", "Certificates"),
        JOSE("🌐", "JOSE"),
        COSE("📦", "COSE"),
        PAYMENTS("💳", "Payments"),
        ASN1("{}", "ASN.1"),
        HISTORY("⏱", "History");

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
        setMinWidth(48);
        setMaxWidth(48);
        setPrefWidth(48);

        // Create buttons for main sections
        for (Section section : Section.values()) {
            addButton(section);
        }

        // Select Keys by default
        selectSection(Section.KEYS);
    }

    private void addButton(Section section) {
        ToggleButton button = new ToggleButton(section.getIcon());
        button.setToggleGroup(toggleGroup);
        button.getStyleClass().add("rail-button");
        button.setMinSize(40, 40);
        button.setMaxSize(40, 40);
        button.setTooltip(new Tooltip(localizedLabel(section)));
        button.setAccessibleText(localizedLabel(section));
        button.setAccessibleHelp(localizedLabel(section));
        button.setFocusTraversable(true);

        // Selection handler
        button.selectedProperty().addListener((obs, wasSelected, isNowSelected) -> {
            if (isNowSelected) {
                handleSectionSelected(section);
            }
        });

        button.setUserData(section);
        getChildren().add(button);
    }

    private String localizedLabel(Section section) {
        return I18nService.getInstance().text("nav." + switch (section) {
            case POST_QUANTUM -> "postQuantum";
            case XML_SECURITY -> "xmlSecurity";
            default -> section.name().toLowerCase(java.util.Locale.ROOT);
        });
    }

    /** Reapplies only user-visible rail labels; section identity and routes remain unchanged. */
    public void refreshLocalizedText() {
        for (var node : getChildren()) {
            if (node instanceof ToggleButton button && button.getUserData() instanceof Section section) {
                String label = localizedLabel(section);
                button.setTooltip(new Tooltip(label));
                button.setAccessibleText(label);
                button.setAccessibleHelp(label);
            }
        }
    }

    private void handleSectionSelected(Section section) {
        System.out.println("Rail section selected: " + section.getLabel());

        // Open side panel if closed
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
