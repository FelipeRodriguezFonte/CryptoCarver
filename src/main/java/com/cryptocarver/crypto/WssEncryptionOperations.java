package com.cryptocarver.crypto;

import org.apache.wss4j.common.WSS4JConstants;
import org.apache.wss4j.common.crypto.Merlin;
import org.apache.wss4j.common.ext.WSPasswordCallback;
import org.apache.wss4j.dom.WSConstants;
import org.apache.wss4j.dom.engine.WSSecurityEngine;
import org.apache.wss4j.dom.handler.WSHandlerResult;
import org.apache.wss4j.dom.message.WSSecEncrypt;
import org.apache.wss4j.dom.message.WSSecHeader;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.security.auth.callback.Callback;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Arrays;

/** WS-Security XML Encryption operations for the SOAP Body content. */
public final class WssEncryptionOperations {

    static {
        org.apache.xml.security.Init.init();
    }

    private static final String SOAP_11_NS = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String SOAP_12_NS = "http://www.w3.org/2003/05/soap-envelope";
    private static final String XMLENC_NS = "http://www.w3.org/2001/04/xmlenc#";
    private static final String XMLENC11_NS = "http://www.w3.org/2009/xmlenc11#";
    private static final String WSSE_NS = "http://docs.oasis-open.org/wss/2004/01/"
            + "oasis-200401-wss-wssecurity-secext-1.0.xsd";

    private WssEncryptionOperations() {
    }

    public enum DataEncryptionAlgorithm {
        AES_128_GCM("AES-128-GCM", WSS4JConstants.AES_128_GCM, 128, true),
        AES_256_GCM("AES-256-GCM", WSS4JConstants.AES_256_GCM, 256, true),
        AES_128_CBC("AES-128-CBC", WSS4JConstants.AES_128, 128, false),
        AES_256_CBC("AES-256-CBC", WSS4JConstants.AES_256, 256, false);

        private final String displayName;
        private final String uri;
        private final int keyBits;
        private final boolean authenticated;

        DataEncryptionAlgorithm(String displayName, String uri, int keyBits, boolean authenticated) {
            this.displayName = displayName;
            this.uri = uri;
            this.keyBits = keyBits;
            this.authenticated = authenticated;
        }

        public String displayName() { return displayName; }
        public boolean authenticated() { return authenticated; }

        public static DataEncryptionAlgorithm fromDisplayName(String value) {
            for (DataEncryptionAlgorithm algorithm : values()) {
                if (algorithm.displayName.equals(value)) return algorithm;
            }
            throw new IllegalArgumentException("Unsupported WSS data encryption algorithm: " + value);
        }
    }

    public enum KeyTransportAlgorithm {
        RSA_OAEP_SHA256("RSA-OAEP SHA-256", WSS4JConstants.KEYTRANSPORT_RSAOAEP_XENC11,
                WSS4JConstants.SHA256, WSS4JConstants.MGF_SHA256),
        RSA_OAEP_SHA1("RSA-OAEP SHA-1 (legacy profile)", WSS4JConstants.KEYTRANSPORT_RSAOAEP,
                null, null);

        private final String displayName;
        private final String uri;
        private final String digestUri;
        private final String mgfUri;

        KeyTransportAlgorithm(String displayName, String uri, String digestUri, String mgfUri) {
            this.displayName = displayName;
            this.uri = uri;
            this.digestUri = digestUri;
            this.mgfUri = mgfUri;
        }

        public String displayName() { return displayName; }

        public static KeyTransportAlgorithm fromDisplayName(String value) {
            for (KeyTransportAlgorithm algorithm : values()) {
                if (algorithm.displayName.equals(value)) return algorithm;
            }
            throw new IllegalArgumentException("Unsupported WSS key transport algorithm: " + value);
        }
    }

    public record OperationResult(Status status, String message, String xml, String technicalDetails) {
        public enum Status { SUCCESS, ERROR }
    }

    public static OperationResult encryptSoapBody(String xml, X509Certificate recipientCertificate,
                                                   DataEncryptionAlgorithm dataAlgorithm,
                                                   KeyTransportAlgorithm keyTransportAlgorithm) {
        try {
            if (recipientCertificate == null) throw new IllegalArgumentException("Recipient certificate is required.");
            if (!"RSA".equalsIgnoreCase(recipientCertificate.getPublicKey().getAlgorithm())) {
                throw new IllegalArgumentException("RSA-OAEP requires an RSA recipient certificate.");
            }
            if (dataAlgorithm == null || keyTransportAlgorithm == null) {
                throw new IllegalArgumentException("Data and key transport algorithms are required.");
            }
            Document document = parseSecurely(xml);
            String soapVersion = validateSoap(document);
            WSSecHeader securityHeader = new WSSecHeader(document);
            securityHeader.insertSecurityHeader();

            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(dataAlgorithm.keyBits);
            SecretKey sessionKey = generator.generateKey();
            WSSecEncrypt encryptor = new WSSecEncrypt(securityHeader);
            encryptor.setUseThisCert(recipientCertificate);
            encryptor.setKeyIdentifierType(WSConstants.BST_DIRECT_REFERENCE);
            encryptor.setIncludeEncryptionToken(true);
            encryptor.setSymmetricEncAlgorithm(dataAlgorithm.uri);
            encryptor.setKeyEncAlgo(keyTransportAlgorithm.uri);
            if (keyTransportAlgorithm.digestUri != null) {
                encryptor.setDigestAlgorithm(keyTransportAlgorithm.digestUri);
                encryptor.setMGFAlgorithm(keyTransportAlgorithm.mgfUri);
            }

            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            trustStore.setCertificateEntry("recipient", recipientCertificate);
            Merlin crypto = new Merlin();
            crypto.setTrustStore(trustStore);
            Document encrypted = encryptor.build(crypto, sessionKey);
            byte[] sessionBytes = sessionKey.getEncoded();
            if (sessionBytes != null) Arrays.fill(sessionBytes, (byte) 0);

            String output = toXml(encrypted);
            String details = "SOAP version: " + soapVersion + "\n"
                    + "Encrypted target: SOAP Body content\n"
                    + "Data algorithm: " + dataAlgorithm.displayName + "\n"
                    + "Authenticated encryption: " + (dataAlgorithm.authenticated ? "yes" : "no") + "\n"
                    + "Key transport: " + keyTransportAlgorithm.displayName + "\n"
                    + "Recipient: " + recipientCertificate.getSubjectX500Principal() + "\n";
            return new OperationResult(OperationResult.Status.SUCCESS,
                    "SOAP Body encrypted successfully.", output, details);
        } catch (Exception e) {
            return error("SOAP encryption failed: " + diagnosticMessage(e));
        }
    }

    public static OperationResult decryptSoapBody(String xml, KeyStore keyStore, char[] keyPassword) {
        try {
            if (keyStore == null) throw new IllegalArgumentException("Decryption KeyStore is required.");
            if (keyPassword == null) throw new IllegalArgumentException("Private key password is required.");
            Document document = parseSecurely(xml);
            String soapVersion = validateSoap(document);
            String dataAlgorithm = encryptionAlgorithm(document, "EncryptedData");
            String keyAlgorithm = encryptionAlgorithm(document, "EncryptedKey");
            if (dataAlgorithm == null || keyAlgorithm == null) {
                throw new IllegalArgumentException("SOAP message does not contain EncryptedData and EncryptedKey.");
            }
            if (!isSupportedDataAlgorithm(dataAlgorithm)) {
                throw new IllegalArgumentException("Unsupported WSS data encryption algorithm: " + dataAlgorithm);
            }
            if (!isSupportedKeyTransport(keyAlgorithm)) {
                throw new IllegalArgumentException("Unsupported WSS key transport algorithm: " + keyAlgorithm);
            }
            validateEncryptedStructure(document);

            Merlin crypto = new Merlin();
            crypto.setKeyStore(keyStore);
            WSHandlerResult handlerResult = new WSSecurityEngine().processSecurityHeader(
                    document, null, callbacks -> provideKeyPassword(callbacks, keyPassword), crypto);
            if (handlerResult == null
                    || handlerResult.getActionResults().getOrDefault(WSConstants.ENCR, java.util.List.of()).isEmpty()) {
                throw new IllegalArgumentException("WSS engine did not report a successful decryption action.");
            }

            String details = "SOAP version: " + soapVersion + "\n"
                    + "Decrypted target: SOAP Body content\n"
                    + "Data algorithm URI: " + dataAlgorithm + "\n"
                    + "Key transport URI: " + keyAlgorithm + "\n";
            return new OperationResult(OperationResult.Status.SUCCESS,
                    "SOAP Body decrypted successfully.", toXml(document), details);
        } catch (Exception e) {
            return error("SOAP decryption failed: " + diagnosticMessage(e));
        }
    }

    private static void provideKeyPassword(Callback[] callbacks, char[] password) {
        String value = new String(password);
        for (Callback callback : callbacks) {
            if (callback instanceof WSPasswordCallback passwordCallback) {
                passwordCallback.setPassword(value);
            }
        }
    }

    private static String encryptionAlgorithm(Document document, String encryptedElementName) {
        NodeList encryptedElements = document.getElementsByTagNameNS(XMLENC_NS, encryptedElementName);
        if (encryptedElements.getLength() == 0) encryptedElements = document.getElementsByTagNameNS(XMLENC11_NS, encryptedElementName);
        if (encryptedElements.getLength() == 0) return null;
        if (encryptedElements.getLength() != 1) {
            throw new IllegalArgumentException("Expected exactly one " + encryptedElementName + " element.");
        }
        Element encrypted = (Element) encryptedElements.item(0);
        for (org.w3c.dom.Node child = encrypted.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && "EncryptionMethod".equals(element.getLocalName())) {
                return element.getAttribute("Algorithm");
            }
        }
        return null;
    }

    private static boolean isSupportedDataAlgorithm(String uri) {
        for (DataEncryptionAlgorithm algorithm : DataEncryptionAlgorithm.values()) {
            if (algorithm.uri.equals(uri)) return true;
        }
        return false;
    }

    private static boolean isSupportedKeyTransport(String uri) {
        for (KeyTransportAlgorithm algorithm : KeyTransportAlgorithm.values()) {
            if (algorithm.uri.equals(uri)) return true;
        }
        return false;
    }

    private static void validateEncryptedStructure(Document document) {
        Element envelope = document.getDocumentElement();
        Element body = directChild(envelope, envelope.getNamespaceURI(), "Body");
        Element header = directChild(envelope, envelope.getNamespaceURI(), "Header");
        if (body == null || header == null) {
            throw new IllegalArgumentException("SOAP Envelope must contain direct Header and Body elements.");
        }

        Element encryptedData = exactlyOneEncryptedElement(document, "EncryptedData");
        if (encryptedData.getParentNode() != body) {
            throw new IllegalArgumentException("EncryptedData must be a direct child of the SOAP Body.");
        }

        Element encryptedKey = exactlyOneEncryptedElement(document, "EncryptedKey");
        if (!(encryptedKey.getParentNode() instanceof Element security)
                || !"Security".equals(security.getLocalName())
                || !WSSE_NS.equals(security.getNamespaceURI())
                || security.getParentNode() != header) {
            throw new IllegalArgumentException(
                    "EncryptedKey must be inside a direct wsse:Security child of the SOAP Header.");
        }
    }

    private static Element exactlyOneEncryptedElement(Document document, String localName) {
        NodeList elements = document.getElementsByTagNameNS(XMLENC_NS, localName);
        if (elements.getLength() == 0) {
            elements = document.getElementsByTagNameNS(XMLENC11_NS, localName);
        }
        if (elements.getLength() != 1) {
            throw new IllegalArgumentException("Expected exactly one " + localName + " element.");
        }
        return (Element) elements.item(0);
    }

    private static Element directChild(Element parent, String namespace, String localName) {
        for (org.w3c.dom.Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element
                    && localName.equals(element.getLocalName())
                    && namespace.equals(element.getNamespaceURI())) {
                return element;
            }
        }
        return null;
    }

    private static Document parseSecurely(String xml) throws Exception {
        if (xml == null || xml.isBlank()) throw new IllegalArgumentException("SOAP XML is required.");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static String validateSoap(Document document) {
        Element envelope = document.getDocumentElement();
        if (!"Envelope".equals(envelope.getLocalName())) throw new IllegalArgumentException("Root element is not SOAP Envelope.");
        if (SOAP_11_NS.equals(envelope.getNamespaceURI())) return "1.1";
        if (SOAP_12_NS.equals(envelope.getNamespaceURI())) return "1.2";
        throw new IllegalArgumentException("Unsupported SOAP Envelope namespace: " + envelope.getNamespaceURI());
    }

    private static String toXml(Document document) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        StringWriter output = new StringWriter();
        factory.newTransformer().transform(new DOMSource(document), new StreamResult(output));
        return output.toString();
    }

    private static OperationResult error(String message) {
        return new OperationResult(OperationResult.Status.ERROR, message, null, "");
    }

    private static String diagnosticMessage(Throwable error) {
        StringBuilder message = new StringBuilder();
        for (Throwable current = error; current != null; current = current.getCause()) {
            String value = current.getMessage();
            if (value != null && !value.isBlank() && message.indexOf(value) < 0) {
                if (!message.isEmpty()) message.append(": ");
                message.append(value);
            }
        }
        return message.isEmpty() ? error.getClass().getSimpleName() : message.toString();
    }
}
