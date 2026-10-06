package com.cryptocarver.ui;

import com.cryptocarver.model.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.util.Base64URL;
import javafx.scene.control.TextArea;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named="runUiTests", matches="true")
class JoseJwkPrivacyCharacterizationUITest {
    @BeforeAll static void start() throws Exception { JoseCharacterizationSupport.startFx(); }

    @Test void classifiedPrivateConversionsAndJwksUseTheShellPolicy() throws Exception {
        UiTestLifecycleExtension.onFx(() -> {
            try (var p = new JoseCharacterizationSupport()) {
                JWK rsa = p.controller.generateNewJWK("RS256", "sig");
                JWK oct = new OctetSequenceKey.Builder(Base64URL.encode("invented-secret-for-jose-73-only".getBytes(StandardCharsets.UTF_8))).build();
                String rsaPem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getEncoder().encodeToString(((RSAKey)rsa).toRSAPrivateKey().getEncoded()) + "\n-----END PRIVATE KEY-----\n";
                assertTrue(rsa.toJSONObject().keySet().containsAll(List.of("d", "p", "q", "dp", "dq", "qi")));
                for (LanguagePreference language : List.of(LanguagePreference.EN, LanguagePreference.ES)) {
                    p.language(language);
                    p.line("language", language);
                    for (SecretVisibilityProfile profile : SecretVisibilityProfile.values()) {
                        AppSettings.getInstance().setSecretVisibilityProfile(profile);
                        p.controller.convertPemToJwk(rsaPem, "RSA", "invented-rsa", p.area("jwkOutputArea"));
                        audit(p, "rsa-json", p.area("jwkOutputArea"), privateValues(rsa), profile);
                        p.controller.convertPemToJwk(Base64.getEncoder().encodeToString(((OctetSequenceKey)oct).toByteArray()), "OCT", "invented-oct", p.area("jwkOutputArea"));
                        audit(p, "oct-json", p.area("jwkOutputArea"), privateValues(oct), profile);
                        p.controller.convertJwkToPem(rsa.toJSONString(), p.area("jwkOutputArea"));
                        audit(p, "private-pem", p.area("jwkOutputArea"), List.of(Base64.getEncoder().encodeToString(((RSAKey)rsa).toRSAPrivateKey().getEncoded()).substring(0, 40)), profile);
                        var coordinator = new JoseJwkCoordinator(() -> new JoseJwkCoordinator.View(p.area("jwkInputArea"), p.area("jwkOutputArea"), p.combo("jwkKeyTypeCombo"), p.control("jwkKeyIdField"), p.combo("jwkUseCombo"), p.control("jwkKeyOpsField"), p.area("jwksSecretArea"), p.combo("jwksRotateAlgoCombo"), p.combo("jwkCurveCombo"), p.label("jwkCurveLabel")), () -> p.reporter, new DialogService());
                        String jwks = com.nimbusds.jose.util.JSONObjectUtils.toJSONString(new JWKSet(List.of(rsa, oct)).toJSONObject(false));
                        coordinator.loadedJWKS(jwks);
                        audit(p, "private-and-oct-jwks", p.area("jwksSecretArea"), privateValues(rsa, oct), profile);
                        assertEquals(1, JWKSet.parse(coordinator.currentPublicJwks()).getKeys().size());
                        p.combo("jwksRotateAlgoCombo").setValue("RS256");
                        coordinator.handleRotateKey();
                        JWKSet rotated = JWKSet.parse(new String(p.reporter.result.getOutput(), StandardCharsets.UTF_8));
                        audit(p, "generated-private-jwks", p.area("jwksSecretArea"), privateValues(rotated.getKeys().toArray(JWK[]::new)), profile);
                        assertEquals(2, JWKSet.parse(coordinator.currentPublicJwks()).getKeys().size());
                    }
                }
                p.digest("3bd9891728349fad323eee284024eeaa03bf7a8548efe970a0bd41968d6d344c");
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    private static List<String> privateValues(JWK... keys) {
        List<String> values = new ArrayList<>();
        for (JWK key : keys) for (String name : List.of("d", "p", "q", "dp", "dq", "qi", "k")) {
            Object value = key.toJSONObject().get(name);
            if (value != null) values.add(value.toString());
        }
        return values;
    }

    private static void verifySecureActions(JoseCharacterizationSupport p, ResultAreaTracker tracker,
            ResultCaptureCoordinator capture, TextArea area, SecretVisibilityProfile profile) {
        List<String> clipboard = new ArrayList<>();
        var viewer = new ResultViewerCoordinator(tracker, null, a -> { }, a -> { }, a -> { }, a -> { },
                () -> "JOSE", () -> true, capture::resolveResultText, capture::classificationForResultArea,
                () -> AppSettings.isFullLab(), message -> { }, clipboard::add, entry -> { },
                new ResultViewerCoordinator.ShelfServices() {
                    public String capture(TextArea a) { return capture.resolveShelfCaptureText(a); }
                    public boolean blockedByVisibility(TextArea a) { return capture.isShelfCaptureBlockedByVisibility(a); }
                    public boolean isCurrentSelection(TextArea a) { return tracker.isCurrentSelection(a, true); }
                    public OperationResult snapshot() { return p.reporter.result; }
                    public String activeOperation() { return "JOSE"; }
                    public ClipboardShelfManager manager() { return ClipboardShelfManager.getInstance(); }
                    public boolean isPrimaryCipherOutput(TextArea a) { return false; }
                    public ShelfPackage createCipherPackage() { return null; }
                });
        viewer.copySecure(area, null, false);
        viewer.copySecure(area, area.getText(), true);
        viewer.addToClipboardShelfSecure(area, null);
        assertTrue(clipboard.isEmpty(), profile + " secure Copy accepted protected output");
        assertEquals(p.shelf, ClipboardShelfManager.getInstance().getEntries());
        var reached = new java.util.concurrent.atomic.AtomicBoolean();
        javafx.event.EventHandler<javafx.scene.input.KeyEvent> sink = event -> { reached.set(true); event.consume(); };
        area.addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, sink);
        area.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED, "c", "c",
                javafx.scene.input.KeyCode.C, false, true, false, true));
        area.removeEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, sink);
        assertFalse(reached.get(), "native Copy shortcut bypassed the live visibility guard");
    }

    private static void audit(JoseCharacterizationSupport p, String name, TextArea area, List<String> secrets, SecretVisibilityProfile profile) throws Exception {
        var tracker = new ResultAreaTracker();
        tracker.register(area); tracker.markUpdated(area); tracker.focus(area);
        var capture = new ResultCaptureCoordinator(() -> null, () -> null, () -> null, () -> null,
                () -> tracker, () -> p.reporter.result, () -> "JOSE", () -> "JOSE", () -> AppSettings.getInstance().getSecretVisibilityProfile(),
                message -> { }, (title, message) -> { }, (control, selected) -> { });
        String copy = capture.resolveResultText(area), expanded = capture.resolveCurrentOutputText(), shelf = capture.resolveShelfCaptureText(area);
        if (profile != SecretVisibilityProfile.FULL_LAB) {
            for (String secret : secrets) {
                assertFalse(copy.contains(secret), name + " Copy leaked private material");
                assertFalse(expanded.contains(secret), name + " Expand leaked private material");
                assertFalse(shelf.contains(secret), name + " Shelf leaked private material");
                assertFalse(area.getText().contains(secret), name + " native textarea Copy could expose private material");
            }
            assertTrue(capture.isShelfCaptureBlockedByVisibility(area), name + " Shelf must be blocked");
        } else assertTrue(secrets.stream().anyMatch(area.getText()::contains), name + " FULL_LAB changed");
        assertNotNull(p.reporter.result, name + " must publish a classified result");
        assertEquals(OperationDetail.Classification.SECRET, ResultPresentationPolicy.classifyPublishedResult(p.reporter.result));
        for (String secret : secrets) {
            assertFalse(Objects.toString(p.reporter.result.getStatusMessage(), "").contains(secret));
            assertFalse(p.reporter.result.getDetails().stream().filter(d -> d.classification() == OperationDetail.Classification.PUBLIC).anyMatch(d -> d.value().contains(secret)), "public telemetry details leaked");
        }
        p.privacy(p.reporter.result, secrets.toArray(String[]::new));
        for (SecretVisibilityProfile restricted : List.of(SecretVisibilityProfile.MASKED, SecretVisibilityProfile.REDACTED)) {
            AppSettings.getInstance().setSecretVisibilityProfile(restricted);
            verifySecureActions(p, tracker, capture, area, restricted);
            String recipe = UiStateSnapshot.captureHistoryRecipe(p.controller).toString();
            for (String secret : secrets) assertFalse(recipe.contains(secret), "history recipe retained private JWKS after profile change");
        }
        AppSettings.getInstance().setSecretVisibilityProfile(profile);
        p.line("status", p.reporter.result.getStatusMessage());
        p.line(name + "_" + profile, "classified;copy/expand/shelf/history/status/telemetry-protected");
    }
}
