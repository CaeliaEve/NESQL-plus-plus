package com.github.dcysteine.nesql.exporter.capture;

import com.google.gson.*;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.ItemFluidContainer;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Proves only callbacks that cannot select a container rule. Never executes item callbacks. */
final class SqueezerCallbacks {
    private static final String IC2="ic2.core.item.", ARMOR=IC2+"armor.";
    private final Set<String> containerRegistries;
    private final boolean ic2,ender;
    private final SqueezerForeignContainers foreign;
    private final boolean gregtech;
    private SqueezerGtContainers gt;
    SqueezerCallbacks(JsonArray rules) {
        containerRegistries=containerRegistries(rules);
        // Loader returns a copied map; take it once for this snapshot, not once per item.
        Map<String,ModContainer> mods=Loader.instance().getIndexedModList();
        ic2=version(mods,"IC2");ender=version(mods,"EnderStorage");
        foreign=new SqueezerForeignContainers(containerRegistries,mods);
        gregtech=version(mods,"gregtech_nh");
    }

    JsonObject describe(String registry,Item item){
        String kind="unsupported";
        Class<?> type=item.getClass();
        String read=owner(type,"getFluid",ItemStack.class),drain=owner(type,"drain",ItemStack.class,int.class,boolean.class);
        if(ender&&type.getName().equals("codechicken.enderstorage.common.ItemEnderStorage")
            &&read.equals(type.getName()))kind="noFluid";
        else if(!containerRegistries.contains(registry)){
            if(read.equals(ItemFluidContainer.class.getName())&&drain.equals(read))kind="forge";
            else if(ic2){
                String name=type.getName();
                if((name.equals(IC2+"ItemFluidCell")||name.equals(IC2+"tool.ItemSprayer"))
                    &&read.equals(IC2+"ItemIC2FluidContainer")&&drain.equals(read))kind="ic2";
                else if((name.equals(ARMOR+"ItemArmorCFPack")||name.equals(ARMOR+"ItemArmorJetpack"))
                    &&read.equals(ARMOR+"ItemArmorFluidTank")&&drain.equals(read))kind="ic2";
                else if(name.equals(ARMOR+"ItemArmorJetpackElectric")&&read.equals(ARMOR+"ItemArmorFluidTank")
                    &&drain.equals(name))kind="ic2";
            }
        }
        if(kind.equals("unsupported")){
            JsonObject proof=foreign.describe(registry,item);
            if(proof!=null)return proof;
            if(gregtech&&(read.equals("gregtech.api.items.MetaBaseItem")||read.equals("gtPlusPlus.xmod.gregtech.api.items.GTMetaItemBase"))){
                if(gt==null)gt=new SqueezerGtContainers(containerRegistries);
                proof=gt.describe(registry,item);if(proof!=null)return proof;
            }
        }
        return object("registry",registry,"kind",kind);
    }
    JsonArray dependencies(){return gt==null?array():gt.dependencies();}
    void verify(){if(gt!=null)gt.verify();}

    private static Set<String> containerRegistries(JsonArray rules){
        Set<String> names=new HashSet<>();
        for(JsonElement value:rules){
            JsonObject key=value.getAsJsonObject().getAsJsonObject("key");
            switch(key.get("kind").getAsString()){
                case "item":names.add(key.get("registry").getAsString());break;
                case "stack":names.add(key.getAsJsonObject("stack").get("registry").getAsString());break;
                case "ore":for(JsonElement member:key.getAsJsonArray("members"))names.add(member.getAsJsonObject().get("registry").getAsString());break;
                default:throw new IllegalArgumentException("Unknown container predicate");
            }
        }
        return names;
    }
    private static String owner(Class<?> type,String name,Class<?>... args){
        try{return type.getMethod(name,args).getDeclaringClass().getName();}
        catch(NoSuchMethodException error){return "";}
    }
    private static boolean version(Map<String,ModContainer> mods,String id){
        return TargetAdmission.matches("SqueezerCallbacks",id,mods.get(id));
    }
}
