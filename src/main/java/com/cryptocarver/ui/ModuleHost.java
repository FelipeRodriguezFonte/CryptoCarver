package com.cryptocarver.ui;

import javafx.scene.Parent;
import javafx.scene.layout.StackPane;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.CompletableFuture;

/**
 * Small shell adapter for deferred modules.  The host owns no module state:
 * {@link ModuleLoader} remains the cache and this node only displays the
 * currently selected root.
 */
public final class ModuleHost extends StackPane {

    private ModuleLoader loader;
    private Executor executor;
    private String activeRoute;

    public ModuleHost() {
        getStyleClass().add("module-host");
    }

    public void configure(ModuleLoader loader, Executor executor) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

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
