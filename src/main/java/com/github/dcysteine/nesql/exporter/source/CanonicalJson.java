package com.github.dcysteine.nesql.exporter.source;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Stable UTF-8 JSON for source identities and manifests. No locale or HTML escaping. */
public final class CanonicalJson {
    private CanonicalJson() {}

    public static final Comparator<String> KEY_ORDER = (left, right) -> {
        byte[] a = left.getBytes(StandardCharsets.UTF_8);
        byte[] b = right.getBytes(StandardCharsets.UTF_8);
        for (int index = 0; index < Math.min(a.length, b.length); index++) {
            int difference = (a[index] & 255) - (b[index] & 255);
            if (difference != 0) return difference;
        }
        return a.length - b.length;
    };

    public static byte[] bytes(JsonElement value) {
        StringBuilder output = new StringBuilder();
        append(value, output, 0);
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static String digest(JsonElement value) { return digest(bytes(value)); }

    public static String digest(byte[] value) {
        return hex(sha256().digest(value));
    }

    public static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException error) { throw new AssertionError(error); }
    }

    public static String hex(byte[] value) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] output = new char[value.length * 2];
        for (int index = 0; index < value.length; index++) {
            output[index * 2] = digits[(value[index] & 255) >>> 4];
            output[index * 2 + 1] = digits[value[index] & 15];
        }
        return new String(output);
    }

    private static void append(JsonElement value, StringBuilder output, int depth) {
        if (depth > 64) throw new IllegalArgumentException("JSON nesting exceeds 64 levels");
        if (value == null || value.isJsonNull()) { output.append("null"); return; }
        if (value.isJsonArray()) {
            output.append('[');
            boolean first = true;
            for (JsonElement element : value.getAsJsonArray()) {
                if (!first) output.append(',');
                first = false;
                append(element, output, depth + 1);
            }
            output.append(']');
        } else if (value.isJsonObject()) {
            output.append('{');
            List<Map.Entry<String, JsonElement>> entries = new ArrayList<>(value.getAsJsonObject().entrySet());
            entries.sort((left, right) -> KEY_ORDER.compare(left.getKey(), right.getKey()));
            boolean first = true;
            for (Map.Entry<String, JsonElement> entry : entries) {
                if (!first) output.append(',');
                first = false;
                string(entry.getKey(), output);
                output.append(':');
                append(entry.getValue(), output, depth + 1);
            }
            output.append('}');
        } else {
            JsonPrimitive primitive = value.getAsJsonPrimitive();
            if (primitive.isString()) string(primitive.getAsString(), output);
            else if (primitive.isBoolean()) output.append(primitive.getAsBoolean());
            else {
                BigDecimal number = primitive.getAsBigDecimal().stripTrailingZeros();
                if (number.precision() > 100 || Math.abs(number.scale()) > 100) {
                    throw new IllegalArgumentException("JSON number exceeds the canonical numeric range");
                }
                output.append(number.toPlainString());
            }
        }
    }

    private static void string(String value, StringBuilder output) {
        output.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"': output.append("\\\""); break;
                case '\\': output.append("\\\\"); break;
                case '\b': output.append("\\b"); break;
                case '\t': output.append("\\t"); break;
                case '\n': output.append("\\n"); break;
                case '\f': output.append("\\f"); break;
                case '\r': output.append("\\r"); break;
                default:
                    if (character < 32) {
                        output.append("\\u00");
                        output.append("0123456789abcdef".charAt(character >>> 4));
                        output.append("0123456789abcdef".charAt(character & 15));
                    } else if (Character.isHighSurrogate(character)) {
                        if (++index >= value.length() || !Character.isLowSurrogate(value.charAt(index))) {
                            throw new IllegalArgumentException("Unpaired Unicode surrogate");
                        }
                        output.append(character).append(value.charAt(index));
                    } else if (Character.isLowSurrogate(character)) {
                        throw new IllegalArgumentException("Unpaired Unicode surrogate");
                    } else output.append(character);
            }
        }
        output.append('"');
    }
}
