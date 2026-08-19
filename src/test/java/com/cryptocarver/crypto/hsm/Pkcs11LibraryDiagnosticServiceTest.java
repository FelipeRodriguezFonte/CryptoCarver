package com.cryptocarver.crypto.hsm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.Provider;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Pkcs11LibraryDiagnosticServiceTest {
    @TempDir
    Path temporaryDirectory;

    private Pkcs11LibraryDiagnosticService service;

    @BeforeEach
    void setUp() {
        service = new Pkcs11LibraryDiagnosticService();
    }

    @Test
    void normalizesThePathBeforeReturningAResult() {
        Path relative = temporaryDirectory.resolve("nested").resolve("..").resolve("module.so");

        Pkcs11DiagnosticResult result = service.diagnose(relative);

        assertEquals(relative.toAbsolutePath().normalize(), result.normalizedLibrary());
        assertEquals(Pkcs11DiagnosticResult.Status.FILE_NOT_FOUND, result.status());
        assertFalse(result.authenticationAttempted());
    }

    @Test
    void reportsMissingFileWithoutExposingAnException() {
        Pkcs11DiagnosticResult result = service.diagnose(
                temporaryDirectory.resolve("does-not-exist").resolve("libpkcs11.so"));

        assertEquals(Pkcs11DiagnosticResult.Status.FILE_NOT_FOUND, result.status());
        assertTrue(result.message().contains("No se encontró"));
        assertSanitized(result);
    }

    @Test
    void reportsDirectoryAsNotRegularFile() throws IOException {
        Path directory = Files.createDirectory(temporaryDirectory.resolve("module.so"));

        Pkcs11DiagnosticResult result = service.diagnose(directory);

        assertEquals(Pkcs11DiagnosticResult.Status.NOT_REGULAR_FILE, result.status());
        assertTrue(result.message().contains("fichero regular"));
        assertFalse(result.readable());
        assertSanitized(result);
    }

    @Test
    void reportsUnreadableFileWhenPosixPermissionsAreEnforced() throws IOException {
        Assumptions.assumeTrue(Files.getFileStore(temporaryDirectory).supportsFileAttributeView("posix"),
                "POSIX permissions are not available on this platform");

        Path unreadable = Files.writeString(temporaryDirectory.resolve("unreadable.so"), "fixture",
                StandardCharsets.UTF_8);
        Set<PosixFilePermission> originalPermissions = Files.getPosixFilePermissions(unreadable);
        try {
            Files.setPosixFilePermissions(unreadable, EnumSet.noneOf(PosixFilePermission.class));
            Assumptions.assumeFalse(Files.isReadable(unreadable),
                    "The test process can still read files without POSIX permissions");

            Pkcs11DiagnosticResult result = service.diagnose(unreadable);

            assertEquals(Pkcs11DiagnosticResult.Status.ACCESS_DENIED, result.status());
            assertTrue(result.message().contains("permisos"));
            assertSanitized(result);
        } finally {
            Files.setPosixFilePermissions(unreadable, originalPermissions);
        }
    }

    @Test
    void classifiesAnInvalidNativeModuleWithoutRawProviderDetails() throws IOException {
        Path invalidLibrary = Files.writeString(temporaryDirectory.resolve("invalid.so"),
                "not a PKCS#11 native module", StandardCharsets.UTF_8);

        Pkcs11DiagnosticResult result = service.diagnose(invalidLibrary);

        assertEquals(Pkcs11DiagnosticResult.Status.NATIVE_LIBRARY_INCOMPATIBLE, result.status());
        assertTrue(result.message().contains("arquitectura"));
        assertFalse(result.message().contains("java."));
        assertFalse(result.message().contains("at "));
        assertSanitized(result);
    }

    @Test
    void sanitizesSunPkcs11ConfigurationRejection() throws IOException {
        Path fixture = Files.writeString(temporaryDirectory.resolve("module.so"), "fixture",
                StandardCharsets.UTF_8);
        Pkcs11LibraryDiagnosticService rejectingService = new Pkcs11LibraryDiagnosticService(
                ignored -> {
                    throw new IllegalArgumentException("PIN=123456; alias=private-key; java.lang.Exception");
                });

        Pkcs11DiagnosticResult result = rejectingService.diagnose(fixture);

        assertEquals(Pkcs11DiagnosticResult.Status.SUNPKCS11_CONFIGURATION_REJECTED, result.status());
        assertTrue(result.message().contains("SunPKCS11"));
        assertFalse(result.message().contains("123456"));
        assertFalse(result.message().contains("private-key"));
        assertFalse(result.message().contains("java.lang"));
        assertFalse(result.message().contains("at "));
        assertFalse(result.authenticationAttempted());
        assertSanitized(result);
    }

    @Test
    void reportsSuccessfulProviderConfigurationUsingAReadOnlyFixture() throws IOException {
        Path fixture = Files.writeString(temporaryDirectory.resolve("module.so"), "fixture",
                StandardCharsets.UTF_8);
        AtomicBoolean configureCalled = new AtomicBoolean();
        AtomicBoolean configContainsCredentialWord = new AtomicBoolean(true);
        Pkcs11LibraryDiagnosticService fixtureService = new Pkcs11LibraryDiagnosticService(configFile -> {
            configureCalled.set(true);
            try {
                String configuration = Files.readString(configFile, StandardCharsets.UTF_8);
                configContainsCredentialWord.set(configuration.toLowerCase().contains("pin")
                        || configuration.toLowerCase().contains("password"));
            } catch (IOException failure) {
                throw new AssertionError("Fixture could not read the temporary provider configuration", failure);
            }
            return new Provider("PKCS11-fixture", 1.0, "test-only provider") {
                private static final long serialVersionUID = 1L;
            };
        });

        Pkcs11DiagnosticResult result = fixtureService.diagnose(fixture);

        assertEquals(Pkcs11DiagnosticResult.Status.OK, result.status());
        assertTrue(result.isSuccessful());
        assertTrue(configureCalled.get());
        assertFalse(configContainsCredentialWord.get());
        assertTrue(result.sunPkcs11Configured());
        assertFalse(result.authenticationAttempted());
        assertNotNull(result.jvmArchitecture());
        assertNotNull(result.osArchitecture());
        assertSanitized(result);
    }

    @Test
    @Tag("integration")
    void diagnosesSoftHsmWhenTheOptionalFixtureIsConfigured() {
        String module = System.getenv("SOFTHSM2_MODULE");
        Assumptions.assumeTrue(module != null && !module.isBlank(),
                "SoftHSM is optional; set SOFTHSM2_MODULE to run this integration check");

        Pkcs11DiagnosticResult result = service.diagnose(Path.of(module));

        assertEquals(Pkcs11DiagnosticResult.Status.OK, result.status());
        assertTrue(result.sunPkcs11Configured());
        assertFalse(result.authenticationAttempted());
        assertSanitized(result);
    }

    private static void assertSanitized(Pkcs11DiagnosticResult result) {
        assertNotNull(result.message());
        assertFalse(result.message().contains("PIN"));
        assertFalse(result.message().contains("password"));
        assertFalse(result.message().contains("PrivateKey"));
        assertFalse(result.message().contains("-----BEGIN"));
    }
}
