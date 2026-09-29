package com.cryptocarver.model;

/** Pure text policy for the guided operation workflow. */
public final class GuidedStepPolicy {
  private GuidedStepPolicy() {}

  public static StepText text(String operation, int step) {
    if (operation == null || step < 1 || step > 5) {
      return null;
    }
    return switch (step) {
      case 1 ->
          new StepText(
              "Step 1 of 5: Choose data/input format",
              "Configure the input encoding on the format flow bar (UTF-8, Hex, Base64).");
      case 2 ->
          new StepText(
              "Step 2 of 5: Choose algorithm & settings (or Start from a Template)",
              algorithmDescription(operation));
      case 3 -> new StepText("Step 3 of 5: Provide key / material", materialDescription(operation));
      case 4 ->
          new StepText(
              "Step 4 of 5: Review & execute",
              "Review your configuration and click the Execute/Run button to process data safely.");
      case 5 ->
          new StepText(
              "Step 5 of 5: Inspect, copy & save",
              "Inspect output bytes in summary bar or inspector. Copy or send to Clipboard Shelf.");
      default -> null;
    };
  }

  private static String algorithmDescription(String operation) {
    return switch (operation) {
      case "ENCRYPT" ->
          "Select cipher algorithm (e.g. AES-256), mode (GCM/CBC), or Apply a safe template.";
      case "HASH" -> "Select digest algorithm (e.g. SHA-256, SHA-512) or Apply a safe template.";
      case "SIGN" -> "Select signature scheme (e.g. RSA-SHA256, ECDSA) or Apply a safe template.";
      case "CERT" -> "Configure certificate format options or Apply a safe template.";
      case "CONVERT" -> "Select target output encoding (Base64, Hex, EBCDIC) or Apply a template.";
      default -> "";
    };
  }

  private static String materialDescription(String operation) {
    return switch (operation) {
      case "ENCRYPT" ->
          "Select key source (Manual, Key Lab, HSM). For GCM/CBC, click Generate for a fresh"
              + " IV/nonce. (Applying a template does not auto-advance or supply keys).";
      case "HASH" -> "Enter or paste the input payload to hash.";
      case "SIGN" -> "Select Private key (to sign) or Public key/cert (to verify).";
      case "CERT" -> "Paste PEM certificate text into input area.";
      case "CONVERT" -> "Enter input data to convert.";
      default -> "";
    };
  }

  public record StepText(String title, String description) {}
}
