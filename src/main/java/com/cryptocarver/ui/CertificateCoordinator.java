package com.cryptocarver.ui;

import com.cryptocarver.crypto.*;
import com.cryptocarver.model.OperationResult;
import javafx.scene.control.*;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Certificate, request and revocation workbenches. */
final class CertificateCoordinator extends KeysCoordinatorSupport {
    private static final Logger LOG = LoggerFactory.getLogger(CertificateCoordinator.class);
    record View(
            TextField certCNField,
            TextField certOrgField,
            TextField certOUField,
            TextField certLocalityField,
            TextField certStateField,
            TextField certCountryField,
            TextField certEmailField,
            TextField certValidityField,
            ComboBox<String> certKeyTypeCombo,
            ComboBox<String> certSignAlgoCombo,
            TextArea certOutputArea,
            TextField certSanDnsField,
            TextField certSanIpField,
            CheckBox certRootCaCheck,
            TextArea certInputArea,
            TextArea certParseResultArea,
            TextArea certCompareLeftArea,
            TextArea certCompareRightArea,
            TextArea certCompareResultArea,
            TextArea certIssueCsrArea,
            TextArea certIssueCaCertArea,
            TextArea certIssueCaKeyArea,
            TextField certIssueValidityField,
            TextField certIssueSignatureField,
            TextArea certIssueResultArea,
            ComboBox<String> certIssueProfileCombo,
            TextField certIssuePathLengthField,
            TextArea crlIssuerCertArea,
            TextArea crlIssuerKeyArea,
            TextArea crlExistingCrlArea,
            TextField crlRevokeSerialField,
            ComboBox<String> crlRevokeReasonCombo,
            TextArea crlResultArea,
            TextArea valCertInput,
            TextArea valIssuerInput,
            TextArea valResultArea) { }

    private final java.util.function.Supplier<View> view;
    private final java.util.function.Supplier<String> signingAlias;

    CertificateCoordinator(java.util.function.Supplier<View> view, java.util.function.Supplier<StatusReporter> reporter, java.util.function.Supplier<String> signingAlias) {
        super(reporter);
        this.view = view;
        this.signingAlias = signingAlias;
    }

    private View view() {
        return view.get();
    }

    void initializeCertificateGen() {

        view().certKeyTypeCombo().getItems().addAll("RSA-2048", "RSA-4096", "ECDSA-P256", "ECDSA-P384", "Local PEM (Parse Area)", "PKCS#11 Active Alias");
        view().certKeyTypeCombo().setValue("RSA-2048");

        view().certSignAlgoCombo().getItems().addAll("SHA256withRSA", "SHA384withRSA", "SHA512withRSA");
        view().certSignAlgoCombo().setValue("SHA256withRSA");

        view().certValidityField().setText("365");
    }

    void initializeCertificateParse() {
    }

    void initializeCertificateComparator() {
    }

    void initializeCertificateIssuer() {

        if (view().certIssueProfileCombo() != null) {
            view().certIssueProfileCombo().getItems().setAll(Arrays.stream(CertificateAuthorityOperations.IssuanceProfile.values())
                .map(Enum::name).toList());
            view().certIssueProfileCombo().setValue(CertificateAuthorityOperations.IssuanceProfile.TLS_SERVER.name());
        }
    }

    void initializeCrlManagement() {

        if (view().crlRevokeReasonCombo() != null) {
            view().crlRevokeReasonCombo().getItems().setAll(
                "UNSPECIFIED", "KEY_COMPROMISE", "CA_COMPROMISE", "AFFILIATION_CHANGED",
                "SUPERSEDED", "CESSATION_OF_OPERATION", "CERTIFICATE_HOLD", "PRIVILEGE_WITHDRAWN"
            );
            view().crlRevokeReasonCombo().setValue("UNSPECIFIED");
        }
    }

    void handleIssueCertificateFromCsr() {
        try {
            String csrPem = view().certIssueCsrArea().getText().trim();
            String compact = csrPem.replaceAll("-----[^-]+-----|\\s", "");
            var csr = new org.bouncycastle.pkcs.PKCS10CertificationRequest(java.util.Base64.getDecoder().decode(compact));
            var factory = java.security.cert.CertificateFactory.getInstance("X.509");
            var issuerCert = (X509Certificate) factory.generateCertificate(new java.io.ByteArrayInputStream(
                    view().certIssueCaCertArea().getText().trim().getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
            var issuerKey = AsymmetricKeyOperations.importPrivateKeyPEMAuto(view().certIssueCaKeyArea().getText().trim());
            int validityDays = Integer.parseInt(view().certIssueValidityField().getText().trim());
            String overrideAlgorithm = view().certIssueSignatureField().getText().trim();
            if ("Automatic".equalsIgnoreCase(overrideAlgorithm)) overrideAlgorithm = null;
            if (overrideAlgorithm == null || overrideAlgorithm.isBlank()) {
                overrideAlgorithm = CertificateAuthorityOperations.suggestSignatureAlgorithm(issuerKey);
            }

            CertificateAuthorityOperations.IssuanceProfile profile = CertificateAuthorityOperations.IssuanceProfile.TLS_SERVER;
            if (view().certIssueProfileCombo() != null && view().certIssueProfileCombo().getValue() != null) {
                profile = CertificateAuthorityOperations.IssuanceProfile.valueOf(view().certIssueProfileCombo().getValue());
            }

            int pathLength = -1;
            if (profile == CertificateAuthorityOperations.IssuanceProfile.INTERMEDIATE_CA) {
                try {
                    pathLength = Integer.parseInt(view().certIssuePathLengthField().getText().trim());
                } catch (NumberFormatException ignored) {}
            }

            var issued = CertificateAuthorityOperations.issueFromCsr(csr, issuerCert, issuerKey, validityDays, overrideAlgorithm, profile, pathLength);
            String outputText = "=== ISSUED CERTIFICATE ===\n\n"
                    + CertificateGenerator.getCertificateInfo(issued)
                    + "\n\n" + CertificateGenerator.exportCertificatePEM(issued);
            view().certIssueResultArea().setText(outputText);
            updateStatus("Certificate issued from validated CSR");
            if (reporter() != null) {
                reporter().publish(com.cryptocarver.model.OperationResult.forOperation("Issue CA Certificate")
                    .enrichedOutput(outputText, com.cryptocarver.model.OperationDetail.Classification.PUBLIC)
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Target", issued.getSubjectX500Principal().getName(), com.cryptocarver.model.OperationDetail.Classification.PUBLIC, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", outputText, com.cryptocarver.model.OperationDetail.Classification.PUBLIC, false, null)
                    ))
                    .build());
            }
        } catch (Exception e) {
            showError("Issue Certificate", "Cannot issue certificate: " + e.getMessage());
        }
    }

    void handleGenerateCrl() {
        try {
            var factory = java.security.cert.CertificateFactory.getInstance("X.509");
            var issuerCert = (X509Certificate) factory.generateCertificate(new java.io.ByteArrayInputStream(
                    view().crlIssuerCertArea().getText().trim().getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
            var issuerKey = AsymmetricKeyOperations.importPrivateKeyPEMAuto(view().crlIssuerKeyArea().getText().trim());

            var crl = RevocationOperations.generateEmptyCrl(issuerCert, issuerKey);
            String outputText = RevocationOperations.exportCrlToPem(crl);
            view().crlResultArea().setText(outputText);
            updateStatus("Empty CRL generated successfully");
            if (reporter() != null) {
                reporter().publish(OperationResult.forOperation("Generate CRL")
                        .enrichedOutput(outputText, com.cryptocarver.model.OperationDetail.Classification.PUBLIC)
                        .detail(com.cryptocarver.model.OperationDetail.publicDetail(
                                "Issuer", issuerCert.getSubjectX500Principal().getName()))
                        .status("Empty CRL generated successfully")
                        .build());
            }
        } catch (Exception e) {
            showError("Generate CRL", "Failed to generate CRL: " + e.getMessage());
        }
    }

    void handleRevokeCrl() {
        try {
            var factory = java.security.cert.CertificateFactory.getInstance("X.509");
            var issuerCert = (X509Certificate) factory.generateCertificate(new java.io.ByteArrayInputStream(
                    view().crlIssuerCertArea().getText().trim().getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
            var issuerKey = AsymmetricKeyOperations.importPrivateKeyPEMAuto(view().crlIssuerKeyArea().getText().trim());
            String existingCrlStr = view().crlExistingCrlArea().getText().trim();
            java.security.cert.X509CRL existingCrl = null;
            if (!existingCrlStr.isEmpty()) {
                existingCrl = RevocationOperations.parseCrlPem(existingCrlStr);
            }

            String serialStr = view().crlRevokeSerialField().getText().trim();
            if (serialStr.isEmpty()) throw new IllegalArgumentException("Serial number required for revocation");
            java.math.BigInteger serial = new java.math.BigInteger(serialStr, 16);

            String reasonStr = view().crlRevokeReasonCombo().getValue();
            int reason = org.bouncycastle.asn1.x509.CRLReason.unspecified;
            if (reasonStr != null) {
                switch (reasonStr) {
                    case "KEY_COMPROMISE": reason = org.bouncycastle.asn1.x509.CRLReason.keyCompromise;
                    break;
                    case "CA_COMPROMISE": reason = org.bouncycastle.asn1.x509.CRLReason.cACompromise;
                    break;
                    case "AFFILIATION_CHANGED": reason = org.bouncycastle.asn1.x509.CRLReason.affiliationChanged;
                    break;
                    case "SUPERSEDED": reason = org.bouncycastle.asn1.x509.CRLReason.superseded;
                    break;
                    case "CESSATION_OF_OPERATION": reason = org.bouncycastle.asn1.x509.CRLReason.cessationOfOperation;
                    break;
                    case "CERTIFICATE_HOLD": reason = org.bouncycastle.asn1.x509.CRLReason.certificateHold;
                    break;
                    case "PRIVILEGE_WITHDRAWN": reason = org.bouncycastle.asn1.x509.CRLReason.privilegeWithdrawn;
                    break;
                }
            }

            var crl = RevocationOperations.appendRevocation(existingCrl, issuerCert, issuerKey, serial, reason, new java.util.Date());
            String outputText = RevocationOperations.exportCrlToPem(crl);
            view().crlResultArea().setText(outputText);
            updateStatus("CRL updated successfully");
            if (reporter() != null) {
                reporter().publish(OperationResult.forOperation("Update CRL")
                        .enrichedOutput(outputText, com.cryptocarver.model.OperationDetail.Classification.PUBLIC)
                        .detail(com.cryptocarver.model.OperationDetail.publicDetail("Revoked Serial", serial.toString(16)))
                        .status("CRL updated successfully")
                        .build());
            }
        } catch (Exception e) {
            showError("Update CRL", "Failed to update CRL: " + e.getMessage());
        }
    }

    void handleCompareCertificates() {
        try {
            var factory = java.security.cert.CertificateFactory.getInstance("X.509");
            var left = (X509Certificate) factory.generateCertificate(new java.io.ByteArrayInputStream(
                    view().certCompareLeftArea().getText().trim().getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
            var right = (X509Certificate) factory.generateCertificate(new java.io.ByteArrayInputStream(
                    view().certCompareRightArea().getText().trim().getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
            String outputText = CertificateComparator.compare(left, right);
            view().certCompareResultArea().setText(outputText);
            updateStatus("Certificates compared");
            if (reporter() != null) {
                reporter().publish(OperationResult.forOperation("Compare Certificates")
                        .enrichedOutput(outputText, com.cryptocarver.model.OperationDetail.Classification.PUBLIC)
                        .status("Certificates compared")
                        .build());
            }
        } catch (Exception e) {
            showError("Compare Certificates", "Cannot compare certificates: " + e.getMessage());
        }
    }

    void initializeValidateCertificate() {
    }

    void handleGenerateCertificate() {
        try {
            // Validate inputs
            String cn = view().certCNField().getText().trim();
            if (cn.isEmpty()) {
                showError("Input Error", "Common Name (CN) is required");
                return;
            }

            int validity;
            try {
                validity = Integer.parseInt(view().certValidityField().getText().trim());
                if (validity <= 0)
                    throw new NumberFormatException();
            } catch (NumberFormatException e) {
                showError("Input Error", "Validity must be a positive number of days");
                return;
            }

            updateStatus("Generating certificate and key pair...");

            // Generate or use existing key pair
            KeyPair keyPair;
            String keyTypeDesc;

            String certKeyType = view().certKeyTypeCombo().getValue();
            if (certKeyType.startsWith("RSA")) {
                int keySize = Integer.parseInt(certKeyType.substring(4));
                keyPair = AsymmetricKeyOperations.generateRSAKeyPair(keySize);
                keyTypeDesc = "RSA-" + keySize;
            } else if (certKeyType.startsWith("ECDSA")) {
                String curve = certKeyType.equals("ECDSA-P256") ? "secp256r1" : "secp384r1";
                keyPair = AsymmetricKeyOperations.generateECDSAFpKeyPair(curve);
                keyTypeDesc = "ECDSA-" + curve;
            } else {
                showError("Input Error", "Invalid key type selected");
                return;
            }

            // Build certificate configuration
            CertificateGenerator.CertificateConfig config = new CertificateGenerator.CertificateConfig();
            config.commonName = cn;
            config.organization = view().certOrgField() != null ? view().certOrgField().getText().trim() : "Crypto Org";
            config.organizationalUnit = view().certOUField() != null ? view().certOUField().getText().trim() : "IT Security";
            config.locality = view().certLocalityField() != null ? view().certLocalityField().getText().trim() : "Madrid";
            config.state = view().certStateField() != null ? view().certStateField().getText().trim() : "Madrid";
            config.country = view().certCountryField() != null ? view().certCountryField().getText().trim() : "ES";
            config.validityDays = validity;
            config.signatureAlgorithm = view().certSignAlgoCombo().getValue();
            applySanConfiguration(config);

            // Email is optional - only add if provided
            String email = view().certEmailField() != null ? view().certEmailField().getText().trim() : "";
            config.email = email.isEmpty() ? null : email;

            // Generate certificate
            boolean rootCa = view().certRootCaCheck() != null && view().certRootCaCheck().isSelected();
            X509Certificate certificate = rootCa
                    ? CertificateGenerator.generateRootCA(keyPair, config, 1)
                    : CertificateGenerator.generateSelfSignedCertificate(keyPair, config);

            // Build output
            StringBuilder output = new StringBuilder();
            output.append(rootCa ? "=== SELF-SIGNED ROOT CA (LABORATORY) ===\n\n" : "=== SELF-SIGNED X.509 CERTIFICATE ===\n\n");
            output.append(CertificateGenerator.getCertificateInfo(certificate));
            output.append("\n\n=== CERTIFICATE (PEM) ===\n");
            output.append(CertificateGenerator.exportCertificatePEM(certificate));
            output.append("\n=== PRIVATE KEY (PEM) ===\n");
            output.append(AsymmetricKeyOperations.exportPrivateKeyPEM(keyPair.getPrivate()));
            output.append("\n=== PUBLIC KEY (PEM) ===\n");
            output.append(AsymmetricKeyOperations.exportPublicKeyPEM(keyPair.getPublic()));

            view().certOutputArea().setText(output.toString());
            view().certOutputArea().setVisible(true);
            view().certOutputArea().setManaged(true);

            updateStatus("Certificate generated successfully with " + keyTypeDesc);

            if (reporter() != null) {
                reporter().publish(com.cryptocarver.model.OperationResult.forOperation("Generate Certificate - " + keyTypeDesc)
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", "CN=" + cn + ", Validity=" + validity + " days", com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", output.toString(), com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null)
                    ))
                    .build());
            }

        } catch (Exception e) {
            showError("Generation Error", "Error generating certificate: " + e.getMessage());
            LOG.warn("Certificate generation failed", e);
        }
    }

    void handleGenerateCSR() {
        try {
            String cn = view().certCNField().getText().trim();
            if (cn.isEmpty()) throw new IllegalArgumentException("Common Name (CN) is required");
            String selected = view().certKeyTypeCombo().getValue();
            CertificateGenerator.CertificateConfig config = new CertificateGenerator.CertificateConfig();
            config.commonName = cn;
            config.organization = view().certOrgField().getText().trim();
            config.organizationalUnit = view().certOUField().getText().trim();
            config.locality = view().certLocalityField().getText().trim();
            config.state = view().certStateField().getText().trim();
            config.country = view().certCountryField().getText().trim();
            config.email = view().certEmailField().getText().trim().isEmpty() ? null : view().certEmailField().getText().trim();
            config.signatureAlgorithm = view().certSignAlgoCombo().getValue();
            applySanConfiguration(config);

            String csrPem;
            String keyDesc = "";

            if ("Local PEM (Parse Area)".equals(selected)) {
                String pem = view().certInputArea() != null ? view().certInputArea().getText().trim() : "";
                if (pem.isEmpty()) throw new IllegalArgumentException("Please paste a private key in the 'Parse Certificate / Key' area");
                PrivateKey privateKey = KeysMaterialSupport.parsePrivateMaterial(pem);
                PublicKey publicKey = AsymmetricKeyOperations.derivePublicKey(privateKey);
                KeyPair pair = new KeyPair(publicKey, privateKey);
                csrPem = CertificateGenerator.generateCSR(pair, config);
                keyDesc = "Local PEM Key";
            } else if ("PKCS#11 Active Alias".equals(selected)) {
                String alias = signingAlias.get();
                csrPem = com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().requireSession().generateCsr(alias, config);
                keyDesc = "PKCS#11 Token (Alias: " + alias + ")";
            } else {
                KeyPair pair;
                if (selected.startsWith("RSA")) pair = AsymmetricKeyOperations.generateRSAKeyPair(Integer.parseInt(selected.substring(4)));
                else if (selected.startsWith("ECDSA")) pair = AsymmetricKeyOperations.generateECDSAFpKeyPair(selected.equals("ECDSA-P256") ? "secp256r1" : "secp384r1");
                else throw new IllegalArgumentException("Unsupported CSR key type");
                csrPem = CertificateGenerator.generateCSR(pair, config);
                keyDesc = "Generated " + selected + "\n\n=== PRIVATE KEY (LABORATORY ONLY) ===\n" + AsymmetricKeyOperations.exportPrivateKeyPEM(pair.getPrivate());
            }

            String outputText = "=== PKCS#10 CERTIFICATE SIGNING REQUEST ===\n\n" + csrPem
                    + "\n" + (keyDesc.startsWith("Generated") ? keyDesc : "Source: " + keyDesc);
            view().certOutputArea().setText(outputText);
            view().certOutputArea().setManaged(true);
            view().certOutputArea().setVisible(true);
            updateStatus("CSR generated with requested SANs");

            if (reporter() != null) {
                com.cryptocarver.model.OperationDetail.Classification cls = keyDesc.startsWith("Generated") ? com.cryptocarver.model.OperationDetail.Classification.SECRET : com.cryptocarver.model.OperationDetail.Classification.PUBLIC;
                reporter().publish(com.cryptocarver.model.OperationResult.forOperation("Generate CSR")
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Common Name", cn, com.cryptocarver.model.OperationDetail.Classification.PUBLIC, false, null),
                        new com.cryptocarver.model.OperationDetail("Source", keyDesc.startsWith("Generated") ? "Generated new pair" : keyDesc, cls, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", outputText, cls, false, null)
                    ))
                    .build());
            }
        } catch (Exception e) {
            showError("CSR Generation", "Cannot generate CSR: " + e.getMessage());
        }
    }

    void applySanConfiguration(CertificateGenerator.CertificateConfig config) {
        config.sanDnsNames = KeysMaterialSupport.commaSeparatedValues(view().certSanDnsField() == null ? null : view().certSanDnsField().getText());
        config.sanIpAddresses = KeysMaterialSupport.commaSeparatedValues(view().certSanIpField() == null ? null : view().certSanIpField().getText());
        config.addSubjectAlternativeNames = !config.sanDnsNames.isEmpty() || !config.sanIpAddresses.isEmpty();
    }

    void handleParseCertificate() {
        try {
            if (view().certInputArea() == null || view().certParseResultArea() == null) {
                updateStatus("Certificate parsing not initialized");
                return;
            }

            String pemCert = view().certInputArea().getText().trim();
            if (pemCert.isEmpty()) {
                showError(new UserFacingError("Missing Certificate Input", "Please paste a certificate in PEM format.", "Provide X.509 PEM certificate data in the input area.", "certInputArea"));
                return;
            }

            updateStatus("Parsing certificate...");

            // Parse certificate using CertificateGenerator
            X509Certificate cert = CertificateGenerator.parseCertificate(pemCert);

            // Get certificate info
            String certInfo = CertificateGenerator.getCertificateInfo(cert);

            StringBuilder output = new StringBuilder();
            output.append("=== CERTIFICATE INFORMATION ===\n\n");
            output.append(certInfo);

            view().certParseResultArea().setText(output.toString());
            view().certParseResultArea().setVisible(true);
            view().certParseResultArea().setManaged(true);

            updateStatus("Certificate parsed successfully");

            if (reporter() != null) {
                reporter().publish(com.cryptocarver.model.OperationResult.forOperation("Parse Certificate")
                    .enrichedOutput(output.toString(), com.cryptocarver.model.OperationDetail.Classification.PUBLIC)
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Subject", cert.getSubjectX500Principal().getName(), com.cryptocarver.model.OperationDetail.Classification.PUBLIC, false, null),
                        new com.cryptocarver.model.OperationDetail("Result", "Parsed successfully", com.cryptocarver.model.OperationDetail.Classification.PUBLIC, false, null)
                    ))
                    .status("Certificate parsed successfully")
                    .build());
            }

        } catch (Exception e) {
            view().certParseResultArea().setText("Error parsing certificate: " + e.getMessage());
            view().certParseResultArea().setVisible(true);
            view().certParseResultArea().setManaged(true);
            updateStatus("Certificate parse failed");
            showError(e, "Certificate Parse Error", "certInputArea");
        }
    }

    void handleValidateCertificate() {
        try {
            if (view().valCertInput() == null || view().valResultArea() == null) {
                // Not initialized
                return;
            }

            String certPem = view().valCertInput().getText().trim();
            if (certPem.isEmpty()) {
                showError(new UserFacingError("Missing Validation Certificate", "Please paste a certificate to validate.", "Provide X.509 PEM certificate data in the validation input field.", "valCertInput"));
                return;
            }

            String issuerPem = view().valIssuerInput().getText().trim();

            updateStatus("Validating certificate...");

            // Parse certificates
            List<X509Certificate> chain = null;
            try {
                chain = CertificateGenerator.parseCertificateChain(certPem);
            } catch (Exception e) {
                view().valResultArea().setText("Error parsing certificate chain: " + e.getMessage());
                updateStatus("Validation failed: Parse error");
                showError(e, "Certificate Chain Parse Error", "valCertInput");
                return;
            }

            if (chain == null || chain.isEmpty()) {
                view().valResultArea().setText("No certificates found in input.");
                return;
            }

            StringBuilder sb = new StringBuilder();
            boolean isValid = false;
            String statusReason = "";

            if (issuerPem.isEmpty()) {
                // Chain validation or single self-signed
                CertificateGenerator.ChainValidationResult result = CertificateGenerator.validateCertificateChain(chain);
                isValid = result.isValid;
                statusReason = result.message;

                sb.append("=== CHAIN VALIDATION RESULT ===\n");
                sb.append("Status: ").append(result.isValid ? "VALID ✅" : "INVALID ❌").append("\n");
                sb.append("Message: ").append(result.message).append("\n\n");
                sb.append("=== DETAILS ===\n");
                for (String detail : result.details) {
                    sb.append("• ").append(detail).append("\n");
                }
            } else {
                // Legacy validation against explicit issuer
                X509Certificate issuer;
                try {
                    issuer = CertificateGenerator.parseCertificate(issuerPem);
                } catch (Exception e) {
                    view().valResultArea().setText("Error parsing issuer certificate: " + e.getMessage());
                    updateStatus("Validation failed: Issuer parse error");
                    return;
                }

                CertificateGenerator.CertificateValidationResult result = CertificateGenerator.validateCertificate(chain.get(0), issuer);
                isValid = result.isValid;
                statusReason = result.status;

                sb.append("=== SINGLE CERTIFICATE VALIDATION RESULT ===\n");
                sb.append("Status: ").append(result.isValid ? "VALID ✅" : "INVALID ❌").append("\n");
                sb.append("Reason: ").append(result.status).append("\n");
                sb.append("Message: ").append(result.message).append("\n\n");
                sb.append("=== DETAILS ===\n");
                for (String detail : result.details) {
                    sb.append("• ").append(detail).append("\n");
                }
            }

            String outputText = sb.toString();
            view().valResultArea().setText(outputText);
            updateStatus(isValid ? "Certificate is valid" : "Certificate is invalid");

            if (reporter() != null) {
                reporter().publish(com.cryptocarver.model.OperationResult.forOperation("Validate Certificate")
                    .enrichedOutput(outputText, com.cryptocarver.model.OperationDetail.Classification.PUBLIC)
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", "Status: " + statusReason, com.cryptocarver.model.OperationDetail.Classification.PUBLIC, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", outputText, com.cryptocarver.model.OperationDetail.Classification.PUBLIC, false, null)
                    ))
                    .build());
            }

        } catch (Exception e) {
            view().valResultArea().setText("Error during validation: " + e.getMessage());
            updateStatus("Validation error");
            LOG.warn("Certificate validation failed", e);
        }
    }
}
