package com.cryptocarver.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RsaPaddingLimitsTest {

    @Test
    void pkcs1LeavesElevenBytesOfOverhead() {
        assertEquals(245, RsaPaddingLimits.maxPlaintextBytes(2048, "RSA/ECB/PKCS1Padding"));
    }

    @Test
    void oaepOverheadDependsOnTheDigest() {
        assertEquals(214, RsaPaddingLimits.maxPlaintextBytes(2048, "RSA/ECB/OAEPWithSHA-1AndMGF1Padding"));
        assertEquals(190, RsaPaddingLimits.maxPlaintextBytes(2048, "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"));
    }

    @Test
    void smallerModulusShrinksTheLimit() {
        assertEquals(117, RsaPaddingLimits.maxPlaintextBytes(1024, "RSA/ECB/PKCS1Padding"));
        assertEquals(62, RsaPaddingLimits.maxPlaintextBytes(1024, "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"));
    }

    @Test
    void publicHelpSelectorCodesUseTheSameRsaLimits() {
        assertEquals(245, RsaPaddingLimits.maxPlaintextBytes(2048, "rsa_ecb_pkcs1padding"));
        assertEquals(214, RsaPaddingLimits.maxPlaintextBytes(2048, "rsa_ecb_oaepwithsha_1andmgf1padding"));
        assertEquals(190, RsaPaddingLimits.maxPlaintextBytes(2048, "rsa_ecb_oaepwithsha_256andmgf1padding"));
    }

    @Test
    void oaepTransformationsExposeTheirEffectiveDigestAndMgf1Defaults() throws Exception {
        java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        var generator = java.security.KeyPairGenerator.getInstance("RSA", "BC");
        generator.initialize(1024);
        var pair = generator.generateKeyPair();
        assertOaepParameters(pair, "RSA/ECB/OAEPWithSHA-1AndMGF1Padding", "SHA-1", "SHA-1");
        assertOaepParameters(pair, "RSA/ECB/OAEPWithSHA-256AndMGF1Padding", "SHA-256", "SHA-256");
    }

    private static void assertOaepParameters(java.security.KeyPair pair, String transformation,
            String digest, String mgfDigest) throws Exception {
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance(transformation, "BC");
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, pair.getPublic());
        javax.crypto.spec.OAEPParameterSpec parameters = cipher.getParameters()
                .getParameterSpec(javax.crypto.spec.OAEPParameterSpec.class);
        assertEquals(digest, parameters.getDigestAlgorithm());
        assertEquals("MGF1", parameters.getMGFAlgorithm());
        assertTrue(parameters.getMGFParameters() instanceof java.security.spec.MGF1ParameterSpec);
        assertEquals(mgfDigest, ((java.security.spec.MGF1ParameterSpec) parameters.getMGFParameters()).getDigestAlgorithm());
    }
}
