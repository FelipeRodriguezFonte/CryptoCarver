package com.cryptocarver.ui;

import com.cryptocarver.crypto.JweComposer;
import com.cryptocarver.crypto.JOSEService;
import com.cryptocarver.crypto.SignerConfig;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import static com.cryptocarver.ui.JoseCharacterizationSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named="runUiTests", matches="true")
class JoseInspectorCharacterizationUITest {
    @BeforeAll static void start() throws Exception { startFx(); }

    @Test void compactJsonNestedMalformedAndPrivacyProfiles() throws Exception {
        UiTestLifecycleExtension.onFx(() -> {
            try (var p = new JoseCharacterizationSupport()) {
                p.language(LanguagePreference.EN);
                String compactJwt = signed(PAYLOAD);
                String generalJws = JOSEService.generateSignedJWT(PAYLOAD, List.of(
                        new SignerConfig("HS256", KEY), new SignerConfig("HS512", KEY + KEY)), "General JSON", false);
                String compactJwe = JweComposer.encrypt(PAYLOAD, "dir", "A256GCM", false,
                        JweComposer.HeaderOptions.none(), KEY, UTF8, 1000);
                String flattenedJwe = JweComposer.encrypt(PAYLOAD, "dir", "A256GCM", false,
                        JweComposer.HeaderOptions.none(), KEY, UTF8, 1000,
                        JweComposer.Serialization.FLATTENED, null);
                String generalJwe = JweComposer.encrypt(PAYLOAD, "dir", "A256GCM", false,
                        JweComposer.HeaderOptions.none(), KEY, UTF8, 1000,
                        JweComposer.Serialization.GENERAL, null);
                String nested = signed(compactJwt);

                String jwtReport = inspect(p, compactJwt);
                assertTrue(jwtReport.contains("[JWS Detected]"));
                assertTrue(jwtReport.contains("\"sub\""));
                p.line("jwt_compact", "JWS/header/payload/signature");

                String jwsReport = inspect(p, generalJws);
                assertTrue(jwsReport.contains("[JWS JSON Detected — General]"));
                assertTrue(jwsReport.contains("SIGNATURE 2 PROTECTED HEADER"));
                p.line("jws_json_general", "2-signatures/protected-headers/payload");

                for (var item : List.of(new String[]{"compact", compactJwe, "[JWE Detected]"},
                        new String[]{"flattened", flattenedJwe, "[JWE JSON Detected — Flattened]"},
                        new String[]{"general", generalJwe, "[JWE JSON Detected — General]"})) {
                    String report = inspect(p, item[1]);
                    assertTrue(report.contains(item[2]), item[0]);
                    assertTrue(report.contains("=== CIPHERTEXT ==="), item[0]);
                    p.line("jwe_" + item[0], "header/ciphertext/tag;random-fields-normalized");
                }

                String nestedReport = inspect(p, nested);
                assertTrue(nestedReport.contains("NESTED TOKEN IN PAYLOAD"));
                assertTrue(nestedReport.contains("  ↳ [JWS Detected]"));
                p.line("nested_jwt", "outer-and-inner-jws-detected");

                String rawReport = inspect(p, "invented-raw-input");
                assertTrue(rawReport.contains("Unknown/Raw Data:"));
                String malformedJson = inspect(p, "{\"ciphertext\":");
                assertTrue(malformedJson.contains("Error:"));
                String malformedCompact = inspect(p, "a.b.c");
                assertTrue(malformedCompact.contains("[JWS Detected]"));
                p.line("malformed", "raw-fallback/json-error/compact-detected;provider-text-omitted");

                for (LanguagePreference language : List.of(LanguagePreference.EN, LanguagePreference.ES)) {
                    p.language(language);
                    p.line(language + "_security_warning", "existing capability characterization covers visible localized warnings");
                }
                OperationResult privacy = OperationResult.forOperation("JOSE Inspector")
                        .input(KEY.getBytes(StandardCharsets.UTF_8))
                        .output(PAYLOAD.getBytes(StandardCharsets.UTF_8), OperationDetail.Classification.SECRET)
                        .status("Inspector report ready").build();
                p.privacy(privacy, KEY, PAYLOAD);
                p.digest("89c07b84cea4a9a478881fd369a1b47796a4d7a5d2008165cddf341518c261da");
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    private static String signed(String payload) throws Exception {
        JWSObject object = new JWSObject(new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(),
                new Payload(payload));
        object.sign(new MACSigner(KEY));
        return object.serialize();
    }

    private static String inspect(JoseCharacterizationSupport fixture, String token) {
        TextFlow flow = fixture.control("inspectorOutputFlow");
        fixture.controller.inspectToken(token, flow);
        return flow.getChildren().stream().map(Text.class::cast).map(Text::getText).collect(Collectors.joining());
    }
}
