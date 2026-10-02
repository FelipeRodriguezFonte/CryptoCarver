package com.cryptocarver.ui;

import com.cryptocarver.crypto.iso8583.Iso8583Operations;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The ISO 8583 workbench of the payments screen: parses a message or builds one from an MTI
 * and field=value lines, for the 1987 and 1993 profiles with binary or hex bitmaps and ASCII or
 * BCD length indicators.
 */
final class Iso8583Coordinator {
    private static final Logger LOG = LoggerFactory.getLogger(Iso8583Coordinator.class);

    /** The panel's controls, injected into PaymentsController from payments.fxml. */
    record View(ComboBox<String> profile,
            ComboBox<String> bitmapEncoding,
            ComboBox<String> lengthEncoding,
            TextArea message,
            TextArea report) {
    }

    private final ComboBox<String> iso8583ProfileCombo;
    private final ComboBox<String> iso8583BitmapEncodingCombo;
    private final ComboBox<String> iso8583LengthEncodingCombo;
    private final TextArea iso8583MessageArea;
    private final TextArea iso8583ReportArea;
    private final Supplier<StatusReporter> reporter;

    Iso8583Coordinator(View view, Supplier<StatusReporter> reporter) {
        this.iso8583ProfileCombo = view.profile();
        this.iso8583BitmapEncodingCombo = view.bitmapEncoding();
        this.iso8583LengthEncodingCombo = view.lengthEncoding();
        this.iso8583MessageArea = view.message();
        this.iso8583ReportArea = view.report();
        this.reporter = reporter;
    }

    private StatusReporter reporter() {
        return reporter.get();
    }

    private static String t(String key, Object... args) {
        return com.cryptocarver.service.I18nService.getInstance().text(key, args);
    }

    private void updateStatus(String message) {
        if (reporter() != null) reporter().updateStatus(message);
    }


    void configure() {
        if (iso8583ProfileCombo != null) {
            iso8583ProfileCombo.getItems().setAll("ISO 8583:1987", "ISO 8583:1993");
            iso8583ProfileCombo.getSelectionModel().selectFirst();
        }
        if (iso8583BitmapEncodingCombo != null) {
            iso8583BitmapEncodingCombo.getItems().setAll("Binary", "Hexadecimal ASCII");
            iso8583BitmapEncodingCombo.getSelectionModel().selectFirst();
        }
        if (iso8583LengthEncodingCombo != null) {
            iso8583LengthEncodingCombo.getItems().setAll("ASCII", "BCD");
            iso8583LengthEncodingCombo.getSelectionModel().selectFirst();
        }
    }

    void handleParseIso8583() {
        runIso8583Operation("parse");
    }

    void handleBuildIso8583() {
        runIso8583Operation("build");
    }

    private void runIso8583Operation(String operation) {
        if (iso8583MessageArea == null || iso8583ReportArea == null) return;
        String input = iso8583MessageArea.getText() == null ? "" : iso8583MessageArea.getText().trim();
        if (input.isEmpty()) {
            iso8583ReportArea.setText(t("module.payments.iso8583.emptyMessage"));
            return;
        }
        try {
            Iso8583Operations.Profile profile = iso8583Profile();
            String report;
            if ("parse".equals(operation)) {
                Iso8583Operations.Message parsed = parseIso8583(input, profile);
                report = parsed.report();
            } else {
                BuiltIso8583 built = buildIso8583(input, profile);
                byte[] rebuilt = Iso8583Operations.build(built.mti(), built.values(), profile);
                report = "Built message (" + profile.bitmapEncoding() + ", " + profile.lengthEncoding() + "):\n"
                        + formatIsoOutput(rebuilt, profile);
            }
            iso8583ReportArea.setText(report);
            updateStatus(t("module.payments.iso8583.completed", operation));
        } catch (Exception e) {
            LOG.debug("ISO 8583 {} failed", operation, e);
            iso8583ReportArea.setText(t("module.payments.iso8583.error", e.getMessage()));
        }
    }

    private Iso8583Operations.Profile iso8583Profile() {
        Iso8583Operations.Version version = "ISO 8583:1993".equals(iso8583ProfileCombo.getValue())
                ? Iso8583Operations.Version.ISO_1993 : Iso8583Operations.Version.ISO_1987;
        Iso8583Operations.BitmapEncoding bitmap = "Hexadecimal ASCII".equals(iso8583BitmapEncodingCombo.getValue())
                ? Iso8583Operations.BitmapEncoding.ASCII_HEX : Iso8583Operations.BitmapEncoding.BINARY;
        Iso8583Operations.LengthEncoding length = "BCD".equals(iso8583LengthEncodingCombo.getValue())
                ? Iso8583Operations.LengthEncoding.BCD : Iso8583Operations.LengthEncoding.ASCII;
        return new Iso8583Operations.Profile(version, bitmap, length, false);
    }

    private static Iso8583Operations.Message parseIso8583(String input, Iso8583Operations.Profile profile) {
        if (profile.bitmapEncoding() == Iso8583Operations.BitmapEncoding.BINARY) {
            return Iso8583Operations.parseBinary(hexToBytes(input), profile);
        }
        return Iso8583Operations.parseAsciiHex(input.replaceAll("\\s+", ""), profile);
    }

    private record BuiltIso8583(String mti, Map<Integer, String> values) { }

    /** Build input is MTI on the first line followed by one decimal field per line, e.g. 3=000000. */
    private static BuiltIso8583 buildIso8583(String input, Iso8583Operations.Profile profile) {
        String[] lines = input.lines().map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
        if (lines.length > 1 && lines[0].matches("\\d{4}")) {
            Map<Integer, String> fields = new LinkedHashMap<>();
            for (int i = 1; i < lines.length; i++) {
                int equals = lines[i].indexOf('=');
                if (equals < 1) throw new IllegalArgumentException("build lines must use field=value");
                int number = Integer.parseInt(lines[i].substring(0, equals).trim());
                fields.put(number, lines[i].substring(equals + 1).trim());
            }
            return new BuiltIso8583(lines[0], fields);
        }
        Iso8583Operations.Message parsed = parseIso8583(input, profile);
        Map<Integer, String> values = new LinkedHashMap<>();
        parsed.fields().forEach((n, field) -> values.put(n, field.value()));
        return new BuiltIso8583(parsed.mti().value(), values);
    }

    private static String formatIsoOutput(byte[] bytes, Iso8583Operations.Profile profile) {
        if (profile.bitmapEncoding() == Iso8583Operations.BitmapEncoding.ASCII_HEX) {
            return new String(bytes, java.nio.charset.StandardCharsets.US_ASCII);
        }
        StringBuilder out = new StringBuilder();
        for (byte b : bytes) out.append(String.format(java.util.Locale.ROOT, "%02X", b & 0xff));
        return out.toString();
    }

    private static byte[] hexToBytes(String value) {
        String hex = value.replaceAll("\\s+", "");
        if (!hex.matches("(?i)[0-9a-f]+") || (hex.length() & 1) != 0) {
            throw new IllegalArgumentException("binary input must be an even-length hexadecimal message");
        }
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < result.length; i++) result[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        return result;
    }
}
