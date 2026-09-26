package com.cryptocarver.crypto;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.SignerInfoGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.tsp.*;
import org.bouncycastle.util.CollectionStore;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/** Shared local RFC 3161 primitives for CMS and PAdES tests. */
final class LocalTimestampAuthority {
    static final Date NOT_BEFORE = new Date(1_577_836_800_000L); // 2020-01-01
    static final Date NOT_AFTER = new Date(2_366_841_600_000L);  // 2045-01-01
    private static final ASN1ObjectIdentifier POLICY = new ASN1ObjectIdentifier("1.2.3.4.5");

    static TimeStampTokenGenerator tokenGenerator(KeyPair keys, X509CertificateHolder signerCertificate,
                                                   Collection<X509CertificateHolder> certificates) throws Exception {
        DigestCalculatorProvider calculators = new JcaDigestCalculatorProviderBuilder().setProvider("BC").build();
        SignerInfoGenerator signerInfo = new JcaSignerInfoGeneratorBuilder(calculators).build(
                new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(keys.getPrivate()), signerCertificate);
        DigestCalculator sha256 = calculators.get(new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256));
        TimeStampTokenGenerator generator = new TimeStampTokenGenerator(signerInfo, sha256, POLICY);
        generator.addCertificates(new CollectionStore<>(List.copyOf(certificates)));
        return generator;
    }

    static byte[] response(TimeStampRequest request, TimeStampTokenGenerator generator,
                           BigInteger serial, Date generationTime) throws Exception {
        return new TimeStampResponseGenerator(generator, TSPAlgorithms.ALLOWED)
                .generate(request, serial, generationTime).getEncoded();
    }

    /** Convenient signed response for tests that do not need a running HTTP TSA. */
    static GeneratedResponse forData(byte[] data) throws Exception {
        KeyPairGenerator keyGenerator = KeyPairGenerator.getInstance("RSA");
        keyGenerator.initialize(2048);
        KeyPair keys = keyGenerator.generateKeyPair();
        X500Name name = new X500Name("CN=Local Test TSA");
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(name, BigInteger.ONE,
                NOT_BEFORE, NOT_AFTER, name, keys.getPublic());
        builder.addExtension(Extension.extendedKeyUsage, true,
                new ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping));
        X509CertificateHolder holder = builder.build(new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider("BC").build(keys.getPrivate()));
        X509Certificate certificate = new JcaX509CertificateConverter().setProvider("BC").getCertificate(holder);
        TimeStampTokenGenerator tokenGenerator = tokenGenerator(keys, holder, List.of(holder));
        TimeStampRequestGenerator requests = new TimeStampRequestGenerator();
        requests.setCertReq(true);
        TimeStampRequest request = requests.generate(NISTObjectIdentifiers.id_sha256,
                java.security.MessageDigest.getInstance("SHA-256").digest(data));
        return new GeneratedResponse(response(request, tokenGenerator, BigInteger.TEN, new Date()), certificate);
    }

    record GeneratedResponse(byte[] response, X509Certificate certificate) { }

    private LocalTimestampAuthority() { }
}
