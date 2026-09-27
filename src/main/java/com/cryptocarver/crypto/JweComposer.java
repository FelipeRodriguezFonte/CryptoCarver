package com.cryptocarver.crypto;

import com.cryptocarver.crypto.JoseKeyMaterial.SecretEncoding;
import com.nimbusds.jose.CompressionAlgorithm;
import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEDecrypter;
import com.nimbusds.jose.JWEEncrypter;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.JWEObjectJSON;
import com.nimbusds.jose.crypto.MultiEncrypter;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.AESDecrypter;
import com.nimbusds.jose.crypto.AESEncrypter;
import com.nimbusds.jose.crypto.DirectDecrypter;
import com.nimbusds.jose.crypto.DirectEncrypter;
import com.nimbusds.jose.crypto.ECDHDecrypter;
import com.nimbusds.jose.crypto.ECDHEncrypter;
import com.nimbusds.jose.crypto.PasswordBasedDecrypter;
import com.nimbusds.jose.crypto.PasswordBasedEncrypter;
import com.nimbusds.jose.crypto.RSADecrypter;
import com.nimbusds.jose.crypto.RSAEncrypter;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jose.util.JSONObjectUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds compact JWE objects for the JOSE module: every key-management
 * algorithm the module offers, the optional protected-header parameters and
 * explicit secret encodings. It holds no JavaFX state so it can be unit tested.
 */
public final class JweComposer {

    /** Key-management algorithms offered for encryption, in UI order. */
    public static final List<String> KEY_ALGORITHMS = List.of(
            "RSA-OAEP-256", "RSA-OAEP-384", "RSA-OAEP-512",
            "ECDH-ES", "ECDH-ES+A128KW", "ECDH-ES+A192KW", "ECDH-ES+A256KW",
            "A128KW", "A192KW", "A256KW",
            "A128GCMKW", "A192GCMKW", "A256GCMKW",
            "PBES2-HS256+A128KW", "PBES2-HS384+A192KW", "PBES2-HS512+A256KW",
            "dir");

    /** Content-encryption algorithms (RFC 7518 §5.1), in UI order. */
    public static final List<String> CONTENT_ALGORITHMS = List.of(
            "A128GCM", "A192GCM", "A256GCM", "A128CBC-HS256", "A192CBC-HS384", "A256CBC-HS512");

    /** OWASP 2023 guidance for PBKDF2-HMAC-SHA256. */
    public static final int DEFAULT_PBES2_ITERATIONS = 600_000;
    public static final int PBES2_SALT_LENGTH = 16;

    /** Header names the composer owns; custom JSON may not override them. */
    private static final Set<String> RESERVED_HEADERS = Set.of("alg", "enc", "zip", "epk", "iv", "tag", "p2s", "p2c");

    /** Output form of the JWE (RFC 7516 §7). */
    public enum Serialization {
        COMPACT("Compact"), FLATTENED("Flattened JSON"), GENERAL("General JSON");

        private final String label;

        Serialization(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public static Serialization fromLabel(String label) {
            for (Serialization serialization : values()) {
                if (serialization.label.equals(label)) return serialization;
            }
            return COMPACT;
        }
    }

    /** Result of decrypting a JSON-serialized JWE. */
    public record JsonDecryption(String payload, Map<String, Object> effectiveHeader, int recipientIndex,
            int recipientCount, String aad, Base64URL encryptedKey, Base64URL iv, Base64URL cipherText,
            Base64URL authTag) {
    }

    /** Optional protected-header parameters; blank strings are omitted. */
    public record HeaderOptions(String kid, String typ, String cty, String apu, String apv, String customJson) {
        public static HeaderOptions none() {
            return new HeaderOptions(null, null, null, null, null, null);
        }
    }

    private JweComposer() {
    }

    public static String encrypt(String payload, String keyAlgorithm, String contentAlgorithm, boolean compress,
            HeaderOptions options, String keyInput, SecretEncoding secretEncoding, int pbes2Iterations)
            throws Exception {
        return encrypt(payload, keyAlgorithm, contentAlgorithm, compress, options, keyInput, secretEncoding,
                pbes2Iterations, Serialization.COMPACT, null);
    }

    /**
     * Encrypts in any serialization. {@code aad} (UTF-8 text) needs a JSON
     * serialization. For General JSON, a JWKS with several keys produces one
     * recipient per key; each key then names its own {@code alg} and the
     * key-management field is not used.
     */
    public static String encrypt(String payload, String keyAlgorithm, String contentAlgorithm, boolean compress,
            HeaderOptions options, String keyInput, SecretEncoding secretEncoding, int pbes2Iterations,
            Serialization serialization, String aad) throws Exception {
        HeaderOptions headerOptions = options == null ? HeaderOptions.none() : options;
        Serialization form = serialization == null ? Serialization.COMPACT : serialization;
        EncryptionMethod enc = EncryptionMethod.parse(contentAlgorithm);
        if (form == Serialization.COMPACT && present(aad)) {
            throw new IllegalArgumentException("AAD needs a JSON serialization; compact JWE has no 'aad' member.");
        }

        List<JWK> recipients = form == Serialization.GENERAL ? multiRecipientKeys(keyInput) : List.of();
        if (!recipients.isEmpty()) {
            if (present(headerOptions.kid()) || present(headerOptions.apu()) || present(headerOptions.apv())) {
                throw new IllegalArgumentException("kid, apu and apv are per recipient; with several recipients they come from each JWK.");
            }
            JWEObjectJSON object = new JWEObjectJSON(header(null, enc, compress, headerOptions),
                    new Payload(payload), null, aadMember(aad));
            object.encrypt(new MultiEncrypter(new JWKSet(recipients)));
            return object.serializeGeneral();
        }

        JWEAlgorithm alg = JWEAlgorithm.parse(keyAlgorithm);
        JWEHeader header = header(alg, enc, compress, headerOptions);
        JWEEncrypter encrypter = encrypter(alg, keyInput, secretEncoding, pbes2Iterations);
        if (form == Serialization.COMPACT) {
            JWEObject object = new JWEObject(header, new Payload(payload));
            object.encrypt(encrypter);
            return object.serialize();
        }
        JWEObjectJSON object = new JWEObjectJSON(header, new Payload(payload), null, aadMember(aad));
        object.encrypt(encrypter);
        return form == Serialization.FLATTENED ? object.serializeFlattened() : object.serializeGeneral();
    }

    /**
     * Nimbus takes the JSON {@code aad} member text, not the raw AAD, and
     * authenticates {@code ASCII(protected || '.' || aad)} as RFC 7516 §5.1
     * step 14 requires.
     */
    private static byte[] aadMember(String aad) {
        return present(aad) ? Base64URL.encode(aad).toString().getBytes(StandardCharsets.US_ASCII) : null;
    }

    /** Keys of a JWKS with more than one entry; empty for any other key input. */
    private static List<JWK> multiRecipientKeys(String keyInput) throws Exception {
        String text = keyInput == null ? "" : keyInput.trim();
        if (!text.startsWith("{") || !text.contains("\"keys\"")) return List.of();
        List<JWK> keys = JWKSet.parse(text).getKeys();
        if (keys.size() < 2) return List.of();
        for (JWK key : keys) {
            if (key.getAlgorithm() == null) {
                throw new IllegalArgumentException("Each recipient JWK needs an 'alg' member (key "
                        + (key.getKeyID() == null ? "without kid" : key.getKeyID()) + ").");
            }
        }
        return keys;
    }

    static JWEHeader header(JWEAlgorithm alg, EncryptionMethod enc, boolean compress, HeaderOptions options)
            throws Exception {
        JWEHeader.Builder builder = alg == null ? new JWEHeader.Builder(enc) : new JWEHeader.Builder(alg, enc);
        if (compress) builder.compressionAlgorithm(CompressionAlgorithm.DEF);
        if (present(options.kid())) builder.keyID(options.kid().trim());
        if (present(options.typ())) builder.type(new JOSEObjectType(options.typ().trim()));
        if (present(options.cty())) builder.contentType(options.cty().trim());
        if (present(options.apu())) builder.agreementPartyUInfo(Base64URL.encode(options.apu().trim()));
        if (present(options.apv())) builder.agreementPartyVInfo(Base64URL.encode(options.apv().trim()));
        if (!present(options.customJson())) return builder.build();

        Map<String, Object> custom;
        try {
            custom = JSONObjectUtils.parse(options.customJson());
        } catch (java.text.ParseException e) {
            throw new IllegalArgumentException("Custom header parameters must be a JSON object: " + e.getMessage(), e);
        }
        for (String name : custom.keySet()) {
            if (RESERVED_HEADERS.contains(name)) {
                throw new IllegalArgumentException("Custom header parameters cannot set '" + name
                        + "'; it is chosen by the algorithm fields.");
            }
        }
        Map<String, Object> merged = builder.build().toJSONObject();
        merged.putAll(custom);
        return JWEHeader.parse(merged);
    }

    public static JWEEncrypter encrypter(JWEAlgorithm alg, String keyInput, SecretEncoding secretEncoding,
            int pbes2Iterations) throws Exception {
        if (JWEAlgorithm.Family.RSA.contains(alg)) {
            requireStrongRsa(alg);
            return new RSAEncrypter(JoseKeyMaterial.rsaPublicKey(keyInput));
        }
        if (JWEAlgorithm.Family.ECDH_ES.contains(alg)) {
            return new ECDHEncrypter(JoseKeyMaterial.ecPublicKey(keyInput));
        }
        if (JWEAlgorithm.Family.AES_KW.contains(alg) || JWEAlgorithm.Family.AES_GCM_KW.contains(alg)) {
            return new AESEncrypter(requireAesKey(alg, JoseKeyMaterial.secret(keyInput, secretEncoding)));
        }
        if (JWEAlgorithm.Family.PBES2.contains(alg)) {
            return new PasswordBasedEncrypter(JoseKeyMaterial.secret(keyInput, secretEncoding),
                    PBES2_SALT_LENGTH, pbes2Iterations);
        }
        if (JWEAlgorithm.DIR.equals(alg)) {
            return new DirectEncrypter(JoseKeyMaterial.secret(keyInput, secretEncoding));
        }
        throw new IllegalArgumentException("Unsupported JWE key-management algorithm: " + alg.getName());
    }

    /**
     * Resolves a decrypter; {@code loaded} receives the parsed private key or
     * secret so the caller can preview the CEK without parsing twice.
     */
    public static JWEDecrypter decrypter(JWEAlgorithm alg, String keyInput, SecretEncoding secretEncoding,
            LoadedKey loaded) throws Exception {
        if (JWEAlgorithm.Family.RSA.contains(alg)) {
            loaded.privateKey = JoseKeyMaterial.rsaPrivateKey(keyInput);
            return new RSADecrypter(loaded.privateKey);
        }
        if (JWEAlgorithm.Family.ECDH_ES.contains(alg)) {
            java.security.interfaces.ECPrivateKey key = JoseKeyMaterial.ecPrivateKey(keyInput);
            loaded.privateKey = key;
            return new ECDHDecrypter(key);
        }
        loaded.secret = JoseKeyMaterial.secret(keyInput, secretEncoding);
        if (JWEAlgorithm.Family.AES_KW.contains(alg) || JWEAlgorithm.Family.AES_GCM_KW.contains(alg)) {
            return new AESDecrypter(requireAesKey(alg, loaded.secret));
        }
        if (JWEAlgorithm.Family.PBES2.contains(alg)) return new PasswordBasedDecrypter(loaded.secret);
        if (JWEAlgorithm.DIR.equals(alg)) return new DirectDecrypter(loaded.secret);
        throw new IllegalArgumentException("Unsupported JWE key-management algorithm: " + alg.getName());
    }

    /**
     * Decrypts a flattened or general JSON JWE with one key: each recipient is
     * tried in turn, so a PEM key works without matching {@code kid}s.
     */
    public static JsonDecryption decryptJson(String json, String keyInput, SecretEncoding secretEncoding)
            throws Exception {
        Map<String, Object> jwe;
        try {
            jwe = JSONObjectUtils.parse(json);
        } catch (java.text.ParseException e) {
            throw new IllegalArgumentException("The JWE JSON serialization is not valid JSON.", e);
        }
        String protectedB64 = JSONObjectUtils.getString(jwe, "protected");
        Map<String, Object> protectedHeader = protectedB64 == null ? Map.of()
                : JSONObjectUtils.parse(new Base64URL(protectedB64).decodeToString());
        Map<String, Object> shared = jwe.get("unprotected") == null ? Map.of()
                : JSONObjectUtils.getJSONObject(jwe, "unprotected");
        String aadB64 = JSONObjectUtils.getString(jwe, "aad");
        Base64URL iv = JSONObjectUtils.getBase64URL(jwe, "iv");
        Base64URL cipherText = JSONObjectUtils.getBase64URL(jwe, "ciphertext");
        Base64URL tag = JSONObjectUtils.getBase64URL(jwe, "tag");
        if (cipherText == null) throw new IllegalArgumentException("The JWE JSON has no 'ciphertext' member.");

        List<Map<String, Object>> recipients = new java.util.ArrayList<>();
        if (jwe.containsKey("recipients")) {
            for (Map<String, Object> recipient : JSONObjectUtils.getJSONObjectArray(jwe, "recipients")) {
                recipients.add(recipient);
            }
        } else {
            Map<String, Object> flattened = new java.util.HashMap<>();
            if (jwe.get("header") != null) flattened.put("header", jwe.get("header"));
            if (jwe.get("encrypted_key") != null) flattened.put("encrypted_key", jwe.get("encrypted_key"));
            recipients.add(flattened);
        }
        if (recipients.isEmpty()) throw new IllegalArgumentException("The JWE JSON lists no recipients.");

        byte[] aad = ((protectedB64 == null ? "" : protectedB64) + (aadB64 == null ? "" : "." + aadB64))
                .getBytes(StandardCharsets.US_ASCII);
        String firstFailure = null;
        for (int i = 0; i < recipients.size(); i++) {
            Map<String, Object> recipient = recipients.get(i);
            Map<String, Object> perRecipient = recipient.get("header") == null ? Map.of()
                    : JSONObjectUtils.getJSONObject(recipient, "header");
            Map<String, Object> merged = new java.util.LinkedHashMap<>(protectedHeader);
            for (Map<String, Object> part : List.of(shared, perRecipient)) {
                for (Map.Entry<String, Object> entry : part.entrySet()) {
                    if (merged.put(entry.getKey(), entry.getValue()) != null) {
                        throw new IllegalArgumentException("Header parameter '" + entry.getKey()
                                + "' appears in more than one JWE header (RFC 7516 §7.2.1).");
                    }
                }
            }
            JWEHeader header = JWEHeader.parse(merged);
            Base64URL encryptedKey = JSONObjectUtils.getBase64URL(recipient, "encrypted_key");
            try {
                JWEDecrypter decrypter = decrypter(header.getAlgorithm(), keyInput, secretEncoding, new LoadedKey());
                byte[] plaintext = decrypter.decrypt(header, encryptedKey, iv, cipherText, tag, aad);
                return new JsonDecryption(new String(plaintext, StandardCharsets.UTF_8), merged, i,
                        recipients.size(), aadB64 == null ? null : new Base64URL(aadB64).decodeToString(),
                        encryptedKey, iv, cipherText, tag);
            } catch (Exception e) {
                if (firstFailure == null) firstFailure = header.getAlgorithm().getName() + ": " + e.getMessage();
            }
        }
        throw new IllegalArgumentException(recipients.size() == 1
                ? "JWE decryption failed (" + firstFailure + ")."
                : "None of the " + recipients.size() + " recipients could be decrypted with the supplied key.");
    }

    /** Key material resolved while building a decrypter. */
    public static final class LoadedKey {
        public java.security.PrivateKey privateKey;
        public byte[] secret;
    }

    private static void requireStrongRsa(JWEAlgorithm alg) {
        if (JWEAlgorithm.RSA1_5.equals(alg) || JWEAlgorithm.RSA_OAEP.equals(alg)) {
            throw new IllegalArgumentException(alg.getName() + " is disabled for encryption; use RSA-OAEP-256 or stronger.");
        }
    }

    private static byte[] requireAesKey(JWEAlgorithm alg, byte[] key) {
        int expected = alg.getName().contains("128") ? 16 : alg.getName().contains("192") ? 24 : 32;
        if (key.length != expected) {
            throw new IllegalArgumentException(alg.getName() + " needs a " + (expected * 8) + "-bit key ("
                    + expected + " bytes); the supplied secret has " + key.length
                    + " bytes. Check the key format (UTF-8 / Hex / Base64).");
        }
        return key;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
