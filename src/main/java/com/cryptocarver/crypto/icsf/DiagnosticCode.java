package com.cryptocarver.crypto.icsf;

/** What a {@link Diagnostic} is about, so callers never have to match on wording. */
public enum DiagnosticCode {

    // --- shared ----------------------------------------------------------
    /** A fixed-length token that is not exactly 64 bytes. */
    UNEXPECTED_TOKEN_LENGTH,
    /** Bytes 2-3 declare a length that differs from the real one. */
    DECLARED_LENGTH_MISMATCH,

    // --- AES fixed-length (Table 614) ------------------------------------
    /** The CV flag is on but the CV is not all zeros; every AES fixed-length key is DATA. */
    AES_CV_FLAG_WITHOUT_ZERO_CV,
    /** Clear key length at offset 56 outside 0/128/192/256. */
    AES_CLEAR_LENGTH_INVALID,
    /** Encrypted key length at offset 58 outside 0/32. */
    AES_ENCRYPTED_LENGTH_INVALID,

    // --- DES fixed-length (Tables 615/616) -------------------------------
    /** Byte 59 demands a token version that the token does not declare. */
    BYTE59_VERSION_MISMATCH,
    /** The Control Vector breaks the structural rules of p. 1678. */
    CV_STRUCTURE_INVALID,
    /** The two CV halves disagree on the export bit. */
    CV_HALVES_DISAGREE,

    // --- variable-length (Table 618) -------------------------------------
    /** Declared associated-data length does not match the sum of its fields. */
    ASSOCIATED_DATA_LENGTH_MISMATCH,
    /** Declared associated data runs past the end of the token. */
    ASSOCIATED_DATA_OVERFLOW,
    /** Declared payload does not fit in the token. */
    PAYLOAD_OVERFLOW,
    /** Fewer management-field bytes present than the counter declares. */
    MANAGEMENT_FIELDS_TRUNCATED,
    /** A DESUSECV token with non-zero management fields, which Table 630 reserves. */
    DESUSECV_RESERVED_NONZERO,

    // --- PKA (Tables 637-659) --------------------------------------------
    /** A section declares a length that does not fit in the token. */
    PKA_SECTION_LENGTH_INVALID,
    /** A section identifier that Table 637 does not document. */
    PKA_SECTION_UNDOCUMENTED,
    /** Bytes left over after the last section. */
    PKA_TRAILING_BYTES,
    /** Sections of more than one algorithm in the same token. */
    PKA_MIXED_ALGORITHMS,
    /** Sections out of the order the manual prescribes (p. 1605). */
    PKA_SECTION_ORDER,
    /** Public and private sections describe different modulus lengths. */
    PKA_MODULUS_MISMATCH,
    /** Internal token with an encrypted private key but an all-zero verification pattern. */
    PKA_INTERNAL_ENCRYPTED_WITHOUT_KVP,
    /** Key source flag set in an external token, where the manual reserves it. */
    PKA_KEY_SOURCE_IN_EXTERNAL,

    // --- provenance coherence --------------------------------------------
    /** The declared provenance cannot produce a token in this state. */
    PROVENANCE_INCONSISTENT,
    /** A TVV that has a value and does not add up: not the non-KDSR case, which leaves it at zero. */
    TVV_PRESENT_BUT_WRONG,
    /** MKVP and TVV at zero on a token that was supposed to arrive materialized. */
    UNMATERIALIZED_ON_KRR,
    /** TVV at zero on a token delivered by Key Record Read. */
    TVV_ABSENT_ON_KRR,
    /** MKVP at zero on an internal token delivered by Key Record Read. */
    MKVP_ABSENT_ON_KRR
}
