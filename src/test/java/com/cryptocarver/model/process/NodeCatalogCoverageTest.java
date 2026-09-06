package com.cryptocarver.model.process;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeCatalogCoverageTest {

    private static final Set<String> OLA_5B1_TYPES = Set.of(
            "CONCAT",
            "SLICE",
            "PAD",
            "UNPAD",
            "XOR",
            "ASSERT_EQUALS",
            "BASE32_ENCODE",
            "BASE32_DECODE",
            "BASE58_ENCODE",
            "BASE58_DECODE",
            "BASE58CHECK_ENCODE",
            "BASE58CHECK_DECODE",
            "EBCDIC_ENCODE",
            "EBCDIC_DECODE",
            "COMPRESS",
            "DECOMPRESS",
            "CHARSET_CONVERT",
            "ASN1_DECODE",
            "CHECK_DIGIT_CALC",
            "CHECK_DIGIT_VERIFY",
            "MODULAR_ARITHMETIC",
            "UUID_GENERATE",
            "BYTE_STATISTICS"
    );

    private static final Set<String> OLA_5B2A_TYPES = Set.of(
            "KCV", "KEY_SPLIT_XOR", "KEY_COMBINE_XOR", "PARITY_ADJUST", "PARITY_CHECK",
            "KDF_HKDF", "KDF_SP800_108", "KDF_X963", "KDF_SCRYPT", "KDF_ARGON2",
            "AES_KEYWRAP_3394", "AES_UNWRAP_3394", "AES_KEYWRAP_5649", "AES_UNWRAP_5649",
            "TR31_WRAP", "TR31_UNWRAP", "TR31_PARSE_HEADER", "ICSF_TOKEN_PARSE",
            "KEYPAIR_GENERATE", "KEY_MATERIAL_INSPECT");

    @Test
    void testAllOla5B1TypesAreDeclaredInNodeCatalog() {
        List<NodeDescriptor> allDescriptors = NodeCatalog.allDescriptors();
        Set<String> declaredTypes = allDescriptors.stream()
                .map(NodeDescriptor::type)
                .collect(Collectors.toSet());

        for (String expectedType : OLA_5B1_TYPES) {
            assertTrue(declaredTypes.contains(expectedType),
                    "NodeCatalog must declare type from Ola 5B.1: " + expectedType);

            NodeDescriptor desc = NodeCatalog.getDescriptor(expectedType).orElse(null);
            assertNotNull(desc, "Descriptor for " + expectedType + " must be present");
            assertNotNull(desc.labelKey(), "labelKey must not be null for " + expectedType);
            assertFalse(desc.labelKey().isBlank(), "labelKey must not be blank for " + expectedType);
            assertNotNull(desc.descriptionKey(), "descriptionKey must not be null for " + expectedType);
            assertFalse(desc.descriptionKey().isBlank(), "descriptionKey must not be blank for " + expectedType);
            assertNotNull(desc.category(), "category must not be null for " + expectedType);
            assertFalse(desc.category().isBlank(), "category must not be blank for " + expectedType);
            assertNotNull(desc.icon(), "icon must not be null for " + expectedType);
        }
    }

    @Test
    void testOla5B1NodeCountMatchesExactRequirement() {
        long count = NodeCatalog.allDescriptors().stream()
                .filter(d -> OLA_5B1_TYPES.contains(d.type()))
                .count();
        assertTrue(count == 23, "Ola 5B.1 must deliver exactly 23 node types, found: " + count);
    }

    @Test
    void testAllOla5B2aTypesAreDeclaredAndCountedExactly() {
        Set<String> declaredTypes = NodeCatalog.allDescriptors().stream()
                .map(NodeDescriptor::type).collect(Collectors.toSet());
        assertTrue(declaredTypes.containsAll(OLA_5B2A_TYPES),
                "NodeCatalog must declare every 5B.2a key type: " + OLA_5B2A_TYPES);
        long count = NodeCatalog.allDescriptors().stream()
                .filter(d -> OLA_5B2A_TYPES.contains(d.type())).count();
        assertTrue(count == 20, "Ola 5B.2a must deliver exactly 20 node types, found: " + count);
    }
}
