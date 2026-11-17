package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import codechicken.nei.recipe.FurnaceRecipeHandler;
import java.lang.reflect.Constructor;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraftforge.oredict.OreDictionary;

/** A large native baked metadata domain compresses by actual selection, keeping its exception. */
final class NativeReturnedSmeltingTest {
  @SuppressWarnings("unchecked") static void run() throws Exception {
    try (NativeRegistryScope scope = new NativeRegistryScope()) {
      java.io.File profile = new java.io.File("returned-smelting-profile").getAbsoluteFile();profile.mkdirs();
      cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(cpw.mods.fml.relauncher.FMLInjectionData.class,null,profile,"minecraftHome");
      net.minecraft.launchwrapper.Launch.minecraftHome=profile;
      NativeCoreFixesTest.version("etfuturum", "2.6.2.25-GTNH");
      Class<?> owner = type("ganymedes01.etfuturum.recipes.BlastFurnaceRecipes");
      Object manager = owner.newInstance();
      scope.replace(owner, null, "smeltingBase", manager);
      for (String name : Arrays.asList("smeltingList", "smeltingListCache", "experienceList", "experienceListCache", "smeltingBlacklist"))
        ((Map<?,?>)field(manager, name)).clear();
      ((Map<ItemStack,ItemStack>)field(manager,"smeltingBlacklist"))
          .put(new ItemStack(Items.paper,1,3000), new ItemStack(Items.paper,1,3000));
      scope.replace(type("ganymedes01.etfuturum.configuration.configs.ConfigFunctions"), null, "enableAutoAddBlastFurnace", true);
      Constructor<FurnaceRecipes> factory = FurnaceRecipes.class.getDeclaredConstructor(); factory.setAccessible(true);
      FurnaceRecipes furnace = factory.newInstance();
      Map<ItemStack,ItemStack> entries = new LinkedHashMap<>();
      entries.put(new ItemStack(Items.paper,1,32767),new ItemStack(Items.iron_ingot));
      scope.replace(FurnaceRecipes.class, furnace, "smeltingList", entries);
      scope.replace(FurnaceRecipes.class, furnace, "experienceList", new HashMap<ItemStack,Float>());
      scope.replace(FurnaceRecipes.class, null, "smeltingBase", furnace);
      for (int meta = 0; meta < 6000; meta++) OreDictionary.registerOre("oreReturnedScale",new ItemStack(Items.paper,1,meta));
      scope.replace(codechicken.nei.ItemList.class,null,"items",new ArrayList<>(Arrays.asList(new ItemStack(Items.paper))));
      scope.replace(codechicken.nei.ItemList.class,null,"loadFinished",true);
      FurnaceRecipeHandler handler = (FurnaceRecipeHandler)scope.allocate(type("ganymedes01.etfuturum.compat.nei.BlastFurnaceRecipeHandler"));
      try (SmeltingRecipes reader = new SmeltingRecipes(handler)) {
        if(reader.size()!=3)throw new AssertionError("Expected one accepted metadata set, one blacklist exception and one unmatched remainder");
        int passed=0,excluded=0;
        for(int index=0;index<reader.size();index++){
          RecipeRow row=NativeCoreFixesTest.row(new ItemStack(Items.paper),new ItemStack(Items.iron_ingot));
          if(reader.capture(index,row)){
            passed++;
            if(!row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject()
                .getAsJsonObject("rule").get("kind").getAsString().equals("metadataSet"))
              throw new AssertionError("Compressed selection lost its native metadata set");
          }else excluded++;
        }
        if(passed!=1||excluded!=2)throw new AssertionError("Smelting selection/exclusion differs");
        reader.finishVerification();
      }
    }
    System.out.println("Et Futurum: 6000 baked metadata variants compressed, native blacklist exclusion retained");
  }
}
