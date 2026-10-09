package com.cryptocarver.ui;

import com.cryptocarver.crypto.CborInspector;
import com.cryptocarver.util.DataConverter;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import java.util.function.Supplier;

/** Coordinates CBOR inspection and CBOR/JSON conversion over the controller-owned FXML controls. */
final class WalletCborCoordinator extends WalletCoordinatorBase {
    record View(Supplier<TextArea> cborInputArea,
            Supplier<ComboBox<String>> cborViewCombo,
            Supplier<TextArea> cborOutputArea,
            Supplier<TextArea> cborJsonArea,
            Supplier<TextArea> cborFromJsonOutputArea) { }

    private final View view;

    WalletCborCoordinator(View view, Supplier<StatusReporter> reporter) {
        super(reporter);
        this.view = view;
    }

    void handleCborInspect() {
        try {
            String hex = textOf(view.cborInputArea().get());
            if (isBlank(hex)) { showValidation(t("module.wallet.cborRequired"), "cborInputArea"); return; }
            byte[] cbor = CborInspector.parseHex(hex);
            String report = WalletPrivateMaterialPolicy.forDisplay(switch (valueOf(view.cborViewCombo().get(), "tree")) {
                case "diagnostic" -> CborInspector.diagnostic(cbor);
                case "summary" -> CborInspector.summary(cbor);
                default -> CborInspector.tree(cbor);
            }, WalletPrivateMaterialPolicy.cborAsJson(cbor));
            view.cborOutputArea().get().setText(report);
            updateStatus(t("module.wallet.status.inspected"));
            publish("CBOR Inspect", report);
        } catch (Exception e) {
            fail(e, "cborInputArea", "cbor inspect");
        }
    }

    void handleCborToJson() {
        try {
            String hex = textOf(view.cborInputArea().get());
            if (isBlank(hex)) { showValidation(t("module.wallet.cborRequired"), "cborInputArea"); return; }
            String json = WalletPrivateMaterialPolicy.forDisplay(CborInspector.toJson(CborInspector.parseHex(hex)));
            view.cborOutputArea().get().setText(json);
            updateStatus(t("module.wallet.status.converted"));
            publish("CBOR to JSON", json);
        } catch (Exception e) {
            fail(e, "cborInputArea", "cbor to json");
        }
    }

    void handleCborFromJson() {
        try {
            String json = textOf(view.cborJsonArea().get());
            if (isBlank(json)) { showValidation(t("module.wallet.jsonRequired"), "cborJsonArea"); return; }
            String hex = WalletPrivateMaterialPolicy.forDisplay(
                    DataConverter.bytesToHex(CborInspector.fromJson(json)).toUpperCase(), json);
            view.cborFromJsonOutputArea().get().setText(hex);
            updateStatus(t("module.wallet.status.converted"));
            publish("JSON to CBOR", hex);
        } catch (Exception e) {
            fail(e, "cborJsonArea", "cbor from json");
        }
    }
}
