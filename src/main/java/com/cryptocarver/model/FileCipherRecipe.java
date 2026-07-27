package com.cryptocarver.model;

import java.util.Objects;

/**
 * Model representing a recipe (configuration) for a File Cipher operation.
 * Designed to be serialized to JSON.
 */
public class FileCipherRecipe {
    public static final String CURRENT_VERSION = "1.0";

    private String version;
    private String algorithm;
    private boolean linesMode;
    private String lineEncoding;
    private boolean compactMode;
    private String charset;
    private String aadHex;
    private String ivNonceHex;
    private String tagRef;

    public FileCipherRecipe() {
        this.version = CURRENT_VERSION;
    }

    public FileCipherRecipe(String algorithm, boolean linesMode, String lineEncoding,
                            boolean compactMode, String charset, String aadHex,
                            String ivNonceHex, String tagRef) {
        this.version = CURRENT_VERSION;
        this.algorithm = algorithm;
        this.linesMode = linesMode;
        this.lineEncoding = lineEncoding;
        this.compactMode = compactMode;
        this.charset = charset;
        this.aadHex = aadHex;
        this.ivNonceHex = ivNonceHex;
        this.tagRef = tagRef;
    }

    public String getVersion() {
        return version;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public boolean isLinesMode() {
        return linesMode;
    }

    public String getLineEncoding() {
        return lineEncoding;
    }

    public boolean isCompactMode() {
        return compactMode;
    }

    public String getCharset() {
        return charset;
    }

    public String getAadHex() {
        return aadHex;
    }

    public String getIvNonceHex() {
        return ivNonceHex;
    }

    public String getTagRef() {
        return tagRef;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileCipherRecipe that = (FileCipherRecipe) o;
        return linesMode == that.linesMode &&
                compactMode == that.compactMode &&
                Objects.equals(version, that.version) &&
                Objects.equals(algorithm, that.algorithm) &&
                Objects.equals(lineEncoding, that.lineEncoding) &&
                Objects.equals(charset, that.charset) &&
                Objects.equals(aadHex, that.aadHex) &&
                Objects.equals(ivNonceHex, that.ivNonceHex) &&
                Objects.equals(tagRef, that.tagRef);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, algorithm, linesMode, lineEncoding, compactMode, charset, aadHex, ivNonceHex, tagRef);
    }
}
