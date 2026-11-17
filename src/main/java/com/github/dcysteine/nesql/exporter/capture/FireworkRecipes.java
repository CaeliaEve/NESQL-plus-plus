package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.FireworkRecipeHandler;
import codechicken.nei.recipe.ICraftingHandler;
import com.github.dcysteine.nesql.exporter.source.*;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.*;
import net.minecraft.item.*;
import net.minecraft.item.crafting.*;
import net.minecraft.nbt.*;

/** Native variable-input crafting and NBT transformations, independent of NEI GUI cycles. */
final class FireworkRecipes extends ReaderSnapshot implements NativeReferences {
  private static final String HANDLER = "codechicken.nei.recipe.FireworkRecipeHandler";
  private static final String FIXED = "ganymedes01.etfuturum.recipes.crafting.RecipeFixedFireworks";
  private  JsonObject source;
  private  List<?> crafting;
  private  Object recipe;
  private Object[] members;
  private int[] colors;
  private final int recipeIndex, nativeCount;
  private final String signature;
  private  Snapshot snapshot;

  static boolean supports(ICraftingHandler value) {
    return value != null && supportsClass(value.getClass().getName());
  }

  static boolean supportsClass(String name) {
    return HANDLER.equals(name);
  }

  FireworkRecipes(ICraftingHandler prototype, JsonObject origin) {
    if (!supports(prototype) || !(prototype instanceof FireworkRecipeHandler))
      throw fault("Unverified firework handler");
    source = copy(origin);
    crafting = (List<?>) registrationAnchors()[2];
    nativeCount = crafting.size();
    members = crafting.toArray(); colors = ItemDye.field_150922_c;
    int found = -1;
    for (int i = 0; i < crafting.size(); i++)
      if (crafting.get(i) instanceof RecipeFireworks) {
        Class<?> owner=crafting.get(i).getClass();
        boolean fixed=owner.getName().equals(FIXED)&&owner==MagicApi.type(FIXED);
        if(fixed)MagicApi.version("FireworkRecipes","etfuturum");
        if (found >= 0 || owner != RecipeFireworks.class && !fixed)
          throw fault("Ambiguous or custom native firework callback");
        found = i;
      }
    if (found < 0)
      throw new Jobs.Fault("handler_not_ready", "Native firework crafting rule is not registered");
    recipeIndex = found;
    recipe = crafting.get(found);
    snapshot = snapshot(source, recipe.getClass().asSubclass(RecipeFireworks.class));
    signature = CanonicalJson.digest(snapshot.records);
  }

  public int size() { checkOpen();
    return snapshot.records.size();
  }

  public String domain() { checkOpen();
    return snapshot.records.get(0).getAsJsonObject().get("domain").getAsString();
  }

  public JsonObject counts() { checkOpen();
    return object(
        "rules",
        3,
        "examples",
        size() - 1,
        "items",
        snapshot.specimens.size());
  }

  public void verify() { checkOpen();
    if (crafting != registrationAnchors()[2]
        || crafting.size() != nativeCount
        || crafting.get(recipeIndex) != recipe || colors != ItemDye.field_150922_c)
      throw new Jobs.Fault("environment_changed", "Native firework rule registration changed");
  }

  public void finishVerification() { checkOpen();
    verify();
    for (int index=0;index<members.length;index++) {
      Jobs.checkpoint();if(crafting.get(index)!=members[index])
        throw new Jobs.Fault("environment_changed", "Native firework crafting priority changed");
    }
    if (!signature.equals(CanonicalJson.digest(snapshot(source,recipe.getClass().asSubclass(RecipeFireworks.class)).records)))
      throw new Jobs.Fault(
          "environment_changed", "Native firework definitions, dyes or callback outputs changed");
  }

  public void capture(int index, Facts facts) { checkOpen();
    verify();
    if (index == 0) for (ItemStack item : snapshot.specimens.values()) facts.item(item.copy());
    facts.row("references", copy(snapshot.records.get(index).getAsJsonObject()));
  }

  static Object[] registrationAnchors() {
    CraftingManager manager=CraftingManager.getInstance();
    if(manager==null)throw new Jobs.Fault("handler_not_ready","Native crafting manager is unavailable");
    if(manager.getClass()!=CraftingManager.class)throw fault("Unadapted native firework crafting manager");
    Object recipes=manager.getRecipeList();
    if(recipes==null)throw new Jobs.Fault("handler_not_ready","Native crafting registry is unavailable");
    if(recipes.getClass()!=ArrayList.class)throw fault("Unadapted native firework crafting-list storage");
    if(((List<?>)recipes).size()>262144)throw new Jobs.Fault("recipe_limit","Firework crafting source exceeds budget");
    return new Object[]{FireworkRecipeHandler.class,manager,recipes,ItemDye.field_150922_c};
  }

  static Snapshot snapshot(JsonObject source) {
    return snapshot(source,RecipeFireworks.class);
  }

  private static Snapshot snapshot(JsonObject source,Class<? extends RecipeFireworks> rule) {
    if (!HANDLER.equals(source.get("handler").getAsString()))
      throw fault("Invalid native firework source");
    Snapshot result = new Snapshot(rule);
    JsonArray data = array(), dyes = array(), shapes = array(), effects = array();
    for (int meta = 0; meta < 16; meta++)
      dyes.add(
          object(
              "item",
              specimen(new ItemStack(Items.dye, 1, meta), result),
              "meta",
              meta,
              "rgb",
              ItemDye.field_150922_c[meta]));
    Item[] shapeItems = {null, Items.fire_charge, Items.gold_nugget, Items.skull, Items.feather};
    for (int type = 0; type < shapeItems.length; type++)
      shapes.add(
          object(
              "item",
              shapeItems[type] == null ? null : specimen(new ItemStack(shapeItems[type]), result),
              "type",
              type));
    effects.add(object("item", specimen(new ItemStack(Items.diamond), result), "nbtKey", "Trail"));
    effects.add(
        object("item", specimen(new ItemStack(Items.glowstone_dust), result), "nbtKey", "Flicker"));
    JsonObject definition =
        object(
            "kind",
            "fireworkDefinition",
            "source",
            source,
            "ready",
            true,
            "consuming",
            false,
            "recipeClass",
            rule.getName(),
            "gridWidth",
            3,
            "gridHeight",
            3,
            "unitsPerCell",
            1,
            "outputAmount",
            result.fixed ? "3" : "1",
            "colorOrder",
            "gridScan",
            "flightPolicy",
            result.fixed ? "alwaysWithPowder" : "onlyWithChargeCell",
            "fadePolicy",
            "copyTaggedStarModifyExistingCompound",
            "gunpowder",
            specimen(new ItemStack(Items.gunpowder), result),
            "paper",
            specimen(new ItemStack(Items.paper), result),
            "star",
            specimen(new ItemStack(Items.firework_charge), result),
            "rocket",
            specimen(new ItemStack(Items.fireworks), result),
            "dyes",
            dyes,
            "shapes",
            shapes,
            "effects",
            effects,
            "examples",
            0);
    data.add(definition);
    for (int shape = 0; shape < 5; shape++)
      for (int mask = 0; mask < 4; mask++) {
        List<ItemStack> cells = new ArrayList<>();
        cells.add(new ItemStack(Items.gunpowder));
        cells.add(new ItemStack(Items.dye));
        if (shapeItems[shape] != null) cells.add(new ItemStack(shapeItems[shape]));
        if ((mask & 1) != 0) cells.add(new ItemStack(Items.diamond));
        if ((mask & 2) != 0) cells.add(new ItemStack(Items.glowstone_dust));
        example("star-" + shape + "-" + mask, cells, result, data);
      }
    for (int meta = 0; meta < 16; meta++)
      example(
          "dye-" + meta,
          Arrays.asList(new ItemStack(Items.gunpowder), new ItemStack(Items.dye, 1, meta)),
          result,
          data);
    example(
        "star-repeated-colors",
        Arrays.asList(
            new ItemStack(Items.dye, 32, 1),
            new ItemStack(Items.gunpowder),
            new ItemStack(Items.dye, 1, 4),
            new ItemStack(Items.dye, 1, 1)),
        result,
        data);
    example(
        "star-repeated-effects",
        Arrays.asList(
            new ItemStack(Items.gunpowder),
            new ItemStack(Items.dye),
            new ItemStack(Items.diamond),
            new ItemStack(Items.diamond),
            new ItemStack(Items.glowstone_dust),
            new ItemStack(Items.glowstone_dust)),
        result,
        data);
    List<ItemStack> full = new ArrayList<>();
    full.add(new ItemStack(Items.gunpowder));
    for (int i = 0; i < 8; i++) full.add(new ItemStack(Items.dye, 1, i));
    example("star-full-grid", full, result, data);
    ItemStack star =
        craft(
            Arrays.asList(
                new ItemStack(Items.gunpowder),
                new ItemStack(Items.dye, 1, 1),
                new ItemStack(Items.skull),
                new ItemStack(Items.diamond)),result);
    if (star == null) throw fault("Native firework star callback is unavailable");
    star.getTagCompound().setString("owner", "retain");
    for (int powder = 1; powder <= 3; powder++)
      for (int count : new int[] {0, 1, 8 - powder}) {
        List<ItemStack> cells = new ArrayList<>();
        cells.add(new ItemStack(Items.paper));
        for (int i = 0; i < powder; i++) cells.add(new ItemStack(Items.gunpowder));
        for (int i = 0; i < count; i++) cells.add(star.copy());
        example("rocket-" + powder + "-" + count, cells, result, data);
      }
    example(
        "rocket-untagged-star",
        Arrays.asList(
            new ItemStack(Items.gunpowder),
            new ItemStack(Items.paper),
            new ItemStack(Items.firework_charge)),
        result,
        data);
    example(
        "fade-valid",
        Arrays.asList(star.copy(), new ItemStack(Items.dye, 1, 2), new ItemStack(Items.dye, 1, 2)),
        result,
        data);
    ItemStack tagged = new ItemStack(Items.firework_charge);
    tagged.setTagCompound(new NBTTagCompound());
    tagged.getTagCompound().setString("owner", "retain");
    example(
        "fade-no-explosion", Arrays.asList(tagged.copy(), new ItemStack(Items.dye)), result, data);
    tagged.getTagCompound().setInteger("Explosion", 4);
    malformedExample(
        "fade-wrong-explosion",
        Arrays.asList(tagged.copy(), new ItemStack(Items.dye)),
        result,
        data);
    example(
        "fade-untagged",
        Arrays.asList(new ItemStack(Items.firework_charge), new ItemStack(Items.dye)),
        result,
        data);
    example(
        "invalid-two-shapes",
        Arrays.asList(
            new ItemStack(Items.gunpowder),
            new ItemStack(Items.dye),
            new ItemStack(Items.skull),
            new ItemStack(Items.feather)),
        result,
        data);
    example("invalid-no-dye", Arrays.asList(new ItemStack(Items.gunpowder)), result, data);
    example(
        "invalid-two-stars-fade",
        Arrays.asList(star.copy(), star.copy(), new ItemStack(Items.dye)),
        result,
        data);
    example(
        "invalid-fade-effect",
        Arrays.asList(star.copy(), new ItemStack(Items.dye), new ItemStack(Items.diamond)),
        result,
        data);
    example(
        "invalid-unknown-item",
        Arrays.asList(
            new ItemStack(Items.gunpowder), new ItemStack(Items.dye), new ItemStack(Items.stick)),
        result,
        data);
    definition.addProperty("examples", data.size() - 1);
    String domain = Identity.content("reference", object("kind", "fireworkRules", "records", data));
    long bytes = 0;
    for (int order = 0; order < data.size(); order++) {
      JsonObject row = object("domain", domain, "order", order, "data", data.get(order));
      row.addProperty("id", Identity.content("refrow", row));
      int length = CanonicalJson.bytes(row).length;
      bytes += length;
      if (length > 1048576 || bytes > 32L * 1024 * 1024)
        throw fault("Native firework references exceed byte budget");
      result.records.add(row);
    }
    return result;
  }

  private static void example(
      String label, List<ItemStack> cells, Snapshot result, JsonArray data) {
    Jobs.checkpoint();
    if (cells.size() > 9) throw fault("Firework sample exceeds crafting grid");
    JsonArray inputs = array();
    for (ItemStack cell : cells) inputs.add(new JsonPrimitive(specimen(cell, result)));
    while (inputs.size() < 9) inputs.add(JsonNull.INSTANCE);
    ItemStack output = craft(cells,result);
    JsonObject value =
        output == null
            ? null
            : object(
                "item", specimen(output, result), "amount", Integer.toString(output.stackSize));
    data.add(
        object(
            "kind",
            "fireworkExample",
            "label",
            label,
            "inputs",
            inputs,
            "output",
            value,
            "failure",
            null));
  }

  /** An intentional corrupt-input probe; all other native exceptions propagate. */
  private static void malformedExample(
      String label, List<ItemStack> cells, Snapshot result, JsonArray data) {
    try {
      craft(cells,result);
      throw fault("Native malformed Explosion probe no longer throws its verified type error");
    } catch (net.minecraft.util.ReportedException error) {
      if (!(error.getCause() instanceof ClassCastException)) throw error;
      JsonArray inputs = array(), states=array();
      for (int slot=0;slot<cells.size();slot++) {
        ItemStack cell=cells.get(slot);NBTTagCompound tag=cell.getTagCompound();
        if(cell.getItem()==Items.firework_charge&&tag!=null&&tag.hasKey("Explosion")&&!tag.hasKey("Explosion",10)){
          NativeStackAudit.audit(cell);
          states.add(object("slot",slot,"template",object("registry",Item.itemRegistry.getNameForObject(cell.getItem()),"meta",cell.getItemDamage(),"amount",Integer.toString(cell.stackSize),"nbt",TypedNbt.encode(tag))));
          inputs.add(JsonNull.INSTANCE);
        }else inputs.add(new JsonPrimitive(specimen(cell,result)));
      }
      while (inputs.size() < 9) inputs.add(JsonNull.INSTANCE);
      data.add(
          object(
              "kind",
              "fireworkExample",
              "label",
              label,
              "inputs",
              inputs,
              "output",
              null,
              "failure",
              object(
                  "code",
                  "malformedExplosion",
                  "exception",
                  error.getClass().getName(),
                  "cause",
                  error.getCause().getClass().getName(),
                  "inputs",states)));
    }
  }

  private static ItemStack craft(List<ItemStack> cells,Snapshot snapshot) {
    InventoryCrafting grid =
        new InventoryCrafting(
            new Container() {
              public boolean canInteractWith(EntityPlayer player) {
                return false;
              }
            },
            3,
            3);
    for (int i = 0; i < cells.size(); i++) grid.setInventorySlotContents(i, cells.get(i).copy());
    RecipeFireworks recipe;
    try { recipe=snapshot.rule.newInstance(); }
    catch(ReflectiveOperationException error){throw new IllegalStateException("Cannot create owned native firework rule",error);}
    return recipe.matches(grid, null) ? recipe.getCraftingResult(grid) : null;
  }

  private static String specimen(ItemStack stack, Snapshot result) {
    String registry = Item.itemRegistry.getNameForObject(stack.getItem());
    if (registry == null || Item.itemRegistry.getObject(registry) != stack.getItem())
      throw fault("Unregistered firework specimen");
    String id =
        Identity.item(registry, stack.getItemDamage(), TypedNbt.encode(stack.getTagCompound()));
    ItemStack copy = stack.copy();
    copy.stackSize = 1;
    result.specimens.putIfAbsent(id, copy);
    return id;
  }

  static final class Snapshot {
    final Class<? extends RecipeFireworks> rule;
    final boolean fixed;
    Snapshot(Class<? extends RecipeFireworks> rule){this.rule=rule;fixed=rule.getName().equals(FIXED);}
    final JsonArray records = array();
    final Map<String, ItemStack> specimens = new TreeMap<>();
  }

  private static JsonObject copy(JsonObject value) {
    return new JsonParser().parse(value.toString()).getAsJsonObject();
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("reference_invalid", message);
  }

    @Override protected void release(){source=null;crafting=null;recipe=null;members=null;colors=null;snapshot=null;}
}
