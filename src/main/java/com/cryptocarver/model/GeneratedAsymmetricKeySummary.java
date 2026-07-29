package com.cryptocarver.model;

import com.cryptocarver.crypto.AsymmetricKeyOperations;
import com.cryptocarver.crypto.hsm.KeyMaterialFactory;

import java.io.Serializable;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure model encapsulating metadata summary of a generated asymmetric key pair (RSA, ECDSA, DSA, Ed25519).
 *
 * Security: Private key bytes and private key PEM strings are marked transient and excluded from
 * toString() and default JSON serialization to prevent secret key leakage.
 */
public class GeneratedAsymmetricKeySummary implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String algorithm;
    private final String curveOrKeySize;
    private final String publicFingerprintTruncated;
    private final String publicKeyLength;
    private final String privateKeyLength;
    private final String createdAt;
    private final List<String> compatibleUses;
    private final String origin;
    private String savedStatus;

    private final String publicKeyPem;
    private final transient String privateKeyPem;
    private final transient byte[] publicKeyBytes;
    private final transient byte[] privateKeyBytes;
    private final transient KeyPair keyPair;

    public GeneratedAsymmetricKeySummary(KeyPair keyPair, String algorithm, String curveOrKeySize) {
        if (keyPair == null || keyPair.getPublic() == null || keyPair.getPrivate() == null) {
            throw new IllegalArgumentException("KeyPair and its public/private components must not be null");
        }
        this.algorithm = algorithm != null ? algorithm : keyPair.getPublic().getAlgorithm();
        this.curveOrKeySize = curveOrKeySize != null ? curveOrKeySize : "Standard";
        this.origin = "Generated locally";
        this.createdAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        PublicKey pub = keyPair.getPublic();
        PrivateKey priv = keyPair.getPrivate();
        this.keyPair = keyPair;

        byte[] pubEncoded = pub.getEncoded() != null ? pub.getEncoded() : new byte[0];
        byte[] privEncoded = priv.getEncoded() != null ? priv.getEncoded() : new byte[0];

        this.publicKeyBytes = pubEncoded.clone();
        this.privateKeyBytes = privEncoded.clone();

        String pubPem = "";
        String privPem = "";
        try {
            pubPem = AsymmetricKeyOperations.exportPublicKeyPEM(pub);
            privPem = AsymmetricKeyOperations.exportPrivateKeyPEM(priv);
        } catch (Exception e) {
            pubPem = "Error exporting public PEM";
            privPem = "Error exporting private PEM";
        }
        this.publicKeyPem = pubPem;
        this.privateKeyPem = privPem;

        String fp = KeyMaterialFactory.generateFingerprint(pubEncoded);
        this.publicFingerprintTruncated = (fp != null && !fp.isEmpty()) ? fp.substring(0, Math.min(16, fp.length())) : "unknown";

        int pubBits = pubEncoded.length * 8;
        int privBits = privEncoded.length * 8;
        this.publicKeyLength = pubBits + " bits (" + pubEncoded.length + " bytes)";
        this.privateKeyLength = privBits + " bits (" + privEncoded.length + " bytes)";

        List<String> uses = new ArrayList<>();
        String algoUpper = this.algorithm.toUpperCase();
        if (algoUpper.contains("RSA")) {
            uses.add("ENCRYPTION");
            uses.add("SIGNATURES");
            uses.add("CERTIFICATES");
        } else if (algoUpper.contains("ECDSA") || algoUpper.contains("EC")) {
            uses.add("SIGNATURES");
            uses.add("CERTIFICATES");
        } else if (algoUpper.contains("DSA")) {
            uses.add("LEGACY_SIGNATURES");
            uses.add("CERTIFICATES");
        } else if (algoUpper.contains("ED25519") || algoUpper.contains("EDDSA")) {
            uses.add("MODERN_SIGNATURES");
            uses.add("CERTIFICATES");
        } else {
            uses.add("SIGNATURES");
        }
        this.compatibleUses = Collections.unmodifiableList(uses);
        this.savedStatus = null;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public String getCurveOrKeySize() {
        return curveOrKeySize;
    }

    public String getPublicFingerprintTruncated() {
        return publicFingerprintTruncated;
    }

    public String getPublicKeyLength() {
        return publicKeyLength;
    }

    public String getPrivateKeyLength() {
        return privateKeyLength;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public List<String> getCompatibleUses() {
        return compatibleUses;
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

    public String getPublicKeyPem() {
        return publicKeyPem;
    }

    public String getPrivateKeyPem() {
        return privateKeyPem;
    }

    public byte[] getPublicKeyBytes() {
        return publicKeyBytes != null ? publicKeyBytes.clone() : new byte[0];
    }

    public byte[] getPrivateKeyBytes() {
        return privateKeyBytes != null ? privateKeyBytes.clone() : new byte[0];
    }

    /** In-memory pair for handing a freshly generated key to compatible laboratory tools. */
    public KeyPair getKeyPair() {
        return keyPair;
    }

    public boolean isEncryptionSupported() {
        return compatibleUses.contains("ENCRYPTION");
    }

    public boolean isSignatureSupported() {
        return compatibleUses.contains("SIGNATURES") || compatibleUses.contains("LEGACY_SIGNATURES") || compatibleUses.contains("MODERN_SIGNATURES");
    }

    public boolean isCertificateSupported() {
        return compatibleUses.contains("CERTIFICATES");
    }

    @Override
    public String toString() {
        return "GeneratedAsymmetricKeySummary{" +
                "algorithm='" + algorithm + '\'' +
                ", curveOrKeySize='" + curveOrKeySize + '\'' +
                ", fingerprint='" + publicFingerprintTruncated + '\'' +
                ", publicKeyLength='" + publicKeyLength + '\'' +
                ", createdAt='" + createdAt + '\'' +
                ", compatibleUses=" + compatibleUses +
                ", origin='" + origin + '\'' +
                ", savedStatus='" + savedStatus + '\'' +
                '}';
    }
}
