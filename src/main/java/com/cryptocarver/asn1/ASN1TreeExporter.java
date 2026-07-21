package com.cryptocarver.asn1;

import com.google.gson.GsonBuilder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Structured, portable exports for parsed ASN.1 trees. */
public final class ASN1TreeExporter {
    private ASN1TreeExporter() { }

    public static String toJson(ASN1TreeNode root) {
        return new GsonBuilder().setPrettyPrinting().create().toJson(toMap(root));
    }

    public static String toMarkdown(ASN1TreeNode root) {
        StringBuilder out = new StringBuilder("# ASN.1 Structure\n\n");
        appendMarkdown(out, root, 0);
        return out.toString();
    }

    private static Map<String, Object> toMap(ASN1TreeNode node) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("label", node.getLabel()); value.put("tag", node.getTag());
        value.put("tagNumber", node.getTagNumber()); value.put("constructed", node.isConstructed());
        value.put("length", node.getLength());
        if (node.getDecodedValue() != null && !node.getDecodedValue().isBlank()) value.put("decodedValue", node.getDecodedValue());
        value.put("children", node.getChildren().stream().map(ASN1TreeExporter::toMap).toList());
        return value;
    }

    private static void appendMarkdown(StringBuilder out, ASN1TreeNode node, int depth) {
        out.append("  ".repeat(depth)).append("- **").append(node.getLabel()).append("** (`")
                .append(node.getTag()).append("`, ").append(node.getLength()).append(" bytes)");
        if (node.getDecodedValue() != null && !node.getDecodedValue().isBlank()) out.append(": ").append(node.getDecodedValue());
        out.append('\n');
        for (ASN1TreeNode child : node.getChildren()) appendMarkdown(out, child, depth + 1);
    }
}
