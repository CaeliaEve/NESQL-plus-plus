package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.ItemList;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.IArcaneRecipe;
import thaumcraft.api.crafting.ShapedArcaneRecipe;
import thaumcraft.api.crafting.ShapelessArcaneRecipe;
import thaumcraft.api.wands.StaffRod;
import thaumcraft.api.wands.WandCap;
import thaumcraft.api.wands.WandRod;
import thaumcraft.common.config.ConfigItems;
import thaumcraft.common.items.wands.ItemWandCasting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Salis' pinned replacement law over bound component/config sources. Only owned
 * stacks enter admitted wand accessors; result callbacks never initialize native caches. */
final class Wands implements AutoCloseable {
    private static final String ROOT = "dev.rndmorris.salisarcana.";
    private static final String CORE = ROOT + "common.recipes.ReplaceWandCoreRecipe";
    private static final String CAPS = ROOT + "common.recipes.ReplaceWandCapsRecipe";
    private static final String BRACELET = "tb.common.item.ItemCastingBracelet";
    private static final String DISPOSABLE = "com.kentington.thaumichorizons.common.items.ItemWandCastingDisposable";
    private final List<WandRod> rods=new ArrayList<>();
    private final List<WandCap> caps=new ArrayList<>();
    private final List<ItemWandCasting> items = new ArrayList<>();
    private final Map<String, State> fixed = new TreeMap<>();
    private final List<State> fixedStates=new ArrayList<>();
    private final List<Part> parts = new ArrayList<>();
    private final Map<String, List<ItemStack>> known = new TreeMap<>();
    private final List<Aspect> primal = new ArrayList<>();
    private final boolean preserve, single, enforce, materials, creative;
    private final int size;
    private final int variableStates;
    private int previous = -1;
    private List<MagicRecipes.Candidate> centers;
    private int excludedIndex = -1;
    private JsonObject exclusion;
    private boolean closed;
    private SalisWandBindings bindings;
    private final NativeItemExamples examples;

    static boolean supports(Object recipe) {
        return recipe != null && (recipe.getClass().getName().equals(CORE) || recipe.getClass().getName().equals(CAPS));
    }

    static boolean creative() {
        if (!cpw.mods.fml.common.Loader.isModLoaded("salisarcana")) return false;
        MagicApi.version("Wands","salisarcana");
        return enabled(field(type(ROOT + "config.SalisConfig"), null, "features"), "infiniteCreativeVis");
    }

    Wands(List<IArcaneRecipe> recipes,NativeItemExamples examples) {
        this.examples=examples;
        MagicApi.version("Wands","salisarcana"); MagicApi.version("Wands","gtnhtcwands");
        try{
        bindings=new SalisWandBindings();
        primal.addAll(Aspect.getPrimalAspects());
        Object features = field(type(ROOT + "config.SalisConfig"), null, "features");
        preserve = enabled(features, "preserveWandVis"); single = enabled(features, "allowSingleWandReplacement");
        creative = enabled(features, "infiniteCreativeVis");
        enforce = enabled(features, "enforceWandCoreTypes");
        Object compat = field(field(type(ROOT + "config.SalisConfig"), null, "modCompat"), "gtnhWands");
        materials = enabled(compat, "coreSwapMaterials");
        // Clearing uses Salis' six-aspect list, while preserving uses TC's primal registry.
        // Require their agreement instead of silently losing a mod-added primal charge.
        AspectList cleared = (AspectList) invoke(type(ROOT + "lib.AspectHelper"), null, "primalList", new Class<?>[] {int.class}, 0);
        if (primal.isEmpty() || primal.size() != cleared.size() || !primal.containsAll(Arrays.asList(cleared.getAspects()))) {
            throw fault("Salis and Thaumcraft disagree on primal charge keys");
        }
        primal.sort(Comparator.comparing(Aspect::getTag));
        rods.addAll(new TreeMap<>(WandRod.rods).values());
        caps.addAll(new TreeMap<>(WandCap.caps).values());
        if (rods.isEmpty() || caps.isEmpty() || rods.size() > 256 || caps.size() > 256) throw fault("Invalid wand component registry size");
        for (Object item : Item.itemRegistry) if (item instanceof ItemWandCasting) {
            ItemWandCasting wand = (ItemWandCasting) item;
            if (item.getClass().getName().equals(BRACELET)) {
                MagicApi.version("Wands","thaumicbases");
                String[] names = (String[]) field(item.getClass(), null, "names");
                if (names.length != 13) throw fault("Unexpected casting bracelet types");
                for (int meta = 0; meta < names.length; meta++) fixed(wand, meta);
            } else {
                if (item.getClass().getName().equals(DISPOSABLE)) MagicApi.version("Wands","ThaumicHorizons");
                else if (item.getClass() != ItemWandCasting.class) throw fault("Wand subclass requires its own state adapter: " + item.getClass().getName());
                items.add(wand);
            }
        }
        items.sort(Comparator.comparing(item -> Item.itemRegistry.getNameForObject(item)));
        for (IArcaneRecipe recipe : recipes) parts.addAll(parts(recipe));
        if (parts.isEmpty()) throw fault("Registered replacements have no initialized component mappings");
        parts.sort(Comparator.comparing((Part part) -> part.core ? "rod" : "cap").thenComparing(part -> part.name));
        for (ItemStack stack : ItemList.items) remember(stack);
        for (Object recipe : ThaumcraftApi.getCraftingRecipes()) {
            if (recipe.getClass() == ShapedArcaneRecipe.class || recipe.getClass() == ShapelessArcaneRecipe.class) {
                remember((ItemStack)field(recipe,"output"));
            }
        }
        fixedStates.addAll(fixed.values());
        long variable = (long) rods.size() * caps.size() * 2 * items.size();
        if (variable > 2000000) throw fault("Wand states exceed their budget");
        variableStates = (int) variable;
        long count = ((long) variableStates + fixedStates.size()) * parts.size();
        if (count > 2000000 || count == 0) throw fault("Invalid wand combination budget");
        size = (int) count;
        }catch(RuntimeException|Error error){close();throw error;}
    }

    int size() { alive();return size; }

    JsonObject exclusion(int index) {
        return index == excludedIndex && exclusion != null
                ? new com.google.gson.JsonParser().parse(exclusion.toString()).getAsJsonObject() : null;
    }
    private Result exclude(int index, State state, Part part, String reason) {
        excludedIndex = index;
        exclusion = object("reason", reason, "nativeIndex", index, "native", part.recipe.getClass().getName(),
                "state", state.key(), "part", part.name, "core", part.core,
                "enforceStaff", enforce, "requireMaterials", materials, "creative", creative);
        return null;
    }

    Result capture(int index, Facts facts) {
        alive();
        excludedIndex = -1; exclusion = null;
        Jobs.checkpoint();
        Part part = parts.get(index % parts.size());
        int group = index / parts.size();
        State state = state(group);
        ItemWandCasting wand = state.item;
        WandRod rod = state.rod; WandCap cap = state.cap; boolean sceptre = state.sceptre;
        if (part.value == (part.core ? rod : cap)) return exclude(index, state, part, "native_replacement_keeps_same_component");
        if (part.core && enforce && state.staff != (part.value instanceof StaffRod))
            return exclude(index, state, part, "native_staff_rod_enforcement_rejects_component");
        if (previous != group) { centers = centers(state); previous = group; }
        List<MagicRecipes.Candidate> admittedCenters=centers(state,part.core,facts);
        if(admittedCenters.isEmpty())return exclude(index,state,part,"native_component_lookup_precedes_wand_classification");
        List<List<MagicRecipes.Candidate>> inputs = new ArrayList<>();
        inputs.add(admittedCenters); inputs.add(part.choices);
        ItemStack center = admittedCenters.get(0).item;
        JsonObject materialRule=null;
        if (part.core && materials) {
            materialRule=materialRule(center,(WandRod)part.value,sceptre);
            if (materialRule==null||!materials(inputs, center, (WandRod) part.value, sceptre,facts))
                return exclude(index, state, part, "native_replacement_has_no_admissible_required_materials");
        } else if (!part.core) {
            for (int slot = 1; slot < (sceptre ? 3 : 2); slot++) inputs.add(part.choices);
        }
        WandRod resultRod=part.core?(WandRod)part.value:rod;
        WandCap resultCap=part.core?cap:(WandCap)part.value;
        int cost=bindings.cost(resultRod,resultCap,state.staff,sceptre);
        AspectList aspects=new AspectList();if(cost>=0)for(Aspect aspect:primal)aspects.add(aspect,cost);
        // Six zeroes can be paid. An empty list needs Salis' creative waiver.
        if (aspects.size() == 0 && !creative)
            return exclude(index, state, part, "native_arcane_empty_aspects_without_creative_waiver");
        ItemStack beforeCost=center.copy();
        if(part.core)wand.setRod(beforeCost,resultRod);else wand.setCap(beforeCost,resultCap);
        // Salis clamps preserved rod charge BEFORE resetting metadata. Bracelets
        // resolve capacity by metadata, so observing capacity afterwards is different.
        int chargeCapacity=wand.getMaxVis(beforeCost);
        ItemStack nativeOutput=replacement(center,part,cost);
        int capacity = wand.getMaxVis(nativeOutput);
        if (capacity < 0||chargeCapacity<0) throw fault("Wand capacity overflows its native integer");
        if (wand.getClass().getName().equals(DISPOSABLE) && capacity != 25000) throw fault("Disposable wand recipe observed outside its crafting context");

        ItemStack changes = new ItemStack(wand, 1, 0);
        if (part.core) wand.setRod(changes, (WandRod) part.value);
        else wand.setCap(changes, (WandCap) part.value);
        NBTTagCompound set = (NBTTagCompound) changes.getTagCompound().copy();
        Map<String, Integer> limits = new TreeMap<>();
        for (Aspect aspect : primal) {
            if (!preserve) set.setInteger(aspect.getTag(), 0);
            else if (part.core) limits.put(aspect.getTag(), chargeCapacity);
        }
        JsonObject limitValues = new JsonObject(); limits.forEach(limitValues::addProperty);
        int resultMetadata=Items.feather.getDamage(nativeOutput);
        JsonObject action = object("kind", "salisWand", "set", TypedNbt.encode(set).getAsJsonObject().get("value"), "limits", limitValues,"metadata",resultMetadata);
        List<ItemStack> specimens = new ArrayList<>(); for (MagicRecipes.Candidate candidate : admittedCenters) specimens.add(candidate.item);
        Products product = Products.observe(specimens, action, input -> {
            if(wand.getRod(input)!=rod||wand.getCap(input)!=cap||wand.isStaff(input)!=state.staff||wand.isSceptre(input)!=sceptre)
                throw fault("Wand specimen differs from its bound component/type state");
            ItemStack output = replacement(input,part,cost);
            ItemStack expected = Products.patch(input, set, limits);
            expected.setItemDamage(resultMetadata);
            if (!ItemStack.areItemStacksEqual(expected, output)) throw fault("Pinned Salis wand law differs from its declared output edit");
            return output;
        }, facts);
        TreeSet<String> research = new TreeSet<>();
        addResearch(research, part.recipe.getResearch());
        addResearch(research, part.core ? ((WandRod) part.value).getResearch() : ((WandCap) part.value).getResearch());
        JsonObject payment = null;
        if (single && !state.staff) {
            JsonObject charges = new JsonObject(); for (Aspect aspect : primal) charges.addProperty(Aspects.id(aspect), aspect.getTag());
            payment = object("input", 0, "charges", charges, "capacity", capacity, "preserve", preserve);
        }
        JsonArray roles=new JsonArray();roles.add(value("center"));roles.add(value("part"));
        for(int slot=2;slot<inputs.size();slot++)roles.add(value(part.core?(slot<inputs.size()-2?"screw":"conductor"):"part"));
        JsonObject process=object("kind","salisReplacement","nativeRecipe",part.recipe.getClass().getName(),"part",part.core?"rod":"cap",
            "staff",state.staff,"sceptre",sceptre,"enforceStaff",enforce,"targetStaff",part.core&&part.value instanceof StaffRod,
            "staffAttributes",set.hasKey("AttributeModifiers")?TypedNbt.encode(set.getTag("AttributeModifiers")):null,
            "roles",roles,"material",materialRule,"cost",cost,"preserve",preserve,"chargeCapacity",chargeCapacity,"resultCapacity",capacity,
            "selfPayment",single&&!state.staff,"paymentCondition","loadedSalisWorkbenchMixinAndNativePlayerPayment");
        return new Result(inputs, aspects.copy(), research, product, payment, process, state.meta != null);
    }

    /** Exact getCraftingResult order on an owned stack; no world/research/config callback. */
    private ItemStack replacement(ItemStack input,Part part,int cost){
        NativeStackAudit.audit(input);ItemStack output=input.copy();ItemWandCasting wand=(ItemWandCasting)output.getItem();
        if(part.core)wand.setRod(output,(WandRod)part.value);else wand.setCap(output,(WandCap)part.value);
        if(!preserve){AspectList cleared=new AspectList();for(Aspect aspect:primal)cleared.add(aspect,0);wand.storeAllVis(output,cleared);}
        else if(part.core){int max=wand.getMaxVis(output);AspectList stored=wand.getAllVis(output),clamped=new AspectList();
            for(Map.Entry<Aspect,Integer> entry:stored.aspects.entrySet())clamped.add(entry.getKey(),Math.min(max,entry.getValue()));wand.storeAllVis(output,clamped);}
        output.setItemDamage(Math.max(cost,0));return output;
    }

    private State state(int index) {
        if (index >= variableStates) return fixedStates.get(index - variableStates);
        int rest = index;
        boolean sceptre = (rest & 1) != 0; rest /= 2;
        WandCap cap = caps.get(rest % caps.size()); rest /= caps.size();
        WandRod rod = rods.get(rest % rods.size()); rest /= rods.size();
        return new State(items.get(rest), rod, cap, rod instanceof StaffRod, sceptre, null);
    }

    private List<MagicRecipes.Candidate> centers(State state) {
        ItemWandCasting wand = state.item; WandRod rod = state.rod; WandCap cap = state.cap; boolean sceptre = state.sceptre;
        Map<String, ItemStack> values = new LinkedHashMap<>();
        for (ItemStack sample : known.getOrDefault(state.key(), Collections.emptyList())) put(values, sample);
        ItemStack base = new ItemStack(wand, 1, state.meta == null ? 0 : state.meta);
        if (state.meta == null) {
            wand.setRod(base, rod); wand.setCap(base, cap);
            if (sceptre) base.getTagCompound().setBoolean("sceptre", true);
        }
        put(values, base);
        // Untagged wood/iron defaults are a different presence predicate from explicit tags.
        for (int omit = 1; state.meta == null && omit < 4; omit++) {
            if (((omit & 1) != 0 && rod != ConfigItems.WAND_ROD_WOOD) || ((omit & 2) != 0 && cap != ConfigItems.WAND_CAP_IRON)) continue;
            ItemStack implicit = base.copy();
            if ((omit & 1) != 0) implicit.getTagCompound().removeTag("rod");
            if ((omit & 2) != 0) implicit.getTagCompound().removeTag("cap");
            if (implicit.getTagCompound().hasNoTags()) implicit.setTagCompound(null);
            put(values, implicit);
        }
        if (values.size() > 128) throw fault("Too many representative states for one wand configuration");
        List<MagicRecipes.Candidate> result = new ArrayList<>();
        for (ItemStack value : values.values()) {
            if (state.meta != null) { result.add(candidate(value, false)); continue; }
            JsonArray keys = new JsonArray(), present = new JsonArray(), absent = new JsonArray();
            for (String name : Arrays.asList("cap", "rod")) {
                if (value.hasTagCompound() && value.getTagCompound().hasKey(name)) keys.add(value(name));
                else absent.add(value(name));
            }
            (sceptre ? present : absent).add(value("sceptre"));
            result.add(new MagicRecipes.Candidate(value, object("kind", "tags", "meta", true, "keys", keys, "present", present, "absent", absent)));
        }
        return result;
    }

    private JsonObject materialRule(ItemStack center,WandRod target,boolean sceptre){
        SalisWandBindings.Material material=bindings.material(target,((ItemWandCasting)center.getItem()).isStaff(center),sceptre);
        if(material==null||material.conductor==null)return null;
        return object("screw",material.screw,"conductor",InfusionLegacyInputs.template(material.conductor),"screws",sceptre?2:4);
    }
    private boolean materials(List<List<MagicRecipes.Candidate>> inputs, ItemStack center, WandRod target, boolean sceptre,Facts facts) {
        SalisWandBindings.Material material=bindings.material(target,((ItemWandCasting)center.getItem()).isStaff(center),sceptre);
        if(material==null)return false;String ore=material.screw;ItemStack conductor=material.conductor;
        if (conductor == null || !OreDictionary.doesOreNameExist(ore)) return false;
        if (conductor.getItem() instanceof ItemWandCasting || mappedRod(conductor) != null) return false;
        List<MagicRecipes.Candidate> screws = new ArrayList<>();
        NativeStackAudit.audit(conductor);
        boolean wildcardSource=false;Map<String,MagicRecipes.Candidate> unique=new TreeMap<>();
        List<ItemStack> members=NativeOreTable.copies(ore);
        // Ore is a cross-item predicate. Its exclusions must cover the whole
        // registered domain, not just the item used by this display specimen.
        Map<String,Item> domain=new TreeMap<>();
        for(ItemStack member:members){NativeStackAudit.audit(member);domain.put(Item.itemRegistry.getNameForObject(member.getItem()),member.getItem());}
        JsonArray excluded=new JsonArray();
        for(Item item:domain.values()){
            if(item instanceof ItemWandCasting)addExclusion(excluded,new ItemStack(item,1,0),facts,true);
            else for(com.google.gson.JsonElement entry:componentExclusions(item,true,facts))addExclusion(excluded,entry.getAsJsonObject());
        }
        addExclusion(excluded,conductor,facts,false);
        JsonObject screwRule=object("kind","except","base",object("kind","ore","name",ore,"exclusive",false),"exclude",excluded);
        for (ItemStack member : members) {
            NativeStackAudit.audit(member);wildcardSource|=Items.feather.getDamage(member)==32767;
            for(ItemStack screw:examples.permutations(member)){
                if(screw.getItem() instanceof ItemWandCasting||mappedRod(screw)!=null||screw.isItemEqual(conductor))continue;
                unique.putIfAbsent(identity(screw),new MagicRecipes.Candidate(screw.copy(),screwRule));
                if(unique.size()>65536)throw fault("Salis screw specimens exceed budget");
            }
        }
        screws.addAll(unique.values());
        if (screws.isEmpty()) {if(wildcardSource)throw fault("Wildcard screw domain has no safe native specimen; this is not an unreachable-recipe proof");return false;}
        for (int slot = 0; slot < (sceptre ? 2 : 4); slot++) inputs.add(screws);
        List<MagicRecipes.Candidate> conductors = Collections.singletonList(candidate(conductor, false));
        inputs.add(conductors); inputs.add(conductors);
        return true;
    }

    private List<Part> parts(IArcaneRecipe recipe) {
        boolean core = recipe.getClass().getName().equals(CORE);
        Map<?, ?> mapping = (Map<?, ?>) field(type(ROOT + "lib.WandHelper"), null, core ? "WAND_RODS" : "WAND_CAPS");
        Map<String, Part> parts = new TreeMap<>();
        for (Map.Entry<?, ?> item : mapping.entrySet()) {
            if (!(item.getKey() instanceof Item)) throw fault("Invalid wand component item key");
            for (Map.Entry<?, ?> variant : componentMap((Item)item.getKey(),core).entrySet()) {
                if(!(variant.getKey() instanceof Integer))throw fault("Invalid Salis component metadata key");
                Object component = variant.getValue();
                if(component==null)continue; // Native lookup treats null as unmapped.
                if(!(core?component instanceof WandRod:component instanceof WandCap))throw fault("Invalid Salis component mapping value");
                String name = core ? ((WandRod) component).getTag() : ((WandCap) component).getTag();
                Part part = parts.computeIfAbsent(name, ignored -> new Part(recipe, core, component, name));
                if (part.value != component) throw fault("Distinct mapped wand components share a tag: " + name);
                part.choices.add(candidate(new ItemStack((Item) item.getKey(), 1, (Integer) variant.getKey()), false));
            }
        }
        for (Part part : parts.values()) part.choices.sort(Comparator.comparing(candidate -> identity(candidate.item)));
        return new ArrayList<>(parts.values());
    }

    private void remember(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemWandCasting)) return;
        NativeStackAudit.audit(stack);
        ItemWandCasting wand = (ItemWandCasting) stack.getItem();
        WandRod rod = wand.getRod(stack); WandCap cap = wand.getCap(stack);
        boolean bracelet = wand.getClass().getName().equals(BRACELET);
        if (!bracelet && (!rods.contains(rod) || !caps.contains(cap))) return;
        State state = bracelet ? fixed(wand, Items.feather.getDamage(stack)) : new State(wand, rod, cap, wand.isStaff(stack), wand.isSceptre(stack), null);
        List<ItemStack> samples = known.computeIfAbsent(state.key(), ignored -> new ArrayList<>());
        if (samples.size() >= 1024) throw fault("Wand examples exceed their budget");
        ItemStack copy = stack.copy(); copy.stackSize = 1; samples.add(copy);
    }
    private State fixed(ItemWandCasting wand, int meta) {
        if (meta < 0) throw fault("Invalid casting bracelet metadata");
        ItemStack sample = new ItemStack(wand, 1, meta);
        State state = new State(wand, wand.getRod(sample), wand.getCap(sample), wand.isStaff(sample), wand.isSceptre(sample), meta);
        if (fixed.size() >= 4096 && !fixed.containsKey(state.key())) throw fault("Casting bracelet states exceed their budget");
        fixed.putIfAbsent(state.key(), state); return fixed.get(state.key());
    }
    private static String identity(ItemStack item) {
        return Identity.item(Item.itemRegistry.getNameForObject(item.getItem()), Items.feather.getDamage(item), TypedNbt.encode(item.getTagCompound()));
    }
    private static void put(Map<String, ItemStack> values, ItemStack item) { values.putIfAbsent(identity(item), item.copy()); }
    private static MagicRecipes.Candidate candidate(ItemStack item, boolean meta) {
        NativeStackAudit.audit(item);
        ItemStack copy = item.copy(); copy.stackSize = 1;
        return new MagicRecipes.Candidate(copy, object("kind", "wildcard", "meta", meta, "nbt", true));
    }
    private static boolean enabled(Object group, String name) {
        return SalisWandBindings.enabled(group,name);
    }
    private static Object mappedRod(ItemStack item) {
        NativeStackAudit.audit(item);
        return componentMap(item.getItem(),true).get(Items.feather.getDamage(item));
    }
    private List<MagicRecipes.Candidate> centers(State state,boolean core,Facts facts){
        JsonArray excluded=componentExclusions(state.item,core,facts);List<MagicRecipes.Candidate> result=new ArrayList<>();
        Map<?,?> mapped=componentMap(state.item,core);
        for(MagicRecipes.Candidate candidate:centers)if(mapped.get(Items.feather.getDamage(candidate.item))==null)
            result.add(new MagicRecipes.Candidate(candidate.item.copy(),excluded.size()==0?candidate.rule:object("kind","except","base",candidate.rule,"exclude",excluded)));
        if(result.isEmpty()&&state.meta==null){
            int meta=0;while(mapped.get(meta)!=null){if(++meta>4096)throw fault("Salis unmapped center metadata search exceeds budget");}
            MagicRecipes.Candidate seed=centers.get(0);ItemStack witness=seed.item.copy();witness.setItemDamage(meta);
            result.add(new MagicRecipes.Candidate(witness,object("kind","except","base",seed.rule,"exclude",excluded)));
        }
        return result;
    }
    private static Map<?,?> componentMap(Item item,boolean core){
        if(ItemCallbacks.method(item,"getDamage","getDamage",ItemStack.class).getDeclaringClass()!=Item.class)
            throw fault("Salis component metadata lookup needs an adapter: "+item.getClass().getName());
        Map<?,?> mapping=(Map<?,?>)field(type(ROOT+"lib.WandHelper"),null,core?"WAND_RODS":"WAND_CAPS");
        Object raw=mapping.get(item);if(raw==null)return Collections.emptyMap();
        if(raw.getClass()!=java.util.HashMap.class||((Map<?,?>)raw).size()>4096)throw fault("Unadapted Salis component metadata backing");return (Map<?,?>)raw;
    }
    private static JsonArray componentExclusions(Item item,boolean core,Facts facts){
        JsonArray result=new JsonArray();for(Map.Entry<?,?> entry:componentMap(item,core).entrySet())if(entry.getValue()!=null){
            if(!(entry.getKey() instanceof Integer))throw fault("Invalid Salis component metadata key");
            addExclusion(result,new ItemStack(item,1,(Integer)entry.getKey()),facts,false);
        }return result;
    }
    private static void addExclusion(JsonArray cases,ItemStack stack,Facts facts,boolean allMetadata){
        JsonObject entry=object("id",facts.item(stack),"rule",object("kind","wildcard","meta",allMetadata,"nbt",true));
        addExclusion(cases,entry);
    }
    private static void addExclusion(JsonArray cases,JsonObject entry){
        for(com.google.gson.JsonElement prior:cases)if(prior.equals(entry))return;
        if(cases.size()>=4096)throw fault("Salis classification exclusions exceed budget");cases.add(entry);
    }
    private static void addResearch(TreeSet<String> keys, String key) { if (key != null && !key.isEmpty()) keys.add(key); }
    private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Native wand reader is closed");}
    void verify(){alive();bindings.verify();}
    void finishVerification(){verify();bindings.finishVerification();}
    public void close(){closed=true;if(bindings!=null)bindings.close();rods.clear();caps.clear();items.clear();fixed.clear();fixedStates.clear();parts.clear();known.clear();primal.clear();centers=null;exclusion=null;}
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }

    private static final class Part {
        final IArcaneRecipe recipe;
        final boolean core;
        final Object value;
        final String name;
        final List<MagicRecipes.Candidate> choices = new ArrayList<>();
        Part(IArcaneRecipe recipe, boolean core, Object value, String name) { this.recipe = recipe; this.core = core; this.value = value; this.name = name; }
    }
    private static final class State {
        final ItemWandCasting item;
        final WandRod rod;
        final WandCap cap;
        final boolean staff, sceptre;
        final Integer meta;
        State(ItemWandCasting item, WandRod rod, WandCap cap, boolean staff, boolean sceptre, Integer meta) {
            if (rod == null || cap == null) throw fault("Wand state has no effective components");
            this.item = item; this.rod = rod; this.cap = cap; this.staff = staff; this.sceptre = sceptre; this.meta = meta;
        }
        String key() { return Item.itemRegistry.getNameForObject(item) + "\n" + rod.getTag() + "\n" + cap.getTag() + "\n" + sceptre + "\n" + meta; }
    }
    static final class Result {
        final List<List<MagicRecipes.Candidate>> inputs;
        final AspectList aspects;
        final TreeSet<String> research;
        final Products product;
        final JsonObject payment;
        final JsonObject process;
        final boolean fixed;
        Result(List<List<MagicRecipes.Candidate>> inputs, AspectList aspects, TreeSet<String> research, Products product, JsonObject payment,JsonObject process, boolean fixed) {
            this.inputs = inputs; this.aspects = aspects; this.research = research; this.product = product; this.payment = payment;this.process=process; this.fixed = fixed;
        }
    }
}
