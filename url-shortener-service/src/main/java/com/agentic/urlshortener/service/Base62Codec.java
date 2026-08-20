package com.agentic.urlshortener.service;

/**
 * Minimal, dependency-free base62 codec.
 *
 * <p>Alphabet is {@code 0-9A-Za-z} so codes are URL-safe and case-sensitive,
 * giving 62^6 (~56.8 billion) six-character codes.
 */
public final class Base62Codec {

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = 62;

    private Base62Codec() {
    }

    public static String encode(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("value must be non-negative, got " + value);
        }
        if (value == 0) {
            return String.valueOf(ALPHABET.charAt(0));
        }
        StringBuilder out = new StringBuilder();
        long remaining = value;
        while (remaining > 0) {
            out.append(ALPHABET.charAt((int) (remaining % BASE)));
            remaining /= BASE;
        }
        return out.reverse().toString();
    }

    public static long decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            throw new IllegalArgumentException("encoded value must not be empty");
        }
        long result = 0L;
        for (int i = 0; i < encoded.length(); i++) {
            int digit = ALPHABET.indexOf(encoded.charAt(i));
            if (digit < 0) {
                throw new IllegalArgumentException("Illegal base62 character '" + encoded.charAt(i) + "'");
            }
            result = result * BASE + digit;
        }
        return result;
    }

    /** Left-pads with the zero digit so every generated code has a stable width. */
    public static String encodePadded(long value, int width) {
        String encoded = encode(value);
        if (encoded.length() >= width) {
            return encoded;
        }
        StringBuilder out = new StringBuilder(width);
        for (int i = encoded.length(); i < width; i++) {
            out.append(ALPHABET.charAt(0));
        }
        return out.append(encoded).toString();
    }
}
