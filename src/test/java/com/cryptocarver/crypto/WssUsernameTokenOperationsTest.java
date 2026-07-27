package com.cryptocarver.crypto;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class WssUsernameTokenOperationsTest {

    private static final char[] PASSWORD = "laboratory-password".toCharArray();
    private static String soap;

    @BeforeAll
    static void loadSoap() throws Exception {
        soap = Files.readString(Paths.get("src/test/resources/soap_test.xml"));
    }

    @Test
    void passwordDigestRoundTrip() throws Exception {
        String secured = WssUsernameTokenOperations.addUsernameToken(
                soap, "alice", PASSWORD, WssUsernameTokenOperations.PasswordType.PASSWORD_DIGEST);

        WssUsernameTokenOperations.VerificationResult result =
                WssUsernameTokenOperations.verifyUsernameToken(secured, "alice", PASSWORD, 300);

        assertEquals(WssUsernameTokenOperations.VerificationResult.Status.VALID, result.status());
        assertTrue(secured.contains("#PasswordDigest"));
        assertTrue(secured.contains("wsse:Nonce"));
        assertTrue(secured.contains("wsu:Created"));
        assertFalse(secured.contains(new String(PASSWORD)));
    }

    @Test
    void passwordTextRoundTripAndWrongPassword() throws Exception {
        String secured = WssUsernameTokenOperations.addUsernameToken(
                soap, "alice", PASSWORD, WssUsernameTokenOperations.PasswordType.PASSWORD_TEXT);

        assertEquals(WssUsernameTokenOperations.VerificationResult.Status.VALID,
                WssUsernameTokenOperations.verifyUsernameToken(secured, "alice", PASSWORD, 300).status());
        assertEquals(WssUsernameTokenOperations.VerificationResult.Status.INVALID,
                WssUsernameTokenOperations.verifyUsernameToken(
                        secured, "alice", "wrong".toCharArray(), 300).status());
        assertTrue(secured.contains(new String(PASSWORD)));
    }

    @Test
    void wrongUsernameAndTamperedNonceAreRejected() throws Exception {
        String secured = WssUsernameTokenOperations.addUsernameToken(
                soap, "alice", PASSWORD, WssUsernameTokenOperations.PasswordType.PASSWORD_DIGEST);

        assertEquals(WssUsernameTokenOperations.VerificationResult.Status.INVALID,
                WssUsernameTokenOperations.verifyUsernameToken(secured, "bob", PASSWORD, 300).status());
        String tampered = secured.replaceFirst("(<wsse:Nonce[^>]*>)[^<]+", "$1QUJDREVGR0hJSktMTU5PUA==");
        assertEquals(WssUsernameTokenOperations.VerificationResult.Status.INVALID,
                WssUsernameTokenOperations.verifyUsernameToken(tampered, "alice", PASSWORD, 300).status());
    }

    @Test
    void expiredAndFutureTokensAreRejected() throws Exception {
        String secured = WssUsernameTokenOperations.addUsernameToken(
                soap, "alice", PASSWORD, WssUsernameTokenOperations.PasswordType.PASSWORD_TEXT);
        String expired = secured.replaceFirst("(<wsu:Created>)[^<]+",
                "$1" + Instant.now().minusSeconds(600));
        String future = secured.replaceFirst("(<wsu:Created>)[^<]+",
                "$1" + Instant.now().plusSeconds(600));

        assertEquals(WssUsernameTokenOperations.VerificationResult.Status.INVALID,
                WssUsernameTokenOperations.verifyUsernameToken(expired, "alice", PASSWORD, 300).status());
        assertEquals(WssUsernameTokenOperations.VerificationResult.Status.INVALID,
                WssUsernameTokenOperations.verifyUsernameToken(future, "alice", PASSWORD, 300).status());
    }

    @Test
    void duplicateTokenAndInvalidAgeFailFast() throws Exception {
        String secured = WssUsernameTokenOperations.addUsernameToken(
                soap, "alice", PASSWORD, WssUsernameTokenOperations.PasswordType.PASSWORD_DIGEST);
        assertThrows(IllegalArgumentException.class, () -> WssUsernameTokenOperations.addUsernameToken(
                secured, "bob", PASSWORD, WssUsernameTokenOperations.PasswordType.PASSWORD_TEXT));
        assertEquals(WssUsernameTokenOperations.VerificationResult.Status.ERROR,
                WssUsernameTokenOperations.verifyUsernameToken(secured, "alice", PASSWORD, 0).status());
    }

    @Test
    void xxeIsRejected() {
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]>"
                + soap.substring(soap.indexOf("<soapenv:Envelope"));
        assertThrows(Exception.class, () -> WssUsernameTokenOperations.addUsernameToken(
                xxe, "alice", PASSWORD, WssUsernameTokenOperations.PasswordType.PASSWORD_DIGEST));
    }
}
