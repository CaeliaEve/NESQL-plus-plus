package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.google.gson.*;
import forestry.api.genetics.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;

/** Dynamic Gendustry contracts are compared to pinned native machine and recipe callbacks. */
public final class NativeGendustryTest {
  public static void run() throws Exception {
    GendustryAllelesTest.run();
    try (NativeRegistryScope scope = new NativeRegistryScope()) {
      scope.replace(AlleleManager.class, null, "alleleRegistry", AlleleManager.alleleRegistry);
      scope.replace(
          cpw.mods.fml.common.Loader.class,
          cpw.mods.fml.common.Loader.instance(),
          "modController",
          MagicApi.field(cpw.mods.fml.common.Loader.instance(), "modController"));
      runScoped(scope);
    }
  }

  private static void runScoped(NativeRegistryScope scope) throws Exception {
    NativeCoreFixesTest.version("gendustry", "1.9.4-GTNH");
    NativeCoreFixesTest.version("bdlib", "1.11.0-GTNH");
    AlleleManager.alleleRegistry = (IAlleleRegistry)
        Class.forName("forestry.core.genetics.alleles.AlleleRegistry").newInstance();
    cpw.mods.fml.common.ModMetadata metadata = new cpw.mods.fml.common.ModMetadata();
    metadata.modId = "gendustry";
    metadata.version = "1.6.4.135";
    Object controller =
        new cpw.mods.fml.common.LoadController(cpw.mods.fml.common.Loader.instance());
    java.lang.reflect.Field controllerField =
        cpw.mods.fml.common.Loader.class.getDeclaredField("modController");
    controllerField.setAccessible(true);
    controllerField.set(cpw.mods.fml.common.Loader.instance(), controller);
    java.lang.reflect.Field active = controller.getClass().getDeclaredField("activeContainer");
    active.setAccessible(true);
    active.set(controller, new cpw.mods.fml.common.DummyModContainer(metadata));
    Object bdlib = Class.forName("net.bdew.lib.BdLib$").getField("MODULE$").get(null);
    java.lang.reflect.Field logger = bdlib.getClass().getDeclaredField("log");
    logger.setAccessible(true);
    logger.set(bdlib, org.apache.logging.log4j.LogManager.getLogger("fixture"));
    Object gendustry = Class.forName("net.bdew.gendustry.Gendustry$").getField("MODULE$").get(null);
    java.lang.reflect.Field gendustryLog = gendustry.getClass().getDeclaredField("log");
    gendustryLog.setAccessible(true);
    gendustryLog.set(gendustry, org.apache.logging.log4j.LogManager.getLogger("fixture"));
    Item template =
        (Item)
            Class.forName("net.bdew.gendustry.items.GeneTemplate$").getField("MODULE$").get(null);
    Item sample =
        (Item) Class.forName("net.bdew.gendustry.items.GeneSample$").getField("MODULE$").get(null);
    if (Item.itemRegistry.getObject("minecraft:bucket") == null)
      Item.itemRegistry.addObject(31980, "minecraft:bucket", new Item());
    active.set(controller, null);
    TemplateRecipeHandler handler =
        (TemplateRecipeHandler) Class.forName(GendustryTemplateRecipes.HANDLER).newInstance();
    java.util.List<Object> registered = new java.util.ArrayList<>();
    registered.add(Class.forName("net.bdew.gendustry.forestry.GeneRecipe").newInstance());
    scope.replace(
        net.minecraft.item.crafting.CraftingManager.class,
        net.minecraft.item.crafting.CraftingManager.getInstance(),
        "recipes",
        registered);
    // Production must preserve borrowed GUI state, not populate display examples to read rules.
    handler.arecipes.add(null);
    GendustryTemplateRecipes adapter = new GendustryTemplateRecipes(handler);
    require(
        handler.arecipes.size() == 1 && handler.arecipes.get(0) == null,
        "Gendustry template capture mutated borrowed NEI GUI cache");
    java.util.List<ItemStack> known = new java.util.ArrayList<>();
    known.add(new ItemStack(template));
    known.add(new ItemStack(sample));
    for (Object raw : Item.itemRegistry) {
      Item item = (Item) raw;
      String name = Item.itemRegistry.getNameForObject(item);
      if (name != null && name.startsWith("gendustry:")) known.add(new ItemStack(item));
    }
    RecipeRow row = NativeCoreFixesTest.row(known.toArray(new ItemStack[0]));
    require(adapter.capture(0, row), "Template recipe disappeared");
    require(
        row.record.has("process")
            && row.record
                .getAsJsonObject("process")
                .get("kind")
                .getAsString()
                .equals("gendustryTemplate"),
        "Native GeneRecipe accepts one template and 1..8 compatible samples; static two-sample NEI projection lost this rule");
    require(
        row.outputs.get(0).getAsJsonObject().get("change").isJsonObject(),
        "Template output must retain offered tags and merge sample genes");
    ItemStack nativeContainer = sample.getContainerItem(new ItemStack(sample));
    require(
        nativeContainer != null
            && nativeContainer.getItem()
                == GendustryGeneticsRecipes.call(
                    GendustryGeneticsRecipes.module("config.Items"), "geneSampleBlank"),
        "Pinned native gene sample no longer returns its blank container");
    for (int slot = 1; slot < 3; slot++) {
      JsonArray returns =
          row.inputs
              .get(slot)
              .getAsJsonObject()
              .getAsJsonArray("choices")
              .get(0)
              .getAsJsonObject()
              .getAsJsonArray("returns");
      require(
          returns.size() == 1
              && returns
                  .get(0)
                  .getAsJsonObject()
                  .get("id")
                  .getAsString()
                  .equals(row.facts.item(nativeContainer)),
          "Template crafting lost native blank sample container return");
    }
    require(
        row.properties.entrySet().isEmpty(),
        "Template reader still embeds a fixed English explanation into every locale");
    JsonObject origin =
        com.github.dcysteine.nesql.exporter.source.Json.object(
            "owner",
            "gendustry",
            "handler",
            handler.getClass().getName(),
            "key",
            "gendustry-template-gui-free");
    row.record.add("source", origin);
    row.record.addProperty(
        "category", com.github.dcysteine.nesql.exporter.source.Identity.origin("category", origin));
    NativeContractFixture.writeOwned("gendustry-template-gui-free", row, known);
    RecipeRow chinese = NativeCoreFixesTest.row("zh_CN", known.toArray(new ItemStack[0]));
    chinese.record.add("source", origin);
    chinese.record.addProperty(
        "category", com.github.dcysteine.nesql.exporter.source.Identity.origin("category", origin));
    adapter.capture(0, chinese);
    NativeContractFixture.writeOwned("gendustry-template-gui-free-zh_CN", chinese, known);
    require(
        row.record.get("id").equals(chinese.record.get("id")),
        "Template rule identity changed with display language");
    templateSourceBoundaries(scope, handler, adapter, registered, known);
    templateCallbacks(template, sample);
    nativeReplication(template);
    nativeMutationDecay();
    nativeExtractorSourcePriority();
    nativeImprinterStockGates(template);
    nativeMutatronStockGates();
    nativeReplicatorStockGates(template);
    nativeExtractorStart();
    nativeImprinterMortality(template);
    nativeMutatronNoCandidate();
    nativeReplicatorLifespan(template);
    nativeGeneticsCatalogueCoverage();
    GendustryConfigTest.run();
    for (int rate : new int[] {0, 1, 17, 37, 99, 100}) {
      Method consumption =
          GendustryGeneticsRecipes.class.getDeclaredMethod("labwareConsumption", double.class);
      consumption.setAccessible(true);
      JsonObject actual = (JsonObject) consumption.invoke(null, (double) rate);
      require(
          actual
              .get("kind")
              .getAsString()
              .equals(rate == 0 ? "keep" : rate == 100 ? "consume" : "chance"),
          "Integer labware rate was rejected or became deterministic: " + rate);
    }
    System.out.println("Native Gendustry dynamic template callbacks and labware rates passed");
  }

  private static void templateSourceBoundaries(
      NativeRegistryScope scope,
      TemplateRecipeHandler handler,
      GendustryTemplateRecipes reader,
      java.util.List<Object> registered,
      java.util.List<ItemStack> known)
      throws Exception {
    Object manager = net.minecraft.item.crafting.CraftingManager.getInstance();
    scope.replace(manager.getClass(), manager, "recipes", new java.util.ArrayList<>(registered));
    expect("environment_changed", reader::verify);
    scope.replace(manager.getClass(), manager, "recipes", registered);
    Object recipe = registered.get(0);
    registered.set(0, Class.forName("net.bdew.gendustry.forestry.GeneRecipe").newInstance());
    expect("recipe_changed", reader::verify);
    registered.set(0, recipe);
    Item nativeSample = (Item) GendustryGeneticsRecipes.module("items.GeneSample");
    Object container = MagicApi.field(Item.class, nativeSample, "containerItem");
    scope.replace(Item.class, nativeSample, "containerItem", null);
    expect("environment_changed", reader::verify);
    scope.replace(Item.class, nativeSample, "containerItem", container);
    reader.finishVerification();
    try (Recipes.Cursor production =
        new Recipes.Handler(handler, 0)
            .open(NativeCoreFixesTest.row(known.toArray(new ItemStack[0])).facts)) {
      require(handler.arecipes.size()==1&&handler.arecipes.get(0)==null,
          "Production Gendustry template cursor changed borrowed display state");
      production.finishVerification();
    }
    require(
        handler.arecipes.size() == 1 && handler.arecipes.get(0) == null,
        "Gendustry production cursor mutated borrowed GUI state");
    require(
        RegistrationBindings.read(handler) != null
            && RegistrationBindings.read(handler)
                .same(
                    RegistrationBindings.read(
                        (TemplateRecipeHandler) handler.getClass().newInstance())),
        "Independent template registrations lost their real native source");
    scope.replace(manager.getClass(), manager, "recipes", new java.util.ArrayList<>());
    try (GendustryTemplateRecipes empty = new GendustryTemplateRecipes(handler)) {
      require(empty.size() == 0, "Absent native GeneRecipe became a synthetic display recipe");
      empty.finishVerification();
    }
    scope.replace(manager.getClass(), manager, "recipes", null);
    expect("handler_not_ready", () -> new GendustryTemplateRecipes(handler));
    final int[] callbacks = {0};
    scope.replace(
        manager.getClass(),
        manager,
        "recipes",
        new java.util.ArrayList<Object>() {
          @Override
          public int size() {
            callbacks[0]++;
            throw new AssertionError("foreign list callback");
          }
        });
    expect("recipe_unsupported", () -> new GendustryTemplateRecipes(handler));
    require(callbacks[0] == 0, "Unknown Gendustry list callback executed before ownership check");
    scope.replace(manager.getClass(), manager, "recipes", registered);
    reader.close();
    reader.close();
    expect("capture_closed", reader::size);
    expect("capture_closed", reader::verify);
    expect(
        "capture_closed",
        () -> reader.capture(0, NativeCoreFixesTest.row(known.toArray(new ItemStack[0]))));
  }

  private static void expect(String code, Runnable action) {
    try {
      action.run();
      throw new AssertionError("Expected Gendustry fault: " + code);
    } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault failure) {
      require(
          failure.code.equals(code),
          "Wrong Gendustry fault: " + failure.code + "; expected " + code);
    }
  }

  private static void templateCallbacks(Item template, Item sample) throws Exception {
    final Map<String, ISpeciesRoot> roots = new HashMap<>();
    final Map<String, IAllele> alleles = new HashMap<>();
    IChromosomeType[] genes = forestry.api.apiculture.EnumBeeChromosome.values();
    ISpeciesRoot root =
        proxy(
            ISpeciesRoot.class,
            (method, args) -> {
              if (method.getName().equals("getUID")) return "fixtureRoot";
              if (method.getName().equals("getKaryotype")) return genes;
              if (method.getName().equals("getGenomeTemplates"))
                return new HashMap<String, IAllele[]>();
              if (method.getName().equals("getMutations")) return new ArrayList<IMutation>();
              return null;
            });
    roots.put("fixtureRoot", root);
    for (String uid : new String[] {"fixture.first", "fixture.last"})
      alleles.put(
          uid,
          proxy(
              IAllele.class,
              (method, args) ->
                  method.getName().equals("getUID")
                      ? uid
                      : method.getName().equals("isDominant") ? true : null));
    AlleleManager.alleleRegistry =
        proxy(
            IAlleleRegistry.class,
            (method, args) -> {
              if (method.getName().equals("getSpeciesRoot") && (args == null || args.length == 0))
                return roots;
              if (method.getName().equals("getSpeciesRoot") && args[0] instanceof String)
                return roots.get(args[0]);
              if (method.getName().equals("getAllele")) return alleles.get(args[0]);
              return null;
            });
    Class<?> info = Class.forName("net.bdew.gendustry.forestry.GeneSampleInfo");
    Object first =
        info.getConstructor(ISpeciesRoot.class, int.class, IAllele.class)
            .newInstance(root, 1, alleles.get("fixture.first"));
    Object last =
        info.getConstructor(ISpeciesRoot.class, int.class, IAllele.class)
            .newInstance(root, 1, alleles.get("fixture.last"));
    ItemStack initial = new ItemStack(template, 7, 13);
    template
        .getClass()
        .getMethod("addSample", ItemStack.class, info)
        .invoke(template, initial, first);
    initial.setTagInfo("retained", new NBTTagString("nondefault"));
    ItemStack firstSample =
        (ItemStack) sample.getClass().getMethod("newStack", info).invoke(sample, first);
    ItemStack lastSample =
        (ItemStack) sample.getClass().getMethod("newStack", info).invoke(sample, last);
    net.minecraft.inventory.InventoryCrafting grid =
        new net.minecraft.inventory.InventoryCrafting(
            new net.minecraft.inventory.Container() {
              @Override
              public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player) {
                return true;
              }
            },
            3,
            3);
    grid.setInventorySlotContents(0, initial);
    grid.setInventorySlotContents(1, firstSample);
    grid.setInventorySlotContents(3, lastSample);
    net.minecraft.item.crafting.IRecipe nativeRecipe =
        (net.minecraft.item.crafting.IRecipe)
            Class.forName("net.bdew.gendustry.forestry.GeneRecipe").newInstance();
    ItemStack result = nativeRecipe.getCraftingResult(grid);
    require(
        result != null
            && result.stackSize == 7
            && result.getItemDamage() == 13
            && result.getTagCompound().getString("retained").equals("nondefault"),
        "Native template did not copy offered count/meta/tags");
    // x is the outer loop, so grid index 1 is visited after index 3.
    require(
        result
            .getTagCompound()
            .getTagList("samples", 10)
            .getCompoundTagAt(0)
            .getString("allele")
            .equals("fixture.first"),
        "Native column-major last-write priority changed");
    require(
        initial
            .getTagCompound()
            .getTagList("samples", 10)
            .getCompoundTagAt(0)
            .getString("allele")
            .equals("fixture.first"),
        "Native callback mutated input template");
    grid.setInventorySlotContents(3, null);
    require(nativeRecipe.matches(grid, null), "One-sample native crafting was rejected");
    grid.setInventorySlotContents(1, null);
    require(
        nativeRecipe.getCraftingResult(grid) == null, "Zero-sample native crafting was accepted");
    grid.setInventorySlotContents(1, lastSample);
    grid.setInventorySlotContents(2, initial.copy());
    require(
        nativeRecipe.getCraftingResult(grid) == null, "Two-template native crafting was accepted");
    nativeTransposer(initial, firstSample);
    nativeSampler(root, alleles.get("fixture.first"), alleles.get("fixture.last"));
  }

  private static void nativeTransposer(ItemStack template, ItemStack sample) throws Exception {
    Object cfg = configure("Transposer");
    Object tile =
        Class.forName("net.bdew.gendustry.machines.transposer.TileTransposer").newInstance();
    Object slots = GendustryGeneticsRecipes.call(tile, "slots");
    int blank = (Integer) GendustryGeneticsRecipes.call(slots, "inBlank"),
        source = (Integer) GendustryGeneticsRecipes.call(slots, "inTemplate"),
        labware = (Integer) GendustryGeneticsRecipes.call(slots, "inLabware");
    net.minecraft.item.ItemStack[] inv = (ItemStack[]) GendustryGeneticsRecipes.call(tile, "inv");
    net.minecraft.tileentity.TileEntity entity = (net.minecraft.tileentity.TileEntity) tile;
    net.minecraft.world.World world =
        (net.minecraft.world.World) allocate(Class.forName("net.minecraft.world.WorldServer"));
    set(
        world,
        "worldInfo",
        new net.minecraft.world.storage.WorldInfo(new net.minecraft.nbt.NBTTagCompound()));
    set(world, "isRemote", true);
    net.minecraft.world.chunk.Chunk emptyChunk =
        new net.minecraft.world.chunk.EmptyChunk(world, 0, 0);
    set(
        world,
        "chunkProvider",
        proxy(
            net.minecraft.world.chunk.IChunkProvider.class,
            (method, args) ->
                method.getReturnType() == boolean.class
                    ? false
                    : method.getReturnType() == int.class
                        ? 0
                        : method.getReturnType() == net.minecraft.world.chunk.Chunk.class
                            ? emptyChunk
                            : null));
    Field rand = net.minecraft.world.World.class.getDeclaredField("rand");
    rand.setAccessible(true);
    Object configured = GendustryGeneticsRecipes.module("config.Items");
    Item lab = (Item) GendustryGeneticsRecipes.call(configured, "labware"),
        blankSample = (Item) GendustryGeneticsRecipes.call(configured, "geneSampleBlank");
    // Every integer threshold is checked at its boundary with the actual tryStart callback.
    for (int rate : new int[] {0, 1, 17, 37, 99, 100})
      for (int draw : new int[] {0, Math.max(0, rate - 1), Math.min(99, rate), 99}) {
        set(cfg, "labwareConsumeChance", rate);
        rand.set(
            world,
            new Random() {
              @Override
              public int nextInt(int bound) {
                require(bound == 100, "Native labware RNG bound changed");
                return draw;
              }
            });
        entity.setWorldObj(world);
        inv[blank] = new ItemStack(blankSample, 4);
        inv[source] = sample.copy();
        inv[source].stackSize = 6;
        inv[source].setTagInfo("retained", new NBTTagString("sample-copy"));
        inv[labware] = new ItemStack(lab, 3);
        require(
            (Boolean) GendustryGeneticsRecipes.call(tile, "tryStart"),
            "Native sample transposition failed");
        ItemStack result =
            (ItemStack)
                GendustryGeneticsRecipes.call(
                    GendustryGeneticsRecipes.call(tile, "output"), "value");
        require(
            result.stackSize == 6
                && result.getTagCompound().getString("retained").equals("sample-copy"),
            "Transposer must copy entire source sample count/NBT");
        require(
            inv[source].stackSize == 6
                && inv[blank].stackSize == 3
                && inv[labware].stackSize == (draw < rate ? 2 : 3),
            "Native transposer retention/threshold consumption differs");
      }
    entity.setWorldObj(null);
    inv[blank] = new ItemStack(template.getItem(), 5);
    inv[source] = template.copy();
    inv[labware] = new ItemStack(lab, 3);
    require(
        (Boolean)
            tile.getClass()
                .getMethod("isValidInputs", ItemStack.class, ItemStack.class)
                .invoke(tile, inv[blank], inv[source]),
        "Native blank template/root compatibility was lost");
    inv[blank].setTagInfo("species", new NBTTagString("anotherRoot"));
    require(
        (Boolean)
            tile.getClass()
                .getMethod("isValidInputs", ItemStack.class, ItemStack.class)
                .invoke(tile, inv[blank], inv[source]),
        "An unresolved blank root is native wildcard compatibility");
    IAlleleRegistry prior = AlleleManager.alleleRegistry;
    ISpeciesRoot different = proxy(ISpeciesRoot.class, (method, args) -> null);
    AlleleManager.alleleRegistry =
        proxy(
            IAlleleRegistry.class,
            (method, args) ->
                method.getName().equals("getSpeciesRoot")
                        && args != null
                        && args[0].equals("anotherRoot")
                    ? different
                    : method.invoke(prior, args));
    require(
        !(Boolean)
            tile.getClass()
                .getMethod("isValidInputs", ItemStack.class, ItemStack.class)
                .invoke(tile, inv[blank], inv[source]),
        "Native incompatible template root was accepted");
  }

  private static void nativeSampler(ISpeciesRoot root, IAllele first, IAllele last)
      throws Exception {
    IChromosome[] chromosomes = new IChromosome[13];
    chromosomes[4] =
        (IChromosome)
            Class.forName("forestry.core.genetics.Chromosome")
                .getConstructor(IAllele.class, IAllele.class)
                .newInstance(first, last);
    IGenome genome =
        proxy(
            IGenome.class,
            (method, args) ->
                method.getName().equals("getChromosomes")
                    ? chromosomes
                    : method.getName().equals("getSpeciesRoot") ? root : null);
    IIndividual individual =
        proxy(
            IIndividual.class,
            (method, args) -> method.getName().equals("getGenome") ? genome : null);
    AlleleManager.alleleRegistry =
        proxy(
            IAlleleRegistry.class,
            (method, args) ->
                method.getName().equals("getSpeciesRoot") && args != null ? root : null);
    // Root boundary supplies the individual; the native sampler still creates and serializes every
    // sample.
    ISpeciesRoot supplied =
        proxy(
            ISpeciesRoot.class,
            (method, args) ->
                method.getName().equals("getMember")
                    ? individual
                    : method.getName().equals("getUID") ? "fixtureRoot" : null);
    AlleleManager.alleleRegistry =
        proxy(
            IAlleleRegistry.class,
            (method, args) ->
                method.getName().equals("getSpeciesRoot") && args != null ? supplied : null);
    Object tile = allocate(Class.forName("net.bdew.gendustry.machines.sampler.TileSampler"));
    Set<String> observed = new HashSet<>();
    for (int i = 0; i < 80; i++) {
      ItemStack result =
          (ItemStack)
              tile.getClass()
                  .getMethod("selectRandomAllele", ItemStack.class)
                  .invoke(tile, new ItemStack(net.minecraft.init.Items.paper));
      require(
          result.getTagCompound().getInteger("chromosome") == 4
              && result.getTagCompound().getString("species").equals("fixtureRoot"),
          "Native sampler changed chromosome/root");
      observed.add(result.getTagCompound().getString("allele"));
    }
    require(
        observed.equals(new HashSet<>(Arrays.asList("fixture.first", "fixture.last"))),
        "Native sampler omitted one chromosome haplotype");
  }

  private static void nativeMutationDecay() throws Exception {
    Object cfg = configure("Mutatron");
    Object helper = GendustryGeneticsRecipes.module("forestry.GeneticsHelper");
    Field rng = helper.getClass().getDeclaredField("random");
    rng.setAccessible(true);
    final boolean[] natural = {true};
    final boolean[] written = {false};
    forestry.api.apiculture.IBee resultBee =
        proxy(
            forestry.api.apiculture.IBee.class,
            (method, args) -> {
              if (method.getName().equals("setIsNatural")) {
                natural[0] = (Boolean) args[0];
                return null;
              }
              if (method.getName().equals("writeToNBT")) {
                written[0] = true;
                ((NBTTagCompound) args[0]).setBoolean("nativeDecay", !natural[0]);
                return null;
              }
              return method.getReturnType() == boolean.class ? false : null;
            });
    forestry.api.apiculture.IBee parentBee =
        proxy(
            forestry.api.apiculture.IBee.class,
            (method, args) ->
                method.getName().equals("isNatural")
                    ? natural[0]
                    : method.getReturnType() == boolean.class ? false : null);
    ItemStack output = new ItemStack(net.minecraft.init.Items.paper),
        parent = new ItemStack(net.minecraft.init.Items.book);
    output.setTagCompound(new NBTTagCompound());
    forestry.api.apiculture.IBeeRoot root =
        proxy(
            forestry.api.apiculture.IBeeRoot.class,
            (method, args) -> {
              if (method.getName().equals("getMember"))
                return args[0] == output ? resultBee : parentBee;
              return method.getReturnType() == boolean.class ? false : null;
            });
    AlleleManager.alleleRegistry =
        proxy(
            IAlleleRegistry.class,
            (method, args) -> method.getName().equals("getSpeciesRoot") ? root : null);
    Method decay =
        helper.getClass().getMethod("applyMutationDecayChance", ItemStack.class, ItemStack.class);
    Item waste =
        (Item)
            GendustryGeneticsRecipes.call(GendustryGeneticsRecipes.module("config.Items"), "waste");
    for (int threshold : new int[] {0, 1, 37, 100})
      for (int draw : new int[] {0, Math.max(0, threshold - 1), Math.min(99, threshold), 99}) {
        final int selected = draw;
        rng.set(
            helper,
            new Random() {
              @Override
              public int nextInt(int bound) {
                require(bound == 100, "Native decay RNG bound changed");
                return selected;
              }
            });
        set(cfg, "degradeChanceNatural", (float) threshold);
        natural[0] = true;
        written[0] = false;
        ItemStack degraded = (ItemStack) decay.invoke(helper, output, parent);
        require(
            degraded == output && written[0] && natural[0] != (draw < threshold),
            "Natural bee decay threshold or output state differs");
        set(cfg, "deathChanceArtificial", (float) threshold);
        natural[0] = false;
        written[0] = false;
        ItemStack dead = (ItemStack) decay.invoke(helper, output, parent);
        require(
            (draw < threshold ? dead.getItem() == waste : dead == output && written[0]),
            "Artificial bee mortality threshold or waste differs");
      }
  }

  private static void nativeReplication(Item template) throws Exception {
    final IAllele original =
        proxy(
            IAllele.class,
            (method, args) -> method.getName().equals("getUID") ? "fixture.original" : null);
    final IAllele replacement =
        proxy(
            IAllele.class,
            (method, args) -> method.getName().equals("getUID") ? "fixture.replacement" : null);
    final IAllele[] defaults = {original, original};
    final IAllele[][] observed = {null};
    final boolean[] analyzed = {false};
    IIndividual member =
        proxy(
            IIndividual.class,
            (method, args) -> {
              if (method.getName().equals("analyze")) {
                analyzed[0] = true;
                return true;
              }
              return null;
            });
    ISpeciesRoot root =
        proxy(
            ISpeciesRoot.class,
            (method, args) -> {
              if (method.getName().equals("getUID")) return "fixtureReplication";
              if (method.getName().equals("getDefaultTemplate")) return defaults;
              if (method.getName().equals("templateAsIndividual")) {
                observed[0] = (IAllele[]) args[0];
                return member;
              }
              if (method.getName().equals("getMemberStack"))
                return new ItemStack(net.minecraft.init.Items.paper, 1, (Integer) args[1]);
              return null;
            });
    AlleleManager.alleleRegistry =
        proxy(
            IAlleleRegistry.class,
            (method, args) -> {
              if (method.getName().equals("getSpeciesRoot")
                  && args != null
                  && args[0] instanceof String) return root;
              if (method.getName().equals("getAllele"))
                return "fixture.replacement".equals(args[0]) ? replacement : original;
              return null;
            });
    Class<?> info = Class.forName("net.bdew.gendustry.forestry.GeneSampleInfo");
    Object offered =
        info.getConstructor(ISpeciesRoot.class, int.class, IAllele.class)
            .newInstance(root, 1, replacement);
    ItemStack source = new ItemStack(template);
    require(
        (Boolean)
            template
                .getClass()
                .getMethod("addSample", ItemStack.class, info)
                .invoke(template, source, offered),
        "Native replication template rejected sample");
    Object helper = GendustryGeneticsRecipes.module("forestry.GeneticsHelper");
    ItemStack output =
        (ItemStack)
            helper
                .getClass()
                .getMethod("individualFromTemplate", ItemStack.class, boolean.class)
                .invoke(helper, source, false);
    require(
        output != null && output.getItem() == net.minecraft.init.Items.paper && analyzed[0],
        "Native replication did not construct and analyze an individual");
    require(
        observed[0] == defaults && defaults[0] == original && defaults[1] == replacement,
        "Native replication did not mutate the root default array in place");
  }

  private static void nativeReplicatorLifespan(Item template) throws Exception {
    Object cfg = configure("Replicator");
    set(cfg, "dnaPerItem", 20);
    set(cfg, "proteinPerItem", 30);
    set(cfg, "dnaTankSize", 1000);
    set(cfg, "proteinTankSize", 1000);
    set(cfg, "makePristineBees", false);
    final int life = forestry.api.apiculture.EnumBeeChromosome.LIFESPAN.ordinal();
    final Map<String, IAllele> registered = new HashMap<>();
    final Map<String, Integer> lifetimes = new TreeMap<>();
    lifetimes.put("fixture.short", 10);
    lifetimes.put("fixture.long", 47);
    for (String uid : lifetimes.keySet())
      registered.put(
          uid,
          proxy(
              IAlleleInteger.class,
              (m, a) ->
                  m.getName().equals("getUID")
                      ? uid
                      : m.getName().equals("getValue")
                          ? lifetimes.get(uid)
                          : m.getReturnType() == boolean.class ? true : null));
    final forestry.api.apiculture.IBeeRoot[] roots = new forestry.api.apiculture.IBeeRoot[1];
    final Map<String, IAllele[]> templates = new LinkedHashMap<>();
    for (String uid :
        new String[] {"forestry.speciesForest", "forestry.treeOak", "forestry.lepiCabbageWhite"}) {
      IAllele[] genes = new IAllele[forestry.api.apiculture.EnumBeeChromosome.values().length];
      for (int slot = 0; slot < genes.length; slot++) {
        final String geneUid = slot == 0 ? uid : "fixture.chromosome" + slot;
        IAllele gene =
            slot == 0
                ? proxy(
                    IAlleleSpecies.class,
                    (m, a) ->
                        m.getName().equals("getUID")
                            ? geneUid
                            : m.getName().equals("getRoot")
                                ? roots[0]
                                : m.getReturnType() == boolean.class ? true : null)
                : proxy(
                    IAllele.class,
                    (m, a) ->
                        m.getName().equals("getUID")
                            ? geneUid
                            : m.getReturnType() == boolean.class ? true : null);
        genes[slot] = gene;
        registered.put(geneUid, gene);
      }
      genes[life] =
          registered.get(uid.equals("forestry.speciesForest") ? "fixture.short" : "fixture.long");
      templates.put(uid, genes);
    }
    final IAllele[] defaults = templates.get("forestry.speciesForest").clone();
    roots[0] =
        proxy(
            forestry.api.apiculture.IBeeRoot.class,
            (m, a) -> {
              switch (m.getName()) {
                case "getUID":
                  return "fixtureLifespan";
                case "getKaryotype":
                  return forestry.api.apiculture.EnumBeeChromosome.values();
                case "getKaryotypeKey":
                  return forestry.api.apiculture.EnumBeeChromosome.SPECIES;
                case "getGenomeTemplates":
                  return templates;
                case "getTemplate":
                  return templates.get(a[0]);
                case "getDefaultTemplate":
                  return defaults;
                case "getMutations":
                  return Collections.emptyList();
                case "templateAsIndividual":
                  final IAllele[] alleles = ((IAllele[]) a[0]).clone();
                  forestry.api.apiculture.IBeeGenome genome =
                      proxy(
                          forestry.api.apiculture.IBeeGenome.class,
                          (gm, ga) -> {
                            if (gm.getName().equals("getLifespan"))
                              return ((IAlleleInteger) alleles[life]).getValue();
                            if (gm.getName().equals("writeToNBT")) {
                              NBTTagList rows = new NBTTagList();
                              Constructor<?> chromosome =
                                  Class.forName("forestry.core.genetics.Chromosome")
                                      .getConstructor(IAllele.class);
                              for (int slot = 0; slot < alleles.length; slot++)
                                if (alleles[slot] != null) {
                                  NBTTagCompound row = new NBTTagCompound();
                                  row.setByte("Slot", (byte) slot);
                                  Object gene = chromosome.newInstance(alleles[slot]);
                                  gene.getClass()
                                      .getMethod("writeToNBT", NBTTagCompound.class)
                                      .invoke(gene, row);
                                  rows.appendTag(row);
                                }
                              ((NBTTagCompound) ga[0]).setTag("Chromosomes", rows);
                              return null;
                            }
                            return gm.getReturnType() == boolean.class ? false : null;
                          });
                  return Class.forName("forestry.apiculture.genetics.Bee")
                      .getConstructor(forestry.api.apiculture.IBeeGenome.class)
                      .newInstance(genome);
                case "getMemberStack":
                  ItemStack stack =
                      new ItemStack(net.minecraft.init.Items.paper, 1, (Integer) a[1]);
                  NBTTagCompound tags = new NBTTagCompound();
                  ((IIndividual) a[0]).writeToNBT(tags);
                  stack.setTagCompound(tags);
                  return stack;
                default:
                  return m.getReturnType() == boolean.class ? true : null;
              }
            });
    AlleleManager.alleleRegistry =
        proxy(
            IAlleleRegistry.class,
            (m, a) -> {
              if (m.getName().equals("getSpeciesRoot"))
                return a == null || a.length == 0
                    ? Collections.singletonMap("fixtureLifespan", roots[0])
                    : roots[0];
              if (m.getName().equals("isBlacklisted")) return false;
              if (m.getName().equals("getAllele")) return registered.get(a[0]);
              if (m.getName().equals("getRegisteredAlleles"))
                return a == null || a.length == 0
                    ? registered
                    : Arrays.asList(
                        registered.get("fixture.short"), registered.get("fixture.long"));
              return m.getReturnType() == boolean.class ? true : null;
            });
    Object helper = GendustryGeneticsRecipes.module("forestry.GeneticsHelper");
    ItemStack offered =
        (ItemStack)
            helper
                .getClass()
                .getMethod("templateFromSpeciesUID", String.class)
                .invoke(helper, "forestry.treeOak");
    ItemStack output =
        (ItemStack)
            helper
                .getClass()
                .getMethod("individualFromTemplate", ItemStack.class, boolean.class)
                .invoke(helper, offered, false);
    require(
        output.getTagCompound().getInteger("Health") == 47
            && output.getTagCompound().getInteger("MaxH") == 47
            && output.getTagCompound().getBoolean("IsAnalyzed")
            && !output.getTagCompound().getBoolean("NA")
            && output.getItemDamage() == 2
            && output
                .getTagCompound()
                .getCompoundTag("Mate")
                .equals(output.getTagCompound().getCompoundTag("Genome")),
        "Actual Forestry Bee serialization lost selected lifespan, queen form, analysis or self-mating");
  }

  private static void nativeGeneticsCatalogueCoverage() throws Exception {
    try (GendustryNativeSource source = new GendustryNativeSource();
         NativeRegistryScope scope = new NativeRegistryScope()) {
      GendustryGeneticsRecipes.call(GendustryGeneticsRecipes.module("misc.GeneticsCache"), "load");
      Object dna = GendustryGeneticsRecipes.module("fluids.LiquidDNASources");
      Class<?> sources = Class.forName("net.bdew.gendustry.fluids.FluidSourceRegistry");
      scope.replace(sources,dna,"values",Class.forName("scala.collection.mutable.HashMap").newInstance());
      Method register=dna.getClass().getMethod("register",Item.class,int.class,int.class);
      register.invoke(dna,net.minecraft.init.Items.paper,32767,90);
      register.invoke(dna,net.minecraft.init.Items.paper,4,0);
      register.invoke(dna,net.minecraft.init.Items.paper,7,125);
      scope.replace(codechicken.nei.ItemList.class,null,"loadFinished",true);
      com.google.common.collect.ArrayListMultimap<Item,ItemStack> variants=com.google.common.collect.ArrayListMultimap.create();
      variants.put(net.minecraft.init.Items.paper,new ItemStack(net.minecraft.init.Items.paper,1,5));
      scope.replace(codechicken.nei.ItemList.class,null,"itemMap",variants);
      Object mutation=configure("Mutatron");
      Object maps=Class.forName("scala.collection.immutable.Map$").getField("MODULE$").get(null);
      Object empty=GendustryGeneticsRecipes.call(maps,"empty");
      Class<? extends Enum> setting=(Class<? extends Enum>)Class.forName("net.bdew.gendustry.api.EnumMutationSetting");
      scope.replace(mutation.getClass(),mutation,"mutatronOverrides",empty.getClass().getMethod("withDefaultValue",Object.class).invoke(empty,Enum.valueOf(setting,"ENABLED")));
      for(String machine:new String[]{"Imprinter","Sampler","Transposer","Replicator","Mutatron","Extractor"}){
        Object cfg=configure(machine);
        if(machine.equals("Extractor"))set(cfg,"tankSize",1000);
        TemplateRecipeHandler handler=(TemplateRecipeHandler)Class.forName("net.bdew.gendustry.nei."+machine+"Handler").newInstance();
        handler.arecipes.add(null);
        try(GendustryGeneticsRecipes reader=new GendustryGeneticsRecipes(handler)){
          GendustryGeneticsCatalog catalogue=(GendustryGeneticsCatalog)MagicApi.field(reader,"catalogue");
          List<ItemStack> known=new ArrayList<>();
          for(GendustryGeneticsCatalog.Entry entry:catalogue.entries){known.addAll(entry.inputs.values());if(entry.output!=null)known.add(entry.output);}
          List<net.minecraftforge.fluids.FluidStack> fluids=new ArrayList<>();
          if(machine.equals("Replicator")){fluids.add(GendustryGeneticsRecipes.nativeFluid("dna",20));fluids.add(GendustryGeneticsRecipes.nativeFluid("protein",30));}
          if(machine.equals("Extractor"))fluids.add(GendustryGeneticsRecipes.nativeFluid("dna",1));
          if(machine.equals("Mutatron"))fluids.add(GendustryGeneticsRecipes.nativeFluid("mutagen",50));
          int captured=0,excluded=0;
          for(int index=0;index<reader.size();index++){
            String identity=null;
            for(String locale:new String[]{"en_US","zh_CN"}){
              RecipeRow row=NativeCoreFixesTest.row(locale,known.toArray(new ItemStack[0]));
              if(!reader.capture(index,row)){require(row.exclusion!=null,"Rejected native source has no disposition");if(locale.equals("en_US"))excluded++;continue;}
              require(row.properties.entrySet().isEmpty(),"Fixed English text leaked into properties");
              if(machine.equals("Mutatron"))require(row.outputs.get(0).getAsJsonObject().getAsJsonObject("change").getAsJsonArray("samples").size()==0,
                  "A catalogue specimen became a native world mutation observation");
              writeGeneticsFixture("gendustry-current-"+machine.toLowerCase(Locale.ROOT)+"-"+index+"-"+locale,row,handler,index,known,fluids.toArray(new net.minecraftforge.fluids.FluidStack[0]));
              if(identity==null){identity=row.record.get("id").getAsString();captured++;}
              else require(identity.equals(row.record.get("id").getAsString()),"Native rule identity changed with UI language");
            }
          }
          require(captured>0,"Native "+machine+" has no exercised rule");
          if(machine.equals("Imprinter"))require(captured==4,"Lost one of the four native bee forms");
          if(machine.equals("Transposer"))require(captured==2,"Lost the sample/template branch");
          if(machine.equals("Extractor"))require(captured==2&&excluded==1,"Lost the exact-zero DNA blocker or positive keys");
          require(handler.arecipes.size()==1&&handler.arecipes.get(0)==null,"Reader mutated borrowed NEI display state");
          require(RegistrationBindings.read(handler).same(RegistrationBindings.read((TemplateRecipeHandler)handler.getClass().newInstance())),"Registration lost native source equivalence");
          reader.finishVerification();
          try(Recipes.Cursor production=new Recipes.Handler(handler,0).open(NativeCoreFixesTest.row(known.toArray(new ItemStack[0])).facts)){production.finishVerification();}
          reader.close();reader.close();expect("capture_closed",reader::size);
          System.out.println("Current native genetics catalogue: "+machine+" captured="+captured+" excluded="+excluded);
        }
      }
      try(GendustryRootBindings bindings=new GendustryRootBindings()){
        bindings.verify();bindings.finishVerification();
        IAllele[] nativeTemplate=GendustryRootBindings.templates(source.root).values().iterator().next();
        scope.replace(Class.forName("forestry.core.genetics.SpeciesRoot"),source.root,"speciesTemplates",new LinkedHashMap<>(GendustryRootBindings.templates(source.root)));
        expect("environment_changed",bindings::verify);
        require(nativeTemplate.length==source.defaults.length,"Fixture genotype lost native chromosome cardinality");
      }
    }
  }

  private static void nativeExtractorSourcePriority() throws Exception {
    Class<?> registryType = Class.forName("net.bdew.gendustry.fluids.FluidSourceRegistry");
    Object sources = registryType.newInstance();
    Method register = registryType.getMethod("register", Item.class, int.class, int.class);
    Method amount = registryType.getMethod("getValue", ItemStack.class);
    Item source = net.minecraft.init.Items.paper;
    register.invoke(sources, source, 32767, 90);
    register.invoke(sources, source, 4, 0);
    require(
        (Integer) amount.invoke(sources, new ItemStack(source, 1, 4)) == 0,
        "Exact zero DNA entry must block wildcard fallback");
    require(
        (Integer) amount.invoke(sources, new ItemStack(source, 1, 5)) == 90,
        "Wildcard DNA source amount differs");
    require(
        (Integer) amount.invoke(sources, new ItemStack(net.minecraft.init.Items.book, 1, 5)) == 0,
        "Unknown DNA source must be zero");
  }

  private static void nativeImprinterStockGates(Item template) throws Exception {
    configure("Imprinter");
    Object tile =
        Class.forName("net.bdew.gendustry.machines.imprinter.TileImprinter").newInstance();
    Object slots = GendustryGeneticsRecipes.call(tile, "slots");
    int templateSlot = (Integer) GendustryGeneticsRecipes.call(slots, "inTemplate");
    int individualSlot = (Integer) GendustryGeneticsRecipes.call(slots, "inIndividual");
    int labwareSlot = (Integer) GendustryGeneticsRecipes.call(slots, "inLabware");
    ItemStack[] inv = (ItemStack[]) GendustryGeneticsRecipes.call(tile, "inv");
    inv[templateSlot] = new ItemStack(template);
    inv[individualSlot] = new ItemStack(net.minecraft.init.Items.paper);
    require(
        !(Boolean) GendustryGeneticsRecipes.call(tile, "canStart"),
        "Imprinter started without labware stock");
    inv[labwareSlot] =
        new ItemStack(
            (Item)
                GendustryGeneticsRecipes.call(
                    GendustryGeneticsRecipes.module("config.Items"), "labware"));
    require(
        (Boolean) GendustryGeneticsRecipes.call(tile, "canStart"),
        "Imprinter rejected complete stock");
    inv[templateSlot] = null;
    require(
        !(Boolean) GendustryGeneticsRecipes.call(tile, "canStart"),
        "Imprinter started without template stock");
    inv[templateSlot] = new ItemStack(template);
    inv[individualSlot] = null;
    require(
        !(Boolean) GendustryGeneticsRecipes.call(tile, "canStart"),
        "Imprinter started without individual stock");
  }

  private static void nativeImprinterMortality(Item template) throws Exception {
    Object cfg = configure("Imprinter");
    set(cfg, "deathChanceNatural", 100);
    set(cfg, "deathChanceArtificial", 100);
    set(cfg, "labwareConsumeChance", 0);
    forestry.api.apiculture.IBeeRoot root =
        proxy(
            forestry.api.apiculture.IBeeRoot.class,
            (method, args) -> {
              if (method.getName().equals("getType"))
                return forestry.api.apiculture.EnumBeeType.PRINCESS;
              return method.getReturnType() == boolean.class ? false : null;
            });
    forestry.api.apiculture.IBeeGenome genome =
        proxy(
            forestry.api.apiculture.IBeeGenome.class,
            (method, args) -> {
              if (method.getName().equals("getSpeciesRoot")) return root;
              if (method.getName().equals("getChromosomes")) return new IChromosome[0];
              return null;
            });
    forestry.api.apiculture.IBee bee =
        proxy(
            forestry.api.apiculture.IBee.class,
            (method, args) -> {
              if (method.getName().equals("getGenome")) return genome;
              if (method.getName().equals("isNatural")) return true;
              return method.getReturnType() == boolean.class ? false : null;
            });
    AlleleManager.alleleRegistry =
        proxy(
            IAlleleRegistry.class,
            (method, args) -> {
              if (method.getName().equals("getIndividual")) return bee;
              if (method.getName().equals("getSpeciesRoot")) return root;
              return null;
            });
    Object tile =
        Class.forName("net.bdew.gendustry.machines.imprinter.TileImprinter").newInstance();
    Object slots = GendustryGeneticsRecipes.call(tile, "slots");
    ItemStack[] inv = (ItemStack[]) GendustryGeneticsRecipes.call(tile, "inv");
    ItemStack offered = new ItemStack(template);
    NBTTagCompound tags = new NBTTagCompound();
    tags.setString("species", "fixtureRoot");
    offered.setTagCompound(tags);
    inv[(Integer) GendustryGeneticsRecipes.call(slots, "inTemplate")] = offered;
    inv[(Integer) GendustryGeneticsRecipes.call(slots, "inIndividual")] =
        new ItemStack(net.minecraft.init.Items.paper, 2);
    inv[(Integer) GendustryGeneticsRecipes.call(slots, "inLabware")] =
        new ItemStack(
            (Item)
                GendustryGeneticsRecipes.call(
                    GendustryGeneticsRecipes.module("config.Items"), "labware"),
            2);
    net.minecraft.world.World world =
        (net.minecraft.world.World) allocate(Class.forName("net.minecraft.world.WorldServer"));
    set(world, "worldInfo", new net.minecraft.world.storage.WorldInfo(new NBTTagCompound()));
    net.minecraft.world.chunk.Chunk chunk = new net.minecraft.world.chunk.EmptyChunk(world, 0, 0);
    set(
        world,
        "chunkProvider",
        proxy(
            net.minecraft.world.chunk.IChunkProvider.class,
            (method, args) ->
                method.getReturnType() == boolean.class
                    ? true
                    : method.getReturnType() == int.class
                        ? 0
                        : method.getReturnType() == net.minecraft.world.chunk.Chunk.class
                            ? chunk
                            : null));
    Field rng = net.minecraft.world.World.class.getDeclaredField("rand");
    rng.setAccessible(true);
    rng.set(
        world,
        new Random() {
          @Override
          public int nextInt(int bound) {
            require(bound == 100, "Imprinter RNG bound changed");
            return 0;
          }
        });
    ((net.minecraft.tileentity.TileEntity) tile).setWorldObj(world);
    require(
        (Boolean) GendustryGeneticsRecipes.call(tile, "tryStart"),
        "Native Imprinter mortality branch did not start");
    ItemStack result =
        (ItemStack)
            GendustryGeneticsRecipes.call(GendustryGeneticsRecipes.call(tile, "output"), "value");
    Item waste =
        (Item)
            GendustryGeneticsRecipes.call(GendustryGeneticsRecipes.module("config.Items"), "waste");
    require(
        result != null
            && result.getItem() == waste
            && inv[(Integer) GendustryGeneticsRecipes.call(slots, "inIndividual")].stackSize == 1,
        "Native Imprinter mortality must output waste and consume the individual");
  }

  private static void nativeMutatronNoCandidate() throws Exception {
    IAlleleSpecies species = proxy(IAlleleSpecies.class, (method, args) -> null);
    IGenome genome =
        proxy(
            IGenome.class,
            (method, args) -> method.getName().equals("getPrimary") ? species : null);
    IIndividual individual =
        proxy(
            IIndividual.class,
            (method, args) -> method.getName().equals("getGenome") ? genome : null);
    ISpeciesRoot root =
        proxy(
            ISpeciesRoot.class,
            (method, args) -> {
              if (method.getName().equals("isMember")) return true;
              if (method.getName().equals("getMember")) return individual;
              if (method.getName().equals("getCombinations")) return new ArrayList<IMutation>();
              return null;
            });
    AlleleManager.alleleRegistry =
        proxy(
            IAlleleRegistry.class,
            (method, args) -> method.getName().equals("getSpeciesRoot") ? root : null);
    Object helper = GendustryGeneticsRecipes.module("forestry.GeneticsHelper");
    ItemStack result =
        (ItemStack)
            helper
                .getClass()
                .getMethod(
                    "getMutationResult",
                    ItemStack.class,
                    ItemStack.class,
                    forestry.api.apiculture.IBeeHousing.class)
                .invoke(
                    helper,
                    new ItemStack(net.minecraft.init.Items.paper),
                    new ItemStack(net.minecraft.init.Items.book),
                    null);
    require(result == null, "Native Mutatron must permit a no-result conditional branch");
  }

  private static void writeGeneticsFixture(
      String name,
      RecipeRow row,
      TemplateRecipeHandler handler,
      int order,
      List<ItemStack> known,
      net.minecraftforge.fluids.FluidStack... fluids)
      throws Exception {
    JsonObject origin =
        com.github.dcysteine.nesql.exporter.source.Json.object(
            "owner",
            "gendustry",
            "handler",
            handler.getClass().getName(),
            "key",
            name.replaceFirst("-(en_US|zh_CN)$", ""));
    row.record.add("source", origin);
    row.record.addProperty(
        "category", com.github.dcysteine.nesql.exporter.source.Identity.origin("category", origin));
    row.record.addProperty("order", order);
    NativeContractFixture.writeOwned(name, row, known, fluids);
  }

  private static void nativeMutatronStockGates() throws Exception {
    Object cfg = configure("Mutatron");
    set(cfg, "mutagenPerItem", 50);
    set(cfg, "tankSize", 1000);
    Object tile = Class.forName("net.bdew.gendustry.machines.mutatron.TileMutatron").newInstance();
    Object slots = GendustryGeneticsRecipes.call(tile, "slots");
    int first = (Integer) GendustryGeneticsRecipes.call(slots, "inIndividual1");
    int second = (Integer) GendustryGeneticsRecipes.call(slots, "inIndividual2");
    int labware = (Integer) GendustryGeneticsRecipes.call(slots, "inLabware");
    ItemStack[] inv = (ItemStack[]) GendustryGeneticsRecipes.call(tile, "inv");
    inv[first] = new ItemStack(net.minecraft.init.Items.paper);
    inv[second] = new ItemStack(net.minecraft.init.Items.book);
    inv[labware] =
        new ItemStack(
            (Item)
                GendustryGeneticsRecipes.call(
                    GendustryGeneticsRecipes.module("config.Items"), "labware"));
    require(
        !(Boolean) GendustryGeneticsRecipes.call(tile, "canStart"),
        "Mutatron started without mutagen stock");
    Object tank = GendustryGeneticsRecipes.call(tile, "tank");
    Method fill =
        tank.getClass()
            .getMethod("fill", net.minecraftforge.fluids.FluidStack.class, boolean.class);
    fill.invoke(tank, GendustryGeneticsRecipes.nativeFluid("mutagen", 49), true);
    require(
        !(Boolean) GendustryGeneticsRecipes.call(tile, "canStart"),
        "Mutatron started below mutagen threshold");
    fill.invoke(tank, GendustryGeneticsRecipes.nativeFluid("mutagen", 1), true);
    require(
        (Boolean) GendustryGeneticsRecipes.call(tile, "canStart"),
        "Mutatron rejected exact mutagen threshold");
    inv[second] = null;
    require(
        !(Boolean) GendustryGeneticsRecipes.call(tile, "canStart"),
        "Mutatron started without second parent");
  }

  private static void nativeReplicatorStockGates(Item template) throws Exception {
    Object cfg = configure("Replicator");
    set(cfg, "dnaPerItem", 20);
    set(cfg, "proteinPerItem", 30);
    set(cfg, "dnaTankSize", 1000);
    set(cfg, "proteinTankSize", 1000);
    Object tile =
        Class.forName("net.bdew.gendustry.machines.replicator.TileReplicator").newInstance();
    Object slots = GendustryGeneticsRecipes.call(tile, "slots");
    int source = (Integer) GendustryGeneticsRecipes.call(slots, "inTemplate");
    int output = (Integer) GendustryGeneticsRecipes.call(slots, "outIndividual");
    ItemStack[] inv = (ItemStack[]) GendustryGeneticsRecipes.call(tile, "inv");
    inv[source] = new ItemStack(template);
    require(
        !(Boolean) GendustryGeneticsRecipes.call(tile, "canStart"),
        "Replicator started with empty tanks");
    Object dna = GendustryGeneticsRecipes.call(tile, "dnaTank"),
        protein = GendustryGeneticsRecipes.call(tile, "proteinTank");
    Method dnaFill =
        dna.getClass().getMethod("fill", net.minecraftforge.fluids.FluidStack.class, boolean.class);
    Method proteinFill =
        protein
            .getClass()
            .getMethod("fill", net.minecraftforge.fluids.FluidStack.class, boolean.class);
    dnaFill.invoke(dna, GendustryGeneticsRecipes.nativeFluid("dna", 20), true);
    proteinFill.invoke(protein, GendustryGeneticsRecipes.nativeFluid("protein", 29), true);
    require(
        !(Boolean) GendustryGeneticsRecipes.call(tile, "canStart"),
        "Replicator started below protein threshold");
    proteinFill.invoke(protein, GendustryGeneticsRecipes.nativeFluid("protein", 1), true);
    require(
        (Boolean) GendustryGeneticsRecipes.call(tile, "canStart"),
        "Replicator rejected exact fluid thresholds");
    inv[output] = new ItemStack(net.minecraft.init.Items.book);
    require(
        !(Boolean) GendustryGeneticsRecipes.call(tile, "canStart"),
        "Replicator started with occupied output slot");
  }

  private static void nativeExtractorStart() throws Exception {
    Object cfg = configure("Extractor");
    set(cfg, "tankSize", 1000);
    set(cfg, "labwareConsumeChance", 37);
    Object sources = GendustryGeneticsRecipes.module("fluids.LiquidDNASources");
    sources
        .getClass()
        .getMethod("register", Item.class, int.class, int.class)
        .invoke(sources, net.minecraft.init.Items.paper, 0, 125);
    Object tile =
        Class.forName("net.bdew.gendustry.machines.extractor.TileExtractor").newInstance();
    Object slots = GendustryGeneticsRecipes.call(tile, "slots");
    int individual = (Integer) GendustryGeneticsRecipes.call(slots, "inIndividual"),
        labware = (Integer) GendustryGeneticsRecipes.call(slots, "inLabware");
    ItemStack[] inv = (ItemStack[]) GendustryGeneticsRecipes.call(tile, "inv");
    net.minecraft.world.World world =
        (net.minecraft.world.World) allocate(Class.forName("net.minecraft.world.WorldServer"));
    set(world, "worldInfo", new net.minecraft.world.storage.WorldInfo(new NBTTagCompound()));
    net.minecraft.world.chunk.Chunk chunk = new net.minecraft.world.chunk.EmptyChunk(world, 0, 0);
    set(
        world,
        "chunkProvider",
        proxy(
            net.minecraft.world.chunk.IChunkProvider.class,
            (method, args) ->
                method.getReturnType() == boolean.class
                    ? true
                    : method.getReturnType() == int.class
                        ? 0
                        : method.getReturnType() == net.minecraft.world.chunk.Chunk.class
                            ? chunk
                            : null));
    Field rng = net.minecraft.world.World.class.getDeclaredField("rand");
    rng.setAccessible(true);
    rng.set(
        world,
        new Random() {
          @Override
          public int nextInt(int bound) {
            require(bound == 100, "Extractor RNG bound changed");
            return 36;
          }
        });
    ((net.minecraft.tileentity.TileEntity) tile).setWorldObj(world);
    require(
        !(Boolean) GendustryGeneticsRecipes.call(tile, "tryStart"),
        "Extractor started without stock");
    inv[individual] = new ItemStack(net.minecraft.init.Items.paper, 2);
    inv[labware] =
        new ItemStack(
            (Item)
                GendustryGeneticsRecipes.call(
                    GendustryGeneticsRecipes.module("config.Items"), "labware"),
            2);
    require(
        (Boolean) GendustryGeneticsRecipes.call(tile, "tryStart"),
        "Extractor rejected registered DNA source");
    require(
        (Integer)
                GendustryGeneticsRecipes.call(
                    GendustryGeneticsRecipes.call(tile, "output"), "value")
            == 125,
        "Extractor lost native DNA amount");
    require(
        inv[individual].stackSize == 1 && inv[labware].stackSize == 1,
        "Extractor consumption did not use native shared draw");
    require(
        (Boolean) GendustryGeneticsRecipes.call(tile, "tryFinish"),
        "Extractor failed to fill available DNA tank");
    require(
        (Integer)
                GendustryGeneticsRecipes.call(
                    GendustryGeneticsRecipes.call(tile, "tank"), "getFluidAmount")
            == 125,
        "Extractor DNA tank amount differs");
  }

  private static Object configure(String machine) throws Exception {
    set(GendustryGeneticsRecipes.module("compat.PowerProxy"), "bitmap$0", (byte) -1);
    cpw.mods.fml.common.ModMetadata metadata = new cpw.mods.fml.common.ModMetadata();
    metadata.modId = "gendustry";
    Object controller = MagicApi.field(cpw.mods.fml.common.Loader.instance(), "modController");
    set(controller, "activeContainer", new cpw.mods.fml.common.DummyModContainer(metadata));
    require(
        cpw.mods.fml.common.registry.GameRegistry.findItem("minecraft", "bucket") != null,
        "Fixture Minecraft bucket was not discoverable");
    Object forestryItems = GendustryGeneticsRecipes.module("forestry.ForestryItems");
    set(forestryItems, "canEmpty", new Item());
    set(forestryItems, "bitmap$0", (byte) 2);
    GendustryGeneticsRecipes.module("config.Fluids");
    Object cfg =
        GendustryGeneticsRecipes.module("machines." + machine.toLowerCase() + ".Machine" + machine);
    if(MagicApi.field(cfg,"tuning")==null)
      set(cfg,"tuning",Class.forName("net.bdew.lib.recipes.gencfg.ConfigSection").getConstructor(String.class).newInstance("fixture."+machine+"."));
    for (String key :
        new String[] {
          "mjPerItem", "powerUseRate", "activationEnergy", "maxStoredEnergy", "maxReceivedEnergy"
        }) set(cfg, key, key.equals("mjPerItem") ? 125f : key.equals("powerUseRate") ? 12.5f : 0f);
    if (!machine.equals("Replicator")) set(cfg, "labwareConsumeChance", 37);
    set(cfg, "bitmap$0", (byte) -1);
    set(controller, "activeContainer", null);
    return cfg;
  }

  private static void set(Object target, String name, Object value) throws Exception {
    for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass())
      try {
        Field f = type.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
        return;
      } catch (NoSuchFieldException missing) {
      }
    throw new AssertionError("Missing fixture field " + name);
  }

  private static Object allocate(Class<?> type) throws Exception {
    Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
    f.setAccessible(true);
    return ((sun.misc.Unsafe) f.get(null)).allocateInstance(type);
  }

  private interface Call {
    Object apply(Method method, Object[] args) throws Exception;
  }

  @SuppressWarnings("unchecked")
  private static <T> T proxy(Class<T> type, Call call) {
    return (T)
        Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            (instance, method, args) -> {
              if (method.getName().equals("equals")) return instance == args[0];
              if (method.getName().equals("hashCode")) return System.identityHashCode(instance);
              return call.apply(method, args);
            });
  }

  private static void require(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
}
