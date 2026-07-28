package com.cryptocarver.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SafeOperationTemplateTest {

    @TempDir
    Path tempDir;

    private Path storeFile;
    private PersonalTemplateStore store;

    @BeforeEach
    void setUp() {
        storeFile = tempDir.resolve("templates.json");
        store = new PersonalTemplateStore(storeFile);
    }

    @Test
    void testSaveAndLoadPersonalTemplate() {
        SafeOperationTemplate template = new SafeOperationTemplate(
                "AES-GCM Base64 Setup",
                "Cipher",
                "Personal safe template for AES-GCM",
                Map.of(
                        "symmetricAlgorithmCombo", "AES-256",
                        "cipherModeCombo", "GCM",
                        "paddingCombo", "NoPadding"
                )
        );

        SafeOperationTemplate saved = store.saveTemplate(template);
        assertNotNull(saved.getId());
        assertEquals("AES-GCM Base64 Setup", saved.getName());

        // Reload store from disk
        PersonalTemplateStore reloaded = new PersonalTemplateStore(storeFile);
        var templates = reloaded.getTemplatesForModule("Cipher");
        assertEquals(1, templates.size());
        assertEquals("AES-GCM Base64 Setup", templates.get(0).getName());
        assertEquals("GCM", templates.get(0).getParameters().get("cipherModeCombo"));
    }

    @Test
    void testExportAndImportSafeTemplate() throws IOException {
        SafeOperationTemplate template = new SafeOperationTemplate(
                "SHA-512 Hex Setup",
                "Hashing",
                "Personal SHA-512 configuration",
                Map.of("hashAlgorithmCombo", "SHA-512")
        );
        store.saveTemplate(template);

        Path exportFile = tempDir.resolve("sha512_template.json");
        store.exportTemplate(template, exportFile);
        assertTrue(Files.exists(exportFile));

        String jsonContent = Files.readString(exportFile);
        assertFalse(jsonContent.contains("key"));
        assertFalse(jsonContent.contains("secret"));
        assertFalse(jsonContent.contains("input"));

        // Import into clean store
        Path cleanStoreFile = tempDir.resolve("clean_templates.json");
        PersonalTemplateStore cleanStore = new PersonalTemplateStore(cleanStoreFile);
        SafeOperationTemplate imported = cleanStore.importTemplate(exportFile);

        assertNotNull(imported);
        assertEquals("SHA-512 Hex Setup", imported.getName());
        assertEquals("Hashing", imported.getModule());
        assertEquals("SHA-512", imported.getParameters().get("hashAlgorithmCombo"));
    }

    @Test
    void testRejectMaliciousJsonWithSecretOrInput() throws IOException {
        String maliciousJson = """
                {
                  "formatVersion": "1.0",
                  "id": "test-id",
                  "name": "Malicious Template",
                  "module": "Cipher",
                  "description": "Contains secret key",
                  "parameters": {
                    "symmetricAlgorithmCombo": "AES-256",
                    "symmetricKeyField": "00112233445566778899AABBCCDDEEFF",
                    "ivField": "0102030405060708090A0B0C"
                  }
                }
                """;

        Path maliciousFile = tempDir.resolve("malicious.json");
        Files.writeString(maliciousFile, maliciousJson);

        assertThrows(IllegalArgumentException.class, () -> store.importTemplate(maliciousFile));
        assertTrue(store.getAllTemplates().isEmpty(), "Store must not contain any template after failed import");
    }

    @Test
    void testRejectRedactedSecretValue() throws IOException {
        String redactedJson = """
                {
                  "formatVersion": "1.0",
                  "id": "test-id-2",
                  "name": "Redacted Secret Template",
                  "module": "Cipher",
                  "parameters": {
                    "symmetricAlgorithmCombo": "AES-256",
                    "cipherModeCombo": "[REDACTED_SECRET]"
                  }
                }
                """;

        Path redactedFile = tempDir.resolve("redacted.json");
        Files.writeString(redactedFile, redactedJson);

        assertThrows(IllegalArgumentException.class, () -> store.importTemplate(redactedFile));
    }

    @Test
    void testRejectUnknownFields() {
        SafeOperationTemplate template = new SafeOperationTemplate();
        template.setName("Invalid Field Template");
        template.setModule("Cipher");
        template.setParameters(Map.of(
                "symmetricAlgorithmCombo", "AES-256",
                "unknownFieldCombo", "Value"
        ));

        assertThrows(IllegalArgumentException.class, () -> SafeTemplateAllowlist.validateTemplate(template));
    }

    @Test
    void testRenameAndDeleteTemplate() {
        SafeOperationTemplate template = new SafeOperationTemplate(
                "Original Name",
                "Manual Conversion",
                "Desc",
                Map.of("manualInputFormatCombo", "Text (UTF-8)")
        );
        store.saveTemplate(template);

        store.renameTemplate(template.getId(), "Renamed Template");
        assertEquals("Renamed Template", store.getTemplateById(template.getId()).orElseThrow().getName());

        assertTrue(store.deleteTemplate(template.getId()));
        assertTrue(store.getTemplateById(template.getId()).isEmpty());
    }
}
