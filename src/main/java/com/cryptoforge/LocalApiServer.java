package com.cryptoforge;

import com.cryptoforge.model.SafeTransformations;
import com.cryptoforge.model.BuildInfo;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
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
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        LocalApiServer api = new LocalApiServer(server);
        server.createContext("/openapi.json", api::openApi);
        server.createContext("/health", api::health);
        server.createContext("/v1/sha256", exchange -> api.transform(exchange, "sha256"));
        server.createContext("/v1/base64url/encode", exchange -> api.transform(exchange, "base64url-encode"));
        server.createContext("/v1/base64url/decode", exchange -> api.transform(exchange, "base64url-decode"));
        server.setExecutor(Executors.newFixedThreadPool(2, runnable -> { Thread thread = new Thread(runnable, "cryptocarver-local-api"); thread.setDaemon(true); return thread; }));
        server.start(); return api;
    }
    public int port() { return server.getAddress().getPort(); }
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
    private final Map<String, Long> rateLimitMap = new LinkedHashMap<>();

    private void openApi(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) { respond(exchange, 405, Map.of("error", "method_not_allowed")); return; }
        if (!checkRateLimitAndCors(exchange)) return;
        String spec = """
        {
          "openapi": "3.0.0",
          "info": { "title": "CryptoCarver Local API", "version": "%s" },
          "paths": {
            "/health": { "get": { "responses": { "200": { "description": "OK" } } } },
            "/v1/sha256": { "post": { "responses": { "200": { "description": "OK" } } } },
            "/v1/base64url/encode": { "post": { "responses": { "200": { "description": "OK" } } } },
            "/v1/base64url/decode": { "post": { "responses": { "200": { "description": "OK" } } } }
          }
        }
        """.formatted(BuildInfo.version());
        respondString(exchange, 200, spec);
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
            @SuppressWarnings("unchecked") Map<String, Object> request = GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), Map.class);
            Object input = request == null ? null : request.get("input");
            if (!(input instanceof String value)) { respond(exchange, 400, Map.of("error", "input_must_be_a_string")); return; }
            String result = switch (operation) {
                case "sha256" -> SafeTransformations.sha256(value);
                case "base64url-encode" -> SafeTransformations.encodeBase64Url(value);
                case "base64url-decode" -> SafeTransformations.decodeBase64Url(value);
                default -> throw new IllegalStateException("Unsupported operation");
            };
            respond(exchange, 200, Map.of("operation", operation, "result", result));
        } catch (IllegalArgumentException e) { respond(exchange, 400, Map.of("error", "invalid_input", "message", e.getMessage())); }
        catch (Exception e) { respond(exchange, 500, Map.of("error", "operation_failed", "message", e.getMessage() == null ? "unknown" : e.getMessage())); }
    }
}
