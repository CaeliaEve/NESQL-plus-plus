package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Three audited native transformations with bounded, correlated display observations. */
final class IntegrationRecipes implements RegistryRecipes {
    private static final String HANDLER="buildcraft.compat.nei.RecipeHandlerIntegrationTable";
    private static final int BATCH=128;
    private final List<?> registry, snapshot;
    private final Object manager;
    private final List<Family> families=new ArrayList<>();
    private int size;
    private boolean closed;

    static boolean supports(IRecipeHandler handler){return handler.getClass().getName().equals(HANDLER);}
    IntegrationRecipes(TemplateRecipeHandler handler){this(handler,registrationSource(handler));}
    static Source registrationSource(IRecipeHandler handler){
        if(!supports(handler))throw fault("Unknown integration handler");
        MagicApi.version("IntegrationRecipes","BuildCraft|Silicon");MagicApi.version("IntegrationRecipes","BuildCraft|Compat");
        Object manager=field(type("buildcraft.api.recipes.BuildcraftRecipeRegistry"),null,"integrationTable");
        if(manager==null)throw new Jobs.Fault("handler_not_ready","Integration manager is unavailable");
        if(!manager.getClass().getName().equals("buildcraft.core.recipes.IntegrationRecipeManager"))throw fault("Unknown integration manager");
        Object rows=field(manager,"integrationRecipes");
        if(rows==null)throw new Jobs.Fault("handler_not_ready","Integration registry is unavailable");
        if(rows.getClass()!=LinkedList.class||invoke(manager.getClass(),manager,"getRecipes",new Class<?>[0])!=rows)throw fault("Integration registry lost its native source");
        return new Source(manager,(List<?>)rows);
    }
    static final class Source{final Object manager;final List<?> recipes;Source(Object manager,List<?> recipes){this.manager=manager;this.recipes=recipes;}}
    private IntegrationRecipes(TemplateRecipeHandler handler,Source source){this(handler,source.recipes,source.manager);}
    IntegrationRecipes(TemplateRecipeHandler handler,List<?> registry){
        this(handler,registry,null);
    }
    private IntegrationRecipes(TemplateRecipeHandler handler,List<?> registry,Object manager){
        if(registry==null)throw new Jobs.Fault("handler_not_ready","Integration registry is unavailable");
        if(!supports(handler)||registry.size()>3)throw fault("Unadapted integration registry");
        this.manager=manager;this.registry=registry;this.snapshot=new ArrayList<>(registry);
        Set<String> kinds=new HashSet<>();
        for(Object raw:snapshot){
            Family family=new Family(raw,size);
            // Disjoint primary classes have no priority overlap. Duplicate family rules
            // require active-recipe retention/priority to be modeled explicitly.
            if(!kinds.add(family.rule.kind))throw fault("Ambiguous duplicate integration family: "+family.rule.kind);
            families.add(family);size+=family.batches;
        }
    }
    public int size(){verify();return size;}
    public void verify(){
        if(closed)throw new Jobs.Fault("capture_closed","Integration reader is closed");
        if(manager!=null&&(field(type("buildcraft.api.recipes.BuildcraftRecipeRegistry"),null,"integrationTable")!=manager
                ||field(manager,"integrationRecipes")!=registry))throw changed("native manager or list changed");
        if(registry.size()!=snapshot.size())throw new Jobs.Fault("recipe_changed","Integration registry size changed");
        for(int i=0;i<snapshot.size();i++)if(registry.get(i)!=snapshot.get(i))throw new Jobs.Fault("recipe_changed","Integration registry order changed");
        for(Family family:families)family.rule.verify();
    }
    public void finishVerification(){
        verify();for(Family family:families){Jobs.checkpoint();family.rule.finishVerification();
            if(!family.signature.equals(new Family(family.nativeRecipe,family.start).signature))throw changed("native examples or rule context changed");}
    }
    public void close(){if(closed)return;closed=true;snapshot.clear();families.clear();size=0;}
    public boolean capture(int index,RecipeRow row){
        verify();
        try{captureRow(index,row);}catch(RuntimeException|Error failure){
            try{verify();}catch(RuntimeException|Error drift){if(drift!=failure)failure.addSuppressed(drift);}throw failure;
        }
        verify();return true;
    }
    private void captureRow(int index,RecipeRow row){
        if(index<0||index>=size)throw fault("Invalid integration batch index");
        Family family=null;for(Family value:families)if(index>=value.start&&index<value.start+value.batches){family=value;break;}
        if(family==null)throw fault("Missing integration family");
        int from=(index-family.start)*BATCH,to=Math.min(from+BATCH,family.count);
        List<ItemStack[]> samples=new ArrayList<>();List<ItemStack> outputs=new ArrayList<>();
        List<LinkedHashMap<String,ItemStack>> choices=new ArrayList<>();for(int slot=0;slot<9;slot++)choices.add(new LinkedHashMap<>());
        for(int sample=from;sample<to;sample++){
            Jobs.checkpoint();ItemStack[] offered=family.sample(sample);
            List<ItemStack> expansions=Arrays.asList(Arrays.copyOfRange(offered,1,9));
            IntegrationRules.Observation preview=family.rule.observe(offered[0],expansions,true);
            IntegrationRules.Observation complete=family.rule.observe(offered[0],expansions,false);
            if(preview.output==null||complete.output==null)throw fault("Native integration example has no output: "+family.rule.kind+" sample="+sample);
            if(preview.output.stackSize!=complete.output.stackSize||!id(preview.output).equals(id(complete.output)))throw fault("Integration preview and completion disagree");
            samples.add(offered);outputs.add(preview.output.copy());
            for(int slot=0;slot<9;slot++)if(offered[slot]!=null)choices.get(slot).putIfAbsent(id(offered[slot]),offered[slot]);
        }
        List<Map<String,Integer>> columns=new ArrayList<>();List<Integer> slots=new ArrayList<>();
        for(int slot=0;slot<9;slot++)if(!choices.get(slot).isEmpty()){
            List<RecipeRow.Ingredient> ingredients=new ArrayList<>();Map<String,Integer> column=new HashMap<>();
            for(Map.Entry<String,ItemStack> choice:choices.get(slot).entrySet()){
                column.put(choice.getKey(),ingredients.size());ItemStack stack=choice.getValue().copy();
                ingredients.add(new RecipeRow.Ingredient(stack,1,false,object("kind","integration")));
            }
            row.itemInput(slot,ingredients,false);
            for(JsonElement choice:row.inputs.get(row.inputs.size()-1).getAsJsonObject().getAsJsonArray("choices"))choice.getAsJsonObject().add("consume",object("kind","allocated"));
            columns.add(column);slots.add(slot);
        }
        JsonArray bindings=new JsonArray(),products=new JsonArray();
        for(int sample=0;sample<samples.size();sample++){
            JsonArray tuple=new JsonArray();for(int column=0;column<slots.size();column++){
                ItemStack offered=samples.get(sample)[slots.get(column)];tuple.add(offered==null?JsonNull.INSTANCE:value(columns.get(column).get(id(offered))));
            }
            bindings.add(tuple);ItemStack output=outputs.get(sample);products.add(object("id",row.facts.item(output),"amount",Integer.toString(output.stackSize)));
        }
        ItemStack first=outputs.get(0);
        row.itemOutput(0,first,10000);
        row.outputs.get(0).getAsJsonObject().add("change",object("input",0,"action",object("kind","integration"),"bindings",bindings,"samples",products));
        row.record.add("process",object("kind","buildcraftIntegration","rule",family.rule.context()));
        if(CanonicalJson.bytes(row.record).length>1000000)throw fault("Integration example batch exceeds the source record budget");
    }
    private static final class Family {
        final IntegrationRules rule;final List<ItemStack> inputs,expansions,wires;
        final int start,count,batches;
        final Object nativeRecipe;final String signature;
        Family(Object raw,int start){
            nativeRecipe=raw;
            rule=new IntegrationRules(raw);this.start=start;
            inputs=stacks(invoke(raw.getClass(),raw,"generateExampleInput",new Class<?>[0]));
            Object generated=invoke(raw.getClass(),raw,"generateExampleExpansions",new Class<?>[0]);
            if(!(generated instanceof List))throw fault("Invalid integration expansion examples");
            List<?> groups=(List<?>)generated;
            if(groups.size()!=(rule.kind.equals("facade")?2:1))throw fault("Integration expansion groups changed");
            expansions=new ArrayList<>(stacks(groups.get(0)));
            wires=rule.kind.equals("facade")?stacks(groups.get(1)):Collections.emptyList();
            if(rule.kind.equals("gate")){
                Object red=field(type("buildcraft.silicon.ItemRedstoneChipset$Chipset"),null,"RED");
                expansions.add(0,one((ItemStack)invoke(red.getClass(),red,"getStack",new Class<?>[0])));
            }
            if(inputs.isEmpty()||expansions.isEmpty()||rule.kind.equals("facade")&&wires.isEmpty())throw fault("Integration family has no native display examples");
            // Cover each registered facade as an expansion and a primary, without
            // materializing every facade x facade pairing. The rule defines the domain.
            long total=rule.kind.equals("facade")?(long)expansions.size()*wires.size()+inputs.size()
                :(long)inputs.size()*(rule.kind.equals("gate")?expansions.size()+1:expansions.size()*2);
            if(total>262144)throw fault("Integration display example budget exceeded");
            count=(int)total;batches=(count+BATCH-1)/BATCH;
            signature=CanonicalJson.digest(object("context",rule.context(),"inputs",facts(inputs),"expansions",facts(expansions),"wires",facts(wires),"count",count));
        }
        ItemStack[] sample(int index){
            ItemStack[] slots=new ItemStack[9];
            if(rule.kind.equals("facade")){
                int expanded=expansions.size()*wires.size();
                if(index<expanded){slots[0]=inputs.get(0);slots[1]=expansions.get(index/wires.size());slots[2]=wires.get(index%wires.size());}
                else {slots[0]=inputs.get(index-expanded);slots[1]=wires.get(0);slots[8]=expansions.get(0);}
            }else if(rule.kind.equals("gate")){
                int stride=expansions.size()+1,choice=index%stride;slots[0]=inputs.get(index/stride);
                slots[1]=expansions.get(choice==expansions.size()?0:choice);if(choice==expansions.size())slots[8]=expansions.get(0);
            }else{
                int stride=expansions.size()*2,choice=index%stride;slots[0]=inputs.get(index/stride);
                slots[choice<expansions.size()?1:8]=expansions.get(choice%expansions.size());
            }
            // Independent owned slots are intentional: the value contract has no
            // Java object alias graph, including when one registry stack appears twice.
            for(int slot=0;slot<slots.length;slot++)if(slots[slot]!=null)slots[slot]=slots[slot].copy();
            return slots;
        }
    }
    private static JsonArray facts(List<ItemStack> values){JsonArray result=array();for(ItemStack value:values){Jobs.checkpoint();result.add(RecipeRow.stackEvidence(value));}return result;}
    private static List<ItemStack> stacks(Object values){
        if(!(values instanceof List)||((List<?>)values).size()>65536)throw fault("Invalid integration example list");
        List<ItemStack> result=new ArrayList<>();for(Object value:(List<?>)values){Jobs.checkpoint();if(!(value instanceof ItemStack))throw fault("Invalid integration example");result.add(one((ItemStack)value));}return result;
    }
    private static ItemStack one(ItemStack input){
        if(input.getItem()==null||input.stackSize<=0)throw fault("Invalid integration example stack");
        ItemStack result=input.copy();result.stackSize=1;return result;
    }
    private static String id(ItemStack stack){
        String name=Item.itemRegistry.getNameForObject(stack.getItem());
        if(name==null||Item.itemRegistry.getObject(name)!=stack.getItem())throw fault("Integration example uses an unregistered item");
        return Identity.item(name,Items.feather.getDamage(stack),TypedNbt.encode(stack.getTagCompound()));
    }
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
    private static Jobs.Fault changed(String message){return new Jobs.Fault("recipe_changed","Integration "+message);}
}
