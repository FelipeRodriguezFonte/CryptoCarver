package com.cryptoforge.service;

import com.cryptoforge.model.SecretVisibility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.math.BigInteger;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public class KeystoreWorkbenchServiceTest {

    private static KeyCertificateFormatService service;
    private static byte[] pkcs12Bytes;
    private static byte[] jksBytes;
    private static byte[] bksBytes;

    private char[] getPassword() {
        return "password".toCharArray();
    }

    @BeforeAll
    public static void setup() throws Exception {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        service = new KeyCertificateFormatService();
        
        KeyPairGenerator rsaGen = KeyPairGenerator.getInstance("RSA");
        rsaGen.initialize(2048);
        KeyPair pair = rsaGen.generateKeyPair();
        
        long now = System.currentTimeMillis();
        Date startDate = new Date(now);
        Date endDate = new Date(now + 365L * 24 * 60 * 60 * 1000);
        X500Name name = new X500Name("CN=Test");
        
        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                name, BigInteger.valueOf(now), startDate, endDate, name, pair.getPublic());
                
        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption").build(pair.getPrivate());
        X509Certificate cert = new JcaX509CertificateConverter().setProvider("BC")
                .getCertificate(certBuilder.build(signer));
                
        Certificate[] chain = new Certificate[] { cert };
        
        // Generate PKCS12
        KeyStore p12 = KeyStore.getInstance("PKCS12");
        p12.load(null, null);
        p12.setKeyEntry("mykey", pair.getPrivate(), "password".toCharArray(), chain);
        p12.setCertificateEntry("mycert", cert);
        ByteArrayOutputStream p12Out = new ByteArrayOutputStream();
        p12.store(p12Out, "password".toCharArray());
        pkcs12Bytes = p12Out.toByteArray();
        
        // Generate JKS
        KeyStore jks = KeyStore.getInstance("JKS");
        jks.load(null, null);
        jks.setKeyEntry("mykey", pair.getPrivate(), "password".toCharArray(), chain);
        jks.setCertificateEntry("mycert", cert);
        ByteArrayOutputStream jksOut = new ByteArrayOutputStream();
        jks.store(jksOut, "password".toCharArray());
        jksBytes = jksOut.toByteArray();
        
        // Generate BKS
        KeyStore bks = KeyStore.getInstance("BKS", "BC");
        bks.load(null, null);
        bks.setKeyEntry("mykey", pair.getPrivate(), "password".toCharArray(), chain);
        bks.setCertificateEntry("mycert", cert);
        ByteArrayOutputStream bksOut = new ByteArrayOutputStream();
        bks.store(bksOut, "password".toCharArray());
        bksBytes = bksOut.toByteArray();
    }

    @Test
    public void testInspectPKCS12() throws Exception {
        KeyCertificateFormatService.DetectionResult res = service.inspectKeystore(pkcs12Bytes, "PKCS12", getPassword());
        assertEquals(KeyCertificateFormatService.FormatType.PKCS12, res.type);
        assertEquals(2, res.keystoreEntries.size());
        
        boolean foundKey = false;
        boolean foundCert = false;
        for (KeyCertificateFormatService.KeystoreEntrySummary entry : res.keystoreEntries) {
            if ("mykey".equals(entry.getAlias())) {
                assertEquals("PrivateKey", entry.getEntryType());
                assertEquals("RSA", entry.getAlgorithm());
                assertTrue(entry.getChainLength() > 0);
                foundKey = true;
            } else if ("mycert".equals(entry.getAlias())) {
                assertEquals("TrustedCert", entry.getEntryType());
                foundCert = true;
            }
        }
        assertTrue(foundKey);
        assertTrue(foundCert);
    }
    
    @Test
    public void testInspectJKS() throws Exception {
        KeyCertificateFormatService.DetectionResult res = service.inspectKeystore(jksBytes, "JKS", getPassword());
        assertEquals(KeyCertificateFormatService.FormatType.JKS, res.type);
        assertEquals(2, res.keystoreEntries.size());
    }
    
    @Test
    public void testInspectBKS() throws Exception {
        KeyCertificateFormatService.DetectionResult res = service.inspectKeystore(bksBytes, "BKS", getPassword());
        assertEquals(KeyCertificateFormatService.FormatType.BKS, res.type);
        assertEquals(2, res.keystoreEntries.size());
    }

    @Test
    public void testInspectWrongPassword() {
        assertThrows(Exception.class, () -> {
            service.inspectKeystore(pkcs12Bytes, "PKCS12", "wrong".toCharArray());
        });
    }

    @Test
    public void testExtractPublicCert() throws Exception {
        String exported = service.extractFromKeystore(jksBytes, "JKS", getPassword(), "mycert", "PEM Cert", SecretVisibility.MASKED);
        assertTrue(exported.contains("BEGIN CERTIFICATE"));
    }
    
    @Test
    public void testExtractPrivateKeyDeniedMasked() {
        Exception e = assertThrows(Exception.class, () -> {
            service.extractFromKeystore(jksBytes, "JKS", getPassword(), "mykey", "PEM Private", SecretVisibility.MASKED);
        });
        assertTrue(e.getMessage().contains("Private key export is blocked"));
    }
    
    @Test
    public void testExtractPrivateKeyAllowedFullLab() throws Exception {
        String exported = service.extractFromKeystore(jksBytes, "JKS", getPassword(), "mykey", "PEM Private", SecretVisibility.FULL_LAB);
        assertTrue(exported.contains("BEGIN PRIVATE KEY") || exported.contains("BEGIN RSA PRIVATE KEY"));
    }
}
