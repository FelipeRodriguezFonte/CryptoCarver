package com.cryptocarver.model;

public class MaterialDetectionResult {

    public enum MaterialType {
        PEM_PRIVATE_KEY,
        PEM_PUBLIC_KEY,
        PEM_CERTIFICATE,
        PEM_CSR,
        PEM_CRL,
        HEX,
        BASE64,
        JWK,
        JWT,
        OPENPGP_PUBLIC_KEY,
        OPENPGP_PRIVATE_KEY,
        OPENPGP_SIGNATURE,
        OPENPGP_MESSAGE,
        JSON,
        TEXT_UNKNOWN,
        EMPTY;

        public boolean isPem() {
            return this == PEM_PRIVATE_KEY || this == PEM_PUBLIC_KEY || this == PEM_CERTIFICATE
                    || this == PEM_CSR || this == PEM_CRL;
        }

        public boolean isOpenPgp() {
            return this == OPENPGP_PUBLIC_KEY || this == OPENPGP_PRIVATE_KEY
                    || this == OPENPGP_SIGNATURE || this == OPENPGP_MESSAGE;
        }
    }

    private final MaterialType type;
    private final String algorithm;
    private final Integer keySizeBits;
    private final Integer byteLength;
    private final boolean secret;
    private final String statusLabelText;
    private final String errorMessage;
    private final boolean valid;

    public MaterialDetectionResult(MaterialType type, String algorithm, Integer keySizeBits,
                                   Integer byteLength, boolean secret, String statusLabelText,
                                   String errorMessage, boolean valid) {
        this.type = type != null ? type : MaterialType.EMPTY;
        this.algorithm = algorithm;
        this.keySizeBits = keySizeBits;
        this.byteLength = byteLength;
        this.secret = secret;
        this.statusLabelText = statusLabelText != null ? statusLabelText : "";
        this.errorMessage = errorMessage;
        this.valid = valid;
    }

    public static MaterialDetectionResult empty() {
        return new MaterialDetectionResult(MaterialType.EMPTY, null, null, 0, false, "Field is empty", null, true);
    }

    public static MaterialDetectionResult invalid(String errorReason) {
        return new MaterialDetectionResult(MaterialType.TEXT_UNKNOWN, null, null, 0, false, "Invalid format", errorReason, false);
    }

    public MaterialType getType() {
        return type;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public Integer getKeySizeBits() {
        return keySizeBits;
    }

    public Integer getByteLength() {
        return byteLength;
    }

    public boolean isSecret() {
        return secret;
    }

    public String getStatusLabelText() {
        return statusLabelText;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isValid() {
        return valid;
    }

    public boolean isCompatibleWith(MaterialType... expectedTypes) {
        if (!valid || expectedTypes == null || expectedTypes.length == 0) return true;
        for (MaterialType expected : expectedTypes) {
            if (this.type == expected) return true;
            if (expected == MaterialType.PEM_PUBLIC_KEY && (this.type == MaterialType.PEM_CERTIFICATE)) return true;
            if (expected == MaterialType.HEX && (this.type == MaterialType.BASE64 || this.type == MaterialType.TEXT_UNKNOWN)) return true;
            if (expected == MaterialType.TEXT_UNKNOWN) return true;
        }
        return false;
    }
}
