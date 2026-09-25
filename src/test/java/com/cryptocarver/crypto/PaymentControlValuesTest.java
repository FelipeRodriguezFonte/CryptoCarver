package com.cryptocarver.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cryptocarver.utils.DataConverter;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Control values for the payment operations that the external tool captures of
 * 2026-09-19 did not cover (those only closed key-block formats).
 *
 * <p>Two kinds of provenance, marked on every test:
 * <ul>
 *   <li><b>Published</b>: the value appears in a public standard or reference
 *       example and was reproduced by both implementations.</li>
 *   <li><b>Cross-checked</b>: computed by {@code scripts/payment_reference.py},
 *       an independent implementation written from the standards on
 *       pycryptodome, and matched by the Java code. Two implementations
 *       agreeing is weaker than a hardware capture; see
 *       {@code docs/VALORES_CONTROL_PAGOS.md} for what still needs one.</li>
 * </ul>
 *
 * <p>House constants: key {@code 0123456789ABCDEFFEDCBA9876543210}, PAN
 * {@code 4111111111111111}, PIN {@code 1234}.
 */
class PaymentControlValuesTest {

    private static final String K2 = "0123456789ABCDEFFEDCBA9876543210";
    private static final String AES128 = "000102030405060708090A0B0C0D0E0F";
    private static final String PAN = "4111111111111111";
    private static final String CVK_A = "0123456789ABCDEF";
    private static final String CVK_B = "FEDCBA9876543210";

    private static String hex(byte[] b) {
        return DataConverter.bytesToHex(b).toUpperCase();
    }

    // ---- KCV ----

    /** Published: the classic double-length test key KCV. */
    @Test
    void tdesKcv() throws Exception {
        assertEquals("08D7B4", hex(KeyOperations.calculateKCV_VISA(DataConverter.hexToBytes(K2))));
    }

    /** Published: FIPS-197 C.1 key encrypting a zero block starts C6A13B. */
    @Test
    void aesZeroBlockKcv() throws Exception {
        assertEquals("C6A13B", hex(KeyOperations.calculateKCV_AES(DataConverter.hexToBytes(AES128))));
    }

    // ---- PIN blocks ----

    /** Cross-checked: ISO 9564-1 format 0. */
    @Test
    void iso0PinBlock() throws Exception {
        assertEquals("041225EEEEEEEEEE", PaymentOperations.encodePinBlock("1234", PAN, "Format 0 (ISO-0)"));
        assertEquals("1234", PaymentOperations.decodePinBlock("041225EEEEEEEEEE", PAN, "Format 0 (ISO-0)"));
    }

    /** Cross-checked: ISO 9564-1 format 2. */
    @Test
    void iso2PinBlock() throws Exception {
        assertEquals("241234FFFFFFFFFF", PaymentOperations.encodePinBlock("1234", PAN, "Format 2 (ISO-2)"));
    }

    // ---- Card verification values ----

    /** Published: PAN 4123456789012345, expiry 8701, service code 101 gives CVV 561. */
    @Test
    void cvvPublishedExample() throws Exception {
        assertEquals("561", PaymentOperations.generateCVV(CVK_A, CVK_B, "4123456789012345", "8701", "101"));
    }

    /** Cross-checked: CVV (201), iCVV (999) and CVV2 (000) on the house card, expiry 2512. */
    @Test
    void cvvVariantsOnHouseCard() throws Exception {
        assertEquals("139", PaymentOperations.generateCVV(CVK_A, CVK_B, PAN, "2512", "201"));
        assertEquals("133", PaymentOperations.generateCVV(CVK_A, CVK_B, PAN, "2512", "999"));
        assertEquals("765", PaymentOperations.generateCVV(CVK_A, CVK_B, PAN, "2512", "000"));
    }

    // ---- PIN verification ----

    /** Cross-checked: Visa PVV, PVKI 1. */
    @Test
    void visaPvv() throws Exception {
        assertEquals("9464", PaymentOperations.generatePVV("1234", PAN, K2, "1", 4));
    }

    /**
     * Cross-checked: IBM 3624 with decimalisation table 0123456789012345 and
     * validation data {@code 0000 || 12 PAN digits before the check digit}.
     * The validation data is an issuer convention, not a standard.
     */
    @Test
    void ibm3624NaturalPinAndOffset() throws Exception {
        assertEquals("5775", PaymentOperations.generateIBM3624Pin(PAN, K2, "0123456789012345", "0000"));
        assertEquals("6569", PaymentOperations.generateIBM3624Offset("1234", PAN, K2, "0123456789012345"));
    }

    // ---- MAC ----

    private static final String HELLO = "48656C6C6F2C20776F726C6421"; // "Hello, world!"

    /** Cross-checked: ISO 9797-1 algorithm 3, padding 1 (full) and padding 2 (Payments truncates to 4 bytes). */
    @Test
    void retailMac() throws Exception {
        assertEquals("ABF9AFD03C6F5F70",
                hex(MACOperations.generate(DataConverter.hexToBytes(HELLO), DataConverter.hexToBytes(K2), "ANSI-X9.19")));
        assertEquals("94A00FFE", PaymentOperations.generateMAC(K2, HELLO, "Retail MAC (ISO 9797-1 Alg 3)"));
    }

    /** Cross-checked: ISO 9797-1 algorithm 1 with TDES, padding 2, truncated to 4 bytes. */
    @Test
    void tdesCbcMac() throws Exception {
        assertEquals("09D0D7B7", PaymentOperations.generateMAC(K2, HELLO, "CBC-MAC (ISO 9797-1 Alg 1)"));
    }

    /** Cross-checked: AES-CMAC (NIST SP 800-38B), truncated to 4 bytes. */
    @Test
    void aesCmac() throws Exception {
        assertEquals("BF170E40", PaymentOperations.generateMAC(AES128, HELLO, "CMAC (ISO 9797-1 Alg 5)"));
    }

    /** Published: X9.19 example, "Now is the time for all ". */
    @Test
    void x919PublishedExample() throws Exception {
        assertEquals("A1C72E74EA3FA9B6", hex(MACOperations.generate(
                "Now is the time for all ".getBytes(StandardCharsets.US_ASCII), DataConverter.hexToBytes(K2), "ANSI-X9.19")));
    }

    // ---- TDES DUKPT (ANSI X9.24-1:2009) ----

    /** Published: X9.24-1 Annex A. KSN 1 PIN key is the published future key with the PIN variant. */
    @Test
    void tdesDukptPublishedKeys() throws Exception {
        String ksn1 = "FFFF9876543210E00001";
        String ipek = DukptKsn.deriveIpek(K2, ksn1);
        assertEquals("6AC292FAA1315B4D858AB3A3D7D5933A", ipek);
        assertEquals("042666B49184CF5C68DE9628D0397B36",
                DukptKsn.deriveWorkingKey(ipek, ksn1, DukptKsn.TdesKeyUsage.PIN_ENCRYPTION).workingKeyHex());
    }

    /** Cross-checked: PIN and MAC request keys at counters 2 and 8. */
    @Test
    void tdesDukptLaterCounters() throws Exception {
        String ipek = DukptKsn.deriveIpek(K2, "FFFF9876543210E00000");
        assertEquals("C46551CEF9FD244FAA9AD834130D3B38",
                DukptKsn.deriveWorkingKey(ipek, "FFFF9876543210E00002", DukptKsn.TdesKeyUsage.PIN_ENCRYPTION).workingKeyHex());
        assertEquals("C46551CEF9FDDBB0AA9AD834130DC4C7",
                DukptKsn.deriveWorkingKey(ipek, "FFFF9876543210E00002", DukptKsn.TdesKeyUsage.MAC_REQUEST).workingKeyHex());
        assertEquals("27F66D5244FF621EAA6F6120EDEB427F",
                DukptKsn.deriveWorkingKey(ipek, "FFFF9876543210E00008", DukptKsn.TdesKeyUsage.PIN_ENCRYPTION).workingKeyHex());
        assertEquals("27F66D5244FF9DE1AA6F6120EDEBBD80",
                DukptKsn.deriveWorkingKey(ipek, "FFFF9876543210E00008", DukptKsn.TdesKeyUsage.MAC_REQUEST).workingKeyHex());
    }

    // ---- EMV (Option A + common session key) ----

    private static final String TXN =
            "000000001000" + "000000000000" + "0724" + "0000000000" + "0978" + "250925"
                    + "00" + "12345678" + "1800" + "0001" + "03A4A000";

    /** Cross-checked: EMV Book 2 A1.4.1 option A, then A1.3 session key for ATC 0001. */
    @Test
    void emvKeyDerivation() throws Exception {
        String mk = EMVOperations.deriveICCMasterKey(K2, PAN, "00");
        assertEquals("30089565674D73ED841A6F0637029C18", mk);
        assertEquals("38F14068B3EA57C194F8E3A20D51E3E6", EMVOperations.deriveSessionKey(mk, "0001", ""));
    }

    /** Cross-checked: ARQC as ISO 9797-1 algorithm 3, padding 1 and 2, and ARPC method 1 with ARC "00". */
    @Test
    void emvArqcAndArpcMethod1() throws Exception {
        String sk = EMVOperations.deriveSessionKey(EMVOperations.deriveICCMasterKey(K2, PAN, "00"), "0001", "");
        assertEquals("E8499E593250A030", EMVOperations.generateARQC(sk, TXN, 1));
        String arqc = EMVOperations.generateARQC(sk, TXN, 2);
        assertEquals("A8DB2B65F9C821F1", arqc);
        assertEquals("ADCB085B842E0A9D", EMVOperations.generateARPC_Method1(sk, arqc, "00"));
    }
}
