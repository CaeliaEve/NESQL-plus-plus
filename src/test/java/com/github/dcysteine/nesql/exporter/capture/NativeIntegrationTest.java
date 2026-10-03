package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.google.gson.*;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import java.lang.reflect.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Pinned native craft calls on isolated objects, without world ticks or mod startup. */
final class NativeIntegrationTest {
    private static final String TRANSPORT="buildcraft.transport.", ROBOTICS="buildcraft.robotics.";
    private static Item gate,wire,facade,board,robot,chip;
    private static final JsonArray observations=new JsonArray();
    static void run() throws Exception {
        gate=item(TRANSPORT+"gates.ItemGate",30001,"gate");
        wire=item(TRANSPORT+"ItemPipeWire",30002,"wire");
        facade=item(TRANSPORT+"ItemFacade",30003,"facade");
        board=item(ROBOTICS+"ItemRedstoneBoard",30004,"board");
        robot=item(ROBOTICS+"ItemRobot",30005,"robot");
        chip=item("buildcraft.silicon.ItemRedstoneChipset",30006,"chip");
        assign("buildcraft.BuildCraftTransport","pipeGate",gate);
        assign("buildcraft.BuildCraftTransport","pipeWire",wire);
        assign("buildcraft.BuildCraftTransport","facadeItem",facade);
        assign("buildcraft.BuildCraftTransport","plugItem",Items.stick);
        assign("buildcraft.BuildCraftRobotics","redstoneBoard",board);
        assign("buildcraft.BuildCraftRobotics","robotItem",robot);
        assign("buildcraft.BuildCraftRobotics","blacklistedRobots",new ArrayList<String>());
        assign("buildcraft.BuildCraftSilicon","redstoneChipset",chip);
        Object registry=type(ROBOTICS+"ImplRedstoneBoardRegistry").newInstance();
        assign("buildcraft.api.boards.RedstoneBoardRegistry","instance",registry);
        Object empty=field(type(ROBOTICS+"boards.RedstoneBoardRobotEmptyNBT"),null,"instance");
        invoke(registry.getClass(),registry,"setEmptyRobotBoard",new Class<?>[]{type("buildcraft.api.boards.RedstoneBoardRobotNBT")},empty);
        Object expansion=field(type(TRANSPORT+"gates.GateExpansionPulsar"),null,"INSTANCE");
        invoke(type("buildcraft.api.gates.GateExpansions"),null,"registerExpansion",new Class<?>[]{type("buildcraft.api.gates.IGateExpansion"),ItemStack.class},expansion,new ItemStack(Items.diamond));
        try { Class.forName("com.github.dcysteine.nesql.exporter.capture.IntegrationRules"); }
        catch (ClassNotFoundException e) { throw new AssertionError("Integration table lacks owned multi-input native observations",e); }
        gates(); facades(); robots(); adapter();
        java.nio.file.Files.write(java.nio.file.Paths.get("build/native-tests/integration-observations.json"),CanonicalJson.bytes(object("native","BuildCraft 7.1.44","cases",observations)));
        System.out.println("Native integration: ordered multi-expansion transformations, preview migration, consumption, owned failure and callback guards passed");
    }
    private static void gates() throws Exception {
        Object raw=type(TRANSPORT+"recipes.GateExpansionRecipe").newInstance(), rules=rules(raw);
        ItemStack input=new ItemStack(gate,5,23);input.setTagInfo("owner",new NBTTagInt(7));
        ItemStack red=(ItemStack)invoke(type("buildcraft.silicon.ItemRedstoneChipset$Chipset"),field(type("buildcraft.silicon.ItemRedstoneChipset$Chipset"),null,"RED"),"getStack",new Class<?>[0]);
        List<ItemStack> slots=Arrays.asList(red,new ItemStack(Items.diamond,3),null,new ItemStack(Items.diamond,2),red.copy(),new ItemStack(Items.paper),null,red.copy());
        Object result=observe(rules,input,slots,false); ItemStack out=(ItemStack)field(result,"output");
        require(out.stackSize==1&&out.getItemDamage()==23&&out.getTagCompound().getInteger("owner")==7&&out.getTagCompound().getByte("logic")==1,"Gate reconstruction lost primary state or repeated toggles");
        require(out.getTagCompound().getTagList("ex",8).tagCount()==1,"Gate duplicate expansion added twice");
        List<?> after=(List<?>)field(result,"expansions");
        require(((ItemStack)after.get(0)).stackSize==1&&((ItemStack)after.get(1)).stackSize==2&&after.get(2)==null&&((ItemStack)after.get(3)).stackSize==2,"Gate expansion consumption or physical holes changed");
        require(input.stackSize==5&&!input.getTagCompound().hasKey("logic")&&slots.get(1).stackSize==3,"Gate observation mutated caller items");
        require(field(observe(rules,out,Collections.singletonList(new ItemStack(Items.diamond)),false),"output")==null,"Already installed gate expansion produced a recipe");
        Object preview=observe(rules,input,slots,true);require(((ItemStack)((List<?>)field(preview,"expansions")).get(1)).stackSize==3,"Native preview consumed a chipset");
        // RED toggles are not consumed even when two toggles restore the original logic.
        ItemStack unchanged=(ItemStack)field(observe(rules,input,Arrays.asList(red,red.copy()),false),"output");
        require(unchanged!=null&&unchanged.getTagCompound().getByte("logic")==0,"Even toggle count incorrectly treated as no change");
        input.setTagInfo("logic",new NBTTagInt(257));input.setTagInfo("ex",new NBTTagString("invalid-list"));
        out=(ItemStack)field(observe(rules,input,Arrays.asList(red,new ItemStack(Items.diamond)),false),"output");
        require(out.getTagCompound().getByte("logic")==0&&out.getTagCompound().getTagList("ex",8).tagCount()==1,"Native byte coercion or malformed expansion list reset lost");
        NBTTagList typedEmpty=new NBTTagList();typedEmpty.appendTag(new NBTTagInt(1));typedEmpty.removeTag(0);input.setTagInfo("ex",typedEmpty);
        Object emptyList=observe(rules,input,Collections.singletonList(new ItemStack(Items.diamond,2)),false);
        require(((ItemStack)field(emptyList,"output")).getTagCompound().getTagList("ex",8).tagCount()==0&&((ItemStack)((List<?>)field(emptyList,"expansions")).get(0)).stackSize==1,"Native typed-empty list rejected append must still consume the chipset and return a result");
        Class<?> api=type("buildcraft.api.recipes.IIntegrationRecipe");
        Object unknown=Proxy.newProxyInstance(api.getClassLoader(),new Class<?>[]{api},(p,m,a)->{throw new AssertionError("Unknown integration callback executed");});
        fails(()->rules(unknown),"recipe_unsupported");
        List<ItemStack> excess=new ArrayList<>(Collections.nCopies(9,red));fails(()->observe(rules,input,excess,false),"recipe_unsupported");
        // Mutation after binding must not silently alter the captured rule.
        @SuppressWarnings("unchecked") Map<Object,ItemStack> recipes=(Map<Object,ItemStack>)field(raw,"recipes");
        ItemStack anchor=recipes.values().iterator().next();anchor.stackSize=2;
        fails(()->observe(rules,input,slots,false),"recipe_changed");anchor.stackSize=1;
        Class<?> expansionApi=type("buildcraft.api.gates.IGateExpansion");
        Object arbitrary=Proxy.newProxyInstance(expansionApi.getClassLoader(),new Class<?>[]{expansionApi},(p,m,a)->{
            if(m.getName().equals("hashCode"))return 123;
            if(m.getName().equals("equals"))return p==a[0];
            throw new AssertionError("Unknown expansion callback executed: "+m);
        });
        recipes.put(arbitrary,new ItemStack(Items.emerald));
        fails(()->rules(raw),"recipe_unsupported");recipes.remove(arbitrary);
        Object existing=recipes.keySet().iterator().next();ItemStack previous=recipes.get(existing);
        Item callback=new Item(){@Override public boolean getHasSubtypes(){throw new AssertionError("Custom subtype callback executed");}};
        Item.itemRegistry.addObject(30010,"fixture:integration_callback",callback);
        recipes.put(existing,new ItemStack(callback));fails(()->rules(raw),"recipe_unsupported");recipes.put(existing,previous);
    }
    private static void facades() throws Exception {
        Object rules=rules(type(TRANSPORT+"recipes.AdvancedFacadeRecipe").newInstance());
        ItemStack input=new ItemStack(facade,3,19);input.setTagInfo("name",new NBTTagString("minecraft:stone"));input.setTagInfo("owner",new NBTTagInt(99));
        ItemStack extra=new ItemStack(facade,4);extra.setTagInfo("name",new NBTTagString("minecraft:dirt"));extra.setTagInfo("meta",new NBTTagInt(258));
        List<ItemStack> slots=Arrays.asList(extra,new ItemStack(wire,2,99));
        Object preview=observe(rules,input,slots,true);ItemStack out=(ItemStack)field(preview,"output");
        require(out.getItem()==facade&&out.getItemDamage()==0&&out.stackSize==1&&out.getTagCompound().func_150296_c().size()==2,"Facade result should have fresh canonical tags");
        NBTTagList states=out.getTagCompound().getTagList("states",10);
        require(states.tagCount()==2&&states.getCompoundTagAt(1).getByte("wire")==0&&states.getCompoundTagAt(1).getByte("metadata")==2,"Facade wire fallback or signed-byte serialization lost");
        require(!((ItemStack)field(preview,"input")).getTagCompound().hasKey("name")&&((ItemStack)((List<?>)field(preview,"expansions")).get(0)).stackSize==4,"Preview migration or no-consumption semantics lost");
        require(input.getTagCompound().hasKey("name")&&extra.getTagCompound().hasKey("name"),"Preview migrated borrowed registry examples");
        Object actual=observe(rules,input,slots,false);List<?> after=(List<?>)field(actual,"expansions");
        require(((ItemStack)after.get(0)).stackSize==3&&((ItemStack)after.get(1)).stackSize==1,"Facade did not consume both selected expansions");
        require(field(observe(rules,input,Arrays.asList(new ItemStack(Items.stick),new ItemStack(wire)),true),"output")==null,"Admission-only plug was invented as a transparent facade output");
        List<ItemStack> malformed=Arrays.asList(new ItemStack(wire,3),new ItemStack(wire,4));
        fails(()->observe(rules,input,malformed,false),"recipe_capture");
        require(malformed.get(0).stackSize==3&&malformed.get(1).stackSize==4&&input.getTagCompound().hasKey("name"),"Native failed craft leaked partial mutations");
        states.getCompoundTagAt(1).setBoolean("hollow",true);
        ItemStack replacement=(ItemStack)field(observe(rules,out,slots,false),"output");
        require(replacement.getTagCompound().getTagList("states",10).tagCount()==2,"Same wire appended instead of replacing first state");
        ItemStack missing=new ItemStack(facade);missing.setTagInfo("name",new NBTTagString("missing:block"));
        ItemStack air=(ItemStack)field(observe(rules,input,Arrays.asList(missing,new ItemStack(wire)),true),"output");
        require(air.getTagCompound().getTagList("states",10).getCompoundTagAt(1).getString("block").equals("minecraft:air"),"Defaulted block registry lookup should resolve an unknown name to air");
        for(String name:new String[]{"stone","MINECRAFT:stone"}) {
            ItemStack named=new ItemStack(facade);named.setTagInfo("name",new NBTTagString(name));
            observe(rules,input,Arrays.asList(named,new ItemStack(wire)),true);
        }
        ItemStack hollow=extra.copy();
        NBTTagCompound state=new NBTTagCompound();state.setString("block","minecraft:dirt");state.setBoolean("hollow",true);state.setBoolean("transparent",true);state.setInteger("metadata",255);
        NBTTagList list=new NBTTagList();list.appendTag(state);hollow.setTagCompound(new NBTTagCompound());hollow.setTagInfo("states",list);
        ItemStack opaque=(ItemStack)field(observe(rules,input,Arrays.asList(hollow,new ItemStack(wire,1,2)),true),"output");
        NBTTagCompound transformed=opaque.getTagCompound().getTagList("states",10).getCompoundTagAt(1);
        require(transformed.getBoolean("hollow")&&!transformed.getBoolean("transparent")&&transformed.getByte("metadata")==-1,"Facade output must retain hollow while clearing transparency and wrapping metadata");
    }
    private static void robots() throws Exception {
        Object nativeRecipe=type(ROBOTICS+"RobotIntegrationRecipe").newInstance();
        Object registry=field(type("buildcraft.api.boards.RedstoneBoardRegistry"),null,"instance");
        Object configured=type(ROBOTICS+"boards.BCBoardNBT").getConstructor(String.class,String.class,Class.class,String.class).newInstance("fixture:miner","miner",type(ROBOTICS+"boards.BoardRobotEmpty"),"miner");
        invoke(registry.getClass(),registry,"registerBoardType",new Class<?>[]{type("buildcraft.api.boards.RedstoneBoardNBT"),int.class},configured,100);
        Object rules=rules(nativeRecipe);
        for(int energy:new int[]{0,-5,17000}) {
            ItemStack input=new ItemStack(robot,4,9);input.setTagInfo("energy",new NBTTagInt(energy));input.setTagInfo("owner",new NBTTagString("discarded"));
            // Automation admits non-board items: native static getBoardNBT still handles their NBT.
            ItemStack expansion=new ItemStack(Items.paper,3);expansion.setTagInfo("id",new NBTTagString("unregistered-board"));
            Object observed=observe(rules,input,Collections.singletonList(expansion),false);ItemStack out=(ItemStack)field(observed,"output");
            require(out.getItem()==robot&&out.getItemDamage()==0&&out.stackSize==1&&out.getTagCompound().func_150296_c().size()==2,"Robot did not construct a fresh output");
            require(out.getTagCompound().getInteger("energy")== (energy==0?20000:energy)&&out.getTagCompound().getCompoundTag("board").getString("id").equals("buildcraft:boardRobotEmpty"),"Robot fallback board or zero-energy substitution changed");
            require(((ItemStack)((List<?>)field(observed,"expansions")).get(0)).stackSize==2&&expansion.stackSize==3&&!input.getTagCompound().hasKey("id"),"Robot count or ownership changed");
        }
        ItemStack input=new ItemStack(robot),expansion=new ItemStack(board,2);expansion.setTagInfo("id",new NBTTagString("fixture:miner"));expansion.setTagInfo("parameters",new NBTTagString("must-not-copy"));
        Object chosen=observe(rules,input,Arrays.asList(null,expansion),true);ItemStack out=(ItemStack)field(chosen,"output");
        require(out.getTagCompound().getCompoundTag("board").func_150296_c().size()==1&&out.getTagCompound().getCompoundTag("board").getString("id").equals("fixture:miner"),"Robot copied board source NBT instead of creating a fresh board");
        require(((ItemStack)field(chosen,"input")).getTagCompound().getString("id").equals("buildcraft:boardRobotEmpty")&&!input.hasTagCompound(),"Robot missing-board migration should affect only the owned primary");
        Object blank=observe(rules,input,Collections.singletonList(new ItemStack(board)),true);
        require(((ItemStack)((List<?>)field(blank,"expansions")).get(0)).getTagCompound().getString("id").equals("buildcraft:boardRobotEmpty"),"Missing expansion id was not initialized by native empty-board serializer");
        require(field(observe(rules,input,Collections.emptyList(),false),"output")==null,"Machine empty expansion guard was omitted");
        fails(()->observe(rules,new ItemStack(Items.paper),Collections.singletonList(expansion),true),"recipe_unsupported");
        // The snapshot must notice registry replacement, including aliases that retain a class.
        Field id=configured.getClass().getDeclaredField("id");id.setAccessible(true);id.set(configured,"fixture:changed");
        fails(()->observe(rules,input,Collections.singletonList(expansion),true),"recipe_changed");id.set(configured,"fixture:miner");
    }
    @SuppressWarnings("unchecked") private static void adapter() throws Exception {
        assign("buildcraft.api.transport.PipeWire","item",wire);
        codechicken.nei.recipe.TemplateRecipeHandler handler=(codechicken.nei.recipe.TemplateRecipeHandler)type("buildcraft.compat.nei.RecipeHandlerIntegrationTable").newInstance();
        require(Recipes.adapter(handler)!=null,"Integration table has no production adapter route");
        List<ItemStack> facades=(List<ItemStack>)field(type(TRANSPORT+"ItemFacade"),null,"allFacades");
        List<ItemStack> prior=new ArrayList<>(facades);facades.clear();
        for(String block:new String[]{"minecraft:stone","minecraft:dirt"}){ItemStack f=new ItemStack(facade);f.setTagInfo("name",new NBTTagString(block));facades.add(f);}
        try {
            List<Object> nativeRecipes=new ArrayList<>();
            nativeRecipes.add(type(TRANSPORT+"recipes.GateExpansionRecipe").newInstance());
            nativeRecipes.add(type(TRANSPORT+"recipes.AdvancedFacadeRecipe").newInstance());
            nativeRecipes.add(type(ROBOTICS+"RobotIntegrationRecipe").newInstance());
            RegistryRecipes adapter=adapter(handler,nativeRecipes);
            require(adapter.size()==3,"Small integration registry should use one bounded row per family");
            Map<String,ItemStack> known=new HashMap<>();
            for(Object raw:nativeRecipes){
                List<ItemStack> inputs=(List<ItemStack>)invoke(raw.getClass(),raw,"generateExampleInput",new Class<?>[0]);
                List<List<ItemStack>> groups=(List<List<ItemStack>>)invoke(raw.getClass(),raw,"generateExampleExpansions",new Class<?>[0]);
                List<ItemStack> extras=new ArrayList<>();for(List<ItemStack> group:groups)extras.addAll(group);
                if(raw.getClass().getName().contains("GateExpansion"))extras.add((ItemStack)invoke(type("buildcraft.silicon.ItemRedstoneChipset$Chipset"),field(type("buildcraft.silicon.ItemRedstoneChipset$Chipset"),null,"RED"),"getStack",new Class<?>[0]));
                for(ItemStack s:inputs)known.put(id(s),s);for(ItemStack s:extras)known.put(id(s),s);
                IntegrationRules rule=new IntegrationRules(raw);
                for(ItemStack in:inputs)for(ItemStack a:extras){
                    if(rule.kind.equals("facade")){for(ItemStack b:extras)if(a.getItem()==facade&&b.getItem()==wire){ItemStack out=rule.observe(in,Arrays.asList(a,b),true).output;known.put(id(out),out);}}
                    else {ItemStack out=rule.observe(in,Collections.singletonList(a),true).output;known.put(id(out),out);
                        if(rule.kind.equals("gate")){out=rule.observe(in,Arrays.asList(a,a.copy()),true).output;if(out!=null)known.put(id(out),out);}}
                }
            }
            boolean hole=false;JsonArray records=new JsonArray();
            for(int index=0;index<adapter.size();index++){
                RecipeRow row=knownRow(known);require(adapter.capture(index,row),"Integration row was silently excluded");
                JsonObject process=row.record.getAsJsonObject("process"),change=row.outputs.get(0).getAsJsonObject().getAsJsonObject("change");
                require(process.get("kind").getAsString().equals("buildcraftIntegration")&&row.record.get("duration").isJsonNull()&&row.record.get("energy").isJsonNull(),"Integration became fixed time/EU");
                JsonArray bindings=change.getAsJsonArray("bindings"),samples=change.getAsJsonArray("samples");
                require(bindings.size()==samples.size()&&bindings.size()<=128,"Integration tuples are missing or unbounded");
                IntegrationRules rule=new IntegrationRules(nativeRecipes.get(index));
                for(int sample=0;sample<bindings.size();sample++){
                    List<ItemStack> slots=new ArrayList<>(Collections.nCopies(8,null));ItemStack primary=null;JsonArray tuple=bindings.get(sample).getAsJsonArray();
                    for(int column=0;column<row.inputs.size();column++){
                        if(tuple.get(column).isJsonNull()){hole=true;continue;}
                        JsonObject input=row.inputs.get(column).getAsJsonObject();int slot=input.get("slot").getAsInt();
                        String fact=input.getAsJsonArray("choices").get(tuple.get(column).getAsInt()).getAsJsonObject().get("id").getAsString();
                        ItemStack stack=known.get(fact).copy();if(slot==0)primary=stack;else slots.set(slot-1,stack);
                    }
                    ItemStack expected=rule.observe(primary,slots,false).output;
                    require(samples.get(sample).getAsJsonObject().get("id").getAsString().equals(id(expected)),"Integration correlated product differs from native craft");
                }
                handler.arecipes.get(0).getIngredients().get(0).items[0].stackSize=123;
                handler.arecipes.get(0).getResult().items[0].stackSize=123;
                RecipeRow repeated=knownRow(known);adapter.capture(index,repeated);
                require(row.inputs.equals(repeated.inputs)&&row.outputs.equals(repeated.outputs),"Display mutation escaped into integration samples");
                row.finish();records.add(row.record);
            }
            require(hole,"Integration examples lost optional physical slots");
            require(facades.get(0).getTagCompound().hasKey("name"),"Integration capture migrated borrowed facade registry");
            fails(()->adapter(handler,Arrays.asList(nativeRecipes.get(0),nativeRecipes.get(0))),"recipe_unsupported");
            for(int i=0;i<130;i++)facades.add(facades.get(i%2).copy());
            RegistryRecipes bounded=adapter(handler,Collections.singletonList(nativeRecipes.get(1)));
            require(bounded.size()>1&&bounded.size()<20,"Facade examples form an unbounded cartesian product");
            RecipeRow tail=knownRow(known);require(bounded.capture(bounded.size()-1,tail),"Last integration batch was omitted");
            tail.finish();records.add(tail.record);
            JsonArray facts=new JsonArray();for(String key:new TreeSet<>(known.keySet()))facts.add(stack(known.get(key)));
            java.nio.file.Files.write(java.nio.file.Paths.get("build/native-tests/integration-adapter-records.json"),CanonicalJson.bytes(object("items",facts,"recipes",records)));
            nativeRecipes.remove(0);fails(()->adapter.capture(0,knownRow(known)),"recipe_changed");
        } finally {facades.clear();facades.addAll(prior);}
    }
    private static RegistryRecipes adapter(codechicken.nei.recipe.TemplateRecipeHandler handler,List<?> recipes)throws Exception{
        Constructor<?> ctor=type("com.github.dcysteine.nesql.exporter.capture.IntegrationRecipes").getDeclaredConstructor(codechicken.nei.recipe.TemplateRecipeHandler.class,List.class);ctor.setAccessible(true);
        try{return (RegistryRecipes)ctor.newInstance(handler,recipes);}catch(InvocationTargetException e){throw (Exception)e.getCause();}
    }
    private static String id(ItemStack stack){return com.github.dcysteine.nesql.exporter.source.Identity.item(Item.itemRegistry.getNameForObject(stack.getItem()),Items.feather.getDamage(stack),TypedNbt.encode(stack.getTagCompound()));}
    @SuppressWarnings("unchecked") private static RecipeRow knownRow(Map<String,ItemStack> known){Facts facts=new Facts("en_US");((Set<String>)field(facts,"items")).addAll(known.keySet());return new RecipeRow(facts,object("owner","BuildCraft|Silicon","handler","fixture","key","integration"),"category_test",0);}
    private static Item item(String cls,int id,String name)throws Exception{Item item=(Item)type(cls).newInstance();Item.itemRegistry.addObject(id,"fixture:integration_"+name,item);return item;}
    private static void assign(String cls,String name,Object value)throws Exception{type(cls).getField(name).set(null,value);}
    private static Object rules(Object nativeRecipe)throws Exception{
        Constructor<?> c=type("com.github.dcysteine.nesql.exporter.capture.IntegrationRules").getDeclaredConstructor(Object.class);c.setAccessible(true);
        try{return c.newInstance(nativeRecipe);}catch(InvocationTargetException e){if(e.getCause() instanceof Error)throw (Error)e.getCause();throw (Exception)e.getCause();}
    }
    private static Object observe(Object rules,ItemStack input,List<ItemStack> expansions,boolean preview){
        JsonObject evidence=object("rule",invoke(rules.getClass(),rules,"context",new Class<?>[0]),"input",stack(input),"expansions",stacks(expansions),"preview",preview);
        try {
            Object result=invoke(rules.getClass(),rules,"observe",new Class<?>[]{ItemStack.class,List.class,boolean.class},input,expansions,preview);
            evidence.add("after",object("input",stack((ItemStack)field(result,"input")),"expansions",stacks((List<?>)field(result,"expansions")),"output",stack((ItemStack)field(result,"output"))));
            observations.add(evidence);return result;
        } catch(Jobs.Fault fault){evidence.addProperty("error",fault.code);observations.add(evidence);throw fault;}
    }
    private static JsonArray stacks(List<?> values){JsonArray array=new JsonArray();for(Object value:values)array.add(stack((ItemStack)value));return array;}
    private static JsonElement stack(ItemStack stack){return stack==null?JsonNull.INSTANCE:object("registry",Item.itemRegistry.getNameForObject(stack.getItem()),"meta",Items.feather.getDamage(stack),"amount",stack.stackSize,"nbt",TypedNbt.encode(stack.getTagCompound()));}
    private interface Checked {void run()throws Exception;}
    private static void fails(Checked operation,String code)throws Exception{try{operation.run();throw new AssertionError("Expected "+code);}catch(Jobs.Fault fault){require(fault.code.equals(code),"Wrong failure: "+fault);}}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
