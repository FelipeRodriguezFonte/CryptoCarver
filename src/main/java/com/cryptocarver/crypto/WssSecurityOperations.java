package com.cryptocarver.crypto;

import com.cryptocarver.model.OperationResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.crypto.AlgorithmMethod;
import javax.xml.crypto.KeySelector;
import javax.xml.crypto.KeySelectorException;
import javax.xml.crypto.KeySelectorResult;
import javax.xml.crypto.XMLCryptoContext;
import javax.xml.crypto.XMLStructure;
import javax.xml.crypto.dom.DOMStructure;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateFactory;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Reusable service for WS-Security (WSS) XML Signature operations.
 * Supports signing and verifying the SOAP Body.
 */
public class WssSecurityOperations {

    private static final String SOAP_11_NS = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String SOAP_12_NS = "http://www.w3.org/2003/05/soap-envelope";
    private static final String WSSE_NS = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd";
    private static final String WSU_NS = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd";
    private static final String X509_V3_VALUE_TYPE =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-x509-token-profile-1.0#X509v3";
    private static final String BASE64_ENCODING_TYPE =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary";
    private static final long VERIFICATION_CLOCK_SKEW_SECONDS = 60;
    private static final long MAX_TIMESTAMP_LIFETIME_SECONDS = 24 * 60 * 60;

    /** SHA-2 XML Signature algorithms currently supported by this WSS laboratory tool. */
    public enum WssSignatureAlgorithm {
        RSA_SHA256("RSA_SHA256", "RSA", "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256", DigestMethod.SHA256),
        RSA_SHA384("RSA_SHA384", "RSA", "http://www.w3.org/2001/04/xmldsig-more#rsa-sha384", "http://www.w3.org/2001/04/xmldsig-more#sha384"),
        RSA_SHA512("RSA_SHA512", "RSA", "http://www.w3.org/2001/04/xmldsig-more#rsa-sha512", DigestMethod.SHA512),
        ECDSA_SHA256("ECDSA_SHA256", "EC", "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha256", DigestMethod.SHA256),
        ECDSA_SHA384("ECDSA_SHA384", "EC", "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha384", "http://www.w3.org/2001/04/xmldsig-more#sha384"),
        ECDSA_SHA512("ECDSA_SHA512", "EC", "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha512", DigestMethod.SHA512);

        private final String displayName;
        private final String keyAlgorithm;
        private final String signatureMethod;
        private final String digestMethod;

        WssSignatureAlgorithm(String displayName, String keyAlgorithm, String signatureMethod, String digestMethod) {
            this.displayName = displayName;
            this.keyAlgorithm = keyAlgorithm;
            this.signatureMethod = signatureMethod;
            this.digestMethod = digestMethod;
        }

        public String displayName() { return displayName; }

        public static WssSignatureAlgorithm fromDisplayName(String value) {
            for (WssSignatureAlgorithm algorithm : values()) if (algorithm.displayName.equals(value)) return algorithm;
            throw new IllegalArgumentException("Unsupported WSS signature algorithm: " + value);
        }

        private static WssSignatureAlgorithm fromSignatureMethod(String value) {
            for (WssSignatureAlgorithm algorithm : values()) if (algorithm.signatureMethod.equals(value)) return algorithm;
            throw new IllegalArgumentException("Forbidden or unsupported WSS signature method: " + value);
        }
    }

    /** Optional WS-Security timestamp policy used while signing. */
    public record WssTimestampOptions(boolean enabled, int validityMinutes, boolean signed) {
        public WssTimestampOptions {
            if (enabled && (validityMinutes < 1 || validityMinutes > 1440)) {
                throw new IllegalArgumentException("Timestamp validity must be between 1 and 1440 minutes.");
            }
        }

        public static WssTimestampOptions disabled() {
            return new WssTimestampOptions(false, 0, false);
        }

        public static WssTimestampOptions signed(int validityMinutes) {
            return new WssTimestampOptions(true, validityMinutes, true);
        }
    }

    public static class WssVerificationResult {
        public enum Status {
            VALID, INVALID, ERROR
        }

        private final Status status;
        private final String message;
        private final String technicalDetails;

        public WssVerificationResult(Status status, String message, String technicalDetails) {
            this.status = status;
            this.message = message;
            this.technicalDetails = technicalDetails;
        }

        public Status getStatus() { return status; }
        public String getMessage() { return message; }
        public String getTechnicalDetails() { return technicalDetails; }
    }

    /**
     * Parse XML string into Document with strict security settings preventing XXE.
     */
    private static Document parseSecurely(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static String soapNamespace(Element envelope) {
        if (!"Envelope".equals(envelope.getLocalName())) {
            throw new IllegalArgumentException("Root element is not a SOAP Envelope (found: " + envelope.getLocalName() + ").");
        }
        String namespace = envelope.getNamespaceURI();
        if (!SOAP_11_NS.equals(namespace) && !SOAP_12_NS.equals(namespace)) {
            throw new IllegalArgumentException("Unsupported SOAP Envelope namespace: " + namespace);
        }
        return namespace;
    }

    private static Element directChild(Element parent, String namespace, String localName) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && localName.equals(element.getLocalName())
                    && namespace.equals(element.getNamespaceURI())) return element;
        }
        return null;
    }

    private static Element uniqueDirectChild(Element parent, String namespace, String localName) {
        Element match = null;
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && localName.equals(element.getLocalName())
                    && namespace.equals(element.getNamespaceURI())) {
                if (match != null) {
                    throw new IllegalArgumentException("Multiple " + localName + " elements are not supported.");
                }
                match = element;
            }
        }
        return match;
    }

    private static String qualifiedName(Element context, String localName) {
        String prefix = context.getPrefix();
        return prefix == null || prefix.isBlank() ? localName : prefix + ":" + localName;
    }

    private static String diagnosticMessage(Throwable error) {
        StringBuilder message = new StringBuilder();
        Throwable current = error;
        while (current != null) {
            String currentMessage = current.getMessage();
            if (currentMessage != null && !currentMessage.isBlank()
                    && message.indexOf(currentMessage) < 0) {
                if (!message.isEmpty()) message.append(": ");
                message.append(currentMessage);
            }
            current = current.getCause();
        }
        return message.isEmpty() ? error.getClass().getSimpleName() : message.toString();
    }

    /**
     * Signs the SOAP Body of the given XML.
     */
    public static String signSoapBody(String xmlContent, KeyStore keystore, String alias, char[] keyPassword) throws Exception {
        return signSoapBody(xmlContent, keystore, alias, keyPassword, WssSignatureAlgorithm.RSA_SHA256);
    }

    public static String signSoapBody(String xmlContent, KeyStore keystore, String alias, char[] keyPassword,
                                      WssSignatureAlgorithm algorithm) throws Exception {
        return signSoapBody(xmlContent, keystore, alias, keyPassword, algorithm, WssTimestampOptions.disabled());
    }

    public static String signSoapBody(String xmlContent, KeyStore keystore, String alias, char[] keyPassword,
                                      WssSignatureAlgorithm algorithm, WssTimestampOptions timestampOptions) throws Exception {
        if (keystore == null || alias == null || keyPassword == null) {
            throw new IllegalArgumentException("Keystore, alias, and key password are required.");
        }
        if (algorithm == null) throw new IllegalArgumentException("A WSS signature algorithm is required.");
        if (timestampOptions == null) throw new IllegalArgumentException("Timestamp options are required.");

        Key key = keystore.getKey(alias, keyPassword);
        if (!(key instanceof PrivateKey)) {
            throw new IllegalArgumentException("Key with alias '" + alias + "' is not a private key.");
        }
        PrivateKey privateKey = (PrivateKey) key;
        if (!algorithm.keyAlgorithm.equalsIgnoreCase(privateKey.getAlgorithm())) {
            throw new IllegalArgumentException(algorithm.displayName + " requires a " + algorithm.keyAlgorithm
                    + " private key, but alias '" + alias + "' contains " + privateKey.getAlgorithm() + ".");
        }

        java.security.cert.Certificate cert = keystore.getCertificate(alias);
        if (!(cert instanceof X509Certificate)) {
            throw new IllegalArgumentException("Key with alias '" + alias + "' does not have an X.509 certificate.");
        }
        X509Certificate x509Cert = (X509Certificate) cert;

        Document doc = parseSecurely(xmlContent);

        Element envelope = doc.getDocumentElement();
        String soapNamespace = soapNamespace(envelope);
        Element body = directChild(envelope, soapNamespace, "Body");
        if (body == null) {
            throw new IllegalArgumentException("No SOAP Body found in the document.");
        }
        Element header = directChild(envelope, soapNamespace, "Header");
        if (header == null) {
            header = doc.createElementNS(soapNamespace, qualifiedName(envelope, "Header"));
            envelope.insertBefore(header, body);
        }

        // Ensure Body has a wsu:Id
        String bodyId = body.getAttributeNS(WSU_NS, "Id");
        if (bodyId == null || bodyId.trim().isEmpty()) {
            bodyId = "Body-" + UUID.randomUUID().toString();
            body.setAttributeNS(WSU_NS, "wsu:Id", bodyId);
            body.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:wsu", WSU_NS);
        }
        body.setIdAttributeNS(WSU_NS, "Id", true); // Crucial for DOMSignContext reference resolution

        // Create or find wsse:Security
        Element security = uniqueDirectChild(header, WSSE_NS, "Security");
        if (security == null) {
            security = doc.createElementNS(WSSE_NS, "wsse:Security");
            security.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:wsse", WSSE_NS);
            header.appendChild(security);
        }

        String tokenId = "X509-" + UUID.randomUUID();
        Element binaryToken = doc.createElementNS(WSSE_NS, "wsse:BinarySecurityToken");
        binaryToken.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:wsu", WSU_NS);
        binaryToken.setAttributeNS(WSU_NS, "wsu:Id", tokenId);
        binaryToken.setAttribute("ValueType", X509_V3_VALUE_TYPE);
        binaryToken.setAttribute("EncodingType", BASE64_ENCODING_TYPE);
        binaryToken.setTextContent(Base64.getEncoder().encodeToString(x509Cert.getEncoded()));
        security.appendChild(binaryToken);

        Element timestamp = null;
        String timestampId = null;
        if (timestampOptions.enabled()) {
            if (uniqueDirectChild(security, WSU_NS, "Timestamp") != null) {
                throw new IllegalArgumentException("The wsse:Security header already contains a wsu:Timestamp.");
            }
            timestampId = "TS-" + UUID.randomUUID();
            Instant created = Instant.now().truncatedTo(ChronoUnit.SECONDS);
            Instant expires = created.plus(timestampOptions.validityMinutes(), ChronoUnit.MINUTES);
            timestamp = doc.createElementNS(WSU_NS, "wsu:Timestamp");
            timestamp.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:wsu", WSU_NS);
            timestamp.setAttributeNS(WSU_NS, "wsu:Id", timestampId);
            timestamp.setIdAttributeNS(WSU_NS, "Id", true);
            Element createdElement = doc.createElementNS(WSU_NS, "wsu:Created");
            createdElement.setTextContent(created.toString());
            Element expiresElement = doc.createElementNS(WSU_NS, "wsu:Expires");
            expiresElement.setTextContent(expires.toString());
            timestamp.appendChild(createdElement);
            timestamp.appendChild(expiresElement);
            security.appendChild(timestamp);
        }

        // Generate Signature
        XMLSignatureFactory sigFactory = XMLSignatureFactory.getInstance("DOM");

        List<Reference> signedReferences = new ArrayList<>();
        signedReferences.add(sigFactory.newReference(
                "#" + bodyId,
                sigFactory.newDigestMethod(algorithm.digestMethod, null),
                Collections.singletonList(sigFactory.newTransform(CanonicalizationMethod.EXCLUSIVE, (TransformParameterSpec) null)),
                null,
                null
        ));
        if (timestamp != null && timestampOptions.signed()) {
            signedReferences.add(sigFactory.newReference(
                    "#" + timestampId,
                    sigFactory.newDigestMethod(algorithm.digestMethod, null),
                    Collections.singletonList(sigFactory.newTransform(CanonicalizationMethod.EXCLUSIVE,
                            (TransformParameterSpec) null)),
                    null,
                    null
            ));
        }

        SignedInfo signedInfo = sigFactory.newSignedInfo(
                sigFactory.newCanonicalizationMethod(CanonicalizationMethod.EXCLUSIVE, (C14NMethodParameterSpec) null),
                sigFactory.newSignatureMethod(algorithm.signatureMethod, null),
                signedReferences
        );

        KeyInfoFactory kif = sigFactory.getKeyInfoFactory();
        Element tokenReference = doc.createElementNS(WSSE_NS, "wsse:SecurityTokenReference");
        Element referenceElement = doc.createElementNS(WSSE_NS, "wsse:Reference");
        referenceElement.setAttribute("URI", "#" + tokenId);
        referenceElement.setAttribute("ValueType", X509_V3_VALUE_TYPE);
        tokenReference.appendChild(referenceElement);
        KeyInfo keyInfo = kif.newKeyInfo(Collections.singletonList(new DOMStructure(tokenReference)));

        DOMSignContext signContext = new DOMSignContext(privateKey, security);

        XMLSignature signature = sigFactory.newXMLSignature(signedInfo, keyInfo);
        signature.sign(signContext);

        // Convert back to String
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer trans = tf.newTransformer();
        StringWriter sw = new StringWriter();
        trans.transform(new DOMSource(doc), new StreamResult(sw));

        return sw.toString();
    }

    /**
     * Verifies the WSS Signature of the SOAP Body.
     */
    public static WssVerificationResult verifySoapSignature(String xmlContent, X509Certificate trustedCert) {
        try {
            Document doc = parseSecurely(xmlContent);
            Element envelope = doc.getDocumentElement();
            String soapNamespace = soapNamespace(envelope);

            Element header = uniqueDirectChild(envelope, soapNamespace, "Header");
            if (header == null) {
                return new WssVerificationResult(WssVerificationResult.Status.ERROR, "No SOAP Header found in the document.", "");
            }

            // Only trust the direct wsse:Security child of the SOAP Header.
            Element security = uniqueDirectChild(header, WSSE_NS, "Security");
            if (security == null) {
                return new WssVerificationResult(WssVerificationResult.Status.ERROR, "No wsse:Security header found.", "");
            }

            Element signatureElement = uniqueDirectChild(security, XMLSignature.XMLNS, "Signature");
            if (signatureElement == null) {
                return new WssVerificationResult(WssVerificationResult.Status.ERROR, "No ds:Signature found in wsse:Security header.", "");
            }

            Element body = directChild(envelope, soapNamespace, "Body");
            if (body == null) {
                return new WssVerificationResult(WssVerificationResult.Status.ERROR, "No SOAP Body found in the document.", "");
            }

            String bodyId = body.getAttributeNS(WSU_NS, "Id");
            if (bodyId == null || bodyId.trim().isEmpty()) {
                return new WssVerificationResult(WssVerificationResult.Status.ERROR, "SOAP Body does not have a wsu:Id attribute.", "");
            }
            body.setIdAttributeNS(WSU_NS, "Id", true);

            Element timestamp = uniqueDirectChild(security, WSU_NS, "Timestamp");
            String timestampId = null;
            Instant timestampCreated = null;
            Instant timestampExpires = null;
            if (timestamp != null) {
                timestampId = timestamp.getAttributeNS(WSU_NS, "Id");
                if (timestampId == null || timestampId.isBlank()) {
                    return new WssVerificationResult(WssVerificationResult.Status.ERROR,
                            "wsu:Timestamp does not have a wsu:Id attribute.", "");
                }
                timestamp.setIdAttributeNS(WSU_NS, "Id", true);
                Element created = uniqueDirectChild(timestamp, WSU_NS, "Created");
                Element expires = uniqueDirectChild(timestamp, WSU_NS, "Expires");
                if (created == null || expires == null) {
                    return new WssVerificationResult(WssVerificationResult.Status.ERROR,
                            "wsu:Timestamp must contain Created and Expires.", "");
                }
                try {
                    timestampCreated = Instant.parse(created.getTextContent().trim());
                    timestampExpires = Instant.parse(expires.getTextContent().trim());
                } catch (DateTimeParseException e) {
                    return new WssVerificationResult(WssVerificationResult.Status.ERROR,
                            "wsu:Timestamp contains an invalid UTC instant.", e.toString());
                }
                if (!timestampExpires.isAfter(timestampCreated)) {
                    return new WssVerificationResult(WssVerificationResult.Status.ERROR,
                            "wsu:Timestamp Expires must be later than Created.", "");
                }
                if (timestampCreated.until(timestampExpires, ChronoUnit.SECONDS) > MAX_TIMESTAMP_LIFETIME_SECONDS) {
                    return new WssVerificationResult(WssVerificationResult.Status.ERROR,
                            "wsu:Timestamp lifetime exceeds 24 hours.", "");
                }
                Instant now = Instant.now();
                if (timestampCreated.isAfter(now.plusSeconds(VERIFICATION_CLOCK_SKEW_SECONDS))) {
                    return new WssVerificationResult(WssVerificationResult.Status.INVALID,
                            "WSS Timestamp is not valid yet.", "Created: " + timestampCreated + "\n");
                }
                if (timestampExpires.isBefore(now.minusSeconds(VERIFICATION_CLOCK_SKEW_SECONDS))) {
                    return new WssVerificationResult(WssVerificationResult.Status.INVALID,
                            "WSS Timestamp has expired.", "Expires: " + timestampExpires + "\n");
                }
            }

            // Configure verification
            XMLSignatureFactory sigFactory = XMLSignatureFactory.getInstance("DOM");
            DOMValidateContext valContext = new DOMValidateContext(new WssKeySelector(trustedCert), signatureElement);
            valContext.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.TRUE);

            // Allow explicit wsu:Id resolution
            valContext.setIdAttributeNS(body, WSU_NS, "Id");
            if (timestamp != null) valContext.setIdAttributeNS(timestamp, WSU_NS, "Id");

            XMLSignature signature = sigFactory.unmarshalXMLSignature(valContext);
            WssSignatureAlgorithm signatureAlgorithm = WssSignatureAlgorithm.fromSignatureMethod(
                    signature.getSignedInfo().getSignatureMethod().getAlgorithm());

            // Allow exactly the Body plus, optionally, the declared Timestamp.
            List<?> references = signature.getSignedInfo().getReferences();
            if (references.isEmpty() || references.size() > 2) {
                return new WssVerificationResult(WssVerificationResult.Status.ERROR,
                        "Signature must reference the SOAP Body and, optionally, its Timestamp.", "");
            }

            boolean bodySigned = false;
            boolean timestampSigned = false;
            for (Object referenceObject : references) {
                Reference reference = (Reference) referenceObject;
                String uri = reference.getURI();
                if (("#" + bodyId).equals(uri) && !bodySigned) {
                    bodySigned = true;
                } else if (timestampId != null && ("#" + timestampId).equals(uri) && !timestampSigned) {
                    timestampSigned = true;
                } else {
                    return new WssVerificationResult(WssVerificationResult.Status.ERROR,
                            "Signature reference does not match the SOAP Body wsu:Id or declared Timestamp wsu:Id: " + uri, "");
                }
                if (!signatureAlgorithm.digestMethod.equals(reference.getDigestMethod().getAlgorithm())) {
                    return new WssVerificationResult(WssVerificationResult.Status.ERROR,
                            "Digest method does not match the WSS signature algorithm.", "");
                }
            }
            if (!bodySigned) {
                return new WssVerificationResult(WssVerificationResult.Status.ERROR,
                        "Signature reference does not match the SOAP Body wsu:Id.", "");
            }

            boolean coreValidity = signature.validate(valContext);

            StringBuilder techDetails = new StringBuilder();
            techDetails.append("Algorithm: ").append(signatureAlgorithm.displayName).append("\n");
            techDetails.append("SOAP version: ").append(SOAP_12_NS.equals(soapNamespace) ? "1.2" : "1.1").append("\n");
            techDetails.append("Certificate source: ").append(
                    security.getElementsByTagNameNS(WSSE_NS, "BinarySecurityToken").getLength() > 0
                            ? "wsse:BinarySecurityToken" : "ds:X509Data/external certificate").append("\n");
            techDetails.append("Trust validation: ").append(trustedCert == null
                    ? "not performed (embedded certificate proves signature integrity only)"
                    : "signature pinned to the supplied trusted certificate").append("\n");
            techDetails.append("Signed Body ID: ").append(bodyId).append("\n");
            if (timestamp != null) {
                techDetails.append("Timestamp: ").append(timestampCreated).append(" -> ")
                        .append(timestampExpires).append("\n");
                techDetails.append("Timestamp signed: ").append(timestampSigned ? "yes" : "no").append("\n");
            } else {
                techDetails.append("Timestamp: not present\n");
            }

            if (coreValidity) {
                return new WssVerificationResult(WssVerificationResult.Status.VALID, "WSS Signature is valid.", techDetails.toString());
            } else {
                boolean sv = signature.getSignatureValue().validate(valContext);
                techDetails.append("Signature Value Valid: ").append(sv).append("\n");
                for (Object refObj : signature.getSignedInfo().getReferences()) {
                    Reference r = (Reference) refObj;
                    boolean refValid = r.validate(valContext);
                    techDetails.append("Reference [").append(r.getURI()).append("] Valid: ").append(refValid).append("\n");
                }
                return new WssVerificationResult(WssVerificationResult.Status.INVALID, "WSS Signature is invalid (tampered or broken reference).", techDetails.toString());
            }

        } catch (Exception e) {
            return new WssVerificationResult(WssVerificationResult.Status.ERROR,
                    "Failed to verify signature: " + diagnosticMessage(e), e.toString());
        }
    }

    /**
     * KeySelector that trusts either a provided certificate or the KeyInfo embedded in the XML.
     */
    private static class WssKeySelector extends KeySelector {
        private final X509Certificate trustedCert;

        public WssKeySelector(X509Certificate trustedCert) {
            this.trustedCert = trustedCert;
        }

        @Override
        public KeySelectorResult select(KeyInfo keyInfo, KeySelector.Purpose purpose, AlgorithmMethod method, XMLCryptoContext context) throws KeySelectorException {
            if (trustedCert != null) {
                return new SimpleKeySelectorResult(trustedCert.getPublicKey());
            }

            if (keyInfo == null) {
                throw new KeySelectorException("No KeyInfo found and no trusted certificate provided.");
            }

            for (Object info : keyInfo.getContent()) {
                if (info instanceof X509Data) {
                    X509Data x509Data = (X509Data) info;
                    for (Object certObj : x509Data.getContent()) {
                        if (certObj instanceof X509Certificate) {
                            return new SimpleKeySelectorResult(((X509Certificate) certObj).getPublicKey());
                        }
                    }
                } else if (info instanceof DOMStructure domStructure && domStructure.getNode() instanceof Element element
                        && "SecurityTokenReference".equals(element.getLocalName())
                        && WSSE_NS.equals(element.getNamespaceURI())) {
                    return new SimpleKeySelectorResult(certificateFromSecurityTokenReference(element).getPublicKey());
                }
            }
            throw new KeySelectorException("No X509Data or valid SecurityTokenReference found in KeyInfo.");
        }

        private X509Certificate certificateFromSecurityTokenReference(Element tokenReference) throws KeySelectorException {
            Element reference;
            try {
                reference = uniqueDirectChild(tokenReference, WSSE_NS, "Reference");
            } catch (IllegalArgumentException e) {
                throw new KeySelectorException(e.getMessage(), e);
            }
            if (reference == null) throw new KeySelectorException("SecurityTokenReference must contain one wsse:Reference.");
            String uri = reference.getAttribute("URI");
            if (uri == null || !uri.startsWith("#") || uri.length() == 1) {
                throw new KeySelectorException("SecurityTokenReference URI must be a local fragment.");
            }
            if (!X509_V3_VALUE_TYPE.equals(reference.getAttribute("ValueType"))) {
                throw new KeySelectorException("Unsupported SecurityTokenReference ValueType.");
            }
            Document document = tokenReference.getOwnerDocument();
            NodeList tokens = document.getElementsByTagNameNS(WSSE_NS, "BinarySecurityToken");
            Element matchingToken = null;
            for (int i = 0; i < tokens.getLength(); i++) {
                Element token = (Element) tokens.item(i);
                if (uri.substring(1).equals(token.getAttributeNS(WSU_NS, "Id"))) {
                    if (matchingToken != null) throw new KeySelectorException("Duplicate BinarySecurityToken wsu:Id.");
                    matchingToken = token;
                }
            }
            if (matchingToken == null) throw new KeySelectorException("Referenced BinarySecurityToken was not found.");
            if (!X509_V3_VALUE_TYPE.equals(matchingToken.getAttribute("ValueType"))
                    || !BASE64_ENCODING_TYPE.equals(matchingToken.getAttribute("EncodingType"))) {
                throw new KeySelectorException("Unsupported BinarySecurityToken type or encoding.");
            }
            try {
                byte[] der = Base64.getMimeDecoder().decode(matchingToken.getTextContent());
                return (X509Certificate) CertificateFactory.getInstance("X.509")
                        .generateCertificate(new ByteArrayInputStream(der));
            } catch (Exception e) {
                throw new KeySelectorException("Invalid X.509 BinarySecurityToken.", e);
            }
        }
    }

    private static class SimpleKeySelectorResult implements KeySelectorResult {
        private final PublicKey publicKey;

        public SimpleKeySelectorResult(PublicKey publicKey) {
            this.publicKey = publicKey;
        }

        @Override
        public Key getKey() {
            return publicKey;
        }
    }
}
