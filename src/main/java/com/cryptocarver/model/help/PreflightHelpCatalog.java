package com.cryptocarver.model.help;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Stable localized help links for checks emitted by the integrated preflight routes. */
public final class PreflightHelpCatalog {
  private static final Set<String> RULE_IDS = Set.of(
      "preflight.input_data.incomplete", "preflight.input_data.ready",
      "preflight.input_format.blocked", "preflight.input_format.ready",
      "preflight.algorithm.incomplete", "preflight.algorithm.ready",
      "preflight.security_policy.warning", "preflight.cipher_mode.incomplete",
      "preflight.cipher_mode.ready", "preflight.symmetric_key.incomplete",
      "preflight.symmetric_key.blocked", "preflight.symmetric_key.ready",
      "preflight.iv_nonce.incomplete", "preflight.iv_nonce.blocked",
      "preflight.iv_nonce.warning", "preflight.iv_nonce.ready",
      "preflight.nonce.incomplete", "preflight.nonce.blocked",
      "preflight.nonce.warning", "preflight.nonce.ready",
      "preflight.aead_auth_tag.incomplete", "preflight.aead_auth_tag.blocked",
      "preflight.aead_auth_tag.ready", "preflight.aad_data.warning",
      "preflight.aad_data.ready", "preflight.input_payload.incomplete",
      "preflight.input_payload.ready", "preflight.digest_algorithm.incomplete",
      "preflight.digest_algorithm.ready", "preflight.digest_security.warning",
      "preflight.signature_input.incomplete", "preflight.signature_input.ready",
      "preflight.signature_algorithm.incomplete", "preflight.signature_algorithm.ready",
      "preflight.signature_key.incomplete", "preflight.signature_key.blocked",
      "preflight.signature_key.ready", "preflight.existing_signature.incomplete",
      "preflight.existing_signature.blocked", "preflight.existing_signature.ready",
      "preflight.mac_input.incomplete", "preflight.mac_input.ready",
      "preflight.mac_algorithm.incomplete", "preflight.mac_algorithm.ready",
      "preflight.mac_key.incomplete", "preflight.mac_key.blocked", "preflight.mac_key.ready",
      "preflight.mac_verification_value.incomplete", "preflight.mac_verification_value.blocked",
      "preflight.mac_verification_value.ready", "preflight.asymmetric_input.incomplete",
      "preflight.asymmetric_input.ready", "preflight.rsa_key.incomplete",
      "preflight.rsa_key.blocked", "preflight.rsa_key.ready",
      "preflight.rsa_padding.incomplete", "preflight.rsa_padding.warning",
      "preflight.rsa_padding.ready");

  private static final Map<String, String> FIELD_LABEL_KEYS = Map.ofEntries(
      Map.entry("cipherInputArea", "help.preflight.field.payload"),
      Map.entry("hashInputArea", "help.preflight.field.payload"),
      Map.entry("authInputArea", "help.preflight.field.payload"),
      Map.entry("symmetricKeyField", "help.preflight.field.symmetricKey"),
      Map.entry("authMacKeyField", "help.preflight.field.macKey"),
      Map.entry("symmetricAlgorithmCombo", "help.preflight.field.cipherAlgorithm"),
      Map.entry("hashAlgorithmCombo", "help.preflight.field.digestAlgorithm"),
      Map.entry("signatureAlgorithmCombo", "help.preflight.field.signatureAlgorithm"),
      Map.entry("authMacAlgorithmCombo", "help.preflight.field.macAlgorithm"),
      Map.entry("cipherModeCombo", "help.preflight.field.mode"),
      Map.entry("inputFormatCombo", "help.preflight.field.format"),
      Map.entry("ivField", "help.preflight.field.ivNonce"),
      Map.entry("gcmTagField", "help.preflight.field.tag"),
      Map.entry("aadField", "help.preflight.field.aad"),
      Map.entry("symHsmKeyCombo", "help.preflight.field.keySource"),
      Map.entry("macHsmKeyCombo", "help.preflight.field.keySource"),
      Map.entry("signaturePrivateKeyArea", "help.preflight.field.privateKey"),
      Map.entry("signaturePublicKeyArea", "help.preflight.field.publicKey"),
      Map.entry("signatureVerifyField", "help.preflight.field.signature"),
      Map.entry("authMacVerifyField", "help.preflight.field.macValue"),
      Map.entry("publicKeyArea", "help.preflight.field.publicKey"),
      Map.entry("privateKeyArea", "help.preflight.field.privateKey"),
      Map.entry("rsaPaddingCombo", "help.preflight.field.rsaPadding"));

  private PreflightHelpCatalog() {}

  public static Optional<String> explanationKey(String ruleId) {
    return ruleId != null && RULE_IDS.contains(ruleId)
        ? Optional.of("help.preflight.rule." + ruleId)
        : Optional.empty();
  }

  public static Optional<String> fieldLabelKey(String fieldId) {
    return Optional.ofNullable(FIELD_LABEL_KEYS.get(fieldId));
  }

  public static Set<String> ruleIds() { return RULE_IDS; }
  public static Set<String> fieldIds() { return FIELD_LABEL_KEYS.keySet(); }
}
