package com.cryptocarver.model.help;

import com.cryptocarver.crypto.SymmetricCipher;
import java.util.List;

/** Read-only view of the symmetric selector/applicability rules used by the UI. */
public final class SymmetricCapabilityPolicy {
  public static final List<String> KEY_SOURCES = List.of("Manual Input", "Simulated HSM");

  public record State(
      String algorithm,
      String mode,
      boolean streamCipher,
      boolean modeSelectable,
      boolean paddingSelectable,
      boolean ecb,
      boolean aead,
      boolean ivApplicable,
      boolean tagApplicable,
      boolean aadVisible,
      boolean aadUsedByOperation,
      int generatedIvBytes,
      String modeRuleId,
      String paddingRuleId,
      String ivRuleId,
      String authenticationRuleId,
      String aadRuleId) {}

  private SymmetricCapabilityPolicy() {}

  public static State resolve(String algorithm, String mode) {
    String selectedAlgorithm = algorithm == null || algorithm.isBlank() ? "AES-256" : algorithm;
    String selectedMode = mode == null || mode.isBlank() ? "CBC" : mode;
    boolean stream = SymmetricCipher.isStreamCipher(selectedAlgorithm);
    boolean gcm = !stream && "GCM".equalsIgnoreCase(selectedMode);
    boolean ecb = !stream && "ECB".equalsIgnoreCase(selectedMode);
    boolean chachaPoly = "ChaCha20-Poly1305".equalsIgnoreCase(selectedAlgorithm);
    boolean xchachaPoly = "XChaCha20-Poly1305".equalsIgnoreCase(selectedAlgorithm);
    boolean aead = gcm || chachaPoly || xchachaPoly;
    boolean ivApplicable = stream || !ecb;
    int ivBytes = ivApplicable
        ? SymmetricCipher.getRecommendedIvLength(selectedAlgorithm, stream ? null : selectedMode)
        : 0;
    return new State(selectedAlgorithm, selectedMode, stream, !stream,
        !stream && SymmetricCipher.supportsPadding(selectedMode), ecb, aead,
        ivApplicable, aead, aead, gcm, ivBytes,
        stream ? "symmetric.mode.stream_disabled" : "symmetric.mode.block_selector",
        stream ? "symmetric.padding.stream_ignored"
            : SymmetricCipher.supportsPadding(selectedMode) ? "symmetric.padding.selectable" : "symmetric.padding.mode_forces_none",
        ecb ? "symmetric.iv.not_applicable" : "symmetric.iv.required_or_nonce",
        aead ? "symmetric.authentication.authenticated" : "symmetric.authentication.none",
        !aead ? "symmetric.aad.not_applicable" : gcm ? "symmetric.aad.gcm_used" : "symmetric.aad.visible_unused");
  }

  public static List<String> algorithms() { return SymmetricCipher.SUPPORTED_ALGORITHMS; }
  public static List<String> modes() { return SymmetricCipher.SUPPORTED_MODES; }
  public static List<String> paddings() { return SymmetricCipher.SUPPORTED_PADDINGS; }
}
