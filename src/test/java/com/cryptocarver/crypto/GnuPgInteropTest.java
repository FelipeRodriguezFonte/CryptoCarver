package com.cryptocarver.crypto;

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

    @Test
    void createGpgHomeDirectoryEnforcesPermissionsAndCleansUpOnFailure() throws Exception {
        java.nio.file.Path home = GnuPgInterop.createGpgHomeDirectory();
        try {
            assertTrue(java.nio.file.Files.exists(home), "Created home directory must exist");
            try {
                java.util.Set<java.nio.file.attribute.PosixFilePermission> perms = java.nio.file.Files.getPosixFilePermissions(home);
                String permString = java.nio.file.attribute.PosixFilePermissions.toString(perms);
                assertTrue(permString.equals("rwx------"), "Temporary GNUPGHOME must have 0700 (rwx------) permissions, was: " + permString);
            } catch (UnsupportedOperationException ignored) {
                // Non-POSIX system
            }

            // Write a dummy file inside
            java.nio.file.Path dummyFile = home.resolve("dummy.txt");
            java.nio.file.Files.writeString(dummyFile, "test data");
            assertTrue(java.nio.file.Files.exists(dummyFile));
        } finally {
            // Confirm deletion in finally
            GnuPgInterop.deleteRecursively(home);
            assertFalse(java.nio.file.Files.exists(home), "Home directory must be deleted after cleanup");
        }
    }
}
