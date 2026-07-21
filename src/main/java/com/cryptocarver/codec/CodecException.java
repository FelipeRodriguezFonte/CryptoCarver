package com.cryptocarver.codec;

public class CodecException extends RuntimeException {

    private final ByteFormat format;
    private final Integer position;

    public CodecException(String message, ByteFormat format) {
        super(message);
        this.format = format;
        this.position = null;
    }

    public CodecException(String message, ByteFormat format, Integer position) {
        super(message);
        this.format = format;
        this.position = position;
    }

    public CodecException(String message, Throwable cause, ByteFormat format) {
        super(message, cause);
        this.format = format;
        this.position = null;
    }

    public ByteFormat getFormat() {
        return format;
    }

    public Integer getPosition() {
        return position;
    }
}
