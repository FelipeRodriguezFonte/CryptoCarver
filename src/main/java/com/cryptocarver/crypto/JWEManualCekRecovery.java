package com.cryptocarver.crypto;

import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.ECDHDecrypter;
import com.nimbusds.jose.crypto.PasswordBasedDecrypter;
import com.nimbusds.jose.crypto.impl.AESKW;
import com.nimbusds.jose.crypto.impl.ECDH;
import com.nimbusds.jose.crypto.impl.ConcatKDF;
import com.nimbusds.jose.crypto.impl.PBKDF2;
import com.nimbusds.jose.crypto.impl.PRFParams;
import com.nimbusds.jose.crypto.impl.RSA1_5;
import com.nimbusds.jose.crypto.impl.RSA_OAEP;
import com.nimbusds.jose.crypto.impl.RSA_OAEP_SHA2;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.util.Base64URL;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.PrivateKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Explicit, local-only recovery of a JWE content-encryption key (CEK).
 *
 * <p>This helper intentionally returns the CEK only to its caller. It does not
 * log, publish, persist, or add the value to any operation result.</p>
 */
public final class JWEManualCekRecovery {

    private static final Set<JWEAlgorithm> SUPPORTED_ALGORITHMS;

    static {
        Set<JWEAlgorithm> algorithms = new LinkedHashSet<>();
        algorithms.add(JWEAlgorithm.RSA1_5);
        algorithms.add(JWEAlgorithm.RSA_OAEP);
        algorithms.add(JWEAlgorithm.RSA_OAEP_256);
        algorithms.add(JWEAlgorithm.RSA_OAEP_384);
        algorithms.add(JWEAlgorithm.RSA_OAEP_512);
        algorithms.add(JWEAlgorithm.A128KW);
        algorithms.add(JWEAlgorithm.A192KW);
        algorithms.add(JWEAlgorithm.A256KW);
        algorithms.add(JWEAlgorithm.DIR);
        algorithms.add(JWEAlgorithm.ECDH_ES);
        algorithms.add(JWEAlgorithm.ECDH_ES_A128KW);
        algorithms.add(JWEAlgorithm.ECDH_ES_A192KW);
        algorithms.add(JWEAlgorithm.ECDH_ES_A256KW);
        algorithms.add(JWEAlgorithm.PBES2_HS256_A128KW);
        algorithms.add(JWEAlgorithm.PBES2_HS384_A192KW);
        algorithms.add(JWEAlgorithm.PBES2_HS512_A256KW);
        SUPPORTED_ALGORITHMS = Collections.unmodifiableSet(algorithms);
    }

    private JWEManualCekRecovery() {
    }

    /**
     * Returns the locally recoverable JWE key-management algorithms.
     * The set is deliberately narrower than Nimbus' complete JWE catalogue.
     */
    public static Set<JWEAlgorithm> supportedAlgorithms() {
        return SUPPORTED_ALGORITHMS;
    }

    public static boolean isSupported(JWEAlgorithm algorithm) {
        return algorithm != null && SUPPORTED_ALGORITHMS.contains(algorithm);
    }

    /**
     * Recovers a CEK for an already parsed JWE using already loaded key
     * material. The private-key argument is used by RSA and ECDH; the secret
     * argument is used by AES-KW and PBES2.
     *
     * <p>{@code dir} is intentionally excluded: it has no wrapped CEK and the
     * direct key must not be returned by automatic preview.</p>
     */
    public static byte[] recover(JWEObject jweObject, PrivateKey privateKey, byte[] secret)
            throws ManualCekRecoveryException {
        if (jweObject == null || jweObject.getHeader() == null) {
            throw new ManualCekRecoveryException("The JWE is missing a valid protected header.");
        }

        JWEHeader header = jweObject.getHeader();
        JWEAlgorithm algorithm = header.getAlgorithm();
        if (!isSupported(algorithm)) {
            throw new ManualCekRecoveryException(
                    "Manual CEK preview does not support JWE key-management algorithm "
                            + safeAlgorithmName(algorithm) + ".");
        }
        if (JWEAlgorithm.DIR.equals(algorithm)) {
            throw new ManualCekRecoveryException(
                    "Direct JWE has no wrapped CEK; the CEK is the direct key and is not returned automatically.");
        }

        try {
            if (JWEAlgorithm.Family.RSA.contains(algorithm)) {
                return recoverRsa(header, jweObject.getEncryptedKey(), privateKey);
            }
            if (JWEAlgorithm.Family.AES_KW.contains(algorithm)) {
                return recoverAesKw(header, jweObject.getEncryptedKey(), secret);
            }
            if (JWEAlgorithm.Family.ECDH_ES.contains(algorithm)) {
                return recoverEcdh(header, jweObject.getEncryptedKey(), privateKey);
            }
            if (JWEAlgorithm.Family.PBES2.contains(algorithm)) {
                return recoverPbes2(header, jweObject.getEncryptedKey(), secret);
            }
        } catch (ManualCekRecoveryException e) {
            throw e;
        } catch (Exception e) {
            throw new ManualCekRecoveryException(
                    "Unable to recover the CEK for " + algorithm.getName()
                            + ": supplied key material is incompatible or the JWE key segment is invalid.", e);
        }

        // This is intentionally unreachable while SUPPORTED_ALGORITHMS and the
        // explicit family branches above are kept in sync.
        throw new ManualCekRecoveryException(
                "Manual CEK preview does not support JWE key-management algorithm " + algorithm.getName() + ".");
    }

    private static byte[] recoverRsa(JWEHeader header, Base64URL encryptedKey, PrivateKey privateKey)
            throws ManualCekRecoveryException, JOSEException {
        JWEAlgorithm algorithm = header.getAlgorithm();
        requireEncryptedKey(algorithm, encryptedKey);
        if (!(privateKey instanceof RSAPrivateKey)) {
            if (privateKey == null) {
                throw new ManualCekRecoveryException("A private RSA key is required for JWE algorithm " + algorithm.getName() + ".");
            }
            throw new ManualCekRecoveryException("The supplied private key is incompatible with JWE algorithm " + algorithm.getName() + ".");
        }

        SecretKey cek;
        if (JWEAlgorithm.RSA1_5.equals(algorithm)) {
            cek = RSA1_5.decryptCEK(privateKey, encryptedKey.decode(), header.getEncryptionMethod().cekBitLength(), null);
        } else if (JWEAlgorithm.RSA_OAEP.equals(algorithm)) {
            cek = RSA_OAEP.decryptCEK(privateKey, encryptedKey.decode(), null);
        } else if (JWEAlgorithm.RSA_OAEP_256.equals(algorithm)) {
            cek = RSA_OAEP_SHA2.decryptCEK(privateKey, encryptedKey.decode(), 256, null);
        } else if (JWEAlgorithm.RSA_OAEP_384.equals(algorithm)) {
            cek = RSA_OAEP_SHA2.decryptCEK(privateKey, encryptedKey.decode(), 384, null);
        } else if (JWEAlgorithm.RSA_OAEP_512.equals(algorithm)) {
            cek = RSA_OAEP_SHA2.decryptCEK(privateKey, encryptedKey.decode(), 512, null);
        } else {
            throw new ManualCekRecoveryException("Manual CEK preview does not support JWE key-management algorithm " + algorithm.getName() + ".");
        }
        if (cek == null || cek.getEncoded() == null) {
            throw new ManualCekRecoveryException("The RSA encrypted key is invalid for JWE algorithm " + algorithm.getName() + ".");
        }
        return cek.getEncoded().clone();
    }

    private static byte[] recoverAesKw(JWEHeader header, Base64URL encryptedKey, byte[] secret)
            throws ManualCekRecoveryException, JOSEException {
        requireEncryptedKey(header.getAlgorithm(), encryptedKey);
        SecretKey kek = requireAesSecret(header.getAlgorithm(), secret);
        SecretKey cek = AESKW.unwrapCEK(kek, encryptedKey.decode(), null);
        if (cek == null || cek.getEncoded() == null) {
            throw new ManualCekRecoveryException("The AES-KW encrypted key is invalid for JWE algorithm " + header.getAlgorithm().getName() + ".");
        }
        return cek.getEncoded().clone();
    }

    private static byte[] recoverEcdh(JWEHeader header, Base64URL encryptedKey, PrivateKey privateKey)
            throws Exception {
        if (!(privateKey instanceof ECPrivateKey ecPrivateKey)) {
            if (privateKey == null) {
                throw new ManualCekRecoveryException("A private EC key is required for JWE algorithm " + header.getAlgorithm().getName() + ".");
            }
            throw new ManualCekRecoveryException("The supplied private key is incompatible with JWE algorithm " + header.getAlgorithm().getName() + ".");
        }

        JWK ephemeral = header.getEphemeralPublicKey();
        if (!(ephemeral instanceof ECKey ecKey)) {
            throw new ManualCekRecoveryException("The JWE header is missing a valid EC ephemeral public key (epk).");
        }
        ECPublicKey ephemeralPublicKey = ecKey.toECPublicKey();
        if (!ECDHDecrypter.SUPPORTED_ELLIPTIC_CURVES.contains(ecKey.getCurve())) {
            throw new ManualCekRecoveryException("The JWE epk curve is not supported for local ECDH CEK recovery.");
        }
        if (!ecKey.getCurve().equals(com.nimbusds.jose.jwk.Curve.forECParameterSpec(ecPrivateKey.getParams()))) {
            throw new ManualCekRecoveryException("The supplied EC private key is incompatible with the JWE epk curve.");
        }

        SecretKey sharedSecret = ECDH.deriveSharedSecret(ephemeralPublicKey, ecPrivateKey, null);
        SecretKey sharedKey = ECDH.deriveSharedKey(header, sharedSecret, new ConcatKDF("SHA-256"));
        if (JWEAlgorithm.ECDH_ES.equals(header.getAlgorithm())) {
            return requireKeyBytes(sharedKey, "ECDH-ES derived key");
        }

        requireEncryptedKey(header.getAlgorithm(), encryptedKey);
        SecretKey cek = AESKW.unwrapCEK(sharedKey, encryptedKey.decode(), null);
        return requireKeyBytes(cek, "ECDH-ES wrapped CEK");
    }

    private static byte[] recoverPbes2(JWEHeader header, Base64URL encryptedKey, byte[] password)
            throws Exception {
        requireEncryptedKey(header.getAlgorithm(), encryptedKey);
        if (password == null || password.length == 0) {
            throw new ManualCekRecoveryException("A password/secret is required for JWE algorithm " + header.getAlgorithm().getName() + ".");
        }
        if (header.getPBES2Salt() == null) {
            throw new ManualCekRecoveryException("The JWE header is missing the PBES2 salt parameter p2s.");
        }
        int iterationCount = header.getPBES2Count();
        if (iterationCount < 1 || iterationCount > PasswordBasedDecrypter.MAX_ALLOWED_ITERATION_COUNT) {
            throw new ManualCekRecoveryException("The JWE header contains an invalid PBES2 iteration count p2c.");
        }

        JWEAlgorithm algorithm = header.getAlgorithm();
        byte[] formattedSalt = PBKDF2.formatSalt(algorithm, header.getPBES2Salt().decode());
        PRFParams prf = PRFParams.resolve(algorithm, null);
        SecretKey kek = PBKDF2.deriveKey(password, formattedSalt, iterationCount, prf);
        SecretKey cek = AESKW.unwrapCEK(kek, encryptedKey.decode(), null);
        return requireKeyBytes(cek, "PBES2 wrapped CEK");
    }

    private static SecretKey requireAesSecret(JWEAlgorithm algorithm, byte[] secret)
            throws ManualCekRecoveryException {
        if (secret == null || secret.length == 0) {
            throw new ManualCekRecoveryException("A symmetric key is required for JWE algorithm " + algorithm.getName() + ".");
        }
        int expectedLength = algorithm.equals(JWEAlgorithm.A128KW) ? 16
                : algorithm.equals(JWEAlgorithm.A192KW) ? 24
                : 32;
        if (secret.length != expectedLength) {
            throw new ManualCekRecoveryException("The supplied symmetric key is incompatible with JWE algorithm " + algorithm.getName() + ".");
        }
        return new SecretKeySpec(secret.clone(), "AES");
    }

    private static byte[] requireKeyBytes(SecretKey key, String description)
            throws ManualCekRecoveryException {
        if (key == null || key.getEncoded() == null) {
            throw new ManualCekRecoveryException(description + " is unavailable.");
        }
        return key.getEncoded().clone();
    }

    private static void requireEncryptedKey(JWEAlgorithm algorithm, Base64URL encryptedKey)
            throws ManualCekRecoveryException {
        if (encryptedKey == null) {
            throw new ManualCekRecoveryException("The JWE encrypted key segment is missing for " + algorithm.getName() + ".");
        }
    }

    private static String safeAlgorithmName(JWEAlgorithm algorithm) {
        return algorithm == null ? "(missing)" : algorithm.getName();
    }

    public static final class ManualCekRecoveryException extends Exception {
        public ManualCekRecoveryException(String message) {
            super(message);
        }

        private ManualCekRecoveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
