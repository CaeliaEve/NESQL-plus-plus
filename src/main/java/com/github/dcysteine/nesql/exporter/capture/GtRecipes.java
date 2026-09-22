package com.github.dcysteine.nesql.exporter.capture;

import bartworks.neiHandler.BioLabNEIHandler;
import bartworks.neiHandler.BioVatNEIHandler;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.PositionedStack;
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

    void capture(GTNEIDefaultHandler.CachedDefaultRecipe cached, RecipeRow row) {
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
                displayed(display, ingredients);
                row.itemInput(display, binding.index, ingredients, false);
            }
        }
        for (Placement placement : projectedOutputs) {
            PositionedStack display = placement.display;
            Binding binding = placement.binding;
            if (!captured.add(binding.identity())) throw new Jobs.Fault("slot_conflict", "GT recipe repeats an output binding");
            if (binding.fluid) row.fluidOutput(display, binding.index, (FluidStack) placement.source, quantities.get(binding.index));
            else row.itemOutput(display, binding.index, (ItemStack) placement.source, recipe.getOutputChance(binding.index));
        }
        BasicUIProperties ui = handler.getRecipeMap().getFrontend().getUIProperties();
        covered(captured, itemInputs, recipe.mInputs, ui.maxItemInputs, false, true, null);
        boolean hidden = handler.getRecipeMap().getFrontend().getClass().getName().equals("gtPlusPlus.api.recipe.ZhuhaiFrontend");
        covered(captured, itemOutputs, recipe.mOutputs, ui.maxItemOutputs, false, false,
                hidden ? (slot, value) -> row.itemOutput(null, slot, (ItemStack) value, recipe.getOutputChance(slot)) : null);
        covered(captured, fluidInputs, recipe.mFluidInputs, ui.maxFluidInputs, true, true, null);
        covered(captured, fluidOutputs, recipe.mFluidOutputs, ui.maxFluidOutputs, true, false, null);
    }

    private Map<Integer, JsonObject> quantities(GTRecipe recipe) {
        if (!handler.getRecipeMap().getFrontend().getClass().getName().equals("gtPlusPlus.api.recipe.SpargeTowerFrontend")) {
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

    static void displayed(PositionedStack display, List<RecipeRow.Ingredient> ingredients) {
        if (display.items.length != ingredients.size()) throw new Jobs.Fault("slot_changed", "GT input expansion changed its alternative count");
        for (int index = 0; index < ingredients.size(); index++) {
            ItemStack shown = display.items[index], expected = ingredients.get(index).display;
            // FixedPositionedStack may render every quantity as 1. Item/meta/NBT
            // order still has to agree with the native combined expansion.
            if (shown == null || shown.getItem() != expected.getItem() || Items.feather.getDamage(shown) != Items.feather.getDamage(expected)
                    || !ItemStack.areItemStackTagsEqual(shown, expected)) {
                throw new Jobs.Fault("slot_changed", "GT input expansion changed alternative " + index);
            }
        }
    }

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
