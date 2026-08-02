package com.cryptocarver.crypto;

/** Optional command-line AES-DUKPT vector diagnostic; not a JUnit test. */
public final class AesManualSmoke {
    private AesManualSmoke() {
    }

    public static void main(String[] args) throws Exception {
        String bdk = "0123456789ABCDEF0123456789ABCDEF";
        String ksn = "FFFF9876543210E000000000";
        for (AesDukpt.KeyUsage usage : AesDukpt.KeyUsage.values()) {
            AesDukpt.DerivedKey key = AesDukpt.deriveWorkingKey(
                    bdk, ksn, usage, AesDukpt.KeyType.AES128);
            System.out.println(usage.name() + ": " + key.workingKeyHex());
        }
    }
}
