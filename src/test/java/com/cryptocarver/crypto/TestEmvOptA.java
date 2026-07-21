package com.cryptocarver.crypto;

public class TestEmvOptA {
    public static void main(String[] args) throws Exception {
        String res = EMVOperations.deriveICCMasterKey("0123456789ABCDEFFEDCBA9876543210", "4512345678901234", "01");
        System.out.println("RES: " + res);
    }
}
