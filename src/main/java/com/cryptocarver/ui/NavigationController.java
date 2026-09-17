package com.cryptocarver.ui;

import java.util.Optional;
import java.util.function.Consumer;

/** Keeps the rail, navigation tree and content route on one shared navigation state. */
final class NavigationController {
    private final NavigationRail rail;
    private final SidePanel sidePanel;
    private final Consumer<String> operationNavigator;

    NavigationController(NavigationRail rail, SidePanel sidePanel, Consumer<String> operationNavigator) {
        this.rail = rail;
        this.sidePanel = sidePanel;
        this.operationNavigator = operationNavigator;
    }

    void install() {
        rail.setOnSectionSelected(this::navigateToSection);
    }

    void navigate(String operation) {
        Optional<UiNavigationRegistry.Route> route = UiNavigationRegistry.resolve(operation);
        route.map(this::sectionFor).ifPresent(section -> {
            rail.selectSectionSilently(section);
            if (sidePanel.getCurrentSection() != section) sidePanel.updateContent(section);
            sidePanel.selectOperation(operation);
        });
    }

    private void navigateToSection(NavigationRail.Section section) {
        sidePanel.setVisible(true);
        sidePanel.setManaged(true);
        sidePanel.updateContent(section);
        sidePanel.selectFirstOperation();
    }

    private NavigationRail.Section sectionFor(UiNavigationRegistry.Route route) {
        return switch (route.module()) {
            case JOSE -> NavigationRail.Section.JOSE;
            case COSE -> NavigationRail.Section.COSE;
            case KEYS_SYMMETRIC, KEYS_ASYMMETRIC -> NavigationRail.Section.KEYS;
            case CERTIFICATES -> NavigationRail.Section.CERTIFICATES;
            case POST_QUANTUM -> NavigationRail.Section.POST_QUANTUM;
            case XML_SECURITY, WSS_SECURITY -> NavigationRail.Section.XML_SECURITY;
            case EMV, PAYMENTS -> NavigationRail.Section.PAYMENTS;
            case HISTORY, SAVED_SESSIONS -> NavigationRail.Section.HISTORY;
            case PROCESS_DESIGNER -> NavigationRail.Section.PROCESS_DESIGNER;
            case CIPHER -> NavigationRail.Section.CIPHER;
            case AUTHENTICATION -> NavigationRail.Section.AUTHENTICATION;
            case EPOCH_CONVERTER, JSON_FORMATTER, GENERIC, CLIPBOARD_SHELF -> NavigationRail.Section.GENERIC;
        };
    }
}
