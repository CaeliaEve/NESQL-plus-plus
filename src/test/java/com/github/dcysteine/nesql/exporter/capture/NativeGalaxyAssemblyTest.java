package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.*;
import net.minecraft.nbt.NBTTagInt;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import java.lang.reflect.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Catches vanilla-crafting substitution, repair-branch interception, and leaked static NEI clocks. */
final class NativeGalaxyAssemblyTest {
    static void run() throws Exception {
        Class<?> adapter;
        try { adapter = Class.forName("com.github.dcysteine.nesql.exporter.capture.GalaxyAssemblyRecipes"); }
        catch (ClassNotFoundException missing) { throw new AssertionError("GalaxySpace assembly lacks its native registry/repair priority adapter", missing); }
        Class<?> registryType = Class.forName("galaxyspace.core.recipe.AssemblyRecipes");
        @SuppressWarnings("unchecked") List<IRecipe> registry = (List<IRecipe>) registryType.getMethod("getRecipeList").invoke(null);
        List<IRecipe> borrowed=new ArrayList<>(registry);
        try {
        registry.clear();
        ItemStack milk = new ItemStack(Items.milk_bucket, 23), coal = new ItemStack(Items.coal, 17), gold = new ItemStack(Items.gold_ingot, 11);
        ItemStack output = new ItemStack(Items.emerald, 70); output.setTagInfo("native", new NBTTagInt(31));
        registry.add(new ShapelessRecipes(new ItemStack(Items.carrot), Collections.singletonList(coal.copy())));
        registry.add(new ShapelessOreRecipe(new ItemStack(Items.diamond), coal.copy()));
        registry.add(new ShapedRecipes(1, 1, new ItemStack[]{coal.copy()}, new ItemStack(Items.apple)));
        ShapedRecipes fixed = new ShapedRecipes(2, 2, new ItemStack[]{milk.copy(), null, coal.copy(), gold.copy()}, output);
        Field copyNbt = ShapedRecipes.class.getDeclaredField("field_92101_f"); copyNbt.setAccessible(true); copyNbt.setBoolean(fixed, true);
        registry.add(fixed);
        TemplateRecipeHandler handler = (TemplateRecipeHandler) Class.forName("galaxyspace.core.nei.AssemblyMachineRecipeHandler").newInstance();
        TemplateRecipeHandler.CachedRecipe sentinel=handler.new CachedRecipe(){
            public PositionedStack getResult(){return null;}
            public List<PositionedStack> getIngredients(){return Collections.emptyList();}
        };handler.arecipes.add(sentinel);
        Constructor<?> ctor = adapter.getDeclaredConstructor(TemplateRecipeHandler.class, List.class, boolean.class); ctor.setAccessible(true);
        RegistryRecipes capture = (RegistryRecipes) ctor.newInstance(handler, registry, false);
        require(capture.size() == 4, "Assembly native registry entries disappeared before reachability classification");
        Facts facts = new Facts("en_US");
        @SuppressWarnings("unchecked") Set<String> known = (Set<String>) MagicApi.field(facts, "items");
        for (ItemStack stack : Arrays.asList(milk, coal, gold, output, new ItemStack(Items.carrot), new ItemStack(Items.diamond), new ItemStack(Items.apple))) known.add(id(stack));
        RecipeRow omitted = row(facts, handler, 0);
        require(!capture.capture(0, omitted), "Native-unrecognized ShapelessRecipes was falsely exported as machine gameplay");
        RecipeRow row = row(facts, handler, 3); require(capture.capture(3, row), "Reachable assembly recipe was omitted");
        JsonObject process = row.record.getAsJsonObject("process");
        require(process.get("kind").getAsString().equals("galaxyspace-assembly") && process.getAsJsonArray("earlier").size() == 2
                && process.getAsJsonArray("earlier").get(0).getAsJsonObject().get("grid").isJsonNull(), "Assembly native first-match selectors include unreachable classes or omit native shapeless priority");
        require(row.record.get("duration").isJsonNull() && row.record.get("energy").isJsonNull(), "Supply-tier timing became a fixed machine time or energy");
        require(row.inputs.size() == 3 && row.outputs.get(0).getAsJsonObject().get("amount").getAsInt() == 70
                && row.outputs.get(0).getAsJsonObject().get("id").getAsString().equals(id(output)), "Initially-empty native output lost amount or fixed NBT");
        for (JsonElement input : row.inputs) for (JsonElement raw : input.getAsJsonObject().getAsJsonArray("choices")) {
            JsonObject choice = raw.getAsJsonObject();
            require(choice.get("amount").getAsInt() == 1 && choice.getAsJsonArray("returns").size() == 0 && choice.getAsJsonObject("rule").get("nbt").getAsBoolean(),
                    "Assembly uses recipe template counts, container callbacks or input NBT");
        }
        JsonObject grid = row.record.getAsJsonObject("grid");
        require(grid.get("width").getAsInt() == 2 && grid.get("height").getAsInt() == 2 && grid.get("mirror").getAsBoolean()
                && grid.getAsJsonArray("cells").get(1).isJsonNull(), "Assembly native translated/mirrored shaped predicate changed");
        require(handler.arecipes.size()==1&&handler.arecipes.get(0)==sentinel,"Assembly semantic capture changed a borrowed native GUI cache");
        Method find = registryType.getMethod("findMatchingRecipe", IInventory.class);
        for (boolean mirror : new boolean[]{false, true}) {
            InventoryBasic inventory = new InventoryBasic("assembly-test", true, 9);
            ItemStack offeredMilk = milk.copy(); offeredMilk.setTagInfo("offered", new NBTTagInt(99));
            inventory.setInventorySlotContents(mirror ? 1 : 0, offeredMilk);
            inventory.setInventorySlotContents(mirror ? 4 : 3, coal.copy()); inventory.setInventorySlotContents(mirror ? 3 : 4, gold.copy());
            require(ItemStack.areItemStacksEqual((ItemStack) find.invoke(null, inventory), output), "Actual assembly matching or fixed-output behavior differs from captured grid/NBT");
        }
        InventoryBasic priority = new InventoryBasic("priority", true, 9); priority.setInventorySlotContents(8, coal.copy());
        require(((ItemStack) find.invoke(null, priority)).getItem() == Items.diamond, "Native first-match fixture changed");
        cpw.mods.fml.common.ModAPIManager.INSTANCE.registerDataTableAndParseAPI(new cpw.mods.fml.common.discovery.ASMDataTable());
        Class<?> tileType = Class.forName("galaxyspace.core.tile.machine.TileEntityAssemblyMachine");
        Object tile = tileType.newInstance();
        IInventory matrix = (IInventory) tileType.getField("testCraftMatrix").get(tile);
        matrix.setInventorySlotContents(0, milk.copy()); matrix.setInventorySlotContents(3, coal.copy()); matrix.setInventorySlotContents(4, gold.copy());
        tileType.getMethod("updateInput").invoke(tile);
        Method finish = tileType.getDeclaredMethod("compressIntoSlot", int.class); finish.setAccessible(true); finish.invoke(tile, 1);
        ItemStack finished = ((IInventory) tile).getStackInSlot(1);
        require(ItemStack.areItemStacksEqual(finished, output) && finished.stackSize == 70,
                "Actual initially-empty assembly output was clamped or lost fixed NBT");
        require(matrix.getStackInSlot(0).stackSize == 22 && matrix.getStackInSlot(3).stackSize == 16 && matrix.getStackInSlot(4).stackSize == 10,
                "Native assembly completion did not decrement each occupied input by one");
        capture.finishVerification(); output.stackSize = 69;
        try { capture.finishVerification(); throw new AssertionError("Assembly output drift was ignored"); }
        catch (Jobs.Fault expected) { require(expected.code.equals("recipe_changed"), "Wrong assembly drift failure"); }
        output.stackSize = 70;
        capture.finishVerification();capture.close();capture.close();fails("capture_closed",capture::size);
        production(handler,registry,Arrays.asList(milk,coal,gold,output,new ItemStack(Items.carrot),new ItemStack(Items.diamond),new ItemStack(Items.apple)));
        ItemStack left = new ItemStack(Items.iron_sword, 9, 200), right = new ItemStack(Items.iron_sword, 4, 220);
        ShapedRecipes repair = new ShapedRecipes(2, 1, new ItemStack[]{left, right}, new ItemStack(Items.apple));
        registry.clear(); registry.add(repair);
        InventoryBasic repairInputs = new InventoryBasic("repair", true, 9);
        repairInputs.setInventorySlotContents(0, new ItemStack(Items.iron_sword, 1, 200)); repairInputs.setInventorySlotContents(1, new ItemStack(Items.iron_sword, 1, 220));
        ItemStack repaired = (ItemStack) find.invoke(null, repairInputs);
        require(repaired.getItem() == Items.iron_sword && repaired.getItemDamage() == 158 && repaired.stackSize == 1, "Native repair-before-registry fixture changed");
        // A registered result is conditional on the earlier native repair branch,
        // rather than an unsupported recipe or a guaranteed fixed output.
        Facts repairFacts = NativeCoreFixesTest.row(left, right, new ItemStack(Items.apple)).facts;
        try (RegistryRecipes reader = (RegistryRecipes) ctor.newInstance(handler, registry, false)) {
            RecipeRow captured = row(repairFacts, handler, 0);
            require(reader.capture(0, captured), "Assembly repair overlap was omitted");
            JsonArray repairs = captured.record.getAsJsonObject("process").getAsJsonArray("repair");
            require(repairs != null && repairs.size() == 1, "Assembly repair priority was not preserved");
            JsonObject state = repairs.get(0).getAsJsonObject();
            require(state.get("item").getAsString().equals(id(left)) && state.get("enabled").getAsBoolean()
                    && state.get("maximum").getAsInt() == 250, "Assembly native repair state differs from its item fields");
            repairInputs.getStackInSlot(0).setTagInfo("discarded", new NBTTagInt(5));
            require(((ItemStack)find.invoke(null, repairInputs)).getTagCompound() == null, "Native repair unexpectedly inherits NBT");
            repairInputs.getStackInSlot(0).stackSize = 2;
            require(((ItemStack)find.invoke(null, repairInputs)).getItem() == Items.apple, "Native non-unit stock did not return to registered crafting");
            Field canRepair = Item.class.getDeclaredField("canRepair"); canRepair.setAccessible(true);
            boolean enabled = canRepair.getBoolean(Items.iron_sword);
            try { canRepair.setBoolean(Items.iron_sword, !enabled); fails("recipe_changed", reader::finishVerification); }
            finally { canRepair.setBoolean(Items.iron_sword, enabled); }
            reader.finishVerification();
            JsonObject origin = object("owner", "GalaxySpace", "handler", handler.getClass().getName(), "key", "native-galaxy-repair");
            captured.record.add("source", origin); captured.record.addProperty("category", Identity.origin("category", origin));
            NativeContractFixture.writeOwned("native-galaxy-repair", captured, Arrays.asList(left, right, new ItemStack(Items.apple)));
        }
        RepairProbe foreign = new RepairProbe();
        registry.set(0, new ShapedRecipes(2, 1, new ItemStack[]{new ItemStack(foreign), new ItemStack(foreign)}, new ItemStack(Items.apple)));
        try { ctor.newInstance(handler, registry, false); throw new AssertionError("Assembly accepted an unaudited repair callback"); }
        catch (InvocationTargetException expected) { require(expected.getCause() instanceof Jobs.Fault && ((Jobs.Fault) expected.getCause()).code.equals("recipe_unsupported"), "Wrong assembly repair callback guard"); }
        require(foreign.calls == 0, "Assembly executed an unaudited repair callback");
        require(milk.stackSize == 23 && coal.stackSize == 17 && output.getTagCompound().getInteger("native") == 31, "Assembly capture mutated registry stacks");
        row.finish(); java.nio.file.Files.write(java.nio.file.Paths.get("build/native-tests/galaxy-assembly-record.json"), CanonicalJson.bytes(row.record));
        System.out.println("Native Galaxy assembly: actual shaped/shapeless selection, repair-first conditional results, unit/no-container consumption, fixed NBT/output count and ownership passed");
        } finally {registry.clear();registry.addAll(borrowed);}
    }
    @SuppressWarnings("unchecked") private static void production(TemplateRecipeHandler handler,List<IRecipe> registry,List<ItemStack> known)throws Exception {
        Map<String,cpw.mods.fml.common.ModContainer> mods=(Map<String,cpw.mods.fml.common.ModContainer>)MagicApi.field(cpw.mods.fml.common.Loader.instance(),"namedMods");
        String[] ids={"GalaxySpace","GalacticraftCore"};Map<String,cpw.mods.fml.common.ModContainer> old=new HashMap<>();
        for(String id:ids)old.put(id,mods.get(id));
        Field locale=null;for(Field f:net.minecraft.client.resources.I18n.class.getDeclaredFields())if(f.getType()==net.minecraft.client.resources.Locale.class)locale=f;
        require(locale!=null,"Native locale root missing");locale.setAccessible(true);Object oldLocale=locale.get(null);
        Field hard=Class.forName("micdoodle8.mods.galacticraft.core.util.ConfigManagerCore").getField("hardMode");boolean oldHard=hard.getBoolean(null);
        List<IRecipe> owned=new ArrayList<>(registry);
        try {
            NativeCoreFixesTest.version("GalaxySpace","1.1.121-GTNH");NativeCoreFixesTest.version("GalacticraftCore","3.3.13-GTNH");
            locale.set(null,new net.minecraft.client.resources.Locale());hard.setBoolean(null,false);
            RegistrationBindings binding=RegistrationBindings.read(handler);
            require(binding.same(RegistrationBindings.read((TemplateRecipeHandler)handler.getClass().newInstance())),"Independent assembly sources differ");
            try(Recipes.Cursor cursor=new Recipes.Handler(handler,0).open(NativeCoreFixesTest.row(known.toArray(new ItemStack[0])).facts)) {
                require(MagicApi.field(cursor,"handler")==null,"Assembly production still owns a GUI projection handler");
                cursor.capture(3);cursor.finishVerification();
            }
            try(GalaxyAssemblyRecipes reader=new GalaxyAssemblyRecipes(handler)) {
                hard.setBoolean(null,true);fails("recipe_changed",reader::size);
                require(!binding.same(RegistrationBindings.read(handler)),"Assembly registration lost its energy-mode identity");hard.setBoolean(null,false);
                Collections.swap(registry,1,2);fails("recipe_changed",reader::finishVerification);Collections.swap(registry,1,2);
                ShapedRecipes selected=(ShapedRecipes)registry.get(3);ItemStack original=selected.recipeItems[0];
                try {selected.recipeItems[0]=original.copy();fails("recipe_changed",reader::finishVerification);}
                finally {selected.recipeItems[0]=original;}
                Thread.currentThread().interrupt();
                try{reader.size();throw new AssertionError("Assembly reader ignored cancellation");}
                catch(java.util.concurrent.CancellationException expected){}
                finally{Thread.interrupted();}
                reader.finishVerification();
            }
            registry.clear();
            try(GalaxyAssemblyRecipes reader=new GalaxyAssemblyRecipes(handler)){require(reader.size()==0,"Assembly ready-empty was rejected");reader.finishVerification();}
            fails("handler_not_ready",()->new GalaxyAssemblyRecipes(handler,null,false));
        } finally {
            registry.clear();registry.addAll(owned);hard.setBoolean(null,oldHard);locale.set(null,oldLocale);
            for(String id:ids)if(old.get(id)==null)mods.remove(id);else mods.put(id,old.get(id));
        }
    }
    private static void fails(String code,Runnable action){
        try{action.run();throw new AssertionError("Assembly accepted an invalid boundary: "+code);}
        catch(Jobs.Fault failure){require(failure.code.equals(code),"Wrong assembly boundary: "+failure.code);}
    }
    public static final class RepairProbe extends Item {
        int calls;
        @Override public boolean isRepairable() { calls++; return true; }
    }
    private static RecipeRow row(Facts facts, TemplateRecipeHandler handler, int order) { return new RecipeRow(facts, object("owner", "GalaxySpace", "handler", handler.getClass().getName(), "key", "assembly"), "category_test", order); }
    private static String id(ItemStack stack) { return Identity.item(Item.itemRegistry.getNameForObject(stack.getItem()), Items.feather.getDamage(stack), TypedNbt.encode(stack.getTagCompound())); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
