package com.cryptocarver.crypto;

import java.math.BigInteger;

/**
 * Modular Arithmetic Calculator
 *
 * Supports:
 * - Modular Addition
 * - Modular Subtraction
 * - Modular Inverse (-a mod m)
 * - Modular Multiplication
 * - Modular Exponentiation
 * - Modular Reciprocal (1/a mod m) - only for prime modulus
 *
 * @author Felipe
 */
public class ModularArithmetic {

    /**
     * Modular Addition: (a + b) mod m
     *
     * @param a First operand (hex string)
     * @param b Second operand (hex string)
     * @param m Modulus (hex string)
     * @return Result as hex string
     */
    public static String modularAddition(String a, String b, String m) {
        BigInteger aBig = new BigInteger(a, 16);
        BigInteger bBig = new BigInteger(b, 16);
        BigInteger mBig = new BigInteger(m, 16);

        if (mBig.compareTo(BigInteger.ONE) <= 0) {
            throw new IllegalArgumentException("Modulus must be greater than 1");
        }

        BigInteger result = aBig.add(bBig).mod(mBig);
        return result.toString(16).toUpperCase();
    }

    /**
     * Modular Subtraction: (a - b) mod m
     *
     * @param a First operand (hex string)
     * @param b Second operand (hex string)
     * @param m Modulus (hex string)
     * @return Result as hex string
     */
    public static String modularSubtraction(String a, String b, String m) {
        BigInteger aBig = new BigInteger(a, 16);
        BigInteger bBig = new BigInteger(b, 16);
        BigInteger mBig = new BigInteger(m, 16);

        if (mBig.compareTo(BigInteger.ONE) <= 0) {
            throw new IllegalArgumentException("Modulus must be greater than 1");
        }

        BigInteger result = aBig.subtract(bBig).mod(mBig);
        return result.toString(16).toUpperCase();
    }

    /**
     * Modular Inverse (Additive): -a mod m
     * Returns the value x such that (a + x) mod m = 0
     *
     * @param a Operand (hex string)
     * @param m Modulus (hex string)
     * @return Result as hex string
     */
    public static String modularInverse(String a, String m) {
        BigInteger aBig = new BigInteger(a, 16);
        BigInteger mBig = new BigInteger(m, 16);

        if (mBig.compareTo(BigInteger.ONE) <= 0) {
            throw new IllegalArgumentException("Modulus must be greater than 1");
        }

        // -a mod m = m - (a mod m)
        BigInteger result = aBig.negate().mod(mBig);
        return result.toString(16).toUpperCase();
    }

    /**
     * Modular Multiplication: (a * b) mod m
     *
     * @param a First operand (hex string)
     * @param b Second operand (hex string)
     * @param m Modulus (hex string)
     * @return Result as hex string
     */
    public static String modularMultiplication(String a, String b, String m) {
        BigInteger aBig = new BigInteger(a, 16);
        BigInteger bBig = new BigInteger(b, 16);
        BigInteger mBig = new BigInteger(m, 16);

        if (mBig.compareTo(BigInteger.ONE) <= 0) {
            throw new IllegalArgumentException("Modulus must be greater than 1");
        }

        BigInteger result = aBig.multiply(bBig).mod(mBig);
        return result.toString(16).toUpperCase();
    }

    /**
     * Modular Exponentiation: (a^b) mod m
     * Uses fast modular exponentiation (square-and-multiply)
     *
     * @param a Base (hex string)
     * @param b Exponent (hex string)
     * @param m Modulus (hex string)
     * @return Result as hex string
     */
    public static String modularExponentiation(String a, String b, String m) {
        BigInteger aBig = new BigInteger(a, 16);
        BigInteger bBig = new BigInteger(b, 16);
        BigInteger mBig = new BigInteger(m, 16);

        if (mBig.compareTo(BigInteger.ONE) <= 0) {
            throw new IllegalArgumentException("Modulus must be greater than 1");
        }

        if (bBig.signum() < 0) {
            throw new IllegalArgumentException("Exponent must be non-negative");
        }

        BigInteger result = aBig.modPow(bBig, mBig);
        return result.toString(16).toUpperCase();
    }

    /**
     * Modular Reciprocal (Multiplicative Inverse): (1/a) mod m
     * Returns the value x such that (a * x) mod m = 1
     *
     * REQUIREMENT: m must be prime, or gcd(a, m) = 1
     *
     * @param a Operand (hex string)
     * @param m Modulus (hex string - should be prime)
     * @return Result as hex string
     * @throws ArithmeticException if inverse doesn't exist
     */
    public static String modularReciprocal(String a, String m) throws ArithmeticException {
        BigInteger aBig = new BigInteger(a, 16);
        BigInteger mBig = new BigInteger(m, 16);

        if (mBig.compareTo(BigInteger.ONE) <= 0) {
            throw new IllegalArgumentException("Modulus must be greater than 1");
        }

        // Check if gcd(a, m) = 1
        BigInteger gcd = aBig.gcd(mBig);
        if (!gcd.equals(BigInteger.ONE)) {
            throw new ArithmeticException(
                "Modular reciprocal doesn't exist: gcd(" + a + ", " + m + ") = " + gcd.toString(16).toUpperCase()
            );
        }

        // Calculate using Extended Euclidean Algorithm
        BigInteger result = aBig.modInverse(mBig);
        return result.toString(16).toUpperCase();
    }

    /**
     * Check if a number is prime (probabilistic test)
     * Uses Miller-Rabin primality test with 100 iterations
     *
     * @param n Number to test (hex string)
     * @return true if probably prime, false if composite
     */
    public static boolean isProbablyPrime(String n) {
        BigInteger nBig = new BigInteger(n, 16);
        return nBig.isProbablePrime(100);
    }

    /**
     * Greatest Common Divisor using Euclidean algorithm
     *
     * @param a First number (hex string)
     * @param b Second number (hex string)
     * @return GCD as hex string
     */
    public static String gcd(String a, String b) {
        BigInteger aBig = new BigInteger(a, 16);
        BigInteger bBig = new BigInteger(b, 16);

        BigInteger result = aBig.gcd(bBig);
        return result.toString(16).toUpperCase();
    }

    /**
     * Least Common Multiple
     * LCM(a,b) = (a * b) / GCD(a,b)
     *
     * @param a First number (hex string)
     * @param b Second number (hex string)
     * @return LCM as hex string
     */
    public static String lcm(String a, String b) {
        BigInteger aBig = new BigInteger(a, 16);
        BigInteger bBig = new BigInteger(b, 16);

        BigInteger gcd = aBig.gcd(bBig);
        BigInteger result = aBig.multiply(bBig).divide(gcd).abs();

        return result.toString(16).toUpperCase();
    }

    /**
     * Extended Euclidean Algorithm
     * Finds x, y such that: ax + by = gcd(a,b)
     *
     * @param a First number (hex string)
     * @param b Second number (hex string)
     * @return String with format "GCD: <gcd>\nx: <x>\ny: <y>"
     */
    public static String extendedGCD(String a, String b) {
        BigInteger aBig = new BigInteger(a, 16);
        BigInteger bBig = new BigInteger(b, 16);

        // Extended Euclidean Algorithm
        BigInteger[] result = extendedGCDHelper(aBig, bBig);

        StringBuilder output = new StringBuilder();
        output.append("GCD: ").append(result[0].toString(16).toUpperCase()).append("\n");
        output.append("x: ").append(result[1].toString(16).toUpperCase()).append("\n");
        output.append("y: ").append(result[2].toString(16).toUpperCase()).append("\n");
        output.append("\nVerification: (").append(a).append(" * ").append(result[1].toString(16).toUpperCase());
        output.append(") + (").append(b).append(" * ").append(result[2].toString(16).toUpperCase());
        output.append(") = ").append(result[0].toString(16).toUpperCase());

        return output.toString();
    }

    /**
     * Helper for Extended Euclidean Algorithm
     * Returns [gcd, x, y]
     */
    private static BigInteger[] extendedGCDHelper(BigInteger a, BigInteger b) {
        if (b.equals(BigInteger.ZERO)) {
            return new BigInteger[]{a, BigInteger.ONE, BigInteger.ZERO};
        }

        BigInteger[] result = extendedGCDHelper(b, a.mod(b));
        BigInteger gcd = result[0];
        BigInteger x1 = result[1];
        BigInteger y1 = result[2];

        BigInteger x = y1;
        BigInteger y = x1.subtract(a.divide(b).multiply(y1));

        return new BigInteger[]{gcd, x, y};
    }

    /**
     * Euler's Totient Function φ(n)
     * For prime p: φ(p) = p - 1
     * For p*q (primes): φ(n) = (p-1)(q-1)
     *
     * @param n Number (hex string)
     * @return φ(n) as hex string, or "COMPOSITE" if cannot calculate
     */
    public static String eulerTotient(String n) {
        BigInteger nBig = new BigInteger(n, 16);

        // If prime, φ(n) = n - 1
        if (nBig.isProbablePrime(100)) {
            return nBig.subtract(BigInteger.ONE).toString(16).toUpperCase();
        }

        // Try to factor (only works for small numbers or semiprimes)
        // This is a simplified version for demonstration
        return "UNABLE_TO_CALCULATE (requires factorization)";
    }

    /**
     * Chinese Remainder Theorem (CRT) solver for 2 equations
     * Solves: x ≡ a1 (mod m1)
     *         x ≡ a2 (mod m2)
     *
     * Requirement: gcd(m1, m2) = 1
     *
     * @param a1 First remainder (hex string)
     * @param m1 First modulus (hex string)
     * @param a2 Second remainder (hex string)
     * @param m2 Second modulus (hex string)
     * @return Solution x as hex string
     */
    public static String chineseRemainderTheorem(String a1, String m1, String a2, String m2) {
        BigInteger a1Big = new BigInteger(a1, 16);
        BigInteger m1Big = new BigInteger(m1, 16);
        BigInteger a2Big = new BigInteger(a2, 16);
        BigInteger m2Big = new BigInteger(m2, 16);

        // Check that m1 and m2 are coprime
        if (!m1Big.gcd(m2Big).equals(BigInteger.ONE)) {
            throw new IllegalArgumentException("Moduli must be coprime (gcd = 1)");
        }

        BigInteger M = m1Big.multiply(m2Big);
        BigInteger M1 = M.divide(m1Big);
        BigInteger M2 = M.divide(m2Big);

        BigInteger y1 = M1.modInverse(m1Big);
        BigInteger y2 = M2.modInverse(m2Big);

        BigInteger x = a1Big.multiply(M1).multiply(y1)
                      .add(a2Big.multiply(M2).multiply(y2))
                      .mod(M);

        return x.toString(16).toUpperCase();
    }

    /**
     * Format result with additional information
     *
     * @param operation Operation name
     * @param result Result (hex string)
     * @return Formatted string with decimal and binary representations
     */
    public static String formatResult(String operation, String result) {
        BigInteger resultBig = new BigInteger(result, 16);

        StringBuilder output = new StringBuilder();
        output.append("Operation: ").append(operation).append("\n\n");
        output.append("Result (Hexadecimal):\n");
        output.append(result).append("\n\n");
        output.append("Result (Decimal):\n");
        output.append(resultBig.toString()).append("\n\n");
        output.append("Result (Binary):\n");
        output.append(resultBig.toString(2)).append("\n\n");
        output.append("Bit Length: ").append(resultBig.bitLength()).append(" bits\n");

        return output.toString();
    }
}
