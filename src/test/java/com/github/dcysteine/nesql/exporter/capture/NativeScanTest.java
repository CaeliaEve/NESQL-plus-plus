package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.ItemList;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.common.collect.ArrayListMultimap;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;

/** Reader-snapshot checks below target admission; not a claim of target-family approval. */
public final class NativeScanTest {
  public static void main(String[] arguments) throws Exception {
    GameTest.run();
  }

  static void run() throws Exception {
    solar();
    ae();
    System.out.println("Native scan guards: Solar and AE local checks, full sealing and root drift passed");
  }

  private static void solar() throws Exception {
    Class<?> manager = type("advsolar.utils.MTRecipeManager");
    Object first = solarRecipe(new ItemStack(Items.apple), 1);
    Object other = solarRecipe(new ItemStack(Items.carrot), 2);
    List<Object> records = new ArrayList<>(Arrays.asList(first, other));
    try (NativeRegistryScope scope = new NativeRegistryScope()) {
      scope.replace(manager, null, "transformerRecipes", records);
      try (RegistryRecipes reader = snapshot(SolarRecipes.class, new Class<?>[] {List.class}, records)) {
        type("advsolar.utils.MTRecipeRecord").getField("energyPerOperation").setInt(other, 3);
        try {
          reader.verify();
          require(reader.capture(0, row(Items.apple)), "Solar did not capture the unchanged entry");
          expect("environment_changed", reader::finishVerification);
        } finally {
          type("advsolar.utils.MTRecipeRecord").getField("energyPerOperation").setInt(other, 2);
        }
        ItemStack input = (ItemStack) field(first, "inputStack");
        input.stackSize++;
        try {
          expect("environment_changed", () -> reader.capture(0, row(Items.apple)));
        } finally {
          input.stackSize--;
        }
        manager.getField("transformerRecipes").set(null, new ArrayList<>(records));
        try {
          expect("environment_changed", reader::verify);
        } finally {
          manager.getField("transformerRecipes").set(null, records);
        }
        reader.finishVerification();
      }
    }
    System.out.println("Solar: unrelated entries are checked at sealing; touched entries and roots reject immediately");
  }

  private static void ae() throws Exception {
    TemplateRecipeHandler handler =
        (TemplateRecipeHandler)
            type("appeng.integration.modules.NEIHelpers.NEIAEShapedRecipeHandler").newInstance();
    CraftingManager manager = CraftingManager.getInstance();
    List<IRecipe> recipes =
        new ArrayList<>(Arrays.asList(aeRecipe(Items.apple), aeRecipe(Items.carrot)));
    ArrayListMultimap<Item, ItemStack> variants = ArrayListMultimap.create();
    ItemStack apple = new ItemStack(Items.apple), carrot = new ItemStack(Items.carrot);
    variants.put(Items.apple, apple);
    variants.put(Items.carrot, carrot);
    try (NativeRegistryScope scope = new NativeRegistryScope()) {
      scope.replace(CraftingManager.class, manager, "recipes", recipes);
      scope.replace(ItemList.class, null, "loadFinished", true);
      scope.replace(ItemList.class, null, "itemMap", variants);
      try (RegistryRecipes reader =
          snapshot(
              AeRecipes.class,
              new Class<?>[] {TemplateRecipeHandler.class, Object[].class},
              handler,
              new Object[] {handler.getClass(), manager, recipes})) {
        require(reader.capture(0, row(Items.apple)), "AE apple wildcard was not captured");
        require(reader.capture(1, row(Items.carrot)), "AE carrot wildcard was not captured");
        carrot.stackSize++;
        try {
          reader.verify();
          require(reader.capture(0, row(Items.apple)), "AE rechecked an unrelated wildcard set");
          expect("recipe_changed", () -> reader.capture(1, row(Items.carrot)));
          expect("recipe_changed", reader::finishVerification);
        } finally {
          carrot.stackSize--;
        }
        variants.put(Items.apple, new ItemStack(Items.apple, 1, 1));
        try {
          expect("recipe_changed", () -> reader.capture(0, row(Items.apple)));
        } finally {
          variants.get(Items.apple).remove(1);
        }
        ItemList.loadFinished = false;
        try {
          expect("recipe_changed", reader::verify);
        } finally {
          ItemList.loadFinished = true;
        }
        ItemList.itemMap = ArrayListMultimap.create();
        try {
          expect("recipe_changed", reader::verify);
        } finally {
          ItemList.itemMap = variants;
        }
        reader.finishVerification();
      }
    }
    System.out.println("AE: only touched wildcard sets are rescanned; final sealing and readiness/root guards remain");
  }

  private static Object solarRecipe(ItemStack input, int energy) throws Exception {
    Class<?> type = type("advsolar.utils.MTRecipeRecord");
    Object recipe = type.newInstance();
    type.getField("inputStack").set(recipe, input);
    type.getField("outputStack").set(recipe, new ItemStack(Items.paper));
    type.getField("energyPerOperation").setInt(recipe, energy);
    return recipe;
  }

  private static IRecipe aeRecipe(Item item) throws Exception {
    Class<?> resultType = type("appeng.api.recipes.ResolverResultSet");
    Object result =
        resultType
            .getConstructor(String.class, ItemStack[].class)
            .newInstance("scan", new ItemStack[] {new ItemStack(item, 1, 32767)});
    Object ingredient =
        type("appeng.recipes.IngredientSet")
            .getConstructor(resultType, int.class)
            .newInstance(result, 1);
    return (IRecipe)
        type("appeng.recipes.game.ShapedRecipe")
            .getConstructor(ItemStack.class, Object[].class)
            .newInstance(new ItemStack(Items.paper), new Object[] {new String[] {"A"}, 'A', ingredient});
  }

  @SuppressWarnings("unchecked")
  private static RecipeRow row(Item input) {
    Facts facts = new Facts("en_US");
    Set<String> known = (Set<String>) field(facts, "items");
    for (Item item : new Item[] {input, Items.paper})
      known.add(Identity.item(Item.itemRegistry.getNameForObject(item), 0, null));
    return new RecipeRow(facts, object("owner", "scan", "handler", "native", "key", "scan"), "scan", 0);
  }

  private static RegistryRecipes snapshot(Class<?> type, Class<?>[] parameters, Object... arguments)
      throws Exception {
    Constructor<?> constructor;
    try {
      constructor = type.getDeclaredConstructor(parameters);
    } catch (NoSuchMethodException missing) {
      throw new AssertionError("Missing native reader snapshot boundary: " + type.getSimpleName(), missing);
    }
    constructor.setAccessible(true);
    try {
      return (RegistryRecipes) constructor.newInstance(arguments);
    } catch (InvocationTargetException failure) {
      if (failure.getCause() instanceof Error) throw (Error) failure.getCause();
      throw (Exception) failure.getCause();
    }
  }

  private static void expect(String code, Runnable action) {
    try {
      action.run();
      throw new AssertionError("Expected scan guard fault " + code);
    } catch (Jobs.Fault failure) {
      require(code.equals(failure.code), "Unexpected scan guard fault: " + failure);
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
