package com.cryptocarver.crypto;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** Single catalog of clear PIN-block formats exposed by the API and the UI. */
public enum PinBlockFormat {
    ISO0("Format 0 (ISO-0)", Set.of("ISO 0 (ANSI X9.8)", "ISO-0"), true, false, Specs.ISO, false),
    ISO1("Format 1 (ISO-1)", Set.of("ISO 1 (ANSI X9.8)", "ISO-1"), false, true, Specs.ISO, false),
    ISO2("Format 2 (ISO-2)", Set.of("ISO 2 (No PAN)", "ISO-2"), false, false, Specs.ISO, false),
    ISO3("Format 3 (ISO-3)", Set.of("ISO 3 (EMV)", "ISO-3"), true, true, Specs.ISO, false),
    ISO4("Format 4 (ISO-4)", Set.of("ISO 4 (EMV 2000)", "ISO-4"), true, true, Specs.ISO, false),
    ANSI("ANSI X9.8", Set.of(), true, false, Specs.ISO, false),
    IBM3624("IBM 3624", Set.of(), false, false, Specs.IBM, false),
    VISA1("VISA-1", Set.of(), true, false, Specs.IBM, false),
    VISA2("VISA-2", Set.of(), false, false, Specs.IBM, false),
    VISA3("VISA-3", Set.of(), false, false, Specs.IBM, false),
    VISA4("VISA-4", Set.of(), true, false, Specs.CAPTURED, false),
    ECI1("ECI-1", Set.of(), true, false, Specs.CAPTURED, false),
    ECI2("ECI-2 (no PAN binding)", Set.of("ECI-2"), false, false, Specs.IBM, false),
    ECI3("ECI-3 (no PAN binding)", Set.of("ECI-3"), false, false, Specs.IBM, false),
    ECI4("ECI-4", Set.of(), false, true, Specs.CAPTURED, false),
    DOCUTEL("Docutel (Format 02)", Set.of("Docutel"), false, true, Specs.LEGACY, false),
    DIEBOLD("Diebold (Format 03)", Set.of("Diebold"), false, false, Specs.LEGACY, false),
    PLUS("Plus Network (Format 04)", Set.of("Plus Network", "PLUS"), true, false, Specs.LEGACY, false),
    EUROPAY("Europay/MasterCard (Pay Now & Pay Later)", Set.of("Europay/MasterCard", "Pay Now & Pay Later"), true, false, Specs.CAPTURED, false);

    private static final class Specs {
        private static final String ISO = "ISO 9564-1; https://www.ibm.com/docs/en/zos/3.1.0?topic=profile-pin-block-format";
        private static final String IBM = "IBM PIN profile; https://www.ibm.com/docs/en/zos/3.1.0?topic=profile-pin-block-format";
        private static final String CAPTURED = "Checked against external tool captures; see docs/CAPTURAS_PIN_BLOCKS_HEREDADOS.md";
        private static final String LEGACY = "payShield Host Programmer's Manual (1270A542-038 v3.5), PIN block formats 02-04, pp. 171-172";
    }

    private final String displayName;
    private final Set<String> aliases;
    private final boolean usesPan;
    private final boolean randomPadding;
    private final String specification;
    private final boolean unverifiedEquivalence;

    PinBlockFormat(String displayName, Set<String> aliases, boolean usesPan, boolean randomPadding,
            String specification, boolean unverifiedEquivalence) {
        this.displayName = displayName;
        this.aliases = aliases;
        this.usesPan = usesPan;
        this.randomPadding = randomPadding;
        this.specification = specification;
        this.unverifiedEquivalence = unverifiedEquivalence;
    }

    /** Supported explicit padding selections; an empty list means padding is not configurable. */
    public List<String> paddingOptions() {
        return switch (this) {
            case VISA2, VISA3, DOCUTEL -> java.util.stream.Stream.concat(
                    "0123456789".chars().mapToObj(c -> String.valueOf((char) c)),
                    java.util.stream.Stream.of(PinBlockPadding.RANDOM_DECIMAL)).toList();
            case ECI2, ECI3, DIEBOLD -> PinBlockPadding.OPTIONS;
            default -> List.of();
        };
    }

    public String defaultPadding() {
        return switch (this) {
            case VISA2, VISA3 -> "5";
            case ECI2, ECI3, DIEBOLD -> "F";
            case DOCUTEL -> PinBlockPadding.RANDOM_DECIMAL;
            default -> null;
        };
    }

    public void validatePadding(String padding) {
        if (!paddingOptions().contains(PinBlockPadding.normalize(padding)))
            throw new IllegalArgumentException(displayName + " does not support PIN block padding " + padding
                    + "; allowed: " + paddingOptions());
    }

    public String displayName() { return displayName; }
    public Set<String> aliases() { return aliases; }
    public boolean usesPan() { return usesPan; }
    public boolean randomPadding() { return randomPadding; }
    public String specification() { return specification; }
    public boolean unverifiedEquivalence() { return unverifiedEquivalence; }

    public static List<String> displayNames() {
        return Arrays.stream(values()).map(PinBlockFormat::displayName).toList();
    }


    public static PinBlockFormat fromName(String name) {
        for (PinBlockFormat format : values()) {
            if (format.displayName.equalsIgnoreCase(name == null ? "" : name.trim())
                    || format.aliases.stream().anyMatch(a -> a.equalsIgnoreCase(name == null ? "" : name.trim()))) {
                return format;
            }
        }
        throw new IllegalArgumentException("Unknown PIN block format: " + name);
    }
}
