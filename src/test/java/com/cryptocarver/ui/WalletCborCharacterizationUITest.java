package com.cryptocarver.ui;

import com.cryptocarver.crypto.CborInspector;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class WalletCborCharacterizationUITest extends WalletRemainingCharacterizationSupport {
    private static final String GOLD = "a2e1669cb0691e6038045a4e1792c422f8c6cb49312fe269bb9cea0b16e9a95a";
    private static final String SAMPLE = "a26161016162820203";

    @Test void inspectsAndConvertsLocalCbor() throws Exception {
        withWallet(() -> {
            shell.navigateTo("CBOR Inspector");
            replaceReporter();
            List<String> transcript = new ArrayList<>();
            assertEquals(List.of("tree", "diagnostic", "summary"), combo("cborViewCombo").getItems());
            forEachLanguageAndProfile(transcript, lines -> {
                put("cborInputArea", "");
                lines.add(observe("handleCborInspect", "cborOutputArea"));
                lines.add(observe("handleCborToJson", "cborOutputArea"));
                put("cborJsonArea", "");
                lines.add(observe("handleCborFromJson", "cborFromJsonOutputArea"));

                put("cborInputArea", SAMPLE);
                byte[] cbor = CborInspector.parseHex(SAMPLE);
                for (String view : List.of("tree", "diagnostic", "summary")) {
                    combo("cborViewCombo").setValue(view);
                    lines.add(observe("handleCborInspect", "cborOutputArea"));
                    String expected = switch (view) {
                        case "diagnostic" -> CborInspector.diagnostic(cbor);
                        case "summary" -> CborInspector.summary(cbor);
                        default -> CborInspector.tree(cbor);
                    };
                    assertEquals(expected, text("cborOutputArea"), view);
                    lines.add(view + "|" + text("cborOutputArea"));
                }
                combo("cborViewCombo").setValue(null);
                lines.add(observe("handleCborInspect", "cborOutputArea"));
                assertEquals(CborInspector.tree(cbor), text("cborOutputArea"));
                combo("cborViewCombo").setValue("tree");

                lines.add(observe("handleCborToJson", "cborOutputArea"));
                assertEquals(CborInspector.toJson(cbor), text("cborOutputArea"));
                lines.add("json|" + text("cborOutputArea"));

                put("cborJsonArea", "{\"a\": 1, \"b\": [2, 3]}");
                lines.add(observe("handleCborFromJson", "cborFromJsonOutputArea"));
                lines.add("hex|" + text("cborFromJsonOutputArea"));
                assertEquals(SAMPLE.toUpperCase(), text("cborFromJsonOutputArea"));

                put("cborInputArea", "zz-not-hex");
                lines.add(observe("handleCborInspect", "cborOutputArea"));
                lines.add(observe("handleCborToJson", "cborOutputArea"));
                put("cborJsonArea", "{not json");
                lines.add(observe("handleCborFromJson", "cborFromJsonOutputArea"));
            });
            pinTranscript("wallet-5", GOLD, transcript);
        });
    }
}
