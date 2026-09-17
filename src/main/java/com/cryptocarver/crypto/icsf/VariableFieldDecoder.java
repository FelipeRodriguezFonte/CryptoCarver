package com.cryptocarver.crypto.icsf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Byte-by-byte reading of the key-usage (Tables 619-628, 631) and key-management
 * (Table 629) fields of a variable-length symmetric token.
 *
 * <p>Every byte of every declared field is reported on its own: its offset, its bit
 * pattern, the Key Token Build2 keyword each bit stands for and what that keyword
 * allows. A hex run such as {@code 010001004000F800} tells an operator nothing; the
 * restriction worth seeing is that KUF3 is {@code B'0100 0000'}, WR-AES and nothing
 * else. Bits the table reserves and combinations it rules out are warned about
 * rather than silently ignored.</p>
 *
 * <p>Keywords are IBM identifiers and stay verbatim; their explanations are bundle
 * keys, rendered in the reader's language.</p>
 */
final class VariableFieldDecoder {

    private VariableFieldDecoder() { }

    private static IcsfText t(String key, Object... arguments) {
        return IcsfText.of(key, arguments);
    }

    // =====================================================================
    // Model
    // =====================================================================

    /**
     * One state a byte can express: an IBM keyword (may be null) and what it means.
     * {@code defaultState} marks what a clear bit means (UDX both, NOP2AUTH, XPRT-DES...):
     * shown in the detail, but not a use worth listing in the summary.
     */
    private record Meaning(String keyword, String key, boolean defaultState) {
        Meaning(String keyword, String key) {
            this(keyword, key, false);
        }
    }

    /** What one byte says. */
    private static final class Reading {
        final List<Meaning> present = new ArrayList<>();
        final List<String> absentKeywords = new ArrayList<>();
        int reservedBits;
        boolean undefinedValue;
        final List<IcsfText> conflicts = new ArrayList<>();
    }

    /** The whole usage-field run, so a byte can depend on another (PINPROT direction, KEK EXPTT31D). */
    private record Usage(byte[] fields, int shift) {
        int high(int field) {
            int offset = (field - 1 + shift) * 2;
            return offset >= 0 && offset < fields.length ? fields[offset] & 0xFF : 0;
        }

        int low(int field) {
            int offset = (field - 1 + shift) * 2 + 1;
            return offset >= 0 && offset < fields.length ? fields[offset] & 0xFF : 0;
        }

        /** The same run seen from {@code fields} further on: DKYGENKY's related usage fields. */
        Usage shifted(int fieldsFurther) {
            return new Usage(fields, shift + fieldsFurther);
        }
    }

    @FunctionalInterface
    private interface ByteRule {
        Reading read(int value, Usage usage);
    }

    /** How to read one 2-byte field, and the role shown next to each byte's name (may be null). */
    private record FieldRule(String highRole, ByteRule high, String lowRole, ByteRule low) {
        /** The low byte takes its role from its rule when that rule is a shared one. */
        FieldRule(String role, ByteRule high, ByteRule low) {
            this(role, high, low == UDX ? "icsf.kuf.role.udx" : low == RESERVED ? "icsf.kuf.role.reserved" : role, low);
        }
    }

    /** A single bit, with the keyword for when it is off if the table names one. */
    private record Bit(int mask, String keyword, String key, String offKeyword, String offKey) {
        Bit(int mask, String keyword, String key) {
            this(mask, keyword, key, null, null);
        }
    }

    // =====================================================================
    // Rule builders
    // =====================================================================

    private static ByteRule bits(Bit... bits) {
        return (value, usage) -> {
            Reading reading = new Reading();
            int defined = 0;
            for (Bit bit : bits) {
                defined |= bit.mask();
                if ((value & bit.mask()) == bit.mask()) {
                    reading.present.add(new Meaning(bit.keyword(), bit.key()));
                } else if (bit.offKey() != null) {
                    reading.present.add(new Meaning(bit.offKeyword(), bit.offKey(), true));
                } else if (bit.keyword() != null) {
                    reading.absentKeywords.add(bit.keyword());
                }
            }
            reading.reservedBits = value & ~defined & 0xFF;
            return reading;
        };
    }

    private static ByteRule values(Map<Integer, Meaning> meanings) {
        return (value, usage) -> {
            Reading reading = new Reading();
            Meaning meaning = meanings.get(value);
            if (meaning == null) {
                reading.undefinedValue = true;
            } else {
                reading.present.add(meaning);
            }
            return reading;
        };
    }

    private static Map<Integer, Meaning> map(Object... pairs) {
        Map<Integer, Meaning> map = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 3) {
            map.put((Integer) pairs[index], new Meaning((String) pairs[index + 1], (String) pairs[index + 2]));
        }
        return map;
    }

    /** Every bit reserved: the byte must be zero. */
    private static final ByteRule RESERVED = (value, usage) -> {
        Reading reading = new Reading();
        reading.reservedBits = value;
        return reading;
    };

    /** Low-order byte of key-usage field 1 for every AES/HMAC type: UDX control. */
    private static final ByteRule UDX = bits(
            new Bit(0x08, "UDX-ONLY", "icsf.kuf.udx.only", null, "icsf.kuf.udx.both"),
            new Bit(0x04, "UDX-100", "icsf.kuf.udx.userBit"),
            new Bit(0x02, "UDX-010", "icsf.kuf.udx.userBit"),
            new Bit(0x01, "UDX-001", "icsf.kuf.udx.userBit"));

    /** The two top bits of MAC, PIN and PINPRW keys: which of the two operations the key may do. */
    private static ByteRule twoBitOperation(Map<Integer, Meaning[]> patterns) {
        return (value, usage) -> {
            Reading reading = new Reading();
            Meaning[] meanings = patterns.get(value >> 6);
            if (meanings == null) {
                reading.undefinedValue = true;
            } else {
                reading.present.addAll(List.of(meanings));
            }
            reading.reservedBits = value & 0x3F;
            return reading;
        };
    }

    private static final Meaning GENERATE = new Meaning("GENERATE", "icsf.kuf.generate");
    private static final Meaning VERIFY = new Meaning("VERIFY", "icsf.kuf.verify");

    private static final ByteRule CBC_ONLY = values(map(0x00, "CBC", "icsf.kuf.mode.cbc"));
    private static final ByteRule CMAC_ONLY = values(map(0x01, "CMAC", "icsf.kuf.cmac"));

    // =====================================================================
    // Tables
    // =====================================================================

    private static final FieldRule[] DESUSECV = {
            new FieldRule("icsf.kuf.role.reserved", RESERVED, "icsf.kuf.role.reserved", RESERVED)};

    private static final FieldRule[] HMAC = {
            new FieldRule("icsf.kuf.role.operation",
                    bits(new Bit(0x80, "GENERATE", "icsf.kuf.generate"),
                            new Bit(0x40, "VERIFY", "icsf.kuf.verify")),
                    UDX),
            new FieldRule("icsf.kuf.role.hash",
                    bits(new Bit(0x80, "SHA-1", "icsf.kuf.hashAllowed"),
                            new Bit(0x40, "SHA-224", "icsf.kuf.hashAllowed"),
                            new Bit(0x20, "SHA-256", "icsf.kuf.hashAllowed"),
                            new Bit(0x10, "SHA-384", "icsf.kuf.hashAllowed"),
                            new Bit(0x08, "SHA-512", "icsf.kuf.hashAllowed")),
                    RESERVED)};

    private static final FieldRule[] AES_CIPHER = {
            new FieldRule("icsf.kuf.role.operation",
                    bits(new Bit(0x80, "ENCRYPT", "icsf.kuf.encrypt"),
                            new Bit(0x40, "DECRYPT", "icsf.kuf.decrypt"),
                            new Bit(0x20, "C-XLATE", "icsf.kuf.cipherTranslateOnly")),
                    UDX),
            new FieldRule("icsf.kuf.role.mode",
                    values(map(0x00, "CBC", "icsf.kuf.mode.cbc", 0x01, "ECB", "icsf.kuf.mode.ecb",
                            0x02, "CFB", "icsf.kuf.mode.cfb", 0x03, "OFB", "icsf.kuf.mode.ofb",
                            0x04, "GCM", "icsf.kuf.mode.gcm", 0x05, "XTS", "icsf.kuf.mode.xts",
                            0x06, "FF1", "icsf.kuf.mode.ff1", 0x07, "FF2", "icsf.kuf.mode.ff2",
                            0x08, "FF2.1", "icsf.kuf.mode.ff21", 0xFF, "ANY-MODE", "icsf.kuf.mode.any")),
                    RESERVED)};

    /** Key-usage field 3 of a DK-enabled key; {@code allowed} lists the common-control values. */
    private static FieldRule dkField(Map<Integer, Meaning> allowed) {
        Map<Integer, Meaning> high = new LinkedHashMap<>(allowed);
        high.put(0x00, new Meaning(null, "icsf.kuf.dk.none", true));
        return new FieldRule("icsf.kuf.role.dk", values(high), "icsf.kuf.role.fieldFormat",
                values(map(0x00, null, "icsf.kuf.dk.notEnabled", 0x01, null, "icsf.kuf.dk.enabled")));
    }

    private static final FieldRule[] AES_MAC = {
            new FieldRule("icsf.kuf.role.operation",
                    twoBitOperation(Map.of(0b01, new Meaning[]{VERIFY}, 0b10, new Meaning[]{GENERATE},
                            0b11, new Meaning[]{GENERATE, VERIFY})),
                    UDX),
            new FieldRule("icsf.kuf.role.mode", CMAC_ONLY, "icsf.kuf.role.authData",
                    bits(new Bit(0x80, "PTR2AUTH", "icsf.kuf.ptr2auth", "NOP2AUTH", "icsf.kuf.nop2auth"))),
            dkField(map(0x01, "DKPINOP", "icsf.kuf.dk.pinop", 0x03, "DKPINAD1", "icsf.kuf.dk.pinad1",
                    0x04, "DKPINAD2", "icsf.kuf.dk.pinad2"))};

    private static final FieldRule[] AES_PINCALC = {
            new FieldRule("icsf.kuf.role.operation",
                    twoBitOperation(Map.of(0b10, new Meaning[]{GENERATE})), UDX),
            new FieldRule("icsf.kuf.role.mode", CBC_ONLY, RESERVED),
            dkField(map(0x01, "DKPINOP", "icsf.kuf.dk.pinop"))};

    private static final FieldRule[] AES_PINPRW = {
            new FieldRule("icsf.kuf.role.operation",
                    twoBitOperation(Map.of(0b01, new Meaning[]{VERIFY}, 0b10, new Meaning[]{GENERATE})), UDX),
            new FieldRule("icsf.kuf.role.mode", CMAC_ONLY, RESERVED),
            dkField(map(0x01, "DKPINOP", "icsf.kuf.dk.pinop"))};

    private static final ByteRule PINPROT_OPERATIONS = (value, usage) -> {
        int direction = usage.high(1) >> 6;
        if (direction == 0b01) {
            return bits(new Bit(0x10, "EPINVER", "icsf.kuf.pin.epinver"),
                    new Bit(0x08, "CPINGENA", "icsf.kuf.pin.cpingena"),
                    new Bit(0x04, "PINXLATE", "icsf.kuf.pin.pinxlate"),
                    new Bit(0x02, "REFORMAT", "icsf.kuf.pin.reformat"),
                    new Bit(0x01, "RFMT4TO1", "icsf.kuf.pin.rfmt4to1")).read(value, usage);
        }
        if (direction == 0b10) {
            return bits(new Bit(0x20, "CPINENC", "icsf.kuf.pin.cpinenc"),
                    new Bit(0x10, "EPINGEN", "icsf.kuf.pin.epingen"),
                    new Bit(0x04, "PINXLATE", "icsf.kuf.pin.pinxlate"),
                    new Bit(0x02, "REFORMAT", "icsf.kuf.pin.reformat"),
                    new Bit(0x01, "RFMT1TO4", "icsf.kuf.pin.rfmt1to4")).read(value, usage);
        }
        // Without a direction the table defines none of these bits.
        Reading reading = RESERVED.read(value, usage);
        if (value != 0) reading.conflicts.add(t("icsf.kuf.conflict.pinprotNoDirection"));
        return reading;
    };

    private static final ByteRule PINPROT_FIELD_FORMAT = (value, usage) ->
            usage.low(3) == 0x01
                    ? values(map(0x01, "DKPINOP", "icsf.kuf.dk.pinop", 0x02, "DKPINOPP", "icsf.kuf.dk.pinopp",
                            0x03, "DKPINAD1", "icsf.kuf.dk.pinad1")).read(value, usage)
                    : values(map(0x00, "NOFLDFMT", "icsf.kuf.noFieldFormat")).read(value, usage);

    private static final FieldRule[] AES_PINPROT = {
            new FieldRule("icsf.kuf.role.direction",
                    twoBitOperation(Map.of(0b01, new Meaning[]{new Meaning("DECRYPT", "icsf.kuf.pin.inbound")},
                            0b10, new Meaning[]{new Meaning("ENCRYPT", "icsf.kuf.pin.outbound")})),
                    UDX),
            new FieldRule("icsf.kuf.role.mode", CBC_ONLY, "icsf.kuf.role.pinOperations", PINPROT_OPERATIONS),
            new FieldRule("icsf.kuf.role.dk", PINPROT_FIELD_FORMAT, "icsf.kuf.role.fieldFormat",
                    values(map(0x00, "NOFLDFMT", "icsf.kuf.noFieldFormat", 0x01, null, "icsf.kuf.dk.enabled"))),
            new FieldRule("icsf.kuf.role.pinBlockFormat",
                    bits(new Bit(0x01, "ISO-4", "icsf.kuf.pin.iso4Only")), RESERVED)};

    private static final ByteRule KEK_OPERATIONS_EXPORTER = kekOperations("EXPORT", "GEN-OPEX", "GEN-EXEX",
            "EXPTT31D", "icsf.kuf.kek.tt31Export");
    private static final ByteRule KEK_OPERATIONS_IMPORTER = kekOperations("IMPORT", "GEN-OPIM", "GEN-IMIM",
            "IMPTT31D", "icsf.kuf.kek.tt31Import");

    /** Table 627, KUF1 HOB: either the six classic operations, or B'0000 0001' alone. */
    private static ByteRule kekOperations(String direction, String generateOperational, String generateSame,
                                          String keyBlockKeyword, String keyBlockKey) {
        ByteRule classic = bits(
                new Bit(0x80, direction, "EXPORT".equals(direction) ? "icsf.kuf.kek.export" : "icsf.kuf.kek.import"),
                new Bit(0x40, "TRANSLAT", "icsf.kuf.kek.translate"),
                new Bit(0x20, generateOperational, "icsf.kuf.kek.genOperational"),
                new Bit(0x10, "GEN-IMEX", "icsf.kuf.kek.genImex"),
                new Bit(0x08, generateSame, "icsf.kuf.kek.genSame"),
                new Bit(0x04, "GEN-PUB", "icsf.kuf.kek.genPub"));
        return (value, usage) -> {
            if (value == 0x01) {
                Reading reading = new Reading();
                reading.present.add(new Meaning(keyBlockKeyword, keyBlockKey));
                return reading;
            }
            Reading reading = classic.read(value & ~0x01, usage);
            reading.reservedBits |= value & 0x02;
            if ((value & 0x01) != 0) {
                reading.conflicts.add(t("icsf.kuf.conflict.tt31Exclusive", keyBlockKeyword));
            }
            return reading;
        };
    }

    private static final ByteRule KEK_KEY_BLOCK = (value, usage) -> {
        Reading reading = bits(new Bit(0x80, "WR-TR31", "icsf.kuf.kek.wrTr31"),
                new Bit(0x01, "VARDRV-D", "icsf.kuf.kek.vardrvD")).read(value, usage);
        boolean keyBlockBinding = usage.high(1) == 0x01;
        if ((value & 0x01) != 0 && !keyBlockBinding) {
            reading.conflicts.add(t("icsf.kuf.conflict.vardrvWithoutTt31"));
        }
        if ((value & 0x80) != 0 && keyBlockBinding) {
            reading.conflicts.add(t("icsf.kuf.conflict.wrTr31WithTt31"));
        }
        return reading;
    };

    private static FieldRule[] kek(ByteRule operations) {
        return new FieldRule[]{
                new FieldRule("icsf.kuf.role.operation", operations, UDX),
                new FieldRule("icsf.kuf.role.keyBlock", KEK_KEY_BLOCK, "icsf.kuf.role.raw",
                        bits(new Bit(0x01, "KEK-RAW", "icsf.kuf.kek.raw"))),
                new FieldRule("icsf.kuf.role.wrapAlgorithms",
                        bits(new Bit(0x80, "WR-DES", "icsf.kuf.wrap.des"),
                                new Bit(0x40, "WR-AES", "icsf.kuf.wrap.aes"),
                                new Bit(0x20, "WR-HMAC", "icsf.kuf.wrap.hmac"),
                                new Bit(0x10, "WR-RSA", "icsf.kuf.wrap.rsa"),
                                new Bit(0x08, "WR-ECC", "icsf.kuf.wrap.ecc"),
                                new Bit(0x04, "WR-QSA", "icsf.kuf.wrap.qsa")),
                        RESERVED),
                new FieldRule("icsf.kuf.role.wrapClasses",
                        bits(new Bit(0x80, "WR-DATA", "icsf.kuf.wrap.data"),
                                new Bit(0x40, "WR-KEK", "icsf.kuf.wrap.kek"),
                                new Bit(0x20, "WR-PIN", "icsf.kuf.wrap.pin"),
                                new Bit(0x10, "WRDERIVE", "icsf.kuf.wrap.derivation"),
                                new Bit(0x08, "WR-CARD", "icsf.kuf.wrap.card"),
                                new Bit(0x04, "WR-CVAR", "icsf.kuf.wrap.cvar")),
                        RESERVED)};
    }

    private static final FieldRule[] AES_EXPORTER = kek(KEK_OPERATIONS_EXPORTER);
    private static final FieldRule[] AES_IMPORTER = kek(KEK_OPERATIONS_IMPORTER);

    private static final FieldRule[] AES_SECMSG = {
            new FieldRule("icsf.kuf.role.operation",
                    values(map(0x00, "SMPIN", "icsf.kuf.secmsg.smpin")), UDX),
            new FieldRule("icsf.kuf.role.serviceRestriction",
                    values(map(0x00, "ANY-USE", "icsf.kuf.secmsg.anyUse", 0x01, "DPC-ONLY", "icsf.kuf.secmsg.dpcOnly")),
                    RESERVED)};

    private static final FieldRule KDKGENKY_TYPE = new FieldRule("icsf.kuf.role.diversification",
            values(map(0x00, "KDKTYPEA", "icsf.kuf.kdk.typeA", 0x01, "KDKTYPEB", "icsf.kuf.kdk.typeB")), UDX);

    /** Table 625: what KUF1 HOB of a DKYGENKY says it may generate, with the key type whose table then applies. */
    private record Derivable(String keyword, String keyType, Set<Integer> counts) { }

    private static final Map<Integer, Derivable> DERIVABLE = Map.of(
            0x00, new Derivable("D-ALL", null, Set.of(2)),
            0x01, new Derivable("D-CIPHER", "CIPHER", Set.of(4)),
            0x02, new Derivable("D-MAC", "MAC", Set.of(4, 5)),
            0x03, new Derivable("D-EXP", "EXPORTER", Set.of(6)),
            0x04, new Derivable("D-IMP", "IMPORTER", Set.of(6)),
            0x05, new Derivable("D-PPROT", "PINPROT", Set.of(5)),
            0x06, new Derivable("D-PCALC", "PINCALC", Set.of(5)),
            0x07, new Derivable("D-PPRW", "PINPRW", Set.of(5)),
            0x08, new Derivable("D-SECMSG", "SECMSG", Set.of(4)),
            0x09, new Derivable("D-KDKGKY", "KDKGENKY", Set.of(13, 25, 37, 49)));

    private static final FieldRule DKYGENKY_TYPE = new FieldRule("icsf.kuf.role.derivable",
            (value, usage) -> {
                Reading reading = new Reading();
                Derivable derivable = DERIVABLE.get(value);
                if (derivable == null) {
                    reading.undefinedValue = true;
                } else {
                    reading.present.add(new Meaning(derivable.keyword(), "icsf.kuf.dky.generates"));
                }
                return reading;
            },
            "icsf.kuf.role.dukptAndUdx",
            bits(new Bit(0x80, "A-DUKPT", "icsf.kuf.dky.aesDukpt"),
                    new Bit(0x08, "UDX-ONLY", "icsf.kuf.udx.only", null, "icsf.kuf.udx.both"),
                    new Bit(0x04, "UDX-100", "icsf.kuf.udx.userBit"),
                    new Bit(0x02, "UDX-010", "icsf.kuf.udx.userBit"),
                    new Bit(0x01, "UDX-001", "icsf.kuf.udx.userBit")));

    private static final FieldRule DKYGENKY_CONTROL = new FieldRule("icsf.kuf.role.derivationControl",
            bits(new Bit(0x80, "KUF-MBE", "icsf.kuf.dky.kufMbe", "KUF-MBP", "icsf.kuf.dky.kufMbp"),
                    new Bit(0x40, "KMF-MBP", "icsf.kuf.dky.kmfMbp", "KMF-GND", "icsf.kuf.dky.kmfGnd"),
                    new Bit(0x20, "KMF-MBE", "icsf.kuf.dky.kmfMbe", "KMF-GND2", "icsf.kuf.dky.kmfGnd2")),
            "icsf.kuf.role.derivationLevel",
            values(map(0x00, "DKYL0", "icsf.kuf.dky.level0", 0x01, "DKYL1", "icsf.kuf.dky.level1",
                    0x02, "DKYL2", "icsf.kuf.dky.level2")));

    /** Table 625, KUF4 of a D-MAC generating key: the MAC table's KUF2 plus the M of N MAC bits. */
    private static final FieldRule DKYGENKY_DMAC_FIELD4 = new FieldRule("icsf.kuf.role.mode", CMAC_ONLY,
            "icsf.kuf.role.authData",
            bits(new Bit(0x80, "PTR2AUTH", "icsf.kuf.ptr2auth", "NOP2AUTH", "icsf.kuf.nop2auth"),
                    new Bit(0x40, "MMSAUTH1", "icsf.kuf.dky.mmsauth1", "NOMAUTH1", "icsf.kuf.dky.nomauth1"),
                    new Bit(0x20, "MMSAUTH2", "icsf.kuf.dky.mmsauth2", "NOMAUTH2", "icsf.kuf.dky.nomauth2")));

    private static FieldRule[] tableFor(String keyType) {
        return switch (keyType) {
            case "CIPHER" -> AES_CIPHER;
            case "MAC" -> AES_MAC;
            case "EXPORTER" -> AES_EXPORTER;
            case "IMPORTER" -> AES_IMPORTER;
            case "PINPROT" -> AES_PINPROT;
            case "PINCALC" -> AES_PINCALC;
            case "PINPRW" -> AES_PINPRW;
            case "SECMSG" -> AES_SECMSG;
            default -> null;
        };
    }

    // =====================================================================
    // Key usage
    // =====================================================================

    /**
     * Adds one field per usage byte to {@code section} and returns the keywords in force,
     * in field order, for the summary card.
     */
    static List<String> decodeUsage(int algorithm, String keyType, byte[] usageFields, int usageOffset,
                                    int count, IcsfSection section, ParseResult result) {
        Usage usage = new Usage(usageFields, 0);
        List<String> keywords = new ArrayList<>();
        int available = usageFields.length / 2;

        Set<Integer> expectedCounts = null;
        String table = null;
        FieldRule[] rules = null;
        if (algorithm == 0x01 && "DESUSECV".equals(keyType)) {
            rules = DESUSECV; expectedCounts = Set.of(1); table = "619";
        } else if (algorithm == 0x03 && "MAC".equals(keyType)) {
            rules = HMAC; expectedCounts = Set.of(2); table = "620";
        } else if (algorithm == 0x02) {
            rules = tableFor(keyType);
            switch (keyType) {
                case "CIPHER" -> { expectedCounts = Set.of(2); table = "628"; }
                case "MAC" -> { expectedCounts = Set.of(2, 3); table = "621"; }
                case "EXPORTER", "IMPORTER" -> { expectedCounts = Set.of(4); table = "627"; }
                case "PINPROT" -> {
                    expectedCounts = Set.of(usage.low(3) == 0x01 ? 3 : 4); table = "623";
                }
                case "PINCALC" -> { expectedCounts = Set.of(3); table = "622"; }
                case "PINPRW" -> { expectedCounts = Set.of(3); table = "624"; }
                case "SECMSG" -> { expectedCounts = Set.of(2); table = "626"; }
                case "KDKGENKY" -> { expectedCounts = Set.of(13, 25, 37, 49); table = "631"; }
                case "DKYGENKY" -> {
                    Derivable derivable = DERIVABLE.get(usage.high(1));
                    expectedCounts = derivable == null ? null : derivable.counts();
                    table = "625";
                }
                default -> { }
            }
        }

        if (table != null) {
            section.add(IcsfSection.Field.derived(usageOffset - 1, t("icsf.kuf.table"),
                    t("icsf.kuf.tableReference", table, keyType)));
        }
        if (expectedCounts != null && !expectedCounts.contains(count)) {
            result.warn(DiagnosticCode.USAGE_FIELD_COUNT_UNEXPECTED,
                    t("icsf.warn.kufCount", keyType, count, joinNumbers(expectedCounts), table));
        }

        for (int field = 1; field <= available; field++) {
            FieldRule rule = null;
            Usage context = usage;
            IcsfText relatedTo = IcsfText.EMPTY;
            if (rules != null) {
                rule = field <= rules.length ? rules[field - 1] : null;
            } else if ("KDKGENKY".equals(keyType) && algorithm == 0x02) {
                if (field == 1) {
                    rule = KDKGENKY_TYPE;
                } else {
                    addBlock(section, usageFields, usageOffset, field, available, "icsf.kuf.kdk.block");
                    break;
                }
            } else if ("DKYGENKY".equals(keyType) && algorithm == 0x02) {
                if (field == 1) {
                    rule = DKYGENKY_TYPE;
                } else if (field == 2) {
                    rule = DKYGENKY_CONTROL;
                } else {
                    Derivable derivable = DERIVABLE.get(usage.high(1));
                    if (derivable != null && derivable.keyType() != null) {
                        relatedTo = t("icsf.kuf.relatedTo", field - 2, derivable.keyType());
                    }
                    if (derivable != null && "MAC".equals(derivable.keyType()) && field == 4) {
                        rule = DKYGENKY_DMAC_FIELD4;
                        context = usage.shifted(2);
                    } else if (derivable != null && "KDKGENKY".equals(derivable.keyType())) {
                        if (field == 3) {
                            rule = KDKGENKY_TYPE;
                        } else {
                            addBlock(section, usageFields, usageOffset, field, available, "icsf.kuf.kdk.block");
                            break;
                        }
                    } else if (derivable != null && derivable.keyType() != null) {
                        FieldRule[] related = tableFor(derivable.keyType());
                        if (related != null && field - 2 <= related.length) {
                            rule = related[field - 3];
                            context = usage.shifted(2);
                        }
                    }
                }
            }

            int offset = usageOffset + (field - 1) * 2;
            int high = usageFields[(field - 1) * 2] & 0xFF;
            int low = usageFields[(field - 1) * 2 + 1] & 0xFF;
            // A DKYGENKY's related fields describe the keys it may derive, not uses of its own.
            List<String> collect = relatedTo.isEmpty() ? keywords : new ArrayList<>();
            addByte(section, result, offset, "KUF", field, true, high, role(rule, true, relatedTo),
                    rule == null ? null : rule.high(), context, collect);
            addByte(section, result, offset + 1, "KUF", field, false, low, role(rule, false, relatedTo),
                    rule == null ? null : rule.low(), context, collect);
        }
        return keywords;
    }

    /** Several fields that form one structure the manual describes elsewhere: shown whole, not bit by bit. */
    private static void addBlock(IcsfSection section, byte[] usageFields, int usageOffset, int fromField,
                                 int available, String key) {
        int start = (fromField - 1) * 2;
        section.add(usageOffset + start, available * 2 - start,
                t("icsf.kuf.blockName", fromField, available),
                IcsfHex.hex(usageFields, start, available * 2), t(key));
    }

    // =====================================================================
    // Key management (Table 629)
    // =====================================================================

    private static final FieldRule[] MANAGEMENT = {
            new FieldRule("icsf.kmf.role.exportPermitted",
                    bits(new Bit(0x80, "XPRT-SYM", "icsf.var.export.symmetric", "NOEX-SYM", "icsf.kmf.noSymmetric"),
                            new Bit(0x40, "XPRTUASY", "icsf.var.export.asymUnauth", "NOEXUASY", "icsf.kmf.noAsymUnauth"),
                            new Bit(0x20, "XPRTAASY", "icsf.var.export.asymAuth", "NOEXAASY", "icsf.kmf.noAsymAuth"),
                            new Bit(0x10, "XPRT-RAW", "icsf.var.export.raw", "NOEX-RAW", "icsf.kmf.noRaw"),
                            new Bit(0x08, null, "icsf.var.export.cpacf"),
                            new Bit(0x01, null, "icsf.var.export.compliantTagged")),
                    "icsf.kmf.role.exportProhibited",
                    bits(new Bit(0x80, "NOEX-DES", "icsf.var.export.prohibitDes", "XPRT-DES", "icsf.kmf.desAllowed"),
                            new Bit(0x40, "NOEX-AES", "icsf.var.export.prohibitAes", "XPRT-AES", "icsf.kmf.aesAllowed"),
                            new Bit(0x08, "NOEX-RSA", "icsf.var.export.prohibitRsa", "XPRT-RSA", "icsf.kmf.rsaAllowed"))),
            new FieldRule("icsf.kmf.role.keyParts",
                    (value, usage) -> {
                        Reading reading = new Reading();
                        reading.present.add(new Meaning(null, switch (value >> 6) {
                            case 0b11 -> "icsf.var.completeness.twoOrMoreMissing";
                            case 0b10 -> "icsf.var.completeness.oneMissing";
                            case 0b01 -> "icsf.var.completeness.mayBeCompleted";
                            default -> "icsf.var.completeness.complete";
                        }));
                        reading.reservedBits = value & 0x3F;
                        return reading;
                    },
                    "icsf.kmf.role.history",
                    bits(new Bit(0x10, null, "icsf.var.history.untrustedKek"),
                            new Bit(0x08, null, "icsf.var.history.noAttributes"),
                            new Bit(0x04, null, "icsf.var.history.weakerKey"),
                            new Bit(0x02, null, "icsf.var.history.nonCca"),
                            new Bit(0x01, null, "icsf.var.history.ecb")))};

    /** Adds the bytes of KMF1 and KMF2 (pedigree, KMF3, is an enumeration the caller already reads). */
    static void decodeManagement(byte[] managementFields, int managementOffset, int count,
                                 IcsfSection section, ParseResult result) {
        Usage context = new Usage(managementFields, 0);
        int available = Math.min(Math.min(count, MANAGEMENT.length), managementFields.length / 2);
        for (int field = 1; field <= available; field++) {
            FieldRule rule = MANAGEMENT[field - 1];
            int offset = managementOffset + (field - 1) * 2;
            addByte(section, result, offset, "KMF", field, true, managementFields[(field - 1) * 2] & 0xFF,
                    role(rule, true, IcsfText.EMPTY), rule.high(), context, new ArrayList<>());
            addByte(section, result, offset + 1, "KMF", field, false, managementFields[(field - 1) * 2 + 1] & 0xFF,
                    role(rule, false, IcsfText.EMPTY), rule.low(), context, new ArrayList<>());
        }
    }

    // =====================================================================
    // Rendering one byte
    // =====================================================================

    private static IcsfText role(FieldRule rule, boolean high, IcsfText relatedTo) {
        String key = rule == null ? null : high ? rule.highRole() : rule.lowRole();
        IcsfText role = key == null ? IcsfText.EMPTY : t(key);
        if (relatedTo.isEmpty()) return role;
        return role.isEmpty() ? relatedTo : t("icsf.join.middot", relatedTo, role);
    }

    private static void addByte(IcsfSection section, ParseResult result, int offset, String prefix, int field,
                                boolean high, int value, IcsfText role, ByteRule byteRule, Usage context,
                                List<String> keywords) {
        IcsfText name = t(high ? "icsf.kuf.highByte" : "icsf.kuf.lowByte", prefix, field);
        if (!role.isEmpty()) {
            name = t("icsf.kuf.nameWithRole", name, role);
        }
        IcsfText bitsText = IcsfText.raw(binary(value));
        String hex = String.format(Locale.ROOT, "%02X", value);

        if (byteRule == null) {
            section.add(offset, 1, name, hex,
                    t("icsf.join.lines", t("icsf.kuf.readingBits", bitsText), t("icsf.kuf.noTable")));
            return;
        }

        Reading reading = byteRule.read(value, context);
        List<String> present = new ArrayList<>();
        for (Meaning meaning : reading.present) {
            if (meaning.keyword() == null) continue;
            present.add(meaning.keyword());
            if (!meaning.defaultState()) keywords.add(meaning.keyword());
        }

        IcsfText value0;
        if (!present.isEmpty()) {
            value0 = t("icsf.kuf.reading", bitsText, IcsfText.raw(String.join(" + ", present)));
        } else if (reading.present.isEmpty() && !reading.undefinedValue) {
            value0 = t("icsf.kuf.readingNothingSet", bitsText);
        } else {
            value0 = t("icsf.kuf.readingBits", bitsText);
        }
        List<IcsfText> lines = new ArrayList<>();
        lines.add(value0);
        for (Meaning meaning : reading.present) {
            lines.add(meaning.keyword() == null
                    ? t("icsf.kuf.meaningLine", t(meaning.key()))
                    : t("icsf.kuf.keywordLine", IcsfText.raw(meaning.keyword()), t(meaning.key())));
        }
        if (!reading.absentKeywords.isEmpty()) {
            lines.add(t("icsf.kuf.notSet", IcsfText.raw(String.join(", ", reading.absentKeywords))));
        }
        String place = prefix + field + (high ? " HOB" : " LOB");
        if (reading.undefinedValue) {
            lines.add(t("icsf.kuf.undefinedValue", hex));
            result.warn(prefix.equals("KUF") ? DiagnosticCode.USAGE_FIELD_UNDEFINED_VALUE
                            : DiagnosticCode.MANAGEMENT_FIELD_RESERVED_BITS,
                    t("icsf.warn.fieldUndefinedValue", place, offset, hex));
        }
        if (reading.reservedBits != 0) {
            String reserved = binary(reading.reservedBits);
            lines.add(t("icsf.kuf.reservedSet", IcsfText.raw(reserved)));
            result.warn(prefix.equals("KUF") ? DiagnosticCode.USAGE_FIELD_RESERVED_BITS
                            : DiagnosticCode.MANAGEMENT_FIELD_RESERVED_BITS,
                    t("icsf.warn.fieldReservedBits", place, offset, IcsfText.raw(reserved)));
        }
        for (IcsfText conflict : reading.conflicts) {
            lines.add(t("icsf.kuf.conflictLine", conflict));
            result.warn(DiagnosticCode.USAGE_FIELD_COMBINATION_INVALID,
                    t("icsf.warn.fieldCombination", place, offset, conflict));
        }

        IcsfText joined = lines.get(0);
        for (int index = 1; index < lines.size(); index++) {
            joined = t("icsf.join.lines", joined, lines.get(index));
        }
        section.add(offset, 1, name, hex, joined);
    }

    static String binary(int value) {
        String bits = String.format(Locale.ROOT, "%8s", Integer.toBinaryString(value & 0xFF)).replace(' ', '0');
        return "B'" + bits.substring(0, 4) + " " + bits.substring(4) + "'";
    }

    private static String joinNumbers(Set<Integer> numbers) {
        return numbers.stream().sorted().map(String::valueOf).reduce((a, b) -> a + ", " + b).orElse("");
    }
}
