package com.github.dcysteine.nesql.exporter.source;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Ordered native facts before graphics are attached. A fresh data capture must match. */
public final class Semantic {
  private final MessageDigest digest = CanonicalJson.sha256();
  private long records;
  private JsonObject result;
  // Shapes use primitive coordinates: retaining a million JsonObjects while
  // waiting for the declaring structure would multiply the preview budget.
  private final Map<String, int[]> shapes = new LinkedHashMap<>();
  private final Map<String, String> blocks = new LinkedHashMap<>();
  private final Map<String, String> builds = new LinkedHashMap<>();

  public void add(String kind, JsonObject source) {
    if (result != null) throw new IllegalStateException("Semantic proof is sealed");
    if (kind.equals("shapes")) {
      JsonArray cells = source.getAsJsonArray("cells");
      if (cells == null || cells.size() < 1 || cells.size() > 2048)
        throw invalid("Invalid geometry shard");
      int[] coordinates = new int[cells.size() * 4];
      for (int i = 0; i < cells.size(); i++) {
        JsonObject cell = cells.get(i).getAsJsonObject();
        JsonArray at = cell.getAsJsonArray("at");
        if (at == null || at.size() != 3) throw invalid("Invalid geometry coordinates");
        for (int axis = 0; axis < 3; axis++) coordinates[i * 4 + axis] = at.get(axis).getAsInt();
        coordinates[i * 4 + 3] = cell.get("index").getAsInt();
      }
      String id = source.get("id").getAsString();
      int[] previous = shapes.put(id, coordinates);
      if (previous != null && !Arrays.equals(previous, coordinates))
        throw invalid("Conflicting geometry shard");
      return;
    }
    if (kind.equals("blocks")) {
      JsonObject block = copy(source);
      String id = block.remove("id").getAsString();
      String state = CanonicalJson.digest(block);
      String previous = blocks.put(id, state);
      if (previous != null && !previous.equals(state))
        throw invalid("Conflicting constructed block state");
      return;
    }
    if (kind.equals("builds")) {
      JsonObject row = copy(source);
      String id = row.remove("id").getAsString();
      JsonArray palette = row.remove("palette").getAsJsonArray();
      JsonArray chunks = row.remove("chunks").getAsJsonArray();
      row.remove("rendered");
      // Geometry binds each occupied cell to the full observed block/NBT,
      // not to an appearance index or a renderer-dependent build ID.
      MessageDigest proof = CanonicalJson.sha256();
      update(proof, row);
      long count = 0;
      for (JsonElement raw : chunks) {
        int[] cells = shape(raw.getAsString());
        for (int i = 0; i < cells.length; i += 4) {
          if (cells[i + 3] < 0 || cells[i + 3] >= palette.size())
            throw invalid("Invalid appearance index");
          String block = palette.get(cells[i + 3]).getAsJsonObject().get("block").getAsString();
          if (!blocks.containsKey(block)) throw invalid("Missing constructed block state");
          update(
              proof,
              object(
                  "at", array(cells[i], cells[i + 1], cells[i + 2]), "block", blocks.get(block)));
          count++;
        }
      }
      if (count != row.get("cells").getAsLong()) throw invalid("Incomplete constructed geometry");
      String normalized = CanonicalJson.hex(proof.digest());
      String previous = builds.put(id, normalized);
      if (previous != null && !previous.equals(normalized))
        throw invalid("Conflicting constructed build");
      return;
    }
    if (kind.equals("structures")) {
      JsonObject row = copy(source);
      for (JsonElement raw : row.getAsJsonArray("pieces")) {
        JsonObject piece = raw.getAsJsonObject();
        JsonArray chunks = piece.remove("chunks").getAsJsonArray();
        MessageDigest geometry = CanonicalJson.sha256();
        long count = 0;
        for (JsonElement chunk : chunks) {
          int[] cells = shape(chunk.getAsString());
          for (int i = 0; i < cells.length; i += 4) {
            update(
                geometry,
                object("at", array(cells[i], cells[i + 1], cells[i + 2]), "index", cells[i + 3]));
            count++;
          }
        }
        if (count != piece.get("cells").getAsLong())
          throw invalid("Incomplete structure definition geometry");
        piece.addProperty("geometry", CanonicalJson.hex(geometry.digest()));
      }
      for (JsonElement raw : row.getAsJsonArray("variants")) {
        JsonObject variant = raw.getAsJsonObject();
        if (!variant.get("build").isJsonNull()) {
          String build = builds.get(variant.get("build").getAsString());
          if (build == null) throw invalid("Missing owned structure build");
          variant.addProperty("build", build);
        }
      }
      update(digest, object("kind", kind, "row", row));
      records++;
      shapes.clear();
      blocks.clear();
      builds.clear();
      return;
    }
    if (!Arrays.asList(
            "items",
            "fluids",
            "aspects",
            "research",
            "categories",
            "recipes",
            "programs",
            "references")
        .contains(kind)) return;
    JsonObject row = copy(source);
    if (kind.equals("items") || kind.equals("fluids") || kind.equals("aspects"))
      row.add("icon", value(null));
    if (kind.equals("research")) {
      row.add("icon", value(null));
      row.add("texture", value(null));
    }
    update(digest, object("kind", kind, "row", row));
    records++;
  }

  public JsonObject finish() {
    if (result == null) {
      if (!shapes.isEmpty() || !blocks.isEmpty() || !builds.isEmpty())
        throw invalid("Unsealed owned structure geometry");
      result =
          object(
              "version",
              2,
              "sha256",
              CanonicalJson.hex(digest.digest()),
              "records",
              Long.toString(records));
    }
    return copy(result);
  }

  private int[] shape(String id) {
    int[] cells = shapes.get(id);
    if (cells == null) throw invalid("Missing owned geometry shard");
    return cells;
  }

  private static JsonObject copy(JsonObject row) {
    return new JsonParser().parse(row.toString()).getAsJsonObject();
  }

  private static void update(MessageDigest target, JsonObject row) {
    target.update(CanonicalJson.bytes(row));
    target.update((byte) '\n');
  }

  private static IllegalArgumentException invalid(String message) {
    return new IllegalArgumentException(message);
  }
}
