package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.service.I18nService;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class JoseSectionCharacterizationUITest {
    private static final List<String> PANELS = List.of("joseContainer", "jwtSection", "jweSection",
            "jwkSection", "jwaSection", "inspectorSection");
    @BeforeAll static void start() throws Exception { JoseCharacterizationSupport.startFx(); }

    @Test void sectionNamesUnknownAndNullPreservePanelsAndTransitionOrder() throws Exception {
        UiTestLifecycleExtension.onFx(() -> {
            LanguagePreference previous = AppSettings.getInstance().getLanguagePreference();
            try {
                I18nService.getInstance().setPreference(LanguagePreference.EN);
                List<String> names = new ArrayList<>(UiNavigationRegistry.routes().entrySet().stream()
                        .filter(entry -> entry.getValue().module() == UiNavigationRegistry.Module.JOSE)
                        .map(java.util.Map.Entry::getKey).sorted().toList());
                // Prefix matching is the API contract, including arbitrary suffixes and exact prefixes.
                names.addAll(Arrays.asList("JWT", "JWE", "JWK", "JWA", "Token Inspector suffix",
                        "JWT invented suffix", "JWE invented suffix", "JWK invented suffix", "JWA invented suffix",
                        "invented-unknown-section-78", "jwt", "", null));
                List<String> transcript = new ArrayList<>();
                for (String name : names) {
                    try (var p = new JoseCharacterizationSupport()) {
                        transcript.add("section=" + (name == null ? "<null>" : name));
                        for (String id : PANELS) {
                            Node node = p.control(id);
                            assertNotNull(node, id);
                            // Start with every panel exposed and the container hidden, so every reset matters.
                            node.setManaged(!id.equals("joseContainer"));
                            node.setVisible(!id.equals("joseContainer"));
                            ChangeListener<Boolean> managed = (obs, oldValue, value) -> transcript.add(id + "/managed=" + value);
                            ChangeListener<Boolean> visible = (obs, oldValue, value) -> transcript.add(id + "/visible=" + value);
                            node.managedProperty().addListener(managed);
                            node.visibleProperty().addListener(visible);
                        }
                        p.controller.showSection(name);
                        for (String id : PANELS) {
                            Node node = p.control(id);
                            transcript.add(id + "=" + node.isVisible() + "/" + node.isManaged());
                        }
                    }
                }
                try (var p = new JoseCharacterizationSupport()) {
                    p.lines.addAll(transcript);
                    p.digest("781e560222264aed10bc3396167b9bafa86991fe0e679204608024052963c81e");
                }
            } catch (Exception failure) { throw new RuntimeException(failure); }
            finally { I18nService.getInstance().setPreference(previous); }
        });
    }
}
