package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.service.I18nService;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Accordion;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TitledPane;
import javafx.scene.image.WritableImage;
import javafx.scene.transform.Transform;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the real shell through the labs of tutorial 25 (JOSE: JWE and JWK) and writes one
 * PNG per step plus a {@code manifest.tsv} (file, id, Spanish caption). The tutorial images,
 * its PDF and its walkthrough video are built from that output by
 * {@code scripts/build_tutorial_25_media.py} and {@code scripts/generate_tutorial_pdf.py}.
 *
 * <p>Not a regression test: it only runs when {@code -DtutorialCaptureOut=<dir>} is given.
 * Frames are rendered by JavaFX itself, so nothing outside the application window is captured,
 * and Surefire's isolated {@code user.home} keeps the developer's history untouched.
 *
 * <pre>
 * mvn -o -q test -Dtest=TutorialJoseCaptureTool -DtutorialCaptureOut=target/tutorial-25
 * </pre>
 */
@EnabledIfSystemProperty(named = "tutorialCaptureOut", matches = ".+")
class TutorialJoseCaptureTool {
    private static final String PAYLOAD = "pedido=42;importe=125.00";
    private static final String SECRET_HEX = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f";
    private static final String HMAC_SECRET = "0123456789abcdef0123456789abcdef";
    private static final double SCALE = 2.0;

    private Path out;
    private Parent root;
    private ModernMainController shell;
    private JOSEController jose;
    private final List<String> manifest = new ArrayList<>();

    @Test
    void capture() throws Exception {
        System.setProperty("test.mode", "true");
        out = Path.of(System.getProperty("tutorialCaptureOut"));
        Files.createDirectories(out);
        CountDownLatch started = new CountDownLatch(1);
        try { Platform.startup(() -> { Platform.setImplicitExit(false); started.countDown(); }); }
        catch (IllegalStateException alreadyStarted) { started.countDown(); }
        assertTrue(started.await(15, TimeUnit.SECONDS));
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try { run(); } catch (Throwable error) { failure.set(error); } finally { done.countDown(); }
        });
        assertTrue(done.await(10, TimeUnit.MINUTES));
        if (failure.get() != null) throw new AssertionError(failure.get());
        Files.write(out.resolve("manifest.tsv"), manifest, StandardCharsets.UTF_8);
    }

    private void run() throws Exception {
        I18nService.getInstance().setPreference(LanguagePreference.EN);
        AppSettings.getInstance().setSecretVisibilityProfile(SecretVisibilityProfile.FULL_LAB);
        FXMLLoader loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
        root = loader.load();
        shell = loader.getController();
        Scene scene = new Scene(root, 1720, 1000);
        scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
        scene.getStylesheets().add(getClass().getResource("/css/theme-light.css").toExternalForm());
        Stage stage = new Stage();
        stage.setScene(scene);
        stage.show();
        try {
            shell.navigateToModule("JWK (Keys)");
            Field field = ModernMainController.class.getDeclaredField("joseController");
            field.setAccessible(true);
            jose = (JOSEController) field.get(shell);
            for (Node pane : root.lookupAll(".titled-pane")) ((TitledPane) pane).setAnimated(false);
            jwk();
            jwe();
            jwsAndReference();
        } finally {
            stage.close();
            shell.shutdown();
            stage.setScene(null);
        }
    }

    // --- JWK (Keys) ---------------------------------------------------------------------

    private String rsaJwk;
    private String ecJwk;

    private void jwk() throws Exception {
        String publicPem = Files.readString(Path.of("tutoriales/datos/clave-publica-laboratorio.pem"));
        shot("jwk-tools", "JWK (Keys) tiene dos paneles: JWK Tools y JWK Set (JWKS).");
        focus("jwkInputArea");

        combo("jwkKeyTypeCombo", "RSA");
        text("jwkKeyIdField", "rsa-2026-01");
        combo("jwkUseCombo", "enc");
        text("jwkKeyOpsField", "wrapKey, unwrapKey");
        text("jwkInputArea", publicPem);
        shot("jwk-pem-entrada", "Pega la clave pública PEM y fija kid, use y key_ops.");
        invoke("handlePemToJwk");
        focus("jwkOutputArea");
        shot("jwk-pem-a-jwk", "PEM → JWK: la JWK pública con sus metadatos y el thumbprint SHA-256.");

        String publicJwk = value("jwkOutputArea").split("\n\n// Thumbprint")[0];
        text("jwkInputArea", publicJwk);
        invoke("handleCalculateThumbprint");
        shot("jwk-thumbprint", "Calc Thumbprint: huella RFC 7638, independiente de kid, use y key_ops.");
        invoke("handleInspectJwkMetadata");
        shot("jwk-metadatos", "Inspect Metadata: resumen de kty, kid, use y key_ops sin material de clave.");

        combo("jwkKeyTypeCombo", "OCT");
        text("jwkKeyIdField", "");
        text("jwkKeyOpsField", "");
        combo("jwkUseCombo", "sig");
        combo("jwkSecretFormatCombo", "Hex");
        text("jwkInputArea", SECRET_HEX);
        text("jwkOutputArea", "");
        shot("jwk-oct-entrada", "Tipo OCT: el selector Secret Format decide cómo se lee el secreto (aquí, Hex).");
        invoke("handlePemToJwk");
        shot("jwk-oct-a-jwk", "Secret → JWK: clave simétrica oct; el thumbprint es determinista.");
        text("jwkInputArea", value("jwkOutputArea").split("\n\n// Thumbprint")[0]);
        invoke("handleJwkToPem");
        shot("jwk-oct-a-secreto", "JWK → Secret: el mismo secreto en Hex, Base64 y Base64URL.");

        combo("jwkKeyTypeCombo", "RSA");
        combo("jwkSecretFormatCombo", "Base64 / Base64URL");
        text("jwkInputArea", "");
        text("jwkOutputArea", "");
        focus("jwksSecretArea");
        invoke("handleNewJWKS");
        shot("jwks-nuevo", "JWK Set (JWKS): New JWKS crea un conjunto vacío.");
        combo("jwksRotateAlgoCombo", "RSA-OAEP");
        invoke("handleRotateKey");
        rsaJwk = lastKey();
        shot("jwks-rsa", "Add New Key to Set con RSA-OAEP: genera un par RSA-2048 con kid aleatorio y use=enc.");
        combo("jwksRotateAlgoCombo", "ES256");
        invoke("handleRotateKey");
        ecJwk = lastKey();
        shot("jwks-ec", "Con ES256 se añade un par EC P-256: el conjunto ya tiene dos claves.");
        combo("jwksRotateAlgoCombo", "ECDH-ES");
        invoke("handleRotateKey");
        scrollTo("jwksRotateAlgoCombo", 520);
        shot("jwks-okp", "Con ECDH-ES se genera un par OKP X25519; la curva y use=enc se ajustan solos.");

        combo("jwkKeyTypeCombo", "RSA");
        focus("jwkInputArea");
        text("jwkInputArea", rsaJwk);
        invoke("handleJwkToPem");
        shot("jwk-a-pem", "JWK → PEM: el par RSA generado, exportado como PUBLIC KEY y PRIVATE KEY.");
    }

    private String lastKey() throws Exception {
        List<JWK> keys = JWKSet.parse(value("jwksSecretArea")).getKeys();
        return keys.get(keys.size() - 1).toJSONString();
    }

    // --- JWE (Encrypted) ----------------------------------------------------------------

    private void jwe() throws Exception {
        String publicPem = Files.readString(Path.of("tutoriales/datos/clave-publica-laboratorio-comparacion.pem"));
        String privatePem = Files.readString(Path.of("tutoriales/datos/clave-privada-laboratorio-comparacion.pem"));
        shell.navigateToModule("JWE (Encrypted)");
        focus("jwePayloadArea");

        // Lab A
        encryptForm("RSA-OAEP-256", "A256GCM", "UTF-8", publicPem, "Compact", "");
        scrollTo("jweKeyAlgoCombo", 120);
        shot("jwe-a-parametros", "Laboratorio A: RSA-OAEP-256 + A256GCM con la clave pública del destinatario.");
        invoke("handleGenerateJWE");
        String tokenA = value("jweOutputArea");
        scrollTo("jweOutputArea", 520);
        shot("jwe-a-resultado", "Encrypt & Generate JWE: token compacto de cinco segmentos.");
        decrypt(tokenA, privatePem, "UTF-8");
        scrollTo("jweInputArea", 120);
        shot("jwe-a-descifrado", "Decrypt: con la privada RSA se recuperan cabecera y payload.");
        focus("jweDecryptedKeyArea");
        scrollTo("jweHeaderArea", 160);
        shot("jwe-a-desglose", "Visual Breakdown: cabecera, clave cifrada, CEK en claro, IV, ciphertext y tag.");

        // Lab B
        focus("jwePayloadArea");
        encryptForm("A256KW", "A256GCM", "Hex", SECRET_HEX, "Compact", "");
        invoke("handleGenerateJWE");
        scrollTo("jweKeyAlgoCombo", 120);
        shot("jwe-b-a256kw", "Laboratorio B: A256KW con un secreto de 256 bits leído como Hex.");

        // Lab C
        encryptForm("dir", "A256GCM", "Hex", SECRET_HEX, "Compact", "");
        invoke("handleGenerateJWE");
        scrollTo("jweOutputArea", 520);
        shot("jwe-c-dir", "Laboratorio C: cifrado directo; el segmento encrypted_key queda vacío (..).");

        // Lab D
        encryptForm("PBES2-HS256+A128KW", "A256GCM", "UTF-8", "correct horse battery staple", "Compact", "");
        invoke("handleGenerateJWE");
        scrollTo("jweKeyAlgoCombo", 120);
        shot("jwe-d-pbes2", "Laboratorio D: PBES2 con contraseña y 600000 iteraciones PBKDF2.");

        // Lab E
        encryptForm("ECDH-ES+A256KW", "A256GCM", "UTF-8", ecJwk, "Compact", "");
        focus("jweApuField");
        text("jweApuField", "Alice");
        text("jweApvField", "Bob");
        scrollTo("jweSerializationCombo", 200);
        shot("jwe-e-cabeceras", "Laboratorio E: ECDH-ES+A256KW con la clave EC generada; apu y apv en Header Parameters.");
        invoke("handleGenerateJWE");
        String tokenE = value("jweOutputArea");
        scrollTo("jweOutputArea", 520);
        shot("jwe-e-resultado", "El token lleva la clave efímera del emisor (epk) en la cabecera protegida.");
        text("jweApuField", "");
        text("jweApvField", "");
        collapse("jweApuField");

        shell.navigateToModule("Token Inspector");
        text("inspectorInputArea", tokenE);
        invoke("handleInspectToken");
        shot("inspector-jwe", "Token Inspector: desglose sin clave; se leen epk, apu y apv.");
        shell.navigateToModule("JWE (Encrypted)");

        // Lab F
        focus("jwePayloadArea");
        encryptForm("RSA-OAEP-256", "A256GCM", "UTF-8", publicPem, "Flattened JSON", "pedido-42");
        invoke("handleGenerateJWE");
        scrollTo("jweOutputArea", 520);
        shot("jwe-f-aad", "Laboratorio F: Flattened JSON con AAD; el miembro aad va autenticado.");

        // Lab G
        shell.navigateToModule("JWK (Keys)");
        focus("jwksSecretArea");
        invoke("handleNewJWKS");
        for (String algorithm : List.of("RSA-OAEP", "ECDH-ES+A256KW", "A256KW")) {
            combo("jwksRotateAlgoCombo", algorithm);
            invoke("handleRotateKey");
        }
        String recipients = value("jwksSecretArea");
        String recipientOkp = JWKSet.parse(recipients).getKeys().get(1).toJSONString();
        scrollTo("jwksSecretArea", 200);
        shot("jwks-destinatarios", "Laboratorio G: un JWKS con tres claves (RSA, X25519 y oct); cada una declara su alg.");
        shell.navigateToModule("JWE (Encrypted)");
        focus("jwePayloadArea");
        encryptForm("RSA-OAEP-256", "A256GCM", "UTF-8", recipients, "General JSON", "");
        scrollTo("jweKeyAlgoCombo", 120);
        shot("jwe-g-destinatarios", "El JWKS completo va en Recipient Key, con Serialization = General JSON.");
        invoke("handleGenerateJWE");
        String tokenG = value("jweOutputArea");
        scrollTo("jweOutputArea", 520);
        shot("jwe-g-resultado", "General JSON: una entrada por destinatario en recipients y un solo ciphertext.");
        decrypt(tokenG, recipientOkp, "UTF-8");
        scrollTo("jweInputArea", 120);
        shot("jwe-g-descifrado", "Cada destinatario descifra con su clave: el estado indica cuál encajó (recipient 2/3).");

        // Nested
        focus("nestedPayloadArea");
        combo("nestedSignAlgoCombo", "HS256");
        combo("nestedSecretFormatCombo", "UTF-8");
        text("nestedSigningKeyArea", HMAC_SECRET);
        text("nestedPayloadArea", "{\"sub\":\"cliente-42\"}");
        combo("nestedKeyAlgoCombo", "RSA-OAEP-256");
        combo("nestedContentAlgoCombo", "A256GCM");
        text("nestedEncryptionKeyArea", publicPem);
        invoke("handleGenerateNestedJWT");
        scrollTo("nestedSignAlgoCombo", 120);
        shot("nested-generar", "Nested: se firma con HS256 y el JWS resultante se cifra con RSA-OAEP-256.");
        text("nestedEncryptionKeyArea", privatePem);
        invoke("handleVerifyNestedJWT");
        scrollTo("nestedOutputArea", 420);
        shot("nested-verificar", "Decrypt & Verify: primero se descifra y después se verifica la firma interior.");
    }

    private void encryptForm(String alg, String enc, String format, String key, String serialization, String aad)
            throws Exception {
        combo("jweKeyAlgoCombo", alg);
        combo("jweContentAlgoCombo", enc);
        combo("jweKeyFormatCombo", format);
        text("jwePublicKeyArea", key);
        text("jwePayloadArea", PAYLOAD);
        combo("jweSerializationCombo", serialization);
        text("jweAadField", aad);
        text("jweOutputArea", "");
    }

    private void decrypt(String token, String key, String format) throws Exception {
        focus("jweInputArea");
        text("jweInputArea", token);
        text("jwePrivateKeyArea", key);
        combo("jweDecryptKeyFormatCombo", format);
        invoke("handleDecryptJWE");
    }

    // --- JWT, JWA -----------------------------------------------------------------------

    private void jwsAndReference() throws Exception {
        shell.navigateToModule("JWT (Signed)");
        focus("jwtPayloadArea");
        combo("jwtAlgoCombo", "HS256");
        combo("jwtSecretFormatCombo", "UTF-8");
        text("jwtKeyArea", HMAC_SECRET);
        text("jwtPayloadArea", "{\"sub\":\"laboratorio\",\"iss\":\"cryptocarver\",\"aud\":\"demo\",\"exp\":1893456000}");
        invoke("handleGenerateSignedJWT");
        String jwt = value("jwtOutputArea");
        scrollTo("jwtPayloadArea", 300);
        shot("jwt-generar", "JWT (Signed): token HS256 de tres segmentos a partir de los claims.");
        focus("jwtValidateTokenArea");
        text("jwtValidateTokenArea", jwt);
        text("jwtValidateKeyArea", HMAC_SECRET);
        combo("jwtValidateSecretFormatCombo", "UTF-8");
        text("jwtExpectedIssField", "cryptocarver");
        text("jwtExpectedAudField", "demo");
        invoke("handleValidateJWT");
        scrollTo("jwtValidateKeyArea", 160);
        shot("jwt-validar", "Validate & Decode: firma y claims se comprueban por separado.");

        shell.navigateToModule("JWA (Algorithms)");
        shot("jwa", "JWA (Algorithms): registro de algoritmos de firma, gestión de clave y contenido.");
    }

    // --- helpers ------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private <T> T control(String name) throws Exception {
        Field field = JOSEController.class.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(jose);
    }

    private void text(String name, String value) throws Exception {
        TextInputControl control = control(name);
        control.setText(value);
        control.positionCaret(0);
    }

    private String value(String name) throws Exception {
        return this.<TextInputControl>control(name).getText();
    }

    private void combo(String name, String value) throws Exception {
        this.<ComboBox<String>>control(name).setValue(value);
    }

    private void invoke(String name) throws Exception {
        Method method = JOSEController.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(jose);
        ClippedTextAuditTool.settle();
    }

    /** Expands every titled pane around the control and scrolls it into view. */
    private void focus(String name) throws Exception {
        Node node = control(name);
        List<TitledPane> panes = new ArrayList<>();
        for (Node parent = node.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof TitledPane pane) panes.add(0, pane);
        }
        for (TitledPane pane : panes) {
            pane.setAnimated(false);
            if (pane.getParent() instanceof Accordion accordion) accordion.setExpandedPane(pane);
            settleLayout();
            if (!pane.isExpanded()) pane.setExpanded(true);
            settleLayout();
        }
        TitledPane outer = panes.isEmpty() ? null : panes.get(0);
        scrollTo(outer == null ? node : outer, 90);
    }

    private void collapse(String name) throws Exception {
        Node node = control(name);
        for (Node parent = node.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof TitledPane pane && !(pane.getParent() instanceof Accordion)) {
                pane.setExpanded(false);
                return;
            }
        }
    }

    private void scrollTo(String name, double offsetFromTop) throws Exception {
        scrollTo(this.<Node>control(name), offsetFromTop);
    }

    private void scrollTo(Node node, double offsetFromTop) {
        settleLayout();
        ScrollPane scroll = null;
        for (Node parent = node.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof ScrollPane pane) scroll = pane;
        }
        if (scroll == null || scroll.getContent() == null) return;
        Node content = scroll.getContent();
        Bounds inContent = content.sceneToLocal(node.localToScene(node.getBoundsInLocal()));
        double range = content.getBoundsInLocal().getHeight() - scroll.getViewportBounds().getHeight();
        scroll.setVvalue(range <= 0 ? 0 : Math.max(0, Math.min(1, (inContent.getMinY() - offsetFromTop) / range)));
        settleLayout();
    }

    private void settleLayout() {
        ClippedTextAuditTool.settle();
        root.applyCss();
        root.layout();
    }

    private void shot(String id, String caption) throws Exception {
        settleLayout();
        settleLayout();
        for (Node check : root.lookupAll(".check-box")) ((CheckBox) check).requestLayout();
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setTransform(Transform.scale(SCALE, SCALE));
        WritableImage image = root.snapshot(parameters, null);
        BufferedImage png = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < png.getHeight(); y++) {
            for (int x = 0; x < png.getWidth(); x++) png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        }
        String file = String.format("%02d-%s.png", manifest.size() + 1, id);
        javax.imageio.ImageIO.write(png, "png", out.resolve(file).toFile());
        manifest.add(file + "\t" + id + "\t" + caption);
    }
}
