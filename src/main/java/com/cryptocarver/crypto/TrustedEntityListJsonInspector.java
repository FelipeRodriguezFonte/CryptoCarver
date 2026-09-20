package com.cryptocarver.crypto;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Local reader for the JSON binding in ETSI TS 119 602 V1.1.1, Annex A. */
public final class TrustedEntityListJsonInspector {
    public record Service(String providerName, String serviceName, String typeIdentifier,
                          String status, String statusStartingTime, List<String> qualifiers) { }
    public record SchemeInformation(int version, long sequenceNumber, String operatorName,
                                    String issueDate, String nextUpdate) { }
    public record TrustedEntityList(SchemeInformation scheme, List<Service> services,
                                    boolean signaturePresent) { }
    private TrustedEntityListJsonInspector() { }

    public static TrustedEntityList parse(byte[] json) {
        try {
            JsonObject root = JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
            only(root, "LoTE"); JsonObject lote = requiredObject(root, "LoTE");
            only(lote, "ListAndSchemeInformation", "TrustedEntitiesList");
            JsonObject info = requiredObject(lote, "ListAndSchemeInformation");
            only(info, "LoTEVersionIdentifier", "LoTESequenceNumber", "LoTEType", "SchemeOperatorName",
                    "SchemeOperatorAddress", "SchemeName", "SchemeInformationURI", "StatusDeterminationApproach",
                    "SchemeTypeCommunityRules", "SchemeTerritory", "PolicyOrLegalNotice", "HistoricalInformationPeriod",
                    "PointersToOtherLoTE", "ListIssueDateTime", "NextUpdate", "DistributionPoints", "SchemeExtensions");
            SchemeInformation scheme = new SchemeInformation(requiredInt(info, "LoTEVersionIdentifier"),
                    requiredLong(info, "LoTESequenceNumber"), firstValue(requiredArray(info, "SchemeOperatorName")),
                    requiredString(info, "ListIssueDateTime"), requiredString(info, "NextUpdate"));
            List<Service> services = new ArrayList<>();
            JsonArray entities = optionalArray(lote, "TrustedEntitiesList");
            if (entities != null) for (JsonElement entityElement : entities) {
                JsonObject entity = object(entityElement, "TrustedEntitiesList item");
                only(entity, "TrustedEntityInformation", "TrustedEntityServices");
                JsonObject entityInfo = requiredObject(entity, "TrustedEntityInformation");
                String provider = firstValue(requiredArray(entityInfo, "TEName"));
                for (JsonElement serviceElement : requiredArray(entity, "TrustedEntityServices")) {
                    JsonObject service = object(serviceElement, "TrustedEntityServices item"); only(service, "ServiceInformation", "ServiceHistory");
                    JsonObject si = requiredObject(service, "ServiceInformation");
                    String name = firstValue(requiredArray(si, "ServiceName"));
                    requiredArray(si, "ServiceDigitalIdentity");
                    services.add(new Service(provider, name, optionalString(si, "ServiceTypeIdentifier"),
                            optionalString(si, "ServiceStatus"), optionalString(si, "StatusStartingTime"),
                            extensions(si.get("ServiceInformationExtensions"))));
                }
            }
            return new TrustedEntityList(scheme, List.copyOf(services), false);
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("Invalid TS 119 602 JSON: " + e.getMessage(), e); }
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
    private static void only(JsonObject o, String... names) { for (String key : o.keySet()) { boolean found=false; for(String n:names) if(n.equals(key)) found=true; if(!found) throw new IllegalArgumentException("Invalid TS 119 602 JSON: unexpected field '"+key+"'"); } }
    private static JsonObject requiredObject(JsonObject o,String n){ if(!o.has(n)) throw new IllegalArgumentException("Invalid TS 119 602 JSON: missing required field '"+n+"'"); return object(o.get(n),n); }
    private static JsonObject object(JsonElement e,String n){ if(e==null||!e.isJsonObject()) throw new IllegalArgumentException("Invalid TS 119 602 JSON: '"+n+"' must be an object"); return e.getAsJsonObject(); }
    private static JsonArray requiredArray(JsonObject o,String n){ JsonArray a=optionalArray(o,n); if(a==null||a.isEmpty()) throw new IllegalArgumentException("Invalid TS 119 602 JSON: missing or empty required field '"+n+"'"); return a; }
    private static JsonArray optionalArray(JsonObject o,String n){ return o.has(n)&&o.get(n).isJsonArray()?o.getAsJsonArray(n):null; }
    private static String requiredString(JsonObject o,String n){ String s=optionalString(o,n); if(s==null||s.isBlank()) throw new IllegalArgumentException("Invalid TS 119 602 JSON: missing required field '"+n+"'"); return s; }
    private static String optionalString(JsonObject o,String n){ return o.has(n)&&o.get(n).isJsonPrimitive()&&o.get(n).getAsJsonPrimitive().isString()?o.get(n).getAsString():null; }
    private static int requiredInt(JsonObject o,String n){ try{return o.get(n).getAsInt();}catch(Exception e){throw new IllegalArgumentException("Invalid TS 119 602 JSON: missing required integer '"+n+"'");} }
    private static long requiredLong(JsonObject o,String n){ try{return o.get(n).getAsLong();}catch(Exception e){throw new IllegalArgumentException("Invalid TS 119 602 JSON: missing required integer '"+n+"'");} }
    private static String firstValue(JsonArray a){ JsonObject v=object(a.get(0),"multilingual string"); String s=optionalString(v,"value"); if(s==null) throw new IllegalArgumentException("Invalid TS 119 602 JSON: multilingual string requires 'value'"); return s; }
    private static List<String> extensions(JsonElement e){ if(e==null||!e.isJsonArray()) return List.of(); List<String> r=new ArrayList<>(); for(JsonElement x:e.getAsJsonArray())r.add(x.toString()); return List.copyOf(r); }
}
