package com.cryptocarver.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FileCipherRecipeCodecTest {

    @Test
    public void testSerializeDeserializeGcmLinesMode() {
        FileCipherRecipe recipe = new FileCipherRecipe(
            "AES-256-GCM",
            true,
            "Base64URL",
            true,
            "UTF-8",
            "01020304", // aadHex
            null, // ivNonceHex
            "detached.tag" // tagRef
        );

        String json = FileCipherRecipeCodec.serialize(recipe);

        // Ensure key is NOT in json
        assertFalse(json.toLowerCase().contains("key"), "JSON should never contain 'key' field");

        FileCipherRecipe restored = FileCipherRecipeCodec.deserialize(json);

        assertEquals(recipe, restored);
        assertEquals("AES-256-GCM", restored.getAlgorithm());
        assertTrue(restored.isLinesMode());
        assertTrue(restored.isCompactMode());
        assertEquals("Base64URL", restored.getLineEncoding());
        assertEquals("01020304", restored.getAadHex());
        assertEquals("detached.tag", restored.getTagRef());
        assertNull(restored.getIvNonceHex());
    }

    @Test
    public void testSerializeDeserializeCbcMode() {
        FileCipherRecipe recipe = new FileCipherRecipe(
            "AES-256-CBC",
            false,
            "Base64URL",
            false,
            null, // charset
            null, // aadHex
            "00112233445566778899AABBCCDDEEFF", // ivNonceHex
            null // tagRef
        );

        String json = FileCipherRecipeCodec.serialize(recipe);
        FileCipherRecipe restored = FileCipherRecipeCodec.deserialize(json);

        assertEquals(recipe, restored);
        assertEquals("AES-256-CBC", restored.getAlgorithm());
        assertEquals("00112233445566778899AABBCCDDEEFF", restored.getIvNonceHex());
    }

    @Test
    public void testSerializeDeserializeFullFileAeadMode() {
        FileCipherRecipe recipe = new FileCipherRecipe(
            "AES-256-GCM",
            false,
            null,
            false,
            null,
            null,
            "00112233445566778899AABB",
            "cipher.tag"
        );

        assertEquals(recipe, FileCipherRecipeCodec.deserialize(FileCipherRecipeCodec.serialize(recipe)));
    }

    @Test
    public void testUnsupportedVersion() {
        String json = "{\n" +
                "  \"version\": \"2.0\",\n" +
                "  \"algorithm\": \"AES-256-CBC\"\n" +
                "}";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> FileCipherRecipeCodec.deserialize(json));
        assertTrue(ex.getMessage().contains("Unsupported recipe version: 2.0"));
    }

    @Test
    public void testVersionValidation() {
        // Missing version
        assertThrows(IllegalArgumentException.class, () -> FileCipherRecipeCodec.deserialize("{ \"algorithm\": \"AES-256-CBC\" }"));
        // Null version
        assertThrows(IllegalArgumentException.class, () -> FileCipherRecipeCodec.deserialize("{ \"version\": null, \"algorithm\": \"AES-256-CBC\" }"));
        // Numeric version
        assertThrows(IllegalArgumentException.class, () -> FileCipherRecipeCodec.deserialize("{ \"version\": 1.0, \"algorithm\": \"AES-256-CBC\" }"));
        // Boolean version
        assertThrows(IllegalArgumentException.class, () -> FileCipherRecipeCodec.deserialize("{ \"version\": true, \"algorithm\": \"AES-256-CBC\" }"));
    }

    @Test
    public void testMalformedJson() {
        String json = "{ \"version\": \"1.0\", \"algorithm\": ";

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> FileCipherRecipeCodec.deserialize(json));
        assertTrue(ex.getMessage().contains("Malformed JSON format"));
    }

    @Test
    public void testRejectsSecretKeysInJson() {
        String json = "{\n" +
                "  \"version\": \"1.0\",\n" +
                "  \"algorithm\": \"AES-256-CBC\",\n" +
                "  \"key\": \"00112233\"\n" +
                "}";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> FileCipherRecipeCodec.deserialize(json));
        assertTrue(ex.getMessage().contains("Unknown property in recipe root"));

        String json2 = "{\n" +
                "  \"version\": \"1.0\",\n" +
                "  \"algorithm\": \"AES-256-CBC\",\n" +
                "  \"metadata\": { \"secretKey\": \"00112233\" }\n" +
                "}";
        ex = assertThrows(IllegalArgumentException.class, () -> FileCipherRecipeCodec.deserialize(json2));
        assertTrue(ex.getMessage().contains("Unknown property in recipe root: metadata"));

        // Let's test the nested key via allowed fields? Not possible since we restrict root fields.
        // But what if tagRef somehow is an object? GSON parses after, we check root fields first.
        // If they bypass root fields by using an allowed key but making it an object with a secret...
        // For example:
        String json3 = "{\n" +
                "  \"version\": \"1.0\",\n" +
                "  \"algorithm\": \"AES-256-CBC\",\n" +
                "  \"tagRef\": { \"PRIVATEKEY\": \"1234\" }\n" +
                "}";
        ex = assertThrows(IllegalArgumentException.class, () -> FileCipherRecipeCodec.deserialize(json3));
        assertTrue(ex.getMessage().contains("Security violation: JSON recipe cannot contain secret key material"));
    }

    @Test
    public void testCanonicalNormalization() {
        String json = "{\n" +
                "  \"version\": \"1.0\",\n" +
                "  \"algorithm\": \"aes-256-cbc\",\n" +
                "  \"lineEncoding\": \"HEXadecimal\",\n" +
                "  \"charset\": \"utf-8\",\n" +
                "  \"ivNonceHex\": \"00112233445566778899AABBCCDDEEFF\"\n" +
                "}";
        FileCipherRecipe recipe = FileCipherRecipeCodec.deserialize(json);
        assertEquals("AES-256-CBC", recipe.getAlgorithm());
        assertEquals("Hexadecimal", recipe.getLineEncoding());
        assertEquals("UTF-8", recipe.getCharset());
    }

    @Test
    public void testRejectsUnknownRootField() {
        String json = "{\n" +
                "  \"version\": \"1.0\",\n" +
                "  \"algorithm\": \"AES-256-CBC\",\n" +
                "  \"invalidField\": \"value\"\n" +
                "}";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> FileCipherRecipeCodec.deserialize(json));
        assertTrue(ex.getMessage().contains("Unknown property in recipe root: invalidField"));
    }

    @Test
    public void testCryptographicLengthsValidation() {
        // CBC IV missing (null)
        String json0 = "{ \"version\": \"1.0\", \"algorithm\": \"AES-256-CBC\" }";
        assertThrows(IllegalArgumentException.class, () -> FileCipherRecipeCodec.deserialize(json0));

        // CBC IV empty
        String json1 = "{ \"version\": \"1.0\", \"algorithm\": \"AES-256-CBC\", \"ivNonceHex\": \"\" }";
        assertThrows(IllegalArgumentException.class, () -> FileCipherRecipeCodec.deserialize(json1));

        // CBC IV odd length
        String json2 = "{ \"version\": \"1.0\", \"algorithm\": \"AES-256-CBC\", \"ivNonceHex\": \"123\" }";
        assertThrows(IllegalArgumentException.class, () -> FileCipherRecipeCodec.deserialize(json2));

        // CBC IV too short
        String json3 = "{ \"version\": \"1.0\", \"algorithm\": \"AES-256-CBC\", \"ivNonceHex\": \"00112233445566778899AABBCCDDEE\" }";
        assertThrows(IllegalArgumentException.class, () -> FileCipherRecipeCodec.deserialize(json3));

        // AAD empty
        String json4 = "{ \"version\": \"1.0\", \"algorithm\": \"AES-256-GCM\", \"aadHex\": \"\" }";
        assertThrows(IllegalArgumentException.class, () -> FileCipherRecipeCodec.deserialize(json4));

        // AAD odd length
        String json5 = "{ \"version\": \"1.0\", \"algorithm\": \"AES-256-GCM\", \"aadHex\": \"123\" }";
        assertThrows(IllegalArgumentException.class, () -> FileCipherRecipeCodec.deserialize(json5));

        // GCM file mode nonce missing
        String json6a = "{ \"version\": \"1.0\", \"algorithm\": \"AES-256-GCM\", \"linesMode\": false }";
        assertThrows(IllegalArgumentException.class, () -> FileCipherRecipeCodec.deserialize(json6a));

        // ChaCha file mode nonce missing
        String json6b = "{ \"version\": \"1.0\", \"algorithm\": \"ChaCha20-Poly1305\", \"linesMode\": false }";
        assertThrows(IllegalArgumentException.class, () -> FileCipherRecipeCodec.deserialize(json6b));

        // GCM file mode nonce too short
        String json6 = "{ \"version\": \"1.0\", \"algorithm\": \"AES-256-GCM\", \"linesMode\": false, \"ivNonceHex\": \"00112233445566778899AA\" }";
        assertThrows(IllegalArgumentException.class, () -> FileCipherRecipeCodec.deserialize(json6));

        // GCM lines mode explicit empty nonce -> rejected
        String json7 = "{ \"version\": \"1.0\", \"algorithm\": \"AES-256-GCM\", \"linesMode\": true, \"ivNonceHex\": \"\" }";
        assertThrows(IllegalArgumentException.class, () -> FileCipherRecipeCodec.deserialize(json7));
    }

    @Test
    public void testSerializeValidatesRecipe() {
        // CBC sin IV
        FileCipherRecipe badCbc = new FileCipherRecipe(
            "AES-256-CBC", false, null, false, null, null, null, null
        );
        assertThrows(IllegalArgumentException.class, () -> FileCipherRecipeCodec.serialize(badCbc));

        // AEAD sin nonce en full file mode
        FileCipherRecipe badGcm = new FileCipherRecipe(
            "AES-256-GCM", false, null, false, null, null, null, null
        );
        assertThrows(IllegalArgumentException.class, () -> FileCipherRecipeCodec.serialize(badGcm));

        // AEAD full file mode requires the detached tag reference too.
        FileCipherRecipe missingTag = new FileCipherRecipe(
            "AES-256-GCM", false, null, false, null, null,
            "00112233445566778899AABB", null
        );
        assertThrows(IllegalArgumentException.class, () -> FileCipherRecipeCodec.serialize(missingTag));
    }

    @Test
    public void testLinesModeRequiresFormatAndCharset() {
        String common = "{ \"version\": \"1.0\", \"algorithm\": \"AES-256-GCM\", \"linesMode\": true";
        assertThrows(IllegalArgumentException.class,
            () -> FileCipherRecipeCodec.deserialize(common + ", \"charset\": \"UTF-8\" }"));
        assertThrows(IllegalArgumentException.class,
            () -> FileCipherRecipeCodec.deserialize(common + ", \"lineEncoding\": \"Hexadecimal\" }"));
    }

    @Test
    public void testRejectsInvalidTagRef() {
        String json1 = "{ \"version\": \"1.0\", \"algorithm\": \"AES-256-GCM\", \"tagRef\": \"../detached.tag\" }";
        assertThrows(IllegalArgumentException.class, () -> FileCipherRecipeCodec.deserialize(json1));

        String json2 = "{ \"version\": \"1.0\", \"algorithm\": \"AES-256-GCM\", \"tagRef\": \"/tmp/detached.tag\" }";
        assertThrows(IllegalArgumentException.class, () -> FileCipherRecipeCodec.deserialize(json2));

        String json3 = "{ \"version\": \"1.0\", \"algorithm\": \"AES-256-GCM\", \"tagRef\": \"a\\\\b.tag\" }";
        assertThrows(IllegalArgumentException.class, () -> FileCipherRecipeCodec.deserialize(json3));
    }

    @Test
    public void testRejectsInvalidAadForCbc() {
        String json = "{ \"version\": \"1.0\", \"algorithm\": \"AES-256-CBC\", \"ivNonceHex\": \"00112233445566778899AABBCCDDEEFF\", \"aadHex\": \"1234\" }";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> FileCipherRecipeCodec.deserialize(json));
        assertTrue(ex.getMessage().contains("AAD is only supported for AEAD algorithms"));
    }

    @Test
    public void testRejectsNonceForAeadLinesMode() {
        String json = "{ \"version\": \"1.0\", \"algorithm\": \"AES-256-GCM\", \"linesMode\": true, \"lineEncoding\": \"Hexadecimal\", \"charset\": \"UTF-8\", \"ivNonceHex\": \"1234\" }";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> FileCipherRecipeCodec.deserialize(json));
        assertTrue(ex.getMessage().contains("IV/Nonce should not be provided for AEAD algorithms in lines mode"));
    }

    @Test
    public void testRejectsInvalidCharset() {
        String json = "{ \"version\": \"1.0\", \"algorithm\": \"AES-256-CBC\", \"charset\": \"ISO-8859-99\" }";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> FileCipherRecipeCodec.deserialize(json));
        assertTrue(ex.getMessage().contains("Unsupported charset: ISO-8859-99"));
    }
}
