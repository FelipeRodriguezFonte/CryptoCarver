package com.cryptocarver.ui;

import com.cryptocarver.crypto.TsaDiagnostics;
import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.model.TsaAuthCredentials;
import com.cryptocarver.model.TsaUrlSanitizer;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/** Coordinates TSA endpoints, saved TSA profiles and RFC 3161 timestamp tokens for the XML module. */
final class XmlSignatureTimestampCoordinator {
    static final String NO_TSA = "No TSA (XAdES-BASELINE-B)";
    static final String DIGICERT_TSA = "DigiCert — http://timestamp.digicert.com";
    static final String FREETSA_TSA = "FreeTSA — https://freetsa.org/tsr";

    record View(Supplier<ComboBox<String>> xmlSignTsaUrlText,
            Supplier<ComboBox<String>> xmlSignTsaProfileCombo,
            Supplier<TextField> xmlSignTsaProfileNameField,
            Supplier<ComboBox<String>> xmlSignTsaAuthTypeCombo,
            Supplier<TextField> xmlSignTsaUserField,
            Supplier<PasswordField> xmlSignTsaPasswordField,
            Supplier<TextField> xmlTimestampFileField,
            Supplier<TextField> xmlTimestampUrlField,
            Supplier<ComboBox<String>> xmlTimestampHashCombo,
            Supplier<TextField> xmlTimestampTokenField,
            Supplier<TextArea> xmlTimestampReportArea,
            Supplier<TextField> xmlTimestampTrustStoreField,
            Supplier<PasswordField> xmlTimestampTrustStorePasswordField,
            Function<String, File> chooseFile) { }

    private final View view;
    private final Supplier<StatusReporter> reporter;
    private byte[] lastTimestampToken;

    XmlSignatureTimestampCoordinator(View view, Supplier<StatusReporter> reporter) {
        this.view = view;
        this.reporter = reporter;
    }

    private String t(String key, Object... args) {
        return com.cryptocarver.service.I18nService.getInstance().text(key, args);
    }

    void clearLastTimestampToken() {
        lastTimestampToken = null;
    }

    TsaAuthCredentials getTsaCredentials() {
        ComboBox<String> authTypeCombo = view.xmlSignTsaAuthTypeCombo().get();
        if (authTypeCombo == null) return null;
        String typeStr = authTypeCombo.getValue();
        if (typeStr == null || "NONE".equals(typeStr)) return null;
        TsaAuthCredentials.AuthType type = TsaAuthCredentials.AuthType.valueOf(typeStr);
        TextField userField = view.xmlSignTsaUserField().get();
        PasswordField passwordField = view.xmlSignTsaPasswordField().get();
        String user = userField != null ? userField.getText() : "";
        String pass = passwordField != null ? passwordField.getText() : "";
        return new TsaAuthCredentials(type, user, pass);
    }

    void handleTestTSA() {
        String url = getTsaUrl();
        if (url == null) {
            reporter.get().showError("TSA Test", t("module.xml.tsaRequired", "XAdES"));
            return;
        }
        if (!isHttpUrl(url)) {
            reporter.get().showError("TSA URL Error", t("module.xml.tsaUrlInvalid"));
            return;
        }
        saveCustomTsa(url);
        reporter.get().updateStatus(t("module.xml.status.testing"));
        TsaAuthCredentials auth = getTsaCredentials();
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                TsaDiagnostics.TokenResult result = TsaDiagnostics.timestamp(url, "CryptoCarver TSA diagnostic".getBytes(java.nio.charset.StandardCharsets.UTF_8), "SHA-256", 15000, 20000, 1024*1024, auth);
                TsaDiagnostics.Report report = result.report();
                javafx.application.Platform.runLater(() -> reporter.get().showInfo("TSA Test", t("module.xml.status.success")
                        + "\nURL: " + publishedTsaUrl(report.url()) + "\nHTTP: " + report.httpStatus() + "\nLatency: " + report.latencyMs()
                                + " ms\nPolicy: " + report.policyOid() + "\nImprint: " + report.imprintAlgorithmOid()
                                + "\nToken time: " + report.generationTime() + "\nResponse: " + report.responseBytes() + " bytes"));
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> reporter.get().showError("TSA Test", t("module.xml.error.generic", e.getMessage())));
            }
        });
    }

    void handleSaveTSA() {
        String url = getTsaUrl();
        if (url == null) {
            reporter.get().showError("Save TSA", t("module.xml.feedback.tsaRequestRequired"));
            return;
        }
        if (!isHttpUrl(url)) {
            reporter.get().showError("TSA URL Error", t("module.xml.tsaUrlInvalid"));
            return;
        }
        saveCustomTsa(url);
        reporter.get().showInfo("TSA Saved", t("module.xml.status.success") + "\n\n" + publishedTsaUrl(url));
    }

    void handleLoadTSASavedProfile() {
        String name = view.xmlSignTsaProfileCombo().get().getValue();
        if (name == null || name.isBlank()) {
            reporter.get().showError("TSA Profile", t("module.xml.feedback.tsaProfileRequired"));
            return;
        }
        AppSettings.getInstance().getTsaProfiles().stream()
                .filter(profile -> name.equals(profile.name()))
                .findFirst()
                .ifPresentOrElse(profile -> {
                    view.xmlSignTsaUrlText().get().getEditor().setText(profile.url());
                    view.xmlSignTsaUrlText().get().setValue(profile.url());
                    view.xmlSignTsaProfileNameField().get().setText(profile.name());
                    reporter.get().updateStatus(t("module.xml.status.success") + " (" + profile.name() + ")");
                }, () -> reporter.get().showError("TSA Profile", t("module.xml.profileMissing")));
    }

    void handleSaveTSASavedProfile() {
        String url = getTsaUrl();
        String name = view.xmlSignTsaProfileNameField().get().getText().trim();
        if (name.isEmpty()) {
            reporter.get().showError("TSA Profile", t("module.xml.feedback.tsaProfileNameRequired"));
            return;
        }
        if (url == null || !isHttpUrl(url)) {
            reporter.get().showError("TSA URL Error", t("module.xml.tsaUrlInvalid"));
            return;
        }
        AppSettings.getInstance().saveTsaProfile(name, url);
        saveCustomTsa(url);
        reloadTsaProfiles();
        view.xmlSignTsaProfileCombo().get().setValue(name);
        reporter.get().showInfo("TSA Profile Saved", name + "\n" + publishedTsaUrl(url) + "\n\nOnly the endpoint is saved; no credentials are stored.");
    }

    void handleDeleteTSASavedProfile() {
        String name = view.xmlSignTsaProfileCombo().get().getValue();
        if (name == null || name.isBlank()) {
            reporter.get().showError("TSA Profile", t("module.xml.feedback.tsaProfileRequired"));
            return;
        }
        AppSettings.getInstance().removeTsaProfile(name);
        reloadTsaProfiles();
        view.xmlSignTsaProfileNameField().get().clear();
        reporter.get().updateStatus(t("module.xml.status.success") + " (" + name + ")");
    }

    void handleBrowseTimestampFile() {
        File file = view.chooseFile().apply("Select File to Timestamp");
        if (file != null) view.xmlTimestampFileField().get().setText(file.getAbsolutePath());
    }

    void handleRequestTimestamp() {
        String path = view.xmlTimestampFileField().get().getText().trim();
        String url = view.xmlTimestampUrlField().get().getText().trim();
        String hash = view.xmlTimestampHashCombo().get().getValue();
        if (path.isEmpty() || url.isEmpty()) {
            reporter.get().showError("RFC 3161 Timestamp", t("module.xml.feedback.tsaRequestRequired"));
            return;
        }
        if (!isHttpUrl(url)) {
            reporter.get().showError("TSA URL Error", t("module.xml.tsaUrlInvalid"));
            return;
        }
        try {
            byte[] data = Files.readAllBytes(new File(path).toPath());
            saveCustomTsa(url);
            reporter.get().updateStatus(t("module.xml.feedback.timestampRequesting"));
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    TsaDiagnostics.TokenResult result = TsaDiagnostics.timestamp(url, data, hash);
                    javafx.application.Platform.runLater(() -> {
                        lastTimestampToken = result.token();
                        TsaDiagnostics.Report report = result.report();
                        TsaDiagnostics.TokenInspection tokenInfo;
                        try {
                            tokenInfo = TsaDiagnostics.inspectToken(result.token());
                        } catch (Exception ignored) {
                            tokenInfo = null;
                        }
                        String text = "--- RFC 3161 Timestamp ---\nFile: " + path + "\nData bytes: " + data.length
                                + "\n" + hash + ": " + result.dataSha256() + "\nTSA: " + publishedTsaUrl(report.url()) + "\nHTTP: " + report.httpStatus()
                                + "\nLatency: " + report.latencyMs() + " ms\nPolicy: " + report.policyOid()
                                + "\nToken time: " + report.generationTime() + "\nToken bytes: " + report.responseBytes();
                        if (tokenInfo != null) {
                            text += "\nTSA certificate subject: " + tokenInfo.signerSubject()
                                    + "\nTSA certificate issuer: " + tokenInfo.signerIssuer()
                                    + "\nTSA certificate SHA-256: " + tokenInfo.signerSha256();
                        }
                        view.xmlTimestampReportArea().get().setText(text);
                        Map<String, String> details = new HashMap<>();
                        details.put("File", path); details.put("Hash", hash); details.put("Imprint", result.dataSha256()); details.put("TSA", publishedTsaUrl(url));
                        details.put("Token bytes", String.valueOf(result.token().length));
                        if (tokenInfo != null) details.put("TSA certificate SHA-256", tokenInfo.signerSha256());
                        reporter.get().publish(OperationResult.forOperation("RFC 3161 Timestamp")
                                .input(data).output(result.token()).details(details)
                                .status(t("module.xml.feedback.timestampReceived")).build());
                    });
                } catch (Exception e) {
                    javafx.application.Platform.runLater(() -> reporter.get().showError("RFC 3161 Timestamp", e.getMessage()));
                }
            });
        } catch (Exception e) { reporter.get().showError("RFC 3161 Timestamp",
                t("module.xml.operationFailed", "Timestamp request", e.getMessage())); }
    }

    void handleSaveTimestampToken() {
        if (lastTimestampToken == null) { reporter.get().showError("Save Timestamp", t("module.xml.feedback.timestampTokenRequired")); return; }
        FileChooser chooser = new FileChooser(); chooser.setTitle("Save RFC 3161 Timestamp Token"); chooser.setInitialFileName("timestamp.tsr");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Timestamp response", "*.tsr", "*.tst"));
        File file = chooser.showSaveDialog(null); if (file == null) return;
        try { Files.write(file.toPath(), lastTimestampToken); reporter.get().updateStatus(t("module.xml.feedback.timestampSaved", file.getName())); }
        catch (Exception e) { reporter.get().showError("Save Timestamp", t("module.xml.operationFailed", "Timestamp save", e.getMessage())); }
    }

    void handleBrowseTimestampToken() {
        File file = view.chooseFile().apply("Select RFC 3161 Timestamp Token");
        if (file != null) view.xmlTimestampTokenField().get().setText(file.getAbsolutePath());
    }

    void handleInspectTimestampToken() {
        String tokenPath = view.xmlTimestampTokenField().get().getText().trim();
        if (tokenPath.isEmpty()) { reporter.get().showError("Timestamp Token", t("module.xml.feedback.timestampFileRequired")); return; }
        try {
            byte[] token = Files.readAllBytes(new File(tokenPath).toPath());
            TsaDiagnostics.TokenInspection info = TsaDiagnostics.inspectToken(token);
            String text = "--- Saved RFC 3161 Token ---\nToken: " + tokenPath + "\nBytes: " + info.responseBytes()
                    + "\nPolicy: " + info.policyOid() + "\nImprint algorithm: " + info.imprintAlgorithmOid()
                    + "\nImprint: " + info.imprintHex() + "\nGeneration time: " + info.generationTime()
                    + "\nSerial: " + info.serialNumber() + "\nSigner: " + info.signerId()
                    + "\nCMS Algorithm: " + info.signatureAlgorithm()
                    + "\nTSA certificate subject: " + info.signerSubject() + "\nTSA certificate issuer: " + info.signerIssuer()
                    + "\nTSA certificate SHA-256: " + info.signerSha256()
                    + "\nTSA cert validity: " + info.certNotBefore() + " to " + info.certNotAfter()
                    + "\nTSA timeStamping EKU: " + (info.hasTimeStampingEku() ? "Present" : "Missing")
                    + "\n\n--- Embedded Certificate Chain ---\n" + info.certificateChainInfo();
            String dataPath = view.xmlTimestampFileField().get().getText().trim();
            if (!dataPath.isEmpty()) text += "\nMatches selected file: " + (TsaDiagnostics.tokenMatchesData(token, Files.readAllBytes(new File(dataPath).toPath())) ? "YES" : "NO");
            view.xmlTimestampReportArea().get().setText(text + "\n\nNote: imprint matching does not validate the TSA certificate chain.");
        } catch (Exception e) { reporter.get().showError("Timestamp Token",
                t("module.xml.operationFailed", "Timestamp inspection", e.getMessage())); }
    }

    void handleValidateTimestampToken() {
        String tokenPath = view.xmlTimestampTokenField().get().getText().trim();
        if (tokenPath.isEmpty()) { reporter.get().showError("Timestamp Token", t("module.xml.feedback.timestampFileRequired")); return; }
        TextField trustStoreField = view.xmlTimestampTrustStoreField().get();
        PasswordField trustStorePasswordField = view.xmlTimestampTrustStorePasswordField().get();
        String trustStorePath = trustStoreField != null ? trustStoreField.getText().trim() : "";
        String trustStorePassword = trustStorePasswordField != null ? trustStorePasswordField.getText() : "";
        String dataPath = view.xmlTimestampFileField().get().getText().trim();
        byte[] data = null;
        try {
            if (!dataPath.isEmpty()) data = Files.readAllBytes(new File(dataPath).toPath());
            byte[] token = Files.readAllBytes(new File(tokenPath).toPath());
            String report = TsaDiagnostics.validateToken(token, data, trustStorePath.isEmpty() ? null : trustStorePath, trustStorePassword);
            view.xmlTimestampReportArea().get().setText(report);
            reporter.get().updateStatus(t("module.xml.feedback.timestampValidated"));
        } catch (Exception e) {
            reporter.get().showError("Timestamp Token", t("module.xml.operationFailed", "Timestamp validation", e.getMessage()));
        }
    }

    String getTsaUrl() {
        String selected = view.xmlSignTsaUrlText().get().getEditor().getText().trim();
        if (selected.isEmpty() || NO_TSA.equals(selected)) {
            return null;
        }
        if (DIGICERT_TSA.equals(selected)) {
            return "http://timestamp.digicert.com";
        }
        if (FREETSA_TSA.equals(selected)) {
            return "https://freetsa.org/tsr";
        }
        return selected;
    }

    boolean isHttpUrl(String value) {
        try {
            java.net.URI uri = java.net.URI.create(value);
            return uri.getHost() != null && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isPresetTsa(String url) {
        return "http://timestamp.digicert.com".equals(url) || "https://freetsa.org/tsr".equals(url);
    }

    private boolean hasTsaUserInfo(String url) {
        try {
            return java.net.URI.create(url).getRawUserInfo() != null;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    void saveCustomTsa(String url) {
        if (url != null && !url.isBlank() && !isPresetTsa(url)) {
            if (hasTsaUserInfo(url) && reporter.get() != null) {
                reporter.get().showInfo("TSA", t("module.xml.tsaCredentialsNotSaved"));
            }
            AppSettings.getInstance().setCustomTsaUrl(url);
        }
    }

    /** Keep the complete endpoint for the active TSA request; redact only published copies. */
    String publishedTsaUrl(String url) {
        if (url == null || AppSettings.getInstance().getSecretVisibilityProfile() == SecretVisibilityProfile.FULL_LAB) {
            return url;
        }
        return TsaUrlSanitizer.withoutUserInfo(url);
    }

    void reloadTsaProfiles() {
        view.xmlSignTsaProfileCombo().get().getItems().setAll(AppSettings.getInstance().getTsaProfiles().stream()
                .map(AppSettings.TsaProfile::name)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList());
    }
}
