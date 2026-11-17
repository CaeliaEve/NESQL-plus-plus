package com.github.dcysteine.nesql.exporter.source;

import com.google.gson.JsonObject;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Job-owned canonical bytes; this type never crosses the source package boundary. */
final class CanonicalRecord {
    final String id;
    final byte[] bytes;

    CanonicalRecord(JsonObject row) throws IOException {
        if (!row.has("id") || !row.get("id").isJsonPrimitive()) throw new IOException("Missing record id");
        id = row.get("id").getAsString();
        validateId(id);
        bytes = CanonicalJson.bytes(row);
        if (bytes.length > Dataset.RECORD_LIMIT) throw new IOException("Record exceeds 1 MiB: " + id);
    }

    private CanonicalRecord(String id, byte[] bytes) {
        this.id = id;
        this.bytes = bytes;
    }

    private static void validateId(String id) throws IOException {
        if (!id.matches("[a-zA-Z0-9_:.#/@-]{1,256}")) throw new IOException("Invalid record id: " + id);
    }

    void spill(DataOutputStream output) throws IOException {
        byte[] key = id.getBytes(StandardCharsets.US_ASCII);
        output.writeShort(key.length);
        output.write(key);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    static CanonicalRecord read(DataInputStream input) throws IOException {
        int first = input.read();
        if (first == -1) return null;
        int second = input.read();
        if (second == -1) throw new EOFException("Truncated sort key length");
        int size = (first << 8) | second;
        if (size == 0 || size > 256) throw new IOException("Invalid sort key length");
        byte[] key = new byte[size];
        input.readFully(key);
        String id = new String(key, StandardCharsets.US_ASCII);
        validateId(id);
        int length = input.readInt();
        if (length <= 0 || length > Dataset.RECORD_LIMIT) throw new IOException("Invalid sort record length");
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return new CanonicalRecord(id, bytes);
    }
}
