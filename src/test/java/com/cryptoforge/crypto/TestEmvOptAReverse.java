package com.cryptoforge.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;

public class TestEmvOptAReverse {
    public static void main(String[] args) throws Exception {
        byte[] imk = new BigInteger("0123456789ABCDEFFEDCBA9876543210", 16).toByteArray();
        if (imk[0] == 0 && imk.length > 16) {
            byte[] tmp = new byte[16];
            System.arraycopy(imk, 1, tmp, 0, 16);
            imk = tmp;
        }
        byte[] udkA = new BigInteger("4512345678901234", 16).toByteArray();
        if (udkA[0] == 0 && udkA.length > 8) {
            byte[] tmp = new byte[8];
            System.arraycopy(udkA, 1, tmp, 0, 8);
            udkA = tmp;
        }

        byte[] tdesKey = new byte[24];
        System.arraycopy(imk, 0, tdesKey, 0, 16);
        System.arraycopy(imk, 0, tdesKey, 16, 8);

        Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(tdesKey, "DESede"));

        byte[] div = cipher.doFinal(udkA);
        for(byte b : div) {
            System.out.printf("%02X", b);
        }
        System.out.println();
    }
}
