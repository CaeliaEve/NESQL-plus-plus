package com.github.dcysteine.nesql.exporter.capture;

import com.google.gson.JsonObject;
import cpw.mods.fml.common.*;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import java.lang.reflect.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Focused regressions from the 0.38.0 core run, using pinned native classes. */
final class NativeCoreFixesTest {
    static void run() throws Exception {
        version("gregtech", "MC1710");
        version("gregtech_nh", "5.09.51.482");
        if (System.getProperty("nesql.nativeFamily").endsWith("names")) { names(); return; }
        if (System.getProperty("nesql.nativeFamily").endsWith("tooltip")) { tooltip(); return; }
        if (System.getProperty("nesql.nativeFamily").endsWith("blast")) { blast(); return; }
        if (System.getProperty("nesql.nativeFamily").endsWith("space")) { space(); return; }
        Item fuel = item("gtPlusPlus.core.item.base.BaseItemBurnable", "fixed_fuel");
        Field meta = fuel.getClass().getDeclaredField("meta"); meta.setAccessible(true); meta.setInt(fuel, 4);
        fuel.setHasSubtypes(true);
        ItemStack input = new ItemStack(fuel, 5, 19);
        require(input.getItemDamage() == 4 && Items.feather.getDamage(input) == 19, "Native fuel must mask raw metadata");
        for (String family : new String[]{"CokeOven", "BlastFurnace"}) {
            Object manager = Class.forName("mods.railcraft.common.util.crafting." + family + "CraftingManager").newInstance();
            if (family.equals("CokeOven")) manager.getClass().getMethod("addRecipe", ItemStack.class, boolean.class, boolean.class, ItemStack.class,
                    net.minecraftforge.fluids.FluidStack.class, int.class).invoke(manager, input, true, false, new ItemStack(Items.paper), null, 20);
            else manager.getClass().getMethod("addRecipe", ItemStack.class, boolean.class, boolean.class, int.class, ItemStack.class)
                    .invoke(manager, input, true, false, 20, new ItemStack(Items.paper));
            codechicken.nei.recipe.TemplateRecipeHandler handler = (codechicken.nei.recipe.TemplateRecipeHandler)
                    Class.forName("tonius.neiintegration.mods.railcraft.RecipeHandler" + family).newInstance();
            RailRecipes adapter = new RailRecipes(handler, (List<?>) manager.getClass().getMethod("getRecipes").invoke(manager));
            RecipeRow row = row(input, new ItemStack(Items.paper));
            require(adapter.capture(0, row), "Constant-metadata fuel was rejected");
            JsonObject rule = row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("rule");
            require(rule.get("meta").getAsBoolean(), "Native constant getter must accept every raw metadata value");
            for (int raw : new int[]{0, 4, 19, 32767}) require(manager.getClass().getMethod("getRecipe", ItemStack.class)
                    .invoke(manager, new ItemStack(fuel, 1, raw)) != null, "Native matching disagrees for raw metadata " + raw);
        }
        require(input.stackSize == 5 && Items.feather.getDamage(input) == 19, "Fuel source was mutated");
        System.out.println("Core regression: Railcraft fixed-metadata GT++ fuel passed");
    }
    static void space() throws Exception {
        Item gt = item("gregtech.common.items.MetaGeneratedItem01", "gt_component");
        ItemStack input = new ItemStack(gt, 1, 32001);
        require(!gt.hasContainerItem(input), "GT component fixture has a container");
        Method method = SpaceRecipes.class.getDeclaredMethod("containerReturn", ItemStack.class, JsonObject.class, Facts.class); method.setAccessible(true);
        JsonObject choice = object("returns", array());
        method.invoke(null, input, choice, new Facts("en_US"));
        require(choice.getAsJsonArray("returns").size() == 0, "GT component gained a fictional container");
        try { ItemCallbacks.hasContainer(new ItemStack(new UnknownContainer())); throw new AssertionError("Unknown container predicate accepted"); }
        catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) { require(expected.code.equals("recipe_unsupported"),"Wrong callback error"); }
        System.out.println("Core regression: NASA GT component container predicate passed");
    }
    static void names() throws Exception {
        Method inherited = IntegrationRules.class.getDeclaredMethod("inherited", Object.class, String.class, String.class, Class[].class);
        inherited.setAccessible(true);
        for (String name : new String[]{"getHasSubtypes", "func_77614_k"}) {
            inherited.invoke(null, Items.paper, name, Item.class.getName(), new Class<?>[0]);
            try { inherited.invoke(null, new CustomSubtype(), name, Item.class.getName(), new Class<?>[0]);
                throw new AssertionError("Custom metadata predicate was accepted through " + name);
            } catch (InvocationTargetException expected) {
                require(expected.getCause() instanceof com.github.dcysteine.nesql.exporter.task.Jobs.Fault,
                        "Wrong custom predicate failure");
            }
        }
        System.out.println("Core regression: native MCP/SRG alias resolution retains override checks");
    }
    static void tooltip() throws Exception {
        version("EnderIO", "2.9.28");
        Item item = item("crazypants.enderio.teleport.telepad.ItemCoordSelector", "coord_selector");
        ItemStack original = new ItemStack(item, 3);
        try { item.addInformation(original, null, new ArrayList<String>(), true); throw new AssertionError("Missing native NBT failure"); }
        catch (NullPointerException expected) { System.out.println("Confirmed native CoordSelector null-NBT tooltip failure"); }
        Method display = Facts.class.getDeclaredMethod("tooltipStack", ItemStack.class); display.setAccessible(true);
        ItemStack projected = (ItemStack) display.invoke(null, original);
        List<String> lines = new ArrayList<>(); item.addInformation(projected, null, lines, true);
        require(lines.isEmpty() && projected.getTagCompound().getBoolean("default")
                && original.getTagCompound() == null && original.stackSize == 3, "Tooltip projection changed the exact source fact");
        original.setTagInfo("default", new net.minecraft.nbt.NBTTagByte((byte)0));
        original.setTagInfo("x", new net.minecraft.nbt.NBTTagInt(17));
        ItemStack tagged = (ItemStack) display.invoke(null, original);
        require(tagged.getTagCompound().getInteger("x") == 17 && !tagged.getTagCompound().getBoolean("default"),
                "Configured coordinates were replaced by defaults");
        require(((ItemStack)display.invoke(null,new ItemStack(Items.paper))).getTagCompound()==null,
                "An unrelated item's empty NBT was normalized");
        System.out.println("Core regression: CoordSelector display initialization preserves source NBT and configured coordinates");
    }
    static void blast() throws Exception {
        version("hodgepodge", "2.6.112"); version("IC2", "2.2.828-experimental");
        Item item = item("ic2.core.item.resources.ItemCell", "ic2_cell"); item.setHasSubtypes(true);
        ItemStack air = new ItemStack(item, 1, 5), empty = new ItemStack(item);
        Class<?> items = Class.forName("ic2.core.Ic2Items"), type = Class.forName("ic2.core.block.machine.tileentity.TileEntityBlastFurnace");
        items.getField("airCell").set(null, air); items.getField("cell").set(null, empty);
        type.getMethod("init").invoke(null);
        Object manager = Class.forName("ic2.api.recipe.Recipes").getField("blastfurance").get(null);
        Object predicate = Class.forName("ic2.api.recipe.RecipeInputItemStack").getConstructor(ItemStack.class, int.class)
                .newInstance(new ItemStack(Items.iron_ingot), 1);
        manager.getClass().getMethod("addRecipe", Class.forName("ic2.api.recipe.IRecipeInput"), net.minecraft.nbt.NBTTagCompound.class, ItemStack[].class)
                .invoke(manager, predicate, null, new ItemStack[]{new ItemStack(Items.diamond)});
        Object machine = type.newInstance(); Method work = type.getDeclaredMethod("work"); work.setAccessible(true);
        Object input = MagicApi.field(machine,"inputSlot"), airSlot = MagicApi.field(machine,"airSlot"), airOut = MagicApi.field(machine,"airOutputSlot");
        input.getClass().getMethod("put",ItemStack.class).invoke(input,new ItemStack(Items.iron_ingot));
        airSlot.getClass().getMethod("put",ItemStack.class).invoke(airSlot,new ItemStack(item,8,5));
        type.getField("progress").setInt(machine,1); work.invoke(machine);
        require(((ItemStack)airSlot.getClass().getMethod("get").invoke(airSlot)).stackSize==8
                && ((ItemStack)airOut.getClass().getMethod("get").invoke(airOut)).stackSize==1,
                "Native patched stacked air should advance without consuming, but still produce the output cell");
        airSlot.getClass().getMethod("put",ItemStack.class).invoke(airSlot,air.copy());
        type.getField("progress").setInt(machine,1000); work.invoke(machine);
        require(((ItemStack)airSlot.getClass().getMethod("get").invoke(airSlot)).getItemDamage()==0
                && ((ItemStack)airOut.getClass().getMethod("get").invoke(airOut)).stackSize==2,
                "Native patched unit air must leave a cell in each slot");
        codechicken.nei.recipe.TemplateRecipeHandler handler=(codechicken.nei.recipe.TemplateRecipeHandler)
                Class.forName("ic2.neiIntegration.core.recipehandler.BlastFurnaceRecipeHandler").newInstance();
        BlastRecipes adapter=new BlastRecipes(handler,manager,50000,air,empty);
        RecipeRow row=row(air,empty,new ItemStack(Items.iron_ingot),new ItemStack(Items.diamond)); adapter.capture(0,row);
        com.google.gson.JsonArray containers=row.record.getAsJsonObject("process").getAsJsonArray("containers");
        require(containers.size()==2 && containers.get(0).getAsJsonArray().get(0).isJsonNull()
                && containers.get(1).getAsJsonArray().get(0).getAsJsonObject().get("amount").getAsString().equals("1"),
                "Blast container-preserving behavior was flattened into fixed unit consumption");
        require(air.getItemDamage()==5 && air.stackSize==1,"Blast capture changed the configured air template");
        System.out.println("Core regression: actual Hodgepodge cell callbacks and native blast consumption passed");
    }
    public static final class CustomSubtype extends Item {
        @Override public boolean getHasSubtypes() { return true; }
    }
    public static final class UnknownContainer extends Item {
        @Override public boolean hasContainerItem(ItemStack stack) { throw new AssertionError("Unknown native callback executed"); }
    }
    static Item item(String className, String name) throws Exception {
        Field access = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); access.setAccessible(true);
        Item item = (Item) ((sun.misc.Unsafe) access.get(null)).allocateInstance(Class.forName(className));
        Field delegate = Item.class.getField("delegate"); delegate.setAccessible(true);
        delegate.set(item, new cpw.mods.fml.common.registry.RegistryDelegate.Delegate<>(item, Item.class));
        item.setMaxStackSize(64); Item.itemRegistry.addObject(31000 + Item.itemRegistry.getKeys().size() % 1000, "nesqltest:" + name, item);
        return item;
    }
    @SuppressWarnings("unchecked") static RecipeRow row(ItemStack... stacks) {
        Facts facts = new Facts("en_US");
        Set<String> known = (Set<String>) MagicApi.field(facts, "items");
        for (ItemStack stack : stacks) known.add(com.github.dcysteine.nesql.exporter.source.Identity.item(
                Item.itemRegistry.getNameForObject(stack.getItem()), Items.feather.getDamage(stack),
                com.github.dcysteine.nesql.exporter.source.TypedNbt.encode(stack.getTagCompound())));
        return new RecipeRow(facts, object("owner", "native", "handler", "core", "key", "regression"), "category_test", 0);
    }
    @SuppressWarnings("unchecked") static void version(String id, String version) {
        ModMetadata metadata = new ModMetadata(); metadata.modId = id; metadata.version = version;
        ((Map<String, ModContainer>) MagicApi.field(Loader.instance(), "namedMods")).put(id, new DummyModContainer(metadata));
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
