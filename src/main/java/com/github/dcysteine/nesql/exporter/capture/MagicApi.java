package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

/** Access only the explicitly named API members audited for the pinned magic mods. */
final class MagicApi {
    private MagicApi() {}
    static void version(String family, String id) { TargetAdmission.require(family, id); }
    static Class<?> type(String name) {
        try { return Class.forName(name); } catch (ClassNotFoundException error) {
            throw fault("Required magic API is unavailable: " + name, error);
        }
    }
    static Object field(Object target, String name) { return field(target.getClass(), target, name); }
    static Object field(Class<?> owner, Object target, String name) {
        for (Class<?> current = owner; current != null && current != Object.class; current = current.getSuperclass()) {
            for(String candidate:new String[]{name,runtimeField(current,name)}){
                if(candidate==null)continue;
                try { Field field = current.getDeclaredField(candidate); field.setAccessible(true); return field.get(target); }
                catch (NoSuchFieldException ignored) { }
                catch (ReflectiveOperationException error) { throw fault(error.toString(), error); }
            }
        }
        throw fault("Missing pinned field " + owner.getName() + "." + name);
    }
    /** MCP names in local fixtures and SRG names in the transformed 1.7.10 client. */
    private static String runtimeField(Class<?> owner,String name){
        switch(owner.getName()+"#"+name){
            case "net.minecraft.nbt.NBTTagCompound#tagMap":return "field_74784_a";
            case "net.minecraft.nbt.NBTTagList#tagList":return "field_74747_a";
            case "net.minecraft.potion.PotionEffect#potionID":return "field_76462_a";
            case "net.minecraft.potion.PotionEffect#duration":return "field_76460_b";
            case "net.minecraft.potion.PotionEffect#amplifier":return "field_76461_c";
            case "net.minecraft.potion.PotionEffect#isAmbient":return "field_82724_e";
            case "net.minecraft.item.ItemStack#stackSize":return "field_77994_a";
            case "net.minecraft.item.ItemStack#itemDamage":return "field_77991_e";
            case "net.minecraft.item.ItemStack#stackTagCompound":return "field_77990_d";
            case "net.minecraft.item.crafting.FurnaceRecipes#smeltingList":return "field_77604_b";
            case "net.minecraft.item.crafting.FurnaceRecipes#experienceList":return "field_77605_c";
            case "net.minecraft.util.WeightedRandom$Item#itemWeight":return "field_76292_a";
            case "net.minecraft.potion.PotionHelper#potionRequirements":return "field_77927_l";
            case "net.minecraft.potion.PotionHelper#potionAmplifiers":return "field_77928_m";
            case "net.minecraft.item.ItemPotion#effectCache":return "field_77836_a";
            case "net.minecraft.entity.ai.attributes.BaseAttribute#unlocalizedName":return "field_111115_a";
            case "net.minecraft.item.Item#itemModifierUUID":return "field_111210_e";
            case "net.minecraft.item.Item#containerItem":return "field_77700_c";
            case "net.minecraft.block.Block#blockMaterial":return "field_149764_J";
            default:return null;
        }
    }
    static String runtimeMethod(Class<?> owner,String name){
        if(owner==null)return name;
        switch(owner.getName()+"#"+name){
            case "net.minecraft.item.Item#getPotionEffect":return "func_150896_i";
            case "net.minecraft.item.Item#isPotionIngredient":return "func_150892_m";
            case "net.minecraft.item.Item#doesContainerItemLeaveCraftingGrid":return "func_77630_h";
            default:return name;
        }
    }
    static Object invoke(Class<?> owner, Object target, String name, Class<?>[] parameters, Object... arguments) {
        for (Class<?> current = owner; current != null && current != Object.class; current = current.getSuperclass()) {
          for(String candidate:new String[]{name,runtimeMethod(current,name)}){
            try { Method method = current.getDeclaredMethod(candidate, parameters); method.setAccessible(true); return method.invoke(target, arguments); }
            catch (NoSuchMethodException ignored) { }
            catch (InvocationTargetException error) {
                Throwable cause = error.getCause();
                if (cause instanceof Error) throw (Error) cause;
                if (cause instanceof RuntimeException) throw (RuntimeException) cause;
                Jobs.Fault failure = fault("Pinned native API call failed: " + owner.getName() + "." + name + ": " + cause);
                failure.initCause(cause); throw failure;
            }
            catch (ReflectiveOperationException error) { throw fault("Pinned magic API call failed: " + owner.getName() + "." + name + ": " + error, error); }
          }
        }
        throw fault("Missing pinned method " + owner.getName() + "." + name);
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("magic_recipe", message); }
    private static Jobs.Fault fault(String message, Throwable cause) {
        Jobs.Fault failure = fault(message);
        failure.initCause(cause);
        return failure;
    }
}
