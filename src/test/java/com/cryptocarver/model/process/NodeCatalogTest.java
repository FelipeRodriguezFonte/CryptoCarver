package com.cryptocarver.model.process;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeCatalogTest {

    private static final Properties MESSAGES_EN = new Properties();
    private static final Properties MESSAGES_ES = new Properties();

    @BeforeAll
    static void loadBundles() throws Exception {
        try (InputStream in = NodeCatalogTest.class.getResourceAsStream("/i18n/messages.properties")) {
            assertNotNull(in, "messages.properties must be present");
            MESSAGES_EN.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        try (InputStream in = NodeCatalogTest.class.getResourceAsStream("/i18n/messages_es.properties")) {
            assertNotNull(in, "messages_es.properties must be present");
            MESSAGES_ES.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void allSupportedTypesHaveDescriptors() {
        List<ProcessNodeHandler> handlers = ProcessEngine.handlers();
        assertFalse(handlers.isEmpty(), "Handlers must not be empty");

        for (ProcessNodeHandler handler : handlers) {
            for (String type : handler.supportedTypes()) {
                var descriptorOpt = NodeCatalog.descriptor(type);
                assertTrue(descriptorOpt.isPresent(),
                        "Missing NodeDescriptor for type '" + type + "' in handler " + handler.getClass().getSimpleName());
                NodeDescriptor descriptor = descriptorOpt.get();
                assertEquals(type, descriptor.type());
                assertNotNull(descriptor.category());
                assertNotNull(descriptor.labelKey());
                assertNotNull(descriptor.descriptionKey());
            }
        }
    }

    @Test
    void noDuplicateTypesAcrossHandlers() {
        List<ProcessNodeHandler> handlers = ProcessEngine.handlers();
        Set<String> seenTypes = new HashSet<>();

        for (ProcessNodeHandler handler : handlers) {
            for (String type : handler.supportedTypes()) {
                boolean added = seenTypes.add(type);
                assertTrue(added, "Duplicate node type detected across handlers: " + type);
            }
        }
    }

    @Test
    void parameterKeysAreUniqueAndValidPerDescriptor() {
        for (NodeDescriptor descriptor : NodeCatalog.descriptors()) {
            Set<String> keys = new HashSet<>();
            for (NodeParameter param : descriptor.parameters()) {
                assertNotNull(param.key(), "Parameter key cannot be null in " + descriptor.type());
                assertFalse(param.key().isBlank(), "Parameter key cannot be blank in " + descriptor.type());
                boolean added = keys.add(param.key());
                assertTrue(added, "Duplicate parameter key '" + param.key() + "' in descriptor " + descriptor.type());
            }
        }
    }

    @Test
    void allI18nKeysExistInBothPropertiesFiles() {
        for (NodeDescriptor descriptor : NodeCatalog.descriptors()) {
            assertI18nKeyExists(descriptor.labelKey(), "labelKey for " + descriptor.type());
            assertI18nKeyExists(descriptor.descriptionKey(), "descriptionKey for " + descriptor.type());

            for (NodeParameter param : descriptor.parameters()) {
                assertI18nKeyExists(param.labelKey(), "labelKey for param " + param.key() + " in " + descriptor.type());
                if (param.helpKey() != null) {
                    assertI18nKeyExists(param.helpKey(), "helpKey for param " + param.key() + " in " + descriptor.type());
                }
            }
        }
    }

    private void assertI18nKeyExists(String key, String context) {
        assertTrue(MESSAGES_EN.containsKey(key),
                "Key '" + key + "' missing in messages.properties (" + context + ")");
        assertTrue(MESSAGES_ES.containsKey(key),
                "Key '" + key + "' missing in messages_es.properties (" + context + ")");
    }
}
