package com.cryptocarver.model.process;

import com.cryptocarver.crypto.*;
import com.cryptocarver.model.process.handlers.EnvelopeSignatureNodeHandler;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyStore;
import java.util.Base64;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class EnvelopeSignatureNodeHandlerTest {
    private final EnvelopeSignatureNodeHandler handler = new EnvelopeSignatureNodeHandler();

    @Test void cmsEnvelopeGraphIsSelfVerifyingAndSecretFree() throws Exception {
        Path fixture = Path.of("src/test/resources/process/ola5b3b_cms_envelope_roundtrip.cfprocess.json");
        String json = Files.readString(fixture);
        assertFalse(json.contains("storepass"));
        ProcessDefinition process = ProcessDefinitionCodec.deserialize(json);
        process.nodes.stream().filter(n -> "CMS_DEVELOPE".equals(n.type)).forEach(n -> n.configuration.put("keystorePassword", "storepass"));
        assertEquals("CMS envelope payload", new String(ProcessEngine.execute(process).get("assert").bytes(), StandardCharsets.UTF_8));
    }

    @Test void cmsCadesXmlAndCertificateNodesUseTheirFacades() throws Exception {
        byte[] payload = "signed CMS payload".getBytes(StandardCharsets.UTF_8);
        ProcessDefinition.Node cmsSign = storeNode("CMS_SIGN");
        byte[] cms = handler.execute(cmsSign, Map.of("payload", FlowValue.binary(payload)), null).bytes();
        ProcessDefinition.Node cmsVerify = trustNode("CMS_VERIFY");
        assertArrayEquals(payload, handler.execute(cmsVerify, Map.of("message", FlowValue.binary(cms)), null).bytes());

        byte[] cades = handler.execute(storeNode("CADES_BES_SIGN"), Map.of("payload", FlowValue.binary(payload)), null).bytes();
        assertTrue(CMSOperations.inspectCadesProfile(cades).certificateBindingPresent());

        String xml = "<root><value>authenticated</value></root>";
        ProcessDefinition.Node xmlSign = storeNode("XMLDSIG_SIGN");
        xmlSign.configuration.put("packaging", "ENVELOPED");
        String signedXml = handler.execute(xmlSign, Map.of("payload", FlowValue.text(xml, StandardCharsets.UTF_8)), null).render();
        ProcessDefinition.Node xmlVerify = trustNode("XMLDSIG_VERIFY");
        String verifiedXml = handler.execute(xmlVerify, Map.of("message", FlowValue.text(signedXml, StandardCharsets.UTF_8)), null).render();
        assertTrue(verifiedXml.contains("authenticated"));

        ProcessDefinition.Node parsed = node("CERT_PARSE");
        assertTrue(handler.execute(parsed, Map.of("certificate", FlowValue.binary(Files.readAllBytes(Path.of("src/test/resources/testcert.pem")))), null).render().contains("X.509"));
        ProcessDefinition.Node trusted = trustNode("CERT_VALIDATE");
        assertTrue(handler.execute(trusted, Map.of("certificate", FlowValue.binary(Files.readAllBytes(Path.of("src/test/resources/testcert.pem")))), null).render().contains("X.509"));
    }

    @Test void padesSignAndVerifyReturnTheVerifiedPdf() throws Exception {
        byte[] pdf;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage()); document.save(output); pdf = output.toByteArray();
        }
        byte[] signed = handler.execute(storeNode("PADES_SIGN"), Map.of("payload", FlowValue.binary(pdf)), null).bytes();
        assertArrayEquals(signed, handler.execute(trustNode("PADES_VERIFY"), Map.of("message", FlowValue.binary(signed)), null).bytes());
    }

    @Test void pqcKeypairNodeEmitsImportablePrivateEncoding() throws Exception {
        ProcessDefinition.Node node = node("PQC_KEYPAIR_GENERATE", "algorithm", "ML-DSA-44");
        byte[] encoded = handler.execute(node, Map.of(), null).bytes();
        assertEquals("ML-DSA-44", PostQuantumOperations.detectAlgorithmFromEncoded(encoded, false).nistName());
    }

    @Test void openPgpNodesReturnVerifiedPayloadAndRejectTampering() throws Exception {
        char[] passphrase = "test-passphrase".toCharArray();
        OpenPgpOperations.KeyPairMaterial keys = OpenPgpOperations.generateRsaKeyPair("Process Test <test@example.invalid>", passphrase);
        byte[] payload = "OpenPGP payload".getBytes(StandardCharsets.UTF_8);
        ProcessDefinition.Node sign = node("OPENPGP_SIGN", "privateKey", keys.secretKeyArmored(), "passphrase", new String(passphrase));
        String signed = handler.execute(sign, Map.of("payload", FlowValue.binary(payload)), null).render();
        assertEquals(OpenPgpOperations.signAttached(payload, keys.secretKeyArmored(), passphrase).substring(0, 27), signed.substring(0, 27));
        ProcessDefinition.Node verify = node("OPENPGP_VERIFY", "publicKey", keys.publicKeyArmored());
        assertArrayEquals(payload, handler.execute(verify, Map.of("message", FlowValue.text(signed, StandardCharsets.UTF_8)), null).bytes());
        int body = signed.indexOf("\n\n") + 2;
        String tampered = signed.substring(0, body) + (signed.charAt(body) == 'A' ? 'B' : 'A') + signed.substring(body + 1);
        assertThrows(Exception.class, () -> handler.execute(verify, Map.of("message", FlowValue.text(tampered, StandardCharsets.UTF_8)), null));

        ProcessDefinition.Node encrypt = node("OPENPGP_ENCRYPT", "publicKey", keys.publicKeyArmored());
        String encrypted = handler.execute(encrypt, Map.of("payload", FlowValue.binary(payload)), null).render();
        ProcessDefinition.Node decrypt = node("OPENPGP_DECRYPT", "privateKey", keys.secretKeyArmored(), "passphrase", new String(passphrase));
        assertArrayEquals(payload, handler.execute(decrypt, Map.of("message", FlowValue.text(encrypted, StandardCharsets.UTF_8)), null).bytes());
    }

    @Test void pqcNodesMatchFacadeForSignatureAndKem() throws Exception {
        byte[] payload = "PQC payload".getBytes(StandardCharsets.UTF_8);
        KeyPair signaturePair = PostQuantumOperations.generateKeyPair("ML-DSA-44");
        ProcessDefinition.Node sign = node("PQC_SIGN", "algorithm", "ML-DSA-44", "privateKey", Base64.getEncoder().encodeToString(signaturePair.getPrivate().getEncoded()));
        byte[] signature = handler.execute(sign, Map.of("payload", FlowValue.binary(payload)), null).bytes();
        ProcessDefinition.Node verify = node("PQC_VERIFY", "algorithm", "ML-DSA-44", "publicKey", Base64.getEncoder().encodeToString(signaturePair.getPublic().getEncoded()));
        assertArrayEquals(payload, handler.execute(verify, Map.of("payload", FlowValue.binary(payload), "signature", FlowValue.binary(signature)), null).bytes());
        signature[0] ^= 1;
        assertThrows(Exception.class, () -> handler.execute(verify, Map.of("payload", FlowValue.binary(payload), "signature", FlowValue.binary(signature)), null));

        KeyPair kemPair = PostQuantumOperations.generateKeyPair("ML-KEM-512");
        PostQuantumOperations.KEMResult direct = PostQuantumOperations.encapsulate(kemPair.getPublic(), "ML-KEM-512");
        assertArrayEquals(direct.sharedSecret(), PostQuantumOperations.decapsulate(kemPair.getPrivate(), direct.encapsulation(), "ML-KEM-512"));
        ProcessDefinition.Node encapsulate = node("PQC_KEM_ENCAPSULATE", "algorithm", "ML-KEM-512", "publicKey", Base64.getEncoder().encodeToString(kemPair.getPublic().getEncoded()));
        byte[] encapsulation = handler.execute(encapsulate, Map.of(), null).bytes();
        ProcessDefinition.Node decapsulate = node("PQC_KEM_DECAPSULATE", "algorithm", "ML-KEM-512", "privateKey", Base64.getEncoder().encodeToString(kemPair.getPrivate().getEncoded()));
        assertEquals(32, handler.execute(decapsulate, Map.of("encapsulation", FlowValue.binary(encapsulation)), null).bytes().length);
    }

    @Test void unsafeXmlFailsBeforeCryptographicWork() {
        String xxe = "<!DOCTYPE root [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><root>&xxe;</root>";
        ProcessDefinition.Node sign = node("XMLDSIG_SIGN", "keystorePath", "src/test/resources/testks.p12", "keystorePassword", "storepass");
        assertThrows(Exception.class, () -> handler.execute(sign, Map.of("payload", FlowValue.text(xxe, StandardCharsets.UTF_8)), null));
        assertThrows(Exception.class, () -> XMLSignatureOperations.requireSafeXml(xxe));
    }

    @Test void certificateValidationRequiresTrustByDefaultAndRejectsUntrustedCertificate() throws Exception {
        ProcessDefinition.Node certificateNode = node("CERT_SELF_SIGNED_GENERATE", "commonName", "untrusted.example", "keyAlgorithm", "RSA", "keySize", "2048", "validityDays", "30");
        byte[] generated = handler.execute(certificateNode, Map.of(), null).bytes();
        ProcessDefinition.Node validate = node("CERT_VALIDATE", "verificationMode", "REQUIRE_TRUST", "trustStorePath", "src/test/resources/testks.p12", "trustStorePassword", "storepass");
        assertThrows(IllegalArgumentException.class, () -> handler.execute(validate, Map.of("certificate", FlowValue.binary(generated)), null));
        ProcessDefinition.Node structural = node("CERT_VALIDATE", "verificationMode", "STRUCTURAL_ONLY");
        assertTrue(handler.execute(structural, Map.of("certificate", FlowValue.binary(generated)), null).render().startsWith("WARNING: STRUCTURAL_ONLY"));
    }

    @Test void every5b3bTypeHasDedicatedPreflightRejection() {
        for (String type : EnvelopeSignatureNodeHandler.TYPES) {
            ProcessDefinition definition = new ProcessDefinition();
            ProcessDefinition.Node operation = new ProcessDefinition.Node("op", type, type, 0, 0);
            operation.configuration.putAll(NodeCatalog.defaultConfiguration(type));
            if ("PQC_KEYPAIR_GENERATE".equals(type)) operation.configuration.put("algorithm", "invalid");
            if ("CERT_SELF_SIGNED_GENERATE".equals(type)) operation.configuration.put("validityDays", "0");
            definition.nodes.add(operation);
            assertThrows(IllegalArgumentException.class, () -> ProcessEngine.validate(definition), type);
        }
    }

    private static ProcessDefinition.Node node(String type, String... values) { ProcessDefinition.Node node = new ProcessDefinition.Node(type, type, type, 0, 0); for (int i = 0; i < values.length; i += 2) node.configuration.put(values[i], values[i + 1]); return node; }
    private static ProcessDefinition.Node storeNode(String type) { return node(type, "keystorePath", "src/test/resources/testks.p12", "keystorePassword", "storepass", "keyPassword", "storepass"); }
    private static ProcessDefinition.Node trustNode(String type) { return node(type, "verificationMode", "REQUIRE_TRUST", "trustStorePath", "src/test/resources/testks.p12", "trustStorePassword", "storepass"); }
}
