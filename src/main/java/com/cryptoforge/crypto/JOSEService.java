package com.cryptoforge.crypto;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jose.crypto.impl.ECDSA;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import com.cryptoforge.util.DataConverter;
import com.nimbusds.jose.jwk.Curve;
import com.cryptoforge.crypto.hsm.Pkcs11JwsSigner;
import com.cryptoforge.crypto.hsm.Pkcs11Session;

public class JOSEService {

    /** Generates a compact JWS with a token-resident private key. */
    public static String generateSignedJwtWithPkcs11(String payloadJson, String algorithm,
            Pkcs11Session session, String keyAlias) throws Exception {
        JWSAlgorithm jwsAlgorithm = JWSAlgorithm.parse(algorithm);
        if (!new Pkcs11JwsSigner(session, keyAlias).supportedJWSAlgorithms().contains(jwsAlgorithm)) {
            throw new IllegalArgumentException("Unsupported PKCS#11 JWT algorithm: " + algorithm);
        }
        JWTClaimsSet claimsSet = JWTClaimsSet.parse(payloadJson);
        JWSHeader header = new JWSHeader.Builder(jwsAlgorithm).type(JOSEObjectType.JWT).keyID(keyAlias).build();
        JWSObject object = new JWSObject(header, new Payload(claimsSet.toJSONObject()));
        object.sign(new Pkcs11JwsSigner(session, keyAlias));
        return object.serialize();
    }

    public static String generateSignedJWT(String payloadJson, List<SignerConfig> signers, String serializationType, boolean unencodedPayload) throws Exception {
        if (signers == null || signers.isEmpty()) {
            throw new IllegalArgumentException("At least one signer must be provided.");
        }
        if (signers.size() > 1 && !("General JSON".equals(serializationType))) {
            throw new IllegalArgumentException("Multiple signatures are only supported when using General JSON serialization.");
        }

        JWTClaimsSet claimsSet = JWTClaimsSet.parse(payloadJson);
        Payload payload = unencodedPayload ? new Payload(payloadJson) : new Payload(claimsSet.toJSONObject());

        if ("Compact".equals(serializationType) && signers.size() == 1) {
            SignerConfig config = signers.get(0);
            JWSAlgorithm jwsAlgo = JWSAlgorithm.parse(config.getAlgorithm());
            JWSHeader.Builder headerBuilder = new JWSHeader.Builder(jwsAlgo).type(JOSEObjectType.JWT);
            if (unencodedPayload) {
                headerBuilder.base64URLEncodePayload(false);
                headerBuilder.criticalParams(Collections.singleton("b64"));
            }
            JWSHeader header = headerBuilder.build();
            JWSSigner signer = createSigner(jwsAlgo, config.getSecretOrKey());
            JWSObject jwsObject = new JWSObject(header, payload);
            jwsObject.sign(signer);
            return jwsObject.serialize(unencodedPayload); // unencodedPayload true -> detached
        }

        // JSON Serialization
        Map<String, Object> json = new HashMap<>();
        if (!unencodedPayload) {
            json.put("payload", payload.toBase64URL().toString());
        } // For unencoded payloads in JSON, if detached is requested we don't put payload, but if attached, we put raw string. We will implement detached for b64=false in JSON

        List<Map<String, Object>> signaturesList = new ArrayList<>();
        for (SignerConfig config : signers) {
            JWSAlgorithm jwsAlgo = JWSAlgorithm.parse(config.getAlgorithm());
            JWSHeader.Builder headerBuilder = new JWSHeader.Builder(jwsAlgo).type(JOSEObjectType.JWT);
            if (unencodedPayload) {
                headerBuilder.base64URLEncodePayload(false);
                headerBuilder.criticalParams(Collections.singleton("b64"));
            }
            JWSHeader header = headerBuilder.build();
            JWSSigner signer = createSigner(jwsAlgo, config.getSecretOrKey());
            
            JWSObject jwsObject = new JWSObject(header, payload);
            jwsObject.sign(signer);

            Map<String, Object> sigObj = new HashMap<>();
            sigObj.put("protected", jwsObject.getHeader().toBase64URL().toString());
            sigObj.put("signature", jwsObject.getSignature().toString());
            signaturesList.add(sigObj);
        }

        if ("Flattened JSON".equals(serializationType)) {
            json.put("protected", signaturesList.get(0).get("protected"));
            json.put("signature", signaturesList.get(0).get("signature"));
        } else {
            json.put("signatures", signaturesList);
        }

        return new com.google.gson.Gson().toJson(json);
    }

    public static String generateDetachedJWS(String rawPayload, List<SignerConfig> signers, String serializationType, boolean unencodedPayload) throws Exception {
        if (signers == null || signers.isEmpty()) {
            throw new IllegalArgumentException("At least one signer must be provided.");
        }
        if (signers.size() > 1 && !("General JSON".equals(serializationType))) {
            throw new IllegalArgumentException("Multiple signatures are only supported when using General JSON serialization.");
        }

        Payload payload = new Payload(rawPayload);

        if ("Compact".equals(serializationType) && signers.size() == 1) {
            SignerConfig config = signers.get(0);
            JWSAlgorithm jwsAlgo = JWSAlgorithm.parse(config.getAlgorithm());
            JWSHeader.Builder headerBuilder = new JWSHeader.Builder(jwsAlgo);
            if (unencodedPayload) {
                headerBuilder.base64URLEncodePayload(false);
                headerBuilder.criticalParams(Collections.singleton("b64"));
            }
            JWSHeader header = headerBuilder.build();
            JWSSigner signer = createSigner(jwsAlgo, config.getSecretOrKey());
            JWSObject jwsObject = new JWSObject(header, payload);
            jwsObject.sign(signer);
            return jwsObject.serialize(true); // true = detached compact
        }

        // JSON Serialization
        Map<String, Object> json = new HashMap<>();
        // In detached mode, we NEVER put the payload in the JSON
        
        List<Map<String, Object>> signaturesList = new ArrayList<>();
        for (SignerConfig config : signers) {
            JWSAlgorithm jwsAlgo = JWSAlgorithm.parse(config.getAlgorithm());
            JWSHeader.Builder headerBuilder = new JWSHeader.Builder(jwsAlgo);
            if (unencodedPayload) {
                headerBuilder.base64URLEncodePayload(false);
                headerBuilder.criticalParams(Collections.singleton("b64"));
            }
            JWSHeader header = headerBuilder.build();
            JWSSigner signer = createSigner(jwsAlgo, config.getSecretOrKey());
            
            JWSObject jwsObject = new JWSObject(header, payload);
            jwsObject.sign(signer);

            Map<String, Object> sigObj = new HashMap<>();
            sigObj.put("protected", jwsObject.getHeader().toBase64URL().toString());
            sigObj.put("signature", jwsObject.getSignature().toString());
            signaturesList.add(sigObj);
        }

        if ("Flattened JSON".equals(serializationType)) {
            json.put("protected", signaturesList.get(0).get("protected"));
            json.put("signature", signaturesList.get(0).get("signature"));
        } else {
            json.put("signatures", signaturesList);
        }

        return new com.google.gson.Gson().toJson(json);
    }

    public static boolean verifyDetachedJWS(String detachedToken, String rawPayload, String algorithmStr, String keyStr) throws Exception {
        JWSObject object;
        try {
            // Try to parse as Compact
            object = JWSObject.parse(detachedToken, new Payload(rawPayload));
        } catch (java.text.ParseException e) {
            // Try to parse as JSON (Nimbus throws error on parse if payload is missing, so we assemble manually)
            try {
                java.util.Map<String, Object> map = new com.google.gson.Gson().fromJson(detachedToken, java.util.Map.class);
                String protectedHeader = null;
                String signature = null;

                if (map.containsKey("signatures")) {
                    // General JSON
                    List<Map<String, Object>> sigs = (List<Map<String, Object>>) map.get("signatures");
                    if (sigs.isEmpty()) throw new IllegalArgumentException("No signatures found in JSON.");
                    // For the UI verify flow, we'll just check the first signature if multiple are present, 
                    // or ideally loop. The UI passes one algorithm and key. Let's find one that matches the algo.
                    for (Map<String, Object> sigObj : sigs) {
                        String ph = (String) sigObj.get("protected");
                        JWSHeader h = JWSHeader.parse(new com.nimbusds.jose.util.Base64URL(ph));
                        if (h.getAlgorithm().getName().equals(algorithmStr)) {
                            protectedHeader = ph;
                            signature = (String) sigObj.get("signature");
                            break;
                        }
                    }
                    if (protectedHeader == null) throw new IllegalArgumentException("No signature matched the selected algorithm.");
                } else {
                    // Flattened JSON
                    protectedHeader = (String) map.get("protected");
                    signature = (String) map.get("signature");
                }
                object = new JWSObject(
                        new com.nimbusds.jose.util.Base64URL(protectedHeader),
                        new Payload(rawPayload),
                        new com.nimbusds.jose.util.Base64URL(signature)
                );
            } catch (Exception ex) {
                throw new IllegalArgumentException("Could not parse detached JWS as Compact or JSON.", ex);
            }
        }

        JWSAlgorithm actual = object.getHeader().getAlgorithm();
        if (!actual.equals(JWSAlgorithm.parse(algorithmStr))) {
            throw new IllegalArgumentException("Header algorithm does not match selection");
        }

        com.nimbusds.jose.JWSVerifier verifier = null;
        String keyStringTrimmed = keyStr.trim();
        
        if (keyStringTrimmed.startsWith("{") && keyStringTrimmed.contains("\"keys\"")) {
            com.nimbusds.jose.jwk.JWKSet jwkSet = com.nimbusds.jose.jwk.JWKSet.parse(keyStringTrimmed);
            String kid = object.getHeader().getKeyID();
            com.nimbusds.jose.jwk.JWK match = null;
            if (kid != null) {
                match = jwkSet.getKeyByKeyId(kid);
                if (match == null) throw new IllegalArgumentException("JWKS does not contain a key matching the token's 'kid': " + kid);
            } else {
                for (com.nimbusds.jose.jwk.JWK k : jwkSet.getKeys()) {
                    if (k.getAlgorithm() != null && k.getAlgorithm().equals(actual)) {
                        match = k; break;
                    }
                }
                if (match == null && !jwkSet.getKeys().isEmpty()) match = jwkSet.getKeys().get(0);
                if (match == null) throw new IllegalArgumentException("JWKS is empty");
            }

            if (match instanceof com.nimbusds.jose.jwk.RSAKey) {
                verifier = new com.nimbusds.jose.crypto.RSASSAVerifier(((com.nimbusds.jose.jwk.RSAKey) match).toRSAPublicKey());
            } else if (match instanceof com.nimbusds.jose.jwk.ECKey) {
                verifier = new com.nimbusds.jose.crypto.ECDSAVerifier(((com.nimbusds.jose.jwk.ECKey) match).toECPublicKey());
            } else if (match instanceof com.nimbusds.jose.jwk.OctetSequenceKey) {
                verifier = new com.nimbusds.jose.crypto.MACVerifier(((com.nimbusds.jose.jwk.OctetSequenceKey) match).toByteArray());
            } else {
                throw new IllegalArgumentException("Unsupported JWK type: " + match.getKeyType());
            }
        } else {
            if (JWSAlgorithm.Family.HMAC_SHA.contains(actual)) verifier = new PromiscuousMACVerifier(keyStr, actual);
            else if (JWSAlgorithm.Family.RSA.contains(actual)) verifier = new com.nimbusds.jose.crypto.RSASSAVerifier((java.security.interfaces.RSAPublicKey) parseRSAPublicKey(keyStr));
            else if (JWSAlgorithm.Family.EC.contains(actual)) verifier = new com.nimbusds.jose.crypto.ECDSAVerifier(requireEcPublicKey(actual, keyStr));
        }

        if (verifier == null) throw new IllegalArgumentException("Unsupported detached JWS algorithm: " + actual);
        return object.verify(verifier);
    }

    public static String generateNestedJWT(String payloadJson, String signAlgoStr, String signKey, String keyAlgoStr, String encAlgoStr, String encKey) throws Exception {
        // 1. Sign
        JWSAlgorithm signAlgo = JWSAlgorithm.parse(signAlgoStr);
        JWSSigner signer = createSigner(signAlgo, signKey);
        JWTClaimsSet claimsSet = JWTClaimsSet.parse(payloadJson);

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(signAlgo).type(JOSEObjectType.JWT).build(),
                claimsSet);
        signedJWT.sign(signer);
        String jwsToken = signedJWT.serialize();

        // 2. Encrypt
        JWEAlgorithm jweAlgo = JWEAlgorithm.parse(keyAlgoStr);
        EncryptionMethod encMethod = EncryptionMethod.parse(encAlgoStr);
        JWEEncrypter encrypter = createEncrypter(jweAlgo, encKey);

        JWEHeader jweHeader = new JWEHeader.Builder(jweAlgo, encMethod)
                .contentType("JWT") // Recommended for nested tokens
                .build();

        JWEObject jweObject = new JWEObject(jweHeader, new Payload(jwsToken));
        jweObject.encrypt(encrypter);

        return jweObject.serialize();
    }

    public static String verifyNestedJWT(String nestedToken, String decryptionKeyPEM, String verificationKeyPEM) throws Exception {
        JWEObject jweObject = JWEObject.parse(nestedToken);
        JWEAlgorithm alg = jweObject.getHeader().getAlgorithm();
        JWEDecrypter decrypter;

        if (JWEAlgorithm.Family.RSA.contains(alg)) {
            decrypter = new RSADecrypter(parseRSAPrivateKey(decryptionKeyPEM));
        } else if (JWEAlgorithm.DIR.equals(alg)) {
            byte[] keyBytes = decryptionKeyPEM.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            decrypter = new DirectDecrypter(keyBytes);
        } else {
            throw new IllegalArgumentException("Unsupported decryption algorithm: " + alg.getName());
        }

        jweObject.decrypt(decrypter);
        SignedJWT signedJWT = jweObject.getPayload().toSignedJWT();
        if (signedJWT == null) {
            throw new IllegalArgumentException("The decrypted payload is not a valid Signed JWT.");
        }

        JWSAlgorithm signAlg = signedJWT.getHeader().getAlgorithm();
        JWSVerifier verifier;

        if (JWSAlgorithm.Family.HMAC_SHA.contains(signAlg)) {
            verifier = new MACVerifier(verificationKeyPEM.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } else if (JWSAlgorithm.Family.RSA.contains(signAlg)) {
            // Need public key for verification
            java.security.PublicKey pubKey = parseRSAPublicKey(verificationKeyPEM);
            verifier = new RSASSAVerifier((RSAPublicKey) pubKey);
        } else {
            throw new IllegalArgumentException("Unsupported verification algorithm: " + signAlg.getName());
        }

        if (!signedJWT.verify(verifier)) {
            throw new JOSEException("Nested JWT decrypted, but inner signature verification failed.");
        }

        return new com.google.gson.Gson().toJson(signedJWT.getJWTClaimsSet().toJSONObject());
    }

    public static JWSSigner createSigner(JWSAlgorithm jwsAlgo, String secretOrKey) throws Exception {
        if (JWSAlgorithm.Family.HMAC_SHA.contains(jwsAlgo)) {
            if (secretOrKey.trim().startsWith("-----BEGIN")) {
                throw new IllegalArgumentException("Detected PEM Key for HMAC Algorithm. HMAC uses a shared secret.");
            }
            return new PromiscuousMACSigner(secretOrKey, jwsAlgo);
        } else if (JWSAlgorithm.Family.RSA.contains(jwsAlgo)) {
            return new RSASSASigner(parseRSAPrivateKey(secretOrKey));
        } else if (JWSAlgorithm.Family.EC.contains(jwsAlgo)) {
            return new ECDSASigner(requireEcPrivateKey(jwsAlgo, secretOrKey));
        } else {
            throw new IllegalArgumentException("Unsupported algorithm family: " + jwsAlgo.getName());
        }
    }

    public static JWEEncrypter createEncrypter(JWEAlgorithm jweAlgo, String encKey) throws Exception {
        if (JWEAlgorithm.Family.RSA.contains(jweAlgo)) {
            java.security.PublicKey pubKey = parseRSAPublicKey(encKey);
            return new RSAEncrypter((java.security.interfaces.RSAPublicKey) pubKey);
        } else if (JWEAlgorithm.DIR.equals(jweAlgo)) {
            byte[] keyBytes = encKey.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return new DirectEncrypter(keyBytes);
        } else {
            throw new IllegalArgumentException("Unsupported JWE algorithm for Nested JWT in JOSEService: " + jweAlgo.getName());
        }
    }

    public static PrivateKey parseRSAPrivateKey(String pem) throws Exception {
        String privateKeyPEM = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] encoded = DataConverter.decodeBase64Flexible(privateKeyPEM);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
    }

    public static ECPrivateKey parseECPrivateKey(String pem) throws Exception {
        if (pem.contains("-----BEGIN EC PRIVATE KEY-----")) {
            throw new IllegalArgumentException("EC private keys must be PEM PKCS#8 (BEGIN PRIVATE KEY), not SEC1");
        }
        String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] encoded = DataConverter.decodeBase64Flexible(base64);
        PrivateKey key = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(encoded));
        if (!(key instanceof ECPrivateKey ecKey)) {
            throw new IllegalArgumentException("The supplied PKCS#8 key is not an EC private key");
        }
        return ecKey;
    }

    public static ECPrivateKey requireEcPrivateKey(JWSAlgorithm algo, String secretOrKey) throws Exception {
        ECPrivateKey privateKey = parseECPrivateKey(secretOrKey);
        Curve requiredCurve = Curve.forJWSAlgorithm(algo).iterator().next();
        Curve keyCurve = Curve.forECParameterSpec(privateKey.getParams());
        if (!requiredCurve.equals(keyCurve)) {
            throw new JOSEException("The provided EC key (curve " + keyCurve + ") doesn't match the algorithm " + algo + " which requires curve " + requiredCurve);
        }
        return privateKey;
    }

    public static java.security.PublicKey parseRSAPublicKey(String pem) throws Exception {
        String publicKeyPEM = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("-----BEGIN RSA PUBLIC KEY-----", "")
                .replace("-----END RSA PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        byte[] encoded = DataConverter.decodeBase64Flexible(publicKeyPEM);
        return KeyFactory.getInstance("RSA").generatePublic(new java.security.spec.X509EncodedKeySpec(encoded));
    }

    public static java.security.interfaces.ECPublicKey requireEcPublicKey(JWSAlgorithm algo, String pem) throws Exception {
        String base64 = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "").replaceAll("\\s", "");
        java.security.PublicKey key = KeyFactory.getInstance("EC").generatePublic(new java.security.spec.X509EncodedKeySpec(DataConverter.decodeBase64Flexible(base64)));
        if (!(key instanceof java.security.interfaces.ECPublicKey ecKey)) throw new IllegalArgumentException("The supplied key is not an EC public key");
        
        Curve requiredCurve = Curve.forJWSAlgorithm(algo).iterator().next();
        Curve keyCurve = Curve.forECParameterSpec(ecKey.getParams());
        if (!requiredCurve.equals(keyCurve)) {
            throw new JOSEException("The provided EC public key doesn't match the algorithm " + algo);
        }
        return ecKey;
    }

    public static class PromiscuousMACSigner implements JWSSigner {
        private final byte[] secret;
        private final JWSAlgorithm algorithm;
        private final com.nimbusds.jose.jca.JCAContext jcaContext = new com.nimbusds.jose.jca.JCAContext();

        public PromiscuousMACSigner(String secretStr, JWSAlgorithm algorithm) {
            this.secret = secretStr.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            this.algorithm = algorithm;
        }

        @Override
        public com.nimbusds.jose.util.Base64URL sign(final JWSHeader header, final byte[] signingInput) throws JOSEException {
            try {
                String jcaAlgo = getJCAAlgorithmName(header.getAlgorithm());
                javax.crypto.Mac mac = javax.crypto.Mac.getInstance(jcaAlgo);
                mac.init(new javax.crypto.spec.SecretKeySpec(secret, jcaAlgo));
                return com.nimbusds.jose.util.Base64URL.encode(mac.doFinal(signingInput));
            } catch (Exception e) {
                throw new JOSEException(e.getMessage(), e);
            }
        }

        @Override
        public java.util.Set<JWSAlgorithm> supportedJWSAlgorithms() {
            return Collections.singleton(algorithm);
        }

        @Override
        public com.nimbusds.jose.jca.JCAContext getJCAContext() {
            return jcaContext;
        }
    }

    public static class PromiscuousMACVerifier implements JWSVerifier {
        private final byte[] secret;
        private final JWSAlgorithm algorithm;
        private final com.nimbusds.jose.jca.JCAContext jcaContext = new com.nimbusds.jose.jca.JCAContext();

        public PromiscuousMACVerifier(String secretStr, JWSAlgorithm algorithm) {
            this.secret = secretStr.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            this.algorithm = algorithm;
        }

        @Override
        public boolean verify(JWSHeader header, byte[] signedContent, com.nimbusds.jose.util.Base64URL signature) throws JOSEException {
            if (!header.getAlgorithm().equals(algorithm)) {
                return false;
            }
            try {
                String jcaAlgo = getJCAAlgorithmName(header.getAlgorithm());
                javax.crypto.Mac mac = javax.crypto.Mac.getInstance(jcaAlgo);
                mac.init(new javax.crypto.spec.SecretKeySpec(secret, jcaAlgo));
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
        public java.util.Set<JWSAlgorithm> supportedJWSAlgorithms() {
            return Collections.singleton(algorithm);
        }

        @Override
        public com.nimbusds.jose.jca.JCAContext getJCAContext() {
            return jcaContext;
        }
    }

    public static String getJCAAlgorithmName(JWSAlgorithm alg) throws JOSEException {
        if (alg.equals(JWSAlgorithm.HS256)) return "HmacSHA256";
        if (alg.equals(JWSAlgorithm.HS384)) return "HmacSHA384";
        if (alg.equals(JWSAlgorithm.HS512)) return "HmacSHA512";
        throw new JOSEException("Unsupported MAC algorithm: " + alg);
    }
}
