package com.cryptocarver.model.process;

import com.cryptocarver.crypto.AesDukpt;
import com.cryptocarver.crypto.EMVOperations;
import com.cryptocarver.crypto.EmvTlv;
import com.cryptocarver.crypto.DukptKsn;
import com.cryptocarver.crypto.PaymentOperations;
import com.cryptocarver.model.process.handlers.PaymentOperationsNodeHandler;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentOperationsNodeHandlerTest {
    private static final PaymentOperationsNodeHandler HANDLER = new PaymentOperationsNodeHandler();
    private static final String PAN = "4761739001010010";
    private static final String CVK_A = "0123456789ABCDEF";
    private static final String CVK_B = "FEDCBA9876543210";
    private static final String PVK = "0123456789ABCDEFFEDCBA9876543210";
    private static final String SESSION_KEY = "0123456789ABCDEFFEDCBA9876543210";

    @Test
    void allPaymentTypesHaveDescriptorsAndKnownFacadeVectors() throws Exception {
        assertEquals(21, PaymentOperationsNodeHandler.TYPES.size());
        for (String type : PaymentOperationsNodeHandler.TYPES) {
            assertNotNull(HANDLER.descriptors().stream().filter(d -> d.type().equals(type)).findFirst().orElse(null), type);
        }

        assertEquals(PaymentOperations.encodePinBlock("1234", PAN, "Format 0 (ISO-0)"),
                HANDLER.execute(node("PIN_BLOCK_ENCODE"), Map.of("pin", text("1234"), "pan", text(PAN)), null).render());
        String block = PaymentOperations.encodePinBlock("1234", PAN, "Format 0 (ISO-0)");
        assertEquals("1234", HANDLER.execute(node("PIN_BLOCK_DECODE"), Map.of("pinBlock", hex(block), "pan", text(PAN)), null).render());

        ProcessDefinition.Node translate = node("PIN_BLOCK_TRANSLATE");
        translate.configuration.put("sourceFormat", "Format 0 (ISO-0)");
        translate.configuration.put("targetFormat", "Format 2 (ISO-2)");
        assertEquals(PaymentOperations.translatePinBlock(block, PAN, "Format 0 (ISO-0)", "Format 2 (ISO-2)"),
                HANDLER.execute(translate, Map.of("pinBlock", hex(block), "pan", text(PAN)), null).render());

        Map<String, FlowValue> cvvInputs = Map.of("cvkA", hex(CVK_A), "cvkB", hex(CVK_B), "pan", text(PAN),
                "expiry", text("2512"), "serviceCode", text("101"));
        String cvv = PaymentOperations.generateCVV(CVK_A, CVK_B, PAN, "2512", "101");
        assertEquals(cvv, HANDLER.execute(node("CVV_GENERATE"), cvvInputs, null).render());
        assertEquals("true", HANDLER.execute(node("CVV_VERIFY"), with(cvvInputs, "inputCvv", text(cvv)), null).render());

        Map<String, FlowValue> dcvvInputs = with(cvvInputs, "panSeq", text("0"), "atc", text("001"));
        String dcvv = PaymentOperations.generateDCVV(CVK_A, CVK_B, PAN, "0", "2512", "001");
        assertEquals(dcvv, HANDLER.execute(node("DCVV_GENERATE"), dcvvInputs, null).render());
        assertEquals("true", HANDLER.execute(node("DCVV_VERIFY"), with(dcvvInputs, "inputCvv", text(dcvv)), null).render());

        Map<String, FlowValue> pvvInputs = Map.of("pin", text("1234"), "pan", text(PAN), "pvk", hex(PVK), "pvki", text("0"));
        String pvv = PaymentOperations.generatePVV("1234", PAN, PVK, "0", 4);
        assertEquals(pvv, HANDLER.execute(node("PVV_GENERATE"), pvvInputs, null).render());
        assertEquals("true", HANDLER.execute(node("PVV_VERIFY"), with(pvvInputs, "pvv", text(pvv)), null).render());

        Map<String, FlowValue> ibmInputs = Map.of("pin", text("1234"), "pan", text(PAN), "pvk", hex(PVK),
                "decTable", hex("0123456789012345"));
        assertEquals(PaymentOperations.generateIBM3624Offset("1234", PAN, PVK, "0123456789012345"),
                HANDLER.execute(node("IBM3624_OFFSET"), ibmInputs, null).render());

        String aesBdk = "FEDCBA9876543210F1F1F1F1F1F1F1F1";
        String aesKsn = "123456789012345600000005";
        Map<String, FlowValue> aesInputs = Map.of("bdk", hex(aesBdk), "ksn", hex(aesKsn));
        ProcessDefinition.Node aes = node("DUKPT_AES_DERIVE");
        aes.configuration.put("usage", "DATA_ENCRYPTION_ENCRYPT");
        aes.configuration.put("outputType", "AES128");
        assertEquals(AesDukpt.deriveWorkingKey(aesBdk, aesKsn, AesDukpt.KeyUsage.DATA_ENCRYPTION_ENCRYPT,
                        AesDukpt.KeyType.AES128).workingKeyHex(),
                HANDLER.execute(aes, aesInputs, null).render());
        String ipek = "6AC292FAA1315B4D858AB3A3D7D5933A";
        String tdesKsn = "FFFF9876543210E00008";
        assertEquals(DukptKsn.deriveWorkingKey(ipek, tdesKsn, DukptKsn.TdesKeyUsage.PIN_ENCRYPTION).workingKeyHex(),
                HANDLER.execute(node("DUKPT_TDES_DERIVE"), Map.of("ipek", hex(ipek), "ksn", hex(tdesKsn)), null).render());
        String clearAesBlock = "04124389999AAAABAAAAAAAAAAAAAAAA";
        String crypt = AesDukpt.cryptPinBlock(aesBdk, aesKsn, AesDukpt.KeyType.AES128, clearAesBlock, false);
        ProcessDefinition.Node pinCrypt = node("DUKPT_PIN_CRYPT");
        pinCrypt.configuration.put("outputType", "AES128");
        assertEquals(crypt, HANDLER.execute(pinCrypt, with(aesInputs, "pinBlock", hex(clearAesBlock)), null).render());

        String imk = "0123456789ABCDEFFEDCBA9876543210";
        String icc = EMVOperations.deriveICCMasterKey(imk, "4512345678901234", "01");
        assertEquals(icc, HANDLER.execute(node("EMV_ICC_MASTER_KEY"), Map.of("imk", hex(imk), "pan", text("4512345678901234"), "panSequence", text("01")), null).render());
        String session = EMVOperations.deriveSessionKey(icc, "0001", "");
        assertEquals(session, HANDLER.execute(node("EMV_SESSION_KEY"), Map.of("mkac", hex(icc), "atc", hex("0001"), "un", hex("")), null).render());
        String txn = "000000001000000000000000097800000000000009781911220012345678";
        ProcessDefinition.Node arqc = node("EMV_ARQC_GENERATE"); arqc.configuration.put("paddingMethod", "1");
        String arqcValue = EMVOperations.generateARQC(session, txn, 1);
        assertEquals(arqcValue, HANDLER.execute(arqc, Map.of("sk", hex(session), "transactionData", hex(txn)), null).render());
        ProcessDefinition.Node arqcVerify = node("EMV_ARQC_VERIFY"); arqcVerify.configuration.put("paddingMethod", "1");
        assertEquals("true", HANDLER.execute(arqcVerify, Map.of("sk", hex(session), "arqc", hex(arqcValue), "transactionData", hex(txn)), null).render());
        ProcessDefinition.Node arpc = node("EMV_ARPC"); arpc.configuration.put("method", "1");
        assertEquals(EMVOperations.generateARPC_Method1(session, arqcValue, "3030"),
                HANDLER.execute(arpc, Map.of("sk", hex(session), "arqc", hex(arqcValue), "arc", text("3030")), null).render());

        String tlv = "9F02060000000100009F26080123456789ABCDEF";
        assertEquals(EmvTlv.transactionSummary(EmvTlv.analyze(tlv)),
                HANDLER.execute(node("EMV_TLV_PARSE"), Map.of("input", hex(tlv)), null).render());
        String track2 = PaymentOperations.encodeTrack2(PAN, "2512", "101", "1234");
        assertEquals(track2, HANDLER.execute(node("TRACK2_ENCODE"), Map.of("pan", text(PAN), "expiry", text("2512"),
                "serviceCode", text("101"), "discretionary", text("1234")), null).render());
        assertEquals(PaymentOperations.parseTrack2(track2), HANDLER.execute(node("TRACK2_PARSE"), Map.of("track2", text(track2)), null).render());
    }

    @Test
    void everyPaymentTypeRejectsIncompleteConfigurationBeforeExecution() {
        for (String type : PaymentOperationsNodeHandler.TYPES) {
            ProcessDefinition definition = new ProcessDefinition();
            definition.nodes.add(new ProcessDefinition.Node("n", type, type, 0, 0));
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> ProcessEngine.validate(definition), type);
            assertFalse(error.getMessage().matches(".*[0-9A-Fa-f]{8,}.*"), type);
        }
    }

    @Test
    void invalidPanIsRejectedDuringPreflight() {
        ProcessDefinition definition = new ProcessDefinition();
        ProcessDefinition.Node node = new ProcessDefinition.Node("n", "CVV_GENERATE", "CVV", 0, 0);
        node.configuration.put("cvkA", CVK_A); node.configuration.put("cvkB", CVK_B);
        node.configuration.put("pan", "4761739001010011"); node.configuration.put("expiry", "2512"); node.configuration.put("serviceCode", "101");
        definition.nodes.add(node);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> ProcessEngine.validate(definition)).getMessage().contains("PAN"));
    }

    private static ProcessDefinition.Node node(String type) { return new ProcessDefinition.Node(type.toLowerCase(), type, type, 0, 0); }
    private static FlowValue text(String value) { return FlowValue.text(value, StandardCharsets.UTF_8); }
    private static FlowValue hex(String value) { return FlowValue.hex(value.getBytes(StandardCharsets.UTF_8)); }
    private static Map<String, FlowValue> with(Map<String, FlowValue> base, String key, FlowValue value) {
        java.util.LinkedHashMap<String, FlowValue> result = new java.util.LinkedHashMap<>(base); result.put(key, value); return result;
    }
    private static Map<String, FlowValue> with(Map<String, FlowValue> base, String key1, FlowValue value1, String key2, FlowValue value2) {
        Map<String, FlowValue> result = with(base, key1, value1); return with(result, key2, value2);
    }
}
