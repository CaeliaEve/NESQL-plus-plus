package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import java.util.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;

/** Runs the installed native RecipeSoul, then the adapter; never mutates a live player. */
final class NativeExtraUtilsSoulTest {
  static void run() throws Exception {
    Class<?> owner = Class.forName("com.rwtema.extrautils.ExtraUtils");
    Object oldSoul = owner.getField("soul").get(null),
        oldSword = owner.getField("ethericSword").get(null);
    boolean oldSoulEnabled = owner.getField("soulEnabled").getBoolean(null),
        oldSwordEnabled = owner.getField("ethericSwordEnabled").getBoolean(null);
    cpw.mods.fml.common.Loader loader = cpw.mods.fml.common.Loader.instance();
    Object oldMods = MagicApi.field(loader, "namedMods");
    Object oldServer = MagicApi.field(net.minecraft.server.MinecraftServer.class, null, "mcServer");
    List source = net.minecraft.item.crafting.CraftingManager.getInstance().getRecipeList(),
        saved = new ArrayList(source);
    try {
      checks();
    } finally {
      owner.getField("soul").set(null, oldSoul);
      owner.getField("ethericSword").set(null, oldSword);
      owner.getField("soulEnabled").setBoolean(null, oldSoulEnabled);
      owner.getField("ethericSwordEnabled").setBoolean(null, oldSwordEnabled);
      set(net.minecraft.item.crafting.CraftingManager.getInstance(), "recipes", source);
      source.clear();
      source.addAll(saved);
      set(loader, "namedMods", oldMods);
      set(net.minecraft.server.MinecraftServer.class, "mcServer", oldServer);
    }
  }

  private static void checks() throws Exception {
    Class<?> owner = Class.forName("com.rwtema.extrautils.ExtraUtils");
    Item soul = NativeCoreFixesTest.item("com.rwtema.extrautils.item.ItemSoul", "xu_soul");
    Item sword =
        NativeCoreFixesTest.item("com.rwtema.extrautils.item.ItemEthericSword", "xu_sword");
    owner.getField("soul").set(null, soul);
    owner.getField("ethericSword").set(null, sword);
    owner.getField("soulEnabled").setBoolean(null, true);
    owner.getField("ethericSwordEnabled").setBoolean(null, true);
    IRecipe nativeRecipe =
        (IRecipe) Class.forName("com.rwtema.extrautils.crafting.RecipeSoul").newInstance();
    InventoryCrafting grid =
        new InventoryCrafting(
            new Container() {
              public boolean canInteractWith(EntityPlayer p) {
                return false;
              }
            },
            2,
            2);
    ItemStack taggedSword = new ItemStack(sword, 23, 19);
    taggedSword.setTagCompound(new NBTTagCompound());
    taggedSword.getTagCompound().setString("owner", "untouched");
    require(!nativeRecipe.matches(grid, null), "Soul accepted an empty grid");
    for (int cell = 0; cell < 4; cell++) {
      grid.setInventorySlotContents(cell, taggedSword);
      require(
          nativeRecipe.matches(grid, null), "Soul lost an allowed 2x2 cell/ignored meta and NBT");
      ItemStack output = nativeRecipe.getCraftingResult(grid);
      require(
          output.getItem() == soul
              && output.getItemDamage() == 2
              && output.stackSize == 1
              && output.getTagCompound() == null,
          "Native pre-onCreated soul is not the pending meta2 result");
      grid.setInventorySlotContents((cell + 1) % 4, new ItemStack(Items.apple));
      require(!nativeRecipe.matches(grid, null), "Soul accepted a second occupied cell");
      grid.setInventorySlotContents((cell + 1) % 4, null);
      grid.setInventorySlotContents(cell, null);
    }
    InventoryCrafting large =
        new InventoryCrafting(
            new Container() {
              public boolean canInteractWith(EntityPlayer p) {
                return false;
              }
            },
            3,
            3);
    large.setInventorySlotContents(0, taggedSword);
    require(!nativeRecipe.matches(large, null), "Soul accepted a crafting table");
    require(
        nativeRecipe.getRecipeOutput().getItemDamage() == 0, "Native display soul is not meta0");
    require(
        !sword.hasContainerItem(taggedSword), "Pinned etheric sword gained a container callback");
    lifecycle(soul);
    TemplateRecipeHandler handler =
        (TemplateRecipeHandler)
            Class.forName("com.rwtema.extrautils.nei.SoulHandler").newInstance();
    require(Recipes.adapter(handler) != null, "Soul crafting has no dedicated native dispatch");
    cpw.mods.fml.common.ModMetadata metadata = new cpw.mods.fml.common.ModMetadata();
    metadata.modId = "ExtraUtilities";
    metadata.version = "1.2.12";
    Map<String, cpw.mods.fml.common.ModContainer> mods =
        new HashMap<>(cpw.mods.fml.common.Loader.instance().getIndexedModList());
    mods.put(metadata.modId, new cpw.mods.fml.common.DummyModContainer(metadata));
    cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(
        cpw.mods.fml.common.Loader.class, cpw.mods.fml.common.Loader.instance(), mods, "namedMods");
    List nativeCrafting = net.minecraft.item.crafting.CraftingManager.getInstance().getRecipeList();
    try (RegistryRecipes empty = new ExtraUtilsSoulRecipes(handler)) {
      require(
          empty.size() == 0, "XU Soul reader invented an unregistered recipe from enable flags");
    }
    nativeCrafting.add(nativeRecipe);
    RegistryRecipes adapter =
        (RegistryRecipes)
            Class.forName("com.github.dcysteine.nesql.exporter.capture.ExtraUtilsSoulRecipes")
                .getDeclaredConstructor(TemplateRecipeHandler.class)
                .newInstance(handler);
    require(adapter.size() == 1, "Enabled native Soul recipe omitted");
    ItemStack pending = new ItemStack(soul, 1, 2), display = new ItemStack(soul, 1, 0);
    RecipeRow row = NativeCoreFixesTest.row(taggedSword, new ItemStack(sword), pending, display);
    adapter.capture(0, row);
    require(
        handler.arecipes.isEmpty(),
        "XU Soul capture still writes or reconstructs the NEI GUI cache");
    require(
        row.record.getAsJsonObject("process").get("kind").getAsString().equals("extraUtilsSoul"),
        "Missing pending/lifecycle process");
    require(
        row.record.get("grid").isJsonNull(),
        "Soul was incorrectly assigned a shifted ordinary 3x3 grid");
    require(
        row.outputs.get(0).getAsJsonObject().get("amount").isJsonNull(),
        "Soul lifecycle became a guaranteed final result");
    require(
        row.inputs
            .get(0)
            .getAsJsonObject()
            .getAsJsonArray("choices")
            .get(0)
            .getAsJsonObject()
            .getAsJsonObject("rule")
            .get("meta")
            .getAsBoolean(),
        "Soul sword damage became exact admission");
    row.record.add(
        "source",
        com.github.dcysteine.nesql.exporter.source.Json.object(
            "owner",
            "ExtraUtilities",
            "handler",
            handler.getClass().getName(),
            "key",
            "native-extrautils-soul"));
    row.record.addProperty(
        "category",
        com.github.dcysteine.nesql.exporter.source.Identity.content(
            "category", row.record.getAsJsonObject("source")));
    NativeContractFixture.writeOwnedWithDurability(
        "native-extrautils-soul", row, Arrays.asList(pending, display, new ItemStack(sword)));
    sourceCases(handler, owner, nativeRecipe, sword, soul, pending, display);
    adapter.verify();
    owner.getField("soulEnabled").setBoolean(null, false);
    try {
      adapter.verify();
      throw new AssertionError("Soul enablement drift was not detected");
    } catch (Jobs.Fault expected) {
      require(expected.code.equals("recipe_changed"), "Wrong Soul drift guard");
    }
    RegistryRecipes disabled =
        (RegistryRecipes)
            Class.forName("com.github.dcysteine.nesql.exporter.capture.ExtraUtilsSoulRecipes")
                .getDeclaredConstructor(TemplateRecipeHandler.class)
                .newInstance(handler);
    require(
        disabled.size() == 0 && disabled.handlerExclusion() != null,
        "Disabled soul needs explicit native exclusion proof");
    disabled.close();
    adapter.close();
    adapter.close();
    expect("capture_closed", adapter::size);
    require(
        taggedSword.stackSize == 23
            && taggedSword.getTagCompound().getString("owner").equals("untouched"),
        "Soul capture mutated input");
    System.out.println(
        "Native Soul: four-cell domain, one occupied sword, pending/display distinction, lifecycle, disable proof and drift passed");
  }

  private static void require(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }

  private static void sourceCases(
      TemplateRecipeHandler handler,
      Class<?> owner,
      IRecipe recipe,
      Item sword,
      Item soul,
      ItemStack pending,
      ItemStack display)
      throws Exception {
    net.minecraft.item.crafting.CraftingManager manager =
        net.minecraft.item.crafting.CraftingManager.getInstance();
    List source = manager.getRecipeList();
    int position = source.indexOf(recipe);
    try (RegistryRecipes cursor = new ExtraUtilsSoulRecipes(handler)) {
      source.set(position, recipe.getClass().newInstance());
      try {
        expect("recipe_changed", cursor::verify);
      } finally {
        source.set(position, recipe);
      }
      set(manager, "recipes", new ArrayList(source));
      try {
        expect("recipe_changed", cursor::verify);
      } finally {
        set(manager, "recipes", source);
      }
      Object container = MagicApi.field(Item.class, sword, "containerItem");
      set(sword, "containerItem", Items.apple);
      try {
        expect("recipe_changed", cursor::verify);
      } finally {
        set(sword, "containerItem", container);
      }
      owner.getField("soul").set(null, null);
      try {
        expect("recipe_changed", cursor::verify);
      } finally {
        owner.getField("soul").set(null, soul);
      }
      cursor.finishVerification();
    }
    owner.getField("soul").set(null, null);
    try {
      expect("handler_not_ready", () -> new ExtraUtilsSoulRecipes(handler));
    } finally {
      owner.getField("soul").set(null, soul);
    }
    SoulItemProbe probe = new SoulItemProbe();
    owner.getField("ethericSword").set(null, probe);
    try {
      expect("recipe_unsupported", () -> new ExtraUtilsSoulRecipes(handler));
      require(probe.calls == 0, "Soul reader executed unknown item callbacks");
    } finally {
      owner.getField("ethericSword").set(null, sword);
    }
    SoulListProbe foreign = new SoulListProbe();
    set(manager, "recipes", foreign);
    try {
      expect("recipe_unsupported", () -> new ExtraUtilsSoulRecipes(handler));
      require(foreign.calls == 0, "Soul reader executed unknown source collection callbacks");
    } finally {
      set(manager, "recipes", source);
    }
    require(
        RegistrationBindings.read(handler)
            .same(
                RegistrationBindings.read(
                    (codechicken.nei.recipe.IRecipeHandler) handler.getClass().newInstance())),
        "Independent Soul registration instances lost their shared semantic source");
    handler.arecipes.add(null);
    try (Recipes.Cursor cursor =
        new Recipes.Handler(handler, 0)
            .open(NativeCoreFixesTest.row(new ItemStack(sword), pending, display).facts)) {
      require(
          MagicApi.field(cursor, "handler") == null,
          "Production Soul cursor still constructs a GUI handler");
      cursor.finishVerification();
    } finally {
      require(
          handler.arecipes.size() == 1 && handler.arecipes.get(0) == null,
          "Production Soul cursor mutated borrowed GUI state");
      handler.arecipes.clear();
    }
  }

  private static void expect(String code, Runnable action) {
    try {
      action.run();
      throw new AssertionError("Expected Soul fault " + code);
    } catch (Jobs.Fault failure) {
      require(failure.code.equals(code), "Wrong Soul fault: " + failure.code + " expected " + code);
    }
  }

  public static final class SoulItemProbe extends Item {
    int calls;

    @Override
    public boolean hasContainerItem(ItemStack stack) {
      calls++;
      throw new AssertionError("Unknown Soul item callback");
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
      calls++;
      throw new AssertionError("Unknown Soul name callback");
    }
  }

  private static final class SoulListProbe extends ArrayList {
    int calls;

    @Override
    public int size() {
      calls++;
      throw new AssertionError("Unknown Soul list callback");
    }

    @Override
    public Iterator iterator() {
      calls++;
      throw new AssertionError("Unknown Soul iterator callback");
    }
  }

  private static void lifecycle(Item soul) throws Exception {
    java.lang.reflect.Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
    unsafeField.setAccessible(true);
    sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
    java.util.UUID modifier =
        (java.util.UUID)
            Class.forName("com.rwtema.extrautils.item.ItemSoul").getField("uuid").get(null);
    EmptySoulWorld world = (EmptySoulWorld) unsafe.allocateInstance(EmptySoulWorld.class);
    net.minecraft.server.MinecraftServer server =
        (net.minecraft.server.MinecraftServer)
            unsafe.allocateInstance(net.minecraft.server.integrated.IntegratedServer.class);
    net.minecraft.server.management.ServerConfigurationManager players =
        (net.minecraft.server.management.ServerConfigurationManager)
            unsafe.allocateInstance(net.minecraft.server.integrated.IntegratedPlayerList.class);
    List<EntityPlayer> connected = new ArrayList<>();
    set(players, "playerEntityList", connected);
    set(server, "serverConfigManager", players);
    set(net.minecraft.server.MinecraftServer.class, "mcServer", server);
    for (boolean remote : new boolean[] {false, true})
      for (double health : new double[] {20, 5}) {
        world.isRemote = remote;
        SoulPlayer player = (SoulPlayer) unsafe.allocateInstance(SoulPlayer.class);
        player.worldObj = world;
        connected.clear();
        connected.add(player);
        player.profile =
            new com.mojang.authlib.GameProfile(
                java.util.UUID.fromString("f10c322b-a4f7-4971-b497-376b86cd774f"), "fixtureOwner");
        player.attributes = new net.minecraft.entity.ai.attributes.ServersideAttributeMap();
        net.minecraft.entity.ai.attributes.IAttributeInstance max =
            player.attributes.registerAttribute(
                net.minecraft.entity.SharedMonsterAttributes.maxHealth);
        max.setBaseValue(health);
        player.inventory = new net.minecraft.entity.player.InventoryPlayer(player);
        ItemStack output = new ItemStack(soul, 1, 2);
        soul.onCreated(output, world, player);
        require(
            output.getItemDamage() == (health == 20 ? 0 : 1),
            "Native soul max-health eligibility differs");
        require(
            output.getTagCompound().getString("owner").equals("fixtureOwner")
                && output
                    .getTagCompound()
                    .getString("owner_id")
                    .equals(player.profile.getId().toString()),
            "Native soul ownership lost");
        require(
            remote ? player.attacks == 0 : player.attacks == 1,
            "Soul damage event must be server-only");
        require(
            (max.getModifier(modifier) != null) == (!remote && health == 20),
            "Soul applied server health modifier outside eligible branch");
        if (!remote && health == 20)
          require(
              max.getModifier(modifier).getOperation() == 2
                  && max.getModifier(modifier).getAmount() == -0.1
                  && max.getAttributeValue() == 18,
              "Native soul modifier is not -10% multiplicative max health");
      }
    world.isRemote = false;
    EntityPlayer fake =
        (EntityPlayer)
            unsafe.allocateInstance(Class.forName("net.minecraftforge.common.util.FakePlayer"));
    ItemStack output = new ItemStack(soul, 1, 2);
    soul.onCreated(output, world, fake);
    require(
        output.getItemDamage() == 1 && output.getTagCompound() == null,
        "Native fake-player early return was lost");
  }

  private static void set(Object target, String name, Object value) throws Exception {
    Class<?> owner = target instanceof Class ? (Class<?>) target : target.getClass();
    while (owner != null) {
      try {
        java.lang.reflect.Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target instanceof Class ? null : target, value);
        return;
      } catch (NoSuchFieldException ignored) {
        owner = owner.getSuperclass();
      }
    }
    throw new NoSuchFieldException(name);
  }

  public static final class EmptySoulWorld extends net.minecraft.client.multiplayer.WorldClient {
    private EmptySoulWorld() {
      super(null, null, 0, null, null);
    }
  }

  public static final class SoulPlayer extends EntityPlayer {
    private com.mojang.authlib.GameProfile profile;
    private net.minecraft.entity.ai.attributes.BaseAttributeMap attributes;
    private int attacks;

    private SoulPlayer() {
      super(null, null);
    }

    @Override
    public com.mojang.authlib.GameProfile getGameProfile() {
      return profile;
    }

    @Override
    public String getCommandSenderName() {
      return profile.getName();
    }

    @Override
    public net.minecraft.entity.ai.attributes.BaseAttributeMap getAttributeMap() {
      return attributes;
    }

    @Override
    public boolean attackEntityFrom(net.minecraft.util.DamageSource damage, float amount) {
      attacks++;
      return false;
    }

    @Override
    public void addChatMessage(net.minecraft.util.IChatComponent message) {}

    @Override
    public boolean canCommandSenderUseCommand(int level, String command) {
      return false;
    }

    @Override
    public net.minecraft.util.ChunkCoordinates getPlayerCoordinates() {
      return new net.minecraft.util.ChunkCoordinates(0, 0, 0);
    }
  }
}
