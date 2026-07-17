package com.cryptoforge.asn1;

/**
 * Schema for PKCS#1 RSA Private Key (RFC 8017)
 * RSAPrivateKey ::= SEQUENCE {
 *   version Version,
 *   modulus INTEGER,
 *   publicExponent INTEGER,
 *   privateExponent INTEGER,
 *   prime1 INTEGER,
 *   prime2 INTEGER,
 *   exponent1 INTEGER,
 *   exponent2 INTEGER,
 *   coefficient INTEGER,
 *   otherPrimeInfos OtherPrimeInfos OPTIONAL
 * }
 */
public class PKCS1RSAKeySchema {

    /**
     * Apply contextual labels to PKCS#1 RSA Private Key
     */
    public static void applyPKCS1RSAKeyLabels(ASN1TreeNode root) {
        if (root == null || root.getChildren().isEmpty()) {
            return;
        }

        // RSAPrivateKey is a SEQUENCE with 9+ elements
        if (root.getChildren().size() < 9) {
            return;
        }

        addContextLabel(root, "RSAPrivateKey");

        int idx = 0;

        // version (should be 0 for two-prime RSA)
        if (idx < root.getChildren().size()) {
            addContextLabel(root.getChildren().get(idx), "version Version");
            idx++;
        }

        // modulus (n)
        if (idx < root.getChildren().size()) {
            addContextLabel(root.getChildren().get(idx), "modulus");
            idx++;
        }

        // publicExponent (e)
        if (idx < root.getChildren().size()) {
            addContextLabel(root.getChildren().get(idx), "publicExponent");
            idx++;
        }

        // privateExponent (d)
        if (idx < root.getChildren().size()) {
            addContextLabel(root.getChildren().get(idx), "privateExponent");
            idx++;
        }

        // prime1 (p)
        if (idx < root.getChildren().size()) {
            addContextLabel(root.getChildren().get(idx), "prime1");
            idx++;
        }

        // prime2 (q)
        if (idx < root.getChildren().size()) {
            addContextLabel(root.getChildren().get(idx), "prime2");
            idx++;
        }

        // exponent1 (d mod (p-1))
        if (idx < root.getChildren().size()) {
            addContextLabel(root.getChildren().get(idx), "exponent1");
            idx++;
        }

        // exponent2 (d mod (q-1))
        if (idx < root.getChildren().size()) {
            addContextLabel(root.getChildren().get(idx), "exponent2");
            idx++;
        }

        // coefficient (q^-1 mod p)
        if (idx < root.getChildren().size()) {
            addContextLabel(root.getChildren().get(idx), "coefficient");
            idx++;
        }

        // otherPrimeInfos (optional, for multi-prime RSA)
        if (idx < root.getChildren().size()) {
            addContextLabel(root.getChildren().get(idx), "otherPrimeInfos OtherPrimeInfos");
        }
    }

    /**
     * Apply contextual labels to PKCS#1 RSA Public Key
     * RSAPublicKey ::= SEQUENCE {
     *   modulus INTEGER,
     *   publicExponent INTEGER
     * }
     */
    public static void applyPKCS1RSAPublicKeyLabels(ASN1TreeNode root) {
        if (root == null || root.getChildren().size() != 2) {
            return;
        }

        addContextLabel(root, "RSAPublicKey");
        addContextLabel(root.getChildren().get(0), "modulus");
        addContextLabel(root.getChildren().get(1), "publicExponent");
    }

    /**
     * Add contextual label to node
     */
    private static void addContextLabel(ASN1TreeNode node, String contextLabel) {
        if (node == null) return;

        String currentLabel = node.getLabel();
        String[] parts = currentLabel.split(" ", 2);
        if (parts.length > 1 && !isASN1Type(parts[0])) {
            return;
        }

        node.setLabel(contextLabel + " " + currentLabel);
    }

    private static boolean isASN1Type(String word) {
        return word.matches("INTEGER|SEQUENCE|SET|OCTET|BIT|OID|UTF8String|UTCTime|GeneralizedTime|BOOLEAN|NULL|\\[\\d+\\]");
    }
}
