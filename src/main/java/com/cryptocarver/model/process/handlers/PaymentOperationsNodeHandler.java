package com.cryptocarver.model.process.handlers;

import com.cryptocarver.crypto.AesDukpt;
import com.cryptocarver.crypto.CheckDigitCalculator;
import com.cryptocarver.crypto.DukptKsn;
import com.cryptocarver.crypto.EMVOperations;
import com.cryptocarver.crypto.EmvTlv;
import com.cryptocarver.crypto.PaymentOperations;
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
            "EMV_TLV_PARSE", "TRACK2_ENCODE", "TRACK2_PARSE");

    private static final Set<Representation> TEXT = Set.of(Representation.TEXT_UTF8);
    private static final Set<Representation> HEX = Set.of(Representation.HEX);
    private static final List<String> PIN_FORMATS = List.of(
            "Format 0 (ISO-0)", "Format 1 (ISO-1)", "Format 2 (ISO-2)",
            "Format 3 (ISO-3)", "Format 4 (ISO-4)");
    private static final List<String> TDES_USAGES = List.of("PIN_ENCRYPTION", "MAC_REQUEST", "MAC_RESPONSE", "DATA_ENCRYPTION");
    private static final List<String> AES_USAGES = List.of(
            "PIN_ENCRYPTION", "MAC_GENERATION", "MAC_VERIFICATION", "MAC_BOTH_WAYS",
            "DATA_ENCRYPTION_ENCRYPT", "DATA_ENCRYPTION_DECRYPT", "DATA_ENCRYPTION_BOTH_WAYS",
            "KEY_ENCRYPTION", "KEY_DERIVATION");
    private static final List<String> AES_KEY_TYPES = List.of("AES128", "AES192", "AES256");

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
            case "DCVV_GENERATE" -> List.of(hexPort("cvkA"), hexPort("cvkB"), textPort("pan"), textPort("panSeq"), textPort("expiry"), textPort("atc"));
            case "DCVV_VERIFY" -> List.of(hexPort("cvkA"), hexPort("cvkB"), textPort("pan"), textPort("panSeq"), textPort("expiry"), textPort("atc"), textPort("inputCvv"));
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
            case "EMV_TLV_PARSE" -> List.of(hexPort("input"));
            case "TRACK2_ENCODE" -> List.of(textPort("pan"), textPort("expiry"), textPort("serviceCode"), textPort("discretionary"));
            case "TRACK2_PARSE" -> List.of(textPort("track2"));
            default -> List.of();
        };
    }

    private static PortDefinition textPort(String name) { return new PortDefinition(name, TEXT, false); }
    private static PortDefinition hexPort(String name) { return new PortDefinition(name, HEX, false); }

    @Override
    public Representation outputRepresentation(ProcessDefinition.Node node, Map<String, Representation> inputs) {
        return switch (node.type.toUpperCase(Locale.ROOT)) {
            case "PIN_BLOCK_ENCODE", "PIN_BLOCK_TRANSLATE", "DUKPT_TDES_DERIVE", "DUKPT_AES_DERIVE", "DUKPT_PIN_CRYPT",
                    "EMV_ICC_MASTER_KEY", "EMV_SESSION_KEY", "EMV_ARQC_GENERATE", "EMV_ARPC" -> Representation.HEX;
            case "PIN_BLOCK_DECODE", "CVV_GENERATE", "CVV_VERIFY", "DCVV_GENERATE", "DCVV_VERIFY", "PVV_GENERATE", "PVV_VERIFY",
                    "IBM3624_OFFSET", "EMV_ARQC_VERIFY", "EMV_TLV_PARSE", "TRACK2_ENCODE", "TRACK2_PARSE" -> Representation.TEXT_UTF8;
            default -> Representation.BINARY;
        };
    }

    @Override
    public void validateConfiguration(ProcessDefinition.Node node) {
        String type = node.type.toUpperCase(Locale.ROOT);
        for (String key : List.of("cvkA", "cvkB", "pvk", "decTable", "ipek", "bdk", "ksn", "pinBlock", "imk", "mkac", "atc", "un", "sk", "arqc", "csu", "transactionData")) {
            validateConfiguredHex(node, key);
        }
        switch (type) {
            case "PIN_BLOCK_ENCODE" -> {
                require(node, "pin"); require(node, "pan");
                decimal(node, "pin", 4, 12); pan(node, "pan"); oneOf(node, "format", PIN_FORMATS);
            }
            case "PIN_BLOCK_DECODE" -> {
                require(node, "pinBlock"); require(node, "pan"); pan(node, "pan"); oneOf(node, "format", PIN_FORMATS); pinBlock(node, "pinBlock", setting(node, "format", PIN_FORMATS.get(0)));
            }
            case "PIN_BLOCK_TRANSLATE" -> {
                require(node, "pinBlock"); require(node, "pan"); pan(node, "pan");
                oneOf(node, "sourceFormat", PIN_FORMATS); oneOf(node, "targetFormat", PIN_FORMATS);
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
            case "EMV_ARPC" -> { require(node, "sk"); require(node, "arc"); oneOf(node, "method", List.of("1", "2")); if ("1".equals(setting(node, "method", "1"))) require(node, "arqc"); }
            case "EMV_TLV_PARSE" -> require(node, "input");
            case "TRACK2_ENCODE" -> { require(node, "pan"); require(node, "expiry"); require(node, "serviceCode"); pan(node, "pan"); decimal(node, "expiry", 4, 4); decimal(node, "serviceCode", 3, 3); }
            case "TRACK2_PARSE" -> require(node, "track2");
            default -> throw new IllegalArgumentException("Unsupported payment operation");
        }
    }

    private static void commonCvv(ProcessDefinition.Node node) {
        require(node, "cvkA"); require(node, "cvkB"); require(node, "pan"); require(node, "expiry"); require(node, "serviceCode");
        hexLength(node, "cvkA", 8); hexLength(node, "cvkB", 8); pan(node, "pan"); decimal(node, "expiry", 4, 4); decimal(node, "serviceCode", 3, 3);
    }
    private static void commonDcvv(ProcessDefinition.Node node) {
        commonCvv(node); require(node, "panSeq"); require(node, "atc"); decimal(node, "panSeq", 1, 2); decimal(node, "atc", 1, 16);
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
                case "PIN_BLOCK_ENCODE" -> hex(PaymentOperations.encodePinBlock(text(node, inputs, "pin"), text(node, inputs, "pan"), setting(node, "format", PIN_FORMATS.get(0))));
                case "PIN_BLOCK_DECODE" -> text(PaymentOperations.decodePinBlock(hexText(node, inputs, "pinBlock"), text(node, inputs, "pan"), setting(node, "format", PIN_FORMATS.get(0))));
                case "PIN_BLOCK_TRANSLATE" -> hex(PaymentOperations.translatePinBlock(hexText(node, inputs, "pinBlock"), text(node, inputs, "pan"), setting(node, "sourceFormat", PIN_FORMATS.get(0)), setting(node, "targetFormat", PIN_FORMATS.get(0))));
                case "CVV_GENERATE" -> text(PaymentOperations.generateCVV(hexText(node, inputs, "cvkA"), hexText(node, inputs, "cvkB"), text(node, inputs, "pan"), text(node, inputs, "expiry"), text(node, inputs, "serviceCode")));
                case "CVV_VERIFY" -> text(Boolean.toString(PaymentOperations.verifyCVV(hexText(node, inputs, "cvkA"), hexText(node, inputs, "cvkB"), text(node, inputs, "pan"), text(node, inputs, "expiry"), text(node, inputs, "serviceCode"), text(node, inputs, "inputCvv"))));
                case "DCVV_GENERATE" -> text(PaymentOperations.generateDCVV(hexText(node, inputs, "cvkA"), hexText(node, inputs, "cvkB"), text(node, inputs, "pan"), text(node, inputs, "panSeq"), text(node, inputs, "expiry"), text(node, inputs, "atc")));
                case "DCVV_VERIFY" -> text(Boolean.toString(PaymentOperations.verifyDCVV(hexText(node, inputs, "cvkA"), hexText(node, inputs, "cvkB"), text(node, inputs, "pan"), text(node, inputs, "panSeq"), text(node, inputs, "expiry"), text(node, inputs, "atc"), text(node, inputs, "inputCvv"))));
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
                        : EMVOperations.generateARPC_Method2(hexText(node, inputs, "sk"), text(node, inputs, "arc"), hexTextOptional(node, inputs, "csu")));
                case "EMV_TLV_PARSE" -> text(EmvTlv.transactionSummary(EmvTlv.analyze(hexText(node, inputs, "input"))));
                case "TRACK2_ENCODE" -> text(PaymentOperations.encodeTrack2(text(node, inputs, "pan"), text(node, inputs, "expiry"), text(node, inputs, "serviceCode"), textOptional(node, inputs, "discretionary")));
                case "TRACK2_PARSE" -> text(PaymentOperations.parseTrack2(text(node, inputs, "track2")));
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
    private static String hexTextOptional(ProcessDefinition.Node node, Map<String, FlowValue> inputs, String name) {
        if (!inputs.containsKey(name) && !node.configuration.containsKey(name)) return "";
        return hexText(node, inputs, name);
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
    private static void decimal(ProcessDefinition.Node node, String key, int min, int max) {
        if (node.configuration.containsKey(key) && !decimalValue(node.configuration.get(key), min, max)) throw new IllegalArgumentException("Invalid decimal payment input");
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
        if (!node.configuration.containsKey(key)) return; int expected = format.contains("Format 4") ? 32 : 16; hexLength(node, key, expected / 2);
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
        result.add(descriptor("DCVV_GENERATE", "dcvvGenerate", "dcvvGenerate", params(cvk("cvkA"), cvk("cvkB"), secret("pan", "module.process.param.payment.pan"), textParam("panSeq", "module.process.param.payment.panSeq", "0"), textParam("expiry", "module.process.param.payment.expiry", ""), textParam("atc", "module.process.param.payment.atc", ""))));
        result.add(descriptor("DCVV_VERIFY", "dcvvVerify", "dcvvVerify", params(cvk("cvkA"), cvk("cvkB"), secret("pan", "module.process.param.payment.pan"), textParam("panSeq", "module.process.param.payment.panSeq", "0"), textParam("expiry", "module.process.param.payment.expiry", ""), textParam("atc", "module.process.param.payment.atc", ""), secret("inputCvv", "module.process.param.payment.cvv"))));
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
        result.add(descriptor("EMV_ARPC", "emvArpc", "emvArpc", params(secret("sk", "module.process.param.payment.sk"), secret("arqc", "module.process.param.payment.arqc"), secret("arc", "module.process.param.payment.arc"), secret("csu", "module.process.param.payment.csu"), combo("method", "module.process.param.payment.method", List.of("1", "2"), "1"))));
        result.add(descriptor("EMV_TLV_PARSE", "emvTlvParse", "emvTlvParse", params(secret("input", "module.process.param.payment.emvData"))));
        result.add(descriptor("TRACK2_ENCODE", "track2Encode", "track2Encode", params(secret("pan", "module.process.param.payment.pan"), textParam("expiry", "module.process.param.payment.expiry", ""), textParam("serviceCode", "module.process.param.payment.serviceCode", ""), secret("discretionary", "module.process.param.payment.discretionary"))));
        result.add(descriptor("TRACK2_PARSE", "track2Parse", "track2Parse", params(secret("track2", "module.process.param.payment.track2"))));
        return result;
    }

    private static NodeParameter cvk(String key) { return secret(key, "module.process.param.payment." + key); }
}
