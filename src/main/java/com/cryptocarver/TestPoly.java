package com.cryptocarver;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class TestPoly {
    public static void main(String[] args) {
        Security.addProvider(new BouncyCastleProvider());
        test("ChaCha20-Poly1305", 8);
        test("ChaCha20-Poly1305", 12);
        test("ChaCha20-Poly1305", 16);
        test("ChaCha20-Poly1305", 24);

        test("ChaCha-Poly1305", 8);
        test("ChaCha-Poly1305", 12);

        test("XChaCha20-Poly1305", 24);
        test("XChaCha20", 24);
    }

    private static void test(String algo, int nonceLen) {
        try {
            System.out.println("Testing " + algo + " with " + nonceLen + " bytes nonce...");
            Cipher c = Cipher.getInstance(algo, "BC");
            byte[] key = new byte[32];
            byte[] nonce = new byte[nonceLen];
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "ChaCha20"), new IvParameterSpec(nonce));
            System.out.println("-> SUCCESS");
        } catch (Exception e) {
            System.out.println("-> FAILED: " + e.getMessage());
        }
    }
}
