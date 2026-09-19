package com.cryptocarver.model.process;

import com.cryptocarver.crypto.EmvOdaOperations;
import com.cryptocarver.model.process.handlers.PaymentOperationsNodeHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAKeyGenParameterSpec;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The offline data authentication nodes, driven the way the Process Designer drives them. */
class EmvOdaNodeHandlerTest {

    private static final PaymentOperationsNodeHandler HANDLER = new PaymentOperationsNodeHandler();
    private static final String PAN = "4761739001010119";
    private static final String STATIC_DATA = "70115A0844AAAAAAAAAAAAAA5F3401009F0702FF00";

    private static KeyPair ca;
    private static KeyPair issuer;
    private static KeyPair icc;
    private static String expiry;

    @BeforeAll
    static void personalise() throws Exception {
        ca = rsa(1024);
        issuer = rsa(768);
        icc = rsa(512);
        YearMonth when = YearMonth.now().plusYears(3);
        expiry = String.format("%02d%02d", when.getMonthValue(), when.getYear() % 100);
    }

    private static KeyPair rsa(int bits) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(new RSAKeyGenParameterSpec(bits, BigInteger.valueOf(3)));
        return generator.generateKeyPair();
    }

    private static EmvOdaOperations.RsaPublicKey pub(KeyPair pair) {
        return EmvOdaOperations.RsaPublicKey.of((RSAPublicKey) pair.getPublic());
    }

    private static EmvOdaOperations.RsaPrivateKey priv(KeyPair pair) {
        return EmvOdaOperations.RsaPrivateKey.of((RSAPrivateKey) pair.getPrivate());
    }

    @Test
    void everyOdaTypeHasADescriptor() {
        for (String type : PaymentOperationsNodeHandler.TYPES) {
            if (!type.startsWith("EMV_ODA_")) {
                continue;
            }
            assertNotNull(HANDLER.descriptors().stream()
                    .filter(d -> d.type().equals(type)).findFirst().orElse(null), type);
        }
    }

    @Test
    void staticDataTakesItsRecordsOnePerLine() throws Exception {
        ProcessDefinition.Node node = node("EMV_ODA_STATIC_DATA");
        node.configuration.put("records", "AABB\nCCDD");
        node.configuration.put("aip", "5C00");
        node.configuration.put("sdaTagList", "82");

        assertEquals("AABBCCDD5C00", HANDLER.execute(node, Map.of(), null).render());
    }

    @Test
    void theIssuerKeyIsRecoveredAndReported() throws Exception {
        EmvOdaOperations.IssuedCertificate issued = EmvOdaOperations.signIssuerCertificate(
                priv(ca), pub(issuer), PAN.substring(0, 8), expiry, "000001");

        ProcessDefinition.Node node = node("EMV_ODA_RECOVER_ISSUER_KEY");
        node.configuration.put("certificate", issued.certificate());
        node.configuration.put("remainder", issued.remainder());
        node.configuration.put("keyExponent", issued.exponent());
        node.configuration.put("caModulus", pub(ca).modulusHex());
        node.configuration.put("caExponent", pub(ca).exponentHex());
        node.configuration.put("pan", PAN);

        String report = HANDLER.execute(node, Map.of(), null).render();

        assertTrue(report.contains("PASSED"), report);
        assertTrue(report.contains(pub(issuer).modulusHex()), report);
    }

    @Test
    void signingAndVerifyingStaticDataComposesAcrossTwoNodes() throws Exception {
        ProcessDefinition.Node sign = node("EMV_ODA_SIGN_SSAD");
        sign.configuration.put("issuerModulus", priv(issuer).modulusHex());
        sign.configuration.put("issuerPrivateExponent", priv(issuer).privateExponentHex());
        sign.configuration.put("dataAuthenticationCode", "1234");
        sign.configuration.put("staticData", STATIC_DATA);
        String ssad = HANDLER.execute(sign, Map.of(), null).render();

        ProcessDefinition.Node verify = node("EMV_ODA_VERIFY_SDA");
        verify.configuration.put("issuerModulus", pub(issuer).modulusHex());
        verify.configuration.put("issuerExponent", pub(issuer).exponentHex());
        verify.configuration.put("staticData", STATIC_DATA);
        String report = HANDLER.execute(verify, inputs("ssad", ssad), null).render();

        assertTrue(report.contains("PASSED"), report);
        assertTrue(report.contains("1234"), report);
    }

    @Test
    void theDynamicSignatureNodesRoundTrip() throws Exception {
        ProcessDefinition.Node sign = node("EMV_ODA_SIGN_SDAD");
        sign.configuration.put("iccModulus", priv(icc).modulusHex());
        sign.configuration.put("iccPrivateExponent", priv(icc).privateExponentHex());
        sign.configuration.put("iccDynamicData", "081122334455667788");
        sign.configuration.put("terminalData", "9F370401020304");
        String sdad = HANDLER.execute(sign, Map.of(), null).render();

        ProcessDefinition.Node verify = node("EMV_ODA_VERIFY_DDA");
        verify.configuration.put("iccModulus", pub(icc).modulusHex());
        verify.configuration.put("iccExponent", pub(icc).exponentHex());
        verify.configuration.put("terminalData", "9F370401020304");
        String report = HANDLER.execute(verify, inputs("sdad", sdad), null).render();

        assertTrue(report.contains("PASSED"), report);
        assertTrue(report.contains("1122334455667788"), report);
    }

    @Test
    void theCdaNodeChecksTheCryptogramAndTheTransactionHash() throws Exception {
        String transactionData = "000000010000000000000000097801020304";
        String dynamicData = EmvOdaOperations.combinedDynamicData("11223344", "80", "A1B2C3D4E5F60718",
                EmvOdaOperations.transactionDataHashCode(transactionData));
        String sdad = EmvOdaOperations.signDynamicApplicationData(priv(icc), dynamicData, "01020304");

        ProcessDefinition.Node verify = node("EMV_ODA_VERIFY_CDA");
        verify.configuration.put("sdad", sdad);
        verify.configuration.put("iccModulus", pub(icc).modulusHex());
        verify.configuration.put("iccExponent", pub(icc).exponentHex());
        verify.configuration.put("unpredictableNumber", "01020304");
        verify.configuration.put("cid", "80");
        verify.configuration.put("transactionData", transactionData);

        String report = HANDLER.execute(verify, Map.of(), null).render();

        assertTrue(report.contains("PASSED"), report);
        assertTrue(report.contains("A1B2C3D4E5F60718"), report);
    }

    @Test
    void aCdaNodeWithoutAFourByteUnpredictableNumberIsRejectedAtValidation() {
        ProcessDefinition.Node node = node("EMV_ODA_VERIFY_CDA");
        node.configuration.put("sdad", "00");
        node.configuration.put("iccModulus", "00");
        node.configuration.put("iccExponent", "03");
        node.configuration.put("unpredictableNumber", "0102");

        assertThrows(IllegalArgumentException.class, () -> HANDLER.validateConfiguration(node));
    }

    @Test
    void nonHexadecimalCardDataIsRejectedAtValidation() {
        ProcessDefinition.Node node = node("EMV_ODA_RECOVER_ISSUER_KEY");
        node.configuration.put("certificate", "not hex");
        node.configuration.put("caModulus", "00");
        node.configuration.put("caExponent", "03");

        assertThrows(IllegalArgumentException.class, () -> HANDLER.validateConfiguration(node));
    }

    @Test
    void theSigningNodesKeepThePrivateExponentOutOfTheSavedProcess() {
        for (String type : new String[] {"EMV_ODA_SIGN_SSAD", "EMV_ODA_SIGN_SDAD"}) {
            NodeDescriptor descriptor = HANDLER.descriptors().stream()
                    .filter(d -> d.type().equals(type)).findFirst().orElseThrow();
            assertTrue(descriptor.parameters().stream()
                            .filter(p -> p.key().toLowerCase().contains("privateexponent"))
                            .allMatch(p -> p.kind() == ParameterKind.PASSWORD),
                    type + " must mark its private exponent as a secret");
        }
    }

    private static ProcessDefinition.Node node(String type) {
        return new ProcessDefinition.Node(type.toLowerCase(), type, type, 0, 0);
    }

    private static Map<String, FlowValue> inputs(String key, String hexValue) {
        Map<String, FlowValue> map = new LinkedHashMap<>();
        map.put(key, FlowValue.hex(hexValue.getBytes(StandardCharsets.UTF_8)));
        return map;
    }
}
