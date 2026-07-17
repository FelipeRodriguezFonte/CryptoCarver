package com.cryptoforge.asn1;

/**
 * Schema for PKCS#10 Certificate Request (RFC 2986)
 * CertificationRequest ::= SEQUENCE {
 *   certificationRequestInfo CertificationRequestInfo,
 *   signatureAlgorithm AlgorithmIdentifier,
 *   signature BIT STRING
 * }
 *
 * CertificationRequestInfo ::= SEQUENCE {
 *   version INTEGER,
 *   subject Name,
 *   subjectPKInfo SubjectPublicKeyInfo,
 *   attributes [0] IMPLICIT Attributes
 * }
 */
public class PKCS10CSRSchema {

    /**
     * Apply contextual labels to PKCS#10 Certificate Request
     */
    public static void applyPKCS10Labels(ASN1TreeNode root) {
        if (root == null || root.getChildren().size() != 3) {
            return;
        }

        addContextLabel(root, "CertificationRequest");

        ASN1TreeNode certReqInfo = root.getChildren().get(0);
        ASN1TreeNode signatureAlg = root.getChildren().get(1);
        ASN1TreeNode signature = root.getChildren().get(2);

        // Label main components
        addContextLabel(certReqInfo, "certificationRequestInfo CertificationRequestInfo");
        addContextLabel(signatureAlg, "signatureAlgorithm AlgorithmIdentifier");
        addContextLabel(signature, "signature");

        // Label CertificationRequestInfo fields
        if (certReqInfo.getChildren().size() >= 3) {
            int idx = 0;

            // version
            if (idx < certReqInfo.getChildren().size()) {
                ASN1TreeNode version = certReqInfo.getChildren().get(idx);
                if (version.getLabel().contains("INTEGER")) {
                    addContextLabel(version, "version Version");
                    idx++;
                }
            }

            // subject (Distinguished Name)
            if (idx < certReqInfo.getChildren().size()) {
                ASN1TreeNode subject = certReqInfo.getChildren().get(idx);
                if (subject.getLabel().contains("SEQUENCE")) {
                    addContextLabel(subject, "subject Name");
                    labelDistinguishedName(subject);
                    idx++;
                }
            }

            // subjectPKInfo
            if (idx < certReqInfo.getChildren().size()) {
                ASN1TreeNode publicKeyInfo = certReqInfo.getChildren().get(idx);
                if (publicKeyInfo.getLabel().contains("SEQUENCE")) {
                    addContextLabel(publicKeyInfo, "subjectPKInfo SubjectPublicKeyInfo");

                    if (publicKeyInfo.getChildren().size() >= 2) {
                        ASN1TreeNode algorithm = publicKeyInfo.getChildren().get(0);
                        ASN1TreeNode publicKey = publicKeyInfo.getChildren().get(1);

                        addContextLabel(algorithm, "algorithm AlgorithmIdentifier");
                        if (algorithm.getChildren().size() >= 1) {
                            addContextLabel(algorithm.getChildren().get(0), "algorithm");
                            if (algorithm.getChildren().size() >= 2) {
                                addContextLabel(algorithm.getChildren().get(1), "parameters");
                            }
                        }

                        addContextLabel(publicKey, "subjectPublicKey");

                        // If BIT STRING contains nested ASN.1 (RSA public key)
                        if (!publicKey.getChildren().isEmpty()) {
                            ASN1TreeNode nestedKey = publicKey.getChildren().get(0);

                            // Check if it's RSA public key (SEQUENCE with 2 INTEGERs)
                            if (nestedKey.getLabel().contains("SEQUENCE") &&
                                nestedKey.getChildren().size() == 2) {

                                ASN1TreeNode first = nestedKey.getChildren().get(0);
                                ASN1TreeNode second = nestedKey.getChildren().get(1);

                                if (first.getLabel().contains("INTEGER") &&
                                    second.getLabel().contains("INTEGER")) {
                                    // This is RSA public key
                                    addContextLabel(nestedKey, "RSAPublicKey");
                                    addContextLabel(first, "modulus");
                                    addContextLabel(second, "publicExponent");
                                }
                            }
                        }
                    }
                    idx++;
                }
            }

            // attributes [0] IMPLICIT
            if (idx < certReqInfo.getChildren().size()) {
                ASN1TreeNode attributes = certReqInfo.getChildren().get(idx);
                if (attributes.getLabel().contains("[0]")) {
                    addContextLabel(attributes, "attributes Attributes");

                    // Label individual attributes
                    if (!attributes.getChildren().isEmpty()) {
                        for (ASN1TreeNode attr : attributes.getChildren()) {
                            if (attr.getLabel().contains("SEQUENCE")) {
                                addContextLabel(attr, "Attribute");
                                if (attr.getChildren().size() >= 2) {
                                    addContextLabel(attr.getChildren().get(0), "type");
                                    addContextLabel(attr.getChildren().get(1), "values");
                                }
                            }
                        }
                    }
                }
            }
        }

        // Label signatureAlgorithm components
        if (signatureAlg.getChildren().size() >= 1) {
            addContextLabel(signatureAlg.getChildren().get(0), "algorithm");
            if (signatureAlg.getChildren().size() >= 2) {
                addContextLabel(signatureAlg.getChildren().get(1), "parameters");
            }
        }
    }

    /**
     * Label Distinguished Name components
     */
    private static void labelDistinguishedName(ASN1TreeNode nameNode) {
        if (nameNode == null || nameNode.getChildren().isEmpty()) {
            return;
        }

        for (ASN1TreeNode rdnSet : nameNode.getChildren()) {
            if (rdnSet.getLabel().contains("SET")) {
                addContextLabel(rdnSet, "RelativeDistinguishedName");
                for (ASN1TreeNode attrTypeAndValue : rdnSet.getChildren()) {
                    if (attrTypeAndValue.getLabel().contains("SEQUENCE")) {
                        addContextLabel(attrTypeAndValue, "AttributeTypeAndValue");
                        if (attrTypeAndValue.getChildren().size() >= 2) {
                            addContextLabel(attrTypeAndValue.getChildren().get(0), "type AttributeType");
                            addContextLabel(attrTypeAndValue.getChildren().get(1), "value AttributeValue");
                        }
                    }
                }
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
