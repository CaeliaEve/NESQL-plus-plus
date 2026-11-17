package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import static com.github.dcysteine.nesql.exporter.source.Json.object;

/** Input-dependent native returns. Discovery never executes wear, RNG or cosmetic deserialization. */
final class NativeCraftingContainers {
    private static final String CUTTER="ic2.core.item.tool.ItemToolCutter";
    private static final String BAUBLE="vazkii.botania.common.item.equipment.bauble.ItemBauble";
    private static final String DIVA="vazkii.botania.common.item.equipment.bauble.ItemDivaCharm";
    private static final String PLANET="vazkii.botania.common.item.equipment.bauble.ItemTinyPlanet";
    private static final String BLACK_HOLE="vazkii.botania.common.item.ItemBlackHoleTalisman";
    private static final String MORTAR="tb.common.item.ItemMortarAndPestle";
    private static final String FIRESTONE="mods.railcraft.common.items.firestone.ItemFirestoneRefined";
    private NativeCraftingContainers() {}

    static JsonObject consumption(ItemStack input) {
        Item item=input.getItem();String name=item.getClass().getName();
        String owner, callback, gridOwner=null;
        if(name.equals(CUTTER)) {
            MagicApi.version("NativeCraftingContainers","IC2");owner=CUTTER;callback="ic2Cutter";
            if(ItemCallbacks.method(item,"getDamage","getDamage",ItemStack.class).getDeclaringClass()!=Item.class)
                throw fault("IC2 cutter metadata callback changed");
        } else if(name.equals(MORTAR)) {
            MagicApi.version("NativeCraftingContainers","thaumicbases");owner=MORTAR;callback="thaumicMortar";
            if(ItemCallbacks.method(item,"getDamage","getDamage",ItemStack.class).getDeclaringClass()!=Item.class
                ||ItemCallbacks.method(item,"getMaxDamage","getMaxDamage",ItemStack.class).getDeclaringClass()!=Item.class
                ||ItemCallbacks.method(item,"getMaxDamage","func_77612_l").getDeclaringClass()!=Item.class)
                throw fault("Thaumic mortar durability callback changed");
        } else if(name.equals(FIRESTONE)) {
            MagicApi.version("NativeCraftingContainers","Railcraft");owner=FIRESTONE;callback="railFirestone";gridOwner=Item.class.getName();
            if(ItemCallbacks.method(item,"getDamage","getDamage",ItemStack.class).getDeclaringClass()!=Item.class
                ||ItemCallbacks.method(item,"getMaxDamage","getMaxDamage",ItemStack.class).getDeclaringClass()!=Item.class
                ||ItemCallbacks.method(item,"getMaxDamage","func_77612_l").getDeclaringClass()!=Item.class)
                throw fault("Railcraft firestone durability callback changed");
        } else if(name.equals(BLACK_HOLE)) {
            MagicApi.version("NativeCraftingContainers","Botania");owner=BLACK_HOLE;callback="botaniaBlackHole";
            if(ItemCallbacks.method(item,"getDamage","getDamage",ItemStack.class).getDeclaringClass()!=Item.class
                ||ItemCallbacks.method(item,"setDamage","setDamage",ItemStack.class,int.class).getDeclaringClass()!=Item.class)
                throw fault("Botania black hole physical metadata callbacks changed");
        } else if(name.equals(DIVA)||name.equals(PLANET)) {
            MagicApi.version("NativeCraftingContainers","Botania");owner=BAUBLE;callback="botaniaCosmetic";
            try {
                if(item.getClass().getMethod("getCosmeticItem",ItemStack.class).getDeclaringClass()!=MagicApi.type(BAUBLE))
                    throw fault("Botania cosmetic deserializer callback changed");
            }catch(NoSuchMethodException failure){throw fault("Botania cosmetic deserializer missing");}
        } else return null;
        if(item.getClass()!=MagicApi.type(name)
                ||!ItemCallbacks.method(item,"hasContainerItem","hasContainerItem",ItemStack.class).getDeclaringClass().getName().equals(owner)
                ||!ItemCallbacks.method(item,"getContainerItem","getContainerItem",ItemStack.class).getDeclaringClass().getName().equals(owner)
                ||!ItemCallbacks.method(item,"doesContainerItemLeaveCraftingGrid","func_77630_h",ItemStack.class).getDeclaringClass().getName().equals(gridOwner==null?owner:gridOwner))
            throw fault("Native dynamic container callback owner changed: "+name);
        return object("kind","nativeContainer","callback",callback);
    }
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
}
