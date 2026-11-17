package com.github.dcysteine.nesql.exporter.capture;

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

/** Native casting predicates and projections of admitted registered event laws on owned data. */
final class TinkerCasting {
    private static final String RECIPE = "tconstruct.library.crafting.CastingRecipe";
    private static final String EVENTS = "tconstruct.library.event.";
    /** Registered listeners and generated wrapper/target identities are borrowed and sealed. */
    static final class Events implements AutoCloseable {
        private final Map<Class<?>,Binding> bindings=new HashMap<>();
        final TinkerCastingSources sources=new TinkerCastingSources();
        private boolean closed;
        void bind(Event event,IEventListener[] listeners,Map<IEventListener,Method> callbacks){
            alive();Binding prior=bindings.get(event.getClass());if(prior==null){if(bindings.size()>=4)throw fault("Casting event family budget exceeded");bindings.put(event.getClass(),new Binding(event,listeners,callbacks));}else prior.verify();
        }
        void verify(){alive();for(Binding binding:bindings.values())binding.verify();sources.verify();}
        void finishVerification(){verify();sources.finishVerification();}
        private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Casting event bindings are closed");}
        public void close(){closed=true;bindings.clear();sources.close();}
    }
    private static final class Binding {
        final ListenerList list;final int bus;final IEventListener[] listeners;final Object[] wrappers,targets;
        final Map<?,?> cache;final Method[] callbacks;
        Binding(Event event,IEventListener[] listeners,Map<IEventListener,Method> methods){this.list=event.getListenerList();this.bus=(Integer)field(EventBus.class,MinecraftForge.EVENT_BUS,"busID");this.listeners=listeners.clone();wrappers=new Object[listeners.length];targets=new Object[listeners.length];callbacks=new Method[listeners.length];cache=cache();
            for(int i=0;i<listeners.length;i++)if(listeners[i].getClass()==ASMEventHandler.class){wrappers[i]=field(listeners[i],"handler");targets[i]=field(wrappers[i],"instance");callbacks[i]=methods.get(listeners[i]);}}
        void verify(){
            if((Integer)field(EventBus.class,MinecraftForge.EVENT_BUS,"busID")!=bus||cache()!=cache)throw changed();IEventListener[] now=list.getListeners(bus);
            if(now.length!=listeners.length)throw changed();for(int i=0;i<now.length;i++){
                if(now[i]!=listeners[i])throw changed();if(wrappers[i]!=null&&(field(now[i],"handler")!=wrappers[i]||field(wrappers[i],"instance")!=targets[i]||cache.get(callbacks[i])!=wrappers[i].getClass()))throw changed();
            }
        }
    }

    static boolean capture(TemplateRecipeHandler handler, Object source, RecipeRow row) {
        return capture(handler.getClass().getName(),source,row);
    }
    static boolean capture(String handler,Object source,RecipeRow row){
        try(NativeItemExamples examples=new NativeItemExamples();Events events=new Events()){boolean result=capture(handler,source,row,examples,events);examples.verify();events.finishVerification();return result;}
    }
    static boolean capture(String handler,Object source,RecipeRow row,NativeItemExamples examples,Events events){
        if (source == null || !source.getClass().getName().equals(RECIPE)) throw fault("Unadapted casting override: " + (source == null ? "null" : source.getClass().getName()));
        ItemStack cast = (ItemStack) field(source, "cast"), output = (ItemStack) field(source, "output");
        FluidStack fluid = (FluidStack) field(source, "castingMetal");
        boolean ignore = (Boolean) field(source, "ignoreNBT"), consume = (Boolean) field(source, "consumeCast");
        int time = (Integer) field(source, "coolTime");
        if (output == null || output.getItem() == null || output.stackSize <= 0 || fluid == null || fluid.getFluid() == null || fluid.amount <= 0)
            throw fault("Casting has no fixed output/fluid requirement");
        // Cooling is only processed while the countdown is positive. The native inventory holds one cast.
        if (time <= 0) return row.exclude(object("native",RECIPE,"reason","native_cooling_requires_positive_countdown","coolTime",time));
        if(cast==null&&ignore)return row.exclude(object("native",RECIPE,"reason","native_ignoreNBT_predicate_requires_nonnull_cast"));
        boolean wildcard = cast != null && cast.getItemDamage() == OreDictionary.WILDCARD_VALUE;
        if (cast != null && cast.getItem() == null) throw fault("Casting has an invalid mould");
        if (cast != null && !wildcard && !ignore && cast.stackSize != 1) return row.exclude(object("native",RECIPE,"reason","native_single_cast_inventory_cannot_match_template_count","cast",RecipeRow.stackEvidence(cast)));
        output = output.copy(); fluid = fluid.copy();
        if (cast != null) { cast = cast.copy(); cast.stackSize = 1; }
        Class<?> recipeType = type(RECIPE);
        Object projection = construct(recipeType, new Class<?>[] {ItemStack.class, FluidStack.class, ItemStack.class, boolean.class,
                int.class, type("tconstruct.library.client.FluidRenderProperties"), boolean.class}, output.copy(), fluid.copy(),
                cast == null ? null : cast.copy(), consume, time, null, ignore);
        String machine = handler.endsWith("Table") ? "CastingTable" : "CastingBasin";
        Event before = (Event) construct(type(EVENTS + "SmelteryCastEvent$" + machine), new Class<?>[] {recipeType, FluidStack.class}, projection, fluid.copy());
        dispatch(before,events);
        if (before.getResult() == Event.Result.DENY) return row.exclude(object("native",before.getClass().getName(),"reason","audited_native_cast_event_denied_owned_projection"));
        Event after = (Event) construct(type(EVENTS + "SmelteryCastedEvent$" + machine), new Class<?>[] {recipeType, ItemStack.class}, projection, output.copy());
        boolean unstable = dispatch(after,events);
        output = (ItemStack) field(after, "output"); consume = (Boolean) field(after, "consumeCast");
        if (output == null || output.getItem() == null || output.stackSize <= 0) throw fault("Casting event produced no fixed result");
        output = output.copy();
        if (cast != null) {
            List<RecipeRow.Ingredient> choices = new ArrayList<>();
            for (ItemStack displayed : examples.copies(cast)) {
                if (displayed.getItem() != cast.getItem() || !wildcard && displayed.getItemDamage() != cast.getItemDamage())
                    throw new Jobs.Fault("slot_changed", "Casting mould expansion changed its source");
                ItemStack fact = displayed.copy(); fact.stackSize = 1;
                fact.setTagCompound(cast.getTagCompound() == null ? null : (NBTTagCompound) cast.getTagCompound().copy());
                choices.add(new RecipeRow.Ingredient(fact, 1, !consume, wildcard || ignore
                        ? object("kind", "wildcard", "meta", wildcard, "nbt", true) : object("kind", "exact")));
            }
            row.itemInput(0, choices, false);
        } else row.note("tconstruct:emptyCast",RECIPE+"#matches",row.facts.locale().equals("zh_CN")?"浇铸槽必须为空，不接受额外模具或物品。":"The casting item slot must be empty; an extra mould or item prevents matching.");
        row.fluidInput(0, fluid, false); row.itemOutput(0, output, 10000);
        row.record.addProperty("duration", Integer.toString(time));
        if (unstable) row.record.add("process", object("kind", "unstableCasting"));
        return true;
    }

    private static boolean dispatch(Event event,Events events) {
        int bus = (Integer) field(EventBus.class, MinecraftForge.EVENT_BUS, "busID");
        IEventListener[] listeners = event.getListenerList().getListeners(bus).clone();
        if(listeners.length>4096)throw fault("Native casting listener budget exceeded");
        Map<IEventListener,Method> callbacks=new IdentityHashMap<>();
        // Validate the complete snapshot before running anything. Unknown callbacks must never execute.
        for (IEventListener listener : listeners) {
            if (listener instanceof EventPriority) continue;
            if (listener.getClass() != ASMEventHandler.class) throw fault("Unadapted casting event listener: " + listener.getClass().getName());
            Object wrapper = field(listener, "handler"), target = field(wrapper, "instance");
            String name = target.getClass().getName(), callback;
            if (name.equals("com.rwtema.extrautils.modintegration.TConEvents")) {
                MagicApi.version("TinkerCasting","ExtraUtilities");
                if (!event.getClass().getName().equals(EVENTS+"SmelteryCastedEvent$CastingTable"))
                    throw fault("Unexpected ExtraUtilities casting event");
                boolean matched=false;
                for (String candidate:new String[]{"addUnstableTimer","addBedrockiumPartSlowness"}) {
                    try {
                        Method method=target.getClass().getDeclaredMethod(candidate,event.getClass());
                        if (cache().get(method)==wrapper.getClass()) {
                            if(matched)throw fault("Casting wrapper aliases two native callbacks");
                            matched=true;callbacks.put(listener,method);
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
                if (cache().get(method) != wrapper.getClass()) throw fault("Unadapted callback on casting event owner: " + name);
                callbacks.put(listener,method);
            } catch (NoSuchMethodException error) { throw fault("Unadapted casting event callback: " + name + "/" + event.getClass().getName()); }
        }
        events.bind(event,listeners,callbacks);
        boolean unstable=false;
        for (IEventListener listener : listeners) {
            Jobs.checkpoint();
            if(listener instanceof EventPriority)continue;
            switch(callbacks.get(listener).getName()){
                case "weaponryPartCast":events.sources.weaponry(event);break;
                case "onPartCasting":events.sources.restrict(event);break;
                case "onCasted":events.sources.consumePattern(event);break;
                case "addUnstableTimer":unstable|=events.sources.extra(event,true);break;
                case "addBedrockiumPartSlowness":events.sources.extra(event,false);break;
                default:throw fault("Unknown validated casting callback");
            }
        }
        return unstable;
    }
    private static Map<?,?> cache(){
        Object raw=field(ASMEventHandler.class,null,"cache");
        if(raw==null||raw.getClass()!=HashMap.class)throw fault("Unadapted casting ASM callback cache");
        return (Map<?,?>)raw;
    }
    private static Jobs.Fault changed(){return new Jobs.Fault("recipe_changed","Registered casting event listeners changed");}
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
