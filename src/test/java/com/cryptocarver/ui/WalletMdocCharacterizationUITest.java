package com.cryptocarver.ui;

import com.cryptocarver.crypto.*;
import com.cryptocarver.model.*;
import com.cryptocarver.service.I18nService;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.*;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class WalletMdocCharacterizationUITest extends WalletExtractionCharacterizationSupport {
    private static final String GOLD = "94eb31450c66aaac07a4fecb1b07c8b181fa32b274b1c511ec98afe93f966ffb";
    @Test void mdocIssueVerifyInspectAndValidation() throws Exception {
        withWallet(() -> {
            shell.navigateTo("mdoc / mDL");
            replaceReporter();
            var issuer = new ECKeyGenerator(Curve.P_256).generate();
            var device = new ECKeyGenerator(Curve.P_256).generate();
            Instant now = Instant.now();
            var name = new X500Name("CN=Invented Wallet 79 Characterization");
            var certificate = new JcaX509CertificateConverter().getCertificate(
                    new JcaX509v3CertificateBuilder(name, BigInteger.valueOf(79), Date.from(now.minusSeconds(60)),
                            Date.from(now.plusSeconds(86400)), name, issuer.toECPublicKey())
                            .build(new JcaContentSignerBuilder("SHA256withECDSA").build(issuer.toECPrivateKey())));
            List<String> transcript = new ArrayList<>();
            assertEquals(List.of("SHA-256", "SHA-384", "SHA-512"), combo("mdocDigestCombo").getItems());
            assertEquals(MdocOperations.MDL_DOCTYPE, text("mdocDocTypeField"));
            for (LanguagePreference language : List.of(LanguagePreference.EN, LanguagePreference.ES)) {
                I18nService.getInstance().setPreference(language);
                for (SecretVisibilityProfile profile : SecretVisibilityProfile.values()) {
                    AppSettings.getInstance().setSecretVisibilityProfile(profile);
                    transcript.add(language + "|" + profile);
                    put("mdocClaimsArea", ""); validation("handleMdocIssue", "mdocClaimsArea", transcript);
                    put("mdocClaimsArea", "{\"org.iso.18013.5.1\":{\"document_number\":\"INVENTED-79\",\"lab_value\":\"synthetic\"}}");
                    put("mdocIssuerKeyArea", ""); validation("handleMdocIssue", "mdocIssuerKeyArea", transcript);
                    put("mdocIssuerKeyArea", pem("PRIVATE KEY", issuer.toECPrivateKey().getEncoded()));
                    put("mdocSignerCertArea", ""); validation("handleMdocIssue", "mdocSignerCertArea", transcript);
                    put("mdocSignerCertArea", pem("CERTIFICATE", certificate.getEncoded()));
                    put("mdocDeviceKeyArea", pem("PUBLIC KEY", device.toECPublicKey().getEncoded()));
                    put("mdocValidityField", "not-an-integer"); combo("mdocDigestCombo").setValue("SHA-384");
                    valid("handleMdocIssue", "mdocIssueOutputArea", transcript);
                    String hex = text("mdocIssueOutputArea"); assertTrue(hex.matches("[0-9A-F]+")); assertEquals(0, hex.length() % 2);
                    byte[] document = HexFormat.of().parseHex(hex);
                    var parsed = MdocOperations.parse(document);
                    assertEquals(1, parsed.namespaces().size()); assertEquals(2, parsed.namespaces().get(MdocOperations.MDL_NAMESPACE).size());
                    var independent = MdocOperations.verify(document, issuer.toECPublicKey(), Instant.now());
                    assertTrue(independent.issuerSignatureValid());
                    assertEquals("SHA-384", independent.mso().digestAlgorithm()); assertNotNull(independent.mso().deviceKeyInfo());
                    assertTrue(independent.findings().stream().noneMatch(f -> f.severity().equals("ERROR")));
                    transcript.add("issue|uppercase-even-hex;namespaces=1;items=2;digest=SHA-384;device=present;issuer-signature=valid");
                    replaceReporter();
                    put("mdocVerifyInputArea", hex); put("mdocVerifyIssuerKeyArea", "");
                    validation("handleMdocVerify", "mdocVerifyIssuerKeyArea", transcript);
                    put("mdocVerifyIssuerKeyArea", pem("PUBLIC KEY", issuer.toECPublicKey().getEncoded()));
                    valid("handleMdocVerify", "mdocVerifyOutputArea", transcript);
                    assertEquals(MdocOperations.describe(document, issuer.toECPublicKey(), Instant.now(), I18nService.getInstance().getLocale()), text("mdocVerifyOutputArea"));
                    transcript.add("verify|" + normalizedReport(text("mdocVerifyOutputArea")));
                    put("mdocVerifyIssuerKeyArea", "");
                    valid("handleMdocInspect", "mdocVerifyOutputArea", transcript);
                    assertTrue(text("mdocVerifyOutputArea").contains("INVENTED-79"));
                    transcript.add("inspect|" + normalizedReport(text("mdocVerifyOutputArea")));
                    put("mdocVerifyInputArea", ""); validation("handleMdocInspect", "mdocVerifyInputArea", transcript);
                    put("mdocVerifyInputArea", "not-hex"); invalidStructure("handleMdocInspect", "mdocVerifyInputArea", transcript);
                }
            }
            pinTranscript("wallet-2", GOLD, transcript);
        });
    }
    private static String normalizedReport(String report) {
        String normalized = report.replaceAll("(?m)^(  (?:validity|validez)\\s*:) .*$", "$1 <VALIDITY>")
                .replaceAll("(?m)^(    )\\[\\d+\\]", "$1[<ID>]");
        List<String> lines = Arrays.asList(normalized.split("\\R", -1));
        var items = lines.stream().filter(line -> line.startsWith("    [")).sorted().iterator();
        List<String> ordered = new ArrayList<>();
        for (String line : lines) ordered.add(line.startsWith("    [") ? items.next() : line);
        return String.join("\n", ordered);
    }
}
