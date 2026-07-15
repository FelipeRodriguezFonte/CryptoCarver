package com.cryptoforge;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class LocalApiServerTest {
    @Test void servesOnlyTheSafeLoopbackSha256Endpoint() throws Exception {
        try (LocalApiServer server = LocalApiServer.start(0)) {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/v1/sha256"))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{\"input\":\"abc\"}")).build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"));
        }
    }
}
