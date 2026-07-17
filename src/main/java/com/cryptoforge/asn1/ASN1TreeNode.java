package com.cryptoforge.asn1;

import java.util.ArrayList;
import java.util.List;

/**
 * Tree node representing an ASN.1 structure element
 */
public class ASN1TreeNode {
    private String label;           // Display label (e.g., "SEQUENCE", "INTEGER", "OID: sha256WithRSA")
    private String tag;             // ASN.1 tag (e.g., "SEQUENCE", "INTEGER [2]", "BIT STRING")
    private int tagNumber;          // Tag number
    private boolean constructed;    // True if constructed (has children)
    private byte[] rawValue;        // Raw encoded value
    private String decodedValue;    // Decoded/interpreted value
    private int depth;              // Depth in tree (for formatting)
    private int length;             // Length in bytes
    private List<ASN1TreeNode> children;

    public ASN1TreeNode(String label, String tag, int tagNumber, boolean constructed,
                       byte[] rawValue, String decodedValue, int depth, int length) {
        this.label = label;
        this.tag = tag;
        this.tagNumber = tagNumber;
        this.constructed = constructed;
        this.rawValue = rawValue;
        this.decodedValue = decodedValue;
        this.depth = depth;
        this.length = length;
        this.children = new ArrayList<>();
    }

    public void addChild(ASN1TreeNode child) {
        children.add(child);
    }

    // Getters
    public String getLabel() { return label; }
    public String getTag() { return tag; }
    public int getTagNumber() { return tagNumber; }
    public boolean isConstructed() { return constructed; }
    public byte[] getRawValue() { return rawValue; }
    public String getDecodedValue() { return decodedValue; }
    public int getDepth() { return depth; }
    public int getLength() { return length; }
    public List<ASN1TreeNode> getChildren() { return children; }

    // Setter for label (for contextual labeling)
    public void setLabel(String label) { this.label = label; }

    /**
     * Get string representation with indentation
     */
    public String toIndentedString() {
        return toIndentedString(false);
    }

    /**
     * Get string representation with indentation
     * @param fullExport If true, don't truncate any content
     */
    public String toIndentedString(boolean fullExport) {
        StringBuilder sb = new StringBuilder();
        appendToStringBuilder(sb, 0, fullExport);
        return sb.toString();
    }

    private void appendToStringBuilder(StringBuilder sb, int indent, boolean fullExport) {
        // Add indentation
        for (int i = 0; i < indent; i++) {
            sb.append("  ");
        }

        // Add tree characters
        if (indent > 0) {
            sb.append("├─ ");
        }

        // Add label
        sb.append(label);

        // Add length info only if not already in label (e.g., "INTEGER (256 bit)")
        if (!label.endsWith("bit)")) {
            sb.append(" (").append(length).append(" bytes)");
        }

        // Add decoded value if available
        if (decodedValue != null && !decodedValue.isEmpty()) {
            // For full export, ensure we show complete hex values
            String valueToShow = decodedValue;
            if (fullExport && decodedValue.contains("...")) {
                // This means it was truncated - we'd need to regenerate
                // For now, just show what we have
                valueToShow = decodedValue;
            }
            sb.append(": ").append(valueToShow);
        }

        sb.append("\n");

        // Add children
        for (ASN1TreeNode child : children) {
            child.appendToStringBuilder(sb, indent + 1, fullExport);
        }
    }

    @Override
    public String toString() {
        return label + (decodedValue != null ? ": " + decodedValue : "");
    }
}
