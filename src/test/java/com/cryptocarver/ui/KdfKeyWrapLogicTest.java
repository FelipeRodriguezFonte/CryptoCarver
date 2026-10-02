package com.cryptocarver.ui;

import com.cryptocarver.crypto.KdfWrapTestVectors;
import com.cryptocarver.util.DataConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class KdfKeyWrapLogicTest {
    @ParameterizedTest
    @ValueSource(strings = {"HKDF-SHA1", "HKDF-SHA256", "HKDF-SHA512", "NIST-800-108-SHA256", "X9.63-SHA256",
            "PBKDF2-SHA1", "PBKDF2-SHA256", "PBKDF2-SHA512", "SCrypt", "Argon2id"})
    void derivesEachAlgorithmAgainstItsFixedVector(String algorithm) throws Exception {
        var vector = KeysSplit2Vectors.kdf(algorithm);
        var result = KdfKeyWrapLogic.derive(new KdfKeyWrapLogic.Request(algorithm, "Hex", "Hex", "Hex",
                vector.input(), vector.salt(), vector.info(), vector.iterations(), vector.length()));
        assertArrayEquals(vector.expected(), result.derivedKey());
        assertTrue(result.resultInfo().contains(DataConverter.bytesToHex(vector.expected())));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void wrapsAndUnwrapsTheSharedVector(boolean padded) throws Exception {
        String mode = padded ? "RFC 5649 - AES Key Wrap with Padding" : "RFC 3394 - AES Key Wrap";
        String input = padded ? KdfWrapTestVectors.HEX_9 : KdfWrapTestVectors.HEX_6;
        var wrapped = KdfKeyWrapLogic.wrap(KdfWrapTestVectors.HEX_7, input, false, mode);
        var unwrapped = KdfKeyWrapLogic.wrap(KdfWrapTestVectors.HEX_7, DataConverter.bytesToHex(wrapped.result()), true, mode);
        assertArrayEquals(DataConverter.hexToBytes(input), unwrapped.result());
        if (!padded) {
            assertArrayEquals(DataConverter.hexToBytes(KdfWrapTestVectors.HEX_8), wrapped.result());
        }
    }

    @Test
    void preservesTheOutputRangeValidationMessageAndField() {
        var request = new KdfKeyWrapLogic.Request("HKDF-SHA256", "UTF-8", "Hex", "UTF-8", "test", "", "", "1", "257");
        var error = assertThrows(KeysInputValidation.class, () -> KdfKeyWrapLogic.derive(request));
        assertEquals("Output length must be between 1 and 256 bytes.", error.getMessage());
        assertEquals("kdfOutputLengthField", error.field());
    }

    @Test
    void rejectsMalformedSaltBeforeDerivation() {
        var request = new KdfKeyWrapLogic.Request("HKDF-SHA256", "UTF-8", "Hex", "UTF-8", "test", "GG", "", "1", "32");
        var error = assertThrows(KeysInputValidation.class, () -> KdfKeyWrapLogic.derive(request));
        assertEquals("kdfSaltField", error.field());
    }

    @Test
    void rejectsUnwrappingWithTheWrongKek() {
        assertThrows(Exception.class, () -> KdfKeyWrapLogic.wrap("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",
                KdfWrapTestVectors.HEX_8, true, "RFC 3394 - AES Key Wrap"));
    }
}
