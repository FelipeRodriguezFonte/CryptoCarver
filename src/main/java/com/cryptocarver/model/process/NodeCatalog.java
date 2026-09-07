package com.cryptocarver.model.process;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Single source of truth for process node descriptors registered in the ProcessEngine.
 */
public final class NodeCatalog {

    private NodeCatalog() {}

    /**
     * Returns all node descriptors defined by currently registered handlers.
     */
    public static List<NodeDescriptor> allDescriptors() {
        List<NodeDescriptor> result = new ArrayList<>();
        Set<String> seenTypes = new HashSet<>();
        for (ProcessNodeHandler handler : ProcessEngine.handlers()) {
            for (NodeDescriptor descriptor : handler.descriptors()) {
                if (seenTypes.add(descriptor.type())) {
                    result.add(descriptor);
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    public static List<NodeDescriptor> descriptors() {
        return allDescriptors();
    }

    public static Optional<NodeDescriptor> descriptor(String type) {
        return getDescriptor(type);
    }

    public static Optional<NodeDescriptor> getDescriptor(String type) {
        if (type == null || type.isBlank()) return Optional.empty();
        for (ProcessNodeHandler handler : ProcessEngine.handlers()) {
            for (NodeDescriptor descriptor : handler.descriptors()) {
                if (descriptor.type().equalsIgnoreCase(type)) {
                    return Optional.of(descriptor);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Returns distinct categories in insertion/logical order.
     */
    public static List<String> categories() {
        return allDescriptors().stream()
                .map(NodeDescriptor::category)
                .distinct()
                .toList();
    }

    /**
     * Returns descriptors matching the given category.
     */
    public static List<NodeDescriptor> descriptorsByCategory(String category) {
        if (category == null) return List.of();
        return allDescriptors().stream()
                .filter(d -> d.category().equalsIgnoreCase(category))
                .toList();
    }

    /**
     * Builds default configuration key-values for a given node type.
     */
    public static Map<String, String> defaultConfiguration(String type) {
        Map<String, String> config = new LinkedHashMap<>();
        getDescriptor(type).ifPresent(descriptor -> {
            for (NodeParameter param : descriptor.parameters()) {
                if (param.defaultValue() != null && !param.defaultValue().isEmpty()) {
                    config.put(param.key(), param.defaultValue());
                }
            }
        });
        return config;
    }

    /**
     * Checks if a configuration key for a specific node type is marked sensitive.
     */
    public static boolean isSensitive(String type, String key) {
        if (type == null || key == null) return false;
        return getDescriptor(type)
                .flatMap(d -> d.parameters().stream()
                        .filter(p -> p.key().equals(key))
                        .findFirst())
                .map(NodeParameter::sensitive)
                .orElse(false);
    }

    /**
     * Suffix of the marker written beside a sensitive parameter whose value the session is
     * holding in memory.
     *
     * <p>Sensitive values never reach {@link ProcessDefinition.Node#configuration}, so a handler
     * validating the live definition cannot tell "the user has not filled this in" apart from
     * "the user filled this in and we are deliberately not carrying it here". The marker carries
     * that one bit and nothing else: it is a boolean, never the value, and it is stripped on both
     * ends of {@link ProcessDefinitionCodec} so a reopened process correctly reports the secret as
     * missing again.</p>
     *
     * <p>This mirrors the existing {@code keyFromFlow} / {@code ivFromFlow} markers the engine
     * derives from the graph.</p>
     */
    public static final String SUPPLIED_SUFFIX = "FromSecrets";

    /** Configuration key of the marker that accompanies {@code parameterKey}. */
    public static String suppliedMarker(String parameterKey) {
        return parameterKey + SUPPLIED_SUFFIX;
    }

    /** True when {@code configurationKey} is a supplied-secret marker rather than real configuration. */
    public static boolean isSuppliedMarker(String configurationKey) {
        return configurationKey != null && configurationKey.endsWith(SUPPLIED_SUFFIX);
    }

    /** True when the session holds a value for {@code parameterKey} on this node. */
    public static boolean isSupplied(ProcessDefinition.Node node, String parameterKey) {
        return node != null && node.configuration != null
                && "true".equalsIgnoreCase(node.configuration.get(suppliedMarker(parameterKey)));
    }

    /**
     * Returns all parameter keys that are marked sensitive across all descriptors.
     */
    public static Set<String> allSensitiveKeys() {
        Set<String> sensitive = new LinkedHashSet<>();
        for (NodeDescriptor desc : allDescriptors()) {
            for (NodeParameter param : desc.parameters()) {
                if (param.sensitive()) {
                    sensitive.add(param.key());
                }
            }
        }
        // Always include known hardcoded legacy sensitive keys for defence-in-depth
        sensitive.add("key");
        sensitive.add("manualKey");
        sensitive.add("keystorePassword");
        sensitive.add("keyPassword");
        sensitive.add("wssPassword");
        return Collections.unmodifiableSet(sensitive);
    }
}
