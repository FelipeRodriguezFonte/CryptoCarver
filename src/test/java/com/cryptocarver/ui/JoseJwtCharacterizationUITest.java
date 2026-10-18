package com.cryptocarver.ui;

import com.cryptocarver.crypto.*;
import com.cryptocarver.model.*;
import com.cryptocarver.service.I18nService;
import javafx.scene.control.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static com.cryptocarver.ui.JoseCharacterizationSupport.*;

@Tag("ui")
@EnabledIfSystemProperty(named="runUiTests", matches="true")
class JoseJwtCharacterizationUITest {
    @BeforeAll static void start() throws Exception { startFx(); }
    @Test void jwtJwsDetachedNestedClaimsErrorsAndPrivacy() throws Exception {
        UiTestLifecycleExtension.onFx(() -> {
            try (var p = new JoseCharacterizationSupport()) {
                p.language(LanguagePreference.EN);
                p.area("jwtPayloadArea").setText(PAYLOAD);
                p.area("jwtKeyArea").setText(KEY);
                p.combo("jwtAlgoCombo").setValue("HS256");
                for (String serialization : List.of("Compact", "Flattened JSON", "General JSON")) {
                    p.combo("jwsSerializationCombo").setValue(serialization);
                    p.invoke("handleGenerateSignedJWT");
                    String signed = p.area("jwtOutputArea").getText();
                    assertFalse(signed.isBlank(), p.reporter.error);
                    if (serialization.equals("Compact")) assertEquals(PAYLOAD, JOSEService.verifyJws(signed, "HS256", KEY));
                    else assertTrue(JOSEService.verifyDetachedJWS(signed, PAYLOAD, "HS256", KEY, UTF8));
                    p.published("signed_"+serialization);
                    p.privacy(p.reporter.result, KEY);
                    p.area("detachedPayloadArea").setText(PAYLOAD);
                    p.area("detachedSigningKeyArea").setText(KEY);
                    p.combo("detachedAlgoCombo").setValue("HS256");
                    p.combo("detachedSerializationCombo").setValue(serialization);
                    for (boolean unencoded : List.of(false, true)) {
                        p.check("detachedUnencodedCheck").setSelected(unencoded);
                        p.invoke("handleGenerateDetachedJWS");
                        p.area("detachedVerificationKeyArea").setText(KEY);
                        p.invoke("handleVerifyDetachedJWS");
                        assertEquals("VALID DETACHED SIGNATURE", p.label("detachedStatusLabel").getText());
                        p.line("detached_"+serialization+"_b64="+!unencoded, p.label("detachedStatusLabel").getText());
                    }
                }
                p.combo("jwsSerializationCombo").setValue("General JSON");
                p.area("jwtKeyArea2").setText(KEY+KEY);
                p.combo("jwtAlgo2Combo").setValue("HS512");
                p.invoke("handleGenerateSignedJWT");
                assertTrue(p.area("jwtOutputArea").getText().contains("signatures"));
                p.published("multiple_signers");
                p.area("jwtKeyArea2").clear();
                p.combo("jwsSerializationCombo").setValue("Compact");
                p.invoke("handleGenerateSignedJWT");
                p.area("jwtValidateTokenArea").setText(p.area("jwtOutputArea").getText());
                p.area("jwtValidateKeyArea").setText(KEY);
                p.invoke("handleValidateJWT");
                assertTrue(p.label("jwtStatusLabel").getStyle().contains("green"));
                p.published("validated");
                p.privacy(p.reporter.result, KEY);
                ((TextField)p.control("jwtAllowedAlgorithmsField")).setText("RS256");
                p.invoke("handleValidateJWT");
                assertTrue(p.label("jwtStatusLabel").getStyle().contains("red"));
                p.line("allowed_algorithms", p.area("jwtFindingsArea").getText());
                ((TextField)p.control("jwtAllowedAlgorithmsField")).clear();
                p.check("jwtRfc9068Check").setSelected(true);
                p.invoke("handleValidateJWT");
                assertTrue(p.label("jwtStatusLabel").getStyle().contains("orange"));
                p.line("rfc9068", p.area("jwtFindingsArea").getText());
                p.check("jwtRfc9068Check").setSelected(false);
                p.check("jwtOidcStrictCheck").setSelected(true);
                ((TextField)p.control("jwtExpectedNonceField")).setText("invented-nonce");
                p.invoke("handleValidateJWT");
                assertTrue(p.label("jwtStatusLabel").getStyle().contains("orange"));
                p.line("oidc", p.area("jwtFindingsArea").getText());
                p.check("jwtOidcStrictCheck").setSelected(false);
                ((TextField)p.control("jwtExpectedNonceField")).clear();
                p.area("jwtProtectedHeaderArea").setText("{\"crit\":[\"toy\"],\"toy\":true}");
                p.invoke("handleGenerateSignedJWT");
                assertEquals("Protected header JSON cannot override 'crit'.", p.reporter.error);
                // Rejected generation retains the previous token; real crit is tested separately below.
                p.area("jwtValidateTokenArea").setText(p.area("jwtOutputArea").getText());
                p.check("jwtIgnoreCritCheck").setSelected(true);
                p.invoke("handleValidateJWT");
                p.line("crit_ignore", p.area("jwtFindingsArea").getText());
                p.check("jwtIgnoreCritCheck").setSelected(false);
                ((TextField)p.control("jwtUnderstoodCritField")).setText("toy");
                p.invoke("handleValidateJWT");
                assertTrue(p.label("jwtStatusLabel").getStyle().contains("green"));
                p.line("crit_understood", p.label("jwtStatusLabel").getText());
                ((TextField)p.control("jwtUnderstoodCritField")).clear();
                p.area("jwtProtectedHeaderArea").clear();
                p.combo("jwtAlgoCombo").setValue("none");
                p.area("jwtKeyArea").clear();
                p.invoke("handleGenerateSignedJWT");
                p.area("jwtValidateTokenArea").setText(p.area("jwtOutputArea").getText());
                p.check("jwtAcceptNoneCheck").setSelected(true);
                p.invoke("handleValidateJWT");
                p.published("none_opt_in");
                p.controller.generateNestedJWT(PAYLOAD, "HS256", KEY, "dir", "A256GCM", KEY, false, UTF8, p.area("nestedOutputArea"));
                String nested = p.area("nestedOutputArea").getText();
                assertFalse(nested.isBlank(), p.reporter.error);
                p.controller.verifyNestedJWT(nested, KEY, KEY, UTF8, p.area("nestedPayloadOutputArea"), p.label("nestedStatusLabel"));
                assertEquals(PAYLOAD, p.area("nestedPayloadOutputArea").getText());
                p.published("nested_verified");
                p.privacy(p.reporter.result, KEY, PAYLOAD);
                p.area("jwtPayloadArea").setText("{}");
                ((TextField)p.control("jwtSubField")).setText("invented-claims");
                p.invoke("handleApplyJWTClaims");
                assertTrue(p.area("jwtPayloadArea").getText().contains("invented-claims"));
                p.line("claims", "sub/iat/exp applied;clock normalized");
                for (var language : List.of(LanguagePreference.EN, LanguagePreference.ES)) {
                    p.language(language);
                    p.area("jwtPayloadArea").clear();
                    p.invoke("handleGenerateSignedJWT");
                    assertNotNull(p.reporter.error);
                    p.line(language+"_empty", p.reporter.error);
                    p.combo("jwtAlgoCombo").setValue("none");
                    p.line(language+"_none_warning", p.label("jwtSecurityWarningLabel").getText());
                    p.combo("jwtAlgoCombo").setValue("HS256");
                    p.area("jwtKeyArea").setText("short");
                    p.line(language+"_short_warning", p.label("jwtSecurityWarningLabel").getText());
                    p.area("jwtValidateTokenArea").setText("malformed");
                    p.invoke("handleValidateJWT");
                    assertFalse(p.label("jwtStatusLabel").getText().isBlank());
                    p.line(language+"_invalid", "readable provider error normalized");
                }
                p.digest("526ed1435a5950126aae7065abbaeee3b6c9bb81c8cee77c3a4841e7e599537e");
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    @Test void decodeWithoutKeyIgnoresValidationSettingsAndNeverMarksTokenValid() throws Exception {
        UiTestLifecycleExtension.onFx(() -> {
            try (var p = new JoseCharacterizationSupport()) {
                String payload = "{\"sub\":\"untrusted-user\",\"exp\":1,\"iat\":9999999999}";
                String signed = JOSEService.signJws(payload, "HS256", KEY);
                // Change the payload without signing again: decoding must still work.
                String[] parts = signed.split("\\.");
                parts[1] = com.nimbusds.jose.util.Base64URL.encode(payload.replace("untrusted-user", "tampered-user")).toString();
                String tampered = String.join(".", parts);
                p.area("jwtValidateKeyArea").clear();
                ((TextField)p.control("jwtClockSkewField")).setText("invalid");
                ((TextField)p.control("jwtExpectedIssField")).setText("wrong-issuer");
                p.check("jwtOidcStrictCheck").setSelected(true);
                p.check("jwtTrustHeaderKeyCheck").setSelected(true);
                p.area("jwtTrustAnchorsArea").setText("invalid certificate");
                ((TextField)p.control("jwtCertificateDateField")).setText("invalid date");
                Button decode = p.control("jwtDecodeButton");
                for (var language : List.of(LanguagePreference.EN, LanguagePreference.ES)) {
                    p.language(language);
                    assertEquals(I18nService.getInstance().text("module.jose.decodeOnly"), decode.getText());
                    for (String token : List.of(tampered, com.nimbusds.jose.util.Base64URL.encode("{\"alg\":\"none\"}").toString() + "." + parts[1] + ".")) {
                        p.area("jwtValidateTokenArea").setText("  " + token + "  ");
                        p.area("jwtFindingsArea").setText("old finding");
                        p.label("jwtStatusLabel").setStyle("-fx-text-fill: green;");
                        decode.fire();
                        assertNull(p.reporter.error);
                        assertTrue(p.area("jwtDecodedHeaderArea").getText().contains("alg"));
                        assertTrue(p.area("jwtDecodedPayloadArea").getText().contains("tampered-user"));
                        assertEquals(I18nService.getInstance().text("module.jose.decodedUnverified"), p.label("jwtStatusLabel").getText());
                        assertTrue(p.label("jwtStatusLabel").getStyle().contains("orange"));
                        assertEquals(I18nService.getInstance().text("module.jose.decodeOnlyHint"), p.area("jwtFindingsArea").getText());
                        assertEquals("JWT Decode", p.reporter.result.getOperation());
                        assertTrue(p.reporter.result.getDetails().stream().filter(d -> d.name().equals("Signature") || d.name().equals("Claim Checks"))
                                .allMatch(d -> d.value().equals("NOT_CHECKED")));
                    }
                    int published = p.reporter.history.size();
                    for (String invalid : List.of("malformed", parts[0] + "." + com.nimbusds.jose.util.Base64URL.encode("not JSON") + ".AA")) {
                        p.area("jwtValidateTokenArea").setText(invalid);
                        decode.fire();
                        assertEquals(I18nService.getInstance().text("module.jose.decodeInvalid"), p.label("jwtStatusLabel").getText());
                        assertTrue(p.area("jwtDecodedHeaderArea").getText().isEmpty());
                        assertTrue(p.area("jwtDecodedPayloadArea").getText().isEmpty());
                        assertTrue(p.area("jwtFindingsArea").getText().isEmpty());
                    }
                    var jwe = new com.nimbusds.jose.JWEObject(new com.nimbusds.jose.JWEHeader(
                            com.nimbusds.jose.JWEAlgorithm.DIR, com.nimbusds.jose.EncryptionMethod.A256GCM), new com.nimbusds.jose.Payload(payload));
                    jwe.encrypt(new com.nimbusds.jose.crypto.DirectEncrypter(KEY.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    p.area("jwtValidateTokenArea").setText(jwe.serialize());
                    decode.fire();
                    assertEquals(I18nService.getInstance().text("module.jose.decodeEncrypted"), p.label("jwtStatusLabel").getText());
                    assertEquals(published, p.reporter.history.size());
                }
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    @Test void advancedOptionsRemainReadableInNarrowPanelsAndRejectInvalidClockSkew() throws Exception {
        UiTestLifecycleExtension.onFx(() -> {
            try (var p = new JoseCharacterizationSupport()) {
                p.controller.showSection("JWT (Signed)");
                javafx.scene.Parent root = p.loader.getRoot();
                var validationContent = p.area("jwtValidateTokenArea").getParent();
                var accordion = (Accordion)root.lookup(".accordion");
                TitledPane validationPane = accordion.getPanes().stream().filter(pane -> pane.getContent() == validationContent).findFirst().orElseThrow();
                validationPane.setAnimated(false);
                accordion.setExpandedPane(validationPane);
                var advancedContent = p.check("jwtCheckExpiryCheck").getParent();
                TitledPane advancedPane = (TitledPane)validationContent.lookupAll(".titled-pane").stream()
                        .filter(node -> ((TitledPane)node).getContent() == advancedContent).findFirst().orElseThrow();
                advancedPane.setAnimated(false);
                advancedPane.setExpanded(true);
                ScrollPane scroll = new ScrollPane(root);
                scroll.setFitToWidth(true);
                var stage = new javafx.stage.Stage();
                stage.setScene(new javafx.scene.Scene(scroll, 800, 900));
                stage.getScene().getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
                stage.show();
                try {
                    for (var language : List.of(LanguagePreference.EN, LanguagePreference.ES)) {
                        p.language(language);
                        for (int width : List.of(800, 600)) {
                            scroll.resize(width, 900);
                            scroll.applyCss(); scroll.layout(); root.layout();
                            double lastBottom = -1;
                            for (String id : List.of("jwtCheckExpiryCheck", "jwtOidcStrictCheck", "jwtAcceptNoneCheck", "jwtTrustHeaderKeyCheck")) {
                                CheckBox check = p.check(id);
                                assertFalse(check.getText().isBlank(), id);
                                var rendered = (javafx.scene.text.Text)check.lookup(".text");
                                assertEquals(check.getText(), rendered.getText(), language + "/" + width + "/" + id);
                                var bounds = check.localToScene(check.getBoundsInLocal());
                                assertTrue(bounds.getMinY() >= lastBottom, "Options must not overlap");
                                lastBottom = bounds.getMaxY();
                            }
                            TextField skew = p.control("jwtClockSkewField");
                            assertTrue(skew.getWidth() >= 80, "Clock skew must remain usable");
                        }
                    }
                    if (Boolean.getBoolean("captureJwtLayout")) {
                        var snapshot = advancedContent.snapshot(null, null);
                        var png = new java.awt.image.BufferedImage((int)snapshot.getWidth(), (int)snapshot.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        for (int y = 0; y < png.getHeight(); y++) for (int x = 0; x < png.getWidth(); x++) png.setRGB(x, y, snapshot.getPixelReader().getArgb(x, y));
                        var path = java.nio.file.Path.of("target/test-artifacts/jwt-advanced-es.png");
                        java.nio.file.Files.createDirectories(path.getParent());
                        javax.imageio.ImageIO.write(png, "png", path.toFile());
                    }
                } finally { stage.close(); }
                p.area("jwtValidateTokenArea").setText(JOSEService.signJws(PAYLOAD, "HS256", KEY));
                p.area("jwtValidateKeyArea").setText(KEY);
                for (String invalid : List.of("garbage", "-1", "9223372036854775807")) {
                    ((TextField)p.control("jwtClockSkewField")).setText(invalid);
                    p.reporter.error = null;
                    p.invoke("handleValidateJWT");
                    assertNotNull(p.reporter.error);
                    assertNull(p.reporter.result, "Invalid skew must stop validation");
                }
                ((TextField)p.control("jwtClockSkewField")).setText("0");
                p.invoke("handleValidateJWT");
                assertNotNull(p.reporter.result);
                assertTrue(p.label("jwtStatusLabel").getStyle().contains("green"));
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    @Test void criticalHeadersUseRealTokensAndVisibleLocalizedWarnings() throws Exception {
        UiTestLifecycleExtension.onFx(() -> {
            try (var p = new JoseCharacterizationSupport()) {
                var header = new com.nimbusds.jose.JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.HS256)
                        .criticalParams(java.util.Set.of("toy")).customParam("toy", true).build();
                var signed = new com.nimbusds.jose.JWSObject(header, new com.nimbusds.jose.Payload(PAYLOAD));
                signed.sign(new com.nimbusds.jose.crypto.MACSigner(KEY));
                p.area("jwtValidateTokenArea").setText(signed.serialize());
                p.area("jwtValidateKeyArea").setText(KEY);
                for (var language : List.of(LanguagePreference.EN, LanguagePreference.ES)) {
                    p.language(language);
                    p.check("jwtIgnoreCritCheck").setSelected(false);
                    ((TextField)p.control("jwtUnderstoodCritField")).clear();
                    p.invoke("handleValidateJWT");
                    assertTrue(p.label("jwtStatusLabel").getStyle().contains("orange"));
                    assertTrue(p.area("jwtFindingsArea").getText().contains(
                            I18nService.getInstance().text("module.jose.claim.unsupportedCrit", "toy")));
                    p.line(language+"_unsupported", "INVALID");
                    p.check("jwtIgnoreCritCheck").setSelected(true);
                    p.invoke("handleValidateJWT");
                    assertTrue(p.label("jwtStatusLabel").getStyle().contains("green"));
                    String warning = I18nService.getInstance().text("module.jose.warning.ignoredCrit", "toy");
                    assertTrue(p.area("jwtFindingsArea").getText().contains(warning));
                    assertTrue(p.reporter.result.getDetails().stream().anyMatch(d -> warning.equals(d.value())
                            && d.classification() == OperationDetail.Classification.PUBLIC));
                    p.line(language+"_ignore", "VALID;PUBLIC_WARNING");
                    p.check("jwtIgnoreCritCheck").setSelected(false);
                    ((TextField)p.control("jwtUnderstoodCritField")).setText("toy");
                    p.invoke("handleValidateJWT");
                    assertTrue(p.label("jwtStatusLabel").getStyle().contains("green"));
                    assertTrue(p.area("jwtFindingsArea").getText().isEmpty());
                    p.line(language+"_understood", "VALID");
                }
                p.digest("0776e413f48a43f8d7afa8f0e4dadee462ef2aae969b4fb41d71e268fc7b499a");
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }
}
