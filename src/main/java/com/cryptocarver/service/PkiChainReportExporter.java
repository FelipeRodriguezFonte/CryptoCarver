package com.cryptocarver.service;

import com.cryptocarver.crypto.CertificateLinter;
import com.cryptocarver.crypto.EidasCertificateInspector;
import com.cryptocarver.model.BuildInfo;

import java.io.File;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.*;
import java.util.*;

/** Builds a reproducible laboratory Markdown report from caller supplied PKI material. */
public final class PkiChainReportExporter {
    private PkiChainReportExporter() { }

    public static String export(List<X509Certificate> chain, KeyStore trustStore, List<X509CRL> crls,
                                List<byte[]> ocsps, boolean online) throws Exception {
        if (chain == null || chain.isEmpty()) throw new IllegalArgumentException("Certificate chain is required");
        StringBuilder md = new StringBuilder("# PKI chain report\n\n")
                .append("Generated: ").append(java.time.Instant.now()).append("\n\n")
                .append("CryptoCarver version: ").append(BuildInfo.version()).append("\n\n")
                .append("> Laboratory report; this is not a qualified validation.\n\n");
        for (int i = 0; i < chain.size(); i++) {
            X509Certificate c = chain.get(i);
            md.append("## Certificate ").append(i + 1).append("\n\n")
              .append("- Subject: ").append(c.getSubjectX500Principal()).append("\n")
              .append("- Issuer: ").append(c.getIssuerX500Principal()).append("\n")
              .append("- Serial: ").append(c.getSerialNumber().toString(16)).append("\n")
              .append("- Validity: ").append(c.getNotBefore().toInstant()).append(" – ").append(c.getNotAfter().toInstant()).append("\n")
              .append("- Signature algorithm: ").append(c.getSigAlgName()).append("\n")
              .append("- Public key: ").append(c.getPublicKey().getAlgorithm()).append(" ").append(keySize(c)).append(" bits\n")
              .append("- KeyUsage: ").append(Arrays.toString(c.getKeyUsage())).append("\n")
              .append("- EKU: ").append(c.getExtendedKeyUsage()).append("\n")
              .append("- BasicConstraints: ").append(c.getBasicConstraints()).append("\n")
              .append("- SKI: ").append(keyIdentifier(c, true)).append("\n")
              .append("- AKI: ").append(keyIdentifier(c, false)).append("\n")
              .append("- SHA-256: ").append(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(c.getEncoded()))).append("\n\n")
              .append("### X.509 linter\n\n");
            for (var finding : CertificateLinter.lint(c)) md.append("- ").append(finding.severity()).append(": ").append(finding.message()).append("\n");
            if (c.getExtensionValue("1.3.6.1.5.5.7.1.3") != null) {
                var report = EidasCertificateInspector.inspect(c);
                md.append("\n### eIDAS certificate inspection\n\n");
                for (var finding : report.findings()) md.append("- ").append(finding.severity()).append(": ").append(finding.message()).append("\n");
            }
            md.append("\n");
        }
        PathAssessment path = assessPath(chain, trustStore);
        md.append("## Certification path\n\nAnchored in truststore: **").append(path.anchored() ? "yes" : "no").append("** — ")
          .append(path.reason())
          .append("\n\n## Revocation\n\nOnline retrieval: **").append(online ? "enabled" : "disabled").append("**\n\n");
        for (int i=0;i<chain.size();i++) {
            X509Certificate c=chain.get(i); RevocationValidationService.Status status=RevocationValidationService.Status.UNKNOWN;
            String source="none";
            X509Certificate issuer=chain.stream().filter(x->x.getSubjectX500Principal().equals(c.getIssuerX500Principal())).findFirst().orElse(null);
            if (crls != null && !crls.isEmpty() && issuer!=null) {
                var result=RevocationValidationService.classifyLocalCrls(c,issuer,crls);
                status=result.status();
                if (result.evidence()==RevocationValidationService.Evidence.LOCAL) source="local CRL";
            }
            if (ocsps != null && !ocsps.isEmpty() && issuer!=null) {
                var result=RevocationValidationService.classifyLocalOcsp(c,issuer,ocsps);
                if (result.status()==RevocationValidationService.Status.REVOKED ||
                        (status==RevocationValidationService.Status.UNKNOWN && result.status()==RevocationValidationService.Status.GOOD)) {
                    status=result.status();
                    source="local OCSP";
                } else if (source.equals("none") && result.evidence()==RevocationValidationService.Evidence.LOCAL) {
                    source="local OCSP";
                }
            }
            if (source.equals("none") && online) source="online source requested; no network lookup performed by default exporter";
            md.append("- ").append(c.getSubjectX500Principal()).append(": **").append(status).append("** (source: ").append(source).append(")\n");
        }
        return md.toString();
    }

    public static List<X509Certificate> readPem(File file) throws Exception {
        try (var in=Files.newInputStream(file.toPath())) { return List.copyOf((Collection<X509Certificate>)CertificateFactory.getInstance("X.509").generateCertificates(in)); }
    }
    public static KeyStore readTrustStore(File file, char[] password) throws Exception {
        KeyStore store=KeyStore.getInstance("PKCS12"); try(var in=Files.newInputStream(file.toPath())) { store.load(in,password); } return store;
    }
    private record PathAssessment(boolean anchored, String reason) { }

    private static PathAssessment assessPath(List<X509Certificate> chain, KeyStore store) {
        if(store==null)return new PathAssessment(false,"no truststore was supplied.");
        try {
            Set<TrustAnchor> anchors=new HashSet<>(); var aliases=store.aliases(); while(aliases.hasMoreElements()) { var cert=store.getCertificate(aliases.nextElement()); if(cert instanceof X509Certificate x)anchors.add(new TrustAnchor(x,null)); }
            if (anchors.isEmpty()) return new PathAssessment(false,"the truststore contains no X.509 trust anchors.");
            X509CertSelector selector=new X509CertSelector(); selector.setCertificate(chain.get(0));
            var params=new PKIXBuilderParameters(anchors,selector); params.setRevocationEnabled(false);
            var certs=new ArrayList<X509Certificate>(chain);
            params.addCertStore(CertStore.getInstance("Collection",new CollectionCertStoreParameters(certs)));
            CertPathBuilder.getInstance("PKIX").build(params);
            return new PathAssessment(true,"PKIX path building reached a configured trust anchor.");
        } catch(Exception e) {
            return new PathAssessment(false,"PKIX path building failed: " + e.getMessage());
        }
    }
    private static int keySize(X509Certificate c) { var key=c.getPublicKey(); if(key instanceof java.security.interfaces.RSAKey k)return k.getModulus().bitLength(); if(key instanceof java.security.interfaces.ECKey k)return k.getParams().getOrder().bitLength(); return -1; }
    private static String keyIdentifier(X509Certificate certificate, boolean subject) throws Exception {
        byte[] extension = certificate.getExtensionValue(subject ? "2.5.29.14" : "2.5.29.35");
        if (extension == null) return "absent";
        byte[] value = org.bouncycastle.asn1.ASN1OctetString.getInstance(extension).getOctets();
        byte[] identifier = subject
                ? org.bouncycastle.asn1.x509.SubjectKeyIdentifier.getInstance(value).getKeyIdentifier()
                : org.bouncycastle.asn1.x509.AuthorityKeyIdentifier.getInstance(value).getKeyIdentifier();
        return identifier == null ? "absent" : HexFormat.of().formatHex(identifier);
    }
}
