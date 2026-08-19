package com.cryptocarver.crypto;

/** Optional command-line EMV vector diagnostic; not a JUnit test. */
public final class EmvOptAManualSmoke {
    private EmvOptAManualSmoke() {
    }

    public static void main(String[] args) throws Exception {
        String result = EMVOperations.deriveICCMasterKey(
                "0123456789ABCDEFFEDCBA9876543210", "4512345678901234", "01");
        System.out.println("RES: " + result);
    }
}
