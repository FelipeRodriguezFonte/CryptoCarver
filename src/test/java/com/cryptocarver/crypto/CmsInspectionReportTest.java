package com.cryptocarver.crypto;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

class CmsInspectionReportTest {

    @Test
    void testImmutabilityOfSignerInfoSummary() {
        Map<String, String> signedAttr = new HashMap<>();
        signedAttr.put("key1", "val1");

        Map<String, String> unsignedAttr = new HashMap<>();
        unsignedAttr.put("key2", "val2");

        CmsInspectionReport.SignerInfoSummary summary = new CmsInspectionReport.SignerInfoSummary(
                "sid", "digest", "encryption",
                CmsInspectionReport.ValidationState.VALID, CmsInspectionReport.ValidationState.VALID,
                signedAttr, unsignedAttr
        );

        assertThrows(UnsupportedOperationException.class, () -> summary.getSignedAttributes().put("key3", "val3"));
        assertThrows(UnsupportedOperationException.class, () -> summary.getUnsignedAttributes().put("key4", "val4"));
    }

    @Test
    void testImmutabilityOfCertificateSummary() {
        List<String> keyUsages = new ArrayList<>();
        keyUsages.add("digitalSignature");

        CmsInspectionReport.CertificateSummary summary = new CmsInspectionReport.CertificateSummary(
                "subject", "issuer", "serial", "notBefore", "notAfter", "alg", "fingerprint", keyUsages
        );

        assertThrows(UnsupportedOperationException.class, () -> summary.getKeyUsages().add("keyEncipherment"));
    }

    @Test
    void testImmutabilityOfCmsInspectionReport() {
        List<CmsInspectionReport.SignerInfoSummary> signers = new ArrayList<>();
        List<CmsInspectionReport.RecipientSummary> recipients = new ArrayList<>();
        List<CmsInspectionReport.CertificateSummary> certificates = new ArrayList<>();
        List<CmsInspectionReport.ValidationStep> steps = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        CmsInspectionReport report = new CmsInspectionReport(
                CmsInspectionReport.CmsContentType.SIGNED_DATA, "oid", "name",
                CmsInspectionReport.ContentState.ENCAPSULATED, 100L, "alg",
                signers, recipients, certificates, steps, warnings
        );

        assertThrows(UnsupportedOperationException.class, () -> report.getSigners().add(null));
        assertThrows(UnsupportedOperationException.class, () -> report.getRecipients().add(null));
        assertThrows(UnsupportedOperationException.class, () -> report.getCertificates().add(null));
        assertThrows(UnsupportedOperationException.class, () -> report.getValidationSteps().add(null));
        assertThrows(UnsupportedOperationException.class, () -> report.getWarnings().add("new warning"));
    }
}
