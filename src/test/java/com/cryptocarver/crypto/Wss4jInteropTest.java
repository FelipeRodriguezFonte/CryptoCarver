package com.cryptocarver.crypto;

import org.apache.wss4j.common.crypto.Merlin;
import org.apache.wss4j.common.ext.WSPasswordCallback;
import org.apache.wss4j.dom.WSConstants;
import org.apache.wss4j.dom.engine.WSSecurityEngine;
import org.apache.wss4j.dom.handler.WSHandlerResult;
import org.apache.wss4j.dom.message.WSSecHeader;
import org.apache.wss4j.dom.message.WSSecSignature;
import org.apache.wss4j.dom.message.WSSecUsernameToken;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.XMLConstants;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.*;

/** Cross-engine proof that CryptoCarver output is accepted by Apache WSS4J. */
class Wss4jInteropTest {

    private static final char[] TEST_PASS = "storepass".toCharArray();
    private static KeyStore keyStore;
    private static String keyAlias;
    private static String soap11;

    @BeforeAll
    static void loadFixture() throws Exception {
        keyStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream input = new FileInputStream("src/test/resources/testks.p12")) {
            keyStore.load(input, TEST_PASS);
        }
        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (keyStore.isKeyEntry(alias)) {
                keyAlias = alias;
                break;
            }
        }
        assertNotNull(keyAlias);
        soap11 = Files.readString(Paths.get("src/test/resources/soap_test.xml"));
    }

    @Test
    void wss4jAcceptsSoap11BodyAndSignedTimestamp() throws Exception {
        String signed = WssSecurityOperations.signSoapBody(
                soap11, keyStore, keyAlias, TEST_PASS,
                WssSecurityOperations.WssSignatureAlgorithm.RSA_SHA256,
                WssSecurityOperations.WssTimestampOptions.signed(5));

        WSHandlerResult result = verifyWithWss4j(signed);

        assertNotNull(result);
        assertFalse(result.getActionResults().getOrDefault(WSConstants.SIGN, java.util.List.of()).isEmpty());
        assertFalse(result.getActionResults().getOrDefault(WSConstants.TS, java.util.List.of()).isEmpty());
    }

    @Test
    void wss4jAcceptsSoap12BodySignature() throws Exception {
        String soap12 = soap11.replace(
                "http://schemas.xmlsoap.org/soap/envelope/",
                "http://www.w3.org/2003/05/soap-envelope");
        String signed = WssSecurityOperations.signSoapBody(
                soap12, keyStore, keyAlias, TEST_PASS,
                WssSecurityOperations.WssSignatureAlgorithm.RSA_SHA512,
                WssSecurityOperations.WssTimestampOptions.disabled());

        WSHandlerResult result = verifyWithWss4j(signed);

        assertNotNull(result);
        assertFalse(result.getActionResults().getOrDefault(WSConstants.SIGN, java.util.List.of()).isEmpty());
    }

    @Test
    void wss4jRejectsTamperedCryptoCarverMessage() throws Exception {
        String signed = WssSecurityOperations.signSoapBody(
                soap11, keyStore, keyAlias, TEST_PASS,
                WssSecurityOperations.WssSignatureAlgorithm.RSA_SHA256,
                WssSecurityOperations.WssTimestampOptions.signed(5));
        String tampered = signed.replace("SensitivePayload", "TamperedPayload");

        assertThrows(Exception.class, () -> verifyWithWss4j(tampered));
    }

    @Test
    void cryptoCarverAcceptsWss4jBodySignature() throws Exception {
        Document document = parseSecurely(soap11);
        WSSecHeader header = new WSSecHeader(document);
        header.insertSecurityHeader();
        WSSecSignature signer = new WSSecSignature(header);
        signer.setUserInfo(keyAlias, new String(TEST_PASS));
        signer.setKeyIdentifierType(WSConstants.BST_DIRECT_REFERENCE);
        signer.setSignatureAlgorithm(WSConstants.RSA_SHA256);
        signer.setDigestAlgo(WSConstants.SHA256);
        Merlin crypto = new Merlin();
        crypto.setKeyStore(keyStore);

        Document signedDocument = signer.build(crypto);
        WssSecurityOperations.WssVerificationResult result =
                WssSecurityOperations.verifySoapSignature(toXml(signedDocument),
                        (java.security.cert.X509Certificate) keyStore.getCertificate(keyAlias));

        assertEquals(WssSecurityOperations.WssVerificationResult.Status.VALID, result.getStatus(),
                result.getMessage());
    }

    @Test
    void wss4jAcceptsCryptoCarverPasswordDigest() throws Exception {
        String secured = WssUsernameTokenOperations.addUsernameToken(
                soap11, "alice", "secret".toCharArray(),
                WssUsernameTokenOperations.PasswordType.PASSWORD_DIGEST);
        CallbackHandler passwords = callbacks -> providePassword(callbacks, "alice", "secret");

        WSHandlerResult result = new WSSecurityEngine().processSecurityHeader(
                parseSecurely(secured), null, passwords, null);

        assertNotNull(result);
        assertFalse(result.getActionResults().getOrDefault(WSConstants.UT, java.util.List.of()).isEmpty());
    }

    @Test
    void cryptoCarverAcceptsWss4jPasswordDigest() throws Exception {
        Document document = parseSecurely(soap11);
        WSSecHeader header = new WSSecHeader(document);
        header.insertSecurityHeader();
        WSSecUsernameToken token = new WSSecUsernameToken(header);
        token.setUserInfo("alice", "secret");
        token.setPasswordType(WSConstants.PASSWORD_DIGEST);
        token.addNonce();
        token.addCreated();
        Document secured = token.build();

        WssUsernameTokenOperations.VerificationResult result =
                WssUsernameTokenOperations.verifyUsernameToken(
                        toXml(secured), "alice", "secret".toCharArray(), 300);

        assertEquals(WssUsernameTokenOperations.VerificationResult.Status.VALID,
                result.status(), result.message());
    }

    private static WSHandlerResult verifyWithWss4j(String xml) throws Exception {
        Merlin crypto = new Merlin();
        crypto.setKeyStore(keyStore);
        crypto.setTrustStore(keyStore);
        return new WSSecurityEngine().processSecurityHeader(parseSecurely(xml), null, null, crypto);
    }

    private static Document parseSecurely(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static String toXml(Document document) throws Exception {
        StringWriter output = new StringWriter();
        TransformerFactory.newInstance().newTransformer()
                .transform(new DOMSource(document), new StreamResult(output));
        return output.toString();
    }

    private static void providePassword(Callback[] callbacks, String username, String password) {
        for (Callback callback : callbacks) {
            if (callback instanceof WSPasswordCallback passwordCallback
                    && username.equals(passwordCallback.getIdentifier())) {
                passwordCallback.setPassword(password);
            }
        }
    }
}
