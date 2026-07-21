package com.cryptocarver.crypto;

import java.util.Arrays;
import java.util.List;

/**
 * Check digit calculation and validation using various algorithms
 */
public class CheckDigitCalculator {

    /**
     * Supported check digit algorithms
     */
    public static final List<String> SUPPORTED_ALGORITHMS = Arrays.asList(
            "Luhn (Mod 10)",
            "Verhoeff",
            "Damm"
    );

    /**
     * Calculate check digit using specified algorithm
     *
     * @param data Input data (numeric string)
     * @param algorithm Algorithm to use
     * @return Check digit
     * @throws IllegalArgumentException if data is invalid or algorithm unsupported
     */
    public static int calculateCheckDigit(String data, String algorithm) {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("Data cannot be null or empty");
        }

        // Remove any non-digit characters
        String digits = data.replaceAll("[^0-9]", "");

        switch (algorithm) {
            case "Luhn (Mod 10)":
                return calculateLuhn(digits);
            case "Verhoeff":
                return calculateVerhoeff(digits);
            case "Damm":
                return calculateDamm(digits);
            default:
                throw new IllegalArgumentException("Unsupported algorithm: " + algorithm);
        }
    }

    /**
     * Validate data with check digit using specified algorithm
     *
     * @param dataWithCheckDigit Complete data including check digit
     * @param algorithm Algorithm to use
     * @return true if check digit is valid
     */
    public static boolean validateCheckDigit(String dataWithCheckDigit, String algorithm) {
        if (dataWithCheckDigit == null || dataWithCheckDigit.length() < 2) {
            return false;
        }

        String digits = dataWithCheckDigit.replaceAll("[^0-9]", "");
        String data = digits.substring(0, digits.length() - 1);
        int providedCheckDigit = Character.getNumericValue(digits.charAt(digits.length() - 1));

        int calculatedCheckDigit = calculateCheckDigit(data, algorithm);

        return calculatedCheckDigit == providedCheckDigit;
    }

    /**
     * Luhn algorithm (Mod 10)
     * Used in credit card numbers, IMEI numbers, etc.
     */
    private static int calculateLuhn(String digits) {
        int sum = 0;
        boolean alternate = false;

        // Process digits from right to left
        for (int i = digits.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(digits.charAt(i));

            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }

            sum += digit;
            alternate = !alternate;
        }

        return (10 - (sum % 10)) % 10;
    }

    /**
     * Verhoeff algorithm
     * More sophisticated than Luhn, detects all single-digit errors and most transpositions
     */
    private static int calculateVerhoeff(String digits) {
        // Multiplication table
        int[][] d = {
                {0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
                {1, 2, 3, 4, 0, 6, 7, 8, 9, 5},
                {2, 3, 4, 0, 1, 7, 8, 9, 5, 6},
                {3, 4, 0, 1, 2, 8, 9, 5, 6, 7},
                {4, 0, 1, 2, 3, 9, 5, 6, 7, 8},
                {5, 9, 8, 7, 6, 0, 4, 3, 2, 1},
                {6, 5, 9, 8, 7, 1, 0, 4, 3, 2},
                {7, 6, 5, 9, 8, 2, 1, 0, 4, 3},
                {8, 7, 6, 5, 9, 3, 2, 1, 0, 4},
                {9, 8, 7, 6, 5, 4, 3, 2, 1, 0}
        };

        // Permutation table
        int[][] p = {
                {0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
                {1, 5, 7, 6, 2, 8, 3, 0, 9, 4},
                {5, 8, 0, 3, 7, 9, 6, 1, 4, 2},
                {8, 9, 1, 6, 0, 4, 3, 5, 2, 7},
                {9, 4, 5, 3, 1, 2, 6, 8, 7, 0},
                {4, 2, 8, 6, 5, 7, 3, 9, 0, 1},
                {2, 7, 9, 3, 8, 0, 6, 4, 1, 5},
                {7, 0, 4, 6, 9, 1, 3, 2, 5, 8}
        };

        // Inverse table
        int[] inv = {0, 4, 3, 2, 1, 5, 6, 7, 8, 9};

        int c = 0;
        int len = digits.length();

        for (int i = 0; i < len; i++) {
            int digit = Character.getNumericValue(digits.charAt(len - i - 1));
            c = d[c][p[(i + 1) % 8][digit]];
        }

        return inv[c];
    }

    /**
     * Damm algorithm
     * Detects all single-digit errors and all adjacent transposition errors
     */
    private static int calculateDamm(String digits) {
        // Quasigroup table
        int[][] table = {
                {0, 3, 1, 7, 5, 9, 8, 6, 4, 2},
                {7, 0, 9, 2, 1, 5, 4, 8, 6, 3},
                {4, 2, 0, 6, 8, 7, 1, 3, 5, 9},
                {1, 7, 5, 0, 9, 8, 3, 4, 2, 6},
                {6, 1, 2, 3, 0, 4, 5, 9, 7, 8},
                {3, 6, 7, 4, 2, 0, 9, 5, 8, 1},
                {5, 8, 6, 9, 7, 2, 0, 1, 3, 4},
                {8, 9, 4, 5, 3, 6, 2, 0, 1, 7},
                {9, 4, 3, 8, 6, 1, 7, 2, 0, 5},
                {2, 5, 8, 1, 4, 3, 6, 7, 9, 0}
        };

        int interim = 0;

        for (int i = 0; i < digits.length(); i++) {
            int digit = Character.getNumericValue(digits.charAt(i));
            interim = table[interim][digit];
        }

        return interim;
    }

    /**
     * Format data with check digit appended
     *
     * @param data Original data
     * @param algorithm Algorithm used
     * @return Data with check digit appended
     */
    public static String formatWithCheckDigit(String data, String algorithm) {
        int checkDigit = calculateCheckDigit(data, algorithm);
        return data + checkDigit;
    }
}
