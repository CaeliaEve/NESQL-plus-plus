package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Registered SAG recipes; ball state belongs to the shared process, not per-slot chances. */
final class SagRecipes implements RegistryRecipes {
    private static final String ROOT="crazypants.enderio.machine.", HANDLER="crazypants.enderio.nei.SagMillRecipeHandler";
    private final TemplateRecipeHandler handler;
    private final List<Entry> entries=new ArrayList<>();
    private final List<Ball> balls=new ArrayList<>();
    private final List<EnderInputs.Pattern> stock=new ArrayList<>(),blocked=new ArrayList<>(),oreBlocked;
    static boolean supports(ICraftingHandler h){return h.getClass().getName().equals(HANDLER);}
    SagRecipes(TemplateRecipeHandler h){this(h,registry());}
    private SagRecipes(TemplateRecipeHandler h,Object manager){this(h,(List<?>)call(manager,"getRecipes"),(List<?>)call(manager,"getBalls"),(List<?>)field(manager,"ballExcludes"));}
    private static Object registry(){
        version("EnderIO","2.9.28");
        Object registry=field(type(ROOT+"MachineRecipeRegistry"),null,"instance");
        Object machine=field(type("crazypants.enderio.ModObject"),null,"blockSagMill");
        Map<?,?> selectors=(Map<?,?>)((Map<?,?>)field(registry,"machineRecipes")).get(field(machine,"unlocalisedName"));
        if(selectors==null||selectors.size()!=1||selectors.values().iterator().next()==null||!selectors.values().iterator().next().getClass().getName().equals(ROOT+"crusher.CrusherMachineRecipe"))
            throw fault("Unknown or absent SAG runtime selector");
        return invoke(type(ROOT+"crusher.CrusherRecipeManager"),null,"getInstance",new Class<?>[0]);
    }
    SagRecipes(TemplateRecipeHandler h,List<?> source,List<?> ballSource,List<?> exclusions){
        if(!supports(h)||source==null||source.size()>262144||ballSource==null||ballSource.size()>4096||exclusions==null||exclusions.size()>65536)
            throw fault("Invalid SAG registry size");
        handler=h;
        List<EnderInputs.Pattern> relevant=new ArrayList<>();
        for(Object record:source){Jobs.checkpoint();Entry e=new Entry(record);entries.add(e);if(e.inputs.size()==1)relevant.addAll(e.inputs.get(0).patterns);}
        for(Object record:ballSource){Ball b=new Ball(record);balls.add(b);stock.addAll(b.patterns);}
        for(Object record:exclusions)if(record!=null){EnderInputs.check(record);blocked.addAll(EnderInputs.patterns(record));}
        relevant.addAll(stock);oreBlocked=oreBlocks(relevant);
    }
    public int size(){return entries.size();}
    public boolean capture(int index,RecipeRow row){
        Entry e=entries.get(index);
        // The native selector offers exactly one material stack to the positive requirements.
        if(e.inputs.size()!=1||e.inputs.get(0).patterns.isEmpty())return false;
        Input in=e.inputs.get(0);
        input(row,0,in.patterns,in.amount,74,2,"allocated");
        if(!stock.isEmpty())input(row,1,stock,1,116,12,"reserve");
        JsonArray earlier=array(),ballTable=array();
        for(int i=0;i<index;i++){
            Entry previous=entries.get(i);
            if(previous.inputs.size()!=1)continue;
            Input p=previous.inputs.get(0);List<EnderInputs.Pattern> overlap=new ArrayList<>();
            for(EnderInputs.Pattern pattern:p.patterns)if(in.patterns.stream().anyMatch(pattern::overlaps))overlap.add(pattern);
            if(!overlap.isEmpty())earlier.add(object("amount",Integer.toString(p.amount),"choices",cases(row,overlap)));
        }
        for(Ball b:balls)ballTable.add(object("choices",cases(row,b.patterns),"grinding",Float.toString(b.grinding),"chance",Float.toString(b.chance),"power",Float.toString(b.power),"duration",b.duration));
        row.record.add("process",object("kind","sag","energy",e.energy,"slot",in.slot,"bonus",e.bonus,"earlier",earlier,"balls",ballTable,
            "blocked",cases(row,intersections(blocked,in.patterns)),"oreBlocked",cases(row,intersections(oreBlocked,combined(in.patterns,stock)))));
        for(int i=0;i<e.outputs.size();i++){
            Output out=e.outputs.get(i);
            row.itemOutput(i<4?new PositionedStack(out.item.copy(),43+21*i,46,false):null,i,out.item.copy(),10000,
                object("kind","grinding","nominal",Integer.toString(out.item.stackSize),"threshold",Float.toString(out.chance)));
        }
        handler.arecipes.clear();handler.arecipes.add(handler.new CachedRecipe(){@Override public PositionedStack getResult(){return null;}});
        return true;
    }
    private static void input(RecipeRow row,int slot,List<EnderInputs.Pattern> patterns,int amount,int x,int y,String consume){
        List<RecipeRow.Ingredient> choices=new ArrayList<>();List<ItemStack> display=new ArrayList<>();
        for(EnderInputs.Pattern p:patterns){choices.add(new RecipeRow.Ingredient(p.item.copy(),amount,false,p.rule()));display.add(p.item.copy());}
        row.itemInput(new PositionedStack(display,x,y,false),slot,choices,false);
        for(JsonElement c:row.inputs.get(row.inputs.size()-1).getAsJsonObject().getAsJsonArray("choices"))c.getAsJsonObject().add("consume",object("kind",consume));
    }
    private static JsonArray cases(RecipeRow row,List<EnderInputs.Pattern> patterns){
        JsonArray result=array();Set<String> seen=new HashSet<>();
        for(EnderInputs.Pattern p:patterns){Jobs.checkpoint();JsonObject c=object("id",row.facts.item(p.item.copy()),"rule",p.rule());if(seen.add(c.toString()))result.add(c);}
        if(result.size()>65536)throw fault("SAG predicate count exceeds budget");
        return result;
    }
    private static List<EnderInputs.Pattern> combined(List<EnderInputs.Pattern> a,List<EnderInputs.Pattern> b){List<EnderInputs.Pattern> result=new ArrayList<>(a);result.addAll(b);return result;}
    private static List<EnderInputs.Pattern> intersections(List<EnderInputs.Pattern> patterns,List<EnderInputs.Pattern> relevant){
        List<EnderInputs.Pattern> result=new ArrayList<>();
        for(EnderInputs.Pattern p:patterns)for(EnderInputs.Pattern r:relevant){EnderInputs.Pattern both=p.intersect(r);if(both!=null)result.add(both);}
        return result;
    }
    private static List<EnderInputs.Pattern> oreBlocks(List<EnderInputs.Pattern> relevant){
        Set<Item> items=Collections.newSetFromMap(new IdentityHashMap<>());
        for(EnderInputs.Pattern p:relevant)items.add(p.item.getItem());
        Map<Item,SortedSet<Integer>> metadata=new IdentityHashMap<>();
        for(String name:OreDictionary.getOreNames())for(ItemStack registered:OreDictionary.getOres(name)){
            Jobs.checkpoint();if(registered!=null&&items.contains(registered.getItem()))metadata.computeIfAbsent(registered.getItem(),k->new TreeSet<>()).add(registered.getItemDamage());
        }
        List<EnderInputs.Pattern> result=new ArrayList<>();
        List<Item> ordered=new ArrayList<>(metadata.keySet());ordered.sort(Comparator.comparing(Item.itemRegistry::getNameForObject));
        for(Item item:ordered){
            SortedSet<Integer> metas=metadata.get(item);boolean wildcard=metas.contains(OreDictionary.WILDCARD_VALUE);
            // Forge's deprecated getOreID chooses wildcard registrations BEFORE exact metadata.
            for(int meta:wildcard?Collections.singleton(0):metas){
                ItemStack example=new ItemStack(item,1,meta);int id=OreDictionary.getOreID(example);
                String first=id<0?"":OreDictionary.getOreName(id);
                if(first.startsWith("ingot")||first.startsWith("block")||first.startsWith("nugget"))result.add(new EnderInputs.Pattern(example,wildcard));
            }
        }
        return result;
    }
    int[][] progressBars(){return new int[][]{{73,20,166,0,17,24,40,1},{136,12,166,24,4,16,160,11}};}
    void draw(int index){
        handler.drawBackground(0);Class<?> power=type("crazypants.enderio.power.PowerDisplayUtil");
        String energy=invoke(power,null,"formatPower",new Class<?>[]{int.class},entries.get(index).energy)+" "+invoke(power,null,"abrevation",new Class<?>[0]);
        codechicken.lib.gui.GuiDraw.drawString(energy,96,33,0x808080,false);
    }
    private static final class Entry {
        final List<Input> inputs=new ArrayList<>();final List<Output> outputs=new ArrayList<>();final int energy;final boolean bonus;
        Entry(Object record){
            if(record==null||!record.getClass().getName().equals(ROOT+"recipe.Recipe"))throw fault("Unknown SAG recipe implementation");
            Object[] ins=(Object[])call(record,"getInputs"),outs=(Object[])call(record,"getOutputs");
            if(ins==null||ins.length==0||ins.length>4096||outs==null||outs.length==0||outs.length>128)throw fault("Invalid SAG recipe shape");
            for(Object in:ins)inputs.add(new Input(in));for(Object out:outs)outputs.add(new Output(out));
            String kind=String.valueOf(call(record,"getBonusType"));if(!kind.equals("NONE")&&!kind.equals("MULTIPLY_OUTPUT")&&!kind.equals("CHANCE_ONLY"))throw fault("Unknown SAG bonus type");
            bonus=kind.equals("MULTIPLY_OUTPUT");energy=(Integer)call(record,"getEnergyRequired");
        }
    }
    private static final class Input {
        final int slot,amount;final List<EnderInputs.Pattern> patterns;
        Input(Object source){
            EnderInputs.check(source);if((Boolean)call(source,"isFluid"))throw fault("SAG has no fluid input tank");
            ItemStack item=(ItemStack)call(source,"getInput");slot=(Integer)call(source,"getSlotNumber");
            if(item==null||item.stackSize<=0||slot< -1||slot>1)throw fault("Unsupported SAG material requirement");
            amount=item.stackSize;patterns=EnderInputs.patterns(source);
        }
    }
    private static final class Output {
        final ItemStack item;final float chance;
        Output(Object source){
            if(source==null||!source.getClass().getName().equals(ROOT+"recipe.RecipeOutput")||(Boolean)call(source,"isFluid"))throw fault("Unsupported SAG result");
            ItemStack original=(ItemStack)call(source,"getOutput");chance=(Float)call(source,"getChance");
            if(original==null||original.stackSize<=0||!Float.isFinite(chance)||chance<0||chance>1)throw fault("Invalid SAG result amount or threshold");
            item=original.copy();
        }
    }
    private static final class Ball {
        final List<EnderInputs.Pattern> patterns;final float grinding,chance,power;final int duration;
        Ball(Object source){
            if(source==null||!source.getClass().getName().equals(ROOT+"crusher.GrindingBall"))throw fault("Unknown SAG grinding ball");
            Object input=field(source,"ri");EnderInputs.check(input);if((Boolean)call(input,"isFluid"))throw fault("Fluid grinding ball");
            patterns=EnderInputs.patterns(input);
            grinding=(Float)call(source,"getGrindingMultiplier");chance=(Float)call(source,"getChanceMultiplier");power=(Float)call(source,"getPowerMultiplier");duration=(Integer)call(source,"getDurationMJ");
            if(!Float.isFinite(grinding)||!Float.isFinite(chance)||!Float.isFinite(power)||grinding<0||grinding>16777218f||chance<=0||power<0)
                throw fault("Unsupported SAG ball parameters");
        }
    }
    private static Object call(Object target,String name){return invoke(target.getClass(),target,name,new Class<?>[0]);}
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
}
