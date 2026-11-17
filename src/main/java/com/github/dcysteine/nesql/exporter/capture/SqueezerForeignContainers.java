package com.github.dcysteine.nesql.exporter.capture;

import com.google.gson.*;
import cpw.mods.fml.common.ModContainer;
import net.minecraft.item.*;
import net.minecraftforge.fluids.ItemFluidContainer;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Proves read effects and copied-item drain identities; never invokes an item callback. */
final class SqueezerForeignContainers {
    private final Set<String> keys;
    private final Map<String,ModContainer> mods;
    private final SqueezerCallbackClasses classes=new SqueezerCallbackClasses();
    SqueezerForeignContainers(Set<String> keys,Map<String,ModContainer> mods){this.keys=keys;this.mods=mods;}

    JsonObject describe(String registry,Item item){
        Class<?> type=item.getClass();String name=type.getName();
        JsonObject canister=canister(registry,item,"micdoodle8.mods.galacticraft.core.items.ItemCanisterGeneric","GalacticraftCore",
                "micdoodle8.mods.galacticraft.core.items.GCItems","oilCanister");
        if(canister!=null)return canister;
        canister=canister(registry,item,"galaxyspace.core.item.ItemCanisterGeneric","GalaxySpace",
                "galaxyspace.core.register.GSItems","BioFuelCanister");
        if(canister!=null)return canister;
        if(keys.contains(registry)||!owner(type,"getFluid",ItemStack.class).equals(name)
                ||!owner(type,"drain",ItemStack.class,int.class,boolean.class).equals(name))return null;
        String id,effect="preserve";int minimum=Integer.MIN_VALUE,maximum=Integer.MAX_VALUE;
        String[] dependencies={name};JsonArray compound=array();
        switch(name){
            case "WayofTime.alchemicalWizardry.common.items.sigil.SigilFluid":id="AWWayofTime";break;
            case "tconstruct.smeltery.itemblocks.LavaTankItemBlock":id="TConstruct";break;
            case "com.glodblock.github.common.item.ItemCertusQuartzTank":id="ae2fc";break;
            case "witchinggadgets.common.items.ItemCrystalCapsule":
                id="WitchingGadgets";
                if(!owner(type,"getFluidStored",ItemStack.class).equals(name))return null;break;
            case "gregtech.common.items.ItemVolumetricFlask":
                id="gregtech_nh";
                if(!owner(type,"setFluid",ItemStack.class,net.minecraftforge.fluids.FluidStack.class).equals(name))return null;break;
            case "gtPlusPlus.core.item.tool.misc.ItemGregtechPump":
                id="gregtech_nh";effect="gtppPump";
                if(!Short.valueOf((short)1000).equals(field(item,"mOffset")))return null;
                dependencies=new String[]{name,"gtPlusPlus.core.util.minecraft.NBTUtils",
                        "gtPlusPlus.core.util.minecraft.FluidUtils","gregtech.api.util.GTUtility"};break;
            case "crazypants.enderio.machine.tank.BlockItemTank":
                id="EnderIO";compound.add(value("tankContents"));
                dependencies=new String[]{name,"crazypants.enderio.machine.tank.TileTank","crazypants.enderio.tool.SmartTank"};break;
            case "com.rwtema.extrautils.item.ItemBlockDrum":
                id="ExtraUtilities";
                Object slave=field(item,"slaveItem");
                if(!owner(type,"setCapacityFromMeta",int.class).equals(name)||slave==null||slave.getClass()!=ItemFluidContainer.class)return null;
                dependencies=new String[]{name,"com.rwtema.extrautils.tileentity.TileEntityDrum"};break;
            case "com.gtnewhorizons.gravisuiteneo.items.ItemPlasmaCell":
                id="gravisuiteneo";effect="ensureCompound";break;
            case "tuhljin.automagy.blocks.ItemBlockThirstyTank":
                id="Automagy";
                if(!version("hodgepodge"))return null;
                dependencies=new String[]{name,"com.mitchej123.hodgepodge.mixins.late.automagy.MixinItemBlockThirstyTank"};break;
            case "gregtech.common.blocks.ItemMachines":
                id="gregtech_nh";
                Object tiles=field(type("gregtech.api.GregTechAPI"),null,"METATILEENTITIES");
                if(tiles==null||!tiles.getClass().isArray())return null;
                minimum=0;maximum=java.lang.reflect.Array.getLength(tiles)-1;if(maximum<0)return null;break;
            default:return null;
        }
        if(!version(id)||!classes.pinned(type.getClassLoader(),dependencies))return null;
        return object("registry",registry,"kind","noContainerRule","drainedRegistries",array(registry),
                "readEffect",effect,"minMeta",minimum,"maxMeta",maximum,"compoundTags",compound);
    }
    private JsonObject canister(String registry,Item item,String base,String mod,String holder,String role){
        Class<?> type=item.getClass();
        if(!owner(type,"getFluid",ItemStack.class).equals(base)||!owner(type,"drain",ItemStack.class,int.class,boolean.class).equals(base)
                ||!declaredOwner(type,"setNewDamage",ItemStack.class,int.class).equals(base)
                ||!owner(type,"setDamage",ItemStack.class,int.class).equals(Item.class.getName())
                ||!version(mod)||!classes.pinned(type.getClassLoader(),base))return null;
        Object raw=field(type(holder),null,role);if(!(raw instanceof Item))return null;
        Item empty=(Item)raw;String target=Item.itemRegistry.getNameForObject(empty);int id=Item.getIdFromItem(empty);
        if(target==null||id<0||id>32767||keys.contains(registry)||keys.contains(target))return null;
        JsonArray targets=array();targets.add(value(registry));if(!registry.equals(target))targets.add(value(target));
        return object("registry",registry,"kind","noContainerRule","drainedRegistries",targets,"readEffect","preserve",
                "minMeta",Integer.MIN_VALUE,"maxMeta",Integer.MAX_VALUE,"compoundTags",array());
    }
    private boolean version(String id){return TargetAdmission.matches("SqueezerForeignContainers",id,mods.get(id));}
    private static String owner(Class<?> type,String method,Class<?>... args){
        try{return type.getMethod(method,args).getDeclaringClass().getName();}
        catch(NoSuchMethodException failure){return "";}
    }
    private static String declaredOwner(Class<?> type,String method,Class<?>... args){
        for(Class<?> current=type;current!=null;current=current.getSuperclass())try{current.getDeclaredMethod(method,args);return current.getName();}catch(NoSuchMethodException ignored){}
        return "";
    }
}
