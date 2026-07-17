package com.cryptoforge.asn1;

/**
 * Schema for PKCS#7 / CMS SignedData structure
 * Maps ASN.1 structure positions to contextual field names
 */
public class PKCS7Schema {

    /**
     * Apply contextual labels to PKCS#7 SignedData structure
     */
    public static void applyPKCS7Labels(ASN1TreeNode root) {
        if (root == null || root.getChildren().isEmpty()) {
            return;
        }

        // Root SEQUENCE should have 2 children: contentType OID and [0] EXPLICIT content
        if (root.getChildren().size() < 2) {
            return;
        }

        ASN1TreeNode contentType = root.getChildren().get(0);
        ASN1TreeNode content = root.getChildren().get(1);

        addContextLabel(contentType, "contentType ContentType");

        // Check if it's signedData (1.2.840.113549.1.7.2)
        String oidValue = contentType.getDecodedValue();
        if (oidValue != null && oidValue.contains("1.2.840.113549.1.7.2")) {
            addContextLabel(content, "content");

            // Process SignedData inside [0] EXPLICIT
            if (!content.getChildren().isEmpty()) {
                ASN1TreeNode signedData = content.getChildren().get(0);
                addContextLabel(signedData, "SignedData");

                labelSignedData(signedData);
            }
        }
    }

    /**
     * Label SignedData structure
     * SignedData ::= SEQUENCE {
     *   version CMSVersion,
     *   digestAlgorithms DigestAlgorithmIdentifiers,
     *   encapContentInfo EncapsulatedContentInfo,
     *   certificates [0] IMPLICIT CertificateSet OPTIONAL,
     *   crls [1] IMPLICIT RevocationInfoChoices OPTIONAL,
     *   signerInfos SignerInfos
     * }
     */
    private static void labelSignedData(ASN1TreeNode signedData) {
        if (signedData.getChildren().size() < 3) {
            return;
        }

        int idx = 0;

        // version
        if (idx < signedData.getChildren().size()) {
            ASN1TreeNode version = signedData.getChildren().get(idx);
            if (version.getLabel().contains("INTEGER")) {
                addContextLabel(version, "version CMSVersion");
                idx++;
            }
        }

        // digestAlgorithms
        if (idx < signedData.getChildren().size()) {
            ASN1TreeNode digestAlgs = signedData.getChildren().get(idx);
            if (digestAlgs.getLabel().contains("SET")) {
                addContextLabel(digestAlgs, "digestAlgorithms DigestAlgorithmIdentifiers");
                for (ASN1TreeNode alg : digestAlgs.getChildren()) {
                    addContextLabel(alg, "DigestAlgorithmIdentifier");
                    labelAlgorithmIdentifier(alg);
                }
                idx++;
            }
        }

        // encapContentInfo
        if (idx < signedData.getChildren().size()) {
            ASN1TreeNode encapContent = signedData.getChildren().get(idx);
            if (encapContent.getLabel().contains("SEQUENCE")) {
                addContextLabel(encapContent, "encapContentInfo EncapsulatedContentInfo");
                if (encapContent.getChildren().size() >= 1) {
                    addContextLabel(encapContent.getChildren().get(0), "eContentType ContentType");
                    if (encapContent.getChildren().size() >= 2) {
                        ASN1TreeNode content = encapContent.getChildren().get(1);
                        addContextLabel(content, "eContent");
                        if (!content.getChildren().isEmpty()) {
                            addContextLabel(content.getChildren().get(0), "OCTET STRING");
                        }
                    }
                }
                idx++;
            }
        }

        // certificates [0] IMPLICIT (optional)
        if (idx < signedData.getChildren().size()) {
            ASN1TreeNode certs = signedData.getChildren().get(idx);
            if (certs.getLabel().contains("[0]")) {
                addContextLabel(certs, "certificates");
                if (!certs.getChildren().isEmpty()) {
                    ASN1TreeNode certSeq = certs.getChildren().get(0);
                    addContextLabel(certSeq, "CertificateSet");
                    for (ASN1TreeNode cert : certSeq.getChildren()) {
                        addContextLabel(cert, "Certificate");
                        // Apply X.509 labels to embedded certificate
                        X509CertificateSchema.applyX509Labels(cert);
                    }
                }
                idx++;
            }
        }

        // signerInfos
        if (idx < signedData.getChildren().size()) {
            ASN1TreeNode signerInfos = signedData.getChildren().get(idx);
            if (signerInfos.getLabel().contains("SET")) {
                addContextLabel(signerInfos, "signerInfos SignerInfos");
                for (ASN1TreeNode signerInfo : signerInfos.getChildren()) {
                    addContextLabel(signerInfo, "SignerInfo");
                    labelSignerInfo(signerInfo);
                }
            }
        }
    }

    /**
     * Label SignerInfo structure
     * SignerInfo ::= SEQUENCE {
     *   version CMSVersion,
     *   sid SignerIdentifier,
     *   digestAlgorithm DigestAlgorithmIdentifier,
     *   signedAttrs [0] IMPLICIT SignedAttributes OPTIONAL,
     *   signatureAlgorithm SignatureAlgorithmIdentifier,
     *   signature SignatureValue,
     *   unsignedAttrs [1] IMPLICIT UnsignedAttributes OPTIONAL
     * }
     */
    private static void labelSignerInfo(ASN1TreeNode signerInfo) {
        if (signerInfo.getChildren().size() < 5) {
            return;
        }

        int idx = 0;

        // version
        if (idx < signerInfo.getChildren().size()) {
            ASN1TreeNode version = signerInfo.getChildren().get(idx);
            if (version.getLabel().contains("INTEGER")) {
                addContextLabel(version, "version CMSVersion");
                idx++;
            }
        }

        // sid (SignerIdentifier)
        if (idx < signerInfo.getChildren().size()) {
            ASN1TreeNode sid = signerInfo.getChildren().get(idx);
            addContextLabel(sid, "sid SignerIdentifier");
            if (sid.getChildren().size() >= 2) {
                addContextLabel(sid.getChildren().get(0), "issuer Name");
                addContextLabel(sid.getChildren().get(1), "serialNumber CertificateSerialNumber");
            }
            idx++;
        }

        // digestAlgorithm
        if (idx < signerInfo.getChildren().size()) {
            ASN1TreeNode digestAlg = signerInfo.getChildren().get(idx);
            addContextLabel(digestAlg, "digestAlgorithm DigestAlgorithmIdentifier");
            labelAlgorithmIdentifier(digestAlg);
            idx++;
        }

        // signedAttrs [0] IMPLICIT (optional)
        if (idx < signerInfo.getChildren().size()) {
            ASN1TreeNode attrs = signerInfo.getChildren().get(idx);
            if (attrs.getLabel().contains("[0]")) {
                addContextLabel(attrs, "signedAttrs SignedAttributes");
                labelAttributes(attrs);
                idx++;
            }
        }

        // signatureAlgorithm
        if (idx < signerInfo.getChildren().size()) {
            ASN1TreeNode sigAlg = signerInfo.getChildren().get(idx);
            addContextLabel(sigAlg, "signatureAlgorithm SignatureAlgorithmIdentifier");
            labelAlgorithmIdentifier(sigAlg);
            idx++;
        }

        // signature SignatureValue
        if (idx < signerInfo.getChildren().size()) {
            ASN1TreeNode signature = signerInfo.getChildren().get(idx);
            addContextLabel(signature, "signature SignatureValue");

            // If signature contains nested ASN.1 (ECDSA signature is SEQUENCE of 2 INTEGERs)
            if (!signature.getChildren().isEmpty()) {
                ASN1TreeNode sigSeq = signature.getChildren().get(0);
                if (sigSeq.getChildren().size() == 2) {
                    // ECDSA signature components (r, s)
                    addContextLabel(sigSeq.getChildren().get(0), "r");
                    addContextLabel(sigSeq.getChildren().get(1), "s");
                }
            }
            idx++;
        }
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
     * Label Attributes (signed or unsigned)
     */
    private static void labelAttributes(ASN1TreeNode attrs) {
        if (attrs.getChildren().isEmpty()) {
            return;
        }

        // Attributes is a SEQUENCE of Attribute
        ASN1TreeNode attrsSeq = attrs.getChildren().get(0);
        if (attrsSeq.getLabel().contains("SEQUENCE")) {
            for (ASN1TreeNode attr : attrsSeq.getChildren()) {
                addContextLabel(attr, "Attribute");
                if (attr.getChildren().size() >= 2) {
                    addContextLabel(attr.getChildren().get(0), "attrType");
                    addContextLabel(attr.getChildren().get(1), "attrValues");
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

        // If label already has multiple words, might already be contextualized
        String[] parts = currentLabel.split(" ", 2);
        if (parts.length > 1 && !parts[0].matches("INTEGER|SEQUENCE|SET|OCTET|BIT|OID|UTF8String|UTCTime|GeneralizedTime|BOOLEAN|NULL|\\[\\d+\\]")) {
            return; // Already contextualized
        }

        // Prepend context label
        node.setLabel(contextLabel + " " + currentLabel);
    }
}
