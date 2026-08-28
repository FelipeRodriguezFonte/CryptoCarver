package com.cryptocarver.crypto.icsf;

import com.cryptocarver.crypto.icsf.IcsfVocabulary.MkvpState;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.TvvState;

import java.util.ArrayList;
import java.util.List;

/**
 * What each declared provenance implies about the state a token should arrive in.
 *
 * <p>Provenance is not derivable from the bytes, so the parser never asserts one.
 * What it can do is say whether the state it observes is consistent with the
 * provenance the analyst declared, and, when none was declared, bound which
 * provenances remain possible.</p>
 */
public final class IcsfProvenance {

    private IcsfProvenance() { }

    /** Human-readable provenance label, using the verb of the data set that applies. */
    public static IcsfText label(Origin origin, TokenFamily family) {
        boolean pka = family == TokenFamily.PKA || family == TokenFamily.PKA_NULL;
        String kds = pka ? "PKDS" : "CKDS";
        return switch (origin) {
            case RAW_KDS -> IcsfText.of("icsf.prov.label.raw", kds);
            case KRR -> IcsfText.of("icsf.prov.label.krr", kds,
                    pka ? "CSNDKRR / CSNDKRR2" : "CSNBKRR / CSNBKRR2");
            case INFER -> IcsfText.of("icsf.prov.label.infer");
        };
    }

    public static IcsfText label(Origin origin) {
        return label(origin, TokenFamily.UNKNOWN);
    }

    /**
     * Applies the provenance notes and coherence warnings for a symmetric token.
     *
     * @param mkvp     whether the master key verification pattern is present
     * @param tvv      the TVV verdict; {@link TvvState#NOT_APPLICABLE} for formats that carry none
     * @param internal whether the token is an internal one
     */
    public static void applySymmetric(ParseResult result, Origin origin,
                                      MkvpState mkvp, TvvState tvv, boolean internal) {
        boolean mkvpAbsent = mkvp == MkvpState.ABSENT;
        boolean tvvAbsent = tvv == TvvState.ABSENT;
        boolean tvvWrong = tvv == TvvState.INVALID;
        boolean complete = !mkvpAbsent && !tvvAbsent;

        switch (origin) {
            case RAW_KDS -> {
                result.provenanceNote(IcsfText.of("icsf.prov.raw.intro"));
                if (!internal) {
                    result.warn(DiagnosticCode.PROVENANCE_INCONSISTENT,
                            IcsfText.of("icsf.prov.raw.externalImpossible"));
                }
                if (mkvpAbsent && tvvAbsent) {
                    result.provenanceNote(IcsfText.of("icsf.prov.raw.expectedState"));
                } else if (complete) {
                    result.provenanceNote(IcsfText.of("icsf.prov.raw.kdsr"));
                } else if (mkvpAbsent) {
                    result.provenanceNote(IcsfText.of("icsf.prov.raw.mkvpOnlyMissing"));
                } else {
                    result.provenanceNote(IcsfText.of("icsf.prov.raw.tvvOnlyMissing"));
                }
                if (tvvWrong) {
                    result.warn(DiagnosticCode.TVV_PRESENT_BUT_WRONG,
                            IcsfText.of("icsf.prov.tvvWrong"));
                }
            }
            case KRR -> {
                result.provenanceNote(IcsfText.of("icsf.prov.krr.intro"));
                if (!internal) {
                    result.warn(DiagnosticCode.PROVENANCE_INCONSISTENT,
                            IcsfText.of("icsf.prov.krr.externalImpossible"));
                }
                if (mkvpAbsent && tvvAbsent) {
                    result.warn(DiagnosticCode.UNMATERIALIZED_ON_KRR,
                            IcsfText.of("icsf.prov.krr.unmaterialized"));
                } else if (tvvAbsent) {
                    result.warn(DiagnosticCode.TVV_ABSENT_ON_KRR,
                            IcsfText.of("icsf.prov.krr.tvvAbsent"));
                } else if (mkvpAbsent && internal) {
                    result.warn(DiagnosticCode.MKVP_ABSENT_ON_KRR,
                            IcsfText.of("icsf.prov.krr.mkvpAbsent"));
                }
                if (tvvWrong) {
                    result.warn(DiagnosticCode.TVV_PRESENT_BUT_WRONG,
                            IcsfText.of("icsf.prov.krr.tvvWrong"));
                }
            }
            case INFER -> {
                List<IcsfText> compatible = new ArrayList<>();
                List<IcsfText> ruledOut = new ArrayList<>();
                if (internal) {
                    if (mkvpAbsent && tvvAbsent) {
                        compatible.add(IcsfText.of("icsf.prov.infer.rawNonKdsr"));
                        ruledOut.add(IcsfText.of("icsf.prov.infer.notKrr"));
                    } else if (complete) {
                        compatible.add(IcsfText.of("icsf.prov.infer.krrOrService"));
                        compatible.add(IcsfText.of("icsf.prov.infer.rawKdsr"));
                    } else {
                        compatible.add(IcsfText.of("icsf.prov.infer.noneCleanly"));
                    }
                } else {
                    ruledOut.add(IcsfText.of("icsf.prov.infer.notCkds"));
                    compatible.add(IcsfText.of("icsf.prov.infer.externalDelivery"));
                }
                if (tvvWrong) {
                    // A TVV that does not add up is an INTEGRITY problem, not a provenance one:
                    // it is reported whatever the analyst declared.
                    result.warn(DiagnosticCode.TVV_PRESENT_BUT_WRONG,
                            IcsfText.of("icsf.prov.tvvWrong"));
                    ruledOut.add(IcsfText.of("icsf.prov.infer.allRuledOut"));
                }
                result.provenanceNote(IcsfText.of("icsf.prov.infer.intro"));
                compatible.forEach(result::provenanceNote);
                ruledOut.forEach(result::provenanceNote);
            }
        }
    }

    /**
     * Provenance notes for a PKA token, which lives in the PKDS.
     *
     * <p>The PKDS does not have the non-KDSR CKDS problem. The manual documents a
     * missing MKVP/TVV only for fixed-length tokens "stored in a non-KDSR CKDS"
     * (pp. 1559, 1560, 1564), and PKA tokens carry no TVV at all. There is no
     * "unmaterialized" state to recognise here.</p>
     *
     * @param kvpAllZero whether the private section's verification pattern is all zeros
     */
    public static void applyPka(ParseResult result, Origin origin, boolean internal,
                                boolean hasPrivateKey, boolean kvpAllZero) {
        boolean withoutKvp = hasPrivateKey && kvpAllZero;
        switch (origin) {
            case RAW_KDS -> {
                result.provenanceNote(IcsfText.of("icsf.prov.pka.raw"));
                if (internal && withoutKvp) {
                    result.provenanceNote(IcsfText.of("icsf.prov.pka.rawZeroKvp"));
                }
            }
            case KRR -> {
                result.provenanceNote(IcsfText.of("icsf.prov.pka.krr"));
                if (!internal) {
                    result.provenanceNote(IcsfText.of("icsf.prov.pka.krrExternal"));
                }
            }
            case INFER -> {
                result.provenanceNote(IcsfText.of("icsf.prov.pka.infer"));
                result.provenanceNote(IcsfText.of(internal
                        ? "icsf.prov.pka.inferInternal" : "icsf.prov.pka.inferExternal"));
            }
        }
    }
}
