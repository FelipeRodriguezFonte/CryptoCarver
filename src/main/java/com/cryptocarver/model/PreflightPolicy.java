package com.cryptocarver.model;

/** Pure operation selection policy for readiness reports. */
public final class PreflightPolicy {
  private PreflightPolicy() {}

  public static PreflightReport evaluate(String operation, boolean isEncrypt, Inputs inputs) {
    if (operation == null) {
      return null;
    }
    String normalizedOperation = FormatProfilePolicy.operation(operation);
    return switch (normalizedOperation) {
      case "Symmetric Ciphers" ->
          OperationPreflightEngine.checkSymmetricCipher(
              inputs.inputData(),
              inputs.inputFormat(),
              inputs.algorithm(),
              inputs.mode(),
              inputs.padding(),
              inputs.keySource(),
              inputs.keyText(),
              inputs.keyReference(),
              inputs.keyMetadataOnly(),
              inputs.iv(),
              inputs.tag(),
              inputs.additionalData(),
              isEncrypt);
      case "Hashing" ->
          OperationPreflightEngine.checkHashing(
              inputs.inputData(), inputs.inputFormat(), inputs.algorithm());
      case "Digital Signatures" ->
          OperationPreflightEngine.checkDigitalSignature(
              inputs.inputData(),
              inputs.algorithm(),
              inputs.keyText(),
              inputs.verificationText(),
              false,
              isEncrypt);
      case "Message Authentication Codes" ->
          OperationPreflightEngine.checkMac(
              inputs.inputData(),
              inputs.algorithm(),
              inputs.keySource(),
              inputs.keyText(),
              inputs.keyReference(),
              inputs.verificationText(),
              isEncrypt,
              inputs.keyMetadataOnly());
      case "Asymmetric Ciphers" ->
          OperationPreflightEngine.checkAsymmetricCipher(
              inputs.inputData(), inputs.keyText(), false, inputs.padding(), isEncrypt);
      default -> null;
    };
  }

  /** Values already read from the active operation controls by the UI adapter. */
  public record Inputs(
      String inputData,
      String inputFormat,
      String algorithm,
      String mode,
      String padding,
      String keySource,
      String keyText,
      String keyReference,
      boolean keyMetadataOnly,
      String iv,
      String tag,
      String additionalData,
      String verificationText) {}
}
