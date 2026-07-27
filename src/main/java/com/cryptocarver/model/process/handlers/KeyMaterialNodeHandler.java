package com.cryptocarver.model.process.handlers;

import com.cryptocarver.model.process.ExecutionContext;
import com.cryptocarver.model.process.FlowValue;
import com.cryptocarver.model.process.ProcessDefinition;
import com.cryptocarver.model.process.ProcessNodeHandler;
import com.cryptocarver.model.process.Representation;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Generates lab key material that can be routed directly to a crypto node's key port. */
public class KeyMaterialNodeHandler implements ProcessNodeHandler {
    @Override
    public Set<String> supportedTypes() {
        return Set.of("AES_KEY_GENERATE", "KDF_PBKDF2", "RSA_KEYPAIR_GENERATE");
    }

    @Override
    public List<PortDefinition> inputPorts(ProcessDefinition.Node node) {
        if ("KDF_PBKDF2".equals(node.type)) {
            return List.of(new PortDefinition("input", Set.of(Representation.values()), true));
        }
        return List.of();
    }

    @Override
    public Representation outputRepresentation(ProcessDefinition.Node node, Map<String, Representation> inputs) {
        return Representation.BINARY;
    }

    @Override
    public FlowValue execute(ProcessDefinition.Node node, Map<String, FlowValue> inputs, ExecutionContext context) throws Exception {
        return switch (node.type) {
            case "AES_KEY_GENERATE" -> generateSymmetricKey(node);
            case "KDF_PBKDF2" -> derivePbkdf2(node, inputs.get("input"));
            case "RSA_KEYPAIR_GENERATE" -> generateRsa(node);
            default -> throw new IllegalArgumentException("Unknown key-material operation: " + node.type);
        };
    }

    private FlowValue generateSymmetricKey(ProcessDefinition.Node node) throws Exception {
        String algorithm = node.configuration.getOrDefault("keyAlgorithm", "AES");
        if ("3DES".equals(algorithm)) {
            KeyGenerator generator = KeyGenerator.getInstance("DESede");
            generator.init(168);
            return FlowValue.binary(generator.generateKey().getEncoded());
        }
        if (!"AES".equals(algorithm)) {
            throw new IllegalArgumentException("Unsupported generated key algorithm: " + algorithm);
        }
        int bits = integerSetting(node, "keySize", 256, Set.of(128, 192, 256));
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(bits);
        return FlowValue.binary(generator.generateKey().getEncoded());
    }

    private FlowValue derivePbkdf2(ProcessDefinition.Node node, FlowValue input) throws Exception {
        if (input == null) throw new IllegalArgumentException("PBKDF2 requires a password input.");
        int bits = integerSetting(node, "keySize", 256, Set.of(128, 192, 256));
        int iterations = integerSetting(node, "iterations", 210_000, null);
        if (iterations < 10_000) throw new IllegalArgumentException("PBKDF2 iterations must be at least 10000.");
        byte[] salt = salt(node);
        char[] password = new String(input.bytes(), input.charset() != null ? input.charset() : StandardCharsets.UTF_8).toCharArray();
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, bits);
            try {
                return FlowValue.binary(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded());
            } finally {
                spec.clearPassword();
            }
        } finally {
            java.util.Arrays.fill(password, '\0');
            java.util.Arrays.fill(salt, (byte) 0);
        }
    }

    private FlowValue generateRsa(ProcessDefinition.Node node) throws Exception {
        int bits = integerSetting(node, "keySize", 2048, Set.of(2048, 3072, 4096));
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(bits);
        // PKCS#8 private material lets a subsequent SIGN node derive its matching RSA public key when needed.
        return FlowValue.binary(generator.generateKeyPair().getPrivate().getEncoded());
    }

    private byte[] salt(ProcessDefinition.Node node) {
        String configured = node.configuration.get("salt");
        if (configured == null || configured.isBlank()) {
            byte[] generated = new byte[16];
            new SecureRandom().nextBytes(generated);
            node.configuration.put("salt", Base64.getEncoder().encodeToString(generated));
            return generated;
        }
        try {
            return Base64.getDecoder().decode(configured);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("PBKDF2 salt must be Base64.", e);
        }
    }

    private static int integerSetting(ProcessDefinition.Node node, String name, int defaultValue, Set<Integer> allowed) {
        int value;
        try {
            value = Integer.parseInt(node.configuration.getOrDefault(name, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be a number.", e);
        }
        if (allowed != null && !allowed.contains(value)) {
            throw new IllegalArgumentException(name + " must be one of " + allowed + ".");
        }
        return value;
    }
}
