package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.GeneticsReferences;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.item.ItemStack;

/**
 * A reference cursor emits genuine Database records, independently of consuming machine RecipeRows.
 */
final class NativeGeneticsReferences implements NativeReferences {
  private final BinnieGeneticsRecipes nativeSource;
  private final List<JsonObject> rows;
  private final Map<String, ItemStack> specimens;

  NativeGeneticsReferences(TemplateRecipeHandler handler) {
    if (!BinnieGeneticsRecipes.supportsReference(handler))
      throw new Jobs.Fault("reference_unsupported", "Unknown native genetic reference handler");
    nativeSource = new BinnieGeneticsRecipes(handler);
    rows = new ArrayList<>(GeneticsReferences.rows(nativeSource.reference(null)));
    specimens = nativeSource.referenceSpecimens();
    verify();
  }

  public int size() {
    nativeSource.size();
    return rows.size();
  }

  public String domain() {
    nativeSource.size();
    return rows.get(0).get("domain").getAsString();
  }

  public void verify() {
    nativeSource.verify();
    if (!rows.get(0)
        .getAsJsonObject("data")
        .getAsJsonObject("explanation")
        .get("text")
        .getAsString()
        .equals(GeneticsReferences.description()))
      throw new Jobs.Fault("environment_changed", "Native Database localized explanation changed");
  }

  public JsonObject counts() {
    nativeSource.size();
    return copy(rows.get(0).getAsJsonObject("data").getAsJsonObject("counts"));
  }

  @Override
  public void finishVerification() {
    verify();
    nativeSource.finishVerification();
  }

  public void capture(int index, Facts facts) {
    nativeSource.size();
    Jobs.checkpoint();
    JsonObject row = copy(rows.get(index)), data = row.getAsJsonObject("data");
    if (data.get("kind").getAsString().equals("gene")) {
      JsonObject gene = data.getAsJsonObject("gene");
      for (String form : new String[] {"serum", "genome", "sequence"})
        fact(facts, gene.get(form).getAsString());
    }
    if (data.get("kind").getAsString().equals("definition"))
      for (JsonElement value : data.getAsJsonArray("containers"))
        fact(facts, value.getAsJsonObject().get("empty").getAsString());
    facts.row("references", row);
  }

  private void fact(Facts facts, String id) {
    ItemStack specimen = specimens.get(id);
    if (specimen == null || !facts.item(specimen.copy()).equals(id))
      throw new Jobs.Fault("environment_changed", "Native Database gene specimen identity changed");
  }

  private static JsonObject copy(JsonObject value) {
    return new JsonParser().parse(value.toString()).getAsJsonObject();
  }

  @Override
  public void close() {
    nativeSource.close();
    specimens.clear();
    rows.clear();
  }
}
