package com.cryptocarver;

import com.cryptocarver.model.batch.BatchInputCodec;
import com.cryptocarver.model.batch.BatchOutputCodec;
import com.cryptocarver.model.batch.BatchRunner;
import com.cryptocarver.model.BuildInfo;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/** Small, local CLI for safe and deterministic CryptoCarver lab operations. */
public final class CryptoCarverCli {
    public static final int EXIT_SUCCESS = 0;
    public static final int EXIT_INVALID_ARGS = 2;
    public static final int EXIT_OPERATION_FAILED = 3;
    public static final int EXIT_IO_ERROR = 4;

    /** Flags that are switches rather than name/value pairs. */
    private static final java.util.Set<String> VALUELESS_FLAGS =
            java.util.Set.of("--json", "--detail", "--no-detail",
                    "--host-version", "--table616-version", "--nocv");

    private CryptoCarverCli() { }

    public static void main(String[] args) {
        int code = run(args, new PrintWriter(System.out, true), new PrintWriter(System.err, true));
        if (code != EXIT_SUCCESS) System.exit(code);
    }

    static int run(String[] args, PrintWriter out, PrintWriter err) {
        if (args == null) {
            args = new String[0];
        }
        boolean json = contains(args, "--json");
        if (args.length == 0) { help(out, json); return EXIT_SUCCESS; }

        try {
            validateNoExtraFlags(args);
            if ("help".equals(args[0]) || "--help".equals(args[0])) { help(out, json); return EXIT_SUCCESS; }
            if ("--version".equals(args[0])) { version(out, json); return EXIT_SUCCESS; }
            return switch (args[0]) {
                case "sha256" -> single(args, "sha256", com.cryptocarver.model.SafeTransformations.sha256(requireArgument(args, 1)), json, out);
                case "base64url-encode" -> single(args, "base64url-encode", com.cryptocarver.model.SafeTransformations.encodeBase64Url(requireArgument(args, 1)), json, out);
                case "base64url-decode" -> single(args, "base64url-decode", com.cryptocarver.model.SafeTransformations.decodeBase64Url(requireArgument(args, 1)), json, out);
                case "compress-gzip" -> single(args, "compress-gzip", com.cryptocarver.model.SafeTransformations.compressGzip(requireArgument(args, 1)), json, out);
                case "decompress-gzip" -> single(args, "decompress-gzip", com.cryptocarver.model.SafeTransformations.decompressGzip(requireArgument(args, 1)), json, out);
                case "inspect-asn1" -> single(args, "inspect-asn1", com.cryptocarver.model.SafeTransformations.inspectAsn1(requireArgument(args, 1)), json, out);
                case "inspect-tlv" -> single(args, "inspect-tlv", com.cryptocarver.model.SafeTransformations.inspectTlv(requireArgument(args, 1)), json, out);
                case "hmac-sha256" -> single(args, "hmac-sha256", hmacSha256(requireArgument(args, 1)), json, out);
                case "batch" -> batch(args, out, json);
                case "icsf-token" -> icsfToken(args, out, json);
                case "icsf-batch" -> icsfBatch(args, out, json);
                case "icsf-export" -> icsfKeyWrap(args, out, json, KeyWrapCommand.EXPORT);
                case "icsf-import" -> icsfKeyWrap(args, out, json, KeyWrapCommand.IMPORT);
                case "icsf-inspect" -> icsfKeyWrap(args, out, json, KeyWrapCommand.INSPECT);
                case "icsf-resolve" -> icsfKeyWrap(args, out, json, KeyWrapCommand.RESOLVE);
                case "serve" -> serve(args, out);
                default -> { error(err, "Unknown command: " + args[0], json); help(err, json); yield EXIT_INVALID_ARGS; }
            };
        } catch (IllegalArgumentException e) { error(err, "Error: " + e.getMessage(), json); return EXIT_INVALID_ARGS; }
        catch (IOException e) { error(err, "I/O error: " + e.getMessage(), json); return EXIT_IO_ERROR; }
        catch (Exception e) { error(err, "Operation failed: " + e.getMessage(), json); return EXIT_OPERATION_FAILED; }
    }

    private static String hmacSha256(String value) throws Exception {
        String key = System.getenv("CRYPTOCARVER_HMAC_KEY");
        if (key == null || key.isBlank()) throw new IllegalArgumentException("CRYPTOCARVER_HMAC_KEY environment variable is required for HMAC");
        return com.cryptocarver.model.SafeTransformations.hmacSha256(value, key);
    }

    /** Which key wrapping verb a command line asked for. */
    private enum KeyWrapCommand { EXPORT, IMPORT, INSPECT, RESOLVE }

    private static void validateNoExtraFlags(String[] args) {
        String cmd = args[0];
        java.util.Set<String> allowedFlags = new java.util.HashSet<>(List.of("--json"));
        if ("batch".equals(cmd)) allowedFlags.addAll(List.of("--format", "--output", "--column"));
        if ("serve".equals(cmd)) allowedFlags.add("--port");
        if ("icsf-token".equals(cmd)) allowedFlags.add("--provenance");
        if ("icsf-batch".equals(cmd)) {
            allowedFlags.addAll(List.of("--provenance", "--format", "--txt", "--csv",
                    "--json-out", "--sep", "--detail", "--no-detail"));
        }
        if (cmd.startsWith("icsf-export") || cmd.startsWith("icsf-import")
                || cmd.startsWith("icsf-inspect") || cmd.startsWith("icsf-resolve")) {
            allowedFlags.addAll(List.of("--key", "--kek", "--token", "--type", "--cv",
                    "--variant", "--mode", "--rn", "--expected-key", "--expected-kcv",
                    "--host-version", "--table616-version", "--nocv", "--lang", "--out"));
        }

        int expectedArgs = switch (cmd) {
            case "batch" -> 3;
            // Every input is a named flag: with a key, a KEK and a token in play, positional
            // hex would be unreadable and easy to transpose.
            case "serve", "icsf-export", "icsf-import", "icsf-inspect", "icsf-resolve" -> 1;
            case "help", "--help", "--version" -> 1;
            default -> 2; // single operations take 1 arg
        };

        for (int i = 1; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                if (!allowedFlags.contains(args[i])) throw new IllegalArgumentException("Unknown flag: " + args[i]);
                if (!VALUELESS_FLAGS.contains(args[i])) {
                    if (i + 1 >= args.length || args[i+1].startsWith("--")) throw new IllegalArgumentException("Flag " + args[i] + " requires a value");
                    i++; // skip value
                }
            } else {
                if (i >= expectedArgs) throw new IllegalArgumentException("Unexpected positional argument: " + args[i]);
            }
        }
    }

    private static void error(PrintWriter err, String message, boolean json) {
        if (json) {
            err.println(new Gson().toJson(Map.of("error", true, "message", message)));
        } else {
            err.println(message);
        }
    }

    private static void version(PrintWriter out, boolean json) {
        String version = BuildInfo.version();
        if (json) out.println(new Gson().toJson(Map.of("version", version)));
        else out.println("CryptoCarver CLI version " + version);
    }

    private static int single(String[] args, String operation, String result, boolean json, PrintWriter out) {
        if (json) out.println(new Gson().toJson(Map.of("operation", operation, "result", result)));
        else out.println(result);
        return EXIT_SUCCESS;
    }

    private static int batch(String[] args, PrintWriter out, boolean json) throws Exception {
        if (args.length < 3) throw new IllegalArgumentException("Usage: batch <operation> <file> [--format csv|jsonl] [--output csv|jsonl] [--column name]");
        String operation = args[1]; Path input = Path.of(args[2]);
        String inputFormat = option(args, "--format");
        if (inputFormat == null) inputFormat = input.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".csv") ? "csv" : "jsonl";
        String outputFormat = option(args, "--output"); if (outputFormat == null) outputFormat = "jsonl";
        String column = option(args, "--column"); if (column == null) column = "input";

        if (!("csv".equals(inputFormat) || "jsonl".equals(inputFormat)) || !("csv".equals(outputFormat) || "jsonl".equals(outputFormat))) {
            throw new IllegalArgumentException("Formats must be csv or jsonl");
        }

        List<Map<String, String>> rows;
        try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
            rows = "csv".equals(inputFormat) ? BatchInputCodec.parseCsv(reader, BatchInputCodec.MAX_ROWS)
                                             : BatchInputCodec.parseJsonLines(reader, BatchInputCodec.MAX_ROWS);
        }
        final String col = column;
        if (rows.stream().anyMatch(row -> !row.containsKey(col))) throw new IllegalArgumentException("Every batch row requires field: " + col);

        BatchRunner.Report report = BatchRunner.run(rows, (rowNum, row) -> Map.of("result", switch (operation) {
            case "sha256" -> com.cryptocarver.model.SafeTransformations.sha256(row.get(col));
            case "base64url-encode" -> com.cryptocarver.model.SafeTransformations.encodeBase64Url(row.get(col));
            case "base64url-decode" -> com.cryptocarver.model.SafeTransformations.decodeBase64Url(row.get(col));
            case "compress-gzip" -> com.cryptocarver.model.SafeTransformations.compressGzip(row.get(col));
            case "decompress-gzip" -> com.cryptocarver.model.SafeTransformations.decompressGzip(row.get(col));
            case "inspect-asn1" -> com.cryptocarver.model.SafeTransformations.inspectAsn1(row.get(col));
            case "inspect-tlv" -> com.cryptocarver.model.SafeTransformations.inspectTlv(row.get(col));
            case "hmac-sha256" -> hmacSha256(row.get(col));
            default -> throw new IllegalArgumentException("Unsupported batch operation: " + operation);
        }), () -> false);
        if (json) {
            out.println(new Gson().toJson(Map.of(
                    "operation", "batch",
                    "cancelled", report.cancelled(),
                    "succeeded", report.succeeded(),
                    "failed", report.failed(),
                    "results", report.results())));
        } else {
            out.print("csv".equals(outputFormat) ? BatchOutputCodec.toCsv(report) : BatchOutputCodec.toJsonLines(report));
        }
        out.flush();
        return report.failed() == 0 ? 0 : 3;
    }

    /** Analyses one ICSF / CCA key token given as hexadecimal. */
    private static int icsfToken(String[] args, PrintWriter out, boolean json) {
        String hex = requireArgument(args, 1);
        com.cryptocarver.crypto.icsf.Origin origin =
                com.cryptocarver.crypto.icsf.Origin.fromValue(option(args, "--provenance"));
        byte[] token = com.cryptocarver.crypto.icsf.IcsfHex.clean(hex);
        var result = com.cryptocarver.crypto.icsf.IcsfTokenParser.parse(token, origin);

        if (json) {
            out.println(new Gson().toJson(com.cryptocarver.crypto.icsf.IcsfTokenReport.toMap(result)));
        } else {
            out.println(com.cryptocarver.crypto.icsf.IcsfTokenReport.renderText(result, origin, token));
        }
        out.flush();
        return result.isOk() ? EXIT_SUCCESS : EXIT_OPERATION_FAILED;
    }

    /**
     * Analyses a whole file of ICSF / CCA key tokens.
     *
     * <p>The files this writes carry the tokens in full in hexadecimal, so they want
     * the same handling as the dump they came from. Nothing here decrypts anything.</p>
     */
    private static int icsfBatch(String[] args, PrintWriter out, boolean json) throws IOException {
        String source = requireArgument(args, 1);
        var format = com.cryptocarver.crypto.icsf.BatchInputFormat.fromValue(option(args, "--format"));
        var origin = com.cryptocarver.crypto.icsf.Origin.fromValue(option(args, "--provenance"));

        String text = "-".equals(source)
                ? new String(System.in.readAllBytes(), StandardCharsets.UTF_8)
                : Files.readString(Path.of(source), StandardCharsets.UTF_8);

        var report = com.cryptocarver.crypto.icsf.IcsfBatchAnalyzer.analyse(text, format, origin);
        boolean detail = contains(args, "--detail");

        if (json) {
            out.println(new Gson().toJson(
                    com.cryptocarver.crypto.icsf.IcsfBatchRenderer.toMap(report, detail)));
        } else {
            out.println(detail
                    ? com.cryptocarver.crypto.icsf.IcsfBatchRenderer.renderFull(report, true)
                    : com.cryptocarver.crypto.icsf.IcsfBatchRenderer.renderSummary(report));
        }

        String txtPath = option(args, "--txt");
        if (txtPath != null) {
            // With a large batch the per-token detail has to be skippable: the full card
            // for an entire CKDS runs to tens of megabytes.
            boolean withDetail = !contains(args, "--no-detail");
            Files.writeString(Path.of(txtPath),
                    com.cryptocarver.crypto.icsf.IcsfBatchRenderer.renderFull(report, withDetail),
                    StandardCharsets.UTF_8);
            out.println("Full report written to " + txtPath);
        }
        String csvPath = option(args, "--csv");
        if (csvPath != null) {
            String separator = option(args, "--sep");
            char delimiter = (separator == null || separator.isEmpty()) ? ';' : separator.charAt(0);
            Files.write(Path.of(csvPath),
                    com.cryptocarver.crypto.icsf.IcsfBatchRenderer.toCsvBytes(report, delimiter));
            out.println("CSV inventory written to " + csvPath);
        }
        String jsonPath = option(args, "--json-out");
        if (jsonPath != null) {
            Files.writeString(Path.of(jsonPath), new com.google.gson.GsonBuilder()
                            .setPrettyPrinting().create()
                            .toJson(com.cryptocarver.crypto.icsf.IcsfBatchRenderer.toMap(report, true)),
                    StandardCharsets.UTF_8);
            out.println("JSON report written to " + jsonPath);
        }
        out.flush();
        return report.failed().isEmpty() ? EXIT_SUCCESS : EXIT_OPERATION_FAILED;
    }

    /**
     * Reproduces the CCA native key export and import verbs in the clear.
     *
     * <p>Unlike the two analysers, this one is handed key material in the clear, because
     * reproducing the host's arithmetic is what it is for. Its output carries whole keys,
     * so it wants the same handling as the key material it came from.</p>
     */
    private static int icsfKeyWrap(String[] args, PrintWriter out, boolean json,
                                   KeyWrapCommand command) throws IOException {
        java.util.Locale locale = localeOf(option(args, "--lang"));
        var variant = keyWrapVariant(option(args, "--variant"));
        var mode = keyWrapMode(option(args, "--mode"));
        String type = option(args, "--type");
        String kek = option(args, "--kek");
        String token = option(args, "--token");

        com.cryptocarver.crypto.icsf.keywrap.KeyWrapResult result = switch (command) {
            case EXPORT -> com.cryptocarver.crypto.icsf.keywrap.IcsfKeyWrapService.export(
                    new com.cryptocarver.crypto.icsf.keywrap.IcsfKeyWrapService.ExportRequest(
                            option(args, "--key"), kek, type == null ? "EXPORTER" : type,
                            option(args, "--cv"), variant, mode,
                            contains(args, "--nocv"),
                            // Table 616 says X'01' for a double-length key; real hosts write
                            // X'00'. The host form is the default because comparing against a
                            // host token is the reason to run this at all.
                            !contains(args, "--table616-version"),
                            option(args, "--rn")));
            case IMPORT -> com.cryptocarver.crypto.icsf.keywrap.IcsfKeyWrapService.importKey(
                    new com.cryptocarver.crypto.icsf.keywrap.IcsfKeyWrapService.ImportRequest(
                            token, kek, option(args, "--cv"), type, variant, mode,
                            option(args, "--rn")));
            case INSPECT -> com.cryptocarver.crypto.icsf.keywrap.IcsfKeyWrapService.inspect(token);
            case RESOLVE -> com.cryptocarver.crypto.icsf.keywrap.IcsfKeyWrapService.resolve(
                    new com.cryptocarver.crypto.icsf.keywrap.IcsfKeyWrapService.ResolveRequest(
                            token, kek, option(args, "--expected-key"),
                            option(args, "--expected-kcv"), option(args, "--cv"), type));
        };

        String rendered = json
                ? new Gson().toJson(com.cryptocarver.crypto.icsf.keywrap.KeyWrapReport.toMap(result, locale))
                : com.cryptocarver.crypto.icsf.keywrap.KeyWrapReport.render(result, locale);
        out.println(rendered);
        out.flush();

        String destination = option(args, "--out");
        if (destination != null) {
            Files.writeString(Path.of(destination), rendered, StandardCharsets.UTF_8);
        }
        return result.ok() ? EXIT_SUCCESS : EXIT_OPERATION_FAILED;
    }

    private static java.util.Locale localeOf(String tag) {
        if (tag == null || tag.isBlank()) return java.util.Locale.getDefault();
        return java.util.Locale.forLanguageTag(tag);
    }

    private static com.cryptocarver.crypto.icsf.keywrap.KeyWrapScheme.Variant keyWrapVariant(String value) {
        if (value == null || value.isBlank()) {
            return com.cryptocarver.crypto.icsf.keywrap.KeyWrapScheme.Variant.CV;
        }
        return switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "cv" -> com.cryptocarver.crypto.icsf.keywrap.KeyWrapScheme.Variant.CV;
            case "nocv", "plain" -> com.cryptocarver.crypto.icsf.keywrap.KeyWrapScheme.Variant.PLAIN;
            case "cv-swapped", "swapped" -> com.cryptocarver.crypto.icsf.keywrap.KeyWrapScheme.Variant.CV_SWAPPED;
            default -> throw new IllegalArgumentException(
                    "Unknown --variant: " + value + " (use cv, nocv or cv-swapped)");
        };
    }

    private static com.cryptocarver.crypto.icsf.keywrap.KeyWrapScheme.Mode keyWrapMode(String value) {
        if (value == null || value.isBlank()) {
            return com.cryptocarver.crypto.icsf.keywrap.KeyWrapScheme.Mode.ECB;
        }
        return switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "ecb" -> com.cryptocarver.crypto.icsf.keywrap.KeyWrapScheme.Mode.ECB;
            case "cbc" -> com.cryptocarver.crypto.icsf.keywrap.KeyWrapScheme.Mode.CBC;
            default -> throw new IllegalArgumentException("Unknown --mode: " + value + " (use ecb or cbc)");
        };
    }

    private static int serve(String[] args, PrintWriter out) throws Exception {
        boolean json = contains(args, "--json");
        String requestedPort = option(args, "--port"); int port = requestedPort == null ? 8787 : Integer.parseInt(requestedPort);
        try (LocalApiServer server = LocalApiServer.start(port)) {
            if (json) out.println(new Gson().toJson(Map.of("serve", "CryptoCarver local API listening on http://127.0.0.1:" + server.port())));
            else out.println("CryptoCarver local API listening on http://127.0.0.1:" + server.port() + " (Ctrl+C to stop)");
            out.flush();
            Runtime.getRuntime().addShutdownHook(new Thread(server::close, "cryptocarver-api-stop"));
            new java.util.concurrent.CountDownLatch(1).await();
        }
        return 0;
    }

    private static String requireArgument(String[] args, int position) {
        if (args.length <= position || args[position].startsWith("--")) throw new IllegalArgumentException("Missing command value");
        return args[position];
    }
    private static boolean contains(String[] args, String option) { for (String arg : args) if (option.equals(arg)) return true; return false; }
    private static String option(String[] args, String option) {
        for (int i = 0; i < args.length; i++) if (option.equals(args[i])) return (i + 1 < args.length) ? args[i + 1] : null; return null;
    }
    private static void help(PrintWriter out, boolean json) {
        if (json) {
            out.println(new Gson().toJson(Map.of("help", "Available commands: sha256, base64url-encode, base64url-decode, compress-gzip, decompress-gzip, inspect-asn1, inspect-tlv, hmac-sha256, batch, icsf-token, icsf-batch, icsf-export, icsf-import, icsf-inspect, icsf-resolve, serve")));
            return;
        }
        out.println("CryptoCarver CLI (local laboratory operations)");
        out.println("  sha256|base64url-encode|base64url-decode|compress-gzip|decompress-gzip|inspect-asn1|inspect-tlv|hmac-sha256 <value> [--json]");
        out.println("  batch <operation> <file> [--format csv|jsonl] [--output csv|jsonl] [--column name]");
        out.println("  icsf-token <hex> [--provenance kds-crudo|key-record-read|inferir] [--json]");
        out.println("  icsf-batch <file|-> [--format auto|linea|dos-filas] [--provenance ...] [--detail]");
        out.println("             [--txt PATH [--no-detail]] [--csv PATH [--sep ;]] [--json-out PATH]");
        out.println("  serve [--port 8787]  (loopback-only local API)");
        out.println("Note: hmac-sha256 requires CRYPTOCARVER_HMAC_KEY env var.");
        out.println("Note: icsf-* decrypt nothing, but their output carries whole key tokens in hex.");
    }
}
