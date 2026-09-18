package com.cryptocarver.ui;

import javafx.scene.Parent;
import javafx.scene.layout.VBox;
import javafx.scene.Node;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.CompletableFuture;

/**
 * Small shell adapter for deferred modules.  The host owns no module state:
 * {@link ModuleLoader} remains the cache and this node only displays the
 * currently selected root.
 */
public final class ModuleHost extends VBox {

    private ModuleLoader loader;
    private Executor executor;
    private String activeRoute;
    private String resource;
    private Parent root;
    private Object controller;

    public ModuleHost() {
        getStyleClass().add("module-host");
    }

    public void configure(ModuleLoader loader, Executor executor) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /** Resource used by FXML shells that declare a deferred module host. */
    public void setResource(String resource) { this.resource = resource; }
    public String getResource() { return resource; }

    /** Loads this host synchronously on the FX thread, once, and caches its root/controller. */
    public Parent loadNow() {
        if (root != null) return root;
        if (loader == null || executor == null) {
            throw new IllegalStateException("ModuleHost is not configured");
        }
        if (resource == null || resource.isBlank()) {
            throw new IllegalStateException("ModuleHost resource is not configured");
        }
        try {
            loader.register(resource, resource);
            root = loader.load(resource, false);
            controller = loader.controller(resource).orElse(null);
            // Several module roots still declare visible="false" managed="false". That dates
            // from when the shell inlined them with fx:include: the root *was* the container
            // the shell showed and hid, so starting hidden was correct. The host is that
            // container now, so a root left hidden never appears and the host collapses to
            // zero height - the section opens on an empty pane. The host owns visibility.
            root.setVisible(true);
            root.setManaged(true);
            getChildren().setAll(root);
            return root;
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Unable to load module '" + resource + "'", error);
        }
    }

    public Object controller() { return controller; }
    public Parent root() { return root; }

    public CompletableFuture<Parent> show(String route) {
        if (loader == null || executor == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("ModuleHost is not configured"));
        }
        String normalized = route == null ? "" : route.trim();
        return loader.loadInto(normalized, this, executor, true)
                .thenApply(root -> {
                    activeRoute = normalized;
                    return root;
                });
    }

    /** Shows the FXML-declared module, loading it only on first use. */
    public Parent showConfigured() {
        Parent loaded = loadNow();
        setManaged(true);
        setVisible(true);
        activeRoute = resource;
        return loaded;
    }

    public CompletableFuture<Parent> preload(String route) {
        if (loader == null || executor == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("ModuleHost is not configured"));
        }
        return loader.loadAsync(route, executor, false);
    }

    public String activeRoute() {
        return activeRoute;
    }
}
