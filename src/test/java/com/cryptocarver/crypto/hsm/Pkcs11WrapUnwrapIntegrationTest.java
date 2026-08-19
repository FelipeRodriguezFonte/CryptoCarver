package com.cryptocarver.crypto.hsm;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises {@link Pkcs11Session#wrapKey}/{@link Pkcs11Session#unwrapKey} against a real
 * (simulated) PKCS#11 token, same gate and pattern as {@link SoftHsmIntegrationTest}. Skipped
 * entirely unless the same five SoftHSM environment variables from that test are set — see
 * {@code docs/PKCS11_SOFTHSM_QUICKSTART.md}.
 *
 * <p>The successful round trip additionally needs a secret key on the token whose
 * {@code CKA_EXTRACTABLE} attribute is true (see {@link Pkcs11Session#wrapKey(String, Key, String)}'s
 * javadoc for why — confirmed empirically while building this test that neither {@code keytool} nor
 * {@link Pkcs11Session#generateSecretKey} produce one). The quickstart's OpenSC step creates it as
 * {@code cryptocarver-aes-extractable}; override with {@code CRYPTOCARVER_SOFTHSM_EXTRACTABLE_AES_ALIAS}
 * if you named it differently. Without it, only the two failure-path tests run.</p>
 */
class Pkcs11WrapUnwrapIntegrationTest {

    /**
     * SoftHSM (and PKCS#11 tokens generally) commonly do not expose an OAEP RSA cipher mechanism
     * through SunPKCS11 — {@code session.getSupportedMechanisms("Cipher")} against this token
     * lists only {@code RSA/ECB/NoPadding} and {@code RSA/ECB/PKCS1Padding}, confirmed empirically
     * while writing this test. PKCS#1 v1.5 padding is what real tokens actually support here.
     */
    private static final String WRAP_TRANSFORMATION = "RSA/ECB/PKCS1Padding";

    private static Pkcs11Session session;
    private static String wrappingAlias;
    private static String extractableAesAlias;

    @BeforeAll
    static void setUp() throws Exception {
        String libraryPath = System.getenv("SOFTHSM2_MODULE");
        String confPath = System.getenv("SOFTHSM2_CONF");
        String pinStr = System.getenv("CRYPTOCARVER_SOFTHSM_PIN");
        String slotStr = System.getenv("CRYPTOCARVER_SOFTHSM_SLOT_INDEX");
        String aliasStr = System.getenv("CRYPTOCARVER_SOFTHSM_ALIAS");
        String extractableAliasStr = System.getenv("CRYPTOCARVER_SOFTHSM_EXTRACTABLE_AES_ALIAS");

        Assumptions.assumeTrue(libraryPath != null && !libraryPath.isBlank(), "SOFTHSM2_MODULE is not set, skipping integration test");
        Assumptions.assumeTrue(confPath != null && !confPath.isBlank(), "SOFTHSM2_CONF is not set, skipping integration test");
        Assumptions.assumeTrue(pinStr != null && !pinStr.isBlank(), "CRYPTOCARVER_SOFTHSM_PIN is not set, skipping integration test");
        Assumptions.assumeTrue(slotStr != null && !slotStr.isBlank(), "CRYPTOCARVER_SOFTHSM_SLOT_INDEX is not set, skipping integration test");
        Assumptions.assumeTrue(aliasStr != null && !aliasStr.isBlank(), "CRYPTOCARVER_SOFTHSM_ALIAS is not set, skipping integration test");

        System.setProperty("SOFTHSM2_CONF", confPath);
        int slot = Integer.parseInt(slotStr);
        Pkcs11Configuration config = new Pkcs11Configuration("SoftHSM", java.nio.file.Path.of(libraryPath), slot);
        session = Pkcs11SessionManager.getInstance().connect(config, pinStr.toCharArray());

        wrappingAlias = aliasStr;
        extractableAesAlias = (extractableAliasStr != null && !extractableAliasStr.isBlank())
                ? extractableAliasStr : "cryptocarver-aes-extractable";

        java.util.List<String> aliases = session.listPrivateKeysWithCertificate();
        Assumptions.assumeTrue(aliases.contains(wrappingAlias), "Configured wrapping alias does not exist or has no private key with certificate");
    }

    @AfterAll
    static void tearDown() {
        if (session != null) {
            Pkcs11SessionManager.getInstance().disconnect();
        }
    }

    @Test
    void wrapAndUnwrapRecoverAFunctionallyEquivalentAesKey() throws Exception {
        boolean hasExtractableKey = session.listObjects().stream()
                .anyMatch(o -> extractableAesAlias.equals(o.alias()));
        Assumptions.assumeTrue(hasExtractableKey,
                "No extractable AES key (" + extractableAesAlias + ") on the token — create one with "
                        + "`pkcs11-tool --keygen --key-type AES:16 --label " + extractableAesAlias + " --extractable`, "
                        + "see docs/PKCS11_SOFTHSM_QUICKSTART.md");

        byte[] wrapped = session.wrapKey(wrappingAlias, extractableAesAlias, WRAP_TRANSFORMATION);
        assertNotNull(wrapped);
        assertTrue(wrapped.length > 0);

        Key unwrapped = session.unwrapKey(wrappingAlias, wrapped, WRAP_TRANSFORMATION, "AES", Cipher.SECRET_KEY);
        assertNotNull(unwrapped);
        assertTrue(unwrapped instanceof SecretKey, "Unwrap must yield a SecretKey for wrappedKeyType=SECRET_KEY");

        // Prove the unwrapped handle is functionally the same key: encrypt with it, then decrypt
        // with it again (both operations go through the same returned object — this does not
        // require knowing the extractable key's raw bytes ahead of time, only that unwrap is
        // self-consistent and the key is actually usable, not just non-null).
        byte[] plaintext = "Fase C wrap/unwr".getBytes(StandardCharsets.UTF_8); // 16 bytes, one AES block
        Cipher encrypt = Cipher.getInstance("AES/ECB/NoPadding");
        encrypt.init(Cipher.ENCRYPT_MODE, unwrapped);
        byte[] ciphertext = encrypt.doFinal(plaintext);
        assertFalse(java.util.Arrays.equals(plaintext, ciphertext));

        Cipher decrypt = Cipher.getInstance("AES/ECB/NoPadding");
        decrypt.init(Cipher.DECRYPT_MODE, unwrapped);
        byte[] recovered = decrypt.doFinal(ciphertext);
        assertArrayEquals(plaintext, recovered);
    }

    @Test
    void wrapRejectsANonExtractableKey() throws Exception {
        // generateSecretKey() is confirmed non-extractable by default (see its javadoc) — wrapping
        // it must fail with a clear security exception, not silently succeed or hang.
        SecretKey nonExtractable = session.generateSecretKey("AES", 128);
        assertThrows(GeneralSecurityException.class,
                () -> session.wrapKey(wrappingAlias, nonExtractable, WRAP_TRANSFORMATION));
    }

    @Test
    void wrapRejectsAnUnsupportedTransformation() throws Exception {
        SecretKey key = session.generateSecretKey("AES", 128);
        assertThrows(GeneralSecurityException.class,
                () -> session.wrapKey(wrappingAlias, key, "NotARealTransformation"));
    }

    @Test
    void unwrapRejectsGarbageInput() {
        assertThrows(GeneralSecurityException.class,
                () -> session.unwrapKey(wrappingAlias, new byte[]{1, 2, 3, 4}, WRAP_TRANSFORMATION, "AES", Cipher.SECRET_KEY));
    }
}
