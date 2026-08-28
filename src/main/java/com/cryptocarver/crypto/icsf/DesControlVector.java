package com.cryptocarver.crypto.icsf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The DES Control Vector: key type, permitted uses and control bits.
 *
 * <p>Bit map from Appendix C, pp. 1671-1678. Bits are numbered from the most
 * significant bit of CV byte 0, as the manual numbers them.</p>
 */
public final class DesControlVector {

    private DesControlVector() { }

    /** What a Control Vector says about the key it protects. */
    public record Info(String keyType, IcsfText family, IcsfText usage, List<IcsfSection.Flag> flags) { }

    // --- Table 677 (p. 1674): main type, bits 8-11 ------------------------
    /** Table 677 (p. 1674): main type, bits 8-11. Keyed, so the report reads in the viewer's language. */
    private static final Map<Integer, String> MAIN_TYPE = Map.of(
            0b0000, "icsf.cv.mainType.data",
            0b0010, "icsf.cv.mainType.pin",
            0b0011, "icsf.cv.mainType.cryptoVariable",
            0b0100, "icsf.cv.mainType.kek",
            0b0101, "icsf.cv.mainType.keyGenerating",
            0b0111, "icsf.cv.mainType.diversified");

    // --- Table 678 (p. 1675): subtype, bits 12-14, per main type ----------
    private static final Map<Integer, Map<Integer, String>> SUBTYPE = Map.of(
            0b0000, Map.of(
                    0b000, "icsf.cv.subtype.compatibility",
                    0b001, "icsf.cv.subtype.confidentiality",
                    0b010, "icsf.cv.subtype.mac",
                    0b101, "icsf.cv.subtype.secureMessaging",
                    0b110, "icsf.cv.subtype.ciphertextTranslation"),
            0b0010, Map.of(
                    0b000, "icsf.cv.subtype.inboundPin",
                    0b001, "icsf.cv.subtype.pinGenVer",
                    0b010, "icsf.cv.subtype.outboundPin"),
            0b0011, Map.of(0b111, "icsf.cv.subtype.cryptoVariable"),
            0b0100, Map.of(
                    0b000, "icsf.cv.subtype.transportSending",
                    0b001, "icsf.cv.subtype.transportReceiving"));

    /**
     * Concrete key type, from Table 676 "Default control vector values" (pp. 1669-1671).
     *
     * <p>Keyed by CV byte 1 and byte 2 with bits 17 (export) and 23 (parity)
     * masked out, because those change without changing the key type.</p>
     */
    private static final Map<Integer, String> KEY_TYPE = buildKeyTypes();

    private static Map<Integer, String> buildKeyTypes() {
        Map<Integer, String> map = new LinkedHashMap<>();
        map.put(key(0x00, 0x3C), "DATA");
        map.put(key(0x00, 0x30), "DATAC");
        map.put(key(0x00, 0x0C), "DATAM");
        map.put(key(0x00, 0x04), "DATAMV");
        map.put(key(0x03, 0x30), "CIPHER");
        map.put(key(0x03, 0x10), "DECIPHER");
        map.put(key(0x03, 0x20), "ENCIPHER");
        map.put(key(0x0C, 0x10), "CIPHERXI");
        map.put(key(0x0C, 0x20), "CIPHERXO");
        map.put(key(0x0C, 0x30), "CIPHERXL");
        map.put(key(0x05, 0x0C), "MAC");
        map.put(key(0x05, 0x04), "MACVER");
        map.put(key(0x21, 0x1E), "IPINENC");
        map.put(key(0x24, 0x36), "OPINENC");
        map.put(key(0x22, 0x3E), "PINGEN");
        map.put(key(0x22, 0x02), "PINVER");
        map.put(key(0x3F, 0x00), "CVARPINE");
        map.put(key(0x3F, 0x02), "CVARDEC");
        map.put(key(0x3F, 0x04), "CVARXCVL");
        map.put(key(0x3F, 0x06), "CVARXCVR");
        map.put(key(0x3F, 0x08), "CVARENC");
        map.put(key(0x41, 0x3C), "EXPORTER");
        map.put(key(0x41, 0x02), "OKEYXLAT");
        map.put(key(0x42, 0x3C), "IMPORTER");
        map.put(key(0x42, 0x02), "IKEYXLAT");
        map.put(key(0x42, 0x04), "IMP-PKA");
        return Map.copyOf(map);
    }

    private static int key(int byte1, int byte2Masked) {
        return (byte1 << 8) | byte2Masked;
    }

    /** Usage bits 18-22, whose meaning belongs to each family (pp. 1672-1673). */
    private record UsageBit(int bit, String name) { }

    private static final Map<String, List<UsageBit>> USAGE_BITS = Map.of(
            "KEK_SEND", List.of(new UsageBit(18, "IMEX"), new UsageBit(19, "OPEX"),
                    new UsageBit(20, "EXEX"), new UsageBit(21, "EXPORT"), new UsageBit(22, "XLATE")),
            "KEK_RECV", List.of(new UsageBit(18, "IMEX"), new UsageBit(19, "OPIM"),
                    new UsageBit(20, "IMIM"), new UsageBit(21, "IMPORT"), new UsageBit(22, "XLATE")),
            "DATA", List.of(new UsageBit(18, "ENCIPHER"), new UsageBit(19, "DECIPHER"),
                    new UsageBit(20, "MACGEN"), new UsageBit(21, "MACVER")),
            "MAC", List.of(new UsageBit(20, "MACGEN"), new UsageBit(21, "MACVER")),
            "SECMSG", List.of(new UsageBit(18, "SMKEY"), new UsageBit(19, "SMPIN")),
            "PINGEN", List.of(new UsageBit(18, "CPINGEN"), new UsageBit(19, "EPINGENA"),
                    new UsageBit(20, "EPINGEN"), new UsageBit(21, "CPINGENA"), new UsageBit(22, "EPINVER")),
            "IPINENC", List.of(new UsageBit(19, "EPINVER"), new UsageBit(20, "CPINGENA"),
                    new UsageBit(21, "TRANSLAT"), new UsageBit(22, "REFORMAT")),
            "OPINENC", List.of(new UsageBit(18, "CPINENC"), new UsageBit(19, "EPINGEN"),
                    new UsageBit(21, "TRANSLAT"), new UsageBit(22, "REFORMAT")),
            "KEYGENKY", List.of(new UsageBit(18, "UKPT"), new UsageBit(19, "CLR8-ENC")));

    /** Bits 0-3 of a MAC / MACVER CV (p. 1673). */
    private static final Map<Integer, String> MAC_SUBTYPE = Map.of(
            0b0000, "ANY", 0b0001, "ANSI X9.9", 0b0010, "CVV KEY-A",
            0b0011, "CVV KEY-B", 0b0100, "AMEX-CSC");

    /** Bits 0-3 of a PINGEN / PINVER CV (p. 1676). */
    private static final Map<Integer, String> PIN_METHOD = Map.of(
            0b0000, "NO-SPEC", 0b0001, "IBM-PIN / IBM-PINO",
            0b0010, "VISA-PVV", 0b0011, "INBK-PIN",
            0b0100, "GBP-PIN / GBP-PINO", 0b0101, "NL-PIN-1");

    /** Bits 18-22 of a cryptographic-variable CV (p. 1673). */
    private static final Map<Integer, String> CVAR_TYPE = Map.of(
            0b00000, "CVARPINE", 0b00001, "CVARDEC", 0b00010, "CVARXCVL",
            0b00011, "CVARXCVR", 0b00100, "CVARENC");

    /** Bits 19-22 of a DKYGENKY: the type of final key it may generate (p. 1678). */
    private static final Map<Integer, String> DKY_USAGE = Map.of(
            0b0001, "icsf.cv.dky.DDATA", 0b0010, "icsf.cv.dky.DMAC",
            0b0011, "icsf.cv.dky.DMV", 0b0100, "icsf.cv.dky.DIMP",
            0b0101, "icsf.cv.dky.DEXP", 0b0110, "icsf.cv.dky.DPVR",
            0b1000, "icsf.cv.dky.DMKEY",
            0b1001, "icsf.cv.dky.DMPIN",
            0b1111, "icsf.cv.dky.DALL");

    /** Bit {@code n} of the Control Vector, counted from the MSB of CV byte 0 (p. 1678). */
    public static boolean bit(byte[] cv, int n) {
        int index = n / 8;
        if (index >= cv.length) return false;
        return (cv[index] & (0x80 >> (n % 8))) != 0;
    }

    /**
     * The structural rules a CV has to satisfy (p. 1678):
     * <ul>
     *   <li>parity: each byte's low bit is set so the byte holds an even number of zero
     *       bits, which over eight bits is the same as an even number of one bits;</li>
     *   <li>anti-variant bits: bit 30 = 0 and bit 38 = 1.</li>
     * </ul>
     */
    public static boolean structureOk(byte[] cv) {
        for (byte value : cv) {
            if (Integer.bitCount(value & 0xFF) % 2 != 0) return false;
        }
        return !bit(cv, 30) && bit(cv, 38);
    }

    /**
     * Whether the token's CV carries control bits worth reading.
     *
     * <p>It does not when the key is NOCV (flag byte 6, bit 2: used without a
     * control vector, transport keys only) or when the CV is all zeros, the
     * legacy DATA "zero CV" of Table 676 (p. 1670).</p>
     */
    public static boolean usable(byte[] token) {
        return !IcsfHex.bit(IcsfHex.u8(token, 6), 2) && !IcsfHex.isAllZero(token, 32, 40);
    }

    /**
     * Translates a Control Vector into key type and permitted uses.
     *
     * <p>Structure: bits 0-3 MAC subtype or PIN calculation method; bits 8-11 main
     * type (Table 677); bits 12-14 subtype (Table 678); bits 18-22 usage bits,
     * each family reading them its own way; bit 37 NOOFFSET on PINGEN / PINVER.</p>
     */
    public static Info decode(byte[] cv) {
        int byte1 = cv.length > 1 ? cv[1] & 0xFF : 0;
        int byte2 = cv.length > 2 ? cv[2] & 0xFF : 0;
        int main = (byte1 >> 4) & 0x0F;
        int subtype = (byte1 >> 1) & 0x07;
        int usageBits = byte2 & 0x3E;
        int lookup = byte2 & 0xBE;              // without bit 17 (export) or bit 23 (parity)

        IcsfText mainText = MAIN_TYPE.containsKey(main)
                ? IcsfText.of(MAIN_TYPE.get(main))
                : IcsfText.of("icsf.cv.undocumentedBits", binary(main, 4));
        String subKey = SUBTYPE.getOrDefault(main, Map.of()).get(subtype);
        IcsfText family = subKey == null
                ? IcsfText.of("icsf.cv.family", mainText)
                : IcsfText.of("icsf.cv.familyWithSubtype", mainText, IcsfText.of(subKey));

        List<IcsfSection.Flag> flags = new ArrayList<>();
        IcsfText usage = IcsfText.EMPTY;
        String keyType = KEY_TYPE.getOrDefault(key(byte1, lookup), "");

        // --- families that read the usage bits their own way --------------
        if (main == 0b0111) {                                   // DKYGENKY
            keyType = "DKYGENKY (subtype " + subtype + ")";
            int finalType = (byte2 >> 1) & 0x0F;                // bits 19-22
            usage = IcsfText.of("icsf.cv.dkyUsage", DKY_USAGE.containsKey(finalType)
                    ? IcsfText.of(DKY_USAGE.get(finalType))
                    : IcsfText.of("icsf.cv.undocumentedBits", binary(finalType, 4)));
            flags.add(new IcsfSection.Flag(IcsfText.of("icsf.cv.flag.dkyLevel"), subtype != 0,
                    IcsfText.of(subtype != 0 ? "icsf.cv.flag.dkyLevelNested"
                            : "icsf.cv.flag.dkyLevelFinal", subtype)));
        } else if (byte1 == 0x53) {                             // KEYGENKY
            if (keyType.isEmpty()) keyType = "KEYGENKY";
        } else if (main == 0b0011) {                            // cryptographic variable
            int variable = (byte2 >> 1) & 0x1F;                 // bits 18-22
            usage = CVAR_TYPE.containsKey(variable)
                    ? IcsfText.raw(CVAR_TYPE.get(variable))
                    : IcsfText.of("icsf.cv.undocumentedVariable", binary(variable, 5));
            if (keyType.isEmpty()) keyType = "CVAR";
        }

        String group = null;
        if (main == 0b0100) {
            group = subtype == 0b000 ? "KEK_SEND" : (subtype == 0b001 ? "KEK_RECV" : null);
        } else if (main == 0b0000) {
            group = subtype == 0b010 ? "MAC" : (subtype == 0b101 ? "SECMSG" : "DATA");
        } else if (main == 0b0010) {
            group = switch (subtype) {
                case 0b000 -> "IPINENC";
                case 0b001 -> "PINGEN";
                case 0b010 -> "OPINENC";
                default -> null;
            };
        } else if (byte1 == 0x53) {
            group = "KEYGENKY";
        }

        if (group != null) {
            List<String> on = new ArrayList<>();
            for (UsageBit usageBit : USAGE_BITS.get(group)) {
                boolean set = bit(cv, usageBit.bit());
                flags.add(new IcsfSection.Flag(
                        IcsfText.of("icsf.cv.flag.use", usageBit.name(), usageBit.bit()), set));
                if (set) on.add(usageBit.name());
            }
            // The usage mnemonics (IMEX, EXPORT, XLATE...) are identifiers, not words.
            usage = on.isEmpty() ? IcsfText.of("icsf.cv.noUsageBits") : IcsfText.raw(String.join(", ", on));
            if ("MAC".equals(group)) {
                // p. 1675: bits 20-21 = B'11' generate key, B'01' verify only.
                int macBits = (bit(cv, 20) ? 2 : 0) | (bit(cv, 21) ? 1 : 0);
                usage = switch (macBits) {
                    case 0b11 -> IcsfText.of("icsf.cv.macGenerateAndVerify");
                    case 0b01 -> IcsfText.of("icsf.cv.macVerifyOnly");
                    default -> IcsfText.of("icsf.cv.macUndocumented", binary(macBits, 2));
                };
            }
        }

        // --- bits 0-3: MAC subtype or PIN method ---------------------------
        int nibble = (cv.length > 0 ? (cv[0] & 0xFF) >> 4 : 0) & 0x0F;
        if ("MAC".equals(group)) {
            flags.add(new IcsfSection.Flag(IcsfText.of("icsf.cv.flag.macSubtype"), nibble != 0,
                    MAC_SUBTYPE.containsKey(nibble) ? IcsfText.raw(MAC_SUBTYPE.get(nibble))
                            : IcsfText.of("icsf.cv.undocumentedBits", binary(nibble, 4))));
            if (MAC_SUBTYPE.containsKey(nibble)) {
                usage = IcsfText.of("icsf.cv.usageWithAlgorithm", usage, MAC_SUBTYPE.get(nibble));
            }
        } else if ("PINGEN".equals(group)) {
            flags.add(new IcsfSection.Flag(IcsfText.of("icsf.cv.flag.pinMethod"), nibble != 0,
                    PIN_METHOD.containsKey(nibble) ? IcsfText.raw(PIN_METHOD.get(nibble))
                            : IcsfText.of("icsf.cv.undocumentedBits", binary(nibble, 4))));
            flags.add(new IcsfSection.Flag(IcsfText.of("icsf.cv.flag.noOffset"), bit(cv, 37),
                    IcsfText.of("icsf.cv.flag.noOffsetHelp")));
            if (PIN_METHOD.containsKey(nibble)) {
                usage = IcsfText.of("icsf.cv.usageWithMethod", usage, PIN_METHOD.get(nibble));
            }
        }

        if (usage.isEmpty()) {
            usage = IcsfText.of("icsf.cv.usageUndecoded", binary(usageBits >> 1, 5));
        }
        return new Info(keyType, family, usage, List.copyOf(flags));
    }

    private static String binary(int value, int width) {
        String bits = Integer.toBinaryString(value);
        return "0".repeat(Math.max(0, width - bits.length())) + bits;
    }
}
