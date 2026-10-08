package com.cryptocarver.ui;

import com.cryptocarver.crypto.JOSEService;
import com.cryptocarver.crypto.MdocOperations;
import com.cryptocarver.crypto.StatusListOperations;
import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.utils.OperationHistory;
import com.nimbusds.jose.*;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.*;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigInteger;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Additional phase-zero audit: only synthetic keys, production controls and local signed inputs. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class WalletAdditionalPrivacyCharacterizationUITest extends EmvExtractionCharacterizationSupport {
    static Stream<Arguments> privateInputs() {
        return Arrays.stream(SecretVisibilityProfile.values()).flatMap(profile ->
                Stream.of("mdoc", "status-list").flatMap(operation ->
                        Stream.of("jwk", "pem").map(kind -> Arguments.of(profile, operation, kind))));
    }

    @ParameterizedTest(name = "{0} {1} private {2}")
    @MethodSource("privateInputs")
    void privateMaterialMustNotReachInspectOrDescribeResults(
            SecretVisibilityProfile profile, String operation, String kind) throws Exception {
        onFx(() -> {
            OperationHistory legacy = OperationHistory.getInstance();
            List<OperationHistory.OperationEntry> previous = legacy.getHistory();
            Path previousPath = (Path) get(legacy, "historyFilePath");
            @SuppressWarnings("unchecked") List<OperationHistory.OperationEntry> entries =
                    (List<OperationHistory.OperationEntry>) get(legacy, "history");
            try {
                set(legacy, "historyFilePath", tempDir.resolve("legacy-history.json"));
                entries.clear();
                AppSettings.getInstance().setSecretVisibilityProfile(profile);
                shell.navigateTo(operation.equals("mdoc") ? "mdoc / mDL" : "Status List");
                WalletController wallet = (WalletController) get(shell, "walletController");
                var issuer = new ECKeyGenerator(Curve.P_256).generate();
                var privateFixture = new ECKeyGenerator(Curve.P_256).generate();
                String privatePem = pem("PRIVATE KEY", privateFixture.toECPrivateKey().getEncoded());
                Object privateValue = kind.equals("jwk") ? privateFixture.toJSONObject() : privatePem;
                String marker = kind.equals("jwk") ? privateFixture.getD().toString()
                        : Base64.getEncoder().encodeToString(privateFixture.toECPrivateKey().getEncoded());
                String handler;
                String output;
                if (operation.equals("mdoc")) {
                    Instant now = Instant.now();
                    var name = new X500Name("CN=Invented Wallet 79 Additional Audit");
                    var cert = new JcaX509CertificateConverter().getCertificate(
                            new JcaX509v3CertificateBuilder(name, BigInteger.valueOf(79),
                                    Date.from(now.minusSeconds(60)), Date.from(now.plusSeconds(86400)), name, issuer.toECPublicKey())
                            .build(new JcaContentSignerBuilder("SHA256withECDSA").build(issuer.toECPrivateKey())));
                    String claims = new com.google.gson.Gson().toJson(Map.of(MdocOperations.MDL_NAMESPACE,
                            Map.of("lab_private_fixture", privateValue)));
                    byte[] document = MdocOperations.issue(MdocOperations.MDL_DOCTYPE, claims, "SHA-256",
                            new MdocOperations.ValidityInfo(now, now, now.plusSeconds(86400), null),
                            issuer.toECPrivateKey(), cert, null);
                    put(wallet, "mdocVerifyInputArea", HexFormat.of().formatHex(document));
                    put(wallet, "mdocVerifyIssuerKeyArea", "");
                    handler = "handleMdocInspect";
                    output = "mdocVerifyOutputArea";
                } else {
                    String signerPem = pem("PRIVATE KEY", issuer.toECPrivateKey().getEncoded());
                    String base = StatusListOperations.issueStatusListToken(new int[] {0, 1, 0, 1}, 1,
                            "https://issuer.lab.invalid/status/79", Instant.now(), null, -1,
                            JWSAlgorithm.ES256, JOSEService.createSigner(JWSAlgorithm.ES256, signerPem));
                    JWSObject original = JWSObject.parse(base);
                    // A private JWK/PEM accidentally attached as a signed custom header member.
                    JWSHeader header = new JWSHeader.Builder(original.getHeader().getAlgorithm())
                            .type(original.getHeader().getType())
                            .customParam("lab_private_fixture", privateValue).build();
                    JWSObject token = new JWSObject(header, original.getPayload());
                    token.sign(JOSEService.createSigner(JWSAlgorithm.ES256, signerPem));
                    assertTrue(token.verify(JOSEService.createVerifier(JWSAlgorithm.ES256,
                            pem("PUBLIC KEY", issuer.toECPublicKey().getEncoded()))));
                    put(wallet, "statusListTokenArea", token.serialize());
                    handler = "handleStatusListDescribe";
                    output = "statusListResolveOutputArea";
                }
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
                var method = WalletController.class.getDeclaredMethod(handler);
                method.setAccessible(true); method.invoke(wallet);
                assertFalse(area.getText().isBlank(), "operation must produce a report");
                assertEquals(1, shell.getHistoryManager().getHistoryItems().size(), "operation must publish exactly once");
                assertTrue(area.getScene() != null && area.isVisible(), "real expanded output must be attached");
                area.requestFocus();
                List<String> violations = new ArrayList<>();
                if (profile == SecretVisibilityProfile.FULL_LAB) {
                    assertTrue(area.getText().contains(marker), "FULL_LAB retains original report");
                } else if (area.getText().contains(marker)) {
                    violations.add(profile + " " + handler + " fx:id=" + output + " exposes private " + kind);
                }
                inspectSurfaces(profile, handler, List.of(marker), shell, root, new ArrayList<>(), violations);
                if (profile != SecretVisibilityProfile.FULL_LAB) {
                    String persisted = Files.readString(tempDir.resolve("history.json"));
                    if (persisted.contains(marker)) violations.add(profile + " " + handler + " history.json exposes private " + kind);
                }
                assertTrue(violations.isEmpty(), String.join("\n", violations));
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
    private static void put(WalletController wallet, String name, String value) throws Exception {
        ((TextInputControl) get(wallet, name)).setText(value);
    }
    private static String pem(String type, byte[] bytes) {
        return "-----BEGIN " + type + "-----\n" + Base64.getEncoder().encodeToString(bytes) + "\n-----END " + type + "-----\n";
    }
}
