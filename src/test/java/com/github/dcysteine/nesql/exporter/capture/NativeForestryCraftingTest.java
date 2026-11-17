package com.github.dcysteine.nesql.exporter.capture;

import com.google.gson.JsonObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Collections;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.oredict.ShapedOreRecipe;

/** Real Forestry matcher: shadow inputs, conditional NBT and source drift. */
final class NativeForestryCraftingTest {
  static void run() throws Exception {
    NativeCoreFixesTest.version("Forestry", "4.10.17");
    Class<?> type = MagicApi.type("forestry.core.recipes.ShapedRecipeCustom");
    Constructor<?> constructor = type.getConstructor(ItemStack.class, Object[].class);
    ItemStack tagged = new ItemStack(Items.paper);
    tagged.setTagInfo("owner", new net.minecraft.nbt.NBTTagString("forestry"));
    ItemStack wildcard = new ItemStack(Items.gold_ingot, 1, 32767);
    ItemStack result = new ItemStack(Items.diamond, 2);
    IRecipe recipe = (IRecipe)constructor.newInstance(result,
        new Object[]{false, "ab", 'a', tagged, 'b', wildcard});
    ItemStack wrong = tagged.copy(); wrong.getTagCompound().setString("owner", "other");
    require(recipe.matches(NativeCraftingTest.inventory(3, 3, tagged, new ItemStack(Items.gold_ingot)), null), "Native tagged match failed");
    require(!recipe.matches(NativeCraftingTest.inventory(3, 3, wrong, new ItemStack(Items.gold_ingot)), null), "Native tagged predicate changed");
    require(!recipe.matches(NativeCraftingTest.inventory(3, 3, new ItemStack(Items.gold_ingot), tagged), null), "Native disabled mirror changed");
    // The superclass array is NOT the array used by Forestry's matches callback.
    ((ShapedOreRecipe)recipe).getInput()[0] = new ItemStack(Items.apple);
    try (RegistryRecipes reader = NativeCraftingTest.reader(Collections.singletonList(recipe), true)) {
      RecipeRow row = NativeCraftingTest.row(true, tagged, new ItemStack(Items.gold_ingot), result);
      require(reader.capture(0, row), "Forestry recipe disappeared");
      JsonObject choice = row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
      require(!choice.getAsJsonObject("rule").get("nbt").getAsBoolean(), "Forestry required NBT was flattened");
      require(row.inputs.get(1).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject()
          .getAsJsonObject("rule").get("nbt").getAsBoolean(), "Untagged Forestry template started requiring NBT");
      require(!row.record.getAsJsonObject("grid").get("mirror").getAsBoolean(), "Forestry mirroring was lost");
      reader.finishVerification();
      Field input = type.getDeclaredField("input"); input.setAccessible(true);
      Object[] cells = (Object[])input.get(recipe);
      ((ItemStack)cells[0]).getTagCompound().setString("owner", "changed");
      try { reader.finishVerification(); throw new AssertionError("Forestry consumed input drift was ignored"); }
      catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) {
        require(expected.code.equals("recipe_changed"), expected.toString());
      }
    }
    System.out.println("Forestry crafting: native shadow inputs, tagged matching, wildcard, mirror and drift passed");
  }
  private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
