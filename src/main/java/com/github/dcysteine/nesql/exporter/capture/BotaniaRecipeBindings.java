package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;

import codechicken.nei.ItemList;
import codechicken.nei.recipe.IRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import java.util.*;
import net.minecraft.item.Item;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraftforge.oredict.OreDictionary;

/** Same-session native roots for the three registry-backed Botania readers, never NEI caches. */
final class BotaniaRecipeBindings implements AutoCloseable {
  private final Class<?> handlerClass;
  private Object[] initial;

  BotaniaRecipeBindings(IRecipeHandler handler) {
    handlerClass = handler.getClass();
    initial = roots(handlerClass);
  }

  static boolean supportsClass(String name) {
    return name.equals("vazkii.botania.client.integration.nei.recipe.RecipeHandlerManaPool")
        || name.equals("vazkii.botania.client.integration.nei.recipe.RecipeHandlerRunicAltar")
        || name.equals("vazkii.botania.client.integration.nei.recipe.RecipeHandlerFloatingFlowers");
  }

  Object[] anchors() {
    if (initial == null) throw closed();
    return initial.clone();
  }

  void verify() {
    if (initial == null) throw closed();
    try {
      Object[] now = roots(handlerClass);
      if (now.length != initial.length) throw changed();
      for (int i = 0; i < now.length; i++) if (now[i] != initial[i]) throw changed();
    } catch (Jobs.Fault cause) {
      if (cause.code.equals("recipe_changed")) throw cause;
      Jobs.Fault drift = changed();
      drift.initCause(cause);
      throw drift;
    }
  }

  private static Object[] roots(Class<?> handler) {
    MagicApi.version("BotaniaRecipeBindings","Botania");
    String name = handler.getName();
    if (!supportsClass(name))
      throw new Jobs.Fault("recipe_unsupported", "Unknown native Botania recipe family");
    Class<?> blocks = type("vazkii.botania.common.block.ModBlocks");
    List<Object> roots = new ArrayList<>();
    roots.add(handler);
    if (name.endsWith("FloatingFlowers")) {
      CraftingManager manager = CraftingManager.getInstance();
      roots.add(manager);
      roots.add(list(manager.getRecipeList()));
      roots.add(list(ItemList.items));
      for (String field :
          new String[] {"floatingFlower", "specialFlower", "floatingSpecialFlower"}) {
        Object block = field(blocks, null, field);
        roots.add(block);
        if (block instanceof net.minecraft.block.Block)
          roots.add(Item.getItemFromBlock((net.minecraft.block.Block) block));
      }
    } else {
      roots.add(
          list(
              field(
                  type("vazkii.botania.api.BotaniaAPI"),
                  null,
                  name.endsWith("ManaPool") ? "manaInfusionRecipes" : "runeAltarRecipes")));
      if (name.endsWith("ManaPool")) {
        roots.add(field(blocks, null, "alchemyCatalyst"));
        roots.add(field(blocks, null, "conjurationCatalyst"));
      } else {
        roots.add(field(blocks, null, "livingrock"));
        roots.add(field(blocks, null, "runeAltar"));
        roots.add(field(type("vazkii.botania.common.item.ModItems"), null, "rune"));
      }
      roots.add(field(OreDictionary.class, null, "nameToId"));
      roots.add(field(OreDictionary.class, null, "idToStack"));
      roots.add(field(OreDictionary.class, null, "idToStackUn"));
    }
    for (Object root : roots)
      if (root == null)
        throw new Jobs.Fault("handler_not_ready", "Native Botania recipe source is unavailable");
    return roots.toArray();
  }

  private static Object list(Object value) {
    if (value == null)
      throw new Jobs.Fault("handler_not_ready", "Native Botania registry is unavailable");
    if (value.getClass() != ArrayList.class && value.getClass() != LinkedList.class)
      throw new Jobs.Fault("recipe_unsupported", "Unadapted native Botania registry storage");
    return value;
  }

  public void close() {
    initial = null;
  }

  private static Jobs.Fault closed() {
    return new Jobs.Fault("capture_closed", "Native Botania roots are closed");
  }

  private static Jobs.Fault changed() {
    return new Jobs.Fault("recipe_changed", "Native Botania registry, item or ore roots changed");
  }
}
