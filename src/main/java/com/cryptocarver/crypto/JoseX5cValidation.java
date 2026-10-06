package com.cryptocarver.crypto;

import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.jwk.*;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.cert.*;
import java.time.Instant;
import java.util.*;

/** Offline validation of the supplied x5c path. No path building or remote certificate stores. */
public final class JoseX5cValidation {
    private JoseX5cValidation() { }
    public record Result(boolean trusted, Map<String, String> checks) {
        public Result { checks = Collections.unmodifiableMap(new LinkedHashMap<>(checks)); }
    }
    public static Result validate(JWSHeader header, String verificationKey, boolean trustHeaderKey,
            String anchorsPem, Instant date) {
        Map<String,String> checks = new LinkedHashMap<>();
        for (String name : List.of("Formation", "Validity", "CA and path length", "Leaf signing usage", "Verification key", "x5t", "x5t#S256", "PKIX"))
            checks.put(name, "NOT_CHECKED");
        checks.put("Revocation", "DISABLED");
        List<X509Certificate> chain = new ArrayList<>();
        try {
            if (header.getX509CertChain() == null || header.getX509CertChain().isEmpty()) {
                checks.put("Formation", "MISSING"); return new Result(false, checks);
            }
            for (var encoded : header.getX509CertChain()) chain.add(CertificateGenerator.parseCertificate(encoded.decode()));
        } catch (Exception invalid) {
            checks.put("Formation", "FAIL"); checks.put("PKIX", "FAIL"); checks.put("PKIX reason", "MALFORMED_CHAIN");
            return new Result(false, checks);
        }
        Instant when = date == null ? Instant.now() : date;
        boolean formation = true, signatures = true, valid = true, constraints = true;
        for (int i=0; i<chain.size(); i++) {
            X509Certificate certificate=chain.get(i);
            try { certificate.checkValidity(Date.from(when)); } catch (Exception expired) { valid=false; }
            if (i>0) {
                int caBelow=0;for(int j=1;j<i;j++) if(chain.get(j).getBasicConstraints()>=0) caBelow++;
                boolean[] usage=certificate.getKeyUsage();
                constraints &= certificate.getBasicConstraints()>=caBelow && (usage==null || usage.length>5 && usage[5]);
            }
            if(i+1<chain.size()) {
                formation &= certificate.getIssuerX500Principal().equals(chain.get(i+1).getSubjectX500Principal());
                boolean link = verifies(certificate,chain.get(i+1).getPublicKey());
                checks.put("Signature link " + (i+1), status(link));signatures &= link;
            }
        }
        X509Certificate lastCertificate=chain.get(chain.size()-1);
        if(lastCertificate.getIssuerX500Principal().equals(lastCertificate.getSubjectX500Principal()))
            checks.put("Root self-signature",status(verifies(lastCertificate,lastCertificate.getPublicKey())));
        checks.put("Formation",status(formation)); checks.put("Validity",status(valid));
        checks.put("CA and path length",status(constraints));
        X509Certificate leaf=chain.get(0);boolean[] usage=leaf.getKeyUsage();
        boolean signingUsage=usage==null || usage.length>0 && usage[0] || usage.length>1 && usage[1];
        checks.put("Leaf signing usage",status(signingUsage));
        boolean keyMatches=false;
        try {
            String key=verificationKey;
            if ((key==null || key.isBlank()) && trustHeaderKey) {
                key=header.getJWK()!=null ? header.getJWK().toPublicJWK().toJSONString() : certificatePem(leaf);
            }
            if(key!=null && !key.isBlank()) {
                if(key.trim().startsWith("{") && key.contains("\"keys\"")) {
                    List<JWK> keys=JWKSet.parse(key).getKeys();
                    JWK selected=header.getKeyID()!=null ? keys.stream().filter(k->header.getKeyID().equals(k.getKeyID())).findFirst().orElseThrow()
                            : keys.stream().filter(k->header.getAlgorithm().equals(k.getAlgorithm())).findFirst().orElse(keys.isEmpty()?null:keys.get(0));
                    key=Objects.requireNonNull(selected).toPublicJWK().toJSONString();
                }
                keyMatches=MessageDigest.isEqual(leaf.getPublicKey().getEncoded(),JoseKeyMaterial.publicKey(key).getEncoded());
                checks.put("Verification key",status(keyMatches));
            }
        } catch(Exception invalidKey) { checks.put("Verification key","FAIL"); }
        boolean thumb1=thumb(header.getX509CertThumbprint(),leaf,"SHA-1",checks,"x5t");
        boolean thumb256=thumb(header.getX509CertSHA256Thumbprint(),leaf,"SHA-256",checks,"x5t#S256");
        boolean pkix=false;
        try {
            if(anchorsPem==null || anchorsPem.isBlank()) { checks.put("PKIX","NO_ANCHORS"); }
            else {
                List<X509Certificate> roots=CertificateGenerator.parseCertificateChain(anchorsPem);
                if(roots.isEmpty()) throw new IllegalArgumentException();
                Set<TrustAnchor> anchors=new LinkedHashSet<>();
                for(var root:roots) {
                    boolean[] rootUsage=root.getKeyUsage();
                    if(root.getBasicConstraints()<0 || rootUsage!=null && (rootUsage.length<=5 || !rootUsage[5])) throw new IllegalArgumentException();
                    anchors.add(new TrustAnchor(root,null));
                }
                List<X509Certificate> path=new ArrayList<>(chain);
                X509Certificate last=path.get(path.size()-1);
                if(roots.contains(last)) path.remove(path.size()-1);
                PKIXParameters parameters=new PKIXParameters(anchors);
                parameters.setDate(Date.from(when));parameters.setRevocationEnabled(false);
                var result=(PKIXCertPathValidatorResult)CertPathValidator.getInstance("PKIX").validate(
                        CertificateFactory.getInstance("X.509").generateCertPath(path),parameters);
                X509Certificate anchor=result.getTrustAnchor().getTrustedCert();
                anchor.checkValidity(Date.from(when));
                int caBelow=0;for(int j=1;j<path.size();j++) if(path.get(j).getBasicConstraints()>=0) caBelow++;
                constraints &= anchor.getBasicConstraints()>=caBelow;
                checks.put("CA and path length",status(constraints));
                if(!path.isEmpty()) {
                    boolean link=verifies(path.get(path.size()-1),anchor.getPublicKey());
                    checks.put("Signature to anchor",status(link));signatures &= link;
                }
                pkix=constraints && valid && formation && signatures;
                checks.put("PKIX",status(pkix));
            }
        } catch(CertPathValidatorException invalid) {
            checks.put("PKIX","FAIL"); checks.put("PKIX reason",reason(invalid));
        } catch(CertificateExpiredException | CertificateNotYetValidException invalidDate) {
            checks.put("Validity","FAIL"); checks.put("PKIX","FAIL"); checks.put("PKIX reason","ANCHOR_VALIDITY");
        } catch(Exception invalidAnchors) {
            checks.put("PKIX","FAIL"); checks.put("PKIX reason","INVALID_ANCHORS");
        }
        return new Result(pkix && signingUsage && keyMatches && thumb1 && thumb256,checks);
    }
    private static String reason(CertPathValidatorException e) {
        if(e.getReason()==CertPathValidatorException.BasicReason.EXPIRED) return "EXPIRED";
        if(e.getReason()==CertPathValidatorException.BasicReason.NOT_YET_VALID) return "NOT_YET_VALID";
        if(e.getReason()==CertPathValidatorException.BasicReason.INVALID_SIGNATURE) return "INVALID_SIGNATURE";
        if(e.getReason()==PKIXReason.NOT_CA_CERT) return "INVALID_CA";
        if(e.getReason()==PKIXReason.PATH_TOO_LONG) return "PATH_LENGTH";
        if(e.getReason()==PKIXReason.NO_TRUST_ANCHOR) return "NO_TRUST_ANCHOR";
        return "PKIX_FAILED";
    }
    private static boolean verifies(X509Certificate cert,PublicKey issuer) {
        try { cert.verify(issuer);return true; }catch(Exception invalid){return false;}
    }
    private static boolean thumb(com.nimbusds.jose.util.Base64URL expected,X509Certificate leaf,String algorithm,Map<String,String> checks,String name) {
        if(expected==null){checks.put(name,"NOT_PRESENT");return true;}
        try {boolean match=MessageDigest.isEqual(expected.decode(),MessageDigest.getInstance(algorithm).digest(leaf.getEncoded()));checks.put(name,status(match));return match;}
        catch(Exception invalid){checks.put(name,"FAIL");return false;}
    }
    private static String status(boolean value){return value?"PASS":"FAIL";}
    private static String certificatePem(X509Certificate cert) throws Exception {
        return "-----BEGIN CERTIFICATE-----\n"+Base64.getMimeEncoder(64,new byte[]{'\n'}).encodeToString(cert.getEncoded())+"\n-----END CERTIFICATE-----";
    }
}
