package com.cryptocarver.model.process.handlers;

import com.cryptocarver.crypto.AesDukpt;
import com.cryptocarver.crypto.CheckDigitCalculator;
import com.cryptocarver.crypto.DukptKsn;
import com.cryptocarver.crypto.EMVOperations;
import com.cryptocarver.crypto.EmvSecureMessaging;
import com.cryptocarver.crypto.EmvOdaOperations;
import com.cryptocarver.crypto.EmvTlv;
import com.cryptocarver.crypto.MastercardDataStorage;
import com.cryptocarver.crypto.PaymentOperations;
import com.cryptocarver.crypto.PinBlockFormat;
import com.cryptocarver.crypto.ThalesKeyBlockOperations;
import com.cryptocarver.crypto.ThalesLmkOperations;
import com.cryptocarver.crypto.VisaHceOperations;
import com.cryptocarver.model.process.ExecutionContext;
import com.cryptocarver.model.process.FlowValue;
import com.cryptocarver.model.process.NodeCatalog;
import com.cryptocarver.model.process.NodeDescriptor;
import com.cryptocarver.model.process.NodeParameter;
import com.cryptocarver.model.process.ParameterKind;
import com.cryptocarver.model.process.ProcessDefinition;
import com.cryptocarver.model.process.ProcessNodeHandler;
import com.cryptocarver.model.process.Representation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Declarative Process Designer adapter for the Phase 5B.2b payment facades. */
public final class PaymentOperationsNodeHandler implements ProcessNodeHandler {
    public static final Set<String> TYPES = Set.of(
            "PIN_BLOCK_ENCODE", "PIN_BLOCK_DECODE", "PIN_BLOCK_TRANSLATE",
            "CVV_GENERATE", "CVV_VERIFY", "DCVV_GENERATE", "DCVV_VERIFY",
            "PVV_GENERATE", "PVV_VERIFY", "IBM3624_OFFSET",
            "DUKPT_TDES_DERIVE", "DUKPT_AES_DERIVE", "DUKPT_PIN_CRYPT",
            "EMV_ICC_MASTER_KEY", "EMV_SESSION_KEY", "EMV_ARQC_GENERATE", "EMV_ARQC_VERIFY", "EMV_ARPC",
            "EMV_SM_CARD_KEY", "EMV_SM_SESSION_KEY", "EMV_SM_PIN", "EMV_SM_MAC",
            "VISA_HCE_LUK", "VISA_HCE_MSD", "VISA_HCE_QVSDC",
            "MC_DS_PARTIAL_KEY", "MC_DS_DIGEST", "MC_DS_SUMMARY",
            "EMV_TLV_PARSE", "TRACK2_ENCODE", "TRACK2_PARSE",
            "EMV_ODA_STATIC_DATA", "EMV_ODA_RECOVER_ISSUER_KEY", "EMV_ODA_RECOVER_ICC_KEY",
            "EMV_ODA_VERIFY_SDA", "EMV_ODA_VERIFY_DDA", "EMV_ODA_VERIFY_CDA",
            "EMV_ODA_SIGN_SSAD", "EMV_ODA_SIGN_SDAD",
            "THALES_LMK_ENCRYPT", "THALES_LMK_DECRYPT", "THALES_LMK_DESCRIBE",
            "THALES_LMK_LOOKUP", "THALES_KCV",
            "THALES_KEY_BLOCK_PARSE", "THALES_KEY_BLOCK_HEADER");

    private static final List<String> SM_SCHEMES = List.of("MASTERCARD", "VISA");
    private static final Set<Representation> TEXT = Set.of(Representation.TEXT_UTF8);
    private static final Set<Representation> HEX = Set.of(Representation.HEX);
    private static final List<String> PIN_FORMATS = PinBlockFormat.displayNames();
    private static final List<String> TDES_USAGES = List.of("PIN_ENCRYPTION", "MAC_REQUEST", "MAC_RESPONSE", "DATA_ENCRYPTION");
    private static final List<String> AES_USAGES = List.of(
            "PIN_ENCRYPTION", "MAC_GENERATION", "MAC_VERIFICATION", "MAC_BOTH_WAYS",
            "DATA_ENCRYPTION_ENCRYPT", "DATA_ENCRYPTION_DECRYPT", "DATA_ENCRYPTION_BOTH_WAYS",
            "KEY_ENCRYPTION", "KEY_DERIVATION");
    private static final List<String> AES_KEY_TYPES = List.of("AES128", "AES192", "AES256");
    /** payShield 10K Host Programmer's Manual clause 7.2.3: the variant schemes. */
    private static final List<String> THALES_SCHEMES = List.of("U", "T", "Z");
    /** Clause 8.5.1: a digit, not the letter an X9.143 block carries. */
    private static final List<String> KEY_BLOCK_VERSIONS = List.of("0", "1");

    @Override
    public Set<String> supportedTypes() { return TYPES; }

    @Override
    public List<PortDefinition> inputPorts(ProcessDefinition.Node node) {
        return switch (node.type.toUpperCase(Locale.ROOT)) {
            case "PIN_BLOCK_ENCODE" -> List.of(textPort("pin"), textPort("pan"));
            case "PIN_BLOCK_DECODE" -> List.of(hexPort("pinBlock"), textPort("pan"));
            case "PIN_BLOCK_TRANSLATE" -> List.of(hexPort("pinBlock"), textPort("pan"));
            case "CVV_GENERATE" -> List.of(hexPort("cvkA"), hexPort("cvkB"), textPort("pan"), textPort("expiry"), textPort("serviceCode"));
            case "CVV_VERIFY" -> List.of(hexPort("cvkA"), hexPort("cvkB"), textPort("pan"), textPort("expiry"), textPort("serviceCode"), textPort("inputCvv"));
            case "DCVV_GENERATE" -> List.of(hexPort("cvkA"), hexPort("cvkB"), textPort("pan"), textPort("panSeq"), textPort("expiry"), textPort("serviceCode"), textPort("atc"));
            case "DCVV_VERIFY" -> List.of(hexPort("cvkA"), hexPort("cvkB"), textPort("pan"), textPort("panSeq"), textPort("expiry"), textPort("serviceCode"), textPort("atc"), textPort("inputCvv"));
            case "PVV_GENERATE" -> List.of(textPort("pin"), textPort("pan"), hexPort("pvk"), textPort("pvki"));
            case "PVV_VERIFY" -> List.of(textPort("pin"), textPort("pan"), hexPort("pvk"), textPort("pvki"), textPort("pvv"));
            case "IBM3624_OFFSET" -> List.of(textPort("pin"), textPort("pan"), hexPort("pvk"), hexPort("decTable"));
            case "DUKPT_TDES_DERIVE" -> List.of(hexPort("ipek"), hexPort("ksn"));
            case "DUKPT_AES_DERIVE" -> List.of(hexPort("bdk"), hexPort("ksn"));
            case "DUKPT_PIN_CRYPT" -> List.of(hexPort("bdk"), hexPort("ksn"), hexPort("pinBlock"));
            case "EMV_ICC_MASTER_KEY" -> List.of(hexPort("imk"), textPort("pan"), textPort("panSequence"));
            case "EMV_SESSION_KEY" -> List.of(hexPort("mkac"), hexPort("atc"), hexPort("un"));
            case "EMV_ARQC_GENERATE" -> List.of(hexPort("sk"), hexPort("transactionData"));
            case "EMV_ARQC_VERIFY" -> List.of(hexPort("sk"), hexPort("arqc"), hexPort("transactionData"));
            case "EMV_ARPC" -> List.of(hexPort("sk"), hexPort("arqc"), textPort("arc"), hexPort("csu"));
            case "EMV_SM_CARD_KEY" -> List.of(hexPort("smMk"), textPort("smPanSeq"));
            case "EMV_SM_SESSION_KEY" -> List.of(hexPort("smUdk"), hexPort("smAc"), hexPort("atc"));
            case "EMV_SM_PIN" -> List.of(hexPort("sk"), textPort("pin"), hexPort("smUdkEnc"));
            case "EMV_SM_MAC" -> List.of(hexPort("sk"), hexPort("smHeader"), hexPort("atc"), hexPort("smAc"), hexPort("smData"));
            case "VISA_HCE_LUK" -> List.of(hexPort("smUdk"));
            case "MC_DS_PARTIAL_KEY" -> List.of(hexPort("dsId"));
            case "MC_DS_DIGEST" -> List.of(hexPort("dsId"), hexPort("dsOperatorId"), hexPort("dsInput"));
            case "MC_DS_SUMMARY" -> List.of(hexPort("dsId"), hexPort("dsSummary1"), hexPort("dsUn"), hexPort("un"));
            case "VISA_HCE_MSD" -> List.of(hexPort("luk"), hexPort("atc"), hexPort("deviceType"));
            case "VISA_HCE_QVSDC" -> List.of(hexPort("luk"), hexPort("terminalData"), hexPort("iccData"));
            case "EMV_TLV_PARSE" -> List.of(hexPort("input"));
            case "TRACK2_ENCODE" -> List.of(textPort("pan"), textPort("expiry"), textPort("serviceCode"), textPort("discretionary"));
            case "TRACK2_PARSE" -> List.of(textPort("track2"));
            case "EMV_ODA_STATIC_DATA" -> List.of(textPort("records"), hexPort("aip"), hexPort("sdaTagList"));
            case "EMV_ODA_RECOVER_ISSUER_KEY" -> List.of(hexPort("certificate"), hexPort("remainder"),
                    hexPort("keyExponent"), hexPort("caModulus"), hexPort("caExponent"), textPort("pan"));
            case "EMV_ODA_RECOVER_ICC_KEY" -> List.of(hexPort("certificate"), hexPort("remainder"),
                    hexPort("keyExponent"), hexPort("issuerModulus"), hexPort("issuerExponent"),
                    hexPort("staticData"), textPort("pan"));
            case "EMV_ODA_VERIFY_SDA" -> List.of(hexPort("ssad"), hexPort("issuerModulus"),
                    hexPort("issuerExponent"), hexPort("staticData"));
            case "EMV_ODA_VERIFY_DDA" -> List.of(hexPort("sdad"), hexPort("iccModulus"),
                    hexPort("iccExponent"), hexPort("terminalData"));
            case "EMV_ODA_VERIFY_CDA" -> List.of(hexPort("sdad"), hexPort("iccModulus"),
                    hexPort("iccExponent"), hexPort("unpredictableNumber"), hexPort("cid"),
                    hexPort("transactionData"));
            case "EMV_ODA_SIGN_SSAD" -> List.of(hexPort("issuerModulus"), hexPort("issuerPrivateExponent"),
                    hexPort("dataAuthenticationCode"), hexPort("staticData"));
            case "EMV_ODA_SIGN_SDAD" -> List.of(hexPort("iccModulus"), hexPort("iccPrivateExponent"),
                    hexPort("iccDynamicData"), hexPort("terminalData"));
            case "THALES_LMK_ENCRYPT" -> List.of(hexPort("clearKey"), hexPort("lmk"));
            case "THALES_LMK_DECRYPT", "THALES_LMK_DESCRIBE" -> List.of(hexPort("cryptogram"), hexPort("lmk"));
            case "THALES_LMK_LOOKUP" -> List.of(hexPort("cryptogram"), hexPort("lmk"), textPort("checkValue"));
            case "THALES_KCV" -> List.of(hexPort("clearKey"));
            case "THALES_KEY_BLOCK_PARSE" -> List.of(textPort("keyBlock"));
            case "THALES_KEY_BLOCK_HEADER" -> List.of();
            default -> List.of();
        };
    }

    private static PortDefinition textPort(String name) { return new PortDefinition(name, TEXT, false); }
    private static PortDefinition hexPort(String name) { return new PortDefinition(name, HEX, false); }

    @Override
    public Representation outputRepresentation(ProcessDefinition.Node node, Map<String, Representation> inputs) {
        return switch (node.type.toUpperCase(Locale.ROOT)) {
            case "PIN_BLOCK_ENCODE", "PIN_BLOCK_TRANSLATE", "DUKPT_TDES_DERIVE", "DUKPT_AES_DERIVE", "DUKPT_PIN_CRYPT",
                    "EMV_ICC_MASTER_KEY", "EMV_SESSION_KEY", "EMV_ARQC_GENERATE", "EMV_ARPC",
                    "EMV_SM_CARD_KEY", "EMV_SM_SESSION_KEY", "EMV_SM_PIN", "EMV_SM_MAC",
                    "VISA_HCE_LUK", "VISA_HCE_QVSDC", "MC_DS_PARTIAL_KEY", "MC_DS_DIGEST", "MC_DS_SUMMARY",
                    "EMV_ODA_STATIC_DATA", "EMV_ODA_SIGN_SSAD", "EMV_ODA_SIGN_SDAD",
                    "THALES_LMK_ENCRYPT", "THALES_LMK_DECRYPT", "THALES_KCV" -> Representation.HEX;
            case "PIN_BLOCK_DECODE", "CVV_GENERATE", "CVV_VERIFY", "DCVV_GENERATE", "DCVV_VERIFY", "PVV_GENERATE", "PVV_VERIFY",
                    "IBM3624_OFFSET", "EMV_ARQC_VERIFY", "VISA_HCE_MSD", "EMV_TLV_PARSE", "TRACK2_ENCODE", "TRACK2_PARSE",
                    "EMV_ODA_RECOVER_ISSUER_KEY", "EMV_ODA_RECOVER_ICC_KEY", "EMV_ODA_VERIFY_SDA",
                    "EMV_ODA_VERIFY_DDA", "EMV_ODA_VERIFY_CDA",
                    "THALES_LMK_DESCRIBE", "THALES_LMK_LOOKUP",
                    "THALES_KEY_BLOCK_PARSE", "THALES_KEY_BLOCK_HEADER" -> Representation.TEXT_UTF8;
            default -> Representation.BINARY;
        };
    }

    @Override
    public void validateConfiguration(ProcessDefinition.Node node) {
        String type = node.type.toUpperCase(Locale.ROOT);
        for (String key : List.of("cvkA", "cvkB", "pvk", "decTable", "ipek", "bdk", "ksn", "pinBlock", "imk", "mkac", "atc", "un", "sk", "arqc", "csu", "transactionData",
                "smMk", "smUdk", "smUdkEnc", "smAc", "smHeader", "smData", "luk", "deviceType", "terminalData", "iccData", "dsId", "dsOperatorId", "dsInput",
                "certificate", "remainder", "keyExponent", "caModulus", "caExponent", "issuerModulus", "issuerExponent",
                "iccModulus", "iccExponent", "issuerPrivateExponent", "iccPrivateExponent", "staticData", "aip",
                "sdaTagList", "ssad", "sdad", "terminalData", "unpredictableNumber", "cid", "dataAuthenticationCode",
                "iccDynamicData", "lmk", "clearKey", "cryptogram")) {
            validateConfiguredHex(node, key);
        }
        switch (type) {
            case "PIN_BLOCK_ENCODE" -> {
                require(node, "pin"); pinFormat(node, "format");
                if (PinBlockFormat.fromName(setting(node, "format", PIN_FORMATS.get(0))).usesPan()) require(node, "pan");
                decimal(node, "pin", 4, 12); pan(node, "pan");
            }
            case "PIN_BLOCK_DECODE" -> {
                require(node, "pinBlock"); pinFormat(node, "format");
                if (PinBlockFormat.fromName(setting(node, "format", PIN_FORMATS.get(0))).usesPan()) require(node, "pan");
                pan(node, "pan"); pinBlock(node, "pinBlock", setting(node, "format", PIN_FORMATS.get(0)));
            }
            case "PIN_BLOCK_TRANSLATE" -> {
                require(node, "pinBlock"); pan(node, "pan");
                pinFormat(node, "sourceFormat"); pinFormat(node, "targetFormat");
                if (PinBlockFormat.fromName(setting(node, "sourceFormat", PIN_FORMATS.get(0))).usesPan()
                        || PinBlockFormat.fromName(setting(node, "targetFormat", PIN_FORMATS.get(0))).usesPan()) require(node, "pan");
                pinBlock(node, "pinBlock", setting(node, "sourceFormat", PIN_FORMATS.get(0)));
            }
            case "CVV_GENERATE" -> { commonCvv(node); }
            case "CVV_VERIFY" -> { commonCvv(node); require(node, "inputCvv"); decimal(node, "inputCvv", 3, 3); }
            case "DCVV_GENERATE" -> { commonDcvv(node); }
            case "DCVV_VERIFY" -> { commonDcvv(node); require(node, "inputCvv"); decimal(node, "inputCvv", 3, 3); }
            case "PVV_GENERATE" -> { commonPvv(node); }
            case "PVV_VERIFY" -> { commonPvv(node); require(node, "pvv"); decimal(node, "pvv", 4, 6); }
            case "IBM3624_OFFSET" -> {
                require(node, "pin"); require(node, "pan"); require(node, "pvk"); require(node, "decTable");
                decimal(node, "pin", 4, 16); pan(node, "pan"); hexLength(node, "decTable", 8);
            }
            case "DUKPT_TDES_DERIVE" -> { require(node, "ipek"); require(node, "ksn"); hexLength(node, "ksn", 10); oneOf(node, "usage", TDES_USAGES); }
            case "DUKPT_AES_DERIVE" -> { require(node, "bdk"); require(node, "ksn"); hexLength(node, "ksn", 12); oneOf(node, "usage", AES_USAGES); oneOf(node, "outputType", AES_KEY_TYPES); }
            case "DUKPT_PIN_CRYPT" -> { require(node, "bdk"); require(node, "ksn"); require(node, "pinBlock"); hexLength(node, "ksn", 12); hexLength(node, "pinBlock", 16); oneOf(node, "outputType", AES_KEY_TYPES); }
            case "EMV_ICC_MASTER_KEY" -> { require(node, "imk"); require(node, "pan"); pan(node, "pan"); decimal(node, "panSequence", 2, 2); }
            case "EMV_SESSION_KEY" -> { require(node, "mkac"); require(node, "atc"); require(node, "un"); hexLength(node, "atc", 2); hexLength(node, "un", 4); }
            case "EMV_ARQC_GENERATE" -> { require(node, "sk"); require(node, "transactionData"); positive(node, "paddingMethod", 1, 2); }
            case "EMV_ARQC_VERIFY" -> { require(node, "sk"); require(node, "arqc"); require(node, "transactionData"); hexLength(node, "arqc", 8); positive(node, "paddingMethod", 1, 2); }
            case "EMV_SM_CARD_KEY" -> { require(node, "smMk"); require(node, "smPanSeq"); decimal(node, "smPanSeq", 16, 16); }
            case "EMV_SM_SESSION_KEY" -> {
                require(node, "smUdk"); oneOf(node, "smScheme", SM_SCHEMES);
                if ("VISA".equalsIgnoreCase(setting(node, "smScheme", "MASTERCARD"))) { require(node, "atc"); hexLength(node, "atc", 2); }
                else { require(node, "smAc"); hexLength(node, "smAc", 8); positive(node, "smCommandNumber", 0, 255); }
            }
            case "EMV_SM_PIN" -> {
                require(node, "sk"); require(node, "pin"); decimal(node, "pin", 4, 12); oneOf(node, "smScheme", SM_SCHEMES);
                if ("VISA".equalsIgnoreCase(setting(node, "smScheme", "MASTERCARD"))) require(node, "smUdkEnc");
            }
            case "VISA_HCE_LUK" -> {
                require(node, "smUdk"); require(node, "hceYear"); require(node, "hceHours"); require(node, "hceCounter");
                decimal(node, "hceYear", 1, 2); decimal(node, "hceHours", 4, 4); decimal(node, "hceCounter", 2, 2);
            }
            case "MC_DS_PARTIAL_KEY" -> require(node, "dsId");
            case "MC_DS_SUMMARY" -> {
                for (String key : List.of("dsId", "dsSummary1", "dsAmount", "dsCurrency", "dsRcp", "dsGac", "dsUn", "un")) require(node, key);
                hexLength(node, "dsSummary1", 8); hexLength(node, "dsUn", 4); hexLength(node, "un", 4);
                decimal(node, "dsAmount", 12, 12); decimal(node, "dsCurrency", 3, 4);
            }
            case "MC_DS_DIGEST" -> { require(node, "dsId"); require(node, "dsOperatorId"); require(node, "dsInput"); hexLength(node, "dsOperatorId", 8); hexLength(node, "dsInput", 8); }
            case "VISA_HCE_MSD" -> { require(node, "luk"); require(node, "atc"); require(node, "deviceType"); hexLength(node, "atc", 2); hexLength(node, "deviceType", 8); }
            case "VISA_HCE_QVSDC" -> { require(node, "luk"); require(node, "terminalData"); require(node, "iccData"); }
            case "EMV_SM_MAC" -> { require(node, "sk"); require(node, "smHeader"); require(node, "atc"); require(node, "smAc"); hexLength(node, "smHeader", 5); hexLength(node, "atc", 2); hexLength(node, "smAc", 8); }
            case "EMV_ARPC" -> { require(node, "sk"); require(node, "arqc"); oneOf(node, "method", List.of("1", "2")); if ("1".equals(setting(node, "method", "1"))) require(node, "arc"); }
            case "EMV_TLV_PARSE" -> require(node, "input");
            case "TRACK2_ENCODE" -> { require(node, "pan"); require(node, "expiry"); require(node, "serviceCode"); pan(node, "pan"); decimal(node, "expiry", 4, 4); decimal(node, "serviceCode", 3, 3); }
            case "TRACK2_PARSE" -> require(node, "track2");
            // Offline data authentication. The PAN is deliberately not required:
            // Book 2 makes the issuer identifier and PAN checks conditional on the
            // terminal having read one, and a bench often has only the certificate.
            case "EMV_ODA_STATIC_DATA" -> require(node, "records");
            case "EMV_ODA_RECOVER_ISSUER_KEY" -> { require(node, "certificate"); require(node, "caModulus"); require(node, "caExponent"); }
            case "EMV_ODA_RECOVER_ICC_KEY" -> { require(node, "certificate"); require(node, "issuerModulus"); require(node, "issuerExponent"); }
            case "EMV_ODA_VERIFY_SDA" -> { require(node, "ssad"); require(node, "issuerModulus"); require(node, "issuerExponent"); }
            case "EMV_ODA_VERIFY_DDA" -> { require(node, "sdad"); require(node, "iccModulus"); require(node, "iccExponent"); }
            case "EMV_ODA_VERIFY_CDA" -> { require(node, "sdad"); require(node, "iccModulus"); require(node, "iccExponent"); require(node, "unpredictableNumber"); hexLength(node, "unpredictableNumber", 4); }
            case "EMV_ODA_SIGN_SSAD" -> { require(node, "issuerModulus"); require(node, "issuerPrivateExponent"); hexLength(node, "dataAuthenticationCode", 2); }
            case "EMV_ODA_SIGN_SDAD" -> { require(node, "iccModulus"); require(node, "iccPrivateExponent"); require(node, "iccDynamicData"); }
            case "THALES_LMK_ENCRYPT" -> { require(node, "clearKey"); require(node, "lmk"); thalesKeyType(node); oneOf(node, "scheme", THALES_SCHEMES); }
            case "THALES_LMK_DECRYPT", "THALES_LMK_DESCRIBE" -> { require(node, "cryptogram"); require(node, "lmk"); thalesKeyType(node); oneOf(node, "scheme", THALES_SCHEMES); }
            case "THALES_LMK_LOOKUP" -> { require(node, "cryptogram"); require(node, "lmk"); require(node, "checkValue"); }
            case "THALES_KCV" -> require(node, "clearKey");
            case "THALES_KEY_BLOCK_PARSE" -> require(node, "keyBlock");
            case "THALES_KEY_BLOCK_HEADER" -> { require(node, "keyUsage"); oneOf(node, "versionId", KEY_BLOCK_VERSIONS); }
            default -> throw new IllegalArgumentException("Unsupported payment operation");
        }
    }

    private static void commonCvv(ProcessDefinition.Node node) {
        require(node, "cvkA"); require(node, "cvkB"); require(node, "pan"); require(node, "expiry"); require(node, "serviceCode");
        hexLength(node, "cvkA", 8); hexLength(node, "cvkB", 8); pan(node, "pan"); decimal(node, "expiry", 4, 4); decimal(node, "serviceCode", 3, 3);
    }
    private static void commonDcvv(ProcessDefinition.Node node) {
        commonCvv(node); require(node, "panSeq"); require(node, "atc"); decimal(node, "panSeq", 1, 2); hexDigits(node, "atc", 1, 4);
    }
    private static void commonPvv(ProcessDefinition.Node node) {
        require(node, "pin"); require(node, "pan"); require(node, "pvk"); require(node, "pvki");
        decimal(node, "pin", 4, 4); pan(node, "pan"); decimal(node, "pvki", 1, 1); positive(node, "pvvLength", 4, 6);
    }

    @Override
    public FlowValue execute(ProcessDefinition.Node node, Map<String, FlowValue> inputs, ExecutionContext context) throws Exception {
        try {
            String type = node.type.toUpperCase(Locale.ROOT);
            return switch (type) {
                case "PIN_BLOCK_ENCODE" -> hex(PaymentOperations.encodePinBlock(text(node, inputs, "pin"), textOptional(node, inputs, "pan"), setting(node, "format", PIN_FORMATS.get(0))));
                case "PIN_BLOCK_DECODE" -> text(PaymentOperations.decodePinBlock(hexText(node, inputs, "pinBlock"), textOptional(node, inputs, "pan"), setting(node, "format", PIN_FORMATS.get(0))));
                case "PIN_BLOCK_TRANSLATE" -> hex(PaymentOperations.translatePinBlock(hexText(node, inputs, "pinBlock"), textOptional(node, inputs, "pan"), setting(node, "sourceFormat", PIN_FORMATS.get(0)), setting(node, "targetFormat", PIN_FORMATS.get(0))));
                case "CVV_GENERATE" -> text(PaymentOperations.generateCVV(hexText(node, inputs, "cvkA"), hexText(node, inputs, "cvkB"), text(node, inputs, "pan"), text(node, inputs, "expiry"), text(node, inputs, "serviceCode")));
                case "CVV_VERIFY" -> text(Boolean.toString(PaymentOperations.verifyCVV(hexText(node, inputs, "cvkA"), hexText(node, inputs, "cvkB"), text(node, inputs, "pan"), text(node, inputs, "expiry"), text(node, inputs, "serviceCode"), text(node, inputs, "inputCvv"))));
                case "DCVV_GENERATE" -> text(PaymentOperations.generateDCVV(hexText(node, inputs, "cvkA"), hexText(node, inputs, "cvkB"), text(node, inputs, "pan"), text(node, inputs, "panSeq"), text(node, inputs, "expiry"), text(node, inputs, "serviceCode"), text(node, inputs, "atc")));
                case "DCVV_VERIFY" -> text(Boolean.toString(PaymentOperations.verifyDCVV(hexText(node, inputs, "cvkA"), hexText(node, inputs, "cvkB"), text(node, inputs, "pan"), text(node, inputs, "panSeq"), text(node, inputs, "expiry"), text(node, inputs, "serviceCode"), text(node, inputs, "atc"), text(node, inputs, "inputCvv"))));
                case "PVV_GENERATE" -> text(PaymentOperations.generatePVV(text(node, inputs, "pin"), text(node, inputs, "pan"), hexText(node, inputs, "pvk"), text(node, inputs, "pvki"), integer(node, "pvvLength", 4, 4, 6)));
                case "PVV_VERIFY" -> text(Boolean.toString(PaymentOperations.verifyPVV(text(node, inputs, "pin"), text(node, inputs, "pan"), hexText(node, inputs, "pvk"), text(node, inputs, "pvki"), text(node, inputs, "pvv"), integer(node, "pvvLength", 4, 4, 6))));
                case "IBM3624_OFFSET" -> text(PaymentOperations.generateIBM3624Offset(text(node, inputs, "pin"), text(node, inputs, "pan"), hexText(node, inputs, "pvk"), hexText(node, inputs, "decTable")));
                case "DUKPT_TDES_DERIVE" -> hex(DukptKsn.deriveWorkingKey(hexText(node, inputs, "ipek"), hexText(node, inputs, "ksn"), DukptKsn.TdesKeyUsage.valueOf(setting(node, "usage", "PIN_ENCRYPTION"))).workingKeyHex());
                case "DUKPT_AES_DERIVE" -> hex(AesDukpt.deriveWorkingKey(hexText(node, inputs, "bdk"), hexText(node, inputs, "ksn"), AesDukpt.KeyUsage.valueOf(setting(node, "usage", "PIN_ENCRYPTION")), AesDukpt.KeyType.valueOf(setting(node, "outputType", "AES128"))).workingKeyHex());
                case "DUKPT_PIN_CRYPT" -> hex(AesDukpt.cryptPinBlock(hexText(node, inputs, "bdk"), hexText(node, inputs, "ksn"), AesDukpt.KeyType.valueOf(setting(node, "outputType", "AES128")), hexText(node, inputs, "pinBlock"), Boolean.parseBoolean(setting(node, "decrypt", "false"))));
                case "EMV_ICC_MASTER_KEY" -> hex(EMVOperations.deriveICCMasterKey(hexText(node, inputs, "imk"), text(node, inputs, "pan"), text(node, inputs, "panSequence")));
                case "EMV_SESSION_KEY" -> hex(EMVOperations.deriveSessionKey(hexText(node, inputs, "mkac"), hexText(node, inputs, "atc"), hexText(node, inputs, "un")));
                case "EMV_ARQC_GENERATE" -> hex(EMVOperations.generateARQC(hexText(node, inputs, "sk"), hexText(node, inputs, "transactionData"), integer(node, "paddingMethod", 2, 1, 2)));
                case "EMV_ARQC_VERIFY" -> text(Boolean.toString(EMVOperations.verifyARQC(hexText(node, inputs, "sk"), hexText(node, inputs, "arqc"), hexText(node, inputs, "transactionData"), integer(node, "paddingMethod", 2, 1, 2))));
                case "EMV_ARPC" -> hex("1".equals(setting(node, "method", "1"))
                        ? EMVOperations.generateARPC_Method1(hexText(node, inputs, "sk"), hexText(node, inputs, "arqc"), text(node, inputs, "arc"))
                        : EMVOperations.generateARPC_Method2(hexText(node, inputs, "sk"), hexText(node, inputs, "arqc"), csuOrDefault(hexTextOptional(node, inputs, "csu"))));
                case "EMV_SM_CARD_KEY" -> hex(EmvSecureMessaging.mastercardUdk(hexText(node, inputs, "smMk"), text(node, inputs, "smPanSeq")));
                case "EMV_SM_SESSION_KEY" -> hex("VISA".equalsIgnoreCase(setting(node, "smScheme", "MASTERCARD"))
                        ? EmvSecureMessaging.visaSessionKey(hexText(node, inputs, "smUdk"), hexText(node, inputs, "atc"))
                        : EmvSecureMessaging.mastercardSessionKey(hexText(node, inputs, "smUdk"), hexText(node, inputs, "smAc"),
                                integer(node, "smCommandNumber", 0, 0, 255)));
                case "EMV_SM_PIN" -> hex("VISA".equalsIgnoreCase(setting(node, "smScheme", "MASTERCARD"))
                        ? EmvSecureMessaging.visaEncryptedPin(hexText(node, inputs, "sk"), hexText(node, inputs, "smUdkEnc"), text(node, inputs, "pin"))
                        : EmvSecureMessaging.mastercardEncryptedPin(hexText(node, inputs, "sk"), text(node, inputs, "pin")));
                case "EMV_SM_MAC" -> hex(EmvSecureMessaging.commandMac(hexText(node, inputs, "sk"), hexText(node, inputs, "smHeader"),
                        hexText(node, inputs, "atc"), hexText(node, inputs, "smAc"), hexTextOptional(node, inputs, "smData")));
                case "VISA_HCE_LUK" -> hex(VisaHceOperations.limitedUseKey(hexText(node, inputs, "smUdk"),
                        setting(node, "hceYear", ""), setting(node, "hceHours", ""), setting(node, "hceCounter", "")));
                case "MC_DS_SUMMARY" -> hex(MastercardDataStorage.summary(hexText(node, inputs, "dsId"),
                        hexText(node, inputs, "dsSummary1"), setting(node, "dsAmount", ""), setting(node, "dsCurrency", ""),
                        setting(node, "dsRcp", ""), setting(node, "dsGac", ""), hexText(node, inputs, "dsUn"), hexText(node, inputs, "un")));
                case "MC_DS_PARTIAL_KEY" -> hex(MastercardDataStorage.partialKey(hexText(node, inputs, "dsId")));
                case "MC_DS_DIGEST" -> hex(MastercardDataStorage.owhf2(hexText(node, inputs, "dsId"),
                        hexText(node, inputs, "dsOperatorId"), hexText(node, inputs, "dsInput")));
                case "VISA_HCE_MSD" -> text(VisaHceOperations.msdVerificationValue(hexText(node, inputs, "luk"),
                        hexText(node, inputs, "atc"), hexText(node, inputs, "deviceType")));
                case "VISA_HCE_QVSDC" -> hex(VisaHceOperations.qvsdcCryptogram(hexText(node, inputs, "luk"),
                        hexText(node, inputs, "terminalData"), hexText(node, inputs, "iccData")));
                case "EMV_TLV_PARSE" -> text(EmvTlv.transactionSummary(EmvTlv.analyze(hexText(node, inputs, "input"))));
                case "TRACK2_ENCODE" -> text(PaymentOperations.encodeTrack2(text(node, inputs, "pan"), text(node, inputs, "expiry"), text(node, inputs, "serviceCode"), textOptional(node, inputs, "discretionary")));
                case "TRACK2_PARSE" -> text(PaymentOperations.parseTrack2(text(node, inputs, "track2")));
                case "EMV_ODA_STATIC_DATA" -> hex(EmvOdaOperations.staticDataToBeAuthenticated(
                        records(textOptional(node, inputs, "records")),
                        hexTextOptional(node, inputs, "aip"),
                        hexTextOptional(node, inputs, "sdaTagList")).hex());
                case "EMV_ODA_RECOVER_ISSUER_KEY" -> text(EmvOdaOperations.describe(
                        EmvOdaOperations.recoverIssuerPublicKey(
                                hexText(node, inputs, "certificate"),
                                hexTextOptional(node, inputs, "remainder"),
                                hexTextOptional(node, inputs, "keyExponent"),
                                publicKey(node, inputs, "caModulus", "caExponent"),
                                textOptional(node, inputs, "pan"))));
                case "EMV_ODA_RECOVER_ICC_KEY" -> text(EmvOdaOperations.describe(
                        EmvOdaOperations.recoverIccPublicKey(
                                hexText(node, inputs, "certificate"),
                                hexTextOptional(node, inputs, "remainder"),
                                hexTextOptional(node, inputs, "keyExponent"),
                                publicKey(node, inputs, "issuerModulus", "issuerExponent"),
                                hexTextOptional(node, inputs, "staticData"),
                                textOptional(node, inputs, "pan"))));
                case "EMV_ODA_VERIFY_SDA" -> text(EmvOdaOperations.describe(
                        EmvOdaOperations.verifyStaticApplicationData(
                                hexText(node, inputs, "ssad"),
                                publicKey(node, inputs, "issuerModulus", "issuerExponent"),
                                hexTextOptional(node, inputs, "staticData"))));
                case "EMV_ODA_VERIFY_DDA" -> text(EmvOdaOperations.describe(
                        EmvOdaOperations.verifyDynamicApplicationData(
                                hexText(node, inputs, "sdad"),
                                publicKey(node, inputs, "iccModulus", "iccExponent"),
                                hexTextOptional(node, inputs, "terminalData"))));
                case "EMV_ODA_VERIFY_CDA" -> text(EmvOdaOperations.describe(
                        EmvOdaOperations.verifyCombinedApplicationData(
                                hexText(node, inputs, "sdad"),
                                publicKey(node, inputs, "iccModulus", "iccExponent"),
                                hexText(node, inputs, "unpredictableNumber"),
                                hexTextOptional(node, inputs, "cid"),
                                hexTextOptional(node, inputs, "transactionData"))));
                case "EMV_ODA_SIGN_SSAD" -> hex(EmvOdaOperations.signStaticApplicationData(
                        privateKey(node, inputs, "issuerModulus", "issuerPrivateExponent"),
                        hexTextOptional(node, inputs, "dataAuthenticationCode"),
                        hexTextOptional(node, inputs, "staticData")));
                case "EMV_ODA_SIGN_SDAD" -> hex(EmvOdaOperations.signDynamicApplicationData(
                        privateKey(node, inputs, "iccModulus", "iccPrivateExponent"),
                        hexText(node, inputs, "iccDynamicData"),
                        hexTextOptional(node, inputs, "terminalData")));
                case "THALES_LMK_ENCRYPT" -> hex(ThalesLmkOperations.encrypt(
                        hexText(node, inputs, "clearKey"), setting(node, "keyType", "000"),
                        thalesScheme(node), lmk(node, inputs),
                        Boolean.parseBoolean(setting(node, "component", "false"))).cryptogram());
                case "THALES_LMK_DECRYPT" -> hex(ThalesLmkOperations.decrypt(
                        hexText(node, inputs, "cryptogram"), setting(node, "keyType", "000"),
                        thalesScheme(node), lmk(node, inputs),
                        Boolean.parseBoolean(setting(node, "component", "false"))).cryptogram());
                case "THALES_LMK_DESCRIBE" -> text(ThalesLmkOperations.describe(
                        ThalesLmkOperations.decrypt(hexText(node, inputs, "cryptogram"),
                                setting(node, "keyType", "000"), thalesScheme(node), lmk(node, inputs),
                                Boolean.parseBoolean(setting(node, "component", "false"))),
                        lmk(node, inputs)));
                case "THALES_LMK_LOOKUP" -> text(ThalesLmkOperations.describe(ThalesLmkOperations.lookup(
                        hexText(node, inputs, "cryptogram"), text(node, inputs, "checkValue"),
                        lmk(node, inputs))));
                case "THALES_KCV" -> hex(ThalesLmkOperations.checkValue(hexText(node, inputs, "clearKey")));
                case "THALES_KEY_BLOCK_PARSE" -> text(ThalesKeyBlockOperations.describe(
                        ThalesKeyBlockOperations.parse(text(node, inputs, "keyBlock"))));
                case "THALES_KEY_BLOCK_HEADER" -> text(ThalesKeyBlockOperations.buildHeader(
                        ThalesKeyBlockOperations.VersionId.of(setting(node, "versionId", "0").charAt(0)),
                        setting(node, "keyUsage", "K0"),
                        setting(node, "algorithm", "T").charAt(0),
                        setting(node, "modeOfUse", "N").charAt(0),
                        setting(node, "keyVersionNumber", "00"),
                        setting(node, "exportability", "N").charAt(0),
                        integer(node, "optionalBlockCount", 0, 0, 99),
                        setting(node, "lmkId", "00"),
                        integer(node, "payloadCharacters", 56, 0, 9983)));
                default -> throw new IllegalArgumentException("Unsupported payment operation");
            };
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Payment operation failed safely.");
        }
    }

    private static String text(ProcessDefinition.Node node, Map<String, FlowValue> inputs, String name) {
        FlowValue value = inputs.get(name);
        if (value != null) {
            if (value.representation() != Representation.TEXT_UTF8) throw new IllegalArgumentException("Payment text input has the wrong representation");
            String rendered = value.render().trim();
            validateRuntimeText(name, rendered);
            return rendered;
        }
        String configured = node.configuration.get(name);
        if (configured == null || configured.isBlank()) throw new IllegalArgumentException("Missing required payment input");
        String rendered = configured.trim();
        validateRuntimeText(name, rendered);
        return rendered;
    }
    private static String textOptional(ProcessDefinition.Node node, Map<String, FlowValue> inputs, String name) {
        FlowValue value = inputs.get(name);
        if (value != null) return text(node, inputs, name);
        return node.configuration.getOrDefault(name, "").trim();
    }
    private static String hexText(ProcessDefinition.Node node, Map<String, FlowValue> inputs, String name) {
        FlowValue value = inputs.get(name);
        if (value != null) {
            if (value.representation() != Representation.HEX) throw new IllegalArgumentException("Payment hexadecimal input has the wrong representation");
            return value.render().trim();
        }
        String configured = node.configuration.get(name);
        if (configured == null || configured.isBlank()) throw new IllegalArgumentException("Missing required payment input");
        return configured.trim();
    }
    /** ARPC method 2 always carries a 4-byte CSU; an absent one means no update. */
    private static String csuOrDefault(String csu) {
        return csu == null || csu.isBlank() ? "00000000" : csu;
    }
    private static String hexTextOptional(ProcessDefinition.Node node, Map<String, FlowValue> inputs, String name) {
        if (!inputs.containsKey(name) && !node.configuration.containsKey(name)) return "";
        return hexText(node, inputs, name);
    }
    /** AFL records, one per line or comma separated; order is what the terminal read. */
    private static List<String> records(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split("[,;\\s]+")).filter(s -> !s.isBlank()).toList();
    }
    private static EmvOdaOperations.RsaPublicKey publicKey(ProcessDefinition.Node node, Map<String, FlowValue> inputs,
                                                           String modulusKey, String exponentKey) {
        return EmvOdaOperations.RsaPublicKey.of(hexText(node, inputs, modulusKey), hexText(node, inputs, exponentKey));
    }
    private static EmvOdaOperations.RsaPrivateKey privateKey(ProcessDefinition.Node node, Map<String, FlowValue> inputs,
                                                             String modulusKey, String exponentKey) {
        return new EmvOdaOperations.RsaPrivateKey(hexText(node, inputs, modulusKey), hexText(node, inputs, exponentKey));
    }
    private static FlowValue text(String value) { return FlowValue.text(value, StandardCharsets.UTF_8); }
    private static FlowValue hex(String value) { return FlowValue.hex(value.toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)); }

    private static void require(ProcessDefinition.Node node, String key) {
        if ((node.configuration.get(key) == null || node.configuration.get(key).isBlank())
                && !"true".equalsIgnoreCase(node.configuration.get(key + "FromFlow"))
                && !NodeCatalog.isSupplied(node, key)) {
            throw new IllegalArgumentException("Missing required payment input");
        }
    }
    private static String setting(ProcessDefinition.Node node, String key, String defaultValue) {
        String value = node.configuration.get(key); return value == null || value.isBlank() ? defaultValue : value.trim();
    }
    private static int integer(ProcessDefinition.Node node, String key, int defaultValue, int min, int max) {
        int value; try { value = Integer.parseInt(setting(node, key, String.valueOf(defaultValue))); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid payment number"); }
        if (value < min || value > max) throw new IllegalArgumentException("Payment number is out of range"); return value;
    }
    private static void positive(ProcessDefinition.Node node, String key, int min, int max) { integer(node, key, min, min, max); }
    private static void oneOf(ProcessDefinition.Node node, String key, List<String> values) {
        String actual = setting(node, key, values.get(0)); if (values.stream().noneMatch(v -> v.equalsIgnoreCase(actual))) throw new IllegalArgumentException("Invalid payment option");
    }
    private static void pinFormat(ProcessDefinition.Node node, String key) {
        PinBlockFormat.fromName(setting(node, key, PIN_FORMATS.get(0)));
    }
    private static void decimal(ProcessDefinition.Node node, String key, int min, int max) {
        if (node.configuration.containsKey(key) && !decimalValue(node.configuration.get(key), min, max)) throw new IllegalArgumentException("Invalid decimal payment input");
    }
    private static void hexDigits(ProcessDefinition.Node node, String key, int min, int max) {
        if (node.configuration.containsKey(key) && !node.configuration.get(key).matches("[0-9A-Fa-f]{" + min + "," + max + "}"))
            throw new IllegalArgumentException("Invalid hexadecimal payment input");
    }
    private static void pan(ProcessDefinition.Node node, String key) {
        if (!node.configuration.containsKey(key)) return;
        String value = node.configuration.get(key);
        if (!validPan(value)) throw new IllegalArgumentException("Invalid PAN");
    }
    private static boolean decimalValue(String value, int min, int max) {
        return value != null && value.matches("[0-9]{" + min + "," + max + "}");
    }
    private static boolean validPan(String value) {
        return value != null && value.matches("[0-9]{13,19}")
                && CheckDigitCalculator.validateCheckDigit(value, "Luhn (Mod 10)");
    }
    private static void validateRuntimeText(String name, String value) {
        switch (name) {
            case "pan" -> { if (!validPan(value)) throw new IllegalArgumentException("Invalid PAN"); }
            case "pin" -> { if (!decimalValue(value, 4, 16)) throw new IllegalArgumentException("Invalid decimal payment input"); }
            case "expiry" -> { if (!decimalValue(value, 4, 4)) throw new IllegalArgumentException("Invalid decimal payment input"); }
            case "serviceCode" -> { if (!decimalValue(value, 3, 3)) throw new IllegalArgumentException("Invalid decimal payment input"); }
            case "panSeq" -> { if (!decimalValue(value, 1, 2)) throw new IllegalArgumentException("Invalid decimal payment input"); }
            case "atc" -> { if (!decimalValue(value, 1, 16)) throw new IllegalArgumentException("Invalid decimal payment input"); }
            case "pvki" -> { if (!decimalValue(value, 1, 1)) throw new IllegalArgumentException("Invalid decimal payment input"); }
            case "inputCvv" -> { if (!decimalValue(value, 3, 3)) throw new IllegalArgumentException("Invalid decimal payment input"); }
            case "pvv" -> { if (!decimalValue(value, 4, 6)) throw new IllegalArgumentException("Invalid decimal payment input"); }
            case "arc" -> { if (value == null || !value.matches("[0-9A-Za-z]{2,4}")) throw new IllegalArgumentException("Invalid authorization response code"); }
            default -> { }
        }
    }
    private static void validateConfiguredHex(ProcessDefinition.Node node, String key) {
        String value = node.configuration.get(key); if (value != null && !value.isBlank() && !value.matches("(?i)[0-9a-f]+") ) throw new IllegalArgumentException("Invalid payment hexadecimal input");
        if (value != null && (value.length() & 1) != 0) throw new IllegalArgumentException("Invalid payment hexadecimal input");
    }
    private static void hexLength(ProcessDefinition.Node node, String key, int bytes) {
        if (!node.configuration.containsKey(key)) return; if (node.configuration.get(key).length() != bytes * 2) throw new IllegalArgumentException("Invalid payment hexadecimal length");
    }
    private static void pinBlock(ProcessDefinition.Node node, String key, String format) {
        if (!node.configuration.containsKey(key)) return;
        int expected = PinBlockFormat.fromName(format) == PinBlockFormat.ISO4 ? 32 : 16;
        hexLength(node, key, expected / 2);
    }

    private static NodeParameter secret(String key, String label) { return new NodeParameter(key, label, ParameterKind.PASSWORD, List.of(), "", true, null, null); }
    private static NodeParameter combo(String key, String label, List<String> options, String value) { return new NodeParameter(key, label, ParameterKind.COMBO, options, value); }
    private static NodeParameter textParam(String key, String label, String value) { return new NodeParameter(key, label, ParameterKind.TEXT, value); }
    private static NodeParameter number(String key, String label, String value) { return new NodeParameter(key, label, ParameterKind.NUMBER, value); }

    private static List<NodeParameter> params(NodeParameter... values) { return List.of(values); }
    private static NodeDescriptor descriptor(String type, String label, String desc, List<NodeParameter> parameters) {
        return new NodeDescriptor(type, "Payments", "module.process.type.payment." + label, "module.process.desc.payment." + desc, "💳", parameters);
    }

    @Override
    public List<NodeDescriptor> descriptors() {
        List<NodeDescriptor> result = new ArrayList<>();
        result.add(descriptor("PIN_BLOCK_ENCODE", "pinBlockEncode", "pinBlockEncode", params(secret("pin", "module.process.param.payment.pin"), secret("pan", "module.process.param.payment.pan"), combo("format", "module.process.param.payment.format", PIN_FORMATS, PIN_FORMATS.get(0)))));
        result.add(descriptor("PIN_BLOCK_DECODE", "pinBlockDecode", "pinBlockDecode", params(secret("pinBlock", "module.process.param.payment.pinBlock"), secret("pan", "module.process.param.payment.pan"), combo("format", "module.process.param.payment.format", PIN_FORMATS, PIN_FORMATS.get(0)))));
        result.add(descriptor("PIN_BLOCK_TRANSLATE", "pinBlockTranslate", "pinBlockTranslate", params(secret("pinBlock", "module.process.param.payment.pinBlock"), secret("pan", "module.process.param.payment.pan"), combo("sourceFormat", "module.process.param.payment.sourceFormat", PIN_FORMATS, PIN_FORMATS.get(0)), combo("targetFormat", "module.process.param.payment.targetFormat", PIN_FORMATS, PIN_FORMATS.get(0)))));
        result.add(descriptor("CVV_GENERATE", "cvvGenerate", "cvvGenerate", params(cvk("cvkA"), cvk("cvkB"), secret("pan", "module.process.param.payment.pan"), textParam("expiry", "module.process.param.payment.expiry", ""), textParam("serviceCode", "module.process.param.payment.serviceCode", ""))));
        result.add(descriptor("CVV_VERIFY", "cvvVerify", "cvvVerify", params(cvk("cvkA"), cvk("cvkB"), secret("pan", "module.process.param.payment.pan"), textParam("expiry", "module.process.param.payment.expiry", ""), textParam("serviceCode", "module.process.param.payment.serviceCode", ""), secret("inputCvv", "module.process.param.payment.cvv"))));
        result.add(descriptor("DCVV_GENERATE", "dcvvGenerate", "dcvvGenerate", params(cvk("cvkA"), cvk("cvkB"), secret("pan", "module.process.param.payment.pan"), textParam("panSeq", "module.process.param.payment.panSeq", "00"), textParam("expiry", "module.process.param.payment.expiry", ""), textParam("serviceCode", "module.process.param.payment.serviceCode", ""), textParam("atc", "module.process.param.payment.atc", ""))));
        result.add(descriptor("DCVV_VERIFY", "dcvvVerify", "dcvvVerify", params(cvk("cvkA"), cvk("cvkB"), secret("pan", "module.process.param.payment.pan"), textParam("panSeq", "module.process.param.payment.panSeq", "00"), textParam("expiry", "module.process.param.payment.expiry", ""), textParam("serviceCode", "module.process.param.payment.serviceCode", ""), textParam("atc", "module.process.param.payment.atc", ""), secret("inputCvv", "module.process.param.payment.cvv"))));
        result.add(descriptor("PVV_GENERATE", "pvvGenerate", "pvvGenerate", params(secret("pin", "module.process.param.payment.pin"), secret("pan", "module.process.param.payment.pan"), secret("pvk", "module.process.param.payment.pvk"), textParam("pvki", "module.process.param.payment.pvki", "0"), number("pvvLength", "module.process.param.payment.pvvLength", "4"))));
        result.add(descriptor("PVV_VERIFY", "pvvVerify", "pvvVerify", params(secret("pin", "module.process.param.payment.pin"), secret("pan", "module.process.param.payment.pan"), secret("pvk", "module.process.param.payment.pvk"), textParam("pvki", "module.process.param.payment.pvki", "0"), secret("pvv", "module.process.param.payment.pvv"), number("pvvLength", "module.process.param.payment.pvvLength", "4"))));
        result.add(descriptor("IBM3624_OFFSET", "ibm3624Offset", "ibm3624Offset", params(secret("pin", "module.process.param.payment.pin"), secret("pan", "module.process.param.payment.pan"), secret("pvk", "module.process.param.payment.pvk"), secret("decTable", "module.process.param.payment.decTable"))));
        result.add(descriptor("DUKPT_TDES_DERIVE", "dukptTdesDerive", "dukptTdesDerive", params(secret("ipek", "module.process.param.payment.ipek"), secret("ksn", "module.process.param.payment.ksn"), combo("usage", "module.process.param.payment.dukptUsage", TDES_USAGES, "PIN_ENCRYPTION"))));
        result.add(descriptor("DUKPT_AES_DERIVE", "dukptAesDerive", "dukptAesDerive", params(secret("bdk", "module.process.param.payment.bdk"), secret("ksn", "module.process.param.payment.ksn"), combo("usage", "module.process.param.payment.dukptUsage", AES_USAGES, "PIN_ENCRYPTION"), combo("outputType", "module.process.param.payment.keyType", AES_KEY_TYPES, "AES128"))));
        result.add(descriptor("DUKPT_PIN_CRYPT", "dukptPinCrypt", "dukptPinCrypt", params(secret("bdk", "module.process.param.payment.bdk"), secret("ksn", "module.process.param.payment.ksn"), secret("pinBlock", "module.process.param.payment.pinBlock"), combo("outputType", "module.process.param.payment.keyType", AES_KEY_TYPES, "AES128"), new NodeParameter("decrypt", "module.process.param.payment.decrypt", ParameterKind.CHECKBOX, "false"))));
        result.add(descriptor("EMV_ICC_MASTER_KEY", "emvIccMasterKey", "emvIccMasterKey", params(secret("imk", "module.process.param.payment.imk"), secret("pan", "module.process.param.payment.pan"), textParam("panSequence", "module.process.param.payment.panSequence", "00"))));
        result.add(descriptor("EMV_SESSION_KEY", "emvSessionKey", "emvSessionKey", params(secret("mkac", "module.process.param.payment.mkac"), secret("atc", "module.process.param.payment.atc"), secret("un", "module.process.param.payment.un"))));
        result.add(descriptor("EMV_ARQC_GENERATE", "emvArqcGenerate", "emvArqcGenerate", params(secret("sk", "module.process.param.payment.sk"), secret("transactionData", "module.process.param.payment.transactionData"), combo("paddingMethod", "module.process.param.payment.paddingMethod", List.of("1", "2"), "2"))));
        result.add(descriptor("EMV_ARQC_VERIFY", "emvArqcVerify", "emvArqcVerify", params(secret("sk", "module.process.param.payment.sk"), secret("arqc", "module.process.param.payment.arqc"), secret("transactionData", "module.process.param.payment.transactionData"), combo("paddingMethod", "module.process.param.payment.paddingMethod", List.of("1", "2"), "2"))));
        result.add(descriptor("EMV_SM_CARD_KEY", "emvSmCardKey", "emvSmCardKey", params(secret("smMk", "module.process.param.payment.smMk"), textParam("smPanSeq", "module.process.param.payment.smPanSeq", ""))));
        result.add(descriptor("EMV_SM_SESSION_KEY", "emvSmSessionKey", "emvSmSessionKey", params(combo("smScheme", "module.process.param.payment.smScheme", SM_SCHEMES, "MASTERCARD"), secret("smUdk", "module.process.param.payment.smUdk"), secret("smAc", "module.process.param.payment.smAc"), number("smCommandNumber", "module.process.param.payment.smCommandNumber", "0"), secret("atc", "module.process.param.payment.atc"))));
        result.add(descriptor("EMV_SM_PIN", "emvSmPin", "emvSmPin", params(combo("smScheme", "module.process.param.payment.smScheme", SM_SCHEMES, "MASTERCARD"), secret("sk", "module.process.param.payment.sk"), secret("pin", "module.process.param.payment.pin"), secret("smUdkEnc", "module.process.param.payment.smUdkEnc"))));
        result.add(descriptor("EMV_SM_MAC", "emvSmMac", "emvSmMac", params(secret("sk", "module.process.param.payment.sk"), textParam("smHeader", "module.process.param.payment.smHeader", ""), secret("atc", "module.process.param.payment.atc"), secret("smAc", "module.process.param.payment.smAc"), textParam("smData", "module.process.param.payment.smData", ""))));
        result.add(descriptor("MC_DS_PARTIAL_KEY", "mcDsPartialKey", "mcDsPartialKey", params(textParam("dsId", "module.process.param.payment.dsId", ""))));
        result.add(descriptor("MC_DS_SUMMARY", "mcDsSummary", "mcDsSummary", params(textParam("dsId", "module.process.param.payment.dsId", ""), textParam("dsSummary1", "module.process.param.payment.dsSummary1", ""), textParam("dsAmount", "module.process.param.payment.dsAmount", ""), textParam("dsCurrency", "module.process.param.payment.dsCurrency", ""), textParam("dsRcp", "module.process.param.payment.dsRcp", ""), textParam("dsGac", "module.process.param.payment.dsGac", "01"), textParam("dsUn", "module.process.param.payment.dsUn", ""), secret("un", "module.process.param.payment.un"))));
        result.add(descriptor("MC_DS_DIGEST", "mcDsDigest", "mcDsDigest", params(textParam("dsId", "module.process.param.payment.dsId", ""), textParam("dsOperatorId", "module.process.param.payment.dsOperatorId", ""), textParam("dsInput", "module.process.param.payment.dsInput", ""))));
        result.add(descriptor("VISA_HCE_LUK", "visaHceLuk", "visaHceLuk", params(secret("smUdk", "module.process.param.payment.smUdk"), textParam("hceYear", "module.process.param.payment.hceYear", ""), textParam("hceHours", "module.process.param.payment.hceHours", ""), textParam("hceCounter", "module.process.param.payment.hceCounter", "01"))));
        result.add(descriptor("VISA_HCE_MSD", "visaHceMsd", "visaHceMsd", params(secret("luk", "module.process.param.payment.luk"), secret("atc", "module.process.param.payment.atc"), textParam("deviceType", "module.process.param.payment.deviceType", ""))));
        result.add(descriptor("VISA_HCE_QVSDC", "visaHceQvsdc", "visaHceQvsdc", params(secret("luk", "module.process.param.payment.luk"), textParam("terminalData", "module.process.param.payment.terminalData", ""), textParam("iccData", "module.process.param.payment.iccData", ""))));
        result.add(descriptor("EMV_ARPC", "emvArpc", "emvArpc", params(secret("sk", "module.process.param.payment.sk"), secret("arqc", "module.process.param.payment.arqc"), secret("arc", "module.process.param.payment.arc"), secret("csu", "module.process.param.payment.csu"), combo("method", "module.process.param.payment.method", List.of("1", "2"), "1"))));
        result.add(descriptor("EMV_TLV_PARSE", "emvTlvParse", "emvTlvParse", params(secret("input", "module.process.param.payment.emvData"))));
        result.add(descriptor("TRACK2_ENCODE", "track2Encode", "track2Encode", params(secret("pan", "module.process.param.payment.pan"), textParam("expiry", "module.process.param.payment.expiry", ""), textParam("serviceCode", "module.process.param.payment.serviceCode", ""), secret("discretionary", "module.process.param.payment.discretionary"))));
        result.add(descriptor("TRACK2_PARSE", "track2Parse", "track2Parse", params(secret("track2", "module.process.param.payment.track2"))));
        result.add(descriptor("EMV_ODA_STATIC_DATA", "emvOdaStaticData", "emvOdaStaticData", params(
                textParam("records", "module.process.param.payment.records", ""),
                textParam("aip", "module.process.param.payment.aip", ""),
                textParam("sdaTagList", "module.process.param.payment.sdaTagList", ""))));
        result.add(descriptor("EMV_ODA_RECOVER_ISSUER_KEY", "emvOdaRecoverIssuerKey", "emvOdaRecoverIssuerKey", params(
                textParam("certificate", "module.process.param.payment.issuerCertificate", ""),
                textParam("remainder", "module.process.param.payment.keyRemainder", ""),
                textParam("keyExponent", "module.process.param.payment.keyExponent", ""),
                textParam("caModulus", "module.process.param.payment.caModulus", ""),
                textParam("caExponent", "module.process.param.payment.caExponent", "03"),
                secret("pan", "module.process.param.payment.pan"))));
        result.add(descriptor("EMV_ODA_RECOVER_ICC_KEY", "emvOdaRecoverIccKey", "emvOdaRecoverIccKey", params(
                textParam("certificate", "module.process.param.payment.iccCertificate", ""),
                textParam("remainder", "module.process.param.payment.keyRemainder", ""),
                textParam("keyExponent", "module.process.param.payment.keyExponent", ""),
                textParam("issuerModulus", "module.process.param.payment.issuerModulus", ""),
                textParam("issuerExponent", "module.process.param.payment.issuerExponent", "03"),
                textParam("staticData", "module.process.param.payment.staticData", ""),
                secret("pan", "module.process.param.payment.pan"))));
        result.add(descriptor("EMV_ODA_VERIFY_SDA", "emvOdaVerifySda", "emvOdaVerifySda", params(
                textParam("ssad", "module.process.param.payment.ssad", ""),
                textParam("issuerModulus", "module.process.param.payment.issuerModulus", ""),
                textParam("issuerExponent", "module.process.param.payment.issuerExponent", "03"),
                textParam("staticData", "module.process.param.payment.staticData", ""))));
        result.add(descriptor("EMV_ODA_VERIFY_DDA", "emvOdaVerifyDda", "emvOdaVerifyDda", params(
                textParam("sdad", "module.process.param.payment.sdad", ""),
                textParam("iccModulus", "module.process.param.payment.iccModulus", ""),
                textParam("iccExponent", "module.process.param.payment.iccExponent", "03"),
                textParam("terminalData", "module.process.param.payment.ddolData", ""))));
        result.add(descriptor("EMV_ODA_VERIFY_CDA", "emvOdaVerifyCda", "emvOdaVerifyCda", params(
                textParam("sdad", "module.process.param.payment.sdad", ""),
                textParam("iccModulus", "module.process.param.payment.iccModulus", ""),
                textParam("iccExponent", "module.process.param.payment.iccExponent", "03"),
                textParam("unpredictableNumber", "module.process.param.payment.un", ""),
                textParam("cid", "module.process.param.payment.cid", ""),
                textParam("transactionData", "module.process.param.payment.cdaTransactionData", ""))));
        result.add(descriptor("EMV_ODA_SIGN_SSAD", "emvOdaSignSsad", "emvOdaSignSsad", params(
                textParam("issuerModulus", "module.process.param.payment.issuerModulus", ""),
                secret("issuerPrivateExponent", "module.process.param.payment.issuerPrivateExponent"),
                textParam("dataAuthenticationCode", "module.process.param.payment.dataAuthenticationCode", ""),
                textParam("staticData", "module.process.param.payment.staticData", ""))));
        result.add(descriptor("EMV_ODA_SIGN_SDAD", "emvOdaSignSdad", "emvOdaSignSdad", params(
                textParam("iccModulus", "module.process.param.payment.iccModulus", ""),
                secret("iccPrivateExponent", "module.process.param.payment.iccPrivateExponent"),
                textParam("iccDynamicData", "module.process.param.payment.iccDynamicData", ""),
                textParam("terminalData", "module.process.param.payment.terminalData", ""))));
        result.add(descriptor("THALES_LMK_ENCRYPT", "thalesLmkEncrypt", "thalesLmkEncrypt", params(
                secret("clearKey", "module.process.param.payment.clearKey"),
                secret("lmk", "module.process.param.payment.lmk"),
                textParam("keyType", "module.process.param.payment.thalesKeyType", "000"),
                combo("scheme", "module.process.param.payment.thalesScheme", THALES_SCHEMES, "U"),
                new NodeParameter("component", "module.process.param.payment.component", ParameterKind.CHECKBOX, "false"))));
        result.add(descriptor("THALES_LMK_DECRYPT", "thalesLmkDecrypt", "thalesLmkDecrypt", params(
                secret("cryptogram", "module.process.param.payment.cryptogram"),
                secret("lmk", "module.process.param.payment.lmk"),
                textParam("keyType", "module.process.param.payment.thalesKeyType", "000"),
                combo("scheme", "module.process.param.payment.thalesScheme", THALES_SCHEMES, "U"),
                new NodeParameter("component", "module.process.param.payment.component", ParameterKind.CHECKBOX, "false"))));
        result.add(descriptor("THALES_LMK_DESCRIBE", "thalesLmkDescribe", "thalesLmkDescribe", params(
                secret("cryptogram", "module.process.param.payment.cryptogram"),
                secret("lmk", "module.process.param.payment.lmk"),
                textParam("keyType", "module.process.param.payment.thalesKeyType", "000"),
                combo("scheme", "module.process.param.payment.thalesScheme", THALES_SCHEMES, "U"),
                new NodeParameter("component", "module.process.param.payment.component", ParameterKind.CHECKBOX, "false"))));
        result.add(descriptor("THALES_LMK_LOOKUP", "thalesLmkLookup", "thalesLmkLookup", params(
                secret("cryptogram", "module.process.param.payment.cryptogram"),
                secret("lmk", "module.process.param.payment.lmk"),
                textParam("checkValue", "module.process.param.payment.checkValue", ""))));
        result.add(descriptor("THALES_KCV", "thalesKcv", "thalesKcv", params(
                secret("clearKey", "module.process.param.payment.clearKey"))));
        result.add(new NodeDescriptor("THALES_KEY_BLOCK_PARSE", "Key Material",
                "module.process.type.payment.thalesKeyBlockParse", "module.process.desc.payment.thalesKeyBlockParse", "🧱",
                params(secret("keyBlock", "module.process.param.payment.keyBlock"))));
        result.add(new NodeDescriptor("THALES_KEY_BLOCK_HEADER", "Key Material",
                "module.process.type.payment.thalesKeyBlockHeader", "module.process.desc.payment.thalesKeyBlockHeader", "🧱",
                params(
                        combo("versionId", "module.process.param.payment.versionId", KEY_BLOCK_VERSIONS, "0"),
                        textParam("keyUsage", "module.process.param.payment.keyBlockUsage", "K0"),
                        textParam("algorithm", "module.process.param.payment.keyBlockAlgorithm", "T"),
                        textParam("modeOfUse", "module.process.param.payment.modeOfUse", "N"),
                        textParam("keyVersionNumber", "module.process.param.payment.keyVersionNumber", "00"),
                        textParam("exportability", "module.process.param.payment.exportability", "N"),
                        number("optionalBlockCount", "module.process.param.payment.optionalBlockCount", "0"),
                        textParam("lmkId", "module.process.param.payment.lmkId", "00"),
                        number("payloadCharacters", "module.process.param.payment.payloadCharacters", "56"))));
        return result;
    }

    private static NodeParameter cvk(String key) { return secret(key, "module.process.param.payment." + key); }

    private static void thalesKeyType(ProcessDefinition.Node node) {
        ThalesLmkOperations.keyType(setting(node, "keyType", "000"));
    }
    private static ThalesLmkOperations.Scheme thalesScheme(ProcessDefinition.Node node) {
        return ThalesLmkOperations.Scheme.of(setting(node, "scheme", "U").charAt(0));
    }
    private static ThalesLmkOperations.Lmk lmk(ProcessDefinition.Node node, Map<String, FlowValue> inputs) {
        return ThalesLmkOperations.Lmk.of(hexText(node, inputs, "lmk"));
    }
}
