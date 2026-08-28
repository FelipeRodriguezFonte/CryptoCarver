package com.cryptocarver.crypto.icsf;

import com.cryptocarver.crypto.icsf.IcsfVocabulary.CvState;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.DesKeyForm;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.EffectiveStrength;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.Exportability;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.MaterialState;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.MkvpState;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.Scope;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.TvvState;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.WrapMethod;

import java.util.ArrayList;
import java.util.List;

/**
 * Raises the audit findings for one token.
 *
 * <p>Works entirely from what the analyser already decided: the typed summary,
 * the typed diagnostics and the byte-level facts the analyser recorded. It never
 * reads the token bytes, so it cannot reach a conclusion the single-token report
 * does not also reach.</p>
 */
public final class FindingDetector {

    private FindingDetector() { }

    /**
     * @param entry  the entry as read, for its length and any reading error
     * @param result the analysis, or {@code null} if the bytes never got that far
     */
    public static List<Finding> detect(BatchEntry entry, ParseResult result) {
        if (entry.failedToRead() || result == null || !result.isOk()) {
            String reason = entry.failedToRead() ? entry.error()
                    : (result == null ? "" : result.error());
            return List.of(Finding.of(FindingCode.ENTRADA_NO_RECONOCIDA, IcsfText.raw(reason)));
        }

        List<Finding> findings = new ArrayList<>();
        TokenFamily family = result.tokenFamily();

        // --- applicable to every family ------------------------------------
        if (result.is(SummaryKey.MATERIAL_STATE, MaterialState.CLEAR)) {
            findings.add(Finding.of(FindingCode.MATERIAL_EN_CLARO));
        }
        if (result.is(SummaryKey.TVV, TvvState.INVALID)) {
            findings.add(Finding.of(FindingCode.TVV_INVALIDO,
                    result.value(SummaryKey.TVV).map(SummaryValue::detail).orElse(IcsfText.EMPTY)));
        } else if (result.is(SummaryKey.TVV, TvvState.ABSENT)) {
            findings.add(Finding.of(FindingCode.TVV_AUSENTE));
        }
        if (result.is(SummaryKey.EXPORTABILITY, Exportability.NO)) {
            findings.add(Finding.of(FindingCode.NO_EXPORTABLE,
                    result.value(SummaryKey.EXPORTABILITY).map(SummaryValue::detail)
                            .orElse(IcsfText.EMPTY)));
        }
        if (result.is(SummaryKey.MKVP, MkvpState.ABSENT)
                && result.is(SummaryKey.MATERIAL_STATE, MaterialState.ENCRYPTED)
                && result.is(SummaryKey.SCOPE, Scope.INTERNAL)) {
            findings.add(Finding.of(FindingCode.MKVP_AUSENTE));
        }
        if (family.isFixedLength() && entry.data().length != 64) {
            findings.add(Finding.of(FindingCode.LONGITUD_INESPERADA,
                    IcsfText.of("icsf.value.bytes", entry.data().length)));
        }

        // --- DES fixed-length ------------------------------------------------
        if (family.isDesFixedLength()) {
            int byte59 = result.byte59().orElse(0);
            if (byte59 != 0x00 && byte59 != 0x10 && byte59 != 0x20) {
                findings.add(Finding.of(FindingCode.BYTE59_FUERA_DE_TABLA,
                        IcsfText.of("icsf.note.byte59",
                                String.format(java.util.Locale.ROOT, "%02X", byte59))));
            }
            if (result.is(SummaryKey.WRAPPING, WrapMethod.ECB)) {
                findings.add(Finding.of(FindingCode.WRAP_ECB));
            } else {
                findings.add(Finding.of(FindingCode.WRAP_MEJORADO,
                        SymmetricFixedTokenParser.wrapLabel(
                                IcsfVocabulary.WrapMethod.valueOf(result.code(SummaryKey.WRAPPING,
                                        IcsfVocabulary.WrapMethod.RESERVED.name())))));
            }
            if (result.is(SummaryKey.KEY_LENGTH, DesKeyForm.SINGLE)) {
                findings.add(Finding.of(FindingCode.DES_56_BITS));
            }
            if (result.desComponentsReliable()) {
                IcsfText pattern = IcsfText.raw(result.code(SummaryKey.COMPONENT_PATTERN, ""));
                if (result.is(SummaryKey.EFFECTIVE_STRENGTH, EffectiveStrength.SINGLE)) {
                    findings.add(Finding.of(FindingCode.DES_FUERZA_SIMPLE, pattern));
                } else if (result.is(SummaryKey.EFFECTIVE_STRENGTH, EffectiveStrength.DOUBLE)
                        && result.desComponentCount() == 3) {
                    findings.add(Finding.of(FindingCode.DES_FUERZA_DOBLE, pattern));
                }
            }
            if (result.is(SummaryKey.CONTROL_VECTOR, CvState.PRESENT)) {
                if (Boolean.FALSE.equals(result.controlVectorStructureValid().orElse(Boolean.TRUE))) {
                    findings.add(Finding.of(FindingCode.CV_INVALIDO,
                            result.warning(DiagnosticCode.CV_STRUCTURE_INVALID)
                                    .map(Diagnostic::message).orElse(IcsfText.EMPTY)));
                }
                if (result.controlVectorEnhOnly()) findings.add(Finding.of(FindingCode.ENH_ONLY));
                if (result.compliantTagged()) findings.add(Finding.of(FindingCode.COMP_TAG));
            } else if (result.is(SummaryKey.CONTROL_VECTOR, CvState.NOCV)) {
                findings.add(Finding.of(FindingCode.NOCV));
            } else {
                findings.add(Finding.of(FindingCode.CV_CERO));
            }
        }

        // --- variable-length ---------------------------------------------------
        if (family == TokenFamily.SYM_VARIABLE) {
            if (result.securityHistoryDegraded()) findings.add(Finding.of(FindingCode.HISTORIA_DEBIL));
            if (result.compliantTagged()) findings.add(Finding.of(FindingCode.COMP_TAG));
        }

        // --- PKA ----------------------------------------------------------------
        if (family == TokenFamily.PKA) {
            findings.add(Finding.of(FindingCode.PKA_EN_PRUEBAS));
            if (result.compliantTagged()) findings.add(Finding.of(FindingCode.COMP_TAG));
        }

        // --- anything the analyser itself flagged --------------------------------
        if (!result.warnings().isEmpty()) {
            boolean alreadyOutOfTable = findings.stream()
                    .anyMatch(finding -> finding.code() == FindingCode.BYTE59_FUERA_DE_TABLA);
            // The analyser already catches byte 59 disagreeing with the version; it is
            // promoted to a finding of its own so it gets counted in the report.
            if (!alreadyOutOfTable) {
                result.warning(DiagnosticCode.BYTE59_VERSION_MISMATCH).ifPresent(warning ->
                        findings.add(Finding.of(FindingCode.BYTE59_INCOHERENTE, warning.message())));
            }
            findings.add(Finding.of(FindingCode.AVISOS_PARSER, result.warnings().get(0).message()));
        }

        return findings;
    }
}
