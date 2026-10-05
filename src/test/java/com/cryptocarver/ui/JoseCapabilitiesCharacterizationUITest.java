package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.ResultPresentationPolicy;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Stable UI and privacy characterization for JOSE capability selectors and warnings. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class JoseCapabilitiesCharacterizationUITest {
    private static final String SYNTHETIC_SECRET = "JOSE-CHARACTERIZATION-SECRET";
    private static final String EN_NONE = "alg=none has no signature; anyone can create or change this token.";
    private static final String EN_RSA15 = "RSA1_5 is vulnerable to padding-oracle attacks such as Bleichenbacher.";
    private static final String EN_SHORT_HMAC = "This HMAC key is shorter than the selected hash output, reducing brute-force resistance.";
    private static final String ES_NONE = "alg=none no tiene firma; cualquiera puede crear o modificar este token.";
    private static final String ES_RSA15 = "RSA1_5 es vulnerable a ataques de oráculo de relleno como el de Bleichenbacher.";
    private static final String ES_SHORT_HMAC = "Esta clave HMAC es más corta que la salida del hash seleccionado, lo que reduce la resistencia ante fuerza bruta.";

    @BeforeAll
    static void startFx() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try {
            Platform.startup(() -> { Platform.setImplicitExit(false); ready.countDown(); });
        } catch (IllegalStateException alreadyStarted) {
            Platform.setImplicitExit(false);
            ready.countDown();
        }
        assertTrue(ready.await(15, TimeUnit.SECONDS), "JavaFX toolkit started");
    }

    @Test
    void selectorsWarningsLocalizationReadableErrorsAndVisibilityHaveAStableTranscript() throws Exception {
        LanguagePreference previousLanguage = AppSettings.getInstance().getLanguagePreference();
        SecretVisibilityProfile previousProfile = AppSettings.getInstance().getSecretVisibilityProfile();
        java.util.concurrent.atomic.AtomicReference<javafx.stage.Stage> stageRef = new java.util.concurrent.atomic.AtomicReference<>();
        try {
            List<String> transcript = onFx(() -> {
                I18nService i18n = I18nService.getInstance();
                i18n.setPreference(LanguagePreference.EN);
                FXMLLoader loader = UiTestFxml.loader("/fxml/jose.fxml");
                Parent root = loader.load();
                javafx.stage.Stage stage = new javafx.stage.Stage();
                stage.setScene(new javafx.scene.Scene(root));
                stage.show();
                stageRef.set(stage);
                ComboBox<String> jws = control(loader, "jwtAlgoCombo", ComboBox.class);
                ComboBox<String> jwe = control(loader, "jweKeyAlgoCombo", ComboBox.class);
                Label jwsWarning = control(loader, "jwtSecurityWarningLabel", Label.class);
                Label jweWarning = control(loader, "jweSecurityWarningLabel", Label.class);
                TextArea key = control(loader, "jwtKeyArea", TextArea.class);
                CheckBox acceptNone = control(loader, "jwtAcceptNoneCheck", CheckBox.class);
                CheckBox trustHeaderKey = control(loader, "jwtTrustHeaderKeyCheck", CheckBox.class);
                Label protectedHeaderLabel = control(loader, "jwtProtectedHeaderLabel", Label.class);
                Label allowedAlgorithmsLabel = control(loader, "jwtAllowedAlgorithmsLabel", Label.class);
                CheckBox rfc9068 = control(loader, "jwtRfc9068Check", CheckBox.class);
                CheckBox ignoreCrit = control(loader, "jwtIgnoreCritCheck", CheckBox.class);
                ComboBox<String> jwkUse = control(loader, "jwkUseCombo", ComboBox.class);
                javafx.scene.control.TextField jwkKeyOps = control(loader, "jwkKeyOpsField", javafx.scene.control.TextField.class);
                JOSEController controller = loader.getController();
                TextArea keyConversionOutput = control(loader, "jwkOutputArea", TextArea.class);

                assertTrue(jws.getItems().containsAll(List.of("ES256K", "none")));
                assertTrue(jwe.getItems().containsAll(List.of("RSA1_5", "RSA-OAEP")));
                assertFalse(acceptNone.isSelected());
                assertFalse(trustHeaderKey.isSelected());
                assertNotNull(root);

                com.nimbusds.jose.jwk.ECKey secpKey = (com.nimbusds.jose.jwk.ECKey)
                        controller.generateNewJWK("ES256K", "sig");
                controller.convertJwkToPem(secpKey.toJSONString(), keyConversionOutput);
                String secpPem = keyConversionOutput.getText();
                String secpPrivate = pemPart(secpPem, "PRIVATE KEY");
                String secpPublic = pemPart(secpPem, "PUBLIC KEY");
                String secpToken = com.cryptocarver.crypto.JOSEService.signJws("roundtrip", "ES256K", secpPrivate);
                assertEquals("roundtrip", com.cryptocarver.crypto.JOSEService.verifyJws(secpToken, "ES256K", secpPublic));
                controller.convertPemToJwk(secpPrivate, "EC", "jose-test", keyConversionOutput);
                String convertedSecpJwk = keyConversionOutput.getText().split("\\n\\n// Thumbprint", 2)[0];
                assertEquals(com.nimbusds.jose.jwk.Curve.SECP256K1,
                        com.nimbusds.jose.jwk.ECKey.parse(convertedSecpJwk).getCurve());

                com.nimbusds.jose.jwk.OctetKeyPair x448Key = (com.nimbusds.jose.jwk.OctetKeyPair)
                        controller.generateNewJWK("ECDH-ES-X448", "enc");
                controller.convertJwkToPem(x448Key.toJSONString(), keyConversionOutput);
                String x448Private = pemPart(keyConversionOutput.getText(), "PRIVATE KEY");
                controller.convertPemToJwk(x448Private, "OKP", "jose-test", keyConversionOutput);
                String convertedX448Jwk = keyConversionOutput.getText().split("\\n\\n// Thumbprint", 2)[0];
                assertEquals(com.nimbusds.jose.jwk.Curve.X448,
                        com.nimbusds.jose.jwk.OctetKeyPair.parse(convertedX448Jwk).getCurve());

                List<String> lines = new ArrayList<>();
                lines.add("JWS=" + String.join(",", jws.getItems()));
                lines.add("JWE=" + String.join(",", jwe.getItems()));
                lines.add("SECP256K1_JWK_PEM=true");
                lines.add("X448_JWK_PEM=true");
                lines.add("EN_PROTECTED_HEADER_LABEL=" + protectedHeaderLabel.getText());
                lines.add("EN_ALLOWED_ALGORITHMS_LABEL=" + allowedAlgorithmsLabel.getText());
                lines.add("EN_RFC9068_OPTION=" + rfc9068.getText());
                lines.add("EN_IGNORE_CRIT_OPTION=" + ignoreCrit.getText());
                lines.add("EN_TRUST_HEADER_OPTION=" + trustHeaderKey.getText());
                lines.add("EN_JWK_USE_OPTIONS=" + String.join(",", jwkUse.getItems()));
                lines.add("EN_JWK_KEY_OPS_LABEL=" + control(loader, "jwkKeyOpsLabel", Label.class).getText());
                lines.add("EN_JWK_KEY_OPS_PROMPT=" + jwkKeyOps.getPromptText());
                jws.setValue("none");
                lines.add("EN_NONE=" + jwsWarning.getText());
                lines.add("EN_NONE_OPTION=" + jws.getButtonCell().getAccessibleText());
                java.util.concurrent.atomic.AtomicReference<OperationResult> published = new java.util.concurrent.atomic.AtomicReference<>();
                controller.setReporter(new StatusReporter() {
                    @Override public void updateStatus(String message) { }
                    @Override public void updateInspector(String operation, byte[] input, byte[] output,
                            List<OperationDetail> details) { }
                    @Override public void showError(String title, String message) { }
                    @Override public void publish(OperationResult result) { published.set(result); }
                });
                controller.generateSignedJWT("{\"sub\":\"invented\"}",
                        List.of(new com.cryptocarver.crypto.SignerConfig("none", "unused")), "Compact", false,
                        control(loader, "jwtOutputArea", TextArea.class));
                String resultWarning = published.get().getDetails().stream()
                        .filter(detail -> detail.name().equals("Security warning")).findFirst().orElseThrow().value();
                lines.add("EN_RESULT_WARNING=" + resultWarning);
                lines.add("EN_RESULT_WARNING_PUBLIC=" + published.get().getDetails().stream()
                        .filter(detail -> detail.name().equals("Security warning")).findFirst().orElseThrow().classification());
                com.nimbusds.jose.jwk.RSAKey mismatchedUse = (com.nimbusds.jose.jwk.RSAKey)
                        controller.generateNewJWK("RS256", "sig");
                mismatchedUse = (com.nimbusds.jose.jwk.RSAKey) com.cryptocarver.crypto.JoseJwkPolicy
                        .withMetadata(mismatchedUse, "enc", "encrypt");
                controller.generateSignedJWT("{\"sub\":\"invented\"}",
                        List.of(new com.cryptocarver.crypto.SignerConfig("RS256", mismatchedUse.toJSONString())),
                        "Compact", false, "{\"kid\":\"phase2-test\"}",
                        control(loader, "jwtOutputArea", TextArea.class));
                String enMetadataWarning = published.get().getDetails().stream()
                        .filter(detail -> detail.name().equals("Security warning")).findFirst().orElseThrow().value();
                lines.add("EN_JWK_METADATA_WARNING=" + enMetadataWarning);
                jws.setValue("HS256");
                key.setText("short");
                lines.add("EN_HMAC=" + jwsWarning.getText());
                jwe.setValue("RSA1_5");
                lines.add("EN_RSA15=" + jweWarning.getText());
                lines.add("EN_RSA15_OPTION=" + jwe.getButtonCell().getAccessibleText());

                i18n.setPreference(LanguagePreference.ES);
                lines.add("ES_ACCEPT=" + acceptNone.getText());
                lines.add("ES_TRUST_HEADER_OPTION=" + trustHeaderKey.getText());
                lines.add("ES_PROTECTED_HEADER_LABEL=" + protectedHeaderLabel.getText());
                lines.add("ES_ALLOWED_ALGORITHMS_LABEL=" + allowedAlgorithmsLabel.getText());
                lines.add("ES_RFC9068_OPTION=" + rfc9068.getText());
                lines.add("ES_IGNORE_CRIT_OPTION=" + ignoreCrit.getText());
                lines.add("ES_JWK_KEY_OPS_LABEL=" + control(loader, "jwkKeyOpsLabel", Label.class).getText());
                controller.generateSignedJWT("{\"sub\":\"invented\"}",
                        List.of(new com.cryptocarver.crypto.SignerConfig("RS256", mismatchedUse.toJSONString())),
                        "Compact", false, "{\"kid\":\"phase2-test\"}",
                        control(loader, "jwtOutputArea", TextArea.class));
                String esMetadataWarning = published.get().getDetails().stream()
                        .filter(detail -> detail.name().equals("Security warning")).findFirst().orElseThrow().value();
                lines.add("ES_JWK_METADATA_WARNING=" + esMetadataWarning);
                jws.setValue("none");
                lines.add("ES_NONE=" + jwsWarning.getText());
                jws.setValue("HS256");
                lines.add("ES_HMAC=" + jwsWarning.getText());
                lines.add("ES_RSA15=" + jweWarning.getText());
                lines.add("ES_RSA15_OPTION=" + jwe.getButtonCell().getAccessibleText());

                boolean readableError;
                try {
                    com.cryptocarver.crypto.JOSEService.verifyJws("malformed", "HS256", "invented");
                    readableError = false;
                } catch (Exception failure) {
                    readableError = failure.getMessage() != null && !failure.getMessage().isBlank();
                }
                lines.add("READABLE_ERROR=" + readableError);

                OperationResult result = OperationResult.forOperation("JOSE characterization")
                        .output("PUBLIC-TEST-OUTPUT".getBytes(StandardCharsets.UTF_8))
                        .detail(OperationDetail.publicDetail("Security warning", ES_NONE))
                        .detail(OperationDetail.secretDetail("Key Material", SYNTHETIC_SECRET))
                        .build();
                assertEquals(OperationDetail.Classification.PUBLIC, result.getDetails().get(0).classification());
                for (SecretVisibilityProfile profile : SecretVisibilityProfile.values()) {
                    AppSettings.getInstance().setSecretVisibilityProfile(profile);
                    List<OperationDetail> history = ResultPresentationPolicy.detailsForHistory(result);
                    boolean secretVisible = profile == SecretVisibilityProfile.FULL_LAB;
                    boolean warningVisible = history.stream().anyMatch(detail -> detail.name().equals("Security warning")
                            && ES_NONE.equals(detail.value()));
                    String detailView = history.stream().map(detail -> detail.classification() == OperationDetail.Classification.SECRET
                            ? profile == SecretVisibilityProfile.FULL_LAB ? detail.value()
                                    : profile == SecretVisibilityProfile.MASKED ? "***MASKED***" : ""
                            : detail.value()).reduce((left, right) -> left + "\n" + right).orElse("");
                    boolean storedSecretVisible = detailView.contains(SYNTHETIC_SECRET);
                    lines.add(profile.name() + "_WARNING=" + warningVisible);
                    lines.add(profile.name() + "_SECRET_VISIBLE=" + storedSecretVisible);
                    lines.add(profile.name() + "_SHELF_BLOCKED=" + ResultPresentationPolicy
                            .isShelfCaptureBlockedByVisibility(ResultPresentationPolicy.classifyPublishedResult(result), profile));
                }
                return lines;
            });

            String joined = String.join("\n", transcript);
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(joined.getBytes(StandardCharsets.UTF_8)));
            assertEquals("bc36301eed279b7a7c7a83ac29214da2dcd1fc9e79eb3a52e034261dea5e64db", digest, joined);
            assertTrue(joined.contains("EN_NONE=" + EN_NONE));
            assertTrue(joined.contains("EN_RESULT_WARNING=" + EN_NONE));
            assertTrue(joined.contains("EN_RESULT_WARNING_PUBLIC=PUBLIC"));
            assertTrue(joined.contains("EN_RSA15=" + EN_RSA15));
            assertTrue(joined.contains("EN_HMAC=" + EN_SHORT_HMAC));
            assertTrue(joined.contains("ES_NONE=" + ES_NONE));
            assertTrue(joined.contains("ES_RSA15=" + ES_RSA15));
            assertTrue(joined.contains("ES_HMAC=" + ES_SHORT_HMAC));
            assertTrue(joined.contains("EN_TRUST_HEADER_OPTION=Trust the public key embedded in the token (insecure)."));
            assertTrue(joined.contains("ES_TRUST_HEADER_OPTION=Confiar en la clave pública incluida en el token (inseguro)."));
            assertTrue(joined.contains("EN_ALLOWED_ALGORITHMS_LABEL=Allowed JWS algorithms (comma-separated):"));
            assertTrue(joined.contains("ES_ALLOWED_ALGORITHMS_LABEL=Algoritmos JWS permitidos (separados por comas):"));
            assertTrue(joined.contains("EN_JWK_METADATA_WARNING=JWK metadata mismatch: use=enc for sig operation; key_ops does not include sign."));
            assertTrue(joined.contains("ES_JWK_METADATA_WARNING=Metadatos JWK incompatibles: use=enc for sig operation; key_ops does not include sign."));
            assertTrue(joined.contains("EN_JWK_USE_OPTIONS=sig,enc"));
            assertTrue(joined.contains("EN_JWK_KEY_OPS_LABEL=key_ops:"));
            assertTrue(joined.contains("READABLE_ERROR=true"));
            assertTrue(joined.contains("FULL_LAB_WARNING=true"));
            assertTrue(joined.contains("MASKED_WARNING=true"));
            assertTrue(joined.contains("REDACTED_WARNING=true"));
            assertTrue(joined.contains("FULL_LAB_SECRET_VISIBLE=true"));
            assertTrue(joined.contains("MASKED_SECRET_VISIBLE=false"));
            assertTrue(joined.contains("REDACTED_SECRET_VISIBLE=false"));
            assertFalse(joined.contains(SYNTHETIC_SECRET));
        } finally {
            onFx(() -> {
                if (stageRef.get() != null) { stageRef.get().close(); stageRef.get().setScene(null); }
                AppSettings.getInstance().setSecretVisibilityProfile(previousProfile);
                I18nService.getInstance().setPreference(previousLanguage);
                return null;
            });
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T control(FXMLLoader loader, String id, Class<T> type) {
        Object value = loader.getNamespace().get(id);
        assertNotNull(value, id);
        assertTrue(type.isInstance(value), id);
        return (T) value;
    }

    private static String pemPart(String source, String keyType) {
        String begin = "-----BEGIN " + keyType + "-----";
        String end = "-----END " + keyType + "-----";
        int start = source.indexOf(begin);
        int finish = source.indexOf(end, start);
        if (start < 0 || finish < 0) throw new AssertionError("Missing " + keyType + " PEM block");
        return source.substring(start, finish + end.length());
    }

    private static <T> T onFx(CheckedSupplier<T> work) throws Exception {
        if (Platform.isFxApplicationThread()) return work.get();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try { value.set(work.get()); } catch (Throwable error) { failure.set(error); }
            finally { done.countDown(); }
        });
        assertTrue(done.await(20, TimeUnit.SECONDS), "JavaFX work completed");
        if (failure.get() != null) throw new AssertionError(failure.get());
        return value.get();
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> { T get() throws Exception; }
}
