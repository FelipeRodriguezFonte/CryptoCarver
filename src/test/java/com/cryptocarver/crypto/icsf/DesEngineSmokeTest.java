package com.cryptocarver.crypto.icsf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cryptocarver.crypto.icsf.keywrap.Des;
import com.cryptocarver.crypto.icsf.keywrap.DesKeyCheck;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/** The classic FIPS 46-3 vector, and the two verification numbers. */
class DesEngineSmokeTest {

    private static byte[] h(String s) {
        return HexFormat.of().parseHex(s);
    }

    private static String x(byte[] b) {
        return HexFormat.of().formatHex(b).toUpperCase();
    }

    @Test
    void singleDesMatchesTheClassicVector() {
        assertEquals("85E813540F0AB405", x(Des.encryptBlock(h("133457799BBCDFF1"), h("0123456789ABCDEF"))));
        assertEquals("0123456789ABCDEF", x(Des.decryptBlock(h("133457799BBCDFF1"), h("85E813540F0AB405"))));
    }

    @Test
    void encZeroMatchesTheReferenceKcv() {
        assertEquals("08D7B4", x(DesKeyCheck.encZero(h("0123456789ABCDEFFEDCBA9876543210"))));
    }

    @Test
    void tdesRoundTripsInBothModes() {
        byte[] key = h("0123456789ABCDEFFEDCBA9876543210");
        byte[] data = h("00112233445566778899AABBCCDDEEFF");
        assertEquals(x(data), x(Des.tdesEcbDecrypt(key, Des.tdesEcbEncrypt(key, data))));
        assertEquals(x(data), x(Des.tdesCbcDecrypt(key, Des.tdesCbcEncrypt(key, data))));
    }
}
