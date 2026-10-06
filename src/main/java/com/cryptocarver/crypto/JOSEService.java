package com.cryptocarver.crypto;

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
import com.cryptocarver.util.DataConverter;
import com.nimbusds.jose.jwk.Curve;
import com.cryptocarver.crypto.hsm.Pkcs11JwsSigner;
import com.cryptocarver.crypto.hsm.Pkcs11Session;

public class JOSEService {

    /** Signs an arbitrary payload as compact JWS (unlike generateSignedJWT, no claims parsing). */
    public static String signJws(String payload, String algorithm, String key) throws Exception {
        JWSAlgorithm selected = JWSAlgorithm.parse(algorithm);
        if ("none".equalsIgnoreCase(selected.getName())) return JoseNoneJws.compact(payload);
        JWSObject object = new JWSObject(new JWSHeader(selected), new Payload(payload));
        object.sign(createSigner(selected, key));
        return object.serialize();
    }

    /** Verifies compact JWS and returns its authenticated payload. */
    public static String verifyJws(String token, String algorithm, String key) throws Exception {
        JWSAlgorithm selected = JWSAlgorithm.parse(algorithm);
        if ("none".equalsIgnoreCase(selected.getName())) return JoseNoneJws.payloadIfUnsigned(token);
        JWSObject object = JWSObject.parse(token);
        if (!selected.equals(object.getHeader().getAlgorithm())) {
            throw new IllegalArgumentException("Header algorithm does not match selection");
        }
        JWSVerifier verifier = createVerifier(selected, key);
        if (!object.verify(verifier)) throw new JOSEException("JWS signature verification failed");
        return object.getPayload().toString();
    }

    /** Encrypts an arbitrary payload as compact JWE. */
    public static String encryptJwe(String payload, String keyAlgorithm, String contentAlgorithm,
                                    String key) throws Exception {
        JWEAlgorithm alg = requireStrongJweAlgorithm(keyAlgorithm);
        EncryptionMethod enc = EncryptionMethod.parse(contentAlgorithm);
        JWEObject object = new JWEObject(new JWEHeader(alg, enc), new Payload(payload));
        object.encrypt(createEncrypter(alg, key));
        return object.serialize();
    }

    /** Decrypts compact JWE and returns the authenticated plaintext. */
    public static String decryptJwe(String token, String key) throws Exception {
        JWEObject object = JWEObject.parse(token);
        JWEAlgorithm alg = requireStrongJweAlgorithm(object.getHeader().getAlgorithm().getName());
        object.decrypt(createDecrypter(alg, key));
        return object.getPayload().toString();
    }

    /** Parses JWT/JWS structure without making a verification claim. */
    public static String inspectJwt(String token) throws Exception {
        JWSObject object = JWSObject.parse(token);
        Map<String, Object> report = new java.util.LinkedHashMap<>();
        report.put("verified", false);
        report.put("header", object.getHeader().toJSONObject());
        report.put("payload", object.getPayload().toString());
        return new com.google.gson.Gson().toJson(report);
    }

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
        return generateSignedJWT(payloadJson, signers, serializationType, unencodedPayload, null);
    }

    /** Generates a JWT with optional caller-authored protected JWS header parameters. */
    public static String generateSignedJWT(String payloadJson, List<SignerConfig> signers, String serializationType,
            boolean unencodedPayload, String customHeaderJson) throws Exception {
        if (signers == null || signers.isEmpty()) {
            throw new IllegalArgumentException("At least one signer must be provided.");
        }
        if (signers.size() > 1 && !("General JSON".equals(serializationType))) {
            throw new IllegalArgumentException("Multiple signatures are only supported when using General JSON serialization.");
        }

        JWTClaimsSet claimsSet = JWTClaimsSet.parse(payloadJson);
        if (signers.size() == 1 && "none".equalsIgnoreCase(signers.get(0).getAlgorithm())) {
            if (unencodedPayload) throw new IllegalArgumentException("alg=none JWT generation does not support b64=false.");
            if (customHeaderJson != null && !customHeaderJson.isBlank()) {
                throw new IllegalArgumentException("Custom protected headers are not supported for alg=none.");
            }
            return JoseNoneJws.signedJwt(claimsSet.toJSONObject(), serializationType);
        }
        Payload payload = unencodedPayload ? new Payload(payloadJson) : new Payload(claimsSet.toJSONObject());

        if ("Compact".equals(serializationType) && signers.size() == 1) {
            SignerConfig config = signers.get(0);
            JWSAlgorithm jwsAlgo = JWSAlgorithm.parse(config.getAlgorithm());
            JWSHeader.Builder headerBuilder = new JWSHeader.Builder(jwsAlgo).type(JOSEObjectType.JWT);
            if (unencodedPayload) {
                headerBuilder.base64URLEncodePayload(false);
                headerBuilder.criticalParams(Collections.singleton("b64"));
            }
            JWSHeader header = withCustomJwsHeader(headerBuilder, customHeaderJson);
            JWSSigner signer = createSigner(jwsAlgo, config.getSecretOrKey(), config.getSecretEncoding());
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
            JWSHeader header = withCustomJwsHeader(headerBuilder, customHeaderJson);
            JWSSigner signer = createSigner(jwsAlgo, config.getSecretOrKey(), config.getSecretEncoding());

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
        return generateDetachedJWS(rawPayload, signers, serializationType, unencodedPayload, null);
    }

    public static String generateDetachedJWS(String rawPayload, List<SignerConfig> signers, String serializationType,
            boolean unencodedPayload, String customHeaderJson) throws Exception {
        if (signers == null || signers.isEmpty()) {
            throw new IllegalArgumentException("At least one signer must be provided.");
        }
        if (signers.size() > 1 && !("General JSON".equals(serializationType))) {
            throw new IllegalArgumentException("Multiple signatures are only supported when using General JSON serialization.");
        }

        if (signers.size() == 1 && "none".equalsIgnoreCase(signers.get(0).getAlgorithm())) {
            if(customHeaderJson!=null && !customHeaderJson.isBlank())
                throw new IllegalArgumentException("Custom protected headers are not supported for alg=none.");
            return JoseNoneJws.detached(serializationType);
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
            JWSHeader header = withCustomJwsHeader(headerBuilder, customHeaderJson);
            JWSSigner signer = createSigner(jwsAlgo, config.getSecretOrKey(), config.getSecretEncoding());
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
            JWSHeader header = withCustomJwsHeader(headerBuilder, customHeaderJson);
            JWSSigner signer = createSigner(jwsAlgo, config.getSecretOrKey(), config.getSecretEncoding());

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
        return verifyDetachedJWS(detachedToken, rawPayload, algorithmStr, keyStr, JoseKeyMaterial.SecretEncoding.UTF8);
    }

    public static boolean verifyDetachedJWS(String detachedToken, String rawPayload, String algorithmStr, String keyStr,
            JoseKeyMaterial.SecretEncoding secretEncoding) throws Exception {
        if ("none".equalsIgnoreCase(algorithmStr)) return JoseNoneJws.verifyDetached(detachedToken, rawPayload);
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

        return object.verify(resolveVerifier(object.getHeader(), keyStr, secretEncoding));
    }

    /**
     * Picks the verifier for a JWS header: a JWKS is searched by {@code kid}
     * (else by {@code alg}, else its first key), a single oct JWK is an HMAC
     * key, and anything else goes through {@link #createVerifier}.
     */
    public static JWSVerifier resolveVerifier(JWSHeader header, String key,
            JoseKeyMaterial.SecretEncoding secretEncoding) throws Exception {
        return resolveVerifier(header, key, secretEncoding, false);
    }

    /** Token-resident keys are considered only when the caller explicitly enables this option. */
    public static JWSVerifier resolveVerifier(JWSHeader header, String key,
            JoseKeyMaterial.SecretEncoding secretEncoding, boolean trustHeaderKey) throws Exception {
        return resolveVerifier(header, key, secretEncoding, trustHeaderKey, Collections.emptySet(), Collections.emptySet());
    }

    /** Critical parameters explicitly accepted by the validator are surfaced to Nimbus as processed/deferred. */
    public static JWSVerifier resolveVerifier(JWSHeader header, String key,
            JoseKeyMaterial.SecretEncoding secretEncoding, boolean trustHeaderKey,
            java.util.Set<String> processedCritical, java.util.Set<String> deferredCritical) throws Exception {
        java.util.Set<String> acceptedCritical = new java.util.HashSet<>(processedCritical);
        acceptedCritical.addAll(deferredCritical);
        if ((key == null || key.isBlank()) && trustHeaderKey) {
            key = JoseJwkPolicy.publicKeyMaterialFromHeader(header);
        }
        JWSAlgorithm algorithm = header.getAlgorithm();
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Provide a verification key or explicitly enable an embedded header key.");
        }
        String trimmed = key.trim();
        com.nimbusds.jose.jwk.JWK match = null;
        if (trimmed.startsWith("{") && trimmed.contains("\"keys\"")) {
            List<com.nimbusds.jose.jwk.JWK> keys = com.nimbusds.jose.jwk.JWKSet.parse(trimmed).getKeys();
            String kid = header.getKeyID();
            if (kid != null) {
                match = keys.stream().filter(k -> kid.equals(k.getKeyID())).findFirst().orElseThrow(
                        () -> new IllegalArgumentException("JWKS does not contain a key matching the token's 'kid': " + kid));
            } else {
                match = keys.stream().filter(k -> algorithm.equals(k.getAlgorithm())).findFirst()
                        .orElse(keys.isEmpty() ? null : keys.get(0));
                if (match == null) throw new IllegalArgumentException("JWKS is empty");
            }
        } else if (trimmed.startsWith("{")) {
            match = com.nimbusds.jose.jwk.JWK.parse(trimmed);
        }

        if (match instanceof com.nimbusds.jose.jwk.OctetSequenceKey oct) {
            if (!JWSAlgorithm.Family.HMAC_SHA.contains(algorithm)) {
                throw new IllegalArgumentException("A symmetric JWK cannot verify " + algorithm + ".");
            }
            return withCriticalParams(new PromiscuousMACVerifier(oct.toByteArray(), algorithm), processedCritical, deferredCritical);
        }
        if (match instanceof com.nimbusds.jose.jwk.RSAKey rsa) {
            return withCriticalParams(new RSASSAVerifier(rsa.toRSAPublicKey(), acceptedCritical), processedCritical, deferredCritical);
        }
        if (match instanceof com.nimbusds.jose.jwk.ECKey ec) {
            if (JWSAlgorithm.ES256K.equals(algorithm)) {
                return withCriticalParams(JoseSecp256k1Jws.verifier(ec.toECPublicKey()), processedCritical, deferredCritical);
            }
            return withCriticalParams(new ECDSAVerifier(ec.toECPublicKey(), acceptedCritical), processedCritical, deferredCritical);
        }
        if (match instanceof com.nimbusds.jose.jwk.OctetKeyPair okp) {
            return withCriticalParams(EdDsaJws.verifier(JoseKeyMaterial.publicKey(okp.toPublicJWK().toJSONString())), processedCritical, deferredCritical);
        }
        if (match != null) throw new IllegalArgumentException("Unsupported JWK type: " + match.getKeyType());
        return withCriticalParams(createVerifier(algorithm, key, secretEncoding), processedCritical, deferredCritical);
    }

    private static JWSVerifier withCriticalParams(JWSVerifier verifier, java.util.Set<String> processed,
            java.util.Set<String> deferred) {
        return new CriticalAwareVerifier(verifier, processed, deferred);
    }

    private static final class CriticalAwareVerifier implements JWSVerifier, CriticalHeaderParamsAware {
        private final JWSVerifier delegate;
        private final java.util.Set<String> processed;
        private final java.util.Set<String> deferred;

        private CriticalAwareVerifier(JWSVerifier delegate, java.util.Set<String> processed,
                java.util.Set<String> deferred) {
            this.delegate = delegate;
            this.processed = java.util.Set.copyOf(processed);
            this.deferred = java.util.Set.copyOf(deferred);
        }

        @Override public boolean verify(JWSHeader header, byte[] signedContent, com.nimbusds.jose.util.Base64URL signature)
                throws JOSEException {
            JWSHeader verificationHeader = header;
            if (!deferred.isEmpty() && header.getCriticalParams() != null) {
                java.util.Set<String> remaining = new java.util.HashSet<>(header.getCriticalParams());
                remaining.removeAll(deferred);
                java.util.Map<String, Object> json = new java.util.LinkedHashMap<>(header.toJSONObject());
                if (remaining.isEmpty()) json.remove("crit"); else json.put("crit", remaining);
                try {
                    verificationHeader = JWSHeader.parse(json);
                } catch (java.text.ParseException ex) {
                    throw new JOSEException("Could not prepare explicitly deferred critical parameters.", ex);
                }
            }
            return delegate.verify(verificationHeader, signedContent, signature);
        }
        @Override public java.util.Set<JWSAlgorithm> supportedJWSAlgorithms() { return delegate.supportedJWSAlgorithms(); }
        @Override public com.nimbusds.jose.jca.JCAContext getJCAContext() { return delegate.getJCAContext(); }
        @Override public java.util.Set<String> getProcessedCriticalHeaderParams() { return processed; }
        @Override public java.util.Set<String> getDeferredCriticalHeaderParams() { return deferred; }
    }

    static JWSHeader withCustomJwsHeader(JWSHeader.Builder builder, String customHeaderJson) throws Exception {
        JWSHeader base = builder.build();
        if (customHeaderJson == null || customHeaderJson.isBlank()) return base;
        Map<String, Object> custom;
        try {
            custom = com.nimbusds.jose.util.JSONObjectUtils.parse(customHeaderJson);
        } catch (java.text.ParseException e) {
            throw new IllegalArgumentException("Protected header parameters must be a JSON object.", e);
        }
        for (String reserved : List.of("alg", "b64", "crit")) {
            if (custom.containsKey(reserved)) {
                throw new IllegalArgumentException("Protected header JSON cannot override '" + reserved + "'.");
            }
        }
        if (custom.containsKey("jwk")) {
            com.nimbusds.jose.jwk.JWK embedded;
            try {
                if (!(custom.get("jwk") instanceof Map<?, ?>)) throw new IllegalArgumentException();
                embedded = com.nimbusds.jose.jwk.JWK.parse((Map<String,Object>)custom.get("jwk"));
            } catch(Exception invalid) { throw new IllegalArgumentException("The embedded jwk must be a valid public key."); }
            if (embedded.isPrivate() || embedded instanceof com.nimbusds.jose.jwk.OctetSequenceKey)
                throw new IllegalArgumentException("The embedded jwk must not contain private or symmetric key material.");
        }
        Map<String, Object> merged = new java.util.LinkedHashMap<>(base.toJSONObject());
        merged.putAll(custom);
        return JWSHeader.parse(merged);
    }

    public static String generateNestedJWT(String payloadJson, String signAlgoStr, String signKey, String keyAlgoStr, String encAlgoStr, String encKey) throws Exception {
        return generateNestedJWT(payloadJson, signAlgoStr, signKey, keyAlgoStr, encAlgoStr, encKey, false);
    }

    /** Signs the claims, then encrypts the JWS with {@code cty: JWT} (RFC 7519 §5.2). */
    public static String generateNestedJWT(String payloadJson, String signAlgoStr, String signKey, String keyAlgoStr,
            String encAlgoStr, String encKey, boolean compress) throws Exception {
        return generateNestedJWT(payloadJson, signAlgoStr, signKey, keyAlgoStr, encAlgoStr, encKey, compress,
                JoseKeyMaterial.SecretEncoding.UTF8);
    }

    /** {@code secretEncoding} decodes the HMAC signing secret and the {@code dir} key. */
    public static String generateNestedJWT(String payloadJson, String signAlgoStr, String signKey, String keyAlgoStr,
            String encAlgoStr, String encKey, boolean compress, JoseKeyMaterial.SecretEncoding secretEncoding)
            throws Exception {
        // 1. Sign
        JWSAlgorithm signAlgo = JWSAlgorithm.parse(signAlgoStr);
        JWSSigner signer = createSigner(signAlgo, signKey, secretEncoding);
        JWTClaimsSet claimsSet = JWTClaimsSet.parse(payloadJson);

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(signAlgo).type(JOSEObjectType.JWT).build(),
                claimsSet);
        signedJWT.sign(signer);
        String jwsToken = signedJWT.serialize();

        // 2. Encrypt
        JWEAlgorithm jweAlgo = JWEAlgorithm.parse(keyAlgoStr);
        EncryptionMethod encMethod = EncryptionMethod.parse(encAlgoStr);
        JWEEncrypter encrypter = JWEAlgorithm.DIR.equals(jweAlgo)
                ? new DirectEncrypter(JoseKeyMaterial.secret(encKey, secretEncoding))
                : createEncrypter(jweAlgo, encKey);

        JWEHeader.Builder jweHeader = new JWEHeader.Builder(jweAlgo, encMethod)
                .contentType("JWT"); // Recommended for nested tokens
        if (compress) jweHeader.compressionAlgorithm(CompressionAlgorithm.DEF);

        JWEObject jweObject = new JWEObject(jweHeader.build(), new Payload(jwsToken));
        jweObject.encrypt(encrypter);

        return jweObject.serialize();
    }

    /**
     * Decrypts and verifies a nested JWT. The verification key may be the
     * signer's private key: its public half is derived.
     */
    public static String verifyNestedJWT(String nestedToken, String decryptionKeyPEM, String verificationKeyPEM) throws Exception {
        return verifyNestedJWT(nestedToken, decryptionKeyPEM, verificationKeyPEM, JoseKeyMaterial.SecretEncoding.UTF8);
    }

    public static String verifyNestedJWT(String nestedToken, String decryptionKeyPEM, String verificationKeyPEM,
            JoseKeyMaterial.SecretEncoding secretEncoding) throws Exception {
        JWEObject jweObject = JWEObject.parse(nestedToken);
        JWEAlgorithm alg = jweObject.getHeader().getAlgorithm();
        if (!JWEAlgorithm.Family.RSA.contains(alg) && !JWEAlgorithm.Family.ECDH_ES.contains(alg)
                && !JWEAlgorithm.DIR.equals(alg)) {
            throw new IllegalArgumentException("Unsupported decryption algorithm: " + alg.getName());
        }
        jweObject.decrypt(JweComposer.decrypter(alg, decryptionKeyPEM, secretEncoding, new JweComposer.LoadedKey()));
        SignedJWT signedJWT = jweObject.getPayload().toSignedJWT();
        if (signedJWT == null) {
            throw new IllegalArgumentException("The decrypted payload is not a valid Signed JWT.");
        }

        JWSAlgorithm signAlg = signedJWT.getHeader().getAlgorithm();
        JWSVerifier verifier;
        try {
            verifier = createVerifier(signAlg, verificationKeyPEM, secretEncoding);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported verification algorithm: " + signAlg.getName(), e);
        }

        if (!signedJWT.verify(verifier)) {
            throw new JOSEException("Nested JWT decrypted, but inner signature verification failed.");
        }

        return new com.google.gson.Gson().toJson(signedJWT.getJWTClaimsSet().toJSONObject());
    }

    public static JWSSigner createSigner(JWSAlgorithm jwsAlgo, String secretOrKey) throws Exception {
        return createSigner(jwsAlgo, secretOrKey, JoseKeyMaterial.SecretEncoding.UTF8);
    }

    /** {@code secretEncoding} decodes HMAC secrets; RSA/EC keys ignore it. */
    public static JWSSigner createSigner(JWSAlgorithm jwsAlgo, String secretOrKey,
            JoseKeyMaterial.SecretEncoding secretEncoding) throws Exception {
        if ("none".equalsIgnoreCase(jwsAlgo.getName())) return JoseNoneJws.signer();
        if (JWSAlgorithm.ES256K.equals(jwsAlgo)) return JoseSecp256k1Jws.signer(JoseKeyMaterial.privateKey(secretOrKey));
        if (JWSAlgorithm.Family.HMAC_SHA.contains(jwsAlgo)) {
            if (secretOrKey.trim().startsWith("-----BEGIN")) {
                throw new IllegalArgumentException("Detected PEM Key for HMAC Algorithm. HMAC uses a shared secret.");
            }
            return new PromiscuousMACSigner(JoseKeyMaterial.secret(secretOrKey, secretEncoding), jwsAlgo);
        } else if (JWSAlgorithm.Family.RSA.contains(jwsAlgo)) {
            return new RSASSASigner(parseRSAPrivateKey(secretOrKey));
        } else if (JWSAlgorithm.Family.EC.contains(jwsAlgo)) {
            return new ECDSASigner(requireEcPrivateKey(jwsAlgo, secretOrKey));
        } else if (JWSAlgorithm.EdDSA.equals(jwsAlgo)) {
            return EdDsaJws.signer(JoseKeyMaterial.privateKey(secretOrKey));
        } else {
            throw new IllegalArgumentException("Unsupported algorithm family: " + jwsAlgo.getName());
        }
    }

    public static JWEEncrypter createEncrypter(JWEAlgorithm jweAlgo, String encKey) throws Exception {
        if (JWEAlgorithm.Family.RSA.contains(jweAlgo)) {
            java.security.PublicKey pubKey = parseRSAPublicKey(encKey);
            return new RSAEncrypter((java.security.interfaces.RSAPublicKey) pubKey);
        } else if (JWEAlgorithm.Family.ECDH_ES.contains(jweAlgo)) {
            java.security.PublicKey publicKey = JoseKeyMaterial.publicKey(encKey);
            if (publicKey instanceof java.security.interfaces.XECPublicKey) return JoseXdhJwe.encrypter(publicKey);
            return new ECDHEncrypter((java.security.interfaces.ECPublicKey) publicKey);
        } else if (JWEAlgorithm.DIR.equals(jweAlgo)) {
            byte[] keyBytes = encKey.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return new DirectEncrypter(keyBytes);
        } else {
            throw new IllegalArgumentException("Unsupported JWE algorithm for Nested JWT in JOSEService: " + jweAlgo.getName());
        }
    }

    private static JWEAlgorithm requireStrongJweAlgorithm(String name) {
        JWEAlgorithm algorithm = JWEAlgorithm.parse(name);
        if (!JWEAlgorithm.Family.RSA.contains(algorithm) && !JWEAlgorithm.DIR.equals(algorithm)) {
            throw new IllegalArgumentException("Unsupported JWE key algorithm: " + name);
        }
        return algorithm;
    }

    private static JWEDecrypter createDecrypter(JWEAlgorithm algorithm, String key) throws Exception {
        if (JWEAlgorithm.Family.RSA.contains(algorithm)) {
            return new RSADecrypter(parseRSAPrivateKey(key));
        }
        return new DirectDecrypter(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Public because SD-JWT verification needs the same key-to-verifier
     *  resolution and there is no reason for a second copy of it. */
    public static JWSVerifier createVerifier(JWSAlgorithm algorithm, String key) throws Exception {
        return createVerifier(algorithm, key, JoseKeyMaterial.SecretEncoding.UTF8);
    }

    /** {@code secretEncoding} decodes HMAC secrets; RSA/EC keys ignore it. */
    public static JWSVerifier createVerifier(JWSAlgorithm algorithm, String key,
            JoseKeyMaterial.SecretEncoding secretEncoding) throws Exception {
        if ("none".equalsIgnoreCase(algorithm.getName())) return JoseNoneJws.verifier();
        if (JWSAlgorithm.ES256K.equals(algorithm)) return JoseSecp256k1Jws.verifier(JoseKeyMaterial.publicKey(key));
        if (JWSAlgorithm.Family.HMAC_SHA.contains(algorithm)) {
            return new PromiscuousMACVerifier(JoseKeyMaterial.secret(key, secretEncoding), algorithm);
        }
        if (JWSAlgorithm.Family.RSA.contains(algorithm)) return new RSASSAVerifier((RSAPublicKey) parseRSAPublicKey(key));
        if (JWSAlgorithm.Family.EC.contains(algorithm)) return new ECDSAVerifier(requireEcPublicKey(algorithm, key));
        if (JWSAlgorithm.EdDSA.equals(algorithm)) return EdDsaJws.verifier(JoseKeyMaterial.publicKey(key));
        throw new IllegalArgumentException("Unsupported JWS algorithm: " + algorithm);
    }

    /** Accepts PKCS#8, PKCS#1, bare DER or JWK; see {@link JoseKeyMaterial}. */
    public static PrivateKey parseRSAPrivateKey(String pem) throws Exception {
        return JoseKeyMaterial.rsaPrivateKey(pem);
    }

    /** Accepts PKCS#8, SEC1, bare DER or JWK; see {@link JoseKeyMaterial}. */
    public static ECPrivateKey parseECPrivateKey(String pem) throws Exception {
        return JoseKeyMaterial.ecPrivateKey(pem);
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

    /** Accepts SubjectPublicKeyInfo, PKCS#1, certificates, JWK or a private key to derive from. */
    public static java.security.PublicKey parseRSAPublicKey(String pem) throws Exception {
        return JoseKeyMaterial.rsaPublicKey(pem);
    }

    public static java.security.interfaces.ECPublicKey requireEcPublicKey(JWSAlgorithm algo, String pem) throws Exception {
        java.security.interfaces.ECPublicKey ecKey = JoseKeyMaterial.ecPublicKey(pem);

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
            this(secretStr.getBytes(java.nio.charset.StandardCharsets.UTF_8), algorithm);
        }

        public PromiscuousMACSigner(byte[] secret, JWSAlgorithm algorithm) {
            this.secret = secret.clone();
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
            this(secretStr.getBytes(java.nio.charset.StandardCharsets.UTF_8), algorithm);
        }

        public PromiscuousMACVerifier(byte[] secret, JWSAlgorithm algorithm) {
            this.secret = secret.clone();
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
