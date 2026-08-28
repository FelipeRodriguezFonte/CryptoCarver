package com.cryptocarver.crypto.icsf;

/**
 * The closed vocabularies the analyser reports its verdicts in.
 *
 * <p>These exist so that nothing downstream has to re-read prose to find out
 * what the parser decided. The Python original stored Spanish sentences in the
 * summary and the batch layer recovered the verdict by matching on them
 * ({@code startswith("SIMPLE")}, {@code "en claro" in text}); translating the
 * report would silently break every one of those checks. Here the verdict is a
 * value, the sentence is presentation, and the batch never reads a sentence.</p>
 *
 * <p>Every {@code code()} is language-invariant and is what statistics count and
 * what the CSV carries. Localized labels are resolved at the edge from
 * {@code icsf.value.*} bundle keys.</p>
 */
public final class IcsfVocabulary {

    private IcsfVocabulary() { }

    /** Whether the token is operational on this system, transportable, or empty. */
    public enum Scope {
        /** X'01' / X'1F': operational here, bound to this system's master key. */
        INTERNAL,
        /** X'02' / X'1E': transportable, bound to a transport KEK (or in the clear). */
        EXTERNAL,
        /** X'00': carries no key. */
        NULL,
        NOT_APPLICABLE;

        public String code() { return name(); }
    }

    /** Cryptographic algorithm the key belongs to. */
    public enum Algorithm {
        AES, DES_TDES, HMAC, RSA, ECC, QSA, NONE, UNKNOWN;

        public String code() { return name(); }
    }

    /** State of the key material carried inside the token. */
    public enum MaterialState {
        /** The token carries the key unencrypted: whoever holds the token holds the key. */
        CLEAR,
        /** Encrypted under a master key or a transport KEK. */
        ENCRYPTED,
        /** The token carries no key material. */
        NO_KEY,
        NOT_DETERMINABLE;

        public String code() { return name(); }
    }

    /**
     * State of the Token Validation Value of a fixed-length token.
     *
     * <p>{@link #ABSENT} is emphatically not {@link #INVALID}: p. 1560 says a
     * fixed-length token stored in a non-KDSR CKDS has neither MKVP nor TVV, and
     * that ICSF fills both in before use. Bytes 60-63 at zero mean the token has
     * not been materialized yet, not that it is corrupt.</p>
     */
    public enum TvvState {
        VALID, ABSENT, INVALID, NOT_APPLICABLE;

        public String code() { return name(); }
    }

    /** Whether the master key verification pattern is present in the token. */
    public enum MkvpState {
        PRESENT, ABSENT, NOT_APPLICABLE;

        public String code() { return name(); }
    }

    /** What the Control Vector of a DES fixed-length token offers to read. */
    public enum CvState {
        /** A usable CV with control bits. */
        PRESENT,
        /** All-zero CV: the legacy DATA "zero CV" of Table 676. No control bits at all. */
        ZERO,
        /** NOCV key (flag byte 6, bit 2): used without a control vector. Transport keys only. */
        NOCV,
        NOT_APPLICABLE;

        public String code() { return name(); }
    }

    /** Whether the bytes permit exporting the key. */
    public enum Exportability {
        YES, NO,
        /** The bytes do not settle it; the service and access control decide. */
        NOT_DETERMINABLE,
        NOT_APPLICABLE;

        public String code() { return name(); }
    }

    /** Structural length of a DES/TDES key. */
    public enum DesKeyForm {
        /** Single-length, 8 bytes, DES 56 bits. */
        SINGLE,
        /** Double-length, 16 bytes, 2-key TDES, 112 bits. */
        DOUBLE,
        /** Triple-length, 24 bytes, 3-key TDES, 168 bits. */
        TRIPLE,
        /** Version X'01' with an inconclusive CV: double or triple, cannot narrow further. */
        DOUBLE_OR_TRIPLE,
        /**
         * WRAPENH3 obfuscates the length on purpose (Table 615): the key-form bits
         * always say triple and offsets 24 and 48 always carry ciphertext.
         */
        OBFUSCATED,
        NOT_APPLICABLE;

        public String code() { return name(); }
    }

    /**
     * Strength a TDES key actually delivers once its components are compared.
     *
     * <p>EDE reduces: K1=K3!=K2 is genuine 2-key TDES; three distinct components
     * are genuine 3-key TDES; every other pattern collapses to a single DES.</p>
     */
    public enum EffectiveStrength {
        SINGLE, DOUBLE, TRIPLE,
        /*
         * A pattern was observed but the comparison does not prove it: enhanced
         * wrapping chains the blocks, and a non-zero CV encrypts each component
         * under a different variant, so equal ciphertext blocks prove nothing.
         * The observation is still reported, kept apart from the trustworthy
         * verdicts so an inventory never counts it as established fact.
         */
        UNRELIABLE_SINGLE, UNRELIABLE_DOUBLE, UNRELIABLE_TRIPLE,
        NOT_APPLICABLE;

        public String code() { return name(); }

        /** True for the three observed-but-unproven verdicts. */
        public boolean isUnreliable() {
            return this == UNRELIABLE_SINGLE || this == UNRELIABLE_DOUBLE || this == UNRELIABLE_TRIPLE;
        }

        /** The same verdict marked as merely observed, when the comparison cannot be trusted. */
        public EffectiveStrength asUnreliable() {
            return switch (this) {
                case SINGLE -> UNRELIABLE_SINGLE;
                case DOUBLE -> UNRELIABLE_DOUBLE;
                case TRIPLE -> UNRELIABLE_TRIPLE;
                default -> this;
            };
        }
    }

    /** How the key material is wrapped. */
    public enum WrapMethod {
        /** Original CCA method: each 8-byte block encrypted independently. No confounder, no chaining. */
        ECB,
        /** Enhanced method, SHA-1. */
        WRAP_ENH,
        /** Enhanced method, SHA-256. */
        WRAPENH2,
        /** Enhanced method, SHA-256 + TDES-CMAC. */
        WRAPENH3,
        /** Variable-length: key is in the clear. */
        CLEAR,
        /** Variable-length: AESKW. */
        AESKW,
        /** Variable-length: PKOAEP2. */
        PKOAEP2,
        RESERVED,
        NOT_APPLICABLE;

        public String code() { return name(); }

        /** True for the three enhanced methods, which no non-CCA system can open. */
        public boolean isEnhanced() {
            return this == WRAP_ENH || this == WRAPENH2 || this == WRAPENH3;
        }
    }

    /** Where the key-type name came from, so the batch can tell a read type from a defaulted one. */
    public enum KeyTypeSource {
        /** Decoded from the Control Vector against Table 676. */
        CONTROL_VECTOR,
        /** NOCV transport key: the token carries no CV to read. */
        NOCV,
        /** All-zero CV: legacy DATA key. */
        ZERO_CV,
        /** Read from the key-type field of a variable-length token. */
        VARIABLE_LENGTH,
        /** Derived from the PKA sections present. */
        PKA,
        /** RKX trusted block. */
        RKX,
        /** AES fixed-length: DATA is the only type this format has. */
        FIXED_AES,
        /** The token carries no key. */
        NONE,
        /** A CV was read but its value is not in Table 676. */
        UNRECOGNIZED;

        public String code() { return name(); }
    }
}
