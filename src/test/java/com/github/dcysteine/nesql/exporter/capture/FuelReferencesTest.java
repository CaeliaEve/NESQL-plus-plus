package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.FurnaceRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cpw.mods.fml.common.registry.GameRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Native fuel table and furnace callbacks, with no client GUI or game session. */
final class FuelReferencesTest {
  static void run() throws Exception {
    Item special = new Item().setContainerItem(Items.bucket);
    Item.itemRegistry.addObject(12000, "nesql_fixture_exhausted_fuel", special);
    java.util.concurrent.atomic.AtomicInteger burnCalls=new java.util.concurrent.atomic.AtomicInteger();
    GameRegistry.registerFuelHandler(stack -> {burnCalls.incrementAndGet();return stack.getItem() == special ? 81 : 0;});
    JsonObject source =
        object(
            "owner", "NEI", "handler", "codechicken.nei.recipe.FuelRecipeHandler", "key", "fuel");
    nativeReadiness(source);
    ArrayList<FurnaceRecipeHandler.FuelPair> fuels =
        new ArrayList<>(
            Arrays.asList(
                new FurnaceRecipeHandler.FuelPair(new ItemStack(Items.coal, 1, 0), 1600),
                new FurnaceRecipeHandler.FuelPair(new ItemStack(Items.lava_bucket), 20000),
                new FurnaceRecipeHandler.FuelPair(new ItemStack(special), 81),
                new FurnaceRecipeHandler.FuelPair(new ItemStack(Items.coal, 1, 0), 1600)));
    FuelRecipes.Snapshot snapshot = FuelRecipes.snapshot(source, fuels);
    require(burnCalls.get()==0,"Reading a native fuel observation executed registered burn callbacks");
    unknownContainer(source);
    rawMetadata(source);
    FuelRecipes.Snapshot observed=FuelRecipes.snapshot(source,Arrays.asList(new FurnaceRecipeHandler.FuelPair(new ItemStack(Items.coal),37)));
    require(observed.records.get(1).getAsJsonObject().getAsJsonObject("data").get("burnTicks").getAsInt()==37,"Native cached observation was recomputed as a world burn guarantee");
    require(snapshot.records.size() == 5, "Native duplicate or fuel definition disappeared");
    JsonObject lava = snapshot.records.get(2).getAsJsonObject().getAsJsonObject("data");
    require(lava.get("burnTicks").getAsInt() == 20000, "Native lava burn ticks changed");
    JsonObject container =
        lava.getAsJsonArray("candidates").get(0).getAsJsonObject().getAsJsonObject("container");
    require(container.get("amount").getAsString().equals("1"), "Native bucket return changed");
    require(
        snapshot
            .records
            .get(3)
            .getAsJsonObject()
            .getAsJsonObject("data")
            .getAsJsonArray("candidates")
            .get(0)
            .getAsJsonObject()
            .getAsJsonObject("container")
            .get("amount")
            .getAsString()
            .equals("1"),
        "Audited native base container was lost");
    require(
        fuels.get(2).stack.items[0].stackSize == 1, "Native callback mutated the original fuel");
    FuelRecipes reader = new FuelRecipes(source, fuels, () -> true);
    require(
        reader.size() == 5 && reader.counts().get("fuels").getAsInt() == 4,
        "Fuel reference counts differ");
    reader.verify();
    reader.finishVerification();
    fuels.get(0).stack.items[0].setItemDamage(1);
    fails("environment_changed", reader::finishVerification);
    fuels.get(0).stack.items[0].setItemDamage(0);
    ItemStack prior=fuels.get(0).stack.items[0];
    try{fuels.get(0).stack.items[0]=prior.copy();fails("environment_changed",reader::finishVerification);}
    finally{fuels.get(0).stack.items[0]=prior;}
    reader.finishVerification();reader.close();reader.close();fails("capture_closed",reader::size);
    require(burnCalls.get()==0,"Fuel verification re-executed native burn callbacks");
    require(
        new FuelRecipes(source, new ArrayList<>(), () -> true).size() == 1,
        "Ready empty fuel table became unavailable");
    fails("handler_not_ready", () -> new FuelRecipes(source, fuels, () -> false));
    fails(
        "reference_invalid",
        () ->
            FuelRecipes.snapshot(
                source,
                Arrays.asList(new FurnaceRecipeHandler.FuelPair(new ItemStack(Items.coal), 0))));
    JsonArray items = array();
    for (Map.Entry<String, ItemStack> entry : snapshot.specimens.entrySet()) {
      ItemStack stack = entry.getValue();
      String registry = Item.itemRegistry.getNameForObject(stack.getItem());
      require(
          entry
              .getKey()
              .equals(
                  Identity.item(
                      registry, stack.getItemDamage(), TypedNbt.encode(stack.getTagCompound()))),
          "Fuel specimen ID differs");
      items.add(
          object(
              "id",
              entry.getKey(),
              "registry",
              registry,
              "meta",
              stack.getItemDamage(),
              "nbt",
              TypedNbt.encode(stack.getTagCompound())));
    }
    Files.write(
        Paths.get("fuel-reference-semantic-contract.json"),
        object("records", snapshot.records, "items", items)
            .toString()
            .getBytes(StandardCharsets.UTF_8));
  }

  private static void nativeReadiness(JsonObject source) throws Exception {
    boolean previousReady=codechicken.nei.ItemList.loadFinished;
    java.util.List<ItemStack> previousPanel=codechicken.nei.ItemList.items;
    ArrayList<FurnaceRecipeHandler.FuelPair> previousFuels=FurnaceRecipeHandler.afuels;
    java.lang.reflect.Field access=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");access.setAccessible(true);
    codechicken.nei.recipe.FuelRecipeHandler handler=(codechicken.nei.recipe.FuelRecipeHandler)((sun.misc.Unsafe)access.get(null)).allocateInstance(codechicken.nei.recipe.FuelRecipeHandler.class);
    try{
      codechicken.nei.ItemList.loadFinished=true;codechicken.nei.ItemList.items=new ArrayList<>();FurnaceRecipeHandler.afuels=null;
      fails("handler_not_ready",()->new FuelRecipes(handler,source));
      require(FurnaceRecipeHandler.afuels==null,"Fuel reader initialized borrowed native table");
      ArrayList<FurnaceRecipeHandler.FuelPair> empty=new ArrayList<>();FurnaceRecipeHandler.afuels=empty;
      try(FuelRecipes reader=new FuelRecipes(handler,source)){
        require(reader.size()==1&&FurnaceRecipeHandler.afuels==empty,"Ready-empty fuel table was rebuilt");reader.finishVerification();
        RegistrationBindings binding=RegistrationBindings.read(handler);
        require(binding.same(RegistrationBindings.read((codechicken.nei.recipe.FuelRecipeHandler)((sun.misc.Unsafe)access.get(null)).allocateInstance(codechicken.nei.recipe.FuelRecipeHandler.class))),"Independent native fuel registrations have different sources");
        try{FurnaceRecipeHandler.afuels=new ArrayList<>();fails("environment_changed",reader::verify);require(!binding.same(RegistrationBindings.read(handler)),"Replaced fuel cache stayed equivalent");}
        finally{FurnaceRecipeHandler.afuels=empty;}
        java.util.List<ItemStack> panel=codechicken.nei.ItemList.items;
        try{codechicken.nei.ItemList.items=new ArrayList<>();fails("environment_changed",reader::verify);}
        finally{codechicken.nei.ItemList.items=panel;}
        reader.close();reader.close();fails("capture_closed",reader::domain);
      }
    }finally{codechicken.nei.ItemList.loadFinished=previousReady;codechicken.nei.ItemList.items=previousPanel;FurnaceRecipeHandler.afuels=previousFuels;}
  }

  private static void unknownContainer(JsonObject source){
    java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
    Item unknown=new Item(){@Override public ItemStack getContainerItem(ItemStack stack){calls.incrementAndGet();return new ItemStack(Items.bucket,2);}};
    Item predicate=new Item(){@Override public boolean hasContainerItem(ItemStack stack){calls.incrementAndGet();return true;}};
    Item legacy=new Item(){@Override public boolean hasContainerItem(){calls.incrementAndGet();return true;}};
    int index=0;for(Item item:Arrays.asList(unknown,predicate,legacy)){
      Item.itemRegistry.addObject(12001+index,"nesql_fixture_unverified_fuel_"+index,item);index++;
      fails("recipe_unsupported",()->FuelRecipes.snapshot(source,Arrays.asList(new FurnaceRecipeHandler.FuelPair(new ItemStack(item),81))));
    }
    require(calls.get()==0,"Unknown fuel container callback executed before audit");
  }

  private static void rawMetadata(JsonObject source){
    java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
    Item item=new Item(){@Override public int getDamage(ItemStack stack){calls.incrementAndGet();return 19;}};
    Item.itemRegistry.addObject(12005,"nesql_fixture_raw_fuel",item);
    FurnaceRecipeHandler.FuelPair pair=new FurnaceRecipeHandler.FuelPair(new ItemStack(item,1,7),81);calls.set(0);
    FuelRecipes.Snapshot captured=FuelRecipes.snapshot(source,Arrays.asList(pair));
    String id=Identity.item(Item.itemRegistry.getNameForObject(item),7,com.google.gson.JsonNull.INSTANCE);
    require(captured.specimens.containsKey(id)&&calls.get()==0,"Fuel identity used an unaudited metadata callback instead of raw metadata");
  }

  private static void fails(String code, Runnable action) {
    try {
      action.run();
      throw new AssertionError("Invalid native fuel state accepted");
    } catch (Jobs.Fault fault) {
      require(code.equals(fault.code), "Wrong native fuel failure: " + fault);
    }
  }

  private static void require(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
}
