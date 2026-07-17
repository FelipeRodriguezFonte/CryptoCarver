package com.cryptocalc.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.Security;

/**
 * Helper to find correct KCV byte indices
 */
public class KCVIndexFinder {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static void main(String[] args) throws Exception {
        // Test key (first 8 bytes)
        String keyHex = "9431CBC42651E3E0";
        byte[] key = hexToBytes(keyHex);

        // Encrypt zero block
        Cipher cipher = Cipher.getInstance("DES/ECB/NoPadding", "BC");
        SecretKeySpec keySpec = new SecretKeySpec(key, "DES");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] zeroBlock = new byte[8];
        byte[] encrypted = cipher.doFinal(zeroBlock);

        System.out.println("========================================");
        System.out.println("KCV INDEX FINDER");
        System.out.println("========================================");
        System.out.println();
        System.out.println("Key (first 8 bytes): " + keyHex);
        System.out.println("Encrypted block:     " + bytesToHex(encrypted));
        System.out.println();
        System.out.println("Byte breakdown:");
        for (int i = 0; i < encrypted.length; i++) {
            System.out.printf("  [%d] = %02X%n", i, encrypted[i] & 0xFF);
        }

        // Target KCVs from BP-Tools
        String[] kcvNames = {"IBM", "FUTUREX", "ATALLA R"};
        String[] targetKCVs = {"831C", "6537", "DBA1"};

        System.out.println();
        System.out.println("========================================");
        System.out.println("FINDING BYTE INDICES");
        System.out.println("========================================");

        for (int k = 0; k < targetKCVs.length; k++) {
            byte[] target = hexToBytes(targetKCVs[k]);
            System.out.println();
            System.out.println(kcvNames[k] + " - Target: " + targetKCVs[k]);
            System.out.printf("  Looking for bytes: %02X %02X%n", target[0] & 0xFF, target[1] & 0xFF);

            boolean found = false;
            for (int i = 0; i < encrypted.length; i++) {
                for (int j = 0; j < encrypted.length; j++) {
                    if (i != j) {
                        if ((encrypted[i] & 0xFF) == (target[0] & 0xFF) &&
                            (encrypted[j] & 0xFF) == (target[1] & 0xFF)) {
                            System.out.printf("  ✓ FOUND: bytes[%d,%d] = %02X %02X%n",
                                i, j, encrypted[i] & 0xFF, encrypted[j] & 0xFF);
                            found = true;
                        }
                    }
                }
            }

            if (!found) {
                System.out.println("  ✗ NOT FOUND - May require transformation");
            }
        }

        System.out.println();
        System.out.println("========================================");
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }
}
