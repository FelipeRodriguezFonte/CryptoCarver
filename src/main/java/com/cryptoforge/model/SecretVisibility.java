package com.cryptoforge.model;

/**
 * Defines the visibility policy for secrets in the application (like keys, passwords).
 */
public enum SecretVisibility {
    /** Show all secret values clearly (standard laboratory behavior). */
    FULL_LAB,
    /** Mask secret values (e.g. show only parts, or ***MASKED***). */
    MASKED,
    /** Completely remove or block the secret value from being visible or exported. */
    REDACTED
}
