package com.cryptocarver.crypto.icsf;

/**
 * The audit findings catalogue.
 *
 * <p>A finding is NOT an error in the token: it is something worth looking at.
 * Each code says what it is and what to do about it, so a report can be
 * understood without the manual open alongside it.</p>
 *
 * <p>The {@link #code()} strings stay in Spanish deliberately. They are stable
 * identifiers that appear in the CSV and in the Python tool's own reports;
 * translating an identifier would break diffing one report against the other.
 * The title and the explanation are what get localized.</p>
 */
public enum FindingCode {

    // --- high severity ----------------------------------------------------
    ENTRADA_NO_RECONOCIDA("ENTRADA-NO-RECONOCIDA", Severity.HIGH,
            "The entry could not be read or analysed",
            "Either the hexadecimal is invalid, or the identifier and version match no documented "
                    + "format. Check the extract: the usual cause is a partial copy-paste or an "
                    + "EBCDIC/ASCII conversion."),

    TVV_INVALIDO("TVV-INVALIDO", Severity.HIGH,
            "TVV holds a value but it is wrong",
            "Bytes 60-63 have content and do not match the sum of bytes 0-59. That is NOT the normal "
                    + "non-KDSR CKDS case (there the TVV arrives at zero): it points to a token altered "
                    + "or truncated somewhere along the way."),

    DES_56_BITS("DES-56-BITS", Severity.HIGH,
            "Single-length DES key (56 effective bits)",
            "A single-length DES key is 56 bits. This is the material that single-DES withdrawal "
                    + "controls reject, so it is worth having the complete list before a host level "
                    + "upgrade."),

    DES_FUERZA_SIMPLE("DES-FUERZA-SIMPLE", Severity.HIGH,
            "Structurally double/triple key that collapses to single DES",
            "The components coincide in a way that reduces EDE to a single DES (K1=K2=K3, K1=K2 or "
                    + "K2=K3). It declares 112 or 168 bits and delivers 56. Only reported when the "
                    + "component comparison is RELIABLE."),

    MATERIAL_EN_CLARO("MATERIAL-EN-CLARO", Severity.HIGH,
            "The key material travels in the clear inside the token",
            "The token itself carries the key unencrypted. Anyone holding the token holds the key: "
                    + "treat the file as sensitive material."),

    CV_INVALIDO("CV-INVALIDO", Severity.HIGH,
            "Control Vector that breaks its structural rules",
            "The CV does not satisfy what p. 1678 requires (even parity of zero bits per byte, "
                    + "bit 30 = 0, bit 38 = 1). If the CV is not valid, this token's type, uses and "
                    + "exportability cannot be read reliably."),

    // --- medium severity --------------------------------------------------
    DES_FUERZA_DOBLE("DES-FUERZA-DOBLE", Severity.MEDIUM,
            "Structurally triple key that is equivalent to a double one",
            "Form K1|K2|K1: it is 2-key TDES (112 bits) even though it occupies 24 bytes. Not a "
                    + "fault, but it does not count as triple in a strength inventory."),

    BYTE59_FUERA_DE_TABLA("BYTE59-FUERA-DE-TABLA", Severity.MEDIUM,
            "Byte 59 holds a value the current Table 615 does not define",
            "Today byte 59 admits only X'00', X'10' and X'20' (single/double/triple). On older ICSF "
                    + "levels that byte was SUBDIVIDED and also carried the algorithm, so a token "
                    + "created back then may legitimately hold a value outside today's table. Check it "
                    + "against the documentation current when the key was created before writing it off "
                    + "as corrupt: the token can be repaired in place by recalculating the TVV, without "
                    + "touching the key."),

    BYTE59_INCOHERENTE("BYTE59-INCOHERENTE", Severity.MEDIUM,
            "Byte 59 does not agree with the token version",
            "Table 615 ties each value to a version: single = version X'00', double and triple = "
                    + "version X'01'. Here they do not match."),

    WRAP_ECB("WRAP-ECB", Severity.MEDIUM,
            "Legacy ECB wrapping (original CCA method)",
            "It is the only method interoperable outside CCA, but also the weakest: no confounder and "
                    + "no chaining. In a host level review this is the list of keys that are candidates "
                    + "for re-wrapping."),

    NOCV("NOCV", Severity.MEDIUM,
            "NOCV key (transport)",
            "Used without a Control Vector variant. It is what makes an EXPORTER interoperable with a "
                    + "non-CCA third party, and at the same time what removes the controls the CV "
                    + "provides: worth knowing how many there are and why."),

    CV_CERO("CV-CERO", Severity.MEDIUM,
            "All-zero Control Vector (legacy DATA key)",
            "No control bits: no type, no uses, no exportability. The token does not say what may be "
                    + "done with the key."),

    MKVP_AUSENTE("MKVP-AUSENTE", Severity.MEDIUM,
            "Internal token with an encrypted key and no MKVP",
            "Normal if this is a RAW copy of a non-KDSR CKDS (p. 1560): the MKVP is copied from the "
                    + "CKDS header before the token is used. Anomalous if the token came from Key "
                    + "Record Read."),

    HISTORIA_DEBIL("HISTORIA-DEBIL", Severity.MEDIUM,
            "The token declares a degraded security history",
            "The management fields say the key was once encrypted under an untrusted KEK, in ECB mode, "
                    + "under a weaker key, or in a format without type and usage attributes. The key "
                    + "carries that past with it even though it is well wrapped now."),

    LONGITUD_INESPERADA("LONGITUD-INESPERADA", Severity.MEDIUM,
            "The token is not the size it should be",
            "A fixed-length token is exactly 64 bytes, and a variable-length one declares its length "
                    + "in bytes 2-3. Here it does not add up."),

    DUPLICADO("DUPLICADO", Severity.MEDIUM,
            "Token repeated in the batch",
            "The same bytes appear more than once. It may be a dump with duplicates, or the same key "
                    + "stored under two labels."),

    AVISOS_PARSER("AVISOS-PARSER", Severity.MEDIUM,
            "The analyser raised warnings about this token",
            "Inconsistencies found while reading it field by field. The full per-token detail at the "
                    + "end of the report lists them one by one."),

    // --- informational ----------------------------------------------------
    WRAP_MEJORADO("WRAP-MEJORADO", Severity.INFO,
            "Enhanced wrapping (WRAP-ENH / WRAPENH2 / WRAPENH3)",
            "Good from a security standpoint, but NOBODY outside a CCA opens these tokens: if this key "
                    + "has to be handed to a third party, this is not the route."),

    ENH_ONLY("ENH-ONLY", Severity.INFO,
            "ENH-ONLY (CV bit 56) is on",
            "The coprocessor will no longer return a WRAP-ECB token for this key: there is no going "
                    + "back to the legacy method."),

    NO_EXPORTABLE("NO-EXPORTABLE", Severity.INFO,
            "The key cannot be exported",
            "In DES this is decided by CV bit 17 (NO-XPORT) or by the flag byte; in variable-length, by "
                    + "the management fields; in PKA, by NO-XLATE. It matters when planning a migration "
                    + "by export: these keys do not leave."),

    COMP_TAG("COMP-TAG", Severity.INFO,
            "Compliant-tagged key",
            "Use restricted to PCI-HSM compliant applications."),

    TVV_AUSENTE("TVV-AUSENTE", Severity.INFO,
            "TVV at zero (token not materialized)",
            "Bytes 60-63 at zero: this is not corruption, it is the record exactly as stored in a "
                    + "non-KDSR CKDS."),

    PKA_EN_PRUEBAS("PKA-EN-PRUEBAS", Severity.INFO,
            "PKA token decoding is in testing",
            "The PKA decoding is checked against the manual and exercised with synthetic tokens, but "
                    + "not yet against a real token from a PKDS. Treat its fields as provisional.");

    /** How urgently a finding wants looking at. Findings are reported in this order. */
    public enum Severity {
        HIGH("alto"), MEDIUM("medio"), INFO("info");

        private final String value;

        Severity(String value) {
            this.value = value;
        }

        /** Stable wire value, shared with the Python tool. */
        public String value() {
            return value;
        }
    }

    private final String code;
    private final Severity severity;
    private final String title;
    private final String detail;

    FindingCode(String code, Severity severity, String title, String detail) {
        this.code = code;
        this.severity = severity;
        this.title = title;
        this.detail = detail;
    }

    /** The stable identifier, as it appears in reports and in the CSV. */
    public String code() {
        return code;
    }

    public Severity severity() {
        return severity;
    }

    /** English title; the UI resolves {@link #titleKey()} instead. */
    public String title() {
        return title;
    }

    /** English explanation of what it is and what to do; the UI resolves {@link #detailKey()}. */
    public String detail() {
        return detail;
    }

    public String titleKey() {
        return "icsf.finding." + name() + ".title";
    }

    public String detailKey() {
        return "icsf.finding." + name() + ".detail";
    }

    /** Looks a code up by its wire string, e.g. {@code "BYTE59-FUERA-DE-TABLA"}. */
    public static FindingCode fromCode(String code) {
        for (FindingCode candidate : values()) {
            if (candidate.code.equals(code)) return candidate;
        }
        throw new IllegalArgumentException("Unknown finding code: " + code);
    }
}
