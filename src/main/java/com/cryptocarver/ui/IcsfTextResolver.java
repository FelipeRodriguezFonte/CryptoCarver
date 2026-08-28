package com.cryptocarver.ui;

import com.cryptocarver.crypto.icsf.FindingCode;
import com.cryptocarver.crypto.icsf.InventoryColumn;
import com.cryptocarver.crypto.icsf.SummaryKey;
import com.cryptocarver.service.I18nService;

/**
 * Resolves the ICSF analyser's verdict codes into the viewer's language.
 *
 * <p>The core reports countable codes, never prose, so the batch layer cannot be
 * broken by a translation. Turning those codes into readable text is this class's
 * job, and it happens here at the edge.</p>
 *
 * <p>Codes with no bundle entry are returned verbatim rather than reported as
 * missing translations. Some of these vocabularies are deliberately open — a key
 * type read from Table 676 ({@code IMPORTER}, {@code PINVER}), an RSA modulus size
 * ({@code 2048}) — and those are technical identifiers that must read the same in
 * every language, exactly as the rest of the application treats technical values.</p>
 */
public final class IcsfTextResolver {

    private IcsfTextResolver() { }

    /** The label of a summary dimension, e.g. "Ámbito" / "Scope". */
    public static String dimension(SummaryKey key) {
        return resolve(key.labelKey(), key.displayName());
    }

    /** The reading of one dimension's value, e.g. INTERNAL -> "INTERNO (X'01')". */
    public static String value(SummaryKey key, String code) {
        if (code == null || code.isBlank()) return "";
        return resolve(key.valueKey(code), code);
    }

    /** An inventory column header. */
    public static String column(InventoryColumn column) {
        return resolve(column.headerKey(), column.header());
    }

    /** A finding's short title. */
    public static String findingTitle(FindingCode code) {
        return resolve(code.titleKey(), code.title());
    }

    /** A finding's explanation: what it is and what to do about it. */
    public static String findingDetail(FindingCode code) {
        return resolve(code.detailKey(), code.detail());
    }

    /** A finding's severity, as a word. */
    public static String severity(FindingCode.Severity severity) {
        return resolve("icsf.severity." + severity.name(), severity.name());
    }

    /**
     * Looks a key up, falling back to the core's English text.
     *
     * <p>Uses {@code containsKey} rather than {@code text()} so that an absent entry
     * for an open vocabulary is not logged as a missing translation.</p>
     */
    private static String resolve(String key, String fallback) {
        I18nService i18n = I18nService.getInstance();
        try {
            if (i18n.getBundle() != null && i18n.getBundle().containsKey(key)) {
                return i18n.text(key);
            }
        } catch (RuntimeException ignored) {
            // No bundle loaded (headless tests, early startup): fall through to English.
        }
        return fallback;
    }
}
