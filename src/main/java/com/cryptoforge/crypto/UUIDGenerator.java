package com.cryptoforge.crypto;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * UUID generation utilities
 */
public class UUIDGenerator {

    private static final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generate a random UUID (Version 4)
     *
     * @return UUID string
     */
    public static String generateUUID() {
        return UUID.randomUUID().toString();
    }

    /**
     * Generate UUID without hyphens
     *
     * @return UUID string without hyphens
     */
    public static String generateUUIDWithoutHyphens() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Generate multiple UUIDs
     *
     * @param count Number of UUIDs to generate
     * @return Array of UUID strings
     */
    public static String[] generateMultipleUUIDs(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("Count must be positive");
        }

        String[] uuids = new String[count];
        for (int i = 0; i < count; i++) {
            uuids[i] = generateUUID();
        }
        return uuids;
    }

    /**
     * Generate uppercase UUID
     *
     * @return Uppercase UUID string
     */
    public static String generateUppercaseUUID() {
        return UUID.randomUUID().toString().toUpperCase();
    }

    /**
     * Validate if string is a valid UUID
     *
     * @param uuid String to validate
     * @return true if valid UUID format
     */
    public static boolean isValidUUID(String uuid) {
        if (uuid == null) {
            return false;
        }

        try {
            UUID.fromString(uuid);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
