package com.cryptoforge;

import com.cryptoforge.model.batch.BatchInputCodec;
import com.cryptoforge.model.batch.BatchOutputCodec;
import com.cryptoforge.model.batch.BatchRunner;
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
    private CryptoCarverCli() { }

    public static void main(String[] args) {
        int code = run(args, new PrintWriter(System.out, true), new PrintWriter(System.err, true));
        if (code != 0) System.exit(code);
    }

    static int run(String[] args, PrintWriter out, PrintWriter err) {
        if (args == null || args.length == 0 || "help".equals(args[0]) || "--help".equals(args[0])) { help(out); return 0; }
        boolean json = contains(args, "--json");
        try {
            return switch (args[0]) {
                case "sha256" -> single(args, "sha256", sha256(requireArgument(args, 1)), json, out);
                case "base64url-encode" -> single(args, "base64url-encode", com.cryptoforge.model.SafeTransformations.encodeBase64Url(requireArgument(args, 1)), json, out);
                case "base64url-decode" -> single(args, "base64url-decode", com.cryptoforge.model.SafeTransformations.decodeBase64Url(requireArgument(args, 1)), json, out);
                case "batch" -> batch(args, out);
                case "serve" -> serve(args, out);
                default -> { err.println("Unknown command: " + args[0]); help(err); yield 2; }
            };
        } catch (IllegalArgumentException e) { err.println("Error: " + e.getMessage()); return 2; }
        catch (IOException e) { err.println("I/O error: " + e.getMessage()); return 4; }
        catch (Exception e) { err.println("Operation failed: " + e.getMessage()); return 3; }
    }

    private static int single(String[] args, String operation, String result, boolean json, PrintWriter out) {
        if (args.length > 2 && !json) throw new IllegalArgumentException("Unexpected argument: " + args[2]);
        if (json) out.println(new Gson().toJson(Map.of("operation", operation, "result", result)));
        else out.println(result);
        return 0;
    }

    private static int batch(String[] args, PrintWriter out) throws IOException {
        if (args.length < 3) throw new IllegalArgumentException("Usage: batch <sha256|base64url-encode|base64url-decode> <file> [--format csv|jsonl] [--output csv|jsonl]");
        String operation = args[1]; Path input = Path.of(args[2]);
        String inputFormat = option(args, "--format");
        if (inputFormat == null) inputFormat = input.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".csv") ? "csv" : "jsonl";
        String outputFormat = option(args, "--output"); if (outputFormat == null) outputFormat = "jsonl";
        if (!("csv".equals(inputFormat) || "jsonl".equals(inputFormat)) || !("csv".equals(outputFormat) || "jsonl".equals(outputFormat))) {
            throw new IllegalArgumentException("Formats must be csv or jsonl");
        }
        String content = Files.readString(input, StandardCharsets.UTF_8);
        List<Map<String, String>> rows = "csv".equals(inputFormat) ? BatchInputCodec.parseCsv(content) : BatchInputCodec.parseJsonLines(content);
        if (rows.stream().anyMatch(row -> !row.containsKey("input"))) throw new IllegalArgumentException("Every batch row requires an input field");
        BatchRunner.Report report = BatchRunner.run(rows, row -> Map.of("result", switch (operation) {
            case "sha256" -> sha256(row.get("input"));
            case "base64url-encode" -> com.cryptoforge.model.SafeTransformations.encodeBase64Url(row.get("input"));
            case "base64url-decode" -> com.cryptoforge.model.SafeTransformations.decodeBase64Url(row.get("input"));
            default -> throw new IllegalArgumentException("Unsupported batch operation: " + operation);
        }), () -> false);
        out.print("csv".equals(outputFormat) ? BatchOutputCodec.toCsv(report) : BatchOutputCodec.toJsonLines(report)); out.flush();
        return report.failed() == 0 ? 0 : 3;
    }

    private static String sha256(String value) throws Exception { return com.cryptoforge.model.SafeTransformations.sha256(value); }
    private static int serve(String[] args, PrintWriter out) throws Exception {
        String requestedPort = option(args, "--port"); int port = requestedPort == null ? 8787 : Integer.parseInt(requestedPort);
        try (LocalApiServer server = LocalApiServer.start(port)) {
            out.println("CryptoCarver local API listening on http://127.0.0.1:" + server.port() + " (Ctrl+C to stop)"); out.flush();
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
        for (int i = 0; i + 1 < args.length; i++) if (option.equals(args[i])) return args[i + 1]; return null;
    }
    private static void help(PrintWriter out) {
        out.println("CryptoCarver CLI (local laboratory operations)");
        out.println("  sha256 <text> [--json]");
        out.println("  base64url-encode <text> [--json]");
        out.println("  base64url-decode <value> [--json]");
        out.println("  batch <sha256|base64url-encode|base64url-decode> <file> [--format csv|jsonl] [--output csv|jsonl]");
        out.println("  serve [--port 8787]  (loopback-only local API)");
    }
}
