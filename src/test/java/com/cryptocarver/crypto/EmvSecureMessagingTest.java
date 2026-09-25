package com.cryptocarver.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Issuer-script secure messaging against the worked examples an external tool ships,
 * captured 2026-09-25 (EMV, Secure Messaging, MasterCard and Visa, default values).
 */
class EmvSecureMessagingTest {

    // ---- Mastercard ----

    private static final String MK_SMI = "862F13DF807A13B9D9AEAEC885FE7CA4";
    private static final String MK_SMC = "BF89B32308CDADDC04B952C7DF0715E0";
    private static final String PAN_SEQ = "7430100000157500";
    private static final String MC_AC = "51DB71A5DCC47F8A";

    @Test
    void mastercardCardKeysUseOptionAWithOddParity() {
        assertEquals("AEB0F198A498E067C4E63D94A770A80E", EmvSecureMessaging.mastercardUdk(MK_SMI, PAN_SEQ));
        assertEquals("640167C1D3C7623804FE97A75E2FC102", EmvSecureMessaging.mastercardUdk(MK_SMC, PAN_SEQ));
    }

    /** Command number 2 separates "AC + n" (…8C, captured) from "AC XOR n" (…88). */
    @Test
    void mastercardSessionKeysAddTheCommandNumberToTheAc() {
        String udkSmi = EmvSecureMessaging.mastercardUdk(MK_SMI, PAN_SEQ);
        String udkSmc = EmvSecureMessaging.mastercardUdk(MK_SMC, PAN_SEQ);
        assertEquals("1241B9DB1E953A02D5620E8B97418AE8", EmvSecureMessaging.mastercardSessionKey(udkSmi, MC_AC, 0));
        assertEquals("46AEA9871A61315C4E174FD9EBEB8AAC", EmvSecureMessaging.mastercardSessionKey(udkSmc, MC_AC, 0));
        assertEquals("E46C87DD5AC1177FCCE8F7A1C56A40C6", EmvSecureMessaging.mastercardSessionKey(udkSmi, MC_AC, 1));
        assertEquals("EA4F899B89521FC70B9A6E6DC44AD2A8", EmvSecureMessaging.mastercardSessionKey(udkSmc, MC_AC, 1));
        assertEquals("5011FA65D22A12DA14C69FB670605658", EmvSecureMessaging.mastercardSessionKey(udkSmi, MC_AC, 2));
        assertEquals("4CD2CC141DDD8DF6DFA6D2D5DD112903", EmvSecureMessaging.mastercardSessionKey(udkSmc, MC_AC, 2));
    }

    @Test
    void mastercardPinChangeCommand() throws Exception {
        assertEquals("2EC06BD5D6AEEBBC",
                EmvSecureMessaging.mastercardEncryptedPin("EA4F899B89521FC70B9A6E6DC44AD2A8", "4222"));
        assertEquals("AC4E7EB35196E310", EmvSecureMessaging.commandMac("E46C87DD5AC1177FCCE8F7A1C56A40C6",
                "8424000210", "0010", MC_AC, "2EC06BD5D6AEEBBC"));
    }

    // ---- Visa ----

    private static final String VISA_UDK = "94E3194C02105E3B153438D562D5A49D";
    private static final String VISA_SK = "94E3194C02105E38153438D562D55B61";

    @Test
    void visaSessionKeyXorsTheAtcAndItsComplement() {
        assertEquals(VISA_SK, EmvSecureMessaging.visaSessionKey(VISA_UDK, "0003"));
    }

    @Test
    void visaPinChangeCommand() throws Exception {
        String encryptedPin = EmvSecureMessaging.visaEncryptedPin(VISA_SK, "64C8621A76A2EA9EF23D5749FE1A64F1", "4222");
        assertEquals("B3511E3333BF9DC56E1EDF6458BB52B6", encryptedPin);
        assertEquals("E36046E6E5C110A2", EmvSecureMessaging.commandMac(VISA_SK,
                "8424000218", "0003", "EFB5340A1BF07421", encryptedPin));
    }
}
