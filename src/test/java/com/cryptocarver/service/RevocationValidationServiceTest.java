package com.cryptocarver.service;

import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;
import java.net.InetSocketAddress;
import com.sun.net.httpserver.HttpServer;

import static org.junit.jupiter.api.Assertions.*;

class RevocationValidationServiceTest {
    @Test void offlineDoesNotConfigureNetworkSources() {
        CommonCertificateVerifier verifier = new CommonCertificateVerifier();
        RevocationValidationService.configure(verifier, RevocationValidationService.Configuration.offline(List.of()));
        assertNull(verifier.getOcspSource());
        assertNull(verifier.getCrlSource());
    }

    @Test void onlineConfiguresBothSources() {
        CommonCertificateVerifier verifier = new CommonCertificateVerifier();
        RevocationValidationService.configure(verifier, new RevocationValidationService.Configuration(true, List.of()));
        assertNotNull(verifier.getOcspSource());
        assertNotNull(verifier.getCrlSource());
    }

    @Test void onlyWebSchemesAreAllowed() {
        assertDoesNotThrow(() -> RevocationValidationService.validateWebUri("http://127.0.0.1:8080/crl"));
        assertDoesNotThrow(() -> RevocationValidationService.validateWebUri("https://ocsp.example.test"));
        assertThrows(IllegalArgumentException.class, () -> RevocationValidationService.validateWebUri("file:///tmp/crl"));
        assertThrows(IllegalArgumentException.class, () -> RevocationValidationService.validateWebUri("data:text/plain,crl"));
        assertThrows(IllegalArgumentException.class, () -> RevocationValidationService.validateWebUri("jar:http://example.test/x"));
    }

    @Test void failuresAreNeverGood() {
        assertNotEquals(RevocationValidationService.Status.GOOD,
                RevocationValidationService.Result.indeterminate("timeout password=secret").status());
        assertTrue(RevocationValidationService.Result.indeterminate("timeout password=secret").errors().get(0).contains("[redacted]"));
    }

    @Test void reportNeedsExplicitRevocationStatusForGood() {
        var unknown = RevocationValidationService.classifyDssReport(true, false,
                "<SignatureStatus>VALID</SignatureStatus>", List.of("http://127.0.0.1/x"), List.of());
        assertEquals(RevocationValidationService.Status.UNKNOWN, unknown.status());

        var good = RevocationValidationService.classifyDssReport(true, false,
                "<RevocationStatus>GOOD</RevocationStatus>", List.of("http://127.0.0.1/x"), List.of());
        assertEquals(RevocationValidationService.Status.GOOD, good.status());
    }

    @Test void boundedLoaderUsesDeterministicLocalHttpFixture() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ocsp", exchange -> {
            byte[] body = "fixture".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            List<String> endpoints = new ArrayList<>();
            var loader = RevocationValidationService.safeLoader(endpoints::add);
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/ocsp";
            assertArrayEquals("fixture".getBytes(java.nio.charset.StandardCharsets.US_ASCII), loader.get(url));
            assertEquals(List.of(url), endpoints);
        } finally {
            server.stop(0);
        }
    }
}
