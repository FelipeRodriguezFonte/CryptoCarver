package com.cryptocarver.test;

import com.cryptocarver.crypto.MACOperations;
import com.cryptocarver.util.DataConverter;
import org.bouncycastle.crypto.macs.CBCBlockCipherMac;
import org.bouncycastle.crypto.engines.DESedeEngine;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.paddings.ISO7816d4Padding;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;

public class RetailMacDebug {
    public static void main(String[] args) {
        try {
            String keyHex = "26837AEFE59E327AFEDCBA9876543210";
            String dataHex = "4E6F77206973207468652074696D6520666F7220616C6C20676F6F64206D656E20746F20636F6D6520746F2074686520616964206F6620746865697220636F756E747279";

            byte[] key = DataConverter.hexToBytes(keyHex);
            byte[] data = DataConverter.hexToBytes(dataHex);

            System.out.println("Searching for match to: 118A0F4A");

            // 1. Try with/without newline
            byte[] dataWithNL = new byte[data.length + 1];
            System.arraycopy(data, 0, dataWithNL, 0, data.length);
            dataWithNL[data.length] = (byte) 0x0A; // \n

            testVariant("Original Data", data, key);
            testVariant("Data + \\n", dataWithNL, key);

            // 2. Try interpretation as String
            try {
                byte[] dataString = dataHex.getBytes("UTF-8");
                testVariant("Data as String bytes", dataString, key);
            } catch (Exception e) {
            }

            // 3. ISO9797 Alg 3 with 3DES (Not standard X9.19, but "Retail MAC 3DES" might
            // imply this)
            try {
                org.bouncycastle.crypto.macs.ISO9797Alg3Mac mac = new org.bouncycastle.crypto.macs.ISO9797Alg3Mac(
                        new DESedeEngine());
                mac.init(new KeyParameter(key));
                mac.update(data, 0, data.length);
                byte[] out = new byte[mac.getMacSize()];
                mac.doFinal(out, 0);
                testVariantImpl("Alg3 + 3DES", out, "118A0F4A");
            } catch (Exception e) {
            }

            // 4. ISO9797 Alg 3 (Standard DES) with ISO7816-4 Padding
            try {
                org.bouncycastle.crypto.macs.ISO9797Alg3Mac mac = new org.bouncycastle.crypto.macs.ISO9797Alg3Mac(
                        new org.bouncycastle.crypto.engines.DESEngine(), new ISO7816d4Padding());
                mac.init(new KeyParameter(key)); // Uses 16 bytes (K1, K2)
                mac.update(data, 0, data.length);
                byte[] out = new byte[mac.getMacSize()];
                mac.doFinal(out, 0);
                testVariantImpl("Alg3 + DES + ISO7816", out, "118A0F4A");
            } catch (Exception e) {
            }

            // 5. CBC-MAC-3DES with ISO7816 Padding
            try {
                CBCBlockCipherMac mac = new CBCBlockCipherMac(new DESedeEngine(), 64, new ISO7816d4Padding()); // 64
                                                                                                               // bits =
                                                                                                               // 8
                                                                                                               // bytes
                                                                                                               // mac
                                                                                                               // size?
                // Wait, CBCBlockCipherMac(cipher, bitSize, padding)
                // Or CBCBlockCipherMac(cipher, padding) -> defaults to cipher block size?
                // Constructor: CBCBlockCipherMac(BlockCipher cipher, BlockCipherPadding
                // padding)
                // This one produces mac size = block size (8 bytes for 3DES)
                mac.init(new KeyParameter(key));
                mac.update(data, 0, data.length);
                byte[] out = new byte[mac.getMacSize()];
                mac.doFinal(out, 0);
                testVariantImpl("CBC-3DES + ISO7816", out, "118A0F4A");
            } catch (Exception e) {
            }

            // 6. ANSI X9.9 (DES first 8 bytes of key)
            try {
                byte[] key8 = new byte[8];
                System.arraycopy(key, 0, key8, 0, 8);
                byte[] mac = MACOperations.generate(data, key8, "ANSI-X9.9");
                testVariantImpl("ANSI X9.9 (DES)", mac, "118A0F4A");
            } catch (Exception e) {
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void testVariantImpl(String label, byte[] mac, String target) {
        String hex = DataConverter.bytesToHex(mac);
        String check = hex.startsWith(target) ? "MATCH!!!" : "";
        System.out.printf("%-20s | %s %s%n", label, hex, check);
    }

    private static void testVariant(String label, byte[] data, byte[] key) {
        try {
            String[] algos = { "Retail-MAC-3DES", "ANSI-X9.19", "CBC-MAC-3DES", "CMAC-3DES" };

            for (String algo : algos) {
                try {
                    byte[] mac = MACOperations.generate(data, key, algo);
                    String hex = DataConverter.bytesToHex(mac);
                    String check = hex.startsWith("118A0F4A") ? "MATCH!!!" : "";
                    System.out.printf("%-20s | %-15s | %s %s%n", label, algo, hex, check);
                } catch (Exception e) {
                }
            }

            // Manual padding variants?
            // Try BP-Tools style Retail MAC (CBC-MAC with ISO padding?)
            // MACOperations currently uses Zero padding for CBC-MAC.

        } catch (Exception e) {
        }
    }
}
