package com.cryptoforge.asn1;

import org.bouncycastle.asn1.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * ASN.1 Parser using BouncyCastle
 */
public class ASN1Parser {

    // Common OID mappings
    private static final Map<String, String> OID_NAMES = new HashMap<>();

    static {
        // PKCS#7 / CMS Content Types
        OID_NAMES.put("1.2.840.113549.1.7.1", "data (PKCS #7)");
        OID_NAMES.put("1.2.840.113549.1.7.2", "signedData (PKCS #7)");
        OID_NAMES.put("1.2.840.113549.1.7.3", "envelopedData (PKCS #7)");
        OID_NAMES.put("1.2.840.113549.1.7.4", "signedAndEnvelopedData (PKCS #7)");
        OID_NAMES.put("1.2.840.113549.1.7.5", "digestedData (PKCS #7)");
        OID_NAMES.put("1.2.840.113549.1.7.6", "encryptedData (PKCS #7)");

        // PKCS#9 Attributes
        OID_NAMES.put("1.2.840.113549.1.9.3", "contentType (PKCS #9)");
        OID_NAMES.put("1.2.840.113549.1.9.4", "messageDigest (PKCS #9)");
        OID_NAMES.put("1.2.840.113549.1.9.5", "signingTime (PKCS #9)");
        OID_NAMES.put("1.2.840.113549.1.9.52", "cmsAlgorithmProtection (PKCS #9)");
        OID_NAMES.put("1.2.840.113549.1.9.16.2.47", "signingCertificateV2 (PKCS #9)");

        // Signature algorithms
        OID_NAMES.put("1.2.840.113549.1.1.1", "rsaEncryption");
        OID_NAMES.put("1.2.840.113549.1.1.5", "sha1WithRSAEncryption");
        OID_NAMES.put("1.2.840.113549.1.1.11", "sha256WithRSAEncryption");
        OID_NAMES.put("1.2.840.113549.1.1.12", "sha384WithRSAEncryption");
        OID_NAMES.put("1.2.840.113549.1.1.13", "sha512WithRSAEncryption");
        OID_NAMES.put("1.2.840.10045.2.1", "ecPublicKey (ANSI X9.62 public key type)");
        OID_NAMES.put("1.2.840.10045.4.3.2", "ecdsa-with-SHA256 (ANSI X9.62 ECDSA algorithm with SHA256)");
        OID_NAMES.put("1.2.840.10045.4.3.3", "ecdsa-with-SHA384 (ANSI X9.62 ECDSA algorithm with SHA384)");
        OID_NAMES.put("1.2.840.10045.4.3.4", "ecdsa-with-SHA512 (ANSI X9.62 ECDSA algorithm with SHA512)");

        // Elliptic curves
        OID_NAMES.put("1.2.840.10045.3.1.7", "secp256r1 / prime256v1 (ANSI X9.62 named elliptic curve)");
        OID_NAMES.put("1.3.132.0.34", "secp384r1 (SECG named elliptic curve)");
        OID_NAMES.put("1.3.132.0.35", "secp521r1 (SECG named elliptic curve)");

        // X.509 extensions
        OID_NAMES.put("2.5.29.15", "keyUsage (X.509 extension)");
        OID_NAMES.put("2.5.29.19", "basicConstraints (X.509 extension)");
        OID_NAMES.put("2.5.29.17", "subjectAltName (X.509 extension)");
        OID_NAMES.put("2.5.29.37", "extKeyUsage (X.509 extension)");
        OID_NAMES.put("2.5.29.14", "subjectKeyIdentifier (X.509 extension)");
        OID_NAMES.put("2.5.29.35", "authorityKeyIdentifier (X.509 extension)");

        // Distinguished name attributes
        OID_NAMES.put("2.5.4.3", "CN / commonName (X.520 DN component)");
        OID_NAMES.put("2.5.4.6", "C / countryName (X.520 DN component)");
        OID_NAMES.put("2.5.4.7", "L / localityName (X.520 DN component)");
        OID_NAMES.put("2.5.4.8", "ST / stateOrProvinceName (X.520 DN component)");
        OID_NAMES.put("2.5.4.10", "O / organizationName (X.520 DN component)");
        OID_NAMES.put("2.5.4.11", "OU / organizationalUnitName (X.520 DN component)");

        // Hash algorithms
        OID_NAMES.put("2.16.840.1.101.3.4.2.1", "sha-256 (NIST Algorithm)");
        OID_NAMES.put("2.16.840.1.101.3.4.2.2", "sha-384 (NIST Algorithm)");
        OID_NAMES.put("2.16.840.1.101.3.4.2.3", "sha-512 (NIST Algorithm)");
    }

    /** Loads local OID descriptions from a JSON object such as {"1.2.3.4":"My OID"}. */
    public static synchronized int loadCustomOidNames(Path file) throws java.io.IOException {
        if (file == null || !Files.isRegularFile(file)) throw new java.io.IOException("OID registry file not found");
        Map<String, String> custom = new Gson().fromJson(Files.readString(file), new TypeToken<Map<String, String>>() {}.getType());
        if (custom == null) return 0;
        int count = 0;
        for (Map.Entry<String, String> entry : custom.entrySet()) {
            String oid = entry.getKey() == null ? "" : entry.getKey().trim();
            String name = entry.getValue() == null ? "" : entry.getValue().trim();
            if (!oid.matches("[0-2](?:\\.[0-9]+)+") || name.isEmpty()) continue;
            OID_NAMES.put(oid, name); count++;
        }
        return count;
    }

    /**
     * Parse ASN.1 DER/BER encoded data
     */
    public static ASN1TreeNode parse(byte[] data) throws IOException {
        return parse(data, 32); // Default: truncate hex at 32 bytes for display
    }

    /**
     * Parse ASN.1 DER/BER encoded data with custom truncation
     *
     * @param data           ASN.1 encoded data
     * @param maxBytesForHex Maximum bytes to show in hex (-1 for no limit)
     */
    public static ASN1TreeNode parse(byte[] data, int maxBytesForHex) throws IOException {
        ASN1InputStream asn1Stream = new ASN1InputStream(new ByteArrayInputStream(data));
        ASN1Primitive primitive = asn1Stream.readObject();
        asn1Stream.close();

        ASN1TreeNode tree = parseObject(primitive, 0, "Root", maxBytesForHex);

        // Apply contextual labels based on detected structure type
        String detectedType = detectType(data);

        if (detectedType.contains("PKCS #7") || detectedType.contains("CMS")) {
            PKCS7Schema.applyPKCS7Labels(tree);
        } else if (detectedType.contains("PKCS #8")) {
            PKCS8PrivateKeySchema.applyPKCS8Labels(tree);
        } else if (detectedType.contains("PKCS #1 RSA Private Key")) {
            PKCS1RSAKeySchema.applyPKCS1RSAKeyLabels(tree);
        } else if (detectedType.contains("PKCS #1 RSA Public Key")) {
            PKCS1RSAKeySchema.applyPKCS1RSAPublicKeyLabels(tree);
        } else if (detectedType.contains("Certificate Request") || detectedType.contains("CSR")) {
            PKCS10CSRSchema.applyPKCS10Labels(tree);
        } else if (detectedType.contains("Certificate Revocation List") || detectedType.contains("CRL")) {
            CRLSchema.applyCRLLabels(tree);
        } else if (detectedType.contains("X.509 Certificate") && !detectedType.contains("PKCS")) {
            // Only apply X.509 if not part of PKCS#7 (PKCS7Schema handles embedded certs)
            X509CertificateSchema.applyX509Labels(tree);
        }

        return tree;
    }

    /**
     * Parse ASN.1 object recursively
     */
    private static ASN1TreeNode parseObject(ASN1Encodable encodable, int depth, String context, int maxBytesForHex) {
        ASN1Primitive primitive = encodable.toASN1Primitive();

        String tag = getTagName(primitive);
        int tagNumber = getTagNumber(primitive);
        boolean constructed = isConstructed(primitive);
        byte[] encoded = getEncoded(primitive);
        int length = encoded != null ? encoded.length : 0;
        String decodedValue = "";

        // Create label
        String label = tag;
        if (!context.equals("Root") && !context.isEmpty()) {
            label = context + ": " + tag;
        }

        ASN1TreeNode node = new ASN1TreeNode(label, tag, tagNumber, constructed,
                encoded, decodedValue, depth, length);

        // Parse based on type
        if (primitive instanceof ASN1Sequence) {
            ASN1Sequence sequence = (ASN1Sequence) primitive;
            node = new ASN1TreeNode(label, tag, tagNumber, true, encoded,
                    "(" + sequence.size() + " elements)", depth, length);

            int index = 0;
            for (Enumeration<?> e = sequence.getObjects(); e.hasMoreElements();) {
                ASN1Encodable element = (ASN1Encodable) e.nextElement();
                ASN1TreeNode child = parseObject(element, depth + 1, "", maxBytesForHex);
                node.addChild(child);
                index++;
            }

        } else if (primitive instanceof ASN1Set) {
            ASN1Set set = (ASN1Set) primitive;
            node = new ASN1TreeNode(label, tag, tagNumber, true, encoded,
                    "(" + set.size() + " elements)", depth, length);

            for (Enumeration<?> e = set.getObjects(); e.hasMoreElements();) {
                ASN1Encodable element = (ASN1Encodable) e.nextElement();
                ASN1TreeNode child = parseObject(element, depth + 1, "", maxBytesForHex);
                node.addChild(child);
            }

        } else if (primitive instanceof ASN1TaggedObject) {
            ASN1TaggedObject tagged = (ASN1TaggedObject) primitive;
            int tagNo = tagged.getTagNo();
            label = "[" + tagNo + "] " + (tagged.isExplicit() ? "EXPLICIT" : "IMPLICIT");
            node = new ASN1TreeNode(label, tag, tagNo, true, encoded, "", depth, length);

            ASN1Encodable baseObject = tagged.getBaseObject();
            ASN1TreeNode child = parseObject(baseObject, depth + 1, "", maxBytesForHex);
            node.addChild(child);

        } else if (primitive instanceof ASN1Integer) {
            ASN1Integer integer = (ASN1Integer) primitive;
            BigInteger value = integer.getValue();
            int bitLength = value.bitLength();

            // Build decoded value
            String valueStr = value.toString();

            // Truncate for display if too long (and not in export mode)
            if (maxBytesForHex != -1 && valueStr.length() > 80) {
                // Show first 40 and last 20 chars with ... in between
                decodedValue = valueStr.substring(0, 40) + "..." + valueStr.substring(valueStr.length() - 20);
            } else {
                decodedValue = valueStr;
            }

            // Add hex representation for small integers
            if (bitLength <= 32) {
                decodedValue += " (0x" + value.toString(16).toUpperCase() + ")";
            }

            // Create label with bit length (like JS decoder)
            String integerLabel = "INTEGER (" + bitLength + " bit)";
            node = new ASN1TreeNode(integerLabel, tag, tagNumber, false, encoded, decodedValue, depth, length);

        } else if (primitive instanceof ASN1ObjectIdentifier) {
            ASN1ObjectIdentifier oid = (ASN1ObjectIdentifier) primitive;
            String oidString = oid.getId();
            decodedValue = oidString;

            String oidName = OID_NAMES.get(oidString);
            if (oidName != null) {
                decodedValue = oidString + " (" + oidName + ")";
            }

            // Create label - OID names already have context
            String enhancedLabel = "OID";
            node = new ASN1TreeNode(enhancedLabel, tag, tagNumber, false, encoded, decodedValue, depth, length);

        } else if (primitive instanceof ASN1OctetString) {
            ASN1OctetString octetString = (ASN1OctetString) primitive;
            byte[] octets = octetString.getOctets();

            // Try to parse as nested ASN.1 first
            boolean isNestedASN1 = false;
            try {
                // Heuristic: check if looks like ASN.1 (starts with sequence/set tag)
                if (octets.length > 2 && (octets[0] == 0x30 || octets[0] == 0x31)) {
                    ASN1InputStream innerStream = new ASN1InputStream(new ByteArrayInputStream(octets));
                    ASN1Primitive innerPrimitive = innerStream.readObject();
                    innerStream.close();

                    if (innerPrimitive != null) {
                        node = new ASN1TreeNode(label + " (contains ASN.1)", tag, tagNumber, true,
                                encoded, "", depth, length);
                        ASN1TreeNode child = parseObject(innerPrimitive, depth + 1, "", maxBytesForHex);
                        node.addChild(child);
                        isNestedASN1 = true;
                    }
                }
            } catch (Exception e) {
                // Not nested ASN.1
            }

            if (!isNestedASN1) {
                decodedValue = bytesToHex(octets, maxBytesForHex);

                // Try to decode as string (if not too long and looks like text)
                String stringValue = tryDecodeString(octets);
                if (stringValue != null) {
                    decodedValue += " (" + stringValue + ")";
                }

                node = new ASN1TreeNode(label, tag, tagNumber, false, encoded, decodedValue, depth, length);
            }

        } else if (primitive instanceof ASN1BitString) {
            ASN1BitString bitString = (ASN1BitString) primitive;
            byte[] bytes = bitString.getBytes();
            int padBits = bitString.getPadBits();
            int bitLength = (bytes.length * 8) - padBits;

            decodedValue = bytesToHex(bytes, maxBytesForHex);
            if (padBits > 0) {
                decodedValue += " (unused bits: " + padBits + ")";
            }

            // Create label with bit length (like JS decoder)
            String bitStringLabel = "BIT STRING (" + bitLength + " bit)";
            node = new ASN1TreeNode(bitStringLabel, tag, tagNumber, false, encoded, decodedValue, depth, length);

        } else if (primitive instanceof ASN1UTCTime) {
            ASN1UTCTime utcTime = (ASN1UTCTime) primitive;
            try {
                Date date = utcTime.getDate();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
                decodedValue = sdf.format(date);
            } catch (Exception e) {
                decodedValue = utcTime.getTime();
            }
            node = new ASN1TreeNode("UTCTime", tag, tagNumber, false, encoded, decodedValue, depth, length);

        } else if (primitive instanceof ASN1GeneralizedTime) {
            ASN1GeneralizedTime genTime = (ASN1GeneralizedTime) primitive;
            try {
                Date date = genTime.getDate();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
                decodedValue = sdf.format(date);
            } catch (Exception e) {
                decodedValue = genTime.getTime();
            }
            node = new ASN1TreeNode("GeneralizedTime", tag, tagNumber, false, encoded, decodedValue, depth, length);

        } else if (primitive instanceof ASN1String) {
            ASN1String asn1String = (ASN1String) primitive;
            decodedValue = "\"" + asn1String.getString() + "\"";
            node = new ASN1TreeNode(label, tag, tagNumber, false, encoded, decodedValue, depth, length);

        } else if (primitive instanceof ASN1Boolean) {
            ASN1Boolean bool = (ASN1Boolean) primitive;
            decodedValue = String.valueOf(bool.isTrue());
            node = new ASN1TreeNode(label, tag, tagNumber, false, encoded, decodedValue, depth, length);

        } else if (primitive instanceof ASN1Null) {
            node = new ASN1TreeNode(label, tag, tagNumber, false, encoded, "NULL", depth, length);

        } else {
            // Unknown type - show hex
            decodedValue = bytesToHex(encoded, maxBytesForHex);
            node = new ASN1TreeNode(label + " (unknown)", tag, tagNumber, false, encoded, decodedValue, depth, length);
        }

        return node;
    }

    /**
     * Get tag name for ASN.1 primitive
     */
    private static String getTagName(ASN1Primitive primitive) {
        if (primitive instanceof ASN1Sequence)
            return "SEQUENCE";
        if (primitive instanceof ASN1Set)
            return "SET";
        if (primitive instanceof ASN1Integer)
            return "INTEGER";
        if (primitive instanceof ASN1ObjectIdentifier)
            return "OBJECT IDENTIFIER";
        if (primitive instanceof ASN1OctetString)
            return "OCTET STRING";
        if (primitive instanceof ASN1BitString)
            return "BIT STRING";
        if (primitive instanceof ASN1UTCTime)
            return "UTCTime";
        if (primitive instanceof ASN1GeneralizedTime)
            return "GeneralizedTime";
        if (primitive instanceof ASN1Boolean)
            return "BOOLEAN";
        if (primitive instanceof ASN1Null)
            return "NULL";
        if (primitive instanceof ASN1TaggedObject)
            return "TAGGED";
        if (primitive instanceof DERPrintableString)
            return "PrintableString";
        if (primitive instanceof DERUTF8String)
            return "UTF8String";
        if (primitive instanceof DERIA5String)
            return "IA5String";
        if (primitive instanceof DERBMPString)
            return "BMPString";
        if (primitive instanceof ASN1String)
            return "STRING";

        return "UNKNOWN";
    }

    /**
     * Get tag number
     */
    private static int getTagNumber(ASN1Primitive primitive) {
        try {
            byte[] encoded = primitive.getEncoded();
            if (encoded.length > 0) {
                return encoded[0] & 0x1F;
            }
        } catch (Exception e) {
            // Ignore
        }
        return 0;
    }

    /**
     * Check if constructed
     */
    private static boolean isConstructed(ASN1Primitive primitive) {
        return primitive instanceof ASN1Sequence ||
                primitive instanceof ASN1Set ||
                primitive instanceof ASN1TaggedObject;
    }

    /**
     * Get encoded bytes
     */
    private static byte[] getEncoded(ASN1Primitive primitive) {
        try {
            return primitive.getEncoded();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    /**
     * Try to decode bytes as a printable string
     * Returns the string in quotes if successful, or null if mostly binary
     */
    private static String tryDecodeString(byte[] bytes) {
        if (bytes == null || bytes.length == 0)
            return null;

        // Don't try for very large arrays (likely binary)
        if (bytes.length > 1024)
            return null;

        int printable = 0;
        int nonPrintable = 0;

        for (byte b : bytes) {
            int val = b & 0xFF;
            // Check for printable ASCII (0x20-0x7E) plus common whitespace
            if ((val >= 0x20 && val <= 0x7E) || val == 0x09 || val == 0x0A || val == 0x0D) {
                printable++;
            } else {
                nonPrintable++;
            }
        }

        // If mostly printable (> 90%), return string
        if (bytes.length > 0 && (double) printable / bytes.length > 0.9) {
            try {
                // Try UTF-8
                String s = new String(bytes, "UTF-8");
                // Double check for weird chars
                if (s.chars().noneMatch(c -> c < 32 && c != 9 && c != 10 && c != 13)) {
                    return "\"" + s + "\"";
                }
                return null;
            } catch (Exception e) {
                return null;
            }
        }

        return null;
    }

    /**
     * Convert bytes to hex string with limit
     *
     * @param bytes    Byte array
     * @param maxBytes Maximum bytes to display (-1 for no limit)
     */
    private static String bytesToHex(byte[] bytes, int maxBytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }

        // If maxBytes is -1, show all bytes (for export)
        int length = (maxBytes == -1) ? bytes.length : Math.min(bytes.length, maxBytes);
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < length; i++) {
            sb.append(String.format("%02X", bytes[i]));
            if (i < length - 1) {
                sb.append(" ");
            }
        }

        // Only add truncation message if we actually truncated
        if (maxBytes != -1 && bytes.length > maxBytes) {
            sb.append(" ... (").append(bytes.length).append(" bytes total)");
        }

        return sb.toString();
    }

    /**
     * Detect ASN.1 structure type
     */
    public static String detectType(byte[] data) {
        try {
            ASN1InputStream asn1Stream = new ASN1InputStream(new ByteArrayInputStream(data));
            ASN1Primitive primitive = asn1Stream.readObject();
            asn1Stream.close();

            if (primitive instanceof ASN1Sequence) {
                ASN1Sequence seq = (ASN1Sequence) primitive;

                // PKCS #7 / CMS: SEQUENCE { contentType OID, [0] EXPLICIT content }
                if (seq.size() == 2) {
                    ASN1Encodable first = seq.getObjectAt(0);
                    ASN1Encodable second = seq.getObjectAt(1);

                    if (first instanceof ASN1ObjectIdentifier) {
                        ASN1ObjectIdentifier oid = (ASN1ObjectIdentifier) first;
                        String oidStr = oid.getId();

                        // Check for PKCS#7 content types
                        if (oidStr.equals("1.2.840.113549.1.7.2")) {
                            return "PKCS #7 SignedData (CMS)";
                        } else if (oidStr.equals("1.2.840.113549.1.7.3")) {
                            return "PKCS #7 EnvelopedData (CMS)";
                        } else if (oidStr.equals("1.2.840.113549.1.7.1")) {
                            return "PKCS #7 Data (CMS)";
                        } else if (oidStr.startsWith("1.2.840.113549.1.7.")) {
                            return "PKCS #7 / CMS Structure";
                        }
                    }
                }

                // PKCS#8 Private Key Info: SEQUENCE with 3-4 elements
                // CHECK THIS BEFORE X.509/CSR (they also have 3 elements)
                if (seq.size() >= 3 && seq.size() <= 4) {
                    ASN1Encodable first = seq.getObjectAt(0);
                    ASN1Encodable second = seq.getObjectAt(1);
                    ASN1Encodable third = seq.getObjectAt(2);

                    // Check for PKCS#8 pattern: INTEGER (version), SEQUENCE (algorithm), OCTET
                    // STRING (key)
                    if (first instanceof ASN1Integer &&
                            second instanceof ASN1Sequence &&
                            third instanceof ASN1OctetString) {

                        ASN1Integer version = (ASN1Integer) first;
                        if (version.getValue().intValue() == 0) {
                            return "PKCS #8 Private Key Info";
                        }
                    }
                }

                // X.509 Certificate or CRL: Both are 3-element SEQUENCE
                if (seq.size() == 3) {
                    ASN1Encodable first = seq.getObjectAt(0);
                    ASN1Encodable second = seq.getObjectAt(1);
                    ASN1Encodable third = seq.getObjectAt(2);

                    if (first instanceof ASN1Sequence &&
                            second instanceof ASN1Sequence &&
                            third instanceof ASN1BitString) {

                        ASN1Sequence firstSeq = (ASN1Sequence) first;

                        // Distinguish between X.509 Certificate and CRL
                        if (firstSeq.size() >= 6 && firstSeq.size() <= 10) {
                            // Check if it has typical certificate fields
                            // TBSCertificate typically has: version, serial, signature, issuer, validity,
                            // subject, publicKey
                            return "X.509 Certificate";
                        } else if (firstSeq.size() >= 4 && firstSeq.size() <= 7) {
                            // TBSCertList has fewer fields: version?, signature, issuer, thisUpdate,
                            // nextUpdate?, revokedCerts?, extensions?
                            // Check for time fields (UTCTime/GeneralizedTime)
                            for (int i = 0; i < Math.min(firstSeq.size(), 5); i++) {
                                ASN1Encodable elem = firstSeq.getObjectAt(i);
                                if (elem instanceof ASN1UTCTime || elem instanceof ASN1GeneralizedTime) {
                                    return "Certificate Revocation List (CRL)";
                                }
                            }
                        }

                        // Default to certificate if can't determine
                        return "X.509 Certificate or CSR";
                    }

                    // PKCS#10 CSR also has 3 elements but different structure
                    return "Certificate Request (CSR) or X.509 Certificate";
                }

                // PKCS#1 RSA Private Key: SEQUENCE with 9+ elements, starts with INTEGER
                // version
                if (seq.size() >= 9) {
                    ASN1Encodable first = seq.getObjectAt(0);
                    if (first instanceof ASN1Integer) {
                        ASN1Integer version = (ASN1Integer) first;
                        // PKCS#1 version is typically 0
                        if (version.getValue().intValue() == 0) {
                            return "PKCS #1 RSA Private Key";
                        }
                    }
                }

                // PKCS#1 RSA Public Key: SEQUENCE with exactly 2 INTEGERs (modulus, exponent)
                if (seq.size() == 2) {
                    ASN1Encodable first = seq.getObjectAt(0);
                    ASN1Encodable second = seq.getObjectAt(1);
                    if (first instanceof ASN1Integer && second instanceof ASN1Integer) {
                        ASN1Integer firstInt = (ASN1Integer) first;
                        ASN1Integer secondInt = (ASN1Integer) second;
                        // Public exponent is typically 65537 (0x10001)
                        if (secondInt.getValue().intValue() == 65537 ||
                                secondInt.getValue().intValue() == 3 ||
                                secondInt.getValue().intValue() == 17) {
                            return "PKCS #1 RSA Public Key";
                        }
                    }
                }
            }

            return "Unknown ASN.1 Structure";

        } catch (Exception e) {
            return "Invalid ASN.1 Data";
        }
    }
}
