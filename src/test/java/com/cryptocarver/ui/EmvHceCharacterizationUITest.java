package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.service.I18nService;
import javafx.scene.control.TextInputControl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Deterministic invented UDK, real HCE controls and all shared visibility surfaces. */
@Tag("ui")
@EnabledIfSystemProperty(named="runUiTests", matches="true")
class EmvHceCharacterizationUITest extends EmvExtractionCharacterizationSupport {
    private static final String KEY = "00112233445566778899AABBCCDDEEFF";

    @Test void examplesLukCryptogramsValidationAndPrivacyKeepTheirTranscript() throws Exception {
        List<String> lines = new ArrayList<>(), violations = new ArrayList<>();
        onFx(() -> {
            EMVController c=controller();
            for (SecretVisibilityProfile profile : SecretVisibilityProfile.values()) {
                AppSettings.getInstance().setSecretVisibilityProfile(profile); resetShared();
                c.handleHceLoadExample();
                assertEquals("26",text(c,"hceYearField"));
                assertEquals("6431",text(c,"hceHoursField"));
                assertEquals("01",text(c,"hceCounterField"));
                assertEquals("",text(c,"hceMsdLukField"));
                assertEquals("",text(c,"hceQvsdcLukField"));
                assertEquals(I18nService.getInstance().text("module.emv.hce.exampleLoaded"),text(c,"hceResultArea"));
                // Replace the shipped example UDK before any operation publishes a result.
                ((TextInputControl)get(c,"hceUdkField")).setText(KEY);
                c.handleHceLuk();
                String luk=text(c,"hceMsdLukField");
                assertTrue(luk.matches("[0-9A-F]{32}"));
                assertEquals(luk,text(c,"hceQvsdcLukField"));
                OperationResult keys=published();
                assertEquals(OperationDetail.Classification.SECRET,keys.getOutputClassification());
                assertEquals(luk,new String(keys.getOutput(),StandardCharsets.UTF_8));
                assertTrue(keys.getDetails().stream().anyMatch(d -> d.name().equals("UDK")
                        && d.classification()==OperationDetail.Classification.SECRET && d.value().equals(KEY)));
                lines.add(profile+" LUK="+luk+" output=SECRET");
                inspectSurfaces(profile,"HCE keys",List.of(KEY,luk),shell,root,lines,violations);
                c.handleHceMsd();
                OperationResult msd=published();
                assertEquals(OperationDetail.Classification.PUBLIC,msd.getOutputClassification());
                String msdValue=new String(msd.getOutput(),StandardCharsets.UTF_8);
                assertTrue(msdValue.matches("[0-9]{3}"));
                assertTrue(text(c,"hceResultArea").contains(msdValue));
                lines.add(profile+" MSD="+msdValue+" output=PUBLIC");
                inspectSurfaces(profile,"HCE MSD",List.of(KEY,luk),shell,root,lines,violations);
                c.handleHceQvsdc();
                OperationResult qvsdc=published();
                assertEquals(OperationDetail.Classification.PUBLIC,qvsdc.getOutputClassification());
                String cryptogram=new String(qvsdc.getOutput(),StandardCharsets.UTF_8);
                assertTrue(cryptogram.matches("[0-9A-F]{16}"));
                assertTrue(text(c,"hceResultArea").contains(cryptogram));
                lines.add(profile+" qVSDC="+cryptogram+" output=PUBLIC");
                inspectSurfaces(profile,"HCE qVSDC",List.of(KEY,luk),shell,root,lines,violations);
                // Persist only this isolated fixture's invented data for a pre-extraction diagnosis.
                java.nio.file.Files.writeString(java.nio.file.Path.of("target/emv7-history-" + profile + ".json"),
                        new com.google.gson.Gson().toJson(shell.getHistoryManager().getHistoryItems()));
            }
            // Validation messages are application's localized literals, never provider exception text.
            for(LanguagePreference language:List.of(LanguagePreference.EN,LanguagePreference.ES)) {
                I18nService.getInstance().setPreference(language);
                for(String field:List.of("hceYearField","hceHoursField","hceCounterField")) {
                    c.handleHceLoadExample(); ((TextInputControl)get(c,"hceUdkField")).setText(KEY);
                    ((TextInputControl)get(c,field)).setText("X");
                    OperationResult before=published(); c.handleHceLuk();
                    String key=field.equals("hceYearField")?"module.emv.hce.yearInvalid":
                            field.equals("hceHoursField")?"module.emv.hce.hoursInvalid":"module.emv.hce.counterInvalid";
                    String expected=I18nService.getInstance().text("module.emv.hce.error",I18nService.getInstance().text(key));
                    assertEquals(expected,text(c,"hceResultArea")); assertSame(before,published());
                    assertEquals("",text(c,"hceMsdLukField")); assertEquals("",text(c,"hceQvsdcLukField"));
                    lines.add(language+" "+field+" invalid="+expected+" published=false");
                }
                c.handleHceLoadExample(); ((TextInputControl)get(c,"hceUdkField")).setText(KEY);c.handleHceLuk();
                ((TextInputControl)get(c,"hceCountryField")).setText("071");
                OperationResult before=published();c.handleHceQvsdc();
                String expected=I18nService.getInstance().text("module.emv.hce.error",
                        I18nService.getInstance().text("module.emv.hce.hexLength",
                                I18nService.getInstance().text("module.emv.hce.country"),2));
                assertEquals(expected,text(c,"hceResultArea"));assertSame(before,published());
                lines.add(language+" country invalid="+expected+" published=false");
            }
            assertTrue(violations.isEmpty(),String.join("\n",violations));
        });
        pinTranscript("emv-hce-75","",lines);
    }
    private OperationResult published() throws Exception {
        return (OperationResult)get(shell,"lastPublishedResultSnapshot");
    }
    private static String text(EMVController c,String field) throws Exception {
        return ((TextInputControl)get(c,field)).getText();
    }
}
