package com.cryptoforge.asn1;

public class TSTInfoSchema {
    public static void applyTSTInfoLabels(ASN1TreeNode root) {
        if (root == null || !root.isConstructed() || root.getChildren().isEmpty()) return;
        root.setLabel("TSTInfo");
        try {
            root.getChildren().get(0).setLabel("Version");
            root.getChildren().get(1).setLabel("Policy");
            root.getChildren().get(2).setLabel("MessageImprint");
            root.getChildren().get(3).setLabel("SerialNumber");
            root.getChildren().get(4).setLabel("GenTime");
        } catch (Exception e) {}
    }
}
