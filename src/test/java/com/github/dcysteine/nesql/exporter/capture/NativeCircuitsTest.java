package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import java.lang.reflect.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Actual Galacticraft registry and machine code, using an owned inventory and no world. */
final class NativeCircuitsTest {
    private static final String GC = "micdoodle8.mods.galacticraft.";
    @SuppressWarnings("unchecked")
    static void run() throws Exception {
        cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(cpw.mods.fml.common.Loader.class, cpw.mods.fml.common.Loader.instance(), Collections.emptyMap(), "namedMods");
        cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(cpw.mods.fml.common.ModAPIManager.class, cpw.mods.fml.common.ModAPIManager.INSTANCE, Collections.emptyMap(), "apiContainers");
        TemplateRecipeHandler handler = (TemplateRecipeHandler) type(GC + "core.nei.CircuitFabricatorRecipeHandler").newInstance();
        TemplateRecipeHandler amunra = (TemplateRecipeHandler) type("de.katzenpapst.amunra.nei.recipehandler.ARCircuitFab").newInstance();
        require(Recipes.adapter(handler) != null && Recipes.adapter(amunra) != null, "Missing circuit adapters");
        require(Recipes.adapter((TemplateRecipeHandler) type("de.katzenpapst.amunra.nei.recipehandler.ARNasaWorkbenchShuttle").newInstance()) != null,
                "AmunRa shuttle has no native adapter");
        Class<?> registryType = type(GC + "api.recipe.CircuitFabricatorRecipes");
        Map<ItemStack[], ItemStack> registry = (Map<ItemStack[], ItemStack>) field(registryType, null, "recipes");
        ItemStack paper = new ItemStack(Items.paper, 17, 32767);
        paper.setTagInfo("ignored", new net.minecraft.nbt.NBTTagInt(42));
        ItemStack output = new ItemStack(Items.diamond, 3);
        output.setTagInfo("preserved", new net.minecraft.nbt.NBTTagInt(9));
        ItemStack[] input = {paper, new ItemStack(Items.iron_ingot, 9), new ItemStack(Items.milk_bucket), new ItemStack(Items.redstone), null};
        Method add = registryType.getMethod("addRecipe", ItemStack.class, ItemStack[].class);
        Method match = registryType.getMethod("getOutputForInput", ItemStack[].class);
        add.invoke(null, output, input);
        ItemStack[] offered = new ItemStack[5];
        for (int i = 0; i < 5; i++) if (input[i] != null) { offered[i] = input[i].copy(); offered[i].stackSize = 1; offered[i].setTagCompound(null); }
        require(match.invoke(null, (Object) offered) == output, "Native matcher unexpectedly requires quantity or NBT");
        offered[0].setItemDamage(0);
        require(match.invoke(null, (Object) offered) == null, "32767 unexpectedly acts as a native wildcard");
        offered[0].setItemDamage(32767); offered[4] = new ItemStack(Items.paper);
        require(match.invoke(null, (Object) offered) == null, "Native matcher ignored an empty slot");
        offered[4] = null;
        List<Map.Entry<ItemStack[], ItemStack>> snapshot = CircuitRecipes.snapshot(registry, null);
        RecipeRow row = row(offered, output);
        require(CircuitRecipes.capture(handler, snapshot.get(0), false, true, row), "Native registered recipe rejected");
        JsonObject choice = row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
        require(choice.get("amount").getAsString().equals("1") && choice.getAsJsonObject("rule").get("nbt").getAsBoolean()
                && !choice.getAsJsonObject("rule").get("meta").getAsBoolean(), "Native amount/NBT/meta rules lost");
        require(row.inputs.size() == 4 && row.inputs.get(2).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonArray("returns").size() == 0,
                "Machine input count or no-container-return behavior changed");
        require(row.record.get("duration").getAsString().equals("300") && row.properties.has("galacticraft:emptySlots")
                && row.outputs.get(0).getAsJsonObject().get("amount").getAsString().equals("3"), "Native time/result/empty requirements lost");
        handler.arecipes.get(0).getIngredients().get(0).item.stackSize = 99;
        handler.arecipes.get(0).getResult().item.setTagCompound(null);
        require(paper.stackSize == 17 && paper.getTagCompound().getInteger("ignored") == 42 && output.getTagCompound().getInteger("preserved") == 9,
                "View changed live registration");

        // Exercise native quick-mode output and actual consume-one/no-container path without world callbacks.
        Class<?> itemsType = type(GC + "core.items.GCItems");
        Field basic = itemsType.getField("basicItem"); basic.set(null, Items.diamond);
        Class<?> config = type(GC + "core.util.ConfigManagerCore");
        Field quick = config.getField("quickMode"); quick.setBoolean(null, true);
        Class<?> machineType = type(GC + "core.tile.TileEntityCircuitFabricator");
        Object machine = machineType.newInstance();
        Field inventoryField = machineType.getDeclaredField("containingItems"); inventoryField.setAccessible(true);
        Method update = machineType.getMethod("updateInput"), compress = machineType.getMethod("compressItems");
        for (int meta : new int[] {13, 14}) {
            registry.clear(); output.setItemDamage(meta); add.invoke(null, output, input);
            ItemStack[] inventory = new ItemStack[7];
            for (int slot = 0; slot < 5; slot++) if (offered[slot] != null) { inventory[slot + 1] = offered[slot].copy(); inventory[slot + 1].stackSize = 4; }
            inventoryField.set(machine, inventory); update.invoke(machine); compress.invoke(machine);
            RecipeRow quickRow = row(offered, output);
            CircuitRecipes.capture(handler, CircuitRecipes.snapshot(registry, null).get(0), true, false, quickRow);
            require(inventory[6].stackSize == (meta == 13 ? 5 : 2) && inventory[3].getItem() == Items.milk_bucket && inventory[3].stackSize == 3,
                    "Native quick output or consumption changed");
            require(quickRow.outputs.get(0).getAsJsonObject().get("amount").getAsInt() == inventory[6].stackSize, "Export differs from native quick output");
        }
        quick.setBoolean(null, false);
        // Same predicate with different count/NBT: native encounter order wins, including another category's row.
        Map<ItemStack[], ItemStack> ordered = new LinkedHashMap<>();
        ordered.put(offered, new ItemStack(Items.gold_ingot)); ordered.put(input, output);
        require(CircuitRecipes.snapshot(ordered, null).size() == 1 && CircuitRecipes.snapshot(ordered, null).get(0).getValue().getItem() == Items.gold_ingot,
                "Native encounter precedence changed");
        Class<?> sourceType = type("de.katzenpapst.amunra.crafting.CircuitFabricatorRecipe");
        Object source = sourceType.getConstructor(ItemStack.class, ItemStack[].class, ItemStack[].class, ItemStack[].class, ItemStack[].class, ItemStack[].class)
                .newInstance(output, new ItemStack[] {input[0]}, new ItemStack[] {input[1]}, new ItemStack[] {input[2]}, new ItemStack[] {input[3]}, new ItemStack[0]);
        List<?> sources = Collections.singletonList(source);
        require(CircuitRecipes.snapshot(registry, sources).size() == 1 && CircuitRecipes.snapshot(ordered, sources).isEmpty(),
                "AmunRa projection bypasses native registration or precedence");
        Map<String, cpw.mods.fml.common.ModContainer> mods = new HashMap<>();
        for (String[] version : new String[][] {{"GalacticraftCore", "3.3.13-GTNH"}, {"GalacticraftAmunRa", "0.8.2"}}) {
            cpw.mods.fml.common.ModMetadata metadata = new cpw.mods.fml.common.ModMetadata(); metadata.modId = version[0]; metadata.version = version[1];
            mods.put(version[0], new cpw.mods.fml.common.DummyModContainer(metadata));
        }
        cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(cpw.mods.fml.common.Loader.class, cpw.mods.fml.common.Loader.instance(), mods, "namedMods");
        ((List<Object>) field(type("de.katzenpapst.amunra.crafting.RecipeHelper"), null, "circuitFabricatorRecipes")).add(source);
        require(new CircuitRecipes(handler).size() == 1 && new CircuitRecipes(amunra).size() == 1, "Production registry cursor or pinned mod identities changed");
        try { CircuitRecipes.snapshot(registry, Collections.singletonList(new Object())); throw new AssertionError("Unknown AmunRa source accepted"); }
        catch (Jobs.Fault expected) { require(expected.code.equals("recipe_unsupported"), "Wrong unknown-source error"); }
        ((List<?>) ((List<?>) field(registryType, null, "slotValidItems")).get(0)).clear();
        require(!CircuitRecipes.capture(handler, snapshot.get(0), false, false, row(offered, snapshot.get(0).getValue())), "Inaccessible native input accepted");
        for (int phase = 0; phase < 3; phase++) {
            int tick = 0;
            for (com.google.gson.JsonElement entry : CircuitRecipes.progress(phase)) {
                JsonObject frame = entry.getAsJsonObject(); JsonArray areas = frame.getAsJsonArray("areas");
                for (int step = 0; step < frame.get("ticks").getAsInt(); step++, tick++) {
                    int width = Math.min(tick, 51);
                    require((areas.size() == 1) == (width > 0 && width / 3 % 3 == phase), "Circuit animation phase differs from native clock");
                    if (areas.size() > 0) require(Math.abs(areas.get(0).getAsJsonArray().get(2).getAsFloat() * 51 - width) < .001, "Circuit progress width changed");
                }
            }
            require(tick == 70, "Circuit animation period changed");
        }
        Field clock = handler.getClass().getDeclaredField("ticksPassed"); clock.setAccessible(true); clock.setInt(handler, 42);
        try { CircuitRecipes.scene(handler, () -> { require((Integer) field(handler, "ticksPassed") == 0, "Scene has baked-in progress"); throw new IllegalStateException("test"); }); }
        catch (IllegalStateException expected) { require(expected.getMessage().equals("test"), "Wrong scene failure"); }
        require(clock.getInt(handler) == 42, "Native UI clock leaked after failure");
        NativeSpaceTest.amunra();
        System.out.println("Native circuits: GC/AmunRa routes, fixed/empty inputs, native quick mode, encounter precedence, consume-one/no returns, ownership and 70-tick animation passed");
    }
    @SuppressWarnings("unchecked")
    private static RecipeRow row(ItemStack[] inputs, ItemStack output) {
        Facts facts = new Facts("en_US"); Set<String> known = (Set<String>) field(facts, "items");
        List<ItemStack> all = new ArrayList<>(Arrays.asList(inputs)); all.add(output);
        for (ItemStack stack : all) if (stack != null) known.add(Identity.item(net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem()), stack.getItemDamage(), TypedNbt.encode(stack.getTagCompound())));
        return new RecipeRow(facts, object("owner", "GalacticraftCore", "handler", "native", "key", "circuits"), "category_test", 0);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
