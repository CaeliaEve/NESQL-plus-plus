package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.*;
import com.google.gson.*;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Native published predictor weights, not a deterministic seed crafting recipe. */
final class Ic2CropBreedingRecipes extends ReaderSnapshot implements RegistryRecipes {
  private static final String HANDLER = "speiger.src.crops.prediction.NEIPlugin";
  private Ic2CropSource source;

  static boolean supports(ICraftingHandler handler) {
    return handler != null && supportsClass(handler.getClass().getName());
  }
  static boolean supportsClass(String name) { return HANDLER.equals(name); }

  Ic2CropBreedingRecipes(TemplateRecipeHandler handler) {
    if (!supports(handler)) throw new com.github.dcysteine.nesql.exporter.task.Jobs.Fault(
        "recipe_unsupported", "Unknown IC2 crop breeding handler");
    source = new Ic2CropSource();
  }
  public int size() { checkOpen(); return source.entries.size(); }
  public void verify() { checkOpen(); source.verify(); }
  public void finishVerification() { checkOpen(); source.finishVerification(); }
  public boolean capture(int index, RecipeRow row) {
    checkOpen(); captureEntry(source.entries.get(index), row); return true;
  }

  static String stackKey(ItemStack stack) {
    NativeStackAudit.audit(stack);
    String registry = Item.itemRegistry.getNameForObject(stack.getItem());
    if (registry == null || Item.itemRegistry.getObject(registry) != stack.getItem())
      throw new com.github.dcysteine.nesql.exporter.task.Jobs.Fault(
          "recipe_unsupported", "Unregistered crop display stack");
    return Identity.item(registry, Items.feather.getDamage(stack), TypedNbt.encode(stack.getTagCompound()));
  }
  private static void captureEntry(Entry entry, RecipeRow row) {
    for (int slot = 0; slot < entry.inputs.length; slot++) {
      ItemStack display = entry.inputs[slot].copy(); display.stackSize = 1;
      row.itemInput(slot, Collections.singletonList(
          new RecipeRow.Ingredient(display, 1, true, object("kind", "exact"))), false);
    }
    ItemStack output = entry.output.copy(); output.stackSize = 1;
    row.itemOutput(0, output, 10000);
    row.outputs.get(0).getAsJsonObject().add("chance", Chance.of(entry.points, entry.total));
    JsonArray usage=array();for(JsonObject parent:entry.usageListedFor)usage.add(parent);
    row.record.add("process", object("kind", "ic2CropPrediction", "parents", entry.parents,
        "result", entry.result, "points", entry.points, "total", entry.total,
        "craftingListed",entry.craftingListed,"usageListedFor",usage));
    row.note("ic2:prediction-assumption", HANDLER + "#drawExtras",
        row.facts.locale().equals("zh_CN")
            ? "原生预测器假设不可能达到的完美条件，并明确警告其结果不可当作可靠的实机成功率。这里保留原生 points/total 权重；分母包含已有父本候选，不重新归一化。"
            : "The native predictor assumes impossible perfect conditions and explicitly warns that its data is not trustworthy as an in-world success rate. These are the native points/total weights, including existing-parent candidates in the denominator, without renormalization.");
  }

  static final class Entry {
    final ItemStack[] inputs;
    final ItemStack output;
    final int points, total;
    final JsonArray parents;
    final JsonObject result, snapshot;
    final String order;
    boolean craftingListed;
    final List<JsonObject> usageListedFor=new ArrayList<>();
    Entry(ItemStack[] inputs, ItemStack output, int points, int total, JsonArray parents, JsonObject result,
        String[] keys, String outputKey) {
      // These are reader-owned cached display specimens; capture copies before changing counts.
      this.inputs = inputs.clone();
      JsonArray identities = array();
      for (int i=0;i<inputs.length;i++)
        identities.add(object("id", keys[i], "amount", inputs[i].stackSize));
      this.output = output; this.points = points; this.total = total;
      this.parents = parents; this.result = result;
      snapshot = object("parents", parents, "result", result, "inputs", identities,
          "output", outputKey, "outputAmount", output.stackSize, "points", points, "total", total);
      order = CanonicalJson.digest(snapshot);
    }
  }
  @Override protected void release() {
    if (source != null) source.close(); source = null;
  }
}
