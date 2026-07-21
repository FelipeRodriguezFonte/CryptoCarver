package com.cryptoforge.model;

import java.nio.file.Path;

/**
 * A persistent profile for a PKCS#11 hardware token connection.
 * <p>
 * This record deliberately omits any PIN or password field to enforce
 * that sensitive credentials are never stored on disk. The user must
 * always supply the PIN explicitly when opening a session.
 * </p>
 *
 * @param name    A descriptive label for the token (e.g., "Nitrokey HSM").
 * @param library The absolute path to the native PKCS#11 driver (e.g., ".so", ".dylib", ".dll").
 * @param slot    The logical slot index for the JVM SunPKCS11 provider (usually 0).
 */
public record Pkcs11Profile(String name, String library, int slot) {
}
