package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import java.util.*;
import java.lang.reflect.Method;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Runs actual Railcraft managers in the existing isolated native bootstrap. */
final class NativeRailTest {
    static void run() throws Exception {
        String prefix = "tonius.neiintegration.mods.railcraft.RecipeHandler";
        for (String name : new String[] {"RollingMachineShaped", "RollingMachineShapeless"})
            require(Recipes.adapter((TemplateRecipeHandler) Class.forName(prefix + name).newInstance()) != null,
                    "Missing native rolling machine adapter: " + name);
        NativeRollingTest.run();
        unknownMetadata();
        disabledRock();
        for (String name : new String[] {"CokeOven", "BlastFurnace"}) {
            TemplateRecipeHandler handler = (TemplateRecipeHandler) Class.forName(prefix + name).newInstance();
            require(Recipes.adapter(handler) != null, "Missing native Railcraft adapter: " + name);
            verify(handler, name.equals("CokeOven"));
        }
        System.out.println("Native Railcraft: two-pass priority, subtype/wildcard metadata, exact/ignored NBT, synthetic presence, unit input, fluids, timing and owned projections passed");
    }
    private static void unknownMetadata()throws Exception{
        TemplateRecipeHandler handler=(TemplateRecipeHandler)Class.forName("tonius.neiintegration.mods.railcraft.RecipeHandlerCokeOven").newInstance();
        UnknownMetadata item=new UnknownMetadata();ItemStack input=new ItemStack(item);
        Object recipe=Class.forName("mods.railcraft.common.util.crafting.CokeOvenCraftingManager$CokeOvenRecipe")
            .getConstructor(ItemStack.class,boolean.class,boolean.class,ItemStack.class,FluidStack.class,int.class)
            .newInstance(input,true,false,new ItemStack(Items.paper),null,50);
        item.armed=true;
        try{new RailRecipes(handler,Collections.singletonList(recipe));throw new AssertionError("Unreviewed Rail metadata getter accepted");}
        catch(Jobs.Fault failure){require(failure.code.equals("recipe_unsupported"),failure.toString());}
        require(item.calls==0,"Unreviewed Rail metadata getter was invoked before audit");
    }
    public static final class UnknownMetadata extends Item{
        boolean armed;int calls;
        @Override public int getDamage(ItemStack stack){if(armed){calls++;throw new AssertionError("Unknown Rail metadata callback executed");}return super.getDamage(stack);}
    }
    private static void disabledRock() throws Exception {
        java.lang.reflect.Field flag = Class.forName("mods.railcraft.common.util.misc.Game").getField("isGTNH");
        boolean previous = flag.getBoolean(null);
        try (NativeRegistryScope scope = new NativeRegistryScope()) {
            NativeCoreFixesTest.version("Railcraft", "9.16.33");
            NativeCoreFixesTest.version("neiintegration", "1.5.0");
            TemplateRecipeHandler handler = (TemplateRecipeHandler) Class.forName("tonius.neiintegration.mods.railcraft.RecipeHandlerRockCrusher").newInstance();
            flag.setBoolean(null, false);
            require(Recipes.adapter(handler) == null, "Enabled rock crusher was claimed without an active-machine adapter");
            flag.setBoolean(null, true);
            require(Recipes.adapter(handler) != null, "Native GTNH-disabled rock crusher has no evidence route");
            // Getters would be side effects; the native machine is disabled before any recipe access.
            Object nativeRecipe = java.lang.reflect.Proxy.newProxyInstance(handler.getClass().getClassLoader(),
                    new Class<?>[] {Class.forName("mods.railcraft.api.crafting.IRockCrusherRecipe")},
                    (proxy, method, args) -> { throw new AssertionError("Disabled recipe method executed: " + method); });
            Class<?> managerType=Class.forName("mods.railcraft.common.util.crafting.RockCrusherCraftingManager");
            Object manager=managerType.newInstance();
            ArrayList<Object> sourceRecipes=new ArrayList<>(Arrays.asList(null,nativeRecipe));
            scope.replace(managerType,manager,"recipes",sourceRecipes);
            scope.replace(Class.forName("mods.railcraft.api.crafting.RailcraftCraftingManager"),null,"rockCrusher",manager);
            DisabledRecipes registry = new DisabledRecipes(handler);
            require(registry.size() == 1, "Disabled NEI enumeration changed null omission");
            registry.finishVerification();
            sourceRecipes.set(1,null);
            try {registry.finishVerification();throw new AssertionError("Disabled source membership drift was missed");}
            catch(Jobs.Fault expected){require(expected.code.equals("environment_changed"),"Wrong disabled source drift failure");}
            sourceRecipes.set(1,nativeRecipe);
            Recipes.Handler source = new Recipes.Handler(handler, 0);
            Facts facts = new Facts("en_US");
            try (Recipes.Cursor cursor = new Recipes.Cursor(source, facts, registry, null)) {
                cursor.capture(0);
                require(facts.drain().records.isEmpty(), "Disabled machine emitted recipe facts");
                JsonObject proof = cursor.exclusions().getAsJsonObject("0");
                require(proof.get("reason").getAsString().equals("native_machine_disabled") && proof.get("value").getAsBoolean(),
                        "Disabled-machine evidence was lost at the diagnostic/export cursor");
                proof.addProperty("value",false);
                require(cursor.exclusions().getAsJsonObject("0").get("value").getAsBoolean(), "Caller mutated stored exclusion evidence");
                flag.setBoolean(null,false);
                try { cursor.capture(0); throw new AssertionError("Changed native flag silently retained a stale exclusion"); }
                catch (Jobs.Fault expected) { require(expected.code.equals("environment_changed"), "Native guard drift must stop the job"); }
            }
        } finally { flag.setBoolean(null, previous); }
    }
    @SuppressWarnings("unchecked")
    private static void verify(TemplateRecipeHandler handler, boolean coke) throws Exception {
        Class<?> managerType = Class.forName("mods.railcraft.common.util.crafting." + (coke ? "CokeOven" : "BlastFurnace") + "CraftingManager");
        Object manager = managerType.newInstance();
        ItemStack broad = new ItemStack(Items.coal, 17, 0), exact = new ItemStack(Items.coal, 7, 1);
        broad.setTagInfo("synthetic", new net.minecraft.nbt.NBTTagByte((byte) 0)); // ignored by the manager, rejected by the machine only on offered stacks
        ItemStack tagged = exact.copy(); tagged.setTagInfo("owner", new net.minecraft.nbt.NBTTagInt(1));
        ItemStack durable = new ItemStack(Items.iron_pickaxe, 6, 20);
        ItemStack zero = new ItemStack(Items.coal, 5, 2);
        ItemStack paper = new ItemStack(Items.paper, 2), book = new ItemStack(Items.book, 3), stick = new ItemStack(Items.stick, 4);
        add(manager, coke, broad, false, false, 51, paper);
        add(manager, coke, exact, true, true, 25, book);
        add(manager, coke, tagged, true, true, 25, stick);
        add(manager, coke, exact, true, true, 25, new ItemStack(Items.apple)); // unreachable duplicate
        add(manager, coke, durable, true, false, 25, stick);
        add(manager, coke, zero, true, false, 0, paper);
        List<?> recipes = (List<?>) managerType.getMethod("getRecipes").invoke(manager);
        TemplateRecipeHandler.CachedRecipe borrowed=handler.new CachedRecipe(){public codechicken.nei.PositionedStack getResult(){return null;}};
        handler.arecipes.add(borrowed);
        RailRecipes adapter = new RailRecipes(handler, recipes);
        Map<String, ItemStack> known = new HashMap<>();
        for (Item item : new Item[] {Items.coal, Items.iron_pickaxe}) for (int meta : new int[] {-1,0,1,2,5,20,21,32767}) {
            for (int tag = 0; tag < 4; tag++) {
                ItemStack stack = new ItemStack(item, 1, Math.max(0,meta));rawMetadata(stack,meta);
                if (tag > 0) stack.setTagCompound(new NBTTagCompound());
                if (tag == 2) stack.getTagCompound().setInteger("owner", 1);
                if (tag == 3) stack.getTagCompound().setBoolean("synthetic", false);
                known.put(id(stack), stack);
            }
        }
        for (ItemStack stack : new ItemStack[] {paper,book,stick,new ItemStack(Items.apple)}) known.put(id(stack), stack);
        List<RecipeRow> rows = new ArrayList<>();
        for (int i = 0; i < adapter.size(); i++) {
            Facts facts = new Facts("en_US"); ((Set<String>) MagicApi.field(facts, "items")).addAll(known.keySet());
            RecipeRow row = new RecipeRow(facts, object("owner","Railcraft","handler","native","key","furnace"), "category_test", i);
            if (!adapter.capture(i, row)) continue;
            require(handler.arecipes.size()==1&&handler.arecipes.get(0)==borrowed,"Rail semantic reader rewrote borrowed GUI cache");
            rows.add(row);
            require(row.inputs.size() == 1, "Fuel was made into a fixed recipe ingredient");
            for (JsonElement choice : row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices"))
                require(choice.getAsJsonObject().get("amount").getAsString().equals("1") && choice.getAsJsonObject().getAsJsonArray("returns").size() == 0,
                        "Native input count/absence of container returns changed");
            require(row.outputs.size() == (coke ? 2 : 1), "Native fluid output lost");
            if (row.properties.getAsJsonObject("railcraft:registeredTime").getAsJsonObject("value").get("value").getAsString().equals("51"))
                require(row.record.get("duration").getAsString().equals(coke ? "100" : "51"), "Native pulse quantization changed");
            if (!coke) require(row.properties.has("railcraft:fuel"), "Blast furnace lost independent fuel requirement");
        }
        require(rows.size() == 5, "Shadowed native entry was retained or an unshadowed recipe was dropped");
        Method lookup = managerType.getMethod("getRecipe", ItemStack.class);
        for (ItemStack stack : known.values()) {
            if (stack.getItem() != Items.coal && stack.getItem() != Items.iron_pickaxe) continue;
            for (int count : new int[] {1,23}) {
                ItemStack offered = stack.copy(); offered.stackSize = count;
                rawMetadata(offered,Items.feather.getDamage(stack));
                Object winner = lookup.invoke(manager, offered);
                if (coke && offered.hasTagCompound() && offered.getTagCompound().hasKey("synthetic")) winner = null;
                String result = winner == null ? null : id((ItemStack) winner.getClass().getMethod("getOutput").invoke(winner));
                List<String> matches = new ArrayList<>();
                for (RecipeRow row : rows) {
                    boolean accepts = false;
                    for (JsonElement raw : row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices")) {
                        JsonObject choice = raw.getAsJsonObject();
                        accepts |= matches(choice.getAsJsonObject("rule"), known.get(choice.get("id").getAsString()), offered, known);
                    }
                    if (accepts) matches.add(row.outputs.get(0).getAsJsonObject().get("id").getAsString());
                }
                require(result == null ? matches.isEmpty() : matches.equals(Collections.singletonList(result)),
                        "Native priority differs: " + offered + "; matches=" + matches + "; expected=" + result);
            }
        }
        require(broad.stackSize == 17 && broad.getTagCompound().hasKey("synthetic") && exact.stackSize == 7 && tagged.getTagCompound().getInteger("owner") == 1 && paper.stackSize == 2,
                "Adapter mutated registry inputs or outputs");
        lifecycle(handler,manager,recipes,adapter,coke,known);
        actualSource(handler,manager,coke,known);
        try { new RailRecipes(handler, Collections.singletonList(new Object())); throw new AssertionError("Unknown override was accepted"); }
        catch (Jobs.Fault expected) { require(expected.code.equals("recipe_unsupported"), "Wrong unknown implementation failure"); }
        if (coke) {
            Object overflow = managerType.newInstance();
            add(overflow, true, exact, true, true, Integer.MAX_VALUE, paper);
            RailRecipes invalid = new RailRecipes(handler, (List<?>) managerType.getMethod("getRecipes").invoke(overflow));
            try { invalid.capture(0, new RecipeRow(new Facts("en_US"), object("owner","Railcraft","handler","native","key","overflow"), "category_test",0));
                throw new AssertionError("Native counter wraparound was flattened to a linear duration"); }
            catch (Jobs.Fault expected) { require(expected.code.equals("recipe_unsupported"), "Wrong counter wraparound failure"); }
        }
    }
    @SuppressWarnings("unchecked") private static void lifecycle(TemplateRecipeHandler handler,Object manager,List<?> recipes,RailRecipes reader,boolean coke,Map<String,ItemStack> known)throws Exception{
        reader.finishVerification();ItemStack raw=(ItemStack)MagicApi.field(recipes.get(0),"input");int amount=raw.stackSize;
        try{raw.stackSize++;fails(reader::finishVerification,"recipe_changed");}finally{raw.stackSize=amount;}
        List<Object> list=(List<Object>)recipes;Object first=list.get(0),last=list.get(list.size()-1);
        try{Collections.swap(list,0,list.size()-1);fails(reader::finishVerification,"recipe_changed");}finally{list.set(0,first);list.set(list.size()-1,last);}
        reader.finishVerification();reader.close();reader.close();fails(reader::size,"capture_closed");
        fails(()->reader.capture(0,knownRow(known)),"capture_closed");
        require(list.size()==6&&handler.arecipes.size()==1,"Rail reader close changed borrowed registry or GUI cache");
        try(RailRecipes empty=new RailRecipes(handler,Collections.emptyList())){require(empty.size()==0,"Rail ready-empty list failed");empty.finishVerification();}
        fails(()->new RailRecipes(handler,(List<?>)null),"handler_not_ready");
    }
    private static void actualSource(TemplateRecipeHandler handler,Object manager,boolean coke,Map<String,ItemStack> known)throws Exception{
        java.lang.reflect.Field api=Class.forName("mods.railcraft.api.crafting.RailcraftCraftingManager").getField(coke?"cokeOven":"blastFurnace");Object priorApi=api.get(null);
        cpw.mods.fml.common.Loader loader=cpw.mods.fml.common.Loader.instance();
        Map<String,cpw.mods.fml.common.ModContainer> old=cpw.mods.fml.relauncher.ReflectionHelper.getPrivateValue(cpw.mods.fml.common.Loader.class,loader,"namedMods");
        Map<String,cpw.mods.fml.common.ModContainer> configured=new HashMap<>(old);
        for(String[] version:new String[][]{{"Railcraft","9.16.33"},{"neiintegration","1.5.0"}}){cpw.mods.fml.common.ModMetadata metadata=new cpw.mods.fml.common.ModMetadata();metadata.modId=version[0];metadata.version=version[1];configured.put(version[0],new cpw.mods.fml.common.DummyModContainer(metadata));}
        try{
            api.set(null,manager);cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(cpw.mods.fml.common.Loader.class,loader,configured,"namedMods");
            java.lang.reflect.Field fuels=null;Object previousFuels=null;
            if(!coke){
                fuels=manager.getClass().getDeclaredField("fuels");fuels.setAccessible(true);previousFuels=fuels.get(manager);
                fuels.set(manager,null);
                NativeRailFuelDefaultsTest.run(manager,handler);
                require(fuels.get(manager)==null,"Rail reader initialized native fuel cache");
                try(RailRecipes cold=new RailRecipes(handler)){
                    require(cold.size()==6,"Cold production Rail reader needs native GUI prewarming");cold.finishVerification();
                    require(handler.arecipes.size()==1&&fuels.get(manager)==null,"Cold production reader changed borrowed state");
                }
                fuels.set(manager,new ArrayList<>(Arrays.asList(new ItemStack(Items.coal))));
            }
            try{
            RegistrationBindings first=RegistrationBindings.read(handler);
            require(first.same(RegistrationBindings.read((TemplateRecipeHandler)handler.getClass().newInstance())),"Independent Rail furnace source bindings differ");
            try(RailRecipes reader=new RailRecipes(handler)){
                RecipeRow captured=knownRow(known);
                require(reader.size()==6&&reader.capture(0,captured),"Actual Rail API source failed");reader.finishVerification();
                if(!coke)require(captured.record.has("process")&&captured.record.getAsJsonObject("process").get("kind").getAsString().equals("railcraftBlast"),"Native independent fuel rules were reduced to a prose property");
                if(!coke){
                    JsonObject origin=object("owner","neiintegration","handler",handler.getClass().getName(),"key","native-blast-fuel");
                    captured.record.add("source",origin);captured.record.addProperty("category",Identity.origin("category",origin));
                    List<ItemStack> specimens=new ArrayList<>();String text=captured.record.toString();
                    for(Map.Entry<String,ItemStack> entry:known.entrySet())if(text.contains("\""+entry.getKey()+"\""))specimens.add(entry.getValue());
                    specimens.add(new ItemStack(Items.coal,1,1));
                    ItemStack taggedFuel=new ItemStack(Items.coal);taggedFuel.setTagInfo("owner",new net.minecraft.nbt.NBTTagInt(99));specimens.add(taggedFuel);
                    NativeContractFixture.writeOwned("native-rail-blast-fuel",captured,specimens);
                }
                try{Object replacement=manager.getClass().newInstance();if(!coke)fuels.set(replacement,new ArrayList<>());api.set(null,replacement);require(!first.same(RegistrationBindings.read(handler)),"Replaced Rail manager stayed equivalent");fails(reader::verify,"recipe_changed");}finally{api.set(null,manager);}
                if(!coke){
                    List<ItemStack> table=(List<ItemStack>)fuels.get(manager);ItemStack original=table.get(0);
                    try{original.stackSize++;fails(reader::verify,"recipe_changed");}finally{original.stackSize--;}
                    try{table.set(0,original.copy());fails(reader::finishVerification,"recipe_changed");}finally{table.set(0,original);}
                    try{fuels.set(manager,new ArrayList<>(table));fails(reader::verify,"recipe_changed");require(!first.same(RegistrationBindings.read(handler)),"Replaced native fuel table stayed equivalent");}
                    finally{fuels.set(manager,table);}
                    try{
                        fuels.set(manager,new ArrayList<>());
                        try(RailBlastFuelSource empty=new RailBlastFuelSource(manager)){require(empty.size()==0,"Native empty fuel table is unavailable");empty.templates();empty.close();fails(empty::size,"capture_closed");}
                        UnknownMetadata unknown=new UnknownMetadata();ItemStack unknownStack=new ItemStack(unknown);unknown.armed=true;
                        fuels.set(manager,new ArrayList<>(Arrays.asList(unknownStack)));
                        fails(()->new RailBlastFuelSource(manager),"recipe_unsupported");require(unknown.calls==0,"Unknown fuel metadata callback executed");
                        java.lang.reflect.Constructor<?> constructor=Class.forName(manager.getClass().getName()+"$1").getDeclaredConstructor(manager.getClass());constructor.setAccessible(true);
                        List<ItemStack> nativeList=(List<ItemStack>)constructor.newInstance(manager);
                        require(!nativeList.add(null),"Native allowed-fuel list did not omit null entries");nativeList.add(original);
                        List<ItemStack> wrapper=Collections.unmodifiableList(nativeList);fuels.set(manager,wrapper);
                        try(RailBlastFuelSource observed=new RailBlastFuelSource(manager)){
                            require(observed.size()==1,"Native wrapper/backing list was rejected");
                            com.google.gson.JsonArray templates=observed.templates();templates.get(0).getAsJsonObject().getAsJsonObject("template").addProperty("amount","999");
                            require(observed.templates().get(0).getAsJsonObject().getAsJsonObject("template").get("amount").getAsString().equals("1"),"Caller mutated the native fuel definition");
                            nativeList.set(0,original.copy());fails(observed::verify,"recipe_changed");nativeList.set(0,original);observed.verify();
                        }
                        require(fuels.get(manager)==wrapper&&nativeList.get(0)==original,"Fuel source changed borrowed native list");
                    }finally{fuels.set(manager,table);}
                }
                reader.finishVerification();
            }
            }finally{if(fuels!=null)fuels.set(manager,previousFuels);}
        }finally{api.set(null,priorApi);cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(cpw.mods.fml.common.Loader.class,loader,old,"namedMods");}
    }
    @SuppressWarnings("unchecked") private static RecipeRow knownRow(Map<String,ItemStack> known){Facts facts=new Facts("en_US");((Set<String>)MagicApi.field(facts,"items")).addAll(known.keySet());return new RecipeRow(facts,object("owner","Railcraft","handler","native","key","furnace"),"category_test",0);}
    private interface Probe{void run()throws Exception;}
    private static void fails(Probe probe,String code)throws Exception{try{probe.run();throw new AssertionError("Expected Rail failure: "+code);}catch(Jobs.Fault failure){require(failure.code.equals(code),"Expected "+code+": "+failure);}}
    private static void rawMetadata(ItemStack item,int meta){cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(ItemStack.class,item,meta,"itemDamage","field_77991_e");}
    private static void add(Object manager, boolean coke, ItemStack input, boolean damage, boolean nbt, int time, ItemStack output) throws Exception {
        if (coke) manager.getClass().getMethod("addRecipe",ItemStack.class,boolean.class,boolean.class,ItemStack.class,FluidStack.class,int.class)
                .invoke(manager,input,damage,nbt,output,new FluidStack(FluidRegistry.WATER,250),time);
        else manager.getClass().getMethod("addRecipe",ItemStack.class,boolean.class,boolean.class,int.class,ItemStack.class).invoke(manager,input,damage,nbt,time,output);
    }
    private static String id(ItemStack stack) { return Identity.item(Item.itemRegistry.getNameForObject(stack.getItem()), Items.feather.getDamage(stack), TypedNbt.encode(stack.getTagCompound())); }
    static boolean matches(JsonObject rule, ItemStack anchor, ItemStack offered, Map<String, ItemStack> known) {
        String kind = rule.get("kind").getAsString();
        if (kind.equals("except")) {
            if (!matches(rule.getAsJsonObject("base"), anchor, offered, known)) return false;
            for (JsonElement raw : rule.getAsJsonArray("exclude")) {
                JsonObject prior = raw.getAsJsonObject();
                if (matches(prior.getAsJsonObject("rule"), known.get(prior.get("id").getAsString()), offered, known)) return false;
            }
            return true;
        }
        if (offered.getItem() != anchor.getItem()) return false;
        if (kind.equals("metadata")) {
            if (offered.getItemDamage() != rule.get("value").getAsInt()
                    || !rule.get("nbt").getAsBoolean() && !ItemStack.areItemStackTagsEqual(offered, anchor)) return false;
            for (JsonElement key : rule.getAsJsonArray("absent"))
                if (offered.hasTagCompound() && offered.getTagCompound().hasKey(key.getAsString())) return false;
            return true;
        }
        if (kind.equals("exact")) return offered.getItemDamage() == anchor.getItemDamage() && ItemStack.areItemStackTagsEqual(offered, anchor);
        if (!rule.get("meta").getAsBoolean() && offered.getItemDamage() != anchor.getItemDamage()) return false;
        if (kind.equals("tags")) return !offered.hasTagCompound() || !offered.getTagCompound().hasKey("synthetic");
        return rule.get("nbt").getAsBoolean() || ItemStack.areItemStackTagsEqual(offered, anchor);
    }
    private static void require(boolean test, String message) { if (!test) throw new AssertionError(message); }
}
