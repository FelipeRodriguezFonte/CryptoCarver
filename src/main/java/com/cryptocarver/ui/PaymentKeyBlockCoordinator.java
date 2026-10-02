package com.cryptocarver.ui;

import com.cryptocarver.crypto.*;
import com.cryptocarver.model.OperationResult;
import javafx.scene.control.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/** Payment key block operations and synchronized header controls. */
final class PaymentKeyBlockCoordinator extends KeysCoordinatorSupport {
    record View(
            TextField thalesLmkField,
            TextField thalesKeyTypeField,
            ComboBox<String> thalesSchemeCombo,
            TextField thalesClearKeyField,
            TextField thalesCryptogramField,
            TextField thalesCheckValueField,
            CheckBox thalesComponentCheck,
            TextArea thalesResultArea,
            TextArea keyBlockInputArea,
            TextArea keyBlockResultArea,
            TextField keyBlockLmkField,
            ComboBox<AtallaAkbHeader.Template> atallaTemplateCombo,
            ComboBox<AtallaAkbHeader.Option> atalla0Combo,
            ComboBox<AtallaAkbHeader.Option> atalla1Combo,
            ComboBox<AtallaAkbHeader.Option> atalla2Combo,
            ComboBox<AtallaAkbHeader.Option> atalla3Combo,
            ComboBox<AtallaAkbHeader.Option> atalla4Combo,
            ComboBox<AtallaAkbHeader.Option> atalla5Combo,
            ComboBox<AtallaAkbHeader.Option> atalla6Combo,
            ComboBox<AtallaAkbHeader.Option> atalla7Combo,
            TextField atallaHeaderField,
            TextArea atallaMeaningArea,
            TextField atallaMfkField,
            TextField atallaKeyField,
            TextArea atallaBlockArea,
            TextArea atallaResultArea) { }

    private final java.util.function.Supplier<View> view;

    PaymentKeyBlockCoordinator(java.util.function.Supplier<View> view, java.util.function.Supplier<StatusReporter> reporter) {
        super(reporter);
        this.view = view;
    }

    private View view() {
        return view.get();
    }

    private static final String MANUAL_LMK_28_29 = "1A1A1A1A1A1A1A1A1C1C1C1C1C1C1C1C";

    private static final String MANUAL_MK_SMI = "F1F1F1F1F1F1F1F1C1C1C1C1C1C1C1C1";

    private static final String MANUAL_CRYPTOGRAM = "5178C9D3D1052B15BF6AEC458B4A4564";

    private static final String MANUAL_CHECK_VALUE = "8357D9";

    private static final String KEY_BLOCK_TEST_LMK = "0123456789ABCDEF8080808080808080FEDCBA9876543210";

    private static final String PUBLISHED_KEY_BLOCK =
            "S00072B0TN00E000256A37F894FD49E61DD3FA27FDE8919D07F7AA966F8BF39AB31D00034";

    private boolean atallaSyncing;

    private interface ThalesStep {
        String run() throws Exception;
    }

    void handleThalesEncrypt() {
        runThales(() -> {
            ThalesLmkOperations.WrappedKey wrapped = ThalesLmkOperations.encrypt(
                    thalesText(view().thalesClearKeyField()), thalesKeyTypeCode(), thalesScheme(), thalesLmk(),
                    view().thalesComponentCheck() != null && view().thalesComponentCheck().isSelected());
            if (view().thalesCryptogramField() != null) view().thalesCryptogramField().setText(wrapped.cryptogram());
            if (view().thalesCheckValueField() != null) view().thalesCheckValueField().setText(wrapped.checkValue());
            return ThalesLmkOperations.describe(wrapped, thalesLmk());
        }, "Thales LMK Encrypt");
    }

    void handleThalesDecrypt() {
        runThales(() -> {
            ThalesLmkOperations.WrappedKey recovered = ThalesLmkOperations.decrypt(
                    thalesText(view().thalesCryptogramField()), thalesKeyTypeCode(), thalesScheme(), thalesLmk(),
                    view().thalesComponentCheck() != null && view().thalesComponentCheck().isSelected());
            if (view().thalesClearKeyField() != null) view().thalesClearKeyField().setText(recovered.cryptogram());
            if (view().thalesCheckValueField() != null) view().thalesCheckValueField().setText(recovered.checkValue());
            return "Recovered key: " + recovered.cryptogram()
                    + "\nCheck value  : " + recovered.checkValue() + "\n";
        }, "Thales LMK Decrypt");
    }

    void handleThalesDescribe() {
        runThales(() -> ThalesLmkOperations.describe(
                ThalesLmkOperations.encrypt(thalesText(view().thalesClearKeyField()), thalesKeyTypeCode(),
                        thalesScheme(), thalesLmk(),
                        view().thalesComponentCheck() != null && view().thalesComponentCheck().isSelected()),
                thalesLmk()), "Thales LMK");
    }

    void handleThalesLookup() {
        runThales(() -> ThalesLmkOperations.describe(ThalesLmkOperations.lookup(
                thalesText(view().thalesCryptogramField()), thalesText(view().thalesCheckValueField()), thalesLmk())),
                "Thales Key Type Lookup");
    }

    void handleThalesLoadExample() {
        if (view().thalesLmkField() != null) view().thalesLmkField().setText(MANUAL_LMK_28_29);
        if (view().thalesKeyTypeField() != null) view().thalesKeyTypeField().setText("209");
        if (view().thalesSchemeCombo() != null) view().thalesSchemeCombo().setValue("U");
        if (view().thalesClearKeyField() != null) view().thalesClearKeyField().setText(MANUAL_MK_SMI);
        if (view().thalesCryptogramField() != null) view().thalesCryptogramField().setText(MANUAL_CRYPTOGRAM);
        if (view().thalesCheckValueField() != null) view().thalesCheckValueField().setText(MANUAL_CHECK_VALUE);
        if (view().thalesComponentCheck() != null) view().thalesComponentCheck().setSelected(false);
        if (view().thalesResultArea() != null) {
            view().thalesResultArea().setText(t("module.keys.thales.exampleLoaded"));
        }
    }

    void initializeThalesControls() {
        if (view().thalesSchemeCombo() == null) {
            return;
        }
        view().thalesSchemeCombo().getItems().setAll("U", "T", "Z");
        view().thalesSchemeCombo().getSelectionModel().selectFirst();
    }

    ThalesLmkOperations.Lmk thalesLmk() {
        return ThalesLmkOperations.Lmk.of(thalesText(view().thalesLmkField()));
    }

    String thalesKeyTypeCode() {
        String code = thalesText(view().thalesKeyTypeField());
        return code.isEmpty() ? "000" : code;
    }

    ThalesLmkOperations.Scheme thalesScheme() {
        String value = view().thalesSchemeCombo() == null || view().thalesSchemeCombo().getValue() == null
                ? "U" : view().thalesSchemeCombo().getValue().trim();
        return ThalesLmkOperations.Scheme.of(value.isEmpty() ? 'U' : value.charAt(0));
    }

    static String thalesText(TextInputControl field) {
        return field == null || field.getText() == null ? "" : field.getText().trim();
    }

    void runThales(ThalesStep step, String operation) {
        try {
            String report = step.run();
            if (view().thalesResultArea() != null) view().thalesResultArea().setText(report);
            if (reporter() != null) {
                reporter().publish(OperationResult.forOperation(operation)
                        .output(report.getBytes(StandardCharsets.UTF_8))
                        .status(t("module.keys.thales.status"))
                        .build());
            }
        } catch (Exception e) {
            if (view().thalesResultArea() != null) {
                view().thalesResultArea().setText(t("module.keys.thales.error", String.valueOf(e.getMessage())));
            }
        }
    }

    void handleKeyBlockInspect() {
        try {
            String report = ThalesKeyBlockOperations.describe(
                    ThalesKeyBlockOperations.parse(thalesText(view().keyBlockInputArea())));
            if (view().keyBlockResultArea() != null) view().keyBlockResultArea().setText(report);
            if (reporter() != null) {
                reporter().publish(OperationResult.forOperation("Thales Key Block")
                        .output(report.getBytes(StandardCharsets.UTF_8))
                        .status(t("module.keys.keyBlock.status"))
                        .build());
            }
        } catch (Exception e) {
            if (view().keyBlockResultArea() != null) {
                view().keyBlockResultArea().setText(t("module.keys.keyBlock.error", String.valueOf(e.getMessage())));
            }
        }
    }

    void handleKeyBlockUnwrap() {
        try {
            String report = ThalesKeyBlockOperations.describe(ThalesKeyBlockOperations.unwrap(
                    thalesText(view().keyBlockLmkField()), thalesText(view().keyBlockInputArea())));
            if (view().keyBlockResultArea() != null) view().keyBlockResultArea().setText(report);
            if (reporter() != null) {
                reporter().publish(OperationResult.forOperation("Thales Key Block Unwrap")
                        .output(report.getBytes(StandardCharsets.UTF_8))
                        .status(t("module.keys.keyBlock.status"))
                        .build());
            }
        } catch (Exception e) {
            if (view().keyBlockResultArea() != null) {
                view().keyBlockResultArea().setText(t("module.keys.keyBlock.error", String.valueOf(e.getMessage())));
            }
        }
    }

    void handleKeyBlockExample() {
        if (view().keyBlockInputArea() != null) view().keyBlockInputArea().setText(PUBLISHED_KEY_BLOCK);
        if (view().keyBlockLmkField() != null) view().keyBlockLmkField().setText(KEY_BLOCK_TEST_LMK);
        if (view().keyBlockResultArea() != null) view().keyBlockResultArea().setText(t("module.keys.keyBlock.exampleLoaded"));
    }

    List<ComboBox<AtallaAkbHeader.Option>> atallaCombos() {
        return Arrays.asList(view().atalla0Combo(), view().atalla1Combo(), view().atalla2Combo(), view().atalla3Combo(),
                view().atalla4Combo(), view().atalla5Combo(), view().atalla6Combo(), view().atalla7Combo());
    }

    void initializeAtalla() {
        if (view().atallaHeaderField() == null || view().atalla0Combo() == null) {
            return;
        }
        List<ComboBox<AtallaAkbHeader.Option>> combos = atallaCombos();
        for (int at = 0; at < combos.size(); at++) {
            combos.get(at).getItems().setAll(AtallaAkbHeader.options(at));
            combos.get(at).valueProperty().addListener((obs, old, now) -> atallaHeaderFromCombos());
        }
        if (view().atallaTemplateCombo() != null) {
            view().atallaTemplateCombo().getItems().setAll(AtallaAkbHeader.templates());
            view().atallaTemplateCombo().valueProperty().addListener((obs, old, now) -> {
                if (now != null) {
                    view().atallaHeaderField().setText(now.header());
                }
            });
        }
        view().atallaHeaderField().textProperty().addListener((obs, old, now) -> atallaCombosFromHeader(now));
        view().atallaHeaderField().setText("1PUNE000");
    }

    void atallaHeaderFromCombos() {
        if (atallaSyncing) {
            return;
        }
        String current = view().atallaHeaderField().getText() == null ? "" : view().atallaHeaderField().getText();
        StringBuilder header = new StringBuilder();
        List<ComboBox<AtallaAkbHeader.Option>> combos = atallaCombos();
        for (int at = 0; at < combos.size(); at++) {
            AtallaAkbHeader.Option option = combos.get(at).getValue();
            header.append(option != null ? option.code() : at < current.length() ? current.charAt(at) : '0');
        }
        atallaSyncing = true;
        try {
            view().atallaHeaderField().setText(header.toString());
        } finally {
            atallaSyncing = false;
        }
        atallaExplainHeader(header.toString());
    }

    void atallaCombosFromHeader(String header) {
        if (!atallaSyncing) {
            atallaSyncing = true;
            try {
                List<ComboBox<AtallaAkbHeader.Option>> combos = atallaCombos();
                for (int at = 0; at < combos.size(); at++) {
                    AtallaAkbHeader.Option match = null;
                    if (header != null && header.length() == AtallaAkbOperations.HEADER_LENGTH) {
                        for (AtallaAkbHeader.Option option : combos.get(at).getItems()) {
                            if (option.code() == header.charAt(at)) {
                                match = option;
                                break;
                            }
                        }
                    }
                    combos.get(at).setValue(match);
                }
            } finally {
                atallaSyncing = false;
            }
        }
        atallaExplainHeader(header);
    }

    void atallaExplainHeader(String header) {
        if (view().atallaMeaningArea() == null) {
            return;
        }
        if (header == null || header.length() != AtallaAkbOperations.HEADER_LENGTH) {
            view().atallaMeaningArea().setText("The header is " + AtallaAkbOperations.HEADER_LENGTH + " characters.");
            return;
        }
        view().atallaMeaningArea().setText(AtallaAkbHeader.decode(header));
    }

    void handleAtallaGenerate() {
        try {
            String header = thalesText(view().atallaHeaderField());
            String block = AtallaAkbOperations.wrap(thalesText(view().atallaMfkField()), header, thalesText(view().atallaKeyField()));
            String report = "AKB: " + block + "\n\n" + AtallaAkbOperations.describe(
                    AtallaAkbOperations.unwrap(thalesText(view().atallaMfkField()), block));
            atallaPublish("Atalla AKB Generate", report);
        } catch (Exception e) {
            if (view().atallaResultArea() != null) view().atallaResultArea().setText("Error: " + e.getMessage());
        }
    }

    void handleAtallaUnwrap() {
        try {
            String text = thalesText(view().atallaBlockArea());
            AtallaAkbOperations.Unwrapped unwrapped = AtallaAkbOperations.unwrap(thalesText(view().atallaMfkField()), text);
            view().atallaHeaderField().setText(unwrapped.akb().header());
            atallaPublish("Atalla AKB Unwrap", AtallaAkbOperations.describe(unwrapped));
        } catch (Exception e) {
            if (view().atallaResultArea() != null) view().atallaResultArea().setText("Error: " + e.getMessage());
        }
    }

    void handleAtallaExample() {
        view().atallaHeaderField().setText("1PUNE000");
        view().atallaMfkField().setText(KEY_BLOCK_TEST_LMK);
        view().atallaKeyField().setText("00112233445566778899AABBCCDDEEFF0123456789ABCDEF");
        view().atallaBlockArea().setText("1PUNE000,23AE722410BC25C24BB6AD0C900A16F085927D34A8C06EB0,DA3BB9004654010D");
        if (view().atallaResultArea() != null) view().atallaResultArea().setText("Example loaded: external tool vector.");
    }

    void atallaPublish(String operation, String report) {
        if (view().atallaResultArea() != null) view().atallaResultArea().setText(report);
        if (reporter() != null) {
            reporter().publish(OperationResult.forOperation(operation)
                    .output(report.getBytes(StandardCharsets.UTF_8))
                    .status("Atalla Key Block")
                    .build());
        }
    }
}
