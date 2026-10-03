package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import java.lang.reflect.Method;

/** Pinned, read-only container callbacks; never a general item callback allowlist. */
final class ItemCallbacks {
    private ItemCallbacks() {}
    static Method method(Item item, String mcp, String srg, Class<?>... args) {
        // Resolve the environment on Item before inspecting the subclass override.
        String name = ReflectionHelper.findMethod(Item.class, null, new String[]{mcp, srg}, args).getName();
        try { return item.getClass().getMethod(name, args); }
        catch (NoSuchMethodException error) { throw fault("Missing native item method: " + name); }
    }
    static boolean hasContainer(ItemStack input) {
        Item item = input.getItem();
        Class<?> presence = method(item, "hasContainerItem", "hasContainerItem", ItemStack.class).getDeclaringClass();
        if (presence == Item.class) {
            if (method(item, "hasContainerItem", "func_77634_r").getDeclaringClass() != Item.class)
                throw fault("Unadapted legacy container predicate: " + item.getClass().getName());
        } else if (presence.getName().equals("ic2.core.item.resources.ItemCell")) {
            MagicApi.version("IC2", "2.2.828-experimental"); MagicApi.version("hodgepodge", "2.6.112");
            if (method(item,"getContainerItem","getContainerItem",ItemStack.class).getDeclaringClass() != presence
                    || method(item,"getDamage","getDamage",ItemStack.class).getDeclaringClass()!=Item.class)
                throw fault("Patched IC2 cell callback changed");
        } else {
            if (!presence.getName().equals("gregtech.api.items.GTGenericItem"))
                throw fault("Unadapted container predicate: " + item.getClass().getName());
            MagicApi.version("gregtech_nh", "5.09.51.482");
            if (method(item, "getDamage", "getDamage", ItemStack.class).getDeclaringClass() != Item.class)
                throw fault("GT container metadata getter changed");
            String getter = method(item, "getContainerItem", "getContainerItem", ItemStack.class).getDeclaringClass().getName();
            switch (getter) {
                case "gregtech.api.items.GTGenericItem":
                case "gregtech.api.items.MetaGeneratedItemX01":
                case "gregtech.api.items.MetaGeneratedItemX32":
                case "gregtech.common.items.MetaGeneratedItem01": break;
                default: throw fault("Unadapted GT container result: " + getter);
            }
        }
        return item.hasContainerItem(input.copy());
    }
    static ItemStack container(ItemStack input, boolean ignoreMeta) {
        Item item=input.getItem();
        // The supported native overrides use metadata, never offered NBT or count.
        if (ignoreMeta && method(item,"hasContainerItem","hasContainerItem",ItemStack.class).getDeclaringClass()!=Item.class)
            throw fault("Container predicate needs fixed metadata");
        if (!hasContainer(input)) return null;
        if (method(item,"hasContainerItem","hasContainerItem",ItemStack.class).getDeclaringClass()==Item.class
                && (method(item,"getContainerItem","getContainerItem",ItemStack.class).getDeclaringClass()!=Item.class
                    || method(item,"getContainerItem","func_77668_q").getDeclaringClass()!=Item.class))
            throw fault("Unadapted native container result: "+item.getClass().getName());
        ItemStack result=item.getContainerItem(input.copy());
        if (result==null || result.getItem()==null || result.stackSize<=0) throw fault("Native container predicate has no positive result");
        return result.copy();
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
