package com.cryptocarver.crypto;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.europa.esig.dss.enumerations.Indication;
import eu.europa.esig.dss.jades.validation.JWSCompactDocumentValidator;
import eu.europa.esig.dss.jades.validation.JWSSerializationDocumentValidator;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.spi.x509.CommonTrustedCertificateSource;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import eu.europa.esig.dss.validation.reports.Reports;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Local reader for the JSON binding in ETSI TS 119 602 V1.1.1, Annex A.
 * Clauses 6.8.0 and D.4–I.4 require an AdES Baseline B signature and specify
 * compact JAdES for the six EU profiles. JSON serialization is also accepted
 * as an input encoding, but is not the prescribed encoding for those profiles.
 */
public final class TrustedEntityListJsonInspector {
    private static final ObjectMapper JACKSON = new ObjectMapper();
    private static final JsonSchema ETSI_SCHEMA = loadSchema();
    public record Service(String providerName, String serviceName, String typeIdentifier,
                          String status, String statusStartingTime, List<String> qualifiers) { }
    public record SchemeInformation(int version, long sequenceNumber, String operatorName,
                                    String issueDate, String nextUpdate) { }
    public record TrustedEntityList(SchemeInformation scheme, List<Service> services) { }
    private TrustedEntityListJsonInspector() { }

    private record Envelope(byte[] payload, byte[] signature, boolean compact) { }

    private static Envelope envelope(byte[] input) {
        if (input == null || input.length == 0) throw new IllegalArgumentException("Invalid TS 119 602 JSON: empty input");
        String raw = new String(input, StandardCharsets.UTF_8).trim();
        if (!raw.startsWith("{")) {
            String[] parts = raw.split("\\.", -1);
            if (parts.length != 3) throw new IllegalArgumentException("Invalid JAdES compact serialization");
            try {
                return new Envelope(Base64.getUrlDecoder().decode(parts[1]), input, true);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid JAdES compact payload", e);
            }
        }
        try {
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            if (root.has("payload") && (root.has("signature") || root.has("signatures"))) {
                return new Envelope(Base64.getUrlDecoder().decode(root.get("payload").getAsString()), input, false);
            }
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid TS 119 602 JSON: " + e.getMessage(), e);
        }
        return new Envelope(input, null, false);
    }

    public static TrustedEntityList parse(byte[] json) {
        try {
            json = envelope(json).payload();
            validateAgainstEtsiSchema(json);
            JsonObject root = JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject lote = root.getAsJsonObject("LoTE");
            JsonObject info = lote.getAsJsonObject("ListAndSchemeInformation");
            SchemeInformation scheme = new SchemeInformation(info.get("LoTEVersionIdentifier").getAsInt(),
                    info.get("LoTESequenceNumber").getAsLong(), firstValue(info.getAsJsonArray("SchemeOperatorName")),
                    info.get("ListIssueDateTime").getAsString(), info.get("NextUpdate").getAsString());
            List<Service> services = new ArrayList<>();
            JsonArray entities = optionalArray(lote, "TrustedEntitiesList");
            if (entities != null) for (JsonElement entityElement : entities) {
                JsonObject entity = entityElement.getAsJsonObject();
                JsonObject entityInfo = entity.getAsJsonObject("TrustedEntityInformation");
                String provider = firstValue(entityInfo.getAsJsonArray("TEName"));
                for (JsonElement serviceElement : entity.getAsJsonArray("TrustedEntityServices")) {
                    JsonObject service = serviceElement.getAsJsonObject();
                    JsonObject si = service.getAsJsonObject("ServiceInformation");
                    String name = firstValue(si.getAsJsonArray("ServiceName"));
                    services.add(new Service(provider, name, optionalString(si, "ServiceTypeIdentifier"),
                            optionalString(si, "ServiceStatus"), optionalString(si, "StatusStartingTime"),
                            extensions(si.get("ServiceInformationExtensions"))));
                }
            }
            return new TrustedEntityList(scheme, List.copyOf(services));
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("Invalid TS 119 602 JSON: " + e.getMessage(), e); }
    }
    private static JsonSchema loadSchema() {
        try (InputStream in = TrustedEntityListJsonInspector.class.getResourceAsStream(
                "/schemas/etsi-ts-119602-v1.1.1.json")) {
            if (in == null) throw new IllegalStateException("ETSI TS 119 602 JSON Schema resource is missing");
            return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(in);
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    private static void validateAgainstEtsiSchema(byte[] json) throws Exception {
        JsonNode node = JACKSON.readTree(json);
        if (node == null) throw new IllegalArgumentException("Invalid TS 119 602 JSON: empty input");
        java.util.Set<ValidationMessage> violations = ETSI_SCHEMA.validate(node);
        if (!violations.isEmpty()) {
            String detail = violations.stream().map(ValidationMessage::getMessage).sorted().findFirst().orElse("schema violation");
            throw new IllegalArgumentException("Invalid TS 119 602 JSON: " + detail);
        }
    }
    public static String describe(byte[] json, Locale locale) {
        return describe(json, locale, null);
    }

    public static String describe(byte[] json, Locale locale, X509Certificate trustAnchor) {
        return describe(json, locale, trustAnchor, null);
    }

    public static String describe(byte[] json, Locale locale, X509Certificate trustAnchor,
                                  X509Certificate certificateToFind) {
        Envelope envelope = envelope(json);
        TrustedEntityList list = parse(envelope.payload()); StringBuilder out = new StringBuilder();
        out.append("ETSI TS 119 602 JSON\noperator: ").append(list.scheme.operatorName())
                .append("\nversion: ").append(list.scheme.version()).append("\nsequence: ").append(list.scheme.sequenceNumber())
                .append("\nissued: ").append(list.scheme.issueDate()).append("\nnext update: ").append(list.scheme.nextUpdate()).append('\n');
        for (Service s : list.services()) { out.append("\n").append(s.providerName()).append(" / ").append(s.serviceName())
                .append("\n  type: ").append(s.typeIdentifier()).append("\n  status: ").append(s.status())
                .append("\n  status since: ").append(s.statusStartingTime()).append('\n'); for (String q : s.qualifiers()) out.append("  qualifier: ").append(q).append('\n'); }
        TrustedEntityListProfileValidator.Assessment profile = TrustedEntityListProfileValidator.assess(
                envelope.payload(), envelope.compact(), envelope.signature() != null);
        out.append("\nprofile: ").append(profile.profile()).append('\n');
        if (profile.unmetRequirements().isEmpty()) out.append("profile requirements: satisfied\n");
        else for (String unmet : profile.unmetRequirements()) out.append("profile unmet: ").append(unmet).append('\n');
        out.append("\nsignature: ").append(signatureStatus(envelope, trustAnchor)).append('\n');
        if (certificateToFind != null) {
            List<Service> matches = findCertificate(envelope.payload(), certificateToFind);
            out.append("certificate matches: ").append(matches.size()).append('\n');
            for (Service match : matches) {
                out.append("  ").append(match.providerName()).append(" / ").append(match.serviceName())
                        .append(" — status: ").append(match.status())
                        .append("; since: ").append(match.statusStartingTime()).append('\n');
            }
        }
        return out.toString();
    }

    /** Compares the complete DER certificate in current service identities (TS 119 602, §6.6.3.1). */
    public static List<Service> findCertificate(byte[] listJson, X509Certificate certificate) {
        if (certificate == null) throw new IllegalArgumentException("A certificate is required");
        byte[] payload = envelope(listJson).payload();
        TrustedEntityList list = parse(payload);
        JsonArray entities = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("LoTE").getAsJsonArray("TrustedEntitiesList");
        if (entities == null) return List.of();
        List<Service> matches = new ArrayList<>();
        int index = 0;
        for (JsonElement entityElement : entities) {
            for (JsonElement serviceElement : entityElement.getAsJsonObject().getAsJsonArray("TrustedEntityServices")) {
                JsonObject info = serviceElement.getAsJsonObject().getAsJsonObject("ServiceInformation");
                JsonArray certificates = optionalArray(info.getAsJsonObject("ServiceDigitalIdentity"), "X509Certificates");
                if (certificates != null) for (JsonElement item : certificates) {
                    try {
                        byte[] listedDer = Base64.getDecoder().decode(item.getAsJsonObject().get("val").getAsString());
                        if (java.util.Arrays.equals(listedDer, encoded(certificate))) {
                            matches.add(list.services().get(index));
                            break;
                        }
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("Invalid X509Certificates value", e);
                    } catch (java.security.cert.CertificateEncodingException e) {
                        throw new IllegalArgumentException("Cannot encode comparison certificate", e);
                    }
                }
                index++;
            }
        }
        return List.copyOf(matches);
    }

    /** Reads an X.509 certificate in PEM or DER form. */
    public static X509Certificate readCertificate(byte[] pemOrDer) {
        try {
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(pemOrDer));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid X.509 certificate", e);
        }
    }

    private static String signatureStatus(Envelope envelope, X509Certificate trustAnchor) {
        if (envelope.signature() == null) return "no signature present";
        if (trustAnchor == null) return "not verified (no trust anchor provided)";
        try {
            CommonTrustedCertificateSource trusted = new CommonTrustedCertificateSource();
            trusted.addCertificate(new CertificateToken(trustAnchor));
            CommonCertificateVerifier verifier = new CommonCertificateVerifier();
            verifier.setTrustedCertSources(trusted);
            SignedDocumentValidator validator = envelope.compact()
                    ? new JWSCompactDocumentValidator(new InMemoryDocument(envelope.signature(), "list.jws"))
                    : new JWSSerializationDocumentValidator(new InMemoryDocument(envelope.signature(), "list.json"));
            validator.setCertificateVerifier(verifier);
            validator.setSigningCertificateSource(trusted);
            Reports reports = validator.validateDocument();
            var simple = reports.getSimpleReport();
            if (simple.getSignatureIdList().size() != 1) return "invalid (expected exactly one signature)";
            String id = simple.getSignatureIdList().get(0);
            if (simple.getIndication(id) != Indication.TOTAL_PASSED) {
                return "invalid (" + simple.getIndication(id) + ": " + simple.getSubIndication(id) + ")";
            }
            var signer = validator.getSignatures().get(0).getSigningCertificateToken();
            if (signer == null || !java.util.Arrays.equals(signer.getEncoded(), encoded(trustAnchor))) {
                return "invalid (signer does not match trust anchor)";
            }
            return "valid (signer: " + trustAnchor.getSubjectX500Principal().getName() + ")";
        } catch (Exception e) {
            return "invalid (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")";
        }
    }

    private static byte[] encoded(X509Certificate certificate) throws java.security.cert.CertificateEncodingException {
        return certificate.getEncoded();
    }
    private static JsonArray optionalArray(JsonObject object, String name) {
        return object.has(name) && object.get(name).isJsonArray()
                ? object.getAsJsonArray(name) : null;
    }

    private static String optionalString(JsonObject object, String name) {
        return object.has(name) && object.get(name).isJsonPrimitive()
                && object.get(name).getAsJsonPrimitive().isString()
                ? object.get(name).getAsString() : null;
    }

    private static String firstValue(JsonArray values) {
        return values.get(0).getAsJsonObject().get("value").getAsString();
    }

    private static List<String> extensions(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonElement value : element.getAsJsonArray()) {
            values.add(value.toString());
        }
        return List.copyOf(values);
    }
}
