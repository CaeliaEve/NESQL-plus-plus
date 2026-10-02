package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import java.lang.reflect.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Real Galacticraft/GalaxySpace matcher and slot tests; no world, networking or GL. */
final class NativeSpaceTest {
    static void run() throws Exception {
        cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(cpw.mods.fml.common.Loader.class, cpw.mods.fml.common.Loader.instance(),
                Collections.emptyMap(), "namedMods");
        cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(cpw.mods.fml.common.ModAPIManager.class, cpw.mods.fml.common.ModAPIManager.INSTANCE,
                Collections.emptyMap(), "apiContainers");
        nativeLayouts();
        require(Recipes.adapter((TemplateRecipeHandler) Class.forName("micdoodle8.mods.galacticraft.core.nei.CircuitFabricatorRecipeHandler").newInstance()) != null,
                "Circuit fabricator has no native registry adapter");
        Class<?> rocket = Class.forName("galaxyspace.core.nei.RocketRecipeHandler");
        for (int tier = 1; tier <= 8; tier++) {
            TemplateRecipeHandler handler = (TemplateRecipeHandler) rocket.getConstructor(int.class, int.class, int.class).newInstance(tier, -12, 176);
            require(Recipes.adapter(handler) != null, "NASA tier " + tier + " has no native adapter");
        }
        for (String name : new String[] {"core.nei.BuggyRecipeHandler", "planets.mars.nei.CargoRocketRecipeHandler", "planets.asteroids.nei.AstroMinerRecipeHandler"})
            require(Recipes.adapter((TemplateRecipeHandler) Class.forName("micdoodle8.mods.galacticraft." + name).newInstance()) != null,
                    "NASA vehicle has no native adapter: " + name);
        Class<?> recipeType = Class.forName("micdoodle8.mods.galacticraft.core.recipe.NasaWorkbenchRecipe");
        HashMap<Integer, ItemStack> input = new HashMap<>();
        ItemStack paper = new ItemStack(Items.paper, 17, 2);
        paper.setTagInfo("ignored", new net.minecraft.nbt.NBTTagInt(1));
        input.put(1, paper); input.put(2, null);
        ItemStack result = new ItemStack(Items.diamond, 3);
        Object recipe = recipeType.getConstructor(ItemStack.class, HashMap.class).newInstance(result, input);
        InventoryBasic inventory = new InventoryBasic("test", true, 3);
        Method matches = recipeType.getMethod("matches", net.minecraft.inventory.IInventory.class);
        inventory.setInventorySlotContents(1, new ItemStack(Items.paper, 1, 2));
        require((Boolean) matches.invoke(recipe, inventory), "Native NASA matching uses quantity or NBT");
        inventory.setInventorySlotContents(1, new ItemStack(Items.paper, 1, 32767));
        require((Boolean) matches.invoke(recipe, inventory), "Native NASA offered wildcard branch changed");
        inventory.setInventorySlotContents(1, new ItemStack(Items.paper, 1, 3));
        require(!(Boolean) matches.invoke(recipe, inventory), "Native NASA accepted wrong metadata");
        inventory.setInventorySlotContents(1, new ItemStack(Items.paper, 1, 2));
        inventory.setInventorySlotContents(2, new ItemStack(Items.paper));
        require(!(Boolean) matches.invoke(recipe, inventory), "Native NASA ignored an explicit empty slot");
        Class<?> slotType = Class.forName("galaxyspace.core.inventory.slot.SlotSchematic");
        Slot slot = (Slot) slotType.getConstructor(net.minecraft.inventory.IInventory.class, int.class, int.class, int.class,
                int.class, int.class, int.class, net.minecraft.entity.player.EntityPlayer.class, net.minecraft.item.Item.class, int.class)
                .newInstance(inventory, 1, 44, 37, 0, 0, 0, null, Items.paper, 2);
        require(slot.isItemValid(new ItemStack(Items.paper, 1, 2)) && !slot.isItemValid(new ItemStack(Items.paper, 1, 32767)),
                "Native physical slot does not narrow the NASA wildcard branch");
        inventory.setInventorySlotContents(2, null);
        Class<?> resultType = Class.forName("micdoodle8.mods.galacticraft.core.inventory.SlotRocketBenchResult");
        Slot output = (Slot) resultType.getConstructor(net.minecraft.entity.player.EntityPlayer.class, net.minecraft.inventory.IInventory.class,
                net.minecraft.inventory.IInventory.class, int.class, int.class, int.class).newInstance(null, inventory, new InventoryBasic("out", true, 1), 0, 134, 73);
        Slot empty = (Slot) slotType.getConstructor(net.minecraft.inventory.IInventory.class, int.class, int.class, int.class,
                int.class, int.class, int.class, net.minecraft.entity.player.EntityPlayer.class, net.minecraft.item.Item.class, int.class)
                .newInstance(inventory, 2, 62, 37, 0, 0, 0, null, Items.book, 0);
        net.minecraft.inventory.Container container = new net.minecraft.inventory.Container() {
            public final net.minecraft.inventory.IInventory craftMatrix = inventory;
            { addSlotToContainer(output); addSlotToContainer(slot); addSlotToContainer(empty); }
            @Override public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player) { return true; }
        };
        SpaceRecipes.Layout layout = new SpaceRecipes.Layout(container, 16);
        require(!SpaceRecipes.shadowed(Collections.emptyList(), recipe, layout)
                && SpaceRecipes.shadowed(Collections.singletonList(recipe), recipe, layout), "Native NASA first-match precedence changed");
        TemplateRecipeHandler handler = (TemplateRecipeHandler) rocket.getConstructor(int.class, int.class, int.class).newInstance(1, -12, 176);
        RecipeRow row = row(new ItemStack(Items.paper, 1, 2), result);
        require(SpaceRecipes.capture(handler, recipe, layout, row), "Usable native NASA recipe was excluded");
        com.google.gson.JsonObject choice = row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
        require(row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").size() == 1 && choice.get("amount").getAsString().equals("1")
                && choice.getAsJsonObject("rule").get("nbt").getAsBoolean() && !choice.getAsJsonObject("rule").get("meta").getAsBoolean(),
                "NASA exported display counts, required NBT, or a physically rejected wildcard");
        require(row.inputs.get(0).getAsJsonObject().get("slot").getAsInt() == 1 && row.properties.has("galacticraft:emptySlots"), "NASA fixed/empty slot requirements disappeared");
        require(row.outputs.get(0).getAsJsonObject().get("amount").getAsString().equals("3") && row.elements.get(0).getAsJsonObject().get("x").getAsInt() == 40,
                "NASA result count or native layout crop changed");
        handler.arecipes.get(0).getResult().item.stackSize = 99;
        handler.arecipes.get(0).getIngredients().get(0).item.setItemDamage(100);
        require(result.stackSize == 3 && paper.stackSize == 17 && paper.getItemDamage() == 2 && paper.getTagCompound().getInteger("ignored") == 1,
                "NASA display mutated original native records");
        input.remove(2);
        try { SpaceRecipes.capture(handler, recipe, layout, row(new ItemStack(Items.paper, 1, 2), result)); throw new AssertionError("Unconstrained usable native slot accepted"); }
        catch (Jobs.Fault expected) { require(expected.code.equals("recipe_unsupported"), "Wrong unconstrained slot failure"); }
        input.put(2, null);
        paper.setItemDamage(3);
        require(!SpaceRecipes.capture(handler, recipe, layout, row(new ItemStack(Items.paper, 1, 3), result)), "Physically impossible registered recipe accepted");
        paper.setItemDamage(2);
        try { SpaceRecipes.capture(handler, new Object(), layout, row(paper, result)); throw new AssertionError("Unknown native predicate accepted"); }
        catch (Jobs.Fault expected) { require(expected.code.equals("recipe_unsupported"), "Wrong unknown NASA predicate failure"); }
        java.lang.reflect.Method returnMethod = SpaceRecipes.class.getDeclaredMethod("containerReturn", ItemStack.class, com.google.gson.JsonObject.class, Facts.class);
        returnMethod.setAccessible(true);
        RecipeRow bucketFacts = row(new ItemStack(Items.bucket));
        com.google.gson.JsonObject bucketChoice = object("returns", new com.google.gson.JsonArray());
        returnMethod.invoke(null, new ItemStack(Items.milk_bucket), bucketChoice, bucketFacts.facts);
        require(bucketChoice.getAsJsonArray("returns").size() == 1 && bucketChoice.getAsJsonArray("returns").get(0).getAsJsonObject()
                .get("id").getAsString().equals(com.github.dcysteine.nesql.exporter.source.Identity.item("minecraft:bucket", 0, null)),
                "NASA legacy container return disappeared");
        // Verify the actual native consume-one path without invoking container-return/player code.
        inventory.setInventorySlotContents(1, new ItemStack(Items.paper, 4, 2));
        output.onPickupFromSlot(null, result.copy());
        require(inventory.getStackInSlot(1).stackSize == 3, "Native NASA consumption is not one item per occupied slot");
        System.out.println("Native NASA: 11 identities, exact/empty slots, native wildcard intersection, one-item consumption, output amount and owned views passed");
    }
    private static void nativeLayouts() throws Exception {
        Field access = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); access.setAccessible(true);
        net.minecraft.entity.player.EntityPlayer player = (net.minecraft.entity.player.EntityPlayer) ((sun.misc.Unsafe) access.get(null))
                .allocateInstance(net.minecraft.entity.player.EntityPlayerMP.class);
        List<String> names = new ArrayList<>();
        for (int tier = 1; tier <= 8; tier++) names.add("galaxyspace.core.inventory.container.rocket.ContainerSchematicTier" + tier + "Rocket");
        names.add("micdoodle8.mods.galacticraft.core.inventory.ContainerBuggyBench");
        names.add("micdoodle8.mods.galacticraft.planets.mars.inventory.ContainerSchematicCargoRocket");
        names.add("micdoodle8.mods.galacticraft.planets.asteroids.inventory.ContainerSchematicAstroMiner");
        int[] counts = {21, 26, 29, 32, 34, 39, 42, 53, 35, 21, 29};
        for (int i = 0; i < names.size(); i++) {
            net.minecraft.entity.player.InventoryPlayer inventory = new net.minecraft.entity.player.InventoryPlayer(player);
            net.minecraft.inventory.Container owned = (net.minecraft.inventory.Container) Class.forName(names.get(i))
                    .getConstructor(net.minecraft.entity.player.InventoryPlayer.class, int.class, int.class, int.class).newInstance(inventory, 0, 0, 0);
            SpaceRecipes.Layout layout = new SpaceRecipes.Layout(owned, 16);
            require(layout.slots.size() == counts[i] && layout.output != null, "Native workbench layout changed: " + names.get(i));
            require(inventory.getStackInSlot(0) == null, "Native empty constructor modified its player inventory");
        }
    }
    @SuppressWarnings("unchecked")
    private static RecipeRow row(ItemStack... stacks) {
        Facts facts = new Facts("en_US");
        Set<String> known = (Set<String>) MagicApi.field(facts, "items");
        for (ItemStack stack : stacks) known.add(com.github.dcysteine.nesql.exporter.source.Identity.item(
                net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem()), stack.getItemDamage(),
                com.github.dcysteine.nesql.exporter.source.TypedNbt.encode(stack.getTagCompound())));
        return new RecipeRow(facts, object("owner", "GalacticraftCore", "handler", "native", "key", "nasa"), "category_test", 0);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
