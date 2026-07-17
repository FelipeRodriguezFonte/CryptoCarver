package com.cryptoforge.asn1;

import java.util.HashMap;
import java.util.Map;

/**
 * Schema for X.509 Certificate structure
 * Maps ASN.1 structure positions to contextual field names
 */
public class X509CertificateSchema {

    /**
     * Apply contextual labels to X.509 certificate structure
     * This modifies the tree in-place, adding descriptive labels
     */
    public static void applyX509Labels(ASN1TreeNode root) {
        if (root == null || root.getChildren().isEmpty()) {
            return;
        }

        // Root should be SEQUENCE with 3 children (tbsCertificate, signatureAlgorithm, signature)
        if (root.getChildren().size() < 3) {
            return;
        }

        ASN1TreeNode tbsCert = root.getChildren().get(0);
        ASN1TreeNode signatureAlgorithm = root.getChildren().get(1);
        ASN1TreeNode signatureValue = root.getChildren().get(2);

        // Label main components
        addContextLabel(tbsCert, "tbsCertificate");
        addContextLabel(signatureAlgorithm, "signatureAlgorithm");
        addContextLabel(signatureValue, "signature");

        // Process tbsCertificate fields
        if (tbsCert.getChildren().size() >= 6) {
            int idx = 0;

            // Version (optional, TAGGED [0])
            ASN1TreeNode firstChild = tbsCert.getChildren().get(idx);
            if (firstChild.getTag().startsWith("[0]")) {
                addContextLabel(firstChild, "version");
                if (!firstChild.getChildren().isEmpty()) {
                    addContextLabel(firstChild.getChildren().get(0), "Version");
                }
                idx++;
            }

            // Serial Number (INTEGER)
            if (idx < tbsCert.getChildren().size()) {
                ASN1TreeNode serialNumber = tbsCert.getChildren().get(idx);
                addContextLabel(serialNumber, "serialNumber CertificateSerialNumber");
                idx++;
            }

            // Signature algorithm (SEQUENCE)
            if (idx < tbsCert.getChildren().size()) {
                ASN1TreeNode signature = tbsCert.getChildren().get(idx);
                addContextLabel(signature, "signature AlgorithmIdentifier");
                if (signature.getChildren().size() >= 1) {
                    addContextLabel(signature.getChildren().get(0), "algorithm");
                    if (signature.getChildren().size() >= 2) {
                        addContextLabel(signature.getChildren().get(1), "parameters");
                    }
                }
                idx++;
            }

            // Issuer (SEQUENCE)
            if (idx < tbsCert.getChildren().size()) {
                ASN1TreeNode issuer = tbsCert.getChildren().get(idx);
                addContextLabel(issuer, "issuer Name");
                labelDistinguishedName(issuer);
                idx++;
            }

            // Validity (SEQUENCE)
            if (idx < tbsCert.getChildren().size()) {
                ASN1TreeNode validity = tbsCert.getChildren().get(idx);
                addContextLabel(validity, "validity Validity");
                if (validity.getChildren().size() >= 2) {
                    addContextLabel(validity.getChildren().get(0), "notBefore");
                    addContextLabel(validity.getChildren().get(1), "notAfter");
                }
                idx++;
            }

            // Subject (SEQUENCE)
            if (idx < tbsCert.getChildren().size()) {
                ASN1TreeNode subject = tbsCert.getChildren().get(idx);
                addContextLabel(subject, "subject Name");
                labelDistinguishedName(subject);
                idx++;
            }

            // SubjectPublicKeyInfo (SEQUENCE)
            if (idx < tbsCert.getChildren().size()) {
                ASN1TreeNode publicKeyInfo = tbsCert.getChildren().get(idx);
                addContextLabel(publicKeyInfo, "subjectPublicKeyInfo SubjectPublicKeyInfo");
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

            // Extensions (optional, TAGGED [3])
            if (idx < tbsCert.getChildren().size()) {
                ASN1TreeNode extensions = tbsCert.getChildren().get(idx);
                if (extensions.getTag().startsWith("[3]")) {
                    addContextLabel(extensions, "extensions");
                    if (!extensions.getChildren().isEmpty()) {
                        ASN1TreeNode extensionsSeq = extensions.getChildren().get(0);
                        addContextLabel(extensionsSeq, "Extensions");

                        // Label each extension
                        for (ASN1TreeNode extension : extensionsSeq.getChildren()) {
                            addContextLabel(extension, "Extension");
                            if (extension.getChildren().size() >= 1) {
                                addContextLabel(extension.getChildren().get(0), "extnID");
                                int extIdx = 1;
                                if (extIdx < extension.getChildren().size() &&
                                    extension.getChildren().get(extIdx).getTag().contains("BOOLEAN")) {
                                    addContextLabel(extension.getChildren().get(extIdx), "critical");
                                    extIdx++;
                                }
                                if (extIdx < extension.getChildren().size()) {
                                    addContextLabel(extension.getChildren().get(extIdx), "extnValue");
                                }
                            }
                        }
                    }
                }
            }
        }

        // Label signatureAlgorithm components
        if (signatureAlgorithm.getChildren().size() >= 1) {
            addContextLabel(signatureAlgorithm.getChildren().get(0), "algorithm");
            if (signatureAlgorithm.getChildren().size() >= 2) {
                addContextLabel(signatureAlgorithm.getChildren().get(1), "parameters");
            }
        }

        // Label signature value
        addContextLabel(signatureValue, "SignatureValue");
    }

    /**
     * Label Distinguished Name components
     */
    private static void labelDistinguishedName(ASN1TreeNode nameNode) {
        if (nameNode == null || nameNode.getChildren().isEmpty()) {
            return;
        }

        // DN is a SEQUENCE of SETs of SEQUENCES
        for (ASN1TreeNode rdnSet : nameNode.getChildren()) {
            addContextLabel(rdnSet, "RelativeDistinguishedName");
            for (ASN1TreeNode attrTypeAndValue : rdnSet.getChildren()) {
                addContextLabel(attrTypeAndValue, "AttributeTypeAndValue");
                if (attrTypeAndValue.getChildren().size() >= 2) {
                    addContextLabel(attrTypeAndValue.getChildren().get(0), "type AttributeType");
                    addContextLabel(attrTypeAndValue.getChildren().get(1), "value AttributeValue");
                }
            }
        }
    }

    /**
     * Add contextual label to node (prepend to existing label)
     */
    private static void addContextLabel(ASN1TreeNode node, String contextLabel) {
        if (node == null) return;

        String currentLabel = node.getLabel();

        // Don't add if already has context
        if (currentLabel.contains(" ")) {
            String[] parts = currentLabel.split(" ", 2);
            if (parts.length > 1 && !parts[0].equals("OID") && !parts[0].equals("OBJECT")) {
                return; // Already has context
            }
        }

        // Prepend context label
        String newLabel = contextLabel + " " + currentLabel;

        // Update label through reflection or create new node
        // For simplicity, we'll create a wrapper method
        node.setLabel(newLabel);
    }
}
