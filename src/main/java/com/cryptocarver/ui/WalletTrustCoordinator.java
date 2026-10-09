package com.cryptocarver.ui;

import com.cryptocarver.crypto.EidasCertificateInspector;
import com.cryptocarver.crypto.TrustedEntityListJsonInspector;
import com.cryptocarver.crypto.TrustedListInspector;
import com.cryptocarver.service.I18nService;
import javafx.scene.control.TextArea;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;

/** Coordinates eIDAS certificate inspection and Trusted List operations over the controller-owned FXML controls. */
final class WalletTrustCoordinator extends WalletCoordinatorBase {
    record View(Supplier<TextArea> eidasCertArea,
            Supplier<TextArea> eidasCertOutputArea,
            Supplier<TextArea> trustedListXmlArea,
            Supplier<TextArea> trustedListCertArea,
            Supplier<TextArea> trustedListOutputArea,
            Supplier<TextArea> trustedEntityListJsonArea,
            Supplier<TextArea> trustedEntityListSignerCertArea,
            Supplier<TextArea> trustedEntityListSearchCertArea) { }

    private final View view;

    WalletTrustCoordinator(View view, Supplier<StatusReporter> reporter) {
        super(reporter);
        this.view = view;
    }

    void handleEidasCertInspect() {
        try {
            String pem = textOf(view.eidasCertArea().get());
            if (isBlank(pem)) { showValidation(t("module.wallet.certificateRequired"), "eidasCertArea"); return; }
            String report = WalletPrivateMaterialPolicy.forDisplay(
                    EidasCertificateInspector.describe(parseCertificate(pem), I18nService.getInstance().getLocale()));
            view.eidasCertOutputArea().get().setText(report);
            updateStatus(t("module.wallet.status.inspected"));
            publish("eIDAS Certificate Inspect", report);
        } catch (Exception e) {
            fail(e, "eidasCertArea", "eidas certificate inspect");
        }
    }

    void handleTrustedListInspect() {
        try {
            byte[] xml = trustedListXml();
            if (xml == null) return;
            String report = WalletPrivateMaterialPolicy.forDisplay(
                    TrustedListInspector.describe(xml, I18nService.getInstance().getLocale()));
            view.trustedListOutputArea().get().setText(report);
            updateStatus(t("module.wallet.status.inspected"));
            publish("Trusted List Inspect", report);
        } catch (Exception e) {
            fail(e, "trustedListXmlArea", "trusted list inspect");
        }
    }

    void handleTrustedEntityListJsonInspect() {
        try {
            String json = textOf(view.trustedEntityListJsonArea().get());
            if (isBlank(json)) { showValidation(t("module.wallet.trustedEntityListRequired"), "trustedEntityListJsonArea"); return; }
            String signer = textOf(view.trustedEntityListSignerCertArea().get());
            String search = textOf(view.trustedEntityListSearchCertArea().get());
            String report = WalletPrivateMaterialPolicy.forDisplay(TrustedEntityListJsonInspector.describe(json.getBytes(StandardCharsets.UTF_8),
                    I18nService.getInstance().getLocale(), signer.isBlank() ? null : parseCertificate(signer),
                    search.isBlank() ? null : parseCertificate(search)), json);
            view.trustedListOutputArea().get().setText(report);
            updateStatus(t("module.wallet.status.inspected"));
            publish("Trusted Entity List JSON Inspect", report);
        } catch (Exception e) { fail(e, "trustedEntityListJsonArea", "trusted entity list JSON inspect"); }
    }

    void handleTrustedListVerify() {
        try {
            byte[] xml = trustedListXml();
            if (xml == null) return;
            TrustedListInspector.SignatureResult result = TrustedListInspector.verifySignature(xml);
            StringBuilder report = new StringBuilder();
            report.append("signature: ").append(result.signatureValid() ? "valid" : "INVALID").append('\n');
            if (result.signingCertificate() != null) {
                report.append("signed by: ")
                        .append(result.signingCertificate().getSubjectX500Principal()).append('\n');
            }
            report.append('\n').append(result.trustNote()).append('\n');
            String text = WalletPrivateMaterialPolicy.forDisplay(report.toString());
            view.trustedListOutputArea().get().setText(text);
            updateStatus(t("module.wallet.status.verified"));
            publish("Trusted List Verify", text);
        } catch (Exception e) {
            fail(e, "trustedListXmlArea", "trusted list verify");
        }
    }

    void handleTrustedListFind() {
        try {
            byte[] xml = trustedListXml();
            if (xml == null) return;
            String pem = textOf(view.trustedListCertArea().get());
            if (isBlank(pem)) { showValidation(t("module.wallet.certificateRequired"), "trustedListCertArea"); return; }

            List<TrustedListInspector.Match> matches = TrustedListInspector.findCertificate(
                    TrustedListInspector.parse(xml), parseCertificate(pem));

            StringBuilder report = new StringBuilder();
            if (matches.isEmpty()) {
                report.append(t("module.wallet.notInTrustedList")).append('\n');
            }
            for (TrustedListInspector.Match match : matches) {
                report.append(match.service().providerName())
                        .append(" / ").append(match.service().serviceName())
                        .append("\n  status   : ").append(match.service().statusLabel())
                        .append("\n  matched  : ").append(match.matchedBy()).append('\n');
                for (String qualifier : match.service().qualifiers()) {
                    report.append("  qualifier: ").append(qualifier).append('\n');
                }
            }
            String text = WalletPrivateMaterialPolicy.forDisplay(report.toString());
            view.trustedListOutputArea().get().setText(text);
            updateStatus(t("module.wallet.status.inspected"));
            publish("Trusted List Find Certificate", text,
                    "Matches", String.valueOf(matches.size()));
        } catch (Exception e) {
            fail(e, "trustedListCertArea", "trusted list find");
        }
    }

    private byte[] trustedListXml() {
        String xml = textOf(view.trustedListXmlArea().get());
        if (isBlank(xml)) {
            showValidation(t("module.wallet.trustedListRequired"), "trustedListXmlArea");
            return null;
        }
        return xml.getBytes(StandardCharsets.UTF_8);
    }
}
