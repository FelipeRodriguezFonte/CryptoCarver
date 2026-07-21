package com.cryptocarver.crypto;

public class TestAes {
    public static void main(String[] args) throws Exception {
        String bdk = "0123456789ABCDEF0123456789ABCDEF";
        String ksn = "FFFF9876543210E000000000";
        for (AesDukpt.KeyUsage usage : AesDukpt.KeyUsage.values()) {
            AesDukpt.DerivedKey k = AesDukpt.deriveWorkingKey(bdk, ksn, usage, AesDukpt.KeyType.AES128);
            System.out.println(usage.name() + ": " + k.workingKeyHex());
        }
    }
}
