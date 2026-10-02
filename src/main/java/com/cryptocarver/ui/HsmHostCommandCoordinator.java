package com.cryptocarver.ui;

import com.cryptocarver.crypto.hsm.PayShieldBodyDecomposer;
import com.cryptocarver.crypto.hsm.PayShieldBodySchema;
import com.cryptocarver.crypto.hsm.PayShieldCommand;
import com.cryptocarver.crypto.hsm.PayShieldErrorCatalog;
import com.cryptocarver.crypto.hsm.PayShieldMessage;
import com.cryptocarver.crypto.hsm.PayShieldMessageCodec;
import com.cryptocarver.crypto.hsm.PayShieldResponse;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The HSM host command panel of the payments screen: composes and parses host command frames
 * (header, two-letter code, body, trailer, optional TCP length prefix) and explains responses
 * and their error codes. Everything is local: nothing is sent to a device.
 */
final class HsmHostCommandCoordinator {

    /** The panel's controls, injected into PaymentsController from payments.fxml. */
    record View(TextField header,
            TextField commandCode,
            TextField body,
            TextField trailer,
            TextField headerLength,
            CheckBox tcpPrefix,
            TextArea capturedFrame,
            TextArea result) {
    }

    private final TextField hsmHostHeaderField;
    private final TextField hsmHostCommandCodeField;
    private final TextField hsmHostBodyField;
    private final TextField hsmHostTrailerField;
    private final TextField hsmHostHeaderLengthField;
    private final CheckBox hsmHostTcpPrefixCheck;
    private final TextArea hsmHostCapturedFrameArea;
    private final TextArea hsmHostResultArea;
    private final Supplier<StatusReporter> reporter;

    HsmHostCommandCoordinator(View view, Supplier<StatusReporter> reporter) {
        this.hsmHostHeaderField = view.header();
        this.hsmHostCommandCodeField = view.commandCode();
        this.hsmHostBodyField = view.body();
        this.hsmHostTrailerField = view.trailer();
        this.hsmHostHeaderLengthField = view.headerLength();
        this.hsmHostTcpPrefixCheck = view.tcpPrefix();
        this.hsmHostCapturedFrameArea = view.capturedFrame();
        this.hsmHostResultArea = view.result();
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


    private static final String SUPPLIED_NC_RESPONSE = "0000ND007B44AC1DDEE2A94B0007-E000";

    void handleHsmHostCompose() {
        runHsmHost(() -> {
            PayShieldMessageCodec codec = hsmHostCodec();
            byte[] frame = codec.composeCommand(
                    controlText(hsmHostHeaderField),
                    controlText(hsmHostCommandCodeField).toUpperCase(java.util.Locale.ROOT),
                    controlText(hsmHostBodyField).getBytes(StandardCharsets.US_ASCII),
                    controlText(hsmHostTrailerField).getBytes(StandardCharsets.US_ASCII));
            String rendered = renderHsmFrame(frame, codec.tcpLengthPrefix());
            if (hsmHostCapturedFrameArea != null) {
                hsmHostCapturedFrameArea.setText(rendered);
            }
            return describeHsmCommand(codec.parseCommand(frame));
        });
    }

    void handleHsmHostAnalyzeCommand() {
        runHsmHost(() -> {
            PayShieldMessageCodec codec = hsmHostCodec();
            return describeHsmCommand(codec.parseCommand(readHsmFrame(codec.tcpLengthPrefix())));
        });
    }

    void handleHsmHostAnalyzeResponse() {
        runHsmHost(() -> {
            PayShieldMessageCodec codec = hsmHostCodec();
            return describeHsmResponse(codec.parseResponse(readHsmFrame(codec.tcpLengthPrefix())));
        });
    }

    /**
     * Origin not recorded: this value was already in the tree when its
     * provenance was questioned, and no independent capture supports it.
     * Kept as an unsourced legacy example, separate from the simulator capture.
     */
    void handleHsmHostLoadExample() {
        if (hsmHostHeaderLengthField != null) {
            hsmHostHeaderLengthField.setText("4");
        }
        if (hsmHostTcpPrefixCheck != null) {
            hsmHostTcpPrefixCheck.setSelected(false);
        }
        if (hsmHostCapturedFrameArea != null) {
            hsmHostCapturedFrameArea.setText(SUPPLIED_NC_RESPONSE);
        }
        if (hsmHostResultArea != null) {
            hsmHostResultArea.setText(t("module.payments.hsmHost.exampleLoaded"));
        }
    }

    private PayShieldMessageCodec hsmHostCodec() {
        String length = controlText(hsmHostHeaderLengthField);
        int headerLength;
        try {
            headerLength = Integer.parseInt(length);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("header length must be a decimal integer");
        }
        return new PayShieldMessageCodec(headerLength,
                hsmHostTcpPrefixCheck != null && hsmHostTcpPrefixCheck.isSelected());
    }

    private byte[] readHsmFrame(boolean tcpPrefix) {
        String value = controlText(hsmHostCapturedFrameArea);
        if (!tcpPrefix) {
            return value.getBytes(StandardCharsets.US_ASCII);
        }
        try {
            return HexFormat.of().parseHex(value.replaceAll("\\s+", ""));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("a TCP-prefixed frame must be entered as hexadecimal");
        }
    }

    private static String renderHsmFrame(byte[] frame, boolean tcpPrefix) {
        return tcpPrefix
                ? HexFormat.of().withUpperCase().formatHex(frame)
                : new String(frame, StandardCharsets.US_ASCII);
    }

    private String describeHsmCommand(PayShieldMessage message) {
        PayShieldCommand command = findHsmCommand(message.code());
        String body = new String(message.body(), StandardCharsets.US_ASCII);
        String trailer = new String(message.trailer(), StandardCharsets.US_ASCII);
        StringBuilder report = new StringBuilder()
                .append("Type: command\n")
                .append("Header: ").append(message.header()).append('\n')
                .append("Command code: ").append(message.code()).append('\n')
                .append("Command: ")
                .append(command == null ? "Unknown; body left opaque" : command.displayName())
                .append('\n')
                .append("Expected response: ")
                .append(command == null ? "Unknown" : command.expectedResponseCode()).append('\n')
                .append("Body length: ").append(message.body().length).append('\n')
                .append("Body (opaque): ").append(body.isEmpty() ? "(empty)" : body).append('\n')
                .append("Trailer: ").append(trailer.isEmpty() ? "(none)" : trailer);
        appendHsmDecomposition(report, PayShieldBodyDecomposer.decompose(message));
        return report.toString();
    }

    private String describeHsmResponse(PayShieldResponse response) {
        PayShieldCommand command = findHsmCommandByResponse(response.responseCode());
        String data = new String(response.data(), StandardCharsets.US_ASCII);
        String trailer = new String(response.trailer(), StandardCharsets.US_ASCII);
        StringBuilder report = new StringBuilder()
                .append("Type: response\n")
                .append("Header: ").append(response.header()).append('\n')
                .append("Response code: ").append(response.responseCode()).append('\n')
                .append("Command: ")
                .append(command == null ? "Unknown" : command.name() + " — " + command.displayName())
                .append('\n')
                .append("Error code: ").append(response.errorCode()).append(" — ")
                .append(PayShieldErrorCatalog.translate(response.errorCode())).append('\n')
                .append("Data length: ").append(response.data().length).append('\n')
                .append("Data (opaque): ").append(data.isEmpty() ? "(empty)" : data).append('\n')
                .append("Trailer: ").append(trailer.isEmpty() ? "(none)" : trailer);
        appendHsmDecomposition(report, PayShieldBodyDecomposer.decompose(response));
        return report.toString();
    }

    private void appendHsmDecomposition(
            StringBuilder report,
            Optional<PayShieldBodyDecomposer.Decomposition> optionalDecomposition) {
        optionalDecomposition.ifPresent(decomposition -> {
            report.append("\nBody schema: ")
                    .append(evidenceLabel(decomposition.schema().evidenceStatus()))
                    .append(" (").append(decomposition.schema().evidenceId()).append(')');
            for (PayShieldBodyDecomposer.DecodedField field : decomposition.fields()) {
                report.append('\n')
                        .append(field.definition().displayName())
                        .append(": ")
                        .append(field.value());
            }
        });
    }

    private String evidenceLabel(PayShieldBodySchema.EvidenceStatus status) {
        return t(switch (status) {
            case EXTERNAL_REQUEST -> "module.payments.hsmHost.evidence.externalRequest";
            case THIRD_PARTY_SIMULATOR -> "module.payments.hsmHost.evidence.simulator";
            case PENDING_CAPTURE -> "module.payments.hsmHost.evidence.pending";
            case VERIFIED -> "module.payments.hsmHost.evidence.verified";
        });
    }

    private static PayShieldCommand findHsmCommand(String code) {
        try {
            return PayShieldCommand.valueOf(code);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static PayShieldCommand findHsmCommandByResponse(String responseCode) {
        for (PayShieldCommand command : PayShieldCommand.values()) {
            if (command.expectedResponseCode().equals(responseCode)) {
                return command;
            }
        }
        return null;
    }

    private interface HsmHostStep {
        String run();
    }

    private void runHsmHost(HsmHostStep step) {
        try {
            String report = step.run();
            if (hsmHostResultArea != null) {
                hsmHostResultArea.setText(report);
            }
            updateStatus(t("module.payments.hsmHost.status"));
        } catch (Exception e) {
            if (hsmHostResultArea != null) {
                hsmHostResultArea.setText(
                        t("module.payments.hsmHost.error", String.valueOf(e.getMessage())));
            }
        }
    }

    private static String controlText(TextInputControl control) {
        return control == null || control.getText() == null ? "" : control.getText().trim();
    }
}
