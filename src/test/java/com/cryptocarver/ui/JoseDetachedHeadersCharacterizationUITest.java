package com.cryptocarver.ui;

import com.cryptocarver.crypto.*;
import com.cryptocarver.model.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.jwk.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named="runUiTests",matches="true")
class JoseDetachedHeadersCharacterizationUITest {
    @BeforeAll static void start() throws Exception { JoseCharacterizationSupport.startFx(); }
    @Test void editedHeadersSerializationsErrorsAndPrivacy() throws Exception {
        UiTestLifecycleExtension.onFx(() -> {
            try(var p=new JoseCharacterizationSupport()) {
                assertNotNull(p.area("detachedProtectedHeaderArea"),"Detached protected header editor is missing");
                var fixture=new JoseTestPki();
                JWK publicKey=new RSAKey.Builder((java.security.interfaces.RSAPublicKey)fixture.leafKey.getPublic()).keyID("invented-detached-73").build();
                Map<String,Object> custom=new LinkedHashMap<>(fixture.header(fixture.chain()).toJSONObject());
                custom.remove("alg");custom.put("kid","invented-detached-73");custom.put("typ","JOSE");custom.put("cty","text/plain");custom.put("jwk",publicKey.toJSONObject());custom.put("invented","header-73");
                String header=com.nimbusds.jose.util.JSONObjectUtils.toJSONString(custom);
                for(LanguagePreference language:List.of(LanguagePreference.EN,LanguagePreference.ES)) {
                    p.language(language);p.line(language+"_label",p.label("detachedProtectedHeaderLabel").getText());
                    for(SecretVisibilityProfile profile:SecretVisibilityProfile.values()) {
                        AppSettings.getInstance().setSecretVisibilityProfile(profile);
                        for(String serialization:List.of("Compact","Flattened JSON","General JSON")) {
                            for(boolean unencoded:List.of(false,true)) {
                                p.combo("detachedAlgoCombo").setValue("RS256");p.combo("detachedSerializationCombo").setValue(serialization);
                                p.check("detachedUnencodedCheck").setSelected(unencoded);
                                p.area("detachedSigningKeyArea").setText(fixture.privatePem());
                                p.area("detachedVerificationKeyArea").setText(new JWKSet(publicKey).toString());
                                p.area("detachedProtectedHeaderArea").setText(header);p.area("detachedPayloadArea").setText("invented detached . payload 73");
                                p.invoke("handleGenerateDetachedJWS");String token=p.area("detachedTokenArea").getText();
                                assertTrue(JOSEService.verifyDetachedJWS(token,p.area("detachedPayloadArea").getText(),"RS256",new JWKSet(publicKey).toString()));
                                p.invoke("handleVerifyDetachedJWS");assertTrue(p.reporter.result.getDetails().stream().anyMatch(d->d.name().equals("Result")&&d.value().equals("VALID")));
                                p.line(profile+"_"+serialization+"_"+unencoded,"custom/kid/typ/cty/x5c/thumbprints/jwk-preserved;JWKS-kid-valid;detached-payload");
                                p.privacy(p.reporter.result,fixture.privatePem());
                            }
                        }
                        p.area("detachedProtectedHeaderArea").setText("{\"alg\":\"HS256\"}");p.reporter.error=null;p.invoke("handleGenerateDetachedJWS");
                        assertTrue(p.reporter.error.contains("cannot override 'alg'"));p.line(profile+"_reserved","readable;alg-override-rejected");
                    }
                }
                p.digest("TO_BE_FILLED");
            }catch(Exception e){throw new RuntimeException(e);}
        });
    }
}
