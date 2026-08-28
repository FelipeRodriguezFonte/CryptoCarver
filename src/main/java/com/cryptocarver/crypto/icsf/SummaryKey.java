package com.cryptocarver.crypto.icsf;

/**
 * The entries of a token's summary card.
 *
 * <p>The twelve marked {@link #aggregated()} are exactly the dimensions batch
 * statistics count and the inventory tabulates. They are the contract between
 * the single-token analyser and the batch layer: the batch reads these and
 * nothing else, so the two cannot disagree about what a token is.</p>
 */
public enum SummaryKey {

    // --- the twelve aggregated dimensions -------------------------------
    FAMILY(true),
    SCOPE(true),
    ALGORITHM(true),
    KEY_TYPE(true),
    KEY_LENGTH(true),
    EFFECTIVE_STRENGTH(true),
    MATERIAL_STATE(true),
    WRAPPING(true),
    EXPORTABILITY(true),
    CONTROL_VECTOR(true),
    TVV(true),
    MKVP(true),

    // --- shown on the card, not counted ---------------------------------
    ALLOWED_USES(false),
    PROTECTION(false),
    PAYLOAD_LENGTH(false),
    COMPONENT_PATTERN(false),
    COMPONENT_RELIABILITY(false),
    PEDIGREE(false),
    KEY_NAME(false),
    PRIVATE_KEY_PRESENT(false),
    PRIVATE_KEY_SOURCE(false),
    COMPLIANT_TAGGED(false),
    SECTIONS(false),
    RULE_ID(false),
    STRUCTURE(false),
    /** The standing caveat that PKA decoding has not been validated against a real PKDS token. */
    MATURITY(false);

    private final boolean aggregated;

    SummaryKey(boolean aggregated) {
        this.aggregated = aggregated;
    }

    /** Whether batch statistics count this dimension. */
    public boolean aggregated() {
        return aggregated;
    }

    /** English label, for the text report and as the fallback when no bundle is loaded. */
    public String displayName() {
        return switch (this) {
            case FAMILY -> "Family";
            case SCOPE -> "Scope";
            case ALGORITHM -> "Algorithm";
            case KEY_TYPE -> "Key type";
            case KEY_LENGTH -> "Key length";
            case EFFECTIVE_STRENGTH -> "Effective strength";
            case MATERIAL_STATE -> "Key material state";
            case WRAPPING -> "Wrapping method";
            case EXPORTABILITY -> "Exportability";
            case CONTROL_VECTOR -> "Control Vector";
            case TVV -> "TVV";
            case MKVP -> "MKVP";
            case ALLOWED_USES -> "Permitted uses";
            case PROTECTION -> "Protection";
            case PAYLOAD_LENGTH -> "Payload length";
            case COMPONENT_PATTERN -> "Component pattern";
            case COMPONENT_RELIABILITY -> "Component inference";
            case PEDIGREE -> "Pedigree";
            case KEY_NAME -> "Key name";
            case PRIVATE_KEY_PRESENT -> "Carries a private key";
            case PRIVATE_KEY_SOURCE -> "Private key origin";
            case COMPLIANT_TAGGED -> "Compliant-tagged";
            case SECTIONS -> "Sections";
            case RULE_ID -> "Rule ID";
            case STRUCTURE -> "Structure";
            case MATURITY -> "Status of this decoding";
        };
    }

    /** Bundle key for this dimension's own label, e.g. {@code icsf.dimension.SCOPE}. */
    public String labelKey() {
        return "icsf.dimension." + name();
    }

    /** Bundle key for one of this dimension's values, e.g. {@code icsf.value.SCOPE.INTERNAL}. */
    public String valueKey(String code) {
        return "icsf.value." + name() + "." + code;
    }
}
