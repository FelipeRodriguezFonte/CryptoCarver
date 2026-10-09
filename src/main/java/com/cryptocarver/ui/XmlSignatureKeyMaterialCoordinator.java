package com.cryptocarver.ui;

import com.cryptocarver.crypto.XMLSignatureOperations;
import com.cryptocarver.model.AppSettings;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.function.Function;
import java.util.function.Supplier;

/** Coordinates signing key discovery and trust store selection for the XML module. */
final class XmlSignatureKeyMaterialCoordinator {
    private static final Logger LOG = LoggerFactory.getLogger(XmlSignatureKeyMaterialCoordinator.class);

    record View(Supplier<RadioButton> xmlSignSourcePkcs11Radio,
            Supplier<TextField> xmlSignKeyPathField,
            Supplier<PasswordField> xmlSignKeyPasswordField,
            Supplier<ComboBox<String>> xmlSignKeyAliasCombo,
            Supplier<TextField> xmlVerifyTrustStorePathField,
            Supplier<PasswordField> xmlVerifyTrustStorePasswordField,
            Supplier<ComboBox<String>> xmlVerifyTrustStoreProfileCombo,
            Supplier<TextField> xmlTimestampTrustStoreField,
            Function<String, File> chooseFile) { }

    private final View view;
    private final Supplier<StatusReporter> reporter;

    XmlSignatureKeyMaterialCoordinator(View view, Supplier<StatusReporter> reporter) {
        this.view = view;
        this.reporter = reporter;
    }

    private String t(String key, Object... args) {
        return com.cryptocarver.service.I18nService.getInstance().text(key, args);
    }

    void handleBrowseXMLKey() {
        File file = view.chooseFile().apply("Select PKCS#12 KeyStore");
        if (file != null) {
            view.xmlSignKeyPathField().get().setText(file.getAbsolutePath());
        }
    }

    void handleLoadXMLKeys() {
        ComboBox<String> aliasCombo = view.xmlSignKeyAliasCombo().get();
        RadioButton pkcs11Radio = view.xmlSignSourcePkcs11Radio().get();
        if (pkcs11Radio != null && pkcs11Radio.isSelected()) {
            try {
                com.cryptocarver.crypto.hsm.Pkcs11Session session = com.cryptocarver.crypto.hsm.Pkcs11SessionManager.getInstance().requireSession();
                if (session == null) {
                    reporter.get().showError("Token Error", t("module.xml.feedback.keyStoreRequired"));
                    return;
                }
                java.util.List<String> aliases = session.listPrivateKeysWithCertificate();
                aliasCombo.getItems().setAll(aliases);
                if (!aliases.isEmpty()) {
                    aliasCombo.getSelectionModel().selectFirst();
                }
                reporter.get().updateStatus(t("module.xml.status.success") + " (" + aliases.size() + " aliases)");
            } catch (Exception e) {
                reporter.get().showError("PKCS#11 Error", t("module.xml.error.generic", e.getMessage()));
            }
            return;
        }

        try {
            String keyPath = view.xmlSignKeyPathField().get().getText();
            String password = view.xmlSignKeyPasswordField().get().getText();

            if (keyPath.isEmpty() || password.isEmpty()) {
                reporter.get().showError(t("module.xml.error.inputTitle"), t("module.xml.error.keyStorePassword"));
                return;
            }
            java.util.List<String> aliases = XMLSignatureOperations.getKeyAliases(keyPath, password);
            aliasCombo.getItems().setAll(aliases);

            if (!aliases.isEmpty()) {
                aliasCombo.getSelectionModel().select(0);
                reporter.get().updateStatus(t("module.xml.status.success") + " (" + aliases.size() + " keys)");
            } else {
                reporter.get().updateStatus(t("module.xml.feedback.aliasRequired"));
            }

        } catch (Exception e) {
            reporter.get().showError("Key Load Error", t("module.xml.operationFailed", "Key loading", e.getMessage()));
            LOG.error("Unable to load XAdES signing keys", e);
        }
    }

    void handleBrowseXMLTrustStore() {
        File file = view.chooseFile().apply("Select TrustStore (PKCS#12 or JKS)");
        if (file != null) {
            view.xmlVerifyTrustStorePathField().get().setText(file.getAbsolutePath());
        }
    }

    void handleBrowseTimestampTrustStore() {
        File file = view.chooseFile().apply("Select TrustStore (PKCS#12 or JKS)");
        if (file != null) {
            TextField field = view.xmlTimestampTrustStoreField().get();
            if (field != null) field.setText(file.getAbsolutePath());
        }
    }

    void handleLoadXMLTrustStoreProfile() {
        String name = view.xmlVerifyTrustStoreProfileCombo().get().getValue();
        if (name == null || name.isBlank()) return;
        AppSettings.getInstance().getTrustStoreProfiles().stream().filter(profile -> name.equals(profile.name())).findFirst()
                .ifPresent(profile -> {
                    view.xmlVerifyTrustStorePathField().get().setText(profile.path());
                    view.xmlVerifyTrustStorePasswordField().get().clear();
                    reporter.get().updateStatus(t("module.xml.feedback.trustStoreLoaded"));
                });
    }
}
