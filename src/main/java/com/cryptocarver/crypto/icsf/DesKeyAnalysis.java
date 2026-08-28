package com.cryptocarver.crypto.icsf;

import com.cryptocarver.crypto.icsf.IcsfVocabulary.DesKeyForm;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.EffectiveStrength;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.WrapMethod;

import java.util.List;

/** How long a DES/TDES key is, and how much strength it actually delivers. */
public final class DesKeyAnalysis {

    private DesKeyAnalysis() { }

    /** The structural length, and what settled it. */
    public record KeyLength(DesKeyForm form, IcsfText basis, boolean uniqueParts) { }

    /** The comparison of the 8-byte components K1/K2/K3. */
    public record Components(int parts, List<String> partsHex, String pattern,
                             EffectiveStrength effective, boolean reliable, IcsfText reason) { }

    /** Wrapping method from flag byte 7, bits 0-2. */
    public static WrapMethod wrapMethod(byte[] token) {
        return switch ((IcsfHex.u8(token, 7) >> 5) & 0b111) {
            case 0b000 -> WrapMethod.ECB;
            case 0b001 -> WrapMethod.WRAP_ENH;
            case 0b010 -> WrapMethod.WRAPENH2;
            case 0b011 -> WrapMethod.WRAPENH3;
            default -> WrapMethod.RESERVED;
        };
    }

    /**
     * Decides whether a DES/TDES key is single, double or triple length.
     *
     * <p>Combined in order of trustworthiness:</p>
     * <ol start="0">
     *   <li>with WRAPENH3 the length is <b>not knowable</b>: Table 615 says the CV
     *       carries key-form bits "indicating a triple-length key" no matter what,
     *       and that offsets 24 and 48 always hold ciphertext "in order to obfuscate
     *       the length of the key". That is deliberate obfuscation, not a datum;</li>
     *   <li>byte 59, valid only for DATA keys with a zero Control Vector
     *       (X'00'/X'10'/X'20' = single/double/triple), Tables 615/616;</li>
     *   <li>the key-form bits (40-42) of the Control Vector when there is one,
     *       p. 1673: 000 single, x10/x01 double, x11 triple, high bit = unique parts;</li>
     *   <li>fall back to the token version for DATA keys with a zero CV:
     *       X'00' single, X'01' double or triple (p. 1561).</li>
     * </ol>
     */
    public static KeyLength keyLength(byte[] token) {
        if (wrapMethod(token) == WrapMethod.WRAPENH3) {
            return new KeyLength(DesKeyForm.OBFUSCATED, IcsfText.of("icsf.len.basis.wrapenh3"), false);
        }

        boolean zeroCv = IcsfHex.isAllZero(token, 32, 40);
        int byte59 = IcsfHex.u8(token, 59);
        if (zeroCv && (byte59 == 0x00 || byte59 == 0x10 || byte59 == 0x20)) {
            DesKeyForm form = switch (byte59) {
                case 0x00 -> DesKeyForm.SINGLE;
                case 0x10 -> DesKeyForm.DOUBLE;
                default -> DesKeyForm.TRIPLE;
            };
            return new KeyLength(form, IcsfText.of("icsf.len.basis.byte59"), false);
        }

        boolean cvPresent = (IcsfHex.u8(token, 6) & 0x40) != 0;
        if (!zeroCv || cvPresent) {
            int keyForm = (IcsfHex.u8(token, 37) >> 5) & 0x07;   // CV bits 40-42
            boolean unique = ((keyForm >> 2) & 1) != 0;
            int low2 = keyForm & 0x03;
            DesKeyForm form = switch (low2) {
                case 0b00 -> DesKeyForm.SINGLE;
                case 0b10, 0b01 -> DesKeyForm.DOUBLE;
                default -> DesKeyForm.TRIPLE;
            };
            return new KeyLength(form, IcsfText.of("icsf.len.basis.keyForm"),
                    unique && form != DesKeyForm.SINGLE);
        }

        if (IcsfHex.u8(token, 4) == 0x00) {
            return new KeyLength(DesKeyForm.SINGLE, IcsfText.of("icsf.len.basis.version00"), false);
        }
        return new KeyLength(DesKeyForm.DOUBLE_OR_TRIPLE,
                IcsfText.of("icsf.len.basis.version01"), false);
    }

    /**
     * Infers the effective strength of a DES/TDES key by comparing its 8-byte
     * components (K1 at 16, K2 at 24, K3 at 48).
     *
     * <p>Under ECB each 8-byte block is encrypted independently, so two components
     * equal in the clear produce identical ciphertext blocks. The comparison is
     * trustworthy when the key is in the clear, or when it is ECB-wrapped under a
     * zero Control Vector (every component then shares the same effective key).
     * It is not trustworthy under a non-zero CV, where each component is encrypted
     * under a different variant, nor under enhanced wrapping (CBC + confounder).</p>
     *
     * <p>Reducing TDES EDE = E_K3(D_K2(E_K1(P))):</p>
     * <ul>
     *   <li>K1=K3 != K2 → genuinely double (2-key TDES);</li>
     *   <li>K1, K2, K3 all different → genuinely triple;</li>
     *   <li>everything else (K1=K2=K3, K1=K2, K2=K3) collapses to a single DES.</li>
     * </ul>
     *
     * @return {@code null} when there is nothing to infer: a single-length key, or
     *         one whose length WRAPENH3 has obfuscated
     */
    public static Components components(byte[] token) {
        KeyLength length = keyLength(token);
        int parts;
        switch (length.form()) {
            case SINGLE, OBFUSCATED, NOT_APPLICABLE -> {
                return null;
            }
            case DOUBLE_OR_TRIPLE -> parts = IcsfHex.isAllZero(token, 48, 56) ? 2 : 3;
            case TRIPLE -> parts = 3;
            default -> parts = 2;
        }

        byte[] k1 = IcsfHex.slice(token, 16, 24);
        byte[] k2 = IcsfHex.slice(token, 24, 32);
        byte[] k3 = IcsfHex.slice(token, 48, 56);

        // --- can the comparison prove anything? ---------------------------
        boolean encrypted = (IcsfHex.u8(token, 6) & 0x80) != 0;
        WrapMethod wrap = wrapMethod(token);
        boolean zeroCv = IcsfHex.isAllZero(token, 32, 48);
        boolean reliable;
        IcsfText reason;
        if (!encrypted) {
            reliable = true;
            reason = IcsfText.of("icsf.components.reason.clear");
        } else if (wrap == WrapMethod.ECB && zeroCv) {
            reliable = true;
            reason = IcsfText.of("icsf.components.reason.ecbZeroCv");
        } else if (wrap != WrapMethod.ECB) {
            reliable = false;
            reason = IcsfText.of("icsf.components.reason.enhanced");
        } else {
            reliable = false;
            reason = IcsfText.of("icsf.components.reason.ecbNonZeroCv");
        }

        // --- equality pattern and effective strength ----------------------
        String pattern;
        EffectiveStrength effective;
        List<String> partsHex;
        if (parts == 3) {
            boolean eq12 = java.util.Arrays.equals(k1, k2);
            boolean eq13 = java.util.Arrays.equals(k1, k3);
            boolean eq23 = java.util.Arrays.equals(k2, k3);
            if (eq12 && eq13) {
                pattern = "K1 = K2 = K3";
                effective = EffectiveStrength.SINGLE;
            } else if (eq13) {
                pattern = "K1 = K3 != K2";
                effective = EffectiveStrength.DOUBLE;
            } else if (eq12) {
                pattern = "K1 = K2 != K3";
                effective = EffectiveStrength.SINGLE;
            } else if (eq23) {
                pattern = "K2 = K3 != K1";
                effective = EffectiveStrength.SINGLE;
            } else {
                pattern = "K1, K2, K3";
                effective = EffectiveStrength.TRIPLE;
            }
            partsHex = List.of(IcsfHex.hex(k1), IcsfHex.hex(k2), IcsfHex.hex(k3));
        } else {
            boolean equal = java.util.Arrays.equals(k1, k2);
            pattern = equal ? "K1 = K2" : "K1 != K2";
            effective = equal ? EffectiveStrength.SINGLE : EffectiveStrength.DOUBLE;
            partsHex = List.of(IcsfHex.hex(k1), IcsfHex.hex(k2));
        }

        return new Components(parts, partsHex, pattern,
                reliable ? effective : effective.asUnreliable(), reliable, reason);
    }

    /** A readable expansion of an effective-strength verdict, for the report card. */
    public static IcsfText describe(EffectiveStrength strength, String pattern) {
        return switch (strength) {
            case SINGLE -> IcsfText.of("icsf.strength.single", pattern);
            case DOUBLE -> IcsfText.of("icsf.strength.double");
            case TRIPLE -> IcsfText.of("icsf.strength.triple");
            case UNRELIABLE_SINGLE -> IcsfText.of("icsf.strength.unreliableSingle");
            case UNRELIABLE_DOUBLE -> IcsfText.of("icsf.strength.unreliableDouble");
            case UNRELIABLE_TRIPLE -> IcsfText.of("icsf.strength.unreliableTriple");
            case NOT_APPLICABLE -> IcsfText.of("icsf.strength.notApplicable");
        };
    }
}
