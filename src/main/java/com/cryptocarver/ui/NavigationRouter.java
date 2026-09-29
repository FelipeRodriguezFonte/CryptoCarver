package com.cryptocarver.ui;

import javafx.scene.Node;
import javafx.scene.control.TitledPane;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Routes shell navigation to module hosts without retaining module controller instances. */
final class NavigationRouter {
    private final Supplier<Iterable<Node>> hosts;
    private final Supplier<Iterable<Node>> placeholders;
    private final Function<UiNavigationRegistry.Module, Node> hostProvider;
    private final Function<UiNavigationRegistry.Module, Object> controllerProvider;
    private final Map<UiNavigationRegistry.Module, BiConsumer<UiNavigationRegistry.Route,Object>> callbacks;
    private final Runnable beforeActivation;
    private final Consumer<String> itemSelected;

    NavigationRouter(Supplier<Iterable<Node>> hosts, Supplier<Iterable<Node>> placeholders,
            Function<UiNavigationRegistry.Module,Node> hostProvider,
            Function<UiNavigationRegistry.Module,Object> controllerProvider,
            Map<UiNavigationRegistry.Module,BiConsumer<UiNavigationRegistry.Route,Object>> callbacks,
            Runnable beforeActivation, Consumer<String> itemSelected) {
        this.hosts=hosts; this.placeholders=placeholders; this.hostProvider=hostProvider;
        this.controllerProvider=controllerProvider; this.callbacks=new EnumMap<>(callbacks);
        this.beforeActivation=beforeActivation; this.itemSelected=itemSelected;
    }

    void handleItemSelected(String item) { itemSelected.accept(item); }

    boolean activate(String operation) {
        Optional<UiNavigationRegistry.Route> resolved=UiNavigationRegistry.resolve(operation);
        if(resolved.isEmpty()) return false;
        UiNavigationRegistry.Route route=resolved.get();
        UiNavigationRegistry.Module module=route.module();
        Object controller=controllerProvider.apply(module); // Resolve afresh for every navigation.
        beforeActivation.run();
        hideAllContainers();
        Node host=hostProvider.apply(module);
        if(host!=null) { host.setManaged(true); host.setVisible(true); }
        BiConsumer<UiNavigationRegistry.Route,Object> callback=callbacks.get(module);
        if(callback!=null) callback.accept(route,controller);
        return true;
    }

    void showContainer(UiNavigationRegistry.Module module) {
        controllerProvider.apply(module);
        beforeActivation.run();
        hideAllContainers();
        Node host=hostProvider.apply(module);
        if(host!=null) { host.setManaged(true); host.setVisible(true); }
    }

    void hideAllContainers() {
        for(Node node:placeholders.get()) if(node!=null) { node.setManaged(false); node.setVisible(false); }
        for(Node host:hosts.get()) if(host!=null) { host.setManaged(false); host.setVisible(false); }
    }

    static void expandByTitle(Node root, String title, Map<String,String> textCatalog, Consumer<TitledPane> reveal) {
        if(title==null||title.isBlank()) return;
        javafx.scene.control.Accordion accordion=findAccordion(root);
        if(accordion==null) return;
        for(TitledPane pane:accordion.getPanes()) {
            if(ModulePaneMatcher.matches(pane,title,textCatalog)) {
                accordion.setExpandedPane(pane);
                reveal.accept(pane);
                return;
            }
        }
    }

    private static javafx.scene.control.Accordion findAccordion(Node node) {
        if(node instanceof javafx.scene.control.Accordion accordion) return accordion;
        if(node instanceof javafx.scene.Parent parent) for(Node child:parent.getChildrenUnmodifiable()) {
            var found=findAccordion(child); if(found!=null) return found;
        }
        return null;
    }

    void restoreStartupLastRoute(Supplier<String> lastRoute, Consumer<String> navigate) {
        try {
            String route=lastRoute.get();
            if(route!=null&&!route.isBlank()&&UiNavigationRegistry.resolve(route).isPresent()) navigate.accept(route);
        } catch(Exception ignored) { /* Preferences must not prevent shell startup. */ }
    }
}
