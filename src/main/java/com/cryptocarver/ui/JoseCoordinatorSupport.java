package com.cryptocarver.ui;
import com.cryptocarver.crypto.JoseKeyMaterial;
import com.cryptocarver.crypto.JoseJwkPolicy;
import com.cryptocarver.model.OperationResult;
import com.nimbusds.jose.util.Base64URL;
import javafx.scene.control.*;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Shared live reporting and presentation helpers; owns no controller or key state. */
abstract class JoseCoordinatorSupport {
    private final Supplier<StatusReporter> statusReporter;
    JoseCoordinatorSupport(Supplier<StatusReporter> reporter) { this.statusReporter = reporter; }
    protected final StatusReporter reporter() { return statusReporter.get(); }
    protected static String t(String key, Object... args) {
        return com.cryptocarver.service.I18nService.getInstance().text(key, args);
    }

    protected static String jwsSecurityWarning(String algorithm, String key, JoseKeyMaterial.SecretEncoding encoding) {
        String alg = algorithm == null ? "" : algorithm.trim();
        if ("none".equalsIgnoreCase(alg)) return t("module.jose.warning.none");
        if (alg.startsWith("HS") && key != null && !key.isBlank()) {
            try {
                int expectedBytes = Integer.parseInt(alg.substring(2)) / 8;
                if (JoseKeyMaterial.secret(key, encoding).length < expectedBytes) return t("module.jose.warning.shortHmac");
            } catch (Exception ignored) { }
        }
        return null;
    }

    protected static String jweSecurityWarning(String algorithm) {
        if ("RSA1_5".equals(algorithm)) return t("module.jose.warning.rsa15");
        if ("RSA-OAEP".equals(algorithm)) return t("module.jose.warning.oaepSha1");
        return null;
    }

    protected static String jwtTokenSecurityWarning(String token, String key, JoseKeyMaterial.SecretEncoding encoding) {
        try {
            String header = token.trim().split("\\.", -1)[0];
            Map<String, Object> values = com.nimbusds.jose.util.JSONObjectUtils.parse(new Base64URL(header).decodeToString());
            String algorithm = String.valueOf(values.get("alg"));
            String warning = jwsSecurityWarning(algorithm, key, encoding);
            return warning;
        } catch (Exception ignored) { return null; }
    }

    protected static void addSecurityWarning(OperationResult.Builder result, String warning) {
        if (warning != null) result.detail("Security warning", warning);
    }

    protected static String metadataWarning(String json, JoseJwkPolicy.Operation operation) {
        String details = JoseJwkPolicy.metadataWarning(json, operation);
        return details == null ? null : t("module.jose.jwkMetadataWarning", details);
    }

    protected void showError(String title, String content) {
        if (reporter() != null) {
            reporter().showError(title, content);
        }
    }

    protected static String textOf(TextInputControl control) {
        return control == null ? null : control.getText();
    }

    protected static Set<String> parseHeaderNames(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return java.util.Arrays.stream(value.split(",")).map(String::trim).filter(name -> !name.isEmpty())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    protected static JoseKeyMaterial.SecretEncoding secretEncoding(ComboBox<String> combo) {
        return JoseKeyMaterial.SecretEncoding.fromLabel(combo == null ? null : combo.getValue());
    }

    protected static boolean isBlank(TextInputControl control) {
        return control == null || control.getText() == null || control.getText().isBlank();
    }

    protected void showValidation(String detail, String fieldKey) {
        showValidation(detail, fieldKey, "preflight.remedy.input");
    }

    protected void showValidation(String detail, String fieldKey, String remedyKey) {
        InlineValidationSupport.show(reporter(), t("preflight.title"), detail,
                t(remedyKey), fieldKey, null);
    }

    protected void showInfo(String title, String content) {
        if (reporter() != null) {
            reporter().showInfo(title, content);
        }
    }

    protected void updateStatus(String msg) {
        if (reporter() != null) {
            reporter().updateStatus(msg);
        }
    }

}
