package com.cryptocarver.model.help;

import com.cryptocarver.model.OperationRegistry;
import com.cryptocarver.model.OperationFormatRegistry;
import com.cryptocarver.model.help.SymmetricCapabilityPolicy;
import com.cryptocarver.crypto.PinBlockFormat;
import org.junit.jupiter.api.Test;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Properties;
import java.io.InputStream;
import static org.junit.jupiter.api.Assertions.*;

class OperationHelpRegistryTest {
    @Test void resolvesEveryCatalogOperationAndMarksUnwrittenEntriesIncomplete() {
        OperationHelpRegistry help = OperationHelpRegistry.getInstance();
        var operations = OperationRegistry.getInstance().getAll();
        assertEquals(96, operations.size());
        for (var operation : operations) {
            OperationHelpDefinition definition = help.resolve(operation.getId());
            assertEquals(operation.getId(), definition.operationId());
            assertFalse(definition.purposeKey().isBlank());
        }
        assertFalse(help.resolve("op_sym_ciphers").actions().isEmpty());
        assertTrue(help.resolve("op_gen_hash").complete());
    }

    @Test void b01SymmetricCatalogueAndTranslationsCoverEveryLiveOption() {
        var definition = OperationHelpRegistry.getInstance().resolve("op_sym_ciphers");
        var actionIds = definition.actions().stream().map(ActionHelp::id).toList();
        assertEquals(java.util.List.of("encrypt", "decrypt", "generate_iv", "select_key_source", "paste_key",
                "load_key", "inspect_key", "save_key_lab", "use_key_shelf", "copy_result", "shelf_result",
                "expand_result", "save_result", "reuse_result", "manage_templates", "apply_template",
                "save_template", "export_template", "import_template", "delete_template", "reset_defaults"), actionIds);
        var options = definition.actions().get(0).options();
        var codes = options.stream().map(OptionHelp::code).toList();
        assertTrue(codes.containsAll(SymmetricCapabilityPolicy.algorithms()));
        assertTrue(codes.containsAll(SymmetricCapabilityPolicy.modes()));
        assertTrue(codes.containsAll(SymmetricCapabilityPolicy.paddings()));
        assertTrue(codes.containsAll(SymmetricCapabilityPolicy.KEY_SOURCES));
        for (Locale locale : java.util.List.of(Locale.ENGLISH, Locale.forLanguageTag("es"))) {
            ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages", locale);
            assertTrue(bundle.containsKey("help.pin.iso4.resultFields"));
            assertTrue(bundle.containsKey("help.pin.decodeKey.absent"));
            assertTrue(bundle.containsKey(definition.purposeKey()));
            for (var action : definition.actions()) {
                assertTrue(bundle.containsKey(action.titleKey()), action.titleKey());
                assertTrue(bundle.containsKey(action.descriptionKey()), action.descriptionKey());
                assertTrue(bundle.containsKey(action.exampleKey()), action.exampleKey());
                for (var field : action.fields()) {
                    assertTrue(bundle.containsKey(field.labelKey()), field.labelKey());
                    assertTrue(bundle.containsKey(field.descriptionKey()), field.descriptionKey());
                    if (!field.ruleId().isBlank()) assertTrue(bundle.containsKey("help.rule." + field.ruleId()), field.ruleId());
                }
            }
            for (var option : options) assertTrue(bundle.containsKey(option.descriptionKey()), option.descriptionKey());
        }
    }

    @Test void b01GlobalFormatHelpCoversEveryRegisteredProfileAndAllowedCode() {
        var registry = OperationFormatRegistry.getInstance();
        var definition = OperationHelpRegistry.getInstance().resolve("aux_global_formats");
        assertEquals(java.util.List.of("input", "output", "contract"),
                definition.actions().stream().map(ActionHelp::id).toList());
        for (var profile : registry.profiles()) {
            String slug = OperationHelpRegistry.slug(profile.operationPath());
            for (Locale locale : java.util.List.of(Locale.ENGLISH, Locale.forLanguageTag("es"))) {
                ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages", locale);
                assertTrue(bundle.containsKey("help.format.contract." + slug), profile.operationPath());
                assertTrue(bundle.containsKey(definition.purposeKey()));
                for (var action : definition.actions()) {
                    assertTrue(bundle.containsKey(action.titleKey()), action.titleKey());
                    assertTrue(bundle.containsKey(action.descriptionKey()), action.descriptionKey());
                    assertTrue(bundle.containsKey(action.exampleKey()), action.exampleKey());
                }
                for (String code : profile.allowedInputFormats()) {
                    String key = "help.format.option." + OperationHelpRegistry.slug(code) + ".input";
                    assertTrue(bundle.containsKey(key), profile.operationPath() + " / " + code);
                }
                for (String code : profile.allowedOutputFormats()) {
                    String key = "help.format.option." + OperationHelpRegistry.slug(code) + ".output";
                    assertTrue(bundle.containsKey(key), profile.operationPath() + " / " + code);
                }
            }
        }
    }

    @Test void b01RouteSpecificFormatOverridesExistInBothLocalesForObservedConsumerDifferences() {
        var keys = java.util.List.of(
                "help.format.profile.manual_conversion.option.text_utf_8.input",
                "help.format.profile.manual_conversion.option.text_utf_8.output",
                "help.format.profile.manual_conversion.option.hexadecimal.input",
                "help.format.profile.manual_conversion.option.binary.input",
                "help.format.profile.manual_conversion.option.base64.input",
                "help.format.profile.manual_conversion.option.base64url.input",
                "help.format.profile.manual_conversion.option.decimal.input",
                "help.format.profile.symmetric_ciphers.option.text_utf_8.input",
                "help.format.profile.symmetric_ciphers.option.hexadecimal.input",
                "help.format.profile.symmetric_ciphers.option.base64.input",
                "help.format.profile.symmetric_ciphers.option.binary.input",
                "help.format.profile.symmetric_ciphers.option.text_utf_8.output",
                "help.format.profile.asymmetric_ciphers.option.text_utf_8.input",
                "help.format.profile.asymmetric_ciphers.option.hexadecimal.input",
                "help.format.profile.asymmetric_ciphers.option.base64.input",
                "help.format.profile.asymmetric_ciphers.option.binary.input",
                "help.format.profile.asymmetric_ciphers.option.text_utf_8.output",
                "help.format.profile.digital_signatures.option.text_utf_8.input",
                "help.format.profile.digital_signatures.option.hexadecimal.input",
                "help.format.profile.digital_signatures.option.base64.input",
                "help.format.profile.digital_signatures.option.text_utf_8.output",
                "help.format.profile.message_authentication_codes.option.text_utf_8.input",
                "help.format.profile.message_authentication_codes.option.hexadecimal.input",
                "help.format.profile.message_authentication_codes.option.base64.input",
                "help.format.profile.message_authentication_codes.option.text_utf_8.output",
                "help.format.profile.random_number_generator.option.decimal.output");
        for (Locale locale : java.util.List.of(Locale.ENGLISH, Locale.forLanguageTag("es"))) {
            ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages", locale);
            for (String key : keys) assertTrue(bundle.containsKey(key), locale + " / " + key);
        }
    }

    @Test void b03PinBlockCatalogueCoversEveryLiveFormatPaddingAndLocalizedField() {
        var registry = OperationHelpRegistry.getInstance();
        var clear = registry.resolve("op_pay_clear_pin");
        var encrypted = registry.resolve("op_pay_enc_pin");
        assertTrue(clear.complete());
        assertTrue(encrypted.complete());
        assertEquals("pay.pin.clear", clear.referenceId());
        assertEquals("pay.pin.encrypted", encrypted.referenceId());
        assertEquals(java.util.List.of("encode", "decode", "select_format"), clear.actions().stream().map(ActionHelp::id).toList());
        assertEquals(java.util.List.of("protect", "decode", "select_format"), encrypted.actions().stream().map(ActionHelp::id).toList());
        var clearOptions = clear.actions().get(0).options();
        assertTrue(clearOptions.stream().filter(option -> option.fieldId().equals("format_encode"))
                .map(OptionHelp::code).toList().containsAll(PinBlockFormat.displayNames()));
        assertTrue(clearOptions.stream().filter(option -> option.fieldId().equals("padding"))
                .map(OptionHelp::code).toList().containsAll(java.util.Arrays.stream(PinBlockFormat.values())
                        .flatMap(format -> format.paddingOptions().stream()).distinct().toList()));
        for (Locale locale : java.util.List.of(Locale.ENGLISH, Locale.forLanguageTag("es"))) {
            ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages", locale);
            var localHelp = new com.cryptocarver.service.LocalHelpService();
            assertTrue(localHelp.readReference(clear.referenceId(), locale).orElseThrow().contains("C080"));
            assertTrue(localHelp.readReference(encrypted.referenceId(), locale).orElseThrow().contains("C081"));
            for (var definition : java.util.List.of(clear, encrypted)) {
                assertTrue(bundle.containsKey(definition.purposeKey()));
                for (var action : definition.actions()) {
                    assertTrue(bundle.containsKey(action.titleKey()), action.titleKey());
                    assertTrue(bundle.containsKey(action.descriptionKey()), action.descriptionKey());
                    assertTrue(bundle.containsKey(action.exampleKey()), action.exampleKey());
                    for (var field : action.fields()) {
                        assertTrue(bundle.containsKey(field.labelKey()), field.labelKey());
                        assertTrue(bundle.containsKey(field.descriptionKey()), field.descriptionKey());
                    }
                    for (var option : action.options()) assertTrue(bundle.containsKey(option.descriptionKey()), option.descriptionKey());
                }
            }
            for (PinBlockFormat format : PinBlockFormat.values()) {
                String slug = OperationHelpRegistry.slug(format.displayName());
                assertTrue(bundle.containsKey("help.pin.format." + slug + ".pan"), slug);
                assertTrue(bundle.containsKey("help.pin.format." + slug + ".block"), slug);
                assertTrue(bundle.containsKey("help.pin.format." + slug + ".fixedPadding"), slug);
            }
            String result = bundle.getString("help.field.op_pay_enc_pin.result.description").toLowerCase(Locale.ROOT);
            String encode = bundle.getString("help.field.op_pay_enc_pin.encode_action.description").toLowerCase(Locale.ROOT);
            String decode = bundle.getString("help.field.op_pay_enc_pin.decode_action.description").toLowerCase(Locale.ROOT);
            assertTrue(result.contains("clear") || result.contains("claro"), locale + " result clear route");
            assertTrue(result.contains("key") || result.contains("clave"), locale + " result key behavior");
            assertTrue(result.contains("valid") || result.contains("válid"), locale + " result does not infer validity");
            assertTrue(encode.contains("aes") && (encode.contains("des") || encode.contains("tdes")), locale + " encode algorithms");
            assertTrue(encode.contains("key") || encode.contains("clave"), locale + " encode key behavior");
            assertTrue(decode.contains("aes") && (decode.contains("des") || decode.contains("tdes")), locale + " decode algorithms");
            assertTrue(decode.contains("pin") && (decode.contains("recover") || decode.contains("recuper")), locale + " decode recovers PIN");
            assertTrue(bundle.containsKey("help.pin.encrypted.padding.default"));
            assertTrue(bundle.containsKey("help.pin.encrypted.padding.notConfigurable"));
        }
    }

    @Test void b04CipherModuleCataloguesHaveLocalizedActionsFieldsAndPackagedReferences() {
        var registry = OperationHelpRegistry.getInstance();
        var localHelp = new com.cryptocarver.service.LocalHelpService();
        for (String id : java.util.List.of("op_fpe", "op_sym_file", "op_openpgp", "op_asym_ciphers",
                "aux_encrypted_file_analysis")) {
            var definition = registry.resolve(id);
            assertTrue(definition.complete(), id);
            assertFalse(definition.actions().isEmpty(), id);
            for (Locale locale : java.util.List.of(Locale.ENGLISH, Locale.forLanguageTag("es"))) {
                Properties bundle = loadOwnBundle(locale);
                assertTrue(bundle.containsKey(definition.purposeKey()), id + " purpose " + locale);
                for (var action : definition.actions()) {
                    assertTrue(bundle.containsKey(action.titleKey()), action.titleKey() + " " + locale);
                    assertTrue(bundle.containsKey(action.descriptionKey()), action.descriptionKey() + " " + locale);
                    assertTrue(bundle.containsKey(action.exampleKey()), action.exampleKey() + " " + locale);
                    for (var field : action.fields()) {
                        assertTrue(bundle.containsKey(field.labelKey()), field.labelKey() + " " + locale);
                        assertTrue(bundle.containsKey(field.descriptionKey()), field.descriptionKey() + " " + locale);
                    }
                    for (var option : action.options())
                        assertTrue(bundle.containsKey(option.descriptionKey()), option.descriptionKey() + " " + locale);
                }
                assertTrue(localHelp.readReference(definition.referenceId(), locale).isPresent(), id + " reference " + locale);
            }
        }
    }

    @Test void b04SpanishKeysAreOwnedBySpanishBundleAndSelectorsMatchLiveControls() {
        OperationHelpRegistry registry = OperationHelpRegistry.getInstance();
        Properties spanish = loadOwnBundle(Locale.forLanguageTag("es"));
        for (String id : java.util.List.of("op_fpe", "op_sym_file", "op_openpgp", "op_asym_ciphers",
                "aux_encrypted_file_analysis")) {
            var definition = registry.resolve(id);
            assertTrue(spanish.containsKey(definition.purposeKey()), id + " purpose must be Spanish-owned");
            for (var action : definition.actions()) {
                assertTrue(spanish.containsKey(action.titleKey()), action.titleKey());
                assertTrue(spanish.containsKey(action.descriptionKey()), action.descriptionKey());
                assertTrue(spanish.containsKey(action.exampleKey()), action.exampleKey());
                for (var field : action.fields()) {
                    assertTrue(spanish.containsKey(field.labelKey()), field.labelKey());
                    assertTrue(spanish.containsKey(field.descriptionKey()), field.descriptionKey());
                }
                for (var option : action.options()) assertTrue(spanish.containsKey(option.descriptionKey()), option.descriptionKey());
            }
        }
        var rsa = registry.resolve("op_asym_ciphers").actions().get(0).options().stream()
                .filter(option -> option.fieldId().equals("input_format") || option.fieldId().equals("output_format"))
                .map(OptionHelp::code).distinct().toList();
        assertEquals(java.util.List.of("Text (UTF-8)", "Hexadecimal", "Base64", "Binary"), rsa);
        assertFalse(rsa.contains("Base64URL"));
        var file = registry.resolve("op_sym_file").actions().get(0).options();
        var charsetCodes = registry.resolve("op_sym_file").actions().stream().flatMap(action -> action.options().stream())
                .filter(option -> option.fieldId().equals("charset")).map(OptionHelp::code).distinct().toList();
        var actualCharsets = new java.util.ArrayList<String>();
        actualCharsets.add("UTF-8"); actualCharsets.addAll(com.cryptocarver.crypto.EBCDICConverter.supportedCodePages().keySet());
        assertTrue(charsetCodes.containsAll(actualCharsets));
        var pgp = registry.resolve("op_openpgp");
        assertEquals(pgp.actions().size(), pgp.actions().stream().map(ActionHelp::id).distinct().count());
        assertTrue(pgp.actions().stream().anyMatch(a -> a.id().equals("save_secret_key")));
        assertTrue(pgp.actions().stream().anyMatch(a -> a.id().equals("clear_secret_material")));
        assertFalse(pgp.actions().stream().anyMatch(a -> a.id().equals("public_key") || a.id().equals("secret_key")));
        assertTrue(spanish.containsKey("help.analysis.route"));
        assertTrue(spanish.containsKey("help.fpe.ff1TweakLimit"));
        assertTrue(spanish.containsKey("help.fileCipher.nonceGeneration"));
        assertTrue(spanish.containsKey("help.rsa.inputFormats"));
        assertTrue(spanish.containsKey("help.rsa.outputFormats"));
        for (String prefix : java.util.List.of("help.fpe.", "help.fileCipher.", "help.rsa.",
                "help.analysis.", "help.openpgp.")) {
            var base = loadOwnBundle(Locale.ENGLISH);
            for (String key : base.stringPropertyNames()) {
                if (key.startsWith(prefix)) assertTrue(spanish.containsKey(key), "ES dynamic key missing: " + key);
            }
        }
    }

    private static Properties loadOwnBundle(Locale locale) {
        String resource = locale.getLanguage().equals("es") ? "/i18n/messages_es.properties" : "/i18n/messages.properties";
        Properties properties = new Properties();
        try (InputStream input = OperationHelpRegistryTest.class.getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            properties.load(input);
            return properties;
        } catch (java.io.IOException error) {
            throw new AssertionError(error);
        }
    }
}
