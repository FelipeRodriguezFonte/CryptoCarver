package com.cryptocarver.crypto;

import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;
import com.upokecenter.numbers.EInteger;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * CBOR (RFC 8949) as something you can actually look at.
 *
 * <p>The app has had CBOR on its classpath all along — {@code cose-java} pulls
 * in {@code com.upokecenter:cbor} — but only ever used it through COSE, so
 * there was no way to see what a binary blob contained before deciding what it
 * was. That is the hole this fills, and it is a prerequisite rather than a
 * convenience: mdoc / mDL (ISO/IEC 18013-5), the other credential format of the
 * European wallet, is CBOR through and through, and its structures nest encoded
 * CBOR inside byte strings in a way that is unreadable without a tool that
 * follows the nesting.</p>
 *
 * <h2>Tag 24, and why it gets special treatment</h2>
 * <p>RFC 8949 §3.4.5.1 defines tag 24 as "Encoded CBOR data item": a byte string
 * whose contents are themselves CBOR. mdoc leans on it heavily — every
 * {@code IssuerSignedItem} arrives as a tag-24 byte string, so that the digest
 * the Mobile Security Object signs covers exact bytes rather than a re-encoding
 * that might differ. A viewer that stops at "byte string, 94 bytes" is useless
 * there, so {@link #tree} decodes tag-24 payloads and keeps walking, marking the
 * boundary where it crossed into embedded content.</p>
 *
 * <p>Diagnostic notation follows RFC 8949 §8: byte strings as {@code h'..'},
 * tags as {@code n(...)}, maps as {@code {k: v}}. It is a reading aid, not an
 * interchange format — {@link #toJson} is the one for feeding other tools.</p>
 */
public final class CborInspector {

    /** Nesting depth at which traversal stops. CBOR permits arbitrary nesting and
     *  a hostile or corrupt input can be deep enough to exhaust the stack; a
     *  laboratory inspector would rather print a marker than die. */
    private static final int MAX_DEPTH = 64;

    /** RFC 8949 §3.4.5.1 — "Encoded CBOR data item". */
    private static final int TAG_ENCODED_CBOR = 24;

    private CborInspector() {
    }

    /**
     * Renders a CBOR item as an indented tree, decoding tag-24 payloads as it
     * goes.
     */
    public static String tree(byte[] cbor) {
        StringBuilder builder = new StringBuilder();
        append(CBORObject.DecodeFromBytes(cbor), builder, 0, null);
        return builder.toString();
    }

    /** Diagnostic notation (RFC 8949 §8) for the whole item, on one line. */
    public static String diagnostic(byte[] cbor) {
        StringBuilder builder = new StringBuilder();
        appendDiagnostic(CBORObject.DecodeFromBytes(cbor), builder, 0);
        return builder.toString();
    }

    /**
     * JSON for the item. Lossy on purpose and worth knowing about: CBOR byte
     * strings have no JSON equivalent, so the library renders them base64url,
     * and CBOR map keys that are not text strings get stringified. Use
     * {@link #tree} when fidelity matters.
     */
    public static String toJson(byte[] cbor) {
        return CBORObject.DecodeFromBytes(cbor).ToJSONString();
    }

    /** JSON in, CBOR bytes out — the direction that builds test material. */
    public static byte[] fromJson(String json) {
        return CBORObject.FromJSONString(json).EncodeToBytes();
    }

    /** Wraps bytes as a tag-24 encoded-CBOR byte string, which is how mdoc
     *  carries a structure it intends to digest byte-exactly. */
    public static byte[] wrapAsEncodedCbor(byte[] cbor) {
        return CBORObject.FromObjectAndTag(CBORObject.FromObject(cbor), TAG_ENCODED_CBOR).EncodeToBytes();
    }

    /** Unwraps a tag-24 byte string back to the item inside it. */
    public static byte[] unwrapEncodedCbor(byte[] tagged) {
        CBORObject object = CBORObject.DecodeFromBytes(tagged);
        if (!object.HasMostOuterTag(TAG_ENCODED_CBOR)) {
            throw new IllegalArgumentException("Not a tag 24 (encoded CBOR data item)");
        }
        return object.Untag().GetByteString();
    }

    /** A one-line summary: what the top-level item is and how big it is. */
    public static String summary(byte[] cbor) {
        CBORObject object = CBORObject.DecodeFromBytes(cbor);
        StringBuilder builder = new StringBuilder();
        builder.append(describeType(object));
        if (object.isTagged()) {
            builder.append(", tagged ").append(tagList(object));
        }
        builder.append(", ").append(cbor.length).append(" bytes");
        return builder.toString();
    }

    // ----------------------------------------------------------------- tree

    private static void append(CBORObject object, StringBuilder builder, int depth, String label) {
        String indent = "  ".repeat(Math.min(depth, MAX_DEPTH));
        builder.append(indent);
        if (label != null) {
            builder.append(label).append(": ");
        }
        if (depth >= MAX_DEPTH) {
            builder.append("... (nesting deeper than ").append(MAX_DEPTH).append(" levels)\n");
            return;
        }

        if (object.isTagged()) {
            builder.append(tagList(object)).append(' ');
            if (object.HasMostOuterTag(TAG_ENCODED_CBOR)) {
                byte[] embedded = object.Untag().GetByteString();
                builder.append("encoded-CBOR (").append(embedded.length).append(" bytes) >>\n");
                append(CBORObject.DecodeFromBytes(embedded), builder, depth + 1, null);
                return;
            }
        }

        CBORObject value = object.isTagged() ? object.Untag() : object;
        CBORType type = value.getType();
        switch (type) {
            case Map -> {
                builder.append("map (").append(value.size()).append(")\n");
                for (CBORObject key : value.getKeys()) {
                    append(value.get(key), builder, depth + 1, keyLabel(key));
                }
            }
            case Array -> {
                builder.append("array (").append(value.size()).append(")\n");
                int index = 0;
                for (CBORObject item : value.getValues()) {
                    append(item, builder, depth + 1, "[" + index++ + "]");
                }
            }
            case ByteString -> {
                byte[] bytes = value.GetByteString();
                builder.append("bytes (").append(bytes.length).append(") ")
                        .append(hexPreview(bytes)).append('\n');
            }
            case TextString -> builder.append("text \"").append(value.AsString()).append("\"\n");
            case Boolean -> builder.append(value.AsBoolean()).append('\n');
            case SimpleValue -> builder.append(value.isNull() ? "null" : "simple(" + value.getSimpleValue() + ")")
                    .append('\n');
            default -> builder.append(describeType(value)).append(' ').append(value).append('\n');
        }
    }

    private static String keyLabel(CBORObject key) {
        return switch (key.getType()) {
            case TextString -> key.AsString();
            case ByteString -> "h'" + hex(key.GetByteString()) + "'";
            default -> key.toString();
        };
    }

    // ----------------------------------------------------------- diagnostic

    private static void appendDiagnostic(CBORObject object, StringBuilder builder, int depth) {
        if (depth >= MAX_DEPTH) {
            builder.append("...");
            return;
        }
        if (object.isTagged()) {
            for (EInteger tag : object.GetAllTags()) {
                builder.append(tag).append('(');
            }
            CBORObject inner = object.Untag();
            if (object.HasMostOuterTag(TAG_ENCODED_CBOR) && inner.getType() == CBORType.ByteString) {
                builder.append("<<");
                appendDiagnostic(CBORObject.DecodeFromBytes(inner.GetByteString()), builder, depth + 1);
                builder.append(">>");
            } else {
                appendDiagnostic(inner, builder, depth + 1);
            }
            builder.append(")".repeat(object.getTagCount()));
            return;
        }

        switch (object.getType()) {
            case Map -> {
                builder.append('{');
                boolean first = true;
                for (CBORObject key : object.getKeys()) {
                    if (!first) {
                        builder.append(", ");
                    }
                    first = false;
                    appendDiagnostic(key, builder, depth + 1);
                    builder.append(": ");
                    appendDiagnostic(object.get(key), builder, depth + 1);
                }
                builder.append('}');
            }
            case Array -> {
                builder.append('[');
                boolean first = true;
                for (CBORObject item : object.getValues()) {
                    if (!first) {
                        builder.append(", ");
                    }
                    first = false;
                    appendDiagnostic(item, builder, depth + 1);
                }
                builder.append(']');
            }
            case ByteString -> builder.append("h'").append(hex(object.GetByteString())).append('\'');
            case TextString -> builder.append('"').append(object.AsString()).append('"');
            case Boolean -> builder.append(object.AsBoolean());
            case SimpleValue -> builder.append(object.isNull() ? "null" : "simple(" + object.getSimpleValue() + ")");
            default -> builder.append(object);
        }
    }

    // ---------------------------------------------------------------- utils

    private static String describeType(CBORObject object) {
        CBORObject value = object.isTagged() ? object.Untag() : object;
        return switch (value.getType()) {
            case Map -> "map (" + value.size() + " entries)";
            case Array -> "array (" + value.size() + " items)";
            case ByteString -> "byte string (" + value.GetByteString().length + " bytes)";
            case TextString -> "text string";
            case Boolean -> "boolean";
            case Integer -> "integer";
            case FloatingPoint -> "floating point";
            case SimpleValue -> value.isNull() ? "null" : "simple value";
            default -> "number";
        };
    }

    private static String tagList(CBORObject object) {
        List<String> tags = new ArrayList<>();
        for (EInteger tag : object.GetAllTags()) {
            tags.add(tag.toString() + "(...)");
        }
        return String.join(" ", tags);
    }

    /** Long byte strings are truncated: a tree is for reading, and a 4 KiB
     *  certificate rendered in full turns one into a wall. */
    private static String hexPreview(byte[] bytes) {
        if (bytes.length <= 32) {
            return "h'" + hex(bytes) + "'";
        }
        byte[] head = new byte[32];
        System.arraycopy(bytes, 0, head, 0, 32);
        return "h'" + hex(head) + "...' (+" + (bytes.length - 32) + " bytes)";
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(Character.forDigit((b >> 4) & 0xF, 16));
            builder.append(Character.forDigit(b & 0xF, 16));
        }
        return builder.toString();
    }

    /** Parses the loose hexadecimal a user pastes: spaces, newlines and an
     *  optional {@code 0x} prefix are all tolerated, the way the app's other
     *  hex inputs behave. */
    public static byte[] parseHex(String input) {
        String cleaned = input.replaceAll("(?i)0x", "").replaceAll("[^0-9A-Fa-f]", "");
        if (cleaned.length() % 2 != 0) {
            throw new IllegalArgumentException("Hexadecimal input has an odd number of digits");
        }
        byte[] bytes = new byte[cleaned.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(cleaned.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    /** UTF-8 bytes of a diagnostic rendering, for callers that want to save it. */
    static byte[] diagnosticBytes(byte[] cbor) {
        return diagnostic(cbor).getBytes(StandardCharsets.UTF_8);
    }
}
