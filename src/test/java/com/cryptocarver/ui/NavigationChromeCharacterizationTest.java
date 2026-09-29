package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Production FXML characterization for navigation chrome and format profiles. */
@Tag("ui")
class NavigationChromeCharacterizationTest {
    @TempDir Path dir;
    static boolean fxStarted;
    private LanguagePreference previousLanguage;
    private AppSettings settings;
    private List<String> previousFavorites;
    private String previousLastRoute;

    @BeforeAll static void startFx() throws Exception {
        CountDownLatch latch=new CountDownLatch(1);
        try { Platform.startup(() -> { Platform.setImplicitExit(false); latch.countDown(); }); }
        catch(IllegalStateException already) { latch.countDown(); }
        assertTrue(latch.await(15,TimeUnit.SECONDS)); fxStarted=true;
    }
    @BeforeEach void prepare() {
        previousLanguage=I18nService.getInstance().getPreference();
        AppSettings.setInstanceForTesting(new AppSettings(dir.resolve("settings.json")));
        settings=AppSettings.getInstance(); previousFavorites=settings.getFavorites(); previousLastRoute=settings.getLastRoute();
    }
    @AfterEach void restore() {
        if(settings!=null) {
            for(String item:List.copyOf(settings.getFavorites())) settings.toggleFavorite(item);
            for(String item:previousFavorites) if(!settings.isFavorite(item)) settings.toggleFavorite(item);
            if(previousLastRoute!=null&&!previousLastRoute.isBlank()) settings.setLastRoute(previousLastRoute);
        }
        I18nService.getInstance().setPreference(previousLanguage);
        AppSettings.resetInstanceForTesting();
    }

    @Test void navigationChromeAndFormatProfilesRemainCharacterized() throws Exception {
        AtomicReference<ModernMainController> c=new AtomicReference<>();
        AtomicReference<Button> section=new AtomicReference<>(), module=new AtomicReference<>(), favorite=new AtomicReference<>();
        AtomicReference<Label> op=new AtomicReference<>(), title=new AtomicReference<>(), subtitle=new AtomicReference<>();
        AtomicReference<ComboBox<String>> input=new AtomicReference<>(), output=new AtomicReference<>(), hashTemplate=new AtomicReference<>();
        AtomicReference<GenericController> generic=new AtomicReference<>();
        runFx(() -> {
            try {
                FXMLLoader loader=Fxml.loader("/fxml/main-view-modern.fxml"); loader.load(); c.set(loader.getController());
                section.set(field(c.get(),"breadcrumbSectionBtn")); module.set(field(c.get(),"breadcrumbModuleBtn"));
                favorite.set(field(c.get(),"favoriteToggleBtn")); op.set(field(c.get(),"breadcrumbOperationLabel"));
                title.set(field(c.get(),"contentTitleLabel")); subtitle.set(field(c.get(),"contentSubtitleLabel"));
                input.set(field(c.get(),"inputFormatCombo")); output.set(field(c.get(),"outputFormatCombo"));

            } catch(Exception e) { throw new RuntimeException(e); }
        });
        var i18n=I18nService.getInstance();
        for(LanguagePreference lang:List.of(LanguagePreference.ES,LanguagePreference.EN)) {
            runFx(() -> i18n.setPreference(lang));
            for(String route:List.of("Hashing","Symmetric Ciphers","Key Generation","JWT (Signed)","PIN Generation")) {
                runFx(() -> c.get().navigateToModule(route));
                assertFalse(section.get().getText().isBlank()); assertFalse(op.get().getText().isBlank());
                String sectionKey=switch(route) { case "Hashing" -> "bread.utilities"; case "Symmetric Ciphers" -> "bread.ciphers"; case "Key Generation" -> "bread.symmetricKeys"; case "JWT (Signed)" -> "bread.joseJwt"; default -> "bread.paymentCryptography"; };
                assertEquals(i18n.text(sectionKey),section.get().getText(),"localized breadcrumb section in "+lang);
                assertEquals(route,op.get().getText());
                assertEquals(route, active(c.get()));
                String moduleRoute=(String)module.get().getUserData();
                runFx(() -> module.get().fire());
                if (moduleRoute != null && !moduleRoute.isBlank()) assertEquals(moduleRoute,active(c.get()),"breadcrumb module routes to its own section/module");
                runFx(() -> section.get().fire());
                assertNotNull(field(c.get(),"sidePanel"));
            }
        }
        runFx(() -> {
            c.get().navigateTo("Key Generation");
            assertEquals(i18n.text("bread.symmetricKeys"),title.get().getText());
            assertTrue(subtitle.get().getText().contains("Stable") || subtitle.get().getText().contains("Experimental"));
            c.get().navigateTo("Hashing");
            generic.set(uncheckedField(c.get(),"genericContainerController"));
            hashTemplate.set(uncheckedField(generic.get(),"hashTemplateCombo"));
            assertEquals("Text (UTF-8)",input.get().getValue()); assertEquals("Hexadecimal",output.get().getValue());
            assertFalse(hashTemplate.get().getItems().isEmpty());
            hashTemplate.get().setValue(hashTemplate.get().getItems().get(0));
            invoke(generic.get(),"handleApplyHashTemplate");
            assertEquals("Text (UTF-8)",input.get().getValue()); assertEquals("Hexadecimal",output.get().getValue());
            c.get().setInputFormat("not-a-format"); assertNull(input.get().getValue());
            c.get().setInputFormat("Text"); assertEquals("Text (UTF-8)",input.get().getValue());
            c.get().navigateTo("Symmetric Ciphers"); assertEquals("Hexadecimal",output.get().getValue());
            c.get().navigateTo("JWT (Signed)"); assertTrue(input.get().isDisabled()); assertTrue(output.get().isDisabled());
            c.get().navigateTo("Hashing");
            String route=active(c.get()); c.get().handleToggleFavorite();
            assertEquals("★",favorite.get().getText()); assertTrue(settings.isFavorite(route));
            c.get().handleToggleFavorite(); assertEquals("☆",favorite.get().getText()); assertFalse(settings.isFavorite(route));
        });
    }

    private static <T>T uncheckedField(Object target,String name) { try { return field(target,name); } catch(Exception e) { throw new RuntimeException(e); } }
    private static void invoke(Object target,String method) { try { var m=target.getClass().getDeclaredMethod(method); m.setAccessible(true); m.invoke(target); } catch(Exception e) { throw new RuntimeException(e); } }
    private static String active(ModernMainController controller) { try { return field(controller,"currentActiveOperation"); } catch(Exception e) { throw new RuntimeException(e); } }
    @SuppressWarnings("unchecked") private static <T>T field(Object target,String name) throws Exception {
        var f=target.getClass().getDeclaredField(name); f.setAccessible(true); return (T)f.get(target);
    }
    private static void runFx(Runnable action) throws Exception {
        CountDownLatch done=new CountDownLatch(1); AtomicReference<Throwable> error=new AtomicReference<>();
        Platform.runLater(() -> { try { action.run(); } catch(Throwable t) { error.set(t); } finally { done.countDown(); } });
        assertTrue(done.await(20,TimeUnit.SECONDS)); if(error.get()!=null) throw new AssertionError(error.get());
    }
}
