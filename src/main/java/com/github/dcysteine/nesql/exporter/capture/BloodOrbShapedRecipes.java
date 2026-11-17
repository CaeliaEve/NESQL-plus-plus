package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Pinned Blood Magic shaped recipes; integer ingredients mean minimum orb level, not a displayed orb. */
final class BloodOrbShapedRecipes implements RegistryRecipes {
    private static final String ROOT = "WayofTime.alchemicalWizardry.";
    private static final String HANDLER = ROOT + "client.nei.NEIBloodOrbShapedHandler";
    private static final String RECIPE = ROOT + "api.items.ShapedBloodOrbRecipe";
    private static final String BATTERY = ROOT + "common.items.EnergyBattery";
    private static final String DIVINE = "fox.spiteful.forbidden.items.ItemDivineOrb";
    private static final String ARMOK = "fox.spiteful.avaritia.compat.bloodmagic.ItemOrbArmok";
    private final BloodOrbCraftingRecipes source;
    static boolean supports(ICraftingHandler handler){return handler.getClass().getName().equals(HANDLER);}
    BloodOrbShapedRecipes(TemplateRecipeHandler handler){source=new BloodOrbCraftingRecipes(handler);}
    public int size(){return source.size();}
    public boolean capture(int index,RecipeRow row){return source.capture(index,row);}
    public void verify(){source.verify();}
    public void finishVerification(){source.finishVerification();}
    public void close(){source.close();}
    static List<Item> orbs(){
        List<Item> values=new ArrayList<>();Class<?> api=type(ROOT+"api.items.interfaces.IBloodOrb");
        for(Object raw:Item.itemRegistry)if(api.isInstance(raw)){
            Jobs.checkpoint();orbLevel((Item)raw);values.add((Item)raw);if(values.size()>4096)throw fault("Blood Orb registry exceeds budget");
        }
        values.sort(Comparator.comparing(item->Item.itemRegistry.getNameForObject(item)));return values;
    }
    static int orbLevel(Item item) {
        if (item == null || !type(ROOT + "api.items.interfaces.IBloodOrb").isInstance(item)) throw fault("Blood Orb candidate is not an orb");
        try {
            if (item.getClass().getMethod("getOrbLevel").getDeclaringClass().getName().equals(ARMOK)) {
                MagicApi.version("BloodOrbShapedRecipes","Avaritia");
                if (ItemCallbacks.method(item, "hasContainerItem", "hasContainerItem", ItemStack.class).getDeclaringClass() != Item.class
                        || !ItemCallbacks.method(item, "hasContainerItem", "func_77634_r").getDeclaringClass().getName().equals(ARMOK)
                        || !ItemCallbacks.method(item, "getContainerItem", "getContainerItem", ItemStack.class).getDeclaringClass().getName().equals(ARMOK)
                        || !ItemCallbacks.method(item, "doesContainerItemLeaveCraftingGrid", "func_77630_h", ItemStack.class).getDeclaringClass().getName().equals(ARMOK))
                    throw fault("Unadapted Armok Orb container override: " + item.getClass().getName());
                return 6;
            }
            if (item.getClass().getMethod("getOrbLevel").getDeclaringClass().getName().equals(DIVINE)) {
                MagicApi.version("BloodOrbShapedRecipes","ForbiddenMagic");
                if (ItemCallbacks.method(item, "hasContainerItem", "hasContainerItem", ItemStack.class).getDeclaringClass() != Item.class
                        || !ItemCallbacks.method(item, "hasContainerItem", "func_77634_r").getDeclaringClass().getName().equals(DIVINE)
                        || !ItemCallbacks.method(item, "getContainerItem", "getContainerItem", ItemStack.class).getDeclaringClass().getName().equals(DIVINE))
                    throw fault("Unadapted Divine Orb container override: " + item.getClass().getName());
                // Installed 0.8.4 returns literal tier 6 and the exact offered stack.
                return 6;
            }
            if (!item.getClass().getMethod("getOrbLevel").getDeclaringClass().getName().equals(BATTERY)
                    || !ItemCallbacks.method(item, "hasContainerItem", "hasContainerItem", ItemStack.class).getDeclaringClass().getName().equals(BATTERY)
                    || !ItemCallbacks.method(item, "getContainerItem", "getContainerItem", ItemStack.class).getDeclaringClass().getName().equals(BATTERY))
                throw fault("Unadapted Blood Orb tier or container override: " + item.getClass().getName());
        } catch (NoSuchMethodException error) { throw fault("Missing Blood Orb tier getter"); }
        // Pinned EnergyBattery methods return this field and the exact offered stack.
        return (Integer)field(type(BATTERY), item, "orbLevel");
    }
    static int maxEssence(Item item) {
        try {
            String owner = item.getClass().getMethod("getMaxEssence").getDeclaringClass().getName();
            if (owner.equals(ARMOK)) { orbLevel(item); return 1000000000; }
            if (owner.equals(DIVINE)) {
                orbLevel(item); // Audit the pinned tier and offered-stack container contract too.
                return 80000000;
            }
            if (!owner.equals(BATTERY)) throw fault("Unadapted altar orb capacity callback: " + item.getClass().getName());
            return (Integer)field(type(BATTERY), item, "maxEssence");
        } catch (NoSuchMethodException error) { throw fault("Missing native altar orb capacity getter"); }
    }
    static boolean filledForFree(Item item) {
        try {
            String owner = item.getClass().getMethod("isFilledForFree").getDeclaringClass().getName();
            if (owner.equals(ARMOK)) { orbLevel(item); return true; }
            if (owner.equals(ROOT + "api.items.interfaces.IBloodOrb")) return false;
            throw fault("Unadapted altar free-charging callback: " + owner);
        } catch (NoSuchMethodException error) { throw fault("Missing native altar orb API"); }
    }
    static boolean oreGroup(Object value) {
        if (value == null) return false;
        if (value.getClass() == ArrayList.class) return true;
        // Forge 1.7.10 getOres exposes this native read-only ArrayList wrapper.
        // Its reads/equals delegate to a backing ArrayList; no custom callbacks.
        if (!value.getClass().getName().equals("net.minecraftforge.oredict.OreDictionary$UnmodifiableArrayList")) return false;
        Object backing = field(value, "list");
        if (backing == null || backing.getClass() != ArrayList.class) throw fault("Unadapted Blood Orb ore-list backing implementation");
        return true;
    }
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
}
