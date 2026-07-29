package com.cryptocarver.service;

import com.cryptocarver.model.MaterialDetectionResult;
import com.cryptocarver.model.MaterialDetectionResult.MaterialType;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECKey;
import java.security.interfaces.RSAKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Locale;

public class CryptoMaterialDetector {

    public static MaterialDetectionResult detect(String rawInput) {
        if (rawInput == null || rawInput.trim().isEmpty()) {
            return MaterialDetectionResult.empty();
        }

        String input = rawInput.trim();

        // 1. Check OpenPGP Armored
        if (input.contains("-----BEGIN PGP ")) {
            if (input.contains("-----BEGIN PGP PUBLIC KEY BLOCK-----")) {
                return new MaterialDetectionResult(MaterialType.OPENPGP_PUBLIC_KEY, "OpenPGP", null,
                        input.getBytes(StandardCharsets.UTF_8).length, false,
                        "Detected: OpenPGP Public Key (ASCII Armor)", null, true);
            }
            if (input.contains("-----BEGIN PGP PRIVATE KEY BLOCK-----") || input.contains("-----BEGIN PGP SECRET KEY BLOCK-----")) {
                return new MaterialDetectionResult(MaterialType.OPENPGP_PRIVATE_KEY, "OpenPGP", null,
                        input.getBytes(StandardCharsets.UTF_8).length, true,
                        "Detected: OpenPGP Secret Key (ASCII Armor)", null, true);
            }
            if (input.contains("-----BEGIN PGP SIGNATURE-----")) {
                return new MaterialDetectionResult(MaterialType.OPENPGP_SIGNATURE, "OpenPGP", null,
                        input.getBytes(StandardCharsets.UTF_8).length, false,
                        "Detected: OpenPGP Detached Signature", null, true);
            }
            if (input.contains("-----BEGIN PGP MESSAGE-----")) {
                return new MaterialDetectionResult(MaterialType.OPENPGP_MESSAGE, "OpenPGP", null,
                        input.getBytes(StandardCharsets.UTF_8).length, false,
                        "Detected: OpenPGP Encrypted Message / Armor", null, true);
            }
        }

        // 2. Check Standard PEM Blocks
        if (input.startsWith("-----BEGIN ") && input.contains("-----END ")) {
            if (input.contains("CERTIFICATE-----") && !input.contains("CERTIFICATE REQUEST")) {
                return detectPemCertificate(input);
            }
            if (input.contains("CERTIFICATE REQUEST")) {
                return new MaterialDetectionResult(MaterialType.PEM_CSR, "X.509 CSR", null,
                        input.getBytes(StandardCharsets.UTF_8).length, false,
                        "Detected: PKCS#10 Certificate Request (PEM)", null, true);
            }
            if (input.contains("CRL-----")) {
                return new MaterialDetectionResult(MaterialType.PEM_CRL, "X.509 CRL", null,
                        input.getBytes(StandardCharsets.UTF_8).length, false,
                        "Detected: X.509 Revocation List CRL (PEM)", null, true);
            }
            if (input.contains("PRIVATE KEY-----")) {
                return detectPemPrivateKey(input);
            }
            if (input.contains("PUBLIC KEY-----")) {
                return detectPemPublicKey(input);
            }
        }

        // 3. Check JSON / JWK / JWKS
        if ((input.startsWith("{") && input.endsWith("}")) || (input.startsWith("[") && input.endsWith("]"))) {
            try {
                JsonElement parsed = JsonParser.parseString(input);
                if (parsed.isJsonObject()) {
                    JsonObject json = parsed.getAsJsonObject();
                    if (json.has("kty")) {
                        String kty = json.get("kty").getAsString();
                        return new MaterialDetectionResult(MaterialType.JWK, kty.toUpperCase(Locale.ROOT), null,
                                input.getBytes(StandardCharsets.UTF_8).length, json.has("d") || json.has("k"),
                                "Detected: JWK (" + kty + ")", null, true);
                    }
                    if (json.has("keys")) {
                        return new MaterialDetectionResult(MaterialType.JWK, "JWKS", null,
                                input.getBytes(StandardCharsets.UTF_8).length, false,
                                "Detected: JWK Set (JWKS)", null, true);
                    }
                }
                return new MaterialDetectionResult(MaterialType.JSON, "JSON", null,
                        input.getBytes(StandardCharsets.UTF_8).length, false,
                        "Detected: JSON Document", null, true);
            } catch (Exception ignored) {
                // Not valid JSON object
            }
        }

        // 4. Check JWT / JWS / JWE (Dot-separated Compact Serialization)
        if (!input.contains(" ") && input.matches("^[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+(\\.[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+)?$")) {
            String[] parts = input.split("\\.");
            if (parts.length == 3) {
                return new MaterialDetectionResult(MaterialType.JWT, "JWS", null,
                        input.getBytes(StandardCharsets.UTF_8).length, false,
                        "Detected: JWT / JWS Compact Token", null, true);
            } else if (parts.length == 5) {
                return new MaterialDetectionResult(MaterialType.JWT, "JWE", null,
                        input.getBytes(StandardCharsets.UTF_8).length, false,
                        "Detected: JWE Encrypted Token", null, true);
            }
        }

        // 5. Check Hexadecimal
        String cleanHex = input.replaceAll("[\\s:]+", "");
        if (cleanHex.length() > 0 && cleanHex.length() % 2 == 0 && cleanHex.matches("^[0-9A-Fa-f]+$")) {
            int byteLen = cleanHex.length() / 2;
            int bitLen = byteLen * 8;
            return new MaterialDetectionResult(MaterialType.HEX, "Hex", bitLen, byteLen, false,
                    "Detected: Hex · " + byteLen + " bytes (" + bitLen + " bits)", null, true);
        }

        // 6. Check Base64
        String cleanB64 = input.replaceAll("[\r\n]+", "");
        if (!cleanB64.contains(" ") && cleanB64.length() > 0 && cleanB64.length() % 4 == 0 && cleanB64.matches("^[A-Za-z0-9+/]+={0,2}$")) {
            int byteLen = (int) Math.ceil(cleanB64.replaceAll("=", "").length() * 3.0 / 4.0);
            return new MaterialDetectionResult(MaterialType.BASE64, "Base64", byteLen * 8, byteLen, false,
                    "Detected: Base64 · ~" + byteLen + " bytes", null, true);
        }

        // 7. Fallback Raw / Unknown Text
        return new MaterialDetectionResult(MaterialType.TEXT_UNKNOWN, "Text", null,
                input.getBytes(StandardCharsets.UTF_8).length, false,
                "Detected: Plain Text (" + input.length() + " chars)", null, true);
    }

    private static MaterialDetectionResult detectPemCertificate(String pem) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(
                    new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
            String keyAlgo = cert.getPublicKey().getAlgorithm();
            int bitLen = 0;
            if (cert.getPublicKey() instanceof RSAKey) {
                bitLen = ((RSAKey) cert.getPublicKey()).getModulus().bitLength();
            } else if (cert.getPublicKey() instanceof ECKey) {
                bitLen = ((ECKey) cert.getPublicKey()).getParams().getCurve().getField().getFieldSize();
            }
            String detail = bitLen > 0 ? keyAlgo + " " + bitLen + " bits" : keyAlgo;
            return new MaterialDetectionResult(MaterialType.PEM_CERTIFICATE, keyAlgo, bitLen > 0 ? bitLen : null,
                    pem.getBytes(StandardCharsets.UTF_8).length, false,
                    "Detected: X.509 Certificate · " + detail, null, true);
        } catch (Exception e) {
            return new MaterialDetectionResult(MaterialType.PEM_CERTIFICATE, "X.509", null,
                    pem.getBytes(StandardCharsets.UTF_8).length, false,
                    "Detected: X.509 Certificate (PEM)", null, true);
        }
    }

    private static MaterialDetectionResult detectPemPublicKey(String pem) {
        String cleanB64 = pem.replaceAll("-----(BEGIN|END) [A-Z0-9 ]+-----", "").replaceAll("[\\s\\r\\n]+", "");
        try {
            byte[] encoded = Base64.getDecoder().decode(cleanB64);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
            for (String algo : new String[]{"RSA", "EC", "Ed25519", "DSA"}) {
                try {
                    KeyFactory kf = KeyFactory.getInstance(algo);
                    java.security.PublicKey pub = kf.generatePublic(keySpec);
                    int bitLen = 0;
                    if (pub instanceof RSAKey) bitLen = ((RSAKey) pub).getModulus().bitLength();
                    else if (pub instanceof ECKey) bitLen = ((ECKey) pub).getParams().getCurve().getField().getFieldSize();
                    String info = bitLen > 0 ? algo + " · " + bitLen + " bits" : algo;
                    return new MaterialDetectionResult(MaterialType.PEM_PUBLIC_KEY, algo, bitLen > 0 ? bitLen : null,
                            encoded.length, false, "Detected: " + info + " Public Key PEM", null, true);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        if (pem.contains("ML-DSA") || pem.contains("SLH-DSA") || pem.contains("FALCON") || pem.contains("KYBER") || pem.contains("ML-KEM")) {
            return new MaterialDetectionResult(MaterialType.PEM_PUBLIC_KEY, "PQC", null,
                    pem.getBytes(StandardCharsets.UTF_8).length, false,
                    "Detected: PQC Public Key PEM", null, true);
        }

        return new MaterialDetectionResult(MaterialType.PEM_PUBLIC_KEY, "Asymmetric", null,
                pem.getBytes(StandardCharsets.UTF_8).length, false,
                "Detected: Public Key PEM", null, true);
    }

    private static MaterialDetectionResult detectPemPrivateKey(String pem) {
        String algo = "Asymmetric";
        int bitLen = 0;
        String cleanB64 = pem.replaceAll("-----(BEGIN|END) [A-Z0-9 ]+-----", "").replaceAll("[\\s\\r\\n]+", "");
        try {
            byte[] encoded = Base64.getDecoder().decode(cleanB64);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
            for (String candidateAlgo : new String[]{"RSA", "EC", "Ed25519", "DSA"}) {
                try {
                    KeyFactory kf = KeyFactory.getInstance(candidateAlgo);
                    java.security.PrivateKey priv = kf.generatePrivate(keySpec);
                    algo = candidateAlgo;
                    if (priv instanceof RSAKey) bitLen = ((RSAKey) priv).getModulus().bitLength();
                    else if (priv instanceof ECKey) bitLen = ((ECKey) priv).getParams().getCurve().getField().getFieldSize();
                    break;
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        if (pem.contains("RSA PRIVATE KEY")) algo = "RSA";
        else if (pem.contains("EC PRIVATE KEY")) algo = "EC";
        else if (pem.contains("ENCRYPTED PRIVATE KEY")) algo = "Encrypted PKCS#8";

        if (pem.contains("ML-DSA") || pem.contains("SLH-DSA") || pem.contains("FALCON") || pem.contains("KYBER") || pem.contains("ML-KEM")) {
            algo = "PQC";
        }

        String info = bitLen > 0 ? algo + " · " + bitLen + " bits" : algo;
        return new MaterialDetectionResult(MaterialType.PEM_PRIVATE_KEY, algo, bitLen > 0 ? bitLen : null,
                pem.getBytes(StandardCharsets.UTF_8).length, true,
                "Detected: " + info + " Private Key PEM", null, true);
    }
}
