package com.cryptocarver.pin;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.Security;

public final class TDes {
    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private TDes() {
    }

    /** Normaliza PVK 8/16/24 a 24 bytes (K1K1K1 / K1K2K1 / K1K2K3). */
    public static byte[] normalizeTo24(byte[] key) {
        if (key == null)
            throw new IllegalArgumentException("PVK is null");
        if (key.length == 24)
            return key.clone();
        if (key.length == 16) {
            byte[] out = new byte[24];
            System.arraycopy(key, 0, out, 0, 16);
            System.arraycopy(key, 0, out, 16, 8); // K1
            return out;
        }
        if (key.length == 8) {
            byte[] out = new byte[24];
            System.arraycopy(key, 0, out, 0, 8);
            System.arraycopy(key, 0, out, 8, 8);
            System.arraycopy(key, 0, out, 16, 8);
            return out;
        }
        throw new IllegalArgumentException("PVK must be 8, 16, or 24 bytes");
    }

    public static byte[] encryptEcbNoPadding(byte[] key, byte[] block8) {
        if (block8 == null || block8.length != 8) {
            throw new IllegalArgumentException("TDES input must be exactly 8 bytes");
        }
        try {
            byte[] k24 = normalizeTo24(key);
            Cipher c = Cipher.getInstance("DESede/ECB/NoPadding", BouncyCastleProvider.PROVIDER_NAME);
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(k24, "DESede"));
            return c.doFinal(block8);
        } catch (Exception e) {
            throw new IllegalStateException("TDES encrypt failed", e);
        }
    }

    public static byte[] decryptEcbNoPadding(byte[] key, byte[] block8) {
        if (block8 == null || block8.length != 8) {
            throw new IllegalArgumentException("TDES input must be exactly 8 bytes");
        }
        try {
            byte[] k24 = normalizeTo24(key);
            Cipher c = Cipher.getInstance("DESede/ECB/NoPadding", BouncyCastleProvider.PROVIDER_NAME);
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(k24, "DESede"));
            return c.doFinal(block8);
        } catch (Exception e) {
            throw new IllegalStateException("TDES decrypt failed", e);
        }
    }
}
