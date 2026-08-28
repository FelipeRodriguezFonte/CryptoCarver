package com.cryptocarver.crypto.icsf;

/**
 * How the key token being analysed reached the analyst's hands.
 *
 * <p>The manual's notes about a missing MKVP/TVV describe the token <em>as
 * stored</em>: "a fixed-length symmetric key token that is <b>stored in</b> a
 * non-KDSR CKDS does not have an MKVP or TVV" (pp. 1559, 1560, 1564). They say
 * nothing about what a service returns; only that the MKVP is copied from the
 * CKDS header record "before such a key token is used". Reading the record
 * straight out of the data set and asking for it with CSNBKRR are therefore
 * different cases.</p>
 *
 * <p>Provenance is not reliably derivable from the bytes, so it is part of the
 * input contract rather than something the parser decides. What the parser does
 * contribute are the observations specific to each scenario; see
 * {@link IcsfProvenance}.</p>
 */
public enum Origin {

    /** Raw copy of the data set (browse, REPRO, a utility): CKDS for symmetric tokens, PKDS for PKA. */
    RAW_KDS("kds-crudo"),

    /** Key Record Read: CSNBKRR/CSNBKRR2 over the CKDS (pp. 1257-1262) or CSNDKRR/CSNDKRR2 over the PKDS (p. 1311). */
    KRR("key-record-read"),

    /** Not declared: the parser bounds which provenances the token state is compatible with, without asserting one. */
    INFER("inferir");

    private final String value;

    Origin(String value) {
        this.value = value;
    }

    /** The stable wire value, shared with the Python tool so reports stay comparable. */
    public String value() {
        return value;
    }

    /**
     * Resolves a wire value, accepting the legacy aliases the Python tool still takes.
     *
     * @throws IllegalArgumentException if the value matches no origin or alias
     */
    public static Origin fromValue(String raw) {
        if (raw == null || raw.isBlank()) return INFER;
        String candidate = raw.trim().toLowerCase(java.util.Locale.ROOT);
        for (Origin origin : values()) {
            if (origin.value.equals(candidate)) return origin;
        }
        return switch (candidate) {
            case "ckds", "pkds" -> RAW_KDS;
            case "csnbkrr", "csndkrr", "key-token-reader" -> KRR;
            case "unknown" -> INFER;
            default -> throw new IllegalArgumentException(
                    "Unknown provenance: " + raw + ". Valid values: kds-crudo, key-record-read, inferir. "
                            + "Accepted aliases: ckds and pkds -> kds-crudo; csnbkrr, csndkrr and "
                            + "key-token-reader -> key-record-read; unknown -> inferir.");
        };
    }
}
