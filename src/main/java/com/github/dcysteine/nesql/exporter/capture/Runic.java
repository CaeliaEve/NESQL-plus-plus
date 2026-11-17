package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.ItemList;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import thaumcraft.api.IRunicArmor;
import thaumcraft.api.crafting.InfusionRecipe;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import static com.github.dcysteine.nesql.exporter.source.Json.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.lib.crafting.InfusionRunicAugmentRecipe;
import thaumcraft.common.config.ConfigItems;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import java.util.*;

/** One symbolic recipe per registered IRunicArmor, not an enumeration of possible NBT. */
final class Runic {
    final InfusionRunicAugmentRecipe recipe;
    final ItemStack center;
    private final InfusionLegacyInputs inputs;
    private Runic(InfusionRunicAugmentRecipe recipe,ItemStack center,NativeItemExamples examples,InfusionLegacyInputs.Context context){
        this.recipe=recipe;this.center=center;inputs=InfusionLegacyInputs.runic(examples,context);
    }

    static final class Source implements AutoCloseable {
        private InfusionRunicAugmentRecipe recipe;
        private List<ItemStack> itemList;
        private int count;
        private final List<Item> items=new ArrayList<>();
        private final List<ItemStack> representatives=new ArrayList<>();
        private final List<Runic> branches=new ArrayList<>();
        private NativeDefinitionGraph graph;
        Source(InfusionRunicAugmentRecipe recipe,NativeItemExamples examples,InfusionLegacyInputs.Context context){
            this.recipe=recipe;
            try{
                if(recipe.getClass()!=InfusionRunicAugmentRecipe.class)throw fault("Runic recipe overrides native semantics");
                itemList=ItemList.items;ready();count=itemList.size();
                items.addAll(census());Map<Item,ItemStack> first=first();
                for(Item item:items){
                    ItemStack representative=first.get(item);representatives.add(representative);
                    ItemStack center=representative==null?new ItemStack(item):representative.copy();center.stackSize=1;
                    if(net.minecraft.init.Items.feather.getDamage(center)==32767)throw fault("A runic specimen cannot be a raw wildcard template");
                    branches.add(new Runic(recipe,center,examples,context));
                }
                graph=new NativeDefinitionGraph(this::roots,NativeDefinitionGraph.shapes(thaumcraft.api.aspects.Aspect.class.getName(),"tag"));
            }catch(RuntimeException|Error failure){close();throw failure;}
        }
        private void ready(){
            if(!ItemList.loadFinished||itemList==null)throw new Jobs.Fault("handler_not_ready","Runic specimens require a finished native item list");
            if(itemList.getClass()!=ArrayList.class||itemList.size()>1048576)throw fault("Unadapted runic item-list storage or size");
        }
        private static List<Item> census(){
            List<Item> result=new ArrayList<>();
            for(Object raw:Item.itemRegistry)if(raw instanceof IRunicArmor){Jobs.checkpoint();NativeStackAudit.identityOwner(raw);result.add((Item)raw);
                if(result.size()>65536)throw fault("Runic registered item budget exceeded");}
            result.sort(Comparator.comparing(Item.itemRegistry::getNameForObject));return result;
        }
        private Map<Item,ItemStack> first(){
            Map<Item,ItemStack> result=new IdentityHashMap<>();
            for(ItemStack stack:itemList){Jobs.checkpoint();if(stack!=null&&stack.getItem() instanceof IRunicArmor){NativeStackAudit.audit(stack);result.putIfAbsent(stack.getItem(),stack);}}
            return result;
        }
        private Object[] roots(){return new Object[]{representatives,field(InfusionRecipe.class,recipe,"research"),ConfigItems.itemResource,
            thaumcraft.api.aspects.Aspect.ARMOR,thaumcraft.api.aspects.Aspect.MAGIC,thaumcraft.api.aspects.Aspect.ENERGY};}
        List<Runic> branches(){verify();return new ArrayList<>(branches);}
        void verify(){
            Jobs.checkpoint();if(recipe==null)throw new Jobs.Fault("capture_closed","Runic source is closed");
            ready();if(ItemList.items!=itemList||itemList.size()!=count)throw changed();graph.verifyRoots();
        }
        void finishVerification(){
            verify();graph.verify();List<Item> current=census();if(current.size()!=items.size())throw changed();
            Map<Item,ItemStack> first=first();
            for(int i=0;i<items.size();i++)if(current.get(i)!=items.get(i)||first.get(items.get(i))!=representatives.get(i))throw changed();
            for(Runic branch:branches)branch.inputs.finishVerification();
        }
        public void close(){if(graph!=null)graph.close();graph=null;recipe=null;itemList=null;items.clear();representatives.clear();for(Runic branch:branches)branch.inputs.close();branches.clear();}
        private static Jobs.Fault changed(){return new Jobs.Fault("environment_changed","Runic registration, specimen or fixed native dependencies changed");}
        private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
    }

    static Sample sample(InfusionRunicAugmentRecipe recipe, ItemStack input,RunicChargeSource source) {
        if(recipe.getClass()!=InfusionRunicAugmentRecipe.class)throw new Jobs.Fault("recipe_unsupported","Runic recipe overrides native semantics");
        if (input == null || !(input.getItem() instanceof IRunicArmor))
            throw new Jobs.Fault("runic_input", "Runic augmentation requires IRunicArmor");
        NativeStackAudit.audit(input);
        int charge=source.finalCharge(input.copy());
        int vis=(int)(32.0*Math.pow(2.0,charge));
        AspectList aspects=new AspectList();
        if(vis>0)aspects.add(thaumcraft.api.aspects.Aspect.ARMOR,vis/2)
            .add(thaumcraft.api.aspects.Aspect.MAGIC,vis/2).add(thaumcraft.api.aspects.Aspect.ENERGY,vis);
        return new Sample(charge,5+charge/2,aspects,output(input));
    }
    static ItemStack output(ItemStack input){
        NativeStackAudit.audit(input);ItemStack output=input.copy();
        output.setTagInfo("RS.HARDEN",new net.minecraft.nbt.NBTTagByte((byte)(RunicChargeSource.hardening(input)+1)));return output;
    }

    boolean capture(RecipeRow row,InfusionInheritance inheritance,RunicChargeSource charges,List<ContainerReturn> returns){
        inputs.verify();inheritance.bindNativeOutput(recipe);Sample sample=sample(recipe,center,charges);
        List<List<ItemStack>> slots=new ArrayList<>();slots.add(Collections.singletonList(center));slots.addAll(inputs.componentCopies());
        List<List<MagicRecipes.Candidate>> shown=new ArrayList<>();
        for(int slot=0;slot<slots.size();slot++){
            List<RecipeRow.Ingredient> choices=new ArrayList<>();List<MagicRecipes.Candidate> candidates=new ArrayList<>();
            for(ItemStack stack:slots.get(slot)){
                inputs.validateSpecimen(slot,stack);inputs.observe(row.facts,stack);
                JsonObject rule=slot==0?object("kind","wildcard","meta",true,"nbt",true):object("kind","exact");
                choices.add(new RecipeRow.Ingredient(stack.copy(),1,false,rule));candidates.add(new MagicRecipes.Candidate(stack.copy(),rule));
            }
            row.itemInput(slot,choices,false);shown.add(candidates);
        }
        row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().add("consume",object("kind","stack"));
        finish(row,shown,sample,inputs.pedestalLimit(),returns);
        JsonArray research=array();String key=recipe.getResearch();if(key==null)throw new Jobs.Fault("recipe_unsupported","Native runic research is null");if(!key.isEmpty())research.add(Magic.study(key));
        row.record.add("magic",object("kind","infusion","aspects",Aspects.amounts(sample.aspects),"research",research,"central",0,"instability",sample.instability,"payment",null,"creative",false));
        row.record.add("process",object("kind","nativeRunic","nativeRecipe",recipe.getClass().getName(),"charge",sample.charge,"pattern",inputs.describe(),"pedestalLimit",inputs.pedestalLimit()));
        Products product=Products.observe(Collections.singletonList(center),object("kind","runic"),Runic::output,row.facts);
        row.itemOutput(0,product.output,10000);product.attach(row);
        row.note("thaumcraft:runicRule",recipe.getClass().getName()+"#getComponents/getAspects",row.facts.locale().equals("zh_CN")
            ?"屏障、费用和不稳定性是当前装备样本的观测，其他元数据、NBT 或内部升级可能改变它们。每个周边材料分别经过启动与后续取料比较；重复资源组的数量表示不同基座，不是堆叠数。基座整体替换，容器逐座返还并受库存上限限制。研究、前序配方和祭坛运行仍是条件。"
            :"Charge, costs and instability are observations for this equipment specimen; other metadata, NBT or internal upgrades may change them. Perimeter specimens satisfy both startup and cycle comparisons. The repeated resource amount counts distinct pedestals, not a stack. Used slots are replaced whole and containers return per pedestal subject to its inventory limit. Research, earlier recipes and live altar conditions still apply.");
        return true;
    }

    static void finish(RecipeRow row,List<List<MagicRecipes.Candidate>> inputs,Sample sample,int limit,List<ContainerReturn> returns) {
        if(limit<=0)throw new Jobs.Fault("runic_input","Invalid native pedestal limit");
        for (int slot = 1; slot <= 2; slot++) {
            JsonArray choices = row.inputs.get(slot).getAsJsonObject().getAsJsonArray("choices");
            for (int at = 0; at < choices.size(); at++) {
                JsonObject choice = choices.get(at).getAsJsonObject();
                if (slot == 2) {
                    choice.addProperty("amount", Long.toString(sample.pedestals));
                    choice.add("consume", object("kind", "pedestals"));
                }else choice.add("consume",object("kind","stack"));
                // TileInfusionMatrix replaces each used pedestal with the native container.
                ItemStack input = inputs.get(slot).get(at).item.copy();
                if(returns.size()>=65536)throw new Jobs.Fault("runic_input","Runic container observations exceed budget");
                ContainerReturn observation=new ContainerReturn(input);returns.add(observation);
                ItemStack returned=observation.output==null?null:observation.output.copy();
                if (returned != null) {
                    if (returned.getItem() == null || returned.stackSize <= 0)
                        throw new Jobs.Fault("runic_input", "Invalid native pedestal container");
                    if(returned.stackSize>limit)returned.stackSize=limit;
                    choice.getAsJsonArray("returns").add(object("kind", "item", "id", row.facts.item(returned.copy()),
                            "amount", Integer.toString(returned.stackSize)));
                }
            }
        }
    }

    static final class ContainerReturn {
        final ItemStack input,output;
        final List<GtItemListBinding> bindings=new ArrayList<>();
        final String signature;
        ContainerReturn(ItemStack input){this.input=input;output=ItemCallbacks.pedestalContainer(input,bindings);signature=signature(output);}
        private static String signature(ItemStack output){return com.github.dcysteine.nesql.exporter.source.CanonicalJson.digest(output==null?com.google.gson.JsonNull.INSTANCE:RecipeRow.stackEvidence(output));}
        void verify(){
            for(GtItemListBinding binding:bindings)binding.verify();
            List<GtItemListBinding> current=new ArrayList<>();ItemStack result=ItemCallbacks.pedestalContainer(input,current);
            if(!signature.equals(signature(result)))throw new Jobs.Fault("environment_changed","Native runic container changed");
            for(GtItemListBinding binding:current)binding.verify();
        }
    }

    static final class Sample {
        final int charge, instability;
        final long pedestals;
        final AspectList aspects;
        final ItemStack output;
        Sample(int charge, int instability, AspectList aspects, ItemStack output) {
            this.charge = charge; this.instability = instability; this.aspects = aspects; this.output = output;
            pedestals = 1L + Math.max(0, charge);
        }
    }
}
