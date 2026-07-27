package com.cryptocarver.crypto;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

/** Standalone WS-Security UsernameToken laboratory operations. */
public final class WssUsernameTokenOperations {

    private static final String SOAP_11_NS = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String SOAP_12_NS = "http://www.w3.org/2003/05/soap-envelope";
    private static final String WSSE_NS =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd";
    private static final String WSU_NS =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd";
    private static final String USERNAME_PROFILE =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0";
    private static final String PASSWORD_TEXT_URI = USERNAME_PROFILE + "#PasswordText";
    private static final String PASSWORD_DIGEST_URI = USERNAME_PROFILE + "#PasswordDigest";
    private static final String BASE64_ENCODING =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary";
    private static final int NONCE_BYTES = 16;
    private static final long CLOCK_SKEW_SECONDS = 60;
    private static final SecureRandom RANDOM = new SecureRandom();

    private WssUsernameTokenOperations() {
    }

    public enum PasswordType {
        PASSWORD_TEXT("PasswordText", PASSWORD_TEXT_URI),
        PASSWORD_DIGEST("PasswordDigest", PASSWORD_DIGEST_URI);

        private final String displayName;
        private final String uri;

        PasswordType(String displayName, String uri) {
            this.displayName = displayName;
            this.uri = uri;
        }

        public String displayName() {
            return displayName;
        }

        public static PasswordType fromDisplayName(String value) {
            for (PasswordType type : values()) {
                if (type.displayName.equals(value)) return type;
            }
            throw new IllegalArgumentException("Unsupported UsernameToken password type: " + value);
        }

        private static PasswordType fromUri(String uri) {
            for (PasswordType type : values()) {
                if (type.uri.equals(uri)) return type;
            }
            throw new IllegalArgumentException("Unsupported UsernameToken Password/@Type: " + uri);
        }
    }

    public record VerificationResult(Status status, String message, String technicalDetails) {
        public enum Status { VALID, INVALID, ERROR }
    }

    public static String addUsernameToken(String xml, String username, char[] password,
                                          PasswordType passwordType) throws Exception {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Username is required.");
        if (password == null || password.length == 0) throw new IllegalArgumentException("Password is required.");
        if (passwordType == null) throw new IllegalArgumentException("Password type is required.");

        Document document = parseSecurely(xml);
        Element envelope = document.getDocumentElement();
        String soapNamespace = soapNamespace(envelope);
        Element body = uniqueDirectChild(envelope, soapNamespace, "Body");
        if (body == null) throw new IllegalArgumentException("No SOAP Body found in the document.");
        Element header = uniqueDirectChild(envelope, soapNamespace, "Header");
        if (header == null) {
            header = document.createElementNS(soapNamespace, qualifiedName(envelope, "Header"));
            envelope.insertBefore(header, body);
        }
        Element security = uniqueDirectChild(header, WSSE_NS, "Security");
        if (security == null) {
            security = document.createElementNS(WSSE_NS, "wsse:Security");
            security.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:wsse", WSSE_NS);
            header.appendChild(security);
        }
        if (uniqueDirectChild(security, WSSE_NS, "UsernameToken") != null) {
            throw new IllegalArgumentException("The wsse:Security header already contains a UsernameToken.");
        }

        String created = Instant.now().toString();
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        Element token = document.createElementNS(WSSE_NS, "wsse:UsernameToken");
        token.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:wsu", WSU_NS);
        token.setAttributeNS(WSU_NS, "wsu:Id", "UsernameToken-" + UUID.randomUUID());
        Element usernameElement = document.createElementNS(WSSE_NS, "wsse:Username");
        usernameElement.setTextContent(username);
        Element passwordElement = document.createElementNS(WSSE_NS, "wsse:Password");
        passwordElement.setAttribute("Type", passwordType.uri);
        if (passwordType == PasswordType.PASSWORD_DIGEST) {
            passwordElement.setTextContent(passwordDigest(nonce, created, password));
        } else {
            passwordElement.setTextContent(new String(password));
        }
        Element nonceElement = document.createElementNS(WSSE_NS, "wsse:Nonce");
        nonceElement.setAttribute("EncodingType", BASE64_ENCODING);
        nonceElement.setTextContent(Base64.getEncoder().encodeToString(nonce));
        Element createdElement = document.createElementNS(WSU_NS, "wsu:Created");
        createdElement.setTextContent(created);
        token.appendChild(usernameElement);
        token.appendChild(passwordElement);
        token.appendChild(nonceElement);
        token.appendChild(createdElement);
        security.appendChild(token);
        Arrays.fill(nonce, (byte) 0);
        return toXml(document);
    }

    public static VerificationResult verifyUsernameToken(String xml, String expectedUsername,
                                                          char[] expectedPassword, int maxAgeSeconds) {
        try {
            if (expectedUsername == null || expectedUsername.isBlank()) {
                throw new IllegalArgumentException("Expected username is required.");
            }
            if (expectedPassword == null || expectedPassword.length == 0) {
                throw new IllegalArgumentException("Expected password is required.");
            }
            if (maxAgeSeconds < 1 || maxAgeSeconds > 86_400) {
                throw new IllegalArgumentException("Maximum token age must be between 1 and 86400 seconds.");
            }
            Document document = parseSecurely(xml);
            Element envelope = document.getDocumentElement();
            String soapNamespace = soapNamespace(envelope);
            Element header = uniqueDirectChild(envelope, soapNamespace, "Header");
            Element security = header == null ? null : uniqueDirectChild(header, WSSE_NS, "Security");
            Element token = security == null ? null : uniqueDirectChild(security, WSSE_NS, "UsernameToken");
            if (token == null) return error("No direct wsse:UsernameToken found in the SOAP Security header.");

            Element usernameElement = requiredChild(token, WSSE_NS, "Username");
            Element passwordElement = requiredChild(token, WSSE_NS, "Password");
            String actualUsername = usernameElement.getTextContent();
            if (!MessageDigest.isEqual(actualUsername.getBytes(StandardCharsets.UTF_8),
                    expectedUsername.getBytes(StandardCharsets.UTF_8))) {
                return invalid("UsernameToken username does not match.", "");
            }
            PasswordType passwordType = PasswordType.fromUri(passwordElement.getAttribute("Type"));
            Element nonceElement = uniqueDirectChild(token, WSSE_NS, "Nonce");
            Element createdElement = uniqueDirectChild(token, WSU_NS, "Created");
            if ((nonceElement == null) != (createdElement == null)) {
                return error("UsernameToken must provide Nonce and Created together.");
            }
            Instant created = null;
            byte[] nonce = null;
            if (createdElement != null) {
                try {
                    created = Instant.parse(createdElement.getTextContent().trim());
                } catch (DateTimeParseException e) {
                    return error("UsernameToken Created is not a valid UTC instant.");
                }
                Instant now = Instant.now();
                if (created.isAfter(now.plusSeconds(CLOCK_SKEW_SECONDS))) {
                    return invalid("UsernameToken is not valid yet.", "Created: " + created + "\n");
                }
                if (created.isBefore(now.minusSeconds(maxAgeSeconds + CLOCK_SKEW_SECONDS))) {
                    return invalid("UsernameToken has expired.", "Created: " + created + "\n");
                }
                if (!BASE64_ENCODING.equals(nonceElement.getAttribute("EncodingType"))) {
                    return error("Unsupported UsernameToken Nonce encoding.");
                }
                try {
                    nonce = Base64.getDecoder().decode(nonceElement.getTextContent().trim());
                } catch (IllegalArgumentException e) {
                    return error("UsernameToken Nonce is not valid Base64.");
                }
                if (nonce.length < 8) return error("UsernameToken Nonce must contain at least 8 bytes.");
            }
            if (passwordType == PasswordType.PASSWORD_DIGEST && (nonce == null || created == null)) {
                return error("PasswordDigest requires Nonce and Created.");
            }

            byte[] expected;
            byte[] actual;
            if (passwordType == PasswordType.PASSWORD_DIGEST) {
                expected = passwordDigest(nonce, createdElement.getTextContent().trim(), expectedPassword)
                        .getBytes(StandardCharsets.US_ASCII);
                actual = passwordElement.getTextContent().trim().getBytes(StandardCharsets.US_ASCII);
            } else {
                expected = passwordBytes(expectedPassword);
                actual = passwordElement.getTextContent().getBytes(StandardCharsets.UTF_8);
            }
            boolean matches = MessageDigest.isEqual(expected, actual);
            Arrays.fill(expected, (byte) 0);
            Arrays.fill(actual, (byte) 0);
            if (nonce != null) Arrays.fill(nonce, (byte) 0);
            if (!matches) return invalid("UsernameToken password does not match.", "");

            String details = "SOAP version: " + (SOAP_12_NS.equals(soapNamespace) ? "1.2" : "1.1") + "\n"
                    + "Password type: " + passwordType.displayName + "\n"
                    + "Nonce: " + (nonceElement == null ? "not present" : "present") + "\n"
                    + "Created: " + (created == null ? "not present" : created) + "\n"
                    + "Cryptographic signature: not evaluated by UsernameToken verification\n";
            return new VerificationResult(VerificationResult.Status.VALID,
                    "UsernameToken credentials are valid.", details);
        } catch (Exception e) {
            return new VerificationResult(VerificationResult.Status.ERROR,
                    "Failed to verify UsernameToken: " + diagnosticMessage(e), e.toString());
        }
    }

    private static String passwordDigest(byte[] nonce, String created, char[] password) throws Exception {
        byte[] createdBytes = created.getBytes(StandardCharsets.UTF_8);
        byte[] passwordBytes = passwordBytes(password);
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        digest.update(nonce);
        digest.update(createdBytes);
        byte[] result = digest.digest(passwordBytes);
        Arrays.fill(passwordBytes, (byte) 0);
        String encoded = Base64.getEncoder().encodeToString(result);
        Arrays.fill(result, (byte) 0);
        return encoded;
    }

    private static byte[] passwordBytes(char[] password) {
        ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password));
        byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        return bytes;
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

    private static String toXml(Document document) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        StringWriter output = new StringWriter();
        factory.newTransformer().transform(new DOMSource(document), new StreamResult(output));
        return output.toString();
    }

    private static String soapNamespace(Element envelope) {
        if (!"Envelope".equals(envelope.getLocalName())) {
            throw new IllegalArgumentException("Root element is not a SOAP Envelope.");
        }
        String namespace = envelope.getNamespaceURI();
        if (!SOAP_11_NS.equals(namespace) && !SOAP_12_NS.equals(namespace)) {
            throw new IllegalArgumentException("Unsupported SOAP Envelope namespace: " + namespace);
        }
        return namespace;
    }

    private static Element uniqueDirectChild(Element parent, String namespace, String localName) {
        Element result = null;
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && namespace.equals(element.getNamespaceURI())
                    && localName.equals(element.getLocalName())) {
                if (result != null) throw new IllegalArgumentException("Multiple " + localName + " elements are not supported.");
                result = element;
            }
        }
        return result;
    }

    private static Element requiredChild(Element parent, String namespace, String localName) {
        Element child = uniqueDirectChild(parent, namespace, localName);
        if (child == null) throw new IllegalArgumentException("UsernameToken is missing " + localName + ".");
        return child;
    }

    private static String qualifiedName(Element context, String localName) {
        String prefix = context.getPrefix();
        return prefix == null || prefix.isBlank() ? localName : prefix + ":" + localName;
    }

    private static VerificationResult error(String message) {
        return new VerificationResult(VerificationResult.Status.ERROR, message, "");
    }

    private static VerificationResult invalid(String message, String details) {
        return new VerificationResult(VerificationResult.Status.INVALID, message, details);
    }

    private static String diagnosticMessage(Throwable error) {
        StringBuilder message = new StringBuilder();
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                if (!message.isEmpty()) message.append(": ");
                message.append(current.getMessage());
            }
        }
        return message.isEmpty() ? error.getClass().getSimpleName() : message.toString();
    }
}
