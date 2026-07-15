package com.cryptoforge.crypto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TsaDiagnosticsTest {
    @Test
    void rejectsNonHttpTsaUrlsBeforeNetworkAccess() {
        assertThrows(IllegalArgumentException.class, () -> TsaDiagnostics.test("file:///tmp/tsa"));
    }
}
