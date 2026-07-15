package com.cryptoforge.model;

import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict ${variable} expansion for portable recipes. */
public final class RecipeVariables {
    private static final Pattern VARIABLE = Pattern.compile("\\$\\{([A-Za-z][A-Za-z0-9_.-]{0,63})}");
    private RecipeVariables() { }

    public static String resolve(String template, Map<String, String> variables) {
        if (template == null) return "";
        Map<String, String> values = variables == null ? Map.of() : variables;
        Matcher matcher = VARIABLE.matcher(template);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!values.containsKey(name)) throw new IllegalArgumentException("Recipe variable is not defined: " + name);
            matcher.appendReplacement(output, Matcher.quoteReplacement(values.get(name) == null ? "" : values.get(name)));
        }
        matcher.appendTail(output);
        if (output.toString().contains("${")) throw new IllegalArgumentException("Invalid recipe variable expression");
        return output.toString();
    }

    /** Returns the distinct placeholders used by a recipe value, in encounter order. */
    public static Set<String> referencedVariables(String template) {
        if (template == null || template.isEmpty()) return Set.of();
        Matcher matcher = VARIABLE.matcher(template);
        Set<String> names = new LinkedHashSet<>();
        while (matcher.find()) names.add(matcher.group(1));
        if (template.contains("${") && names.isEmpty()) {
            throw new IllegalArgumentException("Invalid recipe variable expression");
        }
        return Collections.unmodifiableSet(names);
    }
}
