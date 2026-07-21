package com.cryptocarver.asn1;

public class OCSPResponseSchema {
    public static void applyOCSPResponseLabels(ASN1TreeNode root) {
        if (root == null || !root.isConstructed() || root.getChildren().isEmpty()) return;
        root.setLabel("OCSPResponse");
        try {
            ASN1TreeNode responseStatus = root.getChildren().get(0);
            responseStatus.setLabel("OCSPResponseStatus");
            if (root.getChildren().size() > 1) {
                root.getChildren().get(1).setLabel("ResponseBytes");
            }
        } catch (Exception e) {}
    }
}
