package com.github.dcysteine.nesql.exporter.source;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Actual owned Java geometry, with only the graphics attachment varied. */
public final class SemanticStructureTest {
  public static void run(JsonArray nativeRows) {
    JsonObject original = proof(nativeRows);
    JsonArray changed = copy(nativeRows);
    row(changed, "blocks").addProperty("meta", 4);
    require(!original.equals(proof(changed)), "Recovery ignored actual constructed block state");
    changed = copy(nativeRows);
    row(changed, "builds").addProperty("facing", "south");
    require(!original.equals(proof(changed)), "Recovery ignored native construction facing");
    changed = copy(nativeRows);
    row(changed, "structures").getAsJsonArray("description").add(value("native_extra_condition"));
    require(!original.equals(proof(changed)), "Recovery ignored native structure information");

    JsonArray rendered = copy(nativeRows);
    JsonObject build = row(rendered, "builds");
    build.addProperty("rendered", true);
    for (com.google.gson.JsonElement raw : build.getAsJsonArray("palette"))
      raw.getAsJsonObject().addProperty("model", "model_owned_native_visual");
    build.addProperty("id", "build_with_graphics");
    row(rendered, "structures")
        .getAsJsonArray("variants")
        .get(0)
        .getAsJsonObject()
        .addProperty("build", "build_with_graphics");
    require(original.equals(proof(rendered)), "Graphics attachment changed native geometry proof");

    JsonArray split = copy(rendered);
    JsonObject shape = row(split, "shapes");
    JsonArray cells = shape.getAsJsonArray("cells");
    JsonObject second = object("id", "shape_second_transport_chunk", "cells", array(cells.get(1)));
    shape.add("cells", array(cells.get(0)));
    JsonArray withSecond = array();
    for (com.google.gson.JsonElement entry : split) {
      withSecond.add(entry);
      if (entry.getAsJsonObject().get("kind").getAsString().equals("shapes"))
        withSecond.add(object("kind", "shapes", "value", second));
    }
    row(withSecond, "builds").getAsJsonArray("chunks").add(value(second.get("id").getAsString()));
    require(
        original.equals(proof(withSecond)),
        "Transport chunk boundary changed native geometry proof");

    JsonArray missing = array();
    for (com.google.gson.JsonElement raw : nativeRows)
      if (!raw.getAsJsonObject().get("kind").getAsString().equals("shapes")) missing.add(raw);
    try {
      proof(missing);
      throw new AssertionError("Recovery accepted missing owned geometry");
    } catch (IllegalArgumentException expected) {
    }
    System.out.println(
        "Structure recovery proof: actual block/geometry/facing drift, graphics normalization and missing chunks passed");
  }

  private static JsonObject proof(JsonArray rows) {
    Semantic semantic = new Semantic();
    for (com.google.gson.JsonElement raw : rows) {
      JsonObject entry = raw.getAsJsonObject();
      semantic.add(entry.get("kind").getAsString(), entry.getAsJsonObject("value"));
    }
    return semantic.finish();
  }

  private static JsonObject row(JsonArray rows, String kind) {
    for (com.google.gson.JsonElement raw : rows) {
      JsonObject entry = raw.getAsJsonObject();
      if (entry.get("kind").getAsString().equals(kind)) return entry.getAsJsonObject("value");
    }
    throw new AssertionError("Missing native fixture table: " + kind);
  }

  private static JsonArray copy(JsonArray rows) {
    return new JsonParser().parse(rows.toString()).getAsJsonArray();
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
