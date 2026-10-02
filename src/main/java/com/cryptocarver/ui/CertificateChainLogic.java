package com.cryptocarver.ui;

import com.cryptocarver.crypto.CertificateGenerator;
import com.cryptocarver.crypto.RevocationOperations;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/** Pure PEM parsing, PKIX validation and report construction. */
final class CertificateChainLogic {
    private CertificateChainLogic() { }
    record Validation(CertificateGenerator.ChainValidationResult result, int chainLength, String outputText) { }

    static Validation validate(String chainText, String crlText) throws Exception {
        String chainStr = chainText.trim();
        if (chainStr.isEmpty()) {
            throw new KeysInputValidation("Input Error", "Certificate Chain PEM is required", "");
        }
        // Extract multiple certificates from PEM sequence
        List<String> pemCerts = new ArrayList<>();
        String[] parts = chainStr.split("-----BEGIN CERTIFICATE-----");

        for (String part : parts) {
            if (part.trim().isEmpty())
                continue;
            String pem = "-----BEGIN CERTIFICATE-----" + part;
            int endIndex = pem.indexOf("-----END CERTIFICATE-----");
            if (endIndex != -1) {
                pem = pem.substring(0, endIndex + 25);
                pemCerts.add(pem);
            }
        }

        if (pemCerts.isEmpty()) {
            throw new KeysInputValidation("Input Error", "No valid PEM certificates found", "");
        }

        List<X509Certificate> chain = new ArrayList<>();
        for (String pem : pemCerts) {
            chain.add(CertificateGenerator.parseCertificate(pem));
        }

        List<java.security.cert.X509CRL> crls = null;
        if (crlText != null && !crlText.trim().isEmpty()) {
            crls = new ArrayList<>();
            String crlsStr = crlText.trim();
            String[] crlParts = crlsStr.split("-----BEGIN X509 CRL-----");
            for (String part : crlParts) {
                if (part.trim().isEmpty()) continue;
                String pem = "-----BEGIN X509 CRL-----" + part;
                int endIndex = pem.indexOf("-----END X509 CRL-----");
                if (endIndex != -1) {
                    pem = pem.substring(0, endIndex + 22);
                    crls.add(RevocationOperations.parseCrlPem(pem));
                }
            }
        }

        // Validate
        CertificateGenerator.ChainValidationResult result = CertificateGenerator.validateCertificateChain(chain, crls);

        StringBuilder sb = new StringBuilder();
        sb.append("CHAIN VALIDATION: ").append(result.isValid ? "✅ VALID" : "❌ INVALID").append("\n\n");

        if (result.message != null) {
            sb.append("Message: ").append(result.message).append("\n\n");
        }

        sb.append("DETAILS:\n");
        for (String detail : result.details) {
            sb.append("- ").append(detail).append("\n");
        }

        String outputText = sb.toString();
        return new Validation(result, chain.size(), outputText);
    }
}
