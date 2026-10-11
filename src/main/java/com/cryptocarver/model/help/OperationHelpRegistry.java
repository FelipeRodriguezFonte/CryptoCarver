package com.cryptocarver.model.help;

import com.cryptocarver.model.OperationDescriptor;
import com.cryptocarver.model.OperationRegistry;
import com.cryptocarver.crypto.JoseKeyMaterial;
import com.cryptocarver.crypto.JweComposer;
import com.cryptocarver.crypto.PinBlockFormat;
import com.cryptocarver.crypto.FormatPreservingEncryption;
import com.cryptocarver.model.help.SymmetricCapabilityPolicy;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** ID-based help catalogue. An absent entry is an explicit incomplete fallback. */
public final class OperationHelpRegistry {
    private static final List<FieldHelp> PREFLIGHT_FIELDS = List.of(
            new FieldHelp("status", "help.aux.preflight.field.status", "help.aux.preflight.field.status.description", ""),
            new FieldHelp("rule", "help.aux.preflight.field.rule", "help.aux.preflight.field.rule.description", ""),
            new FieldHelp("target_field", "help.aux.preflight.field.target", "help.aux.preflight.field.target.description", ""),
            new FieldHelp("remedy", "help.aux.preflight.field.remedy", "help.aux.preflight.field.remedy.description", ""),
            new FieldHelp("details", "help.aux.preflight.field.details", "help.aux.preflight.field.details.description", ""));
    private static final List<FieldHelp> FORMAT_FIELDS = List.of(
            new FieldHelp("input_format", "help.aux.global_formats.field.input.label",
                    "help.aux.global_formats.field.input.description", ""),
            new FieldHelp("output_format", "help.aux.global_formats.field.output.label",
                    "help.aux.global_formats.field.output.description", ""),
            new FieldHelp("contract", "help.aux.global_formats.field.contract.label",
                    "help.aux.global_formats.field.contract.description", ""),
            new FieldHelp("remembered_selection", "help.aux.global_formats.field.remembered.label",
                    "help.aux.global_formats.field.remembered.description", ""),
            new FieldHelp("disabled", "help.aux.global_formats.field.disabled.label",
                    "help.aux.global_formats.field.disabled.description", ""));
    private static final List<FieldHelp> SYMMETRIC_FIELDS = List.of(
            cipherField("algorithm"), cipherField("mode"), cipherField("padding"),
            cipherField("key_source"), cipherField("manual_key"), cipherField("key_lab_key"),
            cipherField("iv_nonce"), cipherField("aad"), cipherField("tag"), cipherField("template_selection"),
            cipherField("payload"), cipherField("output"));
    private static final List<OptionHelp> SYMMETRIC_OPTIONS = symmetricOptions();
    private static final Map<String, OperationHelpDefinition> AUXILIARY_DEFINITIONS = Map.of(
            "aux_preflight_error", new OperationHelpDefinition("aux_preflight_error", "help.aux.preflight.purpose",
                    List.of(
                            new ActionHelp("details", "help.aux.preflight.action.details.title", "help.aux.preflight.action.details.description",
                                    PREFLIGHT_FIELDS, List.of(), "help.aux.preflight.action.details.example", ""),
                            new ActionHelp("go_to_field", "help.aux.preflight.action.field.title", "help.aux.preflight.action.field.description",
                                    PREFLIGHT_FIELDS, List.of(), "help.aux.preflight.action.field.example", ""),
                            new ActionHelp("rule", "help.aux.preflight.action.rule.title", "help.aux.preflight.action.rule.description",
                                    PREFLIGHT_FIELDS, List.of(), "help.aux.preflight.action.rule.example", ""),
                            new ActionHelp("copy_diagnostic", "help.aux.preflight.action.copy.title", "help.aux.preflight.action.copy.description",
                                    PREFLIGHT_FIELDS, List.of(), "help.aux.preflight.action.copy.example", "")),
                    "", true),
            "aux_global_formats", new OperationHelpDefinition("aux_global_formats",
                    "help.aux.global_formats.purpose", List.of(
                    new ActionHelp("input", "help.aux.global_formats.action.input.title",
                            "help.aux.global_formats.action.input.description", FORMAT_FIELDS, List.of(),
                            "help.aux.global_formats.action.input.example", ""),
                    new ActionHelp("output", "help.aux.global_formats.action.output.title",
                            "help.aux.global_formats.action.output.description", FORMAT_FIELDS, List.of(),
                            "help.aux.global_formats.action.output.example", ""),
                    new ActionHelp("contract", "help.aux.global_formats.action.contract.title",
                            "help.aux.global_formats.action.contract.description", FORMAT_FIELDS, List.of(),
                            "help.aux.global_formats.action.contract.example", "")), "", true),
            "aux_encrypted_file_analysis", analysisDefinition());
    private static final OperationHelpRegistry INSTANCE = new OperationHelpRegistry();
    private final Map<String, OperationHelpDefinition> definitions = Map.of(
            "op_sym_ciphers", symmetricDefinition(),
            "op_jose_jwe", jweDefinition(),
            "op_pay_clear_pin", clearPinDefinition(),
            "op_pay_enc_pin", encryptedPinDefinition(),
            "op_fpe", fpeDefinition(),
            "op_sym_file", fileCipherDefinition(),
            "op_openpgp", openPgpDefinition(),
            "op_asym_ciphers", asymmetricCipherDefinition());

    private OperationHelpRegistry() { }
    public static OperationHelpRegistry getInstance() { return INSTANCE; }

    public Optional<OperationHelpDefinition> find(String operationId) {
        OperationHelpDefinition auxiliary = AUXILIARY_DEFINITIONS.get(operationId);
        if (auxiliary != null) return Optional.of(auxiliary);
        OperationHelpDefinition b05 = B05HelpCatalog.find(operationId);
        if (b05 != null) return Optional.of(b05);
        OperationHelpDefinition registered = definitions.get(operationId);
        return Optional.ofNullable(registered == null ? ModuleHelpCatalog.find(operationId) : registered);
    }

    public OperationHelpDefinition resolve(String operationId) {
        OperationHelpDefinition auxiliary = AUXILIARY_DEFINITIONS.get(operationId);
        if (auxiliary != null) return auxiliary;
        OperationHelpDefinition registered = find(operationId).orElse(null);
        if (registered != null) return registered;
        OperationDescriptor descriptor = OperationRegistry.getInstance().getById(operationId).orElse(null);
        if (descriptor == null) return new OperationHelpDefinition(operationId == null ? "unknown" : operationId,
                "help.fallback.unknown", List.of(), "", false);
        return find(descriptor.getId()).orElseGet(() -> new OperationHelpDefinition(descriptor.getId(),
                "help.fallback.purpose", List.of(), "", false));
    }

    public OperationHelpDefinition resolve(HelpContext context) {
        return context == null ? resolve("unknown") : resolve(context.operationId());
    }

    public List<OperationHelpDefinition> definitions() {
        java.util.ArrayList<OperationHelpDefinition> all = new java.util.ArrayList<>(definitions.values());
        all.addAll(AUXILIARY_DEFINITIONS.values());
        all.addAll(B05HelpCatalog.definitions());
        all.addAll(ModuleHelpCatalog.definitions());
        return List.copyOf(all);
    }

    private static OperationHelpDefinition symmetricDefinition() {
        List<ActionHelp> actions = List.of(
                new ActionHelp("encrypt", "help.action.op_sym_ciphers.encrypt.title",
                        "help.action.op_sym_ciphers.encrypt.description", fieldsFor("encrypt"), SYMMETRIC_OPTIONS,
                        "help.action.op_sym_ciphers.encrypt.example", "cipher.symmetric"),
                new ActionHelp("decrypt", "help.action.op_sym_ciphers.decrypt.title",
                        "help.action.op_sym_ciphers.decrypt.description", fieldsFor("decrypt"), SYMMETRIC_OPTIONS,
                        "help.action.op_sym_ciphers.decrypt.example", "cipher.symmetric"),
                simpleCipherAction("generate_iv", "generate_iv", List.of("algorithm", "mode", "iv_nonce")),
                simpleCipherAction("select_key_source", "select_key_source", List.of("key_source", "manual_key", "key_lab_key")),
                simpleCipherAction("paste_key", "paste_key", List.of("manual_key")),
                simpleCipherAction("load_key", "load_key", List.of("manual_key")),
                simpleCipherAction("inspect_key", "inspect_key", List.of("manual_key", "key_lab_key")),
                simpleCipherAction("save_key_lab", "save_key_lab", List.of("manual_key", "key_lab_key")),
                simpleCipherAction("use_key_shelf", "use_key_shelf", List.of("manual_key")),
                simpleCipherAction("copy_result", "copy_result", List.of("output")),
                simpleCipherAction("shelf_result", "shelf_result", List.of("output")),
                simpleCipherAction("expand_result", "expand_result", List.of("output")),
                simpleCipherAction("save_result", "save_result", List.of("output")),
                simpleCipherAction("reuse_result", "reuse_result", List.of("output", "payload")),
                simpleCipherAction("manage_templates", "manage_templates", List.of("template_selection")),
                simpleCipherAction("apply_template", "apply_template", List.of("template_selection", "algorithm", "mode", "padding")),
                simpleCipherAction("save_template", "save_template", List.of("template_selection", "algorithm", "mode", "padding")),
                simpleCipherAction("export_template", "export_template", List.of("template_selection")),
                simpleCipherAction("import_template", "import_template", List.of("template_selection")),
                simpleCipherAction("delete_template", "delete_template", List.of("template_selection")),
                simpleCipherAction("reset_defaults", "reset_defaults", List.of("template_selection", "algorithm", "mode", "padding")));
        return new OperationHelpDefinition("op_sym_ciphers", "help.operation.op_sym_ciphers.purpose",
                actions, "cipher.symmetric", true);
    }

    private static OperationHelpDefinition jweDefinition() {
        List<ActionHelp> actions = List.of(
                jweAction("encrypt", List.of("key_algorithm", "content_encryption", "compression", "secret_format",
                        "pbes2_iterations", "public_key", "payload", "serialization", "aad", "kid", "typ",
                        "cty", "apu", "apv", "custom_header", "output"), true),
                jweAction("decrypt", List.of("input", "private_key", "decrypt_key_format", "decoded_header",
                        "decoded_payload", "breakdown_header", "breakdown_encrypted_key", "breakdown_decrypted_cek",
                        "breakdown_content_iv", "breakdown_ciphertext", "breakdown_content_tag"), false,
                        decryptKeyFormatOptions()),
                jweAction("choose_serialization", List.of("serialization", "aad"), true),
                jweAction("select_recipients", List.of("serialization", "public_key", "kid", "apu", "apv"), true),
                jweAction("inspect_breakdown", List.of("decoded_header", "decoded_payload", "breakdown_header",
                        "breakdown_encrypted_key", "breakdown_decrypted_cek", "breakdown_content_iv",
                        "breakdown_ciphertext", "breakdown_content_tag"), false),
                jweAction("import_public_key", List.of("public_key"), false),
                jweAction("use_public_key_shelf", List.of("public_key"), false),
                jweAction("import_private_key", List.of("private_key"), false),
                jweAction("use_private_key_shelf", List.of("private_key"), false),
                jweAction("copy_result", List.of("output"), false),
                jweAction("expand_result", List.of("output"), false));
        return new OperationHelpDefinition("op_jose_jwe", "help.operation.op_jose_jwe.purpose", actions,
                "jose.jwe", true);
    }

    private static ActionHelp jweAction(String id, List<String> fieldIds, boolean withOptions) {
        return jweAction(id, fieldIds, withOptions, withOptions ? jweOptions() : List.of());
    }

    private static ActionHelp jweAction(String id, List<String> fieldIds, boolean withOptions,
            List<OptionHelp> options) {
        return new ActionHelp(id, "help.action.op_jose_jwe." + id + ".title",
                "help.action.op_jose_jwe." + id + ".description",
                fieldIds.stream().map(OperationHelpRegistry::jweField).toList(),
                options,
                "help.action.op_jose_jwe." + id + ".example", "jose.jwe");
    }

    private static OperationHelpDefinition fpeDefinition() {
        String op = "op_fpe";
        List<FieldHelp> fields = fields(op, List.of("operation", "algorithm", "key", "alphabet_preset",
                "alphabet", "tweak", "input", "output"));
        List<OptionHelp> options = new java.util.ArrayList<>();
        for (String code : List.of("ENCRYPT", "DECRYPT")) options.add(option(op, "operation", code));
        for (FormatPreservingEncryption.Algorithm code : FormatPreservingEncryption.Algorithm.values())
            options.add(option(op, "algorithm", code.name()));
        for (String code : List.of("Decimal (0-9)", "Alphanumeric", "ASCII printable", "Custom"))
            options.add(option(op, "alphabet_preset", code));
        return new OperationHelpDefinition(op, "help.operation." + op + ".purpose", List.of(
                action(op, "encrypt", fields, options, "fpe"), action(op, "decrypt", fields, options, "fpe"),
                action(op, "select_algorithm", fields, options, "fpe"),
                action(op, "select_alphabet", fields, options, "fpe")), "fpe", true);
    }

    private static OperationHelpDefinition fileCipherDefinition() {
        String op = "op_sym_file";
        List<FieldHelp> fields = fields(op, List.of("profile", "source", "destination", "tag_file", "key",
                "iv_nonce", "aad", "lines_mode", "line_encoding", "charset", "compact_cbc", "result"));
        List<OptionHelp> options = new java.util.ArrayList<>();
        for (String code : List.of("AES-256-GCM", "AES-256-CTR", "AES-256-CBC", "ChaCha20-Poly1305"))
            options.add(option(op, "profile", code));
        for (String code : List.of("Base64URL", "Hexadecimal")) options.add(option(op, "line_encoding", code));
        options.add(option(op, "charset", "UTF-8"));
        com.cryptocarver.crypto.EBCDICConverter.supportedCodePages().keySet()
                .forEach(code -> options.add(option(op, "charset", code)));
        return new OperationHelpDefinition(op, "help.operation." + op + ".purpose", List.of(
                action(op, "encrypt_file", fields, options, "file.cipher"), action(op, "decrypt_file", fields, options, "file.cipher"),
                action(op, "select_profile", fields, options, "file.cipher"),
                action(op, "select_line_options", fields, options, "file.cipher"),
                action(op, "encrypt_lines", fields, options, "file.cipher"), action(op, "decrypt_lines", fields, options, "file.cipher"),
                action(op, "choose_source", fields, options, "file.cipher"), action(op, "choose_destination", fields, options, "file.cipher"),
                action(op, "choose_tag", fields, options, "file.cipher"), action(op, "generate_nonce", fields, options, "file.cipher"),
                action(op, "export_recipe", fields, options, "file.cipher"), action(op, "import_recipe", fields, options, "file.cipher")), "file.cipher", true);
    }

    private static OperationHelpDefinition openPgpDefinition() {
        String op = "op_openpgp";
        List<FieldHelp> fields = fields(op, List.of("user_id", "passphrase", "public_key", "secret_key", "input",
                "signature", "output", "gnupg_status"));
        List<String> ids = List.of("generate_key_pair", "paste_public_key", "load_public_key", "save_public_key", "inspect_public_key",
                "use_public_key_shelf", "paste_secret_key", "load_secret_key", "save_secret_key", "inspect_secret_key",
                "use_secret_key_shelf", "clear_secret_material", "encrypt_text", "decrypt_text", "sign_detached", "sign_attached",
                "clear_sign", "verify_detached", "verify_attached", "verify_clear_signed", "inspect_detached_signature",
                "load_input", "load_signature", "save_signature", "save_output", "encrypt_file", "decrypt_file", "sign_file",
                "verify_file", "probe_gnupg", "verify_with_gnupg", "run_gnupg_interop", "clear_data");
        List<ActionHelp> actions = ids.stream().map(id -> {
            String prefix = "help.action." + op + "." + id;
            return new ActionHelp(id, prefix + ".title", prefix + ".description", fields, List.of(),
                    prefix + ".example", "openpgp");
        }).toList();
        return new OperationHelpDefinition(op, "help.operation." + op + ".purpose", actions, "openpgp", true);
    }

    private static OperationHelpDefinition asymmetricCipherDefinition() {
        String op = "op_asym_ciphers";
        List<FieldHelp> fields = fields(op, List.of("padding", "input_format", "output_format", "public_key", "private_key",
                "input", "max_plaintext", "output"));
        List<OptionHelp> options = new java.util.ArrayList<>();
        for (String code : List.of("RSA/ECB/PKCS1Padding", "RSA/ECB/OAEPWithSHA-1AndMGF1Padding",
                "RSA/ECB/OAEPWithSHA-256AndMGF1Padding", "RSA/ECB/NoPadding")) options.add(option(op, "padding", code));
        for (String code : List.of("Text (UTF-8)", "Hexadecimal", "Base64", "Binary")) {
            options.add(option(op, "input_format", code)); options.add(option(op, "output_format", code));
        }
        return new OperationHelpDefinition(op, "help.operation." + op + ".purpose", List.of(
                action(op, "encrypt", fields, options, "asymmetric.rsa"), action(op, "decrypt", fields, options, "asymmetric.rsa"),
                action(op, "select_padding", fields, options, "asymmetric.rsa"), action(op, "load_public_key", fields, options, "asymmetric.rsa"),
                action(op, "use_public_key_shelf", fields, options, "asymmetric.rsa"), action(op, "load_private_key", fields, options, "asymmetric.rsa"),
                action(op, "use_private_key_shelf", fields, options, "asymmetric.rsa")), "asymmetric.rsa", true);
    }

    private static OperationHelpDefinition analysisDefinition() {
        String op = "aux_encrypted_file_analysis";
        List<FieldHelp> fields = fields(op, List.of("file_representation", "algorithm", "mode", "padding", "key", "iv_nonce",
                "tag", "aad", "expected_result", "candidates", "report"));
        return new OperationHelpDefinition(op, "help.operation." + op + ".purpose", List.of(
                action(op, "analyze", fields, List.of(), "encrypted.file.analysis")), "encrypted.file.analysis", true);
    }

    private static List<FieldHelp> fields(String operation, List<String> ids) {
        return ids.stream().map(id -> new FieldHelp(id, "help.field." + operation + "." + id + ".label",
                "help.field." + operation + "." + id + ".description", "")).toList();
    }

    private static ActionHelp action(String operation, String id, List<FieldHelp> fields,
            List<OptionHelp> options, String reference) {
        return new ActionHelp(id, "help.action." + operation + "." + id + ".title",
                "help.action." + operation + "." + id + ".description", fields, options,
                "help.action." + operation + "." + id + ".example", reference);
    }

    private static OptionHelp option(String operation, String field, String code) {
        return new OptionHelp(code, "help.option." + operation + "." + field + "." + slug(code), "", field);
    }

    private static OperationHelpDefinition clearPinDefinition() {
        List<OptionHelp> options = new java.util.ArrayList<>();
        PinBlockFormat.displayNames().forEach(code -> {
            options.add(pinOption("format_encode", code));
            options.add(pinOption("format_decode", code));
        });
        java.util.Arrays.stream(PinBlockFormat.values()).flatMap(format -> format.paddingOptions().stream())
                .distinct().forEach(code -> options.add(pinOption("padding", code)));
        List<String> encodeFields = List.of("pin", "pan_encode", "format_encode", "padding", "encode_action", "clear_block", "result");
        List<String> decodeFields = List.of("clear_block", "pan_decode", "format_decode", "decode_action", "result");
        List<OptionHelp> encodeOptions = options.stream()
                .filter(option -> "format_encode".equals(option.fieldId()) || "padding".equals(option.fieldId())).toList();
        List<OptionHelp> decodeOptions = options.stream().filter(option -> "format_decode".equals(option.fieldId())).toList();
        return new OperationHelpDefinition("op_pay_clear_pin", "help.operation.op_pay_clear_pin.purpose", List.of(
                pinAction("op_pay_clear_pin", "encode", encodeFields, encodeOptions),
                pinAction("op_pay_clear_pin", "decode", decodeFields, decodeOptions),
                pinAction("op_pay_clear_pin", "select_format", List.of("format_encode", "format_decode", "padding"), options)),
                "pay.pin.clear", true);
    }

    private static OperationHelpDefinition encryptedPinDefinition() {
        List<OptionHelp> formats = PinBlockFormat.displayNames().stream().map(code -> pinOption("format", code)).toList();
        return new OperationHelpDefinition("op_pay_enc_pin", "help.operation.op_pay_enc_pin.purpose", List.of(
                pinAction("op_pay_enc_pin", "protect", List.of("format", "pin", "pan_encode", "encrypt_key", "encode_action", "encrypted_block", "result", "protected_state"), formats),
                pinAction("op_pay_enc_pin", "decode", List.of("format", "encrypted_block", "pan_decode", "decrypt_key", "decode_action", "result", "protected_state"), formats),
                pinAction("op_pay_enc_pin", "select_format", List.of("format"), formats)), "pay.pin.encrypted", true);
    }

    private static ActionHelp pinAction(String operation, String action, List<String> fields, List<OptionHelp> options) {
        String helpPrefix = "help.action." + operation + "." + action;
        return new ActionHelp(action, helpPrefix + ".title", helpPrefix + ".description",
                fields.stream().map(id -> pinField(operation, id)).toList(), options,
                helpPrefix + ".example", operation.equals("op_pay_clear_pin") ? "pay.pin.clear" : "pay.pin.encrypted");
    }

    private static FieldHelp pinField(String operation, String id) {
        return new FieldHelp(id, "help.field." + operation + "." + id + ".label",
                "help.field." + operation + "." + id + ".description", "");
    }

    private static OptionHelp pinOption(String field, String code) {
        String operation = field.equals("format") ? "op_pay_enc_pin" : "op_pay_clear_pin";
        return new OptionHelp(code, "help.option." + operation + "." + field + "." + slug(code), "", field);
    }

    private static List<OptionHelp> decryptKeyFormatOptions() {
        return java.util.Arrays.stream(JoseKeyMaterial.SecretEncoding.values())
                .map(encoding -> jweOption(encoding.label(), "decrypt_key_format")).toList();
    }

    private static FieldHelp jweField(String id) {
        return new FieldHelp(id, "help.field.op_jose_jwe." + id + ".label",
                "help.field.op_jose_jwe." + id + ".description", "");
    }

    private static List<OptionHelp> jweOptions() {
        java.util.ArrayList<OptionHelp> options = new java.util.ArrayList<>();
        JweComposer.KEY_ALGORITHMS.forEach(code -> options.add(jweOption(code, "key_algorithm")));
        JweComposer.CONTENT_ALGORITHMS.forEach(code -> options.add(jweOption(code, "content_encryption")));
        for (JweComposer.Serialization serialization : JweComposer.Serialization.values()) {
            options.add(jweOption(serialization.label(), "serialization"));
        }
        options.add(jweOption("Enabled", "compression"));
        options.add(jweOption("Disabled", "compression"));
        for (JoseKeyMaterial.SecretEncoding encoding : JoseKeyMaterial.SecretEncoding.values()) {
            options.add(jweOption(encoding.label(), "secret_format"));
            options.add(jweOption(encoding.label(), "decrypt_key_format"));
        }
        return List.copyOf(options);
    }

    private static OptionHelp jweOption(String code, String fieldId) {
        return new OptionHelp(code, "help.option.op_jose_jwe." + fieldId + "." + slug(code), "", fieldId);
    }

    private static ActionHelp simpleCipherAction(String id, String key, List<String> fields) {
        return new ActionHelp(id, "help.action.op_sym_ciphers." + key + ".title",
                "help.action.op_sym_ciphers." + key + ".description",
                fields.stream().map(OperationHelpRegistry::cipherField).toList(), List.of(),
                "help.action.op_sym_ciphers." + key + ".example", "cipher.symmetric");
    }

    private static List<FieldHelp> fieldsFor(String action) {
        List<String> ids = action.equals("encrypt")
                ? List.of("algorithm", "mode", "padding", "key_source", "manual_key", "key_lab_key",
                        "iv_nonce", "aad", "payload", "output", "tag")
                : List.of("algorithm", "mode", "padding", "key_source", "manual_key", "key_lab_key",
                        "iv_nonce", "aad", "tag", "payload", "output");
        return ids.stream().map(OperationHelpRegistry::cipherField).toList();
    }

    private static FieldHelp cipherField(String id) {
        String rule = switch (id) {
            case "mode" -> "symmetric.mode.block_selector";
            case "padding" -> "symmetric.padding.selectable";
            case "manual_key", "key_lab_key" -> "symmetric.key.length";
            case "iv_nonce" -> "symmetric.iv.required_or_nonce";
            case "aad" -> "symmetric.aad.gcm_used";
            case "tag" -> "symmetric.authentication.authenticated";
            case "template_selection" -> "symmetric.template.allowlist";
            default -> "";
        };
        return new FieldHelp(id, "help.field.op_sym_ciphers." + id + ".label",
                "help.field.op_sym_ciphers." + id + ".description", rule);
    }

    private static List<OptionHelp> symmetricOptions() {
        java.util.ArrayList<OptionHelp> options = new java.util.ArrayList<>();
        SymmetricCapabilityPolicy.algorithms().forEach(code -> options.add(option("algorithm", code)));
        SymmetricCapabilityPolicy.modes().forEach(code -> options.add(option("mode", code)));
        SymmetricCapabilityPolicy.paddings().forEach(code -> options.add(option("padding", code)));
        SymmetricCapabilityPolicy.KEY_SOURCES.forEach(code -> options.add(option("key_source", code)));
        return List.copyOf(options);
    }

    private static OptionHelp option(String fieldId, String code) {
        return new OptionHelp(code, "help.option.op_sym_ciphers." + fieldId + "." + slug(code), "", fieldId);
    }

    public static String slug(String value) {
        return value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }
}
