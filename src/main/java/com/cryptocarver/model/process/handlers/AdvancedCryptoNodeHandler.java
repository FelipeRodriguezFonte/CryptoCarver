package com.cryptocarver.model.process.handlers;

import com.cryptocarver.crypto.AsymmetricKeyOperations;
import com.cryptocarver.crypto.MACOperations;
import com.cryptocarver.crypto.SignatureOperations;
import com.cryptocarver.crypto.SymmetricCipher;
import com.cryptocarver.model.process.ExecutionContext;
import com.cryptocarver.model.process.FlowValue;
import com.cryptocarver.model.process.ProcessDefinition;
import com.cryptocarver.model.process.ProcessNodeHandler;
import com.cryptocarver.model.process.Representation;
import java.util.HexFormat;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AdvancedCryptoNodeHandler implements ProcessNodeHandler {

    private static final byte[] MAGIC = "CFGE".getBytes(StandardCharsets.US_ASCII);
    private static final byte VERSION = 1;
    private static final byte ALGO_AES_GCM = 1;
    private static final byte ALGO_AES_CBC = 2;
    private static final byte ALGO_AES_CTR = 3;
    private static final byte ALGO_TDES_CBC = 4;
    private static final byte ALGO_CHACHA20_POLY1305 = 5;
    private static final byte ALGO_TDES_CBC_NO_PADDING = 6;

    @Override
    public void validateConfiguration(ProcessDefinition.Node node) throws IllegalArgumentException {
        switch (node.type) {
            case "ENCRYPT":
            case "DECRYPT":
            case "MAC":
                String macAlgorithm = null;
                if ("MAC".equals(node.type)) {
                    macAlgorithm = normalizeMacAlgorithm(node.configuration.getOrDefault("algorithm", "HmacSHA256"));
                    validateMacAlgorithm(macAlgorithm);
                }
                if (!"MAC".equals(node.type)) {
                    SymmetricCipherSpec spec = SymmetricCipherSpec.fromAlgorithm(node.configuration.getOrDefault("algorithm", "AES/GCM/NoPadding"));
                    if (!spec.supportsEnvelope && "ENVELOPE".equals(node.configuration.getOrDefault("outputFormat", "RAW"))) {
                        throw new IllegalArgumentException("Unsupported envelope algorithm: " + spec.algorithm);
                    }
                    if (spec.ivLength > 0) {
                        boolean needsIv = true;
                        if ("DECRYPT".equals(node.type) && "ENVELOPE".equals(node.configuration.getOrDefault("outputFormat", "RAW"))) {
                            needsIv = false;
                        }
                        if (needsIv) {
                            validateConfiguredIv(node, spec);
                        }
                    }
                }

                if (!Boolean.parseBoolean(node.configuration.getOrDefault("keyFromFlow", "false"))) {
                    byte[] key = getKeyFromConfig(node);
                    try {
                        if ("MAC".equals(node.type)) {
                            validateMacKey(key, macAlgorithm);
                        } else {
                            SymmetricCipherSpec spec = SymmetricCipherSpec.fromAlgorithm(node.configuration.getOrDefault("algorithm", "AES/GCM/NoPadding"));
                            if (key.length > 0 && !spec.acceptedKeySizes.contains(key.length)) {
                                throw new IllegalArgumentException(spec.algorithm + " requires a key size of " + spec.acceptedKeySizes + " bytes. Actual: " + key.length);
                            }
                        }
                    } finally {
                        clearArray(key);
                    }
                }
                break;
            case "SIGN":
                String keystorePath = node.configuration.get("keystorePath");
                String keystoreType = node.configuration.getOrDefault("keystoreType", "PKCS12");
                String alias = node.configuration.get("alias");
                String keystorePass = node.configuration.get("keystorePassword");
                String keyPass = node.configuration.get("keyPassword");

                if (Boolean.parseBoolean(node.configuration.getOrDefault("keyFromFlow", "false"))) break;
                if (keystorePath == null || alias == null || keystorePass == null || keyPass == null) {
                    throw new IllegalArgumentException("Incomplete SIGN configuration. Ensure keystore path, alias, and passwords are set.");
                }

                try (InputStream is = new FileInputStream(keystorePath)) {
                    KeyStore ks = KeyStore.getInstance(keystoreType);
                    ks.load(is, keystorePass.toCharArray());
                    PrivateKey privateKey = (PrivateKey) ks.getKey(alias, keyPass.toCharArray());
                    if (privateKey == null) {
                        throw new IllegalArgumentException("Private key not found for alias: " + alias);
                    }
                } catch (IllegalArgumentException e) {
                    throw e;
                } catch (Exception e) {
                    throw new IllegalArgumentException("Failed to validate SIGN configuration: " + e.getMessage(), e);
                }
                break;
            case "VERIFY":
                String materialPath = node.configuration.get("materialPath");
                String materialType = node.configuration.getOrDefault("materialType", "CERTIFICATE");
                if (materialPath == null) {
                    throw new IllegalArgumentException("Public material path is required for VERIFY.");
                }
                try {
                    if ("CERTIFICATE".equals(materialType)) {
                        CertificateFactory f = CertificateFactory.getInstance("X.509");
                        try (InputStream is = new FileInputStream(materialPath)) {
                            f.generateCertificate(is);
                        }
                    } else if ("PEM".equals(materialType)) {
                        String pem = Files.readString(Paths.get(materialPath));
                        AsymmetricKeyOperations.importPublicKeyPEMAuto(pem);
                    } else {
                        throw new IllegalArgumentException("Unsupported material type: " + materialType);
                    }
                } catch (IllegalArgumentException e) {
                    throw e;
                } catch (Exception e) {
                    throw new IllegalArgumentException("Failed to validate VERIFY configuration: " + e.getMessage(), e);
                }
                break;
        }
    }

    @Override
    public Set<String> supportedTypes() {
        return Set.of("ENCRYPT", "DECRYPT", "MAC", "SIGN", "VERIFY");
    }

    @Override
    public List<PortDefinition> inputPorts(ProcessDefinition.Node node) {
        switch (node.type) {
            case "VERIFY":
                return List.of(
                    new PortDefinition("payload", Set.of(Representation.values()), true),
                    new PortDefinition("signature", Set.of(Representation.BINARY), true)
                );
            case "DECRYPT":
                return getCryptoPorts(node, Set.of(Representation.BINARY));
            case "ENCRYPT":
            case "MAC":
            case "SIGN":
                return getCryptoPorts(node, Set.of(Representation.values()));
            default:
                return List.of(new PortDefinition("payload", Set.of(Representation.values()), true));
        }
    }

    private List<PortDefinition> getCryptoPorts(ProcessDefinition.Node node, Set<Representation> payloadReps) {
        List<PortDefinition> ports = new java.util.ArrayList<>();
        ports.add(new PortDefinition("payload", payloadReps, true));
        ports.add(new PortDefinition("key", Set.of(Representation.BINARY), false));

        if ("ENCRYPT".equals(node.type) || "DECRYPT".equals(node.type)) {
            String algorithm = node.configuration.getOrDefault("algorithm", "AES/GCM/NoPadding");
            SymmetricCipherSpec spec = SymmetricCipherSpec.fromAlgorithm(algorithm);
            if (spec.ivLength > 0) {
                ports.add(new PortDefinition("iv", Set.of(Representation.BINARY), false));
            }
            if (spec.aead) {
                ports.add(new PortDefinition("aad", Set.of(Representation.values()), false));
            }
        }

        return ports;
    }

    @Override
    public Representation outputRepresentation(ProcessDefinition.Node node, Map<String, Representation> inputs) {
        if ("VERIFY".equals(node.type)) {
            return Representation.TEXT_UTF8;
        }
        return Representation.BINARY;
    }

    @Override
    public FlowValue execute(ProcessDefinition.Node node, Map<String, FlowValue> inputs, ExecutionContext context) throws Exception {
        FlowValue payload = inputs.getOrDefault("payload", FlowValue.binary(new byte[0]));

        switch (node.type) {
            case "ENCRYPT":
                return handleEncrypt(node, payload.bytes(), keyFrom(node, inputs), inputs);
            case "DECRYPT":
                return handleDecrypt(node, payload.bytes(), keyFrom(node, inputs), inputs);
            case "MAC":
                return handleMac(node, payload.bytes(), keyFrom(node, inputs));
            case "SIGN":
                return handleSign(node, payload.bytes(), inputs.get("key"));
            case "VERIFY":
                FlowValue signature = inputs.getOrDefault("signature", FlowValue.binary(new byte[0]));
                return handleVerify(node, payload.bytes(), signature.bytes());
            default:
                throw new IllegalArgumentException("Unknown operation: " + node.type);
        }
    }

    private FlowValue handleEncrypt(ProcessDefinition.Node node, byte[] payload, byte[] key, Map<String, FlowValue> inputs) throws Exception {
        String algorithm = node.configuration.getOrDefault("algorithm", "AES/GCM/NoPadding");
        SymmetricCipherSpec spec = SymmetricCipherSpec.fromAlgorithm(algorithm);

        try {
            byte[] iv = spec.ivLength > 0 ? getOrGenerateIv(node, spec.ivLength, inputs) : new byte[0];
            byte[] aad = (spec.aead && inputs.containsKey("aad")) ? inputs.get("aad").bytes() : null;
            byte[] ciphertext = SymmetricCipher.encrypt(payload, key, spec.cipherType, spec.mode, spec.padding, iv, aad);

            if (!spec.supportsEnvelope || !"ENVELOPE".equals(node.configuration.getOrDefault("outputFormat", "RAW"))) {
                return FlowValue.binary(ciphertext);
            }

            // Optional lab envelope: IVs/nonces are public and embedded to make a chained decrypt self-describing.
            ByteBuffer buffer = ByteBuffer.allocate(4 + 1 + 1 + 1 + iv.length + ciphertext.length);
            buffer.put(MAGIC);
            buffer.put(VERSION);
            buffer.put(algorithmId(algorithm));
            buffer.put((byte) iv.length);
            buffer.put(iv);
            buffer.put(ciphertext);

            return FlowValue.binary(buffer.array());
        } finally {
            clearArray(key);
        }
    }

    private FlowValue handleDecrypt(ProcessDefinition.Node node, byte[] ciphertextOrEnvelope, byte[] key, Map<String, FlowValue> inputs) throws Exception {
        String configuredAlgorithm = node.configuration.getOrDefault("algorithm", "AES/GCM/NoPadding");
        SymmetricCipherSpec configuredSpec = SymmetricCipherSpec.fromAlgorithm(configuredAlgorithm);

        if (!isEnvelope(ciphertextOrEnvelope)) {
            byte[] iv = getConfiguredIv(node, configuredSpec.ivLength, inputs);
            byte[] aad = inputs.containsKey("aad") ? inputs.get("aad").bytes() : null;
            try {
                return FlowValue.binary(SymmetricCipher.decrypt(ciphertextOrEnvelope, key, configuredSpec.cipherType, configuredSpec.mode, configuredSpec.padding, iv, aad));
            } finally {
                clearArray(key);
                if (inputs.get("iv") == null && iv.length > 0) clearArray(iv);
            }
        }

        if (!configuredSpec.supportsEnvelope) {
            throw new IllegalArgumentException("Unsupported envelope algorithm for DECRYPT: " + configuredAlgorithm);
        }
        ByteBuffer buffer = ByteBuffer.wrap(ciphertextOrEnvelope);
        byte[] magic = new byte[4];
        buffer.get(magic);
        if (!java.util.Arrays.equals(magic, MAGIC)) {
            throw new IllegalArgumentException("Invalid envelope: bad magic");
        }

        byte version = buffer.get();
        if (version != VERSION) {
            throw new IllegalArgumentException("Unsupported envelope version: " + version);
        }

        String algorithm = algorithmFromId(buffer.get());
        SymmetricCipherSpec spec = SymmetricCipherSpec.fromAlgorithm(algorithm);
        if (!spec.supportsEnvelope) {
            throw new IllegalArgumentException("Unsupported envelope algorithm for DECRYPT: " + algorithm);
        }

        int ivLength = buffer.get() & 0xFF;
        if (ivLength != spec.ivLength) {
            throw new IllegalArgumentException(String.format("Invalid envelope: IV length %d does not match %d", ivLength, spec.ivLength));
        }
        int minimumCiphertextLength = spec.aead ? 16 : 1;
        if (buffer.remaining() < ivLength + minimumCiphertextLength) {
            throw new IllegalArgumentException("Invalid envelope: corrupt data");
        }

        byte[] iv = new byte[ivLength];
        if (ivLength > 0) buffer.get(iv);

        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        byte[] aad = inputs.containsKey("aad") ? inputs.get("aad").bytes() : null;
        try {
            return FlowValue.binary(SymmetricCipher.decrypt(ciphertext, key, spec.cipherType, spec.mode, spec.padding, iv, aad));
        } finally {
            clearArray(key);
            clearArray(iv);
        }
    }

    private FlowValue handleMac(ProcessDefinition.Node node, byte[] payload, byte[] key) throws Exception {
        String algorithm = normalizeMacAlgorithm(node.configuration.getOrDefault("algorithm", "HmacSHA256"));
        try {
            validateMacAlgorithm(algorithm);
            validateMacKey(key, algorithm);
            byte[] mac = MACOperations.generate(payload, key, algorithm);
            return FlowValue.binary(mac);
        } finally {
            clearArray(key);
        }
    }

    private static String normalizeMacAlgorithm(String algorithm) {
        if (algorithm == null) return "HMAC-SHA256";
        return switch (algorithm.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "HMACSHA256", "HMAC-SHA256" -> "HMAC-SHA256";
            case "HMACSHA384", "HMAC-SHA384" -> "HMAC-SHA384";
            case "HMACSHA512", "HMAC-SHA512" -> "HMAC-SHA512";
            case "AES-CMAC", "CMAC-AES" -> "CMAC-AES";
            case "3DES-CMAC", "DESEDE-CMAC", "CMAC-3DES" -> "CMAC-3DES";
            default -> algorithm;
        };
    }

    private static void validateMacAlgorithm(String algorithm) {
        if (!Set.of("HMAC-SHA256", "HMAC-SHA384", "HMAC-SHA512", "CMAC-AES", "CMAC-3DES").contains(algorithm)) {
            throw new IllegalArgumentException("Unsupported MAC algorithm: " + algorithm);
        }
    }

    private static void validateMacKey(byte[] key, String algorithm) {
        if (key == null) throw new IllegalArgumentException("MAC key is required");
        if (algorithm.startsWith("HMAC-") && key.length < 16) {
            throw new IllegalArgumentException("HMAC key should be at least 128 bits (16 bytes)");
        }
        if ("CMAC-AES".equals(algorithm) && key.length != 16 && key.length != 24 && key.length != 32) {
            throw new IllegalArgumentException("CMAC-AES requires a 16, 24, or 32-byte AES key. Actual: " + key.length);
        }
        if ("CMAC-3DES".equals(algorithm) && key.length != 16 && key.length != 24) {
            throw new IllegalArgumentException("CMAC-3DES requires a 16 or 24-byte 3DES key. Actual: " + key.length);
        }
    }

    private FlowValue handleSign(ProcessDefinition.Node node, byte[] payload, FlowValue generatedKey) throws Exception {
        String algo = node.configuration.getOrDefault("algorithm", "SHA256withRSA");
        if (generatedKey != null) {
            PrivateKey privateKey = java.security.KeyFactory.getInstance("RSA")
                    .generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(generatedKey.bytes()));
            return FlowValue.binary(SignatureOperations.sign(payload, privateKey, "RSA-SHA256-PKCS1"));
        }
        String keystorePath = node.configuration.get("keystorePath");
        String keystoreType = node.configuration.getOrDefault("keystoreType", "PKCS12");
        String alias = node.configuration.get("alias");
        String keystorePass = node.configuration.get("keystorePassword");
        String keyPass = node.configuration.get("keyPassword");

        if (keystorePath == null || alias == null || keystorePass == null || keyPass == null) {
            throw new IllegalArgumentException("Incomplete SIGN configuration. Ensure keystore path, alias, and passwords are set.");
        }

        KeyStore ks = KeyStore.getInstance(keystoreType);
        try (InputStream is = new FileInputStream(keystorePath)) {
            ks.load(is, keystorePass.toCharArray());
        }

        PrivateKey privateKey = (PrivateKey) ks.getKey(alias, keyPass.toCharArray());
        if (privateKey == null) {
            throw new IllegalArgumentException("Private key not found for alias: " + alias);
        }

        String internalAlgo = algo.equals("SHA256withRSA") ? "RSA-SHA256-PKCS1" : algo;
        if (algo.equals("SHA256withECDSA")) internalAlgo = "ECDSA-SHA256";

        byte[] signature = SignatureOperations.sign(payload, privateKey, internalAlgo);
        return FlowValue.binary(signature);
    }

    private FlowValue handleVerify(ProcessDefinition.Node node, byte[] payload, byte[] signature) throws Exception {
        String algo = node.configuration.getOrDefault("algorithm", "SHA256withRSA");
        String materialType = node.configuration.getOrDefault("materialType", "CERTIFICATE");
        String materialPath = node.configuration.get("materialPath");

        if (materialPath == null) {
            throw new IllegalArgumentException("Public material path is required for VERIFY.");
        }

        PublicKey publicKey = null;
        if ("CERTIFICATE".equals(materialType)) {
            CertificateFactory f = CertificateFactory.getInstance("X.509");
            try (InputStream is = new FileInputStream(materialPath)) {
                X509Certificate cert = (X509Certificate) f.generateCertificate(is);
                publicKey = cert.getPublicKey();
            }
        } else if ("PEM".equals(materialType)) {
            String pem = Files.readString(Paths.get(materialPath));
            publicKey = AsymmetricKeyOperations.importPublicKeyPEMAuto(pem);
        } else {
            throw new IllegalArgumentException("Unsupported material type: " + materialType);
        }

        String internalAlgo = algo.equals("SHA256withRSA") ? "RSA-SHA256-PKCS1" : algo;
        if (algo.equals("SHA256withECDSA")) internalAlgo = "ECDSA-SHA256";

        try {
            boolean isValid = SignatureOperations.verify(payload, signature, publicKey, internalAlgo);
            return FlowValue.text(isValid ? "VALID" : "INVALID", StandardCharsets.UTF_8);
        } catch (Exception e) {
            // "Una firma inválida es un resultado criptográfico válido: devolver INVALID, no lanzar excepción."
            return FlowValue.text("INVALID", StandardCharsets.UTF_8);
        }
    }

    private byte[] getKeyFromConfig(ProcessDefinition.Node node) {
        String format = node.configuration.getOrDefault("keyFormat", "HEX");
        String keyStr = node.configuration.get("key");
        if (keyStr == null || keyStr.isBlank()) {
            throw new IllegalArgumentException("Key is missing.");
        }

        if ("HEX".equals(format)) {
            return HexFormat.of().parseHex(keyStr);
        } else if ("BASE64".equals(format)) {
            return Base64.getDecoder().decode(keyStr.trim());
        } else {
            throw new IllegalArgumentException("Unknown key format: " + format);
        }
    }

    private byte[] keyFrom(ProcessDefinition.Node node, Map<String, FlowValue> inputs) {
        FlowValue flowKey = inputs.get("key");
        if (flowKey != null) return flowKey.bytes().clone();
        return getKeyFromConfig(node);
    }

    private void validateConfiguredIv(ProcessDefinition.Node node, SymmetricCipherSpec spec) {
        if (Boolean.parseBoolean(node.configuration.getOrDefault("ivFromFlow", "false"))) return;
        String configuredIv = node.configuration.get("nonce");
        if (configuredIv == null || configuredIv.isBlank()) {
            if ("DECRYPT".equals(node.type) && "RAW".equals(node.configuration.getOrDefault("outputFormat", "RAW"))) {
                throw new IllegalArgumentException("IV/nonce is required to decrypt RAW ciphertext.");
            }
            if (!"false".equalsIgnoreCase(node.configuration.getOrDefault("generateNonce", "true"))) return;
            throw new IllegalArgumentException("IV/nonce is required when auto-generate is disabled.");
        }
        byte[] iv = decodeConfiguredIv(node, configuredIv);
        try {
            if (iv.length != spec.ivLength) {
                throw new IllegalArgumentException(spec.algorithm + " requires a " + spec.ivLength + "-byte IV/nonce.");
            }
        } finally {
            clearArray(iv);
        }
    }

    private byte[] getOrGenerateIv(ProcessDefinition.Node node, int expectedLength, Map<String, FlowValue> inputs) {
        FlowValue flowIv = inputs.get("iv");
        if (flowIv != null) {
            byte[] iv = flowIv.bytes();
            if (iv.length != expectedLength) {
                throw new IllegalArgumentException("port [iv] requires exactly " + expectedLength + " bytes. Actual: " + iv.length);
            }
            return iv;
        }

        String configuredIv = node.configuration.get("nonce");
        if (configuredIv == null || configuredIv.isBlank()) {
            if ("false".equalsIgnoreCase(node.configuration.getOrDefault("generateNonce", "true"))) {
                throw new IllegalArgumentException("IV/nonce is required when auto-generate is disabled.");
            }
            byte[] iv = new byte[expectedLength];
            new SecureRandom().nextBytes(iv);
            node.configuration.put("nonce", encodeConfiguredIv(node, iv));
            return iv;
        }
        byte[] iv = decodeConfiguredIv(node, configuredIv);
        if (iv.length != expectedLength) {
            clearArray(iv);
            throw new IllegalArgumentException("IV/nonce must be exactly " + expectedLength + " bytes for the selected algorithm.");
        }
        return iv;
    }

    private byte[] getConfiguredIv(ProcessDefinition.Node node, int expectedLength, Map<String, FlowValue> inputs) {
        if (expectedLength == 0) return new byte[0];
        FlowValue flowIv = inputs.get("iv");
        if (flowIv != null) {
            byte[] iv = flowIv.bytes();
            if (iv.length != expectedLength) {
                throw new IllegalArgumentException("port [iv] requires exactly " + expectedLength + " bytes. Actual: " + iv.length);
            }
            return iv;
        }

        String configuredIv = node.configuration.get("nonce");
        if (configuredIv == null || configuredIv.isBlank()) {
            throw new IllegalArgumentException("IV/nonce is required to decrypt RAW ciphertext.");
        }
        byte[] iv = decodeConfiguredIv(node, configuredIv);
        if (iv.length != expectedLength) {
            clearArray(iv);
            throw new IllegalArgumentException("IV/nonce must be exactly " + expectedLength + " bytes for the selected algorithm.");
        }
        return iv;
    }

    private boolean isEnvelope(byte[] value) {
        if (value.length < 7) return false;
        for (int i = 0; i < MAGIC.length; i++) {
            if (value[i] != MAGIC[i]) return false;
        }
        return true;
    }

    private byte[] decodeConfiguredIv(ProcessDefinition.Node node, String ivString) {
        String format = node.configuration.getOrDefault("keyFormat", "HEX");
        if ("HEX".equals(format)) {
            return HexFormat.of().parseHex(ivString.trim());
        } else if ("BASE64".equals(format)) {
            return Base64.getDecoder().decode(ivString.trim());
        } else {
            throw new IllegalArgumentException("Unknown nonce format: " + format);
        }
    }

    private String encodeConfiguredIv(ProcessDefinition.Node node, byte[] iv) {
        return "BASE64".equals(node.configuration.getOrDefault("keyFormat", "HEX"))
                ? Base64.getEncoder().encodeToString(iv)
                : HexFormat.of().formatHex(iv);
    }

    private byte algorithmId(String algorithm) {
        return switch (algorithm) {
            case "AES/GCM/NoPadding" -> ALGO_AES_GCM;
            case "AES/CBC/PKCS7Padding", "AES/CBC/PKCS5Padding" -> ALGO_AES_CBC;
            case "AES/CTR/NoPadding" -> ALGO_AES_CTR;
            case "3DES/CBC/PKCS7Padding", "3DES/CBC/PKCS5Padding" -> ALGO_TDES_CBC;
            case "3DES/CBC/NoPadding" -> ALGO_TDES_CBC_NO_PADDING;
            case "ChaCha20-Poly1305" -> ALGO_CHACHA20_POLY1305;
            default -> throw new IllegalArgumentException("Unsupported envelope encryption algorithm: " + algorithm);
        };
    }

    private String algorithmFromId(byte id) {
        return switch (id) {
            case ALGO_AES_GCM -> "AES/GCM/NoPadding";
            case ALGO_AES_CBC -> "AES/CBC/PKCS7Padding";
            case ALGO_AES_CTR -> "AES/CTR/NoPadding";
            case ALGO_TDES_CBC -> "3DES/CBC/PKCS7Padding";
            case ALGO_TDES_CBC_NO_PADDING -> "3DES/CBC/NoPadding";
            case ALGO_CHACHA20_POLY1305 -> "ChaCha20-Poly1305";
            default -> throw new IllegalArgumentException("Unsupported envelope algorithm ID: " + id);
        };
    }

    private void clearArray(byte[] array) {
        if (array != null) {
            java.util.Arrays.fill(array, (byte) 0);
        }
    }
}
