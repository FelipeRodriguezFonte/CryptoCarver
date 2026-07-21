package com.cryptocarver.crypto;

import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.jce.spec.ECPrivateKeySpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import java.util.Arrays;
import java.util.List;

/**
 * Advanced Asymmetric Key Operations
 * Supports RSA, DSA, ECDSA F(p) and ECDSA F(2^m)
 *
 * @author Felipe
 */
public class AsymmetricKeyOperations {

    static {
        // Register BouncyCastle provider
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    // ============================================================================
    // RSA KEY GENERATION (1024 - 15360+ bits)
    // ============================================================================

    /**
     * Supported RSA key sizes
     */
    public static final List<Integer> RSA_KEY_SIZES = Arrays.asList(
        1024, 1536, 2048, 2560, 3072, 3584, 4096,
        4608, 5120, 6144, 7168, 8192, 9216, 10240,
        11264, 12288, 13312, 14336, 15360, 16384
    );

    /**
     * Generate RSA key pair
     *
     * @param keySize Key size in bits (1024-16384)
     * @return KeyPair containing public and private keys
     */
    public static KeyPair generateRSAKeyPair(int keySize) throws Exception {
        if (keySize < 1024 || keySize > 16384) {
            throw new IllegalArgumentException("RSA key size must be between 1024 and 16384 bits");
        }

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA", "BC");
        keyGen.initialize(keySize, new SecureRandom());
        return keyGen.generateKeyPair();
    }

    /**
     * Get RSA public key components (n, e)
     */
    public static String getRSAPublicKeyInfo(PublicKey publicKey) throws Exception {
        if (!(publicKey instanceof RSAPublicKey)) {
            throw new IllegalArgumentException("Not an RSA public key");
        }

        RSAPublicKey rsaPub = (RSAPublicKey) publicKey;
        StringBuilder info = new StringBuilder();
        info.append("Modulus (n):\n");
        info.append(rsaPub.getModulus().toString(16).toUpperCase()).append("\n\n");
        info.append("Public Exponent (e):\n");
        info.append(rsaPub.getPublicExponent().toString(16).toUpperCase()).append("\n");
        info.append("Decimal: ").append(rsaPub.getPublicExponent().toString()).append("\n\n");
        info.append("Key Size: ").append(rsaPub.getModulus().bitLength()).append(" bits\n");

        return info.toString();
    }

    /**
     * Get RSA private key components (n, d, p, q)
     */
    public static String getRSAPrivateKeyInfo(PrivateKey privateKey) throws Exception {
        if (!(privateKey instanceof RSAPrivateCrtKey)) {
            throw new IllegalArgumentException("Not an RSA private key");
        }

        RSAPrivateCrtKey rsaPriv = (RSAPrivateCrtKey) privateKey;
        StringBuilder info = new StringBuilder();
        info.append("Modulus (n):\n");
        info.append(rsaPriv.getModulus().toString(16).toUpperCase()).append("\n\n");
        info.append("Private Exponent (d):\n");
        info.append(rsaPriv.getPrivateExponent().toString(16).toUpperCase()).append("\n\n");
        info.append("Prime P:\n");
        info.append(rsaPriv.getPrimeP().toString(16).toUpperCase()).append("\n\n");
        info.append("Prime Q:\n");
        info.append(rsaPriv.getPrimeQ().toString(16).toUpperCase()).append("\n\n");
        info.append("Public Exponent (e):\n");
        info.append(rsaPriv.getPublicExponent().toString(16).toUpperCase()).append("\n\n");
        info.append("dP (d mod (p-1)):\n");
        info.append(rsaPriv.getPrimeExponentP().toString(16).toUpperCase()).append("\n\n");
        info.append("dQ (d mod (q-1)):\n");
        info.append(rsaPriv.getPrimeExponentQ().toString(16).toUpperCase()).append("\n\n");
        info.append("qInv (q^-1 mod p):\n");
        info.append(rsaPriv.getCrtCoefficient().toString(16).toUpperCase()).append("\n");

        return info.toString();
    }

    // ============================================================================
    // DSA KEY GENERATION
    // ============================================================================

    /**
     * Supported DSA key sizes (L, N pairs per FIPS 186-4)
     * L = modulus length, N = divisor length
     */
    public static final List<String> DSA_KEY_SIZES = Arrays.asList(
        "1024/160",  // Legacy
        "2048/224",  // FIPS 186-4
        "2048/256",  // FIPS 186-4
        "3072/256"   // FIPS 186-4
    );

    /**
     * Generate DSA key pair
     *
     * @param keySize String format "L/N" (e.g., "2048/256")
     * @return KeyPair containing public and private keys
     */
    public static KeyPair generateDSAKeyPair(String keySize) throws Exception {
        String[] parts = keySize.split("/");
        int L = Integer.parseInt(parts[0]);
        // N is ignored - Java determines it automatically based on L

        // Generate DSA parameters using standard approach
        AlgorithmParameterGenerator paramGen = AlgorithmParameterGenerator.getInstance("DSA", "BC");
        paramGen.init(L, new SecureRandom());

        AlgorithmParameters params = paramGen.generateParameters();
        DSAParameterSpec dsaParams = params.getParameterSpec(DSAParameterSpec.class);

        // Generate key pair with proper parameters
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("DSA", "BC");
        keyGen.initialize(dsaParams, new SecureRandom());

        return keyGen.generateKeyPair();
    }

    /**
     * Get DSA key information
     */
    public static String getDSAKeyInfo(Key key) throws Exception {
        StringBuilder info = new StringBuilder();

        if (key instanceof DSAPublicKey) {
            DSAPublicKey dsaPub = (DSAPublicKey) key;
            DSAParams params = dsaPub.getParams();

            info.append("Public Key (y):\n");
            info.append(dsaPub.getY().toString(16).toUpperCase()).append("\n\n");
            info.append("Prime (p):\n");
            info.append(params.getP().toString(16).toUpperCase()).append("\n\n");
            info.append("Subprime (q):\n");
            info.append(params.getQ().toString(16).toUpperCase()).append("\n\n");
            info.append("Generator (g):\n");
            info.append(params.getG().toString(16).toUpperCase()).append("\n");

        } else if (key instanceof DSAPrivateKey) {
            DSAPrivateKey dsaPriv = (DSAPrivateKey) key;
            DSAParams params = dsaPriv.getParams();

            info.append("Private Key (x):\n");
            info.append(dsaPriv.getX().toString(16).toUpperCase()).append("\n\n");
            info.append("Prime (p):\n");
            info.append(params.getP().toString(16).toUpperCase()).append("\n\n");
            info.append("Subprime (q):\n");
            info.append(params.getQ().toString(16).toUpperCase()).append("\n\n");
            info.append("Generator (g):\n");
            info.append(params.getG().toString(16).toUpperCase()).append("\n");
        }

        return info.toString();
    }

    // ============================================================================
    // ECDSA F(p) - ELLIPTIC CURVE OVER PRIME FIELD
    // ============================================================================

    /**
     * Standard named curves for ECDSA F(p)
     */
    public static final List<String> ECDSA_FP_NAMED_CURVES = Arrays.asList(
        "secp192r1", "secp224r1", "secp256r1", "secp384r1", "secp521r1",
        "prime192v1", "prime256v1",
        "P-192", "P-224", "P-256", "P-384", "P-521"
    );

    /**
     * Generate ECDSA F(p) key pair using named curve
     */
    public static KeyPair generateECDSAFpKeyPair(String curveName) throws Exception {
        ECParameterSpec ecSpec = ECNamedCurveTable.getParameterSpec(curveName);
        if (ecSpec == null) {
            throw new IllegalArgumentException("Unknown curve: " + curveName);
        }

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("ECDSA", "BC");
        keyGen.initialize(ecSpec, new SecureRandom());
        return keyGen.generateKeyPair();
    }

    /**
     * Generate ECDSA F(p) with manual parameters
     *
     * @param p Prime modulus
     * @param a Curve coefficient a
     * @param b Curve coefficient b
     * @param gx Generator point x-coordinate
     * @param gy Generator point y-coordinate
     * @param n Order of generator
     * @param h Cofactor
     */
    public static KeyPair generateECDSAFpKeyPairManual(
            String p, String a, String b,
            String gx, String gy,
            String n, String h) throws Exception {

        BigInteger pBig = new BigInteger(p, 16);
        BigInteger aBig = new BigInteger(a, 16);
        BigInteger bBig = new BigInteger(b, 16);
        BigInteger gxBig = new BigInteger(gx, 16);
        BigInteger gyBig = new BigInteger(gy, 16);
        BigInteger nBig = new BigInteger(n, 16);
        int hInt = Integer.parseInt(h, 16);

        // Create curve over F(p)
        ECCurve curve = new ECCurve.Fp(pBig, aBig, bBig, nBig, BigInteger.valueOf(hInt));
        ECPoint g = curve.createPoint(gxBig, gyBig);

        ECParameterSpec ecSpec = new ECParameterSpec(curve, g, nBig, BigInteger.valueOf(hInt));

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("ECDSA", "BC");
        keyGen.initialize(ecSpec, new SecureRandom());
        return keyGen.generateKeyPair();
    }

    // ============================================================================
    // ECDSA F(2^m) - ELLIPTIC CURVE OVER BINARY FIELD
    // ============================================================================

    /**
     * Standard named curves for ECDSA F(2^m)
     */
    public static final List<String> ECDSA_F2M_NAMED_CURVES = Arrays.asList(
        "sect163r1", "sect163r2", "sect233r1", "sect283r1",
        "sect409r1", "sect571r1",
        "sect163k1", "sect233k1", "sect283k1", "sect409k1", "sect571k1",
        "c2pnb163v1", "c2pnb272w1", "c2pnb304w1"
    );

    /**
     * Generate ECDSA F(2^m) key pair using named curve
     */
    public static KeyPair generateECDSAF2mKeyPair(String curveName) throws Exception {
        ECParameterSpec ecSpec = ECNamedCurveTable.getParameterSpec(curveName);
        if (ecSpec == null) {
            throw new IllegalArgumentException("Unknown curve: " + curveName);
        }

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("ECDSA", "BC");
        keyGen.initialize(ecSpec, new SecureRandom());
        return keyGen.generateKeyPair();
    }

    /**
     * Generate ECDSA F(2^m) with manual parameters
     *
     * @param m Degree of the field
     * @param k1, k2, k3 Polynomial exponents (pentanomial: x^m + x^k3 + x^k2 + x^k1 + 1)
     * @param a Curve coefficient a
     * @param b Curve coefficient b
     * @param gx Generator point x-coordinate
     * @param gy Generator point y-coordinate
     * @param n Order of generator
     * @param h Cofactor
     */
    public static KeyPair generateECDSAF2mKeyPairManual(
            int m, int k1, int k2, int k3,
            String a, String b,
            String gx, String gy,
            String n, String h) throws Exception {

        BigInteger aBig = new BigInteger(a, 16);
        BigInteger bBig = new BigInteger(b, 16);
        BigInteger gxBig = new BigInteger(gx, 16);
        BigInteger gyBig = new BigInteger(gy, 16);
        BigInteger nBig = new BigInteger(n, 16);
        int hInt = Integer.parseInt(h, 16);

        // Create curve over F(2^m) using pentanomial basis
        ECCurve curve;
        if (k3 == 0) {
            // Trinomial basis: x^m + x^k + 1
            curve = new ECCurve.F2m(m, k1, aBig, bBig, nBig, BigInteger.valueOf(hInt));
        } else {
            // Pentanomial basis: x^m + x^k3 + x^k2 + x^k1 + 1
            curve = new ECCurve.F2m(m, k1, k2, k3, aBig, bBig, nBig, BigInteger.valueOf(hInt));
        }

        ECPoint g = curve.createPoint(gxBig, gyBig);
        ECParameterSpec ecSpec = new ECParameterSpec(curve, g, nBig, BigInteger.valueOf(hInt));

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("ECDSA", "BC");
        keyGen.initialize(ecSpec, new SecureRandom());
        return keyGen.generateKeyPair();
    }

    /**
     * Get EC key information (works for both F(p) and F(2^m))
     */
    public static String getECKeyInfo(Key key) throws Exception {
        StringBuilder info = new StringBuilder();

        if (key instanceof org.bouncycastle.jce.interfaces.ECPublicKey) {
            org.bouncycastle.jce.interfaces.ECPublicKey ecPub =
                (org.bouncycastle.jce.interfaces.ECPublicKey) key;

            ECPoint q = ecPub.getQ();
            ECParameterSpec params = ecPub.getParameters();

            info.append("Public Key (Q):\n");
            info.append("  x: ").append(q.getAffineXCoord().toBigInteger().toString(16).toUpperCase()).append("\n");
            info.append("  y: ").append(q.getAffineYCoord().toBigInteger().toString(16).toUpperCase()).append("\n\n");

            info.append("Curve Type: ");
            if (params.getCurve() instanceof ECCurve.Fp) {
                info.append("F(p) - Prime Field\n");
            } else if (params.getCurve() instanceof ECCurve.F2m) {
                info.append("F(2^m) - Binary Field\n");
            }
            info.append("\n");

            appendECCurveInfo(info, params);

        } else if (key instanceof org.bouncycastle.jce.interfaces.ECPrivateKey) {
            org.bouncycastle.jce.interfaces.ECPrivateKey ecPriv =
                (org.bouncycastle.jce.interfaces.ECPrivateKey) key;

            BigInteger d = ecPriv.getD();
            ECParameterSpec params = ecPriv.getParameters();

            info.append("Private Key (d):\n");
            info.append(d.toString(16).toUpperCase()).append("\n\n");

            info.append("Curve Type: ");
            if (params.getCurve() instanceof ECCurve.Fp) {
                info.append("F(p) - Prime Field\n");
            } else if (params.getCurve() instanceof ECCurve.F2m) {
                info.append("F(2^m) - Binary Field\n");
            }
            info.append("\n");

            appendECCurveInfo(info, params);
        }

        return info.toString();
    }

    /**
     * Append EC curve parameters to info string
     */
    private static void appendECCurveInfo(StringBuilder info, ECParameterSpec params) {
        ECCurve curve = params.getCurve();

        info.append("Curve Parameters:\n");
        info.append("  a: ").append(curve.getA().toBigInteger().toString(16).toUpperCase()).append("\n");
        info.append("  b: ").append(curve.getB().toBigInteger().toString(16).toUpperCase()).append("\n");

        if (curve instanceof ECCurve.Fp) {
            ECCurve.Fp fpCurve = (ECCurve.Fp) curve;
            info.append("  p (prime): ").append(fpCurve.getQ().toString(16).toUpperCase()).append("\n");
        } else if (curve instanceof ECCurve.F2m) {
            ECCurve.F2m f2mCurve = (ECCurve.F2m) curve;
            info.append("  m (degree): ").append(f2mCurve.getM()).append("\n");
        }

        info.append("\nGenerator (G):\n");
        ECPoint g = params.getG();
        info.append("  x: ").append(g.getAffineXCoord().toBigInteger().toString(16).toUpperCase()).append("\n");
        info.append("  y: ").append(g.getAffineYCoord().toBigInteger().toString(16).toUpperCase()).append("\n");

        info.append("\nOrder (n): ").append(params.getN().toString(16).toUpperCase()).append("\n");
        info.append("Cofactor (h): ").append(params.getH()).append("\n");
    }

    // ============================================================================
    // KEY EXPORT/IMPORT UTILITIES
    // ============================================================================


    /**
     * Import public key from PEM format
     */
    public static PublicKey importPublicKeyPEM(String pem) throws Exception {
        // Remove PEM headers and whitespace
        String base64 = pem
            .replaceAll("-----BEGIN PUBLIC KEY-----", "")
            .replaceAll("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s+", "");

        byte[] keyBytes = java.util.Base64.getDecoder().decode(base64);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(spec);
    }

    /**
     * Import private key from PEM format (PKCS#8)
     */
    public static PrivateKey importPrivateKeyPEM(String pem) throws Exception {
        // Remove PEM headers and whitespace
        String base64 = pem
            .replaceAll("-----BEGIN PRIVATE KEY-----", "")
            .replaceAll("-----END PRIVATE KEY-----", "")
            .replaceAll("-----BEGIN RSA PRIVATE KEY-----", "")
            .replaceAll("-----END RSA PRIVATE KEY-----", "")
            .replaceAll("\\s+", "");

        byte[] keyBytes = java.util.Base64.getDecoder().decode(base64);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(spec);
    }

    /** Imports a PKCS#8 PEM key by trying the supported asymmetric key factories. */
    public static PrivateKey importPrivateKeyPEMAuto(String pem) throws Exception {
        byte[] encoded = decodePem(pem);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(encoded);
        Exception last = null;
        for (String algorithm : new String[] { "RSA", "EC", "Ed25519", "DSA" }) {
            try {
                return KeyFactory.getInstance(algorithm, "BC").generatePrivate(spec);
            } catch (Exception e) { last = e; }
        }
        throw new IllegalArgumentException("Unsupported or invalid PKCS#8 private key", last);
    }

    /** Derives a PublicKey from a PrivateKey. */
    public static PublicKey derivePublicKey(PrivateKey privateKey) throws Exception {
        if (privateKey instanceof java.security.interfaces.RSAPrivateCrtKey) {
            java.security.interfaces.RSAPrivateCrtKey rsa = (java.security.interfaces.RSAPrivateCrtKey) privateKey;
            java.security.spec.RSAPublicKeySpec spec = new java.security.spec.RSAPublicKeySpec(rsa.getModulus(), rsa.getPublicExponent());
            return KeyFactory.getInstance("RSA", "BC").generatePublic(spec);
        } else if (privateKey instanceof org.bouncycastle.jce.interfaces.ECPrivateKey) {
            org.bouncycastle.jce.interfaces.ECPrivateKey ec = (org.bouncycastle.jce.interfaces.ECPrivateKey) privateKey;
            org.bouncycastle.math.ec.ECPoint q = ec.getParameters().getG().multiply(ec.getD());
            org.bouncycastle.jce.spec.ECPublicKeySpec spec = new org.bouncycastle.jce.spec.ECPublicKeySpec(q, ec.getParameters());
            return KeyFactory.getInstance(privateKey.getAlgorithm(), "BC").generatePublic(spec);
        } else if (privateKey instanceof java.security.interfaces.ECPrivateKey) {
            java.security.interfaces.ECPrivateKey ec = (java.security.interfaces.ECPrivateKey) privateKey;
            java.security.spec.ECParameterSpec params = ec.getParams();
            org.bouncycastle.jce.spec.ECParameterSpec bcParams = org.bouncycastle.jcajce.provider.asymmetric.util.EC5Util.convertSpec(params);
            org.bouncycastle.math.ec.ECPoint q = bcParams.getG().multiply(ec.getS());
            java.security.spec.ECPoint w = new java.security.spec.ECPoint(q.getAffineXCoord().toBigInteger(), q.getAffineYCoord().toBigInteger());
            java.security.spec.ECPublicKeySpec spec = new java.security.spec.ECPublicKeySpec(w, params);
            return KeyFactory.getInstance(privateKey.getAlgorithm(), "BC").generatePublic(spec);
        }
        throw new IllegalArgumentException("Cannot derive public key from this private key type: " + privateKey.getClass().getName());
    }

    /** Imports an X.509 SubjectPublicKeyInfo PEM key by trying supported factories. */
    public static PublicKey importPublicKeyPEMAuto(String pem) throws Exception {
        byte[] encoded = decodePem(pem);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(encoded);
        Exception last = null;
        for (String algorithm : new String[] { "RSA", "EC", "Ed25519", "DSA" }) {
            try {
                return KeyFactory.getInstance(algorithm, "BC").generatePublic(spec);
            } catch (Exception e) { last = e; }
        }
        throw new IllegalArgumentException("Unsupported or invalid public key", last);
    }

    private static byte[] decodePem(String pem) {
        if (pem == null || pem.isBlank()) throw new IllegalArgumentException("PEM material is required");
        String base64 = pem.replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "").replaceAll("\\s+", "");
        return java.util.Base64.getDecoder().decode(base64);
    }

    /**
     * Export public key to PEM format
     */
    public static String exportPublicKeyPEM(PublicKey publicKey) throws Exception {
        byte[] encoded = publicKey.getEncoded();
        String base64 = java.util.Base64.getEncoder().encodeToString(encoded);

        StringBuilder pem = new StringBuilder();
        pem.append("-----BEGIN PUBLIC KEY-----\n");

        // Split base64 into 64-character lines
        for (int i = 0; i < base64.length(); i += 64) {
            pem.append(base64.substring(i, Math.min(i + 64, base64.length()))).append("\n");
        }

        pem.append("-----END PUBLIC KEY-----\n");
        return pem.toString();
    }

    /**
     * Export private key to PEM format (PKCS#8)
     */
    public static String exportPrivateKeyPEM(PrivateKey privateKey) throws Exception {
        byte[] encoded = privateKey.getEncoded();
        String base64 = java.util.Base64.getEncoder().encodeToString(encoded);

        StringBuilder pem = new StringBuilder();
        pem.append("-----BEGIN PRIVATE KEY-----\n");

        for (int i = 0; i < base64.length(); i += 64) {
            pem.append(base64.substring(i, Math.min(i + 64, base64.length()))).append("\n");
        }

        pem.append("-----END PRIVATE KEY-----\n");
        return pem.toString();
    }

    /**
     * Export key to DER format (binary)
     */
    public static byte[] exportKeyDER(Key key) {
        return key.getEncoded();
    }

    // ============================================================================
    // Ed25519 KEY GENERATION (modern signature algorithm)
    // ============================================================================

    /**
     * Generate Ed25519 key pair
     * Ed25519 is a modern elliptic curve signature scheme (255-bit curve)
     *
     * @return KeyPair containing public and private keys
     */
    public static KeyPair generateEd25519KeyPair() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("Ed25519", "BC");
        return keyGen.generateKeyPair();
    }

    // ============================================================================
    // Ed25519 KEY IMPORT (for digital signatures)
    // ============================================================================

    /**
     * Import Ed25519 public key from PEM format
     */
    public static PublicKey importEd25519PublicKeyPEM(String pem) throws Exception {
        pem = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                 .replace("-----END PUBLIC KEY-----", "")
                 .replaceAll("\\s", "");

        byte[] encoded = java.util.Base64.getDecoder().decode(pem);

        KeyFactory keyFactory = KeyFactory.getInstance("Ed25519", "BC");
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
        return keyFactory.generatePublic(keySpec);
    }

    /**
     * Import Ed25519 private key from PEM format
     */
    public static PrivateKey importEd25519PrivateKeyPEM(String pem) throws Exception {
        pem = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                 .replace("-----END PRIVATE KEY-----", "")
                 .replaceAll("\\s", "");

        byte[] encoded = java.util.Base64.getDecoder().decode(pem);

        KeyFactory keyFactory = KeyFactory.getInstance("Ed25519", "BC");
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
        return keyFactory.generatePrivate(keySpec);
    }

    // ============================================================================
    // ECDSA KEY IMPORT (for digital signatures)
    // ============================================================================

    /**
     * Import ECDSA public key from PEM format
     */
    public static PublicKey importECPublicKeyPEM(String pem) throws Exception {
        pem = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                 .replace("-----END PUBLIC KEY-----", "")
                 .replaceAll("\\s", "");

        byte[] encoded = java.util.Base64.getDecoder().decode(pem);

        KeyFactory keyFactory = KeyFactory.getInstance("EC", "BC");
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
        return keyFactory.generatePublic(keySpec);
    }

    /**
     * Import ECDSA private key from PEM format
     */
    public static PrivateKey importECPrivateKeyPEM(String pem) throws Exception {
        pem = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                 .replace("-----END PRIVATE KEY-----", "")
                 .replace("-----BEGIN EC PRIVATE KEY-----", "")
                 .replace("-----END EC PRIVATE KEY-----", "")
                 .replaceAll("\\s", "");

        byte[] encoded = java.util.Base64.getDecoder().decode(pem);

        KeyFactory keyFactory = KeyFactory.getInstance("EC", "BC");
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
        return keyFactory.generatePrivate(keySpec);
    }
}
