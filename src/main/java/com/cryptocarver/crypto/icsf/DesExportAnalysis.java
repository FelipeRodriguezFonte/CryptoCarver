package com.cryptocarver.crypto.icsf;

import com.cryptocarver.crypto.icsf.IcsfVocabulary.Exportability;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.WrapMethod;

import java.util.ArrayList;
import java.util.List;

/**
 * Whether the bytes of a DES fixed-length token permit exporting the key.
 *
 * <p>There are two independent controls and both have to allow it:</p>
 * <ol>
 *   <li>flag byte 6, bit 7 "Export prohibited", present only on internal tokens
 *       (Table 615, p. 1562); external tokens have no such flag (Table 616, p. 1564);</li>
 *   <li>bit 17 of the Control Vector, the export bit of Appendix C, p. 1678:
 *       "If set to 0, the export bit prevents a key from being exported".</li>
 * </ol>
 *
 * <p>The second is the one Key_Export and Data_Key_Export actually enforce.
 * Restrict Key Attribute with NOEXPORT sets "CV bit 17 = B'0' (NO-XPORT)"
 * (p. 415) and Prohibit Export does the same, without touching the flag byte.
 * Reading only the flag byte therefore yields false "exportable" verdicts.</p>
 */
public final class DesExportAnalysis {

    private DesExportAnalysis() { }

    /**
     * @param verdict         what the bytes settle
     * @param summary         the reasoning, for the report card
     * @param flags           the individual control bits
     * @param warnings        coherence problems found in the CV while reading it
     * @param structureValid  whether the CV passes p. 1678, or {@code null} if there was no CV to check
     * @param enhOnly         CV bit 56: the key can no longer be re-wrapped with the legacy method
     * @param compliantTagged CV bit 58: restricted to PCI-HSM compliant applications
     */
    public record Result(Exportability verdict, IcsfText summary, List<IcsfSection.Flag> flags,
                         List<Diagnostic> warnings, Boolean structureValid,
                         boolean enhOnly, boolean compliantTagged) { }

    public static Result analyse(byte[] token, boolean internal) {
        boolean nocv = IcsfHex.bit(IcsfHex.u8(token, 6), 2);
        boolean noExportFlag = internal && IcsfHex.bit(IcsfHex.u8(token, 6), 7);
        WrapMethod wrap = DesKeyAnalysis.wrapMethod(token);
        byte[] cvLeft = IcsfHex.slice(token, 32, 40);
        byte[] cvRight = IcsfHex.slice(token, 40, 48);

        List<IcsfSection.Flag> flags = new ArrayList<>();
        List<Diagnostic> warnings = new ArrayList<>();

        if (internal) {
            flags.add(new IcsfSection.Flag(IcsfText.of("icsf.export.flag.prohibited"),
                    noExportFlag, IcsfText.of("icsf.export.flag.prohibitedHelp")));
        }

        // --- cases where the CV contributes no control bits ---------------
        if (!DesControlVector.usable(token)) {
            IcsfText reason = IcsfText.of(nocv ? "icsf.export.reason.nocv" : "icsf.export.reason.zeroCv");
            if (noExportFlag) {
                return new Result(Exportability.NO, IcsfText.of("icsf.export.noByFlag"),
                        List.copyOf(flags), List.copyOf(warnings), null, false, false);
            }
            return new Result(Exportability.NOT_DETERMINABLE,
                    IcsfText.of("icsf.export.notDeterminable", reason),
                    List.copyOf(flags), List.copyOf(warnings), null, false, false);
        }

        // --- CV control bits ----------------------------------------------
        boolean exportBit = DesControlVector.bit(cvLeft, 17);
        boolean noTr31Export = DesControlVector.bit(cvLeft, 57);
        boolean cpacf = DesControlVector.bit(cvLeft, 59);
        boolean keyPart = DesControlVector.bit(cvLeft, 44);
        boolean enhOnly = DesControlVector.bit(cvLeft, 56);
        boolean compliantTagged = DesControlVector.bit(cvLeft, 58);

        flags.add(new IcsfSection.Flag(IcsfText.of("icsf.export.flag.xportOk"), exportBit,
                IcsfText.of("icsf.export.flag.xportOkHelp")));
        flags.add(new IcsfSection.Flag(IcsfText.of("icsf.export.flag.not31xpt"), noTr31Export,
                IcsfText.of("icsf.export.flag.not31xptHelp")));
        flags.add(new IcsfSection.Flag(IcsfText.of("icsf.export.flag.xprtcpac"), cpacf,
                IcsfText.of("icsf.export.flag.xprtcpacHelp")));
        flags.add(new IcsfSection.Flag(IcsfText.of("icsf.export.flag.keyPart"), keyPart,
                IcsfText.of("icsf.export.flag.keyPartHelp")));
        flags.add(new IcsfSection.Flag(IcsfText.of("icsf.export.flag.enhOnly"), enhOnly,
                IcsfText.of("icsf.export.flag.enhOnlyHelp")));
        flags.add(new IcsfSection.Flag(IcsfText.of("icsf.export.flag.compTag"), compliantTagged,
                IcsfText.of("icsf.export.flag.compTagHelp")));

        // --- CV coherence ---------------------------------------------------
        boolean structureValid = DesControlVector.structureOk(cvLeft);
        if (!structureValid) {
            warnings.add(Diagnostic.of(DiagnosticCode.CV_STRUCTURE_INVALID,
                    IcsfText.of("icsf.warn.cvStructure")));
        }
        if (!IcsfHex.isAllZero(cvRight, 0, cvRight.length) && wrap != WrapMethod.WRAPENH3
                && DesControlVector.bit(cvRight, 17) != exportBit) {
            warnings.add(Diagnostic.of(DiagnosticCode.CV_HALVES_DISAGREE,
                    IcsfText.of("icsf.warn.cvHalves",
                            exportBit ? 1 : 0, DesControlVector.bit(cvRight, 17) ? 1 : 0)));
        }

        // --- verdict ---------------------------------------------------------
        IcsfText extra = IcsfText.of("icsf.export.extra",
                IcsfText.of(noTr31Export ? "icsf.export.tr31Prohibited" : "icsf.export.tr31Permitted"),
                IcsfText.of(cpacf ? "icsf.export.cpacfPermitted" : "icsf.export.cpacfProhibited"));

        Exportability verdict;
        IcsfText verdictText;
        if (noExportFlag && !exportBit) {
            verdict = Exportability.NO;
            verdictText = IcsfText.of("icsf.export.noByFlagAndCv");
        } else if (noExportFlag) {
            verdict = Exportability.NO;
            verdictText = IcsfText.of("icsf.export.noByFlagOnly");
        } else if (!exportBit) {
            verdict = Exportability.NO;
            verdictText = IcsfText.of("icsf.export.noByCv");
        } else {
            verdict = Exportability.YES;
            verdictText = IcsfText.of("icsf.export.yes");
        }

        return new Result(verdict, IcsfText.of("icsf.export.summary", verdictText, extra),
                List.copyOf(flags), List.copyOf(warnings), structureValid, enhOnly, compliantTagged);
    }
}
