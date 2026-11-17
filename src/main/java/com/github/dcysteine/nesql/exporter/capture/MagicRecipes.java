package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.CrucibleRecipe;
import thaumcraft.api.crafting.IArcaneRecipe;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.api.crafting.ShapedArcaneRecipe;
import thaumcraft.api.crafting.ShapelessArcaneRecipe;
import thaumcraft.common.items.wands.ItemWandCasting;
import thaumcraft.common.lib.crafting.InfusionRunicAugmentRecipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

import static com.github.dcysteine.nesql.exporter.source.Json.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;

/** Pinned Thaumcraft rules and native content examples, with no recipe GUI state. */
final class MagicRecipes implements RegistryRecipes {
    private static final String NEI = "com.gtnewhorizons.aspectrecipeindex.nei.";

    private enum Family {
        SHAPED(NEI + "arcaneworkbench.ShapedArcaneRecipeHandler", ShapedArcaneRecipe.class),
        SHAPELESS(NEI + "arcaneworkbench.ShapelessArcaneRecipeHandler", ShapelessArcaneRecipe.class),
        CRUCIBLE(NEI + "AlchemyRecipeHandler", CrucibleRecipe.class),
        INFUSION(NEI + "InfusionRecipeHandler", InfusionRecipe.class);
        final String handler;
        final Class<?> recipe;
        Family(String handler, Class<?> recipe) { this.handler = handler; this.recipe = recipe; }
    }

    private final Family family;
    private final List<Object> recipes = new ArrayList<>();
    private final List<NativeArcaneRecipes> nativeArcane=new ArrayList<>();
    private final List<NativeInfusionRecipes> nativeInfusion=new ArrayList<>();
    private final List<DisguiseInfusionRecipes> disguiseInfusion=new ArrayList<>();
    private InfusionLegacyInputs.Context legacyInfusion;
    private final List<Object> registrations=new ArrayList<>();
    private List<?> registry;
    private Object aspectRegistry;
    private GadomancyJarSource stickySource;
    private NativeDefinitionGraph ordinaryDefinitions;
    private final List<Object> ordinarySources=new ArrayList<>();
    private final NativeItemExamples examples=new NativeItemExamples();
    private InfusionInheritance inheritance;
    private RunicChargeSource runicCharges;
    private final List<Runic.Source> runicSources=new ArrayList<>();
    private final List<Runic.ContainerReturn> runicReturns=new ArrayList<>();
    private boolean closed;
    private Wands wands;
    private int excludedIndex = -1;
    private JsonObject exclusion;
    private boolean creative;

    static boolean supports(ICraftingHandler handler) { return family(handler) != null; }
    static boolean supportsClass(String name){for(Family family:Family.values())if(family.handler.equals(name))return true;return false;}
    static Object[] registrationAnchors(){return new Object[]{ThaumcraftApi.getCraftingRecipes(),field(Aspect.class,null,"aspects")};}
    private static Family family(ICraftingHandler handler) {
        for (Family family : Family.values()) if (handler.getClass().getName().equals(family.handler)) return family;
        return null;
    }

    @SuppressWarnings("unchecked")
    MagicRecipes(TemplateRecipeHandler handler) {
        family = family(handler);
        if (family == null) throw fault("Unrecognized Thaumcraft NEI handler");
        try{
        MagicApi.version("MagicRecipes","aspectrecipeindex");
        MagicApi.version("MagicRecipes","tc4tweak");
        List<IArcaneRecipe> replacements = new ArrayList<>();
        registry=ThaumcraftApi.getCraftingRecipes();
        aspectRegistry=field(Aspect.class,null,"aspects");
        if(registry==null||registry.size()>262144)throw new Jobs.Fault("handler_not_ready","Thaumcraft registration source is unavailable");
        registrations.addAll(registry);
        if(family==Family.INFUSION)legacyInfusion=new InfusionLegacyInputs.Context();
        for (Object recipe : registry) {
            if(recipe==null)throw fault("Null Thaumcraft native registration");
            if(NativeArcaneRecipes.supports(recipe)) {
                if(family==(recipe.getClass().getName().equals(NativeArcaneRecipes.PHOTO)?Family.SHAPELESS:Family.SHAPED)){
                    NativeArcaneRecipes reader=new NativeArcaneRecipes(recipe);nativeArcane.add(reader);recipes.addAll(reader.branches());
                }
            }
            else if(family==Family.INFUSION&&recipe.getClass().getName().equals(DisguiseInfusionRecipes.RECIPE)){
                DisguiseInfusionRecipes reader=new DisguiseInfusionRecipes((InfusionRecipe)recipe,examples,legacyInfusion);
                disguiseInfusion.add(reader);recipes.addAll(reader.branches());
            }
            else if(family==Family.INFUSION&&recipe.getClass().getName().equals(DisguiseInfusionRecipes.VISUAL))recipes.add(recipe);
            else if(family==Family.INFUSION&&NativeInfusionRecipes.supports(recipe)){
                NativeInfusionRecipes reader=new NativeInfusionRecipes((InfusionRecipe)recipe,examples,legacyInfusion);
                nativeInfusion.add(reader);recipes.add(reader.branch());
            }
            else if (family == Family.INFUSION && recipe instanceof InfusionRunicAugmentRecipe){
                Runic.Source source=new Runic.Source((InfusionRunicAugmentRecipe)recipe,examples,legacyInfusion);
                runicSources.add(source);recipes.addAll(source.branches());
            }
            else if (family.recipe.isInstance(recipe)) recipes.add(recipe);
            else if (Wands.supports(recipe)) { if (family == Family.SHAPELESS) replacements.add((IArcaneRecipe) recipe); }
            else if (family == Family.SHAPED && recipe instanceof IArcaneRecipe && !(recipe instanceof ShapelessArcaneRecipe)) {
                recipes.add(recipe);
            }
        }
        if(family!=Family.INFUSION){
            for(Object source:recipes)if(!(source instanceof NativeArcaneRecipes.Branch))ordinarySources.add(source);
            java.util.Map<String,String[]> shapes=new java.util.HashMap<>();
            shapes.put(ShapedArcaneRecipe.class.getName(),new String[]{"output","input","aspects","research","width","height","mirrored"});
            shapes.put(ShapelessArcaneRecipe.class.getName(),new String[]{"output","input","aspects","research"});
            shapes.put("tuhljin.automagy.lib.recipe.PreserveFilterRecipe",new String[]{"output","input","aspects","research"});
            shapes.put(CrucibleRecipe.class.getName(),new String[]{"recipeOutput","catalyst","aspects","key"});
            shapes.put(AspectList.class.getName(),new String[]{"aspects"});shapes.put(Aspect.class.getName(),new String[]{"tag"});
            shapes.put("makeo.gadomancy.common.crafting.RecipeStickyJar",new String[0]);
            // The visual recipe's mutable output/input/aspects are GUI-only state.
            shapes.put("makeo.gadomancy.common.crafting.RecipeVisualStickyJar",new String[0]);
            ordinaryDefinitions=new NativeDefinitionGraph(()->new Object[]{ordinarySources},shapes);
        }
        if(family==Family.INFUSION){inheritance=new InfusionInheritance();runicCharges=new RunicChargeSource();}
        wands = replacements.isEmpty() ? null : new Wands(replacements,examples);
        creative = Wands.creative();
        }catch(RuntimeException|Error failure){close();throw failure;}
    }

    public int size() { alive();return recipes.size() + (wands == null ? 0 : wands.size()); }
    String sourceType(int index) {if(index>=recipes.size())return Wands.class.getName();Object source=recipes.get(index);if(source instanceof Runic)return ((Runic)source).recipe.getClass().getName();if(source instanceof DisguiseInfusionRecipes.Branch)return DisguiseInfusionRecipes.RECIPE;if(source instanceof NativeInfusionRecipes.Branch)return ((NativeInfusionRecipes.Branch)source).reader.sourceType();return source instanceof NativeArcaneRecipes.Branch?((NativeArcaneRecipes.Branch)source).reader.sourceType():source.getClass().getName();}

    public JsonObject exclusion(int index) {
        return index == excludedIndex && exclusion != null
                ? new com.google.gson.JsonParser().parse(exclusion.toString()).getAsJsonObject() : null;
    }
    private boolean exclude(int index, JsonObject proof) {
        if (proof == null) throw new Jobs.Fault("capture_empty", "Magic adapter omitted a native entry without proof");
        excludedIndex = index;
        exclusion = new com.google.gson.JsonParser().parse(proof.toString()).getAsJsonObject();
        exclusion.addProperty("nativeIndex", index);
        return false;
    }

    @SuppressWarnings("unchecked")
    public boolean capture(int index, RecipeRow row) {
        alive();
        excludedIndex = -1; exclusion = null;
        boolean replacement = index >= recipes.size();
        Object source = replacement ? null : recipes.get(index);
        if(source instanceof NativeArcaneRecipes.Branch){NativeArcaneRecipes.Branch branch=(NativeArcaneRecipes.Branch)source;return branch.reader.capture(branch.count,row,creative);}
        if(source instanceof NativeInfusionRecipes.Branch){NativeInfusionRecipes reader=((NativeInfusionRecipes.Branch)source).reader;return reader.capture(row,inheritance)||exclude(index,reader.exclusion());}
        if(source instanceof DisguiseInfusionRecipes.Branch){DisguiseInfusionRecipes.Branch branch=(DisguiseInfusionRecipes.Branch)source;return branch.reader.capture(branch.position,row,inheritance);}
        if(source instanceof Runic)return ((Runic)source).capture(row,inheritance,runicCharges,runicReturns);
        if(source!=null&&source.getClass().getName().equals(DisguiseInfusionRecipes.VISUAL))
            return exclude(index,object("reason","native_display_only_recipe","native",DisguiseInfusionRecipes.VISUAL,"source","Gadomancy InfusionVisualDisguiseArmor is a clock/player/RNG projection; the registered InfusionDisguiseArmor is captured without its cache"));
        if(source!=null&&source.getClass().getName().equals("makeo.gadomancy.common.crafting.RecipeVisualStickyJar"))
            return exclude(index,object("reason","native_display_only_recipe","native",source.getClass().getName(),"source","Gadomancy RecipeVisualStickyJar#getAspects updates GUI-only examples; real RecipeStickyJar is captured separately"));
        List<List<Candidate>> inputs = new ArrayList<>();
        TreeSet<String> research = new TreeSet<>();
        AspectList aspects;
        ItemStack output = null;
        Products product = null;
        JsonObject payment = null;
        String kind;
        Integer central = null, instability = null;
        if (replacement) {
            Wands.Result result = wands.capture(index - recipes.size(), row.facts);
            if (result == null) return exclude(index, wands.exclusion(index - recipes.size()));
            inputs = result.inputs; aspects = result.aspects; research = result.research; product = result.product;
            output = product.output; payment = result.payment; kind = "arcane";
            row.record.add("process",result.process);
            if (result.fixed) row.note("salisarcana:effect",sourceType(index),row.facts.locale().equals("zh_CN")?"手环按元数据读取固定组件；原生替换仍写入组件标签，并按合成费用重设元数据，后续有效组件可能随之变化。":"Bracelet components are read from metadata. Native replacement writes part tags and resets metadata to the crafting cost; its subsequent effective components may therefore change.");
        } else if (family == Family.SHAPED) {
            if(source.getClass()!=ShapedArcaneRecipe.class&&!source.getClass().getName().equals("makeo.gadomancy.common.crafting.RecipeStickyJar"))throw fault("Unknown shaped arcane inventory callback owner: "+source.getClass().getName());
            IArcaneRecipe arcane = (IArcaneRecipe) source;
            aspects = arcane.getAspects(); addResearch(research, arcane.getResearch());
            int width = 3, height = 3;
            Object[] inputArr = null;
            boolean mirror = false;
            boolean isStickyJar = source.getClass().getName().equals("makeo.gadomancy.common.crafting.RecipeStickyJar");
            List<ItemStack> jarChoices = null;
            if (isStickyJar) {
                if(stickySource==null)stickySource=new GadomancyJarSource();
                List<Candidate> candidates=stickySource.choices(examples);
                if(candidates.isEmpty())return exclude(index,object("reason","native_sticky_jar_registry_empty","native",source.getClass().getName()));
                jarChoices=new ArrayList<>();for(Candidate candidate:candidates)jarChoices.add(candidate.item.copy());

                product = Products.stickyJar(jarChoices, row.facts);
                output = product.output;
                inputArr = new Object[] {jarChoices.toArray(new ItemStack[0]),new ItemStack(Items.slime_ball)};
                width = 1; height = 2;
                row.record.add("process",object("kind","stickyJar","nativeRecipe","makeo.gadomancy.common.crafting.RecipeStickyJar","jars",stickySource.predicates()));
                inputs.add(candidates);inputs.add(java.util.Collections.singletonList(new Candidate(new ItemStack(Items.slime_ball),object("kind","wildcard","meta",true,"nbt",true))));
            } else {
                output = result(arcane.getRecipeOutput());
                if (source instanceof ShapedArcaneRecipe) {
                    ShapedArcaneRecipe recipe = (ShapedArcaneRecipe) source;
                    width = recipe.width; height = recipe.height; inputArr = recipe.input;
                    mirror = (Boolean) field(recipe, "mirrored");
                } else throw fault("Arcane inventory rules require a dedicated adapter: "+source.getClass().getName());
            }
            if (inputArr == null) throw fault("Unsupported arcane recipe inputs: " + source.getClass().getName());
            if (width < 1 || height < 1 || width > 3 || height > 3 || inputArr.length != width * height) {
                throw fault("Invalid native arcane grid");
            }
            JsonArray cells = new JsonArray();
            for (int cellIndex = 0; cellIndex < inputArr.length; cellIndex++) {
                Object ingredient = inputArr[cellIndex];
                if (ingredient == null) cells.add(value(null));
                else {
                    if(isStickyJar){cells.add(value(cellIndex));continue;}
                    cells.add(value(inputs.size()));
                    inputs.add(ordinary(ingredient, true));
                }
            }
            row.record.add("grid", object("width", width, "height", height, "cells", cells, "mirror", mirror));
            kind = "arcane";
        } else if (family == Family.SHAPELESS) {
            if(source.getClass()!=ShapelessArcaneRecipe.class&&!source.getClass().getName().equals("tuhljin.automagy.lib.recipe.PreserveFilterRecipe"))throw fault("Unknown shapeless arcane inventory callback owner: "+source.getClass().getName());
            if (!(source instanceof ShapelessArcaneRecipe)) {
                throw fault("Not a shapeless arcane recipe: " + source.getClass().getName());
            }
            ShapelessArcaneRecipe recipe = (ShapelessArcaneRecipe) source;
            aspects = recipe.getAspects(); output = result(recipe.getRecipeOutput()); addResearch(research, recipe.getResearch());
            for (Object ingredient : recipe.getInput()) {
                inputs.add(ordinary(ingredient, true));
            }
            if (source.getClass()!=ShapelessArcaneRecipe.class) {
                int[] roles = Products.filterRoles(inputs);
                if (roles[0] >= 0) {
                    product = Products.preserveFilter(source, inputs, roles[0], roles[1], row.facts);
                    output = product.output;
                }
            }
            if (output == null) output = result(recipe.getRecipeOutput());
            kind = "arcane";
        } else if (family == Family.CRUCIBLE) {
            exact(source, CrucibleRecipe.class);
            CrucibleRecipe recipe = (CrucibleRecipe) source;
            aspects = recipe.aspects; output = result(recipe.getRecipeOutput()); addResearch(research, recipe.key);
            inputs.add(ordinary(recipe.catalyst, false));
            kind = "crucible";
        } else {
            throw fault("Native infusion execution needs a source-specific reader: " + source.getClass().getName());
        }
        if (inputs.stream().mapToInt(List::size).sum() > 65536) throw fault("Recipe alternatives exceed their budget");
        if (!replacement && kind.equals("arcane") && output.getItem() instanceof ItemWandCasting) {
            ItemWandCasting wand = (ItemWandCasting) output.getItem();
            if (wand.getRod(output) == null || wand.getCap(output) == null) throw fault("Wand recipe has no rod or cap");
            addResearch(research, wand.getRod(output).getResearch()); addResearch(research, wand.getCap(output).getResearch());
            if (wand.isSceptre(output)) addResearch(research, "SCEPTRE");
        }
        JsonArray links = new JsonArray();
        for (String key : research) links.add(Magic.study(key));
        JsonArray amounts = Aspects.amounts(costs(aspects));
        if (kind.equals("arcane") && amounts.size() == 0 && !creative)
            return exclude(index, object("reason", "native_arcane_empty_aspects_without_creative_waiver", "native", sourceType(index), "creative", false));
        row.record.add("magic", object("kind", kind, "aspects", amounts, "research", links, "central", central, "instability", instability,
                "payment", payment, "creative", kind.equals("arcane") && creative));
        for(int slot=0;slot<inputs.size();slot++){
            List<RecipeRow.Ingredient> choices=new ArrayList<>();
            for(Candidate candidate:inputs.get(slot)){
                // Salis' component maps use literal metadata (including 32767).
                // Its screw permutations are already concrete owned specimens.
                ItemStack[] samples=replacement?new ItemStack[]{candidate.item.copy()}:examples.copies(candidate.item);
                for(ItemStack example:samples)choices.add(new RecipeRow.Ingredient(example,1,false,candidate.rule));
            }
            if(kind.equals("arcane"))row.nativeCraftingInput(slot,choices);else row.itemInput(slot,choices,false);
        }
        row.itemOutput(0, output, 10000);
        if (product != null) product.attach(row);
        return true;
    }

    public void verify(){
        alive();
        if(ThaumcraftApi.getCraftingRecipes()!=registry||registry.size()!=registrations.size()
                ||field(Aspect.class,null,"aspects")!=aspectRegistry||Wands.creative()!=creative)
            throw new Jobs.Fault("recipe_changed","Thaumcraft source or creative payment changed");
        for(NativeArcaneRecipes reader:nativeArcane)reader.verify();
        if(legacyInfusion!=null)legacyInfusion.verify();
        if(stickySource!=null)stickySource.verify();
        if(ordinaryDefinitions!=null)ordinaryDefinitions.verifyRoots();
        if(wands!=null)wands.verify();
        if(inheritance!=null)inheritance.verify();
        if(runicCharges!=null)runicCharges.verify();
        for(Runic.Source source:runicSources)source.verify();
    }
    public void finishVerification(){
        verify();examples.verify();
        for(int i=0;i<registry.size();i++)if(registry.get(i)!=registrations.get(i))
            throw new Jobs.Fault("recipe_changed","Thaumcraft native registration changed");
        for(NativeArcaneRecipes reader:nativeArcane)reader.finishVerification();
        for(NativeInfusionRecipes reader:nativeInfusion)reader.finishVerification();
        for(DisguiseInfusionRecipes reader:disguiseInfusion)reader.finishVerification();
        if(legacyInfusion!=null)legacyInfusion.finishVerification();
        if(stickySource!=null)stickySource.finishVerification();
        if(ordinaryDefinitions!=null)ordinaryDefinitions.verify();
        if(wands!=null)wands.finishVerification();
        if(inheritance!=null)inheritance.finishVerification();
        if(runicCharges!=null)runicCharges.finishVerification();
        for(Runic.Source source:runicSources)source.finishVerification();
        for(Runic.ContainerReturn returned:runicReturns)returned.verify();
    }
    private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Thaumcraft reader is closed");}
    public void close(){
        closed=true;recipes.clear();registrations.clear();registry=null;aspectRegistry=null;
        for(NativeArcaneRecipes reader:nativeArcane)reader.close();nativeArcane.clear();
        for(NativeInfusionRecipes reader:nativeInfusion)reader.close();nativeInfusion.clear();
        for(DisguiseInfusionRecipes reader:disguiseInfusion)reader.close();disguiseInfusion.clear();
        if(legacyInfusion!=null)legacyInfusion.close();legacyInfusion=null;
        if(stickySource!=null)stickySource.close();stickySource=null;
        if(ordinaryDefinitions!=null)ordinaryDefinitions.close();ordinarySources.clear();
        if(wands!=null)wands.close();wands=null;
        if(inheritance!=null)inheritance.close();inheritance=null;
        if(runicCharges!=null)runicCharges.close();runicCharges=null;
        for(Runic.Source source:runicSources)source.close();runicSources.clear();
        runicReturns.clear();
        examples.close();exclusion=null;
    }

    static JsonObject tags(ItemStack item, boolean meta) {
        TreeSet<String> keys = new TreeSet<>();
        if (item.hasTagCompound()) for (Object key : item.getTagCompound().func_150296_c()) keys.add((String) key);
        JsonArray names = new JsonArray(); for (String key : keys) names.add(value(key));
        return object("kind", "tags", "meta", meta, "keys", names, "present", new JsonArray(), "absent", new JsonArray());
    }

    private static List<Candidate> ordinary(Object ingredient, boolean tags) {
        List<Candidate> result = new ArrayList<>();
        List<?> values = ingredient instanceof ItemStack ? java.util.Collections.singletonList(ingredient)
                : ingredient instanceof ItemStack[] ? Arrays.asList((ItemStack[]) ingredient)
                : ingredient instanceof List<?> ? (List<?>) ingredient : null;
        if (values == null || values.isEmpty() || values.size() > 65536) throw fault("Unsupported or empty magic ingredient");
        for (Object value : values) {
            if (!(value instanceof ItemStack) || ((ItemStack) value).getItem() == null) throw fault("Magic ingredient contains an invalid stack");
            ItemStack item = ((ItemStack) value).copy(); item.stackSize = 1;
            boolean wildcard = Items.feather.getDamage(item) == OreDictionary.WILDCARD_VALUE;
            result.add(new Candidate(item, tags ? tags(item, wildcard)
                    : object("kind", "wildcard", "meta", wildcard, "nbt", true)));
        }
        return result;
    }

    private static ItemStack result(ItemStack item) {
        if (item == null || item.getItem() == null || item.stackSize < 1) throw fault("Magic recipe has no concrete output");
        return item.copy();
    }
    private static AspectList costs(AspectList aspects) { if (aspects == null) throw fault("Magic recipe has no cost list"); return aspects.copy(); }
    private static void addResearch(TreeSet<String> keys, String key) { if (key != null && !key.isEmpty()) keys.add(key); }
    private static void exact(Object value, Class<?> type) {
        if (value.getClass() != type) throw new Jobs.Fault("recipe_unsupported", "Recipe overrides base semantics: " + value.getClass().getName());
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("magic_recipe", message); }
    static final class Candidate {
        final ItemStack item;
        final JsonObject rule;
        Candidate(ItemStack item, JsonObject rule) { this.item = item; this.rule = rule; }
    }
}
