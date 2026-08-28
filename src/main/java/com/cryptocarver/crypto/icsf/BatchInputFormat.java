package com.cryptocarver.crypto.icsf;

/** How a pasted block of text is cut into tokens. */
public enum BatchInputFormat {

    /**
     * Decided block by block. The three readings below are all tried and the one
     * that produces tokens which genuinely analyse wins.
     */
    AUTO("auto", "automatic (decided block by block)"),

    /** Every line with content is one whole token. */
    LINE("linea", "one token per line (linear hex)"),

    /**
     * Every PAIR of lines is one token: a host dump in two rows, the top one
     * carrying the high hex digit of each byte and the bottom one the low digit.
     */
    TWO_ROW("dos-filas", "two host rows per token (high row + low row)");

    private final String value;
    private final String label;

    BatchInputFormat(String value, String label) {
        this.value = value;
        this.label = label;
    }

    /** Stable wire value, shared with the Python tool. */
    public String value() {
        return value;
    }

    /** English label; the UI resolves {@code icsf.format.<name>} instead. */
    public String label() {
        return label;
    }

    public static BatchInputFormat fromValue(String raw) {
        if (raw == null || raw.isBlank()) return AUTO;
        String candidate = raw.trim().toLowerCase(java.util.Locale.ROOT);
        for (BatchInputFormat format : values()) {
            if (format.value.equals(candidate) || format.name().toLowerCase(java.util.Locale.ROOT)
                    .equals(candidate)) {
                return format;
            }
        }
        throw new IllegalArgumentException("Unknown format: " + raw + ". Valid values: auto, linea, dos-filas.");
    }

    /**
     * How a block was actually read.
     *
     * <p>Distinct from the requested format because {@link #AUTO} resolves to one of
     * these per block, and because a whole block read as a single stacked-hex token
     * is a fourth outcome that cannot be requested on its own.</p>
     */
    public enum Resolved {
        LINE("linea"),
        TWO_ROW("dos-filas"),
        /** The whole block is one token, hex stacked one byte per line. */
        BLOCK("bloque");

        private final String value;

        Resolved(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }
}
