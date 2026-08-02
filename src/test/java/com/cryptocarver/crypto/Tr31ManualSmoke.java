package com.cryptocarver.crypto;

/** Optional command-line smoke for local TR-31 diagnostics; not a JUnit test. */
public final class Tr31ManualSmoke {
    private Tr31ManualSmoke() {
    }

    public static void main(String[] args) throws Exception {
        String block = TR31Operations.wrapKey(
                "00112233445566778899AABBCCDDEEFF",
                "0123456789ABCDEFFEDCBA9876543210",
                "B1", 'D', 'T', 'E', 'S', "");
        System.out.println("HEADER: " + block.substring(0, 16));
    }
}
