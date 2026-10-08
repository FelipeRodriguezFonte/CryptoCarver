package com.cryptocarver.ui;

import com.cryptocarver.model.*;
import com.cryptocarver.utils.OperationHistory;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import javafx.scene.control.*;
import javafx.scene.Node;
import javafx.scene.Parent;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.*;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.math.BigInteger;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Phase zero: required privacy contract on production FXML and the live shell, before extraction. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class WalletPrivacyCharacterizationUITest extends EmvExtractionCharacterizationSupport {
    @ParameterizedTest
    @EnumSource(SecretVisibilityProfile.class)
    void signingKeysStayOutOfAllTenOperationResults(SecretVisibilityProfile profile) throws Exception {
        isolatedLegacy(() -> {
            AppSettings.getInstance().setSecretVisibilityProfile(profile);
            shell.navigateTo("SD-JWT VC");
            WalletController wallet = (WalletController) get(shell, "walletController");
            var issuer = new ECKeyGenerator(Curve.P_256).generate();
            var holder = new ECKeyGenerator(Curve.P_256).generate();
            var device = new ECKeyGenerator(Curve.P_256).generate();
            String issuerPem = pem("PRIVATE KEY", issuer.toECPrivateKey().getEncoded());
            String holderPem = pem("PRIVATE KEY", holder.toECPrivateKey().getEncoded());
            List<String> secrets = List.of(issuer.getD().toString(), holder.getD().toString(), device.getD().toString(),
                    Base64.getEncoder().encodeToString(issuer.toECPrivateKey().getEncoded()),
                    Base64.getEncoder().encodeToString(holder.toECPrivateKey().getEncoded()));
            List<String> violations = new ArrayList<>();
            put(wallet, "sdJwtIssuerKeyArea", issuerPem);
            put(wallet, "sdJwtClaimsArea", "{\"sub\":\"invented-wallet-79\",\"lab_claim\":\"synthetic\"}");
            put(wallet, "sdJwtDisclosableArea", "lab_claim");
            run(wallet, "handleSdJwtIssue", "sdJwtIssueOutputArea", profile, secrets, violations);
            put(wallet, "sdJwtPresentInputArea", text(wallet, "sdJwtIssueOutputArea"));
            put(wallet, "sdJwtHolderKeyArea", holderPem);
            put(wallet, "sdJwtAudienceField", "urn:invented:wallet79");
            put(wallet, "sdJwtNonceField", "invented-nonce-79");
            run(wallet, "handleSdJwtPresent", "sdJwtPresentOutputArea", profile, secrets, violations);
            put(wallet, "sdJwtVerifyInputArea", text(wallet, "sdJwtPresentOutputArea"));
            put(wallet, "sdJwtVerifyIssuerKeyArea", pem("PUBLIC KEY", issuer.toECPublicKey().getEncoded()));
            put(wallet, "sdJwtVerifyHolderKeyArea", pem("PUBLIC KEY", holder.toECPublicKey().getEncoded()));
            put(wallet, "sdJwtVerifyAudienceField", "urn:invented:wallet79");
            put(wallet, "sdJwtVerifyNonceField", "invented-nonce-79");
            run(wallet, "handleSdJwtVerify", "sdJwtVerifyOutputArea", profile, secrets, violations);
            assertTrue(text(wallet, "sdJwtVerifyOutputArea").contains("synthetic"));
            put(wallet, "sdJwtInspectInputArea", text(wallet, "sdJwtPresentOutputArea"));
            run(wallet, "handleSdJwtInspect", "sdJwtInspectOutputArea", profile, secrets, violations);

            shell.navigateTo("mdoc / mDL");
            wallet.showSection("mdoc");
            put(wallet, "mdocIssuerKeyArea", issuerPem);
            put(wallet, "mdocDeviceKeyArea", pem("PUBLIC KEY", device.toECPublicKey().getEncoded()));
            var name = new X500Name("CN=Invented Wallet 79 Lab");
            Instant now = Instant.now();
            var certificate = new JcaX509CertificateConverter().getCertificate(
                    new JcaX509v3CertificateBuilder(name, BigInteger.valueOf(79),
                            Date.from(now.minusSeconds(60)), Date.from(now.plusSeconds(86400)), name, issuer.toECPublicKey())
                    .build(new JcaContentSignerBuilder("SHA256withECDSA").build(issuer.toECPrivateKey())));
            put(wallet, "mdocSignerCertArea", pem("CERTIFICATE", certificate.getEncoded()));
            put(wallet, "mdocClaimsArea", "{\"org.iso.18013.5.1\":{\"document_number\":\"INVENTED-79\"}}");
            run(wallet, "handleMdocIssue", "mdocIssueOutputArea", profile, secrets, violations);
            put(wallet, "mdocVerifyInputArea", text(wallet, "mdocIssueOutputArea"));
            put(wallet, "mdocVerifyIssuerKeyArea", pem("PUBLIC KEY", issuer.toECPublicKey().getEncoded()));
            run(wallet, "handleMdocVerify", "mdocVerifyOutputArea", profile, secrets, violations);
            put(wallet, "mdocVerifyIssuerKeyArea", "");
            run(wallet, "handleMdocInspect", "mdocVerifyOutputArea", profile, secrets, violations);

            shell.navigateTo("Status List");
            wallet.showSection("Status List");
            put(wallet, "statusListKeyArea", issuerPem);
            put(wallet, "statusListStatusesArea", "0,1,0,1");
            put(wallet, "statusListUriField", "https://issuer.lab.invalid/status/79");
            run(wallet, "handleStatusListIssue", "statusListOutputArea", profile, secrets, violations);
            put(wallet, "statusListTokenArea", text(wallet, "statusListOutputArea"));
            put(wallet, "statusListIndexField", "1");
            put(wallet, "statusListVerifyKeyArea", pem("PUBLIC KEY", issuer.toECPublicKey().getEncoded()));
            run(wallet, "handleStatusListResolve", "statusListResolveOutputArea", profile, secrets, violations);
            assertTrue(text(wallet, "statusListResolveOutputArea").startsWith("index 1 -> 1"));
            run(wallet, "handleStatusListDescribe", "statusListResolveOutputArea", profile, secrets, violations);
            assertTrue(violations.isEmpty(), String.join("\n", violations));
        });
    }

    @ParameterizedTest
    @EnumSource(SecretVisibilityProfile.class)
    void privateHolderJwkInVerifiedClaimsMustNotReachResultSurfaces(SecretVisibilityProfile profile) throws Exception {
        nestedPrivateJwk(profile, false);
    }

    @ParameterizedTest
    @EnumSource(SecretVisibilityProfile.class)
    void privateHolderJwkInInspectedClaimsMustNotReachResultSurfaces(SecretVisibilityProfile profile) throws Exception {
        nestedPrivateJwk(profile, true);
    }

    private void nestedPrivateJwk(SecretVisibilityProfile profile, boolean inspect) throws Exception {
        isolatedLegacy(() -> {
            AppSettings.getInstance().setSecretVisibilityProfile(profile);
            shell.navigateTo("SD-JWT VC");
            WalletController wallet = (WalletController) get(shell, "walletController");
            var issuer = new ECKeyGenerator(Curve.P_256).generate();
            var holder = new ECKeyGenerator(Curve.P_256).generate();
            // A valid signed credential carrying an accidentally private holder JWK.
            // This is key material, not arbitrary personal text or the editor used to paste a signing key.
            put(wallet, "sdJwtIssuerKeyArea", pem("PRIVATE KEY", issuer.toECPrivateKey().getEncoded()));
            put(wallet, "sdJwtClaimsArea", "{\"sub\":\"invented-wallet-79\",\"cnf\":{\"jwk\":" + holder.toJSONString() + "}}");
            invoke(wallet, "handleSdJwtIssue");
            String issued = text(wallet, "sdJwtIssueOutputArea");
            assertFalse(issued.isBlank());
            String output = inspect ? "sdJwtInspectOutputArea" : "sdJwtVerifyOutputArea";
            put(wallet, inspect ? "sdJwtInspectInputArea" : "sdJwtVerifyInputArea", issued);
            put(wallet, "sdJwtVerifyIssuerKeyArea", pem("PUBLIC KEY", issuer.toECPublicKey().getEncoded()));
            List<String> violations = new ArrayList<>();
            run(wallet, inspect ? "handleSdJwtInspect" : "handleSdJwtVerify", output, profile,
                    List.of(holder.getD().toString()), violations);
            if (profile == SecretVisibilityProfile.FULL_LAB) {
                assertTrue(text(wallet, output).contains(holder.getD().toString()), "FULL_LAB original report");
            }
            assertTrue(violations.isEmpty(), String.join("\n", violations));
        });
    }

    private void run(WalletController wallet, String handler, String output, SecretVisibilityProfile profile,
                     List<String> secrets, List<String> violations) throws Exception {
        int count = shell.getHistoryManager().getHistoryItems().size();
        TextArea area = (TextArea) get(wallet, output);
        Parent container = (Parent) get(wallet, "walletContainer");
        for (Node node : container.lookupAll(".accordion")) {
            Accordion accordion = (Accordion) node;
            for (TitledPane pane : accordion.getPanes()) if (contains(pane.getContent(), area)) {
                pane.setAnimated(false);
                accordion.setExpandedPane(pane);
            }
        }
        root.applyCss(); root.layout();
        invoke(wallet, handler);
        assertFalse(area.getText().isBlank(), handler + " must succeed, not merely exercise validation");
        assertEquals(count + 1, shell.getHistoryManager().getHistoryItems().size(), handler + " must publish");
        area.requestFocus();
        if (profile != SecretVisibilityProfile.FULL_LAB) {
            for (String secret : secrets) if (area.getText().contains(secret)) {
                assertTrue(area.isVisible() && area.getScene() != null, "output must be attached to the shell");
                violations.add(profile + " " + handler + " fx:id=" + output + " exposes private key");
            }
        }
        inspectSurfaces(profile, handler, secrets, shell, root, new ArrayList<>(), violations);
        if (profile != SecretVisibilityProfile.FULL_LAB && Files.exists(tempDir.resolve("history.json"))) {
            String persisted = Files.readString(tempDir.resolve("history.json"));
            for (String secret : secrets) if (persisted.contains(secret)) violations.add(profile + " " + handler + " history.json exposes private key");
        }
    }

    private void isolatedLegacy(FxAction action) throws Exception {
        onFx(() -> {
            OperationHistory legacy = OperationHistory.getInstance();
            List<OperationHistory.OperationEntry> previous = legacy.getHistory();
            Path previousPath = (Path) get(legacy, "historyFilePath");
            @SuppressWarnings("unchecked") List<OperationHistory.OperationEntry> entries =
                    (List<OperationHistory.OperationEntry>) get(legacy, "history");
            try {
                set(legacy, "historyFilePath", tempDir.resolve("legacy-history.json"));
                entries.clear();
                action.run();
            } finally {
                entries.clear(); entries.addAll(previous);
                set(legacy, "historyFilePath", previousPath);
            }
        });
    }

    private static boolean contains(Node node, Node target) {
        if (node == target) return true;
        return node instanceof Parent parent && parent.getChildrenUnmodifiable().stream().anyMatch(child -> contains(child, target));
    }
    private static void invoke(WalletController wallet, String name) throws Exception {
        var method = WalletController.class.getDeclaredMethod(name);
        method.setAccessible(true); method.invoke(wallet);
    }
    private static void put(WalletController wallet, String name, String value) throws Exception {
        ((TextInputControl) get(wallet, name)).setText(value);
    }
    private static String text(WalletController wallet, String name) throws Exception {
        return ((TextInputControl) get(wallet, name)).getText();
    }
    private static String pem(String type, byte[] bytes) {
        return "-----BEGIN " + type + "-----\n" + Base64.getEncoder().encodeToString(bytes) + "\n-----END " + type + "-----\n";
    }
}
