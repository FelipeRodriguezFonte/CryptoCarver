package com.cryptocarver.ui;

import com.cryptocarver.model.SecretVisibilityProfile;
import javafx.scene.control.TextInputControl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Random throwaway public certificates are characterized by shape and verification, never bytes/date. */
@Tag("ui")
@EnabledIfSystemProperty(named="runUiTests", matches="true")
class EmvOdaCharacterizationUITest extends EmvExtractionCharacterizationSupport {
    private static final String INVENTED_KEY = "00112233445566778899AABBCCDDEEFF";
    private static final List<String> FIELDS = List.of("odaCaModulusArea", "odaCaExponentField",
            "odaIssuerCertificateArea", "odaIssuerRemainderField", "odaIssuerExponentField",
            "odaIccCertificateArea", "odaIccRemainderField", "odaIccExponentField", "odaStaticDataArea",
            "odaPanField", "odaSsadArea", "odaSdadArea", "odaTerminalDataField", "odaCidField",
            "odaTransactionDataArea", "odaResultArea");
    @Test void issueVerifyTamperClearAndPrivacyKeepTheirTranscript() throws Exception {
        List<String> lines = new ArrayList<>(), violations = new ArrayList<>();
        onFx(() -> {
            EMVController c=controller();
            for (SecretVisibilityProfile profile : SecretVisibilityProfile.values()) {
                com.cryptocarver.model.AppSettings.getInstance().setSecretVisibilityProfile(profile);
                resetShared();
                // A retained invented secret input must not get serialized by an ODA history recipe.
                ((TextInputControl)get(c,"imkField")).setText(INVENTED_KEY);
                c.handleOdaIssueTestCard();
                for(String field: List.of("odaCaModulusArea","odaIssuerCertificateArea","odaIccCertificateArea",
                        "odaSsadArea","odaSdadArea","odaPanField"))
                    assertFalse(((TextInputControl)get(c,field)).getText().isBlank(),field);
                assertEquals(256,((TextInputControl)get(c,"odaCaModulusArea")).getText().length());
                lines.add(profile+" issued=populated ca-bytes=128");
                inspectSurfaces(profile,"ODA issue",List.of(INVENTED_KEY),shell,root,lines,violations);
                c.handleOdaRecoverKeys();
                String recovered=((TextInputControl)get(c,"odaResultArea")).getText();
                assertTrue(recovered.contains("Issuer Public Key Certificate"));
                assertTrue(recovered.contains("ICC Public Key Certificate"));
                assertFalse(recovered.contains("FAILED"));
                lines.add(profile+" recovery=issuer+icc passed");
                inspectSurfaces(profile,"ODA recovery",List.of(INVENTED_KEY),shell,root,lines,violations);
                c.handleOdaVerifySda();
                assertTrue(((TextInputControl)get(c,"odaResultArea")).getText().contains("PASSED"));
                lines.add(profile+" SDA=passed");
                inspectSurfaces(profile,"ODA SDA",List.of(INVENTED_KEY),shell,root,lines,violations);
                c.handleOdaVerifyDda();
                assertTrue(((TextInputControl)get(c,"odaResultArea")).getText().contains("PASSED"));
                lines.add(profile+" DDA=passed");
                inspectSurfaces(profile,"ODA DDA",List.of(INVENTED_KEY),shell,root,lines,violations);
                c.handleOdaVerifyCda();
                assertTrue(((TextInputControl)get(c,"odaResultArea")).getText().contains("PASSED"));
                lines.add(profile+" CDA=passed");
                inspectSurfaces(profile,"ODA CDA",List.of(INVENTED_KEY),shell,root,lines,violations);
                TextInputControl data=(TextInputControl)get(c,"odaStaticDataArea");
                data.appendText("00"); c.handleOdaRecoverKeys();
                assertTrue(((TextInputControl)get(c,"odaResultArea")).getText().contains("FAILED"));
                lines.add(profile+" tampered-static-data=failed");
                int history=shell.getHistoryManager().getHistoryItems().size();
                var shelfBefore=List.copyOf(shelf.getEntries());
                c.handleOdaClear();
                for(String field:FIELDS) assertEquals("",((TextInputControl)get(c,field)).getText());
                assertEquals(history,shell.getHistoryManager().getHistoryItems().size());
                assertEquals(shelfBefore,shelf.getEntries());
                lines.add(profile+" clear=empty shared-history+shelf=preserved");
            }
            assertTrue(violations.isEmpty(),String.join("\n",violations));
        });
        pinTranscript("emv-oda-75","4e43fad065dbf13eb60bb6c20e0ee54145a83d9ef29873643aa66f09fce66981",lines);
    }
}
