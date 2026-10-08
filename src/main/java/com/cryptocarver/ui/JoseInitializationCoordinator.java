package com.cryptocarver.ui;

import com.cryptocarver.crypto.JoseKeyMaterial;
import com.cryptocarver.crypto.JweComposer;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Owns JOSE module control initialization and presentation wiring. */
final class JoseInitializationCoordinator extends JoseCoordinatorSupport {
    record View(VBox joseContainer, CheckBox jwtAcceptNoneCheck,
            ComboBox<String> jwtAlgoCombo, ComboBox<String> jwtAlgo2Combo,
            ComboBox<String> jweKeyAlgoCombo, ComboBox<String> jweContentAlgoCombo,
            ComboBox<String> jweKeyFormatCombo, ComboBox<String> jweDecryptKeyFormatCombo,
            ComboBox<String> jwtSecretFormatCombo, ComboBox<String> jwtValidateSecretFormatCombo,
            ComboBox<String> detachedSecretFormatCombo, ComboBox<String> nestedSecretFormatCombo,
            ComboBox<String> detachedAlgoCombo, ComboBox<String> jweSerializationCombo,
            ComboBox<String> jwsSerializationCombo, ComboBox<String> detachedSerializationCombo,
            ComboBox<String> nestedSignAlgoCombo, ComboBox<String> nestedKeyAlgoCombo,
            ComboBox<String> nestedContentAlgoCombo, ComboBox<String> jwkKeyTypeCombo,
            ComboBox<String> jwkUseCombo, ComboBox<String> jwksRotateAlgoCombo,
            ComboBox<String> jwtTemplateCombo, TextField jwePbes2IterField, TextArea jwtPayloadArea,
            TextArea jwkInputArea, Label jwkInputLabel, Label jwkCurveLabel,
            ComboBox<String> jwkCurveCombo, Button pemToJwkBtn, Button jwkToPemBtn,
            Label jwkUseLabel, Label jwkKeyOpsLabel, Button jwkInspectMetadataBtn,
            TextField jwkKeyOpsField, Label jwtAllowedAlgorithmsLabel, Label jwtExpectedTypeLabel,
            Label jwtExpectedContentTypeLabel, Label jwtExpectedNonceLabel, Label jwtAccessTokenLabel,
            Label jwtAuthorizationCodeLabel, Label jwtExpectedJktLabel, Label jwtExpectedX5tLabel,
            Label jwtUnderstoodCritLabel, Label jwtProtectedHeaderLabel, CheckBox jwtRfc9068Check,
            CheckBox jwtIgnoreCritCheck, CheckBox jwtTrustHeaderKeyCheck, TextArea jwtKeyArea,
            TextArea jwtKeyArea2, TextArea detachedSigningKeyArea, TextArea nestedSigningKeyArea,
            Label jwtSecurityWarningLabel, Label detachedSecurityWarningLabel,
            Label nestedSecurityWarningLabel, Label jweSecurityWarningLabel, Label detachedStatusLabel,
            TextArea jwtValidateTokenArea, TextArea jwtValidateKeyArea, TextArea jwePublicKeyArea,
            TextArea jweInputArea, TextArea jwePrivateKeyArea) { }

    private static final List<String> JWS_ALGORITHMS = List.of("HS256", "HS384", "HS512",
            "RS256", "RS384", "RS512", "ES256", "ES256K", "ES384", "ES512",
            "PS256", "PS384", "PS512", "EdDSA", "none");
    private final Supplier<View> controls;
    private final JoseJwkCoordinator jwkCoordinator;
    private final JoseJwtCoordinator jwtCoordinator;
    private ModuleI18n.Binding moduleI18n;
    private Consumer<java.util.Locale> localeChangeListener;

    JoseInitializationCoordinator(Supplier<View> controls, Supplier<StatusReporter> reporter,
            JoseJwkCoordinator jwkCoordinator, JoseJwtCoordinator jwtCoordinator) {
        super(reporter);
        this.controls = controls;
        this.jwkCoordinator = jwkCoordinator;
        this.jwtCoordinator = jwtCoordinator;
    }

    private View view() { return controls.get(); }

    void bindModuleI18n() { moduleI18n = ModuleI18n.bind(view().joseContainer(), ModuleTextCatalog.jose()); }

    void initializeAcceptNoneLabel() {
        if (view().jwtAcceptNoneCheck() != null) view().jwtAcceptNoneCheck().setText(t("module.jose.acceptNone"));
    }

    void registerLocaleChangeListener() {
        localeChangeListener = locale -> {
            updateJwkInputPresentation();
            refreshCapabilityLabels();
            jwkCoordinator.initializeCurveControls();
            jwtCoordinator.initializeDetachedHeaderControls();
            for (ComboBox<String> combo : unsafeAlgorithmCombos()) markUnsafeOptions(combo);
            refreshSecurityWarnings();
            Label status = view().detachedStatusLabel();
            if (status != null && status.getText() != null && status.getText().isBlank()) status.setText("");
        };
        com.cryptocarver.service.I18nService.getInstance().addLocaleChangeListener(localeChangeListener);
    }

    private List<ComboBox<String>> unsafeAlgorithmCombos() {
        View v = view();
        return Arrays.asList(v.jwtAlgoCombo(), v.jwtAlgo2Combo(), v.detachedAlgoCombo(), v.nestedSignAlgoCombo(),
                v.jweKeyAlgoCombo(), v.nestedKeyAlgoCombo(), v.jwksRotateAlgoCombo());
    }

    void initializeJwtAlgorithms() {
        if (view().jwtAlgoCombo() != null && view().jwtAlgoCombo().getItems().isEmpty()) {
            view().jwtAlgoCombo().getItems().addAll(JWS_ALGORITHMS);
            view().jwtAlgoCombo().getSelectionModel().selectFirst();
        }
        if (view().jwtAlgo2Combo() != null && view().jwtAlgo2Combo().getItems().isEmpty()) {
            view().jwtAlgo2Combo().getItems().addAll(JWS_ALGORITHMS);
            view().jwtAlgo2Combo().getSelectionModel().selectFirst();
        }
    }

    void initializeJweAlgorithms() {
        if (view().jweKeyAlgoCombo() != null && view().jweKeyAlgoCombo().getItems().isEmpty()) {
            view().jweKeyAlgoCombo().getItems().setAll(JweComposer.KEY_ALGORITHMS);
            view().jweKeyAlgoCombo().getSelectionModel().selectFirst();
        }
        if (view().jweContentAlgoCombo() != null && view().jweContentAlgoCombo().getItems().isEmpty()) {
            view().jweContentAlgoCombo().getItems().setAll(JweComposer.CONTENT_ALGORITHMS);
            view().jweContentAlgoCombo().getSelectionModel().select("A256GCM");
        }
    }

    void initializeSecretFormats() {
        for (ComboBox<String> format : Arrays.asList(view().jweKeyFormatCombo(), view().jweDecryptKeyFormatCombo(),
                view().jwtSecretFormatCombo(), view().jwtValidateSecretFormatCombo(), view().detachedSecretFormatCombo(),
                view().nestedSecretFormatCombo())) {
            if (format != null && format.getItems().isEmpty()) {
                for (JoseKeyMaterial.SecretEncoding encoding : JoseKeyMaterial.SecretEncoding.values()) format.getItems().add(encoding.label());
                format.getSelectionModel().selectFirst();
            }
        }
    }

    void initializePbes2Default() {
        if (view().jwePbes2IterField() != null && view().jwePbes2IterField().getText().isBlank()) {
            view().jwePbes2IterField().setText(String.valueOf(JweComposer.DEFAULT_PBES2_ITERATIONS));
        }
    }

    void initializeDetachedAlgorithm() {
        if (view().detachedAlgoCombo() != null && view().detachedAlgoCombo().getItems().isEmpty()) {
            view().detachedAlgoCombo().getItems().setAll(JWS_ALGORITHMS);
            view().detachedAlgoCombo().getSelectionModel().selectFirst();
        }
    }

    void initializeSerializationCombos() {
        if (view().jweSerializationCombo() != null && view().jweSerializationCombo().getItems().isEmpty()) {
            for (JweComposer.Serialization serialization : JweComposer.Serialization.values()) view().jweSerializationCombo().getItems().add(serialization.label());
            view().jweSerializationCombo().getSelectionModel().selectFirst();
        }
        for (ComboBox<String> serialization : Arrays.asList(view().jwsSerializationCombo(), view().detachedSerializationCombo())) {
            if (serialization != null && serialization.getItems().isEmpty()) {
                serialization.getItems().setAll("Compact", "Flattened JSON", "General JSON");
                serialization.getSelectionModel().selectFirst();
            }
        }
    }

    void initializeNestedAlgorithms() {
        if (view().nestedSignAlgoCombo() != null && view().nestedSignAlgoCombo().getItems().isEmpty()) {
            view().nestedSignAlgoCombo().getItems().setAll(JWS_ALGORITHMS);
            view().nestedSignAlgoCombo().getSelectionModel().select("HS256");
        }
        if (view().nestedKeyAlgoCombo() != null && view().nestedKeyAlgoCombo().getItems().isEmpty()) {
            view().nestedKeyAlgoCombo().getItems().setAll(JweComposer.KEY_ALGORITHMS);
            view().nestedKeyAlgoCombo().getSelectionModel().selectFirst();
        }
        if (view().nestedContentAlgoCombo() != null && view().nestedContentAlgoCombo().getItems().isEmpty()) {
            view().nestedContentAlgoCombo().getItems().setAll(JweComposer.CONTENT_ALGORITHMS);
            view().nestedContentAlgoCombo().getSelectionModel().select("A256GCM");
        }
    }

    void initializeJwkCombos() {
        if (view().jwkKeyTypeCombo() != null && view().jwkKeyTypeCombo().getItems().isEmpty()) {
            view().jwkKeyTypeCombo().getItems().setAll("RSA", "EC", "OKP", "OCT");
            view().jwkKeyTypeCombo().getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
                if (newV == null) return;
                updateJwkInputPresentation();
            });
            view().jwkKeyTypeCombo().getSelectionModel().selectFirst();
        }
        if (view().jwkUseCombo() != null && view().jwkUseCombo().getItems().isEmpty()) {
            view().jwkUseCombo().getItems().setAll("sig", "enc");
            view().jwkUseCombo().getSelectionModel().selectFirst();
        }
    }

    void initializeJwksRotationAlgorithms() {
        if (view().jwksRotateAlgoCombo() != null && view().jwksRotateAlgoCombo().getItems().isEmpty()) {
            view().jwksRotateAlgoCombo().getItems().setAll(
                    "RS256", "RS384", "RS512", "PS256", "PS384", "PS512", "ES256", "ES256K", "ES384", "ES512", "EdDSA",
                    "RSA1_5", "RSA-OAEP", "ECDH-ES", "ECDH-ES-X448", "ECDH-ES+A128KW", "ECDH-ES+A192KW", "ECDH-ES+A256KW",
                    "HS256", "HS384", "HS512", "A128KW", "A256KW", "A128GCM", "A256GCM", "dir");
            view().jwksRotateAlgoCombo().getSelectionModel().selectFirst();
        }
    }

    void initializeCurveControls() { jwkCoordinator.initializeCurveControls(); }
    void initializeDetachedHeaderControls() { jwtCoordinator.initializeDetachedHeaderControls(); }

    void installSecurityWarningListeners() {
        for (ComboBox<String> combo : unsafeAlgorithmCombos()) {
            markUnsafeOptions(combo);
            if (combo != null) combo.valueProperty().addListener((obs, oldValue, newValue) -> refreshSecurityWarnings());
        }
        for (TextInputControl key : Arrays.asList(view().jwtKeyArea(), view().jwtKeyArea2(),
                view().detachedSigningKeyArea(), view().nestedSigningKeyArea())) {
            if (key != null) key.textProperty().addListener((obs, oldValue, newValue) -> refreshSecurityWarnings());
        }
    }

    void initializeIngestionBindings() {
        IngestionUIHelper.bindField(view().jwtKeyArea(), null, com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_PRIVATE_KEY,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.HEX, com.cryptocarver.model.MaterialDetectionResult.MaterialType.TEXT_UNKNOWN);
        IngestionUIHelper.bindField(view().jwtValidateTokenArea(), null, com.cryptocarver.model.MaterialDetectionResult.MaterialType.JWT);
        IngestionUIHelper.bindField(view().jwtValidateKeyArea(), null, com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_PUBLIC_KEY,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_CERTIFICATE, com.cryptocarver.model.MaterialDetectionResult.MaterialType.HEX,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.TEXT_UNKNOWN);
        IngestionUIHelper.bindField(view().jwePublicKeyArea(), null, com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_PUBLIC_KEY,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_CERTIFICATE, com.cryptocarver.model.MaterialDetectionResult.MaterialType.HEX,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.TEXT_UNKNOWN);
        IngestionUIHelper.bindField(view().jweInputArea(), null, com.cryptocarver.model.MaterialDetectionResult.MaterialType.JWT);
        IngestionUIHelper.bindField(view().jwePrivateKeyArea(), null, com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_PRIVATE_KEY,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.HEX, com.cryptocarver.model.MaterialDetectionResult.MaterialType.TEXT_UNKNOWN);
    }

    void initializeTemplates() {
        if (view().jwtTemplateCombo() != null && view().jwtTemplateCombo().getItems().isEmpty()) {
            view().jwtTemplateCombo().getItems().addAll("OAuth2 Access Token (JWT)", "OIDC ID Token", "DPoP Proof", "Custom (Empty)");
            view().jwtTemplateCombo().setOnAction(e -> {
                String sel = view().jwtTemplateCombo().getValue();
                if (sel == null) return;
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
                            + "\",\n  \"htm\": \"POST\",\n  \"htu\": \"https://resource.server.org/protected\",\n  \"iat\": " + now + "\n}";
                }
                if (view().jwtPayloadArea() != null) view().jwtPayloadArea().setText(tmpl);
            });
        }
    }

    void updateJwkInputPresentation() {
        View v = view();
        boolean secret = v.jwkKeyTypeCombo() != null && "OCT".equals(v.jwkKeyTypeCombo().getValue());
        if (v.jwkInputLabel() != null) v.jwkInputLabel().setText(t(secret ? "module.jose.inputSecret" : "module.jose.inputPem"));
        if (v.jwkInputArea() != null) v.jwkInputArea().setPromptText(t(secret ? "module.jose.inputSecretPrompt" : "module.jose.inputPemPrompt"));
        if (v.pemToJwkBtn() != null) v.pemToJwkBtn().setText(t(secret ? "module.jose.secretToJwk" : "module.jose.pemToJwk"));
        if (v.jwkToPemBtn() != null) v.jwkToPemBtn().setText(t(secret ? "module.jose.jwkToSecret" : "module.jose.jwkToPem"));
    }

    void refreshCapabilityLabels() {
        View v = view();
        jwkCoordinator.refreshCurveLabel();
        jwtCoordinator.refreshCertificateLabels();
        if (v.jwtAcceptNoneCheck() != null) v.jwtAcceptNoneCheck().setText(t("module.jose.acceptNone"));
        if (v.jwtTrustHeaderKeyCheck() != null) v.jwtTrustHeaderKeyCheck().setText(t("module.jose.trustHeaderKey"));
        if (v.jwtProtectedHeaderLabel() != null) v.jwtProtectedHeaderLabel().setText(t("module.jose.protectedHeaderAdditional"));
        if (v.jwtAllowedAlgorithmsLabel() != null) v.jwtAllowedAlgorithmsLabel().setText(t("module.jose.jwtAllowedAlgorithms"));
        if (v.jwtExpectedTypeLabel() != null) v.jwtExpectedTypeLabel().setText(t("module.jose.jwtExpectedType"));
        if (v.jwtExpectedContentTypeLabel() != null) v.jwtExpectedContentTypeLabel().setText(t("module.jose.jwtExpectedContentType"));
        if (v.jwtExpectedNonceLabel() != null) v.jwtExpectedNonceLabel().setText(t("module.jose.jwtExpectedNonce"));
        if (v.jwtAccessTokenLabel() != null) v.jwtAccessTokenLabel().setText(t("module.jose.jwtAccessToken"));
        if (v.jwtAuthorizationCodeLabel() != null) v.jwtAuthorizationCodeLabel().setText(t("module.jose.jwtAuthorizationCode"));
        if (v.jwtExpectedJktLabel() != null) v.jwtExpectedJktLabel().setText(t("module.jose.jwtExpectedJkt"));
        if (v.jwtExpectedX5tLabel() != null) v.jwtExpectedX5tLabel().setText(t("module.jose.jwtExpectedX5t"));
        if (v.jwtUnderstoodCritLabel() != null) v.jwtUnderstoodCritLabel().setText(t("module.jose.jwtUnderstoodCrit"));
        if (v.jwtRfc9068Check() != null) v.jwtRfc9068Check().setText(t("module.jose.jwtRfc9068"));
        if (v.jwtIgnoreCritCheck() != null) v.jwtIgnoreCritCheck().setText(t("module.jose.jwtIgnoreCrit"));
        if (v.jwkUseLabel() != null) v.jwkUseLabel().setText(t("module.jose.jwkUse"));
        if (v.jwkKeyOpsLabel() != null) v.jwkKeyOpsLabel().setText(t("module.jose.jwkKeyOps"));
        if (v.jwkInspectMetadataBtn() != null) v.jwkInspectMetadataBtn().setText(t("module.jose.inspectMetadata"));
        if (v.jwkKeyOpsField() != null) v.jwkKeyOpsField().setPromptText(t("module.jose.jwkKeyOpsPrompt"));
    }


    void refreshSecurityWarnings() {
        View v = view();
        setSecurityWarning(v.jwtSecurityWarningLabel(), combineWarnings(
                jwsSecurityWarning(valueOf(v.jwtAlgoCombo()), textOf(v.jwtKeyArea()), secretEncoding(v.jwtSecretFormatCombo())),
                jwsSecurityWarning(valueOf(v.jwtAlgo2Combo()), textOf(v.jwtKeyArea2()), secretEncoding(v.jwtSecretFormatCombo()))));
        setSecurityWarning(v.detachedSecurityWarningLabel(), jwsSecurityWarning(valueOf(v.detachedAlgoCombo()),
                textOf(v.detachedSigningKeyArea()), secretEncoding(v.detachedSecretFormatCombo())));
        setSecurityWarning(v.nestedSecurityWarningLabel(), combineWarnings(
                jwsSecurityWarning(valueOf(v.nestedSignAlgoCombo()), textOf(v.nestedSigningKeyArea()), secretEncoding(v.nestedSecretFormatCombo())),
                jweSecurityWarning(valueOf(v.nestedKeyAlgoCombo()))));
        setSecurityWarning(v.jweSecurityWarningLabel(), jweSecurityWarning(valueOf(v.jweKeyAlgoCombo())));
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

}
