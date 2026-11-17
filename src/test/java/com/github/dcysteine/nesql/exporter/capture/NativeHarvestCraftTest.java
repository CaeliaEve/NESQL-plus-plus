package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.google.gson.JsonObject;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import java.lang.reflect.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;

/** Exercises the pinned HarvestCraft registry/machine, without a game world or GL. */
public final class NativeHarvestCraftTest {
    public static void run() throws Exception {
        NativeCoreFixesTest.version("harvestcraft", "1.3.2-GTNH");
        Class<?> recipes = Class.forName("com.pam.harvestcraft.TrapRecipes");
        @SuppressWarnings("unchecked") Map<Item,List<Object>> registry = (Map<Item,List<Object>>) field(recipes,null,"FISH_TRAP_RECIPES");
        Map<Item,List<Object>> before = new HashMap<>(registry);
        Field baitField = Class.forName("com.pam.harvestcraft.ItemRegistry").getField("fishtrapbaitItem");
        Object oldBait = baitField.get(null);
        registry.clear(); baitField.set(null, Items.paper);
        ItemStack a = new ItemStack(Items.diamond, 2), b = new ItemStack(Items.gold_ingot, 3);
        a.setTagInfo("origin", new net.minecraft.nbt.NBTTagString("native"));
        Constructor<?> weighted = Class.forName("com.pam.harvestcraft.TrapRecipes$WeightedItemStack")
                .getConstructor(ItemStack.class,int.class);
        List<Object> outputs = new ArrayList<>(Arrays.asList(weighted.newInstance(a,1),weighted.newInstance(b,2)));
        registry.put(Items.paper,outputs);
        try {
            TemplateRecipeHandler handler = (TemplateRecipeHandler) Class.forName("com.pam.harvestcraft.FishTrapRecipeHandler").newInstance();
            RegistryRecipes adapter = new HarvestCraftRecipes(handler);
            RecipeRow row = NativeCoreFixesTest.row(new ItemStack(Items.paper), a, b);
            require(adapter.capture(0,row),"Native accepted bait was excluded");
            // A regression to independent guaranteed output must fail here.
            JsonObject process = row.record.getAsJsonObject("process");
            require(process != null && "harvestDrops".equals(process.get("kind").getAsString()),
                    "Trap candidates were exported as independent guaranteed outputs");
            require(process.get("mode").getAsString().equals("fish") && process.get("draws").getAsInt()==1
                    && process.getAsJsonArray("weights").get(0).getAsInt()==1
                    && process.getAsJsonArray("weights").get(1).getAsInt()==2,
                    "Native integer weights or single mutually exclusive draw changed");
            JsonObject input = row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
            require(input.getAsJsonObject("rule").get("meta").getAsBoolean()
                    && input.getAsJsonObject("rule").get("nbt").getAsBoolean(),"Bait item-only matching lost");
            for(int i=0;i<2;i++) {
                JsonObject out=row.outputs.get(i).getAsJsonObject();
                require(out.get("amount").isJsonNull() && out.getAsJsonObject("quantity").get("kind").getAsString().equals("harvest")
                        && out.getAsJsonObject("quantity").get("nominal").getAsString().equals(i==0?"2":"3"),
                        "Selected stack quantity was flattened into guaranteed output");
            }
            // The actual machine only consumes bait if an EMPTY result slot exists.
            Object machine=Class.forName("com.pam.harvestcraft.TileEntityPamFishTrap").newInstance();
            ItemStack[] inventory=(ItemStack[])field(machine,"inventory");
            Arrays.fill(inventory,new ItemStack(Items.diamond)); inventory[18]=new ItemStack(Items.paper,4,17);
            machine.getClass().getMethod("run").invoke(machine);
            require(inventory[18].stackSize==4,"Native full inventory unexpectedly consumed bait");
            inventory[5]=null; machine.getClass().getMethod("run").invoke(machine);
            require(inventory[18].stackSize==3 && inventory[5]!=null
                    && (inventory[5].getItem()==Items.diamond && inventory[5].stackSize==2
                    || inventory[5].getItem()==Items.gold_ingot && inventory[5].stackSize==3),"Native single draw/consumption disagrees");
            outputs.add(weighted.newInstance(new ItemStack(Items.iron_ingot),3));
            try { adapter.verify(); throw new AssertionError("Native trap registry drift was accepted"); }
            catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) {
                require(expected.code.equals("environment_changed"),"Registry drift changed error class");
            }
            require(a.stackSize==2 && a.getTagCompound().getString("origin").equals("native"),"Capture mutated registry output");
        } finally {registry.clear();registry.putAll(before);baitField.set(null,oldBait);}
        gardens();
        System.out.println("Native HarvestCraft: exact weights, correlated yields, bait admission, native garden/Nether configurations, owned pages and drift passed");
    }
    private static void gardens() throws Exception {
        Map<Field,Object> saved=new LinkedHashMap<>();
        Class<?> blocks=Class.forName("com.pam.harvestcraft.BlockRegistry"),items=Class.forName("com.pam.harvestcraft.ItemRegistry");
        try {
            for(Field field:blocks.getFields())if(field.getType()==net.minecraft.block.Block.class&&field.getName().endsWith("Garden")){save(saved,field);field.set(null,null);}
            save(saved,blocks.getField("gardendropAmount"));blocks.getField("gardendropAmount").setInt(null,4);
            save(saved,blocks.getField("gardensdropSeeds"));blocks.getField("gardensdropSeeds").setBoolean(null,true);
            String[] names={"celery","garlic","ginger","spiceleaf","tea","coffee","mustard"};
            Item[] seeds={Items.paper,Items.diamond,Items.gold_ingot,Items.iron_ingot,Items.coal,Items.apple,Items.carrot};
            for(int i=0;i<names.length;i++){Field f=items.getField(names[i]+"seedItem");save(saved,f);f.set(null,seeds[i]);}
            net.minecraft.block.Block herb=block("com.pam.harvestcraft.BlockPamNormalGarden","nesqltest:herb_garden");
            blocks.getField("pamherbGarden").set(null,herb);
            require(herb.quantityDropped(new Random(1))==4 && herb.getItemDropped(0,new LastIndex(7),0)==Items.carrot,
                    "Native garden draw/count fixture disagrees");
            TemplateRecipeHandler handler=(TemplateRecipeHandler)Class.forName("com.pam.harvestcraft.GardenRecipeHandler").newInstance();
            RegistryRecipes adapter=new HarvestCraftRecipes(handler);
            ItemStack garden=new ItemStack(herb);ItemStack[] known=new ItemStack[8];known[0]=garden;for(int i=0;i<7;i++)known[i+1]=new ItemStack(seeds[i]);
            RecipeRow row=NativeCoreFixesTest.row(known);require(adapter.capture(0,row),"Garden was excluded");
            require(row.record.getAsJsonObject("process").get("draws").getAsInt()==4 && row.outputs.size()==7,"Garden draws were replaced by all guaranteed candidates");
            JsonObject rule=row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("rule");
            require(!rule.get("meta").getAsBoolean(),"Garden widened its captured block metadata state");
            require(handler.arecipes.size()==1 && handler.getOtherStacks(0).get(6).items[0].getItem()==Items.carrot,"Garden native page lost owned candidate projection");
            blocks.getField("gardendropAmount").setInt(null,5);
            try{adapter.verify();throw new AssertionError("Garden config drift was accepted");}catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected){require(expected.code.equals("environment_changed"),"Wrong config drift failure");}

            // Nether's own seed setting/count can differ from the NEI bridge's HarvestCraft setting.
            Class<?> netherBlocks=Class.forName("com.pam.harvestthenether.BlockRegistry"),netherItems=Class.forName("com.pam.harvestthenether.ItemRegistry");
            net.minecraft.block.Block nether=block("com.pam.harvestthenether.BlockNetherGarden","harvestthenether:netherGarden");
            for(String name:new String[]{"netherGarden","gardendropAmount","gardensdropSeeds"})save(saved,netherBlocks.getField(name));
            netherBlocks.getField("netherGarden").set(null,nether);netherBlocks.getField("gardendropAmount").setInt(null,2);netherBlocks.getField("gardensdropSeeds").setBoolean(null,false);
            String[] crops={"bloodleaf","fleshroot","marrowberry"};
            Item[] foods={Items.bread,Items.beef,Items.porkchop};
            for(int i=0;i<3;i++)for(String suffix:new String[]{"Item","seedItem"}){Field f=netherItems.getField(crops[i]+suffix);save(saved,f);f.set(null,suffix.equals("Item")?foods[i]:seeds[i]);}
            require(nether.quantityDropped(new Random(1))==2 && nether.getItemDropped(0,new LastIndex(3),0)==Items.porkchop,"Native Nether garden fixture disagrees");
            RegistryRecipes both=new HarvestCraftRecipes(handler);require(both.size()==2,"Native Nether garden identity missing");
            for(int i=0;i<2;i++){
                RecipeRow next=NativeCoreFixesTest.row(garden,new ItemStack(nether),new ItemStack(Items.bread),new ItemStack(Items.beef),new ItemStack(Items.porkchop),known[1],known[2],known[3],known[4],known[5],known[6],known[7]);
                require(both.capture(i,next),"Native garden entry was excluded");
                if(next.outputs.size()==3){require(next.record.getAsJsonObject("process").get("draws").getAsInt()==2 && handler.getOtherStacks(0).get(2).items[0].getItem()==Items.porkchop,"Nether incorrectly inherited HarvestCraft's seed/config values");}
                require(handler.arecipes.size()==1,"Capture retained a stale first native page");
            }
        }finally{for(Map.Entry<Field,Object> entry:saved.entrySet())entry.getKey().set(null,entry.getValue());}
    }
    private static void save(Map<Field,Object> saved,Field field)throws Exception{if(!saved.containsKey(field))saved.put(field,field.get(null));}
    private static net.minecraft.block.Block block(String name,String registry)throws Exception{
        Constructor<?> ctor=Class.forName(name).getDeclaredConstructor(int.class);ctor.setAccessible(true);
        net.minecraft.block.Block block=(net.minecraft.block.Block)ctor.newInstance(0);
        int id=3000+net.minecraft.block.Block.blockRegistry.getKeys().size();
        // Offline Loader has no active mod container to qualify GameData.register.
        // Use the exact name for the native NEI Nether bridge.
        Object forgeRegistry=cpw.mods.fml.common.registry.GameData.getBlockRegistry();
        Method add=forgeRegistry.getClass().getDeclaredMethod("addObjectRaw",int.class,String.class,Object.class);
        add.setAccessible(true);add.invoke(forgeRegistry,id,registry,block);
        if(net.minecraft.block.Block.blockRegistry!=forgeRegistry)net.minecraft.block.Block.blockRegistry.addObject(id,registry,block);
        Item.itemRegistry.addObject(id,registry,new net.minecraft.item.ItemBlock(block));
        return block;
    }
    private static final class LastIndex extends Random {
        final int size;LastIndex(int size){this.size=size;}
        @Override public int nextInt(int bound){if(bound!=size)throw new AssertionError("Native selection cardinality changed");return bound-1;}
    }
    private static void require(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
