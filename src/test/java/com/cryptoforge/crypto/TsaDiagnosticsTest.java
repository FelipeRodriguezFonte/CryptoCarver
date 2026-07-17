package com.cryptoforge.crypto;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsaDiagnosticsTest {

    private static HttpServer server;
    private static int port;

    @BeforeAll
    static void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);

        server.createContext("/timeout", exchange -> {
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
        });

        server.createContext("/toolarge", exchange -> {
            byte[] bigResponse = new byte[1024]; // 1KB
            exchange.sendResponseHeaders(200, bigResponse.length);
            exchange.getResponseBody().write(bigResponse);
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
    void rejectsNonHttpTsaUrlsBeforeNetworkAccess() {
        assertThrows(IllegalArgumentException.class, () -> TsaDiagnostics.test("file:///tmp/tsa"));
    }

    @Test
    void timeoutThrowsException() {
        Exception exception = assertThrows(Exception.class, () ->
            TsaDiagnostics.timestamp("http://127.0.0.1:" + port + "/timeout", "data".getBytes(), "SHA-256", 50, 50, 1024, null)
        );
        assertTrue(exception instanceof java.net.SocketTimeoutException);
    }

    @Test
    void sizeLimitExceededThrowsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            TsaDiagnostics.timestamp("http://127.0.0.1:" + port + "/toolarge", "data".getBytes(), "SHA-256", 1000, 1000, 500, null)
        );
        assertTrue(exception.getMessage().contains("exceeded maximum size limit of 500 bytes"));
    }
}
