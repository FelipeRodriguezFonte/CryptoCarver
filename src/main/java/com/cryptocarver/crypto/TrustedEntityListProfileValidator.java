package com.cryptocarver.crypto;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.text.MessageFormat;
import java.util.Set;
import javax.naming.ldap.LdapName;

/**
 * Checks locally decidable profile requirements in ETSI TS 119 602 V1.1.1,
 * Tables D.1/D.3, E.1/E.3, F.1/F.3, G.1/G.3, H.1/H.3 and I.1/I.3.
 * Evidence requiring an official register, a publisher URI or a semantic
 * assessment of a certificate is deliberately outside this local check.
 */
public final class TrustedEntityListProfileValidator {
    private static final String ROOT = "http://uri.etsi.org/19602/";

    public record Assessment(String profile, boolean evaluated, boolean statusImpliedByListing,
                             List<String> unmetRequirements) { }

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
        return assess(validatedPayload, compactSignature, signed, Locale.ENGLISH);
    }

    public static Assessment assess(byte[] validatedPayload, boolean compactSignature, boolean signed, Locale locale) {
        JsonObject lote = JsonParser.parseString(new String(validatedPayload, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("LoTE");
        JsonObject info = lote.getAsJsonObject("ListAndSchemeInformation");
        String type = string(info, "LoTEType");
        Profile profile = Profile.fromType(type);
        if (profile == null) return new Assessment(type == null
                ? label(locale, "profileUnspecified") : label(locale, "profileUnrecognized", type),
                false, false, List.of());
        List<String> unmet = new ArrayList<>();
        String scheme = profile.clause + ".2";
        String servicesClause = profile.clause + ".3";
        if (info.get("LoTEVersionIdentifier").getAsInt() != 1)
            unmet.add(label(locale, "rule.version", scheme));
        if (!"EU".equals(string(info, "SchemeTerritory")))
            unmet.add(label(locale, "rule.territory", scheme));
        if (profile.statusRoot != null && !(ROOT + profile.statusRoot + "/StatusDetn/EU")
                .equals(string(info, "StatusDeterminationApproach")))
            unmet.add(label(locale, "rule.statusApproach", scheme));
        if (!hasUri(info.getAsJsonArray("SchemeTypeCommunityRules"), ROOT + profile.rulesRoot + "/schemerules/EU"))
            unmet.add(label(locale, "rule.schemeRules", scheme));
        if (info.getAsJsonArray("SchemeInformationURI") == null)
            unmet.add(label(locale, "rule.schemeInfoUri", scheme));
        if (profile == Profile.PUB_EAA) {
            if (intValue(info, "HistoricalInformationPeriod") != 65535)
                unmet.add(label(locale, "rule.historyPeriod65535", scheme));
            if (info.has("PointersToOtherLoTE"))
                unmet.add(label(locale, "rule.pointersAbsent", scheme));
        } else if (info.has("HistoricalInformationPeriod")) {
            unmet.add(label(locale, "rule.historyPeriodAbsent", scheme));
        }
        try {
            OffsetDateTime issued = OffsetDateTime.parse(string(info, "ListIssueDateTime"));
            OffsetDateTime next = OffsetDateTime.parse(string(info, "NextUpdate"));
            if (!next.isAfter(issued) || next.isAfter(issued.plusMonths(6)))
                unmet.add(label(locale, "rule.nextUpdate", scheme));
        } catch (RuntimeException e) {
            unmet.add(label(locale, "rule.invalidDates", scheme));
        }
        JsonArray entities = lote.getAsJsonArray("TrustedEntitiesList");
        if (entities != null) for (JsonElement entity : entities) {
            JsonObject providerInfo = entity.getAsJsonObject().getAsJsonObject("TrustedEntityInformation");
            String provider = firstValue(providerInfo.getAsJsonArray("TEName"));
            JsonArray electronicAddresses = providerInfo.getAsJsonObject("TEAddress")
                    .getAsJsonArray("TEElectronicAddress");
            if (!hasUriPrefix(electronicAddresses, "mailto:") || !hasUriPrefix(electronicAddresses, "tel:"))
                unmet.add(label(locale, "rule.contact", servicesClause + " " + provider + ": "));
            for (JsonElement serviceElement : entity.getAsJsonObject().getAsJsonArray("TrustedEntityServices")) {
                JsonObject service = serviceElement.getAsJsonObject().getAsJsonObject("ServiceInformation");
                String name = firstValue(service.getAsJsonArray("ServiceName"));
                String prefix = servicesClause + " " + provider + " / " + name + ": ";
                String kind = string(service, "ServiceTypeIdentifier");
                Set<String> allowed = profile == Profile.REGISTRARS
                        ? Set.of(ROOT + "SvcType/Register")
                        : Set.of(ROOT + "SvcType/" + profile.serviceRoot + "/Issuance",
                                 ROOT + "SvcType/" + profile.serviceRoot + "/Revocation");
                if (kind == null || !allowed.contains(kind)) unmet.add(label(locale, "rule.serviceType", prefix));
                if (profile != Profile.PUB_EAA) {
                    JsonObject identity = service.getAsJsonObject("ServiceDigitalIdentity");
                    if (identity.getAsJsonArray("X509Certificates") == null)
                        unmet.add(label(locale, "rule.identityCerts", prefix));
                    if (service.has("ServiceStatus")) unmet.add(label(locale, "rule.statusAbsent", prefix));
                    if (service.has("StatusStartingTime")) unmet.add(label(locale, "rule.statusSinceAbsent", prefix));
                } else {
                    String status = string(service, "ServiceStatus");
                    if (status == null || !Set.of(ROOT + "PubEAAProvidersList/SvcStatus/notified",
                            ROOT + "PubEAAProvidersList/SvcStatus/withdrawn").contains(status))
                        unmet.add(label(locale, "rule.publicEaaStatus", prefix));
                    JsonArray certificates = service.getAsJsonObject("ServiceDigitalIdentity")
                            .getAsJsonArray("X509Certificates");
                    checkPublicEaaCertificates(certificates, provider, prefix, locale, unmet);
                    String since = string(service, "StatusStartingTime");
                    if (since != null && OffsetDateTime.parse(since).isBefore(
                            OffsetDateTime.parse(string(info, "ListIssueDateTime"))))
                        unmet.add(label(locale, "rule.publicEaaStatusSince", prefix));
                }
                if (profile == Profile.REGISTRARS && service.getAsJsonArray("ServiceSupplyPoints") == null)
                    unmet.add(label(locale, "rule.supplyPoint", prefix));
                if (profile == Profile.PUB_EAA) {
                    JsonArray history = serviceElement.getAsJsonObject().getAsJsonArray("ServiceHistory");
                    if (history != null) for (JsonElement historyElement : history) {
                        JsonObject historicalIdentity = historyElement.getAsJsonObject()
                                .getAsJsonObject("ServiceDigitalIdentity");
                        if (historicalIdentity.getAsJsonArray("X509SKIs") == null)
                            unmet.add(label(locale, "rule.historySki", prefix));
                        if (historicalIdentity.has("X509Certificates"))
                            unmet.add(label(locale, "rule.historyCertAbsent", prefix));
                    }
                }
            }
        }
        if (!signed) unmet.add(label(locale, "rule.signatureRequired", profile.clause + ".4"));
        else if (!compactSignature) unmet.add(label(locale, "rule.compactRequired", profile.clause + ".4"));
        return new Assessment(label(locale, "profileAnnex", profile.type, profile.clause),
                true, profile != Profile.PUB_EAA, List.copyOf(unmet));
    }

    private static String label(Locale locale, String key, Object... args) {
        Locale selected = locale == null ? Locale.ENGLISH : locale;
        String pattern = ResourceBundle.getBundle("i18n.messages", selected)
                .getString("module.wallet.ts119602." + key);
        return new MessageFormat(pattern, selected).format(args);
    }

    private static void checkPublicEaaCertificates(JsonArray certificates, String provider,
                                                    String prefix, Locale locale, List<String> unmet) {
        if (certificates == null) return;
        X509Certificate first = null;
        for (JsonElement value : certificates) {
            try {
                byte[] der = Base64.getDecoder().decode(value.getAsJsonObject().get("val").getAsString());
                X509Certificate current = TrustedEntityListJsonInspector.readCertificate(der);
                if (!provider.equals(organizationName(current)))
                    unmet.add(label(locale, "rule.publicEaaOrganization", prefix));
                if (first != null && (!Arrays.equals(first.getPublicKey().getEncoded(),
                        current.getPublicKey().getEncoded()) || !first.getSubjectX500Principal()
                        .equals(current.getSubjectX500Principal())))
                    unmet.add(label(locale, "rule.publicEaaCertConsistency", prefix));
                if (first == null) first = current;
            } catch (Exception e) {
                unmet.add(label(locale, "rule.publicEaaInvalidCertificate", prefix));
            }
        }
    }

    private static String organizationName(X509Certificate certificate) throws Exception {
        for (var part : new LdapName(certificate.getSubjectX500Principal().getName("RFC2253")).getRdns())
            if ("O".equalsIgnoreCase(part.getType())) return part.getValue().toString();
        return null;
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
    private static boolean hasUriPrefix(JsonArray values, String prefix) {
        if (values == null) return false;
        for (JsonElement value : values) {
            String uri = string(value.getAsJsonObject(), "uriValue");
            if (uri != null && uri.regionMatches(true, 0, prefix, 0, prefix.length())) return true;
        }
        return false;
    }
    private static String firstValue(JsonArray values) {
        return values.get(0).getAsJsonObject().get("value").getAsString();
    }
}
