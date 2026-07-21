package com.cryptocarver.crypto;

public class PrintEmvVectors {
    public static void main(String[] args) throws Exception {
        String imk = "0123456789ABCDEFFEDCBA9876543210";
        String pan = "4512345678901234";
        String panSeq = "01";
        String atc = "0001";
        String txnData = "000000001000000000000000097800000000000009781911220012345678";

        String iccMasterKey = EMVOperations.deriveICCMasterKey(imk, pan, panSeq);
        String sessionKey = EMVOperations.deriveSessionKey(iccMasterKey, atc, "");
        String arqc = EMVOperations.generateARQC(sessionKey, txnData, 1);
        String arpc = EMVOperations.generateARPC_Method1(sessionKey, arqc, "3030");

        System.out.println("iccMasterKey: " + iccMasterKey);
        System.out.println("sessionKey: " + sessionKey);
        System.out.println("arqc: " + arqc);
        System.out.println("arpc: " + arpc);
    }
}
