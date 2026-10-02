package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import java.lang.reflect.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Existing native runner; actual EnderIO matching and consumption, no game world or GL. */
final class NativeEnderMachinesTest {
    private static final String ROOT="crazypants.enderio.machine.";
    static void run() throws Exception {
        Object loader=cpw.mods.fml.common.Loader.instance();Field named=loader.getClass().getDeclaredField("namedMods");named.setAccessible(true);Object previous=named.get(loader);
        try {named.set(loader,Collections.emptyMap());alloy();splice();sag();}finally{named.set(loader,previous);}
    }
    @SuppressWarnings("unchecked") private static void sag() throws Exception {
        TemplateRecipeHandler handler=(TemplateRecipeHandler)type("crazypants.enderio.nei.SagMillRecipeHandler").newInstance();
        require(Recipes.adapter(handler)!=null,"Missing EnderIO SAG adapter");
        Class<?> input=type(ROOT+"recipe.RecipeInput"),output=type(ROOT+"recipe.RecipeOutput"),raw=type(ROOT+"recipe.Recipe"),bonus=type(ROOT+"recipe.RecipeBonusType");
        Object in=input.getConstructor(ItemStack.class).newInstance(new ItemStack(Items.paper));
        Object ins=Array.newInstance(input,1);Array.set(ins,0,in);
        Object outs=Array.newInstance(output,2);
        Array.set(outs,0,output.getConstructor(ItemStack.class,float.class).newInstance(new ItemStack(Items.gold_ingot),0.5f));
        Array.set(outs,1,output.getConstructor(ItemStack.class,float.class).newInstance(new ItemStack(Items.diamond),0f));
        Object recipe=raw.getConstructor(ins.getClass(),outs.getClass(),int.class,bonus).newInstance(ins,outs,1000,Enum.valueOf((Class)bonus,"MULTIPLY_OUTPUT"));
        Object ballInput=input.getConstructor(ItemStack.class).newInstance(new ItemStack(Items.flint));
        Object ball=type(ROOT+"crusher.GrindingBall").getConstructor(input,float.class,float.class,float.class,int.class).newInstance(ballInput,2.5f,2f,0.5f,10000);
        Object manager=invoke(type(ROOT+"crusher.CrusherRecipeManager"),null,"getInstance",new Class<?>[0]);
        List<Object> recipes=(List<Object>)field(manager,"recipes"),balls=(List<Object>)field(manager,"balls"),excludes=(List<Object>)field(manager,"ballExcludes");
        List<Object> priorRecipes=new ArrayList<>(recipes),priorBalls=new ArrayList<>(balls),priorExcludes=new ArrayList<>(excludes);
        Set<Object> excluded=(Set<Object>)field(manager,"excludedStacks");Set<Object> priorExcluded=new HashSet<>(excluded);
        try {
            recipes.clear();recipes.add(recipe);balls.clear();balls.add(ball);excludes.clear();excluded.clear();
            Class<?> adapter=type("com.github.dcysteine.nesql.exporter.capture.SagRecipes");
            Constructor<?> factory=adapter.getDeclaredConstructor(TemplateRecipeHandler.class,List.class,List.class,List.class);factory.setAccessible(true);
            RegistryRecipes cursor=(RegistryRecipes)factory.newInstance(handler,recipes,balls,excludes);RecipeRow row=row();
            require(cursor.capture(0,row),"SAG registry row absent");
            require(row.inputs.size()==2&&row.inputs.get(1).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("consume").get("kind").getAsString().equals("reserve"),"Grinding-ball stock became a mandatory per-recipe ingredient");
            require(row.record.getAsJsonObject("process").getAsJsonArray("balls").get(0).getAsJsonObject().get("grinding").getAsString().equals("2.5"),"Ball parameters lost");
            require(excluded.isEmpty(),"Capture mutated the native exclusion cache");
            // Skip unrelated client sound initialization; exercise the real task methods on owned inventory.
            Field access=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");access.setAccessible(true);
            Object tile=((sun.misc.Unsafe)access.get(null)).allocateInstance(type(ROOT+"crusher.TileCrusher"));
            assign(tile,"inventory",new ItemStack[6]);
            assign(tile,"slotDefinition",type(ROOT+"SlotDefinition").getConstructor(int.class,int.class).newInstance(2,4));
            assign(tile,"random",new Random(){@Override public float nextFloat(){return 0.25f;}});
            ((ItemStack[])field(tile,"inventory"))[0]=new ItemStack(Items.paper);
            Field gb=tile.getClass().getDeclaredField("gb");gb.setAccessible(true);gb.set(tile,ball);
            Object machine=type(ROOT+"crusher.CrusherMachineRecipe").newInstance();
            Object task=invoke(tile.getClass(),tile,"createTask",new Class<?>[]{type(ROOT+"IMachineRecipe"),float.class},machine,0.75f);
            require((Float)invoke(task.getClass(),task,"getChance",new Class<?>[0])==0.375f,"Initial ball chance multiplier not applied");
            require((Float)invoke(task.getClass(),task,"getRequiredEnergy",new Class<?>[0])==500f,"Initial ball power multiplier not applied");
            require(((Object[])invoke(task.getClass(),task,"getCompletedResult",new Class<?>[0])).length==1,"Shared output cutoff changed");
            assign(tile,"currentTask",task);
            invoke(tile.getClass(),tile,"taskComplete",new Class<?>[0]);
            ItemStack[] inventory=(ItemStack[])field(tile,"inventory");
            require(inventory[2]!=null&&inventory[2].getItem()==Items.gold_ingot&&inventory[2].stackSize==3&&inventory[3]==null,
                "Completion failed to reuse the task roll for three correlated passes");
            // Deprecated getOreID selects the first wildcard registration before exact metadata.
            net.minecraftforge.oredict.OreDictionary.registerOre("dustSagFirst",new ItemStack(Items.cookie,1,0));
            net.minecraftforge.oredict.OreDictionary.registerOre("blockSagWildcard",new ItemStack(Items.cookie,1,32767));
            Object dyeInput=input.getConstructor(ItemStack.class).newInstance(new ItemStack(Items.cookie,1,0));
            balls.add(ball.getClass().getConstructor(input,float.class,float.class,float.class,int.class).newInstance(dyeInput,1f,1f,1f,1000));
            ((ItemStack[])field(tile,"inventory"))[1]=new ItemStack(Items.cookie,1,0);
            int firstOre=net.minecraftforge.oredict.OreDictionary.getOreID(new ItemStack(Items.cookie,1,0));
            require(firstOre>=0&&net.minecraftforge.oredict.OreDictionary.getOreName(firstOre).equals("blockSagWildcard"),"Unexpected isolated first ore: "+firstOre);
            task=invoke(tile.getClass(),tile,"createTask",new Class<?>[]{type(ROOT+"IMachineRecipe"),float.class},machine,0.75f);
            require((Float)invoke(task.getClass(),task,"getChance",new Class<?>[0])==0.75f,"Stock-slot ore exclusion ignored");
            int before=excluded.size();cursor=(RegistryRecipes)factory.newInstance(handler,recipes,balls,excludes);row=row();cursor.capture(0,row);
            require(row.record.getAsJsonObject("process").getAsJsonArray("oreBlocked").size()>0,"Native wildcard-priority exclusion absent");
            require(excluded.size()==before,"Static ore exclusion capture called native mutating cache");
            inventory[1]=null;
            for(String mode:Arrays.asList("NONE","CHANCE_ONLY")){
                Object single=raw.getConstructor(ins.getClass(),outs.getClass(),int.class,bonus).newInstance(ins,outs,1000,Enum.valueOf((Class)bonus,mode));
                recipes.clear();recipes.add(single);
                cursor=(RegistryRecipes)factory.newInstance(handler,recipes,balls,excludes);row=row();cursor.capture(0,row);
                require(!row.record.getAsJsonObject("process").get("bonus").getAsBoolean(),"Non-multiplying recipe gained repetitions");
                task=invoke(tile.getClass(),tile,"createTask",new Class<?>[]{type(ROOT+"IMachineRecipe"),float.class},machine,0.75f);
                require((Float)invoke(task.getClass(),task,"getChance",new Class<?>[0])==0.375f,"Non-multiplying bonus suppressed initial chance multiplier");
            }
            Object largerInput=input.getConstructor(ItemStack.class).newInstance(new ItemStack(Items.paper,2));
            Object earlierInputs=Array.newInstance(input,1);Array.set(earlierInputs,0,largerInput);
            Object earlierRecipe=raw.getConstructor(ins.getClass(),outs.getClass(),int.class,bonus).newInstance(earlierInputs,outs,2000,Enum.valueOf((Class)bonus,"NONE"));
            recipes.clear();recipes.add(earlierRecipe);recipes.add(recipe);
            cursor=(RegistryRecipes)factory.newInstance(handler,recipes,balls,excludes);row=row();cursor.capture(1,row);
            require(row.record.getAsJsonObject("process").getAsJsonArray("earlier").get(0).getAsJsonObject().get("amount").getAsString().equals("2"),"Ordered quantity-sensitive selector lost");
            sagGate(handler);
            System.out.println("EnderIO SAG: native task chance/energy, stock-slot exclusions, ball table and shared outputs passed");
        } finally {recipes.clear();recipes.addAll(priorRecipes);balls.clear();balls.addAll(priorBalls);excludes.clear();excludes.addAll(priorExcludes);excluded.clear();excluded.addAll(priorExcluded);}
    }
    private static void splice() throws Exception {
        TemplateRecipeHandler handler=(TemplateRecipeHandler)type("crazypants.enderio.nei.SliceAndSpliceRecipeHandler").newInstance();
        require(Recipes.adapter(handler)!=null,"Missing EnderIO slice-and-splice adapter");
        Class<?> input=type(ROOT+"recipe.RecipeInput"),output=type(ROOT+"recipe.RecipeOutput"),raw=type(ROOT+"recipe.Recipe"),bonus=type(ROOT+"recipe.RecipeBonusType");
        Object ins=Array.newInstance(input,6),outs=Array.newInstance(output,1);
        for(int i=0;i<6;i++)Array.set(ins,i,input.getConstructor(ItemStack.class,boolean.class,float.class,int.class).newInstance(new ItemStack(Items.iron_ingot),true,1f,5-i));
        Array.set(outs,0,output.getConstructor(ItemStack.class,float.class).newInstance(new ItemStack(Items.gold_ingot),0.5f));
        Object recipe=raw.getConstructor(ins.getClass(),outs.getClass(),int.class,bonus).newInstance(ins,outs,2000,Enum.valueOf((Class)bonus,"NONE"));
        Object wrapped=type(ROOT+"recipe.BasicManyToOneRecipe").getConstructor(raw).newInstance(recipe);
        Class<?> adapter=type("com.github.dcysteine.nesql.exporter.capture.EnderAssemblyRecipes");
        Constructor<?> factory=adapter.getDeclaredConstructor(TemplateRecipeHandler.class,List.class,boolean.class);factory.setAccessible(true);
        RegistryRecipes cursor=(RegistryRecipes)factory.newInstance(handler,Arrays.asList(wrapped),false);RecipeRow row=row();
        require(cursor.capture(0,row)&&row.inputs.size()==8,"Six splice requirements and both tools must be exported");
        require(row.record.getAsJsonObject("process").get("kind").getAsString().equals("splice"),"Splice exported as ordinary alloy");
        require(row.record.getAsJsonObject("process").getAsJsonArray("slots").toString().equals("[5,4,3,2,1,0]"),"Splice consumption slots lost");
        for(int i=6;i<8;i++){
            com.google.gson.JsonObject tool=row.inputs.get(i).getAsJsonObject();
            require(tool.get("slot").getAsInt()==i,"Wrong native tool slot");
            require(tool.getAsJsonArray("choices").size()>0,"Missing registered tools");
            for(com.google.gson.JsonElement c:tool.getAsJsonArray("choices")){
                com.google.gson.JsonObject choice=c.getAsJsonObject();
                require(choice.getAsJsonObject("consume").get("kind").getAsString().equals("wear"),"Tool damage was flattened to fixed consumption");
                require(choice.getAsJsonObject("rule").get("meta").getAsBoolean()&&choice.getAsJsonObject("rule").get("nbt").getAsBoolean(),"Tool enchantment and damage states excluded");
            }
        }
        Object tile=Class.forName(ROOT+"slicensplice.TileSliceAndSplice").newInstance();
        require((Integer)invoke(tile.getClass(),tile,"getInventoryStackLimit",new Class<?>[0])==1,"Native splice inventory limit changed");
        require((Boolean)invoke(tile.getClass(),tile,"isMachineItemValidForSlot",new Class<?>[]{int.class,ItemStack.class},6,new ItemStack(Items.diamond_axe)),"Native axe admission changed");
        require(!(Boolean)invoke(tile.getClass(),tile,"isMachineItemValidForSlot",new Class<?>[]{int.class,ItemStack.class},6,new ItemStack(Items.shears)),"Shears incorrectly admitted as axe");
        require(((Object[])invoke(tile.getClass(),tile,"getRecipeInputs",new Class<?>[0])).length==6,"Tools became matching requirements");
        require(invoke(tile.getClass(),tile,"canStartNextTask",new Class<?>[]{float.class},0f)==null,"Missing tools did not block start");
        // A minimal player only supplies creative status and deterministic RNG; no world/tick or GL runs.
        Field access=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");access.setAccessible(true);
        net.minecraft.entity.player.EntityPlayer player=(net.minecraft.entity.player.EntityPlayer)((sun.misc.Unsafe)access.get(null)).allocateInstance(net.minecraft.entity.player.EntityPlayerMP.class);
        player.capabilities=new net.minecraft.entity.player.PlayerCapabilities();
        Field random=net.minecraft.entity.Entity.class.getDeclaredField("rand");random.setAccessible(true);
        random.set(player,new Random(){@Override public int nextInt(int bound){return bound-1;}});
        Field fake=tile.getClass().getDeclaredField("fakePlayer");fake.setAccessible(true);fake.set(tile,player);
        ItemStack plain=new ItemStack(Items.iron_axe);
        invoke(tile.getClass(),tile,"damageTool",new Class<?>[]{ItemStack.class,int.class},plain,6);
        require(plain.getItemDamage()==1,"Native plain tool wear changed");
        ItemStack enchanted=new ItemStack(Items.iron_axe);enchanted.addEnchantment(net.minecraft.enchantment.Enchantment.unbreaking,1);
        invoke(tile.getClass(),tile,"damageTool",new Class<?>[]{ItemStack.class,int.class},enchanted,6);
        require(enchanted.getItemDamage()==0,"Unbreaking wear was flattened to a fixed increment");
        ItemStack last=new ItemStack(Items.iron_axe);last.setItemDamage(last.getMaxDamage()-1);
        ((ItemStack[])field(tile,"inventory"))[6]=last;
        invoke(tile.getClass(),tile,"damageTool",new Class<?>[]{ItemStack.class,int.class},last,6);
        require(((ItemStack[])field(tile,"inventory"))[6]==null,"Splice exact-max tool clearing changed");
        registryGate(handler,wrapped);
        System.out.println("EnderIO splice: six requirements, complete tool classes, conditional wear and native admission passed");
    }
    private static void alloy() throws Exception {
        Class<?> raw=type(ROOT+"recipe.Recipe"), input=type(ROOT+"recipe.RecipeInput"), output=type(ROOT+"recipe.RecipeOutput"), bonus=type(ROOT+"recipe.RecipeBonusType"), offeredType=type(ROOT+"MachineRecipeInput");
        require(raw.getProtectionDomain().getCodeSource().getLocation().toString().contains("/native-tests/enderio.jar!"),"Pinned native EnderIO required");
        TemplateRecipeHandler handler=(TemplateRecipeHandler)type("crazypants.enderio.nei.AlloySmelterRecipeHandler").newInstance();
        require(Recipes.adapter(handler)!=null,"Missing EnderIO alloy adapter");
        Object a=input.getConstructor(ItemStack.class,boolean.class,float.class,int.class).newInstance(new ItemStack(Items.iron_ingot,1),true,1f,1);
        Object b=input.getConstructor(ItemStack.class,boolean.class,float.class,int.class).newInstance(new ItemStack(Items.iron_ingot,2),true,1f,0);
        Object ins=Array.newInstance(input,2);Array.set(ins,0,a);Array.set(ins,1,b);
        Object outs=Array.newInstance(output,2);
        Array.set(outs,0,output.getConstructor(ItemStack.class,float.class).newInstance(new ItemStack(Items.gold_ingot),0.5f));
        Array.set(outs,1,output.getConstructor(ItemStack.class,float.class).newInstance(new ItemStack(Items.diamond),0f));
        Object rec=raw.getConstructor(ins.getClass(),outs.getClass(),int.class,bonus).newInstance(ins,outs,1200,Enum.valueOf((Class)bonus,"NONE"));
        Object nativeRecipe=type(ROOT+"recipe.BasicManyToOneRecipe").getConstructor(raw).newInstance(rec);
        Object offered=Array.newInstance(offeredType,3);
        for(int i=0;i<3;i++)Array.set(offered,i,offeredType.getConstructor(int.class,ItemStack.class).newInstance(i,i==2?null:new ItemStack(Items.iron_ingot,i==0?1:2)));
        require((Boolean)invoke(raw,rec,"isInputForRecipe",new Class<?>[]{offered.getClass()},offered),"Native greedy matcher changed");
        Object merged=Array.newInstance(offeredType,1);Array.set(merged,0,offeredType.getConstructor(int.class,ItemStack.class).newInstance(0,new ItemStack(Items.iron_ingot,3)));
        require(!(Boolean)invoke(raw,rec,"isInputForRecipe",new Class<?>[]{merged.getClass()},merged),"Native matcher incorrectly splits one offered stack across requirements");
        Object manager=type(ROOT+"recipe.ManyToOneRecipeManager").getConstructor(String.class,String.class,String.class).newInstance("unused","unused","fixture");
        @SuppressWarnings("unchecked") List<Object> records=(List<Object>)invoke(manager.getClass(),manager,"getRecipes",new Class<?>[0]);records.add(nativeRecipe);
        Object machine=type(ROOT+"recipe.ManyToOneMachineRecipe").getConstructor(String.class,String.class,manager.getClass()).newInstance("fixture","fixture",manager);
        List<?> used=(List<?>)invoke(machine.getClass(),machine,"getQuantitiesConsumed",new Class<?>[]{offered.getClass()},offered);
        require(used.size()==2&&((Integer)field(used.get(0),"slotNumber"))==1&&((ItemStack)field(used.get(0),"item")).stackSize==1
            &&((Integer)field(used.get(1),"slotNumber"))==0&&((ItemStack)field(used.get(1),"item")).stackSize==1,"Native consumption does not follow declared slots independently");
        require(((Object[])invoke(machine.getClass(),machine,"getCompletedResult",new Class<?>[]{float.class,offered.getClass()},0f,offered)).length==2,"Zero chance endpoint lost");
        require(((Object[])invoke(machine.getClass(),machine,"getCompletedResult",new Class<?>[]{float.class,offered.getClass()},0.25f,offered)).length==1,"Shared roll selection changed");
        require(((Object[])invoke(machine.getClass(),machine,"getCompletedResult",new Class<?>[]{float.class,offered.getClass()},0.75f,offered)).length==0,"Shared roll cutoff changed");
        Class<?> adapter=type("com.github.dcysteine.nesql.exporter.capture.EnderAssemblyRecipes");
        Constructor<?> factory=adapter.getDeclaredConstructor(TemplateRecipeHandler.class,List.class,boolean.class);factory.setAccessible(true);
        RegistryRecipes cursor=(RegistryRecipes)factory.newInstance(handler,records,false);RecipeRow row=row();
        require(cursor.size()==1&&cursor.capture(0,row),"Missing alloy native registry row");
        require(row.record.getAsJsonObject("process").getAsJsonArray("slots").toString().equals("[1,0]"),"Declared consumption slots lost");
        require(row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("consume").get("kind").getAsString().equals("allocated"),"Greedy requirement was exported as fixed physical slot debit");
        require(row.outputs.size()==2&&row.outputs.get(1).getAsJsonObject().getAsJsonObject("quantity").get("threshold").getAsString().equals("0.0"),"Native zero-threshold output removed");
        require(((ItemStack)invoke(input,a,"getInput",new Class<?>[0])).stackSize==1&&((ItemStack)field(Array.get(offered,1),"item")).stackSize==2,"Source or offered inputs mutated");
        Object many=Array.newInstance(input,4);for(int i=0;i<4;i++)Array.set(many,i,input.getConstructor(ItemStack.class).newInstance(new ItemStack(Items.iron_ingot)));
        Object impossible=raw.getConstructor(many.getClass(),outs.getClass(),int.class,bonus).newInstance(many,outs,1200,Enum.valueOf((Class)bonus,"NONE"));
        require(!(Boolean)invoke(raw,impossible,"isInputForRecipe",new Class<?>[]{offered.getClass()},offered),"Four positive requirements unexpectedly fit three offered slots");
        Object impossibleWrapper=type(ROOT+"recipe.BasicManyToOneRecipe").getConstructor(raw).newInstance(impossible);
        cursor=(RegistryRecipes)factory.newInstance(handler,Arrays.asList(impossibleWrapper),false);
        require(!cursor.capture(0,row()),"Impossible four-requirement recipe exported");
        try {factory.newInstance(handler,records,true);throw new AssertionError("Enabled furnace silently omitted");}
        catch(InvocationTargetException expected){require(expected.getCause() instanceof com.github.dcysteine.nesql.exporter.task.Jobs.Fault,"Unexpected enabled-furnace failure");}
        registryGate(handler,nativeRecipe);
        System.out.println("EnderIO alloy: native greedy allocation, separate slotted consumption, shared roll endpoints and owned capture passed");
    }
    @SuppressWarnings("unchecked") private static void registryGate(TemplateRecipeHandler handler,Object recipe)throws Exception {
        Object loader=cpw.mods.fml.common.Loader.instance();Field named=loader.getClass().getDeclaredField("namedMods");named.setAccessible(true);Object mods=named.get(loader);
        cpw.mods.fml.common.ModContainer mod=(cpw.mods.fml.common.ModContainer)Proxy.newProxyInstance(NativeEnderMachinesTest.class.getClassLoader(),new Class<?>[]{cpw.mods.fml.common.ModContainer.class},(proxy,method,args)->{
            if(method.getName().equals("getVersion"))return "2.9.28";throw new AssertionError("Unexpected mod query: "+method.getName());});
        boolean splice=handler.getClass().getName().contains("SliceAndSplice");
        Object manager=invoke(type(ROOT+(splice?"slicensplice.SliceAndSpliceRecipeManager":"alloy.AlloyRecipeManager")),null,"getInstance",new Class<?>[0]);
        List<Object> recipes=(List<Object>)invoke(manager.getClass(),manager,"getRecipes",new Class<?>[0]);List<Object> previous=new ArrayList<>(recipes);
        Object vanilla=splice?null:invoke(manager.getClass(),manager,"getVanillaRecipe",new Class<?>[0]);boolean enabled=!splice&&(Boolean)field(vanilla,"enabled");
        Map<Object,Object> registry=(Map<Object,Object>)field(field(type(ROOT+"MachineRecipeRegistry"),null,"instance"),"machineRecipes");
        Object key=field(field(type("crazypants.enderio.ModObject"),null,splice?"blockSliceAndSplice":"blockAlloySmelter"),"unlocalisedName"), prior=registry.get(key);
        Map<String,Object> selectors=new LinkedHashMap<>();
        selectors.put("alloy",type(ROOT+"recipe.ManyToOneMachineRecipe").getConstructor(String.class,String.class,type(ROOT+"recipe.ManyToOneRecipeManager")).newInstance("alloy",key,manager));if(!splice)selectors.put("furnace",vanilla);
        try {
            named.set(loader,Collections.singletonMap("EnderIO",mod));recipes.clear();recipes.add(recipe);registry.put(key,selectors);
            if(!splice)invoke(vanilla.getClass(),vanilla,"setEnabled",new Class<?>[]{boolean.class},false);
            require(new EnderAssemblyRecipes(handler).size()==1,"Production registry gate failed native selector");
            selectors.put("unknown",new Object());
            try {new EnderAssemblyRecipes(handler);throw new AssertionError("Unknown machine selector ignored");}
            catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected){require(expected.code.equals("recipe_unsupported"),"Wrong unknown-selector error");}
        } finally {
            named.set(loader,mods);recipes.clear();recipes.addAll(previous);if(!splice)invoke(vanilla.getClass(),vanilla,"setEnabled",new Class<?>[]{boolean.class},enabled);
            if(prior==null)registry.remove(key);else registry.put(key,prior);
        }
    }
    @SuppressWarnings("unchecked") private static RecipeRow row(){Facts facts=new Facts("en_US");for(Object registered:Item.itemRegistry){Item item=(Item)registered;((Set<String>)field(facts,"items")).add(Identity.item(Item.itemRegistry.getNameForObject(item),0,TypedNbt.encode(null)));}return new RecipeRow(facts,object("owner","fixture","handler","alloy","key","alloy"),"fixture",0);}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void assign(Object target,String name,Object value)throws Exception {
        for(Class<?> c=target.getClass();c!=null;c=c.getSuperclass())try {Field f=c.getDeclaredField(name);f.setAccessible(true);f.set(target,value);return;}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    @SuppressWarnings("unchecked") private static void sagGate(TemplateRecipeHandler handler)throws Exception {
        Object loader=cpw.mods.fml.common.Loader.instance();Field named=loader.getClass().getDeclaredField("namedMods");named.setAccessible(true);Object mods=named.get(loader);
        cpw.mods.fml.common.ModContainer mod=(cpw.mods.fml.common.ModContainer)Proxy.newProxyInstance(NativeEnderMachinesTest.class.getClassLoader(),new Class<?>[]{cpw.mods.fml.common.ModContainer.class},(proxy,method,args)->{
            if(method.getName().equals("getVersion"))return "2.9.28";throw new AssertionError("Unexpected mod query");});
        Map<Object,Object> registry=(Map<Object,Object>)field(field(type(ROOT+"MachineRecipeRegistry"),null,"instance"),"machineRecipes");
        Object key=field(field(type("crazypants.enderio.ModObject"),null,"blockSagMill"),"unlocalisedName"),prior=registry.get(key);
        Map<String,Object> selectors=new LinkedHashMap<>();selectors.put("crusher",type(ROOT+"crusher.CrusherMachineRecipe").newInstance());
        try {
            named.set(loader,Collections.singletonMap("EnderIO",mod));registry.put(key,selectors);
            require(new SagRecipes(handler).size()==2,"Native SAG registry gate rejected valid selector");
            selectors.put("unknown",new Object());
            try {new SagRecipes(handler);throw new AssertionError("Unknown SAG selector ignored");}
            catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected){require(expected.code.equals("recipe_unsupported"),"Wrong selector error");}
        } finally {named.set(loader,mods);if(prior==null)registry.remove(key);else registry.put(key,prior);}
    }
}
