package com.cryptocarver.crypto;

import eu.europa.esig.dss.enumerations.ServiceQualification;
import eu.europa.esig.trustedlist.TrustedListFacade;
import eu.europa.esig.trustedlist.jaxb.ecc.QualificationElementType;
import eu.europa.esig.trustedlist.jaxb.ecc.QualificationsType;
import eu.europa.esig.trustedlist.jaxb.ecc.QualifierType;
import eu.europa.esig.trustedlist.jaxb.tsl.DigitalIdentityType;
import eu.europa.esig.trustedlist.jaxb.tsl.ExtensionType;
import eu.europa.esig.trustedlist.jaxb.tsl.InternationalNamesType;
import eu.europa.esig.trustedlist.jaxb.tsl.MultiLangNormStringType;
import eu.europa.esig.trustedlist.jaxb.tsl.OtherTSLPointerType;
import eu.europa.esig.trustedlist.jaxb.tsl.TSLSchemeInformationType;
import eu.europa.esig.trustedlist.jaxb.tsl.TSPServiceInformationType;
import eu.europa.esig.trustedlist.jaxb.tsl.TSPServiceType;
import eu.europa.esig.trustedlist.jaxb.tsl.TSPType;
import eu.europa.esig.trustedlist.jaxb.tsl.TrustStatusListType;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reads an ETSI TS 119 612 Trusted List: who the scheme operator is, which trust
 * service providers it lists, what status each service is in, and which
 * qualifiers apply to it.
 *
 * <p>This is the other half of {@link EidasCertificateInspector}. A certificate
 * says what it means to be; the Trusted List says whether the state agrees. A
 * QcCompliance statement in a certificate is a claim by its issuer, and only the
 * list turns that claim into qualification.</p>
 *
 * <h2>Local by construction, and why that is not a shortcut</h2>
 * <p>DSS ships a module whose job is to download the List of Trusted Lists, walk
 * its pointers and refresh the national lists on a timer. That is the opposite of
 * what this app does, so it is not used: a list arrives here as bytes the user
 * supplies, like every other artefact. Nothing is fetched. The JAXB model of the
 * specification — which DSS already puts on this app's classpath — is enough to
 * read one.</p>
 *
 * <h2>The limit of verifying a list's own signature</h2>
 * <p>{@link #verifySignature} checks the enveloped XAdES against the certificate
 * the list carries in its own {@code KeyInfo}. That proves the bytes have not
 * been altered since signing; it proves <b>nothing</b> about whether the signer
 * was entitled to publish that list, because a forger would simply sign their
 * own list with their own key and embed that. Authenticity comes from checking
 * the signing certificate against the List of Trusted Lists, and ultimately
 * against what the Official Journal publishes — out of band, by a human. The
 * report says so rather than letting a green tick imply more than it means.</p>
 */
public final class TrustedListInspector {

    private static final String STATUS_PREFIX = "http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/";
    private static final String GRANTED = STATUS_PREFIX + "granted";
    private static final String WITHDRAWN = STATUS_PREFIX + "withdrawn";

    /** One trust service, flattened to what a reader actually asks about. */
    public record Service(String providerName,
                          String serviceName,
                          String typeIdentifier,
                          String status,
                          String statusStartingTime,
                          List<String> qualifiers,
                          List<X509Certificate> digitalIdentities) {

        public boolean isGranted() {
            return GRANTED.equals(status);
        }

        /** Withdrawn is not the same as absent: a service that was once granted
         *  and is now withdrawn still says something about signatures made while
         *  it was granted, which is why the status carries a starting time. */
        public boolean isWithdrawn() {
            return WITHDRAWN.equals(status);
        }

        /** The final path segment of the status URI, which is what a person reads. */
        public String statusLabel() {
            if (status == null) {
                return "unknown";
            }
            int slash = status.lastIndexOf('/');
            return slash < 0 ? status : status.substring(slash + 1);
        }
    }

    public record SchemeInformation(String territory,
                                    String operatorName,
                                    String type,
                                    java.math.BigInteger sequenceNumber,
                                    String issueDate,
                                    String nextUpdate,
                                    List<String> pointersToOtherLists) {
    }

    public record TrustedList(SchemeInformation scheme, List<Service> services) {

        public boolean isListOfTrustedLists() {
            return !scheme.pointersToOtherLists().isEmpty() && services.isEmpty();
        }
    }

    /** @param trustNote always present: what the result does and does not prove */
    public record SignatureResult(boolean signatureValid,
                                  X509Certificate signingCertificate,
                                  String trustNote) {
    }

    /** Where a certificate turned up in a list, if it did. */
    public record Match(Service service, String matchedBy) {
    }

    private TrustedListInspector() {
    }

    // ------------------------------------------------------------------ parse

    public static TrustedList parse(byte[] xml) throws Exception {
        TrustStatusListType list = TrustedListFacade.newFacade()
                .unmarshall(new ByteArrayInputStream(xml), false);

        TSLSchemeInformationType info = list.getSchemeInformation();
        List<String> pointers = new ArrayList<>();
        if (info != null && info.getPointersToOtherTSL() != null) {
            for (OtherTSLPointerType pointer : info.getPointersToOtherTSL().getOtherTSLPointer()) {
                pointers.add(pointer.getTSLLocation());
            }
        }
        SchemeInformation scheme = new SchemeInformation(
                info == null ? null : info.getSchemeTerritory(),
                info == null ? null : firstName(info.getSchemeOperatorName()),
                info == null ? null : info.getTSLType(),
                info == null ? null : info.getTSLSequenceNumber(),
                info == null || info.getListIssueDateTime() == null
                        ? null : info.getListIssueDateTime().toString(),
                info == null || info.getNextUpdate() == null || info.getNextUpdate().getDateTime() == null
                        ? null : info.getNextUpdate().getDateTime().toString(),
                List.copyOf(pointers));

        List<Service> services = new ArrayList<>();
        if (list.getTrustServiceProviderList() != null) {
            for (TSPType provider : list.getTrustServiceProviderList().getTrustServiceProvider()) {
                String providerName = provider.getTSPInformation() == null
                        ? null : firstName(provider.getTSPInformation().getTSPName());
                if (provider.getTSPServices() == null) {
                    continue;
                }
                for (TSPServiceType service : provider.getTSPServices().getTSPService()) {
                    services.add(toService(providerName, service.getServiceInformation()));
                }
            }
        }
        return new TrustedList(scheme, List.copyOf(services));
    }

    private static Service toService(String providerName, TSPServiceInformationType info) {
        if (info == null) {
            return new Service(providerName, null, null, null, null, List.of(), List.of());
        }
        List<X509Certificate> certificates = new ArrayList<>();
        if (info.getServiceDigitalIdentity() != null) {
            for (DigitalIdentityType identity : info.getServiceDigitalIdentity().getDigitalId()) {
                if (identity.getX509Certificate() != null) {
                    try {
                        certificates.add(decodeCertificate(identity.getX509Certificate()));
                    } catch (Exception e) {
                        // A malformed identity is worth skipping, not worth
                        // aborting the whole list over: the other services in it
                        // are still readable and still useful.
                    }
                }
            }
        }
        return new Service(providerName,
                firstName(info.getServiceName()),
                info.getServiceTypeIdentifier(),
                info.getServiceStatus(),
                info.getStatusStartingTime() == null ? null : info.getStatusStartingTime().toString(),
                qualifiers(info),
                List.copyOf(certificates));
    }

    /** The qualifiers live in a TS 119 612 extension, which is where the answer
     *  to "qualified for what?" actually is — for a signature, for a seal, for
     *  website authentication, with or without a QSCD. */
    private static List<String> qualifiers(TSPServiceInformationType info) {
        if (info.getServiceInformationExtensions() == null) {
            return List.of();
        }
        Set<String> uris = new LinkedHashSet<>();
        for (ExtensionType extension : info.getServiceInformationExtensions().getExtension()) {
            for (Object content : extension.getContent()) {
                QualificationsType qualifications = unwrapQualifications(content);
                if (qualifications == null) {
                    continue;
                }
                for (QualificationElementType element : qualifications.getQualificationElement()) {
                    if (element.getQualifiers() == null) {
                        continue;
                    }
                    for (QualifierType qualifier : element.getQualifiers().getQualifier()) {
                        uris.add(qualifier.getUri());
                    }
                }
            }
        }
        return List.copyOf(uris);
    }

    private static QualificationsType unwrapQualifications(Object content) {
        Object value = content instanceof jakarta.xml.bind.JAXBElement<?> element
                ? element.getValue()
                : content;
        return value instanceof QualificationsType qualifications ? qualifications : null;
    }

    // -------------------------------------------------------------- signature

    /**
     * Verifies the list's enveloped XAdES against the certificate in its own
     * {@code KeyInfo}. Read the class documentation before drawing a conclusion
     * from a {@code true}: this establishes integrity, not authority.
     */
    public static SignatureResult verifySignature(byte[] xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));

        // A Trusted List references itself by Id, so the Id attributes have to be
        // registered before the reference can be dereferenced; without this the
        // signature fails to resolve rather than failing to verify, which looks
        // the same from outside and is not.
        registerIdAttributes(document.getDocumentElement());

        NodeList signatures = document.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
        if (signatures.getLength() == 0) {
            return new SignatureResult(false, null,
                    "This list carries no XML signature at all.");
        }

        X509Certificate signing = signingCertificate(document);
        if (signing == null) {
            return new SignatureResult(false, null,
                    "The signature carries no X.509 certificate in its KeyInfo, so it cannot be"
                            + " verified from the list alone.");
        }
        PublicKey key = signing.getPublicKey();
        DOMValidateContext context = new DOMValidateContext(key, signatures.item(0));
        boolean valid = XMLSignatureFactory.getInstance("DOM")
                .unmarshalXMLSignature(context).validate(context);

        String note = valid
                ? "The list is intact and was signed by the certificate it carries. That certificate"
                  + " must still be checked against the List of Trusted Lists, and ultimately against"
                  + " the Official Journal: a forged list would also verify against its own key."
                : "The signature does not verify: these bytes are not the ones that were signed.";
        return new SignatureResult(valid, signing, note);
    }

    private static X509Certificate signingCertificate(Document document) throws Exception {
        NodeList certificates = document.getElementsByTagNameNS(XMLSignature.XMLNS, "X509Certificate");
        if (certificates.getLength() == 0) {
            return null;
        }
        String base64 = certificates.item(0).getTextContent().replaceAll("\\s", "");
        return decodeCertificate(java.util.Base64.getDecoder().decode(base64));
    }

    private static void registerIdAttributes(Element element) {
        if (element.hasAttribute("Id")) {
            element.setIdAttribute("Id", true);
        }
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child) {
                registerIdAttributes(child);
            }
        }
    }

    // ------------------------------------------------------------- lookup

    /**
     * Finds the services a certificate appears in. Matching is by the exact
     * encoded certificate first and by subject key identifier second, because a
     * list may name a service by its key rather than by a whole certificate —
     * and a subject name alone is not an identity, so it is never enough here.
     */
    public static List<Match> findCertificate(TrustedList list, X509Certificate certificate) throws Exception {
        byte[] encoded = certificate.getEncoded();
        byte[] ski = certificate.getExtensionValue("2.5.29.14");
        List<Match> matches = new ArrayList<>();
        for (Service service : list.services()) {
            for (X509Certificate listed : service.digitalIdentities()) {
                if (Arrays.equals(encoded, listed.getEncoded())) {
                    matches.add(new Match(service, "exact certificate"));
                    break;
                }
                byte[] listedSki = listed.getExtensionValue("2.5.29.14");
                if (ski != null && listedSki != null && Arrays.equals(ski, listedSki)) {
                    matches.add(new Match(service, "subject key identifier"));
                    break;
                }
            }
        }
        return List.copyOf(matches);
    }

    // ------------------------------------------------------------- reporting

    public static String describe(byte[] xml, Locale locale) throws Exception {
        boolean spanish = locale != null && "es".equals(locale.getLanguage());
        TrustedList list = parse(xml);
        SchemeInformation scheme = list.scheme();
        StringBuilder text = new StringBuilder();

        text.append(spanish ? "Lista de confianza (TS 119 612)" : "Trusted List (TS 119 612)").append('\n');
        text.append("  ").append(spanish ? "territorio" : "territory")
                .append("      : ").append(scheme.territory()).append('\n');
        text.append("  ").append(spanish ? "operador" : "operator")
                .append("       : ").append(scheme.operatorName()).append('\n');
        text.append("  ").append(spanish ? "tipo" : "type")
                .append("           : ").append(scheme.type()).append('\n');
        text.append("  ").append(spanish ? "secuencia" : "sequence")
                .append("      : ").append(scheme.sequenceNumber()).append('\n');
        text.append("  ").append(spanish ? "emitida" : "issued")
                .append("        : ").append(scheme.issueDate()).append('\n');
        text.append("  ").append(spanish ? "próxima" : "next update")
                .append("        : ").append(scheme.nextUpdate()).append('\n');

        if (list.isListOfTrustedLists()) {
            text.append('\n').append(spanish
                    ? "Es una lista de listas: apunta a " + scheme.pointersToOtherLists().size() + " listas nacionales."
                    : "This is a list of lists: it points at " + scheme.pointersToOtherLists().size()
                      + " national lists.").append('\n');
            for (String pointer : scheme.pointersToOtherLists()) {
                text.append("  - ").append(pointer).append('\n');
            }
            return text.toString();
        }

        long granted = list.services().stream().filter(Service::isGranted).count();
        text.append('\n').append(spanish ? "Servicios" : "Services").append(": ")
                .append(list.services().size()).append(" (").append(granted)
                .append(' ').append(spanish ? "concedidos" : "granted").append(")\n");

        for (Service service : list.services()) {
            text.append("  - ").append(service.providerName())
                    .append(" / ").append(service.serviceName()).append('\n');
            text.append("      ").append(spanish ? "estado" : "status").append(": ")
                    .append(service.statusLabel());
            if (service.statusStartingTime() != null) {
                text.append(" ").append(spanish ? "desde" : "since")
                        .append(' ').append(service.statusStartingTime());
            }
            text.append('\n');
            text.append("      ").append(spanish ? "tipo" : "type").append("  : ")
                    .append(service.typeIdentifier()).append('\n');
            for (String qualifier : service.qualifiers()) {
                ServiceQualification known = ServiceQualification.getByUri(qualifier);
                text.append("      ").append(spanish ? "cualificador" : "qualifier").append(": ")
                        .append(known != null ? known.name() : qualifier).append('\n');
            }
        }
        return text.toString();
    }

    private static String firstName(InternationalNamesType names) {
        if (names == null || names.getName().isEmpty()) {
            return null;
        }
        for (MultiLangNormStringType name : names.getName()) {
            if ("en".equalsIgnoreCase(name.getLang())) {
                return name.getValue();
            }
        }
        return names.getName().get(0).getValue();
    }

    private static X509Certificate decodeCertificate(byte[] encoded) throws Exception {
        return (X509Certificate) java.security.cert.CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(encoded));
    }

}
