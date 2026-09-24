package com.github.dcysteine.nesql.exporter.capture;

import bartworks.neiHandler.BioLabNEIHandler;
import bartworks.neiHandler.BioVatNEIHandler;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.PositionedStack;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.drawable.IDrawable;
import com.gtnewhorizons.modularui.api.math.Pos2d;
import com.gtnewhorizons.modularui.api.widget.Widget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import cpw.mods.fml.relauncher.ReflectionHelper;
import gregtech.api.recipe.NEIRecipeProperties;
import gregtech.api.recipe.BasicUIProperties;
import gregtech.api.enums.SteamVariant;
import gregtech.api.recipe.RecipeMetadataKey;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeConstants;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.nei.GTNEIDefaultHandler;
import gregtech.common.gui.modularui.UIHelper;
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

/** GTNH 2.8.4 adapter: inventory bindings define slot roles; display items never define fluid identity. */
final class GtRecipes implements AutoCloseable {
    private final GTNEIDefaultHandler handler;
    private final List<Binding> bindings = new ArrayList<>();
    private final ModularWindow window;
    final Ui ui;

    static boolean supports(ICraftingHandler handler) {
        // Audited against 2.8.4: these two BartWorks subclasses only specialize item lookups.
        return handler.getClass() == GTNEIDefaultHandler.class || handler.getClass() == BioLabNEIHandler.class
                || handler.getClass() == BioVatNEIHandler.class;
    }

    GtRecipes(GTNEIDefaultHandler handler, boolean views, String location) {
        this.handler = handler;
        window = field("modularWindow");
        try {
            Object itemInputs = field("itemInputsInventory"), itemOutputs = field("itemOutputsInventory");
            Object fluidInputs = field("fluidInputsInventory"), fluidOutputs = field("fluidOutputsInventory");
            Object special = field("specialSlotInventory");
            if (window.getChildren().size() > 4096) throw new Jobs.Fault("view_limit", "GT window exceeds 4096 widgets");
            // Pinned CachedDefaultRecipe visits the direct children in this order,
            // then appends overflow slots. Screen positions are not slot identities.
            for (Widget widget : window.getChildren()) {
                if (!(widget instanceof SlotWidget)) continue;
                SlotWidget slot = (SlotWidget) widget;
                Object inventory = slot.getMcSlot().getItemHandler();
                boolean input = inventory == itemInputs || inventory == fluidInputs || inventory == special;
                boolean fluid = inventory == fluidInputs || inventory == fluidOutputs;
                if (!input && inventory != itemOutputs && inventory != fluidOutputs) continue;
                bindings.add(new Binding(slot.getMcSlot().getSlotIndex(), fluid, inventory == special, false, input,
                        widget.getPos().x + 1, widget.getPos().y + 1));
            }
            ui = views ? new Ui(handler, location) : null;
        } catch (RuntimeException | Error failure) {
            try { Ui.destroy(window); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    @Override public void close() {
        try { if (ui != null) ui.close(); }
        finally { Ui.destroy(window); }
    }

    void foreground(int index, RecipeRow row) {
        GTNEIDefaultHandler.CachedDefaultRecipe cached = (GTNEIDefaultHandler.CachedDefaultRecipe) handler.arecipes.get(index);
        GTRecipe nativeRecipe = cached.mRecipe;
        int duration = row.record.get("duration").getAsInt(), energy = row.record.get("energy").getAsInt();
        if (duration == nativeRecipe.mDuration && energy == nativeRecipe.mEUt) {
            ui.context(nativeRecipe, () -> handler.drawForeground(index));
            return;
        }
        // This handler and its list belong to the capture. Never mutate the shared GT cache recipe.
        GTRecipe branch = nativeRecipe.copy(); branch.mDuration = duration; branch.mEUt = energy;
        try {
            handler.arecipes.set(index, handler.new CachedDefaultRecipe(branch));
            ui.context(branch, () -> handler.drawForeground(index));
        } finally { handler.arecipes.set(index, cached); }
    }

    List<RecipeRow> capture(GTNEIDefaultHandler.CachedDefaultRecipe cached, RecipeRow row) {
        GTRecipe recipe = cached.mRecipe;
        NEIRecipeProperties presentation = handler.getRecipeMap().getFrontend().getNEIProperties();
        Object[] itemInputs = inputs(recipe, presentation);
        ItemStack[] itemOutputs = presentation.itemOutputsGetter.apply(recipe);
        FluidStack[] fluidInputs = presentation.fluidInputsGetter.apply(recipe);
        FluidStack[] fluidOutputs = presentation.fluidOutputsGetter.apply(recipe);
        Map<Integer, JsonObject> quantities = quantities(recipe);
        List<Binding> slots = slots(recipe);
        java.util.function.Function<Binding, Object> source = binding -> {
            if (binding.special) return recipe.mSpecialItems;
            if (binding.fluid) return optional(binding.input
                    ? binding.overflow ? recipe.mFluidInputs : fluidInputs
                    : binding.overflow ? recipe.mFluidOutputs : fluidOutputs, binding.index);
            return optional(binding.input ? binding.overflow ? recipe.mInputs : itemInputs
                    : binding.overflow ? recipe.mOutputs : itemOutputs, binding.index);
        };
        List<Placement> projectedInputs = project(slots, source, cached.mInputs, true);
        List<Placement> projectedOutputs = project(slots, source, cached.mOutputs, false);
        String root = Scan.root(handler.getRecipeMap().unlocalizedName, recipe);
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
        if (root != null) return Scan.capture(root, recipe, projectedInputs, projectedOutputs, row);
        for (Placement placement : projectedInputs) {
            PositionedStack display = placement.display;
            Binding binding = placement.binding;
            if (!captured.add(binding.identity())) throw new Jobs.Fault("slot_conflict", "GT recipe repeats an input binding");
            if (binding.special) {
                int amount = fixed(display).realStackSize;
                requireAmount(amount);
                row.itemInput(display, 65535, Math.max(1, amount), true, false, object("kind", "exact"));
            } else if (binding.fluid) {
                FluidStack fluid = (FluidStack) placement.source;
                requireAmount(fluid.amount);
                row.fluidInput(display, binding.index, fluid);
            } else {
                List<RecipeRow.Ingredient> ingredients = ingredients(placement.source, recipe.isNBTSensitive,
                        item -> new PositionedStack(GTOreDictUnificator.getNonUnifiedStacks(item), display.relx, display.rely, true).items);
                displayed(binding.index, display, ingredients);
                row.itemInput(display, binding.index, ingredients, false);
            }
        }
        String mapName = handler.getRecipeMap().unlocalizedName;
        String frontendName = handler.getRecipeMap().getFrontend().getClass().getName();
        boolean isTreeFarm = "gtpp.recipe.treefarm".equals(mapName) || frontendName.contains("TreeFarm");
        boolean isEoh = "gt.recipe.eyeofharmony".equals(mapName) || frontendName.contains("EyeOfHarmony");
        for (Placement placement : projectedOutputs) {
            PositionedStack display = placement.display;
            Binding binding = placement.binding;
            if (!captured.add(binding.identity())) throw new Jobs.Fault("slot_conflict", "GT recipe repeats an output binding");
            JsonObject qty = quantities.get(binding.index);
            if (binding.fluid) {
                FluidStack fluid = (FluidStack) placement.source;
                String exactAmount = isEoh ? getEohFluidAmount(recipe, fluid, binding.index) : null;
                if (!isEoh && exactAmount == null && quantities.containsKey(binding.index)) {
                    JsonObject q = quantities.get(binding.index);
                    if (q != null && q.has("nominal")) exactAmount = q.get("nominal").getAsString();
                }
                row.fluidOutput(display, binding.index, fluid, exactAmount, qty);
            } else {
                ItemStack item = (ItemStack) placement.source;
                if (item.stackSize < 0) {
                    throw new Jobs.Fault("invalid_quantity", "Negative GT output item stackSize: " + item.stackSize);
                }
                if (qty == null && item.stackSize == 0 && isTreeFarm) {
                    qty = getTreeFarmFruitPotential(recipe, item);
                }
                String exactAmount = isEoh ? getEohItemAmount(recipe, item, binding.index) : null;
                row.itemOutput(display, binding.index, item, recipe.getOutputChance(binding.index), exactAmount, qty);
            }
        }
        BasicUIProperties ui = handler.getRecipeMap().getFrontend().getUIProperties();
        covered(captured, itemInputs, recipe.mInputs, ui.maxItemInputs, false, true, null);
        boolean supportsUnbound = frontendName.equals("gtPlusPlus.api.recipe.ZhuhaiFrontend")
                || isEoh;
        covered(captured, itemOutputs, recipe.mOutputs, ui.maxItemOutputs, false, false,
                supportsUnbound ? (slot, value) -> {
                    ItemStack item = (ItemStack) value;
                    if (item.stackSize < 0) {
                        throw new Jobs.Fault("invalid_quantity", "Negative unbound GT output item stackSize: " + item.stackSize);
                    }
                    JsonObject qty = quantities.get(slot);
                    if (qty == null && item.stackSize == 0 && isTreeFarm) {
                        qty = getTreeFarmFruitPotential(recipe, item);
                    }
                    String exactAmount = isEoh ? getEohItemAmount(recipe, item, slot) : null;
                    row.itemOutput(null, slot, item, recipe.getOutputChance(slot), exactAmount, qty);
                } : null);
        covered(captured, fluidInputs, recipe.mFluidInputs, ui.maxFluidInputs, true, true, null);
        covered(captured, fluidOutputs, recipe.mFluidOutputs, ui.maxFluidOutputs, true, false,
                supportsUnbound ? (slot, value) -> {
                    FluidStack fluid = (FluidStack) value;
                    String exactAmount = isEoh ? getEohFluidAmount(recipe, fluid, slot) : null;
                    if (!isEoh && exactAmount == null && quantities.containsKey(slot)) {
                        JsonObject q = quantities.get(slot);
                        if (q != null && q.has("nominal")) exactAmount = q.get("nominal").getAsString();
                    }
                    row.fluidOutput(null, slot, fluid, exactAmount, quantities.get(slot));
                } : null);
        return java.util.Collections.singletonList(row);
    }

    private static JsonObject getTreeFarmFruitPotential(GTRecipe recipe, ItemStack item) {
        if (recipe.mInputs != null && recipe.mInputs.length > 0 && recipe.mInputs[0] != null) {
            ItemStack sapling = recipe.mInputs[0];
            try {
                Forestry.require();
                forestry.api.genetics.IIndividual ind = forestry.api.arboriculture.TreeManager.treeRoot.getMember(sapling);
                if (ind instanceof forestry.api.arboriculture.ITree) {
                    forestry.api.arboriculture.ITree tree = (forestry.api.arboriculture.ITree) ind;
                    forestry.api.arboriculture.ITreeGenome genome = tree.getGenome();
                    float yield = genome.getYield();
                    forestry.api.arboriculture.IAlleleTreeSpecies species = genome.getPrimary();
                    if (species != null && tree.canBearFruit()) {
                        long nominal = Math.max(1L, (long) Math.ceil(yield * 10.0f));
                        return Amounts.potential("forestry.yield", "species=" + species.getUID() + ";yield=" + yield, "0", nominal);
                    }
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

    private static String getEohItemAmount(GTRecipe recipe, ItemStack item, int slot) {
        if (recipe == null || recipe.mSpecialItems == null || item == null) {
            throw new Jobs.Fault("eoh_quantity_missing", "Eye of Harmony recipe missing mSpecialItems or item");
        }
        try {
            Class<?> eohClass = recipe.mSpecialItems.getClass();
            if (!eohClass.getName().contains("EyeOfHarmonyRecipe")) {
                throw new Jobs.Fault("eoh_quantity_missing", "Expected EyeOfHarmonyRecipe instance, got " + eohClass.getName());
            }
            java.lang.reflect.Method itemsMethod = eohClass.getMethod("getOutputItems");
            Object list = itemsMethod.invoke(recipe.mSpecialItems);
            if (!(list instanceof java.util.List<?>)) {
                throw new Jobs.Fault("eoh_quantity_missing", "Eye of Harmony getOutputItems did not return a List");
            }
            java.util.List<?> outputList = (java.util.List<?>) list;
            if (slot < 0 || slot >= outputList.size()) {
                throw new Jobs.Fault("eoh_quantity_missing", "EOH item slot index out of bounds: " + slot + ", size=" + outputList.size());
            }
            Object elem = outputList.get(slot);
            if (elem == null) {
                throw new Jobs.Fault("eoh_quantity_missing", "EOH item slot " + slot + " element is null");
            }
            java.lang.reflect.Field itemStackField = elem.getClass().getField("itemStack");
            ItemStack is = (ItemStack) itemStackField.get(elem);
            if (is == null) {
                throw new Jobs.Fault("eoh_quantity_missing", "EOH item slot " + slot + " itemStack is null");
            }
            // Strict identity verification: Item, meta, and full NBT must match (C6)
            if (is.getItem() != item.getItem() || is.getItemDamage() != item.getItemDamage()
                    || !ItemStack.areItemStackTagsEqual(is, item)) {
                throw new Jobs.Fault("eoh_quantity_missing", "EOH slot " + slot + " stack mismatch: expected "
                        + item.getUnlocalizedName() + "@" + item.getItemDamage() + ", got "
                        + is.getUnlocalizedName() + "@" + is.getItemDamage());
            }
            java.lang.reflect.Field sizeField = elem.getClass().getField("stackSize");
            long size = sizeField.getLong(elem);
            if (size <= 0) {
                throw new Jobs.Fault("eoh_quantity_missing", "EOH slot " + slot + " has non-positive stackSize: " + size);
            }
            return Long.toString(size);
        } catch (Jobs.Fault f) {
            throw f;
        } catch (Error e) {
            throw e;
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception t) {
            Jobs.Fault fault = new Jobs.Fault("eoh_quantity_missing", "Failed to inspect EOH item slot " + slot + ": " + t.getMessage());
            fault.initCause(t);
            throw fault;
        }
    }

    private static String getEohFluidAmount(GTRecipe recipe, FluidStack fluid, int slot) {
        if (recipe == null || recipe.mSpecialItems == null || fluid == null) {
            throw new Jobs.Fault("eoh_quantity_missing", "Eye of Harmony recipe missing mSpecialItems or fluid");
        }
        try {
            Class<?> eohClass = recipe.mSpecialItems.getClass();
            if (!eohClass.getName().contains("EyeOfHarmonyRecipe")) {
                throw new Jobs.Fault("eoh_quantity_missing", "Expected EyeOfHarmonyRecipe instance, got " + eohClass.getName());
            }
            java.lang.reflect.Method fluidsMethod = eohClass.getMethod("getOutputFluids");
            Object list = fluidsMethod.invoke(recipe.mSpecialItems);
            if (!(list instanceof java.util.List<?>)) {
                throw new Jobs.Fault("eoh_quantity_missing", "Eye of Harmony getOutputFluids did not return a List");
            }
            java.util.List<?> outputList = (java.util.List<?>) list;
            if (slot < 0 || slot >= outputList.size()) {
                throw new Jobs.Fault("eoh_quantity_missing", "EOH fluid slot index out of bounds: " + slot + ", size=" + outputList.size());
            }
            Object elem = outputList.get(slot);
            if (elem == null) {
                throw new Jobs.Fault("eoh_quantity_missing", "EOH fluid slot " + slot + " element is null");
            }
            java.lang.reflect.Field fStackField = elem.getClass().getField("fluidStack");
            Object fStack = fStackField.get(elem);
            if (!(fStack instanceof FluidStack)) {
                throw new Jobs.Fault("eoh_quantity_missing", "EOH fluid slot " + slot + " fluidStack is not FluidStack");
            }
            FluidStack fs = (FluidStack) fStack;
            if (fs.getFluid() != fluid.getFluid() || !fs.isFluidEqual(fluid)) {
                throw new Jobs.Fault("eoh_quantity_missing", "EOH slot " + slot + " fluid mismatch: expected "
                        + fluid.getLocalizedName() + ", got " + fs.getLocalizedName());
            }
            java.lang.reflect.Field amtField = elem.getClass().getField("amount");
            long amt = amtField.getLong(elem);
            if (amt <= 0) {
                throw new Jobs.Fault("eoh_quantity_missing", "EOH slot " + slot + " has non-positive fluid amount: " + amt);
            }
            return Long.toString(amt);
        } catch (Jobs.Fault f) {
            throw f;
        } catch (Error e) {
            throw e;
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception t) {
            Jobs.Fault fault = new Jobs.Fault("eoh_quantity_missing", "Failed to inspect EOH fluid slot " + slot + ": " + t.getMessage());
            fault.initCause(t);
            throw fault;
        }
    }

    private Map<Integer, JsonObject> quantities(GTRecipe recipe) {
        String mapName = handler.getRecipeMap().unlocalizedName;
        String frontendName = handler.getRecipeMap().getFrontend().getClass().getName();
        if ("gg.recipe.extreme_heat_exchanger".equals(mapName) || frontendName.contains("ExtremeHeatExchanger")) {
            Map<Integer, JsonObject> result = new java.util.LinkedHashMap<>();
            if (recipe.mFluidOutputs.length >= 2 && recipe.mFluidInputs.length >= 2 && recipe.mFluidInputs[1] != null) {
                if (recipe.mFluidInputs[1].amount <= 0 || recipe.mSpecialValue <= 0) {
                    throw new Jobs.Fault("invalid_heat_recipe", "Extreme heat exchanger recipe missing positive cold fluid input or threshold: water="
                            + recipe.mFluidInputs[1].amount + ", threshold=" + recipe.mSpecialValue);
                }
                int waterIn = recipe.mFluidInputs[1].amount;
                int thresholdVal = recipe.mSpecialValue;
                long normalRate = (long) waterIn * 160L;
                long heatedRate = (long) waterIn * 80L;
                String thresholdStr = Integer.toString(thresholdVal);
                if (recipe.mFluidOutputs[0] != null) {
                    long amt0 = recipe.mFluidOutputs[0].amount > 0 ? (long) recipe.mFluidOutputs[0].amount : normalRate;
                    result.put(0, Amounts.branch("steam_output", "normal", "tRealConsume < threshold", thresholdStr, amt0));
                }
                if (recipe.mFluidOutputs[1] != null) {
                    long amt1 = recipe.mFluidOutputs[1].amount > 0 ? (long) recipe.mFluidOutputs[1].amount : heatedRate;
                    result.put(1, Amounts.branch("steam_output", "superheated", "tRealConsume >= threshold", thresholdStr, amt1));
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

    private List<Binding> slots(GTRecipe recipe) {
        List<Binding> slots = new ArrayList<>(bindings);
        BasicUIProperties ui = handler.getRecipeMap().getFrontend().getUIProperties();
        Pos2d offset = field("WINDOW_OFFSET");
        // CachedDefaultRecipe adds these overflow stacks separately from the fixed ModularUI slots.
        UIHelper.forEachSlots(
                (index, backgrounds, position) -> overflow(slots, position, index, ui.maxItemInputs, false, true),
                (index, backgrounds, position) -> overflow(slots, position, index, ui.maxItemOutputs, false, false),
                (index, backgrounds, position) -> {},
                (index, backgrounds, position) -> overflow(slots, position, index, ui.maxFluidInputs, true, true),
                (index, backgrounds, position) -> overflow(slots, position, index, ui.maxFluidOutputs, true, false),
                IDrawable.EMPTY, IDrawable.EMPTY, ui, recipe.mInputs.length, recipe.mOutputs.length,
                recipe.mFluidInputs.length, recipe.mFluidOutputs.length, SteamVariant.NONE, offset);
        return slots;
    }

    private void overflow(List<Binding> slots, Pos2d position, int index, int fixed, boolean fluid, boolean input) {
        if (index >= fixed) slots.add(new Binding(index, fluid, false, true, input, position.x + 1, position.y + 1));
    }

    static List<Placement> project(List<Binding> slots, java.util.function.Function<Binding, Object> source,
                                   List<PositionedStack> displays, boolean input) {
        List<Placement> result = new ArrayList<>();
        for (Binding binding : slots) {
            if (binding.input != input) continue;
            Object value = source.apply(binding);
            if (value == null || value instanceof FluidStack && ((FluidStack) value).getFluid() == null) continue;
            if (result.size() >= displays.size()) throw new Jobs.Fault("slot_missing", "GT view omits native binding " + binding.identity());
            PositionedStack display = displays.get(result.size());
            if (display.relx != binding.x || display.rely != binding.y) {
                throw new Jobs.Fault("slot_changed", "GT native slot order or position changed: " + binding.identity());
            }
            result.add(new Placement(binding, value, display));
        }
        if (result.size() != displays.size()) throw new Jobs.Fault("slot_missing", "GT view contains a stack without a native binding");
        return result;
    }

    private static Object[] inputs(GTRecipe recipe, NEIRecipeProperties presentation) {
        if (!(recipe instanceof GTRecipe.GTRecipe_WithAlt)) return presentation.itemInputsGetter.apply(recipe);
        return inputs(recipe.mInputs, ((GTRecipe.GTRecipe_WithAlt) recipe).mOreDictAlt);
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

    static List<RecipeRow.Ingredient> ingredients(Object source, boolean sensitive,
                                                 java.util.function.Function<ItemStack, ItemStack[]> expand) {
        ItemStack[] alternatives = source instanceof ItemStack ? new ItemStack[] {(ItemStack) source}
                : source instanceof ItemStack[] ? (ItemStack[]) source : null;
        if (alternatives == null || alternatives.length == 0) throw new Jobs.Fault("empty_ingredient", "GT ingredient has no source alternatives");
        List<RecipeRow.Ingredient> result = new ArrayList<>();
        for (ItemStack item : alternatives) {
            Jobs.checkpoint();
            if (item == null || item.getItem() == null) throw new Jobs.Fault("empty_ingredient", "GT ingredient contains an empty source stack");
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

    static void displayed(int slot, PositionedStack display, List<RecipeRow.Ingredient> ingredients) {
        String location = "GT input slot=" + slot;
        if (display.items == null || display.items.length != ingredients.size()) {
            throw new Jobs.Fault("slot_changed", location + "; source alternatives=" + ingredients.size()
                    + "; cached alternatives=" + (display.items == null ? "null" : display.items.length));
        }
        // GT reuses mutable NEI display caches. Permutation order is presentation,
        // not a binding to a source candidate's quantity, consumption or predicate.
        // Native exact NBT matching distinguishes a missing compound from an empty one.
        // Tolerate that display-only difference only when every source predicate ignores NBT.
        boolean emptyTags = !ingredients.isEmpty() && ingredients.stream().allMatch(ingredient ->
                "wildcard".equals(ingredient.rule.get("kind").getAsString())
                        && ingredient.rule.has("nbt") && ingredient.rule.get("nbt").getAsBoolean());
        Map<String, Integer> remaining = new java.util.LinkedHashMap<>();
        for (RecipeRow.Ingredient ingredient : ingredients) {
            Jobs.checkpoint();
            remaining.merge(displayKey(ingredient.display, emptyTags), 1, Integer::sum);
        }
        for (int index = 0; index < display.items.length; index++) {
            Jobs.checkpoint();
            ItemStack shown = display.items[index];
            String key = displayKey(shown, emptyTags);
            Integer count = remaining.get(key);
            if (count == null) {
                String expected = "none";
                RecipeRow.Ingredient unmatchedIngredient = null;
                RecipeRow.Ingredient sameItemIngredient = null;
                String shownRegistry = shown != null && shown.getItem() != null
                        ? Item.itemRegistry.getNameForObject(shown.getItem()) : null;
                int shownMeta = shown != null ? Items.feather.getDamage(shown) : 0;
                for (RecipeRow.Ingredient ingredient : ingredients) {
                    if (remaining.containsKey(displayKey(ingredient.display, emptyTags))) {
                        if (unmatchedIngredient == null) unmatchedIngredient = ingredient;
                        if (sameItemIngredient == null && ingredient.display != null && ingredient.display.getItem() != null
                                && shownRegistry != null && shownRegistry.equals(Item.itemRegistry.getNameForObject(ingredient.display.getItem()))
                                && shownMeta == Items.feather.getDamage(ingredient.display)) {
                            sameItemIngredient = ingredient;
                            break;
                        }
                    }
                }
                RecipeRow.Ingredient reported = sameItemIngredient != null ? sameItemIngredient : unmatchedIngredient;
                if (reported != null) expected = describe(reported.display);
                String diff = reported != null && sameItemIngredient != null ? nbtDiff(shown, reported.display) : null;
                throw new Jobs.Fault("slot_changed", location + "; cached alternative=" + index
                        + " is absent from the source or repeated too often: " + describe(shown)
                        + "; unmatched source example: " + expected
                        + (diff == null ? "" : "; " + diff));
            }
            if (count == 1) remaining.remove(key); else remaining.put(key, count - 1);
        }
    }

    private static String displayKey(ItemStack stack, boolean emptyTags) {
        if (stack == null || stack.getItem() == null) return "empty";
        String registry = Item.itemRegistry.getNameForObject(stack.getItem());
        if (registry == null || Item.itemRegistry.getObject(registry) != stack.getItem()) {
            throw new Jobs.Fault("unregistered_item", "GT display uses an unregistered item");
        }
        // This projection never changes item facts, source predicates or source amounts.
        // Nonempty tags always retain their complete typed identity, even for an NBT-ignoring input.
        net.minecraft.nbt.NBTTagCompound tag = stack.getTagCompound();
        com.google.gson.JsonElement nbt = tag == null || emptyTags && tag.hasNoTags() ? null : TypedNbt.encode(tag);
        return Identity.item(registry, Items.feather.getDamage(stack), nbt);
    }

    private static String describe(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return "empty";
        return Item.itemRegistry.getNameForObject(stack.getItem()) + "; meta=" + Items.feather.getDamage(stack)
                + "; id=" + displayKey(stack, false);
    }

    private static String nbtDiff(ItemStack cached, ItemStack source) {
        if (cached == null || source == null) return null;
        net.minecraft.nbt.NBTTagCompound tagA = cached.getTagCompound();
        net.minecraft.nbt.NBTTagCompound tagB = source.getTagCompound();
        boolean emptyA = tagA == null || tagA.hasNoTags();
        boolean emptyB = tagB == null || tagB.hasNoTags();
        if (emptyA && emptyB) return tagA == tagB ? null : "nbt diff: [root: cached="
                + (tagA == null ? "absent" : "compound={}") + " vs source="
                + (tagB == null ? "absent" : "compound={}") + "]";
        java.util.Set<String> keys = new java.util.TreeSet<>();
        if (!emptyA) for (Object key : tagA.func_150296_c()) keys.add((String) key);
        if (!emptyB) for (Object key : tagB.func_150296_c()) keys.add((String) key);
        List<String> diffs = new ArrayList<>();
        int totalDiffs = 0;
        for (String key : keys) {
            net.minecraft.nbt.NBTBase valA = !emptyA && tagA.hasKey(key) ? tagA.getTag(key) : null;
            net.minecraft.nbt.NBTBase valB = !emptyB && tagB.hasKey(key) ? tagB.getTag(key) : null;
            if (valA == null && valB != null) {
                totalDiffs++;
                if (diffs.size() < 3) diffs.add(brief(key) + ": cached=absent vs source=" + describeTag(valB));
            } else if (valA != null && valB == null) {
                totalDiffs++;
                if (diffs.size() < 3) diffs.add(brief(key) + ": cached=" + describeTag(valA) + " vs source=absent");
            } else if (valA != null && valB != null && !valA.equals(valB)) {
                totalDiffs++;
                if (diffs.size() < 3) diffs.add(brief(key) + ": cached=" + describeTag(valA) + " vs source=" + describeTag(valB));
            }
        }
        if (diffs.isEmpty()) return null;
        String result = "nbt diff: [" + String.join(", ", diffs) + "]";
        if (totalDiffs > 3) result += " (+" + (totalDiffs - 3) + " more)";
        return result;
    }

    private static String describeTag(net.minecraft.nbt.NBTBase tag) {
        if (tag == null) return "absent";
        int id = tag.getId();
        String typeName = id >= 0 && id < TAG_TYPES.length ? TAG_TYPES[id] : ("type_" + id);
        String text;
        // Summarize containers instead of formatting an entire subtree or array before truncation.
        switch (id) {
            case 7: text = "length=" + ((net.minecraft.nbt.NBTTagByteArray) tag).func_150292_c().length + " (values omitted)"; break;
            case 8: text = brief(((net.minecraft.nbt.NBTTagString) tag).func_150285_a_()); break;
            case 9: text = "length=" + ((net.minecraft.nbt.NBTTagList) tag).tagCount() + " (values omitted)"; break;
            case 10: text = "keys=" + ((net.minecraft.nbt.NBTTagCompound) tag).func_150296_c().size() + " (values omitted)"; break;
            case 11: text = "length=" + ((net.minecraft.nbt.NBTTagIntArray) tag).func_150302_c().length + " (values omitted)"; break;
            default: text = tag.toString(); break; // Primitive numeric tags only; no recursive rendering.
        }
        return typeName + "=" + text;
    }

    private static String brief(String text) {
        return value(text.length() > 32 ? text.substring(0, 32) + "..." : text).toString();
    }

    private static final String[] TAG_TYPES = {
            "end", "byte", "short", "int", "long", "float", "double", "byte_array", "string", "list", "compound", "int_array"
    };

    static void covered(Set<String> captured, Object[] shown, Object[] source, int fixed, boolean fluid, boolean input,
                        java.util.function.BiConsumer<Integer, Object> omitted) {
        int length = Math.max(Math.min(shown.length, fixed), source.length);
        for (int index = 0; index < length; index++) {
            Object value = index < fixed ? index < shown.length ? shown[index] : null : index < source.length ? source[index] : null;
            if (value != null && !captured.contains(new Binding(index, fluid, false, false, input, 0, 0).identity())) {
                if (omitted == null) throw new Jobs.Fault("slot_missing", "GT recipe contains a value without an exported slot: " + index);
                omitted.accept(index, value);
                captured.add(new Binding(index, fluid, false, false, input, 0, 0).identity());
            }
        }
    }

    private static Object optional(Object[] values, int index) { return index >= 0 && index < values.length ? values[index] : null; }

    private static GTNEIDefaultHandler.FixedPositionedStack fixed(PositionedStack value) {
        if (!(value instanceof GTNEIDefaultHandler.FixedPositionedStack)) throw new Jobs.Fault("slot_type", "Unsupported GT display stack");
        return (GTNEIDefaultHandler.FixedPositionedStack) value;
    }

    private static void requireAmount(int amount) {
        if (amount < 0) throw new Jobs.Fault("invalid_amount", "GT recipe has a negative ingredient quantity");
    }

    private <T> T field(String name) { return ReflectionHelper.getPrivateValue(GTNEIDefaultHandler.class, handler, name); }
    static final class Binding {
        final int index, x, y;
        final boolean fluid, special, overflow, input;
        Binding(int index, boolean fluid, boolean special, boolean overflow, boolean input, int x, int y) {
            this.index = index; this.fluid = fluid; this.special = special; this.overflow = overflow; this.input = input;
            this.x = x; this.y = y;
        }
        String identity() { return input + "/" + fluid + "/" + special + "/" + index; }
    }

    static final class Placement {
        final Binding binding;
        final Object source;
        final PositionedStack display;
        Placement(Binding binding, Object source, PositionedStack display) { this.binding = binding; this.source = source; this.display = display; }
    }
}
