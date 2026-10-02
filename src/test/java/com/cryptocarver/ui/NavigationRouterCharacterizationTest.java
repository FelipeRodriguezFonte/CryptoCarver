package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import javafx.application.Platform;
import javafx.scene.Node;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.function.Executable;
import java.util.ArrayList;
import java.util.List;

/** Routing behavior pinned against the production FXML before router extraction. */
@Tag("ui")
class NavigationRouterCharacterizationTest {
    @TempDir Path dir;
    private AppSettings settings;
    private String previousLastRoute;
    @BeforeAll static void fx() throws Exception { CountDownLatch l=new CountDownLatch(1); try { Platform.startup(()->{Platform.setImplicitExit(false);l.countDown();}); } catch(IllegalStateException e){l.countDown();} assertTrue(l.await(15,TimeUnit.SECONDS)); }
    @BeforeEach void settings() { AppSettings.setInstanceForTesting(new AppSettings(dir.resolve("settings.json"))); settings=AppSettings.getInstance(); previousLastRoute=settings.getLastRoute(); }
    @AfterEach void restore() { if(previousLastRoute!=null) settings.setLastRoute(previousLastRoute); AppSettings.resetInstanceForTesting(); }

    @Test void registeredModulesVariantsDynamicUnknownAndStartupRoutes() throws Exception {
        AtomicReference<ModernMainController> ref=new AtomicReference<>();
        fxRun(()->{try {var l=UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");l.load();ref.set(l.getController());}catch(Exception e){throw new RuntimeException(e);}});
        fxRun(()->{ setField(ref.get(),"lastPublishedResultSnapshot",com.cryptocarver.model.OperationResult.forOperation("Hashing").output(new byte[]{1}).build()); setField(ref.get(),"lastPublishedScreen","Hashing"); ref.get().navigateToModule("Symmetric Ciphers"); });
        assertNull(field(ref.get(),"lastPublishedResultSnapshot"),"navigation clears the published result snapshot");
        String[][] routes={{"JOSE","JWT (Signed)"},{"COSE","COSE Sign1"},{"WALLET","SD-JWT VC"},{"EPOCH_CONVERTER","Epoch Converter"},{"JSON_FORMATTER","JSON Formatter"},{"KEYS_SYMMETRIC","Key Generation"},{"KEYS_ASYMMETRIC","RSA Key Generation"},{"CERTIFICATES","Parse Certificate"},{"GENERIC","Hashing"},{"POST_QUANTUM","PQC Key Generation"},{"XML_SECURITY","XML Security"},{"WSS_SECURITY","WSS Security"},{"EMV","EMV Tool"},{"HISTORY","Recent Operations"},{"CLIPBOARD_SHELF","Clipboard Shelf"},{"SAVED_SESSIONS","Saved Sessions"},{"CIPHER","Symmetric Ciphers"},{"AUTHENTICATION","Digital Signatures"},{"PAYMENTS","Payments"},{"PROCESS_DESIGNER","Process Designer"}};
        List<Executable> routeChecks=new ArrayList<>();
        for(String[] pair:routes) routeChecks.add(()->{
            fxRun(()->ref.get().navigateToModule(pair[1]));
            assertEquals(pair[1],field(ref.get(),"currentActiveOperation"));
            if(!pair[0].equals("EPOCH_CONVERTER")&&!pair[0].equals("JSON_FORMATTER")) {
                Node host=(Node)field(ref.get(),hostField(pair[0]));
                assertTrue(host.isVisible(),pair[0]);
                var route=UiNavigationRegistry.resolve(pair[1]).orElseThrow();
                if(pair[0].equals("CIPHER")) assertTrue(((CipherController)field(ref.get(),"cipherController")).symmetricWorkspaceProperty().get());
                if(pair[0].equals("PROCESS_DESIGNER")) assertTrue((boolean)field(ref.get(),"processDesignerWorkspace"));
                if(route.section()!=null && host instanceof ModuleHost && !pair[0].equals("CIPHER") && !pair[0].equals("PROCESS_DESIGNER")) {
                    var accordion=findAccordion(host);
                    assertNotNull(accordion,pair[0]+" accordion");
                    assertNotNull(accordion.getExpandedPane(),pair[0]+" expanded section");
                    var catalog=switch(pair[0]) {
                        case "KEYS_SYMMETRIC","KEYS_ASYMMETRIC" -> ModuleTextCatalog.keys();
                        case "CERTIFICATES" -> ModuleTextCatalog.certificates(); case "GENERIC" -> ModuleTextCatalog.generic();
                        case "POST_QUANTUM" -> ModuleTextCatalog.pqc(); case "XML_SECURITY" -> ModuleTextCatalog.xmlSecurity();
                        case "WSS_SECURITY" -> ModuleTextCatalog.wssSecurity(); case "EMV" -> ModuleTextCatalog.emv();
                        case "CIPHER" -> ModuleTextCatalog.cipher(); case "AUTHENTICATION" -> ModuleTextCatalog.authentication();
                        case "PAYMENTS" -> ModuleTextCatalog.payments(); case "PROCESS_DESIGNER" -> ModuleTextCatalog.processDesigner();
                        default -> java.util.Map.<String,String>of();
                    };
                    assertTrue(ModulePaneMatcher.matches(accordion.getExpandedPane(),route.section(),catalog),pair[0]+" section "+route.section());
                }
            }
        });
        fxRun(()->ref.get().navigateToModule("ASN.1 Encode")); assertEquals("ASN.1 Encode",field(ref.get(),"currentActiveOperation"));
        fxRun(()->ref.get().navigateToModule("ASN.1 Decode")); assertEquals("ASN.1 Decode",field(ref.get(),"currentActiveOperation"));
        fxRun(()->ref.get().navigateToModule("Export History")); assertEquals("Export History",field(ref.get(),"currentActiveOperation"));
        fxRun(()->ref.get().navigateTo("Hashing: SHA-256")); assertEquals("Hashing: SHA-256",field(ref.get(),"currentActiveOperation")); assertTrue(((ModuleHost)field(ref.get(),"genericContainer")).isVisible());
        fxRun(()->ref.get().navigateTo("no such route")); assertEquals("no such route",field(ref.get(),"currentActiveOperation"));
        fxRun(()->{ settings.setLastRoute("JWT (Signed)"); invoke(ref.get(),"restoreStartupLastRoute"); }); assertEquals("JWT (Signed)",field(ref.get(),"currentActiveOperation"));
        fxRun(()->{ settings.setLastRoute("invalid"); invoke(ref.get(),"restoreStartupLastRoute"); }); assertEquals("JWT (Signed)",field(ref.get(),"currentActiveOperation"));
        assertAll("module routes",routeChecks);
    }
    private static String hostField(String m){return switch(m){case "JOSE"->"jose";case "COSE"->"cose";case "WALLET"->"wallet";case "EPOCH_CONVERTER"->"epochConverter";case "JSON_FORMATTER"->"jsonFormatter";case "KEYS_SYMMETRIC"->"keysContainer";case "KEYS_ASYMMETRIC"->"keysContainer";case "CERTIFICATES"->"certificatesContainer";case "GENERIC"->"genericContainer";case "POST_QUANTUM"->"postQuantumContainer";case "XML_SECURITY"->"xmlSecurityContainer";case "WSS_SECURITY"->"wssSecurityContainer";case "EMV"->"emvContainer";case "HISTORY"->"historyView";case "CLIPBOARD_SHELF"->"clipboardShelf";case "SAVED_SESSIONS"->"savedSessionsContainer";case "CIPHER"->"cipherContainer";case "AUTHENTICATION"->"authenticationContainer";case "PAYMENTS"->"paymentsContainer";default->"processDesignerContainer";};}
    private static javafx.scene.control.Accordion findAccordion(Node n){if(n instanceof javafx.scene.control.Accordion a)return a;if(n instanceof javafx.scene.Parent p)for(Node child:p.getChildrenUnmodifiable()){var a=findAccordion(child);if(a!=null)return a;}return null;}
    private static Object field(Object o,String n){try{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new AssertionError(e);}}
    private static void setField(Object o,String n,Object value){try{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,value);}catch(Exception e){throw new AssertionError(e);}}
    private static void invoke(Object o,String n){try{var m=o.getClass().getDeclaredMethod(n);m.setAccessible(true);m.invoke(o);}catch(Exception e){throw new AssertionError(e);}}
    private static void fxRun(Runnable r)throws Exception{CountDownLatch l=new CountDownLatch(1);AtomicReference<Throwable> e=new AtomicReference<>();Platform.runLater(()->{try{r.run();}catch(Throwable t){e.set(t);}finally{l.countDown();}});assertTrue(l.await(30,TimeUnit.SECONDS));if(e.get()!=null)throw new AssertionError(e.get());}
}
