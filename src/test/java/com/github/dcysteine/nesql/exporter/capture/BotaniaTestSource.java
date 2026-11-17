package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.ItemList;
import codechicken.nei.recipe.TemplateRecipeHandler;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;

/**
 * Scoped real registries for native readers; product constructors have no fixture injection path.
 */
final class BotaniaTestSource implements RegistryRecipes {
  private final NativeRegistryScope scope;
  private final RegistryRecipes reader;
  private boolean disposed;

  static BotaniaTestSource open(TemplateRecipeHandler handler, List<?> registry) throws Exception {
    return open(handler, registry, null);
  }

  static BotaniaTestSource open(
      TemplateRecipeHandler handler, List<?> registry, List<ItemStack> catalog) throws Exception {
    String[] oreFields = {"nameToId", "idToName", "idToStack", "idToStackUn", "stackToId"};
    Object[] ores = new Object[oreFields.length];
    for (int i = 0; i < ores.length; i++)
      ores[i] = MagicApi.field(net.minecraftforge.oredict.OreDictionary.class, null, oreFields[i]);
    NativeRegistryScope scope = new NativeRegistryScope();
    try {
      // This scope isolates mutation tests; these readers must see the native ore roots supplied by
      // their caller.
      for (int i = 0; i < ores.length; i++)
        scope.replace(net.minecraftforge.oredict.OreDictionary.class, null, oreFields[i], ores[i]);
      NativeCoreFixesTest.version("Botania", "1.12.28-GTNH");
      cpw.mods.fml.common.Loader loader = cpw.mods.fml.common.Loader.instance();
      Object controller = new cpw.mods.fml.common.LoadController(loader);
      cpw.mods.fml.common.ModMetadata metadata = new cpw.mods.fml.common.ModMetadata();
      metadata.modId = "Botania";
      scope.replace(
          controller.getClass(),
          controller,
          "activeContainer",
          new cpw.mods.fml.common.DummyModContainer(metadata));
      scope.replace(loader.getClass(), loader, "modController", controller);
      List<?> source =
          registry.getClass() == ArrayList.class || registry.getClass() == LinkedList.class
              ? registry
              : new ArrayList<>(registry);
      String name = handler.getClass().getName();
      boolean floating = name.endsWith("FloatingFlowers"), pool = name.endsWith("ManaPool");
      if (floating) {
        scope.replace(CraftingManager.class, CraftingManager.getInstance(), "recipes", source);
        scope.replace(ItemList.class, null, "items", catalog);
      } else
        scope.replace(
            Class.forName("vazkii.botania.api.BotaniaAPI"),
            null,
            pool ? "manaInfusionRecipes" : "runeAltarRecipes",
            source);
      RegistryRecipes reader =
          floating
              ? new BotaniaFloatingRecipes(handler)
              : pool ? new BotaniaPoolRecipes(handler) : new BotaniaRunicRecipes(handler);
      return new BotaniaTestSource(scope, reader);
    } catch (Exception | Error failure) {
      try {
        scope.close();
      } catch (Exception | Error restore) {
        failure.addSuppressed(restore);
      }
      throw failure;
    }
  }

  private BotaniaTestSource(NativeRegistryScope scope, RegistryRecipes reader) {
    this.scope = scope;
    this.reader = reader;
  }

  public int size() {
    return reader.size();
  }

  public boolean capture(int index, RecipeRow row) {
    return reader.capture(index, row);
  }

  public void verify() {
    reader.verify();
  }

  public void finishVerification() {
    reader.finishVerification();
  }

  public void close() {
    // Retain the closed reader so tests can also assert capture_closed after disposal.
    if (disposed) return;
    disposed = true;
    reader.close();
    try {
      scope.close();
    } catch (Exception e) {
      throw new AssertionError("Botania source did not restore", e);
    }
  }
}
