package com.cryptoforge.model;

import java.util.*;
import java.util.stream.Collectors;

public class OperationRegistry {

    private final Map<String, OperationDescriptor> operations = new LinkedHashMap<>();

    private static final OperationRegistry INSTANCE = new OperationRegistry();

    public static OperationRegistry getInstance() {
        return INSTANCE;
    }

    private OperationRegistry() {
        // Cipher -> Symmetric
        register(new OperationDescriptor("op_sym_ciphers", "Symmetric Ciphers", "Cipher", "Encrypt/Decrypt with symmetric keys", "🔒", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.HIGH, "Symmetric Ciphers", Arrays.asList("AES", "DES", "3DES")));
        register(new OperationDescriptor("op_sym_file", "File Cipher (Streaming)", "Cipher", "Encrypt/Decrypt files", "📄", OperationDescriptor.Status.EXPERIMENTAL, OperationDescriptor.SecretRisk.HIGH, "File Cipher (Streaming)", Collections.emptyList()));
        register(new OperationDescriptor("op_openpgp", "OpenPGP (GPG Compatible)", "Cipher", "Encrypt/decrypt and sign ASCII-armored OpenPGP data", "🔐", OperationDescriptor.Status.EXPERIMENTAL, OperationDescriptor.SecretRisk.HIGH, "OpenPGP (GPG Compatible)", Arrays.asList("GPG", "PGP")));
        // Cipher -> Asymmetric
        register(new OperationDescriptor("op_asym_ciphers", "Asymmetric Ciphers", "Cipher", "Encrypt/Decrypt with asymmetric keys", "🔑", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.HIGH, "Asymmetric Ciphers", Arrays.asList("RSA")));

        // Generic
        register(new OperationDescriptor("op_gen_hash", "Hashing", "Generic", "Calculate hashes", "🧩", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.NONE, "Hashing", Arrays.asList("SHA", "MD5")));
        register(new OperationDescriptor("op_gen_manual", "Manual Conversion", "Generic", "Convert formats", "🔄", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.NONE, "Manual Conversion", Arrays.asList("Hex", "Base64", "EBCDIC")));
        register(new OperationDescriptor("op_gen_compressed_hex", "Compressed Hex (2-row)", "Generic", "Interleave or split two-row host hexadecimal", "↔️", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.NONE, "Compressed Hex (2-row)", Arrays.asList("Host hex", "Interleaved hex", "Two-row hex")));
        register(new OperationDescriptor("op_gen_batch", "Batch Runner", "Generic", "Run batch operations", "⚙", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.LOW, "Batch Runner", Collections.emptyList()));
        register(new OperationDescriptor("op_gen_file", "File Conversion", "Generic", "Convert file formats", "📁", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.NONE, "File Conversion", Collections.emptyList()));
        register(new OperationDescriptor("op_gen_random", "Random Number Generator", "Generic", "Generate random bytes", "🎲", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.NONE, "Random Number Generator", Collections.emptyList()));
        register(new OperationDescriptor("op_gen_check_digits", "Check Digits", "Generic", "Calculate check digits", "🔢", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.NONE, "Check Digits", Arrays.asList("Luhn")));
        register(new OperationDescriptor("op_gen_mod", "Modular Arithmetic", "Generic", "Math operations", "🧮", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.NONE, "Modular Arithmetic", Collections.emptyList()));
        register(new OperationDescriptor("op_gen_clipboard", "Clipboard Shelf", "Generic", "Reuse copied session values across tools", "📋", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.LOW, "Clipboard Shelf", Arrays.asList("Clipboard", "Copy history", "Shelf")));

        // Authentication
        register(new OperationDescriptor("op_auth_sig", "Digital Signatures", "Authentication", "Sign and verify", "✒", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.HIGH, "Digital Signatures", Arrays.asList("RSA", "ECDSA")));
        register(new OperationDescriptor("op_auth_mac", "Message Authentication Codes", "Authentication", "Calculate MACs", "🛡", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.HIGH, "Message Authentication Codes", Arrays.asList("HMAC", "CMAC")));

        // Keys -> Symmetric
        register(new OperationDescriptor("op_keys_gen", "Key Generation", "Keys", "Generate symmetric keys", "🔑", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.HIGH, "Key Generation", Collections.emptyList()));
        register(new OperationDescriptor("op_keys_val", "Validation & KCV", "Keys", "Validate keys", "✅", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.HIGH, "Validation & KCV", Collections.emptyList()));
        register(new OperationDescriptor("op_keys_share", "Key Sharing (XOR Split/Combine)", "Keys", "Split or combine keys", "✂", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.HIGH, "Key Sharing (XOR Split/Combine)", Collections.emptyList()));
        register(new OperationDescriptor("op_keys_kdf", "Key Derivation (KDF)", "Keys", "Derive keys", "🧬", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.HIGH, "Key Derivation (KDF)", Arrays.asList("HKDF", "PBKDF2")));
        register(new OperationDescriptor("op_keys_wrap", "AES Key Wrap", "Keys", "Wrap keys", "🎁", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.HIGH, "AES Key Wrap", Arrays.asList("RFC 3394")));
        register(new OperationDescriptor("op_keys_tr31", "TR-31 Key Blocks", "Keys", "TR-31 operations", "📦", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.HIGH, "TR-31 Key Blocks", Arrays.asList("TR-31", "TR31")));

        // Keys -> Asymmetric
        register(new OperationDescriptor("op_keys_rsa", "RSA Key Generation", "Keys", "Generate RSA keys", "🗝", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.HIGH, "RSA Key Generation", Collections.emptyList()));
        register(new OperationDescriptor("op_keys_ecdsa", "ECDSA Key Generation", "Keys", "Generate ECDSA keys", "🗝", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.HIGH, "ECDSA Key Generation", Collections.emptyList()));
        register(new OperationDescriptor("op_keys_dsa", "DSA Key Generation", "Keys", "Generate DSA keys", "🗝", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.HIGH, "DSA Key Generation", Collections.emptyList()));
        register(new OperationDescriptor("op_keys_eddsa", "EdDSA Key Generation", "Keys", "Generate EdDSA keys", "🗝", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.HIGH, "EdDSA Key Generation", Collections.emptyList()));
        register(new OperationDescriptor("op_keys_compare", "Compare Public / Private Key", "Keys", "Compare key pairs", "⚖", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.HIGH, "Compare Public / Private Key", Collections.emptyList()));

        // Keys -> Tools
        register(new OperationDescriptor("op_keys_material", "Key Material Inspector", "Keys", "Inspect key material", "🔎", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.LOW, "Key Material Inspector", Collections.emptyList()));
        register(new OperationDescriptor("op_keys_format_workbench", "Key & Certificate Format Workbench", "Generic", "Inspect and convert keys and certificates", "🛠", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.HIGH, "Key & Certificate Format Workbench", Arrays.asList("PEM", "DER", "JWK", "PKCS12")));
        register(new OperationDescriptor("op_keys_store", "KeyStore Inspector", "Keys", "Inspect KeyStore", "🗄", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.LOW, "KeyStore Inspector", Arrays.asList("JKS", "PKCS12")));
        register(new OperationDescriptor("op_keys_pkcs11", "PKCS#11 Token", "Keys", "Connect and inspect a laboratory PKCS#11 token", "🔐", OperationDescriptor.Status.EXPERIMENTAL, OperationDescriptor.SecretRisk.HIGH, "PKCS#11 Token", Arrays.asList("HSM", "SunPKCS11", "SoftHSM")));

        // Post-Quantum
        register(new OperationDescriptor("op_pqc_gen", "PQC Key Generation", "Post-Quantum", "Generate PQC keys", "🔮", OperationDescriptor.Status.EXPERIMENTAL, OperationDescriptor.SecretRisk.HIGH, "PQC Key Generation", Arrays.asList("ML-KEM", "Kyber", "ML-DSA", "Dilithium")));
        register(new OperationDescriptor("op_pqc_sign", "PQC Sign/Verify", "Post-Quantum", "PQC signatures", "✒", OperationDescriptor.Status.EXPERIMENTAL, OperationDescriptor.SecretRisk.HIGH, "PQC Sign/Verify", Arrays.asList("SLH-DSA", "SPHINCS+")));

        // XML Security
        register(new OperationDescriptor("op_xml_sign", "Sign XML (XAdES)", "XML Security", "Sign XML documents", "📝", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.HIGH, "Sign XML (XAdES)", Arrays.asList("XAdES")));
        register(new OperationDescriptor("op_xml_inspect", "Inspect Signed XML", "XML Security", "Inspect XML signatures", "🔍", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.LOW, "Inspect Signed XML", Collections.emptyList()));
        register(new OperationDescriptor("op_xml_tsa", "RFC 3161 Timestamp", "XML Security", "Request timestamps", "⏱", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.LOW, "RFC 3161 Timestamp", Arrays.asList("RFC 3161")));
        register(new OperationDescriptor("op_xml_verify", "Verify XML (XAdES)", "XML Security", "Verify XML signatures", "✅", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.LOW, "Verify XML (XAdES)", Collections.emptyList()));

        // Certificates
        register(new OperationDescriptor("op_cert_gen", "Generate Certificate", "Certificates", "Generate self-signed", "📜", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.HIGH, "Generate Certificate", Collections.emptyList()));
        register(new OperationDescriptor("op_cert_issue", "Issue Certificate from CSR", "Certificates", "Issue certificates", "🖋", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.HIGH, "Issue Certificate from CSR", Arrays.asList("CSR")));
        register(new OperationDescriptor("op_cert_parse", "Parse Certificate", "Certificates", "Parse certs", "📖", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.LOW, "Parse Certificate", Collections.emptyList()));
        register(new OperationDescriptor("op_cert_compare", "Compare Certificates", "Certificates", "Compare certs", "⚖", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.LOW, "Compare Certificates", Collections.emptyList()));
        register(new OperationDescriptor("op_cert_val", "Validate Certificate", "Certificates", "Validate certs", "✅", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.LOW, "Validate Certificate", Collections.emptyList()));
        register(new OperationDescriptor("op_cert_chain", "Certificate Chain", "Certificates", "Build chain", "🔗", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.LOW, "Certificate Chain", Collections.emptyList()));
        register(new OperationDescriptor("op_pades", "PAdES PDF Signatures", "Certificates", "Sign and inspect PDFs with PAdES Baseline-B", "📄", OperationDescriptor.Status.EXPERIMENTAL, OperationDescriptor.SecretRisk.HIGH, "PAdES PDF Signatures", Arrays.asList("PDF", "PAdES")));
        register(new OperationDescriptor("op_asic_s", "ASiC-S Containers", "Certificates", "Package one payload with a detached CAdES-BES signature", "📦", OperationDescriptor.Status.EXPERIMENTAL, OperationDescriptor.SecretRisk.HIGH, "ASiC-S Containers", Arrays.asList("ASiC", "ASiC-S", "CAdES")));
        register(new OperationDescriptor("op_cert_cms", "CMS/PKCS#7 Operations", "Certificates", "CMS operations", "🗃", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.HIGH, "CMS/PKCS#7 Operations", Arrays.asList("PKCS7", "CMS")));
        register(new OperationDescriptor("op_cms_inspector", "CMS Inspector", "Certificates", "Inspect CMS/PKCS#7 structures", "🕵️", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.LOW, "CMS Inspector", Arrays.asList("PKCS7", "CMS", "SignedData", "EnvelopedData")));

        // JOSE
        register(new OperationDescriptor("op_jose_jwt", "JWT (Signed)", "JOSE", "Signed JWTs", "🏷", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.HIGH, "JWT (Signed)", Arrays.asList("JWS")));
        register(new OperationDescriptor("op_jose_jwe", "JWE (Encrypted)", "JOSE", "Encrypted JWTs", "🔒", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.HIGH, "JWE (Encrypted)", Arrays.asList("JWE")));
        register(new OperationDescriptor("op_jose_jwk", "JWK (Keys)", "JOSE", "JWK keys", "🔑", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.HIGH, "JWK (Keys)", Arrays.asList("JWKS")));
        register(new OperationDescriptor("op_jose_jwa", "JWA (Algorithms)", "JOSE", "JWA algorithms", "⚙", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.NONE, "JWA (Algorithms)", Collections.emptyList()));
        register(new OperationDescriptor("op_jose_insp", "Token Inspector", "JOSE", "Inspect tokens", "🔍", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.LOW, "Token Inspector", Collections.emptyList()));

        // Payments
        register(new OperationDescriptor("op_pay_clear_pin", "Clear PIN Blocks", "Payments", "Clear PIN blocks", "💳", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.HIGH, "Clear PIN Blocks", Collections.emptyList()));
        register(new OperationDescriptor("op_pay_enc_pin", "Encrypted PIN Blocks", "Payments", "Encrypted PIN blocks", "🔒", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.HIGH, "Encrypted PIN Blocks", Collections.emptyList()));
        register(new OperationDescriptor("op_pay_pin_gen", "PIN Generation", "Payments", "Generate PINs", "🔢", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.HIGH, "PIN Generation", Collections.emptyList()));
        register(new OperationDescriptor("op_pay_cvv", "CVV Operations", "Payments", "CVV operations", "💳", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.HIGH, "CVV Operations", Arrays.asList("CVV", "CVC")));
        register(new OperationDescriptor("op_pay_dukpt", "DUKPT TDES / AES", "Payments", "DUKPT operations", "🔑", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.HIGH, "DUKPT TDES / AES", Arrays.asList("DUKPT")));
        register(new OperationDescriptor("op_pay_emv_tlv", "EMV TLV Inspector", "Payments", "Inspect EMV TLV", "🔍", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.LOW, "EMV TLV Inspector", Arrays.asList("EMV")));
        register(new OperationDescriptor("op_pay_emv_ops", "EMV Operations", "Payments", "EMV operations", "💳", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.HIGH, "EMV Operations", Collections.emptyList()));

        // ASN1
        register(new OperationDescriptor("op_asn1_dec", "Decode ASN.1", "ASN1", "Decode ASN.1", "📖", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.LOW, "Decode ASN.1", Collections.emptyList()));
        register(new OperationDescriptor("op_asn1_enc", "Encode ASN.1", "ASN1", "Encode ASN.1", "🖋", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.LOW, "Encode ASN.1", Collections.emptyList()));

        // History
        register(new OperationDescriptor("op_hist_recent", "Recent Operations", "History", "View recent operations", "🕒", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.NONE, "Recent Operations", Collections.emptyList()));
        register(new OperationDescriptor("op_hist_saved", "Saved Sessions", "History", "View saved sessions", "💾", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.NONE, "Saved Sessions", Collections.emptyList()));
        register(new OperationDescriptor("op_hist_export", "Export History", "History", "Export operations history", "📤", OperationDescriptor.Status.STABLE, OperationDescriptor.SecretRisk.NONE, "Export History", Collections.emptyList()));
    }

    public void register(OperationDescriptor desc) {
        if (operations.containsKey(desc.getId())) {
            throw new IllegalArgumentException("Operation ID already registered: " + desc.getId());
        }
        operations.put(desc.getId(), desc);
    }

    public List<OperationDescriptor> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(operations.values()));
    }

    public Optional<OperationDescriptor> getById(String id) {
        return Optional.ofNullable(operations.get(id));
    }

    public Optional<OperationDescriptor> getByNavigationPath(String path) {
        return operations.values().stream().filter(op -> op.getNavigationPath().equals(path)).findFirst();
    }

    /**
     * Resolves a user-facing navigation value without making callers duplicate
     * title, path and alias matching. Exact matching is intentional: search is
     * available separately and routing must never guess a cryptographic tool.
     */
    public Optional<OperationDescriptor> resolveNavigation(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String candidate = value.trim();
        return operations.values().stream()
                .filter(op -> op.getId().equalsIgnoreCase(candidate)
                        || op.getTitle().equalsIgnoreCase(candidate)
                        || op.getNavigationPath().equalsIgnoreCase(candidate)
                        || op.getAliases().stream().anyMatch(alias -> alias.equalsIgnoreCase(candidate)))
                .findFirst();
    }

    public List<OperationDescriptor> search(String query) {
        if (query == null || query.isBlank()) {
            return getAll();
        }
        String q = query.toLowerCase(Locale.ROOT);
        return operations.values().stream()
                .filter(op -> op.getTitle().toLowerCase(Locale.ROOT).contains(q) ||
                              op.getId().toLowerCase(Locale.ROOT).contains(q) ||
                              op.getSubtitle().toLowerCase(Locale.ROOT).contains(q) ||
                              op.getCategory().toLowerCase(Locale.ROOT).contains(q) ||
                              op.getAliases().stream().anyMatch(alias -> alias.toLowerCase(Locale.ROOT).contains(q)))
                .collect(Collectors.toUnmodifiableList());
    }

    public String toMarkdownCatalog() {
        StringBuilder sb = new StringBuilder();
        sb.append("# CryptoCarver Operations Catalog\n\n");
        sb.append("This document is generated automatically from `OperationRegistry`. Do not edit manually.\n\n");

        Map<String, List<OperationDescriptor>> byCategory = getAll().stream()
                .collect(Collectors.groupingBy(OperationDescriptor::getCategory));

        List<String> sortedCategories = byCategory.keySet().stream().sorted().collect(Collectors.toList());

        for (String category : sortedCategories) {
            sb.append("## ").append(category).append("\n\n");
            sb.append("| Icon | Title | ID | Status | Risk | Navigation Path | Aliases |\n");
            sb.append("|------|-------|----|--------|------|-----------------|---------|\n");

            List<OperationDescriptor> ops = byCategory.get(category).stream()
                    .sorted(Comparator.comparing(OperationDescriptor::getTitle))
                    .collect(Collectors.toList());

            for (OperationDescriptor op : ops) {
                String aliases = op.getAliases().isEmpty() ? "-" : String.join(", ", op.getAliases());
                sb.append(String.format("| %s | %s | `%s` | %s | %s | `%s` | %s |\n",
                        op.getIcon(),
                        op.getTitle(),
                        op.getId(),
                        op.getStatus(),
                        op.getSecretRisk(),
                        op.getNavigationPath(),
                        aliases));
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
