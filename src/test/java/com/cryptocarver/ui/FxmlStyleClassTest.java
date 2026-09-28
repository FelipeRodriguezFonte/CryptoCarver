package com.cryptocarver.ui;

import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FxmlStyleClassTest {
    private static final Pattern STYLE_CLASS_CALL = Pattern.compile(
            "getStyleClass\\(\\)\\.(?:add|addAll|setAll)\\s*\\(([^;]*)\\)", Pattern.DOTALL);
    private static final Pattern STRING_LITERAL = Pattern.compile("\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"");

    @Test
    void styleClassesMustBeSeparateTokens() throws Exception {
        Path root = Path.of("src/main/resources/fxml");
        List<String> violations = new ArrayList<>();
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        try (var paths = Files.walk(root)) {
            for (Path file : paths.filter(path -> path.toString().endsWith(".fxml")).sorted().toList()) {
                var document = factory.newDocumentBuilder().parse(file.toFile());
                var nodes = document.getElementsByTagName("*");
                for (int i = 0; i < nodes.getLength(); i++) {
                    var element = (org.w3c.dom.Element) nodes.item(i);
                    if (element.hasAttribute("styleClass") && element.getAttribute("styleClass").matches(".*\\s+.*")) {
                        violations.add(Path.of(".").toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize()) + ": styleClass=\""
                                + element.getAttribute("styleClass") + "\"");
                    }
                }
            }
        }

        Path javaRoot = Path.of("src/main/java");
        try (var paths = Files.walk(javaRoot)) {
            for (Path file : paths.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                String source = Files.readString(file);
                Matcher calls = STYLE_CLASS_CALL.matcher(source);
                while (calls.find()) {
                    Matcher literals = STRING_LITERAL.matcher(calls.group(1));
                    while (literals.find()) {
                        if (literals.group(1).matches(".*\\s+.*")) {
                            int line = 1 + (int) source.substring(0, calls.start()).chars().filter(ch -> ch == '\n').count();
                            violations.add(Path.of("src/main/java").relativize(file) + ":" + line
                                    + ": style class literal contains whitespace: \"" + literals.group(1) + "\"");
                        }
                    }
                }
            }
        }

        violations.sort(Comparator.naturalOrder());
        assertTrue(violations.isEmpty(), "Whitespace inside style classes:\n" + String.join("\n", violations));
    }
}
