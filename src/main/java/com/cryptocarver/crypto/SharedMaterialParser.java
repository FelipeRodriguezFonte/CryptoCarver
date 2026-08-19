package com.cryptocarver.crypto;

import com.cryptocarver.util.DataConverter;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Base64;

/**
 * Shared cryptographic material parser used directly by controllers and OperationPreflightEngine,
 * and indirectly via MaterialFieldBadge for material format validation.
 */
public final class SharedMaterialParser {

    private SharedMaterialParser() {}

    public static X509Certificate parseCertificate(String certText) throws Exception {
        return CertificateGenerator.parseCertificate(certText);
    }

    public static PrivateKey parsePrivateKeyPem(String pemText) throws Exception {
        if (pemText == null || pemText.trim().isEmpty()) {
            throw new IllegalArgumentException("Private key PEM text is empty");
        }
        try (PEMParser pemParser = new PEMParser(new StringReader(pemText.trim()))) {
            Object object = pemParser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
            if (object instanceof PrivateKeyInfo pki) {
                return converter.getPrivateKey(pki);
            } else if (object instanceof org.bouncycastle.openssl.PEMKeyPair keyPair) {
                return converter.getKeyPair(keyPair).getPrivate();
            } else {
                throw new IllegalArgumentException("Invalid Private Key PEM structure");
            }
        }
    }

    public static PublicKey parsePublicKeyPem(String pemText) throws Exception {
        if (pemText == null || pemText.trim().isEmpty()) {
            throw new IllegalArgumentException("Public key PEM text is empty");
        }
        try (PEMParser pemParser = new PEMParser(new StringReader(pemText.trim()))) {
            Object object = pemParser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
            if (object instanceof SubjectPublicKeyInfo spki) {
                return converter.getPublicKey(spki);
            } else {
                throw new IllegalArgumentException("Invalid Public Key PEM structure");
            }
        }
    }

    public static byte[] parseBytesByFormat(String text, String format) throws Exception {
        if (text == null || text.trim().isEmpty()) {
            return new byte[0];
        }
        String trimmed = text.trim();
        String fmt = format != null ? format.toUpperCase() : "UTF-8";

        if (fmt.contains("HEX") && fmt.contains("BASE64")) {
            String clean = trimmed.replaceAll("\\s+", "");
            if (clean.matches("^[0-9a-fA-F]*$") && clean.length() % 2 == 0) {
                return DataConverter.hexToBytes(clean);
            }
            return Base64.getDecoder().decode(clean);
        } else if (fmt.contains("HEX") && fmt.contains("ASCII")) {
            String hexClean = trimmed.replaceAll("\\s+", "");
            if (hexClean.matches("^[0-9a-fA-F]*$") && hexClean.length() % 2 == 0) {
                return DataConverter.hexToBytes(hexClean);
            }
            return trimmed.getBytes(StandardCharsets.US_ASCII);
        } else if (fmt.contains("HEX")) {
            String hexClean = trimmed.replaceAll("\\s+", "");
            if (!hexClean.matches("^[0-9a-fA-F]*$")) {
                throw new IllegalArgumentException("Invalid hex characters");
            }
            if (hexClean.length() % 2 != 0) {
                throw new IllegalArgumentException("Odd hex string length (" + hexClean.length() + ")");
            }
            return DataConverter.hexToBytes(hexClean);
        } else if (fmt.contains("BASE64")) {
            String b64Clean = trimmed.replaceAll("\\s+", "");
            return Base64.getDecoder().decode(b64Clean);
        } else {
            return trimmed.getBytes(StandardCharsets.UTF_8);
        }
    }
}
