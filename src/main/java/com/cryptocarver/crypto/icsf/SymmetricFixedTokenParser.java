package com.cryptocarver.crypto.icsf;

import com.cryptocarver.crypto.icsf.IcsfVocabulary.Algorithm;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.CvState;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.DesKeyForm;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.EffectiveStrength;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.Exportability;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.MaterialState;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.MkvpState;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.Scope;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.TvvState;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.WrapMethod;

/** The 64-byte fixed-length formats: AES (Table 614), DES (Tables 615/616) and RKX (Table 617). */
final class SymmetricFixedTokenParser {

    private SymmetricFixedTokenParser() { }

    private static IcsfText t(String key, Object... arguments) {
        return IcsfText.of(key, arguments);
    }

    // =====================================================================
    // AES fixed-length internal (id X'01', version X'04') -- Table 614
    // =====================================================================
    static ParseResult parseAes(byte[] token, Origin origin) {
        ParseResult result = ParseResult.ok(TokenFamily.SYM_FIXED_AES, token.length);
        checkFixedLength(result, token, "AES");

        int flag = IcsfHex.u8(token, 6);
        boolean encrypted = IcsfHex.bit(flag, 0);
        boolean cvApplied = IcsfHex.bit(flag, 1);
        boolean noKeyOrNoMkvp = IcsfHex.bit(flag, 2);

        IcsfSection header = new IcsfSection(t("icsf.section.headerFlags"));
        header.add(0, 1, t("icsf.field.tokenId"), IcsfHex.hex(token, 0, 1), t("icsf.value.tokenId.internal"));
        header.add(1, 3, t("icsf.field.implementationBytes"), IcsfHex.hex(token, 1, 4),
                t("icsf.value.implementationBytes"));
        header.add(4, 1, t("icsf.field.tokenVersion"), IcsfHex.hex(token, 4, 5), t("icsf.value.version.aes"));
        header.add(7, 1, t("icsf.field.clearKeyLrc"), IcsfHex.hex(token, 7, 8));
        header.add(t("icsf.flag.encryptedWithMkvp"), encrypted, t("icsf.flag.encryptedWithMkvpAesHelp"));
        header.add(t("icsf.flag.cvApplied"), cvApplied, t("icsf.flag.cvAppliedHelp"));
        header.add(t("icsf.flag.noKeyOrNoMkvp"), noKeyOrNoMkvp, t("icsf.flag.noKeyOrNoMkvpHelp"));

        boolean mkvpZero = IcsfHex.isAllZero(token, 8, 16);
        boolean cvZero = IcsfHex.isAllZero(token, 48, 56);
        int clearBits = IcsfHex.u16(token, 56);
        int encryptedBytes = IcsfHex.u16(token, 58);

        IcsfSection body = new IcsfSection(t("icsf.section.keyBody"));
        body.add(8, 8, t("icsf.field.mkvp"), IcsfHex.hex(token, 8, 16),
                t(mkvpZero ? "icsf.value.mkvp.zeroAes" : "icsf.value.mkvp.presentAes"));
        body.add(16, 32, t("icsf.field.keyValue"), IcsfHex.hex(token, 16, 48));
        body.add(48, 8, t("icsf.field.controlVector"), IcsfHex.hex(token, 48, 56),
                cvZero ? t("icsf.value.cv.zeroAesData") : IcsfText.EMPTY);
        body.add(56, 2, t("icsf.field.clearKeyBits"), IcsfHex.hex(token, 56, 58),
                t("icsf.value.clearKeyBits", clearBits));
        body.add(58, 2, t("icsf.field.encryptedKeyBytes"), IcsfHex.hex(token, 58, 60),
                t("icsf.value.encryptedKeyBytes", encryptedBytes));

        IcsfTvv.Evaluation tvv = IcsfTvv.evaluate(token);
        body.add(60, 4, t("icsf.field.tvv"), IcsfHex.hex(token, 60, 64), tvv.detail());

        result.section(header).section(body);

        // --- coherence with Table 614 / p. 1560 ----------------------------
        if (cvApplied && !cvZero) {
            result.warn(DiagnosticCode.AES_CV_FLAG_WITHOUT_ZERO_CV,
                    t("icsf.warn.aesCvFlagWithoutZeroCv", IcsfHex.hex(token, 48, 56)));
        }
        if (clearBits != 0 && clearBits != 128 && clearBits != 192 && clearBits != 256) {
            result.warn(DiagnosticCode.AES_CLEAR_LENGTH_INVALID,
                    t("icsf.warn.aesClearLength", clearBits));
        }
        if (encryptedBytes != 0 && encryptedBytes != 32) {
            result.warn(DiagnosticCode.AES_ENCRYPTED_LENGTH_INVALID,
                    t("icsf.warn.aesEncryptedLength", encryptedBytes));
        }

        MaterialState material;
        IcsfText materialDetail;
        if (encrypted) {
            material = MaterialState.ENCRYPTED;
            materialDetail = t(noKeyOrNoMkvp
                    ? "icsf.material.aesEncryptedNoMkvp" : "icsf.material.aesEncrypted");
        } else if (noKeyOrNoMkvp) {
            material = MaterialState.NO_KEY;
            materialDetail = t("icsf.material.noKey");
        } else {
            material = MaterialState.CLEAR;
            materialDetail = t("icsf.material.clear");
        }

        result.summary(SummaryKey.FAMILY, TokenFamily.SYM_FIXED_AES, t("icsf.family.aesFixed"))
                .summary(SummaryKey.SCOPE, Scope.INTERNAL, t("icsf.scope.internal"))
                .summary(SummaryKey.ALGORITHM, Algorithm.AES)
                .summary(SummaryKey.KEY_TYPE, "DATA", t("icsf.keyType.aesFixedData"))
                .summary(SummaryKey.KEY_LENGTH, clearBits > 0 ? String.valueOf(clearBits) : "UNDECLARED",
                        clearBits > 0 ? t("icsf.length.bits", clearBits) : t("icsf.length.undeclared"))
                .summary(SummaryKey.EFFECTIVE_STRENGTH, EffectiveStrength.NOT_APPLICABLE)
                .summary(SummaryKey.MATERIAL_STATE, material, materialDetail)
                .summary(SummaryKey.WRAPPING, WrapMethod.NOT_APPLICABLE)
                .summary(SummaryKey.CONTROL_VECTOR, cvZero ? CvState.ZERO : CvState.PRESENT,
                        t(cvZero ? "icsf.cvState.zeroAesData" : "icsf.cvState.nonZero"))
                .summary(SummaryKey.TVV, tvv.state(), tvv.detail())
                .summary(SummaryKey.MKVP, mkvpZero ? MkvpState.ABSENT : MkvpState.PRESENT)
                .summary(SummaryKey.ALLOWED_USES, "DATA", t("icsf.uses.aesData"))
                .summary(SummaryKey.EXPORTABILITY, Exportability.NOT_APPLICABLE,
                        t("icsf.export.aesFixedNotApplicable"));

        IcsfProvenance.applySymmetric(result, origin,
                mkvpZero ? MkvpState.ABSENT : MkvpState.PRESENT, tvv.state(), true);
        return result;
    }

    // =====================================================================
    // DES fixed-length internal / external -- Tables 615 / 616
    // =====================================================================
    static ParseResult parseDes(byte[] token, boolean internal, Origin origin) {
        TokenFamily family = internal ? TokenFamily.SYM_FIXED_DES_INT : TokenFamily.SYM_FIXED_DES_EXT;
        ParseResult result = ParseResult.ok(family, token.length);
        checkFixedLength(result, token, "DES");

        int flag6 = IcsfHex.u8(token, 6);
        boolean encrypted = IcsfHex.bit(flag6, 0);
        boolean cvPresentFlag = IcsfHex.bit(flag6, 1);
        boolean nocv = IcsfHex.bit(flag6, 2);
        boolean noExport = internal && IcsfHex.bit(flag6, 7);
        WrapMethod wrap = DesKeyAnalysis.wrapMethod(token);

        IcsfSection header = new IcsfSection(t("icsf.section.headerFlags"));
        header.add(0, 1, t("icsf.field.tokenId"), IcsfHex.hex(token, 0, 1),
                t(internal ? "icsf.value.tokenId.internal" : "icsf.value.tokenId.external"));
        header.add(4, 1, t("icsf.field.tokenVersion"), IcsfHex.hex(token, 4, 5), t("icsf.value.version.des"));
        header.add(7, 1, t("icsf.field.wrapMethod"), IcsfHex.hex(token, 7, 8), wrapLabel(wrap));
        header.add(t("icsf.flag.encryptedWithMkvp"), encrypted, t("icsf.flag.encryptedWithMkvpDesHelp"));
        header.add(t("icsf.flag.cvPresent"), cvPresentFlag);
        header.add(t("icsf.flag.nocv"), nocv, t("icsf.flag.nocvHelp"));
        if (internal) {
            header.add(t("icsf.flag.exportProhibited"), noExport, t("icsf.flag.exportProhibitedHelp"));
        }

        boolean mkvpZero = IcsfHex.isAllZero(token, 8, 16);
        IcsfSection body = new IcsfSection(t("icsf.section.keyBody"));
        if (internal) {
            body.add(8, 8, t("icsf.field.mkvp"), IcsfHex.hex(token, 8, 16), t("icsf.value.mkvp.des"));
        } else {
            body.add(8, 8, t("icsf.field.reserved"), IcsfHex.hex(token, 8, 16),
                    t("icsf.value.reservedExternal"));
        }
        body.add(16, 8, t("icsf.field.keyLeft"), IcsfHex.hex(token, 16, 24));
        body.add(24, 8, t("icsf.field.keyRight"), IcsfHex.hex(token, 24, 32));
        body.add(32, 8, t("icsf.field.cvLeft"), IcsfHex.hex(token, 32, 40));
        body.add(40, 8, t("icsf.field.cvRight"), IcsfHex.hex(token, 40, 48));
        body.add(48, 8, t("icsf.field.partC"), IcsfHex.hex(token, 48, 56));

        int byte59 = IcsfHex.u8(token, 59);
        result.byte59(byte59);
        DesKeyAnalysis.KeyLength length = DesKeyAnalysis.keyLength(token);

        // Table 615: byte 59 is valid only for DATA keys with a zero CV, and each
        // value is tied to a specific token version.
        if (IcsfHex.isAllZero(token, 32, 40) && (byte59 == 0x00 || byte59 == 0x10 || byte59 == 0x20)) {
            int expectedVersion = byte59 == 0x00 ? 0x00 : 0x01;
            if (IcsfHex.u8(token, 4) != expectedVersion) {
                result.warn(DiagnosticCode.BYTE59_VERSION_MISMATCH, t("icsf.warn.byte59Version",
                        hex2(byte59), hex2(expectedVersion), hex2(IcsfHex.u8(token, 4))));
            }
        }
        body.add(59, 1, t("icsf.field.lengthByte"), IcsfHex.hex(token, 59, 60),
                keyLengthByteLabel(byte59));
        body.add(IcsfSection.Field.derived(59, t("icsf.field.detectedKeyLength"),
                t(length.uniqueParts() ? "icsf.value.detectedLengthUnique" : "icsf.value.detectedLength",
                        keyFormLabel(length.form()), length.basis())));

        IcsfTvv.Evaluation tvv = IcsfTvv.evaluate(token);
        body.add(60, 4, t("icsf.field.tvv"), IcsfHex.hex(token, 60, 64), tvv.detail());

        // --- Control Vector: type, uses and control bits -------------------
        DesExportAnalysis.Result export = DesExportAnalysis.analyse(token, internal);
        boolean cvUsable = DesControlVector.usable(token);
        DesControlVector.Info cvInfo = cvUsable
                ? DesControlVector.decode(IcsfHex.slice(token, 32, 40)) : null;

        IcsfSection cvSection = new IcsfSection(t("icsf.section.controlVector"));
        cvSection.add(32, 8, t("icsf.field.cvEvaluated"), IcsfHex.hex(token, 32, 40),
                t("icsf.value.cvBitNumbering"));
        if (cvInfo != null) {
            cvSection.add(33, 1, t("icsf.field.cvMainAndSubtype"), IcsfHex.hex(token, 33, 34),
                    cvInfo.family());
            cvSection.add(34, 1, t("icsf.field.cvUsageBits"), IcsfHex.hex(token, 34, 35), cvInfo.usage());
            cvSection.addAll(cvInfo.flags());
        }
        cvSection.addAll(export.flags());
        for (Diagnostic warning : export.warnings()) result.warn(warning.code(), warning.message());
        if (export.structureValid() != null) result.controlVectorStructureValid(export.structureValid());
        result.controlVectorEnhOnly(export.enhOnly()).compliantTagged(export.compliantTagged());

        result.section(header).section(body).section(cvSection);

        // --- components / effective strength -------------------------------
        DesKeyAnalysis.Components components = DesKeyAnalysis.components(token);
        if (components != null) {
            IcsfSection componentSection = new IcsfSection(t("icsf.section.components"));
            String[] names = {"K1", "K2", "K3"};
            int[] offsets = {16, 24, 48};
            for (int index = 0; index < components.parts(); index++) {
                componentSection.add(offsets[index], 8,
                        t("icsf.field.component", names[index], offsets[index]),
                        components.partsHex().get(index));
            }
            componentSection.add(IcsfSection.Field.derived(16, t("icsf.field.componentPattern"),
                    IcsfText.raw(components.pattern())));
            componentSection.add(IcsfSection.Field.derived(16, t("icsf.field.effectiveStrength"),
                    DesKeyAnalysis.describe(components.effective(), components.pattern())));
            componentSection.add(IcsfSection.Field.derived(16, t("icsf.field.inferenceReliability"),
                    t(components.reliable() ? "icsf.value.reliable" : "icsf.value.notReliable",
                            components.reason())));
            result.section(componentSection);
            result.desComponents(components.parts(), components.reliable());
        }

        // --- summary card ---------------------------------------------------
        CvState cvState = cvUsable ? CvState.PRESENT : (nocv ? CvState.NOCV : CvState.ZERO);
        String keyType;
        IcsfText keyTypeDetail;
        if (cvInfo != null) {
            keyType = cvInfo.keyType().isEmpty()
                    ? IcsfVocabulary.KeyTypeSource.UNRECOGNIZED.code() : cvInfo.keyType();
            keyTypeDetail = cvInfo.keyType().isEmpty()
                    ? t("icsf.keyType.unrecognized", cvInfo.family())
                    : t("icsf.keyType.fromCv", cvInfo.keyType(), cvInfo.family());
        } else if (nocv) {
            keyType = "NOCV";
            keyTypeDetail = t("icsf.keyType.nocv");
        } else {
            keyType = "DATA";
            keyTypeDetail = t("icsf.keyType.zeroCv");
        }

        result.summary(SummaryKey.FAMILY, family, t("icsf.family.desFixed"))
                .summary(SummaryKey.SCOPE, internal ? Scope.INTERNAL : Scope.EXTERNAL,
                        t(internal ? "icsf.scope.internal" : "icsf.scope.external"))
                .summary(SummaryKey.ALGORITHM, Algorithm.DES_TDES)
                .summary(SummaryKey.KEY_TYPE, keyType, keyTypeDetail)
                .summary(SummaryKey.KEY_LENGTH, length.form(),
                        t("icsf.length.withBasis", keyFormLabel(length.form()), length.basis()))
                .summary(SummaryKey.MATERIAL_STATE,
                        encrypted ? MaterialState.ENCRYPTED : MaterialState.CLEAR,
                        encrypted ? t(internal ? "icsf.material.desUnderMasterKey"
                                : "icsf.material.desUnderKek") : t("icsf.material.clear"))
                .summary(SummaryKey.WRAPPING, wrap, wrapLabel(wrap))
                .summary(SummaryKey.CONTROL_VECTOR, cvState)
                .summary(SummaryKey.TVV, tvv.state(), tvv.detail())
                .summary(SummaryKey.MKVP, internal ? (mkvpZero ? MkvpState.ABSENT : MkvpState.PRESENT)
                        : MkvpState.NOT_APPLICABLE)
                .summary(SummaryKey.EFFECTIVE_STRENGTH,
                        components == null ? EffectiveStrength.NOT_APPLICABLE : components.effective(),
                        components == null ? t("icsf.strength.singleLengthNothingToCompare")
                                : DesKeyAnalysis.describe(components.effective(), components.pattern()));

        if (components != null) {
            result.summary(SummaryKey.COMPONENT_PATTERN, components.pattern(),
                            IcsfText.raw(components.pattern()))
                    .summary(SummaryKey.COMPONENT_RELIABILITY,
                            components.reliable() ? "RELIABLE" : "NOT_RELIABLE", components.reason());
        }
        if (cvInfo != null) {
            result.summary(SummaryKey.ALLOWED_USES, "CV_DECODED", cvInfo.usage());
        }
        result.summary(SummaryKey.EXPORTABILITY, export.verdict(),
                internal ? export.summary() : t("icsf.export.externalPrefix", export.summary()));

        IcsfProvenance.applySymmetric(result, origin,
                internal ? (mkvpZero ? MkvpState.ABSENT : MkvpState.PRESENT) : MkvpState.NOT_APPLICABLE,
                tvv.state(), internal);
        return result;
    }

    // =====================================================================
    // RKX DES external (id X'02', version X'10') -- Table 617
    // =====================================================================
    static ParseResult parseRkx(byte[] token, Origin origin) {
        ParseResult result = ParseResult.ok(TokenFamily.RKX_DES_EXT, token.length);
        checkFixedLength(result, token, "RKX");

        int keyLength = IcsfHex.u8(token, 7);
        byte[] ruleId = IcsfHex.slice(token, 40, 48);
        String ruleText = asciiOrHex(ruleId);

        IcsfSection section = new IcsfSection(t("icsf.section.rkx"));
        section.add(0, 1, t("icsf.field.tokenId"), IcsfHex.hex(token, 0, 1),
                t("icsf.value.tokenId.external"));
        section.add(4, 1, t("icsf.field.tokenVersion"), IcsfHex.hex(token, 4, 5),
                t("icsf.value.version.rkx"));
        section.add(7, 1, t("icsf.field.rkxKeyLength"), IcsfHex.hex(token, 7, 8),
                t("icsf.value.bytes", keyLength));
        section.add(8, 8, t("icsf.field.confounder"), IcsfHex.hex(token, 8, 16));
        section.add(16, 8, t("icsf.field.rkxKeyLeft"), IcsfHex.hex(token, 16, 24));
        section.add(24, 8, t("icsf.field.rkxKeyMiddle"), IcsfHex.hex(token, 24, 32),
                t("icsf.value.zeroIfUnused"));
        section.add(32, 8, t("icsf.field.rkxKeyRight"), IcsfHex.hex(token, 32, 40),
                t("icsf.value.zeroIfUnused"));
        section.add(40, 8, t("icsf.field.ruleId"), IcsfHex.hex(ruleId),
                t("icsf.value.ruleIdAscii", ruleText));
        section.add(56, 8, t("icsf.field.rkxMac"), IcsfHex.hex(token, 56, 64), t("icsf.value.rkxMac"));
        result.section(section);

        result.summary(SummaryKey.FAMILY, TokenFamily.RKX_DES_EXT, t("icsf.family.rkx"))
                .summary(SummaryKey.SCOPE, Scope.EXTERNAL, t("icsf.scope.rkx"))
                .summary(SummaryKey.ALGORITHM, Algorithm.DES_TDES)
                .summary(SummaryKey.KEY_TYPE, "RKX", t("icsf.keyType.rkx"))
                .summary(SummaryKey.KEY_LENGTH, "NOT_APPLICABLE",
                        t("icsf.length.rkxWithConfounder", keyLength))
                .summary(SummaryKey.EFFECTIVE_STRENGTH, EffectiveStrength.NOT_APPLICABLE)
                .summary(SummaryKey.MATERIAL_STATE, MaterialState.ENCRYPTED, t("icsf.material.rkx"))
                .summary(SummaryKey.WRAPPING, WrapMethod.NOT_APPLICABLE)
                .summary(SummaryKey.CONTROL_VECTOR, CvState.NOT_APPLICABLE)
                .summary(SummaryKey.TVV, TvvState.NOT_APPLICABLE)
                .summary(SummaryKey.MKVP, MkvpState.NOT_APPLICABLE)
                .summary(SummaryKey.STRUCTURE, "RKX", t("icsf.structure.rkx"))
                .summary(SummaryKey.RULE_ID, ruleText.isEmpty() ? "EMPTY" : ruleText,
                        IcsfText.raw(ruleText))
                .summary(SummaryKey.EXPORTABILITY, Exportability.NOT_APPLICABLE, t("icsf.export.rkx"));

        result.provenanceNote(t("icsf.prov.rkxNotInCkds"));
        return result;
    }

    // --- helpers ---------------------------------------------------------
    private static void checkFixedLength(ParseResult result, byte[] token, String what) {
        if (token.length != 64) {
            result.warn(DiagnosticCode.UNEXPECTED_TOKEN_LENGTH,
                    t("icsf.warn.unexpectedLength", what, token.length));
        }
    }

    private static String hex2(int value) {
        return String.format(java.util.Locale.ROOT, "%02X", value);
    }

    static IcsfText wrapLabel(WrapMethod wrap) {
        return t("icsf.wrap." + wrap.name());
    }

    private static IcsfText keyLengthByteLabel(int byte59) {
        return switch (byte59) {
            case 0x00 -> t("icsf.byte59.single");
            case 0x10 -> t("icsf.byte59.double");
            case 0x20 -> t("icsf.byte59.triple");
            default -> t("icsf.byte59.undefined");
        };
    }

    static IcsfText keyFormLabel(DesKeyForm form) {
        return t("icsf.keyForm." + form.name());
    }

    static String asciiOrHex(byte[] raw) {
        StringBuilder out = new StringBuilder();
        for (byte value : raw) {
            int unsigned = value & 0xFF;
            if (unsigned < 0x20 || unsigned > 0x7E) return IcsfHex.hex(raw);
            out.append((char) unsigned);
        }
        return out.toString().stripTrailing();
    }
}
