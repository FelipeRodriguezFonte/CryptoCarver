package com.cryptocarver.crypto;

public class TestTr31 {
    public static void main(String[] args) throws Exception {
        String b = TR31Operations.wrapKey("00112233445566778899AABBCCDDEEFF", "0123456789ABCDEFFEDCBA9876543210", "B1", 'D', 'T', 'E', 'S', "");
        System.out.println("HEADER: " + b.substring(0, 16));
    }
}
