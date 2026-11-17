package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Railcraft 9.16.33 furnace registries, with native two-pass matching priority. */
final class RailRecipes implements RegistryRecipes {
    private static final String NEI = "tonius.neiintegration.mods.railcraft.RecipeHandler";
    private static final String CRAFTING = "mods.railcraft.common.util.crafting.";
    private final String handlerName;
    private final Object manager;
    private final List<?> registry;
    private final List<Object> originals=new ArrayList<>();
    private final NativeDefinitions definitions;
    private final boolean coke;
    private final RailBlastFuelSource fuelSource;
    private final List<Entry> entries = new ArrayList<>();
    private boolean closed;

    static boolean supports(IRecipeHandler handler) {
        String name = handler.getClass().getName();
        return name.equals(NEI + "CokeOven") || name.equals(NEI + "BlastFurnace");
    }
    RailRecipes(TemplateRecipeHandler handler) { this(handler, registrationSource(handler)); }
    static Source registrationSource(IRecipeHandler handler) {
        if(!supports(handler))throw fault("Unadapted Railcraft furnace handler");
        MagicApi.version("RailRecipes","Railcraft"); MagicApi.version("RailRecipes","neiintegration");
        boolean coke = handler.getClass().getName().equals(NEI + "CokeOven");
        Object manager = field(type("mods.railcraft.api.crafting.RailcraftCraftingManager"), null, coke ? "cokeOven" : "blastFurnace");
        if(manager==null)throw new Jobs.Fault("handler_not_ready","Railcraft furnace manager is unavailable");
        if (!manager.getClass().getName().equals(CRAFTING + (coke ? "CokeOven" : "BlastFurnace") + "CraftingManager"))
            throw fault("Unadapted Railcraft furnace registry");
        Object recipes=field(manager,"recipes");
        if(recipes==null)throw new Jobs.Fault("handler_not_ready","Railcraft furnace registry is unavailable");
        if(recipes.getClass()!=ArrayList.class||invoke(manager.getClass(),manager,"getRecipes",new Class<?>[0])!=recipes)
            throw fault("Railcraft native registry source changed");
        return new Source(manager,(List<?>)recipes);
    }
    static final class Source {
        final Object manager;
        final List<?> recipes;
        final boolean blast;
        Source(Object manager,List<?> recipes){
            this.manager=manager;this.recipes=recipes;
            blast=manager.getClass().getName().equals(CRAFTING+"BlastFurnaceCraftingManager");
        }
    }
    private RailRecipes(TemplateRecipeHandler handler,Source source){this(handler,source.recipes,source.manager);}
    // Also used by native conformance tests with an owned registry; no world is involved.
    RailRecipes(TemplateRecipeHandler handler, List<?> registry) {
        this(handler,registry,null);
    }
    private RailRecipes(TemplateRecipeHandler handler,List<?> registry,Object manager){
        if (!supports(handler)) throw fault("Unadapted Railcraft furnace handler");
        if(registry==null)throw new Jobs.Fault("handler_not_ready","Railcraft furnace registry is unavailable");
        this.handlerName=handler.getClass().getName();this.registry=registry;this.manager=manager;
        coke = handlerName.equals(NEI + "CokeOven");
        fuelSource=!coke&&manager!=null?new RailBlastFuelSource(manager):null;
        if (registry.size() > 262144) throw new Jobs.Fault("recipe_limit","Railcraft registry exceeds its budget");
        String expected = CRAFTING + (coke ? "CokeOvenCraftingManager$CokeOvenRecipe" : "BlastFurnaceCraftingManager$BlastFurnaceRecipe");
        List<Entry> deferred = new ArrayList<>();
        for (Object recipe : registry) {
            Jobs.checkpoint();
            if (recipe == null || !recipe.getClass().getName().equals(expected)) throw fault("Unadapted Railcraft furnace recipe implementation");
            Entry entry = new Entry(recipe, coke,originals.size());originals.add(recipe);
            // The manager's first pass selects matchDamage/non-wildcard templates,
            // even for items without subtypes. The second pass retains registry order.
            if (entry.damage && !wildcard(entry.input.getItemDamage())) entries.add(entry);
            else deferred.add(entry);
        }
        entries.addAll(deferred);
        definitions=new NativeDefinitions("Railcraft furnace",entries,this::currentVisible,raw->definition((Entry)raw));
    }
    public int size() {verify();return entries.size();}
    public void verify(){if(closed)throw new Jobs.Fault("capture_closed","Railcraft furnace reader is closed");definitions.verify();}
    private List<?> currentVisible(){
        if(fuelSource!=null)fuelSource.verify();
        if(manager!=null&&(field(type("mods.railcraft.api.crafting.RailcraftCraftingManager"),null,coke?"cokeOven":"blastFurnace")!=manager
                ||field(manager,"recipes")!=registry))throw changed("native manager/list changed");
        if(registry.size()!=originals.size())throw changed("native registry count changed");return entries;
    }
    public void finishVerification(){
        verify();for(int index=0;index<originals.size();index++){Jobs.checkpoint();if(registry.get(index)!=originals.get(index))throw changed("native registry order changed");}
        definitions.finishVerification();
    }
    public void close(){if(closed)return;closed=true;entries.clear();originals.clear();definitions.close();if(fuelSource!=null)fuelSource.close();}
    public boolean capture(int index, RecipeRow row) {
        verify();return definitions.checked(index,()->captureRow(index,row));
    }
    private boolean captureRow(int index,RecipeRow row){
        Entry entry = entries.get(index);
        if (coke && entry.time > Integer.MAX_VALUE / 50 * 50)
            throw fault("Railcraft cook time crosses the native signed counter wraparound");
        if (coke && entry.nbt && entry.input.hasTagCompound() && entry.input.getTagCompound().hasKey("synthetic"))
            return row.exclude(object("native", handlerName, "reason", "native_coke_oven_rejects_synthetic_tag",
                    "input", RecipeRow.stackEvidence(entry.input), "requiresNbt", true));
        List<Pattern> earlier = new ArrayList<>();
        for (int i = 0; i < index; i++) {
            Entry previous = entries.get(i);
            if (previous.input.getItem() == entry.input.getItem()) earlier.addAll(previous.patterns());
        }
        List<RecipeRow.Ingredient> choices = new ArrayList<>();
        for (Pattern base : entry.patterns()) {
            Jobs.checkpoint();
            boolean covered = false;
            for (Pattern prior : earlier) if (prior.covers(base)) { covered = true; break; }
            if (covered) continue;
            JsonArray exclude = new JsonArray(); Set<String> seen = new HashSet<>();
            for (Pattern prior : earlier) if (prior.overlaps(base)) {
                JsonObject rule = prior.rule(false);
                JsonObject filter = object("id", row.facts.item(prior.item), "rule", rule);
                if (seen.add(CanonicalJson.digest(filter))) exclude.add(filter);
                if (exclude.size() > 4096) throw fault("Railcraft input priority exceeds its exclusion budget");
            }
            JsonObject rule = base.rule(coke && !entry.nbt);
            if (exclude.size() > 0) rule = object("kind", "except", "base", rule, "exclude", exclude);
            choices.add(new RecipeRow.Ingredient(base.item, 1, false, rule));
        }
        if (choices.isEmpty()) return row.exclude(object("native", handlerName,
                "reason", entry.patterns().isEmpty() ? "empty_native_input_predicate" : "all_input_patterns_covered_by_earlier_native_entries",
                "input", RecipeRow.stackEvidence(entry.input), "earlierPatterns", earlier.size()));
        row.itemInput(0, choices, false);
        if (entry.output != null) row.itemOutput(0, entry.output.copy(), 10000);
        if (entry.fluid != null) row.fluidOutput(0, entry.fluid.copy());
        if (entry.output == null && entry.fluid == null) throw fault("Railcraft furnace recipe has no result");
        // Native coke processing advances by 50 on each machine clock pulse.
        long duration = coke ? Math.max(1L, ((long) entry.time + 49) / 50) * 50 : Math.max(1, entry.time);
        row.record.addProperty("duration", Long.toString(duration));
        if (coke) row.property("railcraft:clockInterval", "Machine processing interval (ticks)", 50);
        else if(fuelSource!=null)row.record.add("process",object("kind","railcraftBlast","registeredTime",entry.time,"fuel",fuelSource.declaration(row.facts)));
        else row.property("railcraft:fuel", "Fuel requirement", "Independent furnace fuel supply; native burn budget decreases by 5 per world tick, including idle time");
        row.property("railcraft:registeredTime", "Registered cook time (ticks)", entry.time);
        return true;
    }
    private JsonObject definition(Entry original){
        if(registry.get(original.nativeIndex)!=original.nativeRecipe)throw changed("native recipe instance/order changed");
        Entry current=new Entry(original.nativeRecipe,coke,original.nativeIndex);JsonArray patterns=array();
        for(Pattern pattern:current.patterns())patterns.add(object("display",RecipeRow.stackEvidence(pattern.item),"meta",pattern.meta,"damage",pattern.damage,"nbt",pattern.nbt));
        FluidStack fluid=current.fluid;
        return object("index",current.nativeIndex,"rawInput",RecipeRow.stackEvidence((ItemStack)field(current.nativeRecipe,"input")),
            "nativeMeta",current.input.getItemDamage(),"subtypes",current.input.getHasSubtypes(),"matchDamage",current.damage,"matchNBT",current.nbt,
            "fixedMeta",current.fixedMeta,"time",current.time,"output",RecipeRow.stackEvidence(current.output),"patterns",patterns,
            "fluid",fluid==null?null:object("name",fluid.getFluid().getName(),"amount",Integer.toString(fluid.amount),"nbt",com.github.dcysteine.nesql.exporter.source.TypedNbt.encode(fluid.tag)));
    }

    private static final class Entry {
        final Object nativeRecipe;final int nativeIndex;
        final ItemStack input, output; final FluidStack fluid; final boolean damage, nbt, fixedMeta; final int time;
        Entry(Object recipe, boolean coke,int nativeIndex) {
            this.nativeRecipe=recipe;this.nativeIndex=nativeIndex;
            ItemStack raw = (ItemStack) field(recipe, "input");
            if (raw == null || raw.getItem() == null) throw fault("Invalid Railcraft input template");
            // InvTools compares Item#getDamage, whereas fact IDs retain raw metadata.
            // GT++ burnables return one final field for every stack of this item.
            try {
                Class<?> getter = raw.getItem().getClass().getMethod("getDamage", ItemStack.class).getDeclaringClass();
                fixedMeta = getter.getName().equals("gtPlusPlus.core.item.base.BaseItemBurnable");
                if (fixedMeta) MagicApi.version("RailRecipes","gregtech_nh");
                else if (getter != Item.class)
                    throw fault("Railcraft input uses a custom metadata predicate: " + raw.getItem().getClass().getName());
            } catch (NoSuchMethodException error) { throw fault("Missing native Item damage getter"); }
            if(ItemCallbacks.method(raw.getItem(),"getHasSubtypes","func_77614_k").getDeclaringClass()!=Item.class
                    ||ItemCallbacks.method(raw.getItem(),"setDamage","setDamage",ItemStack.class,int.class).getDeclaringClass()!=Item.class)
                throw fault("Railcraft input requires audited subtype/metadata assignment semantics");
            if(raw.getItemDamage() < -1)throw fault("Invalid Railcraft input metadata");
            input = raw.copy(); input.stackSize = 1;
            // The native copy constructor clamps negative raw damage. Keep the
            // reviewed -1 predicate sentinel on our owned anchor, never on facts.
            if(Items.feather.getDamage(raw)==-1)rawMetadata(input,-1);
            damage = (Boolean) field(recipe, "matchDamage"); nbt = (Boolean) field(recipe, "matchNBT");
            // This is an owned matching example. Ignored tags do not constrain
            // the recipe, and synthetic-tag templates need a usable coke example.
            if (!nbt) input.setTagCompound(null);
            time = (Integer) field(recipe, "cookTime");
            ItemStack result = (ItemStack) field(recipe, "output"); output = result == null ? null : result.copy();
            FluidStack liquid = coke ? (FluidStack) field(recipe, "fluidOutput") : null; fluid = liquid == null ? null : liquid.copy();
            if(fluid!=null&&(fluid.getFluid()==null||fluid.amount<=0))throw fault("Invalid Railcraft fluid result");
        }
        List<Pattern> patterns() {
            boolean ignoreMeta = fixedMeta || !damage || !input.getHasSubtypes() || wildcard(input.getItemDamage());
            List<Pattern> patterns = new ArrayList<>(); patterns.add(new Pattern(input, ignoreMeta, !nbt));
            if (!ignoreMeta) {
                for(int sentinel:new int[]{-1,32767}){
                    ItemStack anchor = new ItemStack(input.getItem(), 1, 0);rawMetadata(anchor,sentinel);
                    if (input.hasTagCompound()) anchor.setTagCompound((net.minecraft.nbt.NBTTagCompound) input.getTagCompound().copy());
                    patterns.add(new Pattern(anchor, false, !nbt, input));
                }
            }
            return patterns;
        }
    }
    private static final class Pattern {
        final ItemStack item; final boolean meta, nbt; final int damage;
        Pattern(ItemStack item, boolean meta, boolean nbt) { this(item, meta, nbt, item); }
        Pattern(ItemStack template, boolean meta, boolean nbt, ItemStack display) {
            damage = template.getItemDamage(); this.item = example(display); this.meta = meta; this.nbt = nbt;
        }
        boolean covers(Pattern other) { return (meta || !other.meta && damage == other.damage)
                && (nbt || !other.nbt && ItemStack.areItemStackTagsEqual(item, other.item)); }
        boolean overlaps(Pattern other) { return (meta || other.meta || damage == other.damage)
                && (nbt || other.nbt || ItemStack.areItemStackTagsEqual(item, other.item)); }
        JsonObject rule(boolean excludeSynthetic) {
            // Literal sentinel matching must not depend on the concrete display's metadata.
            if (!meta && damage != item.getItemDamage()) return object("kind", "metadata", "value", damage,
                    "nbt", nbt, "absent", excludeSynthetic ? array("synthetic") : array());
            if (excludeSynthetic) return object("kind", "tags", "meta", meta, "keys", array(), "present", array(), "absent", array("synthetic"));
            return meta || nbt ? object("kind", "wildcard", "meta", meta, "nbt", nbt) : object("kind", "exact");
        }
    }
    static ItemStack example(ItemStack template) {
        if (!wildcard(Items.feather.getDamage(template))) return template;
        if(!codechicken.nei.ItemList.loadFinished||codechicken.nei.ItemList.items==null)
            throw new Jobs.Fault("handler_not_ready","Railcraft wildcard metadata catalogue is unavailable");
        if(codechicken.nei.ItemList.items.size()>1000000)throw new Jobs.Fault("recipe_limit","Railcraft wildcard catalogue exceeds budget");
        // This is a predicate anchor, not a literal wildcard item. Keep matching
        // every metadata value; take only concrete metadata from the loaded catalog.
        for (ItemStack candidate : codechicken.nei.ItemList.items) {
            Jobs.checkpoint();
            if (candidate == null || candidate.getItem() != template.getItem() || candidate.stackSize <= 0) continue;
            int meta = Items.feather.getDamage(candidate);
            if (meta < 0 || meta == 32767) continue;
            ItemStack example = template.copy(); example.setItemDamage(meta);
            return example; // Preserve template NBT/count, never catalog-example NBT.
        }
        throw fault("Railcraft wildcard input has no concrete NEI metadata example: "
                + Item.itemRegistry.getNameForObject(template.getItem()));
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
    private static boolean wildcard(int meta){return meta==-1||meta==32767;}
    private static void rawMetadata(ItemStack item,int meta){cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(ItemStack.class,item,meta,"itemDamage","field_77991_e");}
    private static Jobs.Fault changed(String message){return new Jobs.Fault("recipe_changed","Railcraft furnace "+message);}
}
