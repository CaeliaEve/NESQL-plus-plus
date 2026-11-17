package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.ShapedRecipeHandler;
import codechicken.nei.recipe.ShapelessRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.*;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Native matching on owned inventories; no NEI loader, coordinates or game world. */
final class NativeCraftingTest {
    static void run() throws Exception {
        contentPermutations();
        ItemStack paper = new ItemStack(Items.paper, 17), gold = new ItemStack(Items.gold_ingot, 3);
        ShapedRecipes shaped = new ShapedRecipes(2, 2, new ItemStack[] {paper, null, null, paper.copy()}, gold);
        ShapedOreRecipe forge = new ShapedOreRecipe(new ItemStack(Items.diamond), false, "px", 'p', Items.paper, 'x', Items.gold_ingot);
        ShapelessRecipes unordered = new ShapelessRecipes(new ItemStack(Items.iron_ingot, 2), Arrays.asList(paper, paper.copy()));
        ShapelessOreRecipe oreUnordered = new ShapelessOreRecipe(new ItemStack(Items.apple), Items.paper, Items.gold_ingot);
        ShapedOreRecipe empty = new ShapedOreRecipe(new ItemStack(Items.paper), "x", 'x', "nesqlNativeCraftingEmptyOre");
        List<IRecipe> source = new ArrayList<>(Arrays.asList(shaped, unordered, forge, oreUnordered, empty));
        RegistryRecipes reader = reader(source, true);
        require(reader.size() == 3, "Shaped registry order/filter lost native definitions");
        RecipeRow row = row(true, paper, gold);
        require(reader.capture(0, row), "Native shaped definition disappeared");
        require(row.record.getAsJsonObject("grid").getAsJsonArray("cells").toString().equals("[0,null,null,1]")
                && row.record.getAsJsonObject("grid").get("mirror").getAsBoolean(), "Native grid, gaps or mirror lost");
        require(row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().get("amount").getAsString().equals("1")
                && row.outputs.get(0).getAsJsonObject().get("amount").getAsString().equals("3"), "Native crafting consumed display amounts or lost output count");
        require(shaped.matches(inventory(3, 3, new ItemStack(Items.paper), null, null, null, new ItemStack(Items.paper)), null), "Actual translated vanilla grid changed");
        NativeContractFixture.writeOwned("native-crafting-shaped", row, Arrays.asList(paper, gold));
        RecipeRow forgeRow = row(true, paper, new ItemStack(Items.gold_ingot), new ItemStack(Items.diamond));
        require(reader.capture(1, forgeRow) && !forgeRow.record.getAsJsonObject("grid").get("mirror").getAsBoolean(), "Forge disabled mirroring was lost");
        require(!forge.matches(inventory(3, 3, new ItemStack(Items.gold_ingot), new ItemStack(Items.paper)), null)
                && forge.matches(inventory(3, 3, new ItemStack(Items.paper), new ItemStack(Items.gold_ingot)), null), "Actual Forge mirror predicate changed");
        NativeContractFixture.writeOwned("native-crafting-forge", forgeRow, Arrays.asList(paper, new ItemStack(Items.gold_ingot), new ItemStack(Items.diamond)));
        RecipeRow emptyRow = row(true, paper);
        require(!reader.capture(2, emptyRow) && emptyRow.exclusion != null, "Empty native ore predicate was dropped or marked usable");
        reader.finishVerification();
        gold.stackSize = 4; changed(() -> reader.capture(0, row(true, paper, gold))); gold.stackSize = 3;
        shaped.recipeItems[1] = new ItemStack(Items.paper); changed(reader::finishVerification); shaped.recipeItems[1] = null;
        Collections.swap(source, 0, 1); changed(reader::finishVerification); Collections.swap(source, 0, 1);
        reader.close(); reader.close();
        try { reader.size(); throw new AssertionError("Closed crafting reader exposed rows"); }
        catch (Jobs.Fault failure) { require(failure.code.equals("capture_closed"), failure.toString()); }
        RegistryRecipes shapeless = reader(source, false);
        RecipeRow unorderedRow = row(false, paper, new ItemStack(Items.iron_ingot, 2));
        require(shapeless.size() == 2 && shapeless.capture(0, unorderedRow) && unorderedRow.inputs.size() == 2
                && unorderedRow.record.get("grid").isJsonNull(), "Shapeless multiplicity became a grid");
        require(unordered.matches(inventory(3, 3, new ItemStack(Items.paper), new ItemStack(Items.paper)), null), "Native unordered multiplicity changed");
        NativeContractFixture.writeOwned("native-crafting-shapeless", unorderedRow, Arrays.asList(paper, new ItemStack(Items.iron_ingot, 2)));
        shapeless.close();
        RegistryRecipes readyEmpty = reader(new ArrayList<>(), true);
        require(readyEmpty.size() == 0, "Ready empty crafting registry failed"); readyEmpty.finishVerification(); readyEmpty.close();
        try { reader(null, true); throw new AssertionError("Unavailable crafting registry became ready empty"); }
        catch (Jobs.Fault failure) { require(failure.code.equals("handler_not_ready"), failure.toString()); }
        ShapedRecipes override = new ShapedRecipes(1, 1, new ItemStack[]{paper}, gold) {
            @Override public ItemStack getCraftingResult(net.minecraft.inventory.InventoryCrafting grid) { return new ItemStack(Items.diamond); }
        };
        try { reader(Collections.singletonList(override), true); throw new AssertionError("Unknown result override was flattened to parent fields"); }
        catch (Jobs.Fault failure) { require(failure.code.equals("recipe_unsupported"), failure.toString()); }
        int[] listCalls={0};ArrayList<ItemStack> foreign=new ArrayList<ItemStack>(){
            @Override public Object[] toArray(){listCalls[0]++;throw new AssertionError("Unknown source list executed");}
        };
        foreign.add(paper);
        try{reader(Collections.singletonList(new ShapelessRecipes(gold,foreign)),false);throw new AssertionError("Unknown native list owner was admitted");}
        catch(Jobs.Fault failure){require(failure.code.equals("recipe_unsupported")&&listCalls[0]==0,"Foreign source list ran before admission");}
        RecipesMapExtending map = new RecipesMapExtending();
        ItemStack center = new ItemStack(Items.filled_map, 17, 37);
        center.setTagInfo("owner", new net.minecraft.nbt.NBTTagString("retained"));
        List<ItemStack> savedMaps = new ArrayList<>(codechicken.nei.ItemList.itemMap.get(Items.filled_map));
        try {
            codechicken.nei.ItemList.itemMap.replaceValues(Items.filled_map, Collections.singletonList(center));
            RegistryRecipes maps = reader(Collections.singletonList(map), true);
            ItemStack pending = Crafting.pending(map, center);
            RecipeRow mapRow = row(true, center, pending, new ItemStack(Items.paper));
            require(maps.capture(0, mapRow) && mapRow.inputs.size() == 9
                    && mapRow.record.getAsJsonObject("process").get("kind").getAsString().equals("mapScaling"), "Raw map lost pending semantics");
            require(center.stackSize == 17 && !center.getTagCompound().hasKey("map_is_scaling")
                    && pending.getItemDamage() == 37 && pending.getTagCompound().getString("owner").equals("retained"), "Native pending map lost owned data");
            NativeContractFixture.writeOwned("native-crafting-map", mapRow, Arrays.asList(center, pending, new ItemStack(Items.paper)));
            maps.finishVerification(); maps.close();
        } finally { codechicken.nei.ItemList.itemMap.replaceValues(Items.filled_map, savedMaps); }
        ItemStack baseNbt = new ItemStack(Items.gold_ingot, 3, 2);
        baseNbt.setTagInfo("base", new net.minecraft.nbt.NBTTagString("kept-only-without-tagged-input"));
        ItemStack tagged = paper.copy(); tagged.stackSize = 1;
        tagged.setTagInfo("owner", new net.minecraft.nbt.NBTTagString("native-transfer"));
        ItemStack last = new ItemStack(Items.diamond);
        last.setTagCompound(new net.minecraft.nbt.NBTTagCompound()); // Empty compound still counts as tagged.
        ShapedRecipes copyNbt = new ShapedRecipes(2, 1, new ItemStack[]{tagged, last}, baseNbt).func_92100_c();
        ItemStack normal = copyNbt.getCraftingResult(inventory(3, 3, tagged, last));
        ItemStack mirrored = copyNbt.getCraftingResult(inventory(3, 3, last, tagged));
        ItemStack noTags = copyNbt.getCraftingResult(inventory(3, 3, paper, new ItemStack(Items.diamond)));
        require(normal.hasTagCompound() && normal.getTagCompound().hasNoTags()
                && mirrored.getTagCompound().getString("owner").equals("native-transfer")
                && ItemStack.areItemStackTagsEqual(noTags, baseNbt), "Native LAST-tagged/empty/untagged result semantics changed");
        List<ItemStack> nbtSpecimens = Arrays.asList(tagged, last, baseNbt, normal, mirrored, paper);
        RegistryRecipes nbtReader = reader(Collections.singletonList(copyNbt), true);
        RecipeRow nbtRow = row(true, nbtSpecimens.toArray(new ItemStack[0]));
        require(nbtReader.capture(0, nbtRow)
                && nbtRow.record.getAsJsonObject("process").get("kind").getAsString().equals("craftingNbt")
                && nbtRow.outputs.get(0).getAsJsonObject().getAsJsonObject("change").getAsJsonArray("samples").size() == 3,
                "Last-tagged native result lacks independent input projections");
        require(tagged.stackSize == 1 && tagged.getTagCompound().getString("owner").equals("native-transfer")
                && baseNbt.getTagCompound().hasKey("base") && !baseNbt.getTagCompound().hasKey("owner"), "NBT observation mutated borrowed inputs/base");
        NativeContractFixture.writeOwned("native-crafting-nbt", nbtRow, nbtSpecimens);
        nbtReader.finishVerification(); nbtReader.close();
        RegistryRecipes duplicates = reader(Arrays.asList(shaped, shaped), true);
        require(duplicates.size() == 2 && duplicates.capture(1, row(true, paper, gold)), "Repeated native instance lost its registry position");
        duplicates.finishVerification(); duplicates.close();
        List<ItemStack> savedPaper = new ArrayList<>(codechicken.nei.ItemList.itemMap.get(Items.paper));
        try {
            codechicken.nei.ItemList.itemMap.replaceValues(Items.paper, Collections.singletonList(new ItemStack(Items.paper)));
            RegistryRecipes metadata = reader(Collections.singletonList(new ShapedRecipes(1, 1,
                    new ItemStack[]{new ItemStack(Items.paper, 1, 32767)}, gold)), true);
            codechicken.nei.ItemList.itemMap.replaceValues(Items.paper, Collections.singletonList(new ItemStack(Items.paper, 1, 1)));
            changed(metadata::finishVerification); metadata.close();
        } finally { codechicken.nei.ItemList.itemMap.replaceValues(Items.paper, savedPaper); }
        System.out.println("Native crafting: raw grid/gaps/mirror, multiplicity, consume-one, ready/empty, unknown callbacks, drift and close passed");
    }

    private static void contentPermutations()throws Exception{
        try(NativeRegistryScope scope=new NativeRegistryScope()){
            scope.replace(codechicken.nei.ItemList.class,null,"loadFinished",true);
            scope.replace(codechicken.nei.ItemList.class,null,"itemMap",com.google.common.collect.ArrayListMultimap.create());
            ItemStack wildcard=new ItemStack(Items.paper,3,32767);wildcard.setTagInfo("template",new net.minecraft.nbt.NBTTagInt(7));
            try(NativeItemExamples examples=new NativeItemExamples()){
                ItemStack sample=examples.permutations(wildcard)[0];
                require(sample.getItemDamage()==0&&sample.stackSize==3&&ItemStack.areItemStackTagsEqual(sample,wildcard),"Native ready-empty permutation lost template NBT");
                require(sample.getTagCompound()!=wildcard.getTagCompound(),"Owned fallback retained a borrowed tag alias");examples.verify();
            }
            ItemStack negative=new ItemStack(Items.paper,9,0);negative.setTagInfo("variant",new net.minecraft.nbt.NBTTagInt(8));
            Field damage=ItemStack.class.getDeclaredField("itemDamage");damage.setAccessible(true);damage.setInt(negative,-2);
            codechicken.nei.ItemList.itemMap.put(Items.paper,negative);
            try(NativeItemExamples examples=new NativeItemExamples()){
                ItemStack sample=examples.permutations(wildcard)[0];
                require(sample.getItemDamage()==0&&negative.getItemDamage()==-2&&sample.stackSize==3&&ItemStack.areItemStackTagsEqual(sample,negative),"Native copy clamping, source ownership or variant NBT changed");examples.verify();
            }
        }
    }
    static RegistryRecipes reader(List<? extends IRecipe> source,boolean shaped)throws Exception{return new Registered(source,shaped);}
    /** Install an isolated real CraftingManager source; production has no collection-injection constructor. */
    private static final class Registered implements RegistryRecipes{
        private final NativeRegistryScope scope=new NativeRegistryScope();
        private final NativeCraftingRecipes reader;private boolean closed;
        Registered(List<? extends IRecipe> source,boolean shaped)throws Exception{
            try{
                scope.replace(CraftingManager.class,CraftingManager.getInstance(),"recipes",
                    source==null?null:source.getClass()==ArrayList.class?source:new ArrayList<>(source));
                scope.replace(codechicken.nei.ItemList.class,null,"loadFinished",true);
                scope.replace(codechicken.nei.ItemList.class,null,"itemMap",codechicken.nei.ItemList.itemMap==null
                    ?com.google.common.collect.ArrayListMultimap.create():com.google.common.collect.ArrayListMultimap.create(codechicken.nei.ItemList.itemMap));
                reader=new NativeCraftingRecipes(shaped);
            }catch(Exception|Error error){try{scope.close();}catch(Exception release){error.addSuppressed(release);}throw error;}
        }
        public int size(){return reader.size();}
        public boolean capture(int index,RecipeRow row){return reader.capture(index,row);}
        public List<RecipeRow> captureRows(int index,RecipeRow row){return reader.captureRows(index,row);}
        public String program(Facts facts){return reader.program(facts);}
        public JsonObject exclusion(int index){return reader.exclusion(index);}
        public JsonObject handlerExclusion(){return reader.handlerExclusion();}
        public void verify(){reader.verify();}
        public void finishVerification(){reader.finishVerification();}
        public void close(){if(closed)return;closed=true;try{reader.close();}finally{try{scope.close();}catch(Exception error){throw new AssertionError(error);}}}
    }
    static net.minecraft.inventory.InventoryCrafting inventory(int width, int height, ItemStack... inputs) {
        net.minecraft.inventory.InventoryCrafting grid = new net.minecraft.inventory.InventoryCrafting(new net.minecraft.inventory.Container() {
            @Override public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player) { return false; }
        }, width, height);
        for (int i = 0; i < inputs.length; i++) if (inputs[i] != null) grid.setInventorySlotContents(i, inputs[i].copy());
        return grid;
    }
    @SuppressWarnings("unchecked") static RecipeRow row(boolean shaped, ItemStack... items) {
        Facts facts = new Facts("en_US"); Set<String> known = (Set<String>)MagicApi.field(facts, "items");
        for (ItemStack item : items) known.add(Identity.item(Item.itemRegistry.getNameForObject(item.getItem()), Items.feather.getDamage(item), TypedNbt.encode(item.getTagCompound())));
        String handler = shaped ? ShapedRecipeHandler.class.getName() : ShapelessRecipeHandler.class.getName();
        JsonObject origin = object("owner", "NEI", "handler", handler, "key", handler);
        return new RecipeRow(facts, origin, Identity.origin("category", origin), 0);
    }
    private static void changed(Runnable operation) {
        try { operation.run(); throw new AssertionError("Crafting native definition/order drift ignored"); }
        catch (Jobs.Fault failure) { require(failure.code.equals("recipe_changed"), failure.toString()); }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
