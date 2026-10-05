package com.cryptocarver.ui;

import com.cryptocarver.model.payments.PaymentProfile;
import com.cryptocarver.service.I18nService;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextInputControl;

import java.util.List;
import java.util.function.Supplier;

/** Coordinates local EMV module clearing and laboratory profile loading. */
final class EmvModuleStateCoordinator {
    record View(Supplier<Node> moduleRoot,
            Supplier<TextInputControl> sessionKeyResultArea,
            Supplier<TextInputControl> arqcResultArea,
            Supplier<TextInputControl> arpcResultArea,
            Supplier<TextInputControl> track2ResultArea,
            Supplier<TextInputControl> imkField,
            Supplier<TextInputControl> panFieldSession,
            Supplier<TextInputControl> panSeqFieldSession,
            Supplier<TextInputControl> atcField,
            Supplier<TextInputControl> skARQCField,
            Supplier<TextInputControl> amountField,
            Supplier<TextInputControl> amountOtherField,
            Supplier<TextInputControl> atcARQCField,
            Supplier<TextInputControl> unField,
            Supplier<TextInputControl> arqcTerminalDataField,
            Supplier<TextInputControl> iccDataField,
            Supplier<TextInputControl> skARPCField,
            Supplier<TextInputControl> arqcField,
            Supplier<TextInputControl> arcField,
            Supplier<TextInputControl> csuField,
            Supplier<TextInputControl> propAuthDataField,
            Supplier<TextInputControl> panTrack2Field,
            Supplier<TextInputControl> expiryTrack2Field,
            Supplier<TextInputControl> serviceCodeFieldTrack2,
            Supplier<TextInputControl> discretionaryDataField,
            Supplier<TextInputControl> track2InputField,
            Supplier<TextInputControl> emvTlvInputArea,
            Supplier<TextInputControl> emvTlvResultArea,
            Supplier<TextInputControl> emvDolTemplateField,
            Supplier<TextInputControl> emvDolValuesArea,
            Supplier<TextInputControl> emvDolResultArea,
            Supplier<ComboBox<String>> arqcPaddingMethod,
            Supplier<ComboBox<String>> arpcMethod,
            Supplier<EmvArqcCoordinator.State> arqcState) {
    }

    private final View view;
    private final Supplier<StatusReporter> reporter;

    EmvModuleStateCoordinator(View view, Supplier<StatusReporter> reporter) {
        this.view = view;
        this.reporter = reporter;
    }

    void clearModuleData() {
        ModuleResetPolicy.clearTextInputs(view.moduleRoot().get());
        for (Supplier<TextInputControl> field : List.of(
                view.sessionKeyResultArea(), view.arqcResultArea(), view.arpcResultArea(), view.track2ResultArea(),
                view.imkField(), view.panFieldSession(), view.panSeqFieldSession(), view.atcField(),
                view.skARQCField(), view.amountField(), view.amountOtherField(), view.atcARQCField(), view.unField(),
                view.arqcTerminalDataField(), view.iccDataField(), view.skARPCField(), view.arqcField(),
                view.arcField(), view.csuField(), view.propAuthDataField(), view.panTrack2Field(),
                view.expiryTrack2Field(), view.serviceCodeFieldTrack2(), view.discretionaryDataField(),
                view.track2InputField(), view.emvTlvInputArea(), view.emvTlvResultArea(),
                view.emvDolTemplateField(), view.emvDolValuesArea(), view.emvDolResultArea())) {
            clear(field.get());
        }
        view.arqcState().get().clear();
    }

    void loadProfile(PaymentProfile profile) {
        if (profile.getType() == PaymentProfile.ProfileType.EMV) {
            String derivedSessionKey = EmvSessionKeyCoordinator.deriveLaboratorySessionKey(profile);
            if (profile.getName().contains("ARQC")) {
                set(view.skARQCField(), derivedSessionKey);
                setIfPresent(view.imkField(), profile, "imk");
                setIfPresent(view.panFieldSession(), profile, "pan");
                setIfPresent(view.panSeqFieldSession(), profile, "panSeq");
                setIfPresent(view.atcARQCField(), profile, "atc");
                setIfPresent(view.unField(), profile, "unpredictableNumber");
                setIfPresent(view.arqcTerminalDataField(), profile, "transactionData");
                ComboBox<String> paddingCombo = view.arqcPaddingMethod().get();
                if (paddingCombo != null && profile.getParameters().containsKey("padding")) {
                    String padding = profile.getParameters().get("padding");
                    for (String item : paddingCombo.getItems()) {
                        if (item.contains(padding)) { paddingCombo.setValue(item); break; }
                    }
                }
            } else if (profile.getName().contains("ARPC")) {
                set(view.skARPCField(), derivedSessionKey);
                setIfPresent(view.arqcField(), profile, "arqc");
                setIfPresent(view.arcField(), profile, "arc");
                setIfPresent(view.csuField(), profile, "csu");
                ComboBox<String> methodCombo = view.arpcMethod().get();
                if (methodCombo != null) {
                    String method = profile.getName().contains("Method 1") ? "Method 1"
                            : profile.getParameters().getOrDefault("method", "");
                    for (String item : methodCombo.getItems()) {
                        if (item.contains(method)) { methodCombo.setValue(item); break; }
                    }
                }
            }
            StatusReporter statusReporter = reporter.get();
            if (statusReporter != null) {
                statusReporter.updateStatus(text("module.payments.status.profileLoaded", profile.getName()));
            }
            System.out.println("Loaded EMV profile: " + profile.getName());
        } else if (profile.getType() == PaymentProfile.ProfileType.SECURE_MESSAGING) {
            // Secure Messaging has no mapped controls on this EMV screen.
            System.out.println("Loaded Secure Messaging profile: " + profile.getName());
        }
    }

    private static void clear(TextInputControl control) {
        if (control != null) control.clear();
    }

    private static void set(Supplier<TextInputControl> fieldSupplier, String value) {
        TextInputControl field = fieldSupplier.get();
        if (field != null) field.setText(value);
    }

    private static void setIfPresent(Supplier<TextInputControl> fieldSupplier, PaymentProfile profile, String name) {
        TextInputControl field = fieldSupplier.get();
        if (field != null && profile.getInputs().containsKey(name)) field.setText(profile.getInputs().get(name));
    }

    private String text(String key, Object... args) {
        return I18nService.getInstance().text(key, args);
    }
}
