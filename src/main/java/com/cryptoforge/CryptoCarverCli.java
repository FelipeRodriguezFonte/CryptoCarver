package com.cryptoforge;

import com.cryptoforge.model.batch.BatchInputCodec;
import com.cryptoforge.model.batch.BatchOutputCodec;
import com.cryptoforge.model.batch.BatchRunner;
import com.cryptoforge.model.BuildInfo;
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
                case "sha256" -> single(args, "sha256", com.cryptoforge.model.SafeTransformations.sha256(requireArgument(args, 1)), json, out);
                case "base64url-encode" -> single(args, "base64url-encode", com.cryptoforge.model.SafeTransformations.encodeBase64Url(requireArgument(args, 1)), json, out);
                case "base64url-decode" -> single(args, "base64url-decode", com.cryptoforge.model.SafeTransformations.decodeBase64Url(requireArgument(args, 1)), json, out);
                case "compress-gzip" -> single(args, "compress-gzip", com.cryptoforge.model.SafeTransformations.compressGzip(requireArgument(args, 1)), json, out);
                case "decompress-gzip" -> single(args, "decompress-gzip", com.cryptoforge.model.SafeTransformations.decompressGzip(requireArgument(args, 1)), json, out);
                case "inspect-asn1" -> single(args, "inspect-asn1", com.cryptoforge.model.SafeTransformations.inspectAsn1(requireArgument(args, 1)), json, out);
                case "inspect-tlv" -> single(args, "inspect-tlv", com.cryptoforge.model.SafeTransformations.inspectTlv(requireArgument(args, 1)), json, out);
                case "hmac-sha256" -> single(args, "hmac-sha256", hmacSha256(requireArgument(args, 1)), json, out);
                case "batch" -> batch(args, out, json);
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
        return com.cryptoforge.model.SafeTransformations.hmacSha256(value, key);
    }

    private static void validateNoExtraFlags(String[] args) {
        String cmd = args[0];
        java.util.Set<String> allowedFlags = new java.util.HashSet<>(List.of("--json"));
        if ("batch".equals(cmd)) allowedFlags.addAll(List.of("--format", "--output", "--column"));
        if ("serve".equals(cmd)) allowedFlags.add("--port");

        int expectedArgs = switch (cmd) {
            case "batch" -> 3;
            case "serve" -> 1;
            case "help", "--help", "--version" -> 1;
            default -> 2; // single operations take 1 arg
        };

        for (int i = 1; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                if (!allowedFlags.contains(args[i])) throw new IllegalArgumentException("Unknown flag: " + args[i]);
                if (!args[i].equals("--json")) {
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

        BatchRunner.Report report = BatchRunner.run(rows, row -> Map.of("result", switch (operation) {
            case "sha256" -> com.cryptoforge.model.SafeTransformations.sha256(row.get(col));
            case "base64url-encode" -> com.cryptoforge.model.SafeTransformations.encodeBase64Url(row.get(col));
            case "base64url-decode" -> com.cryptoforge.model.SafeTransformations.decodeBase64Url(row.get(col));
            case "compress-gzip" -> com.cryptoforge.model.SafeTransformations.compressGzip(row.get(col));
            case "decompress-gzip" -> com.cryptoforge.model.SafeTransformations.decompressGzip(row.get(col));
            case "inspect-asn1" -> com.cryptoforge.model.SafeTransformations.inspectAsn1(row.get(col));
            case "inspect-tlv" -> com.cryptoforge.model.SafeTransformations.inspectTlv(row.get(col));
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
            out.println(new Gson().toJson(Map.of("help", "Available commands: sha256, base64url-encode, base64url-decode, compress-gzip, decompress-gzip, inspect-asn1, inspect-tlv, hmac-sha256, batch, serve")));
            return;
        }
        out.println("CryptoCarver CLI (local laboratory operations)");
        out.println("  sha256|base64url-encode|base64url-decode|compress-gzip|decompress-gzip|inspect-asn1|inspect-tlv|hmac-sha256 <value> [--json]");
        out.println("  batch <operation> <file> [--format csv|jsonl] [--output csv|jsonl] [--column name]");
        out.println("  serve [--port 8787]  (loopback-only local API)");
        out.println("Note: hmac-sha256 requires CRYPTOCARVER_HMAC_KEY env var.");
    }
}
