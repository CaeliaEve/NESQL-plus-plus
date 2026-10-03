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
        boolean unstable = dispatch(after);
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
        if (unstable) row.record.add("process", object("kind", "unstableCasting"));
        handler.arecipes.clear(); handler.arecipes.add(cached); return true;
    }

    private static boolean dispatch(Event event) {
        int bus = (Integer) field(EventBus.class, MinecraftForge.EVENT_BUS, "busID");
        IEventListener[] listeners = event.getListenerList().getListeners(bus).clone();
        Set<IEventListener> timers = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<IEventListener> extraUtils = Collections.newSetFromMap(new IdentityHashMap<>());
        // Validate the complete snapshot before running anything. Unknown callbacks must never execute.
        for (IEventListener listener : listeners) {
            if (listener instanceof EventPriority) continue;
            if (listener.getClass() != ASMEventHandler.class) throw fault("Unadapted casting event listener: " + listener.getClass().getName());
            Object wrapper = field(listener, "handler"), target = field(wrapper, "instance");
            String name = target.getClass().getName(), callback;
            if (name.equals("com.rwtema.extrautils.modintegration.TConEvents")) {
                version("ExtraUtilities", "1.2.12");
                if (!event.getClass().getName().equals(EVENTS+"SmelteryCastedEvent$CastingTable"))
                    throw fault("Unexpected ExtraUtilities casting event");
                boolean matched=false;
                for (String candidate:new String[]{"addUnstableTimer","addBedrockiumPartSlowness"}) {
                    try {
                        Method method=target.getClass().getDeclaredMethod(candidate,event.getClass());
                        if (((Map<?,?>)field(ASMEventHandler.class,null,"cache")).get(method)==wrapper.getClass()) {
                            matched=true;extraUtils.add(listener);
                            if (candidate.equals("addUnstableTimer")) timers.add(listener);
                        }
                    } catch (NoSuchMethodException error) { throw fault("Missing pinned ExtraUtilities callback: "+candidate); }
                }
                if (!matched) throw fault("Unadapted callback on casting event owner: "+name);
                continue;
            }
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
        boolean unstable=false;
        for (IEventListener listener : listeners) {
            Jobs.checkpoint();
            if (extraUtils.contains(listener)) {
                ItemStack output=(ItemStack)field(event,"output");
                int material=material(output);
                if (timers.contains(listener)) {
                    int configured=(Integer)field(type("com.rwtema.extrautils.ExtraUtils"),null,"tcon_unstable_material_id");
                    if (configured>0 && material==configured) {
                        // The native callback creates a compound, then reads server clocks.
                        // Retain its base output, and represent clock writes as process semantics.
                        if (output.getTagCompound()==null) output.setTagCompound(new NBTTagCompound());
                        unstable=true;
                    }
                    continue;
                }
            }
            listener.invoke(event);
        }
        return unstable;
    }
    private static int material(ItemStack output) {
        if (output==null || !type("tconstruct.library.util.IToolPart").isInstance(output.getItem())) return -1;
        try {
            Method getter=output.getItem().getClass().getMethod("getMaterialID",ItemStack.class);
            String owner=getter.getDeclaringClass().getName();
            if ((!owner.equals("tconstruct.tools.items.ToolPart") && !owner.equals("tconstruct.library.tools.DynamicToolPart"))
                    || ItemCallbacks.method(output.getItem(),"getDamage","getDamage",ItemStack.class).getDeclaringClass()!=net.minecraft.item.Item.class)
                throw fault("Unadapted casting material predicate: "+owner);
            return (Integer)invoke(output.getItem().getClass(),output.getItem(),"getMaterialID",new Class<?>[]{ItemStack.class},output.copy());
        } catch (NoSuchMethodException error) { throw fault("Missing tool-part material getter"); }
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
