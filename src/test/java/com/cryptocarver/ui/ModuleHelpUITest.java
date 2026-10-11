package com.cryptocarver.ui;

import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.model.OperationRegistry;
import com.cryptocarver.model.help.HelpContext;
import com.cryptocarver.model.help.HelpContextState;
import com.cryptocarver.model.help.ModuleHelpCatalog;
import com.cryptocarver.service.I18nService;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ModuleHelpUITest {
    @BeforeAll static void startFx() {
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException alreadyStarted) { Platform.setImplicitExit(false); }
    }

    @Test void everyDeclaredControlExistsInItsRouteAndAnswersShiftF1WithItsOwnHelp() throws Exception {
        LanguagePreference previous = I18nService.getInstance().getPreference();
        UiTestLifecycleExtension.onFx(() -> {
            try {
                I18nService.getInstance().setPreference(LanguagePreference.EN);
                var loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
                Parent root = loader.load();
                ModernMainController shell = loader.getController();
                Stage owner = new Stage(); owner.setScene(new Scene(root, 1800, 900)); owner.show();
                try {
                    HelpContextState state = field(shell, "contextualHelpState");
                    for (ModuleHelpCatalog.Spec spec : ModuleHelpCatalog.specs()) {
                        String route = OperationRegistry.getInstance().getById(spec.id()).orElseThrow().getNavigationPath();
                        shell.navigateTo(route);
                        root.applyCss(); root.layout();
                        Map<String, List<Node>> nodes = ModuleHelpInstaller.nodesById(root);
                        for (ModuleHelpCatalog.Field declared : spec.fields()) {
                            for (String id : declared.controls()) {
                                assertTrue(nodes.containsKey(id), spec.id() + " control not on screen: " + id);
                                ContextualHelpTestSupport.shiftF1(nodes.get(id).get(0));
                                HelpContext context = state.current();
                                assertEquals(spec.id(), context.operationId(), id);
                                assertEquals(declared.id(), context.fieldId(), id);
                                assertEquals(spec.actionFor(declared.id(), context.actionId()), context.actionId(), id);
                            }
                        }
                        for (ModuleHelpCatalog.Action declared : spec.actions()) {
                            for (String id : declared.controls()) {
                                assertTrue(nodes.containsKey(id), spec.id() + " control not on screen: " + id);
                                ContextualHelpTestSupport.shiftF1(nodes.get(id).get(0));
                                HelpContext context = state.current();
                                assertEquals(spec.id(), context.operationId(), id);
                                assertEquals(declared.id(), context.actionId(), id);
                                assertEquals("", context.fieldId(), id);
                            }
                        }
                    }
                } finally { owner.close(); }
            } catch (Exception failure) { throw new RuntimeException(failure); }
            finally { I18nService.getInstance().setPreference(previous); }
        }, 120);
    }

    @SuppressWarnings("unchecked") private static <T> T field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return (T) field.get(target);
    }
}
