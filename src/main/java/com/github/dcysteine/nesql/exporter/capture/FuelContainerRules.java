package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Exhausted-stack rules, never calls random, world, network or container callbacks. */
final class FuelContainerRules {
    private static final String RAIL="mods.railcraft.common.items.firestone.";
    private static final String CRYSTAL="WayofTime.alchemicalWizardry.common.items.LavaCrystal";
    private final ItemStack specimen;
    private final State initial;

    static FuelContainerRules open(ItemStack exhausted) {
        String name=exhausted.getItem().getClass().getName();
        if (!name.equals(RAIL+"ItemFirestoneRefined") && !name.equals(RAIL+"ItemFirestoneCracked") && !name.equals(CRYSTAL)) return null;
        return new FuelContainerRules(exhausted);
    }
    private FuelContainerRules(ItemStack exhausted) { specimen=exhausted.copy();initial=read();verify(); }

    JsonObject declaration(Function<ItemStack,JsonObject> container,Function<ItemStack,String> item) {
        verify();
        JsonObject rule=new com.google.gson.JsonParser().parse(initial.rule.toString()).getAsJsonObject();
        if(rule.get("kind").getAsString().equals("railcraftFirestone")){
            rule.addProperty("degradedBase",item.apply(initial.base.copy()));
            rule.add("normalReturn",value(initial.normal==null?null:container.apply(initial.normal.copy())));
            rule.add("degradedReturn",value(initial.degraded==null?null:container.apply(initial.degraded.copy())));
        }else rule.add("returned",container.apply(initial.normal.copy()));
        return rule;
    }
    void verify() {
        State current=read();
        if(!CanonicalJson.digest(initial.evidence()).equals(CanonicalJson.digest(current.evidence()))
                ||initial.anchors.size()!=current.anchors.size())throw changed();
        for(int index=0;index<initial.anchors.size();index++)if(initial.anchors.get(index)!=current.anchors.get(index))throw changed();
    }
    private State read() {
        Jobs.checkpoint();State state=new State();Item item=specimen.getItem();state.anchors.add(item);
        fixedMetadata(item);
        if(item.getClass().getName().equals(CRYSTAL)){
            MagicApi.version("FuelContainerRules","AWWayofTime");
            int cost=((Number)field(item,"energyUsed")).intValue();
            state.rule=object("kind","bloodMagicLavaCrystal","energyUsed",cost,
                    "resourcePolicy","ownerNameServerWorldZeroCreateNetworkDebitIfEnough");
            state.normal=specimen.copy();state.normal.setItemDamage(Items.feather.getDamage(specimen));state.normal.stackSize=1;
            return state;
        }
        MagicApi.version("FuelContainerRules","Railcraft");
        boolean refined=item.getClass().getName().equals(RAIL+"ItemFirestoneRefined");
        Item target=(Item)field(type(RAIL+(refined?"ItemFirestoneCracked":"ItemFirestoneRaw")),null,"item");
        if(target==null)throw new Jobs.Fault("handler_not_ready","Native firestone degradation item is unregistered");
        if(!target.getClass().getName().equals(RAIL+(refined?"ItemFirestoneCracked":"ItemFirestoneRaw")))throw fault("Unadapted native firestone degradation item");
        fixedMetadata(target);state.anchors.add(target);
        int maximum=((Number)itemField(item,"maxDamage","field_77699_b")).intValue();
        int targetMaximum=((Number)itemField(target,"maxDamage","field_77699_b")).intValue();
        state.rule=object("kind","railcraftFirestone","stage",refined?"refined":"cracked","sourceMaxDamage",maximum,
                "degradationThreshold",Double.toString((double)Items.feather.getDamage(specimen)/(double)maximum*0.0001),
                "comparison","nextDoubleLessThanDamageFractionTimes1e-4","damageStep",1);
        state.normal=specimen.copy();state.normal.stackSize=1;state.normal=damage(state.normal,maximum);
        state.base=new ItemStack(target,1,0);
        if(refined){
            state.degraded=new ItemStack(target,1,targetMaximum-1);
            if(specimen.hasDisplayName())state.degraded.setStackDisplayName(specimen.getTagCompound().getCompoundTag("display").getString("Name"));
            state.degraded=damage(state.degraded,targetMaximum);
        }else state.degraded=new ItemStack(target,1,0);
        state.targetMaximum=targetMaximum;
        return state;
    }
    private static Object itemField(Item item,String mcp,String srg){
        return cpw.mods.fml.relauncher.ReflectionHelper.getPrivateValue(Item.class,item,mcp,srg);
    }
    private static ItemStack damage(ItemStack stack,int maximum){
        NBTTagCompound tag=stack.getTagCompound();
        if(maximum<=0||tag!=null&&tag.getBoolean("Unbreakable"))return stack;
        stack.setItemDamage(Items.feather.getDamage(stack)+1);
        if(Items.feather.getDamage(stack)>maximum)return null;
        return stack;
    }
    private static void fixedMetadata(Item item){
        for(String[] names:new String[][]{{"getDamage","getDamage"},{"getMaxDamage","getMaxDamage"}})
            if(ItemCallbacks.method(item,names[0],names[1],ItemStack.class).getDeclaringClass()!=Item.class)throw fault("Unadapted fuel metadata/durability callback");
        if(ItemCallbacks.method(item,"getMaxDamage","func_77612_l").getDeclaringClass()!=Item.class
                ||ItemCallbacks.method(item,"setDamage","setDamage",ItemStack.class,int.class).getDeclaringClass()!=Item.class)
            throw fault("Unadapted fuel durability callback");
    }
    private static final class State {
        final List<Object> anchors=new ArrayList<>();
        JsonObject rule;ItemStack base,normal,degraded;int targetMaximum;
        JsonObject evidence(){return object("rule",rule,"base",RecipeRow.stackEvidence(base),"normal",RecipeRow.stackEvidence(normal),
                "degraded",RecipeRow.stackEvidence(degraded),"targetMaximum",targetMaximum);}
    }
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
    private static Jobs.Fault changed(){return new Jobs.Fault("recipe_changed","Native dynamic fuel container definition changed");}
}
