package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;
import cpw.mods.fml.common.eventhandler.*;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;
import java.lang.reflect.Method;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.capture.TinkerRecipes.construct;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Native casting predicates and audited, actually registered event callbacks on owned data. */
final class TinkerCasting {
    private static final String RECIPE = "tconstruct.library.crafting.CastingRecipe";
    private static final String EVENTS = "tconstruct.library.event.";

    static boolean capture(TemplateRecipeHandler handler, Object source, RecipeRow row) {
        if (source == null || !source.getClass().getName().equals(RECIPE)) throw fault("Unadapted casting override: " + (source == null ? "null" : source.getClass().getName()));
        ItemStack cast = (ItemStack) field(source, "cast"), output = (ItemStack) field(source, "output");
        FluidStack fluid = (FluidStack) field(source, "castingMetal");
        boolean ignore = (Boolean) field(source, "ignoreNBT"), consume = (Boolean) field(source, "consumeCast");
        int time = (Integer) field(source, "coolTime");
        if (output == null || output.getItem() == null || output.stackSize <= 0 || fluid == null || fluid.getFluid() == null || fluid.amount <= 0)
            throw fault("Casting has no fixed output/fluid requirement");
        // Cooling is only processed while the countdown is positive. The native inventory holds one cast.
        if (time <= 0 || cast == null && ignore) return false;
        boolean wildcard = cast != null && cast.getItemDamage() == OreDictionary.WILDCARD_VALUE;
        if (cast != null && cast.getItem() == null) throw fault("Casting has an invalid mould");
        if (cast != null && !wildcard && !ignore && cast.stackSize != 1) return false;
        output = output.copy(); fluid = fluid.copy();
        if (cast != null) { cast = cast.copy(); cast.stackSize = 1; }
        Class<?> recipeType = type(RECIPE);
        Object projection = construct(recipeType, new Class<?>[] {ItemStack.class, FluidStack.class, ItemStack.class, boolean.class,
                int.class, type("tconstruct.library.client.FluidRenderProperties"), boolean.class}, output.copy(), fluid.copy(),
                cast == null ? null : cast.copy(), consume, time, null, ignore);
        String machine = handler.getClass().getName().endsWith("Table") ? "CastingTable" : "CastingBasin";
        Event before = (Event) construct(type(EVENTS + "SmelteryCastEvent$" + machine), new Class<?>[] {recipeType, FluidStack.class}, projection, fluid.copy());
        dispatch(before);
        if (before.getResult() == Event.Result.DENY) return false;
        Event after = (Event) construct(type(EVENTS + "SmelteryCastedEvent$" + machine), new Class<?>[] {recipeType, ItemStack.class}, projection, output.copy());
        dispatch(after);
        output = (ItemStack) field(after, "output"); consume = (Boolean) field(after, "consumeCast");
        if (output == null || output.getItem() == null || output.stackSize <= 0) throw fault("Casting event produced no fixed result");
        output = output.copy();
        // Project the effective output/consumption without changing the native recipe registry.
        try { recipeType.getField("output").set(projection, output.copy()); recipeType.getField("consumeCast").setBoolean(projection, consume); }
        catch (ReflectiveOperationException error) { Jobs.Fault failure = fault("Cannot project casting result"); failure.initCause(error); throw failure; }
        TemplateRecipeHandler.CachedRecipe cached = (TemplateRecipeHandler.CachedRecipe) construct(type("tconstruct.plugins.nei.RecipeHandlerCastingBase$CachedCastingRecipe"),
                new Class<?>[] {type("tconstruct.plugins.nei.RecipeHandlerCastingBase"), recipeType}, handler, projection);
        if (cast != null) {
            PositionedStack position = cached.getIngredients().get(0);
            List<RecipeRow.Ingredient> choices = new ArrayList<>();
            for (ItemStack displayed : position.items) {
                if (displayed.getItem() != cast.getItem() || !wildcard && displayed.getItemDamage() != cast.getItemDamage())
                    throw new Jobs.Fault("slot_changed", "Casting mould expansion changed its source");
                ItemStack fact = displayed.copy(); fact.stackSize = 1;
                fact.setTagCompound(cast.getTagCompound() == null ? null : (NBTTagCompound) cast.getTagCompound().copy());
                choices.add(new RecipeRow.Ingredient(fact, 1, !consume, wildcard || ignore
                        ? object("kind", "wildcard", "meta", wildcard, "nbt", true) : object("kind", "exact")));
            }
            row.itemInput(position, 0, choices, false);
        } else row.property("tconstruct:emptyCast", "Requires an empty casting slot", true);
        row.fluidInput(null, 0, fluid, false); row.itemOutput(cached.getResult(), 0, output, 10000);
        row.record.addProperty("duration", Integer.toString(time));
        handler.arecipes.clear(); handler.arecipes.add(cached); return true;
    }

    private static void dispatch(Event event) {
        int bus = (Integer) field(EventBus.class, MinecraftForge.EVENT_BUS, "busID");
        IEventListener[] listeners = event.getListenerList().getListeners(bus).clone();
        // Validate the complete snapshot before running anything. Unknown callbacks must never execute.
        for (IEventListener listener : listeners) {
            if (listener instanceof EventPriority) continue;
            if (listener.getClass() != ASMEventHandler.class) throw fault("Unadapted casting event listener: " + listener.getClass().getName());
            Object wrapper = field(listener, "handler"), target = field(wrapper, "instance");
            String name = target.getClass().getName(), callback;
            switch (name) {
                case "tconstruct.weaponry.WeaponryHandler": callback = "weaponryPartCast"; break;
                case "iguanaman.iguanatweakstconstruct.restriction.PartRestrictionHandler": callback = "onPartCasting"; break;
                case "iguanaman.iguanatweakstconstruct.tweaks.handlers.CastHandler": callback = "onCasted"; break;
                default: throw fault("Unadapted casting event owner: " + name);
            }
            try {
                Method method = target.getClass().getDeclaredMethod(callback, event.getClass());
                Map<?, ?> cache = (Map<?, ?>) field(ASMEventHandler.class, null, "cache");
                if (cache.get(method) != wrapper.getClass()) throw fault("Unadapted callback on casting event owner: " + name);
            } catch (NoSuchMethodException error) { throw fault("Unadapted casting event callback: " + name + "/" + event.getClass().getName()); }
        }
        for (IEventListener listener : listeners) { Jobs.checkpoint(); listener.invoke(event); }
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
