package com.cryptoforge.crypto;

import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class CertificateGeneratorCsrTest {
    @Test
    void csrCarriesSubjectAlternativeNameExtensionRequest() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var config = new CertificateGenerator.CertificateConfig();
        config.commonName = "service.example.test";
        config.sanDnsNames = java.util.List.of("service.example.test");
        config.sanIpAddresses = java.util.List.of("127.0.0.1");
        String pem = CertificateGenerator.generateCSR(generator.generateKeyPair(), config);
        String base64 = pem.replaceAll("-----[^-]+-----|\\s", "");
        PKCS10CertificationRequest csr = new PKCS10CertificationRequest(Base64.getDecoder().decode(base64));
        var attributes = csr.getAttributes(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest);
        Extensions extensions = Extensions.getInstance(attributes[0].getAttrValues().getObjectAt(0));
        assertNotNull(extensions.getExtension(Extension.subjectAlternativeName));
    }
}
