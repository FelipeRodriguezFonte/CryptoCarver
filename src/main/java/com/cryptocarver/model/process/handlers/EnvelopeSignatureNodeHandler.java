package com.cryptocarver.model.process.handlers;

import com.cryptocarver.crypto.*;
import com.cryptocarver.model.process.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.*;

/** Process adapters for local envelope, signature, OpenPGP, PQC and X.509 facades. */
public final class EnvelopeSignatureNodeHandler implements ProcessNodeHandler {
    public static final Set<String> TYPES = Set.of(
            "CMS_SIGN", "CMS_VERIFY", "CMS_ENVELOPE", "CMS_DEVELOPE", "CADES_BES_SIGN",
            "XMLDSIG_SIGN", "XMLDSIG_VERIFY", "PADES_SIGN", "PADES_VERIFY",
            "OPENPGP_ENCRYPT", "OPENPGP_DECRYPT", "OPENPGP_SIGN", "OPENPGP_VERIFY",
            "PQC_KEYPAIR_GENERATE", "PQC_SIGN", "PQC_VERIFY", "PQC_KEM_ENCAPSULATE", "PQC_KEM_DECAPSULATE",
            "CERT_PARSE", "CERT_SELF_SIGNED_GENERATE", "CERT_VALIDATE");

    @Override public Set<String> supportedTypes() { return TYPES; }

    @Override public List<NodeDescriptor> descriptors() {
        List<NodeParameter> signingStore = params(file("keystorePath"), secret("keystorePassword"), secret("keyPassword"), plain("alias"));
        List<NodeParameter> trust = params(combo("verificationMode", List.of("REQUIRE_TRUST", "STRUCTURAL_ONLY"), "REQUIRE_TRUST"), file("trustStorePath"), secret("trustStorePassword"));
        List<String> sigPqc = List.of("ML-DSA-44", "ML-DSA-65", "ML-DSA-87", "SLH-DSA-SHA2-128f", "SLH-DSA-SHA2-192f", "SLH-DSA-SHA2-256f");
        List<String> kemPqc = List.of("ML-KEM-512", "ML-KEM-768", "ML-KEM-1024");
        return List.of(
                desc("CMS_SIGN", "cmsSign", plus(signingStore, check("detached", "false"))),
                desc("CMS_VERIFY", "cmsVerify", trust),
                desc("CMS_ENVELOPE", "cmsEnvelope", params(file("certificatePath"))),
                desc("CMS_DEVELOPE", "cmsDevelope", signingStore),
                desc("CADES_BES_SIGN", "cadesBesSign", signingStore),
                desc("XMLDSIG_SIGN", "xmldsigSign", plus(signingStore, combo("packaging", List.of("ENVELOPED", "ENVELOPING", "DETACHED"), "ENVELOPED"))),
                desc("XMLDSIG_VERIFY", "xmldsigVerify", trust),
                desc("PADES_SIGN", "padesSign", plus(signingStore, combo("profile", List.of("B"), "B"))),
                desc("PADES_VERIFY", "padesVerify", trust),
                desc("OPENPGP_ENCRYPT", "openpgpEncrypt", params(multiline("publicKey"))),
                desc("OPENPGP_DECRYPT", "openpgpDecrypt", params(secret("privateKey"), secret("passphrase"))),
                desc("OPENPGP_SIGN", "openpgpSign", params(secret("privateKey"), secret("passphrase"))),
                desc("OPENPGP_VERIFY", "openpgpVerify", params(multiline("publicKey"))),
                desc("PQC_KEYPAIR_GENERATE", "pqcKeypairGenerate", params(combo("algorithm", concat(sigPqc, kemPqc), "ML-DSA-65"))),
                desc("PQC_SIGN", "pqcSign", params(combo("algorithm", sigPqc, "ML-DSA-65"), secret("privateKey"))),
                desc("PQC_VERIFY", "pqcVerify", params(combo("algorithm", sigPqc, "ML-DSA-65"), multiline("publicKey"))),
                desc("PQC_KEM_ENCAPSULATE", "pqcKemEncapsulate", params(combo("algorithm", kemPqc, "ML-KEM-768"), multiline("publicKey"))),
                desc("PQC_KEM_DECAPSULATE", "pqcKemDecapsulate", params(combo("algorithm", kemPqc, "ML-KEM-768"), secret("privateKey"))),
                desc("CERT_PARSE", "certParse", List.of()),
                desc("CERT_SELF_SIGNED_GENERATE", "certSelfSignedGenerate", params(plainDefault("commonName", "localhost"), combo("keyAlgorithm", List.of("RSA", "EC"), "RSA"), combo("keySize", List.of("2048", "3072", "4096"), "2048"), number("validityDays", "365"))),
                desc("CERT_VALIDATE", "certValidate", trust)
        );
    }

    @Override public List<PortDefinition> inputPorts(ProcessDefinition.Node node) {
        Set<Representation> any = Representation.standardValues();
        Set<Representation> binary = Set.of(Representation.BINARY);
        Set<Representation> text = Set.of(Representation.TEXT_UTF8, Representation.BASE64);
        return switch (node.type) {
            case "CERT_SELF_SIGNED_GENERATE", "PQC_KEYPAIR_GENERATE" -> List.of();
            case "PQC_VERIFY" -> List.of(port("payload", any, true), port("signature", binary, true), port("publicKey", binary, false));
            case "OPENPGP_VERIFY" -> List.of(port("message", text, true), port("publicKey", text, false));
            case "PQC_SIGN" -> List.of(port("payload", any, true), port("privateKey", binary, false));
            case "PQC_KEM_ENCAPSULATE" -> List.of(port("publicKey", binary, false));
            case "PQC_KEM_DECAPSULATE" -> List.of(port("encapsulation", binary, true), port("privateKey", binary, false));
            case "CMS_SIGN", "CMS_ENVELOPE", "CADES_BES_SIGN", "PADES_SIGN" -> List.of(port("payload", any, true));
            case "XMLDSIG_SIGN", "OPENPGP_ENCRYPT", "OPENPGP_SIGN" -> List.of(port("payload", any, true));
            case "CMS_VERIFY" -> List.of(port("message", binary, true), port("payload", any, false));
            case "CMS_DEVELOPE", "PADES_VERIFY" -> List.of(port("message", binary, true));
            case "XMLDSIG_VERIFY" -> List.of(port("message", text, true));
            case "OPENPGP_DECRYPT" -> List.of(port("message", text, true));
            case "CERT_PARSE", "CERT_VALIDATE" -> List.of(port("certificate", any, true));
            default -> throw new IllegalArgumentException("Unsupported envelope/signature node: " + node.type);
        };
    }

    @Override public Representation outputRepresentation(ProcessDefinition.Node node, Map<String, Representation> inputs) {
        return switch (node.type) {
            case "XMLDSIG_SIGN", "XMLDSIG_VERIFY", "OPENPGP_ENCRYPT", "OPENPGP_SIGN", "CERT_PARSE", "CERT_VALIDATE" -> Representation.TEXT_UTF8;
            default -> Representation.BINARY;
        };
    }

    @Override public void validateConfiguration(ProcessDefinition.Node node) {
        switch (node.type) {
            case "CMS_SIGN", "CMS_DEVELOPE", "CADES_BES_SIGN", "XMLDSIG_SIGN", "PADES_SIGN" -> validateSigningStore(node);
            case "CMS_ENVELOPE" -> readable(node, "certificatePath");
            case "CMS_VERIFY", "XMLDSIG_VERIFY", "PADES_VERIFY", "CERT_VALIDATE" -> validateTrust(node);
            case "OPENPGP_ENCRYPT", "OPENPGP_VERIFY" -> supplied(node, "publicKey");
            case "OPENPGP_DECRYPT", "OPENPGP_SIGN" -> { supplied(node, "privateKey"); supplied(node, "passphrase"); }
            case "PQC_SIGN" -> { supplied(node, "privateKey"); pqcAlgorithm(node, true, false); }
            case "PQC_VERIFY" -> { supplied(node, "publicKey"); pqcAlgorithm(node, true, false); }
            case "PQC_KEM_ENCAPSULATE" -> { supplied(node, "publicKey"); pqcAlgorithm(node, false, true); }
            case "PQC_KEM_DECAPSULATE" -> { supplied(node, "privateKey"); pqcAlgorithm(node, false, true); }
            case "PQC_KEYPAIR_GENERATE" -> pqcAlgorithm(node, true, true);
            case "CERT_SELF_SIGNED_GENERATE" -> {
                required(node, "commonName");
                int days = integer(node, "validityDays", 1, 36500);
                if (days < 1) throw new IllegalArgumentException("validityDays must be positive");
                if (!Set.of("RSA", "EC").contains(node.configuration.getOrDefault("keyAlgorithm", "RSA"))) throw new IllegalArgumentException("Unsupported certificate key algorithm");
            }
            case "CERT_PARSE" -> { }
            default -> {
                if (node.type.startsWith("PQC_")) pqcAlgorithm(node, node.type.contains("SIGN") || node.type.contains("VERIFY"), node.type.contains("KEM"));
            }
        }
        if ("XMLDSIG_SIGN".equals(node.type) && !Set.of("ENVELOPED", "ENVELOPING", "DETACHED").contains(node.configuration.getOrDefault("packaging", "ENVELOPED"))) throw new IllegalArgumentException("Unsupported XML signature packaging");
        if ("PADES_SIGN".equals(node.type) && !"B".equals(node.configuration.getOrDefault("profile", "B"))) throw new IllegalArgumentException("Only offline PAdES Baseline B is available");
    }

    @Override public FlowValue execute(ProcessDefinition.Node node, Map<String, FlowValue> inputs, ExecutionContext context) throws Exception {
        return switch (node.type) {
            case "CMS_SIGN" -> withStore(node, material -> FlowValue.binary(CMSOperations.generateSignedData(bytes(inputs, "payload"), material.certificate, material.privateKey, Map.of(), Boolean.parseBoolean(node.configuration.getOrDefault("detached", "false")))));
            case "CMS_VERIFY" -> {
                X509Certificate trusted = structuralOnly(node) ? null : trustCertificate(node);
                CMSOperations.VerificationResult result = CMSOperations.verifySignedData(bytes(inputs, "message"), trusted,
                        inputs.containsKey("payload") ? bytes(inputs, "payload") : null);
                if (!result.verified) throw new IllegalArgumentException("CMS signature verification failed");
                yield FlowValue.binary(result.content);
            }
            case "CMS_ENVELOPE" -> FlowValue.binary(CMSOperations.generateEnvelopedData(bytes(inputs, "payload"), certificate(Files.readAllBytes(Path.of(node.configuration.get("certificatePath"))))));
            case "CMS_DEVELOPE" -> withStore(node, material -> FlowValue.binary(CMSOperations.decryptEnvelopedData(bytes(inputs, "message"), material.privateKey)));
            case "CADES_BES_SIGN" -> withStore(node, material -> FlowValue.binary(CMSOperations.generateCadesBes(bytes(inputs, "payload"), material.certificate, material.privateKey, Map.of(), false)));
            case "XMLDSIG_SIGN" -> withStore(node, ignored -> text(XMLSignatureOperations.signXAdES(string(inputs, "payload"), node.configuration.get("keystorePath"), node.configuration.get("keystorePassword"), keyIndex(node), "XAdES_BASELINE_B", null, node.configuration.getOrDefault("packaging", "ENVELOPED"))));
            case "XMLDSIG_VERIFY" -> {
                boolean structural = structuralOnly(node);
                XMLSignatureOperations.VerifiedXmlResult result = XMLSignatureOperations.verifyXAdESPayload(string(inputs, "message"), structural ? null : node.configuration.get("trustStorePath"), structural ? null : node.configuration.get("trustStorePassword"), structural);
                if (!result.verified()) throw new IllegalArgumentException("XML signature or trust validation failed");
                String warning = structural ? "WARNING: STRUCTURAL_ONLY; certificate trust was not evaluated.\n" : "";
                yield text(warning + result.payload());
            }
            case "PADES_SIGN" -> FlowValue.binary(PadesOperations.signBaselineB(bytes(inputs, "payload"), new File(node.configuration.get("keystorePath")), chars(node, "keystorePassword")));
            case "PADES_VERIFY" -> {
                boolean structural = structuralOnly(node);
                PadesOperations.PadesValidationResult result = PadesOperations.validate(bytes(inputs, "message"), structural ? null : new File(node.configuration.get("trustStorePath")), structural ? new char[0] : chars(node, "trustStorePassword"), List.of(), false);
                String summary = result.summary().toUpperCase(Locale.ROOT);
                boolean passed = summary.contains("INDICATION: PASSED") || summary.contains("INDICATION: TOTAL_PASSED");
                boolean integrityOnly = structural && summary.contains("NO_CERTIFICATE_CHAIN_FOUND")
                        && !summary.contains("HASH_FAILURE") && !summary.contains("SIG_CRYPTO_FAILURE");
                if (!passed && !integrityOnly) throw new IllegalArgumentException("PAdES signature or trust validation failed");
                yield FlowValue.binary(bytes(inputs, "message"));
            }
            case "OPENPGP_ENCRYPT" -> text(OpenPgpOperations.encrypt(bytes(inputs, "payload"), setting(node, inputs, "publicKey")));
            case "OPENPGP_DECRYPT" -> FlowValue.binary(OpenPgpOperations.decrypt(string(inputs, "message"), setting(node, inputs, "privateKey"), setting(node, inputs, "passphrase").toCharArray()).plaintext());
            case "OPENPGP_SIGN" -> text(OpenPgpOperations.signAttached(bytes(inputs, "payload"), setting(node, inputs, "privateKey"), setting(node, inputs, "passphrase").toCharArray()));
            case "OPENPGP_VERIFY" -> {
                OpenPgpOperations.SignedMessageVerificationResult result = OpenPgpOperations.verifyAttached(string(inputs, "message"), setting(node, inputs, "publicKey"));
                if (!result.valid()) throw new IllegalArgumentException("OpenPGP signature verification failed");
                yield FlowValue.binary(result.content());
            }
            case "PQC_KEYPAIR_GENERATE" -> FlowValue.binary(PostQuantumOperations.generateKeyPair(node.configuration.getOrDefault("algorithm", "ML-DSA-65")).getPrivate().getEncoded());
            case "PQC_SIGN" -> FlowValue.binary(PostQuantumOperations.sign(pqcPrivate(node, inputs), bytes(inputs, "payload"), node.configuration.getOrDefault("algorithm", "ML-DSA-65")));
            case "PQC_VERIFY" -> {
                byte[] payload = bytes(inputs, "payload");
                if (!PostQuantumOperations.verify(pqcPublic(node, inputs), payload, bytes(inputs, "signature"), node.configuration.getOrDefault("algorithm", "ML-DSA-65"))) throw new IllegalArgumentException("PQC signature verification failed");
                yield FlowValue.binary(payload);
            }
            case "PQC_KEM_ENCAPSULATE" -> FlowValue.binary(PostQuantumOperations.encapsulate(pqcPublic(node, inputs), node.configuration.getOrDefault("algorithm", "ML-KEM-768")).encapsulation());
            case "PQC_KEM_DECAPSULATE" -> FlowValue.binary(PostQuantumOperations.decapsulate(pqcPrivate(node, inputs), bytes(inputs, "encapsulation"), node.configuration.getOrDefault("algorithm", "ML-KEM-768")));
            case "CERT_PARSE" -> text(KeyMaterialInspector.describeCertificate(certificate(bytes(inputs, "certificate"))));
            case "CERT_SELF_SIGNED_GENERATE" -> FlowValue.binary(generateCertificate(node).getEncoded());
            case "CERT_VALIDATE" -> {
                X509Certificate cert = certificate(bytes(inputs, "certificate"));
                if (structuralOnly(node)) yield text("WARNING: STRUCTURAL_ONLY; certificate trust was not evaluated.\n" + KeyMaterialInspector.describeCertificate(cert));
                CertificateGenerator.TrustValidationResult result = CertificateGenerator.validateAgainstTrustStore(cert, new File(node.configuration.get("trustStorePath")), chars(node, "trustStorePassword"));
                if (!result.valid()) throw new IllegalArgumentException(result.message());
                yield text(KeyMaterialInspector.describeCertificate(cert));
            }
            default -> throw new IllegalArgumentException("Unsupported envelope/signature node: " + node.type);
        };
    }

    private record StoreMaterial(PrivateKey privateKey, X509Certificate certificate) { }
    @FunctionalInterface private interface StoreAction { FlowValue run(StoreMaterial material) throws Exception; }
    private static FlowValue withStore(ProcessDefinition.Node node, StoreAction action) throws Exception { return action.run(loadStore(node)); }
    private static StoreMaterial loadStore(ProcessDefinition.Node node) throws Exception {
        char[] storePassword = chars(node, "keystorePassword");
        char[] keyPassword = node.configuration.get("keyPassword") == null ? storePassword.clone() : chars(node, "keyPassword");
        try (InputStream input = Files.newInputStream(Path.of(node.configuration.get("keystorePath")))) {
            KeyStore store = KeyStore.getInstance(storeType(node.configuration.get("keystorePath")));
            store.load(input, storePassword);
            String alias = node.configuration.get("alias");
            if (alias == null || alias.isBlank()) { Enumeration<String> aliases = store.aliases(); while (aliases.hasMoreElements()) { String candidate = aliases.nextElement(); if (store.isKeyEntry(candidate)) { alias = candidate; break; } } }
            if (alias == null) throw new IllegalArgumentException("Keystore contains no private key entry");
            return new StoreMaterial((PrivateKey) store.getKey(alias, keyPassword), (X509Certificate) store.getCertificate(alias));
        } finally { Arrays.fill(storePassword, '\0'); Arrays.fill(keyPassword, '\0'); }
    }
    private static String storeType(String path) { String lower = path.toLowerCase(Locale.ROOT); return lower.endsWith(".jks") ? "JKS" : lower.endsWith(".bcfks") ? "BCFKS" : "PKCS12"; }
    private static X509Certificate certificate(byte[] encoded) throws Exception { return CertificateGenerator.parseCertificate(encoded); }
    private static X509Certificate trustCertificate(ProcessDefinition.Node node) throws Exception { KeyStore store = loadTrustStore(node); Enumeration<String> aliases = store.aliases(); while (aliases.hasMoreElements()) { Certificate c = store.getCertificate(aliases.nextElement()); if (c instanceof X509Certificate x) return x; } throw new IllegalArgumentException("Truststore has no X.509 certificate"); }
    private static KeyStore loadTrustStore(ProcessDefinition.Node node) throws Exception { char[] password = chars(node, "trustStorePassword"); try (InputStream in = Files.newInputStream(Path.of(node.configuration.get("trustStorePath")))) { KeyStore store = KeyStore.getInstance(storeType(node.configuration.get("trustStorePath"))); store.load(in, password); return store; } finally { Arrays.fill(password, '\0'); } }
    private static X509Certificate generateCertificate(ProcessDefinition.Node node) throws Exception { String algorithm = node.configuration.getOrDefault("keyAlgorithm", "RSA"); CertificateGenerator.CertificateConfig config = new CertificateGenerator.CertificateConfig(); config.commonName = node.configuration.getOrDefault("commonName", "localhost"); config.validityDays = Integer.parseInt(node.configuration.getOrDefault("validityDays", "365")); config.signatureAlgorithm = "EC".equals(algorithm) ? "SHA256withECDSA" : "SHA256withRSA"; return CertificateGenerator.generateSelfSignedCertificate(algorithm, Integer.parseInt(node.configuration.getOrDefault("keySize", "2048")), config); }
    private static PrivateKey pqcPrivate(ProcessDefinition.Node n, Map<String, FlowValue> in) throws Exception { return PostQuantumOperations.importPrivateKey(n.configuration.getOrDefault("algorithm", "ML-DSA-65"), settingBytes(n, in, "privateKey")); }
    private static PublicKey pqcPublic(ProcessDefinition.Node n, Map<String, FlowValue> in) throws Exception { return PostQuantumOperations.importPublicKey(n.configuration.getOrDefault("algorithm", "ML-DSA-65"), settingBytes(n, in, "publicKey")); }
    private static byte[] settingBytes(ProcessDefinition.Node n, Map<String, FlowValue> in, String key) { FlowValue flow = in.get(key); if (flow != null) return flow.bytes(); return Base64.getDecoder().decode(Objects.requireNonNull(n.configuration.get(key), key + " is required").replaceAll("\\s", "")); }
    private static String setting(ProcessDefinition.Node n, Map<String, FlowValue> in, String key) { FlowValue flow = in.get(key); return flow == null ? n.configuration.get(key) : new String(flow.bytes(), flow.charset() == null ? StandardCharsets.UTF_8 : flow.charset()); }
    private static byte[] bytes(Map<String, FlowValue> in, String key) { FlowValue value = in.get(key); if (value == null) throw new IllegalArgumentException(key + " is required"); return value.bytes(); }
    private static String string(Map<String, FlowValue> in, String key) { FlowValue value = in.get(key); return new String(bytes(in, key), value.charset() == null ? StandardCharsets.UTF_8 : value.charset()); }
    private static FlowValue text(String value) { return FlowValue.text(value, StandardCharsets.UTF_8); }
    private static char[] chars(ProcessDefinition.Node node, String key) { return Objects.requireNonNull(node.configuration.get(key), key + " is required").toCharArray(); }
    private static int keyIndex(ProcessDefinition.Node node) { return 0; }
    private static boolean structuralOnly(ProcessDefinition.Node node) { return "STRUCTURAL_ONLY".equals(node.configuration.getOrDefault("verificationMode", "REQUIRE_TRUST")); }
    private static void validateSigningStore(ProcessDefinition.Node n) { readable(n, "keystorePath"); supplied(n, "keystorePassword"); if (n.configuration.containsKey("keyPassword") || NodeCatalog.isSupplied(n, "keyPassword")) { } }
    private static void validateTrust(ProcessDefinition.Node n) { String mode = n.configuration.getOrDefault("verificationMode", "REQUIRE_TRUST"); if (!Set.of("REQUIRE_TRUST", "STRUCTURAL_ONLY").contains(mode)) throw new IllegalArgumentException("Unsupported verification mode"); if (!"STRUCTURAL_ONLY".equals(mode)) { readable(n, "trustStorePath"); supplied(n, "trustStorePassword"); } }
    private static void readable(ProcessDefinition.Node n, String key) { required(n, key); if (!Files.isRegularFile(Path.of(n.configuration.get(key)))) throw new IllegalArgumentException(key + " must name a readable local file"); }
    private static void supplied(ProcessDefinition.Node n, String key) { if (!n.configuration.containsKey(key) && !"true".equalsIgnoreCase(n.configuration.get(key + "FromFlow")) && !NodeCatalog.isSupplied(n, key)) throw new IllegalArgumentException(key + " is required"); }
    private static void required(ProcessDefinition.Node n, String key) { if (n.configuration.getOrDefault(key, "").isBlank()) throw new IllegalArgumentException(key + " is required"); }
    private static int integer(ProcessDefinition.Node n, String key, int min, int max) { try { int value = Integer.parseInt(n.configuration.getOrDefault(key, "")); if (value < min || value > max) throw new IllegalArgumentException(key + " is outside the allowed range"); return value; } catch (NumberFormatException e) { throw new IllegalArgumentException(key + " must be numeric"); } }
    private static void pqcAlgorithm(ProcessDefinition.Node n, boolean signing, boolean kem) { String value = n.configuration.getOrDefault("algorithm", ""); boolean valid = signing && (value.startsWith("ML-DSA") || value.startsWith("SLH-DSA")) || kem && value.startsWith("ML-KEM"); if (!valid) throw new IllegalArgumentException("Unsupported PQC algorithm for this operation"); }
    private static PortDefinition port(String name, Set<Representation> reps, boolean required) { return new PortDefinition(name, reps, required); }
    private static NodeDescriptor desc(String type, String key, List<NodeParameter> params) { return new NodeDescriptor(type, "Envelopes / Signatures", "module.process.type." + key, "module.process.desc." + key, "✍", params); }
    private static List<NodeParameter> params(NodeParameter... p) { return List.of(p); }
    private static List<NodeParameter> plus(List<NodeParameter> base, NodeParameter extra) { List<NodeParameter> out = new ArrayList<>(base); out.add(extra); return List.copyOf(out); }
    private static NodeParameter file(String key) { return new NodeParameter(key, "module.process.param." + key, ParameterKind.FILE_OPEN, ""); }
    private static NodeParameter plain(String key) { return new NodeParameter(key, "module.process.param." + key, ParameterKind.TEXT, ""); }
    private static NodeParameter plainDefault(String key, String value) { return new NodeParameter(key, "module.process.param." + key, ParameterKind.TEXT, value); }
    private static NodeParameter multiline(String key) { return new NodeParameter(key, "module.process.param." + key, ParameterKind.MULTILINE, ""); }
    private static NodeParameter secret(String key) { return new NodeParameter(key, "module.process.param." + key, ParameterKind.PASSWORD, List.of(), "", true); }
    private static NodeParameter combo(String key, List<String> options, String value) { return new NodeParameter(key, "module.process.param." + key, ParameterKind.COMBO, options, value); }
    private static NodeParameter check(String key, String value) { return new NodeParameter(key, "module.process.param." + key, ParameterKind.CHECKBOX, value); }
    private static NodeParameter number(String key, String value) { return new NodeParameter(key, "module.process.param." + key, ParameterKind.NUMBER, value); }
    private static List<String> concat(List<String> a, List<String> b) { List<String> values = new ArrayList<>(a); values.addAll(b); return List.copyOf(values); }
}
