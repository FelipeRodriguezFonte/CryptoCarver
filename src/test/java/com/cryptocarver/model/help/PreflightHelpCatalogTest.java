package com.cryptocarver.model.help;

import com.cryptocarver.model.OperationPreflightEngine;
import com.cryptocarver.model.PreflightCheck;
import com.cryptocarver.model.PreflightReport;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PreflightHelpCatalogTest {
  private static final String HEX_16 = "000102030405060708090A0B0C0D0E0F";
  private static final String HEX_24 = HEX_16 + "1011121314151617";
  private static final String HEX_32 = HEX_24 + "18191A1B1C1D1E1F";
  private static final String IV_12 = "000102030405060708090A0B";
  private static final String TAG_16 = "000102030405060708090A0B0C0D0E0F";

  @Test void everyRuleAndFieldEmittedByIntegratedEngineFixturesHasSpanishAndEnglishHelp() throws Exception {
    List<PreflightReport> reports = engineReports();
    Set<String> emittedRuleIds = reports.stream().flatMap(report -> report.getChecks().stream())
        .map(PreflightCheck::getRuleId).collect(Collectors.toSet());
    Set<String> emittedFieldIds = reports.stream().flatMap(report -> report.getChecks().stream())
        .map(PreflightCheck::getTargetControlKey).filter(java.util.Objects::nonNull)
        .collect(Collectors.toSet());
    assertEquals(PreflightHelpCatalog.ruleIds(), emittedRuleIds,
        "The fixtures must enumerate every current route-level check/status identity");

    for (String ruleId : emittedRuleIds) {
      String key = PreflightHelpCatalog.explanationKey(ruleId).orElseThrow(
          () -> new AssertionError("No help mapping for emitted rule " + ruleId));
      assertLocalized(ResourceBundle.getBundle("i18n.messages", Locale.ENGLISH), key, ruleId);
      assertLocalized(ResourceBundle.getBundle("i18n.messages", Locale.forLanguageTag("es")), key, ruleId);
      assertNotEquals("help.preflight.rule.generic",
          PreflightHelpCatalog.explanationKey(ruleId).orElseThrow());
    }
    assertTrue(PreflightHelpCatalog.fieldIds().containsAll(emittedFieldIds),
        "Every emitted target control needs a human-readable label mapping");
    for (String fieldId : emittedFieldIds) {
      String key = PreflightHelpCatalog.fieldLabelKey(fieldId).orElseThrow(
          () -> new AssertionError("No help field label for emitted target " + fieldId));
      assertLocalized(ResourceBundle.getBundle("i18n.messages", Locale.ENGLISH), key, fieldId);
      assertLocalized(ResourceBundle.getBundle("i18n.messages", Locale.forLanguageTag("es")), key, fieldId);
    }
  }

  private static void assertLocalized(ResourceBundle bundle, String key, String identity) {
    assertTrue(bundle.containsKey(key), identity + " is missing " + key + " in " + bundle.getLocale());
    String text = bundle.getString(key);
    assertFalse(text.isBlank(), identity + " has a blank explanation/label");
    assertNotEquals(key, text, identity + " falls through to the unresolved key");
    assertFalse(text.equals(bundle.getString("help.preflight.rule.generic")),
        identity + " resolves to the generic missing-explanation message");
  }

  private static List<PreflightReport> engineReports() throws Exception {
    List<PreflightReport> reports = new ArrayList<>();
    // Symmetric: empty, valid, invalid encodings/key/nonce/tag, AEAD and security warnings.
    reports.add(OperationPreflightEngine.checkSymmetricCipher("", "Text (UTF-8)", "", "", "PKCS5Padding",
        "Manual Input", "", null, false, "", "", "", true));
    reports.add(OperationPreflightEngine.checkSymmetricCipher("00", "Hexadecimal", "AES-256", "GCM", "NoPadding",
        "Manual Input", HEX_32, null, false, IV_12, "", HEX_16, true));
    reports.add(OperationPreflightEngine.checkSymmetricCipher("0", "Hexadecimal", "AES-256", "GCM", "NoPadding",
        "Manual Input", "GG", null, false, "GG", "GG", "GG", false));
    reports.add(OperationPreflightEngine.checkSymmetricCipher("cipher", "Text (UTF-8)", "AES-256", "GCM", "NoPadding",
        "Manual Input", HEX_32, null, false, IV_12, TAG_16, "", false));
    reports.add(OperationPreflightEngine.checkSymmetricCipher("cipher", "Text (UTF-8)", "AES-256", "GCM", "NoPadding",
        "Manual Input", HEX_32, null, false, "00", "", "", false));
    reports.add(OperationPreflightEngine.checkSymmetricCipher("cipher", "Text (UTF-8)", "DES", "ECB", "PKCS5Padding",
        "Manual Input", "0001020304050607", null, false, "", "", "", true));
    reports.add(OperationPreflightEngine.checkSymmetricCipher("payload", "Text (UTF-8)", "Salsa20", "", "",
        "Manual Input", HEX_32, null, false, "GG", "", "", true));
    reports.add(OperationPreflightEngine.checkSymmetricCipher("payload", "Text (UTF-8)", "Salsa20", "", "",
        "Manual Input", HEX_32, null, false, "", "", "", true));
    reports.add(OperationPreflightEngine.checkSymmetricCipher("payload", "Text (UTF-8)", "ChaCha20-Poly1305", "", "",
        "Manual Input", HEX_32, null, false, "00", "", "", true));
    reports.add(OperationPreflightEngine.checkSymmetricCipher("payload", "Text (UTF-8)", "ChaCha20-Poly1305", "", "",
        "Manual Input", HEX_32, null, false, IV_12, "", "", true));
    reports.add(OperationPreflightEngine.checkSymmetricCipher("payload", "Hexadecimal", "AES-128", "CBC", "PKCS5Padding",
        "Simulated HSM", "", "", false, IV_12, "", "", true));
    reports.add(OperationPreflightEngine.checkSymmetricCipher("payload", "Text (UTF-8)", "AES-128", "CBC", "PKCS5Padding",
        "Simulated HSM", "", "key-id", true, IV_12, "", "", true));

    // Hashing: missing/ready payload and selection, plus weak digest warning.
    reports.add(OperationPreflightEngine.checkHashing("", "Text (UTF-8)", ""));
    reports.add(OperationPreflightEngine.checkHashing("payload", "Text (UTF-8)", "SHA-256"));
    reports.add(OperationPreflightEngine.checkHashing("payload", "Text (UTF-8)", "MD5"));

    // Signatures: missing, malformed and usable key material in sign and verify routes.
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair pair = generator.generateKeyPair();
    if (java.security.Security.getProvider("BC") == null) {
      java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    }
    String privatePem = pem("PRIVATE KEY", pair.getPrivate().getEncoded());
    String publicPem = pem("PUBLIC KEY", pair.getPublic().getEncoded());
    reports.add(OperationPreflightEngine.checkDigitalSignature("", "", "", false, true));
    reports.add(OperationPreflightEngine.checkDigitalSignature("payload", "SHA256withRSA", "not a PEM", false, true));
    reports.add(OperationPreflightEngine.checkDigitalSignature("payload", "SHA256withRSA", privatePem, false, true));
    reports.add(OperationPreflightEngine.checkDigitalSignature("payload", "SHA256withRSA", publicPem, "", false, false));
    reports.add(OperationPreflightEngine.checkDigitalSignature("payload", "SHA256withRSA", publicPem, "00", false, false));
    reports.add(OperationPreflightEngine.checkDigitalSignature("payload", "SHA256withRSA", "metadata", true, true));
    reports.add(OperationPreflightEngine.checkDigitalSignature("payload", "SHA256withRSA", publicPem, "@@@", false, false));

    // MAC: manual and external key paths plus generation/verification values.
    reports.add(OperationPreflightEngine.checkMac("", "", "Manual Input", "", null, null, true, false));
    reports.add(OperationPreflightEngine.checkMac("payload", "HmacSHA256", "Manual Input", "GG", null, null, true, false));
    reports.add(OperationPreflightEngine.checkMac("payload", "HmacSHA256", "Manual Input", HEX_16, null, null, false, false));
    reports.add(OperationPreflightEngine.checkMac("payload", "HmacSHA256", "Manual Input", HEX_16, null, "@@@", false, false));
    reports.add(OperationPreflightEngine.checkMac("payload", "HmacSHA256", "Manual Input", HEX_16, null, "00", false, false));
    reports.add(OperationPreflightEngine.checkMac("payload", "HmacSHA256", "Simulated HSM", "", "", null, true, false));
    reports.add(OperationPreflightEngine.checkMac("payload", "HmacSHA256", "Simulated HSM", "", "mac-key", null, true, true));
    reports.add(OperationPreflightEngine.checkMac("payload", "HmacSHA256", "PKCS#11 Token", "", "mac-key", null, true, false));

    // RSA: missing and metadata-only keys, accepted keys and both legacy padding warnings.
    reports.add(OperationPreflightEngine.checkAsymmetricCipher("", "", false, "", true));
    reports.add(OperationPreflightEngine.checkAsymmetricCipher("payload", "metadata", true, "OAEPWithSHA-256AndMGF1Padding", true));
    reports.add(OperationPreflightEngine.checkAsymmetricCipher("payload", publicPem, false, "OAEPWithSHA-256AndMGF1Padding", true));
    reports.add(OperationPreflightEngine.checkAsymmetricCipher("payload", publicPem, false, "PKCS1Padding", true));
    reports.add(OperationPreflightEngine.checkAsymmetricCipher("payload", publicPem, false, "NoPadding", true));
    reports.add(OperationPreflightEngine.checkAsymmetricCipher("payload", privatePem, false, "OAEPWithSHA-256AndMGF1Padding", false));
    return reports;
  }

  private static String pem(String label, byte[] encoded) {
    String body = java.util.Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded);
    return "-----BEGIN " + label + "-----\n" + body + "\n-----END " + label + "-----";
  }
}
