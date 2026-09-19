package com.cryptocarver.crypto.hsm;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Host commands exposed by the offline catalog. Their bodies remain opaque. */
public enum PayShieldCommand {
    A0("Generate key", "A1"),
    A6("Import key", "A7"),
    A8("Export key", "A9"),
    BU("Generate key check value", "BV"),
    CA("Translate PIN block TPK to ZPK/BDK", "CB"),
    CC("Translate PIN block from one ZPK to another", "CD"),
    CW("Generate CVV", "CX"),
    CY("Verify CVV", "CZ"),
    DC("Verify IBM 3624 PIN", "DD"),
    EC("Verify Visa PVV", "ED"),
    FA("Translate ZPK from ZMK to LMK", "FB"),
    NC("Perform diagnostics", "ND"),
    M0("Encrypt data block", "M1"),
    M2("Decrypt data block", "M3"),
    M4("Translate data block", "M5"),
    M6("Generate MAC", "M7");

    private final String displayName;
    private final String responseCode;

    PayShieldCommand(String displayName, String responseCode) {
        this.displayName = displayName;
        this.responseCode = responseCode;
    }

    public String displayName() {
        return displayName;
    }

    public String expectedResponseCode() {
        return responseCode;
    }

    public static Map<String, PayShieldCommand> catalog() {
        return Stream.of(values())
                .collect(Collectors.toUnmodifiableMap(Enum::name, command -> command));
    }
}
