package com.cryptocarver.model.process;

import com.cryptocarver.asn1.ASN1Parser;
import com.cryptocarver.asn1.ASN1TreeExporter;
import com.cryptocarver.asn1.ASN1TreeNode;
import com.cryptocarver.codec.ByteFormat;
import com.cryptocarver.codec.CodecRegistry;
import com.cryptocarver.crypto.ByteStatistics;
import com.cryptocarver.crypto.CheckDigitCalculator;
import com.cryptocarver.crypto.CompressionCodec;
import com.cryptocarver.crypto.EBCDICConverter;
import com.cryptocarver.crypto.KeyOperations;
import com.cryptocarver.crypto.KeyDerivation;
import com.cryptocarver.crypto.KeyMaterialInspector;
import com.cryptocarver.crypto.KeyWrapOperations;
import com.cryptocarver.crypto.TR31Operations;
import com.cryptocarver.crypto.icsf.IcsfTokenParser;
import com.cryptocarver.crypto.icsf.IcsfTokenReport;
import com.cryptocarver.crypto.icsf.Origin;
import com.cryptocarver.crypto.AsymmetricKeyOperations;
import com.cryptocarver.crypto.MACOperations;
import com.cryptocarver.crypto.ModularArithmetic;
import com.cryptocarver.model.process.handlers.EncodingFormatNodeHandler;
import com.cryptocarver.model.process.handlers.PlumbingNodeHandler;
import com.cryptocarver.model.process.handlers.UtilityInspectionNodeHandler;
import com.cryptocarver.model.process.handlers.KeyOperationsNodeHandler;
import com.cryptocarver.utils.PaddingUtil;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HexFormat;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Anti-reimplementation gate test (§5.2): asserts bit-for-bit parity between handler
 * execution and direct facade calls for all Ola 5B.1 nodes.
 */
class HandlerFacadeParityTest {

    private final PlumbingNodeHandler plumbing = new PlumbingNodeHandler();
    private final EncodingFormatNodeHandler encoding = new EncodingFormatNodeHandler();
    private final UtilityInspectionNodeHandler utility = new UtilityInspectionNodeHandler();
    private final KeyOperationsNodeHandler keys = new KeyOperationsNodeHandler();

    private static FlowValue hex(String value) {
        return FlowValue.hex(value.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void keyOperationsHandlerMatchesAllKeySideFacades() throws Exception {
        byte[] key = HexFormat.of().parseHex("00112233445566778899AABBCCDDEEFF");
        String keyHex = "00112233445566778899AABBCCDDEEFF";
        for (String method : List.of("VISA", "IBM", "ATALLA", "ATALLA_R", "FUTUREX", "CMAC", "AES", "SHA256", "FULL_ZERO_BLOCK")) {
            ProcessDefinition.Node kcv = new ProcessDefinition.Node("kcv-" + method, "KCV", "KCV", 0, 0);
            kcv.configuration.put("method", method);
            if ("FULL_ZERO_BLOCK".equals(method)) kcv.configuration.put("algorithm", "AES");
            byte[] direct = switch (method) {
                case "VISA" -> KeyOperations.calculateKCV_VISA(key);
                case "IBM" -> KeyOperations.calculateKCV_IBM(key);
                case "ATALLA" -> KeyOperations.calculateKCV_ATALLA(key);
                case "ATALLA_R" -> KeyOperations.calculateKCV_ATALLA_R(key);
                case "FUTUREX" -> KeyOperations.calculateKCV_FUTUREX(key);
                case "CMAC" -> KeyOperations.calculateKCV_CMAC(key);
                case "AES" -> KeyOperations.calculateKCV_AES(key);
                case "SHA256" -> KeyOperations.calculateKCV_SHA256(key);
                default -> KeyOperations.calculateFullZeroBlockKCV(key, "AES");
            };
            assertEquals(HexFormat.of().withUpperCase().formatHex(direct),
                    keys.execute(kcv, Map.of("key", hex(keyHex)), null).render(), method);
        }

        ProcessDefinition.Node parity = new ProcessDefinition.Node("parity", "PARITY_ADJUST", "Parity", 0, 0);
        byte[] directAdjusted = key.clone();
        KeyOperations.applyOddParity(directAdjusted);
        assertEquals(HexFormat.of().withUpperCase().formatHex(directAdjusted),
                keys.execute(parity, Map.of("key", hex(keyHex)), null).render());
        ProcessDefinition.Node check = new ProcessDefinition.Node("check", "PARITY_CHECK", "Parity", 0, 0);
        assertEquals(KeyOperations.detectParity(key).toString(), keys.execute(check, Map.of("key", hex("00112233445566778899AABBCCDDEEFF")), null).render());

        byte[][] parts = KeyOperations.splitKey(key, 3);
        String bundle = HexFormat.of().withUpperCase().formatHex(parts[0]) + ":" + HexFormat.of().withUpperCase().formatHex(parts[1]) + ":" + HexFormat.of().withUpperCase().formatHex(parts[2]);
        ProcessDefinition.Node split = new ProcessDefinition.Node("split", "KEY_SPLIT_XOR", "Split", 0, 0);
        split.configuration.put("componentCount", "3");
        FlowValue splitResult = keys.execute(split, Map.of("key", hex(keyHex)), null);
        assertEquals(Representation.HEX_COMPONENTS, splitResult.representation());
        String[] emittedParts = splitResult.render().split(":", -1);
        assertEquals(3, emittedParts.length);
        assertEquals(key.length, HexFormat.of().parseHex(emittedParts[0]).length);
        assertArrayEquals(key, KeyOperations.combineKeyComponents(Arrays.stream(emittedParts)
                .map(HexFormat.of()::parseHex).toArray(byte[][]::new)),
                "KEY_SPLIT_XOR must preserve the KeyOperations.splitKey contract");
        assertEquals(key.length, parts[0].length);
        assertArrayEquals(key, KeyOperations.combineKeyComponents(parts));

        ProcessDefinition.Node combine = new ProcessDefinition.Node("combine", "KEY_COMBINE_XOR", "Combine", 0, 0);
        assertEquals(HexFormat.of().withUpperCase().formatHex(KeyOperations.combineKeyComponents(parts)),
                keys.execute(combine, Map.of("components", FlowValue.hexComponents(bundle.getBytes(StandardCharsets.UTF_8))), null).render());
        ProcessDefinition.Node select = new ProcessDefinition.Node("select", "COMPONENT_SELECT", "Select", 0, 0);
        select.configuration.put("index", "2");
        assertEquals(HexFormat.of().withUpperCase().formatHex(parts[1]),
                keys.execute(select, Map.of("components", FlowValue.hexComponents(bundle.getBytes(StandardCharsets.UTF_8))), null).render());

        byte[] ikm = HexFormat.of().parseHex("0B0B0B0B0B0B0B0B0B0B0B0B0B0B0B0B0B0B0B0B0B0B");
        byte[] salt = HexFormat.of().parseHex("000102030405060708090A0B0C");
        byte[] info = HexFormat.of().parseHex("F0F1F2F3F4F5F6F7F8F9");
        ProcessDefinition.Node hkdf = new ProcessDefinition.Node("hkdf", "KDF_HKDF", "HKDF", 0, 0);
        hkdf.configuration.put("outputLength", "42");
        hkdf.configuration.put("digest", "SHA-256");
        assertArrayEquals(KeyDerivation.hkdf(ikm, salt, info, 42, KeyDerivation.getDigest("SHA-256")),
                keys.execute(hkdf, Map.of("ikm", FlowValue.binary(ikm), "salt", FlowValue.binary(salt), "info", FlowValue.binary(info)), null).bytes());

        ProcessDefinition.Node sp = new ProcessDefinition.Node("sp", "KDF_SP800_108", "SP800-108", 0, 0);
        sp.configuration.put("outputLength", "32");
        sp.configuration.put("digest", "SHA-256");
        assertArrayEquals(KeyDerivation.sp800108Counter(ikm, info, salt, 32, KeyDerivation.getDigest("SHA-256")),
                keys.execute(sp, Map.of("key", FlowValue.binary(ikm), "label", FlowValue.binary(info), "context", FlowValue.binary(salt)), null).bytes());

        ProcessDefinition.Node x963 = new ProcessDefinition.Node("x963", "KDF_X963", "X963", 0, 0);
        x963.configuration.put("outputLength", "32");
        x963.configuration.put("digest", "SHA-256");
        assertArrayEquals(KeyDerivation.x963(ikm, info, 32, KeyDerivation.getDigest("SHA-256")),
                keys.execute(x963, Map.of("sharedSecret", FlowValue.binary(ikm), "sharedInfo", FlowValue.binary(info)), null).bytes());

        ProcessDefinition.Node scrypt = new ProcessDefinition.Node("scrypt", "KDF_SCRYPT", "scrypt", 0, 0);
        scrypt.configuration.put("N", "16");
        scrypt.configuration.put("r", "1");
        scrypt.configuration.put("p", "1");
        scrypt.configuration.put("outputLength", "16");
        assertArrayEquals(KeyDerivation.scrypt(ikm, salt, 16, 1, 1, 16),
                keys.execute(scrypt, Map.of("password", FlowValue.binary(ikm), "salt", FlowValue.binary(salt)), null).bytes());

        ProcessDefinition.Node argon = new ProcessDefinition.Node("argon", "KDF_ARGON2", "Argon2", 0, 0);
        argon.configuration.put("iterations", "1");
        argon.configuration.put("memory", "8192");
        argon.configuration.put("parallelism", "1");
        argon.configuration.put("outputLength", "16");
        assertArrayEquals(KeyDerivation.argon2(ikm, salt, 1, 8192, 1, 16),
                keys.execute(argon, Map.of("password", FlowValue.binary(ikm), "salt", FlowValue.binary(salt)), null).bytes());

        byte[] kek = HexFormat.of().parseHex("000102030405060708090A0B0C0D0E0F");
        byte[] wrapped = KeyWrapOperations.wrapRfc3394(kek, key);
        byte[] wrapped5649Input = HexFormat.of().parseHex("466F7250617369");
        byte[] wrapped5649 = KeyWrapOperations.wrapRfc5649(kek, wrapped5649Input);
        ProcessDefinition.Node wrap = new ProcessDefinition.Node("wrap", "AES_KEYWRAP_3394", "Wrap", 0, 0);
        assertArrayEquals(wrapped, keys.execute(wrap, Map.of("kek", FlowValue.binary(kek), "keyData", FlowValue.binary(key)), null).bytes());
        ProcessDefinition.Node unwrap = new ProcessDefinition.Node("unwrap", "AES_UNWRAP_3394", "Unwrap", 0, 0);
        assertArrayEquals(key, keys.execute(unwrap, Map.of("kek", FlowValue.binary(kek), "wrapped", FlowValue.binary(wrapped)), null).bytes());
        ProcessDefinition.Node wrap5649 = new ProcessDefinition.Node("wrap5649", "AES_KEYWRAP_5649", "Wrap 5649", 0, 0);
        assertArrayEquals(wrapped5649, keys.execute(wrap5649, Map.of("kek", FlowValue.binary(kek), "keyData", FlowValue.binary(wrapped5649Input)), null).bytes());
        ProcessDefinition.Node unwrap5649 = new ProcessDefinition.Node("unwrap5649", "AES_UNWRAP_5649", "Unwrap 5649", 0, 0);
        assertArrayEquals(wrapped5649Input, keys.execute(unwrap5649, Map.of("kek", FlowValue.binary(kek), "wrapped", FlowValue.binary(wrapped5649)), null).bytes());

        String kbpk = "0123456789ABCDEFFEDCBA9876543210";
        String block = TR31Operations.wrapKey(kbpk, keyHex, "P0", 'B', 'T', 'E', 'N');
        ProcessDefinition.Node tr31 = new ProcessDefinition.Node("tr31", "TR31_WRAP", "TR31", 0, 0);
        tr31.configuration.put("usage", "P0");
        assertEquals(block, keys.execute(tr31, Map.of("kbpk", hex(kbpk), "key", hex(keyHex)), null).render());
        ProcessDefinition.Node tr31u = new ProcessDefinition.Node("tr31u", "TR31_UNWRAP", "TR31", 0, 0);
        assertEquals(keyHex, keys.execute(tr31u, Map.of("kbpk", hex(kbpk), "keyBlock", FlowValue.text(block, StandardCharsets.UTF_8)), null).render());
        ProcessDefinition.Node header = new ProcessDefinition.Node("header", "TR31_PARSE_HEADER", "TR31 header", 0, 0);
        assertEquals(TR31Operations.parseHeader(block), keys.execute(header, Map.of("keyBlock", FlowValue.text(block, StandardCharsets.UTF_8)), null).render());

        byte[] token = new byte[]{0, 0, 0, 0, 5, 0, 0, 0};
        ProcessDefinition.Node icsf = new ProcessDefinition.Node("icsf", "ICSF_TOKEN_PARSE", "ICSF", 0, 0);
        assertEquals(normalizeReport(IcsfTokenReport.renderText(IcsfTokenParser.parse(token, Origin.INFER), Origin.INFER, token)),
                normalizeReport(keys.execute(icsf, Map.of("token", FlowValue.binary(token)), null).render()));

        ProcessDefinition.Node pair = new ProcessDefinition.Node("pair", "KEYPAIR_GENERATE", "RSA pair", 0, 0);
        pair.configuration.put("algorithm", "RSA");
        pair.configuration.put("keySize", "1024");
        KeyPair directPair = AsymmetricKeyOperations.generateRSAKeyPair(1024);
        byte[] handlerEncoding = keys.execute(pair, Map.of(), null).bytes();
        RSAPrivateKey handlerPrivate = (RSAPrivateKey) KeyFactory.getInstance("RSA", "BC")
                .generatePrivate(new PKCS8EncodedKeySpec(handlerEncoding));
        assertEquals(directPair.getPrivate().getAlgorithm(), handlerPrivate.getAlgorithm());
        assertEquals(directPair.getPrivate().getFormat(), handlerPrivate.getFormat());
        assertEquals(((RSAPrivateKey) directPair.getPrivate()).getModulus().bitLength(), handlerPrivate.getModulus().bitLength());
        assertEquals("PKCS#8", handlerPrivate.getFormat());

        ProcessDefinition.Node inspect = new ProcessDefinition.Node("inspect", "KEY_MATERIAL_INSPECT", "Inspect", 0, 0);
        inspect.configuration.put("algorithm", "AES");
        assertEquals(KeyMaterialInspector.describeKey(new SecretKeySpec(key, "AES")),
                keys.execute(inspect, Map.of("key", FlowValue.binary(key)), null).render());
    }

    private static String normalizeReport(String report) {
        return report.replaceAll("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}", "<timestamp>");
    }

    @Test
    void testPadUnpadParityWithPaddingUtil() throws Exception {
        byte[] payload = "CryptoCarver_Padding_Payload".getBytes(StandardCharsets.UTF_8);

        // PAD PKCS7
        ProcessDefinition.Node padNode = new ProcessDefinition.Node("pad", "PAD", "PAD", 0, 0);
        padNode.configuration.put("paddingType", "PKCS7");
        padNode.configuration.put("blockSize", "16");

        FlowValue padResult = plumbing.execute(padNode, Map.of("input", FlowValue.binary(payload)), null);
        byte[] directPadded = PaddingUtil.addPadding(payload, 16, PaddingUtil.PaddingType.PKCS7);
        assertArrayEquals(directPadded, padResult.bytes(), "PAD result must be bit-identical to PaddingUtil.addPadding");

        // UNPAD PKCS7
        ProcessDefinition.Node unpadNode = new ProcessDefinition.Node("unpad", "UNPAD", "UNPAD", 0, 0);
        unpadNode.configuration.put("paddingType", "PKCS7");

        FlowValue unpadResult = plumbing.execute(unpadNode, Map.of("input", padResult), null);
        byte[] directUnpadded = PaddingUtil.removePadding(directPadded, PaddingUtil.PaddingType.PKCS7);
        assertArrayEquals(directUnpadded, unpadResult.bytes(), "UNPAD result must be bit-identical to PaddingUtil.removePadding");
        assertArrayEquals(payload, unpadResult.bytes());
    }

    @Test
    void testXorParityWithKeyOperations() throws Exception {
        byte[] a = new byte[]{0x01, 0x23, 0x45, 0x67, (byte) 0x89, (byte) 0xAB, (byte) 0xCD, (byte) 0xEF};
        byte[] b = new byte[]{(byte) 0xFE, (byte) 0xDC, (byte) 0xBA, (byte) 0x98, 0x76, 0x54, 0x32, 0x10};

        ProcessDefinition.Node xorNode = new ProcessDefinition.Node("xor", "XOR", "XOR", 0, 0);
        FlowValue xorResult = plumbing.execute(xorNode, Map.of(
                "a", FlowValue.binary(a),
                "b", FlowValue.binary(b)
        ), null);

        byte[] directXor = KeyOperations.xor(a, b);
        assertArrayEquals(directXor, xorResult.bytes(), "XOR result must be bit-identical to KeyOperations.xor");
    }

    @Test
    void testAssertEqualsParityWithMACOperations() throws Exception {
        byte[] data = "IdenticalData12345".getBytes(StandardCharsets.UTF_8);

        ProcessDefinition.Node assertNode = new ProcessDefinition.Node("assert", "ASSERT_EQUALS", "ASSERT_EQUALS", 0, 0);
        FlowValue result = plumbing.execute(assertNode, Map.of(
                "actual", FlowValue.binary(data),
                "expected", FlowValue.binary(Arrays.copyOf(data, data.length))
        ), null);

        assertTrue(MACOperations.constantTimeEquals(data, result.bytes()));
    }

    @Test
    void testBase32ParityWithCodecRegistry() throws Exception {
        byte[] data = "Hello Base32 World!".getBytes(StandardCharsets.UTF_8);

        // ENCODE
        ProcessDefinition.Node encNode = new ProcessDefinition.Node("b32e", "BASE32_ENCODE", "B32E", 0, 0);
        FlowValue encResult = encoding.execute(encNode, Map.of("input", FlowValue.binary(data)), null);
        String directEnc = CodecRegistry.getInstance().encode(data, ByteFormat.BASE32);
        assertEquals(directEnc, encResult.render(), "BASE32_ENCODE must match CodecRegistry.encode");

        // DECODE
        ProcessDefinition.Node decNode = new ProcessDefinition.Node("b32d", "BASE32_DECODE", "B32D", 0, 0);
        FlowValue decResult = encoding.execute(decNode, Map.of("input", encResult), null);
        byte[] directDec = CodecRegistry.getInstance().decode(directEnc, ByteFormat.BASE32);
        assertArrayEquals(directDec, decResult.bytes(), "BASE32_DECODE must match CodecRegistry.decode");
        assertArrayEquals(data, decResult.bytes());
    }

    @Test
    void testBase58ParityWithCodecRegistry() throws Exception {
        byte[] data = "CryptoCarver Base58 Test Vector".getBytes(StandardCharsets.UTF_8);

        // ENCODE
        ProcessDefinition.Node encNode = new ProcessDefinition.Node("b58e", "BASE58_ENCODE", "B58E", 0, 0);
        FlowValue encResult = encoding.execute(encNode, Map.of("input", FlowValue.binary(data)), null);
        String directEnc = CodecRegistry.getInstance().encode(data, ByteFormat.BASE58);
        assertEquals(directEnc, encResult.render(), "BASE58_ENCODE must match CodecRegistry.encode");

        // DECODE
        ProcessDefinition.Node decNode = new ProcessDefinition.Node("b58d", "BASE58_DECODE", "B58D", 0, 0);
        FlowValue decResult = encoding.execute(decNode, Map.of("input", encResult), null);
        byte[] directDec = CodecRegistry.getInstance().decode(directEnc, ByteFormat.BASE58);
        assertArrayEquals(directDec, decResult.bytes(), "BASE58_DECODE must match CodecRegistry.decode");
        assertArrayEquals(data, decResult.bytes());
    }

    @Test
    void testBase58CheckParityWithCodecRegistry() throws Exception {
        byte[] data = "Bitcoin Address Payload or similar".getBytes(StandardCharsets.UTF_8);

        // ENCODE
        ProcessDefinition.Node encNode = new ProcessDefinition.Node("b58ce", "BASE58CHECK_ENCODE", "B58CE", 0, 0);
        FlowValue encResult = encoding.execute(encNode, Map.of("input", FlowValue.binary(data)), null);
        String directEnc = CodecRegistry.getInstance().encode(data, ByteFormat.BASE58_CHECK);
        assertEquals(directEnc, encResult.render(), "BASE58CHECK_ENCODE must match CodecRegistry.encode");

        // DECODE
        ProcessDefinition.Node decNode = new ProcessDefinition.Node("b58cd", "BASE58CHECK_DECODE", "B58CD", 0, 0);
        FlowValue decResult = encoding.execute(decNode, Map.of("input", encResult), null);
        byte[] directDec = CodecRegistry.getInstance().decode(directEnc, ByteFormat.BASE58_CHECK);
        assertArrayEquals(directDec, decResult.bytes(), "BASE58CHECK_DECODE must match CodecRegistry.decode");
        assertArrayEquals(data, decResult.bytes());
    }

    @Test
    void testEbcdicParityWithEBCDICConverter() throws Exception {
        String text = "MAINFRAME EBCDIC TEST 123";
        String codePage = "IBM037 — US/Canada";

        // ENCODE
        ProcessDefinition.Node encNode = new ProcessDefinition.Node("ebce", "EBCDIC_ENCODE", "EBCE", 0, 0);
        encNode.configuration.put("codePage", codePage);
        FlowValue encResult = encoding.execute(encNode, Map.of("input", FlowValue.text(text, StandardCharsets.UTF_8)), null);
        byte[] directEnc = EBCDICConverter.encode(text, codePage);
        assertArrayEquals(directEnc, encResult.bytes(), "EBCDIC_ENCODE must match EBCDICConverter.encode");

        // DECODE
        ProcessDefinition.Node decNode = new ProcessDefinition.Node("ebcd", "EBCDIC_DECODE", "EBCD", 0, 0);
        decNode.configuration.put("codePage", codePage);
        FlowValue decResult = encoding.execute(decNode, Map.of("input", encResult), null);
        String directDec = EBCDICConverter.decode(directEnc, codePage);
        assertEquals(directDec, decResult.render(), "EBCDIC_DECODE must match EBCDICConverter.decode");
        assertEquals(text, decResult.render());
    }

    @Test
    void testCompressDecompressParityWithCompressionCodec() throws Exception {
        byte[] data = "A very repetitive string for compression testing: AAAAAAAAAABBBBBBBBBBCCCCCCCCCCDDDDDDDDDD".getBytes(StandardCharsets.UTF_8);

        for (String fmt : List.of("gzip", "zlib", "deflate")) {
            ProcessDefinition.Node compNode = new ProcessDefinition.Node("c", "COMPRESS", "C", 0, 0);
            compNode.configuration.put("format", fmt);
            FlowValue compResult = encoding.execute(compNode, Map.of("input", FlowValue.binary(data)), null);

            ProcessDefinition.Node decompNode = new ProcessDefinition.Node("d", "DECOMPRESS", "D", 0, 0);
            decompNode.configuration.put("format", fmt);
            FlowValue decompResult = encoding.execute(decompNode, Map.of("input", compResult), null);

            byte[] directDecomp = CompressionCodec.decompress(compResult.bytes(), fmt);
            assertArrayEquals(directDecomp, decompResult.bytes(), "DECOMPRESS must match CompressionCodec.decompress for " + fmt);
            assertArrayEquals(data, decompResult.bytes());
        }
    }

    @Test
    void testAsn1DecodeParityWithASN1ParserAndExporter() throws Exception {
        // Simple DER SEQUENCE containing an INTEGER 42
        // 30 03 02 01 2A
        byte[] der = new byte[]{0x30, 0x03, 0x02, 0x01, 0x2A};

        ProcessDefinition.Node node = new ProcessDefinition.Node("asn1", "ASN1_DECODE", "ASN1", 0, 0);
        node.configuration.put("outputFormat", "JSON");
        FlowValue result = utility.execute(node, Map.of("input", FlowValue.binary(der)), null);

        ASN1TreeNode directRoot = ASN1Parser.parse(der);
        String directJson = ASN1TreeExporter.toJson(directRoot);
        assertEquals(directJson, result.render(), "ASN1_DECODE JSON must match ASN1TreeExporter.toJson");

        node.configuration.put("outputFormat", "MARKDOWN");
        FlowValue resultMd = utility.execute(node, Map.of("input", FlowValue.binary(der)), null);
        String directMd = ASN1TreeExporter.toMarkdown(directRoot);
        assertEquals(directMd, resultMd.render(), "ASN1_DECODE MARKDOWN must match ASN1TreeExporter.toMarkdown");
    }

    @Test
    void testCheckDigitParityWithCheckDigitCalculator() throws Exception {
        String numericData = "7992739871";

        for (String alg : CheckDigitCalculator.SUPPORTED_ALGORITHMS) {
            // CALC
            ProcessDefinition.Node calcNode = new ProcessDefinition.Node("calc", "CHECK_DIGIT_CALC", "CALC", 0, 0);
            calcNode.configuration.put("algorithm", alg);
            FlowValue calcResult = utility.execute(calcNode, Map.of("input", FlowValue.text(numericData, StandardCharsets.UTF_8)), null);

            int directDigit = CheckDigitCalculator.calculateCheckDigit(numericData, alg);
            assertEquals(String.valueOf(directDigit), calcResult.render(), "CHECK_DIGIT_CALC must match CheckDigitCalculator for " + alg);

            // VERIFY
            String fullWithDigit = numericData + directDigit;
            ProcessDefinition.Node verifyNode = new ProcessDefinition.Node("verify", "CHECK_DIGIT_VERIFY", "VERIFY", 0, 0);
            verifyNode.configuration.put("algorithm", alg);
            FlowValue verifyResult = utility.execute(verifyNode, Map.of("input", FlowValue.text(fullWithDigit, StandardCharsets.UTF_8)), null);

            boolean directValid = CheckDigitCalculator.validateCheckDigit(fullWithDigit, alg);
            assertEquals(String.valueOf(directValid), verifyResult.render(), "CHECK_DIGIT_VERIFY must match CheckDigitCalculator for " + alg);
        }
    }

    @Test
    void testModularArithmeticParityWithModularArithmetic() throws Exception {
        String a = "1A";
        String b = "2B";
        String mod = "FF";

        ProcessDefinition.Node node = new ProcessDefinition.Node("mod", "MODULAR_ARITHMETIC", "MOD", 0, 0);
        node.configuration.put("operation", "ADD");
        node.configuration.put("modulus", mod);

        FlowValue result = utility.execute(node, Map.of(
                "a", FlowValue.text(a, StandardCharsets.UTF_8),
                "b", FlowValue.text(b, StandardCharsets.UTF_8)
        ), null);

        String directAdd = ModularArithmetic.modularAddition(a, b, mod);
        assertEquals(directAdd, result.render(), "MODULAR_ARITHMETIC ADD must match ModularArithmetic.modularAddition");

        node.configuration.put("operation", "MULTIPLY");
        FlowValue mulResult = utility.execute(node, Map.of(
                "a", FlowValue.text(a, StandardCharsets.UTF_8),
                "b", FlowValue.text(b, StandardCharsets.UTF_8)
        ), null);
        String directMul = ModularArithmetic.modularMultiplication(a, b, mod);
        assertEquals(directMul, mulResult.render(), "MODULAR_ARITHMETIC MULTIPLY must match ModularArithmetic.modularMultiplication");
    }

    @Test
    void testByteStatisticsParityWithByteStatistics() throws Exception {
        byte[] payload = "Entropy and byte frequency analysis test payload 1234567890".getBytes(StandardCharsets.UTF_8);

        ProcessDefinition.Node node = new ProcessDefinition.Node("stats", "BYTE_STATISTICS", "STATS", 0, 0);
        FlowValue result = utility.execute(node, Map.of("input", FlowValue.binary(payload)), null);

        String directStats = ByteStatistics.analyze(payload);
        assertEquals(directStats, result.render(), "BYTE_STATISTICS must match ByteStatistics.analyze");
    }
}
