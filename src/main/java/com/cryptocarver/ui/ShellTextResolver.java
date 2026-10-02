package com.cryptocarver.ui;

import java.util.Locale;
import java.util.function.Function;

/** Resolves translated shell text without depending on JavaFX. */
public final class ShellTextResolver {
  /** Remedy UserFacingErrorMapper attaches when it has nothing more specific to suggest. */
  private static final String FALLBACK_REMEDY = "Review the parameters and technical details to correct the error.";

  private final Function<String, String> translate;

  public ShellTextResolver(Function<String, String> translate) {
    this.translate = translate;
  }

  public String localizedSectionText(String value) {
    if (value == null) {
      return "";
    }
    String key = switch (value) {
      case "Cryptographic Operations" -> "bread.cryptoOperations";
      case "Symmetric Keys" -> "bread.symmetricKeys";
      case "Asymmetric Keys" -> "bread.asymmetricKeys";
      case "Ciphers" -> "bread.ciphers";
      case "Signatures & MAC" -> "bread.signaturesMac";
      case "Certificates", "Certificates & CMS" -> "bread.certificatesCms";
      case "JOSE / JWT" -> "bread.joseJwt";
      case "Post-Quantum", "Post-Quantum PQC" -> "bread.postQuantumPqc";
      case "XML Security" -> "bread.xmlSecurity";
      case "WSS Security" -> "bread.wssSecurity";
      case "EMV & Smartcards" -> "bread.emvSmartcards";
      case "Payment Cryptography" -> "bread.paymentCryptography";
      case "Utilities" -> "bread.utilities";
      case "History" -> "bread.history";
      case "Clipboard Shelf" -> "bread.clipboardShelf";
      case "Saved Sessions" -> "bread.savedSessions";
      default -> null;
    };
    return key == null ? value : translate.apply(key);
  }

  public String localizedModuleText(String value) {
    if (value == null) {
      return "";
    }
    String key = switch (value) {
      case "Symmetric" -> "bread.symmetric";
      case "Asymmetric" -> "bread.asymmetric";
      case "Tools" -> "bread.tools";
      case "General" -> "bread.module";
      default -> null;
    };
    return key == null ? value : translate.apply(key);
  }

  public UserFacingError localizedError(UserFacingError error) {
    if (error == null) {
      return null;
    }
    String title = error.title() == null ? "" : error.title().toLowerCase(Locale.ROOT);
    String keyPrefix = errorKeyPrefix(title);
    if (keyPrefix == null) {
      return withCommonTextLocalized(error);
    }
    return new UserFacingError(
        translate.apply(keyPrefix + ".title"),
        translate.apply(keyPrefix + ".detail"),
        translate.apply(keyPrefix + ".remedy"),
        error.fieldKey(),
        error.cause());
  }

  /** Errors without a dedicated translation keep their detail but get a translated title and fallback remedy. */
  private UserFacingError withCommonTextLocalized(UserFacingError error) {
    String titleKey = commonTitleKey(error.title());
    boolean fallbackRemedy = FALLBACK_REMEDY.equals(error.remedy());
    if (titleKey == null && !fallbackRemedy) {
      return error;
    }
    return new UserFacingError(
        titleKey == null ? error.title() : translate.apply(titleKey),
        error.detail(),
        fallbackRemedy ? translate.apply("error.wrap.fallback.remedy") : error.remedy(),
        error.fieldKey(),
        error.cause());
  }

  private static String commonTitleKey(String title) {
    if (title == null) {
      return null;
    }
    return switch (title) {
      case "Operation Failed" -> "error.wrap.fallback.title";
      case "Validation Error" -> "error.title.validation";
      case "Input Error" -> "error.title.input";
      case "Encryption Error" -> "error.title.encryption";
      case "Decryption Error" -> "error.title.decryption";
      case "IV Error" -> "error.title.iv";
      case "Tag Error" -> "error.title.tag";
      case "Save Error" -> "error.title.save";
      case "Analysis Error" -> "error.title.analysis";
      default -> null;
    };
  }

  private String errorKeyPrefix(String title) {
    if (title.contains("authentication tag") || title.contains("tag verification") || title.contains("autenticación")) {
      return "error.wrap.tag";
    }
    if (title.contains("padding")) {
      return "error.wrap.padding";
    }
    if (title.contains("key parameter") || title.contains("parámetro de clave")) {
      return "error.wrap.key";
    }
    if (title.contains("hexadecimal")) {
      return "error.wrap.hex";
    }
    if (title.contains("base64")) {
      return "error.wrap.base64";
    }
    if ((title.contains("certificate") || title.contains("certificado") || title.contains("key format"))
        && !title.startsWith("missing ")
        && !title.startsWith("falta ")) {
      return "error.wrap.cert";
    }
    if (title.contains("timestamp authority") || title.contains("sellado de tiempo")) {
      return "error.wrap.tsa";
    }
    return null;
  }
}
