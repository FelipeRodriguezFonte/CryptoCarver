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

    private static final Set<String> OLA_5B2B_TYPES = Set.of(
            "PIN_BLOCK_ENCODE", "PIN_BLOCK_DECODE", "PIN_BLOCK_TRANSLATE",
            "CVV_GENERATE", "CVV_VERIFY", "DCVV_GENERATE", "DCVV_VERIFY",
            "PVV_GENERATE", "PVV_VERIFY", "IBM3624_OFFSET", "DUKPT_TDES_DERIVE", "DUKPT_AES_DERIVE", "DUKPT_PIN_CRYPT",
            "EMV_ICC_MASTER_KEY", "EMV_SESSION_KEY", "EMV_ARQC_GENERATE", "EMV_ARQC_VERIFY", "EMV_ARPC",
            "EMV_TLV_PARSE", "TRACK2_ENCODE", "TRACK2_PARSE", "COMPONENT_SELECT");

    private static final Set<String> OLA_5B3A_TYPES = Set.of(
            "JWS_SIGN", "JWS_VERIFY", "JWS_DETACHED_SIGN", "JWS_DETACHED_VERIFY",
            "JWE_ENCRYPT", "JWE_DECRYPT", "JWT_INSPECT", "COSE_SIGN1", "COSE_VERIFY1",
            "COSE_MAC0", "COSE_VERIFY_MAC0", "COSE_ENCRYPT0", "COSE_DECRYPT0");

    private static final Set<String> OLA_5B3B_TYPES = Set.of(
            "CMS_SIGN", "CMS_VERIFY", "CMS_ENVELOPE", "CMS_DEVELOPE", "CADES_BES_SIGN",
            "XMLDSIG_SIGN", "XMLDSIG_VERIFY", "PADES_SIGN", "PADES_VERIFY",
            "OPENPGP_ENCRYPT", "OPENPGP_DECRYPT", "OPENPGP_SIGN", "OPENPGP_VERIFY",
            "PQC_KEYPAIR_GENERATE", "PQC_SIGN", "PQC_VERIFY", "PQC_KEM_ENCAPSULATE", "PQC_KEM_DECAPSULATE",
            "CERT_PARSE", "CERT_SELF_SIGNED_GENERATE", "CERT_VALIDATE");

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

    @Test
    void testAllOla5B2bTypesAreDeclaredAndCountedExactly() {
        Set<String> declaredTypes = NodeCatalog.allDescriptors().stream()
                .map(NodeDescriptor::type).collect(Collectors.toSet());
        assertTrue(declaredTypes.containsAll(OLA_5B2B_TYPES),
                "NodeCatalog must declare every 5B.2b payment type: " + OLA_5B2B_TYPES);
        long count = NodeCatalog.allDescriptors().stream()
                .filter(d -> OLA_5B2B_TYPES.contains(d.type())).count();
        assertTrue(count == 22, "Ola 5B.2b plus COMPONENT_SELECT must deliver exactly 22 node types, found: " + count);
    }

    @Test
    void testAllOla5B3TypesAreDeclaredAndCountedExactly() {
        Set<String> declared = NodeCatalog.allDescriptors().stream().map(NodeDescriptor::type).collect(Collectors.toSet());
        assertTrue(declared.containsAll(OLA_5B3A_TYPES));
        assertTrue(declared.containsAll(OLA_5B3B_TYPES));
        assertTrue(NodeCatalog.allDescriptors().stream().filter(d -> OLA_5B3A_TYPES.contains(d.type())).count() == 13,
                "5B.3a must contain exactly 13 node types");
        assertTrue(NodeCatalog.allDescriptors().stream().filter(d -> OLA_5B3B_TYPES.contains(d.type())).count() == 21,
                "5B.3b must contain exactly 21 node types");
    }
}
