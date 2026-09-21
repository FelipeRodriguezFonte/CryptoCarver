package com.cryptocarver.crypto;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Local reader for the JSON binding in ETSI TS 119 602 V1.1.1, Annex A. */
public final class TrustedEntityListJsonInspector {
    private static final ObjectMapper JACKSON = new ObjectMapper();
    private static final JsonSchema ETSI_SCHEMA = loadSchema();
    public record Service(String providerName, String serviceName, String typeIdentifier,
                          String status, String statusStartingTime, List<String> qualifiers) { }
    public record SchemeInformation(int version, long sequenceNumber, String operatorName,
                                    String issueDate, String nextUpdate) { }
    public record TrustedEntityList(SchemeInformation scheme, List<Service> services,
                                    boolean signaturePresent) { }
    private TrustedEntityListJsonInspector() { }

    public static TrustedEntityList parse(byte[] json) {
        try {
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
            return new TrustedEntityList(scheme, List.copyOf(services), false);
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
        TrustedEntityList list = parse(json); StringBuilder out = new StringBuilder();
        out.append("ETSI TS 119 602 JSON\noperator: ").append(list.scheme.operatorName())
                .append("\nversion: ").append(list.scheme.version()).append("\nsequence: ").append(list.scheme.sequenceNumber())
                .append("\nissued: ").append(list.scheme.issueDate()).append("\nnext update: ").append(list.scheme.nextUpdate()).append('\n');
        for (Service s : list.services()) { out.append("\n").append(s.providerName()).append(" / ").append(s.serviceName())
                .append("\n  type: ").append(s.typeIdentifier()).append("\n  status: ").append(s.status())
                .append("\n  status since: ").append(s.statusStartingTime()).append('\n'); for (String q : s.qualifiers()) out.append("  qualifier: ").append(q).append('\n'); }
        out.append("\nsignature: not verified (TS 119 602 requires JAdES; no JAdES validation policy is configured locally)\n"); return out.toString();
    }
    private static JsonArray optionalArray(JsonObject o,String n){ return o.has(n)&&o.get(n).isJsonArray()?o.getAsJsonArray(n):null; }
    private static String optionalString(JsonObject o,String n){ return o.has(n)&&o.get(n).isJsonPrimitive()&&o.get(n).getAsJsonPrimitive().isString()?o.get(n).getAsString():null; }
    private static int requiredInt(JsonObject o,String n){ try{return o.get(n).getAsInt();}catch(Exception e){throw new IllegalArgumentException("Invalid TS 119 602 JSON: missing required integer '"+n+"'");} }
    private static long requiredLong(JsonObject o,String n){ try{return o.get(n).getAsLong();}catch(Exception e){throw new IllegalArgumentException("Invalid TS 119 602 JSON: missing required integer '"+n+"'");} }
    private static String firstValue(JsonArray values){ return values.get(0).getAsJsonObject().get("value").getAsString(); }
    private static List<String> extensions(JsonElement e){ if(e==null||!e.isJsonArray()) return List.of(); List<String> r=new ArrayList<>(); for(JsonElement x:e.getAsJsonArray())r.add(x.toString()); return List.copyOf(r); }
}
