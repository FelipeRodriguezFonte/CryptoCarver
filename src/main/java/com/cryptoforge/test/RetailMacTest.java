package com.cryptoforge.test;

import com.cryptoforge.crypto.MACOperations;
import com.cryptoforge.util.DataConverter;

public class RetailMacTest {
    public static void main(String[] args) {
        try {
            // User provided values
            String keyHex = "26837AEFE59E327AFEDCBA9876543210";
            String dataHex = "4E6F77206973207468652074696D6520666F7220616C6C20676F6F64206D656E20746F20636F6D6520746F2074686520616964206F6620746865697220636F756E747279";

            byte[] key = DataConverter.hexToBytes(keyHex);
            byte[] data = DataConverter.hexToBytes(dataHex);

            System.out.println("TESTING MAC GENERATION");
            System.out.println("Key: " + keyHex);
            System.out.println("Data: " + dataHex);
            System.out.println("----------------------------------------");

            // 1. Retail-MAC-3DES (Current Implementation: CBC-MAC-3DES)
            try {
                byte[] mac1 = MACOperations.generate(data, key, "Retail-MAC-3DES");
                System.out.println("Retail-MAC-3DES (Current): " + DataConverter.bytesToHex(mac1));
            } catch (Exception e) {
                System.out.println("Retail-MAC-3DES (Current): ERROR - " + e.getMessage());
            }

            // 2. ANSI X9.19 (Standard Retail MAC)
            try {
                byte[] mac2 = MACOperations.generate(data, key, "ANSI-X9.19");
                System.out.println("ANSI-X9.19 (Standard):     " + DataConverter.bytesToHex(mac2));
            } catch (Exception e) {
                System.out.println("ANSI-X9.19 (Standard):     ERROR - " + e.getMessage());
            }

            // 3. CBC-MAC-DES (Just for comparison)
            try {
                byte[] mac3 = MACOperations.generate(data, key, "CBC-MAC-DES"); // Note: key length mismatch likely
                                                                                // (expects 8)
                System.out.println("CBC-MAC-DES (First 8 bytes of key): " + DataConverter.bytesToHex(mac3));
            } catch (Exception e) {
                // Try with first 8 bytes of key
                try {
                    byte[] key8 = new byte[8];
                    System.arraycopy(key, 0, key8, 0, 8);
                    byte[] mac3 = MACOperations.generate(data, key8, "CBC-MAC-DES");
                    System.out.println("CBC-MAC-DES (First 8 bytes of key): " + DataConverter.bytesToHex(mac3));
                } catch (Exception ex) {
                    System.out.println("CBC-MAC-DES:               ERROR - " + ex.getMessage());
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
