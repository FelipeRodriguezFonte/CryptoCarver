package com.cryptocarver.ui;

import com.cryptocarver.crypto.PostQuantumOperations;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.util.DataConverter;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.stage.FileChooser;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Supplier;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** Owns post-quantum key generation, import validation, export, and key details. */
final class PostQuantumKeyCoordinator {
    record View(ComboBox<String> algorithm,
                Button generateButton,
                TextArea publicKeyArea,
                TextArea privateKeyArea,
                TextArea keyDetailsArea,
                Label keyStatusLabel,
                ComboBox<String> kemAlgorithm,
                ComboBox<String> signatureAlgorithm,
                PostQuantumKeyState keyState) { }

    private record ParsedPqcKey(boolean publicKey, byte[] encoded, String displayValue) { }

    private static final Logger LOG = LoggerFactory.getLogger(PostQuantumKeyCoordinator.class);
    private final View view;
    private final Supplier<StatusReporter> reporter;

    PostQuantumKeyCoordinator(View view, Supplier<StatusReporter> reporter) {
        this.view = view;
        this.reporter = reporter;
    }

    private StatusReporter reporter() { return reporter.get(); }

    private String t(String key, Object... args) {
        return com.cryptocarver.service.I18nService.getInstance().text(key, args);
    }

    void handleGeneratePQCKeyPair() {
        try {
            String algo = view.algorithm().getValue();
            if (algo == null || algo.startsWith("---")) {
                if (reporter() != null) reporter().showError("Algorithm Error", "Please select a valid algorithm");
                return;
            }

            Callable<KeyPair> task = () -> PostQuantumOperations.generateKeyPair(algo);

            Consumer<KeyPair> onSuccess = kp -> {
                try {
                    view.keyState().setPublicKey(kp.getPublic());
                    view.keyState().setPrivateKey(kp.getPrivate());
                    PublicKey currentPublicKey = view.keyState().publicKey();
                    PrivateKey currentPrivateKey = view.keyState().privateKey();

                    String pubHex = DataConverter.bytesToHex(currentPublicKey.getEncoded());
                    String privHex = DataConverter.bytesToHex(currentPrivateKey.getEncoded());

                    try {
                        view.publicKeyArea().setText("-----BEGIN PUBLIC KEY-----\n" +
                            java.util.Base64.getEncoder().encodeToString(currentPublicKey.getEncoded()) +
                            "\n-----END PUBLIC KEY-----");

                        view.privateKeyArea().setText("-----BEGIN PRIVATE KEY-----\n" +
                            java.util.Base64.getEncoder().encodeToString(currentPrivateKey.getEncoded()) +
                            "\n-----END PRIVATE KEY-----");
                    } catch (Exception e) {
                        view.publicKeyArea().setText(pubHex);
                        view.privateKeyArea().setText(privHex);
                    }

                    if (view.keyStatusLabel() != null) {
                        view.keyStatusLabel().setText("Generated " + algo + " Key Pair");
                    }
                    List<OperationDetail> details = describeKeyPair(algo, "Generated");
                    if (view.keyDetailsArea() != null) view.keyDetailsArea().setText(formatDetails(details));
                    if (reporter() != null) {
                        reporter().publish(OperationResult.forOperation("PQC Key Generation")
                                .output(currentPublicKey.getEncoded())
                                .details(detailsWithPublicKeyMaterial(details))
                                .status("Generated " + algo + " Key Pair")
                                .build());
                    }
                } catch (Exception e) {
                    if (reporter() != null) reporter().showError("Generation Error", "Error generating key: " + e.getMessage());
                }
            };

            Consumer<Throwable> onFailure = err -> {
                if (reporter() != null) reporter().showError("Generation Error", "Error generating key: " + (err != null ? err.getMessage() : "Unknown error"));
            };

            Runnable onCancelled = () -> {
                if (view.keyStatusLabel() != null) view.keyStatusLabel().setText(t("module.pqc.cancelled"));
                if (reporter() != null) reporter().updateStatus(t("module.pqc.cancelled"));
            };

            if (reporter() != null && reporter().getOperationExecutor() != null) {
                reporter().getOperationExecutor().execute("PQC-" + algo + " Key Generation", view.generateButton(), task, onSuccess, onFailure, onCancelled);
            } else {
                KeyPair kp = task.call();
                onSuccess.accept(kp);
            }

        } catch (Exception e) {
            if (reporter() != null) reporter().showError("Generation Error", "Error generating key: " + e.getMessage());
            LOG.error("PQC key generation failed", e);
        }
    }

    void handleImportPQCKeys() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select PQC public and/or private PEM/DER keys");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "PQC key files (PEM/DER)", "*.pem", "*.der", "*.pub", "*.key"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All files", "*.*"));
        List<File> files = chooser.showOpenMultipleDialog(null);
        if (files == null || files.isEmpty()) return;

        try {
            importKeysFromFiles(files);
        } catch (Exception e) {
            if (reporter() != null) reporter().showError("Import Error", "Failed to load keys: " + e.getMessage());
        }
    }

    void importKeysFromContents(List<String> pems) throws Exception {
        if (pems == null || pems.isEmpty()) {
            throw new IllegalArgumentException("Select at least one PQC public or private key.");
        }
        List<ParsedPqcKey> parsedKeys = new ArrayList<>();
        for (String pem : pems) {
            parsedKeys.add(parsePemKey(pem));
        }
        importParsedKeys(parsedKeys);
    }

    /** Imports one or both unencrypted PQC key files. Each file must contain one PEM or DER key. */
    void importKeysFromFiles(List<File> files) throws Exception {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Select at least one PQC public or private key.");
        }
        List<ParsedPqcKey> parsedKeys = new ArrayList<>();
        for (File file : files) {
            if (file == null) {
                throw new IllegalArgumentException("The selected key file is missing.");
            }
            byte[] contents = Files.readAllBytes(file.toPath());
            if (contents.length == 0) {
                throw new IllegalArgumentException("Key file " + file.getName() + " is empty.");
            }
            String ascii = new String(contents, StandardCharsets.US_ASCII).stripLeading();
            if (ascii.startsWith("-----BEGIN ")) {
                parsedKeys.add(parsePemKey(ascii));
            } else {
                parsedKeys.add(parseDerKey(contents, file.getName()));
            }
        }
        importParsedKeys(parsedKeys);
    }

    private void importParsedKeys(List<ParsedPqcKey> parsedKeys) throws Exception {
        if (parsedKeys == null || parsedKeys.isEmpty()) {
            throw new IllegalArgumentException("Select at least one PQC public or private key.");
        }
        String lastDetectedAlgorithm = null;
        PublicKey tempPubKey = null;
        PrivateKey tempPrivKey = null;
        String tempPubKeyStr = null;
        String tempPrivKeyStr = null;

        for (ParsedPqcKey parsedKey : parsedKeys) {
            byte[] encoded = parsedKey.encoded();
            boolean isPublic = parsedKey.publicKey();
            PostQuantumOperations.PqcAlgorithmDetectionResult result = PostQuantumOperations.detectAlgorithmFromEncoded(encoded, isPublic);
            if (result == null || !result.isSupported()) {
                String oid = result != null ? result.originalOid() : "Unknown";
                throw new IllegalArgumentException("Cannot detect PQC algorithm in key (OID: " + oid
                        + "). The key is not a supported PQC parameter set or its encoding is ambiguous.");
            }

            String detectedAlgorithm = result.nistName();
            if (lastDetectedAlgorithm != null && !lastDetectedAlgorithm.equals(detectedAlgorithm)) {
                throw new IllegalArgumentException(t("module.pqc.error.mismatchedAlgorithms",
                        lastDetectedAlgorithm, detectedAlgorithm));
            }
            lastDetectedAlgorithm = detectedAlgorithm;

            if (isPublic) {
                if (tempPubKey != null) {
                    throw new IllegalArgumentException("Only one public PQC key may be imported at a time.");
                }
                try {
                    tempPubKey = PostQuantumOperations.importPublicKey(detectedAlgorithm, encoded);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Unable to import the complete PQC public key for "
                            + detectedAlgorithm + ". Expected X.509 SubjectPublicKeyInfo DER.", e);
                }
                tempPubKeyStr = parsedKey.displayValue();
            } else {
                if (tempPrivKey != null) {
                    throw new IllegalArgumentException("Only one private PQC key may be imported at a time.");
                }
                try {
                    tempPrivKey = PostQuantumOperations.importPrivateKey(detectedAlgorithm, encoded);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Unable to import the complete PQC private key for "
                            + detectedAlgorithm + ". Expected unencrypted PKCS#8 DER.", e);
                }
                tempPrivKeyStr = parsedKey.displayValue();
            }
        }

        // Validate both sides before changing key state. This also covers
        // importing one side against an already loaded counterpart.
        PublicKey candidatePublicKey = tempPubKey != null ? tempPubKey : view.keyState().publicKey();
        PrivateKey candidatePrivateKey = tempPrivKey != null ? tempPrivKey : view.keyState().privateKey();
        if ((tempPubKey != null || tempPrivKey != null)
                && candidatePublicKey != null && candidatePrivateKey != null) {
            validateImportedKeyPair(candidatePublicKey, candidatePrivateKey);
        }

        // All files verified successfully, apply state.
        if (tempPubKey != null) {
            view.keyState().setPublicKey(tempPubKey);
            if (view.publicKeyArea() != null) view.publicKeyArea().setText(tempPubKeyStr);
        }
        if (tempPrivKey != null) {
            view.keyState().setPrivateKey(tempPrivKey);
            if (view.privateKeyArea() != null) view.privateKeyArea().setText(tempPrivKeyStr);
        }

        // Update combo if it is one of the known primary names.
        if (lastDetectedAlgorithm != null && view.algorithm() != null) {
            if (PostQuantumOperations.ML_KEM_ALGORITHMS.contains(lastDetectedAlgorithm)) {
                view.algorithm().setValue(lastDetectedAlgorithm);
                if (view.kemAlgorithm() != null) view.kemAlgorithm().setValue(lastDetectedAlgorithm);
            } else if (PostQuantumOperations.ML_DSA_ALGORITHMS.contains(lastDetectedAlgorithm) || PostQuantumOperations.SLH_DSA_ALGORITHMS.contains(lastDetectedAlgorithm)) {
                view.algorithm().setValue(lastDetectedAlgorithm);
                if (view.signatureAlgorithm() != null) view.signatureAlgorithm().setValue(lastDetectedAlgorithm);
            }
        }

        if (lastDetectedAlgorithm != null) {
            if (view.keyStatusLabel() != null) view.keyStatusLabel().setText("Imported PQC key material for " + lastDetectedAlgorithm);
            List<OperationDetail> details = describeKeyPair(lastDetectedAlgorithm, "Imported");
            if (view.keyDetailsArea() != null) view.keyDetailsArea().setText(formatDetails(details));
            if (reporter() != null) {
                reporter().publish(OperationResult.forOperation("PQC Import")
                        .details(details).status("Success").build());
            }
        }
    }

    private ParsedPqcKey parsePemKey(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalArgumentException("PEM key content is empty.");
        }
        String value = pem.trim();
        String begin;
        String end;
        boolean publicKey;
        if (value.startsWith("-----BEGIN PUBLIC KEY-----")) {
            begin = "-----BEGIN PUBLIC KEY-----";
            end = "-----END PUBLIC KEY-----";
            publicKey = true;
        } else if (value.startsWith("-----BEGIN PRIVATE KEY-----")) {
            begin = "-----BEGIN PRIVATE KEY-----";
            end = "-----END PRIVATE KEY-----";
            publicKey = false;
        } else if (value.startsWith("-----BEGIN ")) {
            throw new IllegalArgumentException("Unsupported PEM label. Use PUBLIC KEY or unencrypted PRIVATE KEY (PKCS#8).");
        } else {
            throw new IllegalArgumentException("Content is not a PEM public key or unencrypted PKCS#8 private key.");
        }
        if (!value.endsWith(end)) {
            throw new IllegalArgumentException("PEM key is truncated or has mismatched BEGIN/END labels.");
        }

        String body = value.substring(begin.length(), value.length() - end.length()).trim();
        if (body.isEmpty() || body.contains("-----BEGIN") || body.contains("-----END")) {
            throw new IllegalArgumentException("PEM key contains no complete base64 body or contains multiple PEM blocks.");
        }
        try {
            byte[] encoded = Base64.getDecoder().decode(body.replaceAll("\\s", ""));
            if (encoded.length == 0) {
                throw new IllegalArgumentException("PEM key contains an empty DER body.");
            }
            return new ParsedPqcKey(publicKey, encoded, value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(t("module.pqc.error.malformedPem"), e);
        }
    }

    private ParsedPqcKey parseDerKey(byte[] encoded, String fileName) {
        boolean publicKey = isDerSubjectPublicKeyInfo(encoded);
        boolean privateKey = isDerPrivateKeyInfo(encoded);
        if (publicKey == privateKey) {
            throw new IllegalArgumentException("DER key file " + fileName
                    + " is not exactly one X.509 SubjectPublicKeyInfo or PKCS#8 PrivateKeyInfo.");
        }
        String display = "[DER " + (publicKey ? "PUBLIC KEY" : "PRIVATE KEY") + " imported]";
        return new ParsedPqcKey(publicKey, encoded.clone(), display);
    }

    private boolean isDerSubjectPublicKeyInfo(byte[] encoded) {
        try {
            SubjectPublicKeyInfo.getInstance(ASN1Primitive.fromByteArray(encoded));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isDerPrivateKeyInfo(byte[] encoded) {
        try {
            PrivateKeyInfo.getInstance(ASN1Primitive.fromByteArray(encoded));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void validateImportedKeyPair(PublicKey publicKey, PrivateKey privateKey) throws Exception {
        PostQuantumOperations.PqcAlgorithmDetectionResult publicResult =
                PostQuantumOperations.detectAlgorithmFromEncoded(publicKey.getEncoded(), true);
        PostQuantumOperations.PqcAlgorithmDetectionResult privateResult =
                PostQuantumOperations.detectAlgorithmFromEncoded(privateKey.getEncoded(), false);
        if (publicResult == null || privateResult == null
                || !publicResult.isSupported() || !privateResult.isSupported()) {
            throw new IllegalArgumentException("Public/private keys are not supported PQC keys.");
        }
        if (!publicResult.nistName().equals(privateResult.nistName())) {
            throw new IllegalArgumentException("Incompatible public/private PQC parameter sets: public key is "
                    + publicResult.nistName() + " but private key is " + privateResult.nistName() + ".");
        }

        try {
            if (isKemAlgorithm(publicResult.nistName())) {
                PostQuantumOperations.KEMResult kem = PostQuantumOperations.encapsulate(publicKey, publicResult.nistName());
                byte[] recovered = PostQuantumOperations.decapsulate(privateKey, kem.encapsulation(), publicResult.nistName());
                if (!MessageDigest.isEqual(kem.sharedSecret(), recovered)) {
                    throw new IllegalArgumentException(t("module.pqc.error.nonmatchingPair", publicResult.nistName()));
                }
            } else {
                byte[] challenge = "CryptoCarver PQC key-pair validation".getBytes(StandardCharsets.UTF_8);
                byte[] signature = PostQuantumOperations.sign(privateKey, challenge, publicResult.nistName());
                if (!PostQuantumOperations.verify(publicKey, challenge, signature, publicResult.nistName())) {
                    throw new IllegalArgumentException(t("module.pqc.error.nonmatchingPair", publicResult.nistName()));
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to validate the imported " + publicResult.nistName()
                    + " public/private key pair.", e);
        }
    }

    void handleExportPQCPublicKey() {
        exportKey(view.keyState().publicKey(), "pqc-public-key.pem", "PUBLIC KEY");
    }

    void handleExportPQCPrivateKey() {
        exportKey(view.keyState().privateKey(), "pqc-private-key.pem", "PRIVATE KEY");
    }

    private void exportKey(java.security.Key key, String filename, String pemType) {
        if (key == null) {
            if (reporter() != null) reporter().showError("PQC Export Error", "Generate or import a key first.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export PQC " + pemType);
        chooser.setInitialFileName(filename);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PEM files", "*.pem"));
        File file = chooser.showSaveDialog(null);
        if (file == null) return;
        try {
            Files.writeString(file.toPath(), toPem(pemType, key.getEncoded()), StandardCharsets.US_ASCII);
            List<OperationDetail> details = List.of(
                OperationDetail.publicDetail("Key Type", pemType),
                OperationDetail.publicDetail("Output", file.getAbsolutePath())
            );
            if (reporter() != null) {
                OperationResult.Builder result = OperationResult.forOperation("PQC Key Export")
                        .details(details)
                        .status("PQC key exported: " + file.getName());
                // A private key is written only to the explicitly selected file;
                // it must never become an inspector/history payload.
                if (!"PRIVATE KEY".equals(pemType)) {
                    result.output(key.getEncoded());
                }
                reporter().publish(result.build());
            }
        } catch (Exception e) {
            if (reporter() != null) reporter().showError("PQC Export Error", "Unable to export key: " + e.getMessage());
        }
    }

    private static String toPem(String type, byte[] encoded) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(encoded);
        return "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----\n";
    }

    private List<OperationDetail> describeKeyPair(String selectedAlgorithm, String source) {
        List<OperationDetail> details = new ArrayList<>();
        details.add(OperationDetail.publicDetail("Operation", source + " PQC key pair"));
        details.add(OperationDetail.publicDetail("Parameter Set", selectedAlgorithm));
        details.add(OperationDetail.publicDetail("Purpose", isKemAlgorithm(selectedAlgorithm) ? "Key encapsulation (ML-KEM)" : "Digital signatures"));
        PublicKey currentPublicKey = view.keyState().publicKey();
        PrivateKey currentPrivateKey = view.keyState().privateKey();
        if (currentPublicKey != null) {
            details.add(OperationDetail.publicDetail("Public Key Algorithm", currentPublicKey.getAlgorithm()));
            details.add(OperationDetail.publicDetail("Public Key Format", safeValue(currentPublicKey.getFormat())));
            details.add(OperationDetail.publicDetail("Public Key Size", currentPublicKey.getEncoded().length + " bytes"));
            details.add(OperationDetail.publicDetail("Public Key SHA-256", fingerprint(currentPublicKey.getEncoded())));
        } else {
            details.add(OperationDetail.publicDetail("Public Key", "Not available"));
        }
        if (currentPrivateKey != null) {
            details.add(OperationDetail.publicDetail("Private Key Algorithm", currentPrivateKey.getAlgorithm()));
            details.add(OperationDetail.publicDetail("Private Key Format", safeValue(currentPrivateKey.getFormat())));
            details.add(OperationDetail.publicDetail("Private Key Size", currentPrivateKey.getEncoded().length + " bytes"));
            details.add(OperationDetail.publicDetail("Private Key Handling", "Held in memory; never recorded in history"));
        } else {
            details.add(OperationDetail.publicDetail("Private Key", "Not available"));
        }
        return details;
    }

    private String formatDetails(List<OperationDetail> details) {
        StringBuilder result = new StringBuilder("PQC KEY PAIR DETAILS\n\n");
        for (OperationDetail detail : details) {
            result.append(detail.name()).append(": ").append(detail.value()).append('\n');
        }
        result.append("\nSecurity note: export a private key only to protected storage.");
        return result.toString();
    }

    private List<OperationDetail> detailsWithPublicKeyMaterial(List<OperationDetail> details) {
        List<OperationDetail> historyDetails = new ArrayList<>(details);
        PublicKey currentPublicKey = view.keyState().publicKey();
        if (currentPublicKey != null) {
            historyDetails.add(OperationDetail.publicDetail("Public Key PEM", toPem("PUBLIC KEY", currentPublicKey.getEncoded())));
        }
        return historyDetails;
    }

    private String fingerprint(byte[] encoded) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(encoded);
            String hex = DataConverter.bytesToHex(hash);
            return hex.substring(0, 16) + "…";
        } catch (Exception e) {
            return "Unavailable";
        }
    }

    private String safeValue(String value) {
        return value == null || value.isBlank() ? "Unspecified" : value;
    }

    static boolean isKemAlgorithm(String algorithm) {
        if (algorithm == null) return false;
        String normalized = algorithm.toUpperCase(java.util.Locale.ROOT);
        return normalized.startsWith("KYBER") || normalized.startsWith("ML-KEM");
    }
}
