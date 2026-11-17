package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.Chance;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import forestry.api.recipes.ICentrifugeRecipe;
import forestry.api.recipes.IStillRecipe;
import forestry.api.recipes.RecipeManagers;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.init.Items;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Forestry 4.10.17 factories: machine consumption is distinct from NEI's display quantities. */
final class ForestryRecipes implements RegistryRecipes {
    private static final String PREFIX = "forestry.factory.recipes.";
    private final Class<?> handlerClass;
    private final List<?> recipes;
    private final NativeItemExamples examples=new NativeItemExamples();
    private final ForestryRecipeBindings bindings;
    private final Object manager,sourceRoot;
    private final String signature;
    private boolean closed;

    static boolean supports(ICraftingHandler handler) {
        String name = handler.getClass().getName();
        return name.equals(PREFIX + "nei.NEIHandlerCentrifuge") || name.equals(PREFIX + "nei.NEIHandlerStill");
    }
    private static boolean centrifuge(TemplateRecipeHandler handler) { return handler.getClass().getName().equals(PREFIX + "nei.NEIHandlerCentrifuge"); }
    ForestryRecipes(TemplateRecipeHandler handler) {
        MagicApi.version("ForestryRecipes","Forestry");
        if(!supports(handler))throw fault("Unadapted Forestry factory family");
        this.handlerClass = handler.getClass();
        bindings=new ForestryRecipeBindings(handlerClass);
        manager=centrifuge(handler)?RecipeManagers.centrifugeManager:RecipeManagers.stillManager;
        Object[] roots=registrationAnchors(handlerClass);sourceRoot=roots[2];
        Collection<?> source = (Collection<?>)sourceRoot;
        if (source.size() > 262144) throw fault("Factory registry exceeds its budget");
        recipes = new ArrayList<>(source);
        signature=com.github.dcysteine.nesql.exporter.source.CanonicalJson.digest(snapshot());
    }
    static boolean supportsClass(String name){return name.equals(PREFIX+"nei.NEIHandlerCentrifuge")||name.equals(PREFIX+"nei.NEIHandlerStill");}
    static Object[] registrationAnchors(Class<?> handler){boolean centrifuge=handler.getName().endsWith("Centrifuge");Object manager=centrifuge?RecipeManagers.centrifugeManager:RecipeManagers.stillManager;
        if(manager==null)throw new Jobs.Fault("handler_not_ready","Forestry factory manager is unavailable");
        if(!manager.getClass().getName().equals(PREFIX+(centrifuge?"CentrifugeRecipeManager":"StillRecipeManager")))throw fault("Unadapted Forestry factory manager");
        Object source=field(manager.getClass(),null,"recipes");if(source==null)throw new Jobs.Fault("handler_not_ready","Forestry factory source is unavailable");
        if(source.getClass()!=HashSet.class||((Collection<?>)source).size()>262144)throw fault("Unadapted Forestry factory source storage");
        return new Object[]{handler,manager,source};}
    public int size() { alive();return recipes.size(); }
    public void verify(){alive();bindings.verifyRoots();Object[] now=registrationAnchors(handlerClass);if(now[1]!=manager||now[2]!=sourceRoot||((Collection<?>)sourceRoot).size()!=recipes.size())throw changed();}
    public void finishVerification(){verify();bindings.verify();int i=0;for(Object value:(Collection<?>)sourceRoot)if(i>=recipes.size()||value!=recipes.get(i++))throw changed();if(i!=recipes.size()||!signature.equals(com.github.dcysteine.nesql.exporter.source.CanonicalJson.digest(snapshot())))throw changed();examples.verify();}
    public boolean capture(int index, RecipeRow row) { alive();capture(handlerClass.getName(), recipes.get(index), row,examples); return true; }

    static void capture(TemplateRecipeHandler handler, Object source, RecipeRow row) {
        try(NativeItemExamples examples=new NativeItemExamples()){capture(handler.getClass().getName(),source,row,examples);}
    }
    private static void capture(String handler, Object source, RecipeRow row,NativeItemExamples examples){
        boolean centrifuge = handler.endsWith("Centrifuge");
        if (!supportsClass(handler) || source == null || !source.getClass().getName().equals(PREFIX + (centrifuge ? "CentrifugeRecipe" : "StillRecipe"))) {
            throw fault("Unadapted Forestry recipe implementation: " + (source == null ? "null" : source.getClass().getName()));
        }
        if (centrifuge) centrifuge((ICentrifugeRecipe) source, row,examples);
        else still((IStillRecipe) source, row);
    }

    private static void centrifuge(ICentrifugeRecipe source, RecipeRow row,NativeItemExamples examples) {
        int time = source.getProcessingTime(); requirePositive(time, "centrifuge time");
        ItemStack input = item(source.getInput()); input.stackSize = 1; // TileCentrifuge.workCycle decrements one.
        List<Product> products = new ArrayList<>();
        Map<ItemStack, Float> nativeProducts = products(source);
        if (nativeProducts.isEmpty() || nativeProducts.size() > 4096) throw fault("Invalid centrifuge product count");
        for (Map.Entry<ItemStack, Float> entry : nativeProducts.entrySet()) {
            Jobs.checkpoint();
            if (entry.getValue() == null) throw fault("Missing centrifuge product probability");
            products.add(new Product(item(entry.getKey()), entry.getValue()));
        }
        // Native maps may use identity hashing. Order facts independently of their iteration order,
        // retaining separate identical entries because each gets its own native probability roll.
        products.sort(Comparator.comparing((Product product) -> product.key)
                .thenComparingInt(product -> product.item.stackSize).thenComparingDouble(product -> product.rate));
        List<RecipeRow.Ingredient> ingredients = new ArrayList<>();
        boolean wildcard = input.getItemDamage() == OreDictionary.WILDCARD_VALUE;
        boolean ignoreNbt = input.getTagCompound() == null || input.getTagCompound().hasNoTags();
        for (ItemStack variant : examples.copies(input)) {
            if (variant.getItem() != input.getItem() || !wildcard && variant.getItemDamage() != input.getItemDamage()) throw new Jobs.Fault("slot_changed", "Forestry input expansion changed its item");
            ItemStack fact = variant.copy(); fact.setTagCompound(input.getTagCompound() == null ? null : (net.minecraft.nbt.NBTTagCompound) input.getTagCompound().copy());
            ingredients.add(new RecipeRow.Ingredient(fact, 1, false, wildcard || ignoreNbt
                    ? object("kind", "wildcard", "meta", wildcard, "nbt", ignoreNbt) : object("kind", "exact")));
        }
        row.itemInput(0, ingredients, false);
        int slot = 0;
        for (Product product : products) {
            row.itemOutput(slot++, product.item, 10000);
            float rate = product.rate; // Native getProducts: >=1 always; NaN/<=0 never; otherwise nextFloat < rate.
            row.outputs.get(row.outputs.size() - 1).getAsJsonObject().add("chance", Chance.nextFloat(rate, false));
        }
        requirements(row, time, 160);
    }

    private static void still(IStillRecipe source, RecipeRow row) {
        int cycles = source.getCyclesPerUnit(); requirePositive(cycles, "still cycles");
        FluidStack input = fluid(source.getInput()), output = fluid(source.getOutput());
        try { input.amount = Math.multiplyExact(input.amount, cycles); output.amount = Math.multiplyExact(output.amount, cycles); }
        catch (ArithmeticException overflow) { throw fault("Native still batch overflows a fluid amount"); }
        // The native UI shows the nominal batch. TileStill.hasWork checks only
        // one output unit; workCycle fills the whole batch and discards overflow.
        row.fluidInput(0, input, false);
        row.fluidOutput(0, output, object("kind", "potential", "stat", "forestry:stillTankSpace",
                "condition", "Limited by compatible product tank space at completion; excess fluid is lost",
                "nominal", Integer.toString(output.amount)));
        requirements(row, cycles, 200);
    }

    private static void requirements(RecipeRow row, int time, int rfPerTick) {
        // TilePowered advances its counter every five game ticks, then applies
        // speed, difficulty and power multipliers. A recipe time is not a fixed
        // tick duration, nor is the raw RF parameter an EU/t rate.
        row.note("forestry:powered-cycle","forestry.core.tiles.TilePowered#updateServer;forestry.factory.tiles.TileCentrifuge/TileStill#workCycle",row.facts.locale().equals("zh_CN")?"基础工作步数 "+time+"，每步 5 个游戏 tick，难度前能量参数 "+(time*rfPerTick)+" RF。实际时间与能量由升级、难度和供能状态决定。":"Base work steps: "+time+"; 5 game ticks per step; energy parameter before difficulty: "+(time*rfPerTick)+" RF. Upgrades, difficulty and power state govern actual duration and energy.");
    }
    private static ItemStack item(ItemStack value) { if (value == null || value.getItem() == null || value.stackSize <= 0) throw fault("Invalid native factory item"); NativeStackAudit.audit(value);return value.copy(); }
    private static FluidStack fluid(FluidStack value) { if (value == null || value.getFluid() == null || value.amount <= 0) throw fault("Invalid native factory fluid"); return value.copy(); }
    private static void requirePositive(int value, String field) { if (value <= 0) throw fault("Invalid " + field); }
    private static final class Product {
        final ItemStack item;
        final float rate;
        final String key;
        Product(ItemStack item, float rate) {
            this.item = item; this.rate = rate;
            String registry = Item.itemRegistry.getNameForObject(item.getItem());
            if (registry == null) throw new Jobs.Fault("unregistered_item", "Forestry product is unregistered");
            key = Identity.item(registry, Items.feather.getDamage(item), TypedNbt.encode(item.getTagCompound()));
        }
    }
    private com.google.gson.JsonArray snapshot(){com.google.gson.JsonArray rows=array();for(Object raw:recipes){Jobs.checkpoint();if(raw==null||!raw.getClass().getName().equals(PREFIX+(handlerClass.getName().endsWith("Centrifuge")?"CentrifugeRecipe":"StillRecipe")))throw fault("Unadapted Forestry recipe owner");if(raw instanceof ICentrifugeRecipe){ICentrifugeRecipe r=(ICentrifugeRecipe)raw;com.google.gson.JsonArray outputs=array();Map<ItemStack,Float> products=products(r);List<Product> sorted=new ArrayList<>();for(Map.Entry<ItemStack,Float> p:products.entrySet())sorted.add(new Product(item(p.getKey()),p.getValue()));sorted.sort(Comparator.comparing((Product p)->p.key).thenComparingInt(p->p.item.stackSize).thenComparingDouble(p->p.rate));for(Product p:sorted)outputs.add(object("item",RecipeRow.stackEvidence(p.item),"rate",String.format(Locale.ROOT,"%08x",Float.floatToRawIntBits(p.rate))));rows.add(object("input",RecipeRow.stackEvidence(r.getInput()),"time",r.getProcessingTime(),"outputs",outputs));}else{IStillRecipe r=(IStillRecipe)raw;rows.add(object("input",liquid(r.getInput()),"output",liquid(r.getOutput()),"cycles",r.getCyclesPerUnit()));}}if(com.github.dcysteine.nesql.exporter.source.CanonicalJson.bytes(rows).length>32*1024*1024)throw fault("Forestry factory snapshot exceeds budget");return rows;}
    private static com.google.gson.JsonObject liquid(FluidStack stack){if(stack==null||stack.getClass()!=FluidStack.class)throw fault("Invalid Forestry fluid");NativeStackAudit.nbt(stack.tag);return object("registry",stack.getFluid().getName(),"amount",stack.amount,"nbt",TypedNbt.encode(stack.tag));}
    private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Forestry factory reader is closed");}
    public void close(){closed=true;recipes.clear();examples.close();bindings.close();}
    @SuppressWarnings("unchecked") private static Map<ItemStack,Float> products(ICentrifugeRecipe source){Object value=field(source,"outputs");if(!(value instanceof Map))throw fault("Invalid centrifuge product table");Class<?> owner=value.getClass();String name=owner.getName();if(owner!=HashMap.class&&owner!=LinkedHashMap.class&&owner!=Hashtable.class&&!name.equals("java.util.Collections$SingletonMap")&&!name.equals("com.google.common.collect.RegularImmutableMap")&&!name.equals("com.google.common.collect.SingletonImmutableBiMap")&&!name.equals("com.google.common.collect.EmptyImmutableBiMap"))throw fault("Unadapted centrifuge product storage: "+name);Map<ItemStack,Float> result=(Map<ItemStack,Float>)value;if(result.size()>4096)throw fault("Centrifuge products exceed budget");return result;}
    private static Jobs.Fault changed(){return new Jobs.Fault("environment_changed","Forestry factory source changed");}
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
