package com.cryptocarver.crypto.icsf;

import com.cryptocarver.crypto.icsf.IcsfVocabulary.TvvState;

/** The Token Validation Value of a fixed-length symmetric token (p. 1559). */
public final class IcsfTvv {

    private IcsfTvv() { }

    /** What the TVV says about a token, and the arithmetic behind it. */
    public record Evaluation(TvvState state, long stored, long computed, IcsfText detail) { }

    /**
     * TVV = two's-complement sum, carries discarded, of bytes 0..59 taken four at
     * a time. The stored value lives in bytes 60-63.
     */
    public static long compute(byte[] token) {
        long accumulator = 0;
        for (int offset = 0; offset < 60; offset += 4) {
            long word = ((long) IcsfHex.u16(token, offset) << 16) | IcsfHex.u16(token, offset + 2);
            accumulator = (accumulator + word) & 0xFFFFFFFFL;
        }
        return accumulator;
    }

    /** The value actually stored in bytes 60-63. */
    public static long stored(byte[] token) {
        return ((long) IcsfHex.u16(token, 60) << 16) | IcsfHex.u16(token, 62);
    }

    /**
     * Evaluates the TVV.
     *
     * <p>{@link TvvState#ABSENT} is not {@link TvvState#INVALID}. p. 1560: "a
     * fixed-length symmetric key token that is stored in a non-KDSR CKDS does not
     * have an MKVP or TVV. Before such a key token is used, the MKVP is copied
     * from the CKDS header record and the TVV is calculated and placed in the
     * token". Bytes 60-63 at zero mean the token has not been materialized yet,
     * not that it is corrupt.</p>
     */
    public static Evaluation evaluate(byte[] token) {
        long stored = stored(token);
        long computed = compute(token);
        if (stored == computed) {
            return new Evaluation(TvvState.VALID, stored, computed, IcsfText.of("icsf.tvv.valid"));
        }
        if (stored == 0) {
            String computedHex = String.format(java.util.Locale.ROOT, "%08X", computed);
            return new Evaluation(TvvState.ABSENT, stored, computed,
                    IcsfText.of(IcsfHex.isAllZero(token, 8, 16)
                            ? "icsf.tvv.absentWithMkvp" : "icsf.tvv.absent", computedHex));
        }
        return new Evaluation(TvvState.INVALID, stored, computed, IcsfText.of("icsf.tvv.invalid",
                String.format(java.util.Locale.ROOT, "%08X", computed)));
    }
}
