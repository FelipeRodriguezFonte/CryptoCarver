package com.cryptocarver.model.process.handlers;

import com.cryptocarver.crypto.WssEncryptionOperations;
import com.cryptocarver.crypto.WssSecurityOperations;
import com.cryptocarver.crypto.WssUsernameTokenOperations;
import com.cryptocarver.model.process.ExecutionContext;
import com.cryptocarver.model.process.FlowValue;
import com.cryptocarver.model.process.ProcessDefinition;
import com.cryptocarver.model.process.ProcessNodeHandler;
import com.cryptocarver.model.process.Representation;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Process Designer adapter for WS-Security SOAP operations. */
public final class WssNodeHandler implements ProcessNodeHandler {

    public static final String ENCRYPT_BODY = "WSS_ENCRYPT_BODY";
    public static final String DECRYPT_BODY = "WSS_DECRYPT_BODY";
    public static final String SIGN_BODY = "WSS_SIGN_BODY";
    public static final String VERIFY_SIGNATURE = "WSS_VERIFY_SIGNATURE";
    public static final String ADD_USERNAME_TOKEN = "WSS_USERNAME_TOKEN_ADD";
    public static final String VERIFY_USERNAME_TOKEN = "WSS_USERNAME_TOKEN_VERIFY";

    @Override
    public Set<String> supportedTypes() {
        return Set.of(ENCRYPT_BODY, DECRYPT_BODY, SIGN_BODY, VERIFY_SIGNATURE,
                ADD_USERNAME_TOKEN, VERIFY_USERNAME_TOKEN);
    }

    @Override
    public List<PortDefinition> inputPorts(ProcessDefinition.Node node) {
        return List.of(new PortDefinition("payload", Set.of(Representation.TEXT_UTF8), true));
    }

    @Override
    public Representation outputRepresentation(ProcessDefinition.Node node,
                                                   Map<String, Representation> inputs) {
        return Representation.TEXT_UTF8;
    }

    @Override
    public void validateConfiguration(ProcessDefinition.Node node) {
        if (ENCRYPT_BODY.equals(node.type)) {
            readablePath(node, "materialPath", "Recipient certificate");
            WssEncryptionOperations.DataEncryptionAlgorithm.fromDisplayName(
                    node.configuration.getOrDefault("dataAlgorithm", "AES-256-GCM"));
            WssEncryptionOperations.KeyTransportAlgorithm.fromDisplayName(
                    node.configuration.getOrDefault("keyTransportAlgorithm", "RSA-OAEP SHA-256"));
            return;
        }
        if (DECRYPT_BODY.equals(node.type)) {
            validateKeyStore(node, false);
            return;
        }
        if (SIGN_BODY.equals(node.type)) {
            validateKeyStore(node, true);
            WssSecurityOperations.WssSignatureAlgorithm.fromDisplayName(
                    node.configuration.getOrDefault("signatureAlgorithm", "RSA_SHA256"));
            if (Boolean.parseBoolean(node.configuration.getOrDefault("timestampEnabled", "false"))) {
                integerInRange(node, "timestampMinutes", 1, 1440, "Timestamp validity");
            }
            return;
        }
        if (VERIFY_SIGNATURE.equals(node.type)) {
            optionalReadablePath(node, "materialPath", "Trusted certificate");
            return;
        }
        if (ADD_USERNAME_TOKEN.equals(node.type)) {
            required(node, "username", "Username");
            required(node, "wssPassword", "Password");
            WssUsernameTokenOperations.PasswordType.fromDisplayName(
                    node.configuration.getOrDefault("passwordType", "PasswordDigest"));
            return;
        }
        if (VERIFY_USERNAME_TOKEN.equals(node.type)) {
            required(node, "username", "Expected username");
            required(node, "wssPassword", "Expected password");
            integerInRange(node, "maxAgeSeconds", 1, 86_400, "Maximum token age");
        }
    }

    @Override
    public FlowValue execute(ProcessDefinition.Node node, Map<String, FlowValue> inputs,
                             ExecutionContext context) throws Exception {
        FlowValue payload = inputs.get("payload");
        if (payload == null) throw new IllegalArgumentException("SOAP XML payload is required.");
        String xml = new String(payload.bytes(),
                payload.charset() != null ? payload.charset() : StandardCharsets.UTF_8);

        return switch (node.type) {
            case ENCRYPT_BODY -> executeEncrypt(node, xml);
            case DECRYPT_BODY -> executeDecrypt(node, xml);
            case SIGN_BODY -> executeSign(node, xml);
            case VERIFY_SIGNATURE -> executeSignatureVerification(node, xml);
            case ADD_USERNAME_TOKEN -> executeAddUsernameToken(node, xml);
            case VERIFY_USERNAME_TOKEN -> executeUsernameTokenVerification(node, xml);
            default -> throw new IllegalArgumentException("Unsupported WSS node type: " + node.type);
        };
    }

    private static FlowValue executeEncrypt(ProcessDefinition.Node node, String xml) throws Exception {
        X509Certificate certificate = loadCertificate(Path.of(node.configuration.get("materialPath")));
        WssEncryptionOperations.OperationResult result = WssEncryptionOperations.encryptSoapBody(
                xml,
                certificate,
                WssEncryptionOperations.DataEncryptionAlgorithm.fromDisplayName(
                        node.configuration.getOrDefault("dataAlgorithm", "AES-256-GCM")),
                WssEncryptionOperations.KeyTransportAlgorithm.fromDisplayName(
                        node.configuration.getOrDefault("keyTransportAlgorithm", "RSA-OAEP SHA-256")));
        return successfulXml(result);
    }

    private static FlowValue executeDecrypt(ProcessDefinition.Node node, String xml) throws Exception {
        char[] storePassword = node.configuration.get("keystorePassword").toCharArray();
        char[] keyPassword = node.configuration.get("keyPassword").toCharArray();
        try {
            return successfulXml(WssEncryptionOperations.decryptSoapBody(
                    xml, loadKeyStore(node, storePassword), keyPassword));
        } finally {
            java.util.Arrays.fill(storePassword, '\0');
            java.util.Arrays.fill(keyPassword, '\0');
        }
    }

    private static FlowValue executeSign(ProcessDefinition.Node node, String xml) throws Exception {
        char[] storePassword = node.configuration.get("keystorePassword").toCharArray();
        char[] keyPassword = node.configuration.get("keyPassword").toCharArray();
        try {
            boolean timestampEnabled = Boolean.parseBoolean(
                    node.configuration.getOrDefault("timestampEnabled", "false"));
            WssSecurityOperations.WssTimestampOptions timestamp = timestampEnabled
                    ? new WssSecurityOperations.WssTimestampOptions(
                            true,
                            Integer.parseInt(node.configuration.getOrDefault("timestampMinutes", "5")),
                            Boolean.parseBoolean(node.configuration.getOrDefault("timestampSigned", "true")))
                    : WssSecurityOperations.WssTimestampOptions.disabled();
            String signed = WssSecurityOperations.signSoapBody(
                    xml,
                    loadKeyStore(node, storePassword),
                    node.configuration.get("alias"),
                    keyPassword,
                    WssSecurityOperations.WssSignatureAlgorithm.fromDisplayName(
                            node.configuration.getOrDefault("signatureAlgorithm", "RSA_SHA256")),
                    timestamp);
            return FlowValue.text(signed, StandardCharsets.UTF_8);
        } finally {
            java.util.Arrays.fill(storePassword, '\0');
            java.util.Arrays.fill(keyPassword, '\0');
        }
    }

    private static FlowValue executeSignatureVerification(ProcessDefinition.Node node, String xml) throws Exception {
        String path = node.configuration.get("materialPath");
        X509Certificate trusted = path == null || path.isBlank() ? null : loadCertificate(Path.of(path));
        WssSecurityOperations.WssVerificationResult result =
                WssSecurityOperations.verifySoapSignature(xml, trusted);
        if (result.getStatus() == WssSecurityOperations.WssVerificationResult.Status.ERROR) {
            throw new IllegalArgumentException(result.getMessage());
        }
        return FlowValue.text(result.getStatus().name() + "\n" + result.getMessage() + "\n"
                + result.getTechnicalDetails(), StandardCharsets.UTF_8);
    }

    private static FlowValue executeAddUsernameToken(ProcessDefinition.Node node, String xml) throws Exception {
        char[] password = node.configuration.get("wssPassword").toCharArray();
        try {
            return FlowValue.text(WssUsernameTokenOperations.addUsernameToken(
                    xml,
                    node.configuration.get("username"),
                    password,
                    WssUsernameTokenOperations.PasswordType.fromDisplayName(
                            node.configuration.getOrDefault("passwordType", "PasswordDigest"))),
                    StandardCharsets.UTF_8);
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    private static FlowValue executeUsernameTokenVerification(ProcessDefinition.Node node, String xml) {
        char[] password = node.configuration.get("wssPassword").toCharArray();
        try {
            WssUsernameTokenOperations.VerificationResult result =
                    WssUsernameTokenOperations.verifyUsernameToken(
                            xml,
                            node.configuration.get("username"),
                            password,
                            Integer.parseInt(node.configuration.getOrDefault("maxAgeSeconds", "300")));
            if (result.status() == WssUsernameTokenOperations.VerificationResult.Status.ERROR) {
                throw new IllegalArgumentException(result.message());
            }
            return FlowValue.text(result.status().name() + "\n" + result.message() + "\n"
                    + result.technicalDetails(), StandardCharsets.UTF_8);
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    private static FlowValue successfulXml(WssEncryptionOperations.OperationResult result) {
        if (result.status() != WssEncryptionOperations.OperationResult.Status.SUCCESS) {
            throw new IllegalArgumentException(result.message());
        }
        return FlowValue.text(result.xml(), StandardCharsets.UTF_8);
    }

    private static X509Certificate loadCertificate(Path path) throws Exception {
        try (InputStream input = Files.newInputStream(path)) {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
        }
    }

    private static KeyStore loadKeyStore(ProcessDefinition.Node node, char[] storePassword) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(node.configuration.getOrDefault("keystoreType", "PKCS12"));
        try (InputStream input = Files.newInputStream(Path.of(node.configuration.get("keystorePath")))) {
            keyStore.load(input, storePassword);
        }
        return keyStore;
    }

    private static void validateKeyStore(ProcessDefinition.Node node, boolean aliasRequired) {
        readablePath(node, "keystorePath", "WSS KeyStore");
        required(node, "keystorePassword", "KeyStore password");
        required(node, "keyPassword", "Private key password");
        if (aliasRequired) required(node, "alias", "Private key alias");
        String type = node.configuration.getOrDefault("keystoreType", "PKCS12");
        if (!Set.of("PKCS12", "JKS").contains(type)) {
            throw new IllegalArgumentException("Unsupported WSS KeyStore type: " + type);
        }
    }

    private static void optionalReadablePath(ProcessDefinition.Node node, String key, String label) {
        String value = node.configuration.get(key);
        if (value == null || value.isBlank()) return;
        Path path = Path.of(value);
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalArgumentException(label + " file is not readable: " + value);
        }
    }

    private static int integerInRange(ProcessDefinition.Node node, String key, int minimum, int maximum,
                                      String label) {
        String value = required(node, key, label);
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < minimum || parsed > maximum) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " must be between " + minimum + " and " + maximum + ".");
        }
    }

    private static void readablePath(ProcessDefinition.Node node, String key, String label) {
        String value = required(node, key, label + " path");
        Path path = Path.of(value);
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalArgumentException(label + " file is not readable: " + value);
        }
    }

    private static String required(ProcessDefinition.Node node, String key, String label) {
        String value = node.configuration.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required for " + node.label + ".");
        }
        return value;
    }
}
