package com.cryptocarver.crypto.hsm;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** PKCS#11 v2.x public constants used by the read-only inventory. */
final class Pkcs11NativeConstants {
    private Pkcs11NativeConstants() { }

    static final long CKR_OK = 0x00000000L;
    static final long CKR_BUFFER_TOO_SMALL = 0x00000150L;
    static final long CKR_CRYPTOKI_NOT_INITIALIZED = 0x00000190L;
    static final long CKR_CRYPTOKI_ALREADY_INITIALIZED = 0x00000191L;

    // CK_TOKEN_INFO flags.
    static final long CKF_RNG = 0x00000001L;
    static final long CKF_WRITE_PROTECTED = 0x00000002L;
    static final long CKF_LOGIN_REQUIRED = 0x00000004L;
    static final long CKF_USER_PIN_INITIALIZED = 0x00000008L;
    static final long CKF_RESTORE_KEY_NOT_NEEDED = 0x00000020L;
    static final long CKF_CLOCK_ON_TOKEN = 0x00000040L;
    static final long CKF_PROTECTED_AUTHENTICATION_PATH = 0x00000100L;
    static final long CKF_DUAL_CRYPTO_OPERATIONS = 0x00000200L;
    static final long CKF_TOKEN_INITIALIZED = 0x00000400L;
    static final long CKF_SECONDARY_AUTHENTICATION = 0x00000800L;
    static final long CKF_USER_PIN_COUNT_LOW = 0x00010000L;
    static final long CKF_USER_PIN_FINAL_TRY = 0x00020000L;
    static final long CKF_USER_PIN_LOCKED = 0x00040000L;
    static final long CKF_USER_PIN_TO_BE_CHANGED = 0x00080000L;
    static final long CKF_USER_PIN_EXPIRED = 0x00100000L;

    // CK_MECHANISM_INFO flags.
    static final long CKF_HW = 0x00000001L;
    static final long CKF_ENCRYPT = 0x00000100L;
    static final long CKF_DECRYPT = 0x00000200L;
    static final long CKF_DIGEST = 0x00000400L;
    static final long CKF_SIGN = 0x00000800L;
    static final long CKF_SIGN_RECOVER = 0x00001000L;
    static final long CKF_VERIFY = 0x00002000L;
    static final long CKF_VERIFY_RECOVER = 0x00004000L;
    static final long CKF_GENERATE = 0x00008000L;
    static final long CKF_GENERATE_KEY_PAIR = 0x00010000L;
    static final long CKF_WRAP = 0x00020000L;
    static final long CKF_UNWRAP = 0x00040000L;
    static final long CKF_DERIVE = 0x00080000L;
    static final long CKF_EXTENSION = 0x80000000L;
    static final long CKF_EC_F_P = 0x00100000L;
    static final long CKF_EC_NAMEDCURVE = 0x00800000L;
    static final long CKF_EC_UNCOMPRESS = 0x01000000L;
    static final long CKF_EC_COMPRESS = 0x02000000L;

    static Set<String> tokenFlags(long flags) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        add(result, flags, CKF_RNG, "RNG");
        add(result, flags, CKF_WRITE_PROTECTED, "WRITE_PROTECTED");
        add(result, flags, CKF_LOGIN_REQUIRED, "LOGIN_REQUIRED");
        add(result, flags, CKF_USER_PIN_INITIALIZED, "USER_PIN_INITIALIZED");
        add(result, flags, CKF_RESTORE_KEY_NOT_NEEDED, "RESTORE_KEY_NOT_NEEDED");
        add(result, flags, CKF_CLOCK_ON_TOKEN, "CLOCK_ON_TOKEN");
        add(result, flags, CKF_PROTECTED_AUTHENTICATION_PATH, "PROTECTED_AUTHENTICATION_PATH");
        add(result, flags, CKF_DUAL_CRYPTO_OPERATIONS, "DUAL_CRYPTO_OPERATIONS");
        add(result, flags, CKF_TOKEN_INITIALIZED, "TOKEN_INITIALIZED");
        add(result, flags, CKF_SECONDARY_AUTHENTICATION, "SECONDARY_AUTHENTICATION");
        add(result, flags, CKF_USER_PIN_COUNT_LOW, "USER_PIN_COUNT_LOW");
        add(result, flags, CKF_USER_PIN_FINAL_TRY, "USER_PIN_FINAL_TRY");
        add(result, flags, CKF_USER_PIN_LOCKED, "USER_PIN_LOCKED");
        add(result, flags, CKF_USER_PIN_TO_BE_CHANGED, "USER_PIN_TO_BE_CHANGED");
        add(result, flags, CKF_USER_PIN_EXPIRED, "USER_PIN_EXPIRED");
        return result;
    }

    static Set<String> mechanismFlags(long flags) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        add(result, flags, CKF_HW, "HW");
        add(result, flags, CKF_ENCRYPT, "ENCRYPT");
        add(result, flags, CKF_DECRYPT, "DECRYPT");
        add(result, flags, CKF_DIGEST, "DIGEST");
        add(result, flags, CKF_SIGN, "SIGN");
        add(result, flags, CKF_SIGN_RECOVER, "SIGN_RECOVER");
        add(result, flags, CKF_VERIFY, "VERIFY");
        add(result, flags, CKF_VERIFY_RECOVER, "VERIFY_RECOVER");
        add(result, flags, CKF_GENERATE, "GENERATE");
        add(result, flags, CKF_GENERATE_KEY_PAIR, "GENERATE_KEY_PAIR");
        add(result, flags, CKF_WRAP, "WRAP");
        add(result, flags, CKF_UNWRAP, "UNWRAP");
        add(result, flags, CKF_DERIVE, "DERIVE");
        add(result, flags, CKF_EXTENSION, "EXTENSION");
        add(result, flags, CKF_EC_F_P, "EC_F_P");
        add(result, flags, CKF_EC_NAMEDCURVE, "EC_NAMEDCURVE");
        add(result, flags, CKF_EC_UNCOMPRESS, "EC_UNCOMPRESS");
        add(result, flags, CKF_EC_COMPRESS, "EC_COMPRESS");
        return result;
    }

    private static void add(Set<String> result, long value, long bit, String name) {
        if ((value & bit) == bit) result.add(name);
    }

    static String mechanismName(long id) {
        return switch ((int) id) {
            case 0x00000000 -> "CKM_RSA_PKCS_KEY_PAIR_GEN";
            case 0x00000001 -> "CKM_RSA_PKCS";
            case 0x00000003 -> "CKM_RSA_X_509";
            case 0x00000005 -> "CKM_MD5_RSA_PKCS";
            case 0x00000006 -> "CKM_SHA1_RSA_PKCS";
            case 0x0000000D -> "CKM_RSA_PKCS_PSS";
            case 0x00000040 -> "CKM_SHA256_RSA_PKCS";
            case 0x00000041 -> "CKM_SHA384_RSA_PKCS";
            case 0x00000042 -> "CKM_SHA512_RSA_PKCS";
            case 0x00000043 -> "CKM_SHA256_RSA_PKCS_PSS";
            case 0x00000044 -> "CKM_SHA384_RSA_PKCS_PSS";
            case 0x00000045 -> "CKM_SHA512_RSA_PKCS_PSS";
            case 0x00000046 -> "CKM_SHA224_RSA_PKCS";
            case 0x00000220 -> "CKM_SHA_1";
            case 0x00000221 -> "CKM_SHA_1_HMAC";
            case 0x00000250 -> "CKM_SHA256";
            case 0x00000251 -> "CKM_SHA256_HMAC";
            case 0x00000260 -> "CKM_SHA384";
            case 0x00000261 -> "CKM_SHA384_HMAC";
            case 0x00000270 -> "CKM_SHA512";
            case 0x00000271 -> "CKM_SHA512_HMAC";
            case 0x00001040 -> "CKM_EC_KEY_PAIR_GEN";
            case 0x00001041 -> "CKM_ECDSA";
            case 0x00001042 -> "CKM_ECDSA_SHA1";
            case 0x00001044 -> "CKM_ECDSA_SHA256";
            case 0x00001045 -> "CKM_ECDSA_SHA384";
            case 0x00001046 -> "CKM_ECDSA_SHA512";
            case 0x00001080 -> "CKM_AES_KEY_GEN";
            case 0x00001081 -> "CKM_AES_ECB";
            case 0x00001082 -> "CKM_AES_CBC";
            case 0x00001083 -> "CKM_AES_MAC";
            case 0x00001084 -> "CKM_AES_MAC_GENERAL";
            case 0x00001085 -> "CKM_AES_CBC_PAD";
            case 0x00001086 -> "CKM_AES_CTR";
            case 0x00001087 -> "CKM_AES_GCM";
            case 0x00001090 -> "CKM_AES_KEY_WRAP";
            case 0x00001091 -> "CKM_AES_KEY_WRAP_PAD";
            case 0x000010A0 -> "CKM_AES_CMAC";
            case 0x00001200 -> "CKM_DES_KEY_GEN";
            case 0x00001201 -> "CKM_DES_ECB";
            case 0x00001202 -> "CKM_DES_CBC";
            case 0x00001205 -> "CKM_DES_CBC_PAD";
            case 0x00001210 -> "CKM_DES3_KEY_GEN";
            case 0x00001211 -> "CKM_DES3_ECB";
            case 0x00001212 -> "CKM_DES3_CBC";
            case 0x00001215 -> "CKM_DES3_CBC_PAD";
            case 0x00001310 -> "CKM_SHA224";
            case 0x00001311 -> "CKM_SHA224_HMAC";
            default -> "CKM_0x" + Long.toHexString(id).toUpperCase(Locale.ROOT);
        };
    }
}
