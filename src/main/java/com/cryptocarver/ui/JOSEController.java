package com.cryptocarver.ui;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextFlow;
import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;


import com.cryptocarver.util.DataConverter;
import com.cryptocarver.crypto.JOSEService;
import com.cryptocarver.crypto.JoseNoneJws;
import com.cryptocarver.crypto.JWEManualCekRecovery;
import com.cryptocarver.crypto.JoseKeyMaterial;
import com.cryptocarver.crypto.JoseJwkPolicy;
import com.cryptocarver.crypto.JweComposer;
import com.cryptocarver.crypto.JwtClaimsBuilder;
import com.cryptocarver.crypto.JwtValidator;
import com.cryptocarver.crypto.SignerConfig;
import com.cryptocarver.model.OperationResult;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.*;
import com.nimbusds.jose.jca.JCAContext;
import com.nimbusds.jwt.*;
import com.nimbusds.jose.util.Base64URL;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.text.TextFlow;
import javafx.scene.text.Text;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.UUID;
import java.util.Set;
import java.util.Collections;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;

import javafx.fxml.Initializable;
import java.net.URL;
import java.util.ResourceBundle;

public class JOSEController implements Initializable {
    private JoseJwkCoordinator jwkCoordinator;
    private final JoseInspectorCoordinator inspectorCoordinator = new JoseInspectorCoordinator();
    private JoseJwkCoordinator jwkCoordinator() {
        if (jwkCoordinator == null) jwkCoordinator = new JoseJwkCoordinator(
                () -> new JoseJwkCoordinator.View(jwkInputArea, jwkOutputArea, jwkKeyTypeCombo, jwkKeyIdField,
                        jwkUseCombo, jwkKeyOpsField, jwksArea, jwksRotateAlgoCombo), () -> statusReporter, dialogService);
        return jwkCoordinator;
    }

    private JoseJweCoordinator jweCoordinator;
    private JoseJweCoordinator jweCoordinator() {
        if (jweCoordinator == null) jweCoordinator = new JoseJweCoordinator(
                () -> new JoseJweCoordinator.View(jweAadField, jweApuField, jweApvField, jweAuthTagArea, jweCiphertextArea, jweCompressCheck, jweContentAlgoCombo, jweCtyField, jweCustomHeaderArea, jweDecodedHeaderArea, jweDecodedPayloadArea, jweDecryptKeyFormatCombo, jweDecryptedKeyArea, jweEncryptedKeyArea, jweHeaderArea, jweIVArea, jweInputArea, jweKeyAlgoCombo, jweKeyFormatCombo, jweKidField, jweOutputArea, jwePayloadArea, jwePbes2IterField, jwePrivateKeyArea, jwePublicKeyArea, jweSerializationCombo, jweStatusLabel, jweTypField), () -> statusReporter);
        return jweCoordinator;
    }


    private JoseJwtCoordinator jwtCoordinator;
    private JoseJwtCoordinator jwtCoordinator() {
        if (jwtCoordinator == null) jwtCoordinator = new JoseJwtCoordinator(
                () -> new JoseJwtCoordinator.View(detachedAlgoCombo, detachedPayloadArea, detachedSecretFormatCombo, detachedSerializationCombo, detachedSigningKeyArea, detachedStatusLabel, detachedTokenArea, detachedUnencodedCheck, detachedVerificationKeyArea, jwsSerializationCombo, jwsUnencodedPayloadCheck, jwtAcceptNoneCheck, jwtAccessTokenField, jwtAlgo2Combo, jwtAlgoCombo, jwtAllowedAlgorithmsField, jwtAudField, jwtAuthorizationCodeField, jwtCheckExpiryCheck, jwtClockSkewField, jwtDecodedHeaderArea, jwtDecodedPayloadArea, jwtExpField, jwtExpectedAudField, jwtExpectedContentTypeField, jwtExpectedIssField, jwtExpectedJktField, jwtExpectedNonceField, jwtExpectedTypeField, jwtExpectedX5tField, jwtFindingsArea, jwtIgnoreCritCheck, jwtIssField, jwtKeyArea, jwtKeyArea2, jwtOidcStrictCheck, jwtOutputArea, jwtPayloadArea, jwtProtectedHeaderArea, jwtRfc9068Check, jwtSecretFormatCombo, jwtStatusLabel, jwtSubField, jwtTrustHeaderKeyCheck, jwtUnderstoodCritField, jwtValidateKeyArea, jwtValidateSecretFormatCombo, jwtValidateTokenArea, nestedCompressCheck, nestedContentAlgoCombo, nestedEncryptionKeyArea, nestedKeyAlgoCombo, nestedOutputArea, nestedPayloadArea, nestedPayloadOutputArea, nestedSecretFormatCombo, nestedSignAlgoCombo, nestedSigningKeyArea, nestedStatusLabel), () -> statusReporter);
        return jwtCoordinator;
    }


    /** Held so the locale listener stays registered: I18nService keeps only a weak reference. */
    private java.util.function.Consumer<java.util.Locale> localeChangeListener;

    private final DialogService dialogService = new DialogService();

    private final ExpandedTextViewer expandedInspectorViewer = new ExpandedTextViewer();
    private ModuleI18n.Binding moduleI18n;

    /** JWS algorithms the module can sign and verify. */
    private static final List<String> JWS_ALGORITHMS = List.of(
            "HS256", "HS384", "HS512",
            "RS256", "RS384", "RS512",
            "ES256", "ES256K", "ES384", "ES512",
            "PS256", "PS384", "PS512",
            "EdDSA", "none");

    private String t(String key, Object... args) {
        return com.cryptocarver.service.I18nService.getInstance().text(key, args);
    }

    private void refreshCapabilityLabels() {
        if (jwtAcceptNoneCheck != null) jwtAcceptNoneCheck.setText(t("module.jose.acceptNone"));
        if (jwtTrustHeaderKeyCheck != null) jwtTrustHeaderKeyCheck.setText(t("module.jose.trustHeaderKey"));
        if (jwtProtectedHeaderLabel != null) jwtProtectedHeaderLabel.setText(t("module.jose.protectedHeaderAdditional"));
        if (jwtAllowedAlgorithmsLabel != null) jwtAllowedAlgorithmsLabel.setText(t("module.jose.jwtAllowedAlgorithms"));
        if (jwtExpectedTypeLabel != null) jwtExpectedTypeLabel.setText(t("module.jose.jwtExpectedType"));
        if (jwtExpectedContentTypeLabel != null) jwtExpectedContentTypeLabel.setText(t("module.jose.jwtExpectedContentType"));
        if (jwtExpectedNonceLabel != null) jwtExpectedNonceLabel.setText(t("module.jose.jwtExpectedNonce"));
        if (jwtAccessTokenLabel != null) jwtAccessTokenLabel.setText(t("module.jose.jwtAccessToken"));
        if (jwtAuthorizationCodeLabel != null) jwtAuthorizationCodeLabel.setText(t("module.jose.jwtAuthorizationCode"));
        if (jwtExpectedJktLabel != null) jwtExpectedJktLabel.setText(t("module.jose.jwtExpectedJkt"));
        if (jwtExpectedX5tLabel != null) jwtExpectedX5tLabel.setText(t("module.jose.jwtExpectedX5t"));
        if (jwtUnderstoodCritLabel != null) jwtUnderstoodCritLabel.setText(t("module.jose.jwtUnderstoodCrit"));
        if (jwtRfc9068Check != null) jwtRfc9068Check.setText(t("module.jose.jwtRfc9068"));
        if (jwtIgnoreCritCheck != null) jwtIgnoreCritCheck.setText(t("module.jose.jwtIgnoreCrit"));
        if (jwkUseLabel != null) jwkUseLabel.setText(t("module.jose.jwkUse"));
        if (jwkKeyOpsLabel != null) jwkKeyOpsLabel.setText(t("module.jose.jwkKeyOps"));
        if (jwkInspectMetadataBtn != null) jwkInspectMetadataBtn.setText(t("module.jose.inspectMetadata"));
        if (jwkKeyOpsField != null) jwkKeyOpsField.setPromptText(t("module.jose.jwkKeyOpsPrompt"));
    }

    private String jwsSecurityWarning(String algorithm, String key, JoseKeyMaterial.SecretEncoding encoding) { return JoseCoordinatorSupport.jwsSecurityWarning(algorithm, key, encoding); }

    private String jweSecurityWarning(String algorithm) { return JoseCoordinatorSupport.jweSecurityWarning(algorithm); }

    private String jwtTokenSecurityWarning(String token, String key, JoseKeyMaterial.SecretEncoding encoding) { return JoseCoordinatorSupport.jwtTokenSecurityWarning(token, key, encoding); }

    private static void addSecurityWarning(OperationResult.Builder result, String warning) { JoseCoordinatorSupport.addSecurityWarning(result, warning); }

    private String metadataWarning(String json, JoseJwkPolicy.Operation operation) { return JoseCoordinatorSupport.metadataWarning(json, operation); }

    private void refreshSecurityWarnings() {
        setSecurityWarning(jwtSecurityWarningLabel,
                combineWarnings(jwsSecurityWarning(valueOf(jwtAlgoCombo), textOf(jwtKeyArea), secretEncoding(jwtSecretFormatCombo)),
                        jwsSecurityWarning(valueOf(jwtAlgo2Combo), textOf(jwtKeyArea2), secretEncoding(jwtSecretFormatCombo))));
        setSecurityWarning(detachedSecurityWarningLabel,
                jwsSecurityWarning(valueOf(detachedAlgoCombo), textOf(detachedSigningKeyArea), secretEncoding(detachedSecretFormatCombo)));
        setSecurityWarning(nestedSecurityWarningLabel,
                combineWarnings(jwsSecurityWarning(valueOf(nestedSignAlgoCombo), textOf(nestedSigningKeyArea), secretEncoding(nestedSecretFormatCombo)),
                        jweSecurityWarning(valueOf(nestedKeyAlgoCombo))));
        setSecurityWarning(jweSecurityWarningLabel, jweSecurityWarning(valueOf(jweKeyAlgoCombo)));
    }

    private void setSecurityWarning(Label label, String text) {
        if (label == null) return;
        label.setText(text == null ? "" : text);
        label.setAccessibleText(text == null ? "" : text);
    }

    private String combineWarnings(String first, String second) {
        if (first == null) return second;
        return second == null || first.equals(second) ? first : first + "\n" + second;
    }

    private static String valueOf(ComboBox<String> combo) { return combo == null ? null : combo.getValue(); }

    private void markUnsafeOptions(ComboBox<String> combo) {
        if (combo == null) return;
        javafx.util.Callback<javafx.scene.control.ListView<String>, ListCell<String>> cells = view -> new ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setAccessibleText(null); return; }
                boolean unsafe = "none".equalsIgnoreCase(item) || "RSA1_5".equals(item) || "RSA-OAEP".equals(item);
                String label = unsafe ? item + " — " + t("module.jose.unsafeMarker") : item;
                setText(label);
                setAccessibleText(label);
            }
        };
        combo.setCellFactory(cells);
        combo.setButtonCell(cells.call(null));
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        moduleI18n = ModuleI18n.bind(joseContainer, ModuleTextCatalog.jose());
        if (jwtAcceptNoneCheck != null) jwtAcceptNoneCheck.setText(t("module.jose.acceptNone"));
        localeChangeListener = locale -> {
            updateJwkInputPresentation();
            refreshCapabilityLabels();
            for (ComboBox<String> combo : java.util.Arrays.asList(jwtAlgoCombo, jwtAlgo2Combo, detachedAlgoCombo,
                    nestedSignAlgoCombo, jweKeyAlgoCombo, nestedKeyAlgoCombo, jwksRotateAlgoCombo)) {
                markUnsafeOptions(combo);
            }
            refreshSecurityWarnings();
            if (detachedStatusLabel != null && detachedStatusLabel.getText() != null
                    && detachedStatusLabel.getText().isBlank()) detachedStatusLabel.setText("");
        };
        com.cryptocarver.service.I18nService.getInstance().addLocaleChangeListener(localeChangeListener);
        // Initialize Combo
            if (jwtAlgoCombo != null && jwtAlgoCombo.getItems().isEmpty()) {
                jwtAlgoCombo.getItems().addAll(JWS_ALGORITHMS);
                jwtAlgoCombo.getSelectionModel().selectFirst();
            }
            if (jwtAlgo2Combo != null && jwtAlgo2Combo.getItems().isEmpty()) {
                jwtAlgo2Combo.getItems().addAll(JWS_ALGORITHMS);
                jwtAlgo2Combo.getSelectionModel().selectFirst();
            }

            // Init JWE Combos
            if (jweKeyAlgoCombo != null && jweKeyAlgoCombo.getItems().isEmpty()) {
                jweKeyAlgoCombo.getItems().setAll(JweComposer.KEY_ALGORITHMS);
                jweKeyAlgoCombo.getSelectionModel().selectFirst();
            }
            if (jweContentAlgoCombo != null && jweContentAlgoCombo.getItems().isEmpty()) {
                jweContentAlgoCombo.getItems().setAll(JweComposer.CONTENT_ALGORITHMS);
                jweContentAlgoCombo.getSelectionModel().select("A256GCM");
            }
            for (ComboBox<String> format : java.util.Arrays.asList(jweKeyFormatCombo, jweDecryptKeyFormatCombo,
                    jwtSecretFormatCombo, jwtValidateSecretFormatCombo, detachedSecretFormatCombo,
                    nestedSecretFormatCombo)) {
                if (format != null && format.getItems().isEmpty()) {
                    for (JoseKeyMaterial.SecretEncoding encoding : JoseKeyMaterial.SecretEncoding.values()) {
                        format.getItems().add(encoding.label());
                    }
                    format.getSelectionModel().selectFirst();
                }
            }
            if (jwePbes2IterField != null && jwePbes2IterField.getText().isBlank()) {
                jwePbes2IterField.setText(String.valueOf(JweComposer.DEFAULT_PBES2_ITERATIONS));
            }

            if (detachedAlgoCombo != null && detachedAlgoCombo.getItems().isEmpty()) {
                detachedAlgoCombo.getItems().setAll(JWS_ALGORITHMS);
                detachedAlgoCombo.getSelectionModel().selectFirst();
            }
            if (jweSerializationCombo != null && jweSerializationCombo.getItems().isEmpty()) {
                for (JweComposer.Serialization serialization : JweComposer.Serialization.values()) {
                    jweSerializationCombo.getItems().add(serialization.label());
                }
                jweSerializationCombo.getSelectionModel().selectFirst();
            }
            for (ComboBox<String> serialization : java.util.Arrays.asList(jwsSerializationCombo, detachedSerializationCombo)) {
                if (serialization != null && serialization.getItems().isEmpty()) {
                    serialization.getItems().setAll("Compact", "Flattened JSON", "General JSON");
                    serialization.getSelectionModel().selectFirst();
                }
            }

            // Init Nested Combos
            if (nestedSignAlgoCombo != null && nestedSignAlgoCombo.getItems().isEmpty()) {
                nestedSignAlgoCombo.getItems().setAll(JWS_ALGORITHMS);
                nestedSignAlgoCombo.getSelectionModel().select("HS256");
            }
            if (nestedKeyAlgoCombo != null && nestedKeyAlgoCombo.getItems().isEmpty()) {
                nestedKeyAlgoCombo.getItems().setAll(JweComposer.KEY_ALGORITHMS);
                nestedKeyAlgoCombo.getSelectionModel().selectFirst();
            }
            if (nestedContentAlgoCombo != null && nestedContentAlgoCombo.getItems().isEmpty()) {
                nestedContentAlgoCombo.getItems().setAll(JweComposer.CONTENT_ALGORITHMS);
                nestedContentAlgoCombo.getSelectionModel().select("A256GCM");
            }

            // Init JWK Combo
            if (jwkKeyTypeCombo != null && jwkKeyTypeCombo.getItems().isEmpty()) {
                jwkKeyTypeCombo.getItems().setAll("RSA", "EC", "OKP", "OCT");
                jwkKeyTypeCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
                    if (newV == null)
                        return;
                    updateJwkInputPresentation();
                });
                jwkKeyTypeCombo.getSelectionModel().selectFirst();
            }
            if (jwkUseCombo != null && jwkUseCombo.getItems().isEmpty()) {
                jwkUseCombo.getItems().setAll("sig", "enc");
                jwkUseCombo.getSelectionModel().selectFirst();
            }
            refreshCapabilityLabels();
            if (jwksRotateAlgoCombo != null && jwksRotateAlgoCombo.getItems().isEmpty()) {
                jwksRotateAlgoCombo.getItems().setAll(
                        "RS256", "RS384", "RS512", "PS256", "PS384", "PS512", "ES256", "ES256K", "ES384", "ES512", "EdDSA",
                        "RSA1_5", "RSA-OAEP", "ECDH-ES", "ECDH-ES-X448", "ECDH-ES+A128KW", "ECDH-ES+A192KW", "ECDH-ES+A256KW",
                        "HS256", "HS384", "HS512", "A128KW", "A256KW", "A128GCM", "A256GCM", "dir");
                jwksRotateAlgoCombo.getSelectionModel().selectFirst();
            }

            for (ComboBox<String> combo : java.util.Arrays.asList(jwtAlgoCombo, jwtAlgo2Combo, detachedAlgoCombo,
                    nestedSignAlgoCombo, jweKeyAlgoCombo, nestedKeyAlgoCombo, jwksRotateAlgoCombo)) {
                markUnsafeOptions(combo);
                if (combo != null) combo.valueProperty().addListener((obs, oldValue, newValue) -> refreshSecurityWarnings());
            }
            for (TextInputControl key : java.util.Arrays.asList(jwtKeyArea, jwtKeyArea2, detachedSigningKeyArea, nestedSigningKeyArea)) {
                if (key != null) key.textProperty().addListener((obs, oldValue, newValue) -> refreshSecurityWarnings());
            }
            refreshSecurityWarnings();

        IngestionUIHelper.bindField(jwtKeyArea, null, com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_PRIVATE_KEY, com.cryptocarver.model.MaterialDetectionResult.MaterialType.HEX, com.cryptocarver.model.MaterialDetectionResult.MaterialType.TEXT_UNKNOWN);
        IngestionUIHelper.bindField(jwtValidateTokenArea, null, com.cryptocarver.model.MaterialDetectionResult.MaterialType.JWT);
        IngestionUIHelper.bindField(jwtValidateKeyArea, null, com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_PUBLIC_KEY, com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_CERTIFICATE, com.cryptocarver.model.MaterialDetectionResult.MaterialType.HEX, com.cryptocarver.model.MaterialDetectionResult.MaterialType.TEXT_UNKNOWN);
        IngestionUIHelper.bindField(jwePublicKeyArea, null, com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_PUBLIC_KEY, com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_CERTIFICATE, com.cryptocarver.model.MaterialDetectionResult.MaterialType.HEX, com.cryptocarver.model.MaterialDetectionResult.MaterialType.TEXT_UNKNOWN);
        IngestionUIHelper.bindField(jweInputArea, null, com.cryptocarver.model.MaterialDetectionResult.MaterialType.JWT);
        IngestionUIHelper.bindField(jwePrivateKeyArea, null, com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_PRIVATE_KEY, com.cryptocarver.model.MaterialDetectionResult.MaterialType.HEX, com.cryptocarver.model.MaterialDetectionResult.MaterialType.TEXT_UNKNOWN);

            // Init JWA Table
            if (jwaTable != null && jwaTable.getItems().isEmpty()) {
                initJwaTable();
            }

            // Init Template Combo
            if (jwtTemplateCombo != null && jwtTemplateCombo.getItems().isEmpty()) {
                jwtTemplateCombo.getItems().addAll(
                        "OAuth2 Access Token (JWT)",
                        "OIDC ID Token",
                        "DPoP Proof",
                        "Custom (Empty)");
                jwtTemplateCombo.setOnAction(e -> {
                    String sel = jwtTemplateCombo.getValue();
                    if (sel == null)
                        return;
                    String tmpl = "{}";
                    long now = System.currentTimeMillis() / 1000;
                    if (sel.contains("Access Token")) {
                        tmpl = "{\n  \"iss\": \"https://auth.server.com\",\n  \"sub\": \"user_123\",\n  \"aud\": \"https://api.server.com\",\n  \"iat\": "
                                + now + ",\n  \"exp\": " + (now + 3600) + ",\n  \"scope\": \"read write\"\n}";
                    } else if (sel.contains("ID Token")) {
                        tmpl = "{\n  \"iss\": \"https://auth.server.com\",\n  \"sub\": \"user_123\",\n  \"aud\": \"client_id_456\",\n  \"iat\": "
                                + now + ",\n  \"exp\": " + (now + 3600) + ",\n  \"nonce\": \"n-0S6_WzA2Mj\"\n}";
                    } else if (sel.contains("DPoP")) {
                        tmpl = "{\n  \"jti\": \"" + java.util.UUID.randomUUID().toString()
                                + "\",\n  \"htm\": \"POST\",\n  \"htu\": \"https://resource.server.org/protected\",\n  \"iat\": "
                                + now + "\n}";
                    }
                    if (jwtPayloadArea != null) {
                        jwtPayloadArea.setText(tmpl);
                    }
                });
            }
    }

    private void updateJwkInputPresentation() {
        boolean secret = jwkKeyTypeCombo != null && "OCT".equals(jwkKeyTypeCombo.getValue());
        if (jwkInputLabel != null) jwkInputLabel.setText(t(secret ? "module.jose.inputSecret" : "module.jose.inputPem"));
        if (jwkInputArea != null) jwkInputArea.setPromptText(t(secret ? "module.jose.inputSecretPrompt" : "module.jose.inputPemPrompt"));
        if (pemToJwkBtn != null) pemToJwkBtn.setText(t(secret ? "module.jose.secretToJwk" : "module.jose.pemToJwk"));
        if (jwkToPemBtn != null) jwkToPemBtn.setText(t(secret ? "module.jose.jwkToSecret" : "module.jose.jwkToPem"));
    }

    @FXML
    public void handlePopulateJwtKeyShelf() {
        IngestionUIHelper.populateShelfMenu(jwtKeyShelfMenu, jwtKeyArea, null, null,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_PRIVATE_KEY,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.HEX,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.TEXT_UNKNOWN);
    }

    @FXML
    public void handlePopulateJwtValidateKeyShelf() {
        IngestionUIHelper.populateShelfMenu(jwtValidateKeyShelfMenu, jwtValidateKeyArea, null, null,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_PUBLIC_KEY,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_CERTIFICATE,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.HEX,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.TEXT_UNKNOWN);
    }

    @FXML
    public void handlePopulateJwePubKeyShelf() {
        IngestionUIHelper.populateShelfMenu(jwePubKeyShelfMenu, jwePublicKeyArea, null, null,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_PUBLIC_KEY,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_CERTIFICATE, com.cryptocarver.model.MaterialDetectionResult.MaterialType.HEX, com.cryptocarver.model.MaterialDetectionResult.MaterialType.TEXT_UNKNOWN);
    }

    @FXML
    public void handlePopulateJwePrivKeyShelf() {
        IngestionUIHelper.populateShelfMenu(jwePrivKeyShelfMenu, jwePrivateKeyArea, null, null,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_PRIVATE_KEY,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.HEX, com.cryptocarver.model.MaterialDetectionResult.MaterialType.TEXT_UNKNOWN);
    }

    public void showSection(String sectionName) {
        if (joseContainer != null) {
            joseContainer.setManaged(true);
            joseContainer.setVisible(true);
        }
        if (jwtSection != null) {
            jwtSection.setManaged(false);
            jwtSection.setVisible(false);
        }
        if (jweSection != null) {
            jweSection.setManaged(false);
            jweSection.setVisible(false);
        }
        if (jwkSection != null) {
            jwkSection.setManaged(false);
            jwkSection.setVisible(false);
        }

        if (jwaSection != null) {
            jwaSection.setManaged(false);
            jwaSection.setVisible(false);
        }
        if (inspectorSection != null) {
            inspectorSection.setManaged(false);
            inspectorSection.setVisible(false);
        }

        if (sectionName == null)
            return;

        if (sectionName.startsWith("JWT")) {
            if (jwtSection != null) {
                jwtSection.setManaged(true);
                jwtSection.setVisible(true);
            }
        } else if (sectionName.startsWith("JWE")) {
            if (jweSection != null) {
                jweSection.setManaged(true);
                jweSection.setVisible(true);
            }
        } else if (sectionName.startsWith("JWK")) {
            if (jwkSection != null) {
                jwkSection.setManaged(true);
                jwkSection.setVisible(true);
            }
        } else if (sectionName.startsWith("JWA")) {
            if (jwaSection != null) {
                jwaSection.setManaged(true);
                jwaSection.setVisible(true);
            }
        } else if (sectionName.startsWith("Token Inspector")) {
            if (inspectorSection != null) {
                inspectorSection.setManaged(true);
                inspectorSection.setVisible(true);
            }
        }

    }

    @FXML
    public void handleReset() {
        ModuleResetPolicy.apply(joseContainer, ModuleResetPolicy.Action.RESET_DEFAULTS,
                this::clearModuleData, this::restoreSafeDefaults);
        updateStatus(t("module.jose.resetStatus"));
    }

    @FXML
    public void handleClear() {
        ModuleResetPolicy.apply(joseContainer, ModuleResetPolicy.Action.CLEAR,
                this::clearModuleData, null);
        updateStatus(t("module.jose.clearStatus"));
    }

    private void clearModuleData() {
        ModuleResetPolicy.clearTextInputs(joseContainer);
        if (inspectorOutputFlow != null) inspectorOutputFlow.getChildren().clear();
    }

    private void restoreSafeDefaults() {
        if (jwtAlgoCombo != null && !jwtAlgoCombo.getItems().isEmpty()) jwtAlgoCombo.getSelectionModel().selectFirst();
        if (jwsSerializationCombo != null) jwsSerializationCombo.setValue("Compact");
        if (detachedSerializationCombo != null) detachedSerializationCombo.setValue("Compact");
        if (jwsUnencodedPayloadCheck != null) jwsUnencodedPayloadCheck.setSelected(false);
        showSection("JWT (Signed)");
    }

    public void fillJwtPayload(String value) {
        if (jwtPayloadArea != null) jwtPayloadArea.setText(value == null ? "" : value);
        showSection("JWT (Signed)");
    }

@FXML
    private ComboBox<String> jwtAlgo2Combo;
@FXML private Label detachedStatusLabel;
@FXML
    private TextField jwtAudField;
@FXML
    private TextArea jwtKeyArea2;
@FXML
    private CheckBox jwsUnencodedPayloadCheck;
@FXML
    private TableView<SimpleAlgo> jwaTable;
@FXML
    private TextArea jwtDecodedPayloadArea;
@FXML
    private Button jwkToPemBtn;
@FXML
    private TableColumn<SimpleAlgo, String> jwaDescCol;
@FXML
    private VBox jweSection;
@FXML
    private TextArea jwePrivateKeyArea;
@FXML
    private TextArea jweIVArea;
@FXML
    private TextArea jwtKeyArea;
@FXML private MenuButton jwtKeyShelfMenu;
@FXML private MenuButton jwtValidateKeyShelfMenu;
@FXML private MenuButton jwePubKeyShelfMenu;
@FXML private MenuButton jwePrivKeyShelfMenu;
@FXML
    private Label jwkInputLabel;
@FXML
    private ComboBox<String> nestedContentAlgoCombo;
@FXML
    private TextField jwtExpectedAudField;
@FXML
    private CheckBox jwtOidcStrictCheck;
@FXML
    private TextArea jweDecodedHeaderArea;
@FXML
    private VBox jwtSection;
@FXML
    private ComboBox<String> jwkKeyTypeCombo;
@FXML
    private TextArea jwtValidateKeyArea;
@FXML
    private CheckBox nestedCompressCheck;
@FXML
    private TableColumn<SimpleAlgo, String> jwaNameCol;
@FXML private CheckBox detachedUnencodedCheck;
@FXML
    private TextArea inspectorInputArea;
@FXML
    private TextArea jwtPayloadArea;
    @FXML private TextArea jwtProtectedHeaderArea;
    @FXML private Label jwtProtectedHeaderLabel;
@FXML
    private ComboBox<String> jwtTemplateCombo;
@FXML
    private TextField jwtExpectedIssField;
@FXML
    private TextArea jwkOutputArea;
@FXML
    private Label jweStatusLabel;
@FXML
    private TextField jwtClockSkewField;
@FXML
    private ComboBox<String> jwksRotateAlgoCombo;
@FXML
    private CheckBox jweCompressCheck;
@FXML
    private TextArea jweCiphertextArea;
@FXML
    private ComboBox<String> jweContentAlgoCombo;
@FXML
    private TextArea jweOutputArea;
@FXML
    private TextArea nestedOutputArea;
@FXML
    private ComboBox<String> nestedKeyAlgoCombo;
@FXML
    private TextArea jweAuthTagArea;
@FXML
    private TextArea nestedSigningKeyArea;
@FXML
    private TextArea jwePayloadArea;
@FXML
    private Label jwtStatusLabel;
@FXML
    private TextArea jwksArea;
@FXML
    private TextArea jwtValidateTokenArea;
@FXML
    private TextField jwtIssField;
@FXML
    private TextField jwtExpField;
@FXML
    private TextArea jweInputArea;
@FXML
    private TextArea nestedPayloadArea;
@FXML
    private TextArea jwkInputArea;
@FXML
    private ComboBox<String> jwtAlgoCombo;
@FXML
    private Label nestedStatusLabel;
@FXML
    private TextArea jwtDecodedHeaderArea;
@FXML
    private TableColumn<SimpleAlgo, String> jwaTypeCol;
    @FXML
    private CheckBox jwtCheckExpiryCheck;
    @FXML private CheckBox jwtAcceptNoneCheck;
    @FXML private CheckBox jwtTrustHeaderKeyCheck;
    @FXML private Label jwtSecurityWarningLabel, detachedSecurityWarningLabel;
    @FXML private Label jweSecurityWarningLabel, nestedSecurityWarningLabel;
@FXML
    private TextField jwkKeyIdField;
    @FXML private ComboBox<String> jwkUseCombo;
    @FXML private TextField jwkKeyOpsField;
    @FXML private Label jwkUseLabel, jwkKeyOpsLabel;
    @FXML private Button jwkInspectMetadataBtn;
@FXML
    private TextArea jwePublicKeyArea;
@FXML private TextArea detachedPayloadArea, detachedTokenArea, detachedSigningKeyArea, detachedVerificationKeyArea;
@FXML
    private VBox inspectorSection;
@FXML
    private TextArea jweDecodedPayloadArea;
@FXML
    private TextField jwtSubField;
@FXML
    private ComboBox<String> nestedSignAlgoCombo;
@FXML
    private ComboBox<String> jweKeyAlgoCombo;
@FXML private ComboBox<String> detachedAlgoCombo;
@FXML
    private ComboBox<String> jwsSerializationCombo;
@FXML
    private TextFlow inspectorOutputFlow;
@FXML
    private TextArea jweHeaderArea;
@FXML
    private VBox jwkSection;
@FXML
    private TextArea jweEncryptedKeyArea;
@FXML private ComboBox<String> detachedSerializationCombo;
@FXML
    private VBox jwaSection;
@FXML
    private Button pemToJwkBtn;
@FXML
    private TextArea jwtOutputArea;
@FXML
    private VBox joseContainer;
@FXML
    private TextArea jweDecryptedKeyArea;
@FXML
    private TextArea nestedPayloadOutputArea;
@FXML
    private TextArea nestedEncryptionKeyArea;
    @FXML
    private void handleLoadNestedEncryptionKey() {
        File file = chooseFile("Load Encryption Key");
        if (file != null) {
            try {
                nestedEncryptionKeyArea.setText(Files.readString(file.toPath()));
            } catch (Exception e) {
                showError("Error", t("module.jose.feedback.fileRead", e.getMessage()));
            }
        }
    }
    @FXML
    public void handlePemToJwk() { jwkCoordinator().handlePemToJwk(); }

    @FXML
    private void handleLoadJWKS() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Load JWKS File");
        fileChooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("JSON Files", "*.json"));
        java.io.File file = fileChooser.showOpenDialog(jwtPayloadArea.getScene().getWindow());
        if (file != null) {
            try {
                String content = java.nio.file.Files.readString(file.toPath());
                jwkCoordinator().loadedJWKS(content);

            } catch (Exception e) {
                showError("Load Error", t("module.jose.feedback.fileRead", e.getMessage()));
            }
        }
    }
    @FXML
    private void handleApplyJWTClaims() { jwtCoordinator().handleApplyJWTClaims(); }
    @FXML
    private void handleVerifyDetachedJWS() { jwtCoordinator().handleVerifyDetachedJWS(); }
    @FXML
    private void handleVerifyNestedJWT() { jwtCoordinator().handleVerifyNestedJWT(); }

    @FXML
    private void handleCopyInspectorOutput() {
        String report = getInspectorReportText();
        if (!report.isBlank()) {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
            cc.putString(report);
            clipboard.setContent(cc);
            updateStatus(t("module.jose.feedback.copied"));
        } else {
            showValidation(t("module.jose.feedback.copyEmpty"), "inspectorInputArea");
        }
    }
    @FXML
    private void handleGenerateDetachedJWS() { jwtCoordinator().handleGenerateDetachedJWS(); }

    @FXML
    private void handleGenerateNestedJWT() { jwtCoordinator().handleGenerateNestedJWT(); }

    @FXML
    private void handleLoadNestedSigningKey() {
        File file = chooseFile("Load Signing Key");
        if (file != null) {
            try {
                nestedSigningKeyArea.setText(Files.readString(file.toPath()));
            } catch (Exception e) {
                showError("Error", t("module.jose.feedback.fileRead", e.getMessage()));
            }
        }
    }
    @FXML
    public void handleCalculateThumbprint() { jwkCoordinator().handleCalculateThumbprint(); }

    @FXML
    private void handleLoadJWEPrivateKey() {
        File file = chooseFile("Load Private Key");
        if (file != null) {
            try {
                jwePrivateKeyArea.setText(Files.readString(file.toPath()));
            } catch (Exception e) {
                showError("Error", t("module.jose.feedback.fileRead", e.getMessage()));
            }
        }
    }
    @FXML
    private void handleLoadJWEPublicKey() {
        File file = chooseFile("Load Public Key");
        if (file != null) {
            try {
                jwePublicKeyArea.setText(Files.readString(file.toPath()));
            } catch (Exception e) {
                showError("Error", t("module.jose.feedback.fileRead", e.getMessage()));
            }
        }
    }
    @FXML
    private void handleOpenExpandedInspectorReport() {
        String report = getInspectorReportText();
        if (report.isBlank()) {
            showInfo("No report available", "Analyze a token before opening the expanded viewer.");
            return;
        }
        javafx.stage.Window owner = jwtPayloadArea == null || jwtPayloadArea.getScene() == null
                ? null : jwtPayloadArea.getScene().getWindow();
        expandedInspectorViewer.show(owner, "Expanded Result — Token Inspector", report);
    }
    @FXML
    private void handleClearInspector() {
        if (inspectorInputArea != null)
            inspectorInputArea.clear();
        if (inspectorOutputFlow != null)
            inspectorOutputFlow.getChildren().clear();
    }
    @FXML
    private void handleGenerateSignedJWT() { jwtCoordinator().handleGenerateSignedJWT(); }

    @FXML
    private void handleNewJWKS() { jwkCoordinator().handleNewJWKS(); }
    @FXML
    private void handleValidateJWT() { jwtCoordinator().handleValidateJWT(); }

    @FXML
    private void handleRotateKey() { jwkCoordinator().handleRotateKey(); }
    @FXML
    private void handleGenerateJWE() { jweCoordinator().handleGenerateJWE(); }

    @FXML
    private void handleDecryptJWE() { jweCoordinator().handleDecryptJWE(); }

    @FXML
    private void handleInspectToken() {
        if (inspectorInputArea != null && inspectorOutputFlow != null) {
            if (isBlank(inspectorInputArea)) {
                showValidation(t("module.jose.feedback.inputRequired"), "inspectorInputArea");
                return;
            }
            this.inspectToken(inspectorInputArea.getText(), inspectorOutputFlow);

        }
    }
    @FXML
    private void handleInspectJwkMetadata() { jwkCoordinator().handleInspectJwkMetadata(); }
    @FXML
    private void handleLoadJWTKey() {
        File file = chooseFile("Load Signing Key");
        if (file != null) {
            try {
                String content = Files.readString(file.toPath());
                jwtKeyArea.setText(content);
            } catch (Exception e) {
                showError("Load Error", t("module.jose.feedback.fileRead", e.getMessage()));
            }
        }
    }
    @FXML
    private void handleExportPublicJWKS() {

        try {
            String json = jwksArea.getText();
            String publicJson = this.exportPublicJWKS(json);
            TextArea area = new TextArea(publicJson);
            area.setEditable(false);
            area.setWrapText(true);
            area.setPrefSize(500, 300);
            dialogService.show(Alert.AlertType.INFORMATION, null, "Public JWKS", "Public Keys Only", area, ButtonType.OK);
        } catch (Exception e) {
            showError("Export Error", e.getMessage());
        }
    }
    @FXML
    public void handleJwkToPem() { jwkCoordinator().handleJwkToPem(); }

    @FXML
    private void handleLoadJWTValidateKey() {
        File file = chooseFile("Load Verification Key");
        if (file != null) {
            try {
                String content = Files.readString(file.toPath());
                jwtValidateKeyArea.setText(content);
            } catch (Exception e) {
                showError("Load Error", t("module.jose.feedback.fileRead", e.getMessage()));
            }
        }
    }

    private java.io.File chooseFile(String title) {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle(title);
        javafx.stage.Window owner = null;
        if (jwtPayloadArea != null && jwtPayloadArea.getScene() != null) {
            owner = jwtPayloadArea.getScene().getWindow();
        }
        return fileChooser.showOpenDialog(owner);
    }

    /** Imports a PEM or JSON JWK into the JWK workspace. Invoked by the global File menu bridge. */
    public void importKeyFromFile() {
        File file = chooseFile("Import Key File");
        if (file == null) {
            return;
        }
        try {
            String content = Files.readString(file.toPath());
            boolean isJwk = content.contains("{") && content.contains("\"kty\"");
            boolean isPem = content.contains("BEGIN PRIVATE KEY") || content.contains("BEGIN PUBLIC KEY");
            if (!isJwk && !isPem) {
                showError("Import Error", t("module.jose.feedback.keyFormat"));
                return;
            }
            showSection("JWK (Keys)");
            jwkInputArea.setText(content);
            updateStatus(t(isJwk ? "module.jose.feedback.importedJwk" : "module.jose.feedback.importedPem"));
        } catch (Exception exception) {
            showError("Import Error", t("module.jose.feedback.fileRead", exception.getMessage()));
        }
    }

    private void showError(String title, String content) {
        if (statusReporter != null) {
            statusReporter.showError(title, content);
        }
    }

    private static String textOf(TextInputControl control) {
        return control == null ? null : control.getText();
    }

    private static Set<String> parseHeaderNames(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return java.util.Arrays.stream(value.split(",")).map(String::trim).filter(name -> !name.isEmpty())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static JoseKeyMaterial.SecretEncoding secretEncoding(ComboBox<String> combo) {
        return JoseKeyMaterial.SecretEncoding.fromLabel(combo == null ? null : combo.getValue());
    }

    private static boolean isBlank(TextInputControl control) {
        return control == null || control.getText() == null || control.getText().isBlank();
    }

    private void showValidation(String detail, String fieldKey) {
        showValidation(detail, fieldKey, "preflight.remedy.input");
    }

    private void showValidation(String detail, String fieldKey, String remedyKey) {
        InlineValidationSupport.show(statusReporter, t("preflight.title"), detail,
                t(remedyKey), fieldKey, null);
    }

    private void showInfo(String title, String content) {
        if (statusReporter != null) {
            statusReporter.showInfo(title, content);
        }
    }

    private void updateStatus(String msg) {
        if (statusReporter != null) {
            statusReporter.updateStatus(msg);
        }
    }

    public String getInspectorReportText() {
        if (inspectorOutputFlow == null) return "";
        StringBuilder sb = new StringBuilder();
        for (javafx.scene.Node node : inspectorOutputFlow.getChildren()) {
            if (node instanceof javafx.scene.text.Text) {
                sb.append(((javafx.scene.text.Text)node).getText());
            }
        }
        return sb.toString();
    }




    private static final Logger LOG = LoggerFactory.getLogger(JOSEController.class);

    private StatusReporter statusReporter;

    /** Required by FXMLLoader when this controller is used from an fx:include. */
    public JOSEController() {
    }

    public void generateDetachedJWS(String payload, String algorithm, String key, JoseKeyMaterial.SecretEncoding secretEncoding,
            String serializationType, boolean unencodedPayload, TextArea output) { jwtCoordinator().generateDetachedJWS(payload, algorithm, key, secretEncoding, serializationType, unencodedPayload, output); }

    public void verifyDetachedJWS(String detached, String payload, String algorithm, String key,
            JoseKeyMaterial.SecretEncoding secretEncoding, Label status) { jwtCoordinator().verifyDetachedJWS(detached, payload, algorithm, key, secretEncoding, status); }



    public JOSEController(StatusReporter statusReporter) {
        this.statusReporter = statusReporter;
    }

    /** Connects the child module to the application-wide result publisher. */
    public void setReporter(StatusReporter statusReporter) {
        this.statusReporter = statusReporter;
    }

    // --- JWT (Signed) ---
    public void generateSignedJWT(String payloadJson, java.util.List<SignerConfig> signers, String serializationType, boolean unencodedPayload, TextArea outputArea) { jwtCoordinator().generateSignedJWT(payloadJson, signers, serializationType, unencodedPayload, outputArea); }

    public void generateSignedJWT(String payloadJson, java.util.List<SignerConfig> signers, String serializationType,
            boolean unencodedPayload, String protectedHeaderJson, TextArea outputArea) { jwtCoordinator().generateSignedJWT(payloadJson, signers, serializationType, unencodedPayload, protectedHeaderJson, outputArea); }

    // --- Nested JWT (Sign then Encrypt) ---
    public void generateNestedJWT(String payloadJson, String signAlgoStr, String signKey,
            String keyAlgoStr, String contentAlgoStr, String encKeyPEM, boolean compress,
            JoseKeyMaterial.SecretEncoding secretEncoding, TextArea outputArea) { jwtCoordinator().generateNestedJWT(payloadJson, signAlgoStr, signKey, keyAlgoStr, contentAlgoStr, encKeyPEM, compress, secretEncoding, outputArea); }

    public void verifyNestedJWT(String nestedToken, String decryptionKeyPEM, String verificationKeyPEM,
            JoseKeyMaterial.SecretEncoding secretEncoding, TextArea payloadOut, Label statusLabel) { jwtCoordinator().verifyNestedJWT(nestedToken, decryptionKeyPEM, verificationKeyPEM, secretEncoding, payloadOut, statusLabel); }

    @FXML private ComboBox<String> jwtSecretFormatCombo;
    @FXML private TextArea jwtFindingsArea;
    @FXML private Label jwtAllowedAlgorithmsLabel;
    @FXML private TextField jwtAllowedAlgorithmsField;
    @FXML private Label jwtExpectedTypeLabel;
    @FXML private TextField jwtExpectedTypeField;
    @FXML private Label jwtExpectedContentTypeLabel;
    @FXML private TextField jwtExpectedContentTypeField;
    @FXML private Label jwtExpectedNonceLabel;
    @FXML private TextField jwtExpectedNonceField;
    @FXML private Label jwtAccessTokenLabel;
    @FXML private TextField jwtAccessTokenField;
    @FXML private Label jwtAuthorizationCodeLabel;
    @FXML private TextField jwtAuthorizationCodeField;
    @FXML private Label jwtExpectedJktLabel;
    @FXML private TextField jwtExpectedJktField;
    @FXML private Label jwtExpectedX5tLabel;
    @FXML private TextField jwtExpectedX5tField;
    @FXML private Label jwtUnderstoodCritLabel;
    @FXML private TextField jwtUnderstoodCritField;
    @FXML private CheckBox jwtRfc9068Check;
    @FXML private CheckBox jwtIgnoreCritCheck;
    @FXML private ComboBox<String> jwtValidateSecretFormatCombo;
    @FXML private ComboBox<String> detachedSecretFormatCombo;
    @FXML private ComboBox<String> nestedSecretFormatCombo;
    @FXML private ComboBox<String> jweKeyFormatCombo;
    @FXML private ComboBox<String> jweSerializationCombo;
    @FXML private TextField jweAadField;
    @FXML private TextField jwePbes2IterField;
    @FXML private TextField jweKidField;
    @FXML private TextField jweTypField;
    @FXML private TextField jweCtyField;
    @FXML private TextField jweApuField;
    @FXML private TextField jweApvField;
    @FXML private TextArea jweCustomHeaderArea;
    @FXML private ComboBox<String> jweDecryptKeyFormatCombo;

    // --- JWE (Encrypted) ---
    public void generateJWE(String payload, String keyAlgo, String contentAlgo, String keyMaterial, boolean compress,
            JweComposer.HeaderOptions headerOptions, JoseKeyMaterial.SecretEncoding secretEncoding,
            int pbes2Iterations, JweComposer.Serialization serialization, String aad, TextArea outputArea) { jweCoordinator().generateJWE(payload, keyAlgo, contentAlgo, keyMaterial, compress, headerOptions, secretEncoding, pbes2Iterations, serialization, aad, outputArea); }

    public void decryptJWE(String jweString, String privateKeyPEM, JoseKeyMaterial.SecretEncoding secretEncoding,
            TextArea headerOut, TextArea payloadOut,
            TextArea jweHeaderArea, TextArea jweEncryptedKeyArea, TextArea jweDecryptedKeyArea,
            TextArea jweIVArea, TextArea jweCiphertextArea, TextArea jweAuthTagArea,
            Label statusLabel) { jweCoordinator().decryptJWE(jweString, privateKeyPEM, secretEncoding, headerOut, payloadOut, jweHeaderArea, jweEncryptedKeyArea, jweDecryptedKeyArea, jweIVArea, jweCiphertextArea, jweAuthTagArea, statusLabel); }

    OperationResult buildJweDecryptionResult(String jweString, String payload, JWEObject jweObject) { return jweCoordinator().buildJweDecryptionResult(jweString, payload, jweObject); }

    OperationResult buildJweDecryptionResult(String jweString, String payload, JWEObject jweObject, String keyMaterial) { return jweCoordinator().buildJweDecryptionResult(jweString, payload, jweObject, keyMaterial); }

    static String directCekPreviewMessage() { return JoseJweCoordinator.directCekPreviewMessage(); }

    // --- Enterprise Features (level 4 & 5) ---

    // JWK generation and JWKS manipulation are owned by the lazy coordinator.
    public JWK generateNewJWK(String alg, String use) throws Exception { return jwkCoordinator().generateNewJWK(alg, use); }
    public String addToJWKSet(String currentJson, JWK newKey) throws Exception { return jwkCoordinator().addToJWKSet(currentJson, newKey); }
    public String exportPublicJWKS(String json) throws Exception { return jwkCoordinator().exportPublicJWKS(json); }

    // 2. Advanced Validation
    public void validateJWTAdvanced(String tokenString, String keyString,
            String expectedIss, String expectedAud, long clockSkewSec, boolean checkExpiry, boolean oidcStrict,
            JoseKeyMaterial.SecretEncoding secretEncoding, TextArea headerOut, TextArea payloadOut, Label statusLabel) { jwtCoordinator().validateJWTAdvanced(tokenString, keyString, expectedIss, expectedAud, clockSkewSec, checkExpiry, oidcStrict, secretEncoding, headerOut, payloadOut, statusLabel); }

    public void validateJWTAdvanced(String tokenString, String keyString,
            String expectedIss, String expectedAud, long clockSkewSec, boolean checkExpiry, boolean oidcStrict,
            boolean trustHeaderKey, JoseKeyMaterial.SecretEncoding secretEncoding,
            TextArea headerOut, TextArea payloadOut, Label statusLabel) { jwtCoordinator().validateJWTAdvanced(tokenString, keyString, expectedIss, expectedAud, clockSkewSec, checkExpiry, oidcStrict, trustHeaderKey, secretEncoding, headerOut, payloadOut, statusLabel); }

    public void validateJWTAdvanced(String tokenString, String keyString,
            String expectedIss, String expectedAud, long clockSkewSec, boolean checkExpiry, boolean oidcStrict,
            boolean trustHeaderKey, JwtValidator.Advanced advanced, JoseKeyMaterial.SecretEncoding secretEncoding,
            TextArea headerOut, TextArea payloadOut, Label statusLabel) { jwtCoordinator().validateJWTAdvanced(tokenString, keyString, expectedIss, expectedAud, clockSkewSec, checkExpiry, oidcStrict, trustHeaderKey, advanced, secretEncoding, headerOut, payloadOut, statusLabel); }

    // --- Token Inspector (New Layer 6) ---
    public void inspectToken(String token, TextFlow outputFlow) {
        inspectorCoordinator.inspectToken(token, outputFlow);
    }

    // --- JWK Logic (Capa 5) ---
    public void convertPemToJwk(String pem, String keyType, String keyId, TextArea outputArea) {
        jwkCoordinator().convertPemToJwk(pem, keyType, keyId, outputArea);
    }

    public void convertPemToJwk(String pem, String keyType, String keyId, String use, String keyOps, TextArea outputArea) {
        jwkCoordinator().convertPemToJwk(pem, keyType, keyId, use, keyOps, outputArea);
    }

    static JWK asymmetricJwk(String keyMaterial, String keyType, String kid) throws Exception {
        return JoseJwkCoordinator.asymmetricJwk(keyMaterial, keyType, kid);
    }

    public void convertJwkToPem(String jwkJson, TextArea outputArea) {
        jwkCoordinator().convertJwkToPem(jwkJson, outputArea);
    }

    public void calculateThumbprint(String input, TextArea outputArea) {
        jwkCoordinator().calculateThumbprint(input, outputArea);
    }

    private void initJwaTable() {
        jwaNameCol.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().name()));
        jwaTypeCol.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().type()));
        jwaDescCol.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().description()));
        jwaTable.setItems(javafx.collections.FXCollections.observableArrayList(
                new SimpleAlgo("HS256 / HS384 / HS512", "Signature", "HMAC using SHA-2"),
                new SimpleAlgo("RS256 / RS384 / RS512", "Signature", "RSASSA-PKCS1-v1_5 using SHA-2"),
                new SimpleAlgo("PS256 / PS384 / PS512", "Signature", "RSASSA-PSS using SHA-2 and MGF1"),
                new SimpleAlgo("ES256 / ES384 / ES512", "Signature", "ECDSA using P-256 / P-384 / P-521"),
                new SimpleAlgo("EdDSA", "Signature", "Ed25519 / Ed448 (RFC 8037)"),
                new SimpleAlgo("RSA-OAEP-256 / 384 / 512", "Key Management", "RSAES OAEP using SHA-2 and MGF1"),
                new SimpleAlgo("ECDH-ES", "Key Management", "ECDH-ES direct key agreement (Concat KDF)"),
                new SimpleAlgo("ECDH-ES+A128KW / A192KW / A256KW", "Key Management", "ECDH-ES with AES Key Wrap"),
                new SimpleAlgo("A128KW / A192KW / A256KW", "Key Management", "AES Key Wrap with a shared key"),
                new SimpleAlgo("A128GCMKW / A192GCMKW / A256GCMKW", "Key Management", "AES-GCM key wrap with a shared key"),
                new SimpleAlgo("PBES2-HS256+A128KW / …", "Key Management", "PBKDF2 password-based key wrap"),
                new SimpleAlgo("dir", "Key Management", "Direct use of a shared symmetric key as the CEK"),
                new SimpleAlgo("A128GCM / A192GCM / A256GCM", "Content Encryption", "AES GCM"),
                new SimpleAlgo("A128CBC-HS256 / A192CBC-HS384 / A256CBC-HS512", "Content Encryption",
                        "AES CBC with HMAC SHA-2 authentication")));
    }

    private record SimpleAlgo(String name, String type, String description) {
    }
}
