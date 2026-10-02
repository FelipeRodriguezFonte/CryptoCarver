package com.cryptocarver.ui;

import com.cryptocarver.crypto.EBCDICConverter;
import com.cryptocarver.crypto.LineFileCipher;
import com.cryptocarver.crypto.StreamingCipher;
import com.cryptocarver.model.FileCipherRecipe;
import com.cryptocarver.model.FileCipherRecipeCodec;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.util.DataConverter;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The streaming file-cipher panel of the cipher screen: whole-file AES/ChaCha encryption with a
 * detached tag, record-by-record line mode, nonce generation and recipe import/export. The
 * encrypted-file analysis button stays in CipherController, which owns the analysis fields.
 */
final class FileCipherCoordinator {

    /** The panel's controls, injected into CipherController from cipher.fxml. */
    record View(ComboBox<String> algorithm,
            TextField source,
            TextField destination,
            TextField tag,
            TextField key,
            TextField nonce,
            TextField aad,
            TextArea result,
            CheckBox lines,
            ComboBox<String> lineEncoding,
            ComboBox<String> lineCharset,
            CheckBox compact,
            Button encrypt,
            Button decrypt) {
    }

    private final ComboBox<String> fileCipherAlgorithmCombo;
    private final TextField fileCipherSourceField;
    private final TextField fileCipherDestinationField;
    private final TextField fileCipherTagField;
    private final TextField fileCipherKeyField;
    private final TextField fileCipherNonceField;
    private final TextField fileCipherAadField;
    private final TextArea fileCipherResultArea;
    private final CheckBox fileCipherLinesCheck;
    private final ComboBox<String> fileCipherLineEncodingCombo;
    private final ComboBox<String> fileCipherLineCharsetCombo;
    private final CheckBox fileCipherCompactCbcCheck;
    private final Button fileCipherEncryptBtn;
    private final Button fileCipherDecryptBtn;
    private final Supplier<StatusReporter> reporter;
    private final DialogService dialogService;

    FileCipherCoordinator(View view, Supplier<StatusReporter> reporter, DialogService dialogService) {
        this.fileCipherAlgorithmCombo = view.algorithm();
        this.fileCipherSourceField = view.source();
        this.fileCipherDestinationField = view.destination();
        this.fileCipherTagField = view.tag();
        this.fileCipherKeyField = view.key();
        this.fileCipherNonceField = view.nonce();
        this.fileCipherAadField = view.aad();
        this.fileCipherResultArea = view.result();
        this.fileCipherLinesCheck = view.lines();
        this.fileCipherLineEncodingCombo = view.lineEncoding();
        this.fileCipherLineCharsetCombo = view.lineCharset();
        this.fileCipherCompactCbcCheck = view.compact();
        this.fileCipherEncryptBtn = view.encrypt();
        this.fileCipherDecryptBtn = view.decrypt();
        this.reporter = reporter;
        this.dialogService = dialogService;
    }

    private StatusReporter reporter() {
        return reporter.get();
    }


    /** Fills the panel's choices and keeps its line-mode controls consistent. */
    void configure() {
        fileCipherAlgorithmCombo.getItems().setAll("AES-256-GCM", "AES-256-CTR", "AES-256-CBC", "ChaCha20-Poly1305");
        fileCipherAlgorithmCombo.setValue("AES-256-GCM");
        fileCipherAlgorithmCombo.valueProperty().addListener((observable, oldValue, selected) -> updateFileCipherLineModeState());
        if (fileCipherLineEncodingCombo != null) {
            fileCipherLineEncodingCombo.getItems().setAll("Base64URL", "Hexadecimal");
            fileCipherLineEncodingCombo.setValue("Base64URL");
        }
        if (fileCipherLineCharsetCombo != null) {
            fileCipherLineCharsetCombo.getItems().setAll("UTF-8");
            fileCipherLineCharsetCombo.getItems().addAll(EBCDICConverter.supportedCodePages().keySet());
            fileCipherLineCharsetCombo.setValue("UTF-8");
        }
        if (fileCipherLinesCheck != null) {
            fileCipherLinesCheck.selectedProperty().addListener((observable, oldValue, selected) -> updateFileCipherLineModeState());
            updateFileCipherLineModeState();
        }
    }

    void handleFileCipherEncrypt() {
        executeFileCipher(true);
    }

    void handleFileCipherDecrypt() {
        executeFileCipher(false);
    }

    void handleExportFileCipherRecipe() {
        try {
            String algorithm = fileCipherAlgorithmCombo.getValue();
            boolean linesMode = fileCipherLinesCheck != null && fileCipherLinesCheck.isSelected();
            String lineEncoding = fileCipherLineEncodingCombo != null ? fileCipherLineEncodingCombo.getValue() : null;
            String charset = fileCipherLineCharsetCombo != null ? fileCipherLineCharsetCombo.getValue() : null;
            boolean compactMode = fileCipherCompactCbcCheck != null && fileCipherCompactCbcCheck.isSelected();
            String aadHex = (fileCipherAadField != null && !fileCipherAadField.getText().trim().isEmpty()) ? fileCipherAadField.getText().trim() : null;
            String ivNonceHex = (fileCipherNonceField != null && !fileCipherNonceField.getText().trim().isEmpty()) ? fileCipherNonceField.getText().trim() : null;
            String tagRef = (fileCipherTagField != null && !fileCipherTagField.getText().trim().isEmpty()) ? fileCipherTagField.getText().trim() : null;

            RecipeUIHelper.RecipeUIState state = new RecipeUIHelper.RecipeUIState(
                    algorithm, linesMode, lineEncoding, compactMode, charset, aadHex, ivNonceHex, tagRef
            );

            FileCipherRecipe recipe = RecipeUIHelper.buildRecipeForExport(state);

            // Validar antes de pedir el path para abortar si falta algo
            String json = FileCipherRecipeCodec.serialize(recipe);

            // Security warning
            if (RecipeUIHelper.requiresSecurityWarning(recipe)
                    && LabPrompt.FILE_CIPHER_RECIPE.shouldShow()) {
                dialogService.warning("Advertencia de Seguridad", "Exportando IV/Nonce o AAD\n\nEl archivo de receta contendrá el IV/Nonce o AAD.\n" +
                        "La clave secreta NUNCA se exportará.\n" +
                        "(Reusar un IV/Nonce con la misma clave en modo fichero o CBC compromete la seguridad).");
            }

            FileChooser chooser = new FileChooser();
            chooser.setTitle("Guardar Receta File Cipher");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Recipe", "*.json"));
            java.io.File dest = chooser.showSaveDialog(null);
            if (dest != null) {
                Files.writeString(dest.toPath(), json);
                reporter().updateStatus("Receta exportada a " + dest.getName());
            }
        } catch (Exception e) {
            dialogService.error("Error de Exportación", "No se pudo exportar la receta\n\n" + e.getMessage());
        }
    }

    void handleImportFileCipherRecipe() {
        try {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Abrir Receta File Cipher");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Recipe", "*.json"));
            java.io.File source = chooser.showOpenDialog(null);
            if (source != null) {
                String json = Files.readString(source.toPath());
                FileCipherRecipe recipe = FileCipherRecipeCodec.deserialize(json);

                // Pre-calculate tag path to ensure atomicity
                String currentTag = fileCipherTagField != null ? fileCipherTagField.getText() : null;
                String newTagPath = RecipeUIHelper.calculateLocalTagPath(currentTag, recipe.getTagRef());

                // Update UI atomically after parsing and pre-calculating successfully
                if (recipe.getAlgorithm() != null && fileCipherAlgorithmCombo != null) {
                    fileCipherAlgorithmCombo.setValue(recipe.getAlgorithm());
                }
                if (fileCipherLinesCheck != null) {
                    fileCipherLinesCheck.setSelected(recipe.isLinesMode());
                }
                if (fileCipherLineEncodingCombo != null && recipe.getLineEncoding() != null) {
                    fileCipherLineEncodingCombo.setValue(recipe.getLineEncoding());
                }
                if (fileCipherLineCharsetCombo != null && recipe.getCharset() != null) {
                    fileCipherLineCharsetCombo.setValue(recipe.getCharset());
                }
                if (fileCipherCompactCbcCheck != null) {
                    fileCipherCompactCbcCheck.setSelected(recipe.isCompactMode());
                }
                if (fileCipherAadField != null) {
                    fileCipherAadField.setText(recipe.getAadHex() == null ? "" : recipe.getAadHex());
                }
                if (fileCipherNonceField != null) {
                    fileCipherNonceField.setText(recipe.getIvNonceHex() == null ? "" : recipe.getIvNonceHex());
                }
                if (fileCipherTagField != null && newTagPath != null) {
                    fileCipherTagField.setText(newTagPath);
                }
                updateFileCipherLineModeState();

                boolean isAeadLines = recipe.isLinesMode() &&
                        ("AES-256-GCM".equals(recipe.getAlgorithm()) || "ChaCha20-Poly1305".equals(recipe.getAlgorithm()));

                dialogService.info("Receta Importada", "Receta v" + recipe.getVersion() + " cargada con éxito\n\nAlgoritmo: " + recipe.getAlgorithm() +
                        "\nModo Líneas: " + recipe.isLinesMode() +
                        (isAeadLines ? " (cada registro generará su propio nonce/tag)" : "") +
                        "\nFormato: " + (recipe.getLineEncoding() != null ? recipe.getLineEncoding() : "N/A") +
                        "\nLa Clave Secreta y Rutas de Fichero NO fueron sobrescritas.");
            }
        } catch (Exception e) {
            dialogService.error("Error de Importación", "No se pudo importar la receta\n\n" + e.getMessage());
        }
    }

    private void updateFileCipherLineModeState() {
        boolean lineMode = fileCipherLinesCheck != null && fileCipherLinesCheck.isSelected();
        boolean cbcLineMode = lineMode && "AES-256-CBC".equals(fileCipherAlgorithmCombo.getValue());
        fileCipherNonceField.setDisable(lineMode && !cbcLineMode);
        fileCipherTagField.setDisable(lineMode);
        if (fileCipherLineEncodingCombo != null) fileCipherLineEncodingCombo.setDisable(!lineMode);
        if (fileCipherLineCharsetCombo != null) fileCipherLineCharsetCombo.setDisable(!lineMode);
        if (fileCipherCompactCbcCheck != null) {
            fileCipherCompactCbcCheck.setDisable(!lineMode);
        }
    }

    void chooseFileCipherSource() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select source file");
        java.io.File selected = chooser.showOpenDialog(null);
        if (selected != null) fileCipherSourceField.setText(selected.getAbsolutePath());
    }

    void chooseFileCipherDestination() {
        chooseSavePath(fileCipherDestinationField, "Save encrypted/decrypted file", "output.bin");
    }

    void chooseFileCipherTag() {
        chooseSavePath(fileCipherTagField, "Save/load detached AEAD tag", "output.tag");
    }

    void generateFileCipherNonce() {
        if (fileCipherAlgorithmCombo == null) return;
        String selected = fileCipherAlgorithmCombo.getValue();
        int length = selected != null && (selected.contains("GCM") || selected.startsWith("ChaCha")) ? 12 : 16;
        byte[] nonce = new byte[length];
        new java.security.SecureRandom().nextBytes(nonce);
        fileCipherNonceField.setText(DataConverter.bytesToHex(nonce));
        reporter().updateStatus("Generated fresh " + length + "-byte IV/nonce for file cipher");
    }

    private record FileCipherExecutionResult(long inputBytes, long outputBytes, Long lines, boolean lineMode) {}

    private void executeFileCipher(boolean encrypt) {
        try {
            FileCipherInputs.Parameters parameters = readFileCipherParameters();
            java.nio.file.Path source = java.nio.file.Path.of(fileCipherSourceField.getText().trim());
            java.nio.file.Path destination = java.nio.file.Path.of(fileCipherDestinationField.getText().trim());
            if (!java.nio.file.Files.isRegularFile(source)) throw new IllegalArgumentException("Source file does not exist or is not a regular file");
            if (source.toAbsolutePath().normalize().equals(destination.toAbsolutePath().normalize())) {
                throw new IllegalArgumentException("Source and output file must be different");
            }
            boolean lineMode = fileCipherLinesCheck != null && fileCipherLinesCheck.isSelected();
            java.nio.file.Path tag = parameters.aead() && !lineMode ? FileCipherInputs.requiredPath(fileCipherTagField.getText(), "Tag file path") : null;
            if (!encrypt && parameters.aead() && !lineMode && !java.nio.file.Files.isRegularFile(tag)) {
                throw new IllegalArgumentException("Detached tag file does not exist");
            }

            String algo = fileCipherAlgorithmCombo != null ? fileCipherAlgorithmCombo.getValue() : parameters.algorithm();
            LineFileCipher.Encoding encoding = lineEncoding();
            java.nio.charset.Charset charset = lineCharset();
            boolean compact = compactLineOutput();
            Button triggerBtn = encrypt ? fileCipherEncryptBtn : fileCipherDecryptBtn;

            java.util.UUID sessionUuid = java.util.UUID.randomUUID();
            java.nio.file.Path stagingDest = destination.resolveSibling("." + destination.getFileName() + ".stage." + sessionUuid);
            java.nio.file.Path stagingTag = (encrypt && tag != null) ? tag.resolveSibling("." + tag.getFileName() + ".stage." + sessionUuid) : null;

            String opName = encrypt ? "Encrypting file" : "Decrypting file";
            OperationExecutor.ProgressTask<FileCipherExecutionResult> progressTask = monitor -> {
                FileCipherExecutionResult res;
                if (lineMode) {
                    LineFileCipher.Result r = encrypt
                            ? LineFileCipher.encrypt(source, stagingDest, parameters.key(), algo, parameters.aad(), encoding, parameters.nonce(), charset, compact, monitor)
                            : LineFileCipher.decrypt(source, stagingDest, parameters.key(), algo, parameters.aad(), parameters.nonce(), encoding, charset, monitor);
                    res = new FileCipherExecutionResult(r.inputBytes(), r.outputBytes(), r.lines(), true);
                } else {
                    StreamingCipher.Result r = encrypt
                            ? StreamingCipher.encrypt(source, stagingDest, parameters.key(), parameters.algorithm(), parameters.mode(),
                                    parameters.nonce(), parameters.aad(), stagingTag, monitor)
                            : StreamingCipher.decrypt(source, stagingDest, parameters.key(), parameters.algorithm(), parameters.mode(),
                                    parameters.nonce(), parameters.aad(), tag, monitor);
                    res = new FileCipherExecutionResult(r.inputBytes(), r.outputBytes(), null, false);
                }

                if (monitor.isCancelled() || Thread.currentThread().isInterrupted()) {
                    FileCipherPromotion.cleanupStaging(stagingDest, stagingTag);
                    throw new java.util.concurrent.CancellationException("File cipher operation cancelled");
                }

                // Transactional promotion with atomic enterCommitPhase check
                java.util.function.BooleanSupplier enterCommitCheck = () -> {
                    if (reporter() instanceof ModernMainController mc && mc.getOperationExecutor() != null) {
                        return mc.getOperationExecutor().enterCommitPhase();
                    }
                    return !monitor.isCancelled() && !Thread.currentThread().isInterrupted();
                };

                FileCipherPromotion.promote(stagingDest, stagingTag, destination, tag, encrypt, sessionUuid.toString(), enterCommitCheck);

                return res;
            };

            Consumer<FileCipherExecutionResult> onSuccess = res -> {
                try {
                    publishFileCipherResult(encrypt, parameters, tag, res.inputBytes(), res.outputBytes(), res.lines(), res.lineMode());
                } catch (Exception e) {
                    if (reporter() != null) reporter().showError("File Cipher", "Cannot process file: " + e.getMessage());
                }
            };

            Consumer<Throwable> onFailure = err -> {
                if (reporter() != null) {
                    reporter().showError("File Cipher", "Cannot process file: " + (err != null ? err.getMessage() : "Unknown error"));
                }
            };

            Runnable onCancelled = () -> {
                if (reporter() != null) {
                    reporter().updateStatus(com.cryptocarver.service.I18nService.getInstance().text("module.cipher.cancelled"));
                }
            };

            if (reporter() instanceof ModernMainController mc && mc.getOperationExecutor() != null) {
                mc.getOperationExecutor().executeWithProgress(opName, triggerBtn, progressTask, onSuccess, onFailure, onCancelled);
            } else {
                com.cryptocarver.util.ProgressMonitor noOpMonitor = new com.cryptocarver.util.ProgressMonitor() {
                    @Override public void updateProgress(long b, long t) {}
                    @Override public boolean isCancelled() { return Thread.currentThread().isInterrupted(); }
                };
                FileCipherExecutionResult res = progressTask.run(noOpMonitor);
                onSuccess.accept(res);
            }
        } catch (Exception e) {
            if (reporter() != null) {
                reporter().showError("File Cipher", "Cannot process file: " + e.getMessage());
            }
        }
    }

    private LineFileCipher.Encoding lineEncoding() {
        return fileCipherLineEncodingCombo != null && "Hexadecimal".equals(fileCipherLineEncodingCombo.getValue())
                ? LineFileCipher.Encoding.HEXADECIMAL : LineFileCipher.Encoding.BASE64URL;
    }

    private java.nio.charset.Charset lineCharset() {
        String selected = fileCipherLineCharsetCombo == null ? "UTF-8" : fileCipherLineCharsetCombo.getValue();
        if (selected == null || "UTF-8".equals(selected)) return StandardCharsets.UTF_8;
        String codePage = EBCDICConverter.supportedCodePages().get(selected);
        if (codePage == null) throw new IllegalArgumentException("Unsupported text encoding: " + selected);
        return java.nio.charset.Charset.forName(codePage);
    }

    private boolean compactLineOutput() {
        return fileCipherCompactCbcCheck != null && fileCipherCompactCbcCheck.isSelected();
    }

    private void publishFileCipherResult(boolean encrypt, FileCipherInputs.Parameters parameters, java.nio.file.Path tag,
                                         long inputBytes, long outputBytes, Long lines, boolean lineMode) {
            String operation = encrypt ? "encrypted" : "decrypted";
            String enrichedOutputText = "File " + operation + " successfully\nAlgorithm: " + fileCipherAlgorithmCombo.getValue()
                    + "\nInput: " + inputBytes + " bytes\nOutput: " + outputBytes + " bytes"
                    + (lineMode ? "\nRecords: " + lines + " (independently authenticated)" : parameters.aead() ? "\nAEAD tag: " + tag : "");
            fileCipherResultArea.setText(enrichedOutputText);

            java.util.Map<String, String> details = new java.util.HashMap<>();
            details.put("Algorithm", fileCipherAlgorithmCombo.getValue());
            details.put("Input bytes", Long.toString(inputBytes));
            details.put("Output bytes", Long.toString(outputBytes));
            details.put("Authenticated", Boolean.toString(lineMode || parameters.aead()));
            if (lineMode) details.put("Records", Long.toString(lines));
            reporter().publish(OperationResult.forOperation("File " + (encrypt ? "Encrypt" : "Decrypt"))
                    .enrichedOutput(enrichedOutputText)
                    .details(details).status("File " + operation + " using " + fileCipherAlgorithmCombo.getValue()).build());
    }

    private void chooseSavePath(TextField target, String title, String initialFileName) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.setInitialFileName(initialFileName);
        java.io.File selected = chooser.showSaveDialog(null);
        if (selected != null) target.setText(selected.getAbsolutePath());
    }

    private FileCipherInputs.Parameters readFileCipherParameters() {
        boolean lineMode = fileCipherLinesCheck != null && fileCipherLinesCheck.isSelected();
        return FileCipherInputs.parameters(fileCipherAlgorithmCombo.getValue(), fileCipherKeyField.getText(),
                lineMode, fileCipherNonceField.getText(), fileCipherAadField.getText());
    }
}
