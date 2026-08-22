package com.cryptocarver.ui;

import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks in that portable-configuration sections resolve on a translated UI.
 *
 * <p>Routes name their section in canonical English while the pane on screen shows the
 * active translation. Matching only the canonical name made every translated section
 * unreachable, so exporting a screen configuration threw "Unable to locate configuration
 * section" for anyone not running the app in English. The rest of the suite runs in
 * English by design, which is exactly why this regression needs its own Spanish test.</p>
 */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class LocalizedSectionResolutionUITest {

    private static boolean jfxIsSetup;

    @BeforeAll
    static void initJFX() {
        if (jfxIsSetup) return;
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyRunning) {
            // Another UI test class in the same JVM already started the toolkit.
        }
        jfxIsSetup = true;
    }

    @AfterAll
    static void restoreLanguage() throws Exception {
        runAndWait(() -> I18nService.getInstance().setPreference(LanguagePreference.EN));
    }

    @Test
    void everyPortableSectionResolvesWhileTheUiIsSpanish() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            I18nService.getInstance().setPreference(LanguagePreference.ES);
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.setResources(I18nService.getInstance().getBundle());
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertEquals("es", I18nService.getInstance().getLocale().getLanguage(),
                "The controller must have been built with the Spanish bundle for this to prove anything");

        Map<UiNavigationRegistry.Route, String> representative = new LinkedHashMap<>();
        UiNavigationRegistry.routes().forEach((operation, route) -> {
            if (PORTABLE_MODULES.contains(route.module())) representative.putIfAbsent(route, operation);
        });
        assertTrue(representative.size() > 20, "Expected the registry to expose the portable routes");

        List<String> unresolved = new ArrayList<>();
        runAndWait(() -> representative.forEach((route, operation) -> {
            try {
                controllerRef.get().navigateTo(operation);
                assertEquals(route.module().name(),
                        controllerRef.get().captureActiveScreenConfiguration().module(), operation);
            } catch (RuntimeException failure) {
                unresolved.add(operation + " -> " + failure.getMessage());
            }
        }));

        assertEquals(List.of(), unresolved,
                "Every portable section must resolve with the UI in Spanish");
    }

    /**
     * Navigating in Spanish must open the pane English opens.
     *
     * <p>Comparing the accordion of the module that owns each route, not the whole screen: the
     * modules share one window and sequencing leaves unrelated accordions in states that have
     * nothing to do with the route under test.</p>
     */
    @Test
    void navigationExpandsTheSamePaneInSpanishAsInEnglish() throws Exception {
        // operation -> the accordion that route belongs to
        Map<String, String> cases = new LinkedHashMap<>();
        cases.put("Key Generation", "symmetric");
        cases.put("Key Derivation (KDF)", "symmetric");
        cases.put("Combine Components", "symmetric");
        cases.put("RSA Key Generation", "asymmetric");
        cases.put("Dilithium (ML-DSA)", "pqc");
        cases.put("Sign", "authentication");
        cases.put("Encode PIN Block", "payments");
        cases.put("CMS Inspector", "certificates");

        Map<String, Integer> english = new LinkedHashMap<>();
        runAndWait(() -> {
            I18nService.getInstance().setPreference(LanguagePreference.EN);
            ModernMainController controller = load();
            cases.forEach((operation, owner) -> {
                controller.navigateTo(operation);
                english.put(operation, expandedIndex(controller, owner));
            });
        });

        // Guards against the whole test passing because nothing expands in either language.
        english.forEach((operation, index) -> assertTrue(index >= 0,
                "Baseline is broken: English does not expand a pane for " + operation));

        List<String> mismatches = new ArrayList<>();
        runAndWait(() -> {
            I18nService.getInstance().setPreference(LanguagePreference.ES);
            ModernMainController controller = load();
            cases.forEach((operation, owner) -> {
                controller.navigateTo(operation);
                int spanish = expandedIndex(controller, owner);
                if (spanish != english.get(operation)) {
                    mismatches.add(operation + " (English " + english.get(operation)
                            + ", Spanish " + spanish + ")");
                }
            });
        });

        assertEquals(List.of(), mismatches, "Spanish navigation must open the same pane English does");
    }

    private ModernMainController load() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
            loader.setResources(I18nService.getInstance().getBundle());
            loader.load();
            return loader.getController();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Index of the open pane in the named module's accordion; -1 when none is open. */
    private static int expandedIndex(ModernMainController controller, String owner) {
        javafx.scene.Node container = switch (owner) {
            case "symmetric" -> field(field(controller, "keysContainerController"), "symmetricKeysContainer");
            case "asymmetric" -> field(field(controller, "keysContainerController"), "asymmetricKeysContainer");
            case "pqc" -> field(field(controller, "postQuantumContainerController"), "pqcAccordion");
            case "authentication" -> field(controller, "authenticationContainer");
            case "payments" -> field(controller, "paymentsContainer");
            case "certificates" -> field(controller, "certificatesContainer");
            default -> throw new IllegalArgumentException("Unknown accordion owner: " + owner);
        };
        javafx.scene.control.Accordion accordion = accordionIn(container);
        if (accordion == null) return -1;
        return accordion.getPanes().indexOf(accordion.getExpandedPane());
    }

    private static javafx.scene.control.Accordion accordionIn(javafx.scene.Node node) {
        if (node instanceof javafx.scene.control.Accordion accordion) return accordion;
        if (node instanceof javafx.scene.Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                javafx.scene.control.Accordion found = accordionIn(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object owner, String name) {
        try {
            java.lang.reflect.Field f = owner.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return (T) f.get(owner);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot read " + name + " from " + owner.getClass().getSimpleName(), e);
        }
    }

    private static final java.util.Set<UiNavigationRegistry.Module> PORTABLE_MODULES =
            java.util.EnumSet.of(
                    UiNavigationRegistry.Module.JOSE,
                    UiNavigationRegistry.Module.KEYS_SYMMETRIC,
                    UiNavigationRegistry.Module.KEYS_ASYMMETRIC,
                    UiNavigationRegistry.Module.CERTIFICATES,
                    UiNavigationRegistry.Module.GENERIC,
                    UiNavigationRegistry.Module.POST_QUANTUM,
                    UiNavigationRegistry.Module.XML_SECURITY,
                    UiNavigationRegistry.Module.WSS_SECURITY,
                    UiNavigationRegistry.Module.EMV,
                    UiNavigationRegistry.Module.CIPHER,
                    UiNavigationRegistry.Module.AUTHENTICATION,
                    UiNavigationRegistry.Module.PAYMENTS);

    private static void runAndWait(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(60, TimeUnit.SECONDS)) throw new AssertionError("Timed out on the FX thread");
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
}
