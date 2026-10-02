package com.cryptocarver.ui;

import com.cryptocarver.crypto.AsymmetricKeyOperations;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Material parsing and certificate predicates shared without JavaFX dependencies. */
final class KeysMaterialSupport {
    private KeysMaterialSupport() { }

    static boolean isVerifiedIssuer(X509Certificate issuer, X509Certificate certificate) {
        if (!issuer.getSubjectX500Principal().equals(certificate.getIssuerX500Principal())) {
            return false;
        }
        try {
            certificate.verify(issuer.getPublicKey());
            return true;
        } catch (java.security.GeneralSecurityException e) {
            return false;
        }
    }

    static java.security.PublicKey parsePublicMaterial(String pem) throws Exception {
        if (pem.isBlank()) throw new IllegalArgumentException("Public key or certificate is required");
        if (pem.contains("BEGIN CERTIFICATE")) {
            var factory = java.security.cert.CertificateFactory.getInstance("X.509");
            return ((java.security.cert.X509Certificate) factory.generateCertificate(
                    new java.io.ByteArrayInputStream(pem.getBytes(java.nio.charset.StandardCharsets.US_ASCII)))).getPublicKey();
        }
        return AsymmetricKeyOperations.importPublicKeyPEMAuto(pem);
    }

    static java.security.PrivateKey parsePrivateMaterial(String pem) throws Exception {
        if (pem.isBlank()) throw new IllegalArgumentException("Private key is required");
        if (pem.contains("ED25519")) return AsymmetricKeyOperations.importEd25519PrivateKeyPEM(pem);
        if (pem.contains("EC PRIVATE")) return AsymmetricKeyOperations.importECPrivateKeyPEM(pem);
        return AsymmetricKeyOperations.importPrivateKeyPEMAuto(pem);
    }

    static List<String> commaSeparatedValues(String value) {
        if (value == null || value.isBlank()) return new ArrayList<>();
        return Arrays.stream(value.split(",")).map(String::trim).filter(part -> !part.isEmpty()).toList();
    }
}
