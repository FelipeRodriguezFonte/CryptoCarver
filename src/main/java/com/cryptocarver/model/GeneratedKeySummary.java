package com.cryptocarver.model;

import com.cryptocarver.crypto.KeyOperations;
import com.cryptocarver.crypto.hsm.KeyMaterialFactory;
import com.cryptocarver.util.DataConverter;

import java.io.Serializable;

/**
 * Pure model encapsulating metadata summary of a generated symmetric key.
 *
 * Security: The raw key bytes are marked transient and excluded from toString()
 * and default JSON serialization to prevent secret material leakage.
 */
public class GeneratedKeySummary implements Serializable {

    private static final long serialVersionUID = 1L;

    private final transient byte[] rawKeyBytes;
    private final String algorithm;
    private final int bitLength;
    private final int byteLength;
    private final String kcv3BytesHex;
    private final String kcvFullHex;
    private final String kcvErrorReason;
    private final String fingerprintTruncated;
    private final String parityStatus;
    private final String origin;
    private String savedStatus;

    public GeneratedKeySummary(byte[] keyBytes, String algorithm, boolean forceOddParity) {
        this.rawKeyBytes = keyBytes != null ? keyBytes.clone() : new byte[0];
        this.algorithm = algorithm != null ? algorithm : "Unknown";
        this.byteLength = this.rawKeyBytes.length;
        this.bitLength = this.byteLength * 8;
        this.origin = "Generated locally";

        if (this.rawKeyBytes.length > 0) {
            String fp = KeyMaterialFactory.generateFingerprint(this.rawKeyBytes);
            this.fingerprintTruncated = fp != null ? fp.substring(0, Math.min(16, fp.length())) : "unknown";
        } else {
            this.fingerprintTruncated = "unknown";
        }

        String algoUpper = this.algorithm.toUpperCase();
        if (algoUpper.contains("DES")) {
            if (forceOddParity || KeyOperations.detectParity(this.rawKeyBytes) == KeyOperations.ParityType.ODD) {
                this.parityStatus = "Applied";
            } else {
                this.parityStatus = "Not applied";
            }
        } else {
            this.parityStatus = "Not applicable";
        }

        String k3 = null;
        String kFull = null;
        String err = null;

        try {
            if (this.rawKeyBytes.length == 0) {
                err = "Key buffer is empty";
            } else {
                byte[] kcv3Bytes;
                if (algoUpper.contains("DES")) {
                    kcv3Bytes = KeyOperations.calculateKCV_VISA(this.rawKeyBytes);
                } else {
                    kcv3Bytes = KeyOperations.calculateKCV_AES(this.rawKeyBytes);
                }
                k3 = DataConverter.bytesToHex(kcv3Bytes).toUpperCase();

                byte[] fullBlock = KeyOperations.calculateFullZeroBlockKCV(this.rawKeyBytes, this.algorithm);
                if (fullBlock != null) {
                    kFull = DataConverter.bytesToHex(fullBlock).toUpperCase();
                }
            }
        } catch (Exception e) {
            err = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        }

        this.kcv3BytesHex = k3;
        this.kcvFullHex = kFull;
        this.kcvErrorReason = err;
        this.savedStatus = null;
    }

    public byte[] getRawKeyBytes() {
        return rawKeyBytes != null ? rawKeyBytes.clone() : new byte[0];
    }

    public String getRawKeyHex() {
        return rawKeyBytes != null ? DataConverter.bytesToHex(rawKeyBytes).toUpperCase() : "";
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public int getBitLength() {
        return bitLength;
    }

    public int getByteLength() {
        return byteLength;
    }

    public String getFormattedLength() {
        return bitLength + " bits (" + byteLength + " bytes)";
    }

    public String getKcv3BytesHex() {
        return kcv3BytesHex;
    }

    public String getKcvFullHex() {
        return kcvFullHex;
    }

    public String getKcvErrorReason() {
        return kcvErrorReason;
    }

    public String getFormattedKcv() {
        if (kcvErrorReason != null) {
            return "KCV unavailable: " + kcvErrorReason;
        }
        if (kcv3BytesHex == null) {
            return "KCV unavailable: Unknown error";
        }
        if (kcvFullHex != null && !kcvFullHex.isEmpty() && !kcvFullHex.equalsIgnoreCase(kcv3BytesHex)) {
            return kcv3BytesHex + " (Full: " + kcvFullHex + ")";
        }
        return kcv3BytesHex;
    }

    public String getFingerprintTruncated() {
        return fingerprintTruncated;
    }

    public String getParityStatus() {
        return parityStatus;
    }

    public String getOrigin() {
        return origin;
    }

    public String getSavedStatus() {
        return savedStatus;
    }

    public void setSavedStatus(String savedStatus) {
        this.savedStatus = savedStatus;
    }

    @Override
    public String toString() {
        return "GeneratedKeySummary{" +
                "algorithm='" + algorithm + '\'' +
                ", bitLength=" + bitLength +
                ", kcv='" + getFormattedKcv() + '\'' +
                ", fingerprint='" + fingerprintTruncated + '\'' +
                ", parityStatus='" + parityStatus + '\'' +
                ", origin='" + origin + '\'' +
                ", savedStatus='" + savedStatus + '\'' +
                '}';
    }
}
