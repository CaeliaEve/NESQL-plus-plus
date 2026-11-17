package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;

/** Registered GeneRecipe rules; display examples do not define the accepted sample domain. */
final class GendustryTemplateRecipes implements RegistryRecipes {
  static final String HANDLER = "net.bdew.gendustry.nei.TemplateCraftingHandler";
  private static final String RECIPE = "net.bdew.gendustry.forestry.GeneRecipe";
  private CraftingManager manager;
  private List<?> source;
  private NativeDefinitions definitions;
  private GendustryRootBindings rootBindings;
  private GendustrySpecimens specimens;
  private final List<Integer> indices = new ArrayList<>();
  private Item template, sample;
  private Object sampleContainer;
  private Object alleleRegistry;
  private JsonArray roots;
  private String signature;
  private boolean closed;

  static boolean supports(ICraftingHandler value) {
    return value != null && HANDLER.equals(value.getClass().getName());
  }

  static Object[] registrationRoots() {
    CraftingManager manager = CraftingManager.getInstance();
    if (manager == null || manager.getRecipeList() == null)
      throw new Jobs.Fault("handler_not_ready", "Gendustry crafting registry is unavailable");
    if (manager.getClass() != CraftingManager.class
        || manager.getRecipeList().getClass() != ArrayList.class)
      throw fault("Unadapted Gendustry crafting registry storage");
    if (manager.getRecipeList().size() > 262144)
      throw new Jobs.Fault("recipe_limit", "Gendustry crafting registry exceeds budget");
    return new Object[] {manager, manager.getRecipeList()};
  }

  static Object[] registrationAnchors() {
    Object[] roots = registrationRoots();
    List<Object> anchors = new ArrayList<>(java.util.Arrays.asList(roots));
    Class<?> nativeRecipe = type(RECIPE);
    for (Object recipe : (List<?>) roots[1]) {
      Jobs.checkpoint();
      if (recipe != null && nativeRecipe.isAssignableFrom(recipe.getClass())) {
        anchors.add(nativeItem("GeneTemplate"));
        anchors.add(nativeItem("GeneSample"));
        anchors.add(forestry.api.genetics.AlleleManager.alleleRegistry);
        break;
      }
    }
    return anchors.toArray();
  }

  GendustryTemplateRecipes(TemplateRecipeHandler handler) {
    if (!supports(handler)) throw fault("Unadapted Gendustry template handler");
    try {
      Object[] registry = registrationRoots();
      manager = (CraftingManager) registry[0];
      source = (List<?>) registry[1];
      Class<?> recipeType = type(RECIPE);
      for (int index = 0; index < source.size(); index++) {
        Jobs.checkpoint();
        Object recipe = source.get(index);
        if (recipe != null && recipeType.isAssignableFrom(recipe.getClass())) {
          if (recipe.getClass() != recipeType)
            throw fault("Unadapted Gendustry template recipe callback owner");
          indices.add(index);
        }
      }
      definitions =
          new NativeDefinitions(
              "Gendustry template",
              source,
              this::current,
              raw -> object("owner", raw == null ? null : raw.getClass().getName()));
      if (!indices.isEmpty()) {
        template = nativeItem("GeneTemplate");
        sample = nativeItem("GeneSample");
        sampleContainer =
            GendustryGeneticsRecipes.call(
                GendustryGeneticsRecipes.module("config.Items"), "geneSampleBlank");
        if (field(Item.class, template, "containerItem") != null
            || sampleContainer == null
            || field(Item.class, sample, "containerItem") != sampleContainer
            || !"gendustry:GeneSampleBlank"
                .equals(Item.itemRegistry.getNameForObject(sampleContainer)))
          throw fault("Gendustry template has an unadapted crafting container configuration");
        alleleRegistry = forestry.api.genetics.AlleleManager.alleleRegistry;
        if (alleleRegistry == null)
          throw new Jobs.Fault("handler_not_ready", "Gendustry allele registry is unavailable");
        rootBindings = new GendustryRootBindings();
        specimens=new GendustrySpecimens(rootBindings);
        roots = GendustryGeneticsRecipes.roots(null,specimens);
        signature = CanonicalJson.digest(roots);
      }
      verify();
    } catch (RuntimeException | Error failure) {
      close();
      throw failure;
    }
  }

  private static Item nativeItem(String name) {
    Object item = GendustryGeneticsRecipes.module("items." + name);
    if (!(item instanceof Item)
        || item.getClass() != type("net.bdew.gendustry.items." + name + "$"))
      throw fault("Unadapted Gendustry template item role: " + name);
    if (Item.itemRegistry.getNameForObject(item) == null)
      throw new Jobs.Fault("handler_not_ready", "Gendustry template item is unregistered: " + name);
    return (Item) item;
  }

  private List<?> current() {
    check();
    Object[] registry = registrationRoots();
    if (registry[0] != manager || registry[1] != source) throw changed();
    return source;
  }

  public int size() {
    check();
    return indices.size();
  }

  public void verify() {
    check();
    definitions.verify();
    if (rootBindings != null){rootBindings.verify();specimens.verify();}
    for (int index : indices) definitions.record(index);
    if (template != null
        && (template != nativeItem("GeneTemplate")
            || sample != nativeItem("GeneSample")
            || field(Item.class, template, "containerItem") != null
            || sampleContainer != field(Item.class, sample, "containerItem")
            || sampleContainer
                != GendustryGeneticsRecipes.call(
                    GendustryGeneticsRecipes.module("config.Items"), "geneSampleBlank")
            || alleleRegistry != forestry.api.genetics.AlleleManager.alleleRegistry))
      throw changed();
  }

  public void finishVerification() {
    verify();
    definitions.finishVerification();
    if(rootBindings!=null){
      rootBindings.finishVerification();specimens.finishVerification();
      try(GendustrySpecimens fresh=new GendustrySpecimens(rootBindings)){
        if(!signature.equals(CanonicalJson.digest(GendustryGeneticsRecipes.roots(null,fresh))))throw changed();
      }
    }
  }

  public boolean capture(int index, RecipeRow row) {
    verify();
    if (index < 0 || index >= indices.size())
      throw fault("Gendustry template recipe is outside its registered source");
    definitions.record(indices.get(index));
    ItemStack[] examples = {new ItemStack(template), new ItemStack(sample), new ItemStack(sample)};
    for (int slot = 0; slot < examples.length; slot++)
      row.nativeCraftingInput(
          slot,
          Collections.singletonList(
              new RecipeRow.Ingredient(
                  examples[slot],
                  1,
                  false,
                  object("kind", "wildcard", "meta", true, "nbt", true))));
    row.itemOutput(0, new ItemStack(template), 10000);
    row.record.add(
        "process",
        object(
            "kind",
            "gendustryTemplate",
            "operation",
            "merge",
            "template",
            0,
            "samples",
            array(1, 2),
            "minimumSamples",
            1,
            "maximumSamples",
            8,
            "scanOrder",
            "columnMajor",
            "roots",
            GendustryGeneticsRecipes.owned(roots)));
    // Blank samples illustrate the item roles, not a successful native observation.
    row.outputs
        .get(0)
        .getAsJsonObject()
        .add(
            "change",
            object(
                "input",
                0,
                "action",
                object(
                    "kind",
                    "gendustryGeneTemplate",
                    "operation",
                    "merge",
                    "slots",
                    object("template", 0)),
                "samples",
                array(),
                "bindings",
                array()));
    row.note(
        "gendustry:template-crafting",
        RECIPE + "#getCraftingResult;net.bdew.gendustry.items.GeneSample#containerItem",
        row.facts.locale().equals("zh_CN")
            ? "需要一个基因模板与 1～8 个同根、带有效标签的基因样本。示例中的空样本仅说明物品角色，并非成功合成结果。按列再按行扫描，同染色体最后一个样本覆盖先前样本；产物复制投入模板的数量、元数据与其他标签。每个占用格消耗一件，每份样本返还一个空基因样本容器。"
            : "Requires one gene template and 1–8 compatible, tagged gene samples. Blank samples illustrate item roles, not a successful craft. Scans columns before rows; the last sample for a chromosome replaces earlier samples. The result copies the offered template count, metadata and other tags. Each occupied slot consumes one item; each sample returns one blank gene sample container.");
    return true;
  }

  private void check() {
    Jobs.checkpoint();
    if (closed) throw new Jobs.Fault("capture_closed", "Gendustry template reader is closed");
  }

  public void close() {
    if (closed) return;
    closed = true;
    if(specimens!=null){specimens.close();specimens=null;}
    if (rootBindings != null) {
      rootBindings.close();
      rootBindings = null;
    }
    if (definitions != null) definitions.close();
    definitions = null;
    indices.clear();
    manager = null;
    source = null;
    template = sample = null;
    sampleContainer = null;
    alleleRegistry = null;
    roots = null;
    signature = null;
  }

  private static Jobs.Fault changed() {
    return new Jobs.Fault("environment_changed", "Gendustry template native source changed");
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("recipe_unsupported", message);
  }
}
