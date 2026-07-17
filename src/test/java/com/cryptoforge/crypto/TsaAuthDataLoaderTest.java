package com.cryptoforge.crypto;

import com.cryptoforge.model.TsaAuthCredentials;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

class TsaAuthDataLoaderTest {

    private static HttpServer server;
    private static int port;
    private static String lastAuthHeader = null;

    @BeforeAll
    static void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/auth", exchange -> {
            lastAuthHeader = exchange.getRequestHeaders().getFirst("Authorization");
            byte[] resp = "OK".getBytes();
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.getResponseBody().close();
        });
        server.createContext("/toolarge", exchange -> {
            byte[] resp = new byte[100];
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.getResponseBody().close();
        });
        server.createContext("/timeout", exchange -> {
            try { Thread.sleep(2000); } catch (Exception ignored) {}
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.createContext("/error", exchange -> {
            byte[] resp = "Internal Server Error Data".getBytes();
            exchange.sendResponseHeaders(500, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.getResponseBody().close();
        });
        server.setExecutor(null);
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterAll
    static void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void testBasicAuthSendsCorrectHeader() {
        TsaAuthCredentials auth = new TsaAuthCredentials(TsaAuthCredentials.AuthType.BASIC, "user", "pass");
        TsaAuthDataLoader loader = new TsaAuthDataLoader(auth);
        lastAuthHeader = null;
        loader.post("http://127.0.0.1:" + port + "/auth", new byte[0]);
        assertEquals("Basic dXNlcjpwYXNz", lastAuthHeader);
    }

    @Test
    void testBearerAuthSendsCorrectHeader() {
        TsaAuthCredentials auth = new TsaAuthCredentials(TsaAuthCredentials.AuthType.BEARER, null, "mytoken123");
        TsaAuthDataLoader loader = new TsaAuthDataLoader(auth);
        lastAuthHeader = null;
        loader.post("http://127.0.0.1:" + port + "/auth", new byte[0]);
        assertEquals("Bearer mytoken123", lastAuthHeader);
    }

    @Test
    void testGetThrowsException() {
        TsaAuthDataLoader loader = new TsaAuthDataLoader(null);
        assertThrows(UnsupportedOperationException.class, () -> loader.get("http://example.com"));
        assertThrows(UnsupportedOperationException.class, () -> loader.get(java.util.List.of("http://example.com")));
    }

    @Test
    void testSizeLimitEnforced() {
        TsaAuthDataLoader loader = new TsaAuthDataLoader(null, 5000, 5000, 50); // limit 50 bytes
        RuntimeException e = assertThrows(RuntimeException.class, () ->
            loader.post("http://127.0.0.1:" + port + "/toolarge", new byte[0])
        );
        assertTrue(e.getMessage().contains("exceeded maximum size limit of 50"));
    }

    @Test
    void testToStringRedactsPassword() {
        TsaAuthCredentials auth = new TsaAuthCredentials(TsaAuthCredentials.AuthType.BASIC, "user", "secretpass");
        String str = auth.toString();
        assertTrue(str.contains("user"));
        assertFalse(str.contains("secretpass"));
        assertTrue(str.contains("***REDACTED***"));
    }

    @Test
    void testErrorMessageRedaction() {
        TsaAuthCredentials auth = new TsaAuthCredentials(TsaAuthCredentials.AuthType.BEARER, null, "supersecrettoken");

        // 1. Timeout
        TsaAuthDataLoader loader1 = new TsaAuthDataLoader(auth, 1000, 1000, 100);
        RuntimeException e1 = assertThrows(RuntimeException.class, () ->
            loader1.post("http://127.0.0.1:" + port + "/timeout", new byte[0])
        );
        String msg1 = e1.getMessage();
        assertFalse(msg1.contains("supersecrettoken"));
        assertTrue(msg1.contains("TSA request timed out") || msg1.contains("Transport error"));

        // 2. HTTP Error
        TsaAuthDataLoader loader2 = new TsaAuthDataLoader(auth, 5000, 5000, 100);
        RuntimeException e2 = assertThrows(RuntimeException.class, () ->
            loader2.post("http://127.0.0.1:" + port + "/error", new byte[0])
        );
        String msg2 = e2.getMessage();
        assertFalse(msg2.contains("supersecrettoken"));
        assertFalse(msg2.contains("Internal Server Error Data"));
        assertTrue(msg2.contains("Unexpected error") || msg2.contains("TSA returned HTTP 500"));
    }

    @Test
    void testAppSettingsDoesNotPersistCredentials() throws Exception {
        // Use reflection to instantiate AppSettings with a temporary file to avoid altering user configs
        java.lang.reflect.Constructor<com.cryptoforge.model.AppSettings> constructor = com.cryptoforge.model.AppSettings.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        com.cryptoforge.model.AppSettings settings = constructor.newInstance();

        java.nio.file.Path tempSettingsFile = java.nio.file.Files.createTempFile("cryptoforge_test_settings", ".json");
        java.lang.reflect.Field fileField = com.cryptoforge.model.AppSettings.class.getDeclaredField("file");
        fileField.setAccessible(true);
        fileField.set(settings, tempSettingsFile);

        settings.saveTsaProfile("TestTSA", "http://example.com");

        java.util.List<com.cryptoforge.model.AppSettings.TsaProfile> profiles = settings.getTsaProfiles();
        assertFalse(profiles.isEmpty());

        // Read the actual persisted JSON
        String json = java.nio.file.Files.readString(tempSettingsFile);
        assertFalse(json.toLowerCase().contains("password"));
        assertFalse(json.toLowerCase().contains("token"));
        assertFalse(json.toLowerCase().contains("credential"));
        assertTrue(json.contains("TestTSA"));
        assertTrue(json.contains("http://example.com"));

        java.nio.file.Files.deleteIfExists(tempSettingsFile);
    }
}
