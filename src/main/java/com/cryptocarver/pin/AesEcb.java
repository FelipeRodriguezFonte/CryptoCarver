package com.cryptocarver.pin;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.Security;

public final class AesEcb {
    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private AesEcb() {
    }

    public static byte[] encryptEcbNoPadding(byte[] key, byte[] block16) {
        if (block16 == null || block16.length != 16) {
            throw new IllegalArgumentException("AES input must be exactly 16 bytes");
        }
        try {
            Cipher c = Cipher.getInstance("AES/ECB/NoPadding", BouncyCastleProvider.PROVIDER_NAME);
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
            return c.doFinal(block16);
        } catch (Exception e) {
            throw new IllegalStateException("AES encrypt failed", e);
        }
    }

    public static byte[] decryptEcbNoPadding(byte[] key, byte[] block16) {
        if (block16 == null || block16.length != 16) {
            throw new IllegalArgumentException("AES input must be exactly 16 bytes");
        }
        try {
            Cipher c = Cipher.getInstance("AES/ECB/NoPadding", BouncyCastleProvider.PROVIDER_NAME);
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
            return c.doFinal(block16);
        } catch (Exception e) {
            throw new IllegalStateException("AES decrypt failed", e);
        }
    }
}
