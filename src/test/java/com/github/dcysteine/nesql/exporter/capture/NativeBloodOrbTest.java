package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.google.gson.JsonObject;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.inventory.InventoryCrafting;
import java.lang.reflect.*;
import java.util.*;

/** Shaped native orb levels, preserved offered orb, copied result and guarded callbacks. */
public final class NativeBloodOrbTest {
    public static void main(String[] args) throws Exception { NativeGendustryFluidsTest.main(new String[]{"blood-orb"}); }
    static void run() throws Exception {
        NativeCoreFixesTest.version("AWWayofTime", "1.7.52");
        Item low = NativeCoreFixesTest.item("WayofTime.alchemicalWizardry.common.items.EnergyBattery", "orb_low");
        Item high = NativeCoreFixesTest.item("WayofTime.alchemicalWizardry.common.items.ApprenticeBloodOrb", "orb_high");
        Field level = Class.forName("WayofTime.alchemicalWizardry.common.items.EnergyBattery").getDeclaredField("orbLevel"); level.setAccessible(true);
        level.setInt(low, 1); level.setInt(high, 5);
        ItemStack orb = new ItemStack(high, 1, 7); orb.setTagInfo("ownerName", new net.minecraft.nbt.NBTTagString("unchanged"));
        require(orb.getItem().getContainerItem(orb) == orb && orb.getItem().hasContainerItem(orb), "Native orb must return offered stack including owner NBT");
        ItemStack paper = new ItemStack(Items.paper, 12, 2); paper.setTagInfo("ignored", new net.minecraft.nbt.NBTTagByte((byte)1));
        ItemStack output = new ItemStack(Items.diamond, 3); output.setTagInfo("typed", new net.minecraft.nbt.NBTTagShort((short)9));
        Class<?> nativeType = Class.forName("WayofTime.alchemicalWizardry.api.items.ShapedBloodOrbRecipe");
        IRecipe recipe = (IRecipe) nativeType.getConstructor(ItemStack.class, Object[].class).newInstance(output,
                new Object[]{new String[]{"AB", " C"}, 'A', high, 'B', paper, 'C', new ItemStack(Items.water_bucket)});
        nativeType.getMethod("setMirrored", boolean.class).invoke(recipe, false);
        InventoryCrafting inventory = new InventoryCrafting(new net.minecraft.inventory.Container() {
            public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player) { return true; }
        }, 3, 3);
        inventory.setInventorySlotContents(0, orb.copy()); inventory.setInventorySlotContents(1, new ItemStack(Items.paper, 1, 2));
        inventory.setInventorySlotContents(4, new ItemStack(Items.water_bucket));
        require(recipe.matches(inventory, null), "Native shaped recipe should ignore ordinary ingredient NBT/count");
        inventory.setInventorySlotContents(0, new ItemStack(low)); require(!recipe.matches(inventory, null), "Native insufficient orb tier accepted");
        inventory.setInventorySlotContents(0, orb.copy());
        Class<?> adapter;
        try { adapter = Class.forName("com.github.dcysteine.nesql.exporter.capture.BloodOrbShapedRecipes"); }
        catch (ClassNotFoundException missing) { throw new AssertionError("Missing native shaped blood orb adapter", missing); }
        TemplateRecipeHandler handler = (TemplateRecipeHandler) Class.forName("WayofTime.alchemicalWizardry.client.nei.NEIBloodOrbShapedHandler").newInstance();
        Method capture = adapter.getDeclaredMethod("capture", IRecipe.class, TemplateRecipeHandler.class, List.class, RecipeRow.class); capture.setAccessible(true);
        RecipeRow row = NativeCoreFixesTest.row(new ItemStack(high), new ItemStack(low), new ItemStack(Items.paper,1,2), new ItemStack(Items.water_bucket), new ItemStack(Items.bucket), output);
        require((Boolean)capture.invoke(null, recipe, handler, Arrays.asList(low, high), row), "Valid shaped orb recipe was excluded");
        require(row.inputs.size() == 3 && row.outputs.size() == 1, "Shaped orb cells or output lost");
        JsonObject choice = row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
        require(row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").size() == 1
                && choice.getAsJsonObject("consume").get("kind").getAsString().equals("keep")
                && choice.getAsJsonObject("rule").get("meta").getAsBoolean() && choice.getAsJsonObject("rule").get("nbt").getAsBoolean(),
                "Orb tier alternatives or offered-stack preservation changed");
        require(row.inputs.get(2).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonArray("returns").size() == 1,
                "Native water bucket return was lost");
        require(!row.record.getAsJsonObject("grid").get("mirror").getAsBoolean()
                && row.record.getAsJsonObject("grid").getAsJsonArray("cells").get(2).isJsonNull(), "Grid hole/mirroring changed");
        require(row.outputs.get(0).getAsJsonObject().get("amount").getAsString().equals("3"), "Output quantity changed");
        handler.arecipes.get(0).getResult().items[0].stackSize = 47;
        require(recipe.getRecipeOutput().stackSize == 3 && paper.stackSize == 12 && orb.getTagCompound().getString("ownerName").equals("unchanged"), "Native projection changed registry or offered NBT");
        row.finish();
        java.nio.file.Path file = java.nio.file.Files.createTempFile("blood-orb-facts-", ".json");
        try {
            java.nio.file.Files.write(file, CanonicalJson.bytes(row.record));
            require(new com.google.gson.JsonParser().parse(new String(java.nio.file.Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("inputs").size() == 3,
                    "Serialized shaped orb facts lost inputs");
        } finally { java.nio.file.Files.delete(file); }
        Item custom = new Item() { @Override public boolean hasContainerItem(ItemStack stack) { throw new AssertionError("Unknown container callback executed"); } };
        Item.itemRegistry.addObject(31888, "nesqltest:unknown_orb_companion", custom);
        IRecipe unsafe = (IRecipe)nativeType.getConstructor(ItemStack.class,Object[].class).newInstance(output,new Object[]{"A",'A',custom});
        try { capture.invoke(null,unsafe,handler,Arrays.asList(low,high),NativeCoreFixesTest.row(new ItemStack(custom),output)); throw new AssertionError("Unknown container predicate accepted"); }
        catch (InvocationTargetException expected) { require(expected.getCause() instanceof com.github.dcysteine.nesql.exporter.task.Jobs.Fault, "Wrong callback rejection"); }
        NativeCoreFixesTest.version("gregtech_nh","5.09.51.482");
        Item gt=NativeCoreFixesTest.item("gregtech.common.items.MetaGeneratedItem01","orb_gt_component");
        ItemStack component=new ItemStack(gt,1,32001);
        require(!gt.hasContainerItem(component),"Native GT component fixture unexpectedly has a container");
        IRecipe gtRecipe=(IRecipe)nativeType.getConstructor(ItemStack.class,Object[].class).newInstance(output,new Object[]{"A",'A',component});
        RecipeRow gtRow=NativeCoreFixesTest.row(component,output);
        require((Boolean)capture.invoke(null,gtRecipe,handler,Arrays.asList(low,high),gtRow)
                &&gtRow.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonArray("returns").size()==0,
                "Audited GT companion was rejected or gained a fictional container");
        net.minecraftforge.oredict.OreDictionary.registerOre("nesqltestShapedOrbOre",new ItemStack(Items.paper,1,2));
        IRecipe oreRecipe=(IRecipe)nativeType.getConstructor(ItemStack.class,Object[].class).newInstance(output,new Object[]{"A",'A',"nesqltestShapedOrbOre"});
        InventoryCrafting oreGrid=new InventoryCrafting(new net.minecraft.inventory.Container(){public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player){return true;}},3,3);
        oreGrid.setInventorySlotContents(0,new ItemStack(Items.paper,1,2));
        require(oreRecipe.matches(oreGrid,null),"Native real OreDictionary wrapper failed matching");
        require((Boolean)capture.invoke(null,oreRecipe,handler,Arrays.asList(low,high),NativeCoreFixesTest.row(new ItemStack(Items.paper,1,2),output)),"Native OreDictionary wrapper was rejected");
        List registry=net.minecraft.item.crafting.CraftingManager.getInstance().getRecipeList(); registry.add(recipe);
        try {
            Constructor<?> constructor=adapter.getDeclaredConstructor(TemplateRecipeHandler.class);constructor.setAccessible(true);
            RegistryRecipes cursor=(RegistryRecipes)constructor.newInstance(handler);
            require(cursor.size()==1,"Native CraftingManager discovery lost shaped orb recipe");
            cursor.verify();
            require(cursor.capture(0,NativeCoreFixesTest.row(new ItemStack(high),new ItemStack(low),new ItemStack(Items.paper,1,2),new ItemStack(Items.water_bucket),new ItemStack(Items.bucket),output)),"Registered native orb capture failed");
            level.setInt(high,6);
            try { cursor.verify();throw new AssertionError("Changed native orb level was accepted"); }
            catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected){require(expected.code.equals("environment_changed"),"Wrong orb registry drift failure");}
        } finally { registry.remove(recipe);level.setInt(high,5); }
        System.out.println("Native shaped Blood Orb: tier predicates, ignored ingredient NBT/count, preserved orb NBT/meta, bucket return, grid/mirror, exact output, owned native view, durable JSON, full fixture registries and orb drift passed");
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
