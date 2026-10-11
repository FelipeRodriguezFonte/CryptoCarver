package com.cryptocarver.model.help;

import com.cryptocarver.crypto.HashOperations;
import com.cryptocarver.model.batch.BatchOperationCatalog;
import com.cryptocarver.model.GenericOperationOptions;
import com.cryptocarver.model.OperationFormatProfile;
import com.cryptocarver.model.OperationFormatRegistry;
import com.cryptocarver.model.process.NodeCatalog;
import com.cryptocarver.model.process.NodeDescriptor;
import com.cryptocarver.model.process.NodeParameter;
import com.cryptocarver.crypto.EBCDICConverter;
import com.cryptocarver.crypto.CompressionCodec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** B05 help definitions. Dynamic choices are derived from their live application catalogs. */
public final class B05HelpCatalog {
    private static final Set<String> VERIFIED_B051 = Set.of(
            "op_gen_hash", "op_gen_manual", "op_gen_compressed_hex", "op_gen_file",
            "op_gen_random", "op_gen_check_digits", "op_gen_mod", "aux_uuid");
    private static final Set<String> VERIFIED_B052A = Set.of("op_gen_batch");
    private static final Set<String> B052B1_NODE_TYPES = Set.of(
            "CONSOLE_INPUT", "FILE_INPUT", "CONSOLE_OUTPUT", "FILE_OUTPUT",
            "BASE64", "BASE64_ENCODE", "BASE64_DECODE", "BASE64URL_ENCODE", "BASE64URL_DECODE",
            "HEX_ENCODE", "HEX_DECODE", "UTF8_ENCODE", "UTF8_DECODE", "BASE32_ENCODE", "BASE32_DECODE",
            "BASE58_ENCODE", "BASE58_DECODE", "BASE58CHECK_ENCODE", "BASE58CHECK_DECODE",
            "EBCDIC_ENCODE", "EBCDIC_DECODE", "COMPRESS", "DECOMPRESS", "CHARSET_CONVERT",
            "CONCAT", "SLICE", "PAD", "UNPAD", "XOR", "ASSERT_EQUALS",
            "ASN1_DECODE", "CHECK_DIGIT_CALC", "CHECK_DIGIT_VERIFY", "MODULAR_ARITHMETIC",
            "UUID_GENERATE", "BYTE_STATISTICS");
    private record Spec(String id, String reference, List<String> fields, List<String> actions) { }
    private static final List<Spec> SPECS = List.of(
            spec("op_gen_hash", "hashing", "algorithm,input,input_format,output,output_format,template", "calculate,select_algorithm,apply_template,manage_templates"),
            spec("op_gen_manual", "manual.conversion", "input,input_format,output,output_format,ebcdic_conversion,ebcdic_direction,code_page,word_size,compression,shift,template", "convert,base64url_encode,base64url_decode,base32_encode,base32_decode,endian,url_encode,url_decode,compress,decompress,bcd_encode,bcd_decode,comp3_encode,comp3_decode,shift_left,shift_right,extract_trace_hex,manage_templates"),
            spec("op_gen_compressed_hex", "compressed.hex", "input,output", "expand,compress,clear"),
            spec("op_gen_batch", "batch.runner", "input_format,source_column,destination_column,operation,algorithm,record_encoding,key,iv_nonce,aad,charset,stop_on_error,compact,input,dry_run,progress,status,results,export_format", "load_input,dry_run,run,cancel,reset,export,select_operation"),
            spec("op_gen_process_designer", "process.designer", "process_name,process_file,node_name,palette,canvas,node,ports,target_port,connection,selection,clear_canvas,parameters,run_mode,progress,status,status_summary,result,validation,zoom,snap_to_grid,inspector,focus_mode", "run,dry_run,cancel,load,save,undo,redo,duplicate,layout,delete,clear,reset,reverse_connection,connect,create_node,configure_node,edit_menu,presets,sha256_preset,base64_preset,aes_gcm_preset,aes_cmac_preset,zoom_in,zoom_out,zoom_reset,zoom_fit,snap_to_grid,toggle_inspector,focus_mode,open_results,expand_results,open_window,save_node_settings,search_palette"),
            spec("op_gen_file", "file.conversion", "source_path,destination_path,comparison_path,input_format,output_format,encoding,result", "choose_source,choose_destination,convert,compare,hash_file,preview"),
            spec("op_gen_random", "random.generator", "byte_count,format,output", "generate,select_format"),
            spec("op_gen_check_digits", "check.digits", "algorithm,input,result", "calculate,validate,select_algorithm"),
            spec("op_gen_mod", "modular.arithmetic", "operation,operand_a,operand_b,modulus,result", "calculate,select_operation"),
            spec("op_gen_clipboard", "clipboard.shelf", "search,filter,pin,note,entry,details,comparison", "search,filter,open_copy_window,clear_shelf,pin,compare,use,rename,delete,copy,open_expanded"),
            spec("op_keys_format_workbench", "key.certificate.workbench", "input,password,source_format,destination_format,detected_format,details,secondary_input,output", "load_file,load_shelf,parse,convert,validate,send_to_shelf,copy"),
            spec("op_gen_envelope_inspector", "crypto.envelope", "envelope,profile,private_key,report,recovered_key", "inspect,clear,reset,select_profile,unwrap"),
            spec("aux_byte_inspector", "byte.inspector", "source,representation,offset,length,charset,selection", "hex_view,charsets,statistics,controls,xor,compare,copy"),
            spec("aux_epoch", "epoch.converter", "epoch_value,unit,date_time,zone,result", "convert"),
            spec("aux_json", "json.formatter", "input,formatting,output,error_position", "format"),
            spec("aux_uuid", "uuid.generator", "identifier", "generate,copy"));

    private static final Set<String> GENERIC_IDS = SPECS.stream().map(Spec::id).collect(java.util.stream.Collectors.toUnmodifiableSet());
    private B05HelpCatalog() { }

    public static boolean supports(String id) { return GENERIC_IDS.contains(id); }
    public static List<OperationHelpDefinition> definitions() {
        return SPECS.stream().map(B05HelpCatalog::build).toList();
    }
    public static OperationHelpDefinition find(String id) {
        return SPECS.stream().filter(spec -> spec.id().equals(id)).findFirst().map(B05HelpCatalog::build).orElse(null);
    }

    private static Spec spec(String id, String reference, String fields, String actions) {
        return new Spec(id, reference, List.of(fields.split(",")), List.of(actions.split(",")));
    }
    private static OperationHelpDefinition build(Spec spec) {
        List<FieldHelp> commonFields = spec.fields().stream().map(field -> field(spec.id(), field)).toList();
        List<ActionHelp> actions = new ArrayList<>();
        for (String action : spec.actions()) {
            List<FieldHelp> fields = fieldsForAction(spec, action, commonFields);
            List<OptionHelp> options = List.of();
            if (spec.id().equals("op_gen_hash") && Set.of("calculate", "select_algorithm").contains(action)) {
                List<String> algorithms = new ArrayList<>(HashOperations.SUPPORTED_ALGORITHMS);
                algorithms.add("CRC32");
                for (HashOperations.Crc32Variant variant : HashOperations.Crc32Variant.values()) algorithms.add(variant.displayName());
                List<OptionHelp> hashOptions = new ArrayList<>(algorithms.stream().map(code -> new OptionHelp(code,
                        "help.option.op_gen_hash.algorithm." + slug(code), "", "algorithm")).toList());
                if (action.equals("calculate")) hashOptions.addAll(formatOptions("Hashing", true, true, "op_gen_hash"));
                options = List.copyOf(hashOptions);
            } else if (spec.id().equals("op_gen_manual")) {
                options = manualOptions(action);
            } else if (spec.id().equals("op_gen_file")) {
                options = fileOptions(action);
            } else if (spec.id().equals("op_gen_random") && Set.of("generate", "select_format").contains(action)) {
                options = OperationFormatRegistry.getInstance().getProfile("Random Number Generator")
                        .allowedOutputFormats().stream().map(code -> option(spec.id(), "format", code)).toList();
            } else if (spec.id().equals("op_gen_check_digits") && Set.of("calculate", "validate", "select_algorithm").contains(action)) {
                options = com.cryptocarver.crypto.CheckDigitCalculator.SUPPORTED_ALGORITHMS.stream().map(code -> new OptionHelp(code,
                        "help.option.op_gen_check_digits.algorithm." + slug(code), "", "algorithm")).toList();
            } else if (spec.id().equals("op_gen_mod") && Set.of("calculate", "select_operation").contains(action)) {
                options = GenericOperationOptions.MODULAR_OPERATIONS.stream().map(code -> new OptionHelp(code,
                        "help.option.op_gen_mod.operation." + slug(code), "", "operation")).toList();
            } else if (spec.id().equals("op_gen_batch") && action.equals("select_operation")) {
                options = BatchOperationCatalog.getAvailableOperations().stream().map(code -> {
                    String key = "help.option.op_gen_batch.operation." + BatchOperationCatalog.slug(code);
                    return new OptionHelp(code, key, "batch:" + BatchOperationCatalog.slug(code), "operation",
                            key + ".example", key + ".error");
                }).toList();
            } else if (spec.id().equals("op_gen_batch")
                    && Set.of("load_input", "dry_run").contains(action)) {
                options = List.of("CSV", "JSON Lines (.jsonl)").stream()
                        .map(code -> option(spec.id(), "input_format", code)).toList();
            } else if (spec.id().equals("op_gen_batch") && action.equals("export")) {
                options = List.of("CSV", "JSON Lines (.jsonl)").stream()
                        .map(code -> option(spec.id(), "export_format", code)).toList();
            } else if (spec.id().equals("op_gen_process_designer") && action.equals("configure_node")) {
                options = NodeCatalog.allDescriptors().stream().map(node -> new OptionHelp(node.type(),
                        node.descriptionKey(), "", "node")).toList();
            } else if (spec.id().equals("op_gen_process_designer")
                    && Set.of("edit_menu", "presets").contains(action)) {
                options = processMenuOptions(action);
            }
            actions.add(new ActionHelp(action, "help.action." + spec.id() + "." + action + ".title",
                    "help.action." + spec.id() + "." + action + ".description", fields, options,
                    "help.action." + spec.id() + "." + action + ".example", spec.reference()));
        }
        if (spec.id().equals("op_gen_process_designer")) {
            for (NodeDescriptor node : NodeCatalog.allDescriptors()) {
                List<FieldHelp> nodeFields = new ArrayList<>();
                nodeFields.add(new FieldHelp("node_purpose", node.labelKey(), node.descriptionKey(), ""));
                nodeFields.add(new FieldHelp("ports", "help.field.op_gen_process_designer.ports.label",
                        nodePortDescriptionKey(node), ""));
                for (NodeParameter parameter : node.parameters()) {
                    String description = nodeParameterDescriptionKey(node, parameter);
                    nodeFields.add(new FieldHelp(parameter.key(), parameter.labelKey(), description,
                            parameter.visibleWhen() == null || parameter.visibleWhen().isBlank()
                                    ? "" : nodeConditionKey(node, parameter)));
                }
                String actionId = nodeActionId(node.type());
                actions.add(new ActionHelp(actionId, node.labelKey(), node.descriptionKey(), nodeFields,
                        nodeOptions(node), nodeExampleKey(node), spec.reference()));
            }
        }
        return new OperationHelpDefinition(spec.id(), "help.operation." + spec.id() + ".purpose", actions,
                spec.reference(), VERIFIED_B051.contains(spec.id()) || VERIFIED_B052A.contains(spec.id()));
    }

    private static OptionHelp option(String operation, String field, String code) {
        return new OptionHelp(code, "help.option." + operation + "." + field + "." + slug(code), "", field);
    }

    private static List<OptionHelp> formatOptions(String route, boolean includeInput, boolean includeOutput, String operation) {
        OperationFormatProfile profile = OperationFormatRegistry.getInstance().getProfile(route);
        List<OptionHelp> options = new ArrayList<>();
        if (includeInput) profile.allowedInputFormats().forEach(code -> options.add(option(operation, "input_format", code)));
        if (includeOutput) profile.allowedOutputFormats().forEach(code -> options.add(option(operation, "output_format", code)));
        return List.copyOf(options);
    }

    private static List<OptionHelp> manualOptions(String action) {
        List<OptionHelp> options = new ArrayList<>();
        OperationFormatProfile profile = OperationFormatRegistry.getInstance().getProfile("Manual Conversion");
        if (Set.of("convert", "endian", "compress", "decompress").contains(action)) {
            profile.allowedInputFormats().forEach(code -> options.add(option("op_gen_manual", "input_format", code)));
            profile.allowedOutputFormats().forEach(code -> options.add(option("op_gen_manual", "output_format", code)));
        }
        if (action.equals("convert")) {
            GenericOperationOptions.EBCDIC_DIRECTIONS.forEach(code -> options.add(option("op_gen_manual", "ebcdic_direction", code)));
            EBCDICConverter.supportedCodePages().keySet().forEach(code -> options.add(option("op_gen_manual", "code_page", code)));
        } else if (action.equals("endian")) {
            GenericOperationOptions.ENDIAN_WORD_SIZES.forEach(code -> options.add(option("op_gen_manual", "word_size", code)));
        } else if (action.equals("compress") || action.equals("decompress")) {
            CompressionCodec.supportedFormats().forEach(code -> options.add(option("op_gen_manual", "compression", code)));
        } else if (action.equals("manage_templates")) {
            return List.of(); // Template names can be personal/user data; describe the controls without reading them.
        }
        return List.copyOf(options);
    }

    private static List<OptionHelp> fileOptions(String action) {
        List<OptionHelp> options = new ArrayList<>();
        if (action.equals("convert")) {
            GenericOperationOptions.FILE_FORMATS.forEach(code -> options.add(option("op_gen_file", "input_format", code)));
            GenericOperationOptions.FILE_FORMATS.forEach(code -> options.add(option("op_gen_file", "output_format", code)));
            GenericOperationOptions.FILE_ENCODINGS.forEach(code -> options.add(option("op_gen_file", "encoding", code)));
        }
        return List.copyOf(options);
    }

    private static List<OptionHelp> nodeOptions(NodeDescriptor node) {
        List<OptionHelp> options = new ArrayList<>();
        for (NodeParameter parameter : node.parameters()) {
            for (String code : parameter.options()) {
                String description = nodeOptionDescriptionKey(node, parameter, code);
                options.add(new OptionHelp(code, description, "", parameter.key()));
            }
        }
        return List.copyOf(options);
    }

    private static List<OptionHelp> processMenuOptions(String menu) {
        List<String> codes = menu.equals("edit_menu")
                ? List.of("undo", "redo", "duplicate", "layout", "clear", "reset")
                : List.of("sha256_preset", "base64_preset", "aes_gcm_preset", "aes_cmac_preset");
        return codes.stream().map(code -> new OptionHelp(code,
                "help.option.op_gen_process_designer.menu." + menu + "." + code,
                "", "canvas")).toList();
    }

    private static List<FieldHelp> fieldsForAction(Spec spec, String action, List<FieldHelp> all) {
        if (action.startsWith("node_")) return all;
        List<String> ids = switch (spec.id()) {
            case "op_gen_hash" -> action.equals("manage_templates") || action.equals("apply_template")
                    ? List.of("template")
                    : List.of("algorithm", "input", "input_format", "output", "output_format");
            case "op_gen_manual" -> switch (action) {
                case "manage_templates" -> List.of("template");
                case "convert" -> List.of("input", "input_format", "output", "output_format", "ebcdic_conversion", "ebcdic_direction", "code_page");
                case "endian" -> List.of("input", "input_format", "word_size", "output", "output_format");
                case "compress", "decompress" -> List.of("input", "input_format", "compression", "output", "output_format");
                case "shift_left", "shift_right" -> List.of("input", "shift", "output");
                case "extract_trace_hex" -> List.of("input", "output");
                case "bcd_encode", "bcd_decode", "comp3_encode", "comp3_decode" -> List.of("input", "output");
                case "base64url_encode", "base64url_decode", "base32_encode", "base32_decode", "url_encode", "url_decode" -> List.of("input", "output");
                default -> List.of("input", "output");
            };
            case "op_gen_compressed_hex" -> List.of("input", "output");
            case "op_gen_batch" -> switch (action) {
                case "select_operation" -> List.of("operation");
                case "load_input" -> List.of("input_format", "input");
                case "dry_run" -> List.of("input_format", "input", "source_column", "destination_column",
                        "operation", "status", "results");
                case "run" -> List.of("source_column", "destination_column", "operation", "stop_on_error",
                        "progress", "status", "results", "algorithm", "key", "iv_nonce", "aad",
                        "record_encoding", "charset", "compact");
                case "cancel" -> List.of("progress", "status");
                case "reset" -> List.of("input", "progress", "status", "results");
                case "export" -> List.of("results", "export_format", "status");
                default -> all.stream().map(FieldHelp::id).toList();
            };
            case "op_gen_process_designer" -> action.equals("configure_node") ? List.of("node", "parameters", "ports", "validation")
                    : action.equals("run") || action.equals("dry_run") ? List.of("run_mode", "progress", "result", "validation")
                    : all.stream().map(FieldHelp::id).toList();
            case "op_gen_file" -> action.equals("choose_source") ? List.of("source_path")
                    : action.equals("choose_destination") ? List.of("destination_path")
                    : action.equals("compare") ? List.of("source_path", "comparison_path", "result")
                    : action.equals("preview") ? List.of("source_path", "result")
                    : List.of("source_path", "destination_path", "input_format", "output_format", "encoding", "result");
            case "op_gen_random" -> List.of("byte_count", "format", "output");
            case "op_gen_check_digits" -> List.of("algorithm", "input", "result");
            case "op_gen_mod" -> List.of("operation", "operand_a", "operand_b", "modulus", "result");
            case "op_gen_clipboard" -> action.equals("search") ? List.of("search")
                    : action.equals("filter") ? List.of("filter", "pin")
                    : action.equals("compare") ? List.of("comparison", "entry")
                    : List.of("entry", "details", "pin", "note");
            case "op_keys_format_workbench" -> action.equals("validate") ? List.of("input", "secondary_input", "detected_format", "details")
                    : action.equals("convert") ? List.of("input", "source_format", "destination_format", "password", "output")
                    : List.of("input", "password", "source_format", "detected_format", "details");
            case "op_gen_envelope_inspector" -> action.equals("unwrap") ? List.of("envelope", "profile", "private_key", "recovered_key")
                    : List.of("envelope", "report", "profile");
            case "aux_byte_inspector" -> List.of("source", "representation", "offset", "length", "charset", "selection");
            case "aux_epoch" -> List.of("epoch_value", "unit", "date_time", "zone", "result");
            case "aux_json" -> List.of("input", "formatting", "output", "error_position");
            case "aux_uuid" -> List.of("identifier");
            default -> all.stream().map(FieldHelp::id).toList();
        };
        Set<String> selected = Set.copyOf(ids);
        return all.stream().filter(field -> selected.contains(field.id())).toList();
    }
    private static FieldHelp field(String operation, String id) {
        return new FieldHelp(id, "help.field." + operation + "." + id + ".label",
                "help.field." + operation + "." + id + ".description", "");
    }
    public static String nodeActionId(String type) { return "node_" + slug(type); }
    public static Set<String> verifiedB052B1NodeTypes() { return B052B1_NODE_TYPES; }
    public static boolean isVerifiedB052B1Node(String type) { return B052B1_NODE_TYPES.contains(type); }
    public static String nodeExampleKey(NodeDescriptor node) {
        return "help.action.op_gen_process_designer.node." + slug(node.type()) + ".example";
    }
    public static String nodeParameterDescriptionKey(NodeDescriptor node, NodeParameter parameter) {
        if (parameter.helpKey() != null && !parameter.helpKey().isBlank()) return parameter.helpKey();
        return "help.node.parameter." + slug(node.type()) + "." + slug(parameter.key());
    }
    public static String nodePortDescriptionKey(NodeDescriptor node) {
        return "help.node.ports." + slug(node.type());
    }
    public static String nodeOptionDescriptionKey(NodeDescriptor node, NodeParameter parameter, String code) {
        return "help.node.option." + slug(node.type()) + "." + slug(parameter.key()) + "." + slug(code);
    }
    public static String nodeConditionKey(NodeDescriptor node, NodeParameter parameter) {
        String[] condition = parameter.visibleWhen().split("=", 2);
        return "help.node.condition." + slug(node.type()) + "." + slug(parameter.key()) + "."
                + (condition.length == 2 ? slug(condition[0]) + "." + slug(condition[1]) : "unknown");
    }
    public static String slug(String value) {
        return value == null ? "unknown" : value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }
    public static List<String> liveBatchCodes() { return BatchOperationCatalog.getAvailableOperations(); }
    public static List<String> liveNodeTypes() { return NodeCatalog.allDescriptors().stream().map(NodeDescriptor::type).toList(); }
}
