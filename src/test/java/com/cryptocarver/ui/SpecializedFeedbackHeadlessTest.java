package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.service.I18nService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Headless contracts for actionable UX-20 feedback outside Payments. */
class SpecializedFeedbackHeadlessTest {
    @TempDir Path temporaryDirectory;

    @Test
    void specializedValidationFeedbackHasDistinctEnglishAndSpanishKeys() throws Exception {
        Map<String, List<String>> controllerKeys = new LinkedHashMap<>();
        controllerKeys.put("ASN1Controller", List.of(
                "module.asn1.feedback.inputRequired", "module.asn1.feedback.inputInvalid",
                "module.asn1.feedback.strictDer", "module.asn1.feedback.parseFailed",
                "module.asn1.feedback.encodeRequired", "module.asn1.feedback.exportRequired",
                "module.asn1.feedback.hexInvalid", "module.asn1.feedback.statusParsed"));
        controllerKeys.put("AsicController", List.of(
                "module.asic.feedback.required", "module.asic.feedback.fileMissing",
                "module.asic.feedback.outputExists", "module.asic.feedback.payloads",
                "module.asic.feedback.mime", "module.asic.feedback.tokenKey",
                "module.asic.feedback.operation", "module.asic.feedback.statusCreated"));
        controllerKeys.put("CmsInspectorController", List.of(
                "module.cms.inputRequired", "module.cms.inspectFailed",
                "module.cms.feedback.statusInspected", "module.cms.feedback.statusExported"));
        controllerKeys.put("EMVController", List.of(
                "module.emv.feedback.dolFormat", "module.emv.feedback.sessionRequired",
                "module.emv.feedback.arqcRequired", "module.emv.feedback.arqcAmountRequired",
                "module.emv.feedback.arpcRequired", "module.emv.feedback.trackRequired",
                "module.emv.feedback.trackDataRequired", "module.emv.feedback.arqcValid"));
        controllerKeys.put("JOSEController", List.of(
                "module.jose.feedback.fileRead", "module.jose.feedback.copyEmpty",
                "module.jose.feedback.algorithmRequired", "module.jose.feedback.keyFormat",
                "module.jose.feedback.statusDetachedGenerated", "module.jose.feedback.statusJwtGenerated",
                "module.jose.feedback.statusJwtValidation", "module.jose.feedback.statusNested",
                "module.jose.feedback.statusJweDecrypted", "module.jose.feedback.keyAdded"));
        controllerKeys.put("PadesController", List.of(
                "module.pades.feedback.required", "module.pades.feedback.fileMissing",
                "module.pades.feedback.outputExists", "module.pades.feedback.fileTooLarge",
                "module.pades.feedback.tokenKey", "module.pades.feedback.coordinates",
                "module.pades.feedback.reportRequired", "module.pades.feedback.operation",
                "module.pades.feedback.statusValidated"));
        controllerKeys.put("ProcessDesignerController", List.of(
                "module.process.feedback.nodeError", "module.process.feedback.aad",
                "module.process.feedback.iv", "module.process.feedback.failed",
                "module.process.feedback.connectionReversed", "module.process.feedback.ivLabel"));
        controllerKeys.put("WssSecurityController", List.of(
                "module.wss.feedback.keyStoreRequired", "module.wss.feedback.keyAliasRequired",
                "module.wss.feedback.keyStoreLoad", "module.wss.feedback.statusSaved"));
        controllerKeys.put("XMLSignatureController", List.of(
                "module.xml.feedback.keyStoreRequired", "module.xml.feedback.aliasRequired",
                "module.xml.feedback.tsaProfileRequired", "module.xml.feedback.tsaRequestRequired",
                "module.xml.feedback.timestampFileRequired", "module.xml.feedback.timestampTokenRequired",
                "module.xml.feedback.timestampRequesting", "module.xml.feedback.timestampReceived",
                "module.xml.feedback.timestampValidated", "module.xml.feedback.saveRequired",
                "module.xml.feedback.statusInspected"));

        AppSettings settings = new AppSettings(temporaryDirectory.resolve("settings.json"));
        I18nService service = new I18nService(settings, I18nService.BUNDLE_BASE_NAME,
                Locale.ENGLISH, getClass().getClassLoader());

        for (Map.Entry<String, List<String>> entry : controllerKeys.entrySet()) {
            String source = Files.readString(Path.of("src/main/java/com/cryptocarver/ui/" + entry.getKey() + ".java"));
            for (String key : entry.getValue()) {
                String english = service.text(key, "TECHNICAL_DETAIL");
                assertNotEquals(key, english, entry.getKey() + " missing EN text for " + key);
                assertFalse(english.isBlank(), key);
                assertTrue(source.contains(key), entry.getKey() + " must use " + key);

                service.setPreference(LanguagePreference.ES);
                String spanish = service.text(key, "TECHNICAL_DETAIL");
                assertNotEquals(key, spanish, key + " missing ES text");
                assertNotEquals(english, spanish, key + " must be distinguishable in ES");
                service.setPreference(LanguagePreference.EN);
            }
        }
    }

    @Test
    void concreteValidationFlowsDoNotUseTheOldGenericRequiredFeedback() throws Exception {
        for (String controller : List.of("EMVController", "WssSecurityController", "XMLSignatureController")) {
            String source = Files.readString(Path.of("src/main/java/com/cryptocarver/ui/" + controller + ".java"));
            assertFalse(source.contains("module.emv.error.required"), controller);
            assertFalse(source.contains("module.wss.error.required"), controller);
            assertFalse(source.contains("module.xml.error.required"), controller);
        }
    }
}
