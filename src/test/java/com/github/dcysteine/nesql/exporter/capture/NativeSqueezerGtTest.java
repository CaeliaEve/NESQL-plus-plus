package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import forestry.api.recipes.ISqueezerRecipe;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Finite GT table dispatch with real BW callbacks, including metadata aliases and original NBT. */
final class NativeSqueezerGtTest {
    static void metacells() throws Exception {
        try(NativeRegistryScope scope=new NativeRegistryScope()){
            Class<?> utility=type("gregtech.api.util.GTUtility"), list=type("gregtech.api.enums.ItemList");
            Map<Object,Object> fluids=new HashMap<>();scope.replace(utility,null,"sFilledContainerToData",fluids);
            for(String role:new String[]{"Cell_Empty","Bottle_Empty"}){
                Object entry=field(list,null,role);
                scope.replace(list,entry,"mStack",new ItemStack(role.equals("Cell_Empty")?Items.bucket:Items.glass_bottle));
                scope.replace(list,entry,"mHasNotBeenSet",false);scope.replace(list,entry,"mDeprecated",false);
            }
            Class<?> prefixes=type("gregtech.api.enums.OrePrefixes");
            Object molten=field(prefixes,null,"cellMolten");
            scope.replace(prefixes,molten,"mContainerItem",new ItemStack(Items.bucket));
            for(String name:new String[]{"gregtech.common.items.MetaGeneratedItem98","gregtech.common.items.MetaGeneratedItem99"}){
                Item item=NativeCoreFixesTest.item(name,"squeezer_"+name.substring(name.lastIndexOf('.')+1));
                set(item,"mFluidContainerStats",new ConcurrentHashMap<Short,Long[]>());
                set(item,"mItemBehaviors",new ConcurrentHashMap<Short,Object>());
                Object key=type("gregtech.api.objects.GTItemStack").getConstructor(Item.class,long.class,long.class).newInstance(item,1L,19L);
                fluids.put(key,new FluidContainerRegistry.FluidContainerData(new FluidStack(FluidRegistry.WATER,600),new ItemStack(item,1,19),new ItemStack(Items.bowl)));
                String registry=Item.itemRegistry.getNameForObject(item);
                JsonArray unrelated=array(object("key",object("kind","item","registry","minecraft:stick")));
                SqueezerCallbacks policy=new SqueezerCallbacks(unrelated);
                JsonObject proof=policy.describe(registry,item);
                require(proof.get("kind").getAsString().equals("noContainerRule"),"Native metacell remains unsupported: "+name);
                ItemStack query=tagged(item,1,19), before=query.copy();
                ItemStack empty=(ItemStack)invoke(type("forestry.core.fluids.FluidHelper"),null,"getEmptyContainer",new Class<?>[]{ItemStack.class},query);
                require(empty!=null&&empty.getItem()==Items.bucket&&ItemStack.areItemStacksEqual(query,before),"Native metacell drain target or input ownership differs");
                boolean covered=false;for(JsonElement target:proof.getAsJsonArray("drainedRegistries"))if(target.getAsString().equals("minecraft:bucket"))covered=true;
                require(covered,"Native metacell proof omitted its empty container");
                JsonObject conflict=new SqueezerCallbacks(array(object("key",object("kind","item","registry","minecraft:bucket")))).describe(registry,item);
                require(conflict.get("kind").getAsString().equals("unsupported"),"Metacell admitted a real container-key intersection");
                policy.verify();
                if(name.endsWith("99")){
                    scope.replace(prefixes,molten,"mContainerItem",new ItemStack(Items.paper));
                    try{policy.verify();throw new AssertionError("Metacell lost prefix drift guard");}catch(Jobs.Fault changed){require(changed.code.equals("recipe_changed"),changed.toString());}
                }
            }
        }
        System.out.println("Native metacell squeezer: drain targets, original NBT, disjoint keys and prefix drift passed");
    }
    @SuppressWarnings("unchecked") static void run() throws Exception {
        try(NativeRegistryScope scope=new NativeRegistryScope()){
            Item bw=(Item)scope.allocate(type("bartworks.system.material.BWMetaGeneratedItems"));
            Field delegate=Item.class.getField("delegate");delegate.setAccessible(true);
            delegate.set(bw,new cpw.mods.fml.common.registry.RegistryDelegate.Delegate<>(bw,Item.class));
            Item.itemRegistry.addObject(30400,"fixture:squeezer_bw_capsule",bw);
            String registry=Item.itemRegistry.getNameForObject(bw);
            require(registry!=null&&bw instanceof IFluidContainerItem,"GT fixture was not registered as an actual fluid container");
            set(bw,"mFluidContainerStats",new ConcurrentHashMap<Short,Long[]>());
            set(bw,"mItemBehaviors",new ConcurrentHashMap<Short,Object>());
            set(bw,"orePrefixes",field(type("gregtech.api.enums.OrePrefixes"),null,"capsule"));
            Object entry=field(type("gregtech.api.enums.ItemList"),null,"FR_WaxCapsule");
            ItemStack emptyDefinition=new ItemStack(Items.paper,1,7);emptyDefinition.setTagCompound(new NBTTagCompound());
            emptyDefinition.getTagCompound().setString("discarded","container-only-nbt");
            scope.replace(entry.getClass(),entry,"mStack",emptyDefinition);
            scope.replace(entry.getClass(),entry,"mHasNotBeenSet",false);
            scope.replace(entry.getClass(),entry,"mDeprecated",false);
            scope.replace(entry.getClass(),entry,"mWarned",false);
            Map<Object,Object> fluids=new HashMap<>();
            scope.replace(type("gregtech.api.util.GTUtility"),null,"sFilledContainerToData",fluids);
            Object key=type("gregtech.api.objects.GTItemStack").getConstructor(Item.class,long.class,long.class).newInstance(bw,1L,17L);
            FluidContainerRegistry.FluidContainerData data=new FluidContainerRegistry.FluidContainerData(new FluidStack(FluidRegistry.WATER,600),new ItemStack(bw,1,17),new ItemStack(Items.bucket));
            fluids.put(key,data);
            Class<?> manager=type("forestry.factory.recipes.SqueezerRecipeManager");
            Set<ISqueezerRecipe> ordinary=(Set<ISqueezerRecipe>)field(manager,null,"recipes");ordinary.clear();
            Map<Object,Object> containers=(Map<Object,Object>)field(manager,null,"containerRecipes");containers.clear();
            Object nativeRule=type("forestry.factory.recipes.SqueezerContainerRecipe").getConstructor(ItemStack.class,int.class,ItemStack.class,float.class)
                    .newInstance(new ItemStack(Items.paper,1,7),5,new ItemStack(Items.stick),0.25f);
            ItemStack emptyKey=tagged(Items.paper,1,7);containers.put(emptyKey,nativeRule);
            SqueezerRules rules=new SqueezerRules(ordinary,containers);
            JsonObject program=rules.context(),callback=callback(program,registry);
            require(callback.get("kind").getAsString().equals("gtFixed"),"BW fluid capsule that can select a native rule remains unadapted");
            JsonObject row=callback.getAsJsonArray("rows").get(0).getAsJsonObject();
            require(row.get("meta").getAsInt()==17&&row.get("emptyRegistry").getAsString().equals("minecraft:paper")&&row.get("emptyMeta").getAsInt()==7,
                    "GT static fluid branch used FluidContainerData.emptyContainer instead of BW's native container getter");
            JsonArray cases=new JsonArray();
            observe(cases,"bw-capsule-real-empty-item",program,tagged(bw,1,17),manager,600);
            observe(cases,"bw-capsule-short-metadata-alias",program,tagged(bw,1,65553),manager,600);
            observe(cases,"bw-negative-count-has-no-fluid",program,tagged(bw,-1,17),manager,null);
            data.fluid.amount=0;
            SqueezerRules zero=new SqueezerRules(ordinary,containers);
            observe(cases,"bw-zero-fluid-skips-drain",zero.context(),tagged(bw,1,17),manager,null);
            data.fluid.amount=600;
            Map<Short,Long[]> stats=(Map<Short,Long[]>)field(bw,"mFluidContainerStats");stats.put((short)17,new Long[]{1000L,1L});
            containers.clear();containers.put(Items.paper,nativeRule);
            require(callback(new SqueezerRules(ordinary,containers).context(),registry).get("kind").getAsString().equals("unsupported"),
                    "NBT fluid-stat branch acquired the static table's fluid output");
            ItemStack nbtFluid=tagged(bw,1,17);nbtFluid.getTagCompound().setTag("GT.FluidContent",new FluidStack(FluidRegistry.WATER,700).writeToNBT(new NBTTagCompound()));
            ISqueezerRecipe nativeSelected=(ISqueezerRecipe)invoke(manager,null,"findMatchingRecipe",new Class<?>[]{ItemStack[].class},(Object)new ItemStack[]{nbtFluid});
            require(nativeSelected!=null&&nativeSelected.getFluidOutput().amount==700,"Actual GT NBT-stat overlap did not differ from the fixed fluid table");
            stats.clear();
            containers.clear();containers.put(emptyKey,nativeRule);
            scope.replace(entry.getClass(),entry,"mStack",new ItemStack(Items.paper,1,8));
            failsChanged(rules);
            scope.replace(entry.getClass(),entry,"mStack",emptyDefinition);
            data.fluid.amount=601;failsChanged(rules);data.fluid.amount=600;
            containers.put(bw,nativeRule);
            require(callback(new SqueezerRules(ordinary,containers).context(),registry).get("kind").getAsString().equals("unsupported"),
                    "GT source-key conflict bypassed zero-drain/alias restrictions");
            Files.write(Paths.get("build/native-tests/forestry-gt-container-observations.json"),CanonicalJson.bytes(cases));
        }
        System.out.println("Native GT/BW squeezer: real container target, short metadata aliases, zero/negative fluid selection, NBT overlap boundary and drift passed");
    }

    private static void observe(JsonArray cases,String name,JsonObject program,ItemStack offered,Class<?> manager,Integer amount){
        JsonArray before=NativeSqueezerTest.stacks(new ItemStack[]{offered});
        ISqueezerRecipe selected=(ISqueezerRecipe)invoke(manager,null,"findMatchingRecipe",new Class<?>[]{ItemStack[].class},(Object)new ItemStack[]{offered});
        require(amount==null?selected==null:selected!=null&&selected.getFluidOutput().amount==amount,"Native GT selection differs for "+name);
        require(before.equals(NativeSqueezerTest.stacks(new ItemStack[]{offered})),"Native GT reader mutated the original "+name);
        cases.add(object("name",name,"program",program,"before",before,"after",NativeSqueezerTest.stacks(new ItemStack[]{offered}),"selected",NativeSqueezerTest.recipeJson(selected)));
    }
    private static JsonObject callback(JsonObject program,String registry){for(JsonElement value:program.getAsJsonArray("dynamic")){JsonObject row=value.getAsJsonObject();if(row.get("registry").getAsString().equals(registry))return row;}throw new AssertionError("Missing GT callback");}
    private static ItemStack tagged(Item item,int amount,int meta){ItemStack result=new ItemStack(item,amount,meta);NBTTagCompound tag=new NBTTagCompound();tag.setString("retained","input-nbt");result.setTagCompound(tag);return result;}
    private static void set(Object value,String name,Object content) throws Exception {for(Class<?> owner=value.getClass();owner!=null;owner=owner.getSuperclass())try{Field field=owner.getDeclaredField(name);field.setAccessible(true);field.set(value,content);return;}catch(NoSuchFieldException ignored){}throw new AssertionError(name);}
    private static void failsChanged(SqueezerRules rules){try{rules.checkUnchanged();throw new AssertionError("GT dependency drift was missed");}catch(Jobs.Fault expected){require(expected.code.equals("recipe_changed"),"Wrong GT dependency failure "+expected.code);}}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
