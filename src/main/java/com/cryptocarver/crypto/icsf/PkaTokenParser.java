package com.cryptocarver.crypto.icsf;

import com.cryptocarver.crypto.icsf.IcsfVocabulary.Algorithm;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.CvState;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.EffectiveStrength;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.Exportability;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.MaterialState;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.MkvpState;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.Scope;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.TvvState;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.WrapMethod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PKA tokens: RSA, ECC and QSA (Tables 637-659).
 *
 * <p>This decoding is checked against the manual and exercised with synthetic
 * tokens, but has not been validated against a real token out of a PKDS. Its
 * fields are reported as provisional, and the batch layer raises a standing
 * finding to say so.</p>
 */
final class PkaTokenParser {

    private PkaTokenParser() { }

    private static IcsfText t(String key, Object... arguments) {
        return IcsfText.of(key, arguments);
    }

    private static final Map<Integer, String> SECTION_NAMES = Map.ofEntries(
            Map.entry(0x02, "icsf.pka.section.rsaMe1024External"),
            Map.entry(0x04, "icsf.pka.section.rsaPublic"),
            Map.entry(0x06, "icsf.pka.section.rsaMe1024Opk"),
            Map.entry(0x08, "icsf.pka.section.rsaCrt4096Opk"),
            Map.entry(0x09, "icsf.pka.section.rsaMe4096External"),
            Map.entry(0x10, "icsf.pka.section.privateName"),
            Map.entry(0x20, "icsf.pka.section.eccPrivate"),
            Map.entry(0x21, "icsf.pka.section.eccPublic"),
            Map.entry(0x23, "icsf.pka.section.eccDerivation"),
            Map.entry(0x30, "icsf.pka.section.rsaMeAeskw"),
            Map.entry(0x31, "icsf.pka.section.rsaCrtAeskw"),
            Map.entry(0x50, "icsf.pka.section.qsaPrivate"),
            Map.entry(0x51, "icsf.pka.section.qsaPublic"));

    /** Private-key usage and translation control (pp. 1610, 1615, 1617, 1619, 1627). */
    private static final Map<Integer, String> KEY_USAGE = Map.of(
            0b11, "icsf.pka.usage.kmOnly",
            0b10, "icsf.pka.usage.keyMgmt",
            0b01, "icsf.pka.usage.undefined",
            0b00, "icsf.pka.usage.sigOnly");

    /** How the private key reached the token: the PKA equivalent of a symmetric pedigree. */
    private static final Map<Integer, String> KEY_SOURCE = Map.of(
            0x00, "icsf.pka.source.none",
            0x21, "icsf.pka.source.externalClear",
            0x22, "icsf.pka.source.externalEncrypted",
            0x23, "icsf.pka.source.regenerated",
            0x24, "icsf.pka.source.random");

    private static final Map<Integer, String> KEY_FORMAT_ME = Map.of(
            0x00, "icsf.pka.format.externalClear",
            0x82, "icsf.pka.format.externalEncrypted",
            0x02, "icsf.pka.format.internalEncrypted");

    private static final Map<Integer, String> KEY_FORMAT_CRT = Map.of(
            0x40, "icsf.pka.format.externalClear",
            0x42, "icsf.pka.format.externalEncrypted",
            0x08, "icsf.pka.format.internalEncrypted");

    /** Signature format restrictions are standard names, not words. */
    private static final Map<Integer, String> FORMAT_RESTRICTION = Map.of(
            0x01, "ISO-9796", 0x02, "PKCS-1.0", 0x03, "PKCS-1.1",
            0x04, "PKCS-PSS", 0x05, "X9.31", 0x06, "ZERO-PAD");

    /** Usage bits of the X'30'/X'31' sections with a modern AD version (p. 1614). */
    private record UsageBit(int relativeOffset, int bit, String mnemonic) { }

    private static final List<UsageBit> USAGE_BITS = List.of(
            new UsageBit(48, 0, "U-DIGSIG"), new UsageBit(48, 1, "U-NONRPD"),
            new UsageBit(48, 2, "U-KEYENC"), new UsageBit(48, 3, "U-DATENC"),
            new UsageBit(48, 4, "U-KEYAGR"), new UsageBit(48, 5, "U-KCRTSN"),
            new UsageBit(48, 6, "U-CRLSN"), new UsageBit(48, 7, "U-ENCONL"),
            new UsageBit(49, 0, "U-DECONL"));

    private static final Map<Integer, String> ECC_WRAP = Map.of(
            0x00, "icsf.pka.eccWrap.clear", 0x01, "icsf.pka.eccWrap.aeskw",
            0x02, "icsf.pka.eccWrap.cbcOther");

    private static final Map<Integer, String> ECC_CURVE_TYPE = Map.of(
            0x00, "icsf.pka.curveType.prime", 0x01, "icsf.pka.curveType.brainpool",
            0x02, "icsf.pka.curveType.edwards", 0x03, "icsf.pka.curveType.koblitz");

    /** (curve type, length of p in bits) -> name. Tables 648-651, pp. 1625-1626. */
    private static final Map<Integer, String> ECC_CURVE = buildCurves();

    private static Map<Integer, String> buildCurves() {
        Map<Integer, String> map = new LinkedHashMap<>();
        map.put(curve(0x00, 0x00C0), "Prime P-192 (secp192r1)");
        map.put(curve(0x00, 0x00E0), "Prime P-224 (secp224r1)");
        map.put(curve(0x00, 0x0100), "Prime P-256 (secp256r1)");
        map.put(curve(0x00, 0x0180), "Prime P-384 (secp384r1)");
        map.put(curve(0x00, 0x0209), "Prime P-521 (secp521r1)");
        map.put(curve(0x01, 0x00A0), "brainpoolP160r1");
        map.put(curve(0x01, 0x00C0), "brainpoolP192r1");
        map.put(curve(0x01, 0x00E0), "brainpoolP224r1");
        map.put(curve(0x01, 0x0100), "brainpoolP256r1");
        map.put(curve(0x01, 0x0140), "brainpoolP320r1");
        map.put(curve(0x01, 0x0180), "brainpoolP384r1");
        map.put(curve(0x01, 0x0200), "brainpoolP512r1");
        map.put(curve(0x02, 0x00FF), "Edwards 25519 (Ed25519)");
        map.put(curve(0x02, 0x01C0), "Edwards 448 (Ed448)");
        map.put(curve(0x03, 0x0100), "Koblitz P-256 (secp256k1)");
        return Map.copyOf(map);
    }

    private static int curve(int type, int bits) {
        return (type << 16) | bits;
    }

    /** What one PKA section contributes to the token's summary card. */
    private static final class SectionInfo {
        String algorithm = "";
        boolean hasPrivateKey;
        Boolean encrypted;
        IcsfText keyBits = IcsfText.EMPTY;
        String keyBitsCode = "";
        Integer modulusBits;
        IcsfText usage = IcsfText.EMPTY;
        Boolean translatable;
        Boolean aesExport;
        Boolean cpacf;
        Boolean compliant;
        IcsfText source = IcsfText.EMPTY;
        String name = "";
        Boolean kvpAllZero;
    }

    static ParseResult parse(byte[] token, Origin origin) {
        int tokenId = IcsfHex.u8(token, 0);

        if (tokenId == 0x00) {
            ParseResult nullResult = ParseResult.ok(TokenFamily.PKA_NULL, token.length);
            nullResult.section(new IcsfSection(t("icsf.section.nullToken"))
                    .add(0, 1, t("icsf.field.tokenId"), IcsfHex.hex(token, 0, 1),
                            t("icsf.value.tokenId.null")));
            return SymmetricVariableTokenParser.nullSummary(nullResult, t("icsf.family.pkaNull"));
        }

        boolean internal = tokenId == 0x1F;
        ParseResult result = ParseResult.ok(TokenFamily.PKA, token.length);

        int declaredLength = IcsfHex.u16(token, 2);
        if (declaredLength != token.length) {
            result.warn(DiagnosticCode.DECLARED_LENGTH_MISMATCH,
                    t("icsf.warn.declaredLength", declaredLength, token.length));
        }

        IcsfSection header = new IcsfSection(t("icsf.section.pkaHeader"));
        header.add(0, 1, t("icsf.field.tokenId"), IcsfHex.hex(token, 0, 1),
                t(internal ? "icsf.value.tokenId.pkaInternal" : "icsf.value.tokenId.pkaExternal"));
        header.add(1, 1, t("icsf.field.tokenVersion"), IcsfHex.hex(token, 1, 2));
        header.add(2, 2, t("icsf.field.tokenLength"), IcsfHex.hex(token, 2, 4),
                t("icsf.value.bytes", declaredLength));
        header.add(4, 4, t("icsf.field.reserved"), IcsfHex.hex(token, 4, 8),
                t("icsf.value.binaryZero"));

        // --- walk the concatenated sections --------------------------------
        List<IcsfText> found = new ArrayList<>();
        List<Integer> order = new ArrayList<>();
        List<SectionInfo> infos = new ArrayList<>();
        List<IcsfSection> sections = new ArrayList<>();
        int offset = 8;
        int guard = 0;
        while (offset + 4 <= token.length && guard < 32) {
            guard++;
            int sectionId = IcsfHex.u8(token, offset);
            int sectionLength = IcsfHex.u16(token, offset + 2);
            if (sectionLength == 0 || offset + sectionLength > token.length) {
                header.add(offset, 1, t("icsf.field.pkaSection", hex2(sectionId)),
                        IcsfHex.hex(token, offset, offset + 1),
                        t("icsf.value.pkaSectionInvalidLength", sectionLength));
                result.warn(DiagnosticCode.PKA_SECTION_LENGTH_INVALID,
                        t("icsf.warn.pkaSectionLength", hex2(sectionId), offset, sectionLength,
                                token.length));
                break;
            }
            IcsfText description = SECTION_NAMES.containsKey(sectionId)
                    ? t(SECTION_NAMES.get(sectionId)) : t("icsf.pka.section.unknown");
            found.add(t("icsf.pka.sectionEntry", hex2(sectionId), description));
            order.add(sectionId);

            IcsfSection section = new IcsfSection(
                    t("icsf.section.pkaSection", hex2(sectionId), offset, description));
            section.add(offset, 1, t("icsf.field.pkaSectionId"),
                    IcsfHex.hex(token, offset, offset + 1), description);
            section.add(offset + 1, 1, t("icsf.field.pkaSectionVersion"),
                    IcsfHex.hex(token, offset + 1, offset + 2));
            section.add(offset + 2, 2, t("icsf.field.pkaSectionLength"),
                    IcsfHex.hex(token, offset + 2, offset + 4), t("icsf.value.bytes", sectionLength));
            if (SECTION_NAMES.containsKey(sectionId)) {
                infos.add(decodeSection(token, offset, sectionLength, section, internal, result));
            } else {
                result.warn(DiagnosticCode.PKA_SECTION_UNDOCUMENTED,
                        t("icsf.warn.pkaSectionUndocumented", hex2(sectionId), offset));
            }
            sections.add(section);
            offset += sectionLength;
        }
        if (offset < token.length) {
            result.warn(DiagnosticCode.PKA_TRAILING_BYTES,
                    t("icsf.warn.pkaTrailing", token.length - offset, offset));
        }

        result.section(header);
        for (IcsfSection section : sections) result.section(section);

        // --- aggregate what the sections contributed -----------------------
        Set<String> algorithms = new LinkedHashSet<>();
        for (SectionInfo info : infos) {
            if (!info.algorithm.isEmpty()) algorithms.add(info.algorithm);
        }
        String algorithm = algorithms.isEmpty() ? "" : algorithms.iterator().next();
        if (algorithms.size() > 1) {
            result.warn(DiagnosticCode.PKA_MIXED_ALGORITHMS,
                    t("icsf.warn.pkaMixedAlgorithms", String.join(", ", algorithms)));
        }

        SectionInfo priv = null;
        IcsfText keyBits = IcsfText.EMPTY;
        String keyBitsCode = "";
        String name = "";
        for (SectionInfo info : infos) {
            if (priv == null && info.hasPrivateKey) priv = info;
            if (keyBits.isEmpty() && !info.keyBits.isEmpty()) {
                keyBits = info.keyBits;
                keyBitsCode = info.keyBitsCode;
            }
            if (name.isEmpty() && !info.name.isEmpty()) name = info.name;
        }

        checkSectionOrder(result, order);

        Set<Integer> modulusSizes = new LinkedHashSet<>();
        for (SectionInfo info : infos) {
            if (info.modulusBits != null) modulusSizes.add(info.modulusBits);
        }
        if (modulusSizes.size() > 1) {
            List<String> sorted = modulusSizes.stream().sorted().map(String::valueOf).toList();
            result.warn(DiagnosticCode.PKA_MODULUS_MISMATCH,
                    t("icsf.warn.pkaModulusMismatch", String.join(" / ", sorted)));
        }

        boolean kvpAllZero = priv != null && Boolean.TRUE.equals(priv.kvpAllZero);
        if (priv != null && kvpAllZero && internal && Boolean.TRUE.equals(priv.encrypted)) {
            result.warn(DiagnosticCode.PKA_INTERNAL_ENCRYPTED_WITHOUT_KVP,
                    t("icsf.warn.pkaInternalNoKvp"));
        }

        // --- summary card ----------------------------------------------------
        Algorithm algorithmValue = switch (algorithm) {
            case "RSA" -> Algorithm.RSA;
            case "ECC" -> Algorithm.ECC;
            case "QSA" -> Algorithm.QSA;
            default -> Algorithm.UNKNOWN;
        };

        result.summary(SummaryKey.MATURITY, "IN_TESTING", t("icsf.pka.inTesting"))
                .summary(SummaryKey.FAMILY, TokenFamily.PKA,
                        t("icsf.family.pka", algorithm.isEmpty() ? "?" : algorithm))
                .summary(SummaryKey.SCOPE, internal ? Scope.INTERNAL : Scope.EXTERNAL,
                        t(internal ? "icsf.scope.pkaInternal" : "icsf.scope.pkaExternal"))
                .summary(SummaryKey.ALGORITHM, algorithmValue)
                .summary(SummaryKey.KEY_TYPE, priv != null ? "PRIVATE_AND_PUBLIC" : "PUBLIC_ONLY",
                        t(priv != null ? "icsf.keyType.privateAndPublic" : "icsf.keyType.publicOnly"))
                .summary(SummaryKey.KEY_LENGTH, keyBitsCode.isEmpty() ? "UNKNOWN" : keyBitsCode, keyBits)
                .summary(SummaryKey.EFFECTIVE_STRENGTH, EffectiveStrength.NOT_APPLICABLE)
                .summary(SummaryKey.WRAPPING, WrapMethod.NOT_APPLICABLE)
                .summary(SummaryKey.CONTROL_VECTOR, CvState.NOT_APPLICABLE, t("icsf.cvState.pka"))
                .summary(SummaryKey.TVV, TvvState.NOT_APPLICABLE, t("icsf.tvv.pkaNone"))
                .summary(SummaryKey.MKVP, priv == null ? MkvpState.NOT_APPLICABLE
                        : (kvpAllZero ? MkvpState.ABSENT : MkvpState.PRESENT))
                .summary(SummaryKey.PRIVATE_KEY_PRESENT, priv != null ? "YES" : "NO",
                        t(priv != null ? "icsf.pka.carriesPrivate" : "icsf.pka.publicOnly"))
                .summary(SummaryKey.SECTIONS, found.isEmpty() ? "NONE" : "PRESENT",
                        found.isEmpty() ? t("icsf.pka.noSections") : joinSections(found));

        if (priv != null) {
            if (priv.encrypted != null) {
                result.summary(SummaryKey.MATERIAL_STATE,
                        priv.encrypted ? MaterialState.ENCRYPTED : MaterialState.CLEAR,
                        priv.encrypted
                                ? t(internal ? "icsf.material.pkaUnderMasterKey" : "icsf.material.pkaUnderKek")
                                : t("icsf.material.pkaClear"));
            } else {
                result.summary(SummaryKey.MATERIAL_STATE, MaterialState.NOT_DETERMINABLE);
            }
            if (!priv.usage.isEmpty()) result.summary(SummaryKey.ALLOWED_USES, "DECODED", priv.usage);
            if (!priv.source.isEmpty()) {
                result.summary(SummaryKey.PRIVATE_KEY_SOURCE, "DECLARED", priv.source);
            }
            if (priv.compliant != null) {
                result.compliantTagged(priv.compliant);
                result.summary(SummaryKey.COMPLIANT_TAGGED, priv.compliant ? "YES" : "NO");
            }
            result.summary(SummaryKey.EXPORTABILITY, exportVerdict(priv), exportSummary(priv, internal));
        } else {
            result.summary(SummaryKey.MATERIAL_STATE, MaterialState.NO_KEY, t("icsf.material.pkaPublicOnly"))
                    .summary(SummaryKey.EXPORTABILITY, Exportability.NOT_APPLICABLE,
                            t("icsf.export.pkaPublicOnly"));
        }
        if (!name.isEmpty()) result.summary(SummaryKey.KEY_NAME, "PRESENT", IcsfText.raw(name));

        IcsfProvenance.applyPka(result, origin, internal, priv != null, kvpAllZero);
        return result;
    }

    /**
     * "The token is composed of concatenated sections that must occur in the
     * prescribed order" (p. 1605): the private section comes before the public one.
     */
    private static void checkSectionOrder(ParseResult result, List<Integer> order) {
        int[] rsaPrivate = {0x02, 0x06, 0x08, 0x09, 0x30, 0x31};
        int publicIndex = order.indexOf(0x04);
        if (publicIndex >= 0) {
            int firstPrivate = Integer.MAX_VALUE;
            for (int id : rsaPrivate) {
                int index = order.indexOf(id);
                if (index >= 0) firstPrivate = Math.min(firstPrivate, index);
            }
            if (firstPrivate != Integer.MAX_VALUE && publicIndex < firstPrivate) {
                result.warn(DiagnosticCode.PKA_SECTION_ORDER, t("icsf.warn.pkaOrderRsa"));
            }
        }
        int eccPublic = order.indexOf(0x21);
        int eccPrivate = order.indexOf(0x20);
        if (eccPublic >= 0 && eccPrivate >= 0 && eccPublic < eccPrivate) {
            result.warn(DiagnosticCode.PKA_SECTION_ORDER, t("icsf.warn.pkaOrderEcc"));
        }
    }

    private static Exportability exportVerdict(SectionInfo priv) {
        if (priv.translatable == null) return Exportability.NOT_DETERMINABLE;
        return priv.translatable ? Exportability.YES : Exportability.NO;
    }

    /**
     * The analogue of CV bit 17 is the translation control bit (XLATE-OK / NO-XLATE),
     * present in every private section; ECC sections add AES1ECOK/NOAES1EC and
     * XPRTCPAC/NOEXCPAC (p. 1627).
     */
    private static IcsfText exportSummary(SectionInfo priv, boolean internal) {
        IcsfText summary = t(internal ? "icsf.export.pkaInternal" : "icsf.export.pkaExternal");
        summary = t("icsf.join.middot", summary,
                priv.translatable == null ? t("icsf.export.pkaNoXlateControl")
                        : t(priv.translatable ? "icsf.export.pkaXlateOk" : "icsf.export.pkaNoXlate"));
        if (priv.aesExport != null) {
            summary = t("icsf.join.middot", summary,
                    t(priv.aesExport ? "icsf.export.pkaAes1ecok" : "icsf.export.pkaNoaes1ec"));
        }
        if (priv.cpacf != null) {
            summary = t("icsf.join.middot", summary,
                    t(priv.cpacf ? "icsf.export.pkaXprtcpac" : "icsf.export.pkaNoexcpac"));
        }
        return summary;
    }

    // =====================================================================
    // Section decoding
    // =====================================================================
    private static SectionInfo decodeSection(byte[] token, int offset, int sectionLength,
                                             IcsfSection section, boolean internal, ParseResult result) {
        int sectionId = IcsfHex.u8(token, offset);
        SectionInfo info = new SectionInfo();
        byte[] body = IcsfHex.slice(token, offset, offset + sectionLength);

        switch (sectionId) {
            case 0x04 -> decodeRsaPublic(body, offset, section, info);
            case 0x10 -> decodePrivateName(body, offset, section, info);
            case 0x02, 0x06, 0x08, 0x09 -> decodeLegacyRsaPrivate(body, offset, section, info, sectionId);
            case 0x30, 0x31 -> decodeModernRsaPrivate(body, offset, section, info,
                    sectionId == 0x31, internal, result);
            case 0x20 -> decodeEccPrivate(body, offset, section, info);
            case 0x21 -> decodeEccPublic(body, offset, section, info);
            case 0x23 -> decodeEccDerivation(body, offset, section, info);
            default -> { }
        }
        return info;
    }

    /** X'04' RSA public key (Table 646, p. 1624). */
    private static void decodeRsaPublic(byte[] body, int offset, IcsfSection section, SectionInfo info) {
        info.algorithm = "RSA";
        int exponentLength = u16(body, 6);
        int modulusBits = u16(body, 8);
        int modulusLength = u16(body, 10);
        field(section, body, offset, 6, 2, t("icsf.pka.field.exponentLength"),
                IcsfText.raw(String.valueOf(exponentLength)));
        field(section, body, offset, 8, 2, t("icsf.pka.field.modulusBits"),
                t("icsf.value.bits", modulusBits));
        field(section, body, offset, 10, 2, t("icsf.pka.field.modulusBytes"),
                IcsfText.raw(String.valueOf(modulusLength)));
        if (exponentLength > 0 && 12 + exponentLength <= body.length) {
            java.math.BigInteger exponent =
                    new java.math.BigInteger(1, IcsfHex.slice(body, 12, 12 + exponentLength));
            IcsfText note = exponent.equals(java.math.BigInteger.valueOf(3))
                    ? t("icsf.pka.exponent.three")
                    : exponent.equals(java.math.BigInteger.valueOf(65537))
                            ? t("icsf.pka.exponent.usual") : IcsfText.EMPTY;
            field(section, body, offset, 12, exponentLength, t("icsf.pka.field.exponent"),
                    note.isEmpty() ? IcsfText.raw(exponent.toString())
                            : t("icsf.pka.exponent.withNote", exponent.toString(), note));
        }
        if (modulusLength > 0 && 12 + exponentLength + modulusLength <= body.length) {
            // The modulus is PUBLIC material: shown in full so it can be copied.
            field(section, body, offset, 12 + exponentLength, modulusLength,
                    t("icsf.pka.field.modulus", modulusLength * 8), t("icsf.pka.publicValue"));
        }
        if (modulusBits > 0) {
            info.keyBits = t("icsf.pka.rsaBits", modulusBits);
            info.keyBitsCode = "RSA " + modulusBits + " bits";
            info.modulusBits = modulusBits;
        }
        if (modulusLength == 0) {
            section.add(t("icsf.pka.flag.modulusAbsent"), true, t("icsf.pka.flag.modulusAbsentHelp"));
        }
    }

    /** X'10' private key name (Table 647, p. 1625). */
    private static void decodePrivateName(byte[] body, int offset, IcsfSection section, SectionInfo info) {
        byte[] raw = IcsfHex.slice(body, 4, 68);
        info.name = SymmetricFixedTokenParser.asciiOrHex(raw);
        field(section, body, offset, 4, Math.min(64, Math.max(0, body.length - 4)),
                t("icsf.pka.field.privateName"), IcsfText.raw(info.name));
    }

    /** X'02' / X'06' / X'08' / X'09': the legacy sections (pp. 1609-1619). */
    private static void decodeLegacyRsaPrivate(byte[] body, int offset, IcsfSection section,
                                               SectionInfo info, int sectionId) {
        info.algorithm = "RSA";
        info.hasPrivateKey = true;
        boolean privateHashZero = IcsfHex.isAllZero(body, 4, 24);
        field(section, body, offset, 4, 20, t("icsf.pka.field.privateSha1"),
                t(privateHashZero ? "icsf.pka.value.hashZero" : "icsf.pka.value.hashPresent"));
        Integer format = u8(body, 28);
        Map<Integer, String> table = sectionId == 0x08 ? KEY_FORMAT_CRT : KEY_FORMAT_ME;
        if (format != null) {
            field(section, body, offset, 28, 1, t("icsf.pka.field.keyFormatFlag"),
                    table.containsKey(format) ? t(table.get(format)) : t("icsf.value.undocumented"));
            info.encrypted = format != 0x00 && format != 0x40;
        }
        boolean optionalHashZero = IcsfHex.isAllZero(body, 30, 50);
        field(section, body, offset, 30, 20, t("icsf.pka.field.optionalSha1"),
                t(optionalHashZero ? "icsf.pka.value.optionalHashZero" : "icsf.pka.value.hashPresent"));
        Integer usage = u8(body, 50);
        if (usage != null) {
            applyUsageByte(section, info, usage);
            field(section, body, offset, 50, 1, t("icsf.pka.field.usageAndTranslation"), info.usage);
        }
        if (sectionId == 0x09) {
            int modulusLength = u16(body, 118);
            if (modulusLength > 0) {
                info.keyBits = t("icsf.pka.rsaBitsWithBytes", modulusLength * 8, modulusLength);
                info.keyBitsCode = "RSA " + modulusLength * 8 + " bits";
                info.modulusBits = modulusLength * 8;
                field(section, body, offset, 118, 2, t("icsf.pka.field.modulusBytes"),
                        IcsfText.raw(String.valueOf(modulusLength)));
            }
        }
    }

    /** X'30' ME with AES OPK and X'31' CRT with AES OPK. Tables 642 (p. 1612) and 645 (p. 1620). */
    private static void decodeModernRsaPrivate(byte[] body, int offset, IcsfSection section,
                                               SectionInfo info, boolean crt, boolean internal,
                                               ParseResult result) {
        info.algorithm = "RSA";
        info.hasPrivateKey = true;
        Integer adVersion = u8(body, 10);
        boolean modern = adVersion != null && (adVersion == 0x04 || adVersion == 0x05);
        field(section, body, offset, 10, 1, t("icsf.pka.field.adVersion"),
                t(modern ? "icsf.pka.value.adModern" : "icsf.pka.value.adLegacy"));

        Integer format = u8(body, 11);
        Map<Integer, String> table = crt ? KEY_FORMAT_CRT : KEY_FORMAT_ME;
        if (format != null) {
            field(section, body, offset, 11, 1, t("icsf.pka.field.keyFormatFlag"),
                    table.containsKey(format) ? t(table.get(format)) : t("icsf.value.undocumented"));
            info.encrypted = format != 0x00 && format != 0x40;
        }

        Integer source = u8(body, 12);
        if (source != null) {
            if (internal) {
                info.source = KEY_SOURCE.containsKey(source) ? t(KEY_SOURCE.get(source))
                        : t("icsf.value.undocumentedValue", hex2(source));
                field(section, body, offset, 12, 1, t("icsf.pka.field.keySource"), info.source);
            } else {
                // Tables 642 and 645: "External token: Reserved, binary zero".
                field(section, body, offset, 12, 1, t("icsf.pka.field.keySource"),
                        t("icsf.pka.value.reservedExternal"));
                if (source != 0x00) {
                    section.add(t("icsf.pka.flag.keySourceInExternal"), true,
                            t("icsf.pka.flag.keySourceInExternalHelp"));
                    result.warn(DiagnosticCode.PKA_KEY_SOURCE_IN_EXTERNAL,
                            t("icsf.warn.pkaKeySourceExternal"));
                }
            }
        }

        if (modern) {
            int compliance = u8(body, 13) == null ? 0 : u8(body, 13);
            info.compliant = (compliance & 0x80) != 0;
            info.translatable = (compliance & 0x02) != 0;
            field(section, body, offset, 13, 1, t("icsf.pka.field.complianceExport"),
                    t("icsf.join.middot",
                            t(info.compliant ? "icsf.pka.value.compliantTagged"
                                    : "icsf.pka.value.notCompliantTagged"),
                            t(info.translatable ? "icsf.export.pkaXlateOk" : "icsf.export.pkaNoXlate")));
            section.add(t("icsf.pka.flag.compliantTagged"), info.compliant,
                    t("icsf.pka.flag.compliantTaggedHelp"));
            section.add(t("icsf.pka.flag.xlateOk"), info.translatable, t("icsf.pka.flag.xlateOkHelp"));
            List<String> active = new ArrayList<>();
            for (UsageBit usageBit : USAGE_BITS) {
                Integer value = u8(body, usageBit.relativeOffset());
                if (value == null) continue;
                boolean on = (value & (0x80 >> usageBit.bit())) != 0;
                section.add(t("icsf.pka.flag.use", usageBit.mnemonic()), on);
                if (on) active.add(usageBit.mnemonic());
            }
            info.usage = active.isEmpty() ? t("icsf.pka.noUsageBits")
                    : IcsfText.raw(String.join(", ", active));
            field(section, body, offset, 48, 2, t("icsf.pka.field.usageBits"), info.usage);
        } else {
            Integer usage = u8(body, 50);
            if (usage != null) {
                applyUsageByte(section, info, usage);
                field(section, body, offset, 50, 1, t("icsf.pka.field.usageAndTranslation"), info.usage);
            }
        }

        Integer hashType = u8(body, 14);
        if (hashType != null) {
            field(section, body, offset, 14, 1, t("icsf.pka.field.hashType"),
                    hashType == 0x00 ? t("icsf.pka.value.noHash")
                            : hashType == 0x02 ? IcsfText.raw("SHA-256") : t("icsf.value.undocumented"));
        }
        Integer restriction = u8(body, 51);
        if (restriction != null) {
            field(section, body, offset, 51, 1, t("icsf.pka.field.formatRestriction"),
                    restriction == 0x00 ? t("icsf.pka.value.noRestriction")
                            : FORMAT_RESTRICTION.containsKey(restriction)
                                    ? t("icsf.pka.value.onlyFormat", FORMAT_RESTRICTION.get(restriction))
                                    : t("icsf.value.undocumented"));
        }

        int modulusLength = crt ? u16(body, 62) : u16(body, 52);
        int kvpOffset = crt ? 116 : 104;
        if (modulusLength > 0) {
            info.keyBits = t("icsf.pka.rsaBitsWithBytes", modulusLength * 8, modulusLength);
            info.keyBitsCode = "RSA " + modulusLength * 8 + " bits";
            info.modulusBits = modulusLength * 8;
            field(section, body, offset, crt ? 62 : 52, 2, t("icsf.pka.field.modulusBytes"),
                    IcsfText.raw(String.valueOf(modulusLength)));
        }
        byte[] kvp = IcsfHex.slice(body, kvpOffset, kvpOffset + 16);
        if (kvp.length == 16) {
            info.kvpAllZero = IcsfHex.isAllZero(kvp, 0, 16);
            field(section, body, offset, kvpOffset, 16, t("icsf.field.verificationPattern"),
                    t(info.kvpAllZero ? "icsf.pka.value.kvpZero" : "icsf.pka.value.kvpPresent"));
        }
        int modulusOffset = crt ? 134 : 122;
        if (modulusLength > 0 && modulusOffset + modulusLength <= body.length) {
            field(section, body, offset, modulusOffset, modulusLength,
                    t("icsf.pka.field.modulus", modulusLength * 8), t("icsf.pka.publicValue"));
        }
    }

    /** X'20' ECC private key (Table 652, p. 1626). */
    private static void decodeEccPrivate(byte[] body, int offset, IcsfSection section, SectionInfo info) {
        info.algorithm = "ECC";
        info.hasPrivateKey = true;
        Integer wrap = u8(body, 4);
        Integer wrapHash = u8(body, 5);
        field(section, body, offset, 4, 1, t("icsf.pka.field.sectionWrap"),
                wrap != null && ECC_WRAP.containsKey(wrap) ? t(ECC_WRAP.get(wrap))
                        : t("icsf.value.undocumented"));
        field(section, body, offset, 5, 1, t("icsf.pka.field.wrapHash"),
                wrapHash != null && wrapHash == 0x01 ? IcsfText.raw("SHA-224")
                        : wrapHash != null && wrapHash == 0x02 ? IcsfText.raw("SHA-256")
                                : t("icsf.value.undocumentedOrNotApplicable"));
        info.encrypted = wrap != null && wrap != 0x00;

        Integer usage = u8(body, 8);
        if (usage != null) {
            info.usage = t(KEY_USAGE.get((usage >> 6) & 0b11));
            info.aesExport = (usage & 0x04) != 0;
            info.translatable = (usage & 0x02) != 0;
            info.cpacf = (usage & 0x01) != 0;
            field(section, body, offset, 8, 1, t("icsf.pka.field.usageAndExport"), info.usage);
            section.add(t("icsf.pka.flag.aes1ecok"), info.aesExport, t("icsf.pka.flag.aes1ecokHelp"));
            section.add(t("icsf.pka.flag.xlateOk"), info.translatable, t("icsf.pka.flag.xlateOkHelp"));
            section.add(t("icsf.pka.flag.xprtcpac"), info.cpacf, t("icsf.pka.flag.xprtcpacHelp"));
        }

        Integer curveType = u8(body, 9);
        int primeBits = u16(body, 12);
        String curveName = curveType == null ? null : ECC_CURVE.get(curve(curveType, primeBits));
        field(section, body, offset, 9, 1, t("icsf.pka.field.curveType"),
                curveType != null && ECC_CURVE_TYPE.containsKey(curveType)
                        ? t(ECC_CURVE_TYPE.get(curveType)) : t("icsf.value.undocumented"));
        field(section, body, offset, 12, 2, t("icsf.pka.field.primeBits"),
                curveName != null ? IcsfText.raw(curveName)
                        : t("icsf.pka.value.undocumentedCurve", primeBits));
        info.keyBits = curveName != null ? IcsfText.raw(curveName) : t("icsf.pka.eccBits", primeBits);
        info.keyBitsCode = curveName != null ? curveName : "ECC " + primeBits + " bits";

        Integer format = u8(body, 10);
        if (format != null) {
            field(section, body, offset, 10, 1, t("icsf.pka.field.keyFormatFlag"),
                    KEY_FORMAT_CRT.containsKey(format) ? t(KEY_FORMAT_CRT.get(format))
                            : t("icsf.value.undocumented"));
        }
        Integer sectionVersion = u8(body, 1);
        Integer source = u8(body, 11);
        if (sectionVersion != null && sectionVersion == 0x01 && source != null) {
            info.source = KEY_SOURCE.containsKey(source) ? t(KEY_SOURCE.get(source))
                    : t("icsf.value.undocumentedValue", hex2(source));
            field(section, body, offset, 11, 1, t("icsf.pka.field.pedigreeSource"), info.source);
        }
        byte[] kvp = IcsfHex.slice(body, 16, 24);
        if (kvp.length == 8) {
            info.kvpAllZero = IcsfHex.isAllZero(kvp, 0, 8);
            field(section, body, offset, 16, 8, t("icsf.field.verificationPattern"),
                    t(info.kvpAllZero ? "icsf.pka.value.kvpZero" : "icsf.pka.value.kvpPresentShort"));
        }
        Integer nameLength = u8(body, 77);
        if (nameLength != null && nameLength > 0) {
            byte[] raw = IcsfHex.slice(body, 92, 92 + nameLength);
            info.name = SymmetricFixedTokenParser.asciiOrHex(raw);
            field(section, body, offset, 92, nameLength, t("icsf.pka.field.privateName"),
                    IcsfText.raw(info.name));
        }
    }

    /** X'21' ECC public key (Table 653, p. 1631). */
    private static void decodeEccPublic(byte[] body, int offset, IcsfSection section, SectionInfo info) {
        info.algorithm = "ECC";
        Integer curveType = u8(body, 8);
        int primeBits = u16(body, 10);
        int publicLength = u16(body, 12);
        String curveName = curveType == null ? null : ECC_CURVE.get(curve(curveType, primeBits));
        field(section, body, offset, 8, 1, t("icsf.pka.field.curveType"),
                curveType != null && ECC_CURVE_TYPE.containsKey(curveType)
                        ? t(ECC_CURVE_TYPE.get(curveType)) : t("icsf.value.undocumented"));
        field(section, body, offset, 10, 2, t("icsf.pka.field.primeBits"),
                curveName != null ? IcsfText.raw(curveName)
                        : t("icsf.pka.value.undocumentedCurve", primeBits));
        field(section, body, offset, 12, 2, t("icsf.pka.field.publicKeyLength"),
                IcsfText.raw(String.valueOf(publicLength)));
        info.keyBits = curveName != null ? IcsfText.raw(curveName) : t("icsf.pka.eccBits", primeBits);
        info.keyBitsCode = curveName != null ? curveName : "ECC " + primeBits + " bits";
        if (publicLength > 0 && 14 + publicLength <= body.length) {
            int prefix = IcsfHex.u8(body, 14);
            field(section, body, offset, 14, 1, t("icsf.pka.field.qFirstByte"),
                    prefix == 0x04 ? t("icsf.pka.value.uncompressedPoint")
                            : t("icsf.pka.value.edwardsPoint", hex2(prefix)));
            field(section, body, offset, 14, publicLength, t("icsf.pka.field.publicKeyQ"),
                    t("icsf.pka.publicValue"));
        }
    }

    /** X'23' ECC key derivation (Table 655, p. 1633). */
    private static void decodeEccDerivation(byte[] body, int offset, IcsfSection section, SectionInfo info) {
        info.algorithm = "ECC";
        Integer algorithm = u8(body, 4);
        Integer keyType = u8(body, 5);
        int bits = u16(body, 6);
        Map<Integer, String> derivedTypes = Map.of(
                0x01, "DATA", 0x02, "EXPORTER", 0x03, "IMPORTER", 0x04, "CIPHER",
                0x05, "DECIPHER", 0x06, "ENCIPHER", 0x07, "CIPHERXI",
                0x08, "CIPHERXL", 0x09, "CIPHERXO");
        field(section, body, offset, 4, 1, t("icsf.pka.field.derivedAlgorithm"),
                algorithm == null ? t("icsf.value.undocumented")
                        : algorithm == 0x01 ? IcsfText.raw("DES")
                                : algorithm == 0x02 ? IcsfText.raw("AES") : t("icsf.value.undocumented"));
        field(section, body, offset, 5, 1, t("icsf.pka.field.derivedType"),
                keyType != null && derivedTypes.containsKey(keyType)
                        ? IcsfText.raw(derivedTypes.get(keyType)) : t("icsf.value.undocumented"));
        field(section, body, offset, 6, 2, t("icsf.pka.field.derivedBits"),
                IcsfText.raw(String.valueOf(bits)));
    }

    // --- helpers ---------------------------------------------------------
    private static void applyUsageByte(IcsfSection section, SectionInfo info, int value) {
        info.usage = t(KEY_USAGE.get((value >> 6) & 0b11));
        info.translatable = (value & 0x02) != 0;
        section.add(t("icsf.pka.flag.xlateOk"), info.translatable, t("icsf.pka.flag.xlateOkHelp"));
    }

    private static void field(IcsfSection section, byte[] body, int sectionOffset,
                              int relative, int length, IcsfText name, IcsfText value) {
        section.add(sectionOffset + relative, length, name,
                IcsfHex.hex(body, relative, relative + length), value);
    }

    /** Joins the section descriptions, each still translatable until the report is rendered. */
    private static IcsfText joinSections(List<IcsfText> sections) {
        IcsfText joined = sections.get(0);
        for (int index = 1; index < sections.size(); index++) {
            joined = t("icsf.join.semicolon", joined, sections.get(index));
        }
        return joined;
    }

    private static String hex2(int value) {
        return String.format(java.util.Locale.ROOT, "%02X", value);
    }

    private static Integer u8(byte[] body, int index) {
        return index >= 0 && index < body.length ? body[index] & 0xFF : null;
    }

    private static int u16(byte[] body, int index) {
        return IcsfHex.u16(body, index);
    }
}
