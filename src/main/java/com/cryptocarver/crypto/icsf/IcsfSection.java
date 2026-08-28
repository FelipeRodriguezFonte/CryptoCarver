package com.cryptocarver.crypto.icsf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A logical group of decoded fields and flags: header, associated data, key body...
 *
 * <p>Titles, field names, readings and flag explanations are all {@link IcsfText}:
 * the analyser records what it means, and the words are chosen when the report is
 * rendered. There is deliberately no overload taking a plain {@code String}, so
 * that a new field cannot be added in one language by accident.</p>
 */
public final class IcsfSection {

    private final IcsfText title;
    private final List<Field> fields = new ArrayList<>();
    private final List<Flag> flags = new ArrayList<>();

    public IcsfSection(IcsfText title) {
        this.title = title;
    }

    /** One decoded field of the token. {@code rawHex} is data and stays verbatim. */
    public record Field(int offset, int length, IcsfText name, String rawHex, IcsfText value) {
        /** A derived observation that has no byte range of its own. */
        public static Field derived(int anchorOffset, IcsfText name, IcsfText value) {
            return new Field(anchorOffset, 0, name, "", value);
        }
    }

    /** One decoded bit, with what it means when it is on. */
    public record Flag(IcsfText name, boolean on, IcsfText detail) {
        public Flag(IcsfText name, boolean on) {
            this(name, on, IcsfText.EMPTY);
        }
    }

    public IcsfText title() {
        return title;
    }

    public List<Field> fields() {
        return Collections.unmodifiableList(fields);
    }

    public List<Flag> flags() {
        return Collections.unmodifiableList(flags);
    }

    public IcsfSection add(Field field) {
        fields.add(field);
        return this;
    }

    public IcsfSection add(int offset, int length, IcsfText name, String rawHex, IcsfText value) {
        return add(new Field(offset, length, name, rawHex, value));
    }

    public IcsfSection add(int offset, int length, IcsfText name, String rawHex) {
        return add(new Field(offset, length, name, rawHex, IcsfText.EMPTY));
    }

    public IcsfSection add(Flag flag) {
        flags.add(flag);
        return this;
    }

    public IcsfSection add(IcsfText flagName, boolean on, IcsfText detail) {
        return add(new Flag(flagName, on, detail));
    }

    public IcsfSection add(IcsfText flagName, boolean on) {
        return add(new Flag(flagName, on, IcsfText.EMPTY));
    }

    public IcsfSection addAll(List<Flag> newFlags) {
        flags.addAll(newFlags);
        return this;
    }

    /** True if any flag whose key starts with {@code keyPrefix} is on. */
    public boolean hasFlagOn(String keyPrefix) {
        for (Flag flag : flags) {
            if (flag.on() && flag.name().key() != null && flag.name().key().startsWith(keyPrefix)) {
                return true;
            }
        }
        return false;
    }
}
