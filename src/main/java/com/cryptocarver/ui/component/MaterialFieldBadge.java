package com.cryptocarver.ui.component;

import com.cryptocarver.crypto.CertificateGenerator;
import com.cryptocarver.crypto.SharedMaterialParser;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputControl;

import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.Base64;

/**
 * Reusable material field status badge and validator indicator.
 * Displays purpose label, format in use, byte count, and validity status
 * without exposing secret values.
 */
public class MaterialFieldBadge extends Label {

    public enum Status {
        EMPTY,
        VALID,
        INVALID,
        INCOMPLETE,
        NOT_APPLICABLE
    }

    private String purpose;
    private String fixedFormat;
    private ComboBox<String> formatCombo;
    private TextInputControl targetInput;
    private Integer expectedBytes;
    private Status currentStatus = Status.EMPTY;
    private int currentByteCount = 0;
    private String currentFormatName = "UTF-8";

    public MaterialFieldBadge() {
        getStyleClass().add("material-field-badge");
        setVisible(true);
        setManaged(true);
    }

    public MaterialFieldBadge(String purpose) {
        this();
        this.purpose = purpose;
    }

    public void attach(TextInputControl input, String defaultFormat) {
        this.targetInput = input;
        this.fixedFormat = defaultFormat;
        if (input != null) {
            input.textProperty().addListener((obs, oldVal, newVal) -> updateState());
            visibleProperty().bind(input.visibleProperty());
            managedProperty().bind(input.managedProperty());
        }
        updateState();
    }

    public void attach(TextInputControl input, ComboBox<String> formatCombo) {
        this.targetInput = input;
        this.formatCombo = formatCombo;
        if (input != null) {
            input.textProperty().addListener((obs, oldVal, newVal) -> updateState());
            visibleProperty().bind(input.visibleProperty());
            managedProperty().bind(input.managedProperty());
        }
        if (formatCombo != null) {
            formatCombo.valueProperty().addListener((obs, oldVal, newVal) -> updateState());
        }
        updateState();
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
        updateState();
    }

    public String getPurpose() {
        return purpose;
    }

    public void setExpectedBytes(Integer expectedBytes) {
        this.expectedBytes = expectedBytes;
        updateState();
    }

    public Integer getExpectedBytes() {
        return expectedBytes;
    }

    public void setFixedFormat(String format) {
        this.fixedFormat = format;
        updateState();
    }

    public Status getCurrentStatus() {
        return currentStatus;
    }

    public int getCurrentByteCount() {
        return currentByteCount;
    }

    public String getCurrentFormatName() {
        return currentFormatName;
    }

    public void updateState() {
        String format = fixedFormat;
        if (formatCombo != null && formatCombo.getValue() != null) {
            format = formatCombo.getValue();
        }
        if (format == null || format.trim().isEmpty()) {
            format = "UTF-8";
        }
        this.currentFormatName = format;

        String rawText = targetInput != null ? targetInput.getText() : null;
        if (rawText == null || rawText.trim().isEmpty()) {
            this.currentStatus = Status.EMPTY;
            this.currentByteCount = 0;
            setText(formatMessage("Empty", format));
            applyStyle(Status.EMPTY);
            if (targetInput != null) {
                targetInput.getStyleClass().remove("input-field-error");
            }
            return;
        }

        // Apply uniform space normalization (trim)
        String trimmed = rawText.trim();
        boolean valid = true;
        int byteCount = 0;
        String errorDetail = null;
        String detectedFormatLabel = format;

        String fmtUpper = format.toUpperCase();

        if (fmtUpper.contains("HEX") && fmtUpper.contains("BASE64")) {
            // Dual Hex / Base64 format: delegate to SharedMaterialParser
            String clean = trimmed.replaceAll("\\s+", "");
            if (clean.matches("^[0-9a-fA-F]*$") && clean.length() % 2 == 0) {
                try {
                    byte[] decoded = SharedMaterialParser.parseBytesByFormat(trimmed, "HEX");
                    byteCount = decoded.length;
                    detectedFormatLabel = "HEX";
                } catch (Exception e) {
                    valid = false;
                    errorDetail = e.getMessage() != null ? e.getMessage() : "Invalid Hex format";
                }
            } else {
                try {
                    byte[] decoded = SharedMaterialParser.parseBytesByFormat(trimmed, "BASE64");
                    byteCount = decoded.length;
                    detectedFormatLabel = "BASE64";
                } catch (Exception e) {
                    valid = false;
                    errorDetail = "Invalid Hex / Base64 format";
                }
            }
        } else if (fmtUpper.contains("HEX") && fmtUpper.contains("ASCII")) {
            // AAD or dual Hex/ASCII input: evaluate as Hex if valid hex, else US-ASCII (matching CipherController)
            String hexClean = trimmed.replaceAll("\\s+", "");
            if (hexClean.matches("^[0-9a-fA-F]*$") && hexClean.length() % 2 == 0) {
                byteCount = hexClean.length() / 2;
                detectedFormatLabel = "Hex";
            } else {
                byteCount = trimmed.getBytes(StandardCharsets.US_ASCII).length;
                detectedFormatLabel = "ASCII";
            }
        } else if (fmtUpper.contains("PEM") && fmtUpper.contains("DER")) {
            // Dual PEM / DER certificate or key input using SharedMaterialParser
            if (trimmed.contains("-----BEGIN")) {
                try {
                    if (trimmed.contains("PRIVATE KEY")) {
                        SharedMaterialParser.parsePrivateKeyPem(trimmed);
                        detectedFormatLabel = "PEM";
                    } else if (trimmed.contains("PUBLIC KEY")) {
                        SharedMaterialParser.parsePublicKeyPem(trimmed);
                        detectedFormatLabel = "PEM";
                    } else {
                        X509Certificate cert = SharedMaterialParser.parseCertificate(trimmed);
                        byteCount = cert.getEncoded().length;
                        detectedFormatLabel = "PEM";
                    }
                } catch (Exception e) {
                    valid = false;
                    errorDetail = e.getMessage() != null ? e.getMessage() : "Invalid PEM material structure";
                }
            } else {
                try {
                    X509Certificate cert = SharedMaterialParser.parseCertificate(trimmed);
                    byteCount = cert.getEncoded().length;
                    detectedFormatLabel = "DER";
                } catch (Exception e) {
                    valid = false;
                    errorDetail = "Invalid X.509 DER structure";
                }
            }
        } else if (fmtUpper.contains("HEX")) {
            String hexClean = trimmed.replaceAll("\\s+", "");
            if (!hexClean.matches("^[0-9a-fA-F]*$")) {
                valid = false;
                errorDetail = "Invalid hex chars";
            } else if (hexClean.length() % 2 != 0) {
                valid = false;
                errorDetail = "Odd hex length (" + hexClean.length() + " chars)";
            } else {
                byteCount = hexClean.length() / 2;
                if (expectedBytes != null && expectedBytes > 0 && byteCount != expectedBytes) {
                    valid = false;
                    errorDetail = byteCount + "B (expected " + expectedBytes + "B)";
                }
            }
        } else if (fmtUpper.contains("BASE64")) {
            String b64Clean = trimmed.replaceAll("\\s+", "");
            try {
                byte[] decoded = Base64.getDecoder().decode(b64Clean);
                byteCount = decoded.length;
            } catch (Exception e) {
                valid = false;
                errorDetail = "Invalid Base64 format";
            }
        } else if (fmtUpper.contains("PEM")) {
            try {
                if (trimmed.contains("PRIVATE KEY")) {
                    SharedMaterialParser.parsePrivateKeyPem(trimmed);
                    detectedFormatLabel = "PEM";
                } else if (trimmed.contains("PUBLIC KEY")) {
                    SharedMaterialParser.parsePublicKeyPem(trimmed);
                    detectedFormatLabel = "PEM";
                } else if (trimmed.contains("CERTIFICATE")) {
                    X509Certificate cert = SharedMaterialParser.parseCertificate(trimmed);
                    byteCount = cert.getEncoded().length;
                    detectedFormatLabel = "PEM";
                } else {
                    try {
                        SharedMaterialParser.parsePrivateKeyPem(trimmed);
                    } catch (Exception e1) {
                        SharedMaterialParser.parsePublicKeyPem(trimmed);
                    }
                    detectedFormatLabel = "PEM";
                }
            } catch (Exception e) {
                valid = false;
                errorDetail = e.getMessage() != null ? e.getMessage() : "Invalid PEM structure";
            }
        } else if (fmtUpper.contains("DER")) {
            try {
                X509Certificate cert = CertificateGenerator.parseCertificate(trimmed);
                byteCount = cert.getEncoded().length;
                detectedFormatLabel = "DER";
            } catch (Exception e) {
                valid = false;
                errorDetail = "Invalid X.509 DER structure";
            }
        } else {
            // Default UTF-8 / Text — byte count of trimmed input to match handlers
            byteCount = trimmed.getBytes(StandardCharsets.UTF_8).length;
        }

        this.currentByteCount = byteCount;
        if (valid) {
            this.currentStatus = Status.VALID;
            setText(formatMessage("Valid · " + byteCount + " bytes", detectedFormatLabel));
            applyStyle(Status.VALID);
            if (targetInput != null) {
                targetInput.getStyleClass().remove("input-field-error");
            }
        } else {
            this.currentStatus = Status.INVALID;
            setText(formatMessage("Invalid · " + (errorDetail != null ? errorDetail : "Format error"), detectedFormatLabel));
            applyStyle(Status.INVALID);
            if (targetInput != null && !targetInput.getStyleClass().contains("input-field-error")) {
                targetInput.getStyleClass().add("input-field-error");
            }
        }
    }

    public void updateStateNotApplicable() {
        this.currentStatus = Status.NOT_APPLICABLE;
        this.currentByteCount = 0;
        setText(purpose != null ? purpose + ": Not applicable" : "Not applicable");
        applyStyle(Status.NOT_APPLICABLE);
        if (targetInput != null) {
            targetInput.getStyleClass().remove("input-field-error");
        }
    }

    public void updateStateIncomplete(String reason) {
        this.currentStatus = Status.INCOMPLETE;
        this.currentByteCount = 0;
        String fmtName = fixedFormat != null ? fixedFormat : (formatCombo != null && formatCombo.getValue() != null ? formatCombo.getValue() : "UTF-8");
        setText(formatMessage("Incomplete · " + (reason != null ? reason : "Required"), fmtName));
        applyStyle(Status.INCOMPLETE);
        if (targetInput != null && !targetInput.getStyleClass().contains("input-field-error")) {
            targetInput.getStyleClass().add("input-field-error");
        }
    }

    public void updateStateKeyReference(String keyName, String algorithm, String kcv, boolean available) {
        this.currentStatus = available ? Status.VALID : Status.INVALID;
        this.currentByteCount = 0;
        StringBuilder sb = new StringBuilder();
        if (purpose != null) sb.append(purpose).append(" ");
        sb.append("[Reference]: ").append(keyName != null ? keyName : "Selected");
        if (algorithm != null) sb.append(" (").append(algorithm).append(")");
        if (kcv != null && !kcv.isEmpty()) sb.append(" KCV: ").append(kcv);
        sb.append(available ? " · Available" : " · Metadata-only");
        setText(sb.toString());
        applyStyle(available ? Status.VALID : Status.INVALID);
        if (targetInput != null) {
            if (!available) {
                if (!targetInput.getStyleClass().contains("input-field-error")) targetInput.getStyleClass().add("input-field-error");
            } else {
                targetInput.getStyleClass().remove("input-field-error");
            }
        }
    }

    private String formatMessage(String stateText, String formatName) {
        StringBuilder sb = new StringBuilder();
        if (purpose != null && !purpose.trim().isEmpty()) {
            sb.append(purpose).append(" [").append(formatName != null ? formatName : "UTF-8").append("]: ");
        } else if (formatName != null && !formatName.trim().isEmpty()) {
            sb.append("[").append(formatName).append("]: ");
        }
        sb.append(stateText);
        return sb.toString();
    }

    private void applyStyle(Status status) {
        getStyleClass().removeAll("material-badge-valid", "material-badge-invalid", "material-badge-empty", "material-badge-info");
        switch (status) {
            case VALID:
                getStyleClass().add("material-badge-valid");
                break;
            case INVALID:
            case INCOMPLETE:
                getStyleClass().add("material-badge-invalid");
                break;
            case NOT_APPLICABLE:
                getStyleClass().add("material-badge-info");
                break;
            case EMPTY:
            default:
                getStyleClass().add("material-badge-empty");
                break;
        }
    }
}
