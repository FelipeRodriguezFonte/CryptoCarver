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
    VISA4("VISA-4", Set.of(), true, false, Specs.IBM, true),
    ECI1("ECI-1", Set.of(), true, false, Specs.IBM, true),
    ECI2("ECI-2 (no PAN binding)", Set.of("ECI-2"), false, false, Specs.IBM, false),
    ECI3("ECI-3 (no PAN binding)", Set.of("ECI-3"), false, false, Specs.IBM, false),
    ECI4("ECI-4", Set.of(), false, true, Specs.IBM, true),
    DOCUTEL("Docutel (Format 02)", Set.of("Docutel"), false, true, Specs.LEGACY, false),
    DIEBOLD("Diebold (Format 03)", Set.of("Diebold"), false, false, Specs.LEGACY, false),
    PLUS("Plus Network (Format 04)", Set.of("Plus Network", "PLUS"), true, false, Specs.LEGACY, false);

    private static final class Specs {
        private static final String ISO = "ISO 9564-1; https://www.ibm.com/docs/en/zos/3.1.0?topic=profile-pin-block-format";
        private static final String IBM = "IBM PIN profile; https://www.ibm.com/docs/en/zos/3.1.0?topic=profile-pin-block-format";
        private static final String LEGACY = "Host Programmer's Manual, PIN block formats 02-04, pp. 171-172; https://www.scribd.com/document/713264175/1270A542-038-Host-Programmer-v3-5";
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

    public String displayName() { return displayName; }
    public Set<String> aliases() { return aliases; }
    public boolean usesPan() { return usesPan; }
    public boolean randomPadding() { return randomPadding; }
    public String specification() { return specification; }
    public boolean unverifiedEquivalence() { return unverifiedEquivalence; }

    public static List<String> displayNames() {
        return Arrays.stream(values()).map(PinBlockFormat::displayName).toList();
    }

    /** UI labels remain the persisted format names in every locale. */
    public static List<String> displayNames(java.util.function.Function<String, String> translate) {
        return Arrays.stream(values()).map(format -> switch (format) {
            case DOCUTEL, DIEBOLD, PLUS -> translate.apply("module.payments.pinFormat." + format.name().toLowerCase(java.util.Locale.ROOT));
            default -> format.displayName;
        }).toList();
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
