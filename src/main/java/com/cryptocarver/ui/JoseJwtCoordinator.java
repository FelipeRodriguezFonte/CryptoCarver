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

import java.util.function.Supplier;

final class JoseJwtCoordinator extends JoseCoordinatorSupport {
    record View(ComboBox<String> detachedAlgoCombo,
            TextArea detachedPayloadArea,
            ComboBox<String> detachedSecretFormatCombo,
            ComboBox<String> detachedSerializationCombo,
            TextArea detachedSigningKeyArea,
            Label detachedStatusLabel,
            TextArea detachedTokenArea,
            CheckBox detachedUnencodedCheck,
            TextArea detachedVerificationKeyArea,
            ComboBox<String> jwsSerializationCombo,
            CheckBox jwsUnencodedPayloadCheck,
            CheckBox jwtAcceptNoneCheck,
            TextField jwtAccessTokenField,
            ComboBox<String> jwtAlgo2Combo,
            ComboBox<String> jwtAlgoCombo,
            TextField jwtAllowedAlgorithmsField,
            TextField jwtAudField,
            TextField jwtAuthorizationCodeField,
            CheckBox jwtCheckExpiryCheck,
            TextField jwtClockSkewField,
            TextArea jwtDecodedHeaderArea,
            TextArea jwtDecodedPayloadArea,
            TextField jwtExpField,
            TextField jwtExpectedAudField,
            TextField jwtExpectedContentTypeField,
            TextField jwtExpectedIssField,
            TextField jwtExpectedJktField,
            TextField jwtExpectedNonceField,
            TextField jwtExpectedTypeField,
            TextField jwtExpectedX5tField,
            TextArea jwtFindingsArea,
            CheckBox jwtIgnoreCritCheck,
            TextField jwtIssField,
            TextArea jwtKeyArea,
            TextArea jwtKeyArea2,
            CheckBox jwtOidcStrictCheck,
            TextArea jwtOutputArea,
            TextArea jwtPayloadArea,
            TextArea jwtProtectedHeaderArea,
            CheckBox jwtRfc9068Check,
            ComboBox<String> jwtSecretFormatCombo,
            Label jwtStatusLabel,
            TextField jwtSubField,
            CheckBox jwtTrustHeaderKeyCheck,
            TextField jwtUnderstoodCritField,
            TextArea jwtValidateKeyArea,
            ComboBox<String> jwtValidateSecretFormatCombo,
            TextArea jwtValidateTokenArea,
            CheckBox nestedCompressCheck,
            ComboBox<String> nestedContentAlgoCombo,
            TextArea nestedEncryptionKeyArea,
            ComboBox<String> nestedKeyAlgoCombo,
            TextArea nestedOutputArea,
            TextArea nestedPayloadArea,
            TextArea nestedPayloadOutputArea,
            ComboBox<String> nestedSecretFormatCombo,
            ComboBox<String> nestedSignAlgoCombo,
            TextArea nestedSigningKeyArea,
            Label nestedStatusLabel, TextArea jwtTrustAnchorsArea, TextField jwtCertificateDateField,
            Label jwtTrustAnchorsLabel, Label jwtCertificateDateLabel) { }
    private final Supplier<View> controls;
    private static final Logger LOG = LoggerFactory.getLogger(JoseJwtCoordinator.class);
    JoseJwtCoordinator(Supplier<View> controls, Supplier<StatusReporter> reporter) { super(reporter); this.controls = controls; }
    private View view() { return controls.get(); }
    void refreshCertificateLabels() {
        if(view().jwtTrustAnchorsLabel()!=null) view().jwtTrustAnchorsLabel().setText(t("module.jose.x5cAnchors"));
        if(view().jwtCertificateDateLabel()!=null) view().jwtCertificateDateLabel().setText(t("module.jose.x5cDate"));
    }
    private com.cryptocarver.crypto.JoseX5cValidation.Result certificateValidation(String token,String key,boolean trust) {
        try {
            JWSHeader header=JWSObject.parse(token).getHeader();
            if(header.getX509CertChain()==null || header.getX509CertChain().isEmpty()) return null;
            java.time.Instant date=java.time.Instant.now();
            String configured=textOf(view().jwtCertificateDateField());
            if(configured!=null && !configured.isBlank()) {
                try { date=java.time.Instant.parse(configured.trim()); }
                catch(java.time.format.DateTimeParseException invalid) { throw new IllegalArgumentException(t("module.jose.x5cDateInvalid")); }
            }
            return com.cryptocarver.crypto.JoseX5cValidation.validate(header,key,trust,textOf(view().jwtTrustAnchorsArea()),date);
        } catch(java.text.ParseException invalidToken) { return null; }
    }
    private void certificateDetails(OperationResult.Builder result, com.cryptocarver.crypto.JoseX5cValidation.Result certificates,boolean trust) {
        if(certificates==null) return;
        certificates.checks().forEach((name,value)->result.detail("x5c "+name,value));
        if(trust && !certificates.trusted()) result.detail("Security warning",t("module.jose.warning.x5cUntrusted"));
    }
    void handleApplyJWTClaims() {
        if (view().jwtPayloadArea() == null) return;
        long expHours = 1;
        String hours = textOf(view().jwtExpField());
        if (hours != null && !hours.isBlank()) {
            try {
                expHours = Long.parseLong(hours.trim());
            } catch (NumberFormatException e) {
                showValidation(t("module.jose.feedback.validForHours"), "jwtExpField");
                return;
            }
        }
        try {
            view().jwtPayloadArea().setText(JwtClaimsBuilder.apply(view().jwtPayloadArea().getText(), textOf(view().jwtIssField()),
                    textOf(view().jwtSubField()), textOf(view().jwtAudField()), expHours, System.currentTimeMillis() / 1000L));
        } catch (IllegalArgumentException | ArithmeticException e) {
            showValidation(t("module.jose.feedback.claimsPayload"), "jwtPayloadArea");
        }
    }

    void handleVerifyDetachedJWS() {
        if (isBlank(view().detachedTokenArea())) { showValidation(t("module.jose.feedback.detachedTokenRequired"), "detachedTokenArea"); return; }
        if (isBlank(view().detachedPayloadArea())) { showValidation(t("module.jose.feedback.detachedPayloadRequired"), "detachedPayloadArea"); return; }
        if (isBlank(view().detachedVerificationKeyArea())) { showValidation(t("module.jose.feedback.keyRequired"), "detachedVerificationKeyArea"); return; }
        if (view().detachedAlgoCombo() == null || view().detachedAlgoCombo().getValue() == null) {
            showValidation(t("module.jose.feedback.algorithmRequired"), "detachedAlgoCombo", "preflight.remedy.algorithm");
            return;
        }
        this.verifyDetachedJWS(view().detachedTokenArea().getText(), view().detachedPayloadArea().getText(), view().detachedAlgoCombo().getValue(),
                view().detachedVerificationKeyArea().getText(), secretEncoding(view().detachedSecretFormatCombo()), view().detachedStatusLabel());
    }

    void handleVerifyNestedJWT() {

            if (isBlank(view().nestedOutputArea())) { showValidation(t("module.jose.feedback.nestedTokenRequired"), "nestedOutputArea"); return; }
            if (isBlank(view().nestedEncryptionKeyArea())) { showValidation(t("module.jose.feedback.keyRequired"), "nestedEncryptionKeyArea"); return; }
            if (isBlank(view().nestedSigningKeyArea())) { showValidation(t("module.jose.feedback.keyRequired"), "nestedSigningKeyArea"); return; }

            String nestedToken = view().nestedOutputArea().getText();
            String decryptionKey = view().nestedEncryptionKeyArea().getText();
            String verificationKey = view().nestedSigningKeyArea().getText();
            this.verifyNestedJWT(nestedToken, decryptionKey, verificationKey, secretEncoding(view().nestedSecretFormatCombo()),
                    view().nestedPayloadOutputArea(), view().nestedStatusLabel());
        }

    void handleGenerateDetachedJWS() {

            if (isBlank(view().detachedPayloadArea())) { showValidation(t("module.jose.feedback.detachedPayloadRequired"), "detachedPayloadArea"); return; }
            if (isBlank(view().detachedSigningKeyArea())) { showValidation(t("module.jose.feedback.keyRequired"), "detachedSigningKeyArea"); return; }
            if (view().detachedAlgoCombo() == null || view().detachedAlgoCombo().getValue() == null) {
                showValidation(t("module.jose.feedback.algorithmRequired"), "detachedAlgoCombo", "preflight.remedy.algorithm");
                return;
            }

            String serialization = view().detachedSerializationCombo() != null ? view().detachedSerializationCombo().getValue() : "Compact";
            boolean unencoded = view().detachedUnencodedCheck() != null && view().detachedUnencodedCheck().isSelected();
            this.generateDetachedJWS(view().detachedPayloadArea().getText(), view().detachedAlgoCombo().getValue(), view().detachedSigningKeyArea().getText(),
                    secretEncoding(view().detachedSecretFormatCombo()), serialization, unencoded, view().detachedTokenArea());
        }

    void handleGenerateNestedJWT() {

            if (isBlank(view().nestedPayloadArea())) { showValidation(t("module.jose.feedback.inputRequired"), "nestedPayloadArea"); return; }
            if (isBlank(view().nestedSigningKeyArea())) { showValidation(t("module.jose.feedback.keyRequired"), "nestedSigningKeyArea"); return; }
            if (isBlank(view().nestedEncryptionKeyArea())) { showValidation(t("module.jose.feedback.keyRequired"), "nestedEncryptionKeyArea"); return; }

            this.generateNestedJWT(
                    view().nestedPayloadArea().getText(),
                    view().nestedSignAlgoCombo().getValue(),
                    view().nestedSigningKeyArea().getText(),
                    view().nestedKeyAlgoCombo().getValue(),
                    view().nestedContentAlgoCombo().getValue(),
                    view().nestedEncryptionKeyArea().getText(),
                    view().nestedCompressCheck().isSelected(),
                    secretEncoding(view().nestedSecretFormatCombo()),
                    view().nestedOutputArea());

        }

    void handleGenerateSignedJWT() {

            if (isBlank(view().jwtPayloadArea())) { showValidation(t("module.jose.feedback.inputRequired"), "jwtPayloadArea"); return; }
            String selectedAlgorithm = view().jwtAlgoCombo() == null ? null : view().jwtAlgoCombo().getValue();
            if (isBlank(view().jwtKeyArea()) && !"none".equalsIgnoreCase(selectedAlgorithm)) { showValidation(t("module.jose.feedback.keyRequired"), "jwtKeyArea"); return; }
            if (view().jwtAlgoCombo() == null || view().jwtAlgoCombo().getValue() == null) {
                showValidation(t("module.jose.feedback.algorithmRequired"), "jwtAlgoCombo", "preflight.remedy.algorithm");
                return;
            }

            String algo = view().jwtAlgoCombo().getSelectionModel().getSelectedItem();
            String key = view().jwtKeyArea().getText();
            String serialization = view().jwsSerializationCombo() != null ? view().jwsSerializationCombo().getValue() : "Compact";
            boolean unencoded = view().jwsUnencodedPayloadCheck() != null && view().jwsUnencodedPayloadCheck().isSelected();
            java.util.List<com.cryptocarver.crypto.SignerConfig> signers = new java.util.ArrayList<>();
            JoseKeyMaterial.SecretEncoding secretEncoding = secretEncoding(view().jwtSecretFormatCombo());
            signers.add(new com.cryptocarver.crypto.SignerConfig(algo, key, secretEncoding));
            if (view().jwtAlgo2Combo() != null && view().jwtKeyArea2() != null && !view().jwtKeyArea2().getText().trim().isEmpty()) {
                signers.add(new com.cryptocarver.crypto.SignerConfig(view().jwtAlgo2Combo().getSelectionModel().getSelectedItem(),
                        view().jwtKeyArea2().getText(), secretEncoding));
            }
            this.generateSignedJWT(
                    view().jwtPayloadArea().getText(),
                    signers,
                    serialization,
                    unencoded,
                    textOf(view().jwtProtectedHeaderArea()),
                    view().jwtOutputArea());

        }

    void handleValidateJWT() {

            if (isBlank(view().jwtValidateTokenArea())) { showValidation(t("module.jose.feedback.tokenRequired"), "jwtValidateTokenArea"); return; }
            boolean unsecured = JoseNoneJws.isUnsecuredCompact(view().jwtValidateTokenArea().getText());
            boolean trustHeaderKey = view().jwtTrustHeaderKeyCheck() != null && view().jwtTrustHeaderKeyCheck().isSelected();
            if (isBlank(view().jwtValidateKeyArea()) && !unsecured && !trustHeaderKey) { showValidation(t("module.jose.feedback.keyRequired"), "jwtValidateKeyArea"); return; }

            String iss = view().jwtExpectedIssField().getText();
            String aud = view().jwtExpectedAudField().getText();
            String skewStr = view().jwtClockSkewField().getText();
            long skew = 0;
            try {
                skew = Long.parseLong(skewStr);
            } catch (Exception e) {
            }
            boolean checkExp = view().jwtCheckExpiryCheck().isSelected();
            boolean oidcStrict = view().jwtOidcStrictCheck() != null && view().jwtOidcStrictCheck().isSelected();
            JwtValidator.Advanced advanced = new JwtValidator.Advanced(
                    textOf(view().jwtAllowedAlgorithmsField()), textOf(view().jwtExpectedTypeField()), textOf(view().jwtExpectedContentTypeField()),
                    view().jwtRfc9068Check() != null && view().jwtRfc9068Check().isSelected(), textOf(view().jwtExpectedNonceField()),
                    textOf(view().jwtAccessTokenField()), textOf(view().jwtAuthorizationCodeField()), textOf(view().jwtExpectedJktField()),
                    textOf(view().jwtExpectedX5tField()), parseHeaderNames(textOf(view().jwtUnderstoodCritField())),
                    view().jwtIgnoreCritCheck() != null && view().jwtIgnoreCritCheck().isSelected());
            this.validateJWTAdvanced(
                    view().jwtValidateTokenArea().getText(),
                    view().jwtValidateKeyArea().getText(),
                    iss, aud, skew, checkExp, oidcStrict,
                    trustHeaderKey,
                    advanced,
                    secretEncoding(view().jwtValidateSecretFormatCombo()),
                    view().jwtDecodedHeaderArea(),
                    view().jwtDecodedPayloadArea(),
                    view().jwtStatusLabel());

        }

    public void generateDetachedJWS(String payload, String algorithm, String key, JoseKeyMaterial.SecretEncoding secretEncoding,
            String serializationType, boolean unencodedPayload, TextArea output) {
        try {
            java.util.List<SignerConfig> signers = java.util.Collections.singletonList(new SignerConfig(algorithm, key, secretEncoding));
            String serialized = JOSEService.generateDetachedJWS(payload, signers, serializationType, unencodedPayload);

            output.setText(serialized);
            if (unencodedPayload) {
                reporter().showInfo("JWS Unencoded Payload (b64=false)", "WARNING: b64=false is enabled. The payload is detached if using standard JSON parsing.");
            }
            OperationResult.Builder result = OperationResult.forOperation("Detached JWS Generation")
                    .input(payload.getBytes(StandardCharsets.UTF_8)).output(serialized.getBytes(StandardCharsets.US_ASCII))
                    .detail("Algorithm", algorithm)
                    .detail("Serialization", serializationType != null ? serializationType : "Compact")
                    .detail("Unencoded", Boolean.toString(unencodedPayload));
            addSecurityWarning(result, jwsSecurityWarning(algorithm, key, secretEncoding));
            String metadataWarning = metadataWarning(key, JoseJwkPolicy.Operation.SIGN);
            if (metadataWarning != null) result.detail("Security warning", metadataWarning);
            reporter().publish(result.status(t("module.jose.feedback.statusDetachedGenerated")).build());
        } catch (Exception e) { reporter().showError("Detached JWS", t("module.jose.error", e.getMessage())); }
    }

    public void verifyDetachedJWS(String detached, String payload, String algorithm, String key,
            JoseKeyMaterial.SecretEncoding secretEncoding, Label status) {
        try {
            boolean valid = JOSEService.verifyDetachedJWS(detached, payload, algorithm, key, secretEncoding);
            status.setText(valid ? "VALID DETACHED SIGNATURE" : "INVALID DETACHED SIGNATURE");
            status.setStyle(valid ? "-fx-text-fill: green;" : "-fx-text-fill: red;");
            OperationResult.Builder result = OperationResult.forOperation("Detached JWS Verification")
                    .input(detached.getBytes(StandardCharsets.US_ASCII)).detail("Algorithm", algorithm)
                    .detail("Result", valid ? "VALID" : "INVALID");
            addSecurityWarning(result, jwsSecurityWarning(algorithm, key, secretEncoding));
            String metadataWarning = metadataWarning(key, JoseJwkPolicy.Operation.VERIFY);
            if (metadataWarning != null) result.detail("Security warning", metadataWarning);
            reporter().publish(result.status(t("module.jose.feedback.statusDetachedVerification", valid ? "valid" : "invalid")).build());
        } catch (Exception e) { status.setText(t("module.jose.error", e.getMessage())); status.setStyle("-fx-text-fill: red;"); }
    }

    public void generateSignedJWT(String payloadJson, java.util.List<SignerConfig> signers, String serializationType, boolean unencodedPayload, TextArea outputArea) {
        generateSignedJWT(payloadJson, signers, serializationType, unencodedPayload, null, outputArea);
    }

    public void generateSignedJWT(String payloadJson, java.util.List<SignerConfig> signers, String serializationType,
            boolean unencodedPayload, String protectedHeaderJson, TextArea outputArea) {
        try {
            String serialized = JOSEService.generateSignedJWT(payloadJson, signers, serializationType,
                    unencodedPayload, protectedHeaderJson);

            outputArea.setText(serialized);
            if (unencodedPayload) {
                reporter().showInfo("JWS Unencoded Payload (b64=false)", "WARNING: b64=false is enabled. This requires standard JWS JSON serialization (RFC 7797). The payload is detached if using standard JSON parsing. Many implementations might not support unencoded payloads.");
            }

            String primaryAlgo = signers.get(0).getAlgorithm();
            OperationResult.Builder result = OperationResult.forOperation("Signed JWT Generation")
                    .input(payloadJson.getBytes(StandardCharsets.UTF_8))
                    .output(serialized.getBytes(StandardCharsets.US_ASCII))
                    .detail("Algorithms", signers.stream().map(SignerConfig::getAlgorithm).reduce((a,b) -> a + ", " + b).orElse(""))
                    .detail("Serialization", serializationType != null ? serializationType : "Compact")
                    .detail("Unencoded", Boolean.toString(unencodedPayload));
            for (SignerConfig signer : signers) {
                addSecurityWarning(result, jwsSecurityWarning(signer.getAlgorithm(), signer.getSecretOrKey(), signer.getSecretEncoding()));
                String metadataWarning = metadataWarning(signer.getSecretOrKey(), JoseJwkPolicy.Operation.SIGN);
                if (metadataWarning != null) result.detail("Security warning", metadataWarning);
            }
            reporter().publish(result.status(t("module.jose.feedback.statusJwtGenerated", primaryAlgo)).build());

        } catch (Exception e) {
            reporter().showError("JWT Generation Error", e.getMessage());
            LOG.error("Signed JWT generation failed", e);
        }
    }

    public void generateNestedJWT(String payloadJson, String signAlgoStr, String signKey,
            String keyAlgoStr, String contentAlgoStr, String encKeyPEM, boolean compress,
            JoseKeyMaterial.SecretEncoding secretEncoding, TextArea outputArea) {
        try {
            String serialized = JOSEService.generateNestedJWT(payloadJson, signAlgoStr, signKey, keyAlgoStr,
                    contentAlgoStr, encKeyPEM, compress, secretEncoding);

            outputArea.setText(serialized);
            String status = "Nested JWT Generated (Signed: " + signAlgoStr + ", Encrypted: " + keyAlgoStr + ")";
            if (compress)
                status += " [Compressed]";
            OperationResult.Builder result = OperationResult.forOperation("Nested JWT Generation")
                    .input(payloadJson.getBytes(StandardCharsets.UTF_8))
                    .output(serialized.getBytes(StandardCharsets.US_ASCII))
                    .detail("Signature Algorithm", signAlgoStr).detail("Key Algorithm", keyAlgoStr)
                    .detail("Compression", String.valueOf(compress)).detail(com.cryptocarver.model.OperationDetail.secretDetail("Key Material", signKey + " / " + encKeyPEM));
            addSecurityWarning(result, jwsSecurityWarning(signAlgoStr, signKey, secretEncoding));
            addSecurityWarning(result, jweSecurityWarning(keyAlgoStr));
            reporter().publish(result.status(status).build());

        } catch (Exception e) {
            reporter().showError("Nested JWT Error", e.getMessage());
            LOG.error("Nested JWT generation failed", e);
        }
    }

    public void verifyNestedJWT(String nestedToken, String decryptionKeyPEM, String verificationKeyPEM,
            JoseKeyMaterial.SecretEncoding secretEncoding, TextArea payloadOut, Label statusLabel) {
        try {
            String payload = JOSEService.verifyNestedJWT(nestedToken, decryptionKeyPEM, verificationKeyPEM, secretEncoding);
            payloadOut.setText(payload);
            statusLabel.setText(t("module.jose.decryptedVerified"));
            statusLabel.setStyle("-fx-text-fill: green;");

            reporter().publish(OperationResult.forOperation("Nested JWT Verification")
                .input(nestedToken.getBytes(StandardCharsets.US_ASCII))
                .output(payloadOut.getText().getBytes(StandardCharsets.UTF_8), com.cryptocarver.model.OperationDetail.Classification.SECRET)
                .status(t("module.jose.feedback.statusNested")).build());

        } catch (Exception e) {
            statusLabel.setText(t("module.jose.error", e.getMessage()));
            statusLabel.setStyle("-fx-text-fill: red;");
            reporter().showError("Nested JWT Verification Error", e.getMessage());
            LOG.error("Nested JWT verification failed", e);
        }
    }

    public void validateJWTAdvanced(String tokenString, String keyString,
            String expectedIss, String expectedAud, long clockSkewSec, boolean checkExpiry, boolean oidcStrict,
            JoseKeyMaterial.SecretEncoding secretEncoding, TextArea headerOut, TextArea payloadOut, Label statusLabel) {
        validateJWTAdvanced(tokenString, keyString, expectedIss, expectedAud, clockSkewSec, checkExpiry,
                oidcStrict, false, secretEncoding, headerOut, payloadOut, statusLabel);
    }

    public void validateJWTAdvanced(String tokenString, String keyString,
            String expectedIss, String expectedAud, long clockSkewSec, boolean checkExpiry, boolean oidcStrict,
            boolean trustHeaderKey, JoseKeyMaterial.SecretEncoding secretEncoding,
            TextArea headerOut, TextArea payloadOut, Label statusLabel) {
        validateJWTAdvanced(tokenString, keyString, expectedIss, expectedAud, clockSkewSec, checkExpiry,
                oidcStrict, trustHeaderKey, JwtValidator.Advanced.defaults(), secretEncoding, headerOut, payloadOut, statusLabel);
    }

    public void validateJWTAdvanced(String tokenString, String keyString,
            String expectedIss, String expectedAud, long clockSkewSec, boolean checkExpiry, boolean oidcStrict,
            boolean trustHeaderKey, JwtValidator.Advanced advanced, JoseKeyMaterial.SecretEncoding secretEncoding,
            TextArea headerOut, TextArea payloadOut, Label statusLabel) {
        if (view().jwtFindingsArea() != null) view().jwtFindingsArea().clear();
        com.cryptocarver.crypto.JoseX5cValidation.Result certificates = null;
        try {
            certificates = certificateValidation(tokenString,keyString,trustHeaderKey);
            JwtValidator.Result result = JwtValidator.validate(tokenString, keyString,
                    new JwtValidator.Options(expectedIss, expectedAud, clockSkewSec, checkExpiry, oidcStrict,
                            secretEncoding, view().jwtAcceptNoneCheck() != null && view().jwtAcceptNoneCheck().isSelected(), trustHeaderKey, advanced),
                    java.time.Instant.now());
            headerOut.setText(result.header());
            payloadOut.setText(result.payload());

            List<String> findings = new ArrayList<>();
            for (JwtValidator.Finding finding : result.findings()) {
                findings.add(t("module.jose.claim." + finding.code(), finding.argument()));
            }
            List<String> securityWarnings = new ArrayList<>();
            for (JwtValidator.Warning warning : result.warnings()) {
                securityWarnings.add(t("module.jose.warning." + warning.code(), warning.argument()));
            }
            if (certificates != null && trustHeaderKey && !certificates.trusted()) securityWarnings.add(t("module.jose.warning.x5cUntrusted"));
            if (view().jwtFindingsArea() != null) view().jwtFindingsArea().setText(String.join("\n", findings)
                    + (securityWarnings.isEmpty() ? "" : (findings.isEmpty() ? "" : "\n") + String.join("\n", securityWarnings)));

            String status;
            if (!result.signatureValid()) {
                status = t("module.jose.invalidSignature");
                statusLabel.setStyle("-fx-text-fill: red;");
            } else if (!findings.isEmpty()) {
                status = t("module.jose.invalidClaims");
                statusLabel.setStyle("-fx-text-fill: orange;");
            } else {
                status = t("module.jose.validSignatureAndClaims");
                statusLabel.setStyle("-fx-text-fill: green;");
            }
            statusLabel.setText(status);
            OperationResult.Builder validationResult = OperationResult.forOperation("JWT Validation")
                    .input(tokenString.getBytes(StandardCharsets.US_ASCII))
                    .detail("Signature", result.signatureValid() ? "VALID" : "INVALID")
                    .detail("Claim Checks", findings.isEmpty() ? "OK" : String.join("; ", findings))
                    .detail(com.cryptocarver.model.OperationDetail.secretDetail("Key Material", keyString))
                    .detail("Security warning", jwtTokenSecurityWarning(tokenString, keyString, secretEncoding));
            String metadataWarning = metadataWarning(keyString, JoseJwkPolicy.Operation.VERIFY);
            if (metadataWarning != null) validationResult.detail("Security warning", metadataWarning);
            if (trustHeaderKey) validationResult.detail("Security warning", t("module.jose.warning.trustHeaderKey"));
            for (String warning : securityWarnings) {
                validationResult.detail("Security warning", warning);
            }
            certificateDetails(validationResult,certificates,trustHeaderKey);
            reporter().publish(validationResult.status(t("module.jose.feedback.statusJwtValidation", status)).build());
        } catch (Exception e) {
            headerOut.setText("");
            payloadOut.setText("");
            statusLabel.setText(t("module.jose.error", e.getMessage()));
            statusLabel.setStyle("-fx-text-fill: red;");
            // No exception attached: key parsers may echo key material.
            if(certificates!=null) {
                OperationResult.Builder failed=OperationResult.forOperation("JWT Validation").detail("Signature","INVALID").status(t("module.jose.invalidSignature"));
                certificateDetails(failed,certificates,trustHeaderKey);reporter().publish(failed.build());
            }
            LOG.error("JWT validation failed: {}", e.getClass().getSimpleName());
        }
    }

}
