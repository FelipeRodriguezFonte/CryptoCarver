package com.cryptocarver.crypto.icsf;

/**
 * The key token formats this parser recognises.
 *
 * <p>The {@link #code()} values are the stable identifiers shared with the Python
 * tool, so a report produced here can be diffed against one produced there.</p>
 */
public enum TokenFamily {

    /** AES fixed-length internal, id X'01' version X'04' (Table 614). */
    SYM_FIXED_AES("SYM_FIXED_AES", false),

    /** DES fixed-length internal, id X'01' version X'00'/X'01' (Table 615). */
    SYM_FIXED_DES_INT("SYM_FIXED_DES_INT", false),

    /** DES fixed-length external, id X'02' version X'00'/X'01' (Table 616). */
    SYM_FIXED_DES_EXT("SYM_FIXED_DES_EXT", false),

    /** RKX DES external, id X'02' version X'10' (Table 617). */
    RKX_DES_EXT("RKX_DES_EXT", false),

    /** Variable-length symmetric, version X'05' (Table 618). */
    SYM_VARIABLE("SYM_VARIABLE", false),

    /** Variable-length symmetric null token. */
    SYM_VARIABLE_NULL("SYM_VARIABLE_NULL", true),

    /** PKA token: RSA, ECC or QSA (Tables 637-659). */
    PKA("PKA", false),

    /** PKA null token. */
    PKA_NULL("PKA_NULL", true),

    /** Generic null token, id X'00'. */
    NULL("NULL", true),

    /** Nothing recognisable: the dispatcher could not identify id/version. */
    UNKNOWN("?", false);

    private final String code;
    private final boolean nullToken;

    TokenFamily(String code, boolean nullToken) {
        this.code = code;
        this.nullToken = nullToken;
    }

    public String code() {
        return code;
    }

    /**
     * Whether this family carries no key at all.
     *
     * <p>Batch format detection leans on this: any string starting with X'00'
     * passes as a null token, so a null result must never outscore a reading
     * that produced tokens with a recognised family.</p>
     */
    public boolean isNullToken() {
        return nullToken;
    }

    /** True for the 64-byte fixed-length formats, which are the ones carrying a TVV. */
    public boolean isFixedLength() {
        return this == SYM_FIXED_AES || this == SYM_FIXED_DES_INT
                || this == SYM_FIXED_DES_EXT || this == RKX_DES_EXT;
    }

    /** True for the two DES fixed-length formats, internal and external. */
    public boolean isDesFixedLength() {
        return this == SYM_FIXED_DES_INT || this == SYM_FIXED_DES_EXT;
    }
}
