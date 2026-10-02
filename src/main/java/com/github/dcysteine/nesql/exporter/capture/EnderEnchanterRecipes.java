package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.capture.EnderInputs.Pattern;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Native ordered enchanter registry, expanded by level with an explicit offered-count gate. */
final class EnderEnchanterRecipes implements RegistryRecipes {
    private static final String HANDLER="crazypants.enderio.nei.EnchanterRecipeHandler";
    private static final String INPUT="crazypants.enderio.machine.recipe.RecipeInput";
    private static final String ORE="crazypants.enderio.machine.recipe.OreDictionaryRecipeInput";
    private static final String RECIPE="crazypants.enderio.machine.enchanter.EnchanterRecipe";
    private final TemplateRecipeHandler handler;
    private final List<Entry> entries=new ArrayList<>();
    private final List<Level> levels=new ArrayList<>();
    private final int baseCost;

    static boolean supports(ICraftingHandler handler) {return handler.getClass().getName().equals(HANDLER);}
    EnderEnchanterRecipes(TemplateRecipeHandler handler) {
        this(handler,registry(),(Integer)field(type("crazypants.enderio.config.Config"),null,"enchanterBaseLevelCost"));
    }
    private static List<?> registry() {
        version("EnderIO","2.9.28");
        Object manager=invoke(type("crazypants.enderio.machine.enchanter.EnchanterRecipeManager"),null,"getInstance",new Class<?>[0]);
        return (List<?>)call(manager,"getRecipes");
    }
    EnderEnchanterRecipes(TemplateRecipeHandler handler,List<?> registry,int baseCost) {
        if(!supports(handler)||registry==null||registry.size()>262144)throw fault("Unknown or oversized enchanter registry");
        this.handler=handler;this.baseCost=baseCost;
        for(Object record:registry) {
            Jobs.checkpoint();
            if(record==null||!record.getClass().getName().equals(RECIPE))throw fault("Unknown enchanter recipe implementation");
            if(!(Boolean)call(record,"isValid"))continue; // The native manager also ignores invalid records.
            Entry entry=new Entry(record);int at=entries.size();entries.add(entry);
            for(int level=1;level<=entry.maxLevel;level++) {
                if(levels.size()>=262144)throw fault("Enchanter level expansion exceeds its budget");
                levels.add(new Level(at,level));
            }
        }
    }
    public int size() {return levels.size();}
    public boolean capture(int index,RecipeRow row) {
        Level branch=levels.get(index);Entry entry=entries.get(branch.entry);
        long count=(long)entry.perLevel*branch.level;
        if(count>64)return false; // One ordinary machine input slot, never a split input.
        List<Pattern> previous=new ArrayList<>();
        for(int i=0;i<branch.entry;i++)previous.addAll(entries.get(i).patterns);
        List<RecipeRow.Ingredient> choices=new ArrayList<>();
        for(Pattern pattern:entry.patterns) {
            Jobs.checkpoint();
            boolean covered=false;
            for(Pattern prior:previous)if(prior.covers(pattern)){covered=true;break;}
            if(covered)continue;
            JsonArray exclusions=array();Set<String> seen=new HashSet<>();
            for(Pattern prior:previous)if(prior.overlaps(pattern)) {
                String id=row.facts.item(prior.item);
                if(seen.add(id+":"+prior.anyMeta))exclusions.add(object("id",id,"rule",prior.rule()));
                if(exclusions.size()>4096)throw fault("Enchanter priority filter exceeds its budget");
            }
            ItemStack example=pattern.item.copy();
            if(pattern.anyMeta) {
                int meta=example.getItemDamage();
                while(matchesAny(previous,example)) {
                    if(meta>=65535)throw fault("Cannot find an unshadowed enchanter metadata example");
                    example.setItemDamage(++meta);
                }
            }
            JsonObject rule=pattern.rule();
            if(exclusions.size()>0)rule=object("kind","except","base",rule,"exclude",exclusions);
            choices.add(new RecipeRow.Ingredient(example,count,false,rule));
        }
        if(choices.isEmpty())return false;
        int cost=baseCost+entry.costPerLevel*branch.level*branch.level; // Native Java int wraparound.
        row.record.add("process",object("kind","enchanter","level",branch.level,"maxLevel",entry.maxLevel,
                "itemsPerLevel",entry.perLevel,"cost",cost));
        row.itemInput(new PositionedStack(new ItemStack(Items.writable_book),22,24,false),0,1,false,false,
                object("kind","wildcard","meta",true,"nbt",true));
        List<ItemStack> displays=new ArrayList<>();
        for(RecipeRow.Ingredient choice:choices){ItemStack item=choice.item.copy();item.stackSize=(int)count;displays.add(item);}
        row.itemInput(new PositionedStack(displays,71,24,false),1,choices,false);
        ItemStack output=new ItemStack(Items.enchanted_book);
        Items.enchanted_book.addEnchantment(output,new EnchantmentData(entry.enchantment,branch.level));
        row.itemOutput(new PositionedStack(output.copy(),129,24,false),0,output,10000);
        // Only this owned projection is allowed to be mutated by the native rotating NEI cache.
        ItemStack sample=displays.get(0).copy();sample.stackSize=entry.perLevel;
        Object viewInput=TinkerRecipes.construct(type(INPUT),new Class<?>[]{ItemStack.class,boolean.class},sample,true);
        Object viewRecipe=TinkerRecipes.construct(type(RECIPE),new Class<?>[]{type(INPUT),Enchantment.class,int.class},viewInput,entry.enchantment,entry.costPerLevel);
        handler.arecipes.clear();
        handler.arecipes.add((TemplateRecipeHandler.CachedRecipe)TinkerRecipes.construct(type(HANDLER+"$EnchanterRecipeNEI"),
                new Class<?>[]{handler.getClass(),type(RECIPE)},handler,viewRecipe));
        return true;
    }
    static void scene(TemplateRecipeHandler handler,int level,Runnable draw) {
        try {
            java.lang.reflect.Field clock=TemplateRecipeHandler.class.getDeclaredField("cycleticks");clock.setAccessible(true);
            int previous=clock.getInt(handler);
            try {clock.setInt(handler,(level-1)*20);draw.run();}finally {clock.setInt(handler,previous);}
        }catch(ReflectiveOperationException error){throw fault("Cannot isolate enchanter display clock: "+error);}
    }
    private static final class Entry {
        final Enchantment enchantment;final int perLevel,maxLevel,costPerLevel;final List<Pattern> patterns=new ArrayList<>();
        Entry(Object record) {
            Object input=call(record,"getInput");
            if(input==null||!(input.getClass().getName().equals(INPUT)||input.getClass().getName().equals(ORE)))throw fault("Unknown enchanter input predicate");
            ItemStack item=(ItemStack)call(input,"getInput");
            if(item==null||item.getItem()==null||item.stackSize<=0)throw fault("Enchanter input amount must be positive");
            perLevel=(Integer)call(record,"getItemsPerLevel");
            if(perLevel!=item.stackSize)throw fault("Enchanter input changed after registration");
            enchantment=(Enchantment)call(record,"getEnchantment");maxLevel=enchantment.getMaxLevel();
            costPerLevel=(Integer)call(record,"getCostPerLevel");
            if(maxLevel<1||maxLevel>32767)throw fault("Invalid native enchantment maximum level");
            patterns.addAll(EnderInputs.patterns(input));
        }
    }
    private static boolean matchesAny(List<Pattern> patterns,ItemStack item){for(Pattern p:patterns)if(p.matches(item))return true;return false;}
    private static final class Level {final int entry,level;Level(int entry,int level){this.entry=entry;this.level=level;}}
    private static Object call(Object target,String name){return invoke(target.getClass(),target,name,new Class<?>[0]);}
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
}
