package com.cryptocarver.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Loads a module FXML once and keeps its scene graph and controller available
 * for subsequent navigation. All resources are resolved from the application
 * classpath; this class never follows network URLs.
 *
 * <p>The loader is deliberately independent of the shell controller. A
 * caller can register a route, optionally provide a wiring callback, and use
 * {@link #load(String, boolean)} from navigation code later. The {@code show}
 * flag only changes the selected route; attaching the returned node to a
 * host remains the shell's responsibility.</p>
 */
public final class ModuleLoader {

    private final ClassLoader resources;
    private final Map<String, Definition> definitions = new ConcurrentHashMap<>();
    private final Map<String, LoadedModule> cache = new ConcurrentHashMap<>();
    private volatile String visibleRoute;

    public ModuleLoader() {
        this(ModuleLoader.class.getClassLoader());
    }

    public ModuleLoader(Class<?> resourceAnchor) {
        this(Objects.requireNonNull(resourceAnchor, "resourceAnchor").getClassLoader());
    }

    public ModuleLoader(ClassLoader resources) {
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    /** Registers a route using a classpath resource such as {@code /fxml/cipher.fxml}. */
    public ModuleLoader register(String route, String resource) {
        return register(route, resource, ignored -> { });
    }

    /** Registers a route and a callback used to connect the loaded controller to the shell. */
    public ModuleLoader register(String route, String resource, Consumer<Object> wire) {
        String key = normalizeRoute(route);
        String path = normalizeResource(resource);
        definitions.put(key, new Definition(path, Objects.requireNonNull(wire, "wire")));
        cache.remove(key);
        return this;
    }

    /**
     * Loads a route, returning the cached root node. Calling this method with
     * {@code show=true} marks the route as visible; it does not create a stage
     * or mutate the scene graph outside the returned node.
     */
    public Parent load(String route, boolean show) throws IOException {
        String key = normalizeRoute(route);
        Definition definition = definitions.get(key);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown module route: " + key);
        }
        LoadedModule module = cache.computeIfAbsent(key, ignored -> loadDefinition(key, definition));
        if (show) visibleRoute = key;
        return module.root();
    }

    /** Loads without selecting the route, useful for background preloading. */
    public Parent preload(String route) throws IOException {
        return load(route, false);
    }

    public Optional<Object> controller(String route) {
        LoadedModule module = cache.get(normalizeRoute(route));
        return module == null ? Optional.empty() : Optional.ofNullable(module.controller());
    }

    public Optional<Parent> cached(String route) {
        LoadedModule module = cache.get(normalizeRoute(route));
        return module == null ? Optional.empty() : Optional.of(module.root());
    }

    public Optional<String> visibleRoute() {
        return Optional.ofNullable(visibleRoute);
    }

    public boolean isCached(String route) {
        return cache.containsKey(normalizeRoute(route));
    }

    /** Removes one cached module; the route remains registered for a later load. */
    public void invalidate(String route) {
        cache.remove(normalizeRoute(route));
    }

    /** Removes all loaded scene graphs while preserving route registrations. */
    public void clear() {
        cache.clear();
        visibleRoute = null;
    }

    private LoadedModule loadDefinition(String route, Definition definition) {
        URL location = resources.getResource(definition.resource().substring(1));
        if (location == null) {
            throw new IllegalArgumentException("Missing module resource: " + definition.resource());
        }
        String protocol = location.getProtocol();
        if (!"file".equalsIgnoreCase(protocol) && !"jar".equalsIgnoreCase(protocol)) {
            throw new IllegalArgumentException("Module resources must be local classpath resources: " + location);
        }
        try {
            FXMLLoader loader = new FXMLLoader(location);
            Parent root = loader.load();
            Object controller = loader.getController();
            definition.wire().accept(controller);
            return new LoadedModule(route, root, controller);
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Unable to load module route '" + route + "'", error);
        }
    }

    private static String normalizeRoute(String route) {
        Objects.requireNonNull(route, "route");
        String normalized = route.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("route must not be blank");
        return normalized;
    }

    private static String normalizeResource(String resource) {
        Objects.requireNonNull(resource, "resource");
        String normalized = resource.trim();
        if (normalized.isEmpty() || !normalized.startsWith("/")) {
            throw new IllegalArgumentException("resource must be an absolute classpath path");
        }
        return normalized;
    }

    private record Definition(String resource, Consumer<Object> wire) { }

    private record LoadedModule(String route, Parent root, Object controller) { }
}
