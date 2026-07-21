package com.cryptoforge.ui;

import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.cryptoforge.crypto.GnuPgInterop;
import com.cryptoforge.crypto.OpenPgpOperations;
import com.cryptoforge.model.OperationResult;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;

/** OpenPGP laboratory UI, interoperable with ASCII-armored GnuPG material. */
public final class OpenPgpController {
    private static final long MAX_TEXT_FILE_BYTES = 16L * 1024L * 1024L;

    @FXML private TextField openPgpUserIdField;
    @FXML private PasswordField openPgpPassphraseField;
    @FXML private TextArea openPgpPublicKeyArea;
    @FXML private TextArea openPgpSecretKeyArea;
    @FXML private TextArea openPgpInputArea;
    @FXML private TextArea openPgpSignatureArea;
    @FXML private TextArea openPgpOutputArea;
    @FXML private Label openPgpGnuPgStatusLabel;

    private StatusReporter statusReporter;

    public void setStatusReporter(StatusReporter statusReporter) {
        this.statusReporter = statusReporter;
    }

    @FXML
    private void handleGenerateKeyPair() {
        char[] passphrase = passphrase();
        try {
            OpenPgpOperations.KeyPairMaterial keys = OpenPgpOperations.generateRsaKeyPair(
                    openPgpUserIdField.getText(), passphrase);
            openPgpPublicKeyArea.setText(keys.publicKeyArmored());
            openPgpSecretKeyArea.setText(keys.secretKeyArmored());
            openPgpOutputArea.setText("OpenPGP RSA key pair generated\nKey ID: " + keys.keyId()
                    + "\nFingerprint: " + keys.fingerprint());
            publish("OpenPGP Generate Key Pair", null, null, "Key ID", keys.keyId());
        } catch (Exception error) {
            showError("OpenPGP key generation", error.getMessage());
        } finally {
            Arrays.fill(passphrase, '\0');
        }
    }

    @FXML
    private void handleEncrypt() {
        try {
            byte[] input = requireInput();
            String encrypted = OpenPgpOperations.encrypt(input, openPgpPublicKeyArea.getText());
            openPgpOutputArea.setText(encrypted);
            publish("OpenPGP Encrypt", input, encrypted.getBytes(StandardCharsets.UTF_8), "Encoding", "ASCII armor");
        } catch (Exception error) {
            showError("OpenPGP encryption", error.getMessage());
        }
    }

    @FXML
    private void handleDecrypt() {
        char[] passphrase = passphrase();
        try {
            OpenPgpOperations.DecryptionResult result = OpenPgpOperations.decrypt(
                    openPgpInputArea.getText(), openPgpSecretKeyArea.getText(), passphrase);
            openPgpOutputArea.setText(new String(result.plaintext(), StandardCharsets.UTF_8));
            publish("OpenPGP Decrypt", openPgpInputArea.getText().getBytes(StandardCharsets.UTF_8), result.plaintext(),
                    "Recipient Key ID", result.recipientKeyId());
        } catch (Exception error) {
            showError("OpenPGP decryption", error.getMessage());
        } finally {
            Arrays.fill(passphrase, '\0');
        }
    }

    @FXML
    private void handleSignDetached() {
        char[] passphrase = passphrase();
        try {
            byte[] input = requireInput();
            String signature = OpenPgpOperations.signDetached(input, openPgpSecretKeyArea.getText(), passphrase);
            openPgpSignatureArea.setText(signature);
            openPgpOutputArea.setText("Detached OpenPGP signature generated.");
            publish("OpenPGP Sign Detached", input, signature.getBytes(StandardCharsets.UTF_8), "Encoding", "ASCII armor");
        } catch (Exception error) {
            showError("OpenPGP signing", error.getMessage());
        } finally {
            Arrays.fill(passphrase, '\0');
        }
    }

    @FXML
    private void handleSignAttached() {
        char[] passphrase = passphrase();
        try {
            byte[] input = requireInput();
            String signed = OpenPgpOperations.signAttached(input, openPgpSecretKeyArea.getText(), passphrase);
            openPgpOutputArea.setText(signed);
            publish("OpenPGP Sign Attached", input, signed.getBytes(StandardCharsets.UTF_8), "Encoding", "ASCII armor");
        } catch (Exception error) {
            showError("OpenPGP attached signing", error.getMessage());
        } finally {
            Arrays.fill(passphrase, '\0');
        }
    }

    @FXML
    private void handleVerifyAttached() {
        try {
            OpenPgpOperations.SignedMessageVerificationResult result = OpenPgpOperations.verifyAttached(
                    openPgpInputArea.getText(), openPgpPublicKeyArea.getText());
            String content = new String(result.content(), StandardCharsets.UTF_8);
            openPgpOutputArea.setText((result.valid() ? "VALID" : "INVALID")
                    + " OpenPGP attached signature\n\nContent:\n" + content);
            publish("OpenPGP Verify Attached", openPgpInputArea.getText().getBytes(StandardCharsets.UTF_8),
                    result.content(), "Result", result.valid() ? "VALID" : "INVALID");
        } catch (Exception error) {
            showError("OpenPGP attached verification", error.getMessage());
        }
    }

    @FXML
    private void handleClearSign() {
        char[] passphrase = passphrase();
        try {
            String signed = OpenPgpOperations.clearSign(openPgpInputArea.getText(), openPgpSecretKeyArea.getText(), passphrase);
            openPgpOutputArea.setText(signed);
            publish("OpenPGP Clear-sign", openPgpInputArea.getText().getBytes(StandardCharsets.UTF_8),
                    signed.getBytes(StandardCharsets.UTF_8), "Encoding", "Clear-signed ASCII armor");
        } catch (Exception error) {
            showError("OpenPGP clear-sign", error.getMessage());
        } finally {
            Arrays.fill(passphrase, '\0');
        }
    }

    @FXML
    private void handleVerifyClearSigned() {
        try {
            OpenPgpOperations.SignedMessageVerificationResult result = OpenPgpOperations.verifyClearSigned(
                    openPgpInputArea.getText(), openPgpPublicKeyArea.getText());
            String content = new String(result.content(), StandardCharsets.UTF_8);
            openPgpOutputArea.setText((result.valid() ? "VALID" : "INVALID")
                    + " OpenPGP clear-signed message\n\nContent:\n" + content);
            publish("OpenPGP Verify Clear-sign", openPgpInputArea.getText().getBytes(StandardCharsets.UTF_8),
                    result.content(), "Result", result.valid() ? "VALID" : "INVALID");
        } catch (Exception error) {
            showError("OpenPGP clear-sign verification", error.getMessage());
        }
    }

    @FXML
    private void handleVerifyDetached() {
        try {
            byte[] input = requireInput();
            OpenPgpOperations.VerificationResult result = OpenPgpOperations.verifyDetached(
                    new String(input, StandardCharsets.UTF_8), openPgpSignatureArea.getText(), openPgpPublicKeyArea.getText());
            openPgpOutputArea.setText(result.valid() ? "VALID OpenPGP detached signature" : "INVALID OpenPGP detached signature");
            publish("OpenPGP Verify Detached", input, null, "Result", result.valid() ? "VALID" : "INVALID");
        } catch (Exception error) {
            showError("OpenPGP verification", error.getMessage());
        }
    }

    /** Shows public metadata from a detached signature before or after verification. */
    @FXML
    private void handleInspectDetachedSignature() {
        try {
            OpenPgpOperations.DetachedSignatureInspection inspection =
                    OpenPgpOperations.inspectDetachedSignature(openPgpSignatureArea.getText());
            String createdAt = inspection.creationTime() == null ? "Not present" : inspection.creationTime().toString();
            openPgpOutputArea.setText("OpenPGP detached signature inspection"
                    + "\nSigner key ID: " + inspection.signerKeyId()
                    + "\nVersion: " + inspection.version()
                    + "\nSignature type: " + inspection.signatureType()
                    + "\nPublic-key algorithm: " + inspection.keyAlgorithm()
                    + "\nHash algorithm: " + inspection.hashAlgorithm()
                    + "\nCreation time: " + createdAt
                    + "\n\nIntegrity is NOT evaluated until Verify Detached is run with the exact data and public key."
                    + "\nOpenPGP Web-of-Trust is NOT evaluated by this laboratory inspector.");
            if (statusReporter != null) statusReporter.publish(OperationResult.forOperation("OpenPGP Inspect Detached Signature")
                    .detail("Signer key ID", inspection.signerKeyId())
                    .detail("Version", String.valueOf(inspection.version()))
                    .detail("Signature type", inspection.signatureType())
                    .detail("Public-key algorithm", inspection.keyAlgorithm())
                    .detail("Hash algorithm", inspection.hashAlgorithm())
                    .detail("Creation time", createdAt)
                    .detail("Integrity", "NOT EVALUATED")
                    .detail("Web-of-Trust", "NOT EVALUATED")
                    .status("OpenPGP detached signature inspected").build());
        } catch (Exception error) {
            showError("OpenPGP detached signature inspection", error.getMessage());
        }
    }

    /** Encrypts a local binary attachment into an ASCII-armored .pgp file. */
    @FXML
    private void handleEncryptFile() {
        try {
            File source = chooseInputFile("Select file to encrypt");
            if (source == null) return;
            byte[] input = readBounded(source, "Input file");
            File destination = chooseNewOutputFile(source.getName() + ".pgp", "Save encrypted OpenPGP file",
                    "ASCII-armored OpenPGP", "*.pgp", "*.asc");
            if (destination == null) return;
            byte[] armored = OpenPgpOperations.encrypt(input, openPgpPublicKeyArea.getText())
                    .getBytes(StandardCharsets.UTF_8);
            writeNew(destination, armored);
            openPgpOutputArea.setText("Encrypted " + input.length + " bytes to " + destination.getName()
                    + " (ASCII-armored OpenPGP).");
            publish("OpenPGP Encrypt File", input, armored, "Destination", destination.getName());
        } catch (Exception error) {
            showError("OpenPGP file encryption", error.getMessage());
        }
    }

    /** Decrypts a CryptoCarver/GnuPG ASCII-armored attachment to a new file. */
    @FXML
    private void handleDecryptFile() {
        char[] passphrase = passphrase();
        try {
            File source = chooseInputFile("Select ASCII-armored OpenPGP file to decrypt");
            if (source == null) return;
            byte[] encrypted = readBounded(source, "Encrypted file");
            File destination = chooseNewOutputFile(removeKnownOpenPgpExtension(source.getName()),
                    "Save decrypted file", "All files", "*.*");
            if (destination == null) return;
            OpenPgpOperations.DecryptionResult result = OpenPgpOperations.decrypt(
                    new String(encrypted, StandardCharsets.UTF_8), openPgpSecretKeyArea.getText(), passphrase);
            writeNew(destination, result.plaintext());
            openPgpOutputArea.setText("Decrypted " + result.plaintext().length + " bytes to " + destination.getName()
                    + " (integrity protected: " + result.integrityProtected() + ").");
            publish("OpenPGP Decrypt File", encrypted, result.plaintext(), "Destination", destination.getName());
        } catch (Exception error) {
            showError("OpenPGP file decryption", error.getMessage());
        } finally {
            Arrays.fill(passphrase, '\0');
        }
    }

    /** Signs a file's exact bytes and writes a detached ASCII-armored signature. */
    @FXML
    private void handleSignFileDetached() {
        char[] passphrase = passphrase();
        try {
            File source = chooseInputFile("Select file to sign");
            if (source == null) return;
            byte[] input = readBounded(source, "Input file");
            File destination = chooseNewOutputFile(source.getName() + ".asc", "Save detached OpenPGP signature",
                    "ASCII-armored OpenPGP signature", "*.asc", "*.sig");
            if (destination == null) return;
            byte[] signature = OpenPgpOperations.signDetached(input, openPgpSecretKeyArea.getText(), passphrase)
                    .getBytes(StandardCharsets.UTF_8);
            writeNew(destination, signature);
            openPgpOutputArea.setText("Detached signature for " + source.getName() + " saved as " + destination.getName() + ".");
            publish("OpenPGP Sign File Detached", input, signature, "Signature", destination.getName());
        } catch (Exception error) {
            showError("OpenPGP file signing", error.getMessage());
        } finally {
            Arrays.fill(passphrase, '\0');
        }
    }

    /** Verifies a detached signature against the exact bytes of a selected file. */
    @FXML
    private void handleVerifyFileDetached() {
        try {
            File source = chooseInputFile("Select signed file");
            if (source == null) return;
            File signatureFile = chooseInputFile("Select detached OpenPGP signature");
            if (signatureFile == null) return;
            byte[] input = readBounded(source, "Signed file");
            String signature = new String(readBounded(signatureFile, "Detached signature"), StandardCharsets.UTF_8);
            OpenPgpOperations.VerificationResult result = OpenPgpOperations.verifyDetached(
                    input, signature, openPgpPublicKeyArea.getText());
            String status = result.valid() ? "VALID" : "INVALID";
            openPgpOutputArea.setText(status + " detached OpenPGP file signature\nFile: " + source.getName()
                    + "\nSignature: " + signatureFile.getName() + "\nSigner: " + result.signerKeyId());
            publish("OpenPGP Verify File Detached", input, null, "Result", status);
        } catch (Exception error) {
            showError("OpenPGP file signature verification", error.getMessage());
        }
    }

    @FXML
    private void handleProbeGnuPg() {
        GnuPgInterop.Availability status = GnuPgInterop.probe();
        String message = status.available()
                ? "GnuPG available: " + status.version() + " (" + status.executable() + ")"
                : status.message();
        openPgpGnuPgStatusLabel.setText(message);
        if (statusReporter != null) statusReporter.updateStatus(message);
    }

    /** Runs the explicitly requested, isolated cross-tool OpenPGP lab. */
    @FXML
    private void handleRunGnuPgInterop() {
        final byte[] data;
        final char[] passphrase = passphrase();
        final String publicKey = openPgpPublicKeyArea.getText();
        final String secretKey = openPgpSecretKeyArea.getText();
        try {
            data = requireInput();
            if (!GnuPgInterop.probe().available()) {
                throw new IllegalStateException("GnuPG is not available. Use Check GnuPG… to inspect the configured binary.");
            }
        } catch (Exception error) {
            Arrays.fill(passphrase, '\0');
            showError("GnuPG interoperability", error.getMessage());
            return;
        }
        openPgpGnuPgStatusLabel.setText("Running isolated GnuPG interoperability exercise…");
        CompletableFuture.supplyAsync(() -> {
            try {
                return GnuPgInterop.exerciseBidirectional(publicKey, secretKey, passphrase, data);
            } catch (Exception error) {
                throw new IllegalStateException(error.getMessage(), error);
            } finally {
                Arrays.fill(passphrase, '\0');
            }
        }).whenComplete((result, error) -> Platform.runLater(() -> {
            if (error != null) {
                String message = error.getCause() == null ? error.getMessage() : error.getCause().getMessage();
                openPgpGnuPgStatusLabel.setText("GnuPG interoperability failed");
                showError("GnuPG interoperability", message);
                return;
            }
            String status = result.successful() ? "SUCCESS" : "FAILED";
            openPgpGnuPgStatusLabel.setText("GnuPG interoperability: " + status);
            openPgpOutputArea.setText("GnuPG / CryptoCarver interoperability: " + status + "\n" + result.message()
                    + "\n\nThe isolated GnuPG home has been removed. Trust is not evaluated.");
            publish("OpenPGP GnuPG Interoperability", data, null, "Result", status);
        }));
    }

    @FXML
    private void handleVerifyWithGnuPg() {
        final byte[] data;
        final String publicKey = openPgpPublicKeyArea.getText();
        final String signature = openPgpSignatureArea.getText();
        try {
            data = requireInput();
        } catch (Exception error) {
            showError("GnuPG verification", error.getMessage());
            return;
        }
        openPgpGnuPgStatusLabel.setText("Verifying detached signature with external GnuPG…");
        CompletableFuture.supplyAsync(() -> {
            try {
                return GnuPgInterop.verifyDetached(publicKey, data, signature);
            } catch (Exception error) {
                throw new IllegalStateException(error.getMessage(), error);
            }
        }).whenComplete((result, error) -> Platform.runLater(() -> {
            if (error != null) {
                String message = error.getCause() == null ? error.getMessage() : error.getCause().getMessage();
                openPgpGnuPgStatusLabel.setText("GnuPG verification unavailable or failed");
                showError("GnuPG verification", message);
                return;
            }
            String message = result.cryptographicallyValid() ? "VALID" : "INVALID";
            openPgpGnuPgStatusLabel.setText(result.message());
            openPgpOutputArea.setText("GnuPG detached signature: " + message + "\n" + result.message());
            publish("OpenPGP Verify Detached (GnuPG)", data, null, "Result", message);
        }));
    }

    @FXML
    private void handleInspectPublicKey() {
        inspectKey(openPgpPublicKeyArea.getText(), "OpenPGP Public Key Inspection");
    }

    @FXML
    private void handleInspectSecretKey() {
        inspectKey(openPgpSecretKeyArea.getText(), "OpenPGP Secret Key Inspection");
    }

    @FXML
    private void handleLoadPublicKey() {
        loadKey(openPgpPublicKeyArea, "OpenPGP public key", "*.asc", "*.pgp", "*.gpg", "*.pub");
    }

    @FXML
    private void handleLoadSecretKey() {
        loadKey(openPgpSecretKeyArea, "OpenPGP secret key", "*.asc", "*.pgp", "*.gpg", "*.key");
    }

    @FXML
    private void handleSavePublicKey() {
        saveKey(openPgpPublicKeyArea.getText(), "cryptocarver-openpgp-public.asc", "OpenPGP public key");
    }

    @FXML
    private void handleSaveSecretKey() {
        saveKey(openPgpSecretKeyArea.getText(), "cryptocarver-openpgp-secret.asc", "OpenPGP secret key");
    }

    @FXML
    private void handleLoadInput() {
        loadText(openPgpInputArea, "OpenPGP message or text", "*.asc", "*.pgp", "*.gpg", "*.txt");
    }

    @FXML
    private void handleLoadSignature() {
        loadText(openPgpSignatureArea, "OpenPGP detached signature", "*.asc", "*.sig", "*.pgp", "*.gpg");
    }

    @FXML
    private void handleSaveOutput() {
        saveText(openPgpOutputArea.getText(), "cryptocarver-openpgp-output.asc", "OpenPGP output");
    }

    @FXML
    private void handleSaveSignature() {
        saveText(openPgpSignatureArea.getText(), "cryptocarver-openpgp-signature.asc", "OpenPGP detached signature");
    }

    @FXML
    private void handleClear() {
        openPgpInputArea.clear();
        openPgpSignatureArea.clear();
        openPgpOutputArea.clear();
    }

    @FXML
    private void handleClearSecretMaterial() {
        openPgpSecretKeyArea.clear();
        openPgpPassphraseField.clear();
        if (statusReporter != null) statusReporter.updateStatus("OpenPGP secret material cleared from the visible form");
    }

    private byte[] requireInput() {
        if (openPgpInputArea.getText() == null || openPgpInputArea.getText().isEmpty()) {
            throw new IllegalArgumentException("Input data is required");
        }
        return openPgpInputArea.getText().getBytes(StandardCharsets.UTF_8);
    }

    private char[] passphrase() {
        return openPgpPassphraseField.getText() == null ? new char[0] : openPgpPassphraseField.getText().toCharArray();
    }

    private File chooseInputFile(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All files", "*.*"));
        return chooser.showOpenDialog(openPgpOutputArea.getScene() == null ? null : openPgpOutputArea.getScene().getWindow());
    }

    private File chooseNewOutputFile(String filename, String title, String description, String... patterns) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.setInitialFileName(filename);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(description, patterns));
        File selected = chooser.showSaveDialog(openPgpOutputArea.getScene() == null ? null : openPgpOutputArea.getScene().getWindow());
        if (selected != null && Files.exists(selected.toPath())) {
            throw new IllegalArgumentException("Destination already exists; choose a new file to avoid overwriting it");
        }
        return selected;
    }

    private static byte[] readBounded(File file, String description) throws Exception {
        long size = Files.size(file.toPath());
        if (size > MAX_TEXT_FILE_BYTES) {
            throw new IllegalArgumentException(description + " exceeds the laboratory limit of " + MAX_TEXT_FILE_BYTES + " bytes");
        }
        return Files.readAllBytes(file.toPath());
    }

    private static void writeNew(File file, byte[] bytes) throws Exception {
        Files.write(file.toPath(), bytes, java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE);
    }

    private static String removeKnownOpenPgpExtension(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        for (String extension : List.of(".pgp", ".gpg", ".asc")) {
            if (lower.endsWith(extension) && name.length() > extension.length()) {
                return name.substring(0, name.length() - extension.length());
            }
        }
        return name + ".decrypted";
    }

    private void publish(String operation, byte[] input, byte[] output, String detail, String value) {
        if (statusReporter != null) {
            statusReporter.publish(OperationResult.forOperation(operation).input(input).output(output)
                    .detail(detail, value).status(operation + " completed").build());
        }
    }

    private void showError(String title, String message) {
        if (statusReporter != null) statusReporter.showError(title, message);
    }

    private void inspectKey(String armored, String operation) {
        try {
            OpenPgpOperations.KeyInspection result = OpenPgpOperations.inspectKey(armored);
            String details = formatInspection(result);
            openPgpOutputArea.setText(details);
            publish(operation, null, null, "Fingerprint", result.fingerprint());
        } catch (Exception error) {
            showError(operation, error.getMessage());
        }
    }

    private void loadKey(TextArea destination, String description, String... patterns) {
        loadText(destination, description, patterns);
    }

    private void loadText(TextArea destination, String description, String... patterns) {
        try {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Load " + description);
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(description, patterns));
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All files", "*"));
            File file = chooser.showOpenDialog(destination.getScene() == null ? null : destination.getScene().getWindow());
            if (file == null) return;
            if (Files.size(file.toPath()) > MAX_TEXT_FILE_BYTES) {
                throw new IllegalArgumentException(description + " exceeds the laboratory limit of " + MAX_TEXT_FILE_BYTES + " bytes");
            }
            destination.setText(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            if (statusReporter != null) statusReporter.updateStatus(description + " loaded: " + file.getName());
        } catch (Exception error) {
            showError("OpenPGP key loading", error.getMessage());
        }
    }

    private void saveKey(String armored, String filename, String description) {
        saveText(armored, filename, description);
    }

    private void saveText(String text, String filename, String description) {
        try {
            if (text == null || text.isBlank()) throw new IllegalArgumentException("No data is available to save");
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save " + description);
            chooser.setInitialFileName(filename);
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ASCII-armored OpenPGP", "*.asc"));
            File file = chooser.showSaveDialog(openPgpOutputArea.getScene() == null ? null : openPgpOutputArea.getScene().getWindow());
            if (file == null) return;
            Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
            if (statusReporter != null) statusReporter.showInfo("OpenPGP artifact saved", file.getName());
        } catch (Exception error) {
            showError("OpenPGP key saving", error.getMessage());
        }
    }

    private static String formatInspection(OpenPgpOperations.KeyInspection result) {
        return "OPENPGP KEY INSPECTION\n"
                + "Type: " + (result.secretKeyMaterial() ? "Secret key ring (metadata only)" : "Public key ring") + "\n"
                + "Key ID: " + result.keyId() + "\n"
                + "Fingerprint: " + result.fingerprint() + "\n"
                + "Algorithm tag: " + result.algorithm() + "\n"
                + "Bit strength: " + result.bitStrength() + "\n"
                + "Encryption capable: " + result.encryptionCapable() + "\n"
                + "Signing capable: " + result.signingCapable() + "\n"
                + "User IDs: " + (result.userIds().isEmpty() ? "(none)" : String.join(" | ", result.userIds()));
    }
}
