package com.cryptoforge.asn1;

/**
 * Schema for PKCS#8 Private Key Info (RFC 5208)
 * PrivateKeyInfo ::= SEQUENCE {
 *   version Version,
 *   privateKeyAlgorithm AlgorithmIdentifier,
 *   privateKey OCTET STRING,
 *   attributes [0] IMPLICIT Attributes OPTIONAL
 * }
 */
public class PKCS8PrivateKeySchema {

    /**
     * Apply contextual labels to PKCS#8 Private Key Info
     */
    public static void applyPKCS8Labels(ASN1TreeNode root) {
        if (root == null || root.getChildren().isEmpty()) {
            return;
        }

        // PrivateKeyInfo is a SEQUENCE with 3 or 4 elements
        if (root.getChildren().size() < 3) {
            return;
        }

        addContextLabel(root, "PrivateKeyInfo");

        int idx = 0;

        // version (should be 0)
        if (idx < root.getChildren().size()) {
            ASN1TreeNode version = root.getChildren().get(idx);
            if (version.getLabel().contains("INTEGER")) {
                addContextLabel(version, "version Version");
                idx++;
            }
        }

        // privateKeyAlgorithm
        if (idx < root.getChildren().size()) {
            ASN1TreeNode algorithm = root.getChildren().get(idx);
            if (algorithm.getLabel().contains("SEQUENCE")) {
                addContextLabel(algorithm, "privateKeyAlgorithm AlgorithmIdentifier");
                labelAlgorithmIdentifier(algorithm);
                idx++;
            }
        }

        // privateKey (OCTET STRING containing DER-encoded key)
        if (idx < root.getChildren().size()) {
            ASN1TreeNode privateKey = root.getChildren().get(idx);
            if (privateKey.getLabel().contains("OCTET")) {
                addContextLabel(privateKey, "privateKey");

                // If OCTET STRING contains nested ASN.1 (e.g., PKCS#1 RSA key)
                if (!privateKey.getChildren().isEmpty()) {
                    ASN1TreeNode nestedKey = privateKey.getChildren().get(0);

                    // Try to identify and label nested key structure
                    if (nestedKey.getChildren().size() >= 9) {
                        // Looks like PKCS#1 RSA Private Key
                        PKCS1RSAKeySchema.applyPKCS1RSAKeyLabels(nestedKey);
                    } else if (nestedKey.getChildren().size() == 2) {
                        // Could be EC private key
                        addContextLabel(nestedKey, "ECPrivateKey");
                    }
                }
                idx++;
            }
        }

        // attributes [0] IMPLICIT (optional)
        if (idx < root.getChildren().size()) {
            ASN1TreeNode attributes = root.getChildren().get(idx);
            if (attributes.getLabel().contains("[0]")) {
                addContextLabel(attributes, "attributes Attributes");
            }
        }
    }

    /**
     * Apply contextual labels to Encrypted Private Key Info
     * EncryptedPrivateKeyInfo ::= SEQUENCE {
     *   encryptionAlgorithm AlgorithmIdentifier,
     *   encryptedData OCTET STRING
     * }
     */
    public static void applyEncryptedPKCS8Labels(ASN1TreeNode root) {
        if (root == null || root.getChildren().size() != 2) {
            return;
        }

        addContextLabel(root, "EncryptedPrivateKeyInfo");

        ASN1TreeNode encAlg = root.getChildren().get(0);
        ASN1TreeNode encData = root.getChildren().get(1);

        addContextLabel(encAlg, "encryptionAlgorithm AlgorithmIdentifier");
        labelAlgorithmIdentifier(encAlg);

        addContextLabel(encData, "encryptedData");
    }

    /**
     * Label AlgorithmIdentifier structure
     */
    private static void labelAlgorithmIdentifier(ASN1TreeNode algId) {
        if (algId.getChildren().size() >= 1) {
            addContextLabel(algId.getChildren().get(0), "algorithm");
            if (algId.getChildren().size() >= 2) {
                addContextLabel(algId.getChildren().get(1), "parameters");
            }
        }
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
