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
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.security.spec.MGF1ParameterSpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;

import javafx.fxml.Initializable;
import java.net.URL;
import java.util.ResourceBundle;

public class JOSEController implements Initializable {

    private final ExpandedTextViewer expandedInspectorViewer = new ExpandedTextViewer();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Initialize Combo
            if (jwtAlgoCombo != null && jwtAlgoCombo.getItems().isEmpty()) {
                jwtAlgoCombo.getItems().addAll(
                        "HS256", "HS384", "HS512",
                        "RS256", "RS384", "RS512",
                        "ES256", "ES384", "ES512",
                        "PS256", "PS384", "PS512");
                jwtAlgoCombo.getSelectionModel().selectFirst();
            }
            if (jwtAlgoCombo2 != null && jwtAlgoCombo2.getItems().isEmpty()) {
                jwtAlgoCombo2.getItems().addAll(
                        "HS256", "HS384", "HS512",
                        "RS256", "RS384", "RS512",
                        "ES256", "ES384", "ES512",
                        "PS256", "PS384", "PS512");
                jwtAlgoCombo2.getSelectionModel().selectFirst();
            }

            // Init JWE Combos
            if (jweKeyAlgoCombo != null && jweKeyAlgoCombo.getItems().isEmpty()) {
                jweKeyAlgoCombo.getItems().setAll(
                        "RSA-OAEP-256", "RSA-OAEP-512",
                        "ECDH-ES", "ECDH-ES+A128KW", "ECDH-ES+A256KW");
                jweKeyAlgoCombo.getSelectionModel().selectFirst();
            }
            if (jweContentAlgoCombo != null && jweContentAlgoCombo.getItems().isEmpty()) {
                jweContentAlgoCombo.getItems().setAll("A128GCM", "A256GCM", "A128CBC-HS256", "A256CBC-HS512");
                jweContentAlgoCombo.getSelectionModel().select("A256GCM");
            }

            // Init Nested Combos
            if (nestedSignAlgoCombo != null && nestedSignAlgoCombo.getItems().isEmpty()) {
                nestedSignAlgoCombo.getItems().setAll("HS256", "HS384", "HS512", "RS256", "RS384", "RS512");
                nestedSignAlgoCombo.getSelectionModel().select("HS256");
            }
            if (nestedKeyAlgoCombo != null && nestedKeyAlgoCombo.getItems().isEmpty()) {
                nestedKeyAlgoCombo.getItems().setAll("RSA-OAEP-256", "RSA-OAEP-512");
                nestedKeyAlgoCombo.getSelectionModel().selectFirst();
            }
            if (nestedContentAlgoCombo != null && nestedContentAlgoCombo.getItems().isEmpty()) {
                nestedContentAlgoCombo.getItems().setAll("A128GCM", "A256GCM");
                nestedContentAlgoCombo.getSelectionModel().select("A256GCM");
            }

            // Init JWK Combo
            if (jwkKeyTypeCombo != null && jwkKeyTypeCombo.getItems().isEmpty()) {
                jwkKeyTypeCombo.getItems().setAll("RSA", "EC", "OCT");
                jwkKeyTypeCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
                    if (newV == null)
                        return;
                    if (newV.equals("OCT")) {
                        if (jwkInputLabel != null)
                            jwkInputLabel.setText("Input (Hex / Base64 Secret):");
                        if (jwkInputArea != null)
                            jwkInputArea.setPromptText("Paste Hex or Base64 Secret (e.g. 313233... or MTIz...)");
                        if (pemToJwkBtn != null)
                            pemToJwkBtn.setText("Secret -> JWK");
                        if (jwkToPemBtn != null)
                            jwkToPemBtn.setText("JWK -> Secret");
                    } else {
                        if (jwkInputLabel != null)
                            jwkInputLabel.setText("Input (PEM):");
                        if (jwkInputArea != null)
                            jwkInputArea.setPromptText("Paste PEM Key (e.g. -----BEGIN...)");
                        if (pemToJwkBtn != null)
                            pemToJwkBtn.setText("PEM -> JWK");
                        if (jwkToPemBtn != null)
                            jwkToPemBtn.setText("JWK -> PEM");
                    }
                });
                jwkKeyTypeCombo.getSelectionModel().selectFirst();
            }
            if (jwksRotateAlgoCombo != null && jwksRotateAlgoCombo.getItems().isEmpty()) {
                jwksRotateAlgoCombo.getItems().setAll(
                        "RS256", "RS384", "RS512", "ES256", "ES384", "ES512",
                        "HS256", "HS384", "HS512", "A128KW", "A256KW", "A128GCM", "A256GCM", "dir");
                jwksRotateAlgoCombo.getSelectionModel().selectFirst();
            }

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
    private ComboBox<String> jwtAlgoCombo2;
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
@FXML
    private TextField jwkKeyIdField;
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
                showError("Error", e.getMessage());
            }
        }
    }
    @FXML
    public void handlePemToJwk() {

            this.convertPemToJwk(jwkInputArea.getText(), jwkKeyTypeCombo.getValue(), jwkKeyIdField.getText(),
                    jwkOutputArea);
            // History
            java.util.Map<String, String> details = new java.util.HashMap<>();
            details.put("Key Type", jwkKeyTypeCombo.getValue());
            if (jwkKeyIdField.getText() != null && !jwkKeyIdField.getText().isEmpty()) {
                details.put("Key ID", jwkKeyIdField.getText());
            }

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
                updateStatus("JWKS loaded.");
                // History

            } catch (Exception e) {
                showError("Load Error", e.getMessage());
            }
        }
    }
    @FXML
    private void handleApplyJWTClaims() {
        if (jwtPayloadArea != null) {
            try {
                long now = System.currentTimeMillis() / 1000L;
                long expHours = 1;
                try {
                    expHours = Long.parseLong(jwtExpField.getText());
                } catch (NumberFormatException ignored) {
                }
                String json = String.format(
                        "{\n  \"iss\": \"%s\",\n  \"sub\": \"%s\",\n  \"aud\": \"%s\",\n  \"iat\": %d,\n  \"exp\": %d\n}",
                        jwtIssField.getText(),
                        jwtSubField.getText(),
                        jwtAudField.getText(),
                        now,
                        now + (expHours * 3600));
                jwtPayloadArea.setText(json);
            } catch (Exception e) {
                showError("Claims Error", e.getMessage());
            }
        }
    }
    @FXML
    private void handleVerifyDetachedJWS() { this.verifyDetachedJWS(detachedTokenArea.getText(), detachedPayloadArea.getText(), detachedAlgoCombo.getValue(), detachedVerificationKeyArea.getText(), detachedStatusLabel); }
    @FXML
    private void handleVerifyNestedJWT() {

            String nestedToken = nestedOutputArea.getText();
            String decryptionKey = nestedEncryptionKeyArea.getText();
            String verificationKey = nestedSigningKeyArea.getText();
            this.verifyNestedJWT(nestedToken, decryptionKey, verificationKey, nestedPayloadOutputArea, nestedStatusLabel);
        }

    @FXML
    private void handleCopyInspectorOutput() {
        String report = getInspectorReportText();
        if (!report.isBlank()) {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
            cc.putString(report);
            clipboard.setContent(cc);
            updateStatus("Copied Inspector report to clipboard!");
        } else {
            showError("Copy Error", "Nothing to copy.");
        }
    }
    @FXML
    private void handleGenerateDetachedJWS() {

            String serialization = detachedSerializationCombo != null ? detachedSerializationCombo.getValue() : "Compact";
            boolean unencoded = detachedUnencodedCheck != null && detachedUnencodedCheck.isSelected();
            this.generateDetachedJWS(detachedPayloadArea.getText(), detachedAlgoCombo.getValue(), detachedSigningKeyArea.getText(), serialization, unencoded, detachedTokenArea);
        }

    @FXML
    private void handleGenerateNestedJWT() {

            this.generateNestedJWT(
                    nestedPayloadArea.getText(),
                    nestedSignAlgoCombo.getValue(),
                    nestedSigningKeyArea.getText(),
                    nestedKeyAlgoCombo.getValue(),
                    nestedContentAlgoCombo.getValue(),
                    nestedEncryptionKeyArea.getText(),
                    nestedCompressCheck.isSelected(),
                    nestedOutputArea);
            Map<String, String> details = new HashMap<>();
            details.put("Sign Algo", nestedSignAlgoCombo.getValue());
            details.put("Key Algo", nestedKeyAlgoCombo.getValue());
            details.put("Content Algo", nestedContentAlgoCombo.getValue());
            details.put("Compression", nestedCompressCheck.isSelected() ? "Yes" : "No");

        }

    @FXML
    private void handleLoadNestedSigningKey() {
        File file = chooseFile("Load Signing Key");
        if (file != null) {
            try {
                nestedSigningKeyArea.setText(Files.readString(file.toPath()));
            } catch (Exception e) {
                showError("Error", e.getMessage());
            }
        }
    }
    @FXML
    public void handleCalculateThumbprint() {
        showSection("JWT");

            this.calculateThumbprint(jwkInputArea.getText(), jwkOutputArea);
            // History

        }

    @FXML
    private void handleLoadJWEPrivateKey() {
        File file = chooseFile("Load Private Key");
        if (file != null) {
            try {
                jwePrivateKeyArea.setText(Files.readString(file.toPath()));
            } catch (Exception e) {
                showError("Error", "Could not load file: " + e.getMessage());
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
                showError("Error", "Could not load file: " + e.getMessage());
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
    private void handleGenerateSignedJWT() {

            String algo = jwtAlgoCombo.getSelectionModel().getSelectedItem();
            String key = jwtKeyArea.getText();
            String serialization = jwsSerializationCombo != null ? jwsSerializationCombo.getValue() : "Compact";
            boolean unencoded = jwsUnencodedPayloadCheck != null && jwsUnencodedPayloadCheck.isSelected();
            java.util.List<com.cryptocarver.crypto.SignerConfig> signers = new java.util.ArrayList<>();
            signers.add(new com.cryptocarver.crypto.SignerConfig(algo, key));
            if (jwtAlgoCombo2 != null && jwtKeyArea2 != null && !jwtKeyArea2.getText().trim().isEmpty()) {
                signers.add(new com.cryptocarver.crypto.SignerConfig(jwtAlgoCombo2.getSelectionModel().getSelectedItem(), jwtKeyArea2.getText()));
            }
            this.generateSignedJWT(
                    jwtPayloadArea.getText(),
                    signers,
                    serialization,
                    unencoded,
                    jwtOutputArea);
            // Add to History
            Map<String, String> details = new HashMap<>();
            details.put("Algorithm", algo);
            details.put("Key/Secret", key.length() > 50 ? "Provided (Length: " + key.length() + ")" : key);
            details.put("Payload", jwtPayloadArea.getText());
            details.put("Output JWT", jwtOutputArea.getText());

        }

    @FXML
    private void handleNewJWKS() {
        if (jwksArea != null) {
            jwksArea.setText("{\n  \"keys\": []\n}");
        }
    }
    @FXML
    private void handleValidateJWT() {

            String iss = jwtExpectedIssField.getText();
            String aud = jwtExpectedAudField.getText();
            String skewStr = jwtClockSkewField.getText();
            long skew = 0;
            try {
                skew = Long.parseLong(skewStr);
            } catch (Exception e) {
            }
            boolean checkExp = jwtCheckExpiryCheck.isSelected();
            boolean oidcStrict = jwtOidcStrictCheck != null && jwtOidcStrictCheck.isSelected();
            this.validateJWTAdvanced(
                    jwtValidateTokenArea.getText(),
                    jwtValidateKeyArea.getText(),
                    iss, aud, skew, checkExp, oidcStrict,
                    jwtDecodedHeaderArea,
                    jwtDecodedPayloadArea,
                    jwtStatusLabel);
            // Add to History
            Map<String, String> details = new HashMap<>();
            details.put("Token", jwtValidateTokenArea.getText());
            details.put("Verification Key", jwtValidateKeyArea.getText().length() > 50 ? "Provided (PEM/Secret)"
                    : jwtValidateKeyArea.getText());
            details.put("Status", jwtStatusLabel.getText());

        }

    @FXML
    private void handleRotateKey() {

        try {
            String alg = jwksRotateAlgoCombo.getValue();
            if (alg == null) {
                showError("Rotate Error", "Select an algorithm first.");
                return;
            }
            // Security Warning for Symmetric Keys in JWKS
            if (alg.startsWith("HS") || alg.startsWith("A") || alg.equals("dir")) {
                Alert warning = new Alert(Alert.AlertType.WARNING);
                warning.setTitle("Security Warning");
                warning.setHeaderText("Symmetric Key in Public JWKS");
                warning.setContentText("You are adding a SYMMETRIC key (Secret) to this JWK Set.\n\n" +
                        "If you publish this JWKS file publicly (e.g. at .well-known/jwks.json), ANYONE will be able to read your secret key and forge tokens.\n\n"
                        +
                        "Are you sure you want to proceed?");
                warning.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
                java.util.Optional<ButtonType> result = warning.showAndWait();
                if (result.isEmpty() || result.get() != ButtonType.YES) {
                    return;
                }
            }
            com.nimbusds.jose.jwk.JWK newKey = this.generateNewJWK(alg, "sig");
            String currentJson = jwksArea.getText();
            if (currentJson == null || currentJson.isBlank())
                currentJson = "{\"keys\":[]}";
            String newJson = this.addToJWKSet(currentJson, newKey);
            jwksArea.setText(newJson);
            updateStatus("Added new " + alg + " key to JWKS.");
            // History
            java.util.Map<String, String> details = new java.util.HashMap<>();
            details.put("Algorithm", alg);

        } catch (Exception e) {
            showError("Rotate Key Error", e.getMessage());
        }
    }
    @FXML
    private void handleGenerateJWE() {

            this.generateJWE(
                    jwePayloadArea.getText(),
                    jweKeyAlgoCombo.getValue(),
                    jweContentAlgoCombo.getValue(),
                    jwePublicKeyArea.getText(),
                    jweCompressCheck.isSelected(),
                    jweOutputArea);
            Map<String, String> details = new HashMap<>(
                    Map.of("alg", jweKeyAlgoCombo.getValue(), "enc", jweContentAlgoCombo.getValue()));
            details.put("Compression", jweCompressCheck.isSelected() ? "Yes" : "No");

        }

    @FXML
    private void handleDecryptJWE() {

            this.decryptJWE(
                    jweInputArea.getText(),
                    jwePrivateKeyArea.getText(),
                    jweDecodedHeaderArea,
                    jweDecodedPayloadArea,
                    jweHeaderArea,
                    jweEncryptedKeyArea,
                    jweDecryptedKeyArea,
                    jweIVArea,
                    jweCiphertextArea,
                    jweAuthTagArea,
                    jweStatusLabel);

        }

    @FXML
    private void handleInspectToken() {
        if (inspectorInputArea != null && inspectorOutputFlow != null) {
            this.inspectToken(inspectorInputArea.getText(), inspectorOutputFlow);
            // History
            java.util.Map<String, String> details = new java.util.HashMap<>();
            if (inspectorInputArea.getText().length() > 50) {
                details.put("Token Preview", inspectorInputArea.getText().substring(0, 20) + "...");
            }

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
                showError("Load Error", "Could not read key file: " + e.getMessage());
            }
        }
    }
    @FXML
    private void handleExportPublicJWKS() {

        try {
            String json = jwksArea.getText();
            String publicJson = this.exportPublicJWKS(json);
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Public JWKS");
            alert.setHeaderText("Public Keys Only");
            TextArea area = new TextArea(publicJson);
            area.setEditable(false);
            area.setWrapText(true);
            area.setPrefSize(500, 300);
            alert.getDialogPane().setContent(area);
            alert.setResizable(true);
            alert.showAndWait();
        } catch (Exception e) {
            showError("Export Error", e.getMessage());
        }
    }
    @FXML
    public void handleJwkToPem() {

            this.convertJwkToPem(jwkInputArea.getText(), jwkOutputArea);
            // History

        }

    @FXML
    private void handleLoadJWTValidateKey() {
        File file = chooseFile("Load Verification Key");
        if (file != null) {
            try {
                String content = Files.readString(file.toPath());
                jwtValidateKeyArea.setText(content);
            } catch (Exception e) {
                showError("Load Error", "Could not read key file: " + e.getMessage());
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
                showError("Import Error", "Unknown key format. Choose a PEM or JSON JWK file.");
                return;
            }
            showSection("JWK (Keys)");
            jwkInputArea.setText(content);
            updateStatus(isJwk ? "Imported JSON JWK." : "Imported PEM key.");
        } catch (Exception exception) {
            showError("Import Error", "Could not read key file: " + exception.getMessage());
        }
    }

    private void showError(String title, String content) {
        if (statusReporter != null) {
            statusReporter.showError(title, content);
        }
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

    public boolean isInspectorVisible() {
        return inspectorSection != null && inspectorSection.isVisible();
    }




    private static final Logger LOG = LoggerFactory.getLogger(JOSEController.class);

    private StatusReporter statusReporter;

    /** Required by FXMLLoader when this controller is used from an fx:include. */
    public JOSEController() {
    }

    public void generateDetachedJWS(String payload, String algorithm, String key, String serializationType, boolean unencodedPayload, TextArea output) {
        try {
            java.util.List<SignerConfig> signers = java.util.Collections.singletonList(new SignerConfig(algorithm, key));
            String serialized = JOSEService.generateDetachedJWS(payload, signers, serializationType, unencodedPayload);

            output.setText(serialized);
            if (unencodedPayload) {
                statusReporter.showInfo("JWS Unencoded Payload (b64=false)", "WARNING: b64=false is enabled. The payload is detached if using standard JSON parsing.");
            }
            statusReporter.publish(OperationResult.forOperation("Detached JWS Generation")
                    .input(payload.getBytes(StandardCharsets.UTF_8)).output(serialized.getBytes(StandardCharsets.US_ASCII))
                    .detail("Algorithm", algorithm)
                    .detail("Serialization", serializationType != null ? serializationType : "Compact")
                    .detail("Unencoded", Boolean.toString(unencodedPayload))
                    .status("Detached JWS generated").build());
        } catch (Exception e) { statusReporter.showError("Detached JWS", e.getMessage()); }
    }

    public void verifyDetachedJWS(String detached, String payload, String algorithm, String key, Label status) {
        try {
            boolean valid = JOSEService.verifyDetachedJWS(detached, payload, algorithm, key);
            status.setText(valid ? "VALID DETACHED SIGNATURE" : "INVALID DETACHED SIGNATURE");
            status.setStyle(valid ? "-fx-text-fill: green;" : "-fx-text-fill: red;");
            statusReporter.publish(OperationResult.forOperation("Detached JWS Verification")
                    .input(detached.getBytes(StandardCharsets.US_ASCII)).detail("Algorithm", algorithm)
                    .detail("Result", valid ? "VALID" : "INVALID")
                    .status("Detached JWS verification: " + (valid ? "valid" : "invalid")).build());
        } catch (Exception e) { status.setText("ERROR: " + e.getMessage()); status.setStyle("-fx-text-fill: red;"); }
    }



    public JOSEController(StatusReporter statusReporter) {
        this.statusReporter = statusReporter;
    }

    /** Connects the child module to the application-wide result publisher. */
    public void setReporter(StatusReporter statusReporter) {
        this.statusReporter = statusReporter;
    }

    // --- JWT (Signed) ---
    public void generateSignedJWT(String payloadJson, java.util.List<SignerConfig> signers, String serializationType, boolean unencodedPayload, TextArea outputArea) {
        try {
            String serialized = JOSEService.generateSignedJWT(payloadJson, signers, serializationType, unencodedPayload);

            outputArea.setText(serialized);
            if (unencodedPayload) {
                statusReporter.showInfo("JWS Unencoded Payload (b64=false)", "WARNING: b64=false is enabled. This requires standard JWS JSON serialization (RFC 7797). The payload is detached if using standard JSON parsing. Many implementations might not support unencoded payloads.");
            }

            String primaryAlgo = signers.get(0).getAlgorithm();
            statusReporter.publish(OperationResult.forOperation("Signed JWT Generation")
                    .input(payloadJson.getBytes(StandardCharsets.UTF_8))
                    .output(serialized.getBytes(StandardCharsets.US_ASCII))
                    .detail("Algorithms", signers.stream().map(SignerConfig::getAlgorithm).reduce((a,b) -> a + ", " + b).orElse(""))
                    .detail("Serialization", serializationType != null ? serializationType : "Compact")
                    .detail("Unencoded", Boolean.toString(unencodedPayload))
                    .status("Signed JWT generated successfully (" + primaryAlgo + ")").build());

        } catch (Exception e) {
            statusReporter.showError("JWT Generation Error", e.getMessage());
            LOG.error("Signed JWT generation failed", e);
        }
    }

    public void validateJWT(String tokenString, String keyString, TextArea headerOut, TextArea payloadOut,
            Label statusLabel) {
        try {
            // 1. Parse JWT
            SignedJWT signedJWT = SignedJWT.parse(tokenString);

            // 2. Display Parts
            headerOut.setText(signedJWT.getHeader().toString());
            payloadOut.setText(signedJWT.getJWTClaimsSet().toString());

            // 3. Verify
            JWSVerifier verifier;
            JWSAlgorithm algo = signedJWT.getHeader().getAlgorithm();

            if (JWSAlgorithm.Family.HMAC_SHA.contains(algo)) {
                verifier = new PromiscuousMACVerifier(keyString, algo);
            } else if (JWSAlgorithm.Family.RSA.contains(algo)) {
                PublicKey pubKey = parseRSAPublicKey(keyString);
                verifier = new RSASSAVerifier((RSAPublicKey) pubKey);
            } else if (JWSAlgorithm.Family.EC.contains(algo)) {
                verifier = new ECDSAVerifier(requireEcPublicKey(algo, keyString));
            } else {
                statusLabel.setText("Unsupported Algo for Verification");
                statusLabel.setStyle("-fx-text-fill: orange;");
                return;
            }

            boolean verified = signedJWT.verify(verifier);
            if (verified) {
                statusLabel.setText("VALID SIGNATURE");
                statusLabel.setStyle("-fx-text-fill: green;");
            } else {
                statusLabel.setText("INVALID SIGNATURE");
                statusLabel.setStyle("-fx-text-fill: red;");
            }
            statusReporter.publish(OperationResult.forOperation("JWT Validation")
                    .input(tokenString.getBytes(StandardCharsets.US_ASCII))
                    .detail("Algorithm", algo.getName()).detail("Result", verified ? "VALID" : "INVALID")
                    .detail(com.cryptocarver.model.OperationDetail.secretDetail("Key Material", keyString))
                    .status("JWT validation: " + (verified ? "valid" : "invalid")).build());

        } catch (Exception e) {
            statusLabel.setText("ERROR: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: red;");
            headerOut.setText("");
            payloadOut.setText("");
        }
    }

    // --- Nested JWT (Sign then Encrypt) ---
    public void generateNestedJWT(String payloadJson, String signAlgoStr, String signKey,
            String keyAlgoStr, String contentAlgoStr, String encKeyPEM, boolean compress,
            TextArea outputArea) {
        try {
            if (compress) {
                statusReporter.showInfo("Compression", "Compression handling in pure service is omitted in this refactor unless implemented.");
            }
            String serialized = JOSEService.generateNestedJWT(payloadJson, signAlgoStr, signKey, keyAlgoStr, contentAlgoStr, encKeyPEM);

            outputArea.setText(serialized);
            String status = "Nested JWT Generated (Signed: " + signAlgoStr + ", Encrypted: " + keyAlgoStr + ")";
            if (compress)
                status += " [Compressed]";
            statusReporter.publish(OperationResult.forOperation("Nested JWT Generation")
                    .input(payloadJson.getBytes(StandardCharsets.UTF_8))
                    .output(serialized.getBytes(StandardCharsets.US_ASCII))
                    .detail("Signature Algorithm", signAlgoStr).detail("Key Algorithm", keyAlgoStr)
                    .detail("Compression", String.valueOf(compress)).detail(com.cryptocarver.model.OperationDetail.secretDetail("Key Material", signKey + " / " + encKeyPEM))
                    .status(status).build());

        } catch (Exception e) {
            statusReporter.showError("Nested JWT Error", e.getMessage());
            LOG.error("Nested JWT generation failed", e);
        }
    }

    public void verifyNestedJWT(String nestedToken, String decryptionKeyPEM, String verificationKeyPEM, TextArea payloadOut, Label statusLabel) {
        try {
            String payload = JOSEService.verifyNestedJWT(nestedToken, decryptionKeyPEM, verificationKeyPEM);
            payloadOut.setText(payload);
            statusLabel.setText("SUCCESS: Decrypted & Verified");
            statusLabel.setStyle("-fx-text-fill: green;");

            statusReporter.publish(OperationResult.forOperation("Nested JWT Verification")
                .input(nestedToken.getBytes(StandardCharsets.US_ASCII))
                .output(payloadOut.getText().getBytes(StandardCharsets.UTF_8))
                .status("Nested JWT decrypted and signature verified successfully").build());

        } catch (Exception e) {
            statusLabel.setText("ERROR: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: red;");
            statusReporter.showError("Nested JWT Verification Error", e.getMessage());
            LOG.error("Nested JWT verification failed", e);
        }
    }

    // --- JWE (Encrypted) ---
    public void generateJWE(String payload, String keyAlgo, String contentAlgo, String publicKeyPEM, boolean compress,
            TextArea outputArea) {
        try {
            // 1. Algorithms
            JWEAlgorithm alg = JWEAlgorithm.parse(keyAlgo);
            EncryptionMethod enc = EncryptionMethod.parse(contentAlgo);

            // 2. Key & Encrypter
            JWEEncrypter encrypter;
            if (JWEAlgorithm.Family.RSA.contains(alg)) {
                PublicKey publicKey = parseRSAPublicKey(publicKeyPEM);
                encrypter = new RSAEncrypter((RSAPublicKey) publicKey);
            } else if (JWEAlgorithm.Family.AES_KW.contains(alg)) {
                // AES Key Wrap expects an AES key (e.g. 128, 192, 256 bits)
                // Assuming publicKeyPEM here contains a raw secret (base64 or string) for AES
                byte[] keyBytes = publicKeyPEM.getBytes(StandardCharsets.UTF_8);
                if (publicKeyPEM.startsWith("-----BEGIN")) {
                    throw new IllegalArgumentException("AES Key Wrap requires a symmetric key (secret), not a PEM certificate/key.");
                }
                // Pad or truncate to required length for the algorithm if needed, or assume user provides correct length
                encrypter = new com.nimbusds.jose.crypto.AESEncrypter(keyBytes);
            } else if (JWEAlgorithm.Family.ECDH_ES.contains(alg)) {
                PublicKey ecPublicKey = requireEcPublicKey(new JWSAlgorithm(alg.getName()), publicKeyPEM);
                encrypter = new com.nimbusds.jose.crypto.ECDHEncrypter((java.security.interfaces.ECPublicKey) ecPublicKey);
            } else if (JWEAlgorithm.Family.PBES2.contains(alg)) {
                if (publicKeyPEM.startsWith("-----BEGIN")) {
                    throw new IllegalArgumentException("PBES2 requires a password/secret, not a PEM certificate/key.");
                }
                encrypter = new com.nimbusds.jose.crypto.PasswordBasedEncrypter(publicKeyPEM.getBytes(StandardCharsets.UTF_8), 2048, 16);
            } else {
                throw new IllegalArgumentException("Unsupported JWE Algorithm: " + alg.getName());
            }

            // 3. Header
            JWEHeader.Builder headerBuilder = new JWEHeader.Builder(alg, enc);
            if (compress) {
                headerBuilder.compressionAlgorithm(CompressionAlgorithm.DEF);
            }
            JWEHeader header = headerBuilder.build();

            // 4. Objec
            JWEObject jweObject = new JWEObject(header, new Payload(payload));

            // 5. Encryp
            jweObject.encrypt(encrypter);

            // 6. Outpu
            String serialized = jweObject.serialize();
            outputArea.setText(serialized);
            String status = "JWE Encrypted (" + keyAlgo + " / " + contentAlgo + ")";
            if (compress)
                status += " [Compressed]";
            statusReporter.publish(OperationResult.forOperation("JWE Encryption")
                    .input(payload.getBytes(StandardCharsets.UTF_8))
                    .output(serialized.getBytes(StandardCharsets.US_ASCII))
                    .detail("Key Algorithm", keyAlgo).detail("Content Algorithm", contentAlgo)
                    .detail("Compression", String.valueOf(compress)).detail(com.cryptocarver.model.OperationDetail.secretDetail("Key Material", publicKeyPEM))
                    .status(status).build());

        } catch (Exception e) {
            statusReporter.showError("JWE Encryption Error", e.getMessage());
            LOG.error("JWE encryption failed", e);
        }
    }

    public void decryptJWE(String jweString, String privateKeyPEM,
            TextArea headerOut, TextArea payloadOut,
            TextArea jweHeaderArea, TextArea jweEncryptedKeyArea, TextArea jweDecryptedKeyArea,
            TextArea jweIVArea, TextArea jweCiphertextArea, TextArea jweAuthTagArea,
            Label statusLabel) {
        try {
            // 1. Parse
            JWEObject jweObject = JWEObject.parse(jweString);

            // 2. Key & Decrypter
            JWEDecrypter decrypter;
            JWEAlgorithm alg = jweObject.getHeader().getAlgorithm();
            PrivateKey rsaPrivateKey = null; // Save for CEK display later if RSA

            if (JWEAlgorithm.Family.RSA.contains(alg)) {
                rsaPrivateKey = parseRSAPrivateKey(privateKeyPEM);
                decrypter = new RSADecrypter(rsaPrivateKey);
            } else if (JWEAlgorithm.Family.AES_KW.contains(alg)) {
                byte[] keyBytes = privateKeyPEM.getBytes(StandardCharsets.UTF_8);
                if (privateKeyPEM.startsWith("-----BEGIN")) throw new IllegalArgumentException("AES Key Wrap requires a symmetric key (secret), not a PEM certificate/key.");
                decrypter = new com.nimbusds.jose.crypto.AESDecrypter(keyBytes);
            } else if (JWEAlgorithm.Family.ECDH_ES.contains(alg)) {
                PrivateKey ecPrivateKey = requireEcPrivateKey(new JWSAlgorithm(alg.getName()), privateKeyPEM);
                decrypter = new com.nimbusds.jose.crypto.ECDHDecrypter((java.security.interfaces.ECPrivateKey) ecPrivateKey);
            } else if (JWEAlgorithm.Family.PBES2.contains(alg)) {
                if (privateKeyPEM.startsWith("-----BEGIN")) throw new IllegalArgumentException("PBES2 requires a password/secret, not a PEM certificate/key.");
                decrypter = new com.nimbusds.jose.crypto.PasswordBasedDecrypter(privateKeyPEM.getBytes(StandardCharsets.UTF_8));
            } else {
                throw new IllegalArgumentException("Unsupported JWE Algorithm: " + alg.getName());
            }

            // 3. Decryp
            jweObject.decrypt(decrypter);

            // 4. Display Parts
            headerOut.setText(jweObject.getHeader().toString());
            payloadOut.setText(jweObject.getPayload().toString());

            // 5. Visual Breakdown
            jweHeaderArea.setText(jweObject.getHeader().toString());

            Base64URL encryptedKey = jweObject.getEncryptedKey();
            jweEncryptedKeyArea.setText(encryptedKey != null ? encryptedKey.toString() : "");

            if (encryptedKey != null) {
                try {
                    // Manual Decryption to show the CEK

                    javax.crypto.Cipher cipher = null;
                    if (rsaPrivateKey != null) {
                        if (JWEAlgorithm.RSA_OAEP_256.equals(alg)) {
                            cipher = javax.crypto.Cipher.getInstance("RSA/ECB/OAEPPadding");
                            OAEPParameterSpec spec = new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256,
                                    PSource.PSpecified.DEFAULT);
                            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, rsaPrivateKey, spec);
                        } else if (JWEAlgorithm.RSA_OAEP.equals(alg)) {
                            cipher = javax.crypto.Cipher.getInstance("RSA/ECB/OAEPPadding");
                            OAEPParameterSpec spec = new OAEPParameterSpec("SHA-1", "MGF1", MGF1ParameterSpec.SHA1,
                                    PSource.PSpecified.DEFAULT);
                            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, rsaPrivateKey, spec);
                        } else if (JWEAlgorithm.RSA1_5.equals(alg)) {
                            cipher = javax.crypto.Cipher.getInstance("RSA/ECB/PKCS1Padding");
                            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, rsaPrivateKey);
                        } else {
                            // Fallback attemp
                            cipher = javax.crypto.Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
                            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, rsaPrivateKey);
                        }

                        byte[] decryptedKeyBytes = cipher.doFinal(encryptedKey.decode());

                        StringBuilder hexString = new StringBuilder();
                        for (byte b : decryptedKeyBytes) {
                            String hex = Integer.toHexString(0xff & b);
                            if (hex.length() == 1)
                                hexString.append('0');
                            hexString.append(hex);
                        }
                        jweDecryptedKeyArea.setText(hexString.toString().toUpperCase());
                    } else {
                        jweDecryptedKeyArea.setText("Manual CEK preview not supported for " + alg.getName());
                    }
                } catch (Exception ex) {
                    jweDecryptedKeyArea.setText("Decryption Error: " + ex.getMessage());
                }
            } else {
                jweDecryptedKeyArea.setText("Direct Encryption (No Key)");
            }

            jweIVArea.setText(jweObject.getIV() != null
                    ? jweObject.getIV().toString() + " \n[Hex: "
                            + com.cryptocarver.util.DataConverter.bytesToHex(jweObject.getIV().decode()) + "]"
                    : "");
            jweCiphertextArea.setText(jweObject.getCipherText() != null ? jweObject.getCipherText().toString() : "");
            jweAuthTagArea
                    .setText(
                            jweObject.getAuthTag() != null
                                    ? jweObject.getAuthTag().toString() + " \n[Hex: "
                                            + com.cryptocarver.util.DataConverter
                                                    .bytesToHex(jweObject.getAuthTag().decode())
                                            + "]"
                                    : "");

            statusLabel.setText("DECRYPTION SUCCESSFUL");
            statusLabel.setStyle("-fx-text-fill: green;");

            String payload = jweObject.getPayload().toString();
            statusReporter.publish(OperationResult.forOperation("JWE Decryption")
                    .input(jweString.getBytes(StandardCharsets.US_ASCII))
                    .output(payload.getBytes(StandardCharsets.UTF_8))
                    .detail("Key Algorithm", jweObject.getHeader().getAlgorithm().getName())
                    .detail("Content Algorithm", jweObject.getHeader().getEncryptionMethod().getName())
                    .detail(com.cryptocarver.model.OperationDetail.secretDetail("Key Material", privateKeyPEM))
                    .status("JWE decrypted").build());

        } catch (Exception e) {
            statusLabel.setText("DECRYPTION FAILED");
            statusLabel.setStyle("-fx-text-fill: red;");
            statusReporter.showError("JWE Decryption Error", e.getMessage());
            LOG.error("JWE decryption failed", e);
        }
    }

    // --- JWK ---
    public void generateRSAJWK(TextArea outputArea) {
        try {
            RSAKey rsaJWK = new RSAKeyGenerator(2048)
                    .keyID(UUID.randomUUID().toString())
                    .generate();

            outputArea.setText(rsaJWK.toJSONString());
            statusReporter.updateStatus("RSA JWK generated");
        } catch (Exception e) {
            statusReporter.showError("JWK Error", e.getMessage());
        }
    }

    // --- Helpers ---
    private PrivateKey parseRSAPrivateKey(String pem) throws Exception {
        String privateKeyPEM = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] encoded = DataConverter.decodeBase64Flexible(privateKeyPEM);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(encoded));
    }

    /** Loads an EC private key encoded as PEM PKCS#8 for ES256/ES384/ES512. */
    private java.security.interfaces.ECPrivateKey parseECPrivateKey(String pem) throws Exception {
        if (pem.contains("-----BEGIN EC PRIVATE KEY-----")) {
            throw new IllegalArgumentException("EC private keys must be PEM PKCS#8 (BEGIN PRIVATE KEY), not SEC1 (BEGIN EC PRIVATE KEY)");
        }
        String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] encoded = DataConverter.decodeBase64Flexible(base64);
        PrivateKey key = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(encoded));
        if (!(key instanceof java.security.interfaces.ECPrivateKey ecKey)) {
            throw new IllegalArgumentException("The supplied PKCS#8 key is not an EC private key");
        }
        return ecKey;
    }

    private PublicKey parseRSAPublicKey(String pem) throws Exception {
        String publicKeyPEM = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("-----BEGIN RSA PUBLIC KEY-----", "")
                .replace("-----END RSA PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        byte[] encoded = DataConverter.decodeBase64Flexible(publicKeyPEM);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(new X509EncodedKeySpec(encoded));
    }

    /** Loads an EC public key encoded as PEM SubjectPublicKeyInfo for ES verification. */
    private java.security.interfaces.ECPublicKey parseECPublicKey(String pem) throws Exception {
        String base64 = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "").replaceAll("\\s", "");
        PublicKey key = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(DataConverter.decodeBase64Flexible(base64)));
        if (!(key instanceof java.security.interfaces.ECPublicKey ecKey)) throw new IllegalArgumentException("The supplied key is not an EC public key");
        return ecKey;
    }

    private java.security.interfaces.ECPrivateKey requireEcPrivateKey(JWSAlgorithm algorithm, String pem) throws Exception {
        java.security.interfaces.ECPrivateKey key = parseECPrivateKey(pem);
        validateEcCurve(algorithm, key.getParams().getCurve().getField().getFieldSize());
        return key;
    }

    private java.security.interfaces.ECPublicKey requireEcPublicKey(JWSAlgorithm algorithm, String pem) throws Exception {
        java.security.interfaces.ECPublicKey key = parseECPublicKey(pem);
        validateEcCurve(algorithm, key.getParams().getCurve().getField().getFieldSize());
        return key;
    }

    private void validateEcCurve(JWSAlgorithm algorithm, int fieldSize) {
        int expected = JWSAlgorithm.ES256.equals(algorithm) ? 256 : JWSAlgorithm.ES384.equals(algorithm) ? 384 : JWSAlgorithm.ES512.equals(algorithm) ? 521 : -1;
        if (expected < 0) throw new IllegalArgumentException("Unsupported EC JWS algorithm: " + algorithm);
        if (fieldSize != expected) throw new IllegalArgumentException(algorithm + " requires a P-" + expected + " EC key; supplied key has a " + fieldSize + "-bit field");
    }

    // --- Internal Permissive Implementations ---

    private static class PromiscuousMACSigner implements JWSSigner {
        private final byte[] secret;
        private final JWSAlgorithm algorithm;
        private final JCAContext jcaContext = new JCAContext();

        public PromiscuousMACSigner(String secretStr, JWSAlgorithm algorithm) {
            this.secret = secretStr.getBytes(StandardCharsets.UTF_8);
            this.algorithm = algorithm;
        }

        @Override
        public Base64URL sign(final JWSHeader header, final byte[] signingInput) throws JOSEException {
            try {
                String jcaAlgo = getJCAAlgorithmName(header.getAlgorithm());
                Mac mac = Mac.getInstance(jcaAlgo);
                mac.init(new SecretKeySpec(secret, jcaAlgo));
                return Base64URL.encode(mac.doFinal(signingInput));
            } catch (Exception e) {
                throw new JOSEException(e.getMessage(), e);
            }
        }

        @Override
        public Set<JWSAlgorithm> supportedJWSAlgorithms() {
            return Collections.singleton(algorithm);
        }

        @Override
        public JCAContext getJCAContext() {
            return jcaContext;
        }
    }

    private static class PromiscuousMACVerifier implements JWSVerifier {
        private final byte[] secret;
        private final JWSAlgorithm algorithm;
        private final JCAContext jcaContext = new JCAContext();

        public PromiscuousMACVerifier(String secretStr, JWSAlgorithm algorithm) {
            this.secret = secretStr.getBytes(StandardCharsets.UTF_8);
            this.algorithm = algorithm;
        }

        @Override
        public boolean verify(JWSHeader header, byte[] signedContent, Base64URL signature) throws JOSEException {
            if (!header.getAlgorithm().equals(algorithm)) {
                return false;
            }
            try {
                String jcaAlgo = getJCAAlgorithmName(header.getAlgorithm());
                Mac mac = Mac.getInstance(jcaAlgo);
                mac.init(new SecretKeySpec(secret, jcaAlgo));
                byte[] expectedSignature = mac.doFinal(signedContent);
                byte[] providedSignature = signature.decode();
                if (expectedSignature.length != providedSignature.length) {
                    return false;
                }
                int result = 0;
                for (int i = 0; i < expectedSignature.length; i++) {
                    result |= expectedSignature[i] ^ providedSignature[i];
                }
                return result == 0;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public Set<JWSAlgorithm> supportedJWSAlgorithms() {
            return Collections.singleton(algorithm);
        }

        @Override
        public JCAContext getJCAContext() {
            return jcaContext;
        }
    }

    private static String getJCAAlgorithmName(JWSAlgorithm alg) throws JOSEException {
        if (alg.equals(JWSAlgorithm.HS256))
            return "HmacSHA256";
        if (alg.equals(JWSAlgorithm.HS384))
            return "HmacSHA384";
        if (alg.equals(JWSAlgorithm.HS512))
            return "HmacSHA512";
        throw new JOSEException("Unsupported HMAC algorithm: " + alg.getName());
    }

    // --- Enterprise Features (level 4 & 5) ---

    // 1. JWK Managemen
    public JWK generateNewJWK(String alg, String use) throws Exception {
        if (alg.startsWith("RS") || alg.startsWith("PS")) {
            return new RSAKeyGenerator(2048)
                    .keyUse(use.equals("sig") ? KeyUse.SIGNATURE : KeyUse.ENCRYPTION)
                    .algorithm(new JWSAlgorithm(alg))
                    .keyID(UUID.randomUUID().toString())
                    .generate();
        } else if (alg.startsWith("ES")) {
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
            TextArea headerOut, TextArea payloadOut, Label statusLabel) {
        try {
            // 1. Parse
            SignedJWT signedJWT = SignedJWT.parse(tokenString);
            headerOut.setText(signedJWT.getHeader().toString());
            payloadOut.setText(signedJWT.getJWTClaimsSet().toString());

            // 2. Verify Signature
            JWSVerifier verifier = null;
            JWSAlgorithm algo = signedJWT.getHeader().getAlgorithm();
            boolean sigValid = false;

            String keyStringTrimmed = keyString.trim();
            if (keyStringTrimmed.startsWith("{") && keyStringTrimmed.contains("\"keys\"")) {
                // It's a JWKS
                JWKSet jwkSet = JWKSet.parse(keyStringTrimmed);
                String kid = signedJWT.getHeader().getKeyID();
                JWK match = null;

                if (kid != null) {
                    match = jwkSet.getKeyByKeyId(kid);
                    if (match == null) {
                        throw new Exception("JWKS does not contain a key matching the token's 'kid': " + kid);
                    }
                } else {
                    // If no kid, try to find a key that matches the algorithm, or just take the firs
                    for (JWK k : jwkSet.getKeys()) {
                        if (k.getAlgorithm() != null && k.getAlgorithm().equals(algo)) {
                            match = k; break;
                        }
                    }
                    if (match == null && !jwkSet.getKeys().isEmpty()) {
                        match = jwkSet.getKeys().get(0);
                    }
                    if (match == null) throw new Exception("JWKS is empty");
                }

                if (match instanceof com.nimbusds.jose.jwk.RSAKey) {
                    verifier = new RSASSAVerifier(((com.nimbusds.jose.jwk.RSAKey) match).toRSAPublicKey());
                } else if (match instanceof com.nimbusds.jose.jwk.ECKey) {
                    verifier = new ECDSAVerifier(((com.nimbusds.jose.jwk.ECKey) match).toECPublicKey());
                } else if (match instanceof com.nimbusds.jose.jwk.OctetSequenceKey) {
                    verifier = new MACVerifier(((com.nimbusds.jose.jwk.OctetSequenceKey) match).toByteArray());
                } else {
                    throw new Exception("Unsupported JWK type: " + match.getKeyType());
                }
            } else {
                // Direct PEM or Secre
                if (JWSAlgorithm.Family.HMAC_SHA.contains(algo)) {
                    verifier = new PromiscuousMACVerifier(keyString, algo);
                } else if (JWSAlgorithm.Family.RSA.contains(algo)) {
                    PublicKey pubKey = parseRSAPublicKey(keyString);
                    verifier = new RSASSAVerifier((RSAPublicKey) pubKey);
                } else if (JWSAlgorithm.Family.EC.contains(algo)) {
                    PublicKey pubKey = requireEcPublicKey(algo, keyString);
                    verifier = new ECDSAVerifier((java.security.interfaces.ECPublicKey) pubKey);
                }
            }

            if (verifier == null) {
                statusLabel.setText("Algorithm Verification not supported manually.");
                statusLabel.setStyle("-fx-text-fill: orange;");
                return;
            }

            sigValid = signedJWT.verify(verifier);

            if (!sigValid) {
                statusLabel.setText("INVALID Signature ❌");
                statusLabel.setStyle("-fx-text-fill: red;");
                return;
            }

            // 3. Validate Claims (Enterprise)
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            List<String> errors = new ArrayList<>();

            // Issuer
            if (expectedIss != null && !expectedIss.isEmpty()) {
                if (!expectedIss.equals(claims.getIssuer())) {
                    errors.add("Issuer mismatch (expected " + expectedIss + ")");
                }
            }

            // Audience
            if (expectedAud != null && !expectedAud.isEmpty()) {
                List<String> auds = claims.getAudience();
                if (auds == null || !auds.contains(expectedAud)) {
                    errors.add("Audience mismatch (expected " + expectedAud + ")");
                }
            }

            Date now = new Date();
            long skewMillis = clockSkewSec * 1000L;
            Date exp = claims.getExpirationTime();
            Date nbf = claims.getNotBeforeTime();
            Date iat = claims.getIssueTime();

            // Strict OIDC Check
            if (oidcStrict) {
                if (exp == null) errors.add("OIDC Strict: Missing 'exp' claim");
                if (iat == null) errors.add("OIDC Strict: Missing 'iat' claim");
                if (claims.getIssuer() == null) errors.add("OIDC Strict: Missing 'iss' claim");
                if (claims.getAudience() == null || claims.getAudience().isEmpty()) errors.add("OIDC Strict: Missing 'aud' claim");
            }

            // Expiration, Not Before, Issued At (w/ Clock Skew)
            if (checkExpiry || oidcStrict) {
                if (exp != null) {
                    if (now.getTime() > (exp.getTime() + skewMillis)) {
                        errors.add("Token Expired");
                    }
                }

                if (nbf != null) {
                    if (now.getTime() < (nbf.getTime() - skewMillis)) {
                        errors.add("Token Not Yet Valid (nbf)");
                    }
                }

                if (iat != null) {
                    if (now.getTime() < (iat.getTime() - skewMillis)) {
                        errors.add("Token issued in the future (iat > now)");
                    }
                }
            }

            if (errors.isEmpty()) {
                statusLabel.setText("VALID ✅ (Sig + Claims)");
                statusLabel.setStyle("-fx-text-fill: green;");
            } else {
                statusLabel.setText("INVALID Claims ⚠️");
                statusLabel.setStyle("-fx-text-fill: orange;");
                // Append errors to payload output for visibility
                payloadOut.appendText("\n\n--- VALIDATION ERRORS ---\n" + String.join("\n", errors));
            }

        } catch (Exception e) {
            statusLabel.setText("Error: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: red;");
            LOG.error("JWK generation failed", e);
        }
    }

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
        String[] parts = token.trim().split("\\.");

        try {
            if (parts.length == 3) {
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
        if (pem == null || pem.trim().isEmpty()) {
            outputArea.setText("Error: Input PEM is empty.");
            return;
        }
        try {
            // Flexible PEM parsing using DataConverter logic implicitly via Nimbus or
            // manual strip
            String cleanPem = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                    .replace("-----END RSA PRIVATE KEY-----", "")
                    .replace("-----BEGIN EC PRIVATE KEY-----", "")
                    .replace("-----END EC PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");

            byte[] keyBytes = com.cryptocarver.util.DataConverter.decodeBase64Flexible(cleanPem);

            com.nimbusds.jose.jwk.JWK jwk = null;

            if ("RSA".equalsIgnoreCase(keyType)) {
                // Try parsing as Private -> Public -> Just creation
                try {
                    java.security.spec.PKCS8EncodedKeySpec spec = new java.security.spec.PKCS8EncodedKeySpec(keyBytes);
                    java.security.KeyFactory kf = java.security.KeyFactory.getInstance("RSA");
                    java.security.interfaces.RSAPrivateKey privKey = (java.security.interfaces.RSAPrivateKey) kf
                            .generatePrivate(spec);

                    // Need public key to make full JWK.
                    // Logic to extract mod/exp from private key implies using RSAPrivateCrtKey
                    if (privKey instanceof java.security.interfaces.RSAPrivateCrtKey) {
                        java.security.interfaces.RSAPrivateCrtKey crt = (java.security.interfaces.RSAPrivateCrtKey) privKey;
                        java.security.spec.RSAPublicKeySpec pubSpec = new java.security.spec.RSAPublicKeySpec(
                                crt.getModulus(), crt.getPublicExponent());
                        java.security.interfaces.RSAPublicKey pubKey = (java.security.interfaces.RSAPublicKey) kf
                                .generatePublic(pubSpec);

                        jwk = new com.nimbusds.jose.jwk.RSAKey.Builder(pubKey)
                                .privateKey(privKey)
                                .keyID(keyId != null && !keyId.isEmpty() ? keyId : null)
                                .build();
                    } else {
                        outputArea.setText("Error: Encoded RSA private key is not CRT compatible.");
                        return;
                    }
                } catch (Exception ePriv) {
                    // Try Public
                    try {
                        java.security.spec.X509EncodedKeySpec pubSpec = new java.security.spec.X509EncodedKeySpec(
                                keyBytes);
                        java.security.KeyFactory kf = java.security.KeyFactory.getInstance("RSA");
                        java.security.interfaces.RSAPublicKey pubKey = (java.security.interfaces.RSAPublicKey) kf
                                .generatePublic(pubSpec);
                        jwk = new com.nimbusds.jose.jwk.RSAKey.Builder(pubKey)
                                .keyID(keyId != null && !keyId.isEmpty() ? keyId : null)
                                .build();
                    } catch (Exception ePub) {
                        throw new Exception("Could not parse as RSA Private (PKCS8) or Public (X509) key.");
                    }
                }
            } else if ("EC".equalsIgnoreCase(keyType)) {
                // Simplified EC handling - requires definition of curve usually.
                // For now, attempting generic parsing or failing gracefully.
                outputArea.setText(
                        "EC Key parsing from raw bytes requires Curve context. \nSupport for Generic EC PEM -> JWK is limited.\nTry converting via File -> Import if possible.");
                return;
            } else if ("OCT".equalsIgnoreCase(keyType)) {
                // Symmetric Key - keyBytes is the secre
                jwk = new com.nimbusds.jose.jwk.OctetSequenceKey.Builder(keyBytes)
                        .keyID(keyId != null && !keyId.isEmpty() ? keyId : null)
                        .build();
            }

            if (jwk != null) {
                // Auto-calc KID if not provided
                if (keyId == null || keyId.trim().isEmpty()) {
                    String thumbprint = jwk.computeThumbprint().toString();
                    // Re-build with kid
                    if (jwk instanceof com.nimbusds.jose.jwk.RSAKey) {
                        jwk = new com.nimbusds.jose.jwk.RSAKey.Builder((com.nimbusds.jose.jwk.RSAKey) jwk)
                                .keyID(thumbprint).build();
                    }
                }

                outputArea.setText(jwk.toJSONString());
                outputArea.appendText("\n\n// Thumbprint (SHA-256): " + jwk.computeThumbprint().toString());
            }

        } catch (Exception e) {
            outputArea.setText("Error converting to JWK: " + e.getMessage());
            LOG.error("PEM key import failed", e);
        }
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
                java.security.interfaces.ECPublicKey pub = ecKey.toECPublicKey();
                String pubPem = java.util.Base64.getMimeEncoder(64, new byte[] { '\n' })
                        .encodeToString(pub.getEncoded());
                sb.append("-----BEGIN PUBLIC KEY-----\n").append(pubPem).append("\n-----END PUBLIC KEY-----\n\n");

                if (ecKey.isPrivate()) {
                    sb.append("=== Private Key (PEM) ===\n");
                    java.security.interfaces.ECPrivateKey priv = ecKey.toECPrivateKey();
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
                // Assume PEM -> Convert to JWK -> Calc
                // Reuse convert logic but just output thumbprint?
                // For now, ask user to convert to JWK first for clarity or implemen
                // auto-detect.
                outputArea.setText("Please convert PEM to JWK first, or ensure input is valid JSON JWK.");
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
                    if (title.equals("HEADER")) {
                        decoded = com.nimbusds.jose.JWSHeader.parse(b64).toString();
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
                new SimpleAlgo("HS256", "Signature", "HMAC using SHA-256"),
                new SimpleAlgo("RS256", "Signature", "RSASSA-PKCS1-v1_5 using SHA-256"),
                new SimpleAlgo("ES256", "Signature", "ECDSA using P-256 and SHA-256"),
                new SimpleAlgo("PS256", "Signature", "RSASSA-PSS using SHA-256 and MGF1"),
                new SimpleAlgo("EdDSA", "Signature", "EdDSA using Ed25519 or Ed448"),
                new SimpleAlgo("RSA-OAEP-256", "Encryption", "RSAES OAEP using SHA-256 and MGF1"),
                new SimpleAlgo("A256GCM", "Encryption", "AES GCM (256-bit) content encryption"),
                new SimpleAlgo("dir", "Encryption", "Direct use of shared symmetric key")));
    }

    private record SimpleAlgo(String name, String type, String description) {
    }
}
