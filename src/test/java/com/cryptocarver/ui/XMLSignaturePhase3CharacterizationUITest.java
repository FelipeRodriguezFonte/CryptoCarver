package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextInputControl;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampResponseGenerator;
import org.bouncycastle.tsp.TimeStampTokenGenerator;
import org.bouncycastle.util.CollectionStore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Security;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stable, UI-backed characterization of TSA endpoints, saved TSA profiles and local RFC 3161
 * tokens. No TSA is contacted and no dialog is opened: network handlers are only driven
 * through their validation paths.
 */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class XMLSignaturePhase3CharacterizationUITest extends EmvExtractionCharacterizationSupport {
    private static final String ENDPOINT = "https://tsa.invalid:8443/tsr?q=lab";

    @Test
    @SuppressWarnings("unchecked")
    void managesTsaEndpointsProfilesAndLocalTokens() throws Exception {
        Path data = tempDir.resolve("phase3-data.xml");
        Files.writeString(data, "<invoice><amount>81</amount></invoice>");
        Path other = tempDir.resolve("phase3-other.xml");
        Files.writeString(other, "<invoice><amount>82</amount></invoice>");
        Path tokenFile = tempDir.resolve("phase3-local.tsr");
        Files.write(tokenFile, localTimestampToken(Files.readAllBytes(data)));
        Path notAToken = tempDir.resolve("phase3-not-a-token.tsr");
        Files.write(notAToken, new byte[]{1, 2, 3});

        List<String> transcript = new ArrayList<>();
        onFx(() -> {
            shell.navigateTo("Sign XML (XAdES)");
            var controller = (XMLSignatureController) get(shell, "xmlSecurityContainerController");
            var reporter = new CharacterizationReporter(); controller.initModule(reporter);
            var tsaUrl = (ComboBox<String>) get(controller, "xmlSignTsaUrlText");
            var profileCombo = (ComboBox<String>) get(controller, "xmlSignTsaProfileCombo");
            var profileName = (TextInputControl) get(controller, "xmlSignTsaProfileNameField");

            tsaUrl.getEditor().setText("No TSA (XAdES-BASELINE-B)");
            controller.handleSaveTSA();
            controller.handleTestTSA();
            transcript.add("no-tsa " + reporter.drain());
            tsaUrl.getEditor().setText("not a url");
            controller.handleSaveTSA();
            controller.handleTestTSA();
            profileName.setText("Invented TSA");
            controller.handleSaveTSASavedProfile();
            transcript.add("invalid-url " + reporter.drain() + " custom-empty="
                    + AppSettings.getInstance().getCustomTsaUrl().isEmpty());

            tsaUrl.getEditor().setText("DigiCert — http://timestamp.digicert.com");
            controller.handleSaveTSA();
            transcript.add("preset " + reporter.drain() + " custom-empty="
                    + AppSettings.getInstance().getCustomTsaUrl().isEmpty());

            tsaUrl.getEditor().setText(ENDPOINT);
            controller.handleSaveTSA();
            transcript.add("custom " + reporter.drain() + " custom-saved="
                    + ENDPOINT.equals(AppSettings.getInstance().getCustomTsaUrl()));

            profileName.setText("   ");
            controller.handleSaveTSASavedProfile();
            transcript.add("profile-blank-name " + reporter.drain());
            profileName.setText("Invented TSA");
            controller.handleSaveTSASavedProfile();
            transcript.add("profile-saved " + reporter.drain() + " items=" + profileCombo.getItems()
                    + " selected=" + profileCombo.getValue() + " stored="
                    + AppSettings.getInstance().getTsaProfiles().stream()
                            .anyMatch(profile -> "Invented TSA".equals(profile.name()) && ENDPOINT.equals(profile.url())));

            tsaUrl.getEditor().setText("");
            profileName.clear();
            controller.handleLoadTSASavedProfile();
            transcript.add("profile-loaded " + reporter.drain() + " editor=" + ENDPOINT.equals(tsaUrl.getEditor().getText())
                    + " name=" + profileName.getText());
            profileCombo.setValue("Missing TSA");
            controller.handleLoadTSASavedProfile();
            transcript.add("profile-missing " + reporter.drain());
            profileCombo.setValue(null);
            controller.handleLoadTSASavedProfile();
            controller.handleDeleteTSASavedProfile();
            transcript.add("profile-none " + reporter.drain());
            profileCombo.setValue("Invented TSA");
            controller.handleDeleteTSASavedProfile();
            transcript.add("profile-deleted " + reporter.drain() + " items=" + profileCombo.getItems()
                    + " name-cleared=" + profileName.getText().isEmpty() + " stored="
                    + AppSettings.getInstance().getTsaProfiles().size());

            var timestampFile = (TextInputControl) get(controller, "xmlTimestampFileField");
            var timestampUrl = (TextInputControl) get(controller, "xmlTimestampUrlField");
            var tokenField = (TextInputControl) get(controller, "xmlTimestampTokenField");
            var report = (TextInputControl) get(controller, "xmlTimestampReportArea");
            timestampFile.clear(); timestampUrl.clear();
            controller.handleRequestTimestamp();
            timestampFile.setText(data.toString()); timestampUrl.setText("ftp://tsa.invalid/tsr");
            controller.handleRequestTimestamp();
            controller.handleSaveTimestampToken();
            transcript.add("request-validation " + reporter.drain());

            tokenField.clear();
            controller.handleInspectTimestampToken();
            controller.handleValidateTimestampToken();
            transcript.add("token-missing " + reporter.drain());

            tokenField.setText(tokenFile.toString());
            controller.handleInspectTimestampToken();
            String inspected = report.getText();
            transcript.add("token-inspected " + reporter.drain()
                    + " header=" + inspected.startsWith("--- Saved RFC 3161 Token ---")
                    + " policy=" + inspected.contains("Policy: 1.2.3.4.81")
                    + " subject=" + inspected.contains("TSA certificate subject: CN=Invented Local TSA")
                    + " eku=" + inspected.contains("TSA timeStamping EKU: Present")
                    + " match=" + inspected.contains("Matches selected file: YES")
                    + " note=" + inspected.endsWith("Note: imprint matching does not validate the TSA certificate chain."));
            timestampFile.setText(other.toString());
            controller.handleInspectTimestampToken();
            transcript.add("token-other-file " + reporter.drain()
                    + " match=" + report.getText().contains("Matches selected file: NO"));
            timestampFile.clear();
            controller.handleInspectTimestampToken();
            transcript.add("token-no-file " + reporter.drain()
                    + " match-line=" + report.getText().contains("Matches selected file:"));

            timestampFile.setText(data.toString());
            report.clear();
            controller.handleValidateTimestampToken();
            transcript.add("token-validated " + reporter.drain() + " report=" + !report.getText().isBlank());

            tokenField.setText(notAToken.toString());
            report.clear();
            controller.handleInspectTimestampToken();
            controller.handleValidateTimestampToken();
            transcript.add("token-invalid " + reporter.drain() + " report-empty=" + report.getText().isEmpty());
        });
        pinTranscript("xmlsig-phase3",
                "ab9156a4c477a23f0e26ea50c398739f569269c95b0bb040af09c9400211de0c", transcript);
    }

    private byte[] localTimestampToken(byte[] data) throws Exception {
        if (Security.getProvider("BC") == null) Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        KeyPair tsaKey = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        var name = new org.bouncycastle.asn1.x500.X500Name("CN=Invented Local TSA");
        var builder = new JcaX509v3CertificateBuilder(name, BigInteger.valueOf(81),
                new Date(System.currentTimeMillis() - 60_000), new Date(System.currentTimeMillis() + 3_600_000),
                name, tsaKey.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.extendedKeyUsage, true, new ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping));
        builder.addExtension(Extension.subjectKeyIdentifier, false,
                new JcaX509ExtensionUtils().createSubjectKeyIdentifier(tsaKey.getPublic()));
        X509CertificateHolder tsa = builder.build(
                new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(tsaKey.getPrivate()));
        byte[] imprint = MessageDigest.getInstance("SHA-256").digest(data);
        TimeStampRequestGenerator requests = new TimeStampRequestGenerator();
        requests.setCertReq(true);
        TimeStampRequest request = requests.generate(NISTObjectIdentifiers.id_sha256, imprint);
        DigestCalculatorProvider calculators = new JcaDigestCalculatorProviderBuilder().setProvider("BC").build();
        var contentSigner = new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(tsaKey.getPrivate());
        var signerInfo = new JcaSignerInfoGeneratorBuilder(calculators).build(contentSigner, tsa);
        var digestCalculator = calculators.get(new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256));
        var generator = new TimeStampTokenGenerator(signerInfo, digestCalculator, new ASN1ObjectIdentifier("1.2.3.4.81"));
        generator.addCertificates(new CollectionStore<>(List.of(tsa)));
        return new TimeStampResponseGenerator(generator, org.bouncycastle.tsp.TSPAlgorithms.ALLOWED)
                .generate(request, BigInteger.valueOf(81), new Date()).getEncoded();
    }

    /** Records only titles and localized status lines; exception texts never enter the transcript. */
    private final class CharacterizationReporter implements StatusReporter {
        private final List<String> events = new ArrayList<>();
        String drain() { String joined = String.join(",", events); events.clear(); return "[" + joined + "]"; }
        public void updateStatus(String message) { events.add("status:" + message); shell.updateStatus(message); }
        public void updateInspector(String operation, byte[] input, byte[] output, List<OperationDetail> details) { shell.updateInspector(operation, input, output, details); }
        public void showError(String title, String message) { events.add("error:" + title); }
        public void showError(UserFacingError error) { events.add("error:" + error.title()); }
        public void showInfo(String title, String message) { events.add("info:" + title); }
        public void publish(OperationResult value) { events.add("publish:" + value.getOperation()); shell.publish(value); }
    }
}
