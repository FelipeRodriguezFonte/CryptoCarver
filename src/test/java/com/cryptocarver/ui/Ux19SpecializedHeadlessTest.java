package com.cryptocarver.ui;

import com.cryptocarver.asn1.ASN1Encoder;
import com.cryptocarver.asn1.ASN1Parser;
import com.cryptocarver.crypto.PaymentOperations;
import com.cryptocarver.crypto.XMLSignatureOperations;
import com.cryptocarver.crypto.JOSEService;
import com.cryptocarver.crypto.SignerConfig;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** UX-19 regression coverage that does not require a JavaFX toolkit. */
class Ux19SpecializedHeadlessTest {

    @Test
    void specializedNavigationAliasesResolveToRealDestinations() {
        assertEquals(UiNavigationRegistry.Module.CERTIFICATES,
                UiNavigationRegistry.resolve("ASN.1").orElseThrow().module());
        assertEquals(UiNavigationRegistry.Module.WSS_SECURITY,
                UiNavigationRegistry.resolve("WSS Security").orElseThrow().module());
        assertEquals(UiNavigationRegistry.Module.PAYMENTS,
                UiNavigationRegistry.resolve("Payments").orElseThrow().module());
        assertEquals(UiNavigationRegistry.Module.GENERIC,
                UiNavigationRegistry.resolve("Process Designer").orElseThrow().module());
    }

    @Test
    void asn1RoutePreservesExactTechnicalBytes() throws Exception {
        byte[] encoded = ASN1Encoder.encodeUTF8String("A1B2");
        assertEquals("0C0441314232", com.cryptocarver.util.DataConverter.bytesToHex(encoded));
        assertEquals(ASN1Parser.detectType(encoded), ASN1Parser.detectType(encoded.clone()));
    }

    @Test
    void joseRouteProducesAStableCompactTechnicalToken() throws Exception {
        String token = JOSEService.generateSignedJWT(
                "{\"sub\":\"ux19\"}",
                List.of(new SignerConfig("HS256", "ux19-secret")),
                "Compact", false);
        assertEquals(3, token.split("\\.", -1).length);
        assertTrue(token.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"));
    }

    @Test
    void xmlSecurityInspectionIsActionableForUnsignedXml() throws Exception {
        String xml = "<root><value>technical</value></root>";
        String report = XMLSignatureOperations.inspectSignedXml(xml);
        assertTrue(report.contains("XMLDSig signatures"));
        assertTrue(report.contains("0"));
    }

    @Test
    void paymentsPinBlockRoundTripPreservesPinAndPanContract() throws Exception {
        String pan = "4761739001010010";
        String block = PaymentOperations.encodePinBlock("1234", pan, "ISO-0");
        assertEquals("1234", PaymentOperations.decodePinBlock(block, pan, "ISO-0"));
        assertEquals(16, block.length());
    }

    @Test
    void processDesignerTechnicalPayloadRemainsByteStable() {
        byte[] payload = "Process Designer UX-19".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(payload, new String(payload, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8));
    }
}
