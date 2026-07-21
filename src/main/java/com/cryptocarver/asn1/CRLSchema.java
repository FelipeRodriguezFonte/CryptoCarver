package com.cryptocarver.asn1;

/**
 * Schema for Certificate Revocation List (RFC 5280)
 * CertificateList ::= SEQUENCE {
 *   tbsCertList TBSCertList,
 *   signatureAlgorithm AlgorithmIdentifier,
 *   signatureValue BIT STRING
 * }
 *
 * TBSCertList ::= SEQUENCE {
 *   version Version OPTIONAL,
 *   signature AlgorithmIdentifier,
 *   issuer Name,
 *   thisUpdate Time,
 *   nextUpdate Time OPTIONAL,
 *   revokedCertificates SEQUENCE OF SEQUENCE OPTIONAL,
 *   crlExtensions [0] EXPLICIT Extensions OPTIONAL
 * }
 */
public class CRLSchema {

    /**
     * Apply contextual labels to CRL
     */
    public static void applyCRLLabels(ASN1TreeNode root) {
        if (root == null || root.getChildren().size() != 3) {
            return;
        }

        addContextLabel(root, "CertificateList");

        ASN1TreeNode tbsCertList = root.getChildren().get(0);
        ASN1TreeNode signatureAlg = root.getChildren().get(1);
        ASN1TreeNode signature = root.getChildren().get(2);

        // Label main components
        addContextLabel(tbsCertList, "tbsCertList TBSCertList");
        addContextLabel(signatureAlg, "signatureAlgorithm AlgorithmIdentifier");
        addContextLabel(signature, "signatureValue");

        // Label TBSCertList fields
        if (tbsCertList.getChildren().size() >= 4) {
            int idx = 0;

            // version (optional, v2 = 1)
            ASN1TreeNode firstChild = tbsCertList.getChildren().get(idx);
            if (firstChild.getLabel().contains("INTEGER")) {
                addContextLabel(firstChild, "version Version");
                idx++;
            }

            // signature algorithm
            if (idx < tbsCertList.getChildren().size()) {
                ASN1TreeNode signature2 = tbsCertList.getChildren().get(idx);
                if (signature2.getLabel().contains("SEQUENCE")) {
                    addContextLabel(signature2, "signature AlgorithmIdentifier");
                    labelAlgorithmIdentifier(signature2);
                    idx++;
                }
            }

            // issuer (Distinguished Name)
            if (idx < tbsCertList.getChildren().size()) {
                ASN1TreeNode issuer = tbsCertList.getChildren().get(idx);
                if (issuer.getLabel().contains("SEQUENCE")) {
                    addContextLabel(issuer, "issuer Name");
                    labelDistinguishedName(issuer);
                    idx++;
                }
            }

            // thisUpdate (Time)
            if (idx < tbsCertList.getChildren().size()) {
                ASN1TreeNode thisUpdate = tbsCertList.getChildren().get(idx);
                if (thisUpdate.getLabel().contains("UTCTime") ||
                    thisUpdate.getLabel().contains("GeneralizedTime")) {
                    addContextLabel(thisUpdate, "thisUpdate Time");
                    idx++;
                }
            }

            // nextUpdate (Time, optional)
            if (idx < tbsCertList.getChildren().size()) {
                ASN1TreeNode nextChild = tbsCertList.getChildren().get(idx);
                if (nextChild.getLabel().contains("UTCTime") ||
                    nextChild.getLabel().contains("GeneralizedTime")) {
                    addContextLabel(nextChild, "nextUpdate Time");
                    idx++;
                }
            }

            // revokedCertificates (SEQUENCE OF, optional)
            if (idx < tbsCertList.getChildren().size()) {
                ASN1TreeNode revokedCerts = tbsCertList.getChildren().get(idx);
                if (revokedCerts.getLabel().contains("SEQUENCE") &&
                    !revokedCerts.getLabel().contains("[")) {
                    addContextLabel(revokedCerts, "revokedCertificates");

                    // Label each revoked certificate entry
                    for (ASN1TreeNode revokedCert : revokedCerts.getChildren()) {
                        addContextLabel(revokedCert, "RevokedCertificate");

                        if (revokedCert.getChildren().size() >= 2) {
                            int rcIdx = 0;

                            // userCertificate (serial number)
                            if (rcIdx < revokedCert.getChildren().size()) {
                                ASN1TreeNode serialNumber = revokedCert.getChildren().get(rcIdx);
                                if (serialNumber.getLabel().contains("INTEGER")) {
                                    addContextLabel(serialNumber, "userCertificate CertificateSerialNumber");
                                    rcIdx++;
                                }
                            }

                            // revocationDate (Time)
                            if (rcIdx < revokedCert.getChildren().size()) {
                                ASN1TreeNode revDate = revokedCert.getChildren().get(rcIdx);
                                if (revDate.getLabel().contains("UTCTime") ||
                                    revDate.getLabel().contains("GeneralizedTime")) {
                                    addContextLabel(revDate, "revocationDate Time");
                                    rcIdx++;
                                }
                            }

                            // crlEntryExtensions (optional)
                            if (rcIdx < revokedCert.getChildren().size()) {
                                ASN1TreeNode extensions = revokedCert.getChildren().get(rcIdx);
                                if (extensions.getLabel().contains("SEQUENCE")) {
                                    addContextLabel(extensions, "crlEntryExtensions Extensions");
                                    labelExtensions(extensions);
                                }
                            }
                        }
                    }
                    idx++;
                }
            }

            // crlExtensions [0] EXPLICIT (optional)
            if (idx < tbsCertList.getChildren().size()) {
                ASN1TreeNode extensions = tbsCertList.getChildren().get(idx);
                if (extensions.getLabel().contains("[0]")) {
                    addContextLabel(extensions, "crlExtensions");
                    if (!extensions.getChildren().isEmpty()) {
                        ASN1TreeNode extSeq = extensions.getChildren().get(0);
                        addContextLabel(extSeq, "Extensions");
                        labelExtensions(extSeq);
                    }
                }
            }
        }

        // Label signatureAlgorithm components
        labelAlgorithmIdentifier(signatureAlg);
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
     * Label Extensions
     */
    private static void labelExtensions(ASN1TreeNode extSeq) {
        if (extSeq == null || extSeq.getChildren().isEmpty()) {
            return;
        }

        for (ASN1TreeNode extension : extSeq.getChildren()) {
            if (extension.getLabel().contains("SEQUENCE")) {
                addContextLabel(extension, "Extension");
                if (extension.getChildren().size() >= 1) {
                    addContextLabel(extension.getChildren().get(0), "extnID");
                    int extIdx = 1;
                    if (extIdx < extension.getChildren().size() &&
                        extension.getChildren().get(extIdx).getLabel().contains("BOOLEAN")) {
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
