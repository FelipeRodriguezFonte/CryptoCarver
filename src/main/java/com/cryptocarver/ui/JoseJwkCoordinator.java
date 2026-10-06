package com.cryptocarver.ui;

import com.cryptocarver.crypto.JoseKeyMaterial;
import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.SecretVisibilityProfile;
import java.nio.charset.StandardCharsets;
import com.cryptocarver.crypto.JoseJwkPolicy;
import com.cryptocarver.util.DataConverter;
import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.OctetSequenceKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.util.Base64URL;
import javafx.scene.control.*;
import javafx.scene.control.Alert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.*;
import java.util.function.Supplier;

final class JoseJwkCoordinator extends JoseCoordinatorSupport {
    record View(TextArea jwkInputArea, TextArea jwkOutputArea, ComboBox<String> jwkKeyTypeCombo,
            TextField jwkKeyIdField, ComboBox<String> jwkUseCombo, TextField jwkKeyOpsField,
            TextArea jwksArea, ComboBox<String> jwksRotateAlgoCombo) { }

    private static final Logger LOG = LoggerFactory.getLogger(JoseJwkCoordinator.class);
    private final Supplier<View> controls;
    private final DialogService dialogService;
    private final Map<TextArea, String> originalIds = new WeakHashMap<>();
    private final Set<TextArea> guardedAreas = Collections.newSetFromMap(new WeakHashMap<>());
    private String jwksMaterial, jwksDisplay;

    private void present(String operation, String content, boolean secret, TextArea area) {
        originalIds.putIfAbsent(area, area.getId() == null ? "joseOutputArea" : area.getId());
        area.setId(originalIds.get(area) + (secret ? "Secret" : ""));
        if (guardedAreas.add(area)) {
            area.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
                if (area.getId().endsWith("Secret") && !AppSettings.isFullLab() && event.isShortcutDown()
                        && (event.getCode() == javafx.scene.input.KeyCode.C || event.getCode() == javafx.scene.input.KeyCode.X)) {
                    event.consume(); updateStatus(t("module.jose.jwkPrivateHidden"));
                }
            });
            area.addEventFilter(javafx.scene.input.ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
                if (area.getId().endsWith("Secret") && !AppSettings.isFullLab()) {
                    event.consume(); updateStatus(t("module.jose.jwkPrivateHidden"));
                }
            });
        }
        SecretVisibilityProfile profile = AppSettings.getInstance().getSecretVisibilityProfile();
        area.setText(secret && profile != SecretVisibilityProfile.FULL_LAB
                ? profile == SecretVisibilityProfile.MASKED ? "***MASKED***" : t("module.jose.jwkPrivateHidden") : content);
        OperationDetail.Classification classification = secret ? OperationDetail.Classification.SECRET : OperationDetail.Classification.PUBLIC;
        if (reporter() != null) reporter().publish(OperationResult.forOperation(operation)
                .output(content.getBytes(StandardCharsets.UTF_8), classification).enrichedOutput(content, classification)
                .status(secret && profile != SecretVisibilityProfile.FULL_LAB ? t("module.jose.jwkPrivateHidden") : t("module.jose.jwkResultReady"))
                .build());
    }

    private String currentJwks() {
        String text = textOf(view().jwksArea());
        return Objects.equals(text, jwksDisplay) && jwksMaterial != null ? jwksMaterial : text;
    }

    private void presentJwks(String content) {
        boolean secret;
        try { secret = JWKSet.parse(content).getKeys().stream().anyMatch(JWK::isPrivate); }
        catch (Exception invalid) { secret = true; }
        jwksMaterial = content;
        present("JWK Set", content, secret, view().jwksArea());
        jwksDisplay = view().jwksArea().getText();
    }

    String currentPublicJwks() throws Exception { return exportPublicJWKS(currentJwks()); }

    void handleExportPublicJWKS() {
        try {
            TextArea area = new TextArea(currentPublicJwks());
            area.setEditable(false); area.setWrapText(true); area.setPrefSize(500, 300);
            dialogService.show(Alert.AlertType.INFORMATION, null, "Public JWKS", "Public Keys Only", area, ButtonType.OK);
        } catch (Exception e) { showError("Export Error", t("module.jose.jwkInvalidMaterial")); }
    }

    JoseJwkCoordinator(Supplier<View> controls, Supplier<StatusReporter> reporter, DialogService dialogService) {
        super(reporter);
        this.controls = controls;
        this.dialogService = dialogService;
    }

    private View view() { return controls.get(); }

    void handlePemToJwk() {
        if (isBlank(view().jwkInputArea())) {
            showValidation(t("module.jose.feedback.inputPem"), "jwkInputArea");
            return;
        }
        convertPemToJwk(view().jwkInputArea().getText(), view().jwkKeyTypeCombo().getValue(),
                view().jwkKeyIdField().getText(), view().jwkUseCombo() == null ? null : view().jwkUseCombo().getValue(),
                textOf(view().jwkKeyOpsField()), view().jwkOutputArea());
    }

    void handleJwkToPem() {
        if (isBlank(view().jwkInputArea())) {
            showValidation(t("module.jose.feedback.inputPem"), "jwkInputArea");
            return;
        }
        convertJwkToPem(view().jwkInputArea().getText(), view().jwkOutputArea());
    }

    void handleCalculateThumbprint() {
        if (isBlank(view().jwkInputArea())) {
            showValidation(t("module.jose.feedback.thumbprintInput"), "jwkInputArea");
            return;
        }
        calculateThumbprint(view().jwkInputArea().getText(), view().jwkOutputArea());
    }

    void handleNewJWKS() {
        if (view().jwksArea() != null) presentJwks("{\n  \"keys\": []\n}");
    }

    void loadedJWKS(String content) {
        presentJwks(content);
        updateStatus(t("module.jose.feedback.jwksLoaded"));
    }

    void handleRotateKey() {
        try {
            String alg = view().jwksRotateAlgoCombo().getValue();
            if (alg == null) {
                showValidation(t("module.jose.feedback.algorithmRequired"), "jwksRotateAlgoCombo", "preflight.remedy.algorithm");
                return;
            }
            if ((alg.startsWith("HS") || alg.startsWith("A") || alg.equals("dir"))
                    && LabPrompt.JWKS_SECRET.shouldShow()) {
                String warningText = "You are adding a SYMMETRIC key (Secret) to this JWK Set.\n\n" +
                        "If you publish this JWKS file publicly (e.g. at .well-known/jwks.json), ANYONE will be able to read your secret key and forge tokens.\n\n" +
                        "Are you sure you want to proceed?";
                Optional<ButtonType> result = dialogService.show(Alert.AlertType.WARNING, null,
                        "Security Warning", "Symmetric Key in Public JWKS", new Label(warningText),
                        ButtonType.NO, ButtonType.YES);
                if (result.isEmpty() || result.get() != ButtonType.YES) return;
            }
            String use = alg.startsWith("A") || alg.equals("dir") || alg.startsWith("RSA1_5")
                    || alg.startsWith("RSA-OAEP") || alg.startsWith("ECDH-ES") ? "enc" : "sig";
            JWK newKey = JoseJwkPolicy.withMetadata(generateNewJWK(alg, use), use, textOf(view().jwkKeyOpsField()));
            String currentJson = currentJwks();
            if (currentJson == null || currentJson.isBlank()) currentJson = "{\"keys\":[]}";
            presentJwks(addToJWKSet(currentJson, newKey));
            updateStatus(t("module.jose.feedback.keyAdded", alg));
        } catch (Exception e) {
            showError("Rotate Key Error", t("module.jose.jwkInvalidMaterial"));
        }
    }

    void handleInspectJwkMetadata() {
        try {
            JWK key = JWK.parse(view().jwkInputArea().getText());
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("kty", key.getKeyType().getValue());
            if (key.getKeyID() != null) metadata.put("kid", key.getKeyID());
            if (key.getKeyUse() != null) metadata.put("use", key.getKeyUse().identifier());
            if (key.getKeyOperations() != null) metadata.put("key_ops", key.getKeyOperations().stream()
                    .map(KeyOperation::identifier).sorted().toList());
            if (key.getAlgorithm() != null) metadata.put("alg", key.getAlgorithm().getName());
            present("JWK Metadata", com.nimbusds.jose.util.JSONObjectUtils.toJSONString(metadata), false, view().jwkOutputArea());
        } catch (Exception e) {
            present("JWK Metadata", t("module.jose.jwkInvalidMaterial"), false, view().jwkOutputArea());
        }
    }

    public JWK generateNewJWK(String alg, String use) throws Exception {
        if (Set.of("RS256", "RS384", "RS512", "PS256", "PS384", "PS512").contains(alg)) {
            return new RSAKeyGenerator(2048).keyUse(use.equals("sig") ? KeyUse.SIGNATURE : KeyUse.ENCRYPTION)
                    .algorithm(new JWSAlgorithm(alg)).keyID(UUID.randomUUID().toString()).generate();
        } else if ("RSA1_5".equals(alg) || "RSA-OAEP".equals(alg) || "RSA-OAEP-256".equals(alg)
                || "RSA-OAEP-384".equals(alg) || "RSA-OAEP-512".equals(alg)) {
            return new RSAKeyGenerator(2048).keyUse(KeyUse.ENCRYPTION).algorithm(new JWEAlgorithm(alg))
                    .keyID(UUID.randomUUID().toString()).generate();
        } else if (alg.equals("EdDSA")) {
            java.security.KeyPair pair = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            return new OctetKeyPair.Builder(Curve.Ed25519, Base64URL.encode(JoseKeyMaterial.rawEdPublicKey(pair.getPublic())))
                    .d(Base64URL.encode(JoseKeyMaterial.rawEdPrivateKey(pair.getPrivate()))).keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.EdDSA).keyID(UUID.randomUUID().toString()).build();
        } else if (alg.startsWith("ECDH-ES")) {
            boolean x448 = alg.contains("X448");
            Curve curve = x448 ? Curve.X448 : Curve.X25519;
            String xdhAlgorithm = x448 ? "X448" : "X25519";
            java.security.KeyPair pair = java.security.KeyPairGenerator.getInstance(xdhAlgorithm).generateKeyPair();
            return new OctetKeyPair.Builder(curve, Base64URL.encode(JoseKeyMaterial.rawXPublicKey(pair.getPublic())))
                    .d(Base64URL.encode(JoseKeyMaterial.rawXPrivateKey(pair.getPrivate()))).keyUse(KeyUse.ENCRYPTION)
                    .algorithm(new JWEAlgorithm("ECDH-ES-X448".equals(alg) ? "ECDH-ES" : alg))
                    .keyID(UUID.randomUUID().toString()).build();
        } else if (alg.startsWith("ES")) {
            if ("ES256K".equals(alg)) {
                java.security.KeyPairGenerator generator = java.security.KeyPairGenerator.getInstance("EC",
                        new org.bouncycastle.jce.provider.BouncyCastleProvider());
                generator.initialize(new java.security.spec.ECGenParameterSpec("secp256k1"));
                java.security.KeyPair pair = generator.generateKeyPair();
                return new ECKey.Builder(Curve.SECP256K1, (java.security.interfaces.ECPublicKey)pair.getPublic())
                        .privateKey(pair.getPrivate()).keyUse(use.equals("sig") ? KeyUse.SIGNATURE : KeyUse.ENCRYPTION)
                        .algorithm(new JWSAlgorithm(alg)).keyID(UUID.randomUUID().toString()).build();
            }
            Curve curve = Curve.P_256;
            if (alg.contains("384")) curve = Curve.P_384;
            if (alg.contains("512")) curve = Curve.P_521;
            return new ECKeyGenerator(curve).keyUse(use.equals("sig") ? KeyUse.SIGNATURE : KeyUse.ENCRYPTION)
                    .algorithm(new JWSAlgorithm(alg)).keyID(UUID.randomUUID().toString()).generate();
        } else if (alg.startsWith("HS") || alg.startsWith("A") || alg.equals("dir")) {
            int bitLength = 256;
            if (alg.contains("128")) bitLength = 128;
            if (alg.contains("384")) bitLength = 384;
            if (alg.contains("512")) bitLength = 512;
            LOG.debug("Generating symmetric JWK for algorithm {}", alg);
            JWK key = new OctetSequenceKeyGenerator(bitLength)
                    .keyUse(use.equals("sig") ? KeyUse.SIGNATURE : KeyUse.ENCRYPTION)
                    .algorithm(new Algorithm(alg)).keyID(UUID.randomUUID().toString()).generate();
            LOG.debug("Generated symmetric JWK (key material intentionally omitted from logs)");
            return key;
        }
        throw new IllegalArgumentException("Unsupported algorithm for JWK generation: " + alg);
    }

    public String addToJWKSet(String currentJson, JWK newKey) throws Exception {
        JWKSet jwkSet;
        if (currentJson == null || currentJson.trim().isEmpty()) jwkSet = new JWKSet(newKey);
        else {
            try {
                jwkSet = JWKSet.parse(currentJson);
                List<JWK> keys = new ArrayList<>(jwkSet.getKeys());
                keys.add(newKey);
                jwkSet = new JWKSet(keys);
            } catch (java.text.ParseException e) {
                if (currentJson.trim().length() > 20) throw new Exception("Failed to parse existing JWK Set: " + e.getMessage());
                jwkSet = new JWKSet(newKey);
            }
        }
        return new com.google.gson.Gson().toJson(jwkSet.toJSONObject(false));
    }

    public String exportPublicJWKS(String json) throws Exception {
        return JWKSet.parse(json).toPublicJWKSet().toString();
    }

    public void convertPemToJwk(String pem, String keyType, String keyId, TextArea outputArea) {
        convertPemToJwk(pem, keyType, keyId, null, null, outputArea);
    }

    public void convertPemToJwk(String pem, String keyType, String keyId, String use, String keyOps, TextArea outputArea) {
        if (pem == null || pem.trim().isEmpty()) { outputArea.setText("Error: Input PEM is empty."); return; }
        try {
            String kid = keyId == null || keyId.isBlank() ? null : keyId.trim();
            JWK jwk = "OCT".equalsIgnoreCase(keyType)
                    ? new OctetSequenceKey.Builder(DataConverter.decodeBase64Flexible(pem.replaceAll("\\s+", ""))).keyID(kid).build()
                    : asymmetricJwk(pem, keyType, kid);
            jwk = JoseJwkPolicy.withMetadata(jwk, use, keyOps);
            String thumbprint = jwk.computeThumbprint().toString();
            if (kid == null) jwk = withKeyId(jwk, thumbprint);
            present("PEM to JWK", jwk.toJSONString() + "\n\n// Thumbprint (SHA-256): " + thumbprint, jwk.isPrivate(), outputArea);
        } catch (Exception e) {
            present("PEM to JWK", "Error converting to JWK: " + t("module.jose.jwkInvalidMaterial"), false, outputArea);
            LOG.error("PEM key import failed for key type {}", keyType);
        }
    }

    static JWK asymmetricJwk(String keyMaterial, String keyType, String kid) throws Exception {
        PublicKey publicKey = JoseKeyMaterial.publicKey(keyMaterial);
        PrivateKey privateKey = null;
        try { privateKey = JoseKeyMaterial.privateKey(keyMaterial); }
        catch (IllegalArgumentException publicOnly) { /* public key, certificate or public JWK */ }
        if (publicKey instanceof RSAPublicKey rsa) {
            if (keyType != null && !"RSA".equalsIgnoreCase(keyType)) throw new IllegalArgumentException("The key is RSA but key type " + keyType + " is selected.");
            RSAKey.Builder builder = new RSAKey.Builder(rsa).keyID(kid);
            if (privateKey != null) builder.privateKey(privateKey);
            return builder.build();
        }
        if (publicKey instanceof java.security.interfaces.ECPublicKey ec) {
            if (keyType != null && !"EC".equalsIgnoreCase(keyType)) throw new IllegalArgumentException("The key is EC but key type " + keyType + " is selected.");
            ECKey.Builder builder = new ECKey.Builder(Curve.forECParameterSpec(ec.getParams()), ec).keyID(kid);
            if (privateKey != null) builder.privateKey(privateKey);
            return builder.build();
        }
        if (publicKey instanceof java.security.interfaces.EdECPublicKey) {
            if (keyType != null && !"OKP".equalsIgnoreCase(keyType)) throw new IllegalArgumentException("The key is an Ed25519/Ed448 (OKP) key but key type " + keyType + " is selected.");
            byte[] x = JoseKeyMaterial.rawEdPublicKey(publicKey);
            OctetKeyPair.Builder builder = new OctetKeyPair.Builder(x.length == 32 ? Curve.Ed25519 : Curve.Ed448, Base64URL.encode(x)).keyID(kid);
            if (privateKey != null) builder.d(Base64URL.encode(JoseKeyMaterial.rawEdPrivateKey(privateKey)));
            return builder.build();
        }
        if (publicKey instanceof java.security.interfaces.XECPublicKey) {
            if (keyType != null && !"OKP".equalsIgnoreCase(keyType)) throw new IllegalArgumentException("The key is an X25519/X448 (OKP) key but key type " + keyType + " is selected.");
            boolean x25519 = "X25519".equalsIgnoreCase(JoseKeyMaterial.xdhCurveName(publicKey));
            OctetKeyPair.Builder builder = new OctetKeyPair.Builder(x25519 ? Curve.X25519 : Curve.X448, Base64URL.encode(JoseKeyMaterial.rawXPublicKey(publicKey))).keyID(kid);
            if (privateKey != null) builder.d(Base64URL.encode(JoseKeyMaterial.rawXPrivateKey(privateKey)));
            return builder.build();
        }
        throw new IllegalArgumentException("Unsupported key algorithm: " + publicKey.getAlgorithm());
    }

    private static String pem(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n" + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der)
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
            JWK jwk = JWK.parse(jwkJson);
            StringBuilder sb = new StringBuilder();
            if (jwk instanceof RSAKey rsaKey) {
                sb.append("=== Public Key (PEM) ===\n").append(pem("PUBLIC KEY", rsaKey.toRSAPublicKey().getEncoded())).append('\n');
                if (rsaKey.isPrivate()) sb.append("=== Private Key (PEM) ===\n").append(pem("PRIVATE KEY", rsaKey.toRSAPrivateKey().getEncoded()));
                present("JWK to PEM", sb.toString(), jwk.isPrivate(), outputArea);
            } else if (jwk instanceof ECKey ecKey) {
                java.security.Provider provider = Curve.SECP256K1.equals(ecKey.getCurve()) ? new org.bouncycastle.jce.provider.BouncyCastleProvider() : null;
                java.security.interfaces.ECPublicKey pub = provider == null ? ecKey.toECPublicKey() : ecKey.toECPublicKey(provider);
                sb.append("=== Public Key (PEM) ===\n").append(pem("PUBLIC KEY", pub.getEncoded())).append('\n');
                if (ecKey.isPrivate()) {
                    java.security.interfaces.ECPrivateKey priv = provider == null ? ecKey.toECPrivateKey() : ecKey.toECPrivateKey(provider);
                    sb.append("=== Private Key (PEM) ===\n").append(pem("PRIVATE KEY", priv.getEncoded()));
                }
                present("JWK to PEM", sb.toString(), jwk.isPrivate(), outputArea);
            } else if (jwk instanceof OctetSequenceKey octKey) {
                byte[] secret = octKey.toByteArray();
                sb.append("=== Symmetric Key (Secret) ===\nLength: ").append(secret.length * 8).append(" bits (").append(secret.length).append(" bytes)\n\nHex:\n");
                for (byte b : secret) sb.append(String.format("%02x", b));
                sb.append("\n\nBase64:\n").append(Base64.getEncoder().encodeToString(secret)).append("\n\nBase64URL:\n")
                        .append(Base64.getUrlEncoder().withoutPadding().encodeToString(secret));
                present("JWK to PEM", sb.toString(), jwk.isPrivate(), outputArea);
            } else if (jwk instanceof OctetKeyPair okp) {
                String json = okp.toJSONString();
                sb.append("=== Public Key (PEM) ===\n").append(pem("PUBLIC KEY", JoseKeyMaterial.publicKey(json).getEncoded())).append('\n');
                if (okp.isPrivate()) sb.append("=== Private Key (PEM) ===\n").append(pem("PRIVATE KEY", JoseKeyMaterial.privateKey(json).getEncoded()));
                present("JWK to PEM", sb.toString(), jwk.isPrivate(), outputArea);
            } else outputArea.setText("Unsupported or Unknown Key Type for PEM export: " + jwk.getKeyType());
        } catch (Exception e) { present("JWK to PEM", "Error converting JWK to PEM: " + t("module.jose.jwkInvalidMaterial"), false, outputArea); }
    }

    public void calculateThumbprint(String input, TextArea outputArea) {
        try {
            JWK jwk = input.trim().startsWith("{") ? JWK.parse(input) : asymmetricJwk(input, null, null);
            present("JWK Thumbprint", "SHA-256 Thumbprint (RFC 7638):\n" + jwk.computeThumbprint(), false, outputArea);
        } catch (Exception e) { present("JWK Thumbprint", "Error calculating thumbprint: " + t("module.jose.jwkInvalidMaterial"), false, outputArea); }
    }
}
