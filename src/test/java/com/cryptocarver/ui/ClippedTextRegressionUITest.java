package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.service.I18nService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Five representative production states; full registry audit stays opt-in. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ClippedTextRegressionUITest {
    @Test void spanishLightCaptionsFitDesignerWorkbenchAndInformationDialog() throws Exception {
        ClippedTextAuditTool.onFx(() -> {
            AppSettings settings = AppSettings.getInstance();
            var language = settings.getLanguagePreference();
            String last = settings.getLastRoute();
            double tree = settings.getWorkspaceTreeDividerPosition();
            double inspector = settings.getWorkspaceInspectorDividerPosition();
            var visibility = settings.getSecretVisibilityProfile();
            List<String> counts = new ArrayList<>();
            List<ClippedTextAuditTool.Finding> findings = new ArrayList<>();
            List<String> exclusions = new ArrayList<>();
            try {
                I18nService.getInstance().setPreference(LanguagePreference.ES);
                for (String route : List.of("Process Designer", "Key & Certificate Format Workbench")) {
                    ClippedTextAuditTool.withScreen(route, "light", root ->
                            ClippedTextAuditTool.auditStates(route, root, counts, findings, exclusions));
                    assertEquals(tree, settings.getWorkspaceTreeDividerPosition(), "Tree divider leaked across screens");
                    assertEquals(inspector, settings.getWorkspaceInspectorDividerPosition(), "Inspector divider leaked across screens");
                    assertEquals(visibility, settings.getSecretVisibilityProfile(), "Visibility preference leaked");
                }
                var dialog = ClippedTextAuditTool.infoDialog("light");
                findings.addAll(dialog.findings());
                assertEquals(4, counts.size(), "Two designer and two workbench states must be audited");
                assertTrue(findings.isEmpty(), () -> findings.stream().map(ClippedTextAuditTool.Finding::line)
                        .reduce("Clipped UI captions:\n", (a,b) -> a + b + "\n"));
            } finally {
                settings.setLastRoute(last);
                I18nService.getInstance().setPreference(language);
            }
            return null;
        });
    }
}
