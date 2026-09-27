package com.cryptocarver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class LocalApiServerTest {

    @Test
    void testCorsRejection() throws Exception {
        try (LocalApiServer server = LocalApiServer.start(0)) {
            HttpClient client = HttpClient.newHttpClient();

            String[] maliciousOrigins = {
                "https://malicious.com",
                "http://localhost.evil.com",
                "http://127.0.0.1.evil",
                "null",
                "http://localhost/path",
                "http://localhost?query=1"
            };

            for (String origin : maliciousOrigins) {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + server.port() + "/health"))
                        .header("Origin", origin)
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                assertEquals(403, response.statusCode(), "Origin should be rejected: " + origin);
                assertTrue(response.body().contains("cors_denied"));
                assertTrue(response.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
            }
        }
    }

    @Test
    void testCorsAllowed() throws Exception {
        try (LocalApiServer server = LocalApiServer.start(0)) {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + server.port() + "/health"))
                    .header("Origin", "http://127.0.0.1:8080")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertEquals("http://127.0.0.1:8080", response.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
        }
    }

    @Test
    void testRateLimitAndPurge() throws Exception {
        try (LocalApiServer server = LocalApiServer.start(0)) {
            HttpClient client = HttpClient.newHttpClient();
            String uri = "http://127.0.0.1:" + server.port() + "/health";

            // First request should succeed
            HttpResponse<String> r1 = client.send(HttpRequest.newBuilder(URI.create(uri)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, r1.statusCode());

            // Immediate second request should be rate limited
            HttpResponse<String> r2 = client.send(HttpRequest.newBuilder(URI.create(uri)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(429, r2.statusCode());
            assertTrue(r2.body().contains("rate_limit"));

            // Wait > 50ms and request should succeed again
            Thread.sleep(60);
            HttpResponse<String> r3 = client.send(HttpRequest.newBuilder(URI.create(uri)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, r3.statusCode());
        }
    }

    @Test
    void testOpenApiSchemaRestricted() throws Exception {
        try (LocalApiServer server = LocalApiServer.start(0)) {
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + server.port() + "/openapi.json"))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            String body = response.body();
            assertTrue(body.contains("/health"));
            assertTrue(body.contains("/v1/sha256"));
            assertFalse(body.contains("/v1/hmac")); // Only allowed paths
            assertTrue(body.contains("/v1/transform/{operation}"));
            assertTrue(body.contains("/v1/operations"));
            assertTrue(body.contains("sha-1"));
            assertTrue(body.contains("decimal-to-packed-bcd-hex"));
        }
    }

    @Test
    void genericCatalogApiListsOperationsAndTransformsAcrossFamilies() throws Exception {
        try (LocalApiServer server = LocalApiServer.start(0)) {
            HttpClient client = HttpClient.newHttpClient();
            assertEquals("127.0.0.1", server.bindAddress());
            HttpResponse<String> list = client.send(HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + server.port() + "/v1/operations")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, list.statusCode());
            assertTrue(list.body().contains("sha-256"));
            assertTrue(list.body().contains("track-2-to-analyze-json"));
            assertTrue(list.body().contains("CRC-32C"));

            assertTransform(client, server.port(), "sha-256", "abc", "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
            assertTransform(client, server.port(), "utf8-to-hex", "Hello", "48656c6c6f");
            assertTransform(client, server.port(), "pan-to-validate-luhn-13-19-digits", "4111111111111111", "true");
            assertTransform(client, server.port(), "apdu-status-to-inspect", "9000", "SUCCESS");
            assertTransform(client, server.port(), "emv-tlv-to-json", "9F0206000000000100", "9F02");
            assertTransform(client, server.port(), "asn-1-to-inspect", "3003020101", "INTEGER");

            HttpResponse<String> unknown = post(client, server.port(), "not-a-real-operation", "ok");
            assertEquals(404, unknown.statusCode());
            assertTrue(unknown.body().contains("unknown_operation"));
            assertEquals(404, post(client, server.port(), "hmac-sha256", "ok").statusCode());
            HttpResponse<String> invalid = post(client, server.port(), "base32-to-utf8", "!");
            assertEquals(400, invalid.statusCode());
            assertTrue(invalid.body().contains("Invalid Base32"));

            String hugePayload = "{\"input\":\"" + "a".repeat(1_100_000) + "\"}";
            HttpResponse<String> tooLarge = client.send(HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + server.port() + "/v1/transform/sha-1"))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(hugePayload)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(413, tooLarge.statusCode());
        }
    }

    private static void assertTransform(HttpClient client, int port, String operation, String input, String expected) throws Exception {
        HttpResponse<String> response = post(client, port, operation, input);
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains(expected), response.body());
    }

    private static HttpResponse<String> post(HttpClient client, int port, String operation, String input) throws Exception {
        String requestBody = new com.google.gson.Gson().toJson(java.util.Map.of("input", input));
        return client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/v1/transform/" + operation))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody)).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void testPayloadTooLarge() throws Exception {
        try (LocalApiServer server = LocalApiServer.start(0)) {
            HttpClient client = HttpClient.newHttpClient();
            String hugePayload = "{\"input\":\"" + "a".repeat(1_100_000) + "\"}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + server.port() + "/v1/sha256"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(hugePayload))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(413, response.statusCode());
        }
    }
}
