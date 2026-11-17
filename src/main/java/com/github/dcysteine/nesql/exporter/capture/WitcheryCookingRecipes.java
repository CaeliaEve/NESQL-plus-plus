package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Pinned native Witchery kettle and Witching Gadgets blast furnace registry projection. */
final class WitcheryCookingRecipes implements RegistryRecipes {
    private static final String KETTLE="alkalus.main.nei.NEI_Handler_Kettle";
    private static final String BLAST="witchinggadgets.client.nei.NEIInfernalBlastfurnaceHandler";
    private static final String KETTLE_RECIPE="com.emoniph.witchery.crafting.KettleRecipes$KettleRecipe";
    private static final String BLAST_RECIPE="witchinggadgets.common.util.recipe.InfernalBlastfurnaceRecipe";
    private static final String ORE="witchinggadgets.common.util.Utilities$OreDictStack";
    private List<?> registry;
    private final List<Entry> entries=new ArrayList<>();
    private String signature;
    private final boolean blast;
    private final String handlerClass;
    private Object[] anchors;
    private NativeDefinitionGraph definitions;
    private final NativeItemExamples examplesSource=new NativeItemExamples();
    private final Map<String,List<?>> oreBindings=new TreeMap<>();
    private boolean closed;
    static boolean supportsClass(String name){return name.equals(KETTLE)||name.equals(BLAST);}
    static boolean supports(ICraftingHandler handler){return supportsClass(handler.getClass().getName());}
    static Object[] registrationAnchors(String name){
        Object owner,list;
        if(name.equals(KETTLE)){
            owner=field(type("com.emoniph.witchery.crafting.KettleRecipes"),null,"INSTANCE");
            if(owner==null)throw new Jobs.Fault("handler_not_ready","Witchery kettle registry is unavailable");
            if(owner.getClass()!=type("com.emoniph.witchery.crafting.KettleRecipes"))throw fault("Unadapted kettle registry owner");list=field(owner,"recipes");
        }else if(name.equals(BLAST)){owner=type(BLAST_RECIPE);list=field((Class<?>)owner,null,"recipes");}
        else throw fault("Unadapted Witchery cooking handler");
        if(list==null||list.getClass()!=ArrayList.class||((List<?>)list).size()>65536)throw fault("Unadapted cooking registry backing/budget");
        return name.equals(BLAST)?new Object[]{owner,list,field(OreDictionary.class,null,"nameToId"),field(OreDictionary.class,null,"idToStack")}:new Object[]{owner,list};
    }
    WitcheryCookingRecipes(TemplateRecipeHandler handler){
        if(!supports(handler))throw fault("Unadapted cooking handler");handlerClass=handler.getClass().getName();blast=handlerClass.equals(BLAST);
        try{
        if(!blast){
            MagicApi.version("WitcheryCookingRecipes","witchery");MagicApi.version("WitcheryCookingRecipes","WitcheryExtras");
        }else{
            MagicApi.version("WitcheryCookingRecipes","WitchingGadgets");
        }
        anchors=registrationAnchors(handlerClass);registry=(List<?>)anchors[1];entries.addAll(read());signature=fingerprint(entries);
        Map<String,String[]> shapes=new HashMap<>();shapes.put(KETTLE_RECIPE,new String[]{"output","inputs","power","dimension","familiarType","hatBonus","inBook"});
        shapes.put(BLAST_RECIPE,new String[]{"input","output","bonus","time","isSpecial"});shapes.put(ORE,new String[]{"key","amount"});
        for(Entry entry:entries)if(entry.ore)oreBindings.put(entry.oreKey,NativeOreTable.members(entry.oreKey));
        definitions=new NativeDefinitionGraph(this::definitionRoots,shapes);
        }catch(RuntimeException|Error failure){close();throw failure;}
    }
    private Object[] definitionRoots(){return new Object[]{registrationAnchors(handlerClass)[1],oreBindings};}
    public int size(){alive();return entries.size();}
    public void verify(){alive();Object[] current=registrationAnchors(handlerClass);for(int i=0;i<anchors.length;i++)if(current[i]!=anchors[i])throw changed();for(Map.Entry<String,List<?>> entry:oreBindings.entrySet())if(NativeOreTable.members(entry.getKey())!=entry.getValue())throw changed();definitions.verifyRoots();}
    public void finishVerification(){verify();definitions.verify();examplesSource.verify();if(!signature.equals(fingerprint(read())))throw changed();}
    public JsonObject exclusion(int index){if(!blast)return null;Entry e=entries.get(index);
        for(Example member:e.examples)if(!infernalShadowed(index,member.item,e.amount))return null;
        return object("kind","native-unreachable","reason","Every infernal input is shadowed by an earlier native recipe");
    }
    public boolean capture(int index,RecipeRow row){alive();Entry e=entries.get(index);
        if(blast){if(exclusion(index)!=null)return false;blast(index,e,row);}else kettle(index,e,row);return true;
    }
    private void blast(int index,Entry e,RecipeRow row){
        List<RecipeRow.Ingredient> choices=new ArrayList<>();
        ItemStack first=null;
        for(Example example:e.examples){
            ItemStack display=example.item.copy();display.stackSize=e.amount;
            if(infernalShadowed(index,display,e.amount))continue;
            if(first==null)first=display;
            choices.add(new RecipeRow.Ingredient(display,e.amount,false,object("kind","wildcard",
                    "meta",example.wildcard,"nbt",true)));
        }
        row.itemInput(0,choices,false);
        row.itemOutput(0,e.output.copy(),10000);
        if(e.bonus!=null){
            // TileEntityBlastfurnace starts the bonus count at zero. With no bellows it
            // rolls one 1/4 chance; with bellows each attached unit rolls at 0.44.
            row.itemOutput(1,e.bonus.copy(),10000,object("kind","potential","nominal","4",
                    "stat","witchinggadgets:blastBonus","condition","nativeBellowsRolls"));
        }
        row.record.add("process",object("kind","witcheryInfernal","selector",index,
                "input",e.ore?"ore":"stack","oreKey",e.oreKey,"inputCount",e.amount,"baseTicks",e.time,
                "specialFuel",e.special,"bonus",e.bonus!=null,"maximumBellows",4,
                "noBellowsChance",new JsonPrimitive(0.25),"perBellowsChance",new JsonPrimitive(0.44),
                "ticksPerBellows",40,"visDivisor",2,"earlier",infernalEarlier(index)));
        row.note("witchinggadgets:execution",BLAST_RECIPE+";TileEntityBlastfurnace",row.facts.locale().equals("zh_CN")
            ?"首个满足物品与库存数量的原生配方优先。Vis 加速先将基础时间减半，随后每个风箱减少 40 tick。无风箱时额外产物进行一次 1/4 抽取；有风箱时每个风箱独立按原生 0.44 概率抽取，每次成功一件，忽略注册副产物堆叠数量。特殊燃料状态和库存空间仍须满足。"
            :"The first native entry matching item and stock count wins. Vis acceleration halves base time before each bellows removes 40 ticks. Without bellows the bonus rolls once at 1/4; with bellows each attached unit rolls independently at native 0.44, adding one item per success regardless of the registered bonus stack count. Special fuel state and output space remain execution conditions.");
    }
    private void kettle(int index,Entry e,RecipeRow row){
        for(int i=0;i<e.inputs.length;i++)if(e.inputs[i]!=null){
            ItemStack raw=e.inputs[i].copy();ItemStack shown=raw.copy();shown.stackSize=1;shown.setTagCompound(null);
            row.itemInput(i,Collections.singletonList(new RecipeRow.Ingredient(shown,1,false,object("kind","wildcard","meta",false,"nbt",true))),false);
        }
        row.fluidInput(0,new FluidStack(FluidRegistry.WATER,1000),false);
        row.itemInput(6,Collections.singletonList(new RecipeRow.Ingredient(new ItemStack(Items.glass_bottle),1,false,object("kind","wildcard","meta",false,"nbt",true))),false);
        JsonObject bottleChoice=row.inputs.get(row.inputs.size()-1).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
        bottleChoice.add("consume",object("kind","allocated"));
        if(e.hatBonus>0)row.itemOutput(0,e.output.copy(),10000,object("kind","potential",
                "nominal",Long.toString((long)e.output.stackSize+e.hatBonus),"sample",Integer.toString(e.output.stackSize),
                "stat","witchery:kettleHatYield","condition","playerWitchHat"));
        else row.itemOutput(0,e.output.copy(),10000);
        row.property("witchery:inBook",row.facts.locale().equals("zh_CN")?"显示在巫术配方书":"Appears in the Witchery recipe book",e.inBook);
        row.note("witchery:kettleExecution",KETTLE_RECIPE+";BlockKettle",row.facts.locale().equals("zh_CN")
            ?"原生无序匹配每条需求各取一个匹配的占用槽。维度为零时不限制维度，非零时要求对应维度；魔宠、祭坛能量与巫师帽额外产量按原生条件判定。取出产物时每取出一件消耗一个玻璃瓶，玻璃瓶不是固定投入一次即可取走整批。"
            :"Native unordered matching takes one matching occupied slot per requirement. Dimension zero imposes no dimension restriction; a nonzero value requires that dimension. Familiar, altar power and witch-hat bonus follow native conditions. Extraction consumes one glass bottle per output item removed; one bottle does not withdraw an entire batch.");
        row.record.add("process",object("kind","witcheryKettle","selector",index,"power",new JsonPrimitive(e.power),
                "dimension",e.dimension,"familiarType",e.familiar,"baseOutput",e.output.stackSize,
                "hatBonus",e.hatBonus,"waterMillibuckets",1000,"bottlesPerItem",1));
    }
    private List<Entry> read(){List<Entry> result=new ArrayList<>();for(Object raw:registry){Jobs.checkpoint();result.add(new Entry(raw));}return result;}
    private JsonArray infernalEarlier(int index){
        JsonArray earlier=array();for(int i=0;i<index;i++){
            Entry prior=entries.get(i);ItemStack literal=prior.ore?null:prior.members.get(0);
            earlier.add(object("input",prior.ore?"ore":"stack","oreKey",prior.oreKey,
                    "registry",literal==null?null:Item.itemRegistry.getNameForObject(literal.getItem()).toString(),
                    "meta",literal==null?0:literal.getItemDamage(),
                    "metaWildcard",literal!=null&&literal.getItemDamage()==OreDictionary.WILDCARD_VALUE,
                    "inputCount",prior.amount));
        }return earlier;
    }
    private boolean infernalShadowed(int index,ItemStack offered,int count){
        for(int i=0;i<index;i++){
            Entry prior=entries.get(i);if(count<prior.amount)continue;
            if(!prior.ore&&OreDictionary.itemMatches(prior.members.get(0),offered,false))return true;
            if(prior.ore)for(int id:OreDictionary.getOreIDs(offered))
                if(prior.oreKey.equals(OreDictionary.getOreName(id)))return true;
        }return false;
    }
    private final class Entry{
        final Object raw;final ItemStack output,bonus;final ItemStack[] inputs;final List<ItemStack> members;
        final int amount,time,dimension,familiar,hatBonus;final float power;final boolean special,inBook,ore;final String oreKey,signature;
        final List<Example> examples;
        Entry(Object raw){
            this.raw=raw;if(raw==null||!raw.getClass().getName().equals(blast?BLAST_RECIPE:KETTLE_RECIPE))throw fault("Unknown Witchery cooking callback");
            output=valid((ItemStack)field(raw,"output"));
            if(blast){
                bonus=copy((ItemStack)field(raw,"bonus"));inputs=null;
                Object input=field(raw,"input");List<ItemStack> selected=new ArrayList<>();int count;
                boolean oreInput=input!=null&&input.getClass().getName().equals(ORE);
                String key=null;
                if(input instanceof ItemStack){ItemStack item=valid((ItemStack)input);count=item.stackSize;selected.add(item);}
                else if(input!=null&&input.getClass().getName().equals(ORE)){
                    key=(String)field(input,"key");count=(Integer)field(input,"amount");
                    for(ItemStack item:NativeOreTable.copies(key))selected.add(valid(item));
                }else throw fault("Unknown infernal input callback");
                if(count<=0||selected.isEmpty()||selected.size()>65536)throw fault("Invalid infernal input choices");
                amount=count;members=selected;examples=new ArrayList<>();
                for(ItemStack item:members)for(ItemStack example:examplesSource.copies(item))
                    examples.add(new Example(example,item.getItemDamage()==OreDictionary.WILDCARD_VALUE));
                if(examples.size()>65536)throw fault("Infernal metadata expansion exceeds budget");
                ore=oreInput;oreKey=key;time=(Integer)field(raw,"time");special=(Boolean)field(raw,"isSpecial");
                if(time<=0)throw fault("Invalid infernal smelting time");
                dimension=familiar=hatBonus=0;power=0;inBook=false;
            }else{
                bonus=null;members=null;examples=null;amount=time=0;special=ore=false;oreKey=null;
                ItemStack[] nativeInputs=(ItemStack[])field(raw,"inputs");
                if(nativeInputs==null||nativeInputs.length>6)throw fault("Invalid kettle input slots");
                inputs=new ItemStack[nativeInputs.length];for(int i=0;i<inputs.length;i++)inputs[i]=copy(nativeInputs[i]);
                power=(Float)field(raw,"power");dimension=(Integer)field(raw,"dimension");
                familiar=(Integer)field(raw,"familiarType");hatBonus=(Integer)field(raw,"hatBonus");inBook=(Boolean)field(raw,"inBook");
                if(!Float.isFinite(power)||power<0||hatBonus<0)throw fault("Invalid kettle state requirement");
            }
            JsonArray a=array();if(blast){for(ItemStack item:members)a.add(stack(item));}
            else for(ItemStack item:inputs)a.add(stack(item));
            JsonArray displays=array();if(blast)for(Example example:examples)displays.add(stack(example.item));
            signature=CanonicalJson.digest(object("output",stack(output),"bonus",stack(bonus),"inputs",a,"examples",displays,"oreKey",oreKey,"amount",amount,
                    "time",time,"special",special,"power",new JsonPrimitive(power),"dimension",dimension,"familiar",familiar,"hatBonus",hatBonus,"inBook",inBook));
        }
    }
    private static final class Example {
        final ItemStack item;final boolean wildcard;
        Example(ItemStack item,boolean wildcard){this.item=item;this.wildcard=wildcard;}
    }
    private static ItemStack copy(ItemStack item){return item==null?null:valid(item);}
    private static ItemStack valid(ItemStack item){
        NativeStackAudit.audit(item);
        if(item==null||item.getItem()==null||item.stackSize<=0||Item.itemRegistry.getNameForObject(item.getItem())==null)throw fault("Invalid cooking stack");
        if(ItemCallbacks.method(item.getItem(),"getDamage","getDamage",ItemStack.class).getDeclaringClass()!=Item.class)
            throw fault("Cooking input has dynamic metadata callback");
        return item.copy();
    }
    private static com.google.gson.JsonElement stack(ItemStack item){return item==null?value(null):object("id",Item.itemRegistry.getNameForObject(item.getItem()),
            "meta",item.getItemDamage(),"count",item.stackSize,"nbt",TypedNbt.encode(item.getTagCompound()));}
    private static String fingerprint(List<Entry> entries){JsonArray a=array();for(Entry e:entries)a.add(value(e.signature));return CanonicalJson.digest(a);}
    private static Jobs.Fault fault(String reason){return new Jobs.Fault("recipe_unsupported",reason);}
    private static Jobs.Fault changed(){return new Jobs.Fault("environment_changed","Native Witchery cooking definitions changed");}
    private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Witchery cooking reader is closed");}
    public void close(){closed=true;if(definitions!=null)definitions.close();examplesSource.close();registry=null;anchors=null;entries.clear();oreBindings.clear();signature=null;}
}
