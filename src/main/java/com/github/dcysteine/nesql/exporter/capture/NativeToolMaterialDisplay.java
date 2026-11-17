package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.source.Json.*;
import com.google.gson.JsonObject;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;

/** TGreg's raw pattern templates lack the material tag required by its native display. */
final class NativeToolMaterialDisplay {
    private static final String OWNER="vexatos.tgregworks.item.ItemTGregPart";
    private static final String VERSION="1.7.10-GTNH-1.0.28";
    private NativeToolMaterialDisplay() {}
    private static boolean needsVariant(ItemStack part){
        return part.getItem().getClass().getName().equals(OWNER)
            &&(part.getTagCompound()==null||!part.getTagCompound().hasKey("material"));
    }
    static ItemStack resolve(ItemStack part){
        if(!needsVariant(part))return null;
        MagicApi.version("NativeToolMaterialDisplay","TGregworks");
        if(part.getItem().getClass()!=MagicApi.type(OWNER))throw fault("Foreign TGreg part class");
        NBTTagCompound expected=part.getTagCompound()==null?new NBTTagCompound():(NBTTagCompound)part.getTagCompound().copy();
        ItemStack selected=null;
        for(ItemStack candidate:new NativeItemVariants(part).observedCopies()){
            if(candidate.getItemDamage()!=part.getItemDamage()||candidate.getTagCompound()==null
                ||!candidate.getTagCompound().hasKey("material",8)||candidate.getTagCompound().getString("material").isEmpty())continue;
            NBTTagCompound rest=(NBTTagCompound)candidate.getTagCompound().copy();rest.removeTag("material");
            if(!expected.equals(rest))continue;
            if(selected!=null&&!selected.getTagCompound().equals(candidate.getTagCompound()))throw fault("Ambiguous native TGreg display material");
            selected=candidate.copy();
        }
        if(selected==null)throw fault("Native TGreg inventory variant is unavailable for metadata "+part.getItemDamage());
        return selected;
    }
    static JsonObject binding(ItemStack raw,ItemStack display){
        return needsVariant(raw)?object("kind","tgregInventory","version",VERSION,"material",display.getTagCompound().getString("material")):null;
    }
    private static Jobs.Fault fault(String message){return new Jobs.Fault("tool_materials",message);}
}
