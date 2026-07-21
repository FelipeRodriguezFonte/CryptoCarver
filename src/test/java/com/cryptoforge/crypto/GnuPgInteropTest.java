package com.cryptoforge.crypto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class GnuPgInteropTest {

    @Test
    void parsesGnuPgVersionWithoutExecutingUserConfiguredCommands() {
        assertTrue(GnuPgInterop.parseVersion("gpg (GnuPG) 2.4.7\\nlibgcrypt 1.11.0").contains("2.4.7"));
        assertNull(GnuPgInterop.parseVersion(null));
    }

    @Test
    void reportsUnavailableExecutableAsStructuredStatus() {
        GnuPgInterop.Availability availability = GnuPgInterop.probe("cryptocarver-gpg-definitely-not-installed");
        assertFalse(availability.available());
        assertTrue(availability.message().contains("not available"));
    }

    @Test
    void exercisesBidirectionalInteropWhenLocalGnuPgIsAvailable() throws Exception {
        GnuPgInterop.Availability availability = GnuPgInterop.probe();
        Assumptions.assumeTrue(availability.available(), "GnuPG is not installed; external interop is optional");

        char[] passphrase = "cryptocarver-interop-passphrase".toCharArray();
        try {
            OpenPgpOperations.KeyPairMaterial keys = OpenPgpOperations.generateRsaKeyPair(
                    "CryptoCarver GnuPG Interop <interop@example.invalid>", passphrase);
            GnuPgInterop.InteroperabilityResult result = GnuPgInterop.exerciseBidirectional(
                    keys.publicKeyArmored(), keys.secretKeyArmored(), passphrase,
                    "cross-tool OpenPGP bytes".getBytes(StandardCharsets.UTF_8));
            assertTrue(result.successful(), result.message());
        } finally {
            Arrays.fill(passphrase, '\0');
        }
    }
}
