package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.WeightedRandom;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Native correlated garden/trap drops, independent of NEI recipe pages. */
final class HarvestCraftRecipes implements RegistryRecipes {
    private static final String ROOT="com.pam.harvestcraft.";
    private static final String GARDEN=ROOT+"GardenRecipeHandler", FISH=ROOT+"FishTrapRecipeHandler", ANIMAL=ROOT+"AnimalTrapRecipeHandler";
    private final String handlerClass;
    private final List<Entry> entries=new ArrayList<>();
    private String fingerprint;
    private NativeDefinitionGraph definitions;
    private boolean closed;
    private static final String[] GARDENS={"berry","desert","grass","gourd","ground","herb","leafy","mushroom","stalk","textile","tropical","water"};

    static boolean supports(ICraftingHandler value) {return value!=null&&supportsClass(value.getClass().getName());}
    static boolean supportsClass(String name) {return GARDEN.equals(name)||FISH.equals(name)||ANIMAL.equals(name);}

    HarvestCraftRecipes(TemplateRecipeHandler value) {
        if(!supports(value))throw fault("Unadapted HarvestCraft handler");
        MagicApi.version("HarvestCraftRecipes","harvestcraft");
        handlerClass=value.getClass().getName();
        try {
            entries.addAll(read(handlerClass));
            fingerprint=digest(entries);
            definitions=new NativeDefinitionGraph(this::roots,NativeDefinitionGraph.shapes(
                    ROOT+"TrapRecipes$WeightedItemStack","stack","itemWeight"));
        }catch(RuntimeException|Error error){close();throw error;}
    }

    static Object[] registrationAnchors(String name){
        if(!supportsClass(name))throw fault("Unknown HarvestCraft source");
        return name.equals(GARDEN)?new Object[]{type(ROOT+"BlockRegistry"),type(ROOT+"ItemRegistry")}
                :new Object[]{field(type(ROOT+"TrapRecipes"),null,name.equals(FISH)?"FISH_TRAP_RECIPES":"ANIMAL_TRAP_RECIPES")};
    }

    private Object[] roots(){
        if(!handlerClass.equals(GARDEN))return new Object[]{registrationAnchors(handlerClass)[0],
                field(type(ROOT+"ItemRegistry"),null,"fishtrapbaitItem"),field(type(ROOT+"ItemRegistry"),null,"grainbaitItem"),
                field(type(ROOT+"ItemRegistry"),null,"fruitbaitItem"),field(type(ROOT+"ItemRegistry"),null,"veggiebaitItem")};
        List<Object> roots=new ArrayList<>();Class<?> blocks=type(ROOT+"BlockRegistry");
        for(String kind:GARDENS)roots.add(field(blocks,null,"pam"+kind+"Garden"));
        roots.add(field(blocks,null,"gardendropAmount"));roots.add(field(blocks,null,"gardensdropSeeds"));
        Block nether=cpw.mods.fml.common.registry.GameRegistry.findBlock("harvestthenether","netherGarden");roots.add(nether);
        if(nether!=null){Class<?> registry=type("com.pam.harvestthenether.BlockRegistry");
            roots.add(field(registry,null,"netherGarden"));roots.add(field(registry,null,"gardendropAmount"));roots.add(field(registry,null,"gardensdropSeeds"));}
        return roots.toArray();
    }

    public int size(){alive();return entries.size();}

    public void verify() {
        alive();definitions.verifyRoots();
    }
    public void finishVerification(){
        verify();definitions.verify();List<Entry> current=read(handlerClass);
        if(!fingerprint.equals(digest(current))||current.size()!=entries.size())throw changed();
        for(int i=0;i<entries.size();i++){
            Entry old=entries.get(i),now=current.get(i);
            if(old.input.getItem()!=now.input.getItem()||old.outputs.size()!=now.outputs.size())throw changed();
            for(int j=0;j<old.outputs.size();j++)if(old.outputs.get(j).getItem()!=now.outputs.get(j).getItem())throw changed();
        }
    }

    public JsonObject exclusion(int index) {
        alive();Entry entry=entries.get(index);
        if(entry.reachable)return null;
        return object("kind","harvestcraft-unreachable","reason",entry.mode.equals("garden")?"native garden drop count is nonpositive":entry.outputs.isEmpty()?"native trap pool is empty and returns no result":"native trap rejects this bait item",
                "input",stack(entry.input),"mode",entry.mode);
    }

    public boolean capture(int index,RecipeRow row) {
        alive();Entry entry=entries.get(index);
        if(!entry.reachable)return false;
        row.itemInput(0,Collections.singletonList(new RecipeRow.Ingredient(entry.input.copy(),1,false,object("kind","wildcard","meta",!entry.mode.equals("garden"),"nbt",true))),false);
        JsonArray weights=new JsonArray();for(int weight:entry.weights)weights.add(value(weight));
        row.record.add("process",object("kind","harvestDrops","mode",entry.mode,"draws",entry.draws,"weights",weights));
        for(int i=0;i<entry.outputs.size();i++)row.itemOutput(i,entry.outputs.get(i),10000,
                object("kind","harvest","nominal",Integer.toString(entry.outputs.get(i).stackSize)));
        if(entry.biome!=null)row.property("harvestcraft:biome",row.facts.locale().equals("zh_CN")?"原生花园类别键":"Native garden category key",entry.biome);
        row.note("harvestcraft:nativeDraws",entry.mode.equals("garden")?ROOT+"base.BlockGarden.getItemDropped":ROOT+"TrapRecipes.getRandomOutputForBait",
                row.facts.locale().equals("zh_CN")
                    ?(entry.mode.equals("garden")?"原生花园基础掉落：每次从当前候选中等概率抽取一项，可重复，次数由配置决定；不把候选全部当作固定产物。世界掉落事件仍可能影响实际领取。":"每次完成只进行一次共享加权抽取；不是为每个候选分别抽取。诱饵与世界库存、陷阱计时和放置条件须满足。")
                    :(entry.mode.equals("garden")?"Native base garden drops: each draw uniformly selects one current candidate with replacement; configuration sets the number of draws. Candidates are not simultaneous fixed outputs. World drop events may still affect collection.":"Each completion makes one shared weighted draw, not an independent roll per candidate. Bait, world inventory, trap timing and placement conditions still apply."));
        return true;
    }

    private static List<Entry> read(String name) {
        List<Entry> result=new ArrayList<>();
        if(name.equals(GARDEN)) {
            Map<String,Block> gardens=new LinkedHashMap<>();
            for(String kind:GARDENS){
                Object block=field(type(ROOT+"BlockRegistry"),null,"pam"+kind+"Garden");if(block!=null&&!(block instanceof Block))throw fault("Invalid native garden role");gardens.put("harvestcraft.nei.garden."+kind,(Block)block);
            }
            gardens.put("harvestcraft.nei.garden.nether",cpw.mods.fml.common.registry.GameRegistry.findBlock("harvestthenether","netherGarden"));
            for(Map.Entry<String,Block> garden:gardens.entrySet()) {
                Block block=garden.getValue();if(block==null)continue;String key=garden.getKey();ItemStack input=new ItemStack(block);
                String blockClass=block.getClass().getName();
                if(block.getClass()!=type(blockClass))throw fault("Replaced native garden class identity");
                admitDamageDropped(block);
                List<ItemStack> outputs=new ArrayList<>();int draws;
                if(blockClass.equals("com.pam.harvestthenether.BlockNetherGarden")) {
                    Class<?> registry=type("com.pam.harvestthenether.BlockRegistry");
                    if(block!=field(registry,null,"netherGarden"))throw fault("Unexpected Nether garden block role");
                    MagicApi.version("HarvestCraftRecipes","harvestthenether");
                    boolean seeds=(Boolean)field(registry,null,"gardensdropSeeds");
                    Class<?> items=type("com.pam.harvestthenether.ItemRegistry");
                    for(String item:new String[]{"bloodleaf","fleshroot","marrowberry"})outputs.add(new ItemStack((Item)field(items,null,item+(seeds?"seedItem":"Item")),1,block.damageDropped(0)));
                    draws=(Integer)field(registry,null,"gardendropAmount");
                }else {
                    if(!Arrays.asList(ROOT+"BlockPamNormalGarden",ROOT+"BlockPamWaterGarden",ROOT+"BlockPamMushroomGarden",ROOT+"BlockPamDesertGarden").contains(blockClass))
                        throw fault("Unadapted native garden block: "+blockClass);
                    @SuppressWarnings("unchecked") List<Item> items=(List<Item>)invoke(type(ROOT+"base.BlockGarden"),null,"getDropList",new Class<?>[]{Block.class},block);
                    for(Item item:items)outputs.add(new ItemStack(item,1,block.damageDropped(0)));
                    draws=(Integer)field(type(ROOT+"BlockRegistry"),null,"gardendropAmount");
                }
                int[] weights=new int[outputs.size()];Arrays.fill(weights,1);
                result.add(new Entry(input,outputs,weights,draws,"garden",key,draws>0));
            }
        } else {
            boolean fish=name.equals(FISH);
            @SuppressWarnings("unchecked") Map<Item,List<?>> registry=(Map<Item,List<?>>)field(type(ROOT+"TrapRecipes"),null,fish?"FISH_TRAP_RECIPES":"ANIMAL_TRAP_RECIPES");
            if(registry==null||registry.getClass()!=HashMap.class||registry.size()>65536)throw fault("Unadapted native trap storage");
            List<Item> baits=new ArrayList<>(registry.keySet());baits.sort(Comparator.comparing(item -> {
                String id=Item.itemRegistry.getNameForObject(item);if(id==null)throw fault("Unregistered trap bait");return id;
            }));
            for(Item bait:baits) {
                List<?> nativeOutputs=registry.get(bait);
                if(nativeOutputs==null||nativeOutputs.getClass()!=ArrayList.class||nativeOutputs.size()>128)throw fault("Invalid native trap pool");
                List<ItemStack> outputs=new ArrayList<>();int[] weights=new int[nativeOutputs.size()];long total=0;
                for(int i=0;i<nativeOutputs.size();i++) {
                    Object value=nativeOutputs.get(i);
                    if(value==null||value.getClass()!=type(ROOT+"TrapRecipes$WeightedItemStack"))throw fault("Unadapted trap output type");
                    ItemStack raw=(ItemStack)field(value,"stack");NativeStackAudit.audit(raw);outputs.add(raw.copy());
                    weights[i]=((WeightedRandom.Item)value).itemWeight;
                    if(weights[i]<0)throw fault("Negative native trap weight");total+=weights[i];
                }
                if(!nativeOutputs.isEmpty()&&(total<=0||total>Integer.MAX_VALUE))throw fault("Native trap weight total cannot be selected");
                Class<?> items=type(ROOT+"ItemRegistry");
                boolean admitted=fish?bait==field(items,null,"fishtrapbaitItem"):
                        bait==field(items,null,"grainbaitItem")||bait==field(items,null,"fruitbaitItem")||bait==field(items,null,"veggiebaitItem");
                result.add(new Entry(new ItemStack(bait),outputs,weights,1,fish?"fish":"animal",null,admitted&&!nativeOutputs.isEmpty()));
            }
        }
        if(result.size()>262144)throw fault("HarvestCraft native registry exceeds recipe limit");
        return result;
    }

    private static String digest(List<Entry> entries) {
        JsonArray rows=new JsonArray();
        for(Entry entry:entries) {
            JsonArray outputs=new JsonArray(),weights=new JsonArray();
            for(ItemStack output:entry.outputs)outputs.add(stack(output));for(int weight:entry.weights)weights.add(value(weight));
            rows.add(object("input",stack(entry.input),"outputs",outputs,"weights",weights,"draws",entry.draws,"mode",entry.mode,"biome",entry.biome,"reachable",entry.reachable));
        }
        return CanonicalJson.digest(rows);
    }
    private static JsonObject stack(ItemStack stack) {
        if(stack==null||stack.getItem()==null||stack.stackSize<=0)throw fault("Empty native HarvestCraft stack");
        String id=Item.itemRegistry.getNameForObject(stack.getItem());if(id==null)throw fault("Unregistered native HarvestCraft item");
        return object("registry",id,"meta",Items.feather.getDamage(stack),"amount",stack.stackSize,"nbt",TypedNbt.encode(stack.getTagCompound()));
    }
    private static final class Entry {
        final ItemStack input;final List<ItemStack> outputs;final int[] weights;final int draws;final String mode,biome;final boolean reachable;
        Entry(ItemStack input,List<ItemStack> outputs,int[] weights,int draws,String mode,String biome,boolean reachable) {
            if(outputs.size()>128||reachable&&outputs.isEmpty())throw fault("Invalid HarvestCraft candidate count");
            this.input=input;this.outputs=outputs;this.weights=weights;this.draws=draws;this.mode=mode;this.biome=biome;this.reachable=reachable;
            stack(input);for(ItemStack output:outputs)stack(output);
        }
    }
    private static void admitDamageDropped(Block block){
        String method=cpw.mods.fml.relauncher.ReflectionHelper.findMethod(Block.class,null,new String[]{"damageDropped","func_149692_a"},int.class).getName();
        try{
            Class<?> owner=block.getClass().getMethod(method,int.class).getDeclaringClass();
            // Native gardens inherit BlockFlower's literal input-metadata return.
            if(owner!=Block.class&&owner!=net.minecraft.block.BlockFlower.class)throw fault("Unadapted garden drop metadata callback");
        }
        catch(NoSuchMethodException error){throw fault("Missing garden drop metadata callback");}
    }
    private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","HarvestCraft reader is closed");}
    public void close(){closed=true;if(definitions!=null)definitions.close();entries.clear();fingerprint=null;}
    private static Jobs.Fault changed(){return new Jobs.Fault("environment_changed","HarvestCraft native roles, weights, configuration or drops changed");}
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
}
