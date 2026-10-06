package com.cryptocarver.crypto;

import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.util.Base64URL;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class JoseX5cValidationTest {
    static JoseTestPki p;
    @BeforeAll static void setup() throws Exception { p=new JoseTestPki(); }
    private JoseX5cValidation.Result validate(List<java.security.cert.X509Certificate> chain,String anchors,Instant when) throws Exception {
        return JoseX5cValidation.validate(p.header(chain),p.publicPem(),false,anchors,when);
    }
    @Test void trustedPathWithRootPresentOrOmittedAndMultipleAnchors() throws Exception {
        for(var chain:List.of(p.chain(),List.of(p.leaf,p.intermediate))) {
            var result=validate(chain,p.anchors(),JoseTestPki.DATE);
            assertTrue(result.trusted(),result.checks().toString());assertEquals("DISABLED",result.checks().get("Revocation"));
            for(String check:List.of("Formation","Validity","CA and path length","Leaf signing usage","Verification key","x5t","x5t#S256","PKIX")) assertEquals("PASS",result.checks().get(check),check);
        }
        var otherRoot=p.cert("Other","Other",p.otherKey,p.otherKey,2,true);
        assertTrue(validate(p.chain(),JoseTestPki.pem("CERTIFICATE",otherRoot.getEncoded())+"\n"+p.anchors(),JoseTestPki.DATE).trusted());
    }
    @Test void absentWrongMalformedAndNonCaAnchors() throws Exception {
        assertEquals("NO_ANCHORS",validate(p.chain(),"",JoseTestPki.DATE).checks().get("PKIX"));
        assertFalse(validate(p.chain(),JoseTestPki.pem("CERTIFICATE",p.leaf.getEncoded()),JoseTestPki.DATE).trusted());
        assertEquals("INVALID_ANCHORS",validate(p.chain(),"invented-invalid-PEM",JoseTestPki.DATE).checks().get("PKIX reason"));
        var wrong=p.cert("Other","Other",p.otherKey,p.otherKey,2,true);
        assertFalse(validate(p.chain(),JoseTestPki.pem("CERTIFICATE",wrong.getEncoded()),JoseTestPki.DATE).trusted());
    }
    @Test void validityAndLeafUsageAndKeyBindingAreSeparate() throws Exception {
        for(var date:List.of(Instant.parse("2023-01-01T00:00:00Z"),Instant.parse("2031-01-01T00:00:00Z"))) {
            var result=validate(p.chain(),p.anchors(),date);assertEquals("FAIL",result.checks().get("Validity"));assertFalse(result.trusted());
        }
        var badLeaf=p.cert("Leaf","Intermediate",p.leafKey,p.intermediateKey,-1,false);
        var badUse=validate(List.of(badLeaf,p.intermediate,p.root),p.anchors(),JoseTestPki.DATE);
        assertEquals("FAIL",badUse.checks().get("Leaf signing usage"));assertFalse(badUse.trusted());
        var wrongKey=JoseX5cValidation.validate(p.header(p.chain()),JoseTestPki.pem("PUBLIC KEY",p.otherKey.getPublic().getEncoded()),false,p.anchors(),JoseTestPki.DATE);
        assertEquals("PASS",wrongKey.checks().get("PKIX"));assertEquals("FAIL",wrongKey.checks().get("Verification key"));assertFalse(wrongKey.trusted());
    }
    @Test void issuerSignaturesCaAndPathLengthAreSeparateAndExplicitFallbackWorks() throws Exception {
        var badIntermediate=p.cert("Intermediate","Root",p.intermediateKey,p.otherKey,0,true);
        var badChain=List.of(p.leaf,badIntermediate,p.root);
        var bad=validate(badChain,p.anchors(),JoseTestPki.DATE);
        assertEquals("PASS",bad.checks().get("Formation"));assertEquals("FAIL",bad.checks().get("Signature link 2"));assertFalse(bad.trusted());
        String token=p.token(badChain);
        var options=new JwtValidator.Options(null,null,0,false,false,JoseKeyMaterial.SecretEncoding.UTF8,false,true);
        assertTrue(JwtValidator.validate(token,"",options,JoseTestPki.DATE).signatureValid(),"Explicit x5c key remains usable despite invalid chain");
        assertThrows(Exception.class,()->JwtValidator.validate(token,"",new JwtValidator.Options(null,null,0,false,false,JoseKeyMaterial.SecretEncoding.UTF8),JoseTestPki.DATE));
        var nonCa=p.cert("Intermediate","Root",p.intermediateKey,p.rootKey,-1,true);
        assertEquals("FAIL",validate(List.of(p.leaf,nonCa,p.root),p.anchors(),JoseTestPki.DATE).checks().get("CA and path length"));
        var shortRoot=p.cert("Root","Root",p.rootKey,p.rootKey,0,true);
        var length=validate(List.of(p.leaf,p.intermediate,shortRoot),JoseTestPki.pem("CERTIFICATE",shortRoot.getEncoded()),JoseTestPki.DATE);
        assertEquals("FAIL",length.checks().get("CA and path length"));assertFalse(length.trusted());
        assertEquals("FAIL",validate(List.of(p.leaf,p.root,p.intermediate),p.anchors(),JoseTestPki.DATE).checks().get("Formation"));
    }
    @Test void thumbprintsAndMalformedChainHaveStableCodes() throws Exception {
        var h=new JWSHeader.Builder(p.header(p.chain())).x509CertThumbprint(Base64URL.encode(new byte[20])).x509CertSHA256Thumbprint(Base64URL.encode(new byte[32])).build();
        var result=JoseX5cValidation.validate(h,p.publicPem(),false,p.anchors(),JoseTestPki.DATE);
        assertEquals("FAIL",result.checks().get("x5t"));assertEquals("FAIL",result.checks().get("x5t#S256"));assertFalse(result.trusted());
        var missing=new JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.RS256).x509CertChain(List.of(com.nimbusds.jose.util.Base64.encode("invented-bad-cert"))).build();
        assertEquals("MALFORMED_CHAIN",JoseX5cValidation.validate(missing,p.publicPem(),false,p.anchors(),JoseTestPki.DATE).checks().get("PKIX reason"));
    }
}
