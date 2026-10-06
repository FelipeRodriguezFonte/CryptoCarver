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
                assertNotNull(p.area("detachedProtectedHeaderSecretArea"),"Detached protected header editor is missing");
                String[] info=new String[1];
                p.controller.setReporter(new StatusReporter() {
                    @Override public void updateStatus(String message) { p.reporter.updateStatus(message); }
                    @Override public void updateInspector(String operation,byte[] input,byte[] output,List<OperationDetail> details) { p.reporter.updateInspector(operation,input,output,details); }
                    @Override public void publish(OperationResult result) { p.reporter.publish(result); }
                    @Override public void showError(String title,String message) { p.reporter.showError(title,message); }
                    @Override public void showInfo(String title,String message) { info[0]=message; }
                });
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
                                AppSettings.getInstance().setSecretVisibilityProfile(profile);
                                p.combo("detachedAlgoCombo").setValue("RS256");p.combo("detachedSerializationCombo").setValue(serialization);
                                p.check("detachedUnencodedCheck").setSelected(unencoded);
                                p.area("detachedSigningKeyArea").setText(fixture.privatePem());
                                p.area("detachedVerificationKeyArea").setText(new JWKSet(publicKey).toString());
                                p.area("detachedProtectedHeaderSecretArea").setText(header);p.area("detachedPayloadArea").setText("invented detached . payload 73");
                                p.invoke("handleGenerateDetachedJWS");String token=p.area("detachedTokenArea").getText();
                                JWSObject object;
                                if(serialization.equals("Compact")) object=JWSObject.parse(token,new Payload(p.area("detachedPayloadArea").getText()));
                                else {
                                    Map<String,Object> map=com.nimbusds.jose.util.JSONObjectUtils.parse(token);
                                    if(map.get("signatures") instanceof List<?> signatures) map=(Map<String,Object>)signatures.get(0);
                                    object=new JWSObject(new com.nimbusds.jose.util.Base64URL((String)map.get("protected")),new Payload(p.area("detachedPayloadArea").getText()),new com.nimbusds.jose.util.Base64URL((String)map.get("signature")));
                                }
                                for(var entry:custom.entrySet()) assertEquals(entry.getValue(),object.getHeader().toJSONObject().get(entry.getKey()),entry.getKey());
                                assertEquals(!unencoded,object.getHeader().isBase64URLEncodePayload());
                                if(unencoded) assertEquals(Set.of("b64"),object.getHeader().getCriticalParams());
                                assertTrue(JOSEService.verifyDetachedJWS(token,p.area("detachedPayloadArea").getText(),"RS256",new JWKSet(publicKey).toString()));
                                if(unencoded) {
                                    assertTrue(info[0].startsWith(language==LanguagePreference.EN ? "Warning:" : "Aviso:"));
                                    p.line(profile+"_b64_warning",info[0]);
                                }
                                p.invoke("handleVerifyDetachedJWS");assertTrue(p.reporter.result.getDetails().stream().anyMatch(d->d.name().equals("Result")&&d.value().equals("VALID")));
                                p.line(profile+"_"+serialization+"_"+unencoded,"custom/kid/typ/cty/x5c/thumbprints/jwk-preserved;JWKS-kid-valid;detached-payload");
                                p.privacy(p.reporter.result,fixture.privatePem());
                            }
                        }
                        p.area("detachedProtectedHeaderSecretArea").setText("{\"alg\":\"HS256\"}");p.reporter.error=null;p.invoke("handleGenerateDetachedJWS");
                        assertTrue(p.reporter.error.contains("alg"));p.line(profile+"_reserved","readable;alg-override-rejected");
                        JWK privateKey=new RSAKey.Builder((java.security.interfaces.RSAPublicKey)fixture.leafKey.getPublic()).privateKey(fixture.leafKey.getPrivate()).build();
                        String secret=((RSAKey)privateKey).getPrivateExponent().toString();
                        p.area("detachedProtectedHeaderSecretArea").setText("{\"jwk\":"+privateKey.toJSONString()+"}");
                        AppSettings.getInstance().setSecretVisibilityProfile(profile);
                        p.invoke("handleGenerateDetachedJWS");assertFalse(p.reporter.error.contains(secret));
                        String recipe=UiStateSnapshot.captureHistoryRecipe(p.controller).toString();
                        if(profile!=SecretVisibilityProfile.FULL_LAB) {
                            assertFalse(recipe.contains(secret));
                            var tracker=new ResultAreaTracker();var editor=p.area("detachedProtectedHeaderSecretArea");tracker.register(editor);tracker.markUpdated(editor);tracker.focus(editor);
                            var capture=new ResultCaptureCoordinator(()->null,()->null,()->null,()->null,()->tracker,()->p.reporter.result,()->"JOSE",()->"JOSE",()->AppSettings.getInstance().getSecretVisibilityProfile(),message->{},(title,message)->{},(control,selected)->{});
                            assertFalse(capture.resolveResultText(editor).contains(secret));assertFalse(capture.resolveShelfCaptureText(editor).contains(secret));assertFalse(capture.resolveCurrentOutputText().contains(secret));
                            var reached=new java.util.concurrent.atomic.AtomicBoolean();javafx.event.EventHandler<javafx.scene.input.KeyEvent> sink=event->reached.set(true);editor.addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED,sink);
                            editor.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,"","",javafx.scene.input.KeyCode.C,false,true,false,true));editor.removeEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED,sink);assertFalse(reached.get());
                        }
                        p.line(profile+"_private_header","rejected;generic-error;restricted-editor/history/capture-protected");
                    }
                }
                p.digest("325d384b8f4f546853aabf90b2fa0307f054ec61c3f87752db2edff1ae31693a");
            }catch(Exception e){throw new RuntimeException(e);}
        });
    }
}
