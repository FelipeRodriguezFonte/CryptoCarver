package com.cryptocarver.crypto;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Checks locally decidable profile requirements in ETSI TS 119 602 V1.1.1,
 * Tables D.1/D.3, E.1/E.3, F.1/F.3, G.1/G.3, H.1/H.3 and I.1/I.3.
 * Evidence requiring an official register, a publisher URI or a semantic
 * assessment of a certificate is deliberately outside this local check.
 */
public final class TrustedEntityListProfileValidator {
    private static final String ROOT = "http://uri.etsi.org/19602/";

    public record Assessment(String profile, List<String> unmetRequirements) { }

    private enum Profile {
        PID("EUPIDProvidersList", "D", "PIDProvidersList", "PIDProviders", "PID"),
        WALLET("EUWalletProvidersList", "E", "WalletProvidersList", "WalletProvidersList", "WalletSolution"),
        WRPAC("EUWRPACProvidersList", "F", "WRPACProvidersList", "WRPACProvidersList", "WRPAC"),
        WRPRC("EUWRPRCProvidersList", "G", null, "WRPRCProvidersList", "WRPRC"),
        PUB_EAA("EUPubEAAProvidersList", "H", "PubEAAProvidersList", "PubEAAProvidersList", "PubEAA"),
        REGISTRARS("EURegistrarsAndRegistersList", "I", "RegistrarsAndRegistersList", "RegistrarsAndRegistersList", "Register");

        final String type, clause, statusRoot, rulesRoot, serviceRoot;
        Profile(String type, String clause, String statusRoot, String rulesRoot, String serviceRoot) {
            this.type = type;
            this.clause = clause;
            this.statusRoot = statusRoot;
            this.rulesRoot = rulesRoot;
            this.serviceRoot = serviceRoot;
        }
        static Profile fromType(String uri) {
            for (Profile profile : values()) if ((ROOT + "LoTEType/" + profile.type).equals(uri)) return profile;
            return null;
        }
    }

    private TrustedEntityListProfileValidator() { }

    public static Assessment assess(byte[] validatedPayload, boolean compactSignature, boolean signed) {
        JsonObject lote = JsonParser.parseString(new String(validatedPayload, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("LoTE");
        JsonObject info = lote.getAsJsonObject("ListAndSchemeInformation");
        String type = string(info, "LoTEType");
        Profile profile = Profile.fromType(type);
        if (profile == null) return new Assessment(type == null ? "unspecified" : "unrecognized (" + type + ")", List.of());
        List<String> unmet = new ArrayList<>();
        String scheme = profile.clause + ".2";
        String servicesClause = profile.clause + ".3";
        if (info.get("LoTEVersionIdentifier").getAsInt() != 1)
            unmet.add(scheme + ": LoTEVersionIdentifier must be 1");
        if (!"EU".equals(string(info, "SchemeTerritory")))
            unmet.add(scheme + ": SchemeTerritory must be EU");
        if (profile.statusRoot != null && !(ROOT + profile.statusRoot + "/StatusDetn/EU")
                .equals(string(info, "StatusDeterminationApproach")))
            unmet.add(scheme + ": StatusDeterminationApproach has the wrong URI");
        if (!hasUri(info.getAsJsonArray("SchemeTypeCommunityRules"), ROOT + profile.rulesRoot + "/schemerules/EU"))
            unmet.add(scheme + ": SchemeTypeCommunityRules lacks the required URI");
        if (profile == Profile.PUB_EAA) {
            if (intValue(info, "HistoricalInformationPeriod") != 65535)
                unmet.add(scheme + ": HistoricalInformationPeriod must be 65535");
            if (info.has("PointersToOtherLoTE"))
                unmet.add(scheme + ": PointersToOtherLoTE must be absent");
        } else if (info.has("HistoricalInformationPeriod")) {
            unmet.add(scheme + ": HistoricalInformationPeriod must be absent");
        }
        try {
            OffsetDateTime issued = OffsetDateTime.parse(string(info, "ListIssueDateTime"));
            OffsetDateTime next = OffsetDateTime.parse(string(info, "NextUpdate"));
            if (!next.isAfter(issued) || next.isAfter(issued.plusMonths(6)))
                unmet.add(scheme + ": NextUpdate must be within six months after ListIssueDateTime");
        } catch (RuntimeException e) {
            unmet.add(scheme + ": invalid ListIssueDateTime or NextUpdate");
        }
        JsonArray entities = lote.getAsJsonArray("TrustedEntitiesList");
        if (entities != null) for (JsonElement entity : entities) {
            String provider = firstValue(entity.getAsJsonObject().getAsJsonObject("TrustedEntityInformation").getAsJsonArray("TEName"));
            for (JsonElement serviceElement : entity.getAsJsonObject().getAsJsonArray("TrustedEntityServices")) {
                JsonObject service = serviceElement.getAsJsonObject().getAsJsonObject("ServiceInformation");
                String name = firstValue(service.getAsJsonArray("ServiceName"));
                String prefix = servicesClause + " " + provider + " / " + name + ": ";
                String kind = string(service, "ServiceTypeIdentifier");
                Set<String> allowed = profile == Profile.REGISTRARS
                        ? Set.of(ROOT + "SvcType/Register")
                        : Set.of(ROOT + "SvcType/" + profile.serviceRoot + "/Issuance",
                                 ROOT + "SvcType/" + profile.serviceRoot + "/Revocation");
                if (kind == null || !allowed.contains(kind)) unmet.add(prefix + "ServiceTypeIdentifier is not permitted");
                if (profile != Profile.PUB_EAA) {
                    JsonObject identity = service.getAsJsonObject("ServiceDigitalIdentity");
                    if (identity.getAsJsonArray("X509Certificates") == null)
                        unmet.add(prefix + "ServiceDigitalIdentity needs X509Certificates");
                    if (service.has("ServiceStatus")) unmet.add(prefix + "ServiceStatus must be absent");
                    if (service.has("StatusStartingTime")) unmet.add(prefix + "StatusStartingTime must be absent");
                } else {
                    String status = string(service, "ServiceStatus");
                    if (status == null || !Set.of(ROOT + "PubEAAProvidersList/SvcStatus/notified",
                            ROOT + "PubEAAProvidersList/SvcStatus/withdrawn").contains(status))
                        unmet.add(prefix + "ServiceStatus must be notified or withdrawn");
                }
                if (profile == Profile.REGISTRARS && service.getAsJsonArray("ServiceSupplyPoints") == null)
                    unmet.add(prefix + "ServiceSupplyPoints is required");
            }
        }
        if (!signed) unmet.add(profile.clause + ".4: compact JAdES Baseline B signature is required");
        else if (!compactSignature) unmet.add(profile.clause + ".4: JAdES must use compact serialization");
        return new Assessment(profile.type + " (Annex " + profile.clause + ")", List.copyOf(unmet));
    }

    private static String string(JsonObject object, String key) {
        return object != null && object.has(key) ? object.get(key).getAsString() : null;
    }
    private static int intValue(JsonObject object, String key) {
        return object != null && object.has(key) ? object.get(key).getAsInt() : -1;
    }
    private static boolean hasUri(JsonArray values, String uri) {
        if (values == null) return false;
        for (JsonElement value : values) if (uri.equals(string(value.getAsJsonObject(), "uriValue"))) return true;
        return false;
    }
    private static String firstValue(JsonArray values) {
        return values.get(0).getAsJsonObject().get("value").getAsString();
    }
}
