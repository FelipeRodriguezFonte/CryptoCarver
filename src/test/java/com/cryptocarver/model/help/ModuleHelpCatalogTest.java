package com.cryptocarver.model.help;

import com.cryptocarver.model.OperationRegistry;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModuleHelpCatalogTest {
    @Test void everyDeclaredOperationIsRegisteredLocalizedAndComplete() throws Exception {
        List<Properties> bundles = List.of(bundle("messages.properties"), bundle("messages_en.properties"),
                bundle("messages_es.properties"));
        assertFalse(ModuleHelpCatalog.specs().isEmpty());
        for (OperationHelpDefinition definition : ModuleHelpCatalog.definitions()) {
            String id = definition.operationId();
            assertTrue(OperationRegistry.getInstance().getById(id).isPresent(), id);
            assertEquals(id, OperationHelpRegistry.getInstance().resolve(id).operationId());
            assertFalse(OperationHelpRegistry.getInstance().resolve(id).actions().isEmpty(), id);
            assertTrue(definition.complete(), id);
            assertFalse(definition.actions().isEmpty(), id);
            assertKeys(bundles, definition.purposeKey(), "help.operation." + id + ".title");
            for (ActionHelp action : definition.actions()) {
                assertKeys(bundles, action.titleKey(), action.descriptionKey());
                for (FieldHelp field : action.fields()) assertKeys(bundles, field.labelKey(), field.descriptionKey());
            }
        }
    }

    @Test void spanishAndEnglishTextsDifferForEveryDescription() throws Exception {
        Properties english = bundle("messages.properties"), spanish = bundle("messages_es.properties");
        for (OperationHelpDefinition definition : ModuleHelpCatalog.definitions()) {
            assertNotEquals(english.getProperty(definition.purposeKey()), spanish.getProperty(definition.purposeKey()),
                    definition.operationId());
            for (ActionHelp action : definition.actions())
                assertNotEquals(english.getProperty(action.descriptionKey()), spanish.getProperty(action.descriptionKey()),
                        action.descriptionKey());
        }
    }

    @Test void everyFieldIsUsedAndEveryControlExistsInTheModuleScreen() throws Exception {
        for (ModuleHelpCatalog.Spec spec : ModuleHelpCatalog.specs()) {
            String fxml;
            try (InputStream stream = getClass().getResourceAsStream("/fxml/" + spec.fxml())) {
                assertNotNull(stream, spec.fxml());
                fxml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            Set<String> used = new HashSet<>();
            for (ModuleHelpCatalog.Action action : spec.actions()) {
                used.addAll(action.fieldIds());
                for (String control : action.controls()) assertControl(fxml, control, spec.id() + "/" + action.id());
            }
            for (ModuleHelpCatalog.Field field : spec.fields()) {
                assertTrue(used.contains(field.id()), spec.id() + " never uses field " + field.id());
                assertFalse(spec.actionFor(field.id(), "").isBlank(), field.id());
                for (String control : field.controls()) assertControl(fxml, control, spec.id() + "/" + field.id());
            }
        }
    }

    private static void assertControl(String fxml, String id, String owner) {
        assertTrue(fxml.contains("fx:id=\"" + id + "\"") || fxml.contains(" id=\"" + id + "\""),
                owner + " names a control that the screen does not have: " + id);
    }

    private static void assertKeys(List<Properties> bundles, String... keys) {
        for (String key : keys) for (Properties bundle : bundles) {
            String value = bundle.getProperty(key);
            assertTrue(value != null && !value.isBlank(), "Missing help text: " + key);
        }
    }

    private static Properties bundle(String name) throws Exception {
        Properties properties = new Properties();
        try (InputStream stream = ModuleHelpCatalogTest.class.getResourceAsStream("/i18n/" + name)) {
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
