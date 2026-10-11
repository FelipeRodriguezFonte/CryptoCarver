package com.cryptocarver.model.help;

import com.cryptocarver.model.OperationRegistry;
import com.cryptocarver.model.batch.BatchOperationCatalog;
import com.cryptocarver.model.process.NodeCatalog;
import com.cryptocarver.service.LocalHelpService;
import java.io.InputStream;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class B05HelpCatalogTest {
    private static final Set<String> B051 = Set.of("op_gen_hash", "op_gen_manual", "op_gen_compressed_hex",
            "op_gen_file", "op_gen_random", "op_gen_check_digits", "op_gen_mod", "aux_uuid");
    private static final Set<String> B052A = Set.of("op_gen_batch");

    @Test void everyB05OperationHasOwnLocalizedDefinitionAndLocalReference() throws Exception {
        Properties english = bundle("messages.properties");
        Properties spanish = bundle("messages_es.properties");
        LocalHelpService refs = new LocalHelpService();
        for (OperationHelpDefinition definition : B05HelpCatalog.definitions()) {
            assertEquals(B051.contains(definition.operationId()) || B052A.contains(definition.operationId()),
                    definition.complete(), definition.operationId());
            if (!definition.operationId().startsWith("aux_"))
                assertTrue(OperationRegistry.getInstance().getById(definition.operationId()).isPresent(), definition.operationId());
            assertKeys(definition.purposeKey(), english, spanish);
            assertTrue(refs.readReference(definition.referenceId(), Locale.ENGLISH).isPresent(), definition.referenceId());
            assertTrue(refs.readReference(definition.referenceId(), new Locale("es")).isPresent(), definition.referenceId());
            for (ActionHelp action : definition.actions()) {
                assertKeys(action.titleKey(), english, spanish);
                assertKeys(action.descriptionKey(), english, spanish);
                assertKeys(action.exampleKey(), english, spanish);
                action.fields().forEach(field -> {
                    assertKeys(field.labelKey(), english, spanish);
                    assertKeys(field.descriptionKey(), english, spanish);
                });
                action.options().forEach(option -> assertKeys(option.descriptionKey(), english, spanish));
            }
        }
    }

    @Test void b051ActionsHaveOperationSpecificBilingualExamplesAndOnlyRealFields() throws Exception {
        Properties en = bundle("messages.properties");
        Properties es = bundle("messages_es.properties");
        for (String id : B051) {
            OperationHelpDefinition definition = B05HelpCatalog.find(id);
            Set<String> spanishExamples = new java.util.HashSet<>();
            for (ActionHelp action : definition.actions()) {
                String spanish = es.getProperty(action.exampleKey());
                String english = en.getProperty(action.exampleKey());
                assertNotNull(spanish);
                assertNotNull(english);
                assertFalse(spanish.contains("El ejemplo es ilustrativo"), action.id());
                assertFalse(spanish.startsWith("Aplica la acción"), action.id());
                assertTrue(spanishExamples.add(spanish), id + "/" + action.id() + " repeats an example");
                assertFalse(english.contains("El ejemplo es ilustrativo"), action.id());
            }
        }
        assertEquals(Set.of("input", "output"), B05HelpCatalog.find("op_gen_compressed_hex").actions().stream()
                .filter(action -> action.id().equals("expand")).findFirst().orElseThrow().fields().stream()
                .map(FieldHelp::id).collect(Collectors.toSet()));
        Set<String> manualFields = B05HelpCatalog.find("op_gen_manual").actions().stream()
                .flatMap(action -> action.fields().stream()).map(FieldHelp::id).collect(Collectors.toSet());
        assertFalse(manualFields.contains("bcd"));
        assertFalse(manualFields.contains("trace"));
        Set<String> hashFields = B05HelpCatalog.find("op_gen_hash").actions().stream()
                .flatMap(action -> action.fields().stream()).map(FieldHelp::id).collect(Collectors.toSet());
        assertFalse(hashFields.contains("digest_size"));
        Set<String> fileFields = B05HelpCatalog.find("op_gen_file").actions().stream()
                .flatMap(action -> action.fields().stream()).map(FieldHelp::id).collect(Collectors.toSet());
        assertFalse(fileFields.contains("preview")); assertFalse(fileFields.contains("streaming"));
        Set<String> checkDigitFields = B05HelpCatalog.find("op_gen_check_digits").actions().stream()
                .flatMap(action -> action.fields().stream()).map(FieldHelp::id).collect(Collectors.toSet());
        assertFalse(checkDigitFields.contains("check_digit"));
        assertEquals(Set.of("identifier"), B05HelpCatalog.find("aux_uuid").actions().stream()
                .flatMap(action -> action.fields().stream()).map(FieldHelp::id).collect(Collectors.toSet()));
    }

    @Test void b052b1ParameterAndOptionContractsMatchLiveNodeSemantics() throws Exception {
        Properties en = bundle("messages.properties");
        Properties es = bundle("messages_es.properties");
        var nodes = NodeCatalog.allDescriptors().stream()
                .filter(node -> B05HelpCatalog.isVerifiedB052B1Node(node.type())).toList();
        assertEquals(36, nodes.size());
        assertEquals(B05HelpCatalog.verifiedB052B1NodeTypes(),
                nodes.stream().map(com.cryptocarver.model.process.NodeDescriptor::type).collect(Collectors.toSet()));
        int parameterCount = 0, optionCount = 0;
        for (var node : nodes) for (var parameter : node.parameters()) {
            parameterCount++;
            String key = B05HelpCatalog.nodeParameterDescriptionKey(node, parameter);
            String english = en.getProperty(key), spanish = es.getProperty(key);
            assertNotNull(english, node.type() + "." + parameter.key());
            assertNotNull(spanish, node.type() + "." + parameter.key());
            assertFalse(english.startsWith("Sets the catalog choice"), key);
            assertFalse(spanish.startsWith("Define opción del catálogo"), key);
            assertFalse(english.startsWith("Sets the numeric value"), key);
            assertFalse(spanish.startsWith("Define valor numérico"), key);
            for (String code : parameter.options()) {
                optionCount++;
                String optionKey = B05HelpCatalog.nodeOptionDescriptionKey(node, parameter, code);
                String optionEn = en.getProperty(optionKey), optionEs = es.getProperty(optionKey);
                assertNotNull(optionEn, optionKey); assertNotNull(optionEs, optionKey);
                assertFalse(optionEn.contains("uses exactly this catalog value"), optionKey);
                assertFalse(optionEs.contains("utiliza exactamente este valor del catálogo"), optionKey);
                assertTrue(optionEn.toLowerCase(Locale.ROOT).contains(code.substring(0, Math.min(code.length(), 4)).toLowerCase(Locale.ROOT))
                                || parameter.key().equals("paddingType") || parameter.key().equals("operation")
                                || parameter.key().equals("algorithm") || parameter.key().equals("readMode")
                                || parameter.key().equals("writeMode"),
                        "Option explanation must identify its concrete code: " + optionKey);
                if (parameter.key().equals("paddingType")) {
                    Map<String, String[]> facts = Map.of(
                            "PKCS5", new String[]{"block at 8 bytes", "bloque de 8"},
                            "PKCS7", new String[]{"full block", "bloque completo"},
                            "ISO_9797_M1", new String[]{"no marker", "no escribe marca"},
                            "ISO_9797_M2", new String[]{"80", "80"},
                            "ISO_7816_4", new String[]{"same as ISO 9797-1 M2", "igual que ISO 9797-1 M2"},
                            "ZERO", new String[]{"trailing zeros", "ceros finales"});
                    assertTrue(optionEn.contains(facts.get(code)[0]), optionKey + " lacks implementation rule");
                    assertTrue(optionEs.contains(facts.get(code)[1]), optionKey + " lacks implementation rule");
                } else if (node.type().startsWith("EBCDIC_")) {
                    assertTrue(optionEn.contains(code.split(" — ")[0]) && optionEn.contains("table"), optionKey);
                    assertTrue(optionEs.contains(code.split(" — ")[0]) && optionEs.contains("tabla"), optionKey);
                } else if (parameter.key().toLowerCase(Locale.ROOT).contains("charset")) {
                    boolean ignored = Set.of("BASE64_DECODE", "BASE64URL_DECODE", "HEX_DECODE", "UTF8_ENCODE", "UTF8_DECODE")
                            .contains(node.type());
                    if (ignored) {
                        assertTrue(optionEn.contains("ignores this selector"), optionKey);
                        assertTrue(optionEs.contains("ignora el selector"), optionKey);
                    } else {
                        assertTrue(optionEn.contains(code) && optionEs.contains(code), optionKey);
                        if (code.equals("US-ASCII")) {
                            assertTrue(optionEn.contains("substitutes") && optionEs.contains("sustituye"), optionKey);
                        }
                    }
                } else if (node.type().equals("COMPRESS") || node.type().equals("DECOMPRESS")) {
                    String[] wrapperFacts = code.equals("gzip") ? new String[]{"gzip", "header", "cabecera"}
                            : code.equals("zlib") ? new String[]{"zlib", "wrapper", "envoltura"}
                            : new String[]{"deflate", "raw", "sin envoltura"};
                    assertTrue(optionEn.toLowerCase(Locale.ROOT).contains(wrapperFacts[0])
                            && optionEn.toLowerCase(Locale.ROOT).contains(wrapperFacts[1]), optionKey);
                    assertTrue(optionEs.toLowerCase(Locale.ROOT).contains(wrapperFacts[0])
                            && optionEs.toLowerCase(Locale.ROOT).contains(wrapperFacts[2]), optionKey);
                } else if (node.type().equals("FILE_INPUT") || node.type().equals("FILE_OUTPUT")) {
                    if (code.equals("BINARY")) {
                        assertTrue(optionEn.contains("bytes") && optionEs.contains("bytes"), optionKey);
                    } else if (code.equals("TEXT")) {
                        assertTrue(optionEn.contains("charset") && optionEs.contains("charset"), optionKey);
                    }
                }
            }
        }
        assertEquals(30, parameterCount);
        assertEquals(146, optionCount);

        String[][] parameterFacts = {
                {"console_input.value", "literal emitted as bytes", "Texto literal multilínea"},
                {"console_input.charset", "replaces unmappable", "sustituye caracteres no representables"},
                {"file_input.path", "default is empty", "predeterminada vacía"},
                {"file_input.readmode", "BINARY emits bytes", "BINARY emite bytes"},
                {"file_input.charset", "Hidden in BINARY", "Oculto en BINARY"},
                {"file_output.path", "missing parent directory", "carpeta padre"},
                {"file_output.writemode", "TEXT writes", "TEXT escribe"},
                {"file_output.charset", "writeMode=TEXT", "writeMode=TEXT"},
                {"base64_decode.charset", "does not read this field", "no consulta este campo"},
                {"base64url_decode.charset", "does not read this field", "no consulta este campo"},
                {"hex_decode.charset", "does not read this field", "no consulta este campo"},
                {"utf8_encode.charset", "incoming FlowValue", "FlowValue de entrada"},
                {"utf8_decode.charset", "always uses UTF-8", "usa siempre UTF-8"},
                {"slice.offset", "Zero-based byte offset", "Desplazamiento en bytes desde cero"},
                {"slice.length", "default -1 means", "-1, que significa"},
                {"pad.paddingtype", "PKCS5 fixes", "PKCS5 fija"},
                {"pad.blocksize", "1–256", "1–256"},
                {"unpad.paddingtype", "trailing count", "contador final"},
                {"ebcdic_encode.codepage", "Target EBCDIC page", "Página EBCDIC de destino"},
                {"ebcdic_decode.codepage", "Source EBCDIC page", "Página EBCDIC de origen"},
                {"compress.format", "raw DEFLATE", "flujo DEFLATE sin esa envoltura"},
                {"decompress.format", "truncated data", "datos truncados"},
                {"charset_convert.sourcecharset", "Malformed sequences", "Secuencias mal formadas"},
                {"charset_convert.targetcharset", "yields `3F`", "produce `3F`"},
                {"asn1_decode.outputformat", "does not change DER parsing", "No cambia el parseo DER"},
                {"check_digit_calc.algorithm", "strips non-digits", "elimina caracteres no numéricos"},
                {"check_digit_verify.algorithm", "Returns the text `true`/`false`", "Devuelve el texto `true`/`false`"},
                {"modular_arithmetic.operation", "GCD ignores", "GCD ignora"},
                {"modular_arithmetic.modulus", "integer >1", "entero >1"},
                {"uuid_generate.format", "random UUID v4", "UUID v4 aleatorio"}
        };
        for (String[] fact : parameterFacts) {
            assertTrue(en.getProperty("help.node.parameter." + fact[0]).contains(fact[1]), fact[0] + " EN");
            assertTrue(es.getProperty("help.node.parameter." + fact[0]).contains(fact[2]), fact[0] + " ES");
        }
        for (var node : nodes) {
            String key = B05HelpCatalog.nodeExampleKey(node);
            String english = en.getProperty(key), spanish = es.getProperty(key);
            assertNotNull(english, key); assertNotNull(spanish, key);
            assertFalse(english.contains("Caso de error"), key);
            assertFalse(spanish.contains("Negative case"), key);
            assertNull(en.getProperty(key.substring(0, key.length() - "example".length()) + "error"),
                    "The unrendered duplicate error key is removed; .example is the visible source: " + node.type());
            assertNull(es.getProperty(key.substring(0, key.length() - "example".length()) + "error"), node.type());
        }
        assertTrue(es.getProperty("help.action.op_gen_process_designer.node.charset_convert.example")
                .contains("devuelve `?` (byte `3F`) por sustitución"));
        assertTrue(en.getProperty("help.action.op_gen_process_designer.node.charset_convert.example")
                .contains("yields `?` (byte `3F`) by replacement"));
        assertTrue(es.getProperty("help.action.op_gen_process_designer.node.check_digit_verify.example").contains("devuelve `false`"));
        assertTrue(en.getProperty("help.action.op_gen_process_designer.node.check_digit_verify.example").contains("returns `false`"));
    }

    @Test void b051HelpOptionsComeFromCurrentConsumerChoices() {
        assertOptionCodes("op_gen_random", "select_format", Set.copyOf(
                com.cryptocarver.model.OperationFormatRegistry.getInstance().getProfile("Random Number Generator").allowedOutputFormats()));
        assertOptionCodes("op_gen_file", "convert", Set.copyOf(com.cryptocarver.model.GenericOperationOptions.FILE_FORMATS));
        assertOptionCodes("op_gen_mod", "select_operation", Set.copyOf(com.cryptocarver.model.GenericOperationOptions.MODULAR_OPERATIONS));
        assertOptionCodes("op_gen_manual", "convert", Set.copyOf(
                com.cryptocarver.model.OperationFormatRegistry.getInstance().getProfile("Manual Conversion").allowedInputFormats()));
        assertOptionCodes("op_gen_manual", "endian", Set.copyOf(com.cryptocarver.model.GenericOperationOptions.ENDIAN_WORD_SIZES));
        assertOptionCodes("op_gen_manual", "compress", Set.copyOf(com.cryptocarver.crypto.CompressionCodec.supportedFormats()));
    }

    @Test void checkDigitChoicesExplainTheirActualRulesInBothLanguages() throws Exception {
        Properties en = bundle("messages.properties");
        Properties es = bundle("messages_es.properties");
        var options = B05HelpCatalog.find("op_gen_check_digits").actions().stream()
                .flatMap(action -> action.options().stream()).collect(Collectors.toMap(OptionHelp::code, OptionHelp::descriptionKey, (a, b) -> a));
        assertEquals(Set.copyOf(com.cryptocarver.crypto.CheckDigitCalculator.SUPPORTED_ALGORITHMS), options.keySet());
        String luhnEn = en.getProperty(options.get("Luhn (Mod 10)"));
        String luhnEs = es.getProperty(options.get("Luhn (Mod 10)"));
        String amexEn = en.getProperty(options.get("AMEX SE (Luhn/Mod 10)"));
        String amexEs = es.getProperty(options.get("AMEX SE (Luhn/Mod 10)"));
        String verhoeffEn = en.getProperty(options.get("Verhoeff"));
        String verhoeffEs = es.getProperty(options.get("Verhoeff"));
        String dammEn = en.getProperty(options.get("Damm"));
        String dammEs = es.getProperty(options.get("Damm"));
        assertTrue(luhnEn.contains("doubles alternating base digits")); assertTrue(luhnEs.contains("duplica cifras alternas de la base"));
        assertTrue(amexEn.contains("same Luhn calculation")); assertTrue(amexEs.contains("mismo cálculo Luhn"));
        assertTrue(verhoeffEn.contains("permutation") && verhoeffEn.contains("most transpositions"));
        assertTrue(verhoeffEs.contains("permutación") && verhoeffEs.contains("mayoría de transposiciones"));
        assertTrue(dammEn.contains("quasigroup") && dammEn.contains("adjacent transposition"));
        assertTrue(dammEs.contains("cuasigrupo") && dammEs.contains("transposición adyacente"));
        for (String description : List.of(luhnEn, luhnEs, amexEn, amexEs, verhoeffEn, verhoeffEs, dammEn, dammEs))
            assertFalse(description.contains("aplica su regla decimal de control"));
        assertTrue(en.getProperty("help.field.op_gen_check_digits.input.description").contains("at least one base digit"));
        assertTrue(es.getProperty("help.field.op_gen_check_digits.input.description").contains("cifra de base"));
    }

    @Test void nonNavigationUuidHelpIdResolvesToItsRegisteredDefinition() {
        OperationHelpDefinition definition = OperationHelpRegistry.getInstance().resolve("aux_uuid");
        assertEquals("aux_uuid", definition.operationId());
        assertTrue(definition.complete());
        assertTrue(definition.actions().stream().anyMatch(action -> action.id().equals("generate")));
    }

    private static void assertOptionCodes(String operation, String action, Set<String> expected) {
        Set<String> codes = B05HelpCatalog.find(operation).actions().stream().filter(item -> item.id().equals(action))
                .flatMap(item -> item.options().stream()).map(OptionHelp::code).collect(Collectors.toSet());
        assertTrue(codes.containsAll(expected), operation + "/" + action + " missing live options " + expected);
    }

    @Test void dynamicBatchOptionsMatchEverySelectableCatalogEntry() {
        OperationHelpDefinition definition = B05HelpCatalog.find("op_gen_batch");
        Set<String> documented = definition.actions().stream().filter(action -> action.id().equals("select_operation"))
                .flatMap(action -> action.options().stream()).map(OptionHelp::code).collect(Collectors.toSet());
        assertEquals(Set.copyOf(BatchOperationCatalog.getAvailableOperations()), documented);
    }

    @Test void everyBatchOperationHasItsOwnBilingualTransformationExplanation() throws Exception {
        Properties en = bundle("messages.properties");
        Properties es = bundle("messages_es.properties");
        ActionHelp select = B05HelpCatalog.find("op_gen_batch").actions().stream()
                .filter(action -> action.id().equals("select_operation")).findFirst().orElseThrow();
        assertEquals(Set.copyOf(BatchOperationCatalog.getAvailableOperations()),
                select.options().stream().map(OptionHelp::code).collect(Collectors.toSet()));
        for (OptionHelp option : select.options()) {
            String english = en.getProperty(option.descriptionKey());
            String spanish = es.getProperty(option.descriptionKey());
            assertNotNull(english, option.code());
            assertNotNull(spanish, option.code());
            assertTrue(english.length() > 55, option.code());
            assertTrue(spanish.length() > 55, option.code());
            assertFalse(english.contains("follows this catalog entry"), option.code());
            assertFalse(spanish.contains("sigue esta entrada del catálogo"), option.code());
        }
    }

    @Test void batchActionsExplainTheirRealFieldsFormatsAndExamples() throws Exception {
        Properties en = bundle("messages.properties");
        Properties es = bundle("messages_es.properties");
        OperationHelpDefinition definition = B05HelpCatalog.find("op_gen_batch");
        Set<String> englishExamples = new java.util.HashSet<>();
        Set<String> spanishExamples = new java.util.HashSet<>();
        for (ActionHelp action : definition.actions()) {
            assertTrue(englishExamples.add(en.getProperty(action.exampleKey())), action.id());
            assertTrue(spanishExamples.add(es.getProperty(action.exampleKey())), action.id());
            action.fields().forEach(field -> assertKeys(field.descriptionKey(), en, es));
        }
        assertEquals(Set.of("CSV", "JSON Lines (.jsonl)"), Set.copyOf(
                definition.actions().stream().filter(action -> action.id().equals("load_input"))
                        .flatMap(action -> action.options().stream()).map(OptionHelp::code).toList()));
        assertEquals(Set.of("CSV", "JSON Lines (.jsonl)"), Set.copyOf(
                definition.actions().stream().filter(action -> action.id().equals("export"))
                        .flatMap(action -> action.options().stream()).map(OptionHelp::code).toList()));
        Set<String> runFields = definition.actions().stream().filter(action -> action.id().equals("run"))
                .flatMap(action -> action.fields().stream()).map(FieldHelp::id).collect(Collectors.toSet());
        assertTrue(runFields.containsAll(Set.of("source_column", "destination_column", "stop_on_error", "record_encoding")));
        assertFalse(es.getProperty("help.field.op_gen_batch.key.description").contains("Secret material"));
        assertTrue(es.getProperty("help.field.op_gen_batch.key.description").contains("no disponibles"));
    }

    @Test void allFortySelectableBatchOperationsHaveBilingualContractsExamplesAndConsumerMatchedCases() throws Exception {
        Properties en = bundle("messages.properties");
        Properties es = bundle("messages_es.properties");
        OperationHelpDefinition definition = B05HelpCatalog.find("op_gen_batch");
        ActionHelp selector = definition.actions().stream().filter(action -> action.id().equals("select_operation"))
                .findFirst().orElseThrow();
        assertEquals(com.cryptocarver.model.batch.BatchOperationCatalog.getAvailableOperations().size(), selector.options().size());
        Set<String> englishContracts = new java.util.HashSet<>();
        Set<String> spanishContracts = new java.util.HashSet<>();
        Set<String> englishExamples = new java.util.HashSet<>();
        Set<String> spanishExamples = new java.util.HashSet<>();
        Set<String> englishErrors = new java.util.HashSet<>();
        Set<String> spanishErrors = new java.util.HashSet<>();
        for (OptionHelp option : selector.options()) {
            assertTrue(option.descriptionKey().startsWith("help.option.op_gen_batch.operation."), option.code());
            assertKeys(option.descriptionKey(), en, es);
            assertKeys(option.exampleKey(), en, es);
            assertKeys(option.errorKey(), en, es);
            String english = en.getProperty(option.descriptionKey());
            String spanish = es.getProperty(option.descriptionKey());
            String englishExample = en.getProperty(option.exampleKey());
            String spanishExample = es.getProperty(option.exampleKey());
            String englishError = en.getProperty(option.errorKey());
            String spanishError = es.getProperty(option.errorKey());
            assertFalse(english.isBlank(), option.code());
            assertFalse(spanish.isBlank(), option.code());
            assertFalse(english.contains("follows this catalog entry"), option.code());
            assertFalse(spanish.contains("sigue esta entrada del catálogo"), option.code());
            assertFalse(englishExample.isBlank(), option.code());
            assertFalse(spanishExample.isBlank(), option.code());
            assertFalse(englishError.isBlank(), option.code());
            assertFalse(spanishError.isBlank(), option.code());
            assertTrue(englishContracts.add(english), "Repeated English operation contract: " + option.code());
            assertTrue(spanishContracts.add(spanish), "Repeated Spanish operation contract: " + option.code());
            assertTrue(englishExamples.add(englishExample), "Repeated English operation example: " + option.code());
            assertTrue(spanishExamples.add(spanishExample), "Repeated Spanish operation example: " + option.code());
            assertTrue(englishErrors.add(englishError), "Repeated English operation error case: " + option.code());
            assertTrue(spanishErrors.add(spanishError), "Repeated Spanish operation error case: " + option.code());
        }
        assertEquals(40, selector.options().size());
        assertFalse(B05HelpCatalog.find("op_gen_process_designer").complete(), "C010 is still draft in B05.2a");
    }

    @Test void batchHelpExampleFixturesMatchTheExistingSelectableConsumers() throws Exception {
        String[][] fixtures = {
            {"SHA-256 (UTF-8 → Hex)", "abc", "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"},
            {"SHA-384 (UTF-8 → Hex)", "abc", "cb00753f45a35e8bb5a03d699ac65007272c32ab0eded1631a8b605a43ff5bed8086072ba1e7cc2358baeca134c825a7"},
            {"SHA-512 (UTF-8 → Hex)", "abc", "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f"},
            {"UTF-8 → Hexadecimal", "Hola", "486f6c61"}, {"Hexadecimal → UTF-8", "486f6c61", "Hola"},
            {"UTF-8 → Base64", "Hi", "SGk="}, {"Base64 → UTF-8", "SGk=", "Hi"},
            {"Hexadecimal → Base64", "4869", "SGk="}, {"Base64 → Hexadecimal", "SGk=", "4869"},
            {"UTF-8 → Base64URL", "Hi", "SGk"}, {"Base64URL → UTF-8", "SGk", "Hi"},
            {"Luhn (Mod 10) → Calculate Check Digit", "7992739871", "3"},
            {"Luhn (Mod 10) → Validate Check Digit", "79927398713", "true"},
            {"Verhoeff → Calculate Check Digit", "572", "7"}, {"Verhoeff → Validate Check Digit", "5727", "true"},
            {"Damm → Calculate Check Digit", "572", "4"}, {"Damm → Validate Check Digit", "5724", "true"},
            {"SHA-1 (UTF-8 → Hex)", "abc", "a9993e364706816aba3e25717850c26c9cd0d89d"},
            {"SHA-224 (UTF-8 → Hex)", "abc", "23097d223405d8228642a477bda255b32aadbce4bda0b3f7e36c9da7"},
            {"SHA3-256 (UTF-8 → Hex)", "abc", "3a985da74fe225b2045c172d6bd390bd855f086e3e9d525b46bfe24511431532"},
            {"SHA3-512 (UTF-8 → Hex)", "abc", "b751850b1a57168a5693cd924b6b096e08f621827444f70d884f5d0240d2712e10e116e9192af3c91a7ec57647e3934057340b4cf408d5a56592f8274eec53f0"},
            {"MD5 (legacy; UTF-8 → Hex)", "abc", "900150983cd24fb0d6963f7d28e17f72"},
            {"CRC32 (UTF-8 → Hex)", "123456789", "cbf43926"}, {"CRC32C (UTF-8 → Hex)", "123456789", "e3069283"},
            {"UTF-8 → Base32", "foo", "MZXW6==="}, {"Base32 → UTF-8", "MZXW6===", "foo"},
            {"UTF-8 → Base58", "Hello World", "JxF12TrwUP45BMd"}, {"Base58 → UTF-8", "JxF12TrwUP45BMd", "Hello World"},
            {"UTF-8 → Base94", "foo", ")(h\""}, {"Base94 → UTF-8", ")(h\"", "foo"},
            {"Decimal → Packed BCD Hex", "12345", "012345"}, {"Packed BCD Hex → Decimal", "012345", "012345"},
            {"AMEX SE → Calculate Check Digit", "7992739871", "3"},
            {"PAN → Validate (Luhn, 13–19 digits)", "4111111111111111", "true"},
            {"Track 2 → Analyze JSON", "1234567890123456D2512101F", "\"serviceCode\": \"101\""},
            {"EMV TLV → JSON", "9F0206000000000100", "9F02"},
            {"APDU Status → Inspect", "9000", "SUCCESS"}, {"ASN.1 → Inspect", "3003020101", "INTEGER"},
            {"TLV → Inspect", "5A081234567890123456", "5A"},
            {"TR-31 → Header JSON", "B0016P0TE00E0000", "PIN Encryption Key"}
        };
        assertEquals(com.cryptocarver.model.batch.BatchOperationCatalog.getAvailableOperations().size(), fixtures.length);
        Set<String> fixtureOperations = new java.util.HashSet<>();
        for (String[] fixture : fixtures) {
            assertTrue(fixtureOperations.add(fixture[0]), "Duplicate help vector: " + fixture[0]);
            String result = com.cryptocarver.model.batch.BatchOperationCatalog.execute(fixture[0],
                    Map.of("input", fixture[1]), "input", "result").get("result");
            assertTrue(result.contains(fixture[2]), fixture[0] + " example result: " + result);
        }
        assertEquals(Set.copyOf(com.cryptocarver.model.batch.BatchOperationCatalog.getAvailableOperations()), fixtureOperations);
        for (String operation : com.cryptocarver.model.batch.BatchOperationCatalog.getAvailableOperations()) {
            assertThrows(IllegalArgumentException.class, () -> com.cryptocarver.model.batch.BatchOperationCatalog.execute(
                    operation, Map.of(), "input", "result"), operation + " missing-source error case");
        }
        Map<String, String> invalid = Map.ofEntries(
                Map.entry("Hexadecimal → UTF-8", "48Z0"), Map.entry("Base64 → UTF-8", "SGk!"),
                Map.entry("Base64 → Hexadecimal", "SGk!"), Map.entry("Base64URL → UTF-8", "SGk+"),
                Map.entry("Luhn (Mod 10) → Calculate Check Digit", "79927A"),
                Map.entry("Luhn (Mod 10) → Validate Check Digit", "79927A"),
                Map.entry("Verhoeff → Calculate Check Digit", "57x"), Map.entry("Verhoeff → Validate Check Digit", "57x"),
                Map.entry("Damm → Calculate Check Digit", "57x"), Map.entry("Damm → Validate Check Digit", "57x"),
                Map.entry("Base32 → UTF-8", "MZXW6=!"), Map.entry("Base58 → UTF-8", "0"),
                Map.entry("Base94 → UTF-8", " !"), Map.entry("Decimal → Packed BCD Hex", "12x"),
                Map.entry("Packed BCD Hex → Decimal", "0"), Map.entry("AMEX SE → Calculate Check Digit", "12x"),
                Map.entry("Track 2 → Analyze JSON", "not-track-data"), Map.entry("EMV TLV → JSON", "ZZ"),
                Map.entry("APDU Status → Inspect", "900"), Map.entry("ASN.1 → Inspect", "ZZ"),
                Map.entry("TLV → Inspect", "ZZ"), Map.entry("TR-31 → Header JSON", "Z0016P0TE00E0000"));
        invalid.forEach((operation, value) -> assertThrows(Exception.class,
                () -> com.cryptocarver.model.batch.BatchOperationCatalog.execute(operation,
                        Map.of("input", value), "input", "result"), operation + " malformed-input case"));
        assertEquals("false", com.cryptocarver.model.batch.BatchOperationCatalog.execute("PAN → Validate (Luhn, 13–19 digits)",
                Map.of("input", "4111111111111112"), "input", "result").get("result"));
    }

    @Test void everyLiveNodeHasSpecificActionAndParameterFieldsWithoutSensitiveValues() throws Exception {
        OperationHelpDefinition definition = B05HelpCatalog.find("op_gen_process_designer");
        Properties en = bundle("messages.properties");
        Properties es = bundle("messages_es.properties");
        Set<String> actions = definition.actions().stream().map(ActionHelp::id).collect(Collectors.toSet());
        Set<String> examplesEnglish = new java.util.HashSet<>();
        Set<String> examplesSpanish = new java.util.HashSet<>();
        for (var node : NodeCatalog.allDescriptors()) {
            ActionHelp action = definition.actions().stream()
                    .filter(candidate -> candidate.id().equals(B05HelpCatalog.nodeActionId(node.type())))
                    .findFirst().orElseThrow(() -> new AssertionError("Missing node help: " + node.type()));
            Set<String> fieldIds = action.fields().stream().map(FieldHelp::id).collect(Collectors.toSet());
            assertKeys(action.exampleKey(), en, es);
            assertNotEquals("help.action.op_gen_process_designer.node.example", action.exampleKey());
            assertTrue(examplesEnglish.add(en.getProperty(action.exampleKey())), "Repeated English node example: " + node.type());
            assertTrue(examplesSpanish.add(es.getProperty(action.exampleKey())), "Repeated Spanish node example: " + node.type());
            FieldHelp ports = action.fields().stream().filter(field -> field.id().equals("ports")).findFirst().orElseThrow();
            assertKeys(ports.descriptionKey(), en, es);
            Set<String> optionCodes = action.options().stream().map(OptionHelp::code).collect(Collectors.toSet());
            for (var parameter : node.parameters()) {
                assertTrue(fieldIds.contains(parameter.key()), node.type() + "." + parameter.key());
                FieldHelp field = action.fields().stream().filter(candidate -> candidate.id().equals(parameter.key()))
                        .findFirst().orElseThrow();
                assertKeys(field.descriptionKey(), en, es);
                if (parameter.visibleWhen() != null && !parameter.visibleWhen().isBlank()) {
                    assertTrue(field.ruleId().startsWith("help.node.condition."), node.type() + "." + parameter.key());
                    assertFalse(field.ruleId().contains("="), "Raw condition leaked into help: " + field.ruleId());
                    assertKeys(field.ruleId(), en, es);
                }
                assertEquals(Set.copyOf(parameter.options()), action.options().stream()
                        .filter(option -> option.fieldId().equals(parameter.key())).map(OptionHelp::code).collect(Collectors.toSet()),
                        node.type() + "." + parameter.key() + " options");
                if (parameter.sensitive()) {
                    assertTrue(en.getProperty(field.descriptionKey()).contains("never reads or displays its value"),
                            "Sensitive help must explicitly redact the value");
                    assertTrue(es.getProperty(field.descriptionKey()).contains("nunca lee ni muestra su valor"),
                            "Sensitive help must explicitly redact the value in Spanish");
                }
            }
            for (OptionHelp option : action.options()) {
                assertKeys(option.descriptionKey(), en, es);
                assertFalse(option.descriptionKey().equals("help.node.option.catalog"));
            }
            assertTrue(actions.contains(B05HelpCatalog.nodeActionId(node.type())));
        }
    }

    @Test void b052b1NodesHaveReviewedSpecificExamplesPortsAndParameterSemantics() throws Exception {
        Set<String> expected = Set.of(
                "CONSOLE_INPUT", "FILE_INPUT", "CONSOLE_OUTPUT", "FILE_OUTPUT",
                "BASE64", "BASE64_ENCODE", "BASE64_DECODE", "BASE64URL_ENCODE", "BASE64URL_DECODE",
                "HEX_ENCODE", "HEX_DECODE", "UTF8_ENCODE", "UTF8_DECODE", "BASE32_ENCODE", "BASE32_DECODE",
                "BASE58_ENCODE", "BASE58_DECODE", "BASE58CHECK_ENCODE", "BASE58CHECK_DECODE",
                "EBCDIC_ENCODE", "EBCDIC_DECODE", "COMPRESS", "DECOMPRESS", "CHARSET_CONVERT",
                "CONCAT", "SLICE", "PAD", "UNPAD", "XOR", "ASSERT_EQUALS",
                "ASN1_DECODE", "CHECK_DIGIT_CALC", "CHECK_DIGIT_VERIFY", "MODULAR_ARITHMETIC",
                "UUID_GENERATE", "BYTE_STATISTICS");
        assertEquals(expected, B05HelpCatalog.verifiedB052B1NodeTypes());
        Set<String> live = NodeCatalog.allDescriptors().stream().map(node -> node.type())
                .filter(expected::contains).collect(Collectors.toSet());
        assertEquals(expected, live, "The reviewed slice must match live NodeCatalog types exactly");
        OperationHelpDefinition definition = B05HelpCatalog.find("op_gen_process_designer");
        assertFalse(definition.complete(), "C010 remains partial until all 194 nodes are reviewed");
        Properties en = bundle("messages.properties");
        Properties es = bundle("messages_es.properties");
        for (String type : expected) {
            String actionId = B05HelpCatalog.nodeActionId(type);
            ActionHelp action = definition.actions().stream().filter(candidate -> candidate.id().equals(actionId))
                    .findFirst().orElseThrow(() -> new AssertionError("Missing help action " + type));
            String exampleEn = en.getProperty(action.exampleKey());
            String exampleEs = es.getProperty(action.exampleKey());
            assertNotNull(exampleEn); assertNotNull(exampleEs);
            assertTrue(exampleEn.toLowerCase(Locale.ROOT).contains("case"), type + " English negative/edge case");
            assertTrue(exampleEs.toLowerCase(Locale.ROOT).contains("caso "), type + " Spanish negative/edge case");
            assertFalse(exampleEn.contains("connect a producer whose output"), type + " generic example");
            FieldHelp ports = action.fields().stream().filter(field -> field.id().equals("ports")).findFirst().orElseThrow();
            assertTrue(en.getProperty(ports.descriptionKey()).toLowerCase(Locale.ROOT).contains("output"), type + " concrete English ports");
            assertTrue(es.getProperty(ports.descriptionKey()).toLowerCase(Locale.ROOT).contains("salida"), type + " concrete Spanish ports");
            assertNotEquals(en.getProperty("help.node.ports.catalog"), en.getProperty(ports.descriptionKey()), type);
            for (FieldHelp field : action.fields()) {
                if (!field.id().equals("node_purpose") && !field.id().equals("ports")) {
                    assertNotNull(en.getProperty(field.descriptionKey()), type + "." + field.id() + " EN");
                    assertNotNull(es.getProperty(field.descriptionKey()), type + "." + field.id() + " ES");
                }
            }
            for (OptionHelp option : action.options()) {
                assertNotNull(en.getProperty(option.descriptionKey()), type + " option " + option.code() + " EN");
                assertNotNull(es.getProperty(option.descriptionKey()), type + " option " + option.code() + " ES");
            }
        }
    }

    @Test void processDesignerGeneralActionsHaveSpecificLocalizedHelpAndLiveControls() throws Exception {
        OperationHelpDefinition definition = B05HelpCatalog.find("op_gen_process_designer");
        Properties en = bundle("messages.properties");
        Properties es = bundle("messages_es.properties");
        Set<String> expected = Set.of("run", "dry_run", "cancel", "load", "save", "undo", "redo", "duplicate",
                "layout", "clear", "reset", "reverse_connection", "connect", "create_node", "configure_node",
                "edit_menu", "presets", "sha256_preset", "base64_preset", "aes_gcm_preset", "aes_cmac_preset",
                "zoom_in", "zoom_out", "zoom_reset", "zoom_fit", "snap_to_grid", "toggle_inspector",
                "focus_mode", "expand_results", "open_window", "save_node_settings", "search_palette");
        for (String id : expected) {
            ActionHelp action = definition.actions().stream().filter(candidate -> candidate.id().equals(id))
                    .findFirst().orElseThrow(() -> new AssertionError("Missing Process Designer action " + id));
            assertNotNull(en.getProperty(action.descriptionKey()), id + " EN description");
            assertNotNull(es.getProperty(action.descriptionKey()), id + " ES description");
            assertTrue(en.getProperty(action.descriptionKey()).length() > 25, id + " must explain its effect");
            assertTrue(es.getProperty(action.exampleKey()).length() > 45, id + " must include a concrete ES example");
            assertFalse(en.getProperty(action.descriptionKey()).contains("Aplica la acción"), id + " generic description");
        }
        for (var expectedMenu : Map.of("edit_menu", Set.of("undo", "redo", "duplicate", "layout", "clear", "reset"),
                "presets", Set.of("sha256_preset", "base64_preset", "aes_gcm_preset", "aes_cmac_preset")).entrySet()) {
            ActionHelp menu = definition.actions().stream().filter(action -> action.id().equals(expectedMenu.getKey()))
                    .findFirst().orElseThrow();
            assertEquals(expectedMenu.getValue(), menu.options().stream().map(OptionHelp::code).collect(Collectors.toSet()));
            for (OptionHelp option : menu.options()) assertKeys(option.descriptionKey(), en, es);
        }
    }

    private static void assertKeys(String key, Properties en, Properties es) {
        assertNotNull(en.getProperty(key), "Missing own English B05 key " + key);
        assertNotNull(es.getProperty(key), "Missing own Spanish B05 key " + key);
    }
    private static Properties bundle(String name) throws Exception {
        Properties properties = new Properties();
        try (InputStream input = B05HelpCatalogTest.class.getResourceAsStream("/i18n/" + name)) {
            assertNotNull(input, name);
            properties.load(new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8));
        }
        return properties;
    }
}
