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
import java.util.List;
import java.util.Map;

/** Variable-length symmetric tokens, version X'05' (Tables 618-631): AES, DES and HMAC. */
final class SymmetricVariableTokenParser {

    private SymmetricVariableTokenParser() { }

    private static IcsfText t(String key, Object... arguments) {
        return IcsfText.of(key, arguments);
    }

    private static final Map<Integer, String> KEY_MATERIAL_STATE = Map.of(
            0x00, "icsf.var.state.noKey",
            0x01, "icsf.var.state.clear",
            0x02, "icsf.var.state.underKek",
            0x03, "icsf.var.state.underMasterKey");

    private static final Map<Integer, String> KVP_TYPE = Map.of(
            0x00, "icsf.var.kvp.none",
            0x01, "icsf.var.kvp.aesMkvp",
            0x02, "icsf.var.kvp.kek",
            0x03, "icsf.var.kvp.truncatedCompliance");

    /** Hash mnemonics are identifiers, not words. */
    private static final Map<Integer, String> HASH_ALGORITHM = Map.of(
            0x01, "SHA-1", 0x02, "SHA-256", 0x04, "SHA-384", 0x08, "SHA-512");

    private static final Map<Integer, String> KEY_TYPE_AES = Map.ofEntries(
            Map.entry(0x0001, "CIPHER"), Map.entry(0x0002, "MAC"), Map.entry(0x0003, "EXPORTER"),
            Map.entry(0x0004, "IMPORTER"), Map.entry(0x0005, "PINPROT"), Map.entry(0x0006, "PINCALC"),
            Map.entry(0x0007, "PINPRW"), Map.entry(0x0009, "DKYGENKY"), Map.entry(0x000A, "SECMSG"),
            Map.entry(0x000B, "KDKGENKY"));

    private static final Map<Integer, String> PEDIGREE_ORIGINAL = Map.of(
            0x00, "icsf.pedigree.orig.unknown",
            0x01, "icsf.pedigree.orig.other",
            0x02, "icsf.pedigree.orig.random",
            0x03, "icsf.pedigree.orig.keyAgreement",
            0x04, "icsf.pedigree.orig.clearComponents",
            0x05, "icsf.pedigree.orig.clearValue",
            0x06, "icsf.pedigree.orig.derived",
            0x07, "icsf.pedigree.orig.tke");

    private static final Map<Integer, String> PEDIGREE_CURRENT = Map.ofEntries(
            Map.entry(0x00, "icsf.pedigree.curr.unknown"),
            Map.entry(0x01, "icsf.pedigree.curr.other"),
            Map.entry(0x02, "icsf.pedigree.curr.random"),
            Map.entry(0x03, "icsf.pedigree.curr.keyAgreement"),
            Map.entry(0x04, "icsf.pedigree.curr.clearComponents"),
            Map.entry(0x05, "icsf.pedigree.curr.clearValue"),
            Map.entry(0x06, "icsf.pedigree.curr.derived"),
            Map.entry(0x07, "icsf.pedigree.curr.importedWithPedigree"),
            Map.entry(0x08, "icsf.pedigree.curr.importedWithoutPedigree"),
            Map.entry(0x09, "icsf.pedigree.curr.importedFromCv"),
            Map.entry(0x0A, "icsf.pedigree.curr.importedFromZeroCv"),
            Map.entry(0x0B, "icsf.pedigree.curr.importedTr31WithCv"),
            Map.entry(0x0C, "icsf.pedigree.curr.importedTr31NoCv"),
            Map.entry(0x0D, "icsf.pedigree.curr.importedPkcs12"),
            Map.entry(0x0E, "icsf.pedigree.curr.importedOaep"),
            Map.entry(0x0F, "icsf.pedigree.curr.importedPka92"),
            Map.entry(0x10, "icsf.pedigree.curr.importedZeroPad"),
            Map.entry(0x11, "icsf.pedigree.curr.convertedWithCv"),
            Map.entry(0x12, "icsf.pedigree.curr.convertedZeroCv"),
            Map.entry(0x13, "icsf.pedigree.curr.tke"),
            Map.entry(0x14, "icsf.pedigree.curr.exportedWithPedigree"),
            Map.entry(0x15, "icsf.pedigree.curr.exportedWithoutPedigree"),
            Map.entry(0x16, "icsf.pedigree.curr.exportedOaep"));

    static ParseResult parse(byte[] token, Origin origin) {
        int tokenId = IcsfHex.u8(token, 0);

        if (tokenId == 0x00) {
            ParseResult nullResult = ParseResult.ok(TokenFamily.SYM_VARIABLE_NULL, token.length);
            nullResult.section(new IcsfSection(t("icsf.section.nullToken"))
                    .add(0, 1, t("icsf.field.tokenId"), IcsfHex.hex(token, 0, 1),
                            t("icsf.value.tokenId.null")));
            return nullSummary(nullResult, t("icsf.family.variableNull"));
        }

        boolean internal = tokenId == 0x01;
        ParseResult result = ParseResult.ok(TokenFamily.SYM_VARIABLE, token.length);

        int declaredLength = IcsfHex.u16(token, 2);
        if (declaredLength != token.length) {
            result.warn(DiagnosticCode.DECLARED_LENGTH_MISMATCH,
                    t("icsf.warn.declaredLength", declaredLength, token.length));
        }

        // Header (8) + wrapping (22) + fixed associated data (15) + kuf: every field
        // up to and including offset 44 is at a fixed position (Table 618).
        if (token.length < 45) {
            return ParseResult.failure("Truncated variable-length token: " + token.length
                    + " bytes; the header and the fixed-position associated data run to offset 44 "
                    + "(Table 618).", token.length);
        }

        int materialState = IcsfHex.u8(token, 8);
        int kvpType = IcsfHex.u8(token, 9);
        byte[] kvp = IcsfHex.slice(token, 10, 26);
        int wrapMethod = IcsfHex.u8(token, 26);
        int hashAlgorithm = IcsfHex.u8(token, 27);
        int payloadVersion = IcsfHex.u8(token, 28);

        int associatedDataLength = IcsfHex.u16(token, 32);
        int keyNameLength = IcsfHex.u8(token, 34);
        int ibmExtendedLength = IcsfHex.u8(token, 35);
        int installationLength = IcsfHex.u8(token, 36);
        int payloadBits = IcsfHex.u16(token, 38);
        int algorithm = IcsfHex.u8(token, 41);
        int keyType = IcsfHex.u16(token, 42);
        int usageFieldCount = IcsfHex.u8(token, 44);

        int usageOffset = 45;
        byte[] usageFields = IcsfHex.slice(token, usageOffset, usageOffset + usageFieldCount * 2);

        int managementCountOffset = usageOffset + usageFieldCount * 2;
        if (managementCountOffset >= token.length) {
            return ParseResult.failure("Truncated token: the " + usageFieldCount + " declared usage fields "
                    + "(kuf, offset 44) put the kmf counter at offset " + managementCountOffset
                    + ", past the token's " + token.length + " bytes.", token.length);
        }
        int managementCount = IcsfHex.u8(token, managementCountOffset);
        int managementOffset = managementCountOffset + 1;
        byte[] managementFields = IcsfHex.slice(token, managementOffset,
                managementOffset + managementCount * 2);
        if (managementFields.length < managementCount * 2) {
            result.warn(DiagnosticCode.MANAGEMENT_FIELDS_TRUNCATED,
                    t("icsf.warn.managementTruncated", managementCount, managementCount * 2,
                            managementOffset, managementFields.length));
        }

        int nameOffset = managementOffset + managementCount * 2;
        byte[] keyName = IcsfHex.slice(token, nameOffset, nameOffset + keyNameLength);

        // Table 618: the associated data runs from offset 30 to 30+adl and consists of
        // 16 fixed bytes plus the usage, management, name, IEAD and UAD fields.
        int computedAdl = 16 + usageFieldCount * 2 + managementCount * 2
                + keyNameLength + ibmExtendedLength + installationLength;
        if (associatedDataLength != computedAdl) {
            result.warn(DiagnosticCode.ASSOCIATED_DATA_LENGTH_MISMATCH,
                    t("icsf.warn.adlMismatch", associatedDataLength, computedAdl));
        }
        int associatedDataEnd = 30 + associatedDataLength;
        if (associatedDataEnd > token.length) {
            result.warn(DiagnosticCode.ASSOCIATED_DATA_OVERFLOW,
                    t("icsf.warn.adOverflow", associatedDataEnd, token.length));
        } else if (associatedDataEnd + (payloadBits + 7) / 8 > token.length) {
            result.warn(DiagnosticCode.PAYLOAD_OVERFLOW,
                    t("icsf.warn.payloadOverflow", payloadBits, associatedDataEnd, token.length));
        }

        String keyTypeName = keyTypeName(algorithm, keyType);
        Algorithm algorithmValue = switch (algorithm) {
            case 0x01 -> Algorithm.DES_TDES;
            case 0x02 -> Algorithm.AES;
            case 0x03 -> Algorithm.HMAC;
            default -> Algorithm.UNKNOWN;
        };
        WrapMethod wrapValue = switch (wrapMethod) {
            case 0x00 -> WrapMethod.CLEAR;
            case 0x02 -> WrapMethod.AESKW;
            case 0x03 -> WrapMethod.PKOAEP2;
            default -> WrapMethod.RESERVED;
        };

        // --- sections --------------------------------------------------------
        IcsfSection header = new IcsfSection(t("icsf.section.headerWrapping"));
        header.add(0, 1, t("icsf.field.tokenId"), IcsfHex.hex(token, 0, 1),
                t(internal ? "icsf.value.tokenId.internal" : "icsf.value.tokenId.external"));
        header.add(2, 2, t("icsf.field.tokenLength"), IcsfHex.hex(token, 2, 4),
                t("icsf.value.bytes", declaredLength));
        header.add(4, 1, t("icsf.field.tokenVersion"), IcsfHex.hex(token, 4, 5),
                t("icsf.value.version.variable"));
        header.add(8, 1, t("icsf.field.keyMaterialState"), IcsfHex.hex(token, 8, 9),
                stateText(materialState));
        header.add(9, 1, t("icsf.field.kvpType"), IcsfHex.hex(token, 9, 10), kvpText(kvpType));
        header.add(10, 16, t("icsf.field.verificationPattern"), IcsfHex.hex(kvp));
        header.add(26, 1, t("icsf.field.wrapMethod"), IcsfHex.hex(token, 26, 27),
                SymmetricFixedTokenParser.wrapLabel(wrapValue));
        header.add(27, 1, t("icsf.field.wrapHash"), IcsfHex.hex(token, 27, 28),
                HASH_ALGORITHM.containsKey(hashAlgorithm)
                        ? IcsfText.raw(HASH_ALGORITHM.get(hashAlgorithm))
                        : t(hashAlgorithm == 0 ? "icsf.value.none" : "icsf.value.reserved"));
        header.add(28, 1, t("icsf.field.payloadVersion"), IcsfHex.hex(token, 28, 29),
                t(payloadVersion == 0 ? "icsf.value.payloadVariable"
                        : payloadVersion == 1 ? "icsf.value.payloadFixed" : "icsf.value.reserved"));

        IcsfSection associated = new IcsfSection(t("icsf.section.associatedData"));
        associated.add(30, 1, t("icsf.field.adVersion"), IcsfHex.hex(token, 30, 31));
        associated.add(32, 2, t("icsf.field.adl"), IcsfHex.hex(token, 32, 34),
                IcsfText.raw(String.valueOf(associatedDataLength)));
        associated.add(34, 1, t("icsf.field.keyNameLength"), IcsfHex.hex(token, 34, 35),
                IcsfText.raw(String.valueOf(keyNameLength)));
        associated.add(35, 1, t("icsf.field.iead"), IcsfHex.hex(token, 35, 36),
                IcsfText.raw(String.valueOf(ibmExtendedLength)));
        associated.add(36, 1, t("icsf.field.uad"), IcsfHex.hex(token, 36, 37),
                IcsfText.raw(String.valueOf(installationLength)));
        associated.add(38, 2, t("icsf.field.payloadBits"), IcsfHex.hex(token, 38, 40),
                IcsfText.raw(String.valueOf(payloadBits)));
        associated.add(41, 1, t("icsf.field.algorithm"), IcsfHex.hex(token, 41, 42),
                algorithmLabel(algorithmValue));
        associated.add(42, 2, t("icsf.field.keyType"), IcsfHex.hex(token, 42, 44),
                IcsfText.raw(keyTypeName));
        associated.add(44, 1, t("icsf.field.kuf"), IcsfHex.hex(token, 44, 45),
                IcsfText.raw(String.valueOf(usageFieldCount)));
        associated.add(usageOffset, usageFieldCount * 2, t("icsf.field.usageFields"),
                IcsfHex.hex(usageFields));
        associated.add(managementCountOffset, 1, t("icsf.field.kmf"),
                IcsfHex.hex(token, managementCountOffset, managementCountOffset + 1),
                IcsfText.raw(String.valueOf(managementCount)));
        associated.add(managementOffset, managementCount * 2, t("icsf.field.managementFields"),
                IcsfHex.hex(managementFields));
        if (keyNameLength > 0) {
            associated.add(nameOffset, keyNameLength, t("icsf.field.keyName"), IcsfHex.hex(keyName),
                    IcsfText.raw(SymmetricFixedTokenParser.asciiOrHex(keyName)));
        }

        IcsfSection uses = new IcsfSection(t("icsf.section.permittedUses"));
        List<IcsfText> useSummary = decodeUsageFields(algorithm, keyTypeName, usageFieldCount,
                usageFields, uses);

        IcsfSection management = new IcsfSection(t("icsf.section.management"));
        Exportability exportability;
        IcsfText exportDetail;

        // The Table 629 management bits belong to AES and HMAC keys. For DESUSECV keys
        // Table 630 (p. 1595) defines kmf = 1 and the whole field as RESERVED, so
        // reading it as exportability would be inventing.
        boolean desUseCv = algorithm == 0x01;
        if (desUseCv) {
            exportability = Exportability.NOT_APPLICABLE;
            exportDetail = t("icsf.export.desusecv");
            management.add(managementOffset, managementFields.length,
                    t("icsf.field.managementReserved"), IcsfHex.hex(managementFields),
                    t("icsf.value.table630Reserved"));
            if (!IcsfHex.isAllZero(managementFields, 0, managementFields.length)) {
                result.warn(DiagnosticCode.DESUSECV_RESERVED_NONZERO,
                        t("icsf.warn.desusecvReserved", IcsfHex.hex(managementFields)));
            }
        } else if (managementCount >= 1 && managementFields.length >= 2) {
            int high = managementFields[0] & 0xFF;
            int low = managementFields[1] & 0xFF;
            String[] allowedKeys = {"icsf.var.export.symmetric", "icsf.var.export.asymUnauth",
                    "icsf.var.export.asymAuth", "icsf.var.export.raw", "icsf.var.export.cpacf"};
            List<IcsfText> allowed = new ArrayList<>();
            for (int index = 0; index < allowedKeys.length; index++) {
                boolean on = IcsfHex.bit(high, index);
                management.add(t(allowedKeys[index]), on);
                if (on) allowed.add(t(allowedKeys[index]));
            }
            management.add(t("icsf.var.export.compliantTagged"), IcsfHex.bit(high, 7));
            result.compliantTagged(IcsfHex.bit(high, 7));

            String[] prohibitedKeys = {"icsf.var.export.prohibitDes", "icsf.var.export.prohibitAes",
                    null, null, "icsf.var.export.prohibitRsa"};
            List<IcsfText> prohibited = new ArrayList<>();
            for (int index = 0; index < prohibitedKeys.length; index++) {
                if (prohibitedKeys[index] == null) continue;
                boolean on = IcsfHex.bit(low, index);
                management.add(t(prohibitedKeys[index]), on);
                if (on) prohibited.add(t(prohibitedKeys[index]));
            }

            if (allowed.isEmpty()) {
                exportability = Exportability.NO;
                exportDetail = t("icsf.export.noMethodEnabled");
            } else {
                exportability = Exportability.YES;
                exportDetail = t("icsf.export.permitted", joined(allowed));
            }
            if (!prohibited.isEmpty()) {
                exportDetail = t("icsf.export.withProhibitions", exportDetail, joined(prohibited));
            }
        } else {
            exportability = Exportability.NOT_DETERMINABLE;
            exportDetail = t("icsf.export.noManagementFields");
        }

        if (!desUseCv && managementCount >= 2 && managementFields.length >= 4) {
            int high = managementFields[2] & 0xFF;
            int low = managementFields[3] & 0xFF;
            String completeness = switch ((high >> 6) & 0b11) {
                case 0b11 -> "icsf.var.completeness.twoOrMoreMissing";
                case 0b10 -> "icsf.var.completeness.oneMissing";
                case 0b01 -> "icsf.var.completeness.mayBeCompleted";
                default -> "icsf.var.completeness.complete";
            };
            management.add(t("icsf.field.completeness"), true, t(completeness));
            String[] historyKeys = {null, null, null, "icsf.var.history.untrustedKek",
                    "icsf.var.history.noAttributes", "icsf.var.history.weakerKey",
                    "icsf.var.history.nonCca", "icsf.var.history.ecb"};
            for (int index = 3; index < historyKeys.length; index++) {
                boolean on = IcsfHex.bit(low, index);
                management.add(t(historyKeys[index]), on);
                if (on) result.securityHistoryDegraded(true);
            }
        }

        IcsfText pedigree = t("icsf.pedigree.notPresent");
        boolean pedigreePresent = false;
        if (!desUseCv && managementCount >= 3 && managementFields.length >= 6) {
            int original = managementFields[4] & 0xFF;
            int current = managementFields[5] & 0xFF;
            pedigree = t("icsf.pedigree.pair",
                    PEDIGREE_ORIGINAL.containsKey(original) ? t(PEDIGREE_ORIGINAL.get(original))
                            : IcsfText.raw(String.format(java.util.Locale.ROOT, "0x%02X", original)),
                    PEDIGREE_CURRENT.containsKey(current) ? t(PEDIGREE_CURRENT.get(current))
                            : IcsfText.raw(String.format(java.util.Locale.ROOT, "0x%02X", current)));
            pedigreePresent = true;
            management.add(managementOffset + 4, 2, t("icsf.field.pedigree"),
                    IcsfHex.hex(managementFields, 4, 6), pedigree);
        }

        result.section(header).section(associated).section(uses).section(management);

        // --- summary card ----------------------------------------------------
        boolean mkvpAbsent = kvpType == 0x00 || IcsfHex.isAllZero(kvp, 0, kvp.length);
        MaterialState material = switch (materialState) {
            case 0x00 -> MaterialState.NO_KEY;
            case 0x01 -> MaterialState.CLEAR;
            case 0x02, 0x03 -> MaterialState.ENCRYPTED;
            default -> MaterialState.NOT_DETERMINABLE;
        };

        result.summary(SummaryKey.FAMILY, TokenFamily.SYM_VARIABLE, t("icsf.family.variable"))
                .summary(SummaryKey.SCOPE, internal ? Scope.INTERNAL : Scope.EXTERNAL,
                        t(internal ? "icsf.scope.internalShort" : "icsf.scope.externalShort"))
                .summary(SummaryKey.ALGORITHM, algorithmValue)
                .summary(SummaryKey.KEY_TYPE, keyTypeName)
                .summary(SummaryKey.KEY_LENGTH, String.valueOf(payloadBits),
                        t("icsf.length.payloadBits", payloadBits))
                .summary(SummaryKey.EFFECTIVE_STRENGTH, EffectiveStrength.NOT_APPLICABLE)
                .summary(SummaryKey.MATERIAL_STATE, material, stateText(materialState))
                .summary(SummaryKey.WRAPPING, wrapValue, SymmetricFixedTokenParser.wrapLabel(wrapValue))
                .summary(SummaryKey.CONTROL_VECTOR, CvState.NOT_APPLICABLE, t("icsf.cvState.variable"))
                .summary(SummaryKey.TVV, TvvState.NOT_APPLICABLE, t("icsf.tvv.notApplicable"))
                .summary(SummaryKey.MKVP, mkvpAbsent ? MkvpState.ABSENT : MkvpState.PRESENT)
                .summary(SummaryKey.PROTECTION, wrapValue, t("icsf.protection.under",
                        SymmetricFixedTokenParser.wrapLabel(wrapValue), kvpText(kvpType)))
                .summary(SummaryKey.PAYLOAD_LENGTH, String.valueOf(payloadBits),
                        t("icsf.length.bits", payloadBits))
                .summary(SummaryKey.ALLOWED_USES, useSummary.isEmpty() ? "SEE_FLAGS" : "DECODED",
                        joined(useSummary))
                .summary(SummaryKey.EXPORTABILITY, exportability, exportDetail)
                .summary(SummaryKey.PEDIGREE, pedigreePresent ? "PRESENT" : "ABSENT", pedigree);

        if (keyNameLength > 0) {
            result.summary(SummaryKey.KEY_NAME, "PRESENT",
                    IcsfText.raw(SymmetricFixedTokenParser.asciiOrHex(keyName)));
        }

        IcsfProvenance.applySymmetric(result, origin,
                mkvpAbsent ? MkvpState.ABSENT : MkvpState.PRESENT, TvvState.NOT_APPLICABLE, internal);
        return result;
    }

    /** Minimal summary shared by every null token, so statistics can still count them. */
    static ParseResult nullSummary(ParseResult result, IcsfText description) {
        return result.summary(SummaryKey.FAMILY, result.tokenFamily(), description)
                .summary(SummaryKey.SCOPE, Scope.NULL, t("icsf.scope.nullToken"))
                .summary(SummaryKey.ALGORITHM, Algorithm.NONE)
                .summary(SummaryKey.KEY_TYPE, "NONE", t("icsf.keyType.none"))
                .summary(SummaryKey.KEY_LENGTH, "NOT_APPLICABLE")
                .summary(SummaryKey.EFFECTIVE_STRENGTH, EffectiveStrength.NOT_APPLICABLE)
                .summary(SummaryKey.MATERIAL_STATE, MaterialState.NO_KEY)
                .summary(SummaryKey.WRAPPING, WrapMethod.NOT_APPLICABLE)
                .summary(SummaryKey.CONTROL_VECTOR, CvState.NOT_APPLICABLE)
                .summary(SummaryKey.TVV, TvvState.NOT_APPLICABLE)
                .summary(SummaryKey.MKVP, MkvpState.NOT_APPLICABLE)
                .summary(SummaryKey.EXPORTABILITY, Exportability.NOT_APPLICABLE,
                        t("icsf.export.noKeyToExport"));
    }

    /** Joins already-translatable parts, resolving each when the report is rendered. */
    private static IcsfText joined(List<IcsfText> parts) {
        if (parts.isEmpty()) return t("icsf.uses.seeFlags");
        if (parts.size() == 1) return parts.get(0);
        IcsfText joined = parts.get(0);
        for (int index = 1; index < parts.size(); index++) {
            joined = t("icsf.join.commaSeparated", joined, parts.get(index));
        }
        return joined;
    }

    private static IcsfText stateText(int state) {
        return KEY_MATERIAL_STATE.containsKey(state)
                ? t(KEY_MATERIAL_STATE.get(state)) : t("icsf.value.reserved");
    }

    private static IcsfText kvpText(int kvpType) {
        return KVP_TYPE.containsKey(kvpType) ? t(KVP_TYPE.get(kvpType)) : t("icsf.value.reserved");
    }

    private static String keyTypeName(int algorithm, int keyType) {
        return switch (algorithm) {
            case 0x02 -> KEY_TYPE_AES.getOrDefault(keyType,
                    String.format(java.util.Locale.ROOT, "0x%04X", keyType));
            case 0x03 -> keyType == 0x0002 ? "MAC"
                    : String.format(java.util.Locale.ROOT, "0x%04X", keyType);
            case 0x01 -> keyType == 0x0008 ? "DESUSECV"
                    : String.format(java.util.Locale.ROOT, "0x%04X", keyType);
            default -> String.format(java.util.Locale.ROOT, "0x%04X", keyType);
        };
    }

    private static IcsfText algorithmLabel(Algorithm algorithm) {
        return switch (algorithm) {
            case DES_TDES -> IcsfText.raw("DES");
            case AES -> IcsfText.raw("AES");
            case HMAC -> IcsfText.raw("HMAC");
            default -> t("icsf.value.reserved");
        };
    }

    /** Decodes the key-usage fields for the commonest key types. */
    private static List<IcsfText> decodeUsageFields(int algorithm, String keyTypeName, int count,
                                                    byte[] usageFields, IcsfSection section) {
        List<IcsfText> uses = new ArrayList<>();
        if (algorithm == 0x03 || (algorithm == 0x02 && "MAC".equals(keyTypeName))) {
            // HMAC (Table 620) and, by extension, AES MAC share generate/verify semantics.
            if (count >= 1) {
                int high = high(usageFields, 1);
                boolean generate = IcsfHex.bit(high, 0);
                boolean verify = IcsfHex.bit(high, 1);
                section.add(t("icsf.use.generate"), generate);
                section.add(t("icsf.use.verify"), verify);
                if (generate) uses.add(t("icsf.use.generateMac"));
                if (verify) uses.add(t("icsf.use.verifyMac"));
            }
            if (count >= 2 && algorithm == 0x03) {
                int high = high(usageFields, 2);
                String[] hashes = {"SHA-1", "SHA-224", "SHA-256", "SHA-384", "SHA-512"};
                for (int index = 0; index < hashes.length; index++) {
                    if (IcsfHex.bit(high, index)) {
                        section.add(t("icsf.use.permittedHash", hashes[index]), true);
                    }
                }
            }
        } else if (algorithm == 0x02 && "CIPHER".equals(keyTypeName)) {
            // Table 628
            if (count >= 1) {
                int high = high(usageFields, 1);
                boolean encrypt = IcsfHex.bit(high, 0);
                boolean decrypt = IcsfHex.bit(high, 1);
                boolean translate = IcsfHex.bit(high, 2);
                section.add(t("icsf.use.encrypt"), encrypt);
                section.add(t("icsf.use.decrypt"), decrypt);
                section.add(t("icsf.use.dataTranslateOnly"), translate);
                if (encrypt) uses.add(t("icsf.use.encryption"));
                if (decrypt) uses.add(t("icsf.use.decryption"));
                if (translate) uses.add(t("icsf.use.dataTranslate"));
            }
            if (count >= 2) {
                Map<Integer, String> modes = Map.ofEntries(
                        Map.entry(0x00, "CBC"), Map.entry(0x01, "ECB"), Map.entry(0x02, "CFB"),
                        Map.entry(0x03, "OFB"), Map.entry(0x04, "GCM"), Map.entry(0x05, "XTS"),
                        Map.entry(0x06, "FF1"), Map.entry(0x07, "FF2"), Map.entry(0x08, "FF2.1"));
                int mode = high(usageFields, 2);
                section.add(t("icsf.use.cipherMode"), true,
                        mode == 0xFF ? t("icsf.value.anyMode")
                                : modes.containsKey(mode) ? IcsfText.raw(modes.get(mode))
                                        : t("icsf.value.reserved"));
            }
        } else if (algorithm == 0x02 && ("EXPORTER".equals(keyTypeName) || "IMPORTER".equals(keyTypeName))) {
            // Table 627 (KEK). These are mnemonics, not words.
            int high = high(usageFields, 1);
            String[] names = "EXPORTER".equals(keyTypeName)
                    ? new String[]{"EXPORT", "TRANSLAT", "GEN-OPEX", "GEN-IMEX", "GEN-EXEX", "GEN-PUB"}
                    : new String[]{"IMPORT", "TRANSLAT", "GEN-OPIM", "GEN-IMEX", "GEN-IMIM", "GEN-PUB"};
            for (int index = 0; index < names.length; index++) {
                if (IcsfHex.bit(high, index)) {
                    section.add(IcsfText.raw(names[index]), true);
                    uses.add(IcsfText.raw(names[index]));
                }
            }
            if (count >= 3) {
                String[] algorithms = {"DES", "AES", "HMAC", "RSA", "ECC", "QSA"};
                List<String> wrapped = onNames(high(usageFields, 3), algorithms);
                if (!wrapped.isEmpty()) {
                    section.add(t("icsf.use.mayWrapAlgorithms"), true,
                            IcsfText.raw(String.join(", ", wrapped)));
                }
            }
            if (count >= 4) {
                String[] classes = {"DATA", "KEK", "PIN", "DERIVATION", "CARD", "CVAR"};
                List<String> wrapped = onNames(high(usageFields, 4), classes);
                if (!wrapped.isEmpty()) {
                    section.add(t("icsf.use.mayWrapClasses"), true,
                            IcsfText.raw(String.join(", ", wrapped)));
                }
            }
        }
        return uses;
    }

    private static List<String> onNames(int value, String[] names) {
        List<String> on = new ArrayList<>();
        for (int index = 0; index < names.length; index++) {
            if (IcsfHex.bit(value, index)) on.add(names[index]);
        }
        return on;
    }

    /** High byte of the 1-based usage field {@code index}. */
    private static int high(byte[] usageFields, int index) {
        int offset = (index - 1) * 2;
        return offset < usageFields.length ? usageFields[offset] & 0xFF : 0;
    }
}
