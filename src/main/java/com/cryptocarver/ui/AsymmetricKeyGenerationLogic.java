package com.cryptocarver.ui;

import com.cryptocarver.crypto.AsymmetricKeyOperations;
import java.security.KeyPair;

/** Pure encoding and report construction for generated pairs. */
final class AsymmetricKeyGenerationLogic {
    private AsymmetricKeyGenerationLogic() { }
    record Material(String publicPem, String privatePem, String publicReport, String privateReport) { }

    static Material material(KeyPair pair, String algorithm, String parameter) throws Exception {
        String publicPem = AsymmetricKeyOperations.exportPublicKeyPEM(pair.getPublic());
        String privatePem = AsymmetricKeyOperations.exportPrivateKeyPEM(pair.getPrivate());
        String publicInfo;
        String privateInfo;
        String heading;
        switch (algorithm) {
            case "RSA" -> {
                publicInfo = AsymmetricKeyOperations.getRSAPublicKeyInfo(pair.getPublic());
                privateInfo = AsymmetricKeyOperations.getRSAPrivateKeyInfo(pair.getPrivate());
                heading = "RSA";
            }
            case "DSA" -> {
                publicInfo = AsymmetricKeyOperations.getDSAKeyInfo(pair.getPublic());
                privateInfo = AsymmetricKeyOperations.getDSAKeyInfo(pair.getPrivate());
                heading = "DSA";
            }
            case "ECDSA" -> {
                publicInfo = AsymmetricKeyOperations.getECKeyInfo(pair.getPublic());
                privateInfo = AsymmetricKeyOperations.getECKeyInfo(pair.getPrivate());
                return new Material(publicPem, privatePem,
                        "=== ECDSA F(p) PUBLIC KEY ===\nCurve: " + parameter + "\n\n" + publicInfo + "\n\n=== PEM FORMAT ===\n" + publicPem,
                        "=== ECDSA F(p) PRIVATE KEY ===\nCurve: " + parameter + "\n\n" + privateInfo + "\n\n=== PEM FORMAT ===\n" + privatePem);
            }
            case "Ed25519" -> {
                String info = "Algorithm: Ed25519 (255-bit curve)\nUse: Digital signatures (fast, secure)\n\n=== PEM FORMAT ===\n";
                return new Material(publicPem, privatePem,
                        "=== Ed25519 PUBLIC KEY ===\n" + info + publicPem,
                        "=== Ed25519 PRIVATE KEY ===\n" + info + privatePem);
            }
            default -> throw new IllegalArgumentException("Unsupported generation algorithm");
        }
        return new Material(publicPem, privatePem,
                "=== " + heading + " PUBLIC KEY ===\n\n" + publicInfo + "\n\n=== PEM FORMAT ===\n" + publicPem,
                "=== " + heading + " PRIVATE KEY ===\n\n" + privateInfo + "\n\n=== PEM FORMAT ===\n" + privatePem);
    }

    static String renderGeneratedKeyPair(String publicKeyPem, String privateKeyPem) {
        return "=== PUBLIC KEY ===\n\n" + publicKeyPem
                + "\n\n=== PRIVATE KEY ===\n\n" + privateKeyPem;
    }
}
