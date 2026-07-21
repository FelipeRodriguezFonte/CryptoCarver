package com.cryptoforge;

import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import static org.junit.jupiter.api.Assertions.*;

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
        }
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
