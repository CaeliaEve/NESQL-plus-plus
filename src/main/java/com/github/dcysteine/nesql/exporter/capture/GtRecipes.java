package com.github.dcysteine.nesql.exporter.capture;

import bartworks.neiHandler.BioLabNEIHandler;
import bartworks.neiHandler.BioVatNEIHandler;
import codechicken.nei.recipe.ICraftingHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import cpw.mods.fml.relauncher.ReflectionHelper;
import gregtech.api.recipe.NEIRecipeProperties;
import gregtech.api.recipe.RecipeCategory;
import gregtech.api.recipe.RecipeMetadataKey;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeConstants;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.nei.GTNEIDefaultHandler;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Pinned native semantic arrays define roles and quantities; no GUI/cache is constructed. */
final class GtRecipes implements RegistryRecipes, AutoCloseable {
    private final GTNEIDefaultHandler handler;
    private final GtRecipeSelection selection;
    private final List<ItemStack> panel;
    private final int panelSize;
    private final NativeItemExamples examples=new NativeItemExamples();

    static boolean supports(ICraftingHandler handler) {
        // Audited against 2.8.4: these two BartWorks subclasses only specialize item lookups.
        return handler.getClass() == GTNEIDefaultHandler.class || handler.getClass() == BioLabNEIHandler.class
                || handler.getClass() == BioVatNEIHandler.class;
    }

    GtRecipes(GTNEIDefaultHandler handler) {
        this(admitted(handler), () -> {
            if (gregtech.GTMod.proxy == null) throw new Jobs.Fault("recipe_unready", "GT native proxy is unavailable");
            return gregtech.GTMod.proxy.getNEIReloadCount();
        });
    }

    private static GTNEIDefaultHandler admitted(GTNEIDefaultHandler handler) {
        TargetAdmission.require("gt.recipes", "gregtech_nh");
        return handler;
    }

    GtRecipes(GTNEIDefaultHandler handler, java.util.function.IntSupplier reloadVersion) {
        this.handler = handler;
        panel = codechicken.nei.ItemList.items;
        if (!codechicken.nei.ItemList.loadFinished || panel == null)
            throw new Jobs.Fault("recipe_unready", "GT ingredient permutation requires the complete native item list");
        panelSize = panel.size();
        RecipeCategory category = ReflectionHelper.getPrivateValue(GTNEIDefaultHandler.class, handler, "recipeCategory");
        if (category.recipeMap != handler.getRecipeMap())
            throw new Jobs.Fault("handler_changed", "GT handler changed its native recipe map");
        selection = new GtRecipeSelection(category, reloadVersion);
    }

    @Override public int size() { return selection.size(); }
    @Override public void verify() {
        selection.verify();
        if (!codechicken.nei.ItemList.loadFinished || codechicken.nei.ItemList.items != panel || panel.size() != panelSize)
            throw new Jobs.Fault("recipe_changed", "GT native ingredient panel readiness or ownership changed");
    }
    @Override public void finishVerification() { verify(); selection.finishVerification();examples.verify(); }
    @Override public void close() { selection.close();examples.close(); }

    @Override public boolean capture(int index, RecipeRow row) {
        throw new IllegalStateException("GT semantic rows must use captureRows");
    }

    @Override public List<RecipeRow> captureRows(int index, RecipeRow row) {
        verify();
        GTRecipe recipe = selection.recipe(index);
        JsonObject proof = Replaced.inspect(recipe, row.record.getAsJsonObject("source").get("key").getAsString());
        if (proof != null) { row.exclude(proof); return java.util.Collections.emptyList(); }
        List<RecipeRow> result = capture(recipe, row);
        selection.recipe(index); // A semantic callback may not mutate the owned native definition.
        return result;
    }

    List<RecipeRow> capture(GTRecipe recipe, RecipeRow row) {
        NEIRecipeProperties presentation = handler.getRecipeMap().getFrontend().getNEIProperties();
        boolean harmony = "gt.recipe.eyeofharmony".equals(handler.getRecipeMap().unlocalizedName);
        String root = Scan.root(handler.getRecipeMap().unlocalizedName, recipe);
        boolean specialized = harmony || root != null;
        JsonObject nativeFacts = GtProbability.capture(recipe, row.facts, specialized);
        GtProbability.validateProjection(recipe);
        Object[] itemInputs = specialized ? inputs(recipe, presentation) : nativeInputs(recipe);
        ItemStack[] itemOutputs = specialized ? presentation.itemOutputsGetter.apply(recipe) : recipe.mOutputs;
        FluidStack[] fluidInputs = specialized ? presentation.fluidInputsGetter.apply(recipe) : recipe.mFluidInputs;
        FluidStack[] fluidOutputs = specialized ? presentation.fluidOutputsGetter.apply(recipe) : recipe.mFluidOutputs;
        Map<Integer, JsonObject> quantities = quantities(recipe);
        List<Placement> projectedInputs = placements(itemInputs, harmony ? new FluidStack[0] : fluidInputs,
                harmony ? null : recipe.mSpecialItems, true);
        List<Placement> projectedOutputs = placements(itemOutputs, fluidOutputs, null, false);
        Set<String> captured = new HashSet<>();
        row.record.addProperty("duration", Integer.toString(recipe.mDuration));
        row.record.addProperty("energy", Integer.toString(recipe.mEUt));
        row.property("gregtech:special", "Special value", recipe.mSpecialValue);
        row.property("gregtech:enabled", "Enabled", recipe.mEnabled);
        row.property("gregtech:fake", "Display recipe", recipe.mFakeRecipe);
        row.property("gregtech:amperage", "Amperage", handler.getRecipeMap().getAmperage());
        if (recipe.getNeiDesc() != null) row.property("gregtech:description", "Description", recipe.getNeiDesc());
        if (recipe.getMetadataStorage() != null) for (Map.Entry<RecipeMetadataKey<?>, Object> metadata : recipe.getMetadataStorage().getEntries()) {
            String name = ReflectionHelper.getPrivateValue(RecipeMetadataKey.class, metadata.getKey(), "identifier");
            Class<?> type = ReflectionHelper.getPrivateValue(RecipeMetadataKey.class, metadata.getKey(), "clazz");
            // GT identifies keys by both their declared type and name.
            row.property("gregtech:metadata/" + type.getName().replace('[', '_').replace(';', '_') + "/" + name, name, metadata.getValue());
        }
        if (specialized) {
            List<RecipeRow> rows = harmony ? Harmony.capture(recipe, projectedInputs, projectedOutputs, row)
                    : Scan.capture(root, recipe, projectedInputs, projectedOutputs, row,examples);
            for (RecipeRow branch : rows) branch.record.add("gtNative", nativeFacts);
            return rows;
        }
        row.record.add("gtNative", nativeFacts);
        for (Placement placement : projectedInputs) {
            Binding binding = placement.binding;
            if (!captured.add(binding.identity())) throw new Jobs.Fault("slot_conflict", "GT recipe repeats an input binding");
            if (binding.special) {
                row.itemInput(65535, special(placement.source), false);
            } else if (binding.fluid) {
                FluidStack fluid = (FluidStack) placement.source;
                requireAmount(fluid.amount);
                FluidStack[] alternatives = recipe.mAltFluidInputs != null && binding.index < recipe.mAltFluidInputs.length
                        ? recipe.mAltFluidInputs[binding.index] : null;
                // A nonnull empty native alternative array disables the slot; the UI getter
                // may instead fall back to a representative, so it is not used here.
                if (alternatives != null && alternatives.length == 0) continue;
                if (fluid.amount == 0) row.zeroFluidInput(binding.index, fluid,
                        "gtpp.recipe.multiblockrockbreaker".equals(handler.getRecipeMap().unlocalizedName)
                                ? "gregtechPresence" : "gregtechCost");
                else if (alternatives == null) row.fluidInput(binding.index, fluid, false,
                        object("kind", "wildcard", "meta", false, "nbt", true));
                else {
                    com.google.gson.JsonArray choices = array();
                    for (FluidStack alternative : alternatives) {
                        if (alternative == null || alternative.getFluid() == null || alternative.amount <= 0)
                            throw new Jobs.Fault("recipe_unsupported", "GT fluid alternative is null or nonpositive");
                        choices.add(object("id", row.facts.fluid(alternative), "amount", Integer.toString(alternative.amount),
                                "consume", object("kind", "consume"), "returns", array(),
                                "rule", object("kind", "wildcard", "meta", false, "nbt", true)));
                    }
                    row.inputs.add(object("slot", binding.index, "kind", "fluid", "choices", choices));
                }
                GtProbability.inputChance(row.inputs.get(row.inputs.size() - 1).getAsJsonObject(),
                        recipe.getFluidInputChance(binding.index));
            } else {
                List<RecipeRow.Ingredient> ingredients = ingredients(placement.source, recipe.isNBTSensitive,
                        item -> alternatives(item,examples));
                row.itemInput(binding.index, ingredients, false);
                GtProbability.inputChance(row.inputs.get(row.inputs.size() - 1).getAsJsonObject(),
                        recipe.getInputChance(binding.index));
            }
        }
        String mapName = handler.getRecipeMap().unlocalizedName;
        String frontendName = handler.getRecipeMap().getFrontend().getClass().getName();
        boolean isTreeFarm = "gtpp.recipe.treefarm".equals(mapName) || frontendName.contains("TreeFarm");
        for (Placement placement : projectedOutputs) {
            Binding binding = placement.binding;
            if (!captured.add(binding.identity())) throw new Jobs.Fault("slot_conflict", "GT recipe repeats an output binding");
            JsonObject qty = quantities.get(binding.index);
            if (binding.fluid) {
                FluidStack fluid = (FluidStack) placement.source;
                String exactAmount = null;
                if (exactAmount == null && quantities.containsKey(binding.index)) {
                    JsonObject q = quantities.get(binding.index);
                    if (q != null && q.has("nominal")) exactAmount = q.get("nominal").getAsString();
                }
                row.fluidOutput(binding.index, fluid, exactAmount, qty);
                row.outputs.get(row.outputs.size() - 1).getAsJsonObject().add("chance",
                        com.github.dcysteine.nesql.exporter.source.Chance.of(recipe.getFluidOutputChance(binding.index), 10000));
            } else {
                ItemStack item = (ItemStack) placement.source;
                if (item.stackSize < 0) {
                    throw new Jobs.Fault("invalid_quantity", "Negative GT output item stackSize: " + item.stackSize);
                }
                if (qty == null && item.stackSize == 0 && isTreeFarm) {
                    qty = getTreeFarmFruitPotential(recipe, item);
                }
                String exactAmount = null;
                row.itemOutput(binding.index, item, recipe.getOutputChance(binding.index), exactAmount, qty);
            }
        }
        return java.util.Collections.singletonList(row);
    }

    private static JsonObject getTreeFarmFruitPotential(GTRecipe recipe, ItemStack item) {
        ItemStack sapling = null;
        if (recipe.mSpecialItems instanceof ItemStack) {
            sapling = (ItemStack) recipe.mSpecialItems;
        } else if (recipe.mInputs != null && recipe.mInputs.length > 0 && recipe.mInputs[0] != null) {
            sapling = recipe.mInputs[0];
        }
        if (sapling != null) {
            try {
                Forestry.require();
                forestry.api.genetics.IIndividual ind = ForestryMembers.read(forestry.api.arboriculture.TreeManager.treeRoot,sapling);
                if (ind instanceof forestry.api.arboriculture.ITree) {
                    forestry.api.arboriculture.ITree tree = (forestry.api.arboriculture.ITree) ind;
                    forestry.api.arboriculture.ITreeGenome genome = tree.getGenome();
                    float yield = genome.getYield();
                    forestry.api.arboriculture.IAlleleTreeSpecies species = genome.getPrimary();
                    String speciesId = species != null ? species.getUID() : "unknown";
                    ItemStack produce = null;
                    for (ItemStack candidate : tree.getProduceList()) {
                        if (candidate != null && candidate.getItem() == item.getItem()
                                && candidate.getItemDamage() == item.getItemDamage()
                                && ItemStack.areItemStackTagsEqual(candidate, item)) {
                            produce = candidate;
                            break;
                        }
                    }
                    if (!tree.canBearFruit() || produce == null) {
                        return Amounts.potential("forestry.yield",
                                "species=" + speciesId + ";canBearFruit=false", "0", 0,
                                object("mode", "fruit", "modeMultiplier", "1", "tierFormula", "2*tier^2-2*tier+5", "base", "0"));
                    }
                    int baseSize = Math.max(0, produce.stackSize);
                    int truncated = (int) ((double) baseSize * (double) yield * 10.0);
                    return Amounts.potential("forestry.yield",
                            "species=" + speciesId + ";yield=" + yield + ";base=" + baseSize,
                            Integer.toString(Math.max(0, truncated)), Math.max(0, truncated),
                            object("mode", "fruit", "modeMultiplier", "1", "tierFormula", "2*tier^2-2*tier+5",
                                    "yieldScale", "10", "base", Integer.toString(baseSize), "species", speciesId));
                }
            } catch (Jobs.Fault f) {
                throw f;
            } catch (Error e) {
                throw e;
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception t) {
                Jobs.Fault fault = new Jobs.Fault("invalid_quantity", "Error extracting Forestry genetics for " + item.getUnlocalizedName() + ": " + t.getMessage());
                fault.initCause(t);
                throw fault;
            }
        }
        throw new Jobs.Fault("invalid_quantity", "Zero stackSize output without verifiable Forestry tree genetics: " + item.getUnlocalizedName());
    }

    private Map<Integer, JsonObject> quantities(GTRecipe recipe) {
        String mapName = handler.getRecipeMap().unlocalizedName;
        String frontendName = handler.getRecipeMap().getFrontend().getClass().getName();
        if ("gg.recipe.extreme_heat_exchanger".equals(mapName) || frontendName.contains("ExtremeHeatExchanger")) {
            Map<Integer, JsonObject> result = new java.util.LinkedHashMap<>();
            if (recipe.mFluidOutputs.length >= 3 && recipe.mFluidInputs.length >= 2 && recipe.mFluidInputs[0] != null && recipe.mFluidInputs[1] != null) {
                if (recipe.mFluidInputs[1].amount <= 0 || recipe.mSpecialValue <= 0) {
                    throw new Jobs.Fault("invalid_heat_recipe", "Extreme heat exchanger recipe missing positive cold fluid input or threshold: water="
                            + recipe.mFluidInputs[1].amount + ", threshold=" + recipe.mSpecialValue);
                }
                int thresholdVal = recipe.mSpecialValue;
                int eut = extremeHeatEnergy(recipe);
                int maxHot = extremeHeatMaxHot(recipe);
                if (eut < 0 || maxHot <= 0) {
                    throw new Jobs.Fault("invalid_heat_recipe", "Extreme heat exchanger recipe has invalid native energy or hot-fluid capacity: eut=" + eut + ";maxHot=" + maxHot);
                }
                boolean isPlasma = false;
                if (recipe.mFluidInputs[0] != null) {
                    String hotName = recipe.mFluidInputs[0].getUnlocalizedName();
                    if (hotName != null && hotName.toLowerCase().contains("plasma")) isPlasma = true;
                    if (recipe.mFluidInputs[0].getFluid() != null && recipe.mFluidInputs[0].getFluid().getName().toLowerCase().contains("plasma")) isPlasma = true;
                }
                // Native MTEExtremeHeatExchanger uses getEUt(), then truncates water and steam per tick.
                double normalPower = steamPower(recipe.mFluidOutputs[0]);
                double heatedPower = steamPower(recipe.mFluidOutputs[1]);
                long normalRate = extremeHeatSteam(eut, 1.0d, normalPower, isPlasma);
                long heatedRate = extremeHeatSteam(eut, 1.0d, heatedPower, isPlasma);
                if (normalRate <= 0 || heatedRate <= 0) {
                    throw new Jobs.Fault("invalid_heat_recipe", "Native extreme heat exchanger yields no steam: normal=" + normalRate + ";heated=" + heatedRate);
                }

                String thresholdStr = Integer.toString(thresholdVal);
                JsonObject parameters = object("maxHot", Integer.toString(maxHot),
                        "energy", Integer.toString(eut), "duration", "20",
                        "efficiency", "1", "hotUnit", "mb", "outputUnit", "mb",
                        "plasmaScale", isPlasma ? "1000" : "1",
                        "thresholdStep", "150", "circuitMin", "1", "circuitMax", "25");
                if (recipe.mFluidOutputs[0] != null) {
                    result.put(0, Amounts.branch("steam_output", "normal", "tRealConsume < threshold", thresholdStr, normalRate, parameters));
                }
                if (recipe.mFluidOutputs[1] != null) {
                    result.put(1, Amounts.branch("steam_output", "superheated", "tRealConsume >= threshold", thresholdStr, heatedRate, parameters));
                }
            }
            return result;
        }
        if (!frontendName.equals("gtPlusPlus.api.recipe.SpargeTowerFrontend")) {
            return java.util.Collections.emptyMap();
        }
        if (recipe.mFluidInputs.length < 1 || recipe.mFluidInputs[0] == null || recipe.mFluidOutputs.length < 2
                || recipe.mFluidOutputs[1] == null || !recipe.mFluidInputs[0].isFluidEqual(recipe.mFluidOutputs[1])) {
            throw new Jobs.Fault("quantity_rule", "Sparging remainder must return its source gas");
        }
        return Amounts.sparge(0, recipe.mFluidOutputs.length,
                recipe.getMetadataOrDefault(GTRecipeConstants.SPARGE_MAX_BYPRODUCT, 0), recipe.mFluidInputs[0].amount);
    }

    private static int extremeHeatEnergy(GTRecipe recipe) {
        try {
            java.lang.reflect.Method method = recipe.getClass().getMethod("getEUt");
            return ((Number) method.invoke(recipe)).intValue();
        } catch (ReflectiveOperationException error) {
            throw new Jobs.Fault("invalid_heat_recipe", "Extreme heat exchanger does not expose native getEUt(): " + error.getMessage());
        }
    }

    private static int extremeHeatMaxHot(GTRecipe recipe) {
        try {
            java.lang.reflect.Method method = recipe.getClass().getMethod("getMaxHotFluidConsume");
            return ((Number) method.invoke(recipe)).intValue();
        } catch (ReflectiveOperationException error) {
            throw new Jobs.Fault("invalid_heat_recipe", "Extreme heat exchanger does not expose native hot-fluid capacity: " + error.getMessage());
        }
    }

    private static double steamPower(FluidStack stack) {
        String name = stack == null || stack.getFluid() == null ? "" : stack.getFluid().getName();
        if ("steam".equals(name)) return 0.5d;
        if ("ic2superheatedsteam".equals(name) || "supercriticalsteam".equals(name) || "densesupercriticalsteam".equals(name)) return 1.0d;
        throw new Jobs.Fault("invalid_heat_recipe", "Unknown native steam output: " + name);
    }

    private static long extremeHeatSteam(int eut, double efficiency, double unitPower, boolean plasma) {
        int waterPerTick = (int) (eut * efficiency / unitPower) / 160;
        long amount = (long) waterPerTick * 160L * 20L;
        return plasma ? amount / 1000L : amount;
    }

    static List<Placement> placements(Object[] items, FluidStack[] fluids, Object special, boolean input) {
        if (items == null || fluids == null || items.length > (special == null ? 65536 : 65535)
                || fluids.length > 65536 || items.length + fluids.length > 65536)
            throw new Jobs.Fault("recipe_limit", "GT semantic slots are null or exceed their index/count budget");
        List<Placement> result = new ArrayList<>();
        for (int index = 0; index < items.length; index++) {
            Jobs.checkpoint();
            Object item = items[index];
            if (item != null) result.add(new Placement(new Binding(index, false, false, input),
                    item));
        }
        if (special != null) result.add(new Placement(new Binding(65535, false, true, input),
                special));
        for (int index = 0; index < fluids.length; index++) {
            Jobs.checkpoint();
            FluidStack fluid = fluids[index];
            if (fluid == null) continue;
            if (fluid.getFluid() == null) throw new Jobs.Fault("recipe_invalid", "GT native fluid has no registry identity");
            result.add(new Placement(new Binding(index, true, false, input), fluid));
        }
        return result;
    }

    private static List<RecipeRow.Ingredient> special(Object source) {
        // Native special-slot matching retains the offered stack; quantities belong to each candidate.
        ItemStack[] items;
        if (source instanceof ItemStack) items = new ItemStack[] {(ItemStack) source};
        else if (source instanceof ItemStack[]) items = (ItemStack[]) source;
        else if (source instanceof List<?>) {
            List<?> values = (List<?>) source;
            if (values.size() > 65536) throw new Jobs.Fault("recipe_limit", "GT special choices exceed 65536");
            items = new ItemStack[values.size()];
            for (int index = 0; index < items.length; index++) {
                if (!(values.get(index) instanceof ItemStack))
                    throw new Jobs.Fault("recipe_unsupported", "GT special input is not an item stack");
                items[index] = (ItemStack) values.get(index);
            }
        } else throw new Jobs.Fault("recipe_unsupported", "Unknown GT special input semantics: " + source.getClass().getName());
        if (items.length == 0 || items.length > 65536)
            throw new Jobs.Fault("empty_ingredient", "GT special input has no bounded candidates");
        List<RecipeRow.Ingredient> result = new ArrayList<>();
        for (ItemStack item : items) {
            Jobs.checkpoint();
            if (item == null || item.getItem() == null) throw new Jobs.Fault("empty_ingredient", "Empty GT special item");
            requireAmount(item.stackSize);
            result.add(new RecipeRow.Ingredient(item.copy(), Math.max(1, item.stackSize), true, object("kind", "exact")));
        }
        return result;
    }

    private static Object[] inputs(GTRecipe recipe, NEIRecipeProperties presentation) {
        if (!(recipe instanceof GTRecipe.GTRecipe_WithAlt)) return presentation.itemInputsGetter.apply(recipe);
        return inputs(recipe.mInputs, ((GTRecipe.GTRecipe_WithAlt) recipe).mOreDictAlt);
    }

    static Object[] nativeInputs(GTRecipe recipe) {
        if (!(recipe instanceof GTRecipe.GTRecipe_WithAlt)) return recipe.mInputs;
        ItemStack[][] alternatives = ((GTRecipe.GTRecipe_WithAlt) recipe).mOreDictAlt;
        if (alternatives == null) return recipe.mInputs;
        if (alternatives.length > recipe.mInputs.length)
            throw new Jobs.Fault("recipe_unsupported", "GT item alternatives extend beyond native quantity slots");
        for (int slot = 0; slot < alternatives.length; slot++)
            if (recipe.mInputs[slot] == null && alternatives[slot] != null && alternatives[slot].length != 0)
                throw new Jobs.Fault("recipe_unsupported", "GT alternatives have no native quantity representative");
        return inputs(recipe.mInputs, alternatives);
    }

    /** Same source choice/copy boundary as GTRecipe_WithAlt.getAltRepresentativeInput. */
    static Object[] inputs(ItemStack[] base, ItemStack[][] alternatives) {
        int size = Math.max(base.length, alternatives.length);
        if (size > 65536) throw new Jobs.Fault("recipe_limit", "GT ingredient slot count exceeds 65536");
        Object[] inputs = new Object[size];
        for (int index = 0; index < inputs.length; index++) {
            Jobs.checkpoint();
            ItemStack[] choices = index < alternatives.length ? alternatives[index] : null;
            if (choices != null && choices.length > 0) {
                if (choices.length > 65536) throw new Jobs.Fault("recipe_limit", "GT ingredient choices exceed 65536");
                ItemStack[] copies = new ItemStack[choices.length];
                for (int choice = 0; choice < copies.length; choice++) copies[choice] = copy(choices[choice]);
                inputs[index] = copies;
            } else if (index < base.length) inputs[index] = copy(base[index]);
        }
        return inputs;
    }

    private static ItemStack copy(ItemStack item) { return item == null ? null : item.copy(); }

    static ItemStack[] alternatives(ItemStack source,NativeItemExamples examples){
        List<ItemStack> expanded=new ArrayList<>();
        List<ItemStack> aliases=GTOreDictUnificator.getNonUnifiedStacks(source);
        if(aliases==null||aliases.size()>65536)throw new Jobs.Fault("recipe_limit","GT native aliases exceed budget");
        for(ItemStack alias:aliases){
            if(alias==null)continue;
            for(ItemStack item:examples.permutations(alias)){
                if(expanded.size()==65536)throw new Jobs.Fault("recipe_limit","GT native permutations exceed budget");expanded.add(item);
            }
        }
        if(expanded.isEmpty())throw new Jobs.Fault("empty_ingredient","GT native aliases have no item content");
        return expanded.toArray(new ItemStack[0]);
    }

    static List<RecipeRow.Ingredient> ingredients(Object source, boolean sensitive,
                                                 java.util.function.Function<ItemStack, ItemStack[]> expand) {
        ItemStack[] alternatives = source instanceof ItemStack ? new ItemStack[] {(ItemStack) source}
                : source instanceof ItemStack[] ? (ItemStack[]) source : null;
        if (alternatives == null || alternatives.length == 0) throw new Jobs.Fault("empty_ingredient", "GT ingredient has no source alternatives");
        List<RecipeRow.Ingredient> result = new ArrayList<>();
        for (ItemStack item : alternatives) {
            Jobs.checkpoint();
            if (item == null || item.getItem() == null) throw new Jobs.Fault("empty_ingredient", "GT ingredient contains an empty source stack");
            registeredName(item, "GT native source before unification");
            requireAmount(item.stackSize);
            boolean wildcard = Items.feather.getDamage(item) == OreDictionary.WILDCARD_VALUE;
            JsonObject rule = wildcard || !sensitive ? object("kind", "wildcard", "meta", wildcard, "nbt", !sensitive) : object("kind", "exact");
            ItemStack[] variants = expand.apply(item.copy());
            if (variants == null || variants.length == 0 || variants.length > 65536 - result.size()) {
                throw new Jobs.Fault("recipe_limit", "GT ingredient expansion is empty or exceeds 65536 choices");
            }
            for (ItemStack variant : variants) {
                if (variant == null || variant.getItem() == null) throw new Jobs.Fault("empty_ingredient", "GT expansion contains an empty stack");
                ItemStack candidate = variant.copy();
                // NEI's wildcard permutation replaces the stack with an ItemList
                // example; the native recipe's NBT predicate still belongs to its source.
                if (sensitive) candidate.setTagCompound(item.hasTagCompound() ? (net.minecraft.nbt.NBTTagCompound) item.getTagCompound().copy() : null);
                result.add(new RecipeRow.Ingredient(candidate, variant.copy(), Math.max(1, item.stackSize), item.stackSize == 0, rule));
            }
        }
        return result;
    }

    private static String registeredName(ItemStack stack, String location) {
        String registry = Item.itemRegistry.getNameForObject(stack.getItem());
        if (registry == null || Item.itemRegistry.getObject(registry) != stack.getItem()) {
            throw new Jobs.Fault("unregistered_item", location + "; unregistered " + itemContext(stack));
        }
        return registry;
    }

    private static String itemContext(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return "empty";
        // Do not invoke tooltip/name/render callbacks while reporting an invalid registry entry.
        return "item class=" + stack.getItem().getClass().getName() + "; registry="
                + Item.itemRegistry.getNameForObject(stack.getItem()) + "; meta=" + Items.feather.getDamage(stack)
                + "; amount=" + stack.stackSize;
    }

    private static void requireAmount(int amount) {
        if (amount < 0) throw new Jobs.Fault("invalid_amount", "GT recipe has a negative ingredient quantity");
    }

    static final class Binding {
        final int index;
        final boolean fluid, special, input;
        Binding(int index, boolean fluid, boolean special, boolean input) {
            this.index = index; this.fluid = fluid; this.special = special; this.input = input;
        }
        String identity() { return input + "/" + fluid + "/" + special + "/" + index; }
    }

    static final class Placement {
        final Binding binding;
        final Object source;
        Placement(Binding binding,Object source){this.binding=binding;this.source=source;}
    }
}
