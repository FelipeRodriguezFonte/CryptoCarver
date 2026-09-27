package com.cryptocarver;

import com.cryptocarver.model.SafeTransformations;
import com.cryptocarver.model.BuildInfo;
import com.cryptocarver.model.batch.BatchOperationCatalog;
import com.cryptocarver.codec.CodecException;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/** Explicitly-started loopback API for deterministic laboratory transforms. */
public final class LocalApiServer implements AutoCloseable {
    private static final int MAX_REQUEST_BYTES = 1_048_576;
    private static final Gson GSON = new Gson();
    private final HttpServer server;

    private LocalApiServer(HttpServer server) { this.server = server; }
    public static LocalApiServer start(int port) throws IOException {
        if (port < 0 || port > 65535) throw new IllegalArgumentException("Port must be between 0 and 65535");
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 0);
        LocalApiServer api = new LocalApiServer(server);
        server.createContext("/openapi.json", api::openApi);
        server.createContext("/health", api::health);
        server.createContext("/v1/operations", api::operations);
        server.createContext("/v1/transform/", api::genericTransform);
        server.createContext("/v1/sha256", exchange -> api.transform(exchange, "sha256"));
        server.createContext("/v1/base64url/encode", exchange -> api.transform(exchange, "base64url-encode"));
        server.createContext("/v1/base64url/decode", exchange -> api.transform(exchange, "base64url-decode"));
        server.setExecutor(Executors.newFixedThreadPool(2, runnable -> { Thread thread = new Thread(runnable, "cryptocarver-local-api"); thread.setDaemon(true); return thread; }));
        server.start(); return api;
    }
    public int port() { return server.getAddress().getPort(); }
    public String bindAddress() { return server.getAddress().getAddress().getHostAddress(); }
    @Override public void close() { server.stop(0); }

    private static void respond(HttpExchange exchange, int status, Map<String, ?> body) throws IOException {
        byte[] data = GSON.toJson(new LinkedHashMap<>(body)).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, data.length);
        try (java.io.OutputStream output = exchange.getResponseBody()) { output.write(data); }
    }

    private static void respondString(HttpExchange exchange, int status, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, data.length);
        try (java.io.OutputStream output = exchange.getResponseBody()) { output.write(data); }
    }

    private boolean checkRateLimitAndCors(HttpExchange exchange) throws IOException {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin != null) {
            try {
                if ("null".equals(origin)) throw new IllegalArgumentException("Null origin is not allowed");
                java.net.URI uri = new java.net.URI(origin);
                String host = uri.getHost();
                if (!"http".equalsIgnoreCase(uri.getScheme()) || host == null ||
                    (!host.equalsIgnoreCase("localhost") && !host.equals("127.0.0.1"))) {
                    throw new IllegalArgumentException("Invalid origin host or scheme");
                }
                if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                    throw new IllegalArgumentException("Origin contains invalid components");
                }
                String path = uri.getPath();
                if (path != null && !path.isEmpty() && !"/".equals(path)) {
                    throw new IllegalArgumentException("Origin contains a path");
                }
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
            } catch (Exception e) {
                respond(exchange, 403, Map.of("error", "cors_denied"));
                return false;
            }
        }

        long now = System.currentTimeMillis();
        String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
        String endpoint = exchange.getRequestURI().getPath();
        String key = clientIp + ":" + endpoint;

        synchronized (rateLimitMap) {
            rateLimitMap.entrySet().removeIf(entry -> now - entry.getValue() > 60000);
            Long last = rateLimitMap.get(key);
            if (last != null && now - last < 50) { // Max 20 req/s per endpoint
                respond(exchange, 429, Map.of("error", "rate_limit_exceeded"));
                return false;
            }
            rateLimitMap.put(key, now);
        }
        return true;
    }

    private static void respondError(HttpExchange exchange, int status, String error) throws IOException {
        respond(exchange, status, Map.of("error", error));
    }

    private final Map<String, Long> rateLimitMap = new LinkedHashMap<>();

    private void openApi(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) { respond(exchange, 405, Map.of("error", "method_not_allowed")); return; }
        if (!checkRateLimitAndCors(exchange)) return;
        Map<String, Object> paths = new LinkedHashMap<>();
        paths.put("/health", Map.of("get", Map.of("responses", Map.of("200", Map.of("description", "OK")))));
        paths.put("/v1/operations", Map.of("get", Map.of("responses", Map.of("200", Map.of("description", "Available deterministic operations")))));
        paths.put("/v1/sha256", legacyOpenApiOperation());
        paths.put("/v1/base64url/encode", legacyOpenApiOperation());
        paths.put("/v1/base64url/decode", legacyOpenApiOperation());
        Map<String, Object> generic = new LinkedHashMap<>();
        generic.put("post", Map.of("summary", "Apply a catalog operation", "description", "Operation slug is listed by GET /v1/operations.",
                "parameters", List.of(Map.of("name", "operation", "in", "path", "required", true, "schema", Map.of("type", "string", "enum", BatchOperationCatalog.descriptions().keySet()))),
                "requestBody", Map.of("required", true, "content", Map.of("application/json", Map.of("schema", Map.of("type", "object", "required", List.of("input"), "properties", Map.of("input", Map.of("type", "string")))))),
                "responses", Map.of("200", Map.of("description", "Transformed input"), "400", Map.of("description", "Invalid request or input"),
                        "404", Map.of("description", "Unknown operation"), "413", Map.of("description", "Request body exceeds 1 MiB"))));
        paths.put("/v1/transform/{operation}", generic);
        Map<String, Object> spec = Map.of("openapi", "3.0.0", "info", Map.of("title", "CryptoCarver Local API", "version", BuildInfo.version()),
                "components", Map.of("schemas", Map.of("Error", Map.of("type", "object", "required", List.of("error"), "properties", Map.of("error", Map.of("type", "string")), "additionalProperties", false))), "paths", paths);
        respond(exchange, 200, spec);
    }

    private static Map<String, Object> legacyOpenApiOperation() {
        return Map.of("post", Map.of("responses", Map.of("200", Map.of("description", "OK"), "400", Map.of("description", "Invalid request or input"), "413", Map.of("description", "Request body exceeds 1 MiB"))));
    }

    private void operations(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) { respond(exchange, 405, Map.of("error", "method_not_allowed")); return; }
        if (!checkRateLimitAndCors(exchange)) return;
        Map<String, Object> entries = new LinkedHashMap<>();
        BatchOperationCatalog.descriptions().forEach((slug, description) -> entries.put(slug, Map.of("description", description)));
        respond(exchange, 200, Map.of("operations", entries));
    }

    private void health(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) { respond(exchange, 405, Map.of("error", "method_not_allowed")); return; }
        if (!checkRateLimitAndCors(exchange)) return;
        respond(exchange, 200, Map.of("status", "ok", "scope", "loopback-only"));
    }
    private void transform(HttpExchange exchange, String operation) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) { respond(exchange, 405, Map.of("error", "method_not_allowed")); return; }
        if (!checkRateLimitAndCors(exchange)) return;
        try {
            byte[] bytes = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
            if (bytes.length > MAX_REQUEST_BYTES) { respond(exchange, 413, Map.of("error", "request_too_large", "maxBytes", MAX_REQUEST_BYTES)); return; }
            Map<String, Object> request;
            try {
                @SuppressWarnings("unchecked") Map<String, Object> parsed = GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), Map.class);
                request = parsed;
            } catch (JsonParseException e) {
                respondError(exchange, 400, "invalid_request");
                return;
            }
            Object input = request == null ? null : request.get("input");
            if (!(input instanceof String value)) { respondError(exchange, 400, "invalid_request"); return; }
            String result = switch (operation) {
                case "sha256" -> SafeTransformations.sha256(value);
                case "base64url-encode" -> SafeTransformations.encodeBase64Url(value);
                case "base64url-decode" -> SafeTransformations.decodeBase64Url(value);
                default -> throw new IllegalStateException("Unsupported operation");
            };
            respond(exchange, 200, Map.of("operation", operation, "result", result));
        } catch (CodecException | IllegalArgumentException e) { respondError(exchange, 400, "invalid_input"); }
        catch (Exception e) { respondError(exchange, 500, "operation_failed"); }
    }

    private void genericTransform(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) { respond(exchange, 405, Map.of("error", "method_not_allowed")); return; }
        if (!checkRateLimitAndCors(exchange)) return;
        String path = exchange.getRequestURI().getPath();
        String slug = path.startsWith("/v1/transform/") ? path.substring("/v1/transform/".length()) : "";
        if (slug.isBlank() || slug.contains("/")) { respondError(exchange, 404, "unknown_operation"); return; }
        String operation = BatchOperationCatalog.getAvailableOperations().stream()
                .filter(candidate -> BatchOperationCatalog.slug(candidate).equals(slug)).findFirst().orElse(null);
        if (operation == null) { respondError(exchange, 404, "unknown_operation"); return; }
        try {
            byte[] bytes = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
            if (bytes.length > MAX_REQUEST_BYTES) { respond(exchange, 413, Map.of("error", "request_too_large", "maxBytes", MAX_REQUEST_BYTES)); return; }
            Map<String, Object> request;
            try {
                @SuppressWarnings("unchecked") Map<String, Object> parsed = GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), Map.class);
                request = parsed;
            } catch (JsonParseException e) { respondError(exchange, 400, "invalid_request"); return; }
            Object input = request == null ? null : request.get("input");
            if (!(input instanceof String value)) { respondError(exchange, 400, "invalid_request"); return; }
            String result = BatchOperationCatalog.execute(operation, Map.of("input", value), "input", "result").get("result");
            respond(exchange, 200, Map.of("operation", slug, "result", result));
        } catch (CodecException | IllegalArgumentException e) {
            respondError(exchange, 400, e.getMessage() == null ? "invalid_input" : e.getMessage());
        } catch (Exception e) { respondError(exchange, 500, "operation_failed"); }
    }
}
