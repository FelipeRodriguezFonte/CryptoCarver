package com.cryptocarver.crypto;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class TrustedEntityListJsonInspectorTest {
    private static final String LIST = """
      {"LoTE":{"ListAndSchemeInformation":{"LoTEVersionIdentifier":1,"LoTESequenceNumber":7,"SchemeOperatorName":[{"lang":"en","value":"Example authority"}],"ListIssueDateTime":"2025-11-01T00:00:00Z","NextUpdate":"2025-12-01T00:00:00Z"},"TrustedEntitiesList":[{"TrustedEntityInformation":{"TEName":[{"lang":"en","value":"Example provider"}],"TEAddress":{"TEPostalAddress":[{"lang":"en","Country":"ES"}],"TEElectronicAddress":["mailto:contact@example.test"]},"TEInformationURI":[{"lang":"en","uriValue":"https://example.test"}]},"TrustedEntityServices":[{"ServiceInformation":{"ServiceName":[{"lang":"en","value":"Wallet service"}],"ServiceDigitalIdentity":[{"OtherId":"provider-1"}],"ServiceTypeIdentifier":"https://example.test/type","ServiceStatus":"https://example.test/granted","StatusStartingTime":"2025-11-01T00:00:00Z","ServiceInformationExtensions":[{"qualifier":"example"}]}}]}]}}""";
    @Test void readsSchemeProviderServiceStatusAndQualifiers() {
        var list = TrustedEntityListJsonInspector.parse(LIST.getBytes(StandardCharsets.UTF_8));
        assertEquals("Example authority", list.scheme().operatorName()); assertEquals(7, list.scheme().sequenceNumber());
        var service = list.services().get(0); assertEquals("Example provider", service.providerName()); assertEquals("https://example.test/granted", service.status());
        assertEquals("{\"qualifier\":\"example\"}", service.qualifiers().get(0));
    }
    @Test void describesAndMarksJadesSignatureAsNotVerified() {
        String report = TrustedEntityListJsonInspector.describe(LIST.getBytes(StandardCharsets.UTF_8), Locale.ENGLISH);
        assertTrue(report.contains("Wallet service")); assertTrue(report.contains("signature: not verified"));
    }
    @Test void rejectsMalformedListWithClearMessage() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> TrustedEntityListJsonInspector.parse("{\"LoTE\":{}}".getBytes(StandardCharsets.UTF_8)));
        assertTrue(error.getMessage().contains("ListAndSchemeInformation"));
    }
    @Test void rejectsProviderMissingTheRequiredAddress() {
        String malformed = LIST.replace("\"TEAddress\":{\"TEPostalAddress\":[{\"lang\":\"en\",\"Country\":\"ES\"}],\"TEElectronicAddress\":[\"mailto:contact@example.test\"]},", "");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> TrustedEntityListJsonInspector.parse(malformed.getBytes(StandardCharsets.UTF_8)));
        assertTrue(error.getMessage().contains("TEAddress"));
    }
}
