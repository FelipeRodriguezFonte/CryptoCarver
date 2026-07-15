package com.cryptoforge.ui;

import com.cryptoforge.util.DataConverter;
import com.cryptoforge.model.OperationResult;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.*;
import com.nimbusds.jose.jca.JCAContext;
import com.nimbusds.jwt.*;
import com.nimbusds.jose.util.Base64URL;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.text.TextFlow;
import javafx.scene.text.Text;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.UUID;
import java.util.Set;
import java.util.Collections;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.security.spec.MGF1ParameterSpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;

public class JOSEController {

    private static final Logger LOG = LoggerFactory.getLogger(JOSEController.class);

    private final StatusReporter statusReporter;

    public void generateDetachedJWS(String payload, String algorithm, String key, TextArea output) {
        try {
            JWSAlgorithm jwsAlgorithm = JWSAlgorithm.parse(algorithm);
            JWSObject object = new JWSObject(new JWSHeader(jwsAlgorithm), new Payload(payload));
            if (JWSAlgorithm.Family.HMAC_SHA.contains(jwsAlgorithm)) object.sign(new PromiscuousMACSigner(key, jwsAlgorithm));
            else if (JWSAlgorithm.Family.RSA.contains(jwsAlgorithm)) object.sign(new RSASSASigner(parseRSAPrivateKey(key)));
            else if (JWSAlgorithm.Family.EC.contains(jwsAlgorithm)) object.sign(new ECDSASigner(requireEcPrivateKey(jwsAlgorithm, key)));
            else throw new IllegalArgumentException("Unsupported detached JWS algorithm: " + algorithm);
            String serialized = object.serialize(true);
            output.setText(serialized);
            statusReporter.publish(OperationResult.forOperation("Detached JWS Generation")
                    .input(payload.getBytes(StandardCharsets.UTF_8)).output(serialized.getBytes(StandardCharsets.US_ASCII))
                    .detail("Algorithm", jwsAlgorithm.getName()).detail(com.cryptoforge.model.OperationDetail.secretDetail("Key Material", key))
                    .status("Detached JWS generated").build());
        } catch (Exception e) { statusReporter.showError("Detached JWS", e.getMessage()); }
    }

    public void verifyDetachedJWS(String detached, String payload, String algorithm, String key, Label status) {
        try {
            JWSObject object = JWSObject.parse(detached, new Payload(payload));
            JWSAlgorithm actual = object.getHeader().getAlgorithm();
            if (!actual.equals(JWSAlgorithm.parse(algorithm))) throw new IllegalArgumentException("Header algorithm does not match selection");
            boolean valid;
            if (JWSAlgorithm.Family.HMAC_SHA.contains(actual)) valid = object.verify(new PromiscuousMACVerifier(key, actual));
            else if (JWSAlgorithm.Family.RSA.contains(actual)) valid = object.verify(new RSASSAVerifier((RSAPublicKey) parseRSAPublicKey(key)));
            else if (JWSAlgorithm.Family.EC.contains(actual)) valid = object.verify(new ECDSAVerifier(requireEcPublicKey(actual, key)));
            else throw new IllegalArgumentException("Unsupported detached JWS algorithm: " + actual);
            status.setText(valid ? "VALID DETACHED SIGNATURE" : "INVALID DETACHED SIGNATURE");
            status.setStyle(valid ? "-fx-text-fill: green;" : "-fx-text-fill: red;");
            statusReporter.publish(OperationResult.forOperation("Detached JWS Verification")
                    .input(detached.getBytes(StandardCharsets.US_ASCII)).detail("Algorithm", actual.getName())
                    .detail("Result", valid ? "VALID" : "INVALID").detail(com.cryptoforge.model.OperationDetail.secretDetail("Key Material", key))
                    .status("Detached JWS verification: " + (valid ? "valid" : "invalid")).build());
        } catch (Exception e) { status.setText("ERROR: " + e.getMessage()); status.setStyle("-fx-text-fill: red;"); }
    }

    public JOSEController(StatusReporter statusReporter) {
        this.statusReporter = statusReporter;
    }

    // --- JWT (Signed) ---
    public void generateSignedJWT(String payloadJson, String algorithm, String secretOrKey, TextArea outputArea) {
        try {
            // 1. Parse Payload
            JWTClaimsSet claimsSet = JWTClaimsSet.parse(payloadJson);

            // 2. Determine Algorithm
            JWSAlgorithm jwsAlgo = JWSAlgorithm.parse(algorithm);
            JWSHeader header = new JWSHeader.Builder(jwsAlgo).type(JOSEObjectType.JWT).build();
            JWSSigner signer;

            // 3. Create Signer based on Algo Family
            if (JWSAlgorithm.Family.HMAC_SHA.contains(jwsAlgo)) {
                if (secretOrKey.trim().startsWith("-----BEGIN")) {
                    throw new IllegalArgumentException(
                            "Detected PEM Key for HMAC Algorithm. \nHMAC uses a shared secret, not a Private Key. \nPlease select an RS* or ES* algorithm for RSA/EC keys.");
                }

                signer = new PromiscuousMACSigner(secretOrKey, jwsAlgo);

            } else if (JWSAlgorithm.Family.RSA.contains(jwsAlgo)) {
                PrivateKey privateKey = parseRSAPrivateKey(secretOrKey);
                signer = new RSASSASigner(privateKey);
            } else if (JWSAlgorithm.Family.EC.contains(jwsAlgo)) {
                signer = new ECDSASigner(requireEcPrivateKey(jwsAlgo, secretOrKey));
            } else {
                throw new IllegalArgumentException("Unsupported algorithm family: " + algorithm);
            }

            // 4. Sign
            SignedJWT signedJWT = new SignedJWT(header, claimsSet);
            signedJWT.sign(signer);

            String serialized = signedJWT.serialize();
            outputArea.setText(serialized);
            statusReporter.publish(OperationResult.forOperation("Signed JWT Generation")
                    .input(payloadJson.getBytes(StandardCharsets.UTF_8))
                    .output(serialized.getBytes(StandardCharsets.US_ASCII))
                    .detail("Algorithm", algorithm).detail(com.cryptoforge.model.OperationDetail.secretDetail("Key Material", secretOrKey))
                    .status("Signed JWT generated successfully (" + algorithm + ")").build());

        } catch (Exception e) {
            statusReporter.showError("JWT Generation Error", e.getMessage());
            LOG.error("Signed JWT generation failed", e);
        }
    }

    public void validateJWT(String tokenString, String keyString, TextArea headerOut, TextArea payloadOut,
            Label statusLabel) {
        try {
            // 1. Parse JWT
            SignedJWT signedJWT = SignedJWT.parse(tokenString);

            // 2. Display Parts
            headerOut.setText(signedJWT.getHeader().toString());
            payloadOut.setText(signedJWT.getJWTClaimsSet().toString());

            // 3. Verify
            JWSVerifier verifier;
            JWSAlgorithm algo = signedJWT.getHeader().getAlgorithm();

            if (JWSAlgorithm.Family.HMAC_SHA.contains(algo)) {
                verifier = new PromiscuousMACVerifier(keyString, algo);
            } else if (JWSAlgorithm.Family.RSA.contains(algo)) {
                PublicKey pubKey = parseRSAPublicKey(keyString);
                verifier = new RSASSAVerifier((RSAPublicKey) pubKey);
            } else if (JWSAlgorithm.Family.EC.contains(algo)) {
                verifier = new ECDSAVerifier(requireEcPublicKey(algo, keyString));
            } else {
                statusLabel.setText("Unsupported Algo for Verification");
                statusLabel.setStyle("-fx-text-fill: orange;");
                return;
            }

            boolean verified = signedJWT.verify(verifier);
            if (verified) {
                statusLabel.setText("VALID SIGNATURE");
                statusLabel.setStyle("-fx-text-fill: green;");
            } else {
                statusLabel.setText("INVALID SIGNATURE");
                statusLabel.setStyle("-fx-text-fill: red;");
            }
            statusReporter.publish(OperationResult.forOperation("JWT Validation")
                    .input(tokenString.getBytes(StandardCharsets.US_ASCII))
                    .detail("Algorithm", algo.getName()).detail("Result", verified ? "VALID" : "INVALID")
                    .detail(com.cryptoforge.model.OperationDetail.secretDetail("Key Material", keyString))
                    .status("JWT validation: " + (verified ? "valid" : "invalid")).build());

        } catch (Exception e) {
            statusLabel.setText("ERROR: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: red;");
            headerOut.setText("");
            payloadOut.setText("");
        }
    }

    // --- Nested JWT (Sign then Encrypt) ---
    public void generateNestedJWT(String payloadJson, String signAlgoStr, String signKey,
            String keyAlgoStr, String contentAlgoStr, String encKeyPEM, boolean compress,
            TextArea outputArea) {
        try {
            // 1. Prepare Inner Signed JWT
            JWTClaimsSet claimsSet = JWTClaimsSet.parse(payloadJson);
            JWSAlgorithm signAlgo = JWSAlgorithm.parse(signAlgoStr);

            // "cty": "JWT" is recommended for nested tokens
            JWSHeader signHeader = new JWSHeader.Builder(signAlgo)
                    .type(JOSEObjectType.JWT)
                    .contentType("JWT")
                    .build();

            JWSSigner signer;
            if (JWSAlgorithm.Family.HMAC_SHA.contains(signAlgo)) {
                signer = new PromiscuousMACSigner(signKey, signAlgo);
            } else if (JWSAlgorithm.Family.RSA.contains(signAlgo)) {
                PrivateKey privateKey = parseRSAPrivateKey(signKey);
                signer = new RSASSASigner(privateKey);
            } else if (JWSAlgorithm.Family.EC.contains(signAlgo)) {
                signer = new ECDSASigner(requireEcPrivateKey(signAlgo, signKey));
            } else {
                throw new IllegalArgumentException("Unsupported signing algo: " + signAlgoStr);
            }

            SignedJWT signedJWT = new SignedJWT(signHeader, claimsSet);
            signedJWT.sign(signer);

            // 2. Encrypt the Signed JWT (Outer JWE)
            JWEAlgorithm keyAlgo = JWEAlgorithm.parse(keyAlgoStr);
            EncryptionMethod contentAlgo = EncryptionMethod.parse(contentAlgoStr);

            JWEHeader.Builder headerBuilder = new JWEHeader.Builder(keyAlgo, contentAlgo)
                    .contentType("JWT"); // Outer content type

            if (compress) {
                headerBuilder.compressionAlgorithm(CompressionAlgorithm.DEF);
            }

            JWEHeader jweHeader = headerBuilder.build();

            JWEObject jweObject = new JWEObject(jweHeader, new Payload(signedJWT));

            PublicKey pubKey = parseRSAPublicKey(encKeyPEM);
            jweObject.encrypt(new RSAEncrypter((RSAPublicKey) pubKey));

            // 3. Output
            String serialized = jweObject.serialize();
            outputArea.setText(serialized);
            String status = "Nested JWT Generated (Signed: " + signAlgoStr + ", Encrypted: " + keyAlgoStr + ")";
            if (compress)
                status += " [Compressed]";
            statusReporter.publish(OperationResult.forOperation("Nested JWT Generation")
                    .input(payloadJson.getBytes(StandardCharsets.UTF_8))
                    .output(serialized.getBytes(StandardCharsets.US_ASCII))
                    .detail("Signature Algorithm", signAlgoStr).detail("Key Algorithm", keyAlgoStr)
                    .detail("Compression", String.valueOf(compress)).detail(com.cryptoforge.model.OperationDetail.secretDetail("Key Material", signKey + " / " + encKeyPEM))
                    .status(status).build());

        } catch (Exception e) {
            statusReporter.showError("Nested JWT Error", e.getMessage());
            LOG.error("Nested JWT generation failed", e);
        }
    }

    // --- JWE (Encrypted) ---
    public void generateJWE(String payload, String keyAlgo, String contentAlgo, String publicKeyPEM, boolean compress,
            TextArea outputArea) {
        try {
            // 1. Algorithms
            JWEAlgorithm alg = JWEAlgorithm.parse(keyAlgo);
            EncryptionMethod enc = EncryptionMethod.parse(contentAlgo);

            // 2. Key
            PublicKey publicKey = parseRSAPublicKey(publicKeyPEM);

            // 3. Header
            JWEHeader.Builder headerBuilder = new JWEHeader.Builder(alg, enc);
            if (compress) {
                headerBuilder.compressionAlgorithm(CompressionAlgorithm.DEF);
            }
            JWEHeader header = headerBuilder.build();

            // 4. Object
            JWEObject jweObject = new JWEObject(header, new Payload(payload));

            // 5. Encrypt
            jweObject.encrypt(new RSAEncrypter((RSAPublicKey) publicKey));

            // 6. Output
            String serialized = jweObject.serialize();
            outputArea.setText(serialized);
            String status = "JWE Encrypted (" + keyAlgo + " / " + contentAlgo + ")";
            if (compress)
                status += " [Compressed]";
            statusReporter.publish(OperationResult.forOperation("JWE Encryption")
                    .input(payload.getBytes(StandardCharsets.UTF_8))
                    .output(serialized.getBytes(StandardCharsets.US_ASCII))
                    .detail("Key Algorithm", keyAlgo).detail("Content Algorithm", contentAlgo)
                    .detail("Compression", String.valueOf(compress)).detail(com.cryptoforge.model.OperationDetail.secretDetail("Key Material", publicKeyPEM))
                    .status(status).build());

        } catch (Exception e) {
            statusReporter.showError("JWE Encryption Error", e.getMessage());
            LOG.error("JWE encryption failed", e);
        }
    }

    public void decryptJWE(String jweString, String privateKeyPEM,
            TextArea headerOut, TextArea payloadOut,
            TextArea jweHeaderArea, TextArea jweEncryptedKeyArea, TextArea jweDecryptedKeyArea,
            TextArea jweIVArea, TextArea jweCiphertextArea, TextArea jweAuthTagArea,
            Label statusLabel) {
        try {
            // 1. Parse
            JWEObject jweObject = JWEObject.parse(jweString);

            // 2. Key
            PrivateKey privateKey = parseRSAPrivateKey(privateKeyPEM);

            // 3. Decrypt
            jweObject.decrypt(new RSADecrypter(privateKey));

            // 4. Display Parts
            headerOut.setText(jweObject.getHeader().toString());
            payloadOut.setText(jweObject.getPayload().toString());

            // 5. Visual Breakdown
            jweHeaderArea.setText(jweObject.getHeader().toString());

            Base64URL encryptedKey = jweObject.getEncryptedKey();
            jweEncryptedKeyArea.setText(encryptedKey != null ? encryptedKey.toString() : "");

            if (encryptedKey != null) {
                try {
                    // Manual Decryption to show the CEK
                    JWEAlgorithm alg = jweObject.getHeader().getAlgorithm();

                    javax.crypto.Cipher cipher;
                    if (JWEAlgorithm.RSA_OAEP_256.equals(alg)) {
                        cipher = javax.crypto.Cipher.getInstance("RSA/ECB/OAEPPadding");
                        OAEPParameterSpec spec = new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256,
                                PSource.PSpecified.DEFAULT);
                        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, privateKey, spec);
                    } else if (JWEAlgorithm.RSA_OAEP.equals(alg)) {
                        cipher = javax.crypto.Cipher.getInstance("RSA/ECB/OAEPPadding");
                        OAEPParameterSpec spec = new OAEPParameterSpec("SHA-1", "MGF1", MGF1ParameterSpec.SHA1,
                                PSource.PSpecified.DEFAULT);
                        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, privateKey, spec);
                    } else if (JWEAlgorithm.RSA1_5.equals(alg)) {
                        cipher = javax.crypto.Cipher.getInstance("RSA/ECB/PKCS1Padding");
                        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, privateKey);
                    } else {
                        // Fallback attempt
                        cipher = javax.crypto.Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
                        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, privateKey);
                    }

                    byte[] decryptedKeyBytes = cipher.doFinal(encryptedKey.decode());

                    StringBuilder hexString = new StringBuilder();
                    for (byte b : decryptedKeyBytes) {
                        String hex = Integer.toHexString(0xff & b);
                        if (hex.length() == 1)
                            hexString.append('0');
                        hexString.append(hex);
                    }
                    jweDecryptedKeyArea.setText(hexString.toString().toUpperCase());
                } catch (Exception ex) {
                    jweDecryptedKeyArea.setText("Decryption Error: " + ex.getMessage());
                }
            } else {
                jweDecryptedKeyArea.setText("Direct Encryption (No Key)");
            }

            jweIVArea.setText(jweObject.getIV() != null
                    ? jweObject.getIV().toString() + " \n[Hex: "
                            + com.cryptoforge.util.DataConverter.bytesToHex(jweObject.getIV().decode()) + "]"
                    : "");
            jweCiphertextArea.setText(jweObject.getCipherText() != null ? jweObject.getCipherText().toString() : "");
            jweAuthTagArea
                    .setText(
                            jweObject.getAuthTag() != null
                                    ? jweObject.getAuthTag().toString() + " \n[Hex: "
                                            + com.cryptoforge.util.DataConverter
                                                    .bytesToHex(jweObject.getAuthTag().decode())
                                            + "]"
                                    : "");

            statusLabel.setText("DECRYPTION SUCCESSFUL");
            statusLabel.setStyle("-fx-text-fill: green;");

            String payload = jweObject.getPayload().toString();
            statusReporter.publish(OperationResult.forOperation("JWE Decryption")
                    .input(jweString.getBytes(StandardCharsets.US_ASCII))
                    .output(payload.getBytes(StandardCharsets.UTF_8))
                    .detail("Key Algorithm", jweObject.getHeader().getAlgorithm().getName())
                    .detail("Content Algorithm", jweObject.getHeader().getEncryptionMethod().getName())
                    .detail(com.cryptoforge.model.OperationDetail.secretDetail("Key Material", privateKeyPEM))
                    .status("JWE decrypted").build());

        } catch (Exception e) {
            statusLabel.setText("DECRYPTION FAILED");
            statusLabel.setStyle("-fx-text-fill: red;");
            statusReporter.showError("JWE Decryption Error", e.getMessage());
            LOG.error("JWE decryption failed", e);
        }
    }

    // --- JWK ---
    public void generateRSAJWK(TextArea outputArea) {
        try {
            RSAKey rsaJWK = new RSAKeyGenerator(2048)
                    .keyID(UUID.randomUUID().toString())
                    .generate();

            outputArea.setText(rsaJWK.toJSONString());
            statusReporter.updateStatus("RSA JWK generated");
        } catch (Exception e) {
            statusReporter.showError("JWK Error", e.getMessage());
        }
    }

    // --- Helpers ---
    private PrivateKey parseRSAPrivateKey(String pem) throws Exception {
        String privateKeyPEM = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] encoded = DataConverter.decodeBase64Flexible(privateKeyPEM);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(encoded));
    }

    /** Loads an EC private key encoded as PEM PKCS#8 for ES256/ES384/ES512. */
    private java.security.interfaces.ECPrivateKey parseECPrivateKey(String pem) throws Exception {
        if (pem.contains("-----BEGIN EC PRIVATE KEY-----")) {
            throw new IllegalArgumentException("EC private keys must be PEM PKCS#8 (BEGIN PRIVATE KEY), not SEC1 (BEGIN EC PRIVATE KEY)");
        }
        String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] encoded = DataConverter.decodeBase64Flexible(base64);
        PrivateKey key = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(encoded));
        if (!(key instanceof java.security.interfaces.ECPrivateKey ecKey)) {
            throw new IllegalArgumentException("The supplied PKCS#8 key is not an EC private key");
        }
        return ecKey;
    }

    private PublicKey parseRSAPublicKey(String pem) throws Exception {
        String publicKeyPEM = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("-----BEGIN RSA PUBLIC KEY-----", "")
                .replace("-----END RSA PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        byte[] encoded = DataConverter.decodeBase64Flexible(publicKeyPEM);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(new X509EncodedKeySpec(encoded));
    }

    /** Loads an EC public key encoded as PEM SubjectPublicKeyInfo for ES verification. */
    private java.security.interfaces.ECPublicKey parseECPublicKey(String pem) throws Exception {
        String base64 = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "").replaceAll("\\s", "");
        PublicKey key = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(DataConverter.decodeBase64Flexible(base64)));
        if (!(key instanceof java.security.interfaces.ECPublicKey ecKey)) throw new IllegalArgumentException("The supplied key is not an EC public key");
        return ecKey;
    }

    private java.security.interfaces.ECPrivateKey requireEcPrivateKey(JWSAlgorithm algorithm, String pem) throws Exception {
        java.security.interfaces.ECPrivateKey key = parseECPrivateKey(pem);
        validateEcCurve(algorithm, key.getParams().getCurve().getField().getFieldSize());
        return key;
    }

    private java.security.interfaces.ECPublicKey requireEcPublicKey(JWSAlgorithm algorithm, String pem) throws Exception {
        java.security.interfaces.ECPublicKey key = parseECPublicKey(pem);
        validateEcCurve(algorithm, key.getParams().getCurve().getField().getFieldSize());
        return key;
    }

    private void validateEcCurve(JWSAlgorithm algorithm, int fieldSize) {
        int expected = JWSAlgorithm.ES256.equals(algorithm) ? 256 : JWSAlgorithm.ES384.equals(algorithm) ? 384 : JWSAlgorithm.ES512.equals(algorithm) ? 521 : -1;
        if (expected < 0) throw new IllegalArgumentException("Unsupported EC JWS algorithm: " + algorithm);
        if (fieldSize != expected) throw new IllegalArgumentException(algorithm + " requires a P-" + expected + " EC key; supplied key has a " + fieldSize + "-bit field");
    }

    // --- Internal Permissive Implementations ---

    private static class PromiscuousMACSigner implements JWSSigner {
        private final byte[] secret;
        private final JWSAlgorithm algorithm;
        private final JCAContext jcaContext = new JCAContext();

        public PromiscuousMACSigner(String secretStr, JWSAlgorithm algorithm) {
            this.secret = secretStr.getBytes(StandardCharsets.UTF_8);
            this.algorithm = algorithm;
        }

        @Override
        public Base64URL sign(final JWSHeader header, final byte[] signingInput) throws JOSEException {
            try {
                String jcaAlgo = getJCAAlgorithmName(header.getAlgorithm());
                Mac mac = Mac.getInstance(jcaAlgo);
                mac.init(new SecretKeySpec(secret, jcaAlgo));
                return Base64URL.encode(mac.doFinal(signingInput));
            } catch (Exception e) {
                throw new JOSEException(e.getMessage(), e);
            }
        }

        @Override
        public Set<JWSAlgorithm> supportedJWSAlgorithms() {
            return Collections.singleton(algorithm);
        }

        @Override
        public JCAContext getJCAContext() {
            return jcaContext;
        }
    }

    private static class PromiscuousMACVerifier implements JWSVerifier {
        private final byte[] secret;
        private final JWSAlgorithm algorithm;
        private final JCAContext jcaContext = new JCAContext();

        public PromiscuousMACVerifier(String secretStr, JWSAlgorithm algorithm) {
            this.secret = secretStr.getBytes(StandardCharsets.UTF_8);
            this.algorithm = algorithm;
        }

        @Override
        public boolean verify(JWSHeader header, byte[] signedContent, Base64URL signature) throws JOSEException {
            if (!header.getAlgorithm().equals(algorithm)) {
                return false;
            }
            try {
                String jcaAlgo = getJCAAlgorithmName(header.getAlgorithm());
                Mac mac = Mac.getInstance(jcaAlgo);
                mac.init(new SecretKeySpec(secret, jcaAlgo));
                byte[] expectedSignature = mac.doFinal(signedContent);
                byte[] providedSignature = signature.decode();
                if (expectedSignature.length != providedSignature.length) {
                    return false;
                }
                int result = 0;
                for (int i = 0; i < expectedSignature.length; i++) {
                    result |= expectedSignature[i] ^ providedSignature[i];
                }
                return result == 0;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public Set<JWSAlgorithm> supportedJWSAlgorithms() {
            return Collections.singleton(algorithm);
        }

        @Override
        public JCAContext getJCAContext() {
            return jcaContext;
        }
    }

    private static String getJCAAlgorithmName(JWSAlgorithm alg) throws JOSEException {
        if (alg.equals(JWSAlgorithm.HS256))
            return "HmacSHA256";
        if (alg.equals(JWSAlgorithm.HS384))
            return "HmacSHA384";
        if (alg.equals(JWSAlgorithm.HS512))
            return "HmacSHA512";
        throw new JOSEException("Unsupported HMAC algorithm: " + alg.getName());
    }

    // --- Enterprise Features (level 4 & 5) ---

    // 1. JWK Management
    public JWK generateNewJWK(String alg, String use) throws Exception {
        if (alg.startsWith("RS") || alg.startsWith("PS")) {
            return new RSAKeyGenerator(2048)
                    .keyUse(use.equals("sig") ? KeyUse.SIGNATURE : KeyUse.ENCRYPTION)
                    .algorithm(new JWSAlgorithm(alg))
                    .keyID(UUID.randomUUID().toString())
                    .generate();
        } else if (alg.startsWith("ES")) {
            Curve curve = Curve.P_256;
            if (alg.contains("384"))
                curve = Curve.P_384;
            if (alg.contains("512"))
                curve = Curve.P_521;
            return new ECKeyGenerator(curve)
                    .keyUse(use.equals("sig") ? KeyUse.SIGNATURE : KeyUse.ENCRYPTION)
                    .algorithm(new JWSAlgorithm(alg))
                    .keyID(UUID.randomUUID().toString())
                    .generate();
        } else if (alg.startsWith("HS") || alg.startsWith("A") || alg.equals("dir")) {
            // Symmetric Key (oct)
            int bitLength = 256;
            if (alg.contains("128"))
                bitLength = 128;
            if (alg.contains("384"))
                bitLength = 384;
            if (alg.contains("512"))
                bitLength = 512;

            JWK key;
            if (alg.startsWith("HS") || alg.startsWith("A") || alg.equals("dir")) {
                LOG.debug("Generating symmetric JWK for algorithm {}", alg);
                key = new OctetSequenceKeyGenerator(bitLength)
                        .keyUse(use.equals("sig") ? KeyUse.SIGNATURE : KeyUse.ENCRYPTION)
                        .algorithm(new Algorithm(alg))
                        .keyID(UUID.randomUUID().toString())
                        .generate();
                LOG.debug("Generated symmetric JWK (key material intentionally omitted from logs)");
                return key;
            } else {
                return null;
            }
        } else {
            throw new IllegalArgumentException("Unsupported algorithm for JWK generation: " + alg);
        }
    }

    public String addToJWKSet(String currentJson, JWK newKey) throws Exception {
        JWKSet jwkSet;
        if (currentJson == null || currentJson.trim().isEmpty()) {
            jwkSet = new JWKSet(newKey);
        } else {
            try {
                jwkSet = JWKSet.parse(currentJson);
                List<JWK> keys = new ArrayList<>(jwkSet.getKeys());
                keys.add(newKey);
                jwkSet = new JWKSet(keys);
            } catch (java.text.ParseException e) {
                // If parse fails, decide whether to start fresh or throw
                if (currentJson.trim().length() > 20) {
                    throw new Exception("Failed to parse existing JWK Set: " + e.getMessage());
                }
                jwkSet = new JWKSet(newKey);
            }
        }
        // Force output of private/secret keys (false = do not exclude private keys)
        return new com.google.gson.Gson().toJson(jwkSet.toJSONObject(false));
    }

    public String exportPublicJWKS(String json) throws Exception {
        JWKSet jwkSet = JWKSet.parse(json);
        return jwkSet.toPublicJWKSet().toString();
    }

    // 2. Advanced Validation
    public void validateJWTAdvanced(String tokenString, String keyString,
            String expectedIss, String expectedAud, long clockSkewSec, boolean checkExpiry,
            TextArea headerOut, TextArea payloadOut, Label statusLabel) {
        try {
            // 1. Parse
            SignedJWT signedJWT = SignedJWT.parse(tokenString);
            headerOut.setText(signedJWT.getHeader().toString());
            payloadOut.setText(signedJWT.getJWTClaimsSet().toString());

            // 2. Verify Signature
            JWSVerifier verifier;
            JWSAlgorithm algo = signedJWT.getHeader().getAlgorithm();
            boolean sigValid = false;

            if (JWSAlgorithm.Family.HMAC_SHA.contains(algo)) {
                verifier = new PromiscuousMACVerifier(keyString, algo);
                sigValid = signedJWT.verify(verifier);
            } else if (JWSAlgorithm.Family.RSA.contains(algo)) {
                PublicKey pubKey = parseRSAPublicKey(keyString);
                verifier = new RSASSAVerifier((RSAPublicKey) pubKey);
                sigValid = signedJWT.verify(verifier);
            } else if (JWSAlgorithm.Family.EC.contains(algo)) {
                // Basic EC support
                // ... (skipping for brevity, assumes RSA/HMAC for now as per plan focus)
                statusLabel.setText("EC Verification not fully wired manually.");
                statusLabel.setStyle("-fx-text-fill: orange;");
                return;
            }

            if (!sigValid) {
                statusLabel.setText("INVALID Signature ❌");
                statusLabel.setStyle("-fx-text-fill: red;");
                return;
            }

            // 3. Validate Claims (Enterprise)
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            List<String> errors = new ArrayList<>();

            // Issuer
            if (expectedIss != null && !expectedIss.isEmpty()) {
                if (!expectedIss.equals(claims.getIssuer())) {
                    errors.add("Issuer mismatch (expected " + expectedIss + ")");
                }
            }

            // Audience
            if (expectedAud != null && !expectedAud.isEmpty()) {
                List<String> auds = claims.getAudience();
                if (auds == null || !auds.contains(expectedAud)) {
                    errors.add("Audience mismatch (expected " + expectedAud + ")");
                }
            }

            // Expiration & Not Before (w/ Clock Skew)
            if (checkExpiry) {
                Date now = new Date();
                Date exp = claims.getExpirationTime();
                Date nbf = claims.getNotBeforeTime();

                // Effective times with skew
                long skewMillis = clockSkewSec * 1000L;

                if (exp != null) {
                    if (now.getTime() > (exp.getTime() + skewMillis)) {
                        errors.add("Token Expired");
                    }
                }

                if (nbf != null) {
                    if (now.getTime() < (nbf.getTime() - skewMillis)) {
                        errors.add("Token Not Yet Valid (nbf)");
                    }
                }
            }

            if (errors.isEmpty()) {
                statusLabel.setText("VALID ✅ (Sig + Claims)");
                statusLabel.setStyle("-fx-text-fill: green;");
            } else {
                statusLabel.setText("INVALID Claims ⚠️");
                statusLabel.setStyle("-fx-text-fill: orange;");
                // Append errors to payload output for visibility
                payloadOut.appendText("\n\n--- VALIDATION ERRORS ---\n" + String.join("\n", errors));
            }

        } catch (Exception e) {
            statusLabel.setText("Error: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: red;");
            LOG.error("JWK generation failed", e);
        }
    }

    // --- Token Inspector (New Layer 6) ---
    // --- Token Inspector (New Layer 6) ---
    public void inspectToken(String token, TextFlow outputFlow) {
        outputFlow.getChildren().clear();
        inspectTokenRecursive(token, outputFlow, 0);
    }

    private void inspectTokenRecursive(String token, TextFlow outputFlow, int depth) {
        if (token == null || token.trim().isEmpty())
            return;

        String indent = "  ".repeat(depth);
        String prefix = depth > 0 ? indent + "↳ " : "";
        String[] parts = token.trim().split("\\.");

        try {
            if (parts.length == 3) {
                // JWS
                addText(outputFlow, prefix + "[JWS Detected]\n", Color.LIGHTGREEN, true);
                addSection(outputFlow, "HEADER", parts[0], Color.RED, true, depth);
                String payload = addSection(outputFlow, "PAYLOAD", parts[1], Color.MAGENTA, true, depth);
                addSection(outputFlow, "SIGNATURE", parts[2], Color.CYAN, false, depth);

                // Recursion check on Payload
                if (payload != null && (payload.startsWith("ey") || payload.startsWith("{"))) {
                    // Check if it looks like a token
                    if (payload.split("\\.").length >= 3) {
                        addText(outputFlow, "\n" + indent + "=== NESTED TOKEN IN PAYLOAD ===\n", Color.GOLD, true);
                        inspectTokenRecursive(payload, outputFlow, depth + 1);
                    }
                }

            } else if (parts.length == 5) {
                // JWE
                addText(outputFlow, prefix + "[JWE Detected]\n", Color.LIGHTBLUE, true);
                String header = addSection(outputFlow, "HEADER", parts[0], Color.RED, true, depth);
                addSection(outputFlow, "ENCRYPTED KEY", parts[1], Color.ORANGE, false, depth);
                addSection(outputFlow, "IV", parts[2], Color.GREEN, false, depth);
                addSection(outputFlow, "CIPHERTEXT", parts[3], Color.BLUE, false, depth);
                addSection(outputFlow, "TAG", parts[4], Color.YELLOW, false, depth);

                // Hint for Nested JWE
                if (header != null && (header.contains("\"cty\":\"JWT\"") || header.contains("\"cty\": \"JWT\""))) {
                    addText(outputFlow, "\n" + indent
                            + " > NOTE: Header indicates 'cty':'JWT'. This JWE contains a Nested Token (likely Signed).\n",
                            Color.GOLD, true);
                    addText(outputFlow, indent
                            + " > Decrypt this token in the 'JWE' tab, then inspect the result to see the inner token.\n",
                            Color.GOLD, false);
                }
            } else {
                addText(outputFlow, prefix + "Unknown/Raw Data: " + token + "\n\n", Color.WHITE);
            }
        } catch (Exception e) {
            addText(outputFlow, prefix + "Error: " + e.getMessage() + "\n", Color.RED);
        }
    }

    // --- JWK Logic (Capa 5) ---

    public void convertPemToJwk(String pem, String keyType, String keyId, TextArea outputArea) {
        if (pem == null || pem.trim().isEmpty()) {
            outputArea.setText("Error: Input PEM is empty.");
            return;
        }
        try {
            // Flexible PEM parsing using DataConverter logic implicitly via Nimbus or
            // manual strip
            String cleanPem = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                    .replace("-----END RSA PRIVATE KEY-----", "")
                    .replace("-----BEGIN EC PRIVATE KEY-----", "")
                    .replace("-----END EC PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");

            byte[] keyBytes = com.cryptoforge.util.DataConverter.decodeBase64Flexible(cleanPem);

            com.nimbusds.jose.jwk.JWK jwk = null;

            if ("RSA".equalsIgnoreCase(keyType)) {
                // Try parsing as Private -> Public -> Just creation
                try {
                    java.security.spec.PKCS8EncodedKeySpec spec = new java.security.spec.PKCS8EncodedKeySpec(keyBytes);
                    java.security.KeyFactory kf = java.security.KeyFactory.getInstance("RSA");
                    java.security.interfaces.RSAPrivateKey privKey = (java.security.interfaces.RSAPrivateKey) kf
                            .generatePrivate(spec);

                    // Need public key to make full JWK.
                    // Logic to extract mod/exp from private key implies using RSAPrivateCrtKey
                    if (privKey instanceof java.security.interfaces.RSAPrivateCrtKey) {
                        java.security.interfaces.RSAPrivateCrtKey crt = (java.security.interfaces.RSAPrivateCrtKey) privKey;
                        java.security.spec.RSAPublicKeySpec pubSpec = new java.security.spec.RSAPublicKeySpec(
                                crt.getModulus(), crt.getPublicExponent());
                        java.security.interfaces.RSAPublicKey pubKey = (java.security.interfaces.RSAPublicKey) kf
                                .generatePublic(pubSpec);

                        jwk = new com.nimbusds.jose.jwk.RSAKey.Builder(pubKey)
                                .privateKey(privKey)
                                .keyID(keyId != null && !keyId.isEmpty() ? keyId : null)
                                .build();
                    } else {
                        outputArea.setText("Error: Encoded RSA private key is not CRT compatible.");
                        return;
                    }
                } catch (Exception ePriv) {
                    // Try Public
                    try {
                        java.security.spec.X509EncodedKeySpec pubSpec = new java.security.spec.X509EncodedKeySpec(
                                keyBytes);
                        java.security.KeyFactory kf = java.security.KeyFactory.getInstance("RSA");
                        java.security.interfaces.RSAPublicKey pubKey = (java.security.interfaces.RSAPublicKey) kf
                                .generatePublic(pubSpec);
                        jwk = new com.nimbusds.jose.jwk.RSAKey.Builder(pubKey)
                                .keyID(keyId != null && !keyId.isEmpty() ? keyId : null)
                                .build();
                    } catch (Exception ePub) {
                        throw new Exception("Could not parse as RSA Private (PKCS8) or Public (X509) key.");
                    }
                }
            } else if ("EC".equalsIgnoreCase(keyType)) {
                // Simplified EC handling - requires definition of curve usually.
                // For now, attempting generic parsing or failing gracefully.
                outputArea.setText(
                        "EC Key parsing from raw bytes requires Curve context. \nSupport for Generic EC PEM -> JWK is limited.\nTry converting via File -> Import if possible.");
                return;
            } else if ("OCT".equalsIgnoreCase(keyType)) {
                // Symmetric Key - keyBytes is the secret
                jwk = new com.nimbusds.jose.jwk.OctetSequenceKey.Builder(keyBytes)
                        .keyID(keyId != null && !keyId.isEmpty() ? keyId : null)
                        .build();
            }

            if (jwk != null) {
                // Auto-calc KID if not provided
                if (keyId == null || keyId.trim().isEmpty()) {
                    String thumbprint = jwk.computeThumbprint().toString();
                    // Re-build with kid
                    if (jwk instanceof com.nimbusds.jose.jwk.RSAKey) {
                        jwk = new com.nimbusds.jose.jwk.RSAKey.Builder((com.nimbusds.jose.jwk.RSAKey) jwk)
                                .keyID(thumbprint).build();
                    }
                }

                outputArea.setText(jwk.toJSONString());
                outputArea.appendText("\n\n// Thumbprint (SHA-256): " + jwk.computeThumbprint().toString());
            }

        } catch (Exception e) {
            outputArea.setText("Error converting to JWK: " + e.getMessage());
            LOG.error("PEM key import failed", e);
        }
    }

    public void convertJwkToPem(String jwkJson, TextArea outputArea) {
        try {
            com.nimbusds.jose.jwk.JWK jwk = com.nimbusds.jose.jwk.JWK.parse(jwkJson);

            StringBuilder sb = new StringBuilder();

            if (jwk instanceof com.nimbusds.jose.jwk.RSAKey) {
                com.nimbusds.jose.jwk.RSAKey rsaKey = (com.nimbusds.jose.jwk.RSAKey) jwk;

                // Public
                sb.append("=== Public Key (PEM) ===\n");
                java.security.interfaces.RSAPublicKey pub = rsaKey.toRSAPublicKey();
                String pubPem = java.util.Base64.getMimeEncoder(64, new byte[] { '\n' })
                        .encodeToString(pub.getEncoded());
                sb.append("-----BEGIN PUBLIC KEY-----\n").append(pubPem).append("\n-----END PUBLIC KEY-----\n\n");

                // Private
                if (rsaKey.isPrivate()) {
                    sb.append("=== Private Key (PEM) ===\n");
                    java.security.interfaces.RSAPrivateKey priv = rsaKey.toRSAPrivateKey();
                    String privPem = java.util.Base64.getMimeEncoder(64, new byte[] { '\n' })
                            .encodeToString(priv.getEncoded());
                    sb.append("-----BEGIN PRIVATE KEY-----\n").append(privPem).append("\n-----END PRIVATE KEY-----\n");
                }

                outputArea.setText(sb.toString());

            } else if (jwk instanceof com.nimbusds.jose.jwk.ECKey) {
                com.nimbusds.jose.jwk.ECKey ecKey = (com.nimbusds.jose.jwk.ECKey) jwk;
                // Public
                sb.append("=== Public Key (PEM) ===\n");
                java.security.interfaces.ECPublicKey pub = ecKey.toECPublicKey();
                String pubPem = java.util.Base64.getMimeEncoder(64, new byte[] { '\n' })
                        .encodeToString(pub.getEncoded());
                sb.append("-----BEGIN PUBLIC KEY-----\n").append(pubPem).append("\n-----END PUBLIC KEY-----\n\n");

                if (ecKey.isPrivate()) {
                    sb.append("=== Private Key (PEM) ===\n");
                    java.security.interfaces.ECPrivateKey priv = ecKey.toECPrivateKey();
                    String privPem = java.util.Base64.getMimeEncoder(64, new byte[] { '\n' })
                            .encodeToString(priv.getEncoded());
                    sb.append("-----BEGIN PRIVATE KEY-----\n").append(privPem).append("\n-----END PRIVATE KEY-----\n");
                }
                outputArea.setText(sb.toString());
            } else if (jwk instanceof com.nimbusds.jose.jwk.OctetSequenceKey) {
                com.nimbusds.jose.jwk.OctetSequenceKey octKey = (com.nimbusds.jose.jwk.OctetSequenceKey) jwk;
                sb.append("=== Symmetric Key (Secret) ===\n");
                byte[] secret = octKey.toByteArray();

                sb.append("Length: ").append(secret.length * 8).append(" bits (").append(secret.length)
                        .append(" bytes)\n\n");

                sb.append("Hex:\n");
                for (byte b : secret) {
                    sb.append(String.format("%02x", b));
                }
                sb.append("\n\n");

                sb.append("Base64:\n");
                sb.append(java.util.Base64.getEncoder().encodeToString(secret)).append("\n\n");

                sb.append("Base64URL:\n");
                sb.append(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(secret));

                outputArea.setText(sb.toString());
            } else {
                outputArea.setText("Unsupported or Unknown Key Type for PEM export: " + jwk.getKeyType());
            }

        } catch (Exception e) {
            outputArea.setText("Error converting JWK to PEM: " + e.getMessage());
        }
    }

    public void calculateThumbprint(String input, TextArea outputArea) {
        try {
            // Heuristic: Is it JWK or PEM?
            if (input.trim().startsWith("{")) {
                // Assume JWK
                com.nimbusds.jose.jwk.JWK jwk = com.nimbusds.jose.jwk.JWK.parse(input);
                outputArea.setText("SHA-256 Thumbprint (RFC 7638):\n" + jwk.computeThumbprint().toString());
            } else {
                // Assume PEM -> Convert to JWK -> Calc
                // Reuse convert logic but just output thumbprint?
                // For now, ask user to convert to JWK first for clarity or implement
                // auto-detect.
                outputArea.setText("Please convert PEM to JWK first, or ensure input is valid JSON JWK.");
            }
        } catch (Exception e) {
            outputArea.setText("Error calculating thumbprint: " + e.getMessage());
        }
    }

    private String addSection(TextFlow flow, String title, String part, Color color, boolean isJson, int depth) {

        String indent = "  ".repeat(depth);
        addText(flow, indent + "=== " + title + " ===\n", color, true);
        addText(flow, indent + "Raw: " + part + "\n", Color.GRAY);

        String decodedContent = null;
        try {
            if (part.isEmpty()) {
                addText(flow, indent + "(Empty)\n\n", Color.WHITE);
                return null;
            }
            Base64URL b64 = new Base64URL(part);
            String decoded = b64.decodeToString();
            decodedContent = decoded;

            if (isJson) {
                try {
                    if (title.equals("HEADER")) {
                        decoded = com.nimbusds.jose.JWSHeader.parse(b64).toString();
                    } else if (title.equals("PAYLOAD")) {
                        try {
                            decoded = com.nimbusds.jwt.JWTClaimsSet.parse(decoded).toString();
                        } catch (Exception e) {
                            // Plain text or token string
                        }
                    }
                } catch (Exception e) {
                    /* ignore */ }
            } else {
                byte[] bytes = b64.decode();
                decoded = "Hex: " + bytesToHex(bytes) + " (" + bytes.length + " bytes)";
            }
            // Indent decoded output
            decoded = decoded.replace("\n", "\n" + indent);
            addText(flow, indent + decoded + "\n\n", Color.WHITE);

        } catch (Exception e) {
            addText(flow, indent + "Could not decode: " + e.getMessage() + "\n\n", Color.RED);
        }
        return decodedContent;
    }

    private void addText(TextFlow flow, String text, Color color) {
        addText(flow, text, color, false);
    }

    private void addText(TextFlow flow, String text, Color color, boolean bold) {
        Text t = new Text(text);
        t.setFill(color);
        t.setFont(Font.font("Monospaced", bold ? FontWeight.BOLD : FontWeight.NORMAL, 13));
        flow.getChildren().add(t);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}
