package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.Chance;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** One opening has one outcome. Aggregate identical native drops using exact Random.nextFloat intervals. */
final class ScrapboxRecipes implements RegistryRecipes {
    private static final String HANDLER = "ic2.neiIntegration.core.recipehandler.ScrapboxRecipeHandler";
    private static final String MANAGER = "ic2.core.item.ItemScrapbox$ScrapboxRecipeManager";
    private static final String DROP = "ic2.core.item.ItemScrapbox$Drop";
    private static final int SAMPLES = 1 << 24;
    private final TemplateRecipeHandler handler;
    private final ItemStack box;
    private final List<Drop> drops;

    static boolean supports(ICraftingHandler handler) {return handler.getClass().getName().equals(HANDLER);}
    ScrapboxRecipes(TemplateRecipeHandler handler) {this(handler, registry(), box());}
    private static Object registry() {
        version("IC2", "2.2.828-experimental");
        return field(type("ic2.api.recipe.Recipes"),null,"scrapboxDrops");
    }
    private static ItemStack box() {
        ItemStack box = (ItemStack) field(type("ic2.core.Ic2Items"),null,"scrapBox");
        if (box == null || box.getItem() == null || !box.getItem().getClass().getName().equals("ic2.core.item.ItemScrapbox"))
            throw fault("Unknown scrapbox use behavior");
        return box;
    }
    ScrapboxRecipes(TemplateRecipeHandler handler, Object manager, ItemStack box) {
        if (!supports(handler) || manager == null || !manager.getClass().getName().equals(MANAGER)) throw fault("Unknown scrapbox manager");
        this.handler=handler;
        if (box == null || box.getItem() == null) throw fault("Missing scrapbox item");
        this.box=box.copy();this.box.stackSize=1;
        List<?> records=(List<?>)field(manager,"drops");
        if (records == null || records.size() > 262144) throw fault("Invalid scrapbox drop registry");
        float total=(Float)field(type(DROP),null,"topChance"), previous=0;
        if (!Float.isFinite(total) || total < 0) throw fault("Invalid scrapbox total weight");
        LinkedHashMap<String,Drop> outcomes=new LinkedHashMap<>();
        int prior=0;
        for(int index=0;index<records.size();index++) {
            Jobs.checkpoint();
            Object record=records.get(index);
            if(record==null || !record.getClass().getName().equals(DROP)) throw fault("Unknown scrapbox drop record");
            float bound=(Float)field(record,"upperChanceBound");
            if (!Float.isFinite(bound) || bound < previous || bound < 0) throw fault("Scrapbox thresholds are not finite and monotone");
            previous=bound;
            int next=index==records.size()-1?SAMPLES:below(total,bound);
            ItemStack source=(ItemStack)field(record,"item");
            if(source==null || source.getItem()==null || source.stackSize<=0) throw fault("Invalid scrapbox output");
            String registry=Item.itemRegistry.getNameForObject(source.getItem());
            if(registry==null)throw fault("Unregistered scrapbox output");
            String key=Identity.item(registry,source.getItemDamage(),TypedNbt.encode(source.getTagCompound()))+":"+source.stackSize;
            Drop drop=outcomes.get(key);
            if(drop==null) {drop=new Drop(source.copy());outcomes.put(key,drop);}
            drop.samples+=next-prior;prior=next;
        }
        drops=new ArrayList<>(outcomes.values());
    }
    public int size() {return drops.size();}
    public boolean capture(int index, RecipeRow row) {
        Drop drop=drops.get(index);
        if(drop.samples==0)return false;
        row.itemInput(new PositionedStack(box.copy(),51,24,false),0,1,false,false,object("kind","wildcard","meta",true,"nbt",true));
        row.itemOutput(new PositionedStack(drop.item.copy(),111,24,false),0,drop.item.copy(),10000);
        row.outputs.get(0).getAsJsonObject().add("chance",Chance.of(drop.samples,SAMPLES));
        row.property("ic2:scrapboxOutcome","Drop selection","One opening consumes one scrapbox and selects exactly one outcome from this category; rows are mutually exclusive. Creative opening does not consume the box.");
        handler.arecipes.clear();
        handler.arecipes.add((TemplateRecipeHandler.CachedRecipe) TinkerRecipes.construct(type(HANDLER+"$CachedScrapboxRecipe"),
            new Class<?>[]{handler.getClass(),Map.Entry.class},handler,new AbstractMap.SimpleImmutableEntry<>(drop.item.copy(),(float)drop.samples/SAMPLES)));
        return true;
    }
    /** Count k in [0,2^24) for which Java's rounded (k/2^24)*total is below bound. */
    static strictfp int below(float total,float bound) {
        int low=0,high=SAMPLES;
        while(low<high) {
            int middle=(low+high)>>>1;
            float sample=((float)middle/SAMPLES)*total;
            if(sample<bound)low=middle+1;else high=middle;
        }
        return low;
    }
    private static final class Drop {
        final ItemStack item;int samples;
        Drop(ItemStack item) {this.item=item;}
    }
    private static Jobs.Fault fault(String message) {return new Jobs.Fault("recipe_unsupported",message);}
}
