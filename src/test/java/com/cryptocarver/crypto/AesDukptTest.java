package com.cryptocarver.crypto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AesDukptTest {
    private static final String BDK = "FEDCBA9876543210F1F1F1F1F1F1F1F1";
    private static final String KSN = "123456789012345600000005";
    @Test void parsesAndAdvancesTheStandardTwelveByteKsn() {
        AesDukpt.ParsedKsn parsed = AesDukpt.parseKsn(KSN);
        assertEquals("1234567890123456", parsed.initialKeyIdHex()); assertEquals(5, parsed.transactionCounter());
        assertEquals("123456789012345600000006", AesDukpt.nextKsn(KSN));
    }
    @Test void matchesPublishedAes128InitialAndDataEncryptionVectors() throws Exception {
        assertEquals("1273671EA26AC29AFA4D1084127652A1", AesDukpt.deriveInitialKey(BDK, KSN));
        AesDukpt.DerivedKey key = AesDukpt.deriveWorkingKey(BDK, KSN, AesDukpt.KeyUsage.DATA_ENCRYPTION_ENCRYPT, AesDukpt.KeyType.AES128);
        assertEquals("CA02DF6F30B39E14BD0B4A30E460920F", key.workingKeyHex());
    }
    @Test void rejectsWrongAesKsnLength() { assertThrows(IllegalArgumentException.class, () -> AesDukpt.parseKsn("1234")); }

    @Test void encryptsAndDecryptsPublishedAesDukptPinBlockVector() throws Exception {
        String clear = "04124389999AAAABAAAAAAAAAAAAAAAA";
        String cryptogram = AesDukpt.cryptPinBlock(BDK, KSN, AesDukpt.KeyType.AES128, clear, false);
        assertEquals("AD444123078A462677E5718CDD833280", cryptogram);
        assertEquals(clear, AesDukpt.cryptPinBlock(BDK, KSN, AesDukpt.KeyType.AES128, cryptogram, true));
    }
}
