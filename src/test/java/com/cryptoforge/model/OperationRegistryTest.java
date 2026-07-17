package com.cryptoforge.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class OperationRegistryTest {

    @Test
    void testRegistryPopulated() {
        List<OperationDescriptor> all = OperationRegistry.getInstance().getAll();
        assertNotNull(all);
        assertFalse(all.isEmpty(), "Registry should have operations");
    }

    @Test
    void testUniqueIdsAndPaths() {
        List<OperationDescriptor> all = OperationRegistry.getInstance().getAll();

        Set<String> ids = all.stream().map(OperationDescriptor::getId).collect(Collectors.toSet());
        assertEquals(all.size(), ids.size(), "All operation IDs must be unique");

        Set<String> paths = all.stream().map(OperationDescriptor::getNavigationPath).collect(Collectors.toSet());
        assertEquals(all.size(), paths.size(), "All navigation paths must be unique");
    }

    @Test
    void testRequiredOperationsPresent() {
        OperationRegistry registry = OperationRegistry.getInstance();

        assertTrue(registry.search("XAdES").size() > 0, "Missing XAdES");
        assertTrue(registry.search("RFC 3161").size() > 0, "Missing RFC 3161");
        assertTrue(registry.search("ML-KEM").size() > 0, "Missing ML-KEM");
        assertTrue(registry.search("TR-31").size() > 0, "Missing TR-31");
        assertTrue(registry.search("EMV").size() > 0, "Missing EMV");
        assertTrue(registry.search("EBCDIC").size() > 0, "Missing EBCDIC");
    }

    @Test
    void testSearchCapabilities() {
        OperationRegistry registry = OperationRegistry.getInstance();

        // By Title
        assertFalse(registry.search("Hashing").isEmpty());
        // By Alias
        assertFalse(registry.search("AES").isEmpty());
        assertFalse(registry.search("DES").isEmpty());
        // By Subtitle (or parts of it)
        assertFalse(registry.search("Encrypt/Decrypt files").isEmpty());
        // Case insensitivity
        assertFalse(registry.search("ebcdic").isEmpty());
        assertFalse(registry.search("EBCDIC").isEmpty());
    }

    @Test
    void testUnmodifiableList() {
        List<OperationDescriptor> all = OperationRegistry.getInstance().getAll();
        assertThrows(UnsupportedOperationException.class, () -> all.remove(0), "Registry should return unmodifiable collections");
    }

    @Test
    void testGenerateCatalogMarkdownString() throws Exception {
        String markdown = OperationRegistry.getInstance().toMarkdownCatalog();
        assertNotNull(markdown);
        assertTrue(markdown.contains("CryptoCarver Operations Catalog"));
        assertTrue(markdown.contains("XAdES"));
        assertTrue(markdown.contains("ML-KEM"));

        // Ensure docs/OPERATIONS_CATALOG.md is up to date
        java.nio.file.Path catalogPath = java.nio.file.Paths.get("docs", "OPERATIONS_CATALOG.md");
        if (java.nio.file.Files.exists(catalogPath)) {
            String currentContent = java.nio.file.Files.readString(catalogPath);
            assertEquals(markdown, currentContent, "docs/OPERATIONS_CATALOG.md is out of date. Please run scripts/generate-operations-catalog.sh to regenerate it.");
        }
    }

    @Test
    void testAliasImmutability() {
        OperationDescriptor desc = new OperationDescriptor("test_id", "Title", "Category", "Desc", "Icon",
                OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.NONE, "Path", List.of("Alias1", "Alias2"));

        List<String> aliases = desc.getAliases();
        assertThrows(UnsupportedOperationException.class, () -> aliases.add("NewAlias"), "Aliases list should be unmodifiable");
    }

    @Test
    void testRejectDuplicateIds() {
        OperationRegistry registry = OperationRegistry.getInstance();
        OperationDescriptor duplicate = new OperationDescriptor("op_sym_ciphers", "Duplicate", "Category", "Desc", "Icon",
                OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.NONE, "Path", List.of());

        assertThrows(IllegalArgumentException.class, () -> registry.register(duplicate), "Registry should reject duplicate IDs");
    }
}
