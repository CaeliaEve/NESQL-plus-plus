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
import com.gtnewhorizons.modularui.api.widget.IWidgetParent;
import com.gtnewhorizons.modularui.api.widget.Widget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import cpw.mods.fml.relauncher.ReflectionHelper;
import gregtech.api.recipe.NEIRecipeProperties;
import gregtech.api.recipe.BasicUIProperties;
import gregtech.api.enums.SteamVariant;
import gregtech.api.recipe.RecipeMetadataKey;
import gregtech.api.util.GTRecipe;
import gregtech.nei.GTNEIDefaultHandler;
import gregtech.common.gui.modularui.UIHelper;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** GTNH 2.8.4 adapter: inventory bindings define slot roles; display items never define fluid identity. */
final class GtRecipes implements AutoCloseable {
    private final GTNEIDefaultHandler handler;
    private final Map<String, Binding> bindings = new HashMap<>();
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
        Object itemInputs = field("itemInputsInventory"), itemOutputs = field("itemOutputsInventory");
        Object fluidInputs = field("fluidInputsInventory"), fluidOutputs = field("fluidOutputsInventory");
        Object special = field("specialSlotInventory");
        List<Node> nodes = new ArrayList<>();
        for (Widget child : window.getChildren()) nodes.add(new Node(child, 0, 0));
        Set<Widget> visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (int index = 0; index < nodes.size(); index++) {
            Node node = nodes.get(index);
            Widget widget = node.widget;
            if (!visited.add(widget) || visited.size() > 4096) throw new Jobs.Fault("view_limit", "GT widget tree is cyclic or exceeds 4096 nodes");
            int x = node.x + widget.getPos().x, y = node.y + widget.getPos().y;
            if (widget instanceof IWidgetParent) for (Widget child : ((IWidgetParent) widget).getChildren()) nodes.add(new Node(child, x, y));
            if (!(widget instanceof SlotWidget)) continue;
            SlotWidget slot = (SlotWidget) widget;
            Object inventory = slot.getMcSlot().getItemHandler();
            boolean input = inventory == itemInputs || inventory == fluidInputs || inventory == special;
            boolean fluid = inventory == fluidInputs || inventory == fluidOutputs;
            if (!input && inventory != itemOutputs && inventory != fluidOutputs) continue;
            bind(bindings, x + 1, y + 1, input, new Binding(slot.getMcSlot().getSlotIndex(), fluid, inventory == special, false));
        }
        try { ui = views ? new Ui(handler, location) : null; }
        catch (RuntimeException | Error failure) {
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
        ItemStack[] itemInputs = presentation.itemInputsGetter.apply(recipe);
        ItemStack[] itemOutputs = presentation.itemOutputsGetter.apply(recipe);
        FluidStack[] fluidInputs = presentation.fluidInputsGetter.apply(recipe);
        FluidStack[] fluidOutputs = presentation.fluidOutputsGetter.apply(recipe);
        Map<String, Binding> slots = slots(recipe);
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
        for (PositionedStack display : cached.mInputs) {
            Binding binding = binding(slots, display, true);
            if (!captured.add(binding.identity(true))) throw new Jobs.Fault("slot_conflict", "GT recipe repeats an input binding");
            if (binding.special) {
                int amount = fixed(display).realStackSize;
                requireAmount(amount);
                row.itemInput(display, 65535, Math.max(1, amount), true, false, object("kind", "exact"));
            } else if (binding.fluid) {
                FluidStack fluid = at(binding.overflow ? recipe.mFluidInputs : fluidInputs, binding.index);
                requireAmount(fluid.amount);
                row.fluidInput(display, binding.index, fluid);
            } else {
                ItemStack item = at(binding.overflow ? recipe.mInputs : itemInputs, binding.index);
                requireAmount(item.stackSize);
                boolean meta = Items.feather.getDamage(item) == OreDictionary.WILDCARD_VALUE;
                JsonObject rule = meta || !recipe.isNBTSensitive
                        ? object("kind", "wildcard", "meta", meta, "nbt", !recipe.isNBTSensitive) : object("kind", "exact");
                row.itemInput(display, binding.index, Math.max(1, item.stackSize), item.stackSize == 0, false, rule);
            }
        }
        for (PositionedStack display : cached.mOutputs) {
            Binding binding = binding(slots, display, false);
            if (!captured.add(binding.identity(false))) throw new Jobs.Fault("slot_conflict", "GT recipe repeats an output binding");
            if (binding.fluid) row.fluidOutput(display, binding.index, at(binding.overflow ? recipe.mFluidOutputs : fluidOutputs, binding.index));
            else row.itemOutput(display, binding.index, at(binding.overflow ? recipe.mOutputs : itemOutputs, binding.index), recipe.getOutputChance(binding.index));
        }
        BasicUIProperties ui = handler.getRecipeMap().getFrontend().getUIProperties();
        covered(captured, itemInputs, recipe.mInputs, ui.maxItemInputs, false, true);
        covered(captured, itemOutputs, recipe.mOutputs, ui.maxItemOutputs, false, false);
        covered(captured, fluidInputs, recipe.mFluidInputs, ui.maxFluidInputs, true, true);
        covered(captured, fluidOutputs, recipe.mFluidOutputs, ui.maxFluidOutputs, true, false);
    }

    private Binding binding(Map<String, Binding> slots, PositionedStack display, boolean input) {
        Binding binding = slots.get(key(display.relx, display.rely, input));
        if (binding == null) throw new Jobs.Fault("slot_missing", "GT recipe display has no inventory binding: " + handler.getOverlayIdentifier());
        return binding;
    }

    private Map<String, Binding> slots(GTRecipe recipe) {
        Map<String, Binding> slots = new HashMap<>(bindings);
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

    private void overflow(Map<String, Binding> slots, Pos2d position, int index, int fixed, boolean fluid, boolean input) {
        if (index >= fixed) bind(slots, position.x + 1, position.y + 1, input, new Binding(index, fluid, false, true));
    }

    private void bind(Map<String, Binding> slots, int x, int y, boolean input, Binding binding) {
        Binding previous = slots.putIfAbsent(key(x, y, input), binding);
        if (previous != null && !previous.identity(input).equals(binding.identity(input))) {
            throw new Jobs.Fault("slot_conflict", "GT UI has overlapping recipe bindings: " + handler.getOverlayIdentifier());
        }
    }

    private static void covered(Set<String> captured, Object[] shown, Object[] source, int fixed, boolean fluid, boolean input) {
        int length = Math.max(Math.min(shown.length, fixed), source.length);
        for (int index = 0; index < length; index++) {
            Object value = index < fixed ? index < shown.length ? shown[index] : null : index < source.length ? source[index] : null;
            if (value != null && !captured.contains(new Binding(index, fluid, false, false).identity(input))) {
                throw new Jobs.Fault("slot_missing", "GT recipe contains a value without an exported slot: " + index);
            }
        }
    }

    private static <T> T at(T[] values, int index) {
        if (values == null || index < 0 || index >= values.length || values[index] == null) throw new Jobs.Fault("slot_missing", "GT recipe slot has no source value");
        return values[index];
    }

    private static GTNEIDefaultHandler.FixedPositionedStack fixed(PositionedStack value) {
        if (!(value instanceof GTNEIDefaultHandler.FixedPositionedStack)) throw new Jobs.Fault("slot_type", "Unsupported GT display stack");
        return (GTNEIDefaultHandler.FixedPositionedStack) value;
    }

    private static void requireAmount(int amount) {
        if (amount < 0) throw new Jobs.Fault("invalid_amount", "GT recipe has a negative ingredient quantity");
    }

    private <T> T field(String name) { return ReflectionHelper.getPrivateValue(GTNEIDefaultHandler.class, handler, name); }
    private static String key(int x, int y, boolean input) { return x + "/" + y + "/" + input; }

    private static final class Binding {
        final int index;
        final boolean fluid, special, overflow;
        Binding(int index, boolean fluid, boolean special, boolean overflow) {
            this.index = index; this.fluid = fluid; this.special = special; this.overflow = overflow;
        }
        String identity(boolean input) { return input + "/" + fluid + "/" + special + "/" + index; }
    }

    private static final class Node {
        final Widget widget;
        final int x, y;
        Node(Widget widget, int x, int y) { this.widget = widget; this.x = x; this.y = y; }
    }
}
