package com.cryptocarver.crypto;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class TrustedEntityListJsonInspectorTest {
    private static final String LIST = """
      {"LoTE":{"ListAndSchemeInformation":{"LoTEVersionIdentifier":1,"LoTESequenceNumber":7,"SchemeOperatorName":[{"lang":"en","value":"Example authority"}],"ListIssueDateTime":"2025-11-01T00:00:00Z","NextUpdate":"2025-12-01T00:00:00Z"},"TrustedEntitiesList":[{"TrustedEntityInformation":{"TEName":[{"lang":"en","value":"Example provider"}],"TEAddress":{"TEPostalAddress":[{"lang":"en","StreetAddress":"1 Example Street","Locality":"Madrid","Country":"ES"}],"TEElectronicAddress":[{"lang":"en","uriValue":"mailto:contact@example.test"}]},"TEInformationURI":[{"lang":"en","uriValue":"https://example.test"}]},"TrustedEntityServices":[{"ServiceInformation":{"ServiceName":[{"lang":"en","value":"Wallet service"}],"ServiceDigitalIdentity":{"OtherIds":[{"OtherId":"provider-1"}]},"ServiceTypeIdentifier":"https://example.test/type","ServiceStatus":"https://example.test/granted","StatusStartingTime":"2025-11-01T00:00:00Z","ServiceInformationExtensions":[{"qualifier":"example"}]}}]}]}}""";
    @Test void readsSchemeProviderServiceStatusAndQualifiers() {
        var list = TrustedEntityListJsonInspector.parse(validJson().getBytes(StandardCharsets.UTF_8));
        assertEquals("Example authority", list.scheme().operatorName()); assertEquals(7, list.scheme().sequenceNumber());
        var service = list.services().get(0); assertEquals("Example provider", service.providerName()); assertEquals("https://example.test/granted", service.status());
        assertEquals("{\"qualifier\":\"example\"}", service.qualifiers().get(0));
    }
    @Test void describesAndMarksJadesSignatureAsNotVerified() {
        String report = TrustedEntityListJsonInspector.describe(validJson().getBytes(StandardCharsets.UTF_8), Locale.ENGLISH);
        assertTrue(report.contains("Wallet service")); assertTrue(report.contains("signature: not verified"));
    }
    @Test void rejectsMalformedListWithClearMessage() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> TrustedEntityListJsonInspector.parse("{\"LoTE\":{}}".getBytes(StandardCharsets.UTF_8)));
        assertTrue(error.getMessage().contains("ListAndSchemeInformation"));
    }
    @Test void rejectsProviderMissingTheRequiredAddress() {
        String malformed = validJson().replace("\"TEAddress\":{\"TEPostalAddress\":[{\"lang\":\"en\",\"StreetAddress\":\"1 Example Street\",\"Locality\":\"Madrid\",\"Country\":\"ES\"}],\"TEElectronicAddress\":[{\"lang\":\"en\",\"uriValue\":\"mailto:contact@example.test\"}]},", "");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> TrustedEntityListJsonInspector.parse(malformed.getBytes(StandardCharsets.UTF_8)));
        assertTrue(error.getMessage().contains("TEAddress"));
    }
    @Test void readsSeveralEntitiesAndServices() {
        JsonObject root = JsonParser.parseString(validJson()).getAsJsonObject();
        var entities = root.getAsJsonObject("LoTE").getAsJsonArray("TrustedEntitiesList");
        JsonObject first = entities.get(0).getAsJsonObject();
        first.getAsJsonArray("TrustedEntityServices").add(first.getAsJsonArray("TrustedEntityServices").get(0).deepCopy());
        JsonObject second = first.deepCopy();
        second.getAsJsonObject("TrustedEntityInformation").getAsJsonArray("TEName").get(0).getAsJsonObject().addProperty("value", "Second provider");
        entities.add(second);
        var list = TrustedEntityListJsonInspector.parse(root.toString().getBytes(StandardCharsets.UTF_8));
        assertEquals(4, list.services().size());
        assertEquals("Second provider", list.services().get(2).providerName());
    }
    @Test void acceptsListWithoutTrustedEntities() {
        String listOnly = validJson().replaceFirst(",\"TrustedEntitiesList\":\\[.*", "}}");
        assertTrue(TrustedEntityListJsonInspector.parse(listOnly.getBytes(StandardCharsets.UTF_8)).services().isEmpty());
    }
    @Test void rejectsInvalidNextUpdateWithItsPath() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                TrustedEntityListJsonInspector.parse(validJson().replace("2025-12-01T00:00:00Z", "not-a-date")
                        .getBytes(StandardCharsets.UTF_8)));
        assertTrue(error.getMessage().contains("NextUpdate"));
    }
    @Test void acceptsSchemaValidOptionalFields() {
        String withOptionals = validJson().replace("\"NextUpdate\":\"2025-12-01T00:00:00Z\"",
                "\"LoTEType\":\"https://example.test/type\",\"SchemeTerritory\":\"ES\",\"NextUpdate\":\"2025-12-01T00:00:00Z\"");
        assertDoesNotThrow(() -> TrustedEntityListJsonInspector.parse(withOptionals.getBytes(StandardCharsets.UTF_8)));
    }
    private static String validJson() {
        return LIST.replace("\"OtherIds\":[{\"OtherId\":\"provider-1\"}]", "\"OtherIds\":[\"provider-1\"]");
    }
}
