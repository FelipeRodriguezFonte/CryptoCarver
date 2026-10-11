package com.cryptocarver.model.process;

import static org.junit.jupiter.api.Assertions.*;

import com.cryptocarver.model.help.B05HelpCatalog;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Consumer vectors for the exact 36-node C010 subdelivery; no engine behavior is changed. */
class B052B1ReviewedNodeConsumersTest {
    @TempDir Path temp;

    @Test void eachReviewedNodeExampleIsCheckedAgainstItsCurrentHandler() throws Exception {
        var seen = new java.util.LinkedHashSet<String>();
        ProcessDefinition consoleInput = graph(); add(consoleInput, "target", "CONSOLE_INPUT", Map.of("value", "A", "charset", "UTF-8"));
        assertEquals("A", ProcessEngine.execute(consoleInput).get("target").render()); seen.add("CONSOLE_INPUT");
        assertEquals("4142", runFileInput(new byte[]{0x41, 0x42}).render()); seen.add("FILE_INPUT");
        assertEquals("Hi", run("CONSOLE_OUTPUT", "Hi", null, Map.of()).render()); seen.add("CONSOLE_OUTPUT");
        Path outputFile = temp.resolve("written.bin");
        run("FILE_OUTPUT", "OK", null, Map.of("path", outputFile.toString(), "writeMode", "BINARY"));
        assertArrayEquals(new byte[]{0x4f, 0x4b}, Files.readAllBytes(outputFile)); seen.add("FILE_OUTPUT");

        assertEquals("TWFu", run("BASE64_ENCODE", "Man", null, Map.of()).render()); seen.add("BASE64_ENCODE");
        assertEquals("4869", run("BASE64_DECODE", "SGk=", null, Map.of()).render()); seen.add("BASE64_DECODE");
        assertEquals("-_8", runFileInputTo("BASE64URL_ENCODE", new byte[]{(byte) 0xfb, (byte) 0xff}, Map.of()).render()); seen.add("BASE64URL_ENCODE");
        assertEquals("FBFF", run("BASE64URL_DECODE", "-_8", null, Map.of()).render()); seen.add("BASE64URL_DECODE");
        assertEquals("4869", run("HEX_ENCODE", "Hi", null, Map.of()).render()); seen.add("HEX_ENCODE");
        assertEquals("4869", run("HEX_DECODE", "4869", null, Map.of()).render()); seen.add("HEX_DECODE");
        assertEquals("4869", run("UTF8_ENCODE", "Hi", null, Map.of()).render()); seen.add("UTF8_ENCODE");
        assertEquals("Hi", runFileInputTo("UTF8_DECODE", new byte[]{0x48, 0x69}, Map.of()).render()); seen.add("UTF8_DECODE");
        assertEquals("MY======", run("BASE32_ENCODE", "f", null, Map.of()).render()); seen.add("BASE32_ENCODE");
        assertEquals("66", run("BASE32_DECODE", "MY======", null, Map.of()).render()); seen.add("BASE32_DECODE");
        assertEquals("12g", runFileInputTo("BASE58_ENCODE", new byte[]{0, 0x61}, Map.of()).render()); seen.add("BASE58_ENCODE");
        assertEquals("0061", run("BASE58_DECODE", "12g", null, Map.of()).render()); seen.add("BASE58_DECODE");
        assertArrayEquals(new byte[]{0, 0x61}, runBase58CheckRoundTrip(new byte[]{0, 0x61}).bytes());
        seen.add("BASE58CHECK_ENCODE"); seen.add("BASE58CHECK_DECODE");
        assertEquals("C1", run("EBCDIC_ENCODE", "A", null, Map.of("codePage", "IBM037 — US/Canada")).render()); seen.add("EBCDIC_ENCODE");
        assertEquals("A", runFileInputTo("EBCDIC_DECODE", new byte[]{(byte) 0xc1}, Map.of("codePage", "IBM037 — US/Canada")).render()); seen.add("EBCDIC_DECODE");
        assertArrayEquals("hello".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                runCompressionRoundTrip("hello", "gzip").bytes()); seen.add("COMPRESS"); seen.add("DECOMPRESS");
        assertEquals("é", runFileInputTo("CHARSET_CONVERT", new byte[]{(byte) 0xe9},
                Map.of("sourceCharset", "ISO-8859-1", "targetCharset", "UTF-8")).render()); seen.add("CHARSET_CONVERT");

        assertEquals("4142", runTwoInputs("CONCAT", "A", "B", Map.of(), "a", "b").render()); seen.add("CONCAT");
        assertEquals("4243", run("SLICE", "ABCD", null, Map.of("offset", "1", "length", "2")).render()); seen.add("SLICE");
        assertEquals("41030303", run("PAD", "A", null, Map.of("paddingType", "PKCS7", "blockSize", "4")).render()); seen.add("PAD");
        assertEquals("41", runPaddingRoundTrip("A").render()); seen.add("UNPAD");
        assertEquals("03", runTwoInputs("XOR", "A", "B", Map.of(), "a", "b").render()); seen.add("XOR");
        assertEquals("A", runTwoInputs("ASSERT_EQUALS", "A", "A", Map.of(), "actual", "expected").render()); seen.add("ASSERT_EQUALS");

        assertTrue(runFileInputTo("ASN1_DECODE", new byte[]{0x02, 0x01, 0x01}, Map.of("outputFormat", "JSON"))
                .render().contains("1")); seen.add("ASN1_DECODE");
        assertEquals("3", run("CHECK_DIGIT_CALC", "7992739871", null, Map.of("algorithm", "Luhn (Mod 10)")).render()); seen.add("CHECK_DIGIT_CALC");
        assertEquals("true", run("CHECK_DIGIT_VERIFY", "79927398713", null, Map.of("algorithm", "Luhn (Mod 10)")).render()); seen.add("CHECK_DIGIT_VERIFY");
        BigInteger modular = new BigInteger(runTwoInputs("MODULAR_ARITHMETIC", "03", "04",
                Map.of("operation", "ADD", "modulus", "0B"), "a", "b").render(), 16);
        assertEquals(BigInteger.valueOf(7), modular); seen.add("MODULAR_ARITHMETIC");
        ProcessDefinition uuid = graph(); add(uuid, "target", "UUID_GENERATE", Map.of("format", "STANDARD"));
        assertTrue(ProcessEngine.execute(uuid).get("target").render().matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")); seen.add("UUID_GENERATE");
        assertTrue(runFileInputTo("BYTE_STATISTICS", new byte[]{0, 0, (byte) 0xff}, Map.of()).render().contains("3")); seen.add("BYTE_STATISTICS");

        ProcessDefinition legacy = graph();
        sourceText(legacy, "src", "A");
        add(legacy, "target", "BASE64", Map.of());
        connect(legacy, "src", "target", "input");
        Exception legacyError = assertThrows(Exception.class, () -> ProcessEngine.execute(legacy));
        assertTrue(legacyError.getMessage().contains("Unknown codec type: BASE64")); seen.add("BASE64");
        assertEquals(B05HelpCatalog.verifiedB052B1NodeTypes(), seen, "A consumer vector or documented error is required for each reviewed node");
    }

    @Test void documentedNegativeExamplesAndNonErrorOutcomesMatchCurrentConsumers() throws Exception {
        ProcessDefinition missingTextFile = graph();
        add(missingTextFile, "target", "FILE_INPUT", Map.of("path", temp.resolve("missing.txt").toString(), "readMode", "TEXT"));
        assertThrows(Exception.class, () -> ProcessEngine.execute(missingTextFile), "A missing source path is a real file-input failure");

        Path missingParent = temp.resolve("missing-parent").resolve("out.bin");
        assertThrows(Exception.class, () -> run("FILE_OUTPUT", "OK", null,
                Map.of("path", missingParent.toString(), "writeMode", "BINARY")), "FILE_OUTPUT reports a real I/O error");

        assertThrows(Exception.class, () -> run("BASE64_DECODE", "SG!k", null, Map.of()));
        assertThrows(Exception.class, () -> run("BASE64URL_DECODE", "+/8", null, Map.of()));
        assertThrows(Exception.class, () -> run("HEX_DECODE", "48G1", null, Map.of()));
        assertThrows(Exception.class, () -> run("BASE32_DECODE", "M0======", null, Map.of()));
        assertThrows(Exception.class, () -> run("BASE58_DECODE", "0OIl", null, Map.of()));
        assertThrows(Exception.class, () -> run("BASE58CHECK_DECODE", "1", null, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> run("EBCDIC_DECODE", "A", null,
                Map.of("codePage", "IBM999 — unsupported")));
        assertThrows(IllegalArgumentException.class, () -> run("COMPRESS", "hello", null, Map.of("format", "unsupported")));

        ProcessDefinition mismatchedWrappers = graph();
        sourceText(mismatchedWrappers, "src", "hello");
        add(mismatchedWrappers, "compress", "COMPRESS", Map.of("format", "gzip"));
        add(mismatchedWrappers, "decompress", "DECOMPRESS", Map.of("format", "zlib"));
        connect(mismatchedWrappers, "src", "compress", "input");
        connect(mismatchedWrappers, "compress", "decompress", "input");
        assertThrows(Exception.class, () -> ProcessEngine.execute(mismatchedWrappers));

        byte[] converted = runFileInputTo("CHARSET_CONVERT", new byte[]{(byte) 0xe9},
                Map.of("sourceCharset", "ISO-8859-1", "targetCharset", "US-ASCII")).bytes();
        assertArrayEquals(new byte[]{0x3f}, converted, "Unmappable é is replaced by ?, and the operation succeeds");
        assertEquals("false", run("CHECK_DIGIT_VERIFY", "79927398714", null,
                Map.of("algorithm", "Luhn (Mod 10)")).render(), "A mismatched check digit is false, not an exception");
        assertArrayEquals(new byte[]{(byte) 0xef, (byte) 0xbf, (byte) 0xbd, 0x28},
                runFileInputTo("UTF8_DECODE", new byte[]{(byte) 0xc3, 0x28}, Map.of()).bytes(),
                "Malformed UTF-8 is replaced rather than rejected");
        assertArrayEquals(new byte[]{0x3f}, ProcessEngine.execute(consoleInput("ñ", "US-ASCII")).get("target").bytes());
        assertArrayEquals(new byte[]{0x3f},
                run("EBCDIC_ENCODE", "漢", null, Map.of("codePage", "IBM037 — US/Canada")).bytes(),
                "Java CharsetEncoder substitutes its replacement byte for an unrepresentable EBCDIC character");

        assertThrows(IllegalArgumentException.class, () -> run("SLICE", "ABCD", null,
                Map.of("offset", "5", "length", "1")));
        assertThrows(IllegalArgumentException.class, () -> run("SLICE", "ABCD", null,
                Map.of("offset", "0", "length", "5")));
        assertThrows(IllegalArgumentException.class, () -> run("PAD", "A", null,
                Map.of("paddingType", "PKCS7", "blockSize", "0")));
        assertThrows(IllegalArgumentException.class, () -> runFileInputTo("UNPAD", new byte[]{0x41, 0x02, 0x03},
                Map.of("paddingType", "PKCS7")));
        assertThrows(IllegalArgumentException.class, () -> runTwoInputs("XOR", "AA", "B", Map.of(), "a", "b"));
        assertThrows(IllegalStateException.class, () -> runTwoInputs("ASSERT_EQUALS", "CAFE", "CAFF",
                Map.of(), "actual", "expected"));
        assertThrows(Exception.class, () -> runFileInputTo("ASN1_DECODE", new byte[]{0x02, 0x02, (byte) 0xff}, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> run("CHECK_DIGIT_CALC", "", null,
                Map.of("algorithm", "Luhn (Mod 10)")));
        assertThrows(Exception.class, () -> runTwoInputs("MODULAR_ARITHMETIC", "06", "01",
                Map.of("operation", "MULTIPLICATIVE_INVERSE", "modulus", "09"), "a", "b"));
    }

    @Test void everyLiveRequiredPortAndRepresentationMismatchIsChecked() throws Exception {
        for (String type : B05HelpCatalog.verifiedB052B1NodeTypes()) {
            var descriptor = NodeCatalog.getDescriptor(type).orElseThrow();
            ProcessDefinition definition = graph();
            ProcessDefinition.Node target = new ProcessDefinition.Node("target", type, type, 0, 0);
            target.configuration.putAll(NodeCatalog.defaultConfiguration(type));
            definition.nodes.add(target);
            var required = ProcessEngine.getHandlerFor(type).inputPorts(target).stream()
                    .filter(ProcessNodeHandler.PortDefinition::required).findFirst();
            if (required.isPresent()) {
                String port = required.orElseThrow().name();
                IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                        () -> ProcessEngine.validate(definition), type + " must reject missing required input " + port);
                assertTrue(error.getMessage().contains("port '" + port + "'"), type + " / " + port);
            } else assertDoesNotThrow(() -> ProcessEngine.validate(definition), type + " is a valid source/no-input node");
            assertEquals(type, descriptor.type());
        }

        ProcessDefinition binaryToUtf8Encoder = graph();
        addFileInput(binaryToUtf8Encoder, "src", new byte[]{0x41});
        add(binaryToUtf8Encoder, "target", "UTF8_ENCODE", Map.of());
        connect(binaryToUtf8Encoder, "src", "target", "input");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> ProcessEngine.validate(binaryToUtf8Encoder))
                .getMessage().contains("expects"));

        ProcessDefinition textToUtf8Decoder = graph();
        sourceText(textToUtf8Decoder, "src", "A");
        add(textToUtf8Decoder, "target", "UTF8_DECODE", Map.of());
        connect(textToUtf8Decoder, "src", "target", "input");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> ProcessEngine.validate(textToUtf8Decoder))
                .getMessage().contains("expects"));

        ProcessDefinition base64ToHexDecoder = graph();
        sourceText(base64ToHexDecoder, "src", "Hi");
        add(base64ToHexDecoder, "encode", "BASE64_ENCODE", Map.of());
        add(base64ToHexDecoder, "target", "HEX_DECODE", Map.of());
        connect(base64ToHexDecoder, "src", "encode", "input");
        connect(base64ToHexDecoder, "encode", "target", "input");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> ProcessEngine.validate(base64ToHexDecoder))
                .getMessage().contains("expects"));

        ProcessDefinition unknownPort = graph();
        sourceText(unknownPort, "src", "A"); add(unknownPort, "target", "HEX_DECODE", Map.of());
        connect(unknownPort, "src", "target", "not_a_port");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> ProcessEngine.validate(unknownPort))
                .getMessage().contains("Unknown target port"));
    }

    @Test void everyPadAndUnpadOptionMatchesTheNamedByteRule() throws Exception {
        Map<String, byte[]> padded = Map.of(
                "PKCS7", new byte[]{0x41, 0x03, 0x03, 0x03},
                "PKCS5", new byte[]{0x41, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07},
                "ISO_9797_M1", new byte[]{0x41, 0x00, 0x00, 0x00},
                "ISO_9797_M2", new byte[]{0x41, (byte) 0x80, 0x00, 0x00},
                "ISO_7816_4", new byte[]{0x41, (byte) 0x80, 0x00, 0x00},
                "ZERO", new byte[]{0x41, 0x00, 0x00, 0x00});
        for (var option : padded.entrySet()) {
            byte[] result = run("PAD", "A", null, Map.of("paddingType", option.getKey(), "blockSize", "4")).bytes();
            assertArrayEquals(option.getValue(), result, "PAD " + option.getKey());
            assertArrayEquals(new byte[]{0x41}, runFileInputTo("UNPAD", result,
                    Map.of("paddingType", option.getKey())).bytes(), "UNPAD " + option.getKey());
        }
        assertArrayEquals(new byte[]{0x41, 0x01}, run("PAD", "A", null,
                Map.of("paddingType", "PKCS7", "blockSize", "1")).bytes());
        assertArrayEquals(new byte[]{0x41, 0x42, 0x43, 0x44, 0x04, 0x04, 0x04, 0x04}, run("PAD", "ABCD", null,
                Map.of("paddingType", "PKCS7", "blockSize", "4")).bytes(), "PKCS7 adds a full block to already aligned input");
        assertArrayEquals(new byte[]{0x41}, run("PAD", "A", null,
                Map.of("paddingType", "ZERO", "blockSize", "1")).bytes(), "ZERO does not add bytes to aligned input");
        assertArrayEquals(new byte[]{0x41}, runFileInputTo("UNPAD", new byte[]{0x41, 0x00},
                Map.of("paddingType", "ZERO")).bytes(), "ZERO removal is lossy for legitimate trailing zero data");
        assertArrayEquals(new byte[]{0x41, 0x04, 0x04, 0x04, 0x04}, run("PAD", "A", null,
                Map.of("paddingType", "PKCS7", "blockSize", "5")).bytes());
    }

    private FlowValue run(String type, String text, byte[] binary, Map<String, String> config) throws Exception {
        ProcessDefinition p = graph();
        if (text != null) sourceText(p, "src", text); else sourceBytes(p, "src", binary);
        add(p, "target", type, config); connect(p, "src", "target", "input");
        return ProcessEngine.execute(p).get("target");
    }

    private FlowValue runFileInput(byte[] bytes) throws Exception {
        ProcessDefinition p = graph(); addFileInput(p, "target", bytes); return ProcessEngine.execute(p).get("target");
    }

    private FlowValue runFileInputTo(String type, byte[] bytes, Map<String, String> config) throws Exception {
        ProcessDefinition p = graph(); addFileInput(p, "src", bytes); add(p, "target", type, config);
        connect(p, "src", "target", "input"); return ProcessEngine.execute(p).get("target");
    }

    private FlowValue runTwoInputs(String type, String a, String b, Map<String, String> config,
                                                String portA, String portB) throws Exception {
        ProcessDefinition p = graph(); sourceText(p, "a_src", a); sourceText(p, "b_src", b); add(p, "target", type, config);
        connect(p, "a_src", "target", portA); connect(p, "b_src", "target", portB);
        return ProcessEngine.execute(p).get("target");
    }

    private FlowValue runBase58CheckRoundTrip(byte[] bytes) throws Exception {
        ProcessDefinition p = graph(); addFileInput(p, "src", bytes); add(p, "encode", "BASE58CHECK_ENCODE", Map.of());
        add(p, "decode", "BASE58CHECK_DECODE", Map.of()); connect(p, "src", "encode", "input");
        connect(p, "encode", "decode", "input"); return ProcessEngine.execute(p).get("decode");
    }

    private FlowValue runCompressionRoundTrip(String value, String format) throws Exception {
        ProcessDefinition p = graph(); sourceText(p, "src", value); add(p, "compress", "COMPRESS", Map.of("format", format));
        add(p, "decompress", "DECOMPRESS", Map.of("format", format)); connect(p, "src", "compress", "input");
        connect(p, "compress", "decompress", "input"); return ProcessEngine.execute(p).get("decompress");
    }

    private FlowValue runPaddingRoundTrip(String value) throws Exception {
        ProcessDefinition p = graph(); sourceText(p, "src", value); add(p, "pad", "PAD", Map.of("paddingType", "PKCS7", "blockSize", "4"));
        add(p, "unpad", "UNPAD", Map.of("paddingType", "PKCS7")); connect(p, "src", "pad", "input");
        connect(p, "pad", "unpad", "input"); return ProcessEngine.execute(p).get("unpad");
    }

    private void addFileInput(ProcessDefinition p, String id, byte[] bytes) throws Exception {
        Path source = temp.resolve(id + "-" + System.nanoTime() + ".bin"); Files.write(source, bytes);
        add(p, id, "FILE_INPUT", Map.of("path", source.toString(), "readMode", "BINARY"));
    }

    private static void sourceText(ProcessDefinition p, String id, String value) {
        add(p, id, "CONSOLE_INPUT", Map.of("value", value, "charset", "UTF-8"));
    }

    private static ProcessDefinition consoleInput(String value, String charset) {
        ProcessDefinition p = graph(); add(p, "target", "CONSOLE_INPUT", Map.of("value", value, "charset", charset)); return p;
    }

    private void sourceBytes(ProcessDefinition p, String id, byte[] value) throws Exception {
        addFileInput(p, id, value);
    }

    private static void add(ProcessDefinition p, String id, String type, Map<String, String> config) {
        ProcessDefinition.Node n = new ProcessDefinition.Node(id, type, type, p.nodes.size(), 0);
        n.configuration.putAll(config); p.nodes.add(n);
    }

    private static void connect(ProcessDefinition p, String from, String to, String port) {
        p.connections.add(new ProcessDefinition.Connection(from, to, port));
    }

    private static ProcessDefinition graph() { return new ProcessDefinition(); }
}
