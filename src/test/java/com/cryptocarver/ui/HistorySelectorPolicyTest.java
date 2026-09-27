package com.cryptocarver.ui;

import javafx.scene.control.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class HistorySelectorPolicyTest {
    private static final Set<String> OLD_TOKENS = Set.of("kbpk", "cvk", "pvk", "pin", "pan", "cvv",
            "key", "password", "secret", "private", "certificate", "cert", "iv", "nonce", "aad",
            "salt", "token", "mac", "signature", "input", "payload", "info", "verify", "tag");
    private static final Set<String> SELECTORS = Set.of("ComboBox", "ChoiceBox", "CheckBox", "Spinner", "RadioButton", "ToggleButton");
    static class AliasController {
        @javafx.fxml.FXML public ComboBox<String> keyLabStatusFilterCombo = new ComboBox<>();
        AliasController() { keyLabStatusFilterCombo.getItems().add("Active Only"); keyLabStatusFilterCombo.setValue("Active Only"); }
    }

    @BeforeAll static void startToolkit() {
        try { javafx.application.Platform.startup(() -> {}); }
        catch (IllegalStateException alreadyStarted) { /* toolkit already running */ }
    }

    @Test
    void everyFxmlSelectorIsNonEditableAndClassifiedAsNonSecret() throws Exception {
        forEachFxmlControl((file, element, id) -> {
            String type = element.getTagName();
            if (hasSelectorSuffix(id)) assertTrue(SELECTORS.contains(type), file + ":" + id + " suffix is reserved for selectors");
            if (!SELECTORS.contains(type)) return;
            if (type.equals("ComboBox") && Boolean.parseBoolean(element.getAttribute("editable"))) {
                assertFalse(hasSelectorSuffix(id), file + ":" + id + " editable combo is text");
                assertFalse(UiStateSnapshot.isHistorySensitiveField(id), file + ":" + id + " text name has no secret token");
                return;
            }
            assertFalse(Boolean.parseBoolean(element.getAttribute("editable")), file + ":" + id + " must not be editable");
            assertTrue(hasSelectorSuffix(id), file + ":" + id + " selector id needs a canonical suffix");
            assertFalse(UiStateSnapshot.isHistorySensitiveField(id), file + ":" + id);
        });
    }

    @Test
    void everyPreviouslySensitiveFxmlTextFieldRemainsSensitive() throws Exception {
        forEachFxmlControl((file, element, id) -> {
            if (!Set.of("TextField", "TextArea", "PasswordField").contains(element.getTagName())) return;
            if (oldSensitive(id)) assertTrue(UiStateSnapshot.isHistorySensitiveField(id), file + ":" + id);
        });
    }

    @Test
    void codeCreatedControllerFieldsFollowTheSameInventoryPolicy() throws Exception {
        Set<String> fxmlIds = new java.util.HashSet<>();
        forEachFxmlControl((file, element, id) -> fxmlIds.add(id));
        Pattern declaration = Pattern.compile("^ {4}(?:private|protected|public)\\s+(?:final\\s+)?(TextField|TextArea|PasswordField|ComboBox(?:<[^;=]+?>)?|ChoiceBox(?:<[^;=]+?>)?|CheckBox|Spinner(?:<[^;=]+?>)?|RadioButton|ToggleButton)\\s+(\\w+)\\s*(?:=|;)");
        Path dir = Path.of("src/main/java/com/cryptocarver/ui");
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith("Controller.java")).toList()) {
                for (String line : Files.readAllLines(file)) {
                    var matcher = declaration.matcher(line);
                    if (!matcher.find()) continue;
                    String type = matcher.group(1), id = matcher.group(2);
                    if (fxmlIds.contains(id)) continue;
                    boolean selector = !type.equals("TextField") && !type.equals("TextArea") && !type.equals("PasswordField");
                    if (selector) {
                        assertTrue(hasSelectorSuffix(id), file.getFileName() + ":" + id + " needs selector suffix");
                        assertFalse(UiStateSnapshot.isHistorySensitiveField(id), file.getFileName() + ":" + id);
                    } else if (oldSensitive(id)) {
                        assertTrue(UiStateSnapshot.isHistorySensitiveField(id), file.getFileName() + ":" + id);
                    }
                }
            }
        }
    }

    @Test
    void selectorSuffixesCoverAllSupportedSelectorTypesAndEditableCombosRemainText() {
        assertFalse(UiStateSnapshot.isHistorySensitiveField("keySourceCombo", new ComboBox<>()));
        assertFalse(UiStateSnapshot.isHistorySensitiveField("keyLabStatusFilter"));
        ComboBox<String> editable = new ComboBox<>();
        editable.setEditable(true);
        assertTrue(UiStateSnapshot.isHistorySensitiveField("keyInput", editable));
        assertFalse(UiStateSnapshot.isHistorySensitiveField("keySourceCombo"));
        assertFalse(UiStateSnapshot.isHistorySensitiveField("hasPinBlockToggle", new ToggleButton()));
        assertFalse(UiStateSnapshot.isHistorySensitiveField("hasPinBlockRadio", new RadioButton()));
        Spinner<Integer> spinner = new Spinner<>(0, 10, 2);
        spinner.setEditable(true);
        assertFalse(UiStateSnapshot.isHistorySensitiveField("pinBlockSpinner", spinner));
    }

    @Test
    void secretPromptCountsOnlyNonemptyTextSecrets() {
        var emptyState = java.util.Map.<String, Object>of(
                "CipherController.symKeySourceCombo", "Manual Input",
                "CipherController.symmetricKeyField", "");
        long emptyCount = emptyState.entrySet().stream()
                .filter(entry -> UiStateSnapshot.holdsSecretValue(entry.getKey(), entry.getValue())).count();
        assertEquals(0, emptyCount);
        var filledState = new java.util.LinkedHashMap<>(emptyState);
        filledState.put("CipherController.symmetricKeyField", "invented-test-key");
        long filledCount = filledState.entrySet().stream()
                .filter(entry -> UiStateSnapshot.holdsSecretValue(entry.getKey(), entry.getValue())).count();
        assertEquals(1, filledCount);
    }

    @Test
    void oldRedactedSelectorLeavesItsDefaultInPlace() {
        var controller = new UiStateSnapshotTest.DummyController();
        UiStateSnapshot.restoreHistoryRecipe(controller,
                java.util.Map.of("DummyController.algoCombo", "[REDACTED_SECRET]"));
        assertEquals("AES", controller.algoCombo.getValue());
        AliasController aliases = new AliasController();
        UiStateSnapshot.restoreHistoryRecipe(aliases,
                java.util.Map.of("AliasController.keyLabStatusFilter", "[REDACTED_SECRET]"));
        assertEquals("Active Only", aliases.keyLabStatusFilterCombo.getValue());
    }

    private static boolean hasSelectorSuffix(String id) {
        return List.of("Combo", "Choice", "Check", "Spinner", "Radio", "Toggle").stream().anyMatch(id::endsWith);
    }

    private static boolean oldSensitive(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.equals("certcnfield")) return false;
        return OLD_TOKENS.stream().anyMatch(lower::contains);
    }

    private static void forEachFxmlControl(ControlConsumer consumer) throws Exception {
        Path dir = Path.of("src/main/resources/fxml");
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".fxml")).toList()) {
                var factory = DocumentBuilderFactory.newInstance();
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                NodeList all = factory.newDocumentBuilder().parse(file.toFile()).getElementsByTagName("*");
                for (int i = 0; i < all.getLength(); i++) {
                    Element e = (Element) all.item(i);
                    String id = e.getAttribute("fx:id");
                    if (!id.isEmpty()) consumer.accept(file.getFileName().toString(), e, id);
                }
            }
        }
    }

    @FunctionalInterface private interface ControlConsumer { void accept(String file, Element element, String id); }
}
