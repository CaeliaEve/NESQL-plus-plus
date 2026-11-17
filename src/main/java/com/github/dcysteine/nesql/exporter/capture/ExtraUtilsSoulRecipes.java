package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;

/** Registered personal-inventory rule; player/world onCreated remains a separate lifecycle. */
final class ExtraUtilsSoulRecipes implements RegistryRecipes {
  private static final String HANDLER = "com.rwtema.extrautils.nei.SoulHandler";
  private static final String RECIPE = "com.rwtema.extrautils.crafting.RecipeSoul";
  private Class<?> owner;
  private CraftingManager manager;
  private List<?> crafting;
  private NativeDefinitions definitions;
  private final List<Integer> indices = new ArrayList<>();
  private Item sword, soul;
  private Object swordContainer;
  private boolean soulEnabled, swordEnabled, closed;
  private UUID modifier;

  static boolean supports(ICraftingHandler handler) {
    return handler.getClass().getName().equals(HANDLER);
  }

  static Object[] registrationRoots() {
    CraftingManager manager = CraftingManager.getInstance();
    if (manager == null || manager.getRecipeList() == null)
      throw new Jobs.Fault("handler_not_ready", "Soul crafting registry is unavailable");
    if (manager.getClass() != CraftingManager.class
        || manager.getRecipeList().getClass() != ArrayList.class)
      throw fault("Unadapted Soul crafting registry storage");
    if (manager.getRecipeList().size() > 262144)
      throw fault("Soul crafting registry exceeds budget");
    return new Object[] {manager, manager.getRecipeList()};
  }

  static Object[] registrationAnchors() {
    Object[] roots = registrationRoots();
    Class<?> owner = type("com.rwtema.extrautils.ExtraUtils");
    List<Object> anchors = new ArrayList<>(Arrays.asList(roots));
    boolean soul = (Boolean) field(owner, null, "soulEnabled"),
        sword = (Boolean) field(owner, null, "ethericSwordEnabled");
    anchors.add(Boolean.valueOf(soul));
    anchors.add(Boolean.valueOf(sword));
    if (soul && sword)
      for (Object recipe : (List<?>) roots[1])
        if (recipe != null && recipe.getClass() == type(RECIPE)) {
          Object soulItem = field(owner, null, "soul"),
              swordItem = field(owner, null, "ethericSword");
          if (soulItem == null || swordItem == null)
            throw new Jobs.Fault("handler_not_ready", "Soul native item roles are unavailable");
          anchors.add(soulItem);
          anchors.add(swordItem);
          break;
        }
    return anchors.toArray();
  }

  ExtraUtilsSoulRecipes(TemplateRecipeHandler handler) {
    MagicApi.version("ExtraUtilsSoulRecipes","ExtraUtilities");
    if (!supports(handler)) throw fault("Unknown Soul crafting handler");
    try {
      owner = type("com.rwtema.extrautils.ExtraUtils");
      soulEnabled = (Boolean) field(owner, null, "soulEnabled");
      swordEnabled = (Boolean) field(owner, null, "ethericSwordEnabled");
      Object[] roots = registrationRoots();
      manager = (CraftingManager) roots[0];
      crafting = (List<?>) roots[1];
      Class<?> recipeType = type(RECIPE);
      for (int index = 0; index < crafting.size(); index++) {
        Jobs.checkpoint();
        Object recipe = crafting.get(index);
        if (recipe != null && recipeType.isAssignableFrom(recipe.getClass())) {
          if (recipe.getClass() != recipeType) throw fault("Unadapted Soul recipe callback owner");
          indices.add(index);
        }
      }
      definitions =
          new NativeDefinitions(
              "XU Soul",
              crafting,
              this::current,
              recipe -> object("owner", recipe == null ? null : recipe.getClass().getName()));
      if (soulEnabled && swordEnabled && !indices.isEmpty()) {
        sword = (Item) field(owner, null, "ethericSword");
        soul = (Item) field(owner, null, "soul");
        if (sword == null || soul == null)
          throw new Jobs.Fault(
              "handler_not_ready", "Registered Soul crafting has missing native item roles");
        if (sword.getClass() != type("com.rwtema.extrautils.item.ItemEthericSword")
            || soul.getClass() != type("com.rwtema.extrautils.item.ItemSoul"))
          throw fault("Unadapted Soul item/lifecycle callback owner");
        if (Item.itemRegistry.getNameForObject(sword) == null
            || Item.itemRegistry.getNameForObject(soul) == null)
          throw fault("Registered Soul crafting has unregistered native items");
        swordContainer = field(Item.class, sword, "containerItem");
        if (swordContainer != null)
          throw fault(
              "Soul sword with a configured container requires a separate consumption contract");
        Object value = field(type("com.rwtema.extrautils.item.ItemSoul"), null, "uuid");
        if (!(value instanceof UUID)) throw fault("Unadapted Soul health modifier identity");
        modifier = (UUID) value;
      }
      finishVerification();
    } catch (RuntimeException | Error failure) {
      close();
      throw failure;
    }
  }

  private List<?> current() {
    check();
    Object[] roots;
    try {
      roots = registrationRoots();
    } catch (Jobs.Fault failure) {
      Jobs.Fault drift = changed();
      drift.initCause(failure);
      throw drift;
    }
    if (roots[0] != manager || roots[1] != crafting) throw changed();
    return crafting;
  }

  private void roles() {
    if (soulEnabled != (Boolean) field(owner, null, "soulEnabled")
        || swordEnabled != (Boolean) field(owner, null, "ethericSwordEnabled")) throw changed();
    if (sword != null
        && (sword != field(owner, null, "ethericSword")
            || soul != field(owner, null, "soul")
            || swordContainer != field(Item.class, sword, "containerItem")
            || modifier != field(type("com.rwtema.extrautils.item.ItemSoul"), null, "uuid")))
      throw changed();
  }

  public int size() {
    check();
    return soulEnabled && swordEnabled ? indices.size() : 0;
  }

  public JsonObject handlerExclusion() {
    check();
    return !soulEnabled || !swordEnabled
        ? object(
            "code",
            "native_disabled",
            "nativeRecipe",
            RECIPE,
            "soulEnabled",
            soulEnabled,
            "ethericSwordEnabled",
            swordEnabled)
        : null;
  }

  public void verify() {
    check();
    roles();
    definitions.verify();
    for (int index : indices) definitions.record(index);
  }

  public void finishVerification() {
    verify();
    definitions.finishVerification();
  }

  public boolean capture(int index, RecipeRow row) {
    check();
    if (index < 0 || index >= size()) throw fault("Soul recipe outside enabled native registry");
    roles();
    definitions.record(indices.get(index));
    ItemStack input = new ItemStack(sword),
        pending = new ItemStack(soul, 1, 2),
        display = new ItemStack(soul);
    row.nativeCraftingInput(
        0,
        Collections.singletonList(
            new RecipeRow.Ingredient(
                input, 1, false, object("kind", "wildcard", "meta", true, "nbt", true))));
    row.itemOutput(0,
        pending,
        10000,
        Amounts.potential("extrautils:soulLifecycle", "playerWorldCreationLifecycle", "1", 1));
    row.record.add(
        "process",
        object(
            "kind",
            "extraUtilsSoul",
            "nativeRecipe",
            RECIPE,
            "nativeLifecycle",
            "com.rwtema.extrautils.item.ItemSoul.onCreated",
            "inventorySize",
            4,
            "sword",
            row.facts.item(input),
            "pending",
            row.facts.item(pending),
            "display",
            row.facts.item(display),
            "healthDecrement",
            "0.1",
            "minimumHealth",
            "6",
            "referenceHealth",
            "20",
            "modifierUuid",
            modifier.toString()));
    roles();
    definitions.record(indices.get(index));
    return true;
  }

  private void check() {
    Jobs.checkpoint();
    if (closed) throw new Jobs.Fault("capture_closed", "Soul native reader is closed");
  }

  public void close() {
    if (closed) return;
    closed = true;
    if (definitions != null) definitions.close();
    definitions = null;
    indices.clear();
    manager = null;
    crafting = null;
    owner = null;
    sword = null;
    soul = null;
    swordContainer = null;
    modifier = null;
  }

  private static Jobs.Fault changed() {
    return new Jobs.Fault(
        "recipe_changed", "Soul native registration, item roles, enablement or lifecycle changed");
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("recipe_unsupported", message);
  }
}
