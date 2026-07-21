package com.cryptoforge.asn1;

public class OCSPRequestSchema {
    public static void applyOCSPRequestLabels(ASN1TreeNode root) {
        if (root == null || !root.isConstructed() || root.getChildren().isEmpty()) return;
        root.setLabel("OCSPRequest");
        try {
            ASN1TreeNode tbsRequest = root.getChildren().get(0);
            tbsRequest.setLabel("TBSRequest");
            if (root.getChildren().size() > 1) {
                root.getChildren().get(1).setLabel("OptionalSignature");
            }
        } catch (Exception e) {}
    }
}
