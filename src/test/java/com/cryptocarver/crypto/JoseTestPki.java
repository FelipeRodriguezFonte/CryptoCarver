package com.cryptocarver.crypto;

import com.nimbusds.jose.*;
import com.nimbusds.jose.util.Base64URL;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.jcajce.*;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import java.security.*;
import java.security.cert.X509Certificate;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;

/** Entirely invented test PKI; no production material. Generated DER never enters UI digests. */
public final class JoseTestPki {
    public static final Instant DATE = Instant.parse("2025-01-01T00:00:00Z");
    public final KeyPair rootKey = key(), intermediateKey = key(), leafKey = key(), otherKey = key();
    public final X509Certificate root, intermediate, leaf;
    public JoseTestPki() throws Exception {
        root = cert("Root", "Root", rootKey, rootKey, 2, true);
        intermediate = cert("Intermediate", "Root", intermediateKey, rootKey, 0, true);
        leaf = cert("Leaf", "Intermediate", leafKey, intermediateKey, -1, true);
    }
    private static KeyPair key() {
        try { var g=KeyPairGenerator.getInstance("RSA");g.initialize(2048);return g.generateKeyPair(); }
        catch(Exception e){throw new RuntimeException(e);}
    }
    public X509Certificate cert(String subject, String issuer, KeyPair own, KeyPair signing, int path, boolean signingUse) throws Exception {
        var bc = new org.bouncycastle.jce.provider.BouncyCastleProvider();
        var b = new JcaX509v3CertificateBuilder(new X500Name("CN=Invented73" + issuer), BigInteger.ONE,
                Date.from(Instant.parse("2024-01-01T00:00:00Z")), Date.from(Instant.parse("2030-01-01T00:00:00Z")),
                new X500Name("CN=Invented73" + subject), own.getPublic());
        b.addExtension(Extension.basicConstraints, true, path < 0 ? new BasicConstraints(false) : new BasicConstraints(path));
        b.addExtension(Extension.keyUsage, true, new KeyUsage(path < 0 ? signingUse ? KeyUsage.digitalSignature : KeyUsage.keyEncipherment : KeyUsage.keyCertSign));
        b.addExtension(Extension.authorityInfoAccess, false, new AuthorityInformationAccess(AccessDescription.id_ad_caIssuers,
                new GeneralName(GeneralName.uniformResourceIdentifier, "http://127.0.0.1:9/invented-no-network")));
        return new JcaX509CertificateConverter().setProvider(bc).getCertificate(b.build(new JcaContentSignerBuilder("SHA256withRSA").setProvider(bc).build(signing.getPrivate())));
    }
    public List<X509Certificate> chain(){return List.of(leaf,intermediate,root);}
    public String anchors() throws Exception{return pem("CERTIFICATE",root.getEncoded());}
    public String publicPem(){return pem("PUBLIC KEY",leafKey.getPublic().getEncoded());}
    public String privatePem(){return pem("PRIVATE KEY",leafKey.getPrivate().getEncoded());}
    public JWSHeader header(List<X509Certificate> chain) throws Exception {
        var c=new ArrayList<com.nimbusds.jose.util.Base64>();for(var cert:chain)c.add(com.nimbusds.jose.util.Base64.encode(cert.getEncoded()));
        return new JWSHeader.Builder(JWSAlgorithm.RS256).x509CertChain(c)
                .x509CertThumbprint(Base64URL.encode(MessageDigest.getInstance("SHA-1").digest(chain.get(0).getEncoded())))
                .x509CertSHA256Thumbprint(Base64URL.encode(MessageDigest.getInstance("SHA-256").digest(chain.get(0).getEncoded())))
                .jwkURL(java.net.URI.create("http://127.0.0.1:9/invented-jku")).x509CertURL(java.net.URI.create("http://127.0.0.1:9/invented-x5u")).build();
    }
    public String token(List<X509Certificate> chain) throws Exception {
        var object=new JWSObject(header(chain), new Payload("{\"sub\":\"invented-pkix-73\"}"));
        object.sign(new com.nimbusds.jose.crypto.RSASSASigner(leafKey.getPrivate()));return object.serialize();
    }
    public static String pem(String type,byte[] bytes){return "-----BEGIN "+type+"-----\n"+Base64.getMimeEncoder(64,new byte[]{'\n'}).encodeToString(bytes)+"\n-----END "+type+"-----";}
}
