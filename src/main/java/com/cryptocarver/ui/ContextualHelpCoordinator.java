package com.cryptocarver.ui;

import com.cryptocarver.model.OperationDescriptor;
import com.cryptocarver.model.OperationFormatProfile;
import com.cryptocarver.model.OperationFormatRegistry;
import com.cryptocarver.model.OperationRegistry;
import com.cryptocarver.model.help.ActionHelp;
import com.cryptocarver.model.help.HelpContext;
import com.cryptocarver.model.help.HelpContextSource;
import com.cryptocarver.model.help.OperationHelpDefinition;
import com.cryptocarver.model.help.OperationHelpRegistry;
import com.cryptocarver.model.help.PreflightHelpCatalog;
import com.cryptocarver.model.help.SymmetricCapabilityPolicy;
import com.cryptocarver.model.help.FieldHelp;
import com.cryptocarver.model.help.OptionHelp;
import com.cryptocarver.service.I18nService;
import com.cryptocarver.service.LocalHelpService;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Renders local help from stable operation/action IDs; it never reads form values. */
public final class ContextualHelpCoordinator {
    private final VBox content;
    private final I18nService i18n;
    private final HelpContextSource contextSource;
    private final Consumer<HelpContext> contextListener;
    private final LocalHelpService localHelp = new LocalHelpService();
    private HelpContext context;
    private Consumer<String> actionSelectionListener = ignored -> { };

    public ContextualHelpCoordinator(VBox content, I18nService i18n, HelpContextSource contextSource) {
        this.content = content;
        this.i18n = i18n;
        this.contextSource = contextSource;
        this.context = contextSource.current();
        this.contextListener = next -> { context = next; render(); };
        contextSource.addListener(contextListener);
        if (content != null) content.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (!(event.getTarget() instanceof Button button) || button.getParent() != content) return;
            if (event.getCode() != KeyCode.ENTER && event.getCode() != KeyCode.SPACE) return;
            Object action = button.getUserData();
            if (action instanceof String id && !id.isBlank()) {
                event.consume();
                selectAction(id);
            }
        });
    }

    public void showOperation(String navigationPath) {
        OperationDescriptor descriptor = OperationRegistry.getInstance().getByNavigationPath(navigationPath)
                .or(() -> OperationRegistry.getInstance().getById(navigationPath)).orElse(null);
        contextSource.setOperation(descriptor == null ? "unknown" : descriptor.getId());
    }

    public void showAction(String id) {
        if (!isAction(id)) return;
        contextSource.setAction(id);
    }

    private boolean isAction(String id) {
        return id != null && OperationHelpRegistry.getInstance().find(context.operationId())
                .stream().flatMap(definition -> definition.actions().stream())
                .anyMatch(action -> action.id().equals(id));
    }

    private void focusAction(String id) {
        if (content != null) content.getChildren().stream()
                .filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> id.equals(button.getUserData())).findFirst()
                .ifPresent(Button::requestFocus);
    }

    public void setActionSelectionListener(Consumer<String> listener) {
        actionSelectionListener = listener == null ? ignored -> { } : listener;
    }

    private void selectAction(String id) {
        if (!isAction(id)) return;
        contextSource.setAction(id);
        focusAction(id);
        actionSelectionListener.accept(id);
    }

    public void refresh() { context = contextSource.current(); render(); }
    public Node view() { return content; }
    public String operationId() { return context.operationId(); }
    public String actionId() { return context.actionId(); }
    public HelpContext currentContext() { return context; }
    public void close() { contextSource.removeListener(contextListener); }
    public String operationTitle() {
        String key = "help.operation." + context.operationId() + ".title";
        String localized = text(key);
        if (!localized.equals(key)) return localized;
        return OperationRegistry.getInstance().getById(context.operationId())
                .map(OperationDescriptor::getTitle).orElse(text("help.title"));
    }

    private void render() {
        if (content == null) return;
        content.getChildren().clear();
        content.setPadding(new Insets(12));
        OperationHelpDefinition definition = OperationHelpRegistry.getInstance().resolve(context);
        Label title = new Label(operationTitle());
        title.getStyleClass().add("inspector-section-title");
        title.setWrapText(true);
        content.getChildren().add(title);
        Label purpose = new Label(text(definition.purposeKey()));
        purpose.setWrapText(true);
        content.getChildren().add(purpose);

        if ("aux_global_formats".equals(context.operationId())) {
            renderGlobalFormatContext();
            return;
        }

        if (!definition.actions().isEmpty()) {
            Label actionsHeading = new Label(text("help.actions"));
            actionsHeading.getStyleClass().add("inspector-label-small");
            content.getChildren().add(actionsHeading);
            for (ActionHelp action : definition.actions()) {
                if ("op_gen_process_designer".equals(context.operationId())
                        && action.id().startsWith("node_") && !action.id().equals(context.actionId())) continue;
                Button button = new Button(text(action.titleKey()));
                button.setUserData(action.id());
                button.setId("help-action-" + action.id());
                button.setMaxWidth(Double.MAX_VALUE);
                button.setAccessibleHelp(text(action.descriptionKey()));
                button.setOnAction(event -> selectAction(action.id()));
                content.getChildren().add(button);
            }
        }
        ActionHelp selected = definition.actions().stream()
                .filter(action -> action.id().equals(context.actionId())).findFirst().orElse(null);
        if (selected != null) {
            addLocalizedParagraph(text(selected.descriptionKey()));
            if (!selected.exampleKey().isBlank()) addLocalizedParagraph(text(selected.exampleKey()));
            renderActionFields(selected);
            renderActionOptions(selected);
            if ("op_gen_batch".equals(context.operationId())) renderSelectedBatchCase(selected);
        }
        if ("op_sym_ciphers".equals(context.operationId())) renderSymmetricApplicability();
        if ("op_jose_jwe".equals(context.operationId())) renderJweApplicability();
        if ("op_pay_clear_pin".equals(context.operationId()) || "op_pay_enc_pin".equals(context.operationId()))
            renderPinBlockApplicability();
        if ("op_fpe".equals(context.operationId())) renderFpeApplicability();
        if ("op_sym_file".equals(context.operationId())) renderFileCipherApplicability();
        if ("op_asym_ciphers".equals(context.operationId())) renderRsaApplicability();
        if ("op_openpgp".equals(context.operationId())) renderOpenPgpApplicability();
        if ("aux_encrypted_file_analysis".equals(context.operationId())) renderEncryptedFileAnalysisApplicability();
        if (!definition.complete()) {
            Label incomplete = new Label(text("help.detailUnavailable"));
            incomplete.setWrapText(true);
            incomplete.getStyleClass().add("text-muted");
            content.getChildren().add(incomplete);
        }
        if (!definition.referenceId().isBlank()) {
            localHelp.readReference(definition.referenceId(), i18n.getLocale()).ifPresent(referenceText -> {
                Label reference = new Label(referenceText.strip());
                reference.setWrapText(true);
                // A long wrapped text must keep its full height; the scroll pane scrolls instead of eliding it.
                reference.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
                content.getChildren().add(reference);
            });
        }
        if ("aux_preflight_error".equals(context.operationId())) renderPreflightContext();
    }

    private void renderActionFields(ActionHelp action) {
        if (action.fields().isEmpty()) return;
        List<FieldHelp> fields = action.fields();
        if (!context.fieldId().isBlank()) {
            fields = fields.stream().filter(field -> field.id().equals(context.fieldId())).toList();
            Button back = new Button(text("help.backToOperation"));
            back.setAccessibleHelp(text("help.backToOperation.help"));
            back.setOnAction(event -> {
                contextSource.replace(context.withAction("").withField(""));
                actionSelectionListener.accept("");
            });
            content.getChildren().add(back);
        }
        if (fields.isEmpty()) return;
        Label heading = new Label(text(context.fieldId().isBlank() ? "help.fields" : "help.field.details"));
        heading.getStyleClass().add("inspector-label-small");
        content.getChildren().add(heading);
        for (FieldHelp field : fields) {
            addLocalizedParagraph(text(field.labelKey()) + ": " + text(field.descriptionKey()));
            if (field.ruleId().startsWith("help.node.condition.")) {
                addLocalizedParagraph(text(field.ruleId()));
            }
        }
    }

    private void renderActionOptions(ActionHelp action) {
        if (action.options().isEmpty()) return;
        List<OptionHelp> options = action.options();
        if (!context.fieldId().isBlank()) {
            options = options.stream().filter(option -> context.fieldId().equals(option.fieldId())).toList();
        }
        if (options.isEmpty()) return;
        Label heading = new Label(text("help.options"));
        heading.getStyleClass().add("inspector-label-small");
        content.getChildren().add(heading);
        String previousField = "";
        ActionHelp selectedAction = OperationHelpRegistry.getInstance().find(context.operationId())
                .flatMap(definition -> definition.actions().stream()
                        .filter(candidate -> candidate.id().equals(context.actionId())).findFirst())
                .orElse(null);
        for (OptionHelp option : options) {
            if (!option.fieldId().equals(previousField) && context.fieldId().isBlank()) {
                previousField = option.fieldId();
                String fieldId = previousField;
                String labelKey = selectedAction == null ? "" : selectedAction.fields().stream()
                        .filter(field -> field.id().equals(fieldId)).map(FieldHelp::labelKey).findFirst().orElse("");
                if (labelKey.isBlank()) labelKey = "help.field." + context.operationId() + "." + previousField + ".label";
                Label group = new Label(text(labelKey));
                group.getStyleClass().add("inspector-label-small");
                content.getChildren().add(group);
            }
            addLocalizedParagraph(option.code() + ": " + text(option.descriptionKey()));
        }
    }

    private void renderSelectedBatchCase(ActionHelp action) {
        String selected = context.selections().getOrDefault(HelpContext.Selection.BATCH_OPERATION,
                HelpContext.PublicSelection.of("unknown")).code();
        List<OptionHelp> batchOperations = action.options().stream().anyMatch(candidate -> !candidate.exampleKey().isBlank())
                ? action.options()
                : OperationHelpRegistry.getInstance().find("op_gen_batch")
                        .stream().flatMap(definition -> definition.actions().stream())
                        .filter(candidate -> candidate.id().equals("select_operation"))
                        .flatMap(candidate -> candidate.options().stream()).toList();
        OptionHelp option = batchOperations.stream()
                .filter(candidate -> !candidate.exampleKey().isBlank())
                .filter(candidate -> selected.equals(candidate.code().trim().toUpperCase(java.util.Locale.ROOT)
                        .replaceAll("[^A-Z0-9_.:-]+", "_")))
                .findFirst().orElse(null);
        if (option == null) return;
        addLocalizedParagraph(text(option.exampleKey()));
        addLocalizedParagraph(text(option.errorKey()));
    }

    private void renderGlobalFormatContext() {
        String profileId = context.selections().getOrDefault(HelpContext.Selection.PROFILE,
                HelpContext.PublicSelection.of("unknown")).code();
        OperationDescriptor descriptor = OperationRegistry.getInstance().getById(profileId).orElse(null);
        String route = descriptor == null ? profileId : descriptor.getNavigationPath();
        OperationFormatProfile profile = OperationFormatRegistry.getInstance().getProfile(route);
        String routeKey = com.cryptocarver.model.help.OperationHelpRegistry.slug(profile.operationPath());
        String action = context.actionId();
        String fieldId = context.fieldId();
        String actionPrefix = "help.aux.global_formats.action." + (action.isBlank() ? "input" : action);
        if ("input".equals(action) || "output".equals(action) || "contract".equals(action)) {
            addLocalizedParagraph(text(actionPrefix + ".description"));
            addLocalizedParagraph(text(actionPrefix + ".example"));
        }
        if ("contract".equals(action) || "contractOperationLabel".equals(fieldId)) {
            addLocalizedParagraph(text("help.format.contract." + routeKey));
            addLocalizedParagraph(text("help.format.type.input." + profile.inputDataType().name().toLowerCase(java.util.Locale.ROOT))
                    + " → " + text("help.format.type.output." + profile.outputDataType().name().toLowerCase(java.util.Locale.ROOT)));
            addLocalizedParagraph(text("help.format.selection.memory"));
            return;
        }
        boolean input = "input".equals(action) || "inputFormatCombo".equals(fieldId);
        String direction = input ? "input" : "output";
        String labelKey = input ? "help.aux.global_formats.field.input.label" : "help.aux.global_formats.field.output.label";
        addLocalizedParagraph(text(labelKey) + ": " + text(input
                ? "help.aux.global_formats.field.input.description"
                : "help.aux.global_formats.field.output.description"));
        List<String> allowed = input ? profile.allowedInputFormats() : profile.allowedOutputFormats();
        if (allowed.isEmpty()) {
            addLocalizedParagraph(text("help.format.disabled." + routeKey));
            addLocalizedParagraph(text("help.format.type.input." + profile.inputDataType().name().toLowerCase(java.util.Locale.ROOT))
                    + " → " + text("help.format.type.output." + profile.outputDataType().name().toLowerCase(java.util.Locale.ROOT)));
            return;
        }
        HelpContext.PublicSelection selected = context.selections().get(HelpContext.Selection.FORMAT);
        if (selected != null) {
            String selectedLabel = allowed.stream().filter(code -> com.cryptocarver.model.help.OperationHelpRegistry
                    .slug(code).equals(selected.code())).findFirst().orElse("");
            if (!selectedLabel.isBlank()) {
                addLocalizedParagraph(text("help.format.selected") + ": " + selectedLabel + " — "
                        + formatOptionDescription(routeKey, selectedLabel, direction));
            }
        }
        Label heading = new Label(text("help.format.availableOptions"));
        heading.getStyleClass().add("inspector-label-small");
        content.getChildren().add(heading);
        for (String code : allowed) {
            addLocalizedParagraph(code + ": " + formatOptionDescription(routeKey, code, direction));
        }
        addLocalizedParagraph(text("help.format.selection.memory"));
    }

    private String formatOptionDescription(String profile, String format, String direction) {
        String specificKey = "help.format.profile." + profile + ".option."
                + OperationHelpRegistry.slug(format) + "." + direction;
        java.util.ResourceBundle bundle = i18n.getBundle();
        if (bundle != null && bundle.containsKey(specificKey)) return text(specificKey);
        return text("help.format.option." + OperationHelpRegistry.slug(format) + "." + direction);
    }

    private void renderSymmetricApplicability() {
        String algorithm = resolveChoice(HelpContext.Selection.ALGORITHM, SymmetricCapabilityPolicy.algorithms(), "AES-256");
        String mode = resolveChoice(HelpContext.Selection.MODE, SymmetricCapabilityPolicy.modes(), "CBC");
        var policy = SymmetricCapabilityPolicy.resolve(algorithm, mode);
        addLocalizedParagraph(text("help.symmetric.current") + ": " + policy.algorithm() + " / " + policy.mode());
        addLocalizedParagraph(text("help.rule." + policy.modeRuleId()));
        addLocalizedParagraph(text("help.rule." + policy.paddingRuleId()));
        addLocalizedParagraph(text("help.rule." + policy.ivRuleId())
                + (policy.ivApplicable() ? " " + text("help.symmetric.iv.recommended") + ": " + policy.generatedIvBytes() + " " + text("help.unit.bytes") : ""));
        addLocalizedParagraph(text("help.rule." + policy.authenticationRuleId()));
        addLocalizedParagraph(text("help.rule." + policy.aadRuleId()));
        if ("manual_key".equals(context.fieldId()) || "key_lab_key".equals(context.fieldId())) {
            boolean present = Boolean.parseBoolean(context.selections()
                    .getOrDefault(HelpContext.Selection.MATERIAL_PRESENT, HelpContext.PublicSelection.flag(false)).code());
            addLocalizedParagraph(text(present ? "help.symmetric.key.present" : "help.symmetric.key.absent"));
        }
    }

    private void renderJweApplicability() {
        boolean generatorContext = List.of("encrypt", "choose_serialization", "select_recipients")
                .contains(context.actionId());
        if (generatorContext) {
            String algorithm = selectedChoice(HelpContext.Selection.JWE_KEY_ALGORITHM,
                    com.cryptocarver.crypto.JweComposer.KEY_ALGORITHMS);
            String contentEncryption = selectedChoice(HelpContext.Selection.JWE_CONTENT_ENCRYPTION,
                    com.cryptocarver.crypto.JweComposer.CONTENT_ALGORITHMS);
            String serialization = selectedChoice(HelpContext.Selection.JWE_SERIALIZATION,
                    java.util.Arrays.stream(com.cryptocarver.crypto.JweComposer.Serialization.values())
                            .map(com.cryptocarver.crypto.JweComposer.Serialization::label).toList());
            String compression = selectedCode(HelpContext.Selection.JWE_COMPRESSION);
            if (!algorithm.isBlank()) addLocalizedParagraph(text("help.jwe.current.algorithm") + ": " + algorithm);
            if (!contentEncryption.isBlank()) addLocalizedParagraph(text("help.jwe.current.contentEncryption") + ": " + contentEncryption);
            if (!serialization.isBlank()) addLocalizedParagraph(text("help.jwe.current.serialization") + ": " + serialization);
            if (!compression.isBlank()) addLocalizedParagraph(text("help.jwe.current.compression") + ": "
                    + text("help.jwe.compression." + compression));

            String normalizedAlgorithm = OperationHelpRegistry.slug(algorithm);
            String normalizedSerialization = OperationHelpRegistry.slug(serialization);
            if (normalizedAlgorithm.startsWith("pbes2_")) {
                addLocalizedParagraph(text("help.jwe.rule.pbes2"));
            } else if (normalizedAlgorithm.endsWith("gcmkw")) {
                addLocalizedParagraph(text("help.jwe.rule.gcmkw"));
            } else if (normalizedAlgorithm.startsWith("ecdh_es")) {
                addLocalizedParagraph(text("help.jwe.rule.ecdh"));
            } else if ("dir".equals(normalizedAlgorithm)) {
                int bytes = requiredCekBytes(contentEncryption);
                addLocalizedParagraph(bytes > 0
                        ? text("help.jwe.rule.direct.bytes", bytes, bytes * 8)
                        : text("help.jwe.rule.direct.generic"));
            }
            if ("compact".equals(normalizedSerialization)) {
                addLocalizedParagraph(text(isSelected(HelpContext.Selection.JWE_AAD_PRESENT)
                        ? "help.jwe.rule.serialization.compact.aad" : "help.jwe.rule.serialization.compact"));
            } else if ("flattened_json".equals(normalizedSerialization)) {
                addLocalizedParagraph(text("help.jwe.rule.serialization.flattened"));
            } else if ("general_json".equals(normalizedSerialization)) {
                addLocalizedParagraph(text("help.jwe.rule.serialization.general"));
            }
        }
        if ("public_key".equals(context.fieldId())) {
            addLocalizedParagraph(text(isSelected(HelpContext.Selection.JWE_PUBLIC_MATERIAL_PRESENT)
                    ? "help.jwe.material.public.present" : "help.jwe.material.public.absent"));
        } else if ("private_key".equals(context.fieldId())) {
            addLocalizedParagraph(text(isSelected(HelpContext.Selection.JWE_PRIVATE_MATERIAL_PRESENT)
                    ? "help.jwe.material.private.present" : "help.jwe.material.private.absent"));
        } else if ("payload".equals(context.fieldId())) {
            addLocalizedParagraph(text(isSelected(HelpContext.Selection.JWE_PAYLOAD_PRESENT)
                    ? "help.jwe.material.payload.present" : "help.jwe.material.payload.absent"));
        }
    }

    private void renderPinBlockApplicability() {
        boolean clear = "op_pay_clear_pin".equals(context.operationId());
        HelpContext.Selection selection = clear
                ? ("decode".equals(context.actionId()) ? HelpContext.Selection.CLEAR_PIN_DECODE_FORMAT
                        : HelpContext.Selection.CLEAR_PIN_ENCODE_FORMAT)
                : HelpContext.Selection.ENCRYPTED_PIN_FORMAT;
        HelpContext.PublicSelection selectedValue = context.selections().get(selection);
        String selected = selectedValue == null ? "" : selectedValue.code();
        com.cryptocarver.crypto.PinBlockFormat format = java.util.Arrays.stream(com.cryptocarver.crypto.PinBlockFormat.values())
                .filter(candidate -> com.cryptocarver.model.help.OperationHelpRegistry.slug(candidate.displayName()).equals(selected))
                .findFirst().orElse(null);
        if (format != null) {
            addLocalizedParagraph(text("help.pin.currentFormat") + ": " + format.displayName());
            String key = "help.pin.format." + com.cryptocarver.model.help.OperationHelpRegistry.slug(format.displayName());
            addLocalizedParagraph(text(key + ".pan"));
            addLocalizedParagraph(text(key + ".block"));
            if (clear) {
                if ("decode".equals(context.actionId())) {
                    addLocalizedParagraph(text("help.pin.clear.padding.decode"));
                } else if (!format.paddingOptions().isEmpty()) {
                    addLocalizedParagraph(text(key + ".padding") + " " + format.paddingOptions().stream()
                            .map(code -> code + " — " + text("help.pin.padding." + com.cryptocarver.model.help.OperationHelpRegistry.slug(code)))
                            .collect(java.util.stream.Collectors.joining("; ")));
                    String selectedPadding = context.selections().getOrDefault(HelpContext.Selection.CLEAR_PIN_PADDING,
                            HelpContext.PublicSelection.of("none")).code();
                    String paddingCode = format.paddingOptions().stream()
                            .filter(code -> com.cryptocarver.model.help.OperationHelpRegistry.slug(code).equals(selectedPadding))
                            .findFirst().orElse("");
                    if (!paddingCode.isBlank()) addLocalizedParagraph(text("help.pin.selectedPadding") + ": " + paddingCode + " — "
                            + text("help.pin.padding." + com.cryptocarver.model.help.OperationHelpRegistry.slug(paddingCode)));
                } else addLocalizedParagraph(text(key + ".fixedPadding"));
            } else if ("decode".equals(context.actionId())) {
                addLocalizedParagraph(text("help.pin.encrypted.padding.decode"));
            } else {
                String effectivePadding = format.defaultPadding();
                if (effectivePadding != null) {
                    addLocalizedParagraph(text("help.pin.encrypted.padding.default") + ": " + effectivePadding + " — "
                            + text("help.pin.padding." + com.cryptocarver.model.help.OperationHelpRegistry.slug(effectivePadding)));
                } else addLocalizedParagraph(text(key + ".fixedPadding"));
                addLocalizedParagraph(text("help.pin.encrypted.padding.notConfigurable"));
            }
            if (!clear) {
                addLocalizedParagraph(text("help.pin.encryption." + (format == com.cryptocarver.crypto.PinBlockFormat.ISO4 ? "aes" : "tdes")));
                if (format == com.cryptocarver.crypto.PinBlockFormat.ISO4 && "protect".equals(context.actionId()))
                    addLocalizedParagraph(text("help.pin.iso4.resultFields"));
                boolean encodeField = "encrypt_key".equals(context.fieldId());
                boolean decodeField = "decrypt_key".equals(context.fieldId());
                if (encodeField || decodeField) {
                    HelpContext.Selection keySelection = encodeField ? HelpContext.Selection.ENCRYPT_PIN_KEY_PRESENT
                            : HelpContext.Selection.DECRYPT_PIN_KEY_PRESENT;
                    boolean present = isSelected(keySelection);
                    addLocalizedParagraph(text("help.pin.key." + (present ? "present" : "absent")));
                }
                if ("protect".equals(context.actionId()) && !isSelected(HelpContext.Selection.ENCRYPT_PIN_KEY_PRESENT))
                    addLocalizedParagraph(text("help.pin.key.absent"));
                if ("decode".equals(context.actionId()) && !isSelected(HelpContext.Selection.DECRYPT_PIN_KEY_PRESENT))
                    addLocalizedParagraph(text("help.pin.decodeKey.absent"));
            }
        }
    }

    private void renderFpeApplicability() {
        String direction = selectedCode(HelpContext.Selection.FPE_OPERATION);
        String algorithm = selectedCode(HelpContext.Selection.FPE_ALGORITHM);
        String preset = selectedCode(HelpContext.Selection.FPE_PRESET);
        String radixText = selectedCode(HelpContext.Selection.FPE_RADIX);
        String keyBytes = selectedCode(HelpContext.Selection.FPE_KEY_BYTES);
        String tweakBytes = selectedCode(HelpContext.Selection.FPE_TWEAK_BYTES);
        if (!algorithm.isBlank()) addLocalizedParagraph(text("help.fpe.currentAlgorithm") + ": " + algorithm);
        if (!direction.isBlank()) addLocalizedParagraph(text("help.fpe.currentDirection") + ": " + direction);
        if (!preset.isBlank()) addLocalizedParagraph(text("help.fpe.currentPreset") + ": " + preset);
        if (!keyBytes.isBlank()) addLocalizedParagraph(text("help.fpe.currentKeyBytes") + ": " + keyBytes);
        if (!tweakBytes.isBlank()) addLocalizedParagraph(text("help.fpe.currentTweakBytes") + ": " + tweakBytes);
        int radix = parsePublicInt(radixText);
        if (radix >= 2 && radix <= 256) {
            addLocalizedParagraph(text("help.fpe.currentRadix") + ": " + radix);
            addLocalizedParagraph(text("help.fpe.minimumDomain"));
            if ("ff3_1".equalsIgnoreCase(algorithm)) {
                int maximum = com.cryptocarver.crypto.FormatPreservingEncryption.maximumFf3_1InputLength(radix);
                addLocalizedParagraph(text("help.fpe.ff3Limit") + ": " + maximum);
            }
        }
        addLocalizedParagraph(text("help.fpe.implementationLimit") + ": "
                + com.cryptocarver.crypto.FormatPreservingEncryption.maximumInputLength());
        if ("ff3_1".equalsIgnoreCase(algorithm)) addLocalizedParagraph(text("help.fpe.ff3Tweak"));
        else if ("ff1".equalsIgnoreCase(algorithm)) {
            addLocalizedParagraph(text("help.fpe.ff1Tweak"));
            addLocalizedParagraph(text("help.fpe.ff1TweakLimit") + ": "
                    + com.cryptocarver.crypto.FormatPreservingEncryption.maximumTweakLength());
        }
    }

    private void renderFileCipherApplicability() {
        String profile = selectedCode(HelpContext.Selection.FILE_PROFILE);
        if (!profile.isBlank()) addLocalizedParagraph(text("help.fileCipher.currentProfile") + ": " + profile);
        boolean lines = isSelected(HelpContext.Selection.FILE_LINES);
        addLocalizedParagraph(text(lines ? "help.fileCipher.linesEnabled" : "help.fileCipher.linesDisabled"));
        boolean compact = isSelected(HelpContext.Selection.FILE_COMPACT_CBC);
        if (lines && compact) addLocalizedParagraph(text("help.fileCipher.compactCbc"));
        if (lines) {
            String encoding = selectedCode(HelpContext.Selection.FILE_LINE_ENCODING);
            String charset = selectedCode(HelpContext.Selection.FILE_CHARSET);
            if (!encoding.isBlank()) addLocalizedParagraph(text("help.fileCipher.currentLineEncoding") + ": " + encoding);
            if (!charset.isBlank()) addLocalizedParagraph(text("help.fileCipher.currentCharset") + ": " + charset);
            addLocalizedParagraph(text("help.fileCipher.lineAuthentication"));
        } else if (profile.toUpperCase(java.util.Locale.ROOT).contains("GCM") || profile.toUpperCase(java.util.Locale.ROOT).contains("CHACHA")) {
            addLocalizedParagraph(text("help.fileCipher.detachedTag"));
        }
        if (profile.toUpperCase(java.util.Locale.ROOT).contains("CTR")) addLocalizedParagraph(text("help.fileCipher.ctrIntegrity"));
        if (profile.toUpperCase(java.util.Locale.ROOT).contains("CBC")) addLocalizedParagraph(text("help.fileCipher.cbcIntegrity"));
        addLocalizedParagraph(text("help.fileCipher.nonceGeneration"));
        if (lines) addLocalizedParagraph(text("help.fileCipher.ebcdicCodePages"));
    }

    private void renderRsaApplicability() {
        String padding = selectedCode(HelpContext.Selection.RSA_PADDING);
        String modulusBits = selectedCode(HelpContext.Selection.RSA_MODULUS_BITS);
        if ("input_format".equals(context.fieldId())) addLocalizedParagraph(text("help.rsa.inputFormats"));
        if ("output_format".equals(context.fieldId())) addLocalizedParagraph(text("help.rsa.outputFormats"));
        if ("public_key".equals(context.fieldId())) addLocalizedParagraph(text("help.rsa.publicFormats"));
        if ("private_key".equals(context.fieldId())) addLocalizedParagraph(text("help.rsa.privateFormats"));
        if (!padding.isBlank()) {
            addLocalizedParagraph(text("help.rsa.currentPadding") + ": " + padding);
            if (padding.toUpperCase(java.util.Locale.ROOT).contains("PKCS1PADDING")
                    || padding.toUpperCase(java.util.Locale.ROOT).contains("NOPADDING"))
                addLocalizedParagraph(text("help.rsa.legacyWarning"));
        }
        int bits = parsePublicInt(modulusBits);
        if (bits > 0 && !padding.isBlank() && !padding.toUpperCase(java.util.Locale.ROOT).contains("NOPADDING")) {
            int maximum = com.cryptocarver.ui.RsaPaddingLimits.maxPlaintextBytes(bits, padding);
            addLocalizedParagraph(text("help.rsa.currentMaximum") + ": " + maximum + " " + text("help.unit.bytes"));
        }
    }

    private void renderOpenPgpApplicability() {
        if (isSelected(HelpContext.Selection.OPENPGP_GNUPG_AVAILABLE))
            addLocalizedParagraph(text("help.openpgp.gnupgAvailable"));
        else addLocalizedParagraph(text("help.openpgp.gnupgUnavailable"));
        for (var entry : java.util.List.of(
                java.util.Map.entry(HelpContext.Selection.OPENPGP_PUBLIC_KEY_PRESENT, "publicKey"),
                java.util.Map.entry(HelpContext.Selection.OPENPGP_SECRET_KEY_PRESENT, "secretKey"),
                java.util.Map.entry(HelpContext.Selection.OPENPGP_SIGNATURE_PRESENT, "signature"),
                java.util.Map.entry(HelpContext.Selection.OPENPGP_INPUT_PRESENT, "input"),
                java.util.Map.entry(HelpContext.Selection.OPENPGP_PASSPHRASE_PRESENT, "passphrase"))) {
            addLocalizedParagraph(text("help.openpgp." + entry.getValue() + (isSelected(entry.getKey()) ? ".present" : ".absent")));
        }
    }

    private void renderEncryptedFileAnalysisApplicability() {
        for (var entry : java.util.List.of(
                java.util.Map.entry(HelpContext.Selection.ANALYSIS_FORMAT, "format"),
                java.util.Map.entry(HelpContext.Selection.ANALYSIS_MODE, "mode"),
                java.util.Map.entry(HelpContext.Selection.ANALYSIS_PADDING, "padding"))) {
            String value = selectedCode(entry.getKey());
            if (!value.isBlank()) addLocalizedParagraph(text("help.analysis.current." + entry.getValue()) + ": " + value);
        }
        for (var entry : java.util.List.of(
                java.util.Map.entry(HelpContext.Selection.ANALYSIS_HAS_KEY, "key"),
                java.util.Map.entry(HelpContext.Selection.ANALYSIS_HAS_IV, "iv"),
                java.util.Map.entry(HelpContext.Selection.ANALYSIS_HAS_TAG, "tag"),
                java.util.Map.entry(HelpContext.Selection.ANALYSIS_HAS_AAD, "aad"))) {
            addLocalizedParagraph(text("help.analysis.presence." + entry.getValue() + "."
                    + (isSelected(entry.getKey()) ? "present" : "absent")));
        }
        addLocalizedParagraph(text("help.analysis.route"));
        addLocalizedParagraph(text("help.analysis.noRecoveryGuarantee"));
    }

    private int parsePublicInt(String value) {
        try { return Integer.parseInt(value); } catch (RuntimeException ignored) { return 0; }
    }

    private String selectedCode(HelpContext.Selection selection) {
        HelpContext.PublicSelection value = context.selections().get(selection);
        return value == null ? "" : value.code();
    }

    private String selectedChoice(HelpContext.Selection selection, List<String> choices) {
        String code = selectedCode(selection);
        return choices.stream().filter(choice -> OperationHelpRegistry.slug(choice).equals(code))
                .findFirst().orElse("");
    }

    private boolean isSelected(HelpContext.Selection selection) {
        return Boolean.parseBoolean(selectedCode(selection));
    }

    private int requiredCekBytes(String contentEncryption) {
        return switch (contentEncryption) {
            case "A128GCM" -> 16;
            case "A192GCM" -> 24;
            case "A256GCM", "A128CBC-HS256" -> 32;
            case "A192CBC-HS384" -> 48;
            case "A256CBC-HS512" -> 64;
            default -> 0;
        };
    }

    private String resolveChoice(HelpContext.Selection key, List<String> choices, String fallback) {
        HelpContext.PublicSelection selected = context.selections().get(key);
        if (selected == null) return fallback;
        return choices.stream().filter(code -> com.cryptocarver.model.help.OperationHelpRegistry.slug(code)
                .equalsIgnoreCase(com.cryptocarver.model.help.OperationHelpRegistry.slug(selected.code())))
                .findFirst().orElse(fallback);
    }

    private void addLocalizedParagraph(String value) {
        Label paragraph = new Label(value);
        paragraph.setWrapText(true);
        content.getChildren().add(paragraph);
    }

    private void renderPreflightContext() {
        HelpContext.PublicSelection status = context.selections().get(HelpContext.Selection.PREFLIGHT_STATUS);
        HelpContext.PublicSelection rule = context.selections().get(HelpContext.Selection.RULE_ID);
        if (status != null) addContextLine(text("help.aux.preflight.status"), textOrFallback("help.preflight.status." + status.code().toLowerCase(java.util.Locale.ROOT), "help.preflight.status.unknown"));
        if (!context.fieldId().isBlank()) addContextLine(text("help.aux.preflight.target"), fieldLabel(context.fieldId(), rule));
        addContextLine(text("help.aux.preflight.remedy"), text(remedyKeyForField(context.fieldId())));
        if (rule != null) {
            String explanation = PreflightHelpCatalog.explanationKey(rule.code())
                    .map(key -> textOrFallback(key, "help.preflight.rule.generic"))
                    .orElseGet(() -> text("help.preflight.rule.generic"));
            addContextLine(text("help.aux.preflight.rule"), explanation);
        } else {
            Label fallback = new Label(text("help.preflight.noRule"));
            fallback.setWrapText(true);
            fallback.getStyleClass().add("text-muted");
            content.getChildren().add(fallback);
        }
        Label diagnosis = new Label(text("help.preflight.redactedDiagnostic"));
        diagnosis.setWrapText(true);
        content.getChildren().add(diagnosis);
    }

    private String fieldLabel(String fieldId, HelpContext.PublicSelection rule) {
        if (rule != null && rule.code().startsWith("preflight.nonce.")) {
            return text("help.preflight.field.nonce");
        }
        return PreflightHelpCatalog.fieldLabelKey(fieldId).map(this::text).orElse(fieldId);
    }

    private String remedyKeyForField(String fieldId) {
        String target = fieldId == null ? "" : fieldId.toLowerCase(java.util.Locale.ROOT);
        if (target.contains("algorithm")) return "preflight.remedy.algorithm";
        if (target.contains("mode")) return "preflight.remedy.mode";
        if (target.contains("key")) return "preflight.remedy.key";
        if (target.contains("iv") || target.contains("nonce")) return "preflight.remedy.iv";
        if (target.contains("tag")) return "preflight.remedy.tag";
        if (target.contains("input") || target.contains("data")) return "preflight.remedy.input";
        return "preflight.remedy.generic";
    }

    private void addContextLine(String prefix, String value) {
        Label line = new Label(prefix + ": " + value);
        line.setWrapText(true);
        content.getChildren().add(line);
    }

    private String textOrFallback(String key, String fallbackKey) {
        String value = text(key);
        return value.equals(key) ? text(fallbackKey) : value;
    }

    private String text(String key) {
        if (key == null || key.isBlank()) return "";
        String value = i18n.text(key);
        return value == null ? key : value;
    }

    private String text(String key, Object... arguments) {
        if (key == null || key.isBlank()) return "";
        return i18n.text(key, arguments);
    }
}
