package com.cryptocarver.crypto;

import com.cryptocarver.service.RevocationValidationService;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cert.ocsp.*;
import org.bouncycastle.cert.ocsp.jcajce.JcaBasicOCSPRespBuilder;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigInteger;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** RFC 6960 §4.2.2.2: responses signed by a delegated responder the CA issued with id-kp-OCSPSigning. */
class LocalOcspDelegationTest {
    @TempDir Path temp;

    @Test void acceptsDelegatedResponderAndRejectsResponderWithoutOcspSigning() throws Exception {
        try (LocalPkiFixture pki = new LocalPkiFixture(temp.resolve("pki"))) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair responderKeys = generator.generateKeyPair();

            X509Certificate delegated = responder(pki, responderKeys, true);
            assertEquals(RevocationValidationService.Status.GOOD,
                    RevocationValidationService.classifyLocalOcsp(pki.signer, pki.intermediate,
                            List.of(response(pki, responderKeys, delegated, false))).status());
            assertEquals(RevocationValidationService.Status.REVOKED,
                    RevocationValidationService.classifyLocalOcsp(pki.signer, pki.intermediate,
                            List.of(response(pki, responderKeys, delegated, true))).status());

            X509Certificate notAResponder = responder(pki, responderKeys, false);
            assertEquals(RevocationValidationService.Status.UNKNOWN,
                    RevocationValidationService.classifyLocalOcsp(pki.signer, pki.intermediate,
                            List.of(response(pki, responderKeys, notAResponder, false))).status());
        }
    }

    private static X509Certificate responder(LocalPkiFixture pki, KeyPair keys, boolean ocspSigning) throws Exception {
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                new X500Name(pki.intermediate.getSubjectX500Principal().getName()), BigInteger.valueOf(ocspSigning ? 40 : 41),
                LocalPkiFixture.NOT_BEFORE, LocalPkiFixture.NOT_AFTER,
                new X500Name("CN=CryptoCarver Offline OCSP Responder"), keys.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.extendedKeyUsage, false, new ExtendedKeyUsage(
                ocspSigning ? KeyPurposeId.id_kp_OCSPSigning : KeyPurposeId.id_kp_clientAuth));
        return new JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(
                new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(pki.intermediateKeys.getPrivate())));
    }

    private static byte[] response(LocalPkiFixture pki, KeyPair responderKeys, X509Certificate responder,
                                   boolean revoked) throws Exception {
        DigestCalculator digest = new JcaDigestCalculatorProviderBuilder().setProvider("BC").build().get(CertificateID.HASH_SHA1);
        CertificateID id = new CertificateID(digest, new JcaX509CertificateHolder(pki.intermediate), pki.signer.getSerialNumber());
        BasicOCSPRespBuilder builder = new JcaBasicOCSPRespBuilder(responderKeys.getPublic(), digest);
        CertificateStatus status = revoked
                ? new RevokedStatus(new Date(System.currentTimeMillis() - 60_000L), 0)
                : CertificateStatus.GOOD;
        builder.addResponse(id, status, LocalPkiFixture.NOT_BEFORE, LocalPkiFixture.NOT_AFTER, null);
        BasicOCSPResp basic = builder.build(
                new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(responderKeys.getPrivate()),
                new X509CertificateHolder[]{new JcaX509CertificateHolder(responder)}, new Date());
        return new OCSPRespBuilder().build(OCSPResp.SUCCESSFUL, basic).getEncoded();
    }
}
