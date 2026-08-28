package com.cryptocarver.ui;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Declarative mapping between operation names and their JavaFX destination.
 *
 * <p>This keeps navigation aliases and accordion-pane names out of the main
 * controller. Cryptographic operation metadata remains in OperationRegistry;
 * this registry only describes how the desktop shell reveals a view.</p>
 */
public final class UiNavigationRegistry {

    public enum Module {
        JOSE,
        COSE,
        EPOCH_CONVERTER,
        JSON_FORMATTER,
        KEYS_SYMMETRIC,
        KEYS_ASYMMETRIC,
        CERTIFICATES,
        GENERIC,
        POST_QUANTUM,
        XML_SECURITY,
        WSS_SECURITY,
        EMV,
        HISTORY,
        CLIPBOARD_SHELF,
        SAVED_SESSIONS,
        CIPHER,
        AUTHENTICATION,
        PAYMENTS
    }

    public enum Variant {
        NONE,
        ASN1_DECODE,
        ASN1_ENCODE,
        HISTORY_EXPORT
    }

    public record Route(Module module, String section, Variant variant) {
        public Route(Module module, String section) {
            this(module, section, Variant.NONE);
        }
    }

    private static final Map<String, Route> ROUTES = buildRoutes();

    private UiNavigationRegistry() {
    }

    public static Optional<Route> resolve(String operation) {
        if (operation == null || operation.isBlank()) return Optional.empty();
        String candidate = operation.trim();
        Route route = ROUTES.get(candidate);
        return Optional.ofNullable(route != null ? route : resolveLegacyExecution(candidate));
    }

    /**
     * Routes result labels created by older releases, which stored a detailed
     * execution name instead of the workspace that produced it. New records
     * persist their workspace separately in {@code HistoryCommand}.
     */
    private static Route resolveLegacyExecution(String operation) {
        if (operation.startsWith("Derive - ") || operation.equals("KDF Derivation")) {
            return new Route(Module.KEYS_SYMMETRIC, "Key Derivation");
        }
        if (operation.startsWith("Validate - ")) {
            return new Route(Module.KEYS_SYMMETRIC, "Validation & KCV");
        }
        if (operation.startsWith("Split - ") || operation.startsWith("Combine - ")) {
            return new Route(Module.KEYS_SYMMETRIC, "Key Sharing");
        }
        if (operation.startsWith("AES Key ")) {
            return new Route(Module.KEYS_SYMMETRIC, "AES Key Wrap");
        }
        if (operation.startsWith("Wrap Key - ") || operation.startsWith("Unwrap Key - ")) {
            return new Route(Module.KEYS_SYMMETRIC, "TR-31 Key Blocks");
        }
        if (operation.startsWith("Generate ECDSA F(p) - ")) {
            return new Route(Module.KEYS_ASYMMETRIC, "ECDSA Key Generation");
        }
        if (operation.startsWith("Generate Certificate - ")) {
            return new Route(Module.CERTIFICATES, "Generate Certificate");
        }
        if (operation.startsWith("Format Workbench: ")) {
            return new Route(Module.GENERIC, "Key & Certificate Format Workbench");
        }
        return null;
    }

    static Map<String, Route> routes() {
        return ROUTES;
    }

    private static Map<String, Route> buildRoutes() {
        Map<String, Route> routes = new LinkedHashMap<>();

        add(routes, new Route(Module.JOSE, null),
                "JWT (Signed)", "JWE (Encrypted)", "JWK (Keys)", "JWA (Algorithms)",
                "Generate JWE", "Decrypt JWE", "Generate JWT", "Validate JWT",
                "Generate Nested JWT", "Token Inspector", "PEM to JWK", "JWK to PEM",
                "JWK Thumbprint", "JWKS Rotate Key", "Load JWKS File",
                "Import Key (PEM)", "Import Key (JSON)");
        add(routes, new Route(Module.COSE, null),
                "COSE Sign1", "COSE Verify1", "COSE MAC0", "COSE Verify MAC0",
                "COSE Encrypt0", "COSE Decrypt0");
        add(routes, new Route(Module.EPOCH_CONVERTER, null), "Epoch Converter");
        add(routes, new Route(Module.JSON_FORMATTER, null), "JSON Formatter");

        add(routes, new Route(Module.KEYS_SYMMETRIC, "Key Lab"), "Key Lab", "Secure Key Inventory");
        add(routes, new Route(Module.KEYS_SYMMETRIC, "Key Generation"),
                "Key Generation", "Generate Symmetric Key", "Symmetric Keys");
        add(routes, new Route(Module.KEYS_SYMMETRIC, "Validation & KCV"),
                "Validation & KCV", "Validate Symmetric Key");
        add(routes, new Route(Module.KEYS_SYMMETRIC, "Key Material Inspector"), "Key Material Inspector");
        add(routes, new Route(Module.KEYS_SYMMETRIC, "KeyStore Inspector"), "KeyStore Inspector");
        add(routes, new Route(Module.KEYS_SYMMETRIC, "PKCS#11 Token"), "PKCS#11 Token");
        add(routes, new Route(Module.KEYS_SYMMETRIC, "PKCS#11 Profiles"), "PKCS#11 Profiles");
        // CCA native key tokens. The section name keeps "ICSF" in it because
        // KeysController routes the included pane by that word.
        add(routes, new Route(Module.KEYS_SYMMETRIC, "ICSF / CCA Key Token Analyzer"),
                "ICSF / CCA Key Token Analyzer", "ICSF Key Token Analyzer", "ICSF Key Token");
        add(routes, new Route(Module.KEYS_SYMMETRIC, "ICSF / CCA Batch Analysis"),
                "ICSF / CCA Batch Analysis", "ICSF Batch Analysis", "ICSF Batch");
        add(routes, new Route(Module.KEYS_SYMMETRIC, "Compare Public / Private Key"),
                "Compare Public / Private Key");
        add(routes, new Route(Module.KEYS_SYMMETRIC, "Key Sharing"),
                "Key Sharing (XOR Split/Combine)", "Split Key", "Combine Components");
        add(routes, new Route(Module.KEYS_SYMMETRIC, "Key Derivation"),
                "Key Derivation (KDF)", "Derive Key");
        add(routes, new Route(Module.KEYS_SYMMETRIC, "AES Key Wrap"), "AES Key Wrap");
        add(routes, new Route(Module.KEYS_SYMMETRIC, "TR-31 Key Blocks"),
                "TR-31 Key Blocks", "TR-31 Export", "TR-31 Import", "TR-31 Parse");
        add(routes, new Route(Module.KEYS_SYMMETRIC, "RSA Key Exchange"), "RSA Key Exchange");
        add(routes, new Route(Module.KEYS_SYMMETRIC, "TR-34 Key Distribution"), "TR-34 Key Distribution");

        add(routes, new Route(Module.KEYS_ASYMMETRIC, "RSA Key Generation"),
                "RSA Key Generation", "Generate RSA Key");
        add(routes, new Route(Module.KEYS_ASYMMETRIC, "ECDSA Key Generation"),
                "ECDSA Key Generation", "Generate ECDSA Key");
        add(routes, new Route(Module.KEYS_ASYMMETRIC, "DSA Key Generation"),
                "DSA Key Generation", "Generate DSA Key");
        add(routes, new Route(Module.KEYS_ASYMMETRIC, "EdDSA Key Generation"),
                "EdDSA Key Generation", "Generate EdDSA Key");

        add(routes, new Route(Module.CERTIFICATES, "Generate Certificate"), "Generate Certificate");
        add(routes, new Route(Module.CERTIFICATES, "Issue Certificate from CSR"), "Issue Certificate from CSR");
        add(routes, new Route(Module.CERTIFICATES, "Compare Certificates"), "Compare Certificates");
        add(routes, new Route(Module.CERTIFICATES, "Parse Certificate"), "Parse Certificate");
        add(routes, new Route(Module.CERTIFICATES, "Validate Certificate"), "Validate Certificate");
        add(routes, new Route(Module.CERTIFICATES, "Validate Chain"),
                "Validate Chain", "Validate Cert Chain", "Certificate Chain");
        add(routes, new Route(Module.CERTIFICATES, "PAdES PDF Signatures"),
                "PAdES PDF Signatures", "PAdES", "PDF Signatures");
        add(routes, new Route(Module.CERTIFICATES, "ASiC-S Containers"),
                "ASiC-S Containers", "ASiC-S", "ASiC");
        add(routes, new Route(Module.CERTIFICATES, "CMS Operations"),
                "CMS Sign/Verify", "CMS Encrypt/Decrypt", "CMS/PKCS#7 Operations",
                "CMS Sign", "CMS Verify", "CMS Encrypt", "CMS Decrypt");
        add(routes, new Route(Module.CERTIFICATES, "Inspect / Validate CMS"), "CMS Inspector");
        add(routes, new Route(Module.CERTIFICATES, "ASN.1 Decoder", Variant.ASN1_DECODE),
                "ASN.1 Decoder", "Decode ASN.1", "ASN.1 Parse");
        add(routes, new Route(Module.CERTIFICATES, "ASN.1 Decoder", Variant.ASN1_ENCODE),
                "Encode ASN.1", "ASN.1 Encode");
        add(routes, new Route(Module.CERTIFICATES, "ASN.1 Decoder"), "ASN.1");

        add(routes, new Route(Module.GENERIC, "Hashing"), "Hashing");
        add(routes, new Route(Module.GENERIC, "File Conversion"), "Encoding/Conversion", "File Conversion");
        add(routes, new Route(Module.GENERIC, "Manual Conversion"),
                "Manual Conversion", "Decode EBCDIC", "Encode EBCDIC");
        add(routes, new Route(Module.GENERIC, "Compressed Hex"), "Compressed Hex (2-row)");
        add(routes, new Route(Module.GENERIC, "Batch Runner"), "Batch Runner");
        add(routes, new Route(Module.GENERIC, "Process Designer"), "Process Designer");
        add(routes, new Route(Module.GENERIC, "Random Generator"),
                "Random Number Generator", "Random Generation");
        add(routes, new Route(Module.GENERIC, "UUID Generator"), "Generate UUID");
        add(routes, new Route(Module.GENERIC, "Check Digits"), "Check Digits", "Check Digits: Validate");
        add(routes, new Route(Module.GENERIC, "Modular Arithmetic"), "Modular Arithmetic");
        add(routes, new Route(Module.GENERIC, "Key & Certificate Format Workbench"),
                "Key & Certificate Format Workbench");
        add(routes, new Route(Module.GENERIC, "Crypto Envelope Inspector"), "Crypto Envelope Inspector");

        add(routes, new Route(Module.POST_QUANTUM, "Key Generation"),
                "Post-Quantum Cryptography", "PQC Key Generation", "PQC Key Import",
                "PQC Key Export", "Kyber (ML-KEM)", "Dilithium (ML-DSA)");
        add(routes, new Route(Module.POST_QUANTUM, "Sign"), "PQC Sign/Verify", "PQC Sign", "PQC Verify");
        add(routes, new Route(Module.POST_QUANTUM, "KEM Educational Demo"),
                "ML-KEM Encapsulate", "ML-KEM Decapsulate");

        add(routes, new Route(Module.XML_SECURITY, "Sign XML"),
                "XML Security", "Sign XML (XAdES)", "XAdES Sign", "Save XAdES XML");
        add(routes, new Route(Module.XML_SECURITY, "Verify XML"), "Verify XML (XAdES)", "XAdES Verify");
        add(routes, new Route(Module.XML_SECURITY, "Inspect Signed XML"), "Inspect Signed XML");
        add(routes, new Route(Module.XML_SECURITY, "RFC 3161 Timestamp"), "RFC 3161 Timestamp");
        add(routes, new Route(Module.WSS_SECURITY, "Sign SOAP"), "Sign SOAP (WSS)");
        add(routes, new Route(Module.WSS_SECURITY, "Sign SOAP"), "WSS Security");
        add(routes, new Route(Module.WSS_SECURITY, "Add UsernameToken"), "Add UsernameToken (WSS)");
        add(routes, new Route(Module.WSS_SECURITY, "Encrypt SOAP Body"), "Encrypt SOAP Body (WSS)");
        add(routes, new Route(Module.WSS_SECURITY, "Decrypt SOAP Body"), "Decrypt SOAP Body (WSS)");
        add(routes, new Route(Module.WSS_SECURITY, "Verify SOAP"), "Verify SOAP (WSS)");
        add(routes, new Route(Module.WSS_SECURITY, "Verify UsernameToken"), "Verify UsernameToken (WSS)");

        add(routes, new Route(Module.EMV, "Session Key"), "EMV Operations", "Session Key Derivation", "EMV Tool");
        add(routes, new Route(Module.EMV, "ARQC"), "ARQC Generation");
        add(routes, new Route(Module.EMV, "ARPC"), "ARPC Generation");
        add(routes, new Route(Module.EMV, "Track 2"),
                "Track 2 Encoding", "Track 2 Decoding", "Track 2 Operations");
        add(routes, new Route(Module.EMV, "EMV TLV Inspector"), "EMV TLV Inspector");

        add(routes, new Route(Module.HISTORY, null), "Recent Operations");
        add(routes, new Route(Module.HISTORY, null, Variant.HISTORY_EXPORT), "Export History");
        add(routes, new Route(Module.CLIPBOARD_SHELF, null), "Clipboard Shelf");
        add(routes, new Route(Module.SAVED_SESSIONS, null), "Saved Sessions");

        add(routes, new Route(Module.CIPHER, "Symmetric Cipher"),
                "Symmetric Ciphers", "AES Encryption", "DES/3DES Encryption", "Symmetric Encryption",
                "Modes & Padding", "Symmetric Encrypt", "Symmetric Decrypt");
        add(routes, new Route(Module.CIPHER, "File Cipher"), "File Cipher (Streaming)");
        add(routes, new Route(Module.CIPHER, "OpenPGP"), "OpenPGP (GPG Compatible)", "OpenPGP", "GPG");
        add(routes, new Route(Module.CIPHER, "Asymmetric Cipher"),
                "Asymmetric Ciphers", "RSA Encryption", "ECC Encryption",
                "Asymmetric Encrypt", "Asymmetric Decrypt");

        add(routes, new Route(Module.AUTHENTICATION, "Digital Signatures"),
                "Digital Signatures", "Sign", "Verify Signature");
        add(routes, new Route(Module.AUTHENTICATION, "MAC"),
                "Message Authentication Codes", "MAC", "Generate MAC", "Verify MAC", "MAC Generated", "MAC Verified");

        add(routes, new Route(Module.PAYMENTS, "Clear PIN Blocks"),
                "Clear PIN Blocks", "Encode PIN Block", "Decode PIN Block",
                "PIN Block Operations", "PIN Block Encoding", "PIN Block Decoding",
                "PIN Block Encoded", "PIN Block Decoded");
        add(routes, new Route(Module.PAYMENTS, "Clear PIN Blocks"), "Payments");
        add(routes, new Route(Module.PAYMENTS, "Encrypted PIN Blocks"),
                "Encrypted PIN Blocks", "ISO PIN Blocks", "ISO 0", "ISO 2");
        add(routes, new Route(Module.PAYMENTS, "PIN Generation"),
                "PIN Generation", "PIN Verification", "IBM 3624", "Generate PIN", "Verify PIN");
        add(routes, new Route(Module.PAYMENTS, "DUKPT KSN"), "DUKPT TDES", "DUKPT TDES / AES");
        add(routes, new Route(Module.PAYMENTS, "CVV Operations"),
                "CVV Operations", "CVV Generation");
        add(routes, new Route(Module.PAYMENTS, "Encrypted PIN Blocks"),
                "Encrypted PIN Block Operations", "Encrypted PIN Block Encoding", "Encrypted PIN Block Decoding",
                "Encrypted PIN Block Encoded", "Encrypted PIN Block Decoded");

        return Map.copyOf(routes);
    }

    private static void add(Map<String, Route> routes, Route route, String... operations) {
        for (String operation : operations) {
            Route previous = routes.putIfAbsent(operation, route);
            if (previous != null) {
                throw new IllegalStateException("Duplicate UI route: " + operation);
            }
        }
    }
}
