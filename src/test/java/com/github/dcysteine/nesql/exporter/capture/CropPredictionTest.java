package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import java.lang.reflect.Array;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/** Actual published native objects; no predictor thread, GUI, world or injected reader source. */
final class CropPredictionTest {
  static void run() throws Exception {
    try (NativeRegistryScope scope = new NativeRegistryScope()) {
      Class<?> breederType = type("speiger.src.crops.prediction.Breeder"),
          apiType = type("speiger.src.crops.api.CropPluginAPI"),
          registryType = type("ic2.core.crop.IC2Crops"),
          cropType = type("ic2.api.crops.CropCard"),
          resultType = type("speiger.src.crops.prediction.BreedResult");
      Object breeder = field(breederType, null, "INSTANCE"),
          api = scope.allocate(apiType), registry = scope.allocate(registryType);
      Object wheat = scope.allocate(type("ic2.core.crop.CropWheat")),
          potato = scope.allocate(type("ic2.core.crop.CropPotato")),
          coffee = scope.allocate(type("ic2.core.crop.CropCoffee"));
      HashMap<String, Object> names = new HashMap<>();
      names.put("wheat", wheat); names.put("potato", potato); names.put("coffee", coffee);
      HashMap<String, Object> crops = new HashMap<>(); crops.put("IC2", names);
      HashMap<Object, ItemStack> displays = new HashMap<>();
      displays.put(wheat, new ItemStack(Items.wheat));
      displays.put(potato, new ItemStack(Items.potato));
      displays.put(coffee, new ItemStack(Items.wheat_seeds));
      scope.replace(type("ic2.api.crops.Crops"), null, "instance", registry);
      scope.replace(registryType, registry, "cropMap", crops);
      scope.replace(apiType, null, "instance", api);
      scope.replace(apiType, api, "displayItems", displays);
      Object parents = Array.newInstance(cropType, 2);
      Array.set(parents, 0, wheat); Array.set(parents, 1, potato);
      Object result = scope.allocate(resultType);
      scope.replace(resultType, result, "input", parents);
      scope.replace(resultType, result, "result", coffee);
      scope.replace(resultType, result, "points", 1);
      scope.replace(resultType, result, "total", 3);
      HashMap<Object, List<Object>> craft = groups(wheat, potato, coffee),
          usage = groups(wheat, potato, coffee);
      craft.get(coffee).add(result); usage.get(wheat).add(result); usage.get(potato).add(result);
      scope.replace(breederType, breeder, "craft", craft);
      scope.replace(breederType, breeder, "usage", usage);
      TemplateRecipeHandler handler = (TemplateRecipeHandler) scope.allocate(
          type("speiger.src.crops.prediction.NEIPlugin"));

      // Dropping retained parents, the exact denominator or published identity breaks this case.
      try (Ic2CropBreedingRecipes reader = new Ic2CropBreedingRecipes(handler)) {
        RecipeRow row = NativeCoreFixesTest.row(
            new ItemStack(Items.wheat), new ItemStack(Items.potato), new ItemStack(Items.wheat_seeds));
        JsonObject origin = object("owner", "IC2CropPlugin", "handler",
            "speiger.src.crops.prediction.NEIPlugin", "key", "crop-prediction-native");
        row.record.add("source", origin);
        row.record.addProperty("category", com.github.dcysteine.nesql.exporter.source.Identity.origin("category", origin));
        require(reader.size() == 1 && reader.capture(0, row), "Published prediction was lost");
        JsonObject process = row.record.getAsJsonObject("process");
        require(process.getAsJsonObject("result").equals(object("owner", "IC2", "name", "coffee")),
            "Registered crop identity was replaced by a display name");
        require(row.outputs.get(0).getAsJsonObject().getAsJsonObject("chance")
            .equals(object("numerator", "1", "denominator", "3")), "Predictor weight was renormalized");
        require(row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0)
            .getAsJsonObject().getAsJsonObject("consume").get("kind").getAsString().equals("keep"),
            "Parent plant became consumed seed stock");
        reader.finishVerification();
        NativeContractFixture.writeOwned("ic2-crop-prediction", row,
            Arrays.asList(new ItemStack(Items.wheat), new ItemStack(Items.potato), new ItemStack(Items.wheat_seeds)));
        scope.replace(resultType, result, "input", Array.newInstance(cropType, 2));
        Array.set(field(result, "input"), 0, wheat); Array.set(field(result, "input"), 1, potato);
        reader.verify(); // Cheap guard does not sort/rebuild all predictions.
        expect("environment_changed", reader::finishVerification);
        scope.replace(resultType, result, "input", parents);
      }
      // Equal stack and equal group replacements still invalidate the source seal.
      try (Ic2CropBreedingRecipes reader = new Ic2CropBreedingRecipes(handler)) {
        ItemStack old = displays.get(wheat); displays.put(wheat, old.copy());
        expect("environment_changed", reader::finishVerification); displays.put(wheat, old);
      }
      try (Ic2CropBreedingRecipes reader = new Ic2CropBreedingRecipes(handler)) {
        List<Object> old = usage.get(wheat); usage.put(wheat, new ArrayList<>(old));
        expect("environment_changed", reader::finishVerification); usage.put(wheat, old);
      }
      int[] calls = {0};
      HashMap<Object, List<Object>> foreign = new HashMap<Object, List<Object>>() {
        @Override public Set<Map.Entry<Object, List<Object>>> entrySet() {
          calls[0]++; throw new AssertionError("Unknown map callback ran");
        }
      };
      scope.replace(breederType, breeder, "craft", foreign);
      expect("recipe_unsupported", () -> new Ic2CropBreedingRecipes(handler));
      require(calls[0] == 0, "Collection admission executed an unknown callback");
      scope.replace(breederType, breeder, "craft", craft);
      craft.get(coffee).set(0, new Object());
      expect("recipe_unsupported", () -> new Ic2CropBreedingRecipes(handler));
      craft.get(coffee).set(0, result);
      scope.replace(breederType, breeder, "usage", null);
      require(Ic2CropSource.registrationAnchors()[1]==breeder,
          "Registration discovery tried to execute or await the predictor");
      expect("handler_not_ready", () -> new Ic2CropBreedingRecipes(handler));
      scope.replace(breederType, breeder, "usage", usage);
      // Native matches() deduplicates each table separately by result/points/parent count,
      // even when parent vectors or denominators differ. Preserve the union, not a false bijection.
      Object melon=scope.allocate(type("ic2.core.crop.CropMelon"));names.put("melon",melon);displays.put(melon,new ItemStack(Items.melon));
      craft.put(melon,new ArrayList<>());usage.put(melon,new ArrayList<>());
      Object alternate=scope.allocate(resultType),otherParents=Array.newInstance(cropType,2);
      Array.set(otherParents,0,wheat);Array.set(otherParents,1,melon);
      scope.replace(resultType,alternate,"input",otherParents);scope.replace(resultType,alternate,"result",coffee);
      scope.replace(resultType,alternate,"points",1);scope.replace(resultType,alternate,"total",7);
      for(Object nativeResult:new Object[]{result,alternate})scope.replace(resultType,nativeResult,"remover",new LinkedHashSet<>(Arrays.asList((Object[])field(nativeResult,"input"))));
      craft.values().forEach(List::clear);usage.values().forEach(List::clear);
      for(Object nativeResult:new Object[]{result,alternate})resultType.getMethod("addToMaps",Map.class,Map.class).invoke(nativeResult,craft,usage);
      require(craft.get(coffee).size()==1&&usage.get(melon).size()==1,"Fixture did not replay native coarse per-table deduplication");
      try(Ic2CropBreedingRecipes union=new Ic2CropBreedingRecipes(handler)){
        require(union.size()==2,"Native usage-only prediction was lost");
        for(int i=0;i<union.size();i++){
          RecipeRow row=NativeCoreFixesTest.row(new ItemStack(Items.wheat),new ItemStack(Items.potato),new ItemStack(Items.wheat_seeds),new ItemStack(Items.melon));
          union.capture(i,row);JsonObject process=row.record.getAsJsonObject("process");
          require(process.has("craftingListed")&&process.getAsJsonArray("usageListedFor").size()>0,"Native directional membership was not retained");
          JsonObject origin=object("owner","IC2CropPlugin","handler","speiger.src.crops.prediction.NEIPlugin","key","crop-prediction-union");
          row.record.add("source",origin);row.record.addProperty("category",com.github.dcysteine.nesql.exporter.source.Identity.origin("category",origin));
          NativeContractFixture.writeOwned("ic2-crop-union-"+i,row,Arrays.asList(new ItemStack(Items.wheat),new ItemStack(Items.potato),new ItemStack(Items.wheat_seeds),new ItemStack(Items.melon)));
        }
        union.finishVerification();
      }
      craft.values().forEach(List::clear); usage.values().forEach(List::clear);
      Ic2CropBreedingRecipes empty = new Ic2CropBreedingRecipes(handler);
      require(empty.size() == 0, "Published empty cache became unready");
      empty.finishVerification(); empty.close(); empty.close();
      expect("capture_closed", empty::size);
      require(!craft.isEmpty() && !displays.isEmpty() && !crops.isEmpty(), "Close cleared native sources");
    }
  }

  private static HashMap<Object, List<Object>> groups(Object... crops) {
    HashMap<Object, List<Object>> result = new HashMap<>();
    for (Object crop : crops) result.put(crop, new ArrayList<>());
    return result;
  }
  private static void expect(String code, Runnable action) {
    try { action.run(); throw new AssertionError("Expected " + code); }
    catch (Jobs.Fault error) { require(error.code.equals(code), "Unexpected failure: " + error); }
  }
  private static void require(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
}
