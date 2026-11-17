package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;
import codechicken.nei.recipe.ICraftingHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.google.gson.*;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import java.nio.file.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Actual-adapter transport samples replayed by the Compiler conformance suite. */
final class NativeContractFixture {
    /** Keep real source/category identity while priming only owned item facts. */
    static RecipeRow forestryRow(String name, ICraftingHandler handler, ItemStack... specimens) {
        Facts facts=NativeCoreFixesTest.row(specimens).facts;
        JsonObject origin=object("owner","Forestry","handler",handler.getClass().getName(),"key",name);
        return new RecipeRow(facts,origin,Identity.origin("category",origin),0);
    }
    static Path directory() throws java.io.IOException {
        Path root=Paths.get(System.getProperty("nesql.nativeFixtureDirectory","build/native-tests"));
        Files.createDirectories(root);return root;
    }
    /** Semantic cursor samples must not ask a native handler for a GUI/cached stack list. */
    static void writeOwned(String name, RecipeRow row, Collection<ItemStack> specimens,
                           net.minecraftforge.fluids.FluidStack... fluids) throws Exception {
        writeOwned(name,row,specimens,false,false,fluids);
    }
    /** Domains whose matching semantics include ore memberships need the real
     * native tags too; simple owned fixtures may intentionally omit that census.
     */
    static void writeOwnedWithOres(String name, RecipeRow row, Collection<ItemStack> specimens) throws Exception {
        writeOwned(name,row,specimens,true,false);
    }
    static void writeOwnedWithDurability(String name,RecipeRow row,Collection<ItemStack> specimens,net.minecraftforge.fluids.FluidStack... fluids)throws Exception{
        writeOwned(name,row,specimens,false,true,fluids);
    }
    private static void writeOwned(String name, RecipeRow row, Collection<ItemStack> specimens, boolean nativeOres,boolean durability,
                                   net.minecraftforge.fluids.FluidStack... fluids) throws Exception {
        row.finish();
        JsonArray items = array(), liquids = array(), strings = array();
        Set<String> ids = new HashSet<>();
        for (ItemStack stack : specimens) {
            JsonObject item = RecipeRow.stackEvidence(stack); item.remove("amount");
            String id = Identity.item(item.get("registry").getAsString(), Items.feather.getDamage(stack), item.get("nbt"));
            if (ids.add(id)) {
                if(durability)item.addProperty("durability",stack.getMaxDamage());
                item.addProperty("id", id);JsonArray tags=array();
                if(nativeOres){Set<String> names=new TreeSet<>();for(int ore:net.minecraftforge.oredict.OreDictionary.getOreIDs(stack))names.add(net.minecraftforge.oredict.OreDictionary.getOreName(ore));
                    for(String tag:names)tags.add(value(tag));}
                item.add("tags", tags); items.add(item);
            }
        }
        ids.clear();
        for (net.minecraftforge.fluids.FluidStack stack : fluids) {
            JsonElement tag = TypedNbt.encode(stack.tag);
            String id = Identity.fluid(stack.getFluid().getName(), tag);
            if (ids.add(id)) liquids.add(object("id", id, "registry", stack.getFluid().getName(), "nbt", tag));
        }
        directory();
        JsonArray programs=array();
        for (Facts.Record record : row.facts.drain().records) {
            if (record.kind.equals("strings")) strings.add(record.value);
            if (record.kind.equals("programs")) programs.add(record.value);
        }
        JsonObject result=object("recipe", row.record, "items", items, "fluids", liquids, "strings", strings);
        if(programs.size()>0)result.add("programs",programs);
        Files.write(directory().resolve(name + "-contract.json"),
                CanonicalJson.bytes(result));
    }
    static void writeReference(String name,JsonObject reference,Collection<ItemStack> specimens) throws Exception {
        JsonArray rows=array();Set<String> identities=new HashSet<>();
        for(ItemStack stack:specimens){
            String registry=Item.itemRegistry.getNameForObject(stack.getItem());JsonElement nbt=TypedNbt.encode(stack.getTagCompound());
            String id=Identity.item(registry,Items.feather.getDamage(stack),nbt);
            if(identities.add(id))rows.add(object("id",id,"registry",registry,"meta",Items.feather.getDamage(stack),"nbt",nbt,"tags",array(),"durability",stack.getMaxDamage()));
        }
        Files.write(directory().resolve(name+"-contract.json"),CanonicalJson.bytes(object("reference",reference,"items",rows)));
    }
    static void write(String name,RecipeRow row,ICraftingHandler handler,int page,net.minecraftforge.fluids.FluidStack... knownFluids) throws Exception {
        writeWithItems(name,row,handler,page,Collections.emptyList(),knownFluids);
    }
    static void writeWithItems(String name,RecipeRow row,ICraftingHandler handler,int page,Collection<ItemStack> knownItems,net.minecraftforge.fluids.FluidStack... knownFluids) throws Exception {
        JsonObject recipe=new JsonParser().parse(row.record.toString()).getAsJsonObject();
        String className=handler.getClass().getName();
        String owner;
        if(className.startsWith("ganymedes01.etfuturum."))owner="etfuturum";
        else if(className.startsWith("gregtech."))owner="gregtech";
        else if(className.startsWith("gtnhintergalactic."))owner="gregtech";
        else if(className.startsWith("eu.usrv.enhancedlootbags."))owner="enhancedlootbags";
        else if(className.startsWith("forestry."))owner="Forestry";
        else if(className.startsWith("WayofTime."))owner="AWWayofTime";
        else if(className.startsWith("binnie.genetics."))owner="Genetics";
        else if(className.startsWith("binnie.extratrees."))owner="ExtraTrees";
        else if(className.startsWith("net.bdew.gendustry."))owner="gendustry";
        else if(className.startsWith("team.chisel."))owner="chisel";
        else if(className.startsWith("com.rwtema.extrautils."))owner="ExtraUtilities";
        else if(className.startsWith("appeng."))owner="appliedenergistics2";
        else if(className.startsWith("witchinggadgets."))owner="WitchingGadgets";
        else if(className.startsWith("alkalus.main.nei."))owner="WitcheryExtras";
        else throw new IllegalArgumentException("Unassigned native fixture owner: "+className);
        recipe.add("source",object("owner",owner,"handler",handler.getClass().getName(),"key",name));
        recipe.addProperty("id",Identity.recipe(recipe));
        Map<String,JsonObject> items=new TreeMap<>();List<PositionedStack> stacks=new ArrayList<>(handler.getIngredientStacks(page));
        if(handler.getResultStack(page)!=null)stacks.add(handler.getResultStack(page));
        if(handler.getOtherStacks(page)!=null)stacks.addAll(handler.getOtherStacks(page));
        for(ItemStack stack:knownItems)stacks.add(new PositionedStack(stack,0,0,false));
        for(PositionedStack position:stacks)if(position!=null)for(ItemStack stack:position.items)if(stack!=null){
            JsonElement nbt=TypedNbt.encode(stack.getTagCompound());String registry=Item.itemRegistry.getNameForObject(stack.getItem());
            String id=Identity.item(registry,Items.feather.getDamage(stack),nbt);JsonArray tags=array();Set<String> names=new TreeSet<>();
            for(int ore:net.minecraftforge.oredict.OreDictionary.getOreIDs(stack))names.add(net.minecraftforge.oredict.OreDictionary.getOreName(ore));
            for(String tag:names)tags.add(value(tag));
            items.put(id,object("id",id,"registry",registry,"meta",Items.feather.getDamage(stack),"nbt",nbt,"tags",tags,
                "durability",stack.getMaxDamage()));
        }
        JsonArray itemRecords=array();for(JsonObject item:items.values())itemRecords.add(item);
        JsonArray chunks=array(),fluidRecords=array();Facts.Batch batch=(Facts.Batch)MagicApi.field(row.facts,"batch");
        for(Facts.Record record:batch.records){if(record.kind.equals("programs"))chunks.add(record.value);if(record.kind.equals("fluids"))fluidRecords.add(record.value);}
        for(net.minecraftforge.fluids.FluidStack fluid:knownFluids){
            JsonElement nbt=TypedNbt.encode(fluid.tag);String registry=fluid.getFluid().getName();
            fluidRecords.add(object("id",Identity.fluid(registry,nbt),"registry",registry,"nbt",nbt));
        }
        Files.write(directory().resolve(name+"-contract.json"),CanonicalJson.bytes(
            object("recipe",recipe,"items",itemRecords,"fluids",fluidRecords,"programs",chunks)));
    }
}
