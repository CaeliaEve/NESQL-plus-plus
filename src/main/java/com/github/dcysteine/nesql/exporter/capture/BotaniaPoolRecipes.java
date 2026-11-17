package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Botania 1.12.28 mana infusion, including the catalyst-dependent first matching recipe. */
final class BotaniaPoolRecipes implements RegistryRecipes {
  private static final String HANDLER =
      "vazkii.botania.client.integration.nei.recipe.RecipeHandlerManaPool";
  private static final String RECIPE = "vazkii.botania.api.recipe.RecipeManaInfusion";
  private static final String MINI = "vazkii.botania.api.recipe.RecipeMiniFlower";
  private static final String BEE = "magicbees.main.utils.compat.botania.SpeciesRecipeManaInfusion";
  private static final String INTERNAL = "vazkii.botania.common.core.handler.InternalMethodHandler";
  private static final String FLOWER = "vazkii.botania.common.item.block.ItemBlockSpecialFlower";
  private static final String API = "vazkii.botania.api.BotaniaAPI";
  private static final String[] STATES = {"none", "alchemy", "conjuration"};
  private List<?> registry;
  private final List<Entry> entries = new ArrayList<>();
  private final List<Variant> variants = new ArrayList<>();
  private boolean closed;
  private BotaniaRecipeBindings bindings;
  private Object alchemyBlock, conjurationBlock;
  private MagicBeesPoolSource beeSource;
  private MagicBeesPoolCache beeCache;
  private JsonObject nativeProgram;

  static boolean supports(ICraftingHandler handler) {
    return handler.getClass().getName().equals(HANDLER);
  }

  BotaniaPoolRecipes(TemplateRecipeHandler handler) {
    if (!supports(handler)) throw fault("Invalid mana infusion handler");
    bindings = new BotaniaRecipeBindings(handler);
    registry = registry();
    if (registry.size() > 65536) throw fault("Invalid mana infusion registry");
    alchemyBlock = field(type("vazkii.botania.common.block.ModBlocks"), null, "alchemyCatalyst");
    conjurationBlock =
        field(type("vazkii.botania.common.block.ModBlocks"), null, "conjurationCatalyst");
    for(Object raw:registry)if(raw!=null&&raw.getClass().getName().equals(BEE)){
      beeSource=new MagicBeesPoolSource();
      try{beeCache=MagicBeesPoolCache.bound(
          field(type("forestry.apiculture.genetics.BeeGenome"),null,"beeGenomeCache"),beeSource);}
      catch(RuntimeException|Error failure){beeSource.close();throw failure;}
      break;
    }
    BotaniaBorrowed.Budget work = new BotaniaBorrowed.Budget();
    try{for (Object raw : registry) {
      Jobs.checkpoint();
      work.step();
      Entry entry = new Entry(raw, work, beeSource);
      entries.add(entry);
      if (entry.alchemy && entry.conjuration) variants.add(new Variant(entry, -1));
      else
        for (int state = 0; state < 3; state++)
          if (entry.enabled(state)) variants.add(new Variant(entry, state));
    }
    if(beeSource!=null){
      if(entries.size()>4096)throw fault("Native bee pool registry exceeds shared program budget");
      JsonArray contexts=new JsonArray(),coercions=new JsonArray();
      Map<String,String> observed=new HashMap<>();
      for(Entry entry:entries)if(entry.mini){
        Pattern pattern=entry.patterns.get(0);
        String key=CanonicalJson.digest(stackValue(pattern.template));
        String previous=observed.putIfAbsent(key,pattern.subTileKey);
        if(previous!=null&&!previous.equals(pattern.subTileKey))
          throw fault("Equal canonical MiniFlower inputs have conflicting native key observations");
        if(previous==null)coercions.add(object("input",RecipeRow.stackEvidence(pattern.template),"value",pattern.subTileKey));
      }
      for(Entry entry:entries)contexts.add(entry.nativeEntry(coercions));
      nativeProgram=MagicBeesPoolProgram.rules(beeSource.snapshot(),contexts,beeCache.state(),beeCache.binding());
    }}catch(RuntimeException|Error failure){close();throw failure;}
  }

  private static List<?> registry() {
    MagicApi.version("BotaniaPoolRecipes","Botania");
    Object alchemy = field(type("vazkii.botania.common.block.ModBlocks"), null, "alchemyCatalyst");
    Object conjuration =
        field(type("vazkii.botania.common.block.ModBlocks"), null, "conjurationCatalyst");
    if (alchemy == null || conjuration == null || alchemy == conjuration)
      throw fault("Mana pool catalyst identities are invalid");
    return (List<?>) field(type(API), null, "manaInfusionRecipes");
  }

  private void alive() {
    if (closed) throw new Jobs.Fault("capture_closed", "Native mana pool reader is closed");
  }

  public int size() {
    alive();
    return variants.size();
  }

  public void verify() {
    alive();
    if (bindings != null) bindings.verify();
    if (field(type(API), null, "manaInfusionRecipes") != registry
        || registry.size() != entries.size()) changed();
    if ((field(type("vazkii.botania.common.block.ModBlocks"), null, "alchemyCatalyst")
            != alchemyBlock
        || field(type("vazkii.botania.common.block.ModBlocks"), null, "conjurationCatalyst")
            != conjurationBlock)) changed();
    BotaniaBorrowed.Budget work = new BotaniaBorrowed.Budget();
    for (int i = 0; i < entries.size(); i++) {
      Jobs.checkpoint();
      Entry entry = entries.get(i);
      if (registry.get(i) != entry.source || !entry.same(new Entry(registry.get(i), work,beeSource)))
        changed();
      for (Pattern pattern : entry.patterns)
        if (pattern.candidates != null) pattern.candidates.verify();
    }
  }

  public void finishVerification(){
    verify();
    if(beeSource!=null){beeSource.finishVerification();beeCache.finishVerification();}
  }

  public String program(Facts facts){
    alive();return nativeProgram==null?null:facts.beePoolProgram(nativeProgram);
  }

  public boolean capture(int index, RecipeRow row) {
    alive();
    Variant variant = variants.get(index);
    Entry entry = variant.entry;
    BotaniaBorrowed.Budget work = new BotaniaBorrowed.Budget();
    if (!entry.same(new Entry(entry.source, work,beeSource))) changed();
    if (variant.state < 0 || entry.patterns.isEmpty())
      return row.exclude(
          object(
              "native",
              entry.source.getClass().getName(),
              "reason",
              variant.state < 0
                  ? "conflicting_alchemy_and_conjuration_catalysts"
                  : "empty_native_input_predicate",
              "catalystState",
              variant.state,
              "alchemy",
              entry.alchemy,
              "conjuration",
              entry.conjuration,
              "signature",
              entry.signature));
    if(nativeProgram!=null){
      List<RecipeRow.Ingredient> displays=new ArrayList<>();
      for(Pattern pattern:entry.patterns)
        displays.add(new RecipeRow.Ingredient(pattern.example(),1,false,object("kind","nativePoolInput")));
      row.itemInput(0,displays,false);
      row.itemOutput(0,entry.output.copy(),10000);
      // A constructor prototype is a display role, not a claimed successful match.
      row.outputs.get(0).getAsJsonObject().add("change",
          object("input",0,"action",object("kind","nativePoolResult"),"samples",new JsonArray()));
      row.record.add("process",object("kind","nativeBotaniaPool","version","1.12.28-GTNH",
          "nativeRecipe",entry.source.getClass().getName(),"program",program(row.facts),
          "index",entries.indexOf(entry),"mana",entry.mana,"catalyst",STATES[variant.state],
          "scope","serialOwnedSnapshot","display","constructorPrototype",
          "matching","callbacksBeforeCatalystFirstEligible","settlement","serverDroppedItemUnitAndSignedManaUpdate"));
      return true;
    }
    List<Pattern> earlier = new ArrayList<>();
    for (Entry prior : entries) {
      work.step();
      if (prior == entry) break;
      if (prior.enabled(variant.state)) earlier.addAll(prior.patterns);
      if (earlier.size() > 65536) throw fault("Mana pool first-match budget exceeded");
    }
    boolean specialSelection = entry.mini;
    for (Pattern prior : earlier) if (prior.subTileKey != null) specialSelection = true;
    List<RecipeRow.Ingredient> choices = new ArrayList<>();
    for (Pattern base : entry.patterns) {
      Jobs.checkpoint();
      boolean covered = false;
      for (Pattern prior : earlier) {
        work.step();
        if (prior.covers(base)) {
          covered = true;
          break;
        }
      }
      if (covered) continue;
      JsonArray excluded = new JsonArray();
      Set<String> seen = new HashSet<>();
      if (!specialSelection)
        for (Pattern prior : earlier) {
          work.step();
          if (!prior.overlaps(base)) continue;
          JsonObject filter = object("id", row.facts.item(prior.example()), "rule", prior.rule());
          if (seen.add(CanonicalJson.digest(filter))) excluded.add(filter);
          if (excluded.size() > 4096)
            throw fault("Mana infusion priority exceeds the exclusion budget");
        }
      JsonObject predicate = base.rule();
      if (excluded.size() != 0)
        predicate = object("kind", "except", "base", predicate, "exclude", excluded);
      choices.add(new RecipeRow.Ingredient(base.example(), 1, false, predicate));
    }
    if (choices.isEmpty())
      return row.exclude(
          object(
              "native",
              entry.source.getClass().getName(),
              "reason",
              "all_input_patterns_covered_by_earlier_native_entries",
              "catalystState",
              variant.state,
              "signature",
              entry.signature,
              "earlierPatterns",
              earlier.size()));
    row.itemInput(0, choices, false);
    row.itemOutput(0, entry.output.copy(), 10000);
    JsonObject process = object(
            "kind",
            "botaniaPool",
            "version",
            "1.12.28-GTNH",
            "nativeRecipe",
            entry.source.getClass().getName(),
            "mana",
            entry.mana,
            "catalyst",
            STATES[variant.state],
            "matching",
            "firstEligibleNativeRegistryEntry",
            "settlement",
            "serverDroppedItemUnitAndSignedManaUpdate");
    if (specialSelection) process.add("selection", selection(entry.patterns, earlier, row, work));
    row.record.add("process", process);
    return true;
  }

  private static JsonObject selection(
      List<Pattern> current, List<Pattern> earlier, RecipeRow row, BotaniaBorrowed.Budget work) {
    JsonArray active = new JsonArray(), prior = new JsonArray(), coercions = new JsonArray();
    Map<String, String> observed = new LinkedHashMap<>();
    long bytes = 0;
    for (int phase = 0; phase < 2; phase++)
      for (Pattern pattern : phase == 0 ? current : earlier) {
        work.step();
        if (++work.selectors > 65536) throw fault("Mana pool selection exceeds selector budget");
        (phase == 0 ? active : prior).add(pattern.selector());
        if (pattern.subTileKey == null) continue;
        String id = row.facts.item(pattern.example());
        String previous = observed.putIfAbsent(id, pattern.subTileKey);
        if (previous != null) {
          if (!previous.equals(pattern.subTileKey))
            throw fault("Equal canonical flower tags have different native subtile reads");
          continue;
        }
        bytes += pattern.subTileKey.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (bytes > 1024 * 1024) throw fault("Mana pool coercions exceed byte budget");
        coercions.add(object("input", id, "value", pattern.subTileKey));
      }
    return object("current", active, "earlier", prior, "coercions", coercions);
  }

  public void close() {
    if (closed) return;
    closed = true;
    entries.clear();
    variants.clear();
    registry = null;
    if (bindings != null) {
      bindings.close();
      bindings = null;
    }
    alchemyBlock = null;
    conjurationBlock = null;
    nativeProgram=null;
    if(beeCache!=null){beeCache.close();beeCache=null;}
    if(beeSource!=null){beeSource.close();beeSource=null;}
  }

  private static final class Variant {
    final Entry entry;
    final int state;

    Variant(Entry entry, int state) {
      this.entry = entry;
      this.state = state;
    }
  }

  private static final class Entry {
    final Object source;
    final boolean alchemy, conjuration, mini;
    final int mana;
    final ItemStack output;
    final List<Pattern> patterns = new ArrayList<>();
    final String signature;
    final BotaniaBorrowed borrowed;
    final MagicBeesPoolRecipe bee;

    Entry(Object source, BotaniaBorrowed.Budget work) {
      this(source,work,null);
    }
    Entry(Object source, BotaniaBorrowed.Budget work,MagicBeesPoolSource beeSource) {
      this.source = source;
      borrowed = new BotaniaBorrowed(work);
      if (source == null
          || source.getClass() != type(RECIPE) && source.getClass() != type(MINI)
              && !(beeSource!=null&&source.getClass().getName().equals(BEE)))
        throw fault("Unadapted mana infusion recipe callback");
      bee=source.getClass().getName().equals(BEE)?new MagicBeesPoolRecipe(source,beeSource):null;
      mini = source.getClass() == type(MINI);
      alchemy = (Boolean) field(source, "isAlchemy");
      conjuration = (Boolean) field(source, "isConjuration");
      mana = (Integer) field(source, "mana");
      ItemStack result = borrowed.stack(field(source, "output"));
      if (result == null || result.getItem() == null || result.stackSize <= 0)
        throw fault("Invalid mana infusion output");
      output = result.copy();
      Object input = field(source, "input");
      borrowed.bind(input);
      JsonArray fingerprint = new JsonArray();
      if(bee!=null){
        ItemStack specimen=borrowed.stack(input);
        patterns.add(new Pattern(specimen,true));fingerprint.add(stackValue(specimen));
      }else if (mini) {
        Object handler = field(type(API), null, "internalHandler");
        if (handler == null || handler.getClass() != type(INTERNAL))
          throw fault("Unadapted MiniFlower subtile-key callback owner");
        borrowed.bind(handler);
        ItemStack sample;
        String key;
        if (input instanceof ItemStack) {
          ItemStack nativeInput = borrowed.stack(input);
          // The pinned InternalMethodHandler delegates exactly to this static NBT reader.
          // Read the bound stack before copying: non-string compound rendering is order-sensitive.
          key = (String) invoke(type(FLOWER), null, "getType",
              new Class<?>[] {ItemStack.class}, nativeInput);
          sample = nativeInput.copy();
          fingerprint.add(stackValue(nativeInput));
        } else if (input instanceof String) {
          key = (String) input;
          Object block = field(type("vazkii.botania.common.block.ModBlocks"), null, "specialFlower");
          if (!(block instanceof Block) || Item.getItemFromBlock((Block) block) == null)
            throw new Jobs.Fault("handler_not_ready", "MiniFlower native item role is unavailable");
          borrowed.bind(block);
          borrowed.bind(Item.getItemFromBlock((Block) block));
          sample = new ItemStack((Block) block);
          NBTTagCompound tag = new NBTTagCompound(); tag.setString("type", key);
          sample.setTagCompound(tag);
          fingerprint.add(value(key));
        } else throw fault("Unadapted MiniFlower input key");
        if (key == null || key.length() > 16384) throw fault("MiniFlower key exceeds text budget");
        patterns.add(new Pattern(sample, key));
      } else {
        List<?> candidates;
        if (input instanceof ItemStack) candidates = Collections.singletonList(input);
        else if (input instanceof String) candidates = borrowed.ore((String) input);
        else throw fault("Unadapted mana infusion input");
        if (candidates.size() > 65536) throw fault("Mana infusion candidate budget exceeded");
        for (Object raw : candidates) {
          work.step();
          if (!(raw instanceof ItemStack) || ((ItemStack) raw).getItem() == null)
            throw fault("Invalid mana infusion candidate");
          ItemStack item = borrowed.stack(raw);
          if (type("vazkii.botania.api.item.IManaDissolvable").isInstance(item.getItem()))
            throw fault(
                "Mana infusion input has an unadapted pre-recipe dissolve callback: "
                    + item.getItem().getClass().getName());
          if (ItemCallbacks.method(item.getItem(), "getDamage", "getDamage", ItemStack.class)
                  .getDeclaringClass()
              != Item.class) throw fault("Mana infusion input overrides metadata matching");
          if (item.getItemDamage() == 32767
              && ItemCallbacks.method(
                          item.getItem(), "setDamage", "setDamage", ItemStack.class, int.class)
                      .getDeclaringClass()
                  != Item.class)
            throw fault("Mana infusion input overrides wildcard metadata substitution");
          patterns.add(new Pattern(item));
          fingerprint.add(stackValue(item));
        }
      }
      JsonObject definition =
          object(
              "nativeRecipe", source.getClass().getName(),
              "subTileKey", mini ? patterns.get(0).subTileKey : null,
              "inputKind",
              input instanceof String ? input : "stack",
              "inputs",
              fingerprint,
              "output",
              stackValue(output),
              "alchemy",
              alchemy,
              "conjuration",
              conjuration,
              "mana",
              mana,
              "bee",bee==null?null:bee.entry());
      work.definition(definition);
      signature = CanonicalJson.digest(definition);
    }

    boolean same(Entry other) {
      if(bee!=null)bee.verify();
      return signature.equals(other.signature) && borrowed.same(other.borrowed);
    }

    boolean enabled(int state) {
      return (!alchemy || state == 1) && (!conjuration || state == 2);
    }

    JsonObject nativeEntry(JsonArray coercions){
      if(bee!=null)return bee.entry();
      if(mini)return object("kind","mini","key",patterns.get(0).subTileKey,
          "output",RecipeRow.stackEvidence(output),"mana",mana,"alchemy",alchemy,
          "conjuration",conjuration,"coercions",coercions);
      JsonArray predicates=new JsonArray();
      for(Pattern pattern:patterns)predicates.add(pattern.selector());
      // Session predicates contain only the native registry/metadata pair.
      for(JsonElement predicate:predicates)predicate.getAsJsonObject().remove("kind");
      return object("kind","item","predicates",predicates,"output",RecipeRow.stackEvidence(output),
          "mana",mana,"alchemy",alchemy,"conjuration",conjuration);
    }
  }

  private static final class Pattern {
    final ItemStack template;
    final boolean wildcard;
    final String subTileKey;
    final boolean displayOnly;
    NativeItemVariants candidates;

    Pattern(ItemStack item) {
      template = item.copy();
      template.stackSize = 1;
      template.setTagCompound(null);
      wildcard = template.getItemDamage() == 32767;
      subTileKey = null;
      displayOnly=false;
    }

    Pattern(ItemStack item,boolean displayOnly){
      template=item.copy();template.stackSize=1;wildcard=true;subTileKey=null;this.displayOnly=displayOnly;
    }
    Pattern(ItemStack item, String key) {
      ItemStack owned = item.copy();
      owned.stackSize = 1;
      if (Items.feather.getDamage(owned) == 32767) {
        ItemStack concrete = new ItemStack(owned.getItem(), 1, 0);
        concrete.setTagCompound(owned.getTagCompound());
        owned = concrete;
      }
      template = owned;
      wildcard = true;
      subTileKey = key;
      displayOnly=false;
    }

    boolean covers(Pattern other) {
      if (subTileKey != null || other.subTileKey != null)
        return subTileKey != null && subTileKey.equals(other.subTileKey);
      return template.getItem() == other.template.getItem()
          && (wildcard
              || !other.wildcard && template.getItemDamage() == other.template.getItemDamage());
    }

    boolean overlaps(Pattern other) {
      if (subTileKey != null || other.subTileKey != null)
        return subTileKey == null || other.subTileKey == null || subTileKey.equals(other.subTileKey);
      return template.getItem() == other.template.getItem()
          && (wildcard
              || other.wildcard
              || template.getItemDamage() == other.template.getItemDamage());
    }

    JsonObject rule() {
      return object("kind", "wildcard", "meta", wildcard, "nbt", true);
    }

    JsonObject selector() {
      if (subTileKey != null) return object("kind", "subTileKey", "key", subTileKey);
      String registry = Item.itemRegistry.getNameForObject(template.getItem());
      if (registry == null || registry.isEmpty()) throw fault("Unregistered mana pool selector item");
      return object("kind", "item", "registry", registry, "metadata", Items.feather.getDamage(template));
    }

    ItemStack example() {
      if(displayOnly)return template.copy();
      if (subTileKey != null) return template.copy();
      if (!wildcard) return template.copy();
      if (candidates == null) candidates = new NativeItemVariants(template);
      else candidates.verify();
      ItemStack[] samples = candidates.copies();
      if (samples == null || samples.length == 0 || samples.length > 65536)
        throw fault("Mana infusion wildcard has no bounded native display sample");
      for (ItemStack sample : samples)
        if (sample != null
            && sample.getItem() == template.getItem()
            && sample.getItemDamage() != 32767) {
          ItemStack owned = sample.copy();
          owned.stackSize = 1;
          owned.setTagCompound(null);
          return owned;
        }
      throw fault("Mana infusion wildcard has no concrete native display sample");
    }
  }

  private static JsonObject stackValue(ItemStack item) {
    return object(
        "item",
        Item.itemRegistry.getNameForObject(item.getItem()),
        "meta",
        Items.feather.getDamage(item),
        "amount",
        item.stackSize,
        "nbt",
        TypedNbt.encode(item.getTagCompound()));
  }

  private static void changed() {
    throw new Jobs.Fault("recipe_changed", "Botania mana infusion registry changed during capture");
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("recipe_unsupported", message);
  }
}
