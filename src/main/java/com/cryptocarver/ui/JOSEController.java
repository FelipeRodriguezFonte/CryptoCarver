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
    public void handlePemToJwk() {

            if (isBlank(jwkInputArea)) {
                showValidation(t("module.jose.feedback.inputPem"), "jwkInputArea");
                return;
            }

            this.convertPemToJwk(jwkInputArea.getText(), jwkKeyTypeCombo.getValue(), jwkKeyIdField.getText(),
                    jwkUseCombo == null ? null : jwkUseCombo.getValue(), textOf(jwkKeyOpsField),
                    jwkOutputArea);

        }

    @FXML
    private void handleLoadJWKS() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Load JWKS File");
        fileChooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("JSON Files", "*.json"));
        java.io.File file = fileChooser.showOpenDialog(jwtPayloadArea.getScene().getWindow());
        if (file != null) {
            try {
                String content = java.nio.file.Files.readString(file.toPath());
                jwksArea.setText(content);
                updateStatus(t("module.jose.feedback.jwksLoaded"));

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
    public void handleCalculateThumbprint() {

            if (isBlank(jwkInputArea)) {
                showValidation(t("module.jose.feedback.thumbprintInput"), "jwkInputArea");
                return;
            }

            this.calculateThumbprint(jwkInputArea.getText(), jwkOutputArea);

        }

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
    private void handleNewJWKS() {
        if (jwksArea != null) {
            jwksArea.setText("{\n  \"keys\": []\n}");
        }
    }
    @FXML
    private void handleValidateJWT() { jwtCoordinator().handleValidateJWT(); }

    @FXML
    private void handleRotateKey() {

        try {
            String alg = jwksRotateAlgoCombo.getValue();
            if (alg == null) {
                showValidation(t("module.jose.feedback.algorithmRequired"), "jwksRotateAlgoCombo", "preflight.remedy.algorithm");
                return;
            }
            // Security Warning for Symmetric Keys in JWKS
            if ((alg.startsWith("HS") || alg.startsWith("A") || alg.equals("dir"))
                    && LabPrompt.JWKS_SECRET.shouldShow()) {
                String warningText = "You are adding a SYMMETRIC key (Secret) to this JWK Set.\n\n" +
                        "If you publish this JWKS file publicly (e.g. at .well-known/jwks.json), ANYONE will be able to read your secret key and forge tokens.\n\n"
                        + "Are you sure you want to proceed?";
                java.util.Optional<ButtonType> result = dialogService.show(Alert.AlertType.WARNING, null,
                        "Security Warning", "Symmetric Key in Public JWKS", new Label(warningText),
                        ButtonType.NO, ButtonType.YES);
                if (result.isEmpty() || result.get() != ButtonType.YES) {
                    return;
                }
            }
            String use = alg.startsWith("A") || alg.equals("dir") || alg.startsWith("RSA1_5")
                    || alg.startsWith("RSA-OAEP") || alg.startsWith("ECDH-ES") ? "enc" : "sig";
            com.nimbusds.jose.jwk.JWK newKey = JoseJwkPolicy.withMetadata(this.generateNewJWK(alg, use), use,
                    textOf(jwkKeyOpsField));
            String currentJson = jwksArea.getText();
            if (currentJson == null || currentJson.isBlank())
                currentJson = "{\"keys\":[]}";
            String newJson = this.addToJWKSet(currentJson, newKey);
            jwksArea.setText(newJson);
            updateStatus(t("module.jose.feedback.keyAdded", alg));

        } catch (Exception e) {
            showError("Rotate Key Error", e.getMessage());
        }
    }
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
    public void handleJwkToPem() {

            if (isBlank(jwkInputArea)) {
                showValidation(t("module.jose.feedback.inputPem"), "jwkInputArea");
                return;
            }

            this.convertJwkToPem(jwkInputArea.getText(), jwkOutputArea);

        }

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

    // 1. JWK Managemen
    public JWK generateNewJWK(String alg, String use) throws Exception {
        if (java.util.Set.of("RS256", "RS384", "RS512", "PS256", "PS384", "PS512").contains(alg)) {
            return new RSAKeyGenerator(2048)
                    .keyUse(use.equals("sig") ? KeyUse.SIGNATURE : KeyUse.ENCRYPTION)
                    .algorithm(new JWSAlgorithm(alg))
                    .keyID(UUID.randomUUID().toString())
                    .generate();
        } else if ("RSA1_5".equals(alg) || "RSA-OAEP".equals(alg)
                || "RSA-OAEP-256".equals(alg) || "RSA-OAEP-384".equals(alg) || "RSA-OAEP-512".equals(alg)) {
            return new RSAKeyGenerator(2048).keyUse(KeyUse.ENCRYPTION).algorithm(new JWEAlgorithm(alg))
                    .keyID(UUID.randomUUID().toString()).generate();
        } else if (alg.equals("EdDSA")) {
            java.security.KeyPair pair = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            return new OctetKeyPair.Builder(Curve.Ed25519,
                    Base64URL.encode(JoseKeyMaterial.rawEdPublicKey(pair.getPublic())))
                    .d(Base64URL.encode(JoseKeyMaterial.rawEdPrivateKey(pair.getPrivate())))
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.EdDSA)
                    .keyID(UUID.randomUUID().toString())
                    .build();
        } else if (alg.startsWith("ECDH-ES")) {
            boolean x448 = alg.contains("X448");
            Curve curve = x448 ? Curve.X448 : Curve.X25519;
            String xdhAlgorithm = x448 ? "X448" : "X25519";
            java.security.KeyPair pair = java.security.KeyPairGenerator.getInstance(xdhAlgorithm).generateKeyPair();
            return new OctetKeyPair.Builder(curve,
                    Base64URL.encode(JoseKeyMaterial.rawXPublicKey(pair.getPublic())))
                    .d(Base64URL.encode(JoseKeyMaterial.rawXPrivateKey(pair.getPrivate())))
                    .keyUse(KeyUse.ENCRYPTION)
                    .algorithm(new JWEAlgorithm("ECDH-ES-X448".equals(alg) ? "ECDH-ES" : alg))
                    .keyID(UUID.randomUUID().toString())
                    .build();
        } else if (alg.startsWith("ES")) {
            if ("ES256K".equals(alg)) {
                java.security.KeyPairGenerator generator = java.security.KeyPairGenerator.getInstance("EC",
                        new org.bouncycastle.jce.provider.BouncyCastleProvider());
                generator.initialize(new java.security.spec.ECGenParameterSpec("secp256k1"));
                java.security.KeyPair pair = generator.generateKeyPair();
                return new ECKey.Builder(Curve.SECP256K1,
                        (java.security.interfaces.ECPublicKey) pair.getPublic())
                        .privateKey(pair.getPrivate())
                        .keyUse(use.equals("sig") ? KeyUse.SIGNATURE : KeyUse.ENCRYPTION)
                        .algorithm(new JWSAlgorithm(alg))
                        .keyID(UUID.randomUUID().toString())
                        .build();
            }
            Curve curve = Curve.P_256;
            if (alg.contains("384"))
                curve = Curve.P_384;
            if (alg.contains("512"))
                curve = Curve.P_521;
            return new ECKeyGenerator(curve)
                    .keyUse(use.equals("sig") ? KeyUse.SIGNATURE : KeyUse.ENCRYPTION)
                    .algorithm(new JWSAlgorithm(alg))
                    .keyID(UUID.randomUUID().toString())
                    .generate();
        } else if (alg.startsWith("HS") || alg.startsWith("A") || alg.equals("dir")) {
            // Symmetric Key (oct)
            int bitLength = 256;
            if (alg.contains("128"))
                bitLength = 128;
            if (alg.contains("384"))
                bitLength = 384;
            if (alg.contains("512"))
                bitLength = 512;

            JWK key;
            if (alg.startsWith("HS") || alg.startsWith("A") || alg.equals("dir")) {
                LOG.debug("Generating symmetric JWK for algorithm {}", alg);
                key = new OctetSequenceKeyGenerator(bitLength)
                        .keyUse(use.equals("sig") ? KeyUse.SIGNATURE : KeyUse.ENCRYPTION)
                        .algorithm(new Algorithm(alg))
                        .keyID(UUID.randomUUID().toString())
                        .generate();
                LOG.debug("Generated symmetric JWK (key material intentionally omitted from logs)");
                return key;
            } else {
                return null;
            }
        } else {
            throw new IllegalArgumentException("Unsupported algorithm for JWK generation: " + alg);
        }
    }

    public String addToJWKSet(String currentJson, JWK newKey) throws Exception {
        JWKSet jwkSet;
        if (currentJson == null || currentJson.trim().isEmpty()) {
            jwkSet = new JWKSet(newKey);
        } else {
            try {
                jwkSet = JWKSet.parse(currentJson);
                List<JWK> keys = new ArrayList<>(jwkSet.getKeys());
                keys.add(newKey);
                jwkSet = new JWKSet(keys);
            } catch (java.text.ParseException e) {
                // If parse fails, decide whether to start fresh or throw
                if (currentJson.trim().length() > 20) {
                    throw new Exception("Failed to parse existing JWK Set: " + e.getMessage());
                }
                jwkSet = new JWKSet(newKey);
            }
        }
        // Force output of private/secret keys (false = do not exclude private keys)
        return new com.google.gson.Gson().toJson(jwkSet.toJSONObject(false));
    }

    public String exportPublicJWKS(String json) throws Exception {
        JWKSet jwkSet = JWKSet.parse(json);
        return jwkSet.toPublicJWKSet().toString();
    }

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
    // --- Token Inspector (New Layer 6) ---
    public void inspectToken(String token, TextFlow outputFlow) {
        outputFlow.getChildren().clear();
        inspectTokenRecursive(token, outputFlow, 0);
    }

    private void inspectTokenRecursive(String token, TextFlow outputFlow, int depth) {
        if (token == null || token.trim().isEmpty())
            return;

        String indent = "  ".repeat(depth);
        String prefix = depth > 0 ? indent + "↳ " : "";
        String[] parts = token.trim().split("\\.", -1);

        try {
            if (token.trim().startsWith("{")) {
                inspectJsonSerialization(token.trim(), outputFlow, depth, prefix);
            } else if (parts.length == 3) {
                // JWS
                addText(outputFlow, prefix + "[JWS Detected]\n", Color.LIGHTGREEN, true);
                addSection(outputFlow, "HEADER", parts[0], Color.RED, true, depth);
                String payload = addSection(outputFlow, "PAYLOAD", parts[1], Color.MAGENTA, true, depth);
                addSection(outputFlow, "SIGNATURE", parts[2], Color.CYAN, false, depth);

                // Recursion check on Payload
                if (payload != null && (payload.startsWith("ey") || payload.startsWith("{"))) {
                    // Check if it looks like a token
                    if (payload.split("\\.").length >= 3) {
                        addText(outputFlow, "\n" + indent + "=== NESTED TOKEN IN PAYLOAD ===\n", Color.GOLD, true);
                        inspectTokenRecursive(payload, outputFlow, depth + 1);
                    }
                }

            } else if (parts.length == 5) {
                // JWE
                addText(outputFlow, prefix + "[JWE Detected]\n", Color.LIGHTBLUE, true);
                String header = addSection(outputFlow, "HEADER", parts[0], Color.RED, true, depth);
                addSection(outputFlow, "ENCRYPTED KEY", parts[1], Color.ORANGE, false, depth);
                addSection(outputFlow, "IV", parts[2], Color.GREEN, false, depth);
                addSection(outputFlow, "CIPHERTEXT", parts[3], Color.BLUE, false, depth);
                addSection(outputFlow, "TAG", parts[4], Color.YELLOW, false, depth);

                // Hint for Nested JWE
                if (header != null && (header.contains("\"cty\":\"JWT\"") || header.contains("\"cty\": \"JWT\""))) {
                    addText(outputFlow, "\n" + indent
                            + " > NOTE: Header indicates 'cty':'JWT'. This JWE contains a Nested Token (likely Signed).\n",
                            Color.GOLD, true);
                    addText(outputFlow, indent
                            + " > Decrypt this token in the 'JWE' tab, then inspect the result to see the inner token.\n",
                            Color.GOLD, false);
                }
            } else {
                addText(outputFlow, prefix + "Unknown/Raw Data: " + token + "\n\n", Color.WHITE);
            }
        } catch (Exception e) {
            addText(outputFlow, prefix + "Error: " + e.getMessage() + "\n", Color.RED);
        }
    }

    // --- JWK Logic (Capa 5) ---

    public void convertPemToJwk(String pem, String keyType, String keyId, TextArea outputArea) {
        convertPemToJwk(pem, keyType, keyId, null, null, outputArea);
    }

    public void convertPemToJwk(String pem, String keyType, String keyId, String use, String keyOps, TextArea outputArea) {
        if (pem == null || pem.trim().isEmpty()) {
            outputArea.setText("Error: Input PEM is empty.");
            return;
        }
        try {
            String kid = keyId == null || keyId.isBlank() ? null : keyId.trim();
            JWK jwk = "OCT".equalsIgnoreCase(keyType)
                    ? new OctetSequenceKey.Builder(DataConverter.decodeBase64Flexible(pem.replaceAll("\\s+", "")))
                            .keyID(kid).build()
                    : asymmetricJwk(pem, keyType, kid);
            jwk = JoseJwkPolicy.withMetadata(jwk, use, keyOps);
            String thumbprint = jwk.computeThumbprint().toString();
            if (kid == null) jwk = withKeyId(jwk, thumbprint);

            outputArea.setText(jwk.toJSONString());
            outputArea.appendText("\n\n// Thumbprint (SHA-256): " + thumbprint);
        } catch (Exception e) {
            outputArea.setText("Error converting to JWK: " + e.getMessage());
            // No exception attached: parser messages may echo key bytes.
            LOG.error("PEM key import failed for key type {}", keyType);
        }
    }

    @FXML
    private void handleInspectJwkMetadata() {
        try {
            JWK key = JWK.parse(jwkInputArea.getText());
            Map<String, Object> metadata = new java.util.LinkedHashMap<>();
            metadata.put("kty", key.getKeyType().getValue());
            if (key.getKeyID() != null) metadata.put("kid", key.getKeyID());
            if (key.getKeyUse() != null) metadata.put("use", key.getKeyUse().identifier());
            if (key.getKeyOperations() != null) metadata.put("key_ops", key.getKeyOperations().stream()
                    .map(KeyOperation::identifier).sorted().toList());
            if (key.getAlgorithm() != null) metadata.put("alg", key.getAlgorithm().getName());
            jwkOutputArea.setText(com.nimbusds.jose.util.JSONObjectUtils.toJSONString(metadata));
        } catch (Exception e) {
            jwkOutputArea.setText(t("module.jose.jwkMetadataReadError", e.getMessage()));
        }
    }

    /** RSA or EC key as a JWK; keeps the private half when the input has one. */
    static JWK asymmetricJwk(String keyMaterial, String keyType, String kid) throws Exception {
        PublicKey publicKey = JoseKeyMaterial.publicKey(keyMaterial);
        PrivateKey privateKey = null;
        try {
            privateKey = JoseKeyMaterial.privateKey(keyMaterial);
        } catch (IllegalArgumentException publicOnly) {
            // public key, certificate or public JWK
        }
        if (publicKey instanceof RSAPublicKey rsa) {
            if (keyType != null && !"RSA".equalsIgnoreCase(keyType)) {
                throw new IllegalArgumentException("The key is RSA but key type " + keyType + " is selected.");
            }
            RSAKey.Builder builder = new RSAKey.Builder(rsa).keyID(kid);
            if (privateKey != null) builder.privateKey(privateKey);
            return builder.build();
        }
        if (publicKey instanceof java.security.interfaces.ECPublicKey ec) {
            if (keyType != null && !"EC".equalsIgnoreCase(keyType)) {
                throw new IllegalArgumentException("The key is EC but key type " + keyType + " is selected.");
            }
            ECKey.Builder builder = new ECKey.Builder(Curve.forECParameterSpec(ec.getParams()), ec).keyID(kid);
            if (privateKey != null) builder.privateKey(privateKey);
            return builder.build();
        }
        if (publicKey instanceof java.security.interfaces.EdECPublicKey) {
            if (keyType != null && !"OKP".equalsIgnoreCase(keyType)) {
                throw new IllegalArgumentException("The key is an Ed25519/Ed448 (OKP) key but key type " + keyType + " is selected.");
            }
            byte[] x = JoseKeyMaterial.rawEdPublicKey(publicKey);
            OctetKeyPair.Builder builder = new OctetKeyPair.Builder(x.length == 32 ? Curve.Ed25519 : Curve.Ed448,
                    Base64URL.encode(x)).keyID(kid);
            if (privateKey != null) builder.d(Base64URL.encode(JoseKeyMaterial.rawEdPrivateKey(privateKey)));
            return builder.build();
        }
        if (publicKey instanceof java.security.interfaces.XECPublicKey) {
            if (keyType != null && !"OKP".equalsIgnoreCase(keyType)) {
                throw new IllegalArgumentException("The key is an X25519/X448 (OKP) key but key type " + keyType + " is selected.");
            }
            boolean x25519 = "X25519".equalsIgnoreCase(JoseKeyMaterial.xdhCurveName(publicKey));
            OctetKeyPair.Builder builder = new OctetKeyPair.Builder(x25519 ? Curve.X25519 : Curve.X448,
                    Base64URL.encode(JoseKeyMaterial.rawXPublicKey(publicKey))).keyID(kid);
            if (privateKey != null) builder.d(Base64URL.encode(JoseKeyMaterial.rawXPrivateKey(privateKey)));
            return builder.build();
        }
        throw new IllegalArgumentException("Unsupported key algorithm: " + publicKey.getAlgorithm());
    }

    private static String pem(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n"
                + java.util.Base64.getMimeEncoder(64, new byte[] { '\n' }).encodeToString(der)
                + "\n-----END " + type + "-----\n";
    }

    private static JWK withKeyId(JWK jwk, String kid) {
        if (jwk instanceof RSAKey rsa) return new RSAKey.Builder(rsa).keyID(kid).build();
        if (jwk instanceof ECKey ec) return new ECKey.Builder(ec).keyID(kid).build();
        if (jwk instanceof OctetSequenceKey oct) return new OctetSequenceKey.Builder(oct).keyID(kid).build();
        if (jwk instanceof OctetKeyPair okp) return new OctetKeyPair.Builder(okp).keyID(kid).build();
        return jwk;
    }

    public void convertJwkToPem(String jwkJson, TextArea outputArea) {
        try {
            com.nimbusds.jose.jwk.JWK jwk = com.nimbusds.jose.jwk.JWK.parse(jwkJson);

            StringBuilder sb = new StringBuilder();

            if (jwk instanceof com.nimbusds.jose.jwk.RSAKey) {
                com.nimbusds.jose.jwk.RSAKey rsaKey = (com.nimbusds.jose.jwk.RSAKey) jwk;

                // Public
                sb.append("=== Public Key (PEM) ===\n");
                java.security.interfaces.RSAPublicKey pub = rsaKey.toRSAPublicKey();
                String pubPem = java.util.Base64.getMimeEncoder(64, new byte[] { '\n' })
                        .encodeToString(pub.getEncoded());
                sb.append("-----BEGIN PUBLIC KEY-----\n").append(pubPem).append("\n-----END PUBLIC KEY-----\n\n");

                // Private
                if (rsaKey.isPrivate()) {
                    sb.append("=== Private Key (PEM) ===\n");
                    java.security.interfaces.RSAPrivateKey priv = rsaKey.toRSAPrivateKey();
                    String privPem = java.util.Base64.getMimeEncoder(64, new byte[] { '\n' })
                            .encodeToString(priv.getEncoded());
                    sb.append("-----BEGIN PRIVATE KEY-----\n").append(privPem).append("\n-----END PRIVATE KEY-----\n");
                }

                outputArea.setText(sb.toString());

            } else if (jwk instanceof com.nimbusds.jose.jwk.ECKey) {
                com.nimbusds.jose.jwk.ECKey ecKey = (com.nimbusds.jose.jwk.ECKey) jwk;
                // Public
                sb.append("=== Public Key (PEM) ===\n");
                java.security.Provider provider = Curve.SECP256K1.equals(ecKey.getCurve())
                        ? new org.bouncycastle.jce.provider.BouncyCastleProvider() : null;
                java.security.interfaces.ECPublicKey pub = provider == null ? ecKey.toECPublicKey()
                        : ecKey.toECPublicKey(provider);
                String pubPem = java.util.Base64.getMimeEncoder(64, new byte[] { '\n' })
                        .encodeToString(pub.getEncoded());
                sb.append("-----BEGIN PUBLIC KEY-----\n").append(pubPem).append("\n-----END PUBLIC KEY-----\n\n");

                if (ecKey.isPrivate()) {
                    sb.append("=== Private Key (PEM) ===\n");
                    java.security.interfaces.ECPrivateKey priv = provider == null ? ecKey.toECPrivateKey()
                            : ecKey.toECPrivateKey(provider);
                    String privPem = java.util.Base64.getMimeEncoder(64, new byte[] { '\n' })
                            .encodeToString(priv.getEncoded());
                    sb.append("-----BEGIN PRIVATE KEY-----\n").append(privPem).append("\n-----END PRIVATE KEY-----\n");
                }
                outputArea.setText(sb.toString());
            } else if (jwk instanceof com.nimbusds.jose.jwk.OctetSequenceKey) {
                com.nimbusds.jose.jwk.OctetSequenceKey octKey = (com.nimbusds.jose.jwk.OctetSequenceKey) jwk;
                sb.append("=== Symmetric Key (Secret) ===\n");
                byte[] secret = octKey.toByteArray();

                sb.append("Length: ").append(secret.length * 8).append(" bits (").append(secret.length)
                        .append(" bytes)\n\n");

                sb.append("Hex:\n");
                for (byte b : secret) {
                    sb.append(String.format("%02x", b));
                }
                sb.append("\n\n");

                sb.append("Base64:\n");
                sb.append(java.util.Base64.getEncoder().encodeToString(secret)).append("\n\n");

                sb.append("Base64URL:\n");
                sb.append(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(secret));

                outputArea.setText(sb.toString());
            } else if (jwk instanceof OctetKeyPair okp) {
                String json = okp.toJSONString();
                sb.append("=== Public Key (PEM) ===\n");
                sb.append(pem("PUBLIC KEY", JoseKeyMaterial.publicKey(json).getEncoded())).append("\n");
                if (okp.isPrivate()) {
                    sb.append("=== Private Key (PEM) ===\n");
                    sb.append(pem("PRIVATE KEY", JoseKeyMaterial.privateKey(json).getEncoded()));
                }
                outputArea.setText(sb.toString());
            } else {
                outputArea.setText("Unsupported or Unknown Key Type for PEM export: " + jwk.getKeyType());
            }

        } catch (Exception e) {
            outputArea.setText("Error converting JWK to PEM: " + e.getMessage());
        }
    }

    public void calculateThumbprint(String input, TextArea outputArea) {
        try {
            // Heuristic: Is it JWK or PEM?
            if (input.trim().startsWith("{")) {
                // Assume JWK
                com.nimbusds.jose.jwk.JWK jwk = com.nimbusds.jose.jwk.JWK.parse(input);
                outputArea.setText("SHA-256 Thumbprint (RFC 7638):\n" + jwk.computeThumbprint().toString());
            } else {
                JWK jwk = asymmetricJwk(input, null, null);
                outputArea.setText("SHA-256 Thumbprint (RFC 7638):\n" + jwk.computeThumbprint().toString());
            }
        } catch (Exception e) {
            outputArea.setText("Error calculating thumbprint: " + e.getMessage());
        }
    }

    private String addSection(TextFlow flow, String title, String part, Color color, boolean isJson, int depth) {

        String indent = "  ".repeat(depth);
        addText(flow, indent + "=== " + title + " ===\n", color, true);
        addText(flow, indent + "Raw: " + part + "\n", Color.GRAY);

        String decodedContent = null;
        try {
            if (part.isEmpty()) {
                addText(flow, indent + "(Empty)\n\n", Color.WHITE);
                return null;
            }
            Base64URL b64 = new Base64URL(part);
            String decoded = b64.decodeToString();
            decodedContent = decoded;

            if (isJson) {
                try {
                    if (title.contains("HEADER")) {
                        decoded = prettyJson(decoded);
                    } else if (title.equals("PAYLOAD")) {
                        try {
                            decoded = com.nimbusds.jwt.JWTClaimsSet.parse(decoded).toString();
                        } catch (Exception e) {
                            // Plain text or token string
                        }
                    }
                } catch (Exception e) {
                    /* ignore */ }
            } else {
                byte[] bytes = b64.decode();
                decoded = "Hex: " + bytesToHex(bytes) + " (" + bytes.length + " bytes)";
            }
            // Indent decoded outpu
            decoded = decoded.replace("\n", "\n" + indent);
            addText(flow, indent + decoded + "\n\n", Color.WHITE);

        } catch (Exception e) {
            addText(flow, indent + "Could not decode: " + e.getMessage() + "\n\n", Color.RED);
        }
        return decodedContent;
    }

    /** Breaks down JWS or JWE JSON serialization (RFC 7515 §7.2, RFC 7516 §7.2). */
    private void inspectJsonSerialization(String json, TextFlow flow, int depth, String prefix) throws Exception {
        Map<String, Object> members = com.nimbusds.jose.util.JSONObjectUtils.parse(json);
        String indent = "  ".repeat(depth);
        if (members.containsKey("ciphertext")) {
            boolean general = members.containsKey("recipients");
            addText(flow, prefix + "[JWE JSON Detected — " + (general ? "General" : "Flattened") + "]\n", Color.LIGHTBLUE, true);
            addOptionalSection(flow, "PROTECTED HEADER", members.get("protected"), Color.RED, true, depth);
            if (members.get("unprotected") != null) addJsonMember(flow, "SHARED UNPROTECTED HEADER", members.get("unprotected"), depth);
            addOptionalSection(flow, "AAD", members.get("aad"), Color.MAGENTA, true, depth);
            List<Map<String, Object>> recipients = new ArrayList<>();
            if (general) {
                for (Map<String, Object> recipient : com.nimbusds.jose.util.JSONObjectUtils.getJSONObjectArray(members, "recipients")) {
                    recipients.add(recipient);
                }
            } else {
                recipients.add(members);
            }
            for (int i = 0; i < recipients.size(); i++) {
                String label = general ? "RECIPIENT " + (i + 1) + " " : "";
                if (recipients.get(i).get("header") != null) addJsonMember(flow, label + "HEADER", recipients.get(i).get("header"), depth);
                addOptionalSection(flow, label + "ENCRYPTED KEY", recipients.get(i).get("encrypted_key"), Color.ORANGE, false, depth);
            }
            addOptionalSection(flow, "IV", members.get("iv"), Color.GREEN, false, depth);
            addOptionalSection(flow, "CIPHERTEXT", members.get("ciphertext"), Color.BLUE, false, depth);
            addOptionalSection(flow, "TAG", members.get("tag"), Color.YELLOW, false, depth);
        } else if (members.containsKey("signatures") || members.containsKey("signature")) {
            boolean general = members.containsKey("signatures");
            addText(flow, prefix + "[JWS JSON Detected — " + (general ? "General" : "Flattened") + "]\n", Color.LIGHTGREEN, true);
            if (members.get("payload") == null) {
                addText(flow, indent + "=== PAYLOAD ===\n" + indent + "(Detached — not included)\n\n", Color.MAGENTA, true);
            } else {
                addSection(flow, "PAYLOAD", String.valueOf(members.get("payload")), Color.MAGENTA, true, depth);
            }
            List<Map<String, Object>> signatures = new ArrayList<>();
            if (general) {
                for (Map<String, Object> signature : com.nimbusds.jose.util.JSONObjectUtils.getJSONObjectArray(members, "signatures")) {
                    signatures.add(signature);
                }
            } else {
                signatures.add(members);
            }
            for (int i = 0; i < signatures.size(); i++) {
                String label = general ? "SIGNATURE " + (i + 1) + " " : "";
                addOptionalSection(flow, label + "PROTECTED HEADER", signatures.get(i).get("protected"), Color.RED, true, depth);
                if (signatures.get(i).get("header") != null) addJsonMember(flow, label + "UNPROTECTED HEADER", signatures.get(i).get("header"), depth);
                addOptionalSection(flow, label + "SIGNATURE", signatures.get(i).get("signature"), Color.CYAN, false, depth);
            }
        } else {
            addText(flow, prefix + "JSON without JWS/JWE members (no 'ciphertext', 'signature' or 'signatures').\n\n", Color.WHITE);
        }
    }

    private void addOptionalSection(TextFlow flow, String title, Object value, Color color, boolean isJson, int depth) {
        if (value != null) addSection(flow, title, String.valueOf(value), color, isJson, depth);
    }

    private void addJsonMember(TextFlow flow, String title, Object value, int depth) {
        String indent = "  ".repeat(depth);
        addText(flow, indent + "=== " + title + " ===\n", Color.RED, true);
        addText(flow, indent + prettyJson(new com.google.gson.Gson().toJson(value)).replace("\n", "\n" + indent) + "\n\n", Color.WHITE);
    }

    private static String prettyJson(String json) {
        try {
            return new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
                    .toJson(com.google.gson.JsonParser.parseString(json));
        } catch (Exception notJson) {
            return json;
        }
    }

    private void addText(TextFlow flow, String text, Color color) {
        addText(flow, text, color, false);
    }

    private void addText(TextFlow flow, String text, Color color, boolean bold) {
        Text t = new Text(text);
        t.setFill(color);
        t.setFont(Font.font("Monospaced", bold ? FontWeight.BOLD : FontWeight.NORMAL, 13));
        flow.getChildren().add(t);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
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
