package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.oredict.OreDictionary;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Native ordered pattern matcher; dye choices are correlated by native color. */
final class BannerRecipes implements RegistryRecipes {
    private static final String HANDLER="ganymedes01.etfuturum.compat.nei.BannerPatternHandler";
    private static final String RECIPE="ganymedes01.etfuturum.recipes.crafting.RecipeAddPattern";
    private static final String PATTERN="ganymedes01.etfuturum.tileentities.TileEntityBanner$EnumBannerPattern";
    private IRecipe recipe;
    private Item banner;
    private final List<Rule> rules=new ArrayList<>();
    private final List<List<ItemStack>> dyes=new ArrayList<>();
    private final List<Variant> variants=new ArrayList<>();
    private final List<NativeItemVariants> metadata=new ArrayList<>();
    private final List<Object> anchors;
    private Object[] roots;
    private final NativeDefinitions definitions;
    private boolean closed;
    static boolean supports(ICraftingHandler value){return value.getClass().getName().equals(HANDLER);}
    static Object[] registrationAnchors(codechicken.nei.recipe.IRecipeHandler handler){
        if(!handler.getClass().getName().equals(HANDLER))throw fault("Unknown banner registration");
        MagicApi.version("BannerRecipes","etfuturum");return roots();
    }
    private static Object[] roots(){
        Object definition=field(type("ganymedes01.etfuturum.ModBlocks"),null,"BANNER");
        if(definition==null)throw new Jobs.Fault("handler_not_ready","Native banner definition unavailable");
        if(definition.getClass()!=type("ganymedes01.etfuturum.ModBlocks"))throw fault("Unadapted native banner definition callback");
        Object block=field(definition,"theBlock");
        if(!(block instanceof Block))throw new Jobs.Fault("handler_not_ready","Native banner block unavailable");
        Object patterns=field(type(PATTERN),null,"VALUES"),colors=field(type("ganymedes01.etfuturum.lib.EnumColor"),null,"VALUES");
        Object ids=field(OreDictionary.class,null,"nameToId"),stacks=field(OreDictionary.class,null,"idToStack");
        Object names=field(OreDictionary.class,null,"idToName"),lookup=field(OreDictionary.class,null,"stackToId");
        if(patterns==null||colors==null||ids==null||stacks==null||names==null||lookup==null)throw new Jobs.Fault("handler_not_ready","Native banner patterns/colors/ore source unavailable");
        if(patterns.getClass()!=java.lang.reflect.Array.newInstance(type(PATTERN),0).getClass()
                ||colors.getClass()!=java.lang.reflect.Array.newInstance(type("ganymedes01.etfuturum.lib.EnumColor"),0).getClass()
                ||!NativeOreStorage.names(ids)||stacks.getClass()!=ArrayList.class||names.getClass()!=ArrayList.class
                ||!NativeOreStorage.reverse(lookup))throw fault("Unadapted banner registry storage");
        Item item=Item.getItemFromBlock((Block)block);
        if(item==null||Item.itemRegistry.getNameForObject(item)==null)throw new Jobs.Fault("handler_not_ready","Native banner item unavailable");
        if(block.getClass()!=type("ganymedes01.etfuturum.blocks.BlockBanner")||item.getClass()!=type("ganymedes01.etfuturum.blocks.itemblocks.ItemBlockBanner"))
            throw fault("Native banner block/item override requires an adapter");
        return new Object[]{definition,block,item,patterns,colors,ids,stacks,names,lookup};
    }
    BannerRecipes(TemplateRecipeHandler handler){
        MagicApi.version("BannerRecipes","etfuturum");
        if(!supports(handler))throw fault("Unknown banner handler");
        roots=registrationAnchors(handler);
        try{recipe=(IRecipe)type(RECIPE).newInstance();}catch(ReflectiveOperationException e){throw fault(e.toString());}
        banner=(Item)roots[2];
        auditStack(new ItemStack(banner));
        anchors=readAnchors();
        for(Object nativeRule:(Object[])field(type(PATTERN),null,"VALUES"))if((Boolean)call(nativeRule,"hasValidCrafting")){
            ItemStack catalyst=(Boolean)call(nativeRule,"hasCraftingStack")?((ItemStack)call(nativeRule,"getCraftingStack")).copy():null;
            String[] mask=catalyst==null?((String[])call(nativeRule,"getCraftingLayers")).clone():null;
            if(mask!=null&&(mask.length!=3||Arrays.stream(mask).anyMatch(s->s==null||s.length()!=3)))throw fault("Invalid native banner mask");
            rules.add(new Rule(nativeRule,(String)call(nativeRule,"getPatternID"),mask,catalyst));
            if(rules.size()>4096)throw fault("Banner pattern registry exceeds budget");
        }
        dyes.addAll(readDyes(true));
        for(int i=0;i<rules.size();i++){
            Rule rule=rules.get(i);
            // Dye classification precedes catalyst equality in native crafting.
            if(rule.catalyst!=null&&color(rule.catalyst)>=0)continue;
            for(int color=0;color<16;color++)if(!dyes.get(color).isEmpty())variants.add(new Variant(i,color,false));
            if(rule.catalyst!=null)variants.add(new Variant(i,15,true));
        }
        definitions=new NativeDefinitions("Native banner",Collections.singletonList(this),this::current,ignored->definition());
    }
    private List<?> current(){
        if(closed)throw new Jobs.Fault("capture_closed","Native banner reader is closed");
        Object[] actual;
        try{actual=roots();}catch(Jobs.Fault failure){Jobs.Fault drift=changed();drift.initCause(failure);throw drift;}
        for(int i=0;i<roots.length;i++)if(roots[i]!=actual[i])throw changed();
        return Collections.singletonList(this);
    }
    private JsonObject definition(){
        List<Object> actual;
        try{actual=readAnchors();}catch(Jobs.Fault failure){Jobs.Fault drift=changed();drift.initCause(failure);throw drift;}
        if(actual.size()!=anchors.size())throw changed();
        for(int i=0;i<actual.size();i++)if(anchors.get(i)!=actual.get(i))throw changed();
        for(NativeItemVariants binding:metadata)binding.verify();
        return object("definition",digest());
    }
    public int size(){verify();return variants.size();}
    public void verify(){if(closed)throw new Jobs.Fault("capture_closed","Native banner reader is closed");definitions.finishVerification();}
    public void finishVerification(){verify();}
    public void close(){if(!closed){closed=true;definitions.close();rules.clear();dyes.clear();variants.clear();metadata.clear();anchors.clear();roots=null;recipe=null;banner=null;}}
    public boolean capture(int index,RecipeRow row){
        if(closed)throw new Jobs.Fault("capture_closed","Native banner reader is closed");
        return definitions.checked(0,()->captureRow(index,row));
    }
    private boolean captureRow(int index,RecipeRow row){
        Variant v=variants.get(index);Rule rule=rules.get(v.rule);String[] mask;
        ItemStack base=new ItemStack(banner,1,0);
        if(rule.catalyst!=null){mask=v.noDye?new String[]{"   ","yx ","   "}:new String[]{"   ","y# "," x "};}
        else{mask=rule.mask.clone();boolean placed=false;for(int y=0;y<3&&!placed;y++)for(int x=0;x<3;x++)if(mask[y].charAt(x)==' '){
            mask[y]=mask[y].substring(0,x)+"x"+mask[y].substring(x+1);placed=true;break;}
            if(!placed)throw fault("Banner mask has no space for its banner");}
        InventoryCrafting grid=new InventoryCrafting(new Container(){public boolean canInteractWith(EntityPlayer p){return false;}},3,3);
        int bannerSlot=-1,slot=0;JsonArray dyeSlots=array(),cells=array();Integer[] occupied=new Integer[9];
        for(int cell=0;cell<9;cell++){
            char symbol=mask[cell/3].charAt(cell%3);if(symbol==' ')continue;
            List<ItemStack> candidates=symbol=='x'?Collections.singletonList(base):symbol=='y'?Collections.singletonList(rule.catalyst):symbol=='#'?dyes.get(v.color):null;
            if(candidates==null||candidates.isEmpty())throw fault("Native banner logical cell has no material candidates");
            ItemStack sample=candidates.get(0).copy();sample.stackSize=1;
            grid.setInventorySlotContents(cell,sample);occupied[cell]=slot;
            boolean isBanner=sample.getItem()==banner;
            JsonObject predicate=isBanner?object("kind","bannerInput","maxPatterns",6):object("kind","wildcard","meta",false,"nbt",true);
            List<RecipeRow.Ingredient> choices=new ArrayList<>();
            for(ItemStack candidate:candidates)choices.add(new RecipeRow.Ingredient(candidate.copy(),1,false,predicate));
            row.itemInput(slot,choices,false);
            if(isBanner)bannerSlot=slot;
            else if(color(sample)>=0)dyeSlots.add(value(slot));
            slot++;
        }
        if(bannerSlot<0||!recipe.matches(grid,null))throw fault("Frozen banner sample is not accepted by native crafting");
        ItemStack result=recipe.getCraftingResult(grid);if(result==null)throw fault("Native banner result is missing");
        Object matched=invoke(recipe.getClass(),recipe,"getPattern",new Class<?>[]{InventoryCrafting.class},grid);
        if(matched!=rule.nativeRule)return row.exclude(object("native",RECIPE,"reason","preceding_native_banner_pattern_claims_generated_grid",
            "requestedPattern",rule.id,"matchedPattern",matched==null?null:call(matched,"getPatternID"),"color",v.color,"noDye",v.noDye));
        row.itemOutput(0,result,10000);
        row.outputs.get(0).getAsJsonObject().add("change",object("input",bannerSlot,
            "action",object("kind","bannerPattern","pattern",rule.id,"color",v.color),
            "samples",array(object("id",row.facts.item(result),"amount","1"))));
        JsonArray nativeRules=array(),palette=array();
        for(Rule r:rules)nativeRules.add(object("id",r.id,"mask",r.mask==null?null:array((Object[])r.mask),
            "catalyst",r.catalyst==null?null:row.facts.item(r.catalyst)));
        for(int color=0;color<16;color++)for(ItemStack dye:dyes.get(color)){
            JsonArray oreOrder=array();for(int ore:OreDictionary.getOreIDs(dye))oreOrder.add(value(OreDictionary.getOreName(ore)));
            palette.add(object("id",row.facts.item(dye),"color",color,"ores",oreOrder));
        }
        JsonArray colorOres=array();for(Object color:(Object[])field(type("ganymedes01.etfuturum.lib.EnumColor"),null,"VALUES"))
            colorOres.add(object("name",call(color,"getOreName"),"color",call(color,"getDamage")));
        for(Integer cell:occupied)cells.add(value(cell));
        row.record.add("process",object("kind","bannerPattern","nativeRecipe",RECIPE,"pattern",v.rule,"rules",nativeRules,
            "mode",rule.catalyst==null?"mask":v.noDye?"catalystNoDye":"catalyst","color",v.color,
            "palette",palette,"colorOres",colorOres,"banner",bannerSlot,"dyes",dyeSlots,"maxPatterns",6,"layout",cells));
        // The native matcher allows the banner in any blank mask cell and arbitrary
        // catalyst placement, including the personal grid. This is a view sample.
        row.record.add("grid",value(null));
        return true;
    }
    private List<List<ItemStack>> readDyes(boolean bind){
        List<List<ItemStack>> result=new ArrayList<>();for(int i=0;i<16;i++)result.add(new ArrayList<>());
        Set<String> seen=new HashSet<>();
        for(Object c:(Object[])field(type("ganymedes01.etfuturum.lib.EnumColor"),null,"VALUES"))
            for(ItemStack template:oreInputs((String)call(c,"getOreName"))){
                auditStack(template);
                List<ItemStack> examples;
                if(net.minecraft.init.Items.feather.getDamage(template)==32767){
                    NativeItemVariants binding=new NativeItemVariants(template);if(bind)metadata.add(binding);
                    examples=Arrays.asList(binding.copies());
                }else examples=Collections.singletonList(template.copy());
                for(ItemStack item:examples){
                    Jobs.checkpoint();
                    item.stackSize=1;item.setTagCompound(null);String key=CanonicalJson.digest(raw(item));
                    if(seen.add(key)){int color=color(item);if(color>=0)result.get(color).add(item);}
                    if(seen.size()>65536)throw fault("Banner dye inventory exceeds budget");
                }
            }
        return result;
    }
    private int color(ItemStack item){return (Integer)invoke(recipe.getClass(),recipe,"getDyeIndex",new Class<?>[]{ItemStack.class},item);}
    private String digest(){
        JsonArray list=array();for(Object r:(Object[])field(type(PATTERN),null,"VALUES"))list.add(object("id",call(r,"getPatternID"),
            "mask",array((Object[])call(r,"getCraftingLayers")),"catalyst",call(r,"getCraftingStack")==null?null:raw((ItemStack)call(r,"getCraftingStack"))));
        JsonArray nativeDyes=array();for(Object c:(Object[])field(type("ganymedes01.etfuturum.lib.EnumColor"),null,"VALUES")){
            String name=(String)call(c,"getOreName");JsonArray values=array();for(ItemStack stack:oreInputs(name))values.add(raw(stack));
            nativeDyes.add(object("name",name,"damage",call(c,"getDamage"),"templates",values));
        }
        JsonArray palette=array();for(List<ItemStack> values:readDyes(false))for(ItemStack item:values){JsonArray ores=array();
            for(int ore:OreDictionary.getOreIDs(item))ores.add(value(OreDictionary.getOreName(ore)));palette.add(object("item",raw(item),"color",color(item),"ores",ores));}
        return CanonicalJson.digest(object("rules",list,"nativeDyes",nativeDyes,"palette",palette));
    }
    @SuppressWarnings("unchecked") private static List<ItemStack> oreInputs(String name){
        Object key=((Map<?,?>)field(OreDictionary.class,null,"nameToId")).get(name);
        if(key==null)return Collections.emptyList();
        List<?> groups=(List<?>)field(OreDictionary.class,null,"idToStack");
        if(!(key instanceof Integer)||(Integer)key<0||(Integer)key>=groups.size())throw fault("Invalid native banner ore index");
        Object group=groups.get((Integer)key);
        if(group==null||group.getClass()!=ArrayList.class)throw fault("Unadapted native banner ore list");
        if(((List<?>)group).size()>65536)throw fault("Native banner dye source exceeds budget");
        return (List<ItemStack>)group;
    }
    private static List<Object> readAnchors(){
        List<Object> result=new ArrayList<>();Collections.addAll(result,roots());
        Object[] patterns=(Object[])field(type(PATTERN),null,"VALUES");
        if(patterns.length>4096)throw fault("Native banner patterns exceed budget");
        for(Object pattern:patterns){
            Jobs.checkpoint();if(pattern==null||pattern.getClass()!=type(PATTERN))throw fault("Unadapted native banner pattern callback");
            result.add(pattern);result.add(field(pattern,"craftingLayers"));
            Object stack=field(pattern,"patternCraftingStack");result.add(stack);
            if(stack!=null){auditStack(stack);result.add(((ItemStack)stack).getItem());result.add(((ItemStack)stack).getTagCompound());}
        }
        for(Object color:(Object[])field(type("ganymedes01.etfuturum.lib.EnumColor"),null,"VALUES")){
            Jobs.checkpoint();if(color==null||color.getClass()!=type("ganymedes01.etfuturum.lib.EnumColor"))throw fault("Unadapted native banner color callback");
            result.add(color);String name=(String)call(color,"getOreName");List<ItemStack> values=oreInputs(name);result.add(values);
            for(Object stack:values){auditStack(stack);result.add(stack);result.add(((ItemStack)stack).getItem());result.add(((ItemStack)stack).getTagCompound());}
        }
        return result;
    }
    private static void auditStack(Object value){
        if(value==null||value.getClass()!=ItemStack.class||((ItemStack)value).getItem()==null)throw fault("Unadapted native banner stack");
        ItemStack stack=(ItemStack)value;
        if(ItemCallbacks.method(stack.getItem(),"getDamage","getDamage",ItemStack.class).getDeclaringClass()!=Item.class)
            throw fault("Native banner needs an audited metadata getter");
        if(net.minecraft.init.Items.feather.getDamage(stack)<0)throw fault("Native banner negative metadata requires a dedicated predicate");
    }
    private static JsonObject raw(ItemStack item){auditStack(item);return RecipeRow.stackEvidence(item);}
    private static Object call(Object target,String method){return invoke(target.getClass(),target,method,new Class<?>[0]);}
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
    private static Jobs.Fault changed(){return new Jobs.Fault("recipe_changed","Native banner roots, pattern/ore members or facts changed");}
    private static final class Rule{final Object nativeRule;final String id;final String[] mask;final ItemStack catalyst;
        Rule(Object r,String id,String[] mask,ItemStack c){nativeRule=r;this.id=id;this.mask=mask;catalyst=c;}}
    private static final class Variant{final int rule,color;final boolean noDye;Variant(int r,int c,boolean n){rule=r;color=c;noDye=n;}}
}
