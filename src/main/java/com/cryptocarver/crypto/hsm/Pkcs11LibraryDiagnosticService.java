package com.cryptocarver.crypto.hsm;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.Provider;
import java.security.Security;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * Performs a read-only local diagnosis of a PKCS#11 native library.
 *
 * <p>The service validates the path and asks SunPKCS11 to configure a
 * transient provider configuration. It intentionally does not create a
 * {@code KeyStore}, call {@code load}, enumerate slots or mechanisms, open a
 * session, or collect authentication data. The only temporary write is the
 * provider configuration file required by the JDK SunPKCS11 API; it contains
 * the library path and a slot-list index, never a PIN or password.</p>
 */
public final class Pkcs11LibraryDiagnosticService {
    private static final String SUN_PKCS11 = "SunPKCS11";
    private final Function<Path, Provider> providerConfigurer;
    private final RuntimeInfo runtimeInfo;

    public Pkcs11LibraryDiagnosticService() {
        this(defaultProviderConfigurer());
    }

    /**
     * Package-private seam used by deterministic tests. Production callers
     * should use {@link #Pkcs11LibraryDiagnosticService()} so configuration is
     * delegated to the JVM's SunPKCS11 provider.
     */
    Pkcs11LibraryDiagnosticService(Function<Path, Provider> providerConfigurer) {
        this.providerConfigurer = providerConfigurer;
        this.runtimeInfo = RuntimeInfo.current();
    }

    /**
     * Diagnoses one native library path without accepting or requesting a PIN.
     * The supplied path is converted to an absolute, normalized path before
     * any filesystem or provider operation is attempted.
     */
    public Pkcs11DiagnosticResult diagnose(Path library) {
        Path normalized;
        try {
            if (library == null) {
                return result(Pkcs11DiagnosticResult.Status.INVALID_PATH, null,
                        "La ruta de la biblioteca PKCS#11 es obligatoria.",
                        Set.of(), false, false, false);
            }
            normalized = library.toAbsolutePath().normalize();
        } catch (RuntimeException invalidPath) {
            return result(Pkcs11DiagnosticResult.Status.INVALID_PATH, null,
                    "La ruta de la biblioteca PKCS#11 no es válida. Usa una ruta local absoluta.",
                    Set.of(), false, false, false);
        }

        Set<String> expectedExtensions = expectedExtensions(runtimeInfo.osName());
        boolean extensionMatches = extensionMatches(normalized, expectedExtensions);

        BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(normalized, BasicFileAttributes.class);
        } catch (NoSuchFileException missing) {
            return result(Pkcs11DiagnosticResult.Status.FILE_NOT_FOUND, normalized,
                    "No se encontró la biblioteca PKCS#11. Comprueba la ruta y que el dispositivo o volumen esté disponible.",
                    expectedExtensions, extensionMatches, false, false);
        } catch (AccessDeniedException denied) {
            return result(Pkcs11DiagnosticResult.Status.ACCESS_DENIED, normalized,
                    "No se puede acceder a la biblioteca PKCS#11. Revisa permisos de lectura y las restricciones del sistema.",
                    expectedExtensions, extensionMatches, false, false);
        } catch (IOException | SecurityException unreadable) {
            return result(Pkcs11DiagnosticResult.Status.ACCESS_DENIED, normalized,
                    "No se puede inspeccionar la biblioteca PKCS#11. Revisa permisos de lectura y las restricciones del sistema.",
                    expectedExtensions, extensionMatches, false, false);
        }

        if (!attributes.isRegularFile()) {
            return result(Pkcs11DiagnosticResult.Status.NOT_REGULAR_FILE, normalized,
                    "La ruta de la biblioteca PKCS#11 no apunta a un fichero regular. Selecciona el módulo nativo, no un directorio u otro recurso.",
                    expectedExtensions, extensionMatches, false, false);
        }

        try (ReadableByteChannel ignored = Files.newByteChannel(normalized, StandardOpenOption.READ)) {
            // Opening the channel catches ACL and sandbox errors that metadata
            // access alone can miss. The library contents are not copied or
            // parsed by this check.
        } catch (AccessDeniedException denied) {
            return result(Pkcs11DiagnosticResult.Status.ACCESS_DENIED, normalized,
                    "La biblioteca PKCS#11 existe, pero no se puede leer. Revisa sus permisos y los de sus directorios padre.",
                    expectedExtensions, extensionMatches, false, false);
        } catch (NoSuchFileException missing) {
            return result(Pkcs11DiagnosticResult.Status.FILE_NOT_FOUND, normalized,
                    "La biblioteca PKCS#11 dejó de estar disponible durante el diagnóstico. Comprueba la ruta.",
                    expectedExtensions, extensionMatches, false, false);
        } catch (IOException | SecurityException unreadable) {
            return result(Pkcs11DiagnosticResult.Status.ACCESS_DENIED, normalized,
                    "La biblioteca PKCS#11 existe, pero no se puede leer. Revisa sus permisos y los de sus directorios padre.",
                    expectedExtensions, extensionMatches, false, false);
        }

        if (providerConfigurer == null) {
            return result(Pkcs11DiagnosticResult.Status.SUNPKCS11_UNAVAILABLE, normalized,
                    "La JVM actual no expone el proveedor SunPKCS11. Usa una JVM con el módulo jdk.crypto.cryptoki habilitado.",
                    expectedExtensions, extensionMatches, true, false);
        }

        Path temporaryConfig = null;
        try {
            Pkcs11Configuration configuration = new Pkcs11Configuration(
                    "CryptoCarverDiagnostic", normalized, 0);
            temporaryConfig = Files.createTempFile("cryptocarver-pkcs11-diagnostic-", ".cfg");
            Files.writeString(temporaryConfig, configuration.toSunPkcs11Configuration(), StandardCharsets.UTF_8);

            Provider configuredProvider = providerConfigurer.apply(temporaryConfig);
            if (configuredProvider == null) {
                return result(Pkcs11DiagnosticResult.Status.SUNPKCS11_CONFIGURATION_REJECTED, normalized,
                        "SunPKCS11 rechazó la configuración de diagnóstico. Comprueba que el módulo acepta la ruta indicada.",
                        expectedExtensions, extensionMatches, true, false);
            }

            String extensionNote = extensionMatches || expectedExtensions.isEmpty()
                    ? ""
                    : " La extensión no es la habitual para este sistema; la carga se confirmó igualmente y conviene verificar la arquitectura si el proveedor falla después.";
            return result(Pkcs11DiagnosticResult.Status.OK, normalized,
                    "Diagnóstico correcto: SunPKCS11 configuró la biblioteca sin abrir sesión ni autenticarse." + extensionNote,
                    expectedExtensions, extensionMatches, true, true);
        } catch (AccessDeniedException denied) {
            return result(Pkcs11DiagnosticResult.Status.ACCESS_DENIED, normalized,
                    "El sistema denegó el acceso durante la configuración de SunPKCS11. Revisa permisos y restricciones de carga nativa.",
                    expectedExtensions, extensionMatches, true, false);
        } catch (IOException temporaryConfigFailure) {
            return result(Pkcs11DiagnosticResult.Status.TEMPORARY_CONFIGURATION_FAILED, normalized,
                    "No se pudo preparar la configuración temporal de SunPKCS11. Revisa el acceso temporal de la aplicación e inténtalo de nuevo.",
                    expectedExtensions, extensionMatches, true, false);
        } catch (IllegalArgumentException configurationRejected) {
            return result(Pkcs11DiagnosticResult.Status.SUNPKCS11_CONFIGURATION_REJECTED, normalized,
                    "SunPKCS11 rechazó la configuración de diagnóstico. Comprueba que la ruta y los parámetros del módulo son compatibles.",
                    expectedExtensions, extensionMatches, true, false);
        } catch (SecurityException denied) {
            return result(Pkcs11DiagnosticResult.Status.ACCESS_DENIED, normalized,
                    "El sistema bloqueó la carga de la biblioteca PKCS#11. Revisa permisos, firma y restricciones de seguridad.",
                    expectedExtensions, extensionMatches, true, false);
        } catch (LinkageError incompatibleNativeLibrary) {
            return result(Pkcs11DiagnosticResult.Status.NATIVE_LIBRARY_INCOMPATIBLE, normalized,
                    nativeLibraryMessage(), expectedExtensions, extensionMatches, true, false);
        } catch (RuntimeException nativeOrProviderFailure) {
            return result(classifyProviderFailure(nativeOrProviderFailure), normalized,
                    classifyProviderFailure(nativeOrProviderFailure) == Pkcs11DiagnosticResult.Status.NATIVE_LIBRARY_INCOMPATIBLE
                            ? nativeLibraryMessage()
                            : "SunPKCS11 rechazó la configuración de diagnóstico. Comprueba la ruta y los parámetros del módulo.",
                    expectedExtensions, extensionMatches, true, false);
        } finally {
            if (temporaryConfig != null) {
                try {
                    Files.deleteIfExists(temporaryConfig);
                } catch (IOException ignored) {
                    // The file contains no credentials and is best-effort
                    // cleanup only; never expose cleanup details to callers.
                }
            }
        }
    }

    private Pkcs11DiagnosticResult result(
            Pkcs11DiagnosticResult.Status status,
            Path normalized,
            String message,
            Set<String> expectedExtensions,
            boolean extensionMatches,
            boolean readable,
            boolean sunPkcs11Configured) {
        return new Pkcs11DiagnosticResult(status, normalized, message,
                runtimeInfo.osName(), runtimeInfo.osArchitecture(), runtimeInfo.jvmArchitecture(),
                expectedExtensions, extensionMatches, readable, sunPkcs11Configured, false);
    }

    private static Function<Path, Provider> defaultProviderConfigurer() {
        Provider baseProvider;
        try {
            baseProvider = Security.getProvider(SUN_PKCS11);
        } catch (SecurityException denied) {
            return null;
        }
        if (baseProvider == null) {
            return null;
        }
        return configFile -> baseProvider.configure(configFile.toString());
    }

    private static Pkcs11DiagnosticResult.Status classifyProviderFailure(RuntimeException failure) {
        if (containsCause(failure, LinkageError.class)
                || containsCause(failure, UnsatisfiedLinkError.class)) {
            return Pkcs11DiagnosticResult.Status.NATIVE_LIBRARY_INCOMPATIBLE;
        }
        // The generated configuration is syntactically valid. A provider
        // runtime failure at this point normally means the native module is
        // not loadable or does not implement the PKCS#11 entry points.
        return Pkcs11DiagnosticResult.Status.NATIVE_LIBRARY_INCOMPATIBLE;
    }

    private static String nativeLibraryMessage() {
        return "La biblioteca nativa PKCS#11 no es compatible con esta JVM/SO o no se puede cargar. Comprueba la arquitectura, las dependencias nativas y que el fichero sea un módulo PKCS#11 válido.";
    }

    private static boolean containsCause(Throwable failure, Class<? extends Throwable> type) {
        Throwable current = failure;
        while (current != null) {
            if (type.isInstance(current)) return true;
            current = current.getCause();
        }
        return false;
    }

    private static Set<String> expectedExtensions(String osName) {
        String normalizedOs = osName.toLowerCase(Locale.ROOT);
        if (normalizedOs.contains("win")) return Set.of(".dll");
        if (normalizedOs.contains("mac") || normalizedOs.contains("darwin")) {
            return Set.of(".dylib", ".so");
        }
        if (normalizedOs.contains("linux") || normalizedOs.contains("unix")
                || normalizedOs.contains("freebsd") || normalizedOs.contains("openbsd")) {
            return Set.of(".so");
        }
        return Set.of();
    }

    private static boolean extensionMatches(Path path, Set<String> expectedExtensions) {
        if (path == null || expectedExtensions.isEmpty() || path.getFileName() == null) return true;
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return expectedExtensions.stream().anyMatch(fileName::endsWith);
    }

    private record RuntimeInfo(String osName, String osArchitecture, String jvmArchitecture) {
        private static RuntimeInfo current() {
            String osName = safeProperty("os.name", "desconocido");
            String osArchitecture = safeProperty("os.arch", "desconocida");
            String dataModel = safeProperty("sun.arch.data.model", "desconocido");
            String jvmArchitecture = dataModel.equals("desconocido")
                    ? osArchitecture
                    : dataModel + "-bit (" + osArchitecture + ")";
            return new RuntimeInfo(osName, osArchitecture, jvmArchitecture);
        }

        private static String safeProperty(String name, String fallback) {
            try {
                String value = System.getProperty(name);
                return value == null || value.isBlank() ? fallback : value;
            } catch (SecurityException denied) {
                return fallback;
            }
        }
    }
}
