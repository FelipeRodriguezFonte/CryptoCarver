package com.cryptoforge.crypto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DukptKsnTest {
    @Test void parsesKnownTdesKsnCounter() {
        DukptKsn.Parsed parsed = DukptKsn.parseTdes("FFFF9876543210E00008");
        assertEquals(8, parsed.transactionCounter());
        assertEquals("FFFF9876543210E00000", parsed.baseKsnHex());
    }
    @Test void rejectsMalformedKsn() {
        assertThrows(IllegalArgumentException.class, () -> DukptKsn.parseTdes("FFFF"));
    }
    @Test void derivesKnownIpek() throws Exception {
        assertEquals("6AC292FAA1315B4D858AB3A3D7D5933A",
                DukptKsn.deriveIpek("0123456789ABCDEFFEDCBA9876543210", "FFFF9876543210E00008"));
    }
    @Test void advancesCounterWithoutChangingKsnIdentity() {
        assertEquals("FFFF9876543210E00009", DukptKsn.nextTdesKsn("FFFF9876543210E00008"));
    }
    @Test void rejectsCounterWrapAround() {
        assertThrows(IllegalStateException.class, () -> DukptKsn.nextTdesKsn("FFFF9876543210FFFFFF"));
    }
}
